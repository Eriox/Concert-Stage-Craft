package com.concertstage.client.audio;

import com.concertstage.ConcertStage;
import com.concertstage.audio.SpeakerModels;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Meshes of the CSC cabinets, read from {@code assets/concertstage/speakers/<id>.json} (made by tools/make_speakers.py from the
 * GAME_READY exports): near LOD (every part), far LOD (no screws or small connectors) and a box LOD built here for great distances.
 * Triangles, 8 floats per vertex (x, y, z, u, v, nx, ny, nz), origin at the centre of the front bottom edge, front towards +Z.
 */
public final class SpeakerMeshes {
    public static final ResourceLocation ATLAS = new ResourceLocation(ConcertStage.MOD_ID, "textures/block/speakers_atlas.png");
    public static final ResourceLocation AUREX_ATLAS = new ResourceLocation(ConcertStage.MOD_ID, "textures/block/aurex_atlas.png");
    /** Rev. 4: the Professional Audio Expansion and the C6 portable system (tools/audio_expansion/build_assets.py). */
    public static final ResourceLocation PRO_ATLAS = new ResourceLocation(ConcertStage.MOD_ID, "textures/block/aurex_pro_atlas.png");
    private static final java.util.Set<String> PRO = java.util.Set.of("csc_aurex_la12", "csc_aurex_la8", "csc_aurex_c6", "csc_aurex_b18p",
        "csc_aurex_la12_ground_frame", "csc_aurex_la8_ground_frame", "csc_aurex_la12_fly_frame", "csc_aurex_la8_fly_frame",
        "csc_orvane_lx8_stack_adapter", "csc_orvane_lx12_stack_adapter", "csc_veyra_a1_stack_adapter", "csc_veyra_a2_stack_adapter",
        "csc_noryx_a1_stack_adapter", "csc_noryx_a2_stack_adapter", "csc_aurex_portable_pole");
    public static ResourceLocation texture(String id) {
        if (id != null && PRO.contains(id)) return PRO_ATLAS;
        return id != null && id.startsWith("csc_aurex_") ? AUREX_ATLAS : ATLAS;
    }
    /** Atlas places: a dark coating swatch (frames, rigging) and the perforated grille. */
    public static final float DARK_U = 528f / 1024, DARK_V = 264f / 1024;

    public record Mesh(float[] near, float[] far, float[] box) {}

    private static final Map<String, Mesh> CACHE = new ConcurrentHashMap<>();

    private SpeakerMeshes() {}

    public static void clear() {
        CACHE.clear();
    }

    public static Mesh get(String id) {
        return CACHE.computeIfAbsent(id, SpeakerMeshes::load);
    }

    private static Mesh load(String id) {
        SpeakerModels.Model m = SpeakerModels.get(id);
        float[] box = m == null ? new float[0] : box(m.width(), m.height(), m.depth());
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(new ResourceLocation(ConcertStage.MOD_ID, "speakers/" + id + ".json"));
            if (res.isPresent()) try (BufferedReader r = new BufferedReader(new InputStreamReader(res.get().open(), StandardCharsets.UTF_8))) {
                JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                float k = (float) SpeakerModels.scale(id);              // rev. 4b: line arrays at the Orvane size
                return new Mesh(floats(o.getAsJsonArray("near"), k), floats(o.getAsJsonArray("far"), k), o.has("box") ? floats(o.getAsJsonArray("box"), k) : box);
            }
        } catch (Exception ignored) {
            // missing or damaged: the box stands in for the cabinet
        }
        return new Mesh(box, box, box);
    }

    private static float[] floats(JsonArray a, float k) {
        float[] f = new float[a.size()];
        for (int i = 0; i < f.length; i++) f[i] = a.get(i).getAsFloat();
        if (k != 1f) for (int i = 0; i + 2 < f.length; i += 8) { f[i] *= k; f[i + 1] *= k; f[i + 2] *= k; }
        return f;
    }

    /** Cabinet as a plain box: grille on the front, dark coating elsewhere (12 triangles). */
    static float[] box(double w, double h, double d) {
        float x0 = (float) (-w / 2), x1 = (float) (w / 2), y1 = (float) h, z0 = (float) -d;
        java.util.List<Float> out = new java.util.ArrayList<>();
        float gu = 0.5f / 1024, gv = 0.5f / 1024, gu1 = 512f / 1024 * 0.5f, gv1 = 256f / 1024 * 0.5f;
        // front (+Z): grille
        quad(out, new float[] {x0, 0, 0, x1, 0, 0, x1, y1, 0, x0, y1, 0}, new float[] {gu, gv1, gu1, gv1, gu1, gv, gu, gv}, 0, 0, 1);
        float[] dk = {DARK_U, DARK_V, DARK_U, DARK_V, DARK_U, DARK_V, DARK_U, DARK_V};
        quad(out, new float[] {x1, 0, z0, x0, 0, z0, x0, y1, z0, x1, y1, z0}, dk, 0, 0, -1);
        quad(out, new float[] {x0, y1, 0, x1, y1, 0, x1, y1, z0, x0, y1, z0}, dk, 0, 1, 0);
        quad(out, new float[] {x0, 0, z0, x1, 0, z0, x1, 0, 0, x0, 0, 0}, dk, 0, -1, 0);
        quad(out, new float[] {x1, 0, 0, x1, 0, z0, x1, y1, z0, x1, y1, 0}, dk, 1, 0, 0);
        quad(out, new float[] {x0, 0, z0, x0, 0, 0, x0, y1, 0, x0, y1, z0}, dk, -1, 0, 0);
        float[] f = new float[out.size()];
        for (int i = 0; i < f.length; i++) f[i] = out.get(i);
        return f;
    }

    /** Two triangles of a quad (corners counter-clockwise seen from outside). */
    static void quad(java.util.List<Float> out, float[] p, float[] uv, float nx, float ny, float nz) {
        int[] order = {0, 1, 2, 0, 2, 3};
        for (int k : order) {
            out.add(p[k * 3]); out.add(p[k * 3 + 1]); out.add(p[k * 3 + 2]);
            out.add(uv[k * 2]); out.add(uv[k * 2 + 1]);
            out.add(nx); out.add(ny); out.add(nz);
        }
    }
}
