package com.concertstage.light.core;

import java.util.ArrayList;
import java.util.List;

/**
 * CSC PROFESSIONAL LIGHTING RENDER ENGINE — Optical Simulation System (1.2.0 rev. 16), pure Java so it can be tested without the game.
 *
 * <p>Each fixture family has its own <b>light distribution</b>: how the light is spread over a cross-section of the beam at a given
 * distance from the lens. It is the single optical mask of the engine: the GLSL include {@code concertstage_optics.glsl} evaluates the
 * same function, with the same parameters, for the beam in the haze (volume shader) and for the light on surfaces (projector shader), so
 * the shape seen in the air and the shape on the floor cannot differ. {@link #light} is the Java twin of {@code csc_light} (tests,
 * offline pictures); both are kept line for line equal.
 *
 * <ul>
 * <li>{@link #SPOT} (VORTEX X1, hybrid beam / spot): one optic. A narrow zoom gives a concentrated core (beam), a wide zoom an even disc
 * with an edge whose softness follows the focus (spot); the gobo is the transmission of the optic; a 3-facet prism splits it into three
 * smaller images.</li>
 * <li>{@link #LENS_ARRAY} (LUMORA W7, 19 lenses / W19, 37 lenses): the sum of the small beams of every lens, at their real places in the
 * model (grouped by ring). Near the fixture each lens shows as its own shaft; further away they overlap into an even wash. The W19 has
 * more rings, a smaller pitch relative to its face and a stronger skirt: a different distribution, not a brighter W7.</li>
 * <li>{@link #PANEL} (PULSESTRIKE M, strobe / blinder): a rectangular emitting face (the real extent of its cells). Near the face the light
 * keeps the rectangle and the grid of its cells; with the distance (and the zoom) it opens into a broad, flat, frontal flood.</li>
 * </ul>
 *
 * <p>Coordinates: {@code qx, qy} in the frame of the beam, divided by its half width {@code hw} at that distance (|q| = 1 is the edge of
 * the cone the renderer draws); {@code along}: blocks from the lens. The mean of the distribution over the disc |q| ≤ 1 is about 1 for
 * every family, so the energy calibration of the renderer stays the same whatever the family.
 */
public final class OpticalProfile {
    public static final int SPOT = 0, LENS_ARRAY = 1, PANEL = 2;
    public static final int MAX_RINGS = 6;

    public final int kind;
    private float spotGain = 1f;
    /** Lens rings (LENS_ARRAY): radius (blocks, in the plane of the face), lens count, angle of the first lens (rad). */
    public final float[] ringR, ringN, ringPhase;
    public final int rings, lenses;
    /** Radius of one lens (LENS_ARRAY) — blocks. */
    public final float lensRadius;
    /** Skirt of scattered light around the main distribution (0..1). */
    public final float skirt;
    /** PANEL: half extent of the emitting face (blocks) and pitch of its cell grid. */
    public final float panelX, panelY, cellX, cellY;
    /**
     * Rev. 16b: colour groups of the emitter cells (index = zone of the fixture): LENS_ARRAY the ring of each lens, PANEL the column of
     * each cell (RGB cells and white segments that fall in it). {@link #groups} groups at most ({@link #MAX_TINTS}).
     */
    public final int[] groupOf;
    public final int groups;
    public final float[] groupWeight;
    public static final int MAX_TINTS = 8;

    private OpticalProfile(int kind, float[] rr, float[] rn, float[] rp, int rings, int lenses, float lensRadius, float skirt,
                           float panelX, float panelY, float cellX, float cellY, int[] groupOf, int groups, float[] groupWeight) {
        this.groupOf = groupOf; this.groups = groups; this.groupWeight = groupWeight;
        this.kind = kind;
        this.ringR = rr; this.ringN = rn; this.ringPhase = rp;
        this.rings = rings; this.lenses = lenses;
        this.lensRadius = lensRadius; this.skirt = skirt;
        this.panelX = panelX; this.panelY = panelY; this.cellX = cellX; this.cellY = cellY;
    }

    /**
     * The profile of a fixture type from its model: {@code cells} = centre x, y, half sizes hx, hy and kind (0 RGB, 1 white, 2 cell, as
     * {@link FixtureProfile#zoneKind}) of its emitter zones (model units, face plane, relative to the beam origin), {@code scale} = render
     * scale of the model.
     */
    public static OpticalProfile of(FixtureProfile p, float[][] cells, float scale) {
        if (p.isPanel()) return panel(cells, scale);
        if (p == FixtureProfile.LUMORA_W7 || p == FixtureProfile.LUMORA_W19 || p == FixtureProfile.PIXELWASH_W37) return lensArray(cells, scale, p == FixtureProfile.LUMORA_W19 ? 0.16f : 0.07f);
        OpticalProfile optic = new OpticalProfile(SPOT, new float[MAX_RINGS], new float[MAX_RINGS], new float[MAX_RINGS], 0, 1, 0f, 0.05f, 0, 0, 0, 0,
            new int[cells.length], 0, new float[MAX_TINTS]);
        if (p != FixtureProfile.VORTEX_X1) optic.spotGain = 1.2f;
        return optic;
    }

    /** Lenses grouped in rings by their distance to the centre (the hexagonal rings of 19 / 37 split into their real radii). */
    static OpticalProfile lensArray(float[][] cells, float scale, float skirt) {
        int n = cells.length;
        float maxD = 0f, half = 0f;
        float[] dist = new float[n], ang = new float[n];
        for (int i = 0; i < n; i++) {
            dist[i] = (float) Math.hypot(cells[i][0], cells[i][1]) * scale;
            ang[i] = (float) Math.atan2(cells[i][1], cells[i][0]);
            maxD = Math.max(maxD, dist[i]);
            half += 0.5f * (cells[i][2] + cells[i][3]) * scale;
        }
        half /= Math.max(1, n);
        // the lenses of a model are not on perfect circles: a ring holds the lenses within 15 % of the ring spacing (about
        // maxD / sqrt(n / 3) for a round array); a lens within a quarter of a lens radius of the centre is the centre lens
        float tol = 0.15f * maxD / (float) Math.max(1.0, Math.sqrt(n / 3.0));
        for (int i = 0; i < n; i++) if (dist[i] < 0.25f * half) dist[i] = 0f;
        List<float[]> groups = new ArrayList<>();          // radius sum, count, first angle
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        java.util.Arrays.sort(order, (x, y) -> Float.compare(dist[x], dist[y]));
        int[] member = new int[n];
        for (int idx : order) {
            float[] last = groups.isEmpty() ? null : groups.get(groups.size() - 1);
            if (last != null && Math.abs(dist[idx] - last[0] / last[1]) <= tol) { last[0] += dist[idx]; last[1] += 1; }
            else groups.add(new float[] {dist[idx], 1, ang[idx]});
            member[idx] = groups.size() - 1;
        }
        // more groups than uniforms: merge the closest neighbours (keeps the lens count)
        while (groups.size() > MAX_RINGS) {
            int best = 1;
            float gap = Float.MAX_VALUE;
            for (int i = 1; i < groups.size(); i++) {
                float g = groups.get(i)[0] / groups.get(i)[1] - groups.get(i - 1)[0] / groups.get(i - 1)[1];
                if (g < gap && groups.get(i - 1)[0] > 0) { gap = g; best = i; }
            }
            float[] a = groups.get(best - 1), b = groups.remove(best);
            a[0] += b[0]; a[1] += b[1];
            for (int i = 0; i < n; i++) if (member[i] >= best) member[i]--;
        }
        float[] rr = new float[MAX_RINGS], rn = new float[MAX_RINGS], rp = new float[MAX_RINGS];
        for (int i = 0; i < groups.size(); i++) {
            float[] g = groups.get(i);
            rr[i] = g[0] / g[1];
            rn[i] = g[1];
            float period = (float) (2 * Math.PI / g[1]);
            rp[i] = (float) (((g[2] % period) + period) % period);
        }
        float[] gw = new float[MAX_TINTS];
        for (int i = 0; i < groups.size(); i++) gw[i] = groups.get(i)[1];
        return new OpticalProfile(LENS_ARRAY, rr, rn, rp, groups.size(), n, Math.max(half, 0.005f), skirt, 0, 0, 0, 0, member, groups.size(), gw);
    }

    /** Rectangular face: extent of all the cells, pitch of the RGB cells (the grid seen near the face). */
    static OpticalProfile panel(float[][] cells, float scale) {
        float x0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y0 = Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        List<Float> xs = new ArrayList<>(), ys = new ArrayList<>();
        for (float[] c : cells) {
            x0 = Math.min(x0, c[0] - c[2]); x1 = Math.max(x1, c[0] + c[2]);
            y0 = Math.min(y0, c[1] - c[3]); y1 = Math.max(y1, c[1] + c[3]);
            if (c.length > 4 && c[4] == 1f) continue;           // white strobe segments: not part of the grid of RGB cells
            xs.add(c[0]); ys.add(c[1]);
        }
        float px = pitch(xs), py = pitch(ys);
        // columns of the face (the RGB cells side by side; each white segment counts in the column it falls in), weighted by area
        int cols = 1;
        if (px > 1e-4f) cols = Math.max(1, Math.min(MAX_TINTS, Math.round((x1 - x0) / px)));
        int[] col = new int[cells.length];
        float[] gw = new float[MAX_TINTS];
        for (int i = 0; i < cells.length; i++) {
            float[] c = cells[i];
            col[i] = Math.max(0, Math.min(cols - 1, (int) Math.floor((c[0] - x0) / (x1 - x0) * cols)));
            gw[col[i]] += 4f * c[2] * c[3];
        }
        return new OpticalProfile(PANEL, new float[MAX_RINGS], new float[MAX_RINGS], new float[MAX_RINGS], 0, cells.length, 0f, 0.10f,
            0.5f * (x1 - x0) * scale, 0.5f * (y1 - y0) * scale, px * scale, py * scale, col, cols, gw);
    }

    private static float pitch(List<Float> v) {
        List<Float> s = new ArrayList<>(v);
        s.sort(Float::compare);
        float best = 0f;
        for (int i = 1; i < s.size(); i++) {
            float d = s.get(i) - s.get(i - 1);
            if (d > 1e-3f && (best == 0f || d < best)) best = d;
        }
        return best;
    }

    // ------------------------------------------------------------ per frame parameters (uniforms OptKind / OptShape)
    /** Beam (1) or spot (0) character of the VORTEX optic for a beam angle in degrees: under 6° a beam, over 14° a spot. */
    public static float beamness(float angleDeg) {
        return clamp01((14f - angleDeg) / 8f);
    }

    /** Edge softness of the spot (fraction of the radius over which the edge fades) for a focus sharpness 0..1 (1 = sharp). */
    public static float edge(float sharpness) {
        return 0.03f + 0.32f * (1f - clamp01(sharpness));
    }

    /**
     * {@code OptKind}: x = family, y = gobo in (0/1), z = prism in (0/1), w = prism angle (rad). {@code OptShape}: SPOT x beamness,
     * y edge, z skirt; LENS_ARRAY x lens radius, y skirt, z lens count, w ring count; PANEL x, y half extent, z, w cell pitch.
     */
    public void uniforms(boolean gobo, boolean prism, float prismRad, float angleDeg, float sharpness, float[] out, int at) {
        out[at] = kind; out[at + 1] = gobo ? 1f : 0f; out[at + 2] = prism ? 1f : 0f; out[at + 3] = prismRad;
        switch (kind) {
            case SPOT -> { out[at + 4] = beamness(angleDeg); out[at + 5] = edge(sharpness); out[at + 6] = skirt; out[at + 7] = 1f + (spotGain - 1f) * beamness(angleDeg); }
            case LENS_ARRAY -> { out[at + 4] = lensRadius; out[at + 5] = skirt; out[at + 6] = lenses; out[at + 7] = rings; }
            default -> { out[at + 4] = panelX; out[at + 5] = panelY; out[at + 6] = cellX; out[at + 7] = cellY; }
        }
        for (int i = 0; i < MAX_RINGS; i++) {
            out[at + 8 + i * 4] = ringR[i]; out[at + 9 + i * 4] = ringN[i]; out[at + 10 + i * 4] = ringPhase[i]; out[at + 11 + i * 4] = 0f;
        }
    }

    /** Floats written by {@link #uniforms}: OptKind, OptShape, OptRing0..5. */
    public static final int UNIFORM_FLOATS = 8 + 4 * MAX_RINGS;

    // ------------------------------------------------------------ rev. 16b: colours of the cells (uniforms OptTintInfo / OptTint0..7)
    /**
     * Colours of the groups from the zones of the fixture ({@code zones} = r, g, b, level per zone, as Engine.Output.zones), each the mean
     * of its cells weighted by their area, then scaled so that the mean over the face has its brightest channel at 1 (the strength stays
     * in the flux / the haze strength, as without tints). Writes OptTintInfo (x = on, y = group count) and OptTint0..7 at {@code at};
     * returns false (and writes "off") when every group has the same colour: the shader then uses the plain colour of the beam.
     */
    public boolean tints(float[] zones, float[] out, int at) {
        java.util.Arrays.fill(out, at, at + TINT_FLOATS, 0f);
        if (groups < 2 || zones == null) return false;
        float[] acc = new float[groups * 4];
        for (int z = 0; z < groupOf.length && z * 4 + 3 < zones.length; z++) {
            int g = groupOf[z];
            if (g < 0 || g >= groups) continue;
            float l = zones[z * 4 + 3];
            acc[g * 4] += zones[z * 4] * l; acc[g * 4 + 1] += zones[z * 4 + 1] * l; acc[g * 4 + 2] += zones[z * 4 + 2] * l; acc[g * 4 + 3] += 1f;
        }
        float mr = 0, mg = 0, mb = 0, wsum = 0;
        for (int g = 0; g < groups; g++) {
            float k = acc[g * 4 + 3] > 0 ? 1f / acc[g * 4 + 3] : 0f;
            acc[g * 4] *= k; acc[g * 4 + 1] *= k; acc[g * 4 + 2] *= k;
            mr += acc[g * 4] * groupWeight[g]; mg += acc[g * 4 + 1] * groupWeight[g]; mb += acc[g * 4 + 2] * groupWeight[g]; wsum += groupWeight[g];
        }
        float peak = Math.max(mr, Math.max(mg, mb)) / Math.max(wsum, 1e-6f);
        if (peak < 1e-4f) return false;
        boolean same = true;
        for (int g = 1; g < groups && same; g++)
            for (int c = 0; c < 3; c++) if (Math.abs(acc[g * 4 + c] - acc[c]) > 0.02f) same = false;
        if (same) return false;
        out[at] = 1f; out[at + 1] = groups;
        for (int g = 0; g < groups; g++)
            for (int c = 0; c < 3; c++) out[at + 4 + g * 4 + c] = acc[g * 4 + c] / peak;
        return true;
    }

    /** Floats written by {@link #tints}: OptTintInfo then OptTint0..7. */
    public static final int TINT_FLOATS = 4 + 4 * MAX_TINTS;

    /**
     * Colour of the light at {@code q} (Java twin of csc_tint): the group colours weighted by what each group brings there — LENS_ARRAY
     * the light of each ring, PANEL each column of the face spread by the optic. {@code t} = the floats of {@link #tints}.
     */
    public static void tint(float[] u, float[] t, float qx, float qy, float hw, float along, float tanH, float[] rgb) {
        if (t[0] < 0.5f) { rgb[0] = rgb[1] = rgb[2] = 1f; return; }
        int n = Math.round(t[1]);
        float wr = 0, wg = 0, wb = 0, ws = 0;
        int kind = Math.round(u[0]);
        if (kind == LENS_ARRAY) {
            float ax = qx * hw, ay = qy * hw, ra = (float) Math.hypot(ax, ay), th = (float) Math.atan2(ay, ax);
            float s = u[4] + Math.max(along, 0f) * tanH;
            float sig = Math.max(0.5f * s, 1e-3f), sig2 = sig * sig;
            for (int i = 0; i < Math.min(n, MAX_RINGS); i++) {
                float w = ring(u[8 + i * 4], u[9 + i * 4], u[10 + i * 4], ra, th, sig2) + 1e-6f;
                wr += w * t[4 + i * 4]; wg += w * t[5 + i * 4]; wb += w * t[6 + i * 4]; ws += w;
            }
        } else {
            float bx = u[4], x = qx * hw;
            float s = Math.max(Math.max(along, 0f) * tanH, 1e-3f);
            float cw = 2f * bx / Math.max(1, n);
            // 1.2.0 beta 2b: far from the face part of the footprint keeps the image of the bar (twin of concertstage_optics.glsl)
            float far = 0.7f * smoothstep(0.5f, 2f, s / Math.max(bx, 1e-3f));
            float xi = Math.max(-1f, Math.min(1f, qx)) * bx;
            for (int c = 0; c < n; c++) {
                float xc = -bx + (c + 0.5f) * cw;
                float w = mix(overlap(Math.abs(x - xc), s, 0.5f * cw), overlap(Math.abs(xi - xc), 0.5f * cw, 0.5f * cw), far) + 1e-6f;
                wr += w * t[4 + c * 4]; wg += w * t[5 + c * 4]; wb += w * t[6 + c * 4]; ws += w;
            }
        }
        rgb[0] = wr / ws; rgb[1] = wg / ws; rgb[2] = wb / ws;
    }

    /** One ring of lenses (twin of csc_ring). */
    static float ring(float R, float cnt, float ph, float ra, float th, float sig2) {
        if (cnt <= 0f) return 0f;
        if (R < 1e-4f) return cnt * (float) Math.exp(-ra * ra / (2f * sig2));
        float z = ra * R / sig2, dr = ra - R;
        float g = (float) (Math.exp(-dr * dr / (2f * sig2)) / Math.sqrt(1.0 + 2.0 * Math.PI * z));
        float arc = (float) (2 * Math.PI * R / cnt);
        float m = (float) Math.exp(-2.0 * Math.PI * Math.PI * sig2 / (arc * arc));
        return cnt * g * Math.max(0f, 1f + 2f * m * (float) Math.cos(cnt * (th - ph)));
    }

    // ------------------------------------------------------------ the distribution (Java twin of csc_light in concertstage_optics.glsl)
    /**
     * Light of the distribution at {@code q} (normalised cross-section), {@code hw} half width of the beam there, {@code along} blocks
     * from the lens, {@code tanH} tangent of the half angle; {@code gobo} = transmission of the gobo already read at the rotated point
     * (1 without gobo). Prism handled by the caller ({@link #withPrism}). Uses {@code u} = the uniforms of {@link #uniforms}.
     */
    public static float light(float[] u, float qx, float qy, float hw, float along, float tanH, float gobo) {
        int kind = Math.round(u[0]);
        float rho = (float) Math.hypot(qx, qy);
        float cut = 1f - smoothstep(0.92f, 1.08f, rho);
        if (cut <= 0f) return 0f;
        if (kind == SPOT) {
            float b = u[4], e = u[5];
            float spot = 1f - smoothstep(1f - e, 1f, rho);
            spot *= 1f + 0.18f * (1f - rho * rho);                       // gentle hot centre of a real spot
            float core = (float) Math.exp(-rho * rho / (2f * 0.3f * 0.3f));
            float base = mix(spot, 2.4f * core + 0.12f * spot, b);       // beam: same flux concentrated in its core
            return base * gobo * cut * ProfessionalOptics.framing(u, qx, qy) * ProfessionalOptics.secondary(u, qx, qy) * (u[7] > 0 ? u[7] : 1f);
        }
        if (kind == 3) return ProfessionalOptics.laser(u, qx, qy) * cut;
        if (kind == LENS_ARRAY) {
            float lensR = u[4], skirt = u[5], n = Math.max(1f, u[6]);
            int rings = Math.round(u[7]);
            float ax = qx * hw, ay = qy * hw, ra = (float) Math.hypot(ax, ay), th = (float) Math.atan2(ay, ax);
            float s = lensR + Math.max(along, 0f) * tanH;
            float sig = Math.max(0.5f * s, 1e-3f), sig2 = sig * sig;
            float sum = 0f;
            for (int i = 0; i < rings; i++) {
                float R = u[8 + i * 4], cnt = u[9 + i * 4], ph = u[10 + i * 4];
                if (cnt <= 0f) continue;
                float g;
                if (R < 1e-4f) g = (float) Math.exp(-ra * ra / (2f * sig2));
                else {
                    float z = ra * R / sig2;
                    float dr = ra - R;
                    g = (float) (Math.exp(-dr * dr / (2f * sig2)) / Math.sqrt(1.0 + 2.0 * Math.PI * z));   // e^-z I0(z), angular mean
                    float arc = (float) (2 * Math.PI * R / cnt);
                    float m = (float) Math.exp(-2.0 * Math.PI * Math.PI * sig2 / (arc * arc));
                    g *= Math.max(0f, 1f + 2f * m * (float) Math.cos(cnt * (th - ph)));
                }
                sum += cnt * g;
            }
            float dens = sum / (2f * (float) Math.PI * sig2);            // lenses per unit area of the cross-section, each of power 1
            float lit = dens * (float) Math.PI * hw * hw / n;            // mean over the disc ~ 1
            float halo = skirt * (float) Math.exp(-rho * rho * 1.6f) * 1.4f;
            // rev. 22: soft field edge of a wash (twin of concertstage_optics.glsl): fades from 45 % of the cone, same flux (x1.78)
            float field = (1f - smoothstep(0.45f, 1f, rho)) * 1.78f;
            return (lit * (1f - skirt) + halo) * gobo * Math.min(cut, field);
        }
        // PANEL: the face (a box) seen through the spread of each of its points (a box kernel of half width s): a trapezoid per axis,
        // flat in the middle, soft to its edges, with the grid of the cells near the face
        float bx = u[4], by = u[5], px = u[6], py = u[7];
        float ax = Math.abs(qx * hw), ay = Math.abs(qy * hw);
        float s = Math.max(Math.max(along, 0f) * tanH, 1e-3f);
        float ox = overlap(ax, s, bx), oy = overlap(ay, s, by);
        float lit = ox * oy * (float) Math.PI * hw * hw / Math.max(4f * bx * by, 1e-5f);
        float sig = 0.5f * s;
        if (px > 1e-4f) lit *= 1f + 0.85f * (float) Math.exp(-2.0 * Math.PI * Math.PI * sig * sig / (px * px)) * (float) Math.cos(2 * Math.PI * qx * hw / px);
        if (py > 1e-4f) lit *= 1f + 0.85f * (float) Math.exp(-2.0 * Math.PI * Math.PI * sig * sig / (py * py)) * (float) Math.cos(2 * Math.PI * qy * hw / py);
        // rev. 23: a soft round footprint once the spread is larger than the face (twin of concertstage_optics.glsl)
        // 1.2.0 beta 2b: with a hot centre, same flux (twin of concertstage_optics.glsl)
        float round = (float) Math.exp(-rho * rho / (2f * 0.35f * 0.35f)) * 4.03f;
        // 1.2.0 beta 2b: brighter towards the axis near the face too (twin of concertstage_optics.glsl)
        float hot = 0.35f + 0.65f * (float) Math.exp(-rho * rho / (2f * 0.4f * 0.4f)) / 0.306f;
        lit = mix(Math.max(0f, lit) * hot, round, smoothstep(0.5f, 2f, s / Math.max(Math.max(bx, by), 1e-3f)));
        float halo = 0.10f * (float) Math.exp(-rho * rho * 1.2f);          // scattered light of the face
        return (lit * 0.9f + halo) * gobo * cut;
    }

    /** Part of [a - s, a + s] inside [-b, b], over 2 s (the light of a box of half width b spread by a box kernel of half width s). */
    static float overlap(float a, float s, float b) {
        float lo = Math.max(a - s, -b), hi = Math.min(a + s, b);
        return Math.max(0f, hi - lo) / (2f * s);
    }

    /** 3-facet prism: centres of the three images (fraction of the widened cone) and their size. Same constants as the GLSL. */
    public static final float PRISM_OFFSET = 0.55f, PRISM_SCALE = 0.45f, PRISM_WIDEN = 1.8f;

    /** {@link #light} without gobo, with the 3-facet prism when {@code u} says so (twin of csc_light with an open gobo wheel). */
    public static float lightAt(float[] u, float qx, float qy, float hw, float along, float tanH) {
        if (u[2] < 0.5f) return light(u, qx, qy, hw, along, tanH, 1f);
        float sum = 0f;
        float[] qk = new float[2];
        int facets = u[13] >= 3 ? Math.min(8, Math.round(u[13])) : 3;
        for (int k = 0; k < facets; k++) {
            double a = u[3] + k * 2 * Math.PI / facets;
            qk[0] = (qx - PRISM_OFFSET * (float) Math.cos(a)) / PRISM_SCALE;
            qk[1] = (qy - PRISM_OFFSET * (float) Math.sin(a)) / PRISM_SCALE;
            sum += light(u, qk[0], qk[1], hw * PRISM_SCALE, along, tanH, 1f);
        }
        return sum / (facets * PRISM_SCALE * PRISM_SCALE);
    }

    /** Point {@code k} (0..2) of the prism: q of the image seen at {@code (qx, qy)}, written to {@code out}. */
    public static void withPrism(float qx, float qy, float prismRad, int k, float[] out) {
        double a = prismRad + k * 2 * Math.PI / 3;
        out[0] = (qx - PRISM_OFFSET * (float) Math.cos(a)) / PRISM_SCALE;
        out[1] = (qy - PRISM_OFFSET * (float) Math.sin(a)) / PRISM_SCALE;
    }

    static float smoothstep(float a, float b, float x) {
        float t = clamp01((x - a) / (b - a));
        return t * t * (3f - 2f * t);
    }

    static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }

    static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(1f, v);
    }
}
