// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// The meadow's macro variation (light pass 3, the texture developer's round 41: a texture repeats every block, so the
// lawn read as uniform felt across a 1600-px frame - luma 0.43 +- 0.03, all of it high-frequency). Biome-tinted terrain
// (the grass top and lip, tufts, ferns, vines) takes a world-space low-frequency field on its albedo: patches 8-16
// blocks across a little lighter or darker (MEADOW_VALUE), and on a field of its own drier yellow-green or lusher
// blue-green (MEADOW_HUE). Two octaves of value noise at cell sizes that share no factor with each other or with the
// block, so no tiling shows; the same world position always gets the same value, and the mean is unchanged (the
// value factor is a log-normal normalised to a mean of 1; the hue shift is symmetric).
// Needs lib/settings.glsl and lib/color.glsl.

#ifndef MEADOW_GLSL
#define MEADOW_GLSL

// 0..1 from an integer lattice point (a permutation polynomial mod 289: exact in a float)
float meadowHash(vec2 c) {
    c = mod(c, 289.0);
    float h = mod((c.x * 34.0 + 7.0) * c.x + c.y * 13.0, 289.0);
    h = mod((h * 34.0 + 1.0) * h + c.y, 289.0);
    return h / 288.0;
}

float meadowNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);     // quintic: no creases along the lattice
    return mix(mix(meadowHash(i), meadowHash(i + vec2(1.0, 0.0)), f.x),
               mix(meadowHash(i + vec2(0.0, 1.0)), meadowHash(i + vec2(1.0, 1.0)), f.x), f.y);
}

// -1..1, two octaves (MEADOW_CELL and ~0.43 of it), offset so the value and hue fields are unrelated
float meadowField(vec2 xz, vec2 offset) {
    // the lattice turned off the block axes (light pass 9: value noise on an axis-aligned lattice drew broad light and
    // dark bands along the block axes on the lawn, in the RTS view's diagonals), each octave by its own angle
    const mat2 TURN_A = mat2(0.8090, 0.5878, -0.5878, 0.8090);        // 36 degrees
    const mat2 TURN_B = mat2(0.3746, -0.9272, 0.9272, 0.3746);        // -68 degrees
    vec2 p = TURN_A * xz / MEADOW_CELL + offset;
    float n = 0.80 * meadowNoise(p) + 0.20 * meadowNoise(TURN_B * p * 2.31 + vec2(41.0, 17.0));
    return clamp((n - 0.5) * 2.4, -1.0, 1.0);
}

// the albedo (display values) at world position xz, for a biome-tinted surface
vec3 meadow(vec3 albedo, vec2 xz) {
    float v = meadowField(xz, vec2(0.0));
    float d = meadowField(xz, vec2(113.0, 59.0));
    // value: a log-normal factor in linear light, normalised to a mean of 1 (v has a std of ~0.45)
    float k = MEADOW_VALUE * v - 0.5 * (MEADOW_VALUE * 0.45) * (MEADOW_VALUE * 0.45);
    vec3 lin = toLinear(albedo) * exp(k);
    // hue: dry (d > 0) leans yellow - red up, blue down; lush leans blue-green. ~6 degrees at the field's extremes
    lin *= vec3(1.0 + MEADOW_HUE_RED * d, 1.0, 1.0 - MEADOW_HUE_BLUE * d);
    return toDisplay(lin);
}

#endif
