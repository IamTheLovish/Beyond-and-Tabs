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
| 5 | **Networking** (`registrars/PacketHandler`, ~90 packet classes) | 0.6k + | RoN + ours | **improve - audiences DONE (rewrite 3)** | 137 `PacketDistributor.ALL` sends (worker resource loads, hero mana / XP / ranks and ability state reach enemies too) - now one `network/Audience` policy each; still open: handlers mix client and server code; per-packet linear entity scans on the client (R2-04) | M |
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
   state). GameTest per policy on recipient sets (pure functions, like `MapDrawRules.recipients`). **Done** (branch
   `net-audience`, see "Rewrite 3" below).
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

## Rewrite 3 (done, branch `net-audience`): the networking audience policy

**Before.** Reign of Nether sent almost every clientbound packet with `PacketDistributor.ALL` - 138 sends in 43
files. With remote players in the match (lobby + e4mc) everything a client receives is something a modified client can
show, so under fog of war these were fairness bugs: every worker's carried load (`sendSyncResourcesPacket`, and the
unit batch sync's resources / gather target / anchor / inventory fields, which were per player but never fog-gated),
hero mana, XP, ranks, skill points and charges (R2-09), ability cooldowns, research completions (a tech switch),
every side's stockpile, every side's idle-worker list, unit paths (where an army is going), status effects and kill
tallies of fogged units, wreck metal (reclaim in the fog), positional sounds, BarFx battle effects within 200 blocks
(shots, deaths, nano beams - the R2-02 batch was range-gated only), attack warnings (where a fight is), diplomacy
requests, refused fog-queued build sites, the navmesh debug chunks (where units pathed). And waste: lock / unlock went
out once per player to everyone (N^2), every custom building's NBT to everyone at every login, and the hero and
ability sync requests a client makes for each unit it loads were answered to everyone - and to anyone asking about any
id.

**After.** `network/Audience` (new): seven policies, narrowest first - OWNER (the player it is about), COMMANDERS
(whoever may order the owner's units, `PacketAuth.mayActFor`), ALLIES (the owner, allies, and the Hero-role players
serving either - the same side `FogOfWarServerEvents.teamOwnersFor` shares vision with), TEAM (ALLIES + spectators:
private state), SEEN (TEAM + whoever's fog shows the spot, `isBlockVisibleFor`), SPECTATORS, ALL. Spectators see
everything (BAR's spectator rules; a defeated player spectates). The rules are pure functions over a `Facts`
interface; the send sites use `PacketTarget`s built from them with live facts (one custom `PacketDistributor`: the
packet is encoded once and the same bytes go to each accepted player). Every former `ALL` send names its policy and
the reason on its line; none is left outside `Audience`.

| Policy | What (the reason is on each send line) |
|---|---|
| OWNER | resource / population warnings, drop-off floating text, research and cheats, camera / teleport / RTS lock, a player's sound, the attacked player's ranged-reveal, alliance requests, a custom building's join sync, non-commander attack warnings, idle workers (per player) |
| COMMANDERS | unit paths (drawn only for units you may order), refused fog-queued build sites |
| ALLIES | commander-hit warnings, who possesses a unit |
| TEAM | stockpiles, market rates, cooldowns and charges, hero skill points and charges, a Necromancer's souls, autocast toggles, a unit's shop panel |
| SEEN | worker loads, hero XP / ranks / mana, status effects, kills, veteran look, anchors, worker jobs, inventories, conversions, visible ability effects (Marauder swing, aura, blizzard), sounds at a spot, a shop's stock, a finished building; BarFx events are now fog-gated per event too (either end of a shot or beam, any corner of a collapse) |
| unfogged | navmesh debug chunks (everyone while fog is off, spectators while it is on) |
| ALL | game rules, game mode, the player list, defeat / victory / reset, the match clock, the beacon objective and its countdown, map state and start picks (public by design, maps.md 5), capture sites (maps.md 3), the metal map, neutral camps, fog on/off, alliances, match-wide cues (dawn, dusk, game ender moments), the end-of-match stats, unit removals and owner changes (clients keep units in a sticky list), building-destroyed (fog ghosts), lobby start positions, survival waves, tutorial, scenario roles, datapack buttons, debug perf numbers |

`network/AudienceResync` (new) is the other half: a narrowed audience must never leave a client with stale data.
`UnitSyncBatcher` already decided once a second, per player and unit, whether to send stats (fog); it now decides
whether that player may know the unit at all (their side, a spectator, or in view), sends nothing else about it
otherwise, and when that turns true calls `onUnitKnown`: status effects and kills always (no client asks for them
when an entity loads - before, a unit loaded after an effect landed never showed it, on any client), hero XP / ranks
/ mana unless the client only just loaded the entity (then it asks itself: the hero and ability sync requests now
answer the asker alone, if they may know). The snapshot delta re-sends whatever else changed while it was hidden.
When what a player may know changes (`viewChanged`: a defeat made them a spectator, an alliance, a Hero role) they get
the stockpiles they may now know and every unit's private state (cooldowns, skill points); their first record also
re-sends stockpiles, because a match start syncs them before it forms the team alliances.

**Referenced.** BAR / Spring: unit information is LOS-gated, allies share LOS, enemy resources, factory queues and
orders are never shown, spectators see everything. SC2: nothing in the fog makes a sound or an effect; an enemy unit's
energy is readable when you can see it. Warcraft III: a visible enemy hero's level, mana and learnt abilities are
readable, its unspent skill points are not - the split used for heroes here (SEEN vs TEAM). AoE2: a visible enemy
villager's carried load can be read; through the fog nothing - worker loads are SEEN, not TEAM. Company of Heroes:
capture points show on every minimap - kept public, as design/maps.md already decided.

**Tests** (`gametest/AudienceGameTests`): `audience_recipients_per_policy` (owner, ally, enemy, spectator, sandbox
editor, a Hero-role player of each side, neutral things, shared control on/off), `audience_live_facts_follow_alliances_and_hero_roles`,
`audience_resync_sends_what_a_newly_visible_unit_needs` (through `AudienceResync.sink`, no connection),
`capture_points_leave_unrelated_entities_untagged` (R2-10), `audience_cuts_8v8_deliveries` (below).

**Measured** (`audience_cuts_8v8_deliveries`, its own batch). A headless GameTest server has no clients, so packets
cannot be counted on the wire; instead the test lays out an 8v8 (16 armies of 25 of the stress mix, each side on its
own half, five fighters per army at the front, two Hero-role players and two spectators online: 20 clients), sends one
round of the per-unit packet families through the real send sites, records each send's policy
(`Audience.accountant`) and counts recipients with team vision (a side sees a spot one of its units has in sight
range). Before this rewrite every one of them reached all 20 clients. Result: MEASURED_RESULT_PLACEHOLDER

**Proposals** (outside this rewrite's lane).
- *Fog: tracked entities are sticky.* `TrackedEntityMixin` cancels `updatePlayer` while an entity is fogged for a
  player, so it never adds a tracker - but never removes one either: an enemy unit that walks into the fog after being
  seen keeps streaming its movement to that client until it leaves view distance (an honest client hides it; a
  modified one shows it). And vanilla only re-evaluates trackers when a section changes, so a unit can stand
  untracked inside a viewer's circle. Make the gate remove the pairing when fogged (the client keeps units in a sticky
  list, so nothing is lost) and re-evaluate fog-gated trackers on the fog tick; then `Audience.tracking` would be
  fog-exact for free.
- *Building ghosts.* "Building destroyed" still goes to everyone because a client holding a fog ghost must learn it
  is gone, and nobody tracks who holds one. A per-building "known by" set (filled where `BuildingClientboundPacket`
  sends) would let it go to SEEN plus ghost holders.
- *Hero-role HUD.* The client treats a Hero-role player as "not an RTS player", i.e. an observer: it shows enemy
  ability buttons and selected enemies' stockpiles, which now (correctly) arrive empty for them. The HUD should treat
  a Hero like a team member (CR-06 family).
- *Match start order.* `MultiplayerLobby.startMatch` assigns stockpiles and market rates before forming alliances;
  forming alliances first would make the first sync right without `AudienceResync`'s first-record re-send (market
  rates still reach allies only after a change).
- *Kill feed.* `KillFeedServerEvents.canSee` is the SEEN rule without the Hero-role side (a Hero hears of its
  team's kills only where its fog shows them); it could call `Audience.mayKnowAt`.
- *Join sync.* `PlayerServerEvents.syncUnits` re-sends every hero's state to its whole SEEN audience at each login;
  the joiner alone would do (left alone here: match-lifecycle is restructuring that file).
