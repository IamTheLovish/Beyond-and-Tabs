# command-queue progress (Reign of Nether rewrite #2, first slice)

Branch `command-queue`, base origin/main d73acee. Brief: per-unit BAR command queue replacing RoN's two global action
lists (design/ron_rewrite_plan.md, "Rewrite 2" - the full design note, what was referenced in Spring/BAR, tests and
follow-on slices are there).

## Done
- Read the plan, code_review.md, the old pipeline (UnitServerEvents slow/fast lists, UnitActionItem switch,
  UnitActionServerboundPacket, UnitQueueSync) and every caller (bot/, hero/PossessionServer, onboarding hook,
  ProductionPlacement rally, AreaCommands, WreckServerEvents chain, BuildingServerEvents, survival WaveEnemy,
  commands/ debug commands, client UnitClientEvents / MinimapClientEvents).
- Baseline: main d73acee CI `[Stress]` notice: queue lines 0.187 ms, total 2.169 ms (REF builder lookup 2.821 ms).
- Step 1 (3778f2e): behaviour pins in `gametest/CommandQueueGameTests.java`, written to pass on the OLD pipeline.
- Step 2: `unit/command` (QueueMode, CommandKind, UnitCommand, CommandQueue, CommandQueues); UnitServerEvents lists
  removed, old entry points kept as adapters; executor at the end of the overworld tick; UnitActionItem: owner rule as
  `mayCommand`, equals fixed, hunting drop-off on the worker's own queue (server only), state commands keep
  patrol/guard; UnitActionServerboundPacket carries a QueueMode byte (old shift boolean values kept); UnitQueueSync
  reads per-unit queues, skips dead targets, builds only for online owners (CR-18); AreaCommands on CommandQueues.issue;
  Unit.isIdle cheapest-first and allocation-free; GuardServerEvents.isGuarding; client: Ctrl = insert front.
- New-behaviour tests + benchmark `stress_command_queue_executor_stays_in_budget` (batch `stress_command_queue`).
- Local check (not shipped): the unit/command package compiled against hand-written stubs and run in a simulation
  harness with fake units (scratchpad): ordering, cap, dead-target skip, insert-front resume rules, Ctrl+Stop,
  state commands, group start, re-entrancy, vanilla-pathfinding budget, 0 bytes allocated per executor pass.

- CI slowdown found (not the queues): runs 38252524132 / 38259715545 ran the default batch at 0.3-3 tps. A sampling
  profiler of the server thread (commit 5f6add7, `CommandQueueGameTests.ServerThreadProfile`; removed again, cherry-
  pick it to profile any CI slowdown) put 70-90% of the time in `SkirmishGameTests.stampOnPad` ->
  `ServerLevel.sendBlockUpdated` -> `Mob.getNavigation`: the production pad tests' ~36k shape-changing setBlocks per
  pad, each walking every navigating mob, while the duel matrix (DuelGameTests, default batch) had ~2,000 units out.
  Which 100-test chunk a test lands in follows the HashSet order of every holder's methods, so adding tests anywhere
  can put the two together: match-lifecycle's runs 38256543886 / 38259602034 show the same (duel wall 866 s / 642 s,
  32 min watchdog). Fix: the duel matrix gets its own batch (`duel_matrix`), like the counter matrix.
  The queue counters in the same run (20 min of default batch): 1,191 orders given in 524 ms (dispatch included),
  13 executor passes in 8 ms that started 7 queued orders.
- Benchmark fixes: the 80 workers of the 400 can't go idle without ticking (GatherResourcesGoal.isIdle waits for one
  idle tick past its timeout), so only non-workers are expected idle in the "army finishing" phase; the queue lines
  are compared with a copy of the whole old build (the old reference timed only its regrouping pass); the
  measurement waits until its forced chunks are entity-ticking (run 38268701338: 35 units in still-loading chunks,
  unreachable by id).
- CI green: run 38271512327 (f7c5ad6), all tests. "Measured" in the plan filled from it.

## Next
- Nothing left in this slice; follow-on slices are listed in the plan (Rewrite 2).

## Known failures
- none on f7c5ad6. Earlier: run 38259715545 (5f6add7) and 38268701338 (e305402) failed only the benchmark, for the
  test reasons above.

## Proposals (outside this slice / lane)
- A worker ordered to hunt while its inventory is full and with no reachable drop-off cycles drop-off / attack every
  couple of ticks (pre-existing; the old slow list did the same). Give up after one failed drop-off.
- PATROL and GUARD are not in UnitActionItem's NON_ABILITY_ACTIONS, so they don't reset the unit's behaviours (a unit
  chasing a target keeps chasing when told to patrol). Pre-existing; belongs with "patrol/guard as commands".
- `/rts unit action` (CommandsServerEvents) and rtsapi UnitCommands bypass the queues (call UnitActionItem directly).
- MinimapClientEvents' formation drag still sends the shift boolean (no Ctrl): fine today, revisit with queue editing.
- `Unit.isIdle`'s "casting a spell" check never fires: `goalSelector.getAvailableGoals()` holds `WrappedGoal`s, so
  `goal instanceof GenericUntargetedSpellGoal` is always false (it should test `wrapped.getGoal()`). Kept as is here
  (rewrite 2 keeps isIdle's answers identical; the benchmark checks that); fixing it changes when casting units count
  as idle (queue waits for the cast, no idle aggro mid-cast) and wants its own tests.
