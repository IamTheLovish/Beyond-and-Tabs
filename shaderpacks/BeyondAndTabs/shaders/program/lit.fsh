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
#ifdef LIT_TERRAIN
#include "/lib/wind.glsl"
#include "/lib/meadow.glsl"
#endif
#if CLOUD_SHADOWS == 1
#include "/lib/wind.glsl"
#include "/lib/clouds.glsl"
#endif
#ifdef LIT_PBR
#include "/lib/pbr.glsl"
#endif
#if defined LIT_WATER && WATER_REALISM == 1
#define WATER_SURFACE
#ifndef LIT_PBR
// the GGX glint and the sky in a reflection, shared with the materials
#include "/lib/pbr.glsl"
#endif
#include "/lib/water.glsl"
#endif

uniform sampler2D texture;
#ifdef LIT_PBR
uniform sampler2D normals;             // the _n and _s maps beside each texture (the loader binds flat defaults where
uniform sampler2D specular;            // a texture has none)
#endif
#ifdef WATER_SURFACE
// cameraPosition and frameTimeCounter: declared once, in lib/wind.glsl (include-guarded), as the cloud shadows read
// them too and a uniform declared twice does not compile
#include "/lib/wind.glsl"
uniform sampler2D depthtex1;           // the depth of the opaque world only: the bed under the water
uniform float viewWidth;
uniform float viewHeight;
uniform mat4 gbufferModelView;
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
varying float plantUp;
varying float asDrawn;
varying float waterBody;
#ifdef LIT_PBR
varying vec3 vT;
varying vec3 vB;
#endif

// colortex1: the sky's share of each pixel's light, for the ambient occlusion (slice 3, composite.fsh). Water also
// writes colortex5 (lib/water.glsl): composite reads it for the caustics and reflections. Oculus runs the GLSL
// preprocessor (comments kept) before it looks for the directive, so only the branch compiled in is seen
#ifdef LIT_WATER
/* DRAWBUFFERS:015 */
#else
/* DRAWBUFFERS:01 */
#endif
void main() {
    // sharper texture detail under the RTS camera ("better resolution", lovish Oct 30): it is orthographic at ~30-36
    // screen pixels a block, so a 128 px block texture is drawn from the atlas's mip 2 (~4 texels to a pixel) and
    // reads soft. A negative bias moves the read toward mip 1 - the atlas blends between levels, so a half step is
    // half way - for albedo, relief and material alike (a sharper albedo over blurred relief would look painted on).
    // First person and everything but terrain keep the hardware's choice. The README has the shimmer trade-off
#ifdef LIT_TERRAIN
    float lodBias = orthoView() ? -TEXTURE_SHARPNESS : 0.0;
    {
        // the grass (a coloured vertex tint) takes GRASS_SHARPNESS_SHARE of it (light pass 6: the lawn's grain)
        float hi = max(glcolor.r, max(glcolor.g, glcolor.b));
        float tint = (hi - min(glcolor.r, min(glcolor.g, glcolor.b))) / max(hi, 1e-3);
        lodBias *= mix(1.0, GRASS_SHARPNESS_SHARE, smoothstep(0.05, 0.15, tint));
    }
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
#ifdef LIT_TERRAIN
    // biome-tinted terrain (a coloured vertex tint: grass, ferns, leaves, vines; a flower's is white and the fog of
    // war's grey, so both pass through as they are)
    float tintChroma = max(glcolor.r, max(glcolor.g, glcolor.b)) - min(glcolor.r, min(glcolor.g, glcolor.b));
    float tinted = smoothstep(0.05, 0.15, tintChroma / max(max(glcolor.r, max(glcolor.g, glcolor.b)), 1e-3));
    // the meadow's patches (lib/meadow.glsl, light pass 3), on the world position, so a tuft follows its patch
    if (tinted > 0.0) {
        vec3 world = (gbufferModelViewInverse * vec4(viewPos, 1.0)).xyz + cameraPosition;
        albedo = mix(albedo, meadow(albedo, world.xz), tinted);
    }
    if (foliage > 0.5) {
        // a biome-tinted plant keeps its albedo's saturation inside the lawn's band, so a tuft is not neon lime (light
        // passes 2-3, lib/settings.glsl). Flowers keep their petals' colour
        float top = max(albedo.r, max(albedo.g, albedo.b));
        float sat = (top - min(albedo.r, min(albedo.g, albedo.b))) / max(top, 1e-4);
        float l = dot(albedo, LUMA);
        // HSV saturation scales with the distance from the grey of the same luma (for a fixed brightest channel)
        vec3 held = mix(vec3(l), albedo, min(1.0, FOLIAGE_MAX_SAT / max(sat, 1e-4)));
        albedo = mix(albedo, held, tinted);
        // the blades crowd at the foot: a little base occlusion, as the lawn's own texture has between its clumps
        ao *= mix(FOLIAGE_BASE_AO, 1.0, plantUp);
    }
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
#ifdef LIT_TERRAIN
    // no plant in block.properties' 10001 / 10003 glows, and the pack paints them no _s map: whatever the loader binds
    // in its place must never read as emission (a tuft drawn unlit-bright; light pass 2)
    if (foliage > 0.5)
        mat.emission = 0.0;
#endif
    vec3 n = mat.normal;
    float texAo = mat.ao;
#else
    vec3 n = geomN;
    float texAo = 1.0;
#endif
#ifdef LIT_TERRAIN
    // the grass and plants rake harder under a low sun than everything else (light pass 4, lib/light.glsl)
    float facing = mix(sunFacing(n, l), sunFacingRaked(n, l), tinted);
#else
    float facing = sunFacing(n, l);
#endif
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
    vec3 light = surfaceLight(n, facing, shadow, levels, ao, texAo, sunColour, skyColour);
    // near-white faces turned from up lose the light's colour (WHITE_WALL_NEUTRAL, light pass 3): white sides read
    // white, not cream, while the ground keeps the warm sun
    {
        float top = max(albedo.r, max(albedo.g, albedo.b));
        float whiteSat = (top - min(albedo.r, min(albedo.g, albedo.b))) / max(top, 1e-4);
        float whiteness = smoothstep(0.62, 0.75, dot(albedo, LUMA)) * (1.0 - smoothstep(0.06, 0.14, whiteSat));
        float side = 1.0 - clamp(dot(geomN, normalize(upPosition)), 0.0, 1.0);
        light = mix(light, vec3(dot(light, LUMA)), WHITE_WALL_NEUTRAL * whiteness * side);
    }
    vec3 c = lin * light;
    // the light that comes from the sky as a whole, which ambient occlusion shuts out (slice 3, composite.fsh): the
    // fill, and below a metal's or a gem's sky reflection
    vec3 ambient = lin * skyFill(n, levels, ao, texAo, sunColour, skyColour) * fillDirection(n);
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
    // a rough dielectric never glints past ROUGH_GLINT_CAP of SPEC_MAX, and no glint where the mapped normal turns
    // from the eye (light pass 3: nether bricks' bevels strobed white)
    float glossy = smoothstep(0.2, 0.6, 1.0 - sqrt(mat.roughness));
    float glintCap = SPEC_MAX * mix(mix(ROUGH_GLINT_CAP, 1.0, glossy), 1.0, mat.metal);
    vec3 reflected = min(sunSpecular(mat, v, l) * sunArriving(shadow, levels, sunColour) * terminator,
                         vec3(glintCap)) * smoothstep(0.0, 0.2, dot(n, v));
#ifdef LIT_TERRAIN
    // a plant's crossed planes lit as ground have no map: GGX at roughness 1 with Schlick's grazing Fresnel put a white
    // glint on every far tuft in a perspective view, the haze's "snow" (light pass 3). Plants show no sun glint
    reflected *= 1.0 - foliage;
#endif
    vec3 sheen = skyReflection(mat, v, levels.y, skyColour, sunColour) * (ao * texAo);
    reflected += sheen;
    c += reflected * tint;
    ambient += sheen * tint;
    // emission: the texture glows with its own colour, unlit (lanterns, spell glass, the Bone Dragon's thread)
    c += lin * mat.emission;
#endif

#ifdef WATER_SURFACE
    if (waterBody > 0.5) {
        // the bed under this pixel and the path the view travels through the water to reach it
        vec2 uv = gl_FragCoord.xy / vec2(viewWidth, viewHeight);
        float bedDepth = texture2D(depthtex1, uv).r;
        bool bed = bedDepth < 1.0;
        vec3 bedView = waterViewAt(uv, bedDepth);
        float path = bed ? length(bedView - viewPos) : WATER_NO_BED;
        // the lit water colour (slice 1's, which the palette was tuned on) is what deep water settles to; the bed
        // already drawn under it shows through by what is left
        float a = waterOpacity(path);
        c *= mix(SHALLOW_TINT, vec3(1.0), smoothstep(0.0, SHALLOW_TINT_PATH, path));

        // the ripples, on the top surface only (a waterfall's side keeps its face): world xz, so they stay put as the
        // camera pans
        vec3 playerPos = (gbufferModelViewInverse * vec4(viewPos, 1.0)).xyz;
        vec3 bedPlayer = (gbufferModelViewInverse * vec4(bedView, 1.0)).xyz;
        // the depth of water straight down to the bed, in blocks
        float below = bed ? max(playerPos.y - bedPlayer.y, 0.0) : WATER_NO_BED;
        bool top = (mat3(gbufferModelViewInverse) * geomN).y > 0.5;
        vec3 wn = top ? mat3(gbufferModelView) * waterNormal(playerPos.xz + cameraPosition.xz, frameTimeCounter)
                      : geomN;
        // the RTS camera is orthographic: one view direction for every pixel (as the materials' highlight)
        vec3 v = orthoView() ? vec3(0.0, 0.0, 1.0) : normalize(-viewPos);
        float fres = top ? waterFresnel(dot(wn, v)) : WATER_F0;
        c *= 1.0 + RIPPLE_SHADE * clamp(dot(wn, l) - dot(geomN, l), -0.25, 0.25);
        // the fog of war's darkening of the water's vertex colour, for what is lit without the albedo (foam, reflections)
        float fogDim = clamp(max(glcolor.r, max(glcolor.g, glcolor.b)) / WATER_TINT_BRIGHTEST, 0.0, 1.0);
        // the shore line: the body turns to lit foam and the layer thickens a little where the water runs out
        float foam = bed ? shoreFoam(below, playerPos.xz + cameraPosition.xz, frameTimeCounter) : 0.0;
        c = mix(c, toLinear(vec3(FOAM_ALBEDO)) * light * fogDim, foam);
        a = max(a, foam * FOAM_OPACITY);

        // what the surface reflects: the sky of this frame's light table (dimmed under a roof, as the materials' sky
        // sheen) and the sun's glint, which a bank's shadow on the water puts out
        vec3 reflected = skyInDirection(reflect(-v, wn), skyColour, sunColour) * (fres * levels.y);
        Material wm;
        wm.normal = wn;
        wm.ao = 1.0;
        wm.roughness = WATER_ROUGHNESS;
        wm.f0 = vec3(WATER_F0);
        wm.metal = 0.0;
        wm.emission = 0.0;
        reflected += min(sunSpecular(wm, v, l) * sunArriving(shadow, levels, sunColour), vec3(SPEC_MAX));
        reflected *= fogDim * (1.0 - foam);

        // Fresnel takes its share off what crosses the surface: the bed and the water body each show at (1 - F), the
        // reflection on top. One blend over the bed, dst (1 - A) + src A, with A = 1 - (1 - a)(1 - F)
        float A = 1.0 - (1.0 - a) * (1.0 - fres);
        c = (c * (a * (1.0 - fres)) + reflected) / max(A, 1e-3);
        alpha = A;

        float boost = 0.0;
#if CAUSTICS == 1
        // caustics on the bed: the sun's direction in the world, followed from the bed up to the surface
        vec3 lw = mat3(gbufferModelViewInverse) * l;
        if (bed && lw.y > 0.05 && below > 0.02) {
            vec2 atSurface = bedPlayer.xz + cameraPosition.xz + lw.xz * (below / lw.y);
            // the bed's own sun shadow (a bank's shadow puts the caustics out on the bed under it too)
            float bedShadow = sunShadow(bedPlayer, vec3(0.0, 1.0, 0.0), fit);
            float bedSun = dot(sunArriving(bedShadow, levels, sunColour), LUMA) * sunFacing(normalize(upPosition), l);
            float bedLight = bedSun + dot(skyColour, LUMA) + 1e-3;
            // a share of the bed's light, seen through what is left of the water's opacity; none right at the shore
            // line (the bed meets the surface there) and fading with depth
            boost = caustics(atSurface, frameTimeCounter) * (bedSun / bedLight) * (1.0 - A)
                * exp(-CAUSTIC_FADE * below) * smoothstep(0.02, 0.2, below);
        }
#endif
        gl_FragData[0] = vec4(c, alpha);
        // no occlusion on the water's own surface (colortex1 0, blended by its alpha, as for glass)
        gl_FragData[1] = vec4(0.0, 0.0, 0.0, alpha);
        // colortex5, written opaque (alpha 1) so it replaces what the buffer held: one water layer's data per pixel
        // b: how much of the reflected sky this pixel shows per unit of Fresnel (top surface only), so composite's
        // screen-space reflection can swap the sky for what the ray meets, on the same terms
        float reflectShare = top ? levels.y * fogDim * (1.0 - foam) : 0.0;
        gl_FragData[2] = vec4(1.0, clamp(0.5 + boost * CAUSTIC_ENCODE, 0.0, 1.0), reflectShare, 1.0);
        return;
    }
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
#ifdef LIT_WATER
    // glass, ice and the rest: no water data, blended by their own alpha so a clear pane over water keeps it
    gl_FragData[2] = vec4(0.0, 0.5, 0.0, alpha);
#endif
}
