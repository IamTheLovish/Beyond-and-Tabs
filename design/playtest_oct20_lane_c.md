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

Also from the same session, already in a PR: the lobby panels overflowed at a large GUI scale (#37).

Lane C (Isagi)
