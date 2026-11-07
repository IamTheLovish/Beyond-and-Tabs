#version 150

// Instanced unit bodies, the light pass: the eyes fragment - texel times colour, faded out in the fog.

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 c = texture(Sampler0, texCoord0) * vertexColor;
    float fade = 1.0;
    if (vertexDistance >= FogEnd)
        fade = 0.0;
    else if (vertexDistance > FogStart)
        fade = smoothstep(FogEnd, FogStart, vertexDistance);
    fragColor = c * ColorModulator * fade;
}
