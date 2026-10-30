#version 120
// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// The shadow map keeps depth only; cut-out texels (leaf gaps, fences) cast nothing.

#include "/lib/settings.glsl"

uniform sampler2D texture;

varying vec2 texcoord;

void main() {
    if (texture2D(texture, texcoord).a < 0.1)
        discard;
    gl_FragData[0] = vec4(1.0);
}
