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

## Next
- RoN capitol detail overlays (Town Centre, Mausoleum, Central Portal) in tools/gen_structures.py + buildTimeBlocks
- capitol ambience spots / Mausoleum soul bell
- gametest/BiomeBaseGameTests.java
- design/biome_bases.md, persistence.md row

## Known failures
- none yet (CI not run)

## Proposals (outside this lane)
- NetherZone.netherConvertTick allocates a BlockPos for every cell of a (2r)x(r)x(2r) box every 5 ticks (r up to 30:
  ~108k objects per zone per convert tick) for 75 s after every Horde portal goes up. A plan/cursor walk like
  FactionAura's would make it a few hundred reads a pass.
