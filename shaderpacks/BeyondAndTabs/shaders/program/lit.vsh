// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// Vertex stage of everything lit by the sun: terrain (LIT_TERRAIN), translucent terrain and water (LIT_WATER),
// entities and block entities (LIT_ENTITY), the first-person hand (LIT_HAND).

#include "/lib/settings.glsl"
#include "/lib/shadowfit.glsl"
#include "/lib/pbrswitch.glsl"
#include "/lib/wind.glsl"

attribute vec4 mc_Entity;
#ifdef LIT_TERRAIN
attribute vec2 mc_midTexCoord;         // the centre of this quad's sprite in the atlas (the loader's)
#endif
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
varying float plantUp;                 // a plant's height up its sprite: 0 at the foot, 1 at the top (FOLIAGE_BASE_AO)
varying float groundMaterial;          // the ground's shade tint's share (light pass 8): 1 ground, 0.5 plants, else 0
varying float asDrawn;
varying float waterBody;              // 1 on water itself (block.properties), 0 on glass, ice and the rest of
                                       // the translucent terrain
#ifdef LIT_PBR
varying vec3 vT;                       // the tangent frame for the _n map (lib/pbr.glsl), view space
varying vec3 vB;
#endif

#if defined LIT_TERRAIN && WIND == 1
// how much of the wind's sway this terrain vertex takes (lib/wind.glsl): x the plant's share, y the leaves'
vec2 windShare() {
    float id = mc_Entity.x;
    if (abs(id - ID_LEAVES) < 0.5)
        return vec2(0.0, 1.0);
    bool lower = abs(id - ID_FOLIAGE) < 0.5;
    if (!lower && abs(id - ID_FOLIAGE_UPPER) >= 0.5)
        return vec2(0.0);
    // the foot stays put: a vertex on the sprite's top half (v above the sprite's centre; v runs down the texture) is
    // the top of the plane. A two-block plant's upper half starts where the lower half's top is and bends further.
    // A sprite lying flat on the ground (pink petals) does not sway: its "top" is just one side
    float top = gl_MultiTexCoord0.y < mc_midTexCoord.y ? 1.0 : 0.0;
    float plant = lower ? top : 1.0 + 0.7 * top;
    return vec2(plant * (1.0 - abs(gl_Normal.y)), 0.0);
}
#endif

void main() {
    gl_Position = ftransform();
    viewPos = (gl_ModelViewMatrix * gl_Vertex).xyz;
#if defined LIT_TERRAIN && WIND == 1
    // terrain's model space has the world's axes, offset per chunk: an offset in blocks adds to the vertex as it is.
    // The moved position is the one lit and shadowed, so a swaying tuft is shaded where it is drawn
    vec2 share = windShare();
    if (share.x + share.y > 0.0) {
        vec3 playerPos = (gbufferModelViewInverse * vec4(viewPos, 1.0)).xyz;
        vec4 moved = gl_Vertex + vec4(windOffset(playerPos + cameraPosition, share.x, share.y), 0.0);
        gl_Position = gl_ModelViewProjectionMatrix * moved;
        viewPos = (gl_ModelViewMatrix * moved).xyz;
    }
#endif
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
    foliage = abs(mc_Entity.x - ID_FOLIAGE) < 0.5 || abs(mc_Entity.x - ID_FOLIAGE_UPPER) < 0.5 ? 1.0 : 0.0;
    // v runs down the texture: a vertex above the sprite's centre is the plane's top; a two-block plant's upper half
    // is all top. Interpolated, 0 at the foot to 1 at the top
    plantUp = abs(mc_Entity.x - ID_FOLIAGE_UPPER) < 0.5 || gl_MultiTexCoord0.y < mc_midTexCoord.y ? 1.0 : 0.0;
    groundMaterial = abs(mc_Entity.x - ID_GROUND) < 0.5 ? 1.0 : 0.5 * foliage;
#else
    foliage = 0.0;
    plantUp = 1.0;
    groundMaterial = 0.0;
#endif
#ifdef LIT_WATER
    waterBody = abs(mc_Entity.x - ID_WATER) < 0.5 ? 1.0 : 0.0;
#else
    waterBody = 0.0;
#endif
}
