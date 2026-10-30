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

#include "/lib/settings.glsl"
#include "/lib/color.glsl"

/*
const int colortex0Format = RGBA16F;
const int colortex2Format = RGBA8;
const vec4 colortex2ClearColor = vec4(0.0, 0.5, 0.0, 0.0);
*/

uniform sampler2D colortex0;
uniform sampler2D colortex2;           // the water layer's data (gbuffers_water, lib/water.glsl): r water, g caustics,
                                       // b the reflected sky's share
uniform sampler2D depthtex0;
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
#include "/lib/pbr.glsl"
#include "/lib/water.glsl"

const float HAZE_MAX = 0.30;
const float HAZE_MAX_LIMIT = 0.85;
const float FP_HAZE_START = 12.0;
const float FP_HAZE_SCALE = 180.0;
const float RTS_HAZE_SCALE = 56.0;
const vec3 DISTANCE_COOL = vec3(0.93, 0.99, 1.07);   // scattering drifts distant colour cool before the haze veils it

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

/* DRAWBUFFERS:0 */
void main() {
    vec3 lin = texture2D(colortex0, texcoord).rgb;
    float depth = texture2D(depthtex0, texcoord).r;
    bool ortho = orthoView();
#if WATER_REALISM == 1 && (CAUSTICS == 1 || SSR == 1)
    vec4 water = texture2D(colortex2, texcoord);
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
    vec3 c = toDisplay(toneMap(lin));
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
