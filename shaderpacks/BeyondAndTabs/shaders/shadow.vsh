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
varying float plantTurned;             // 1 where the turn to a card below ran (SHADOW_PROBE paints it blue)

void main() {
    plantCaster = abs(mc_Entity.x - ID_FOLIAGE) < 0.5 || abs(mc_Entity.x - ID_FOLIAGE_UPPER) < 0.5 ? 1.0 : 0.0;
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    plantTurned = 0.0;
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
            // its block's centre; PLANT_SHADOW_HEIGHT of its height). Light pass 12: and turned to face the light - the
            // shadow probe showed each tuft casting a thin X, its two crossed quads seen edge-on from the sun. Both
            // quads become one card square to the light's horizontal direction, so the sprite's whole silhouette casts
            vec2 centre = floor(world.xz) + 0.5;
            vec2 o = world.xz - centre;
            vec3 lightDir = mat3(shadowModelViewInverse) * vec3(0.0, 0.0, 1.0);
            vec2 lh = lightDir.xz;
            vec2 across = dot(lh, lh) > 1e-6 ? normalize(vec2(-lh.y, lh.x)) : vec2(1.0, 0.0);
            // which end of its quad a vertex is. Light pass 14: by the sprite's u against its centre's, not by the
            // position against the block's centre - grass is drawn with a random XZ offset and a resource pack's model
            // need not run along the diagonals, and a vertex whose side was misread left the quad's own line (the
            // pass-12 close still: every tuft shadow still a thin cross along the world's diagonals). Half a block
            // each way, as vanilla's cross spans its block corner to corner (~0.64), x PLANT_SHADOW_SHRINK
            float end = gl_MultiTexCoord0.x < mc_midTexCoord.x ? -1.0 : 1.0;
            vec2 turned = across * (end * 0.64 * PLANT_SHADOW_SHRINK);
            plantTurned = 1.0;
            offset.xz += centre + turned - world.xz;
            float foot = floor(world.y + 0.01);
            offset.y += (foot - world.y) * (lower ? 1.0 - PLANT_SHADOW_HEIGHT : 0.0);
        }
        gl_Position = gl_ModelViewProjectionMatrix * (gl_Vertex + vec4(offset, 0.0));
    }
    gl_Position.xy = shadowRemap(gl_Position.xy, shadowFit());
}
