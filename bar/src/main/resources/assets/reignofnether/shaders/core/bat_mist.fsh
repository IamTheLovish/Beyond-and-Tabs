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
uniform sampler2D VisSampler;       // fog of war round VisOrigin: r = 0 in sight, else the boost's share (see below)

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

// the wisp mask at a world position: 0 clear .. 1 a dense strand
float wisps(vec3 w) {
    vec3 q = w + Offset;
    // in the wind's frame: along, up, across - stretched along the wind so the strands stream with it
    vec3 k = vec3(dot(q.xz, WindDir), q.y, dot(q.xz, vec2(-WindDir.y, WindDir.x)));
    // a slow, large warp field that turns over time: the strands bend and curl instead of sliding as one sheet
    vec2 warp = vec2(vnoise(k * 0.03 + vec3(0.0, Time * 0.035, 0.0)),
                     vnoise(k * 0.03 + vec3(7.3, Time * 0.035 + 3.1, 2.9))) - 0.5;
    k.xz += warp * 16.0;
    // a second, finer warp: the strands' own curls and eddies
    vec2 curl = vec2(vnoise(k * 0.11 + vec3(1.7, Time * 0.09, 4.2)), vnoise(k * 0.11 + vec3(9.1, Time * 0.09, 0.6))) - 0.5;
    k.xz += curl * 4.0;
    float n = fbm(k * vec3(0.045, 0.2, 0.1) + vec3(0.0, Time * 0.02, 0.0));
    return smoothstep(0.4, 0.7, n);
}

float fogAt(vec2 xz) {
    vec2 uv = (xz - VisOrigin) * VisInvSize;
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0))))
        return 0.0;
    return texture(VisSampler, uv).r;
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
    float top = Floor + 3.0 * Height;                          // above this the layer is as good as clear
    if (surface.y >= top) {
        fragColor = scene;
        return;
    }
    vec3 toViewer = Ortho > 0.5 ? normalize((InvViewMat * vec4(0.0, 0.0, 1.0, 0.0)).xyz) : normalize(-rel);
    float toEye = Ortho > 0.5 ? 1e6 : length(rel);
    float toTop = toViewer.y > 1e-3 ? (top - surface.y) / toViewer.y : MAX_PATH;
    float len = min(min(toEye, toTop), MAX_PATH);
    float ds = len / float(STEPS);
    // interleaved-gradient jitter of the first sample: no banding between the steps
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    float boost = 1.0 + (FogBoostMax - 1.0) * fogAt(surface.xz);

    vec3 c = scene.rgb;
    float trans = 1.0;
    // back to front: from the surface toward the viewer
    for (int i = 0; i < STEPS; i++) {
        vec3 w = surface + toViewer * ((float(i) + jitter) * ds);
        float h = max(w.y - Floor, -6.0);
        float prof = exp(-h / Height);
        float shape = 0.15 + 1.8 * wisps(w);
        float dens = Density * boost * prof * shape;
        float a = 1.0 - exp(-Sigma * dens * ds);
        // lit on top and where it is thin; cool and dim in the thick low core
        float lit = clamp(0.15 + 0.6 * clamp(h / (2.0 * Height), 0.0, 1.0) + 0.35 * (1.0 - min(dens, 1.5) / 1.5), 0.0, 1.0);
        c = mix(c, mix(DeepColor, LitColor, lit), a);
        trans *= 1.0 - a;
    }
    // in sight a unit in a hollow must read: the cap is lower there; out of sight the mist may close in
    float cap = mix(MaxCover * 0.76, MaxCover * 1.08, clamp(fogAt(surface.xz), 0.0, 1.0));
    float cover = 1.0 - trans;
    float k = cover > cap ? cap / cover : 1.0;
    fragColor = vec4(mix(scene.rgb, c, k), scene.a);
}
