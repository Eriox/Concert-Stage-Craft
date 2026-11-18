package com.concertstage.audio.core;

/**
 * Game-side model of how loud one speaker array is at the listener (pure maths, no Minecraft). Simplified on purpose and tuned for
 * gameplay: these are not measurements of real loudspeakers.
 * <ul>
 * <li>distance: full level up to {@link #REF} m; a line array then loses about 3 dB per doubling of distance (cylindrical wave) up to
 * its critical distance (2.5 x its height), then 6 dB per doubling; a sub stack 6 dB per doubling; it fades out to silence near the
 * end of its range;</li>
 * <li>horizontal directivity: full inside the dispersion of the model, then a smooth roll-off over 60° down to its rear level;</li>
 * <li>vertical directivity (line arrays): full between the aim of the top and of the bottom cabinet (+- half the vertical dispersion
 * of one cabinet), then a roll-off over 25°;</li>
 * <li>subwoofers: nearly omnidirectional, a little less behind.</li>
 * </ul>
 */
public final class Acoustics {
    public static final double REF = 12;

    private Acoustics() {}

    public static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** Range of an array (blocks): beyond it, silence. */
    public static double range(boolean sub, double lengthM) {
        return sub ? 112 : Math.min(192, 112 + lengthM * 12);
    }

    public static double distanceGain(double d, boolean sub, double lengthM) {
        double g;
        if (d <= REF) g = 1;
        else {
            double crit = sub ? REF : Math.max(REF, lengthM * 2.5);
            g = d <= crit ? Math.sqrt(REF / d) : Math.sqrt(REF / crit) * (crit / d);
        }
        double r = range(sub, lengthM);
        return g * (1 - smooth((d - 0.75 * r) / (0.25 * r)));
    }

    /**
     * @param angleH horizontal angle between the aim of the array and the listener (degrees, 0..180)
     * @param elevation angle of the listener below the array centre (degrees, positive downwards)
     * @param pitchTop aim of the top cabinet, @param pitchBottom aim of the bottom cabinet (degrees, positive downwards)
     */
    public static double directivity(boolean sub, double angleH, double elevation, double hDisp, double vDisp, double pitchTop,
                                     double pitchBottom, double rear) {
        if (sub) return 1 - (1 - rear) * (1 - Math.cos(Math.toRadians(angleH))) / 2;
        double half = hDisp / 2;
        double h = angleH <= half ? 1 : 1 - (1 - rear) * smooth((angleH - half) / 60);
        if (angleH >= 90) return h;
        double top = Math.min(pitchTop, pitchBottom) - vDisp / 2, bottom = Math.max(pitchTop, pitchBottom) + vDisp / 2;
        double out = elevation < top ? top - elevation : elevation > bottom ? elevation - bottom : 0;
        double v = 1 - 0.65 * smooth(out / 25);
        // in front, the vertical roll-off fades out towards the sides (behind, only the horizontal one counts)
        double w = 1 - smooth((angleH - 60) / 30);
        return h * (1 - w * (1 - v));
    }
}
