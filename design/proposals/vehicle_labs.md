---
title: Beyond and Tabs - faction vehicle labs (design doc for approval)
type: design
project: Beyond and Tabs
status: draft v2 for lovish (revised Oct 28, see Revision notes) - no code until approved
created: 2026-10-28
source: lovish's Oct 28 decisions and answers (via Command Center Vertos); repo main read-only (design/moving_buildings.md, design/building_upgrades.md, design/building_detail.md, design/building-revamp-progress.md, design/LANES.md, design/ART-FAULTS.md, the lab classes, ResourceCosts + config, TierGate, RaidTuning, BuildingValidators, the structure .nbt sizes); the art sheets design/buildings/*.png and design/building_preview_*.png; lovish's shots D:\Downloads\buildings_evening.png and D:\Downloads\moving-buildings.png; [[vehicles-design-2026-10-28]]
---

# Faction vehicle labs

## Revision notes (Oct 28, design revision pass before anything is committed)

- **Names.** "Horde" is now **Ashborn** (Horde / PIGLINS in code) throughout, as in the art sheets and the capitol
  doc. The lab is the Ashborn Crater Pen.
- **Creep contradiction fixed.** This doc said no vehicle lab ever needs faction ground. The capitol doc's creep rule
  (which lovish's answer calls for) makes Ashborn labs valid only when half their cells lie inside the owner's creep
  circles. Both now say the same: no lab uses a block-based ground test (the old 80%-netherrack rule is gone), and the
  Ashborn Crater Pen obeys the area-based creep rule like every Ashborn building. At tech 1 the capitol's creep
  already covers the whole home build ground, so it never blocks placement.
- **Vehicle sizes aligned** with the vehicles doc's new hitbox rule (T1 hitbox up to 2x2 x 3 tall, T2 up to 3x3 x
  4.5 tall, bodies up to 3 / 4 long). The 5 x 8 gate and 5 x 7 x 8 bay pass all of them.
- **Roster aligned**: the Bombard Crew is a T1 Fitting Shed vehicle in both docs; the Steam Tractor is T2 (refit);
  the Flak Cart moves to the Engine Shed unless lovish keeps it (questions file); Sunforged and Gravebound labs ship
  with their first vehicle (still lovish's call).
- **Tech steps and capitol levels are now one stated rule**: the capitol is the one building that grows in real
  blocks per tech level (capitol doc); every other building, these labs included, shows tech with drawn parts, glow
  and particles only. Still an open question for lovish (drawn vs real blocks), listed in the questions file.
- Open questions moved to [[open-questions-2026-10-28]]; section 6 below points there.

Scope: the seven per-faction vehicle labs only. Capitols, metal extractors and the faction corruption / creep rules
are designed in the coordinator session ([[capitols-extractors-design-2026-10-28]]). Each lab's look is built on its
faction's **existing building art** (lovish: existing art is the reference); section 3 names the sheet each lab builds
on.

## 0. What lovish decided (Oct 28)

- Each faction's vehicle slot is faction-themed (see [[vehicles-design-2026-10-28]]).
- Vehicles get **a separate lab per faction**, like BAR's vehicle plant.
- Infantry break buildings half as fast as vehicles (per unit).
- Brassworks keeps steam machines; a futuristic faction gets real tanks later.
- Design docs come before code; existing art is the reference for new buildings.
- **T2 vehicles: a T2 refit of the vehicle lab, in place** (his answer to this doc's first draft).
- **Tech has 4 levels (1-2-3-4); buildings grow more detailed and get more particles as tech advances.** Every lab
  gets a visual step per tech level (section 2.4, per lab in section 3).
- **No nether requirement.** The Ashborn's netherrack rule is being replaced by a faction corruption / creep that
  spreads much farther from the capitol ([[capitols-extractors-design-2026-10-28]] section 5). lovish: "The buildings
  are large and it makes it hard to place buildings." No lab here reads ground blocks; the Ashborn Crater Pen follows
  the capitol doc's area-based creep rule (half its cells inside the owner's creep circles), which the capitol's creep
  satisfies anywhere in the home build ground from tech 1.

## 1. Where the game stands (read from main, Oct 28)

Every faction has one T1 army lab (trains infantry AND today's T1 vehicles), one T2 lab and one T3 lab. The
infantry / vehicle / air split ("the Oct 16 lab split") is only a line in `design/moving_buildings.md` 2.2 ("vehicle:
gantry trolley and hoist"), never built.

| Faction | T1 lab (box WxHxD, L1 / L3) | HP | T1 vehicles it trains today | T2 lab |
|---|---|---|---|---|
| Sunforged | Barracks - Training Hall (10x15x8 / 10x25x8) | 150 | none | Arcane Tower |
| Gravebound | Graveyard - Ossuary Yard (8x9x8 / 8x21x8) | 120 | none | Dungeon |
| Ashborn (Horde in code) | Military Portal - Ember Gate (8x7x5 / 8x20x5) | 100 | Hoglin (the Cinder Boar after its reskin) | Flame Sanctuary |
| Verdant | Grove - Stag Glade (9x14x8 / 9x24x8) | 150 | Sentinel Treant | Circle of Elders |
| Tidewrought | Slipway - The Slip (9x13x9 / 9x21x9) | 150 | Mudskipper Sloop, Bombard Crew | Drydock (Mortar Galley) |
| Stormcallers | Lotus Academy - Lotus Court (9x9x9 / 9x23x9) | 150 | Rocket Battery | Celestial Observatory |
| Brassworks | Machine Shop (9x13x9 / 9x20x9) | 150 | Mortar Crawler, Flak Cart | Assembly Works (Steam Juggernaut) |

- T1 lab cost: config (0,150,0) = **45 metal / 230 energy** after the BAR conversion; build time is uneven (Military
  Portal 50 blocks, Graveyard 99, Barracks 117, Slipway 141, Lotus 181, Machine Shop 216, Grove 287). A T1 lab needs
  a finished capitol.
- T1 labs level in place L1 -> L3 (`BuildingLevels`: 175% / 250% build power, x1.5 / x2 HP, upgrades 90/360 and
  180/720), same footprint, taller. Annexes (3x3, within 4 blocks of an own army lab, max 2) add +40% each.
- T2 labs: 11x9x11, 340 HP, 700 metal / 6000 energy, 480 build blocks (`TierGate`), need Tier 2 research and the T1
  lab. The Assembly Works uses `spawnRadiusOffset = 2` so the wide Juggernaut walks out clear of its walls.
- `RaidTuning.isT1Vehicle`: Hoglin, Sentinel Treant, Mudskipper Sloop, Bombard Crew, Rocket Battery, Mortar Crawler.
  Sunforged and Gravebound have no T1 vehicle yet. Flak Cart is a Brassworks vehicle but not in that list.
- Rules from LANES.md and ART-FAULTS.md: every walkable space at least 4 wide x 8 tall or sealed ("large units can't
  leave their lab" is a listed fault); "labs training large units get an exit apron"; no Reign of Nether art players
  can see; no blocky stacked-block figures (statues and creatures are drawn models); cohesive with the faction's
  other buildings; depth layering and warm light at RTS distance.
- Placement: the validator keeps every building out of a metal patch's reserved 7x7 square. Main still has the
  Ashborn netherrack rule (`MIN_NETHER_BLOCKS_PERCENT`); lovish is replacing it (section 0), so this doc does not use
  it.
- Vehicle sizes the labs must pass (vehicle doc, revised): T1 hitbox at most 2x2 by 3 tall, T2 hitbox at most 3x3
  by 4.5 tall; drawn bodies up to 3 long at T1 and 4 long at T2, never wider than the hitbox.

## 2. Rules shared by all seven vehicle labs

### 2.1 Role and footprint

**Role.** BAR's vehicle plant: a second T1 army lab beside the infantry lab, with its own economy (vehicles are
metal-heavy, energy-light). It trains the faction's T1 vehicles, and its T2 vehicles after the T2 refit (2.3).

**Footprint: 11 wide x 13 deep (structure box), up to 14 tall at L1.**
- The back 9 rows are the building; the **front 4 rows are a paved exit apron inside the structure** (the faction's
  own yard floor from its T1 lab: flat blocks / slabs, walkable), so the apron is reserved with the building and no
  one can wall the door shut.
- Same width as a T2 lab (11), so the T2 lab rules (spawn offset, pocket audit) carry over.
- It is bigger than an infantry lab (lovish finds large buildings hard to place), but 11 is the narrowest box that
  holds a 5-wide gate with walls and the 3x3 vehicles, and the apron is ground he would have to keep free anyway.
  It stands on **any flat ground**: its own yard floor is part of the structure, so no block under it is ever tested
  (the old 80%-netherrack rule is gone). The Ashborn Crater Pen alone also obeys the capitol doc's area rule (half
  its cells inside the owner's creep circles), which the capitol's tech-1 creep (40 blocks from its edge) covers
  everywhere in the home build ground.
- Fits the map: 13 deep is well under the 26-block gap PR #131 keeps between metal groups.
- Massing rule from the existing labs: a stone or kerb plinth one block high round the whole box (every T1 lab sheet
  has one), the yard open to the camera side, the tall masses at the back so units in the yard stay visible.

**Exit gate (sized for the biggest vehicle).**
- Gate opening: **5 wide x 8 tall clear** (meets the 4 x 8 walkable rule; a 3-wide vehicle has a block each side).
- Drive-through bay behind the gate: **5 wide x 7 deep x 8 tall**, open floor, no part-height blocks (no candles,
  pickles, carpets on the path: the Seedshaper lesson), spawn cell in the middle of the bay facing the gate.
- `spawnRadiusOffset = 2` (as the Assembly Works) so a 3x3 vehicle with a 4-long body never spawns into a wall.
- Gate on the structure's south face (+z, the RTS camera's side) so the player sees vehicles roll out.
- Every other walkable gap in the lab: at least 4 wide x 8 tall, or sealed (pocket audit).

### 2.2 Numbers (starting points, est.)

| | Vehicle lab | Infantry T1 lab (today) | T2 lab (today) |
|---|---|---|---|
| Cost | **90 metal / 300 energy** (config (0,200,30)) | 45 / 230 | 700 / 6000 |
| Build time | **240 blocks** (252 builder-seconds: one worker 4.2 min, commander + 3 workers about 42 s) | 50-287 | 480 |
| HP | **220** (x1.5 at L2, x2 at L3) | 100-150 | 340 |
| Tier | T1; needs a finished capitol (whether the infantry lab must come first is in the questions file) | needs capitol | needs Tier 2 + T1 lab |
| Levels | L1 -> L3 in place, same numbers as T1 labs (90/360, 180/720) | yes | yes |
| Annexes | the faction's annex attaches to it like any army lab (max 2) | yes | no |
| explodeChance | 0.2 | 0.2 | 0.2 |

Why: vehicles are the metal-heavy economy, so the plant is too (2x the infantry lab's metal, 1.3x its energy, a
little sturdier because it is what raiders want dead). It stays far below the T2 package, so it is a T1 choice.

**Production levels follow the faction's own T1 lab.** Each T1 lab sheet shows how that faction grows L1 -> L3
(Barracks: the Training Hall becomes the Cathedral of Arms; Graveyard: Ossuary Yard -> Catacomb Gate -> Bone Spire;
and so on). The vehicle lab grows the same way with the same flourishes, so "L3" reads the same on either lab.

### 2.3 The T2 refit (decided)

- An in-place order in the lab's own queue, like the extractor refit and the lab level-ups: available at **L3**, needs
  **Tier 2** research, costs the T2 lab price (`TierGate.T2_LAB_METAL/ENERGY` = 700 / 6000), 90 s, the lab makes
  nothing while it runs (cancel refunds what was spent), **+100 HP**.
- It adds blocks only (one more structure per faction, `<lab>_t2`, drawn over `<lab>_l3`): a crown that says "this
  lab makes T2 vehicles" to the opponent (each lab's crown is in section 3).
- After the refit the lab trains the faction's T2 vehicles: Sunlens Engine and Cloud Barge (Sunforged), Skullflinger
  (Gravebound), Warcrawler (Ashborn), Seedcaster (Verdant), Mortar Galley and Manta Ferry (Tidewrought), Temple Lion
  (Stormcallers), Steam Juggernaut and Steam Tractor (Brassworks). The produce gate is checked on the server like the
  T2 labs' `tier2Error`. T1 vehicles (vehicles doc section 2.3, the "(T1)" entries) train before the refit.
- T2 beasts (Oriflamme Elephant, Gorehorn Rhino, Ossuary Behemoth, Mossback Bison, Shellback Turtle, Jade Qilin, Steam
  Hauler) stay in the T2 infantry labs. Mortar Galley leaves the Drydock and Steam Juggernaut leaves the Assembly
  Works in the PR that adds their faction's refit.
- Bots: a bot with Tier 2 refits its vehicle lab once it is L3 and it is not starved of metal (the same rule as its
  lab level-ups).

### 2.4 Tech steps: one visual step per tech level (lovish: 4 levels)

Tech level = the owner's tech (1-4; tech 4 is an open question in [[open-questions-2026-10-28]]). As it rises,
**every** vehicle lab the player owns gets more detailed and gets more particles, whatever its production level, so
an opponent reads tech from any lab. The steps are separate from the L1-L3 production levels and from the T2 refit.
The shared rule across the three docs: **the capitol is the one building that grows in real blocks per tech level**
(it is the centre piece and there is one of it); **every other building shows tech with drawn parts, glow and
particles**, so a tech step never rebuilds pathing or HP on twenty buildings at once. (Still lovish's call: the
drawn-vs-real-blocks question in the questions file.)

| | What it is | Cost to the game |
|---|---|---|
| Production levels L1-L3 | structure swap (real blocks, taller), bought per lab | as today |
| T2 refit | structure swap (the crown), bought per lab | as above |
| **Tech steps 1-4** | **drawn detail parts + emissive accents + particles**, free, applied to every lab at once | client-side only: no block change, no pathing or HP change, no packet beyond the tech level the client already knows |

Why drawn and not blocks: a tech step lands on every lab at once; swapping structures on all of them would rebuild
pathing, re-count HP (RoN counts blocks as health) and multiply the structure files (3 levels x 4 tech x 2 refit =
24 per faction). Drawn parts follow moving_buildings.md: declared anchors in the structure, built and in sight only,
frozen under fog, never in strategic zoom, Building animation Off / Low / Full, particles inside the shared ambience
budget (40 emitters / 4 ticks). Off still shows a whole building.

The grammar every faction follows (section 3 gives each faction's version):
- **Tech 1:** the lab as built. Lanterns lit, the moving part runs while the queue runs.
- **Tech 2:** small detail parts on the trim (finials, banners, studs), one slow ambient particle emitter.
- **Tech 3:** the prop gains a second part, the windows / cracks / runes glow (emissive), a second emitter.
- **Tech 4:** a crown effect over the lab (a drawn beam, ring or cloud) and the strongest particles, so a tech-4 base
  reads from across the map.

### 2.5 Moving parts and the existing labs

**Moving part** (moving_buildings.md 2.2 and lovish's `moving-buildings.png`, "Lab: Brassworks Machine Shop" panel:
the flywheel turns while the queue runs, x1.75 at L2, x2.5 at L3, slower when the economy stalls, still when idle):
one drawn part per faction (section 3), same rules; the gate's door or portcullis is drawn and lifts when a vehicle
leaves (`completeProduction`), like that sheet's "Gate: Portcullis Gate" panel. Nothing is real redstone.

**What changes in the existing labs:**

| Lab | Loses (moves to the vehicle lab) | Keeps |
|---|---|---|
| Military Portal (Ashborn) | Hoglin (reskinned as the Cinder Boar, same stats) | Brute, Marauder, Headhunter, Warchief |
| Grove (Verdant) | Sentinel Treant (becomes the Rooted Treant) | Leafblade, Thornbow, Owl Watcher, Hive Keeper, Moonwell Bearer, Druid |
| Slipway (Tidewrought) | Mudskipper Sloop, Bombard Crew | Cutlass Raider, Reef Guard, Tide Priest, Harpooner, Flak Swivel, Corsair Captain |
| Lotus Academy (Stormcallers) | Rocket Battery | Blade Dancer, Fire/Frost Adept, Ink Scribe, Jade Guard, Wind Monk, Storm Witch |
| Machine Shop (Brassworks) | Mortar Crawler, Flak Cart (unless lovish keeps it; questions file) | Steam Runner, Rifle Squad, Iron Warden, Shield Projector, Sapper, Arch Engineer |
| Barracks, Graveyard | nothing (no vehicles yet) | all |
| Drydock, Assembly Works | Mortar Galley, Steam Juggernaut - with their faction's T2 refit | the rest |

- Heroes stay in the infantry labs. Freed hotkey slots close up.
- A faction's vehicles move **in the same PR as its vehicle lab**, so no faction is ever without them.
- Sunforged and Gravebound: the lab ships with their first vehicle, never empty (recommended; still lovish's call in
  the questions file).
- `isT1Vehicle` is unchanged; the Flak Cart stays out of it (AA, no building bonus) even though it trains here.
- Bots: the bot learns "vehicle lab" as a lab type (one per bot at T1, after its first infantry lab on easy, before it
  on hard), queues vehicles there, and the PR #146 vehicle raid draws from it. The bot's lab cap counts both.

## 3. The seven labs, each on its faction's existing art

For each: the sheet(s) it builds on, what it takes from them, the new vehicle parts (gate, bay, prop), the moving
part, production levels, the T2 refit crown, and the four tech steps. "Sheet" paths are in the repo's `design/`
folder.

### Sunforged - the Knights' Coach-House (Dawnrider, Sanctified Ram, Sunlens Engine, Cloud Barge)
- **Builds on:** `buildings/sunforged_barracks.png` (Training Hall / Knights' Cloister / Cathedral of Arms) and
  `buildings/sunforged_capitol.png` (the Temple of the Sun's style kit); palette check against the Sunforged rows of
  lovish's `buildings_evening.png` (white, gold, deep blue).
- **Takes:** white quartz pillar walls with gold capitals and gold-capped lantern finials, lapis-blue bands and
  pale-blue glass lancets, candle pedestals, the calcite-and-sand training-yard floor (it becomes the apron), the
  stone plinth.
- **New:** the Training Hall's yard turned into a coach-house: a long quartz hall at the back with a **gold-crowned
  pointed arch** as the 5 x 8 gate (the old keep's "gatehouse with a gold-crowned arch", building_detail.md), a blue
  lancet over it, and a **gold sun disc** (3 across) on the gable as the prop. Inside the bay, a gilded cradle where
  the Dawnrider's cloud and the Sanctified Ram stand.
- **Moving part:** the sun disc turns slowly while the queue runs; rays pulse on release (the Town Centre panel of
  `moving-buildings.png` does the same with its halo).
- **Levels:** as the Barracks - L2 the Knights' Cloister's corner towers and blue banners, L3 the Cathedral of Arms'
  gold cross spire and rose window. **T2 crown:** a gold mirror frame on the gable (the Sunlens Engine).
- **Tech steps:** 1 as built - 2 gold finials on every pier, blue-and-gold pennants, slow gold motes in the yard -
  3 a halo ring round the sun disc, the lancets glow, light motes drift through the arch - 4 a slow sunbeam rising
  from the disc into the sky.

### Gravebound - the Charnel Wheelhouse (Soulfire Wheel, Carrion Hauler, Skullflinger)
- **Builds on:** `buildings/gravebound_graveyard.png` (Ossuary Yard / Catacomb Gate / Bone Spire, tools/lab_graveyard.py)
  and lovish's `buildings_evening.png` (the Gravebound economy family and the drawn bone wheel on the Ossuary Well rig).
- **Takes:** blackstone and polished-deepslate walls, bone-block pillars and lintels, a soul-lantern on every post
  (the cyan points that read at RTS zoom), dark needle spirelets, the dirt-and-coffin yard (the apron), the deepslate
  plinth.
- **New:** a long blackstone barn behind the yard; the gate is the Catacomb Gate's **bone-pillar arch** widened to
  5 x 8 with a drawn bone portcullis; stacked coffins and spare rib-wheels along the inner walls.
- **Prop and moving part:** a **big bone wheel** (the Ossuary Well rig's wheel, scaled up, drawn) on the barn's gable
  that turns while the queue runs; soul fire flares in two braziers on release.
- **Levels:** as the Graveyard - L2 the Catacomb Gate's raised walls and spires, L3 the Bone Spire with its cyan cap.
  **T2 crown:** a ribcage catapult frame on the roof (the Skullflinger).
- **Tech steps:** 1 as built - 2 skulls on the spire tips, hanging chains, soul wisps over the lantern posts -
  3 the bone wheel burns with soul fire, the windows glow cyan, a low soul mist rolls over the yard - 4 a slow ring of
  soul fire circling the tallest spire.

### Ashborn - the Crater Pen (Cinder Boar, War Sled, Magma Pot Wagon, Warcrawler)
- **Builds on:** `buildings/ashborn_military_portal.png` (Ember Gate / Crater Forge / The Giant's Gate, the Ashborn
  kit) and `buildings/ashborn_economy.png`.
- **Takes:** stepped basalt-and-tuff walls, magma and shroomlight glow in the cracks and fire bowls, bronze
  (terracotta-tone) lintels and posts, the obsidian accent. No nether brick, no gilded blackstone, no piglin heads
  (the sheet's BEFORE column is exactly what is gone).
- **New:** a crater-walled pen: the Crater Forge's stepped basalt rim drawn round an open beaten-ground yard; the gate is
  the **Ember Gate's basalt arch with its bronze lintel**, widened to 5 x 8; a forge pit with magma at the back for
  sled plating; a strider stall.
- **Ground:** it brings its own basalt yard floor, so no block under it is tested (no netherrack rule). As an
  Ashborn building it must have half its cells inside the owner's ash creep (capitol doc section 5.3), which the
  capitol's creep covers across the whole home build ground from tech 1; the pen's own creep (lab radius 16-28) then
  carries the ash outward.
- **Prop and moving part:** two **fire-bowl towers** flanking the gate (the Ember Gate's corner glow, raised); a drawn
  war drum on each beats faster as the queue runs; the fire flares on release.
- **Levels:** as the Military Portal - L2 the Crater Forge's taller stepped walls, L3 the Giant's Gate's stone giant
  (drawn as a model, not stacked blocks) over the gate. **T2 crown:** a strider-height gantry with a drum platform
  (the Warcrawler).
- **Tech steps:** 1 as built - 2 bronze spikes and war banners, embers rising from the wall cracks - 3 the cracks
  pulse with magma glow, smoke from the forge pit, the drums glow - 4 a drawn lava-fall pouring from both fire-bowl
  towers (no fluid blocks) and drifting ash.

### Verdant - the Treant Nursery (Rooted Treant, Bramble Roller, Seedcaster)
- **Builds on:** `buildings/verdant_grove.png` (Stag Glade / Rooted Court / Elder Grove) and
  `buildings/verdant_capitol.png` (the World-Tree Court).
- **Takes:** ruined mossy stone-brick pillars, the **birch stag-antler boughs** of the stag-dryad, verdant-froglight
  glow, the white calcite path through flowers and grass (it becomes the apron path), lanterns on the pillar tops,
  articulated oak canopy tiers.
- **New:** the Stag Glade's two front pillars become the gate posts, joined over a **5 x 8 opening by an arch of birch
  antler boughs** with a froglight at the crown; behind it a dark seed-bed where half-grown treants (root-and-bark
  shapes) stand; a moonwell at the back as in the World-Tree Court.
- **Moving part:** a sapling rises out of the seed-bed while the queue runs; the canopy sways on the gust clock; the
  froglight pulses on release.
- **Rule:** no leaves inside the 5 x 8 opening (leaves are solid to pathing).
- **Levels:** as the Grove - L2 the Rooted Court's canopy and root arches, L3 the Elder Grove's cherry-blossom crown.
  **T2 crown:** a seed-pod tree (the Seedcaster's sling).
- **Tech steps:** 1 as built - 2 flowering vines on the pillars, fireflies over the seed-bed - 3 glowing sap veins up
  the pillars, the antler arch sprouts glowing buds, falling leaves - 4 the arch blooms and a slow drift of blossom
  petals and spores rises from the crown.

### Tidewrought - the Fitting Shed (Mudskipper Sloop, Coral Crab, Bombard Crew, Mortar Galley, Manta Ferry)
- **Builds on:** `buildings/tide_slipway.png` (The Slip / The Fitting-Out / The Launch) and
  `building_preview_economy_tide.png`; `buildings/tide_capitol.png` for the striped lighthouse.
- **Takes:** the prismarine kerb with its sandstone and pale edge, the red mangrove-plank hull, dark-oak timber crane
  and posts, the walled still-water channel (sources only, walled on every side), lantern posts, the blue-striped
  sails, the prismarine-and-cyan-glass lighthouse lantern at L3.
- **New:** The Slip's open ramp roofed into a fitting shed: dark-oak trusses over a walled water channel along the
  back where a mangrove hull sits on cradles; the gate is a **dark-oak portal with a striped beam**, 5 x 8, opening onto
  the sand-and-prismarine apron. Vehicles leave on land, never through water.
- **Prop and moving part:** the **dark-oak cargo crane** (The Slip's crane, taller) swings its load and the hull rocks
  while the queue runs (moving_buildings: "Slipway: the crane swings its cargo barrel").
- **Levels:** as the Slipway - L2 the Fitting-Out's mast and sails, L3 The Launch's lighthouse turret.
  **T2 crown:** a manta-shaped striped sail canopy (the Manta Ferry).
- **Tech steps:** 1 as built - 2 rope coils, pennants, drips and a sea-glow at the waterline - 3 coral grows up the
  piers, foam and spray along the channel, the portholes glow - 4 a drawn lighthouse beam sweeping slowly over the
  base.

### Stormcallers - the Lion Gate Court (Gale Kite, Temple Lion, Rocket Battery)
- **Builds on:** `buildings/storm_lotus_academy.png` (Lotus Court / Hall of Winds / Thousand-Lotus Pagoda) and
  `buildings/storm_capitol.png`.
- **Takes:** the grey stone-brick plinth and courtyard, red-lacquer columns, curved jade (oxidised copper) roofs with
  gold corner blocks and lightning-rod finials, hanging white paper lanterns, stone lanterns, cherry blossom, and the
  Lotus Court's **dark-oak and red gateway** (it is already a gate at the front of the court).
- **New:** the Lotus Court's gateway widened to 5 x 8 as the vehicle gate; the hall at the back becomes a workshop with
  paper kites drying on lines over the yard and a rocket rack.
- **Prop:** two **guardian lions** on plinths either side of the gate (the Temple Lion's look). They are drawn as
  sculpted models through the statue pipeline (`BuildingParts.STATUES`, like the White Angel), never stacked blocks
  (ART-FAULTS). If the statue pipeline is too heavy for the first pass, the plinths stand empty with stone lanterns.
- **Moving part:** the kites sway on their lines and a prayer wheel turns while the queue runs; an arc sparks between
  the gate's lightning rods on release ("Lotus Academy: a spark arcs between the gate rods per unit").
- **Levels:** as the Lotus Academy - L2 the Hall of Winds' second roof tier, L3 the Thousand-Lotus Pagoda's tower.
  **T2 crown:** a lacquered rocket-rack tower.
- **Tech steps:** 1 as built - 2 more paper lanterns and wind chimes under the eaves, drifting blossom - 3 sparks
  hop between the roof's lightning rods, the lions' eyes glow - 4 a small drawn storm cloud over the hall with
  flickering arcs.

### Brassworks - the Engine Shed (Boiler Trike, Steam Tractor, Mortar Crawler, Flak Cart, Steam Juggernaut)
- **Builds on:** `buildings/brass_machine_shop.png` (Machine Shop / Engine Works / Clockwork Foundry),
  `building_preview_assembly_works.png` (open front, travelling gantry) and the "Lab: Brassworks Machine Shop" panel
  of lovish's `moving-buildings.png` (the flywheel).
- **Takes:** the copper / terracotta plank roofs, dark-oak timber frame and posts, blackstone-and-deepslate plinth,
  orange-lit windows and orange-capped smokestacks, the gear window, the yard with a machine being assembled.
- **New:** the Assembly Works' open front made into a 5 x 8 **dark-oak framed door with a drawn rolling brass
  shutter**; rails as the floor pattern from the bay out onto the apron (plain rails only, never powered or detector
  rails); a copper water tank on the side wall.
- **Prop and moving part:** the **flywheel** on the side wall (exactly the moving-buildings panel) and a travelling
  gantry over the bay (the Assembly Works' crane) run while the queue runs; steam bursts from the stacks on release.
- Stays 1800s steam: rivets, brick, copper, timber; no armour-plate panels.
- **Levels:** as the Machine Shop - L2 the Engine Works' second stack and raised roof, L3 the Clockwork Foundry's clock
  tower. **T2 crown:** a heavy gantry over the whole roof (the Juggernaut's cradle).
- **Tech steps:** 1 as built - 2 pipes and gauges on the walls, steam puffs from the stacks - 3 welding sparks under
  the gantry, the firebox and gear window glow - 4 a lit clock face, heavy smoke columns and a steam-whistle burst on
  every vehicle released.

## 4. Build order (recommended)

1. **Shared vehicle-lab code + the Verdant Treant Nursery first.** Why: it pairs with the vehicle doc's first prototype
   (the Rooted Treant, lovish's own idea, PR #305), so lovish plays the new lab and the new vehicle together; Verdant
   has a T1 vehicle today, so the lab is never empty; its art reuses the Grove's existing kit with no new figure to
   sculpt; and it builds every shared piece once (the class, the 11x13 box with the apron, the 5x8 gate rule, moving
   vehicles out of a T1 lab, the bot's new lab type, the tests). The other six labs then reuse it and are mostly art.
2. Ashborn Crater Pen (after the creep system exists, capitol doc build order step 1, so its placement rule has
   something to read), Tidewrought Fitting Shed (two vehicles move), Brassworks Engine Shed, Stormcallers Lion Gate
   Court (the lions need the statue pipeline).
3. Sunforged Knights' Coach-House and Gravebound Charnel Wheelhouse, each with its first vehicle (Dawnrider,
   Soulfire Wheel) unless lovish wants them earlier and empty.
4. The T2 refit (shared code once, then each faction's crown with its first T2 vehicle; Mortar Galley and Steam
   Juggernaut move with theirs).
5. Tech steps 2-4 for all seven labs, in one pass with the shared drawn-part code, once lovish has answered how
   tech 4 is reached (questions file). The structures from steps 1-3 must already declare the anchors (section 5).

## 5. For Itachi 2 to build from (stands alone)

Context: Beyond and Tabs (repo IamTheLovish/Beyond-and-Tabs), Minecraft 1.20.1 Forge RTS on Reign of Nether. lovish
decided (Oct 28) that every faction gets a **separate vehicle lab** (BAR's vehicle plant), that new buildings are
based on the faction's existing art, that T2 vehicles come from an in-place T2 refit of the vehicle lab, that tech
has 4 levels with buildings getting more detail and particles at each, and that the Ashborn (Horde in code) nether
requirement is being replaced by an area-based creep rule (so no lab may read ground blocks). Build the first lab:
the **Verdant Treant Nursery**, with the shared code every faction's vehicle lab will reuse. Do not start before
lovish approves this design; do not merge without his OK.

What to build:
1. `VehicleLab` building (a `ProductionBuilding`, like `building/buildings/verdant/Grove.java`), registered for
   Verdant as **Treant Nursery** (structure `treant_nursery`, `_l2`, `_l3`, in assets/ and data/), placed with a
   finished capitol (Heartwood Hall) as the gate, like the Grove's build button. It stands on any flat ground (its
   yard floor is part of the structure); no ground-block rule. (The Ashborn pen, a later PR, adds the creep area
   rule from the capitol doc; the shared class must not hard-code "no ground rule".)
2. Numbers: cost config `Building(0,200,30)` (= 90 metal / 300 energy after the BAR conversion), `buildTimeBlocks =
   240`, `maxHealth = 220`, `explodeChance = 0.2f`, `spawnRadiusOffset = 2f`, `maxLevel = BuildingLevels.MAX_LEVEL`
   with `UPGRADE_LAB_L2` / `UPGRADE_LAB_L3` on `hotkey12` (same as the T1 labs). It counts as an army lab for
   annexes (`LabAnnex`) and for the bot's lab cap.
3. Production: move `SENTINEL_TREANT` from the Grove into the Treant Nursery (slot 1) and remove it from the Grove.
   Later vehicles (Bramble Roller, Seedcaster) join here. If the Rooted Treant work (PR #305) has landed, it is the
   same unit.
4. Structure: box **11 wide x 13 deep**, at most 14 tall at L1 (levels taller, same footprint, only add or replace
   blocks; `tools/gen_lab_levels.py` rules). The front (south, +z) 4 rows are a flat apron inside the box. The gate is
   a **5 wide x 8 tall clear opening**; behind it a **5 x 7 x 8 drive-through bay**, spawn cell in its middle facing
   the gate. Every walkable gap at least 4 wide x 8 tall or sealed; no part-height blocks (candles, pickles, carpets)
   on any path; no leaves inside the 5x8 opening. Pass `tools/audit_structure_pockets.py`.
   **Art: build it from the Grove's own kit** (`tools/lab_grove.py`, sheet `design/buildings/verdant_grove.png`:
   the Stag Glade / Rooted Court / Elder Grove) so it sits beside the Grove as one family: a one-block mossy plinth;
   ruined mossy stone-brick pillars; the two front pillars are the gate posts, joined over the 5 x 8 opening by an
   arch of birch stag-antler boughs with a verdant froglight at the crown; the white calcite path through flowers and
   grass runs from the bay over the apron; behind the bay a dark seed-bed with half-grown treants (root-and-bark
   shapes); a moonwell at the back (as the World-Tree Court's); lanterns on the pillar tops. L2 adds the Rooted
   Court's canopy and root arches, L3 the Elder Grove's cherry-blossom crown. `BiomePalette` rules as the Grove.
   Original art only; check the render against `design/ART-FAULTS.md` before lovish sees it.
5. Anchors for later (declare them now, draw nothing yet): the moving part (the seed-bed centre), the gate door, and
   the tech-step anchors - pillar tops (tech 2 vines), pillar faces (tech 3 sap veins), the arch crown (tech 4 bloom
   and petals). Keep them in the structure's own frame like `BuildingParts`' other rows, so `tools/check_parts.py`
   can pin them. The T2 refit (`treant_nursery_t2` over `_l3`, a seed-pod tree crown) is a later PR.
6. Bot: one Treant Nursery per bot at T1 (after its first Grove on easy, before it on hard); it trains Sentinel
   Treants there; the PR #146 vehicle raid takes its vehicles from it.
7. Moving part (optional in this PR): a sapling rising out of the seed-bed while the queue runs, drawn client-side
   like the other building parts (never real redstone).

Vehicle sizes the gate must pass: T1 hitbox up to 2x2 x 3 tall, T2 hitbox up to 3x3 x 4.5 tall (drawn bodies up to
4 long along the facing axis, never wider than the hitbox).

Tests first (red, then green), in their own GameTest batch if they form battlefields or call `MetalPatches.forgetAll`:
- the Treant Nursery trains a Sentinel Treant and the Grove no longer offers it;
- a 3x3 x 4.5 test vehicle spawned at the nursery walks out through the gate onto the apron and away (no stuck tick);
- the structure: box 11x13, gate 5x8 clear, apron flat, spawn cell open, each level keeps every block of the one
  before (extend the existing lab-level checks), each declared anchor is air or its intended block at every level;
- an annex attaches to it; level-up works; the bot builds one and queues a Treant.
Run them on GitHub runners, never on lovish's PC (he is gaming on it).

Do not touch: metal extractors and patch code (PR #203 owns `MetalPatches`), capitols, the faction ground /
corruption rules (being redesigned elsewhere), the T2 labs. Hand lovish an L1-L3 sheet in the same layout as
`verdant_grove.png` (before | after, 32 px/block) and eyes stills before merge.

Later PRs, in order: the other labs (same class, art from each faction's T1 lab sheet, moved units) - Ashborn
Crater Pen (Hoglin / Cinder Boar; Ashborn kit from `ashborn_military_portal.png`; placed under the capitol doc's creep
area rule once that exists), Tidewrought Fitting Shed (Mudskipper Sloop, Bombard Crew; `tide_slipway.png`),
Brassworks Engine Shed (Mortar Crawler, Flak Cart unless lovish keeps it; `brass_machine_shop.png` + the Assembly
Works), Stormcallers Lion Gate Court (Rocket Battery; `storm_lotus_academy.png`; the lions are drawn statues);
Sunforged Knights' Coach-House (`sunforged_barracks.png`) and Gravebound Charnel Wheelhouse
(`gravebound_graveyard.png`) with their first vehicle. Then the **T2 refit**: an in-place order at L3, needs Tier 2,
700 metal / 6000 energy, 90 s, lab paused, +100 HP, a `<lab>_t2` crown structure over `_l3`, server-side Tier 2
produce gate; T2 vehicles train there (Mortar Galley and Steam Juggernaut move out of the Drydock and Assembly Works).
Then the **tech steps**: drawn parts, emissive accents and particles on the declared anchors, keyed to the owner's
tech level 1-4, on every vehicle lab at once, client-side only (no block change), within the ambience particle budget,
honouring Building animation Off / Low / Full.

## 6. Open questions for lovish

Merged into [[open-questions-2026-10-28]]: opening with the vehicle lab, the Sunforged / Gravebound lab timing, the
Flak Cart's lab, and tech steps drawn vs real blocks.

## Related
- [[vehicles-design-2026-10-28]] (the vehicles these labs train; lovish's answers)
- [[capitols-extractors-design-2026-10-28]] (capitols, extractors, faction corruption / creep)
- [[open-questions-2026-10-28]] (every open question across the three docs, with recommendations)
- Repo: design/moving_buildings.md, design/building_upgrades.md, design/building-revamp-progress.md, design/LANES.md,
  design/ART-FAULTS.md; sheets design/buildings/*_barracks/_graveyard/_military_portal/_grove/_slipway/_lotus_academy/
  _machine_shop.png, design/building_preview_assembly_works.png
- PRs: #146 (raid roles), #203 (extractor footprint), #305 (Rooted Treant)
