# Code review: the Oct 11 merges and the 8v8 hot paths

Two rounds. **Round 2** (branch `code-quality-2`, base 102ba10: heroes, bots on maps, walls/traps, Brassworks T3, big
units and game enders, unit skins, building detail, round 1) is at the end of this file, findings R2-01..R2-07, plus
CR-11 resolved. **Round 1** (branch `code-quality`) follows directly below.

Branch `code-quality` (base: origin/main 30a1b6f). Scope: the merges on main since e211f6a (economy pace + batch
commands + tree clearing, building levels / annexes / decommission, HUD declutter, gothic splash, lively buildings +
geothermal, pre-made maps + start phase, Brassworks T2, hero possession), checked for semantic conflicts between
features, then the server hot paths that dominate an 8v8 match, client per-frame work and packet volume.

Severity: **High** = breaks competitive integrity or loses player state; **Medium** = a real bug or exploit, or a
measurable cost at 8v8; **Low** = polish, latent risk, or a small cost. Status: FIXED (on this branch, with a GameTest
in `gametest/RegressionGameTests.java` where the logic is testable), PROPOSAL (left for a focused change), NOTE.

## Summary

| ID | Sev | Area | Finding | Status |
|---|---|---|---|---|
| CR-15 | High | perf, benchmark | The 8v8 benchmark walked ~2,355 units (every test in its batch), not its 400: CI red on most branches | FIXED (own batch): 6.0-8.2 ms -> 2.46 ms |
| CR-06 | High | hero x fog/economy | Hero-role players are "not RTS players", so every observer rule applies to them: fog off, enemy economy, enemy stats | FIXED (fog: hero-slice-3) + test |
| CR-02 | Medium | decommission x save | Decommission progress not saved; a reload leaves a half-dismantled, already-refunded building to repair and sell again | FIXED + test |
| CR-01 | Medium | decommission x batch commands | A decommissioning lab still accepts production (multi-building card, stale card) | FIXED + test |
| CR-03 | Medium | save/load | `DataType`s registered in lazily-initialised classes are dropped on a freshly launched game (the Repeat toggle) | FIXED |
| CR-04 | Medium | possession x camera toggle | Backspace with one enemy/neutral unit selected sends a doomed possess request and swallows the view toggle | FIXED |
| CR-05 | Medium | start phase x PacketAuth | Start-position clicks are not rate limited; each runs a spiral search and a server-wide broadcast | FIXED |
| CR-08 | Medium | perf, production | The production step asked the population check three times per item per tick (each walks every building's queue) | FIXED |
| CR-09 | Medium | perf, annexes | Annex counting was O(buildings x annexes) per lab per second | FIXED |
| CR-10 | Medium | perf, factions | `Factions.getFaction(Unit)` was three hash lookups, per unit in several per-unit passes | FIXED + test |
| CR-11 | Medium | perf, units | `getOwnerName()` reads synched entity data under a lock (1.20.1), and is called per unit in every pass | FIXED in round 2 (lock removed centrally) |
| CR-14 | Low | perf, client HUD | Economy bar streamed every client unit every frame for the worker count | FIXED |
| CR-12 | Low | perf, capture sites | `Kind.values()` cloned an array per site per call (fog, tick, every production step) | FIXED |
| CR-16 | Low | pace x decommission | Decommission speed ignored the economy pace (construction, production and wreck reclaim follow it) | FIXED |
| CR-07 | Low | hero x keybinds | The Hero start message hard-codes "Backspace" (rebindable) | FIXED (hero-slice-3) |
| CR-13 | Low | maps x save | A world saved while a pre-made map rises / starts are placed reloads with no match and a half-raised map | PROPOSAL |
| CR-17 | Low | hero x reset | Client `heroRole` is only cleared on logout; after /rts-reset Backspace with nothing selected asks the server for a hero | FIXED (hero-slice-3) + test |
| CR-18 | Low | perf, queue lines | Queue-line payloads are built for owners nobody receives (bots) | PROPOSAL |
| CR-19 | Low | RoN legacy | Dead supply code, `new Random()` per block placed, per-tick capitol re-count | NOTE |

## Integration bugs (semantic conflicts between merged features)

### CR-06 (High) Hero-role players are treated as observers
**Where.** `MultiplayerLobby.seatAll` gives a Hero seat no base, so `PlayerServerEvents.isRTSPlayer(hero)` is false.
Every "observer" rule keys off exactly that: `FogOfWarServerEvents.isFogActiveFor` / `FogOfWarClientEvents.isEnabled`
(fog off - the whole map, including enemy bases and armies), `EconomyClientboundPacket.sync` (every side's economy,
4x a second), `PlayerPanelServerEvents.entriesFor` (every side's income, army value, storage).
**Why it matters.** A Hero is a player on a team, usually on voice with their commanders: in a competitive 8v8 they
are a free full-map scout. `design/hero_mode.md` lists "team fog for Hero-role players" as slice 2, but the economy and
stats leaks were not listed at all.
**Fix.** Economy packets and the stats table now treat a Hero as a member of their commanders' team (own team's data,
never the enemy's; real observers unchanged) - test `hero_role_player_sees_only_their_teams_stats`.
**Proposal (fog, left to the hero slice-2 work to avoid colliding with the parallel heroes branch):**
`isFogActiveFor` = RTS player **or** `HeroRoles.isHeroRole`; in the per-player vision union merge
`coveredByOwner` of `HeroRoles.commandersOf(name)` (and their allies); invalidate `isRtsPlayerCache` in
`HeroRoles.assign/clearAll`; client side `refreshLocalIsRTSPlayer` also true for `PossessionClientEvents.isHeroRole()`
and re-run it on the ROLE packet; and `HeroRoles.clearAll` / match end must send `role(false)` so client and server
never disagree (a client with fog on and a server sending no vision sees a black world).

### CR-02 (Medium) Decommission was not saved
**Where.** `BuildingPlacement.decommissioning / reclaimProgress / reclaimPaid` were memory only ("a reload simply
leaves the building standing"), but the dismantled blocks (chunks) and the refunded metal (resource pool) are saved.
**Why it matters.** After any reload (singleplayer quit, server restart) the building was a damaged, not
decommissioning building whose metal had partly been paid out: repair it (free) and decommission again - the
repeat-for-metal loop `BuildingReclaim`'s own comment says decommission must never allow.
**Fix.** `BuildingReclaim.DECOMMISSION` (a per-building `DataType`) is written in `BuildingServerEvents.saveBuildings`
and restored in `loadBuildingsAndNetherZones` (`restoreFromSave`), which puts the building back in the active list -
the work resumes where it stopped. Test `decommission_refuses_production_and_survives_a_reload` (NBT round trip).

### CR-01 (Medium) A decommissioning lab accepted new production
**Where.** `BuildingReclaim.start` refunds and clears the queue once; `ProductionPlacement.startProductionItem` never
looked at `decommissioning`. The new multi-building card (`BuildingBatchServerboundPacket` QUEUE/APPLY) selects every
lab of a type, including one being dismantled, and a client card opened before the order was given can still click.
**Why it matters.** Metal flows into units (or a level upgrade) on a building that removes itself; the queue is not
refunded at the end (`finish` just demolishes).
**Fix.** The server refuses production on a decommissioning placement. Same test as CR-02.

### CR-03 (Medium) DataTypes lost on a freshly launched game
**Where.** A `DataType` registers itself in its owner's static initialiser (`DataType.createRegistered`).
`BuildingSaveData.load` decodes every building's data storage before any placement exists; an unknown key is logged
("Unknown DataType") and dropped. `ProductionPlacement.REPEAT_QUEUE` lives in a class nothing initialises before that
point, so after a game restart (not after a mere world reload in the same session - GameTests never see it) the
factory Repeat toggle came back off. The new decommission type would have had the same problem.
**Fix.** `loadBuildingsAndNetherZones` touches both types before decoding. **Proposal:** one eagerly loaded
`BuildingDataTypes` holder (called from mod setup) for every DataType, so a new one can't repeat this.

### CR-04 (Medium) Backspace = possess and RTS/Minecraft toggle
**Where.** `OrthoviewClientEvents.onInput` -> `PossessionClientEvents.onToggleKey`: with exactly one unit selected,
Backspace sends a possess request and returns true (the view toggle is skipped). Selecting a single enemy or neutral
unit (to read its card) is common; the server always refused, and the key did nothing at all.
**Fix.** The client only asks for units it could be allowed to drive (own, allied, or any when Hero role); otherwise
the old view toggle runs. The server check (`PossessionServer.mayPossess`) is unchanged.
**Note (UX).** With one own unit selected Backspace now possesses instead of switching to the Minecraft view; the
tutorial / camera hint text still describes only the view toggle.

### CR-05 (Medium) Start-position packets unthrottled
**Where.** `StartPhaseServerboundPacket` -> `StartPhase.handlePlace` had no `PacketAuth.allow`. Each packet runs
`clearSpot` (up to MAX_SNAP rings, every metal patch per candidate) and, on success, broadcasts the map state to
every player. **Fix.** 8/s, burst 16 ("startpos" bucket).

### Checked and fine
- **Lobby snapshot field order** (`LobbyClientboundPacket`): read/write agree (pace, then seats with `heroRole`, then
  map and choosePositions); the seat count is capped on both sides. `MapStateClientboundPacket` and
  `EconomyClientboundPacket` (pace byte appended) agree too.
- **Production speed stacking**: `ProductionItem.tick` applies (level build power + 0.4 per annex) x pace x cheat, once.
  Construction applies pace once (`tickFlowConstruction`), income once (`recalculateIncomeAndStorage`), wreck reclaim
  once; the Ancient Forge discount scales cost, not speed. No double application.
- **Lobby actions** (SET_PACE / SET_MAP / SET_STARTPOS / SET_ROLE): host-gated or seat-owner gated, value-checked,
  rate limited; Start refuses a Hero on a team with no commander.
- **Keybindings**: I (stats table vs ability 8), M (minimap vs move), H, "." and F7 (debug vs resign "no") all have
  explicit precedence; config spec has no duplicate keys.
- **HUD layout**: `tools/hud_layout.py` - 76/76 and 342/342 scenes clean.
- **Pace persistence**: saved in `bt-match-state`, reset on a new world, synced on the economy packet.
- **Possession teardown**: released on logout, death, unit removal, /rts-reset; client clears on logout.
- **Mixins**: the loading-overlay / title mixins are client-only in the mixin config; `PossessionMobMixin` costs one
  empty-map check per mob AI step.

## Hot paths at 8v8

Last main CI before this branch ("[Stress]" notice, 400 units / 40 wrecks / 18 sites, average per pass):
queue lines 2.13 ms, bot enemy scans 1.05, momentum 0.83, player panel 0.80, formation 0.68, wreck reclaim 0.44,
unit grid 0.34, builder lookup 0.26, capture points 0.20 - total 6.72 ms of the 8 ms budget.

**After this branch (5115519, benchmark in its own batch, 400 of 400 units in allUnits):** queue lines 0.27 ms,
bot enemy scans 1.25, formation 0.36, capture points 0.19, wreck reclaim 0.13, momentum 0.13, unit grid 0.08,
builder lookup 0.06 - **total 2.46 ms**. (The old-scan references fell from 25 / 14 ms to 3.0 / 2.6 ms: they too had
been walking ~2,355 units.) The top real cost is now the bot enemy scan (64 grid queries, 20 us each).
Caveat: "player panel" now reads 0.002 ms because no RTS player exists in the benchmark's batch and `buildRows`
returns early - **proposal:** register the eight stress owners as RTS players inside the benchmark so the stats table
stays covered.

### CR-15 (High) What the benchmark measured - FIXED
**Symptom.** CI was red for almost everyone: of the last eleven failed `build` runs on the feature branches
(hero-slice-2, big-units, bots-maps, building-detail, brass-t3, walls-traps), eight failed on
`stress_8v8_server_systems_stay_in_budget` alone at 8.24-10.12 ms; the others on `units_survive_reload_4`
(clockwork drone, brass-t3), `hero_gains_xp_from_a_kill` and `tidewrought_t2_units_attack` (Mortar Galley never hit -
also seen once on this branch with no Tidewrought change: **flaky**, one shell at 14.5 blocks either lands or not).
On this branch the same code gave 6.19, 7.36, 8.23 and 6.02 ms in four runs.

**Root cause (found with temporary profile counters in `UnitQueueSync`, one CI run, removed):** while the benchmark's
calls ran, `UnitServerEvents.allUnits` held **~2,355 units, not 400**. The benchmark ran in a default batch next to
~100 other tests (the bot soak armies alone are hundreds of units), and every per-unit system it times walks all of
them (1,857 of them had queue lines). Its figures scaled with whatever other tests were alive in its batch - which is
why branches that only *added tests* went over budget, and why the same code swung by 2 ms between runs.
(A first probe that timed `buildPayloads` over its own 442 units in a tight loop measured 0.21 ms against 2.1-2.6 ms
inside the benchmark; most of that gap was the 5x unit count, the rest cold caches between interleaved systems.)

**Fix.** The benchmark runs in its own GameTest batch (`batch = "stress_8v8"`; batches run one after another), so it
measures its own 400 units - the 8v8 it claims to measure - and its log line prints the `allUnits` size so this can't
silently come back: **2.46 ms**, "400 units (400 in allUnits)" on 5115519.

**Also done (CR-15a):** cheapest-first filter order where per-unit passes read synched entity data: queue lines read
health only for units that have lines; the stats table checks the commander tag before health; formation checks types
and the cached faction before owner and health; momentum checks the cached faction first. Same results.

**Proposals.**
- Tighten the budget now that it measures the right thing (e.g. 4 ms, ~1.6x the new total) so it catches regressions.
- Log `sum(cost_i / period_i)` (amortised per tick) next to the per-pass sum: the systems run at different rates
  (queue lines and momentum every 10 ticks, formation every 20, player panel every 40, grid every tick), so a per-tick
  system that doubles is invisible next to a 2 s one.
- Report each system's median pass as well as the mean (one GC pause lands on one system at random).
- Register the eight stress owners as RTS players inside the benchmark so the player panel is measured again.
- `tidewrought_t2_units_attack`: give the Mortar Galley more than one shot's worth of time, or place the target at
  the range its shell is tuned for, so it stops failing runs at random.

### CR-08 (Medium) Production step: population checked 3x per item per tick - FIXED
`ProductionItem.tick` called `isBelowPopulationSupply` up to three times; each call walks every building's queue
(`UnitServerEvents.getCurrentPopulation`, ~600 buildings at 8v8 late game) per producing item per tick. Now once.
**Proposal:** a per-owner, per-tick cache of the queued-population part (stale by at most one tick is harmless for
the "did we dip below" check; the queue-time afford check must stay exact).

### CR-09 (Medium) Annex counting - FIXED
`BuildingLevels.annexCount` (once a second per levelled lab) called `LabAnnex.hostOf` - itself a walk over every
building - for every built annex on the map. Now the owner and range are checked first (`LabAnnex.gap` made public);
same answer, since `hostOf` only ever returns an own lab within `ANNEX_RANGE`.

### CR-10 (Medium) Faction lookup - FIXED
`Factions.getFaction(Unit)` did `EntityType.getKey` (registry reverse lookup), `ENTITY_FACTION.get` and
`FACTIONS.get` on every call; momentum, formation (per neighbour), traits, targeting and HUD code call it per unit.
Now an identity-keyed cache per entity type, cleared whenever `registerEntity` runs. Test
`faction_lookup_cache_matches_the_registry`.

### CR-11 (Medium, FIXED in round 2 - see the round 2 section) Owner name reads
`Unit.getOwnerName()` is `entityData.get(ownerDataAccessor)`; in 1.20.1 `SynchedEntityData.get` takes a read lock.
Every per-unit pass (queue lines, player panel, formation, momentum, grid queries, targeting) pays it per unit.
Proposal: keep a plain `String owner` field on each unit, written wherever the accessor is set (and on the client in
`onSyncedDataUpdated`), and read that. It touches every unit class (or a shared mixin/base), so it was not done here
while seven feature branches add units.

### CR-18 (Low, PROPOSAL) Queue lines for nobody
`UnitQueueSync.buildPayloads` builds every owner's payload twice a second; only connected players receive one. In a
1 human + 15 bots match, 15/16 of the work is thrown away. Pass the connected names and skip others before `collect`.

### CR-14 / CR-12 (Low) - FIXED
Economy bar: the worker count was a stream over all client units every frame; now recounted 4x a second.
Capture sites: `Kind.values()` cached.

## Other findings

- **CR-16 (Low, FIXED)** decommission speed now scales with the economy pace like every other build-power flow.
- **CR-07 (Low)** `MultiplayerLobby.seatAll` tells Heroes "press Backspace"; the server can't know the binding - send
  a translatable key with the client filling in `Keybindings.toggleCamera.getDisplayName()`.
- **CR-13 (Low)** `MapMatch` / `StartPhase` live state is static only: a world saved while a pre-made map rises or
  starts are placed reloads with a half-raised map, no match, and `refusesLateStart` back to false. Either persist
  the phase or, simpler, refuse to save mid-phase in singleplayer and finish the phase instantly on server stop.
- **CR-17 (Low)** `HeroRoles.clearAll` (reset, lobby close) does not tell clients; `PossessionClientEvents.heroRole`
  stays true until logout, so Backspace with nothing selected keeps asking the server for a hero. Send `role(false)`.
- **CR-19 (Note, RoN legacy)** `getTotalPopulationSupply` keeps unreachable supply code behind `if (true)`;
  `giveMasonExp` allocates `new Random()` per block placed; an unbuilt capitol re-counts the owner's buildings every
  tick to decide its 2x build time (RoN's "second capitol" rule, which a BAR-like game may not want at all).

---

# Round 2: the merges since 30a1b6f

Branch `code-quality-2` (base: origin/main 102ba10). Scope: heroes slice 2 (seven faction heroes, XP, ranks,
possession rank-up), bots on the pre-made maps, walls / gates / traps / anti-raid towers, Brassworks T3 (Great Forge,
Iron Leviathan Engine), the T2 beasts and the game-ender framework (Sun Spire, Necropolis, Ward Spires), unit skins
(team-colour layer, captain banners), building detail, and round 1 itself. Checked: semantic conflicts, save/load of
the new state, every new serverbound packet, client null-safety outside a match, per-frame allocations in the new
renderers and layers, and 8v8 cost (the new systems were added to the stress benchmark). Then CR-11, the RoN rewrite
plan (`design/ron_rewrite_plan.md`) and rewrite 1 (delayed work on the game thread).

## Summary (round 2)

| ID | Sev | Area | Finding | Status |
|---|---|---|---|---|
| R2-07 | High | RoN legacy, threading | 19 `CompletableFuture.delayedExecutor` callbacks ran game code on a pool thread in wall time (rally orders, join syncs over the building list, beacon block changes, chunk resends); RoN's tick scheduler re-ran tasks after an exception, threw CME on re-entry and leaked tasks across worlds | FIXED (rewrite 1, `TickScheduler`) + 2 tests |
| R2-01 | Medium | game enders x save | Game-ender charge and its "announced" flag were not saved: every reload re-broadcast the start and pinged every enemy, and wiped the charge (a save-scum or server restart reset a bot's or player's game ender); Ward Spire stockpile likewise | FIXED + test |
| R2-05 | Medium | traps x heroes | A Hero-role player is not an RTS player, so hidden enemy traps were sent to them like to a spectator: a free trap scout (the CR-06 family) | FIXED + test |
| CR-11 | Medium | perf, every unit | Synched-data reads (owner, health, flags) took a read lock | FIXED (`SynchedEntityDataMixin`) |
| R2-02 | Low | perf, network | Anti-raid / anti-T3 tower tracer: ~16 `sendParticles` per shot, each a packet per player in range and a fog check per player | FIXED (BarFx batch) |
| R2-06 | Low | tests | `each_faction_hero_trains_at_its_lab` placed labs up to 280 blocks east of its arena: passed or failed with the test layout | FIXED |
| R2-03 | Low | perf, save size | `GameEnderServerEvents.onJoin` called `getPersistentData()` on every joining entity, creating (and saving) an empty ForgeData tag on each | FIXED |
| R2-04 | Low | perf, client | `HeroClientboundPacket` walked every client unit per packet; faction heroes now send mana/XP to everyone | FIXED |
| R2-08 | Low | perf, defences | Trap plate check asked the grid for the blast radius every 4 ticks; now the plate only | FIXED |
| R2-09 | Note | info leak | Hero mana / XP / ranks and worker loads are broadcast to every player (`PacketDistributor.ALL`) | PROPOSAL (rewrite plan #3) |
| R2-10 | Note | perf, other | `CapturePointServerEvents` also calls `getPersistentData()` on joining entities (line ~206) | PROPOSAL |

## Integration findings

### R2-01 (Medium) Game-ender and ward state lost on reload - FIXED
**Where.** `GameEnderPlacement` kept `announced`, `stage`, `stageProgress`, `raidDamage` in memory only ("like the
Rail Battery's cooldown ... only ever favours the defender"). But `announced = false` after a load made the first
tick call `announceStart` again: a server-wide chat line and an attack ping for every enemy at every reload, and the
whole charge (up to 150 s of it) was gone - in singleplayer a save-scum wipes a bot's game ender, in multiplayer every
restart does. `WardSpirePlacement.charges` had the same gap.
**Fix.** A generic per-placement save hook, `BuildingPlacement.writeSaveState()` / `readSaveState()`, called by
`saveBuildings` and `loadBuildingsAndNetherZones` (types touched before decoding, CR-03). Game enders save
`{announced, stage, progress, raidDamage}` (`GameEnderPlacement.CHARGE`), wards `{charges, progress}`; loads are
clamped. A strike that was mid-telegraph returns fully charged and re-aims (re-announcing) on its first tick, so the
victim still gets the full warning. Test `game_ender_charge_and_ward_stockpile_survive_a_reload`.

### R2-05 (Medium) Hidden traps leaked to enemy heroes - FIXED
**Where.** `FogOfWarServerEvents.canPlayerSeeBuilding` hid a trap only from RTS players. Hero-role players have no
base (round 1, CR-06), so the enemy hero received every hidden trap: outline, health bar, minimap mark.
**Fix.** `Concealment.isBuildingHiddenFromPlayer`: hidden from enemy RTS players and from Hero-role players who serve
none of the trap's side; the owner's own hero and spectators still see it. The check now runs only for traps (it was
a name lookup per building per player). Test `hidden_trap_is_hidden_from_an_enemy_hero_role_player`.
The broader CR-06 item (Hero-role team fog) is still open and belongs to the hero branch.

### Checked and fine
- **Serverbound packets.** The only new one is `PossessionServerboundPacket.RANK_UP`: rate limited ("possess_act"),
  possessor-checked, slot range-checked, and spends points under `HeroAbility.rankUp`'s own rules. Embark, Breach
  Charge and the defences go through the existing owner-checked unit-action and building packets; gates and traps
  have no packet (server-driven).
- **Hero save/load.** XP, skill points, ranks (by slot), mana and max mana go through RoN's `addHeroUnitSaveData`;
  `FactionHero.loadHero` re-applies stats and mana after the ranks. Ossuary Behemoth stacks are saved and re-applied
  idempotently; Mossback Bison riders are vanilla passengers (saved inside the vehicle) and re-register on join.
  Trap `revealed` and tower cooldowns are deliberately not saved (documented in `design/defences.md`).
- **Gates.** Compare block state rather than the flag, so a reload or a repaired gate converges on the next check.
- **Renderers / layers.** `TeamColourLayer` caches the team texture per base texture (one map lookup per frame),
  `BackBannerLayer` bakes its parts once; the beast renderers draw block pieces through `BlockPieces` (no allocation);
  `HeroRenderers` reuse static textures. Nothing allocates per frame. Minor: `TeamColourLayer.TEAM_TEXTURES` is not
  cleared on a resource reload (a pack adding a `_team.png` needs a restart).
- **Client outside a match.** New client code null-checks `MC.player` / `level` (possession toggle, hero packet,
  wall line drag); the client chat follow-ups that ran 2 s later on a pool thread now check the player (R2-07).
- **Hero damage stacking.** `damageTakenMult` (hero `hurt`), the Oriflamme aura and the bison rider halving
  (`LivingHurtEvent`) multiply once each; no double application.

## Rewrite 1 and CR-11

### R2-07 (High) Game code on a pool thread - FIXED
See `design/ron_rewrite_plan.md`, "Rewrite 1". In short: `TickScheduler` (game thread, game ticks, once, in order,
re-entrant, exception-isolated, allocation-free per tick, cleared on stop / logout) behind
`TaskSchedulerServerEvents` and a new `TaskSchedulerClientEvents`; all 19 delayed executors moved onto them. Tests
`tick_scheduler_runs_each_task_once_in_order_on_time`, `server_tasks_run_on_the_server_thread_in_game_ticks`.

### CR-11 - FIXED centrally
A per-unit owner field needs ~100 unit classes edited (each declares its own accessor). The cost CR-11 named was the
read lock in `SynchedEntityData.getItem`, so `mixin/SynchedEntityDataMixin` redirects that lock's `lock()` /
`unlock()` to no-ops: every synched read (owner, health, flags) on every entity becomes a hash lookup. Safe because
each side's entity data is only touched on its own main thread, the map is only structurally changed during
construction (`define`, still write-locked), later game versions dropped the lock, and R2-07 removed this mod's
off-thread game code. Both redirects are required, so a game update that changes `getItem` fails loudly at startup.

Measured with two new logged references in the benchmark (400 units x 25 owner + health reads):

| run | synched reads x10k | plain-field reads x10k | REF bot scans (old full scan) |
|---|---|---|---|
| 6371d02, lock (one sample) | 1.567 ms | 0.172 ms | 2.829 ms |
| ba01050, no lock | 0.656 ms | 0.134 ms | 0.978 ms |
| 0cdff1d, no lock | 0.629 ms | 0.086 ms | 0.933 ms |
| a9fa563, no lock | 0.967 ms | 0.129 ms | 1.611 ms |
| f182c51, no lock (fast runner) | 0.311 ms | 0.066 ms | 0.417 ms |

Runner speed varies 2x, so normalise by the builder-lookup reference (reads no synched data): synched reads cost 0.45
of it with the lock (one sample) and 0.16-0.30 without (four samples) - **roughly half of every synched read gone**.
One baseline sample is thin; a second lock run would firm the number up, but the direction matches every per-unit
system that reads owner/health (formation, bot scans). The rest of the gap to a plain field (~50 ns per owner+health pair) is the
megamorphic `getOwnerName()` call over ~100 unit classes plus the `Float` unboxing of health; a `UnitCore` with a
plain owner field (rewrite plan #4) would close it.

## 8v8 benchmark (round 2)

The stress scenario now also fields one faction hero per owner (8), and per owner 4 gates, 6 traps, 3 anti-raid towers,
a game ender and a charged Ward Spire (32 / 48 / 24 / 8 / 8), as built, unregistered placements inside each army's
cluster; the new budgeted system "defences+game enders" times every gate's enemy check, every trap's plate check,
every tower's target pick and every game ender's aim + ward search **in one pass** (worst case: in play they run every
5 / 4 / 10 ticks and once per strike, about a fifth of that per tick).

c09ccfc (lock, before R2-08): total 2.98 ms, of which defences+game enders 1.02 ms; bot enemy scans 0.89, queue lines
0.25, formation 0.26, wreck reclaim 0.21, capture points 0.16. Without the lock: ba01050 2.88, 0cdff1d 3.12, a9fa563
3.16, f182c51 (with R2-08, a fast runner) **1.53 ms** - runner speed swings the total 2x, so compare systems against
the REF lines of the same run, not totals across runs. All far inside the 8 ms budget.

**Proposals.** Tighten the budget from 8 ms to ~5 ms now that it measures the right thing (round 1 CR-15); amortise
per system by its period in the log line; register the eight stress owners as RTS players so the player panel is
measured again (it reads 0.003 ms because it returns early).
