# command-queue progress (Reign of Nether rewrite #2, first slice)

Branch `command-queue`, base origin/main d73acee. Brief: per-unit BAR command queue replacing RoN's two global action
lists (design/ron_rewrite_plan.md, rewrite 2).

## Done
- Read the plan, code_review.md, the old pipeline (UnitServerEvents slow/fast lists, UnitActionItem switch,
  UnitActionServerboundPacket, UnitQueueSync) and every caller (bot/, hero/PossessionServer, onboarding hook,
  ProductionPlacement rally, AreaCommands, WreckServerEvents chain, BuildingServerEvents, survival WaveEnemy,
  commands/ debug commands, client UnitClientEvents / MinimapClientEvents).
- Baseline: main d73acee CI `[Stress]` notice: queue lines 0.187 ms, total 2.169 ms (REF builder lookup 2.821 ms).
- Step 1: behaviour pins in `gametest/CommandQueueGameTests.java` (must pass on the OLD pipeline): queue cap 32 in
  order, shift order + plain replaces, removed target skipped, non-owner packet refused, production rally in order,
  fire-state toggle keeps the queue.

## Next
- CommandQueue / CommandQueues / UnitCommand / CommandKind (unit/command), switch UnitServerEvents adapters,
  UnitActionServerboundPacket queue mode (Ctrl = insert front), UnitQueueSync from the per-unit queue, AreaCommands
  on the new API, Unit.isIdle cheapest-first.
- New-behaviour tests + `[Stress] command queue` benchmark (own batch), plan doc "Rewrite 2".

## Known failures
- none yet

## Proposals
- (filled in as found)
