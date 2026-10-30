#version 120
// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// The shadow map keeps depth only; cut-out texels (leaf gaps, fences) cast nothing.

#include "/lib/settings.glsl"

uniform sampler2D texture;

varying vec2 texcoord;
varying float plantCaster;

void main() {
    float a = texture2D(texture, texcoord).a;
    if (a < 0.1)
        discard;
    // plants cast a thinner shadow (light pass 8: a tuft's dark splat as big as the tuft): only their solid texels
    // (alpha >= PLANT_SHADOW_ALPHA, inside the silhouette's antialiased rim), and every other texel of the map, which
    // the soft filter averages to ~half a shadow
    if (plantCaster > 0.5 && (a < PLANT_SHADOW_ALPHA || mod(floor(gl_FragCoord.x) + floor(gl_FragCoord.y), 2.0) > 0.5))
        discard;
    gl_FragData[0] = vec4(1.0);
}
