package com.concertstage.light.dmx;

import com.concertstage.light.core.Attr;
import com.concertstage.light.core.FixtureProfile;
import com.concertstage.light.core.Show;

/**
 * From received (or recorded) DMX to the values of one fixture, through its DMX mode, then its local transforms. Values are
 * normalised like the programmer (see {@link Attr}); {@code zones} gets r, g, b, level per emitter zone, NaN where the mode does not
 * reach the zones (the engine then makes them follow the fixture colour).
 *
 * <p>Zone channels (FULL mode) merge with the colour of the whole fixture highest-takes-precedence: a cell shows the brighter of the
 * fixture colour and its own channels, so a desk can either colour the whole fixture or, with the main colour at zero, draw pixels.
 * A white segment of the PULSESTRIKE M shows the brighter of the White channel and its own channel.
 */
public final class DmxDecode {
    private DmxDecode() {}

    /**
     * Decodes fixture {@code p} from {@code dmx} into {@code v} (one value per {@link Attr}, untouched where the mode has no channel)
     * and {@code zones} (4 per zone). Returns false when its universe has no data.
     */
    public static boolean decode(Show.Patch p, DmxData dmx, float[] v, float[] zones) {
        FixtureProfile pr = p.profile();
        DmxMode m = DmxMode.of(pr, p.dmxMode);
        byte[] u = dmx.get(p.universe);
        if (u == null || m.mode == DmxMode.MATRIX) return false;
        int base = p.address - 1;
        DmxMode.Channel[] ch = m.channels;
        int[] coarse = new int[Attr.COUNT];
        int[] fine = new int[Attr.COUNT];
        java.util.Arrays.fill(coarse, -1);
        java.util.Arrays.fill(fine, -1);
        int macro = -1, hue = -1, sat = -1, shutterFx = -1, goboFx = -1;
        if (zones != null) java.util.Arrays.fill(zones, Float.NaN);
        float[] zr = zones == null ? null : new float[pr.zones * 4];
        float[] zw = zones == null ? null : new float[pr.zones];
        boolean anyZone = false;
        for (int i = 0; i < ch.length; i++) {
            int slot = base + i;
            if (slot < 0 || slot >= 512) break;                       // a fixture patched across the end of the universe: the rest is missing
            int val = u[slot] & 255;
            DmxMode.Channel c = ch[i];
            switch (c.kind()) {
                case ATTR -> coarse[c.attr().ordinal()] = val;
                case FINE -> fine[c.attr().ordinal()] = val;
                case COLOR_MACRO -> macro = val;
                case HUE -> hue = val;
                case SATURATION -> sat = val;
                case SHUTTER_FX -> shutterFx = val;
                case GOBO_FX -> goboFx = val;
                case ZONE_R, ZONE_G, ZONE_B, ZONE_W, ZONE_LEVEL -> {
                    if (zr == null) break;
                    if (c.kind() == DmxMode.Kind.ZONE_W) {
                        zw[c.zone()] = val / 255f; anyZone = true; break;
                    }
                    int k = c.kind() == DmxMode.Kind.ZONE_R ? 0 : c.kind() == DmxMode.Kind.ZONE_G ? 1 : c.kind() == DmxMode.Kind.ZONE_B ? 2 : 3;
                    zr[c.zone() * 4 + k] = val / 255f;
                    anyZone = true;
                }
            }
        }
        for (Attr a : Attr.ALL) {
            int c = coarse[a.ordinal()];
            if (c < 0) continue;
            int f = fine[a.ordinal()];
            v[a.ordinal()] = f < 0 ? DmxMode.decode(pr, a, c, false) : DmxMode.decode(pr, a, c << 8 | f, true);
        }
        if (macro >= 0) {
            int rgb = DmxMode.macroColor(macro);
            v[Attr.RED.ordinal()] = ((rgb >> 16) & 255) / 255f;
            v[Attr.GREEN.ordinal()] = ((rgb >> 8) & 255) / 255f;
            v[Attr.BLUE.ordinal()] = (rgb & 255) / 255f;
            if (pr.has(Attr.WHITE)) v[Attr.WHITE.ordinal()] = pr.rgbZones == 0 && pr.whiteZones > 0 ? 1f : 0f;
            if (pr.has(Attr.CTO)) v[Attr.CTO.ordinal()] = 0f;
        }
        // rev. 17: packed Compact channels (a channel cut off by the end of the universe stays at the rest values)
        if (hue >= 0 || sat >= 0) DmxMode.hueSat(pr, Math.max(0, hue), Math.max(0, sat), v);
        if (shutterFx >= 0) DmxMode.shutterFx(pr, shutterFx, v, held(p, pr));
        if (goboFx >= 0) {
            DmxMode.goboFx(pr, goboFx, v);
            // 1.2.0 beta 2: the Compact's own rotation channel turns the gobo in the light (wheel 2 when it is a gobo of wheel 2)
            int rc = coarse[Attr.GOBO_ROT.ordinal()];
            if (rc >= 0 && (rc < DmxMode.ROT_STOP_LO || rc > DmxMode.ROT_STOP_HI)) {
                float rot = DmxMode.decode(pr, Attr.GOBO_ROT, rc, false);
                boolean second = pr.has(Attr.GOBO_2) && v[Attr.GOBO_2.ordinal()] > 0f;
                if (second && pr.has(Attr.GOBO_2_ROT)) v[Attr.GOBO_2_ROT.ordinal()] = rot;
                else if (pr.has(Attr.GOBO_ROT)) v[Attr.GOBO_ROT.ordinal()] = rot;
            }
        }
        // rev. 21: the zone layouts have no master dimmer (the zones are the dimmers): the fixture itself is at full
        boolean zoned = pr.hasSections() && (m.mode == DmxMode.FULL || m.mode == DmxMode.FULL_M || m.mode == DmxMode.COMPACT || m.mode == DmxMode.COMPACT_Z3);
        // 1.2.0 beta 2b: the pixel Full has no master dimmer either (each cell is its own intensity)
        boolean pixel = m.mode == DmxMode.FULL && DmxMode.pixelFull(pr);
        if ((zoned || pixel) && coarse[Attr.DIMMER.ordinal()] < 0) v[Attr.DIMMER.ordinal()] = 1f;
        if (pixel) {
            // ... and no fixture colour where the cells have theirs: the cells show their own channels only (not the home white)
            for (Attr a : new Attr[] {Attr.RED, Attr.GREEN, Attr.BLUE}) if (coarse[a.ordinal()] < 0) v[a.ordinal()] = 0f;
            if (pr.rgbwPixels && pr.has(Attr.WHITE) && coarse[Attr.WHITE.ordinal()] < 0) v[Attr.WHITE.ordinal()] = 0f;
            // zone fixtures: no LED bar runs: the bars are at full and add no colour of their own (a cell = its own channels)
            if (pr.hasSections()) for (Attr a : new Attr[] {Attr.TOP_DIM, Attr.BOT_DIM})
                if (coarse[a.ordinal()] < 0) v[a.ordinal()] = 1f;
            if (pr.hasSections()) for (Attr a : new Attr[] {Attr.TOP_RED, Attr.TOP_GREEN, Attr.TOP_BLUE, Attr.BOT_RED, Attr.BOT_GREEN, Attr.BOT_BLUE})
                if (coarse[a.ordinal()] < 0) v[a.ordinal()] = 0f;
        }
        // rev. 24: blinders in one channel: white, in the colour of a halogen lamp at that dimmer
        if (DmxMode.tungsten(pr) && (m.mode == DmxMode.ULTRA || m.mode == DmxMode.ULTRA_M)) {
            v[Attr.WHITE.ordinal()] = 1f;
            v[Attr.CTO.ordinal()] = 0f;                                    // rev. 25: the plain halogen lamp (Engine.halogen)
            v[Attr.STROBE.ordinal()] = 0f;
        }
        // rev. 24: Compact in two instances: the LED bars instance drives the top and the bottom bars
        if (DmxMode.barsLinked(pr, m.mode))
            for (int k = 0; k <= Attr.TOP_STROBE.ordinal() - Attr.TOP_DIM.ordinal(); k++)
                v[Attr.BOT_DIM.ordinal() + k] = v[Attr.TOP_DIM.ordinal() + k];
        if (anyZone && zones != null) {
            float r = v[Attr.RED.ordinal()], g = v[Attr.GREEN.ordinal()], b = v[Attr.BLUE.ordinal()];
            float w = pr.has(Attr.WHITE) ? v[Attr.WHITE.ordinal()] : 0f;
            // rev. 21 (zone layouts): a centre segment follows the centre (full) while every segment channel is at 0; as soon as one is
            // above 0, the segments show their own channels (a desk drawing pixels on the centre)
            boolean segments = false;
            if (zoned) for (int z = 0; z < pr.zones; z++) if (pr.zoneKind(z) == 1 && zr[z * 4 + 3] > 0f) { segments = true; break; }
            for (int z = 0; z < pr.zones; z++) {
                int i = z * 4;
                if (zoned) {
                    // the cell shows the brighter of its zone colour and its own channels; the zone intensity applies after (Engine)
                    int k = pr.section(z);
                    if (pr.zoneKind(z) == 1) { zones[i] = Float.NaN; zones[i + 1] = Float.NaN; zones[i + 2] = Float.NaN; zones[i + 3] = segments ? zr[i + 3] : 1f; continue; }
                    Attr red = com.concertstage.light.core.FixtureProfile.sectionRed(k);
                    float br = r * v[red.ordinal()], bg = g * v[red.ordinal() + 1], bb = b * v[red.ordinal() + 2];
                    zones[i] = Math.max(br, zr[i]); zones[i + 1] = Math.max(bg, zr[i + 1]); zones[i + 2] = Math.max(bb, zr[i + 2]);
                    zones[i + 3] = 1f;
                    continue;
                }
                if (pr.zoneKind(z) == 1) {
                    zones[i + 3] = Math.max(w, zr[i + 3]);
                } else {
                    float pixelWhite = pr.rgbwPixels ? Math.max(w, zw[z]) : 0f;
                    float[] tint = com.concertstage.light.core.Engine.white(v[Attr.CTO.ordinal()]);
                    zones[i] = Math.min(1f, Math.max(r, zr[i]) + pixelWhite * tint[0]);
                    zones[i + 1] = Math.min(1f, Math.max(g, zr[i + 1]) + pixelWhite * tint[1]);
                    zones[i + 2] = Math.min(1f, Math.max(b, zr[i + 2]) + pixelWhite * tint[2]);
                    zones[i + 3] = 1f;
                }
            }
        }
        transform(p, v);
        return true;
    }

    /** Rev. 1.3.0-2: iris and focus last set on the shared strobe / iris / focus channel of each patched fixture (fid, universe, address). */
    private static final java.util.Map<Long, float[]> HELD = new java.util.concurrent.ConcurrentHashMap<>();

    private static float[] held(Show.Patch p, FixtureProfile pr) {
        long key = ((long) p.fid << 32) | ((long) (p.universe & 0xFFFF) << 12) | (p.address & 0xFFF);
        if (HELD.size() > 8192) HELD.clear();
        return HELD.computeIfAbsent(key, k -> new float[] {pr.home(Attr.IRIS), pr.home(Attr.FOCUS)});
    }

    /** Local transforms of a fixture (inverted axes, offsets in degrees): not extra DMX control, the same values adapted to its place. */
    public static void transform(Show.Patch p, float[] v) {
        FixtureProfile pr = p.profile();
        if (pr.hasPan()) {
            int i = Attr.PAN.ordinal();
            float x = p.invertPan ? 1f - v[i] : v[i];
            if (pr.panRange > 0) x += p.panOffset / pr.panRange;
            v[i] = clamp01(x);
        }
        int t = Attr.TILT.ordinal();
        float y = p.invertTilt ? 1f - v[t] : v[t];
        if (pr.tiltRange > 0) y += p.tiltOffset / pr.tiltRange;
        v[t] = clamp01(y);
    }

    static float clamp01(float v) {
        return Float.isNaN(v) ? 0f : v < 0 ? 0 : v > 1 ? 1 : v;
    }
}
