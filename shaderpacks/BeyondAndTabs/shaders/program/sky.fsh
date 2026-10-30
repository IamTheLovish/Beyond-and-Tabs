// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// The sky (SKY_BASIC: the dome, stars) and the sun and moon (SKY_TEXTURED). Seen in first person and the 3/4 stills;
// the RTS camera looks down, and what lies past the map's edge there is the mod's own edge tone, filled in by
// composite.fsh - so in the RTS view the sky draws nothing.

#include "/lib/settings.glsl"
#include "/lib/color.glsl"
#include "/lib/shadowfit.glsl"
#include "/lib/light.glsl"

uniform vec3 skyColor;
uniform vec3 fogColor;
uniform float viewWidth;
uniform float viewHeight;
#ifdef SKY_TEXTURED
uniform sampler2D texture;
#endif
#if defined SKY_BASIC && defined MC_RENDER_STAGE_STARS
uniform int renderStage;
#endif

varying vec2 texcoord;
varying vec4 glcolor;

/* DRAWBUFFERS:0 */
void main() {
    if (orthoView())
        discard;
#ifdef SKY_TEXTURED
    vec4 t = texture2D(texture, texcoord) * glcolor;
    gl_FragData[0] = vec4(toLinear(t.rgb) * 2.0, t.a);   // the sun's disc a little above white
#else
#ifdef MC_RENDER_STAGE_STARS
    if (renderStage == MC_RENDER_STAGE_STARS) {
        gl_FragData[0] = vec4(toLinear(glcolor.rgb) * 1.5, glcolor.a);
        return;
    }
#endif
    vec4 v = gbufferProjectionInverse * vec4(gl_FragCoord.xy / vec2(viewWidth, viewHeight) * 2.0 - 1.0, 1.0, 1.0);
    vec3 dir = normalize(v.xyz / v.w);
    float up = dot(dir, normalize(upPosition));
    // vanilla's own horizon and zenith colours for the time and biome, blended over a wider band
    vec3 c = mix(toLinear(fogColor), toLinear(skyColor), smoothstep(-0.05, 0.45, up));
    // by day our own gradient (light pass 2, lib/settings.glsl): a bluer zenith over a paler horizon, the colour the
    // aerial haze fades distant ground into (composite.fsh). Vanilla's pair stays for dawn, dusk and night
    vec3 day = mix(toLinear(SKY_HORIZON_DAY), toLinear(SKY_ZENITH_DAY), smoothstep(0.0, SKY_GRADIENT_SPAN, up));
    c = mix(c, day, skyDay());
    // a warm glow round the sun, strongest when it is low
    float toSun = max(dot(dir, normalize(sunPosition)), 0.0);
    float h = sunHeight();
    c += vec3(1.0, 0.72, 0.45) * (pow(toSun, 6.0) * 0.25 * smoothstep(-0.12, 0.05, h) * (1.0 - 0.6 * smoothstep(0.1, 0.6, h)));
    c *= mix(0.6, 1.0, smoothstep(-0.3, 0.0, up));          // the ground side of the dome darker
    gl_FragData[0] = vec4(c, 1.0);
#endif
}
