#version 120
// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// Ambient occlusion, step 1 of 3 (lib/ao.glsl): how much of the sky a surface sees, at half resolution, from the depth
// of the opaque world (water is drawn after this pass, so a lake's surface never occludes its shore).
// For each pixel: its position and normal from depth, then 8 points round it within AO_RADIUS blocks in the view plane;
// each point the depth buffer shows standing above the surface's plane shuts out sky in proportion to the cosine of its
// elevation (a wall beside the pixel: a lot; a gentle slope: little), fading with its distance along the surface and
// ignored when it towers far above (a dragon flying over a unit is not a crease). Out: R the sky seen (1 = open),
// G the view depth in blocks, for the blur and the upsampling to stop at depth edges.

#include "/lib/settings.glsl"
#include "/lib/ao.glsl"

/*
const int colortex2Format = RG16F;
const int colortex3Format = RG16F;
*/

uniform sampler2D depthtex0;
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;
uniform float viewWidth;
uniform float viewHeight;

vec3 posAt(vec2 uv) {
    return aoViewPos(gbufferProjectionInverse, uv, texture2D(depthtex0, uv).r);
}

/* DRAWBUFFERS:2 */
void main() {
#if AO == 1
    vec2 viewSize = vec2(viewWidth, viewHeight);
    vec2 uv = gl_FragCoord.xy / (viewSize * AO_SCALE);
    float depth = texture2D(depthtex0, uv).r;
    if (uv.x > 1.0 || uv.y > 1.0 || depth >= 1.0) {
        gl_FragData[0] = vec4(1.0, 1e4, 0.0, 1.0);
        return;
    }
    vec3 p = aoViewPos(gbufferProjectionInverse, uv, depth);
    // the normal from the neighbours one full-resolution pixel away, each axis from the side nearer in depth, so a
    // pixel on a unit's silhouette takes the unit's face, not the jump to the ground behind
    vec2 px = 1.0 / viewSize;
    vec3 pr = posAt(uv + vec2(px.x, 0.0)) - p, pl = p - posAt(uv - vec2(px.x, 0.0));
    vec3 pu = posAt(uv + vec2(0.0, px.y)) - p, pd = p - posAt(uv - vec2(0.0, px.y));
    vec3 dx = abs(pr.z) < abs(pl.z) ? pr : pl;
    vec3 dy = abs(pu.z) < abs(pd.z) ? pu : pd;
    vec3 n = normalize(cross(dx, dy));
    bool ortho = aoOrtho(gbufferProjection);
    vec3 toEye = ortho ? vec3(0.0, 0.0, 1.0) : -p;
    if (dot(n, toEye) < 0.0)
        n = -n;

    // blocks to screen uv: the projection's scale (an ortho view: the same at every depth)
    vec2 uvPerBlock = 0.5 * vec2(gbufferProjection[0][0], gbufferProjection[1][1]);
    if (!ortho)
        uvPerBlock /= max(-p.z, 0.05);
    float radius = AO_RADIUS;
    // a 4x4 pattern of rotations, which deferred1's 4x4 blur averages back out exactly
    vec2 cell = mod(floor(gl_FragCoord.xy), 4.0);
    float turn = (cell.x + 4.0 * cell.y) / 16.0;
    float occlusion = 0.0;
    for (int i = 0; i < AO_TAPS; i++) {
        float a = float(i) * 2.39996323 + turn * 6.2831853;
        float r = radius * sqrt((float(i) + 0.5) / float(AO_TAPS));
        vec2 suv = uv + vec2(cos(a), sin(a)) * (r * uvPerBlock);
        if (suv.x < 0.0 || suv.y < 0.0 || suv.x > 1.0 || suv.y > 1.0)
            continue;
        float sd = texture2D(depthtex0, suv).r;
        if (sd >= 1.0)
            continue;
        vec3 v = aoViewPos(gbufferProjectionInverse, suv, sd) - p;
        float vv = dot(v, v);
        float h = dot(v, n);
        float rise = max(h * inversesqrt(vv + 1e-6) - AO_BIAS, 0.0) / (1.0 - AO_BIAS);
        float along = max(vv - h * h, 0.0);
        float reach = clamp(1.0 - along / (4.0 * radius * radius), 0.0, 1.0)
            * (1.0 - clamp((h - 3.0 * radius) / (2.0 * radius), 0.0, 1.0));
        occlusion += rise * reach;
    }
    float ao = 1.0 - AO_GAIN * AO_STRENGTH * occlusion * (2.0 / float(AO_TAPS));
    gl_FragData[0] = vec4(max(ao, AO_FLOOR), -p.z, 0.0, 1.0);
#else
    gl_FragData[0] = vec4(1.0, 1e4, 0.0, 1.0);
#endif
}
