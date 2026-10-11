# HUD

How the Beyond and Tabs HUD maps BAR's interface onto Reign of Nether's. Regions (see `HudClientEvents`):
the economy bar top-centre, control groups top-left, the player panel top-right, one alert column under the
economy bar (danger banner, then toasts, then the kill feed), the command panel bottom-left (orders grid, portrait,
stats, selection strip), the minimap bottom-right. Everything is 1x font and `fill()` so GUI scale 2 and 3 both read.

## Selection summary (BAR's selection grid)

With two or more units selected the selection strip shows **one icon per unit type**, not one per unit:

- a `xN` count badge (bottom-left of the icon) and a thin **group health bar** over it (the type's summed HP);
- a totals line above the grid: unit count, a summed HP bar with numbers, summed metal and energy cost, and the
  group's kills (`✕ 7`);
- **click** a type to sub-select it (BAR), **Ctrl-click** to drop that type from the selection, **Shift-click** to put
  that type's command card up without changing the selection; more types than slots collapse into a `+N` tile with a
  tooltip.

A single selected unit keeps RoN's portrait + the compact unit card; the card's cost line also carries the unit's
kills once it has one. Code: `HudClientEvents` (grid), `UnitInfoCard.renderGroupSummary / renderGroupBar`.

### Kill counter

`UnitKills`: a per-unit int in the entity's persistent data on both sides (no unit-class field), bumped on the
server for every enemy unit a unit kills, flushed to all clients at most once a second in one packet
(`UnitKillsClientboundPacket`, changed ids only), sent whole to a joining player, and written into the unit's save
data. Veterancy keeps experience (a metal ratio), so it does not double as a kill count.

## Command-card state toggles

BAR's cycling state buttons, lit while active (`Button.isActive`: amber glow + bright edge, distinct from the
transient pressed frame):

- **Fire state** (`StanceButton`, `EngagementStance`): Hold fire → Return fire → Fire at will (default). Left click
  forward, right click back, three pips under the icon mark the state, the frame lights when not Fire at will.
  Applies to every selected non-worker fighter. Server side: Hold fire never picks a target on its own (no idle aggro,
  no retaliation, no attack-move acquisition, no retargeting, guards don't engage); Return fire only answers whatever
  hits it; Fire at will is RoN's usual behaviour. An explicit attack order (forced target) always works. Setting Hold
  fire drops a self-picked target. Stored in persistent data and the unit's save data; the SET_STANCE_* unit actions
  run on both sides and never reset behaviours, clear patrol/guard, or touch the shift-queue.
- **Hold position** and the production **Repeat** toggle use the same lit frame.

## Reclaim labels

With a worker (or the commander) selected, hovering a wreck floats a green `⟲ 120 metal` over it; while an area
reclaim circle is dragged (Ctrl + right-drag) the circle's total metal hangs over its centre. Wreck metal lives
server-side on the block-display entity, so `WreckSyncServerEvents` sends the changed heaps' whole-metal values once a
second (`WreckSyncClientboundPacket`), the full list to a joining player; `WreckClientEvents` keeps the id → metal map,
prunes gone entities once a second, and matches the hovered block to the nearest wreck within 1.5 blocks.

## Kill feed

`KillFeedServerEvents` queues a line for every unit killed by an enemy unit and every building an enemy unit brings
down (not cancels or defeat clean-up), merges duplicates within a flush (`x3`), and every 5 ticks sends each player
only the lines it may know about: it owns or is allied with either side, or the victim fell inside its vision
(spectators see everything). `KillFeedClientEvents` shows up to 5 lines, newest on top, right-aligned in the alert
column under the toasts, each name in its owner's colour, building kills in bold, promoted from a short queue at most
one per 5 ticks, fading after 7 s. Names travel as translation keys.

## LOS shading

Already provided by the fog of war: terrain outside any friendly sight radius is tinted (`FOG_TINT_RGB`) per column
through the block-colour hook (`FogTintingBlockColor`, server-authoritative edge masks), baked into chunk meshes, so
it costs nothing per frame. No second layer was added - it would double-tint the same blocks.

## Match menu and resign vote

Escape on the RTS view opens **`MatchMenuScreen`** instead of the vanilla Game Menu (`MatchMenuClientEvents` swaps
the `PauseScreen` as it opens, so every path that would have shown Advancements / Statistics / Give Feedback lands
on ours; outside the RTS view Escape is vanilla). Escape with a building on the cursor drops the placement instead.
One dark panel with the match clock, your faction and a stripe in your player colour: **Resume**, **Resign** (two
clicks - the second confirms), **Spectate** once you are out of the match, vanilla **Options**, **Mod Settings**
(`ReignOfNetherConfigScreen`: alerts, announcer voice), **Open to LAN** (kept for a friend to join), **Save & Quit
to Title**. It pauses a single-player world like the vanilla menu.

**Resign** sends one `RESIGN_VOTE` and the server decides (`ResignVoteServerEvents`): no living human ally means the
existing surrender path at once (defeat title, then the results popup with its Spectate button); otherwise a BAR
team vote - the living *human* members of the caller's team (bots never vote) have 30 s to answer with **F6 yes /
F7 no** (`ResignVoteClientEvents`; the F7 debug overlay waits while a vote is open), the caller counts as a yes, a
strict majority passes it and every human on the team is defeated (bots on the team fight on), enough no votes or
the clock failing it. The tally (`Vote`) is pure and game-tested. Each member sees a persistent banner at the top
of the alert column ("X called a vote to resign (1/2) - F6 yes / F7 no  24s", toasts and kill feed shift under it)
plus a chat line per event, synced through `PlayerClientboundPacket RESIGN_VOTE_SYNC` once a second and on every
answer, only to the team.

## Player list eco bars

Allied lines on the top-right player panel (`PlayerPanelClientEvents`, F4) carry BAR's economy readout: **two thin
bars**, metal (grey) and energy (yellow), filled to **stored / storage** (white when full), each with the income
figure beside it, then the commander health bar. Enemy lines stay name + alive/defeated. Hovering an allied line
gives the exact `Metal 350 / 1000 (+12.3/s)` figures as a tooltip. `PlayerPanelServerEvents` adds the four stored /
storage floats to the 2 s roster packet, still only on the recipient's own and allied entries, so an enemy's pool
never reaches the wire. Rows stay 11 px so an 8v8 roster fits.

## Build-bar icons

Every building icon is checked twice: `tools/check_building_icons.py` (CI safety step, no Minecraft) and the
`every_building_and_production_item_icon_ships` GameTest. Both fail on a missing mod texture, a non-texture vanilla
path, a `waxed_` block, or any of the **placeholder block faces** the faction labs / towers / capitols used to ship
with (cobblestone for the Castle, chiseled nether bricks for the Fortress...). `tools/gen_hud_icons.py` draws a
16x16 silhouette for each of those in its faction's palette (labs, T3 labs, towers and capitols of the Kingdom,
Gravebound and Horde, plus the Jade Pagoda); the shared vault / extractor / converter / wind generator icons are
there too. Re-running the script is deterministic.
