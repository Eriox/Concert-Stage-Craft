import com.concertstage.light.analysis.*;
import com.concertstage.light.core.*;
import com.concertstage.light.dmx.*;
import java.util.*;

/**
 * Rev. 24 — DMX checks without Minecraft: Compact of the PULSESTRIKE M / FLASHCORE FX in two instances, blinders in one channel
 * (3200 K), gobo names in every mode, migration of the shows saved before, fixture angle tool. Uses the helpers of tools/rev21/ZoneTest.
 */
public class Rev24Test {
    static void check(boolean ok, String what) { ZoneTest.check(ok, what); }

    public static void main(String[] a) {
        for (FixtureProfile p : new FixtureProfile[] {FixtureProfile.PULSESTRIKE_M, FixtureProfile.FLASHCORE_FX}) twoInstances(p);
        blinders();
        goboNames();
        migration();
        angleTool();
        autoShow();
        newEffects();
        System.out.println();
        System.out.println("passed " + ZoneTest.passed + ", failed " + ZoneTest.failed);
        if (ZoneTest.failed > 0) System.exit(1);
    }

    static String fmt(float[] l) { return String.format(Locale.ROOT, "%.2f %.2f %.2f", l[0], l[1], l[2]); }

    static void twoInstances(FixtureProfile p) {
        System.out.println("== " + p.name + " Compact in two instances");
        DmxMode m = DmxMode.of(p, DmxMode.COMPACT);
        List<String> names = m.instances().stream().map(DmxMode.Instance::name).toList();
        check(names.equals(List.of("LED bars", "White bar")), "two instances " + names);
        check(m.footprint() == (p.has(Attr.ZOOM) ? 9 : 8), "footprint " + m.footprint() + " channels (" + 512 / m.footprint() + " per universe)");
        for (DmxMode.Instance in : m.instances()) {
            boolean run = true;
            for (int i = 1; i < in.channels().length; i++) run &= in.channels()[i] == in.channels()[i - 1] + 1;
            check(run, in.name() + ": contiguous channels " + Arrays.toString(in.channels()));
        }
        Show s = ZoneTest.show(p, DmxMode.COMPACT);
        // the LED bars instance lights top AND bottom, the centre stays dark
        byte[] u = ZoneTest.zonesOn(m, 255, 0, 0);
        float[] l = ZoneTest.drawn(p, ZoneTest.dmx(s, u), 1.0);
        check(l[0] > 0.9f && l[2] > 0.9f && l[1] < 1e-4f, "LED bars dimmer: top and bottom bars, centre dark (" + fmt(l) + ")");
        u = ZoneTest.zonesOn(m, 0, 255, 0);
        l = ZoneTest.drawn(p, ZoneTest.dmx(s, u), 1.0);
        check(l[1] > 0.9f && l[0] < 1e-4f && l[2] < 1e-4f, "White bar dimmer: the centre alone (" + fmt(l) + ")");
        // colour of the bars on both
        u = ZoneTest.zonesOn(m, 255, 0, 0);
        ZoneTest.set(u, m, Attr.TOP_RED, 255); ZoneTest.set(u, m, Attr.TOP_GREEN, 0); ZoneTest.set(u, m, Attr.TOP_BLUE, 0);
        Engine.Output o = ZoneTest.dmx(s, u);
        float[] ct = ZoneTest.colour(p, o.zones, 0), cb = ZoneTest.colour(p, o.zones, 2);
        check(ct[0] > 0.9f && ct[2] < 0.01f && cb[0] > 0.9f && cb[2] < 0.01f, "LED bars red: both bars red (" + fmt(ct) + " / " + fmt(cb) + ")");
        // LED bars strobe: top and bottom flash together, never in turn; the white bar is not touched
        u = ZoneTest.zonesOn(m, 255, 255, 0);
        ZoneTest.set(u, m, Attr.TOP_STROBE, 120);
        o = ZoneTest.dmx(s, u);
        check(o.sectionHz[0] > 1f && Math.abs(o.sectionHz[0] - o.sectionHz[2]) < 1e-4f && o.sectionHz[1] == 0f && !o.alternate,
            "LED bars strobe: both bars at " + o.sectionHz[0] + " Hz, together; white bar steady");
        int apart = 0, on = 0, whiteOff = 0;
        for (int i = 0; i < 400; i++) {
            float[] d = ZoneTest.drawn(p, o, 1.0 + i * 0.0025);
            if ((d[0] > 0.5f) != (d[2] > 0.5f)) apart++;
            if (d[0] > 0.5f) on++;
            if (d[1] < 0.5f) whiteOff++;
        }
        check(apart == 0 && on > 20 && on < 380 && whiteOff == 0, "drawn: bars flash together (" + on + " / 400 frames on, " + apart + " apart), white bar always on");
        u = ZoneTest.zonesOn(m, 255, 255, 0);
        ZoneTest.set(u, m, Attr.MID_STROBE, 120);
        o = ZoneTest.dmx(s, u);
        check(o.sectionHz[1] > 1f && o.sectionHz[0] == 0f && o.sectionHz[2] == 0f, "White bar strobe: the centre alone (" + o.sectionHz[1] + " Hz)");
        check(m.missing().stream().anyMatch(x -> x.startsWith("Top and bottom LED bars separately")), "patch screen says the bars are one instance");
    }

    static void blinders() {
        System.out.println("== blinders in one channel");
        for (FixtureProfile p : new FixtureProfile[] {FixtureProfile.BLINDER_X2, FixtureProfile.BLINDER_X4, FixtureProfile.BLINDER_X8}) {
            DmxMode m = DmxMode.of(p, DmxMode.ULTRA);
            check(m.footprint() == 1 && m.channels[0].attr() == Attr.DIMMER, p.name + " Ultra Compact: 1 channel (" + m.channels[0].name() + ")");
            check(DmxMode.of(p, DmxMode.ULTRA_V1).footprint() == 2, p.name + " former Ultra Compact (dimmer + strobe) kept");
            Show s = ZoneTest.show(p, DmxMode.ULTRA);
            byte[] u = new byte[512];
            u[0] = (byte) 255;
            Engine.Output full = ZoneTest.dmx(s, u);
            u[0] = (byte) 60;
            Engine.Output low = ZoneTest.dmx(s, u);
            // rev. 25: the colour of a halogen blinder lamp (Engine.halogen, measured on a photo), redder as it dims
            float gF = full.beamG / full.beamR, bF = full.beamB / full.beamR, gL = low.beamG / low.beamR, bL = low.beamB / low.beamR;
            check(full.get(Attr.DIMMER) > 0.99f && full.get(Attr.WHITE) > 0.99f && Math.abs(gF - 0.80f) < 0.03f && Math.abs(bF - 0.42f) < 0.03f,
                String.format(Locale.ROOT, "%s at full: halogen yellow (1, %.2f, %.2f)", p.name, gF, bF));
            check(gL < gF - 0.05f && bL < bF - 0.05f && low.get(Attr.DIMMER) < 0.3f, String.format(Locale.ROOT, "%s dimmed to 24 %%: redder (1, %.2f, %.2f)", p.name, gL, bL));
            check(full.beamR > full.beamB * 1.3f, String.format(Locale.ROOT, "%s beam colour is warm (R %.2f G %.2f B %.2f)", p.name, full.beamR, full.beamG, full.beamB));
            check(full.strobeHz == 0f, p.name + ": no strobe in one channel");
        }
        check(!DmxMode.tungsten(FixtureProfile.FLOODLIGHT_F8) && DmxMode.of(FixtureProfile.FLOODLIGHT_F8, DmxMode.ULTRA).footprint() == DmxMode.of(FixtureProfile.FLOODLIGHT_F8, DmxMode.ULTRA_V1).footprint(),
            "other fixtures: Ultra Compact unchanged");
    }

    static void goboNames() {
        System.out.println("== gobo names in every mode");
        for (FixtureProfile p : FixtureProfile.all()) {
            if (!DmxMode.hasGoboWheel(p)) continue;
            GoboWheels.Wheel w = GoboWheels.first(p);
            for (int mode : new int[] {DmxMode.COMPACT, DmxMode.ULTRA}) {
                DmxMode m = DmxMode.of(p, mode);
                Set<String> found = new HashSet<>();
                for (DmxMode.Channel c : m.channels)
                    for (DmxMode.Range r : DmxMode.ranges(p, c)) for (int k = 1; k <= w.size(); k++) if (r.what().equals("GOBO") && r.name().endsWith(" " + w.name(k))) found.add(w.name(k));
                boolean full = m.channelsOf(Attr.GOBO).length > 0 && found.isEmpty();     // a plain gobo channel: named by the exporters as in Full
                check(found.size() == w.size() || full, p.name + " " + m.name() + ": " + (full ? "gobo channel as in Full" : found.size() + " / " + w.size() + " gobo names"));
            }
        }
    }

    static void migration() {
        System.out.println("== shows saved before rev. 24");
        for (FixtureProfile p : new FixtureProfile[] {FixtureProfile.PULSESTRIKE_M, FixtureProfile.FLASHCORE_FX}) {
            Show s = ZoneTest.show(p, DmxMode.COMPACT);
            byte[] b = s.toBytes();
            check(Show.fromBytes(b).fixture(1).dmxMode == DmxMode.COMPACT, p.name + " Compact saved now: stays Compact (format " + Show.FORMAT + ")");
            b[4] = 5;
            check(Show.fromBytes(b).fixture(1).dmxMode == DmxMode.COMPACT_Z3, p.name + " Compact saved by rev. 21 to 23 -> " + DmxMode.NAMES[DmxMode.COMPACT_Z3]);
            b[4] = 4;
            check(Show.fromBytes(b).fixture(1).dmxMode == DmxMode.COMPACT_V1, p.name + " Compact saved before rev. 21 -> " + DmxMode.NAMES[DmxMode.COMPACT_V1]);
        }
        Show s = ZoneTest.show(FixtureProfile.BLINDER_X4, DmxMode.ULTRA);
        byte[] b = s.toBytes(); b[4] = 5;
        check(Show.fromBytes(b).fixture(1).dmxMode == DmxMode.ULTRA_V1, "BLINDER X4 Ultra Compact saved before rev. 24 keeps dimmer + strobe");
        s = ZoneTest.show(FixtureProfile.VORTEX_X1, DmxMode.COMPACT);
        b = s.toBytes(); b[4] = 5;
        check(Show.fromBytes(b).fixture(1).dmxMode == DmxMode.COMPACT, "VORTEX X1 Compact: no migration");
        check(DmxMode.legacy(DmxMode.COMPACT_Z3) && DmxMode.current(DmxMode.COMPACT_Z3) == DmxMode.COMPACT, "the rev. 21 Compact is a legacy layout, re-patched to the current one");
    }

    static void angleTool() {
        System.out.println("== fixture angle tool");
        check(FixtureTilt.next(0, 5, false) == 5f && FixtureTilt.next(5, 5, true) == 0f, "5° steps up and down");
        check(FixtureTilt.next(88, 5, false) == 90f && FixtureTilt.next(-88, 15, true) == -90f, "held between -90° and +90°");
        check(FixtureTilt.next(7, 5, false) == 10f, "back on the grid of the step (7 -> 10)");
        check(FixtureTilt.next(Float.NaN, 1, false) == 1f, "a broken value starts again from 0");
    }

    // ------------------------------------------------------------------ AutoShow
    static final Movement.Stage STAGE = new Movement.Stage(8, 70, 10, 64, -1, 0, 20);

    static Show rig() {
        Show s = new Show();
        s.id = "auto";
        int fid = 1;
        Object[][] types = {{FixtureProfile.VORTEX_X1, 8}, {FixtureProfile.LUMORA_W19, 8}, {FixtureProfile.PULSESTRIKE_M, 4}, {FixtureProfile.BLINDER_X4, 4}};
        for (Object[] t : types) for (int i = 0; i < (int) t[1]; i++, fid++)
            Commands.apply(s, new Live(), Commands.op(Commands.PATCH_ADD).varInt(fid).str(((FixtureProfile) t[0]).id).str("").sInt(i * 2).sInt(70).sInt(0).f32(0).varInt(1).toBytes(), 0, null);
        return s;
    }

    static Analysis track(float bpm) {
        Analysis a = new Analysis();
        a.bpm = bpm; a.hopMs = 10; a.firstBeatMs = 0; a.beatsPerBar = 4; a.durationMs = 160000; a.tempoConfidence = 0.8f;
        int n = 16000;
        a.rms = new float[n]; a.low = new float[n]; a.onset = new float[n]; a.mid = new float[n]; a.high = new float[n];
        Arrays.fill(a.rms, 0.6f); Arrays.fill(a.low, 0.7f); Arrays.fill(a.onset, 0.6f); Arrays.fill(a.mid, 0.5f); Arrays.fill(a.high, 0.5f);
        int[][] secs = {{0, 15000, Analysis.INTRO, 0}, {15000, 45000, Analysis.VERSE, 1}, {45000, 60000, Analysis.BUILD, 2}, {60000, 90000, Analysis.DROP, 3},
            {90000, 110000, Analysis.BREAK, 4}, {110000, 140000, Analysis.CHORUS, 5}, {140000, 160000, Analysis.OUTRO, 6}};
        for (int[] q : secs) {
            Analysis.Section x = new Analysis.Section();
            x.startMs = q[0]; x.endMs = q[1]; x.kind = q[2]; x.motif = q[3]; x.energy = q[2] == Analysis.DROP ? 1f : q[2] == Analysis.CHORUS ? 0.85f : 0.5f; x.confidence = 0.8f;
            a.sections.add(x);
        }
        return a;
    }

    static void autoShow() {
        System.out.println("== AutoShow");
        Show s = rig();
        s.auto.style = Show.AutoSettings.EDM;
        AutoShow.Report r = AutoShow.generate(s, track(128), STAGE);
        Show.CueList list = s.lists.stream().filter(l -> AutoShow.LIST_NAME.equals(l.name)).findFirst().orElse(null);
        check(list != null && r.cues() > 7, "AutoShow written: " + r.cues() + " cues, " + r.events() + " events, " + r.accents() + " accents");
        Set<Integer> types = new TreeSet<>();
        for (Show.Cue c : list.cues) for (Show.Effect e : c.effects) types.add(e.type);
        long newOnes = types.stream().filter(t -> t >= Show.Effect.FIGURE8).count();
        List<String> names = types.stream().map(t -> Show.Effect.NAMES[t]).toList();
        check(types.size() >= 10 && newOnes >= 3, "effects used: " + types.size() + " kinds, " + newOnes + " of rev. 24 " + names);
        Show.Group bl = s.groups.stream().filter(g -> g.name.equals("Blinders")).findFirst().orElse(null);
        Show.Group st = s.groups.stream().filter(g -> g.name.equals("Strobes")).findFirst().orElse(null);
        check(bl != null && bl.fids.length == 4 && st != null && st.fids.length == 4, "groups: Blinders (4) apart from Strobes (" + (st == null ? 0 : st.fids.length) + ")");
        List<Show.Event> hits = s.timeline.events.stream().filter(e -> e.kind == Show.Event.ACCENT && bl != null && e.group == bl.id).toList();
        check(!hits.isEmpty() && hits.get(0).ms >= 60000 && hits.get(0).ms < 60000 + 500, "blinder hit on the first downbeat of the drop (" + (hits.isEmpty() ? "none" : hits.get(0).ms + " ms") + "), " + hits.size() + " hits");
        boolean dark = true, noFx = true;
        for (Show.Cue c : list.cues) {
            if (c.name.startsWith("Drop")) for (int f : bl.fids) dark &= c.looks.get(f) != null && c.looks.get(f).get(Attr.DIMMER) < 0.01f;
            for (Show.Effect e : c.effects) for (int f : e.fids) noFx &= Arrays.stream(bl.fids).noneMatch(b -> b == f);
        }
        check(dark && noFx, "blinders dark in the drop looks, never in an effect (they only hit)");
        Show.Cue fill = list.cues.stream().filter(c -> c.name.endsWith("fill")).findFirst().orElse(null);
        long fillAt = -1;
        if (fill != null) for (Show.Event e : s.timeline.events) if (e.kind == Show.Event.GO && Math.abs(e.cue - fill.number) < 1e-4f) fillAt = e.ms;
        long barMs = Math.round(60000 / 128.0 * 4);
        check(fill != null && Math.abs(fillAt - (110000 - barMs)) <= 300, "fill on the last bar before the chorus (" + (fill == null ? "none" : fill.name + " at " + fillAt + " ms") + ")");
        // tempo: the same track at 170 BPM moves in half time (rates in beats doubled)
        Show s2 = rig(); s2.auto.style = Show.AutoSettings.EDM;
        AutoShow.generate(s2, track(170), STAGE);
        float r128 = firstMoveRate(s), r170 = firstMoveRate(s2);
        check(r170 > r128 * 1.9f, String.format(Locale.ROOT, "fast track (170 BPM): movements in half time (%.0f -> %.0f beats)", r128, r170));
        // the effect mask is respected; no strobe effect and no accent when strobes are off
        Show s3 = rig(); s3.auto.effectMask = (1 << Show.Effect.FIGURE8) - 1; s3.auto.strobes = false;
        AutoShow.generate(s3, track(128), STAGE);
        boolean onlyOld = true, noStrobe = true;
        Set<Integer> strobeFids = new HashSet<>();
        for (Show.Patch p : s3.patch) if (p.profile().autoRole() == 2) strobeFids.add(p.fid);
        for (Show.CueList l : s3.lists) for (Show.Cue c : l.cues) for (Show.Effect e : c.effects) {
            onlyOld &= e.type < Show.Effect.FIGURE8;
            for (int f : e.fids) noStrobe &= !strobeFids.contains(f) || e.type == Show.Effect.PIXEL || e.type == Show.Effect.WAVE;
        }
        check(onlyOld, "effects switched off in the settings are never used");
        check(noStrobe && s3.timeline.events.stream().noneMatch(e -> e.kind == Show.Event.ACCENT), "strobes off: no strobe effect, no accent, no blinder hit");
    }

    static float firstMoveRate(Show s) {
        for (Show.CueList l : s.lists) for (Show.Cue c : l.cues) if (c.name.startsWith("Verse"))
            for (Show.Effect e : c.effects) if (e.type == Show.Effect.SWEEP || e.type == Show.Effect.CIRCLE || e.type == Show.Effect.FIGURE8 || e.type == Show.Effect.FAN
                || e.type == Show.Effect.BALLYHOO || e.type == Show.Effect.TILT_WAVE) return e.rate;
        return 0;
    }

    static Engine.Output output(int fid) {
        try {
            var c = Engine.Output.class.getDeclaredConstructor(int.class);
            c.setAccessible(true);
            return c.newInstance(fid);
        } catch (ReflectiveOperationException ex) { throw new RuntimeException(ex); }
    }

    static void newEffects() {
        System.out.println("== new effects (rev. 24)");
        Show s = rig();
        int[] fids = s.patch.stream().filter(p -> p.profile() == FixtureProfile.VORTEX_X1).mapToInt(p -> p.fid).toArray();
        for (int type = Show.Effect.FIGURE8; type < Show.Effect.NAMES.length; type++) {
            Show.Effect e = new Show.Effect();
            e.type = type; e.fids = fids; e.rate = 1f; e.amplitude = 0.6f; e.spread = 1f; e.duty = 0.5f;
            Set<String> seen = new HashSet<>();
            boolean finite = true;
            for (int t = 0; t < 64; t++) {
                Map<Integer, Engine.Output> out = new HashMap<>();
                for (int f : fids) {
                    Engine.Output o = output(f);
                    o.profile = FixtureProfile.VORTEX_X1;
                    for (Attr a : Attr.ALL) o.v[a.ordinal()] = o.profile.home(a);
                    o.v[Attr.DIMMER.ordinal()] = 1f; o.v[Attr.RED.ordinal()] = 1f; o.v[Attr.GREEN.ordinal()] = 1f; o.v[Attr.BLUE.ordinal()] = 1f;
                    out.put(f, o);
                }
                Effects.apply(e, 1f, t * 0.037, t * 0.074, s, out);
                StringBuilder sb = new StringBuilder();
                for (int f : fids) for (Attr a : new Attr[] {Attr.PAN, Attr.TILT, Attr.DIMMER, Attr.RED, Attr.GREEN, Attr.BLUE, Attr.ZOOM}) {
                    float v = out.get(f).v[a.ordinal()];
                    finite &= Float.isFinite(v);
                    sb.append(Math.round(v * 50)).append(',');
                }
                seen.add(sb.toString());
            }
            check(finite && seen.size() >= 2, Show.Effect.NAMES[type] + ": moves over time (" + seen.size() + " different states in 64 frames), no NaN");
        }
    }
}
