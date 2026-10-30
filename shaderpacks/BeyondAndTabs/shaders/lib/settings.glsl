// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack. design/own_shader_pack.md has the plan, the slices and how each is measured.
//
// The pack's settings: what the option screen shows (shaders.properties `screen`), and the directives the loader reads
// from the source. Every program includes this file, so each directive is seen whichever program the loader reads it
// from.

// the sun's path tilted off the zenith: vanilla's sun crosses straight overhead, so at noon nothing throws a shadow the
// RTS camera can see. lovish, Oct 29: the clear tilt, about 30 degrees
const float sunPathRotation = 30.0; // [0.0 15.0 30.0 45.0]

// the shadow map. The RTS view fits it to what is on screen (lib/shadowfit.glsl): ~25 texels per block at the default
// zoom by the arithmetic, falling to the loader's whole box (~6 per block) when zoomed fully out
const int shadowMapResolution = 2048; // [1024 2048 3072 4096]
// half the side of the box the loader draws shadow casters in (blocks), centred on the camera. The RTS fit picks the
// part of it on screen; first person uses all of it. Smaller: fewer casters drawn each frame, shadows end nearer
const float shadowDistance = 160.0; // [96.0 128.0 160.0 224.0]
// draw casters in that box, not out to the render distance (the fit never looks past the box)
const float shadowDistanceRenderMul = 1.0;
const bool shadowHardwareFiltering = false;
// soft shadows (slice 3, lib/shadow.glsl): sharp at the caster's foot, softer the farther the shadow falls, as the
// sun's disc makes them. Off: slice 1's crisp edge everywhere
#define SOFT_SHADOWS 1 // [0 1]
// vanilla's per-vertex ambient occlusion, kept (it arrives in the vertex colour's alpha: separateAo)
const float ambientOcclusionLevel = 1.0;
// screen-space ambient occlusion (slice 3: deferred.fsh, deferred1.fsh, applied in composite.fsh): building bases,
// wall creases and unit feet shut out part of the sky, so they lose part of the sky's fill (never the sun's light)
#define AO 1 // [0 1]
// how strongly: 1 = the response tuned on a model of the RTS camera (lib/ao.glsl has the numbers)
#define AO_STRENGTH 1.0 // [0.5 0.75 1.0 1.25 1.5]
// bloom (slice 3, composite.fsh -> final.fsh): a small halo round lanterns, glows and glints; lit ground never blooms
#define BLOOM 1 // [0 1]

// distance haze strength, as the mod's own haze setting (1 = the Enchanted look's)
#define HAZE_STRENGTH 1.0 // [0.0 0.5 1.0 1.5 2.0]
// the colour grade: 1 = Enchanted (lovish's pick, Oct 28), 0 = none (tone curve only)
#define GRADE 1 // [0 1]
// edge smoothing on geometry edges (as the mod's world look does without a pack)
#define EDGE_AA 1 // [0 1]
// our blocks' and units' material maps (slice 2, lib/pbr.glsl): relief from _n, highlights, sky sheen on metals and
// gems, emission from _s. Off: the slice 1 look, for an A/B
#define MATERIALS 1 // [0 1]

// plants and leaves move in the wind (lib/wind.glsl): gusts rolling across the lawn, leaves shimmering. Off: still
#define WIND 1 // [0 1]
// how far they move: 1 = a grass tuft's top ~0.12 block at a full gust
#define WIND_STRENGTH 1.0 // [0.5 0.75 1.0 1.5 2.0]
// cloud shadows (lib/clouds.glsl): soft patches drifting with the wind that take up to 30% of the sun, never the sky
#define CLOUD_SHADOWS 1 // [0 1]
// sharper block textures in the RTS view: mip levels the terrain's texture reads are pulled finer under the ortho
// camera (0 = the hardware's choice; 1 = a whole level finer, visibly shimmering as the camera pans). README: trade-off
#define TEXTURE_SHARPNESS 0.5 // [0.0 0.25 0.5 0.75 1.0]

// realistic water (slice 4, lib/water.glsl): opacity from the depth of water under each pixel - clear over a shallow
// bed, the palette's dark teal-blue over deep water. Off: slice 1's one fixed alpha, for an A/B
#define WATER_REALISM 1 // [0 1]
// the ripples on water: their steepness (0 = a still mirror, 1 = gentle, 2 = a breezy day)
#define WATER_WAVES 1.0 // [0.0 0.5 1.0 1.5 2.0]
// sunlight focused by the ripples into moving bright lines on the bed under shallow water
#define CAUSTICS 1 // [0 1]
// screen-space reflections on water (composite.fsh): the banks, trees and buildings mirrored where the surface
// reflects strongly - a grazing first-person view. The march runs only where Fresnel passes 4%, which the RTS
// camera's ~65 degrees down never reaches (water there is ~2% mirror), so from above it costs one fetch per pixel
#define SSR 1 // [0 1]

// ---- the light pass (Oct 30, lovish's option "B" after the critic's 4.5/10 against Complementary on the block
// samplers): the lit lawn at the top of the environment palette's band, shadows lifted, walls filled. Each number is a
// target the eyes stills measure (tools/shader_judge.py); tools/test_shader_pack.py pins the arithmetic between
// them ----
// open ground under the high sun, lit to this multiple of its albedo in linear light (was 1.0: "open ground at its
// albedo"). With the grade on top, a noon lawn reads ~0.42-0.43 luma on screen, the top of the palette's 0.36-0.43
// lawn band (it read ~0.37)
const float LIGHT_OPEN_GROUND = 1.30;
// the sky's share of that light: a cast shadow on open ground keeps this much of the sunlit ground's light (was ~0.41,
// on screen ~0.65 of the lit lawn; now ~0.78 on screen). Shadows lift without the sun losing its direction
const float SHADOW_FILL_SHARE = 0.62;
// the hemisphere fill: a face sees the sky by (1 + up) / 2 of its directions and the ground by the rest; the ground
// returns this share of the light falling on it (sun and sky; generous for grass, as it stands in for the bounce off
// everything near). One term for what GROUND_BOUNCE did on its own: by the light table an unlit wall face gets ~0.53 of
// a sunlit top's light, ~0.72 on screen (it was ~0.66 by the same model, measured ~0.55-0.6; benchmark ~0.65-0.7)
const float GROUND_ALBEDO = 0.45;
// the sun's penumbra (lib/shadow.glsl): angular spread (radians; the disc is 0.0093, plus the bright aureole), and
// the narrowest edge in blocks. Light pass 2 (the critic, 5.5/10: "an edgeless darkening, things float"): light pass
// 1's 0.025 / 0.10 drew every edge at least 0.10 block soft (~7 px at rts_close's ~67 px a block) and a shadow a block
// or two across never reached its full depth. Now the contact is crisp (0.03 block: ~1 px at the default zoom, ~2 px
// at rts_close) and the edge widens with the blocker's distance: a 2-block caster's tip ~2.5 px at rts_close, a
// 5-block one's ~6 px
const float SUN_SPREAD = 0.016;
const float PENUMBRA_MIN_BLOCKS = 0.03;
// the screen-space ambient occlusion's floor on the fill (slice 3 had 0.55): a block-block contact darkens a little,
// never to a black crease (lib/ao.glsl)
const float CONTACT_AO_FLOOR = 0.85;
// the display's S-curve (final.fsh, light pass 2), on luma as a ratio, after the grade. The lit lawn is its pivot and
// stays where it is (lovish: the lawn mid-toned on the palette band, ~0.42; buildings own the lights). Below the
// pivot a toe takes the darks down in proportion to how far below the lawn they sit - x (1 - SLOPE u - TOE u^2),
// u = 1 - x / PIVOT - so the fill scales with albedo all the way down: by the model of the light table and grade, a
// cast shadow on the lawn 0.33 -> 0.30 (0.71 of the lit lawn; was ~0.78), blackstone's sunlit top 0.27 -> 0.22, a
// dark block's unlit side ~0.20 -> 0.15. Above the pivot the slope 1 + SLOPE eases back to 1 over LIFT_SPAN (at most
// +0.05), so buildings brighten without their shaded faces flattening into the shoulder. Day only: the curve fades in
// with the sun's height (S_CURVE_DAY_*), so dusk keeps its glow and night is not crushed
const float S_CURVE_PIVOT = 0.42;
const float S_CURVE_SLOPE = 0.32;
const float S_CURVE_TOE = 0.36;
const float S_CURVE_LIFT_SPAN = 0.30;
const float S_CURVE_DAY_LO = 0.05;            // sine of the sun's height: no curve at or below
const float S_CURVE_DAY_HI = 0.35;            // the whole curve from here (Noon and Morning are ~0.86)
// the display's white: past DISPLAY_SHOULDER the curve rolls highlights off toward DISPLAY_WHITE and never reaches it,
// so white blocks land ~0.95-0.97 (light pass 1's 0.80 / 0.96 held them at ~0.92) and nothing clips
const float DISPLAY_SHOULDER = 0.85;
const float DISPLAY_WHITE = 0.985;
// Enchanted's glow (final.fsh) is a screen blend: it lifted every pixel near a white block by the same amount, so a
// dark face beside a white top gained ~+0.09 - an albedo-independent floor on the darks (light pass 2). Its lift now
// scales with the pixel's own luma, (luma / GLOW_THRESHOLD)^GLOW_DARK_GUARD: the lawn keeps ~0.7 of it, darks little
const float GLOW_DARK_GUARD = 2.0;
// the daytime sky in a perspective view (program/sky.fsh) and the aerial haze's air (composite.fsh): a gradient from
// SKY_HORIZON_DAY to SKY_ZENITH_DAY over the first SKY_GRADIENT_SPAN of the sine of the view's height. Display
// colours before the grade, solved through the model of the grade and the S-curve so the screen shows a zenith ~0.88
// luma, bluer (0.78, 0.90, 0.98), and a paler horizon ~0.75 (0.72, 0.76, 0.80). It had vanilla's colours, which the
// glow and the white guard took to a flat near-white (critic, light pass 1's 34 / dusk stills)
const vec3 SKY_ZENITH_DAY = vec3(0.58, 0.81, 1.00);
const vec3 SKY_HORIZON_DAY = vec3(0.61, 0.65, 0.70);
const float SKY_GRADIENT_SPAN = 0.60;
// metals' reflections (lib/pbr.glsl): the sky above the horizon, the ground below it as the fill sees it (GROUND_ALBEDO
// of the sun and sky falling on the ground), never dimmed below SKY_FILL_FLOOR by a low sky light - the fill's own
// floor. Light pass 1's iron_block side reflected ~nothing and read near black (RGB ~0.24) beside its white mortar.
// Which texels are metal: LabPBR F0 codes 230-237 and 255 (the pack's only metal codes: tools/gen_blocks.py, 230 / 231
// / 234 / 255). The pack's dielectrics stop at 44 (the gems); a code between DIELECTRIC_F0_MAX_CODE and 230 is a
// mipmap's blend of a metal texel and a dielectric one (an iron hoop on a barrel, rust on iron), read as that share
// of metal - not as a dielectric with an F0 up to 0.9, a white mirror sheen on wood or rust
const float DIELECTRIC_F0_MAX_CODE = 64.0;
// biome-tinted plants (grass, ferns; lit.fsh): their albedo held to the lawn's band, so a tuft reads as part of the
// lawn, not neon lime (light pass 1's tufts: luma ~0.62 on a 0.42 lawn, saturation ~0.8). HSV saturation of the
// albedo at most FOLIAGE_MAX_SAT (the lawn's own is ~0.36; the grade lifts both ~1.5x on screen), its luma at most
// FOLIAGE_MAX_LUMA (the lawn's ~0.37), and the foot of the plant shaded to FOLIAGE_BASE_AO (the blades crowd there)
const float FOLIAGE_MAX_SAT = 0.40;
const float FOLIAGE_MAX_LUMA = 0.42;
const float FOLIAGE_BASE_AO = 0.70;
// whites read white, not cream: from WHITE_GUARD_LUMA up the grade's colour is taken back by WHITE_DESATURATE
const float WHITE_GUARD_LUMA = 0.85;
const float WHITE_DESATURATE = 0.30;
// aerial perspective in a perspective view (first person, the 3/4 camera; never the orthographic RTS camera, which
// keeps composite's own RTS haze): sky-tinted air from AERIAL_START blocks, AERIAL_MAX x (1 - e^-(d - start)/scale),
// ~27% at 150 blocks (x HAZE_STRENGTH)
const float AERIAL_START = 40.0;
const float AERIAL_SCALE = 100.0;
const float AERIAL_MAX = 0.40;
// final.fsh's dither, in 8-bit steps (0: none). It read as film grain over the lawn at the RTS zoom; the 16-bit
// buffers and the shoulder leave no banding a camera this close to the ground shows
const float DITHER_LSB = 0.0;

// block ids from block.properties
#define ID_FOLIAGE 10001.0             // plants, and the lower half of two-block ones
#define ID_LEAVES 10002.0
#define ID_FOLIAGE_UPPER 10003.0       // the upper half of a two-block plant
#define ID_WATER 10004.0               // still and flowing water
