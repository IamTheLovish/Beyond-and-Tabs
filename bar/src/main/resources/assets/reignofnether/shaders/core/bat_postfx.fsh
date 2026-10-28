#version 150

// The world look (postfx/WorldLookPass): edge smoothing on geometry edges, aerial-perspective haze from the depth
// buffer, a parametric film grade and a faint vignette - in that order: the picture, the air, then the "camera". No
// blur anywhere else (no depth of field: blur plus a high camera is the toy-miniature look): textures keep every
// texel. Overlays drawn without depth (health and build bars) are smoothed only where they cross a geometry edge.
// Enchanted (Oct 28) adds the one deliberate exception: a soft glow of the BRIGHT areas only (glowAt), plus dimming and
// vibrance for dull colours - lovish asked for a dimmer, richer, fantasy look.

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
uniform float Saturation;           // a uniform mix toward grey (1 = unchanged)
uniform float Vignette;             // corner darkening
uniform float Exposure;             // overall brightness (1 = unchanged); Enchanted dims the world a little
uniform float Vibrance;             // extra colour for DULL colours only: team colours and UI-like saturated pixels gain ~nothing
uniform float Glow;                 // a soft glow of the bright areas, screened in (0 = none)
uniform float GlowThreshold;        // luma where the glow starts
uniform float GlowRadius;           // its reach as a fraction of the screen height
uniform vec3 ShadowTint;            // the split tone's shadow and highlight tints (near unit luma)
uniform vec3 HighlightTint;
uniform float Aspect;               // width / height
uniform float EdgeAA;               // 1: smooth geometry edges (FXAA where the depth buffer shows an edge)

in vec2 texCoord;

out vec4 fragColor;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);
// the split tone's tints come from WorldLookPass (Subtle / Full: cool 0.96, 1.0, 1.06 and warm 1.04, 1.0, 0.94)
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
// Most pixels are settled before any matrix: a plane is affine in the depth buffer under both projections (ortho depth
// is linear in view z, perspective depth in 1/z, and both in screen position), so on a plane the second difference of
// the raw depths is quantisation only. The eyes CI renders on the CPU (Mesa llvmpipe), where every per-pixel matrix
// of this pass shows in the frame time.
const float DEPTH_STEPS = 4.0 / 16777216.0;   // what 24-bit quantisation alone can put in a second difference
const float EDGE_FOOTPRINTS = 0.25;  // off-line distance, in pixel footprints, that marks an edge
// Thin features stay as drawn: a pixel that is the near side of a one-pixel-wide feature (in front of what lies on
// BOTH sides of it - a glyph of an in-world name tag, a weapon shaft, a line) or next to one is not smoothed - FXAA
// smears those into the background (an eyes still's "Commander" tag read "Cummanuer", Oct 15).
const float THIN_GAP = 0.2;          // blocks in front of both neighbours that make a pixel a thin feature
const float DEPTH_NOISE = 4e-6;      // perspective only: margin = this * view distance^2 (blocks)
const float FXAA_REDUCE_MIN = 1.0 / 128.0;
const float FXAA_REDUCE_MUL = 1.0 / 8.0;
const float FXAA_SPAN_MAX = 8.0;

vec3 viewPos(vec2 uv, float depth) {
    vec4 v = InvProjMat * vec4(vec3(uv, depth) * 2.0 - 1.0, 1.0);
    return v.xyz / v.w;
}

// distance of b from the line through a and c: zero on any plane (ortho or perspective), large across an edge
float offLine(vec3 a, vec3 b, vec3 c) {
    vec3 ac = c - a;
    return length(cross(ac, b - a)) / max(length(ac), 1e-4);
}

// b (view z: larger = nearer, in both projections) stands clearly in front of both its neighbours a and c
bool thin(float a, float b, float c) {
    return b - a > THIN_GAP && b - c > THIN_GAP;
}

bool geometryEdge(vec2 uv, vec2 px, float depth) {
    // the outer two pixel rings: their clamped neighbours would bend any slanted plane into a false edge
    if (any(lessThan(uv, 2.5 * px)) || any(greaterThan(uv, 1.0 - 2.5 * px)))
        return false;
    vec2 ux = vec2(px.x, 0.0), uy = vec2(0.0, px.y);
    float dL = textureLod(DepthSampler, uv - ux, 0.0).r, dR = textureLod(DepthSampler, uv + ux, 0.0).r;
    float dD = textureLod(DepthSampler, uv - uy, 0.0).r, dU = textureLod(DepthSampler, uv + uy, 0.0).r;
    if (abs(dL + dR - 2.0 * depth) <= DEPTH_STEPS && abs(dD + dU - 2.0 * depth) <= DEPTH_STEPS)
        return false;                                   // a plane
    vec3 centre = viewPos(uv, depth);
    vec3 pL = viewPos(uv - ux, dL), pR = viewPos(uv + ux, dR), pD = viewPos(uv - uy, dD), pU = viewPos(uv + uy, dU);
    // one pixel's width at the centre's depth (ortho: the same everywhere; perspective: grows with distance)
    float footprint = length(viewPos(uv + ux, depth) - centre);
    float tolerance = EDGE_FOOTPRINTS * footprint + (Ortho > 0.5 ? 0.0 : DEPTH_NOISE * centre.z * centre.z);
    if (max(offLine(pL, centre, pR), offLine(pD, centre, pU)) <= tolerance)
        return false;
    if (thin(pL.z, centre.z, pR.z) || thin(pD.z, centre.z, pU.z))
        return false;                                   // on a thin feature
    float zLL = viewPos(uv - 2.0 * ux, textureLod(DepthSampler, uv - 2.0 * ux, 0.0).r).z;
    float zRR = viewPos(uv + 2.0 * ux, textureLod(DepthSampler, uv + 2.0 * ux, 0.0).r).z;
    float zDD = viewPos(uv - 2.0 * uy, textureLod(DepthSampler, uv - 2.0 * uy, 0.0).r).z;
    float zUU = viewPos(uv + 2.0 * uy, textureLod(DepthSampler, uv + 2.0 * uy, 0.0).r).z;
    return !(thin(zLL, pL.z, centre.z) || thin(centre.z, pR.z, zRR)       // next to one
        || thin(zDD, pD.z, centre.z) || thin(centre.z, pU.z, zUU));
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

// ---- glow ----
// Enchanted (lovish Oct 28: "dim it down a bit and make it look more saturated and whimsy ... fantasy esque"): the soft
// light-bloom of a painted fantasy scene. The bright-pass of 16 taps (golden-angle spiral, so no ring
// pattern shows) round the pixel, weighted toward the centre, screened onto it: light spills a little onto what is
// next to it. Cheap enough for one pass (16 reads, ~0.2 ms on a desktop GPU); off (Glow 0) for Subtle and Full.
vec3 glowAt(vec2 uv) {
    vec2 r = vec2(GlowRadius / Aspect, GlowRadius);
    vec3 sum = vec3(0.0);
    float wsum = 0.0;
    for (int i = 0; i < 16; i++) {
        float f = (float(i) + 0.5) / 16.0;
        float a = float(i) * 2.39996323;            // the golden angle
        vec2 o = vec2(cos(a), sin(a)) * sqrt(f) * r;
        vec3 s = texture(DiffuseSampler, uv + o).rgb;
        float l = dot(s, vec3(0.2126, 0.7152, 0.0722));
        float w = 1.0 - 0.7 * f;
        sum += s * clamp((l - GlowThreshold) / max(1.0 - GlowThreshold, 1e-3), 0.0, 1.0) * w;
        wsum += w;
    }
    return sum / wsum;
}

void main() {
    vec4 scene = texture(DiffuseSampler, texCoord);
    vec3 c = scene.rgb;
    float depth = texture(DepthSampler, texCoord).r;

    if (EdgeAA > 0.5) {
        vec2 px = 1.0 / vec2(textureSize(DiffuseSampler, 0));
        if (geometryEdge(texCoord, px, depth))
            c = fxaa(texCoord, px, c);
    }
    if (Glow > 0.0)
        c = 1.0 - (1.0 - c) * (1.0 - Glow * glowAt(texCoord));

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
    c *= Exposure;
    float l = dot(c, LUMA);
    float lc = l + Contrast * 8.0 * l * l * (1.0 - l) * (1.0 - l) * (2.0 * l - 1.0);
    c *= lc / max(l, 1e-4);
    float chroma = length(c - lc);
    // bell-shaped weights (peaks at 1/4 and 3/4 luma): black, white and the midtones stay neutral, so white rings and
    // damage numbers never turn cream and shadows never lift toward blue
    float shadows = smoothstep(0.0, 0.25, lc) - smoothstep(0.25, 0.5, lc);
    float highlights = smoothstep(0.5, 0.75, lc) - smoothstep(0.75, 1.0, lc);
    c *= mix(vec3(1.0), ShadowTint, shadows * SplitTone) * mix(vec3(1.0), HighlightTint, highlights * SplitTone);
    // the tint turns hue but never adds colour to what already had it (a warm tint on warm sand would): chroma is
    // capped at its value before the tint, except that near-greys may take the tint's own faint colour
    float lt = dot(c, LUMA);
    vec3 v = c - lt;
    c = lt + v * min(1.0, max(chroma, 0.1 * SplitTone * lt) / max(length(v), 1e-5));
    c = mix(vec3(lt), c, Saturation);
    // vibrance: colour for the dull pixels (grass, stone, bark) - a pixel's gain falls to nothing as its own
    // saturation (chroma / luma) reaches 0.6, so team colours, glows and the overlays' strong colours stay as drawn
    if (Vibrance != 0.0) {
        float lv = dot(c, LUMA);
        vec3 dv = c - lv;
        float sat = length(dv) / max(lv, 1e-3);
        c = lv + dv * (1.0 + Vibrance * clamp(1.0 - sat / 0.6, 0.0, 1.0));
    }

    // quartic falloff from the centre, 1 at the corners
    vec2 d = (texCoord - 0.5) * vec2(Aspect, 1.0);
    float r2 = dot(d, d) / (0.25 * (Aspect * Aspect + 1.0));
    c *= 1.0 - Vignette * r2 * r2;

    // one 8-bit step of interleaved-gradient dither: the curve and the faint vignette gradient never band
    c += (fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715)))) - 0.5) / 255.0;
    fragColor = vec4(clamp(c, 0.0, 1.0), scene.a);
}
