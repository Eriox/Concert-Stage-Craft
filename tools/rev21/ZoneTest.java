import com.concertstage.light.analysis.Analysis;
import com.concertstage.light.analysis.AutoShow;
import com.concertstage.light.core.Attr;
import com.concertstage.light.core.Commands;
import com.concertstage.light.core.Engine;
import com.concertstage.light.core.FixtureProfile;
import com.concertstage.light.core.Live;
import com.concertstage.light.core.Look;
import com.concertstage.light.core.Show;
import com.concertstage.light.core.ZoneStrobe;
import com.concertstage.light.dmx.DmxData;
import com.concertstage.light.dmx.DmxMode;

import java.util.Locale;

/**
 * CSC 1.2.0 rev. 21 — the three light zones of the PULSESTRIKE M and the FLASHCORE FX (top RGB bar, centre white strobe, bottom RGB bar):
 * the tests of the specification (each zone alone, colours, strobes per zone, all on / all off, cells), DMX Full and Compact, the
 * layouts before rev. 21 kept for old shows (migration), the desk programmer, the AutoShow looks, save and reload. Pure Java, no
 * Minecraft: what is drawn is checked through ZoneStrobe (the function the renderer uses each frame). Usage: sh tools/rev21/run.sh
 */
public class ZoneTest {
    static int passed, failed;

    static void check(boolean ok, String what) {
        if (ok) { passed++; System.out.println("  ok   " + what); } else { failed++; System.out.println("  FAIL " + what); }
    }

    static void near(double a, double b, double tol, String what) {
        check(Math.abs(a - b) <= tol, what + String.format(Locale.ROOT, " (%.3f ~ %.3f)", a, b));
    }

    public static void main(String[] args) {
        for (FixtureProfile p : new FixtureProfile[] {FixtureProfile.PULSESTRIKE_M, FixtureProfile.FLASHCORE_FX}) {
            System.out.println("== " + p.name);
            layout(p);
            dmxFull(p);
            dmxCompact(p);
            strobes(p);
            programmer(p);
        }
        migration();
        autoShow();
        others();
        System.out.println();
        System.out.println("passed " + passed + ", failed " + failed);
        if (failed > 0) System.exit(1);
    }

    // ------------------------------------------------------------------ helpers
    static Show show(FixtureProfile p, int mode) {
        Show s = new Show();
        s.id = "zones";
        Commands.apply(s, new Live(), Commands.op(Commands.PATCH_ADD).varInt(1).str(p.id).str("").sInt(0).sInt(70).sInt(0).f32(0).varInt(1).toBytes(), 0, null);
        Show.Patch pa = s.fixture(1);
        pa.universe = 1; pa.address = 1; pa.dmxMode = mode;
        return s;
    }

    static Live external() {
        Live l = new Live();
        l.control = l.previousControl = Live.EXTERNAL;
        return l;
    }

    static void set(byte[] u, DmxMode m, Attr a, int v) {
        for (int c : m.channelsOf(a)) u[c - 1] = (byte) v;
    }

    static Engine.Output dmx(Show s, byte[] u) {
        DmxData d = new DmxData();
        d.set(1, u);
        return new Engine().evaluate(s, external(), 5000, Engine.Music.NONE, d).get(1);
    }

    /** Mean level of the cells of a zone in the engine output (colour ignored). */
    static float level(FixtureProfile p, float[] zones, int k) {
        float sum = 0; int n = 0;
        for (int z = 0; z < p.zones; z++) if (p.section(z) == k) { sum += zones[z * 4 + 3]; n++; }
        return n == 0 ? 0 : sum / n;
    }

    /** Mean colour of a zone (r, g, b x level). */
    static float[] colour(FixtureProfile p, float[] zones, int k) {
        float[] c = new float[3]; int n = 0;
        for (int z = 0; z < p.zones; z++) if (p.section(z) == k) { for (int i = 0; i < 3; i++) c[i] += zones[z * 4 + i] * zones[z * 4 + 3]; n++; }
        for (int i = 0; i < 3; i++) c[i] /= Math.max(1, n);
        return c;
    }

    /** What the renderer draws at time t: cell levels x intensity of the frame. */
    static float[] drawn(FixtureProfile p, Engine.Output o, double t) {
        float[] z = new float[o.zones.length], beam = new float[4];
        float in = ZoneStrobe.frame(p, o.get(Attr.DIMMER), o.zones, o.sectionHz, o.alternate, t, z, beam);
        float[] lv = new float[3];
        for (int k = 0; k < 3; k++) lv[k] = level(p, z, k) * in;
        return lv;
    }

    static byte[] zonesOn(DmxMode m, int top, int mid, int bot) {
        byte[] u = new byte[512];
        for (DmxMode.Channel c : m.channels) {
            int i = java.util.Arrays.asList(m.channels).indexOf(c);
            u[i] = (byte) c.def();
        }
        set(u, m, Attr.TOP_DIM, top); set(u, m, Attr.MID_DIM, mid); set(u, m, Attr.BOT_DIM, bot);
        return u;
    }

    // ------------------------------------------------------------------ tests
    static void layout(FixtureProfile p) {
        int[] n = new int[3];
        for (int z = 0; z < p.zones; z++) n[p.section(z)]++;
        check(p.hasSections() && n[0] > 0 && n[1] > 0 && n[2] > 0, "three zones from the model: top " + n[0] + " cells, centre " + n[1] + " segments, bottom " + n[2] + " cells");
        boolean kinds = true;
        for (int z = 0; z < p.zones; z++) kinds &= (p.section(z) == FixtureProfile.MID) == (p.zoneKind(z) == 1);
        check(kinds, "the centre is the white segments, the bars the RGB cells");
        // rev. 24: the 12-channel Compact of rev. 21 is kept as COMPACT_Z3 (the Compact is now two instances, tools/rev24)
        for (int mode : new int[] {DmxMode.FULL, DmxMode.COMPACT_Z3}) {
            DmxMode m = DmxMode.of(p, mode);
            boolean[] used = new boolean[m.footprint()];
            boolean ok = true;
            for (Attr a : Attr.ALL) for (int c : m.channelsOf(a)) { ok &= !used[c - 1]; used[c - 1] = true; }
            check(ok, m.name() + ": no channel carries two attributes (" + m.footprint() + " channels)");
            check(m.channelsOf(Attr.DIMMER).length == 0, m.name() + ": no master dimmer (the zones are the dimmers)");
            for (Attr a : new Attr[] {Attr.TOP_DIM, Attr.TOP_RED, Attr.TOP_GREEN, Attr.TOP_BLUE, Attr.MID_DIM, Attr.BOT_DIM, Attr.BOT_RED, Attr.BOT_GREEN, Attr.BOT_BLUE})
                check(m.channelsOf(a).length == 1, m.name() + " carries " + a.label);
            for (Attr a : new Attr[] {Attr.TOP_STROBE, Attr.MID_STROBE, Attr.BOT_STROBE})
                check((m.channelsOf(a).length == 1) == (mode == DmxMode.FULL), m.name() + (mode == DmxMode.FULL ? " carries " : " shares the strobe instead of ") + a.label);
            check(m.instances().size() == 3, m.name() + ": one sub-fixture per zone " + m.instances().stream().map(DmxMode.Instance::name).toList());
        }
        int cells = 0;
        for (DmxMode.Channel c : DmxMode.of(p, DmxMode.FULL).channels) if (c.zone() >= 0) cells++;
        check(cells == p.rgbZones * 3 + p.whiteZones, "Full keeps every cell / segment (" + cells + " channels)");
        check(DmxMode.of(p, DmxMode.FULL_V1).footprint() > 0 && DmxMode.of(p, DmxMode.COMPACT_V1).footprint() > 0, "layouts before rev. 21 available");
    }

    static void dmxFull(FixtureProfile p) {
        DmxMode m = DmxMode.of(p, DmxMode.FULL);
        Show s = show(p, DmxMode.FULL);
        // 1-3: each zone alone
        String[] nm = {"top bar", "centre", "bottom bar"};
        for (int k = 0; k < 3; k++) {
            Engine.Output o = dmx(s, zonesOn(m, k == 0 ? 255 : 0, k == 1 ? 255 : 0, k == 2 ? 255 : 0));
            float[] l = drawn(p, o, 1.0);
            check(l[k] > 0.9f && l[(k + 1) % 3] < 1e-4f && l[(k + 2) % 3] < 1e-4f, "Full: " + nm[k] + " alone (" + fmt(l) + ")");
        }
        // 4: different colours top / bottom
        byte[] u = zonesOn(m, 255, 0, 255);
        set(u, m, Attr.TOP_RED, 255); set(u, m, Attr.TOP_GREEN, 0); set(u, m, Attr.TOP_BLUE, 0);
        set(u, m, Attr.BOT_RED, 0); set(u, m, Attr.BOT_GREEN, 0); set(u, m, Attr.BOT_BLUE, 255);
        Engine.Output o = dmx(s, u);
        float[] tc = colour(p, o.zones, 0), bc = colour(p, o.zones, 2);
        check(tc[0] > 0.9f && tc[2] < 0.01f && bc[2] > 0.9f && bc[0] < 0.01f, "Full: top red, bottom blue (" + fmt(tc) + " / " + fmt(bc) + ")");
        // 8 / 9: all on, all off
        float[] all = drawn(p, dmx(s, zonesOn(m, 255, 255, 255)), 1.0);
        check(all[0] > 0.9f && all[1] > 0.9f && all[2] > 0.9f, "Full: every zone on (" + fmt(all) + ")");
        Engine.Output off = dmx(s, zonesOn(m, 0, 0, 0));
        float[] z = new float[off.zones.length], beam = new float[4];
        float in = ZoneStrobe.frame(p, off.get(Attr.DIMMER), off.zones, off.sectionHz, off.alternate, 1.0, z, beam);
        check(in == 0f && beam[0] + beam[1] + beam[2] == 0f, "Full: every zone off -> no light thrown, nothing lit");
        // half levels: dimmer distinct from strobe
        float[] half = drawn(p, dmx(s, zonesOn(m, 128, 64, 255)), 1.0);
        near(half[0], 128 / 255.0, 0.01, "Full: top at 50 %");
        near(half[1], 64 / 255.0, 0.01, "Full: centre at 25 %");
        // cells: a desk drawing pixels (zone colour at 0, own channel up)
        u = zonesOn(m, 255, 255, 255);
        set(u, m, Attr.TOP_RED, 0); set(u, m, Attr.TOP_GREEN, 0); set(u, m, Attr.TOP_BLUE, 0);
        int firstTopCell = -1;
        for (int i = 0; i < m.channels.length; i++) if (m.channels[i].kind() == DmxMode.Kind.ZONE_G && p.section(m.channels[i].zone()) == FixtureProfile.TOP) { firstTopCell = i; break; }
        u[firstTopCell] = (byte) 255;
        o = dmx(s, u);
        int cz = m.channels[firstTopCell].zone();
        int other = -1;
        for (int zz = 0; zz < p.zones; zz++) if (p.section(zz) == FixtureProfile.TOP && zz != cz) { other = zz; break; }
        check(o.zones[cz * 4 + 1] > 0.99f && o.zones[other * 4 + 1] < 0.01f && o.zones[other * 4] < 0.01f, "Full: one top cell green, the others dark (pixel)");
        int seg = -1;
        for (int i = 0; i < m.channels.length; i++) if (m.channels[i].kind() == DmxMode.Kind.ZONE_LEVEL) { seg = i; break; }
        u = zonesOn(m, 0, 255, 0);
        u[seg] = (byte) 255;
        o = dmx(s, u);
        int sz = m.channels[seg].zone(), so = -1;
        for (int zz = 0; zz < p.zones; zz++) if (p.section(zz) == FixtureProfile.MID && zz != sz) { so = zz; break; }
        check(o.zones[sz * 4 + 3] > 0.99f && o.zones[so * 4 + 3] < 0.01f, "Full: one centre segment alone once a segment channel is up");
    }

    static void dmxCompact(FixtureProfile p) {
        DmxMode m = DmxMode.of(p, DmxMode.COMPACT_Z3);
        Show s = show(p, DmxMode.COMPACT_Z3);
        String[] nm = {"top bar", "centre", "bottom bar"};
        for (int k = 0; k < 3; k++) {
            float[] l = drawn(p, dmx(s, zonesOn(m, k == 0 ? 255 : 0, k == 1 ? 255 : 0, k == 2 ? 255 : 0)), 1.0);
            check(l[k] > 0.9f && l[(k + 1) % 3] < 1e-4f && l[(k + 2) % 3] < 1e-4f, "Compact: " + nm[k] + " alone (" + fmt(l) + ")");
        }
        byte[] u = zonesOn(m, 255, 255, 255);
        set(u, m, Attr.STROBE, 128);
        Engine.Output o = dmx(s, u);
        check(o.sectionHz[0] > 1f && Math.abs(o.sectionHz[0] - o.sectionHz[1]) < 1e-4f && Math.abs(o.sectionHz[1] - o.sectionHz[2]) < 1e-4f && !o.alternate,
            "Compact: the shared strobe flashes the three zones together (" + o.sectionHz[0] + " Hz)");
        check(m.footprint() == 12, "Compact: 12 channels (" + m.footprint() + ")");
    }

    static void strobes(FixtureProfile p) {
        DmxMode m = DmxMode.of(p, DmxMode.FULL);
        Show s = show(p, DmxMode.FULL);
        // 5: centre strobe, bars steady
        byte[] u = zonesOn(m, 255, 255, 255);
        set(u, m, Attr.MID_STROBE, 60);
        Engine.Output o = dmx(s, u);
        check(o.sectionHz[1] > 1f && o.sectionHz[0] == 0f && o.sectionHz[2] == 0f, "centre strobe only (" + o.sectionHz[1] + " Hz)");
        int on = 0, off = 0;
        boolean barsSteady = true;
        for (int i = 0; i < 400; i++) {
            float[] l = drawn(p, o, 1.0 + i * 0.0025);
            if (l[1] > 0.5f) on++; else off++;
            barsSteady &= l[0] > 0.9f && l[2] > 0.9f;
        }
        check(on > 0 && off > 0, "centre flashes (" + on + " on / " + off + " off of 400 instants)");
        check(barsSteady, "the bars stay lit while the centre flashes");
        // 6 / 7: top strobe alone, bottom strobe alone
        for (Attr a : new Attr[] {Attr.TOP_STROBE, Attr.BOT_STROBE}) {
            u = zonesOn(m, 255, 255, 255);
            set(u, m, a, 60);
            o = dmx(s, u);
            int k = a == Attr.TOP_STROBE ? 0 : 2;
            boolean othersSteady = true;
            int fl = 0;
            for (int i = 0; i < 400; i++) {
                float[] l = drawn(p, o, 1.0 + i * 0.0025);
                if (l[k] < 0.5f) fl++;
                othersSteady &= l[1] > 0.9f && l[2 - k] > 0.9f;
            }
            check(fl > 0 && othersSteady, a.label + " alone: that bar flashes, the other zones steady");
        }
        // alternation: top and bottom at the same rate flash in turn
        u = zonesOn(m, 255, 0, 255);
        set(u, m, Attr.TOP_STROBE, 60); set(u, m, Attr.BOT_STROBE, 60);
        o = dmx(s, u);
        boolean together = false; int topOnly = 0, botOnly = 0;
        for (int i = 0; i < 800; i++) {
            float[] l = drawn(p, o, 1.0 + i * 0.00125);
            if (l[0] > 0.5f && l[2] > 0.5f) together = true;
            if (l[0] > 0.5f && l[2] < 0.5f) topOnly++;
            if (l[2] > 0.5f && l[0] < 0.5f) botOnly++;
        }
        check(o.alternate && !together && topOnly > 0 && botOnly > 0, "top and bottom at the same rate alternate (never together)");
        // no accidental flashing: no strobe channel up -> steady
        o = dmx(s, zonesOn(m, 255, 255, 255));
        boolean steady = true;
        for (int i = 0; i < 200; i++) { float[] l = drawn(p, o, 1.0 + i * 0.01); steady &= l[0] > 0.9f && l[1] > 0.9f && l[2] > 0.9f; }
        check(steady && o.sectionHz[0] == 0 && o.sectionHz[1] == 0 && o.sectionHz[2] == 0, "no strobe channel up: no flash at all");
        // safety: "no strobe" stops every zone
        u = zonesOn(m, 255, 255, 255);
        set(u, m, Attr.MID_STROBE, 200); set(u, m, Attr.TOP_STROBE, 200);
        DmxData d = new DmxData(); d.set(1, u);
        Live l = external(); l.noStrobe = true;
        o = new Engine().evaluate(s, l, 5000, Engine.Music.NONE, d).get(1);
        check(o.sectionHz[0] == 0 && o.sectionHz[1] == 0 && o.sectionHz[2] == 0, "safety 'no strobe' holds for every zone");
        l.noStrobe = false; l.strobeCapHz = 5f;
        o = new Engine().evaluate(s, l, 5000, Engine.Music.NONE, d).get(1);
        check(o.sectionHz[0] <= 5f && o.sectionHz[1] <= 5f, "strobe cap holds for every zone (" + o.sectionHz[1] + " Hz)");
    }

    static void programmer(FixtureProfile p) {
        Show s = show(p, DmxMode.FULL);
        Live l = new Live();
        Engine e = new Engine();
        Commands.apply(s, l, Commands.op(Commands.P_SET).varInt(1).varInt(1).varInt(Attr.DIMMER.ordinal()).unit(1f).toBytes(), 0, null);
        Engine.Output o = e.evaluate(s, l, 1000, Engine.Music.NONE).get(1);
        float[] lv = drawn(p, o, 1.0);
        check(lv[0] > 0.9f && lv[2] > 0.9f, "desk, no zone set: the whole fixture as before (" + fmt(lv) + ")");
        Commands.apply(s, l, Commands.op(Commands.P_SET).varInt(1).varInt(1).varInt(Attr.TOP_DIM.ordinal()).unit(0f).toBytes(), 0, null);
        Commands.apply(s, l, Commands.op(Commands.P_SET).varInt(1).varInt(1).varInt(Attr.BOT_RED.ordinal()).unit(0f).toBytes(), 0, null);
        o = e.evaluate(s, l, 2000, Engine.Music.NONE).get(1);
        lv = drawn(p, o, 1.0);
        float[] bc = colour(p, o.zones, 2);
        check(lv[0] < 1e-4f && lv[2] > 0.9f && bc[0] < 0.01f && bc[1] > 0.9f, "desk programmer: top off, bottom cyan (" + fmt(lv) + ")");
        // save and reload: the zone values of a cue survive
        Show.CueList list = new Show.CueList();
        Show.Cue c = new Show.Cue();
        Look look = new Look();
        look.set(Attr.MID_STROBE, 0.4f); look.set(Attr.TOP_RED, 0.25f);
        c.looks.put(1, look);
        list.cues.add(c);
        s.lists.add(list);
        Show back = Show.fromBytes(s.toBytes());
        Look lb = back.lists.get(back.lists.size() - 1).cues.get(0).looks.get(1);
        near(lb.get(Attr.MID_STROBE), 0.4, 0.01, "save / reload: centre strobe of a cue");
        near(lb.get(Attr.TOP_RED), 0.25, 0.01, "save / reload: top red of a cue");
        check(back.fixture(1).dmxMode == DmxMode.FULL, "save / reload: the zone Full mode stays Full (format " + Show.FORMAT + ")");
    }

    static void migration() {
        System.out.println("== migration");
        for (FixtureProfile p : new FixtureProfile[] {FixtureProfile.PULSESTRIKE_M, FixtureProfile.FLASHCORE_FX}) {
            for (int mode : new int[] {DmxMode.FULL, DmxMode.COMPACT, DmxMode.ULTRA}) {
                Show s = show(p, mode);
                byte[] b = s.toBytes();
                b[4] = 4;                                   // the same show written by rev. 20 (format 4: identical bytes, older meaning)
                Show old = Show.fromBytes(b);
                int want = mode == DmxMode.FULL ? DmxMode.FULL_V1 : mode == DmxMode.COMPACT ? DmxMode.COMPACT_V1 : mode;
                check(old != null && old.fixture(1).dmxMode == want, p.name + " " + DmxMode.NAMES[mode] + " saved before rev. 21 -> " + DmxMode.NAMES[want]);
            }
            // the old layout reads the old channels exactly as rev. 20 did (dimmer, colour, white of the whole fixture)
            DmxMode v1 = DmxMode.of(p, DmxMode.FULL_V1);
            Show s = show(p, DmxMode.FULL_V1);
            byte[] u = new byte[512];
            set(u, v1, Attr.DIMMER, 255); set(u, v1, Attr.RED, 255); set(u, v1, Attr.GREEN, 0); set(u, v1, Attr.BLUE, 0);
            Engine.Output o = dmx(s, u);
            float[] lv = drawn(p, o, 1.0);
            check(o.get(Attr.DIMMER) > 0.99f && lv[0] > 0.9f && lv[2] > 0.9f && colour(p, o.zones, 0)[1] < 0.01f, p.name + " Full before rev. 21: master dimmer + red drive both bars as before");
            check(java.util.Arrays.stream(v1.channels).noneMatch(c -> c.attr() != null && c.attr().zoneAttr()), p.name + " layout before rev. 21 has no zone channel (nothing reinterpreted)");
        }
        Show s = show(FixtureProfile.VORTEX_X1, DmxMode.FULL);
        byte[] b = s.toBytes(); b[4] = 4;
        check(Show.fromBytes(b).fixture(1).dmxMode == DmxMode.FULL, "other fixtures: Full stays Full (no migration)");
        check(DmxMode.legacy(DmxMode.FULL_V1) && DmxMode.current(DmxMode.COMPACT_V1) == DmxMode.COMPACT, "legacy layouts are replaced by the current ones on re-patch");
    }

    static void autoShow() {
        System.out.println("== AutoShow");
        Show.AutoSettings set = new Show.AutoSettings();
        set.strobes = true;
        set.strobeMaxHz = 10f;
        for (FixtureProfile p : new FixtureProfile[] {FixtureProfile.PULSESTRIKE_M, FixtureProfile.FLASHCORE_FX}) {
            com.concertstage.light.analysis.Palettes.Palette pal = new com.concertstage.light.analysis.Palettes.Palette(0xFF0000, 0x00FF00, 0x0000FF);
            Look drop = new Look();
            AutoShow.zones(drop, p, Analysis.DROP, false, 1f, 8f, pal, 0, false);
            check(drop.get(Attr.MID_STROBE) > 0 && drop.get(Attr.TOP_STROBE) == 0 && drop.get(Attr.BOT_STROBE) == 0 && drop.get(Attr.TOP_DIM) == 1 && drop.get(Attr.STROBE) == 0,
                p.name + " drop: centre flash with steady bars");
            Look end = new Look();
            AutoShow.zones(end, p, Analysis.BUILD, true, 0.9f, 6f, pal, 0, false);
            check(end.get(Attr.TOP_STROBE) > 0 && end.get(Attr.TOP_STROBE) == end.get(Attr.BOT_STROBE) && end.get(Attr.MID_DIM) == 0, p.name + " end of build: top / bottom alternation");
            Look build = new Look();
            AutoShow.zones(build, p, Analysis.BUILD, false, 0.5f, 3f, pal, 0, false);
            check(build.get(Attr.MID_STROBE) > 0 && build.get(Attr.TOP_DIM) == 0 && build.get(Attr.BOT_DIM) == 0, p.name + " build: centre flash only");
            Look chorus = new Look();
            AutoShow.zones(chorus, p, Analysis.CHORUS, false, 0.5f, 0f, pal, 0, false);
            check(chorus.get(Attr.TOP_RED) != chorus.get(Attr.BOT_RED) || chorus.get(Attr.TOP_GREEN) != chorus.get(Attr.BOT_GREEN), p.name + " chorus: top and bottom in different colours");
            for (Look l : new Look[] {drop, end, build, chorus}) {
                int flashing = (l.get(Attr.TOP_STROBE) > 0 ? 1 : 0) + (l.get(Attr.MID_STROBE) > 0 ? 1 : 0) + (l.get(Attr.BOT_STROBE) > 0 ? 1 : 0);
                check(flashing < 3 && l.get(Attr.STROBE) == 0, p.name + ": never the three zones flashing together");
            }
            // the desk keeps priority: the programmer over an AutoShow cue
            Show s = show(p, DmxMode.FULL);
            Show.CueList list = new Show.CueList();
            list.priority = 0;
            Show.Cue c = new Show.Cue();
            drop.set(Attr.DIMMER, 1f);
            c.looks.put(1, drop);
            list.cues.add(c);
            s.lists.add(list);
            Live l = new Live();
            l.playbacks.put(list.id, new Live.Playback());
            Commands.apply(s, l, Commands.op(Commands.P_SET).varInt(1).varInt(1).varInt(Attr.MID_STROBE.ordinal()).unit(0f).toBytes(), 0, null);
            Engine.Output o = new Engine().evaluate(s, l, 1000, Engine.Music.NONE).get(1);
            check(o.sectionHz[1] == 0f, p.name + ": a manual command (centre strobe off) wins over the AutoShow cue");
        }
    }

    static void others() {
        System.out.println("== other fixtures");
        for (FixtureProfile p : FixtureProfile.all()) {
            if (p.hasSections()) continue;
            boolean none = p.attrs.stream().noneMatch(Attr::zoneAttr);
            check(none && DmxMode.of(p, DmxMode.FULL).footprint() == DmxMode.of(p, DmxMode.FULL_V1).footprint()
                && DmxMode.of(p, DmxMode.COMPACT).footprint() == DmxMode.of(p, DmxMode.COMPACT_V1).footprint(), p.name + ": unchanged (no zone attribute, same layouts)");
        }
    }

    static String fmt(float[] v) {
        StringBuilder s = new StringBuilder();
        for (float x : v) s.append(s.length() == 0 ? "" : " ").append(String.format(Locale.ROOT, "%.2f", x));
        return s.toString();
    }
}
