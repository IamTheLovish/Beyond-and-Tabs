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
    // plants: only their solid texels (alpha >= PLANT_SHADOW_ALPHA, inside the silhouette's antialiased rim). Light
    // pass 9: pass 8 also dropped every other map texel, and the soft filter averaged the tufts' shadows to nothing;
    // the caster is drawn smaller instead (shadow.vsh)
    if (plantCaster > 0.5 && a < PLANT_SHADOW_ALPHA)
        discard;
    gl_FragData[0] = vec4(1.0);
}
