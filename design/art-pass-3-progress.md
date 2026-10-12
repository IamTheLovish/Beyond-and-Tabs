# art-pass-3 - progress

Branch `art-pass-3` (base origin/main 0882349). lovish: "intricate, detailed units and buildings, each with its own
individuality, readable from the RTS camera". Lane: textures / portraits / icons, skin tools, the Necropolis strike
placement, the beast renderers' decoration offsets. No unit stats or abilities change.

## Done
- Necropolis: the risen no longer stand on the target's roof. `NecropolisPlacement.riseCell` - the ring hugs the
  target's footprint (2 blocks out from its walls) and each risen takes the nearest cell searched from the target's
  ground level (never the heightmap) that is standable (not water/lava/fire), fits its body with no lava or fire in or
  beside it (`UnitUnstick.bodyFits`), is outside every building's box (`GameEnderPlacement.onBuilding`: no roofs,
  wall tops, courtyards), not taken by another risen and not a pocket. New public
  `UnitUnstick.findStandPos(..., Predicate)` overload (the private one with the accept test, made reachable).
  GameTest `ArtPassThreeGameTests.necropolis_host_rises_on_the_ground_round_a_roofed_target` (a stamped Machine Shop:
  9x9 roof over an open floor, plus a lava pool at a ring point).

## Next
- game-ender + Ward Spire icons, beast portraits, beast decoration check + preview, villager worker skins.

## Known failures
- none yet (CI not run).

## Proposals
- (filled in as found)
