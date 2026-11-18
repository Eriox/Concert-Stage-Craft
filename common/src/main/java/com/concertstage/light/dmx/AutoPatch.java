package com.concertstage.light.dmx;

import com.concertstage.light.core.FixtureProfile;
import com.concertstage.light.core.Show;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AUTO PATCH — 1 UNIVERSE (CSC SINGLE UNIVERSE MODE): fits the fixtures of a show into one DMX universe of 512 channels.
 *
 * <ol>
 * <li>Every fixture (or shared address, see 3) starts in the preferred mode.</li>
 * <li>While the total is over 512, the fixture type whose next smaller mode saves the most channels goes down one mode (Full →
 * Compact → Ultra Compact), all fixtures of that type together.</li>
 * <li>When sharing is asked for, fixtures share addresses first: by type (all fixtures of a type get the same address and the same
 * values) or by the groups of the show (the fixtures of one group and one type share; fixtures in no group keep their own address);
 * the modes are then reduced only if the shared patch still does not fit.</li>
 * <li>With the Virtual Matrix option, every fixture follows the 16 matrix channels instead.</li>
 * </ol>
 * Addresses are given in order: type (VORTEX, W7, W19, PULSESTRIKE), then fixture number. A shared address is only ever shared by
 * fixtures of the same type and mode. Deterministic: the same show gives the same plan.
 */
public final class AutoPatch {
    private AutoPatch() {}

    public static final int SHARE_NONE = 0, SHARE_BY_TYPE = 1, SHARE_BY_GROUP = 2, MATRIX = 3;
    public static final String[] SHARE_NAMES = {"Individual", "Share by type", "Share by group", "Virtual Matrix"};
    public static final int UNIVERSE_SIZE = 512;

    /** One fixture of the plan. */
    public record Entry(int fid, int universe, int address, int mode, String shareKey) {}

    /** The plan and what it costs. {@code fits}: all of it in {@code universe}; {@code used}: channels used. */
    public record Plan(List<Entry> entries, int used, boolean fits, int share, String report) {}

    public static Plan plan(Show show, int universe, int preferredMode, int share) {
        List<Show.Patch> fx = new ArrayList<>(show.patch);
        fx.sort(Comparator.comparingInt((Show.Patch p) -> typeOrder(p.profile())).thenComparingInt(p -> p.fid));
        if (share == MATRIX) {
            List<Entry> e = new ArrayList<>();
            for (Show.Patch p : fx) e.add(new Entry(p.fid, universe, 1, DmxMode.MATRIX, "matrix"));
            int addr = Math.max(1, Math.min(UNIVERSE_SIZE - VirtualMatrix.CHANNELS + 1, show.external.matrixAddress));
            return new Plan(e, VirtualMatrix.CHANNELS, true, MATRIX,
                fx.size() + " fixtures follow the Virtual Matrix: 16 channels at " + universe + "." + addr);
        }
        int pref = Math.max(DmxMode.FULL, Math.min(DmxMode.ULTRA, preferredMode));
        int[] shares = {share};                                     // sharing is used when asked for (the user chose it), not only as a last resort
        Plan last = null;
        for (int sh : shares) {
            Map<String, List<Show.Patch>> units = units(show, fx, sh);
            int[] mode = new int[FixtureProfile.all().size()];
            java.util.Arrays.fill(mode, pref);
            // rev. 25: the blinders are patched on one channel, their dimmer (halogen lamp colour), as on a real rig
            for (FixtureProfile fp : FixtureProfile.all()) if (DmxMode.tungsten(fp)) mode[typeOrder(fp)] = DmxMode.ULTRA;
            while (true) {
                int used = used(units, mode);
                if (used <= UNIVERSE_SIZE) {
                    upgrade(units, mode, pref);
                    return build(units, mode, universe, sh, used(units, mode), true);
                }
                // rev. 17 (VORTEX stuck in Ultra Compact, 11 November): Ultra Compact keeps only position, dimmer and a colour macro, so
                // every type goes down to Compact before any type goes down to Ultra Compact. Before, the type that saved the most
                // channels went first, whatever it lost: a rig with many VORTEX sent all of them to Ultra (14 -> 4 channels each)
                // while the washes stayed in Compact.
                int bestType = -1, bestSave = 0, bestFrom = Integer.MAX_VALUE;
                for (int t = 0; t < mode.length; t++) {
                    if (mode[t] >= DmxMode.ULTRA) continue;
                    int save = 0;
                    for (Map.Entry<String, List<Show.Patch>> u : units.entrySet()) {
                        FixtureProfile p = u.getValue().get(0).profile();
                        if (typeOrder(p) != t) continue;
                        save += DmxMode.of(p, mode[t]).footprint() - DmxMode.of(p, mode[t] + 1).footprint();
                    }
                    if (save <= 0) continue;
                    if (mode[t] < bestFrom || mode[t] == bestFrom && save > bestSave) { bestFrom = mode[t]; bestSave = save; bestType = t; }
                }
                if (bestType < 0) { last = build(units, mode, universe, sh, used, false); break; }
                mode[bestType]++;
            }
        }
        return last;
    }

    /**
     * Rev. 17: once the patch fits, every type that was taken down further than needed goes back up (Ultra Compact first, then
     * Compact), never above the preferred mode, as long as the universe still holds it. Types in the order of the patch.
     */
    private static boolean blinderType(int t) {
        for (FixtureProfile fp : FixtureProfile.all()) if (typeOrder(fp) == t) return DmxMode.tungsten(fp);
        return false;
    }

    private static void upgrade(Map<String, List<Show.Patch>> units, int[] mode, int pref) {
        for (int target = DmxMode.ULTRA - 1; target >= pref; target--) {
            boolean changed = true;
            while (changed) {
                changed = false;
                for (int t = 0; t < mode.length; t++) {
                    if (mode[t] != target + 1 || blinderType(t)) continue;
                    mode[t] = target;
                    if (used(units, mode) <= UNIVERSE_SIZE) changed = true;
                    else mode[t] = target + 1;
                }
            }
        }
    }

    /**
     * Rev. 17: new DMX mode for some fixtures without running the auto patch (which chooses the modes itself). Each fixture keeps
     * its address when its new footprint is free there; otherwise it moves to the first free place of its universe, then of the
     * next ones. The other fixtures do not move. Returns {fid, universe, address, mode} for each fixture (a PATCH_BULK plan).
     */
    public static List<int[]> changeMode(Show show, int[] fids, int mode) {
        java.util.Set<Integer> moving = new java.util.HashSet<>();
        for (int f : fids) if (show.fixture(f) != null) moving.add(f);
        Map<Integer, java.util.BitSet> taken = new java.util.HashMap<>();
        for (Show.Patch p : show.patch) {
            if (moving.contains(p.fid) || p.dmxMode == DmxMode.MATRIX) continue;
            mark(taken, p.universe, p.address, p.footprint());
        }
        List<int[]> plan = new ArrayList<>();
        List<Show.Patch> order = new ArrayList<>();
        for (Show.Patch p : show.patch) if (moving.contains(p.fid)) order.add(p);
        order.sort(Comparator.comparingInt((Show.Patch p) -> p.universe).thenComparingInt(p -> p.address).thenComparingInt(p -> p.fid));
        for (Show.Patch p : order) {
            int foot = DmxMode.of(p.profile(), mode).footprint();
            int u = p.universe, a = p.address;
            if (mode == DmxMode.MATRIX || foot == 0) { plan.add(new int[] {p.fid, u, a, mode}); continue; }
            if (!free(taken, u, a, foot)) {
                a = -1;
                for (int uu = u; uu <= ArtNetSettings.MAX_UNIVERSE && a < 0; uu++) {
                    for (int s = 1; s + foot - 1 <= UNIVERSE_SIZE; s++) if (free(taken, uu, s, foot)) { a = s; u = uu; break; }
                }
                if (a < 0) { u = p.universe; a = p.address; }
            }
            mark(taken, u, a, foot);
            plan.add(new int[] {p.fid, u, a, mode});
        }
        return plan;
    }

    private static void mark(Map<Integer, java.util.BitSet> taken, int u, int a, int foot) {
        java.util.BitSet b = taken.computeIfAbsent(u, k -> new java.util.BitSet(UNIVERSE_SIZE));
        for (int c = a; c < a + foot && c <= UNIVERSE_SIZE; c++) b.set(c - 1);
    }

    private static boolean free(Map<Integer, java.util.BitSet> taken, int u, int a, int foot) {
        if (a < 1 || a + foot - 1 > UNIVERSE_SIZE) return false;
        java.util.BitSet b = taken.get(u);
        if (b == null) return true;
        int next = b.nextSetBit(a - 1);
        return next < 0 || next >= a - 1 + foot;
    }

    /** Addressing units: one per fixture, or one per shared address. */
    private static Map<String, List<Show.Patch>> units(Show show, List<Show.Patch> fx, int share) {
        Map<String, List<Show.Patch>> u = new LinkedHashMap<>();
        for (Show.Patch p : fx) {
            String key;
            if (share == SHARE_BY_TYPE) key = "type:" + p.type;
            else if (share == SHARE_BY_GROUP) {
                Show.Group g = firstGroup(show, p.fid);
                key = g == null ? "fixture:" + p.fid : "group:" + g.id + ":" + p.type;
            } else key = "fixture:" + p.fid;
            u.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }
        return u;
    }

    private static Show.Group firstGroup(Show show, int fid) {
        for (Show.Group g : show.groups) for (int f : g.fids) if (f == fid) return g;
        return null;
    }

    private static int used(Map<String, List<Show.Patch>> units, int[] mode) {
        int n = 0;
        for (List<Show.Patch> l : units.values()) {
            FixtureProfile p = l.get(0).profile();
            n += DmxMode.of(p, mode[typeOrder(p)]).footprint();
        }
        return n;
    }

    private static Plan build(Map<String, List<Show.Patch>> units, int[] mode, int universe, int share, int used, boolean fits) {
        List<Entry> out = new ArrayList<>();
        int addr = 1, u = universe;
        for (Map.Entry<String, List<Show.Patch>> e : units.entrySet()) {
            FixtureProfile p = e.getValue().get(0).profile();
            int m = mode[typeOrder(p)];
            int foot = DmxMode.of(p, m).footprint();
            if (!fits && addr + foot - 1 > UNIVERSE_SIZE) { u++; addr = 1; }          // does not fit: the rest goes on to the next universes
            for (Show.Patch f : e.getValue()) out.add(new Entry(f.fid, u, addr, m, e.getValue().size() > 1 ? e.getKey() : ""));
            addr += foot;
        }
        StringBuilder r = new StringBuilder();
        r.append(used).append(" / ").append(UNIVERSE_SIZE).append(" channels");
        r.append(fits ? " — fits in universe " + universe : " — does NOT fit in one universe (" + (used - UNIVERSE_SIZE) + " too many): continued on the next universes");
        String[] names = FixtureProfile.all().stream().map(p -> p.name.replace("CSC ", "")).toArray(String[]::new);
        for (int t = 0; t < mode.length; t++) {
            int n = 0;
            for (List<Show.Patch> l : units.values()) if (typeOrder(l.get(0).profile()) == t) n += l.size();
            if (n > 0) r.append(" · ").append(n).append(" ").append(names[t]).append(" ").append(DmxMode.NAMES[mode[t]]);
        }
        int shared = 0;
        for (List<Show.Patch> l : units.values()) if (l.size() > 1) shared += l.size();
        if (shared > 0) r.append(" · ").append(shared).append(" fixtures share ").append(countShared(units)).append(" addresses (same values)");
        return new Plan(out, used, fits, share, r.toString());
    }

    private static int countShared(Map<String, List<Show.Patch>> units) {
        int n = 0;
        for (List<Show.Patch> l : units.values()) if (l.size() > 1) n++;
        return n;
    }

    static int typeOrder(FixtureProfile p) {
        return FixtureProfile.indexOf(p);
    }

    // ------------------------------------------------------------ checks
    /** A problem of the patch: two different fixtures (not a deliberate share) using the same channels, or a fixture past channel 512. */
    public record Conflict(int fidA, int fidB, int universe, int from, int to, String text) {}

    public static List<Conflict> conflicts(Show show) {
        List<Conflict> out = new ArrayList<>();
        List<Show.Patch> l = new ArrayList<>(show.patch);
        for (Show.Patch p : l) {
            int foot = p.footprint();
            if (foot > 0 && p.address + foot - 1 > UNIVERSE_SIZE)
                out.add(new Conflict(p.fid, -1, p.universe, p.address, p.address + foot - 1, "fixture " + p.fid + " ends past channel 512"));
        }
        for (int i = 0; i < l.size(); i++) for (int j = i + 1; j < l.size(); j++) {
            Show.Patch a = l.get(i), b = l.get(j);
            if (a.universe != b.universe) continue;
            int fa = a.footprint(), fb = b.footprint();
            if (fa == 0 || fb == 0) continue;
            int lo = Math.max(a.address, b.address), hi = Math.min(a.address + fa - 1, b.address + fb - 1);
            if (lo > hi) continue;
            boolean share = a.address == b.address && a.type.equals(b.type) && a.dmxMode == b.dmxMode;
            if (!share) out.add(new Conflict(a.fid, b.fid, a.universe, lo, hi, "fixtures " + a.fid + " and " + b.fid + " overlap on " + a.universe + "." + lo + "-" + hi));
        }
        return out;
    }

    /** Channels used in each universe (a shared address counted once). */
    public static Map<Integer, Integer> usage(Show show) {
        Map<Integer, java.util.BitSet> bits = new java.util.TreeMap<>();
        for (Show.Patch p : show.patch) {
            int foot = p.footprint();
            java.util.BitSet b = bits.computeIfAbsent(p.universe, k -> new java.util.BitSet(512));
            for (int c = p.address; c < p.address + foot && c <= 512; c++) b.set(c - 1);
        }
        boolean matrix = false;
        for (Show.Patch p : show.patch) if (p.dmxMode == DmxMode.MATRIX) matrix = true;
        if (matrix) {
            java.util.BitSet b = bits.computeIfAbsent(show.external.matrixUniverse, k -> new java.util.BitSet(512));
            for (int c = show.external.matrixAddress; c < show.external.matrixAddress + VirtualMatrix.CHANNELS && c <= 512; c++) b.set(c - 1);
        }
        Map<Integer, Integer> out = new java.util.TreeMap<>();
        for (Map.Entry<Integer, java.util.BitSet> e : bits.entrySet()) out.put(e.getKey(), e.getValue().cardinality());
        return out;
    }
}
