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
// low sun: golden light raking across, a dim warm-violet fill
const vec3 SUN_LOW = vec3(1.00, 0.62, 0.34) * 1.50;
const vec3 SKY_LOW = vec3(0.74, 0.70, 0.80) * 0.17;
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
const float SKY_FILL_FLOOR = 0.35;

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
    // the sky's fill has no direction: it may glow on through twilight
    skyColour = mix(SKY_NIGHT, mix(SKY_LOW, SKY_HIGH, high), smoothstep(-0.08, 0.02, h));
    sunColour *= 1.0 - 0.75 * rainStrength;
    skyColour *= 1.0 - 0.30 * rainStrength;
}

// lightmap coordinates (block light, sky light) to 0..1: the loader hands them over as texel centres of the 16x16 map
vec2 lightLevels(vec2 lm) {
    return clamp((lm - 1.0 / 32.0) * (16.0 / 15.0), 0.0, 1.0);
}

// Lambert, eased toward a square root: a low sun still rakes the open ground (Dusk's long shadows read as shade, not
// as nothing), and the terminator on round shapes is softer
float sunFacing(vec3 n, vec3 l) {
    float ndl = max(dot(n, l), 0.0);
    return mix(ndl, sqrt(ndl), 0.5);
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
    return (skyColour * (skySeen * mix(SKY_FILL_FLOOR, 1.0, levels.y))
            + ground * ((1.0 - skySeen) * levels.y)) * (ao * texAo);
}

// the light falling on a surface with view-space normal n: shadow (0..1) from lib/shadow.glsl, levels from lightLevels,
// ao the vertex ambient occlusion (1 = open), texAo the material's own (the _n map's blue, slice 2; 1 = open): it
// darkens the fill fully and the sun at half strength - a crevice still catches some of a direct sun
vec3 surfaceLight(vec3 n, float facing, float shadow, vec2 levels, float ao, float texAo, vec3 sunColour,
                  vec3 skyColour) {
    float daylight = smoothstep(-0.05, 0.2, sunHeight());
    vec3 light = sunArriving(shadow, levels, sunColour) * (facing * mix(1.0, texAo, 0.5));
    light += skyFill(n, levels, ao, texAo, sunColour, skyColour);
    light += BLOCK_LIGHT * (pow(levels.x, 3.0) * mix(1.0, 0.35, daylight) * mix(0.6, 1.0, ao));
    return light + MIN_LIGHT;
}

vec3 surfaceLight(vec3 n, float facing, float shadow, vec2 levels, float ao, vec3 sunColour, vec3 skyColour) {
    return surfaceLight(n, facing, shadow, levels, ao, 1.0, sunColour, skyColour);
}
