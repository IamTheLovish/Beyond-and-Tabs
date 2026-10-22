#version 150

// Capsule silhouettes (orthoview/CanopySilhouettes): a rounded capsule in the unit's colour, its rim drawn here (an
// antialiased band of the distance field) so the composite only has to blend - one texture read per pixel. The depth
// test (GREATER, set by the render type) has already kept only the pixels with a leaf or wall in front.

uniform float FillAlpha;
uniform float RimAlpha;

in vec4 vertexColor;
in vec2 shape;

out vec4 fragColor;

void main() {
    float a = vertexColor.a * 4.0;                       // half height / half width
    vec2 p = vec2(shape.x, max(abs(shape.y) - (a - 1.0), 0.0));
    float d = length(p) - 1.0;                           // < 0 inside, in half-widths
    float px = fwidth(d);
    if (d > px)
        discard;
    float rim = smoothstep(-0.30 - px, -0.30 + px, d);  // the outer 30 % of the half-width is rim
    float edge = 1.0 - smoothstep(-px, px, d);           // antialiased outline
    float alpha = mix(FillAlpha, RimAlpha, rim) * edge;
    if (alpha <= 0.004)
        discard;
    // the composite blends this texel as it is: alpha 1.0 is reserved for the model silhouettes (see composite.fsh)
    fragColor = vec4(vertexColor.rgb, min(alpha, 0.98));
}
