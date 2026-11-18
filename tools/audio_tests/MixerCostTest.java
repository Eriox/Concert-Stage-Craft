import com.concertstage.audio.core.*;
import java.util.*;

/**
 * 1.1.1 — Test E (cost) of the sound engine, off game: time of the console mix (sound thread) per second of sound, with every group
 * heard and with the usual case (mains left / right, subs, one monitor group), and time of the per-tick level computation of 2 to 100
 * arrays (game thread). Indicative: one shared machine, after warm-up; not a measure of the game's frame rate.
 */
public class MixerCostTest {
    static final int RATE = 48000, GROUPS = 9;

    static double mixMsPerSecond(float[] w, int seconds) {
        ConsoleMixer m = new ConsoleMixer(RATE, GROUPS);
        Random r = new Random(1);
        short[] in = new short[2048 * 2], out = new short[2048 * 2];
        for (int i = 0; i < in.length; i++) in[i] = (short) (r.nextGaussian() * 6000);
        int blocks = seconds * RATE / 2048;
        for (int i = 0; i < blocks / 4; i++) m.process(in, 2048, w, 0, 0, 0, true, 1.5f, SoundProfile.DEFAULT, 20000, out);   // warm-up
        long t = System.nanoTime();
        for (int i = 0; i < blocks; i++) m.process(in, 2048, w, 0, 0, 0, true, 1.5f, SoundProfile.DEFAULT, 20000, out);
        return (System.nanoTime() - t) / 1e6 / seconds;
    }

    public static void main(String[] a) {
        float[] all = new float[GROUPS * 2];
        Arrays.fill(all, .3f);
        float[] usual = new float[GROUPS * 2];
        for (int g : new int[] {0, 1, 2, 6}) { usual[g * 2] = .3f; usual[g * 2 + 1] = .3f; }
        double tAll = 0, tUsual = 0;
        for (int rep = 0; rep < 3; rep++) { tAll = mixMsPerSecond(all, 20); tUsual = mixMsPerSecond(usual, 20); }
        System.out.printf(Locale.ROOT, "console mix, sound thread, per second of sound: 9 groups heard %.2f ms, 4 groups heard (mains L/R, subs, monitors) %.2f ms%n", tAll, tUsual);
        Random r = new Random(3);
        for (int n : new int[] {2, 10, 25, 50, 100}) {
            List<ArrayMix.Contribution> in = new ArrayList<>();
            for (int i = 0; i < n; i++) in.add(new ArrayMix.Contribution(i % GROUPS, i % 3 == 0 ? "csc_aurex_m12" : "csc_aurex_s218_f",
                r.nextDouble() * 60 - 30, r.nextDouble() * 12, r.nextDouble() * 10, r.nextDouble(), r.nextDouble() * 2 - 1));
            float[] out = new float[GROUPS * 2];
            for (int i = 0; i < 2000; i++) ArrayMix.weights(in, GROUPS, 1, out);
            long t = System.nanoTime();
            int reps = 20000;
            for (int i = 0; i < reps; i++) ArrayMix.weights(in, GROUPS, 1, out);
            double us = (System.nanoTime() - t) / 1e3 / reps;
            System.out.printf(Locale.ROOT, "  %3d arrays: level sum %.2f us per tick (%.3f ms per second of game at 20 ticks)%n", n, us, us * 20 / 1000);
        }
        System.out.println("MixerCostTest done (timings only, no pass / fail)");
    }
}
