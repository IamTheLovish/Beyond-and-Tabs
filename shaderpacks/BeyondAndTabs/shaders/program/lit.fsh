// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// Fragment stage of everything lit by the sun (see lit.vsh). Forward lighting into colortex0 in linear light; the tone
// curve, haze and grade follow in composite and final.
// The vertex colour carries the biome tint and the fog of war's tint (fogofwar/FogExploration bakes it into the chunk
// mesh): it multiplies the albedo, so it darkens the sun, the sky fill and the torch light alike - fogged ground stays
// as dark as the mod made it.

#include "/lib/settings.glsl"
#include "/lib/color.glsl"
#include "/lib/shadowfit.glsl"
#include "/lib/shadow.glsl"
#include "/lib/light.glsl"
#include "/lib/pbrswitch.glsl"
#if CLOUD_SHADOWS == 1
#include "/lib/wind.glsl"
#include "/lib/clouds.glsl"
#endif
#ifdef LIT_PBR
#include "/lib/pbr.glsl"
#endif

uniform sampler2D texture;
#ifdef LIT_PBR
uniform sampler2D normals;             // the _n and _s maps beside each texture (the loader binds flat defaults where
uniform sampler2D specular;            // a texture has none)
#endif
#ifdef LIT_ENTITY
uniform vec4 entityColor;              // the hurt / creeper flash
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
varying vec3 vT;
varying vec3 vB;
#endif

/* DRAWBUFFERS:0 */
void main() {
    // sharper texture detail under the RTS camera ("better resolution", lovish Oct 30): it is orthographic at ~30-36
    // screen pixels a block, so a 128 px block texture is drawn from the atlas's mip 2 (~4 texels to a pixel) and
    // reads soft. A negative bias moves the read toward mip 1 - the atlas blends between levels, so a half step is
    // half way - for albedo, relief and material alike (a sharper albedo over blurred relief would look painted on).
    // First person and everything but terrain keep the hardware's choice. The README has the shimmer trade-off
#ifdef LIT_TERRAIN
    float lodBias = orthoView() ? -TEXTURE_SHARPNESS : 0.0;
#else
    float lodBias = 0.0;
#endif
    vec4 tex = texture2D(texture, texcoord, lodBias);
    vec3 albedo = tex.rgb * glcolor.rgb;
#if defined LIT_TERRAIN || defined LIT_WATER
    float ao = glcolor.a;              // separateAo: the vertex AO, not an alpha
    float alpha = tex.a;
#else
    float ao = 1.0;
    float alpha = tex.a * glcolor.a;
#endif
#ifdef LIT_ENTITY
    if (asDrawn > 0.5) {               // an overlay box (lit.vsh): as drawn, fading all the way out
        if (alpha < 0.004)
            discard;
        gl_FragData[0] = vec4(toLinear(albedo), alpha);
        return;
    }
#endif
#ifndef LIT_WATER
    if (alpha < 0.1)
        discard;
#endif
#ifdef LIT_ENTITY
    albedo = mix(albedo, entityColor.rgb, entityColor.a);
#endif

    // grass, flowers and crops are crossed planes whose normals point sideways: lit as the ground they stand on, so a
    // tuft reads as part of the lawn (no dark patches on the planes turned from the sun)
    // a draw with no normal (all zero) would light as NaN, and the glow and edge smoothing would spread it: up instead
    // geomN is the surface's own normal: the shadow lookup's offset and the terminator use it, so a bump on a face
    // turned from the sun never catches light through the block; n, the mapped one, shades
    bool plane = foliage > 0.5 || dot(viewNormal, viewNormal) < 1e-6;
    vec3 geomN = plane ? normalize(upPosition) : normalize(viewNormal);
    vec3 l = normalize(shadowLightPosition);
    vec3 lin = toLinear(albedo);
    vec2 levels = lightLevels(lmcoord);
#ifdef LIT_PBR
    // the plants keep their up normal: their maps would tilt it back toward the planes' sideways look
    Material mat = readMaterial(texture2D(normals, texcoord, lodBias), texture2D(specular, texcoord, lodBias), lin,
                                geomN, vT, vB, !plane);
    vec3 n = mat.normal;
    float texAo = mat.ao;
#else
    vec3 n = geomN;
    float texAo = 1.0;
#endif
    float facing = sunFacing(n, l);
#ifdef LIT_PBR
    // past the face's own terminator no bump is lit (a soft step, for the smooth normals of round entity parts)
    float terminator = clamp(dot(geomN, l) * 20.0, 0.0, 1.0);
    facing *= terminator;
#endif
    float shadow = 0.0;
    if (facing > 0.0) {
        vec3 playerPos = (gbufferModelViewInverse * vec4(viewPos, 1.0)).xyz;
        vec3 worldNormal = mat3(gbufferModelViewInverse) * geomN;
        shadow = sunShadow(playerPos, worldNormal, fit);
#if CLOUD_SHADOWS == 1
        // a passing cloud takes some of the sun (and its glints, which read the same term), never the sky's fill
        shadow *= cloudShade(playerPos + cameraPosition, normalize(mat3(gbufferModelViewInverse) * l));
#endif
    }
    vec3 sunColour, skyColour;
    lightColours(sunColour, skyColour);
    vec3 c = lin * surfaceLight(n, facing, shadow, levels, ao, texAo, sunColour, skyColour);
#ifdef LIT_PBR
    // the RTS camera is orthographic: every pixel looks along the same direction, the view's -z, so the direction to
    // the eye is +z everywhere (-viewPos would point at an eye that does not exist). First person keeps the real eye
    vec3 v = orthoView() ? vec3(0.0, 0.0, 1.0) : normalize(-viewPos);
    // a metal's light is its reflection: little diffuse. A dielectric gives up what it reflects (F0, 4% for most of
    // ours), so the lawn's sheen does not lift the lawn off its noon value target (design, slice 1 bands)
    c *= mix(1.0 - mat.f0.g, METAL_DIFFUSE, mat.metal);
    // the fog of war and a dark vertex colour dim the reflections as they dim the albedo (no light in the fog); the
    // brightest channel, so a biome's green tint does not count as darkening. A metal's F0 is its albedo, already tinted
    float tint = mix(max(glcolor.r, max(glcolor.g, glcolor.b)), 1.0, mat.metal);
    vec3 reflected = min(sunSpecular(mat, v, l) * sunArriving(shadow, levels, sunColour) * terminator,
                         vec3(SPEC_MAX));
    reflected += skyReflection(mat, v, levels.y, skyColour, sunColour) * (ao * texAo);
    c += reflected * tint;
    // emission: the texture glows with its own colour, unlit (lanterns, spell glass, the Bone Dragon's thread)
    c += lin * mat.emission;
#endif

    gl_FragData[0] = vec4(c, alpha);
}
