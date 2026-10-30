// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// Sun (or moon) shadow lookup: 1 = lit, 0 = in shadow.
// Slice 1 drew crisp shadows: four depth comparisons blended by the sample's position between texels, so an edge is
// smooth rather than stair-stepped. Slice 3 (SOFT_SHADOWS) softens them as real sunlight does: the sun is a disc, not
// a point, so a shadow's edge blurs in proportion to how far the shadow falls from its caster. A wall's shadow is sharp
// where it meets the wall's foot and a tall tower's tip is soft; a unit's shadow at its feet stays crisp. Steps:
//   1. blocker search: shadow-map depths round the point within the widest penumbra we draw; none nearer the light
//      than the point -> fully lit (most of the screen stops here);
//   2. their mean distance to the point along the light, in blocks, times the sun's angular size = the penumbra;
//   3. that many blocks of filtering: 12 taps on a rotated spiral, each one the slice 1 four-texel comparison.
// Every distance is in blocks, so the softness is the same at every zoom of the fitted map. Each tap's depth follows
// the receiving surface's own plane (from its normal in the light's view), so a wide filter on sloped or sun-raked
// ground never compares against itself (no acne) and no larger constant bias is needed (no peter-panning).
// Softer edges change only the edge: the shade inside a shadow is the same fill as before, so the judged shadow / lit
// band (tools/shader_judge.py, day 0.55-0.70) holds.
// Needs lib/shadowfit.glsl, and the fit computed in the vertex shader (it is the same for every vertex of a frame).

uniform sampler2D shadowtex0;

// a world-space normal offset and a depth bias, both in shadow texels, so neither depends on the zoom: a fitted map
// at zoom 10 has a texel of ~1/40 block, the loader's whole box ~1/6
const float SHADOW_NORMAL_OFFSET = 1.5;
const float SHADOW_DEPTH_BIAS = 1.0;

// the penumbra's width per block between caster and receiver (SUN_SPREAD) and its narrowest (PENUMBRA_MIN_BLOCKS) are
// in lib/settings.glsl (the light pass: slice 3's 0.012 drew a 4-block wall's tip ~2 px soft, still a razor edge)
// the farthest caster the blocker search looks for (blocks along the light): its penumbra is the widest drawn
const float SHADOW_SEARCH_BLOCKS = 32.0;
// the narrowest filter, in shadow texels (radius): light pass 3 0.75 -> 1.5 - at 0.75 with the floor at 0.03 block the
// edge was a razor that showed the shadow map's texels as stair-steps (critic, N_s2_blob)
// Light pass 7: 1.0 in the RTS view (the fitted map at 4096 texels: ~1 px a texel at the default zoom, so an edge
// is <= 2 px and straight - pass 6's 1.5 texels of a 2048 map drew a ~10 px wavy band), 2.0 in a perspective view
// (the loader's whole box: coarse texels near the camera, which a narrower filter showed as stair-steps)
// the sun's spread scaled per draw (light pass 9): lit.fsh widens it under a low sun (LOW_SUN_SPREAD), whose long
// shadows read hard and ink-black against the benchmark's hazy ones
float gShadowSpread = 1.0;
const float PENUMBRA_MIN_TEXELS = 1.0;
const float PENUMBRA_MIN_TEXELS_PERSPECTIVE = 2.0;
// the steepest a receiver's plane may tilt in the light's view (depth per block across): past this the surface is
// edge-on to the sun and gets little sun anyway; capping it keeps a far tap from passing behind the caster. What the
// cap cuts off is added back as a bias that grows with the tap's distance in every direction (shortfall), so a tap
// never lands in front of the true plane: before the light pass, the capped plane under-followed a sun-raked wall and
// its taps toward the light met the wall itself - self-shadowed slivers along walls the sun grazes
const float SHADOW_MAX_SLOPE = 3.0;

float shadowTap(vec2 uv, float depth) {
    return depth <= texture2D(shadowtex0, uv).r ? 1.0 : 0.0;
}

// slice 1's lookup: four comparisons blended by position between the texels
float shadowBilinear(vec2 uv, float depth, float res) {
    vec2 st = uv * res - 0.5;
    vec2 f = fract(st);
    vec2 base = (floor(st) + 0.5) / res;
    float px = 1.0 / res;
    float a = shadowTap(base, depth);
    float b = shadowTap(base + vec2(px, 0.0), depth);
    float c = shadowTap(base + vec2(0.0, px), depth);
    float d = shadowTap(base + vec2(px, px), depth);
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

#if SOFT_SHADOWS == 1
// point i of n on a spiral filling the unit disc evenly (golden angle steps), turned by `turn` (0..1)
vec2 shadowSpiral(int i, int n, float turn) {
    float a = float(i) * 2.39996323 + turn * 6.2831853;
    return vec2(cos(a), sin(a)) * sqrt((float(i) + 0.5) / float(n));
}

// s: the point in shadow-map space (uv, depth, bias applied); blocksPerUv: blocks across one unit of shadow uv;
// depthPerBlock: shadow depth per block along the light; slope: the receiver's depth change per block of uv offset
float softShadow(vec3 s, float blocksPerUv, float depthPerBlock, vec2 slope, float shortfall, float res) {
    // a per-pixel turn of the tap pattern: neighbouring pixels sample different points, so a wide penumbra is smooth
    // to the eye rather than banded in 12 steps
    float turn = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    float texel = 1.0 / res;
    float spread = SUN_SPREAD * gShadowSpread;
    float searchUv = max(0.5 * SHADOW_SEARCH_BLOCKS * spread / blocksPerUv, 1.5 * texel);
    float blockers = 0.0;
    float blockerDepth = 0.0;
    for (int i = 0; i < 8; i++) {
        vec2 o = shadowSpiral(i, 8, turn) * searchUv;
        float receiver = s.z + (dot(slope, o) + shortfall * length(o)) * blocksPerUv * depthPerBlock;
        float d = texture2D(shadowtex0, s.xy + o).r;
        if (d < receiver) {
            blockers += 1.0;
            blockerDepth += d;
        }
    }
    if (blockers < 0.5)
        return 1.0;
    float gap = max(s.z - blockerDepth / blockers, 0.0) / depthPerBlock;          // blocks from caster to receiver
    float minTexels = orthoView() ? PENUMBRA_MIN_TEXELS : PENUMBRA_MIN_TEXELS_PERSPECTIVE;
    float radiusUv = max(0.5 * max(gap * spread, PENUMBRA_MIN_BLOCKS) / blocksPerUv, minTexels * texel);
    // every search tap blocked and the penumbra inside the searched disc: deep inside the shadow
    if (blockers > 7.5 && radiusUv < searchUv * 0.5)
        return 0.0;
    float lit = 0.0;
    float plane = blocksPerUv * depthPerBlock;
    for (int i = 0; i < 12; i++) {
        vec2 o = shadowSpiral(i, 12, turn) * radiusUv;
        lit += shadowBilinear(s.xy + o, s.z + (dot(slope, o) + shortfall * length(o)) * plane, res);
    }
    return lit / 12.0;
}
#endif

float sunShadow(vec3 playerPos, vec3 worldNormal, vec3 fit) {
    // one shadow texel in blocks: the clip span of a texel over the clip units per block of the light's projection
    float texelBlocks = (2.0 / float(shadowMapResolution)) / (abs(shadowProjection[0][0]) * fit.z);
    vec3 p = playerPos + worldNormal * (texelBlocks * SHADOW_NORMAL_OFFSET);
    vec4 clip = shadowProjection * (shadowModelView * vec4(p, 1.0));
    clip.xy = shadowRemap(clip.xy, fit);
    float edge = max(abs(clip.x), abs(clip.y));
    if (edge >= 1.0)
        return 1.0;                    // outside the map: nothing was drawn there
    vec3 s = clip.xyz * 0.5 + 0.5;
    float depthPerBlock = abs(shadowProjection[2][2]) * 0.5;
    float bias = texelBlocks * SHADOW_DEPTH_BIAS * depthPerBlock;
    float depth = s.z - bias;
    float res = float(shadowMapResolution);

#if SOFT_SHADOWS == 1
    // the receiver's plane in the light's view: moving across it by o blocks changes its distance from the light by
    // dot(n.xy, o) / n.z (the light looks down its -z, so a surface facing the light has n.z > 0)
    vec3 ns = mat3(shadowModelView) * worldNormal;
    vec2 slope = ns.xy / max(ns.z, 1e-3);
    float steep = length(slope);
    float shortfall = max(steep - SHADOW_MAX_SLOPE, 0.0);
    if (steep > SHADOW_MAX_SLOPE)
        slope *= SHADOW_MAX_SLOPE / steep;
    float lit = softShadow(vec3(s.xy, depth), texelBlocks * res, depthPerBlock, slope, shortfall, res);
#else
    float lit = shadowBilinear(s.xy, depth, res);
#endif
    // fade out at the map's edge rather than end in a line
    return mix(lit, 1.0, smoothstep(0.94, 1.0, edge));
}
