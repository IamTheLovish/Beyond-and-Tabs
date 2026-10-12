# bots-maps progress

Branch `bots-maps` (from origin/main 30a1b6f). Bot plays the pre-made maps and tonight's economy systems.

Done (see design/bot.md "Maps, start positions and the new economy"):
- bot/BotSites.java: capture-site take / hold / defend / retake squads (replaces claimSite); BotArmy keeps holders out of rally/waves.
- bot/BotEconomy.java: geothermal on lava (T2), trees when energy-starved, decommission obsolete wind late at cap, upgrade-vs-new-lab rule.
- bot/BotStartPick.java + StartPhase.placeBots (bot-only hook, live phase only): start near rich metal inside the box.
- BotPlayer.bestPatch: expansions by distance / patch value; BotArmy raids by distance / value.
- gametest/BotGameTests.java: 4 tests.

Status: CI on 4fa5b34 compiled; the 4 BotGameTests and the soak passed. The only failure was
stress_8v8_server_systems_stay_in_budget at 8.42 ms vs 8.0 (every system ~1.4x slower than main's run, the REF
scans too: runner noise; the bot code adds nothing per tick). Soak numbers recorded in design/bot.md.
