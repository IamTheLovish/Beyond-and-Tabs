# Moving buildings: machinery that is drawn, not wired

lovish (Oct 16): *"The building redesign should include possible animations from what you think is best or moving
pieces like using redstone to create animations or effects with piston and blocks, maybe you can also download a
texture pack or create something to add this."* Same message: *"Metal extractors are hard to see"* and *"I want the
game to be playable and as optimized as possible"*.

Picture: `design/moving_buildings.png` (the extractor rig pumping / resting / damaged, then a lab, a tower, a gate and a
capitol with their moving parts). Builds on `design/lively_buildings.md` (energy buildings already move) and
`WindmillRenderClientEvents`. Status: concept, nothing built.

## 1. The verdict: draw the machinery, don't wire it

**In plain words:** every moving piece should be *drawn by each player's own game* on top of a building that never
changes: real Minecraft models (piston heads, chains, hoppers, rails, bells, grindstones) moved by code. Real redstone
would move real blocks, and in this game a block that changes is expensive and confuses five other systems.

| | Real redstone / pistons in the world | Rendered parts (today's sails, extended) |
|---|---|---|
| Server tick | dust and observers update neighbours; every stroke = a block event, a `moving_piston` block entity, 2-3 block changes | none: client only |
| Network | every stroke goes to every client tracking the chunk; on a fog-edge chunk a part-visible section update is dropped and a **full-chunk resend** queued (`FogOfWarServerEvents.shouldSendChunkPacket`) | none: each client computes the same pose from game time |
| Pathfinding | each `setBlockState` marks the walkability chunk dirty (`LevelChunkMixin`), patched 4 ticks later; the drain patches at most 8 chunks a tick, so 100 pumping extractors (~80 patches a second) eat half of it and real changes - a wall going up, a building falling - wait behind them; a piston head entering a cell changes collision | untouched |
| HP and repair | `BuildingBlock.isPlaced` wants the exact state (only gates, plates, sculk, stairs/fences/walls/bars are lenient): an extended piston, a lit lamp, a powered rail read as **missing blocks** - HP drops and workers walk over to "repair" | parts are not blocks; a part whose anchor block is gone is simply not drawn |
| Build and damage | RoN lays and removes blocks one at a time: a circuit runs half-built and half-destroyed, a piston can fire into a cell the builder has not reached and push building blocks | parts appear on `isBuilt`, vanish with the anchor |
| Multiplayer, saves | tick-exact on the server only; pistons are vanilla's best-known source of ghost blocks and dupes; saves hold half-pushed states | nothing to sync or save |
| Look | 1-block steps, 2-tick moves, vanilla behaviour only | any speed or amplitude, eased, tied to state - still vanilla models |
| Player control | none | **Building animation: Off / Low / Full** |

**What real redstone may still do:**
- **The existing exception, kept:** a block may change state only when the change *is* the gameplay (collision or
  pathing), only on a state change (never a loop), with flags `2|16`, and with `isPlaced` leniency for it - exactly
  how `WallGate` swings its fence gates and traps press their plates.
- **Static redstone-look blocks** are fine: an unpowered piston as a housing, a lamp lit by a redstone block (RoN's
  Arcane Tower does it). Never observers (they pulse when a neighbour is placed), detector or powered rails, or a
  consumer touching a source by accident; a structure check enforces it (my scan of all structures: only RoN's
  intentional lamp + redstone-block pairs).
- **Client-side pokes of vanilla block entities**: a real bell's swing (`BellBlockEntity`), a chest lid - animated
  locally, no state change.
- **A one-off showpiece** (a capitol court clockwork) only where no match runs: the lobby or the eyes showcase world.
  Recommendation: none in matches at all.

**Performance rules** (cost grows with buildings on screen, never with units):

| Rule | Today | Proposed |
|---|---|---|
| Built only, never in strategic zoom | yes | keep |
| On screen | not checked: anything within 220 blocks is drawn | frustum test per placement (cached AABB, `evt.getFrustum()`) |
| How many | all within range | nearest 64 (Full) / 24 (Low), the scaffold renderer's fixed-array pick |
| Pixel size | none | LOD from the ortho zoom: a part under 3 px is skipped, under 10 px drawn simplified (4 spokes, no cart) |
| Fog | parts keep moving on remembered buildings | outside a bright chunk: frozen pose, no particles (visibility cached every 10 ticks, like scaffolds) |
| Clock | `gameTime x speed + phase` | per-placement phase += speed x dt, so a speed change never jumps; phase seeded from the position (no lockstep) |
| Allocation | `pushPose` + `renderSingleBlock` per box | the scaffold path: cached `BakedQuad`s, pose rewritten in place, one `RandomSource` - zero bytes per frame |
| Particles | ambience budget (40 emitters / 4 ticks) | same budget shared by damage smoke and state effects |

Settings: **Off** = no parts, no building particles (every structure must look whole without its parts); **Low** =
nearest 24, LOD on, no carts or secondary parts, particles halved; **Full** = everything. `building_ambience=false`
migrates to Off. Budget per slice: **parts <= 0.5 ms/frame and 0 bytes allocated on the render thread with 60
animated buildings in view**, read from an eyes `[Render]` notice.

## 2. Per building role

### 2.1 Metal extractor (first, most detail)

Why it is hard to see: 5-8 tall in ground colours (Verdant moss on grass, Brassworks soot), and no banner
(`fliesBanner` skips it), no idle life (`isLively`), no motion; the gem leaves when it is built. The ring is all
that marks it.

**The rig grammar** (every faction, own skin): an open headframe on the inner 3x3 (posts, back braces only, so the
camera's west and south faces stay open and units behind it stay visible), 9-11 tall at T1, 12-13 at T2 (every T1
block kept: `check_mex_tiers`). Footprint, HP (fixed `maxHealth`), build time (fixed `buildTimeBlocks`) unchanged.

| Part | Drawn with | Anchor | Motion | Reads at ~36 px/block / ~10 px/block | Setting |
|---|---|---|---|---|---|
| Sheave wheel | boxes (spokes, brass rim) | axle between two crown posts | turns as the skip travels | spokes turning / a round silhouette | Low+ |
| Ore skip | hopper model + raw-iron lump, **fullbright** | empty shaft column, hung from the crown | 5 blocks up and down, tips at the top | big vertical move / **a moving light** | Low+ |
| Piston stamp | real unpowered piston + rendered `piston_head` | the piston block | 1-block stamp on an ore block, dust puff | the Minecraft piston / not visible | Full |
| Ore cart | hopper/minecart model on a straight rail (rails re-shape when placed: `isPlaced` leniency, or a drawn track) | rail cells, camera side | 2-block shuttle to a bin | rolling cart / a moving speck | Full |
| Pennant | the banner ribbon, **owner colour** (`PlayerColors`) | mast top | ripples | owner colour high up / a colour patch | Low+ |

**Pace = income:** stroke period = 2.4 s x sqrt(2 / metal per s), clamped 0.9-2.4 s. A T2 on a centre patch (14 m/s)
races a T1 on a lean one; rich patches already wear a double ring, now they also run faster.

**Tie to the ring and the gem:** the gem is raw iron, fullbright; the skip carries the same block, fullbright. The
player learns one thing: *that light is metal*. On completion the gem "lands" (a one-off flash on the crown).

| State | What you see | Client data (no new packet) |
|---|---|---|
| Under construction | scaffold (exists); no parts | `isBuilt`, blocks placed |
| Pumping | all parts running at the income pace | `MetalPatchesClient.valueOf`, `getUpgradeLevel` |
| Resting (your storage full) | skip parked, wheel still, cart empty, pennant limp: *you are wasting metal* | `ClientEconomy.metalWasted > 0` - owner, allies and observers only; the enemy always sees it working |
| Upgrading to T2 | pump stops, a jib on the corner swings, welding sparks | `UPGRADE_EXTRACTOR` in the queue |
| Damaged | <75% wisps; <50% smoke and sparks, the skip jams 1 stroke in 3; <25% flame flicker (particles, never fire blocks) | `getHealth / getMaxHealth` |
| Fogged | frozen, grey pennant (like the grey ring) | `isInBrightChunk` |

**Faction skins:**

| Faction | Frame | Stroke part | Rotary part | Glow |
|---|---|---|---|---|
| Sunforged | quartz-and-gold bell frame | gilded skip | sun wheel; a small bell rings every 4th stroke | gold glints |
| Gravebound | bone ribcage crane | chained bone bucket | bone winch | soul fire in the bucket |
| Horde | blackstone-and-bone derrick | magma-bit piston hammer | chain drum | molten ore, smoke |
| Verdant | roots gripping the core (no machinery: "the base is a forest") | the core swells and settles | fronds sway | sap pulse up the roots |
| Tidewrought | dredge pulley (exists) | cauldron bucket on its chain | pulley wheel | sea glow, drips |
| Stormcallers | ley shrine (exists) | lodestone drill rises and falls | stones orbit the drill | arcs to the rod |
| Brassworks | soot gantry (exists) | its real piston hammer, now rendered stroking | flywheel | firebox flash, steam per stroke |

Cost: Full ~190 quads, Low ~90, Off 0; zero server time and packets.

### 2.2 Every other role

| Role | What moves | Why it reads from the RTS camera | What it tells | Cost |
|---|---|---|---|---|
| Capitol | one hero piece (sun halo, soul belfry, portal braziers, gear medallion, crown sway) + banner (exists) | the biggest building gets the slowest, largest motion | trains: rays pulse; hit: real bell swings client-side; each capitol upgrade adds a part | 1-2 parts |
| Energy (done) | sails, wheels, bell, petals, vanes | - | improvements: frustum, fog freeze, zero allocation, LOD (2 blades far), one shared gust clock so a wind farm surges together; a Geothermal vent cap that lifts on each steam puff | as today |
| Unit labs (infantry / vehicle / air at T1-T2, shared T3 - the Oct 16 lab split) | infantry: door or portcullis lifts when a unit leaves; vehicle: gantry trolley and hoist; air: roost elevator and windsock; T3: crane arms over a frame that rises with progress | a door opening is the moment a unit appears; cranes are long and thin | queue running = motion; x1.75 L2, x2.5 L3; slower with your stall; unit released (`completeProduction`) | 2-3 parts |
| Research | one rotary piece: armillary, telescope, grindstone, tesla arc | rotation reads at any size | researching = turning; done = a flourish | 1 part |
| Defences | turret head swings to the target, recoils | a head turning is the first sign of a fight | aims at the `to` of the BarFx shot event the client already gets, recoil on it; slow sweep idle | 1 part |
| Traps | nothing, ever | they are hidden | - | 0 |
| Walls / gates | walls: nothing (hundreds of segments); gates: a drawn portcullis or door rides with the real gate | the gate is the wall's only state | open for you / shut for the enemy | gates only |
| Storage / converters | vault: a heap that rises with fill; converter: crucible pours while converting | a fill level is a gauge | own/allied: fill and conversion; enemy: fixed half | 1 part |
| Annexes | a working arm toward its lab | links annex to lab at a glance | assisting = moving | 1 part |
| T3 / game enders | one slow set piece each (Great Forge trip hammer, Heaven's Gate rings, Shipyard gantry, Heart of the Wild pulse) | announced, rare, big | charging / producing | 2-3 parts |

## 3. Per faction signature motion

| Faction | Signature | Example |
|---|---|---|
| Sunforged | bells, slowly turning sun discs, banners | Barracks L3: the belfry bell swings each time a unit leaves |
| Gravebound | guttering soul braziers, swaying chains, bone cranes | Graveyard: a bone crane lowers a coffin while a unit rises |
| Horde | war drums, spit roasts, smoke | Military Portal L2: bastion drums beat faster as the queue runs |
| Verdant | growth: vines creep, canopies sway, spores glow | Grove: canopy sways on the gust clock, heart-glow pulses per unit |
| Tidewrought | waterwheels, tide pumps, foam | Slipway: the crane swings its cargo barrel, the hull rocks |
| Stormcallers | spinning rods, arcs, floating stones | Lotus Academy: prayer wheel turns, a spark arcs between the gate rods per unit |
| Brassworks | gears, pistons, steam, flywheels | Machine Shop flywheel; Foundry Hall's gear medallion turns |
| RoN Villagers buildings | grindstones, bellows, flags | Blacksmith: grindstone spins while researching |
| RoN Monsters buildings | tesla arcs, cobwebs, spawner cages | Laboratory: arcs between its rods while researching |
| RoN Piglins buildings | portal swirl (vanilla), bellows, steam vents | Basalt Springs: the vent cap lifts on each puff |

## 4. State-driven effects (every building, one table)

| State | Signal | Every building | Who sees it |
|---|---|---|---|
| Construction | `isBuilt`, blocks placed | scaffold (exists); parts appear at completion | anyone in sight |
| Producing | queue non-empty (sent to anyone who sees the building) | working part runs; speed x level | anyone in sight |
| Upgrade in progress | `UPGRADE_LAB_L2/L3`, `UPGRADE_EXTRACTOR` in the queue | working part stops; scaffold ring + a swinging crane | anyone in sight |
| Damaged | HP <75 / <50 / <25% | wisps / smoke + sparks / flame flicker; the working part stutters | anyone in sight |
| Low power | `EconomyClientEvents.getStall` (owner, allies, observers) | working motion slows to the stall; stops at 0 | own and allied only |
| Storage full | storage / wasted | extractors rest, converters pause | own and allied only |
| Fogged | `isInBrightChunk` | frozen pose, no particles | - |

## 5. "Download a texture pack, or create something"

**Create.** We already make our own pack: `tools/gen_blocks.py`, procedural 128x with LabPBR maps, and its water
already animates (32 frames, the `_n` and `_s` maps animated too). The moving parts are our own code. Nothing to
download.

**Animated textures** (`.mcmeta` frames) are the cheapest motion there is: the cost is per texture, not per block -
one atlas update when a frame changes, however many buildings show it. Memory: 128x128 x 3 maps = ~192 KB per frame,
so 8-16 frames with per-frame times (long holds, short flickers) and `"interpolate": true`. Caveat: overriding a
vanilla texture animates every block of that type in the world, so use blocks that live mostly in our buildings.

| Texture | Where | Effect |
|---|---|---|
| lantern, soul_lantern | every faction | **restore the flicker**: our pack's lanterns are single frames with no `.mcmeta` (only water has one), so with the pack on, whatever flicker vanilla gives is gone - check against Mojang's jar in pack-study |
| blast_furnace_front_on, smoker_front_on | fireboxes (Brassworks, Sunforged extractors, labs) | furnace glow breathing |
| magma_block | Horde, Machine Shop forge | slow crawl |
| sea_lantern | Tidewrought | shimmer |
| raw_iron_block, raw_gold_block | **every metal patch** | a glint sweeping across the ore every ~3 s: free patches read better (underground veins glint too: harmless) |
| emission channel (`_s`) on runes and lancets | Sunforged, Stormcallers | glow pulse under shaders only |

**Never download** other people's packs or models: the hard rule is original art only (no recolours), and licences.
Downloads are only from official sources (Mojang's jar for reference data on CI; shader packs from Modrinth with the
sha512 checked) and only with lovish's OK each time.

## 6. Build order

| Slice | Contents | Checks | Budget |
|---|---|---|---|
| 1 | `PartRenderer` (scaffold-style draw, frustum, fog freeze, LOD, Off/Low/Full); **extractor rig** for all 7 factions (structures + a `BuildingParts` table of anchors); owner pennant on extractors; **damage smoke for every building** by HP band | `tools/check_parts.py` (anchors, room to move); GameTests `every_building_part_sits_on_its_anchor`, `damage_band_follows_health`, `rig_pace_follows_income` (pure functions), structure check "no redstone consumer touches a source"; `check_mex_tiers`, pocket audit; eyes scene "metal row" (6 extractors, two zooms, one damaged, one resting) + clip | <= 0.3 ms/frame for 40 rigs in view, 0 bytes/frame |
| 2 | labs: working motion, level and stall speed, unit-release door, upgrade crane | GameTest `lab_part_speed_follows_level_and_stall`; eyes: a lab training at L1 and L3 | <= 0.5 ms for 60 buildings |
| 3 | turret heads (BarFx shot target + recoil), gate portcullis riding the real gate, anti-T3 winch | GameTest: the gate part follows the gate state; eyes: a raid on a tower line | same |
| 4 | capitols, research, storage, converters, T3 set pieces; animated textures (lanterns, fireboxes, magma, sea lantern, ore glint) | `materials.check_labpbr` on every frame; face-census; eyes stills with and without shaders | atlas +3 MB max |
| 5 | energy buildings move onto `PartRenderer` (then `WindmillRenderClientEvents` goes); gust clock; Geothermal vent cap | existing sail/motion GameTests unchanged; eyes diff of the energy shots | 0 bytes/frame |

## 7. Open questions for lovish

1. **Real redstone in matches?** Recommended: **no** - everything drawn; real blocks change only where collision is
   the gameplay (gates). Alternatives: one capitol court showpiece, or a lobby-only diorama.
2. **Extractor height?** Recommended: **a 9-11 tall open headframe** (T2 12-13) - tall enough to read, see-through so
   it hides no units. Alternative: keep 5-8 and rely on motion, light and pennant.
3. **Owner-colour pennant on extractors** (grey under fog, like the ring)? Recommended: **yes**; later every
   building's banner could carry an owner-colour trim beside the faction colours.
4. **Show "your metal storage is full" by resting your extractors?** Recommended: **yes**, own and allied only.
5. **Default setting?** Recommended: **Full, dropping to Low by itself below 40 fps** (with a one-line notice).
   Alternatives: Full fixed, or Low by default.
6. **Animated ore glint on raw iron and gold** (every patch, and underground veins)? Recommended: **yes**.

## 8. Found in the code (changes the plan)

- The life layers skip extractors (banner, idle life, motion) - the main reason they read as dead stone.
- `WindmillRenderClientEvents` says its loop allocates nothing, but the scaffold renderer's own note says `pushPose`
  allocates two matrices per box in 1.20.1, and `renderSingleBlock` builds a `RandomSource` per call: sails, wheels,
  banners and the gem allocate every frame. Slice 1's `PartRenderer` copies the scaffold path; slice 5 moves the
  mills onto it.
- No frustum test anywhere in the part, banner or gem code; parts ignore fog (a dead enemy mill under fog keeps
  turning).
- The gem and the banners translate by `MC.cameraEntity` - the pattern `WindGenerator`'s javadoc blames for sails
  floating off their towers (the rings use the render camera). Check in an eyes still.
- `MetalPatchMarkerClientEvents.buildingOn` walks every building for every patch, twice a frame; with metal now
  spread over the map, cache it every 10 ticks.

## 9. Statues: sculpted figures drawn like units (Oct 20)

lovish on the White Angel capitol v4's 13 x 13 block angel: "the angel looks bad" - he chose a real sculpted statue,
drawn as a model like the units, in stone, on the dome, its wings perhaps unfolding as the capitol levels up.

- **The figure** is made with the unit pipeline: `tools/gen_angel_statue.py` (robed angel, feathered wings spread and
  reclined, halo, sword planted point-down with both hands on the grip - the "guardian at rest") ->
  `design/models/white_angel_statue.json` -> `tools/dsl_to_game.py` -> the generated `WhiteAngelStatueModel` and its
  painted marble atlas (`materials.py` "stone": cloud, veins, worn arrises). 662 cubes, 64 of them "lod" detail.
- **Where it stands:** `BuildingParts.STATUES` - a row per statue: base structure name (matches its `_l2`.. `_l9`
  levels too), anchor cell in the structure's own frame, height in blocks (16, soles to wing tips), the model's own
  height in px (the scale), half span (culling). The White Angel row is keyed to `capitol_white_angel` (the name the
  v4 capitol will be exported under) at `capitol_white_angel_v4.STATUE_ANCHOR` = (33, 91, 14), the air cell on the
  dome's plinth. `tools/check_parts.py` keeps the row equal to the generator's anchor, the anchor air on solid plinth
  at every level, and the row's model height equal to the model's; the GameTest
  `statue_rows_follow_levels_and_wings_unfold` pins the name matching and the wing maths.
- **Drawn by** `building/StatueRenderClientEvents`: built and explored placements only, never in strategic zoom,
  frustum-culled, within 260 blocks, "lod" detail off past the units' detail zoom; one draw of the baked model with the
  mipmapped entity cutout type. Always drawn (the building is not whole without it); "Building animation" Off holds
  the wings still.
- **Motion:** `BuildingParts.statueWingFold` - the wings stand half open at L1 and spread a little more each level
  (fully at L6), easing open over 6 s after a level-up, and breathe +-1.2 degrees over 40 s.
- Preview: `python3 tools/angel_statue_preview.py --out design/buildings/angel_statue_v1.png` (close-ups, and the
  statue on the capitol at the RTS camera, L6 and L1, and at full zoom-out).
