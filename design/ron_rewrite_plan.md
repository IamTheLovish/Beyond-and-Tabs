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
| 16 | **Match lifecycle** (`PlayerServerEvents` 1.6k: start, defeat, victory, sync, spectators; `matchstart`, `maps`) | 6.6k | RoN + ours | **rewrite - DONE (rewrite 5, branch `match-lifecycle`)** | defeat/victory/spectate logic spread over one 1.6k-line class with static lists under `synchronized`; map phase not persisted (CR-13); a `MatchState` machine with saved phases | M |
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
   saved with the world (fixes CR-13), defeat/victory/spectate moved out of `PlayerServerEvents`. Done: "Rewrite 5"
   below.

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

## Rewrite 5 (done, branch `match-lifecycle`): the match lifecycle as one saved state machine

**Before.** RoN had no notion of a match beyond "rtsPlayers is not empty". `PlayerServerEvents` (1621 lines) kept the
sides in two `Collections.synchronizedList`s with a `synchronized` block around every walk, decided defeat and victory
inline in one 130-line method (inside the list's `removeIf`), and ran the match clock. Everything we added around it
lived in memory only: maps.MapMatch's `preparing` flag and a callback chain closed over the lineup (MapBuilder ->
StartPhase -> seat), MultiplayerLobby's `liveMatch`, the map in play. What that cost:
- **CR-13**: a world saved while a pre-made map rose or during start positions reloaded with a half-raised map, no
  match, and late starts allowed again.
- After *any* reload the lobby match forgot who may rejoin and who watches; the match clock restarted at 0; who had
  finished was forgotten (a winner could no longer spectate, the results lost the beaten sides' rows).
- **Alliances were not saved at all**: a reloaded team game made teammates enemies (their units fight each other, the
  resign vote and the commander rule see no team, the victory check sees two sides where there is one).
- A beaten base half taken down at save time (DefeatCleanupServerEvents) stayed ownerless and shootable for good.
- A beacon victory beats the other sides from inside a walk over the synchronized list: the second defeat threw
  ConcurrentModificationException. `synchronized` never protected against that (same thread), and since rewrite 1
  there is no other thread to protect against.
- Two callers passed a bare translation key as the defeat reason: "Bot1 has server.reignofnether.defeat_commander and
  is defeated!".

**After.** A `match` package (3.1k lines, most of it comments and tests' seams), `PlayerServerEvents` down to 1383:
- `MatchPhase`: LOBBY -> MAP_RISING -> START_POSITIONS -> PLAYING -> ENDED and the one transition table
  (`canMove`): forward only while preparing, LOBBY from anywhere, a new match over an ENDED one.
- `MatchState`: the only owner of a phase change (`prepare`, `mapRaised`, `startsPlaced`, `seatNow`, `sideJoined`,
  `onDecided`, `reset`); illegal ones are refused and logged, stray signals (a map rising with nothing rising, another
  match's result) ignored. It holds what each phase needs: the frozen `MatchSetup` and the live `StartPhase` while
  preparing, the seated `MultiplayerMatch` (victory scope, who may rejoin), the winners, the clock and the map in play.
  Its effects (stamping, the start phase, seating, chat) go through `MatchEffects`, implemented by
  `maps.MapMatch.EFFECTS` - the steps MapMatch always took, minus the closures - so the game tests drive real
  instances through every transition with a recording implementation and never touch the live match.
- `MatchSetup`: the lineup and settings frozen at Start, including what the world or chance decided then (colours
  made unique, the pre-made map's base height, the random ring's ground sites, one seed for the faction rolls), so a
  resumed setup seats exactly the match that was set up.
- `MatchRoster`: the side lists, now copy-on-write (game thread only; read every tick, written a handful of times per
  match, a walk is a snapshot so a side may be beaten in the middle of one). `PlayerServerEvents.rtsPlayers` /
  `postGameRtsPlayers` are the same objects; the `synchronized` blocks other packages still put around them are
  harmless (Proposals).
- `MatchOutcome` (defeat, the victory check, the beacon victory, the commander rule, the results table) and
  `Spectators` (leaving play to watch). `PlayerServerEvents` keeps every public method and field as a one-line
  delegate, so no caller elsewhere changed.
- `MatchLifecycleSaveData` (`bt-match-lifecycle`): the state, the alliances between the sides and the finished sides
  (RTSPlayerSaveData's own format), snapshot on every save, a HIGHEST-priority snapshot at ServerStoppingEvent (the
  statics clear themselves before the final save), read at ServerStartedEvent (LOW: after the sides are back) - never
  lazily. A world saved before this branch reloads as a classic match when it has sides in play.
- `MatchServerEvents`: the clock (and the results graphs' sampling) moved here, saved; a player logging in to a match
  being prepared gets the RTS view and, in start position mode, the camera on their own pick; a side of a decided
  match logging in gets its results again (`MatchState.owesResults`, timed at the decision: `endClock`).

**CR-13: resume, not cancel.** BAR has nothing to copy here - Spring saves only singleplayer games and only after the
start, and its pre-game lives outside the world - so the choice was made for the player. Cancel would leave what can't
be taken back (a half-stamped 500-block map) and throw away a lobby the host just set up. Resume costs nothing: the
build is deterministic and writes only blocks that differ, so it simply runs again from the saved setup (what is
already stamped costs reads); the start phase comes back with every pick and the clock. The rules:
- MAP_RISING saves how far the build got (`MapBuilder.stage()`, a new accessor - MapBuilder itself is untouched).
  Below 3 the build re-runs; at 3 the patches and sites are already stamped, so the match goes straight on (stamping
  twice would double them).
- START_POSITIONS comes back **held**: its clock waits until one of its humans is online again (at server start
  nobody is, and nobody may be auto-placed while still loading in), then runs on with at least 20 s
  (`StartPhase.RESUME_FLOOR_SECONDS`); "everyone placed" ends it early only once every human is back. AoE2 restores
  a saved multiplayer game the same way: it waits for the players. A human who never comes back is left out at the
  seat, as anyone who left during the countdown always was.
- **Cancel** - to LOBBY, nothing seated, a notice for everyone who logs in - only when a setup can't resume as saved:
  unreadable, a map this build doesn't know, start positions saved without their picks or of another lineup.
- Same family, fixed on the way: the defeat cleanup is finished at server stop before the final save
  (`DefeatCleanupServerEvents.finishAll`, CR-13's "finish the phase instantly on server stop").

**References.** BAR / Spring: the pre-game start boxes and Ready (our StartPhase); a team dies by its game-end rule
and `GameOver` fires when one ally team is left (our victory check, already scoped per match); spectators have full
view and switch to one player's view from the player list. AoE2: the held resume above. SC2: no mid-game saves at all.

**What players see differently** (rule 1). Victory, defeat, resign and spectate behave as before (pinned). New: a
reload keeps the phase, the map being raised or the starts being placed, the clock, the map's fog tint, the
alliances, the finished sides and the lobby's late-join rule; a defeat reason reads as text; a skirmish or lobby
Start is refused with a message while another match is being set up or fought (it used to be mixed into it), while
a match with nobody left in play is simply replaced; a winning team is listed once in the results; a side of a decided
match gets the results popup back on logging in after a reload or a dropped connection - and with it Spectate, where a
winner's Match Menu used to offer Resign again (the results are client memory).

**Open question for lovish.** BAR's commander-ends mode kills an ally team when its *last* commander falls. Here (your
rule, pinned by `commander_death_in_a_team_game_fights_on`) a team player whose commander dies always fights on, so a
team whose every commander is dead still plays until it has nothing left. Keep, or end the team with its last
commander?

**Tests** (`gametest/MatchLifecycleGameTests`, own holder and own batch `match_lifecycle`; the bodies are in
`MatchLifecycleCases` - the holder's class doc says why). Pinned first, against the pre-rewrite API, green on the old
code (run 38248700318): `commander_death_ends_a_1v1_and_the_base_becomes_wrecks`,
`commander_death_in_a_team_game_fights_on`, `victory_goes_to_the_last_team_standing`,
`resign_alone_surrenders_and_a_passed_team_vote_beats_every_human`,
`a_winner_spectates_and_keeps_their_base_but_a_fighter_cannot`. New: `match_state_walks_every_phase_and_each_survives_a_reload`
(every phase through real NBT bytes, what each resume does), `resumed_start_phase_holds_for_its_humans_and_keeps_a_floor`,
`a_setup_that_cannot_resume_is_cancelled_cleanly`, `match_phases_move_only_along_the_table`,
`alliances_between_the_sides_survive_a_reload`, `the_roster_survives_a_defeat_inside_a_walk_over_it`,
`a_beaten_base_is_cleared_at_once_before_the_world_saves`, `defeat_reason_keys_read_as_sentence_fragments`.

**Spectator polish (stretch, design/playtest_oct12.md "BAR's spectator").** The Match Menu says *Leave Match* to a
spectator (BAR's spectator menu says Quit; same action). The observer table's population comes from the server
(the player panel's row carries units-plus-queue population and the worker / army split, behind its existing ally /
observer gate) instead of the client's loaded units - far bots read 0 before (GameTest
`spectators_get_every_sides_population_and_enemies_do_not`). F8 makes a spectator follow one side
(`hud/playerdisplay/SpectatorView`): its economy on the top bar while nothing is selected and the camera on its
capitol; Shift+F8 back, past the ends the full view. Following a side's *line of sight* is left as a proposal: fog is
computed per player on the server and a spectator gets every chunk, so it needs the server to send that side's sight.

**Not verified headless.** The client side of a resume (RTS view and camera on login, the held countdown on the
phase banner, the results popup reopening), the spectator menu label, the observer table drawing and F8: GameTests run
a dedicated server. A real
save-and-quit / reload: GameTests can't restart the server, so the round trips go through the SavedData shell and
real NBT bytes instead.
