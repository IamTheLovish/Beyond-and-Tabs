#version 150

// Unit silhouettes (orthoview/CanopySilhouettes): the flat colour where the body's own texture is not cut out. The
// depth test (GREATER, set by the render type) has already kept only the pixels where a leaf or wall is in front.

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    if (texture(Sampler0, texCoord0).a < 0.1)
        discard;
    fragColor = vec4(vertexColor.rgb, 1.0);
}
