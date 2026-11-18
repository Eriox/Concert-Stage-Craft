import com.concertstage.light.analysis.Analysis;
import com.concertstage.light.analysis.Analyzer;
import com.concertstage.light.analysis.AutoShow;
import com.concertstage.light.analysis.LiveAutoShow;
import com.concertstage.light.core.BeamOptics;
import com.concertstage.light.core.*;
import java.util.*;

/**
 * Off-game tests of the CSC 1.2.0 lighting core: show format, commands, engine (fades, tracking, priorities, follow, pause, timeline,
 * masters, safety), effects, music analysis on a synthetic track with a known structure, AutoShow (pre and live), and timings.
 * Usage: sh tools/lightshow_tests/run.sh
 */
public class LightshowTest {
    static int passed, failed;

    static void check(boolean ok, String what) {
        if (ok) { passed++; System.out.println("  ok   " + what); }
        else { failed++; System.out.println("  FAIL " + what); }
    }

    static void near(double a, double b, double tol, String what) {
        check(Math.abs(a - b) <= tol, what + " (" + String.format(Locale.ROOT, "%.4f", a) + " ~ " + String.format(Locale.ROOT, "%.4f", b) + ")");
    }

    public static void main(String[] args) throws Exception {
        showFormat();
        engine();
        timeline();
        safety();
        effects();
        commandsValidation();
        movement();
        Analysis a = analysis();
        autoshow(a);
        live();
        perf();
        optics();
        System.out.println();
        System.out.println("passed " + passed + ", failed " + failed);
        if (failed > 0) System.exit(1);
    }

    // ------------------------------------------------------------------------------------------------------------------ helpers
    static Show rig(int vortex, int w7, int w19, int pulse) {
        Show s = new Show();
        s.id = "test";
        Live l = new Live();
        int fid = 1;
        String[] types = {FixtureProfile.VORTEX_X1.id, FixtureProfile.LUMORA_W7.id, FixtureProfile.LUMORA_W19.id, FixtureProfile.PULSESTRIKE_M.id};
        int[] counts = {vortex, w7, w19, pulse};
        for (int t = 0; t < 4; t++) for (int i = 0; i < counts[t]; i++) {
            byte[] c = Commands.op(Commands.PATCH_ADD).varInt(fid).str(types[t]).str("").sInt(-8 + fid * 2).sInt(70).sInt(-20).f32(0).varInt(1).toBytes();
            Commands.apply(s, l, c, 0, null);
            fid++;
        }
        return s;
    }

    static int[] all(Show s) {
        return s.patch.stream().mapToInt(p -> p.fid).toArray();
    }

    static byte[] set(int[] fids, Attr a, float v) {
        Bytes.Writer w = Commands.op(Commands.P_SET);
        Commands.fids(w, fids);
        return w.varInt(a.ordinal()).unit(v).toBytes();
    }

    static byte[] store(int list, float number, float fade) {
        return Commands.op(Commands.STORE_CUE).varInt(list).f32(number).str("Cue " + number).f32(fade).varInt(0).varInt(0).toBytes();
    }

    static byte[] list(int op, int list) {
        return Commands.op(op).varInt(list).toBytes();
    }

    static byte[] clearProg() {
        Bytes.Writer w = Commands.op(Commands.P_CLEAR);
        Commands.fids(w, new int[0]);
        return w.bool(true).toBytes();
    }

    // ------------------------------------------------------------------------------------------------------------------ tests
    static void showFormat() {
        System.out.println("Show format");
        Show s = rig(2, 2, 2, 2);
        Live l = new Live();
        Commands.apply(s, l, set(all(s), Attr.DIMMER, 0.8f), 0, null);
        Commands.apply(s, l, store(1, 1, 2), 0, null);
        Bytes.Writer g = Commands.op(Commands.GROUP_STORE).str("Front Wash");
        Commands.fids(g, new int[] {3, 4});
        Commands.apply(s, l, g.i32(0xFF00FF).toBytes(), 0, null);
        byte[] b = s.toBytes();
        Show r = Show.fromBytes(b);
        check(r != null, "decodes");
        check(r.patch.size() == 8 && r.lists.size() == 1 && r.groups.size() == 1, "patch, lists, groups kept");
        check(Arrays.equals(r.toBytes(), b), "round trip is byte identical");
        check(r.patch.get(0).universe == 0 && r.patch.get(1).address > r.patch.get(0).address, "automatic addressing (rev. 16h: from universe 0)");
        check(Show.fromBytes(Arrays.copyOf(b, b.length / 2)) == null, "truncated show rejected");
        b[0] ^= 0x55;
        check(Show.fromBytes(b) == null, "bad magic rejected");
        Live lr = Live.fromBytes(l.toBytes());
        check(lr.programmer.size() == l.programmer.size() && lr.revision == l.revision, "live state round trip");
        System.out.println("  show bytes for 8 fixtures and 1 cue: " + r.toBytes().length);
    }

    static void engine() {
        System.out.println("Engine: cues, fades, tracking, HTP/LTP, follow, back, pause");
        Show s = rig(2, 2, 0, 0);
        Live l = new Live();
        Engine e = new Engine();
        int[] f = all(s);
        Commands.apply(s, l, set(f, Attr.DIMMER, 1f), 0, null);
        Commands.apply(s, l, set(f, Attr.PAN, 0.75f), 0, null);
        Commands.apply(s, l, store(1, 1, 2), 0, null);
        Commands.apply(s, l, clearProg(), 0, null);
        Commands.apply(s, l, set(f, Attr.DIMMER, 0.2f), 0, null);
        Commands.apply(s, l, store(1, 2, 4), 0, null);
        Commands.apply(s, l, clearProg(), 0, null);
        Show.CueList list = s.list(1);
        check(list != null && list.cues.size() == 2, "two cues recorded");
        // tracking: cue 2 only changed the dimmer, the pan of cue 1 tracks
        near(e.trackedAt(s, 1, 1).get(1).get(Attr.PAN), 0.75, 1e-4, "pan tracks into cue 2");
        Map<Integer, Engine.Output> o = e.evaluate(s, l, 0, Engine.Music.NONE);
        near(o.get(1).get(Attr.DIMMER), 0, 1e-6, "nothing running: dark");
        Commands.apply(s, l, list(Commands.GO, 1), 1000, null);
        o = e.evaluate(s, l, 2000, Engine.Music.NONE);
        near(o.get(1).get(Attr.DIMMER), 0.5, 0.01, "halfway through the 2 s fade in");
        o = e.evaluate(s, l, 3500, Engine.Music.NONE);
        near(o.get(1).get(Attr.DIMMER), 1, 1e-4, "fade done");
        near(o.get(1).get(Attr.PAN), 0.75, 1e-4, "pan reached");
        Commands.apply(s, l, list(Commands.GO, 1), 4000, null);
        o = e.evaluate(s, l, 6000, Engine.Music.NONE);
        near(o.get(1).get(Attr.DIMMER), 0.6, 0.01, "cue 2 fades down with its 4 s fade out");
        // pause freezes the fade
        Commands.apply(s, l, list(Commands.PAUSE, 1), 6000, null);
        o = e.evaluate(s, l, 9000, Engine.Music.NONE);
        near(o.get(1).get(Attr.DIMMER), 0.6, 0.01, "paused: frozen");
        Commands.apply(s, l, list(Commands.PAUSE, 1), 9000, null);
        o = e.evaluate(s, l, 11000, Engine.Music.NONE);
        near(o.get(1).get(Attr.DIMMER), 0.2, 0.01, "resumed and finished");
        // back
        Commands.apply(s, l, list(Commands.BACK, 1), 12000, null);
        o = e.evaluate(s, l, 20000, Engine.Music.NONE);
        near(o.get(1).get(Attr.DIMMER), 1, 1e-4, "back to cue 1");
        // follow: cue 1 follows into cue 2 after its fade + 1 s
        Bytes.Writer ed = Commands.op(Commands.CUE_EDIT).varInt(1).varInt(0).f32(1).str("Intro").f32(2).f32(2).f32(0).f32(1).varInt(0).sInt(-1);
        Commands.apply(s, l, ed.toBytes(), 20000, null);
        Commands.apply(s, l, Commands.op(Commands.GOTO).varInt(1).varInt(0).toBytes(), 30000, null);
        o = e.evaluate(s, l, 32500, Engine.Music.NONE);
        near(o.get(1).get(Attr.DIMMER), 1, 1e-4, "follow not yet");
        o = e.evaluate(s, l, 40000, Engine.Music.NONE);
        near(o.get(1).get(Attr.DIMMER), 0.2, 1e-4, "follow went to cue 2 by itself");
        // HTP between two lists, LTP by priority
        Commands.apply(s, l, set(f, Attr.DIMMER, 0.4f), 40000, null);
        Commands.apply(s, l, set(f, Attr.PAN, 0.1f), 40000, null);
        Commands.apply(s, l, store(2, 1, 0), 40000, null);
        Commands.apply(s, l, clearProg(), 40000, null);
        Commands.apply(s, l, Commands.op(Commands.LIST_EDIT).varInt(2).str("High").varInt(80).bool(false).toBytes(), 40000, null);
        Commands.apply(s, l, list(Commands.GO, 2), 41000, null);
        o = e.evaluate(s, l, 45000, Engine.Music.NONE);
        near(o.get(1).get(Attr.DIMMER), 0.4, 1e-4, "HTP: 0.4 beats 0.2");
        near(o.get(1).get(Attr.PAN), 0.1, 1e-4, "LTP: higher priority list wins the pan");
        Commands.apply(s, l, Commands.op(Commands.FADER).varInt(2).unit(0.5f).toBytes(), 45000, null);
        o = e.evaluate(s, l, 45000, Engine.Music.NONE);
        near(o.get(1).get(Attr.DIMMER), 0.2, 1e-4, "fader scales the intensity (0.4 x 0.5 = 0.2)");
        // programmer above everything
        Commands.apply(s, l, set(new int[] {1}, Attr.DIMMER, 0.9f), 45000, null);
        o = e.evaluate(s, l, 45000, Engine.Music.NONE);
        near(o.get(1).get(Attr.DIMMER), 0.9, 1e-4, "programmer overrides");
        // stop releases with the fade out
        Commands.apply(s, l, clearProg(), 45000, null);
        Commands.apply(s, l, list(Commands.STOP, 2), 46000, null);
        Commands.apply(s, l, list(Commands.STOP, 1), 46000, null);
        o = e.evaluate(s, l, 46000 + 30000, Engine.Music.NONE);
        near(o.get(1).get(Attr.DIMMER), 0, 1e-4, "stopped lists released");
        // determinism: two engines, same inputs, same output
        Engine e2 = new Engine();
        Commands.apply(s, l, list(Commands.GO, 1), 50000, null);
        Map<Integer, Engine.Output> a1 = e.evaluate(s, l, 50750, Engine.Music.NONE);
        float d1 = a1.get(2).get(Attr.DIMMER);
        Map<Integer, Engine.Output> a2 = e2.evaluate(Show.fromBytes(s.toBytes()), Live.fromBytes(l.toBytes()), 50750, Engine.Music.NONE);
        near(a2.get(2).get(Attr.DIMMER), d1, 1e-6, "same result on a replica (client) as on the server");
    }

    static void timeline() {
        System.out.println("Timeline driven by the music clock");
        Show s = rig(1, 1, 0, 0);
        Live l = new Live();
        Engine e = new Engine();
        int[] f = all(s);
        Commands.apply(s, l, set(f, Attr.DIMMER, 1f), 0, null);
        Commands.apply(s, l, store(1, 1, 0), 0, null);
        Commands.apply(s, l, clearProg(), 0, null);
        Commands.apply(s, l, set(f, Attr.DIMMER, 0.3f), 0, null);
        Commands.apply(s, l, store(1, 2, 1), 0, null);
        Commands.apply(s, l, clearProg(), 0, null);
        Commands.apply(s, l, Commands.op(Commands.TL_TRACK).str("abc").str("Song").toBytes(), 0, null);
        Commands.apply(s, l, Commands.op(Commands.TL_EVENT_ADD).varLong(10000).varInt(0).varInt(1).f32(1).sInt(-1).unit(1).varInt(100).toBytes(), 0, null);
        Commands.apply(s, l, Commands.op(Commands.TL_EVENT_ADD).varLong(20000).varInt(0).varInt(1).f32(2).sInt(-1).unit(1).varInt(100).toBytes(), 0, null);
        Commands.apply(s, l, Commands.op(Commands.TL_EVENT_ADD).varLong(30000).varInt(1).varInt(1).f32(0).sInt(-1).unit(1).varInt(100).toBytes(), 0, null);
        Map<Integer, Engine.Output> o = e.evaluate(s, l, 999999, new Engine.Music(true, "abc", true, 5000));
        near(o.get(1).get(Attr.DIMMER), 0, 1e-6, "before the first event");
        o = e.evaluate(s, l, 999999, new Engine.Music(true, "abc", true, 15000));
        near(o.get(1).get(Attr.DIMMER), 1, 1e-6, "cue 1 at 10 s");
        o = e.evaluate(s, l, 999999, new Engine.Music(true, "abc", true, 20500));
        near(o.get(1).get(Attr.DIMMER), 0.65, 0.01, "half of the 1 s fade to cue 2");
        o = e.evaluate(s, l, 999999, new Engine.Music(true, "abc", false, 20500));
        near(o.get(1).get(Attr.DIMMER), 0.65, 0.01, "paused music: same light");
        o = e.evaluate(s, l, 999999, new Engine.Music(true, "abc", true, 12000));
        near(o.get(1).get(Attr.DIMMER), 1, 1e-6, "seek backwards: back to cue 1 at once");
        o = e.evaluate(s, l, 999999, new Engine.Music(true, "abc", true, 40000));
        near(o.get(1).get(Attr.DIMMER), 0, 1e-6, "STOP event released the list");
        o = e.evaluate(s, l, 999999, new Engine.Music(true, "other", true, 15000));
        near(o.get(1).get(Attr.DIMMER), 0, 1e-6, "another track: timeline not used");
        Commands.apply(s, l, Commands.op(Commands.TL_OFFSET).sInt(500).toBytes(), 0, null);
        o = e.evaluate(s, l, 999999, new Engine.Music(true, "abc", true, 10300));
        near(o.get(1).get(Attr.DIMMER), 0, 1e-6, "latency offset delays the lights");
    }

    static void safety() {
        System.out.println("Masters and safety");
        Show s = rig(1, 0, 0, 1);
        Live l = new Live();
        Engine e = new Engine();
        int[] f = all(s);
        Commands.apply(s, l, set(f, Attr.DIMMER, 1f), 0, null);
        Commands.apply(s, l, set(f, Attr.STROBE, 1f), 0, null);
        Commands.apply(s, l, set(f, Attr.PAN, 1f), 0, null);
        Commands.apply(s, l, Commands.op(Commands.GRAND_MASTER).unit(0.5f).toBytes(), 0, null);
        Map<Integer, Engine.Output> o = e.evaluate(s, l, 0, Engine.Music.NONE);
        near(o.get(1).get(Attr.DIMMER), 0.5, 1e-4, "grand master halves");
        near(o.get(2).strobeHz, 10, 1e-4, "strobe capped at 10 Hz by default (fixture can do 25)");
        Commands.apply(s, l, Commands.op(Commands.STROBE_CAP).f32(3).toBytes(), 0, null);
        o = e.evaluate(s, l, 0, Engine.Music.NONE);
        near(o.get(2).strobeHz, 3, 1e-4, "safe mode cap 3 Hz");
        Commands.apply(s, l, Commands.op(Commands.NO_STROBE).bool(true).toBytes(), 0, null);
        o = e.evaluate(s, l, 0, Engine.Music.NONE);
        near(o.get(2).strobeHz, 0, 1e-6, "no strobe");
        Commands.apply(s, l, Commands.op(Commands.MOVE_LIMIT).bool(true).toBytes(), 0, null);
        o = e.evaluate(s, l, 0, Engine.Music.NONE);
        check(FixtureProfile.VORTEX_X1.panDegrees(o.get(1).get(Attr.PAN)) <= 30.01f, "movement limit keeps pan within 30 degrees");
        Commands.apply(s, l, Commands.op(Commands.BLACKOUT).bool(true).toBytes(), 0, null);
        o = e.evaluate(s, l, 0, Engine.Music.NONE);
        near(o.get(1).get(Attr.DIMMER), 0, 1e-6, "blackout");
        Commands.apply(s, l, Commands.op(Commands.HOME_ALL).toBytes(), 0, null);
        Commands.apply(s, l, Commands.op(Commands.BLACKOUT).bool(false).toBytes(), 0, null);
        Commands.apply(s, l, Commands.op(Commands.MOVE_LIMIT).bool(false).toBytes(), 0, null);
        o = e.evaluate(s, l, 0, Engine.Music.NONE);
        near(o.get(1).get(Attr.PAN), 0.5, 1e-4, "home: pan centred");
        near(o.get(1).get(Attr.DIMMER), 0, 1e-4, "home: lamps at zero");
    }

    static void effects() {
        System.out.println("Effects");
        Show s = rig(4, 0, 1, 0);
        Live l = new Live();
        Engine e = new Engine();
        int[] beams = {1, 2, 3, 4};
        Commands.apply(s, l, set(all(s), Attr.DIMMER, 1f), 0, null);
        Show.Effect c = new Show.Effect();
        c.type = Show.Effect.CIRCLE; c.fids = beams; c.rate = 0.5f; c.amplitude = 0.5f; c.spread = 1f; c.fade = 0;
        Bytes.Writer w = Commands.op(Commands.P_EFFECT_ADD);
        writeEffect(w, c);
        check(Commands.apply(s, l, w.toBytes(), 0, null) != Commands.REJECTED, "circle added");
        Map<Integer, Engine.Output> o = e.evaluate(s, l, 1000, Engine.Music.NONE);
        float p1 = o.get(1).get(Attr.PAN), p2 = o.get(2).get(Attr.PAN);
        check(Math.abs(p1 - p2) > 0.01f, "spread: fixtures at different phases");
        o = e.evaluate(s, l, 3000, Engine.Music.NONE);
        near(o.get(1).get(Attr.PAN), p1, 1e-4, "period of 2 s at 0.5 Hz");
        Show.Effect px = new Show.Effect();
        px.type = Show.Effect.PIXEL; px.fids = new int[] {5}; px.rate = 1; px.amplitude = 1; px.spread = 1; px.duty = 0.3f; px.fade = 0;
        w = Commands.op(Commands.P_EFFECT_ADD);
        writeEffect(w, px);
        Commands.apply(s, l, w.toBytes(), 0, null);
        o = e.evaluate(s, l, 1234, Engine.Music.NONE);
        int on = 0;
        float[] z = o.get(5).zones;
        for (int i = 0; i < z.length / 4; i++) if (z[i * 4 + 3] > 0.5f) on++;
        check(on > 3 && on < 37, "pixel chase lights a part of the 37 cells (" + on + ")");
    }

    static void writeEffect(Bytes.Writer w, Show.Effect e) {
        w.varInt(e.type).varInt(e.fids.length);
        for (int f : e.fids) w.varInt(f);
        w.f32(e.rate).bool(e.bpmSync).unit(e.amplitude).unit(e.spread).sInt(e.direction).bool(e.symmetric).unit(e.duty).f32(e.fade);
        w.varInt(e.colors.length);
        for (int x : e.colors) w.varInt(x);
    }

    static void commandsValidation() {
        System.out.println("Command validation");
        Show s = rig(1, 0, 0, 0);
        Live l = new Live();
        long rev = l.revision;
        check(Commands.apply(s, l, new byte[] {(byte) 200}, 0, null) == Commands.REJECTED, "unknown op rejected");
        check(Commands.apply(s, l, Commands.op(Commands.GO).varInt(99).toBytes(), 0, null) == Commands.REJECTED, "GO on a missing list rejected");
        check(Commands.apply(s, l, Commands.op(Commands.P_SET).varInt(1).toBytes(), 0, null) == Commands.REJECTED, "truncated command rejected");
        check(Commands.apply(s, l, Commands.op(Commands.TL_TEMPO).f32(999).sLong(0).toBytes(), 0, null) == Commands.REJECTED, "absurd tempo rejected");
        check(l.revision == rev, "rejected commands do not move the revision");
        Bytes.Writer w = Commands.op(Commands.P_SET);
        Commands.fids(w, new int[] {1, 777});
        Commands.apply(s, l, w.varInt(Attr.GOBO.ordinal()).unit(0.5f).toBytes(), 0, null);
        check(l.programmer.size() == 1 && l.programmer.containsKey(1), "unknown fixture ignored");
        Show w7 = rig(0, 1, 0, 0);
        Live l2 = new Live();
        Bytes.Writer g = Commands.op(Commands.P_SET);
        Commands.fids(g, new int[] {1});
        Commands.apply(w7, l2, g.varInt(Attr.GOBO.ordinal()).unit(0.5f).toBytes(), 0, null);
        check(l2.programmer.isEmpty(), "attribute the fixture does not have is not programmed");
    }

    static void movement() {
        System.out.println("Movement planner");
        Show s = new Show();
        Show.Patch p = new Show.Patch();
        p.fid = 1; p.type = FixtureProfile.VORTEX_X1.id; p.x = 0; p.y = 10; p.z = 0; p.mount = 1; p.yaw = 0;
        s.patch.add(p);
        float[] down = Movement.aimDir(p, 0, -1, 0);
        near(FixtureProfile.VORTEX_X1.tiltDegrees(down[1]), 90, 0.5, "hung fixture: straight down = tilt +90 (local up)");
        float[] north = Movement.aimDir(p, 0, 0, -1);
        near(FixtureProfile.VORTEX_X1.panDegrees(north[0]), 0, 0.5, "hung, aim north: pan 0");
        near(FixtureProfile.VORTEX_X1.tiltDegrees(north[1]), 0, 0.5, "hung, aim north: tilt 0");
        p.mount = 0;
        float[] up = Movement.aimDir(p, 0, 1, 0);
        near(FixtureProfile.VORTEX_X1.tiltDegrees(up[1]), 90, 0.5, "standing fixture: straight up = tilt +90");
        float[] west = Movement.aimDir(p, -1, 0, 0);
        near(FixtureProfile.VORTEX_X1.panDegrees(west[0]), 90, 0.5, "standing, aim west: pan +90");
        Movement.Stage st = Movement.stage(s, 0, 0, 20);
        List<Show.Preset> pos = Movement.standardPositions(s, st, 1);
        check(pos.size() == Movement.POSITION_NAMES.length && pos.get(0).perFixture.containsKey(1), "standard positions built");
    }

    // ------------------------------------------------------------------------------------------------------------------ analysis
    static final int SR = 44100;
    static final double BPM = 128, BEAT = 60.0 / BPM, BAR = 4 * BEAT;
    /** Structure of the synthetic track (bars): intro, verse, build, drop, break, build, drop, outro. */
    static final String[] PARTS = {"intro", "verse", "build", "drop", "break", "build", "drop", "outro"};
    static final int[] BARS = {8, 16, 8, 16, 8, 8, 16, 8};

    static short[] synth() {
        int totalBars = Arrays.stream(BARS).sum();
        int n = (int) (totalBars * BAR * SR);
        short[] out = new short[n * 2];
        Random rnd = new Random(7);
        double t0 = 0;
        for (int part = 0; part < PARTS.length; part++) {
            String kind = PARTS[part];
            int len = (int) (BARS[part] * BAR * SR);
            int start = (int) (t0 * SR);
            double[][] chords = kind.equals("drop") ? new double[][] {{174.6, 220, 261.6}, {196, 246.9, 293.7}} : new double[][] {{261.6, 329.6, 392}, {220, 261.6, 329.6}};
            for (int i = 0; i < len && start + i < n; i++) {
                double t = i / (double) SR;
                double local = t;
                double beatPos = local / BEAT, inBeat = (beatPos - Math.floor(beatPos)) * BEAT;
                int beat = (int) Math.floor(beatPos);
                double prog = i / (double) len;
                double[] ch = chords[(beat / 8) % 2];
                double pad = 0;
                for (double fq : ch) pad += Math.sin(2 * Math.PI * fq * (t0 + t));
                double s = 0;
                switch (kind) {
                    case "intro", "outro" -> s = 0.06 * pad * (kind.equals("outro") ? 1 - prog : Math.min(1, prog * 3)) + hat(inBeat, rnd) * 0.02;
                    case "verse" -> s = 0.08 * pad + kick(inBeat) * 0.35 + hat(Math.abs(inBeat - BEAT / 2), rnd) * 0.05;
                    case "build" -> {
                        double density = prog < 0.5 ? 1 : prog < 0.75 ? 2 : 4;
                        double sub = BEAT / density, inSub = (beatPos * density - Math.floor(beatPos * density)) * sub;
                        s = 0.07 * pad + (rnd.nextDouble() * 2 - 1) * 0.25 * prog + snare(inSub, rnd) * (0.15 + 0.25 * prog);
                    }
                    case "drop" -> s = 0.13 * pad + kick(inBeat) * 0.6 + 0.25 * Math.signum(Math.sin(2 * Math.PI * 55 * (t0 + t))) * Math.exp(-inBeat * 3)
                        + ((beat % 2 == 1) ? snare(inBeat, rnd) * 0.25 : 0) + hat(Math.abs(inBeat - BEAT / 2), rnd) * 0.08;
                    default -> s = 0.07 * pad;
                }
                short v = (short) Math.max(-32767, Math.min(32767, s * 22000));
                out[2 * (start + i)] = v;
                out[2 * (start + i) + 1] = v;
            }
            t0 += BARS[part] * BAR;
        }
        return out;
    }

    static double kick(double t) {
        return t < 0.25 ? Math.sin(2 * Math.PI * (50 + 90 * Math.exp(-t * 30)) * t) * Math.exp(-t * 9) : 0;
    }

    static double hat(double t, Random r) {
        return t < 0.04 ? (r.nextDouble() * 2 - 1) * Math.exp(-t * 90) : 0;
    }

    static double snare(double t, Random r) {
        return t < 0.12 ? ((r.nextDouble() * 2 - 1) * 0.7 + 0.3 * Math.sin(2 * Math.PI * 190 * t)) * Math.exp(-t * 25) : 0;
    }

    static final short[] TRACK = synth();

    static Analyzer.Pcm pcm() {
        return new Analyzer.Pcm() {
            int pos;
            public int sampleRate() { return SR; }
            public int read(short[] out) {
                int frames = Math.min(out.length / 2, TRACK.length / 2 - pos);
                if (frames <= 0) return -1;
                System.arraycopy(TRACK, pos * 2, out, 0, frames * 2);
                pos += frames;
                return frames;
            }
        };
    }

    static Analysis analysis() throws Exception {
        System.out.println("Music analysis (synthetic 128 BPM track, " + Arrays.stream(BARS).sum() + " bars)");
        long t0 = System.nanoTime();
        Analysis a = Analyzer.analyze(pcm(), "synthetic", null);
        double secs = (System.nanoTime() - t0) / 1e9;
        System.out.printf(Locale.ROOT, "  analysed %.0f s of audio in %.2f s%n", a.durationMs / 1000.0, secs);
        near(a.bpm, BPM, 1.5, "tempo");
        double phaseErr = ((a.firstBeatMs / 1000.0) % BEAT);
        phaseErr = Math.min(phaseErr, BEAT - phaseErr);
        check(phaseErr < 0.04, "beat phase within 40 ms (" + Math.round(phaseErr * 1000) + " ms)");
        double t = 0;
        System.out.println("  truth:");
        for (int i = 0; i < PARTS.length; i++) { System.out.printf(Locale.ROOT, "    %6.1f s %s%n", t, PARTS[i]); t += BARS[i] * BAR; }
        System.out.println("  found:");
        for (Analysis.Section s : a.sections)
            System.out.printf(Locale.ROOT, "    %6.1f-%6.1f s %-14s energy %.2f slope %+.2f conf %.2f%n", s.startMs / 1000.0, s.endMs / 1000.0, s.label(), s.energy, s.slope, s.confidence);
        // boundaries close to the true ones (within one bar)
        int hits = 0;
        t = 0;
        for (int i = 1; i < PARTS.length; i++) {
            t += BARS[i - 1] * BAR;
            for (Analysis.Section s : a.sections) if (Math.abs(s.startMs / 1000.0 - t) <= BAR + 0.05) { hits++; break; }
        }
        check(hits >= 5, "section boundaries found within a bar: " + hits + " / 7");
        Analysis.Section d1 = a.sectionAt((long) ((8 + 16 + 8 + 4) * BAR * 1000)), d2 = a.sectionAt((long) ((8 + 16 + 8 + 16 + 8 + 8 + 4) * BAR * 1000));
        check(d1 != null && d2 != null && (d1.kind == Analysis.DROP || d1.kind == Analysis.CHORUS), "first drop recognised as a high-energy section (" + (d1 == null ? "-" : d1.label()) + ")");
        check(d1 != null && d2 != null && d1.motif == d2.motif, "second drop recognised as a repetition of the first");
        Analysis.Section b1 = a.sectionAt((long) ((8 + 16 + 4) * BAR * 1000));
        check(b1 != null && b1.kind == Analysis.BUILD, "build recognised (" + (b1 == null ? "-" : b1.label()) + ")");
        Analysis.Section br = a.sectionAt((long) ((8 + 16 + 8 + 16 + 4) * BAR * 1000));
        check(br != null && (br.kind == Analysis.BREAK || br.kind == Analysis.VERSE), "break is a calm section (" + (br == null ? "-" : br.label()) + ")");
        Analysis back = Analysis.fromBytes(a.toBytes());
        check(back != null && back.sections.size() == a.sections.size() && Math.abs(back.bpm - a.bpm) < 1e-3, "analysis cache round trip");
        System.out.println("  analysis cache: " + a.toBytes().length + " bytes");
        return a;
    }

    static void autoshow(Analysis a) {
        System.out.println("AutoShow (pre-analysis)");
        for (int style = 0; style < 4; style++) {
            Show s = rig(4, 4, 2, 2);
            s.auto.style = style;
            Movement.Stage st = Movement.stage(s, 0, 64, 10);
            AutoShow.Report r = AutoShow.generate(s, a, st);
            Show.CueList list = null;
            for (Show.CueList l : s.lists) if (l.name.equals(AutoShow.LIST_NAME)) list = l;
            String name = Show.AutoSettings.STYLES[style];
            check(list != null && list.cues.size() >= a.sections.size(), name + ": cues written (" + r.cues() + ")");
            boolean sorted = true;
            for (int i = 1; i < s.timeline.events.size(); i++) if (s.timeline.events.get(i).ms < s.timeline.events.get(i - 1).ms) sorted = false;
            check(sorted, name + ": timeline sorted");
            // accents never faster than the cap
            long last = -1000000, minGap = Long.MAX_VALUE;
            for (Show.Event e : s.timeline.events) if (e.kind == Show.Event.ACCENT) { minGap = Math.min(minGap, e.ms - last); last = e.ms; }
            check(minGap >= 1000 / 3, name + ": accents at most 3 per second (min gap " + (minGap == Long.MAX_VALUE ? "none" : minGap + " ms") + ")");
            if (style == Show.AutoSettings.AMBIENT) check(r.accents() == 0, "Ambient: no flash accents");
            // repeated motif = same palette
            Map<Integer, Float> red = new HashMap<>();
            boolean same = true;
            int idx = 0;
            for (Analysis.Section sec : a.sections) {
                Show.Cue c = null;
                for (Show.Cue q : list.cues) if (q.name.equals(sec.label())) { c = q; break; }
                if (c == null || sec.kind == Analysis.BUILD) continue;
                Look w = c.looks.get(5);
                if (w == null) continue;
                float key = w.get(Attr.RED) * 100 + w.get(Attr.GREEN) * 10 + w.get(Attr.BLUE);
                Float prev = red.putIfAbsent(sec.motif * 10 + (sec.kind == Analysis.DROP || sec.kind == Analysis.CHORUS ? 1 : 0), key);
                if (prev != null && sec.occurrence % 2 == 0 && Math.abs(prev - key) > 1e-3) same = false;
                idx++;
            }
            check(same, name + ": a repeated section finds its palette again");
            // the light really follows the music
            Engine e = new Engine();
            Live l = new Live();
            float calm = level(e, s, l, a, (long) ((8 + 4) * BAR * 1000));
            float drop = level(e, s, l, a, (long) ((8 + 16 + 8 + 6) * BAR * 1000));
            check(drop > calm, name + ": the drop is brighter than the verse (" + String.format(Locale.ROOT, "%.2f > %.2f", drop, calm) + ")");
            byte[] bytes = s.toBytes();
            check(Show.fromBytes(bytes) != null, name + ": generated show is a normal show (" + bytes.length + " bytes)");
            // rev. 2: more effects, and smooth changes between looks
            int effects = 0, hotMax = 0, kinds = 0;
            boolean[] seen = new boolean[Show.Effect.NAMES.length];   // rev. 24: 17 effects
            for (Show.Cue c : list.cues) {
                effects += c.effects.size();
                if (c.name.startsWith("Drop") || c.name.startsWith("Chorus")) hotMax = Math.max(hotMax, c.effects.size());
                for (Show.Effect ef : c.effects) if (!seen[ef.type]) { seen[ef.type] = true; kinds++; }
            }
            System.out.println("    " + name + ": " + effects + " effects in " + list.cues.size() + " cues, " + kinds + " kinds, up to " + hotMax + " on a drop");
            check(hotMax >= (style == Show.AutoSettings.AMBIENT ? 1 : 3), name + ": several effect layers on the drops");
            check(kinds >= (style == Show.AutoSettings.AMBIENT ? 3 : 5), name + ": varied effects (" + kinds + " kinds)");
            float[] worst = transitions(s, a);
            System.out.println(String.format(Locale.ROOT, "    %s: largest change per 20 ms around the cue changes: pan/tilt %.4f, colour %.3f", name, worst[0], worst[1]));
            check(worst[0] < 0.012f, name + ": movements change smoothly between looks");
            check(worst[1] < 0.2f, name + ": colours change smoothly between looks");
        }
    }

    /**
     * Largest visible change (fixture lit) from one 20 ms frame to the next, around every GO of the timeline (1 s before to 3 s after), of pan / tilt (normalised)
     * and of the colour, for every fixture. Cuts to darkness before a drop are intensity only, so they are not measured here.
     */
    static float[] transitions(Show s, Analysis a) {
        Engine e = new Engine();
        Live l = new Live();
        float move = 0, color = 0;
        for (Show.Event ev : s.timeline.events) {
            if (ev.kind != Show.Event.GO) continue;
            Map<Integer, float[]> last = new HashMap<>();
            for (long t = Math.max(0, ev.ms - 1000); t < ev.ms + 3000; t += 20) {
                Map<Integer, Engine.Output> o = e.evaluate(s, l, t, new Engine.Music(true, a.sha1, true, t));
                for (Engine.Output x : o.values()) {
                    float[] now = {x.get(Attr.PAN), x.get(Attr.TILT), x.get(Attr.RED), x.get(Attr.GREEN), x.get(Attr.BLUE), x.get(Attr.DIMMER)};
                    float[] p = last.put(x.fid, now);
                    if (p == null || now[5] < 0.05f || p[5] < 0.05f) continue;      // moves in the dark are not seen (move in dark)
                    float mv = Math.max(Math.abs(now[0] - p[0]), Math.abs(now[1] - p[1]));
                    if (mv > move && System.getenv("CSC_DEBUG") != null) System.out.println("      worst move at " + t + " ms, GO cue " + ev.cue + " fixture " + x.fid + " " + mv);
                    move = Math.max(move, mv);
                    color = Math.max(color, Math.max(Math.abs(now[2] - p[2]), Math.max(Math.abs(now[3] - p[3]), Math.abs(now[4] - p[4]))));
                }
            }
        }
        return new float[] {move, color};
    }

    static float level(Engine e, Show s, Live l, Analysis a, long ms) {
        Map<Integer, Engine.Output> o = e.evaluate(s, l, 0, new Engine.Music(true, a.sha1, true, ms));
        float sum = 0;
        for (Engine.Output x : o.values()) sum += x.get(Attr.DIMMER);
        return sum / o.size();
    }

    static void live() throws Exception {
        System.out.println("AutoShow (live analysis)");
        Analyzer.Features f = new Analyzer.Features();
        LiveAutoShow live = new LiveAutoShow();
        short[] buf = new short[2 * 2205];
        Analyzer.Pcm p = pcm();
        long ms = 0, lastChange = -100000;
        int changes = 0, drops = 0;
        long minDwell = Long.MAX_VALUE;
        List<String> log = new ArrayList<>();
        int n;
        while ((n = p.read(buf)) > 0) {
            f.add(buf, n, SR);
            ms += n * 1000L / SR;
            int st = live.update(f, ms);
            if (st >= 0) {
                if (changes > 0) minDwell = Math.min(minDwell, ms - lastChange);
                changes++;
                lastChange = ms;
                if (LiveAutoShow.STATES[st] == Analysis.DROP) drops++;
                log.add(String.format(Locale.ROOT, "%.1f s %s", ms / 1000.0, Analysis.KINDS[LiveAutoShow.STATES[st]]));
            }
        }
        System.out.println("  " + String.join(" | ", log));
        check(changes >= 4 && changes <= 30, "a reasonable number of look changes (" + changes + ")");
        check(minDwell >= 1000, "no flickering between looks (min " + (minDwell == Long.MAX_VALUE ? "-" : minDwell + " ms") + ")");
        check(drops >= 1, "drops taken live (" + drops + ")");
        near(live.bpm(), BPM, 2, "live tempo");
        Show s = rig(2, 2, 2, 2);
        int id = LiveAutoShow.prepare(s, Movement.stage(s, 0, 64, 10));
        check(s.list(id).cues.size() == LiveAutoShow.STATES.length, "live look list prepared");
    }

    static void perf() {
        System.out.println("Timings");
        for (int count : new int[] {10, 25, 50, 100, 200}) {
            Show s = rig(count / 4, count / 4, count / 4, count - 3 * (count / 4));
            Analysis a = Analyzer.finish(new Analyzer.Features(), "x", 0);
            Live l = new Live();
            Engine e = new Engine();
            Show.Effect c = new Show.Effect();
            c.type = Show.Effect.CIRCLE; c.fids = all(s); c.fade = 0;
            Bytes.Writer w = Commands.op(Commands.P_EFFECT_ADD);
            writeEffect(w, c);
            Commands.apply(s, l, set(all(s), Attr.DIMMER, 1f), 0, null);
            Commands.apply(s, l, store(1, 1, 3), 0, null);
            Commands.apply(s, l, w.toBytes(), 0, null);
            Commands.apply(s, l, list(Commands.GO, 1), 0, null);
            for (int i = 0; i < 200; i++) e.evaluate(s, l, i * 16, Engine.Music.NONE);
            long t0 = System.nanoTime();
            int runs = 2000;
            for (int i = 0; i < runs; i++) e.evaluate(s, l, 1000 + i * 16, Engine.Music.NONE);
            double us = (System.nanoTime() - t0) / 1000.0 / runs;
            System.out.printf(Locale.ROOT, "  %3d fixtures: engine %.1f us per frame (all fixtures), show %d bytes, live %d bytes%n", count, us,
                s.toBytes().length, l.toBytes().length);
            check(us < 2000, count + " fixtures evaluated in under 2 ms");
        }
    }

    // ------------------------------------------------------------------------------------------------------------------ optics (rev. 5)
    static void optics() {
        System.out.println("-- beam optics (rev. 5)");
        float tanSpot = (float) Math.tan(Math.toRadians(4 / 2.0)), tanW19 = (float) Math.tan(Math.toRadians(60 / 2.0));
        // light in the air
        float prev = 2f; boolean down = true;
        for (float d = 1f; d <= 30f; d += 1f) { float a = BeamOptics.air(d, 30f, true, 0.4f, tanW19); if (a > prev + 1e-6f) down = false; prev = a; }
        check(down, "wash: light in the air only decreases after the lens");
        near(BeamOptics.air(30f, 30f, false, 0.4f, tanW19), 0, 1e-6, "a throw that hits nothing ends at zero");
        float spot8 = BeamOptics.air(8f, 40f, true, 0.3f, tanSpot), wash8 = BeamOptics.air(8f, 40f, true, 0.45f, tanW19);
        System.out.printf(java.util.Locale.ROOT, "  air at 8 blocks: VORTEX 4 deg %.3f, W19 60 deg %.3f%n", spot8, wash8);
        check(spot8 > 4f * wash8, "a tight beam carries far, a wide wash fades (1/r law)");
        // rev. 17: 15 % (10 % before): the wash cones carry a little further (BeamOptics.SPREAD_EXP), still no flat triangle across the stage
        check(BeamOptics.air(12f, 40f, true, 0.45f, tanW19) < 0.15f, "W19 at 60 deg: under 15 % of its light in the air 12 blocks away (no flat triangle across the stage): " + BeamOptics.air(12f, 40f, true, 0.45f, tanW19));
        float worst = 9f;
        for (int deg = 0; deg <= 90; deg += 5) {
            float c = (float) Math.cos(Math.toRadians(deg));
            worst = Math.min(worst, Math.max(BeamOptics.sideWeight(c), BeamOptics.axisWeight(c)));
        }
        check(worst >= 0.3f, "the beam is visible from every viewing angle (front, side, behind)");
        // light on surfaces
        check(BeamOptics.surface(1f, 10f, 0.6f, 1f) > BeamOptics.surface(1f, 10f, 6f, 1f), "a tight spot lights its surface more than a wide wash");
        check(BeamOptics.surface(1f, 10f, 6f, 1f) > 0.6f, "a wide wash still clearly lights the stage 10 blocks away");
        check(BeamOptics.surface(1f, 10f, 2f, 0.1f) < BeamOptics.surface(1f, 10f, 2f, 1f), "grazing light is weaker");
        check(BeamOptics.surface(1f, 40f, 2f, 1f) < BeamOptics.surface(1f, 5f, 2f, 1f), "light weakens with distance");
        near(BeamOptics.surface(0f, 5f, 2f, 1f), 0, 1e-9, "no light, no lit surface");
        check(BeamOptics.surface(5f, 0f, 0f, 1f) <= 3f, "lit surfaces are capped (no burnt white)");
        check(BeamOptics.relight(1f, 0f) > 3f * BeamOptics.relight(1f, 1f) - 2f && BeamOptics.relight(1f, 0f) > 4f,
            "the same light shows much more on a dark stage at night than in daylight");
        check(BeamOptics.relight(3f, 0f) <= 12f && BeamOptics.relight(0f, 0f) == 1f, "relighting is capped, and no light changes nothing");
        check(BeamOptics.focusSharpness(0.5f) == 1f && BeamOptics.focusSharpness(0f) == 1f && BeamOptics.focusSharpness(1f) == 0f
            && BeamOptics.focusSharpness(0.7f) > BeamOptics.focusSharpness(0.9f), "gobo focus (rev. 16g): sharp from 0 to 60 %, softer up to 100 %");
        near(FixtureProfile.PULSESTRIKE_M.renderScale, 2.0 * 1.3, 1e-4, "PULSESTRIKE M drawn 30 % larger (rev. 6)");
        near(FixtureProfile.PULSESTRIKE_M.tiltPivotY, 0.222 * 2.6, 1e-4, "PULSESTRIKE M tilt pivot follows its size");
        // rev. 8: beams
        float tanSpot2 = (float) Math.tan(Math.toRadians(2));
        check(BeamOptics.air(27f, 30f, true, 0.3f, tanSpot2) > 0.2f && BeamOptics.air(29.95f, 30f, true, 0.3f, tanSpot2) > 0.03f,
            "a beam that hits a block is visible down to the surface, softened just before it (no hard cut line)");
        check(BeamOptics.air(19.9f, 20f, true, 0.45f, tanW19) < 0.5f * BeamOptics.air(12f, 20f, true, 0.45f, tanW19),
            "a wide wash fades before the floor it meets (no hard edge where the floor cuts it)");
        check(BeamOptics.cameraFade(0.5f, 3f) == 0f && BeamOptics.cameraFade(20f, 3f) == 1f && BeamOptics.cameraFade(4f, 3f) < 1f,
            "a beam fades where the camera stands in it (no wall of colour)");
        check(BeamOptics.discWeight(6f) < 0.15f && BeamOptics.discWeight(0.3f) == 1f, "a wide wash seen along its axis shows almost no flat discs");
        check(BeamOptics.washAirScale((float) Math.tan(Math.toRadians(25))) < 0.6f && BeamOptics.washAirScale((float) Math.tan(Math.toRadians(5))) > 0.85f,
            "a wide wash is thinner in the air than a narrow one");
        // rev. 10: beams reach the floor
        check(BeamOptics.air(19.95f, 20f, true, 0.45f, tanW19) > 0.4f * BeamOptics.air(18f, 20f, true, 0.45f, tanW19),
            "a beam keeps at least 40 % of its light down to the surface it lands on (it visibly touches the floor)");
        check(BeamOptics.AIR_DENSITY > 1f, "rev. 10: more volume in the air than rev. 9");
        // rev. 9
        check(BeamOptics.minBeamRadius(60f) > 0.15f && BeamOptics.minBeamRadius(2f) < 0.01f, "far beams keep a few pixels of soft width");
        check(BeamOptics.irisExit(0f) < BeamOptics.irisExit(1f) && BeamOptics.irisExit(1f) == 1f && BeamOptics.irisLight(1f) == 1f
            && BeamOptics.irisLight(0f) >= 0.7f, "closing the iris narrows the exit and holds back a little light");
        check(BeamOptics.edgeDefinition(1f, 1f) == 1f && BeamOptics.edgeDefinition(0.2f, 1f) < 0.25f && BeamOptics.edgeDefinition(1f, 0f) < 0.4f,
            "open iris + sharp focus = defined spot edge in the haze; closed iris or soft focus = soft shaft");
        near(BeamOptics.blinderAngle(0f), 70, 1e-4, "PULSESTRIKE focus 0 = the wide 70 deg flood (unchanged)");
        check(BeamOptics.blinderAngle(1f) == 20f && FixtureProfile.PULSESTRIKE_M.has(Attr.FOCUS), "PULSESTRIKE M has a focus, down to a 20 deg punch");
        // rev. 8: spill light on the rig
        float[] dir = new float[3];
        BeamOptics.spillDir(0, dir);
        check(dir[1] == 1f, "the first spill ray goes up (the truss / roof above a hung fixture)");
        java.util.Set<String> dirs = new java.util.HashSet<>();
        boolean unit = true;
        for (int i = 0; i < BeamOptics.spillCount(); i++) {
            BeamOptics.spillDir(i, dir);
            if (Math.abs(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2] - 1f) > 1e-4f) unit = false;
            dirs.add(Math.round(dir[0] * 100) + "," + Math.round(dir[1] * 100) + "," + Math.round(dir[2] * 100));
        }
        check(unit && dirs.size() == BeamOptics.spillCount() && BeamOptics.spillRays(BeamOptics.HIGH) == BeamOptics.spillCount(),
            "spill rays: " + BeamOptics.spillCount() + " different unit directions, all used at High");
        check(BeamOptics.spill(1f, 1f, 1f, true) > 0.6f && BeamOptics.spill(BeamOptics.SPILL_RANGE, 1f, 1f, true) == 0f
            && BeamOptics.spill(4f, 1f, 1f, true) < BeamOptics.spill(1f, 1f, 1f, true), "spill: strong on the truss next to the fixture, gone at its range");
        check(BeamOptics.spill(1f, 1f, 1f, false) < BeamOptics.spill(1f, 1f, 1f, true), "a beam fixture spills less on its rig than a wash");
        check(BeamOptics.relight(BeamOptics.spill(1f, 1f, 1f, true), 0.27f) > 3f, "the truss next to a lit wash at night is clearly brightened (x > 3)");
        // sampling
        check(BeamOptics.rays(BeamOptics.ULTRA, 0.5f) == 1, "tight beam: one surface ray");
        check(BeamOptics.rays(BeamOptics.HIGH, 6f) == 7 && BeamOptics.rays(BeamOptics.ULTRA, 6f) == 13 && BeamOptics.rays(BeamOptics.LOW, 6f) == 1,
            "wide beam: 1 / 7 / 13 rays at low / high / ultra");
        float[] o = new float[2];
        boolean inside = true;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 13; i++) {
            BeamOptics.rayOffset(i, 13, o);
            if (o[0] * o[0] + o[1] * o[1] > 1.0001f) inside = false;
            seen.add(Math.round(o[0] * 1000) + "," + Math.round(o[1] * 1000));
        }
        check(inside && seen.size() == 13, "surface rays stay inside the cone and are all different");
        // geometry cost per beam (vertices = 4 per quad)
        int rev3Wash = 2 * 18 * 8 * 2 * 4, rev4Wash = 2 * 14 * 6 * 4;
        int rev5Wash = 4 * BeamOptics.quadsPerBeam(BeamOptics.HIGH, 7, false), rev5Spot = 4 * BeamOptics.quadsPerBeam(BeamOptics.HIGH, 1, true);
        System.out.printf(java.util.Locale.ROOT, "  vertices per wash beam at High: rev. 3 %d, rev. 4 %d, rev. 5 %d (VORTEX beam rev. 5: %d)%n", rev3Wash, rev4Wash, rev5Wash, rev5Spot);
        check(rev5Wash * 10 < rev3Wash, "rev. 5 wash beam: over 10 x fewer vertices than rev. 3");
        int ultra = 4 * BeamOptics.quadsPerBeam(BeamOptics.ULTRA, 13, true);
        check(ultra < 1152, "even Ultra stays under one rev. 3 cone (" + ultra + " vertices)");
        check(BeamOptics.rayBudget(BeamOptics.HIGH) >= 52 * 7 / 8, "High: 52 wide beams resampled every 8 frames fit the ray budget");
    }
}
