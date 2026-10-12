# Lively buildings

lovish (Oct 12): *"I really want the buildings to feel lively in a Minecraft setting; right now they lack variables
that give them individuality. Windmills don't need to be windmill-shaped - they can come in different shapes and
contrasts, like a tree that spawns flowers. Buildings should relate to the faction. Use fences, trapdoors, slabs layered
for dimension, lighting with torches/lanterns ... A good starting point: villages that change with the biome."*

This slice does four things:

1. every faction's **energy building** (the `WindGenerator` variants) gets its own shape and its own moving part;
2. **ambient life**: capitols, energy buildings and labs breathe near the camera;
3. **biome-aware palettes**, slice 1: placed buildings take snow / sand / moss touches from the biome under them;
4. a shared **Geothermal Plant** that only stands over lava.

## Ground rules

- **Footprints don't move.** Energy buildings stay 3x3 (the placement / slope / metal-patch rules and the bot's
  layouts assume it); HP, cost, build time and income are unchanged.
- **Vanilla blocks only.** Macaw's, Supplementaries, Chipped and Epic Knights are in lovish's pack but *not* on the
  headless GameTest classpath (only Alex's Mobs + Citadel are, `bar/build.gradle`). A mod block in a structure loads as
  air there, so the pocket and blueprint tests would check a different building from the one players see. Vanilla 1.20
  has everything this needed: stairs, slabs, walls, fences, chains, lanterns, candles, trapdoors, froglights, cherry
  leaves, lava cauldrons, lightning rods (they don't weather), waxed copper.
- **The camera sees -x and +z.** The RTS camera looks north-east from the south-west, so the "show" details (the spirit
  heart, the waterwheel, the flywheel, the firebox) go on the west and back faces. Sails stay on the -z front, tilted
  back over the tower so the camera sees them.
- **Moving parts are rendered, not blocks.** `WindmillRenderClientEvents` draws them from declared anchors
  (`WindGenerator.SAIL_HUBS` / `MOTIONS`): built-only, never in strategic zoom, distance-culled (220 blocks, 96 for
  the small bell and petals), no per-frame allocation, no block updates, no server cost.
- **Pocket-free.** `tools/audit_structure_pockets.py` is clean for all eight new structures; the GameTests are the truth.

## Energy buildings: three concepts per faction

Scores 1-5: **Identity** (reads as this faction), **Silhouette** (distinct at strategic zoom from the other six),
**Detail** (layered depth, night light, something to look at up close), **Perf** (render cost: block count, moving
parts, particles). The pick is in bold.

### Sunforged (gold / white cathedral) - variant ""

| Concept | Identity | Silhouette | Detail | Perf | Notes |
|---|---|---|---|---|---|
| **Gilded sun mill** | 5 | 4 | 5 | 4 | white quartz belfry, gilded sail spars, a sun disc crowning it |
| Sunlit lighthouse | 4 | 3 | 3 | 5 | beacon-like lamp tower; static, reads like the Tidewrought lighthouse |
| Prayer-flag shrine | 3 | 2 | 4 | 4 | low, flags lost at zoom, not cathedral enough |

**Pick: the gilded sun mill.** The Sunforged *should* keep the classic windmill - it is the "kingdom" faction, and the
other six now break the mould around it. What makes it theirs: white canvas on **gold** spars, blue lancets lit from
inside by a glowstone heart, a quartz corbel under the hub, lit candles on the shoulders, a gold bell in the open
belfry, and a 3x3 **sun disc** (gold rim, yellow glass, ochre-froglight core) on the roof that glows at night and gives
it a silhouette no other faction has.
Moving part: sails. Ambience: gilded glints (end-rod motes) drifting off the disc.

### Gravebound (bone, soul fire, crypts) - "_dark"

| Concept | Identity | Silhouette | Detail | Perf | Notes |
|---|---|---|---|---|---|
| **Soul-bell tower** | 5 | 4 | 5 | 5 | open belfry, swinging bone bell, chained soul braziers |
| Rag-sail mill | 3 | 2 | 3 | 4 | the old design: a windmill in grey - "just a windmill" |
| Ossuary obelisk | 4 | 4 | 3 | 5 | static, no life |

**Pick: the soul-bell tower.** A crypt tower that *tolls*: a bone bell with a soul-lantern clapper swings under a
dark-oak beam in the open belfry; soul-campfire braziers hang on chains at the front corners; a barred window glows
with a soul lantern; a leaning spire carries another. Cheapest moving part of all (four boxes).
Moving part: `BELL` at (1,6,1). Ambience: soul flames and rising souls off the braziers and spire.

### Ironhide Horde (rust iron, bone, furs) - "_nether"

| Concept | Identity | Silhouette | Detail | Perf | Notes |
|---|---|---|---|---|---|
| **Smoke-belching war mill** | 5 | 4 | 4 | 4 | bone-and-hide rotor, chimney stack, forge glow |
| Magma bellows | 4 | 3 | 4 | 3 | pumping bellows need an animated block; squat, loses at zoom |
| Totem drum | 3 | 3 | 3 | 5 | static totem, no energy read |

**Pick: the war mill.** The Horde keeps a rotor but makes it theirs: brown **hide** sails on **bone** spars, a
blackstone chimney up the back corner belching campfire smoke (vanilla smoke column, free), a smoker "bellows" glowing
on the camera side, shroomlight forge-glow in the windows, lanterns hung under the fence posts, bone spikes and a skull
totem. The off-centre chimney is what makes it read differently from the Sunforged mill at zoom.
Moving part: sails. Ambience: chimney smoke and lava pops, embers at the bellows.

### Verdant Court (living wood, moss, lanterns) - "_verdant"

| Concept | Identity | Silhouette | Detail | Perf | Notes |
|---|---|---|---|---|---|
| **Blossoming spirit tree** | 5 | 5 | 5 | 4 | cherry crown, petals falling, a glowing heart in the trunk |
| Living mill (oak with sails) | 4 | 3 | 3 | 4 | the old design: sails bolted onto a tree |
| Mushroom spore-chimney | 3 | 4 | 3 | 4 | reads more swamp than court |

**Pick: the spirit tree** - lovish's own example ("a tree that spawns flowers"). No sails at all: a cherry trunk with
flared roots on a moss pad, a pearlescent-froglight **spirit heart** glowing out of the trunk's back, boughs, lanterns
hung under a full pink crown, pink petals carpeting the foot. Cherry leaves are *persistent* (no decay) and drop their
own vanilla petals; the renderer adds a slow spiral of eight petals from the crown to the pad. The only pink canopy on
the map: unmistakable at zoom.
Moving part: `PETALS` from the crown leaf (1,8,1). Ambience: cherry petals and spores.

### Tidewrought (shipwreck, brass, kelp, coral, teal) - "_tide"

| Concept | Identity | Silhouette | Detail | Perf | Notes |
|---|---|---|---|---|---|
| **Tide-mill with a waterwheel** | 5 | 4 | 5 | 4 | ship-timber mill house, turning wheel, mast lantern |
| Mast with sails | 3 | 2 | 3 | 4 | the old design - another windmill |
| Kelp turbine in a basin | 4 | 3 | 3 | 2 | needs real water in the structure (it flows) |

**Pick: the tide-mill.** A ship-timber mill house on a sandstone apron with a dark-prismarine mill race, mangrove
posts, windows lit by a lantern inside, a brass (waxed copper) band, a spruce roof with a dried-kelp patch, dead coral,
lanterns on the front posts and a spruce mast with a lantern masthead. The **waterwheel** (dark oak rim and spokes,
spruce paddles) turns slowly on the west face, dipping into the race.
Moving part: `WHEEL` on the mangrove axle (0,1,1), radius 1.4. Ambience: spray and drips off the paddles.

### Stormcallers (jade, red lacquer, gold pagodas) - "_storm"

| Concept | Identity | Silhouette | Detail | Perf | Notes |
|---|---|---|---|---|---|
| **Wind-chime pagoda** | 5 | 5 | 5 | 4 | three jade eaves, copper chimes, a spinning vane crown, a sparking rod |
| Wind-chime tower with sails | 4 | 2 | 3 | 4 | the old design - sails again |
| Dragon kite pole | 4 | 3 | 2 | 3 | a kite needs a long animated tail; thin at zoom |

**Pick: the pagoda.** Three tiers of **jade eaves** (waxed oxidised copper stairs) on red-lacquer posts, glowing paper
screens (white glass round a glowstone core), copper **wind-chimes** (lightning rods hung point-down) at the upper
corners, and on top a **vane crown** - red arms carrying jade cups - spinning round a cherry hub like an anemometer,
under a gold cap and a lightning-rod tip that sparks. Stacked eaves are the strongest silhouette of the seven.
Moving part: `VANES` round (1,8,1). Ambience: electric sparks off the rod tip.

### Brassworks (brass, soot black, furnace orange) - "_brass"

| Concept | Identity | Silhouette | Detail | Perf | Notes |
|---|---|---|---|---|---|
| **Steam boiler with a flywheel** | 5 | 4 | 4 | 4 | brass drum, copper pipes, glowing firebox, spinning flywheel |
| Clockwork tower | 4 | 3 | 4 | 3 | gears need many animated parts |
| Piston pump | 3 | 3 | 2 | 5 | static block piston; reads as nothing at zoom |

**Pick: the steam boiler**, rebuilt with more going on: a riveted brass drum (darker band, gauge) on a soot firebox
whose lit blast furnace glows behind bars on the camera side, four-high copper **pipes** up the corners (lightning
rods: copper that never weathers), a piston pump, a soot chimney with a campfire for smoke, a lantern, and an iron
**flywheel** with a brass rim spinning fast on the west face.
Moving part: `WHEEL` on the basalt shaft (0,2,1), radius 1.1. Ambience: steam puffs off the pipe tops, firebox embers.

## Ambient life (`BuildingAmbienceClientEvents`)

The existing "working building" smoke stays. On top, every faction's **capitol, energy building and army labs**
(`maxLevel > 1`) emit an idle particle in their idiom - glints, soul flames, chimney smoke, petals, splashes, sparks,
steam - plus smoke and steam off a Geothermal Plant. Energy buildings emit from their own spots (chimney, braziers,
crown, wheel, pipe tops; rotated with the placement once and cached); capitols and labs from the roofline.

- only within **48 blocks of the render camera**, never in strategic zoom;
- **rate-limited**: one pass every 4 ticks, at most 40 emitters a pass whatever the base size;
- **off while under construction** (`isBuilt`) and in unexplored fog;
- client option **Display settings -> "Building ambience: On/Off"** (`building_ambience` in the client config) turns
  all building particles off.

Lantern flicker comes free: lanterns, candles, campfires, soul campfires and cherry leaves all animate on their own.
Birds are left for a later slice (they need an entity or a sprite path; particles don't flock).

## Villages that change with the biome, slice 1 (`BiomePalette`)

> Superseded by `design/biome_bases.md` (branch biome-bases): eight biome families, a table per faction, shared
> weathering, protected blocks and faction terraform auras. Kept below as the slice-1 record.

When a building is placed, a small set of its palette blocks is swapped by the biome at its origin, per faction:

| Biome kind | Which biomes | Caps (bare tops) | Trim swaps |
|---|---|---|---|
| SNOWY | base temperature < 0.15 (snowy plains/taiga/slopes, groves, peaks, ice spikes) | ground, stone, timber, tiles, bricks, leaves -> **snow block** | light woods -> **spruce** |
| ARID | hot and dry (desert, badlands, savanna) | ground -> **smooth sandstone** | cobble -> sandstone, stone bricks -> cut/smooth sandstone, light woods -> **acacia** |
| LUSH | jungles, swamps, lush caves | dirt/grass -> **moss** | cobble and stone bricks -> **mossy**, light woods -> **jungle** |

"Bare top" = no block of the building sits directly on it. Every swap is **shape-for-shape** (full block for full
block, stairs/slabs/fences/walls for the same, properties copied), so the footprint, block count (HP, build time) and
walkable cells (pockets) never change, and a building's `startingBlockTypes` are never swapped. The Verdant Court's
living wood and the Tidewrought ship timber keep their trim (their wood *is* their identity); dark oak stays dark.
Bridges, custom buildings, portals, beacons, farms and sculk are left alone. The swap runs on the placement's own block
list (`BuildingPlacement.setBlocks`) on the server and every client from the exact noise biome, so both agree; a client
that doesn't have the chunk yet defers it (`biomePalettePending`, retried in its tick).

Next slices: per-faction accent tables (e.g. Gravebound frost-bone in the snow, Brassworks sandblasted brass in the
desert), snow layers on slabs once there's a safe way to add blocks without changing HP, and biome-specific props.

## Geothermal Plant

One shared structure (`geothermal_plant.nbt`, 5x5x8) and one `GeothermalPlant` instance per faction (a building has
one faction). A squat basalt-and-blackstone housing on a magma floor: basalt corner piers with lanterns, blackstone
brick walls with **iron-bar windows** looking down on **lava cauldrons** (a block, not a fluid: it can't flow, and it
lights the room), a hip roof, a basalt vent stack belching smoke and two exhaust pipes. +120 energy/s (about nine Wind
Generators), 1000 energy + 280 metal, 400 HP, explodes when destroyed.

- **T2 constructors only** (`T2Workers.needsT2Builder`; build button and server). The Brassworks have no T2 constructor
  yet, so theirs is greyed out until they do.
- **Placement**: the footprint centre column must have lava at the foundation layer or the block below it, else
  *"Must be placed over lava"*. Lava under the rest of the footprint counts as ground for this building only, and its
  foundation may sit in lava. Any natural lava pool works; the maps agent's lava vents, when they land, are the
  intended spots.

## Checks

- `tools/check_sail_hubs.py` (CI safety step): sail hubs as before, plus every NO_SAILS building has a `MOTIONS` entry
  on its anchor (axle / beam / leaf / upright hub, with room to move and clear of the ground).
- GameTests (end of `SkirmishGameTests`): `every_sail_hub_sits_inside_its_mill` (now 2 sail mills),
  `every_moving_part_sits_on_its_anchor`, `snowy_biome_caps_buildings_without_pockets`,
  `geothermal_plant_is_refused_off_lava`, `geothermal_plant_is_accepted_over_lava`; the existing pocket, blueprint,
  icon and lang tests cover the new structures and buildings.
