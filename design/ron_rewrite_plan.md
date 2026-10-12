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
| 2 | **Command pipeline** (`UnitServerEvents` fast/slow action queues, `UnitActionItem`, `UnitActionServerboundPacket`, `UnitAction` switch in `Unit.java`) | 1.3k + | RoN, patched | **rewrite - slice 1 DONE (`command-queue`, below)** | BAR's command queue (move / fight / patrol / guard / repair / reclaim, shift-queue, insert-front) is the core of its feel; RoN keeps orders in two global `synchronizedList`s scanned linearly per unit (shift-queue depth, de-dup), executes them a tick late through a giant switch, and has no queue per unit to show or edit | L (slices) |
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

## Rewrite 2 (first slice, branch `command-queue`): a command queue per unit

**Before.** Reign of Nether kept every order in two global `Collections.synchronizedList`s in `UnitServerEvents`.
Plain orders went to the "fast" list, run at the end of the overworld tick, so an order from a packet took effect a
tick after it was handled. Shift orders went to the "slow" list as one single-unit item per unit; every tick the list
was scanned from the top (`level.getEntity` + `Unit.isIdle` per item) for the first item whose unit was idle, and that
ONE item was started - one queued order per tick for the whole server. What that cost:
- at 8v8, units finishing their waypoints together waited for their place in the list (400 units: 20 s for the last);
- with nobody idle every queued item was checked every tick (400 units x 3 waypoints = 1,200 `isIdle` per tick);
- the slow list ran before the fast list, so an idle unit given a plain order with shift orders behind it started
  shift order #1, the plain order then overrode it, and #1 was lost (`AreaCommands` called the dispatch directly to
  dodge this);
- queue depth (`MAX_QUEUED_ACTIONS`), `hasQueuedActions`, de-dup and the queue lines (`UnitQueueSync` regrouped the
  whole list twice a second) were scans of everybody's orders, and the lists were never cleared on server stop (an
  order left in one singleplayer world ran in the next);
- the hunting worker's drop-off: its "already queued?" check compared the queued items' units with the animal's id
  (never true), it ran on the client too (in singleplayer adding to the server's list from the client thread), and the
  "inventory full" variant queued its two items for the selection's first unit, not the full worker;
- a building ability clicked with Shift held did nothing (no unit item to carry it) or fired once per selected unit,
  and an autocast toggle emptied the unit's shift-queue and cancelled its patrol or guard.

The "giant switch" is `UnitActionItem.action` (Unit.java holds the reset and idle logic); it stays, for this slice, as
the effect of each order.

**After.** `unit/command`:
- `QueueMode` - REPLACE (plain), APPEND (Shift), INSERT_FRONT (Ctrl); one byte in `UnitActionServerboundPacket`, in
  the slot and with the values of the old shift boolean.
- `CommandKind` - Spring's command families over `UnitAction`: STATE commands (fire state, autocast and passive
  toggles: applied at once, never queued, never clear a queue or cancel a patrol/guard - BAR's CMD_FIRE_STATE /
  CMD_REPEAT never enter the queue either), which orders need a live target entity, which can be resumed.
- `UnitCommand` - an immutable order (action, owner, target entity id, position), shared by every unit it was given to.
- `CommandQueue` - one unit's queue: the order it is carrying out (if the queue started it) and a ring buffer of waiting
  orders, read by index (no iterator for the queue lines), allocation-free once grown.
- `CommandQueues` - the queues by entity id, `issue` (the one way orders are given) and the executor.

Semantics, from Spring / Recoil's `CCommandAI` (what BAR runs on): a plain order clears the unit's queue and starts at
once (`GiveAllowedCommand` clears `commandQue` without SHIFT_KEY and runs `SlowUpdate` when the new command is the only
one); Shift appends, and starts at once on a unit with nothing to do; Ctrl is `CMD_INSERT` at position 0 - the new order
starts and the interrupted one goes back to the head of the queue, but only if the unit was still carrying it out (a
finished move, a dead target, or a fight the unit picked itself is not resumed; Ctrl+Stop and Ctrl+Hold act as plain
orders). BAR puts insert on Space, which is the shop key here; Ctrl+right-click was free (Ctrl+right-DRAG stays the area
command). When a unit finishes its order the next one starts (`FinishCommand`), for every unit in the same tick, but
never a second one for a unit in the tick an order started (Spring allows one FinishCommand-driven SlowUpdate per frame
"to avoid infinite loops"; here the goals also get a tick to act). An order whose target died or was removed is skipped
when it comes up and the next one starts in the same pass, and the queue lines draw no line to it. Units starting an
order together run it as one group order (formation spread, one cast of a one-click ability), so Shift on idle units
equals a plain click; a building ability on an order and mobs under the control-all-mobs cheat run once, at once.

"Finished" is `Unit.isIdle`, looked at once per tick at the end of the overworld tick, only for units with orders
waiting - Reign of Nether's goals don't report completion. Units merely carrying out an order cost nothing per tick.
With vanilla pathfinding (a start runs a synchronous A*) a burst of starts is spread like the formation dispatch: at
least 10 per tick, then a 4 ms budget, resuming where the last pass stopped; with the RTS pathfinder every due order
starts at once. Queues are dropped when their unit leaves the level and on server stop, and are not saved (the old
lists weren't; their entity ids would not survive a reload). Server thread only: an order from another thread is logged
and moved onto it.

`Unit.isIdle` answers cheapest-first (follow target, move target, attacker, ranged, worker, item goal, then the goal
scan for a casting spell) with no allocation (the distance to the move target without `BlockPos.getCenter()`'s Vec3):
same answers - the benchmark checks it against a copy of the old form on 400 units, busy and idle - but a busy unit
answers after a field read or two. `AttackerUnit.tick` calls it twice per unit every 4 ticks, so the whole game gains.

Kept as thin adapters (other branches compile unchanged): `UnitServerEvents.addActionItem` (both overloads; plus a
`QueueMode` overload), `getQueuedActions`, `hasQueuedActions`, `clearQueuedActions`, `MAX_QUEUED_ACTIONS` (= 32, "Queue
full"), `getUnitActionSlowQueue` (deprecated: a read-only snapshot - adding to it throws instead of dropping the order),
`UnitActionServerboundPacket`'s shift-boolean constructor and `accept` overload, `UnitQueueSync.buildPayloads()`.
`PacketAuth.mayActFor` / `controllableIds` and the possession divert stay in `accept`; the owner rule the dispatch
applies is now one method, `UnitActionItem.mayCommand`, also applied before an order is queued. `AreaCommands` uses
`CommandQueues.issue` (its workaround is gone; an area drain now honours Shift). `UnitQueueSync` reads each unit's own
queue and builds payloads only for owners with a player online (code_review CR-18). Also fixed on the way:
`UnitActionItem.equals` (its `a && b || c && d` chain compared only the building position and ids, so the client
skipped predicting a move to a new spot with the same selection), the drop-off bugs above, `nonAbilityActions` built
per order. No call site in bot/, hero/ or onboarding/ changed.

Behaviour players see: orders take effect one tick sooner; queued waypoints flow without waiting for a server-wide
turn; Shift on idle units spreads them like a plain click; Ctrl inserts at the front; autocast toggles keep the queue
and patrol/guard; Shift + a building ability works. Bots' orders also take effect at once, so a bot no longer sees a
unit it just ordered as idle later in the same think step (it used to be able to order it twice, the last one winning).

**Measured.** Before (main d73acee, `[Stress]`): queue lines 0.187 ms, total 2.169 ms (REF builder lookup 2.821 ms, the
runner-speed reference). After: filled in from this branch's CI run below.

**Tests.** `gametest/CommandQueueGameTests`. Pinned first, on the old pipeline (run 38249579532): the cap of 32 in
order, shift order + a plain order replaces, a removed target's order skipped, a non-owner's packet refused (shift or
not; an own-name order listing another's unit has it filtered out), production rally points in order, a fire-state
toggle keeps the queue. New behaviour: orders take effect the tick the packet is handled (Shift on an idle unit, plain,
Ctrl with resume), Ctrl interrupts and the interrupted order resumes (nothing finished comes back), a dying target's
order skipped in the same pass, Shift on idle units starts them together in formation, state commands keep the queue
and a patrol, queue lines read from the unit's queue (none to a removed target), `UnitActionItem.equals` and the
packet's queue-mode round trip. Benchmark `stress_command_queue_executor_stays_in_budget` (own batch): 400 units in the
8v8 mix, busy three ways, three orders waiting each - the executor pass is timed and its allocation counted (must be 0),
against the old slow-list scan of the same orders with the old `isIdle`; then the whole army finishes at once and every
unit must start its next order within a few passes.

**Follow-on slices.**
1. Fight / patrol / guard as first-class queued commands: PATROL and GUARD still set standing orders in
   `PatrolServerEvents` / `GuardServerEvents` outside the queue (Spring cycles CMD_PATROL through the queue and keeps
   CMD_GUARD at its head); orders shift-queued behind a patrol still start after its first leg.
2. Each command kind's effect out of `UnitActionItem.action` into per-kind executors on the server; the client's
   prediction becomes a small mirror.
3. Abilities as commands: targets validated when they come up (dead target: skip), cooldown / mana checked at start
   with a wait-or-skip rule, BAR's manual-fire D-gun as a queued command.
4. Factory queues: `ProductionPlacement` as a factory command queue (repeat, Ctrl insert-front, BAR's "new unit
   commands": the rally points as a real command list handed to each new unit, guard-assist).
5. One builder queue: `BuildRepairGoal.queuedBuildings` (shift-placed sites) is a second queue; build orders become
   `UnitCommand`s.
6. Queue editing and persistence: Shift-click on a queued waypoint removes it (Spring's CancelCommands), insert at the
   nearest position (Spring's CMD_INSERT with ALT / BAR's Space+Shift), queues saved with the world (target UUIDs).
7. Units with orders waiting don't auto-engage between waypoints (`AttackerUnit` idle aggro can still pick a fight in
   the tick a move ends; BAR moves don't stop to fight).
8. `/rts unit action` and the rtsapi commands call `UnitActionItem` directly and bypass the queues: route them through
   `CommandQueues.issue`.
