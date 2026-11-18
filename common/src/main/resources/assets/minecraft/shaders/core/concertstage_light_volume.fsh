#version 150

// Concert Stage Craft 1.2.0 rev. 9: lights what is already drawn around a point, like a coloured lamp.
// The position of each pixel is rebuilt from a copy of the depth buffer; the light falls off with the distance
// to its centre and with the angle of the surface. Blending dst * (1 + src): textures keep their detail, black
// stays black, the sky is never touched. Drawn in several passes (PassRoot = 1 / passes) for strong lights.

uniform sampler2D DepthSampler;
uniform mat4 InvProjMat;
uniform vec2 TargetSize;
uniform float Gain;
uniform float PassRoot;
uniform float AddMode;
uniform float AddK;
uniform vec3 AmbientTint;
uniform float AccumMode;      // rev. 20: 1 = add the light into the accumulation target (applied with the projected light, tone mapped)
// Rev. 20 — AmbientTint: the colour of the moonlight the scene is drawn with, divided out of the multiplied light. The game lights a
// night scene with a blue sky light (its light map: about twice as much blue as red), so a white fixture multiplying a moonlit
// floor gave a lavender pool (seen in game, 11 November). The multiplied part of the light is weighted by luminance / sky colour
// (1, 1, 1 by day); the added part keeps the colour of the light.

in vec4 vertexColor;
in vec3 centreView;
in float radius;

out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy / TargetSize;
    float depth = texture(DepthSampler, uv).r;
    vec4 ndc = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 vp = InvProjMat * ndc;
    vec3 p = vp.xyz / vp.w;
    // surface orientation from the neighbouring pixels (computed before any discard)
    vec3 dpx = dFdx(p);
    vec3 dpy = dFdy(p);
    vec3 n = cross(dpx, dpy);
    float jump = max(length(dpx), length(dpy));
    if (depth >= 0.99999) {
        discard;
    }
    vec3 toLight = centreView - p;
    float dist = length(toLight);
    if (dist >= radius) {
        discard;
    }
    float nl = length(n);
    float facing = 0.7;
    // rev. 12: surface orientation only trusted away from object edges (see the projector shader)
    if (nl > 1.0e-6 && jump < 0.02 * max(1.0, -p.z)) {
        n = n / nl;
        if (dot(n, -p) < 0.0) {
            n = -n;
        }
        facing = max(dot(n, toLight / max(dist, 1.0e-3)), 0.0);
    }
    float x = 1.0 - dist / radius;
    float att = x * x;
    if (AccumMode > 0.5) {
        // stored / 4 like the projectors (concertstage_light_projector, AccumMode)
        fragColor = vec4(vertexColor.rgb * (vertexColor.a * Gain * att * (0.3 + 0.7 * facing)) * 0.25, 0.0);
        return;
    }
    if (AddMode > 0.5) {
        // rev. 11: a little coloured light added on top, so dark materials (black truss, dark tarps) take the colour too
        fragColor = vec4(vertexColor.rgb * vertexColor.a * att * (0.3 + 0.7 * facing) * AddK, 1.0);
        return;
    }
    float f = 1.0 + vertexColor.a * Gain * att * (0.3 + 0.7 * facing);
    float s = pow(f, PassRoot) - 1.0;
    fragColor = vec4(vertexColor.rgb * AmbientTint * s, 1.0);
}
