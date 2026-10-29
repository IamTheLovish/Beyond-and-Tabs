// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// Fragment stage of what is drawn as it is (see unlit.vsh). Our world-space overlays - selection rings, health and
// build bars, range rings, markers - land here: they are written in linear light so the tone curve, which is exactly
// linear below its knee, hands them back as drawn. Only the haze and the grade touch them afterwards, as the mod's own
// world look does without a pack.

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
#ifdef UNLIT_GLOW
    lin *= 1.5;                        // emissive: a little above white, so the tone curve's shoulder keeps it bright
#endif

/* DRAWBUFFERS:0 */
    gl_FragData[0] = vec4(lin, c.a);
}
