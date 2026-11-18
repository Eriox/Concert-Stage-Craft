package com.concertstage.audio.core;

import com.concertstage.audio.SpeakerModels;
import java.util.ArrayList;
import java.util.List;

/**
 * 1.1.1 audio fix — where the delay towers of a console should stand (pure maths, no Minecraft; shown in the chat and the Diag. tab).
 *
 * <p>The level of the mains is followed along their aim, at ear height, from the front of house ({@link #FOH} blocks from the hangs,
 * the reference) into the audience. Where it has fallen {@link #DROP_DB} dB under the front of house, the audience needs a tower: one
 * is placed {@link #LEAD} blocks before that point (a hang 7 blocks up starts to cover about that far in front of it), and the walk
 * goes on with that tower sounding, until the end of the mains' range (at most {@link #DEPTH} blocks). The towers already built are
 * then compared with these places, and the deepest dip of the real installation is reported.
 *
 * <p>Distances are counted from the mains (the hangs, the front of the stage), along their aim. Game levels (DistanceLaw, Acoustics),
 * not measured sound pressure.
 */
public final class TowerAdvice {
    public static final double FOH = 25, DROP_DB = 6, LEAD = 8, DEPTH = 300, MIN_GAP = 20;

    /** One built tower: its distance from the mains, the recommended one it is compared with (NaN if none), the gap (blocks). */
    public record Built(int tower, double distance, double recommended, double offset) {}

    /**
     * @param recommended recommended distances of the towers from the mains (blocks), first to last
     * @param built the towers already there, compared with the recommendation
     * @param dipDb deepest fall under the target (dB, 0 = none) with the towers already built, and where (blocks from the mains)
     * @param spacing usual distance between two towers (blocks), NaN with fewer than two
     */
    public record Advice(List<Double> recommended, List<Built> built, double dipDb, double dipAt, double spacing, double depth) {
        public static final Advice NONE = new Advice(List.of(), List.of(), 0, Double.NaN, Double.NaN, 0);
        public boolean any() { return !recommended.isEmpty() || !built.isEmpty(); }
    }

    private record Emitter(SpeakerModels.Model model, double length, double x, double y, double z, double yaw, double top, double bottom) {}

    private TowerAdvice() {}

    public static Advice of(List<SoundSystems.Source> sources, SoundSystems.Layout layout, double curveScale) {
        if (!layout.hasMain()) return Advice.NONE;
        double ax = layout.aimX(), az = layout.aimZ(), mx = layout.mainX(), mz = layout.mainZ(), ey = layout.earY();
        double yaw = Math.toDegrees(Math.atan2(-ax, az));
        // the mains (full-range left / right arrays of the main system) and the towers built
        List<Emitter> mains = new ArrayList<>();
        List<List<Emitter>> towers = new ArrayList<>();
        List<Double> towerDist = new ArrayList<>();
        SpeakerModels.Model towerModel = null; int towerCab = 0;
        SpeakerModels.Model mainModel = null; int mainCab = 0;
        for (int i = 0; i < sources.size(); i++) {
            SoundSystems.Source s = sources.get(i);
            SoundSystems.Installation inst = layout.of(i);
            if (inst == null || s.model() == null || s.model().isSub() || s.model().isMonitor()) continue;
            Emitter e = new Emitter(s.model(), s.lengthM(), s.x(), s.y(), s.z(), s.yaw(), s.pitchTop(), s.pitchBottom());
            if (inst.role() == SoundSystems.Role.MAIN && (s.group() == com.concertstage.audio.AudioGroups.MAIN_LEFT || s.group() == com.concertstage.audio.AudioGroups.MAIN_RIGHT)) {
                mains.add(e);
                if (s.cabinets() > mainCab) { mainCab = s.cabinets(); mainModel = s.model(); }
            } else if (inst.role() == SoundSystems.Role.TOWER) {
                while (towers.size() <= inst.index()) { towers.add(null); towerDist.add(Double.NaN); }
                if (towers.get(inst.index()) == null) { towers.set(inst.index(), new ArrayList<>()); towerDist.set(inst.index(), (inst.x() - mx) * ax + (inst.z() - mz) * az); }
                towers.get(inst.index()).add(e);
                if (s.cabinets() > towerCab) { towerCab = s.cabinets(); towerModel = s.model(); }
            }
        }
        if (mains.isEmpty()) return Advice.NONE;
        // the range of the mains: where the audience can still be (the end of the walk)
        double depth = 0;
        for (Emitter e : mains) depth = Math.max(depth, DistanceLaw.of(e.model(), e.length(), 1).range() * .9);
        depth = Math.min(DEPTH, depth);
        // a virtual tower: the model of the towers already built, else the model of the mains with 3/4 of their cabinets
        SpeakerModels.Model vm = towerModel != null ? towerModel : mainModel;
        int vc = towerModel != null ? towerCab : Math.max(4, (int) Math.round(mainCab * .75));
        double vlen = vm.height() * vc;
        double target = level(mains, List.of(), mx + ax * FOH, ey, mz + az * FOH, curveScale) * Math.pow(10, -DROP_DB / 20);
        // ---- the recommendation: mains alone, towers added where the level falls under the target
        List<Emitter> virtual = new ArrayList<>();
        List<Double> rec = new ArrayList<>();
        double last = -1e9;
        for (double d = FOH; d <= depth && rec.size() < 8; d += 1) {
            double lvl = level(mains, virtual, mx + ax * d, ey, mz + az * d, curveScale);
            if (lvl >= target) continue;
            double at = Math.max(d - LEAD, last + MIN_GAP);
            if (at > depth - LEAD) break;
            rec.add(at);
            last = at;
            double tx = mx + ax * at, tz = mz + az * at, lx = az, lz = -ax;   // lateral unit vector
            virtual.add(new Emitter(vm, vlen, tx - lx * 5, ey + 7, tz - lz * 5, yaw, 2, 25));   // a tower hang curved down to its audience
            virtual.add(new Emitter(vm, vlen, tx + lx * 5, ey + 7, tz + lz * 5, yaw, 2, 25));
            d = Math.max(d, at + LEAD);                                     // the new tower covers from here on
        }
        // ---- the towers built: compared with the recommendation in order, and the deepest dip of the real installation
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < towers.size(); i++) if (towers.get(i) != null) order.add(i);
        order.sort(java.util.Comparator.comparingDouble(towerDist::get));
        List<Built> built = new ArrayList<>();
        List<Emitter> real = new ArrayList<>();
        for (int k = 0; k < order.size(); k++) {
            double dist = towerDist.get(order.get(k));
            double r = k < rec.size() ? rec.get(k) : Double.NaN;
            built.add(new Built(k, dist, r, Double.isNaN(r) ? Double.NaN : dist - r));
            real.addAll(towers.get(order.get(k)));
        }
        double dip = 0, dipAt = Double.NaN;
        double end = order.isEmpty() ? depth : Math.min(depth, towerDist.get(order.get(order.size() - 1)) + 40);
        for (double d = FOH; d <= end; d += 1) {
            double lvl = level(mains, real, mx + ax * d, ey, mz + az * d, curveScale);
            double under = 20 * Math.log10(target / Math.max(1e-9, lvl));
            if (under > dip) { dip = under; dipAt = d; }
        }
        double spacing = rec.size() >= 2 ? (rec.get(rec.size() - 1) - rec.get(0)) / (rec.size() - 1) : Double.NaN;
        return new Advice(List.copyOf(rec), List.copyOf(built), dip, dipAt, spacing, depth);
    }

    /** Level (linear, energy sum) of the mains and towers at a point, as ListenerMix would give it without controls. */
    static double level(List<Emitter> mains, List<Emitter> towers, double x, double y, double z, double scale) {
        double e = 0;
        for (List<Emitter> list : List.of(mains, towers))
            for (Emitter s : list) {
                double dx = x - s.x(), dy = y - s.y(), dz = z - s.z(), dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                double g = DistanceLaw.of(s.model(), s.length(), 1).gain(dist, scale);
                if (!(g > 0)) continue;
                double yr = Math.toRadians(s.yaw()), fx = -Math.sin(yr), fz = Math.cos(yr), hl = Math.sqrt(dx * dx + dz * dz);
                double angleH = hl < 1e-6 ? 0 : Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, (dx * fx + dz * fz) / hl))));
                double el = Math.toDegrees(Math.atan2(-dy, Math.max(.5, hl)));
                SpeakerModels.Model m = s.model();
                g *= Acoustics.directivity(false, angleH, el, m.hDispersion(), m.vDispersion(), s.top(), s.bottom(), m.rear());
                e += g * g;
            }
        return Math.sqrt(e);
    }
}
