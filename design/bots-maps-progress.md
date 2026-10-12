# bots-maps progress

Branch `bots-maps` (from origin/main 30a1b6f). Bot plays the pre-made maps and tonight's economy systems.

Done (see design/bot.md "Maps, start positions and the new economy"):
- bot/BotSites.java: capture-site take / hold / defend / retake squads (replaces claimSite); BotArmy keeps holders out of rally/waves.
- bot/BotEconomy.java: geothermal on lava (T2), trees when energy-starved, decommission obsolete wind late at cap, upgrade-vs-new-lab rule.
- bot/BotStartPick.java + StartPhase.placeBots (bot-only hook, live phase only): start near rich metal inside the box.
- BotPlayer.bestPatch: expansions by distance / patch value; BotArmy raids by distance / value.
- gametest/BotGameTests.java: 4 tests.

Next / status: push, wait CI, read [Soak] notice (before = main 30a1b6f run), record numbers in design/bot.md.
