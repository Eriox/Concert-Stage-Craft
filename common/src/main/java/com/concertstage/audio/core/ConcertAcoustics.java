package com.concertstage.audio.core;

/**
 * What makes a PA sound like a real concert and not like a hi-fi in the room (1.1.0-aurex.2). Runs in the console mix on the sound thread,
 * after the system processing and before the room reverb and the limiter. Every part is cheap (no transcendental function per sample).
 * <ul>
 * <li><b>Echo of the place</b>: outdoors, the slap-back of the buildings and slopes around the audience (one late, dull repeat at
 * 2 × distance / 343 m/s, a little wider in stereo); indoors, the first reflections of the walls before the reverb tail. The client
 * measures the distances (SoundSpace) and sets {@link #echo}. Delay changes glide (no click).</li>
 * <li><b>Glue</b>: the slow bus compression of a front-of-house mix (2:1 above −20 dBFS, 30 ms / 300 ms, gain computed every 32
 * samples) with its make-up, and a soft saturation of the peaks: the dense, even level of a show.</li>
 * <li><b>Concert bass</b>: what a sub does to the chest cannot come out of headphones or a laptop; the second and third harmonics of
 * the 35–110 Hz band are added (80–330 Hz), the ear hears the fundamental again.</li>
 * <li><b>Wind</b>: far from the stage in the open air the highs come and go with the wind; a slow random dulling, more with the
 * distance (set by the client, {@link #wind}).</li>
 * </ul>
 * Game settings tuned by ear, not acoustic measurements.
 */
public final class ConcertAcoustics {
    private static final float MAX_ECHO_S = 0.75f;
    private final int rate;
    private final float[] ring;
    private int pos;
    private float delayNow = -1;
    private float echoGainNow;
    private final SpeakerDsp.Biquad[] echoLow = {new SpeakerDsp.Biquad(), new SpeakerDsp.Biquad()};
    private final SpeakerDsp.Biquad[] echoHigh = {new SpeakerDsp.Biquad(), new SpeakerDsp.Biquad()};
    private final SpeakerDsp.Biquad[] bassBand = {new SpeakerDsp.Biquad(), new SpeakerDsp.Biquad()};
    private final SpeakerDsp.Biquad[] bassLow = {new SpeakerDsp.Biquad(), new SpeakerDsp.Biquad()};
    private final SpeakerDsp.Biquad[] harmLow = {new SpeakerDsp.Biquad(), new SpeakerDsp.Biquad()};
    private final SpeakerDsp.Biquad[] harmHigh = {new SpeakerDsp.Biquad(), new SpeakerDsp.Biquad()};
    private final SpeakerDsp.Biquad[] windLow = {new SpeakerDsp.Biquad(), new SpeakerDsp.Biquad()};
    private double envelope, glueGain = 1;
    private double windMix, windTarget;
    private int windCountdown;
    private long seed = 0x9E3779B97F4A7C15L;

    private volatile boolean enabled;                   // off until the client turns it on (ConsoleMixer.concert)
    private volatile float echoMs, echoGain, glue = .5f, bass = .5f, wind;

    public ConcertAcoustics(int rate) {
        this.rate = rate;
        ring = new float[(int) (rate * MAX_ECHO_S + 4) * 2];
        for (int c = 0; c < 2; c++) {
            echoLow[c].lowPass(rate, 2800, .707);          // a far surface sends back the mids, not the highs
            echoHigh[c].highPass(rate, 180, .707);         // nor the deep lows (they go round it)
            bassBand[c].highPass(rate, 35, .707);
            bassLow[c].lowPass(rate, 110, .707);
            harmLow[c].highPass(rate, 80, .707);
            harmHigh[c].lowPass(rate, 330, .707);
            windLow[c].lowPass(rate, 3500, .707);
        }
    }

    /** On / off as a whole (off: nothing computed, the mix goes through untouched). */
    public void enabled(boolean on) { enabled = on; }

    /** Echo of the place: delay (ms, 0 = none) and level (0..0.5 of the direct sound). */
    public void echo(float ms, float gain) {
        echoMs = Float.isFinite(ms) ? Math.max(0, Math.min(MAX_ECHO_S * 1000 - 5, ms)) : 0;
        echoGain = Float.isFinite(gain) ? Math.max(0, Math.min(.5f, gain)) : 0;
    }

    /** Glue (0..1) and concert bass (0..1). */
    public void style(float glue, float bass) {
        this.glue = Float.isFinite(glue) ? Math.max(0, Math.min(1, glue)) : 0;
        this.bass = Float.isFinite(bass) ? Math.max(0, Math.min(1, bass)) : 0;
    }

    /** Wind (0 = none .. 1 = far in the open air). */
    public void wind(float amount) {
        wind = Float.isFinite(amount) ? Math.max(0, Math.min(1, amount)) : 0;
    }

    /** 1.1.1 audio fix: the program the glue listens to (stereo 16-bit, the frames of the next {@link #process}); null = the mix. */
    private short[] key;

    public void key(short[] program) {
        key = program;
    }

    public void process(float[] data, int frames) {
        if (!enabled) { delayNow = -1; return; }
        final float eMs = echoMs, eGain = echoGain, gl = glue, bs = bass, wd = wind;
        final int half = ring.length / 2;
        float targetDelay = eMs * rate / 1000f;
        if (delayNow < 0) delayNow = targetDelay;
        // glue: 2:1 above -20 dBFS (6553 on the 16-bit scale), make-up up to +3 dB, soft knee on the peaks
        final double thr = 6553.6, attack = 1 - Math.exp(-32.0 / (rate * .030)), release = 1 - Math.exp(-32.0 / (rate * .300));
        final double makeup = 1 + .41 * gl;
        for (int i = 0; i < frames; i++) {
            double l = data[2 * i], r = data[2 * i + 1];
            // --- concert bass: harmonics of the low band (|x| doubles the frequency, x·|x| adds the third)
            if (bs > 0.001f) {
                for (int c = 0; c < 2; c++) {
                    double x = c == 0 ? l : r;
                    double low = bassLow[c].run(bassBand[c].run(x)) / 32768.0;
                    double h = Math.abs(low) * 1.6 + low * Math.abs(low) * 2.2;
                    h = harmHigh[c].run(harmLow[c].run(h)) * 32768.0 * .55 * bs;
                    if (c == 0) l += h; else r += h;
                }
            }
            // --- echo of the place (delay glides toward its target: no click when the listener walks)
            ring[2 * pos] = (float) l; ring[2 * pos + 1] = (float) r;
            if (eGain > 0.001f || echoGainNow > 0.001f) {
                delayNow += (targetDelay - delayNow) * 0.0005f;
                echoGainNow += (eGain - echoGainNow) * 0.0005f;
                float d = Math.max(1, Math.min(half - 2, delayNow));
                int di = (int) d; float frac = d - di;
                int a = (pos - di + half) % half, b = (a - 1 + half) % half;
                double el = ring[2 * a] * (1 - frac) + ring[2 * b] * frac;
                double er = ring[2 * a + 1] * (1 - frac) + ring[2 * b + 1] * frac;
                // crossed a little: the reflection comes from around, not from the stage
                double xl = echoLow[0].run(echoHigh[0].run(.7 * el + .3 * er)), xr = echoLow[1].run(echoHigh[1].run(.3 * el + .7 * er));
                l += xl * echoGainNow; r += xr * echoGainNow;
            } else {
                delayNow = targetDelay;
            }
            pos = pos + 1 == half ? 0 : pos + 1;
            // --- wind: slow random dulling of the highs far in the open air
            if (wd > 0.001f) {
                if (--windCountdown <= 0) {
                    seed = seed * 6364136223846793005L + 1442695040888963407L;
                    windTarget = ((seed >>> 40) / (double) (1L << 24)) * .55 * wd;
                    windCountdown = rate / 2 + (int) ((seed >>> 20) & 0xFFFF) % rate;
                }
                windMix += (windTarget - windMix) * 0.00004;
                double dl = windLow[0].run(l), dr = windLow[1].run(r);
                l += (dl - l) * windMix; r += (dr - r) * windMix;
            } else if (windMix > 0) {
                windMix = 0;
            }
            // --- glue (gain worked out every 32 samples) and soft peaks
            if (gl > 0.001f) {
                if ((i & 31) == 0) {
                    // 1.1.1 audio fix: the glue listens to the program (the console's stereo track), not to the mix at the listener.
                    // Before, it read the sum after the distance gains: 2:1 above -20 dBFS halved every level change while walking
                    // near the PA (10 dB closer sounded 5 dB louder)
                    short[] kk = key;
                    double peak = kk != null && 2 * i + 1 < kk.length ? Math.max(Math.abs(kk[2 * i]), Math.abs(kk[2 * i + 1])) : Math.max(Math.abs(l), Math.abs(r));
                    envelope += (peak - envelope) * (peak > envelope ? attack : release);
                    double over = envelope / thr;
                    double g = over > 1 ? 1 / Math.sqrt(over) : 1;                     // 2:1 : gain = (env / thr)^(1/2 - 1)
                    glueGain = 1 + (g - 1) * gl;
                }
                double k = glueGain * makeup;
                l = soft(l * k); r = soft(r * k);
            }
            data[2 * i] = (float) l; data[2 * i + 1] = (float) r;
        }
    }

    /** Soft saturation above 0.7 of full scale (linear below): x - x³ curve joined smoothly, never above full scale. */
    private static double soft(double x) {
        double a = Math.abs(x) / 32768.0;
        if (a <= .7) return x;
        double t = Math.min(1, (a - .7) / .6);
        double y = .7 + .3 * (1.5 * t - .5 * t * t * t);
        return Math.copySign(y * 32768.0, x);
    }

    public void clear() {
        java.util.Arrays.fill(ring, 0);
        for (SpeakerDsp.Biquad[] f : new SpeakerDsp.Biquad[][] {echoLow, echoHigh, bassBand, bassLow, harmLow, harmHigh, windLow})
            for (SpeakerDsp.Biquad b : f) b.reset();
        envelope = 0; glueGain = 1; windMix = 0; delayNow = -1; echoGainNow = 0;
    }
}
