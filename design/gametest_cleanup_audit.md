# GameTest cleanup audit: what a failing test leaves behind

Oct 28 2026. Every GameTest under `bar/src/main/java/com/solegendary/reignofnether/gametest/` (158 files, about 60k
lines) was read for state that outlives a test that FAILS or TIMES OUT but would have been cleaned up on a pass. Shard 1
of CI runs a few hundred tests in one world, so one test's leftovers make other tests fail. The known case was #338:
the AC-5 extractor test failed, its radius-48 stone pad stayed at y 264, and the scar test above another arena read
264 - 210 = 54 as its top face.

Line numbers are on main `3671a98a`, before this change. **FIXED** means this PR moves the cleanup onto a path that
always runs. **OPEN** is listed for a follow-up.

## How a test ends (vanilla 1.20.1, checked in the bytecode of the MCP jar)

- `GameTestInfo.fail(t)` and `succeed()` only mark the test done. Then `tick()` tells every `GameTestListener`
  `testPassed` or `testFailed` in the same tick. This happens for a pass, for a throw from the test method or any
  callback (`helper.fail`, every `helper.assert*`, `ArenaReady.whenTicking`, `GameTestChunks.whenEntitiesReady`), and
  for a timeout (`GameTestTimeoutException`). After that none of the test's callbacks run again.
- In `succeedWhen` a throw means "not yet": the test only fails at its timeout. So cleanup placed after the assertion
  inside the lambda runs only on a pass.
- Vanilla removes **no** entities when a test ends, pass or fail. It clears entities only inside a template box, when
  a structure is placed there. 1.20.1 never reuses a slot between batches: each batch is laid out on fresh ground
  further along, so a test's leftovers stay for good. Blocks outside the 16x12x16 template (sky pads, far floors) are
  never touched. Leftovers hurt where another test works: a wide or offset pad over a neighbour's arena, units that
  keep fighting and pick a neighbour's units as targets, a flag every later test reads.

## The fix: `TestCleanup` (new)

- `TestCleanup.atEnd(helper, undo)` hangs `undo` on the test's own `GameTestInfo` as a `GameTestListener`. The
  `GameTestInfo` is found by its type, the way `GameTestShards` finds the batch list. Steps run last in, first out,
  each once, each in its own try. They run on a pass, a fail and a timeout alike, in the ending tick. The returned
  Runnable runs the step early and is then spent, so the old straight-line cleanup lines still work.
- `TestCleanup.entities(helper, made)`: discards whatever is in the list at the end.
- `TestCleanup.blocks(helper)` returns a `BlockUndo`. `set(pos, state)` remembers what stood there first.
  `region(a, b)` snapshots a box where the game also builds (stamped patches, placed buildings). Everything is put
  back at the end.
- `TestCleanup.rtsPathfinding(helper)`: a counted hold on `UnitServerEvents.rtsPathfinding`. The flag goes back to
  its old value only when the last holder ends. It is given back at the end however the test ends, so it replaces the
  hand-restored `wasRts` and serves the same purpose as PR #177's `RtsPathingLease` (see OPEN 1).
- `TestCleanup.sweep(info)` is called from `GameTestShards`' reporter for **every failed test**. It discards every
  non-player entity the failed test left in its own arena (the full 16x12x16 template box) or on its own pads: the
  columns of every block it set through `TestCleanup.blocks` (`set` or `region`), from the pad's lowest block to 4
  above its top. That covers the arena floor and the shared sky-pad helpers (`SkirmishGameTests.skyPad`,
  `ArenaReady.pad`, the dragon pads), so this one hook covers the biggest class below (B, live units).
  It is deliberately not the whole column: tests still running send units over their neighbours' arenas on purpose
  (`TerrainGameTests.formation_move_spreads_into_a_line_facing_the_move` walks 12 units to corner.x+28 at y 280 for
  about 400 ticks; the bone dragon in `DragonFlightGameTests` flies to x-20; the Admiral in `SkirmishGameTests` is 60
  out). A whole-column sweep after a neighbour failed would remove them and turn that test red (or green).

Proof:
- `TestCleanupGameTests.cleanup_runs_when_a_test_fails_times_out_or_passes`, in its own batch `test_cleanup`. It makes
  three tests by hand (real `GameTestInfo`s that no runner knows about) and ticks them to their end: a thrown
  assertion, a timeout and a pass. Each time it checks four things: its unit is gone, its block above the arena is put
  back, rtsPathfinding is back, and two steps ran newest first, once each. It then runs the sweep on itself: its units
  in the arena, on the arena's last x/z row and on its own pad go. A unit 70 above the arena on no pad of its own, one
  in the gap beside the arena, and the unit of a second, still-running hand-made test standing on that test's own pad
  over this arena all stay. When that second test then times out, its own cleanup takes its unit and its pad.
- `TestCleanupTest` (JUnit) covers the bookkeeping without a server: order, run-once, an early run, a throwing step
  not skipping the rest, and a step added while ending. It also covers the pad footprint the sweep reads (on the pad,
  4 above it, not higher, not beside, a `region` box from bottom to top).
- `BlockUndo.restore` sets blocks back with flags 2|16 (no neighbour updates), so a torch or door on a pad being taken
  away does not pop off as an item.

## A. Global flags and shared state (HIGH: every later test in the shard sees them)

| Where | What stays | Path that skips cleanup | Hurts | State |
|---|---|---|---|---|
| OrdersObeyGameTests.java:182 `a_move_into_unloaded_chunks_walks_toward_it_never_stands_busy` | `rtsPathfinding = true`, invulnerable vindicator | `ArenaReady.whenTicking(150)` throws at L212; restores only on own branches (L197/215/271) | every later test's pathing; L402 there becomes "proves nothing" | FIXED (`TestCleanup.rtsPathfinding`, `entities`) |
| OrdersObeyGameTests.java:289 `a_unit_shifted_along_the_wall_still_gives_up...` | same | whenTicking throw at L314 | same | FIXED |
| PathingBudgetGameTests.java:206 `vanilla_path_wall_detour_beyond_the_short_search_still_arrives` | `rtsPathfinding = false`, `VanillaPathBudget.shortRangeCapForTests = 6`, stone rim fence outside the arena (x/z -1 and 16), `sealed()` rim stone, a vindicator | whenTicking throw L245; also a timeout (500 < order + 420 if the order is late) | every later vanilla-path test | FIXED (`restore` is an `atEnd` step) |
| PathingBudgetGameTests.java:84 / :150 (melee chaser, shooter) | `sealed()` rim stone outside the arena, slipslopslap cheat, units | whenTicking throw L98/L161; timeout 400 < order + 300 | later tests reading the cheat or crossing the rim | FIXED |
| PerfScanGameTests.java:71 `neutral_aggro_points_a_hostile_mob_at_a_neighbour` | game rule NEUTRAL_AGGRO = true, villager plus 2 husks on pad 135 | reset sits after the assertion in `succeedWhen` (L89-92); also fail at L77 | every later test (wild mobs aggro units) | FIXED |
| PerfProfileGameTests.java:77 `lag_capture_profiles_the_tick_and_names_the_heaviest_sections` | `Sys.force` hold (measuring forced on), 24 fighting vindicators | released only once a summary exists (L88); timeout 500 | `sections_are_inert_when_nothing_is_measuring` passes without checking; tick cost | FIXED |
| ParadeGroundGameTests.java:35 `parade_spawns_every_unit_type_and_runs_a_loop` | static `ParadeGround.active` plus 50+ looping units | `helper.assertTrue` L37 throws after `start` | every later test; a later `start` finds one active | FIXED |
| SkirmishGameTests.java:1322 `bots_play_each_other_without_errors` | 5-6 RTS bots in `rtsPlayers` / `BotServerEvents.brains`, their units, 2 alliances, 2 EVENT_BUS listeners | `helper.fail` in the test method at L1322; all cleanup was in the L1495 callback | everything reading rtsPlayers, brains or all units; later soaks | FIXED (three `atEnd` steps) |
| SkirmishGameTests.java:835 / :1139 (raise dead, dragon risen skeletons) | `UnitServerEvents.maxPopulation = 0` | only an exception from `raise` (no finally) | every later production | FIXED (try/finally) |
| SpawnReadyGameTests.java:194 `all_ready_on_the_live_phase_sends_phase_off_and_starts` | `StartPhase.live` = this phase (broadcast) for up to 120 s | assert in `succeedWhen`, timeout 200 | later tests reading `StartPhase.live()` | FIXED |
| FogSnapshotGameTests.java:361 `capture_bounded_when_barrier_hangs` | global FogChunkSnapshot still capturing, 9 FORCED tickets | timeout 20000 if capture never ends (the regression guarded) | fog_snapshot batch, real match start | OPEN |
| LadderGameTests.java:232 `ladder_match` | 2 bots, 2 Forge listeners, `StuckNudge.observer` | `helper.fail` L232 before `cleanup()` | only when BAT_LADDER_MATCH is set (nightly) | OPEN |
| ExtractorFootprintGameTests.java:169/193/217/241 | `MetalPatches.forgetAll()` in the DEFAULT batch wipes every concurrent test's stamped patches | every run, not only fail | patch tests running beside it | OPEN (not a fail leak; move to its own batch) |

## B. Live units left on a fail or timeout (MED: they fight or aggro neighbours ~21 blocks away)

All of these are covered by `sweep` when the units stand in the test's own arena or on a pad it set through
`TestCleanup.blocks` (`skyPad`, `ArenaReady.pad`, the dragon pads). **FIXED-sweep** means they are covered that way.
Units that walk off the arena or the pad, and units on a pad set any other way, are not covered and are listed
separately.

**SkirmishGameTests (assert in a `runAfterDelay` callback, discard only after it; or `helper.fail` before the discard):**
L366 war horn, L391 liveries, L722 dgun, L761 horde momentum, L800 sunforged formation, L831 raise dead, L1020
veterans, L1101 sun colossus, L1842 crypt tide, L1883 war drums, L1921 bastion aegis (also leaves a dome in static
`BastionAegis.DOMES`: OPEN), L1960 magma rupture, L2000 holy bell, L2081 withering fog, L2589 match history, L3219
commander death blast, L5903/L5916 broadside, L5991 riptide, L6666 fog bank, L4945/L5073 thicket (also static
`ThicketBlockEntity.LIVE`: OPEN), L1143 dragon skeleton, L2123 sunrise sortie, L3278 grove warden, L4110 archers,
L5677 admiral, L359/L714 commanders, L3404/L5744/L5756/L6728/L6740 units_are_made loops, L8167 thunder sage, L8339-8379
heaven's judgement, L8510 jade guard, L8666-8699 seal (also a Ward left in `WardServerEvents.WARDS`: OPEN), L8750
frost field, L8826 rocket battery, L8901 ward (Ward in WARDS: OPEN), L9200 earthshaker, L9375 mirror images, L10445
hold fire, L10475 return fire, L10519 kill counter, L11407 refund, L11544/L11660 bot army (pads 120/150; units may walk
off east), L12146 deploy turret, L13356 area reclaim. FIXED-sweep. Commanders among them also sit in
`CommanderAntiAir.track` until discarded.

**Other files, same pattern, FIXED-sweep:**
- AttackMoveGameTests L83, L186 (also FIXED by an explicit `atEnd` cleanUp, which clears queued actions too).
- ArtilleryGameTests L264, L304, L331.
- CommandQueueGameTests L127, L203, L362, L459, L530.
- IdleWorkerGameTests L55 (a commander).
- HeroGameTests L271.
- H13GameTests L296, L328, L368 (Ash Shaman casting by itself).
- HeroAiV2GameTests L525 (a timeout when REST starts late; the `brains` entry is OPEN, LOW).
- OrdersObeyGameTests L95, L384, L459 (also FIXED explicitly).
- QueueBetweenOrdersGameTests L57.
- PerfPushGameTests L65 (40 units).
- PerfScanGameTests L33.
- OnboardingGameTests L238, L276, L307, L351.
- TerrainGameTests L355, L556.
- SupportAiGameTests L118, L189, L283, L346, L405, L469, L542 (pads 140-260).
- SupportEngageGameTests L62.
- WinPaysGameTests L135/L141, L224/L229, L270 (the wreck stays in `WreckServerEvents`: OPEN, LOW).
- QueueAfterOrderGameTests L42.
- PerfTickingGameTests L70.
- QueueEditGameTests L245.
- WorkerQueueFogGameTests L133, L273.
- WagerBookGameTests L349/L414/L466/L515/L706.
- BotGameTests L83.

**Units OUTSIDE their test's arena and pads (not covered by the sweep):**

| Where | What | Path | State |
|---|---|---|---|
| CombatEngageGameTests.java:204-507 (6 tests) | 2-300 fighting units on glass pads 9000 W/N, FORCED chunks keep them ticking | `whenReady` throws (L147); asserts before cleanUp in `succeedWhen` | FIXED (`atEnd` cleanUp, which also releases chunks) |
| SkirmishGameTests.java:10786 via L10950 / L11195 (production_buildings_place_every_unit_*, wide_units_walk_away) | produced units 9000+ south, FORCED tickets of held jobs | `holdAndAwaitOrFail` throws in a callback | OPEN |
| RaidGameTests.java:1244 | 6 vindicators, worker, raider, a raid tower in `getBuildings()`, forced chunks (x about 69000) | timeout 1500 < 1300 + 420 | OPEN |
| NewMatchCases.java:95 / :235 | vindicator at y 311 plus a registered wind generator | undo never discards them; `helper.fail` L100/113/121/239/250 | OPEN (the pad at 310 is not set through `TestCleanup.blocks`, so the sweep does not reach it) |
| HeroAiGameTests / HeroGameTests / H13 / CommandQueue early "could not spawn" returns | partial spawns | create() null only | LOW, FIXED-sweep |

## C. Buildings and server registries left on a fail or timeout

| Where | What stays | Path | State |
|---|---|---|---|
| AttackMoveGameTests.java:149 `attack_move_turns_from_a_building_to_a_unit` | wind generator placement in `BuildingServerEvents.getBuildings()` | remove sits after the asserts in `succeedWhen` (timeout 300) | FIXED |
| SkirmishGameTests.java:301 placed_extractor_and_windmill_survive_as_sites | 3 sites in `getBuildings()` (any path), `MetalPatches.STAMPED` entry | `forgetAll` at L331 skipped on fail | OPEN |
| SkirmishGameTests.java:647 builders_send_nano_beams | wind generator site in `getBuildings()`; worker discard after the assert in `succeedWhen` | timeout 200 | OPEN (worker FIXED-sweep) |
| SkirmishGameTests.java:7904 build_order_to_an_unreachable_site_ends | Verdant wind generator site plus its forced chunk ticket | `helper.fail` in `succeedWhen` before remove | OPEN |
| SkirmishGameTests.java:12041 sapper_charge_damages_a_building | Machine Shop in `getBuildings()` plus forced chunk; a live charge | fuse-check callback has no try | OPEN |
| SkirmishGameTests.java:277 wildlife_is_cleared_from_bases_but_pigs_stay | Town Centre in `getBuildings()` (base wildlife sweep near y 290) | assert in the test method | OPEN |
| `getBuildings().remove` without `forceChunk(false)` (Skirmish L7911, L8190, L9507, L9955, L11996/12034/12074) | FORCED ticket set by `raiseSite` | every path | OPEN (LOW/MED, chunk load) |
| Pools in `ResourcesServerEvents.resourcesList` (about 40 places, owner-scoped) | a Resources pool per owner | `helper.fail` before remove | OPEN (LOW) |
| Tidepool / thicket / ward / decoy / frost registries (Skirmish L5429, L5594, L5963, L4920, L5054, L5134, L8697, L8901, L9375) | per-owner entries in static registries | `fail` before clear | OPEN (LOW-MED) |
| `GameTestChunks.whenEntitiesReady` throw: AudienceGameTests L368, BuildingGridGameTests L545, CommandQueueGameTests L948, RaidGameTests (8 tests), TargetScanGameTests (5 tests) | FORCED tickets far away | the wait throws before the body's finally | OPEN (LOW: load only) |
| WorldStopGameTests.java:83/92 | temp block in `BlockServerEvents.tempBlocks` (reverts after 2000 ticks) | `fail` before `revertAndClear` | OPEN (LOW) |
| LobbyColors L123, TierGate L434, Skirmish L12931/L14777 | bots / RTS players / matches | only an unexpected exception | OPEN (LOW probability, HIGH impact) |

## D. Blocks outside the arena (shared space)

Vanilla resets only the 16x12x16 template, and never places a later batch's arena in an earlier one's slot. Everything
here stays in the world for good. It hurts when it stands where another test works: offset or wide pads land over
other tests' arenas in the same batch, and a pad at an absolute height can stand where a later test's units walk.

| Where | What | When | Collides with | State |
|---|---|---|---|---|
| `SkirmishGameTests.skyPad` (L3313; pads at 120-312, about 15 tests) | 16x16 glass | every path | units of a test beside it at the same height | FIXED (helper restores at end) |
| `ArenaReady.pad` (L48; about 20 callers, 130-301, plus 6 air layers) | 16x16 glass | every path | e.g. pad 135 (TerrainGameTests, PerfScan) vs the WaterPathing basin 132-141 | FIXED |
| `DragonFlightGameTests.skyPad`, `DragonDiveGameTests.skyPad` (y 290; 11 tests) | 16x16 glass | every path | CombatEngineer pad at 290 | FIXED |
| WaterPathingGameTests.java:69/134 | stone and water basin at abs y 132-141 | whenTicking throw (`clear` only on own branches) | `ArenaReady.pad(135)` users | FIXED |
| DefenceGameTests.java:104 | stone floor plus wall and gate at abs y 210-216 | every path | pads at 200-216 (BuilderAssist, GhostClick, CombatEngineer, FogMex) | FIXED (`region`) |
| ExtractorFootprintGameTests.java:161 / :208 | dirt and grass at 219-220 with bamboo to 236; at 229-230 with a hill to 242 | every path | HeroGameTests possession strips at 228-240 | FIXED (`region`) |
| SkirmishGameTests.java:4152 built_blocks_still_count_after_the_world_settles | Heartwood Hall plus a tide wind generator and stone floors at y 229+, 120-160 blocks off (over other arenas) | every path | the arena under them | FIXED (`region` per building) |
| SkirmishGameTests.java:5648 tidewrought_capitol_places_and_the_admiral_spawns | 19x19 sand pad at 199-200 plus the Wreck Harbour, offset +60/-60 | every path | patch tests at 199-210 reading `solidTop` | FIXED (`region`) |
| SkirmishGameTests.java:1762 wind_generator_accepts_a_step_but_not_a_cliff | dirt columns 238-247 | every path | Leviathan pad at 240 (L7318) | FIXED (`region`) |
| BuildingCountGameTests.java:31 | Heartwood Hall plus stone at about y 227-250, 240/160 off | every path | the arena under it | FIXED |
| MapRimGameTests.java:188 | 113x113 dirt layer at y 199 (spans neighbours); finally clears 200-216 only | every path | patch tests at 199-200 | OPEN |
| MatchLifecycleCases.pad (226-236, 310), NewMatchCases (310), WorldMemoryGameTests.pad (209-210, and clears everything above), RegressionGameTests.stamp (235), ReactionGameTests.pad (32 wide: 16 into the +x neighbour), TerrainGameTests L458 (49 wide at 279), RealmMapGameTests L369 (slabs to near the build limit), BotGameTests.stonePad (26 wide at 170, 230), BiomeBase / BuildingGrid / Duel / Raid / TargetScan far pads, Skirmish grassPad (29 wide at 169-180), stampOnPad / stampedRow / verdant pads, FactoryGuard floor (+-8 round the Barracks), GraveyardGameTests ring | floors and structures | every path | the next test at the slot; the wide ones reach into neighbours | OPEN |

## Rules for new tests

1. Register cleanup the moment the state exists: `TestCleanup.atEnd(helper, undo)` (or `entities`, `blocks`,
   `rtsPathfinding`). Never put cleanup after `helper.fail`, `finish(...)`, an assertion, or at the end of a
   `succeedWhen` lambda.
2. Blocks outside the template go through `TestCleanup.blocks(helper)`. Use `.region(a, b)` before game code builds
   there.
3. A server-wide flag is held, not set: `TestCleanup.rtsPathfinding(helper)`. For any other flag, save the old value
   and give it back in an `atEnd` step.
4. No nested `runAfterDelay` inside GameTest callbacks (it crashes the whole shard). A `succeedWhen` test must not
   clean up before its `helper.fail` (it would go green on the next tick); record a sticky verdict instead.

## OPEN follow-ups (not in this PR)

1. PR #177 (`claude/order-latency-graveyard`): `RtsPathingLease.release()` runs only from the test's own `finish`
   consumer. A `whenTicking` throw or a timeout leaves `holders > 0`, so the flag stays on for good. Use
   `TestCleanup.rtsPathfinding(helper)` (same counted hold, given back at the end), or call
   `TestCleanup.atEnd(helper, RtsPathingLease::release)` right after `take()`.
2. Buildings and registries in section C: one `atEnd` step each (remove plus `forceChunk(false)`/`Blueprints.cancel`).
3. `whenEntitiesReady` / `holdAndAwaitOrFail` callers: register `releaseAll` with `atEnd` before the wait.
4. The every-path floors in section D marked OPEN: route them through `TestCleanup.blocks(helper)`.
5. A suite sanity check: the air above every arena at the start of each test (harvest idea from #338).
