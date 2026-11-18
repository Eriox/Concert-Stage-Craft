#version 150

// CSC shader harness: evaluates the common optical mask (csc_light) on a grid of the cross-section, |q| <= 1.1, for the comparison
// with its Java twin (OpticalProfile.lightAt); gba = csc_tint (OpticalProfile.tint). ProbeArgs: x = hw, y = along, z = tanH.

uniform sampler2D PatternSampler;
uniform vec2 TargetSize;
uniform vec4 ProbeArgs;

#moj_import <concertstage_optics.glsl>

out vec4 fragColor;

void main() {
    vec2 q = (gl_FragCoord.xy / TargetSize * 2.0 - 1.0) * 1.1;
    vec3 t = csc_tint(q, ProbeArgs.x, ProbeArgs.y, ProbeArgs.z);
    fragColor = vec4(csc_light(q, ProbeArgs.x, ProbeArgs.y, ProbeArgs.z, vec2(1.0, 0.0), vec2(0.0)), t);
}
