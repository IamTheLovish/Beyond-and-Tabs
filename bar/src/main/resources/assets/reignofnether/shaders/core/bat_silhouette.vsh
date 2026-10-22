#version 150

// Unit silhouettes (orthoview/CanopySilhouettes): a unit's body redrawn in one flat colour. Only the position, the
// colour and the texture coordinate (for the body's cut-outs) are needed.

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    texCoord0 = UV0;
}
