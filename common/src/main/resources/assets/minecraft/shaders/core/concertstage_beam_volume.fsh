#version 150

// Concert Stage Craft 1.2.0 rev. 15: the beam of one fixture in the haze, computed along each line of sight.
// Rev. 16: the light at each point is the common optical mask csc_light (family distribution x gobo x prism), the same function
// as the projected light on surfaces.
//
// For every pixel, the line of sight from the camera is intersected with the cone of the beam and stopped at the scene (copy of
// the depth buffer, fixture bodies included). Along that segment the haze is sampled: at each point, the light that reaches it
// is the pattern of the optic there (gobo, open spot, soft wash — it turns with the gobo wheel, opens with the zoom), times
// what the shadow map of the fixture says (0 behind a truss tube or a brace as seen from the fixture, 1 in the light), times the
// light law of the beams (1 / radius, absorption of the haze). Behind each tube a dark shaft runs through the beam; between the
// tubes the light goes on and lights the haze and what is behind. Blending: screen (one, one minus src colour), as the other beams.
//
// BeamShape:  x = tan(half angle), y = cos(gobo rotation), z = sin(gobo rotation), w = range (blocks from the lens)
// BeamLight:  rgb = colour x strength (same scale as the beam ribbons), a = absorption of the haze per block
// BeamOptics: x = radius at the lens, y = focus spread (0 = sharp), z = number of steps, w = end fade (blocks, 0 = stops at a surface)

uniform sampler2D DepthSampler;
uniform sampler2D PatternSampler;
uniform sampler2D ShadowSampler;
uniform mat4 InvProjMat;
uniform vec2 TargetSize;
uniform vec3 BeamOrigin;
uniform vec3 BeamAxis;
uniform vec3 BeamSide;
uniform vec4 BeamShape;
uniform vec4 BeamLight;
uniform vec4 BeamOptics;
uniform mat4 ShadowMat;
uniform vec3 ShadowNF;
uniform float DebugMode;        // rev. 16: 0 normal; diagnostic views (see LightDiagnostics)

#moj_import <concertstage_optics.glsl>

out vec4 fragColor;

const float R_REF = 0.55;          // BeamOptics.R_REF

float shadowLinear(float z, float n, float f) {
    return 2.0 * n * f / (f + n - (z * 2.0 - 1.0) * (f - n));
}

float d0(vec3 c) {
    return shadowLinear(c.z * 0.5 + 0.5, ShadowNF.x, ShadowNF.y);
}

// the steps along the ray and their jitter average it into a soft edge
float lightVisibility(vec3 p) {
    if (ShadowNF.z < 0.5) {
        return 1.0;
    }
    vec4 lc = ShadowMat * vec4(p, 1.0);
    if (lc.w <= 0.0) {
        return 1.0;
    }
    vec3 c = lc.xyz / lc.w;
    if (c.z <= -1.0 || c.z >= 1.0) {
        return 1.0;
    }
    vec2 suv = c.xy * 0.5 + 0.5;
    if (suv.x <= 0.0 || suv.x >= 1.0 || suv.y <= 0.0 || suv.y >= 1.0) {
        return 1.0;
    }
    float d = shadowLinear(c.z * 0.5 + 0.5, ShadowNF.x, ShadowNF.y) - (0.05 + 0.006 * d0(c));
    // rev. 20 — bilinear filtering of the comparison (the 4 texels around the point, weighted by where it falls between them). With
    // one hard test per point, the shadows in the haze were drawn texel by texel: seen in game behind a stage (CraftCity, red
    // washes), the buildings and trees behind cast a mosaic of big squares through the haze
    vec2 size = vec2(textureSize(ShadowSampler, 0));
    vec2 st = suv * size - 0.5;
    vec2 f = fract(st);
    vec2 base = (floor(st) + 0.5) / size;
    vec2 tx = 1.0 / size;
    float a = d <= shadowLinear(texture(ShadowSampler, base).r, ShadowNF.x, ShadowNF.y) ? 1.0 : 0.0;
    float b = d <= shadowLinear(texture(ShadowSampler, base + vec2(tx.x, 0.0)).r, ShadowNF.x, ShadowNF.y) ? 1.0 : 0.0;
    float e = d <= shadowLinear(texture(ShadowSampler, base + vec2(0.0, tx.y)).r, ShadowNF.x, ShadowNF.y) ? 1.0 : 0.0;
    float g = d <= shadowLinear(texture(ShadowSampler, base + tx).r, ShadowNF.x, ShadowNF.y) ? 1.0 : 0.0;
    return mix(mix(a, b, f.x), mix(e, g, f.x), f.y);
}

// interleaved gradient noise: a different start for neighbouring pixels, no banding
float ign(vec2 p) {
    return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}

// part of the line t in [lo, hi] (empty when lo > hi)
vec2 meet(vec2 a, vec2 b) {
    return vec2(max(a.x, b.x), min(a.y, b.y));
}

void main() {
    vec2 uv = gl_FragCoord.xy / TargetSize;
    float depth = texture(DepthSampler, uv).r;
    vec4 vp = InvProjMat * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec3 scene = vp.xyz / vp.w;
    vec3 dir = normalize(scene);
    float tScene = depth >= 0.99999 ? 1.0e5 : length(scene);

    float tanH = max(BeamShape.x, 0.004);
    float r0 = BeamOptics.x;
    float back = r0 / tanH;                         // the apex of the cone is behind the lens
    vec3 apex = BeamOrigin - BeamAxis * back;
    float cos2 = 1.0 / (1.0 + tanH * tanH);

    // slab: from just in front of the lens to the range
    vec3 co = -apex;                                // camera (origin) relative to the apex
    float da = dot(dir, BeamAxis);
    float ca = dot(co, BeamAxis);
    float hLo = back + 0.05, hHi = back + BeamShape.w;
    vec2 slab;
    if (abs(da) < 1.0e-5) {
        if (ca < hLo || ca > hHi) {
            discard;
        }
        slab = vec2(-1.0e6, 1.0e6);
    } else {
        float t0 = (hLo - ca) / da, t1 = (hHi - ca) / da;
        slab = vec2(min(t0, t1), max(t0, t1));
    }
    slab = meet(slab, vec2(0.05, tScene));
    if (slab.x >= slab.y) {
        discard;
    }

    // cone: (v.a)^2 >= cos^2 |v|^2 with v = t dir - apex
    float qa = da * da - cos2;
    float qb = 2.0 * (da * ca - cos2 * dot(dir, co));
    float qc = ca * ca - cos2 * dot(co, co);
    vec2 seg = vec2(1.0, 0.0);
    if (abs(qa) < 1.0e-6) {
        if (abs(qb) > 1.0e-6) {
            float tr = -qc / qb;
            seg = meet(slab, qb > 0.0 ? vec2(tr, 1.0e6) : vec2(-1.0e6, tr));
        }
    } else {
        float disc = qb * qb - 4.0 * qa * qc;
        if (disc <= 0.0) {
            if (qa > 0.0) {
                seg = slab;
            }
        } else {
            float sd = sqrt(disc);
            float ra = (-qb - sd) / (2.0 * qa), rb = (-qb + sd) / (2.0 * qa);
            float r1 = min(ra, rb), r2 = max(ra, rb);
            if (qa < 0.0) {
                seg = meet(slab, vec2(r1, r2));
            } else {
                // two half lines, one per nappe of the double cone: the slab keeps the one in front of the lens
                vec2 s1 = meet(slab, vec2(-1.0e6, r1));
                vec2 s2 = meet(slab, vec2(r2, 1.0e6));
                seg = (s1.y - s1.x) > (s2.y - s2.x) ? s1 : s2;
            }
        }
    }
    if (seg.x >= seg.y) {
        discard;
    }

    vec3 up = cross(BeamAxis, BeamSide);
    int steps = int(BeamOptics.z);
    // rev. 16g: the gobo read at the size of a pixel (mipmaps): a far beam no longer shimmers
    float pixAngle = 2.0 * InvProjMat[1][1] / TargetSize.y;
    float dt = (seg.y - seg.x) / float(steps);
    float jitter = ign(gl_FragCoord.xy);
    vec3 sum = vec3(0.0);
    bool tinted = OptTintInfo.x > 0.5;
    for (int i = 0; i < 64; i++) {
        if (i >= steps) {
            break;
        }
        float t = seg.x + (float(i) + jitter) * dt;
        vec3 p = dir * t;
        vec3 rel = p - BeamOrigin;
        float along = dot(rel, BeamAxis);
        float hw = r0 + max(along, 0.0) * tanH;
        vec2 q = vec2(dot(rel, BeamSide), dot(rel, up)) / hw;
        cscGoboLod = max(0.0, log2(max(t * pixAngle / hw * 128.0, 1.0e-4)) - 0.5);
        vec2 blur = vec2(0.0);
        if (BeamOptics.y > 0.001) {
            // rev. 20 — out of focus: the gobo read from a coarser level of its mipmaps (a smooth blur of the same width). Before, every
            // step looked at a random place around the point: seen in game with the focus turned up, the beam turned into a grain of
            // speckles fixed on the screen, crawling over the beam as the camera moved
            cscGoboLod = max(cscGoboLod, log2(1.0 + BeamOptics.y * 0.5 * 128.0 * 2.0));
        }
        // rev. 16: the common optical mask (concertstage_optics.glsl): the distribution of the family x the gobo, as on the surfaces
        float k = csc_light(q, hw, max(along, 0.0), tanH, BeamShape.yz, blur);
        if (k <= 0.004) {
            continue;
        }
        float vis = lightVisibility(p);
        if (DebugMode > 3.5 && DebugMode < 4.5) {
            k = 1.0 - vis;          // diagnostic "occlusions": only the shadowed parts of the beam
        } else {
            k *= vis;
        }
        if (k <= 0.0) {
            continue;
        }
        // light law of the beams (BeamOptics.air): ramp out of the lens, 1/r softened, absorption; spread over the width of the beam
        // rev. 20: the beam leaves the lens at full strength (0.35 at the lens before: in close-up the beam seemed to start in the
        // air in front of the fixture instead of out of its lens)
        float ramp = min(1.0, 0.8 + along / 0.4);
        float spread = pow(min(1.0, R_REF / max(hw, 1.0e-3)), 0.65);     // BeamOptics.SPREAD_EXP (rev. 17: 0.85 before)
        float absorb = exp(-BeamLight.a * along);
        float tail = 1.0;
        if (BeamOptics.w > 0.0) {
            tail = smoothstep(0.0, 1.0, (BeamShape.w - along) / BeamOptics.w);
        }
        // the camera inside the beam: no blinding wall of haze (BeamOptics.cameraFade)
        float cam = smoothstep(0.6 * hw + 0.6, 1.8 * hw + 2.5, t);
        // rev. 16b: the colour of the cells there (rings of a wash, columns of a PULSESTRIKE face), the plain beam colour otherwise
        vec3 tint = tinted ? csc_tint(q, hw, max(along, 0.0), tanH) : vec3(1.0);
        sum += tint * (k * ramp * spread * absorb * tail * cam / (2.0 * hw) * dt);
    }
    if (max(sum.r, max(sum.g, sum.b)) <= 1.0e-4) {
        discard;
    }
    // looking down the beam towards the lens: haze scatters more light forward
    // rev. 17: a wider forward lobe (haze droplets scatter forward over tens of degrees, not only on the axis): a beam aimed at the
    // audience shows a cone of lit haze around its lens instead of the lens alone (11 November pictures). On the axis the exposure
    // below still compresses it.
    float toward = max(-da, 0.0);
    // rev. 20: the narrow lobe lower (0.9 -> 0.55) and the exposure brought down from further off the axis (0.90 -> 0.80, 0.42 -> 0.3):
    // seen in game from the point eight beams were aimed at in dense haze, the whole screen was white
    float forward = 1.0 + 0.6 * pow(toward, 3.0) + 0.55 * pow(toward, 12.0);
    // rev. 16e — looking into the beam (9 November, 21 h: a flat white disc as large as the beam). Along the axis the line of sight
    // crosses the whole length of the beam instead of its width: the sum is tens of times the side view and every pixel of the cone
    // clipped to white, gobo and falloff gone. The eye (and a camera) adapt to a source in the field: the end-on view is exposed
    // lower, and the light is compressed softly (per pixel, keeping the hue) instead of clipped, so the gradient from the lens out
    // to the edge of the cone and the spokes of a gobo stay visible, as on photos taken into a beam in haze.
    float adapt = mix(1.0, 0.3, smoothstep(0.80, 0.999, toward));
    vec3 x = BeamLight.rgb * sum * forward * adapt;
    float m = max(x.r, max(x.g, x.b));
    float f = m / pow(1.0 + m * m * m, 1.0 / 3.0);         // ~linear below 0.6, then rolls off to 1
    fragColor = vec4(x * (f / max(m, 1.0e-5)), 1.0);
}
