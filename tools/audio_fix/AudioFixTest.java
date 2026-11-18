import com.concertstage.audio.AudioGroups;
import com.concertstage.audio.SpeakerModels;
import com.concertstage.audio.core.*;
import java.util.*;

/**
 * 1.1.1 audio fix — checks with a verdict (PASS / FAIL), off game: distance laws (B), subwoofer voicing and crossover (A), delay tower
 * recognition (C), several towers (E), false detections (F), two listeners (G), delays and their alignment, legacy curve, memory of
 * idle delay lines. Usage: java AudioFixTest
 */
public class AudioFixTest {
    static int fail, checks;

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) { fail++; System.out.println("  FAIL " + what); }
    }

    static List<SoundSystems.Source> sources(List<Rig.Arr> scene) {
        List<SoundSystems.Source> s = new ArrayList<>();
        for (int i = 0; i < scene.size(); i++) s.add(M.source(i, scene.get(i)));
        return s;
    }

    static SoundSystems.Layout layout(List<Rig.Arr> scene) { return SoundSystems.classify(sources(scene), SoundSystems.Mode.AUTO, 10); }

    static long towers(SoundSystems.Layout l) { return l.installations().stream().filter(i -> i.role() == SoundSystems.Role.TOWER).count(); }

    static SoundSystems.Installation of(List<Rig.Arr> scene, SoundSystems.Layout l, String name) {
        for (int i = 0; i < scene.size(); i++) if (scene.get(i).name().equals(name)) return l.of(i);
        return null;
    }

    public static void main(String[] a) {
        // ---------------------------------------------------------- B: distance laws
        for (String id : SpeakerModels.ids()) {
            SpeakerModels.Model m = SpeakerModels.get(id);
            DistanceLaw law = DistanceLaw.of(m, m.height() * 6, 1);
            double prev = Double.POSITIVE_INFINITY;
            boolean mono = true, finite = true;
            for (double d = 0; d <= 300; d += .25) {
                double g = law.gain(d, 1);
                if (!Double.isFinite(g) || g < 0) finite = false;
                if (g > prev + 1e-9) mono = false;
                prev = g;
            }
            check(mono && finite, id + ": level never rises with distance, finite");
            check(law.gain(0, 1) <= 2.5 && law.gain(0, 1) >= 1, id + ": capped close to the cabinet (" + law.gain(0, 1) + ")");
            check(law.gain(law.range() + 1, 1) == 0, id + ": silent past its range");
            check(Math.abs(law.gain(law.reference(), 1) - 1) < 1e-9, id + ": 1 at the reference distance");
        }
        DistanceLaw line = DistanceLaw.of(SpeakerModels.get("csc_orvane_lx12"), 8 * .78, 1), sub = DistanceLaw.of(SpeakerModels.get("csc_aurex_s218_f"), 4 * 1.15, 1);
        double l2 = 20 * Math.log10(line.gain(2, 1)), l20 = 20 * Math.log10(line.gain(20, 1)), l120 = 20 * Math.log10(line.gain(120, 1));
        System.out.println(String.format(Locale.ROOT, "B: LX12 x8 2 -> 20 blocks %.1f dB, 20 -> 120 blocks %.1f dB (before: -4.9 / -6.6 dB)", l20 - l2, l120 - l20));
        check(l20 - l2 < -7 && l120 - l20 < -9, "line array: clearly quieter going away");
        check(sub.range() >= line.range(), "subs carry at least as far as the mains (before: 112 vs 192 blocks)");
        check(DistanceLaw.of(SpeakerModels.get("csc_orvane_lx12"), 6, 1).gain(30, 1.3) < DistanceLaw.of(SpeakerModels.get("csc_orvane_lx12"), 6, 1).gain(30, .75), "curve setting: strong falls faster than gentle");
        // legacy curve: the old numbers exactly
        DistanceCurve old = new DistanceCurve(12, 192, .65, 1);
        List<Rig.Arr> one = List.of(new Rig.Arr("A", AudioGroups.MAIN_LEFT, "csc_orvane_lx12", 0, Rig.EAR, 0, 0, 8));
        M.State st = M.prepare(one);
        ListenerMix.Settings legacy = new ListenerMix.Settings(true, false, 1, old, 1, 1, 1, 1, 1, true, true, true);
        ListenerMix.Result r = ListenerMix.compute(st.sources, st.layout, st.desk, 0, Rig.EAR, 40, 0, legacy, st, ListenerMix.NO_WALLS, null);
        double expected = new DistanceCurve(12, 192 * Acoustics.range(false, 8 * .78) / 192, .65, 1).gain(40);
        check(Math.abs(Math.hypot(r.weights[0], r.weights[1]) - expected) < 1e-4, "legacy curve gives the old level (" + Math.hypot(r.weights[0], r.weights[1]) + " / " + expected + ")");

        // ---------------------------------------------------------- A: subwoofers
        for (String id : List.of("csc_aurex_s218_f", "csc_orvane_sb28", "csc_veyra_b2", "csc_noryx_b1")) {
            SubVoicing v = SubVoicing.of(SpeakerModels.get(id));
            check(v.crossoverHz() >= 80 && v.crossoverHz() <= 100, id + ": crossover 80-100 Hz");
            check(v.gainDb() > 0 && v.gainDb() <= 6, id + ": its own level above a flat sum, at most +6 dB");
            // the low pass of the sub feed and the high pass of the mains at the model's crossover: -6 dB each at the cut, sum flat
            double[] lp = response(v.crossoverHz(), true), hp = response(v.crossoverHz(), false);
            check(Math.abs(lp[0] + 6.02) < .3 && Math.abs(hp[0] + 6.02) < .3, id + ": -6 dB at the cut on both sides (" + lp[0] + " / " + hp[0] + ")");
            check(lp[1] < -22, id + ": low pass at least 22 dB down one octave above the cut (" + lp[1] + ")");
            check(Math.abs(lp[2]) < .35, id + ": low pass + high pass in phase, flat sum (" + lp[2] + " dB)");
        }
        // the sub slot protection: a sub feed far above full scale never makes the output clip
        {
            ConsoleMixer mx = new ConsoleMixer(Rig.RATE, ListenerMix.SLOTS, ListenerMix.sides(), ListenerMix.subs());
            float[] w = new float[ListenerMix.SLOTS * 2];
            w[AudioGroups.SUBS * 2] = w[AudioGroups.SUBS * 2 + 1] = 4;
            Rig.concertBass = 0;                                   // the path itself (concert bass adds harmonics on purpose)
            short[] out = Rig.run(mx, Rig.sine(45, 0, 3), new Rig.Feed() { public float[] weights(long f) { return w; } }, true);
            Rig.concertBass = .5f;
            check(Rig.clipped(out) == 0 && mx.subLimiterDb() > 3, "sub protection: +12 dB on a full-scale 45 Hz tone, no clipping (protection -" + mx.subLimiterDb() + " dB)");
            System.out.println(String.format(Locale.ROOT, "A: sub feed at +12 dB on a 0 dBFS tone: peak %.1f dBFS, THD %.2f %%, clipped %d", Rig.peakDb(out, Rig.RATE), Rig.thd(out, 45, Rig.RATE), Rig.clipped(out)));
        }
        // the subs follow their crossover: a Noryx B1 console crosses at 100 Hz, an S218-F at 80
        check(compute(Rig.festival("csc_noryx_b1"), 0, 25).crossoverHz == 100 && compute(Rig.festival("csc_aurex_s218_f"), 0, 25).crossoverHz == 80, "crossover of the console follows its sub model");

        // ---------------------------------------------------------- C: one delay tower, recognised by itself
        List<Rig.Arr> c = new ArrayList<>(Rig.festival("csc_aurex_s218_f"));
        c.addAll(Rig.tower(60, AudioGroups.MAIN_LEFT, AudioGroups.MAIN_RIGHT));
        SoundSystems.Layout lc = layout(c);
        SoundSystems.Installation t = of(c, lc, "Tower L z60");
        check(towers(lc) == 1 && t != null && t.role() == SoundSystems.Role.TOWER, "C: stereo tower 60 blocks in front, aimed away: recognised");
        double[] mc = SoundSystems.mainCentre(lc);
        // the exact delay: at the point the tower serves (15 blocks in front, ear height) its sound arrives 10 ms after the mains'
        double px = t.x(), py = lc.earY(), pz = t.z() + SoundSystems.ALIGN_AHEAD;
        double viaMain = Math.sqrt(Math.pow(px - mc[0], 2) + Math.pow(py - mc[1], 2) + Math.pow(pz - mc[2], 2)) / 343 * 1000;
        double viaTower = t.delayMs() + Math.sqrt(Math.pow(px - t.x(), 2) + Math.pow(py - t.y(), 2) + Math.pow(pz - t.z(), 2)) / 343 * 1000;
        check(Math.abs(viaTower - viaMain - 10) < 1e-6 && t.delayMs() > 150 && t.delayMs() < 200, "C: automatic delay " + String.format(Locale.ROOT, "%.1f ms", t.delayMs()) + ": the tower arrives exactly 10 ms after the mains where its audience stands");
        // and the listener there hears exactly that (ListenerMix with propagation)
        ListenerMix.Result atP = compute(c, px, pz);
        check(Math.abs(atP.delays[AudioGroups.COUNT] - atP.delays[AudioGroups.MAIN_LEFT] - 10) < .05, "C: at the alignment point the player hears the tower " + atP.delays[AudioGroups.COUNT] + " ms after the mains (10 expected)");
        check(of(c, lc, "LX12 L").role() == SoundSystems.Role.MAIN && of(c, lc, "Subs L").role() == SoundSystems.Role.MAIN && of(c, lc, "LX12 R").role() == SoundSystems.Role.MAIN, "C: hangs and sub stacks are the main system");
        check(of(c, lc, "M15 0").role() != SoundSystems.Role.TOWER && of(c, lc, "Fill L").role() != SoundSystems.Role.TOWER, "C: monitors and fills stay what they are");
        // the same tower in the Delay group (mono), and the mode switches
        List<Rig.Arr> cd = new ArrayList<>(Rig.festival("csc_aurex_s218_f"));
        cd.addAll(Rig.tower(40, AudioGroups.DELAY, AudioGroups.DELAY));
        check(towers(layout(cd)) == 1, "C: tower in the Delay group recognised");
        check(towers(SoundSystems.classify(sources(c), SoundSystems.Mode.DELAY_GROUP, 10)) == 0 && towers(SoundSystems.classify(sources(cd), SoundSystems.Mode.DELAY_GROUP, 10)) == 1, "C: mode 'Delay group only'");
        check(towers(SoundSystems.classify(sources(c), SoundSystems.Mode.OFF, 10)) == 0, "C: mode off");

        // ---------------------------------------------------------- E: several towers
        List<Rig.Arr> e = new ArrayList<>(Rig.festival("csc_aurex_s218_f"));
        for (int z : new int[] {50, 100, 150}) e.addAll(Rig.tower(z, AudioGroups.MAIN_LEFT, AudioGroups.MAIN_RIGHT));
        SoundSystems.Layout le = layout(e);
        SoundSystems.Installation e1 = of(e, le, "Tower L z50"), e2 = of(e, le, "Tower L z100"), e3 = of(e, le, "Tower L z150");
        check(towers(le) == 3 && e1.tower() == 0 && e2.tower() == 1 && e3.tower() == 2, "E: three towers, numbered from the stage");
        check(e1.delayMs() < e2.delayMs() && e2.delayMs() < e3.delayMs(), "E: each tower's delay from its own distance to the mains (" + (int) e1.delayMs() + " / " + (int) e2.delayMs() + " / " + (int) e3.delayMs() + " ms)");
        // in each zone its own tower is the loudest installation
        for (SoundSystems.Installation ti : List.of(e1, e2, e3)) {
            // full-range only (subs off): the subs of the stage carry far on purpose, the question is which installation you hear
            M.State se = M.prepare(e);
            ListenerMix.Settings noSubs = new ListenerMix.Settings(true, true, 1, null, 1, 1, 1, 0, 1, true, true, true);
            ListenerMix.Result rr = ListenerMix.compute(se.sources, se.layout, se.desk, 0, Rig.EAR, ti.z() + 20, Math.PI, noSubs, se, ListenerMix.NO_WALLS, null);
            int loudest = 0;
            for (int i = 1; i < rr.installationLevel.length; i++) if (rr.installationLevel[i] > rr.installationLevel[loudest]) loudest = i;
            check(loudest == ti.index(), "E: 20 blocks in front of tower " + ti.tower() + ", it is the loudest full-range installation");
        }

        // ---------------------------------------------------------- F: false detections
        Object[][] wrong = {
            {"aimed back at the stage", new Rig.Arr("X", AudioGroups.CUSTOM, "csc_orvane_lx8", 0, 8, 60, 180, 6)},
            {"beside the stage (out fill aimed sideways)", new Rig.Arr("X", AudioGroups.CUSTOM, "csc_orvane_lx8", 60, 8, 10, 270, 6)},
            {"too close (15 blocks in front)", new Rig.Arr("X", AudioGroups.CUSTOM, "csc_orvane_lx8", 0, 8, 15, 0, 6)},
            {"monitors far away", new Rig.Arr("X", AudioGroups.MONITOR_2, "csc_aurex_m15_w", 0, 1.2, 60, 0, 1)},
            {"subs alone far away", new Rig.Arr("X", AudioGroups.SUBS, "csc_aurex_s218_f", 0, 1.2, 60, 0, 4)},
            {"aimed 70° sideways", new Rig.Arr("X", AudioGroups.CUSTOM, "csc_orvane_lx8", 0, 8, 60, 70, 6)},
            {"front fill group far away", new Rig.Arr("X", AudioGroups.FRONT_FILL, "csc_noryx_a1", 0, 1.2, 40, 0, 2)}};
        for (Object[] w : wrong) {
            List<Rig.Arr> f = new ArrayList<>(Rig.festival("csc_aurex_s218_f"));
            f.add((Rig.Arr) w[1]);
            SoundSystems.Layout lf = layout(f);
            check(towers(lf) == 0, "F: " + w[0] + " is not a delay tower (" + lf.of(f.size() - 1).reason() + ")");
        }
        // no mains at all: nothing classified (no guess)
        List<Rig.Arr> nomain = new ArrayList<>(Rig.tower(60, AudioGroups.CUSTOM, AudioGroups.CUSTOM));
        nomain.add(new Rig.Arr("Y", AudioGroups.CUSTOM, "csc_orvane_lx12", 0, 9, 0, 0, 8));
        check(towers(layout(nomain)) == 0, "F: no left / right mains, nothing classified");

        // ---------------------------------------------------------- D: delays where main and tower overlap
        ListenerMix.Result mid = compute(c, 0, 70);
        float[] dl = mid.delays;
        double heardDiff = dl[AudioGroups.COUNT] - dl[AudioGroups.MAIN_LEFT];
        double mainArr = Math.sqrt(Math.pow(0 - mc[0], 2) + Math.pow(Rig.EAR - mc[1], 2) + Math.pow(70 - mc[2], 2)) / 343 * 1000;
        double towArr = t.delayMs() + Math.sqrt(Math.pow(0 - t.x(), 2) + Math.pow(Rig.EAR - t.y(), 2) + Math.pow(70 - t.z(), 2)) / 343 * 1000;
        check(Math.abs(heardDiff - (towArr - mainArr)) < .01 && heardDiff > 0 && heardDiff < 30, "D: 10 blocks in front of the tower, it is heard " + String.format(Locale.ROOT, "%.1f", heardDiff) + " ms after the mains (precedence, no echo)");
        check(dl[AudioGroups.MAIN_LEFT] == 0, "D: the earliest installation is not delayed (the music stays on the beat of the light show)");
        float[] applied = new float[ListenerMix.SLOTS], target = new float[ListenerMix.SLOTS];
        target[10] = 1.5f;
        check(!ListenerMix.settle(target, applied, 2) && applied[10] == 0, "D: delays under 2 ms of change are not applied (no crossfade every tick)");
        target[10] = 2.5f;
        check(ListenerMix.settle(target, applied, 2) && applied[10] == 2.5f, "D: delays moved by more are applied");

        // ---------------------------------------------------------- anti-echo: a tower heard late is turned down, not in front of it
        {
            M.State sc = M.prepare(c);
            ListenerMix.Settings off = new ListenerMix.Settings(true, true, 1, null, 1, 1, 1, 1, 1, true, true, false);
            ListenerMix.Result behindOn = ListenerMix.compute(sc.sources, sc.layout, sc.desk, 0, Rig.EAR, 45, Math.PI, M.SET, sc, ListenerMix.NO_WALLS, null);
            double onDb = 20 * Math.log10(Math.hypot(behindOn.weights[AudioGroups.COUNT * 2], behindOn.weights[AudioGroups.COUNT * 2 + 1]));
            double late = behindOn.delays[AudioGroups.COUNT] - behindOn.delays[AudioGroups.MAIN_LEFT];
            M.State sc2 = M.prepare(c);
            ListenerMix.Result behindOff = ListenerMix.compute(sc2.sources, sc2.layout, sc2.desk, 0, Rig.EAR, 45, Math.PI, off, sc2, ListenerMix.NO_WALLS, null);
            double offDb = 20 * Math.log10(Math.hypot(behindOff.weights[AudioGroups.COUNT * 2], behindOff.weights[AudioGroups.COUNT * 2 + 1]));
            check(late > 80 && offDb - onDb > 17, String.format(Locale.ROOT, "anti-echo: 15 blocks behind the tower it arrives %.0f ms late and is turned down %.1f dB", late, offDb - onDb));
            M.State sc3 = M.prepare(c), sc4 = M.prepare(c);
            ListenerMix.Result fOn = ListenerMix.compute(sc3.sources, sc3.layout, sc3.desk, 0, Rig.EAR, 80, Math.PI, M.SET, sc3, ListenerMix.NO_WALLS, null);
            ListenerMix.Result fOff = ListenerMix.compute(sc4.sources, sc4.layout, sc4.desk, 0, Rig.EAR, 80, Math.PI, off, sc4, ListenerMix.NO_WALLS, null);
            check(Arrays.equals(fOn.weights, fOff.weights), "anti-echo: in front of the tower nothing is touched");
        }

        // ---------------------------------------------------------- advice: where the towers should stand
        {
            List<Rig.Arr> alone = Rig.festival("csc_aurex_s218_f");
            M.State sa = M.prepare(alone);
            TowerAdvice.Advice adv = TowerAdvice.of(sa.sources, sa.layout, 1);
            for (int i = 0; i < 20; i++) TowerAdvice.of(sa.sources, sa.layout, 1);
            long t0 = System.nanoTime();
            for (int i = 0; i < 50; i++) TowerAdvice.of(sa.sources, sa.layout, 1);
            System.out.println(String.format(Locale.ROOT, "advice, mains alone: towers at %s blocks, depth %d; computed in %.2f ms (once per change of the installation)",
                adv.recommended(), Math.round(adv.depth()), (System.nanoTime() - t0) / 50e6));
            check(!adv.recommended().isEmpty() && adv.recommended().get(0) > 40 && adv.recommended().get(0) < 110, "advice: a first tower recommended between 40 and 110 blocks");
            for (int k = 1; k < adv.recommended().size(); k++) check(adv.recommended().get(k) - adv.recommended().get(k - 1) >= TowerAdvice.MIN_GAP, "advice: towers at least 20 blocks apart");
            // a tower built where advised: well placed, no dip; built far too close: told so
            double r0 = adv.recommended().get(0);
            List<Rig.Arr> good = new ArrayList<>(alone);
            good.addAll(Rig.tower(Math.round(r0), AudioGroups.MAIN_LEFT, AudioGroups.MAIN_RIGHT));
            M.State sg = M.prepare(good);
            TowerAdvice.Advice ag = TowerAdvice.of(sg.sources, sg.layout, 1);
            check(ag.built().size() == 1 && Math.abs(ag.built().get(0).offset()) <= 10, "advice: a tower built where advised is well placed (" + ag.built() + ")");
            List<Rig.Arr> bad = new ArrayList<>(alone);
            bad.addAll(Rig.tower(Math.round(r0) - 30, AudioGroups.MAIN_LEFT, AudioGroups.MAIN_RIGHT));
            M.State sb = M.prepare(bad);
            TowerAdvice.Advice ab = TowerAdvice.of(sb.sources, sb.layout, 1);
            check(ab.built().size() == 1 && ab.built().get(0).offset() < -20, "advice: a tower 30 blocks too close is reported too close (" + ab.built() + ")");
            // a small PA close to its audience: the mains alone may be enough; no mains: no advice
            M.State nm = M.prepare(nomain);
            check(!TowerAdvice.of(nm.sources, nm.layout, 1).any(), "advice: without mains, no advice");
        }

        // ---------------------------------------------------------- G: two listeners
        ListenerMix.Result near = compute(c, 0, 10), far = compute(c, 0, 100);
        double nearDb = 10 * Math.log10(energy(near.weights)), farDb = 10 * Math.log10(energy(far.weights));
        check(nearDb - farDb > 6, String.format(Locale.ROOT, "G: player at 10 blocks hears %.1f dB more than one at 100 (each his own mix)", nearDb - farDb));
        ListenerMix.Result again = compute(c, 0, 10);
        check(Arrays.equals(near.weights, again.weights), "G: a player's mix depends only on his own position");

        // ---------------------------------------------------------- memory: idle delay lines take nothing
        {
            Runtime rt = Runtime.getRuntime();
            System.gc();
            long before = rt.totalMemory() - rt.freeMemory();
            ConsoleMixer[] keep = new ConsoleMixer[8];
            for (int i = 0; i < keep.length; i++) keep[i] = new ConsoleMixer(Rig.RATE, ListenerMix.SLOTS, ListenerMix.sides(), ListenerMix.subs());
            System.gc();
            long after = rt.totalMemory() - rt.freeMemory();
            double perMixer = (after - before) / 8.0 / 1e6;
            System.out.println(String.format(Locale.ROOT, "memory: one %d-slot mixer before any delay: about %.2f MB (JVM heap difference, 8 mixers)", ListenerMix.SLOTS, perMixer));
            check(perMixer < 3, "memory: delay lines allocated only when a delay is used");
        }

        System.out.println(fail == 0 ? "AudioFixTest PASS (" + checks + " checks)" : "AudioFixTest FAIL " + fail + " / " + checks);
        if (fail > 0) System.exit(1);
    }

    static double energy(float[] w) { double e = 0; for (float v : w) e += v * v; return e; }

    static ListenerMix.Result compute(List<Rig.Arr> scene, double x, double z) {
        M.State st = M.prepare(scene);
        return ListenerMix.compute(st.sources, st.layout, st.desk, x, Rig.EAR, z, Math.PI, M.SET, st, ListenerMix.NO_WALLS, null);
    }

    /** {level at the cut, level one octave above (low pass) / below (high pass), sum of both at the cut} in dB. */
    static double[] response(double hz, boolean lowPass) {
        double cut = tone(hz, hz, 1), octave = tone(hz, lowPass ? 2 * hz : hz / 2, lowPass ? 1 : 2);
        double sum = sumAt(hz, hz);
        return lowPass ? new double[] {cut, octave, sum} : new double[] {tone(hz, hz, 2), tone(hz, hz / 2, 2), sum};
    }

    static double tone(double xo, double f, int mode) {
        SpeakerDsp d = new SpeakerDsp(Rig.RATE);
        int n = Rig.RATE;
        short[] in = new short[n * 2];
        for (int i = 0; i < n; i++) { short v = (short) Math.round(16000 * Math.sin(2 * Math.PI * f * i / Rig.RATE)); in[2 * i] = v; in[2 * i + 1] = v; }
        float[] out = new float[n];
        d.process(in, n, 0, 0, 0, 0, mode, (float) xo, out);
        double e = 0;
        for (int i = n / 2; i < n; i++) e += out[i] * out[i];
        return 10 * Math.log10(e / (n / 2) / (16000.0 * 16000.0 / 2));
    }

    static double sumAt(double xo, double f) {
        SpeakerDsp lp = new SpeakerDsp(Rig.RATE), hp = new SpeakerDsp(Rig.RATE);
        int n = Rig.RATE;
        short[] in = new short[n * 2];
        for (int i = 0; i < n; i++) { short v = (short) Math.round(16000 * Math.sin(2 * Math.PI * f * i / Rig.RATE)); in[2 * i] = v; in[2 * i + 1] = v; }
        float[] a = new float[n], b = new float[n];
        lp.process(in, n, 0, 0, 0, 0, 1, (float) xo, a);
        hp.process(in, n, 0, 0, 0, 0, 2, (float) xo, b);
        double e = 0;
        for (int i = n / 2; i < n; i++) { double s = a[i] + b[i]; e += s * s; }
        return 10 * Math.log10(e / (n / 2) / (16000.0 * 16000.0 / 2));
    }
}
