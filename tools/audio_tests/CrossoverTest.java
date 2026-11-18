import com.concertstage.audio.core.*;
import java.util.Locale;

/**
 * 1.1.1 — Test C (bass): the M12 feed (high pass of the console crossover), the S218-F feed (low pass) and both together, measured
 * with sine tones through the real SpeakerDsp of the mixer (Linkwitz-Riley 24 dB/oct, the crossover of the Sound settings). The sum of
 * a Linkwitz-Riley pair has a flat magnitude (its phase turns like an all-pass): checked at every frequency, for 80, 100 and 120 Hz.
 */
public class CrossoverTest {
    static final int RATE = 48000;

    static double levelDb(double f, int mode, float hz) {
        int n = RATE;
        short[] st = new short[n * 2];
        for (int i = 0; i < n; i++) { short v = (short) Math.round(16000 * Math.sin(2 * Math.PI * f * i / RATE)); st[2 * i] = v; st[2 * i + 1] = v; }
        float[] lo = new float[n], hi = new float[n];
        new SpeakerDsp(RATE).process(st, n, 0, 0, 0, 0, 1, hz, lo);
        new SpeakerDsp(RATE).process(st, n, 0, 0, 0, 0, 2, hz, hi);
        double s = 0;
        for (int i = n / 2; i < n; i++) { double v = mode == 1 ? lo[i] : mode == 2 ? hi[i] : lo[i] + hi[i]; s += v * v; }
        double rms = Math.sqrt(s / (n / 2.0));
        return 20 * Math.log10(rms / (16000 / Math.sqrt(2)));
    }

    public static void main(String[] a) {
        int fail = 0;
        for (float hz : new float[] {80, 100, 120}) {
            System.out.printf(Locale.ROOT, "crossover %.0f Hz:   f (Hz) | S218-F (low pass) | M12 (high pass) | both together%n", hz);
            double worst = 0;
            for (double f : new double[] {30, 50, 70, hz, 140, 200, 400, 1000}) {
                double l = levelDb(f, 1, hz), h = levelDb(f, 2, hz), b = levelDb(f, 3, hz);
                worst = Math.max(worst, Math.abs(b));
                System.out.printf(Locale.ROOT, "                     %6.0f | %8.2f dB       | %8.2f dB     | %6.2f dB%n", f, l, h, b);
            }
            if (Math.abs(levelDb(hz, 1, hz) + 6.02) > 0.3 || Math.abs(levelDb(hz, 2, hz) + 6.02) > 0.3) { fail++; System.out.println("FAIL -6 dB at the crossover point"); }
            if (worst > 0.3) { fail++; System.out.println("FAIL sum not flat within 0.3 dB: " + worst); }
            if (levelDb(hz * 4, 1, hz) > -40 || levelDb(hz / 4, 2, hz) > -40) { fail++; System.out.println("FAIL slopes under 24 dB/oct"); }
        }
        System.out.println(fail == 0 ? "CrossoverTest PASS: -6 dB at the cut on each side, flat sum (+-0.3 dB), 24 dB/oct slopes" : "CrossoverTest FAIL " + fail);
        if (fail > 0) System.exit(1);
    }
}
