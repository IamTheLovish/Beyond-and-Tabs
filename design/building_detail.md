# Building detail pass (Oct 12)

lovish: buildings must be *"intricate, detailed, complex, each with its own individuality"*; *"use fences, trapdoors,
slabs layered to create dimension, lighting with torches/lanterns to simulate the game coming to life"*; *"upgraded
buildings can change in shape or fundamental design ... make upgraded buildings stand out for the opponent"*;
*"biome-aware like villages"*.

Tools: `tools/gen_lab_levels.py` (lab L2/L3), `tools/gen_structures.py` (economy, capitols),
`tools/preview_structure.py --rts` (now draws slabs, stairs, fences, walls, lanterns, banners, trapdoors, carpets
with their shapes, so detail reads on a contact sheet), `tools/audit_structure_pockets.py`. Contact sheets:
`design/building_preview_*.png` (the RTS camera's side: it looks from the south-west, so the back (+z) and west (-x)
faces and the roofs).

## 1. Lab levels: each faction's L2 and L3 transform its lab

Before: every L2 was the L1 plus four corner pillars and battlements, every L3 the same plus a spire, in faction
colours. Now each faction draws its own levels (`building_preview_<lab>.png`, L1 | L2 | L3):

| Lab | L2 | L3 |
|---|---|---|
| Sunforged Barracks | fortified keep: four 2x2 stone towers with quartz bands and battlements, curtain walls, a gatehouse with a gold-crowned arch, yellow/white banners, the bell hung on the wall | cathedral-barracks: a quartz chapel over the back of the yard with a slate gable, a yellow rose window under a gold sun disc, a 3x3 bell tower (open belfry with the bell, hip roof, lightning rod) on the corner the camera sees first, gilded turrets on the front towers, a flying buttress |
| Gravebound Graveyard | mausoleum ring: four deepslate crypts with dark tile pyramids and soul-lantern finials, a railed wall with soul-lantern posts, skulls | necropolis: a crypt hall across the back with crying-obsidian doors, a bone spire with soul fire, a bone gate with chains and skulls at the front, soul braziers |
| Horde Military Portal | war camp: two blackstone bastions flanking the (untouched) portal frame, fence spikes, braziers, piglin-head trophies, red and black banners | war-fortress: the bastions rise with shroomlight bands, bone horns, a nether-brick lintel and gilded crown with magma eyes over the portal |
| Verdant Grove | three crowns: the back post becomes a tree, two silver birch sentinels break through the canopy, a mangrove-root arch over the entrance, a canopy bridge with lanterns | the elder tree: a thick trunk rising to a wide flowering crown with froglights, branches, buttress roots, an owl perch |
| Tidewrought Slipway | a hull on the slip: timber hull with a copper waterline, deck rail, a mast with a square sail and pennant, the crane hoisting a cargo barrel | war galleon: stern castle with lanterns, gun ports (dispensers), a foremast with a second sail, a crow's nest and topsail, a copper figurehead with a sea-lantern eye, a lighthouse lamp on the crane |
| Stormcallers Lotus Academy | pagoda hall: a jade hip-roofed hall on red posts over the back of the courtyard (open underneath), gold upturned eaves with lightning rods, a second roof tier on the gate, lantern posts | jade pagoda: three shrinking red-and-jade tiers with a glowing core rising to a gold finial and lightning rod, drum towers flanking the gate |
| Brassworks Machine Shop / Assembly Works | workshop: twin brick-and-brass smokestacks, a sawtooth north-light roof (orange glass and brass), steam pipes with valves on the camera's eave | engine house: a soot-and-brass clock tower with orange-glass faces round a gold hub, a boiler dome with a valve, a gantry crane with a chain and hook, taller stacks with furnace bands |

Rules every level keeps (asserted by `Canvas.build()` or checked by the tools):
- **Footprint unchanged.** `BuildingLevels` has no larger-footprint support (and the GameTest
  `lab_level_structures_ship_and_have_no_pockets` fails a changed footprint); levels grow upward (L3s reach 14-18).
- **Only adds or replaces.** `BuildingPlacement.changeStructure` + `refreshBlocks` place what is missing and never
  remove anything, so every non-air cell of the level below stays non-air in the next level (L1 -> L2 -> L3). A base
  block in a new gateway becomes a carpet, a fletching table in a wall becomes stone.
- **Nothing pops off.** Standing lanterns / torches / banners / carpets need a floor, hanging lanterns a ceiling, wall
  torches / banners / bells a wall (`Canvas.unsupported()`).
- **States stay as placed.** Leaves get their true distance to a log (`Canvas.fix_leaves`), no growing plants, no
  hay under a campfire.
- **Pocket-free.** Towers and wings are solid; halls are open pavilions or have their yard open to the gate. The spawn
  point is the L1 centre (`centrePos` is never recomputed on an upgrade), which every design keeps clear.
- Level structures don't change build time (an upgrade is a production order); their HP comes from `HP_MULT`.

## 2. Economy and capitols: audit

Audit of every faction's economy buildings and capitol (rendered with the shaped preview):

| Faction | Extractor T1 / T2 | Energy converter | Storage | Capitol | Verdict |
|---|---|---|---|---|---|
| Sunforged | bespoke chapel drill / bell-tower drill | bespoke crucible | bespoke treasury | Town Centre (RoN art) | detailed already |
| Gravebound | bespoke ribcage / spire | bespoke soul furnace | bespoke ossuary | Mausoleum (RoN art) | detailed already |
| Horde | bespoke rig / tower | bespoke smelter | bespoke hoard | Central Portal (RoN art) | detailed already (capitol is RoN's) |
| Verdant | bespoke living wood | bespoke campfire | **generic box** | Heartwood Hall | vault plain; hall plain from afar |
| Tidewrought | **generic derrick / copper post** | **generic box** | **generic box** | Wreck Harbour | economy plain |
| Stormcallers | **generic derrick / copper post** | **generic box** | **generic box** | Jade Pagoda | economy plain |
| Brassworks | **generic derrick / copper post** | **generic box** | **generic box** | Foundry Hall | economy plain; hall a flat dome |

The palette-driven generic builders gave Tidewrought, Stormcallers and Brassworks the same box in different colours.
Changed (`building_preview_economy_<faction>.png`: old T1 | new T1 | new T2 | converter | vault;
`building_preview_capitols.png`: before | after pairs):

- **Tidewrought**: *dredge rig* (ship-timber frame over a dark-prismarine well, pulley beam, chain and cauldron
  bucket, rope barrel with a lantern, dead coral, teal pennant) -> T2 *tide pump* (a waxed copper pump tower through
  the pulley with a prismarine cornice and a sea-lantern head, pipes, prismarine lamp posts); *soul-fire galley stove*
  (firebox door, grates showing the teal flame, copper boiler drum and domed lid, stovepipe with soul smoke);
  *chandlery* (barrels under crate-lid trapdoors, kelp bales, a sailcloth awning, lantern, pennant).
- **Stormcallers**: *ley drill shrine* (red posts, jade hip roof with upturned gold eaves, coffered ceiling, a lodestone
  drill on a chain, gold finial and lightning rod, lanterns, red banner) -> T2 *two-tier pagoda drill* (a red tier with
  green-glazed panels and a glowing core, its own jade roof, lightning rods on the eaves); *Mana Condenser* (soul
  flame under a chain, jade roof with gold corners, a glowing tier and a lightning-rod tip); *Mana Cistern* (emerald
  store behind green-glazed panels, jade hip roof, gold cap, lantern).
- **Brassworks**: *steam drill* (soot gantry, piston hammer driving a lightning-rod bit through a brass head, a boiler
  with its firebox facing the camera, brick stack with smoke) -> T2 (riveted brass housing with a valve, a taller
  stack with a furnace band, a second drum and a feed pipe); *pressure boiler* (two lit fireboxes, grates, riveted
  drum, dome, valves on soot posts, stack); *strongbox* (iron door, barrels, raw copper, brass band and lid, lantern).
- **Verdant**: *root cellar* vault (oak posts, barrels, stripped-oak bands, seed bales, a moss roof with a flowering
  azalea, lanterns).
- **Foundry Hall**: a second brass-banded stack on the back-west corner (twin-stacked foundry), lanterns on the deck,
  a gold-and-shroomlight gear medallion on the back wall.
- **Heartwood Hall**: the great trunk breaks through the dome into a small flowering crown with a froglight heart,
  silver birch bands on the posts, calcite moonwell posts with lanterns on the four corners.

Rules: footprints unchanged; T1 extractors hold no `copper_block` / `cut_copper` / `waxed_copper_block` and every T1
block position survives into the T2 (`check_mex_tiers`, because the tier upgrade is also a `changeStructure`);
nothing hangs; copper is waxed; coral is dead. **Build times unchanged**: extractors, converters and vaults have a
fixed `buildTimeBlocks`; the two capitols whose block count grew now set `buildTimeBlocks` to their old count
(Foundry Hall 428, Heartwood Hall 498).

Biome-aware: `BiomePalette` already swaps stone bricks / cobble / timber trim and caps tops per biome for every
building, levels included, so the new designs lean on stone bricks, spruce/oak trim and full-block tops that it knows.

Not changed: the RoN capitols (Town Centre, Mausoleum, Central Portal) are hand-made RoN structures, not generated
- a detail overlay for them is a follow-up; wind generators and the Geothermal Plant (their ambience emitters are
tied to block positions in `BuildingAmbienceClientEvents`).
