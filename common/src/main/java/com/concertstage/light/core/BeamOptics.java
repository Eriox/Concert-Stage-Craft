package com.concertstage.light.core;

/**
 * Optics of a CSC beam (1.2.0 rev. 5), pure Java so it can be tested without the game: how much light the beam shows in the air along
 * its length, how strongly it lights the surfaces it reaches, and how the surfaces under a wide beam are sampled. The client renderer
 * only turns these numbers into geometry.
 *
 * <p>The model separates the two things a real light does (a separation also made by Theatrical ExtraLights, whose MIT-licensed code
 * was studied but not copied):
 * <ul>
 *   <li><b>light in the air</b> (the visible beam): the light scattered by haze. Seen from the side, a beam of radius {@code r} carries the
 *   same flux spread over a section that grows with {@code r²} while the path through it only grows with {@code r}: the brightness per
 *   area of the beam falls as {@code 1/r}. A tight beam stays bright far away; a 60° wash glows near the fixture and fades within a few
 *   blocks — no more giant flat triangles across the stage. Haze also absorbs (exponential), and a throw that hits nothing ends softly;</li>
 *   <li><b>light on surfaces</b> (the stage, instruments, truss, backdrop): the flux reaching a surface, spread over the beam footprint,
 *   attenuated with distance and by the incidence angle.</li>
 * </ul>
 */
public final class BeamOptics {
    /** Same flash gate for lenses, haze and surfaces; a zero dimmer can never emit. */
    /**
     * Strobe intensity at a time. Strobe off ({@code hz} at 0): the dimmer, untouched — never a flash. Rev. 19 audit: a flash lasts
     * 45 ms (duty 0.045 x rate, at most half the period), longer than a frame at 30 FPS and more, so every flash is seen by at least
     * one frame and sampling at the frame time is not a cause of irregular flashes (LightingFixTest measures it).
     */
    public static float strobeIntensity(float dimmer, float hz, double seconds) {
        if (dimmer <= 0f) return 0f;
        if (!(hz > 0.05f)) return dimmer;
        double phase = seconds * hz;
        float duty = Math.min(0.5f, 0.045f * hz);
        return phase - Math.floor(phase) > duty ? 0f : Math.min(1f, dimmer * 1.35f + 0.1f);
    }

    public static final int LOW = 0, MEDIUM = 1, HIGH = 2, ULTRA = 3, LEVELS = 4;

    /** Segments of the soft ribbon along the beam, per quality. */
    private static final int[] SEGMENTS = {4, 6, 9, 14};
    /** Soft cross-section discs (what is seen when looking along the beam), per quality. */
    private static final int[] DISCS = {0, 4, 7, 12};
    /** Surface sample rays around the centre ray (wide beams only), per quality. */
    private static final int[] RING_RAYS = {0, 4, 6, 12};
    /** Extra Shimmer lights spread over a wide footprint, per quality. */
    private static final int[] SHIMMER_EXTRA = {0, 0, 2, 4};
    /** Surface sample rays a frame may cast in total, per quality. */
    private static final int[] RAY_BUDGET = {48, 64, 160, 320};          // rev. 22: low 24 -> 48
    /** Beams drawn at most per frame, per quality. */
    private static final int[] MAX_BEAMS = {48, 64, 160, 256};           // rev. 22: low 24 -> 48 (38 of the 62 fixtures of the author's stage fell back to the far ribbons)

    /** Absorption of the haze, per block. */
    public static final float ABSORPTION = 0.018f;
    /**
     * Exponent of the 1/r law of the light in the haze (ribbons). Rev. 17: 0.75 (0.85 before); the beam volume shader uses 0.65, its
     * light being also divided by the width of the cone. A wide wash lost its light in the air within a few blocks of the lens: aimed
     * at the audience it showed a lit lens and no cone (11 November pictures). Beams narrower than {@link #R_REF} (the spots) are not
     * affected.
     */
    public static final float SPREAD_EXP = 0.75f;
    /** Reference radius of the 1/r law: a beam narrower than this does not get brighter. */
    public static final float R_REF = 0.55f;

    private BeamOptics() {}

    public static int q(int quality) {
        return Math.max(LOW, Math.min(ULTRA, quality));
    }

    public static int segments(int quality) { return SEGMENTS[q(quality)]; }
    public static int discs(int quality) { return DISCS[q(quality)]; }
    public static int rayBudget(int quality) { return RAY_BUDGET[q(quality)]; }
    public static int maxBeams(int quality) { return MAX_BEAMS[q(quality)]; }
    public static int shimmerExtra(int quality) { return SHIMMER_EXTRA[q(quality)]; }

    /** Radius of the beam at distance {@code d}. */
    public static float radius(float r0, float tanHalf, float d) {
        return r0 + Math.max(0f, d) * tanHalf;
    }

    /**
     * Light of the beam in the air at distance {@code d} (0..1, before colour, intensity and haze): soft start at the lens, 1/r spread,
     * absorption, and a soft end of the throw when the beam hits nothing.
     */
    public static float air(float d, float len, boolean hits, float r0, float tanHalf) {
        if (d < 0f || len <= 0f || d > len) return 0f;
        float ramp = Math.min(1f, 0.35f + d / 0.8f);
        // 1/r, softened (exponent SPREAD_EXP): a wash keeps a visible glow over the first blocks
        float spread = (float) Math.pow(Math.min(1f, R_REF / Math.max(1e-3f, radius(r0, tanHalf, d))), SPREAD_EXP);
        float absorb = (float) Math.exp(-ABSORPTION * d);
        float tail = 1f;
        if (!hits) {
            float t = d / len;
            if (t > 0.6f) { float x = (1f - t) / 0.4f; tail = x * x * (3f - 2f * x); }
        } else {
            // rev. 8: the last part of a beam that meets a surface fades over about its own radius. The game cannot fade geometry
            // where it crosses a surface (its shaders have no depth texture): without this, a wide beam is cut by the floor along a
            // hard straight line
            // rev. 10: shorter and gentler, so the beam visibly reaches the floor (0.45 of its light is left where it lands)
            float fadeLen = Math.min(len * 0.25f, 0.5f * radius(r0, tanHalf, len) + 0.3f);
            float left = len - d;
            if (left < fadeLen) { float x = left / fadeLen; tail = 0.45f + 0.55f * x * x * (3f - 2f * x); }
        }
        return ramp * spread * absorb * tail;
    }

    /**
     * Weight of the ribbon (beam seen from the side) and of the cross-section discs (beam seen along its axis), from the cosine between
     * the beam axis and the view direction. Their sum keeps the beam visible from every angle; looking into the beam stays brighter, as
     * the eye looks through more lit haze.
     */
    public static float sideWeight(float cosView) {
        float c = Math.min(1f, Math.abs(cosView));
        return 0.3f + 0.7f * (float) Math.sqrt(Math.max(0f, 1f - c * c));
    }

    public static float axisWeight(float cosView) {
        float c = Math.min(1f, Math.abs(cosView));
        return c < 0.12f ? 0f : (c - 0.12f) / 0.88f;
    }

    /**
     * Light received by a surface (0..~3, before colour): {@code flux} = intensity × output of the fixture, at distance {@code dist}, under
     * a footprint of radius {@code footprint}, with the cosine of incidence {@code cosI}. A wider footprint spreads the same light (a
     * softened 1/area law, so a wide wash still lights a stage), distance and haze attenuate it.
     */
    public static float surface(float flux, float dist, float footprint, float cosI) {
        if (flux <= 0f) return 0f;
        float spread = 1f / (1f + 0.22f * Math.max(0f, footprint));
        float att = 1f / (1f + 0.012f * Math.max(0f, dist));
        float incidence = 0.25f + 0.75f * Math.max(0f, Math.min(1f, cosI));
        return Math.min(3f, 2.4f * flux * spread * att * incidence);
    }

    /**
     * Factor by which a surface lit with {@link #surface} light {@code e} is brightened, given how lit it already is ({@code ambient}
     * 0 = black night, 1 = full day / full block light). The projected light multiplies what is drawn: on a dark stage at night the
     * texture only shows a small part of its colour, so the same light needs a larger factor than in daylight, where a beam barely
     * shows. Capped so a surface is never burnt to white.
     */
    public static float relight(float e, float ambient) {
        if (e <= 0f) return 1f;
        float dark = 1f - Math.max(0f, Math.min(1f, ambient));
        float gain = 0.5f + 6f * dark * dark;
        return Math.min(12f, 1f + e * gain);
    }

    /**
     * Sharpness of a projected gobo (1 = in focus) for a focus value 0..1: sharp in the middle of the range, softer towards both ends,
     * like the focus of a real spot set for one throw distance.
     */
    public static float focusSharpness(float focus) {
        // rev. 16g: sharp from 0 to 60 % (the home value 0 and the AutoShow 50 % both give a sharp gobo: untouched VORTEX showed
        // a blurred gobo, 9 November 22 h 37 video), then softer up to a fully soft gobo at 100 % (the "soft focus" look)
        float f = Math.max(0f, Math.min(1f, focus));
        return f <= 0.6f ? 1f : Math.max(0f, (1f - f) / 0.4f);
    }

    /**
     * Rev. 8: the beam fades out where the camera stands in it or right next to it (a volume of light you are inside is not seen as
     * a wall, and the camera-facing geometry would twist there). {@code dc}: camera to that point of the axis, {@code r}: radius there.
     */
    public static float cameraFade(float dc, float r) {
        float near = 0.6f * r + 0.6f, far = 1.8f * r + 2.5f;
        if (dc <= near) return 0f;
        if (dc >= far) return 1f;
        float t = (dc - near) / (far - near);
        return t * t * (3f - 2f * t);
    }

    /** Rev. 9: smallest drawn radius of a beam seen at distance {@code dc} (about 3 pixels across on a 1080p screen at 70°). */
    public static float minBeamRadius(float dc) {
        return 0.0035f * Math.max(0f, dc);
    }

    /**
     * Rev. 9, iris of a beam / spot fixture (1 = open, 0 = closed): how much the exit of the beam shrinks (the iris cuts the outer
     * part of the light) and how much light still passes.
     */
    public static float irisExit(float iris) {
        return 0.45f + 0.55f * Math.max(0f, Math.min(1f, iris));
    }

    public static float irisLight(float iris) {
        return 0.7f + 0.3f * Math.max(0f, Math.min(1f, iris));
    }

    /**
     * Rev. 9: how defined the edge of a beam in haze is (0 = soft, 1 = a clean edge), from the iris and the focus: an open iris shows
     * the edge of the optic, a sharp focus makes it crisp — the look of a spot, as opposed to the soft glow of a beam or a wash.
     */
    public static float edgeDefinition(float iris, float focusSharpness) {
        float open = Math.max(0f, Math.min(1f, iris));
        return Math.max(0f, Math.min(1f, open * (0.35f + 0.65f * focusSharpness)));
    }

    /**
     * Rev. 9: beam angle of the PULSESTRIKE M from its focus: 0 = wide blinder flood (70°), 1 = a tight punch (20°).
     */
    public static float blinderAngle(float focus) {
        return 70f - 50f * Math.max(0f, Math.min(1f, focus));
    }

    /** Rev. 8: weight of the cross-section discs: full for a tight beam, small for a wide wash (no flat walls along its axis). */
    public static float discWeight(float r) {
        return Math.min(1f, 1.2f * R_REF / Math.max(1e-3f, r));
    }

    /** Rev. 8 / 10: share of a wash's light seen in the air from its half-angle tangent: ~0.88 at 10°, ~0.57 at 50°. */
    public static float washAirScale(float tanHalf) {
        return 1f / (1f + 1.6f * Math.max(0f, tanHalf));          // rev. 10: 2.5 before — more volume in the air
    }

    // ---- spill light (rev. 8): the light a fixture throws around itself — on the truss it hangs from, the roof and the tarps above,
    // the platform or the structure next to it — besides its beam. A real fixture lights its rig through its lens flare, the light
    // reflected by what its beam hits and the haze around it; the reference pictures owe their coloured truss and roof to it.

    /** How far the spill light reaches, in blocks. */
    public static final float SPILL_RANGE = 8f;

    /** Directions probed around a fixture (world frame, unit vectors): up, 4 sideways, 4 up-diagonals, 4 down-diagonals, down. */
    private static final float[][] SPILL_DIRS;
    static {
        java.util.List<float[]> d = new java.util.ArrayList<>();
        d.add(new float[] {0, 1, 0});
        float s = (float) Math.sqrt(0.5);
        for (int i = 0; i < 4; i++) {
            float x = i == 0 ? 1 : i == 1 ? -1 : 0, z = i == 2 ? 1 : i == 3 ? -1 : 0;
            d.add(new float[] {x, 0, z});
            d.add(new float[] {x * s, s, z * s});
            d.add(new float[] {x * s, -s, z * s});
        }
        d.add(new float[] {0, -1, 0});
        SPILL_DIRS = d.toArray(new float[0][]);
    }

    public static int spillRays(int quality) {
        return switch (q(quality)) { case LOW -> 5; case MEDIUM -> 9; default -> SPILL_DIRS.length; };
    }

    /** Direction of spill ray {@code i} (the first 5 are up and the 4 sideways ones, the most useful for a rig). */
    public static void spillDir(int i, float[] out) {
        int[] order = {0, 1, 4, 7, 10, 2, 5, 8, 11, 3, 6, 9, 12, 13};
        float[] d = SPILL_DIRS[order[Math.max(0, Math.min(order.length - 1, i))]];
        out[0] = d[0]; out[1] = d[1]; out[2] = d[2];
    }

    public static int spillCount() {
        return SPILL_DIRS.length;
    }

    /**
     * Light of the spill on a surface at distance {@code d} from the fixture (before the fixture's own intensity): strong on the
     * truss right next to it, gone at {@link #SPILL_RANGE}, weaker for grazing surfaces. {@code wash}: washes and blinders spill more
     * than a beam fixture, whose light leaves through a narrow optic.
     */
    public static float spill(float d, float cosI, float output, boolean wash) {
        if (d < 0f || d >= SPILL_RANGE) return 0f;
        float x = 1f - d / SPILL_RANGE;
        return output * (wash ? 0.9f : 0.55f) * x * x * (0.35f + 0.65f * Math.max(0f, Math.min(1f, cosI)));
    }

    /** Radius of the spill light projected at distance {@code d}: it spreads as it goes. */
    public static float spillRadius(float d) {
        return 1.6f + 0.55f * Math.max(0f, d);
    }

    /** Rev. 10: overall density of the haze in the air (the beams show 35 % more than in rev. 9). */
    public static final float AIR_DENSITY = 1.35f;

    /** Number of surface rays for a beam whose footprint (at the hit) has radius {@code footprint}: one ray for a tight beam. */
    public static int rays(int quality, float footprint) {
        if (footprint < 1.2f) return 1;
        int ring = RING_RAYS[q(quality)];
        if (footprint < 3f) ring = Math.min(ring, 4);
        return 1 + ring;
    }

    /**
     * Direction of surface ray {@code i} as an offset in the beam's cross-section, in units of the cone half-angle tangent:
     * {@code out[0]} along the first side axis, {@code out[1]} along the second. Ray 0 is the axis; the others sit on one ring at 0.62
     * of the half angle, or on two rings (0.45 / 0.85) when there are more than 6.
     */
    public static void rayOffset(int i, int count, float[] out) {
        if (i <= 0 || count <= 1) { out[0] = 0f; out[1] = 0f; return; }
        int ring = count - 1;
        float frac;
        int k, n;
        if (ring > 6) {
            int inner = ring / 2;
            if (i <= inner) { frac = 0.45f; k = i - 1; n = inner; }
            else { frac = 0.85f; k = i - 1 - inner; n = ring - inner; }
        } else { frac = 0.62f; k = i - 1; n = ring; }
        double a = Math.PI * 2 * k / n + (frac > 0.8f ? Math.PI / n : 0);
        out[0] = (float) Math.cos(a) * frac;
        out[1] = (float) Math.sin(a) * frac;
    }

    /** Radius of the projected light of ray {@code i} on its surface, for a footprint of radius {@code footprint} at that distance. */
    public static float decalRadius(int i, int count, float footprint) {
        if (count <= 1) return Math.max(0.35f, footprint * 1.05f);
        return Math.max(0.35f, footprint * (i == 0 ? 0.7f : count > 7 ? 0.42f : 0.55f));
    }

    /** Weight of ray {@code i} in the light it projects (the centre of a beam is brighter than its edge). */
    public static float rayWeight(int i, int count) {
        if (i == 0) return 1f;
        return count > 7 && i > (count - 1) / 2 ? 0.55f : 0.75f;
    }

    /** Quads of one beam (ribbon + discs + projected light), to keep the cost per beam known for each quality. */
    public static int quadsPerBeam(int quality, int rays, boolean core) {
        int ribbon = SEGMENTS[q(quality)] * (core ? 2 : 1);
        return ribbon + DISCS[q(quality)] + rays;
    }
}
