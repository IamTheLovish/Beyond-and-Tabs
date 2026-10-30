// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// Vertex stage of what is drawn as it is: lines and coloured shapes (UNLIT_BASIC), textured overlays, clouds and glows
// (textured), particles and weather lit by the lightmap only (UNLIT_LIGHTMAP).

varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;

void main() {
    gl_Position = ftransform();
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
    glcolor = gl_Color;
}
