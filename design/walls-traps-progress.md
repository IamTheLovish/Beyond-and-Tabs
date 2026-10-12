# walls-traps - progress (resume from the branch alone)

Task: walls + gates, traps, a T1 anti-raid tower and a T3 anti-experimental defence for all 7 factions.
Design: design/defences.md. Base: origin/main 30a1b6f.

## Done
- building/buildings/defence/: DefenceBuilding (base + build button), WallSegment(+WallPlacement), WallGate(+GatePlacement),
  Trap(+TrapPlacement), DefenceTower(+DefenceTowerPlacement), DefenceCosts, DefenceStyle, Defences (42 registrations).
- Hooks: Buildings (static block at end), Factions.registerBuildings, ResourceCosts bake, BuildingBlock gate/plate
  equivalence, BuildingValidators piglin exemption, MiscUtil wall aggro, FogOfWar canPlayerSeeBuilding + Concealment,
  BuildingLineClientEvents wall lines.
- Structures (84 nbt: 42 x assets/data) via tools/defence_structures.py; icons (42) via tools/defence_icons.py; lang (126 keys).
- bot/BotDefences.java + one call in BotPlayer.think.
- gametest/DefenceGameTests.java (4 tests).

## CI
- fe2b444: build + bar (compile, all GameTests incl. the 4 DefenceGameTests, bot soak) green.

## Next (not started)
- Per-faction mechanical twists (hedge regrowth, steam gates), a real choke finder for the bot wall, saving a trap's
  revealed flag. See design/defences.md "Not done / next".

## Known failures
- none
