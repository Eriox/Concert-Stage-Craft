package com.concertstage.light.core;

/**
 * Rev. 21 — the light zones of a zone fixture (PULSESTRIKE M, FLASHCORE FX) at one instant: each zone (top bar, centre strobe,
 * bottom bar) flashes at its own rate, the others stay as they are. Pure function of the engine output and the time, used by the
 * renderer every frame and by the tests (tools/rev21).
 *
 * <p>The renderer draws a fixture with one intensity and one cell array. Here the intensity is the brightest zone at this instant and
 * each cell keeps its own level times (its zone at this instant / that intensity): a cell lights exactly as bright as its zone
 * says, a zone between two flashes is dark while a steady zone next to it stays lit, and the light the fixture throws (beam,
 * projection) is the mean of what its cells emit at this instant — a centre flash with steady red bars throws red light with a
 * white burst, never a dark gap.
 *
 * <p>Top and bottom strobing at the same rate flash in turn (the bottom half a period late): the classic alternation of these
 * fixtures. The shared strobe (Attr.STROBE, every zone without its own) flashes all the zones together.
 */
public final class ZoneStrobe {
    private ZoneStrobe() {}

    /** Half a period late for the bottom bar when it alternates with the top (Output#alternate), none otherwise. */
    public static double phaseOffset(int section, boolean alternate) {
        return alternate && section == FixtureProfile.BOTTOM ? 0.5 : 0.0;
    }

    /**
     * Fills {@code outZones} (r, g, b, level per cell, as {@code zones}) for this instant and {@code outBeam} (r, g, b of the light
     * thrown, flash 1 / 0). Returns the intensity to draw the fixture with (0: dark).
     *
     * @param master    the dimmer of the fixture (masters applied)
     * @param alternate top and bottom strobe with their own channels at the same rate (Output#alternate)
     */
    public static float frame(FixtureProfile pr, float master, float[] zones, float[] sectionHz, boolean alternate, double seconds,
                              float[] outZones, float[] outBeam) {
        return frame(pr, master, zones, sectionHz, alternate, seconds, outZones, outBeam, null);
    }

    /** Same, and {@code outFlash[k]}: zone k is in a flash at this instant (it strobes and is lit now); its cells get the flash look. */
    public static float frame(FixtureProfile pr, float master, float[] zones, float[] sectionHz, boolean alternate, double seconds,
                              float[] outZones, float[] outBeam, boolean[] outFlash) {
        int nz = pr.zones;
        float[] s = new float[3];
        float top = 0f;
        boolean flash = false;
        for (int k = 0; k < 3; k++) {
            double off = phaseOffset(k, alternate);
            s[k] = BeamOptics.strobeIntensity(master, sectionHz[k], seconds + (sectionHz[k] > 0.05f ? off / sectionHz[k] : 0.0));
            top = Math.max(top, s[k]);
            if (outFlash != null) outFlash[k] = sectionHz[k] > 0.05f && s[k] > 0f;
        }
        float sr = 0, sg = 0, sb = 0, sl = 0;
        for (int z = 0; z < nz && z * 4 + 3 < zones.length; z++) {
            int i = z * 4, k = Math.max(0, pr.section(z));
            outZones[i] = zones[i]; outZones[i + 1] = zones[i + 1]; outZones[i + 2] = zones[i + 2];
            float lv = top > 0f ? zones[i + 3] * s[k] / top : 0f;
            outZones[i + 3] = Math.min(1f, lv);
            if (lv > 0.003f && sectionHz[k] > 0.05f) flash = true;
            sr += outZones[i] * outZones[i + 3]; sg += outZones[i + 1] * outZones[i + 3]; sb += outZones[i + 2] * outZones[i + 3]; sl += outZones[i + 3];
        }
        if (top <= 0f || sl < 1e-4f || nz == 0) {
            outBeam[0] = outBeam[1] = outBeam[2] = 0f;
            outBeam[3] = 0f;
            return 0f;
        }
        if (pr == FixtureProfile.PULSESTRIKE_M) {
            // the calibration of Engine#finishZones for the historical fixture: hue of the cells, level = their mean
            float m = Math.max(1e-4f, Math.max(sr, Math.max(sg, sb)));
            float avg = sl / nz;
            outBeam[0] = sr / m * avg; outBeam[1] = sg / m * avg; outBeam[2] = sb / m * avg;
        } else {
            outBeam[0] = sr / nz; outBeam[1] = sg / nz; outBeam[2] = sb / nz;
        }
        outBeam[3] = flash ? 1f : 0f;
        return top;
    }
}
