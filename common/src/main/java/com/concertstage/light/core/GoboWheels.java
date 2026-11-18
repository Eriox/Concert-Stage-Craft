package com.concertstage.light.core;

/**
 * Rev. 22 — the gobo wheels of the CSC fixtures: which pictures each fixture carries and what each slot is called. The pictures are
 * drawn by {@code tools/gobos/make_gobos.py} (geometry only) into {@code textures/lightshow/<file>.png}; the same names go into the
 * exported profiles (GDTF wheels, QLC+ capabilities, grandMA2 / dot2 channel sets). Before, the four gobo fixtures shared the wheel of
 * the VORTEX X1. The VORTEX keeps it, and the PHANTOM S1 keeps its second wheel: a show programmed before looks the same on them.
 * Pure Java (no game class).
 */
public final class GoboWheels {
    private GoboWheels() {}

    /** A wheel: the texture file of each slot (slot 1 first) and its name. */
    public record Wheel(String key, String[] files, String[] names) {
        public int size() { return files.length; }
        /** File of slot {@code slot} (1-based); slot 0 (open) has none. */
        public String file(int slot) { return slot < 1 || slot > files.length ? null : files[slot - 1]; }
        public String name(int slot) { return slot < 1 || slot > names.length ? "Open" : names[slot - 1]; }
    }

    public static final Wheel VORTEX = wheel("vortex", "gobo_%d", "Flower", "Dots", "Swirl", "Target", "Starburst", "Bars", "Waves", "Clover");
    public static final Wheel PHANTOM = wheel("phantom", "gobo_phantom_%d", "Tunnel", "Sun", "Honeycomb", "Orbit", "Flames", "Half moon", "Propeller", "Triangles");
    public static final Wheel PHANTOM_2 = wheel("phantom_2", "gobo_2_%d", "Six rays", "Ring", "Cross", "Bars", "Fine star", "Square", "Diagonals", "Rings");
    public static final Wheel PROFILE = wheel("profile", "gobo_profile_%d", "Window", "Leaves", "Clouds", "Blinds", "Arch window", "Star field", "Water", "Cells");
    public static final Wheel RAZOR = wheel("razor", "gobo_razor_%d", "Pinhole", "Ring", "Triangle", "Three dots", "Star", "Slit", "Double ring", "Six dots");

    private static Wheel wheel(String key, String pattern, String... names) {
        String[] files = new String[names.length];
        for (int i = 0; i < names.length; i++) files[i] = String.format(pattern, i + 1);
        return new Wheel(key, files, names);
    }

    /** First gobo wheel of a fixture profile, or null when it has none. */
    public static Wheel first(FixtureProfile p) {
        if (p == null || p.gobos <= 0) return null;
        if (p == FixtureProfile.PHANTOM_S1) return PHANTOM;
        if (p == FixtureProfile.PROFILE_P1) return PROFILE;
        if (p == FixtureProfile.RAZOR_B1) return RAZOR;
        return VORTEX;
    }

    /** Second gobo wheel, or null. */
    public static Wheel second(FixtureProfile p) {
        return p != null && p.has(Attr.GOBO_2) ? PHANTOM_2 : null;
    }

    /** Slot (0 = open, 1..n) for a GOBO attribute value in [0, 1], as the renderer and the DMX decoding agree. */
    public static int slot(FixtureProfile p, float value) {
        if (p == null || p.gobos <= 0) return 0;
        return Math.max(0, Math.min(p.gobos, Math.round(value * p.gobos)));
    }
}
