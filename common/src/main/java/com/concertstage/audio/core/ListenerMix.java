package com.concertstage.audio.core;

import com.concertstage.audio.AudioGroups;
import com.concertstage.audio.SpeakerModels;
import java.util.List;

/**
 * 1.1.1 audio fix — what one listener hears of one console (pure maths, no Minecraft; ClientAudio calls it every tick for the local
 * player, the tests call it for any position). Each player computes his own mix from his own position: two players at different
 * distances hear two different mixes of the same synchronised stream.
 *
 * <p>Per array: distance law of its kind ({@link DistanceLaw}), directivity (Acoustics), obstacles, console fader / mute / master,
 * array volume, the level of its type, and for the subwoofers their voicing ({@link SubVoicing}) and the Subs level. Arrays then add
 * up per mixer slot with {@link ArrayMix} (one system = one source, separate systems by energy). Nothing is normalised: the level is
 * what the audible arrays really give at this place.
 *
 * <p>Slots: the nine console groups (main installation and every array that is not a tower), then {@link #SLOTS_PER_TOWER} slots per
 * delay tower (left, right, mono, subs) so each tower keeps the stereo side of its arrays and has its own delay.
 *
 * <p>Delays: each installation's sound reaches the listener at {@code electronic delay + distance / 343 m/s}. The arrival of the
 * earliest audible installation is taken as the reference (the stream is not delayed as a whole: the music stays on the beat of the
 * light show); every other installation is delayed by how much later it really arrives. A tower's electronic delay is its automatic
 * one (distance to the main system / 343 + the trim). In the zone where the main and a tower overlap, both arrive together, the main a
 * few ms first: no echo. The console's group delays are kept on top of this, per group, as before (they are not added twice to a
 * tower: a tower uses its automatic delay unless automatic delays are turned off, then its group's).
 */
public final class ListenerMix {
    public static final int GROUPS = AudioGroups.COUNT, SLOTS_PER_TOWER = 4;
    public static final int SLOTS = GROUPS + SoundSystems.MAX_TOWERS * SLOTS_PER_TOWER;
    /** Level under which an installation does not count as heard for the delay reference (-50 dB). */
    static final double HEARD = .003;
    /** Anti-echo: a tower later than this (ms) after the first sound is turned down, by up to {@link #ECHO_CUT_DB}. */
    public static final double ECHO_MS = 35, ECHO_CUT_DB = 18;

    private ListenerMix() {}

    /** Side of the program each slot plays (-1 left, +1 right, 0 mono sum) and whether it is a subwoofer slot (low pass). */
    public static int side(int slot) {
        if (slot < GROUPS) return AudioGroups.side(slot);
        int k = (slot - GROUPS) % SLOTS_PER_TOWER;
        return k == 0 ? -1 : k == 1 ? 1 : 0;
    }

    public static boolean subSlot(int slot) {
        return slot < GROUPS ? slot == AudioGroups.SUBS : (slot - GROUPS) % SLOTS_PER_TOWER == 3;
    }

    public static int[] sides() {
        int[] s = new int[SLOTS];
        for (int i = 0; i < SLOTS; i++) s[i] = side(i);
        return s;
    }

    public static boolean[] subs() {
        boolean[] s = new boolean[SLOTS];
        for (int i = 0; i < SLOTS; i++) s[i] = subSlot(i);
        return s;
    }

    /** Console values (master, faders, mutes, group delays). */
    public record Desk(double master, float[] faders, boolean[] mutes, float[] groupDelays) {
        double fader(int g) { return g >= 0 && g < faders.length ? faders[g] : 1; }
        boolean muted(int g) { return g >= 0 && g < mutes.length && mutes[g]; }
        float delay(int g) { return g >= 0 && g < groupDelays.length ? groupDelays[g] : 0; }
    }

    /**
     * Listener settings. {@code legacyCurve}: the old single curve when the player chose it (null = the laws of each kind);
     * {@code rangeFactor}: per array range multiplier (families, fills, delays) x the diffusion distance.
     */
    public record Settings(boolean distance, boolean directivity, double curveScale, DistanceCurve legacyCurve, double volume,
                           double levelMains, double levelMonitors, double levelSubs, double spatial, boolean propagation,
                           boolean autoDelays, boolean antiEcho) {}

    /** Per array: its range factor (settings), its volume (array control). */
    public interface ArrayInfo {
        double rangeFactor(int source, SoundSystems.Source s, SoundSystems.Installation inst);
        double volume(int source);
    }

    /** Obstacles between an array and the listener: their amount, asked only for arrays that can be heard. */
    public interface Walls {
        double walls(int source);
        double gain(double walls, boolean sub);
    }

    public static final Walls NO_WALLS = new Walls() {
        public double walls(int source) { return 0; }
        public double gain(double walls, boolean sub) { return 1; }
    };

    /** The result, reused from tick to tick (no allocation in the steady state). */
    public static final class Result {
        public final float[] weights = new float[SLOTS * 2];
        public final float[] delays = new float[SLOTS];
        /** Sum of the array levels before the controls (audibility), weighted distance, obstacles. */
        public double sum, weightedDistance, heardWalls, heard;
        public int contributing, systems;
        /** Crossover of the subs of this console (Hz, from the voicing of its main sub model). */
        public float crossoverHz = 100;
        /** Level of each installation at the listener (index of the layout), for the diagnostics and the tests. */
        public double[] installationLevel = new double[0];
        final double[] slotE = new double[SLOTS];
        // ---- per layout (rebuilt when the layout or the number of arrays changes): slot and system of every array, its law
        Object cachedLayout;
        int cachedSize = -1;
        int[] slotOf = new int[0], systemOf = new int[0], systemSlot = new int[0];
        DistanceLaw[] laws = new DistanceLaw[0];
        double[] lawRf = new double[0];
        double[] sysL = new double[0], sysR = new double[0], instE = new double[0];
        int[] sysCount = new int[0];
        double[] energy = new double[SLOTS * 2];
        double[] arrival = new double[0];
    }

    /** Slot, system (ArrayMix rule: same slot, same model, within 3.5 blocks) and crossover, once per layout. */
    private static void prepare(List<SoundSystems.Source> arrays, SoundSystems.Layout layout, Result out) {
        int n = arrays.size();
        if (out.cachedLayout == layout && out.cachedSize == n) return;
        out.cachedLayout = layout; out.cachedSize = n;
        out.slotOf = new int[n]; out.systemOf = new int[n]; out.laws = new DistanceLaw[n]; out.lawRf = new double[n];
        java.util.Arrays.fill(out.lawRf, Double.NaN);
        for (int i = 0; i < n; i++) {
            SoundSystems.Source a = arrays.get(i);
            int g = a.group(), slot = g;
            SoundSystems.Installation inst = layout.of(i);
            if (inst != null && inst.role() == SoundSystems.Role.TOWER && inst.tower() >= 0 && a.model() != null) {
                int base = GROUPS + inst.tower() * SLOTS_PER_TOWER;
                slot = a.model().isSub() || g == AudioGroups.SUBS ? base + 3 : g == AudioGroups.MAIN_LEFT ? base : g == AudioGroups.MAIN_RIGHT ? base + 1 : base + 2;
            }
            out.slotOf[i] = slot;
        }
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        double r2 = ArrayMix.CLUSTER_BLOCKS * ArrayMix.CLUSTER_BLOCKS;
        for (int i = 0; i < n; i++) {
            SoundSystems.Source a = arrays.get(i);
            for (int j = i + 1; j < n; j++) {
                SoundSystems.Source b = arrays.get(j);
                if (out.slotOf[i] != out.slotOf[j] || a.model() != b.model()) continue;
                double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
                if (dx * dx + dy * dy + dz * dz <= r2) {
                    int ra = i, rb = j;
                    while (parent[ra] != ra) ra = parent[ra];
                    while (parent[rb] != rb) rb = parent[rb];
                    if (ra != rb) parent[Math.max(ra, rb)] = Math.min(ra, rb);
                }
            }
        }
        int systems = 0;
        int[] index = new int[n];
        java.util.Arrays.fill(index, -1);
        for (int i = 0; i < n; i++) {
            int r = i;
            while (parent[r] != r) r = parent[r];
            if (index[r] < 0) index[r] = systems++;
            out.systemOf[i] = index[r];
        }
        out.systemSlot = new int[systems];
        for (int i = 0; i < n; i++) out.systemSlot[out.systemOf[i]] = out.slotOf[i];
        out.sysL = new double[systems]; out.sysR = new double[systems]; out.sysCount = new int[systems];
        // the subs of the console: the voicing of the model with the most cabinets
        java.util.Map<String, Integer> subCount = new java.util.HashMap<>();
        SpeakerModels.Model mainSub = null; int best = 0;
        for (SoundSystems.Source a : arrays)
            if (a.model() != null && a.model().isSub()) {
                int k = subCount.merge(a.model().id(), a.cabinets(), Integer::sum);
                if (k > best) { best = k; mainSub = a.model(); }
            }
        out.crossoverHz = (float) SubVoicing.of(mainSub).crossoverHz();
        int ni = layout.installations().size();
        out.installationLevel = new double[ni];
        out.instE = new double[ni];
    }

    /**
     * Computes the weights (slot * 2 = left, + 1 = right) and the delays (ms) of the slots for a listener at (ex, ey, ez) looking at
     * {@code headingRad} (the game's yaw in radians).
     */
    public static Result compute(List<SoundSystems.Source> arrays, SoundSystems.Layout layout, Desk desk, double ex, double ey, double ez,
                                 double headingRad, Settings set, ArrayInfo info, Walls walls, Result out) {
        if (out == null) out = new Result();
        prepare(arrays, layout, out);
        out.sum = out.weightedDistance = out.heardWalls = out.heard = 0;
        out.contributing = 0;
        java.util.Arrays.fill(out.installationLevel, 0);
        java.util.Arrays.fill(out.instE, 0);
        java.util.Arrays.fill(out.slotE, 0);
        java.util.Arrays.fill(out.sysL, 0); java.util.Arrays.fill(out.sysR, 0); java.util.Arrays.fill(out.sysCount, 0);
        double cosH = Math.cos(headingRad), sinH = Math.sin(headingRad);
        double spatial = Double.isFinite(set.spatial()) ? Math.max(0, Math.min(1, set.spatial())) : 1;
        for (int i = 0; i < arrays.size(); i++) {
            SoundSystems.Source a = arrays.get(i);
            SpeakerModels.Model m = a.model();
            if (m == null) continue;
            int g = a.group();
            if (g < 0 || g >= GROUPS) continue;
            SoundSystems.Installation inst = layout.of(i);
            double dx = ex - a.x(), dy = ey - a.y(), dz = ez - a.z();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double rf = info.rangeFactor(i, a, inst);
            double dg;
            if (!set.distance()) dg = set.legacyCurve() != null ? set.legacyCurve().output() : 1;
            else if (set.legacyCurve() != null) {
                // the old single curve (kept as a choice): its maximum scaled by the range of the model, as 1.1.0 / 1.1.1 did
                DistanceCurve c = set.legacyCurve();
                double modelRange = Acoustics.range(m.isSub(), a.lengthM()) * rf;
                dg = new DistanceCurve(c.reference(), c.maximum() * modelRange / 192, c.rolloff(), c.output()).gain(dist);
            } else {
                if (out.laws[i] == null || out.lawRf[i] != rf) { out.laws[i] = DistanceLaw.of(m, a.lengthM(), rf); out.lawRf[i] = rf; }
                dg = out.laws[i].gain(dist, set.curveScale());
            }
            if (!(dg > 0)) continue;
            double dir = 1;
            if (set.directivity()) {
                double fx = a.fx(), fz = a.fz(), hl = Math.sqrt(dx * dx + dz * dz);
                double angleH = hl < 1e-6 ? 0 : Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, (dx * fx + dz * fz) / hl))));
                double elevation = Math.toDegrees(Math.atan2(-dy, Math.max(.5, hl)));
                dir = Acoustics.directivity(m.isSub(), angleH, elevation, m.hDispersion(), m.vDispersion(), a.pitchTop(), a.pitchBottom(), m.rear());
                // behind a full-range array its vertical pattern still applies (the rear lobe is no wider than the front one): under and
                // behind a delay tower, where its sound arrives late, it is clearly below the mains instead of nearly as loud (an echo)
                // (faded in from 90° to 120° off axis: no step when walking under the array)
                if (!m.isSub() && angleH > 90) {
                    double top = Math.min(a.pitchTop(), a.pitchBottom()) - m.vDispersion() / 2, bottom = Math.max(a.pitchTop(), a.pitchBottom()) + m.vDispersion() / 2;
                    double outV = elevation < top ? top - elevation : elevation > bottom ? elevation - bottom : 0;
                    dir *= 1 - .65 * Acoustics.smooth(outV / 25) * Acoustics.smooth((angleH - 90) / 30);
                }
            }
            double spatialLevel = dg * dir;
            if (!Double.isFinite(spatialLevel) || spatialLevel <= 0) continue;
            out.sum += spatialLevel;
            out.weightedDistance += spatialLevel * dist;
            if (desk.muted(g)) continue;
            double w = walls.walls(i);
            out.heardWalls += w * spatialLevel; out.heard += spatialLevel;
            double type = m.isSub() ? SubVoicing.of(m).gain() * set.levelSubs() : m.isMonitor() ? set.levelMonitors() : set.levelMains();
            double level = desk.master() * desk.fader(g) * info.volume(i) * spatialLevel * walls.gain(w, m.isSub()) * type;
            if (!Double.isFinite(level) || level <= 0) continue;
            int slot = out.slotOf[i];
            // pan from the listener's head (the stream is listener-relative stereo: OpenAL cannot place a stereo stream in 3D)
            double rx = -dx, rz = -dz;
            double pan = Math.max(-1, Math.min(1, (-cosH * rx - sinH * rz) / Math.sqrt(rx * rx + rz * rz + 1) * spatial));
            // ArrayMix: one system = the mean of its elements' amplitudes (+1.5 dB per doubling), systems add by energy
            int sys = out.systemOf[i];
            out.sysL[sys] += level * Math.sqrt((1 - pan) * .5);
            out.sysR[sys] += level * Math.sqrt((1 + pan) * .5);
            out.sysCount[sys]++;
            out.contributing++;
            if (inst != null) out.instE[inst.index()] += level * level;
            out.slotE[slot] += level * level;
        }
        java.util.Arrays.fill(out.energy, 0);
        int systems = 0;
        for (int k = 0; k < out.sysCount.length; k++) {
            int c = out.sysCount[k];
            if (c == 0) continue;
            systems++;
            double gain = Math.pow(10, ArrayMix.ARRAY_GAIN_DB * (Math.log(c) / Math.log(2)) / 20) / c;
            double l = out.sysL[k] * gain, r = out.sysR[k] * gain;
            int slot = out.systemSlot[k];
            out.energy[slot * 2] += l * l;
            out.energy[slot * 2 + 1] += r * r;
        }
        out.systems = systems;
        double v = Double.isFinite(set.volume()) ? Math.max(0, set.volume()) : 0;
        for (int i = 0; i < out.weights.length; i++) out.weights[i] = (float) (Math.sqrt(out.energy[i]) * v);
        for (int i = 0; i < out.instE.length; i++) out.installationLevel[i] = Math.sqrt(out.instE[i]);
        delays(layout, desk, ex, ey, ez, set, out.instE, out);
        return out;
    }

    /** Delays of the slots: group delays of the console + the difference of arrival of each installation (see the class notes). */
    private static void delays(SoundSystems.Layout layout, Desk desk, double ex, double ey, double ez, Settings set, double[] instE, Result out) {
        float[] d = out.delays;
        for (int g = 0; g < GROUPS; g++) d[g] = desk.delay(g);
        // arrival of each installation (ms), from its centre
        int ni = layout.installations().size();
        double earliest = Double.MAX_VALUE;
        if (out.arrival.length != ni) out.arrival = new double[ni];
        double[] arrival = out.arrival;
        double loud = 0;
        for (double e : instE) loud = Math.max(loud, Math.sqrt(e));
        for (SoundSystems.Installation inst : layout.installations()) {
            // without propagation nothing arrives late, so a tower is not delayed either (it would only add an echo)
            double electronic = inst.role() == SoundSystems.Role.TOWER && set.autoDelays() && set.propagation() ? inst.delayMs() : 0;
            double dx = ex - inst.x(), dy = ey - inst.y(), dz = ez - inst.z();
            arrival[inst.index()] = electronic + (set.propagation() ? Math.sqrt(dx * dx + dy * dy + dz * dz) / SoundSystems.SPEED * 1000 : 0);
            double level = Math.sqrt(instE[inst.index()]);
            if (level > HEARD && level > loud * .01 && inst.role() == SoundSystems.Role.TOWER)
                earliest = Math.min(earliest, arrival[inst.index()]);
        }
        boolean towers = false;
        for (SoundSystems.Installation inst : layout.installations()) if (inst.role() == SoundSystems.Role.TOWER) towers = true;
        // the main installation (all its groups) and the towers move relative to each other; the rest keeps its group delays
        // the mains arrive from their acoustic centre (the point the towers' delays are measured from: both use the same reference)
        double mainArrival = Double.NaN;
        if (layout.hasMain()) {
            double dx = ex - layout.mainX(), dy = ey - layout.mainY(), dz = ez - layout.mainZ();
            mainArrival = set.propagation() ? Math.sqrt(dx * dx + dy * dy + dz * dz) / SoundSystems.SPEED * 1000 : 0;
            boolean heard = false;
            for (SoundSystems.Installation inst : layout.installations())
                if (inst.role() == SoundSystems.Role.MAIN && Math.sqrt(instE[inst.index()]) > HEARD) heard = true;
            if (heard) earliest = Math.min(earliest, mainArrival);
            for (SoundSystems.Installation inst : layout.installations()) if (inst.role() == SoundSystems.Role.MAIN) arrival[inst.index()] = mainArrival;
        }
        if (!towers || earliest == Double.MAX_VALUE || Double.isNaN(mainArrival)) {
            for (int s = GROUPS; s < SLOTS; s++) d[s] = 0;
            return;
        }
        double mainShift = mainArrival - earliest;
        if (mainShift > 0) for (int g = 0; g < GROUPS; g++) d[g] = (float) Math.min(1500, d[g] + mainShift);
        for (int s = GROUPS; s < SLOTS; s++) d[s] = 0;
        for (SoundSystems.Installation inst : layout.installations()) {
            if (inst.role() != SoundSystems.Role.TOWER || inst.tower() < 0) continue;
            // a tower with automatic delays off keeps the delay of its arrays' group on the console (the old manual way)
            double manual = set.autoDelays() ? 0 : desk.delay(AudioGroups.DELAY);
            float v = (float) Math.max(0, Math.min(1500, arrival[inst.index()] - earliest + manual));
            int base = GROUPS + inst.tower() * SLOTS_PER_TOWER;
            for (int k = 0; k < SLOTS_PER_TOWER; k++) d[base + k] = Math.max(d[base + k], v);
            // anti-echo: a tower heard more than 35 ms after the first sound (behind it, beside it, or a far tower heard near another)
            // would be heard as an echo; from 35 to 80 ms late it is turned down progressively, by up to 18 dB. In front of a tower,
            // where it is meant to be heard, it arrives 10-30 ms late and is not touched
            double late = arrival[inst.index()] + manual - earliest;
            if (set.antiEcho() && late > ECHO_MS) {
                float k = (float) Math.pow(10, -ECHO_CUT_DB * Acoustics.smooth((late - ECHO_MS) / 45) / 20);
                for (int j = 0; j < SLOTS_PER_TOWER * 2; j++) out.weights[base * 2 + j] *= k;
                out.installationLevel[inst.index()] *= k;
            }
        }
    }

    /**
     * Applies new delays only where they moved by more than {@code stepMs} (a delay line changes its tap by a 20 ms crossfade:
     * walking changes the arrivals continuously, a crossfade every tick would colour the sound). Returns true if something changed.
     */
    public static boolean settle(float[] target, float[] applied, float stepMs) {
        boolean changed = false;
        for (int i = 0; i < Math.min(target.length, applied.length); i++)
            if (Math.abs(target[i] - applied[i]) > stepMs || target[i] == 0 && applied[i] != 0) { applied[i] = target[i]; changed = true; }
        return changed;
    }
}
