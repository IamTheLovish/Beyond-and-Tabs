// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// Vertex stage of everything lit by the sun: terrain (LIT_TERRAIN), translucent terrain and water (LIT_WATER),
// entities and block entities (LIT_ENTITY), the first-person hand (LIT_HAND).

#include "/lib/settings.glsl"
#include "/lib/shadowfit.glsl"

attribute vec4 mc_Entity;

varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;
varying vec3 viewPos;
varying vec3 viewNormal;
varying vec3 fit;
varying float foliage;

void main() {
    gl_Position = ftransform();
    viewPos = (gl_ModelViewMatrix * gl_Vertex).xyz;
    viewNormal = normalize(gl_NormalMatrix * gl_Normal);
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
    glcolor = gl_Color;
    fit = shadowFit();
#ifdef LIT_TERRAIN
    foliage = abs(mc_Entity.x - ID_FOLIAGE) < 0.5 ? 1.0 : 0.0;
#else
    foliage = 0.0;
#endif
}
