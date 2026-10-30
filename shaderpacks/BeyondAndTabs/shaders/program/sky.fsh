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
#include "/lib/sky.glsl"

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
    // the sun is our own disc, drawn with the dome (light pass 4: the loader does not name the sun's render stage, so
    // pass 3's check never ran and vanilla's square showed as a pale rectangle). The sprite is found by direction:
    // vanilla's sun quad spans ~0.3 rad from its centre (0.42 to a corner), the moon sits opposite
    vec4 sv = gbufferProjectionInverse * vec4(gl_FragCoord.xy / vec2(viewWidth, viewHeight) * 2.0 - 1.0, 1.0, 1.0);
    if (dot(normalize(sv.xyz / sv.w), normalize(sunPosition)) > SUN_SPRITE_COS)
        discard;
    vec4 t = texture2D(texture, texcoord) * glcolor;
#if SKY_PROBE == 1
    gl_FragData[0] = vec4(0.0, 1.0, 0.0, t.a);
    return;
#endif
    gl_FragData[0] = vec4(toLinear(t.rgb) * 2.0, t.a);   // the moon (and the sun, were it not dropped above)
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
    // vanilla's own horizon and zenith colours carry the night; from dawn to dusk our sky (lib/sky.glsl), which
    // composite.fsh also paints over every undrawn pixel (light pass 6: this program alone left the clear colour showing)
    vec3 c = mix(toLinear(fogColor), toLinear(skyColor), smoothstep(-0.05, 0.45, up));
    c = mix(c, skyWithLights(dir), skyOwn());
#if SKY_PROBE == 1
    c = vec3(1.0, 0.0, 1.0);
#endif
    gl_FragData[0] = vec4(c, 1.0);
#endif
}
