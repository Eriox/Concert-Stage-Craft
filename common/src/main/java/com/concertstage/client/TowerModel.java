package com.concertstage.client;

import com.concertstage.ConcertStage;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Cube lists of the motorised towers, read from assets/concertstage/towers/*.json (exported from the Blockbench models by
 * tools/export_towers.py) and drawn without any block model: a tower is taller than the 48 px a block model can hold and
 * its diagonals use free angles.
 *
 * A cube is 16 numbers: from (3), to (3), rotation origin (3), rotation in degrees (3), uv rectangle in a 16 x 16 texture (4).
 * All faces of a cube use the same tile, which is how the models were painted.
 */
public final class TowerModel {
    public static final ResourceLocation TEXTURE = new ResourceLocation(ConcertStage.MOD_ID, "textures/block/motor_tower.png");
    public static final ResourceLocation TEXTURE_NOIR = new ResourceLocation(ConcertStage.MOD_ID, "textures/block/motor_tower_noir.png");

    /** Texture of the chain hoist (its own 64 x 64 atlas; the cubes use uv in a 16 x 16 space, so the size does not matter). */
    public static final ResourceLocation HOIST_TEXTURE = new ResourceLocation(ConcertStage.MOD_ID, "textures/block/palan_chaine.png");

    /** The lower part of a chain hoist: hook, weight and tip (cubes in the authored position), and the chain links in between. */
    public static final class HoistHook {
        public List<float[]> hook = new ArrayList<>();
        public float[] linkA, linkB;
        public float pitch = 0.95f;
        public int firstLink = 6;
        public float hookDy = -6f;
        public float massTop = 3.8f;
        /** 1.1.0: the upper hook (swivel + hook) that hangs the hoist on its truss: drawn by the renderer, raised into the truss. */
        public List<float[]> topHook = new ArrayList<>();
        public float topBight = 16.456f;
        public float topBase = 14.1f;
    }

    private static HoistHook hoistHook;

    private static final Map<Integer, List<List<float[]>>> MASTS = new HashMap<>();
    private static final Map<String, List<float[]>> CARRIAGES = new HashMap<>();

    private TowerModel() {}

    /** Cubes of one cell (16 px slice) of the mast of a tower of the given height. */
    public static List<float[]> mastCell(int height, int cell) {
        List<List<float[]>> cells = MASTS.computeIfAbsent(height, h -> {
            List<List<float[]>> out = new ArrayList<>();
            JsonObject root = load("towers/mast_" + h + ".json");
            if (root != null) {
                for (JsonElement c : root.getAsJsonArray("cells")) out.add(cubes(c.getAsJsonArray()));
            }
            return out;
        });
        return cell >= 0 && cell < cells.size() ? cells.get(cell) : List.of();
    }

    /** Cubes of the sliding joint, relative to the bottom of its cell. */
    public static List<float[]> carriage(String corner) {
        return CARRIAGES.computeIfAbsent(corner, c -> {
            JsonObject root = load("towers/carriage_" + c + ".json");
            return root == null ? new ArrayList<>() : cubes(root.getAsJsonArray("cubes"));
        });
    }

    /** The hook assembly of the chain hoist (towers/hoist_hook.json). */
    public static HoistHook hoistHook() {
        if (hoistHook == null) {
            HoistHook h = new HoistHook();
            JsonObject root = load("towers/hoist_hook.json");
            if (root != null) {
                h.hook = cubes(root.getAsJsonArray("hook"));
                h.linkA = cubes(wrap(root.get("link_a"))).get(0);
                h.linkB = cubes(wrap(root.get("link_b"))).get(0);
                h.pitch = root.get("pitch").getAsFloat();
                h.firstLink = root.get("first_link").getAsInt();
                h.hookDy = root.get("hook_dy").getAsFloat();
                h.massTop = root.get("mass_top").getAsFloat();
                if (root.has("top_hook")) h.topHook = cubes(root.getAsJsonArray("top_hook"));
                if (root.has("top_bight")) h.topBight = root.get("top_bight").getAsFloat();
                if (root.has("top_base")) h.topBase = root.get("top_base").getAsFloat();
            }
            hoistHook = h;
        }
        return hoistHook;
    }

    private static JsonArray wrap(JsonElement cube) {
        JsonArray a = new JsonArray();
        a.add(cube);
        return a;
    }

    private static List<float[]> cubes(JsonArray array) {
        List<float[]> list = new ArrayList<>(array.size());
        for (JsonElement e : array) {
            JsonArray a = e.getAsJsonArray();
            float[] c = new float[16];
            for (int i = 0; i < 16; i++) c[i] = a.get(i).getAsFloat();
            list.add(c);
        }
        return list;
    }

    private static JsonObject load(String path) {
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(new ResourceLocation(ConcertStage.MOD_ID, path));
            if (res.isEmpty()) return null;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(res.get().open(), StandardCharsets.UTF_8))) {
                return JsonParser.parseReader(r).getAsJsonObject();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Forgets the cached models (called when resources reload). */
    public static void clear() {
        MASTS.clear();
        CARRIAGES.clear();
        hoistHook = null;
    }

    // ------------------------------------------------------------ drawing
    public static void render(List<float[]> cubes, PoseStack pose, VertexConsumer vc, int light) {
        for (float[] c : cubes) {
            boolean rotated = c[9] != 0 || c[10] != 0 || c[11] != 0;
            if (rotated) {
                pose.pushPose();
                pose.translate(c[6] / 16.0, c[7] / 16.0, c[8] / 16.0);
                if (c[9] != 0) pose.mulPose(Axis.XP.rotationDegrees(c[9]));
                if (c[10] != 0) pose.mulPose(Axis.YP.rotationDegrees(c[10]));
                if (c[11] != 0) pose.mulPose(Axis.ZP.rotationDegrees(c[11]));
                pose.translate(-c[6] / 16.0, -c[7] / 16.0, -c[8] / 16.0);
            }
            box(pose.last(), vc, c[0] / 16f, c[1] / 16f, c[2] / 16f, c[3] / 16f, c[4] / 16f, c[5] / 16f,
                c[12] / 16f, c[13] / 16f, c[14] / 16f, c[15] / 16f, light);
            if (rotated) pose.popPose();
        }
    }

    private static void box(PoseStack.Pose pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
                            float u0, float v0, float u1, float v1, int light) {
        quad(pose, vc, light, u0, v0, u1, v1, 0, -1, 0, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1);     // down
        quad(pose, vc, light, u0, v0, u1, v1, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);      // up
        quad(pose, vc, light, u0, v0, u1, v1, 0, 0, -1, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0);     // north
        quad(pose, vc, light, u0, v0, u1, v1, 0, 0, 1, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1);      // south
        quad(pose, vc, light, u0, v0, u1, v1, -1, 0, 0, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1);     // west
        quad(pose, vc, light, u0, v0, u1, v1, 1, 0, 0, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0);      // east
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vc, int light, float u0, float v0, float u1, float v1,
                             float nx, float ny, float nz, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        vertex(pose, vc, ax, ay, az, u0, v0, light, nx, ny, nz);
        vertex(pose, vc, bx, by, bz, u0, v1, light, nx, ny, nz);
        vertex(pose, vc, cx, cy, cz, u1, v1, light, nx, ny, nz);
        vertex(pose, vc, dx, dy, dz, u1, v0, light, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float u, float v, int light,
                               float nx, float ny, float nz) {
        vc.vertex(pose.pose(), x, y, z).color(255, 255, 255, 255).uv(u, v)
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.normal(), nx, ny, nz).endVertex();
    }
}
