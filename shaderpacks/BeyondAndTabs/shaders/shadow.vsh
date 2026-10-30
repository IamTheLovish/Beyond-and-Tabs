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
varying float plantCaster;             // 1 for plants: shadow.fsh keeps only their solid texels

void main() {
    plantCaster = abs(mc_Entity.x - ID_FOLIAGE) < 0.5 || abs(mc_Entity.x - ID_FOLIAGE_UPPER) < 0.5 ? 1.0 : 0.0;
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    // light pass 7: plants cast shadows again (the benchmark's tufts do; dropped since #350, when hard shadows read as
    // dark stars - the penumbra is soft now), and sway here exactly as lit.vsh sways them (the same windShare rule)
    gl_Position = ftransform();
    vec3 offset = vec3(0.0);
    float id = mc_Entity.x;
    bool leaves = abs(id - ID_LEAVES) < 0.5;
    bool lower = abs(id - ID_FOLIAGE) < 0.5;
    bool upper = abs(id - ID_FOLIAGE_UPPER) < 0.5;
    if (leaves || lower || upper) {
        // this pass's model view is the light's: its inverse gives the same player-space position the gbuffers see
        vec3 playerPos = (shadowModelViewInverse * (gl_ModelViewMatrix * gl_Vertex)).xyz;
        vec3 world = playerPos + cameraPosition;
#if WIND == 1
        float top = gl_MultiTexCoord0.y < mc_midTexCoord.y ? 1.0 : 0.0;
        float plant = lower ? top : (upper ? 1.0 + 0.7 * top : 0.0);
        plant *= 1.0 - abs(gl_Normal.y);
        offset = windOffset(playerPos + cameraPosition, plant, leaves ? 1.0 : 0.0);
#endif
        if (lower || upper) {
            // light pass 9: a plant's shadow caster is drawn smaller than the plant (PLANT_SHADOW_SHRINK across, toward
            // its block's centre; PLANT_SHADOW_HEIGHT of its height): pass 7's full sprite threw a splat as big as the
            // tuft, pass 8's thinning removed it altogether. A solid, smaller shadow, offset in the sun's direction
            vec2 centre = floor(world.xz) + 0.5;
            offset.xz += (centre - world.xz) * (1.0 - PLANT_SHADOW_SHRINK);
            float foot = floor(world.y + 0.01);
            offset.y += (foot - world.y) * (lower ? 1.0 - PLANT_SHADOW_HEIGHT : 0.0);
        }
        gl_Position = gl_ModelViewProjectionMatrix * (gl_Vertex + vec4(offset, 0.0));
    }
    gl_Position.xy = shadowRemap(gl_Position.xy, shadowFit());
}
