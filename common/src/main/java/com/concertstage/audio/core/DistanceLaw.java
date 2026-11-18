package com.concertstage.audio.core;

import com.concertstage.audio.SpeakerModels;

/**
 * 1.1.1 audio fix — how the level of one array falls with the distance to the listener (pure maths, no Minecraft). Game tuning
 * inspired by real loudspeakers, not measurements of a product.
 *
 * <p>Two slopes in dB per doubling of distance, joined smoothly ({@code log2(sqrt(1 + (d / knee)^2))}, so no corner and no jump):
 * <ul>
 * <li>a line array behaves as a column up to its critical distance (about 2.5 x its height): about 3 dB per doubling (cylindrical
 *     wave), then 6 dB per doubling (spherical);</li>
 * <li>a point source (AUREX M12 main, monitor wedge) loses 6 dB per doubling from its near field;</li>
 * <li>a sub stack, coupled to the ground and with no air absorption at its frequencies, 4.5 dB per doubling, and it carries at least
 *     as far as the mains (before: it fell silent at 112 blocks while the high-passed mains went on, so far away there was no bass);</li>
 * <li>very close (inside {@code near}) the level stops rising: at most about +8 dB over the reference distance, no runaway gain
 *     against the cabinet;</li>
 * <li>near the end of its range the array fades to silence (smoothstep over the last quarter): a far array is inaudible and costs
 *     nothing (its group is idle in the mixer).</li>
 * </ul>
 * The level is 1 at {@link #reference}; closer it is a little above 1 (up to the near cap), further below. The curve setting
 * multiplies both slopes (gentle 0.75, realistic 1, strong 1.3).
 *
 * <p>Before this fix the game used one gentle curve for every speaker ({@code (1 + d / 12)^-0.65}): -0.7 dB at 1 block, -13.6 dB at
 * 120 blocks, so walking from 2 to 20 blocks changed the level by only 4 dB, and the output compressor then halved what was left
 * (see ConsoleMixer, the glue now reads the program, not the distance).
 */
public record DistanceLaw(double near, double critical, double slopeNear, double slopeFar, double reference, double range) {
    public static final int LINE = 0, POINT = 1, SUB = 2, MONITOR = 3;

    public DistanceLaw {
        near = Math.max(.25, near);
        critical = Math.max(near, critical);
        reference = Math.max(near, reference);
        range = Math.max(8, range);
    }

    /** The kind of law of a model. */
    public static int kind(SpeakerModels.Model m) {
        if (m == null) return LINE;
        return m.isSub() ? SUB : m.isMonitor() ? MONITOR : m.kind() == SpeakerModels.Kind.MAIN || portable(m) ? POINT : LINE;
    }

    /** Rev. 4: the AUREX C6 portable system (two small modules on a pole: point sources, a small system's reach). */
    static boolean portable(SpeakerModels.Model m) {
        return m != null && (m.family().equals("aurex_c6") || m.family().equals("aurex_portable"));
    }

    /**
     * The law of an array.
     * @param lengthM height of the array (sum of its cabinets, metres = blocks)
     * @param rangeFactor range multiplier (settings: per family, front fills, delays; and the global diffusion distance)
     */
    public static DistanceLaw of(SpeakerModels.Model m, double lengthM, double rangeFactor) {
        double f = Double.isFinite(rangeFactor) ? Math.max(.1, Math.min(4, rangeFactor)) : 1;
        double len = Double.isFinite(lengthM) ? Math.max(0, lengthM) : 0;
        if (portable(m)) return m.isSub() ? new DistanceLaw(1.5, 2, 4.5, 4.5, 4, 112 * f) : new DistanceLaw(2, 2, 6, 6, 4, 96 * f);
        return switch (kind(m)) {
            case SUB -> new DistanceLaw(2, Math.max(2, Math.min(12, 1.5 * len)), 4.5, 4.5, 6, 240 * f);
            case MONITOR -> new DistanceLaw(1.2, 1.2, 6, 6, 2, 128 * f);
            case POINT -> new DistanceLaw(3, 3, 6, 6, 6, 176 * f);
            default -> new DistanceLaw(1.5, Math.max(6, Math.min(40, 2.5 * len)), 3, 6, 6, Math.min(256, 140 + 12 * len) * f);
        };
    }

    private static double knee(double d, double k) {
        double x = d / k;
        return Math.log(Math.sqrt(1 + x * x)) / Math.log(2);
    }

    /** Loss (dB, positive) from the near field to {@code d}, slopes x {@code scale}. */
    public double lossDb(double d, double scale) {
        double s = Double.isFinite(scale) ? Math.max(.25, Math.min(2, scale)) : 1;
        double x = Double.isFinite(d) ? Math.max(0, d) : 0;
        return s * (slopeNear * knee(x, near) + (slopeFar - slopeNear) * knee(x, critical));
    }

    /** Level at {@code d} (1 at the reference distance), with the fade at the end of the range. */
    public double gain(double d, double scale) {
        if (!Double.isFinite(d)) return 0;
        double g = Math.pow(10, (lossDb(reference, scale) - lossDb(d, scale)) / 20);
        return g * (1 - Acoustics.smooth((d - range * .75) / (range * .25)));
    }
}
