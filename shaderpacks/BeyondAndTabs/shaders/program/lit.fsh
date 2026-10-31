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
varying float groundMaterial;
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
// a plant's albedo luma for its caps, taken in light and shown as a display value (light pass 16): scaling the albedo
// by k scales this by k, as the display luma does, but a saturated green no longer reads darker than the light it
// carries (a display-space luma of 0.47 on (0.05, 0.95, 0.10) was 1.5x a grey 0.47's light)
float plantLuma(vec3 albedo) {
    return pow(max(dot(toLinear(albedo), LUMA), 0.0), 1.0 / 2.2);
}

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
#if LIGHTMAP_PROBE == 1 && defined LIT_TERRAIN
    if (tex.a < 0.1)
        discard;
    vec2 lmRaw = clamp((lmcoord - 1.0 / 32.0) * (16.0 / 15.0), 0.0, 1.0);
    gl_FragData[0] = vec4(toLinear(vec3(lmRaw, 0.0)), 1.0);
    gl_FragData[1] = vec4(0.0, 0.0, 0.0, 1.0);
    return;
#endif
#if ALBEDO_PROBE == 1 && defined LIT_TERRAIN
    if (tex.a < 0.1)
        discard;
    gl_FragData[0] = vec4(toLinear(albedo), 1.0);
    gl_FragData[1] = vec4(0.0, 0.0, 0.0, 1.0);
    return;
#endif
#if defined LIT_TERRAIN || defined LIT_WATER
    float ao = glcolor.a;              // separateAo: the vertex AO, not an alpha
    float alpha = tex.a;
#else
    float ao = 1.0;
    float alpha = tex.a * glcolor.a;
#endif
#if PROBE2 == 1 && defined PROBE_COLOUR
    if (alpha < 0.004)
        discard;
    gl_FragData[0] = vec4(PROBE_COLOUR, 1.0);
    gl_FragData[1] = vec4(0.0, 0.0, 0.0, 1.0);
#ifdef LIT_WATER
    gl_FragData[2] = vec4(0.0, 0.5, 0.0, 1.0);
#endif
    return;
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
        // and its brightness under FOLIAGE_MAX_LUMA, eased in from FOLIAGE_LUMA_KNEE of it, hue and saturation kept
        // (light pass 13: the pass-11 stills had 673 neon lime tuft pixels, median (102, 222, 62), luma 0.72 = 1.8x the
        // lit lawn; the cap holds a sunlit tuft at <= ~1.15x the lawn beside it)
        float hl = plantLuma(held);
        float knee = FOLIAGE_LUMA_KNEE * FOLIAGE_MAX_LUMA;
        float room = FOLIAGE_MAX_LUMA - knee;
        float capped = hl <= knee ? hl : knee + room * (1.0 - exp(-(hl - knee) / room));
        held *= capped / max(hl, 1e-4);
        albedo = mix(albedo, held, tinted);
        // light pass 15: the plants without a tint (flowers, saplings, crops) under their own, looser cap
        {
            // light pass 16: a green texel's saturation under PLANT_MAX_SAT first (leaves and stems; petals keep theirs)
            float ptop = max(albedo.r, max(albedo.g, albedo.b));
            float psat = (ptop - min(albedo.r, min(albedo.g, albedo.b))) / max(ptop, 1e-4);
            float green = albedo.g >= ptop - 1e-4 ? 1.0 : 0.0;
            float pg = dot(albedo, LUMA);
            vec3 calmed = mix(vec3(pg), albedo, min(1.0, PLANT_MAX_SAT / max(psat, 1e-4)));
            albedo = mix(albedo, calmed, green * (1.0 - tinted));
            float pl = plantLuma(albedo);
            float pk = PLANT_LUMA_KNEE * PLANT_MAX_LUMA;
            float pr = PLANT_MAX_LUMA - pk;
            float pc = pl <= pk ? pl : pk + pr * (1.0 - exp(-(pl - pk) / pr));
            albedo *= mix(1.0, pc / max(pl, 1e-4), 1.0 - tinted);
        }
        // the blades crowd at the foot: a little base occlusion, as the lawn's own texture has between its clumps
        ao *= mix(FOLIAGE_BASE_AO, 1.0, plantUp);
    } else if (tinted < 0.5) {
        // light pass 17: a cutout sprite no plant id names (lp16's neon sprites, green 236-252): its sprite's coverage
        // from the mip-4 alpha; under PLANT_CUTOUT_COVER the plants' green saturation cap and luma cap
        float cover = texture2D(texture, texcoord, lodBias + 4.0).a;
        float cutout = (1.0 - smoothstep(PLANT_CUTOUT_COVER - 0.05, PLANT_CUTOUT_COVER, cover)) * (1.0 - tinted);
        if (cutout > 0.0) {
            float ptop = max(albedo.r, max(albedo.g, albedo.b));
            float psat = (ptop - min(albedo.r, min(albedo.g, albedo.b))) / max(ptop, 1e-4);
            float green = albedo.g >= ptop - 1e-4 ? 1.0 : 0.0;
            vec3 calmed = mix(vec3(dot(albedo, LUMA)), albedo, min(1.0, PLANT_MAX_SAT / max(psat, 1e-4)));
            albedo = mix(albedo, calmed, green * cutout);
            float pl = plantLuma(albedo);
            float pk = PLANT_LUMA_KNEE * PLANT_MAX_LUMA;
            float pr = PLANT_MAX_LUMA - pk;
            float pc = pl <= pk ? pl : pk + pr * (1.0 - exp(-(pl - pk) / pr));
            albedo *= mix(1.0, pc / max(pl, 1e-4), cutout * green);   // green texels only: a torch's flame keeps its light
        }
    }
#endif

#if ALBEDO_PROBE == 2 && defined LIT_TERRAIN
    gl_FragData[0] = vec4(toLinear(albedo), 1.0);
    gl_FragData[1] = vec4(0.0, 0.0, 0.0, 1.0);
    return;
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
    float cloud = 1.0;
    float plantShadow = 0.0;           // SHADOW_PROBE
    gShadowSpread = 1.0 + LOW_SUN_SPREAD * lowSun();
    if (facing > 0.0) {
        vec3 playerPos = (gbufferModelViewInverse * vec4(viewPos, 1.0)).xyz;
        vec3 worldNormal = mat3(gbufferModelViewInverse) * geomN;
        // a plant looks its shadow up a block toward the light: plants cast shadows now (FOLIAGE_SHADOW_LIFT)
        vec3 lookup = playerPos + (mat3(gbufferModelViewInverse) * l) * (FOLIAGE_SHADOW_LIFT * foliage);
        shadow = sunShadow(lookup, worldNormal, fit);
#if SHADOW_PROBE == 1
        plantShadow = shadow < 0.8 ? plantShadowProbe(lookup, fit) : 0.0;
#endif
#if CLOUD_SHADOWS == 1
        // a passing cloud takes some of the sun (and its glints, which read the same term), never the sky's fill
        // Light pass 11: on the sun's colour, not the shadow term - under a cloud the shade tint (which reads the shadow
        // term) cooled the whole lit lawn, as if it were in shadow (the pass-10 5000 lawn: R and G down, B up)
        cloud = cloudShade(playerPos + cameraPosition, normalize(mat3(gbufferModelViewInverse) * l));
#endif
    }
#if SHADOW_PROBE == 1
    if (plantShadow > 0.5) {
        gl_FragData[0] = plantShadow > 1.5 ? vec4(0.0, 0.2, 1.0, 1.0) : vec4(1.0, 0.0, 0.0, 1.0);
        gl_FragData[1] = vec4(0.0, 0.0, 0.0, 1.0);
#ifdef LIT_WATER
        gl_FragData[2] = vec4(0.0, 0.5, 0.0, 1.0);
#endif
        return;
    }
#endif
    vec3 sunColour, skyColour;
    lightColours(sunColour, skyColour);
    sunColour *= cloud;
    gShadeGround = groundMaterial;
    // light pass 19: on a top no sun reaches, the vertex AO stops at VERTEX_AO_SHADOW_FLOOR (the pad's grass between the
    // cubes: a 0.78 shadow times the corner AO beside a block column read 0.61 of lit; plants keep their base AO)
    {
        float sunHere = clamp(shadow * min(facing * 4.0, 1.0), 0.0, 1.0);
        float upw = smoothstep(0.5, 0.9, dot(geomN, normalize(upPosition))) * (1.0 - foliage);
        ao = mix(ao, max(ao, VERTEX_AO_SHADOW_FLOOR), (1.0 - sunHere) * upw);
    }
    vec3 light = surfaceLight(n, facing, shadow, levels, ao, texAo, sunColour, skyColour);
#ifdef LIT_TERRAIN
    // light pass 19: the biome-tinted ground (grass, tufts, leaves) under the low sun takes LOW_SUN_GRASS of its light,
    // full from t11800's height (lowSunFull), nothing at 5000: lawn L1's brighter albedo put t11800's lawn at 0.334
    light *= mix(1.0, LOW_SUN_GRASS, lowSunFull() * tinted);
#endif
    // near-white faces turned from up lose the light's colour (WHITE_WALL_NEUTRAL, light pass 3): white sides read
    // white, not cream, while the ground keeps the warm sun
    {
        float top = max(albedo.r, max(albedo.g, albedo.b));
        float whiteSat = (top - min(albedo.r, min(albedo.g, albedo.b))) / max(top, 1e-4);
        float whiteness = smoothstep(0.62, 0.75, dot(albedo, LUMA)) * (1.0 - smoothstep(0.06, 0.14, whiteSat));
        float side = 1.0 - clamp(dot(geomN, normalize(upPosition)), 0.0, 1.0);
        float neutral = max(WHITE_WALL_NEUTRAL * side, WHITE_LOW_NEUTRAL * lowSun());
        // light pass 15: only where the sun arrives - a white wall in shade keeps the sky's cool (quartz's shade face read
        // a neutral (115, 116, 112), Complementary's (119, 122, 128))
        neutral *= clamp(shadow * min(facing * 4.0, 1.0), 0.0, 1.0);
        light = mix(light, vec3(dot(light, LUMA)), neutral * whiteness);
        // light pass 16: on a side the sun does not reach, a near-white albedo loses WHITE_SHADE_NEUTRAL of its own cast
        // under a high sun, so the shade of every white wall is the sky's cool (cream and blue-white quartz alike)
        // light pass 18: the sun's share read by WHITE_SHADE_SUN_* (a grazing morning sun no longer makes it a lit side)
        float sunOnSide = clamp(shadow * smoothstep(WHITE_SHADE_SUN_LO, WHITE_SHADE_SUN_HI, facing), 0.0, 1.0);
        float shadeSide = side * (1.0 - sunOnSide) * highSunTint();
        lin = mix(lin, vec3(dot(lin, LUMA)), WHITE_SHADE_NEUTRAL * whiteness * shadeSide);
        // light pass 17: and its light to the same cool on every block (the warm bounce's share varies with the AO)
        light = mix(light, WHITE_SHADE_COOL * dot(light, LUMA), WHITE_SHADE_NEUTRAL * whiteness * shadeSide);
#if SHADE_PROBE_WHITE == 1
        if (WHITE_SHADE_NEUTRAL * whiteness * shadeSide > 0.5) {
            lin = vec3(1.0, 0.0, 1.0);
            light = vec3(1.0);
        } else if (whiteness > 0.5 && side > 0.5) {
            lin = vec3(1.0, 1.0, 0.0);
            light = vec3(1.0);
        }
#endif
    }
    vec3 c = lin * light;
#ifdef LIT_TERRAIN
    // the grass's grain in shade (SHADE_DETAIL, light pass 15): its texel against its neighbourhood, taken further where
    // no sun arrives. Display texels to linear by a square (close enough for a ratio near 1), kept within 0.5-2
    if (tinted > 0.0 && foliage < 0.5) {
        float near = dot(texture2D(texture, texcoord, lodBias + 3.0).rgb, LUMA);
        float own = dot(tex.rgb, LUMA);
        float grain = clamp((own * own) / max(near * near, 1e-4), 0.5, 2.0);
        float shade = 1.0 - clamp(shadow * min(facing * 4.0, 1.0), 0.0, 1.0);
        c *= pow(grain, SHADE_DETAIL * shade * tinted);
    }
#endif
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
    // colortex1 blue: a plant (composite's contact term leaves plants out: it ringed every tuft, light pass 10)
    gl_FragData[1] = vec4(skyShare, 0.0, foliage, alpha);
#ifdef LIT_WATER
    // glass, ice and the rest: no water data, blended by their own alpha so a clear pane over water keeps it
    gl_FragData[2] = vec4(0.0, 0.5, 0.0, alpha);
#endif
}
