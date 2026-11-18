#version 150

// Concert Stage Craft 1.2.0 rev. 15: depth of what can stop a beam (the colour is not used).
// Rev. 16c: the transparent texels of cut-out textures let the light through (as the game discards them when it draws the block):
// before, a face with holes stopped the light like a plain wall — an invisible surface in the openings. testAlpha 0: no test
// (the truss lattice boxes of BeamShadows).

uniform sampler2D Sampler0;

in vec2 texCoord0;
in float testAlpha;

out vec4 fragColor;

void main() {
    if (testAlpha > 0.5 && texture(Sampler0, texCoord0).a < 0.1) {
        discard;
    }
    fragColor = vec4(1.0);
}
