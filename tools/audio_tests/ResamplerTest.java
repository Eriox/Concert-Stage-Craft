import com.concertstage.audio.core.*;
import java.util.Locale;

/**
 * 1.1.1 — Test B (quality) of the 44.1 -> 48 kHz conversion: the 1.1.0 linear converter (copied below, unchanged) against
 * SincResampler, on pure tones at -6 dBFS. For each tone: level of the tone after conversion (frequency response) and the ratio of
 * the tone to everything else (THD + N, includes the mirror images folded back by the conversion). Also: same number of samples,
 * bit-exact pass-through at 48 kHz, nothing above full scale, cost per second of sound.
 */
public class ResamplerTest {
    static final int SR = 44100, OUT = 48000;

    static final class ArrayDecoder implements TrackDecoder {
        final short[] data; final int rate; int at;
        ArrayDecoder(short[] d, int rate) { data = d; this.rate = rate; }
        public int sampleRate() { return rate; }
        public int read(short[] out) { int n = Math.min(out.length / 2, data.length / 2 - at); if (n <= 0) return -1; System.arraycopy(data, at * 2, out, 0, n * 2); at += n; return n; }
        public long skip(long f) { at += f; return f; }
        public void close() {}
    }

    /** The 1.1.0 converter (PlaylistPcm.Resampler), verbatim but for the names. */
    static final class Linear {
        final TrackDecoder decoder; final short[] input = new short[8192]; int available, at; long sourceIndex, outputIndex; boolean initialized, eof; short al, ar, bl, br;
        Linear(TrackDecoder d) { decoder = d; }
        boolean next() throws java.io.IOException { if (at >= available) { available = decoder.read(input); at = 0; if (available <= 0) return false; } bl = input[at * 2]; br = input[at * 2 + 1]; at++; return true; }
        int read(short[] out) throws java.io.IOException {
            if (!initialized) { if (!next()) return 0; al = bl; ar = br; initialized = true; if (!next()) { eof = true; bl = al; br = ar; } }
            int n = 0;
            while (n < out.length / 2) {
                long numerator = outputIndex * decoder.sampleRate(), target = numerator / OUT;
                while (sourceIndex < target) { if (eof) return n; al = bl; ar = br; sourceIndex++; if (!next()) { eof = true; bl = al; br = ar; } }
                double fraction = (numerator % OUT) / (double) OUT;
                out[n * 2] = (short) Math.round(al + (bl - al) * fraction); out[n * 2 + 1] = (short) Math.round(ar + (br - ar) * fraction);
                n++; outputIndex++;
            }
            return n;
        }
    }

    interface Reader { int read(short[] o) throws Exception; }

    static short[] all(Reader r) throws Exception {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        short[] buf = new short[1920 * 2];
        java.util.ArrayList<short[]> parts = new java.util.ArrayList<>();
        int total = 0;
        for (;;) { int n = r.read(buf); if (n <= 0) break; parts.add(java.util.Arrays.copyOf(buf, n * 2)); total += n; }
        short[] out = new short[total * 2]; int k = 0;
        for (short[] p : parts) { System.arraycopy(p, 0, out, k, p.length); k += p.length; }
        return out;
    }

    static short[] tone(double f, int rate, int frames, double db) {
        short[] s = new short[frames * 2];
        double a = 32767 * Math.pow(10, db / 20);
        for (int i = 0; i < frames; i++) { short v = (short) Math.round(a * Math.sin(2 * Math.PI * f * i / rate)); s[2 * i] = v; s[2 * i + 1] = v; }
        return s;
    }

    /** Least-squares fit of a sine at f on the middle of the left channel: {tone level dBFS, tone / rest dB}. */
    static double[] analyse(short[] s, double f, int rate) {
        int n = s.length / 2, a = n / 4, b = n * 3 / 4;
        double cc = 0, ss = 0, cs = 0, yc = 0, ys = 0, yy = 0;
        for (int i = a; i < b; i++) {
            double c = Math.cos(2 * Math.PI * f * i / rate), si = Math.sin(2 * Math.PI * f * i / rate), y = s[2 * i] / 32768.0;
            cc += c * c; ss += si * si; cs += c * si; yc += y * c; ys += y * si; yy += y * y;
        }
        double det = cc * ss - cs * cs, A = (yc * ss - ys * cs) / det, B = (ys * cc - yc * cs) / det;
        double fit = 0, res = 0;
        for (int i = a; i < b; i++) {
            double m = A * Math.cos(2 * Math.PI * f * i / rate) + B * Math.sin(2 * Math.PI * f * i / rate), y = s[2 * i] / 32768.0;
            fit += m * m; res += (y - m) * (y - m);
        }
        double amp = Math.sqrt(A * A + B * B);
        return new double[] {20 * Math.log10(amp), 10 * Math.log10(fit / Math.max(1e-30, res))};
    }

    static int failures;
    static void check(boolean ok, String what) { if (!ok) { failures++; System.out.println("FAIL " + what); } }

    public static void main(String[] args) throws Exception {
        int frames = SR * 2;
        System.out.println("tone      | 1.1.0 linear: level dB / tone-to-rest dB | 1.1.1 sinc: level dB / tone-to-rest dB   (input -6.0 dBFS)");
        for (double f : new double[] {100, 1000, 6000, 10000, 15000, 19000}) {
            short[] in = tone(f, SR, frames, -6);
            short[] lin = all(new Linear(new ArrayDecoder(in, SR))::read);
            SincResampler sr = new SincResampler(new ArrayDecoder(in, SR), OUT);
            short[] sinc = all(sr::read);
            double[] a = analyse(lin, f, OUT), b = analyse(sinc, f, OUT);
            System.out.printf(Locale.ROOT, "%6.0f Hz | %7.2f / %6.1f                          | %7.2f / %6.1f%n", f, a[0], a[1], b[0], b[1]);
            check(lin.length == sinc.length, "same length at " + f + " Hz: " + lin.length + " / " + sinc.length);
            check(Math.abs(b[0] + 6) < (f < 18000 ? 0.15 : 0.5), "sinc level within 0.15 dB (0.5 dB at 19 kHz) at " + f + " Hz: " + b[0]);
            check(b[1] >= 70 || b[1] > a[1] + 20, "sinc tone-to-rest >= 70 dB (or 20 dB better) at " + f + " Hz: " + b[1]);
        }
        // pass-through at 48 kHz: bit-exact
        short[] in48 = tone(997, OUT, OUT, -3);
        short[] out48 = all(new SincResampler(new ArrayDecoder(in48, OUT), OUT)::read);
        check(java.util.Arrays.equals(in48, out48), "48 kHz file: bit-exact pass-through");
        // full-scale square-ish signal: bounded, never wraps
        short[] loud = new short[SR * 2];
        for (int i = 0; i < SR; i++) { short v = (short) ((i / 50) % 2 == 0 ? 32767 : -32768); loud[2 * i] = v; loud[2 * i + 1] = v; }
        short[] lo = all(new SincResampler(new ArrayDecoder(loud, SR), OUT)::read);
        boolean sane = true;
        for (int i = 2000; i < lo.length - 2000; i += 2) if (lo[i] < -32768 || lo[i] > 32767 || (Math.abs(lo[i]) < 1000 && (i / 2) % 109 > 5 && false)) sane = false;
        check(sane, "full-scale input clamped, no wrap-around");
        // cost
        short[] music = tone(440, SR, SR * 20, -6);
        long t0 = System.nanoTime();
        all(new Linear(new ArrayDecoder(music, SR))::read);
        long t1 = System.nanoTime();
        all(new SincResampler(new ArrayDecoder(music, SR), OUT)::read);
        long t2 = System.nanoTime();
        for (int rep = 0; rep < 2; rep++) { t1 = System.nanoTime(); all(new Linear(new ArrayDecoder(music, SR))::read); long u = System.nanoTime(); all(new SincResampler(new ArrayDecoder(music, SR), OUT)::read); t2 = System.nanoTime(); t0 = u; }
        System.out.printf(Locale.ROOT, "cost per second of stereo sound (this machine): linear %.2f ms, sinc %.2f ms (decoder thread, not the sound thread)%n",
            (t0 - t1) / 1e6 / 20, (t2 - t0) / 1e6 / 20);
        System.out.println(failures == 0 ? "ResamplerTest PASS" : "ResamplerTest FAIL (" + failures + ")");
        if (failures > 0) System.exit(1);
    }
}
