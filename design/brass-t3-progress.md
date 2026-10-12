# brass-t3 progress (slice 4 of design/brassworks_plan.md)

Branch `brass-t3`, based on origin/main 30a1b6f. Save points are pushed; CI is the compiler.
Full as-built notes: design/brassworks_plan.md section 10.

## Done
- Great Forge T3 lab (`building/buildings/brass/GreatForge`, hotkey2): Tier 3 + Assembly Works + Engineer-Captain
  (`T2Workers.isT3Lab`), levels L1-L3, structure + levels (`great_forge*.nbt`), HUD icon, LAB3 glyph, save case.
- Iron Leviathan Engine (`unit/units/brass/IronLeviathanEngineUnit`, ravager at 1.6x + foundry layer, own skin and
  portrait), BombardShell cannon, Heat 35/shell, drones (`ClockworkDroneUnit`, cap 4, 2 per 30 s), x2 from Sappers and
  Drill Burrowers (BrassUnitEvents), standard experimental price, arrival announced.
- Bot kit t3Building/t3Unit; bot Overclock counts only cool machines; Rail Battery own HUD icon.
- Duel T3 row; unit_stats roster; GameTests appended to gametest/BrassworksGameTests.

## Next
- Watch CI; fix compile errors / GameTest failures.

## Known failures
- none yet (first CI pending)
