# bots-4 progress

Branch `bots-4` (from origin/main 0882349). Lane: bot/ (plus HeroBot, the bots' hero brain). Task: the bots play the
whole game - beasts, game enders + Ward Spire, heroes, start positions on every map, and playtest Oct 12 finding 1.

## Done
- Step 1 (WIP, pre-fix): reproduction GameTest for finding 1, `BotsFourGameTests.bots_seated_far_apart_on_a_big_arena_all_start`
  (batch `bots4_soak`), seats four hard bots through the real Watch Battle path (BotServerEvents.seatNear, the Far ring,
  unloaded chunks) at a centre where the old blind search puts bots in water. `bot/BotTerrain` reads a column without
  loading it (real chunk if loaded, else the chunk generator's base terrain).

## Finding 1 analysis (so far)
- `BotServerEvents.findBotStart` read `Level.getHeight` on chunks that are not loaded: that returns the minimum build
  height (-64) and the block under it is void air, so every unloaded candidate looked like dry, flat ground. With Far
  spawns (300-525 blocks from the spectator) every candidate is unloaded: bots were seated blind, at y=-64, in the sea
  if that is where the first candidate fell.
- Workers seated on water float and cannot path (units carry a WATER path malus of -1): a capitol placed on the shore is
  never built, the pool stays untouched, and `lostStart` never fires because the workers are alive.
- The seat's y=-64 also blinded BaseWildlifeControl (its sweep box and spawn veto are +-20/40 blocks round y=-64) and
  BotArmy/BotSites' height bands until the capitol stood.

## Next
- Fix: terrain-aware findBotStart, grounded addBotAt, home y from the ground, stalled-start safety net (keep the reseat).
- Beasts, game enders, heroes, start picks, sites, difficulty, tests, design/bot.md.

## Known failures
- (none yet)

## Proposals (outside this lane)
- `MultiplayerLobby.siteOk` / `groundSite` and `MapMatch.seat` (via `MetalPatches.solidTop`) read heightmaps of chunks
  that may not be loaded: the same blindness for human starts on a big ring. BotTerrain.probe is public for them.
