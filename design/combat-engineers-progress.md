# combat-engineers - progress (save points)

Branch `combat-engineers` from origin/main d73acee. Task: lovish's vision #18 - one combat engineer per faction.
Design: design/combat_engineers.md.

## Done
- Design doc (roster audit, BAR references, per-faction stats and abilities, counters).

## Next
1. Java: CombatEngineers rules + interface, CombatEngineerUnit base + 6 units, WreckerUnit (Horde), prod items,
   7 abilities, registrations, renderers, lang.
2. Bot helper (bot/BotCombatEngineers) + one hook line in BotPlayer.think.
3. GameTests (gametest/CombatEngineerGameTests).
4. Skins/portraits (tools/gen_hd_skins.py painters).
5. tools/unit_stats.py + balance_audit tier, regenerate design/balance.md.

## Known failures
- none yet (no CI run).

## Proposals (outside this lane)
- Building repair ignores build power (RoN builder-count loop) - BAR repairs at build power.
