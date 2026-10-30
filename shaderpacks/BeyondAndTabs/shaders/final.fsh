#version 120
// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack. The edge smoothing and the grade are ported from our own world look (bat_postfx.fsh, the
// mod's no-pack pass, with the Enchanted values of #326), so players with and without the pack see the same mood.
// tools/test_shader_pack.py fails if these constants drift from WorldLookPass's Enchanted column once #326 is in.
//
// The "camera" after the air: edge smoothing on geometry edges, a gentle local contrast, Enchanted's soft glow of the
// bright areas, the film grade (exposure, an S on luma, a warm split tone, saturation and vibrance that guard skin,
// team colours and greys) and a vignette.

#include "/lib/settings.glsl"
#include "/lib/color.glsl"

uniform sampler2D colortex0;
uniform sampler2D depthtex0;
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;
uniform float viewWidth;
uniform float viewHeight;

varying vec2 texcoord;

// Enchanted (WorldLookPass, lovish's pick on Oct 28: "dim it down a bit and make it look more saturated and whimsy ...
// fantasy esque")
const float CONTRAST = 0.18;
const float SPLIT_TONE = 1.2;
// 1.0 -> 1.12 (slice 3, Oct 30, lovish: "richer saturation ... pop", then "hyper realism"): the richer colour a good
// camera or slide film gives a sunny day, never cartoon. The boost is guarded (grade()): greys get none, skin and the
// strongest colours (team colours, spell glows, overlays) little, so it lands on the lawn, roofs, wood and water.
// #326's WorldLookPass Enchanted column should take the same value (and the guards) when it lands
const float SATURATION = 1.12;
const float VIGNETTE = 0.16;
// 0.95 -> 1.05 (Oct 30): with slice 2's materials on, the noon lawn read 0.315 on the block stills, under the doc's
// 0.36-0.43 band, and the critic called the whole scene a stop under the benchmark. Exposure scales lit and shade
// alike, so the judged shadow / lit ratios hold
const float EXPOSURE = 1.05;
// 0.45 -> 0.60 (slice 3): vibrance does most of the richening, on the dull colours only (see grade())
const float VIBRANCE = 0.60;
const float GLOW = 0.30;
const float GLOW_THRESHOLD = 0.50;
const float GLOW_RADIUS = 0.016;
const vec3 SHADOW_TINT = vec3(1.06, 1.00, 0.90);
// highlights near neutral (lovish, Oct 30: "more white then cream sounds fine"): the old (1.09, 1.00, 0.85) turned
// white quartz, iron and the capitol cream and grey stone brown (block critic vs the Complementary benchmark). The warm
// Enchanted mood stays in the shadows' tint and the light table; whites read white. #326's WorldLookPass Enchanted
// column must take the same value so players with and without the pack match.
const vec3 HIGHLIGHT_TINT = vec3(1.03, 1.00, 0.96);

// ---- slice 3's colour guards and local contrast ----
// how much of the saturation boost skin keeps out of (0 = none, 1 = all): a face, a villager's hands and a unit's
// bare arms keep their tone instead of turning orange (vibrance already leaves colours as strong as skin alone)
const float SKIN_GUARD = 0.6;
// greys get no boost at all: below GREY_GATE_LO's colourfulness (chroma over luma, before the split tone's faint
// tint) the boost is zero, full from GREY_GATE_HI. White quartz, stone and iron stay neutral (whites stay white)
const float GREY_GATE_LO = 0.03;
const float GREY_GATE_HI = 0.10;
// strong colours keep their drawn value: the saturation boost fades out between these colourfulness values (chroma
// over luma: the lawn ~0.45, water and roofs ~0.5-0.6, team red and blue ~2.2)
const float STRONG_LO = 1.0;
const float STRONG_HI = 1.8;
// local contrast: luma pushed away from its neighbourhood's (~1 block across at the default zoom: colortex0's mip
// 4.5 in five taps), so roofs, walls and the ground between them separate from the RTS camera. Subtle by design: at
// most LOCAL_CONTRAST x LOCAL_CONTRAST_LIMIT (0.022) of luma, faded to nothing at black and white, so it never clips
// a highlight or crushes a shadow and draws no visible halo round a unit
const float LOCAL_CONTRAST = 0.22;
const float LOCAL_CONTRAST_LIMIT = 0.10;
const float LOCAL_CONTRAST_LOD = 4.5;
const float LOCAL_CONTRAST_SPREAD = 20.0;      // pixels between the five taps
// the loader builds colortex0's mipmaps before this pass: the local contrast's neighbourhood comes from them
const bool colortex0MipmapEnabled = true;

// ---- edge smoothing: FXAA only where the depth buffer shows a geometry edge (textures inside a face keep every
// texel; one-pixel-thin features such as name-tag glyphs are left as drawn) ----
const float DEPTH_STEPS = 4.0 / 16777216.0;
const float EDGE_FOOTPRINTS = 0.25;
const float THIN_GAP = 0.2;
const float DEPTH_NOISE = 4e-6;
const float FXAA_REDUCE_MIN = 1.0 / 128.0;
const float FXAA_REDUCE_MUL = 1.0 / 8.0;
const float FXAA_SPAN_MAX = 8.0;

bool orthoView() {
    return gbufferProjection[2][3] == 0.0 && gbufferProjection[3][3] == 1.0;
}

vec3 viewPos(vec2 uv, float depth) {
    vec4 v = gbufferProjectionInverse * vec4(vec3(uv, depth) * 2.0 - 1.0, 1.0);
    return v.xyz / v.w;
}

float depthAt(vec2 uv) {
    return texture2D(depthtex0, uv).r;
}

float offLine(vec3 a, vec3 b, vec3 c) {
    vec3 ac = c - a;
    return length(cross(ac, b - a)) / max(length(ac), 1e-4);
}

bool thin(float a, float b, float c) {
    return b - a > THIN_GAP && b - c > THIN_GAP;
}

bool geometryEdge(vec2 uv, vec2 px, float depth, bool ortho) {
    if (any(lessThan(uv, 2.5 * px)) || any(greaterThan(uv, 1.0 - 2.5 * px)))
        return false;
    vec2 ux = vec2(px.x, 0.0), uy = vec2(0.0, px.y);
    float dL = depthAt(uv - ux), dR = depthAt(uv + ux), dD = depthAt(uv - uy), dU = depthAt(uv + uy);
    if (abs(dL + dR - 2.0 * depth) <= DEPTH_STEPS && abs(dD + dU - 2.0 * depth) <= DEPTH_STEPS)
        return false;
    vec3 centre = viewPos(uv, depth);
    vec3 pL = viewPos(uv - ux, dL), pR = viewPos(uv + ux, dR), pD = viewPos(uv - uy, dD), pU = viewPos(uv + uy, dU);
    float footprint = length(viewPos(uv + ux, depth) - centre);
    float tolerance = EDGE_FOOTPRINTS * footprint + (ortho ? 0.0 : DEPTH_NOISE * centre.z * centre.z);
    if (max(offLine(pL, centre, pR), offLine(pD, centre, pU)) <= tolerance)
        return false;
    if (thin(pL.z, centre.z, pR.z) || thin(pD.z, centre.z, pU.z))
        return false;
    float zLL = viewPos(uv - 2.0 * ux, depthAt(uv - 2.0 * ux)).z;
    float zRR = viewPos(uv + 2.0 * ux, depthAt(uv + 2.0 * ux)).z;
    float zDD = viewPos(uv - 2.0 * uy, depthAt(uv - 2.0 * uy)).z;
    float zUU = viewPos(uv + 2.0 * uy, depthAt(uv + 2.0 * uy)).z;
    return !(thin(zLL, pL.z, centre.z) || thin(centre.z, pR.z, zRR)
        || thin(zDD, pD.z, centre.z) || thin(centre.z, pU.z, zUU));
}

vec3 colourAt(vec2 uv) {
    return texture2D(colortex0, uv).rgb;
}

vec3 fxaa(vec2 uv, vec2 px, vec3 rgbM) {
    vec3 rgbNW = colourAt(uv + vec2(-1.0, -1.0) * px);
    vec3 rgbNE = colourAt(uv + vec2(1.0, -1.0) * px);
    vec3 rgbSW = colourAt(uv + vec2(-1.0, 1.0) * px);
    vec3 rgbSE = colourAt(uv + vec2(1.0, 1.0) * px);
    float lNW = dot(rgbNW, LUMA), lNE = dot(rgbNE, LUMA), lSW = dot(rgbSW, LUMA), lSE = dot(rgbSE, LUMA);
    float lM = dot(rgbM, LUMA);
    float lMin = min(lM, min(min(lNW, lNE), min(lSW, lSE)));
    float lMax = max(lM, max(max(lNW, lNE), max(lSW, lSE)));
    if (lMax - lMin < max(0.0312, lMax * 0.166))
        return rgbM;
    vec2 dir = vec2(-((lNW + lNE) - (lSW + lSE)), (lNW + lSW) - (lNE + lSE));
    float reduce = max((lNW + lNE + lSW + lSE) * 0.25 * FXAA_REDUCE_MUL, FXAA_REDUCE_MIN);
    float rcpMin = 1.0 / (min(abs(dir.x), abs(dir.y)) + reduce);
    dir = clamp(dir * rcpMin, vec2(-FXAA_SPAN_MAX), vec2(FXAA_SPAN_MAX)) * px;
    vec3 rgbA = 0.5 * (colourAt(uv + dir * (1.0 / 3.0 - 0.5)) + colourAt(uv + dir * (2.0 / 3.0 - 0.5)));
    vec3 rgbB = rgbA * 0.5 + 0.25 * (colourAt(uv - dir * 0.5) + colourAt(uv + dir * 0.5));
    float lB = dot(rgbB, LUMA);
    return (lB < lMin || lB > lMax) ? rgbA : rgbB;
}

// Enchanted's soft glow: 16 taps on a golden-angle spiral round the pixel, bright-passed, weighted to the centre
vec3 glowAt(vec2 uv, float aspect) {
    vec2 r = vec2(GLOW_RADIUS / aspect, GLOW_RADIUS);
    vec3 sum = vec3(0.0);
    float wsum = 0.0;
    for (int i = 0; i < 16; i++) {
        float f = (float(i) + 0.5) / 16.0;
        float a = float(i) * 2.39996323;
        vec3 s = colourAt(uv + vec2(cos(a), sin(a)) * sqrt(f) * r);
        float w = 1.0 - 0.7 * f;
        sum += s * clamp((dot(s, LUMA) - GLOW_THRESHOLD) / (1.0 - GLOW_THRESHOLD), 0.0, 1.0) * w;
        wsum += w;
    }
    return sum / wsum;
}

// 0..1: how much a colour looks like skin. Skin of every tone sits in the red-to-yellow sextant (r > g > b) about a
// third of the way to yellow (hue ~15-40 degrees), moderately saturated
float skinLike(vec3 c) {
    float span = c.r - c.b;
    if (c.r <= c.g || c.g <= c.b || span < 1e-3)
        return 0.0;
    float hue = (c.g - c.b) / span;                // 0 red .. 1 yellow
    float s = span / c.r;
    return (1.0 - smoothstep(0.2, 0.4, abs(hue - 0.45))) * smoothstep(0.08, 0.18, s) * (1.0 - smoothstep(0.45, 0.60, s));
}

// the neighbourhood's luma round uv, from colortex0's mipmaps
float neighbourhoodLuma(vec2 uv, vec2 px) {
    vec2 o = px * LOCAL_CONTRAST_SPREAD;
    vec3 s = texture2D(colortex0, uv, LOCAL_CONTRAST_LOD).rgb * 0.36
        + (texture2D(colortex0, uv + vec2(o.x, o.y), LOCAL_CONTRAST_LOD).rgb
           + texture2D(colortex0, uv + vec2(-o.x, o.y), LOCAL_CONTRAST_LOD).rgb
           + texture2D(colortex0, uv + vec2(o.x, -o.y), LOCAL_CONTRAST_LOD).rgb
           + texture2D(colortex0, uv + vec2(-o.x, -o.y), LOCAL_CONTRAST_LOD).rgb) * 0.16;
    return dot(s, LUMA);
}

vec3 localContrast(vec3 c, vec2 uv, vec2 px) {
    float l = dot(c, LUMA);
    float detail = clamp(l - neighbourhoodLuma(uv, px), -LOCAL_CONTRAST_LIMIT, LOCAL_CONTRAST_LIMIT);
    float midtones = 4.0 * l * (1.0 - l);
    return c * ((l + LOCAL_CONTRAST * detail * midtones) / max(l, 1e-4));
}

vec3 grade(vec3 c) {
    // filmic S on luma (slope 1 at black and white, 1 + CONTRAST through the midtones), as a ratio so saturation holds
    c *= EXPOSURE;
    float l = dot(c, LUMA);
    float lc = l + CONTRAST * 8.0 * l * l * (1.0 - l) * (1.0 - l) * (2.0 * l - 1.0);
    c *= lc / max(l, 1e-4);
    float chroma = length(c - lc);
    // the pixel's own colourfulness, before the split tone gives near-greys its faint tint, decides the boosts below:
    // none for greys, less for skin
    float colourful = chroma / max(lc, 1e-3);
    float grey = smoothstep(GREY_GATE_LO, GREY_GATE_HI, colourful);
    float guard = grey * (1.0 - SKIN_GUARD * skinLike(c));
    // split tone in bells at 1/4 and 3/4 luma: black, white and the midtones stay neutral
    float shadows = smoothstep(0.0, 0.25, lc) - smoothstep(0.25, 0.5, lc);
    float highlights = smoothstep(0.5, 0.75, lc) - smoothstep(0.75, 1.0, lc);
    c *= mix(vec3(1.0), SHADOW_TINT, shadows * SPLIT_TONE) * mix(vec3(1.0), HIGHLIGHT_TINT, highlights * SPLIT_TONE);
    // the tint turns hue but never adds colour (near-greys may take its faint colour)
    float lt = dot(c, LUMA);
    vec3 v = c - lt;
    c = lt + v * min(1.0, max(chroma, 0.1 * SPLIT_TONE * lt) / max(length(v), 1e-5));
    c = mix(vec3(lt), c, 1.0 + (SATURATION - 1.0) * guard * (1.0 - smoothstep(STRONG_LO, STRONG_HI, colourful)));
    // vibrance for the dull pixels only: team colours, glows and the overlays' strong colours stay as drawn
    float lv = dot(c, LUMA);
    vec3 dv = c - lv;
    float sat = length(dv) / max(lv, 1e-3);
    return max(lv + dv * (1.0 + VIBRANCE * grey * clamp(1.0 - sat / 0.6, 0.0, 1.0)), 0.0);
}

void main() {
    vec2 px = 1.0 / vec2(viewWidth, viewHeight);
    float aspect = viewWidth / viewHeight;
    vec3 c = colourAt(texcoord);
#if EDGE_AA == 1
    float depth = depthAt(texcoord);
    if (depth < 1.0 && geometryEdge(texcoord, px, depth, orthoView()))
        c = fxaa(texcoord, px, c);
#endif
#if GRADE == 1
    c = localContrast(c, texcoord, px);
    c = 1.0 - (1.0 - c) * (1.0 - GLOW * glowAt(texcoord, aspect));
    c = grade(c);
    vec2 d = (texcoord - 0.5) * vec2(aspect, 1.0);
    float r2 = dot(d, d) / (0.25 * (aspect * aspect + 1.0));
    c *= 1.0 - VIGNETTE * r2 * r2;
#endif
    // one 8-bit step of dither, so the curve and the vignette never band
    c += (fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715)))) - 0.5) / 255.0;
    gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
