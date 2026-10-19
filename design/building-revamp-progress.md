# Building revamp - progress (resume from here)

Task (lovish, Oct 19 2026, 03:20 ET): "start revamping all the buildings they need work ... all the buildings dont meet
our standard". "the metal extractors could be fountains with angels or something more creative". "use reference art to
get inspired and try to create articulate figures and buildings with that". Refine each one as far as it goes.

## The standard (lovish's art notes, "THE BUILDING STANDARD", Oct 17)
Every faction building - labs T1-T3, economy T1-T3, extractors, capitols, defences, walls - is built to the density of
his Minecraft references, not a box with a roof:
1. **Three depths on every wall**: a pier / buttress plane, the wall plane, a recessed window or door plane, 1-2 blocks
   apart. No flat face larger than 3x3.
2. **A broken top silhouette**: spires, pinnacles, finials, chimneys, crenels - the RTS camera sees roofs and tops first.
3. **Mixed materials per surface**: 3-4 blocks per material zone, stairs and slabs on every edge, walls / fences as
   mullions.
4. **Real architecture per faction**: White Angel (Sunforged) classical T1-T2 -> gothic T3; Death (Gravebound) dark
   gothic and blackstone; Verdant overgrown; Stormcallers towers and observatories; windmills for the wind generators.
5. **Dressing around it**: paths, gardens, fountains, lanterns, graves - walkable, never trapping units.
6. **Tiers look different**: T2 and T3 add storeys, spires and ornament to the same building.
7. **Lights**: lanterns and lit windows are the warm points at night and under shaders.

The check: `tools/structure_render.py` of the building (close and at RTS scale) beside the reference it draws on.
Inspiration only - never trace a build. Art and feel are lovish's call: every building lands as a before/after sheet
in `design/buildings/` for him to judge.

## Owner decisions (brainstorm, Oct 19 03:27-03:41)
- **Cohesion**: "I want the buildings to look like they match each other so when bases get bigger they look cohesive
  and beautiful". Each faction has a STYLE KIT - 3 main blocks + 1 accent, one silhouette motif, one trim, one light,
  one signature detail - and every building of the faction is built from it:
  - White Angel / Sunforged: white stone + quartz, gold trim, blue glass, lanterns; tiered domes.
  - Death / Gravebound: deepslate + blackstone, bone, soul lanterns, green glow; spires.
  - Ashborn (renamed from Piglins - must NOT resemble Reign of Nether's Piglins: no pigs, gold, gilded blackstone,
    nether brick, portals): basalt + tuff + obsidian, magma cracks, bronze trim, fire bowls; stepped volcano-temples.
  - Brassworks: copper (weathering) + dark oak, iron chains, smoke; pipes everywhere.
  - Stormcallers: jade green + dark oak, gold, paper lanterns; curved pagoda roofs.
  - Tidewrought: prismarine + sand, driftwood, sea lanterns; lighthouse towers.
  - Verdant: mossy stone + living wood, leaves, glow berries; roots and canopies.
- **Extractors, one per faction**: Sunforged fountain; Gravebound bone-ribbed well with chains over a green pit;
  Ashborn stone-giant smith hammering the ore over a vent; Brass steam drill; Storm jade spire with a lightning rod;
  Tide tide-pool pump with a waterwheel (T2 lighthouse pump tower); Verdant a tree wrapping the patch. Every one
  shows one glowing ore core from the RTS camera.
- **Capitols**: "a lot bigger than the current capital size, a lot taller, a lot more detailed", "the center piece of
  the base" that everything connects to and flows from - "don't break the game". No fixed paths (players build
  anywhere): the plaza edge blends on every side. Saved for later: paths laid by the game as buildings are placed.

## Rules learned from the code
- Footprints are fixed by the building classes (an extractor is 5x5); a level may grow taller, never wider.
- Tier 2 may only add or replace blocks (`check_mex_tiers`, `gen_lab_levels.py` asserts the same for labs);
  waxed copper is the extractor's tier-2 marker (MetalExtractor.getUpgradeLevel).
- Regenerating `tools/gen_structures.py` rewrites some untouched .nbt byte-differently: commit only the files that
  really changed (git checkout the rest).
- Real water is allowed in a structure (Reign of Nether's Healing Fountain does it; BuildingPlacement clears fluids
  first when a building falls): sources only, walled on every side.
- A drawn moving part (BuildingParts.RIGS) pins its structure's air cells: change the structure, change the row and
  `tools/check_parts.py`.

## Done
- **Sunforged metal extractor -> the Angel Fountain** (T1 + T2), `design/buildings/sunforged_metal_extractor.png`.
  A stepped quartz basin of still water with lapis thresholds and a gold sun under the water; candle pedestals on the
  corners; a column with a bowl and four falling sheets (light-blue panes); a white angel on it - flared robe, quartz
  shoulders, wings spread behind as two fans - lifting the glowing sun over its head on thin end-rod arms (the
  Sunforged emblem, its halo and the night light at once). T2: copper in the parvis corners (marker), the candles
  grown into gold-capped lantern posts, lit wing tips, a crown of rays over the sun. Its drawn headframe machinery
  (sheave wheel, skip, pennant) is gone with the headframe. The basin's corners are lit sea lanterns, not open water:
  open corners were pockets a unit could not climb out of (audit_structure_pockets.py).
- **Verdant metal extractor -> the Root Spring / Elder Spring** (`tools/mex_verdant.py`),
  `design/buildings/verdant_metal_extractor.png`. A ruined mossy well with still pools over the ore, broken pillars,
  candles; a living oak grown out of its back rim, roots clawing the patch, its trunk a stag-dryad head (birch mask,
  glowing eyes) with antler branches. T2: a flowering crown with a shroomlight heart and glowing fruit, a lantern post
  regrown, the ore under the water a copper vein (the marker).
- **Brassworks metal extractor -> the Iron Golem Drill** (`tools/mex_brass.py`),
  `design/buildings/brass_metal_extractor.png`. A squat brass automaton gripping a screw drill bored into the ore, a
  glowing furnace belly, eyes behind an iron visor, weathered copper plates, a soot boiler on its back with a smoking
  stack. T2: a second stack, a cog crown, shoulder lanterns, a copper pressure dome (marker), a lit firebox. Open
  critique: the head is small beside the stacks - a later pass could enlarge the helm.
- **Stormcallers metal extractor -> the Jade Thunder Spire / Storm Needle** (`tools/mex_storm.py`),
  `design/buildings/storm_metal_extractor.png`. A slim jade pagoda spire on dark-oak legs whose lightning rod is the
  patch's highest point; the strike has crystallised the ground - amethyst round a glowing core on a gold seam; roofs
  stepping 5-3-3 with gold corners and upturned tips, chimes under the eaves, lantern posts. T2: a third roof, eight
  eave rods, more chimes and crystal, a copper band (marker). (The renderer draws amethyst buds as cubes; in game they
  are small crystals.)
- **Ashborn (Piglins) metal extractor -> the Stone Giant** (`tools/mex_ashborn.py`, variant `_nether`),
  `design/buildings/ashborn_metal_extractor.png`. A hooded basalt giant, the Ashborn's first smith, built waist-deep
  into a stepped volcano forge: right fist raising a bronze hammer high over the anvil, left fist holding the glowing
  ore in chain tongs, a glowing face under the hood, magma cracking his chest and cracks of glow across the floor, fire
  bowls on obsidian. T2: a spiked bronze crown and shoulder plates, four fire bowls, more cracks, a bronze anvil band
  (marker). Nothing of RoN's Piglins (no pigs, gold, gilded blackstone, nether brick).
- **Sunforged capitol (Town Centre) -> the Temple of the Sun**, 15 x 20 x 15 (was RoN's 11 x 9 x 11), 1590 blocks
  (`tools/capitol_town_centre.py` build_grand(), written by gen_capitol_details.py), `design/buildings/sunforged_capitol.png`
  (the compact 11x9x11 fallback build() is drawn there too). The same on all four sides: a stepped stylobate, a
  quartz-and-lapis cross to a gold sun at the heart, quartz columns with gold capitals framing porticoes, blue lancets,
  doors on every axis from the spawn cell, a stair cornice, a balustrade with candles, a drum of pillars and blue glass,
  tiered dome to a gold crown, corner pinnacles with spires and banners, angels on the plaza corners, and a garden edge
  (grass, moss, calcite flags, flowers) that fades into the ground on every side. The Sunforged style kit is written in
  its docstring for the other Sunforged buildings. Java: the GameTest box, the glint spots, the wildlife test's pad, and
  BuildingPlacement's per-block pacing now timed by buildTimeBlocks - the capitol laid at a readied start (command-
  placed) took longer for every detail block (it would have been ~80 s instead of ~16 s).
- **Gravebound metal extractor -> the Bone-Ribbed Well / Ossuary Well** (`tools/mex_grave.py`),
  `design/buildings/gravebound_metal_extractor.png`. Great bone ribs bow out of a deepslate-and-blackstone grave plot
  and curl over a green-glowing well (the ore core); the back pair closes into a bone arch with soul fire; the drawn
  bone wheel turns between the ribs and hauls a bone bucket up the middle (the rig kept, on new anchors: gen_structures
  GRAVE_RIG_ANCHORS = mex_grave.ANCHORS, BuildingParts.RIGS); gothic spirelets with soul lanterns, skulls on the rib
  tips, chains. T2: the whole pit glows, the arch rises three blocks, longer ribs, more chains, taller spires, a
  gargoyle; a copper band on the winch (marker).
- **Gravebound capitol (Mausoleum) -> the Necropolis Cathedral**, 15 x 22 x 15 (was 10 x 13 x 10)
  (`tools/capitol_mausoleum.py`, written by gen_capitol_details.py), `design/buildings/gravebound_capitol.png`. A dark
  gothic cathedral on a two-block crypt podium (pier - niche - pier with skulls; a grand stair on every side between
  soul-lantern cheek walls); a cross plan whose four arms end in gothic fronts (pointed portal between bone posts,
  green rose window round a verdant froglight, steep tile gable); four corner towers with needle spires and
  gargoyles; eight flying buttresses with lit pinnacles and hanging chains; a crossing tower on bone ribs with the
  open soul belfry (the swinging bell, CapitolLife) under the tallest spire; an ossuary garden of headstones, bones and
  grave lights fading out on every side. Java: the GameTest box, CapitolLife's bell cell, the soul-glow spots.
- **Tidewrought metal extractor -> the Tide-Pool Pump / Lighthouse Pump** (`tools/mex_tide.py`),
  `design/buildings/tide_metal_extractor.png`. A walled tide pool on a prismarine-and-sandstone kerb over the glowing
  ore, a big upright driftwood waterwheel (mangrove rim, dark-oak paddles, chain diagonals, copper hub) facing the
  camera, a striped pump house, a ship's-mast crane hanging a chain over a timber chute into a salvage barrel, sea
  lanterns and dead coral. T2: the pump house rises into a striped lighthouse with a gallery, a cyan-glass lamp room
  round a sea lantern and a copper cap (marker). Open critique: the ore core is modest; the crane reads a little like a
  gallows from some angles.
- **Brassworks capitol (Foundry Hall) -> the Grand Foundry**, 15 x 22 x 15 (was 11 x 12 x 11)
  (`tools/capitol_foundry_hall.py`, gen_structures.foundry_hall), `design/buildings/brass_capitol.png`. A soot plinth
  and a yard of cobbles, rails, crates and coal that breaks into the ground on every side; a 9 x 9 hall the same on
  all four faces (corner towers, furnace bays with lit blast furnaces under tall orange windows, dark-oak buttresses
  with copper caps and lanterns, gabled portals with grilled round furnace windows); a dark-oak nave with iron
  trusses; three smokestacks stepping up to the back with smoke on top, a copper pressure dome on the camera corner;
  pipes everywhere; a crane; a copper clock tower with a great gear on every face under a catwalk and a riveted dome;
  four brass automatons (the Iron Golem Drill in miniature) guarding the yard. Java: steam/ember spots, the class doc,
  a box row in the capitol GameTest.
- **Tidewrought capitol (Wreck Harbour) -> the Admiral's Beacon**, 15 x 22 x 15 (was 11 x 10 x 11)
  (`tools/capitol_wreck_harbour.py`, gen_structures.wreck_harbour), `design/buildings/tide_capitol.png`. A giant
  galleon hauled ashore, prow to the south: barnacled bilge, mangrove planking, sea-lantern portholes between ribs, a
  rope rail; the hold is the hall (a gate cut through each side, units spawn under the cargo hatch); two masts with
  torn blue-striped sails and chain shrouds; a stern castle with blue windows and the striped lighthouse rising from
  it as the base's tallest point (gallery, sea-lantern lamp room, vane); a sea-maiden figurehead lifting a sea lantern;
  anchors with chain cables; a still-water dock with a gangplank (west) and a salvage yard (east); a shore that fades
  into sand, clay, coral rubble and driftwood on every side. Java: buildTimeBlocks = 243 (it had none: the art would
  have built ~4x slower), glint and splash spots, a box row in the capitol GameTest.
- **Stormcallers capitol (Jade Pagoda) -> the Pagoda of a Thousand Storms**, 15 x 22 x 15 (was 11 x 14 x 11)
  (`tools/capitol_jade_pagoda.py`, gen_structures.jade_pagoda), `design/buildings/storm_capitol.png`. Five curved jade
  roofs (13-11-9-7-5 wide; edges dip and sweep up to gold corners with upturned tips) over red-lacquer storeys with
  paper screens; 20 eave chimes; a hall with a door on every side inside a red-columned verandah; a crown of lightning
  rods round a floating amethyst storm ring; a torii, stone lanterns and blossom trees on every side, jade guardians
  on the corners, a raked-stone and moss garden fading out. Java: buildTimeBlocks = 494 (it had none), spark spots,
  a box row in the capitol GameTest.
- BiomePalette decisions added for the new blocks: Verdant ruin bricks, amethyst crystals, Tide chiseled sandstone,
  Storm lacquer mangrove / moss / lawn (ground rules), Brass yard ground and rubble (ground rules). The offline
  emulation of biome_palette_covers_every_faction_block (checks each building's blocks against its faction table) is
  worth keeping in mind: run it before pushing a building with new block families.
- **Verdant capitol (Heartwood Hall) -> the World-Tree Court**, 15 x 22 x 15 (was 11 x 13 x 11)
  (`tools/capitol_heartwood_hall.py`, gen_structures.heartwood_hall), `design/buildings/verdant_capitol.png`. A ruined
  mossy temple hall, the same on all four sides (piers, lancets, pointed arches, a broken parapet spilling leaves),
  with a world tree grown up through it: four oak legs in the inner corners lean into one trunk over the crossing (its
  hollow heart is the spawn, lit from above); a root bursts through each corner pier and arches like a flying buttress
  over a moonwell (a water cauldron with candles); a stag-dryad face on all four sides of the trunk with birch antler
  boughs; layered canopy tiers to a cherry-blossom crown with a shroomlight heart and glowing fruit; a garden of moss,
  grass, podzol, stepping stones, ferns and flowers fading out. Java: petal spots, a box row in the capitol GameTest.
- World-Tree Court fix: the moonwell kerbs lost their lime candles - a Seedshaper leaving by a corner stood on a
  candle and could not step off (heartwood_hall_frees_its_seedshapers). Rule learned: no candles, pickles or other
  part-height blocks on a walkable kerb along a way out; put small lights where units do not path.
- **Gravebound T1 lab (Graveyard) -> the Ossuary Yard / Catacomb Gate / Bone Spire** (`tools/lab_graveyard.py`, wired
  in gen_lab_levels.LABS; it also draws the L1 and the Overflowing Graveyard), `design/buildings/gravebound_graveyard.png`.
  L1 8 x 9 x 8 (217 blocks): a railed grave plot with a bone gate and a gargoyle, headstones and candles, a bone
  ossuary wall and a stela as the skull racks, a corner crypt with its door to the camera. Overflowing: tiled kerb,
  bones in the soil. L2 8 x 13 x 8 (330): two roofed crypt wings with skull niches, chained soul lanterns, a gatehouse.
  L3 8 x 21 x 8 (435): a bone-ribbed tower with green lancets and a soul-fire needle, a chapel with a green rose window,
  a flying buttress. Fixed on the way: the old L2/L3 built over 8-9 of the 20 overflow skull cells. Java:
  buildTimeBlocks = 99 (RoN's graveyard). Rule: any graveyard level keeps the 20 skull cells
  (GraveyardPlacement skullPoses / wallSkullPoses) air, and overflow is read from full deepslate_tiles /
  chiseled_deepslate only.
- **Sunforged T1 lab (Barracks) -> the Training Hall / Knights' Cloister / Cathedral of Arms** (`tools/lab_barracks.py`,
  wired in gen_lab_levels.LABS; it draws the L1 too), `design/buildings/sunforged_barracks.png`. The range stands along
  the north and faces south across a drill yard to the camera; the bell turret is the west end. L1 10 x 15 x 8 (320
  blocks): a colonnaded hall on a stylobate (quartz columns, gold capitals, lapis triglyphs), a small tiered dome on a
  blue-glass drum, a bell turret with an angel, a sand drill yard with a gold sun, a pell, lances and banners. L2
  10 x 16 x 8 (508): open domed pavilions on the yard's front corners, arcaded cloister walls with a gate, a taller
  dome. L3 10 x 25 x 8 (761): a rose-window gable to the camera, a crossing tower, flying buttresses, a staged belfry
  spire with a gold angel (kept under the capitol's height on purpose). Java: buildTimeBlocks = 117 (RoN's barracks);
  BiomePalette keeps the drill-yard sand (SAND_FLOOR) for the Sunforged. Rule learned: on an even footprint the spawn
  column is any of the four centre cells (the centre is truncated in world coordinates, and rotation moves it), and an
  upgrade keeps the L1's spawn height - keep all four centre columns open to the sky at every level.
- **Stormcallers T1 lab (Lotus Academy) -> the Lotus Court / Hall of Winds / Thousand-Lotus Pagoda**
  (`tools/lab_lotus_academy.py`; gen_structures.lotus_academy and gen_lab_levels both take it),
  `design/buildings/storm_lotus_academy.png`. L1 9 x 9 x 9 (320 blocks): a hall on a podium with red posts, paper
  screens and one curved jade roof with gold corners and chimes, a red torii, a jade path to a lotus mosaic, a lotus
  sculpture (cherry petals round a glowing heart) on the camera corner, a blossom tree. L2 9 x 15 x 9 (435): a
  double-eaved hall, a roofed torii with two dragon-headed jade guardians. L3 9 x 23 x 9 (529): four curved roofs
  stacking to a gilded lotus crown. Java: buildTimeBlocks = 181 (it had none). Also fixed: the Pagoda capitol's upper
  blossom leaf had distance 1 on a leaf (true 2); harmless (BuildingBlock.isPlaced ignores leaf states), just tidy.
- **Brassworks T1 lab (Machine Shop) -> the Machine Shop / Engine Works / Clockwork Foundry**
  (`tools/lab_machine_shop.py`; gen_structures.machine_shop and gen_lab_levels both take it; assembly_works and
  great_forge keep brass_lab), `design/buildings/brass_machine_shop.png`. An L of buildings round a works yard open to
  the camera. L1 9 x 13 x 9 (417 blocks): a dark-oak and copper shop range with lit furnace bays, a taller engine house
  carrying the Grand Foundry's gear, a smokestack, a copper turntable, rails, crates and a golem being assembled on its
  stand. L2 9 x 16 x 9 (492): a sawtooth roof with orange glass, twin stacks and a pipe bridge, a riveted boiler, a jib
  crane holding the golem's helm. L3 9 x 20 x 9 (626): a clock tower on the camera corner with two gear dials, taller
  stacks, a catwalk, the golem finished with a boiler pack and crown. Java: buildTimeBlocks = 216 (it had none).
- **Ashborn (Horde/Piglins in code) T1 lab (Military Portal) -> the Ember Gate / Crater Forge / The Giant's Gate**
  (`tools/lab_portal_military.py`, wired in gen_lab_levels), `design/buildings/ashborn_military_portal.png`. The Ashborn
  kit (basalt, tuff, a little obsidian, bronze trim, glowing cracks, fire bowls, the Stone Giant) - no nether brick or
  gold. The portal stays (frame cells, opening and passages untouched; nether-brick starting cells kept under it) and
  becomes the fire door of a volcano. L1 8 x 7 x 5 (132): stepped basalt flanks, a bronze lintel, fire bowls, glowing
  cracks. L2 8 x 11 x 5 (193): a stepped cone with a glowing crater over the gate, an anvil-stone with the ore. L3
  8 x 20 x 5 (322): the Stone Giant rises waist-deep from the crater, hammer raised, the glowing ore hung from his fist.
  Java: buildTimeBlocks = 50. Rule: the portal's L1 stays at most 7 high (its centre - where the portal is lit - is
  computed from the L1 box). Known nit, not fixed: after the Basic -> Military research the box keeps the basic
  portal's 6-block height, so the L1's top layer sits just above the box until the L2 (the Transport Portal has the
  same; nothing breaks).
- **Tidewrought T1 lab (Slipway) -> the Slip / the Fitting-Out / the Launch** (`tools/lab_slipway.py`;
  gen_structures.slipway and gen_lab_levels both take it), `design/buildings/tide_slipway.png`. The ship lies along the
  north with its bow west, a striped crane house in the far corner, a walled launching basin along the south. L1
  9 x 13 x 9 (274): a half-built hull of bare red ribs on keel blocks, a striped crane house lowering a timber, rails
  to the basin, a quay with sea-lantern corners and a rope boom. L2 9 x 16 x 9 (387): the hull planked in the capitol's
  bands with glowing portholes, the mainmast with a furled sail, a stern castle. L3 9 x 21 x 9 (500): the sails set,
  the sea-maiden figurehead with a sea lantern, cannons, the crane house a full striped lighthouse. Java:
  buildTimeBlocks = 141 (it had none). Rule: a rope boom (fence) over each water cell fills its headroom, so the
  grid never counts water as walkable ground - nothing steps in, nothing is stranded.
- **Verdant T1 lab (Grove) -> the Stag Glade / the Rooted Court / the Elder Grove** (`tools/lab_grove.py`;
  gen_structures.grove and gen_lab_levels both take it), `design/buildings/verdant_grove.png`. Buildings along the north
  facing south over an open glade (open west and south: the way out). L1 9 x 14 x 8 (307): the Mother Tree between two
  ruined shrine piers, its trunk the stag-dryad's head looking down the glade (birch mask, glowing eyes, birch antlers
  over a leaf cloak), a froglight floor in a calcite ring, a moonwell, the Thornbows' butts. L2 9 x 18 x 8 (442): the
  piers rise into an owl tower and a glowing-hive tower, root arches, the first canopy tier with glowing fruit. L3
  9 x 24 x 8 (529): a thick trunk to an upper tier and a cherry crown, the antlers climbing into a crown of tines.
  Java: buildTimeBlocks = 287 (it had none).
- Rule learned (bot soak, Oct 19: the Pagoda stalled at 1541/1542 blocks): grass or mycelium under a full block dies
  to dirt on a random tick, long after it is placed, and the building then counts that cell missing forever. No grass
  under full blocks: `_SunRig.bury_grass()` swaps such cells for moss (used by the Pagoda and the grove); check with
  scratchpad grasscheck (every shipped structure is clean now).
- Each revamped extractor is its own module (`tools/mex_<key>.py`, t1()/t2()), wired by gen_structures.REDESIGNED_MEX.

## Next (in this order; check lovish's reference folder for new art before each)
1. All seven extractors done. Six capitols done (all but the Ashborn, which waits for the Ashborn rename);
   then the rest of the capitols at the same scale (the Temple of the Sun is the bar).
2. Later: the Angel Fountain could take the kit's blue glass and a tiered dome; the Brass golem's helm is small.
3. All seven T1 labs done (Oct 19 morning). Next: the T2/T3 labs, then the rest of the economy (wind generators,
   converters, vaults), then defences.
