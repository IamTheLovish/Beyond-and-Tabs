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

- **Capitol L4 shrunk from 39x39x64 to 33x33x56** (levels 15 / 21 / 27 / 33 wide, 22 / 32 / 44 / 56 tall). Why: the
  39 box's corner reached 26.9 blocks from the start, leaving as little as 8 blocks of build ground before the
  nearest allowed patch (40) - not enough for the 11x13 vehicle lab (lab doc) plus a T2 lab on that side. At 33 the
  corner reaches 22.6, every side keeps at least 13 blocks before a 40-block patch and 21 before home metal at 48,
  and the capitol still more than doubles its width and grows 2.5x in height with "constant particle ooze" at L4.
  lovish asked for "bigger and bigger"; he should look at this one (it is the main thing I changed on him).
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

## 1. What all capitols do today (shared, main)

| Item | Today |
|---|---|
| Class | a `ProductionBuilding` with `isCapitol` (one class per faction, below) |
| Cost | 250 metal / 350 energy, 10 supply (config `BuildingCosts`; ore = metal, wood = energy). Placed at match start. |
| Build time | about 60 s with 3 workers (`buildTimeBlocks` pinned to the old block count) |
| Economy | +2 metal/s and +20 energy/s while you own a built capitol (once per player); +500/+500 storage; drop-off |
| Production | slot 1 worker, slot 2 scout, slot 9 Tier 2 research, slot 10 Tier 3 research |
| Tier 2 research | 1000 metal / 10080 energy / 180 s (`TierGate`); opens the T2 lab, the extractor refit (and, per lovish, the vehicle lab's T2 refit) |
| Start check | the start-position phase tests a 15x15 "capitol square", sampled 3x3 at +-7, for void, lava and deep water (`MapLayout.startGround`, `StartPhase.inBox`; bots use the same test) |
| Home metal | **pre-made maps (main): 6 patches 11-24 from the start (`HomeMetal.RING` 18)**. Generated maps (PR #131, unmerged, lovish's Oct 28 answer): 7 home patches 48-72 out, no patch within 40 of a start, build ground 46 |
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
| L1 | placed at start (tech 1) | 15x15 (today's box) | 22 | today's art | 400 |
| L2 | Tier 2 research done (tech 2) | 21x21 | 32 | new wings and towers; faction particles start (embers, soul motes, petals, spray, sparks, steam) | 470 |
| L3 | Tier 3 research done (tech 3) | 27x27 | 44 | the faction's lovish-named form appears; heavier particles, a light column at night | 540 |
| L4 | tech 4 (questions file, Q1) | 33x33 | 56 | full silhouette, the signature at its largest, constant particle ooze | 610 |

- Each level only adds or replaces blocks (the extractor-tier and lab-level rule), so a level-up is one structure
  swap and old blocks never vanish. Each swap builds itself in 20 s (it is paid for by the tech).
- Each level raises the corruption radius (section 5) and, in a later pass, unlocks one ability (section 6).
- Every level keeps a spawn cell on a floor and walkable gaps of at least 4 wide x 8 tall.
- The capitol is the only building that grows in real blocks per tech level. Labs and every other building show
  tech with drawn parts, glow and particles (lab doc section 2.4), so one tech-up swaps one structure, not twenty.
- (Revised from 23 / 31 / 39 wide and 34 / 48 / 64 tall; see the revision notes. Each step is +6 wide, so the
  growth still reads at every level, and the extra width goes to wings, cloisters and terraces on the camera side.)

### 4.2 Why these sizes stay clear of the metal

A patch's reserved square (half-width 3) is at worst 3 x root 2 = 4.2 blocks nearer the start than its centre; a
capitol corner reaches half-width x root 2. Keeping one gap block plus one worker lane:

| Level | Half-width | Corner reach | Room left vs "no patch within 40" (35.8) | Room left vs home metal at 48 (43.8) |
|---|---|---|---|---|
| L1 | 7 | 9.9 | 25 blocks | 33 |
| L2 | 10 | 14.1 | 21 | 29 |
| L3 | 13 | 18.4 | 17 | 25 |
| L4 | 16 | 22.6 | 13 | 21 |

On generated maps (PR #131 spacing) no level can touch a patch, and at L4 at least 13 blocks of build ground stay
free on every side inside the 46-block build ground: room for the 11x13 vehicle lab (lab doc) or an 11x11 T2 lab
between the capitol and the nearest patch, which the old 39 box (8 blocks at the corners) did not leave. **Pre-made
maps are the problem**: main's home ring is 11-24 from the start, so L1 is the most that fits there today (L2's
14.1 corner already reaches an 11-block patch). Questions file Q3: move pre-made home metal out to 48-72 like PR
#131, or limit growth on those maps.

### 4.3 How the start check grows

- The L4 square (33x33, +-16) is **reserved when the capitol is placed** (a faint outline in the owner's colour);
  nobody builds in it, so growth never crushes a building. Questions file Q4. The reserve costs the player a
  9-block ring round the L1 capitol from the first minute; that is the price of growth and the reason the box is
  not bigger.
- `MapLayout.startGround` / `StartPhase.inBox` test the L4 square, not 15x15: a 5x5 grid of samples at +-16 (25
  samples, was 9 at +-7) for void, lava and deep water, and the square must hold no patch's reserved square. Bots use
  the same test. The generated-map fill already keeps patches 40+ out, so only pre-made maps are affected.
- Every capitol's L4 art (the White Angel crag included) must fit inside 33x33 at the reserved origin.

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
- Questions file Q2 also offers "every race" and "none".

### 5.3 One creep system for everyone (extends `FactionAura`)

`FactionAura` already does most of this: budgeted, reversible (recedes when its source dies, outermost first),
never on a building, never on a patch's reserved square, never in an unloaded chunk, a resweep every 60 s. The creep
is FactionAura with these changes; `NetherZone` and nether blocks leave the Horde entirely:

1. **Every building oozes**, not only capitols and labs. Radius in blocks from the building's edge, by its owner's
   tech level:

   | Source | tech 1 | tech 2 | tech 3 | tech 4 |
   |---|---|---|---|---|
   | Capitol | 40 | 55 | 70 | 85 |
   | Lab | 16 | 20 | 24 | 28 |
   | Any other building (eco, storage, defence) | 8 | 10 | 12 | 14 |
   | Extractor (creep races only) | 6 | 7 | 8 | 9 |

   Today's ceilings are 30 (Ashborn) and 16 (FactionAura). Total reach from the start point (capitol half-width
   plus radius): tech 1 47, tech 2 65, tech 3 83, tech 4 101. The capitol alone covers the whole 46-block build
   ground at tech 1, and chains of buildings carry the creep out to expansions. Enemy creep stops at yours (rule 7),
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
   creep, and at tech 1 the capitol's 40-block creep already holds a 15x15 lab anywhere in the build ground. The
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
- Map v5's six stages onto four levels: L1 the crag, gate, crossing tower and angel (wings closed); L2 the hall and
  terrace; L3 the front towers, chapels and great stair; L4 the west and east wings and the crown (wings spread).
- **Size**: v5 is 59x59x104 and 56k blocks at its top stage. It is rebuilt to the 33x33x56 L4 box (a bit over half
  scale: drop the outer curtain wall and the timber town, keep the crag, hall, crossing tower, spires and angel); the
  full v5 stays as the lobby showcase. The angel and its LOD work stay as they are.
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

**Capitol proposal - the cathedral and the pale haunted world-tree.** L1 today's cathedral. L2 an ossuary cloister and
two bone towers. L3 a **pale dead world-tree** bursts up through the crossing tower (bone-white trunk of calcite, bone
and pale birch; bare branching crown). L4 the crown spreads over the square, soul-fire in the branches, the bell hung
from a branch. The pale crown is the signature and cannot be confused with Verdant's green. Particles: soul motes
from L2, soul-fire in the branches at L3-L4.
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
- L1 the volcano core and gong (15x15). L2 the lower terraces and the first brick hall on piers. L3 lovish's "carved
  brick palace on piers": arcaded halls round the cone. L4 a second caldera vent, war banners on every pier, the gong
  at double size. Particles: embers from L1, ash plumes from L2, lava spatter at L4.
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

**Capitol proposal - the living tree-castle.** L1 today's court. L2 living-wood towers grow from the root buttresses.
L3 branch bridges and canopy halls link the towers. L4 the canopy fills the square with the blossom crown on top (the
signature). Particles: petals from L2, fireflies at night from L3. No candles or part-height blocks on walkable kerbs.
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
**great floating ring** over the lighthouse (drawn part, gold and teal) - the signature. Particles: spray from L2,
glowing motes in the pool at L3, the ring's light at L4.
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
and gold finials. L3 four towers linked by roofed galleries. L4 the storm ring scaled up as a drawn ring of arcing
light over the crown (the signature). Particles: sparks from L2, small arcs between towers at L3-L4.
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
a fourth stack. L3 lovish's "carved brick palace on piers" closes the square, with a rail viaduct. L4 the **great gear
medallion** at double size, turning while the capitol trains (the signature). Particles: steam from L1, sparks from
L2, smoke columns at L4.
- **L2 Steam Works** (passive): own builders on Brassworks ground build 10% faster.
- **L3 Clockwork Rhythm** (passive): own labs on Brassworks ground produce 10% faster.
- **L4 Overclock** (active, 120 s): every own building within 40 produces 30% faster for 20 s, then 10 s at -20%
  (Heat cooldown).

**Extractor today.** `metal_extractor_brass` (5x12x5, 118) / `_t2_brass` (5x14x5, 134): the **Iron Golem Drill**. Art:
brass_metal_extractor.png (its own critique: the head is small).

**Extractor proposal.** Keep; enlarge the helm one block each way. Height stays 12 / 14.

### 6.8 Hivebound (planned, alien insect swarm)

Reserved so it follows the same rules later. Capitol: a **hive spire** (chitin mound, ribbed spire, glowing egg
chambers) growing a new spire per level inside the same 15/21/27/33 boxes; abilities later. Creep: End-like resin,
with the placement rule (section 5). Extractor: a **burrow mound** with a mandibled drill and a glowing sac on top;
5x5, at most 14 tall, copper T2 band. No art yet: concept sheets first.

## 7. HP and cost notes

| Building | HP | 6 infantry raiders | 4 melee T1 vehicles |
|---|---|---|---|
| T1 extractor | 150 (keep) | 9-11 s | 6-7 s |
| T2 extractor | 300 (keep) | 18-23 s | 14-16 s |
| Capitol L1 | 400 (all factions; today 380 / 400 / 460) | about 42-47 s | about 27 s |
| Capitol L2 | 470 | about 50-56 s | about 32 s |
| Capitol L3 | 540 | about 57-64 s | about 37 s |
| Capitol L4 | 610 | about 64-72 s | about 42 s |

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
2. **Capitol levels L1-L4** (structure swap on tech, the 33x33 reserve, start check at +-16), proven on the Ashforge.
3. **Look-only ooze for the six other races** (the FactionAura styles grown per section 5); cheap once step 1 exists.
4. **Sunforged: the White Angel at 33x33** (PR #86 folded in), then the other factions' L2-L4 art; abilities last,
   one faction at a time.
5. Extractor touch-ups last (all already pass the 5x5 contract).

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
  - Every building is a source. Radius from its edge: capitol 40/55/70/85, lab 16/20/24/28, other 8/10/12/14,
    extractor 6/7/8/9, by owner's tech 1/2/3/4 (1 = start, 2 = T2 research done, 3 = T3 done, 4 = see open Q1).
  - Placement rule (Horde/PIGLINS faction only for now): valid when >= 50% of the footprint's cells are inside the
    owner's creep circles. Pure geometry from the sources (client ghost and server agree); never reads blocks.
    Keep today's exemptions (capitol, extractors, wind, geothermal, solar, defences, portals); every lab, the
    coming vehicle lab included, follows the rule. Total reach from the start: 47 / 65 / 83 / 101 by tech.
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
  - Design so L2-L4 can grow round it (21/27/33 boxes, heights 32/44/56); the 33x33 reserve and the +-16 start
    check belong to the next task (the level system), not this one.
TESTS FIRST:
  - a Horde lab placed on creep, destroyed, re-placed on the same spot: accepted (fails today after a crater).
  - a 15x15 Horde lab anywhere within 40 of the capitol at tech 1: accepted.
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

Merged into [[open-questions-2026-10-28]] (tech 4, which races build on creep, pre-made maps' home metal, the growth
reserve, the Ashborn creep blocks, and the vehicle questions), ordered by how much each blocks building. Also for
him to look at: the L4 box shrunk from 39 to 33 (revision notes above).

## Related
- [[vehicles-design-2026-10-28]], [[vehicle-labs-design-2026-10-28]], [[open-questions-2026-10-28]]
