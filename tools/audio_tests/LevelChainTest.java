import com.concertstage.audio.AudioGroups;
import com.concertstage.audio.SpeakerModels;
import com.concertstage.audio.core.*;
import java.util.*;

/**
 * 1.1.1 — Test A (level), D (spatialisation) and C (crossover sum) of the sound chain, measured, off game.
 *
 * <p>A festival PA linked to one console (2 hangs of 6 AUREX M12, 2 flown stacks of 3 AUREX S218-F, 4 AUREX M15-W monitors, 2 front
 * fills) is heard from several places. The level of each array at the listener is the 1.1.0 game model (DistanceCurve, Acoustics
 * directivity, the same pan law); the arrays add up with the rule given on the command line ({@code legacy} = 1.1.0: each array
 * divided by the number of arrays; {@code systems} = 1.1.1 ArrayMix). A synthetic master (kick, bass, snare, hats, pad, about -9
 * LUFS, peaks -0.3 dBFS) and pink noise then go through the real ConsoleMixer (group filters, crossover, processing, limiter) and the
 * output is measured with LevelMeter (BS.1770-4).
 *
 * <p>Usage: java LevelChainTest legacy|systems outputGain monitorRangeFactor [wavOut]
 */
public class LevelChainTest {
    static final int RATE = 48000;

    record Array(String name, int group, String model, double x, double y, double z, double yawDeg, int cabinets) {}

    static List<Array> pa() {
        List<Array> a = new ArrayList<>();
        // yaw 0 = facing +z (south, towards the audience); hangs: centres of 6 cabinets 0.6 m high
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 6; i++) a.add(new Array("M12 " + (side < 0 ? "L" : "R") + i, side < 0 ? AudioGroups.MAIN_LEFT : AudioGroups.MAIN_RIGHT, "csc_aurex_m12", side * 12, 11.7 - i * 0.6, 0, 0, 1));
            for (int i = 0; i < 3; i++) a.add(new Array("S218 " + (side < 0 ? "L" : "R") + i, AudioGroups.SUBS, "csc_aurex_s218_f", side * 15, 11.4 - i * 1.15, 0, 0, 1));
        }
        for (int i = 0; i < 4; i++) a.add(new Array("M15 " + i, AudioGroups.MONITOR_1, "csc_aurex_m15_w", -6 + i * 4, 1.2, -1, 180, 1));
        for (int side = -1; side <= 1; side += 2) a.add(new Array("Fill " + side, AudioGroups.FRONT_FILL, "csc_noryx_a1", side * 4, 1.2, 1, 0, 1));
        return a;
    }

    /** The level of an array at the listener: the 1.1.0 ClientAudio#arrayGain with the default settings. */
    static double level(Array a, double ex, double ey, double ez, double monitorFactor) {
        SpeakerModels.Model m = SpeakerModels.get(a.model);
        double dx = ex - a.x, dy = ey - a.y, dz = ez - a.z, dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double range = Acoustics.range(m.isSub(), m.height() * a.cabinets);
        if (m.isMonitor()) range *= monitorFactor;
        if (a.group == AudioGroups.FRONT_FILL) range *= .6;
        DistanceCurve c = new DistanceCurve(12, 192 * range / 192, .65, 1);
        double g = c.gain(dist);
        if (g <= 0) return 0;
        double yr = Math.toRadians(a.yawDeg), fx = -Math.sin(yr), fz = Math.cos(yr);
        // the game's yaw convention: 0 faces south (+z)
        fx = Math.sin(-yr); fz = Math.cos(yr);
        double hl = Math.sqrt(dx * dx + dz * dz);
        double angleH = hl < 1e-6 ? 0 : Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, (dx * fx + dz * fz) / hl))));
        double elevation = Math.toDegrees(Math.atan2(-dy, Math.max(.5, hl)));
        return g * Acoustics.directivity(m.isSub(), angleH, elevation, m.hDispersion(), m.vDispersion(), 0, 0, m.rear());
    }

    static float[] weights(String rule, List<Array> pa, double ex, double ey, double ez, double monitorFactor) {
        List<ArrayMix.Contribution> in = new ArrayList<>();
        double heading = Math.toRadians(180);              // facing the stage (north, -z)
        for (Array a : pa) {
            double l = level(a, ex, ey, ez, monitorFactor);
            if (l <= 0) continue;
            double dx = a.x - ex, dz = a.z - ez;
            double pan = (-Math.cos(heading) * dx - Math.sin(heading) * dz) / Math.sqrt(dx * dx + dz * dz + 1);
            in.add(new ArrayMix.Contribution(a.group, a.model, a.x, a.y, a.z, l, pan));
        }
        return rule.equals("legacy") ? ArrayMix.legacyWeights(in, AudioGroups.COUNT, 1, pa.size()) : ArrayMix.weights(in, AudioGroups.COUNT, 1);
    }

    // ------------------------------------------------------------------ signals
    static short[] music(int seconds) {
        int n = RATE * seconds;
        double[] l = new double[n], r = new double[n];
        Random rnd = new Random(42);
        double spb = RATE * 0.5;                               // 120 BPM
        double hpL = 0, hpPrev = 0, lpBass = 0;
        double[] chord = {220, 277.18, 329.63};
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE, beatPos = (i % spb) / RATE, bar = Math.floor(i / spb);
            double kick = Math.exp(-beatPos * 9) * Math.sin(2 * Math.PI * (48 + 60 * Math.exp(-beatPos * 30)) * beatPos) * 0.9;
            double f = ((int) (bar / 4) % 2 == 0) ? 55 : 73.42;
            double saw = 2 * ((t * f) % 1) - 1;
            lpBass += (saw - lpBass) * 0.02;
            double bass = lpBass * 0.55 * (0.6 + 0.4 * Math.exp(-((i % (spb / 2)) / RATE) * 6));
            double noise = rnd.nextDouble() * 2 - 1;
            double snare = ((int) bar % 2 == 1) ? Math.exp(-beatPos * 18) * (noise * 0.45 + 0.25 * Math.sin(2 * Math.PI * 190 * beatPos)) : 0;
            double hp = noise - hpPrev + 0.95 * hpL; hpPrev = noise; hpL = hp;  // crude high pass
            double hat = Math.exp(-((i % (spb / 2)) / RATE) * 60) * hp * 0.18;
            double pad = 0;
            for (double c : chord) pad += Math.sin(2 * Math.PI * c * t + c) * 0.07;
            double mono = kick + bass + snare;
            l[i] = mono + hat * 1.1 + pad * (1 + 0.3 * Math.sin(t));
            r[i] = mono + hat * 0.9 + pad * (1 - 0.3 * Math.sin(t));
        }
        // "mastering": soft saturation, then peaks at -0.3 dBFS
        double peak = 0;
        for (int i = 0; i < n; i++) { l[i] = Math.tanh(l[i] * 1.6); r[i] = Math.tanh(r[i] * 1.6); peak = Math.max(peak, Math.max(Math.abs(l[i]), Math.abs(r[i]))); }
        double k = 32767 * Math.pow(10, -0.3 / 20) / peak;
        short[] s = new short[n * 2];
        for (int i = 0; i < n; i++) { s[2 * i] = (short) Math.round(l[i] * k); s[2 * i + 1] = (short) Math.round(r[i] * k); }
        return s;
    }

    static short[] pink(int seconds) {
        int n = RATE * seconds;
        Random rnd = new Random(7);
        double[] b = new double[7], c = new double[7];
        double[] x = new double[n * 2];
        double peak = 0;
        for (int i = 0; i < n; i++) for (int ch = 0; ch < 2; ch++) {
            double[] s = ch == 0 ? b : c, w = {rnd.nextGaussian()};
            s[0] = 0.99886 * s[0] + w[0] * 0.0555179; s[1] = 0.99332 * s[1] + w[0] * 0.0750759; s[2] = 0.96900 * s[2] + w[0] * 0.1538520;
            s[3] = 0.86650 * s[3] + w[0] * 0.3104856; s[4] = 0.55000 * s[4] + w[0] * 0.5329522; s[5] = -0.7616 * s[5] - w[0] * 0.0168980;
            double v = s[0] + s[1] + s[2] + s[3] + s[4] + s[5] + s[6] + w[0] * 0.5362; s[6] = w[0] * 0.115926;
            x[i * 2 + ch] = v; peak = Math.max(peak, Math.abs(v));
        }
        short[] out = new short[n * 2];
        double k = 32767 * Math.pow(10, -1.0 / 20) / peak;
        for (int i = 0; i < n * 2; i++) out[i] = (short) Math.round(x[i] * k);
        return out;
    }

    static LevelMeter measure(short[] s) {
        LevelMeter m = new LevelMeter(RATE, 32768, true);
        m.add(s, s.length / 2);
        return m;
    }

    /** The whole console mix for these weights, in callbacks of 2048 frames (as the sound thread asks). */
    static short[] mix(short[] in, float[] w, float outputGain, boolean limiter) {
        ConsoleMixer mixer = new ConsoleMixer(RATE, AudioGroups.COUNT);
        mixer.smoothing(60);
        SoundProfile profile = limiter ? SoundProfile.DEFAULT : SoundProfile.DEFAULT.with(8, 0);
        int frames = in.length / 2, block = 2048;
        short[] out = new short[in.length], ib = new short[block * 2], ob = new short[block * 2];
        for (int at = 0; at < frames; at += block) {
            int n = Math.min(block, frames - at);
            System.arraycopy(in, at * 2, ib, 0, n * 2);
            mixer.process(ib, n, w, 0, 0, 0, true, outputGain, profile, 20000, ob);
            System.arraycopy(ob, 0, out, at * 2, n * 2);
        }
        return out;
    }

    public static void main(String[] args) throws Exception {
        String rule = args.length > 0 ? args[0] : "systems";
        float gain = args.length > 1 ? Float.parseFloat(args[1]) : 2.8f;
        double monitorFactor = args.length > 2 ? Double.parseDouble(args[2]) : 1;
        short[] music = music(30), pinkN = pink(20);
        LevelMeter src = measure(music), srcP = measure(pinkN);
        System.out.printf(Locale.ROOT, "SOURCE   music: %.1f LUFS, peak %.1f dBFS, RMS %.1f dBFS | pink: %.1f LUFS, peak %.1f dBFS%n",
            src.integrated(), src.peakDb(), src.rmsDb(), srcP.integrated(), srcP.peakDb());
        if (args.length > 3) writeWav(args[3], music);
        System.out.printf(Locale.ROOT, "RULE %s, output gain x%.2f, monitor range x%.2f%n", rule, gain, monitorFactor);
        List<Array> pa = pa();
        double[][] places = {{0, 1.7, 6}, {0, 1.7, 25}, {0, 1.7, 50}, {0, 1.7, 100}, {0, 1.7, 160}, {-30, 1.7, 20}, {30, 1.7, 20}, {0, 1.7, -15}, {0, 1.7, -4}};
        String[] names = {"front row 6 m", "FOH 25 m", "50 m", "100 m", "160 m", "left side", "right side", "behind the stage", "on stage (monitors)"};
        System.out.println("  place                  | LUFS-I  | peak dBFS | clipped | L/R weight sum (mains) | subs | monitors | limiter >31000 (no limiter)");
        for (int p = 0; p < places.length; p++) {
            float[] w = weights(rule, pa, places[p][0], places[p][1], places[p][2], monitorFactor);
            short[] out = mix(music, w, gain, true);
            short[] raw = mix(music, w, 1, false);
            LevelMeter m = measure(out);
            long over = 0;
            for (short v : raw) if (Math.abs(v) * gain > 31000) over++;
            double mainsL = w[AudioGroups.MAIN_LEFT * 2] + w[AudioGroups.MAIN_RIGHT * 2], mainsR = w[AudioGroups.MAIN_LEFT * 2 + 1] + w[AudioGroups.MAIN_RIGHT * 2 + 1];
            System.out.printf(Locale.ROOT, "  %-22s | %6.1f  | %6.1f    | %7d | %.3f / %.3f            | %.3f | %.3f    | %.2f %%%n", names[p], m.integrated(), m.peakDb(), m.clippedSamples(),
                mainsL, mainsR, w[AudioGroups.SUBS * 2] + w[AudioGroups.SUBS * 2 + 1], w[AudioGroups.MONITOR_1 * 2] + w[AudioGroups.MONITOR_1 * 2 + 1], 100.0 * over / raw.length);
        }
        // left / right image at FOH, 10 m to the side
        float[] wl = weights(rule, pa, -10, 1.7, 25, monitorFactor), wr = weights(rule, pa, 10, 1.7, 25, monitorFactor);
        System.out.printf(Locale.ROOT, "  image: 10 m left of FOH, left/right channel weights %.3f / %.3f; 10 m right %.3f / %.3f%n",
            sumCh(wl, 0), sumCh(wl, 1), sumCh(wr, 0), sumCh(wr, 1));
        // pink noise at FOH
        float[] wf = weights(rule, pa, 0, 1.7, 25, monitorFactor);
        LevelMeter pm = measure(mix(pinkN, wf, gain, true));
        System.out.printf(Locale.ROOT, "  pink noise at FOH: %.1f LUFS, peak %.1f dBFS%n", pm.integrated(), pm.peakDb());
        // more arrays must not make the show quieter (1.1.0: adding monitors lowered the front of house)
        List<Array> more = new ArrayList<>(pa);
        for (int i = 0; i < 8; i++) more.add(new Array("extra monitor " + i, AudioGroups.MONITOR_2, "csc_aurex_m15_w", -8 + i * 2, 1.2, -6, 180, 1));
        float[] w1 = weights(rule, pa, 0, 1.7, 25, monitorFactor), w2 = weights(rule, more, 0, 1.7, 25, monitorFactor);
        System.out.printf(Locale.ROOT, "  FOH mains weight with 8 more monitors on stage: %.3f -> %.3f (%+.1f dB)%n", sumCh(w1, 0), sumCh(w2, 0),
            20 * Math.log10(sumCh(w2, 0) / sumCh(w1, 0)));
    }

    static double sumCh(float[] w, int ch) {
        double s = 0;
        for (int g = 0; g < AudioGroups.COUNT; g++) s += w[g * 2 + ch];
        return s;
    }

    static void writeWav(String path, short[] s) throws java.io.IOException {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream d = new java.io.DataOutputStream(b);
        int bytes = s.length * 2;
        d.writeBytes("RIFF"); d.writeInt(Integer.reverseBytes(36 + bytes)); d.writeBytes("WAVEfmt ");
        d.writeInt(Integer.reverseBytes(16)); d.writeShort(Short.reverseBytes((short) 1)); d.writeShort(Short.reverseBytes((short) 2));
        d.writeInt(Integer.reverseBytes(RATE)); d.writeInt(Integer.reverseBytes(RATE * 4)); d.writeShort(Short.reverseBytes((short) 4)); d.writeShort(Short.reverseBytes((short) 16));
        d.writeBytes("data"); d.writeInt(Integer.reverseBytes(bytes));
        for (short v : s) d.writeShort(Short.reverseBytes(v));
        java.nio.file.Files.write(java.nio.file.Path.of(path), b.toByteArray());
    }
}
