# maps-startpos progress

Branch `maps-startpos` (from origin/main 9b69b0a). Design: design/maps.md.

## Done
- design/maps.md (map system, 6 maps, capture kinds, start phase, tests)
- maps/: MapPreset, MapLayout (pure + symmetric; verified offline with a scratch harness: equal mex per slot,
  equal distance, equal slot height for n = 2..16), MapBuilder (tick-sliced terrain, trees, pillars, vents,
  economy), MapMatch (Start -> build -> phase -> seat), StartPhase (+ live instance, server tick),
  MapStateClientboundPacket / StartPhaseServerboundPacket, MapClient (minimap boxes/picks/sites, world labels,
  banner, click to place, fog tint), MapPreview (skirmish lobby picture)
- MetalPatches values (saved, synced, extractor income x value, rich heart blocks, minimap tint)
- capture kinds BELL/WATCHTOWER/FORGE/VENT/OASIS (fog vision hook, ProductionItem research discount, vent
  energy, oasis heal), progress + claimant synced for "Capturing x%" labels
- lobby: Map + Starts options in both lobbies (SET_MAP / SET_STARTPOS appended), skirmish packet fields
- BattlefieldSetup: expectFormation + premadeLayout (skip procedural stamping, wall on the map edge)
- GameTests at end of SkirmishGameTests: premade_maps_are_fair_for_every_slot,
  map_capture_sites_spawn_with_their_effect, start_phase_places_everyone_in_their_box

## Next
- final CI on the reset/cancel polish commit

## Known failures / unverified
- CI run 38200454035 green (build + bar job incl. GameTests) at b020d43; job logs not readable from this sandbox (blob storage 403)
- in-game look of the maps unverified (no client here)
