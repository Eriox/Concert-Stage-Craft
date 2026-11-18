package com.concertstage.client;

import com.concertstage.block.regie.RegieRoofBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * FOH tarp roof: the black PVC of the stage tarps, opaque, both faces, in 1-block tiles. Gable roof (ridge along the long side), flat roof
 * or single slope, closed at the ends, with a valance all round.
 */
public class RegieRoofRenderer implements BlockEntityRenderer<RegieRoofBlockEntity> {


    private record Cached(String key, List<double[]> mesh) {}
    private static final java.util.Map<RegieRoofBlockEntity, Cached> CACHE = new java.util.WeakHashMap<>();

    public RegieRoofRenderer(BlockEntityRendererProvider.Context context) {}

    private static final java.util.Map<String, TextureAtlasSprite> SPRITES = new java.util.concurrent.ConcurrentHashMap<>();

    /** Resource reload: the sprites move in the atlas. */
    public static void clear() {
        SPRITES.clear();
        CACHE.clear();
    }

    @Override
    public void render(RegieRoofBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null || be.x1() - be.x0() < 0.1 || be.z1() - be.z0() < 0.1) return;
        if (!ClientCulling.visible(be, be.getRenderBoundingBox())) return;                  // off screen (Fabric; Forge checks it itself)
        TextureAtlasSprite cloth = SPRITES.computeIfAbsent(be.colorName(), c -> Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS)
            .getSprite(new ResourceLocation("concertstage", "block/stage_tarp_" + c)));
        VertexConsumer vc = GpuMesh.cutout(buffers);
        int lit = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above());
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        String key = be.x0() + "/" + be.z0() + "/" + be.x1() + "/" + be.z1() + "/" + be.eave() + "/" + be.shape() + "/" + be.pitch() + "/" + be.valance20();
        Cached cached = CACHE.get(be);
        if (cached == null || !cached.key.equals(key)) CACHE.put(be, cached = new Cached(key, mesh(be)));     // built once per change, not every frame
        // rev. 20: the roof stops the light of the fixtures (LightBlockers): one face of each quad
        java.util.List<double[]> mesh = cached.mesh;
        double[][] quads = new double[(mesh.size() + 1) / 2][];
        for (int i = 0, n = 0; i < mesh.size(); i += 2, n++) {
            double[] q = mesh.get(i), o = new double[12];
            for (int v = 0; v < 4; v++) { o[v * 3] = q[v * 5]; o[v * 3 + 1] = q[v * 5 + 1]; o[v * 3 + 2] = q[v * 5 + 2]; }
            quads[n] = o;
        }
        com.concertstage.client.light.LightBlockers.put(be, be.getBlockPos(), key, quads);
        for (double[] q : cached.mesh) {
            for (int j = 0; j < 4; j++) {
                int c = (int) (255 * RiggingMeshes.blockShade((float) q[20], (float) q[21], (float) q[22]));
                vc.vertex(m, (float) q[j * 5], (float) q[j * 5 + 1], (float) q[j * 5 + 2]).color(c, c, c, 255)
                    .uv(cloth.getU(q[j * 5 + 3]), cloth.getV(q[j * 5 + 4])).uv2(lit).normal(nm, (float) q[20], (float) q[21], (float) q[22]).endVertex();
            }
        }
    }

    /** Height of the roof above the eave at the distance {@code t} across the span {@code s}. */
    static double height(int shape, double t, double s, double rise) {
        return switch (shape) {
            case RegieRoofBlockEntity.FLAT -> 0;
            case RegieRoofBlockEntity.MONO -> rise * t / s;
            default -> rise * (1 - Math.abs(2 * t / s - 1));
        };
    }

    /** Quads: 4 × (x, y, z, u, v) then the normal; each one twice, one per face. */
    static List<double[]> mesh(RegieRoofBlockEntity be) {
        List<double[]> out = new ArrayList<>();
        boolean alongX = be.x1() - be.x0() >= be.z1() - be.z0();             // ridge along the long side
        double a0 = alongX ? be.x0() : be.z0(), a1 = alongX ? be.x1() : be.z1();  // along the ridge
        double b0 = alongX ? be.z0() : be.x0(), b1 = alongX ? be.z1() : be.x1();  // across
        double s = b1 - b0, e = be.eave() + 0.02;
        int shape = be.shape();
        double f = be.pitchFactor();
        double rise = shape == RegieRoofBlockEntity.MONO ? Math.max(0.2, Math.min(1.4, s * f * 0.65)) : Math.max(0.25, Math.min(2.4, s * f));
        List<Double> across = cuts(b0, b1);
        if (shape == RegieRoofBlockEntity.GABLE && !across.contains(b0 + s / 2)) { across.add(b0 + s / 2); across.sort(Double::compare); }
        List<Double> along = cuts(a0, a1);
        // roof
        for (int i = 0; i + 1 < along.size(); i++)
            for (int k = 0; k + 1 < across.size(); k++) {
                double p0 = along.get(i), p1 = along.get(i + 1), q0 = across.get(k), q1 = across.get(k + 1);
                double h0 = e + height(shape, q0 - b0, s, rise), h1 = e + height(shape, q1 - b0, s, rise);
                double len = Math.hypot(q1 - q0, h1 - h0);
                double[][] v = {{p0, h0, q0}, {p1, h0, q0}, {p1, h1, q1}, {p0, h1, q1}};
                double[][] uv = {{0, 0}, {(p1 - p0) * 16, 0}, {(p1 - p0) * 16, Math.min(16, len * 16)}, {0, Math.min(16, len * 16)}};
                add(out, v, uv, alongX);
            }
        // ends (gables) and the high side of a single slope: from the eave up to the roof, column by column
        for (int end = 0; end < 2; end++) {
            double p = end == 0 ? a0 : a1;
            for (int k = 0; k + 1 < across.size(); k++) {
                double q0 = across.get(k), q1 = across.get(k + 1);
                double h0 = e + height(shape, q0 - b0, s, rise), h1 = e + height(shape, q1 - b0, s, rise);
                if (Math.max(h0, h1) - e < 0.01) continue;
                double[][] v = {{p, e, q0}, {p, e, q1}, {p, h1, q1}, {p, h0, q0}};
                double[][] uv = {{0, 16}, {(q1 - q0) * 16, 16}, {(q1 - q0) * 16, 16 - Math.min(16, (h1 - e) * 16)}, {0, 16 - Math.min(16, (h0 - e) * 16)}};
                add(out, v, uv, alongX);
            }
        }
        if (shape == RegieRoofBlockEntity.MONO)
            for (int i = 0; i + 1 < along.size(); i++) {
                double p0 = along.get(i), p1 = along.get(i + 1), top = e + rise;
                double[][] v = {{p0, e, b1}, {p1, e, b1}, {p1, top, b1}, {p0, top, b1}};
                add(out, v, new double[][]{{0, 16}, {(p1 - p0) * 16, 16}, {(p1 - p0) * 16, 16 - rise * 16}, {0, 16 - rise * 16}}, alongX);
            }
        // valance all round, under the eave
        double low = e - be.valance();
        if (be.valance() > 0.01)
        for (int side = 0; side < 2; side++) {
            double q = side == 0 ? b0 : b1;
            for (int i = 0; i + 1 < along.size(); i++) {
                double p0 = along.get(i), p1 = along.get(i + 1);
                add(out, new double[][]{{p0, low, q}, {p1, low, q}, {p1, e, q}, {p0, e, q}},
                    new double[][]{{0, 16}, {(p1 - p0) * 16, 16}, {(p1 - p0) * 16, 12}, {0, 12}}, alongX);
            }
            double p = side == 0 ? a0 : a1;
            for (int k = 0; k + 1 < across.size(); k++) {
                double q0 = across.get(k), q1 = across.get(k + 1);
                add(out, new double[][]{{p, low, q0}, {p, low, q1}, {p, e, q1}, {p, e, q0}},
                    new double[][]{{0, 16}, {(q1 - q0) * 16, 16}, {(q1 - q0) * 16, 12}, {0, 12}}, alongX);
            }
        }
        return out;
    }

    private static List<Double> cuts(double from, double to) {
        List<Double> c = new ArrayList<>();
        c.add(from);
        for (double v = Math.floor(from) + 1; v < to - 1e-3; v++) if (v > from + 1e-3) c.add(v);
        c.add(to);
        return c;
    }

    /** One tile, both faces; (along, y, across) to (x, y, z). */
    private static void add(List<double[]> out, double[][] v, double[][] uv, boolean alongX) {
        double[][] w = new double[4][];
        for (int i = 0; i < 4; i++) w[i] = alongX ? new double[]{v[i][0], v[i][1], v[i][2]} : new double[]{v[i][2], v[i][1], v[i][0]};
        double ux = w[1][0] - w[0][0], uy = w[1][1] - w[0][1], uz = w[1][2] - w[0][2];
        double vx = w[3][0] - w[0][0], vy = w[3][1] - w[0][1], vz = w[3][2] - w[0][2];
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx, l = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-9) { ux = w[2][0] - w[1][0]; uy = w[2][1] - w[1][1]; uz = w[2][2] - w[1][2]; nx = uy * vz - uz * vy; ny = uz * vx - ux * vz; nz = ux * vy - uy * vx; l = Math.sqrt(nx * nx + ny * ny + nz * nz); if (l < 1e-9) return; }
        nx /= l; ny /= l; nz /= l;
        for (int face = 0; face < 2; face++) {
            double[] q = new double[23];
            for (int i = 0; i < 4; i++) {
                int j = face == 0 ? i : 3 - i;
                q[i * 5] = w[j][0]; q[i * 5 + 1] = w[j][1]; q[i * 5 + 2] = w[j][2]; q[i * 5 + 3] = uv[j][0]; q[i * 5 + 4] = uv[j][1];
            }
            double sgn = face == 0 ? 1 : -1;
            q[20] = nx * sgn; q[21] = ny * sgn; q[22] = nz * sgn;
            out.add(q);
        }
    }

    @Override public boolean shouldRenderOffScreen(RegieRoofBlockEntity be) { return true; }
    @Override public int getViewDistance() { return RenderQuality.distance(128); }
}
