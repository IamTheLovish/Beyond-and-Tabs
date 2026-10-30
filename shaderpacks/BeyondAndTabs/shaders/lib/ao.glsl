// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// Slice 3's ambient occlusion (design/own_shader_pack.md), shared by its three steps:
//   deferred.fsh   after the opaque world is drawn (before water): how much of the sky each pixel's surface sees,
//                  from the depth buffer, at half resolution (8 taps a pixel, rotated in a 4x4 pattern);
//   deferred1.fsh  a 4x4 blur that stops at depth edges (it averages the 16 rotations back into a smooth result
//                  without bleeding a unit's occlusion onto the ground behind it);
//   composite.fsh  back to full resolution, picking the half-resolution neighbours at the pixel's own depth, and
//                  applied to the sky's share of the pixel's light only (colortex1, written by the lit draws): a
//                  crease shuts out sky, not the sun, so a sunlit wall base darkens a little and a shaded one more,
//                  as real light does.
// The radius is in blocks. The RTS camera is orthographic, so a block is the same number of pixels everywhere on screen
// and the projection's scale converts blocks to screen directly; in first person it is divided by the distance. Either
// way a building base darkens over the same ~1.5 blocks at every zoom.
// The half-resolution passes render into the bottom-left quarter of their buffers (shaders.properties
// scale.deferred=0.5). They find their pixel from gl_FragCoord, not from the quad's texcoord, so a loader that ignores
// the scale directive still fills the same quarter correctly (and leaves the rest unused).

#ifndef AO_GLSL
#define AO_GLSL

const float AO_SCALE = 0.5;            // the passes' resolution, of the screen's
const float AO_RADIUS = 1.5;           // blocks: how far round a pixel an occluder counts
// the response, calibrated on a ray-traced model of the RTS camera (65 degrees down, ortho) at AO_STRENGTH 1: open
// ground 2 or more blocks from a 4-block wall 1.0; half a block from its foot 0.55 (the floor) on the side facing the
// camera, 0.69 beside a wall seen edge-on; 1 block 0.90; ground half a block from a unit's feet 0.81. In linear light,
// so on screen the shaded ground half a block from a wall reads ~0.76-0.85 of the open shade (design: 0.75-0.90)
const float AO_GAIN = 1.2;
// the darkest the occlusion takes the sky's fill: a narrow alley in shade stays readable (the judged shadow floor,
// tools/shader_judge.py: darkest shade >= 0.35 of the sunlit lawn)
const float AO_FLOOR = 0.55;
// an occluder must rise this far above the surface's own plane (as the cosine of its elevation) to count: the depth
// buffer's steps on flat ground never darken it
const float AO_BIAS = 0.10;
const int AO_TAPS = 8;

bool aoOrtho(mat4 projection) {
    return projection[2][3] == 0.0 && projection[3][3] == 1.0;
}

vec3 aoViewPos(mat4 projectionInverse, vec2 uv, float depth) {
    vec4 v = projectionInverse * vec4(vec3(uv, depth) * 2.0 - 1.0, 1.0);
    return v.xyz / v.w;
}

// buffer uv of half-resolution pixel `cell` (pixel centres at +0.5) in a full-size buffer the passes fill the
// bottom-left quarter of: one half-resolution pixel is one texel
vec2 aoCellUv(vec2 cell, vec2 viewSize) {
    vec2 last = floor(viewSize * AO_SCALE) - 0.5;
    return clamp(cell, vec2(0.5), last) / viewSize;
}

#endif
