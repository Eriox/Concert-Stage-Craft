package com.concertstage.client.light;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.2.0 rev. 18 — how a hung fixture is fixed to the truss above it, as on a real rig (pure maths, no Minecraft; tested off game by
 * MountTest).
 *
 * <p>Real hardware it reproduces (omega bracket bolted under the fixture base, two hook clamps / half couplers closing around a 48–51
 * mm tube, a hanging adaptor bar with two half couplers across the chords of a box truss):
 * <ul>
 * <li>{@link #BAR} — two parallel bottom chords on each side of the fixture (box, square, triangular trusses): a hanging bar across
 *     both chords under them, held by a half coupler on each chord; the omega bracket hangs from the bar by two hook clamps; the
 *     fixture stays centred under the truss;</li>
 * <li>{@link #TUBE} — one tube over the fixture (ladder truss, the edge of a truss, round truss, single pipe): the omega bracket hangs
 *     straight from that tube by its two hook clamps, the fixture centred under the tube;</li>
 * <li>{@link #PLATE} — anything else (a base plate, a beam, a block): the omega bracket is bolted under its lowest face.</li>
 * </ul>
 * The fixture top (the omega bracket's underside) is where the model of the fixture starts. Coordinates: blocks, relative to the
 * origin of the fixture's own cell (the support cell is y 1..2).
 */
public final class MountGeometry {
    public static final int NONE = 0, BAR = 1, TUBE = 2, PLATE = 3;
    public static final int DARK = 0x24272B, STEEL = 0xA9AFB7, SAFETY = 0xC9CDD2;

    /** A straight horizontal tube: axis 0 = X, 2 = Z; y range, across range (Z for an X tube), along range. */
    public record Tube(int axis, float yMin, float yMax, float cMin, float cMax, float aMin, float aMax) {
        public float radius() { return Math.min(yMax - yMin, cMax - cMin) / 2; }
        public float y() { return (yMin + yMax) / 2; }
        public float c() { return (cMin + cMax) / 2; }
    }

    /** The chosen fixing: kind, top of the fixture (y), horizontal offset of its centre, and the triangles of the hardware. */
    public record Layout(int kind, float top, float ox, float oz, List<float[]> tris, List<Integer> colors, String key) {}

    private MountGeometry() {}

    /**
     * @param tubes horizontal tubes of the support (any cell coordinates; only those over the fixture count)
     * @param plateBottom lowest face of the support over the fixture (y), NaN when there is none
     * @param scale size of the fixture (its render scale, 1.95 for the VORTEX)
     */
    public static Layout plan(List<Tube> tubes, float plateBottom, float scale) {
        // tubes in the support cell that run over the centre of the fixture
        List<Tube> over = new ArrayList<>();
        for (Tube t : tubes) {
            if (t.yMin() < 0.97f || t.yMin() > 2f) continue;
            if (t.aMin() > 0.4f || t.aMax() < 0.6f) continue;
            if (t.c() < -0.3f || t.c() > 1.3f) continue;
            over.add(t);
        }
        float s = Math.max(.6f, Math.min(1.4f, scale / 1.95f));
        if (!over.isEmpty()) {
            float lowest = Float.MAX_VALUE;
            for (Tube t : over) lowest = Math.min(lowest, t.yMin());
            List<Tube> bottom = new ArrayList<>();
            for (Tube t : over) if (t.yMin() < lowest + 0.06f) bottom.add(t);
            // a pair of parallel chords, one on each side of the centre: the widest pair wins
            // main chords first (they run the whole cell); the short braces between them are used only when there are no chords
            List<Tube> chords = new ArrayList<>();
            for (Tube t : bottom) if (t.aMax() - t.aMin() >= 0.9f) chords.add(t);
            if (!chords.isEmpty()) bottom = chords;
            Tube bestA = null, bestB = null;
            float bestGap = 0;
            for (int axis : new int[] {0, 2}) {
                Tube left = null, right = null;
                for (Tube t : bottom) {
                    if (t.axis() != axis) continue;
                    if (t.c() <= 0.5f && (left == null || t.c() > left.c())) left = t;
                    if (t.c() >= 0.5f && (right == null || t.c() < right.c())) right = t;
                }
                if (left != null && right != null && left != right && right.c() - left.c() >= 0.42f && right.c() - left.c() > bestGap) {
                    bestGap = right.c() - left.c(); bestA = left; bestB = right;
                }
            }
            if (bestA != null) return bar(bestA, bestB, s);
            Tube near = null;
            for (Tube t : bottom) if (near == null || Math.abs(t.c() - 0.5f) < Math.abs(near.c() - 0.5f)) near = t;
            if (near != null && Math.abs(near.c() - 0.5f) <= 0.5f) return tube(near, s);
        }
        if (Float.isFinite(plateBottom) && plateBottom >= 0.97f && plateBottom <= 2f) return plate(plateBottom, s);
        return new Layout(NONE, 1f, 0, 0, List.of(), List.of(), "none");
    }

    // ------------------------------------------------------------ layouts
    private static Layout bar(Tube a, Tube b, float s) {
        Mesh m = new Mesh();
        int axis = a.axis(), across = axis == 0 ? 2 : 0;
        float r = Math.max(a.radius(), b.radius());
        float rb = Math.max(0.03f, 0.7f * r);                                // hanging bar, a little thinner than the chords
        float chordBottom = Math.min(a.yMin(), b.yMin());
        float barY = chordBottom - rb - 0.004f;
        float c0 = a.c() - r - 0.05f, c1 = b.c() + r + 0.05f;
        // the bar across the chords, at the centre of the fixture along them
        m.tube(across, barY, 0.5f, c0, c1, rb, 12, DARK, true);
        // a half coupler on each chord, and its jaw around the bar
        for (Tube t : new Tube[] {a, b}) {
            m.ring(axis, t.y(), t.c(), 0.5f, t.radius(), 0.22f * t.radius() + 0.006f, 0.9f * t.radius() + 0.02f, DARK);
            m.ring(across, barY, 0.5f, t.c(), rb, 0.22f * rb + 0.006f, 0.9f * rb + 0.02f, DARK);
            m.box(axis, t.c(), 0.5f, t.y() + t.radius() + 0.02f, 0.03f, 0.022f, STEEL);  // bolt head on top of the chord
        }
        // omega bracket on two hook clamps hung from the bar (hooks along the bar, inside the chords)
        float half = Math.min(0.18f * s, (b.c() - a.c()) / 2 - r - 0.07f);
        half = Math.max(0.06f, half);
        float top = m.omega(across, barY, rb, 0.5f, 0.5f - half, 0.5f + half, s);
        m.finish();
        return new Layout(BAR, top, 0, 0, m.tris, m.colors, "bar/" + axis + "/" + q(a.c()) + "/" + q(b.c()) + "/" + q(chordBottom) + "/" + q(r) + "/" + q(s));
    }

    private static Layout tube(Tube t, float s) {
        Mesh m = new Mesh();
        int axis = t.axis();
        float a0 = Math.max(t.aMin() + 0.04f, 0.5f - 0.18f * s), a1 = Math.min(t.aMax() - 0.04f, 0.5f + 0.18f * s);
        if (a1 - a0 < 0.12f) { a0 = 0.44f; a1 = 0.56f; }
        // the fixture hangs under the tube: its centre moves across to the tube (at most half a block)
        float shift = Math.max(-0.45f, Math.min(0.45f, t.c() - 0.5f));
        float top = m.omega(axis, t.y(), t.radius(), t.c(), a0, a1, s);
        float ox = axis == 2 ? shift : 0, oz = axis == 0 ? shift : 0;
        m.finish();
        return new Layout(TUBE, top, ox, oz, m.tris, m.colors, "tube/" + axis + "/" + q(t.c()) + "/" + q(t.y()) + "/" + q(t.radius()) + "/" + q(a0) + "/" + q(a1) + "/" + q(s));
    }

    private static Layout plate(float bottom, float s) {
        Mesh m = new Mesh();
        float w = 0.11f * s, l = 0.2f * s, th = 0.02f;
        m.cuboid(0.5f - l, bottom - th, 0.5f - w, 0.5f + l, bottom, 0.5f + w, DARK);
        for (int i = -1; i <= 1; i += 2) m.cuboid(0.5f + i * (l - 0.03f) - 0.012f, bottom - th - 0.012f, 0.488f, 0.5f + i * (l - 0.03f) + 0.012f, bottom - th, 0.512f, STEEL);
        m.finish();
        return new Layout(PLATE, bottom - th, 0, 0, m.tris, m.colors, "plate/" + q(bottom) + "/" + q(s));
    }

    private static String q(float v) { return String.valueOf(Math.round(v * 1000)); }

    // ------------------------------------------------------------ geometry
    /** Coloured triangles (x, y, z per vertex), grouped per colour. */
    static final class Mesh {
        final List<float[]> tris = new ArrayList<>();
        final List<Integer> colors = new ArrayList<>();
        private final java.util.Map<Integer, FloatList> by = new java.util.LinkedHashMap<>();

        private FloatList list(int color) {
            FloatList l = by.get(color);
            if (l == null) { l = new FloatList(); by.put(color, l); }
            return l;
        }

        void tri(int color, float[] a, float[] b, float[] c) {
            FloatList l = list(color);
            l.add(a); l.add(b); l.add(c);
        }

        void quad(int color, float[] a, float[] b, float[] c, float[] d) {
            tri(color, a, b, c);
            tri(color, a, c, d);
        }

        /** Fills {@link #tris} / {@link #colors} (one array per colour). */
        Mesh finish() {
            tris.clear(); colors.clear();
            for (var e : by.entrySet()) { colors.add(e.getKey()); tris.add(e.getValue().toArray()); }
            return this;
        }

        /** Point at (along, y, across) for a tube along {@code axis}. */
        static float[] p(int axis, float along, float y, float across) {
            return axis == 0 ? new float[] {along, y, across} : new float[] {across, y, along};
        }

        /** A closed tube of {@code seg} sides along {@code axis}, centre (y, c), from along a0 to a1. */
        void tube(int axis, float y, float alongMid, float a0, float a1, float r, int seg, int color, boolean caps) {
            // note: for the bar, a0 / a1 run across the chords (the bar's own axis is {@code axis})
            for (int i = 0; i < seg; i++) {
                double t0 = 2 * Math.PI * i / seg, t1 = 2 * Math.PI * (i + 1) / seg;
                float y0 = y + (float) (Math.sin(t0) * r), c0 = alongMid + (float) (Math.cos(t0) * r);
                float y1 = y + (float) (Math.sin(t1) * r), c1 = alongMid + (float) (Math.cos(t1) * r);
                quad(color, p(axis, a0, y0, c0), p(axis, a1, y0, c0), p(axis, a1, y1, c1), p(axis, a0, y1, c1));
                if (caps) {
                    tri(color, p(axis, a0, y, alongMid), p(axis, a0, y0, c0), p(axis, a0, y1, c1));
                    tri(color, p(axis, a1, y, alongMid), p(axis, a1, y1, c1), p(axis, a1, y0, c0));
                }
            }
        }

        /**
         * A clamp ring around a tube along {@code axis} (centre y, across c), at {@code along}, inner radius r, thickness th, width w:
         * outer and inner faces and both sides, 12 segments.
         */
        void ring(int axis, float y, float c, float along, float r, float th, float w, int color) {
            int seg = 12;
            float ri = r + 0.002f, ro = r + th, a0 = along - w / 2, a1 = along + w / 2;
            for (int i = 0; i < seg; i++) {
                double t0 = 2 * Math.PI * i / seg, t1 = 2 * Math.PI * (i + 1) / seg;
                float s0 = (float) Math.sin(t0), k0 = (float) Math.cos(t0), s1 = (float) Math.sin(t1), k1 = (float) Math.cos(t1);
                float[] o00 = p(axis, a0, y + s0 * ro, c + k0 * ro), o01 = p(axis, a1, y + s0 * ro, c + k0 * ro);
                float[] o10 = p(axis, a0, y + s1 * ro, c + k1 * ro), o11 = p(axis, a1, y + s1 * ro, c + k1 * ro);
                float[] i00 = p(axis, a0, y + s0 * ri, c + k0 * ri), i01 = p(axis, a1, y + s0 * ri, c + k0 * ri);
                float[] i10 = p(axis, a0, y + s1 * ri, c + k1 * ri), i11 = p(axis, a1, y + s1 * ri, c + k1 * ri);
                quad(color, o00, o01, o11, o10);
                quad(color, i00, i10, i11, i01);
                quad(color, i00, o00, o10, i10);
                quad(color, i01, i11, o11, o01);
            }
        }

        /** An axis-aligned box. */
        void cuboid(float x0, float y0, float z0, float x1, float y1, float z1, int color) {
            float[][] v = {{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
            int[][] f = {{0, 3, 2, 1}, {4, 5, 6, 7}, {0, 1, 5, 4}, {3, 7, 6, 2}, {0, 4, 7, 3}, {1, 2, 6, 5}};
            for (int[] q : f) quad(color, v[q[0]], v[q[1]], v[q[2]], v[q[3]]);
        }

        /** A small box centred on (across c, along a, y) for a tube along {@code axis}: size w across / along, h high. */
        void box(int axis, float c, float a, float y, float w, float h, int color) {
            float[] lo = p(axis, a - w / 2, y, c - w / 2), hi = p(axis, a + w / 2, y + h, c + w / 2);
            cuboid(Math.min(lo[0], hi[0]), lo[1], Math.min(lo[2], hi[2]), Math.max(lo[0], hi[0]), hi[1], Math.max(lo[2], hi[2]), color);
        }

        /**
         * The omega bracket hung from a tube along {@code axis} (centre y, across c, radius r) by two hook clamps at along a0 and a1:
         * each hook closes around the tube, with its wing bolt and its shank down to the bracket. Returns the y of the bracket's
         * underside (where the fixture starts).
         */
        float omega(int axis, float y, float r, float c, float a0, float a1, float s) {
            float th = 0.22f * r + 0.006f, w = Math.max(0.035f, 0.75f * r);
            float shankTop = y - r - th, shankLen = Math.max(0.03f, 0.55f * r) + 0.02f * s;
            float plateTop = shankTop - shankLen, plateTh = 0.016f + 0.004f * s;
            for (float a : new float[] {a0, a1}) {
                ring(axis, y, c, a, r, th, w, DARK);
                // wing bolt on the side of the hook, steel
                float[] b0 = p(axis, a - 0.012f, y - 0.012f, c + r + th), b1 = p(axis, a + 0.012f, y + 0.012f, c + r + th + 0.045f);
                cuboid(Math.min(b0[0], b1[0]), b0[1], Math.min(b0[2], b1[2]), Math.max(b0[0], b1[0]), b1[1], Math.max(b0[2], b1[2]), STEEL);
                // shank from the bottom of the hook to the bracket
                box(axis, c, a, plateTop, Math.max(0.022f, 0.4f * w), shankLen + 0.002f, DARK);
            }
            // the bracket: a flat bar along the tube, raised ends (the omega), under both shanks
            float half = (a1 - a0) / 2 + 0.05f, mid = (a0 + a1) / 2, bw = 0.07f * s + 0.03f;
            float[] lo = p(axis, mid - half, plateTop - plateTh, c - bw / 2), hi = p(axis, mid + half, plateTop, c + bw / 2);
            cuboid(Math.min(lo[0], hi[0]), lo[1], Math.min(lo[2], hi[2]), Math.max(lo[0], hi[0]), hi[1], Math.max(lo[2], hi[2]), DARK);
            for (int e = -1; e <= 1; e += 2) {
                float ae = mid + e * (half - 0.012f);
                float[] l2 = p(axis, ae - 0.012f, plateTop - plateTh, c - bw / 2), h2 = p(axis, ae + 0.012f, plateTop + 0.03f, c + bw / 2);
                cuboid(Math.min(l2[0], h2[0]), l2[1], Math.min(l2[2], h2[2]), Math.max(l2[0], h2[0]), h2[1], Math.max(l2[2], h2[2]), DARK);
            }
            // quarter-turn fasteners into the fixture base, steel
            for (int e = -1; e <= 1; e += 2) box(axis, c, mid + e * half * 0.55f, plateTop - plateTh - 0.008f, 0.02f, 0.008f, STEEL);
            return plateTop - plateTh - 0.008f;
        }
    }

    /** Growable float array. */
    static final class FloatList {
        float[] a = new float[96];
        int n;
        void add(float[] v) {
            if (n + 3 > a.length) a = java.util.Arrays.copyOf(a, a.length * 2);
            a[n++] = v[0]; a[n++] = v[1]; a[n++] = v[2];
        }
        float[] toArray() { return java.util.Arrays.copyOf(a, n); }
    }
}
