#version 120
// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// The shadow map keeps depth, and in shadowcolor0's red how much of the sun the nearest caster takes (1 = all; a
// plant PLANT_SHADOW, shadow.vsh); cut-out texels (leaf gaps, fences, between grass blades) cast nothing.
// A caster that takes none of the sun (a plant under a low sun, shadow.vsh's fade) writes nothing either: the map
// keeps the NEAREST caster per texel, so a tuft written at opacity 0 in front of a hill, a bank or a low wall would
// hide that solid caster's shadow and read fully lit (Eris.16, #544 r1)
const float CASTER_NONE = 0.003;       // under one step (1/255) of an 8-bit shadowcolor0

#include "/lib/settings.glsl"

uniform sampler2D texture;

varying vec2 texcoord;
varying float casterOpacity;

void main() {
    if (texture2D(texture, texcoord).a < 0.1 || casterOpacity < CASTER_NONE)
        discard;
    gl_FragData[0] = vec4(casterOpacity, casterOpacity, casterOpacity, 1.0);
}
