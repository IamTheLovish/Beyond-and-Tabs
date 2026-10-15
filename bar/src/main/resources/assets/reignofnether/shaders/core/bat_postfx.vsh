#version 150

// The world look pass (postfx/WorldLookPass): one quad drawn straight in clip space, so no matrices are needed.

in vec3 Position;

out vec2 texCoord;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    texCoord = Position.xy * 0.5 + 0.5;
}
