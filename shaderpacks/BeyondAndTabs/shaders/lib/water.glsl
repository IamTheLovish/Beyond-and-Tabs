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

// ---- the surface: ripple normals, Fresnel, the sun's glint ----

// water's reflectance straight on (n = 1.33): 2%. At the RTS camera's ~65 degrees down the view is ~25 degrees off the
// surface's normal, where Fresnel is still ~2% - from above, water is mostly what lies under it, as in the reference;
// toward a grazing first-person view it climbs to a mirror
const float WATER_F0 = 0.02;
// the glint's GGX roughness: small, so the sun breaks into sparkles on the ripples rather than one smeared sheet
const float WATER_ROUGHNESS = 0.06;
// how much the ripples shade the water body itself (the diffuse light's change with the tilt): a hint of relief on deep
// water, where the 2% reflection alone would hide them
const float RIPPLE_SHADE = 0.8;
// the battlefield's water colour (#3A6A8C, its biome) has 0.55 as its brightest channel: a vertex colour darker than
// that is the fog of war's tint, which dims the reflections as it dims the water (no light in the fog)
const float WATER_TINT_BRIGHTEST = 0.55;

// the slope (dh/dx, dh/dz) of one travelling wave a sin(k d.p - w t): steepness s = a k, so its slope is s d cos(..)
vec2 waveSlope(vec2 p, float t, vec2 dir, float k, float steep) {
    // deep-water dispersion w = sqrt(g k), slowed: a calm river, not a sea
    return dir * (steep * cos(k * dot(dir, p) - 0.5 * sqrt(9.8 * k) * t));
}

// the surface's slope at world xz (blocks) and time t (seconds): four waves from 0.5 to 3 blocks long in different
// directions, the shorter ones less steep, ~0.17 at most in all - gentle ripples that never look like a sea.
// WATER_WAVES scales them (0 = a still mirror)
vec2 waterSlope(vec2 p, float t) {
    vec2 g = waveSlope(p, t, vec2(0.80, 0.60), 2.0, 0.050);
    g += waveSlope(p, t, vec2(-0.48, 0.88), 3.7, 0.045);
    g += waveSlope(p, t, vec2(0.96, -0.28), 6.8, 0.040);
    g += waveSlope(p, t, vec2(-0.70, -0.71), 11.8, 0.030);
    return g * WATER_WAVES;
}

// the rippled normal, world space (y up)
vec3 waterNormal(vec2 p, float t) {
    vec2 g = waterSlope(p, t);
    return normalize(vec3(-g.x, 1.0, -g.y));
}

float waterFresnel(float cosTheta) {
    return WATER_F0 + (1.0 - WATER_F0) * pow(1.0 - clamp(cosTheta, 0.0, 1.0), 5.0);
}

// ---- the shore: a soft light line where the water runs out ----
// Where the depth to the bed goes to zero (a bank's side meets the surface, a beach rises through it) the water is a
// film over the bed, stirred to foam and catching the light: a narrow bright band. Measured as depth straight down, so
// against a bank's wall it is ~0.12 blocks wide on screen at the RTS tilt - about four pixels at the default zoom.

// the depth of water (blocks) over which the band fades out
const float FOAM_WIDTH = 0.25;
// the band's colour (display value) and its opacity at the very edge
const float FOAM_ALBEDO = 0.85;
const float FOAM_OPACITY = 0.6;

// the foam's share at a pixel with `below` blocks of water under it: soft, and broken by a slow ripple along the shore
float shoreFoam(float below, vec2 p, float t) {
    float f = 1.0 - smoothstep(0.0, FOAM_WIDTH, below);
    return f * (0.75 + 0.25 * sin(dot(p, vec2(3.1, 2.3)) + t * 1.7));
}

// ---- caustics: the ripples focusing sunlight into a moving net of bright lines on the bed ----
// The water pass knows the bed under each pixel (depthtex1) but cannot change the bed's colour, which is already in
// colortex0; it writes how much brighter the bed should be into colortex2 (WATER_DATA_*), and composite multiplies.
// The pattern is followed from the bed up the sun's direction to the surface (so it slides as the sun moves and lines up
// with the ripples above), animated, faded with depth (deeper focus spreads and dims), zero in the bed's shadow.

// share of the bed's sunlight the net moves around: its lines reach ~ +1.3x the sun's light, its gaps ~ -0.5x (the light they took)
const float CAUSTIC_STRENGTH = 1.3;
// fades per block of water above the bed
const float CAUSTIC_FADE = 0.45;
// colortex2's caustic channel: 0.5 + boost * scale, so a boost of -2..+2 fits in 8 bits (steps of 1/64)
const float CAUSTIC_ENCODE = 0.25;

// one net: three plane waves 120 degrees apart, the plane warped by a slow swirl; where they cancel (their sum near 0) a
// thin bright line - the pattern of light focused through a rippled surface
float causticNet(vec2 p, float t) {
    vec2 q = p + 0.45 * vec2(sin(p.y * 1.3 + t * 0.7), sin(p.x * 1.1 - t * 0.6));
    float s = sin(q.x * 2.3 + t * 0.9) + sin(dot(q, vec2(-1.15, 1.99)) + t * 1.1)
        + sin(dot(q, vec2(-1.15, -1.99)) - t * 0.8);
    return 1.0 - smoothstep(0.0, 0.9, abs(s));
}

// the bed's extra sunlight as a share of the sun's light, about zero on average: two nets of different sizes, so it
// never reads as a tiled lattice
float caustics(vec2 p, float t) {
    float n = 0.6 * causticNet(p, t) + 0.4 * causticNet(p * 1.73 + vec2(3.1, 7.7), t * 1.21 + 2.0);
    return clamp(n * n * 3.0 - 0.42, -0.4, 1.0) * CAUSTIC_STRENGTH;
}
