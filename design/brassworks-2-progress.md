# brassworks-2 progress (slice 2 of design/brassworks_plan.md)

Branch `brassworks-2`, based on origin/main 9b69b0a. Save points are pushed; CI is the compiler.

## Done
- Registrations for the T2 lab and seven T2 units (costs, config, ProductionItems, EntityRegistrar, ItemRegistrar,
  spawn eggs, attributes, renderers, Factions, BuildingSaveData, T2Workers, lang).
- Units: EngineerCaptain (Tinker subclass, 2x build, 2x machine repair), SteamJuggernaut (Ravager), TeslaWalker
  (Pillager, 3-target arc), GyrocopterGunship (Parrot flyer, strafing run), DrillBurrower (Silverfish, Burrow ability,
  Concealment hook), RepairCrawler (CaveSpider, machine-only aura), ClockworkSpider (Spider, low-wall climb).
- brass/MachineRepair (idle Tinkers mend machines; Engineer-Captain 2x).
- AssemblyWorks building (Tier 2 gate, levels L1-L3).

## Next
- structure assembly_works (gen_structures.py) + levels (gen_lab_levels.py), pocket GameTest list.
- skins (gen_brass_skins.py), portraits, HUD icon (gen_hud_icons.py).
- UnitInfoCard roles, unit_stats roster, balance audit, DuelGameTests T2 row, bot kit + habits.
- GameTests at the end of SkirmishGameTests.
- slice 3: Rail Battery, heat gauge.

## Known failures
- none yet (first CI pending)
