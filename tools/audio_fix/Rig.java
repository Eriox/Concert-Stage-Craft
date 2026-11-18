import com.concertstage.audio.AudioGroups;
import com.concertstage.audio.SpeakerModels;
import com.concertstage.audio.core.ConsoleMixer;
import com.concertstage.audio.core.LevelMeter;
import com.concertstage.audio.core.SoundProfile;
import com.concertstage.audio.core.SpeakerDsp;
import java.util.ArrayList;
import java.util.List;

/**
 * 1.1.1 audio fix — shared test rig (compiled against the code before AND after the fix, so it only uses classes both have):
 * scenes of arrays, the synthetic master of LevelChainTest, the mixer run in sound-thread callbacks with the game's default
 * listening settings (concert realism on: glue 50 %, concert bass 50 %; limiter on; output gain x1), and band / loudness measures.
 */
public final class Rig {
    public static final int RATE = 48000;
    public static final double EAR = 1.7;

    /** One array: console group, model, acoustic centre, yaw (0 = facing +z), number of cabinets. */
    public record Arr(String name, int group, String model, double x, double y, double z, double yaw, int cabinets) {
        public SpeakerModels.Model m() { return SpeakerModels.get(model); }
        public double length() { return m().height() * cabinets; }
    }

    // ------------------------------------------------------------------ scenes
    /** A festival PA: two hangs of 8 ORVANE LX12, two stacks of 4 subs, 4 monitors, 2 front fills. */
    public static List<Arr> festival(String subModel) {
        List<Arr> a = new ArrayList<>();
        a.add(new Arr("LX12 L", AudioGroups.MAIN_LEFT, "csc_orvane_lx12", -12, 9, 0, 0, 8));
        a.add(new Arr("LX12 R", AudioGroups.MAIN_RIGHT, "csc_orvane_lx12", 12, 9, 0, 0, 8));
        a.add(new Arr("Subs L", AudioGroups.SUBS, subModel, -6, 1.2, 1, 0, 4));
        a.add(new Arr("Subs R", AudioGroups.SUBS, subModel, 6, 1.2, 1, 0, 4));
        for (int i = 0; i < 4; i++) a.add(new Arr("M15 " + i, AudioGroups.MONITOR_1, "csc_aurex_m15_w", -6 + i * 4, 1.2, -2, 180, 1));
        a.add(new Arr("Fill L", AudioGroups.FRONT_FILL, "csc_noryx_a1", -3, 1.2, 2, 0, 2));
        a.add(new Arr("Fill R", AudioGroups.FRONT_FILL, "csc_noryx_a1", 3, 1.2, 2, 0, 2));
        return a;
    }

    /** A delay tower: two hangs of 6 ORVANE LX8 (left / right groups, as a stereo tower is linked), at z, facing +z. */
    public static List<Arr> tower(double z, int leftGroup, int rightGroup) {
        return List.of(new Arr("Tower L z" + (int) z, leftGroup, "csc_orvane_lx8", -5, 8, z, 0, 6),
            new Arr("Tower R z" + (int) z, rightGroup, "csc_orvane_lx8", 5, 8, z, 0, 6));
    }

    // ------------------------------------------------------------------ signals
    public static short[] music(int seconds) { return LevelChainTest.music(seconds); }

    public static short[] sine(double hz, double dbfs, int seconds) {
        int n = RATE * seconds;
        short[] s = new short[n * 2];
        double a = 32767 * Math.pow(10, dbfs / 20);
        for (int i = 0; i < n; i++) { short v = (short) Math.round(a * Math.sin(2 * Math.PI * hz * i / RATE)); s[2 * i] = v; s[2 * i + 1] = v; }
        return s;
    }

    /** A weights / delays provider for the block starting at {@code frame}. */
    public interface Feed { float[] weights(long frame); default float[] delays(long frame) { return null; } default float crossover(long frame) { return Float.NaN; } }

    /** The mixer with the game's default listening settings. */
    /** Concert bass of the listening settings (50 % by default: it adds 2nd / 3rd harmonics of the lows on purpose). */
    public static float concertBass = .5f;

    public static void defaults(ConsoleMixer m) {
        m.smoothing(60);
        m.environment(0, .5f, .5f);
        m.concert(true, 0, 0, .5f, concertBass, 0);
    }

    /** Runs the input through the mixer in callbacks of 2048 frames (the sound thread's), weights / delays read per callback. */
    public static short[] run(ConsoleMixer mixer, short[] in, Feed feed, boolean subs) {
        defaults(mixer);
        int frames = in.length / 2, block = 2048;
        short[] out = new short[in.length], ib = new short[block * 2], ob = new short[block * 2];
        for (int at = 0; at < frames; at += block) {
            int n = Math.min(block, frames - at);
            System.arraycopy(in, at * 2, ib, 0, n * 2);
            float[] w = feed.weights(at), d = feed.delays(at);
            if (d != null) mixer.groupDelays(d);
            mixer.process(ib, n, w, 0, 0, 0, subs, 1f, SoundProfile.DEFAULT, 20000, ob);
            System.arraycopy(ob, 0, out, at * 2, n * 2);
        }
        return out;
    }

    // ------------------------------------------------------------------ measures
    public static double lufs(short[] s, int fromFrame) {
        LevelMeter m = new LevelMeter(RATE, 32768, true);
        short[] part = java.util.Arrays.copyOfRange(s, fromFrame * 2, s.length);
        m.add(part, part.length / 2);
        return m.integrated();
    }

    /** RMS (dBFS) of a band of the output (4th-order band pass), from {@code fromFrame}. */
    public static double band(short[] s, double lo, double hi, int fromFrame) {
        SpeakerDsp.Biquad h1 = new SpeakerDsp.Biquad(), h2 = new SpeakerDsp.Biquad(), l1 = new SpeakerDsp.Biquad(), l2 = new SpeakerDsp.Biquad();
        h1.highPass(RATE, lo, Math.sqrt(.5)); h2.highPass(RATE, lo, Math.sqrt(.5)); l1.lowPass(RATE, hi, Math.sqrt(.5)); l2.lowPass(RATE, hi, Math.sqrt(.5));
        double sum = 0; long n = 0;
        for (int i = 0; i < s.length / 2; i++) {
            double x = (s[2 * i] + s[2 * i + 1]) * .5;
            double y = l2.run(l1.run(h2.run(h1.run(x))));
            if (i >= fromFrame) { sum += y * y; n++; }
        }
        return 10 * Math.log10(Math.max(1e-12, sum / Math.max(1, n)) / (32768.0 * 32768.0));
    }

    public static int clipped(short[] s) {
        int c = 0;
        for (short v : s) if (v >= 32767 || v <= -32768) c++;
        return c;
    }

    public static double peakDb(short[] s, int fromFrame) {
        int p = 0;
        for (int i = fromFrame * 2; i < s.length; i++) p = Math.max(p, Math.abs(s[i]));
        return 20 * Math.log10(Math.max(1, p) / 32768.0);
    }

    /** Total harmonic distortion (%) of a sine at {@code hz} (harmonics 2..5, Goertzel), from {@code fromFrame}. */
    public static double thd(short[] s, double hz, int fromFrame) {
        double f = goertzel(s, hz, fromFrame), h = 0;
        for (int k = 2; k <= 5; k++) { double g = goertzel(s, hz * k, fromFrame); h += g * g; }
        return 100 * Math.sqrt(h) / Math.max(1e-9, f);
    }

    private static double goertzel(short[] s, double hz, int from) {
        int n = s.length / 2 - from;
        double w = 2 * Math.PI * hz / RATE, c = 2 * Math.cos(w), s1 = 0, s2 = 0;
        for (int i = 0; i < n; i++) {
            double x = (s[2 * (from + i)] + s[2 * (from + i) + 1]) * .5 * (0.5 - 0.5 * Math.cos(2 * Math.PI * i / (n - 1)));
            double s0 = x + c * s1 - s2; s2 = s1; s1 = s0;
        }
        return Math.sqrt(s1 * s1 + s2 * s2 - c * s1 * s2);
    }

    public static String db(double v) { return Double.isFinite(v) ? String.format(java.util.Locale.ROOT, "%6.1f", v) : "  -inf"; }
}
