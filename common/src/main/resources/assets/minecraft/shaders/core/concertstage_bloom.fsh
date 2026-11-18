#version 150

// Concert Stage Craft 1.2.0 rev. 21 — bloom of the light of the fixtures (see com.concertstage.client.light.Bloom).
// Mode 0: source at half size = what the beams, lenses and flashes added to the frame (frame − copy taken before them), above a soft
//         threshold (faint haze does not bloom, a beam core and a lit lens do), 4 bilinear taps.
// Mode 1: down (dual filter: centre x 4 + 4 diagonal taps).
// Mode 2: up (8 taps, tent), added to the level above by the blending.
// Mode 3: up onto the frame, times Strength.

uniform sampler2D SrcSampler;
uniform sampler2D BeforeSampler;
uniform float Mode;
uniform vec2 SrcTexel;
uniform vec2 OutSize;
uniform float Strength;

out vec4 fragColor;

vec3 added(vec2 uv) {
    return max(texture(SrcSampler, uv).rgb - texture(BeforeSampler, uv).rgb, vec3(0.0));
}

vec3 up(vec2 uv) {
    vec2 h = SrcTexel * 0.5;
    vec3 s = texture(SrcSampler, uv + vec2(-h.x * 2.0, 0.0)).rgb;
    s += texture(SrcSampler, uv + vec2(h.x * 2.0, 0.0)).rgb;
    s += texture(SrcSampler, uv + vec2(0.0, -h.y * 2.0)).rgb;
    s += texture(SrcSampler, uv + vec2(0.0, h.y * 2.0)).rgb;
    s += texture(SrcSampler, uv + vec2(-h.x, h.y)).rgb * 2.0;
    s += texture(SrcSampler, uv + vec2(h.x, h.y)).rgb * 2.0;
    s += texture(SrcSampler, uv + vec2(-h.x, -h.y)).rgb * 2.0;
    s += texture(SrcSampler, uv + vec2(h.x, -h.y)).rgb * 2.0;
    return s / 12.0;
}

void main() {
    vec2 uv = gl_FragCoord.xy / OutSize;
    if (Mode < 0.5) {
        vec2 o = SrcTexel * 0.5;
        vec3 c = (added(uv + vec2(-o.x, -o.y)) + added(uv + vec2(o.x, -o.y)) + added(uv + vec2(-o.x, o.y)) + added(uv + o)) * 0.25;
        float l = max(c.r, max(c.g, c.b));
        // soft knee: nothing below 0.12, all of it above 0.45
        float k = smoothstep(0.12, 0.45, l);
        fragColor = vec4(c * k, 1.0);
    } else if (Mode < 1.5) {
        vec2 h = SrcTexel * 0.5;
        vec3 s = texture(SrcSampler, uv).rgb * 4.0;
        s += texture(SrcSampler, uv - h).rgb;
        s += texture(SrcSampler, uv + h).rgb;
        s += texture(SrcSampler, uv + vec2(h.x, -h.y)).rgb;
        s += texture(SrcSampler, uv - vec2(h.x, -h.y)).rgb;
        fragColor = vec4(s / 8.0, 1.0);
    } else if (Mode < 2.5) {
        fragColor = vec4(up(uv), 1.0);
    } else {
        fragColor = vec4(up(uv) * Strength, 1.0);
    }
}
