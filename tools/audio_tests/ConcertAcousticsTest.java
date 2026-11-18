import com.concertstage.audio.core.ConcertAcoustics;

/** Concert realism (aurex.2): off = untouched, echo where expected, glue keeps peaks under full scale, bass adds harmonics, no NaN. */
public final class ConcertAcousticsTest {
    static final int RATE = 48000;
    static int checks;

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }

    static float[] sine(double hz, double amp, int frames) {
        float[] d = new float[frames * 2];
        for (int i = 0; i < frames; i++) { float v = (float) (amp * 32767 * Math.sin(2 * Math.PI * hz * i / RATE)); d[2 * i] = v; d[2 * i + 1] = v; }
        return d;
    }

    static double goertzel(float[] d, int from, int frames, double hz) {
        double w = 2 * Math.PI * hz / RATE, c = 2 * Math.cos(w), s1 = 0, s2 = 0;
        for (int i = from; i < from + frames; i++) { double s = d[2 * i] + c * s1 - s2; s2 = s1; s1 = s; }
        return Math.sqrt(s1 * s1 + s2 * s2 - c * s1 * s2) / frames;
    }

    static void run(ConcertAcoustics a, float[] d) {
        int frames = d.length / 2;
        for (int off = 0; off < frames; off += 1920) {
            int n = Math.min(1920, frames - off);
            float[] block = java.util.Arrays.copyOfRange(d, off * 2, (off + n) * 2);
            a.process(block, n);
            System.arraycopy(block, 0, d, off * 2, n * 2);
        }
    }

    public static void main(String[] args) {
        // off: bit-exact
        ConcertAcoustics off = new ConcertAcoustics(RATE);
        off.enabled(false);
        float[] x = sine(440, .5, RATE / 2), y = x.clone();
        run(off, y);
        check(java.util.Arrays.equals(x, y), "disabled: untouched");

        // echo: an impulse comes back about 100 ms later, quieter
        ConcertAcoustics echo = new ConcertAcoustics(RATE); echo.enabled(true);
        echo.style(0, 0); echo.wind(0); echo.echo(100, .3f);
        float[] warm = new float[RATE * 2 * 2];                          // two seconds of silence: the echo level settles
        run(echo, warm);
        float[] imp = new float[RATE * 2];
        for (int i = 0; i < 96; i++) imp[2 * i] = imp[2 * i + 1] = (float) (20000 * Math.sin(Math.PI * i / 96.0));
        run(echo, imp);
        int at = 0; double best = 0;
        for (int i = 2000; i < RATE; i++) { double v = Math.abs(imp[2 * i]); if (v > best) { best = v; at = i; } }
        check(Math.abs(at - 4800) < 400, "echo near 100 ms (found " + at + " samples)");
        check(best > 500 && best < 20000 * .4, "echo quieter than the sound (" + best + ")");

        // glue: loud material stays under full scale, quiet material only gets the make-up
        ConcertAcoustics glue = new ConcertAcoustics(RATE); glue.enabled(true);
        glue.echo(0, 0); glue.wind(0); glue.style(1, 0);
        float[] loud = sine(220, .95, RATE);
        run(glue, loud);
        double max = 0; for (float v : loud) max = Math.max(max, Math.abs(v));
        check(max <= 32768.0, "glue: no sample above full scale (" + max + ")");
        ConcertAcoustics glue2 = new ConcertAcoustics(RATE); glue2.enabled(true);
        glue2.echo(0, 0); glue2.wind(0); glue2.style(1, 0);
        float[] quiet = sine(220, .01, RATE);
        run(glue2, quiet);
        double inLevel = goertzel(sine(220, .01, RATE), RATE / 2, RATE / 4, 220), outLevel = goertzel(quiet, RATE / 2, RATE / 4, 220);
        check(Math.abs(outLevel / inLevel - 1.41) < .05, "glue: quiet parts only get the make-up (x" + outLevel / inLevel + ")");

        // concert bass: a 50 Hz tone gains its 100 / 150 Hz harmonics
        ConcertAcoustics bass = new ConcertAcoustics(RATE); bass.enabled(true);
        bass.echo(0, 0); bass.wind(0); bass.style(0, 1);
        float[] low = sine(50, .5, RATE);
        double h0 = goertzel(low, RATE / 2, RATE / 4, 100) + goertzel(low, RATE / 2, RATE / 4, 150);
        run(bass, low);
        double h1 = goertzel(low, RATE / 2, RATE / 4, 100) + goertzel(low, RATE / 2, RATE / 4, 150);
        check(h1 > h0 * 20 + 50, "bass harmonics added (" + h0 + " -> " + h1 + ")");

        // everything at once on noise, with wind: finite numbers
        ConcertAcoustics all = new ConcertAcoustics(RATE); all.enabled(true);
        all.echo(350, .3f); all.style(.5f, .5f); all.wind(1);
        java.util.Random r = new java.util.Random(3);
        float[] n = new float[RATE * 3 * 2];
        for (int i = 0; i < n.length; i++) n[i] = (float) (r.nextGaussian() * 6000);
        run(all, n);
        boolean finite = true; for (float v : n) finite &= Float.isFinite(v);
        check(finite, "finite output");
        all.clear();
        System.out.println("ConcertAcousticsTest: " + checks + " checks PASS");
    }
}
