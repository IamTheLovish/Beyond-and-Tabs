// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// Vertex stage of everything lit by the sun: terrain (LIT_TERRAIN), translucent terrain and water (LIT_WATER),
// entities and block entities (LIT_ENTITY), the first-person hand (LIT_HAND).

#include "/lib/settings.glsl"
#include "/lib/shadowfit.glsl"
#include "/lib/pbrswitch.glsl"

attribute vec4 mc_Entity;
#ifdef LIT_PBR
attribute vec4 at_tangent;             // the loader's tangent along +u, w the handedness (OptiFine / Iris format)
#endif

varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;
varying vec3 viewPos;
varying vec3 viewNormal;
varying vec3 fit;
varying float foliage;
varying float asDrawn;
#ifdef LIT_PBR
varying vec3 vT;                       // the tangent frame for the _n map (lib/pbr.glsl), view space
varying vec3 vB;
#endif

void main() {
    gl_Position = ftransform();
    viewPos = (gl_ModelViewMatrix * gl_Vertex).xyz;
    viewNormal = gl_NormalMatrix * gl_Normal;   // normalised in lit.fsh, after its zero-normal guard
#ifdef LIT_PBR
    // the bitangent along +v, down the texture, where our maps point green (tools/materials.py): cross(N, T) times
    // the handedness. Both stay unnormalised here: a draw with no tangent hands over zeros, and lit.fsh keeps the
    // vertex normal for it
    vT = gl_NormalMatrix * at_tangent.xyz;
    vB = cross(viewNormal, vT) * (at_tangent.w < 0.0 ? -1.0 : 1.0);
#endif
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
    glcolor = gl_Color;
    fit = shadowFit();
    // our overlay boxes (MyRenderer.drawSolidBox: selection, checkpoints, building placement) come as translucent
    // entities with a packed block light of 255, which real lighting never produces (vanilla's brightest is 240):
    // they are drawn as drawn, not lit
#ifdef LIT_ENTITY
    asDrawn = gl_MultiTexCoord1.x > 248.0 && gl_MultiTexCoord1.y < 8.0 ? 1.0 : 0.0;
#else
    asDrawn = 0.0;
#endif
#ifdef LIT_TERRAIN
    foliage = abs(mc_Entity.x - ID_FOLIAGE) < 0.5 ? 1.0 : 0.0;
#else
    foliage = 0.0;
#endif
}
