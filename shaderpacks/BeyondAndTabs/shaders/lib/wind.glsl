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
// Needs lib/settings.glsl and lib/world.glsl (cameraPosition, frameTimeCounter) before it.

#ifndef WIND_GLSL
#define WIND_GLSL

const float WIND_TWO_PI = 6.2831853;
const float WIND_HEADING = 0.6;        // radians, x toward +x, z toward +z: WindField.BASE_HEADING
const float WIND_TURN = 0.35;          // the heading wanders this much either way...
const float WIND_TURN_PERIOD = 140.0;  // ...over this many seconds
const float WIND_L1 = 41.0;            // the swell: wavelength (blocks) and speed (blocks/s)
const float WIND_V1 = 6.5;
const float WIND_L2 = 17.0;            // the ripple
const float WIND_V2 = 10.0;
const float WIND_FLUTTER_HZ = 1.6;

// Fable critic, wind round 1 (Nov 5): at the RTS camera's ~30 px a block the sway read as every tuft jittering on
// its own, not as gusts rolling across the field. So the gust leans on the slow swell (6.3 s a wave, the ripple's
// share 0.4 -> 0.2), the per-plant flutter is a whisper, a canopy's leaves flutter nearly in step, and a gust shows
// as a sheen on the plants it bends (GUST_SHEEN) - a wave you can see from above, not a pixel's jitter.
// The top of a grass tuft at a full gust moves ~0.08 block (~2.5 px at the default zoom); a leaf block ~0.02
const float PLANT_SWAY = 0.08;
const float PLANT_FLUTTER = 0.05;      // the flutter's share of a plant's sway (was 0.15)
const float LEAF_SWAY = 0.018;
const float LEAF_PHASE = 0.2;          // the leaves' flutter phase changes 5x slower across space than a plant's: a
                                       // canopy moves as one, not block by block
const float WIND_RIPPLE = 0.2;         // the 17-block ripple's share of a gust (1.7 s a wave: the fast part)
// a plant brightens as a gust bends it and dims in the lull, by at most this much: from above, gusts roll across
// the field as soft light bands 41 blocks apart. +-0.7 x 0.07 = at most 5% of a tuft's colour; ground and units keep
// theirs (plants only)
const float GUST_SHEEN = 0.07;

float windHeading(float t) {
    return WIND_HEADING + WIND_TURN * sin(WIND_TWO_PI * t / WIND_TURN_PERIOD);
}

// -0.4 .. 1: biased downwind (mean 0.3), so a plant leans with the wind and swings back on the lulls
float windGust(vec2 xz, float t, vec2 dir) {
    float d = dot(xz, dir);
    float swell = sin(WIND_TWO_PI * (d - t * WIND_V1) / WIND_L1);
    float ripple = sin(WIND_TWO_PI * (d - t * WIND_V2) / WIND_L2 + 1.7);
    return 0.3 + 0.7 * ((1.0 - WIND_RIPPLE) * swell + WIND_RIPPLE * ripple);
}

// the gust (-0.4 .. 1, mean 0.3) at world position wp now: the plants' sheen (lit.vsh -> lit.fsh)
float windGustAt(vec3 wp) {
    float heading = windHeading(frameTimeCounter);
    return windGust(wp.xz, frameTimeCounter, vec2(cos(heading), sin(heading)));
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
    float h = PLANT_SWAY * plant * (g + PLANT_FLUTTER * flutter);
    off.xz += dir * h + side * (PLANT_SWAY * plant * 0.08 * across);
    off.y -= 0.15 * abs(h);
    // leaves: a small shimmer about their rest, stronger as a gust passes, nearly in step across a canopy
    float s = LEAF_SWAY * leaf * (0.35 + 0.65 * clamp(g, 0.0, 1.0));
    float leafFlutter = sin(WIND_TWO_PI * WIND_FLUTTER_HZ * t + phase * LEAF_PHASE);
    float leafAcross = sin(WIND_TWO_PI * WIND_FLUTTER_HZ * 0.73 * t + phase * LEAF_PHASE * 1.31);
    off += vec3(dir * leafFlutter + side * (0.5 * leafAcross), 0.4 * leafAcross * leafFlutter) * s;
    return off * WIND_STRENGTH;
}

#endif
