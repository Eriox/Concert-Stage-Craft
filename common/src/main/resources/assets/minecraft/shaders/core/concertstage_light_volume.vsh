#version 150

// Concert Stage Craft 1.2.0 rev. 9: coloured point light drawn over the scene (see VolumeLights.java).
// Position: corner of a quad covering the light on screen. UV0 + UV2.x: centre of the light (UV2.x in 1/100 block),
// UV2.y: radius (1/100 block). Color: hue of the light, alpha = strength.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec3 centreView;
out float radius;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    centreView = (ModelViewMat * vec4(UV0, float(UV2.x) / 100.0, 1.0)).xyz;
    radius = float(UV2.y) / 100.0;
}
