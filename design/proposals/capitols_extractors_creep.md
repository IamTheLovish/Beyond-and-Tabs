---
title: Beyond and Tabs - capitols, corruption and metal extractors per faction (design doc for approval)
type: design
project: Beyond and Tabs
status: draft v3 for lovish - revised to his Oct 28 answers, then the Oct 28 revision pass (see Revision notes); no code until approved
created: 2026-10-28
source: repo main read-only (building classes, structures/*.nbt, MetalPatches, HomeMetal, BuildingValidators, NetherZone, FactionAura, RaidTuning, TierGate, EconomyServerEvents, design/LANES.md, design/building-revamp-progress.md, design/building_detail.md, design/biome_bases.md, design/moving_buildings.md); PR #86, #131, #146, #203; the art in design/buildings/ and lovish's shots in D:\Downloads
related: [[vehicles-design-2026-10-28]], [[vehicle-labs-design-2026-10-28]] (vehicle labs are designed there, not here), [[open-questions-2026-10-28]]
---

# Capitols, corruption and metal extractors, faction by faction

## Revision notes (Oct 28, design revision pass before anything is committed)

- **Capitol levels are 15 / 23 / 31 / 41 wide and 22 / 40 / 60 / 84 tall** (lovish, Oct 28: "even bigger: push
  metal further out and go larger than 39"; my earlier cut to 33 is withdrawn). Height is where the size goes: his
  nine reference images (section 0.1) are all tall, layered clusters of spires, "far taller than wide", so L4 is
  twice as tall as it is wide and nearly 4x L1's height, while the footprint grows just past 39 so the labs and the
  metal still fit. To keep the 11x13 vehicle lab and an 11x11 T2 lab fitting between the L4 capitol and the nearest
  patch on every side, **home metal moves out to 52-76 blocks** (was 48-72), "no patch within" goes from 40 to 48,
  and the home build ground from 46 to 50. The walk to the first patch grows by 4 blocks (about 1 s each way,
  section 4.2).
- **Tech 4 is "Ascension"** (lovish's answer): a late research at the capitol after Tier 3 that also opens the
  faction's game ender. Only Ashborn and Hivebound must build on creep (answer 2); pre-made maps move their home
  metal out to the same band as generated maps (answer 3); the full L4 square is reserved from the start (answer 4).
- **lovish's nine reference images** (section 0.1) are now the quality bar for every capitol's L3-L4 and are named
  in each faction entry ("scale ref" / "detail ref"). References only: our builds stay original.
- **The creep rule now names labs explicitly** (the vehicle lab doc had exempted its labs; it no longer does): every
  Ashborn building except the listed exemptions needs half its cells inside the owner's creep circles, which the
  capitol's tech-1 creep (40 from its edge, 47 from the centre) covers across the whole 46-block home build ground.
  Added the total reach per level so nobody mis-sizes it later, and limited the ooze **particles** to 12 blocks of
  a building (the creep ground itself still spreads the whole radius) to keep the ambience budget.
- **Tech steps rule shared with the lab doc**: the capitol is the one building that grows in real blocks per tech
  level; every other building (labs included) shows tech with drawn parts, glow and particles.
- **Names**: Ashborn (Horde in code) throughout; "Vertos" references dropped (this doc stands on its own).
- **Abilities marked as phase 2**: 21 new capitol abilities is a project of its own; the levels, the creep and the
  art come first, the abilities land one faction at a time afterwards (build order step 4), and any of them can be
  cut without touching the rest.
- **Extractor contract kept exactly** (5x5 on the 7x7 levelled pad, PR #131 clump spacing); no change.
- Open questions moved to [[open-questions-2026-10-28]]; section 10 below points there.

## 0. Rules this doc keeps

**lovish, Oct 28 (answers to draft v1, his words):**
- Capitol size: "should get bigger and bigger after each tech upgrade giving more unique upgrades and abilities as
  you upgrade it. More detail more size, more intricate, more particle effects, as technology advances the
  corruption that each building oozes should increase and spread."
- Capitol levels: "should be based on each tech lvl 1-2-3-4" - **four levels**, not the six of Oct 20.
- Horde nether: "we are trying to stay away from nether mod, I think nether should be replaced with something else
  like end blocks or something else. I also think if were going to set a requirement for buildings to be in nether
  or whatever corruption or creep a race needs it needs to spread alot further from the intitial capital. The
  buildings are large and it makes it hard to place buildings."
- T2 vehicles: a T2 refit of the vehicle lab, in place (designed in [[vehicle-labs-design-2026-10-28]]; here it
  only means the capitol's Tier 2 research is what opens that refit, and tech 2 is what raises the capitol to L2).

**Still in force from earlier:**
- **Existing art is the reference.** Every proposal names the picture it builds on.
- Oct 19: capitols "a lot bigger ... a lot taller, a lot more detailed", "the center piece of the base", "don't
  break the game". Extractors "could be fountains with angels or something more creative". One style kit per faction.
- Oct 20 (design/LANES.md): growth into a footprint reserved at placement; **no Reign of Nether content players can
  see**; every walkable space at least 4 wide and 8 tall, or sealed. Capitol directions: Death = a pale haunted
  world-tree; Verdant = a living tree-castle; Stormcallers = sandstone towers with jade domes and gold; Tidewrought =
  a glowing coral palace with a ring and a sea-god temple with a water spiral; Ashborn/Brassworks = a carved brick
  palace on piers; White Angel = the gothic castle-cathedral on a crag with the angel on the crown.
- Oct 28: infantry break buildings at about half a vehicle's rate per unit (PR #146 numbers stay).
- One oversized signature silhouette per building that reads at strategic zoom. Labs are out of scope (the vehicle
  labs are in [[vehicle-labs-design-2026-10-28]]); the creep rule in section 5 applies to them like any building.

Names: the faction is **Ashborn** in every doc (code: Horde / PIGLINS; the Oct 19 brief gave its art the name and
look: basalt, tuff, obsidian, magma cracks, bronze, fire bowls, stepped volcano temples; nothing of Reign of
Nether's Piglins). "Horde" below only where it quotes a class or config name.

### 0.1 lovish's reference images (Oct 28) - the bar for scale, detail and silhouette

Nine Minecraft builds he sent as the quality bar (references only; credits where the image carried one; our builds
must be original, never copies). The lesson they share: **tall clusters of towers, layered roofs, dense ornament, a
landmark silhouette that dominates the map vertically, rising out of terrain the building shapes.** Height is cheap
on footprint, so the capitols go much taller and more layered rather than much wider.

| # | Image | What it gives | Used for |
|---|---|---|---|
| 1 | **Cliff citadel**: slender spires with green onion domes and spike finials, a huge warm terracotta / red sandstone keep with arched windows, buttresses and layered balconies, a giant central arch through the mass, terraced gardens and trees down the cliff, a slim watchtower on its own crag joined by a chain bridge, waterfalls at the base; far taller than wide | **vertical scale, drama, layering, terrain shaped by the building** | the L3-L4 bar for every capitol; most directly Sunforged and Stormcallers ("scale ref: lovish's cliff citadel image, Oct 28") |
| 2 | **Blossom palace** (credit BuildsbyArs): cream and white walls, two or three storeys of round-arched arcades, tiered teal copper-patina domes and bell roofs with small gold spires, a taller central tower with a pointed teal cap, domed corner pavilions, pink and magenta blossom trees, purple foliage, mossy rock behind | **ornament density, repeated arch rhythm, tiered domes, a planted garden setting** | Stormcallers' Jade Pagoda (closest: jade roofs, gold finials, cherry blossom); its arcade / dome / garden lessons for every faction's L3-L4 ("detail ref: lovish's blossom palace image, Oct 28") |
| 3 | **Gothic cathedral**: pale grey and white stone, dark slate-blue steep roofs, a tall central spire-tower, flying buttresses, pointed tracery windows, a rose window, a ribbed dome on one wing, in a dark pine forest | **solemn vastness, buttress rhythm, tracery, a dome** | Gravebound L3-L4 (the Necropolis Cathedral), in the haunted palette |
| 4 | **Crooked wizard tower** (credit KodiBuilds): tall, narrow, stone and dark oak, leaning, steep moss-green conical roofs, a smaller second tower, small windows, a purple glowing doorway, on a mountain slope | **whimsy, verticality, an organic lean** | not a capitol: the Stormcallers Celestial Observatory (T2 lab) and the Verdant Circle of Elders; the drawn tech-step crowns on labs (lab doc 2.4) |
| 5 | **Victorian manor**: cream walls, steep pink and magenta shingle roofs, a round corner turret with a conical cap, gables and dormers, ornate trim, bay windows, a stone stair, lawns and trees | **roofline variety, ornament on a mid-size building** | the L1-L2 capitol pass of every faction (a 15x15 / 23x23 box lives on roofline variety, not mass); Sunforged and Brassworks economy buildings and the Sunforged Barracks |
| 6 | **Fantasy castle**: a tall vertical cluster of white and pale-stone towers with teal domes and conical caps, gold pennants on every spire, ivy and greenery climbing it, rising from rock; a separate domed white tower to one side | **a tower cluster as one silhouette; white, gold, teal** | Sunforged / White Angel L4 ("scale ref" with 1) |
| 7 | **Dark gothic fortress**: dark grey and slate, a tall central keep with many sharp spires, round corner towers with pointed caps, curtain walls, a gatehouse, a winding path up a grassy hill | **a militaristic dark keep** | Ashborn L3-L4 massing (the war-temple's keep, walls and gatehouse, in basalt and bronze instead of slate); Gravebound borrows the corner towers |
| 8 | **Colourful tower-house**: many steep spires in teal, deep red and purple with gold trim, warm glowing windows and lanterns, a stone terrace among trees | **glow and light at height, a dense roofline** | the emitter and window-glow plan of every capitol's tech 3-4 steps; the detail bar for mid-size buildings (labs' L3, storage, converters) |
| 9 | **Dark castle on a twisted tree**: grey stone towers with sharp red conical roofs built into and around a huge gnarled dead tree on a rock outcrop, bare twisted branches, red foliage at the base | **exactly lovish's Gravebound idea: the cathedral at L1, the dead tree growing through it from L3-L4** | **Gravebound L3-L4, marked as the primary reference** |

Rules that follow from them for every capitol:
- L3-L4 are **tower clusters**, not one mass: at least five spires of different heights, the tallest at the centre,
  roofs in the faction's own shapes (onion domes, jade tiers, needle spires, caldera cones, masts, smokestacks).
- The building **shapes its ground**: a crag, terraces, a cliff, roots or a pier base inside the reserved square,
  with the faction's creep ring and planting (gardens, blossom, pines, ash, coral) round the foot.
- **Layered detail at every height**: arcades, balconies, buttresses, tracery, finials, pennants; warm windows and
  lanterns from L2, more at every tech step (ART-FAULTS "no light").
- A **giant arch or gate** through the mass on the camera side, and one **secondary tower** off to a side joined
  by a bridge, so the silhouette is never symmetric-flat from the RTS angle.
- Each faction keeps its own palette and theme; the images give scale, verticality, layering and intricacy, not
  colours.

## 1. What all capitols do today (shared, main)

| Item | Today |
|---|---|
| Class | a `ProductionBuilding` with `isCapitol` (one class per faction, below) |
| Cost | 250 metal / 350 energy, 10 supply (config `BuildingCosts`; ore = metal, wood = energy). Placed at match start. |
| Build time | about 60 s with 3 workers (`buildTimeBlocks` pinned to the old block count) |
| Economy | +2 metal/s and +20 energy/s while you own a built capitol (once per player); +500/+500 storage; drop-off |
| Production | slot 1 worker, slot 2 scout, slot 9 Tier 2 research, slot 10 Tier 3 research |
| Tier 2 research | 1000 metal / 10080 energy / 180 s (`TierGate`); opens the T2 lab, the extractor refit (and, per lovish, the vehicle lab's T2 refit) |
| Tier 3 research | slot 10 (`TierGate`); opens the T3 lab |
| **Ascension (new, tech 4)** | lovish's answer, Oct 28: a late research at the capitol after Tier 3, slot 11; opens the capitol's L4 and the faction's game ender. Starting point: 2500 metal / 25000 energy / 240 s (est., about 2.5x Tier 2) |
| Start check | the start-position phase tests a 15x15 "capitol square", sampled 3x3 at +-7, for void, lava and deep water (`MapLayout.startGround`, `StartPhase.inBox`; bots use the same test) |
| Home metal | **pre-made maps (main): 6 patches 11-24 from the start (`HomeMetal.RING` 18)**. Generated maps (PR #131, unmerged): 7 home patches 48-72 out, no patch within 40 of a start, build ground 46. **Both move to the band in section 4.2: 7 patches at 52-76, no patch within 48, build ground 50** (lovish's answers 3 and 4, Oct 28) |
| Durability (PR #146) | 380 HP: 6 infantry raiders 40-45 s, 4 melee T1 vehicles about 26 s |
| Levels | none: one structure each today (labs already swap structures in place for L1-L3) |
| Ground look | `FactionAura`: a built capitol or lab slowly turns the ground round it into its faction's look (max radius 16, reversible, budgeted). The Ashborn instead has Reign of Nether's `NetherZone` (radius 6 -> 30). |

## 2. What all extractors do today (shared, main)

| Item | Today |
|---|---|
| Class | `MetalExtractor`, one instance per faction (`variant` suffix) |
| Footprint | **5x5** for all 14 files (7 factions x T1/T2); heights 9-14 (measured from the .nbt files) |
| Income | T1 2.0 metal/s; T2 4.5 metal/s with 20 energy/s upkeep |
| Cost | T1 50 metal / 60 energy (config; design/balance.md's 70/90 is stale); T2 refit 300 metal / 600 energy / 75 s (`TierGate`) |
| HP | T1 150, T2 300 (`RaidTuning`) |
| Tier marker | any copper block / cut copper / waxed copper block = T2; never in a T1 file; T2 only adds or replaces blocks |
| Placement | only on a metal patch; exempt from the Ashborn nether rule; `keepsGroundIntact` (no crater when razed) |
| Drawn parts | Gravebound T1/T2 bone wheel rig (`BuildingParts.RIGS`); the rest static |

Raid times (PR #146, 6 raiders / 4 T1 vehicles): T1 9-11 s / 6-7 s (Marauder 20 s, the outlier); T2 18-23 s /
14-16 s. Per unit infantry do about 0.4-0.5x a vehicle's rate: lovish's "half as fast".

## 3. The hard constraint: PR #203 and the metal patch geometry

PR #203 (`claude/fix-extractor-footprint`, open) makes every patch a dry, level **7x7 pad** (`PAD_HALF` 3),
cleared from the pad to the surface (at least 8, at most 32 up), never re-levelling an earlier pad, raising an
island when there is no dry ground. Its GameTest asks the validator, for **every live faction's extractor**, whether
each patch takes it at the bots' 9 origins (centre + (-3..-1, -3..-1), dy -1..+1) and at the player's origin
(centre - 2). With a 5x5 footprint the widest bot origins span centre-3 .. centre+3: exactly the pad. **A 6x6 or
larger footprint fails it**, and `EXTRACTOR_HALF` 2 also sets `RESERVED_HALF` 3 and `ExtractorClearance` 11.

**The extractor footprint contract (every extractor in this doc obeys it):**
1. Block extent exactly 5x5; nothing overhangs. Anything reaching further is a drawn part anchored inside the 5x5.
2. Height at most 14 (today's tallest), well inside the 8-32 the pad clears.
3. Origin = centre - 2; bottom layer on the pad; the raw-iron centre cross stays under it.
4. T1 has no copper; T2 only adds or replaces blocks in the same 5x5 and carries the copper marker.
5. The 1-block ring between the 5x5 and the 7x7 pad stays empty (building gap and builder's cell).
6. Creep (section 5) never converts a pad or a patch's reserved square, so it cannot break a pad PR #203 levelled.

**PR #131 clumps:** members stand 9.5-14 apart (`CLUMP_STEP` 11), groups 26 apart, lone patches at most 18%, paths
of 3-5 along roads. Two 5x5 extractors 9.5 apart leave at least 4.5 blocks between them and 7x7 pads never overlap,
so every clump keeps a 4-wide lane (room for a 3x3 T2 vehicle). Designs read from all four sides (bots place with no
rotation). Clump of 2-4: same-height bodies with the prop top-centre read as a row of beacons. Lone stray: the
owner ring and a drawn owner pennant say whose it is. Path: low wide base, tall prop in the middle, so a line does
not wall the road.

## 4. Capitol growth: four levels on tech 1-2-3-4

### 4.1 The levels

| Level | When | Footprint (block extent) | Height | Detail and particles | HP |
|---|---|---|---|---|---|
| L1 | placed at start (tech 1) | 15x15 (today's box) | 22 | today's art, roofline variety (ref 5) | 400 |
| L2 | Tier 2 research done (tech 2) | 23x23 | 40 | new wings, the first tall spires; faction particles start (embers, soul motes, petals, spray, sparks, steam); warm windows and lanterns | 470 |
| L3 | Tier 3 research done (tech 3) | 31x31 | 60 | the faction's lovish-named form appears as a tower cluster on shaped ground (refs 1-3, 9); heavier particles, a light column at night | 540 |
| L4 | Ascension research done (tech 4) | 41x41 | 84 | full silhouette: the tallest spire, the secondary tower and bridge, the crown; constant particle ooze and the most glow (ref 8) | 610 |

- Each level only adds or replaces blocks (the extractor-tier and lab-level rule), so a level-up is one structure
  swap and old blocks never vanish. Each swap builds itself in 20 s (it is paid for by the tech).
- Each level raises the corruption radius (section 5) and, in a later pass, unlocks one ability (section 6).
- Every level keeps a spawn cell on a floor and walkable gaps of at least 4 wide x 8 tall.
- The capitol is the only building that grows in real blocks per tech level. Labs and every other building show
  tech with drawn parts, glow and particles (lab doc section 2.4), so one tech-up swaps one structure, not twenty.
- **Why these numbers.** lovish: larger than 39 wide, and his references are all "far taller than wide". Width
  steps +8 / +8 / +10 (a new ring of 4-5 blocks a side each level: a cloister, a terrace, a wing); height steps
  +18 / +20 / +24 so L4 is about 2:1 tall to wide and nearly 4x L1. The battlefield floor is y 48 (LANES.md) and
  the world top 320, so 84 tall is comfortable; from the RTS camera a tower that tall is the landmark he asked
  for, and it costs nothing in placement room. Going wider than 41 would eat the lab ring (4.2); going taller is
  free, so any "even bigger" after this goes up, not out.

### 4.2 Why these sizes stay clear of the metal, and what it costs the opening

A patch's reserved square (half-width 3) is at worst 3 x root 2 = 4.2 blocks nearer the start than its centre; a
capitol corner reaches half-width x root 2. The metal band is set from L4: corner reach 28.3 + a vehicle lab 13
deep with a block of gap each side (15) + the patch's 4.2 = 47.5, so **no patch within 48 of a start** (was 40),
**home patches at 52-76** (was 48-72), **home build ground 50** (was 46).

| Level | Half-width | Corner reach | Room left vs "no patch within 48" (43.8) | Room left vs home metal at 52 (47.8) |
|---|---|---|---|---|
| L1 | 7 | 9.9 | 34 blocks | 38 |
| L2 | 11 | 15.6 | 28 | 32 |
| L3 | 15 | 21.2 | 23 | 27 |
| L4 | 20 | 28.3 | 15 | 20 |

At L4 at least 15 blocks stay free on every side even at the corners (28 on the flat sides): room for the 11x13
vehicle lab or an 11x11 T2 lab plus a gap between the capitol and the nearest patch all round. Clump spacing inside
the band is unchanged (PR #131: members 9.5-14 apart, groups 26 apart, 7x7 pads).

**What the longer walk does to the opening.** A worker walks about 4 blocks/s (the Villager's walk; measure it in
game before tuning). To the nearest home patch: today 48-72 blocks = 12-18 s; new 52-76 blocks = 13-19 s, so about
+1 s each way. The capitol takes about 60 s to finish, the first extractor is placed while it builds, and the
first patch is reached well inside the first minute either way; across a match the extra second per trip is noise
next to the 25 s it costs to build an extractor. Raids feel it a little more than the economy: an outer patch at
76 is 4 blocks farther from the capitol's towers, so the defender leans slightly more on towers at the clumps (PR
#146's anti-raid tower test keeps its row). Map size: two starts need their 50-block build grounds and 76-block
metal bands clear of each other, so **starts stay at least 170 blocks apart**; any map that already holds two
72-block bands with a lane between them does.

**Pre-made maps** (lovish's answer 3): their home ring (11-24 today, `HomeMetal.RING` 18) moves out to the same
52-76 band; PR #131's placer runs on them too, so one rule serves both kinds of map.

**Note for the generated-maps work (PR #131).** lovish earlier chose 7 home patches at 48-72 blocks, no patch
within 40 of a start and build ground 46. With his Oct 28 "even bigger" answer those become **7 home patches at
52-76, no patch within 48, build ground 50**; everything else in PR #131 (clump spacing, groups 26 apart, lone
patches, paths along roads, the 7x7 pads) stays. `MapLayout.startGround` must test the 41x41 capitol square
(section 4.3) at the same time.

### 4.3 How the start check grows

- The L4 square (41x41, +-20) is **reserved when the capitol is placed** (a faint outline in the owner's colour);
  nobody builds in it, so growth never crushes a building (lovish's answer 4: the full square from the start). The
  reserve costs the player a 13-block ring round the L1 capitol from the first minute; the 50-block build ground is
  sized so a full T1 base (two labs, the vehicle lab, eco) still fits outside it.
- `MapLayout.startGround` / `StartPhase.inBox` test the L4 square, not 15x15: a 5x5 grid of samples at +-20 (25
  samples, was 9 at +-7) for void, lava and deep water, and the square must hold no patch's reserved square. Bots use
  the same test. With the metal band at 52-76 no patch can be inside it. The 84-block height needs no check: the
  battlefield floor is y 48 and nothing on the map stands above y 132.
- Every capitol's L4 art (the White Angel crag included) must fit inside 41x41 at the reserved origin; shaped
  ground (crag, terraces, roots) counts as part of the structure and stays inside the square.

## 5. Corruption / creep per race (replaces the Horde's nether)

### 5.1 What goes wrong today

- The Ashborn use Reign of Nether's `NetherZone`: radius 6 growing to 30 in about 60 s, then it **stops converting**.
  Ashborn labs and other buildings need at least 80% of their bottom blocks on nether ground
  (`BuildingValidators.isOnNetherBlocks`). Exempt: the capitol, extractors, windmills, geothermal, solar, defences,
  basic and pocket portals.
- **Used-up ground**: nether blocks removed after the zone stops are never put back. A collapsing building fires
  TNT-style blasts at its bottom layer and craters the ground (only extractors are spared, PR #146), foundations
  re-level ground, and placed buildings cover it. After a few fights no free 80%-nether spot is big enough for a
  lab. (My reading of the code; not watched in play.)
- **Too small**: radius 30 round the capitol, while Ashborn buildings are big (labs 11-15 wide, the vehicle lab
  11x13). lovish: it "needs to spread alot further".
- **Nether look**: lovish wants away from the nether.

### 5.2 Which races need a creep rule

- **Placement rule (build only on your creep): Ashborn and Hivebound.** They are the spreading races; the creep is
  their base identity, as the nether was for the old Horde. The rule covers every Ashborn building, the infantry
  labs and the vehicle lab included, except the exemptions in 5.3 rule 4.
- **Look only (ooze, no placement rule): Sunforged, Gravebound, Verdant, Tidewrought, Stormcallers, Brassworks.**
  Their buildings ooze corruption that grows with tech (lovish: "the corruption that each building oozes should
  increase and spread") and gives a small home-ground bonus, but they build anywhere.
- Answered by lovish, Oct 28: only Ashborn and Hivebound (option A).

### 5.3 One creep system for everyone (extends `FactionAura`)

`FactionAura` already does most of this: budgeted, reversible (recedes when its source dies, outermost first),
never on a building, never on a patch's reserved square, never in an unloaded chunk, a resweep every 60 s. The creep
is FactionAura with these changes; `NetherZone` and nether blocks leave the Horde entirely:

1. **Every building oozes**, not only capitols and labs. Radius in blocks from the building's edge, by its owner's
   tech level:

   | Source | tech 1 | tech 2 | tech 3 | tech 4 |
   |---|---|---|---|---|
   | Capitol | 44 | 56 | 68 | 80 |
   | Lab | 16 | 20 | 24 | 28 |
   | Any other building (eco, storage, defence) | 8 | 10 | 12 | 14 |
   | Extractor (creep races only) | 6 | 7 | 8 | 9 |

   Today's ceilings are 30 (Ashborn) and 16 (FactionAura). Total reach from the start point (capitol half-width
   7 / 11 / 15 / 20 plus radius): tech 1 51, tech 2 67, tech 3 83, tech 4 100. The capitol alone covers the whole
   50-block build ground at tech 1 (51 > 50) and reaches the home metal band at every level, and chains of
   buildings carry the creep out to expansions. Enemy creep stops at yours (rule 7),
   so two tech-4 capitols on a small map simply meet in the middle.
   **Particles** (the "ooze") are drawn only within 12 blocks of a building, inside the shared ambience budget; the
   creep ground itself spreads the whole radius. Particles across a 100-block circle would blow the budget and read
   as fog, not corruption.
2. **The placement test reads the creep's area, not its blocks.** A creep-race building is valid when **at least half
   its footprint's cells lie inside its owner's creep circles** (pure geometry from the sources, the same on client
   and server). Digging, craters or covering can never "use up" a valid spot again. Blocks are the look; circles are
   the rule.
3. **Craters**: every building of a creep race collapses with `keepsGroundIntact` (as extractors do), and the creep
   resweeps every 10 s (not 60) inside a living source, refilling holes up to 2 deep with its ground block, so a
   battlefield heals into creep.
4. **Big buildings**: with rule 2 an 11-15-wide lab (the 11x13 vehicle lab included) needs only half its cells in
   creep, and at tech 1 the capitol's 44-block creep already holds a 15x15 lab anywhere in the build ground. The
   capitol stays exempt; extractors, windmills, geothermal, solar and defences stay exempt (they follow the map, not
   the creep). Nothing else is exempt: no lab doc may declare its lab free of the rule.
5. **Spread speed**: a new source reaches full radius in about 60 s (the old nether pace), budgeted per pass like
   FactionAura (biome_bases.md measured 0.2-0.8 ms per pass at 8v8). The radius steps up within 30 s of a new tech.
6. **When a source dies** the creep recedes over about 45 s. Creep-race buildings left outside creep keep working and
   keep their HP, but cannot be rebuilt there until creep returns (no decay damage: it would punish raids twice).
7. **Never converted**: metal pads and reserved squares, roads, water, buildings and their 1-block ring, the arena
   wall, other players' start squares, and another player's creep (first come keeps the cell; enemy creep stops at
   yours).
8. **A small bonus on your own creep** (all races, so the look-only races get something): own units +10% speed; own
   buildings +1 HP/s after 5 s without damage.

### 5.4 The creep look per race (all original, no nether blocks)

| Race | Creep ground (main blocks, then accents) | Ooze particles | Rule |
|---|---|---|---|
| Ashborn | ash ground: our own **ash soil** and **cinder rock** blocks (grey ash with ember speckle), tuff and smooth basalt accents, rare magma cracks (a non-damaging look-alike) | drifting ash and embers | placement |
| Hivebound (planned) | **End-like alien resin**: end stone and a custom chitin-resin block with purple veins, purpur accents | spores | placement |
| Gravebound | grave-soil: coarse dirt, rooted dirt, podzol, pale grass; bone and headstone decorations | soul motes | look |
| Verdant | moss: moss block, moss carpet, flowering azalea, ferns | petals, fireflies | look |
| Sunforged | flagstones: calcite and smooth stone paving, gold-veined from tech 3 | light motes | look |
| Tidewrought | strand: sand, clay, shallow walkable tidepools, dead coral | spray | look |
| Stormcallers | raked garden: gravel, moss, mossy cobble stepping stones | sparks | look |
| Brassworks | worked ground: cobble, gravel, packed mud, rail and coal scraps | steam | look |

The six look-only styles already exist in FactionAura (biome_bases.md) and grow from there; Ashborn and Hivebound get
new styles. lovish suggested End blocks: I put them on the alien Hivebound and gave the Ashborn its own ash so it reads
volcanic, not alien. Questions file Q5. The ash texture must pass the ART-FAULTS "pepper dots" line: soft large-scale
variation, the ember speckle sparse.

## 6. Faction by faction: capitols (with level abilities) and extractors

**Abilities are phase 2.** They are starting points (est.) for balance_audit and playtests: one per level from L2;
L1 keeps what the capitol does today; actives sit on the capitol's ability bar. They are built after the levels, the
creep and the art (build order step 4), one faction at a time, and any one of them can be cut or swapped without
touching the rest of this doc. The two that reuse existing code come first: Verdant's Awakening (the Rooted Treant's
root line) and Gravebound's Soul Bell (Raise Dead on wrecks).

### 6.1 Sunforged (White Angel)

**Capitol today.** `TownCentre`, `town_centre.nbt` **15x20x15, 1590 blocks**: the Temple of the Sun (stepped
stylobate, quartz-and-lapis cross to a gold sun, quartz columns, tiered dome). HP 380. Trains Villager (worker),
Scout Dog or Cat; T2/T3 research; Call to Arms / Back to Work (militia within 60). Art: sunforged_capitol.png.

**Capitol proposal - the White Angel castle-cathedral on the crag (PR #86 folded in).** PR #86
(`claude/white-angel-capitol-v5`, unmerged) is this faction's capitol: tools/capitol_white_angel_v5.py - a rock crag,
the hall with a rose window, the crossing tower with a tracery lantern and dome, brick needle spires, flying
buttresses, a portcullis gate, and the smooth marble **angel statue** (mesh, 3 LODs, wings opening wider with level)
on a carved pedestal. Art: white_angel_capitol_v4.png (lovish: "wow I really like the picture on the left"),
white_angel_capitol_stages_v3.png, PR #86's v5_r16 sheet.
- Map v5's six stages onto four levels: L1 the crag top, gate, crossing tower and angel (wings closed); L2 the hall
  and terrace, the first needle spires; L3 the front towers, chapels and great stair, the crag cut into terraced
  gardens; L4 the west and east wings, the curtain wall, a separate domed watchtower on its own crag joined by a
  chain bridge, and the crown (wings spread).
- **Size**: v5 is 59x59x104 and 56k blocks at its top stage. It is rebuilt to the 41x41x84 L4 box (70% of v5's
  width, 80% of its height: keep the crag, hall, crossing tower, spires, buttresses, curtain wall and the angel;
  drop the timber town); the full v5 stays as the lobby showcase. The angel and its LOD work stay as they are.
- **Scale ref: lovish's cliff citadel image, Oct 28** (the crag, terraces, chain-bridged watchtower, a giant arch
  through the mass) and **his fantasy castle image** (a white tower cluster with teal domes, gold pennants on every
  spire, ivy on the stone). Detail ref: the blossom palace's arcades on the hall and terrace. References only; the
  build stays v5's own gothic kit.
- Palette tie-in: keep v5's warm ashlar, red brick cones and slate; carry its gold finials and blue banners and add a
  gold sun disc over the gate, so it matches the white-and-gold Angel Fountain extractors.
- Particles: light motes round the angel from L2, a sunbeam column at night from L3, drifting gold feathers at L4.
- **L2 Sun Halo** (passive): own units and buildings within 24 of the capitol regenerate 2 HP/s.
- **L3 Angel's Watch** (passive): the angel sees 80 blocks (reveals fog, not cloaked units).
- **L4 Dawn Beam** (active, 90 s cooldown): a sunbeam at a point within 90: 2 s glow, then a 6-wide burn for 3 s.

**Extractor today.** `metal_extractor` (T1 5x9x5, 97 blocks) / `_t2` (5x10x5, 114): the **Angel Fountain** (Oct 19):
a quartz basin of still water over a gold sun, an angel lifting the glowing sun; T2 lantern posts, lit wing tips, a
crown of rays. Art: sunforged_metal_extractor.png. lovish's buildings_evening.png and extractors_now.png show the older
Oct 16 chapel headframe with a drawn wheel; main replaced it.

**Extractor proposal.** Keep the Angel Fountain. Raise the sun 2 (T1 11, T2 13) and widen it to a 3-wide gold ring
inside the 5x5 so a clump reads as a row of suns; add the drawn owner pennant.

### 6.2 Gravebound (Death)

**Capitol today.** `Mausoleum`, `mausoleum.nbt` **15x22x15, 1316 blocks**: the Necropolis Cathedral - crypt podium,
cross plan, green rose windows, corner needle spires, flying buttresses, an open soul belfry with a swinging bell
(`CapitolLife`). HP 460. Trains Zombie Villager (worker), Bat (scout); T2/T3 research. Night source 60 (oldest) / 40.
Art: gravebound_capitol.png.

**Capitol proposal - the cathedral and the pale haunted world-tree.** L1 today's cathedral. L2 an ossuary cloister,
two bone towers and round corner towers with pointed caps. L3 a **pale dead world-tree** bursts up through the
crossing tower (bone-white trunk of calcite, bone and pale birch; bare twisted branches), the cathedral's spires and
flying buttresses climbing it, the crypt podium cut into a rock outcrop. L4 the crown spreads over the square at
the full 84 height with towers built into and around the branches, soul-fire in the branches, the bell hung from a
branch, a ribbed dome on one wing. The pale crown is the signature and cannot be confused with Verdant's green.
Particles: soul motes from L2, soul-fire in the branches at L3-L4.
- **PRIMARY REFERENCE: lovish's dark castle on a twisted tree image, Oct 28** - it is exactly this idea (the
  cathedral at L1, the dead tree growing through it from L3-L4; towers built into the branches on a rock outcrop).
  Our palette is bone-white, grey and soul-fire cyan, not its red roofs. Scale ref also: his gothic cathedral image
  (spire-tower, buttress rhythm, tracery, rose window, the dome) and the dark gothic fortress's corner towers.
- **L2 Soul Bell** (active, 60 s): the bell tolls; each wreck within 30 raises a Zombie for 20 s (up to 4).
- **L3 Long Night** (passive): night source range +20; own units in the night zone regenerate 1 HP/s.
- **L4 Pale Bloom** (passive): own units that die on Gravebound creep rise once as a Zombie for 20 s.

**Extractor today.** `metal_extractor_dark` (5x10x5, 97) / `_t2_dark` (5x12x5, 118): the **Bone-Ribbed Well** with the
drawn bone wheel and bucket. Art: gravebound_metal_extractor.png (and lovish's two shots).

**Extractor proposal.** Keep. At T2 two pale dead branches grow from the back arch (inside the 5x5, height 13) to
echo the capitol's tree.

### 6.3 Ashborn (Horde in code)

**Capitol today.** `CentralPortal`, `central_portal.nbt` **11x16x9, 338 blocks**: Reign of Nether's portal with the
biome_bases detail pass (braziers, banners, a crown, skull totems). HP 380. Trains Grunt (worker), Strider (scout);
T2/T3 research. A fire at its centre; obsidian and portal blocks indestructible; starts the `NetherZone`. Art:
building_preview_ron_capitols.png (right pair).

**Capitol proposal - the Ashforge, a stepped volcano war-temple.** It replaces the Reign of Nether portal and drops
the nether (section 5). Built from the Ashborn kit of the Stone Giant extractor (ashborn_metal_extractor.png): stepped
basalt and tuff terraces with magma cracks, bronze bands, fire bowls; on the crown a lava-glow caldera with a **giant
bronze war gong** between two horned obsidian pillars (the signature; it rings when the capitol trains or is hit). A
magma-arch gate on the camera side. No pigs, gold blocks, gilded blackstone or nether bricks.
- L1 the volcano core and gong (15x15). L2 the lower terraces, the first brick hall on piers and the gatehouse. L3
  lovish's "carved brick palace on piers": arcaded halls round the cone, a tall central keep rising from the
  caldera with sharp basalt spires, curtain walls with round corner towers and a winding ramp up. L4 the keep at
  the full 84 height, a second caldera vent, war banners on every pier, the gong at double size on a bridge between
  the two horned pillars. Particles: embers from L1, ash plumes from L2, lava spatter at L4.
- **Scale ref: lovish's dark gothic fortress image, Oct 28** (the central keep with many sharp spires, round corner
  towers, curtain walls, gatehouse, the path winding up the hill), translated into basalt, tuff, obsidian and bronze
  with magma in the cracks; and the cliff citadel's terraces for the cone. References only.
- **L2 War Gong** (active, 60 s): own units within 30 get +20% speed and Momentum for 8 s.
- **L3 Ash Vents** (passive): enemy units on Ashborn creep move 15% slower.
- **L4 Caldera Eruption** (active, 120 s): lava bombs on a 10-wide area within 70 for 5 s; leaves ash creep there.

**Extractor today.** `metal_extractor_nether` (5x14x5, 125) / `_t2_nether` (5x14x5, 137): the **Stone Giant** - a
hooded basalt giant hammering the ore in a volcano forge; T2 bronze crown and plates. Art: ashborn_metal_extractor.png.
(Only the file suffix says nether.)

**Extractor proposal.** Keep (the strongest silhouette, already 14 tall). Add war paint: a red team-colour stripe on
the hood and two bands on the arm (block swaps only). As a creep-race extractor it oozes ash creep (section 5).

### 6.4 Verdant Court

**Capitol today.** `HeartwoodHall`, `heartwood_hall.nbt` **15x22x15, 1273 blocks**: the World-Tree Court - a ruined
mossy temple with a world tree grown through it, root buttresses, stag-dryad faces, a cherry-blossom crown. HP 400.
Trains Seedshaper (worker), Fox Courier (scout); T2/T3 research. Art: verdant_capitol.png.

**Capitol proposal - the living tree-castle.** L1 today's court. L2 living-wood towers grow from the root buttresses,
with the manor-style gables and dormers of ref 5 in bark and moss. L3 branch bridges and canopy halls link the
towers; the world tree's trunk becomes the central spire with halls grown into it, the roots shaping a mossy
outcrop with terraced gardens. L4 the canopy fills the square with the blossom crown at the full 84 height (the
signature), a slimmer sapling-tower to one side joined by a vine bridge. Particles: petals from L2, fireflies at
night from L3. No candles or part-height blocks on walkable kerbs.
- **Detail ref: lovish's blossom palace image, Oct 28** (the planted garden setting, blossom and purple foliage,
  tiered roofs) and the crooked wizard tower's organic lean for the sapling-tower and the Circle of Elders lab.
  Scale ref: the cliff citadel's terraced gardens. References only; the kit is the World-Tree Court's.
- **L2 Rootways** (passive): own units +15% speed on Verdant moss (on top of the creep bonus).
- **L3 Entangle** (passive): enemy units coming within 20 of the capitol are rooted 1.5 s (once per 15 s each).
- **L4 Awakening** (active, 90 s): the world tree's roots burst out in a line up to 24 blocks toward a target,
  hitting everything on the line (the Rooted Treant's ground line, at capitol scale).

**Extractor today.** `metal_extractor_verdant` (5x12x5, 108) / `_t2_verdant` (5x14x5, 154): the **Root Spring / Elder
Spring** - a mossy well with a living oak and stag-dryad face. Art: verdant_metal_extractor.png.

**Extractor proposal.** Keep; T1 crown 1 taller (13) with blossom on top to echo the capitol.

### 6.5 Tidewrought

**Capitol today.** `WreckHarbour`, `wreck_harbour.nbt` **15x22x15, 931 blocks**: the Admiral's Beacon - a galleon
hauled ashore with masts, torn sails, a stern lighthouse, a figurehead, a dock. HP 400. Trains Shipwright (worker),
Gull Spotter (scout); T2/T3 research. Art: tide_capitol.png.

**Capitol proposal - the coral palace and the sea-god ring.** L1 the galleon and lighthouse. L2 coral-and-prismarine
halls rise round the hull. L3 the sea-god temple with a still-water spiral pool (walled source blocks only). L4 a
**great floating ring** over the lighthouse (drawn part, gold and teal) - the signature. The lighthouse grows into
the central tower (84 at L4) with coral spires in tiers round it and a prismarine watchtower on a sea-stack joined
by a rope bridge; waterfalls and tidepools at the base. Particles: spray from L2, glowing motes in the pool at L3,
the ring's light at L4.
- **Scale ref: lovish's cliff citadel image, Oct 28** (the watchtower on its own crag with the bridge, waterfalls at
  the base). Detail ref: the blossom palace's tiered domes, as coral domes. References only.
- **L2 Tidepools** (passive): the capitol's creep makes walkable tidepools; own units on them regenerate 2 HP/s.
- **L3 Lighthouse Sweep** (passive): the lamp sweeps a 90-block cone that reveals fog as it passes.
- **L4 Sea-God's Wave** (active, 90 s): a ring wave from the capitol (radius 18) pushes enemies out and slows them
  30% for 4 s.

**Extractor today.** `metal_extractor_tide` (5x12x5, 136) / `_t2_tide` (5x14x5, 184): the **Tide-Pool Pump /
Lighthouse Pump** with a waterwheel and a mast crane. Art: tide_metal_extractor.png (its own critique: the crane reads
like a gallows; the ore core is modest).

**Extractor proposal.** Keep the waterwheel and the T2 lighthouse; replace the crane with a dead-coral arch; two more
sea lanterns in the pool floor.

### 6.6 Stormcallers

**Capitol today.** `JadePagoda`, `jade_pagoda.nbt` **15x22x15, 1542 blocks**: the Pagoda of a Thousand Storms - five
curved jade roofs, chimes, a crown of lightning rods round a floating amethyst storm ring. HP 400. Trains Geomancer
(worker), Paper Crane (scout); T2/T3 research. Art: storm_capitol.png.

**Capitol proposal - the pagoda among sandstone towers.** L1 the pagoda. L2 two cut-sandstone towers with jade domes
and gold finials, arcades of round arches on the court. L3 four towers linked by roofed galleries, a taller central
tower with a pointed jade cap, domed corner pavilions, a cherry garden on terraces round the foot. L4 the central
tower at the full 84 height, a slim lightning-rod spire on its own outcrop joined by a chain bridge, and the storm
ring scaled up as a drawn ring of arcing light over the crown (the signature). Particles: sparks from L2, small arcs
between towers at L3-L4.
- **Detail ref: lovish's blossom palace image, Oct 28** - this is the faction it fits best (cream and white walls,
  arcades, tiered teal copper-patina domes with gold spires, a taller central tower with a pointed teal cap, corner
  pavilions, blossom trees): its ornament density and arch rhythm are the Jade Pagoda's L3-L4 bar. **Scale ref: the
  cliff citadel image** (slender spires with green onion domes and spike finials, the bridged watchtower). The
  crooked wizard tower image is the Celestial Observatory (T2 lab), not the capitol. References only.
- **L2 Mana Well** (passive): +5 energy/s.
- **L3 Lightning Rods** (passive, toggle): strikes one enemy within 26 every 4 s; drains 2 energy/s while on.
- **L4 Storm Ring** (active, 120 s): a storm over a 12-wide area within 70; chain lightning for 8 s.

**Extractor today.** `metal_extractor_storm` (5x13x5, 129) / `_t2_storm` (5x14x5, 158): the **Jade Thunder Spire /
Storm Needle**. Art: storm_metal_extractor.png.

**Extractor proposal.** Keep; one sandstone plinth course under the legs (inside the 5x5) to tie to the towers.

### 6.7 Brassworks

**Capitol today.** `FoundryHall`, `foundry_hall.nbt` **15x22x15, 1504 blocks**: the Grand Foundry - a soot yard, a
hall with furnace bays, three smokestacks, a crane, a copper clock tower with a great gear, brass automatons. HP 400.
Trains Tinker (worker), Gyro Scout; T2/T3 research. Art: brass_capitol.png.

**Capitol proposal - the foundry palace on piers.** L1 the Grand Foundry. L2 brick arcades on piers round the yard and
a fourth stack, dormers and a corner turret on the hall (ref 5). L3 lovish's "carved brick palace on piers" closes
the square, with a rail viaduct, the clock tower rising as the central spire among tall smokestacks and a copper
water tower. L4 the clock tower at the full 84 height with the **great gear medallion** at double size, turning
while the capitol trains (the signature), a separate chimney-stack tower joined by a gantry bridge, warm glowing
windows on every storey. Particles: steam from L1, sparks from L2, smoke columns at L4.
- **Detail ref: lovish's Victorian manor image, Oct 28** (roofline variety, bay windows, ornate trim, in brick,
  copper and timber) and his colourful tower-house image (glowing windows and lanterns at height). Scale ref: the
  cliff citadel's giant arch through the mass, here the viaduct arch. References only.
- **L2 Steam Works** (passive): own builders on Brassworks ground build 10% faster.
- **L3 Clockwork Rhythm** (passive): own labs on Brassworks ground produce 10% faster.
- **L4 Overclock** (active, 120 s): every own building within 40 produces 30% faster for 20 s, then 10 s at -20%
  (Heat cooldown).

**Extractor today.** `metal_extractor_brass` (5x12x5, 118) / `_t2_brass` (5x14x5, 134): the **Iron Golem Drill**. Art:
brass_metal_extractor.png (its own critique: the head is small).

**Extractor proposal.** Keep; enlarge the helm one block each way. Height stays 12 / 14.

### 6.8 Hivebound (planned, alien insect swarm)

Reserved so it follows the same rules later. Capitol: a **hive spire** (chitin mound, ribbed spire, glowing egg
chambers) growing a new spire per level inside the same 15/23/31/41 boxes and 22/40/60/84 heights; abilities later. Creep: End-like resin,
with the placement rule (section 5). Extractor: a **burrow mound** with a mandibled drill and a glowing sac on top;
5x5, at most 14 tall, copper T2 band. No art yet: concept sheets first.

## 7. HP and cost notes

| Building | HP | 6 infantry raiders | 4 melee T1 vehicles |
|---|---|---|---|
| T1 extractor | 150 (keep) | 9-11 s | 6-7 s |
| T2 extractor | 300 (keep) | 18-23 s | 14-16 s |
| Capitol L1 (15x15x22) | 400 (all factions; today 380 / 400 / 460) | about 42-47 s | about 27 s |
| Capitol L2 (23x23x40) | 470 | about 50-56 s | about 32 s |
| Capitol L3 (31x31x60) | 540 | about 57-64 s | about 37 s |
| Capitol L4 (41x41x84) | 610 | about 64-72 s | about 42 s |

HP is pinned per level, not counted from blocks (a 41x41x84 capitol counted by blocks would be unkillable), so the
bigger boxes change nothing in this table. Ascension (tech 4) is a new cost: 2500 metal / 25000 energy / 240 s
(est.), paid once at the capitol after Tier 3; it opens L4 and the game ender. The L4 swap itself is free like
the other levels.

Capitol times scale PR #146's measured 40-45 s and 26 s at 380 HP, so infantry stay at about 0.4x a vehicle per unit
(lovish's "half as fast"; the vehicles doc's band of 1.5-2.0 vs 3.7 building HP/s per unit). The creep regeneration
(+1 HP/s after 5 s without damage) does not act during a raid. Extractors keep one HP and one cost for every faction
(fairness on clumped maps). Costs unchanged: T1 extractor 50m/60e, refit 300m/600e/75s, capitol 250m/350e. **Capitol
levels are free**: they ride on tech research the player already pays for (Tier 2 is 1000m/10080e). Abilities cost
nothing to use beyond their cooldowns; Storm's rods drain energy as stated. Flags from PR #146 kept, not changed
here: the Ashborn Marauder (20 s on a T1 extractor; the vehicles doc brings it up to the infantry band) and T1
artillery that one-volleys a T1 extractor.

## 8. Build order (recommended)

1. **Ashborn creep + the Ashforge L1.** The Ashborn nether rule is the live problem (hard to place, used-up ground,
   the nether look), and the Ashborn capitol is the last one showing Reign of Nether art. The creep system built here
   (area-based rule, every building oozes, no craters) is the base for every race's ooze. The Ashborn vehicle lab
   (lab doc) waits for this step so it has a creep rule to read.
2. **The metal band** (PR #131 and `HomeMetal`: 52-76, no patch within 48, build ground 50, the 41x41 start
   check) - small, and it must land before any capitol grows.
3. **Capitol levels L1-L4** (structure swap on tech, the 41x41 reserve, the Ascension research for L4), proven on
   the Ashforge.
4. **Look-only ooze for the six other races** (the FactionAura styles grown per section 5); cheap once step 1 exists.
5. **Sunforged: the White Angel at 41x41x84** (PR #86 folded in), then the other factions' L2-L4 art to the section
   0.1 bar (Gravebound's tree-castle next: lovish's primary reference); abilities last, one faction at a time.
6. Extractor touch-ups last (all already pass the 5x5 contract).

## 9. For Itachi 2 to build from

```
TASK: Ashborn (Horde / PIGLINS in code) creep replacing the nether, + the Ashforge capitol L1. lovish approves
  the doc first. The faction's display name is Ashborn; code identifiers stay as they are.
REPO: IamTheLovish/Beyond-and-Tabs, branch from main. GameTests on GitHub runners only (never on lovish's PC).
NOW: Horde capitol = CentralPortal.java, central_portal.nbt 11x16x9 (Reign of Nether art), HP 380, GRUNT slot1,
  STRIDER slot2, RESEARCH_TIER_2 slot9, RESEARCH_TIER_3 slot10. NetherConvertingAddon -> NetherZone (6 -> 30, then
  stops). BuildingValidators.isNonPiglinOrOnNetherBlocks: >= 80% of bottom blocks on nether (capitol, extractors,
  wind, geothermal, solar, defences, basic/pocket portals exempt). FactionAura gives the other factions a look-only
  ground aura (capitols/labs, radius <= 16, reversible, budgeted).
CREEP (extend FactionAura; remove NetherZone from the Horde):
  - Every building is a source. Radius from its edge: capitol 44/56/68/80, lab 16/20/24/28, other 8/10/12/14,
    extractor 6/7/8/9, by owner's tech 1/2/3/4 (1 = start, 2 = T2 research done, 3 = T3 done, 4 = the new
    Ascension research at the capitol after T3, slot 11, ~2500m/25000e/240s est., which also opens the game ender).
  - Placement rule (Horde/PIGLINS faction only for now; lovish: only Ashborn and Hivebound): valid when >= 50% of
    the footprint's cells are inside the owner's creep circles. Pure geometry from the sources (client ghost and
    server agree); never reads blocks. Keep today's exemptions (capitol, extractors, wind, geothermal, solar,
    defences, portals); every lab, the coming vehicle lab included, follows the rule. Total reach from the start:
    51 / 67 / 83 / 100 by tech (covers the 50-block build ground from tech 1).
  - Ooze particles only within 12 blocks of a building, inside the ambience budget; the ground spreads the full
    radius.
  - Horde buildings: keepsGroundIntact() = true. Resweep every 10 s inside a living source; refill holes up to 2
    deep with creep ground.
  - Ashborn ground: new blocks ash_soil and cinder_rock (gen_blocks.py, LabPBR textures); accents tuff, smooth
    basalt, a rare non-damaging magma-crack look-alike. No netherrack, nylium, soul sand/soil, nether bricks.
  - Never convert: metal pads / reserved squares (MetalPatches.RESERVED_HALF), roads, water, buildings + 1 ring,
    the arena wall, another player's creep. Recede over ~45 s when a source dies; no decay damage.
  - On own creep: own units +10% speed; own buildings +1 HP/s after 5 s without damage.
ASHFORGE L1 (art):
  - tools/capitol_ashforge.py -> central_portal.nbt (keep the name), 15x22x15. Stepped basalt/tuff volcano temple,
    magma cracks, bronze (waxed copper) bands, fire bowls, a magma-arch gate on the camera side, a lava-glow caldera
    with a big bronze war gong between two horned obsidian pillars (the signature). Style source:
    tools/mex_ashborn.py and design/buildings/ashborn_metal_extractor.png.
  - Forbidden: pigs, gold blocks, gilded blackstone, nether bricks; candles on walkable kerbs; open lava where units
    path. Walkable gaps >= 4 wide x 8 tall or sealed. Spawn cell open on a floor. buildTimeBlocks stays 314.
  - Fix getIndoorSpawnPoint (-5 y was the portal's) and the centre fire; drop canDestroyBlock's obsidian/portal
    exemption unless still needed. HP 380 -> 400. The gong rings on train/hit (CapitolLife MOTIONS, BELL kind).
  - Design so L2-L4 can grow round it (23/31/41 boxes, heights 40/60/84: a tall keep with sharp spires, section
    0.1 ref 7); the 41x41 reserve and the +-20 start check belong to the level-system task, not this one.
METAL BAND (its own small task, before any capitol grows; PR #131 + HomeMetal): home patches 7 at 52-76 from the
  start (was 48-72), no patch within 48 (was 40), build ground 50 (was 46); pre-made maps use the same band
  (HomeMetal.RING 18 -> the 52-76 placer). Clump spacing, groups 26 apart, 7x7 pads unchanged. Starts >= 170 apart.
  MapLayout.startGround / StartPhase.inBox test the 41x41 square (5x5 samples at +-20) and reject any patch's
  reserved square inside it.
TESTS FIRST:
  - a Horde lab placed on creep, destroyed, re-placed on the same spot: accepted (fails today after a crater).
  - a 15x15 Horde lab anywhere within 44 of the capitol at tech 1: accepted.
  - creep never converts a metal pad or reserved square; ExtractorFootprintGameTests stays green.
  - creep recedes when its source dies; a dug creep cell refills within 15 s.
  - the capitol box/build-time/anchor row for the new central_portal; biome_palette_covers_every_faction_block.
SHEETS: structure_render.py close-up and RTS scale beside ashborn_metal_extractor.png; a creep still at tech 1 from
  the RTS camera; before/after in design/buildings/horde_capitol.png. Run the ART-FAULTS list.
EXTRACTOR CONTRACT (any extractor, PR #203/#131): extent exactly 5x5, height <= 14, origin centre-2, raw-iron cross
  kept under it, the 1-block ring to the 7x7 pad empty, no copper in T1, T2 adds/replaces only.
NUMBERS: T1 extractor 150 HP 2.0 m/s 50m/60e; T2 300 HP 4.5 m/s, refit 300m/600e/75s, 20 e/s upkeep. Capitol
  250m/350e, +2 m/s +20 e/s, +500/+500 storage. Infantry break buildings at ~half a vehicle's rate.
DO NOT: change extractor HP/cost, patch spacing, labs, or other factions' auras in this task.
```

## 10. Open questions for lovish

Merged into [[open-questions-2026-10-28]]. Answered Oct 28: tech 4 = Ascension research; only Ashborn and
Hivebound build on creep; pre-made maps move home metal out; the full L4 square is reserved and the capitol goes
larger than 39 (now 41 wide, 84 tall) with metal pushed out to 52-76. Still open there: the Ashborn creep blocks
and the vehicle questions.

## Related
- [[vehicles-design-2026-10-28]], [[vehicle-labs-design-2026-10-28]], [[open-questions-2026-10-28]]
