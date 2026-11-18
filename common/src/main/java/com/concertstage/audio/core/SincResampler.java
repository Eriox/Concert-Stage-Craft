package com.concertstage.audio.core;

import java.io.IOException;

/**
 * 1.1.1: sample-rate conversion of a decoded track to the 48 kHz of the console stream, by band-limited (windowed sinc)
 * interpolation: 32 taps, Kaiser window (beta 7), 512 sub-sample phases interpolated linearly, pass band up to 95.5 % of the lower
 * Nyquist frequency (21 kHz for a 44.1 kHz file).
 *
 * <p>Before, the conversion was linear between two samples: most music files are 44.1 kHz, so nearly every track was converted that
 * way — highs rolled off (-1 dB at 6 kHz, about -5 dB at 15 kHz) and mirror images of the highs folded back into the audible band
 * (measured by ResamplerTest). The number of samples produced, the start, the end (the last source value held, never zeros
 * inserted) and the bit-exact pass-through of a 48 kHz file are those of the former converter.
 *
 * <p>Runs on the decoder thread of the playlist (never on the sound thread); no allocation after construction.
 */
public final class SincResampler {
    private static final int HALF = 16, TAPS = HALF * 2, PHASES = 512;
    private static final double BETA = 7.0;

    private final TrackDecoder decoder;
    private final int sourceRate, rate;
    private final float[] table;                 // (PHASES + 1) x TAPS
    private final short[] input = new short[8192];
    private int available, at;
    private boolean eof;
    // decoded frames [start, end) in buf (interleaved stereo), source index of buf[0] = start
    private double[] buf = new double[8192 * 2];
    private long start, end;
    private long outputIndex;
    private double firstL, firstR, lastL, lastR;
    private boolean any;

    public SincResampler(TrackDecoder decoder, int rate) {
        this.decoder = decoder;
        this.sourceRate = decoder.sampleRate();
        this.rate = rate;
        this.table = sourceRate == rate ? null : kernel(0.5 * 0.955 * Math.min(1.0, rate / (double) sourceRate));
    }

    /** Kernel of cut-off {@code fc} (cycles per source sample), PHASES + 1 rows of TAPS, each row normalised to unity gain. */
    private static float[] kernel(double fc) {
        float[] t = new float[(PHASES + 1) * TAPS];
        double i0b = bessel0(BETA);
        for (int p = 0; p <= PHASES; p++) {
            double frac = p / (double) PHASES, sum = 0;
            double[] row = new double[TAPS];
            for (int j = 0; j < TAPS; j++) {
                double x = (j - (HALF - 1)) - frac;                      // tap j holds source sample ip - (HALF - 1) + j
                double w = Math.abs(x) >= HALF ? 0 : bessel0(BETA * Math.sqrt(1 - (x / HALF) * (x / HALF))) / i0b;
                double s = x == 0 ? 1 : Math.sin(2 * Math.PI * fc * x) / (2 * Math.PI * fc * x);
                row[j] = 2 * fc * s * w;
                sum += row[j];
            }
            for (int j = 0; j < TAPS; j++) t[p * TAPS + j] = (float) (row[j] / sum);
        }
        return t;
    }

    private static double bessel0(double x) {
        double s = 1, term = 1, h = x / 2;
        for (int k = 1; k < 50; k++) { term *= (h / k) * (h / k); s += term; if (term < 1e-12 * s) break; }
        return s;
    }

    private boolean pull() throws IOException {
        if (eof) return false;
        if (at >= available) {
            available = decoder.read(input);
            at = 0;
            if (available <= 0) { eof = true; return false; }
        }
        if ((end - start + 1) * 2 > buf.length) {
            // keep only what the next outputs can still need
            long keep = Math.max(start, (long) Math.floor(outputIndex * (double) sourceRate / rate) - HALF - 2);
            int drop = (int) (keep - start);
            if (drop > 0) {
                System.arraycopy(buf, drop * 2, buf, 0, (int) (end - keep) * 2);
                start = keep;
            }
            if ((end - start + 1) * 2 > buf.length) buf = java.util.Arrays.copyOf(buf, buf.length * 2);
        }
        int k = (int) (end - start) * 2;
        double l = input[at * 2], r = input[at * 2 + 1];
        buf[k] = l; buf[k + 1] = r;
        if (!any) { firstL = l; firstR = r; any = true; }
        lastL = l; lastR = r;
        at++; end++;
        return true;
    }

    private double sample(long index, int ch) {
        if (index < 0) return ch == 0 ? firstL : firstR;
        if (index >= end) return ch == 0 ? lastL : lastR;           // past the end: the last value held
        return buf[(int) (index - start) * 2 + ch];
    }

    /** Fills {@code out} (interleaved stereo at the console rate); returns the frames written, 0 at the end of the track. */
    public int read(short[] out) throws IOException {
        int n = 0, want = out.length / 2;
        while (n < want) {
            long numerator = outputIndex * sourceRate;
            long ip = numerator / rate;
            // the former converter: an output exists while its source index is a real sample
            while (end <= ip && pull()) { }
            if (end <= ip) return n;
            if (table == null) {
                out[n * 2] = (short) Math.round(sample(ip, 0));
                out[n * 2 + 1] = (short) Math.round(sample(ip, 1));
            } else {
                while (end <= ip + HALF && pull()) { }
                double frac = (numerator % rate) / (double) rate;
                double pf = frac * PHASES;
                int p = (int) pf;
                double w = pf - p;
                int r0 = p * TAPS, r1 = Math.min(PHASES, p + 1) * TAPS;
                double l = 0, r = 0;
                long first = ip - (HALF - 1);
                for (int j = 0; j < TAPS; j++) {
                    double c = table[r0 + j] * (1 - w) + table[r1 + j] * w;
                    long idx = first + j;
                    double sl, sr;
                    if (idx >= start && idx < end) { int b = (int) (idx - start) * 2; sl = buf[b]; sr = buf[b + 1]; }
                    else { sl = sample(idx, 0); sr = sample(idx, 1); }
                    l += c * sl; r += c * sr;
                }
                out[n * 2] = (short) Math.max(-32768, Math.min(32767, Math.round(l)));
                out[n * 2 + 1] = (short) Math.max(-32768, Math.min(32767, Math.round(r)));
            }
            n++;
            outputIndex++;
        }
        return n;
    }
}
