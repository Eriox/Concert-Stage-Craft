package com.concertstage.audio.core;

/** Stereo-linked lookahead limiter: quieter tracks gain level, peaks stay bounded.
 * Five milliseconds of fixed delay; one millisecond attack, 150 ms release. */
public final class OutputLimiter {
    private final float[] delay;
    private final double attack, release;
    private double gain = 1;
    private int at;
    private volatile float reductionDb;
    /** 1.1.1: largest gain reduction of the last callback (dB, 0 = the limiter did nothing). */
    public float reductionDb() { return reductionDb; }
    public OutputLimiter(int rate) {
        delay = new float[Math.max(1, rate / 200) * 2];
        attack = 1 - Math.exp(-1.0 / (rate * .001));
        release = 1 - Math.exp(-1.0 / (rate * .150));
    }
    public float process(float[] mix, int frames, float boost, short[] output) {
        if (!Float.isFinite(boost)) boost = 1;
        boost = Math.max(0, Math.min(8, boost));
        float peak = 0;
        double least = 1;
        for (int i = 0; i < frames; i++) {
            float l = mix[2*i] * boost, r = mix[2*i+1] * boost;
            double amplitude = Math.max(Math.abs(l), Math.abs(r));
            double target = amplitude > 31000 ? 31000 / amplitude : 1;
            gain += (target - gain) * (target < gain ? attack : release);
            if (gain < least) least = gain;
            float dl = delay[at], dr = delay[at+1];
            delay[at] = l; delay[at+1] = r;
            at = (at + 2) % delay.length;
            double ol = Math.max(-32112, Math.min(32112, dl * gain));
            double or = Math.max(-32112, Math.min(32112, dr * gain));
            output[2*i] = (short)Math.round(ol); output[2*i+1] = (short)Math.round(or);
            peak = Math.max(peak, (float)Math.max(Math.abs(ol), Math.abs(or))/32768);
        }
        reductionDb = (float) (-20 * Math.log10(Math.max(1e-6, least)));
        return peak;
    }
}
