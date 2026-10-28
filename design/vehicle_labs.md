# Vehicle labs: one vehicle plant per faction

Status: **design, step 1 of 2. Nothing built.** lovish approves this doc before step 2 starts. Picture:
`design/vehicle_labs.png` (the shared plan: top view and gate). Numbers marked *(code)* were read from the live code on
Oct 28; numbers marked *(proposal)* are new and wait for lovish; *(assumption)* means the code did not say.

## 1. Why, and the rules

**BAR splits production by kind of unit.** A BAR player opens with a bot lab or a vehicle plant (later an aircraft
plant, a shipyard), and the choice is strategy: bots are cheap and climb hills, vehicles are metal-heavy, sturdy and
fast on the flat. Our Oct 16 lab split (`design/moving_buildings.md` section 2.2: "Unit labs (infantry / vehicle / air
at T1-T2, shared T3)") named this but nothing was built: today every faction has **one** T1 lab that makes everything,
including its beasts and siege.

**lovish's decisions (Oct 28)**, which this doc follows:

- Each faction gets a **separate vehicle lab** (like BAR's vehicle plant), next to today's single T1/T2/T3 lab.
- "Vehicle" is **themed per faction**, not modern machines: Sunforged "Dawnrider" (a light wielder riding a cloud);
  Verdant "Rooted Treant" (an ent that roots and attacks along the ground - prototyped on `claude/rooted-treant`; this
  doc only designs the lab it comes from). Brassworks keeps steam machines. Real tanks belong to a later futuristic
  faction (not one of the 7).
- Vehicles are **metal-heavy and energy-light**.
- Vehicle footprints: **T1 at most 2x2 and 3 tall; T2 at most 3x3 and 4 tall; siege/transport at most 3x4.** Every
  walkable gap in a building is **at least 4 wide** (and 8 tall, or sealed: `design/LANES.md` Oct 20,
  `design/ART-FAULTS.md` "Units trapped").
- The vehicles that exist now are `RaidTuning.isT1Vehicle`: Hoglin, Sentinel Treant, Mudskipper Sloop, Bombard Crew,
  Rocket Battery, Mortar Crawler. Sunforged and Gravebound have none yet.
- Art bar (north star): hyper-realism first with a touch of dark fantasy, judged against the best game in the category;
  faction identity through silhouette and the faction's style kit; readable from the RTS camera.

### 1.1 What exists today *(code)*

Faction names in code: Sunforged = `VILLAGERS`, Gravebound = `MONSTERS`, Horde (Ashborn kit) = `PIGLINS`.

| Faction | T1 lab (BotPlayer `armyBuilding`) | L1 footprint x * z * h (L3 h) | Cost M / E | Build blocks -> builder-s | HP | T2 lab (`t2Lab`) | T3 (`t3Building`) |
|---|---|---|---|---|---|---|---|
| Sunforged | Barracks | 10 x 8 x 15 (25) | 45 / 230 | 117 -> 123 s | 150 | Arcane Tower | Castle |
| Gravebound | Graveyard | 8 x 8 x 9 (21) | 45 / 230 | 99 -> 104 s | 120 | Dungeon | Stronghold |
| Horde | Basic Portal -> Military Portal | 8 x 5 x 7 (20) | 25 / 110 | 50 x 1.2 -> 63 s | 100 | Flame Sanctuary | Fortress |
| Horde (side) | Hoglin Stables | 10 x 8 x 5 (no levels) | 95 / 230 | 128 (its block count) -> 134 s | 150 | - | - |
| Verdant | Grove | 9 x 8 x 14 (24) | 45 / 230 | 287 -> 301 s | 150 | Circle of Elders | Heart of the Wild |
| Tidewrought | Slipway | 9 x 9 x 13 (21) | 45 / 230 | 141 -> 148 s | 150 | Drydock | Abyssal Shipyard |
| Stormcallers | Lotus Academy | 9 x 9 x 9 (23) | 45 / 230 | 181 -> 190 s | 150 | Celestial Observatory | Heaven's Gate |
| Brassworks | Machine Shop | 9 x 9 x 13 (20) | 45 / 230 | 216 -> 227 s | 150 | Assembly Works | Great Forge |

How these were read: footprints from the structure NBT `size`; costs from `ReignOfNetherCommonConfigs` converted by
`ResourceCost.bakeValues` (metal = ore + food x 0.6 + wood x 0.3, energy = wood x 1.5 + food x 0.8; e.g. the labs'
`Building(0,150,0)` = 45 M / 230 E); builder-seconds = `buildTimeBlocks` x `buildTimeModifier` x 21 ticks / 20 (one
worker = build power 1; the commander is 3, per the TierGate comment "commander + 3 workers (6 build power)").
The T2 labs are all 700 M / 6000 E / 480 blocks = 504 builder-s (`TierGate`).

**TierGate today:** a T2 lab needs the Tier 2 research (1000 M / 10080 E / 180 s at the capitol) **and** the faction's
finished T1 lab (`TierGate.t1LabFor`: Barracks, Graveyard, **Hoglin Stables**, Grove, Slipway, Lotus Academy, Machine
Shop). A T1 lab needs only the finished capitol (each build button).

**The vehicles today *(code)*:**

| Vehicle | Faction | Made at (slot) | Cost M / E | Time | Hitbox w x h | Role (balance.md) |
|---|---|---|---|---|---|---|
| Hoglin | Horde | Military Portal (4), needs a finished Hoglin Stables (`HoglinProd`) | 155 / 110 | 35 s | 1.40 x 1.40 | mount / raider |
| Sentinel Treant | Verdant | Grove (3) | 170 / 90 | 45 s | 1.54 x 2.97 | **line** (the Court's only T1 line) |
| Mudskipper Sloop | Tidewrought | Slipway (7) | 95 / 100 | 22 s | 1.54 x 0.97 | amphibious raider |
| Bombard Crew | Tidewrought | Slipway (3) | 125 / 200 | 34 s | 0.6 x 1.95 (crew + mortar) | artillery |
| Rocket Battery | Stormcallers | Lotus Academy (7) | 120 / 210 | 34 s | 0.6 x 1.95 (crew + rack) | artillery |
| Mortar Crawler | Brassworks | Machine Shop (5) | 120 / 210 | 34 s | 0.6 x 1.95 (crew + carriage) | artillery |

All six fit the T1 rule (<= 2x2, <= 3 tall). *(assumption)* the drawn carriage of the three crews is within 2x2; their
hitbox is pillager-sized. Note the three artillery crews are **energy-heavy today** (200-210 E against 120-125 M),
the opposite of lovish's rule - see "Numbers for lovish".

## 2. The shared plan (every faction)

One plan, seven style kits, so the rules are proven once (`design/vehicle_labs.png`):

| Part | Size (blocks) | Why |
|---|---|---|
| Footprint | **11 wide x 13 deep** (incl. a 2-deep paved apron) | BAR plants are bigger than bot labs; odd width gives one centre spawn column |
| Height | L1 12, L2 16, L3 <= 22 (levels via the shared `UPGRADE_LAB_L2/L3`, same as T1 labs) | under each capitol, like the T1 labs' L3 (20-25) |
| Assembly bay | 5 wide x 8 deep, open to the sky, spawn column in the centre | fits a 3x4 siege body with 1 clear each side; spawn column open at every level (the Barracks rule) |
| Exit gate | **5 wide x 8 high**, on the south (camera) side, at every level | >= biggest vehicle (3 wide) + 1 each side, and >= the 4 x 8 walkable rule; sized for T2/siege now so the art never has to be redone |
| Wings | 3 wide each side of the bay, **sealed** (solid machinery, never walkable) | a 3-wide space is under 4, so it must be closed |
| Back works | 3 deep across the north, sealed | gantry anchors, hoist, the faction set piece |
| Gantry / arch over the bay | underside >= 9 high | stays out of the 8-high walkable space |
| Apron | 11 x 2 paved, walkable, inside the footprint | the Oct 20 "exit apron" rule; nothing can be built on it |
| Moving parts (moving_buildings 2.2) | gantry trolley + hoist, 2-3 parts | queue running = motion; unit released = hoist drops |

**Read from above:** every T1 infantry lab today is a *hall plus a yard* (Barracks, Grove, Lotus, Machine Shop) or a
*gate* (Portal, Graveyard). The vehicle lab is a **long open bay between two solid wings with a beam across it**: a
different shape at RTS distance for all seven, before any faction dressing.

**Tier:** the vehicle lab is a **T1 plant**: it needs only the finished capitol, like the T1 lab (Q2). T2 vehicles:
Q1. **Cost / time (all seven, proposal):** 120 M / 150 E, fixed 200 build blocks (210 builder-s: the commander alone 70
s, commander + 2 workers 42 s), 200 HP - see "Numbers for lovish" for the basis.

## 3. The seven labs

### Sunforged - the Sunwain Court
- **Look:** a carriage court in warm light-grey stone with lapis trim; two corner towers with the faction's red cones
  flank the gate; the gantry is a gilded beam carrying a **turning sun disc** (the hoist); the bay floor is a white
  **cloud basin** (calcite rim, soft glow) where Dawnriders' clouds are spun. Warm lanterns in the back works.
- **Above vs the Training Hall:** the Hall is a colonnade with a blue-glass dome over a sand drill yard; the Court is
  an open white basin between two grey wings and two red cones - no dome, no sand.
- **Footprint / gate:** 11 x 13 x 12 (L3 <= 22, kept under the capitol); gate 5 x 8 between the red-coned towers.
- **Cost / time:** shared proposal (Barracks basis: 45 / 230, 123 s).
- **Builds (proposal):** **Dawnrider** - fast T1 raider, a light wielder riding a low cloud, short sun-lance; fills
  the empty raider slot (balance.md role table). **Sunwheel Ballista** - T1 artillery, a gilded bolt-thrower on a
  sun-disc carriage pushed by two acolytes; fills the empty artillery slot. Both <= 2x2x3.
- **Current lab changes:** Barracks loses nothing. BotPlayer: `vehicleLab = SUNWAIN_COURT` only once a vehicle exists,
  `null` until then. TierGate: Arcane Tower accepts Barracks **or** the Court (Q3).

### Gravebound - the Charnel Works
- **Look:** a sunken deepslate pit; a giant **bone ribcage** arches over the bay as the gantry (the moving_buildings
  "bone ribcage crane"); a chained bone bucket is the hoist; a soul-fire cauldron and green lancets glow in the back
  works; chains sway.
- **Above vs the Ossuary Yard:** the Yard is a railed grave plot of headstones with a corner crypt; the Works is a dark
  pit crossed by white ribs - one big repeating rib rhythm, no headstones.
- **Footprint / gate:** 11 x 13 x 12; gate 5 x 8 under the last rib (rib underside >= 9).
- **Cost / time:** shared proposal (Graveyard basis: 45 / 230, 104 s).
- **Builds (proposal):** **Bonecart Catapult** - T1 artillery, a bone-and-sinew catapult on a cart drawn by a skeletal
  horse, lobs soul-fire skulls; fills the empty artillery slot. **Charnel Crawler** - T1 building-breaker, a centipede
  of stitched ribcages (T1 vehicles break buildings: RaidTuning). Both <= 2x2x3.
- **Current lab changes:** Graveyard loses nothing; Spider Lair and Slime Pit stay as they are (Q5). Bot kit:
  `vehicleLab` null until a vehicle exists. TierGate: Dungeon accepts Graveyard **or** the Works.

### Horde (Ashborn) - the Ember Pens (the Hoglin Stables, rebuilt)
- **Look:** Ashborn kit only (basalt, tuff, a little obsidian, bronze trim, glowing cracks, fire bowls - no nether
  brick): basalt stalls along both wings, a glowing cracked floor in the bay, a bronze chain gate, a basalt beam with a
  bronze chain hoist holding hoglin barding. Today's Hoglin Stables is still Reign of Nether art (nether-brick fence,
  crimson stem), which `LANES.md` (Oct 20) rules out anyway.
- **Above vs the Ember Gate:** the Gate is a stepped volcano cone with a fire door; the Pens are a long glowing floor
  between two stall rows - flat and lit, not a cone.
- **Footprint / gate:** 11 x 13 x 12 (was 10 x 8 x 5); gate 5 x 8. The registry id `hoglin_stables` stays (old saves,
  `BuildingSaveData`); the display name changes.
- **Cost / time:** shared proposal (basis: Hoglin Stables 95 / 230, 134 s; Military Portal 25 / 110, 63 s).
- **Builds:** **Hoglin** (moved from the Military Portal); `RESEARCH_HOGLIN_CAVALRY` stays here.
- **Current lab changes:** Military Portal loses `HOGLIN` (slot 4); `HoglinProd`'s "needs a finished Hoglin Stables"
  check becomes moot. Bot: `vehicleLab = HOGLIN_STABLES`, `vehicleArmy = [HOGLIN]` - Horde bots train Hoglins for the
  first time (`army` is Brute and Headhunter only today). The `t1Req != armyBuilding` special case in `BotPlayer.think`
  (raise the Stables when T2 research starts) is replaced by the normal vehicle-lab rule. TierGate: Flame Sanctuary
  accepts the Military Portal **or** the Pens (today: the Stables only).

### Verdant - the Rooting Hollow
- **Look:** "the base is a forest": no machinery. A sunken bed of dark rooted soil, mud and moss; great **buttress
  roots** arch over the bay (the gantry; underside >= 9) between standing stones with faint glyphs; the wings are
  root-bound earth banks. Moving part: the roots tighten and a treant is **pulled up out of the soil** at the spawn
  column (the hoist). No grass under full blocks (the Pagoda stall rule).
- **Above vs the Stag Glade:** the Glade is a bright froglight glade under the Mother Tree's canopy; the Hollow is a
  dark earth bed under bare root arches - no canopy, darker value.
- **Footprint / gate:** 11 x 13 x 12; gate 5 x 8 through the last root arch.
- **Cost / time:** shared proposal (Grove basis: 45 / 230, 301 s - the slowest T1 lab).
- **Builds:** **Sentinel Treant** (moved from the Grove); the **Rooted Treant** when `claude/rooted-treant` lands
  (that session decides whether it joins or replaces the Sentinel).
- **Current lab changes:** Grove loses `SENTINEL_TREANT` (slot 3) and its Javadoc line. Bot: `army` loses
  `SENTINEL_TREANT`; `vehicleArmy = [SENTINEL_TREANT]`. TierGate: Circle of Elders accepts Grove **or** the Hollow.
  Balance note: the Sentinel is the Court's only T1 line, so a Verdant opener now needs the Hollow early.

### Tidewrought - the Careening Hard
- **Look:** a beach hard of sand and gravel; a coral-crusted hull **hauled over on its side** along the back works (a
  ship being careened); timber shear-legs over the bay with block-and-tackle (the gantry and hoist); a big capstan in
  one wing; sea lanterns. No water in the walkable bay (the rope-boom rule); a tide pool only inside a sealed wing.
- **Above vs the Slip:** the Slip is an upright hull along the north over a basin; the Hard is a hull lying on its side
  plus a round capstan - the tilted hull is the read.
- **Footprint / gate:** 11 x 13 x 12; gate 5 x 8 between the shear-legs' feet.
- **Cost / time:** shared proposal (Slipway basis: 45 / 230, 148 s).
- **Builds:** **Mudskipper Sloop** and **Bombard Crew** (moved from the Slipway).
- **Current lab changes:** Slipway loses slots 3 and 7. Bot: `army` loses both (`BOMBARD_CREW`, `MUDSKIPPER_SLOOP`);
  `vehicleArmy = [MUDSKIPPER_SLOOP, BOMBARD_CREW]`. TierGate: Drydock accepts Slipway **or** the Hard.

### Stormcallers - the Thunder Arsenal
- **Look:** a sandstone powder yard with flat grey-tile roofs; one round brick **kiln tower with a jade dome** and a
  lightning rod in a back corner; red lacquered gate posts; a bronze rocket-rack frame rides the gantry and tilts up
  when a battery is released; sparks at the kiln.
- **Above vs the Lotus Court:** the Court is one big curved jade roof and a torii; the Arsenal is a flat sandstone yard
  with one small round jade dome off-centre.
- **Footprint / gate:** 11 x 13 x 12; gate 5 x 8 between the red posts (no lintel under 8).
- **Cost / time:** shared proposal (Lotus basis: 45 / 230, 190 s).
- **Builds:** **Rocket Battery** (moved from the Lotus Academy).
- **Current lab changes:** Lotus Academy loses slot 7. Bot: `army` loses `ROCKET_BATTERY`; `vehicleArmy =
  [ROCKET_BATTERY]`. TierGate: Celestial Observatory accepts Lotus Academy **or** the Arsenal.

### Brassworks - the Engine Shed
- **Look:** steam machines: a long brick-and-iron shed with a big **arched door** to the camera, rails through the
  bay, a travelling gantry crane with chain and hook, a riveted water tower and coal stage in the back works, one tall
  stack. The roof over the bay is split by an open smoke-vent strip, so the spawn column stays open to the sky.
- **Above vs the Machine Shop:** the Shop is an L of buildings round an open yard with a turntable; the Shed is one
  long straight roof and a round water tank - no L, no turntable.
- **Footprint / gate:** 11 x 13 x 12; arched door 5 wide x 8 high at the springing (higher at the crown).
- **Cost / time:** shared proposal (Machine Shop basis: 45 / 230, 227 s).
- **Builds:** **Mortar Crawler** (moved from the Machine Shop). Candidate: Steam Runner (a stilt-legged boiler machine,
  not in `isT1Vehicle`) - Q5.
- **Current lab changes:** Machine Shop loses slot 5. Bot: `army` loses `MORTAR_CRAWLER`; `vehicleArmy =
  [MORTAR_CRAWLER]`. TierGate: Assembly Works accepts Machine Shop **or** the Shed.

### Summary

| Faction | Vehicle lab | Footprint | Builds (T1) |
|---|---|---|---|
| Sunforged | Sunwain Court | 11 x 13 x 12 | Dawnrider, Sunwheel Ballista *(proposal)* |
| Gravebound | Charnel Works | 11 x 13 x 12 | Bonecart Catapult, Charnel Crawler *(proposal)* |
| Horde | Ember Pens (rebuilt Hoglin Stables) | 11 x 13 x 12 | Hoglin |
| Verdant | Rooting Hollow | 11 x 13 x 12 | Sentinel Treant (+ Rooted Treant) |
| Tidewrought | Careening Hard | 11 x 13 x 12 | Mudskipper Sloop, Bombard Crew |
| Stormcallers | Thunder Arsenal | 11 x 13 x 12 | Rocket Battery |
| Brassworks | Engine Shed | 11 x 13 x 12 | Mortar Crawler |

## 4. Cross-cutting

**Bots (`BotPlayer`).**
- `Kit` gains `Building vehicleLab, List<ProductionItem> vehicleArmy` (null / empty for Sunforged and Gravebound until
  their vehicles exist; every use null-checks, like `tower`). The six vehicles leave `army` (table above).
- Build order: after "the first lab from minute 1", a new rule places the **vehicle lab as the second lab** (minute 3+,
  one infantry lab built). After that `targetLabs` counts both kinds and `placeLab` alternates 2 infantry : 1 vehicle
  *(proposal)*. `isLab` / `countLabs` count vehicle labs, so levelling and annexes work for both.
- Production: the loop that queues `kit.army()` at the army lab (`BotPlayer` ~1372) gets a twin for `vehicleArmy` at
  the vehicle lab. `BotArmy`'s vehicle raid already keys on `RaidTuning.isT1Vehicle` - unchanged.
- The Horde special case (raise the Hoglin Stables when T2 research starts) goes; the Pens are its second lab.

**TierGate.** `t1LabFor(b)` becomes `t1LabsFor(b)`: a T2 lab needs Tier 2 and **either** finished T1 lab (Q3).
`placementError`, the build buttons' grey-out and the bot's `ownsFinished` check use the set. The vehicle lab itself is
not gated (capitol only). For T2 vehicles see Q1: the recommended T2 vehicle plant joins `t2Labs()` / `t2LabCosts()`
so it shares the 700 / 6000 / 480 numbers and the "needs Tier 2" production check.

**Build menu and hotkeys.** Registered in `Factions.registerBuildings` with `BuildGroup.MILITARY`, **directly after the
faction's T1 lab**, so it is X then W (the infantry lab stays X then Q) - BAR's grid puts the plants side by side.
Every later Military building moves one cell; `BuildCategoryKeysGameTests` / `BuildGroupGameTests` are checked and
updated. Inside the vehicle lab the vehicles take ability slots 1, 2, ...; the infantry labs **keep** their other items'
keys (the freed slot stays empty - no relearning).

**Art.** Each lab goes through the art pipeline (concept, `tools/lab_<name>.py`, eyes stills at RTS / close-up /
dusk, the ART-FAULTS review, then lovish). Same machinery as `gen_lab_levels` (three levels). Every level re-checks the
4-wide rule, the 5 x 8 gate and the open spawn column.

**Balance notes (no number changes without lovish).** The move itself changes cost of access: a player who wants the
vehicles now pays a second lab. That hits Verdant hardest (its line unit moves) and Tidewrought least (two vehicles,
three other infantry roles). The artillery crews break "energy-light" today.

### Numbers for lovish

| What | Today *(code)* | Proposed | Basis |
|---|---|---|---|
| Vehicle lab cost | T1 labs 45 M / 230 E; Hoglin Stables 95 / 230 | **120 M / 150 E** | metal-heavy, energy-light: 2.7x the T1 lab's metal, 0.65x its energy; about BAR's plant-to-lab ratio *(assumption: BAR ratio from memory)* |
| Vehicle lab build | T1 labs 50-287 blocks (63-301 builder-s), median 141 | **200 blocks fixed** (210 builder-s) | 1.4x the median; a fixed count like `T2_LAB_BUILD_BLOCKS` so art detail never changes build time |
| Vehicle lab HP | T1 labs 100-150 | **200** | plants are sturdier than labs |
| Bombard Crew | 125 M / 200 E | 145 / 120 | same 34 s; energy below metal |
| Rocket Battery, Mortar Crawler | 120 M / 210 E | 140 / 120 | as above |
| Mudskipper Sloop | 95 M / 100 E | 105 / 70 | as above |
| Hoglin, Sentinel Treant | 155 / 110, 170 / 90 | unchanged | already metal-heavy |
| Bot lab mix | one lab type | 2 infantry : 1 vehicle | BAR openers: bot lab first, plant second |

## 5. Open questions for lovish

1. **Where do T2 vehicles come from?** (a) *Recommended:* a separate **T2 vehicle plant** per faction, gated exactly
   like the T2 lab (Tier 2 + the finished T1 vehicle lab, 700 M / 6000 E / 480 blocks), made later as step 3 - BAR's
   advanced plant; until then T2 beasts (Elder Treant, Mossback Bison, Gorehorn Rhino, Steam Juggernaut...) stay in
   the T2 lab. (b) The vehicle lab **refits in place** to T2 after the Tier 2 research (like the extractor refit): no
   seven new buildings, less BAR. (c) T2 vehicles stay in the T2 lab for good.
2. **Can a player open with the vehicle lab?** (a) *Recommended:* yes, it needs only the capitol, like BAR's plant.
   (b) It needs a finished infantry lab first.
3. **Which T1 lab unlocks the T2 lab?** (a) *Recommended:* **either** (infantry or vehicle lab), so a vehicle opener
   can tech; this also fixes the Horde oddity (its T2 lab needs the Hoglin Stables today). (b) The infantry lab only.
   (c) Keep today's table (Horde keeps needing the Pens).
4. **Sunforged and Gravebound vehicles:** (a) *Recommended:* approve **one each** first (Dawnrider; Bonecart
   Catapult), designed in their own unit sessions; the lab ships with it. (b) Approve both proposals each (adds the
   Sunwheel Ballista and Charnel Crawler). (c) These two factions get no vehicle lab until their units exist.
5. **Which units count as vehicles?** (a) *Recommended:* only the six `isT1Vehicle` units for step 2. (b) Also other
   machines and beasts now (Brassworks Steam Runner and Flak Cart; Gravebound spiders, folding the Spider Lair into
   the Charnel Works).

## 6. Build order for step 2 (after approval)

Tests first (LANES.md, Oct 22): each test is written and seen to **fail** before the implementation. New GameTests go
in their own class, `VehicleLabGameTests`, registered in the shard lists (`tools/check_shards.py`).

**Verdant first (one PR):**
1. Tests: `rooting_hollow_gate_clears_vehicles` (place L1, L2, L3: every walkable gap >= 4 wide x 8 tall or sealed,
   gate >= 5 x 8, spawn column open to the sky, a 2x2 and a 3x4 body walk from spawn to past the apron - the
   `verdantStructureFreesUnits` pattern); `rooting_hollow_trains_sentinel_treant`; `grove_no_longer_trains_sentinel_
   treant` (not in its list, a crafted queue refused); `verdant_bot_builds_rooting_hollow` (bot kit has it, a soak
   places it as the second lab and queues a Sentinel there); `circle_of_elders_accepts_either_t1_lab` (if Q3 = a).
2. Implementation: building class, structure (`tools/lab_rooting_hollow.py` + levels), `Buildings` / `ResourceCosts` /
   config entry / `BuildingSaveData` / lang / icon, `Factions.registerBuildings` after the Grove, Grove production
   list, BotPlayer kit + rules, TierGate set. Update existing tests that assume the Grove trains Sentinels.
3. Art loop to the bar above; eyes stills in the PR.

**Then, one PR each, same test set:** Tidewrought (Careening Hard), Stormcallers (Thunder Arsenal), Brassworks (Engine
Shed) - moves only; Horde (Ember Pens: rebuild + move + the TierGate/bot special case removed, plus an old-save load
test for `hoglin_stables`); Sunforged and Gravebound last, after their vehicles (Q4) are designed. Q1's T2 vehicle
plant is step 3.

## Assumptions (the code did not say)
- The three crews' drawn carriages fit 2x2 (hitboxes are 0.6 wide).
- BAR's plant-vs-lab cost ratio is quoted from memory, not from BAR's unit files.
- No vehicle constructor: workers come from the capitol in this game, so the plant trains only vehicles.
- Air labs (the other half of the Oct 16 split) are out of scope.
