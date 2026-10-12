# net-audience progress (RoN rewrite 3: networking audience policy)

Branch `net-audience`, base origin/main d73acee. Task: design/ron_rewrite_plan.md rewrite 3 - one `Audience` helper,
every `PacketDistributor.ALL` send reviewed, fog leaks fixed first, resync when a unit comes into view, R2-10.

## Done
- `network/Audience.java`: policies OWNER / COMMANDERS / ALLIES / TEAM / SEEN / SPECTATORS / ALL as pure functions over
  `Facts` (`receives`, `recipients`, `knownOwners`), live facts (alliances, Hero roles, seats, allied control), and
  `PacketTarget`s for send sites (one custom distributor: the packet is encoded once, sent to each accepted player).
  Test hook `Audience.accountant`.
- `network/AudienceResync.java`: re-sends effects / kills / hero state when a unit comes into a player's knowledge,
  and stockpiles + cooldowns when what a player may know changes (defeat -> spectator, alliance, Hero role).
- All 138 `PacketDistributor.ALL` sends now go through `Audience` with a one-line reason (grep: none left outside
  Audience). Fog leaks fixed: worker loads, hero mana / XP / ranks (R2-09), skill points / charges (team), cooldowns
  (team), research (owner), stockpiles (team), idle workers (owner, per player), unit paths (commanders), status
  effects, kills, wreck metal, sounds at a spot, BarFx battle effects (fog-gated per event), the unit batch sync's
  resources / worker / anchor / inventory fields (were sent to everyone), attack warnings, possession tags,
  alliance requests, fog-queued build sites, navmesh debug chunks.
- R2-10: CapturePointServerEvents.onJoin checks the scoreboard tag / entity type before getPersistentData().
- Fixes found on the way: remove-anchor packet sent SYNC_ANCHOR_POS (0,0,0) instead of REMOVE_ANCHOR_POS;
  hero / ability sync requests answered to everyone (now: the asker, if they may know); lock / unlock RTS sent N^2.
- `gametest/AudienceGameTests.java`: recipients per policy (owner, ally, enemy, spectator, Hero of each side),
  live facts, resync contents, R2-10, 8v8 delivery measurement (`[Stress] [Audience 8v8]` CI notice).

## Next
- CI green; read the `[Audience 8v8]` notice and write the numbers into ron_rewrite_plan.md (Rewrite 3).
- design docs: Rewrite 3 section, code_review R2-09 / R2-10 status.

## Known
- tools/check_java.py reports 2 "constructor is ambiguous" errors (RtsDebugChunksClientboundPacket,
  UnitIdleWorkerClientBoundPacket): checker noise without the Minecraft classpath (FriendlyByteBuf unresolved);
  origin/main's RtsDebugChunksClientboundPacket gives the same error.

## Proposals (outside this lane)
- see the Rewrite 3 section in design/ron_rewrite_plan.md
