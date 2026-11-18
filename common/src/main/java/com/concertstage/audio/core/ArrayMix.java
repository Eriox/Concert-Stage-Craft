package com.concertstage.audio.core;

/**
 * 1.1.1: how the speaker arrays of one console add up at the listener (pure maths, no Minecraft; used by ClientAudio and the tests).
 *
 * <p>Before (1.1.0) every contribution was divided by the number of arrays linked to the console, whatever their place: a PA of ten
 * arrays (two hangs, subs, monitors, fills) played every array at a tenth (-20 dB) of its level, the front of house heard the mains at
 * -20 dB, and adding a monitor on stage made the whole show quieter. Now:
 * <ul>
 * <li>arrays of the same group and the same model whose acoustic centres are close (a hang of M12 one above the other, a sub stack,
 *     cabinets side by side; within {@link #CLUSTER_BLOCKS} of a member) form one <b>system</b>: they play the same feed from the same
 *     place, so they add up like one bigger source — the mean of their levels, plus 1.5 dB per doubling of elements, never their sum
 *     (no uncontrolled build-up when a hang gets longer);</li>
 * <li>separate systems (left and right hangs, delay towers, fills, monitors) add up by energy (square root of the sum of squares):
 *     the level of one system is kept where only it is heard, two systems heard equally give +3 dB, as a real PA does on average
 *     between its arrays.</li>
 * </ul>
 * Distance, directivity, obstacles, faders, mutes and the master are all inside the level of each array: nothing is normalised away.
 */
public final class ArrayMix {
    /** Distance between acoustic centres (blocks) under which arrays of one group and model are one system. */
    public static final double CLUSTER_BLOCKS = 3.5;
    /** Level added per doubling of the elements of one system (dB). */
    public static final double ARRAY_GAIN_DB = 1.5;

    private ArrayMix() {}

    /**
     * One array heard by the listener. {@code level}: everything but the pan (distance x directivity x obstacles x fader x master x
     * array volume), 0..1+; {@code pan}: -1 left .. +1 right; {@code model}: model id (only arrays of the same model are one system).
     */
    public record Contribution(int group, String model, double x, double y, double z, double level, double pan) {}

    /** The per group / side weights (group * 2 = left, group * 2 + 1 = right) of a list of contributions, times {@code volume}. */
    public static float[] weights(java.util.List<Contribution> in, int groups, double volume) {
        float[] out = new float[groups * 2];
        weights(in, groups, volume, out);
        return out;
    }

    /** Same, into {@code out} (length groups * 2, overwritten). Returns the number of systems found. */
    public static int weights(java.util.List<Contribution> in, int groups, double volume, float[] out) {
        java.util.Arrays.fill(out, 0f);
        int n = in.size();
        if (n == 0) return 0;
        // single-link clustering (n is small: the arrays of one console)
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        double r2 = CLUSTER_BLOCKS * CLUSTER_BLOCKS;
        for (int i = 0; i < n; i++) {
            Contribution a = in.get(i);
            for (int j = i + 1; j < n; j++) {
                Contribution b = in.get(j);
                if (a.group() != b.group() || !java.util.Objects.equals(a.model(), b.model())) continue;
                double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
                if (dx * dx + dy * dy + dz * dz <= r2) union(parent, i, j);
            }
        }
        // per system: mean of the left / right amplitudes of its elements, the array gain, then energy sum per group / side
        double[] sumL = new double[n], sumR = new double[n];
        int[] count = new int[n];
        for (int i = 0; i < n; i++) {
            Contribution c = in.get(i);
            int root = find(parent, i);
            double level = Double.isFinite(c.level()) ? Math.max(0, c.level()) : 0;
            double pan = Double.isFinite(c.pan()) ? Math.max(-1, Math.min(1, c.pan())) : 0;
            sumL[root] += level * Math.sqrt((1 - pan) * .5);
            sumR[root] += level * Math.sqrt((1 + pan) * .5);
            count[root]++;
        }
        double[] energy = new double[groups * 2];
        int systems = 0;
        for (int i = 0; i < n; i++) {
            if (count[i] == 0) continue;
            systems++;
            int g = in.get(i).group();
            if (g < 0 || g >= groups) continue;
            double k = Math.pow(10, ARRAY_GAIN_DB * (Math.log(count[i]) / Math.log(2)) / 20) / count[i];
            double l = sumL[i] * k, r = sumR[i] * k;
            energy[g * 2] += l * l;
            energy[g * 2 + 1] += r * r;
        }
        double v = Double.isFinite(volume) ? Math.max(0, volume) : 0;
        for (int i = 0; i < out.length; i++) out[i] = (float) (Math.sqrt(energy[i]) * v);
        return systems;
    }

    /** The 1.1.0 rule (each array divided by the number of arrays of the console), kept for the comparison tests. */
    public static float[] legacyWeights(java.util.List<Contribution> in, int groups, double volume, int linkedArrays) {
        float[] out = new float[groups * 2];
        for (Contribution c : in) {
            int g = c.group();
            if (g < 0 || g >= groups) continue;
            out[g * 2] += (float) (c.level() * Math.sqrt((1 - c.pan()) * .5));
            out[g * 2 + 1] += (float) (c.level() * Math.sqrt((1 + c.pan()) * .5));
        }
        float norm = (float) (volume / Math.max(1, linkedArrays));
        for (int i = 0; i < out.length; i++) out[i] *= norm;
        return out;
    }

    private static int find(int[] p, int i) {
        while (p[i] != i) { p[i] = p[p[i]]; i = p[i]; }
        return i;
    }

    private static void union(int[] p, int a, int b) {
        int ra = find(p, a), rb = find(p, b);
        if (ra != rb) p[Math.max(ra, rb)] = Math.min(ra, rb);
    }
}
