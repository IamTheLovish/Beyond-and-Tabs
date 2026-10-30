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
// the sun's penumbra (lib/shadow.glsl): angular spread (radians; the disc is 0.0093, plus the bright aureole and the
// scatter of a hazy day), and the narrowest edge in blocks - about 3.3 px at the RTS camera's default zoom (~33 px a
// block), so no edge is a razor line. A 5-block caster's tip ~0.15 block (~5 px)
const float SUN_SPREAD = 0.025;
const float PENUMBRA_MIN_BLOCKS = 0.10;
// the screen-space ambient occlusion's floor on the fill (slice 3 had 0.55): a block-block contact darkens a little,
// never to a black crease (lib/ao.glsl)
const float CONTACT_AO_FLOOR = 0.85;
// the display's white: final.fsh rolls highlights off with a soft shoulder from DISPLAY_SHOULDER, so white blocks
// stay under DISPLAY_WHITE (no clipping, p99 ~0.95)
const float DISPLAY_SHOULDER = 0.80;
const float DISPLAY_WHITE = 0.96;
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
