package com.concertstage.client.light;

import com.concertstage.ConcertStage;
import com.concertstage.client.GpuMesh;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The four fixture models exported from the validated Blender files ({@code assets/concertstage/lightshow/<id>.json}, written by
 * {@code tools/lightshow/export_fixtures.py}): three parts (base, yoke, head) in three levels of detail, coloured triangles on one
 * palette texture, the emitter zones and the optics. Parts are kept in the graphics card ({@link GpuMesh}) per level of detail and
 * light level; when the cache is off (shader pack), the lowest level is drawn directly.
 */
public final class FixtureMeshes {
    public static final ResourceLocation PALETTE = new ResourceLocation(ConcertStage.MOD_ID, "textures/block/lightshow_palette.png");
    /** 1.3.0: centre of the white texel (0, 0) of the 64 x 64 palette, for meshes coloured by their vertices (trusses, LiveVision models). */
    public static final float WHITE_U = 0.5f / 64, WHITE_V = 0.5f / 64;
    public static final int NEAR = 0, FAR = 1, LOW = 2;

    /** One part at one level of detail: per colour, the triangles (x, y, z per vertex, already in blocks). */
    public static final class Part {
        final List<int[]> colors = new ArrayList<>();
        final List<float[]> tris = new ArrayList<>();
        int triangles;
    }

    public record Zone(float cx, float cy, float cz, float hx, float hy, int kind) {}

    public static final class Model {
        public final String id;
        final Map<String, Part[]> parts = new HashMap<>();
        public float[] panPivot, tiltPivot;
        public float[] beamOrigin = {0, 0, -0.1f}, beamAxis = {0, 0, -1};
        public float lensRadius = 0.05f;
        /**
         * Rev. 6: depth of the front of the lens at the beam origin (head frame, -Z is the front). The exported lenses are domes or
         * optics standing in front of the plane of the emitters: a glow drawn on that plane was hidden behind the opaque lens.
         */
        public float lensFrontZ = -0.1f;
        public final List<Zone> zones = new ArrayList<>();
        float whiteU = 0.5f / 16, whiteV = 0.5f / 16;

        Model(String id) {
            this.id = id;
        }

        public boolean hasYoke() {
            return parts.containsKey("yoke");
        }
    }

    private static final Map<String, Model> MODELS = new HashMap<>();
    /** Rev. 18: 256 (was 96): 16 models x 3 parts x 3 levels x a few light levels did not fit, and a missing mesh is rebuilt. */
    private static final GpuMesh.Cache GPU = new GpuMesh.Cache(256);
    /** Rev. 18: hanging hardware, one mesh per layout and light level. */
    private static final GpuMesh.Cache MOUNTS = new GpuMesh.Cache(128);

    private FixtureMeshes() {}

    public static void clear() {
        MODELS.clear();
        GPU.clear();
        MOUNTS.clear();
        FixtureMount.clear();
    }

    public static Model get(String id) {
        return MODELS.computeIfAbsent(id, FixtureMeshes::load);
    }

    private static Model load(String id) {
        Model m = new Model(id);
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(new ResourceLocation(ConcertStage.MOD_ID, "lightshow/" + id + ".json"));
            if (res.isEmpty()) return m;
            JsonObject root;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(res.get().open(), StandardCharsets.UTF_8))) {
                root = JsonParser.parseReader(r).getAsJsonObject();
            }
            float q = root.get("quant").getAsFloat();
            String[] lods = {"parts", "lod_far", "lod_low"};
            for (int l = 0; l < 3; l++) {
                JsonObject lod = root.getAsJsonObject(lods[l]);
                if (lod == null) continue;
                for (Map.Entry<String, JsonElement> e : lod.entrySet()) {
                    Part p = new Part();
                    JsonObject stat = e.getValue().getAsJsonObject().getAsJsonObject("static");
                    for (Map.Entry<String, JsonElement> c : stat.entrySet()) {
                        int rgb = Integer.parseInt(c.getKey(), 16);
                        JsonArray a = c.getValue().getAsJsonArray();
                        float[] t = new float[a.size()];
                        for (int i = 0; i < t.length; i++) t[i] = a.get(i).getAsInt() / q;
                        p.colors.add(new int[] {rgb});
                        p.tris.add(t);
                        p.triangles += t.length / 9;
                    }
                    m.parts.computeIfAbsent(e.getKey(), k -> new Part[3])[l] = p;
                }
            }
            JsonObject piv = root.getAsJsonObject("pivots");
            m.panPivot = piv.get("pan").isJsonNull() ? null : floats(piv.getAsJsonArray("pan"));
            m.tiltPivot = floats(piv.getAsJsonArray("tilt"));
            JsonObject beam = root.getAsJsonObject("beam");
            m.beamOrigin = floats(beam.getAsJsonArray("origin"));
            m.beamAxis = floats(beam.getAsJsonArray("axis"));
            m.lensRadius = beam.get("lens_radius").getAsFloat();
            JsonObject zones = root.getAsJsonObject("zones");
            for (int i = 0; i < zones.size(); i++) {
                JsonObject z = zones.getAsJsonObject(String.valueOf(i));
                if (z == null || !z.has("centre")) { m.zones.add(new Zone(0, 0, 0, 0, 0, 2)); continue; }
                float[] c = floats(z.getAsJsonArray("centre")), h = floats(z.getAsJsonArray("half"));
                String kind = z.get("kind").getAsString();
                // rev. 6: the glow of a cell goes just in front of whatever the model has in front of it (lens dome, optic),
                // otherwise the opaque lens hides it and a lit wash shows dark lenses
                float front = frontZ(m, c[0], c[1], h[0] * 0.7f, h[1] * 0.7f, c[2]);
                m.zones.add(new Zone(c[0], c[1], front, h[0], h[1], kind.equals("rgb") ? 0 : kind.equals("white") ? 1 : 2));
            }
            float lr = m.lensRadius * 0.7f;
            m.lensFrontZ = frontZ(m, m.beamOrigin[0], m.beamOrigin[1], lr, lr, m.beamOrigin[2]);
            if (root.has("palette_white_uv")) {
                JsonArray uv = root.getAsJsonArray("palette_white_uv");
                m.whiteU = uv.get(0).getAsFloat();
                m.whiteV = uv.get(1).getAsFloat();
            }
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().warn("Concert Stage: fixture model {} could not be read", id, e);
        }
        return m;
    }

    /**
     * The frontmost depth (smallest z) of the head geometry over a rectangle of the lens plane, in every level of detail, not behind
     * {@code fallback}: the glows drawn there are never hidden by the lens they stand for.
     */
    static float frontZ(Model m, float cx, float cy, float hx, float hy, float fallback) {
        float min = fallback;
        Part[] levels = m.parts.get("head");
        if (levels == null) return fallback;
        for (Part p : levels) {
            if (p == null) continue;
            for (float[] t : p.tris)
                for (int i = 0; i + 2 < t.length; i += 3)
                    if (Math.abs(t[i] - cx) <= hx && Math.abs(t[i + 1] - cy) <= hy && t[i + 2] < min && t[i + 2] > fallback - 0.12f) min = t[i + 2];
        }
        return min;
    }

    private static float[] floats(JsonArray a) {
        float[] f = new float[a.size()];
        for (int i = 0; i < f.length; i++) f[i] = a.get(i).getAsFloat();
        return f;
    }

    /** Light levels are rounded (4 steps of block and sky light) so few meshes are cached. */
    private static int roundLight(int packed) {
        int block = (packed & 0xFFFF) >> 4, sky = (packed >> 20) & 0xF;
        block = Math.min(15, (block + 2) / 4 * 4 + (block >= 14 ? 1 : 0));
        sky = Math.min(15, (sky + 2) / 4 * 4 + (sky >= 14 ? 1 : 0));
        return block << 4 | sky << 20;
    }

    /** Draws one part (at the renderer's current pose). */
    public static void draw(Model m, String part, int lod, PoseStack pose, MultiBufferSource buffers, int light) {
        Part[] levels = m.parts.get(part);
        if (levels == null) return;
        int l = Math.max(0, Math.min(2, lod));
        if (GpuMesh.enabled()) {
            int lt = roundLight(light);
            Part p = levels[l] != null ? levels[l] : levels[2];
            if (p == null) return;
            GpuMesh mesh = GPU.get(m.id + "/" + part + "/" + l + "/" + lt, b -> emit(m, p, new PoseStack(), b.consumer(PALETTE, true, true), lt));
            if (mesh != null) { mesh.drawLate(pose); return; }       // rev. 14b: after the stage lights (see GpuMesh.LATE)
        }
        // without the cache every triangle is sent each frame: the middle level close up (FarView keeps it within 12 blocks), else the lowest
        Part p = l <= FAR && levels[1] != null ? levels[1] : levels[2] != null ? levels[2] : levels[l];
        if (p == null) return;
        emit(m, p, pose, buffers.getBuffer(RenderType.entityCutout(PALETTE)), light);
    }

    /**
     * Rev. 18: the hanging hardware of a fixture (bar, half couplers, omega, hooks), at the current pose (block-local, not turned with the
     * fixture). Shared in the graphics card by every fixture with the same layout; without the cache (shader pack) it is drawn directly,
     * and only close up (the caller limits the distance).
     */
    public static void drawMount(Model m, MountGeometry.Layout layout, PoseStack pose, MultiBufferSource buffers, int light) {
        if (layout.tris().isEmpty()) return;
        if (GpuMesh.enabled()) {
            int lt = roundLight(light);
            GpuMesh mesh = MOUNTS.get("mount/" + layout.key() + "/" + lt, b -> emit(m, part(layout), new PoseStack(), b.consumer(PALETTE, true, true), lt));
            if (mesh != null) { mesh.drawLate(pose); return; }
        }
        emit(m, part(layout), pose, buffers.getBuffer(RenderType.entityCutout(PALETTE)), light);
    }

    private static Part part(MountGeometry.Layout layout) {
        Part p = new Part();
        for (int i = 0; i < layout.tris().size(); i++) {
            p.colors.add(new int[] {layout.colors().get(i)});
            p.tris.add(layout.tris().get(i));
            p.triangles += layout.tris().get(i).length / 9;
        }
        return p;
    }

    /** Triangles as quads with a repeated last vertex (the quad formats of the game). */
    static void emit(Model m, Part p, PoseStack pose, VertexConsumer vc, int light) {
        Matrix4f mat = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        for (int c = 0; c < p.tris.size(); c++) {
            int rgb = p.colors.get(c)[0];
            int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
            float[] t = p.tris.get(c);
            for (int i = 0; i + 8 < t.length; i += 9) {
                float ax = t[i], ay = t[i + 1], az = t[i + 2], bx = t[i + 3], by = t[i + 4], bz = t[i + 5], cx = t[i + 6], cy = t[i + 7], cz = t[i + 8];
                float ux = bx - ax, uy = by - ay, uz = bz - az, vx = cx - ax, vy = cy - ay, vz = cz - az;
                float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
                float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (nl < 1e-12f) continue;
                nx /= nl; ny /= nl; nz /= nl;
                v(vc, mat, nm, ax, ay, az, r, g, b, m, light, nx, ny, nz);
                v(vc, mat, nm, bx, by, bz, r, g, b, m, light, nx, ny, nz);
                v(vc, mat, nm, cx, cy, cz, r, g, b, m, light, nx, ny, nz);
                v(vc, mat, nm, cx, cy, cz, r, g, b, m, light, nx, ny, nz);
            }
        }
    }

    private static void v(VertexConsumer vc, Matrix4f mat, Matrix3f nm, float x, float y, float z, int r, int g, int b, Model m, int light,
                          float nx, float ny, float nz) {
        vc.vertex(mat, x, y, z).color(r, g, b, 255).uv(m.whiteU, m.whiteV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
            .normal(nm, nx, ny, nz).endVertex();
    }
}
