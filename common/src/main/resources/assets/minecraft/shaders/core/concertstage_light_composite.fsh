#version 150

// Concert Stage Craft 1.2.0 rev. 16b — Surface Illumination System: applies the light of every fixture at once.
// The projectors (concertstage_light_projector, AccumMode) add their light into an accumulation target (stored / 4); this pass reads
// the sum and lights the scene: dst x f with f = 1 + 3 tanh(L / 2.5) per channel (a single light as before, many lights saturating
// softly towards x4 instead of multiplying each other), in two multiply passes (PassRoot 1/2, blending dst colour + one), then the
// small additive pass for dark materials and the light reflected by the surfaces (AddMode, blending one + one; rev. 16d). Diagnostic views (DebugMode 4, 5): the target as it is.

uniform sampler2D LightSampler;
uniform vec2 TargetSize;
uniform float PassRoot;
uniform float AddMode;
uniform float AddK;
uniform float Gain;
uniform float DebugMode;
uniform vec3 AmbientTint;
// Rev. 20 — AmbientTint: the colour of the moonlight the scene is drawn with, divided out of the multiplied light. The game lights a
// night scene with a blue sky light (its light map: about twice as much blue as red), so a white fixture multiplying a moonlit
// floor gave a lavender pool (seen in game, 11 November). The multiplied part of the light is weighted by luminance / sky colour
// (1, 1, 1 by day); the added part keeps the colour of the light. Rev. 20b: the tint is applied to the gain after its saturation
// (inside tanh it vanished under a strong light: a full white wash on the audience floor still came out lavender, B/R 1.37).

out vec4 fragColor;

void main() {
    vec4 acc = texture(LightSampler, gl_FragCoord.xy / TargetSize);
    vec3 L = 4.0 * acc.rgb;
    float spec = 4.0 * acc.a;               // rev. 16d: light reflected towards the camera (projector, AccumMode)
    float m = max(L.r, max(L.g, L.b));
    if (m <= 0.002) {
        discard;
    }
    if (DebugMode > 3.5) {
        if (AddMode < 0.5) {
            discard;
        }
        fragColor = vec4(L, 1.0);
        return;
    }
    if (AddMode > 0.5) {
        float sat = 1.0 - min(L.r, min(L.g, L.b)) / m;
        // the reflected light keeps the colour of the light (its hue, normalised)
        fragColor = vec4((L / m) * min(m / max(Gain, 1.0e-3), 1.0) * AddK * (0.75 + 1.25 * sat) + min(spec, 1.5) * 0.2 * (L / m), 1.0);
        return;
    }
    vec3 f = 1.0 + 3.0 * (L / m) * tanh(m / 2.5) * AmbientTint;   // rev. 24: compressed on the brightest channel, hue kept
    fragColor = vec4(pow(f, vec3(PassRoot)) - 1.0, 1.0);
}
