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
// vanilla's per-vertex ambient occlusion, kept (it arrives in the vertex colour's alpha: separateAo)
const float ambientOcclusionLevel = 1.0;

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

// block ids from block.properties
#define ID_FOLIAGE 10001.0             // plants, and the lower half of two-block ones
#define ID_LEAVES 10002.0
#define ID_FOLIAGE_UPPER 10003.0       // the upper half of a two-block plant
