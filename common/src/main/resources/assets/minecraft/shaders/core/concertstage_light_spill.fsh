#version 150

// Concert Stage Craft 1.2.0 rev. 21 — the light a fixture sends around its beam, added to the accumulation of the projected light.
// Two kinds, one shader:
//  * the light of the beam in the haze on what is next to it (a segment light along the beam: SpillA -> SpillB). A truss tube or
//    a brace a little beside a beam is lit by the haze the beam lights, on every side, not only on the faces the beam itself meets;
//  * the light the lit floor / deck sends back up (a point light just above the footprint: SpillA = SpillB), which lights the
//    underside of the truss and the roof over a lit stage (pictures 3 and 4 of 11 November: truss lit on one side only).
// Neither goes through what the light of the fixture cannot cross: SpillPlane keeps them on the side of the surface the beam meets
// (the deck top: nothing under the stage). Output: light / 4 into the accumulation target (as concertstage_light_projector, AccumMode).

uniform sampler2D DepthSampler;
uniform mat4 InvProjMat;
uniform vec2 TargetSize;
uniform float Gain;
uniform vec3 SpillA;
uniform vec3 SpillB;
uniform vec4 SpillLight;        // rgb colour (brightest channel 1), a strength
uniform vec4 SpillShape;        // x radius (blocks), y core size (blocks), z wrap of the facing (0 hard, 1 every side), w start above the plane
uniform vec4 SpillPlane;        // view space: n.xyz, d; |n| = 0: no plane
uniform vec4 SpillFloor;        // view space: a second plane the light stays above (the lowest point of a beam); |n| = 0: none
uniform float DebugMode;

#moj_import <concertstage_depth.glsl>

out vec4 fragColor;

void main() {
    if (DebugMode > 3.5) {
        discard;                // diagnostic views show the beams' own light only
    }
    vec2 uv = gl_FragCoord.xy / TargetSize;
    float depth = texture(DepthSampler, uv).r;
    if (depth >= 0.99999) {
        discard;
    }
    vec3 p = csc_position(uv);
    vec3 ab = SpillB - SpillA;
    float l2 = dot(ab, ab);
    float t = l2 > 1.0e-6 ? clamp(dot(p - SpillA, ab) / l2, 0.0, 1.0) : 0.0;
    vec3 c = SpillA + ab * t;
    vec3 d = p - c;
    float dist = length(d);
    float R = SpillShape.x;
    if (dist >= R) {
        discard;
    }
    float side = 1.0;
    if (dot(SpillPlane.xyz, SpillPlane.xyz) > 0.25) {
        float h = dot(SpillPlane.xyz, p) + SpillPlane.w;
        // SpillShape.w: how far above that surface the light starts (the surface itself already has the projected light)
        side = smoothstep(SpillShape.w - 0.1, SpillShape.w + 0.25, h);
        if (side <= 0.0) {
            discard;
        }
    }
    if (dot(SpillFloor.xyz, SpillFloor.xyz) > 0.25) {
        side *= smoothstep(-0.15, 0.1, dot(SpillFloor.xyz, p) + SpillFloor.w);
        if (side <= 0.0) {
            discard;
        }
    }
    bool known;
    vec3 n = csc_normal(uv, p, known);
    float facing = known ? dot(n, -d / max(dist, 1.0e-3)) : 0.4;
    float surf = known ? csc_flat(uv, p) : 0.0;
    facing = mix(0.4, facing, surf);
    float w = SpillShape.z;
    float lit = clamp((facing + w) / (1.0 + w), 0.0, 1.0);
    float x = dist / R;
    float edge = (1.0 - x * x);
    float core = SpillShape.y;
    float fall = edge * edge / (1.0 + dist * dist / max(core * core, 1.0e-3));
    float e = SpillLight.a * fall * lit * side;
    if (e * Gain < 0.002) {
        discard;
    }
    fragColor = vec4(SpillLight.rgb * (e * Gain) * 0.25, 0.0);
}
