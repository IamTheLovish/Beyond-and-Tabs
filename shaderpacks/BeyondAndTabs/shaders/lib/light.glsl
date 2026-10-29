// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// The light of the moment: sun (or moon) colour, sky ambient and torch light, from one table keyed by the sun's
// height. The lobby's four times (match/TimeOfDay.java) land on its rows: Morning 5000 and Noon 6000 on the high sun
// (with the 30 degree tilt both are 55-60 degrees up), Dusk 11800 on the low sun (about 3 degrees up), Night 18000 on
// the moon. A day cycle slides between the rows.
// Lighting is linear: albedo is decoded from sRGB, lit, then tone-mapped and encoded again (composite.fsh). The table is
// calibrated so open ground at noon comes out at its albedo - the brightness players know - while the shade beside a
// building keeps about a third of the light (shadow / lit luma about 0.55 on screen: visible, never black).

uniform vec3 sunPosition;
uniform vec3 shadowLightPosition;
uniform vec3 upPosition;
uniform float rainStrength;

// high sun: warm light, near-neutral sky fill (Oct 28: a blue fill tinted the whole lawn - #326's lawn fix)
const vec3 SUN_HIGH = vec3(1.00, 0.94, 0.84) * 0.80;
const vec3 SKY_HIGH = vec3(0.92, 0.96, 1.04) * 0.30;
// low sun: golden light raking across, a dim warm-violet fill
const vec3 SUN_LOW = vec3(1.00, 0.62, 0.34) * 1.50;
const vec3 SKY_LOW = vec3(0.74, 0.70, 0.80) * 0.14;
// moonlight: cool and dim; lanterns and lit buildings carry the night (TimeOfDay: NIGHT)
const vec3 MOON = vec3(0.50, 0.62, 1.00) * 0.22;
const vec3 SKY_NIGHT = vec3(0.42, 0.50, 0.75) * 0.08;
// torch, lantern and fire light (vanilla's block light)
const vec3 BLOCK_LIGHT = vec3(1.00, 0.70, 0.40) * 1.60;
// so the darkest cave is never pure black
const vec3 MIN_LIGHT = vec3(0.012);

// sine of the sun's height above the horizon
float sunHeight() {
    return dot(normalize(sunPosition), normalize(upPosition));
}

// the colours for this frame: the shadow light (sun by day, moon by night) and the sky's fill
void lightColours(out vec3 sunColour, out vec3 skyColour) {
    float h = sunHeight();
    float high = smoothstep(0.03, 0.45, h);   // low sun -> high sun
    float day = smoothstep(-0.08, 0.02, h);   // night -> low sun (the shadow light changes over here)
    sunColour = mix(MOON, mix(SUN_LOW, SUN_HIGH, high), day);
    skyColour = mix(SKY_NIGHT, mix(SKY_LOW, SKY_HIGH, high), day);
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
    light += skyColour * ((0.75 + 0.25 * dot(n, up)) * sky * sky * ao);
    light += BLOCK_LIGHT * (pow(levels.x, 3.0) * mix(1.0, 0.35, daylight) * mix(0.6, 1.0, ao));
    return light + MIN_LIGHT;
}
