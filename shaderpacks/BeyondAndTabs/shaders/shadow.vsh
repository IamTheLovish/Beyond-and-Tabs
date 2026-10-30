#version 120
// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// The shadow map: everything that casts a sun (or moon) shadow, seen from the light, fitted to the RTS view
// (lib/shadowfit.glsl). Plants cast too (light pass 7; #350 had dropped them). Our overlays already skip this pass
// (ShaderPacks.skipOverlays).
// Leaves move in the wind exactly as lit.vsh moves them (lib/wind.glsl, the same world position in), so a shimmering
// canopy's shadow shimmers with it instead of standing still under it.

#include "/lib/settings.glsl"
#include "/lib/shadowfit.glsl"
#include "/lib/wind.glsl"

uniform mat4 shadowModelViewInverse;

attribute vec4 mc_Entity;
attribute vec2 mc_midTexCoord;         // the centre of this quad's sprite (a plant's foot stays put in the wind)

varying vec2 texcoord;

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    // light pass 7: plants cast shadows again (the benchmark's tufts do; dropped since #350, when hard shadows read as
    // dark stars - the penumbra is soft now), and sway here exactly as lit.vsh sways them (the same windShare rule)
    gl_Position = ftransform();
#if WIND == 1
    float id = mc_Entity.x;
    bool leaves = abs(id - ID_LEAVES) < 0.5;
    bool lower = abs(id - ID_FOLIAGE) < 0.5;
    bool upper = abs(id - ID_FOLIAGE_UPPER) < 0.5;
    if (leaves || lower || upper) {
        float top = gl_MultiTexCoord0.y < mc_midTexCoord.y ? 1.0 : 0.0;
        float plant = lower ? top : (upper ? 1.0 + 0.7 * top : 0.0);
        plant *= 1.0 - abs(gl_Normal.y);
        // this pass's model view is the light's: its inverse gives the same player-space position the gbuffers see
        vec3 playerPos = (shadowModelViewInverse * (gl_ModelViewMatrix * gl_Vertex)).xyz;
        vec4 moved = gl_Vertex + vec4(windOffset(playerPos + cameraPosition, plant, leaves ? 1.0 : 0.0), 0.0);
        gl_Position = gl_ModelViewProjectionMatrix * moved;
    }
#endif
    gl_Position.xy = shadowRemap(gl_Position.xy, shadowFit());
}
