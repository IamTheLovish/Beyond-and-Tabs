// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// Water (slice 4, design/own_shader_pack.md). Slice 1 drew every water pixel at the texture's one alpha (0.85, #401):
// a ford ankle-deep over gravel looked exactly as murky as the middle of a lake. Real water is clear in a thin layer
// and only takes its own colour over distance: light crossing it is absorbed (and scattered back toward the eye) per
// block of path, so what shows of the bed falls off as exp(-k * path) - Beer-Lambert. The path is measured per pixel
// from the depth under the surface: depthtex1 (opaque geometry only, sampled in gbuffers_water) against the surface's
// own position.
// The environment palette's targets meet the reference through that depth: deep water (3+ blocks of path) settles into
// the biome's dark teal-blue at about the palette's value; a shallow ford (under a block) stays mostly clear, the bed
// and anything standing in it showing through - units crossing a river stay readable (the 12 river stills).

// extinction per block of path through the water. 0.8: half the bed's light is gone after ~0.9 blocks, 90% after ~2.9,
// so a 1-block ford (path ~1.1 at the RTS tilt) still shows the bed at ~40% and 3+ blocks read as open water
const float WATER_EXTINCTION = 0.80;
// the opacity deep water settles to: a little above slice 1's 0.85, so a lake reads as a body of water, not tinted
// glass, while the far bank's shapes still ghost through at the very edge
const float WATER_DEEP_OPACITY = 0.92;
// the path used where nothing opaque lies behind the water (a waterfall over the void, the map's edge): deep
const float WATER_NO_BED = 64.0;

// opacity of the water layer over a path of `path` blocks through it
float waterOpacity(float path) {
    return WATER_DEEP_OPACITY * (1.0 - exp(-WATER_EXTINCTION * max(path, 0.0)));
}

// view-space position of a screen point (uv 0..1) at a depth buffer value
vec3 waterViewAt(vec2 uv, float depth) {
    vec4 v = gbufferProjectionInverse * vec4(vec3(uv, depth) * 2.0 - 1.0, 1.0);
    return v.xyz / v.w;
}
