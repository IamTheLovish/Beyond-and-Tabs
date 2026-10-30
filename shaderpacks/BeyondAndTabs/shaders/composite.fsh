#version 120
// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// The lit world (linear light, colortex0) onto the screen: the tone curve, then the air. The distance haze takes the
// mod's own world look's distance rule (postfx/WorldLookPass, bat_postfx.fsh), aiming for the same air: in the RTS
// view distance is measured along the view from the ground at the screen's bottom edge (an ortho image has no eye),
// one e-fold per 56 blocks; in first person from the eye, clear for the first 12 blocks, one e-fold per 180. The haze
// never covers more than 30% (Enchanted), so far overlays stay legible, and dark ground keeps less of it (the fog of
// war's seen / unseen read is gameplay).
// Before the tone curve, slice 3's ambient occlusion (lib/ao.glsl) takes its share of each pixel's light: the sky's
// fill, as the lit draws recorded it in colortex1 (0 for overlays, water and everything drawn as is).
// Also before the tone curve, slice 3's bloom source (colortex4): only light past BLOOM_THRESHOLD in linear light,
// which lit ground never reaches (noon ground sits at its albedo, under 0.85) - lanterns and other emissive texels, glows,
// the sun's glint on a metal. final.fsh spreads it.

#include "/lib/settings.glsl"
#include "/lib/color.glsl"
#include "/lib/ao.glsl"

/*
const int colortex0Format = RGBA16F;
const int colortex1Format = RGBA8;
const vec4 colortex1ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const int colortex4Format = RGBA16F;
*/
const bool colortex4Clear = false;     // this pass writes every pixel of it

uniform sampler2D colortex0;
uniform sampler2D colortex1;
uniform sampler2D colortex3;
uniform float viewWidth;
uniform sampler2D depthtex0;
uniform sampler2D depthtex1;
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;
uniform vec3 skyColor;
uniform vec3 fogColor;
uniform float far;
uniform float viewHeight;
uniform int isEyeInWater;

varying vec2 texcoord;

const float HAZE_MAX = 0.30;
const float HAZE_MAX_LIMIT = 0.85;
const float FP_HAZE_START = 12.0;
const float FP_HAZE_SCALE = 180.0;
const float RTS_HAZE_SCALE = 56.0;
const vec3 DISTANCE_COOL = vec3(0.93, 0.99, 1.07);   // scattering drifts distant colour cool before the haze veils it
// linear luma past which light blooms. The light table puts sunlit open ground at its albedo, so even white quartz or
// snow at noon stays under ~0.85: a plain noon lawn adds exactly nothing (design: mean change under 1/255)
const float BLOOM_THRESHOLD = 1.0;

bool orthoView() {
    return gbufferProjection[2][3] == 0.0 && gbufferProjection[3][3] == 1.0;
}

vec3 viewPosAt(vec2 uv, float depth) {
    vec4 v = gbufferProjectionInverse * vec4(vec3(uv, depth) * 2.0 - 1.0, 1.0);
    return v.xyz / v.w;
}

// view depth of the ground at the bottom edge of the RTS view: the farthest of five samples along the bottom row, so a
// unit or a wall standing there does not pull the haze toward the camera
float rtsHazeStart() {
    float v = 2.5 / viewHeight;
    float best = -1.0;
    for (int i = 0; i < 5; i++) {
        float d = texture2D(depthtex0, vec2((float(i) + 0.5) / 5.0, v)).r;
        if (d < 1.0)
            best = max(best, d);
    }
    return best < 0.0 ? 0.0 : -viewPosAt(vec2(0.5, v), best).z;
}

// the blurred occlusion back at full resolution: of the four half-resolution neighbours, bilinear weights times how
// near each one's depth is to this pixel's, so the value never crosses a silhouette (a unit's edge keeps its own)
float occlusionAt(float viewDepth) {
    vec2 viewSize = vec2(viewWidth, viewHeight);
    vec2 h = gl_FragCoord.xy * AO_SCALE - 0.5;
    vec2 base = floor(h) + 0.5;
    vec2 f = fract(h);
    float tolerance = 0.25 + 0.02 * viewDepth;
    float sum = 0.0, weights = 0.0;
    float nearest = 1.0, nearestGap = 1e9;
    for (int i = 0; i < 4; i++) {
        vec2 o = vec2(float(i - 2 * (i / 2)), float(i / 2));
        vec2 s = texture2D(colortex3, aoCellUv(base + o, viewSize)).rg;
        float gap = abs(s.y - viewDepth);
        vec2 b = mix(1.0 - f, f, o);
        float w = b.x * b.y * max(1.0 - gap / tolerance, 0.0);
        sum += s.x * w;
        weights += w;
        if (gap < nearestGap) {
            nearestGap = gap;
            nearest = s.x;
        }
    }
    return weights > 1e-3 ? sum / weights : nearest;
}

/* DRAWBUFFERS:04 */
void main() {
    vec3 lit = texture2D(colortex0, texcoord).rgb;
    float depth = texture2D(depthtex0, texcoord).r;
#if AO == 1
    if (depth < 1.0) {
        float skyShare = texture2D(colortex1, texcoord).r;
        // depthtex1: the depth without water, the one the occlusion was found at
        if (skyShare > 0.0)
            lit *= 1.0 - skyShare * (1.0 - occlusionAt(-viewPosAt(texcoord, texture2D(depthtex1, texcoord).r).z));
    }
#endif
#if BLOOM == 1
    float bright = dot(lit, LUMA);
    gl_FragData[1] = vec4(lit * (max(bright - BLOOM_THRESHOLD, 0.0) / max(bright, 1e-4)), 1.0);
#else
    gl_FragData[1] = vec4(0.0, 0.0, 0.0, 1.0);
#endif
    vec3 c = toDisplay(toneMap(lit));
    bool ortho = orthoView();
    if (depth >= 1.0) {
        // nothing drawn: in the RTS view that is past the map's edge, where the mod sets its own edge tone as the fog
        // colour (OrthoviewClientEvents.onFogColor) - shown as is, as without a pack
        if (ortho)
            c = fogColor;
    } else if (isEyeInWater == 0) {
        vec3 pos = viewPosAt(texcoord, depth);
        float dist = ortho ? -pos.z : length(pos);
        float start = ortho ? rtsHazeStart() : FP_HAZE_START;
        float scale = ortho ? RTS_HAZE_SCALE : FP_HAZE_SCALE;
        float h = min(HAZE_MAX * HAZE_STRENGTH, HAZE_MAX_LIMIT) * (1.0 - exp(-max(dist - start, 0.0) / scale));
        h *= mix(0.4, 1.0, smoothstep(0.05, 0.4, dot(c, LUMA)));
        // toward the sky's colour lightened toward the horizon's: in the RTS view the fog colour is the map edge's tone,
        // not air, and the vanilla horizon colour WorldLookPass uses is not handed to packs (eyes measures the gap)
        vec3 air = ortho ? mix(skyColor, vec3(1.0), 0.25) : fogColor;
        c = mix(c, vec3(dot(c, LUMA)), h * 0.5);
        c *= mix(vec3(1.0), DISTANCE_COOL, h);
        c = mix(c, air, h);
        if (!ortho)                    // vanilla's fog at the render distance's edge
            c = mix(c, fogColor, smoothstep(far * 0.8, far, length(pos)));
    }
    gl_FragData[0] = vec4(c, 1.0);
}
