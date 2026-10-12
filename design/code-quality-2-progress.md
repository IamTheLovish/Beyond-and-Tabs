# code-quality-2 - progress

Branch `code-quality-2`, based on origin/main 102ba10. Findings: `design/code_review.md` (round 2 section at the end).
Rewrite plan: `design/ron_rewrite_plan.md`.

## Done
- Integration audit of 30a1b6f..102ba10: findings R2-01..R2-10 (code_review.md).
- Fixes: R2-01 game ender charge/announced + Ward Spire stockpile saved (generic BuildingPlacement.writeSaveState /
  readSaveState hook); R2-02 tower tracers through BarFx; R2-03 no ForgeData tag on every entity join; R2-04 hero packet
  uses the level's id index; R2-05 hidden traps hidden from enemy Hero-role players; R2-06 layout-dependent hero test;
  R2-08 trap plate check queries the plate only.
- Rewrite 1 (R2-07): taskscheduler/TickScheduler + TaskSchedulerClientEvents; all 19 CompletableFuture.delayedExecutor
  sites moved onto the server/client schedulers.
- CR-11: mixin/SynchedEntityDataMixin removes the read lock from SynchedEntityData.getItem (measured: roughly half of every synched read).
- Stress scenario: 8 heroes, 32 gates, 48 traps, 24 towers, 8 game enders, 8 wards; new budgeted system
  "defences+game enders"; CR-11 REF timings.
- GameTests (RegressionGameTests): game_ender_charge_and_ward_stockpile_survive_a_reload,
  hidden_trap_is_hidden_from_an_enemy_hero_role_player, tick_scheduler_runs_each_task_once_in_order_on_time,
  server_tasks_run_on_the_server_thread_in_game_ticks.
- design/ron_rewrite_plan.md (inventory of 20 subsystems, ratings, order, rewrite 1 write-up).

## Next (for whoever picks this up)
- Rewrite plan #2 (command pipeline) and #3 (networking audience policy).
- Proposals: tighten the stress budget (~5 ms), amortised reporting, RTS-player stress owners; CapturePointServerEvents
  getPersistentData on join (R2-10); Hero-role team fog (CR-06, hero branch).

## CI
- a9fa563 and f182c51 green (stress 3.16 / 1.53 ms; runner speed varies 2x).
- c09ccfc green (stress 2.98 ms). 5cb18c9 / 6371d02 / ba01050 / 0cdff1d red only on each_faction_hero_trains_at_its_lab
  (layout-dependent, R2-06; fixed in a9fa563).
