import com.concertstage.audio.core.*;
import java.util.Locale;

/**
 * 1.1.1 — Test B (distortion): a 60 Hz tone at -1 dBFS with the console's low EQ at +12 dB, played by the main group at a quarter of
 * its level (weight 0.25, limiter off, gain 1): the output is far below full scale, so it must stay a clean sine. In 1.1.0 the feed of
 * each group was rounded and clipped to 16 bits before its gain: the boosted tone was clipped there. Prints the tone-to-rest ratio of
 * the output (THD + N). Runs against any version of the core (only the public mixer API).
 */
public class HeadroomTest {
    public static void main(String[] a) {
        int rate = 48000, n = rate * 2, groups = 9;
        short[] in = new short[n * 2], out = new short[n * 2];
        double amp = 32767 * Math.pow(10, -1 / 20.0), f = 60;
        for (int i = 0; i < n; i++) { short v = (short) Math.round(amp * Math.sin(2 * Math.PI * f * i / rate)); in[2 * i] = v; in[2 * i + 1] = v; }
        float[] w = new float[groups * 2];
        w[0] = .25f;                                                       // main left, left channel
        ConsoleMixer m = new ConsoleMixer(rate, groups);
        SoundProfile p = SoundProfile.DEFAULT.with(8, 0);                 // limiter off
        int block = 2048;
        short[] ib = new short[block * 2], ob = new short[block * 2];
        for (int at = 0; at < n; at += block) {
            int k = Math.min(block, n - at);
            System.arraycopy(in, at * 2, ib, 0, k * 2);
            m.process(ib, k, w, 12, 0, 0, false, 1f, p, 20000, ob);
            System.arraycopy(ob, at * 0, out, at * 2, k * 2);
        }
        // fit of the 60 Hz tone on the second half, left channel
        double cc = 0, ss = 0, cs = 0, yc = 0, ys = 0, peak = 0;
        for (int i = n / 2; i < n; i++) {
            double c = Math.cos(2 * Math.PI * f * i / rate), s = Math.sin(2 * Math.PI * f * i / rate), y = out[2 * i];
            cc += c * c; ss += s * s; cs += c * s; yc += y * c; ys += y * s; peak = Math.max(peak, Math.abs(y));
        }
        double det = cc * ss - cs * cs, A = (yc * ss - ys * cs) / det, B = (ys * cc - yc * cs) / det, fit = 0, res = 0;
        for (int i = n / 2; i < n; i++) {
            double mdl = A * Math.cos(2 * Math.PI * f * i / rate) + B * Math.sin(2 * Math.PI * f * i / rate), y = out[2 * i];
            fit += mdl * mdl; res += (y - mdl) * (y - mdl);
        }
        double ratio = 10 * Math.log10(fit / Math.max(1e-30, res));
        System.out.printf(Locale.ROOT, "HeadroomTest: output peak %.1f dBFS, tone-to-rest %.1f dB (60 Hz -1 dBFS, low EQ +12 dB, group at 25 %%)%n",
            20 * Math.log10(peak / 32768), ratio);
        if (a.length > 0 && a[0].equals("check")) {
            if (ratio < 60) { System.out.println("HeadroomTest FAIL: distorted"); System.exit(1); }
            System.out.println("HeadroomTest PASS: no clipping inside the mixer");
        }
    }
}
