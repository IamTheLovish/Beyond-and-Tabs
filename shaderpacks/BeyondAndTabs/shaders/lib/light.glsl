// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// The light of the moment: sun (or moon) colour, sky ambient and torch light, from one table keyed by the sun's
// height. The lobby's four times (match/TimeOfDay.java) with the 30 degree tilt: Morning 5000 (~58 degrees up) and
// Noon 6000 (~60) sit on the high-sun row; Dusk 11800 (~13 degrees up) is mostly the golden low-sun row; Night 18000 is
// the moon (~60 up). A day cycle slides between them.
// Which body lights the scene follows the loader: shadowLightPosition is the sun while it is above the horizon and the
// moon from the moment it sets. The colour follows the same switch - the sun's colour is never put on the moon's
// direction (that flipped golden light from the west walls to the east ones at sunset) - and the directional light
// fades to nothing as either body nears the horizon, so the hand-over itself shows no jump.
// Lighting is linear: albedo is decoded from sRGB, lit, then tone-mapped and encoded again (composite.fsh). The table is
// aimed at open ground at noon coming out at LIGHT_OPEN_GROUND x its albedo (lib/settings.glsl, the light pass: 1.3)
// and the shade beside a building keeping SHADOW_FILL_SHARE of the light (0.62; ~0.78 on screen): a unit standing in a
// building's shadow must stay easy to read from the RTS camera (#386 r5). tools/shader_judge.py measures it on the
// eyes stills against two-sided bands ("fill" and "floor").

uniform vec3 sunPosition;
uniform vec3 shadowLightPosition;
uniform vec3 upPosition;
uniform float rainStrength;

// high sun: warm light, a lightly cool sky fill (Oct 28: a strongly blue fill tinted the whole lawn - #326's lawn fix;
// the light pass keeps the tint mild for the same reason, as the fill is now most of the light). Derived from the
// targets in lib/settings.glsl: open ground at LIGHT_OPEN_GROUND x its albedo under the noon sun (~60 degrees up with
// the tilt), the sky giving SHADOW_FILL_SHARE of it. The tints' lumas and the noon facing are spelled out as numbers
// (tools/test_shader_pack.py checks them) so the constants stay plain arithmetic
const vec3 SUN_TINT_HIGH = vec3(1.00, 0.92, 0.76);
const float SUN_TINT_HIGH_LUMA = 0.9255;
const vec3 SKY_TINT_HIGH = vec3(0.92, 0.97, 1.06);
const float SKY_TINT_HIGH_LUMA = 0.9659;
const float NOON_SUN_FACING = 0.8983;          // sunFacing() of open ground with the sun 60 degrees up
const vec3 SUN_HIGH = SUN_TINT_HIGH
    * ((1.0 - SHADOW_FILL_SHARE) * LIGHT_OPEN_GROUND / (SUN_TINT_HIGH_LUMA * NOON_SUN_FACING));
const vec3 SKY_HIGH = SKY_TINT_HIGH * (SHADOW_FILL_SHARE * LIGHT_OPEN_GROUND / SKY_TINT_HIGH_LUMA);
// low sun: golden light raking across, a dim warm-violet fill. Light pass 3 (critic 6.5/10: t11800 "a dim room" - lit
// lawn 0.22-0.24, shadow 0.15, sun / shadow 1.5:1, the lawn olive at hue 75-80): the sun is brighter and more orange
// ((1.00, 0.62, 0.34) x 1.50 before; luma 2.34 now) and rakes the ground harder (LOW_SUN_RAKE), the fill unchanged.
// By the model at t11800 (sun ~13 degrees up): lit lawn ~0.38 at hue ~68, a cast shadow ~0.13, ratio ~2.9:1
// Light pass 4: x 3.90 -> x 3.70 (luma 2.22): the grass now takes the whole rake (LOW_SUN_RAKE 1) and everything else
// the plain facing, so the lawn holds ~0.38 while block tops come down (a white top ~0.89 after the low shoulder)
const vec3 SUN_LOW = vec3(1.00, 0.52, 0.22) * 3.70;
// light pass 9 (critic: the low sun's shadows ink-black, the benchmark's hazy gold): warmer and a little more of it
// ((0.74, 0.70, 0.80) x 0.17 before). Model at t11800: a cast shadow ~0.13 -> ~0.16, the ratio ~2.9 -> ~2.4:1
// Light pass 10: olive-gold and more of it ((0.82, 0.74, 0.70) x 0.24 before): t11800's shadow / lit ~0.6 on screen
// Light pass 11: t11800's shadows measured (34, 50, 23), target ~(45, 55, 30): redder and ~1.4x of it
// light pass 12: redder (1.00, 0.80, 0.45) -> (1.00, 0.75, 0.40) and x0.50 -> x0.55, toward the olive ~(45, 55, 30)
const vec3 SKY_LOW = vec3(1.00, 0.75, 0.40) * 0.55;
// moonlight: cool and dim; lanterns and lit buildings carry the night (TimeOfDay: NIGHT)
const vec3 MOON = vec3(0.50, 0.62, 1.00) * 0.22;
const vec3 SKY_NIGHT = vec3(0.42, 0.50, 0.75) * 0.08;
// torch, lantern and fire light (vanilla's block light)
const vec3 BLOCK_LIGHT = vec3(1.00, 0.70, 0.40) * 1.60;
// so the darkest cave is never pure black
const vec3 MIN_LIGHT = vec3(0.012);
// the sky's fill under a sky light of 0 as a share of the open sky's: the fill falls off with the sky light linearly
// from 1 to this, never to nothing. The light engine can leave open ground with a stale low sky light (the eyes
// structure pads: freshly laid ground read 1/3 of its sky light), which the sun hides but a shadow showed as black
// Light pass 5: 0.35 -> 0.55. The pass-4 noon stills put the lawn's darkest shadow at luma ~0.11 (target 0.17-0.20),
// half of what the light table gives a cast shadow on open ground: consistent with the eyes pads' stale low sky light
// on freshly laid ground (above), which the floor is for
const float SKY_FILL_FLOOR = 0.55;

// sine of the sun's height above the horizon
float sunHeight() {
    return dot(normalize(sunPosition), normalize(upPosition));
}

// the colours for this frame: the shadow light (sun by day, moon by night) and the sky's fill
void lightColours(out vec3 sunColour, out vec3 skyColour) {
    float h = sunHeight();
    float high = smoothstep(0.10, 0.55, h);    // low sun -> high sun (Dusk 11800, h ~0.23, stays mostly golden)
    // the directional light: the sun's colour only while the sun is the light, the moon's after it sets; zero at the
    // horizon, where the loader swaps one body for the other
    sunColour = h > 0.0 ? mix(SUN_LOW, SUN_HIGH, high) : MOON;
    sunColour *= smoothstep(0.0, 0.05, abs(h));
    // the moon takes over slowly (light pass 8): just after sunset it barely lights - the dusk still (13000, the sun ~3
    // degrees under) read mid-afternoon in its light, lawn 0.18 against the benchmark's 0.07 - and is full by night
    if (h <= 0.0)
        sunColour *= smoothstep(MOON_FADE_LO, MOON_FADE_HI, -h);
    // and the sun's own strength follows its elevation down to nothing ~1 degree under the horizon (light pass 9: the
    // dusk still was lit like 3 pm, 0.39 with hard shadows - the sun was still the shadow light there): ~0.3 a few
    // degrees up, full from SUN_FADE_HI (t11800 is ~0.23)
    else
        sunColour *= smoothstep(SUN_FADE_LO, SUN_FADE_HI, h);
    // the sky's fill has no direction: it may glow on through twilight
    skyColour = mix(SKY_NIGHT, mix(SKY_LOW, SKY_HIGH, high), smoothstep(-0.08, 0.02, h));
    // twilight (light pass 11): the day's fill dims as the sun sinks under ~11 degrees; the night's own fill untouched
    skyColour *= mix(mix(SKY_TWILIGHT_DIM, 1.0, smoothstep(0.05, 0.20, h)), 1.0, 1.0 - smoothstep(-0.08, 0.02, h));
    sunColour *= 1.0 - 0.75 * rainStrength;
    skyColour *= 1.0 - 0.30 * rainStrength;
}

// lightmap coordinates (block light, sky light) to 0..1: the loader hands them over as texel centres of the 16x16 map
vec2 lightLevels(vec2 lm) {
    vec2 levels = clamp((lm - 1.0 / 32.0) * (16.0 / 15.0), 0.0, 1.0);
    // open sky from SKY_LIGHT_FULL up (light pass 8): a sky light of 14 against 15 across a chunk border of the eyes
    // pads' freshly laid ground drew a faint straight light step over the empty lawn
    levels.y = clamp(levels.y / SKY_LIGHT_FULL, 0.0, 1.0);
    return levels;
}

// Lambert, eased toward a square root: a low sun still rakes the open ground (Dusk's long shadows read as shade, not
// as nothing), and the terminator on round shapes is softer. The grass rakes harder as the sun sinks: sunFacingRaked
float sunFacing(vec3 n, vec3 l) {
    float ndl = max(dot(n, l), 0.0);
    // light pass 12: the easing is for faces looking up (and round shapes); a wall takes plain N.L, so a side the sun
    // grazes reads as grazed, not half lit
    return mix(ndl, sqrt(ndl), 0.5 * clamp(dot(n, normalize(upPosition)), 0.0, 1.0));
}

// 0..1: how low the sun is (the light table's low row; 0 by night)
float lowSun() {
    float h = sunHeight();
    return h > 0.0 ? 1.0 - smoothstep(0.10, 0.55, h) : 0.0;
}

// the grass's facing (light pass 4: biome-tinted terrain only, lit.fsh): eased further toward a square root as the sun
// sinks, by LOW_SUN_RAKE - warm light raking across the grass, while block tops keep the plain facing
float sunFacingRaked(vec3 n, vec3 l) {
    float ndl = max(dot(n, l), 0.0);
    return mix(ndl, sqrt(ndl), min(0.5 + 0.5 * LOW_SUN_RAKE * lowSun(), 1.0));
}

// the sun's light reaching a surface before its facing: the colour, the shadow, and the fade where the sky light says
// the surface is indoors. The specular highlight (lib/pbr.glsl) takes the same light, so a glint never shows in shade
vec3 sunArriving(float shadow, vec2 levels, vec3 sunColour) {
    return sunColour * (shadow * smoothstep(0.05, 0.45, levels.y));
}

// the fill: what reaches a surface from every direction but the sun's. A hemisphere: the face sees the sky by
// (1 + up) / 2 of its directions and the ground by the rest, and the ground returns GROUND_ALBEDO of the sun and sky
// falling on it (warm: it carries the sun's colour). A top gets the sky alone (a cast shadow on the lawn keeps
// SHADOW_FILL_SHARE of the sunlit lawn); a wall half sky, half bounce (the light pass's ~0.7 of a top on screen; this
// replaces slice 2's separate GROUND_BOUNCE); an overhang's underside the bounce alone. The sky part falls off with the
// sky light to SKY_FILL_FLOOR; the bounce with the sky light itself, so a cave stays dark. ao the vertex ambient
// occlusion, texAo the material's (the _n map's blue, slice 2), both 1 = open.
// skyFill is also the part slice 3's ambient occlusion takes (lit.fsh records its share, composite.fsh applies it)
vec3 skyFill(vec3 n, vec2 levels, float ao, float texAo, vec3 sunColour, vec3 skyColour) {
    vec3 up = normalize(upPosition);
    float skySeen = 0.5 + 0.5 * dot(n, up);
    vec3 ground = GROUND_ALBEDO * (sunColour * sunFacing(up, normalize(shadowLightPosition)) + skyColour);
    // the ground's bounce takes only BOUNCE_AO of the occlusion (light pass 7): it comes from the ground right at a
    // wall's foot, the occluded corner itself, so a shaded wall brightens toward its base instead of going near black
    return skyColour * (skySeen * mix(SKY_FILL_FLOOR, 1.0, levels.y) * ao * texAo)
           + ground * ((1.0 - skySeen) * levels.y * mix(1.0, ao * texAo, BOUNCE_AO));
}

// where no sun arrives (a cast shadow, a face turned from the light) the fill is all the light there is, and it is the
// blue sky's: SHADE_TINT (light pass 3, luma 1) cools it there, so a shadow is the lit ground's colour shifted toward
// blue, not the same hue darker. sunLit: the sun's share arriving, 0..1. Where the sun reaches, the fill keeps the
// light table's mild tint (a strongly blue fill tinted the whole lawn: #326)
// the ground's tint goes to the ground's materials only (light pass 8: a shaded stone walkway went cobalt like water):
// lit.fsh sets this per draw from the block id - 1 grass, dirt and the like (block.properties 10005), 0.5 plants, 0
// everything else, which takes the walls' tint whichever way it faces
float gShadeGround = 0.0;

vec3 shadeTint(float sunLit, float upness) {
    vec3 full = mix(SHADE_TINT_WALL, SHADE_TINT, clamp(upness, 0.0, 1.0) * gShadeGround);
    float strength = (1.0 + SHADE_TINT_LOW_BOOST * lowSun()) * (1.0 - SHADE_TINT_LOW_CUT * lowSun());
    vec3 tint = max(mix(vec3(1.0), full, strength), vec3(0.0));
    return mix(tint, vec3(1.0), clamp(sunLit, 0.0, 1.0));
}

// the fill's direction (FILL_DIRECTION, light pass 5): brighter on faces turned toward the sun's azimuth, by the
// horizontal part of the normal along the horizontal direction to the light; 1 for tops, bottoms and a sun overhead
float fillDirection(vec3 n) {
    vec3 up = normalize(upPosition);
    // and the walls' share of the fill (WALL_FILL, light pass 12): pass 11's 0.70 lean took both of quartz's sides into
    // the shoulder (0.85 / 0.69 against a 0.95 top); faces looking sideways or down get WALL_FILL of the fill
    float wall = mix(WALL_FILL, 1.0, clamp(dot(n, up), 0.0, 1.0));
    vec3 l = normalize(shadowLightPosition);
    vec3 lh = l - up * dot(l, up);
    if (dot(lh, lh) < 1e-6)
        return wall;
    vec3 nh = n - up * dot(n, up);
    return wall * (1.0 + FILL_DIRECTION * dot(nh, normalize(lh)));
}

// the light falling on a surface with view-space normal n: shadow (0..1) from lib/shadow.glsl, levels from lightLevels,
// ao the vertex ambient occlusion (1 = open), texAo the material's own (the _n map's blue, slice 2; 1 = open): it
// darkens the fill fully and the sun at half strength - a crevice still catches some of a direct sun
vec3 surfaceLight(vec3 n, float facing, float shadow, vec2 levels, float ao, float texAo, vec3 sunColour,
                  vec3 skyColour) {
    float daylight = smoothstep(-0.05, 0.2, sunHeight());
    vec3 light = sunArriving(shadow, levels, sunColour) * (facing * mix(1.0, texAo, 0.5));
    float direction = fillDirection(n);
    light += skyFill(n, levels, ao, texAo, sunColour, skyColour) * direction;
    // the sky's part of the fill, cooled where no sun arrives (shadeTint); the ground's bounce keeps its warmth
    float upness = dot(n, normalize(upPosition));
    float skySeen = 0.5 + 0.5 * upness;
    vec3 skyPart = skyColour * (skySeen * mix(SKY_FILL_FLOOR, 1.0, levels.y) * ao * texAo * direction);
    light += skyPart * (shadeTint(shadow * min(facing * 4.0, 1.0), upness) - 1.0);
    light += BLOCK_LIGHT * (pow(levels.x, 3.0) * mix(1.0, 0.35, daylight) * mix(0.6, 1.0, ao));
    return light + MIN_LIGHT;
}

vec3 surfaceLight(vec3 n, float facing, float shadow, vec2 levels, float ao, vec3 sunColour, vec3 skyColour) {
    return surfaceLight(n, facing, shadow, levels, ao, 1.0, sunColour, skyColour);
}
