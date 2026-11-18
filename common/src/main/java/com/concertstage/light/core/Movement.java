package com.concertstage.light.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Movement planner: aims fixtures at points of the stage from their real place and orientation (block, base yaw, standing or hung),
 * within the mechanical limits of their profile, and builds the standard position palettes of a show (stage centre, audience,
 * upstage, cross, vertical beams, rest) from the geometry of the rig.
 *
 * <p>Fixture frame: the model faces north (-Z); pan turns the yoke about +Y, tilt turns the head about +X, positive tilt raises the
 * beam. World = translate(block) * rotY(yaw) * (hung ? rotZ(180) about the block centre : identity) * local.
 */
public final class Movement {
    private Movement() {}

    /** Geometry of the stage seen from the desk: rig centre, floor height, and the direction from the stage to the audience. */
    public record Stage(double cx, double cy, double cz, double floorY, double toAudienceX, double toAudienceZ, double width) {}

    /** World position of the tilt pivot of a fixture (block centre, base at the bottom or at the top when hung). */
    public static double[] pivot(Show.Patch p) {
        FixtureProfile pr = p.profile();
        if (p.posed()) return FixturePose.toWorld(p, 0, pr.tiltPivotY, 0);           // 1.2.1: free pose
        double y = p.mount == 1 ? p.y + 1.0 - pr.tiltPivotY : p.y + pr.tiltPivotY;
        return new double[] {p.x + 0.5, y, p.z + 0.5};
    }

    /** {pan, tilt} normalised values that point the fixture at the world point, or null if the profile cannot reach it. */
    public static float[] aim(Show.Patch p, double tx, double ty, double tz) {
        double[] o = pivot(p);
        return aimDir(p, tx - o[0], ty - o[1], tz - o[2]);
    }

    /** Same with a world direction. */
    public static float[] aimDir(Show.Patch p, double dx, double dy, double dz) {
        FixtureProfile pr = p.profile();
        double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1e-6) return null;
        dx /= l; dy /= l; dz /= l;
        // world -> local: inverse of rotY(yaw), then of the hung flip (rotZ 180: x -> -x, y -> -y)
        double a = Math.toRadians(-p.yaw);
        double lx = dx * Math.cos(a) + dz * Math.sin(a);
        double lz = -dx * Math.sin(a) + dz * Math.cos(a);
        double ly = dy;
        if (p.mount == 1) { lx = -lx; ly = -ly; }
        if (p.posed()) {                                                         // 1.2.1: pitch and roll of a free pose too
            double[] lv = FixturePose.toLocalDir(p, dx, dy, dz);
            lx = lv[0]; ly = lv[1]; lz = lv[2];
        }
        // local direction = rotY(pan) * (0, sin t, -cos t) = (-sin pan cos t, sin t, -cos pan cos t)
        double tilt = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, ly))));
        double pan = Math.toDegrees(Math.atan2(-lx, -lz));
        if (!pr.hasPan()) {
            // fixed yoke (PULSESTRIKE): the head only tilts; aim in the vertical plane of the base
            double fwd = -lz;
            tilt = Math.toDegrees(Math.atan2(ly, Math.max(1e-6, fwd)));
            if (Math.abs(tilt) > pr.tiltRange / 2 + 0.5) return new float[] {0.5f, pr.tiltValue((float) tilt)};
            return new float[] {0.5f, pr.tiltValue((float) tilt)};
        }
        // pan has more than 360 degrees: take the solution closest to the neutral position, and keep tilt in range
        double best = pan;
        for (double c : new double[] {pan - 360, pan + 360}) if (Math.abs(c) < Math.abs(best) && Math.abs(c) <= pr.panRange / 2) best = c;
        // rev. 1.3.0-2: the tilt is centred on the straight position (tiltZero): a direction below the plane of the base is reached
        // the other way round (pan + 180, tilt over the top)
        if (Math.abs(best) > pr.panRange / 2 || tilt < pr.tiltMin() || tilt > pr.tiltMax()) {
            // the other way round: pan + 180 with the tilt mirrored over the top
            double alt = best > 0 ? best - 180 : best + 180;
            double altTilt = 180 - tilt;
            if (altTilt > 180) altTilt -= 360;
            if (altTilt < -180) altTilt += 360;
            for (double c : new double[] {alt, alt - 360, alt + 360})
                if (Math.abs(c) <= pr.panRange / 2 && altTilt >= pr.tiltMin() && altTilt <= pr.tiltMax()) { best = c; tilt = altTilt; break; }
        }
        return new float[] {pr.panValue((float) best), pr.tiltValue((float) tilt)};
    }

    /** Value of the tilt that points the beam straight away from the base (up when standing, down when hung): the rest position. */
    public static float restTilt(Show.Patch p) {
        FixtureProfile pr = p.profile();
        return pr.tiltValue(Math.min(90f, pr.tiltRange / 2));
    }

    /** Geometry of the stage from the patch and the desk position (the desk is in the audience, looking at the stage). */
    public static Stage stage(Show show, double deskX, double deskY, double deskZ) {
        if (show.patch.isEmpty()) return new Stage(deskX, deskY + 4, deskZ - 10, deskY, 0, 1, 8);
        double cx = 0, cy = 0, cz = 0;
        double minA = Double.MAX_VALUE, maxA = -Double.MAX_VALUE;
        for (Show.Patch p : show.patch) { cx += p.x + 0.5; cy += p.y + 0.5; cz += p.z + 0.5; }
        int n = show.patch.size();
        cx /= n; cy /= n; cz /= n;
        double ax = deskX + 0.5 - cx, az = deskZ + 0.5 - cz;
        double al = Math.sqrt(ax * ax + az * az);
        if (al < 1e-3) { ax = 0; az = 1; } else { ax /= al; az /= al; }
        for (Show.Patch p : show.patch) {
            double side = (p.x + 0.5 - cx) * az - (p.z + 0.5 - cz) * ax;
            minA = Math.min(minA, side); maxA = Math.max(maxA, side);
        }
        double floor = Math.min(deskY, minFixtureY(show) - 1);
        return new Stage(cx, cy, cz, floor, ax, az, Math.max(4, maxA - minA));
    }

    private static double minFixtureY(Show show) {
        double m = Double.MAX_VALUE;
        for (Show.Patch p : show.patch) m = Math.min(m, p.y);
        return m;
    }

    /** Lateral coordinate of a fixture across the stage (-1 stage left .. 1 stage right, seen from the audience). */
    public static double side(Stage s, Show.Patch p) {
        double d = (p.x + 0.5 - s.cx()) * s.toAudienceZ() - (p.z + 0.5 - s.cz()) * s.toAudienceX();
        return Math.max(-1, Math.min(1, d / Math.max(1e-3, s.width() / 2)));
    }

    /** Names of the standard positions, in the order {@link #standardPositions} builds them. */
    public static final String[] POSITION_NAMES = {"Stage centre", "Audience", "Upstage", "Cross", "Vertical beams", "Fan out", "Rest"};

    /** Position palettes for every fixture of the show: one {pan, tilt} look per fixture and per standard position. */
    public static List<Show.Preset> standardPositions(Show show, Stage s, int firstId) {
        List<Show.Preset> out = new ArrayList<>();
        int id = firstId;
        for (int k = 0; k < POSITION_NAMES.length; k++) {
            Show.Preset pr = new Show.Preset();
            pr.id = id++;
            pr.kind = Show.Preset.POSITION;
            pr.name = POSITION_NAMES[k];
            pr.swatch = 0xFF35C9E8;
            for (Show.Patch p : show.patch) {
                float[] pt = null;
                double side = side(s, p);
                double lateralX = s.toAudienceZ(), lateralZ = -s.toAudienceX();      // unit vector across the stage
                switch (k) {
                    case 0 -> pt = aim(p, s.cx() + side * s.width() * 0.12 * lateralX, s.floorY() + 1, s.cz() + side * s.width() * 0.12 * lateralZ);
                    case 1 -> pt = aim(p, s.cx() + s.toAudienceX() * 14 + side * s.width() * 0.4 * lateralX, s.floorY() + 1.5,
                        s.cz() + s.toAudienceZ() * 14 + side * s.width() * 0.4 * lateralZ);
                    case 2 -> pt = aim(p, s.cx() - s.toAudienceX() * 6 + side * s.width() * 0.3 * lateralX, s.floorY() + 3,
                        s.cz() - s.toAudienceZ() * 6 + side * s.width() * 0.3 * lateralZ);
                    case 3 -> pt = aim(p, s.cx() - side * s.width() * 0.45 * lateralX + s.toAudienceX() * 3, s.floorY() + 1,
                        s.cz() - side * s.width() * 0.45 * lateralZ + s.toAudienceZ() * 3);
                    case 4 -> pt = aimDir(p, 0, p.mount == 1 ? -1 : 1, 0);
                    case 5 -> pt = aimDir(p, side * 0.8 * lateralX + s.toAudienceX() * 0.25, p.mount == 1 ? -0.55 : 0.8,
                        side * 0.8 * lateralZ + s.toAudienceZ() * 0.25);
                    default -> pt = new float[] {0.5f, restTilt(p)};
                }
                if (pt == null) continue;
                Look l = new Look();
                if (p.profile().hasPan()) l.set(Attr.PAN, pt[0]);
                l.set(Attr.TILT, pt[1]);
                pr.perFixture.put(p.fid, l);
            }
            out.add(pr);
        }
        coherent(show, out);
        return out;
    }

    /**
     * Rev. 2: every position of a fixture is taken, among its equivalent pan / tilt solutions (pan ± 360, or pan ± 180 with the tilt over
     * the top), as close as possible to its "Stage centre" position — so going from one position to another is a short, smooth move and
     * never a full turn of the yoke. Vertical and rest positions (pan has no effect when the beam is straight up or down) keep the
     * centre pan, so the heads only tilt.
     */
    static void coherent(Show show, List<Show.Preset> presets) {
        if (presets.isEmpty()) return;
        Show.Preset ref = presets.get(0);
        for (Show.Patch p : show.patch) {
            FixtureProfile pr = p.profile();
            if (!pr.hasPan()) continue;
            Look r = ref.perFixture.get(p.fid);
            if (r == null || !r.has(Attr.PAN)) continue;
            double refPan = pr.panDegrees(r.get(Attr.PAN)), refTilt = pr.tiltDegrees(r.get(Attr.TILT));
            for (int k = 1; k < presets.size(); k++) {
                Look l = presets.get(k).perFixture.get(p.fid);
                if (l == null || !l.has(Attr.PAN)) continue;
                double pan = pr.panDegrees(l.get(Attr.PAN)), tilt = pr.tiltDegrees(l.get(Attr.TILT));
                if (Math.abs(Math.abs(tilt) - 90) < 2) { l.set(Attr.PAN, pr.panValue((float) refPan)); continue; }
                double bestPan = pan, bestTilt = tilt, bestCost = Double.MAX_VALUE;
                for (int flip = 0; flip < 2; flip++) {
                    double t = flip == 0 ? tilt : (tilt >= 0 ? 180 - tilt : -180 - tilt);
                    if (t < pr.tiltMin() || t > pr.tiltMax()) continue;
                    for (int turn = -2; turn <= 2; turn++) {
                        double c = pan + (flip == 0 ? 0 : 180) + turn * 360;
                        if (Math.abs(c) > pr.panRange / 2) continue;
                        double cost = Math.abs(c - refPan) + 0.5 * Math.abs(t - refTilt);
                        if (cost < bestCost) { bestCost = cost; bestPan = c; bestTilt = t; }
                    }
                }
                l.set(Attr.PAN, pr.panValue((float) bestPan));
                l.set(Attr.TILT, pr.tiltValue((float) bestTilt));
            }
        }
    }
}
