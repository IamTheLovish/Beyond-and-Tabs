// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// Vanilla's clouds (light pass 5). They fell back to gbuffers_textured and were drawn as vanilla colours them: a
// grey-white slab against the dusk sky (critic, 2_dusk). From dawn to dusk they are lit by the sky's own ramp in their
// direction (lib/sky.glsl: CLOUD_SKY of it) plus CLOUD_SUN of the sun, keeping vanilla's face shading (tops lighter
// than undersides) as a relative brightness; by night vanilla's colours as drawn. No ambient occlusion (colortex1 0).

#include "/lib/settings.glsl"
#include "/lib/color.glsl"
#include "/lib/shadowfit.glsl"
#include "/lib/light.glsl"
#include "/lib/sky.glsl"

uniform sampler2D texture;
uniform float far;

varying vec2 texcoord;
varying vec4 glcolor;
varying vec3 viewPos;

/* DRAWBUFFERS:01 */
void main() {
    vec4 c = texture2D(texture, texcoord) * glcolor;
    if (c.a < 0.004)
        discard;
    vec3 asDrawn = toLinear(c.rgb);
    vec3 sunColour, skyColour;
    lightColours(sunColour, skyColour);
    // vanilla shades a cloud's faces (and dims them all at night): that shading, relative to its brightest face
    float shade = clamp(dot(c.rgb, LUMA) / max(max(glcolor.r, max(glcolor.g, glcolor.b)), 1e-3), 0.5, 1.0);
    vec3 dir = normalize(viewPos);
    // lit from the sun's side (light pass 6): the face of the layer toward the sun takes up to twice CLOUD_SUN
    float sunSide = 0.5 + 0.5 * dot(dir, normalize(sunPosition));
    vec3 lit = (skyGradient(dir) * CLOUD_SKY + sunColour * (CLOUD_SUN * 2.0 * sunSide)) * shade;
    // the layer fades out toward its rim (CLOUD_RIM_*, of the render distance, measured across the ground): seen from a
    // 3/4 view its far edge was a dotted ellipse in the sky (light pass 6)
    vec3 player = mat3(gbufferModelViewInverse) * viewPos;
    float alpha = c.a * (1.0 - smoothstep(CLOUD_RIM_START, CLOUD_RIM_END, length(player.xz) / max(far, 16.0)));
    if (alpha < 0.004)
        discard;
#if SKY_PROBE == 1
    gl_FragData[0] = vec4(0.0, 1.0, 1.0, alpha);
    gl_FragData[1] = vec4(0.0, 0.0, 0.0, alpha);
    return;
#endif
    // the RTS camera is orthographic (no view direction to read the ramp along): its clouds stay as drawn
    gl_FragData[0] = vec4(orthoView() ? asDrawn : mix(asDrawn, lit, skyOwn()), alpha);
    gl_FragData[1] = vec4(0.0, 0.0, 0.0, alpha);
}
