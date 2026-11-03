// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// Sun (or moon) shadow lookup: 1 = lit, 0 = in shadow. Slice 1 draws crisp shadows - four depth comparisons blended by
// the sample's position between texels, so an edge is smooth rather than stair-stepped; the softening with distance
// from the caster comes in slice 3.
// Needs lib/shadowfit.glsl, and the fit computed in the vertex shader (it is the same for every vertex of a frame).

uniform sampler2D shadowtex0;

// a world-space normal offset and a depth bias, both in shadow texels, so neither depends on the zoom: a fitted map
// at zoom 10 has a texel of ~1/40 block, the loader's whole box ~1/6
const float SHADOW_NORMAL_OFFSET = 1.5;
const float SHADOW_DEPTH_BIAS = 1.0;

float shadowTap(vec2 uv, float depth) {
    return depth <= texture2D(shadowtex0, uv).r ? 1.0 : 0.0;
}

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
    float bias = texelBlocks * SHADOW_DEPTH_BIAS * abs(shadowProjection[2][2]) * 0.5;
    float depth = s.z - bias;

    float res = float(shadowMapResolution);
    vec2 st = s.xy * res - 0.5;
    vec2 f = fract(st);
    vec2 base = (floor(st) + 0.5) / res;
    float px = 1.0 / res;
    float a = shadowTap(base, depth);
    float b = shadowTap(base + vec2(px, 0.0), depth);
    float c = shadowTap(base + vec2(0.0, px), depth);
    float d = shadowTap(base + vec2(px, px), depth);
    float lit = mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
    // fade out at the map's edge rather than end in a line
    return mix(lit, 1.0, smoothstep(0.94, 1.0, edge));
}
