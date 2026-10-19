# Building revamp - progress (resume from here)

Task (lovish, Oct 19 2026, 03:20 ET): "start revamping all the buildings they need work ... all the buildings dont meet
our standard". "the metal extractors could be fountains with angels or something more creative". "use reference art to
get inspired and try to create articulate figures and buildings with that". Refine each one as far as it goes.

## The standard (lovish's art notes, "THE BUILDING STANDARD", Oct 17)
Every faction building - labs T1-T3, economy T1-T3, extractors, capitols, defences, walls - is built to the density of
his Minecraft references, not a box with a roof:
1. **Three depths on every wall**: a pier / buttress plane, the wall plane, a recessed window or door plane, 1-2 blocks
   apart. No flat face larger than 3x3.
2. **A broken top silhouette**: spires, pinnacles, finials, chimneys, crenels - the RTS camera sees roofs and tops first.
3. **Mixed materials per surface**: 3-4 blocks per material zone, stairs and slabs on every edge, walls / fences as
   mullions.
4. **Real architecture per faction**: White Angel (Sunforged) classical T1-T2 -> gothic T3; Death (Gravebound) dark
   gothic and blackstone; Verdant overgrown; Stormcallers towers and observatories; windmills for the wind generators.
5. **Dressing around it**: paths, gardens, fountains, lanterns, graves - walkable, never trapping units.
6. **Tiers look different**: T2 and T3 add storeys, spires and ornament to the same building.
7. **Lights**: lanterns and lit windows are the warm points at night and under shaders.

The check: `tools/structure_render.py` of the building (close and at RTS scale) beside the reference it draws on.
Inspiration only - never trace a build. Art and feel are lovish's call: every building lands as a before/after sheet
in `design/buildings/` for him to judge.

## Owner decisions (brainstorm, Oct 19 03:27-03:41)
- **Cohesion**: "I want the buildings to look like they match each other so when bases get bigger they look cohesive
  and beautiful". Each faction has a STYLE KIT - 3 main blocks + 1 accent, one silhouette motif, one trim, one light,
  one signature detail - and every building of the faction is built from it:
  - White Angel / Sunforged: white stone + quartz, gold trim, blue glass, lanterns; tiered domes.
  - Death / Gravebound: deepslate + blackstone, bone, soul lanterns, green glow; spires.
  - Ashborn (renamed from Piglins - must NOT resemble Reign of Nether's Piglins: no pigs, gold, gilded blackstone,
    nether brick, portals): basalt + tuff + obsidian, magma cracks, bronze trim, fire bowls; stepped volcano-temples.
  - Brassworks: copper (weathering) + dark oak, iron chains, smoke; pipes everywhere.
  - Stormcallers: jade green + dark oak, gold, paper lanterns; curved pagoda roofs.
  - Tidewrought: prismarine + sand, driftwood, sea lanterns; lighthouse towers.
  - Verdant: mossy stone + living wood, leaves, glow berries; roots and canopies.
- **Extractors, one per faction**: Sunforged fountain; Gravebound bone-ribbed well with chains over a green pit;
  Ashborn stone-giant smith hammering the ore over a vent; Brass steam drill; Storm jade spire with a lightning rod;
  Tide tide-pool pump with a waterwheel (T2 lighthouse pump tower); Verdant a tree wrapping the patch. Every one
  shows one glowing ore core from the RTS camera.
- **Capitols**: "a lot bigger than the current capital size, a lot taller, a lot more detailed", "the center piece of
  the base" that everything connects to and flows from - "don't break the game". No fixed paths (players build
  anywhere): the plaza edge blends on every side. Saved for later: paths laid by the game as buildings are placed.

## Rules learned from the code
- Footprints are fixed by the building classes (an extractor is 5x5); a level may grow taller, never wider.
- Tier 2 may only add or replace blocks (`check_mex_tiers`, `gen_lab_levels.py` asserts the same for labs);
  waxed copper is the extractor's tier-2 marker (MetalExtractor.getUpgradeLevel).
- Regenerating `tools/gen_structures.py` rewrites some untouched .nbt byte-differently: commit only the files that
  really changed (git checkout the rest).
- Real water is allowed in a structure (Reign of Nether's Healing Fountain does it; BuildingPlacement clears fluids
  first when a building falls): sources only, walled on every side.
- A drawn moving part (BuildingParts.RIGS) pins its structure's air cells: change the structure, change the row and
  `tools/check_parts.py`.

## Done
- **Sunforged metal extractor -> the Angel Fountain** (T1 + T2), `design/buildings/sunforged_metal_extractor.png`.
  A stepped quartz basin of still water with lapis thresholds and a gold sun under the water; candle pedestals on the
  corners; a column with a bowl and four falling sheets (light-blue panes); a white angel on it - flared robe, quartz
  shoulders, wings spread behind as two fans - lifting the glowing sun over its head on thin end-rod arms (the
  Sunforged emblem, its halo and the night light at once). T2: copper in the parvis corners (marker), the candles
  grown into gold-capped lantern posts, lit wing tips, a crown of rays over the sun. Its drawn headframe machinery
  (sheave wheel, skip, pennant) is gone with the headframe. The basin's corners are lit sea lanterns, not open water:
  open corners were pockets a unit could not climb out of (audit_structure_pockets.py).
- **Verdant metal extractor -> the Root Spring / Elder Spring** (`tools/mex_verdant.py`),
  `design/buildings/verdant_metal_extractor.png`. A ruined mossy well with still pools over the ore, broken pillars,
  candles; a living oak grown out of its back rim, roots clawing the patch, its trunk a stag-dryad head (birch mask,
  glowing eyes) with antler branches. T2: a flowering crown with a shroomlight heart and glowing fruit, a lantern post
  regrown, the ore under the water a copper vein (the marker).
- **Brassworks metal extractor -> the Iron Golem Drill** (`tools/mex_brass.py`),
  `design/buildings/brass_metal_extractor.png`. A squat brass automaton gripping a screw drill bored into the ore, a
  glowing furnace belly, eyes behind an iron visor, weathered copper plates, a soot boiler on its back with a smoking
  stack. T2: a second stack, a cog crown, shoulder lanterns, a copper pressure dome (marker), a lit firebox. Open
  critique: the head is small beside the stacks - a later pass could enlarge the helm.
- **Stormcallers metal extractor -> the Jade Thunder Spire / Storm Needle** (`tools/mex_storm.py`),
  `design/buildings/storm_metal_extractor.png`. A slim jade pagoda spire on dark-oak legs whose lightning rod is the
  patch's highest point; the strike has crystallised the ground - amethyst round a glowing core on a gold seam; roofs
  stepping 5-3-3 with gold corners and upturned tips, chimes under the eaves, lantern posts. T2: a third roof, eight
  eave rods, more chimes and crystal, a copper band (marker). (The renderer draws amethyst buds as cubes; in game they
  are small crystals.)
- **Ashborn (Piglins) metal extractor -> the Stone Giant** (`tools/mex_ashborn.py`, variant `_nether`),
  `design/buildings/ashborn_metal_extractor.png`. A hooded basalt giant, the Ashborn's first smith, built waist-deep
  into a stepped volcano forge: right fist raising a bronze hammer high over the anvil, left fist holding the glowing
  ore in chain tongs, a glowing face under the hood, magma cracking his chest and cracks of glow across the floor, fire
  bowls on obsidian. T2: a spiked bronze crown and shoulder plates, four fire bowls, more cracks, a bronze anvil band
  (marker). Nothing of RoN's Piglins (no pigs, gold, gilded blackstone, nether brick).
- **Sunforged capitol (Town Centre) -> the Temple of the Sun**, 15 x 20 x 15 (was RoN's 11 x 9 x 11), 1590 blocks
  (`tools/capitol_town_centre.py` build_grand(), written by gen_capitol_details.py), `design/buildings/sunforged_capitol.png`
  (the compact 11x9x11 fallback build() is drawn there too). The same on all four sides: a stepped stylobate, a
  quartz-and-lapis cross to a gold sun at the heart, quartz columns with gold capitals framing porticoes, blue lancets,
  doors on every axis from the spawn cell, a stair cornice, a balustrade with candles, a drum of pillars and blue glass,
  tiered dome to a gold crown, corner pinnacles with spires and banners, angels on the plaza corners, and a garden edge
  (grass, moss, calcite flags, flowers) that fades into the ground on every side. The Sunforged style kit is written in
  its docstring for the other Sunforged buildings. Java: the GameTest box, the glint spots, the wildlife test's pad, and
  BuildingPlacement's per-block pacing now timed by buildTimeBlocks - the capitol laid at a readied start (command-
  placed) took longer for every detail block (it would have been ~80 s instead of ~16 s).
- Each revamped extractor is its own module (`tools/mex_<key>.py`, t1()/t2()), wired by gen_structures.REDESIGNED_MEX.

## Next (in this order; check lovish's reference folder for new art before each)
1. In progress: Gravebound (bone well), Tide (tide-pool pump) extractors;
   then the other six capitols at the same scale (the Temple of the Sun is the bar).
2. Later: the Angel Fountain could take the kit's blue glass and a tiered dome; the Brass golem's helm is small.
3. Capitols, then T1 labs, then the rest of the economy (wind generators, converters, vaults), then defences.
