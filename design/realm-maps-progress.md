# realm-maps progress

Branch `realm-maps` (from origin/main d73acee). lovish's vision list #16 (map choice: biomes, nether, the end) and
#12 (reasons to explore). Design: design/maps.md (sections 1, 3 "Forsaken Garrison", 4.7, 4.8, 6).

## Done
- **Soulfire Deltas** (MapPreset id 7, Nether theme): soul-sand valleys on every boundary, lava lakes sunk 3 below
  their banks with geothermal vents on the shore (boundary line) + 2 private vents per base, crimson fungus groves on
  the banks and warped stands in the backfield (MapBuilder.growFungus, on nylium: TreeClearing fells them), basalt
  pillars, blackstone causeways and plaza, nether-brick garrisons (wither skeletons), bastion Forge in the plaza.
- **Chorus Isles** (id 8, End theme): home islands, land necks, lane islands (rich pair), centre island + dais
  (obsidian Watchtower spire), railed purpur bridges to garrison islets (obsidian pillars, endermen), chorus scenery
  islets, guard rails on every void edge (MapLayout.railAt + MapBuilder.railState: joined walls, 4-connected line).
- **Forsaken Garrison** (CapturePointServerEvents.Kind.GARRISON, appended): muster 90 s paced, cap 2 alive per
  holder, banks 2 while neutral, released on capture; paused while an enemy stands on it; Style (RUIN /
  NETHER_BRICK / OBSIDIAN) picks the ruin's look and the recruit; recruits tagged (RECRUIT_TAG) + KEY_RAISED_BY so a
  reload re-counts; onJoin checks scoreboard tags first (fixes R2-10); tickSite for tests; muster/bank synced in the
  site list (packet +2 bytes per site) and shown in the map labels; BotSites.worth case.
- MapLayout.startGround + StartPhase.inBox: no start on void / lava / deep water (fixes End Spires and Archipelago).
- Map pickers (lobby + skirmish) name maps through lang keys (MapPreview.nameOf/blurbOf, I18n fallback to the
  English name); previews colour soul sand, warped nylium, blackstone, purpur, groves.
- MapBuilder: buildArea (chunk or test window), rails, groves stage inside the per-tick budget, stampPillar,
  siteStyle, buildWindow (tests).
- gametest/RealmMapGameTests.java: fairness, ground connectivity (flood), builder windows (palette per column, rails,
  groves, geothermal on a vent / refused at a lake edge), garrison rules + reload, lang keys.
- Offline harness (scratch, not committed): MapLayout compiled with a stub Kind enum; all fairness/connectivity rules
  pass for every preset and n = 2..16; PNG previews inspected.

## Next
- wait for CI on the push that adds the tests; fix what it finds

## Known failures / unverified
- everything client-side is unverified in game: fog tints, labels ("2 waiting", "next recruit 40%"), the lobby
  previews and names, how the fungi / chorus / rails / obsidian pillars look from the RTS camera
- CI ea15c40: build job green; bar job (compile + GameTests) pending when this was written

## Proposals (outside this lane)
- **Pathing ignores soul sand**: vanilla WalkNodeEvaluator gives soul sand no malus, so units ordered across Soulfire
  Deltas wade the valley instead of taking the causeway. BAR's pathfinder weighs terrain speed; a mixin giving soul
  sand a path malus (e.g. 4) for ground units would make the valley a deliberate choice (waypoints) as in BAR.
- **Void tether** (Chorus Isles, End Spires): a once-a-second check that lifts a ground unit found on the void floor
  back to the nearest island edge would close the last way to lose a unit (an explosion lifting it over a rail).
  Needs the map's baseY kept in MapMatch (match-lifecycle's file).
- **End Spires rails**: End Spires has the same void without rails; its 5-wide bridges would drop to 3 walkable, and
  its diagonal bridges to ~2, so railing it needs its bridges widened to 9 first.
- **Chorus reclaim**: chorus is not a ResourceSource and TreeClearing does not fell it, so the chorus groves are
  scenery only (kept on air-only islets for that reason). Adding CHORUS_PLANT / CHORUS_FLOWER as WOOD (energy) and
  teaching TreeClearing a chorus "tree" would let End groves be reclaim like the Nether's fungi.
- **Hire menu**: RoN's capturable neutral End Portal building already offers endermen for a price; a map could place
  it as a "hireable special units" site (#12) once building capture is wired to the lobby maps.
- MapMatch's chat line "Forging <name>" stays English (server side); a translatable component would localise it.
