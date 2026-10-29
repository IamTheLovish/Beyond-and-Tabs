#version 150

// Capsule silhouettes (orthoview/CanopySilhouettes): one camera-facing quad per covered unit. UV0 runs -1..1 across the
// quad's width and -A..A along its height, A = the capsule's height / width, carried in the colour's alpha as A / 4.

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec2 shape;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    shape = UV0;
}
