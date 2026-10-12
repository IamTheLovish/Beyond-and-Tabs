# combat-engineers - progress (save points)

Branch `combat-engineers` from origin/main d73acee. Task: lovish's vision #18 - one combat engineer per faction.
Design: design/combat_engineers.md.

## Done
- Design doc (roster audit, BAR references, per-faction stats and abilities, counters, bots, tests, skins).
- Java: CombatEngineers rules + interface, CombatEngineerUnit base + 6 units, WreckerUnit (Horde), prod items (shared
  Tier 2 gate), 7 abilities (UnitAction values appended at the end), registrations, renderers, lang. CI compiled it
  (run 38252625785, "Build the BAR-on-RoN mod" green).
- Bot helper `bot/BotCombatEngineers` + one hook line in `BotPlayer.think`.
- Skins and portraits: seven painters in `tools/gen_hd_skins.py`, generated with `--only`; `check_building_icons.py`
  clean.
- GameTests: `gametest/CombatEngineerGameTests` (13 tests); BrassworksGameTests skips the Pioneer in the Assembly Works'
  eight-unit count.
- `tools/unit_stats.py` tier `engineer`, `balance_audit.py` table, `design/balance.md` regenerated.

## Next
- Watch CI on the latest push; fix whatever the GameTests report.

## Known failures
- main's own `production_buildings_place_every_unit_3` (the Jade Qilin rally spawn at the Celestial Observatory) failed
  on the first branch run (6a84357, design doc only) - not ours.
- Run 38252625785 (a291c54) failed its GameTests but GitHub kept no annotations for it; the Assembly Works count in
  BrassworksGameTests was one certain cause (fixed since).

## Proposals (outside this lane)
- Building repair ignores build power (RoN builder-count loop) - BAR repairs at build power.
- Livery chest pieces hide the Field Mason's, Bonesetter's and Wrecker's skins (unit_skins.md finding 2).
- An "Engineer" role line on the UnitInfoCard.
