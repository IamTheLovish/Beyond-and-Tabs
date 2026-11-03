#version 120
// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// The shadow map: everything that casts a sun (or moon) shadow, seen from the light, fitted to the RTS view
// (lib/shadowfit.glsl). Grass, flowers and crops cast none: from above their hard shadows read as dark stars under
// every tuft (Itachi 1, Oct 28; #350). Our overlays already skip this pass (ShaderPacks.skipOverlays).

#include "/lib/settings.glsl"
#include "/lib/shadowfit.glsl"

attribute vec4 mc_Entity;

varying vec2 texcoord;

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    if (abs(mc_Entity.x - ID_FOLIAGE) < 0.5) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);   // outside the clip volume: the whole plant is dropped
        return;
    }
    gl_Position = ftransform();
    gl_Position.xy = shadowRemap(gl_Position.xy, shadowFit());
}
