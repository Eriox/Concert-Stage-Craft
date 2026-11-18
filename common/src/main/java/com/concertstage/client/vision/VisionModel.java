package com.concertstage.client.vision;

import com.concertstage.ConcertStage;
import com.concertstage.client.GpuMesh;
import com.concertstage.client.light.FixtureMeshes;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
 * CSC 1.3.0 - a LiveVision model exported from the supplied Blender file ({@code assets/concertstage/vision/<id>.json}, written in
 * Blender by {@code tools/v13/export_vision_blender.py}): the parts hanging from each pivot (ROOT, PAN, TILT, BOOM...), at four levels
 * of detail, triangles coloured by their material (Principled base colour; emissive materials drawn at full brightness), smooth
 * corner normals of the file. Nothing is simplified or redrawn: the geometry is only re-expressed in game coordinates.
 *
 * <p>Drawing: each part is a mesh kept in the graphics card (one per part, level of detail and light level), drawn with its pivot
 * transform (rest position of the pivot + the angle of the part), so moving parts turn about the real pivots of the file. The video
 * surfaces (LED_SURFACE of the panel, DISPLAY_DYNAMIC of the processor) and the "ledvideo" / "screendyn" triangles of the far levels can
 * be left out where the video is drawn instead.
 */
public final class VisionModel {
    public record Pivot(String name, String parent, float x, float y, float z) {}
    public record Point(String pivot, float x, float y, float z, float dx, float dy, float dz) {}
    public record Material(String name, int rgb, boolean emissive, float metal, boolean video) {}
    /** Triangles of one material: positions (3 floats per corner, part frame), normals (3 bytes per corner). */
    public record Batch(int material, float[] pos, byte[] nrm, float[] uv) {}

    public final String id;
    public final Map<String, Pivot> pivots = new LinkedHashMap<>();
    /** Pivot names, parents before children. */
    public final List<String> order = new ArrayList<>();
    public final List<Material> materials = new ArrayList<>();
    @SuppressWarnings("unchecked")
    public final Map<String, List<Batch>>[] lods = new Map[4];
    public final Map<String, List<Batch>> surfaces = new HashMap<>();
    public final Map<String, Point> points = new HashMap<>();
    public final int[] tris = new int[4];
    public float size = 1f;

    private static final Map<String, VisionModel> CACHE = new HashMap<>();
    private static final Map<String, GpuMesh> GPU = new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, GpuMesh> e) {
            if (size() <= 512) return false;
            if (e.getValue() != null) e.getValue().free();
            return true;
        }
    };

    private VisionModel(String id) { this.id = id; for (int i = 0; i < 4; i++) lods[i] = new LinkedHashMap<>(); }

    /** The model (loaded once), or null when its file is missing. */
    public static VisionModel get(String id) {
        if (CACHE.containsKey(id)) return CACHE.get(id);
        VisionModel m = null;
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(new ResourceLocation(ConcertStage.MOD_ID, "vision/" + id + ".json"));
            if (res.isPresent()) {
                try (var in = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
                    m = parse(id, JsonParser.parseReader(in).getAsJsonObject());
                }
            }
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().warn("Concert Stage LiveVision: model {} not loaded", id, e);
        }
        CACHE.put(id, m);
        return m;
    }

    public static void clear() {
        CACHE.clear();
        GPU.values().forEach(g -> { if (g != null) g.free(); });
        GPU.clear();
    }

    private static float[] floats(String b64) {
        ByteBuffer bb = ByteBuffer.wrap(Base64.getDecoder().decode(b64)).order(ByteOrder.LITTLE_ENDIAN);
        float[] f = new float[bb.remaining() / 4];
        bb.asFloatBuffer().get(f);
        return f;
    }

    private static VisionModel parse(String id, JsonObject o) {
        VisionModel m = new VisionModel(id);
        JsonObject pv = o.getAsJsonObject("pivots");
        Map<String, Pivot> tmp = new HashMap<>();
        for (var e : pv.entrySet()) {
            JsonObject p = e.getValue().getAsJsonObject();
            JsonArray pos = p.getAsJsonArray("pos");
            String parent = p.has("parent") && !p.get("parent").isJsonNull() ? p.get("parent").getAsString() : null;
            tmp.put(e.getKey(), new Pivot(e.getKey(), parent, pos.get(0).getAsFloat(), pos.get(1).getAsFloat(), pos.get(2).getAsFloat()));
        }
        // parents first
        while (m.order.size() < tmp.size()) {
            int before = m.order.size();
            for (Pivot p : tmp.values()) {
                if (m.order.contains(p.name)) continue;
                if (p.parent == null || m.order.contains(p.parent) || !tmp.containsKey(p.parent)) m.order.add(p.name);
            }
            if (m.order.size() == before) break;
        }
        for (String n : m.order) m.pivots.put(n, tmp.get(n));
        for (JsonElement me : o.getAsJsonArray("materials")) {
            JsonObject mo = me.getAsJsonObject();
            String name = mo.get("name").getAsString();
            boolean emit = mo.has("emit") && !mo.get("emit").isJsonNull();
            int rgb = Integer.parseInt(emit ? mo.get("emit").getAsString() : mo.get("rgb").getAsString(), 16);
            if (emit) rgb = mix(Integer.parseInt(mo.get("rgb").getAsString(), 16), rgb, 0.75f);
            boolean video = name.endsWith("ledvideo") || name.endsWith("screendyn");
            m.materials.add(new Material(name, rgb, emit, mo.has("metal") ? mo.get("metal").getAsFloat() : 0f, video));
        }
        JsonArray lods = o.getAsJsonArray("lods");
        for (int l = 0; l < Math.min(4, lods.size()); l++) {
            for (var e : lods.get(l).getAsJsonObject().entrySet()) {
                List<Batch> list = new ArrayList<>();
                for (JsonElement be : e.getValue().getAsJsonArray()) {
                    JsonObject b = be.getAsJsonObject();
                    float[] pos = floats(b.get("v").getAsString());
                    byte[] n = Base64.getDecoder().decode(b.get("n").getAsString());
                    list.add(new Batch(b.get("m").getAsInt(), pos, n, null));
                    m.tris[l] += pos.length / 9;
                }
                m.lods[l].put(e.getKey(), list);
            }
        }
        if (o.has("surfaces")) {
            for (var e : o.getAsJsonObject("surfaces").entrySet()) {
                List<Batch> list = new ArrayList<>();
                for (JsonElement be : e.getValue().getAsJsonArray()) {
                    JsonObject b = be.getAsJsonObject();
                    list.add(new Batch(b.get("m").getAsInt(), floats(b.get("v").getAsString()), Base64.getDecoder().decode(b.get("n").getAsString()),
                        b.has("uv") ? floats(b.get("uv").getAsString()) : null));
                }
                m.surfaces.put(e.getKey(), list);
            }
        }
        if (o.has("points")) {
            for (var e : o.getAsJsonObject("points").entrySet()) {
                JsonObject p = e.getValue().getAsJsonObject();
                JsonArray pos = p.getAsJsonArray("pos"), dir = p.getAsJsonArray("dir");
                String piv = p.has("pivot") && !p.get("pivot").isJsonNull() ? p.get("pivot").getAsString() : "ROOT";
                m.points.put(e.getKey(), new Point(piv, pos.get(0).getAsFloat(), pos.get(1).getAsFloat(), pos.get(2).getAsFloat(),
                    dir.get(0).getAsFloat(), dir.get(1).getAsFloat(), dir.get(2).getAsFloat()));
            }
        }
        if (o.has("stats")) {
            JsonArray bb = o.getAsJsonObject("stats").getAsJsonArray("bbox").get(0).getAsJsonArray();
            float s = 0;
            for (int k = 0; k < 3; k++) s = Math.max(s, bb.get(1).getAsJsonArray().get(k).getAsFloat() - bb.get(0).getAsJsonArray().get(k).getAsFloat());
            m.size = s;
        }
        return m;
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    // ------------------------------------------------------------------ pose
    /** Angles / offsets of the moving parts (degrees, metres), by pivot name. */
    public interface Poser {
        /** Rotation of the part about its pivot, applied to the pose (identity when the part does not move). */
        void apply(String pivot, PoseStack pose);
    }

    public static final Poser REST = (p, s) -> { };

    /** Transform of every pivot (model frame), for the pose: parent transform, then the offset to the pivot, then the part's motion. */
    public Map<String, Matrix4f> transforms(Poser poser) {
        Map<String, Matrix4f> out = new HashMap<>();
        PoseStack tmp = new PoseStack();
        for (String n : order) {
            Pivot p = pivots.get(n);
            Matrix4f base = p.parent != null && out.containsKey(p.parent) ? out.get(p.parent) : new Matrix4f();
            Pivot par = p.parent != null ? pivots.get(p.parent) : null;
            tmp.setIdentity();
            tmp.last().pose().set(base);
            tmp.translate(p.x - (par == null ? 0 : par.x), p.y - (par == null ? 0 : par.y), p.z - (par == null ? 0 : par.z));
            poser.apply(n, tmp);
            out.put(n, new Matrix4f(tmp.last().pose()));
        }
        return out;
    }

    /** A point of the model (lens axis, attach point) in the model frame for a pose: {x, y, z, dx, dy, dz}. */
    public float[] point(String name, Poser poser) {
        Point p = points.get(name);
        if (p == null) return null;
        Matrix4f t = transforms(poser).getOrDefault(p.pivot, new Matrix4f());
        org.joml.Vector4f pos = t.transform(new org.joml.Vector4f(p.x, p.y, p.z, 1));
        org.joml.Vector4f dir = t.transform(new org.joml.Vector4f(p.dx, p.dy, p.dz, 0));
        return new float[] {pos.x, pos.y, pos.z, dir.x, dir.y, dir.z};
    }

    // ------------------------------------------------------------------ drawing
    /**
     * Draws the model in the current pose (model frame = the pose's origin). {@code hideVideo}: leaves out the video surfaces (the wall or
     * the screen draws its image there). {@code tint}: 0xRRGGBB multiplied with the colours (0xFFFFFF = as modelled).
     */
    public void draw(PoseStack pose, MultiBufferSource buffers, int lod, int light, Poser poser, boolean hideVideo) {
        Map<String, Matrix4f> tr = transforms(poser);
        lod = Math.max(0, Math.min(3, lod));
        for (String part : order) {
            List<Batch> batches = lods[lod].get(part);
            boolean surf = lod == 0 && !hideVideo && part.equals(firstPivot());
            if ((batches == null || batches.isEmpty()) && !surf) continue;
            pose.pushPose();
            pose.last().pose().mul(tr.get(part));
            pose.last().normal().mul(new Matrix3f(tr.get(part)));
            String key = id + "/" + part + "/" + lod + "/" + light + (hideVideo ? "h" : "v");
            if (GpuMesh.enabled()) {
                GpuMesh gm = GPU.get(key);
                if (!GpuMesh.usable(gm)) {
                    GpuMesh.Builder b = GpuMesh.builder();
                    PoseStack origin = new PoseStack();
                    emitPart(b.consumer(FixtureMeshes.PALETTE, false, true), origin.last().pose(), origin.last().normal(), batches, surf, light, hideVideo);
                    gm = b.build();
                    GPU.put(key, gm);
                }
                if (gm != null) {
                    gm.draw(pose);
                    pose.popPose();
                    continue;
                }
            }
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(FixtureMeshes.PALETTE));
            emitPart(vc, pose.last().pose(), pose.last().normal(), batches, surf, light, hideVideo);
            pose.popPose();
        }
        VisionStats.model(tris[lod]);
    }

    /** Draws one part alone in its own frame (origin = its pivot): the LED accessories are three parts of one file. */
    public void drawPart(PoseStack pose, MultiBufferSource buffers, String part, int lod, int light) {
        lod = Math.max(0, Math.min(3, lod));
        List<Batch> batches = lods[lod].get(part);
        if (batches == null || batches.isEmpty()) return;
        String key = id + "/" + part + "/" + lod + "/" + light + "p";
        if (GpuMesh.enabled()) {
            GpuMesh gm = GPU.get(key);
            if (!GpuMesh.usable(gm)) {
                GpuMesh.Builder b = GpuMesh.builder();
                PoseStack origin = new PoseStack();
                emitPart(b.consumer(FixtureMeshes.PALETTE, false, true), origin.last().pose(), origin.last().normal(), batches, false, light, false);
                gm = b.build();
                GPU.put(key, gm);
            }
            if (gm != null) { gm.draw(pose); VisionStats.model(0); return; }
        }
        emitPart(buffers.getBuffer(RenderType.entityCutoutNoCull(FixtureMeshes.PALETTE)), pose.last().pose(), pose.last().normal(), batches, false, light, false);
    }

    private String firstPivot() { return order.isEmpty() ? "ROOT" : order.get(0); }

    private void emitPart(VertexConsumer vc, Matrix4f m, Matrix3f nm, List<Batch> batches, boolean surfaces, int light, boolean hideVideo) {
        if (batches != null) for (Batch b : batches) emitBatch(vc, m, nm, b, light, hideVideo);
        if (surfaces) for (List<Batch> l : this.surfaces.values()) for (Batch b : l) emitBatch(vc, m, nm, b, light, false);
    }

    private void emitBatch(VertexConsumer vc, Matrix4f m, Matrix3f nm, Batch b, int light, boolean hideVideo) {
        Material mat = b.material < materials.size() ? materials.get(b.material) : null;
        if (mat != null && hideVideo && mat.video) return;
        int rgb = mat == null ? 0x303030 : mat.rgb;
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, bl = rgb & 255;
        int lit = mat != null && mat.emissive ? 0xF000F0 : light;
        float u = FixtureMeshes.WHITE_U, v = FixtureMeshes.WHITE_V;
        float[] p = b.pos;
        byte[] n = b.nrm;
        for (int t = 0; t + 9 <= p.length; t += 9) {
            for (int k = 0; k < 4; k++) {
                int c = Math.min(k, 2);
                int o = t + c * 3;
                vc.vertex(m, p[o], p[o + 1], p[o + 2]).color(r, g, bl, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(lit)
                    .normal(nm, n[o] / 127f, n[o + 1] / 127f, n[o + 2] / 127f).endVertex();
            }
        }
    }

    /** Standard part motions: PAN / HEAD_PAN about Y, TILT / HEAD_TILT / BOOM / HEAD_LEVEL about X, OPTICAL_BLOCK along -Z (zoom). */
    public static Poser poser(float pan, float tilt, float zoom, float boom, float headPan, float headTilt) {
        return (p, s) -> {
            switch (p) {
                case "PAN" -> s.mulPose(Axis.YP.rotationDegrees(pan));
                case "TILT" -> s.mulPose(Axis.XP.rotationDegrees(tilt));
                case "OPTICAL_BLOCK" -> s.translate(0, 0, -0.012f * zoom);
                case "BOOM" -> s.mulPose(Axis.XP.rotationDegrees(boom));
                case "HEAD_LEVEL" -> s.mulPose(Axis.XP.rotationDegrees(-boom));      // keeps the head level (no parallelogram in the file)
                case "HEAD_PAN" -> s.mulPose(Axis.YP.rotationDegrees(headPan));
                case "HEAD_TILT" -> s.mulPose(Axis.XP.rotationDegrees(headTilt));
                default -> { }
            }
        };
    }
}
