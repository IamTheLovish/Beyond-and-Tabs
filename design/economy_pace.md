# Economy pace

lovish: "make the resource speed variable to allow slower players more time to build and spread their APM, with
faster speeds for players familiar with the game; the default should be what most competent players feel
comfortable with."

## The option

A host-only lobby setting in both the skirmish lobby (`SkirmishSetupScreen`, sent with `SkirmishServerboundPacket`)
and the multiplayer lobby (`MultiplayerLobby` action `SET_PACE`, shown to everyone in the lobby snapshot):

| Pace | Multiplier |
|---|---|
| Relaxed | 0.75 |
| **Standard** (default) | **1.0** |
| Fast | 1.35 |

`resources/EconomyPace` holds the match's pace. It is set when the match starts, saved with the world
(`MatchStateSaveData`, key `pace`; a world saved before the option loads as Standard) and synced to clients on the
economy packet (one byte, already sent 4x a second).

## What it scales (one multiplier, all together)

- **All income**: capitol base income, extractors, generators, every building's income
  (`EconomyServerEvents.recalculateIncomeAndStorage`), energy converters' capacity, capture-site income (mine and
  shrine), and the rate at which workers strip wrecks.
- **Construction and production speed**: build power on every site (`BuildingPlacement.tickFlowConstruction`) and
  every factory's production speed (`ProductionItem.tick`, with the same factor in the client's prediction).
- **Bots**: their build-order clock runs in economy minutes (`BotPlayer.paceMinutes`), so "labs from minute 1,
  T2 at minute N, attack thresholds by minute" fire when the economy behind them actually exists.

Costs, storage and the value of a wreck or a tree are unchanged: the pace speeds up or slows down how fast
resources flow, not what anything is worth. A Relaxed match plays the same build orders more slowly; it is not a
poorer match. Because income and spending speed move together, the BAR balance between "how much I make" and "how
fast I can spend it" (stall, overflow) is the same at every pace.

**Not scaled**: movement, combat, weapon ranges and reloads, ability cooldowns, mana-well charge, the camp's hire
timer, and tree felling energy (a fixed 5 per log). Those are what the player's hands and eyes deal with; slowing
them would make Relaxed feel sluggish instead of giving more time to think.

## Why these numbers

- **Standard 1.0** is the pace every number in `design/balance.md` and the bot tuning were playtested at; lovish asked
  for the default to stay what competent players are comfortable with, so it does not move.
- **Relaxed 0.75**: a 4:00 timing lands at 5:20, so every economic decision gets a third more wall-clock time
  (1/0.75 = 1.33). That is real headroom for a newer player who builds one thing at a time, while a 25-minute
  match stays around 33 minutes instead of dragging past 40. A lower setting (0.5) would double match length while
  combat stays at full speed, so armies would spend long stretches with nothing to fight over.
- **Fast 1.35**: the mirror of Relaxed. Relaxed stretches time by 1.33x, Fast compresses it by 1.35x, so the two
  steps are the same size either side of Standard (ln 1.33 = 0.29, ln 1.35 = 0.30). A 25-minute match becomes about
  18.5 minutes and build-queue clicks per minute rise by about a third, which fits the spare APM of players who
  already know the build orders. Above about 1.5 the unscaled movement falls behind: an army crosses the map while
  the base it was sent at has already teched past it, and the game turns into pure macro.

## Where it shows

The match menu header ("12:30 - Sunforged Kingdom - Fast pace") and the results screen next to the match length.
The lobby buttons explain each pace in a tooltip.

## Tests

`economy_pace_scales_income_and_build_speed` (GameTest): a built wind generator's income and a queued Grunt's
progress per tick are exactly the Standard figures times 0.75 and 1.35. Tests pin the pace per owner
(`EconomyPace.setTestOverride`) because the match pace is world-wide and the GameTests run in parallel.
