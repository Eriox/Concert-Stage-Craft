package com.concertstage.client.light;

/**
 * CSC PROFESSIONAL LIGHTING RENDER ENGINE — diagnostics (1.2.0 rev. 16). Isolation views to tell a beam problem from a lighting problem,
 * and counters of the frame for the F3 screen (LightBatch#stats). Off by default; when off it costs one int comparison per call.
 *
 * <ul>
 * <li>{@link #VOLUME}: only the beams in the air (volume shader or ribbons);</li>
 * <li>{@link #SURFACE}: only the light received by the surfaces (projector shader, point lights, decals, Shimmer);</li>
 * <li>{@link #EMISSIVE}: only the lenses, cells and halos;</li>
 * <li>{@link #OCCLUSION}: the shadows of the structures: on surfaces green = lit, red = behind an obstacle as seen from the fixture; in
 * the air only the shadowed parts of the beams;</li>
 * <li>{@link #MASK}: the common optical mask itself on the surfaces (distribution x gobo x prism, white), without colour or distance;</li>
 * <li>{@link #DEPTH} (rev. 16c): the depth the engine reads (only surfaces really drawn: what is not in it cannot receive light);</li>
 * <li>{@link #NORMALS} (rev. 16c): the normals rebuilt from it, as the projected light uses them.</li>
 * </ul>
 * Chosen on the LUMICORE X1 (settings row, "Diagnostic"), client only, not saved.
 */
public final class LightDiagnostics {
    public static final int OFF = 0, VOLUME = 1, SURFACE = 2, EMISSIVE = 3, OCCLUSION = 4, MASK = 5, DEPTH = 6, NORMALS = 7;
    public static final String[] NAMES = {"Off", "Beams only", "Surfaces only", "Emitters only", "Visibility / shadows", "Optical masks",
        "Depth", "Normals"};
    public static int mode = OFF;

    private LightDiagnostics() {}

    public static void next() {
        mode = (mode + 1) % NAMES.length;
    }

    /** DebugMode uniform of the light shaders. */
    static int shaderMode() {
        return mode == OCCLUSION ? 4 : mode == MASK ? 5 : mode == DEPTH ? 6 : mode == NORMALS ? 7 : 0;
    }

    /** Full-screen views of the depth buffer and its normals (VolumeLights draws them, nothing else of the light). */
    static boolean bufferView() {
        return mode == DEPTH || mode == NORMALS;
    }

    static boolean air() {
        return mode == OFF || mode == VOLUME || mode == OCCLUSION;
    }

    static boolean surfaces() {
        return mode == OFF || mode == SURFACE || mode == OCCLUSION || mode == MASK;
    }
    // DEPTH / NORMALS: air(), surfaces(), emitters(), pointLights() all false: only the buffer view is drawn

    static boolean emitters() {
        return mode == OFF || mode == EMISSIVE;
    }

    /** Point lights / Shimmer lights (light received by surfaces, not shaped by the mask). */
    static boolean pointLights() {
        return mode == OFF || mode == SURFACE;
    }

    // ---- counters of the frame (the renderer adds, the F3 line reads the last complete frame)
    static int fixtures, beams, volumes, projections, gobos, prisms;
    private static int lFixtures, lBeams, lGobos, lPrisms;
    private static int[] feedCounts = new int[6];
    private static boolean feedSide;

    /** Rev. 3b: a LiveVision camera image counts apart from the main view (LightTargets). */
    static void swapFeed() {
        int[] m = {fixtures, beams, volumes, projections, gobos, prisms};
        feedSide = !feedSide;
        int[] n = feedSide ? new int[6] : feedCounts;       // a camera image starts from zero; the main view gets its own back
        fixtures = n[0]; beams = n[1]; volumes = n[2]; projections = n[3]; gobos = n[4]; prisms = n[5];
        feedCounts = m;
    }

    static void endFrame() {
        lFixtures = fixtures; lBeams = beams; lGobos = gobos; lPrisms = prisms;
        fixtures = beams = volumes = projections = gobos = prisms = 0;
    }

    static String stats() {
        return "CSC optics: " + lFixtures + " lit fixtures drawn, " + lBeams + " beams, " + lGobos + " gobos, " + lPrisms + " prisms"
            + (mode == OFF ? "" : " | DIAGNOSTIC " + NAMES[mode]);
    }
}
