// Concert Stage Craft 1.2.0 rev. 16c — the surfaces really drawn, rebuilt from the copy of the depth buffer (Surface Illumination).
// Shared by the projected light (concertstage_light_projector) and the diagnostic views (concertstage_light_debug), so that what the
// diagnostic shows is exactly what the light uses. Needs: DepthSampler, InvProjMat, TargetSize declared before the import.
//
// csc_position(uv): view-space position of the pixel (a point on a surface that was drawn; the sky: depth 1)
// csc_normal(uv, p, known): orientation of that surface. Each axis takes the neighbour pixel (left / right, below / above) on the
// same surface — the one nearer in depth — so the normal stays right on the edge of a thin truss tube or a diagonal, where one of
// the two neighbours is on the floor far behind (the 2 x 2 derivatives used before mixed the tube and the floor there). A floor seen
// at a grazing angle is recognised as a plane (its depth changes evenly), not as an edge. known = false when both neighbours are on
// other surfaces (a one-pixel-wide tube): the caller then uses a neutral facing.

vec3 csc_position(vec2 uv) {
    float depth = texture(DepthSampler, uv).r;
    vec4 vp = InvProjMat * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return vp.xyz / vp.w;
}

vec3 csc_normal(vec2 uv, vec3 p, out bool known) {
    vec2 px = 1.0 / TargetSize;
    vec3 l = csc_position(uv - vec2(px.x, 0.0));
    vec3 r = csc_position(uv + vec2(px.x, 0.0));
    vec3 d = csc_position(uv - vec2(0.0, px.y));
    vec3 u = csc_position(uv + vec2(0.0, px.y));
    // each axis: the side on the same surface. A plane seen at a grazing angle changes depth a lot from pixel to pixel but evenly
    // (both sides agree: a plane, known); an edge has one side far away (take the near one, known if it is near enough)
    vec3 dr = r - p, dl = p - l, du = u - p, dd = p - d;
    vec3 dx = abs(dr.z) < abs(dl.z) ? dr : dl;
    vec3 dy = abs(du.z) < abs(dd.z) ? du : dd;
    float lim = 0.02 * max(1.0, -p.z) + 0.02;
    bool planeX = abs(dr.z - dl.z) <= 0.25 * max(abs(dr.z), abs(dl.z)) + 1.0e-4;
    bool planeY = abs(du.z - dd.z) <= 0.25 * max(abs(du.z), abs(dd.z)) + 1.0e-4;
    known = (planeX || abs(dx.z) < lim) && (planeY || abs(dy.z) < lim);
    vec3 n = cross(dx, dy);
    float nl = length(n);
    if (nl < 1.0e-9) {
        known = false;
        return vec3(0.0, 0.0, 1.0);
    }
    n /= nl;
    return dot(n, -p) < 0.0 ? -n : n;
}

// rev. 20 — how much the pixel lies on a smooth surface (1) rather than on broken depth (0): leaves, grass, flowers, the holes of
// a cutout texture, the edge of a block. Each axis compares the two depth steps around the pixel (a plane, even seen at a grazing
// angle, has two nearly equal steps; foliage has one step to the leaf behind). The orientation found there is noise: the light then
// uses a neutral facing and no glint (seen in game, 11 November: trees under a white wash full of white and yellow speckles).
float csc_flat(vec2 uv, vec3 p) {
    vec2 px = 1.0 / TargetSize;
    float l = csc_position(uv - vec2(px.x, 0.0)).z;
    float r = csc_position(uv + vec2(px.x, 0.0)).z;
    float d = csc_position(uv - vec2(0.0, px.y)).z;
    float u = csc_position(uv + vec2(0.0, px.y)).z;
    float lim = 0.004 * max(1.0, -p.z) + 0.004;
    float sx = abs(r + l - 2.0 * p.z) / (max(abs(r - p.z), abs(p.z - l)) + lim);
    float sy = abs(u + d - 2.0 * p.z) / (max(abs(u - p.z), abs(p.z - d)) + lim);
    return 1.0 - smoothstep(0.25, 0.7, max(sx, sy));
}
