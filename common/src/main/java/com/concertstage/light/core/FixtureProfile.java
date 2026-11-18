package com.concertstage.light.core;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a fixture type can do. Only the capabilities that are implemented (engine, renderer and console) are listed: the console shows
 * exactly these attributes for the selected fixtures, and nothing else is claimed.
 *
 * <p>The four CSC 1.2.0 fixtures. Their 3D models were rebuilt in Blender 5.3 in the design language of the 13 new fixtures
 * (source-assets/new_lights/Blender, CSC_VORTEX_X1 / CSC_LUMORA_W7 / CSC_LUMORA_W19 / CSC_PULSESTRIKE_M); tilt axis heights, zone
 * counts and zone order are those of the former models, so the tables below are unchanged:
 * <ul>
 * <li>VORTEX X1 — beam / spot: pan 540, tilt 270, dimmer, RGB colour mix, zoom 2–32°, focus, iris (beam mode), 8 rotating gobos,
 * 3-facet rotating prism, strobe.</li>
 * <li>LUMORA W7 — compact wash: pan 540, tilt 270, dimmer, RGBW, CTO, zoom 8–50°, 19 individually controllable LED cells, strobe.</li>
 * <li>LUMORA W19 — high power wash: pan 540, tilt 270, dimmer, RGBW, CTO, zoom 6–60°, 37 LED cells (pixel effects), strobe.</li>
 * <li>PULSESTRIKE M — motorised strobe / blinder: tilt 190 (no pan), dimmer, 14 RGB matrix zones, 28 white strip segments (CTO),
 * strobe up to 25 Hz (capped by the safety settings), zoom 20–70° (1.2.0 External Show Control; it replaces the angle the focus
 * used to set in rev. 9, the focus now only softens or sharpens the edge of its light).</li>
 * </ul>
 */
public final class FixtureProfile {
    public final String id, name, function;
    public final EnumSet<Attr> attrs;
    public final float panRange, tiltRange;
    public final float zoomMin, zoomMax;
    /** Degrees per second the motors can turn (the heads follow their target at this speed, like real fixtures). */
    public final float panSpeed, tiltSpeed;
    public final int gobos;
    public final float strobeMaxHz;
    /** Total emitter zones of the model (LED cells, RGB zones then white segments). */
    public final int zones, rgbZones, whiteZones;
    /** Relative brightness of the beam (render): the VORTEX shaft is narrow and very bright, the blinder is wide. */
    public final float output;
    /** Nominal beam length in blocks (render; cut where the beam hits a block). */
    public final float throwBlocks;
    /** Height of the tilt axis above the base (blocks, from the exported model). */
    public float tiltPivotY;
    /**
     * Size of the model in the world (1 = the Blender file at 1 m = 1 block). 1.2.0 rev. 2: the fixtures were too small next to the big
     * CSC structures; the Blender models are unchanged, only drawn larger (hit boxes, pivots, beam origin and aiming follow).
     */
    public float renderScale = 1f;

    private FixtureProfile(String id, String name, String function, EnumSet<Attr> attrs, float pan, float tilt, float zMin, float zMax,
                           float panSpeed, float tiltSpeed, int gobos, float strobe, int rgbZones, int whiteZones, int cellZones,
                           float output, float throwBlocks) {
        this.id = id; this.name = name; this.function = function; this.attrs = attrs;
        this.panRange = pan; this.tiltRange = tilt; this.zoomMin = zMin; this.zoomMax = zMax;
        this.panSpeed = panSpeed; this.tiltSpeed = tiltSpeed; this.gobos = gobos; this.strobeMaxHz = strobe;
        this.rgbZones = rgbZones; this.whiteZones = whiteZones; this.zones = rgbZones + whiteZones + cellZones;
        this.output = output; this.throwBlocks = throwBlocks;
        this.tiltZero = tilt > 0 ? 90f : 0f;                  // rev. 4c: the bars and strobes too (reported 13 Nov.: "tilt 0 on Art-Net is 90")
    }

    /**
     * Rev. 1.3.0-2 (reported 13 November: "tilt is wrong on all moving lights, 90 is straight and 0 is 90"): angle of the head model
     * (0 = beam horizontal, 90 = beam along the base axis, up when standing, down when hung) at the centre of the tilt (DMX 128). The
     * moving heads now have their tilt centred on the straight position, like real fixtures and their GDTF / MA files (0 = straight,
     * -135..+135 around it). Rev. 4c: the motorised bars and strobes (no pan) too: 0 on the desk is straight, along the base axis.
     */
    public final float tiltZero;

    public static final FixtureProfile VORTEX_X1 = new FixtureProfile("csc_vortex_x1", "CSC VORTEX X1", "Beam / Spot",
        EnumSet.of(Attr.DIMMER, Attr.PAN, Attr.TILT, Attr.RED, Attr.GREEN, Attr.BLUE, Attr.ZOOM, Attr.FOCUS, Attr.IRIS, Attr.GOBO,
            Attr.GOBO_ROT, Attr.PRISM, Attr.PRISM_ROT, Attr.STROBE),
        540, 270, 2, 32, 300, 220, 8, 20, 0, 0, 0, 1.6f, 72);
    public static final FixtureProfile LUMORA_W7 = new FixtureProfile("csc_lumora_w7", "CSC LUMORA W7", "Wash (compact)",
        EnumSet.of(Attr.DIMMER, Attr.PAN, Attr.TILT, Attr.RED, Attr.GREEN, Attr.BLUE, Attr.WHITE, Attr.CTO, Attr.ZOOM, Attr.STROBE),
        540, 270, 8, 50, 240, 190, 0, 20, 0, 0, 19, 0.85f, 44);
    public static final FixtureProfile LUMORA_W19 = new FixtureProfile("csc_lumora_w19", "CSC LUMORA W19", "Wash (high power)",
        EnumSet.of(Attr.DIMMER, Attr.PAN, Attr.TILT, Attr.RED, Attr.GREEN, Attr.BLUE, Attr.WHITE, Attr.CTO, Attr.ZOOM, Attr.STROBE),
        540, 270, 6, 60, 200, 160, 0, 20, 0, 0, 37, 1.0f, 56);
    public static final FixtureProfile PULSESTRIKE_M = new FixtureProfile("csc_pulsestrike_m", "CSC PULSESTRIKE M", "Strobe / Blinder",
        EnumSet.of(Attr.DIMMER, Attr.TILT, Attr.RED, Attr.GREEN, Attr.BLUE, Attr.WHITE, Attr.CTO, Attr.STROBE, Attr.FOCUS, Attr.ZOOM),
        0, 190, 20, 70, 0, 140, 0, 25, 14, 28, 0, 1.2f, 30);

    /** New fixtures have independent RGBW pixel channels; legacy RGB personalities remain byte-identical. */
    public boolean rgbwPixels;
    public int prismFacets = 3;

    private static FixtureProfile professional(String id, String name, String family, float pan, float tilt,
            float lo, float hi, int gobos, int rgb, int white, int cells, float pivot, Attr... extras) {
        EnumSet<Attr> attrs = EnumSet.of(Attr.DIMMER, Attr.STROBE, Attr.RED, Attr.GREEN, Attr.BLUE);
        if (pan > 0) attrs.add(Attr.PAN);
        if (tilt > 0) attrs.add(Attr.TILT);
        if (hi > lo) attrs.add(Attr.ZOOM);
        java.util.Collections.addAll(attrs, extras);
        if (white > 0 && rgb == 0 && cells == 0) attrs.removeAll(EnumSet.of(Attr.RED, Attr.GREEN, Attr.BLUE));
        // rev. 19: motor speeds of each family (before: 300° / 220° per second for all thirteen): a beam is the fastest, a profile
        // (heavy optics, blades) the slowest of the spots, a pixel wash like the LUMORA, the tilt-only bars and strobes turn slowly
        float ps = family.contains("Beam") ? 420 : family.contains("Profile") ? 250 : family.contains("Wash") ? 240 : 300;
        float ts = family.contains("Beam") ? 300 : family.contains("Profile") ? 190 : family.contains("Wash") ? 190 : pan > 0 ? 220 : 140;
        FixtureProfile p = new FixtureProfile(id, name, family, attrs, pan, tilt, lo, hi,
                pan > 0 ? ps : 0, tilt > 0 ? ts : 0, gobos, 25, rgb, white, cells, 1f, gobos > 0 ? 72 : 44);
        p.tiltPivotY = pivot;
        p.rgbwPixels = cells > 0 && attrs.contains(Attr.WHITE);
        return p;
    }

    // rev. 1.3.0-2 (reported 13 November: "the Profile has a duplicate RGB colour with the sub-CMY, it's RGB, not CMY"): the PHANTOM S1
    // and the PROFILE P1 mix their colour in RGB only, the CMY channels that doubled it are gone (in the game and in the patch files)
    public static final FixtureProfile PHANTOM_S1 = professional("csc_phantom_s1", "CSC PHANTOM S1", "Moving Spot", 540, 270, 5, 45, 8, 0, 0, 0, 0.5f, Attr.FOCUS, Attr.IRIS, Attr.GOBO, Attr.GOBO_ROT, Attr.PRISM, Attr.PRISM_ROT, Attr.FROST, Attr.MOVE_SPEED, Attr.GOBO_2, Attr.GOBO_2_ROT, Attr.ANIMATION, Attr.PRISM_2, Attr.PRISM_2_ROT, Attr.SHUTTER);
    public static final FixtureProfile PROFILE_P1 = professional("csc_profile_p1", "CSC PROFILE P1", "Moving Profile", 540, 270, 7, 50, 8, 0, 0, 0, 0.44f, Attr.FOCUS, Attr.IRIS, Attr.GOBO, Attr.GOBO_ROT, Attr.PRISM, Attr.PRISM_ROT, Attr.FROST, Attr.BLADE_1, Attr.BLADE_2, Attr.BLADE_3, Attr.BLADE_4, Attr.FRAME_ROT, Attr.MOVE_SPEED);
    public static final FixtureProfile RAZOR_B1 = professional("csc_razor_b1", "CSC RAZOR B1", "Moving Beam", 540, 270, 1, 4, 8, 0, 0, 0, 0.33f, Attr.COLOR_WHEEL, Attr.FOCUS, Attr.GOBO, Attr.PRISM, Attr.PRISM_ROT, Attr.FROST, Attr.MOVE_SPEED, Attr.GOBO_ROT);
    public static final FixtureProfile FLASHCORE_FX = professional("csc_flashcore_fx", "CSC FLASHCORE FX", "Hybrid Strobe", 0, 190, 30, 80, 0, 12, 12, 0, 0.165f, Attr.WHITE, Attr.CTO, Attr.MOVE_SPEED);
    public static final FixtureProfile PIXELBAR_B16 = professional("csc_pixelbar_b16", "CSC PIXELBAR B16", "Motorized LED Bar", 0, 190, 5, 50, 0, 0, 0, 16, 0.205f, Attr.WHITE, Attr.MOVE_SPEED);
    public static final FixtureProfile PIXELTUBE_T1 = professional("csc_pixeltube_t1", "CSC PIXELTUBE T1", "RGB LED Tube", 0, 0, 110, 110, 0, 0, 0, 16, 0.12f, Attr.CTO);
    public static final FixtureProfile BLINDER_X2 = professional("csc_blinder_x2", "CSC BLINDER X2", "Dual Blinder", 0, 0, 90, 90, 0, 0, 2, 0, 0.155f, Attr.WHITE, Attr.CTO);
    public static final FixtureProfile BLINDER_X4 = professional("csc_blinder_x4", "CSC BLINDER X4", "Quad Blinder", 0, 0, 90, 90, 0, 0, 4, 0, 0.23f, Attr.WHITE, Attr.CTO);
    public static final FixtureProfile BLINDER_X8 = professional("csc_blinder_x8", "CSC BLINDER X8", "Mega Blinder", 0, 0, 90, 90, 0, 0, 8, 0, 0.26f, Attr.WHITE, Attr.CTO);
    public static final FixtureProfile FOLLOWSPOT_F1 = professional("csc_followspot_f1", "CSC FOLLOWSPOT F1", "Followspot", 0, 0, 3, 20, 0, 0, 0, 0, 1.46f, Attr.FOCUS, Attr.IRIS, Attr.DOUSER, Attr.COLOR_WHEEL);
    public static final FixtureProfile PIXELWASH_W37 = professional("csc_pixelwash_w37", "CSC PIXELWASH W37", "Pixel Wash", 540, 270, 6, 60, 0, 0, 0, 37, 0.43f, Attr.WHITE, Attr.CTO, Attr.MOVE_SPEED);
    public static final FixtureProfile LASERCORE_L1 = professional("csc_lasercore_l1", "CSC LASERCORE L1", "RGB Laser", 0, 0, 0.6f, 0.6f, 0, 0, 0, 0, 0.165f, Attr.DOUSER, Attr.LASER_PATTERN, Attr.LASER_SIZE, Attr.LASER_SPEED);
    public static final FixtureProfile FLOODLIGHT_F8 = professional("csc_floodlight_f8", "CSC FLOODLIGHT F8", "Architectural Floodlight", 0, 0, 75, 75, 0, 0, 0, 8, 0.215f, Attr.WHITE, Attr.CTO);

    // 1.2.1 (asked 15 November: "there are no conventionals in your mod"): the conventional halogen fixtures. One dimmer channel (a
    // dimmer pack), a fixed beam angle, no motor: aimed by hand with the angle tool, coloured with a gel slid in front of the lens
    // (Fixture Angle Tool screen). Fictional fixtures: the angles are the design of the mod, not the data of a real product.
    public static final FixtureProfile PAR_P64 = conventional("csc_par_p64", "CSC PAR P64", "PAR Can (halogen)", 24, 1.0f, 44, 0.45f, 0.17f);
    public static final FixtureProfile FRESNEL_F1 = conventional("csc_fresnel_f1", "CSC FRESNEL F1", "Fresnel (halogen)", 40, 0.85f, 40, 0.15f, 0.19f);
    public static final FixtureProfile PC_C1 = conventional("csc_pc_c1", "CSC PC C1", "Plano-Convex (halogen)", 30, 0.9f, 44, 0.55f, 0.19f);
    public static final FixtureProfile ELLIPSOIDAL_E1 = conventional("csc_ellipsoidal_e1", "CSC ELLIPSOIDAL E1", "Ellipsoidal Profile (halogen)", 26, 1.1f, 60, 1f, 0.2f);

    /** 1.2.1: a conventional halogen fixture (dimmer only, fixed angle, manual tilt, gel). */
    public boolean conventional;
    /** 1.2.1: edge sharpness of a fixture without a focus channel (1 = hard, the default; a Fresnel is soft). */
    public float fixedSharpness = 1f;

    private static FixtureProfile conventional(String id, String name, String family, float angle, float output, float throwBlocks, float sharpness, float pivot) {
        FixtureProfile p = new FixtureProfile(id, name, family, EnumSet.of(Attr.DIMMER), 0, 0, angle, angle, 0, 0, 0, 0, 0, 0, 0, output, throwBlocks);
        p.tiltPivotY = pivot;
        p.conventional = true;
        p.fixedSharpness = sharpness;
        return p;
    }

    private static final Map<String, FixtureProfile> BY_ID = new LinkedHashMap<>();
    static {
        // rev. 3: half as large again as rev. 2 (1.30 / 1.25 / 1.40 / 1.35), still too small next to the big structures
        VORTEX_X1.renderScale = 1.95f;
        LUMORA_W7.renderScale = 1.90f;
        LUMORA_W19.renderScale = 2.10f;
        PULSESTRIKE_M.renderScale = 2.60f;          // rev. 6: +30 % (2.00 before)
        // height of the tilt axis above the base, at the size drawn in the world (used to aim the positions)
        VORTEX_X1.tiltPivotY = 0.361f * VORTEX_X1.renderScale;
        LUMORA_W7.tiltPivotY = 0.322f * LUMORA_W7.renderScale;
        LUMORA_W19.tiltPivotY = 0.396f * LUMORA_W19.renderScale;
        PULSESTRIKE_M.tiltPivotY = 0.222f * PULSESTRIKE_M.renderScale;
        // 1.2.0 rev. 17: the 13 new fixtures were drawn at the size of their Blender file (1 m = 1 block), half the size of the four
        // historical ones next to them. They get the same treatment: the factor of the historical fixture of the same family
        // (moving heads x1.95 like the VORTEX, pixel wash x2.10 like the W19, strobe x2.60 like the PULSESTRIKE), the bars, panels
        // and blinders x2.0 to 2.2, the followspot x1.5 (2.5 blocks with its stand, taller than a player, not a tower). Their real
        // proportions between them are kept (a PHANTOM stays larger than a RAZOR). The four historical fixtures are untouched.
        PHANTOM_S1.renderScale = 1.95f;
        PROFILE_P1.renderScale = 1.95f;
        RAZOR_B1.renderScale = 1.95f;
        PIXELWASH_W37.renderScale = 2.10f;
        FLASHCORE_FX.renderScale = 2.60f;
        PIXELBAR_B16.renderScale = 2.0f;
        PIXELTUBE_T1.renderScale = 2.0f;
        BLINDER_X2.renderScale = 2.2f;
        BLINDER_X4.renderScale = 2.2f;
        BLINDER_X8.renderScale = 2.2f;
        FOLLOWSPOT_F1.renderScale = 1.5f;
        LASERCORE_L1.renderScale = 2.2f;
        FLOODLIGHT_F8.renderScale = 2.0f;
        for (FixtureProfile p : new FixtureProfile[] {PAR_P64, FRESNEL_F1, PC_C1, ELLIPSOIDAL_E1}) {
            p.renderScale = 2.0f;                          // 1.2.1: drawn like the blinders next to them
            p.tiltPivotY *= p.renderScale;
        }
        for (FixtureProfile p : new FixtureProfile[] {PHANTOM_S1, PROFILE_P1, RAZOR_B1, FLASHCORE_FX, PIXELBAR_B16, PIXELTUBE_T1, BLINDER_X2, BLINDER_X4, BLINDER_X8, FOLLOWSPOT_F1, PIXELWASH_W37, LASERCORE_L1, FLOODLIGHT_F8})
            p.tiltPivotY *= p.renderScale;                 // set at the Blender size by professional(): now at the size drawn
        for (FixtureProfile p : new FixtureProfile[] {VORTEX_X1, LUMORA_W7, LUMORA_W19, PULSESTRIKE_M, PHANTOM_S1, PROFILE_P1, RAZOR_B1, FLASHCORE_FX, PIXELBAR_B16, PIXELTUBE_T1, BLINDER_X2, BLINDER_X4, BLINDER_X8, FOLLOWSPOT_F1, PIXELWASH_W37, LASERCORE_L1, FLOODLIGHT_F8,
                PAR_P64, FRESNEL_F1, PC_C1, ELLIPSOIDAL_E1}) {
            if (BY_ID.put(p.id, p) != null) throw new IllegalStateException("Duplicate fixture " + p.id);
        }
        RAZOR_B1.prismFacets = 8;
        // rev. 21: the three light zones, read from the models (assets/concertstage/lightshow/<id>.json, zone names and centres):
        // PULSESTRIKE M — RGB_01..07 a row of 7 RGB cells below the centre (y -0.046), RGB_08..14 a row of 7 above (y +0.046),
        //   STROBE_01..28 two rows of 14 white segments in the middle (y -0.010 / +0.010);
        // FLASHCORE FX — RGB_B_01..06 the bottom row of 6 RGB cells (y -0.058), RGB_T_01..06 the top row (y +0.058), STROBE_01..12 one
        //   row of 12 white segments in the middle (y 0). Same layout: top RGB bar, centre white strobe, bottom RGB bar.
        // Top / bottom are those of the fixture (its base under it); hung upside down, its "top" bar is the lower one in the world.
        PULSESTRIKE_M.sections(new int[] {7, 14}, new int[] {0, 7}, new int[] {14, 42});
        FLASHCORE_FX.sections(new int[] {6, 12}, new int[] {0, 6}, new int[] {12, 24});
    }

    /** Rev. 21: zone of each emitter cell: {@link #TOP}, {@link #MID}, {@link #BOTTOM}, -1 none (fixtures without light zones). */
    public static final int TOP = 0, MID = 1, BOTTOM = 2;
    private int[] sectionOf;

    private void sections(int[] top, int[] bottom, int[] mid) {
        sectionOf = new int[zones];
        java.util.Arrays.fill(sectionOf, -1);
        for (int z = top[0]; z < top[1]; z++) sectionOf[z] = TOP;
        for (int z = bottom[0]; z < bottom[1]; z++) sectionOf[z] = BOTTOM;
        for (int z = mid[0]; z < mid[1]; z++) sectionOf[z] = MID;
        for (int k : sectionOf) if (k < 0) throw new IllegalStateException(id + ": a cell outside the light zones");
        java.util.Collections.addAll(attrs, Attr.TOP_DIM, Attr.TOP_RED, Attr.TOP_GREEN, Attr.TOP_BLUE, Attr.TOP_STROBE, Attr.MID_DIM,
            Attr.MID_STROBE, Attr.BOT_DIM, Attr.BOT_RED, Attr.BOT_GREEN, Attr.BOT_BLUE, Attr.BOT_STROBE);
    }

    /** Rev. 21: the fixture has independent light zones (PULSESTRIKE M, FLASHCORE FX). */
    public boolean hasSections() {
        return sectionOf != null;
    }

    /** Rev. 21: the light zone of emitter cell {@code zone}, or -1. */
    public int section(int zone) {
        return sectionOf == null || zone < 0 || zone >= sectionOf.length ? -1 : sectionOf[zone];
    }

    /** Rev. 21: intensity, strobe and colour (red; green and blue follow) attributes of a light zone; colour null for the centre. */
    public static Attr sectionDim(int k) { return k == TOP ? Attr.TOP_DIM : k == MID ? Attr.MID_DIM : Attr.BOT_DIM; }
    public static Attr sectionStrobe(int k) { return k == TOP ? Attr.TOP_STROBE : k == MID ? Attr.MID_STROBE : Attr.BOT_STROBE; }
    public static Attr sectionRed(int k) { return k == TOP ? Attr.TOP_RED : k == BOTTOM ? Attr.BOT_RED : null; }
    public static final String[] SECTION_NAMES = {"Top", "Centre", "Bottom"};

    public static int indexOf(FixtureProfile profile) {
        int i = 0;
        for (FixtureProfile p : BY_ID.values()) { if (p == profile) return i; i++; }
        throw new IllegalArgumentException("Unregistered fixture");
    }
    public boolean isSpot() { return zones == 0; }
    public boolean isPanel() { return whiteZones > 0 || this == PIXELBAR_B16 || this == PIXELTUBE_T1 || this == FLOODLIGHT_F8; }
    public int autoRole() { return this == PULSESTRIKE_M || this == FLASHCORE_FX || whiteZones > 0 ? 2 : isSpot() ? 0 : 1; }

    public static FixtureProfile get(String id) {
        return BY_ID.get(id);
    }

    public static java.util.Collection<FixtureProfile> all() {
        return java.util.Collections.unmodifiableCollection(BY_ID.values());
    }

    /**
     * Value of an attribute nothing controls. Same as {@link Attr#home()} except the zoom of the PULSESTRIKE M, which rests at its widest
     * (70°, the blinder flood it had before it got a zoom).
     */
    public float home(Attr a) {
        if (a == Attr.ZOOM && this == PULSESTRIKE_M) return 1f;
        return a.home();
    }

    public boolean has(Attr a) {
        return attrs.contains(a);
    }

    public boolean hasPan() {
        return attrs.contains(Attr.PAN);
    }

    /** Pan in degrees from the neutral position for a normalised value. */
    public float panDegrees(float v) {
        return (v - 0.5f) * panRange;
    }

    /** Angle of the head model (degrees, 0 = beam horizontal, +90 = beam along the base axis) for a normalised tilt. */
    public float tiltDegrees(float v) {
        return (v - 0.5f) * tiltRange + tiltZero;
    }

    /** Lowest and highest head model angle the tilt reaches. */
    public float tiltMin() {
        return tiltZero - tiltRange / 2;
    }

    public float tiltMax() {
        return tiltZero + tiltRange / 2;
    }

    /** Normalised pan for an angle in degrees (clamped to the mechanical range). */
    public float panValue(float degrees) {
        return panRange <= 0 ? 0.5f : clamp01(degrees / panRange + 0.5f);
    }

    public float tiltValue(float degrees) {
        return tiltRange <= 0 ? 0.5f : clamp01((degrees - tiltZero) / tiltRange + 0.5f);
    }

    /** Beam angle in degrees for a zoom value. */
    public float beamAngle(float zoom) {
        return zoomMin + (zoomMax - zoomMin) * clamp01(zoom);
    }

    public float strobeHz(float v) {
        return v <= 0.001f ? 0f : 1f + (strobeMaxHz - 1f) * clamp01(v);
    }

    /** Index of the RGB matrix zone / white segment / LED cell {@code i}: 0 RGB, 1 white, 2 cell. */
    public int zoneKind(int i) {
        if (i < rgbZones) return 0;
        if (i < rgbZones + whiteZones) return 1;
        return 2;
    }

    static float clamp01(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }
}
