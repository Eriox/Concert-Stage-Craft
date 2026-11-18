import com.concertstage.angle.AngleMath;

/**
 * Checks of the angle geometry (no Minecraft needed).
 *   javac -d /tmp/angle-check common/src/main/java/com/concertstage/angle/AngleMath.java tools/AngleCheck.java
 *   java -cp /tmp/angle-check AngleCheck
 */
public class AngleCheck {
    static int failures = 0;
    static void check(boolean ok, String what) {
        if (!ok) { failures++; System.out.println("  ECHEC : " + what); }
    }
    static boolean near(double a, double b) { return Math.abs(a - b) < 1e-9; }

    public static void main(String[] args) {
        // ---- compass angles of the four faces
        check(AngleMath.compassOfStep(1, 0) == 0 && AngleMath.compassOfStep(0, 1) == 90 && AngleMath.compassOfStep(-1, 0) == 180 && AngleMath.compassOfStep(0, -1) == 270,
            "compass of the four steps");

        // ---- the model transform (translate to pivot, rotate -angle, move back half a block) puts the start and end faces where the maths says
        for (int deg = 0; deg < 360; deg += 5) {
            for (int len = 1; len <= 2; len++) {
                double px = 3.5, pz = -7.25;
                double[] s = AngleMath.modelToWorld(px, pz, deg, 0.0, 0.5);            // start face centre in model coordinates: x = 0, z = 0.5
                double[] e = AngleMath.modelToWorld(px, pz, deg, len, 0.5);            // end face centre: x = length
                double[] s2 = AngleMath.startPoint(px, pz, deg);
                double[] e2 = AngleMath.endPoint(px, pz, deg, len);
                check(near(s[0], s2[0]) && near(s[1], s2[1]), "start face at " + deg + " deg");
                check(near(e[0], e2[0]) && near(e[1], e2[1]), "end face at " + deg + " deg, length " + len);
                double[] c = AngleMath.modelToWorld(px, pz, deg, 0.5, 0.5);             // centre of the first cell stays on the pivot
                check(near(c[0], px) && near(c[1], pz), "pivot is the centre of the first cell at " + deg);
            }
        }

        // ---- the four cardinal angles are exactly the quarter turns of the existing placement (east, south, west, north)
        double[] e0 = AngleMath.endPoint(0.5, 0.5, 0, 2), e90 = AngleMath.endPoint(0.5, 0.5, 90, 2), e180 = AngleMath.endPoint(0.5, 0.5, 180, 2), e270 = AngleMath.endPoint(0.5, 0.5, 270, 2);
        check(near(e0[0], 2.0) && near(e0[1], 0.5), "east: a 2-block piece from cell 0 ends at x = 2");
        check(near(e90[0], 0.5) && near(e90[1], 2.0), "south: ends at z = 2");
        check(near(e180[0], -1.0) && near(e180[1], 0.5), "west: ends at x = -1");
        check(near(e270[0], 0.5) && near(e270[1], -1.0), "north: ends at z = -1");

        // ---- a chain of pieces at any angle is one straight line, end to start
        for (int deg : new int[] {10, 20, 30, 45, 60, 75, 100, 135, 250, 355}) {
            double[] start = {12.5, 40.0};
            double px = AngleMath.pivotFromStart(start[0], start[1], deg)[0], pz = AngleMath.pivotFromStart(start[0], start[1], deg)[1];
            double total = 0;
            double[] prevEnd = start;
            boolean joined = true;
            for (int k = 0; k < 12; k++) {
                int len = 1 + (k % 2);                                        // alternate 1x1 and 1x2
                double[] s = AngleMath.startPoint(px, pz, deg);
                joined &= near(s[0], prevEnd[0]) && near(s[1], prevEnd[1]);
                double[] e = AngleMath.endPoint(px, pz, deg, len);
                prevEnd = e;
                total += len;
                double[] np = AngleMath.pivotFromStart(e[0], e[1], deg);       // next piece starts where this one ends
                px = np[0]; pz = np[1];
            }
            double[] u = AngleMath.dir(deg);
            check(joined, "chain at " + deg + ": every piece starts where the previous one ends");
            check(near(prevEnd[0], start[0] + total * u[0]) && near(prevEnd[1], start[1] + total * u[1]), "chain at " + deg + ": the end is on the straight line");
        }

        // ---- attaching to the east face of a cell at a bend: the piece leaves from the face centre
        {
            int cx = 4, cz = 9;                                               // clicked cell
            double sx = cx + 1.0, sz = cz + 0.5;                              // centre of its east face
            int bend = 30;
            double deg = AngleMath.norm360(AngleMath.compassOfStep(1, 0) + bend);
            double[] p = AngleMath.pivotFromStart(sx, sz, deg);
            double[] s = AngleMath.startPoint(p[0], p[1], deg);
            check(near(s[0], sx) && near(s[1], sz), "attached piece starts on the face centre");
            check(near(deg, 30.0), "east + 30 = 30");
            check(AngleMath.cellOf(p[0]) == 5 && AngleMath.cellOf(p[1]) == 9, "holder cell of the first piece: (" + AngleMath.cellOf(p[0]) + ", " + AngleMath.cellOf(p[1]) + ")");
        }
        check(near(AngleMath.norm360(AngleMath.compassOfStep(0, -1) + 30), 300.0), "north + 30 = 300");
        check(near(AngleMath.norm360(-10), 350.0) && near(AngleMath.norm360(725), 5.0), "norm360");

        // ---- a truss on the side of a turned tower takes the angle of the tower, whatever the tool says
        for (int tower = 0; tower < 360; tower += 5) {
            check(near(AngleMath.towerFlankAngle(1, 0, tower), AngleMath.norm360(tower)), "east flank of a tower at " + tower);
            check(near(AngleMath.towerFlankAngle(0, 1, tower), AngleMath.norm360(90 + tower)), "south flank of a tower at " + tower);
            check(near(AngleMath.towerFlankAngle(-1, 0, tower), AngleMath.norm360(180 + tower)), "west flank of a tower at " + tower);
            check(near(AngleMath.towerFlankAngle(0, -1, tower), AngleMath.norm360(270 + tower)), "north flank of a tower at " + tower);
        }

        // ---- a line carries on from far away with the tool, only at its end without it (and only a line that went its own way)
        check(AngleMath.chainApplies(true, false, 50.0 * 50.0, 0), "tool: 50 blocks from the end still carries the line on");
        check(AngleMath.chainApplies(true, true, 64.0 * 64.0, 8), "tool: 64 blocks away and 8 blocks higher still carries on");
        check(!AngleMath.chainApplies(true, true, 65.0 * 65.0, 0), "tool: beyond 64 blocks starts a new line");
        check(!AngleMath.chainApplies(true, true, 1.0, 9), "tool: more than 8 blocks higher or lower starts a new line");
        check(AngleMath.chainApplies(false, true, 2.0 * 2.0, 0), "no tool, automatic line: right at its end carries on");
        check(!AngleMath.chainApplies(false, true, 4.0 * 4.0, 0), "no tool, automatic line: 4 blocks away does not");
        check(!AngleMath.chainApplies(false, false, 0.25, 0), "no tool, line made with the tool: never");
        // a line of any length: the end moves with every piece, so the 64 blocks are counted from the NEW end every time
        {
            double[] end = {0.5, 0.5}; int deg = 20; int placed = 0;
            double clickX = 0.5, clickZ = 0.5;                                // the player never moves: clicks the same ground block
            for (int k = 0; k < 400; k++) {
                if (!AngleMath.chainApplies(true, true, AngleMath.dist2(end[0], end[1], clickX, clickZ), 0)) break;
                double[] p = AngleMath.pivotFromStart(end[0], end[1], deg);
                end = AngleMath.endPoint(p[0], p[1], deg, 2);
                placed++;
            }
            check(placed >= 30 && placed < 400, "standing still, you can carry a line on until it is 64 blocks from you (" + placed + " pieces); then you move on, the line is not limited");
        }

        // ---- the tool
        check(AngleMath.clampBend(7) == 5 && AngleMath.clampBend(-13) == -15 && AngleMath.clampBend(200) == 90 && AngleMath.clampBend(-200) == -90, "clampBend");
        int b = 0;
        int[] up = {10, 20, 30, 45, 60, 75, 90, -90};
        for (int expected : up) { b = AngleMath.stepBend(b, true); check(b == expected, "click up -> " + expected + " (got " + b + ")"); }
        check(AngleMath.stepBend(0, false) == -10 && AngleMath.stepBend(-10, false) == -20, "sneaking goes down through -10, -20");
        check(AngleMath.stepBend(-90, false) == 90, "wraps from -90 down to 90");
        check(AngleMath.stepBend(30, false) == 20 && AngleMath.stepBend(10, false) == 0, "back down to 0");
        check(AngleMath.stepBend(25, true) == 30 && AngleMath.stepBend(5, true) == 10 || AngleMath.stepBend(5, true) == 20, "a value written by the previous version (5 degree steps) snaps to a preset");
        // the examples of the request: 10, 20, 30 degrees take 1, 2, 3 clicks
        int c = 0; int clicks = 0;
        while (c != 30) { c = AngleMath.stepBend(c, true); clicks++; }
        check(clicks == 3, "30 degrees in 3 clicks (" + clicks + ")");

        // ---- a hoist under an angled beam: the closest point of the axis, and the direction along the beam
        {
            double px = 10.5, pz = 20.5; int deg = 30, len = 2;
            double[] axisPoint = AngleMath.closestOnAxis(px, pz, deg, len, 11.0, 21.4);
            double[] u = AngleMath.dir(deg);
            double d = AngleMath.distanceToAxis(px, pz, deg, len, axisPoint[0], axisPoint[1]);
            check(d < 1e-9, "a point of the axis is at distance 0");
            check(axisPoint[2] >= 0.0 && axisPoint[2] <= 1.0, "t stays in [0, 1]");
            // perpendicular: the hoist offset is perpendicular to the axis
            double ox = 11.0 - axisPoint[0], oz = 21.4 - axisPoint[1];
            check(Math.abs(ox * u[0] + oz * u[1]) < 1e-9 || axisPoint[2] == 0.0 || axisPoint[2] == 1.0, "offset is perpendicular to the beam");
            double[] beyond = AngleMath.closestOnAxis(px, pz, deg, len, 100.0, 100.0);
            double[] end = AngleMath.endPoint(px, pz, deg, len);
            check(near(beyond[0], end[0]) && near(beyond[1], end[1]) && beyond[2] == 1.0, "clamped to the end face");
        }
        check(near(AngleMath.alongBeam(30, 20), 30.0) && near(AngleMath.alongBeam(30, 200), 210.0), "hoist keeps the end of the beam axis closest to the look");
        check(near(AngleMath.angleDiff(350, 10), 20.0), "angleDiff wraps");

        // ---- hoist cell under an angled beam: the beam crosses the cells under it, and only those
        {
            double px = 10.5, pz = 20.5; int deg = 30, len = 2;               // a 1 x 2 beam from (10, 20.25) to about (11.7, 20.9) + 1
            double[] mid = AngleMath.dir(deg);
            int under = 0, crossing = 0;
            double worst = 0;
            for (int cx = 5; cx <= 20; cx++) {
                for (int cz = 15; cz <= 28; cz++) {
                    double[] hit = AngleMath.axisPointInCell(px, pz, deg, len, cx + 0.5, cz + 0.5);
                    if (hit == null) continue;
                    crossing++;
                    check(Math.abs(hit[0] - (cx + 0.5)) <= 0.5 + 1e-9 && Math.abs(hit[1] - (cz + 0.5)) <= 0.5 + 1e-9, "the hoist point is inside its cell");
                    check(AngleMath.distanceToAxis(px, pz, deg, len, hit[0], hit[1]) < 1e-6, "the hoist point is on the axis of the beam");
                    worst = Math.max(worst, Math.hypot(hit[0] - (cx + 0.5), hit[1] - (cz + 0.5)));
                }
            }
            check(crossing >= 2 && crossing <= 4, "a 2-block beam at 30 degrees crosses 2 to 4 cells (" + crossing + ")");
            check(worst <= 0.71, "the hoist is never further than a half diagonal from the middle of its cell (" + worst + ")");
            check(AngleMath.axisPointInCell(px, pz, deg, len, 30.5, 30.5) == null, "a far cell is not under the beam");
            // an east-west beam (angle 0) crosses the row of cells it lies in, hoist right in the middle of the cell on the beam axis
            double[] straight = AngleMath.axisPointInCell(4.5, 7.5, 0, 2, 5.5, 7.5);
            check(straight != null && Math.abs(straight[0] - 5.5) < 0.03 && Math.abs(straight[1] - 7.5) < 1e-9, "straight beam: the hoist is at the middle of the cell");
        }

        // ---- lifting: two pieces that touch are always found from one another (the next piece is held within one cell of the end of the previous one)
        {
            int checked = 0;
            for (int deg = 0; deg < 360; deg += 5) {
                for (int bend : new int[] {0, 5, 30, -45, 90, -90}) {
                    for (int len = 1; len <= 2; len++) {
                        double px = 7.37, pz = -3.81;
                        double[] end = AngleMath.endPoint(px, pz, deg, len);
                        double nextDeg = AngleMath.norm360(deg + bend);
                        double[] np = AngleMath.pivotFromStart(end[0], end[1], nextDeg);
                        int dx = Math.abs(AngleMath.cellOf(np[0]) - AngleMath.cellOf(end[0]));
                        int dz = Math.abs(AngleMath.cellOf(np[1]) - AngleMath.cellOf(end[1]));
                        check(dx <= 1 && dz <= 1, "next piece's cell is within one cell of the end (deg " + deg + ", bend " + bend + ", len " + len + ")");
                        checked++;
                    }
                }
            }
            // a piece attached to the side of a straight block is held in a cell that touches that block (face, edge or corner)
            for (int face = 0; face < 4; face++) {
                int[] step = {1, 0, -1, 0}, stepZ = {0, 1, 0, -1};
                int fx = step[face], fz = stepZ[face];
                for (int bend = -90; bend <= 90; bend += 5) {
                    double sx = 10 + 0.5 + 0.5 * fx, sz = 10 + 0.5 + 0.5 * fz;
                    double deg = AngleMath.norm360(AngleMath.compassOfStep(fx, fz) + bend);
                    double[] p = AngleMath.pivotFromStart(sx, sz, deg);
                    int[] hc = AngleMath.holderCell(p[0], p[1], fx, fz);
                    int dx = Math.abs(hc[0] - 10), dz = Math.abs(hc[1] - 10);
                    check(dx <= 1 && dz <= 1 && (dx + dz) > 0, "piece on face " + face + " bend " + bend + " is held next to the block");
                    checked++;
                }
            }
            System.out.println("  liaisons de levage vérifiées : " + checked);
        }

        System.out.println(failures == 0 ? "TOUT EST OK" : (failures + " ECHEC(S)"));
        System.exit(failures == 0 ? 0 : 1);
    }
}
