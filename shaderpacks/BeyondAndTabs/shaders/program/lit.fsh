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

/* DRAWBUFFERS:01 */
void main() {
    vec4 tex = texture2D(texture, texcoord);
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
        gl_FragData[1] = vec4(0.0, 0.0, 0.0, alpha);   // no ambient occlusion on a selection or placement box
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
    Material mat = readMaterial(texture2D(normals, texcoord), texture2D(specular, texcoord), lin, geomN, vT, vB,
                                !plane);
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
    }
    vec3 sunColour, skyColour;
    lightColours(sunColour, skyColour);
    vec3 c = lin * surfaceLight(n, facing, shadow, levels, ao, texAo, sunColour, skyColour);
    // the light that comes from the sky as a whole, which ambient occlusion shuts out (slice 3, composite.fsh): the
    // fill, and below a metal's or a gem's sky reflection
    vec3 ambient = lin * skyFill(n, levels, ao, texAo, skyColour);
#ifdef LIT_PBR
    // the RTS camera is orthographic: every pixel looks along the same direction, the view's -z, so the direction to
    // the eye is +z everywhere (-viewPos would point at an eye that does not exist). First person keeps the real eye
    vec3 v = orthoView() ? vec3(0.0, 0.0, 1.0) : normalize(-viewPos);
    // a metal's light is its reflection: little diffuse. A dielectric gives up what it reflects (F0, 4% for most of
    // ours), so the lawn's sheen does not lift the lawn off its noon value target (design, slice 1 bands)
    c *= mix(1.0 - mat.f0.g, METAL_DIFFUSE, mat.metal);
    ambient *= mix(1.0 - mat.f0.g, METAL_DIFFUSE, mat.metal);
    // the fog of war and a dark vertex colour dim the reflections as they dim the albedo (no light in the fog); the
    // brightest channel, so a biome's green tint does not count as darkening. A metal's F0 is its albedo, already tinted
    float tint = mix(max(glcolor.r, max(glcolor.g, glcolor.b)), 1.0, mat.metal);
    vec3 reflected = min(sunSpecular(mat, v, l) * sunArriving(shadow, levels, sunColour) * terminator,
                         vec3(SPEC_MAX));
    vec3 sheen = skyReflection(mat, v, levels.y, skyColour, sunColour) * (ao * texAo);
    reflected += sheen;
    c += reflected * tint;
    ambient += sheen * tint;
    // emission: the texture glows with its own colour, unlit (lanterns, spell glass, the Bone Dragon's thread)
    c += lin * mat.emission;
#endif

    gl_FragData[0] = vec4(c, alpha);
    // colortex1 R: the sky's share of this pixel's light. Water stays 0: its surface was not in the depth the
    // occlusion was found from, and blending its 0 in by its alpha fades the bed's occlusion under it
#ifdef LIT_WATER
    float skyShare = 0.0;
#else
    float skyShare = clamp(dot(ambient, LUMA) / max(dot(c, LUMA), 1e-5), 0.0, 1.0);
#endif
    gl_FragData[1] = vec4(skyShare, 0.0, 0.0, alpha);
}
