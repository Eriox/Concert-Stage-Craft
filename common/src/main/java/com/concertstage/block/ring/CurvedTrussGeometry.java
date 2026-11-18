package com.concertstage.block.ring;

import java.util.Arrays;

/**
 * CSC 1.3.0 - Dynamic Truss: the curved and circular trusses built from parameters (radius, angle, section, level of detail), with
 * the constants and the construction of the supplied Blender models ({@code source-assets/livevision_1_3_0/Scripts/csc_models.py},
 * function {@code curved}): chords following the arc at a constant section, posts and diagonals between stations spaced along the arc,
 * a coupler (8-sided lathe) and its pin at each chord end. The tubes keep their thickness whatever the radius: nothing is scaled.
 *
 * <p>The sections are the ones of the straight trusses of the mod (TriTruss X30D and QuadTruss H1, in 1/16 of a block). Levels of
 * detail as in the Blender files: LOD1 without pins and with fewer sides, LOD2 without diagonals nor couplers, LOD3 the chords only.
 * The triangle counts match the Blender variants exactly (tools/v13/check_truss_geometry: QUAD 90° R3 = 2688 / 1408 / 496 / 112).
 *
 * <p>Pure geometry (no Minecraft client class): the server uses {@link #centre} for the collision cells. Output: triangles, 7 floats
 * per vertex (x, y, z, nx, ny, nz, material), flat normals (the Blender files mark every edge of these tubes sharp: 45° and 60°
 * between faces, above their 38° auto-smooth angle).
 */
public final class CurvedTrussGeometry {
    private static final double U = 1.0 / 16;
    public static final int M_ALU = 0, M_COUPLER = 1, M_STEEL = 2;

    public enum Section {
        QUAD(new double[][] {{-6.5 * U, -6.5 * U}, {6.5 * U, -6.5 * U}, {6.5 * U, 6.5 * U}, {-6.5 * U, 6.5 * U}}, new int[][] {{0, 1}, {1, 2}, {2, 3}, {3, 0}},
            1.25 * U, .52 * U, 1.8 * U, 14.2 * U),
        TRI(new double[][] {{-4.64 * U, -4.018 * U}, {4.64 * U, -4.018 * U}, {0, 4.018 * U}}, new int[][] {{0, 1}, {1, 2}, {2, 0}},
            .95 * U, .42 * U, 1.6 * U, 14.4 * U);

        /** Chord positions in the section: (radial offset, offset along the section normal), metres. */
        public final double[][] pts;
        public final int[][] faces;
        /** Chord radius, diagonal radius, end margin, station pitch (metres). */
        public final double r, rd, margin, pitch;

        Section(double[][] pts, int[][] faces, double r, double rd, double margin, double pitch) {
            this.pts = pts; this.faces = faces; this.r = r; this.rd = rd; this.margin = margin; this.pitch = pitch;
        }

        /** Half extent of the section, chords included (collision reach). */
        public double reach() {
            double m = 0;
            for (double[] p : pts) m = Math.max(m, Math.max(Math.abs(p[0]), Math.abs(p[1])));
            return m + r;
        }
    }

    /** Frame of an arc: centre {@code c}, unit vectors {@code e1} (centre to the start point) and {@code e2} (tangent at the start), section normal {@code n}. */
    public record Frame(double[] c, double[] e1, double[] e2, double[] n) {
        public static Frame of(double[] c, double[] e1, double[] e2, double[] n) { return new Frame(c, e1, e2, n); }
    }

    /** Growing float buffer of triangles (7 floats per vertex). */
    public static final class Mesh {
        float[] d = new float[4096];
        int size;

        public float[] data() { return Arrays.copyOf(d, size); }
        public int triangles() { return size / 21; }

        void tri(double[] a, double[] b, double[] c, int mat) {
            double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
            double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx, l = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (l < 1e-12) { nx = 0; ny = 1; nz = 0; } else { nx /= l; ny /= l; nz /= l; }
            if (size + 21 > d.length) d = Arrays.copyOf(d, d.length * 2);
            for (double[] p : new double[][] {a, b, c}) {
                d[size++] = (float) p[0]; d[size++] = (float) p[1]; d[size++] = (float) p[2];
                d[size++] = (float) nx; d[size++] = (float) ny; d[size++] = (float) nz; d[size++] = mat;
            }
        }

        /** Polygon as a fan (Blender triangulates quads and n-gons the same way for the counts). */
        void poly(double[][] p, int mat) {
            for (int i = 1; i + 1 < p.length; i++) tri(p[0], p[i], p[i + 1], mat);
        }
    }

    private CurvedTrussGeometry() {}

    // ------------------------------------------------------------------ helpers (Blender csc_lib: sg, rod, lathe, arc)
    static int sg(int lod, int n) {
        if (lod == 0) return n;
        if (lod == 1) return n <= 12 ? Math.max(4, n / 2) : Math.max(8, n / 2);
        return lod == 2 ? Math.max(4, n / 3) : 4;
    }

    private static double[] add(double[] a, double[] b, double k) { return new double[] {a[0] + b[0] * k, a[1] + b[1] * k, a[2] + b[2] * k}; }
    private static double[] sub(double[] a, double[] b) { return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    private static double len(double[] a) { return Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); }
    private static double[] norm(double[] a) { double l = len(a); return l < 1e-12 ? new double[] {0, 1, 0} : new double[] {a[0] / l, a[1] / l, a[2] / l}; }
    private static double[] cross(double[] a, double[] b) { return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }

    /** Two unit vectors perpendicular to {@code d} (and to each other). */
    private static double[][] perp(double[] d) {
        double[] a = Math.abs(d[1]) < 0.9 ? new double[] {0, 1, 0} : new double[] {1, 0, 0};
        double[] u = norm(cross(d, a));
        return new double[][] {u, cross(d, u)};
    }

    /** Cylinder from a to b ({@code segs} sides, capped by two n-gons): Blender create_cone(cap_tris=False). */
    static void rod(Mesh m, int lod, int rodLod, double[] a, double[] b, double r, int segs, int mat) {
        if (lod > rodLod) return;
        double[] d = sub(b, a);
        if (len(d) < 1e-9) return;
        d = norm(d);
        int n = sg(lod, segs);
        double[][] uv = perp(d);
        double[][] ra = new double[n][], rb = new double[n][];
        for (int k = 0; k < n; k++) {
            double t = 2 * Math.PI * k / n;
            double[] o = add(add(new double[3], uv[0], Math.cos(t) * r), uv[1], Math.sin(t) * r);
            ra[k] = add(a, o, 1); rb[k] = add(b, o, 1);
        }
        for (int k = 0; k < n; k++) m.poly(new double[][] {ra[k], ra[(k + 1) % n], rb[(k + 1) % n], rb[k]}, mat);
        double[][] capA = new double[n][], capB = new double[n][];
        for (int k = 0; k < n; k++) { capA[k] = ra[n - 1 - k]; capB[k] = rb[k]; }
        m.poly(capA, mat);
        m.poly(capB, mat);
    }

    /** Revolved closed profile {(radius, z)} around {@code axis} at {@code o} (the couplers). */
    static void lathe(Mesh m, int lod, int latheLod, double[][] prof, double[] o, double[] axis, int segs, int mat) {
        if (lod > latheLod) return;
        int n = sg(lod, segs);
        double[][] uv = perp(axis);
        double[][][] rings = new double[prof.length][][];
        for (int i = 0; i < prof.length; i++) {
            rings[i] = new double[n][];
            for (int k = 0; k < n; k++) {
                double t = 2 * Math.PI * k / n;
                double[] p = add(o, axis, prof[i][1]);
                p = add(p, uv[0], prof[i][0] * Math.cos(t));
                rings[i][k] = add(p, uv[1], prof[i][0] * Math.sin(t));
            }
        }
        for (int i = 0; i < prof.length; i++) {
            double[][] A = rings[i], B = rings[(i + 1) % prof.length];
            for (int k = 0; k < n; k++) m.poly(new double[][] {A[k], A[(k + 1) % n], B[(k + 1) % n], B[k]}, mat);
        }
    }

    /** Chord following the arc: ring of {@code sides} vertices at each step, closed at both ends. Blender csc_lib.arc. */
    static void arc(Mesh m, int lod, Frame f, double rc, double z, double r, double a0, double a1) {
        int ns = new int[] {8, 6, 4, 4}[lod];
        double step = Math.toRadians(6);
        int k = Math.max(2, (int) (Math.abs(a1 - a0) / (step * new double[] {1, 1.5, 2.5, 5}[lod])) + 1);
        double[][][] rings = new double[k + 1][ns][];
        for (int i = 0; i <= k; i++) {
            double a = a0 + (a1 - a0) * i / k;
            double[] er = add(add(new double[3], f.e1, Math.cos(a)), f.e2, Math.sin(a));
            double[] centre = add(add(f.c, er, rc), f.n, z);
            for (int j = 0; j < ns; j++) {
                double t = 2 * Math.PI * (j + .5) / ns;
                rings[i][j] = add(add(centre, er, r * Math.cos(t)), f.n, r * Math.sin(t));
            }
        }
        for (int i = 0; i < k; i++)
            for (int j = 0; j < ns; j++)
                m.poly(new double[][] {rings[i][j], rings[i][(j + 1) % ns], rings[i + 1][(j + 1) % ns], rings[i + 1][j]}, M_ALU);
        double[][] first = new double[ns][];
        for (int j = 0; j < ns; j++) first[j] = rings[0][ns - 1 - j];
        m.poly(first, M_ALU);
        m.poly(rings[k], M_ALU);
    }

    /** Point of chord {@code i} at angle {@code t}. */
    private static double[] P(Frame f, Section s, double R, int i, double t) {
        double[] er = add(add(new double[3], f.e1, Math.cos(t)), f.e2, Math.sin(t));
        return add(add(f.c, er, R + s.pts[i][0]), f.n, s.pts[i][1]);
    }

    /** Centre of the section at angle {@code t} (collision cells, attach points). */
    public static double[] centre(Frame f, double R, double t) {
        double[] er = add(add(new double[3], f.e1, Math.cos(t)), f.e2, Math.sin(t));
        return add(f.c, er, R);
    }

    // ------------------------------------------------------------------ the truss
    /** One curved truss from angle {@code a0} over {@code sw} (radians, signed), Blender csc_models.curved. Returns the station count. */
    public static int curved(Mesh m, int lod, Frame f, Section s, double R, double a0, double sw, boolean couplers) {
        double[][] pts = s.pts;
        for (double[] p : pts) arc(m, lod, f, R + p[0], p[1], s.r, a0, a0 + sw);
        double asw = Math.abs(sw), sign = Math.signum(sw);
        double mg = s.margin / R;
        int n = Math.max(1, (int) Math.round((R * asw - 2 * s.margin) / s.pitch));
        double[] ts = new double[n + 1];
        for (int k = 0; k <= n; k++) ts[k] = a0 + sign * (mg + k * (asw - 2 * mg) / n);
        for (int fi = 0; fi < s.faces.length; fi++) {
            int i = s.faces[fi][0], j = s.faces[fi][1];
            for (double t : ts) rod(m, lod, 2, P(f, s, R, i, t), P(f, s, R, j, t), s.rd, 6, M_ALU);
            for (int k = 0; k < n; k++) {
                boolean even = (k + fi) % 2 == 0;
                int u = even ? i : j, v = even ? j : i;
                rod(m, lod, 1, P(f, s, R, u, ts[k]), P(f, s, R, v, ts[k + 1]), s.rd, 6, M_ALU);
            }
        }
        if (couplers) {
            for (int end = 0; end < 2; end++) {
                double a = end == 0 ? a0 : a0 + sw, sg = end == 0 ? 1 : -1;
                // tangent pointing into the truss at this end
                double[] tan = add(add(new double[3], f.e1, -Math.sin(a) * sign * sg), f.e2, Math.cos(a) * sign * sg);
                for (int i = 0; i < pts.length; i++) {
                    double[] p = P(f, s, R, i, a);
                    double r = s.r;
                    lathe(m, lod, 1, new double[][] {{r * 1.07, 0}, {r * 1.12, .32 * U}, {r, 1.2 * U}, {r * .68, 1.2 * U}, {r * .68, 0}}, p, tan, 8, M_COUPLER);
                    double[] q = add(p, tan, .65 * U);
                    rod(m, lod, 0, add(q, f.n, -(r + .14 * U)), add(q, f.n, r + .14 * U), .085 * U, 6, M_STEEL);
                }
            }
        }
        return n;
    }

    /** Segment count of a ring of radius R (segments of 3 m at most), Blender csc_models.ring. */
    public static int ringSegments(double R) {
        return Math.max(4, 2 * (int) Math.ceil(Math.PI * R / 3));
    }

    /** A full ring: segments joined by couplers. */
    public static void ring(Mesh m, int lod, Frame f, Section s, double R) {
        int k = ringSegments(R);
        for (int i = 0; i < k; i++) curved(m, lod, f, s, R, i * 2 * Math.PI / k, 2 * Math.PI / k, true);
    }

    /** Blender variant layout (pivot at end A, centre at +R on x, horizontal arc): used by the checks against the supplied models. */
    public static Frame blenderFrame(double R) {
        return Frame.of(new double[] {R, 0, 0}, new double[] {-1, 0, 0}, new double[] {0, 0, 1}, new double[] {0, 1, 0});
    }
}
