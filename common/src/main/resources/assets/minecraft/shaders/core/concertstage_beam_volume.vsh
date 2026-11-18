#version 150

// Concert Stage Craft 1.2.0 rev. 15: the beam of one fixture in the haze. Position: corner of the screen rectangle that holds the
// cone, directly in normalised device coordinates.

in vec3 Position;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
}
