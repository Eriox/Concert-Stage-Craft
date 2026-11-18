#version 150

// Concert Stage Craft 1.2.0 rev. 16c — diagnostic views of what the light engine reads (LightDiagnostics), over the whole screen.
// DebugMode 6 = depth used by the engine (copy of the depth buffer: near white, far dark, nothing drawn = blue);
// DebugMode 7 = normals rebuilt from it, as the projected light uses them (view space, x red, y green, z blue; magenta = unknown,
// the pixel is alone on its surface — a tube one pixel wide —: the light uses a neutral facing there).

uniform sampler2D DepthSampler;
uniform mat4 InvProjMat;
uniform vec2 TargetSize;
uniform float DebugMode;

#moj_import <concertstage_depth.glsl>

out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy / TargetSize;
    float depth = texture(DepthSampler, uv).r;
    if (depth >= 0.99999) {
        fragColor = vec4(0.05, 0.08, 0.25, 1.0);
        return;
    }
    vec3 p = csc_position(uv);
    if (DebugMode < 6.5) {
        float g = 1.0 - log2(1.0 + length(p)) / log2(257.0);
        fragColor = vec4(vec3(g), 1.0);
        return;
    }
    bool known;
    vec3 n = csc_normal(uv, p, known);
    fragColor = known ? vec4(n * 0.5 + 0.5, 1.0) : vec4(1.0, 0.0, 1.0, 1.0);
}
