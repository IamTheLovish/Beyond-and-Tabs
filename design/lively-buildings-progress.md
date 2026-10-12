# lively-buildings: progress note

Branch `lively-buildings`, based on origin/main 9b69b0a. Brief: design/lively_buildings.md (lovish: "buildings should feel
lively"). Regenerate only the changed structures: `python3 <scratch>/regen.py`-style - import tools/gen_structures.py and
write just wind_generator*.nbt + geothermal_plant.nbt to BOTH assets/ and data/ (a full `python3 tools/gen_structures.py`
rewrites ~34 unrelated nbt files that main ships hand-tuned; don't commit those).

## Done
- [x] 7 energy buildings rebuilt in tools/gen_structures.py (sun/grave/horde/verdant wind generators, tide/storm new
      functions, brass boiler), geothermal_plant(); pocket audit clean.

## Next
- [ ] design/lively_buildings.md (3 concepts x 7 factions, scored)
- [ ] WindGenerator: SAIL_HUBS only "" and "_nether"; NO_SAILS the rest; MOTIONS (BELL/PETALS/WHEEL/VANES)
- [ ] WindmillRenderClientEvents: motions; tools/check_sail_hubs.py motions; sail GameTest (mills < 6 -> < 2) + motion test
- [ ] BuildingAmbienceClientEvents idle life + client config "Building ambience"
- [ ] BiomePalette (snowy/arid/lush swaps at placement) + GameTest
- [ ] GeothermalPlant class, registration x7, lava validator, lang, GameTests

## Known failures
- none yet (CI not run)
