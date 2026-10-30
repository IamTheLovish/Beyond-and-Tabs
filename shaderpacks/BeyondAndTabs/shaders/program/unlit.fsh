// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// Fragment stage of what is drawn as it is (see unlit.vsh). Our world-space overlays - selection rings, health and
// build bars, range rings, markers - land here: they are written in linear light so the tone curve, which is exactly
// linear below its knee, hands an opaque overlay back as drawn. A translucent one blends with the ground in linear
// light, so it lands a little lighter over dark ground than vanilla's blend would put it. Only the haze and the grade
// touch them afterwards, as the mod's own world look does without a pack.
// UNLIT_CRACKS: the block-breaking cracks, which multiply what is under them (twice the product, so mid-grey is no
// change): their colour is turned into the linear multiplier that does on screen what it does without a pack.

#include "/lib/settings.glsl"
#include "/lib/color.glsl"
#ifdef UNLIT_LIGHTMAP
#include "/lib/light.glsl"
#endif

#ifndef UNLIT_BASIC
uniform sampler2D texture;
#endif

varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;

void main() {
#ifdef UNLIT_BASIC
    vec4 c = glcolor;
#else
    vec4 c = texture2D(texture, texcoord) * glcolor;
#endif
    if (c.a < 0.004)
        discard;
    vec3 lin = toLinear(c.rgb);
#ifdef UNLIT_LIGHTMAP
    // particles and rain: no shadow lookup (they are tiny and many), lit as an upward face in the open would be
    vec3 sunColour, skyColour;
    lightColours(sunColour, skyColour);
    vec3 up = normalize(upPosition);
    lin *= surfaceLight(up, 0.6, 1.0, lightLevels(lmcoord), 1.0, sunColour, skyColour);
#endif
#ifdef UNLIT_CRACKS
    lin = 0.5 * toLinear(min(2.0 * c.rgb, vec3(1.0)));
#endif
#ifdef UNLIT_GLOW
    lin *= 1.5;                        // emissive: a little above white, so the tone curve's shoulder keeps it bright
#endif

/* DRAWBUFFERS:0 */
    gl_FragData[0] = vec4(lin, c.a);
}
