import com.concertstage.client.light.FrameBudget;
import com.concertstage.light.core.*;
import com.concertstage.light.dmx.*;
import java.util.*;

/**
 * CSC 1.2.0 rev. 19 — lighting fixes, off game: strobes nobody asked for (state, fades, frame sampling, budgets that changed with
 * the drawing order), gobos in the reduced DMX modes and the migration of older shows, the tone curve of the light on the blocks.
 * Every number printed is measured here. Usage: java LightingFixTest
 */
public class LightingFixTest {
    static int passed, failed;

    static void check(boolean ok, String what) {
        if (ok) passed++; else { failed++; System.out.println("  FAIL " + what); }
    }

    public static void main(String[] a) {
        strobeState();
        strobeFades();
        strobeFrames();
        budgets();
        gobosInReducedModes();
        migration();
        toneCurve();
        motors();
        System.out.println("passed " + passed + ", failed " + failed);
        if (failed > 0) System.exit(1);
    }

    // ------------------------------------------------------------------ helpers (same commands as the game)
    static Show rig(String... types) {
        Show s = new Show();
        s.id = "rev19";
        Live l = new Live();
        int fid = 1;
        for (String t : types) {
            Commands.apply(s, l, Commands.op(Commands.PATCH_ADD).varInt(fid).str(t).str("").sInt(fid * 2).sInt(70).sInt(-20).f32(0).varInt(1).toBytes(), 0, null);
            fid++;
        }
        return s;
    }

    static int[] all(Show s) { return s.patch.stream().mapToInt(p -> p.fid).toArray(); }

    static byte[] set(int[] fids, Attr a, float v) {
        Bytes.Writer w = Commands.op(Commands.P_SET);
        Commands.fids(w, fids);
        return w.varInt(a.ordinal()).unit(v).toBytes();
    }

    static byte[] store(int list, float number, float fade) {
        return Commands.op(Commands.STORE_CUE).varInt(list).f32(number).str("Cue " + number).f32(fade).varInt(0).varInt(0).toBytes();
    }

    static byte[] clearProg() {
        Bytes.Writer w = Commands.op(Commands.P_CLEAR);
        Commands.fids(w, new int[0]);
        return w.bool(true).toBytes();
    }

    static String[] every() { return FixtureProfile.all().stream().map(p -> p.id).toArray(String[]::new); }

    // ------------------------------------------------------------------ 8.1 / 8.3: the state of the strobe
    static void strobeState() {
        System.out.println("Strobe state (every fixture)");
        Show s = rig(every());
        Live l = new Live();
        Engine e = new Engine();
        Map<Integer, Engine.Output> o = e.evaluate(s, l, 0, Engine.Music.NONE);
        boolean homeOff = true;
        for (Show.Patch p : s.patch) homeOff &= o.get(p.fid).strobeHz == 0f;
        check(homeOff, "at rest (new show, after patch) no fixture strobes");
        // dimmer 100 %, colours, gobos, zoom, prism changed: never a strobe
        int[] f = all(s);
        Commands.apply(s, l, set(f, Attr.DIMMER, 1f), 0, null);
        boolean off = true;
        Random r = new Random(1);
        for (int k = 0; k < 200; k++) {
            Attr at = new Attr[] {Attr.RED, Attr.GREEN, Attr.BLUE, Attr.GOBO, Attr.ZOOM, Attr.PRISM, Attr.CTO, Attr.WHITE, Attr.GOBO_ROT, Attr.IRIS}[k % 10];
            Commands.apply(s, l, set(f, at, r.nextFloat()), k * 10, null);
            o = e.evaluate(s, l, k * 10, Engine.Music.NONE);
            for (Show.Patch p : s.patch) off &= o.get(p.fid).strobeHz == 0f;
        }
        check(off, "dimmer 100 % and 200 changes of colour / gobo / zoom / prism: no strobe on any of the 17 fixtures");
        // DMX: every mode of every fixture, the desk at its default values (what a console sends at rest) -> no strobe
        int modes = 0, bad = 0;
        for (FixtureProfile p : FixtureProfile.all())
            for (int m : new int[] {DmxMode.FULL, DmxMode.COMPACT, DmxMode.ULTRA, DmxMode.ULTRA_V1}) {
                DmxMode d = DmxMode.of(p, m);
                byte[] u = new byte[512];
                for (int i = 0; i < d.channels.length; i++) u[i] = (byte) d.channels[i].def();
                for (int i = 0; i < d.channels.length; i++) if (d.channels[i].attr() == Attr.DIMMER && d.channels[i].kind() == DmxMode.Kind.ATTR) u[i] = (byte) 255;
                DmxData dmx = new DmxData();
                dmx.set(1, u);
                Show.Patch pt = new Show.Patch();
                pt.type = p.id; pt.universe = 1; pt.address = 1; pt.dmxMode = m;
                float[] v = new float[Attr.COUNT];
                for (Attr at : Attr.ALL) v[at.ordinal()] = p.home(at);
                DmxDecode.decode(pt, dmx, v, new float[p.zones * 4]);
                modes++;
                if (p.strobeHz(v[Attr.STROBE.ordinal()]) != 0f) { bad++; System.out.println("    strobes at default DMX: " + p.id + " " + d.name()); }
            }
        check(bad == 0, "DMX default values of the " + modes + " modes, dimmer full: shutter open (no strobe)");
        // the neutral DMX values: 0..7 open, 8 = open too (the decoder starts the rate above), 9 = 1 Hz
        FixtureProfile v = FixtureProfile.VORTEX_X1;
        check(v.strobeHz(DmxMode.decode(v, Attr.STROBE, 7, false)) == 0 && v.strobeHz(DmxMode.decode(v, Attr.STROBE, 8, false)) == 0
            && v.strobeHz(DmxMode.decode(v, Attr.STROBE, 9, false)) > 0.9f, "strobe channel: 0-8 open, 9-255 strobe");
        float[] fx = new float[Attr.COUNT];
        DmxMode.shutterFx(v, 0, fx);
        check(fx[Attr.STROBE.ordinal()] == 0 && fx[Attr.IRIS.ordinal()] == 1f, "Compact shutter channel at 0: open, iris open");
    }

    // ------------------------------------------------------------------ 8.5: strobe through cue fades
    static void strobeFades() {
        System.out.println("Strobe and cue fades");
        Show s = rig(FixtureProfile.VORTEX_X1.id, FixtureProfile.PULSESTRIKE_M.id, FixtureProfile.FLASHCORE_FX.id);
        Live l = new Live();
        Engine e = new Engine();
        int[] f = all(s);
        Commands.apply(s, l, set(f, Attr.DIMMER, 1f), 0, null);
        Commands.apply(s, l, set(f, Attr.STROBE, 0.5f), 0, null);
        Commands.apply(s, l, store(1, 1, 3), 0, null);              // cue 1: strobe 50 %
        Commands.apply(s, l, clearProg(), 0, null);
        Commands.apply(s, l, set(f, Attr.DIMMER, 1f), 0, null);
        Commands.apply(s, l, set(f, Attr.STROBE, 0f), 0, null);
        Commands.apply(s, l, store(1, 2, 3), 0, null);              // cue 2: shutter open, 3 s fade
        Commands.apply(s, l, clearProg(), 0, null);
        Commands.apply(s, l, Commands.op(Commands.GO).varInt(1).toBytes(), 0, null);
        Map<Integer, Engine.Output> o = e.evaluate(s, l, 5000, Engine.Music.NONE);
        float on = o.get(1).strobeHz;
        check(on > 5, "cue 1 strobes (" + on + " Hz)");
        Commands.apply(s, l, Commands.op(Commands.GO).varInt(1).toBytes(), 6000, null);
        float worst = 0;
        for (int t = 6050; t <= 9500; t += 50) {
            o = e.evaluate(s, l, t, Engine.Music.NONE);
            for (int fid : f) worst = Math.max(worst, o.get(fid).strobeHz);
        }
        check(worst == 0f, "cue 2 (open, 3 s fade): the strobe stops at the GO, no slower flashes during the fade (worst " + worst + " Hz)");
        // the other way: from open to strobe, the rate is right at once (no seconds of 1 Hz)
        Commands.apply(s, l, Commands.op(Commands.GOTO).varInt(1).varInt(0).toBytes(), 10000, null);
        o = e.evaluate(s, l, 10050, Engine.Music.NONE);
        check(Math.abs(o.get(1).strobeHz - on) < 0.01f, "fade into the strobe cue: its rate from the start (" + o.get(1).strobeHz + " Hz)");
        // safety: no strobe master
        l.noStrobe = true;
        o = e.evaluate(s, l, 12000, Engine.Music.NONE);
        check(o.get(1).strobeHz == 0f, "\"no strobe\" safety: off");
    }

    // ------------------------------------------------------------------ 8.4 / 8.5: what a frame shows
    static void strobeFrames() {
        System.out.println("Strobe as the frames see it (10 s): flashes of 45 ms, sampled at the frame time");
        System.out.println("   rate | fps | expected | seen | irregular gaps");
        boolean ok = true;
        for (double fps : new double[] {60, 144, 45}) {
            for (float hz : new float[] {1, 2, 5, 10, 15, 20, 25}) {
                int[] seen = flashes(hz, fps, 0);
                int expected = (int) Math.round(hz * 10);
                System.out.println(String.format(Locale.ROOT, "   %4.0f | %3.0f | %8d | %4d | %4d", hz, fps, expected, seen[0], seen[1]));
                if (hz <= fps / 3) ok &= Math.abs(seen[0] - expected) <= 1 && seen[1] == 0;
            }
        }
        check(ok, "every flash seen once, at regular intervals, up to a third of the frame rate (the frame sampling is not a cause)");
        // strobe off: the intensity is the dimmer, frame after frame
        boolean steady = true;
        Random r = new Random(3);
        double t = 0;
        for (int i = 0; i < 100000; i++) {
            t += 1 / (30 + 120 * r.nextDouble());
            steady &= BeamOptics.strobeIntensity(0.73f, 0f, t) == 0.73f;
        }
        check(steady, "strobe off: 100 000 frames at random frame rates, intensity always the dimmer");
    }

    /** {flashes seen, gaps between flashes that differ from the usual one by more than a frame}; mode 0 = sampled, 1 = windowed. */
    static int[] flashes(float hz, double fps, int mode) {
        Random r = new Random(11);
        double t = 0;
        boolean prev = false;
        int count = 0;
        List<Double> onsets = new ArrayList<>();
        while (t < 10) {
            double dt = 1 / fps * (fps == 45 ? 0.85 + 0.3 * r.nextDouble() : 1);   // 45 fps: an uneven frame rate
            t += dt;
            boolean on = BeamOptics.strobeIntensity(1f, hz, t) > 0;
            if (on && !prev) { count++; onsets.add(t); }
            prev = on;
        }
        int irregular = 0;
        double period = 1 / hz;
        for (int i = 1; i < onsets.size(); i++) if (Math.abs(onsets.get(i) - onsets.get(i - 1) - period) > 1.05 / fps * (fps == 45 ? 1.2 : 1)) irregular++;
        return new int[] {count, irregular};
    }

    // ------------------------------------------------------------------ 8.4: the budgets and the drawing order
    static void budgets() {
        System.out.println("Budgets of the frame (100 lit fixtures, 40 places, drawing order shuffled every frame)");
        int n = 100, cap = 40, frames = 600;
        Random r = new Random(5);
        float[] base = new float[n];
        for (int i = 0; i < n; i++) base[i] = 0.2f + r.nextFloat();
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        FrameBudget b = new FrameBudget("test");
        boolean[] before = new boolean[n], after = new boolean[n];
        long flipsBefore = 0, flipsAfter = 0;
        long t0 = System.nanoTime();
        for (int fr = 0; fr < frames; fr++) {
            Collections.shuffle(Arrays.asList(order), r);
            b.frame(cap);
            int used = 0;
            boolean[] nb = new boolean[n], na = new boolean[n];
            for (int k : order) {
                float score = base[k] * (0.97f + 0.06f * r.nextFloat());      // the camera moves a little
                nb[k] = used++ < cap;                                         // before: first come, first served
                na[k] = b.admit(k, score);
            }
            if (fr > 2) for (int i = 0; i < n; i++) { if (nb[i] != before[i]) flipsBefore++; if (na[i] != after[i]) flipsAfter++; }
            before = nb; after = na;
        }
        double us = (System.nanoTime() - t0) / 1e3 / frames;
        System.out.println(String.format(Locale.ROOT, "   fixtures changing look per frame: before %.1f, after %.3f (cost %.1f us per frame for 100 fixtures)",
            flipsBefore / (double) (frames - 3), flipsAfter / (double) (frames - 3), us));
        check(flipsAfter == 0, "after: no fixture changes look while the scene does not really change");
        int in = 0;
        for (boolean x : after) if (x) in++;
        check(in == cap, "after: the budget is used in full (" + in + " / " + cap + ")");
        // the best are in: the 40 highest scores
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        Arrays.sort(idx, (x, y) -> Float.compare(base[y], base[x]));
        int top = 0;
        for (int i = 0; i < cap; i++) if (after[idx[i]]) top++;
        check(top >= cap - 3, "after: the places go to the fixtures that show most (" + top + " of the 40 best)");
        // a fixture clearly better than one inside takes its place within a frame or two
        FrameBudget c = new FrameBudget("swap");
        for (int fr = 0; fr < 3; fr++) { c.frame(1); c.admit("a", 1f); c.admit("b", 0.5f); }
        boolean aIn = false, bIn = false;
        for (int fr = 0; fr < 3; fr++) { c.frame(1); aIn = c.admit("a", 0.3f); bIn = c.admit("b", 1f); }
        check(!aIn && bIn, "a fixture that becomes much brighter takes the place");
        for (int nn : new int[] {200, 1000}) {
            FrameBudget d = new FrameBudget("cost");
            long s0 = System.nanoTime();
            for (int fr = 0; fr < 200; fr++) { d.frame(64); for (int k = 0; k < nn; k++) d.admit(k, (k * 7919 % 1000) / 1000f); }
            System.out.println(String.format(Locale.ROOT, "   cost with %d fixtures asking: %.1f us per frame", nn, (System.nanoTime() - s0) / 1e3 / 200));
        }
    }

    // ------------------------------------------------------------------ 9: gobos in the reduced modes
    static void gobosInReducedModes() {
        System.out.println("Gobos in every DMX mode");
        int fixtures = 0;
        for (FixtureProfile p : FixtureProfile.all()) {
            if (!DmxMode.hasGoboWheel(p)) continue;
            fixtures++;
            for (int m : new int[] {DmxMode.FULL, DmxMode.COMPACT, DmxMode.ULTRA}) {
                DmxMode d = DmxMode.of(p, m);
                check(d.channelsOf(Attr.GOBO).length > 0, p.name + " " + d.name() + ": a gobo channel (" + d.footprint() + " channels)");
                check(d.channelsOf(Attr.STROBE).length > 0, p.name + " " + d.name() + ": a strobe / shutter channel");
                for (int wheel = 1; wheel <= (p.has(Attr.GOBO_2) ? 2 : 1); wheel++)
                    for (int g = 0; g <= p.gobos; g++) {
                        float[] v = decodeGobo(p, m, wheel, g);
                        Attr ga = wheel == 1 ? Attr.GOBO : Attr.GOBO_2;
                        float want = g / (float) p.gobos;
                        boolean exact = Math.abs(v[ga.ordinal()] - want) < 1e-4f;
                        if (m == DmxMode.FULL && wheel == 2 && !p.has(Attr.GOBO_2)) continue;
                        check(exact, p.name + " " + d.name() + " wheel " + wheel + " gobo " + g + ": " + v[ga.ordinal()] + " (want " + want + ")");
                    }
            }
            // Compact / Ultra: the same gobo picture as Full (same value -> same slot of the renderer)
            for (int g = 1; g <= p.gobos; g++) {
                float full = decodeGobo(p, DmxMode.FULL, 1, g)[Attr.GOBO.ordinal()], compact = decodeGobo(p, DmxMode.COMPACT, 1, g)[Attr.GOBO.ordinal()],
                    ultra = decodeGobo(p, DmxMode.ULTRA, 1, g)[Attr.GOBO.ordinal()];
                int slotF = Math.round(full * p.gobos), slotC = Math.round(compact * p.gobos), slotU = Math.round(ultra * p.gobos);
                check(slotF == g && slotC == g && slotU == g, p.name + " gobo " + g + ": the same picture in the three modes");
            }
            System.out.println("   " + p.name + ": Full " + DmxMode.of(p, DmxMode.FULL).footprint() + " ch, Compact " + DmxMode.of(p, DmxMode.COMPACT).footprint()
                + " ch, Ultra " + DmxMode.of(p, DmxMode.ULTRA).footprint() + " ch (was 4, no gobo) — " + String.join(" | ", Arrays.stream(DmxMode.of(p, DmxMode.ULTRA).channels).map(DmxMode.Channel::name).toList()));
        }
        check(fixtures == 4, "the four fixtures with gobos (VORTEX X1, PHANTOM S1, PROFILE P1, RAZOR B1) checked");
        // the fixtures without gobos: their Ultra did not change
        boolean same = true;
        // rev. 24: except the blinders, whose Ultra Compact became one channel (dimmer, 3200 K), their former one kept as ULTRA_V1
        for (FixtureProfile p : FixtureProfile.all()) if (!DmxMode.hasGoboWheel(p) && !DmxMode.tungsten(p)) same &= DmxMode.of(p, DmxMode.ULTRA).footprint() == DmxMode.of(p, DmxMode.ULTRA_V1).footprint();
        check(same, "fixtures without gobos (blinders apart, rev. 24): Ultra Compact unchanged");
        // the internal desk: the programmer reaches the gobos of every fixture whatever its DMX mode (the mode is only the DMX input)
        Show s = rig(FixtureProfile.VORTEX_X1.id, FixtureProfile.PHANTOM_S1.id);
        for (Show.Patch p : s.patch) p.dmxMode = DmxMode.ULTRA;
        Live l = new Live();
        Engine e = new Engine();
        Commands.apply(s, l, set(all(s), Attr.DIMMER, 1f), 0, null);
        Commands.apply(s, l, set(all(s), Attr.GOBO, 3 / 8f), 0, null);
        Map<Integer, Engine.Output> o = e.evaluate(s, l, 0, Engine.Music.NONE);
        check(Math.round(o.get(1).get(Attr.GOBO) * 8) == 3 && Math.round(o.get(2).get(Attr.GOBO) * 8) == 3, "desk programmer: gobo 3 on fixtures patched in Ultra Compact");
    }

    /** Decoded values when the gobo channel of mode {@code m} sends gobo {@code g} of {@code wheel} (rest at default). */
    static float[] decodeGobo(FixtureProfile p, int m, int wheel, int g) {
        DmxMode d = DmxMode.of(p, m);
        byte[] u = new byte[512];
        for (int i = 0; i < d.channels.length; i++) {
            DmxMode.Channel c = d.channels[i];
            int val = c.def();
            if (c.kind() == DmxMode.Kind.GOBO_FX && (wheel == 1 || p.has(Attr.GOBO_2))) val = DmxMode.goboValue(p, wheel, g, 0f);
            if (c.kind() == DmxMode.Kind.ATTR && c.attr() == (wheel == 1 ? Attr.GOBO : Attr.GOBO_2)) val = DmxMode.encode(p, c.attr(), g / (float) p.gobos, false);
            u[i] = (byte) val;
        }
        DmxData dmx = new DmxData();
        dmx.set(1, u);
        Show.Patch pt = new Show.Patch();
        pt.type = p.id; pt.universe = 1; pt.address = 1; pt.dmxMode = m;
        float[] v = new float[Attr.COUNT];
        for (Attr at : Attr.ALL) v[at.ordinal()] = p.home(at);
        DmxDecode.decode(pt, dmx, v, new float[p.zones * 4]);
        return v;
    }

    // ------------------------------------------------------------------ 9.4: older shows
    static void migration() {
        System.out.println("Migration of shows saved before rev. 19");
        Show s = rig(FixtureProfile.VORTEX_X1.id, FixtureProfile.LUMORA_W7.id, FixtureProfile.RAZOR_B1.id);
        for (Show.Patch p : s.patch) { p.dmxMode = DmxMode.ULTRA; p.universe = 1; }
        s.patch.get(0).address = 1; s.patch.get(1).address = 5; s.patch.get(2).address = 9;   // packed as the old 4-channel Ultra allowed
        byte[] now = s.toBytes();
        Show same = Show.fromBytes(now);
        check(same.patch.get(0).dmxMode == DmxMode.ULTRA, "a show saved now keeps the new Ultra Compact");
        // the same show as an older version wrote it: format byte 3 (after the 4 bytes of the magic)
        byte[] old = now.clone();
        check(old[4] == Show.FORMAT, "format byte found (" + old[4] + ")");
        old[4] = 3;
        Show r = Show.fromBytes(old);
        check(r != null && r.patch.get(0).dmxMode == DmxMode.ULTRA_V1 && r.patch.get(2).dmxMode == DmxMode.ULTRA_V1,
            "older show: the VORTEX and the RAZOR keep their 4-channel Ultra (their desk's layout)");
        check(r.patch.get(1).dmxMode == DmxMode.ULTRA, "older show: the W7 (no gobo) stays in Ultra, unchanged");
        check(r.patch.get(0).footprint() == 4 && com.concertstage.light.dmx.AutoPatch.conflicts(r).isEmpty(), "no address moved, no overlap");
        // the auto patch moves them to the current modes
        com.concertstage.light.dmx.AutoPatch.Plan plan = com.concertstage.light.dmx.AutoPatch.plan(r, 1, DmxMode.ULTRA, com.concertstage.light.dmx.AutoPatch.SHARE_NONE);
        boolean upgraded = true;
        for (com.concertstage.light.dmx.AutoPatch.Entry en : plan.entries()) upgraded &= en.mode() != DmxMode.ULTRA_V1;
        check(upgraded, "auto patch: no fixture left in the old 4-channel mode (" + plan.report() + ")");
    }

    // ------------------------------------------------------------------ 2: the tone curve of the light on the blocks (the shader, on the CPU)
    static float[] oldComposite(float[] scene, float[] L) {
        float[] c = new float[3];
        for (int i = 0; i < 3; i++) c[i] = Math.min(1f, scene[i] * (1f + 3f * (float) Math.tanh(L[i] / 2.5)));      // the frame buffer clips at 1
        return c;
    }

    static float[] newComposite(float[] scene, float[] L) {
        float[] c = new float[3];
        float peak = 0;
        for (int i = 0; i < 3; i++) { c[i] = scene[i] * (1f + 2.2f * (float) Math.tanh(L[i] / 1.83)); peak = Math.max(peak, c[i]); }
        float k = 0.65f;
        if (peak > k) {
            float x = (peak - k) / (1 - k);
            float np = k + (1 - k) * x / (1 + x);
            for (int i = 0; i < 3; i++) c[i] *= np / peak;
        }
        return c;
    }

    static void toneCurve() {
        System.out.println("Light on the blocks: tone curve (the shader's maths)");
        // a textured floor: two neighbouring texels 0.55 and 0.70 (light concrete), under 1 to 8 white fixtures
        System.out.println("   fixtures | before: texels (contrast) | after: texels (contrast)");
        boolean detail = true, noClip = true, mono = true;
        float prev = 0;
        for (int n = 1; n <= 8; n++) {
            float L = 0.9f * n;
            float[] a0 = oldComposite(new float[] {.55f, .55f, .55f}, new float[] {L, L, L}), b0 = oldComposite(new float[] {.70f, .70f, .70f}, new float[] {L, L, L});
            float[] a1 = newComposite(new float[] {.55f, .55f, .55f}, new float[] {L, L, L}), b1 = newComposite(new float[] {.70f, .70f, .70f}, new float[] {L, L, L});
            System.out.println(String.format(Locale.ROOT, "   %8d | %.2f / %.2f (%4.0f %%)       | %.2f / %.2f (%4.1f %%)", n, a0[0], b0[0], 100 * (b0[0] - a0[0]) / Math.max(1e-4, b0[0]),
                a1[0], b1[0], 100 * (b1[0] - a1[0]) / b1[0]));
            detail &= b1[0] - a1[0] > 0.005f;
            noClip &= b1[0] < 1f;
            mono &= a1[0] >= prev;
            prev = a1[0];
        }
        check(detail, "after: the two texels stay different under 8 fixtures (the texture is still visible)");
        check(noClip, "after: never clipped to white");
        check(mono, "after: more fixtures = brighter (monotonic)");
        // one fixture on a dark surface (below the knee): within 3 % of before
        float[] dim = newComposite(new float[] {.3f, .2f, .1f}, new float[] {.4f, .4f, .4f}), dimOld = oldComposite(new float[] {.3f, .2f, .1f}, new float[] {.4f, .4f, .4f});
        check(Math.abs(dim[0] / dimOld[0] - 1) < .03f, String.format(Locale.ROOT, "one fixture on a dark surface: as before (%.3f / %.3f)", dim[0], dimOld[0]));
        // hue kept: red light on a white wall, many fixtures
        float[] red = newComposite(new float[] {.8f, .8f, .8f}, new float[] {6f, .5f, .5f});
        float[] redOld = oldComposite(new float[] {.8f, .8f, .8f}, new float[] {6f, .5f, .5f});
        System.out.println(String.format(Locale.ROOT, "   strong red on a white wall: before %.2f %.2f %.2f, after %.2f %.2f %.2f", redOld[0], redOld[1], redOld[2], red[0], red[1], red[2]));
        check(red[0] / red[1] >= redOld[0] / redOld[1] - 1e-4f, "strong red: at least as saturated as before, not washed to white");
    }

    // ------------------------------------------------------------------ 6 / 7: motors of the families
    static void motors() {
        System.out.println("Motor speeds (pan / tilt, degrees per second)");
        Set<String> distinct = new HashSet<>();
        for (FixtureProfile p : FixtureProfile.all()) {
            if (p.panRange <= 0 && p.tiltRange <= 0) continue;
            distinct.add(p.panSpeed + "/" + p.tiltSpeed);
            System.out.println(String.format(Locale.ROOT, "   %-18s %-24s %5.0f / %5.0f", p.name, p.function, p.panSpeed, p.tiltSpeed));
        }
        check(FixtureProfile.RAZOR_B1.panSpeed > FixtureProfile.PHANTOM_S1.panSpeed && FixtureProfile.PHANTOM_S1.panSpeed > FixtureProfile.PROFILE_P1.panSpeed,
            "beam faster than spot, spot faster than profile");
        check(distinct.size() >= 5, "families move differently (" + distinct.size() + " speed pairs)");
    }
}
