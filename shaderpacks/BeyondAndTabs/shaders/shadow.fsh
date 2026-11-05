#version 120
// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// The shadow map keeps depth, and in shadowcolor0's red how much of the sun the nearest caster takes (1 = all; a
// plant PLANT_SHADOW, shadow.vsh); cut-out texels (leaf gaps, fences, between grass blades) cast nothing.

#include "/lib/settings.glsl"

uniform sampler2D texture;

varying vec2 texcoord;
varying float casterOpacity;

void main() {
    if (texture2D(texture, texcoord).a < 0.1)
        discard;
    gl_FragData[0] = vec4(casterOpacity, casterOpacity, casterOpacity, 1.0);
}
