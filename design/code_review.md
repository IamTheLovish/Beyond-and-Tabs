# Code review: the Oct 11 merges and the 8v8 hot paths

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
| CR-06 | High | hero x fog/economy | Hero-role players are "not RTS players", so every observer rule applies to them: fog off, enemy economy, enemy stats | stats + economy FIXED, fog PROPOSAL |
| CR-02 | Medium | decommission x save | Decommission progress not saved; a reload leaves a half-dismantled, already-refunded building to repair and sell again | FIXED + test |
| CR-01 | Medium | decommission x batch commands | A decommissioning lab still accepts production (multi-building card, stale card) | FIXED + test |
| CR-03 | Medium | save/load | `DataType`s registered in lazily-initialised classes are dropped on a freshly launched game (the Repeat toggle) | FIXED |
| CR-04 | Medium | possession x camera toggle | Backspace with one enemy/neutral unit selected sends a doomed possess request and swallows the view toggle | FIXED |
| CR-05 | Medium | start phase x PacketAuth | Start-position clicks are not rate limited; each runs a spiral search and a server-wide broadcast | FIXED |
| CR-08 | Medium | perf, production | The production step asked the population check three times per item per tick (each walks every building's queue) | FIXED |
| CR-09 | Medium | perf, annexes | Annex counting was O(buildings x annexes) per lab per second | FIXED |
| CR-10 | Medium | perf, factions | `Factions.getFaction(Unit)` was three hash lookups, per unit in several per-unit passes | FIXED + test |
| CR-15 | Medium | perf, benchmark | The 8v8 benchmark sums per-pass costs of systems that run at very different rates | PROPOSAL + probe |
| CR-11 | Medium | perf, units | `getOwnerName()` reads synched entity data under a lock (1.20.1), and is called per unit in every pass | PROPOSAL |
| CR-14 | Low | perf, client HUD | Economy bar streamed every client unit every frame for the worker count | FIXED |
| CR-12 | Low | perf, capture sites | `Kind.values()` cloned an array per site per call (fog, tick, every production step) | FIXED |
| CR-16 | Low | pace x decommission | Decommission speed ignored the economy pace (construction, production and wreck reclaim follow it) | FIXED |
| CR-07 | Low | hero x keybinds | The Hero start message hard-codes "Backspace" (rebindable) | PROPOSAL |
| CR-13 | Low | maps x save | A world saved while a pre-made map rises / starts are placed reloads with no match and a half-raised map | PROPOSAL |
| CR-17 | Low | hero x reset | Client `heroRole` is only cleared on logout; after /rts-reset Backspace with nothing selected asks the server for a hero | PROPOSAL |
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

Last main CI ("[Stress]" notice, 400 units / 40 wrecks / 18 sites, average per pass):
queue lines 2.13 ms, bot enemy scans 1.05, momentum 0.83, player panel 0.80, formation 0.68, wreck reclaim 0.44,
unit grid 0.34, builder lookup 0.26, capture points 0.20 - total 6.72 ms of the 8 ms budget.

On this branch: 6.19 ms (first push) and 7.36 ms (second push, player panel alone 0.69 -> 1.87 ms with no change
to that code) - **run-to-run noise on the CI runner is about +-1 ms**, larger than most single fixes here. Read the
per-system figures over several runs before acting on one.

### CR-15 (Medium) What the benchmark measures
The budget sums **one pass of each system**, but they run at different rates: queue lines every 10 ticks, momentum
every 10, formation every 20, player panel every 40, capture points / reclaim / grid more often. Amortised per tick
the same numbers are roughly 1 ms, not 6.7 ms - and a per-tick system that doubles is invisible next to a 2 s one.
**Proposal:** log (and budget) `sum(cost_i / period_i)` per tick next to the per-pass sum. Also: `allUnits` during
the benchmark includes every parallel test's units, so per-unit costs are inflated by an unknown N.
**Probe added:** `RegressionGameTests.profile_queue_lines_components` logs a "[Stress] queue-lines profile" notice:
allUnits / slow-queue sizes and the split between payload building, owner-name reads, faction lookups, goal reads and
reclaim lookups, over the same units. Use it to pick the next cut.

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

### CR-11 (Medium, PROPOSAL) Owner name reads
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
