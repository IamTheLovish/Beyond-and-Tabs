# Merged buildings - the code (slice 1, Oct 21 2026)

The spec is the project doc `claude/merged-buildings.md` (v2: sections, the shape ladder to 5x5, capabilities, art)
and `claude/merged-buildings-animation.md` (the raising / collapse moments). This note is the code's side of it, for
whoever builds the next slices: Lane B (the shapes' art), Lane C (the card with section ticks, one strategic plate
per complex), Lane A (bigger shapes, capabilities, other factions, bots laying grids).

Two builds of this slice existed (PR #80, one `ComplexPlacement` that replaced its members; PR #82, complexes derived
over ordinary placements). #82 is the one that landed: a fraction of the surface, nothing else in the game learns
about complexes, and every section is literally a building. What #80 did better was carried over (the stability
rule, the y-step limit, the world-oriented preview lattice, its tests) - see "Ported from #80" below.

## What slice 1 does
Lay the White Angel wind generator (`Buildings.WIND_GENERATOR_VILLAGERS`, `Building.mergeable`) in a Shift+Alt grid.
The tick its last member finishes, the finished, switched-on mills of one owner standing **edge to edge, in line and
within 2 blocks of each other's ground** (exactly what the grid lays; a one-block gap - the bot's pitch, a hand-laid
row - an offset or a cliff never merges) are cut into the ladder, biggest shape first:

| members | shape   | output bonus on every member |
|---|---|---|
| 2 (2x1)  | Pair    | +4%  |
| 4 (2x2)  | Farm    | +8%  |
| 6 (3x2)  | Row     | +10% |
| 9 (3x3)  | Quarter | +12% |
| 16, 25   | Citadel, Wonder | declared (+14%, +15%), **off** until slice 2 (`MergedBuildings.MAX_MEMBERS = 9`) |

Leftovers become the most Pairs the cells allow, the rest stay single (a 4x3 of 12 = a Quarter, a Pair and a single;
a ring of 8 = four Pairs). A complex is one building to the player: click a section and the whole thing is selected,
the card's title reads "Wind Generator · Farm +8%", the hover card "Farm: 4 sections, +8% output each"; while the grid
is still rising a member's card reads "· Quarter, 4 of 9 standing". Each member keeps its own blocks and health, so a
hit lands on the section under it; a section that falls leaves the registry's input and the complex **shrinks to the
biggest shape the survivors still form** (a Quarter minus a corner = Row + Pair; minus its centre = four Pairs), the
HUD says so, and the ghost of a new mill snaps into the hole; rebuilt, the bigger shape re-forms. Merging is on by
default and switched per building from the card (the lightbulb; one click sets every selected mill of that kind).
The merge moment is a placeholder until Lane B's animations: every section's link beam to the middle of the footprint
for 3 s and the faction's completion motif a step lower per shape.

## The pieces (all in `building/merge/`, plus small hooks)
- **`MergedShape`** - the ladder and its bonuses; `ladder(max)` biggest first.
- **`MergeGrid`** - pure integer geometry: `partition(cells, max, touching)` cuts a set of lattice cells biggest
  shape first (each shape lying and standing, anchors row by row), then Pairs as a *maximum matching* of the
  leftovers (Kuhn on the grid graph), every lattice edge a shape uses checked with `touching` (the lattice is a guide,
  the world is the truth). Deterministic: server, ghost preview and every client cut a grid the same way.
- **`MergedBuildings`** - the registry (`SERVER` the authority; `CLIENT` the same derivation over the buildings a
  client has; `CLIENT_PLANNED` over built and rising members, for the card). Once a tick `update` checks a cheap
  signature over the candidate buildings and re-derives only when that set changed (a member finished, died, was sold
  or switched). `neighbourCell` is the adjacency rule (edge to edge, flush, `MAX_Y_STEP`); `matchGroup` walks one
  connected block at a time; `partition` weighs two cuts (see stability); `recompute` keeps the identity (and the
  formed tick) of every complex whose members did not change and reports FORMED / GREW / SHRUNK / DISSOLVED.
  Save / load: the switch (`MERGE_OFF`) and the section's anchor (`MERGE_ANCHOR`) are saved in the building's data;
  the complexes are re-derived at load, seeded from the anchors, silently (no beams for a loaded base). Multiplayer:
  the switch and the anchor ride the once-a-second building sync; the toggle goes up as `BuildingAction.TOGGLE_MERGE`
  (owner-checked); each client derives its own complexes with the server's anchors as the cut's guide, so a client
  under fog that sees part of an enemy grid sees a smaller shape, which is what fog should show.
- **`MergedComplex`** - the shape, the members in row order, the footprint, and the animation state (Lane B reads,
  never writes): `formedAt` / `isRaising` (RAISING_TICKS = 60), `previousShape`, `lostSections`, `isSettling`,
  `healthFraction` / `sectionHealth` for the ticked bar.
- **`MergedBuildingsFx`** (server, the placeholder moment) and **`MergedBuildingsClient`** (the card switch, the HUD
  lines with their join / load guards, the shape names, the rebuild snap).
- Hooks: `BuildingPlacement.getEnergyIncome` x `outputMultiplier` (so the wind, the ley links and the economy pace
  compose untouched); `BuildingPlacement.mergeEnabled` / `mergeAnchor` + `updateButtons`; `BuildingLineClientEvents.DragLattice` /
  `drawMergePreview` (gold frames, the live "Quarter (+12%)" line, the queued line); `BuildingClientEvents` whole-
  complex selection and the ghost snap; `BuildingStats` titles.

## Stability (the rule that keeps a base from rearranging itself)
A row-major cut alone would re-form a Quarter one column over the moment a twelfth mill appeared on its west side -
and with the art, move its turbine. So `MergedBuildings.partition` weighs two cuts of a connected block: the fresh one
over every cell, and the stable one that first keeps every complex already standing whole in the block and cuts only
the rest. The fresh one wins only when it builds something bigger (`biggerShapes`: sizes largest first, compared one
by one - [6] beats [2, 2, 2], [9, 2] ties [9, 2] and the standing cut wins a tie). A Pair's neighbours completing a
Farm, a Farm's column completing a Row, a Row's third row completing a Quarter all replace the smaller shape (GREW,
`previousShape` set); a complex that lost a member is not whole and is cut afresh with the rest (the shrink). The
ghost preview cuts the dragged rectangle fresh: mills already standing beside the drag can grow it further.

The stable cut is history-dependent, so the history is kept where it has to be: every section saves the origin of
its complex's first member (`MERGE_ANCHOR` in the building's data) and the load seeds the server registry from those
groups (`seed`) before its first silent update - a reload keeps the Quarter where the player built it. The same
anchor rides the once-a-second building sync (`BuildingPlacement.mergeAnchor`), and the client registry
(`followAnchors`) takes the anchor groups as the standing complexes its stable cut keeps, so a client that joins or
rejoins cuts the grid as the server has it within one sync; a group the client sees only part of (fog) is cut fresh
among what it has. The anchors steer only the cut - identity, the formed tick and the HUD lines stay each
registry's own.

## Ported from #80
The y-step limit (`MAX_Y_STEP = 2`), the stability rule above, the preview's world-oriented lattice (a drag towards
-x previews the same cut the server makes), `partition`'s per-edge `touching` check, and its tests (the ladder above
the cap, stability, neutral mills). Not ported, on purpose: one placement per complex (sections as real buildings
need no replacement dance, no new packet, no save-format tricks), and leaving a decommissioning section out of the
grid (the clients are never told a decommission is running, and every side must cut the same grid; the section leaves
when it is gone).

## Not in slice 1 (honest list)
- Art per shape (Lane B): a section's structure swaps with `changeStructure` per member - the Quarter's central
  turbine, galleries between sections, emissive windows at dusk. The raising / collapse animations and sounds.
- Capabilities (Surge at 9, Grid Ward at 16, the Wonder's landmark power); the Citadel and the Wonder (the cap).
- Other factions' generators, converters, storage, Bastions. The Stormcallers' generator must wait until the ley
  network keys on sections (it keys on one centre per building).
- One portrait with section ticks on the card and one strategic plate per complex (Lane C; the state is here).
- Bots keep laying single mills a block apart, so they never merge (slice 4 teaches them grids).

## Tests
`gametest/MergedBuildingGameTests` (own class; private registries over placements that are never in the world's
list, except the income and collapse tests, which register theirs and take them out again): the ladder and the
bonuses, the cap and the ladder above it, the per-edge touching check; a Farm's summed output +8% at wind 4, 24 and
live; a Quarter losing a corner (the real collapse path) shrinks to Row + Pair and a rebuilt corner re-forms it; the
switch; what never merges (a gap, an offset, two owners, an unfinished member, other factions' mills, labs, the
extractor, the capitol); identity across unrelated changes, a Pair losing a member, a rotated member, a reload; the
planned cut of a grid under construction; the switch and the anchor surviving a save, a seeded reload keeping an
eastward Quarter and a stale anchor cut afresh; stability (a Quarter stands beside a new column, Pair -> Farm -> Row
-> Quarter); a terrace step merges, a cliff never, nor do neutral mills; a client cutting the grid as the server has
it from the synced anchors, fresh under fog.
