package com.concertstage.light.dmx;

import com.concertstage.light.core.Attr;
import com.concertstage.light.core.FixtureProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * DMX modes (personalities) of the four CSC fixtures: which channel carries what, in which order, with which default. They are the
 * single source of the channel layouts: the decoder ({@link DmxDecode}), the auto patch, the console, the exported GDTF / QLC+ profiles
 * and the documentation are all built from these tables.
 *
 * <p>Only what the fixtures really do is listed. Modes:
 * <ul>
 * <li>{@link #FULL} — every function, 16-bit pan / tilt / dimmer (zoom on the VORTEX), every LED cell / zone.</li>
 * <li>{@link #COMPACT} — the fixture as a whole: 8-bit except pan / tilt, no per cell / zone control.</li>
 * <li>{@link #ULTRA} — the minimum: dimmer, position and a colour macro (17 fixed colours). Nothing else (see {@link #missing}).</li>
 * <li>{@link #MATRIX} — no channel of its own: the fixture follows the CSC VIRTUAL MATRIX ({@link VirtualMatrix}).</li>
 * </ul>
 */
public final class DmxMode {
    public static final int FULL = 0, COMPACT = 1, ULTRA = 2, MATRIX = 3;
    /**
     * Rev. 19: the Ultra Compact of the gobo fixtures before rev. 19 (4 channels: pan, tilt, dimmer, colour macro — no gobo, no strobe).
     * Kept only for shows saved before (their fixtures keep reading DMX the way the desk was set up for); never offered for a new
     * patch, and the auto patch replaces it by the current Ultra Compact. For the fixtures without gobos it is the same as Ultra.
     */
    public static final int ULTRA_V1 = 4;
    /**
     * Rev. 21: the Full and Compact modes of the PULSESTRIKE M and the FLASHCORE FX before their three light zones (one intensity, one
     * strobe and one colour for the whole fixture). Kept only for shows saved before, which keep reading DMX the way their desk was
     * set up (no channel changes meaning, no address moves); never offered for a new patch. For the other fixtures they are the same
     * as Full and Compact.
     */
    public static final int FULL_V1 = 5, COMPACT_V1 = 6;
    /**
     * Rev. 24: the Compact of the PULSESTRIKE M and the FLASHCORE FX from rev. 21 to rev. 23 (12 channels, three zones each with its
     * own dimmer). Kept for the shows saved with it; never offered for a new patch. The Compact is now two instances (LED bars, white
     * bar). For the other fixtures it is the same as Compact.
     */
    public static final int COMPACT_Z3 = 7;
    /**
     * 1.2.0 beta 2 (asked 13 November: "in Compact the gobo fixtures need the gobo rotation"): the Compact of the gobo fixtures before it
     * got its own "Gobo rotation" channel (8 channels, rotation only in the ranges of the gobo channel). Kept for the shows saved with it;
     * never offered for a new patch. For the other fixtures it is the same as Compact.
     */
    public static final int COMPACT_G8 = 8;
    /**
     * 1.2.0 beta 2b (14 November: "the Full of the pixel bar: a cell effect looks right on the desk and does nothing in the game", "the
     * Full of the PULSESTRIKE should be like the pixel mode of an RGB bar"): the Full of the pixel fixtures (RGB cells) and of the zone
     * fixtures became a pixel mode: no master dimmer and no fixture colour the cells already have (the console sets each pixel, with a
     * virtual dimmer per pixel), no LED bar runs on the PULSESTRIKE M / FLASHCORE FX. This is the Full before that: master dimmer (the
     * cells were dark while it was at 0), fixture colour, bar runs. Kept for the shows saved with it; never offered for a new patch.
     */
    public static final int FULL_M = 9;
    /**
     * 1.2.0 (15 November: "nobody likes to work with hue and saturation instead of RGB, saving one channel is not worth it"):
     * the colour is mixed in red / green / blue in every mode of every fixture. The layouts before, with a colour macro in Ultra Compact
     * ({@code ULTRA_M}) and hue + saturation in Compact ({@code COMPACT_HS}), are kept for the shows saved with them (no address moves);
     * never offered for a new patch. For the fixtures whose layout did not change they are the same as Ultra Compact / Compact.
     */
    public static final int ULTRA_M = 10, COMPACT_HS = 11;
    public static final String[] NAMES = {"Full", "Compact", "Ultra Compact", "Virtual Matrix", "Ultra Compact (4 ch, before rev. 19)",
        "Full (before rev. 21)", "Compact (before rev. 21)", "Compact (rev. 21 to 23)", "Compact (8 ch, before 1.2.0 beta 2)",
        "Full (master dimmer, before 1.2.0 beta 2b)", "Ultra Compact (colour macro, before 1.2.0)",
        "Compact (hue / saturation, before 1.2.0)"};
    /** Highest mode number. */
    public static final int LAST = COMPACT_HS;

    /** Rev. 21: a legacy layout kept for old shows (never offered for a new patch). */
    public static boolean legacy(int mode) {
        return mode == ULTRA_V1 || mode == FULL_V1 || mode == COMPACT_V1 || mode == COMPACT_Z3 || mode == COMPACT_G8 || mode == FULL_M
            || mode == ULTRA_M || mode == COMPACT_HS;
    }

    /** Rev. 21: the current mode a legacy one is replaced by (auto patch, patch screen). */
    public static int current(int mode) {
        return mode == ULTRA_V1 || mode == ULTRA_M ? ULTRA : mode == FULL_V1 || mode == FULL_M ? FULL
            : mode == COMPACT_V1 || mode == COMPACT_Z3 || mode == COMPACT_G8 || mode == COMPACT_HS ? COMPACT : mode;
    }

    /**
     * Rev. 21: the mode a show saved before rev. 21 keeps for fixture {@code p}: Full and Compact of the zone fixtures become their
     * "before rev. 21" layouts; everything else is unchanged.
     */
    public static int migrateRev21(com.concertstage.light.core.FixtureProfile p, int mode) {
        if (p == null || !p.hasSections()) return mode;
        return mode == FULL ? FULL_V1 : mode == COMPACT ? COMPACT_V1 : mode;
    }

    /**
     * Rev. 24: the mode a show saved from rev. 21 to rev. 23 keeps for fixture {@code p}: the Compact of the zone fixtures becomes its
     * three-zone layout of those revisions; everything else is unchanged.
     */
    public static int migrateRev24(com.concertstage.light.core.FixtureProfile p, int mode) {
        if (p == null) return mode;
        if (tungsten(p)) return mode == ULTRA ? ULTRA_V1 : mode;                 // blinders: Ultra Compact was dimmer + strobe
        if (!p.hasSections()) return mode;
        return mode == COMPACT ? COMPACT_Z3 : mode;
    }

    /**
     * 1.2.0: the mode a show saved before keeps: Compact / Ultra Compact of a fixture whose colour was hue + saturation / a
     * colour macro stay so (no address moves, the desk keeps its patch); the other fixtures are unchanged.
     */
    public static int migrateRgb(com.concertstage.light.core.FixtureProfile p, int mode) {
        if (p == null) return mode;
        if (mode == COMPACT && !same(of(p, COMPACT), of(p, COMPACT_HS))) return COMPACT_HS;
        if (mode == ULTRA && !same(of(p, ULTRA), of(p, ULTRA_M))) return ULTRA_M;
        return mode;
    }

    private static boolean same(DmxMode a, DmxMode b) {
        return java.util.Arrays.equals(a.channels, b.channels);
    }

    /** 1.2.0 beta 2: the mode a show saved before keeps: the Compact of the gobo fixtures stays in 8 channels (no address moves). */
    public static int migrateGoboRot(com.concertstage.light.core.FixtureProfile p, int mode) {
        return p != null && mode == COMPACT && goboRotChannel(p) ? COMPACT_G8 : mode;
    }

    /** 1.2.0 beta 2b: the mode a show saved before keeps: the Full of the pixel and zone fixtures keeps its master dimmer layout. */
    public static int migratePixelFull(com.concertstage.light.core.FixtureProfile p, int mode) {
        return p != null && mode == FULL && pixelFull(p) ? FULL_M : mode;
    }

    /**
     * 1.2.0 beta 2b: the Full of this fixture is a pixel mode (see {@link #FULL_M}): the fixtures with RGB cells (PIXELBAR B16, PIXELTUBE
     * T1, PIXELWASH W37, FLOODLIGHT F8, LUMORA W7 / W19) and the zone fixtures (PULSESTRIKE M, FLASHCORE FX). Not the blinders (white
     * segments only: their dimmer is the lamp).
     */
    public static boolean pixelFull(com.concertstage.light.core.FixtureProfile p) {
        if (p.hasSections()) return true;
        for (int z = 0; z < p.zones; z++) if (p.zoneKind(z) != 1) return true;
        return false;
    }

    /** 1.2.0 beta 2: the Compact of this fixture has its own gobo rotation channel. */
    public static boolean goboRotChannel(com.concertstage.light.core.FixtureProfile p) {
        return hasGoboWheel(p) && (p.has(Attr.GOBO_ROT) || p.has(Attr.GOBO_2_ROT)) && !p.hasSections();
    }

    /**
     * Rev. 24 — the blinders (BLINDER X2 / X4 / X8): their Ultra Compact is one channel, the dimmer, in the colour of a classic blinder
     * (DWE PAR36 650 W lamps, 3200 K), warmer as it dims like a halogen lamp (about 2700 K near the bottom). Before rev. 24 it was
     * dimmer + strobe (kept as ULTRA_V1 for the shows saved with it).
     */
    public static boolean tungsten(FixtureProfile p) {
        return p == FixtureProfile.BLINDER_X2 || p == FixtureProfile.BLINDER_X4 || p == FixtureProfile.BLINDER_X8;
    }

    /** CTO value (0 = 6500 K .. 1 = 2700 K) of a halogen blinder lamp at dimmer {@code dim}: 3200 K at full, warmer when dimmed. */
    public static float tungstenCto(float dim) {
        float full = (6500f - 3200f) / (6500f - 2700f);
        float d = Math.max(0f, Math.min(1f, dim));
        return full + (1f - full) * (1f - d) * (1f - d);
    }

    /**
     * Rev. 24: in this mode the top and bottom LED bars are one instance (one dimmer, one colour, one strobe): they strobe together
     * instead of in turn.
     */
    public static boolean barsLinked(com.concertstage.light.core.FixtureProfile p, int mode) {
        return p != null && p.hasSections() && mode == COMPACT;
    }

    /**
     * What a channel carries. Rev. 17 (Compact of 8 channels at most, see {@link #COMPACT_MAX}): {@code HUE} and {@code SATURATION}
     * mix the colour (they replace the red / green / blue / white / CTO channels), {@code SHUTTER_FX} carries the strobe and, in
     * ranges, the iris and the focus, {@code GOBO_FX} every gobo of every wheel of the fixture with its rotation.
     */
    public enum Kind { ATTR, FINE, ZONE_R, ZONE_G, ZONE_B, ZONE_W, ZONE_LEVEL, COLOR_MACRO, HUE, SATURATION, SHUTTER_FX, GOBO_FX }

    /**
     * Rev. 17: the Compact mode of every fixture takes 8 channels at most, so 64 fixtures fit in one universe (more than 60, asked
     * on 11 November). Fixtures whose Compact already fitted keep it unchanged.
     */
    public static final int COMPACT_MAX = 8;

    /**
     * One channel. {@code attr}: the attribute (ATTR / FINE); {@code zone}: the emitter zone (ZONE_*); {@code def}: DMX value a
     * desk should send at rest (the GDTF default).
     */
    public record Channel(Kind kind, Attr attr, int zone, String name, int def) {}

    /** Fixed colours of the colour macro channel (ULTRA): 15 DMX values each, from 0. Index 0 = open white. */
    public static final int[] MACRO_COLORS = {0xFFFFFF, 0xFF0000, 0xFF4000, 0xFF8000, 0xFFC000, 0xFFFF00, 0x80FF00, 0x00FF00, 0x00FF80,
        0x00FFFF, 0x0080FF, 0x0000FF, 0x4000FF, 0x8000FF, 0xFF00FF, 0xFF0080, 0xFFD8A0};
    public static final String[] MACRO_NAMES = {"White", "Red", "Orange", "Amber", "Gold", "Yellow", "Lime", "Green", "Sea green", "Cyan",
        "Sky blue", "Blue", "Indigo", "Violet", "Magenta", "Pink", "Warm white"};
    public static final int MACRO_STEP = 15;

    public final FixtureProfile profile;
    public final int mode;
    public final Channel[] channels;

    private DmxMode(FixtureProfile profile, int mode, Channel[] channels) {
        this.profile = profile;
        this.mode = mode;
        this.channels = channels;
    }

    /**
     * Rev. 16g: a sub-fixture (instance) of the mode as the console profiles show it: a name and the DMX channels (0-based offsets
     * in the mode) it carries. The channels are the mode's own channels, only grouped.
     */
    public record Instance(String name, int[] channels) {}

    /**
     * Sub-fixtures of this mode in the console profiles (grandMA2 / dot2 instances, GDTF geometries used by grandMA3, QLC+ heads).
     * PULSESTRIKE M Compact: two instances, the RGB LED bars (red, green, blue) and the white bar in the middle of the face (white,
     * CTO): the game drives the white segments from the White channel and the RGB zones from red / green / blue
     * ({@code Engine.finishZones}). Tilt, dimmer, strobe and zoom stay on the fixture itself. Other modes: none here (the Full mode
     * has one sub-fixture per zone, built from its zone channels).
     */
    public List<Instance> instances() {
        List<Instance> out = new ArrayList<>();
        if (profile.hasSections() && mode == COMPACT) {
            // rev. 24: two sub-fixtures, the LED bars (top and bottom together) and the white bar in the middle
            List<Integer> bars = new ArrayList<>(), white = new ArrayList<>();
            for (int i = 0; i < channels.length; i++) {
                Attr a = channels[i].attr();
                if (a == null || !a.zoneAttr()) continue;
                (zoneOf(a) == FixtureProfile.MID ? white : bars).add(i);
            }
            out.add(new Instance("LED bars", bars.stream().mapToInt(Integer::intValue).toArray()));
            out.add(new Instance("White bar", white.stream().mapToInt(Integer::intValue).toArray()));
            return out;
        }
        if (profile.hasSections() && (mode == FULL_M || mode == COMPACT_Z3)) {
            // rev. 21: one sub-fixture per light zone (its intensity, strobe and colour); tilt, zoom... stay on the fixture
            for (int k = 0; k < 3; k++) {
                List<Integer> l = new ArrayList<>();
                for (int i = 0; i < channels.length; i++) {
                    Attr a = channels[i].attr();
                    boolean mine = a != null && a.zoneAttr() && zoneOf(a) == k;      // the cells keep their own sub-fixture each
                    if (mine) l.add(i);
                }
                out.add(new Instance(FixtureProfile.SECTION_NAMES[k] + (k == FixtureProfile.MID ? " strobe" : " bar"), l.stream().mapToInt(Integer::intValue).toArray()));
            }
            return out;
        }
        if (profile == FixtureProfile.PULSESTRIKE_M && mode == COMPACT_V1) {
            // rev. 17: the LED bars are mixed by hue and saturation; the white bar keeps its own White and CTO channels
            out.add(new Instance("LED bars", offsets(Kind.HUE, Kind.SATURATION)));
            out.add(new Instance("White bar", offsets(Attr.WHITE, Attr.CTO)));
        }
        return out;
    }

    private int[] offsets(Kind... kinds) {
        List<Integer> l = new ArrayList<>();
        for (Kind k : kinds)
            for (int i = 0; i < channels.length; i++)
                if (channels[i].kind() == k) l.add(i);
        return l.stream().mapToInt(Integer::intValue).toArray();
    }

    private int[] offsets(Attr... attrs) {
        List<Integer> l = new ArrayList<>();
        for (Attr a : attrs)
            for (int i = 0; i < channels.length; i++)
                if (channels[i].kind() == Kind.ATTR && channels[i].attr() == a) l.add(i);
        int[] o = new int[l.size()];
        for (int i = 0; i < o.length; i++) o[i] = l.get(i);
        return o;
    }

    public int footprint() {
        return channels.length;
    }

    public String name() {
        return NAMES[mode];
    }

    private static final java.util.Map<FixtureProfile, DmxMode[]> TABLE = new java.util.IdentityHashMap<>();
    /** Rev. 21: the light zone of a zone attribute (0 top, 1 centre, 2 bottom), -1 for the others. */
    public static int zoneOf(Attr a) {
        return switch (a) {
            case TOP_DIM, TOP_RED, TOP_GREEN, TOP_BLUE, TOP_STROBE -> FixtureProfile.TOP;
            case MID_DIM, MID_STROBE -> FixtureProfile.MID;
            case BOT_DIM, BOT_RED, BOT_GREEN, BOT_BLUE, BOT_STROBE -> FixtureProfile.BOTTOM;
            default -> -1;
        };
    }

    public static DmxMode of(FixtureProfile p, int mode) {
        FixtureProfile.indexOf(p);
        int m = Math.max(0, Math.min(LAST, mode));
        synchronized (TABLE) {
            DmxMode[] row = TABLE.computeIfAbsent(p, key -> new DmxMode[NAMES.length]);
            if (row[m] == null) row[m] = new DmxMode(p, m, build(p, m));
            return row[m];
        }
    }

    // ------------------------------------------------------------ layouts
    private static Channel[] build(FixtureProfile p, int mode) {
        // 1.2.1: a conventional is one dimmer channel in every mode (the channel of a dimmer pack)
        if (p.conventional && mode != MATRIX) return new Channel[] {new Channel(Kind.ATTR, Attr.DIMMER, -1, "Dimmer (halogen)", 0)};
        if (mode == ULTRA_V1) return layout(p, ULTRA_V1);
        // rev. 21: the zone fixtures in Full / Compact (their layouts before rev. 21 stay under FULL_V1 / COMPACT_V1)
        if (p.hasSections() && mode == FULL) return zonedPixel(p);
        if (p.hasSections() && (mode == FULL_M || mode == COMPACT_Z3)) return zoned(p, mode == FULL_M);
        if (p.hasSections() && mode == COMPACT) return twoInstances(p);
        if (mode == COMPACT_Z3) mode = COMPACT;
        if (mode == FULL_V1 || mode == FULL_M) return layout(p, FULL);
        if (mode == FULL && pixelFull(p)) return pixelLayout(p);
        // 1.2.0: the colour in red / green / blue; the Compacts saved before (V1, Z3, G8, HS) keep hue + saturation
        boolean hs = mode != COMPACT;
        if (mode == COMPACT_V1) mode = COMPACT;
        boolean rotCh = mode == COMPACT || mode == COMPACT_HS;     // 1.2.0 beta 2: the 8-channel Compact (COMPACT_G8) has none
        if (mode == COMPACT_G8 || mode == COMPACT_HS) mode = COMPACT;
        if ((mode == ULTRA || mode == ULTRA_M) && tungsten(p)) return new Channel[] {new Channel(Kind.ATTR, Attr.DIMMER, -1, "Dimmer (halogen)", 0)};
        if (mode == ULTRA || mode == ULTRA_M) {
            // rev. 19: a fixture with gobos keeps them in Ultra Compact (11 November: "no gobo control" once the auto patch had put the
            // spots in Ultra to fit a universe), with its strobe: pan, tilt, dimmer, colour macro, strobe, gobo (+ rotation, both wheels)
            List<Channel> c = new ArrayList<>(List.of(layout(p, mode)));
            if (hasGoboWheel(p)) {
                if (c.stream().noneMatch(ch -> ch.attr() == Attr.STROBE)) c.add(new Channel(Kind.ATTR, Attr.STROBE, -1, "Strobe", 0));
                c.add(goboChannel(p));
            }
            return c.toArray(new Channel[0]);
        }
        if (mode != COMPACT) return layout(p, mode);
        // rev. 17: Compact in 8 channels at most. 1) the former Compact when it fits; 2) the same without its fine bytes (PIXELBAR B16:
        // tilt 8-bit, free RGBW kept); 3) the fixture repacked: pan / tilt 8-bit, dimmer, strobe (+ iris / focus ranges), hue +
        // saturation (+ CTO range, or White / CTO channels for the fixtures with a white bar), zoom, every gobo with its rotation.
        Channel[] legacy = layout(p, COMPACT);
        if (legacy.length <= COMPACT_MAX) return legacy;
        List<Channel> coarse = new ArrayList<>();
        for (Channel ch : legacy) if (ch.kind() != Kind.FINE) coarse.add(ch);
        if (coarse.size() <= COMPACT_MAX) return coarse.toArray(new Channel[0]);
        List<Channel> c = new ArrayList<>();
        if (p.hasPan()) c.add(new Channel(Kind.ATTR, Attr.PAN, -1, "Pan", 128));
        if (p.has(Attr.TILT)) c.add(new Channel(Kind.ATTR, Attr.TILT, -1, "Tilt", 128));
        c.add(new Channel(Kind.ATTR, Attr.DIMMER, -1, "Dimmer", 0));
        if (p.has(Attr.IRIS) || p.has(Attr.FOCUS))
            c.add(new Channel(Kind.SHUTTER_FX, Attr.STROBE, -1, "Strobe" + (p.has(Attr.IRIS) ? " / iris" : "") + (p.has(Attr.FOCUS) ? " / focus" : ""), 0));
        else c.add(new Channel(Kind.ATTR, Attr.STROBE, -1, "Strobe", 0));
        if (p.has(Attr.RED) && hs) {
            c.add(new Channel(Kind.HUE, null, -1, "Hue", 0));
            c.add(new Channel(Kind.SATURATION, null, -1, satCto(p) ? "Saturation / CTO" : "Saturation", 0));
        } else if (p.has(Attr.RED)) {                                   // 1.2.0
            c.add(new Channel(Kind.ATTR, Attr.RED, -1, "Red", 255));
            c.add(new Channel(Kind.ATTR, Attr.GREEN, -1, "Green", 255));
            c.add(new Channel(Kind.ATTR, Attr.BLUE, -1, "Blue", 255));
            if (satCto(p)) c.add(new Channel(Kind.ATTR, Attr.CTO, -1, "CTO", 0));
        }
        if (separateWhite(p)) {
            c.add(new Channel(Kind.ATTR, Attr.WHITE, -1, "White", 0));
            if (p.has(Attr.CTO)) c.add(new Channel(Kind.ATTR, Attr.CTO, -1, "CTO", 0));
        }
        if (p.has(Attr.ZOOM)) c.add(new Channel(Kind.ATTR, Attr.ZOOM, -1, "Zoom", Math.round(p.home(Attr.ZOOM) * 255)));
        if (hasGoboWheel(p)) c.add(goboChannel(p));
        // 1.2.0 beta 2: the rotation of the gobo in the light (either wheel), 128 = still; the gobo channel's rotation ranges still work
        // when it is at 128
        if (rotCh && goboRotChannel(p)) c.add(new Channel(Kind.ATTR, Attr.GOBO_ROT, -1, "Gobo rotation", 128));
        if (c.size() > COMPACT_MAX + (rotCh && goboRotChannel(p) ? 1 : 0) + (hs ? 0 : 2)) throw new IllegalStateException(p.id + " Compact takes " + c.size() + " channels");
        return c.toArray(new Channel[0]);
    }

    /**
     * Rev. 21 — Full and Compact of the zone fixtures (PULSESTRIKE M, FLASHCORE FX). Each light zone has its intensity and its strobe,
     * the RGB bars their colour; no master dimmer (the zones are the dimmers: the fixture is at full, each zone at its own level).
     * <ul>
     * <li>Full: tilt 16-bit, the motor and optic functions of the fixture (zoom, focus, motor speed), CTO of the white, then each zone
     * in one run (top bar intensity, red, green, blue, strobe; centre intensity, strobe; bottom bar as the top), then every cell zone by
     * zone (RGB of the top cells, level of the centre segments, RGB of the bottom cells). A cell shows the brighter of its zone colour
     * and its own channels.</li>
     * <li>Compact: tilt, zoom, a strobe shared by the three zones, top intensity + RGB, centre intensity, bottom intensity + RGB (12).
     * Rev. 21 asks for the zones in Compact: it grows from 8 to 12 channels for these two fixtures (42 per universe instead of 64).</li>
     * </ul>
     * Strobe channels: 0..7 open, 8..255 slow to the fastest rate of the fixture (as the shared strobe). Top and bottom flash in turn
     * when they strobe at the same rate (the classic alternation); the shared strobe flashes every zone together.
     */
    private static Channel[] zoned(FixtureProfile p, boolean full) {
        List<Channel> c = new ArrayList<>();
        wide(c, Attr.TILT, "Tilt", full, 128);
        if (full) {
            if (p.has(Attr.MOVE_SPEED)) c.add(new Channel(Kind.ATTR, Attr.MOVE_SPEED, -1, "Motor speed", 0));
            if (p.has(Attr.ZOOM)) c.add(new Channel(Kind.ATTR, Attr.ZOOM, -1, "Zoom", Math.round(p.home(Attr.ZOOM) * 255)));
            if (p.has(Attr.FOCUS)) c.add(new Channel(Kind.ATTR, Attr.FOCUS, -1, "Focus", 0));
            if (p.has(Attr.CTO)) c.add(new Channel(Kind.ATTR, Attr.CTO, -1, "CTO (white)", 0));
        } else {
            if (p.has(Attr.ZOOM)) c.add(new Channel(Kind.ATTR, Attr.ZOOM, -1, "Zoom", Math.round(p.home(Attr.ZOOM) * 255)));
            c.add(new Channel(Kind.ATTR, Attr.STROBE, -1, "Strobe (all zones)", 0));
        }
        // each zone in one run of channels (a sub-fixture on a console): intensity, colour of a bar, strobe (Full)
        for (int k : new int[] {FixtureProfile.TOP, FixtureProfile.MID, FixtureProfile.BOTTOM}) {
            String zn = FixtureProfile.SECTION_NAMES[k];
            c.add(new Channel(Kind.ATTR, FixtureProfile.sectionDim(k), -1, zn + " dimmer", 0));
            Attr red = FixtureProfile.sectionRed(k);
            if (red != null) {
                c.add(new Channel(Kind.ATTR, red, -1, zn + " red", 255));
                c.add(new Channel(Kind.ATTR, Attr.ALL[red.ordinal() + 1], -1, zn + " green", 255));
                c.add(new Channel(Kind.ATTR, Attr.ALL[red.ordinal() + 2], -1, zn + " blue", 255));
            }
            if (full) c.add(new Channel(Kind.ATTR, FixtureProfile.sectionStrobe(k), -1, zn + " strobe", 0));
        }
        // Full: then every cell, zone by zone (top cells, centre segments, bottom cells)
        if (full) for (int k : new int[] {FixtureProfile.TOP, FixtureProfile.MID, FixtureProfile.BOTTOM}) {
            String zn = FixtureProfile.SECTION_NAMES[k];
            int n = 0;
            for (int z = 0; z < p.zones; z++) {
                if (p.section(z) != k) continue;
                n++;
                if (p.zoneKind(z) == 1) { c.add(new Channel(Kind.ZONE_LEVEL, null, z, zn + " segment " + n, 0)); continue; }
                c.add(new Channel(Kind.ZONE_R, null, z, zn + " cell " + n + " red", 0));
                c.add(new Channel(Kind.ZONE_G, null, z, zn + " cell " + n + " green", 0));
                c.add(new Channel(Kind.ZONE_B, null, z, zn + " cell " + n + " blue", 0));
            }
        }
        return c.toArray(new Channel[0]);
    }

    /**
     * 1.2.0 beta 2b — Full of the PULSESTRIKE M and the FLASHCORE FX as the pixel mode of an LED bar: tilt 16-bit, the motor and optic
     * functions, CTO of the white, the white bar in the middle (dimmer: the level of its segments) and one strobe for the whole fixture,
     * then every cell zone by zone (RGB of the top cells, level of the centre segments, RGB of the bottom cells). No LED bar runs: a cell
     * is its own colour and intensity (the console gives each one a virtual dimmer), as on a pixel bar.
     */
    private static Channel[] zonedPixel(FixtureProfile p) {
        List<Channel> c = new ArrayList<>();
        for (Channel ch : zoned(p, true)) {
            Attr a = ch.attr();
            if (a == null || !a.zoneAttr()) { c.add(ch); continue; }
            if (a == Attr.MID_DIM) {
                c.add(new Channel(Kind.ATTR, Attr.MID_DIM, -1, "White bar dimmer", 0));
                c.add(new Channel(Kind.ATTR, Attr.STROBE, -1, "Strobe", 0));
            }
        }
        return c.toArray(new Channel[0]);
    }

    /**
     * 1.2.0 beta 2b — Full of the pixel fixtures as a pixel mode: the Full before (master dimmer, fixture colour, then the cells) without
     * the master dimmer and without the colours the cells have (the White of a LUMORA stays: its cells have none). Each cell is its own
     * colour and intensity; strobe, zoom, position, CTO... stay on the fixture.
     */
    private static Channel[] pixelLayout(FixtureProfile p) {
        boolean cellWhite = p.rgbwPixels;
        List<Channel> c = new ArrayList<>();
        for (Channel ch : layout(p, FULL)) {
            Attr a = ch.attr();
            if (ch.zone() < 0 && a != null && (a == Attr.DIMMER || a == Attr.RED || a == Attr.GREEN || a == Attr.BLUE || a == Attr.WHITE && cellWhite)) continue;
            c.add(ch);
        }
        return c.toArray(new Channel[0]);
    }

    /**
     * Rev. 24 — Compact of the PULSESTRIKE M and the FLASHCORE FX in two instances (asked 11 November, 23 h): tilt, zoom, then the LED
     * bars (top and bottom together: dimmer, red, green, blue, strobe) and the white bar in the middle (dimmer, strobe). 9 channels
     * (8 without zoom): 56 fixtures per universe. The bar channels drive the top zone; DmxDecode copies them to the bottom zone.
     */
    private static Channel[] twoInstances(FixtureProfile p) {
        List<Channel> c = new ArrayList<>();
        c.add(new Channel(Kind.ATTR, Attr.TILT, -1, "Tilt", 128));
        if (p.has(Attr.ZOOM)) c.add(new Channel(Kind.ATTR, Attr.ZOOM, -1, "Zoom", Math.round(p.home(Attr.ZOOM) * 255)));
        c.add(new Channel(Kind.ATTR, Attr.TOP_DIM, -1, "LED bars dimmer", 0));
        c.add(new Channel(Kind.ATTR, Attr.TOP_RED, -1, "LED bars red", 255));
        c.add(new Channel(Kind.ATTR, Attr.TOP_GREEN, -1, "LED bars green", 255));
        c.add(new Channel(Kind.ATTR, Attr.TOP_BLUE, -1, "LED bars blue", 255));
        c.add(new Channel(Kind.ATTR, Attr.TOP_STROBE, -1, "LED bars strobe", 0));
        c.add(new Channel(Kind.ATTR, Attr.MID_DIM, -1, "White bar dimmer", 0));
        c.add(new Channel(Kind.ATTR, Attr.MID_STROBE, -1, "White bar strobe", 0));
        return c.toArray(new Channel[0]);
    }

    /** Rev. 19: the fixture has a gobo wheel (every reduced mode then carries a gobo channel). */
    public static boolean hasGoboWheel(FixtureProfile p) {
        return p.gobos > 0 && p.has(Attr.GOBO);
    }

    /** The packed gobo channel (every gobo of every wheel, with its rotation), the same in Compact and Ultra Compact. */
    static Channel goboChannel(FixtureProfile p) {
        return new Channel(Kind.GOBO_FX, Attr.GOBO, -1, p.has(Attr.GOBO_2) ? "Gobos 1 + 2 / rotation" : p.has(Attr.GOBO_ROT) ? "Gobo / rotation" : "Gobo", 0);
    }

    /** Rev. 17 Compact: a fixture with separate white emitters (white bar / white segments next to RGB zones) keeps White and CTO. */
    public static boolean separateWhite(FixtureProfile p) {
        return p.has(Attr.WHITE) && p.has(Attr.RED) && p.whiteZones > 0 && p.rgbZones > 0;
    }

    /** Rev. 17 Compact: the saturation channel ends with a CTO range (white, cold to warm) when the fixture has a CTO and no White / CTO channels. */
    public static boolean satCto(FixtureProfile p) {
        return p.has(Attr.CTO) && !separateWhite(p);
    }

    private static Channel[] layout(FixtureProfile p, int mode) {
        List<Channel> c = new ArrayList<>();
        if (mode == MATRIX) return new Channel[0];
        boolean full = mode == FULL, ultra = mode == ULTRA || mode == ULTRA_V1 || mode == ULTRA_M;
        // position
        if (p.hasPan()) wide(c, Attr.PAN, "Pan", !ultra, 128);
        if (p.has(Attr.TILT)) wide(c, Attr.TILT, "Tilt", !ultra, 128);
        // intensity
        wide(c, Attr.DIMMER, "Dimmer", full, 0);
        if (ultra) {
            if (p.has(Attr.RED) && mode != ULTRA) c.add(new Channel(Kind.COLOR_MACRO, null, -1, "Color macro", 0));
            else if (p.has(Attr.RED)) {                                 // 1.2.0: red / green / blue in Ultra Compact too
                c.add(new Channel(Kind.ATTR, Attr.RED, -1, "Red", 255));
                c.add(new Channel(Kind.ATTR, Attr.GREEN, -1, "Green", 255));
                c.add(new Channel(Kind.ATTR, Attr.BLUE, -1, "Blue", 255));
            }
            if (p == FixtureProfile.PULSESTRIKE_M || p.whiteZones > 0 && p.rgbZones == 0) c.add(new Channel(Kind.ATTR, Attr.STROBE, -1, "Strobe", 0));   // a strobe without strobe is no use
            return c.toArray(new Channel[0]);
        }
        c.add(new Channel(Kind.ATTR, Attr.STROBE, -1, "Strobe", 0));
        // colour
        if (p.has(Attr.RED)) c.add(new Channel(Kind.ATTR, Attr.RED, -1, "Red", 255));
        if (p.has(Attr.GREEN)) c.add(new Channel(Kind.ATTR, Attr.GREEN, -1, "Green", 255));
        if (p.has(Attr.BLUE)) c.add(new Channel(Kind.ATTR, Attr.BLUE, -1, "Blue", 255));
        if (p.has(Attr.WHITE)) c.add(new Channel(Kind.ATTR, Attr.WHITE, -1, "White", 0));
        if (p.has(Attr.CTO)) c.add(new Channel(Kind.ATTR, Attr.CTO, -1, "CTO", 0));
        // beam
        if (p.has(Attr.ZOOM)) wide(c, Attr.ZOOM, "Zoom", full && p == FixtureProfile.VORTEX_X1, Math.round(p.home(Attr.ZOOM) * 255));
        if (p.has(Attr.FOCUS) && (full || p == FixtureProfile.VORTEX_X1)) c.add(new Channel(Kind.ATTR, Attr.FOCUS, -1, "Focus", 0));
        if (p.has(Attr.IRIS)) c.add(new Channel(Kind.ATTR, Attr.IRIS, -1, "Iris", 255));
        if (p.has(Attr.GOBO)) c.add(new Channel(Kind.ATTR, Attr.GOBO, -1, "Gobo", 0));
        // rev. 1.3.0-2: the RAZOR B1 got its gobo rotation after its tables were published: its channel goes at the end of Full (below),
        // the earlier channels keep their offsets; Compact / Ultra carry it in their gobo channel (GOBO_FX) without an extra channel
        if (p.has(Attr.GOBO_ROT) && p != FixtureProfile.RAZOR_B1) c.add(new Channel(Kind.ATTR, Attr.GOBO_ROT, -1, "Gobo rotation", 128));
        if (p.has(Attr.PRISM) && full) c.add(new Channel(Kind.ATTR, Attr.PRISM, -1, "Prism", 0));
        if (p.has(Attr.PRISM_ROT) && full) c.add(new Channel(Kind.ATTR, Attr.PRISM_ROT, -1, "Prism rotation", 128));
        // Attributes added for the new families. Legacy tables above keep their order and offsets.
        if (full) for (Attr a : p.attrs) {
            if (a.zoneAttr()) continue;                 // rev. 21: the zones have their own layouts (zoned), never in the older ones
            boolean present = c.stream().anyMatch(ch -> ch.attr() == a);
            if (a == Attr.GOBO_ROT && p == FixtureProfile.RAZOR_B1) continue;
            if (!present) wide(c, a, a.label, false, Math.round(p.home(a) * 255));
        }
        if (full && p == FixtureProfile.RAZOR_B1) c.add(new Channel(Kind.ATTR, Attr.GOBO_ROT, -1, "Gobo rotation", 128));
        // pixels (FULL only): RGB per cell / RGB zone, one level per white segment
        if (full) {
            for (int z = 0; z < p.zones; z++) {
                int kind = p.zoneKind(z);
                String n = kind == 1 ? "White segment " + (z - p.rgbZones + 1) : kind == 0 ? "Zone " + (z + 1) : "Cell " + (z + 1 - p.rgbZones - p.whiteZones);
                if (kind == 1) { c.add(new Channel(Kind.ZONE_LEVEL, null, z, n, 0)); continue; }
                c.add(new Channel(Kind.ZONE_R, null, z, n + " red", 0));
                c.add(new Channel(Kind.ZONE_G, null, z, n + " green", 0));
                c.add(new Channel(Kind.ZONE_B, null, z, n + " blue", 0));
                if (p.rgbwPixels) c.add(new Channel(Kind.ZONE_W, null, z, n + " white", 0));
            }
        }
        return c.toArray(new Channel[0]);
    }

    private static void wide(List<Channel> c, Attr a, String name, boolean fine, int def) {
        c.add(new Channel(Kind.ATTR, a, -1, name, def));
        if (fine) c.add(new Channel(Kind.FINE, a, -1, name + " fine", 0));
    }

    /** What the fixture can do that this mode does not reach (for the console and the documentation). */
    public List<String> missing() {
        List<String> out = new ArrayList<>();
        if (mode == MATRIX) { out.add("No channel of its own: follows the CSC VIRTUAL MATRIX"); return out; }
        java.util.EnumSet<Attr> reached = java.util.EnumSet.noneOf(Attr.class);
        boolean zones = false, macro = false;
        for (Channel ch : channels) {
            reached.addAll(covers(profile, ch));
            if (ch.zone >= 0) zones = true;
            if (ch.kind == Kind.COLOR_MACRO) macro = true;
        }
        boolean zoned = profile.hasSections() && (mode == FULL || mode == FULL_M || mode == COMPACT || mode == COMPACT_Z3);   // (COMPACT_G8: no zones)
        boolean linked = barsLinked(profile, mode);
        if (linked) {
            // rev. 24: the LED bars instance drives the bottom bar too (DmxDecode); the instances have their own strobes
            for (Attr a : Attr.ALL) if (reached.contains(a) && zoneOf(a) == FixtureProfile.TOP) reached.add(Attr.ALL[a.ordinal() - Attr.TOP_DIM.ordinal() + Attr.BOT_DIM.ordinal()]);
            reached.add(Attr.STROBE);
        }
        boolean lamp = tungsten(profile) && mode == ULTRA;
        if (lamp) { reached.add(Attr.WHITE); reached.add(Attr.CTO); }
        for (Attr a : profile.attrs) {
            if (reached.contains(a)) continue;
            // rev. 21: in the zone layouts the zones are the dimmers, the colours and (Full) the strobes of the fixture
            if (zoned && (a == Attr.DIMMER || a == Attr.RED || a == Attr.GREEN || a == Attr.BLUE || a == Attr.WHITE || a == Attr.STROBE && mode == FULL)) continue;
            if (macro && (a == Attr.RED || a == Attr.GREEN || a == Attr.BLUE)) continue;
            // 1.2.0 beta 2b: in the pixel Full the cells are the dimmer and the colours (the LED bars of a zone fixture: its cells)
            if (mode == FULL && pixelFull(profile) && (a == Attr.DIMMER || a == Attr.RED || a == Attr.GREEN || a == Attr.BLUE || a == Attr.WHITE || a.zoneAttr())) continue;
            out.add(a.label + (macro && (a == Attr.WHITE || a == Attr.CTO) ? " (colour macro only)" : ""));
        }
        if (macro) out.add("Free RGB mixing (17 fixed colours only)");
        if (linked) out.add("Top and bottom LED bars separately (one instance for both)");
        if (lamp) out.add("Colour (fixed: halogen blinder lamp, warmer when dimmed)");
        if (profile.zones > 0 && !zones) out.add("Per cell / per zone control (" + profile.zones + " zones)");
        boolean fine = false, packed = false;
        for (Channel ch : channels) { if (ch.kind == Kind.FINE) fine = true; if (ch.kind == Kind.SHUTTER_FX || ch.kind == Kind.GOBO_FX) packed = true; }
        if ((mode == COMPACT || mode == COMPACT_V1 || mode == COMPACT_G8) && !fine && (profile.hasPan() || profile.has(Attr.TILT)) && layout(profile, COMPACT).length > channels.length)
            out.add("16-bit position (8-bit pan / tilt)");
        if (packed) for (Channel ch : channels) {
            if (ch.kind == Kind.SHUTTER_FX) out.add("Strobe with " + (profile.has(Attr.IRIS) ? "iris" : "focus") + " at the same time (one range at a time)");
            if (ch.kind == Kind.GOBO_FX && profile.has(Attr.GOBO_2)) out.add("Both gobo wheels at the same time (one gobo at a time)");
        }
        return out;
    }

    /** Attributes a channel reaches (rev. 17: a packed channel reaches several). */
    public static java.util.Set<Attr> covers(FixtureProfile p, Channel ch) {
        java.util.EnumSet<Attr> s = java.util.EnumSet.noneOf(Attr.class);
        switch (ch.kind) {
            case HUE -> { s.add(Attr.RED); s.add(Attr.GREEN); s.add(Attr.BLUE); }
            case SATURATION -> {
                s.add(Attr.RED); s.add(Attr.GREEN); s.add(Attr.BLUE);
                if (p.has(Attr.WHITE) && !separateWhite(p)) s.add(Attr.WHITE);
                if (satCto(p)) s.add(Attr.CTO);
            }
            case SHUTTER_FX -> {
                s.add(Attr.STROBE);
                if (p.has(Attr.IRIS)) s.add(Attr.IRIS);
                if (p.has(Attr.FOCUS)) s.add(Attr.FOCUS);
            }
            case GOBO_FX -> {
                s.add(Attr.GOBO);
                for (Attr a : new Attr[] {Attr.GOBO_ROT, Attr.GOBO_2, Attr.GOBO_2_ROT}) if (p.has(a)) s.add(a);
            }
            default -> { if (ch.attr != null) s.add(ch.attr); }
        }
        return s;
    }

    // ------------------------------------------------------------ rev. 17: packed Compact channels
    /**
     * One DMX range of a packed Compact channel, for the console profiles, the charts and the console of the game: DMX {@code lo..hi},
     * what it does ({@code what}: SHUTTER_OPEN, STROBE, IRIS, FOCUS, HUE, SATURATION, CTO, GOBO, GOBO_SPIN, GOBO_2, GOBO_2_SPIN),
     * its physical values from / to (degrees, 0..1, Hz, degrees per second; the gobo number for GOBO / GOBO_2 / *_SPIN in {@code slot}).
     */
    public record Range(int lo, int hi, String name, String what, float from, float to, int slot) {}

    /** Gobo rotation of the game at full speed (FixtureRenderer: 220° per second at the end of the rotation channel). */
    public static final float GOBO_DEG_S = 220f;

    /** The ranges of a packed channel (HUE, SATURATION, SHUTTER_FX, GOBO_FX); empty for the other kinds. Same rules as the decoder. */
    public static List<Range> ranges(FixtureProfile p, Channel ch) {
        List<Range> r = new ArrayList<>();
        switch (ch.kind) {
            case HUE -> r.add(new Range(0, 255, "Hue", "HUE", 0f, 358.6f, 0));
            case SATURATION -> {
                if (satCto(p)) {
                    r.add(new Range(0, SAT_END, "Saturation (0 = white)", "SATURATION", 0f, 1f, 0));
                    r.add(new Range(SAT_END + 1, 255, "White CTO 6500 K to 2700 K", "CTO", 6500f, 2700f, 0));
                } else r.add(new Range(0, 255, "Saturation (0 = white)", "SATURATION", 0f, 1f, 0));
            }
            case SHUTTER_FX -> {
                r.add(new Range(0, STROBE_OPEN - 1, "Open", "SHUTTER_OPEN", 0f, 0f, 0));
                r.add(new Range(STROBE_OPEN, FX_STROBE_END, "Strobe", "STROBE", 1f, p.strobeMaxHz, 0));
                int[] ir = irisRange(p), fr = focusRange(p);
                if (ir != null) r.add(new Range(ir[0], ir[1], "Iris open to closed", "IRIS", 1f, 0f, 0));
                if (fr != null) r.add(new Range(fr[0], fr[1], "Focus", "FOCUS", 0f, 1f, 0));
            }
            case GOBO_FX -> {
                int slots = goboSlots(p), b = goboBlock(p);
                for (int s = 0; s < slots; s++) {
                    int lo = s * b, hi = s == slots - 1 ? 255 : lo + b - 1;
                    boolean second = s > p.gobos;
                    int idx = second ? s - p.gobos : s;
                    String what = second ? "GOBO_2" : "GOBO";
                    // rev. 24: each slot carries the name of the fixture's own gobo (GoboWheels), as the Full mode does since rev. 22
                    com.concertstage.light.core.GoboWheels.Wheel wh = second ? com.concertstage.light.core.GoboWheels.second(p) : com.concertstage.light.core.GoboWheels.first(p);
                    String n = s == 0 ? "Open" : (p.has(Attr.GOBO_2) ? (second ? "Wheel 2 gobo " : "Wheel 1 gobo ") : "Gobo ") + idx
                        + (wh != null && idx <= wh.size() ? " " + wh.name(idx) : "");
                    if (!goboTurns(p, s)) { r.add(new Range(lo, hi, n, what, idx, idx, idx)); continue; }
                    int still = goboStill(p), span = b - still, half = span / 2;
                    int cwHi = lo + still + half - 1, ccwHi = s == slots - 1 ? 255 : lo + b - 1;
                    r.add(new Range(lo, lo + still - 1, n, what, idx, idx, idx));
                    r.add(new Range(lo + still, cwHi, n + " rotating, slow to fast", what + "_SPIN", GOBO_DEG_S / half, GOBO_DEG_S, idx));
                    r.add(new Range(cwHi + 1, ccwHi, n + " rotating other way, slow to fast", what + "_SPIN", -GOBO_DEG_S / (span - half), -GOBO_DEG_S, idx));
                }
            }
            default -> { }
        }
        return r;
    }

    /** Hue channel: DMX 0..255 = 0..360° (255 = 358.6°, just before red again). */
    public static float[] hueRgb(int raw) {
        float h = (raw & 255) / 256f * 6f;
        int k = (int) h;
        float f = h - k;
        return switch (k) {
            case 0 -> new float[] {1f, f, 0f};
            case 1 -> new float[] {1f - f, 1f, 0f};
            case 2 -> new float[] {0f, 1f, f};
            case 3 -> new float[] {0f, 1f - f, 1f};
            case 4 -> new float[] {f, 0f, 1f};
            default -> new float[] {1f, 0f, 1f - f};
        };
    }

    /** Saturation channel with a CTO range: 0..{@value #SAT_END} saturation 0 (white) to 100 %, then white cold to warm. */
    public static final int SAT_END = 199;

    /**
     * Writes the colour of the Hue and Saturation channels into {@code v}: red / green / blue, and white / CTO where the saturation
     * channel carries them. RGB fixtures mix towards white ({@code 1 - s (1 - hue)}); RGBW fixtures (one emitter set) give the
     * unsaturated part to their white emitters, as a real RGBW mix does.
     */
    public static void hueSat(FixtureProfile p, int hueRaw, int satRaw, float[] v) {
        float s, cto = 0f;
        if (satCto(p)) {
            if (satRaw <= SAT_END) s = satRaw / (float) SAT_END;
            else { s = 0f; cto = (satRaw - SAT_END - 1) / (float) (255 - SAT_END - 1); }
        } else s = satRaw / 255f;
        float[] h = hueRgb(hueRaw);
        boolean rgbw = p.has(Attr.WHITE) && !separateWhite(p);
        for (int i = 0; i < 3; i++) {
            float x = rgbw ? s * h[i] : 1f - s * (1f - h[i]);
            v[Attr.RED.ordinal() + i] = x;
        }
        if (rgbw) v[Attr.WHITE.ordinal()] = 1f - s;
        if (satCto(p)) v[Attr.CTO.ordinal()] = cto;
    }

    /** Strobe channel with ranges (SHUTTER_FX): 0..7 open, 8..127 strobe, then iris and / or focus. */
    public static final int FX_STROBE_END = 127;

    /** Range of the iris (and of the focus) in a SHUTTER_FX channel: {from, to} or null. Iris: open at {@code from}, closed at {@code to}. */
    public static int[] irisRange(FixtureProfile p) {
        if (!p.has(Attr.IRIS)) return null;
        return p.has(Attr.FOCUS) ? new int[] {128, 191} : new int[] {128, 255};
    }

    public static int[] focusRange(FixtureProfile p) {
        if (!p.has(Attr.FOCUS)) return null;
        return p.has(Attr.IRIS) ? new int[] {192, 255} : new int[] {128, 255};
    }

    /**
     * Rev. 1.3.0-2: decodes a SHUTTER_FX channel keeping the iris and the focus where they were last set ({@code held}: iris, focus,
     * updated here) while the channel is in its open or strobe range. Asked 12 November: a strobe no longer opens or closes the iris,
     * the beam keeps the size the desk gave it. {@code held} null: the stateless decoding below.
     */
    public static void shutterFx(FixtureProfile p, int raw, float[] v, float[] held) {
        if (held == null) { shutterFx(p, raw, v); return; }
        int[] ir = irisRange(p), fr = focusRange(p);
        float strobe = 0f;
        if (raw >= STROBE_OPEN && raw <= FX_STROBE_END) strobe = (raw - STROBE_OPEN) / (float) (FX_STROBE_END - STROBE_OPEN);
        else if (ir != null && raw >= ir[0] && raw <= ir[1]) held[0] = 1f - (raw - ir[0]) / (float) (ir[1] - ir[0]);
        else if (fr != null && raw >= fr[0] && raw <= fr[1]) held[1] = (raw - fr[0]) / (float) (fr[1] - fr[0]);
        v[Attr.STROBE.ordinal()] = strobe;
        if (p.has(Attr.IRIS)) v[Attr.IRIS.ordinal()] = held[0];
        if (p.has(Attr.FOCUS)) v[Attr.FOCUS.ordinal()] = held[1];
    }

    /** Decodes a SHUTTER_FX channel: strobe, iris and focus (each at its rest value outside its range). */
    public static void shutterFx(FixtureProfile p, int raw, float[] v) {
        float strobe = 0f, iris = p.home(Attr.IRIS), focus = p.home(Attr.FOCUS);
        int[] ir = irisRange(p), fr = focusRange(p);
        if (raw >= STROBE_OPEN && raw <= FX_STROBE_END) strobe = (raw - STROBE_OPEN) / (float) (FX_STROBE_END - STROBE_OPEN);
        else if (ir != null && raw >= ir[0] && raw <= ir[1]) iris = 1f - (raw - ir[0]) / (float) (ir[1] - ir[0]);
        else if (fr != null && raw >= fr[0] && raw <= fr[1]) focus = (raw - fr[0]) / (float) (fr[1] - fr[0]);
        v[Attr.STROBE.ordinal()] = strobe;
        if (p.has(Attr.IRIS)) v[Attr.IRIS.ordinal()] = iris;
        if (p.has(Attr.FOCUS)) v[Attr.FOCUS.ordinal()] = focus;
    }

    /** Number of positions of a GOBO_FX channel: open, the gobos of wheel 1, then those of wheel 2. */
    public static int goboSlots(FixtureProfile p) {
        return 1 + p.gobos + (p.has(Attr.GOBO_2) ? p.gobos : 0);
    }

    /** DMX values per position of a GOBO_FX channel (the last position runs to 255). */
    public static int goboBlock(FixtureProfile p) {
        return 256 / goboSlots(p);
    }

    /** Values of a position that hold the gobo still (the rest of the block: turning one way, then the other, slow to fast). */
    public static int goboStill(FixtureProfile p) {
        return Math.max(1, goboBlock(p) / 5);
    }

    /** Whether the gobos of a position of the GOBO_FX channel turn (wheel with rotation; never the open position). */
    public static boolean goboTurns(FixtureProfile p, int slot) {
        if (slot <= 0) return false;
        return slot <= p.gobos ? p.has(Attr.GOBO_ROT) : p.has(Attr.GOBO_2_ROT);
    }

    /** Decodes a GOBO_FX channel into gobo / rotation of wheel 1 and wheel 2. */
    public static void goboFx(FixtureProfile p, int raw, float[] v) {
        int slots = goboSlots(p), b = goboBlock(p);
        int slot = Math.min(slots - 1, raw / b);
        int off = Math.min(b - 1, raw - slot * b);
        float rot = 0.5f;
        if (goboTurns(p, slot)) {
            int still = goboStill(p);
            if (off >= still) {
                int span = b - still, half = span / 2;
                int r = off - still;
                rot = r < half ? 0.5f + 0.5f * (r + 1) / half : 0.5f - 0.5f * (r - half + 1) / (float) (span - half);
            }
        }
        boolean second = slot > p.gobos;
        int index = second ? slot - p.gobos : slot;
        float g = p.gobos <= 0 ? 0f : index / (float) p.gobos;
        v[Attr.GOBO.ordinal()] = second ? 0f : g;
        if (p.has(Attr.GOBO_ROT)) v[Attr.GOBO_ROT.ordinal()] = second ? 0.5f : rot;
        if (p.has(Attr.GOBO_2)) v[Attr.GOBO_2.ordinal()] = second ? g : 0f;
        if (p.has(Attr.GOBO_2_ROT)) v[Attr.GOBO_2_ROT.ordinal()] = second ? rot : 0.5f;
    }

    /**
     * DMX value of a GOBO_FX channel for gobo {@code index} (0 = open, 1..gobos) of wheel {@code wheel} (1 or 2) turning at
     * {@code speed} (-1..1, 0 still; positive = the direction GOBO_ROT &gt; 0.5). Inverse of {@link #goboFx} (tests, profiles).
     */
    public static int goboValue(FixtureProfile p, int wheel, int index, float speed) {
        if (index <= 0) return 0;
        int slot = wheel == 2 ? p.gobos + index : index;
        int b = goboBlock(p), base = slot * b;
        if (!goboTurns(p, slot) || Math.abs(speed) < 1e-4f) return base;
        int still = goboStill(p), span = b - still, half = span / 2;
        float sp = Math.min(1f, Math.abs(speed));
        if (speed > 0) return base + still + Math.max(0, Math.min(half - 1, Math.round(sp * half) - 1));
        int n = span - half;
        return base + still + half + Math.max(0, Math.min(n - 1, Math.round(sp * n) - 1));
    }

    /** Channel numbers (1-based) of an attribute in this mode, coarse then fine; empty if not in the mode. */
    public int[] channelsOf(Attr a) {
        int coarse = -1, fine = -1;
        for (int i = 0; i < channels.length; i++) {
            if (channels[i].attr != a) continue;
            if (channels[i].kind == Kind.ATTR || channels[i].kind == Kind.SHUTTER_FX || channels[i].kind == Kind.GOBO_FX) coarse = i + 1;
            if (channels[i].kind == Kind.FINE) fine = i + 1;
        }
        if (coarse < 0) return new int[0];
        return fine < 0 ? new int[] {coarse} : new int[] {coarse, fine};
    }

    // ------------------------------------------------------------ value mappings (shared by the decoder and the exported profiles)
    /** Gobo wheel: slot k (0 = open, 1..8 = gobos) is DMX {@code k * 28 .. k * 28 + 27} (the last slot to 255). */
    public static final int GOBO_STEP = 28;
    /** Strobe: 0..7 shutter open, 8..255 = 1 Hz .. the maximum rate of the fixture. */
    public static final int STROBE_OPEN = 8;
    /** Gobo / prism rotation: 124..131 stopped, below = one way (faster towards 0), above = the other way (faster towards 255). */
    public static final int ROT_STOP_LO = 124, ROT_STOP_HI = 131;
    /** Prism: 0..127 out, 128..255 in. */
    public static final int PRISM_IN = 128;

    /** Normalised value of an attribute from its DMX value (8-bit {@code raw} or 16-bit when {@code wide}). */
    public static float decode(FixtureProfile p, Attr a, int value, boolean wide) {
        int raw = wide ? value >> 8 : value;
        float lin = wide ? value / 65535f : value / 255f;
        switch (a) {
            case STROBE: case TOP_STROBE: case MID_STROBE: case BOT_STROBE: return raw < STROBE_OPEN ? 0f : (raw - STROBE_OPEN) / (float) (255 - STROBE_OPEN);
            case GOBO: case GOBO_2: {
                int slot = Math.min(p.gobos, raw / GOBO_STEP);
                return p.gobos <= 0 ? 0f : slot / (float) p.gobos;
            }
            case GOBO_ROT: case GOBO_2_ROT: case PRISM_ROT: case PRISM_2_ROT:
                if (raw >= ROT_STOP_LO && raw <= ROT_STOP_HI) return 0.5f;
                return raw < ROT_STOP_LO ? 0.5f * raw / ROT_STOP_LO : 0.5f + 0.5f * (raw - ROT_STOP_HI) / (255f - ROT_STOP_HI);
            case PRISM: case PRISM_2: return raw >= PRISM_IN ? 1f : 0f;
            default: return lin;
        }
    }

    /** The DMX value (8-bit, or 16-bit when {@code wide}) that gives an attribute value (inverse of {@link #decode}, for tests and exports). */
    public static int encode(FixtureProfile p, Attr a, float v, boolean wide) {
        v = Math.max(0f, Math.min(1f, v));
        switch (a) {
            case STROBE: case TOP_STROBE: case MID_STROBE: case BOT_STROBE: return v <= 0f ? 0 : STROBE_OPEN + Math.round(v * (255 - STROBE_OPEN));
            case GOBO: case GOBO_2: return p.gobos <= 0 ? 0 : Math.round(v * p.gobos) * GOBO_STEP + GOBO_STEP / 2;
            case GOBO_ROT: case GOBO_2_ROT: case PRISM_ROT: case PRISM_2_ROT:
                if (Math.abs(v - 0.5f) < 1e-4f) return 128;
                return v < 0.5f ? Math.round(v / 0.5f * ROT_STOP_LO) : ROT_STOP_HI + Math.round((v - 0.5f) / 0.5f * (255 - ROT_STOP_HI));
            case PRISM: case PRISM_2: return v >= 0.5f ? 255 : 0;
            default: return wide ? Math.round(v * 65535f) : Math.round(v * 255f);
        }
    }

    /** Colour of the colour macro for its DMX value. */
    public static int macroColor(int raw) {
        return MACRO_COLORS[Math.min(MACRO_COLORS.length - 1, raw / MACRO_STEP)];
    }
}
