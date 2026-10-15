#version 150

// The world look (postfx/WorldLookPass): edge smoothing on geometry edges, aerial-perspective haze from the depth
// buffer, a parametric film grade and a faint vignette - in that order: the picture, the air, then the "camera". No
// blur anywhere else (no depth of field: blur plus a high camera is the toy-miniature look): textures keep every
// texel. Overlays drawn without depth (health and build bars) are smoothed only where they cross a geometry edge.

uniform sampler2D DiffuseSampler;   // the finished world colour
uniform sampler2D DepthSampler;     // the main depth texture (read only: the pass writes into its own target)

uniform mat4 InvProjMat;            // inverse of the projection the world was drawn with (RTS ortho or perspective)
uniform vec3 HazeColor;
uniform float HazeStart;            // blocks; perspective: from the eye, ortho: view depth of the screen's bottom edge
uniform float HazeInvScale;         // 1 / blocks per e-fold of the haze
uniform float HazeMax;              // the haze never covers more than this fraction (overlays stay readable)
uniform float Ortho;                // 1: distance along the view direction (ortho has no eye), 0: distance from the eye
uniform float Contrast;             // extra midtone slope of the S-curve on luma (0 = none)
uniform float SplitTone;            // cool shadows / warm highlights
uniform float Saturation;           // at or below 1, never boosted
uniform float Vignette;             // corner darkening
uniform float Aspect;               // width / height
uniform float EdgeAA;               // 1: smooth geometry edges (FXAA where the depth buffer shows an edge)

in vec2 texCoord;

out vec4 fragColor;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);
// near-unit-luma tints: the split tone moves hue, not brightness
const vec3 SHADOW_TINT = vec3(0.96, 1.0, 1.06);
const vec3 HIGHLIGHT_TINT = vec3(1.04, 1.0, 0.94);
// scattering drifts distant colour cool before the in-scattered light veils it
const vec3 DISTANCE_COOL = vec3(0.93, 0.99, 1.07);

// ---- edge smoothing ----
// Minecraft draws with no anti-aliasing, and from the RTS camera a unit is ~200 small box faces in ~90 px: the stair
// steps of their edges crawl whenever the camera or the unit moves - most of the shimmer left after the unit mipmaps
// (tools/unit_rts_view.py: the crawl over a body is 90%+ edges and seams). FXAA (the compact form of Lottes' fast
// approximate anti-aliasing) smooths them - but run on every pixel it would also soften the block textures (an
// offline test on an eyes still: grass detail -14%, in-world name tags smeared), so it runs only where the DEPTH
// buffer shows a geometry edge: a pixel whose neighbours do not lie on one plane with it. Flat ground, walls and every
// texture inside a face keep their texels exactly.
// The test is relative to one pixel's footprint p at that depth: a silhouette puts the centre ~p off the line through
// its neighbours, a crease where two faces meet (a box corner, a block step) a good part of p, a plane exactly 0 - so
// it holds at every zoom and in first person. Perspective depth is quantised ~z^2 (24-bit, near 0.05): far terrain
// gets a matching margin, else oblique hillsides past ~90 blocks would read as edges (and flicker) - a reviewer's
// simulation, Oct 15.
const float EDGE_FOOTPRINTS = 0.25;  // off-line distance, in pixel footprints, that marks an edge
const float DEPTH_NOISE = 4e-6;      // perspective only: margin = this * view distance^2 (blocks)
const float FXAA_REDUCE_MIN = 1.0 / 128.0;
const float FXAA_REDUCE_MUL = 1.0 / 8.0;
const float FXAA_SPAN_MAX = 8.0;

vec3 viewPos(vec2 uv) {
    vec4 v = InvProjMat * vec4(vec3(uv, textureLod(DepthSampler, uv, 0.0).r) * 2.0 - 1.0, 1.0);
    return v.xyz / v.w;
}

// distance of b from the line through a and c: zero on any plane (ortho or perspective), large across an edge
float offLine(vec3 a, vec3 b, vec3 c) {
    vec3 ac = c - a;
    return length(cross(ac, b - a)) / max(length(ac), 1e-4);
}

bool geometryEdge(vec2 uv, vec2 px, float depth, vec3 centre) {
    // one pixel's width at the centre's depth (ortho: the same everywhere; perspective: grows with distance)
    vec4 side = InvProjMat * vec4(vec3(uv + vec2(px.x, 0.0), depth) * 2.0 - 1.0, 1.0);
    float footprint = length(side.xyz / side.w - centre);
    float tolerance = EDGE_FOOTPRINTS * footprint + (Ortho > 0.5 ? 0.0 : DEPTH_NOISE * centre.z * centre.z);
    float ex = offLine(viewPos(uv - vec2(px.x, 0.0)), centre, viewPos(uv + vec2(px.x, 0.0)));
    float ey = offLine(viewPos(uv - vec2(0.0, px.y)), centre, viewPos(uv + vec2(0.0, px.y)));
    return max(ex, ey) > tolerance;
}

vec3 fxaa(vec2 uv, vec2 px, vec3 rgbM) {
    vec3 rgbNW = textureLod(DiffuseSampler, uv + vec2(-1.0, -1.0) * px, 0.0).rgb;
    vec3 rgbNE = textureLod(DiffuseSampler, uv + vec2(1.0, -1.0) * px, 0.0).rgb;
    vec3 rgbSW = textureLod(DiffuseSampler, uv + vec2(-1.0, 1.0) * px, 0.0).rgb;
    vec3 rgbSE = textureLod(DiffuseSampler, uv + vec2(1.0, 1.0) * px, 0.0).rgb;
    float lNW = dot(rgbNW, LUMA), lNE = dot(rgbNE, LUMA), lSW = dot(rgbSW, LUMA), lSE = dot(rgbSE, LUMA);
    float lM = dot(rgbM, LUMA);
    float lMin = min(lM, min(min(lNW, lNE), min(lSW, lSE)));
    float lMax = max(lM, max(max(lNW, lNE), max(lSW, lSE)));
    if (lMax - lMin < max(0.0312, lMax * 0.166))      // too little contrast to show a stair step
        return rgbM;
    vec2 dir = vec2(-((lNW + lNE) - (lSW + lSE)), (lNW + lSW) - (lNE + lSE));
    float reduce = max((lNW + lNE + lSW + lSE) * 0.25 * FXAA_REDUCE_MUL, FXAA_REDUCE_MIN);
    float rcpMin = 1.0 / (min(abs(dir.x), abs(dir.y)) + reduce);
    dir = clamp(dir * rcpMin, vec2(-FXAA_SPAN_MAX), vec2(FXAA_SPAN_MAX)) * px;
    // bilinear taps along the edge (WorldLookPass samples the colour with LINEAR filtering for this pass)
    vec3 rgbA = 0.5 * (textureLod(DiffuseSampler, uv + dir * (1.0 / 3.0 - 0.5), 0.0).rgb
        + textureLod(DiffuseSampler, uv + dir * (2.0 / 3.0 - 0.5), 0.0).rgb);
    vec3 rgbB = rgbA * 0.5 + 0.25 * (textureLod(DiffuseSampler, uv - dir * 0.5, 0.0).rgb
        + textureLod(DiffuseSampler, uv + dir * 0.5, 0.0).rgb);
    float lB = dot(rgbB, LUMA);
    return (lB < lMin || lB > lMax) ? rgbA : rgbB;
}

void main() {
    vec4 scene = texture(DiffuseSampler, texCoord);
    vec3 c = scene.rgb;
    float depth = texture(DepthSampler, texCoord).r;

    if (EdgeAA > 0.5) {
        vec2 px = 1.0 / vec2(textureSize(DiffuseSampler, 0));
        if (geometryEdge(texCoord, px, depth, viewPos(texCoord)))
            c = fxaa(texCoord, px, c);
    }

    // depth 1.0 = nothing drawn there: the sky (or the RTS view's void tone) is never hazed
    if (depth < 1.0) {
        vec4 view = InvProjMat * vec4(vec3(texCoord, depth) * 2.0 - 1.0, 1.0);
        vec3 pos = view.xyz / view.w;
        float dist = Ortho > 0.5 ? -pos.z : length(pos);
        float h = HazeMax * (1.0 - exp(-max(dist - HazeStart, 0.0) * HazeInvScale));
        // haze lifts dark pixels most: the fog of war's unexplored ground would close in on the explored ground toward
        // the top of the RTS screen (3.0:1 -> ~1.9:1 fully zoomed out). Dark ground keeps 40% of the haze - the
        // seen / unseen read is gameplay; air comes second
        h *= mix(0.4, 1.0, smoothstep(0.05, 0.4, dot(c, LUMA)));
        c = mix(c, vec3(dot(c, LUMA)), h * 0.5);
        c *= mix(vec3(1.0), DISTANCE_COOL, h);
        c = mix(c, HazeColor, h);
    }

    // filmic S on luma: a quintic with slope 1 at black and white and 1 + Contrast through the midtones, so shadows
    // are not crushed (the fog of war's dark ground must stay readable) and highlights not flattened. Applied as a
    // ratio to rgb, so the curve leaves saturation (chroma / luma) unchanged.
    float l = dot(c, LUMA);
    float lc = l + Contrast * 8.0 * l * l * (1.0 - l) * (1.0 - l) * (2.0 * l - 1.0);
    c *= lc / max(l, 1e-4);
    float chroma = length(c - lc);
    // bell-shaped weights (peaks at 1/4 and 3/4 luma): black, white and the midtones stay neutral, so white rings and
    // damage numbers never turn cream and shadows never lift toward blue
    float shadows = smoothstep(0.0, 0.25, lc) - smoothstep(0.25, 0.5, lc);
    float highlights = smoothstep(0.5, 0.75, lc) - smoothstep(0.75, 1.0, lc);
    c *= mix(vec3(1.0), SHADOW_TINT, shadows * SplitTone) * mix(vec3(1.0), HIGHLIGHT_TINT, highlights * SplitTone);
    // the tint turns hue but never adds colour to what already had it (a warm tint on warm sand would): chroma is
    // capped at its value before the tint, except that near-greys may take the tint's own faint colour
    float lt = dot(c, LUMA);
    vec3 v = c - lt;
    c = lt + v * min(1.0, max(chroma, 0.1 * SplitTone * lt) / max(length(v), 1e-5));
    c = mix(vec3(lt), c, Saturation);

    // quartic falloff from the centre, 1 at the corners
    vec2 d = (texCoord - 0.5) * vec2(Aspect, 1.0);
    float r2 = dot(d, d) / (0.25 * (Aspect * Aspect + 1.0));
    c *= 1.0 - Vignette * r2 * r2;

    // one 8-bit step of interleaved-gradient dither: the curve and the faint vignette gradient never band
    c += (fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715)))) - 0.5) / 255.0;
    fragColor = vec4(clamp(c, 0.0, 1.0), scene.a);
}
