// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// Vertex stage of vanilla's clouds (light pass 5): see clouds.fsh.

varying vec2 texcoord;
varying vec4 glcolor;
varying vec3 viewPos;

void main() {
    gl_Position = ftransform();
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    glcolor = gl_Color;
    viewPos = (gl_ModelViewMatrix * gl_Vertex).xyz;
}
