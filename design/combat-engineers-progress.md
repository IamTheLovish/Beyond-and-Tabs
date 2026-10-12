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
- Final push: the real-build test now covers all seven engineers (one anti-raid tower each).

## CI history
- 0a9c814: all green (build and every GameTest).
- 0fdc610: one failure, `production_buildings_place_every_unit_6` (drydock, no rotation: the Sky Corsair, a flyer, "in
  a wall" 40 ticks after spawning). The same Drydock list passed on 29afca7 and 0a9c814 - a flake of that spawn test,
  like main's own `production_buildings_place_every_unit_3` (Jade Qilin rally spawn) on the design-doc-only run 6a84357.
- 29afca7: one failure, BrassworksGameTests' Assembly Works count (the Pioneer) - fixed in 0fdc610.
- a291c54: GameTests failed but GitHub kept no annotations; the Assembly Works count was the certain cause.

## Proposals (outside this lane)
- Building repair ignores build power (RoN builder-count loop) - BAR repairs at build power.
- Livery chest pieces hide the Field Mason's, Bonesetter's and Wrecker's skins (unit_skins.md finding 2).
- An "Engineer" role line on the UnitInfoCard.
