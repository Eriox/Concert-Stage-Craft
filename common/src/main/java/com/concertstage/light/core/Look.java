package com.concertstage.light.core;

import java.util.Arrays;

/**
 * Sparse values of one fixture: a value per attribute (NaN = not set), and optionally per emitter zone (pixel mapping): red, green,
 * blue and level of each zone (NaN = follows the fixture colour / dimmer). Used by the programmer, the cues and the presets.
 */
public final class Look {
    public final float[] v = new float[Attr.COUNT];
    /** Zone values (4 per zone: r, g, b, level), or null when no zone is programmed. */
    public float[] zones;

    public Look() {
        Arrays.fill(v, Float.NaN);
    }

    public Look copy() {
        Look l = new Look();
        System.arraycopy(v, 0, l.v, 0, v.length);
        if (zones != null) l.zones = zones.clone();
        return l;
    }

    public boolean has(Attr a) {
        return !Float.isNaN(v[a.ordinal()]);
    }

    public float get(Attr a) {
        return v[a.ordinal()];
    }

    public Look set(Attr a, float value) {
        v[a.ordinal()] = Float.isNaN(value) ? Float.NaN : FixtureProfile.clamp01(value);
        return this;
    }

    public void clear(Attr a) {
        v[a.ordinal()] = Float.NaN;
    }

    public boolean isEmpty() {
        for (float f : v) if (!Float.isNaN(f)) return false;
        if (zones != null) for (float f : zones) if (!Float.isNaN(f)) return false;
        return true;
    }

    /** Sets zone {@code z} of a fixture with {@code count} zones (allocates the zone array on first use). */
    public void setZone(int z, int count, float r, float g, float b, float level) {
        if (z < 0 || z >= count) return;
        if (zones == null || zones.length != count * 4) {
            float[] n = new float[count * 4];
            Arrays.fill(n, Float.NaN);
            if (zones != null) System.arraycopy(zones, 0, n, 0, Math.min(zones.length, n.length));
            zones = n;
        }
        zones[z * 4] = clampOrNaN(r);
        zones[z * 4 + 1] = clampOrNaN(g);
        zones[z * 4 + 2] = clampOrNaN(b);
        zones[z * 4 + 3] = clampOrNaN(level);
    }

    /** Copies every value set in {@code other} over this one (tracking: later values win). */
    public void merge(Look other) {
        for (int i = 0; i < v.length; i++) if (!Float.isNaN(other.v[i])) v[i] = other.v[i];
        if (other.zones != null) {
            if (zones == null || zones.length != other.zones.length) {
                zones = new float[other.zones.length];
                Arrays.fill(zones, Float.NaN);
            }
            for (int i = 0; i < zones.length; i++) if (!Float.isNaN(other.zones[i])) zones[i] = other.zones[i];
        }
    }

    /** Keeps only the attributes of the given families ("intensity", "position", "color", "beam"); zones count as color. */
    public Look filter(java.util.Set<String> families) {
        Look l = new Look();
        for (Attr a : Attr.ALL) if (families.contains(a.family)) l.v[a.ordinal()] = v[a.ordinal()];
        if (families.contains("color") && zones != null) l.zones = zones.clone();
        return l;
    }

    private static float clampOrNaN(float f) {
        return Float.isNaN(f) ? Float.NaN : FixtureProfile.clamp01(f);
    }

    // ------------------------------------------------------------ codec
    public void write(Bytes.Writer w) {
        // Extended look marker was impossible in the legacy 16-attribute mask.
        // Keep reading old masks; use an explicitly tagged 64-bit mask for new looks.
        long mask = 0;
        if (v.length > 63) throw new IllegalStateException("Too many fixture attributes");
        for (int i = 0; i < v.length; i++) if (!Float.isNaN(v[i])) mask |= 1L << i;
        w.varInt(-1).varLong(mask);
        for (int i = 0; i < v.length; i++) if ((mask & (1L << i)) != 0) w.unit(v[i]);
        if (zones == null) { w.varInt(0); return; }
        w.varInt(zones.length / 4);
        for (float f : zones) w.unit(f);
    }

    public static Look read(Bytes.Reader r) {
        Look l = new Look();
        int prefix = r.varInt();
        long mask = prefix == -1 ? r.varLong() : Integer.toUnsignedLong(prefix);
        for (int i = 0; i < Attr.COUNT; i++) if ((mask & (1L << i)) != 0) l.v[i] = r.unit();
        int nz = r.count(256);
        if (nz > 0) {
            l.zones = new float[nz * 4];
            for (int i = 0; i < l.zones.length; i++) l.zones[i] = r.unit();
        }
        return l;
    }
}
