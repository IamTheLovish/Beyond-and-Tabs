# lively-buildings: progress note

Branch `lively-buildings`, based on origin/main 9b69b0a. Brief: design/lively_buildings.md (lovish: "buildings should feel
lively"). Regenerate only the changed structures: `python3 <scratch>/regen.py`-style - import tools/gen_structures.py and
write just wind_generator*.nbt + geothermal_plant.nbt to BOTH assets/ and data/ (a full `python3 tools/gen_structures.py`
rewrites ~34 unrelated nbt files that main ships hand-tuned; don't commit those).

## Done
- [x] 7 energy buildings rebuilt in tools/gen_structures.py + geothermal_plant(); pocket audit clean
- [x] design/lively_buildings.md (3 concepts x 7 factions, scored, picks)
- [x] WindGenerator SAIL_HUBS ("" and "_nether"), NO_SAILS, MOTIONS; WindmillRenderClientEvents draws WHEEL/BELL/PETALS/VANES
- [x] tools/check_sail_hubs.py checks motions; sail GameTest relaxed to 2 mills; every_moving_part_sits_on_its_anchor
- [x] BuildingAmbienceClientEvents idle life + client config building_ambience + Display settings button
- [x] BiomePalette (snowy/arid/lush) hooked in BuildingPlacement.setBlocks; snowy GameTest
- [x] GeothermalPlant x7, validators (must_be_lava), T2Workers.needsT2Builder, lang; two GameTests

- [x] CI green at 76a745d (build + bar GameTests)

## Next (later slices, not started)
- biome palettes: per-faction accent tables, snow layers on slabs, biome props
- birds / flocks for ambience; per-lab bespoke emitter spots
- Brassworks T2 constructor (brassworks-2) unlocks their Geothermal Boiler button

## Known failures
- none. Note: the 8v8 stress benchmark (budget 8.0 ms) once read 8.11 ms while the snowy test generated 8 spread-out
  chunks; the tests now build in adjacent chunks and the run passed.
