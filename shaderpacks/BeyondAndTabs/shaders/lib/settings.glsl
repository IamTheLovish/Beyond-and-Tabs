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
// light pass 7: 2048 -> 4096 by default - the RTS fit at 2048 drew ~2 px texels at the default zoom, and its edges
// either stair-stepped (a narrow filter) or blurred into wavy bands (a wide one)
const int shadowMapResolution = 4096; // [1024 2048 3072 4096]
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
// the biome-tinted grass takes this share of the sharpness (lit.fsh, light pass 6): a finer mip sharpened the lawn's
// grain past the benchmark's (high-pass std 0.045 against 0.037)
const float GRASS_SHARPNESS_SHARE = 0.4;

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
// Light pass 5: 1.30 -> 1.12. The pass-4 stills measured the noon lit lawn at 0.45-0.47 (median 0.46, green pixels;
// pass 2 0.43, pass 3 ~0.40), over lovish's band; at 1.12 the lawn's pre-curve luma falls by (1.12 / 1.30)^(1/2.2),
// ~0.934, the S-curve's pivot slope of 1.32 around 0.42 taking 0.46 back to ~0.42. The low sun has its own row
// Light pass 6: 1.12 -> 1.20. Pass 5's stills were shot at a true zenith sun (the eyes' noon was not yet stable, #442)
// and measured the lawn 0.41; the day is now judged at time 5000 (the skirmish's own), sun ~58 degrees up, which
// gives open ground ~10% less sun than the zenith: 1.20 puts the lit lawn back at ~0.42 there
const float LIGHT_OPEN_GROUND = 1.20;
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
// Light pass 3 (critic 6.5/10: the edge now ~0 px, a razor with stair-steps - the shadow texel showing through a
// filter narrower than it): the floor 0.05 block (~3.4 px at rts_close) and never under PENUMBRA_MIN_TEXELS shadow
// texels (lib/shadow.glsl), so the 12 jittered taps always span more than a texel. A 2-3 block caster's edge
// ~3.4 px at rts_close, a 5-block one's ~5.4 px; t11800's long shadows (a 3-block caster's tip ~13 blocks along the
// light) ~0.18 block: ~6 px at the default zoom, ~12 at rts_close
const float SUN_SPREAD = 0.014;
const float PENUMBRA_MIN_BLOCKS = 0.05;
// the screen-space ambient occlusion's floor on the fill (slice 3 had 0.55): a block-block contact darkens a little,
// never to a black crease (lib/ao.glsl)
// Light pass 8 (the critic: the benchmark's one real advantage on the pad is the contact darkening at block feet;
// ours, floored at 0.85 of the sky's share, measured ~0.02): 0.85 -> 0.65, and the occlusion takes CONTACT_SUN_SHARE
// of the rest of the light too. Half a block from a wall's foot on the lit lawn: ~-0.06-0.08 luma
const float CONTACT_AO_FLOOR = 0.65;
const float CONTACT_SUN_SHARE = 0.5;
// the display's S-curve (final.fsh, light pass 2), on luma as a ratio, after the grade. The lit lawn is its pivot and
// stays where it is (lovish: the lawn mid-toned on the palette band, ~0.42; buildings own the lights). Below the
// pivot a toe takes the darks down in proportion to how far below the lawn they sit - x (1 - SLOPE u - TOE u^2),
// u = 1 - x / PIVOT - so the fill scales with albedo all the way down: by the model of the light table and grade, a
// cast shadow on the lawn 0.33 -> 0.30 (0.71 of the lit lawn; was ~0.78), blackstone's sunlit top 0.27 -> 0.22, a
// dark block's unlit side ~0.20 -> 0.15. Above the pivot the slope 1 + SLOPE eases back to 1 over LIFT_SPAN (at most
// +0.05), so buildings brighten without their shaded faces flattening into the shoulder. Day only: the curve fades in
// with the sun's height (S_CURVE_DAY_*), so dusk keeps its glow and night is not crushed
const float S_CURVE_PIVOT = 0.42;
// Light pass 6 (judged at day time 5000, what a skirmish plays at): the day's curve is gentle - pass 2's 0.32 / 0.36
// crushed the dark blocks (black concrete 0.106, five dark blocks at 0.15 +- 0.004, the dark pad's median 0.27 against
// the benchmark's 0.48) and its pivot slope of 1.32 sharpened the lawn's grain (high-pass std 0.045 against 0.037).
// The low sun keeps pass 2's curve (S_CURVE_*_LOW, by the light table's low row): its 2.8:1 shadows scored 8.0-8.3.
// Model at 5000: black concrete ~0.16, a pre-curve 0.30 dark top ~0.29, the lawn's grain x0.82 (pivot slope 1.08)
const float S_CURVE_SLOPE = 0.08;
const float S_CURVE_TOE = 0.0;
const float S_CURVE_SLOPE_LOW = 0.32;
const float S_CURVE_TOE_LOW = 0.36;
// a perspective view's share of the low sun's curve at every hour (final.fsh, light pass 7)
const float PERSPECTIVE_CURVE = 0.8;
const float S_CURVE_LIFT_SPAN = 0.30;
const float S_CURVE_DAY_LO = 0.05;            // sine of the sun's height: no curve at or below
// the whole curve from here (Noon and Morning are ~0.86). Light pass 3: 0.35 -> 0.20, so the low sun (t11800, ~0.23)
// takes all of it: at 0.6 of the curve its shadows kept the flat 1.5:1 of a dim room
const float S_CURVE_DAY_HI = 0.20;
// the display's white: past DISPLAY_SHOULDER the curve rolls highlights off toward DISPLAY_WHITE and never reaches it,
// so white blocks land ~0.95-0.97 (light pass 1's 0.80 / 0.96 held them at ~0.92) and nothing clips
const float DISPLAY_SHOULDER = 0.85;
const float DISPLAY_WHITE = 0.99;               // light pass 6: 0.985 -> 0.99 (the gentler day curve lifts whites less)
// a perspective view (first person, the 3/4 camera) looks at walls square on, often straight into the sun: white
// buildings there read blown out (light pass 3: the castle at luma 0.82, saturation 0.05 in the 34 / dusk / t11800
// stills). Its geometry rolls off from DISPLAY_SHOULDER_PERSPECTIVE toward DISPLAY_WHITE_PERSPECTIVE instead, so a lit
// white wall lands ~0.76-0.78 with its brick lines; the white guard takes less of its colour there
// (WHITE_DESATURATE_PERSPECTIVE). The sky keeps the RTS shoulder (its zenith sits ~0.88)
// Light pass 4 (critic: pass 3's roll-off from 0.64 made the castle a grey cardboard cutout, lit and shaded faces 0.02
// apart): the highlights are scaled down as a ratio instead - by PERSPECTIVE_HIGHLIGHT, eased in from luma 0.5 to 0.9
// so the lawn and the midtones are untouched - and only the very top rolls off (0.86 toward 0.94). A lit white wall
// (~1.10 after the S-curve) lands ~0.87, its shaded face (~0.90) ~0.72: 0.15 apart, the brick lines kept
const float PERSPECTIVE_HIGHLIGHT = 0.80;
const float DISPLAY_SHOULDER_PERSPECTIVE = 0.86;
const float DISPLAY_WHITE_PERSPECTIVE = 0.94;
// the low sun's whites (light pass 4: block tops at t11800 0.95 against a 0.38 lawn read pasted on): as the sun sinks
// (the light table's low row) the shoulder moves to DISPLAY_SHOULDER_LOW / DISPLAY_WHITE_LOW. A white top ~0.89
const float DISPLAY_SHOULDER_LOW = 0.80;
const float DISPLAY_WHITE_LOW = 0.925;
const float WHITE_DESATURATE_PERSPECTIVE = 0.10;
// Enchanted's glow (final.fsh) is a screen blend: it lifted every pixel near a white block by the same amount, so a
// dark face beside a white top gained ~+0.09 - an albedo-independent floor on the darks (light pass 2). Its lift now
// scales with the pixel's own luma, (luma / GLOW_THRESHOLD)^GLOW_DARK_GUARD: the lawn keeps ~0.7 of it, darks little
const float GLOW_DARK_GUARD = 2.0;
// the daytime sky in a perspective view (program/sky.fsh) and the aerial haze's air (composite.fsh): a gradient from
// SKY_HORIZON_DAY to SKY_ZENITH_DAY over the first SKY_GRADIENT_SPAN of the sine of the view's height. Display
// colours before the grade, solved through the model of the grade and the S-curve so the screen shows a zenith ~0.88
// luma, bluer (0.78, 0.90, 0.98), and a paler horizon ~0.75 (0.72, 0.76, 0.80). It had vanilla's colours, which the
// glow and the white guard took to a flat near-white (critic, light pass 1's 34 / dusk stills)
// Light pass 6 (critic: every frame's sky darker toward the horizon, noon 0.73 -> 0.60, the inverse of a real sky): a
// deeper zenith (0.58, 0.81, 1.00 before) over a lighter horizon (0.60, 0.65, 0.72 before), ~0.64 -> ~0.80 luma
// Light pass 7 (critic: the painted sky read overcast - in a 3/4 frame the top edge is only ~5 degrees above the
// horizon, so the 0.60 span never left the horizon's colour): the gradient spans SKY_GRADIENT_SPAN 0.10 of the view's
// sine, a deeper zenith, a paler, faintly warm horizon. Model on screen: zenith ~0.64 (hue ~215), the frame's top
// edge ~0.66, the horizon ~0.86
// Light pass 8 (critic: pass 7's sky a hard two-band, saturated blue for ~60 px then a flat cream; the benchmark one
// soft blue-grey ramp): solved through the model of the grade for a zenith ~(150, 180, 220), a neutral blue-grey
// horizon ~(190, 197, 208) at ~0.77 luma and the void below it ~(174, 184, 198); a square-root ramp over 0.60 of the
// view's sine
const vec3 SKY_ZENITH_DAY = vec3(0.55, 0.634, 0.754);
// light pass 3: the horizon a little bluer (0.61, 0.65, 0.70 before), on screen ~(0.70, 0.76, 0.84), luma ~0.75
const vec3 SKY_HORIZON_DAY = vec3(0.66, 0.70, 0.755);
// the void below the horizon (linear factor on the horizon's colour, reached 0.4 of the sine down)
const vec3 SKY_BELOW_TINT = vec3(0.84, 0.85, 0.875);
// the brightening within ~30 degrees of the sun (lib/sky.glsl)
const float SKY_SUN_GLOW = 0.25;
// the horizon away from the sun once it has set (light pass 8: the dusk still at 13000 read mid-afternoon, its sky
// 0.76; the benchmark's 0.46): a dim blue-grey
const vec3 SKY_HORIZON_DUSK = vec3(0.42, 0.44, 0.55);
// our own moon (vanilla's square sprite dropped): radius (radians), colour in linear light
const float MOON_DISC_RADIUS = 0.022;
const vec3 MOON_DISC_COLOUR = vec3(0.80, 0.84, 0.92);
// the aerial perspective's air by day (lib/sky.glsl skyAir): pass 4's horizon, kept for the haze
const vec3 SKY_AIR_DAY = vec3(0.60, 0.65, 0.72);
// below the horizon (the void past the map's edge in a 3/4 view) the horizon's colour, down to this share of it
// a probe for which sky programs the loader runs (light pass 6): 1 paints gbuffers_skybasic magenta,
// gbuffers_skytextured green and gbuffers_clouds cyan, and composite.fsh leaves the sky as those programs drew it.
// Run eyes once with it on; off for play
#define SKY_PROBE 0 // [0 1]
const float SKY_GRADIENT_SPAN = 0.60;
// the low sun's sky (lib/sky.glsl, light pass 4): below SKY_SUNSET_HIGH of the sun's sine the horizon warms toward the
// sun - SKY_SUNSET_GOLD (hue ~33, t11800) turning SKY_SUNSET_DEEP (hue ~15, darker: the dusk still) as the sun reaches
// the horizon - with SKY_SUNSET_AWAY of it on the side away from the sun, and the zenith deepens to SKY_ZENITH_DUSK.
// Display colours before the grade. At the horizon dusk and t11800 differ by ~0.18 luma and ~18 degrees of hue
const float SKY_SUNSET_HIGH = 0.25;
const vec3 SKY_SUNSET_GOLD = vec3(0.95, 0.75, 0.50);
// light pass 5 (critic: the dusk sky a flat peach wash, zenith to horizon 0.05 luma): the horizon band lighter and
// less red, ~0.78 luma at hue ~23 (was 0.55 at 15), under a cooler, darker zenith
// light pass 8: (0.97, 0.74, 0.60) -> (0.80, 0.55, 0.42): toward the set sun ~0.58 luma at hue ~20 (target ~0.55)
const vec3 SKY_SUNSET_DEEP = vec3(0.80, 0.55, 0.42);
const float SKY_SUNSET_AWAY = 0.35;
// light pass 8: (0.50, 0.58, 0.68) -> a deep blue-violet, ~0.37 luma at hue ~240 (target ~0.35)
const vec3 SKY_ZENITH_DUSK = vec3(0.36, 0.36, 0.55);
// clouds (gbuffers_clouds, light pass 5): lit by the sky's ramp in their own direction (CLOUD_SKY of it) plus
// CLOUD_SUN of the sun, so a dusk cloud takes the dusk colours instead of vanilla's grey-white slab
const float CLOUD_SKY = 1.15;
const float CLOUD_SUN = 0.18;
// vanilla's clouds come through gbuffers_textured / gbuffers_basic under Oculus (light pass 7, the SKY_PROBE run):
// found there by world height (the layer is at y 192 in 1.20), softened to CLOUD_OPACITY of vanilla's alpha
const float CLOUD_Y_MIN = 170.0;
const float CLOUD_Y_MAX = 215.0;
const float CLOUD_OPACITY = 0.55;            // (unused since light pass 8: the caught clouds are dropped)
// the unlit programs drop what they draw past this share of the render distance in a perspective view (light pass 8:
// the dotted ring beyond the map's edge)
const float SKYCATCH_FAR = 0.85;
const float CLOUD_RIM_START = 0.45;            // of the render distance (far), across the ground: the clouds fade out
const float CLOUD_RIM_END = 0.80;              // between these, before the layer's rim
// the sun's disc in the sky (program/sky.fsh, light pass 3; vanilla's square sprite is not drawn): SUN_DISC_RADIUS
// (radians, ~2x the real disc so it reads at 1080p) with a soft rim, slightly warm, ~2x white in linear light so it
// blooms; a halo of SUN_HALO round it. The moon keeps vanilla's sprite with a soft MOON_GLOW round it at dusk and night
const float SUN_DISC_RADIUS = 0.018;
const vec3 SUN_DISC_COLOUR = vec3(1.00, 0.93, 0.80) * 2.2;
const vec3 SUN_DISC_LOW = vec3(1.00, 0.85, 0.60) * 2.2;      // the low sun's disc, warmer
const float SUN_HALO = 0.35;
const float SUN_SPRITE_COS = 0.90;           // vanilla's sun sprite: within ~0.45 rad of the sun's direction
const float MOON_GLOW = 0.18;
// the fill where no sun arrives (lib/light.glsl shadeTint, light pass 3): the sky's blue, luma 1. A cast shadow on the
// noon lawn reads ~+10 degrees of hue toward blue against the lit lawn, by the model (it was the same hue, darker)
// Light pass 4 (the critic: the noon shadow interior still rgb(0.106, 0.158, 0.073), hue 97, a dead green-black; target
// hue 130-170, blue >= 0.75 of green): much bluer, and on the sky's part of the fill only (the ground's bounce stays
// warm). Model of a noon cast shadow on the lawn: hue ~160, blue ~0.85 of green, its luma unchanged
// Light pass 5 (critic 7.2/10: pass 4's (0.78, 0.95, 2.10) was indigo - every shaded vertical face went violet, H
// 231-251, saturation 0.12-0.26, the castle lavender - while the ground's shadow stayed green-black): a sky blue
// ((0.84, 0.95, 1.28) at luma 1), full on faces looking up, SHADE_TINT_WALL of it on walls, where the warm ground
// bounce meets it (the violet mix). Model: a shaded white wall ~hue 210 at saturation ~0.03-0.05, the ground's shadow
// hue ~120 in the model (pass 3's tint modelled 113 and measured 97: ~105-115 in game)
// Light pass 7: separate tints for the ground and for walls. The ground's (faces looking up) bluer: at 5000 the lawn's
// shadow read rgb 52/76/39, "a dimmer lawn without sky" (blue 0.51 of green); target ~48/74/52 (>= 0.65). By the
// model x1.22 on its blue / green on screen -> ~0.62-0.65. A white top in shade reads blue ~1.5x red, as snow does
const vec3 SHADE_TINT = vec3(0.763, 0.966, 2.035);
// walls (faces looking sideways; pass 6's 0.60 share of the old tint, now its own colour): hue ~205-215, saturation
// ~0.06-0.10 on shaded stone
const vec3 SHADE_TINT_WALL = vec3(0.93, 1.00, 1.21);
// Light pass 6 (critic: shaded stone walls now dead grey, saturation 0.01-0.02; target hue 200-215 at 0.06-0.10,
// half pass 4's violet): 0.35 -> 0.60 of the tint on walls (light pass 7: SHADE_TINT_WALL above, its own colour)
// the low sun's shadows take the tint harder by this share (light pass 6: the t11800 ground shadow read yellow-green,
// hue 70-95 at saturation 0.53-0.58; target ~105-120, <= 0.40)
const float SHADE_TINT_LOW_BOOST = 0.6;
// the moon's light fades in between these depths of the sun under the horizon (sine), lib/light.glsl
const float MOON_FADE_LO = 0.05;
const float MOON_FADE_HI = 0.30;
// a sky light at or above this share of 15 counts as open sky (lib/light.glsl lightLevels, light pass 8)
const float SKY_LIGHT_FULL = 0.8;
// the material class of the ground's shade tint (light pass 8)
#define ID_GROUND 10005.0
// the fill's direction (lib/light.glsl, light pass 5: the castle at noon had no form, its two visible walls 0.68 and
// 0.67): the sky is brighter toward the sun and the sunlit ground bounces from that side, so a face turned toward the
// sun's azimuth takes up to 1 + FILL_DIRECTION of the fill, one turned away 1 - FILL_DIRECTION; tops and the lawn none.
// A wall facing the sun's azimuth against one facing away: fill ratio 1.86, ~1.2-1.3x on screen with the sun on it
const float FILL_DIRECTION = 0.30;
// the share of the occlusion (vertex and material AO) the ground's bounce takes (lib/light.glsl skyFill, light pass 7)
const float BOUNCE_AO = 0.35;
// how much harder a sinking sun rakes the grass (lib/light.glsl sunFacingRaked): the facing's easing toward a square
// root grows from 0.5 by this much as the sun goes from 0.55 to 0.10 of its height's sine. Light pass 4: grass and plants
// only (biome-tinted terrain; pass 3 raked every top, so white block tops outshone the lawn at t11800) and all the way
// to the square root: the lawn under a 13-degree sun takes 0.47 of the sun, block tops 0.35
const float LOW_SUN_RAKE = 1.0;
// metals' reflections (lib/pbr.glsl): the sky above the horizon, the ground below it as the fill sees it (GROUND_ALBEDO
// of the sun and sky falling on the ground), never dimmed below SKY_FILL_FLOOR by a low sky light - the fill's own
// floor. Light pass 1's iron_block side reflected ~nothing and read near black (RGB ~0.24) beside its white mortar.
// Which texels are metal: LabPBR F0 codes 230-237 and 255 (the pack's only metal codes: tools/gen_blocks.py, 230 / 231
// / 234 / 255). The pack's dielectrics stop at 44 (the gems); a code between DIELECTRIC_F0_MAX_CODE and 230 is a
// mipmap's blend of a metal texel and a dielectric one (an iron hoop on a barrel, rust on iron), read as that share
// of metal - not as a dielectric with an F0 up to 0.9, a white mirror sheen on wood or rust
const float DIELECTRIC_F0_MAX_CODE = 64.0;
// biome-tinted plants (grass, ferns; lit.fsh): their albedo's HSV saturation held to FOLIAGE_MAX_SAT (the lawn's own is
// ~0.36; the grade lifts both ~1.5x on screen), so a tuft is not neon lime (light pass 1: saturation ~0.8). Light pass
// 3: no luma cap any more (pass 2's 0.42 with a 0.70 foot made dark grey smudges of the tufts; the texture side is
// lightening their albedo), and a gentler foot, FOLIAGE_BASE_AO
const float FOLIAGE_MAX_SAT = 0.40;
const float FOLIAGE_BASE_AO = 0.85;
// plants cast sun shadows (light pass 7: the benchmark's tufts do, ours were dropped from the shadow pass since #350,
// when hard shadows read as dark stars; the penumbra is soft now). A plant looks its own shadow up FOLIAGE_SHADOW_LIFT
// blocks toward the light, so it is never shadowed by itself or the tufts round its foot
const float FOLIAGE_SHADOW_LIFT = 1.0;
const float PLANT_SHADOW_ALPHA = 0.5;      // shadow.fsh: plants' texels below this alpha cast nothing
// the meadow's macro variation (lib/meadow.glsl, light pass 3): patches MEADOW_CELL blocks per noise cell (~8-16 blocks
// across), the albedo's linear value by exp(MEADOW_VALUE x field) - by a numpy model of the field through the
// S-curve's 1.32 slope at the lawn: a macro std of ~0.04 luma (p5-p95 ~0.36-0.49), the frame-wide lawn std ~0.03 ->
// ~0.05, the mean unchanged (factor mean ~0.997) - and the hue by MEADOW_HUE_* (red and blue in linear light, ~+-6
// degrees at the field's extremes, ~+-2.5 typical)
// Light pass 4 (critic: imperceptible from RTS height): larger, softer cells (11.3 -> 17.3 blocks, the coarse octave
// 0.80 of the field), value 0.40 -> 0.55 (macro std ~0.06 on screen, p5-p95 ~0.32-0.52, lawn std ~0.07 in all) and
// hue 0.11 / 0.15 -> 0.17 / 0.23 (~+-3.5 degrees typical, ~+-8-10 at the extremes); the mean unchanged
const float MEADOW_CELL = 17.3;
// light pass 8 (the critic: the lawn should read "full", not brighter): value 0.55 -> 0.70 (macro std ~0.075), hue
// 0.17 / 0.23 -> 0.22 / 0.30 (~+-4.5 degrees typical, ~+-10-12 at the extremes); the mean unchanged
const float MEADOW_VALUE = 0.70;
const float MEADOW_HUE_RED = 0.22;
const float MEADOW_HUE_BLUE = 0.30;
// near-white faces turned from the up direction lose the light's colour (light pass 3: white wool and white blocks'
// sides read cream, H 40, S 0.07, under the warm sun while their tops stayed neutral): the light on a face of albedo
// luma past ~0.7 and saturation under ~0.12 is pulled toward its own grey by WHITE_WALL_NEUTRAL x (1 - up). The ground
// keeps the warm sun; coloured walls keep it too
// Light pass 4: 0.85 -> 0.50 - fully neutral, the perspective views' sunlit white walls lost their warmth (the castle
// read hue-less); half keeps a lit white wall at a warm hue ~35-45 with a low saturation (cream was ~0.07)
const float WHITE_WALL_NEUTRAL = 0.50;
// and under a low sun every near-white face, tops too, by this much (light pass 7: t11800's whites read 235/221/213, a
// 22 spread; target <= 12 while the lawn keeps the warm light)
const float WHITE_LOW_NEUTRAL = 0.55;
// a rough dielectric's sun glint never passes this share of SPEC_MAX (light pass 3: nether bricks strobed white on
// their bevels' normals at smoothness ~0.1-0.2): the cap rises to the full SPEC_MAX from smoothness 0.2 to 0.6. Metals
// keep the full cap. Also every glint fades out where the mapped normal turns from the eye (n.v under 0.2)
const float ROUGH_GLINT_CAP = 0.12;
// whites read white, not cream: from WHITE_GUARD_LUMA up the grade's colour is taken back by WHITE_DESATURATE
const float WHITE_GUARD_LUMA = 0.85;
const float WHITE_DESATURATE = 0.30;
// aerial perspective in a perspective view (first person, the 3/4 camera; never the orthographic RTS camera, which
// keeps composite's own RTS haze): sky-tinted air from AERIAL_START blocks, AERIAL_MAX x (1 - e^-(d - start)/scale),
// ~27% at 150 blocks (x HAZE_STRENGTH)
// Light pass 3 (the critic: the far lawn muddy grey, H 102, S 0.28, luma 0.43, where the benchmark goes airy blue-white):
// denser, from 30 blocks, ~41% at 100 blocks, ~54% at 150, ~62% at 200, toward the sky gradient's bluer horizon, and
// the air takes the colour it veils fully (AERIAL_DESAT; the RTS haze keeps its half). By the model, far lawn at
// 150-200 blocks: H ~165-185, S ~0.18-0.20, luma ~0.58-0.61 on screen (was ~27% at 150, the 0.40 / 100 / 40 of pass 1)
// Light pass 4 (critic 7.0/10: overshot - the far ground took the sky's colour, 0.62, saturation 0.20, hue 212, the
// horizon dissolved, the 34 views' local contrast fell to 0.016): ~31% at 150 blocks, ~35% at 200, and the air no
// longer takes the colour it veils (AERIAL_DESAT 0). Model: far lawn saturation ~0.29-0.31, luma ~0.53, ~0.2 under the
// sky's horizon, hue ~125-140 (blue-green, not the sky's blue)
const float AERIAL_START = 30.0;
const float AERIAL_SCALE = 80.0;
const float AERIAL_MAX = 0.40;
const float AERIAL_DESAT = 0.0;
// final.fsh's dither, in 8-bit steps (0: none). It read as film grain over the lawn at the RTS zoom; the 16-bit
// buffers and the shoulder leave no banding a camera this close to the ground shows
const float DITHER_LSB = 0.0;

// block ids from block.properties
#define ID_FOLIAGE 10001.0             // plants, and the lower half of two-block ones
#define ID_LEAVES 10002.0
#define ID_FOLIAGE_UPPER 10003.0       // the upper half of a two-block plant
#define ID_WATER 10004.0               // still and flowing water
