# Biome-aware faction bases

lovish's vision list (Oct 11):

> **#5** Biome-aware faction bases: like villages that change per biome; factions may terraform the surroundings into
> their own look (not all must); extremely detailed yet optimised.
>
> **#4** Buildings must feel lively and individual ... use fences, trapdoors, slabs, layering, lighting
> (torches/lanterns), effects.

This branch (`biome-bases`) does four things:

1. **Biome palettes** (`building/BiomePalette.java`): every building adapts its materials to the biome at its site,
   per faction, through one substitution table applied at placement (no structure is copied per biome).
2. **Faction terraform auras** (`building/FactionAura.java`, `FactionAuraServerEvents.java`): the ground round a
   capitol or lab slowly takes its faction's look, on a budget, and gives it back when the building goes.
3. **The three RoN capitols** (Town Centre, Mausoleum, Central Portal) get the detail pass the other capitols had
   (`tools/gen_capitol_details.py`), with a moving part and ambience.
4. GameTests in `gametest/BiomeBaseGameTests.java`.

It replaces slice 1 of `design/lively_buildings.md` ("Villages that change with the biome"): three biome kinds and
one table shared by everyone become eight families and a table per faction.

## What I looked at, and what I took from it

| Reference | What it does | What this branch takes |
|---|---|---|
| **Vanilla villages** | Five village types (plains, desert, savanna, taiga, snowy) chosen by the `has_structure/village_*` biome tags; the same set of buildings drawn in each type's materials (oak and cobble / sandstone and terracotta / acacia and terracotta / spruce and mossy cobble / spruce, snow and ice). On top, *processor lists* (`mossify_10_percent`, the `street_*` rules) run block-substitution rules over a piece as it is placed. Villagers have seven biome skins (the five plus jungle and swamp). | The families are exactly vanilla's: the five village types plus jungle, swamp and badlands. The mechanism is a processor: one structure, a rule table applied at placement. Weathering is `mossify_N_percent`, made deterministic. |
| **Beyond All Reason** | Maps change everything about the terrain; Armada and Cortex stay readable by silhouette and team colour alone. | The silhouette and the team colour never change. Only *neutral* materials adapt; identity materials are listed per faction and never swapped. |
| **Age of Empires II / IV** | A civilisation's architecture set is the same on every map; player colour sits on flags and roofs. | Identity first. Biome flavour is a second layer on the materials a faction doesn't own. |
| **StarCraft II creep** | Zerg buildings spread a ground layer that shows their territory at a glance and *recedes* slowly when its source dies. | The aura's lifecycle: grows from the walls outward, recedes outermost-first on destruction. |
| **Warcraft III blight**, **Age of Mythology: Gaia's lush** | A faction-coloured ground layer round a faction's buildings (both with gameplay rules attached). | The idea of a per-faction ground look, deliberately *without* gameplay effects (see the chess note). |
| **Minecraft Legends** | Piglin bases corrupt the overworld into the Nether; destroy the base and the land heals. | Already in our game: RoN's Nether Zones do this round every Horde portal. So the Horde gets no second aura. |
| **Chess lens** | Tempo, space, counterplay. | The aura is *information*, not power: it shows "whose ground is this" from the RTS camera, but gives no stat, speed or build bonus, so it needs no counter and can't snowball. It never touches a metal patch (no space denial). |

## 1. Biome palettes

### Families

`BiomePalette.familyOf` reads tags first (vanilla's, then Forge's `forge:is_snowy / is_desert / is_swamp`, so modded
biomes from Terralith or Biomes O' Plenty sort themselves), climate as the fallback:

| Family | Vanilla village type | Biomes |
|---|---|---|
| TEMPERATE | plains | everything else (plains, forests, meadow, cherry grove, beaches, mushroom fields...), and the Nether / End |
| SNOWY | snowy | `forge:is_snowy` or base temperature < 0.15: snowy plains / taiga / slopes / beach, groves, peaks, ice spikes |
| TAIGA | taiga | `is_taiga` or temperature < 0.3: taigas, old-growth taigas, windswept hills / forest |
| DESERT | desert | desert, `forge:is_desert`, or hot (>= 1.5) and rainless |
| BADLANDS | (desert) | `is_badlands` |
| SAVANNA | savanna | `is_savanna` |
| JUNGLE | (jungle villager) | `is_jungle`, lush caves |
| SWAMP | (swamp villager) | swamp, mangrove swamp, `forge:is_swamp` |

TEMPERATE is the home family: a building in the plains is exactly the drawn structure (the GameTest checks it block
for block).

### Three layers

1. **Material swaps**, per faction and family. A faction's neutral materials (generic stone, plinths, light timber,
   the ground in its yards) take the local look; its identity materials never move.
2. **Weathering**, shared: a deterministic share of some blocks ages in place by climate (moss in the damp, cracks in
   the heat and frost, sandblasted or verdigris brass). Two copies of a building in one biome are not identical.
3. **Snow caps** (SNOWY only): a bare top (nothing of the building on it) of ground, stone, timber, bricks or leaves
   becomes a snow block. Brass, gold and quartz stay bare.

Every swap is shape-for-shape: full block for full block, stairs for stairs, slab for slab, wall for wall, log for log
(axis kept), leaves for leaves, and every property the two blocks share is copied. So the footprint, the silhouette,
the block count (HP, build time) and every walkable cell (pockets) are what was drawn. The GameTest checks the collision
shape of every state of every pair in the table.

### Shared targets (rows = material, columns = family)

"-" = as drawn. A faction only uses the rows it lists below.

| Material | SNOWY | TAIGA | DESERT | BADLANDS | SAVANNA | JUNGLE | SWAMP |
|---|---|---|---|---|---|---|---|
| Light timber (oak, spruce, birch, jungle: planks, stairs, slabs, fences, gates, trapdoors, logs, wood, leaves) | spruce | spruce | sun-bleached birch (stripped logs) | acacia | acacia | jungle | mangrove |
| Stone bricks (stairs, slab, wall) | - | - | cut sandstone | cut red sandstone | polished granite | mossy stone bricks | mud bricks |
| Mossy stone bricks | - | - | cut sandstone | cut red sandstone | polished granite | - | mud bricks |
| Cobblestone | - | - | sandstone | red sandstone | granite | mossy cobblestone | mossy cobblestone |
| Mossy cobblestone, stone | - | - | sandstone | red sandstone | granite | - | - |
| Smooth stone, polished andesite (plinths, paving) | polished diorite | - | smooth sandstone | smooth red sandstone | polished granite | - | - |
| Andesite | diorite | - | sandstone | red sandstone | granite | - | - |
| Chiseled stone bricks | - | - | chiseled sandstone | chiseled red sandstone | - | - | - |
| Cracked stone bricks | - | - | cut sandstone | cut red sandstone | polished granite | mossy stone bricks | mud bricks |
| Grass block | (snow cap) | podzol | packed sand (smooth sandstone) | terracotta | coarse dirt | moss | moss |
| Dirt | - | coarse dirt | packed sand | terracotta | coarse dirt | rooted dirt | packed mud |
| Coarse dirt | - | - | packed sand | terracotta | - | rooted dirt | packed mud |
| Podzol, rooted dirt, moss (Sunforged yards) | - | - | packed sand | terracotta | coarse dirt | - | (rooted: packed mud) |

Snow caps settle on the bare tops of everything above that is grey stone, timber or ground, and on bricks, deepslate
and blackstone roofs, nether bricks, sandstone, terracotta, dried kelp and soul soil.

### Per faction

| Faction | Adapts | Keeps everywhere (identity) |
|---|---|---|
| **Sunforged** | stone courts and walls (all masonry rows), light timber, yards | white quartz, gold, lapis and blue, polished granite and diorite accents (the Town Centre's cross), dark oak trim, bamboo and mangrove trims |
| **Gravebound** | the RoN crypts' grey stone (masonry rows), the sheds' spruce/oak, yards | deepslate, blackstone, bone, soul fire, dark oak, podzol (grave soil), the slime pit's moss |
| **Horde** | nothing - the Horde brings the Nether with it | every block (nether stone, gilded blackstone, basalt, crimson, mangrove) - it still takes snow caps and sun cracks, and its Nether Zones terraform round its portals |
| **Verdant** | its living trunks grow the local species: spruce in the north, palm-like jungle in the desert and jungle, acacia on the savanna, mangrove in the swamp, oak in the (wooded) badlands | the azalea canopy, moss, mossy stone, calcite moonwells, silver birch, the spirit tree's cherry, rooted dirt |
| **Tidewrought** | the sandstone apron and quay: grey stone quays in the north, red sand in the badlands, mud flats in the swamp | prismarine, waxed copper, the wreck timber (spruce, mangrove, dark oak), coral, kelp |
| **Stormcallers** | the grey plinths and courts (masonry rows; white marble under snow), spruce/oak sheds, the annex's sandstone paving | red lacquer, jade roofs, gold, green glaze, cherry, dark oak |
| **Brassworks** | nothing structural - the guild builds the same everywhere | brass, soot (blackstone, deepslate bricks), brick stacks, furnace orange - the climate weathers its brass and cracks its soot, and its soot roofs take snow while the brass stays bare |

Shared identity, kept by every faction (`BiomePalette.isSharedKeep`): colours (wool, carpets, banners, stained glass,
terracotta, glazed terracotta, concrete), lights, glass, metals and precious blocks, quartz, copper, devices (anything
with a block entity, doors, buttons, plates, ladders, rails, bars, chains, cauldrons, pots, anvils, work tables), plants
and coral, the Nether and the End, the deep (deepslate, tuff, calcite, dripstone), sea stone, bricks and mud, bone, hay,
snow and ice, infested blocks, and non-full blocks (paths, farmland). Blocks from other mods are never swapped.

### Weathering (shares of positions)

| | TEMPERATE | SNOWY | TAIGA | DESERT | BADLANDS | SAVANNA | JUNGLE | SWAMP |
|---|---|---|---|---|---|---|---|---|
| Moss (stone bricks, cobblestone rows) | - | - | 25% | - | - | - | 30% | 30% |
| Cracks (stone, deepslate, nether, polished blackstone bricks) | - | 8% | 4% | 12% | 14% | 8% | - | - |
| Brass (waxed cut copper rows) | - | - | - | 20% sandblasted (exposed) | 20% sandblasted | - | 18% verdigris (weathered) | 22% verdigris |

All copper stays waxed: nothing weathers further during a match.

### What is never swapped

- **A building's startingBlockTypes** (its instant foundation queue). Swapping all of them away would leave the queue
  empty, and RoN counts an empty fresh site as destroyed on its first tick. A swap *into* a starting type is skipped
  too.
- **Protected blocks** that code reads back by identity, as source or target: the extractor tier marker (copper block,
  cut copper, waxed copper block), the Graveyard's (deepslate tiles, chiseled deepslate), the Laboratory's rod on
  copper, the Castle's and Villager Market's wool, the Blacksmith's blast furnace, the Library's glowstone, the markets'
  brewing stand and crimson stairs, chests, the beacon and its base metals, sculk, and the metal-patch blocks (raw iron,
  raw gold, gold). Slice 1 capped deepslate tiles with snow, which could have flipped an overflowing graveyard back to
  plain; that is gone.
- **Soil under a plant**: a block a flower, grass or sapling stands on only takes a target that is still soil (in
  `BlockTags.DIRT`), or the plant would pop off and the building would rebuild it forever.
- Bridges, custom buildings, portals, beacons, farms and sculk (their placements read or rewrite their own blocks).

### Determinism and cost

The swap runs on the placement's block list (`BuildingPlacement.setBlocks`) on the server and on every client, reading
the exact noise biome at the footprint's centre (the origin is a corner: a building straddling a border follows the
ground most of it stands on) and hashing each block's world position for weathering. Every side computes the same
building; a client without the chunk yet defers it (`biomePalettePending`, as in slice 1). Saves store only the
building, and the swap re-runs identically on load. Levels (`changeStructure`) run the same table, so a weathered
cell stays weathered through L2 and L3.

The tables are built once at class load into identity maps (`EnumMap<Family, IdentityHashMap<Block, Block>>` per
faction). A placement does one map lookup per block, one for weathering, and one position map for caps and plants:
no string work, one allocation per placement, nothing per tick.

## 2. Faction terraform auras

| Faction | Style | Ground (weights) | Decoration on top | Cover at the walls |
|---|---|---|---|---|
| Sunforged | royal court | polished andesite 5, smooth stone 3, stone bricks 2 | - | 40% |
| Gravebound | blight | podzol 4, coarse dirt 4, soul soil 2 | dead bush, 10% | 55% |
| Horde | - | its RoN Nether Zones (portals and Central Portal) | | |
| Verdant | bloom | moss 11, grass 9; melts snow | azure bluet, oxeye daisy, cornflower, allium, 25% | 65% |
| Tidewrought | strand | sand 11, clay 6, gravel 3 (clay where nothing sturdy holds sand up) | dead coral fan, 6% | 50% |
| Stormcallers | temple garden | moss 4, grass 3, raked gravel 3 | pink petals, 30% | 50% |
| Brassworks | worked ground | gravel 35, cobblestone 35, coarse dirt 30 | - | 50% |

**Sources**: a built capitol (skirt 6 blocks past its walls) or lab (4 blocks): a production building that makes
neither metal nor energy and is not a defence. A new aura starts once its building has stood 20 s.

**Which cells**: the plan (`FactionAura.plan`) picks each skirt cell with a chance that starts at the style's cover
against the walls and fades to nothing at the rim, and orders them by distance from the walls plus a jitter (a ragged
growth front). Both come from a hash of the world position, so nothing about the plan is saved. Typical plans: a
capitol 60-100 cells, a 9x9 lab 30-50.

**Rules each conversion keeps**:
- only natural ground (grass, dirts, moss, mycelium, sand, red sand, gravel, clay, snow block), the top of its column,
  within 3 blocks of the building's foundation height; a small plant or snow layer on it may be cleared (taller plants:
  skipped);
- the result is a full, solid, walkable block; decorations are walk-through plants placed only where they survive;
  sand and gravel only on a sturdy block. Units walk exactly where they walked before;
- never inside a building's box grown by one, never on a metal patch's reserved square (7x7), never in a Nether Zone,
  never in an unloaded chunk, never a cell another aura holds;
- no block updates to neighbours (nothing cascades) and no drops if a plant gives way.

**Decommission, destruction, rule off**: the aura *recedes* at the same budget, newest (outermost) cells first, the
way StarCraft's creep recedes, putting back exactly what was there wherever the block is still the aura's own (a
building, a Nether Zone or anything else that changed a cell since keeps it); on ground someone else changed, the aura
just takes its own decoration away. Decommission counts from the moment dismantling starts. A building raised again on
the same spot starts a fresh aura, which takes cells over as the old one lets them go. The gamerule
`doFactionTerraform` (default on) turned off makes every aura recede.

**Saved**: every aura with its cells and what they were, through `MatchStateSaveData` (block states through a per-aura
palette of NBT states, not raw ids). A reload keeps the look and the way back; a restored aura keeps its ground while
its building's built-age restarts.

## 3. The RoN capitols

The Town Centre, Mausoleum and Central Portal are Reign of Nether's hand-made structures; the building-detail pass
left them alone. `tools/gen_capitol_details.py` draws a detail layer over the pristine RoN copies kept in
`tools/capitol_bases/` (so a rerun is reproducible and never applies an edit twice). Previews:
`design/building_preview_ron_capitols.png` (before | after, from the RTS camera's side).

| Capitol | Added |
|---|---|
| **Town Centre** (Sunforged) | a white quartz crest along the ridge and gold finials on both gable apexes; quartz bosses in the gables; a 3x3 sun disc (gold rim, yellow glass, glowing ochre-froglight core - the Sunforged mill's motif) on the camera-facing gable; quartz capitals where the posts meet the beams; blue banners on the posts; stone-brick lamp posts with lanterns in the four corner bushes; a market corner (barrel with a lantern, hay bale) under the west eave. Ambience: gilded glints off the disc and finials. |
| **Mausoleum** (Gravebound) | soul braziers on the four corners (polished deepslate plinth and pillar, chiseled blackstone, lit soul campfire: four soul-smoke columns framing the crypt); bone door posts at all eight doorways; a wither skull on each arm's ridge end; the lantern tower opened into a **soul belfry** whose bone bell swings under a polished deepslate ceiling (drawn by `WindmillRenderClientEvents` from `CapitolLife.MOTIONS`, the Gravebound soul-bell tower's own bell) beside a hanging soul lantern. Ambience: soul flames and rising souls off the braziers. |
| **Central Portal** (Horde) | braziers on the four corner piers (blackstone posts, lit campfires); red war banners down the frame pillars' camera side; a gilded crown (gilded blackstone corners, a gold heart, magma eyes over the portal front and back); skull totems (bone posts, wither skulls) on the platform corners; two crimson-nylium patches with roots and a fungus. The obsidian frame and the air inside it are untouched: the portal still lights. Ambience: embers off the braziers. |

Rules: the bounding box is RoN's exactly (so minCorner, maxCorner and centrePos - the spawn point, the Central
Portal's lighting fire, the war banner's mount - never move); build time is unchanged (`buildTimeBlocks` = RoN's
counts 305 / 364 / 314; HP is a fixed `maxHealth`); the starting block types stay in the foundation layers; nothing
pops off (`Canvas.unsupported`); `tools/audit_structure_pockets.py` is clean for all three (only roof-only cells,
which nothing reaches).

## 4. Tests (`gametest/BiomeBaseGameTests.java`)

- `biome_palette_covers_every_faction_block`: every block of every structure each faction uses (buildings, levels,
  upgrades, shared structures) has a decision - no unmapped surprises.
- `biome_palette_tables_keep_shape_and_identity`: every swap and weathering pair keeps the collision shape in every
  state, the pathfinder's view (leaves, fence-likes, gates, doors, trapdoors, slabs, stairs, pits, solid), never makes a
  falling block, a light or a protected block; no faction both keeps and swaps a block; TEMPERATE swaps nothing.
- `biome_families_follow_village_types`: 18 vanilla biomes land in the right family.
- `desert_and_plains_bases_take_their_palettes`: six buildings (Sunforged Barracks, Stormcallers Lotus Academy,
  Gravebound Mausoleum, Brassworks Machine Shop, Verdant Grove, Tidewrought Wreck Harbour) placed in a plains chunk
  and a desert chunk: plains = the drawn structure; desert = no swapped material left, every identity block untouched,
  same shapes, and the two copies stamped side by side walk identically cell for cell (so no new pockets).
- `faction_aura_respects_its_budget_and_metal_patches`: per-call budget and examine bound, nothing inside the
  footprint or on the patch's square, every converted cell plain walkable ground, receding restores the pad exactly.
- `faction_aura_registry_keeps_the_global_budget`: twelve overlapping auras, per-pass budget, one owner per cell,
  everything given back.
- `faction_aura_styles_only_make_walkable_ground`: every style's grounds and decorations; the Horde has none.
- `capitol_details_keep_box_build_time_and_anchors`: RoN's boxes, build times and foundations, the portal frame, the
  Mausoleum bell's anchor.
- `biome_palette_and_aura_costs_stay_small`: the measurement below.
- The existing `structures_have_no_unit_pockets_*` and `snowy_biome_caps_buildings_without_pockets` cover the new
  capitol structures and the snowy family.

## 5. Performance

PERF_PLACEHOLDER

## Not verified headless

Client-side only, not provable in a headless test: the Mausoleum bell's rendering, the capitols' ambience particles,
and how the palettes look in game (the previews are a silhouette check). The client-side swap path itself is the
server's code with the client's biome; the deferral path is slice 1's.

## Next

- Show the biome palette on the placement ghost (today the ghost shows the home palette and the placed building the
  local one).
- Per-biome props (cacti planters in the desert, snow on slabs) once there is a safe way to add blocks without
  changing HP or the instant foundation.
