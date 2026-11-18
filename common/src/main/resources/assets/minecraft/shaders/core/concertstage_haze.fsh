#version 150

// Concert Stage Craft 1.2.0 rev. 16b — Atmospheric System: the haze between the camera and the scenery. With a medium or dense haze
// the decor fades with the distance into the colour of the haze (dark at night: the smoke only shows where a beam lights it, which
// is the beam shader's job). Drawn once over the screen after the opaque scene and the fixture bodies, before the beams.
// HazeVeil: rgb = colour of the haze, a = density per block. Blending: src alpha, one minus src alpha.

uniform sampler2D DepthSampler;
uniform mat4 InvProjMat;
uniform vec2 TargetSize;
uniform vec4 HazeVeil;

out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy / TargetSize;
    float depth = texture(DepthSampler, uv).r;
    vec4 vp = InvProjMat * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    float dist = depth >= 0.99999 ? 192.0 : length(vp.xyz / vp.w);
    float a = 1.0 - exp(-HazeVeil.a * dist);
    if (a <= 0.002) {
        discard;
    }
    fragColor = vec4(HazeVeil.rgb, min(a, 0.85));
}
