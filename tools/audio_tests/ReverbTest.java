import com.concertstage.audio.core.*;

/** Room reverb (1.1.0): dry when off, a decaying tail when on, finite and bounded, smooth level changes. */
public class ReverbTest {
    public static void main(String[] a) {
        int rate = 48000;
        // off: the signal is untouched (bit for bit)
        Reverb off = new Reverb(rate);
        float[] x = new float[rate * 2];
        for (int i = 0; i < rate; i++) { x[2 * i] = (float) (8000 * Math.sin(i * 0.05)); x[2 * i + 1] = x[2 * i]; }
        float[] y = x.clone();
        off.process(y, rate, 0, .5f, .5f);
        for (int i = 0; i < y.length; i++) if (y[i] != x[i]) throw new AssertionError("dry path changed at " + i);
        // on: an impulse leaves a tail that decays
        Reverb r = new Reverb(rate);
        float[] buf = new float[rate * 2 * 3];
        buf[0] = buf[1] = 20000;
        for (int b = 0; b < 3 * rate; b += 2400) {
            float[] block = new float[2400 * 2];
            System.arraycopy(buf, b * 2, block, 0, block.length);
            r.process(block, 2400, .3f, .8f, .4f);
            System.arraycopy(block, 0, buf, b * 2, block.length);
        }
        double e1 = energy(buf, rate / 10, rate / 2), e2 = energy(buf, 2 * rate, 3 * rate);
        if (!(e1 > 1)) throw new AssertionError("no tail: " + e1);
        if (!(e2 < e1 / 20)) throw new AssertionError("tail does not decay: " + e1 + " -> " + e2);
        float peak = 0;
        for (float v : buf) { if (!Float.isFinite(v)) throw new AssertionError("not finite"); peak = Math.max(peak, Math.abs(v)); }
        if (peak > 40000) throw new AssertionError("peak " + peak);
        // non-finite controls are ignored
        Reverb n = new Reverb(rate);
        float[] z = x.clone();
        n.process(z, rate, Float.NaN, Float.POSITIVE_INFINITY, -3);
        for (float v : z) if (!Float.isFinite(v)) throw new AssertionError("NaN control leaked");
        // through the console mixer: default (no environment) = same as before
        ConsoleMixer m1 = new ConsoleMixer(rate), m2 = new ConsoleMixer(rate);
        short[] in = new short[4800 * 2], o1 = new short[4800 * 2], o2 = new short[4800 * 2];
        for (int i = 0; i < 4800; i++) { in[2 * i] = (short) (6000 * Math.sin(i * .03)); in[2 * i + 1] = in[2 * i]; }
        float[] t = new float[12]; t[0] = t[1] = .5f;
        m1.process(in, 4800, t, 0, 0, 0, false, o1);
        m2.environment(0, .5f, .5f);
        m2.process(in, 4800, t, 0, 0, 0, false, o2);
        for (int i = 0; i < o1.length; i++) if (o1[i] != o2[i]) throw new AssertionError("mixer changed with reverb off");
        System.out.printf("ReverbTest PASS: dry bit-exact when off, tail energy %.0f -> %.2f (decays), peak %.0f, finite, mixer unchanged when off%n", e1, e2, peak);
    }

    static double energy(float[] b, int from, int to) {
        double e = 0;
        for (int i = from; i < to; i++) e += b[2 * i] * (double) b[2 * i];
        return e / (to - from);
    }
}
