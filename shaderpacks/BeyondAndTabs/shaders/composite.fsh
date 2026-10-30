#version 120
// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// The lit world (linear light, colortex0) onto the screen: the tone curve, then the air. The distance haze takes the
// mod's own world look's distance rule (postfx/WorldLookPass, bat_postfx.fsh), aiming for the same air: in the RTS
// view distance is measured along the view from the ground at the screen's bottom edge (an ortho image has no eye),
// one e-fold per 56 blocks, never covering more than 30% (Enchanted), so far overlays stay legible. A perspective view
// gets aerial perspective from the eye instead (AERIAL_*, lib/settings.glsl). Dark ground keeps less of either (the
// fog of war's seen / unseen read is gameplay).
// Before the tone curve, slice 3's ambient occlusion (lib/ao.glsl) takes its share of each pixel's light: the sky's
// fill, as the lit draws recorded it in colortex1 (0 for overlays, water and everything drawn as is).
// Also before the tone curve, slice 3's bloom source (colortex4): only light past BLOOM_THRESHOLD in linear light,
// which lit ground never reaches (noon ground sits at its albedo, under 0.85) - lanterns and other emissive texels, glows,
// the sun's glint on a metal. final.fsh spreads it.
// The order (the stack of slices 3 and 4): the occlusion first (it is found from the opaque depth, the bed under any
// water), then the water's caustics and screen-space reflections (slice 4, colortex5), then the bloom source from what
// all of that leaves, then the tone curve and the air.

#include "/lib/settings.glsl"
#include "/lib/color.glsl"
#include "/lib/ao.glsl"

/*
const int colortex0Format = RGBA16F;
const int colortex1Format = RGBA8;
const vec4 colortex1ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const int colortex4Format = RGBA16F;
const int colortex5Format = RGBA8;
const vec4 colortex5ClearColor = vec4(0.0, 0.5, 0.0, 0.0);
*/
const bool colortex4Clear = false;     // this pass writes every pixel of it

uniform sampler2D colortex0;
uniform sampler2D colortex1;           // the sky's share of each pixel's light (the lit draws), for the occlusion
uniform sampler2D colortex3;           // the blurred half-resolution occlusion (deferred1.fsh)
uniform sampler2D colortex5;           // the water layer's data (gbuffers_water, lib/water.glsl): r water, g caustics,
                                       // b the reflected sky's share
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
uniform mat4 gbufferModelView;
uniform mat4 gbufferModelViewInverse;
uniform vec3 cameraPosition;
uniform float frameTimeCounter;

varying vec2 texcoord;

#include "/lib/light.glsl"
#include "/lib/sky.glsl"
#include "/lib/pbr.glsl"
#include "/lib/water.glsl"

const float HAZE_MAX = 0.30;
const float HAZE_MAX_LIMIT = 0.85;
const float RTS_HAZE_SCALE = 56.0;
const vec3 DISTANCE_COOL = vec3(0.93, 0.99, 1.07);   // scattering drifts distant colour cool before the haze veils it
// linear luma past which light blooms. The light table puts sunlit open ground at LIGHT_OPEN_GROUND x its albedo (lib/
// settings.glsl), so even white quartz or snow at noon stays under it: a plain noon lawn adds exactly nothing (design:
// mean change under 1/255). It moved with the light pass's brighter table (1.0 -> 1.3), or white blocks would bloom
const float BLOOM_THRESHOLD = LIGHT_OPEN_GROUND;

bool orthoView() {
    return gbufferProjection[2][3] == 0.0 && gbufferProjection[3][3] == 1.0;
}

vec3 viewPosAt(vec2 uv, float depth) {
    vec4 v = gbufferProjectionInverse * vec4(vec3(uv, depth) * 2.0 - 1.0, 1.0);
    return v.xyz / v.w;
}

// ---- screen-space reflections on water ----
// The water pass reflects the light table's sky (it cannot read the scene it is drawn into). Here, where the water's
// Fresnel is strong enough to show, the reflected ray is marched through the depth buffer; where it meets something
// drawn on screen (a bank, a tree, a wall) that colour replaces the sky in the reflection, faded toward the screen's
// edge and the march's far end so a reflection never ends in a hard cut. A miss keeps the sky.
const int SSR_STEPS = 16;
const float SSR_FIRST_STEP = 0.35;     // blocks; each step 1.32x the last: ~130 blocks in 16 steps
const float SSR_GROWTH = 1.32;
const int SSR_REFINE = 4;              // halvings of the step that crossed the surface
const float SSR_MIN_FRESNEL = 0.04;    // below this the mirror is too faint to see: no march (the RTS view, ~2%)

vec2 ssrProject(vec3 p) {
    vec4 c = gbufferProjection * vec4(p, 1.0);
    return c.xy / c.w * 0.5 + 0.5;
}

// the scene's colour (linear) where the ray from origin along dir first passes behind what is drawn, and how far to
// trust it (a; 0 on a miss)
vec4 ssrMarch(vec3 origin, vec3 dir) {
    float stepLen = SSR_FIRST_STEP;
    vec3 prev = origin;
    for (int i = 0; i < SSR_STEPS; i++) {
        vec3 p = prev + dir * stepLen;
        vec2 uv = ssrProject(p);
        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0)
            break;
        float d = texture2D(depthtex0, uv).r;
        float behind = d < 1.0 ? viewPosAt(uv, d).z - p.z : -1.0;
        if (behind > 0.0 && behind < stepLen * 2.0 + 0.5) {
            vec3 a = prev;
            vec3 b = p;
            for (int j = 0; j < SSR_REFINE; j++) {
                vec3 m = (a + b) * 0.5;
                vec2 muv = ssrProject(m);
                if (viewPosAt(muv, texture2D(depthtex0, muv).r).z > m.z)
                    b = m;
                else
                    a = m;
            }
            vec2 hit = ssrProject(b);
            vec2 edge = min(hit, 1.0 - hit);
            float reach = float(i) / float(SSR_STEPS);
            return vec4(texture2D(colortex0, hit).rgb, smoothstep(0.0, 0.08, min(edge.x, edge.y)) * (1.0 - reach * reach));
        }
        prev = p;
        stepLen *= SSR_GROWTH;
    }
    return vec4(0.0);
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
// a short-radius occlusion at full resolution (light pass 9: the critic wanted 2-3 px of darkening at every block foot
// and between adjacent blocks, floor 0.75 of the lawn beside it; the half-resolution AO's 1.5-block radius cannot draw
// a crease that narrow). The surface's plane from its neighbours one pixel away, 8 taps CONTACT_RADIUS blocks round
// it; a tap the depth buffer shows rising above the plane occludes by the sine of its elevation. Taken off all the
// light (a crease that tight shades the sun's light too), never below CONTACT_FLOOR
float contactOcclusion(vec2 uv, float depth) {
    if (depth >= 1.0)
        return 1.0;
    vec2 px = 1.0 / vec2(viewWidth, viewHeight);
    vec3 p = viewPosAt(uv, depth);
    vec3 pr = viewPosAt(uv + vec2(px.x, 0.0), texture2D(depthtex1, uv + vec2(px.x, 0.0)).r) - p;
    vec3 pl = p - viewPosAt(uv - vec2(px.x, 0.0), texture2D(depthtex1, uv - vec2(px.x, 0.0)).r);
    vec3 pu = viewPosAt(uv + vec2(0.0, px.y), texture2D(depthtex1, uv + vec2(0.0, px.y)).r) - p;
    vec3 pd = p - viewPosAt(uv - vec2(0.0, px.y), texture2D(depthtex1, uv - vec2(0.0, px.y)).r);
    vec3 n = normalize(cross(abs(pr.z) < abs(pl.z) ? pr : pl, abs(pu.z) < abs(pd.z) ? pu : pd));
    bool ortho = orthoView();
    if (dot(n, ortho ? vec3(0.0, 0.0, 1.0) : -p) < 0.0)
        n = -n;
    vec2 uvPerBlock = 0.5 * vec2(gbufferProjection[0][0], gbufferProjection[1][1]);
    if (!ortho)
        uvPerBlock /= max(-p.z, 0.05);
    float turn = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    float occ = 0.0;
    if (texture2D(colortex1, uv).b > 0.5)
        return 1.0;                    // a plant: no crease on it
    for (int i = 0; i < 9; i++) {
        float a = (float(i) + turn) * 2.39996323;
        // three radii (light pass 10): the crease fades over its width instead of ending in a line
        float r = CONTACT_RADIUS * (float(i / 3) + 1.0) / 3.0;
        vec2 suv = uv + vec2(cos(a), sin(a)) * (r * uvPerBlock);
        float sd = texture2D(depthtex1, suv).r;
        // the sky, and plants (they ringed every tuft with a crease, light pass 10), occlude nothing here
        if (sd >= 1.0 || texture2D(colortex1, suv).b > 0.5)
            continue;
        vec3 v = viewPosAt(suv, sd) - p;
        float h = dot(v, n);
        occ += max(h * inversesqrt(dot(v, v) + 1e-6) - 0.15, 0.0) * (1.0 - clamp((h - 3.0 * r) / r, 0.0, 1.0));
    }
    return max(1.0 - CONTACT_GAIN * occ / 9.0, CONTACT_FLOOR);
}

void main() {
    vec3 lin = texture2D(colortex0, texcoord).rgb;
    float depth = texture2D(depthtex0, texcoord).r;
#if AO == 1
    if (depth < 1.0) {
        float skyShare = texture2D(colortex1, texcoord).r;
        // depthtex1: the depth without water, the one the occlusion was found at
        if (skyShare > 0.0) {
            // the sky's share fully, CONTACT_SUN_SHARE of the rest
            lin *= 1.0 - (skyShare + CONTACT_SUN_SHARE * (1.0 - skyShare))
                * (1.0 - occlusionAt(-viewPosAt(texcoord, texture2D(depthtex1, texcoord).r).z));
            // and the short contact term at full resolution, on all the light (light pass 9)
            lin *= contactOcclusion(texcoord, texture2D(depthtex1, texcoord).r);
        }
    }
#endif
    bool ortho = orthoView();
#if WATER_REALISM == 1 && (CAUSTICS == 1 || SSR == 1)
    vec4 water = texture2D(colortex5, texcoord);
#endif
#if WATER_REALISM == 1 && CAUSTICS == 1
    // the caustics the water pass worked out for the bed under it: a share more (or less) of the light seen through
    if (water.r > 0.5)
        lin *= max(1.0 + (water.g - 0.5) / CAUSTIC_ENCODE, 0.0);
#endif
#if WATER_REALISM == 1 && SSR == 1
    if (water.r > 0.5 && water.b > 0.0 && depth < 1.0 && isEyeInWater == 0) {
        // the same ripple normal and Fresnel the water pass used, from the surface's position
        vec3 pos = viewPosAt(texcoord, depth);
        vec3 player = (gbufferModelViewInverse * vec4(pos, 1.0)).xyz;
        vec3 n = mat3(gbufferModelView) * waterNormal(player.xz + cameraPosition.xz, frameTimeCounter);
        vec3 v = ortho ? vec3(0.0, 0.0, 1.0) : normalize(-pos);
        float fres = waterFresnel(dot(n, v));
        if (fres > SSR_MIN_FRESNEL) {
            vec3 r = reflect(-v, n);
            vec4 hit = ssrMarch(pos, r);
            if (hit.a > 0.0) {
                vec3 sunColour, skyColour;
                lightColours(sunColour, skyColour);
                // swap the sky the water pass added (Fresnel x its share) for the scene the ray met
                vec3 sky = skyInDirection(r, skyColour, sunColour);
                lin = max(lin + (hit.rgb - sky) * (fres * water.b * hit.a), vec3(0.0));
            }
        }
    }
#endif
    // bloom's source from the finished linear light: what the water's reflections and caustics brought past the
    // threshold blooms as well (a glint mirrored in a lake), lit ground still never does
#if BLOOM == 1
    float bright = dot(lin, LUMA);
    gl_FragData[1] = vec4(lin * (max(bright - BLOOM_THRESHOLD, 0.0) / max(bright, 1e-4)), 1.0);
#else
    gl_FragData[1] = vec4(0.0, 0.0, 0.0, 1.0);
#endif
    vec3 c = toDisplay(toneMap(lin));
    if (depth >= 1.0) {
        // nothing drawn: in the RTS view that is past the map's edge, where the mod sets its own edge tone as the fog
        // colour (OrthoviewClientEvents.onFogColor) - shown as is, as without a pack
        if (ortho)
            c = fogColor;
#if SKY_PROBE == 0
        else
            // the sky (light pass 6): painted here over every pixel nothing was drawn at. Under Oculus the 3/4 views'
            // "sky" was mostly the loader's clear colour (vanilla's fog colour) - the sky programs' dome only covers
            // the view above ~2 degrees and these cameras look 30 degrees down - so pass 4-5's gradient, the dusk
            // colours and the sun disc never showed, and vanilla's sun square stayed. Vanilla's colours by night
            // light pass 7: only where gbuffers_skybasic did not draw (it runs under Oculus - the SKY_PROBE run - and
            // marks its pixels in colortex1's green); the loader's clear colour elsewhere
            if (texture2D(colortex1, texcoord).g < 0.5)
                c = mix(c, toDisplay(toneMap(skyWithLights(normalize(viewPosAt(texcoord, 1.0))))), skyOwn());
#endif
    } else if (isEyeInWater == 0) {
        vec3 pos = viewPosAt(texcoord, depth);
        float dist = ortho ? -pos.z : length(pos);
        // a perspective view (first person, the 3/4 camera) gets aerial perspective: sky-tinted air from
        // AERIAL_START blocks, ~27% at 150 (lib/settings.glsl, the light pass; it had 12 blocks and 180 per e-fold,
        // ~16% at 150, which read as none). The orthographic RTS camera keeps its own haze, from the screen's bottom edge
        float start = ortho ? rtsHazeStart() : AERIAL_START;
        float scale = ortho ? RTS_HAZE_SCALE : AERIAL_SCALE;
        float hazeMax = ortho ? HAZE_MAX : AERIAL_MAX;
        float h = min(hazeMax * HAZE_STRENGTH, HAZE_MAX_LIMIT) * (1.0 - exp(-max(dist - start, 0.0) / scale));
        h *= mix(0.4, 1.0, smoothstep(0.05, 0.4, dot(c, LUMA)));
        // toward the sky's colour lightened toward the horizon's: in the RTS view the fog colour is the map edge's tone,
        // not air, and the vanilla horizon colour WorldLookPass uses is not handed to packs (eyes measures the gap)
        // A perspective view's air by day is the sky gradient's horizon (light pass 2, program/sky.fsh), so far ground
        // fades into the sky drawn behind it
        vec3 air = ortho ? mix(skyColor, vec3(1.0), 0.25) : mix(fogColor, skyAir(), skyOwn());
        // the air takes the colour it veils: half in the RTS haze, all of it in aerial perspective (AERIAL_DESAT)
        c = mix(c, vec3(dot(c, LUMA)), min(h * (ortho ? 0.5 : AERIAL_DESAT), 1.0));
        c *= mix(vec3(1.0), DISTANCE_COOL, h);
        c = mix(c, air, h);
        if (!ortho)                    // the render distance's edge fades into the same air (vanilla's fog there)
            c = mix(c, air, smoothstep(far * 0.8, far, length(pos)));
    }
    gl_FragData[0] = vec4(c, 1.0);
}
