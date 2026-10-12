# net-audience progress (RoN rewrite 3: networking audience policy)

Branch `net-audience`, base origin/main d73acee. Task: design/ron_rewrite_plan.md rewrite 3 - one `Audience` helper,
every `PacketDistributor.ALL` send reviewed, fog leaks fixed first, resync when a unit comes into view, R2-10.
Write-up: design/ron_rewrite_plan.md "Rewrite 3"; design/code_review.md R2-09 / R2-10.

## Done
- `network/Audience.java`: policies OWNER / COMMANDERS / ALLIES / TEAM / SEEN / SPECTATORS / ALL as pure functions over
  `Facts` (`receives`, `recipients`, `knownOwners`), live facts (alliances, Hero roles, seats, allied control), and
  `PacketTarget`s for send sites (one custom distributor: the packet is encoded once, sent to each accepted player).
  Test hook `Audience.accountant`.
- `network/AudienceResync.java`: re-sends effects / kills / hero state when a unit comes into a player's knowledge,
  and stockpiles + cooldowns when what a player may know changes (defeat -> spectator, alliance, Hero role); also
  stockpiles on a player's first record (a match start syncs them before it forms alliances). Sends through `sink`.
- All 138 `PacketDistributor.ALL` sends go through `Audience` with a one-line reason (none left outside Audience).
  Fog leaks fixed: worker loads, hero mana / XP / ranks (R2-09), skill points / charges (team), cooldowns (team),
  research (owner), stockpiles (team), idle workers (owner, per player), unit paths (commanders), status effects,
  kills, wreck metal (per-player delta), sounds at a spot, BarFx battle effects (fog-gated per event), the unit batch
  sync's resources / worker / anchor / inventory fields, the join sync (anchors, loads), attack warnings, possession
  tags, alliance requests, fog-queued build sites, navmesh debug chunks.
- R2-10: CapturePointServerEvents.onJoin checks the scoreboard tag / entity type before getPersistentData().
- Fixes found on the way: remove-anchor packet sent SYNC_ANCHOR_POS (0,0,0) instead of REMOVE_ANCHOR_POS;
  hero / ability sync requests answered to everyone and about any id (now: the asker, if they may know);
  lock / unlock RTS sent N^2; custom building NBT re-sent to everyone at every login.
- `gametest/AudienceGameTests.java` (5 tests) - all green on CI (e4b16ce, 5e743db).
- Measured (CI notice `[Stress] [Audience 8v8]`): one round of the per-unit packet families at 8v8 with 2 Heroes and
  2 spectators (20 clients): 24,560 deliveries -> 15,211 (38% fewer).

## Next
- Nothing open in the lane; final CI on the last commit.

## Known
- tools/check_java.py reports 2 "constructor is ambiguous" errors (RtsDebugChunksClientboundPacket,
  UnitIdleWorkerClientBoundPacket): checker noise without the Minecraft classpath (FriendlyByteBuf unresolved);
  origin/main's RtsDebugChunksClientboundPacket gives the same error. CI compiles both.
- Client-side behaviour (HUD with narrowed data, resync on sight) is unverified headless beyond the GameTests.

## Proposals (outside this lane; details in ron_rewrite_plan.md "Rewrite 3")
- Fog: TrackedEntityMixin never removes a tracker when an entity goes into the fog (positions keep streaming), and
  trackers are only re-evaluated on section changes.
- Building ghosts: a per-building "known by" set so "destroyed" can go to SEEN + ghost holders instead of everyone.
- Hero-role HUD treats a Hero as an observer (enemy ability buttons / stockpiles now arrive empty for them).
- MultiplayerLobby.startMatch: form alliances before assigning stockpiles and market rates.
- KillFeedServerEvents.canSee could use Audience.mayKnowAt (adds the Hero side).
- PlayerServerEvents.syncUnits: the hero part could go to the joiner only.
