package com.concertstage.light.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Music analysis for the AutoShow (pure Java, runs off the game threads). The track is decoded once, mixed to mono and resampled to
 * 11025 Hz; every 23 ms a 1024-point FFT gives the energy of the low (30–150 Hz), mid (150–2000 Hz) and high (2–5 kHz) bands, a
 * spectral-flux onset strength and a 12-bin chroma. From these:
 * <ul>
 * <li>tempo: autocorrelation of the onset envelope (60–200 BPM, with a prior around 120 BPM and octave checks), beat phase by comb
 * scoring, first downbeat from the low-band accents;</li>
 * <li>bars, then a bar self-similarity matrix (chroma + energy); boundaries from a checkerboard novelty and from energy jumps, snapped
 * to phrases when close; sections of at least 4 bars;</li>
 * <li>repetitions: sections are grouped into motifs by similarity, so a returning chorus is recognised (with a confidence);</li>
 * <li>roles (intro, verse, build, chorus, drop, break, outro, silence) from relative energy, slope, bass share and context, each with a
 * confidence — an estimate from musical cues, not a certainty.</li>
 * </ul>
 * Buffers are allocated once per analysis and reused frame to frame.
 */
public final class Analyzer {
    public static final int RATE = 11025, N = 1024, HOP = 256;
    public static final float HOP_MS = HOP * 1000f / RATE;

    /** Frame features accumulated while the audio arrives (also used by the live analysis). */
    public static final class Features {
        private final float[] window = new float[N];
        private final float[] ring = new float[N];
        private int filled, sinceHop;
        private final float[] re = new float[N], im = new float[N], mag = new float[N / 2], prevLog = new float[N / 2];
        private double resamplePos, srcAccum;
        private int srcCount;
        final FloatList rms = new FloatList(), low = new FloatList(), mid = new FloatList(), high = new FloatList(), flux = new FloatList();
        final List<float[]> chroma = new ArrayList<>();
        private final int[] chromaBin = new int[N / 2];

        public Features() {
            for (int i = 0; i < N; i++) window[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / (N - 1)));
            for (int k = 0; k < N / 2; k++) {
                double f = k * (double) RATE / N;
                chromaBin[k] = f < 55 || f > 2000 ? -1 : Math.floorMod((int) Math.round(12 * Math.log(f / 440.0) / Math.log(2)) + 9, 12);
            }
        }

        public int frames() {
            return rms.size;
        }

        /** Adds interleaved stereo 16-bit samples at {@code srcRate}. */
        public void add(short[] stereo, int frames, int srcRate) {
            double step = srcRate / (double) RATE;
            for (int i = 0; i < frames; i++) {
                srcAccum += (stereo[2 * i] + stereo[2 * i + 1]) * (0.5 / 32768.0);
                srcCount++;
                resamplePos += 1.0;
                if (resamplePos >= step) {
                    resamplePos -= step;
                    push((float) (srcAccum / srcCount));        // box filter over the source samples of this output sample
                    srcAccum = 0;
                    srcCount = 0;
                }
            }
        }

        private int head;

        private void push(float s) {
            ring[head] = s;                                  // circular buffer: head is the oldest sample after the write
            head = (head + 1) & (N - 1);
            if (filled < N) filled++;
            if (++sinceHop >= HOP && filled >= N / 2) {
                sinceHop = 0;
                frame();
            }
        }

        private void frame() {
            double sum = 0;
            for (int i = 0; i < N; i++) {
                float x = ring[(head + i) & (N - 1)];
                sum += x * x;
                re[i] = x * window[i];
                im[i] = 0;
            }
            fft(re, im);
            double lo = 0, mi = 0, hi = 0, fl = 0;
            float[] ch = new float[12];
            for (int k = 1; k < N / 2; k++) {
                float m = (float) Math.sqrt(re[k] * re[k] + im[k] * im[k]);
                mag[k] = m;
                double f = k * (double) RATE / N;
                double e = m * m;
                if (f >= 30 && f < 150) lo += e; else if (f >= 150 && f < 2000) mi += e; else if (f >= 2000 && f < 5000) hi += e;
                float lg = (float) Math.log1p(10 * m);
                float d = lg - prevLog[k];
                if (d > 0) fl += d;
                prevLog[k] = lg;
                if (chromaBin[k] >= 0) ch[chromaBin[k]] += m;
            }
            rms.add((float) Math.sqrt(sum / N));
            low.add((float) Math.sqrt(lo));
            mid.add((float) Math.sqrt(mi));
            high.add((float) Math.sqrt(hi));
            flux.add((float) fl);
            float cs = 0;
            for (float c : ch) cs += c;
            if (cs > 1e-9f) for (int i = 0; i < 12; i++) ch[i] /= cs;
            chroma.add(ch);
        }
    }

    /** Growable float array without boxing. */
    static final class FloatList {
        float[] a = new float[1024];
        int size;

        void add(float v) {
            if (size == a.length) a = Arrays.copyOf(a, a.length * 2);
            a[size++] = v;
        }

        float[] toArray() {
            return Arrays.copyOf(a, size);
        }
    }

    /** In-place radix-2 complex FFT (length a power of two). */
    static void fft(float[] re, float[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                float t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            float wr = (float) Math.cos(ang), wi = (float) Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                float cr = 1, ci = 0;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k, b = i + k + len / 2;
                    float xr = re[b] * cr - im[b] * ci, xi = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - xr; im[b] = im[a] - xi;
                    re[a] += xr; im[a] += xi;
                    float ncr = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = ncr;
                }
            }
        }
    }

    /** Source of PCM for {@link #analyze}: interleaved stereo shorts, -1 at the end. */
    public interface Pcm {
        int sampleRate();

        int read(short[] out) throws java.io.IOException;
    }

    /** Full analysis of a track. {@code cancel} is polled (returns null when it says stop). */
    public static Analysis analyze(Pcm pcm, String sha1, java.util.function.BooleanSupplier cancel) throws java.io.IOException {
        Features f = new Features();
        short[] buf = new short[8192];
        int sr = Math.max(8000, pcm.sampleRate());
        long total = 0;
        while (true) {
            if (cancel != null && cancel.getAsBoolean()) return null;
            int n = pcm.read(buf);
            if (n < 0) break;
            f.add(buf, n, sr);
            total += n;
            if (total > (long) sr * 60 * 30) break;                  // 30 minutes at most
        }
        return finish(f, sha1, total * 1000L / sr);
    }

    // ------------------------------------------------------------ track level analysis
    public static Analysis finish(Features f, String sha1, long durationMs) {
        Analysis a = new Analysis();
        a.sha1 = sha1 == null ? "" : sha1;
        a.durationMs = durationMs;
        a.hopMs = HOP_MS;
        a.rms = normalise(f.rms.toArray());
        a.low = normalise(f.low.toArray());
        a.mid = normalise(f.mid.toArray());
        a.high = normalise(f.high.toArray());
        float[] env = onsetEnvelope(f.flux.toArray());
        a.onset = normalise(env.clone());
        tempo(a, env);
        sections(a, f.chroma);
        return a;
    }

    static float[] normalise(float[] x) {
        if (x.length == 0) return x;
        float[] s = x.clone();
        Arrays.sort(s);
        float ref = s[Math.min(s.length - 1, (int) (s.length * 0.995))];
        if (ref <= 1e-9f) return new float[x.length];
        for (int i = 0; i < x.length; i++) x[i] = Math.min(1f, x[i] / ref);
        return x;
    }

    /** Flux minus its local mean (1 s), half-wave rectified: the onsets stand out of the steady texture. */
    static float[] onsetEnvelope(float[] flux) {
        int n = flux.length, w = Math.max(3, Math.round(1000f / HOP_MS / 2));
        float[] out = new float[n];
        float[] pre = new float[n + 1];
        for (int i = 0; i < n; i++) pre[i + 1] = pre[i] + flux[i];
        for (int i = 0; i < n; i++) {
            int lo = Math.max(0, i - w), hi = Math.min(n, i + w + 1);
            float mean = (pre[hi] - pre[lo]) / (hi - lo);
            out[i] = Math.max(0, flux[i] - mean);
        }
        return out;
    }

    static void tempo(Analysis a, float[] env) {
        int n = env.length;
        double fr = 1000.0 / HOP_MS;
        if (n < fr * 8) { a.bpm = 0; a.tempoConfidence = 0; return; }
        int minLag = (int) Math.floor(fr * 60 / 200), maxLag = (int) Math.ceil(fr * 60 / 60);
        double[] ac = new double[maxLag + 2];
        for (int lag = minLag - 1; lag <= maxLag + 1; lag++) {
            double s = 0;
            for (int t = 0; t + lag < n; t++) s += env[t] * env[t + lag];
            ac[lag] = s / (n - lag);
        }
        int best = -1;
        double bestScore = -1, sumAc = 0;
        int cnt = 0;
        for (int lag = minLag; lag <= maxLag; lag++) {
            double bpm = 60 * fr / lag;
            double prior = Math.exp(-0.5 * Math.pow(Math.log(bpm / 120.0) / Math.log(2) / 0.9, 2));
            double score = ac[lag] * (0.55 + 0.45 * prior);
            sumAc += ac[lag]; cnt++;
            if (score > bestScore) { bestScore = score; best = lag; }
        }
        // parabolic refinement of the peak
        double y0 = ac[best - 1], y1 = ac[best], y2 = ac[best + 1];
        double denom = y0 - 2 * y1 + y2;
        double lagF = best + (Math.abs(denom) > 1e-12 ? 0.5 * (y0 - y2) / denom : 0);
        double bpm = 60 * fr / lagF;
        // octave check: the usual concert range is 80-170 BPM
        if (bpm < 80) {
            int half = (int) Math.round(lagF / 2);
            if (half >= minLag - 1 && ac[Math.max(1, half)] > 0.5 * y1) { bpm *= 2; lagF /= 2; }
        } else if (bpm > 170) {
            int dbl = (int) Math.round(lagF * 2);
            if (dbl < ac.length && ac[dbl] > 0.5 * y1) { bpm /= 2; lagF *= 2; }
        }
        double mean = sumAc / Math.max(1, cnt);
        a.tempoConfidence = (float) Math.max(0, Math.min(1, (y1 - mean) / Math.max(1e-9, y1)));
        a.bpm = (float) (Math.round(bpm * 10) / 10.0);
        // tempo and phase refined together: a comb over the whole onset envelope, tempo +-2 % in 0.02 BPM steps (a tempo off by
        // 0.3 BPM drifts by half a beat over a 3-minute song, so the autocorrelation peak alone is not precise enough)
        double coarse = bpm;
        double bestPhase = 0, bestPhaseScore = -1, bestBpm = coarse;
        for (double b = coarse * 0.98; b <= coarse * 1.02; b += 0.02) {
            double period = 60 * fr / b;
            for (double ph = 0; ph < period; ph += 0.5) {
                double s = comb(env, ph, period);
                if (s > bestPhaseScore) { bestPhaseScore = s; bestPhase = ph; bestBpm = b; }
            }
        }
        // finer phase at the chosen tempo
        double period = 60 * fr / bestBpm;
        for (double ph = Math.max(0, bestPhase - 0.5); ph <= bestPhase + 0.5; ph += 0.05) {
            double s = comb(env, ph, period);
            if (s > bestPhaseScore) { bestPhaseScore = s; bestPhase = ph; }
        }
        a.bpm = (float) bestBpm;
        a.firstBeatMs = Math.round(bestPhase * HOP_MS);
        // downbeat: the beat of the bar where the bass accents fall
        double[] score = new double[4];
        int k = 0;
        for (double t = bestPhase; t < n; t += period, k++) {
            int i = (int) Math.round(t);
            if (i >= n) break;
            score[k % 4] += a.low[Math.min(a.low.length - 1, i)] + 0.5 * a.onset[i];
        }
        int db = 0;
        for (int i = 1; i < 4; i++) if (score[i] > score[db]) db = i;
        a.downbeatPhase = db;
        a.beatsPerBar = 4;
    }

    /** Sum of the onset envelope (linearly interpolated) on a beat grid. */
    static double comb(float[] env, double phase, double period) {
        double s = 0;
        int n = env.length;
        for (double t = phase; t < n - 1; t += period) {
            int i = (int) t;
            double f = t - i;
            s += env[i] * (1 - f) + env[i + 1] * f;
        }
        return s;
    }

    /** Novelty of the last analysis, per bar (diagnostics of the tests). */
    public static double[] debugNovelty;

    static void sections(Analysis a, List<float[]> chroma) {
        long dur = a.durationMs;
        if (a.bpm <= 0 || a.rms.length == 0) {
            // no beat: one section per 8 s of steady energy
            single(a);
            return;
        }
        double barMs = a.beatMs() * a.beatsPerBar;
        long start = a.beat(a.downbeatPhase);
        while (start - barMs >= 0) start -= Math.round(barMs);
        int bars = (int) Math.max(1, Math.ceil((dur - start) / barMs));
        int dim = 5 + 12;
        float[][] feat = new float[bars][dim];
        for (int b = 0; b < bars; b++) {
            long s = start + Math.round(b * barMs), e = Math.min(dur, start + Math.round((b + 1) * barMs));
            int i0 = Math.max(0, (int) (s / a.hopMs)), i1 = Math.min(a.rms.length, (int) Math.max(i0 + 1, e / a.hopMs));
            float[] v = feat[b];
            int cnt = 0;
            for (int i = i0; i < i1; i++) {
                v[0] += a.rms[i]; v[1] += a.low[i]; v[2] += a.mid[i]; v[3] += a.high[i]; v[4] += a.onset[i];
                if (i < chroma.size()) { float[] c = chroma.get(i); for (int j = 0; j < 12; j++) v[5 + j] += c[j]; }
                cnt++;
            }
            if (cnt > 0) for (int j = 0; j < dim; j++) v[j] /= cnt;
        }
        // self-similarity of bars and checkerboard novelty
        double[] nov = new double[bars + 1];
        int K = Math.min(4, Math.max(1, bars / 4));
        for (int b = 1; b < bars; b++) {
            double s = 0;
            int c = 0;
            for (int i = Math.max(0, b - K); i < b; i++) {
                for (int j = b; j < Math.min(bars, b + K); j++) {
                    s += 1 - sim(feat[i], feat[j]);                          // across the boundary: dissimilar is novel
                    c++;
                }
            }
            double within = 0;
            int cw = 0;
            for (int i = Math.max(0, b - K); i < b; i++) for (int j = i + 1; j < b; j++) { within += 1 - sim(feat[i], feat[j]); cw++; }
            for (int i = b; i < Math.min(bars, b + K); i++) for (int j = i + 1; j < Math.min(bars, b + K); j++) { within += 1 - sim(feat[i], feat[j]); cw++; }
            double cross = c > 0 ? s / c : 0, in = cw > 0 ? within / cw : 0;
            // energy jump between the bars before and after
            double eb = 0, ea = 0;
            int nb = 0, na = 0;
            for (int i = Math.max(0, b - K); i < b; i++) { eb += feat[i][0]; nb++; }
            for (int i = b; i < Math.min(bars, b + K); i++) { ea += feat[i][0]; na++; }
            double jump = Math.abs(ea / Math.max(1, na) - eb / Math.max(1, nb));
            nov[b] = Math.max(0, cross - in) + 1.6 * jump;
            if ((b % 4) == 0) nov[b] *= 1.25;                                  // phrases of 4 bars
            if ((b % 8) == 0) nov[b] *= 1.1;
        }
        debugNovelty = nov;
        // peaks by prominence (how much a bar stands out of its +-4 bars), not by a global threshold: a quiet song has small
        // boundaries too, and one huge drop must not hide them
        double maxNov = 1e-9;
        for (int b = 1; b < bars; b++) maxNov = Math.max(maxNov, nov[b]);
        List<Integer> bounds = new ArrayList<>();
        bounds.add(0);
        int last = 0;
        for (int b = 2; b < bars - 1; b++) {
            boolean peak = true;
            double lo = nov[b];
            for (int d = -4; d <= 4; d++) {
                int j = b + d;
                if (d == 0 || j <= 0 || j >= bars) continue;
                if (Math.abs(d) <= 2 && nov[j] > nov[b]) peak = false;
                lo = Math.min(lo, nov[j]);
            }
            if (!peak || nov[b] - lo < 0.1 || nov[b] < 0.12 * maxNov) continue;
            if (b - last >= 4) { bounds.add(b); last = b; }
            else if (last > 0 && nov[b] > nov[last]) { bounds.set(bounds.size() - 1, b); last = b; }
        }
        if (bars - last < 4 && bounds.size() > 1) bounds.remove(bounds.size() - 1);
        bounds.add(bars);
        // sections
        List<float[]> secFeat = new ArrayList<>();
        for (int s = 0; s + 1 < bounds.size(); s++) {
            int b0 = bounds.get(s), b1 = bounds.get(s + 1);
            Analysis.Section sec = new Analysis.Section();
            sec.startMs = s == 0 ? 0 : start + Math.round(b0 * barMs);
            sec.endMs = s + 2 == bounds.size() ? dur : start + Math.round(b1 * barMs);
            float[] m = new float[dim];
            for (int b = b0; b < b1; b++) for (int j = 0; j < dim; j++) m[j] += feat[b][j] / (b1 - b0);
            int q = Math.max(1, (b1 - b0) / 4);
            float first = 0, lastQ = 0;
            for (int b = b0; b < b0 + q; b++) first += feat[b][0] / q;
            for (int b = b1 - q; b < b1; b++) lastQ += feat[b][0] / q;
            sec.energy = m[0];
            float tot = m[1] + m[2] + m[3];
            sec.lowRatio = tot > 1e-6f ? m[1] / tot : 0;
            sec.slope = lastQ - first;
            a.sections.add(sec);
            secFeat.add(m);
        }
        classify(a, secFeat);
    }

    private static void single(Analysis a) {
        Analysis.Section s = new Analysis.Section();
        s.startMs = 0;
        s.endMs = a.durationMs;
        float e = 0;
        for (float v : a.rms) e += v;
        s.energy = a.rms.length == 0 ? 0 : e / a.rms.length;
        s.kind = s.energy < 0.05f ? Analysis.SILENCE : Analysis.VERSE;
        s.confidence = 0.3f;
        a.sections.add(s);
    }

    /** Similarity of two bar features: chroma (harmony) and energy profile (texture), 0..1. */
    static double sim(float[] x, float[] y) {
        double dot = 0, nx = 0, ny = 0;
        for (int j = 5; j < x.length; j++) { dot += x[j] * y[j]; nx += x[j] * x[j]; ny += y[j] * y[j]; }
        double chroma = nx > 1e-12 && ny > 1e-12 ? dot / Math.sqrt(nx * ny) : 1;
        double de = 0;
        for (int j = 0; j < 5; j++) de += Math.abs(x[j] - y[j]);
        double energy = Math.max(0, 1 - de / 1.5);
        return 0.55 * chroma + 0.45 * energy;
    }

    /** Motifs (repetitions) and roles of the sections. */
    static void classify(Analysis a, List<float[]> feat) {
        List<Analysis.Section> s = a.sections;
        int n = s.size();
        float maxE = 1e-6f;
        for (Analysis.Section x : s) maxE = Math.max(maxE, x.energy);
        for (Analysis.Section x : s) { x.energy /= maxE; x.slope /= maxE; }
        // motifs: greedy clustering against the first section of each motif
        List<Integer> reps = new ArrayList<>();
        int[] count = new int[n];
        for (int i = 0; i < n; i++) {
            int motif = -1;
            double bestSim = 0.84;
            for (int m = 0; m < reps.size(); m++) {
                int r = reps.get(m);
                double sm = sim(feat.get(i), feat.get(r)) - 0.35 * Math.abs(s.get(i).energy - s.get(r).energy);
                if (sm > bestSim) { bestSim = sm; motif = m; }
            }
            if (motif < 0) { motif = reps.size(); reps.add(i); }
            s.get(i).motif = motif;
            s.get(i).occurrence = count[motif]++;
        }
        float[] lows = new float[n];
        for (int i = 0; i < n; i++) lows[i] = s.get(i).lowRatio;
        float[] sorted = lows.clone();
        Arrays.sort(sorted);
        float medianLow = n == 0 ? 0 : sorted[n / 2];
        for (int i = 0; i < n; i++) {
            Analysis.Section x = s.get(i);
            Analysis.Section p = i > 0 ? s.get(i - 1) : null, nx = i + 1 < n ? s.get(i + 1) : null;
            int repeats = count[x.motif];
            if (x.energy < 0.08f) { x.kind = Analysis.SILENCE; x.confidence = 0.9f; continue; }
            if (i == 0 && x.energy < 0.75f) { x.kind = Analysis.INTRO; x.confidence = 0.55f + 0.3f * (0.75f - x.energy); continue; }
            if (i == n - 1 && n > 2 && (x.energy < 0.8f || x.slope < -0.15f)) { x.kind = Analysis.OUTRO; x.confidence = 0.55f; continue; }
            if (x.slope > 0.16f && nx != null && nx.energy > x.energy * 1.12f) {
                x.kind = Analysis.BUILD; x.confidence = Math.min(0.95f, 0.45f + x.slope * 2f); continue;
            }
            if (x.energy >= 0.8f && x.lowRatio >= medianLow * 1.03f && p != null && (p.kind == Analysis.BUILD || p.energy < x.energy * 0.75f)) {
                x.kind = Analysis.DROP; x.confidence = Math.min(0.95f, 0.5f + (x.energy - (p.energy)) * 0.8f + (p.kind == Analysis.BUILD ? 0.2f : 0f)); continue;
            }
            if (x.energy >= 0.68f && (repeats >= 2 || x.energy >= 0.86f)) {
                x.kind = Analysis.CHORUS; x.confidence = Math.min(0.9f, 0.4f + 0.15f * repeats + (x.energy - 0.68f)); continue;
            }
            if (p != null && x.energy < p.energy * 0.65f) { x.kind = Analysis.BREAK; x.confidence = Math.min(0.9f, 0.5f + (p.energy - x.energy)); continue; }
            x.kind = Analysis.VERSE;
            x.confidence = 0.4f + 0.1f * Math.min(3, repeats);
        }
    }
}
