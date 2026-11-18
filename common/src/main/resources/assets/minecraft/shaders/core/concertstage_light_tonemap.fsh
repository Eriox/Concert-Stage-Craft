#version 150

// Concert Stage Craft 1.2.0 rev. 19 — the light of every fixture applied in one pass, with tone mapping.
// Before (rev. 16b), the accumulated light multiplied the scene in two blended passes (dst x f, f = 1 + 3 tanh(L / 2.5), up to x4):
// the blending cannot read the scene, so a pale floor or a light truss under several fixtures went past white and was clipped — a
// flat white patch with no texture left (overexposure). Here the scene is read (a copy of the frame), lit the same way (same f, same
// additive light for dark materials), then the highlights are compressed above a knee on the brightest channel of each pixel: the
// hue is kept, the texture stays visible in the brightest spots and nothing is clipped. Below the knee nothing changes.
// The CPU copy of this maths is tested in tools/rev19/LightingFixTest (toneCurve) and tools/rev24/MixTest (rev. 24, hue kept).

uniform sampler2D LightSampler;
uniform sampler2D SceneSampler;
uniform vec2 TargetSize;
uniform float AddK;
uniform float Gain;
uniform float DebugMode;
uniform float Knee;
uniform vec3 AmbientTint;

// Rev. 20 — AmbientTint: the colour of the moonlight the scene is drawn with, divided out of the multiplied light. The game lights a
// night scene with a blue sky light (its light map: about twice as much blue as red), so a white fixture multiplying a moonlit
// floor gave a lavender pool (seen in game, 11 November). The multiplied part of the light is weighted by luminance / sky colour
// (1, 1, 1 by day); the added part keeps the colour of the light. Rev. 20b: the tint is applied to the gain after its saturation
// (inside tanh it vanished under a strong light: a full white wash on the audience floor still came out lavender, B/R 1.37).

out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy / TargetSize;
    vec4 acc = texture(LightSampler, uv);
    vec3 L = 4.0 * acc.rgb;
    float spec = 4.0 * acc.a;
    float m = max(L.r, max(L.g, L.b));
    if (m <= 0.002) {
        discard;
    }
    vec3 scene = texture(SceneSampler, uv).rgb;
    if (DebugMode > 3.5) {
        fragColor = vec4(scene + L, 1.0);
        return;
    }
    float sat = 1.0 - min(L.r, min(L.g, L.b)) / m;
    // Rev. 24 — the light is compressed on its brightest channel, the same factor for the three channels: the hue of the mix is kept.
    // Before, each channel saturated on its own (tanh per channel, min per channel): under a cyan wash the green and blue were at the
    // ceiling, a magenta gobo on top could only raise the red, and the pattern turned white x floor = the colour of the floor.
    vec3 hue = L / m;
    vec3 add = hue * min(m / max(Gain, 1.0e-3), 1.0) * AddK * (0.75 + 1.25 * sat) + min(spec, 1.5) * 0.2 * hue;
    // the same slope as before for one fixture (1 + 2.2 tanh(L / 1.83) starts at 1.2 L), a ceiling of x3.2 where many fixtures overlap;
    // then a soft shoulder (x / (1 + x) above the knee) on the brightest channel
    vec3 c = scene * (1.0 + 2.2 * hue * tanh(m / 1.83) * AmbientTint) + add;
    float peak = max(c.r, max(c.g, c.b));
    float k = clamp(Knee, 0.5, 0.95);
    if (peak > k) {
        float x = (peak - k) / (1.0 - k);
        float np = k + (1.0 - k) * x / (1.0 + x);
        c *= np / peak;
    }
    fragColor = vec4(c, 1.0);
}
