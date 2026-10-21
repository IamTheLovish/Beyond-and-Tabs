# Merged buildings - the code (slice 1, Oct 21 2026)

The spec is the project doc `claude/merged-buildings.md` (v2: sections, the shape ladder to 5x5, capabilities, art).
This note is the code's side of it, for whoever builds the next slices: Lane B (the shapes' art), Lane C (the HUD's
section ticks and the grid ghost's merge toggle), Lane A (bigger shapes, capabilities, other factions).

## What slice 1 does
Lay the White Angel wind generator (`Buildings.WIND_GENERATOR_VILLAGERS`) in a Shift+Alt grid. When a generator
finishes, the finished generators of the same owner standing **edge to edge and in line** with it (the grid lays them
exactly so; a one-block gap, the bot's 4-block pitch, never merges) are matched to the ladder, biggest shape first:

| members | shape   | output bonus |
|---|---|---|
| 2 (2x1)  | Pair    | +4%  |
| 4 (2x2)  | Farm    | +8%  |
| 6 (3x2)  | Row     | +10% |
| 9 (3x3)  | Quarter | +12% |
| 16, 25   | Citadel, Wonder | declared (+14%, +15%), **off** until slice 2 (`MergeShapes.MAX_MEMBERS = 9`) |

The matched members are swapped for one `ComplexPlacement`: one selection box, one health bar, one strategic icon, one
minimap mark, one kill-feed line, named "Wind Generator (Farm x4)". Leftovers stay single buildings (a 4x3 of 12 = a
Quarter, a Pair and a single). Damage lands on the **section** nearest the attacker; a section at the collapse
threshold (the same 50% a lone building dies at) falls - its blocks come down with the collapse effect - and the
survivors re-match into the biggest shapes they still form (a Quarter minus a corner = a Row + a Pair; minus its
centre = four Pairs). Rebuilding a generator on the fallen footprint re-forms the bigger shape when it finishes.
Merging is on by default and can be switched off per building (`BuildingPlacement.setMergeEnabled`, the `no_merge`
tag; `BuildingAction.TOGGLE_MERGE` from the client, `BuildingServerboundPacket.toggleMerge`).

## The pieces (all in `building/`)
- **`MergeShapes`** - pure: the ladder, the bonuses, and `match(cells, cap, touching)`, the greedy biggest-first
  partition of lattice cells into rectangles (both orientations), verifying every lattice edge against the world.
  Shared by the server, the grid ghost's preview (`BuildingLineClientEvents.drawMergePreview`, gold outlines) and
  the tests.
- **`ComplexPlacement extends BuildingPlacement`** - the complex. Its blocks are the union of its members'
  structures (the placeholder look: the members' own mills, joined; the shape's real drawing arrives later through
  `changeStructure`, the code never depends on it). Sections = member footprints + blocks; health, income and storage
  are the live sections summed (income x the bonus); `destroyCandidates()` restricts a hit to the target section;
  `shouldBeDestroyed` is "no live section left". Saved as its sections (ordinary built buildings), re-formed on
  load. Carries the animation cues (`formedAtTick`, `collapsedAtTick`, `recentlyCollapsed`) for the raising /
  collapse animation that comes with the art.
- **`BuildingMerge`** - the server's rules through one `Registry` (the world's list with packets, or a test's private
  list without): `onBuilt` (a generator finished), `checkSections` (from the complex's tick once a block fell),
  `reformAll` (after a load), `toggle`. All of them are the same **re-match** of the connected component round a
  seed: keep every complex whose sections are exactly one of the new groups, dissolve the others into lone
  buildings, form the new groups. **Stability:** two partitions are weighed, the fresh greedy one and the one that
  keeps every standing complex whole; the fresh one wins only when it builds a bigger shape (group sizes largest
  first), so a Quarter is never re-formed one column over for a 12th cell, while a Pair's neighbours completing a
  Farm do replace it. Deterministic, so the client's preview and the tests agree with the server. A complex being
  decommissioned is never on the grid; if a section of it falls, the parts carry the decommission on (progress and
  their share of the metal paid, BuildingReclaim) and never re-merge, so no metal is paid twice. Members keep what
  they brought (tags, scenario commands and role, payer) through the complex and back out of it.
- **`MergedBuildingClientboundPacket`** - MERGE: the complex and its members to the clients that may see it
  (fog-filtered like every building packet). The client rebuilds the same complex from the members
  (`BuildingClientEvents.mergeBuildings`), drops what it held at those origins and keeps the selection. The registry
  tells each player once the list is settled: a replaced placement the player saw is REMOVEd unless a new one they
  also see stands on its origin (the PLACE / MERGE replaces it in place, selection and HUD card carried over); one
  they could not see is flagged destroyed, so its fog ghost clears when they look again. The periodic SYNC keeps
  health current.
- Small hooks: `Building.canMerge()` (only the White Angel mill for now), `BuildingPlacement.memberCount()` (the
  bot's build counts, the market's six generators and a decommission's refund count members), the sails renderer
  flies every section's sails, `BuildingStats.titleSuffix` names the shape.

## Not in slice 1 (honest list)
- Art: the shapes' own structures, the raising / collapse animation and sound, effects, lights. The cues are synced.
- Capabilities (Surge at 9, Grid Ward at 16, the Wonder's landmark power), Citadel and Wonder (the cap).
- Other factions' generators, converters, storage, Bastions. The Stormcallers' generator must wait until the ley
  network keys on sections (it keys on one centre per building).
- The HUD's section ticks on the health bar and the merge toggle on the grid ghost (Lane C; the server side exists).
- A hit "where the shell lands": every attack path resolves to the section nearest the attacker;
  `ComplexPlacement.destroyRandomBlocksAt` is there for callers that know the impact block (arrows, shells).
- Decommissioning one section on its own; a decommission of a whole complex refunds every member (saved and
  handed on per section if the complex shrinks or reloads mid-way).
- Bots still lay their rows a block apart, so they never merge (by design for now).

## Tests
`gametest/MergedBuildingGameTests` (own class): the ladder and its bonuses; a 2x2 forms a Farm with summed output
+8%, four mills' health and refund; only touching, aligned neighbours merge; merge-off keeps a mill apart and labs
never merge; a Quarter losing a corner shrinks to a Row and a Pair (its centre: four Pairs), losing everything ends
it; a rebuilt section re-forms the Farm; income follows the wind at 4 and 24 through the members; the MERGE packet
round-trips. Every placement lives in a private registry - nothing touches the world's building list.
