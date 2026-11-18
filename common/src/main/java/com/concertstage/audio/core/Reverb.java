package com.concertstage.audio.core;

/**
 * Stereo room reverb (Schroeder / Moorer "Freeverb" structure: 8 damped comb filters and 4 all-pass filters per channel, the right
 * channel slightly detuned). Runs inside the console mix on Minecraft's sound thread, after the processing and before the limiter: it
 * goes through Minecraft's sound engine like the rest of the mix (no OpenAL EFX, no access to Minecraft's sources).
 * {@code wet} 0 = dry (nothing is computed), {@code room} 0..1 = decay, {@code damp} 0..1 = how fast the highs die out. Changes are
 * smoothed per block. A game setting tuned by ear, not a measured room.
 */
public final class Reverb {
    private static final int[] COMBS = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
    private static final int[] ALLPASS = {556, 441, 341, 225};
    private static final int SPREAD = 23;
    private static final float FIXED_GAIN = 0.015f;

    private final float[][] comb = new float[16][];
    private final int[] combPos = new int[16];
    private final float[] combStore = new float[16];
    private final float[][] allpass = new float[8][];
    private final int[] allpassPos = new int[8];
    private float wet, room = 0.5f, damp = 0.5f;
    private boolean silent = true;

    public Reverb(int rate) {
        double scale = rate / 44100.0;
        for (int c = 0; c < 2; c++) {
            for (int i = 0; i < 8; i++) comb[c * 8 + i] = new float[Math.max(1, (int) Math.round((COMBS[i] + c * SPREAD) * scale))];
            for (int i = 0; i < 4; i++) allpass[c * 4 + i] = new float[Math.max(1, (int) Math.round((ALLPASS[i] + c * SPREAD) * scale))];
        }
    }

    public float wet() { return wet; }

    /** Mixes the reverb into the interleaved stereo {@code data} (float samples on the 16-bit scale). */
    public void process(float[] data, int frames, float targetWet, float targetRoom, float targetDamp) {
        targetWet = finite(targetWet, 0, 0, 0.6f);
        targetRoom = finite(targetRoom, 0.5f, 0, 1);
        targetDamp = finite(targetDamp, 0.5f, 0, 1);
        if (targetWet <= 0.001f && wet <= 0.001f) {
            if (!silent) clear();                                        // off: nothing kept ringing, nothing computed
            wet = 0;
            return;
        }
        silent = false;
        float w0 = wet, w1 = targetWet;
        room += (targetRoom - room) * 0.25f;
        damp += (targetDamp - damp) * 0.25f;
        float feedback = 0.70f + room * 0.28f, d1 = damp * 0.4f, d2 = 1 - d1;
        for (int i = 0; i < frames; i++) {
            float w = w0 + (w1 - w0) * (i + 1) / frames;                 // no step in level from one block to the next
            float in = (data[2 * i] + data[2 * i + 1]) * FIXED_GAIN;
            for (int c = 0; c < 2; c++) {
                float out = 0;
                for (int k = 0; k < 8; k++) {
                    int j = c * 8 + k;
                    float[] buf = comb[j];
                    int p = combPos[j];
                    float y = buf[p];
                    combStore[j] = y * d2 + combStore[j] * d1;
                    buf[p] = in + combStore[j] * feedback;
                    combPos[j] = p + 1 == buf.length ? 0 : p + 1;
                    out += y;
                }
                for (int k = 0; k < 4; k++) {
                    int j = c * 4 + k;
                    float[] buf = allpass[j];
                    int p = allpassPos[j];
                    float b = buf[p];
                    buf[p] = out + b * 0.5f;
                    out = b - out;
                    allpassPos[j] = p + 1 == buf.length ? 0 : p + 1;
                }
                data[2 * i + c] += out * w * 3f;
            }
        }
        wet = w1;
        if (Math.abs(combStore[0]) < 1e-12f) combStore[0] = 0;
    }

    public void clear() {
        for (float[] b : comb) java.util.Arrays.fill(b, 0);
        for (float[] b : allpass) java.util.Arrays.fill(b, 0);
        java.util.Arrays.fill(combStore, 0);
        silent = true;
    }

    private static float finite(float v, float fallback, float min, float max) {
        return Float.isFinite(v) ? Math.max(min, Math.min(max, v)) : fallback;
    }
}
