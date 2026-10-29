# Our own shader pack (design, Oct 29 2026)

Status: **design only, no code yet.** Waiting on lovish's OK (through the coordinator) before slice 1.

## Why

From Oct 22 the tester pack shipped Complementary Reimagined on Oculus, because lovish said "the game needs better
graphics, everything still feels flat" (#132). On Oct 29 its author refused us permission to ship it. lovish decided
to stop shipping it and build our own: "we can make something better than whatever he made". Until ours ships, the
old pack may still be used in CI only (the eyes shader lane), so we have a before/after.

## Clean room and licence

- **This pack is clean-room.** No Complementary Reimagined source, settings file, shader file or option list was opened,
  read, fetched, quoted or adapted to write this design, and none will be to write the pack. Its structure is not based
  on it. The old pack is only ever looked at from the outside: as eyes screenshots, compared by measurements.
- Sources we work from: the public documentation of the OptiFine / Iris shader-pack format (`shaders.properties`,
  `block.properties`, `entity.properties`, the program list `gbuffers_*`, `shadow`, `deferred`, `composite`, `final`,
  the standard uniforms), Oculus's own docs and changelog for what 1.8.0 supports, general real-time graphics knowledge
  (shadow mapping, PCF, tone mapping, SSAO, bloom, Fresnel), and our own code (`WorldLookPass`, `bat_postfx.fsh`,
  `tools/materials.py`, the ortho camera).
- Every shader file is written from scratch by us. Each file starts with a header naming the pack, our licence and the
  line "Original work, written clean-room; not derived from any other shader pack".
- **Licence: ours.** The pack is part of this repository and is released under the repository's licence (GPL v2 or
  later, see `LICENSE` and `NOTICE.md`), unless lovish picks another (question 6). `NOTICE.md` gets a section for it.
- Whoever writes a slice must not use another shader pack's code as a reference either (Bliss, Photon, BSL and the
  rest included): the rule is "documentation and our own heads", not "anything but Complementary".

## What players saw before (goals, judged from our own stills)

Measured on our eyes stills: plain lane (branch `eyes`, our Enchanted-era post pass not yet in) against the shader lane
(branch `eyes-shaders`, build d056035), same tour shots, 1600x900, crop of the play area without the HUD. The two lanes
are different runs, so the scenes differ in detail; the numbers show the direction, not exact sizes.

| still | lane | luma p5 / p50 / p95 | saturation p50 | mean R / G / B |
|---|---|---|---|---|
| 02 RTS default | plain | 0.22 / 0.34 / 0.88 | 0.43 | 0.37 / 0.44 / 0.32 |
| 02 RTS default | shaders | 0.15 / 0.37 / 0.58 | 0.58 | 0.38 / 0.37 / 0.22 |
| 09 fight | plain | 0.19 / 0.33 / 0.76 | 0.46 | 0.32 / 0.39 / 0.27 |
| 09 fight | shaders | 0.17 / 0.39 / 0.64 | 0.56 | 0.41 / 0.39 / 0.25 |
| 03 RTS close | plain | 0.29 / 0.62 / 0.88 | 0.07 | 0.64 / 0.62 / 0.58 |
| 03 RTS close | shaders | 0.13 / 0.37 / 0.63 | 0.15 | 0.40 / 0.37 / 0.38 |

What the shader look gave players, as goals for ours:

1. **Things stand on the ground.** Buildings and units throw sun shadows, so a capitol reads as a tall mass and a unit
   as a figure on the grass, not a sticker. This is the biggest single reason the plain view "feels flat".
2. **Whites stop blowing out.** The capitol's white walls clip near 0.88 luma plain; with the shader look the highlights
   sit lower and its shape (towers, walls, voids) reads. Our tone curve must keep whites white but unclipped.
3. **Richer colour.** Saturation up about a third. lovish's later ask (Oct 28, #326) points the same way: "dim it down a
   bit and make it look more saturated and whimsy ... fantasy esque", which became our Enchanted look.
4. **Our labPBR maps finally showed** (relief from `_n`, smoothness, metals and emission from `_s`), once the old pack
   was told to read them. Without a pack they are never read.

What we do **not** want back (found in our own stills and notes):

- **The olive cast.** The shader stills lose a third of their blue (mean B 0.32 -> 0.22): green grass reads yellow-olive
  and brown. lovish's Oct 17 note on a low sun "washing the whole map orange" is the same fault at sunrise
  (`match/TimeOfDay.java`). Grass must read green at every time of day the lobby offers.
- **Dark stars under grass tufts** at noon: small plants throwing hard sun shadows (Itachi 1's finding, #350).
- **Fog and AO that do not understand our camera.** Packs written for a first-person eye treated the whole ortho
  battlefield as the hand until we bent our projection to suit them (`OrthoviewClientEvents`, the lopsided depth
  range). Our pack is written for the RTS camera first.
- **Black squares from translucent things in the shadow map** (the Bone Dragon's sail, our overlays; see
  `ShaderPacks.skipOverlays`).

## What we are building for

- **The RTS camera.** Orthographic, looking down at 55-75 degrees (12-90 allowed), zoom 10-200 blocks of view height,
  eye 30-100 blocks above the ground (`design/camera.md`, `OrthoviewClientEvents`). Most of the frame is ground,
  buildings and small units seen from above; the sky is rarely on screen. First person (hero possession, the eyes 14
  still) must also work, but is second.
- **Readability beats realism.** Units must stay readable against the ground at every zoom; team colours, selection
  rings, health bars and the strategic icons must stay as drawn. Every effect is judged first by whether a player can
  still read a fight.
- **Our maps and our textures.** The realistic block pack (`resourcepacks/BeyondAndTabs-Realistic`, 128 px, photo
  scans) and our units and buildings all ship labPBR 1.3 maps from one encoder (`tools/materials.py`): `_n` RG normal,
  B ambient occlusion, A height; `_s` R perceptual smoothness, G F0 (0-229) or a metal id (230+), B porosity / SSS, A
  emission. Our pack reads exactly that layout and nothing else.
- **A few times of day per match.** The lobby offers Morning 5000, Noon 6000, Dusk 11800 and Night 18000, plus an
  optional slow day cycle (`match/TimeOfDay.java`). Vanilla's sun crosses straight overhead, so at noon nothing throws a
  shadow you can see from above. Our pack tilts the sun's path (the format's documented `sunPathRotation`) so every
  preset has readable shadows, and grades each preset so grass stays green and night stays lit by lanterns.
- **The mod's own look pass.** `postfx/WorldLookPass.java` (run by `WorldLookClientEvents.java`) grades, hazes and
  edge-smooths the frame for players without a pack, and steps aside when `ShaderPacks.inUse()` says a pack draws.
  Our pack takes over the same jobs with the same intent: the same ortho-aware distance haze (distance from the
  ground at the bottom of the screen, not from an eye), the Enchanted grade's targets (#326: lawn hue about 72-82,
  value about 0.36-0.43 at noon), the same "never saturate the overlays" rule. Players with and without the pack
  should see the same game, the pack just deeper.
- **Fog of war.** Fogged ground is tinted in the chunk mesh (`fogofwar/FogExploration`, `FogTintingBlockColor`:
  explored 0x7882A0, never explored 0x242834, about 15% brightness). Our lighting must multiply that tint through
  everything, including sun, specular and bloom, or fog lights up again under the pack.
- **Mac players** have no Oculus (it needs Embeddium, which the Mac pack drops). They keep `WorldLookPass`. The pack
  is Windows and Linux.

### What our own pack can do that a general one cannot

- **A shadow map fitted to the RTS view.** A general pack centres its shadow map on the player's eye and spreads it
  over a fixed radius. Ours knows the view is an ortho footprint on the ground: the shadow vertex shader centres the
  map on the ground point at the screen centre and scales it to the footprint (plus a margin toward the sun for tall
  casters off screen). At the default zoom 30 a 2048 map covers about 60 blocks, roughly 30 shadow texels per block,
  several times sharper than a player-centred map; at full zoom-out about 6 per block, which still draws building
  shadows. Shadow softness is in blocks, so it looks the same at every zoom.
- **Distance from our camera, not an eye**, for haze and fog, the way `WorldLookPass` already does it. No dependence on
  the hand-depth convention: the pack knows the hand from its own `gbuffers_hand` program.
- **Our block and entity ids.** `block.properties` / `entity.properties` name our own blocks and entities, so foliage,
  water, lanterns, our building blocks and our units each get their own treatment (plants: soft or no sun shadow;
  lanterns: emission; units: a rim of light so they read on dark ground).
- **One look across the game**: the grade's numbers come from the same table as `WorldLookPass`, checked by the same
  tools (`tools/postfx_check.py`, `tools/world_look_model.py` from #326 once merged).

## Slice plan (cheapest wins first)

Each slice is one PR, with eyes stills before/after and the numbers below. A slice is done when its measurements pass
and lovish (with Itachi 1's review rules) has seen the stills.

**Frame cost.** lovish's GPU is not known to us (question 1); the budgets below are milliseconds of GPU time at
1920x1080 on a mid-range card of the RTX 3060 / RX 6600 class, and are re-based once we know his. Two measurements:

- **CI (every slice):** the eyes runner draws on Mesa llvmpipe (software), so its milliseconds mean nothing for a real
  GPU. It is used only as a ratio and a tripwire: the same scripted scene with our pack, with the old pack and with no
  pack; our pack's frame time over "no pack" must not exceed the old pack's at its shipped settings, and a slice that
  doubles the previous slice's cost fails.
- **lovish's PC (when he chooses, never by us):** slice 1 adds a short client command that holds a fixed camera over a
  fixed scene and writes the existing `[Frame]` p50 / p95 line with the pack on, then off (Oculus's toggle), into his
  log. He runs it once per slice if he wants; we read the log. Gate: p95 frame with the pack no more than 25% above
  pack off at 60 units, and no worse than the old pack was (lovish's Oct 23 log: 120 fps below 60 units).

| slice | what | GPU budget (1080p, mid-range) | measured by | judged by |
|---|---|---|---|---|
| 1 | sun shadows (fitted to the RTS view), tone curve and colour grade, distance haze and fog, sky and sun / moon, plants without dark stars | +2.5 ms over no pack (one 2048 shadow pass, one full-screen grade) | CI ratio vs old pack; lovish's bench line | below |
| 2 | labPBR from our maps: `_n` normals and AO, `_s` smoothness, F0 and metals (sun specular), emission | +0.5 ms | same | below |
| 3 | soft shadows (filtered, softer with distance from the caster), ambient occlusion (half resolution, ortho-aware), gentle bloom from bright and emissive pixels only | +1.5 ms | same | below |
| 4 | water (Fresnel sky reflection, depth tint, gentle waves), spell light (emissive glow on the ground near spells), team glow, fog of war drawn by the shader | +1.5 ms | same | below |

The whole pack: no more than the old pack cost on the same CI scene, and well under its cost on lovish's PC.

### Slice 1: sun shadows, grade, fog, sky

- Programs: `gbuffers_terrain`, `_entities`, `_block`, `_hand`, `_water` (plain for now), `_textured` / `_basic` /
  `_skybasic` / `_skytextured` / `_clouds` / `_weather`, `shadow`, one `composite` (lighting, haze, fog), `final`
  (tone curve, grade, vignette, edge smoothing as in `bat_postfx.fsh`).
- Sun path tilted so Noon still throws short readable shadows; light colour and strength per time of day from one table
  (Morning, Noon, Dusk, Night, and the moving day cycle in between).
- Shadow map fitted to the ortho footprint (above); first person falls back to an eye-centred map.
- Plants (`block.properties` foliage group) do not throw hard sun shadows; leaves throw softened ones.
- Translucent and overlay draws stay out of the shadow pass (our renderers already skip it; the pack does not undo that).
- Fog of war tint multiplies the final lit colour.
- `shaders.properties`: a short option screen (shadow quality, shadow distance, haze strength, grade on/off), sane
  defaults so testers never need it.
- Shipping: the pack folder in our repo, built into the tester pack; Oculus 1.8.0 + Embeddium back in (below).
- Eyes: a new `shader_pack: ours` choice and a look scene (below).
- Judged (measured on fixed crops, at Noon 6000 and Dusk 11800; Morning and Night as extra stills):
  - building shadow on lawn: shadow / lit luma 0.45-0.70 (visible, never black);
  - noon lawn: hue 72-85, value 0.36-0.43 (the Enchanted targets); Dusk lawn hue at least 55 (green-gold, not orange);
    mean blue / green over the lawn not below the plain lane's by more than 15% (no olive cast);
  - capitol white wall: p95 luma 0.80-0.92 (white, not clipped, not grey);
  - grass tufts: luma under a tuft at least 0.8x the open lawn beside it (no dark stars);
  - units: luma contrast between a unit and the ground around it no lower than the plain lane's on the 07 close stills;
  - haze at the top edge of the default RTS view within 2 points of `WorldLookPass`'s Enchanted haze;
  - fogged ground: mean value within 10% of the plain lane's on the 17 fog stills.

### Slice 2: labPBR from our maps

- Tangent-space normals from `_n` (the format gives `at_tangent`), its B as material AO, `_s` smoothness and F0 into a
  sun specular term (metals use their albedo as F0), `_s` A as emission. Height (`_n` A) is not used for parallax:
  at RTS distances it costs more than it shows.
- Judged: low-sun local contrast on the terrain crop up at least 15% over slice 1 (the relief shows); specular
  highlights present on the metal and wet stills but clipped pixels under 0.5% of the frame; the capitol does not turn
  back into "a white mountain" (ART-FAULTS): wall-to-roof luma step at least as large as slice 1's.

### Slice 3: soft shadows, ambient occlusion, gentle bloom

- Filtered shadows whose blur grows with the distance from the caster (a tall tower's tip is softer than its base),
  in blocks so it holds at every zoom.
- Ambient occlusion at half resolution from depth and normals, radius in blocks (ortho-aware), so building bases and
  unit feet sit in the ground.
- Bloom from a downsampled bright and emissive buffer, strength small: lanterns, spell effects and Enchanted's "soft
  glow" (#326), not a hazy screen.
- Judged: contact darkening at a building base (luma 1 block from the wall over 4 blocks away) 0.75-0.90; bloom
  halo radius under 1% of screen height around a lantern at default zoom, and zero bloom on a plain noon lawn
  (mean change under 1/255); no shadow acne or peter-panning on the 07 close stills.

### Slice 4: water, spell light, team glow, fog of war in the shader

- Water: Fresnel sky reflection, a depth tint from the depth under the surface, gentle normal waves; units crossing a
  river stay readable (the 12 river stills).
- Spell light: emissive spell effects light the ground near them through a cheap screen-space glow from the emissive
  buffer (no new light sources, no light-engine updates).
- Team glow: a faint team-coloured rim or ground glow under our units; needs one bit of team data the shader can read
  (question 4).
- Fog of war in the shader: fogged ground desaturated and cool with a soft animated edge, instead of the flat mesh
  tint; needs the fog state readable in the shader (question 4). Done only if it is cheaper than or equal to today's
  re-mesh path, measured with the fog scene's existing A/B lines.
- Judged: water crop shows sky hue at grazing view and ground hue straight down; team hue preserved on unit pixels
  (within 5 degrees of the plain lane); fogged crop max luma not above the fog tint level (no light in the fog).

## How it is judged

- **Measure, do not eyeball.** Every claim in a PR (hue, value, contrast, shadow ratio, frame ratio) comes with the
  crop it was measured on. Itachi 1's art review rules apply (`itachi1-lessons-2026-10-28.md`): prove the subject is in
  the still before critique; judge colour at noon (day time 6000) named per still, low sun in its own still; time
  captures by drawn frames, not seconds; one eyes branch per lane; measure every critic claim; one change per element
  per round so each before/after is attributable; a critic fix that contradicts lovish's own ask goes to lovish.
- **Art judging belongs to lovish and Itachi 1.** We bring stills and numbers; they decide what looks right.
- **Eyes workflow (`.github/workflows/eyes.yml`).** Slice 1 adds:
  - `shader_pack: ours`: packs this repo's shader pack folder into the run (no download), same Oculus / Embeddium setup
    as the old lane; publishes to its own branch (`eyes-shaders-ours`) so the old lane's stills stay as the baseline;
  - a look scene: one pad with a capitol, a lawn with grass tufts, a pond, lanterns, a dozen units and a fogged edge,
    photographed from the default RTS view, RTS close and first person at Noon 6000 and Dusk 11800 (plus Morning 5000
    and Night 18000), each still named with its day time, plus the fixed-camera frame-time lines.
  - A before/after is two dispatches pinned to the same `build_run` with `keep_only`: `shader_pack: complementary` (CI
    only, allowed for now) and `shader_pack: ours`, plus the plain lane. Runs are dispatched only after the PR's build
    has compiled the mod, as few as the slice needs, and never cancel anyone else's.
- **A measuring script** (`tools/shader_judge.py`, slice 1) reads the stills of two or three lanes, cuts the fixed crops
  the look scene defines, and prints the numbers above as a table for the PR.

## Shipping

- The pack lives in the repo at `shaderpacks/BeyondAndTabs/` (`shaders/` inside, plus `LICENSE` and a README), built
  into a zip by the pack build and shipped in the tester pack's `shaderpacks/` folder; Windows and Linux only.
- The pack-removal session is taking Complementary and Oculus out of the tester pack now. Slice 1 adds Oculus 1.8.0
  (and Embeddium 0.3.31, its pair, already shipped) back with our pack as the only shader pack, and a fresh install
  writes `config/oculus.properties` with our pack selected. The old pack's preserved settings file is no longer
  written; one left on a tester's disk is harmless.
- Licences of everything shipped for the look:

  | item | licence | how it ships |
  |---|---|---|
  | our shader pack | ours (GPL v2 or later unless lovish picks otherwise) | in our repo, built into the pack |
  | Oculus 1.8.0 | LGPL-3.0 | unmodified, fetched from its official Modrinth file by packwiz |
  | Embeddium 0.3.31 | LGPL-3.0 | already in the pack, unchanged |
  | our textures and labPBR maps | ours / CC0 scans (`NOTICE.md`) | unchanged |
  | Complementary Reimagined | not ours, permission refused | **never shipped**; CI eyes lane only, while allowed |
  | Vanilla Normals Renewed | free with a link | CI eyes lane only, as today; not shipped |

## Open questions for lovish (via the coordinator)

1. **Your GPU and screen size?** The frame budgets assume a mid-range card at 1080p; we re-base them on yours.
2. **Noon shadows:** OK to tilt the sun's path so Noon still throws short shadows you can see from above? (Real noon
   overhead light is flat; a tilt is the usual fix.) If yes, roughly how strong: slight (about 15 degrees) or clear
   (about 30)?
3. **The target look:** keep the Enchanted intent you picked on Oct 28 (dimmer, more saturated, golden, whimsical) as
   the pack's default grade, so players with and without the pack see the same mood?
4. **Data from the mod to the shader** (slice 4 only): team glow and fog of war in the shader need the mod to pass a
   little data to the pack (for example a team bit in the unit's vertex colour, or fog in a spare vertex channel). OK
   for slice 4 to change our renderers for that, or keep slice 4 to water and spell light?
5. **The old pack in CI:** may the eyes lane keep photographing it as the "before" until our slice 3 lands, then drop
   it? (Your Oct 29 decision allows CI use "for now".)
6. **Licence for the pack:** same as the mod (GPL v2 or later), or something more open so others can learn from it?
7. **A name** for the pack, if you want one (working name "Beyond and Tabs").
