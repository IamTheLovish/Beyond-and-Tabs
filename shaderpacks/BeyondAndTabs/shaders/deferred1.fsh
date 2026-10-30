#version 120
// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// Ambient occlusion, step 2 of 3 (lib/ao.glsl): a 4x4 blur at half resolution over deferred.fsh's result, the same
// 4x4 tile its rotations repeat in, so the 16 rotations average into one smooth value. A neighbour counts less the
// further its depth is from the pixel's (half a block in the RTS view, a little more with distance in first person):
// the occlusion at a unit's feet stays on the ground at its feet and does not smear onto the unit or the far ground.

#include "/lib/settings.glsl"
#include "/lib/ao.glsl"

uniform sampler2D colortex2;
uniform float viewWidth;
uniform float viewHeight;

const float AO_DEPTH_TOLERANCE = 0.5;  // blocks

/* DRAWBUFFERS:3 */
void main() {
#if AO == 1
    vec2 viewSize = vec2(viewWidth, viewHeight);
    vec2 cell = gl_FragCoord.xy;
    if (any(greaterThan(cell, viewSize * AO_SCALE + 1.0))) {
        gl_FragData[0] = vec4(1.0, 1e4, 0.0, 1.0);
        return;
    }
    vec2 centre = texture2D(colortex2, aoCellUv(cell, viewSize)).rg;
    float tolerance = AO_DEPTH_TOLERANCE + 0.02 * centre.y;
    float sum = 0.0;
    float weights = 0.0;
    for (int y = -2; y < 2; y++) {
        for (int x = -2; x < 2; x++) {
            vec2 s = texture2D(colortex2, aoCellUv(cell + vec2(float(x), float(y)), viewSize)).rg;
            float w = max(1.0 - abs(s.y - centre.y) / tolerance, 0.0) + 1e-4;
            sum += s.x * w;
            weights += w;
        }
    }
    gl_FragData[0] = vec4(sum / weights, centre.y, 0.0, 1.0);
#else
    gl_FragData[0] = vec4(1.0, 1e4, 0.0, 1.0);
#endif
}
