# Multiplayer: humans against humans

Several people in one match: 2 humans (up to 8), plus bots in any free seats, on an integrated server opened to LAN
(the e4mc mod turns that into a public internet address) or on a dedicated server. Everyone needs the same modpack.

## How to host (tonight's setup)

Easiest:

1. Title screen -> **Skirmish** -> **Host Online** (left column). This makes a fresh battlefield world, opens it to
   LAN with *Allow Cheats OFF*, and opens the **Multiplayer Lobby**.
2. With the **e4mc** mod installed, a chat line a few seconds later gives the public address, which ends in
   `.e4mc.link` (the exact wording depends on the e4mc version). Click it to copy it, then send it to your friend.
   No e4mc: only players on your own network can join, at your LAN IP and the port printed in chat.
3. Wait for your friend to show up in the lobby. Add AIs to free seats if you want them (`+AI T1`, `+AI T2`), set Map /
   Metal / Spawns, and press **Start Battle** once everyone shows *Ready*.

Or with a world you already have:

1. Load the world, press **Esc -> Open to LAN**. Leave **Allow Cheats: OFF**. If cheats are on, everyone who joins
   is an operator and can use `/rts-reset` and the cheat words. You, the owner, stay host either way. Press
   *Start LAN World*; e4mc prints the address in chat.
2. **Esc -> Multiplayer Lobby** (top-left button on the pause menu; in the RTS view it is a row in the match menu),
   or type `/rts-lobby`.
3. The world must not have a running match (the lobby refuses Start while there are RTS players). Use `/rts-reset`
   first, or host from the title screen for a fresh battlefield.


### One address that never changes (a fixed tunnel)

e4mc gives a new address every session, so friends need a new one each time. A fixed tunnel gives the host one
address to share once. This is an option, not a requirement; e4mc keeps working as before.

1. Host Online opens the world on port **25565** every session (client config `host_port`; 0 = any free port). If
   25565 is taken, chat says which port it used instead, and the tunnel won't reach that session.
2. Install the **playit.gg** agent (free), sign in once, and add a **Minecraft Java** tunnel to `127.0.0.1:25565`.
   playit shows the public address (a name like `something.joinmc.link`, sometimes with a `:port`).
3. Friends add that address once in **Multiplayer -> Add Server**. From then on: start playit, Host Online, and the
   saved server works.

Not yet checked end to end in a real session; please report what works. A friends list inside the mod (a Join
button, no addresses at all) would need a small online lookup service that someone runs: see the Lane C feedback
note (design/playtest_oct20_lane_c.md, item 35).
On a dedicated server, any op (permission level 2) is the host: `/rts-lobby` opens the lobby.

## How to join

1. Install the same modpack (same mod versions: our network channel checks that the versions match).
2. **Multiplayer -> Direct Connection**, paste the address from the host (e.g. `xxxx-xxxx.eu.e4mc.link`), **Join
   Server**.
3. The lobby opens by itself when you arrive (or Esc -> Multiplayer Lobby). Pick your faction (button), colour
   (click the swatch: next free colour; right-click: any colour) and team (`T1`..`T4`), then press **Ready**.
   **Spectate** takes you off the teams to watch.
4. When the host starts, your view switches to the RTS camera and your base appears two seconds later.

If you join after the match started, you are a **spectator**: RTS view, fog off, the observer HUD, and no base of your
own. If you are a player and lose your connection, rejoin with the same name and you get your side back.

## What happens on Start (`MultiplayerLobby.startMatch`)

- Every client gets START, closes the lobby and turns on the RTS view. 40 ticks later the server seats everyone.
- Colours: each colour someone asked for is kept if nobody else has it, and everyone else gets the first free palette
  colour. Each side's colour is different, and that matters because RoN's readied start allies sides that share a
  colour.
- Start positions: all playing seats sit evenly around one ring centred on the host (in a fresh battlefield that is
  the world spawn). Teammates sit next to each other, like BAR's team sides. The ring's radius follows *Spawns*
  (close 170 / normal 220 / far 360 blocks) and grows so that neighbouring sides are always at least 150 blocks
  apart (`ringRadius`). Each spot is moved onto the nearest dry, fairly flat ground (`groundSite`, the same test the
  bot spawn search uses). Every side is the same distance from the centre and from its neighbours.
- Humans: `PlayerServerEvents.startRTS` with a readied start places the capitol foundations, the commander and
  workers, the starting resources, the fog, and a camera teleport to the base. Bots: `BotServerEvents.addBotAt` on
  their ring spot.
- Teams are alliances. An alliance left over from an earlier match between two people now on different teams is
  dissolved.
- The battlefield (cleared bases, lanes, metal patches, boundary wall) forms once, a few seconds after the last base
  (`BattlefieldSetup`), using the lobby's Map and Metal settings.
- A `MultiplayerMatch` records who is in the match. When a participant is defeated, victory is decided among *that
  match's* remaining participants (`match.MatchOutcome.winnersAmong`). The live match is held by `match.MatchState`
  and saved with the world (rewrite 5): after a reload or a server restart victory is still scoped to it, joiners
  who weren't seated still watch, and the teams' alliances come back (they were not saved before). The rest is RoN's existing code: per-player
  victory and defeat titles, the results popup for everyone (`MatchStatsClientboundPacket`), match history per side,
  and the team resign vote. A beaten player is no longer an RTS player, so the fog lifts for them and they watch the
  rest of the match.

## Singleplayer assumptions found

| Where | Problem | Fix |
|---|---|---|
| `SkirmishServerboundPacket` | Any connected player could send the singleplayer lobby's start, which spawns bots and sets the arena for everyone | Host only, and refused while a lobby match is live |
| Skirmish lobby | Only for one human: the "Multiplayer" button was disabled | `MultiplayerLobby` / `MultiplayerLobbyScreen`, "Host Online" |
| `PlayerServerEvents.defeat` | Victory was checked against every RTS player on the server | Scoped to the lobby match when there is one (same result on a real server, correct in tests) |
| `PlayerServerEvents.startRTS` | Anyone joining mid-match could start their own base inside the match | Refused for non-participants of a live lobby match; they get the spectator view instead |
| `MatchMenuScreen.isPauseScreen` | Returned true from Escape even in a shared world | False once the world is open to LAN or on a server. Vanilla doesn't pause a published server either way. The lobby screen never pauses |
| Login sync (`syncUnits`) | Each login broadcast 4 packets per unit to every player, and ran on a pool thread | Sent to the joining player only, back on the server thread |
| `UnitSyncServerboundPacket` | A client asks for ability cooldowns for every unit that comes into view; each answer went to everyone, after a walk over all units | Answer goes to the asker only; direct id lookup |
| Economy sync | Every side's economy went to every client 4 times a second, which also showed the enemy's economy | Sent to the owner, allies, and observers |
| Beacon owner ticks | One packet per side per second to every client, even with no beacon | Only when the count changes, plus a refresh every 10 s |
| Unit animations | One broadcast per ranged shot, to everyone | Only to players tracking that unit |

Checked and already fine: per-player victory and defeat handling on the client (`PlayerClientEvents.defeat/victory`
compare names), the results screen, `MatchHistory` (sampled for every side), the resign vote (per team), bots (no
assumption of one human), and ResourceCost sync to remote clients (`ConfigVanillaServerEvents`). Server code that
touches `Minecraft.getInstance()` only does so in client-side send helpers inside packet classes, which a dedicated
server never calls.

## Network safety (`network/PacketAuth`)

Every serverbound packet now authorises against the sender, never against a name or id the client wrote into it:

- **Unit orders** (`UnitActionServerboundPacket.accept`): the claimed owner must be the sender (or an ally who gave
  allied control, or the sender is in sandbox). The unit ids are cut down to units the sender may order. Before,
  the sandbox exception tested the claimed owner, not the sender.
- **Area commands**: already scoped to the sender. Now rate-limited.
- **Buildings**: a new building is checked against the claimed owner, an existing building against its *real* owner.
  The attack-rally variants were missing from the check. Builder ids are filtered to the sender's units, so nobody
  can put the host's workers on their own foundation. DESTROY refunds the real owner. An empty rally-entity id list
  no longer crashes the handler.
- **Production, repeat, cancel**: the owner, an ally with control, or a sandbox player. Before, everyone was let
  through whenever *anyone* was in sandbox, and the ally check asked about `""`.
- **Abilities, hero rank-ups, building abilities, items**: owner checks (the item check had the same claimed-owner
  sandbox hole).
- **Sandbox, scenario editing, custom buildings**: the *sender* must be a sandbox player. Before it was "anyone is", so
  one sandbox player let everyone give themselves the host's units with SET_UNIT_OWNER. Starting sandbox
  (`START_RTS_SANDBOX`) is host or op only.
- **Player actions**: the action is always about the sender (a `playerId` of -1 used to slip through). Lock/unlock,
  syncing, scenario publishing and sandbox are host or op only.
- **Start positions** (RoN map lobby): you reserve, free and ready only your own slot.
- **Game mode, survival waves**: host or op only. **Tutorial scripted spawns**: only in the tutorial world, host only.
- **Map drawing, map markers, hover info, resign vote**: drawing already had a limit, now markers do too. Hover
  answers only what the asker can see and is rate-limited. The resign vote is per sender.
- **Lobby**: you edit only your own seat; bots, settings and Start belong to the host.

Rate limits (token buckets per player, dropped silently): unit orders 200/s with a burst of 600 (a formation drag
sends one packet per unit), building packets 200/s, production 200/s (a login re-requests every building's queue),
ability-sync requests 400/s with a burst of 1000 (one per unit entering view), hero sync 50/s, player actions 10/s
(teleport exempt: it follows a held portrait every tick), area commands 10/s, map markers 3/s, start-pos 10/s,
lobby 10/s.

## Bandwidth (2-player match)

These are estimates from reading the send code, not measurements. The assumed match is 2 humans and 2 bots in the mid
game: about 150 units, 30 buildings, one fight going on. Rates are packets per second received by **one** client.

| Stream | Cadence | Before | After |
|---|---|---|---|
| Unit batch sync (delta, fog-gated) | 1 per player per s | 1/s, 2-5 KB/s | same |
| Idle workers | 1 per s | 1/s | same |
| Economy | 4/s per side | 16/s (4 sides) | 4/s (own; 8/s with an ally) |
| Beacon owner ticks | 1/s per side | 4/s | ~0 (0.4/s refresh) |
| Game clock | every 10 s | 0.1/s | same |
| Player panel | every 2 s | 0.5/s | same |
| Fog chunks | changed chunks, every 0.5 s | 0-2/s, bursts when the camera moves | same |
| Kill feed | <= every 5 ticks, only with kills | <= 4/s | same |
| Wreck metal | <= 1/s, only on change | <= 1/s | same |
| Unit kill counts | <= 1/s | <= 1/s | same |
| Capture points | on change, refresh every 5 s | <= 1/s | same |
| Bar FX (nano beams, muzzle flashes) | <= 1 per tick, near you | <= 20/s in a fight | same |
| Unit animations | per ranged shot | ~50-100/s in a big fight, every client | only clients tracking the shooter |
| Ability-sync answers | per unit entering anyone's view | every pan by anyone: up to N/s to all | to the asker only |
| Login sync | on each login | 4 x units to everyone (600 for 150 units) | to the joiner only |
| Hover info | <= 5/s while hovering | to the hoverer | same |

The mod's own traffic now stays around 30-40 packets/s per client in a fight, a few KB/s. The largest stream is
vanilla entity movement: each tracked, moving unit is sent every 3 ticks, so about 100 moving units means about 650
small packets/s, roughly 15 KB/s. That is still far below what the e4mc relay handles (about 0.3 Mbit/s per client
in total). At 8v8 scale with hundreds of units the vanilla movement stream grows linearly with the number of moving
units in view. That is the next thing to look at if a big match stutters (raise the tracking interval of unit entity
types, or cull movement packets for fogged units).

Nothing here was measured on a real connection yet. A two-instance test with `/rts-debug` (or a packet logger) would
put real numbers in this table.

## Players without the mod (vanilla clients)

lovish wanted friends without the mod to be able to join, as a normal player or as a spectator. **This is not
feasible cheaply, and no code was changed for it. Modded play is untouched.** The findings:

1. **The handshake refuses them.** Forge 1.20.1 lets a vanilla client connect only if every registered network
   channel accepts a missing ("absent") version on the other side. Ours (`reignofnether:main`) requires an exact
   version match on both sides (`PROTOCOL_VERSION::equals`), and so do the channels of almost every content mod
   (Alex's Mobs and Citadel are hard dependencies in our `mods.toml`). Forge turns the vanilla client away at login
   with "This server has mods that require Forge to be installed on the client". `mods.toml` sets no `displayTest`,
   so the default applies. That setting only changes the server-list compatibility icon, not who may join.
2. **Even if the channel were made optional, the game would break for them right away.** We register about 131
   entity types (every unit), about 139 items, 47 blocks, 7 particle types and 64 sounds, and Alex's Mobs and Citadel
   add hundreds more. A vanilla client has none of these ids. The first unit it sees arrives with an unknown entity
   type id (the client falls back to a pig) followed by entity data the pig doesn't have, and the client disconnects
   with a decoding error. Chunks containing our blocks, and our particles, fail the same way.
3. **Forge has no server-side translation layer** like Fabric's Polymer, which presents modded content to vanilla
   clients as vanilla look-alikes. Building one means intercepting every chunk, entity, particle, sound and item
   packet per player. That is a large project. Even then a vanilla client would get none of the RTS: no RTS camera,
   no HUD, no fog rendering, no unit models. All of that is client-side mod code.

Options, cheapest first:

- **Install the pack.** Import the modpack zip into Prism Launcher or CurseForge, about 5 minutes. This is the
  supported path.
- **Watch without the mod**: the host streams the match (Discord screen share or OBS). No install, full RTS view.
- **A smaller spectator pack** (only the mods needed to see the match) is still a modded client. It is not a
  vanilla join, and it would need the same mod versions.
- A server-side vanilla-compatibility layer (the Polymer approach) for Forge. Weeks of work, fragile with every
  content mod, and it still couldn't show the RTS view. Not recommended.

## Known limits

- **Not tested over a real connection yet.** CI's game test covers the server side (two fake players, seats, capitols,
  resources, command rejection, victory). The lobby screen and the e4mc path have only been checked by reading the
  code.
- The lobby centres the ring on the host. Hosting from a world where the host stands near an ocean or a mountain can
  put a base on poor ground (`groundSite` tries 13 spots around each ring point, then gives up and uses the point).
  A fresh battlefield (Host Online) avoids this.
- Up to 16 seats (8 humans), 4 teams of up to 8. More humans than seats join as spectators.
- One lobby per server. A rematch needs `/rts-reset` (or a fresh world from Host Online), then reopen the lobby.
- If the host leaves, the lobby closes. On an integrated server the host leaving ends the server anyway.
- Joined players are refused the classic "start my own base" mid-match only while a lobby match is live. Outside it
  RoN's normal free-join rules apply (`/rts-lock` still locks).
- The game mode selector, survival waves and RoN's start-position map settings are host- or op-only now. On a
  dedicated server where non-ops used to change them, an op has to.
- Vanilla clients cannot join (see above).
