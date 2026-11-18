#version 150

// Concert Stage Craft 1.2.0 rev. 15: depth of what can stop a beam, seen from the fixture (BeamShadows).
// Rev. 16c: with the colour and the texture coordinates (block format order: Position, Color, UV0), for the alpha test.

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord0;
out float testAlpha;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    testAlpha = Color.a;
}
