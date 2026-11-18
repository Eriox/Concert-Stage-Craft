#version 150

// Concert Stage Craft 1.2.0 rev. 10: a fixture projecting its light (gobo, spot or wash) on everything drawn in its cone.
// Position: corner of a quad covering the cone on screen.

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
