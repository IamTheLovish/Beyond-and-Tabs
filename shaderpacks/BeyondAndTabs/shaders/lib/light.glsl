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
// aimed at open ground at noon coming out at its albedo - the brightness players know - and the shade beside a
// building keeping about 40% of the light (shadow / lit luma ~0.65 on screen): a unit standing in a building's shadow
// must stay easy to read from the RTS camera (#386 r5). tools/shader_judge.py measures it on the eyes stills against
// two-sided bands ("fill" and "floor").

uniform vec3 sunPosition;
uniform vec3 shadowLightPosition;
uniform vec3 upPosition;
uniform float rainStrength;

// high sun: warm light, near-neutral sky fill (Oct 28: a blue fill tinted the whole lawn - #326's lawn fix). Sun 0.68 at
// ~60 degrees up plus fill 0.41 keeps open ground at its albedo; the fill alone is 40% of that
const vec3 SUN_HIGH = vec3(1.00, 0.94, 0.84) * 0.68;
const vec3 SKY_HIGH = vec3(0.92, 0.96, 1.04) * 0.41;
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

// the light falling on a surface with view-space normal n: shadow (0..1) from lib/shadow.glsl, levels from lightLevels,
// ao the vertex ambient occlusion (1 = open)
vec3 surfaceLight(vec3 n, float facing, float shadow, vec2 levels, float ao, vec3 sunColour, vec3 skyColour) {
    vec3 up = normalize(upPosition);
    float sky = levels.y;
    float daylight = smoothstep(-0.05, 0.2, sunHeight());
    vec3 light = sunColour * (facing * shadow * smoothstep(0.05, 0.45, sky));
    light += skyColour * ((0.75 + 0.25 * dot(n, up)) * mix(SKY_FILL_FLOOR, 1.0, sky) * ao);
    light += BLOCK_LIGHT * (pow(levels.x, 3.0) * mix(1.0, 0.35, daylight) * mix(0.6, 1.0, ao));
    return light + MIN_LIGHT;
}
