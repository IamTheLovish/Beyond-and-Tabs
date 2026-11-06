#version 150

// The instanced unit bodies' lit pass (see the .vsh): the entity cutout's fragment - the texel (cut out under alpha
// 0.1), times the shaded colour, the hurt overlay over it, times the light map, into the fog.

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
in vec4 normal;

out vec4 fragColor;

void main() {
    vec4 texel = texture(Sampler0, texCoord0);
    if (texel.a < 0.1)
        discard;
    vec4 c = texel * vertexColor * ColorModulator;
    c.rgb = mix(overlayColor.rgb, c.rgb, overlayColor.a);
    c *= lightMapColor;
    if (vertexDistance > FogStart) {
        float f = vertexDistance < FogEnd ? smoothstep(FogStart, FogEnd, vertexDistance) : 1.0;
        c = vec4(mix(c.rgb, FogColor.rgb, f * FogColor.a), c.a);
    }
    fragColor = c;
}
