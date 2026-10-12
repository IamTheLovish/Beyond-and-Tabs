# walls-traps - progress (resume from the branch alone)

Task: walls + gates, traps, a T1 anti-raid tower and a T3 anti-experimental defence for all 7 factions
(design/defences.md is the design). Base: origin/main 30a1b6f.

## Done
- (nothing yet - plan below)

## Plan / next
1. building/buildings/defence/: WallSegment+WallPlacement, WallGate+GatePlacement, DefenceTower+DefenceTowerPlacement,
   Trap+TrapPlacement, DefenceCosts, Defences (registry of the 42 buildings, one set per faction).
2. Hooks: Buildings (static block at the end), Factions.registerBuildings, ResourceCosts bake, BuildingBlock fence-gate
   equivalence, piglin nether-ground exemption, MiscUtil wall aggro, FogOfWar canPlayerSeeBuilding (hidden traps),
   BuildingLineClientEvents (contiguous wall lines).
3. tools/defence_structures.py (called by gen_structures.py), tools/defence_icons.py (called by gen_hud_icons.py), lang.
4. bot/BotDefences.java + one call in BotPlayer.
5. gametest/DefenceGameTests.java.

## Known failures
- none yet
