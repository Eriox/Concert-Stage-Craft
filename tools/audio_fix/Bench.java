import com.concertstage.audio.AudioGroups;
import com.concertstage.audio.core.ConsoleMixer;
import java.util.*;

/**
 * 1.1.1 audio fix — measurements run the same way on the code before and after the fix (compiled once with before/M.java against
 * the old sources, once with after/M.java against the new ones). Tests B (distance), A (subwoofers), D (transition to a delay tower),
 * H (cost). Every number printed is measured by this program; nothing is estimated. Usage: java Bench [b|a|d|h|all]
 */
public class Bench {
    static final double FACING_STAGE = Math.PI;     // the game's yaw 180°: looking towards -z, at the stage

    public static void main(String[] args) {
        String what = args.length > 0 ? args[0] : "all";
        System.out.println("=== " + M.name() + " ===");
        if (what.equals("all") || what.equals("b")) distance();
        if (what.equals("all") || what.equals("a")) subs();
        if (what.equals("all") || what.equals("d")) transition();
        if (what.equals("all") || what.equals("h")) cost();
    }

    static double energyDb(float[] w) {
        double e = 0;
        for (float v : w) e += v * v;
        return e <= 0 ? Double.NEGATIVE_INFINITY : 10 * Math.log10(e);
    }

    // ------------------------------------------------------------------ B: distance
    static void distance() {
        double[] ds = {1, 2, 5, 10, 20, 40, 80, 120, 160, 200};
        Object[][] types = {
            {"line array LX12 x8", new Rig.Arr("A", AudioGroups.MAIN_LEFT, "csc_orvane_lx12", 0, Rig.EAR, 0, 0, 8)},
            {"point source M12", new Rig.Arr("A", AudioGroups.MAIN_LEFT, "csc_aurex_m12", 0, Rig.EAR, 0, 0, 1)},
            {"sub stack S218-F x4", new Rig.Arr("A", AudioGroups.SUBS, "csc_aurex_s218_f", 0, Rig.EAR, 0, 0, 4)},
            {"monitor M15-W", new Rig.Arr("A", AudioGroups.MONITOR_1, "csc_aurex_m15_w", 0, Rig.EAR, 0, 0, 1)}};
        System.out.println("\n[B] level of one array in front of it (weight energy, dB) at distance (blocks)");
        StringBuilder h = new StringBuilder(String.format("%-22s", "array"));
        for (double d : ds) h.append(String.format(Locale.ROOT, "%7.0f", d));
        System.out.println(h + "   | 2->20 | 20->120 | monotone");
        for (Object[] t : types) {
            List<Rig.Arr> scene = List.of((Rig.Arr) t[1]);
            Object st = M.prepare(scene);
            StringBuilder b = new StringBuilder(String.format("%-22s", t[0]));
            double prev = Double.POSITIVE_INFINITY, at2 = 0, at20 = 0, at120 = 0;
            boolean mono = true;
            for (double d : ds) {
                double v = energyDb(M.weights(st, scene, 0, Rig.EAR, d, FACING_STAGE));
                if (v > prev + 1e-6) mono = false;
                prev = v;
                if (d == 2) at2 = v; if (d == 20) at20 = v; if (d == 120) at120 = v;
                b.append(String.format("%7s", Rig.db(v).trim()));
            }
            System.out.println(b + String.format(Locale.ROOT, "   | %5.1f | %6.1f  | %s", at20 - at2, at120 - at20, mono ? "yes" : "NO"));
        }
        // the whole chain: the synthetic master through the mixer (glue, limiter), integrated loudness
        System.out.println("\n[B] whole chain, LX12 x8 hang + 4 x S218-F, music through the mixer (LUFS integrated, 6 s after 1 s)");
        List<Rig.Arr> pa = List.of(new Rig.Arr("A", AudioGroups.MAIN_LEFT, "csc_orvane_lx12", -1, Rig.EAR, 0, 0, 8),
            new Rig.Arr("B", AudioGroups.MAIN_RIGHT, "csc_orvane_lx12", 1, Rig.EAR, 0, 0, 8),
            new Rig.Arr("S", AudioGroups.SUBS, "csc_aurex_s218_f", 0, 0.6, 0, 0, 4));
        Object st = M.prepare(pa);
        short[] music = Rig.music(7);
        StringBuilder row = new StringBuilder("  LUFS:");
        double prev = Double.POSITIVE_INFINITY; boolean mono = true; double first = 0, last = 0;
        for (double d : ds) {
            float[] w = M.weights(st, pa, 0, Rig.EAR, d, FACING_STAGE);
            float[] dl = M.delays(st, pa, 0, Rig.EAR, d);
            ConsoleMixer mx = M.mixer();
            M.configure(mx, st);
            short[] out = Rig.run(mx, music, new Rig.Feed() { public float[] weights(long f) { return w; } public float[] delays(long f) { return dl; } }, true);
            double l = Rig.lufs(out, Rig.RATE);
            if (l > prev + .05) mono = false;
            prev = l;
            if (d == 1) first = l; last = l;
            row.append(String.format(Locale.ROOT, " %.0f:%s", d, Rig.db(l).trim()));
        }
        System.out.println(row + String.format(Locale.ROOT, "\n  1 -> 200 blocks: %.1f dB, never louder further away: %s", last - first, mono ? "yes" : "NO"));
        // the same at 2, 10, 20 blocks, the old curve vs the new one: how much of the change you hear
        double[] near = {2, 5, 10, 20, 40};
        StringBuilder r2 = new StringBuilder("  weights vs heard (dB change from 2 blocks):");
        double w0 = Double.NaN, l0 = Double.NaN;
        for (double d : near) {
            float[] w = M.weights(st, pa, 0, Rig.EAR, d, FACING_STAGE);
            float[] dl = M.delays(st, pa, 0, Rig.EAR, d);
            ConsoleMixer mx = M.mixer();
            M.configure(mx, st);
            short[] out = Rig.run(mx, music, new Rig.Feed() { public float[] weights(long f) { return w; } public float[] delays(long f) { return dl; } }, true);
            double l = Rig.lufs(out, Rig.RATE), e = energyDb(w);
            if (Double.isNaN(w0)) { w0 = e; l0 = l; }
            r2.append(String.format(Locale.ROOT, " %.0f: %+.1f / %+.1f", d, e - w0, l - l0));
        }
        System.out.println(r2 + "   (weights / heard)");
    }

    // ------------------------------------------------------------------ A: subwoofers
    static void subs() {
        String[] models = {"csc_aurex_s218_f", "csc_orvane_sb28", "csc_veyra_b2", "csc_noryx_b1"};
        short[] music = Rig.music(7);
        System.out.println("\n[A] festival PA (2 x 8 LX12) with 2 stacks of 4 subs of each model; music through the mixer");
        System.out.println("  model              | place   | lows 35-90 Hz | mids 300-3k | lows - mids | peak dBFS | clipped | xover");
        for (String model : models) {
            List<Rig.Arr> pa = Rig.festival(model);
            Object st = M.prepare(pa);
            for (double[] p : new double[][] {{0, 25}, {0, 90}, {0, 150}}) {
                float[] w = M.weights(st, pa, p[0], Rig.EAR, p[1], FACING_STAGE);
                float[] dl = M.delays(st, pa, p[0], Rig.EAR, p[1]);
                ConsoleMixer mx = M.mixer();
                float xo = M.crossover(st);
                M.configure(mx, st);
                short[] out = Rig.run(mx, music, new Rig.Feed() { public float[] weights(long f) { return w; } public float[] delays(long f) { return dl; } }, true);
                double lo = Rig.band(out, 35, 90, Rig.RATE), mid = Rig.band(out, 300, 3000, Rig.RATE);
                System.out.println(String.format(Locale.ROOT, "  %-18s | %3.0f blk | %13s | %11s | %+11.1f | %9s | %7d | %s", model.replace("csc_", ""), p[1], Rig.db(lo), Rig.db(mid), lo - mid,
                    Rig.db(Rig.peakDb(out, Rig.RATE)), Rig.clipped(out), Float.isNaN(xo) ? "100 Hz (profile)" : String.format(Locale.ROOT, "%.0f Hz (model)", xo)));
            }
        }
        // distortion: a loud 45 Hz tone (-3 dBFS in the track) heard 2 blocks from the subs, every model
        System.out.println("  45 Hz tone at -3 dBFS in the track, 2 blocks in front of the subs: THD (harmonics 2-5) and clipping"
            + "\n  (concert bass off for this measure: at 50 % it adds 2nd / 3rd harmonics on purpose, about 14 % THD before and after)");
        Rig.concertBass = 0;
        short[] tone = Rig.sine(45, -3, 4);
        for (String model : models) {
            List<Rig.Arr> pa = Rig.festival(model);
            Object st = M.prepare(pa);
            float[] w = M.weights(st, pa, 0, Rig.EAR, 3, FACING_STAGE);
            float[] dl = M.delays(st, pa, 0, Rig.EAR, 3);
            ConsoleMixer mx = M.mixer();
            M.configure(mx, st);
            short[] out = Rig.run(mx, tone, new Rig.Feed() { public float[] weights(long f) { return w; } public float[] delays(long f) { return dl; } }, true);
            System.out.println(String.format(Locale.ROOT, "  %-18s | tone level %s dBFS | THD %5.2f %% | clipped %d", model.replace("csc_", ""), Rig.db(Rig.band(out, 35, 60, Rig.RATE)),
                Rig.thd(out, 45, Rig.RATE), Rig.clipped(out)));
        }
        Rig.concertBass = .5f;
    }

    // ------------------------------------------------------------------ D: from the stage to a delay tower, and on
    static void transition() {
        for (int variant = 0; variant < 2; variant++) {
            List<Rig.Arr> pa = new ArrayList<>(Rig.festival("csc_aurex_s218_f"));
            // variant 0: a stereo tower linked as left / right (auto detection); variant 1: in the Delay group with the delay a sound
            // engineer would set by hand on a real PA (distance to the mains / 343 m/s + 10 ms = 185 ms)
            pa.addAll(variant == 0 ? Rig.tower(60, AudioGroups.MAIN_LEFT, AudioGroups.MAIN_RIGHT) : Rig.tower(60, AudioGroups.DELAY, AudioGroups.DELAY));
            pa.addAll(Rig.tower(120, AudioGroups.MAIN_LEFT, AudioGroups.MAIN_RIGHT));
            Object st = M.prepare(pa);
            System.out.println("\n[D] walk from the stage (z 3) to 140 blocks, towers at z 60 and z 120 — " + (variant == 0 ? "stereo towers (L/R groups)" : "tower 1 in the Delay group, 185 ms set on the console")
                + "\n  classification: " + M.classify(pa));
            System.out.println("    z | total dB | mains dB | tower1 dB | tower2 dB | heard delay tower1 - mains (ms) | tower2 - mains");
            double prevTotal = Double.NaN, maxStep = 0;
            int towerSlot1 = -1, towerSlot2 = -1;
            for (int z = 3; z <= 140; z++) {
                float[] w = M.weights(st, pa, 0, Rig.EAR, z, FACING_STAGE);
                float[] dl = M.delays(st, pa, 0, Rig.EAR, z);
                if (variant == 1 && M.name().equals("before")) dl[AudioGroups.DELAY] = 185;
                // (partLevel below recomputes the levels of a part only; the weights / delays above are kept)
                double tot = energyDb(w);
                if (!Double.isNaN(prevTotal) && Double.isFinite(tot) && Double.isFinite(prevTotal)) maxStep = Math.max(maxStep, Math.abs(tot - prevTotal));
                prevTotal = tot;
                if (z % 10 == 3 || z == 60 || z == 120) {
                    double mains = M.partLevel(st, pa, "LX12", 0, Rig.EAR, z), t1 = M.partLevel(st, pa, "Tower L z60", 0, Rig.EAR, z) + 0 * M.partLevel(st, pa, "Tower R z60", 0, Rig.EAR, z);
                    t1 = Math.hypot(M.partLevel(st, pa, "Tower L z60", 0, Rig.EAR, z), M.partLevel(st, pa, "Tower R z60", 0, Rig.EAR, z));
                    double t2 = Math.hypot(M.partLevel(st, pa, "Tower L z120", 0, Rig.EAR, z), M.partLevel(st, pa, "Tower R z120", 0, Rig.EAR, z));
                    double mainDelay = dl[AudioGroups.MAIN_LEFT];
                    double d1 = (dl.length > AudioGroups.COUNT ? dl[AudioGroups.COUNT] : (variant == 1 ? dl[AudioGroups.DELAY] : dl[AudioGroups.MAIN_LEFT])) - mainDelay;
                    double d2 = (dl.length > AudioGroups.COUNT + 4 ? dl[AudioGroups.COUNT + 4] : dl[AudioGroups.MAIN_LEFT]) - mainDelay;
                    if (variant == 1 && dl.length > AudioGroups.COUNT) d1 = dl[AudioGroups.COUNT + 2] - mainDelay;     // Delay group: mono slot
                    System.out.println(String.format(Locale.ROOT, "  %3d | %8s | %8s | %9s | %9s | %31.1f | %14.1f", z, Rig.db(tot), Rig.db(20 * Math.log10(mains)), Rig.db(20 * Math.log10(t1)),
                        Rig.db(20 * Math.log10(t2)), d1, d2));
                }
            }
            System.out.println(String.format(Locale.ROOT, "  largest level step between two neighbouring blocks: %.2f dB", maxStep));
            // echo: places where a part of the system is heard more than 35 ms after the first and no more than 10 dB under the loudest
            // (heard as a repeat, not as one sound). Parts = groups of slots: 9 console groups / each tower's 4 slots.
            int echo = 0, places = 0; double worstLate = 0;
            for (int z = 3; z <= 140; z++) {
                float[] w = M.weights(st, pa, 0, Rig.EAR, z, FACING_STAGE);
                float[] dl = M.delays(st, pa, 0, Rig.EAR, z);
                if (variant == 1 && M.name().equals("before")) dl[AudioGroups.DELAY] = 185;
                int parts = dl.length;
                double loud = 0, first = Double.MAX_VALUE;
                double[] lv = new double[parts];
                for (int k = 0; k < parts; k++) { lv[k] = Math.hypot(w[2 * k], w[2 * k + 1]); loud = Math.max(loud, lv[k]); }
                for (int k = 0; k < parts; k++) if (lv[k] > loud * .1) first = Math.min(first, dl[k]);
                boolean e = false;
                for (int k = 0; k < parts; k++) if (lv[k] >= loud * Math.pow(10, -10 / 20.0) && dl[k] - first > 35) { e = true; worstLate = Math.max(worstLate, dl[k] - first); }
                places++;
                if (e) echo++;
            }
            System.out.println(String.format(Locale.ROOT, "  places with an audible echo (a part >35 ms late and within 10 dB of the loudest): %d / %d%s", echo, places,
                echo > 0 ? String.format(Locale.ROOT, " (worst %.0f ms late)", worstLate) : ""));
            // the same walk in real time with the music: 5.6 blocks/s (sprinting) from z 5 to z 117, through the mixer
            short[] music = Rig.music(20);
            ConsoleMixer mx = M.mixer();
            M.configure(mx, st);
            final int fv = variant;
            short[] out = Rig.run(mx, music, new Rig.Feed() {
                double z(long f) { return 5 + 5.6 * f / (double) Rig.RATE; }
                public float[] weights(long f) { return M.weights(st, pa, 0, Rig.EAR, z(f), FACING_STAGE); }
                public float[] delays(long f) { float[] d = M.delays(st, pa, 0, Rig.EAR, z(f)); if (fv == 1 && M.name().equals("before")) d[AudioGroups.DELAY] = 185; return d; }
            }, true);
            // momentary loudness every 100 ms
            com.concertstage.audio.core.LevelMeter lm = new com.concertstage.audio.core.LevelMeter(Rig.RATE, 32768);
            double prev = Double.NaN, worst = 0; int step = Rig.RATE / 10, nan = 0;
            for (int at = 0; at + step <= out.length / 2; at += step) {
                short[] part = Arrays.copyOfRange(out, at * 2, (at + step) * 2);
                lm.add(part, step);
                double m = lm.momentary();
                if (!Double.isFinite(m)) { nan++; continue; }
                if (at > Rig.RATE && !Double.isNaN(prev)) worst = Math.max(worst, Math.abs(m - prev));
                prev = m;
            }
            System.out.println(String.format(Locale.ROOT, "  real-time walk (20 s of music, 5.6 blocks/s): largest change of momentary loudness in 100 ms: %.2f dB, clipped samples %d, silent blocks %d",
                worst, Rig.clipped(out), nan));
        }
    }

    // ------------------------------------------------------------------ H: cost
    static List<Rig.Arr> scene(int n) {
        List<Rig.Arr> a = new ArrayList<>(Rig.festival("csc_aurex_s218_f"));
        int t = 0;
        while (a.size() < n) {
            double z = 40 + 30 * (t / 4), x = -45 + 30 * (t % 4);
            a.add(new Rig.Arr("Tower " + t + " a", AudioGroups.MAIN_LEFT, "csc_orvane_lx8", x - 3, 8, z, 0, 6));
            if (a.size() < n) a.add(new Rig.Arr("Tower " + t + " b", AudioGroups.MAIN_RIGHT, "csc_orvane_lx8", x + 3, 8, z, 0, 6));
            t++;
        }
        return a.subList(0, n);
    }

    static void cost() {
        java.lang.management.ThreadMXBean tb = java.lang.management.ManagementFactory.getThreadMXBean();
        com.sun.management.ThreadMXBean sun = (com.sun.management.ThreadMXBean) tb;
        long me = Thread.currentThread().getId();
        System.out.println("\n[H] per tick, one console: level + mix of every array for the local player (" + M.name() + ")");
        System.out.println("  arrays | us / tick | bytes allocated / tick | classification (once per change) | towers found");
        for (int n : new int[] {10, 25, 50, 100, 200}) {
            List<Rig.Arr> sc = scene(n);
            Object st = M.prepare(sc);
            int iters = 20000;
            for (int i = 0; i < iters; i++) { double z = 10 + (i % 200); M.weights(st, sc, 0, Rig.EAR, z, FACING_STAGE); M.delays(st, sc, 0, Rig.EAR, z); }
            long a0 = sun.getThreadAllocatedBytes(me), t0 = System.nanoTime();
            for (int i = 0; i < iters; i++) { double z = 10 + (i % 200); M.weights(st, sc, 0, Rig.EAR, z, FACING_STAGE); M.delays(st, sc, 0, Rig.EAR, z); }
            long t1 = System.nanoTime(), a1 = sun.getThreadAllocatedBytes(me);
            long c0 = System.nanoTime();
            int reps = 200;
            Object last = null;
            for (int i = 0; i < reps; i++) last = M.prepare(sc);
            long c1 = System.nanoTime();
            String cls = M.classify(sc);
            int towers = cls.split("TOWER\\(").length - 1;
            System.out.println(String.format(Locale.ROOT, "  %6d | %9.1f | %22d | %26.1f us | %d", n, (t1 - t0) / 1e3 / iters, (a1 - a0) / iters, (c1 - c0) / 1e3 / reps, towers));
        }
        // the mixer: one second of audio, festival PA + 2 towers heard at 90 blocks
        List<Rig.Arr> pa = new ArrayList<>(Rig.festival("csc_aurex_s218_f"));
        pa.addAll(Rig.tower(60, AudioGroups.MAIN_LEFT, AudioGroups.MAIN_RIGHT));
        pa.addAll(Rig.tower(120, AudioGroups.MAIN_LEFT, AudioGroups.MAIN_RIGHT));
        Object st = M.prepare(pa);
        short[] music = Rig.music(10);
        float[] w = M.weights(st, pa, 0, Rig.EAR, 90, FACING_STAGE);
        float[] dl = M.delays(st, pa, 0, Rig.EAR, 90);
        for (int k = 0; k < 2; k++) {
            long a0 = sun.getThreadAllocatedBytes(me);
            ConsoleMixer mx = M.mixer();
            M.configure(mx, st);
            long t0 = tb.getCurrentThreadCpuTime();
            short[] out = Rig.run(mx, music, new Rig.Feed() { public float[] weights(long f) { return w; } public float[] delays(long f) { return dl; } }, true);
            long t1 = tb.getCurrentThreadCpuTime(), a1 = sun.getThreadAllocatedBytes(me);
            if (k == 1) {
                int active = mx.activeGroups();
                System.out.println(String.format(Locale.ROOT, "  mixer, 10 s of audio at 90 blocks (PA + 2 towers): %.2f ms CPU per second of sound, %d slots active of %d, %.1f MB allocated (mixer + 10 s of output)",
                    (t1 - t0) / 1e6 / 10, active, M.slots(), (a1 - a0) / 1e6));
            }
        }
    }
}
