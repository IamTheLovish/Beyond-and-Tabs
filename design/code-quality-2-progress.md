# code-quality-2 - progress

Branch `code-quality-2`, based on origin/main 102ba10. Findings go in `design/code_review.md` (round 2 section).
Rewrite plan: `design/ron_rewrite_plan.md`.

## Done
- Integration audit pass 1 (packets, save/load, renderers, client null-safety): findings R2-01..R2-05.
- Fixes: R2-01 game ender charge + announced flag and Ward Spire stockpile saved (generic
  BuildingPlacement.writeSaveState/readSaveState hook); R2-02 defence tower tracer through BarFx (was ~16 particle
  packets per shot per player); R2-03 no ForgeData tag created on every entity join; R2-04 hero packet uses the
  level's id index; R2-05 hidden traps hidden from enemy Hero-role players.
- Stress scenario: one hero per owner, 4 gates / 6 traps / 3 raid towers / a game ender / a charged ward per owner,
  new budgeted system "defences+game enders".
- GameTests (RegressionGameTests): game_ender_charge_and_ward_stockpile_survive_a_reload,
  hidden_trap_is_hidden_from_an_enemy_hero_role_player.

## Next
1. Integration audit of 30a1b6f..102ba10 (heroes, bots-maps, walls/traps, Brassworks T3, big units/game enders,
   unit skins, building detail): save/load, packets (PacketAuth + rate limits), client null-safety, renderer allocations.
2. Stress scenario: add game enders, walls, traps, heroes.
3. CR-11 owner-name cache.
4. RoN rewrite plan + rewrite of one subsystem with GameTests.

## CI
- none yet
