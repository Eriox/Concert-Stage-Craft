package com.concertstage.fabric.client;

import com.google.gson.JsonObject;
import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * An OBJ model of the mod, baked the way Forge's OBJ loader does it for these files: one unit = one block, materials from the MTL
 * ({@code map_Kd #key}) mapped through the JSON {@code textures}, {@code flip_v}, {@code shade_quads}, no automatic culling, the blockstate
 * rotation applied around the block centre, item display from the JSON.
 */
public final class ObjUnbakedModel implements UnbakedModel {
    private final JsonObject json;
    private final FabricObjModels.ObjMesh mesh;

    ObjUnbakedModel(JsonObject json, FabricObjModels.ObjMesh mesh) {
        this.json = json;
        this.mesh = mesh;
    }

    @Override
    public Collection<ResourceLocation> getDependencies() {
        return List.of();
    }

    @Override
    public void resolveParents(Function<ResourceLocation, UnbakedModel> resolver) {
    }

    private Material material(String key) {
        String tex = key;
        if (key == null) tex = "particle";
        if (tex.startsWith("#")) tex = tex.substring(1);
        JsonObject textures = json.has("textures") ? json.getAsJsonObject("textures") : new JsonObject();
        for (int i = 0; i < 4 && textures.has(tex); i++) {                 // "#key" chains
            String v = textures.get(tex).getAsString();
            if (!v.startsWith("#")) { tex = v; break; }
            tex = v.substring(1);
        }
        if (textures.has(tex)) tex = textures.get(tex).getAsString();
        return new Material(InventoryMenu.BLOCK_ATLAS, new ResourceLocation(tex.contains(":") || tex.contains("/") ? tex : "concertstage:block/stage/" + tex));
    }

    @Override
    public BakedModel bake(ModelBaker baker, Function<Material, TextureAtlasSprite> sprites, ModelState state, ResourceLocation location) {
        boolean flipV = !json.has("flip_v") || json.get("flip_v").getAsBoolean();
        boolean shade = !json.has("shade_quads") || json.get("shade_quads").getAsBoolean();
        boolean ao = json.has("ambientocclusion") && json.get("ambientocclusion").getAsBoolean();
        Transformation rot = state.getRotation();
        Matrix4f m = rot.getMatrix();
        boolean identity = rot.equals(Transformation.identity());
        List<BakedQuad> quads = new ArrayList<>(mesh.faces.size());
        for (FabricObjModels.ObjMesh.Face f : mesh.faces) {
            TextureAtlasSprite sprite = sprites.apply(material(f.texture()));
            int[] data = new int[32];
            float[][] p = new float[4][];
            for (int i = 0; i < 4; i++) {
                int c = Math.min(i, f.pos().length - 1);                    // a triangle repeats its last corner
                float[] v = f.pos()[c];
                Vector4f q = new Vector4f(v[0], v[1], v[2], 1);
                if (!identity) { q.sub(0.5f, 0.5f, 0.5f, 0); m.transform(q); q.add(0.5f, 0.5f, 0.5f, 0); }
                p[i] = new float[] {q.x(), q.y(), q.z()};
                float u = f.uv()[c][0], vv = flipV ? 1 - f.uv()[c][1] : f.uv()[c][1];
                int o = i * 8;
                data[o] = Float.floatToRawIntBits(q.x());
                data[o + 1] = Float.floatToRawIntBits(q.y());
                data[o + 2] = Float.floatToRawIntBits(q.z());
                data[o + 3] = -1;
                data[o + 4] = Float.floatToRawIntBits(sprite.getU(u * 16));
                data[o + 5] = Float.floatToRawIntBits(sprite.getV(vv * 16));
                data[o + 6] = 0;
            }
            Vector3f n;
            if (f.normal() != null) n = new Vector3f(f.normal()[0], f.normal()[1], f.normal()[2]);
            else n = new Vector3f(p[1][0] - p[0][0], p[1][1] - p[0][1], p[1][2] - p[0][2]).cross(p[2][0] - p[0][0], p[2][1] - p[0][1], p[2][2] - p[0][2]);
            if (!identity && f.normal() != null) m.transformDirection(n);
            if (n.lengthSquared() > 1e-12f) n.normalize();
            int packed = (((int) (n.x() * 127)) & 255) | ((((int) (n.y() * 127)) & 255) << 8) | ((((int) (n.z() * 127)) & 255) << 16);
            for (int i = 0; i < 4; i++) data[i * 8 + 7] = packed;
            quads.add(new BakedQuad(data, -1, Direction.getNearest(n.x(), n.y(), n.z()), sprite, shade));
        }
        ItemTransforms transforms = ItemTransforms.NO_TRANSFORMS;
        boolean blockLight = true;
        if (json.has("display") || json.has("gui_light")) {
            try {
                JsonObject view = new JsonObject();
                if (json.has("display")) view.add("display", json.get("display"));
                if (json.has("gui_light")) view.add("gui_light", json.get("gui_light"));
                BlockModel bm = BlockModel.fromString(view.toString());
                transforms = bm.getTransforms();
                blockLight = bm.getGuiLight().lightLikeBlock();
            } catch (RuntimeException ignored) {
                // bad display: none
            }
        }
        Map<Direction, List<BakedQuad>> culled = new EnumMap<>(Direction.class);
        for (Direction d : Direction.values()) culled.put(d, List.of());
        return new SimpleBakedModel(quads, culled, ao, blockLight, true, sprites.apply(material("particle")), transforms, ItemOverrides.EMPTY);
    }
}
