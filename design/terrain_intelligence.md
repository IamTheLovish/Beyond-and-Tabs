# Terrain intelligence, round 1 (branch `terrain-oct14`)

**Honest line.** The brief's plan (wading state + chases that respect water + engagement slots + a line formation +
a stuck nudge) is the right shape; I would not make deep water impassable yet - the maps have fords that whole armies
must cross and the RTS pathfinder's 25x water cost already makes a crossing a last resort. Two things I changed:

1. **Wading speed is 0.5x of LAND speed, not 0.5x of vanilla's water speed.** Vanilla `LivingEntity.travel` already
   crawls a mob through water: the water branch accelerates by `0.02 * zza` per tick against friction 0.8, where on land
   it is `1.33 * speed * zza` against 0.546. For a 0.25-speed unit that is ~0.4 blocks/s in water against ~2 on land
   (5x slower; 7x for faster units). Halving the attribute on top would make a 4-wide ford a 20 s crawl - the very
   place the Oct 13 clips showed 40 units stacking - so the rule is written as "a wading unit moves at half its land
   speed" and set through the water friction the game already uses (`getWaterSlowDown`, which the Drowned, dogs, cats
   and the Steam Runner already override at 0.94 to wade well). That is a speed-UP for crossings and still a real
   decision: half speed and 0.75x damage, and the chase rules below keep the fights on the banks.
2. **Chases stay on vanilla A* with a terrain-checked goal cell, not the RTS pathfinder.** See "what the chase does".

## What the chase actually did (read from the code, Oct 14)

- Every unit gets `WATER` malus -1 on join (`UnitServerEvents.onEntityJoin`). In `WalkNodeEvaluator.findAcceptedNode`
  a water cell with a negative malus gets no node; the jump branch then tries the air above it, which is `OPEN` and
  falls back into the water, and the fall loop returns a `BLOCKED` node with cost -1. So a unit **on land never enters
  water on a vanilla path**: `createPath(target)` for a target standing in a river returns a partial path (`canReach()
  false`) whose last node is the bank cell nearest the target, and the unit walks there and stops. Not a beeline:
  `MoveControl` only steers to the next path node and the melee goal never calls it directly.
- The reverse is the bug. `WalkNodeEvaluator.getStart` makes the start node whatever the unit stands in, cost -1 in
  water, and `isNeighborValid` accepts a negative-cost neighbour whenever the CENTRE node is negative. So a unit that is
  already in water (pushed in by the crowd behind it, or walked in on an RTS crossing path whose water cost is finite)
  chases through water freely - at negative node cost, so the river becomes the preferred route. That is the
  "attack-move funnelled into water chasing targets" clip: the first rank is pushed off the bank, and from then on
  everyone in the water chases anything in the water.
- Every bank unit chasing the same wading target paths to the same nearest bank cell (one tile, 40 units), and every
  attack-move sends the whole selection to ONE block (`UnitActionItem` only builds a formation for plain MOVE).

## Chosen design

- `unit/Terrain.java` is the one place: `WadingState.of(entity)` (NONE / WADING / EXEMPT), the speed and damage numbers,
  the cell tests (`canStand(level, cell, mobility)`, `isWet`). `LivingEntityMixin` reads it for `getWaterSlowDown`
  and in `actuallyHurt` (every unit-sourced hit, melee and projectile, splash included). `Unit.tick` calls
  `Terrain.tick` for the splash particle every 2 s. No unit class is touched.
- `unit/Engage.java`: the approach cell for a chase. Melee: 8 ring slots at ~1.2 blocks round the target, claimed per
  target (a slot is free when its owner is dead or has another target), only cells the attacker's mobility can stand
  in; a target in water that leaves no land slot gets the nearest land cells within 3 blocks as its slots; none at all
  = hold. Ranged: the target's own cell, or the nearest land cell to a wading target (so a shooter holds the shore).
  The goals path to that cell with vanilla A* and, once the path is walked and the slot point is still out of reach,
  close the last half block with `MoveControl` (a cell centre can be 0.1 out of reach of a target that drifted).
  Why not the RTS pathfinder for chases: `RtsPathfinder.requestPath` captures a chunk snapshot and goes through the
  async worker pool per request, and a chase repaths every 5 ticks per unit at close range - a 60-unit scrum would be
  hundreds of snapshot requests a second (the pool backpressures at 1000 and drops as "busy"). Vanilla A* over 1-3
  cells is microseconds and synchronous, and with the goal cell chosen by terrain it cannot end in water.
- `UnitFormations.getMoveFormation`: cell spacing from the widest unit (`max(1, ceil(width + 0.3))`: 1 for 0.6-wide,
  2 for >1.0), a rectangle twice as wide as deep, rotated to face the move direction, centred on the clicked block;
  units keep their relative order (front row stays front, left stays left). The 3-unit line stays.
- `unit/StuckNudge.java`: a unit following a path that moved < 0.05 blocks in 40 ticks: re-path, then a 1-block
  sidestep perpendicular to its heading (served through `Engage` so the chase goal's own repath does not undo it),
  then drop the chase target. One stage per 40-tick check, one `[Stuck]` debug line per unit per 10 s.

## Progress

- [x] 1. Water is a decision (Terrain, mixin reads, particle, "wading" line on the unit card; test)
- [x] 2. Chases respect terrain (Engage.approach; melee + ranged goals; test)
- [x] 3. Engage with spacing (ring slots, ranged sidestep; test)
- [x] 4. Formation spacing (rectangle facing the move; test)
- [x] 5. Stuck nudge (test)
- [x] 6. Eyes scene 12_river_crossing
- [x] 7. balance_audit.py re-run: "balance audit: OK", the same four pre-existing outlier warnings as main (the audit reads the unit stat constants; the wading 0.75x is a state, not a stat, so no sheet changed)

## What CI measured (branch runs, Oct 14)

- Wading: a Pillager on land covers 6.8 blocks in 60 ticks, one in a 1-deep pool 2.3 (ratio 0.33-0.34, friction
  0.91 from `Terrain.waterFriction`); the pool Pillager's hit lands for exactly 0.75x. Vanilla's own water crawl would
  have read ~0.15 here.
- Bank hold: the Vindicator acquires the villager two blocks into the river, waits on the bank with dry feet and does
  not hit it; when the villager steps out it is engaged (25 -> 7 health in 100 ticks). First run: it settled on the
  bank cell it already stood on (3 blocks off) because bank cells were ranked by the attacker's distance like ring
  slots; now the water's edge ranks first.
- Six Vindicators on one target: mean pairwise distance 1.42-1.52 blocks (was ~0.3), all six within 1.5 of it, six
  slot claims.
- Twelve units moved 24 blocks: 2.1 deep along the move x 4.0 across, closest pair 0.92, mean nearest neighbour 0.97
  (the test floors are 0.55 / 0.8: a vanilla path is done within ~0.45 of its last node, so 1-block cells cannot
  promise 0.8 between every pair; pushing keeps bodies 0.6 apart).
- Stuck nudge: the first run never nudged (`now - Long.MIN_VALUE` overflowed), fixed; see the latest run.
- Eyes scene 12 runs on the next main tour (the eyes workflow follows a green build on main; it was not dispatched
  from this branch, which would write the eyes branch).

