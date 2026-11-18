package com.concertstage.client;

import com.concertstage.block.ring.CurvedTrussGeometry;
import com.concertstage.block.ring.RingTrussBlockEntity;
import com.concertstage.client.light.FixtureMeshes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws the circular trusses and, since 1.3.0, the curved trusses (CSC CURVED TRITRUSS / QUADTRUSS), from {@link CurvedTrussGeometry}:
 * round chords following the arc, posts, diagonals and couplers of the supplied Blender models (1.3.0 rework of the circle, which used
 * square tubes without couplers), square or triangular section, at constant tube thickness for any radius.
 *
 * <p>Performance: the geometry of a shape is built once (cached by section / radius / angle / bend / level of detail), kept in the
 * graphics card ({@link GpuMesh}, one draw call) and rebuilt only when the shape, the level of detail or the light changes. The level of
 * detail follows the distance (LOD0 to LOD3, as in the Blender files: 35 200 triangles for a 20-block circle up close, 1 760 far away),
 * see {@link com.concertstage.client.vision.VisionSettings#trussLod}; off-screen trusses are skipped (frustum culling).
 */
public class RingTrussRenderer implements BlockEntityRenderer<RingTrussBlockEntity> {
    /** Material colours: the aluminium of the straight trusses of the mod (so a curved piece continues a straight one), couplers, pins. */
    private static final int[][] ALU = {{162, 167, 174}, {150, 154, 160}, {173, 175, 177}};
    private static final int[][] BLACK = {{40, 40, 43}, {52, 53, 56}, {70, 71, 73}};

    /** Geometry by shape (CPU, float triangles), small LRU. */
    private static final Map<String, float[]> MESHES = new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, float[]> e) { return size() > 96; }
    };
    /** Graphics card copies by shape + finish + light + orientation. */
    private static final Map<String, GpuMesh> GPU = new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, GpuMesh> e) {
            if (size() <= 256) return false;
            if (e.getValue() != null) e.getValue().free();
            return true;
        }
    };
    /** Tilt of each ring at the previous frame: a ring that is tilting is drawn the old way until it stops. */
    private static final Map<RingTrussBlockEntity, Double> LAST_TILT = new java.util.WeakHashMap<>();

    public RingTrussRenderer(BlockEntityRendererProvider.Context context) {}

    public static void clear() { MESHES.clear(); GPU.values().forEach(m -> { if (m != null) m.free(); }); GPU.clear(); }

    /** Geometry key of a block entity at a level of detail. */
    private static String shapeKey(RingTrussBlockEntity be, int lod) {
        return be.isCurved()
            ? "c" + be.section() + "/" + be.radius2() + "/" + be.sweep() + "/" + be.bend() + "/" + be.facing().get2DDataValue() + "/" + lod
            : "r" + be.section() + "/" + be.radius2() + "/" + lod;
    }

    private static float[] mesh(RingTrussBlockEntity be, int lod) {
        return MESHES.computeIfAbsent(shapeKey(be, lod), k -> {
            CurvedTrussGeometry.Mesh m = new CurvedTrussGeometry.Mesh();
            if (be.isCurved()) {
                CurvedTrussGeometry.curved(m, lod, be.curveFrame(), be.sectionShape(), be.radius(), 0, Math.toRadians(be.sweep()), true);
            } else {
                CurvedTrussGeometry.ring(m, lod, CurvedTrussGeometry.Frame.of(new double[3], new double[] {1, 0, 0}, new double[] {0, 0, 1},
                    new double[] {0, 1, 0}), be.sectionShape(), be.radius());
            }
            com.concertstage.client.vision.VisionStats.trussBuilt(m.triangles());
            return m.data();
        });
    }

    @Override
    public void render(RingTrussBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) return;
        net.minecraft.world.phys.AABB box = be.getRenderBoundingBox();
        if (!ClientCulling.visible(be, box)) return;                                        // off screen (Fabric; Forge checks it itself)
        int lod = com.concertstage.client.vision.VisionSettings.trussLod(ClientCulling.distance(box));
        float[] mesh = mesh(be, lod);
        com.concertstage.client.vision.VisionStats.trussDrawn(mesh.length / 21);
        int lit = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos());
        int[][] colours = be.isBlack() ? BLACK : ALU;
        pose.pushPose();
        double tilt = 0;
        if (!be.isCurved()) {
            tilt = be.tiltAt(be.getLevel().getGameTime(), partial);
            double rad = Math.toRadians(tilt);
            // the ring hangs under its rigging point: its highest point stays just under the shackle (5 px from the bottom of the cell)
            double top = be.radius() * Math.sin(rad) + be.sectionHalf() * Math.cos(rad);
            pose.translate(0.5, 5.0 / 16 - top - be.chordRadius(), 0.5);
        }
        Double before = be.isCurved() ? Double.valueOf(0) : LAST_TILT.put(be, tilt);
        if (GpuMesh.enabled() && before != null && before == tilt) {
            String gk = shapeKey(be, lod) + (be.isBlack() ? "b" : "a") + lit + "/" + be.yaw() + "/" + tilt;
            GpuMesh gm = GPU.get(gk);
            if (!GpuMesh.usable(gm)) {
                PoseStack local = new PoseStack();
                if (!be.isCurved()) {
                    local.mulPose(Axis.YP.rotationDegrees(-be.yaw()));
                    local.mulPose(Axis.XP.rotationDegrees((float) tilt));
                }
                GpuMesh.Builder b = GpuMesh.builder();
                emit(b.consumer(FixtureMeshes.PALETTE, false, true), local.last().pose(), local.last().normal(), mesh, lit, colours);
                gm = b.build();
                GPU.put(gk, gm);
            }
            if (gm != null) {
                gm.draw(pose);
                pose.popPose();
                return;
            }
        }
        if (!be.isCurved()) {
            pose.mulPose(Axis.YP.rotationDegrees(-be.yaw()));
            pose.mulPose(Axis.XP.rotationDegrees((float) tilt));
        }
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(FixtureMeshes.PALETTE));
        emit(vc, pose.last().pose(), pose.last().normal(), mesh, lit, colours);
        pose.popPose();
    }

    /** Triangles as degenerate quads (the block / entity formats take quads): the 4th vertex repeats the 3rd. */
    private static void emit(VertexConsumer vc, Matrix4f m, Matrix3f nm, float[] d, int lit, int[][] colours) {
        float u = FixtureMeshes.WHITE_U, v = FixtureMeshes.WHITE_V;
        for (int t = 0; t + 21 <= d.length; t += 21) {
            int[] c = colours[Math.max(0, Math.min(2, (int) d[t + 6]))];
            for (int k = 0; k < 4; k++) {
                int o = t + Math.min(k, 2) * 7;
                vc.vertex(m, d[o], d[o + 1], d[o + 2]).color(c[0], c[1], c[2], 255).uv(u, v)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(lit).normal(nm, d[o + 3], d[o + 4], d[o + 5]).endVertex();
            }
        }
    }

    @Override
    public boolean shouldRenderOffScreen(RingTrussBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return RenderQuality.distance(192);
    }

    // ---------------------------------------------------------------- tube helper kept for the roof trusses (RoofTrussRenderer)
    static void tube(java.util.List<float[]> out, double[] p, double[] q, double t) {
        double[] d = {q[0] - p[0], q[1] - p[1], q[2] - p[2]};
        double len = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        if (len < 1e-6) return;
        for (int i = 0; i < 3; i++) d[i] /= len;
        double[] a = Math.abs(d[1]) < 0.9 ? new double[] {0, 1, 0} : new double[] {1, 0, 0};
        double[] u = normT(crossT(d, a)), w = normT(crossT(d, u));
        // extend the tube a little so the joints close
        double[] p0 = {p[0] - d[0] * t, p[1] - d[1] * t, p[2] - d[2] * t}, q0 = {q[0] + d[0] * t, q[1] + d[1] * t, q[2] + d[2] * t};
        double[][] side = {u, w, negT(u), negT(w)};
        for (int s = 0; s < 4; s++) {
            double[] e = side[s], f = side[(s + 1) % 4];
            double[] c0 = addT(p0, e, f, t), c1 = addT(q0, e, f, t), c2 = addT(q0, e, negT(f), t), c3 = addT(p0, e, negT(f), t);
            float[] quad = new float[32];
            double[][] cs = {c0, c1, c2, c3};
            float[][] uvs = {{0.1f, 0.1f}, {3.9f, 0.1f}, {3.9f, 3.9f}, {0.1f, 3.9f}};
            for (int k = 0; k < 4; k++) {
                quad[k * 8] = (float) cs[k][0]; quad[k * 8 + 1] = (float) cs[k][1]; quad[k * 8 + 2] = (float) cs[k][2];
                quad[k * 8 + 3] = uvs[k][0]; quad[k * 8 + 4] = uvs[k][1];
                quad[k * 8 + 5] = (float) e[0]; quad[k * 8 + 6] = (float) e[1]; quad[k * 8 + 7] = (float) e[2];
            }
            out.add(quad);
        }
    }

    private static double[] addT(double[] p, double[] e, double[] f, double t) {
        return new double[] {p[0] + (e[0] + f[0]) * t, p[1] + (e[1] + f[1]) * t, p[2] + (e[2] + f[2]) * t};
    }

    private static double[] crossT(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double[] normT(double[] v) {
        double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new double[] {v[0] / l, v[1] / l, v[2] / l};
    }

    private static double[] negT(double[] v) {
        return new double[] {-v[0], -v[1], -v[2]};
    }
}
