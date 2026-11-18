package com.concertstage.client.light;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Rev. 20 — the sheets CSC draws with a renderer and that have no block of their own where they are: the PVC of the stage roofs
 * (RoofTruss) and of the FOH roofs (RegieRoof). Seen in game on the author's stage (CraftCity, 11 November): the light rays of the
 * fixtures only met blocks, so every beam aimed at the roof went on through the sheet into the sky (the ribbons and the end of the
 * beams in the haze), and its light was laid on whatever was above.
 *
 * <p>The renderers give their sheet here (world coordinates, one face per quad) when it changes; each quad is filed under every block
 * cell its box touches, so the ray of a fixture, which already walks the cells it crosses ({@link FixtureRenderer#clipRig}), tests
 * only the few quads of those cells. The shadow maps of the beams draw them too ({@link BeamShadows}).
 */
public final class LightBlockers {
    private LightBlockers() {}

    private record Sheet(String key, double[][] quads) {}

    private static final Map<Object, Sheet> SHEETS = new java.util.WeakHashMap<>();
    private static Map<Long, List<double[]>> cells = new HashMap<>();
    private static boolean dirty;
    static int quadCount;

    /** A renderer's sheet: {@code quads} are 4 × (x, y, z) relative to {@code origin}; {@code key} changes when the sheet changes. */
    public static void put(Object owner, BlockPos origin, String key, double[][] quads) {
        Sheet s = SHEETS.get(owner);
        if (s != null && s.key.equals(key)) return;
        double[][] w = new double[quads.length][];
        for (int i = 0; i < quads.length; i++) {
            double[] q = quads[i], o = new double[12];
            for (int k = 0; k < 4; k++) {
                o[k * 3] = q[k * 3] + origin.getX();
                o[k * 3 + 1] = q[k * 3 + 1] + origin.getY();
                o[k * 3 + 2] = q[k * 3 + 2] + origin.getZ();
            }
            w[i] = o;
        }
        SHEETS.put(owner, new Sheet(key, w));
        dirty = true;
    }

    public static void remove(Object owner) {
        if (SHEETS.remove(owner) != null) dirty = true;
    }

    /** Sheets of block entities gone (broken, chunk unloaded, other world) are forgotten (called every few seconds). */
    static void prune() {
        var lvl = net.minecraft.client.Minecraft.getInstance().level;
        if (SHEETS.keySet().removeIf(o -> o instanceof net.minecraft.world.level.block.entity.BlockEntity be && (be.isRemoved() || be.getLevel() != lvl)))
            dirty = true;
    }

    public static void clear() {
        SHEETS.clear();
        cells = new HashMap<>();
        dirty = false;
        quadCount = 0;
    }

    private static void index() {
        if (!dirty) return;
        dirty = false;
        Map<Long, List<double[]>> m = new HashMap<>();
        int n = 0;
        for (Sheet s : SHEETS.values()) for (double[] q : s.quads) {
            n++;
            double x0 = Math.min(Math.min(q[0], q[3]), Math.min(q[6], q[9])), x1 = Math.max(Math.max(q[0], q[3]), Math.max(q[6], q[9]));
            double y0 = Math.min(Math.min(q[1], q[4]), Math.min(q[7], q[10])), y1 = Math.max(Math.max(q[1], q[4]), Math.max(q[7], q[10]));
            double z0 = Math.min(Math.min(q[2], q[5]), Math.min(q[8], q[11])), z1 = Math.max(Math.max(q[2], q[5]), Math.max(q[8], q[11]));
            for (int x = (int) Math.floor(x0 - 1e-4); x <= (int) Math.floor(x1 + 1e-4); x++)
                for (int y = (int) Math.floor(y0 - 1e-4); y <= (int) Math.floor(y1 + 1e-4); y++)
                    for (int z = (int) Math.floor(z0 - 1e-4); z <= (int) Math.floor(z1 + 1e-4); z++)
                        m.computeIfAbsent(BlockPos.asLong(x, y, z), k -> new ArrayList<>(2)).add(q);
        }
        cells = m;
        quadCount = n;
    }

    /** The first sheet the segment from-to meets inside the cell {@code pos}, or null. */
    static BlockHitResult clip(BlockPos pos, Vec3 from, Vec3 to) {
        index();
        if (cells.isEmpty()) return null;
        List<double[]> list = cells.get(pos.asLong());
        if (list == null) return null;
        double best = Double.MAX_VALUE;
        double[] bestN = null;
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        for (double[] q : list) {
            for (int t = 0; t < 2; t++) {
                int a = 0, b = t == 0 ? 1 : 2, c = t == 0 ? 2 : 3;
                double hit = triangle(from, dx, dy, dz, q, a, b, c);
                if (hit >= 0 && hit < best) {
                    // keep only a hit inside this cell (the quad is filed in every cell it touches)
                    double px = from.x + dx * hit, py = from.y + dy * hit, pz = from.z + dz * hit;
                    if (px < pos.getX() - 1e-4 || px > pos.getX() + 1 + 1e-4 || py < pos.getY() - 1e-4 || py > pos.getY() + 1 + 1e-4
                        || pz < pos.getZ() - 1e-4 || pz > pos.getZ() + 1 + 1e-4) continue;
                    best = hit;
                    bestN = q;
                }
            }
        }
        if (bestN == null) return null;
        Vec3 at = new Vec3(from.x + dx * best, from.y + dy * best, from.z + dz * best);
        double ux = bestN[3] - bestN[0], uy = bestN[4] - bestN[1], uz = bestN[5] - bestN[2];
        double vx = bestN[6] - bestN[0], vy = bestN[7] - bestN[1], vz = bestN[8] - bestN[2];
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        if (nx * dx + ny * dy + nz * dz > 0) { nx = -nx; ny = -ny; nz = -nz; }   // the face looking at the light
        return new BlockHitResult(at, Direction.getNearest(nx, ny, nz), pos, false);
    }

    /** Möller-Trumbore: the fraction 0..1 of the segment where it meets the triangle (a, b, c) of {@code q}, or -1. */
    private static double triangle(Vec3 o, double dx, double dy, double dz, double[] q, int a, int b, int c) {
        double ax = q[a * 3], ay = q[a * 3 + 1], az = q[a * 3 + 2];
        double e1x = q[b * 3] - ax, e1y = q[b * 3 + 1] - ay, e1z = q[b * 3 + 2] - az;
        double e2x = q[c * 3] - ax, e2y = q[c * 3 + 1] - ay, e2z = q[c * 3 + 2] - az;
        double px = dy * e2z - dz * e2y, py = dz * e2x - dx * e2z, pz = dx * e2y - dy * e2x;
        double det = e1x * px + e1y * py + e1z * pz;
        if (Math.abs(det) < 1e-12) return -1;
        double inv = 1.0 / det;
        double tx = o.x - ax, ty = o.y - ay, tz = o.z - az;
        double u = (tx * px + ty * py + tz * pz) * inv;
        if (u < 0 || u > 1) return -1;
        double qx = ty * e1z - tz * e1y, qy = tz * e1x - tx * e1z, qz = tx * e1y - ty * e1x;
        double v = (dx * qx + dy * qy + dz * qz) * inv;
        if (v < 0 || u + v > 1) return -1;
        double t = (e2x * qx + e2y * qy + e2z * qz) * inv;
        return t >= 1e-6 && t <= 1 ? t : -1;
    }

    /** Every quad whose box meets the box x0..x1 (world), for the shadow maps. */
    static void forEach(double x0, double y0, double z0, double x1, double y1, double z1, java.util.function.Consumer<double[]> out) {
        for (Sheet s : SHEETS.values()) for (double[] q : s.quads) {
            double qx0 = Math.min(Math.min(q[0], q[3]), Math.min(q[6], q[9])), qx1 = Math.max(Math.max(q[0], q[3]), Math.max(q[6], q[9]));
            if (qx1 < x0 || qx0 > x1) continue;
            double qy0 = Math.min(Math.min(q[1], q[4]), Math.min(q[7], q[10])), qy1 = Math.max(Math.max(q[1], q[4]), Math.max(q[7], q[10]));
            if (qy1 < y0 || qy0 > y1) continue;
            double qz0 = Math.min(Math.min(q[2], q[5]), Math.min(q[8], q[11])), qz1 = Math.max(Math.max(q[2], q[5]), Math.max(q[8], q[11]));
            if (qz1 < z0 || qz0 > z1) continue;
            out.accept(q);
        }
    }
}
