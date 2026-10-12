# biome-bases: progress note

Branch `biome-bases` (from origin/main d73acee). Brief: lovish's vision list (Oct 11) #5 biome-aware faction bases and
#4 lively individual buildings. Design write-up: `design/biome_bases.md`.

## Done
- `building/BiomePalette.java` rewritten: 8 biome families (TEMPERATE = home look, SNOWY, TAIGA, DESERT, BADLANDS,
  SAVANNA, JUNGLE, SWAMP), per-faction material-swap tables for all seven factions (identity maps built at class
  load), shared weathering (moss / cracks / sandblasted and verdigris brass), snow caps, protected blocks, a coverage
  query (`isDecided`) for the GameTest. The snowy GameTest's two references renamed (Kind -> Family).
- `building/FactionAura.java` + `FactionAuraServerEvents.java`: terraform auras round capitols and labs (6 styles; the
  Horde keeps its RoN Nether Zones), budgeted passes, recede on destruction/decommission/rule off, saved through
  MatchStateSaveData. Gamerule `doFactionTerraform` (default on). Registered in ServerEventRegistrar.

- RoN capitols: `tools/gen_capitol_details.py` (new) overlays the pristine RoN copies in `tools/capitol_bases/` and
  writes town_centre / mausoleum / central_portal into both trees; `buildTimeBlocks` = RoN counts (305/364/314) in
  TownCentre / Mausoleum / CentralPortal; pocket audit clean. `building/CapitolLife.java`: the Mausoleum bell motion
  (WindmillRenderClientEvents draws capitol motions); BuildingAmbienceClientEvents: CAPITOL_SPOTS.
- `gametest/BiomeBaseGameTests.java`: 9 tests (coverage, table shapes, families, desert vs plains, aura budget and
  patches, registry budget, aura styles, capitols, cost measurement logged as a `[Stress] biome-bases` line).
- Docs: `design/biome_bases.md`, persistence.md row, pointers in lively_buildings.md / building_detail.md, contact
  sheet `design/building_preview_ron_capitols.png`.
- Fixed after review: a reload no longer makes every restored aura recede (the start delay only gates NEW auras);
  idle auras skip the obstacle walk; a snow layer the heightmap counts is treated as cover; receding on ground someone
  else changed removes the aura's own decoration.

- CI 38253121132 (132b95a): everything green except `biome_palette_covers_every_faction_block` (Gravebound's
  stockpile cartography table and the sculk catalyst's sculk had no decision): both are now shared identity.
- Starting types: they used to be kept everywhere, which left the many buildings whose main material is a starting
  type (Castle, Watchtower, Lotus Academy, Tidewrought aprons, Mausoleum) nearly untouched. Now they swap like any
  other material; the swap remembers the foundation positions it changed (`BuildingPlacement.biomePaletteStarts`) and
  BuildingServerEvents' queue asks `BiomePalette.joinsStartQueue`, so the instant queue is exactly the drawn one
  (GameTest `biome_palette_keeps_every_foundation_queue`).
- The cost test times growing and idle aura passes apart.

## Next
- CI results; fill the performance numbers in design/biome_bases.md section 5 from the `[Stress] biome-bases` notice.

## Regenerating
- capitols: `python3 tools/gen_capitol_details.py [town_centre] [mausoleum] [central_portal]` (only those files)
- never a full `tools/gen_structures.py` run for this lane

## Known failures
- 38251272961 (fe9371c): green. 38253121132 (132b95a): the coverage test only (fixed above). 2e4085d's run carries
  the same coverage failure (the fix came after it).

## Proposals (outside this lane)
- NetherZone.netherConvertTick allocates a BlockPos for every cell of a (2r)x(r)x(2r) box every 5 ticks (r up to 30:
  ~108k objects per zone per convert tick) for 75 s after every Horde portal goes up. A plan/cursor walk like
  FactionAura's would make it a few hundred reads a pass.
