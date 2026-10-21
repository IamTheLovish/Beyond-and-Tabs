# Playtest feedback, Oct 20 2026 (Lane C tester, multiplayer)

Notes from a multiplayer session by Lane C's developer (GitHub: Joisco), given in chat and written down here so every
lane sees them. Each item lists a suggested owner from design/LANES.md and design/HANDOFF-lane-c.md. **Items marked
"lovish decides" are design calls: nobody builds them before he answers.** Lane C will add the code locations to this
note and open one PR per item it takes.

| # | Feedback | Suggested owner | Needs |
|---|---|---|---|
| 1 | Build buttons for buildings of the same kind (foundations such as metal extractors) are always in the same command-card slot, for every builder and faction. | Lane C (C1, UI) | none - consistency fix |
| 2 | Some hotkey letters are blocked by other buttons (for example the metal extractor takes one), so a building can't get its natural key. | Lane C (C1, UI) | lovish decides the key scheme if it changes BAR's keys |
| 3 | Hold **Alt+Shift** together while placing to lay buildings out in a grid (BAR-style). | Lane C, after Lane A's `BuildingPlacement` work lands | coordinate with Lane A |
| 4 | Builders can queue **3x** the current total number of buildings. | Lane A or C (a limit change) | lovish OKs the number |
| 5 | The commander is its own unit type, apart from the builder types: "select idle builders" must not pick the commander by accident. | Lane C (C1, selection / HUD) | none |
| 6 | **Fix bridges.** Crossing water is still a problem in play. | Lane C (C3, water crossing) | repro clip or map name |
| 7 | An option to **level heroes up automatically**, picking abilities at random, so you don't have to do it by hand. | unassigned (hero/) | lovish decides: default off? random or a fixed build order? |
| 8 | **Support units** stay at range and keep using their ability. An attack order on an enemy doesn't make them walk in to fight. | Lane A (unit AI) | lovish decides which units count as support |
| 9 | **Separate storage buildings** for metal and for redstone (energy), not one combined storage. | lovish decides, then Lane B (building) + economy code | design call |
| 10 | A **placement grid** that shows which kind of building fits in which spot (for example metal spots for extractors), while placing. | Lane C (UI) with Lane A (placement rules) | lovish decides the look (pictures first) |
| 11 | **Ctrl and Alt groups for every unit type.** Ctrl+number groups last for the whole match. Alt+number groups are saved and last across matches (BAR-style autogroups: a unit type bound to a number, so new units of that type join it). Today Ctrl+# makes a match group and Alt+# moves the selection into a group for that match only (`hud/HudClientEvents.java` ~2274). | Lane C (C1, HUD/selection) | lovish OKs Alt = autogroups |
| 12 | **Queue a move after any order:** Shift+move after a build or an attack order runs once that order is done. | Lane A or C (command queue, design/command-queue-progress.md) | check what the queue already allows |
| 13 | **Builders assist builders:** guard a builder to help with whatever it builds (`UnitAction.GUARD` says workers assist), and the **order line changes its colour or style** with the state (moving, building, assisting). | Lane C (order lines UI) + Lane A (assist logic) | lovish decides the line colours |
| 14 | **Tab jumps the camera to your commander; Space stays BAR's jump to the last alert.** Today plain Tab is BAR's camera overview (`HudClientEvents`), and Space also opens the shop (`openShop`), so the overview needs a new key. | Lane C (camera / keys) | lovish decides where the overview goes |
| 15 | **Buildings go in Ctrl and Alt groups too.** Ctrl groups already take buildings (`hud/ControlGroup.java`); Alt autogroups should do the same by building type. | Lane C (with #11) | none |
| 16 | **A left click on empty ground deselects.** After selecting units, a click that isn't an order lets go of them, so you can select new ones. | Lane C (selection) | check against BAR (BAR keeps the selection on a ground click unless you box-select) |
| 17 | **Shift+click or Shift+box adds** units to the current selection instead of replacing it. | Lane C (selection) | none - check what Shift does today |
| 18 | **Selection panel icons:** left-click a unit-type icon to keep only that type; right-click to remove one unit of that type (BAR: Ctrl/Shift variants too). | Lane C (HUD) | lovish OKs which click does which |
| 19 | **Area mex as a circle** (BAR): with builders and the extractor picked, right-drag a circle and every free patch inside gets an extractor. Today Shift+right-click on a patch does a fixed 40-block area (`PatchQuickBuildClientEvents`), and nothing in the game tells players. | Lane C | none (BAR behaviour) |
| 20 | **Extractors snap to the nearest free patch** while placing. | Lane C | done in #43 |
| 21 | **An undo hotkey** that removes the last queued order. | Lane C (command queue UI) | lovish OKs the key |
| 22 | **Right-click a queued building's ghost** to cancel it, or drag it to move it. | Lane C (building UI) | none |
| 23 | **Unfinished buildings stay inert, and building only starts when a builder arrives:** a placed foundation doesn't act or progress until a builder is on it. | Lane A (building / economy) | check what happens today first |
| 24 | **Box selection prefers combat units:** a box over 2 builders and 3 combat units selects only the combat units; a box over builders and buildings takes both. Smart Select already exists (`cursor/SmartSelect`, BAR's widget), so this is checking its rules against the tester's. | Lane C (selection) | lovish OKs the rule |
| 25 | **Double-click selects all units and buildings of that type in a radius that grows with zoom.** Today a double-click selects nearby units of the same type (`UnitClientEvents` ~922); buildings and the zoom-based radius are new. | Lane C (selection) | none |
| 26 | **Attack-move:** units move forward and stop to fight whatever they meet. `UnitAction.ATTACK_MOVE` and Fight (F) exist; check they stop and engage the way the tester expects. | Lane A (unit AI) | repro if it misbehaves |
| 27 | **Support units auto-guard** nearby units and use their support abilities on their own (see #8). | Lane A (unit AI) | lovish decides which units are support |
| 28 | **Area attack.** It exists (Ctrl+right-drag with fighters, `unit/AreaCommandClientEvents`), but nothing in the game shows it. | Lane C | discoverability: a hint or a button |
| 29 | **Heroes start on auto abilities:** on level-up the hero takes a random ability, unless the player picks one to take the moment it levels. The hero also uses its abilities on its own unless that behaviour is changed. Extends #7; `hero/HeroBot.spendPoints` already auto-picks for bots. | unassigned (hero/) | lovish decides random vs a build order, and the default |
| 30 | **Hero regeneration scales with level.** | unassigned (hero/) | lovish sets the numbers |
| 31 | **Possessed RTS units fight like a real game:** effects, and abilities by tier (T1 one ability or weapon, T2 two, T3 three, mixing weapons and abilities), and the first-person fight should feel like normal combat. | unassigned (hero/possession) | lovish decides per-unit kits |
| 32 | **Auto reclaim or area reclaim for constructors and support units** (reclaim on the move). Area reclaim exists (Ctrl+right-drag with workers); auto-reclaim while idle or moving is new. | Lane C (orders) + Lane A (AI) | default on or off |
| 33 | **Command preview while holding Shift:** lines and markers for every queued order of the selection. | Lane C (order lines UI) | none (BAR behaviour); see #13 for colours |
| 34 | **Every key rebindable**, including the modifiers and the unit/building hotkeys, ideally with a BAR preset. Most RTS keys are already in Minecraft's Controls (`keybinds/Keybindings`); check which aren't. | Lane C (keys) | none |
| 35 | **No new address every time you host.** Options: (1) a fixed tunnel such as playit.gg: one address forever, no new service; Host Online now keeps a fixed port for it (#46). (2) A **friends list in the mod**: add a friend's code once, see who is hosting, press Join. The host's mod posts its current e4mc address to a small lookup service under a signed code, so someone must run that service (for example a free Cloudflare Worker), and codes must be signed so nobody can send friends to a fake server. About a day of Claude work. (3) Post the address to the group's Discord webhook automatically. | Lane C | **lovish decides** whether to run a lookup service (option 2: account and security). Option 1 is in #46 |
| 36 | **The placement ghost turns green wherever the building fits, red where it doesn't, until it is placed.** The ghost is tinted by validity today (`BuildingClientEvents.drawBuildingToPlace`); check why the tester didn't see it green (colour too weak at RTS zoom, or a validity bug). | Lane C (building UI) | repro first |

Done so far: #5 in #40 (idle builders skip the commander), #3 in #41 (Shift+Alt grid).

Also from the same session: the lobby panels overflowed at a large GUI scale (#37).

Lane C (Isagi)
