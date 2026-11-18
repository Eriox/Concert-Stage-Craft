package com.concertstage.client;

import com.concertstage.block.roof.RoofTrussBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws a roof support frame: a box truss (4 chords, posts, rungs and diagonals, same section, texture and shading as the 1-block trusses and
 * the circular truss) along the centre line of its block entity (arc, gable or single slope). Meshes are cached per shape.
 */
public class RoofTrussRenderer implements BlockEntityRenderer<RoofTrussBlockEntity> {
    private static final double H = RoofTrussBlockEntity.H, CHORD = RoofTrussBlockEntity.CHORD, BRACE = 0.52 / 16;
    /** Meshes by roof geometry (a few roofs with the same settings share them), least recently used dropped beyond {@link #MAX_MESHES}. */
    private static final int MAX_MESHES = 48;
    private static final Map<String, Meshes> MESHES = new java.util.LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Meshes> e) { return size() > MAX_MESHES; }
    };
    /** Per roof: its geometry key, computed again only when the roof changes (not every frame). */
    private static final Map<RoofTrussBlockEntity, Keyed> KEYS = new java.util.WeakHashMap<>();
    private static net.minecraft.client.renderer.texture.TextureAtlasSprite cloth;

    private record Keyed(int revision, String frame, String sheet) {}

    /** The frame at its three levels of detail (built when first needed) and the roof sheet. */
    private static final class Meshes {
        final float[][][] lod = new float[3][][];
        float[][] sheet;
    }

    public RoofTrussRenderer(BlockEntityRendererProvider.Context context) {}

    /** 1.1.0: the same meshes in the graphics card, by geometry + level of detail + colour + light (see GpuMesh). */
    private static final Map<String, GpuMesh> GPU = new java.util.LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, GpuMesh> e) {
            if (size() <= 256) return false;
            if (e.getValue() != null) e.getValue().free();
            return true;
        }
    };

    public static void clear() { MESHES.clear(); KEYS.clear(); cloth = null; GPU.values().forEach(m -> { if (m != null) m.free(); }); GPU.clear(); }

    private static Keyed keys(RoofTrussBlockEntity be) {
        Keyed k = KEYS.get(be);
        if (k != null && k.revision == be.revision()) return k;
        String key = be.shape() + "/" + be.span() + "/" + be.rise2() + "/" + be.turn() + (be.isFull() ? "/f" + be.cover() + (be.isHeavy() ? "h" : "") + "e" + be.overhang() : "")
            + (be.hasKingPost() ? "/k" + (be.isKingSmall() ? "s" : "") + java.util.Arrays.deepHashCode(be.kingPosts().toArray()) : "");
        String sk = be.cover() != 0 || be.isFull() ? key + "/c" + be.cover() + "/" + be.overhang() + (be.isHeavy() ? "h" : "") + "/" + java.util.Arrays.hashCode(be.skirt(0))
            + "/" + java.util.Arrays.hashCode(be.skirt(1)) + "/" + java.util.Arrays.hashCode(be.flap(0)) + "/" + java.util.Arrays.hashCode(be.flap(1)) : null;
        k = new Keyed(be.revision(), key, sk);
        KEYS.put(be, k);
        return k;
    }

    @Override
    public void render(RoofTrussBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) return;
        Keyed k = keys(be);
        blockLight(be, k);
        net.minecraft.world.phys.AABB box = be.getRenderBoundingBox();
        if (!ClientCulling.visible(be, box)) return;                                        // off screen (Fabric; Forge checks it itself)
        int detail = ClientCulling.trussDetail(ClientCulling.distance(box));
        Meshes meshes = MESHES.computeIfAbsent(k.frame, x -> new Meshes());
        float[][] mesh = meshes.lod[detail];
        if (mesh == null) mesh = meshes.lod[detail] = roof(be, detail);
        int lit = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above());
        if (GpuMesh.enabled()) {
            String gk = k.frame + "#" + detail + (be.isBlack() ? "b" : "a") + lit + (k.sheet == null ? "" : "#" + k.sheet);
            GpuMesh gm = GPU.get(gk);
            if (!GpuMesh.usable(gm)) {
                GpuMesh.Builder b = GpuMesh.builder();
                emitFrame(b.consumer(be.isBlack() ? RiggingMeshes.BLACK : RiggingMeshes.ALUMINIUM, false, true), new Matrix4f(), new Matrix3f(), mesh, lit);
                if (k.sheet != null) emitSheet(be, k, b.consumer(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS, true, false), new Matrix4f(), new Matrix3f(), lit);
                gm = b.build();
                GPU.put(gk, gm);
            }
            if (gm != null) { gm.draw(pose); return; }
        }
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(be.isBlack() ? RiggingMeshes.BLACK : RiggingMeshes.ALUMINIUM));
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        emitFrame(vc, m, nm, mesh, lit);
        if (k.sheet != null) emitSheet(be, k, GpuMesh.cutout(buffers), m, nm, lit);
    }

    /** Rev. 20: the roof sheet stops the light of the fixtures (LightBlockers): one face of each tile. */
    private static void blockLight(RoofTrussBlockEntity be, Keyed k) {
        if (k.sheet == null) { com.concertstage.client.light.LightBlockers.remove(be); return; }
        Meshes sm = MESHES.computeIfAbsent(k.sheet, x -> new Meshes());
        if (sm.sheet == null) sm.sheet = sheet(be);
        float[][] all = sm.sheet;
        double[][] quads = new double[(all.length + 1) / 2][];
        for (int i = 0, n = 0; i < all.length; i += 2, n++) {
            double[] q = new double[12];
            for (int v = 0; v < 4; v++) { q[v * 3] = all[i][v * 8]; q[v * 3 + 1] = all[i][v * 8 + 1]; q[v * 3 + 2] = all[i][v * 8 + 2]; }
            quads[n] = q;
        }
        com.concertstage.client.light.LightBlockers.put(be, be.getBlockPos(), k.sheet + "@" + k.revision, quads);
    }

    private static void emitFrame(VertexConsumer vc, Matrix4f m, Matrix3f nm, float[][] mesh, int lit) {
        int ov = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
        for (float[] q : mesh) {
            for (int j = 0; j < 32; j += 8) {
                vc.vertex(m, q[j], q[j + 1], q[j + 2]).color(255, 255, 255, 255).uv(q[j + 3] / 16f, q[j + 4] / 16f)
                    .overlayCoords(ov).uv2(lit).normal(nm, q[j + 5], q[j + 6], q[j + 7]).endVertex();
            }
        }
    }

    /** Roof sheet: the PVC of the stage tarps (black), opaque, both faces, in 1-block tiles. */
    private static void emitSheet(RoofTrussBlockEntity be, Keyed k, VertexConsumer cv, Matrix4f m, Matrix3f nm, int lit) {
        Meshes sm = MESHES.computeIfAbsent(k.sheet, x -> new Meshes());
        if (sm.sheet == null) sm.sheet = sheet(be);
        if (cloth == null) cloth = net.minecraft.client.Minecraft.getInstance().getModelManager()
            .getAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)
            .getSprite(new net.minecraft.resources.ResourceLocation("concertstage", "block/stage_tarp_black"));
        for (float[] q : sm.sheet)
            for (int j = 0; j < 32; j += 8) {
                int c = (int) (255 * RiggingMeshes.blockShade(q[j + 5], q[j + 6], q[j + 7]));
                cv.vertex(m, q[j], q[j + 1], q[j + 2]).color(c, c, c, 255).uv(cloth.getU(q[j + 3]), cloth.getV(q[j + 4])).uv2(lit)
                    .normal(nm, q[j + 5], q[j + 6], q[j + 7]).endVertex();
            }
    }

    /** Bottom of the sheets turned down to the trusses: a little under the top of the truss, where the stage tarps hang from it. */
    private static final double DOWN_TO = -0.3, OUTSIDE = 0.03;

    /**
     * Roof sheet on the top chords, over the whole width of the edge trusses (and its overhang); where asked, the sheet turned down outside
     * the edge frames (gables, the space under an arc) and at the frame ends, to the trusses under them, so it meets the stage tarps.
     */
    static float[][] sheet(RoofTrussBlockEntity be) {
        List<float[]> out = new ArrayList<>();
        List<double[]> line = RoofTrussBlockEntity.roofLine(be.shape(), be.span(), be.rise(), be.turn(), be.isFull(), be.overhang());
        double[] side = RoofTrussBlockEntity.side(be.turn());
        double h = be.isHeavy() ? H * RoofTrussBlockEntity.HEAVY : H;
        double ch = CHORD * h / H, lift = h + ch + 0.02, half = h + ch + OUTSIDE;
        double ov = be.isFull() ? be.overhang() : 0, sign = be.cover() == 0 ? 1 : Math.signum(be.cover());
        double from = -sign * (ov + half), to = be.cover() + sign * (ov + half);
        // the sheet covers the end trusses of the frames too: carried on half a truss past both ends, along the end slopes
        List<double[]> top = RoofTrussBlockEntity.withEaves(line, half);
        if (be.cover() != 0) {
            for (int s = 0; s + 1 < top.size(); s++) {
                double[] a = {top.get(s)[0], top.get(s)[1] + lift, top.get(s)[2]}, b = {top.get(s + 1)[0], top.get(s + 1)[1] + lift, top.get(s + 1)[2]};
                tiles(out, a, b, side, from, to);
            }
        }
        // gables: outside the edge frame (towards the audience / the outside), from the sheet down to the top of the stage tarp near it
        // (slanted into its plane, like the flaps at the frame ends), else straight down to the truss
        for (int edge = 0; edge < 2; edge++) {
            int[] sk = be.skirt(edge);
            double off = edge == 0 ? -sign * half : be.cover() + sign * half;
            for (int i = 0; i + 1 < line.size() && 3 * i + 5 < sk.length; i++) {
                if (sk[3 * i] == RoofTrussBlockEntity.NONE || sk[3 * i + 3] == RoofTrussBlockEntity.NONE) continue;
                double[] p0 = line.get(i), p1 = line.get(i + 1);
                double[] a = {p0[0] + side[0] * off, p0[1] + lift, p0[2] + side[2] * off}, b = {p1[0] + side[0] * off, p1[1] + lift, p1[2] + side[2] * off};
                double[] ta = {sk[3 * i] / 1000.0, sk[3 * i + 1] / 1000.0, sk[3 * i + 2] / 1000.0};
                double[] tb = {sk[3 * i + 3] / 1000.0, sk[3 * i + 4] / 1000.0, sk[3 * i + 5] / 1000.0};
                slant(out, a, b, tb, ta);
            }
        }
        // ends of the frames: the sheet goes on from the roof edge to the top of the stage tarp near it, slanted like a stage tarp stretched
        // to a stage rim (or straight down outside the truss when there is no stage tarp), column by column
        Direction d = be.direction();
        for (int end = 0; end < 2; end++) {
            int[] fl = be.flap(end);
            int n = fl.length / 3;
            if (n < 2) continue;
            double[] p = top.get(end == 0 ? 0 : top.size() - 1);                         // edge of the sheet (past the end truss)
            double out2 = 0;
            for (int i = 0; i + 1 < n; i++) {
                if (fl[3 * i] == RoofTrussBlockEntity.NONE || fl[3 * (i + 1)] == RoofTrussBlockEntity.NONE) continue;
                double w0 = sign * i - (i == 0 ? sign * half : 0), w1 = sign * (i + 1) + (i + 1 == n - 1 ? sign * half : 0);
                double[] a = {p[0] + d.getStepX() * out2 + side[0] * w0, p[1] + lift, p[2] + d.getStepZ() * out2 + side[2] * w0};
                double[] b = {p[0] + d.getStepX() * out2 + side[0] * w1, p[1] + lift, p[2] + d.getStepZ() * out2 + side[2] * w1};
                double[] ta = {fl[3 * i] / 1000.0 - (i == 0 ? side[0] * sign * half : 0), fl[3 * i + 1] / 1000.0, fl[3 * i + 2] / 1000.0 - (i == 0 ? side[2] * sign * half : 0)};
                double[] tb = {fl[3 * i + 3] / 1000.0 + (i + 1 == n - 1 ? side[0] * sign * half : 0), fl[3 * i + 4] / 1000.0,
                    fl[3 * i + 5] / 1000.0 + (i + 1 == n - 1 ? side[2] * sign * half : 0)};
                slant(out, a, b, tb, ta);
            }
        }
        return out.toArray(new float[0][]);
    }

    /** Sheet over the segment a-b, from {@code from} to {@code to} blocks sideways, cut in tiles of at most one block (texture scale kept). */
    private static void tiles(List<float[]> out, double[] a, double[] b, double[] side, double from, double to) {
        double len = Math.sqrt(dot(sub(b, a), sub(b, a)));
        if (len < 1e-6) return;
        double lo = Math.min(from, to), hi = Math.max(from, to);
        double[] n = norm(cross(side, sub(b, a)));
        if (n[1] < 0) n = new double[] {-n[0], -n[1], -n[2]};
        for (double u = 0; u < len - 1e-6; u += 1) {
            double u1 = Math.min(len, u + 1);
            for (double w = lo; w < hi - 1e-6; w = Math.floor(w) + 1) {
                double w1 = Math.min(hi, Math.floor(w) + 1);
                double[] p0 = lerpSide(a, b, u / len, side, w), p1 = lerpSide(a, b, u1 / len, side, w), p2 = lerpSide(a, b, u1 / len, side, w1), p3 = lerpSide(a, b, u / len, side, w);
                p3 = lerpSide(a, b, u / len, side, w1);
                float[][] uv = {{0, (float) ((w - Math.floor(w)) * 16)}, {(float) ((u1 - u) * 16), (float) ((w - Math.floor(w)) * 16)},
                    {(float) ((u1 - u) * 16), (float) ((w1 - Math.floor(w)) * 16)}, {0, (float) ((w1 - Math.floor(w)) * 16)}};
                doubleSided(out, new double[][] {p0, p1, p2, p3}, uv, n);
            }
        }
    }

    /** Quad a-b (top) to c-d (bottom, c under b), cut along the slope in pieces of at most one block; both faces. */
    private static void slant(List<float[]> out, double[] a, double[] b, double[] c, double[] d) {
        double len = Math.max(Math.sqrt(dot(sub(d, a), sub(d, a))), Math.sqrt(dot(sub(c, b), sub(c, b))));
        double wid = Math.min(16, Math.sqrt(dot(sub(b, a), sub(b, a))) * 16);
        int k = Math.max(1, (int) Math.ceil(len));
        for (int j = 0; j < k; j++) {
            double f0 = j / (double) k, f1 = (j + 1) / (double) k;
            double[] p0 = mix(a, d, f0), p1 = mix(b, c, f0), p2 = mix(b, c, f1), p3 = mix(a, d, f1);
            float v = (float) Math.min(16, len / k * 16);
            doubleSided(out, new double[][] {p0, p1, p2, p3}, new float[][] {{0, 0}, {(float) wid, 0}, {(float) wid, v}, {0, v}}, new double[] {0, 1, 0});
        }
    }

    private static double[] mix(double[] a, double[] b, double f) {
        return new double[] {a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f};
    }

    private static double[] lerpSide(double[] a, double[] b, double f, double[] side, double w) {
        return new double[] {a[0] + (b[0] - a[0]) * f + side[0] * w, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f + side[2] * w};
    }

    /** Both faces (the cutout type culls back faces); each face lit with its own outward normal (from its winding). */
    private static void doubleSided(List<float[]> out, double[][] p, float[][] uv, double[] hint) {
        double[] n = norm(cross(sub(p[1], p[0]), sub(p[2], p[0])));
        if (dot(n, n) < 0.5) n = hint;
        out.add(quad(p, uv, n));
        out.add(quad(new double[][] {p[3], p[2], p[1], p[0]}, new float[][] {uv[3], uv[2], uv[1], uv[0]}, new double[] {-n[0], -n[1], -n[2]}));
    }

    private static float[] quad(double[][] p, float[][] uv, double[] n) {
        float[] q = new float[32];
        for (int k = 0; k < 4; k++) {
            q[k * 8] = (float) p[k][0]; q[k * 8 + 1] = (float) p[k][1]; q[k * 8 + 2] = (float) p[k][2];
            q[k * 8 + 3] = uv[k][0]; q[k * 8 + 4] = uv[k][1];
            q[k * 8 + 5] = (float) n[0]; q[k * 8 + 6] = (float) n[1]; q[k * 8 + 7] = (float) n[2];
        }
        return q;
    }

    @Override
    public boolean shouldRenderOffScreen(RoofTrussBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return RenderQuality.distance(160);
    }

    // ------------------------------------------------------------ mesh
    private static double[] sub(double[] a, double[] b) { return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    private static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    private static double[] cross(double[] a, double[] b) { return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    private static double[] norm(double[] v) { double l = Math.sqrt(dot(v, v)); return l < 1e-9 ? new double[] {0, 1, 0} : new double[] {v[0] / l, v[1] / l, v[2] / l}; }
    private static double[] at(double[] p, double[] up, double u, double[] side, double s) {
        return new double[] {p[0] + up[0] * u + side[0] * s, p[1] + up[1] * u + side[1] * s, p[2] + up[2] * u + side[2] * s};
    }

    /** This frame alone, or a whole roof (frames every ~3 m, ridge bar, purlins; heavier ridge and end gables for the CT roof). */
    static float[][] roof(RoofTrussBlockEntity be, int detail) {
        float[][] frames = frames(be, detail);
        List<double[][]> kings = be.kingPosts();
        if (kings.isEmpty()) return frames;
        List<float[]> out = new ArrayList<>(java.util.Arrays.asList(frames));
        double h = be.isKingSmall() ? H * RoofTrussBlockEntity.SMALL : be.isHeavy() ? H * RoofTrussBlockEntity.HEAVY : H;
        for (double[][] k : kings) {                                                    // truss columns from the ridge down to the middle truss
            double len = k[0][1] - k[1][1];
            if (len < 0.3) continue;
            int n = Math.max(1, (int) Math.round(len));
            List<double[]> col = new ArrayList<>();
            for (int i = 0; i <= n; i++) col.add(new double[] {k[0][0], k[0][1] - len * i / n, k[0][2]});
            for (float[] q : build(col, be.direction(), List.of(), h, detail)) out.add(q);
        }
        return out.toArray(new float[0][]);
    }

    static float[][] frames(RoofTrussBlockEntity be, int detail) {
        List<double[]> line = RoofTrussBlockEntity.roofLine(be.shape(), be.span(), be.rise(), be.turn(), be.isFull(), be.overhang());
        List<double[][]> posts = RoofTrussBlockEntity.posts(be.shape(), be.span(), be.rise(), be.turn());
        if (!be.isFull() || be.cover() == 0) return build(line, be.direction(), posts, H, detail);
        List<float[]> out = new ArrayList<>();
        double[] side = RoofTrussBlockEntity.side(be.turn());
        double[] offs = RoofTrussBlockEntity.frameOffsets(true, be.cover());
        for (int f = 0; f < offs.length; f++) {
            boolean end = f == 0 || f == offs.length - 1;
            List<double[][]> moved = new ArrayList<>();
            for (double[][] p : posts) moved.add(new double[][] {{p[0][0] + side[0] * offs[f], p[0][1], p[0][2] + side[2] * offs[f]}, {p[1][0] + side[0] * offs[f], p[1][1], p[1][2] + side[2] * offs[f]}});
            for (float[] q : build(RoofTrussBlockEntity.shifted(line, side, offs[f]), be.direction(), moved, be.isHeavy() && end ? H * RoofTrussBlockEntity.HEAVY : H, detail)) out.add(q);
        }
        double[] a = RoofTrussBlockEntity.apex(line, be.shape());
        double[] b = {a[0] + side[0] * be.cover(), a[1], a[2] + side[2] * be.cover()};
        Direction ridge = be.cover() > 0 ? be.direction().getClockWise() : be.direction().getCounterClockWise();
        for (float[] q : build(List.of(a, b), ridge, List.of(), be.isHeavy() ? H * RoofTrussBlockEntity.HEAVY : H, detail)) out.add(q);           // ridge bar
        int n = line.size() - 1;
        if (detail < 2) for (int i : new int[] {n / 4, 3 * n / 4}) {                                                                     // purlins
            if (i <= 0 || i >= n) continue;
            double[] p = line.get(i);
            double[] p0 = {p[0], p[1] + H + CHORD, p[2]}, p1 = {p0[0] + side[0] * be.cover(), p0[1], p0[2] + side[2] * be.cover()};
            RingTrussRenderer.tube(out, p0, p1, CHORD);
        }
        return out.toArray(new float[0][]);
    }

    /** {@code detail}: 0 everything, 1 without the bracing diagonals (seen from afar), 2 the chords and the end frames only. */
    static float[][] build(List<double[]> line, Direction dir, List<double[][]> posts, double H, int detail) {
        double CHORD = RoofTrussRenderer.CHORD * H / RoofTrussRenderer.H, BRACE = RoofTrussRenderer.BRACE * Math.max(1, H / RoofTrussRenderer.H);
        List<float[]> out = new ArrayList<>();
        int n = line.size();
        double[] side = {-dir.getStepZ(), 0, dir.getStepX()};
        // "up" of each segment, then mitred at the vertices so the chords meet at the ridge / along the arc
        double[][] seg = new double[n - 1][];
        for (int s = 0; s < n - 1; s++) seg[s] = norm(cross(side, sub(line.get(s + 1), line.get(s))));
        double[][] up = new double[n][];
        for (int i = 0; i < n; i++) {
            double[] a = seg[Math.max(0, i - 1)], b = seg[Math.min(n - 2, i)];
            double[] avg = norm(new double[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]});
            double k = 1 / Math.max(0.5, dot(avg, b));
            up[i] = new double[] {avg[0] * k, avg[1] * k, avg[2] * k};
        }
        for (int s = 0; s < n - 1; s++) {
            double[] p0 = line.get(s), p1 = line.get(s + 1), u0 = up[s], u1 = up[s + 1];
            for (double a : new double[] {-H, H})
                for (double b : new double[] {-H, H}) RingTrussRenderer.tube(out, at(p0, u0, a, side, b), at(p1, u1, a, side, b), CHORD);   // chords
            if (detail >= 1) continue;
            boolean flip = s % 2 == 0;
            for (double b : new double[] {-H, H})                                                                                       // side diagonals
                RingTrussRenderer.tube(out, at(p0, u0, flip ? -H : H, side, b), at(p1, u1, flip ? H : -H, side, b), BRACE);
            for (double a : new double[] {-H, H})                                                                                       // top / bottom diagonals
                RingTrussRenderer.tube(out, at(p0, u0, a, side, flip ? -H : H), at(p1, u1, a, side, flip ? H : -H), BRACE);
        }
        for (int i = 0; i < n; i++) {                                                                                                     // posts and rungs
            if (detail >= 2 && i != 0 && i != n - 1 && i % 4 != 0) continue;
            double[] p = line.get(i), u = up[i];
            double t = (i == 0 || i == n - 1) ? CHORD : BRACE;
            for (double b : new double[] {-H, H}) RingTrussRenderer.tube(out, at(p, u, -H, side, b), at(p, u, H, side, b), t);
            for (double a : new double[] {-H, H}) RingTrussRenderer.tube(out, at(p, u, a, side, -H), at(p, u, a, side, H), t);
        }
        for (double[][] post : posts) {                                                                                                // arc on posts
            for (double b : new double[] {-H, H}) {
                double[] top = {post[0][0] + side[0] * b, post[0][1], post[0][2] + side[2] * b};
                double[] bottom = {post[1][0] + side[0] * b, post[1][1], post[1][2] + side[2] * b};
                RingTrussRenderer.tube(out, top, bottom, CHORD);
            }
            double[] mid = {post[0][0], post[1][1] + (post[0][1] - post[1][1]) * 0.5, post[0][2]};
            RingTrussRenderer.tube(out, new double[] {mid[0] + side[0] * -H, mid[1], mid[2] + side[2] * -H}, new double[] {mid[0] + side[0] * H, mid[1], mid[2] + side[2] * H}, BRACE);
        }
        return out.toArray(new float[0][]);
    }
}
