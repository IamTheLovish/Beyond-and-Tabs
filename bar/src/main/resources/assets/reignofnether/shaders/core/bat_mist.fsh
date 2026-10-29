#version 150

// The living valley mist as a volume (ambient/mist/MistPass; the numbers are MistMath's "the volume" block).
// lovish, Oct 29: "it needs to be deep and rich and look like its wisping around not just white blotches", and
// "the fod should be thicker in fog of war compared to not fog of war".
//
// Height fog over a floor near the ground, ray-marched through the depth buffer: from the surface each pixel shows,
// back toward the viewer, a dozen samples through the layer. A pixel looking through more mist gets more of it, so
// hollows fill and rises, walls and roofs poke out of it - the depth. Each sample's density is shaped by a domain-
// warped value noise stretched along the wind and drifting with it (the Offset the Java side accumulates), its warp
// field itself slowly turning: the mist streams and curls - the wisps. Lit from above: a sample high in the layer or
// in thin mist takes the sky's light, the thick low core goes cool and dim - the richness. Out of sight (the fog of
// war: VisSampler, 0 in sight .. 1 never explored) it lies MistMath.FOG_* times thicker.
// Composited back to front from the surface, capped at MaxCover so a unit in a hollow still reads. Runs before the
// world look pass (which grades it like the rest of the world); off with a shader pack (the sprites stand in).

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform sampler2D VisSampler;       // round VisOrigin: r = fog of war (0 in sight, else the boost's share), g = where the
                                    // mist lies (ValleyField.cover), b = the smoothed ground (ValleyField.encodeHeight)

uniform mat4 InvProjMat;            // inverse projection (RTS ortho or perspective)
uniform mat4 InvViewMat;            // inverse view rotation: view space -> camera-relative world
uniform vec3 CamPos;                // camera world position
uniform float Ortho;                // 1 in the RTS view (parallel rays)
uniform float Floor;                // world y of the mist floor
uniform float Height;               // blocks per e-fold above the floor (MistMath.HEIGHT)
uniform float Sigma;                // extinction per block at the floor (MistMath.SIGMA)
uniform float Density;              // the hour's density x the zoom fade (0..1)
uniform vec3 Offset;                // accumulated wind drift (blocks), added to the noise lookup
uniform vec2 WindDir;               // unit wind direction in x, z: the wisps are stretched along it
uniform float Time;                 // seconds, wrapped: the warp field turns slowly
uniform vec3 LitColor;              // the mist's top in the sky's light
uniform vec3 DeepColor;             // its thick, low core
uniform vec2 VisOrigin;             // world x, z of the fog texture's corner
uniform float VisInvSize;           // 1 / blocks the fog texture spans
uniform float FogBoostMax;          // the boost at r = 1 (MistMath.FOG_UNEXPLORED)
uniform float MaxCover;             // MistMath.MAX_COVER
uniform vec2 FlowDir;               // r11d: the one direction the strands stream in (ValleyField.flow)
uniform float ValleyBase;           // r11: the base of VisSampler's ground channel (ValleyField.encodeHeight)

in vec2 texCoord;
out vec4 fragColor;

const int STEPS = 12;
const float MAX_PATH = 48.0;        // blocks of ray through the layer at most

float hash(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.71, 0.113, 0.419));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float vnoise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i + vec3(1, 0, 0)), f.x),
                   mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x),
                   mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float fbm(vec3 p) {
    float n = 0.5 * vnoise(p);
    n += 0.25 * vnoise(p * 2.03 + vec3(17.1, 3.7, 9.2));
    n += 0.125 * vnoise(p * 4.07 + vec3(5.3, 11.9, 2.1));
    return n / 0.875;
}

// a world position in the wind's frame: along, up, across - stretched along the wind so the strands stream with it
vec3 windFrame(vec3 w, vec2 dir) {
    vec3 q = w + Offset;
    return vec3(dot(q.xz, dir), q.y, dot(q.xz, vec2(-dir.y, dir.x)));
}

// the warp at a point (wind frame): a slow, large field that turns over time, so the strands bend and curl instead of
// sliding as one sheet, plus a finer one for their own eddies. Once per pixel, at the middle of its ray (review, Oct
// 29: the two fields barely change across a step, and they were 4 of the 7 noise lookups in every step)
vec2 warpAt(vec3 k) {
    vec2 warp = vec2(vnoise(k * 0.03 + vec3(0.0, Time * 0.035, 0.0)),
                     vnoise(k * 0.03 + vec3(7.3, Time * 0.035 + 3.1, 2.9))) - 0.5;
    vec3 k2 = k + vec3(warp.x, 0.0, warp.y) * 16.0;
    vec2 curl = vec2(vnoise(k2 * 0.11 + vec3(1.7, Time * 0.09, 4.2)), vnoise(k2 * 0.11 + vec3(9.1, Time * 0.09, 0.6))) - 0.5;
    return warp * 16.0 + curl * 4.0;
}

// the wisp mask: 0 clear .. 1 a dense strand
float wisps(vec3 k, vec2 warp) {
    k.xz += warp;
    float n = fbm(k * vec3(0.045, 0.2, 0.1) + vec3(0.0, Time * 0.02, 0.0));
    return smoothstep(0.4, 0.7, n);
}

// r11: the whole texel - fog share, where the mist lies, the smoothed ground; off the texture: no mist, ground unknown
vec4 visAt(vec2 xz) {
    vec2 uv = (xz - VisOrigin) * VisInvSize;
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0))))
        return vec4(0.0, 0.0, -1.0, 0.0);
    return texture(VisSampler, uv);
}

// r11d: the strands stream along FlowDir - ONE direction for the view (ValleyField.flow, the river's own). r11-r11c took
// it per pixel from the cover's slope: turning the noise round world coordinates hundreds of blocks out sheared it
// wherever the direction changed - rims along the banks that read as ice floes (eyes r50 / r51b; mist lab, Oct 29)

// r9 (lovish, Oct 29: "mist needs to be improved still still looks fake"): fog BANKS, not a tinted film. r6-r8's
// exponential layer put a thin veil over everything - a grey filter laid over the scene, which is what read fake.
// Real valley fog seen from above is a cloud sea: dense banks with a billowing top surface lit by the sky, clear air
// between them, wispy torn edges, deeper banks over lower ground. Per sample:
//   cover   where banks lie: large, wind-stretched, slowly drifting patches (out of sight more of the ground is covered)
//   top     the bank's top surface over the floor: higher where the cover is strong, billowing on a finer noise
//   body    dense under the top, a soft 1.4-block top edge; zero above it
//   erosion the detail noise tears the edges into wisps and gives the inside its texture
// Lit from above: a sample near its bank's top takes LitColor, deeper inside the cool DeepColor; the bank's own top
// shades what lies under it (no extra noise: the same cover and top a little higher).
const float BANK_DENSITY = 2.5;     // r11b 1.7 -> 2.6: r48's banks too thin to read as mist from the RTS camera

struct Bank { float cover; float top; };

Bank bankAt(vec3 k, float fogShare, float local) {
    Bank b;
    // r11: the banks lie where ValleyField says (the water, the low banks, hollows); the large noise only breaks them
    // into strands and gaps, it never puts mist on open lawn. Out of sight a little more lies there (lovish, Oct 29)
    // r11d (mist lab variant g, matched to the approved paintover): strands ~17 blocks along, ~2 across
    float cov = fbm(vec3(k.x * 0.06, Time * 0.004, k.z * 0.45));
    // r11b (eyes r48: over the water one uniform faint sheet - 0.35 + 0.85 x cov saturated the body everywhere):
    // the large noise now opens real gaps between strands
    b.cover = local * (2.2 * cov - 0.9) + 0.12 * fogShare * smoothstep(0.0, 0.25, local);
    float billow = vnoise(vec3(k.x * 0.11, Time * 0.03, k.z * 0.2)) - 0.5;
    b.top = 1.0 + 3.6 * smoothstep(0.32, 0.78, b.cover) + 1.4 * billow;
    return b;
}

// r10 (r9's still, run 40834052650): the banks still read as paint - a hard rim where a bank meets bare ground, a
// dirty dark-grey underside, and dead clear air between banks. Real valley fog from above is low-contrast and
// continuous: banks rise out of a thin ground haze, their edges thin over several blocks, and their shaded sides are
// a lifted blue-grey, never smoke. So: wider top and cover ramps, a haze layer under and between the banks, the deep
// colour lifted toward the lit one, and the ground seen through mist desaturated (aerial perspective).
const float HAZE = 0.04;            // the ground haze's share of a bank's density at the floor
const float HAZE_HEIGHT = 1.6;      // blocks per e-fold of the haze (no growth below the floor: the r8 lesson)
const float DEEP_LIFT = 0.9;        // how far the shaded core is lifted toward the lit colour (r11c 0.45 -> 0.8: eyes r50's
                                    // core was darker than the water under it - mist darkened the river)

float bankBody(Bank b, float h, float detail) {
    // r11c (eyes r50: the banks read as glass slabs - a top cut off over 3 blocks draws its contour as a line): the
    // top thins over ~6 blocks, and the detail noise erodes it, so the edge is a fade, never a rim
    float body = smoothstep(b.top + 2.5 + 1.5 * detail, b.top - 3.5, h);
    return body * smoothstep(0.1, 0.45, b.cover + 0.6 * (detail - 0.5));
}

float hazeAt(float h, float detail) {
    return HAZE * exp(-max(h, 0.0) / HAZE_HEIGHT) * (0.6 + 0.8 * detail);
}

void main() {
    vec4 scene = texture(DiffuseSampler, texCoord);
    float depth = texture(DepthSampler, texCoord).r;
    if (depth >= 1.0 || Density <= 0.001) {     // the sky / the RTS void: never misted
        fragColor = scene;
        return;
    }
    vec4 v = InvProjMat * vec4(vec3(texCoord, depth) * 2.0 - 1.0, 1.0);
    vec3 rel = (InvViewMat * vec4(v.xyz / v.w, 1.0)).xyz;       // camera-relative world
    vec3 surface = CamPos + rel;
    vec4 vis = visAt(surface.xz);
    float local = vis.g;
    if (local <= 0.004) {                                      // r11: open lawn - no mist, no film
        fragColor = scene;
        return;
    }
    // r11: measured from the smoothed ground (ValleyField), not one floor for the whole view - the mist follows the
    // valley, and its edge no longer draws every one-block terrace step
    float ground = vis.b >= 0.0 ? ValleyBase - 8.0 + vis.b * 32.0 : Floor;
    float top = ground + 10.5;   // the highest a bank's soft top reaches (r11c: 6.8 cut the fade)
    if (surface.y >= top) {
        fragColor = scene;
        return;
    }
    vec3 toViewer = Ortho > 0.5 ? normalize((InvViewMat * vec4(0.0, 0.0, 1.0, 0.0)).xyz) : normalize(-rel);
    float toEye = Ortho > 0.5 ? 1e6 : length(rel);
    // the ray starts no lower than half a block under the smoothed ground: a pixel one terrace down does not look
    // through an extra block of mist (the step), it looks through the same layer as its neighbour
    float t0 = toViewer.y > 1e-3 ? max(0.0, (ground - 0.5 - surface.y) / toViewer.y) : 0.0;
    float toTop = toViewer.y > 1e-3 ? (top - surface.y) / toViewer.y : MAX_PATH;
    float len = max(min(min(toEye, toTop), MAX_PATH) - t0, 0.0);
    float ds = len / float(STEPS);
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    float fogShare = clamp(vis.r, 0.0, 1.0);
    vec2 flow = FlowDir;
    float boost = 1.0 + (FogBoostMax - 1.0) * fogShare;
    vec2 warp = warpAt(windFrame(surface + toViewer * (t0 + 0.5 * len), flow));

    vec3 c = scene.rgb;
    float trans = 1.0;
    // back to front: from the surface toward the viewer
    for (int i = 0; i < STEPS; i++) {
        vec3 w = surface + toViewer * (t0 + (float(i) + jitter) * ds);
        vec3 k = windFrame(w, flow);
        k.xz += warp;
        float h = w.y - ground;
        Bank b = bankAt(k, fogShare, local);
        if (h > b.top + 4.0 && h > 3.0 * HAZE_HEIGHT)
            continue;                                          // clear air above this bank and the haze
        float detail = fbm(k * vec3(0.1, 0.3, 0.5) + vec3(0.0, Time * 0.05, 0.0));
        float body = bankBody(b, h, detail);
        float dens = Density * sqrt(boost) * (BANK_DENSITY * body + local * hazeAt(h, detail));
        float a = 1.0 - exp(-Sigma * dens * ds);
        // light: bright at the bank's top, cool and dim deeper in; the bank above a sample shades it
        // r11c: lit by how much mist lies above the sample, not like a surface - r9-r11b's top band and billow slope
        // light drew bright rims and dark outlines (eyes r50: ice floes, not mist). Thin mist is bright, the thick
        // core a little cooler; no outlines anywhere
        float shade = bankBody(b, h + 1.5, detail);
        float lit = clamp(0.95 - 0.35 * shade * body, 0.0, 1.0);
        vec3 deep = mix(DeepColor, LitColor, DEEP_LIFT);
        c = mix(c, mix(deep, LitColor, lit), a);
        trans *= 1.0 - a;
    }
    // aerial perspective: what shows through the mist loses colour with the mist's cover (lit through, not tinted)
    float seen = 1.0 - trans;
    float luma = dot(scene.rgb, vec3(0.2126, 0.7152, 0.0722));
    vec3 washed = mix(scene.rgb, vec3(luma), 0.35 * seen);      // r11: 0.6 greyed the ground under any mist
    c += (washed - scene.rgb) * trans;          // back-to-front mixing leaves scene x trans in c: wash that share only
    // in sight a unit in a hollow must read: the cap is lower there; out of sight the mist may close in
    // r10b (lab A/B): a hard cap turned every deep hollow into one flat grey pond - every pixel hit the same cover.
    // A soft knee instead, and the cap itself streaked by the wisp noise at the surface, so a full pool keeps its
    // thicker and thinner strands.
    vec3 ks = windFrame(surface, flow);
    ks.xz += warp;
    float streak = fbm(ks * vec3(0.16, 0.3, 0.5) + vec3(0.0, Time * 0.04, 0.0));
    // r11d: the cap only clips the thickest strands (a unit in a ford still reads); r10b-r11c's soft knee scaled ALL
    // the mist down, which turned every bank into one pale grey veil (mist lab: no cap = white strands, clear gaps)
    float cap = MaxCover * (0.9 + 0.2 * streak);
    float cover = 1.0 - trans;
    float kc = cover > cap ? cap / cover : 1.0;
    fragColor = vec4(mix(scene.rgb, c, kc), scene.a);
}
