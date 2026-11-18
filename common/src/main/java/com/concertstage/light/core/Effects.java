package com.concertstage.light.core;

import java.util.Map;

/**
 * The effects engine: one computation per effect per frame, shared by all its fixtures, each fixture with its own phase offset (spread,
 * direction, symmetry). Position effects add an offset to pan / tilt, intensity effects scale the dimmer, colour chases replace the
 * colour, pixel effects modulate the emitter zones. {@code weight} fades the effect in (cue fade, playback fader).
 */
public final class Effects {
    private Effects() {}

    private static final double TAU = Math.PI * 2;

    public static void apply(Show.Effect e, float weight, double seconds, double beats, Show show, Map<Integer, Engine.Output> outputs) {
        if (weight <= 0.0001f || e.fids.length == 0) return;
        double cycles = e.bpmSync ? beats / Math.max(0.0625, e.rate) : seconds * e.rate;
        int n = e.fids.length;
        float amp = e.amplitude * weight;
        for (int k = 0; k < n; k++) {
            Engine.Output o = outputs.get(e.fids[k]);
            if (o == null) continue;
            double pos = n <= 1 ? 0 : k / (double) (n - 1);              // 0..1 along the fixture list
            double rel = e.symmetric ? Math.abs(pos * 2 - 1) : pos;        // symmetric: from the middle outwards
            double phase = e.direction * cycles + e.spread * rel * (n <= 1 ? 0 : (e.symmetric ? 1.0 : (n - 1) / (double) n));
            double frac = phase - Math.floor(phase);
            float[] v = o.v;
            FixtureProfile pr = o.profile;
            switch (e.type) {
                case Show.Effect.CIRCLE -> {
                    float a = amp * 0.25f;
                    if (pr.has(Attr.PAN)) v[Attr.PAN.ordinal()] += (float) (a * Math.cos(TAU * phase)) * 180f / Math.max(1f, pr.panRange);
                    if (pr.has(Attr.TILT)) v[Attr.TILT.ordinal()] += (float) (a * Math.sin(TAU * phase)) * 180f / Math.max(1f, pr.tiltRange);
                }
                case Show.Effect.FAN -> {
                    double side = n <= 1 ? 0 : pos * 2 - 1;                // -1 .. 1 across the fixtures
                    double breathe = e.rate <= 0.011f ? 1.0 : 0.5 + 0.5 * Math.sin(TAU * (e.bpmSync ? beats / Math.max(0.0625, e.rate) : seconds * e.rate));
                    if (pr.has(Attr.PAN)) v[Attr.PAN.ordinal()] += (float) (amp * 90f * side * breathe / Math.max(1f, pr.panRange));
                    else if (pr.has(Attr.TILT)) v[Attr.TILT.ordinal()] += (float) (amp * 60f * side * breathe / Math.max(1f, pr.tiltRange));
                }
                case Show.Effect.SWEEP -> {
                    double tri = frac < 0.5 ? frac * 2 : 2 - frac * 2;
                    float deg = (float) ((tri * 2 - 1) * amp * 90f);
                    if (pr.has(Attr.PAN)) v[Attr.PAN.ordinal()] += deg / Math.max(1f, pr.panRange);
                    else if (pr.has(Attr.TILT)) v[Attr.TILT.ordinal()] += deg / Math.max(1f, pr.tiltRange);
                }
                case Show.Effect.WAVE -> v[Attr.DIMMER.ordinal()] *= (float) ((1 - amp) + amp * (0.5 + 0.5 * Math.sin(TAU * phase)));
                case Show.Effect.CHASE -> v[Attr.DIMMER.ordinal()] *= frac < Math.max(0.02f, e.duty) ? 1f : 1f - amp;
                case Show.Effect.PULSE -> {
                    double decay = Math.exp(-frac / Math.max(0.03, e.duty * 0.5));
                    v[Attr.DIMMER.ordinal()] *= (float) ((1 - amp) + amp * decay);
                }
                case Show.Effect.DIMMER_CHASE -> {
                    // a soft bump travelling over the fixtures, its width set by the duty cycle
                    double bump = Math.pow(Math.max(0, 1 - Math.abs(frac - 0.5) * 2 / Math.max(0.05, e.duty)), 2);
                    v[Attr.DIMMER.ordinal()] *= (float) ((1 - amp) + amp * bump);
                }
                case Show.Effect.COLOR_CHASE -> {
                    if (e.colors.length == 0) break;
                    double step = frac * e.colors.length;
                    int i0 = (int) Math.floor(step) % e.colors.length, i1 = (i0 + 1) % e.colors.length;
                    double edge = Math.max(0.0, (step - Math.floor(step) - (1 - Math.max(0.05, 1 - e.duty))) / Math.max(0.05, 1 - e.duty));
                    float t = (float) Math.min(1, edge);
                    float r = lerp(ch(e.colors[i0], 16), ch(e.colors[i1], 16), t);
                    float g = lerp(ch(e.colors[i0], 8), ch(e.colors[i1], 8), t);
                    float b = lerp(ch(e.colors[i0], 0), ch(e.colors[i1], 0), t);
                    float w = Math.min(1f, weight);
                    v[Attr.RED.ordinal()] = lerp(v[Attr.RED.ordinal()], r, w);
                    v[Attr.GREEN.ordinal()] = lerp(v[Attr.GREEN.ordinal()], g, w);
                    v[Attr.BLUE.ordinal()] = lerp(v[Attr.BLUE.ordinal()], b, w);
                    if (pr.has(Attr.WHITE)) v[Attr.WHITE.ordinal()] = lerp(v[Attr.WHITE.ordinal()], 0f, w);
                }
                case Show.Effect.PIXEL -> {
                    int nz = pr.zones;
                    if (nz == 0) { v[Attr.DIMMER.ordinal()] *= (float) ((1 - amp) + amp * (0.5 + 0.5 * Math.sin(TAU * phase))); break; }
                    for (int z = 0; z < nz; z++) {
                        double zp = phase + e.spread * z / (double) nz;
                        double zf = zp - Math.floor(zp);
                        double on = zf < Math.max(0.04, e.duty) ? 1.0 : 0.0;
                        double smooth = 0.5 + 0.5 * Math.cos(TAU * zf);
                        double lvl = (1 - amp) + amp * (e.duty >= 0.5 ? smooth : on);
                        int i = z * 4 + 3;
                        float cur = Float.isNaN(o.zones[i]) ? 1f : o.zones[i];
                        o.zones[i] = (float) (cur * lvl);
                    }
                }
                // ---- rev. 24
                case Show.Effect.FIGURE8 -> {
                    float a = amp * 0.25f;
                    if (pr.has(Attr.PAN)) v[Attr.PAN.ordinal()] += (float) (a * Math.sin(TAU * phase)) * 180f / Math.max(1f, pr.panRange);
                    if (pr.has(Attr.TILT)) v[Attr.TILT.ordinal()] += (float) (a * 0.6 * Math.sin(2 * TAU * phase)) * 180f / Math.max(1f, pr.tiltRange);
                }
                case Show.Effect.TILT_WAVE -> {
                    if (pr.has(Attr.TILT)) v[Attr.TILT.ordinal()] += (float) (amp * 45f * Math.sin(TAU * phase)) / Math.max(1f, pr.tiltRange);
                    else v[Attr.DIMMER.ordinal()] *= (float) ((1 - amp) + amp * (0.5 + 0.5 * Math.sin(TAU * phase)));
                }
                case Show.Effect.BALLYHOO -> {
                    // smooth wandering: two incommensurate sines per fixture, different for each fixture (its place in the list)
                    double seed = e.fids[k] * 0.618;
                    double px = Math.sin(TAU * (phase + seed)) * 0.7 + Math.sin(TAU * (phase * 1.73 + seed * 2.1)) * 0.3;
                    double ty = Math.sin(TAU * (phase * 1.31 + seed * 1.7)) * 0.7 + Math.sin(TAU * (phase * 2.27 + seed)) * 0.3;
                    if (pr.has(Attr.PAN)) v[Attr.PAN.ordinal()] += (float) (amp * 70f * px) / Math.max(1f, pr.panRange);
                    if (pr.has(Attr.TILT)) v[Attr.TILT.ordinal()] += (float) (amp * 35f * ty) / Math.max(1f, pr.tiltRange);
                }
                case Show.Effect.RAINBOW -> {
                    if (!pr.has(Attr.RED)) break;
                    float[] rgb = hue((float) frac);
                    float w = Math.min(1f, weight) * Math.min(1f, e.amplitude * 1.5f);
                    v[Attr.RED.ordinal()] = lerp(v[Attr.RED.ordinal()], rgb[0], w);
                    v[Attr.GREEN.ordinal()] = lerp(v[Attr.GREEN.ordinal()], rgb[1], w);
                    v[Attr.BLUE.ordinal()] = lerp(v[Attr.BLUE.ordinal()], rgb[2], w);
                    if (pr.has(Attr.WHITE)) v[Attr.WHITE.ordinal()] = lerp(v[Attr.WHITE.ordinal()], 0f, w);
                }
                case Show.Effect.SPARKLE -> {
                    // each fixture on or dimmed at random, a new draw four times per cycle (the same on every client: hashed)
                    long step = (long) Math.floor(cycles * 4);
                    boolean on = hash(e.fids[k], step) < Math.max(0.1f, e.duty);
                    v[Attr.DIMMER.ordinal()] *= on ? 1f : 1f - amp;
                }
                case Show.Effect.RANDOM_STROBE -> {
                    // one short flash per step on a few fixtures drawn at random, dark in between (a step = a quarter cycle)
                    double c4 = cycles * 4;
                    long step = (long) Math.floor(c4);
                    boolean mine = hash(e.fids[k], step) < Math.max(0.1f, e.duty * 0.6f);
                    boolean flash = c4 - step < 0.3;
                    v[Attr.DIMMER.ordinal()] *= mine && flash ? 1f : 1f - amp;
                }
                case Show.Effect.ZOOM_PULSE -> {
                    double wave = 0.5 + 0.5 * Math.sin(TAU * phase);
                    if (pr.has(Attr.ZOOM)) v[Attr.ZOOM.ordinal()] = (float) (v[Attr.ZOOM.ordinal()] * (1 - amp) + amp * wave);
                    else v[Attr.DIMMER.ordinal()] *= (float) ((1 - amp * 0.5) + amp * 0.5 * wave);
                }
                case Show.Effect.ALTERNATE -> {
                    // odd / even fixtures in turn
                    boolean odd = (k & 1) == 1;
                    boolean first = (cycles - Math.floor(cycles)) < 0.5;
                    v[Attr.DIMMER.ordinal()] *= odd == first ? 1f : 1f - amp;
                }
                default -> { }
            }
        }
    }

    /** Rev. 24: a value in [0, 1) that is the same for a fixture and a step on every machine. */
    static float hash(int fid, long step) {
        long h = fid * 0x9E3779B97F4A7C15L + step * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 29;
        return (h >>> 40) / (float) (1L << 24);
    }

    /** Rev. 24: a fully saturated colour around the colour wheel (0..1). */
    static float[] hue(float t) {
        float h = (t - (float) Math.floor(t)) * 6f;
        int i = (int) h;
        float f = h - i;
        return switch (i) {
            case 0 -> new float[] {1f, f, 0f};
            case 1 -> new float[] {1f - f, 1f, 0f};
            case 2 -> new float[] {0f, 1f, f};
            case 3 -> new float[] {0f, 1f - f, 1f};
            case 4 -> new float[] {f, 0f, 1f};
            default -> new float[] {1f, 0f, 1f - f};
        };
    }

    static float ch(int rgb, int shift) {
        return ((rgb >> shift) & 255) / 255f;
    }

    static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
