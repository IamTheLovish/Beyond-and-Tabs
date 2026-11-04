#version 120
// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// The shadow map: everything that casts a sun (or moon) shadow, seen from the light, fitted to the RTS view
// (lib/shadowfit.glsl). Grass, flowers and crops cast none: from above their hard shadows read as dark stars under
// every tuft (Itachi 1, Oct 28; #350). Our overlays already skip this pass (ShaderPacks.skipOverlays).
// Leaves move in the wind exactly as lit.vsh moves them (lib/wind.glsl, the same world position in), so a shimmering
// canopy's shadow shimmers with it instead of standing still under it.

#include "/lib/settings.glsl"
#include "/lib/shadowfit.glsl"
#include "/lib/wind.glsl"

uniform mat4 shadowModelViewInverse;

attribute vec4 mc_Entity;

varying vec2 texcoord;

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    if (abs(mc_Entity.x - ID_FOLIAGE) < 0.5 || abs(mc_Entity.x - ID_FOLIAGE_UPPER) < 0.5) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);   // outside the clip volume: the whole plant is dropped
        return;
    }
    gl_Position = ftransform();
#if WIND == 1
    if (abs(mc_Entity.x - ID_LEAVES) < 0.5) {
        // this pass's model view is the light's: its inverse gives the same player-space position the gbuffers see
        vec3 playerPos = (shadowModelViewInverse * (gl_ModelViewMatrix * gl_Vertex)).xyz;
        vec4 moved = gl_Vertex + vec4(windOffset(playerPos + cameraPosition, 0.0, 1.0), 0.0);
        gl_Position = gl_ModelViewProjectionMatrix * moved;
    }
#endif
    gl_Position.xy = shadowRemap(gl_Position.xy, shadowFit());
}
