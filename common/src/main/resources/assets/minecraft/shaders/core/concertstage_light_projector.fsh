#version 150

// Concert Stage Craft 1.2.0 rev. 10: projected light of one fixture.
// Rev. 16: the light at each point is the common optical mask csc_light (concertstage_optics.glsl), shared with the beam in the haze.
// Every pixel's position is rebuilt from a copy of the depth buffer and taken into the frame of the fixture: inside the
// cone it receives the pattern (gobo / open spot / soft wash) seen through the optic, attenuated with the distance and
// the angle of the surface. Rev. 15: what is behind an obstacle as seen from the fixture (shadow map of BeamShadows: truss
// tubes and braces, blocks) gets nothing, so a tube casts its shadow on the floor and the wall and cuts the gobo there.
// Blending dst * (1 + src): textures keep their detail.
//
// ProjShape:  x = tan(half angle), y = cos(gobo rotation), z = sin(gobo rotation), w = range (blocks)
// ProjLight:  rgb = hue (brightest channel 1), a = flux (intensity x output)
// ProjOptics: x = radius of the beam at the lens, y = focus spread (0 = sharp)
// AddMode / AddK (rev. 11): the additive pass (its own json, blending one + one) and its strength

uniform sampler2D DepthSampler;
uniform sampler2D PatternSampler;
uniform mat4 InvProjMat;
uniform vec2 TargetSize;
uniform float Gain;
uniform float PassRoot;
uniform vec3 ProjOrigin;
uniform vec3 ProjAxis;
uniform vec3 ProjSide;
uniform vec4 ProjShape;
uniform vec4 ProjLight;
uniform vec4 ProjOptics;
uniform float AddMode;
uniform float AddK;
uniform sampler2D ShadowSampler;
uniform mat4 ShadowMat;
uniform vec3 ShadowNF;          // near, far, 1 when this fixture has a shadow map
uniform float DebugMode;        // rev. 16: 0 normal, 4 occlusions, 5 optical mask (see LightDiagnostics)
uniform float AccumMode;
uniform mat4 ViewToWorld;       // rev. 20: rotation from the camera frame to the world (face shading of the game, below)        // rev. 16b: 1 = write the light into the accumulation target (concertstage_light_composite applies it)

#moj_import <concertstage_optics.glsl>
#moj_import <concertstage_depth.glsl>

out vec4 fragColor;

// rev. 15: distance along the light from a depth of its shadow map
float shadowLinear(float z, float n, float f) {
    return 2.0 * n * f / (f + n - (z * 2.0 - 1.0) * (f - n));
}

// rev. 15: 1 = lit, 0 = behind an obstacle seen by the fixture (truss tube, brace, block), soft over 4 taps
float lightVisibility(vec3 p, float grazing, vec3 nOff) {
    if (ShadowNF.z < 0.5) {
        return 1.0;
    }
    // rev. 21 — normal offset: the point is read a texel and a half off its surface, towards its outside, instead of a large depth
    // bias. With the depth bias alone (up to a block on a surface grazed by a wide wash) the floor under a 40 cm riser and the
    // scaffolding under the deck edge were "in front of" the deck that hides them: lit through the planks (pictures 1 and 2 of 11
    // November, the legs of the risers glowing red under a dark deck)
    vec4 lc0 = ShadowMat * vec4(p, 1.0);
    if (lc0.w <= 0.0) {
        return 1.0;
    }
    float d0 = shadowLinear(lc0.z / lc0.w * 0.5 + 0.5, ShadowNF.x, ShadowNF.y);
    float tw = 2.0 * (ProjShape.x * 1.04 + 0.01) * d0 / float(textureSize(ShadowSampler, 0).x);
    vec4 lc = ShadowMat * vec4(p + nOff * (1.5 * tw + 0.02), 1.0);
    if (lc.w <= 0.0) {
        return 1.0;
    }
    vec3 c = lc.xyz / lc.w;
    if (c.z <= -1.0) {
        return 1.0;                 // nearer than the map starts: nothing between the lens and the point
    }
    if (c.z >= 1.0) {
        return 0.0;                 // rev. 21: beyond the reach of the map (the range of the light stops there anyway)
    }
    vec2 suv = c.xy * 0.5 + 0.5;
    if (suv.x <= 0.0 || suv.x >= 1.0 || suv.y <= 0.0 || suv.y >= 1.0) {
        return 1.0;
    }
    float d = shadowLinear(c.z * 0.5 + 0.5, ShadowNF.x, ShadowNF.y);
    float texel = 1.0 / float(textureSize(ShadowSampler, 0).x);     // rev. 21: maps of 256 or 384 texels
    // bias: grows with the size of a texel far away, and (rev. 16) with the slope of the surface seen from the fixture — a wide
    // blinder or wash grazing the floor showed shadow acne (stripes) with the distance-only bias (found with the shader harness)
    float texelWorld = 2.0 * (ProjShape.x * 1.04 + 0.01) * d * texel;
    // rev. 21: with the normal offset, a small bias (the slope part capped lower: 1 block -> 0.3 at most)
    float bias = 0.03 + 0.002 * d + 0.5 * texelWorld * min(grazing, 4.0);
    // rev. 20 — bilinear filtering of the comparison (as in the haze, concertstage_beam_volume): the 4 texels around the point weighted
    // by where it falls between them. The 4 hard taps drew the long shadows of a crowd barrier grazing the audience floor as jagged
    // saw teeth (seen in game, CraftCity, 11 November)
    float dd = d - bias;
    vec2 size = vec2(textureSize(ShadowSampler, 0));
    vec2 st = suv * size - 0.5;
    vec2 fr = fract(st);
    vec2 base = (floor(st) + 0.5) / size;
    vec2 tx = 1.0 / size;
    float a = dd <= shadowLinear(texture(ShadowSampler, base).r, ShadowNF.x, ShadowNF.y) ? 1.0 : 0.0;
    float b = dd <= shadowLinear(texture(ShadowSampler, base + vec2(tx.x, 0.0)).r, ShadowNF.x, ShadowNF.y) ? 1.0 : 0.0;
    float e = dd <= shadowLinear(texture(ShadowSampler, base + vec2(0.0, tx.y)).r, ShadowNF.x, ShadowNF.y) ? 1.0 : 0.0;
    float g = dd <= shadowLinear(texture(ShadowSampler, base + tx).r, ShadowNF.x, ShadowNF.y) ? 1.0 : 0.0;
    return mix(mix(a, b, fr.x), mix(e, g, fr.x), fr.y);
}


void main() {
    vec2 uv = gl_FragCoord.xy / TargetSize;
    float depth = texture(DepthSampler, uv).r;
    if (depth >= 0.99999) {
        discard;                // nothing drawn there (sky): the light only lands on surfaces that exist
    }
    vec3 p = csc_position(uv);
    vec3 rel = p - ProjOrigin;
    float along = dot(rel, ProjAxis);
    if (along <= 0.05 || along > ProjShape.w) {
        discard;
    }
    vec3 up = cross(ProjAxis, ProjSide);
    float halfWidth = ProjOptics.x + along * ProjShape.x;
    vec2 q = vec2(dot(rel, ProjSide), dot(rel, up)) / halfWidth;
    // rev. 16: the common optical mask (concertstage_optics.glsl), the same function as the beam in the haze. Focus blur (rev. 12):
    // a small disc of 8 taps around the point, on the gobo (an optic out of focus spreads every point of the pattern into a disc)
    vec2 rot = ProjShape.yz;
    float dist = length(rel);
    // rev. 16c: orientation from the neighbour pixels on the same surface (concertstage_depth.glsl): right on the edges of tubes
    bool known;
    vec3 n = csc_normal(uv, p, known);
    // rev. 16g: size of this pixel on the surface (camera distance x angle of a pixel / obliquity), in texels of the gobo (256 px
    // across the beam, 128 per half width) -> level of the mipmap to read
    float pixAngle = 2.0 * InvProjMat[1][1] / TargetSize.y;
    // the pixel is stretched along the slope only: the mean of its two sides (square root of the obliquity) chooses the level,
    // less half a level, so that a floor seen at a grazing angle keeps the bars of the gobo instead of a uniform blur
    float oblique = known ? sqrt(max(abs(dot(n, normalize(p))), 0.1)) : 0.7;
    float texels = length(p) * pixAngle / oblique / max(halfWidth, 1.0e-3) * 128.0;
    cscGoboLod = max(0.0, log2(max(texels, 1.0e-4)) - 0.5);
    float k = csc_light9(q, halfWidth, along, ProjShape.x, rot, ProjOptics.y > 0.001 ? ProjOptics.y * 0.5 : 0.0);
    float mask = k;
    // rev. 15: the gobo keeps its pattern but the truss tubes and braces in front of the surface hide part of it
    float facing = known ? max(dot(n, -rel / max(dist, 1.0e-3)), 0.0) : 0.7;
    // rev. 20: on broken depth (foliage, cutout holes, block edges) the orientation is noise: neutral facing, no glint (csc_flat)
    float surf = known ? csc_flat(uv, p) : 0.0;
    float facingLit = mix(0.7, facing, surf);
    // rev. 21: a smooth surface turned away from the fixture gets no direct light (the underside of a deck, the back of a panel): it
    // kept a quarter of it, so the light seemed to cross the planks. Edges and thin tubes (orientation unknown) keep their light.
    float frontSide = known ? mix(1.0, smoothstep(-0.02, 0.12, dot(n, -rel / max(dist, 1.0e-3))), surf) : 1.0;
    // tangent of the incidence (0 facing the fixture, large when grazing): slope part of the shadow bias
    float grazing = sqrt(max(1.0 - facing * facing, 0.0)) / max(facing, 0.05);
    if (!known) {
        grazing = 1.0;      // orientation unknown (edge of an object): a moderate bias
    }
    // rev. 21: the offset goes out of the surface (its normal, towards the light) or, on an edge with no known orientation, a little
    // towards the fixture along the ray
    vec3 nOff = known ? n * sign(dot(n, -rel) + 1.0e-4) : -rel / max(dist, 1.0e-3);
    float vis = lightVisibility(p, grazing, nOff);
    if (DebugMode > 3.5) {
        // diagnostics, additive pass only: 4 = occlusions (green lit, red behind an obstacle), 5 = the optical mask itself
        if ((AddMode < 0.5 && AccumMode < 0.5) || mask <= 0.002) {
            discard;
        }
        fragColor = (AccumMode > 0.5 ? 0.25 : 1.0) * (DebugMode < 4.5 ? vec4(0.5 * (1.0 - vis), 0.5 * vis, 0.0, 1.0) * min(1.0, mask) : vec4(vec3(min(1.0, 0.5 * mask)), 1.0));
        return;
    }
    k *= vis * frontSide;
    if (k <= 0.002) {
        discard;
    }
    // rev. 16b: colour of the cells there (zones of the fixture), the plain colour of the beam otherwise
    vec3 colour = ProjLight.rgb * csc_tint(q, halfWidth, along, ProjShape.x);
    // same law as BeamOptics.surface: the flux spread over the footprint, distance, incidence
    // rev. 14: stronger fall-off with distance (a beam far over the audience no longer floods the ground and the desk)
    float e = 2.4 * ProjLight.a / (1.0 + 0.22 * halfWidth) / (1.0 + 0.035 * dist) * (0.25 + 0.75 * facingLit);
    // the end of the range fades over one block (the range already stops a little behind the surface the centre meets)
    // rev. 20: over two blocks, smoothly (a red wash grazing the audience floor ended on a hard straight line)
    e *= smoothstep(0.0, 2.0, ProjShape.w - along);
    // rev. 20 — the game darkens the faces of the blocks by their direction (top 1, north / south 0.8, east / west 0.6, underside 0.5):
    // a fixed "sky" shading, not the light of the fixture. Multiplied by it, the light of a fixture on a ceiling, the underside of a
    // roof or a tarp seen from below came out at half of the same light on the floor (seen in game, 11 November: gobos on a white
    // ceiling barely visible next to the same gobos on a wall). The light received is divided by that shading.
    if (known) {
        vec3 nw = mat3(ViewToWorld) * n;
        float shade = nw.x * nw.x * 0.6 + nw.z * nw.z * 0.8 + nw.y * nw.y * (nw.y > 0.0 ? 1.0 : 0.5);
        e /= clamp(mix(1.0, shade, surf), 0.5, 1.0);
    }
    if (AccumMode > 0.5) {
        // rev. 16b: the light of this fixture added into the accumulation target (stored / 4, blending one + one); the composite
        // applies the sum of every fixture once, saturating softly — eight blinders on one floor no longer multiply it 4^8 times
        // rev. 16d: the light the surface reflects towards the camera (glints on the tubes of the truss, sheen of a tarp), in the
        // alpha channel: the composite adds it whatever the colour of the material, so a black truss shows where it is lit
        float spec = 0.0;
        if (known && surf > 0.01) {
            vec3 rf = reflect(rel / max(dist, 1.0e-3), n);
            // rev. 16e: a tighter, weaker glint (exponent 40, was 20): matte blocks showed large white discs under a row of blinders
            spec = pow(max(dot(rf, normalize(-p)), 0.0), 40.0) * min(1.0, e * Gain) * k * surf * surf;
        }
        fragColor = vec4(colour * (e * Gain * k) * 0.25, min(1.0, spec) * 0.25);
        return;
    }
    if (AddMode > 0.5) {
        // rev. 11: a little light added on top (blending one + one): dark materials (black truss, dark tarps) show the pattern
        // in its colour too, like the shine of a metal tube in a beam, where multiplying their dark texture showed almost nothing
        // rev. 16b: a coloured light lifts dark materials a little more (a red wash on a dark floor: dark red tiles, not black
        // tiles between bright red joints); a white light keeps the small rev. 14 amount (no grey veil)
        float sat = 1.0 - min(ProjLight.r, min(ProjLight.g, ProjLight.b)) / max(max(ProjLight.r, max(ProjLight.g, ProjLight.b)), 1.0e-3);
        fragColor = vec4(colour * min(1.0, e) * k * AddK * (0.75 + 1.25 * sat), 1.0);
        return;
    }
    // rev. 14: at most x4 (it reached x16 at night and burnt large areas to white)
    float f = 1.0 + min(3.0, e * Gain) * k;
    float s = pow(f, PassRoot) - 1.0;
    fragColor = vec4(colour * s, 1.0);
}
