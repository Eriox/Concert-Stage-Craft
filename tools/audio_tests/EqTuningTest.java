import com.concertstage.audio.core.*;

/** Listening EQ (preview.9): the band follows its frequency setting, the default tuning is unchanged. */
public class EqTuningTest {
    static double gainAt(double f, float[] hz, float q) {
        int rate = 48000, n = rate;
        SystemProcessor p = new SystemProcessor(rate);
        if (hz != null) p.tuning(hz, q);
        SoundProfile prof = SoundProfile.DEFAULT.with(2, 12).with(8, 0);
        float[] d = new float[n * 2];
        for (int i = 0; i < n; i++) d[2 * i] = d[2 * i + 1] = (float) (3000 * Math.sin(2 * Math.PI * f * i / rate));
        p.process(d, n, prof);
        double e = 0; for (int i = n / 2; i < n; i++) e += d[2 * i] * (double) d[2 * i];
        return 20 * Math.log10(Math.sqrt(e / (n / 2)) / (3000 / Math.sqrt(2)));
    }
    public static void main(String[] a) {
        double def1k = gainAt(1000, null, .8f), def3k = gainAt(3000, null, .8f);
        double moved3k = gainAt(3000, new float[] {90, 300, 3000, 3000, 8000}, .8f), moved1k = gainAt(1000, new float[] {90, 300, 3000, 3000, 8000}, .8f);
        if (Math.abs(def1k - 12) > 0.5) throw new AssertionError("default 1 kHz " + def1k);
        if (Math.abs(moved3k - 12) > 0.5 || moved1k > 6) throw new AssertionError("moved band " + moved3k + " / " + moved1k);
        double narrow = gainAt(1400, new float[] {90, 300, 1000, 3000, 8000}, 4f), wide = gainAt(1400, new float[] {90, 300, 1000, 3000, 8000}, .4f);
        if (!(wide > narrow + 3)) throw new AssertionError("Q " + narrow + " / " + wide);
        System.out.printf("EqTuningTest PASS: default +%.1f dB at 1 kHz (3 kHz %+.1f); band moved to 3 kHz: %+.1f dB there, %+.1f at 1 kHz; Q 4 / 0.4 at 1.4 kHz: %+.1f / %+.1f dB%n",
            def1k, def3k, moved3k, moved1k, narrow, wide);
    }
}
