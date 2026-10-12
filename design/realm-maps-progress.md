# realm-maps progress

Branch `realm-maps` (from origin/main d73acee). lovish's vision list #16 (map choice: biomes, nether, the end) and
#12 (reasons to explore). Design: design/maps.md (sections 4.7, 4.8, 3 "Forsaken Garrison").

## Plan
1. Two new pre-made maps built in the Overworld like the six existing ones (no dimension change):
   - **Soulfire Deltas** (Nether): soul-sand valleys along every boundary (slow ground, a terrain choice with a
     counter), lava lakes in the valleys with geothermal vents on their shores, crimson and warped fungus groves
     (reclaim), basalt cliffs and pillars, blackstone causeways, nether-brick ruins as capture sites.
   - **Chorus Isles** (End): end-stone islands joined by land necks (main routes) and railed purpur bridges, every
     land/void edge guard-railed (no void that eats units), chorus groves on scenery islets, obsidian-pillar sites.
2. New capture kind **GARRISON** (Forsaken Garrison) in CapturePointServerEvents: musters a map-specific recruit for
   its holder every 90 s while held and uncontested (max 2 of its recruits alive per holder), banks up to 2 while
   neutral (released on capture). Site styles (nether brick / obsidian) for the new maps.
3. MapLayout.startGround + StartPhase.inBox: no start on void / lava / deep water (fixes End Spires / Archipelago too).
4. GameTests in gametest/RealmMapGameTests.java; lobby pickers via lang keys; previews; design/maps.md.

## Done
- (nothing yet)

## Next
- offline harness for MapLayout (fairness, connectivity flood fill, previews as PNG)

## Known failures / unverified
- everything client-side (fog tint, labels, previews) unverified in game

## Proposals
- (filled as found)
