# Reign of Nether rewrite plan

Decision (lovish, Oct 11): Beyond and Tabs stays a credited GPL-3 fork of Reign of Nether (RoN); its systems are
rewritten or enhanced **one at a time**, each behind GameTests, toward two goals: it must play like Beyond All Reason
(BAR), and its code must be better than the code it replaces. This file is the inventory and the order. Branch
`code-quality-2` (base 102ba10) wrote it and did rewrite 1.

Ratings: **keep** (sound; leave it), **improve** (sound core, fix specific defects in place), **rewrite** (the
structure itself is the problem). Effort: S (a day), M (a few days), L (a week or more, done in slices).
"Already ours" marks subsystems this project has rewritten since the fork.

## Rules for every rewrite

1. **Behaviour first, then structure.** Pin current behaviour with GameTests before changing the code; a rewrite
   that changes what players see says so in its design doc.
2. **One subsystem per branch**, small enough to merge while ~10 feature branches run in parallel. Prefer a new
   class next to the old one and a switch-over, over editing 100 files at once.
3. **Game thread only** for game state (`taskscheduler.TickScheduler` for anything later). No wall-clock timers.
4. **Server authoritative, every serverbound packet through `PacketAuth`** (owner check + rate limit), every
   clientbound packet to the narrowest audience (owner / team / tracking players), never `PacketDistributor.ALL`
   for information an enemy should not have.
5. **8v8 budget:** no per-tick work proportional to (units x units) or (buildings x buildings); spatial queries through
   `UnitGrid`; per-frame client code allocates nothing; the stress GameTest covers the new code before it merges.
6. **Saved state round-trips** (a GameTest writes NBT and reads it back) and DataTypes are registered before load.
7. Credit stays: RoN-derived files keep their origin; new files say what they replaced and why.

## Inventory

| # | Subsystem (main RoN classes) | Size | State | Rating | Why | Effort |
|---|---|---|---|---|---|---|
| 1 | **Delayed work** (`TaskSchedulerServerEvents`, 19x `CompletableFuture.delayedExecutor`) | 0.2k | RoN | **rewrite - DONE here** | game state mutated on a pool thread in wall time; scheduler re-ran tasks after an exception, threw CME on re-entry, leaked tasks across worlds, allocated per tick | S |
| 2 | **Command pipeline** (`UnitServerEvents` fast/slow action queues, `UnitActionItem`, `UnitActionServerboundPacket`, `UnitAction` switch in `Unit.java`) | 1.3k + | RoN, patched | **rewrite** | BAR's command queue (move / fight / patrol / guard / repair / reclaim, shift-queue, insert-front) is the core of its feel; RoN keeps orders in two global `synchronizedList`s scanned linearly per unit (shift-queue depth, de-dup), executes them a tick late through a giant switch, and has no queue per unit to show or edit | L (slices) |
| 3 | **Unit behaviour core** (`Unit.java` / `AttackerUnit.java` static tick methods, ~30 goals: `MoveToTargetBlockGoal`, `UnitBowAttackGoal`, `UnitCrossbowAttackGoal`, `UnitRangedAttackGoal`, `RangedAttack*Goal`, `MeleeAttack*Goal`...) | 4.7k goals + 1.6k | RoN, heavily patched | **rewrite** | one weapon concept per vanilla mob type instead of BAR weapon definitions (range, reload, target categories, priority, ground/air, leading); target acquisition re-implemented per goal; vanilla `Goal` lifecycle fights RTS orders (resetBehaviours everywhere) | L (slices) |
| 4 | **Unit classes** (~280 files in `unit/units`) | 52k | RoN pattern, ours mostly | **improve** | every class re-declares owner accessor, save data, attribute boilerplate (why CR-11 could not be a field). A `UnitCore` composition (like `hero/HeroCore`) lets new units be bodies + data | L (incremental) |
| 5 | **Networking** (`registrars/PacketHandler`, ~90 packet classes) | 0.6k + | RoN + ours | **improve** | 137 `PacketDistributor.ALL` sends (worker resource loads, hero mana / XP / ranks and ability state reach enemies too); handlers mix client and server code; per-packet linear entity scans on the client (R2-04) | M |
| 6 | **Building core** (`BuildingPlacement` 1.8k lines, `BuildingServerEvents`, `BuildingClientEvents`) | 4k | RoN, patched | **improve -> split** | one class does block bookkeeping, construction, health, abilities, levels, decommission, sync; per-placement save state now has a hook (R2-01). Split into components behind the same public API | M-L |
| 7 | **Production** (`ProductionPlacement`, `ProductionItem`) | 0.9k | RoN, patched (BAR flow, levels, repeat) | **improve** | population check walks every queue (CR-08 proposal: per-owner cache); rally orders went through delayed executors (fixed by #1); BAR's factory queue (repeat, insert, guard-assist) is half there | M |
| 8 | **Economy / resources** (`EconomyServerEvents`, `ResourcesServerEvents`, `GatherResourcesGoal`, wrecks, metal patches) | 3.5k | largely ours (BAR flow economy) | **keep / improve** | flow economy, metal extractors, wreck reclaim are ours; RoN's block gathering survives in `GatherResourcesGoal` (600 lines) - improve when trees/reclaim are revisited | S-M |
| 9 | **Pathing** (`unit/pathfinding`: `RtsPathfinder`, worker pool, walkability snapshots) | 1.9k | already ours | **keep** | off-thread A* over snapshots, budgeted chunk classification; measured | - |
| 10 | **Fog of war** (`fogofwar`, chunk gating mixins) | 1.8k | already ours (on RoN's API) | **keep / improve** | server-side chunk gating and snapshots are ours; open: Hero-role team vision (CR-06), particle gate cost per send (R2-02) | S |
| 11 | **HUD** (`HudClientEvents` 2.4k, `hud/*`) | 13k | RoN + bar-hud rounds | **improve** | per-frame list building and string formatting in the 2.4k-line event class; split by panel, cache per selection change | M |
| 12 | **Minimap** (`MinimapClientEvents` 1.6k) | 2.6k | RoN + ours | **improve** | full texture rebuilds; unit dots walk every client unit per refresh; fine until 8v8 measurements say otherwise | M |
| 13 | **Selection / cursor / camera** (`CursorClientEvents`, `OrthoviewClientEvents`, `UnitClientEvents` 2k) | 4k | RoN + bar-camera | **improve** | works; `UnitClientEvents` mixes selection, control groups, rendering hooks and packets | M |
| 14 | **Research / tech** (`research/*`, 61 files) | 4.8k | RoN pattern | **keep** | item-per-class is verbose but data-like and stable; registration could become data later | - |
| 15 | **Abilities** (`ability/*`, 139 files; `Ability`, `Abilities`) | 16.8k | RoN framework, ours mostly | **improve** | framework is serviceable; cooldowns keyed by cloned instances (`updateAbilityButtons` re-clones), per-ability packets; autocast and BAR "command-type" abilities fit a command queue (#2) | M |
| 16 | **Match lifecycle** (`PlayerServerEvents` 1.6k: start, defeat, victory, sync, spectators; `matchstart`, `maps`) | 6.6k | RoN + ours | **rewrite** | defeat/victory/spectate logic spread over one 1.6k-line class with static lists under `synchronized`; map phase not persisted (CR-13); a `MatchState` machine with saved phases | M |
| 17 | **Save data** (`BuildingSaveData`, `persistence/*`, unit NBT in each class) | 1k | RoN + ours | **improve** | DataTypes register lazily (CR-03 workaround list), per-placement state hook added (R2-01); one eagerly-loaded `BuildingDataTypes` holder | S |
| 18 | **Event registration** (`ClientEventRegistrar` / `ServerEventRegistrar` hand-copied lists vs `@Mod.EventBusSubscriber`) | 0.4k | RoN | **improve** | two hand-maintained lists that must agree, plus annotations elsewhere; one mechanism | S |
| 19 | **Bots** (`bot/*`) | 4.4k | ours | **keep** | BAR-style priorities, measured in soaks | - |
| 20 | **Legacy modes** (survival waves, scenario, tutorial, sandbox) | 5k | RoN | **keep (frozen)** | not BAR; keep working, don't invest; candidates for removal if they block a rewrite | - |

## Order (top 5)

1. **Delayed work -> `TickScheduler`** (S) - done on this branch (below). Highest value per line: it removed
   concurrency bugs from production rally points, join syncs, beacon block changes and defeat chunk resends, and it
   is the precondition for the CR-11 lock removal.
2. **Command pipeline** (L, in slices): per-unit command queue (`CommandQueue` on the unit: deque of typed commands,
   shift = append, ctrl = insert front), server-thread executor, queue lines read from it (`UnitQueueSync` stops
   rebuilding from the global lists), then fight / patrol / guard as commands. GameTests: queue depth, shift order,
   replace vs append, a dead target's order skipped, packets owner-checked.
3. **Networking audience policy** (M): one `Audience` helper (owner / team / tracking / all) and a review of the 137
   `ALL` sends - first the ones that leak information under fog (worker loads, hero mana / XP / ranks, ability
   state). GameTest per policy on recipient sets (pure functions, like `MapDrawRules.recipients`).
4. **Weapons and targeting** (L, in slices): a `Weapon` definition (range, reload, damage, target categories, air/ground,
   priority) and one target-acquisition service on `UnitGrid` used by every attacker; the per-mob attack goals become
   thin animation adapters. Starts with ranged units (most duplicated goals). Duel and stress GameTests pin balance and cost.
5. **Match lifecycle state machine** (M): `MatchState` (lobby -> map rising -> start positions -> playing -> ended)
   saved with the world (fixes CR-13), defeat/victory/spectate moved out of `PlayerServerEvents`.

After those: building core split (#6), production queue (#7), HUD split (#11), `UnitCore` (#4), registration (#18).

## Rewrite 1 (done): delayed work on the game thread

**Before.** `TaskSchedulerServerEvents` (RoN) kept `ScheduledTask(ticksRemaining, task)` records in an `ArrayList`
and, every server tick, built a new list with a new record for every waiting task. A task that scheduled another
task threw `ConcurrentModificationException`; a task that threw left the list un-cleared, so every task already run
that tick ran again on the next one (a sapper charge would detonate every tick); nothing cleared it on server stop, so
a task scheduled in one singleplayer world ran in the next. Most RoN code did not even use it:
19 `CompletableFuture.delayedExecutor(ms)` callbacks ran game code on a ForkJoin pool thread, in wall-clock time,
while the server thread mutated the same state: production rally orders and the gather target of a new worker
(`ProductionPlacement`), the building and custom-building syncs on join (iterating the building list),
`BeaconPlacement.activate` (block changes off-thread), `FogOfWarServerEvents.resendAllTrackedChunks` after a defeat
or a switch to spectator (chunk packets off-thread), Call to Arms bells, the scenario role sync, and four client chat
messages that dereferenced `Minecraft.player` two seconds later on a pool thread.

**After.** `taskscheduler/TickScheduler`: a binary heap keyed by (due tick, sequence), an inbox
(`ConcurrentLinkedQueue`) so any thread may schedule, `tick()` runs due tasks on the ticking thread. Guarantees: game
thread, game ticks (pause and lag delay it with the game), exactly once, due order then FIFO, re-entrant (a task's
task runs on a later pass), exception isolated and logged, nothing allocated per tick, cleared on server stop /
logout. `TaskSchedulerServerEvents` (server tick END) and the new `TaskSchedulerClientEvents` (client tick END)
wrap one each; RoN's `schedule(delayTicks, task)` semantics are kept (0 = end of this tick), so existing callers
(sapper charges, mortar/rocket volleys) are unchanged. All 19 delayed executors now schedule on the right one
(ms / 50 = ticks); the beacon re-activation also skips a beacon destroyed in the meantime, and the client messages
skip a player who has left.

**Tests.** `RegressionGameTests.tick_scheduler_runs_each_task_once_in_order_on_time` (order, once, re-entry, a
throwing task, negative delay, clear) and `server_tasks_run_on_the_server_thread_in_game_ticks` (thread and delay,
including a task scheduled from another thread). Existing tests over the migrated callers (production, sapper
charges, volleys) keep passing.

**Follow-on.** With no game code left off-thread, CR-11 removed the read lock from `SynchedEntityData.getItem`
(`mixin/SynchedEntityDataMixin`).
