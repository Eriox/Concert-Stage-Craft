package com.concertstage.audio.core;

/**
 * Signal of one speaker array, from the stereo track to the mono feed its sound source plays: the side of the mix it plays (left,
 * right or the mono sum), the console's 3-band EQ (low shelf 120 Hz, bell 1 kHz, high shelf 6 kHz, +-12 dB) and the crossover
 * between the subwoofers and the mains (Linkwitz-Riley 24 dB/oct at 100 Hz: low pass on the subs, high pass on the mains when the
 * console drives subs). Runs on the sound thread, one instance per stream.
 */
public final class SpeakerDsp {
    public static final double CROSSOVER_HZ = 100;
    private final int rate;
    private final Biquad low = new Biquad(), mid = new Biquad(), high = new Biquad();
    private final Biquad x1 = new Biquad(), x2 = new Biquad();
    private float eqL = Float.NaN, eqM = Float.NaN, eqH = Float.NaN;
    private int xover = -2;
    private float crossoverHz=100;

    public SpeakerDsp(int rate) {
        this.rate = rate;
    }

    /**
     * @param side -1 left, +1 right, 0 mono sum
     * @param xoverMode 0 none, 1 low pass (subwoofer), 2 high pass (mains with subs)
     * @return peak of the output (0..1)
     */
    public float process(short[] stereo, int frames, int side, float eqLowDb, float eqMidDb, float eqHighDb, int xoverMode, short[] mono) {
        return process(stereo,frames,side,eqLowDb,eqMidDb,eqHighDb,xoverMode,100,mono);
    }
    public float process(short[] stereo,int frames,int side,float eqLowDb,float eqMidDb,float eqHighDb,int xoverMode,float hz,short[] mono) {
        configure(eqLowDb, eqMidDb, eqHighDb, xoverMode, hz);
        boolean eq = eqL != 0 || eqM != 0 || eqH != 0;
        float peak = 0;
        for (int i = 0; i < frames; i++) {
            double l = stereo[2 * i], r = stereo[2 * i + 1];
            double s = side < 0 ? l : side > 0 ? r : (l + r) * 0.5;
            if (eq) s = high.run(mid.run(low.run(s)));
            if (xover != 0) s = x2.run(x1.run(s));
            if (s > 32767) s = 32767; else if (s < -32768) s = -32768;
            mono[i] = (short) s;
            float a = (float) Math.abs(s) / 32768f;
            if (a > peak) peak = a;
        }
        return peak;
    }

    /**
     * 1.1.1: the same processing into a float buffer, without the 16-bit clamp. The 1.1.0 path rounded and clipped the feed of each
     * group to 16 bits <i>before</i> its gain: a loud master with the low EQ raised clipped there (hard, at every group), even when the
     * group was played quietly. The level is now only bounded once, by the output limiter.
     */
    public float process(short[] stereo, int frames, int side, float eqLowDb, float eqMidDb, float eqHighDb, int xoverMode, float hz, float[] mono) {
        configure(eqLowDb, eqMidDb, eqHighDb, xoverMode, hz);
        boolean eq = eqL != 0 || eqM != 0 || eqH != 0;
        float peak = 0;
        for (int i = 0; i < frames; i++) {
            double l = stereo[2 * i], r = stereo[2 * i + 1];
            double s = side < 0 ? l : side > 0 ? r : (l + r) * 0.5;
            if (eq) s = high.run(mid.run(low.run(s)));
            if (xover != 0) s = x2.run(x1.run(s));
            mono[i] = (float) s;
            float a = (float) Math.abs(s) / 32768f;
            if (a > peak) peak = a;
        }
        return peak;
    }

    private void configure(float eqLowDb, float eqMidDb, float eqHighDb, int xoverMode, float hz) {
        if (eqLowDb != eqL || eqMidDb != eqM || eqHighDb != eqH) {
            eqL = eqLowDb; eqM = eqMidDb; eqH = eqHighDb;
            low.lowShelf(rate, 120, eqL);
            mid.peaking(rate, 1000, 0.8, eqM);
            high.highShelf(rate, 6000, eqH);
        }
        if (xoverMode != xover || hz != crossoverHz) {
            xover = xoverMode; crossoverHz = hz;
            if (xover == 1) { x1.lowPass(rate, crossoverHz, Math.sqrt(0.5)); x2.lowPass(rate, crossoverHz, Math.sqrt(0.5)); }
            else if (xover == 2) { x1.highPass(rate, crossoverHz, Math.sqrt(0.5)); x2.highPass(rate, crossoverHz, Math.sqrt(0.5)); }
            x1.reset(); x2.reset();
        }
    }

    /** 1.1.1: forget the filter memory (a group that starts again after being silent starts from silence, not from old samples). */
    public void reset() {
        low.reset(); mid.reset(); high.reset(); x1.reset(); x2.reset();
    }

    /** RBJ audio EQ cookbook biquad, direct form I. */
    public static final class Biquad {
        private double b0 = 1, b1, b2, a1, a2, x1, x2, y1, y2;

        public void reset() { x1 = x2 = y1 = y2 = 0; }

        public double run(double x) {
            double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1; x1 = x; y2 = y1; y1 = y;
            if (Math.abs(y1) < 1e-20) y1 = 0;                       // no denormals
            return y;
        }

        private void set(double b0, double b1, double b2, double a0, double a1, double a2) {
            this.b0 = b0 / a0; this.b1 = b1 / a0; this.b2 = b2 / a0; this.a1 = a1 / a0; this.a2 = a2 / a0;
        }

        public void lowPass(int rate, double f, double q) {
            double w = 2 * Math.PI * f / rate, c = Math.cos(w), al = Math.sin(w) / (2 * q);
            set((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + al, -2 * c, 1 - al);
        }

        public void highPass(int rate, double f, double q) {
            double w = 2 * Math.PI * f / rate, c = Math.cos(w), al = Math.sin(w) / (2 * q);
            set((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + al, -2 * c, 1 - al);
        }

        public void peaking(int rate, double f, double q, double db) {
            double A = Math.pow(10, db / 40), w = 2 * Math.PI * f / rate, c = Math.cos(w), al = Math.sin(w) / (2 * q);
            set(1 + al * A, -2 * c, 1 - al * A, 1 + al / A, -2 * c, 1 - al / A);
        }

        public void lowShelf(int rate, double f, double db) {
            double A = Math.pow(10, db / 40), w = 2 * Math.PI * f / rate, c = Math.cos(w), s = Math.sin(w);
            double al = s / 2 * Math.sqrt(2), sq = 2 * Math.sqrt(A) * al;
            set(A * ((A + 1) - (A - 1) * c + sq), 2 * A * ((A - 1) - (A + 1) * c), A * ((A + 1) - (A - 1) * c - sq),
                (A + 1) + (A - 1) * c + sq, -2 * ((A - 1) + (A + 1) * c), (A + 1) + (A - 1) * c - sq);
        }

        public void highShelf(int rate, double f, double db) {
            double A = Math.pow(10, db / 40), w = 2 * Math.PI * f / rate, c = Math.cos(w), s = Math.sin(w);
            double al = s / 2 * Math.sqrt(2), sq = 2 * Math.sqrt(A) * al;
            set(A * ((A + 1) + (A - 1) * c + sq), -2 * A * ((A - 1) + (A + 1) * c), A * ((A + 1) + (A - 1) * c - sq),
                (A + 1) - (A - 1) * c + sq, 2 * ((A - 1) - (A + 1) * c), (A + 1) - (A - 1) * c - sq);
        }
    }
}
