package com.concertstage.light.dmx;

import com.concertstage.light.core.Attr;
import com.concertstage.light.core.FixtureProfile;
import com.concertstage.light.core.Show;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * CSC VIRTUAL MATRIX: 16 DMX channels drive every fixture patched in the {@link DmxMode#MATRIX} mode. The desk sends master
 * parameters; CSC spreads them over the fixtures with fixed rules. It is a pure function of (channel values, fixture order, clock):
 * the same values at the same time always give the same light, on the server and on every client.
 *
 * <pre>
 *  1  Intensity            0..255 = 0..100 %
 *  2-4  Colour A (R G B)   main colour
 *  5-7  Colour B (R G B)   second colour (alternate, chase, gradient)
 *  8  Speed                0 = stopped, 255 = 4 cycles / s (quadratic: fine control of slow speeds)
 *  9  Movement             0-31 none · 32-63 circle · 64-95 pan sweep · 96-127 tilt sweep · 128-159 figure eight ·
 *                          160-191 fan (static spread) · 192-223 random positions (held one cycle) · 224-255 ballyhoo
 * 10  Movement size        0..255 = 0..±45° (pan) / ±35° (tilt) around the centre
 * 11  Direction            0-63 forward · 64-127 reverse · 128-191 forward mirrored · 192-255 reverse mirrored (from the middle out)
 * 12  Effect               0-31 colour A · 32-63 A / B alternate · 64-95 colour chase A → B · 96-127 dimmer chase ·
 *                          128-159 dimmer wave · 160-191 rainbow · 192-223 sparkle · 224-255 gradient A → B along the line
 * 13  Phase spread         0 = all together, 255 = one full cycle across the line
 * 14  Strobe               as the fixture strobe channel (0-7 open)
 * 15  Tilt centre          128 = neutral
 * 16  Pan centre           128 = neutral
 * </pre>
 * Fixture order along the "line": {@link Show.External#matrixOrder} (fixture number, or position east-west / north-south).
 */
public final class VirtualMatrix {
    private VirtualMatrix() {}

    public static final int CHANNELS = 16;
    public static final String[] CHANNEL_NAMES = {"Intensity", "Colour A red", "Colour A green", "Colour A blue", "Colour B red",
        "Colour B green", "Colour B blue", "Speed", "Movement", "Movement size", "Direction", "Effect", "Phase spread", "Strobe",
        "Tilt centre", "Pan centre"};
    public static final String[] MOVEMENTS = {"None", "Circle", "Pan sweep", "Tilt sweep", "Figure eight", "Fan", "Random", "Ballyhoo"};
    public static final String[] EFFECTS = {"Colour A", "A / B alternate", "Colour chase", "Dimmer chase", "Dimmer wave", "Rainbow",
        "Sparkle", "Gradient"};

    /** Fixtures of the show that follow the matrix, in matrix order. */
    public static List<Show.Patch> members(Show show) {
        List<Show.Patch> m = new ArrayList<>();
        for (Show.Patch p : show.patch) if (p.dmxMode == DmxMode.MATRIX) m.add(p);
        Comparator<Show.Patch> c = switch (show.external.matrixOrder) {
            case Show.External.ORDER_X -> Comparator.<Show.Patch>comparingInt(p -> p.x).thenComparingInt(p -> p.z).thenComparingInt(p -> p.fid);
            case Show.External.ORDER_Z -> Comparator.<Show.Patch>comparingInt(p -> p.z).thenComparingInt(p -> p.x).thenComparingInt(p -> p.fid);
            default -> Comparator.comparingInt(p -> p.fid);
        };
        m.sort(c);
        return m;
    }

    /**
     * Values of member {@code index} of {@code count} at {@code nowMs}, written into {@code v} (one per {@link Attr}) from the 16
     * channel values {@code ch} (0..255). Returns false when the channels are missing.
     */
    public static void values(FixtureProfile pr, int[] ch, int index, int count, long nowMs, float[] v) {
        float intensity = ch[0] / 255f;
        float ar = ch[1] / 255f, ag = ch[2] / 255f, ab = ch[3] / 255f, br = ch[4] / 255f, bg = ch[5] / 255f, bb = ch[6] / 255f;
        double speed = 4.0 * Math.pow(ch[7] / 255.0, 2);
        int movement = ch[8] / 32, effect = ch[11] / 32, dirSel = ch[10] / 64;
        float size = ch[9] / 255f;
        boolean reverse = dirSel == 1 || dirSel == 3, mirror = dirSel >= 2;
        double spread = ch[12] / 255.0;
        double pos = count <= 1 ? 0 : index / (double) (count - 1);
        double rel = mirror ? Math.abs(pos * 2 - 1) : pos;
        double cycles = speed * nowMs / 1000.0;
        double phase = (reverse ? -1 : 1) * cycles + spread * rel;
        double frac = phase - Math.floor(phase);
        // ---- position
        float panC = (ch[15] - 128) / 255f, tiltC = (ch[14] - 128) / 255f;
        double dp = 0, dt = 0;                                       // degrees
        double ap = 45 * size, at = 35 * size, tau = Math.PI * 2;
        switch (movement) {
            case 1 -> { dp = ap * Math.cos(tau * phase); dt = at * Math.sin(tau * phase); }
            case 2 -> dp = ap * Math.sin(tau * phase);
            case 3 -> dt = at * Math.sin(tau * phase);
            case 4 -> { dp = ap * Math.sin(tau * phase); dt = at * Math.sin(2 * tau * phase); }
            case 5 -> dp = ap * (count <= 1 ? 0 : pos * 2 - 1) * (mirror ? -1 : 1) * (reverse ? -1 : 1);
            case 6 -> {
                long step = (long) Math.floor(phase);
                dp = ap * (hash(index, step, 1) * 2 - 1);
                dt = at * (hash(index, step, 2) * 2 - 1);
            }
            case 7 -> { dp = ap * Math.sin(tau * phase) * Math.cos(tau * phase * 0.5); dt = at * Math.abs(Math.sin(tau * phase * 1.5)) - at * 0.5; }
            default -> { }
        }
        if (pr.hasPan()) v[Attr.PAN.ordinal()] = clamp01(0.5f + panC * 1.0f + (float) dp / Math.max(1f, pr.panRange));
        v[Attr.TILT.ordinal()] = clamp01(0.5f + tiltC * 1.0f + (float) dt / Math.max(1f, pr.tiltRange));
        // ---- colour and intensity
        float r = ar, g = ag, b = ab, dim = intensity;
        switch (effect) {
            case 1 -> { if (((index + (long) Math.floor(cycles)) & 1) == 1) { r = br; g = bg; b = bb; } }
            case 2 -> {
                float t = (float) (0.5 + 0.5 * Math.sin(tau * phase));
                r = lerp(ar, br, t); g = lerp(ag, bg, t); b = lerp(ab, bb, t);
            }
            case 3 -> dim *= (float) Math.pow(Math.max(0, 1 - Math.abs(frac - 0.5) * 4), 2);
            case 4 -> dim *= (float) (0.5 + 0.5 * Math.sin(tau * phase));
            case 5 -> { float[] c = hsv((float) frac); r = c[0]; g = c[1]; b = c[2]; }
            case 6 -> dim *= hash(index, (long) Math.floor(cycles * 8), 3) > 0.7 ? 1f : 0.08f;
            case 7 -> { float t = (float) rel; r = lerp(ar, br, t); g = lerp(ag, bg, t); b = lerp(ab, bb, t); }
            default -> { }
        }
        v[Attr.DIMMER.ordinal()] = clamp01(dim);
        v[Attr.RED.ordinal()] = r; v[Attr.GREEN.ordinal()] = g; v[Attr.BLUE.ordinal()] = b;
        if (pr.has(Attr.WHITE)) v[Attr.WHITE.ordinal()] = pr.whiteZones > 0 && pr.rgbZones == 0 ? Math.max(r, Math.max(g, b)) : 0f;
        if (pr.has(Attr.LASER_PATTERN)) {
            v[Attr.LASER_PATTERN.ordinal()] = (effect % 5) / 5f;
            v[Attr.LASER_SIZE.ordinal()] = size;
            v[Attr.LASER_SPEED.ordinal()] = ch[7] / 255f;
        }
        v[Attr.STROBE.ordinal()] = DmxMode.decode(pr, Attr.STROBE, ch[13], false);
    }

    /** The 16 channel values of the matrix of a show, or null when its universe has no data. */
    public static int[] channels(Show show, DmxData dmx) {
        byte[] u = dmx.get(show.external.matrixUniverse);
        if (u == null) return null;
        int[] c = new int[CHANNELS];
        for (int i = 0; i < CHANNELS; i++) {
            int slot = show.external.matrixAddress - 1 + i;
            c[i] = slot < 512 ? u[slot] & 255 : (i == 14 || i == 15 ? 128 : 0);
        }
        return c;
    }

    /** Deterministic pseudo random 0..1 from (fixture, step, salt). */
    static float hash(long a, long b, long salt) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
        h ^= h >>> 33; h *= 0xFF51AFD7ED558CCDL; h ^= h >>> 33; h *= 0xC4CEB9FE1A85EC53L; h ^= h >>> 33;
        return (h >>> 40) / (float) (1L << 24);
    }

    static float[] hsv(float h) {
        float x = h * 6f;
        int i = (int) Math.floor(x) % 6;
        float f = x - (float) Math.floor(x);
        return switch (i) {
            case 0 -> new float[] {1, f, 0};
            case 1 -> new float[] {1 - f, 1, 0};
            case 2 -> new float[] {0, 1, f};
            case 3 -> new float[] {0, 1 - f, 1};
            case 4 -> new float[] {f, 0, 1};
            default -> new float[] {1, 0, 1 - f};
        };
    }

    static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    static float clamp01(float v) {
        return Float.isNaN(v) ? 0f : v < 0 ? 0 : v > 1 ? 1 : v;
    }
}
