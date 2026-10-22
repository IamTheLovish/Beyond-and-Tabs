#version 150

// The silhouette composite (orthoview/CanopySilhouettes): the hidden parts of units, in their flat colours, blended over
// the finished picture - a see-through fill so the canopy still reads as a canopy, and a solid rim on both sides of the
// shape's edge so even a unit a few pixels tall stands out. Empty pixels (alpha 0) are discarded.

uniform sampler2D DiffuseSampler;   // the silhouette target: unit colour, alpha 1 where a hidden part is
uniform vec2 OutSize;               // its size in pixels
uniform float FillAlpha;
uniform float RimAlpha;
uniform float RimPx;                // the rim's reach in pixels (1 at 1080p)

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 c = texture(DiffuseSampler, texCoord);
    vec2 px = RimPx / OutSize;
    vec3 sum = vec3(0.0);
    float filled = 0.0;
    float empty = 0.0;
    for (int i = 0; i < 8; i++) {
        float a = float(i) * 0.785398;   // eight directions, 45 degrees apart
        vec4 s = texture(DiffuseSampler, texCoord + vec2(cos(a), sin(a)) * px);
        if (s.a > 0.5) {
            sum += s.rgb;
            filled += 1.0;
        } else {
            empty += 1.0;
        }
    }
    if (c.a > 0.5) {
        // inside the shape: the rim where it meets an empty pixel, else the see-through fill
        fragColor = vec4(c.rgb, empty > 0.0 ? RimAlpha : FillAlpha);
    } else if (filled > 0.0) {
        // just outside it: the outer half of the rim, in the neighbours' colour
        fragColor = vec4(sum / filled, RimAlpha * 0.8);
    } else {
        discard;
    }
}
