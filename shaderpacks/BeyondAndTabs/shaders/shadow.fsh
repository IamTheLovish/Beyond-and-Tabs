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
    // plants read their full-size texture (light pass 10): at the shadow map's scale the grass sprites came from a mip
    // whose thin blades averaged under PLANT_SHADOW_ALPHA, so nearly every tuft's shadow was discarded
    float a = texture2D(texture, texcoord, plantCaster > 0.5 ? -8.0 : 0.0).a;
    if (a < 0.1)
        discard;
    // plants: only their solid texels (alpha >= PLANT_SHADOW_ALPHA, inside the silhouette's antialiased rim). Light
    // pass 9: pass 8 also dropped every other map texel, and the soft filter averaged the tufts' shadows to nothing;
    // the caster is drawn smaller instead (shadow.vsh)
    if (plantCaster > 0.5 && a < PLANT_SHADOW_ALPHA)
        discard;
    // shadowcolor0: red for plants, for SHADOW_PROBE (lit.fsh); white for everything else
    gl_FragData[0] = plantCaster > 0.5 ? vec4(1.0, 0.0, 0.0, 1.0) : vec4(1.0);
}
