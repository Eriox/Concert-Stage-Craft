package com.concertstage.angle;

/**
 * Geometry of trusses placed at an angle. No Minecraft dependency, so it is unit tested (tools/AngleCheck.java).
 *
 * Conventions: x grows east and z grows south. An angle is a "compass" angle in degrees, clockwise seen from above,
 * 0 = east (+x), 90 = south (+z), 180 = west, 270 = north. A piece is described by its pivot (the centre of its first cell, which is
 * where the model is authored from) and the angle of its authored +x axis. The piece then runs from its start face (the pivot moved back
 * by half a block) along that axis for {@code length} blocks.
 *
 * To draw it, a renderer moves to the pivot, rotates about the vertical axis by {@code -angle} degrees (Minecraft's Axis.YP is counter-
 * clockwise seen from above, so the minus sign gives a clockwise turn) and moves back by half a block in x and z (the model is authored
 * from the corner of its first cell).
 */
public final class AngleMath {
    /** The angle tool moves by this many degrees. */
    public static final int STEP = 5;
    /** The tool goes from -MAX_BEND to +MAX_BEND (relative to the straight placement). */
    public static final int MAX_BEND = 90;

    private AngleMath() {}

    /** Compass angle of a horizontal step (east 0, south 90, west 180, north 270). */
    public static int compassOfStep(int dx, int dz) {
        if (dx > 0) return 0;
        if (dz > 0) return 90;
        if (dx < 0) return 180;
        return 270;
    }

    /**
     * Angle a truss placed on the side (face step {@code stepX}, {@code stepZ}) of a tower turned by {@code towerAngle} takes: the direction of
     * the flange of the joint on that side, i.e. the angle of the face turned with the tower. The angle tool is not part of it.
     */
    public static double towerFlankAngle(int stepX, int stepZ, double towerAngle) {
        return norm360(compassOfStep(stepX, stepZ) + towerAngle);
    }

    /** Angle brought into [0, 360). */
    public static double norm360(double deg) {
        double a = deg % 360.0;
        return a < 0 ? a + 360.0 : a;
    }

    /** Unit vector (x, z) of a compass angle. */
    public static double[] dir(double deg) {
        double r = Math.toRadians(deg);
        return new double[] {Math.cos(r), Math.sin(r)};
    }

    /** Bend of the tool snapped to the step and kept in [-MAX_BEND, MAX_BEND]. */
    public static int clampBend(int bend) {
        int snapped = Math.round(bend / (float) STEP) * STEP;
        return Math.max(-MAX_BEND, Math.min(MAX_BEND, snapped));
    }

    /** The values the tool goes through, in order. A click moves to the next one, a sneaking click to the previous one. */
    public static final int[] PRESETS = {-90, -75, -60, -45, -30, -20, -10, 0, 10, 20, 30, 45, 60, 75, 90};

    /** Index of the preset closest to {@code bend}. */
    public static int presetIndex(int bend) {
        int best = 0;
        for (int i = 1; i < PRESETS.length; i++) {
            if (Math.abs(PRESETS[i] - bend) < Math.abs(PRESETS[best] - bend)) best = i;
        }
        return best;
    }

    /** Next value of the tool: the next (or previous) preset, wrapping from one end of the list to the other. */
    public static int stepBend(int bend, boolean up) {
        int i = presetIndex(bend) + (up ? 1 : -1);
        if (i >= PRESETS.length) i = 0;
        if (i < 0) i = PRESETS.length - 1;
        return PRESETS[i];
    }

    /** Centre of the start face of a piece with the given pivot. */
    public static double[] startPoint(double px, double pz, double deg) {
        double[] u = dir(deg);
        return new double[] {px - 0.5 * u[0], pz - 0.5 * u[1]};
    }

    /** Centre of the end face of a piece that is {@code length} blocks long. */
    public static double[] endPoint(double px, double pz, double deg, int length) {
        double[] u = dir(deg);
        double d = length - 0.5;
        return new double[] {px + d * u[0], pz + d * u[1]};
    }

    /** Pivot of a piece whose start face centre is (sx, sz). */
    public static double[] pivotFromStart(double sx, double sz, double deg) {
        double[] u = dir(deg);
        return new double[] {sx + 0.5 * u[0], sz + 0.5 * u[1]};
    }

    /** Where a point of the model (authored coordinates, in blocks from the corner of the first cell) ends up in the world. */
    public static double[] modelToWorld(double px, double pz, double deg, double mx, double mz) {
        double lx = mx - 0.5, lz = mz - 0.5;                       // move back by half a block
        double a = Math.toRadians(-deg);                           // Axis.YP.rotationDegrees(-angle)
        double x = lx * Math.cos(a) + lz * Math.sin(a);
        double z = -lx * Math.sin(a) + lz * Math.cos(a);
        return new double[] {px + x, pz + z};
    }

    /** Block coordinate that contains a world coordinate. */
    public static int cellOf(double v) {
        return (int) Math.floor(v);
    }

    /**
     * Cell {x, z} that holds a piece whose pivot is (px, pz). A piece attached to the side of a block, bent by exactly +-90 degrees, has its
     * pivot on the grid line next to the block: the point is pushed a hair away from the block (outward normal (nx, nz)) so it falls in the
     * cell beside the block and not in the block itself. Pass (0, 0) when there is nothing to push away from.
     */
    public static int[] holderCell(double px, double pz, int nx, int nz) {
        return new int[] {cellOf(px + 1e-4 * nx), cellOf(pz + 1e-4 * nz)};
    }

    /** Squared distance between two points of the horizontal plane. */
    public static double dist2(double ax, double az, double bx, double bz) {
        double dx = ax - bx, dz = az - bz;
        return dx * dx + dz * dz;
    }

    /**
     * The point of a piece's axis (from its start face to its end face) that is closest to (qx, qz): where something that hangs under
     * the piece, like a chain hoist, sits to be on its axis. Returns {x, z, t} with t = 0 at the start face and 1 at the end face.
     */
    public static double[] closestOnAxis(double px, double pz, double deg, int length, double qx, double qz) {
        double[] u = dir(deg);
        double[] s = startPoint(px, pz, deg);
        double t = ((qx - s[0]) * u[0] + (qz - s[1]) * u[1]) / length;
        t = Math.max(0.0, Math.min(1.0, t));
        return new double[] {s[0] + t * length * u[0], s[1] + t * length * u[1], t};
    }

    /** Distance from (qx, qz) to the axis segment of a piece. */
    public static double distanceToAxis(double px, double pz, double deg, int length, double qx, double qz) {
        double[] c = closestOnAxis(px, pz, deg, length, qx, qz);
        return Math.sqrt(dist2(c[0], c[1], qx, qz));
    }

    /**
     * The point of a piece's axis that lies inside the cell centred at (cx, cz) (half a block each way) and is closest to the middle of the
     * cell, or null if the axis does not cross the cell. Returns {x, z}. This is where a hoist hangs under an angled beam.
     */
    public static double[] axisPointInCell(double px, double pz, double deg, int length, double cx, double cz) {
        double[] u = dir(deg);
        double[] s = startPoint(px, pz, deg);
        double[] best = null;
        double bestD = Double.MAX_VALUE;
        int steps = 50 * length;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps * length;
            double x = s[0] + t * u[0], z = s[1] + t * u[1];
            if (Math.abs(x - cx) > 0.5 + 1e-9 || Math.abs(z - cz) > 0.5 + 1e-9) continue;
            double d = dist2(x, z, cx, cz);
            if (d < bestD) {
                bestD = d;
                best = new double[] {x, z};
            }
        }
        return best;
    }

    /**
     * How far from the end of the line it remembers a click still carries it on, while the player holds the angle tool. The line itself has
     * no limit: every piece moves the end, so a line can be carried on for as many blocks as you like. This is only how far from the end you
     * may click (the ground next to you is enough, you do not have to walk to the end).
     */
    public static final double CHAIN_REACH_TOOL = 64.0;
    /** Without the tool, a line started on a turned tower (or carried on) is carried on by a click this close to its end. */
    public static final double CHAIN_REACH_AUTO = 3.0;
    /** Largest height difference (blocks) between the clicked cell and the end of the line. */
    public static final double CHAIN_DY = 8.0;

    /**
     * Does a click at squared horizontal distance {@code dist2} and height difference {@code dy} from the end of the remembered line carry
     * the line on? With the tool: from far away (a line can be as long as you like). Without it: only a line that started automatically
     * (on a turned tower, or carried on), and only right at its end, so a straight truss placed elsewhere is not pulled into the line.
     */
    public static boolean chainApplies(boolean holdingTool, boolean auto, double dist2, double dy) {
        if (Math.abs(dy) > CHAIN_DY) return false;
        double reach = holdingTool ? CHAIN_REACH_TOOL : (auto ? CHAIN_REACH_AUTO : 0.0);
        return reach > 0.0 && dist2 <= reach * reach;
    }

    /** Smallest difference between two compass angles, in [0, 180]. */
    public static double angleDiff(double a, double b) {
        double d = Math.abs(norm360(a) - norm360(b));
        return d > 180.0 ? 360.0 - d : d;
    }

    /**
     * Angle a hanging block (a hoist) should take to run along a beam at {@code beamDeg}: the beam axis has no direction, so the
     * hoist keeps the one of the two (beam, beam + 180) that is closest to {@code lookDeg}.
     */
    public static double alongBeam(double beamDeg, double lookDeg) {
        double a = norm360(beamDeg);
        double b = norm360(beamDeg + 180.0);
        return angleDiff(a, lookDeg) <= angleDiff(b, lookDeg) ? a : b;
    }
}
