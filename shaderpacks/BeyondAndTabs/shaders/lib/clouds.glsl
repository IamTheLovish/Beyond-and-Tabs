// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// Cloud shadows (lovish, Oct 30): soft patches 20-40 blocks across drifting over the field with the wind, so a sunny
// map has weather moving over it. They dim the sun only - the sky's fill comes from the whole sky and stays - and never
// below CLOUD_DARKEST of the sun. By the light table (lib/light.glsl) at noon: open ground under the thickest cloud
// keeps ~82% of its light (~0.91 of its screen luma), and a building's shadow inside a cloud keeps its fill-only light
// as before, so its shadow / lit ratio rises from ~0.66 to ~0.72 on screen - a softer shadow, never a darker unit.
// The design's band (design/own_shader_pack.md, slice 1: 0.45-0.70) is about units staying readable in shade; under
// a cloud they are lighter than that, not darker.
// Pure arithmetic, no texture: a two-octave value noise over world XZ from a small integer-polynomial hash.
// Needs lib/wind.glsl (the clock, the camera and the wind heading) before it.

#ifndef CLOUDS_GLSL
#define CLOUDS_GLSL

// the sun's share left under the thickest part of a cloud. Light pass 11: 0.70 -> 0.85 - the patches are 15-60 blocks,
// so a cloud can cover a whole RTS frame, and the eyes' lawn measurement swung by ~0.04 with the clouds
// light pass 22: 0.85 -> 0.92 - the light adds ~0.045 of the lawn's 48-px cv over its albedo (lp21 probes); a cloud
// took up to ~0.012 of a 0.48 lawn at 5000, a soft blotch the critic counted with the rest. Half of it now
const float CLOUD_DARKEST = 0.92;
// blocks per noise cell. Measured on a 900-block square of this noise (numpy): patches past half cover have a median
// diameter of 31 blocks, the middle half 14-60; a quarter of the ground is under one, and the edge ramp is >= 2.5 blocks
const float CLOUD_CELL = 34.0;
const float CLOUD_SPEED = 2.0;         // blocks per second, downwind
const float CLOUD_HEIGHT = 190.0;      // the layer's world height: shadows land along the light, so walls and the
                                       // ground under one cloud agree
const float CLOUD_COVER_LO = 0.52;     // noise below this: open sky; above CLOUD_COVER_HI: the full shadow. The gap
const float CLOUD_COVER_HI = 0.72;     // between them is the soft edge

// 0..1 from an integer lattice point, by a permutation polynomial mod 289 (exact in a float: every product < 2^24)
float cloudHash(vec2 c) {
    c = mod(c, 289.0);
    float h = mod((c.x * 34.0 + 1.0) * c.x + c.y, 289.0);
    h = mod((h * 34.0 + 1.0) * h, 289.0);
    h = mod((h * 34.0 + 7.0) * h + c.x, 289.0);
    return h / 288.0;
}

float cloudNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);       // smooth between lattice points: no creases in the soft edges
    float a = cloudHash(i);
    float b = cloudHash(i + vec2(1.0, 0.0));
    float c = cloudHash(i + vec2(0.0, 1.0));
    float d = cloudHash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

// the sun's share reaching world position wp past the clouds, CLOUD_DARKEST..1. worldLight: the direction to the
// light, world axes (normalised)
float cloudShade(vec3 wp, vec3 worldLight) {
    // follow the light up to the cloud layer (a low sun throws the patches a long way, as real ones do)
    float up = max(worldLight.y, 0.15);
    vec2 xz = wp.xz + worldLight.xz * (max(CLOUD_HEIGHT - wp.y, 0.0) / up);
    // the layer drifts along the prevailing wind (the heading's mean: a wandering heading would make it slosh)
    xz -= vec2(cos(WIND_HEADING), sin(WIND_HEADING)) * (frameTimeCounter * CLOUD_SPEED);
    // the lattice turned off the block axes (light pass 9: an axis-aligned value-noise lattice reads as bands along
    // the block axes from the RTS camera)
    vec2 p = mat2(0.8090, 0.5878, -0.5878, 0.8090) * xz / CLOUD_CELL;
    float n = 0.78 * cloudNoise(p) + 0.22 * cloudNoise(p * 2.3 + vec2(17.0, 41.0));
    float cover = smoothstep(CLOUD_COVER_LO, CLOUD_COVER_HI, n);
    return mix(1.0, CLOUD_DARKEST, cover);
}

#endif
