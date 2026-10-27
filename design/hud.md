# HUD

How the Beyond and Tabs HUD maps BAR's interface onto Reign of Nether's. Regions (see `HudClientEvents`):
the economy bar top-centre, control groups top-left, the player panel top-right, one alert column under the
economy bar (danger banner, then toasts, then the kill feed), the command panel bottom-left (orders grid, portrait,
stats, unit card or selection strip), the minimap bottom-right with the idle-worker cycler on its corner, the helper
column (chat, army, idle labs, attack warning) on the right edge between the panel and the map. Everything is 1x
font and `fill()` so GUI scale 2 and 3 both read.

## Layout contract (`HudLayout`, `tools/hud_layout.py`)

lovish plays at GUI scale 2 or 3 on a 1080p screen: at scale 3 the logical screen is 640x360, and in his clip the
selection strip ran under the minimap and the attack warning sat in the player panel. The rectangle maths now lives
in `HudLayout` (no Minecraft in it) and `tools/hud_layout.py` mirrors it function for function, asserting that no two
regions overlap at scale 2, 3 and 4 on 1920x1080 and 2560x1440 in the worst case (ten control groups, a production
queue, stall warning, danger banner, resign vote, three toasts, five kill lines, a 16-player roster). Run it after
touching any HUD position; change a constant in one, change it in the other. The rules it encodes:

- the command panel's right edge stops `MAP_GAP` short of the minimap backing (diamond) or the square map's button
  grid: the selection strip gets as many columns as fit, 3..8 (RoN sized it off the width alone), and the single
  unit's card narrows to what is left, or is dropped under 70 px;
- the player panel clips its rows above the map (`+N more`), the helper column tightens its 30 px step to 24 when it
  would reach the map, and the alert column drops under the production queue when a narrow window puts it there;
- the kill feed never draws a line across the command panel (`HudClientEvents.bottomPanelTop`);
- hover cards (a hovered unit, a hovered building, a hovered strip tile) are transient and may sit over the HUD like a
  tooltip, but never on the minimap. Documented limits: the large (M) map and the square map have no room at 480x270;
- the controls cheat-sheet (nothing selected) is a hard region in the command panel's corner, drawn only where it
  clears the map (`HudLayout.CHEAT_SHEET_*`).

## Human-POV audit and declutter

lovish (BAR veteran, 1920x1080 at GUI scale 2-3, 60% keyboard: no F-row without Fn, no arrows): "there's a lot of
things that pop on the screen that take up a lot of space, like the AI and player teams - being able to minimise
things to see the game in a more effective way." References: ZeroSpace's top-left "INCOME [I]" panel (a HIDE toggle,
one compact table with tabs) and per-player observer bar; BAR's compact right-side Allies / Enemies / Spectators list
that collapses. Adapted, not copied: BAR stays the reference.

### Audit (screen share at 1920x1080; `tools/hud_layout.py` sizes, default map r=60)

Scale 2 is a 960x540 logical screen, scale 3 is 640x360. **Always** = the player needs it every few seconds;
**on-demand** = shown when asked for or when it has something to say; **never** = removed.

| Element | Size (px) | Scale 2 | Scale 3 | Class |
|---|---|---|---|---|
| Economy bar (metal / pop / energy) | 478x35 / 376x35 | 3.2% | 5.7% | always (compact 23 px: 2.1% / 3.8%) |
| Stall / waste / overflow rows under it | 200x12 each | 0.5% | 1.0% | on-demand (only while true) |
| Minimap (diamond backing) | 132x132 | 3.4% | 7.6% | always; large map (M) 12.2% / 27.6% on-demand |
| Command card, one unit (orders, portrait, stats, unit card) | 380x102 | 7.5% | 16.8% | on-demand (selection); compact unit card 15 px tall |
| Command card, multi-selection strip | 447x102 | 8.8% | 19.8% | on-demand (selection) |
| Control groups row | up to 220x22 | 0.9% | 2.1% | always while groups exist (hidden in clean view) |
| Production queue | 120x54 | 1.2% | 2.8% | on-demand (only while producing) |
| **Player / AI panel (old, always on)** | 206x216 / 206x172 | **8.6%** | **15.4%** | **was always - now on-demand** (stats table, I) |
| Stats strip (new default) | 160x11 | 0.3% | 0.8% | always |
| Alert column: danger banner, resign vote, 3 toasts, 5 kill lines | 449x130 / 146x130 | 11.3% | 8.2% | on-demand, transient (toasts 3 s, kills 7 s) |
| Kill feed | 5 lines x 12 px | | | on-demand: 1-line ticker by default |
| Controls cheat-sheet (nothing selected) | 180x96 | 3.3% | 7.5% | **was always - now on-demand** ("?" 12x12 button) |
| Hover cards (unit, building, strip type) | 128-136 x 50-113 | 1.3-2.8% | 3.0-6.3% | on-demand, transient (tooltip) |
| Right-edge helpers (help/diplomacy, chat, army, idle labs, attack warning), beacon | 22x22 each | 0.1% each | 0.2% each | always (hidden in clean view) |
| Idle-worker cycler | 22x22 | 0.1% | 0.2% | on-demand (idle workers exist) |
| Square-map button grid | 134x78 | | | always with the square map (clean view: size toggle only) |
| Spectator observer bar (new, 8v8) | 672x50 / 496x50 | 6.5% | 10.8% | on-demand (spectating, nothing selected) |
| Game clock above the map | 16x16 | | | always (part of the minimap cluster) |

Removed outright (**never**): nothing a player uses was deleted; the old panel's always-on eco bars moved into the
Economy tab, the "+N more (F4 hides)" hint is now "+N more".

### What changed

- **Stats table** (`PlayerPanelClientEvents`): the always-on panel is a slim strip by default - your colour,
  "Allies n · Enemies m [I]" and a 2 px pip per allied commander coloured by health (every commander for a
  spectator). **I** (`Keybindings.statsTable`, rebindable; **F4** stays as a fixed alias) or a click on the strip opens
  a 206 px table in the same corner with four tabs: **Players** (Allies / Enemies / Spectators sections - a spectator
  sees one section per team - alive / defeated and commander HP where sent), **Economy** (BAR's two thin eco bars and
  income; allies only, enemies only when spectating or defeated - the server never sends an enemy's economy to a
  player still in the match), **Army** (army value = metal in non-worker units, units, kills, losses; same gate) and
  **Production** (the head of every own / allied lab's queue with its percentage). Open / closed and the tab survive
  restarts (`hud_stats_open`, `hud_stats_tab`). I is ability slot 8 too: a selected card with a button on I keeps it
  (`HudClientEvents.isCardKey`, own conflict context `HUD_PANEL` so Controls shows no red); F4 always works.
  Multiplayer: every client gets its own roster copy from `PlayerPanelServerEvents` every 2 s, spectators (connected
  players not in the match, e.g. a `MultiplayerLobby` mid-match joiner) are listed - no host-only state.
- **Roster packet** (`PlayerPanelClientboundPacket`): each line now carries its relation to the recipient (self /
  ally / spectator), a team index (allied players share one, `PlayerPanelServerEvents.teamsOf`), and behind the
  existing ally / observer gate army value, units, kills, losses (`KillFeedServerEvents` tally, cleared when the
  match ends), tech tier and faction powers off cooldown (the set `BotPlayer.useFactionPowers` casts). Game-tested:
  `stats_table_army_figures_follow_the_ally_gate`.
- **Chevrons** (`HudPanels`): every non-essential panel folds with a 9 px chevron in its corner, state remembered in
  the client config: kill feed -> one-line ticker (default), alerts -> coloured icons with count badges (text on
  hover), cheat-sheet -> "?" button (default), minimap -> small / large (M, now remembered), unit card -> one line
  (role + veterancy), economy bar -> one 22 px row (amount / storage, net, a 2 px fill line), command card -> no
  backing and no group totals line. Zones are a fixed array rebuilt each frame (no allocation) and count as HUD for
  the cursor, so a chevron click never box-selects. Mod Settings > **HUD...** (`HudSettingsScreen`) sets the same
  state plus toast seconds and strategic-plate opacity.
- **Clean view** (`Keybindings.cleanView`, default **=**: on a 60% board without Fn, unbound by vanilla and every
  card; backslash is Overcharge): only the economy bar, the minimap (with its size toggle) and the command card stay.
  Control-group and helper hotkeys keep working; the buttons are just not drawn. Not remembered (a glance key).
- **F1 / hideGui**: RoN's F1 (`HudClientEvents.enabled`) and vanilla's `options.hideGui` are one switch
  (`HudPanels.onF1`, `hidden()`); every RTS screen overlay - HUD, stats table, observer bar, toasts, kill feed,
  resign banner, minimap, clock - checks `HudPanels.hidden()`. Not hidden: world-space health bars and the
  strategic view's plates (they are the strategic view, not an overlay).
- **Observer bar** (`ObserverBarClientEvents`): while spectating (defeated, a bots-only Watch Battle, a mid-match
  joiner) a bottom-left strip with one 52-84 px cell per player - name in their colour, tier, metal income, army
  value, units, faction powers ready - one row per team, wrapped when a team has more players than fit, always short
  of the minimap. It gives way to the command panel when the spectator selects something; hover for exact numbers;
  `hud_observer_bar` turns it off.
- **Auto-tidy** (`NotificationClientEvents`): at most three toasts; the same line again bumps "x2" on the one already
  up instead of stacking; they fade after `hud_toast_seconds` (1-10, default 3). Strategic-plate fill opacity
  `hud_plate_opacity` (10-100%, `StrategicViewClientEvents.plateAlpha`). Nothing permanent sits in the playfield
  centre (below).
- **Idle-worker cycler** moved to the minimap backing's **bottom-left** corner (it was top-left): at scale 3 the
  top-left corner reached into the centre ellipse. The alert column also stops short of the large map's corner
  (`HudLayout.alertColumnMapLimit`) when the map's top comes within 100 px of the bar.

### Clearance

`tools/hud_layout.py` encodes three more scene families - **collapsed** (the defaults), **clean** (clean view) and
**spectator** (8v8 observer bar) - each with the full and the compact economy bar and with no / single / multi
selection, on every screen and scale the existing scenes cover, checking that no two regions overlap. With nothing
selected at 1920x1080 scale 2 and 3 and the default map, no **permanent** element may enter the ellipse inscribed in
the central 70% x 70% of the screen (the box's corners are where every RTS keeps its map, card and bar; at scale 3 the
102 px command card alone covers the bottom 28%, so a rectangle could never be clear with anything selected).
Transient elements are exempt: toasts, the kill ticker (it sits under the bar's stall row, ~y 50-62 at scale 3), the
danger banner, hover cards and the opened stats table. The existing 76 scenes still pass with the open table
modelled as the worst case (16 players + 2 section headers + its tab row).

## Command-card badges

Every corner label on a tile is a `Button.renderBadge`: 0.75x text on a dark backing hugging the frame edge, never
past the tile's centre, never outside the frame - the hotkey top-right, ability charges bottom-right, the strip's
`xN` count bottom-left, a trade rate top-left. RoN drew the hotkey 1x over the icon's bottom-right, which on 22 px
tiles at scale 2 read as "Q", "Z", "E A" clipping each other and the icons (lovish's clip). A T3 lab's card (Fortress,
Castle...) shows its research tiles plus the faction's experimental as the production grid; the
`every_t3_lab_keeps_a_unit_production_with_distinct_hotkeys` GameTest keeps every T3 lab with at least one unit
production item and distinct hotkeys, so the grid never empties once research is done.

## Command card: BAR order keys, Attack, line formations

lovish's clip 5: "I can't use formation when using attack command, which I don't even see an attack command" - the
orders row read X (sword), B, G, N, Y, P and a cut-off "Spc", seven tiles in a row the portrait starts 6.5 tiles in.
The card is now laid out from `CommandCard` (side-neutral, shared with the hotkey GameTest) in rows of 6 from the
bottom: **Attack (X)**, **Move (M)**, **Stop (S)**, **Hold (H)** for fighters or **Build (B)** for workers,
**Patrol (P)**, **Guard (G)**; above them the fire state (click), the gather target (click; it lost G to Guard) and
**Garrison (N)**. Ability and build-menu rows start above the order rows.

- **S is also WASD panning** (and so is any order a player puts on W/A/S/D). `KeyTaps` times the key in real
  wall-clock time: a press shorter than 180 ms fires the card's order on that key, a press the camera panned for fires
  nothing, and the camera waits out the tap window before an order key pans (`OrthoviewClientEvents.panKeyDown`), so
  tapping S doesn't nudge the view. Key events reach the game once per frame, so in lag a tap is only seen when its
  frame ends: the verdict forgives the frame the release landed in (up to 150 ms; `KeyTapTracker`, tested on scripted
  frame times) and never calls a press a tap once the camera has moved for it. Oct 21 playtest: at 30-50 ms frames with
  spikes, a 120 ms tap measured over 180 ms, the camera had not moved yet and no order fired.
- **Attack is X** (A is pan-left), but only the DEFAULT moved in #39: Minecraft saves every key in options.txt, so
  lovish's card kept reading "A". `KeybindMigration` moves a saved binding still on an old default to the new one once
  (client config `keybind_layout`, toast "Controls updated"); a key the player chose is never touched. Moving a default
  later = bump `LAYOUT_VERSION` and add the old key to `KeybindMigration.STALE`. Controls marks a card key a player put
  on a pan key red (`KeyConflicts`); Stop on S, as shipped, is not flagged.
- **Space** looks at the last alert (`Keybindings.lastAlert`), so Stop lost its Space default.
- **Card keys win**: a key used by the selected card's own buttons (`HudClientEvents.cardButtons`: orders, abilities,
  build menu, production) never also fires a HUD-wide button - the minimap toggle on M, the idle-worker cycler on
  ".", the last-alert jump on Space. This generalises the old idle-worker rule.
- Duplicates fixed: Patrol moved Y -> P (the faction labs sit on Y, the Geomancer's Lotus Academy among them), Guard
  P -> G; the Library and the Laboratory left P (Library to `[` = hotkey11, Laboratory to `,`); the Stormcallers'
  storage moved `.` -> H because the Geomancer's Ley Link is on `.`; the T2 builders' powers left Q, their capitol's
  build slot (Embalmer's Soul Wisps and Bonewright's Totem to V, Elder Druid's Awaken Thicket to E, Royal
  Architect's Holy Bell from the default Z - the Oak Stockpile - to `'` = hotkey12). Two ability pairs share a key on
  purpose because they never show together (Militia bow/sword swap, Ravager vexes/ground attack by rider). The GameTest
  `no_two_buttons_on_a_command_card_share_a_hotkey` checks every unit type's orders + abilities + (workers) the
  faction build menu, aliases included. Not covered: a commander's runtime abilities (D-gun, signature) still take
  default slots and can share Z/E/R with the Kingdom's, Gravebound's and Horde's build menus.
- **Attack** is lit while armed. Armed, a left click on a unit/building attacks it and on the ground attack-moves;
  a **left- or right-drag lays a line formation** exactly like a move drag (`FormationDragMove`, same slots and
  nearest-unit assignment) and every slot gets **ATTACK_MOVE**; the preview line is red instead of green. **Shift**
  keeps Attack (and Move) armed and queues each slot's order. No box selection starts under a command press.
  Game-tested: `attack_move_line_gives_each_unit_its_own_slot`.

## Alerts: Space to look

`AttackWarningClientEvents` keeps the last place worth looking at - an off-screen hit on our stuff, any hit on our
commander, an ally's commander - for a minute; **Space** centres the camera on it (unless the card uses Space, e.g.
the item shop). Alert lines with a place ("Base under attack", "Units under attack", "Commander under attack",
"Ally needs help", the danger banner) end in " - Space to look" (the key's current name) while there is one.

## Nothing selected: cheat-sheet and building hover card

From the reference clip of another Minecraft RTS mod: with nothing selected the unit-info corner shows a small,
localised **controls cheat-sheet** (Left-drag select, Right-click move/attack, Right-drag line formation, A
attack-move, S stop, H hold, B build, . idle worker, Space last alert, Ctrl+right-drag area command; keys read from
the live bindings), hidden as soon as anything is selected or hovered. **Hovering a building** instead shows its card
(`HoverCards`): name, owner swatch, health or build progress, and "Producing <item> 45% +N". Your own and allied
queues are on the client already; for anyone else the client asks the server (`BuildingHoverServerboundPacket`,
at most every 500 ms while hovering, throttled to 200 ms per player server-side) and `BuildingHoverInfo` answers
only if that player can see the building right now (`FogOfWarServerEvents.canPlayerSeeBuilding`, owner or ally) -
an unseen lab answers nothing. Game-tested: `enemy_production_info_only_for_visible_buildings`.

## Economy overflow banner

It used to shout "YOU ARE OVERFLOWING METAL AND ENERGY" for the whole game. Now it says what to do ("Overflowing
metal and energy - build more labs or storage" / "build converters or storage" for energy), pulses for 10 s, then
shrinks to one 0.75x amber line on a dark strip; it pulses again only after it has been clear for 30 s. The spoken /
toast line keeps its one-a-minute cooldown.

## Idle-worker cycler

BAR's idle-constructor button: `HelperButtons.idleWorkerButton` sits on the minimap backing's empty bottom-left corner
(diamond - it was the top-left one until the declutter pass; the square grid's free top-right cell otherwise) with the idle count as its badge. Click, `.`
(`Keybindings.idleWorker`) or F10 select the next idle worker and centre the camera on it; Ctrl-click selects them
all. `.` is also the storage vault's build slot on some factions, so with a worker selected that card button wins and
the cycler stays quiet; F10 always cycles. The server decides idleness (`UnitIdleWorkerClientBoundPacket`, as before).

## Selection summary (BAR's selection grid)

With two or more units selected the selection strip shows **one icon per unit type**, not one per unit, ordered by
unit cost (metal + energy) descending as BAR's grid is:

- a `xN` count badge (bottom-left of the icon, a `renderBadge`) and a thin **group health bar** over it (the type's
  summed HP);
- a totals line above the grid: unit count, a summed HP bar with numbers, summed metal and energy cost, and the
  group's kills (`✕ 7`), laid out from measured widths - the bar grows to fit its "1099 / 1354" (it used to run into
  its neighbours: "x261099 / 13541990..."), and when the line doesn't fit, the numbers go compact (1.3k);
- **hovering** a tile shows that type's full unit card above the panel (in place of a tooltip) with the click hints
  as its footer;
- **click** a type to sub-select it (BAR), **double-click** to select every unit of that type on screen
  (`UnitClientEvents.getOwnedUnitsOnScreen`), **Ctrl-click** to drop that type from the selection, **Shift-click** to
  put that type's command card up without changing the selection; more types than slots collapse into a `+N` tile
  with a tooltip.

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

With a worker (or the commander) selected, hovering a wreck floats a green `⟲ 120 metal` over it - drawn as vanilla
name tags are (a see-through pass on a dark 0xC0 backing, then a normal pass in full green), since the see-through
pass alone on a 0x60 backing read as faint grey in lovish's clip; while an area
reclaim circle is dragged (Ctrl + right-drag) the circle's total metal hangs over its centre. Wreck metal lives
server-side on the block-display entity, so `WreckSyncServerEvents` sends the changed heaps' whole-metal values once a
second (`WreckSyncClientboundPacket`), the full list to a joining player; `WreckClientEvents` keeps the id → metal map,
prunes gone entities once a second, and matches the hovered block to the nearest wreck within 1.5 blocks.

**Wreck markers (Oct 24, "winning pays").** Players did not notice the wrecks, so in the RTS view every known wreck now
carries a compact number (just the metal; a big heap of 150+ gets the reclaim mark and a brighter, larger number, 500+
- a commander's 600 - is gold and larger still). The hovered wreck of a selected worker keeps the fuller label above and
loses its marker; while an area-reclaim circle is dragged only the circle's total shows. Per frame the cost is bounded
by `resources/WreckMarkerRules` (pure, JUnit-tested): wrecks within 240 blocks of the camera, inside the frustum and in a
chunk the fog shows now, richest first, at most 48, fading from 120 blocks out and scaling with the camera zoom.
Fog: `WreckSyncServerEvents` sends a client only the wrecks it may know (`Audience.mayKnow`), the marker also needs the
chunk to be lit, so a fogged heap is never marked; a heap seen earlier and since fogged simply loses its number.

## Idle auto-reclaim and the winner's wreck

An idle worker (nothing ordered, queued, built, gathered or fought) with a wreck within **8 blocks** walks over and
strips it by itself, by the same reclaim target + move target an explicit RECLAIM order sets
(`WreckServerEvents.autoReclaimIdle`, once a second, on the wreck cell index). It never overrides orders or building and
leaves commanders and combat engineers alone; a heap it cannot reach in 12 s is dropped. The `idleReclaim` gamerule
(default on) switches it off. First Battle's Reclaim step is not auto-completed by it. Fallen commanders leave a 600
metal wreck, fallen heroes 150 + 50 x level (never below the normal half-cost heap).

## Kill feed

`KillFeedServerEvents` queues a line for every unit killed by an enemy unit and every building an enemy unit brings
down (not cancels or defeat clean-up), merges duplicates within a flush (`x3`), and every 5 ticks sends each player
only the lines it may know about: it owns or is allied with either side, or the victim fell inside its vision
(spectators see everything). `KillFeedClientEvents` shows up to 5 lines, newest on top, small (0.75x, `HudLayout.FEED_SCALE`)
and right-aligned at the side under the stats strip or table (`PlayerPanelClientEvents.feedTop`; it was in the alert column
under the toasts, mid-screen), each name in light text - the owner's hue lightened to >= 4.5:1 contrast against the
line's backing composited over a white world (`TextContrast`) - after a small swatch in the owner's true colour, on a
0xC8 dark backing (lovish's dark-blue names on dark-green ground were unreadable), building kills in bold, promoted from a short queue at most
one per 5 ticks, fading after 4.5 s (was 7). A name too long for the 160 px column is cut with "...". Names travel as translation keys.

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

Allied lines in the stats table's Economy tab (`PlayerPanelClientEvents`, I / F4; it was the always-on player panel
until the declutter pass) carry BAR's economy readout: **two thin
bars**, metal (grey) and energy (yellow), filled to **stored / storage** (white when full), each with the income
figure beside it, then the commander health bar. Enemy lines stay name + alive/defeated. Hovering an allied line
gives the exact `Metal 350 / 1000 (+12.3/s)` figures as a tooltip. `PlayerPanelServerEvents` adds the four stored /
storage floats to the 2 s roster packet, still only on the recipient's own and allied entries, so an enemy's pool
never reaches the wire. Rows stay 11 px so an 8v8 roster fits.

## Build-bar icons

Every building icon is checked twice: `tools/check_building_icons.py` (CI safety step, no Minecraft; it also covers
every faction's production-item and unit icons, and refuses any plain planks / wool / concrete face) and the
`every_building_and_production_item_icon_ships` GameTest. Both fail on a missing mod texture, a non-texture vanilla
path, a `waxed_` block, or any of the **placeholder block faces** the faction labs / towers / capitols used to ship
with (cobblestone for the Castle, chiseled nether bricks for the Fortress...). `tools/gen_hud_icons.py` draws a
16x16 silhouette for each of those in its faction's palette (labs, T3 labs, towers and capitols of the Kingdom,
Gravebound and Horde, plus the Jade Pagoda and the Lotus Academy); the shared vault / extractor / converter / wind generator icons are
there too. Re-running the script is deterministic.

## Announcer voice files

The 23 Kokoro lines (`tools/voice_lines.txt`) are generated by `.github/workflows/voice.yml`, which uploads them as the
`announcer-voice` artifact **and commits them to the `voice-oggs` branch** (rebuilt on top of `origin/main`, never
pushed to main) under `assets/reignofnether/sounds/announcer/`: a sandbox session can't download an Actions artifact
but can merge a branch. `sounds.json` already points every `announcer.*` event at `announcer/<key>`, and
`NotificationClientEvents` falls back to the alert's chord while a file is missing, so merging the branch is the whole
switch-on.

## Slim info panel and thinner bars (Oct 22)

lovish, Oct 22: "the HUD for the units is so large, same with the buildings ... make them smaller without making them
look odd ... make the metal bar and energy bar not as thick ... units don't need their models to pop up, just a picture
of their icon with their stats", then "a lot of things are taking up space in the interface, which makes it more
difficult to see what's going on" (his Oct 21 playtest clip: announcements across a third of the screen, three or four
text rows down the middle under the bar, the bottom panel's model portrait).

- **Unit block** (`PortraitRendererUnit`): the unit's HUD icon - the mob head the selection strip and the buttons use -
  in a 32 px frame in the player's colour, the stats in a 2 x 3 grid beside it (attack, attack speed, range | armour,
  speed; a hero's level leads the grid), one 9 px health bar with the numbers on it under both, a hero's mana bar under
  that. 100 x 43 px (a hero 53) where the model portrait + stats column + health line were 104 x 60+. The name stays above
  it, the passive-effect icons above the name. The animated 3D head is only the fallback for an entity with no mob head
  (a player, a mob nobody drew one for): `PortraitRendererUnit.MODEL_FALLBACK`; set it false to find them.
- **Building block** (`PortraitRendererBuilding`): the building's icon in the same frame, the same health bar, and a
  frame beside the icon that holds the Repeat / Steward toggles of an own finished production building (they floated one
  row above the panel before, which forced the panel to be 94 px tall). The production queue and the multi-building grid
  are still laid out from the old 60 px portrait (`LAYOUT_TOP`) so they stay where they were (and where #140's compact
  tiles will shrink them).
- **L-shaped backing** (`HudClientEvents.infoPanelHeight`): the orders column keeps its 102 px, the info part right of it
  is only as tall as its content: block + name (+ passive icons), the unit card, the type grid with its totals line, the
  inventory or the queue's frame. The compact unit card is as tall as its lines (26 px, +10 per ability row; was the
  panel's 94) and 108 px wide (was 124); the hovered-unit card likewise (62 px, +10 per row; was 102).
  `HudLayout.infoHeightSingleWorst()` / `info_height_single_worst()` is the worst case the checker draws (a hero with
  passive icons: 79 px).
- **Economy bar**: the strip is 28 px tall (was 34) and each bar 5 px thick (was 7); amount row, bar and income / expense
  row each sit a pixel closer, the slider markers' cap moved under the amount row, the OVERCHARGE plate overhangs the
  thinner bar by a pixel each side. Colours, numbers and the income / drain alignment are unchanged. The stats strip under
  it follows (`STATS_TOP` 38 -> 32).
- **RTS chat feed** (`ForgeGuiMixin`): the unfocused chat is two lines at 75% (it was four at full size), drawn with a
  pose scale and a scissor, not the `chatScale` option (setting that re-wraps every line, every frame). Typing the full
  chat is unchanged. The announcement collapse rule (`ChatDedupe`) is unchanged.
- **Slim tiles** (`SlimTiles`, `HudLayout.SLIM_*`): the control groups, the global production queue and the helper column
  under the help button (chat, army, idle labs, attack warning) are 18 px frames (`Button` iconSize 10 + 8, the icon 12 px
  inside) instead of 22, the column's rows 22 px apart instead of 30. Same frame art, hotkey badge and hover light; the
  command panel's own tiles are untouched (#140's). Empty control groups were already hidden.
- **One status line** (`NotificationClientEvents`, `EconomyBarRenderer.stallRowY`): only the newest toast is drawn, as one
  12 px chip with a "+1" for the one waiting under it (it was a 13 px line each, up to two, under the stall strip). While
  the "Build speed N%" strip is up the toast is drawn IN its row for its few seconds and the strip steps aside (it keeps
  its row and comes back); a resign-vote banner, or toasts folded to icons, keep the old stacking. Same toast rules
  (priority, "x2" merge, `hud_toast_seconds`).
- **Side feed** (`KillFeedClientEvents`): the kill ticker is 0.75x text at the right, under the stats strip (under the
  table when it is open), out of the middle of the screen; it hides after 4.5 s. The chevron still opens the five-line
  version. 0.75x is the smallest text the HUD draws anywhere (hotkey badges, chat feed, tab labels) and the floor for new
  HUD text.
- **Chat feed, placed and quicker** (`ForgeGuiMixin`): the two-line feed rests on the command panel's top edge instead of
  7 px into the orders column, and a line fades after 6 s (vanilla 10): the feed is drawn 80 ticks "late" so each line is
  older than it is. The announcement collapse (`ChatDedupe`: "x2" for a repeated "has fielded...") is unchanged.
- **Audit** (`tools/hud_audit.py`, GUI scale 3 on 1080p, the sizes `tools/hud_layout.py` asserts; typical case = one unit
  with two abilities, five control groups, the default collapsed panels):

| Element | When | Before (px) | Before | After (px) | After | Saved |
|---|---|---|---|---|---|---|
| Economy bar (metal / pop / energy) | always | 440x35 | 6.7% | 440x29 | 5.5% | 17% |
| Minimap (diamond backing) | always | 132x132 | 7.6% | 132x132 | 7.6% |  |
| Control groups (5 in use) | always | 110x22 | 1.1% | 90x18 | 0.7% | 33% |
| Stats strip (Allies / Enemies) | always | 160x11 | 0.8% | 160x11 | 0.8% |  |
| Helper buttons: chat, army | always | 22x44 | 0.4% | 18x36 | 0.3% | 33% |
| Production queue (3 queued) | on demand | 76x32 | 1.1% | 64x28 | 0.8% | 26% |
| Production queue (10 queued, as in the 18-12-12 clip) | on demand | 120x54 | 2.8% | 100x46 | 2.0% | 29% |
| Stall / waste / overflow row | on demand | 200x12 | 1.0% | 200x12 | 1.0% |  |
| Alert column: banner + toasts | on demand | 200x82 | 7.1% | 200x45 | 3.9% | 45% |
| Kill feed ticker (side, 0.75x) | on demand | 0x0 | 0.0% | 160x9 | 0.6% | moved |
| Announcements (chat feed) | on demand | 320x36 | 5.0% | 240x14 | 1.5% | 71% |
| Helper buttons: idle labs, attack, idle worker, beacon | on demand | 22x88 | 0.8% | 22x80 | 0.8% | 9% |
| Hover card (a unit under the cursor) | on demand | 128x102 | 5.7% | 128x82 | 4.6% | 20% |
| Controls cheat-sheet (opened with ?) | on demand | 180x96 | 7.5% | 180x96 | 7.5% |  |
| Command panel, one unit: orders column | selection | 143x102 | 6.3% | 143x102 | 6.3% |  |
| Command panel, one unit: info block + card | selection | 237x102 | 10.5% | 217x56 | 5.3% | 50% |
| Command panel, multi: orders column | selection | 143x102 | 6.3% | 143x102 | 6.3% |  |
| Command panel, multi: info block + type grid | selection | 304x102 | 13.5% | 300x75 | 9.8% | 27% |

| Total HUD footprint | Before | After | Saved |
|---|---|---|---|
| Nothing selected (always-on elements) | 16.5% | 14.8% | 10% |
| One unit selected | 33.3% | 26.5% | 21% |
| Multi-selection | 36.3% | 30.9% | 15% |


### Bigger options for lovish (not built), ranked by space saved at scale 3 on 1080p

Done in this pass, off the earlier list: the slim status line (toasts + stall strip + side feed) and the 18 px tiles for
the control groups, queue and helper column. What is left, biggest first:

1. **Smaller default minimap** (2.5%, always on): the 132 x 132 backing is the biggest single element; radius 60 -> 48 gives
   108 x 108, M still enlarges it. (A taste call: the map is how a BAR player reads the game.)
2. **A three-step HUD key** (1.7% at rest, 2.5% while producing): full -> slim (the stats strip, control groups, helper column
   and queue appear only when they have something to say) -> clean (today's `=` view).
3. **Merge panels** (about 1.1% always, 1.7% with every helper showing): fold the Allies / Enemies strip into the economy
   bar's population block (0.8%) and move the chat / army / idle-labs / attack buttons onto the minimap's frame, where the
   idle-worker cycler already lives.
4. **Economy bar folded by default** (1.1%): the existing chevron's one-row mode (22 px: amount / storage, net, a 2 px fill
   line; income and expense in the tooltip), expanding itself while a stall / waste / overflow line is up.
5. **One-line chat feed** (0.7%): the feed is two lines now; one line, newest only.

## Windmill sails

Every wind generator declares its rotor hub in `WindGenerator.SAIL_HUBS` (structure-relative hub block, the face the
rotor sits outside of, blade length); `WindmillRenderClientEvents` rotates it with the placement exactly as the
structure is rotated and caches it per placement. The sails float no more: the renderer used to translate by the
camera *entity* (the player, nowhere near the RTS camera) instead of the render camera, so they drifted beside and
above the towers by a camera-dependent amount, and it guessed the hub from the first horizontal log it found. Sails
draw only on a built mill whose hub block is standing (not on a site being rebuilt). Blades are 2.6 blocks on the
hub-at-4 mills and 3.0 on the taller Tidewrought / Stormcallers towers. `tools/check_sail_hubs.py` (CI) and the
`every_sail_hub_sits_inside_its_mill` GameTest keep every hub a horizontal hub log inside its footprint, open on the
rotor side, matching the placed structure in all four rotations.

## Settings

Mod Settings (title screen Mods -> Configure, or the match menu's Mod Settings) has three pages: the main one
(alliance colours, minimap shape, lobby sounds, alerts), **Camera...** (design/camera.md) and **Display...**
(`config/DisplaySettingsScreen`, read through `config/DisplaySettings`). Every control writes the client config at
once; nothing needs Apply.

| Option | Values | Notes |
|---|---|---|
| Icons | By zoom (default) / Always icons / Never icons | Strategic view (`StrategicViewClientEvents`). Always: units are team-coloured role icons at every zoom; Never: always the models. |
| Icon zoom threshold | 20-70 (default 70) | Zoom at which units turn into icons. Capped at 70: past it vanilla culls far mobs, and the icons have to take over before that or there is a zoom band where far units vanish. Exit hysteresis stays 8 below. Big units (T3, heroes, the commander, anything 1.5+ blocks wide) keep their model until 1.35x the threshold (94.5 by default, same 8 hysteresis), BAR-style, so they don't pop into dots with the infantry; the big switch only arms while the small one is. |
| Icon size | 50-200% | Unit icons and building role glyphs. On top of the tier size (`UnitIconTier`, as BAR's icontypes): workers 0.9x, T1 1x, T2 1.25x, T3 / heroes / commander 1.6x of a 3.6 px radius. Every icon sits on a 1 px dark edge; selected icons get a white ring outside it, preselected a grey one. Tier is read client-side from metal cost (T3 >= 1000, T2 >= 180) plus the ten T2 units priced inside the T1 band. |
| Fog style | Subtle / Clear (default) / Strong | Changes `FogOfWarClientEvents.FOG_TINT_RGB`, the multiplier `FogTintingBlockColor` bakes into fogged chunk meshes: one chunk rebuild on change, zero per-frame cost. Strong also hatches never-seen ground on the minimap (baked into the minimap's terrain pass). |
| World look | Off / Subtle (default) / Full, plus Distance haze 0-200% | `postfx/WorldLookPass`: one fullscreen pass at `RenderLevelStageEvent.AFTER_LEVEL` - distance haze from the depth buffer (ortho RTS view: measured from the ground at the screen's bottom edge; first person: from the eye), a filmic grade with saturation at or below neutral, a faint vignette, no depth of field. The hand, entity outlines and the whole HUD (strategic icons included) come after it and are untouched; world-space overlays take the haze and grade of the ground they sit on. Off by itself while an Oculus / Iris shader pack is on. |

Match-wide options are host choices in the lobbies, not client settings: **Economy pace** (Relaxed / Standard / Fast,
design/economy_pace.md) shows in the match menu header and on the results screen.
