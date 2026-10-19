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
- Each revamped extractor is its own module (`tools/mex_<key>.py`, t1()/t2()), wired by gen_structures.REDESIGNED_MEX.

## Next (in this order; check lovish's reference folder for new art before each)
1. Gravebound metal extractor (dark gothic; ref 47 gargoyles, 42-44, 73, 90-91) - keep or rethink its rig.
2. The other extractors: Horde, Tide, Storm (in progress).
3. Capitols, then T1 labs, then the rest of the economy (wind generators, converters, vaults), then defences.
