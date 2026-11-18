package com.concertstage.light.core;

/**
 * 1.2.1: where a patched fixture is and how it is turned, the way the game draws it (FixtureRenderer):
 * world = translate(block centre + offset, at the bottom of the block or at its top when hung) * rotY(yaw) * rotX(pitch) * rotZ(roll)
 * * (hung ? rotZ(180) : identity) * local. With no offset, pitch or roll it is the pose of every version before 1.2.1.
 * Pure Java (desk aiming, MVR export, tests).
 */
public final class FixturePose {
    private FixturePose() {}

    /** Rotation (row-major 3 x 3) from the fixture's local frame to the world. */
    public static double[][] rotation(Show.Patch p) {
        double[][] m = mul(mul(rotY(p.yaw), rotX(p.pitch)), rotZ(p.roll));
        return p.mount == 1 ? mul(m, rotZ(180)) : m;
    }

    /** World position of the origin of the base (bottom centre of the base; top centre when hung). */
    public static double[] origin(Show.Patch p) {
        return new double[] {p.x + 0.5 + p.ox, (p.mount == 1 ? p.y + 1.0 : p.y) + p.oy, p.z + 0.5 + p.oz};
    }

    /** {@code o + R v}. */
    public static double[] toWorld(Show.Patch p, double lx, double ly, double lz) {
        double[][] r = rotation(p);
        double[] o = origin(p);
        return new double[] {o[0] + r[0][0] * lx + r[0][1] * ly + r[0][2] * lz, o[1] + r[1][0] * lx + r[1][1] * ly + r[1][2] * lz,
            o[2] + r[2][0] * lx + r[2][1] * ly + r[2][2] * lz};
    }

    /** A world direction in the local frame ({@code R^T d}). */
    public static double[] toLocalDir(Show.Patch p, double dx, double dy, double dz) {
        double[][] r = rotation(p);
        return new double[] {r[0][0] * dx + r[1][0] * dy + r[2][0] * dz, r[0][1] * dx + r[1][1] * dy + r[2][1] * dz,
            r[0][2] * dx + r[1][2] * dy + r[2][2] * dz};
    }

    public static double[][] rotY(double deg) {
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
        return new double[][] {{c, 0, s}, {0, 1, 0}, {-s, 0, c}};
    }

    public static double[][] rotX(double deg) {
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
        return new double[][] {{1, 0, 0}, {0, c, -s}, {0, s, c}};
    }

    public static double[][] rotZ(double deg) {
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
        return new double[][] {{c, -s, 0}, {s, c, 0}, {0, 0, 1}};
    }

    public static double[][] mul(double[][] a, double[][] b) {
        double[][] r = new double[3][3];
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) for (int k = 0; k < 3; k++) r[i][j] += a[i][k] * b[k][j];
        return r;
    }
}
