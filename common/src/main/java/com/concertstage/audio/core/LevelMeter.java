package com.concertstage.audio.core;

/**
 * 1.1.1: level of a 48 kHz (or any rate) stereo signal as audio engineers read it — sample peak, RMS, loudness (ITU-R BS.1770-4:
 * K-weighting, 400 ms blocks with 75 % overlap, absolute gate -70 LUFS and relative gate -10 LU for the integrated value; momentary
 * = last 400 ms, short-term = last 3 s) and the number of samples at or beyond digital full scale.
 *
 * <p>Used by the console diagnostics (level indicator of the sound actually sent to Minecraft) and by the offline tests that measure
 * each stage of the chain. No allocation once built; owned by one thread.
 */
public final class LevelMeter {
    private final int rate;
    private final double[][] k = new double[2][8];          // per channel: two biquads (x1 x2 y1 y2) of the K-weighting
    private final double b0a, b1a, b2a, a1a, a2a, b0b, b1b, b2b, a1b, a2b;
    private final int blockStep;                            // 100 ms: blocks of 400 ms overlap by 75 %
    private final double[] quarters = new double[30];       // mean square of the last 30 steps of 100 ms (3 s), ring
    private int qAt, qCount;
    private double stepSum;
    private int stepFill;
    private final java.util.ArrayList<Double> blocks = new java.util.ArrayList<>();
    private double peak, sumSquares;
    private long frames, clipped;
    private final double clipLevel;
    private final boolean keepBlocks;

    /** A meter for the live indicator: momentary / short-term loudness, peak, clipping (no integrated value, no allocation). */
    public LevelMeter(int rate, double fullScale) { this(rate, fullScale, false); }

    /**
     * @param fullScale value of digital full scale for the samples given (32768 for 16-bit integers, 1 for floats)
     * @param integrated keep every 400 ms block for {@link #integrated()} (tests and measurements; grows with the duration)
     */
    public LevelMeter(int rate, double fullScale, boolean integrated) {
        this.rate = rate;
        this.keepBlocks = integrated;
        this.clipLevel = fullScale * 0.99997;
        // BS.1770-4 pre-filter (high shelf, +4 dB) and RLB high pass, designed for this rate (the published 48 kHz values otherwise)
        double f0 = 1681.974450955533, g = 3.999843853973347, q = 0.7071752369554196;
        double kk = Math.tan(Math.PI * f0 / rate), vh = Math.pow(10, g / 20), vb = Math.pow(vh, 0.4996667741545416);
        double a0 = 1 + kk / q + kk * kk;
        b0a = (vh + vb * kk / q + kk * kk) / a0; b1a = 2 * (kk * kk - vh) / a0; b2a = (vh - vb * kk / q + kk * kk) / a0;
        a1a = 2 * (kk * kk - 1) / a0; a2a = (1 - kk / q + kk * kk) / a0;
        double f1 = 38.13547087602444, q1 = 0.5003270373238773, k1 = Math.tan(Math.PI * f1 / rate);
        double a01 = 1 + k1 / q1 + k1 * k1;
        b0b = 1; b1b = -2; b2b = 1;
        a1b = 2 * (k1 * k1 - 1) / a01; a2b = (1 - k1 / q1 + k1 * k1) / a01;
        blockStep = Math.max(1, rate / 10);
    }

    public void reset() {
        for (double[] c : k) java.util.Arrays.fill(c, 0);
        java.util.Arrays.fill(quarters, 0);
        qAt = qCount = stepFill = 0; stepSum = 0; blocks.clear(); peak = sumSquares = 0; frames = clipped = 0;
    }

    private double kw(int ch, double x) {
        double[] s = k[ch];
        double y = b0a * x + b1a * s[0] + b2a * s[1] - a1a * s[2] - a2a * s[3];
        s[1] = s[0]; s[0] = x; s[3] = s[2]; s[2] = y;
        double z = b0b * y + b1b * s[4] + b2b * s[5] - a1b * s[6] - a2b * s[7];
        s[5] = s[4]; s[4] = y; s[7] = s[6]; s[6] = z;
        return z;
    }

    /** Interleaved stereo 16-bit samples. */
    public void add(short[] stereo, int n) {
        for (int i = 0; i < n; i++) frame(stereo[2 * i], stereo[2 * i + 1]);
    }

    /** Interleaved stereo float samples (same full scale as given to the constructor). */
    public void add(float[] stereo, int n) {
        for (int i = 0; i < n; i++) frame(stereo[2 * i], stereo[2 * i + 1]);
    }

    private void frame(double l, double r) {
        double al = Math.abs(l), ar = Math.abs(r);
        if (al > peak) peak = al;
        if (ar > peak) peak = ar;
        if (al >= clipLevel) clipped++;
        if (ar >= clipLevel) clipped++;
        sumSquares += l * l + r * r;
        double kl = kw(0, l), kr = kw(1, r);
        stepSum += kl * kl + kr * kr;
        frames++;
        if (++stepFill == blockStep) {
            quarters[qAt] = stepSum / blockStep;
            qAt = (qAt + 1) % quarters.length;
            qCount++;
            stepSum = 0; stepFill = 0;
            if (keepBlocks && qCount >= 4) blocks.add(meanOf(4));
        }
    }

    private double meanOf(int steps) {
        int n = Math.min(steps, Math.min(qCount, quarters.length));
        if (n == 0) return 0;
        double s = 0;
        for (int i = 1; i <= n; i++) s += quarters[Math.floorMod(qAt - i, quarters.length)];
        return s / n;
    }

    private double lufs(double meanSquare, double fullScale) {
        return meanSquare <= 0 ? Double.NEGATIVE_INFINITY : -0.691 + 10 * Math.log10(meanSquare / (fullScale * fullScale));
    }

    private double fs() { return clipLevel / 0.99997; }

    /** Sample peak in dBFS. */
    public double peakDb() { return peak <= 0 ? Double.NEGATIVE_INFINITY : 20 * Math.log10(peak / fs()); }

    /** RMS of both channels together in dBFS (a full-scale sine reads -3 dBFS). */
    public double rmsDb() { return frames == 0 || sumSquares <= 0 ? Double.NEGATIVE_INFINITY : 10 * Math.log10(sumSquares / (2.0 * frames) / (fs() * fs())); }

    /** Momentary loudness (last 400 ms), LUFS. */
    public double momentary() { return lufs(meanOf(4), fs()); }

    /** Short-term loudness (last 3 s), LUFS. */
    public double shortTerm() { return lufs(meanOf(30), fs()); }

    /** Integrated loudness of everything measured since the last reset, gated (BS.1770-4), LUFS. */
    public double integrated() {
        double abs = Math.pow(10, (-70 + 0.691) / 10) * fs() * fs();
        double s = 0; int n = 0;
        for (double b : blocks) if (b > abs) { s += b; n++; }
        if (n == 0) return Double.NEGATIVE_INFINITY;
        double rel = s / n * Math.pow(10, -1);                    // -10 LU
        s = 0; n = 0;
        for (double b : blocks) if (b > abs && b > rel) { s += b; n++; }
        return n == 0 ? Double.NEGATIVE_INFINITY : lufs(s / n, fs());
    }

    public long clippedSamples() { return clipped; }
    public long frames() { return frames; }
    public int rate() { return rate; }
}
