#version 150

// The silhouette composite (orthoview/CanopySilhouettes), drawn only over the screen tiles silhouettes touched.
// Two kinds of texel share the target:
//   alpha 1.0      a model silhouette (CanopyRenderTypes.silhouette, flat colour): fill inside, a rim made here from
//                  its neighbours on both sides of the shape's edge;
//   alpha 0 - 0.98 a capsule (bat_silhouette_capsule), already shaded with its own fill and rim: blended as it is.
// With no model silhouette this frame (Models = 0) the neighbour reads are skipped: one read per pixel.

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float FillAlpha;
uniform float RimAlpha;
uniform float RimPx;                // the rim's reach in pixels (1 at 1080p)
uniform float Models;               // 1: model silhouettes are in the target this frame

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 c = texture(DiffuseSampler, texCoord);
    if (Models < 0.5) {
        if (c.a <= 0.0)
            discard;
        fragColor = c;
        return;
    }
    if (c.a > 0.0 && c.a < 0.99) {   // a capsule texel
        fragColor = c;
        return;
    }
    vec2 px = RimPx / OutSize;
    vec3 sum = vec3(0.0);
    float filled = 0.0;
    float empty = 0.0;
    for (int i = 0; i < 8; i++) {
        float a = float(i) * 0.785398;   // eight directions, 45 degrees apart
        vec4 s = texture(DiffuseSampler, texCoord + vec2(cos(a), sin(a)) * px);
        if (s.a > 0.99) {
            sum += s.rgb;
            filled += 1.0;
        } else {
            empty += 1.0;
        }
    }
    if (c.a > 0.99) {
        // inside a model silhouette: the rim where it meets a non-model pixel, else the see-through fill
        fragColor = vec4(c.rgb, empty > 0.0 ? RimAlpha : FillAlpha);
    } else if (filled > 0.0) {
        // just outside one: the outer half of the rim, in the neighbours' colour
        fragColor = vec4(sum / filled, RimAlpha * 0.8);
    } else {
        discard;
    }
}
