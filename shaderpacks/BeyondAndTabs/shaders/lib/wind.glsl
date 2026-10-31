// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// The wind the plants and leaves move in (lovish, Oct 30: "Wind sway + cloud shadows"). It is the mod's own wind field
// (environment/wind/WindField.java, design/branch_wind.md), the one the tree limbs already sway in: the same heading
// wandering about east-south-east, the same two gust waves travelling along it (a 41-block swell at 6.5 blocks/s, a
// 17-block ripple at 10), the same small flutter on top. From the RTS camera that reads as gusts rolling across the
// lawn, not as every tuft jittering on its own. The shader's clock is frameTimeCounter, not the game time the limbs
// use, so the two are the same field at a different phase - the look and direction agree, not the exact gust.
//
// Every offset is a smooth function of the vertex's world position, so two blocks sharing a vertex move it the same
// way (no cracks between leaves), and the shadow pass (shadow.vsh) calls the same function on the same position, so a
// moving leaf's shadow moves with it. Amplitudes are in blocks.
// Needs lib/settings.glsl before it.

#ifndef WIND_GLSL
#define WIND_GLSL

uniform float frameTimeCounter;        // seconds; the loader wraps it after an hour (one jump in the gusts then)
uniform vec3 cameraPosition;

const float WIND_TWO_PI = 6.2831853;
const float WIND_HEADING = 0.6;        // radians, x toward +x, z toward +z: WindField.BASE_HEADING
const float WIND_TURN = 0.35;          // the heading wanders this much either way...
const float WIND_TURN_PERIOD = 140.0;  // ...over this many seconds
const float WIND_L1 = 41.0;            // the swell: wavelength (blocks) and speed (blocks/s)
const float WIND_V1 = 6.5;
const float WIND_L2 = 17.0;            // the ripple
const float WIND_V2 = 10.0;
const float WIND_FLUTTER_HZ = 1.6;

// the top of a grass tuft at a full gust moves ~0.13 block (a few pixels at the default zoom: alive, never smeared);
// a leaf block shimmers ~0.035 at most - the tree limbs' tips (WindField) move about 0.09 at the average wind
const float PLANT_SWAY = 0.10;
const float LEAF_SWAY = 0.030;

float windHeading(float t) {
    return WIND_HEADING + WIND_TURN * sin(WIND_TWO_PI * t / WIND_TURN_PERIOD);
}

// -0.4 .. 1: biased downwind (mean 0.3), so a plant leans with the wind and swings back on the lulls
float windGust(vec2 xz, float t, vec2 dir) {
    float d = dot(xz, dir);
    float swell = sin(WIND_TWO_PI * (d - t * WIND_V1) / WIND_L1);
    float ripple = sin(WIND_TWO_PI * (d - t * WIND_V2) / WIND_L2 + 1.7);
    return 0.3 + 0.7 * (0.6 * swell + 0.4 * ripple);
}

// the offset (blocks) of a vertex at world position wp. plant: how much of a plant's sway this vertex takes (0 at a
// stem's foot, 1 at the top of a one-block plant, 2 at the top of a two-block one); leaf: 1 for a leaf block
vec3 windOffset(vec3 wp, float plant, float leaf) {
    float t = frameTimeCounter;
    float heading = windHeading(t);
    vec2 dir = vec2(cos(heading), sin(heading));
    float g = windGust(wp.xz, t, dir);
    // the flutter's phase changes slowly across space (a few radians a block): neighbours are out of step, the two ends
    // of one plant almost in step, so a tuft sways rather than twists
    float phase = dot(wp, vec3(1.9, 1.3, 2.3));
    float flutter = sin(WIND_TWO_PI * WIND_FLUTTER_HZ * t + phase);
    float across = sin(WIND_TWO_PI * WIND_FLUTTER_HZ * 0.73 * t + phase * 1.31);
    vec2 side = vec2(-dir.y, dir.x);

    vec3 off = vec3(0.0);
    // plants: lean along the wind with the gust, a little flutter both ways; a pushed stem dips a touch
    float h = PLANT_SWAY * plant * (g + 0.15 * flutter);
    off.xz += dir * h + side * (PLANT_SWAY * plant * 0.08 * across);
    off.y -= 0.15 * abs(h);
    // leaves: a small shimmer about their rest, stronger as a gust passes
    float s = LEAF_SWAY * leaf * (0.35 + 0.65 * clamp(g, 0.0, 1.0));
    off += vec3(dir * flutter + side * (0.5 * across), 0.4 * across * flutter) * s;
    return off * WIND_STRENGTH;
}

#endif
