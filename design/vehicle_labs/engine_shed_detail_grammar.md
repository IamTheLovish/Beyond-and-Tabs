# Engine Shed T1 - the detail grammar for round 8 (architecture study, Oct 28 2026)

Scope: research and design only. Massing is FROZEN (plot 17 x 25, h <= 26; cone outline 11 15 19 23 25 23 19 15 11
over z 8..16; lane x 0..8 z 10..14 y 1..8 clear; spawn column (8,*,12) air; halls z 3..7 / 17..21 eave y 7 ridge
y 9-10; swept wings; shed gable x 11..16 ridge y 6; needle y 25). Everything below is detail ON that envelope; the only
outward growth is the 1-block eave overhang the brief allows.

Grid reminder: x = depth from the front (x 0 = west face, the lawn row), z = across (0..24), y = up. The RTS camera
looks NE from the SW: it sees the west face, the SOUTH face (z 24) and, most of all, ROOFS (shed ~40 %, hall roofs,
the south wing). Block shapes are given as vanilla 1.20.1 ids plus Macaw's shapes; every `mcwroofs:` / `mcwwindows:` /
`decorative_blocks:` / `chipped:` id is an ASSUMPTION - **to verify** against the CI id list.

Sources (paraphrased): Historic England list entry for the Derby railway workshop (blind arcades on a shallow plinth,
gauged-brick arch heads springing from a plain impost band, pilasters dividing gable bays, a raking double band under
each gable apex, slate roof) https://historicengland.org.uk/listing/the-list/list-entry/1230740 ; Westeroscraft
exteriors guide ("a roof that cuts straight to the wall looks unfinished"; depth must serve structure - overhangs,
reveals, sill projections; wooden roofs need a base plus an alternate weathering type; sun-bleach on exposed ridges,
water damage at drainage points and eaves, never random discoloration)
https://westeroscraft.com/docs/building/fundamentals/exteriors ; slate ridge practice (ridge tiles bedded in a true
line, or a lead roll ridge) https://www.cupapizarras.com/uk/resource-centre/faqs/slate-roof-ridges/ ; Macaw's Roofs
shapes (roof, steep roof, lower roof, top roof, gutters, awnings) https://minecraft.how/mod/macaws-roofs and
https://www.curseforge.com/minecraft/mc-mods/macaws-roofs .

---------------------------------------------------------------------------------------------------------------------
## (a) Diagnosis - why r7 reads as a toy, not a building

Measured against ref18 (230 x 180 px; the one primary) and against real brick engine sheds.

1. **No roof thickness, no eave, no gutter line.** Our hall and wing roofs are 1-block stair steps that start ON the
   wall top: the wall becomes roof with no overhang, no soffit shadow, no drip. Ref18's every roof tier throws a
   shadow line under it (dark undersides, a pale lip on top) and its eaves stand proud of the arcade. A real slate roof
   has a 1.5-2 course eave overhang over a gutter on brackets; that shadow line is the single strongest "this is a
   roof" cue at 36 px.
2. **Stair sawtooth instead of a tile plane.** Every slope is 45-degree stairs, so from the RTS camera the roofs read
   as corrugated steps, and the shed (40 % of the view) as a flat grey slab with four stripes. Real slate / tile
   roofs are ONE continuous plane with fine horizontal courses, a different-textured ridge, and verges that stop
   cleanly. Ref18 gets its roof read from flat red planes with bright lip courses, not from steps.
3. **Flat single-plane walls between big dark piers.** The arcade is pier / hole / pier with nothing inside the hole
   but a red square and a white dot. Real engine-shed bays are blind arcades: a pier (projecting), an arch head
   (gauged brick, a different tone), a recessed panel under it, a sill, a window in a reveal. Three nested depths per
   bay; ours has one.
4. **No windows anywhere, and the light is random.** A 17 x 25 industrial building without a single glazed opening
   reads as a model. Victorian sheds are mostly glass: tall round-headed windows in every bay, roof lights, louvred
   vents. Our only light is candles on the cornice and lanterns in slots - decoration, not openings.
5. **Material is uniform and clean.** Every red block is red_terracotta or mangrove at full saturation; every dark block
   is blackstone. No tonal courses, no weathering under eaves, no soot over the stack, no lime bloom on the plinth.
   Realism at this scale is 2-3 tones of ONE material in structured bands, darker where water runs, paler where sun
   bleaches.
6. **No structural logic to the ornament.** Pale quartz caps sit on top of the wing roof as a chequer; the emblem
   floats on the gable; brass cubes sit on roof corners. In a real building each ornament sits where a load or a
   junction is: a finial on the ridge END, a bargeboard on the verge, a corbel under an overhang, a keystone over an
   arch, a string course where a floor is. Decoration that does not explain a junction reads as a sticker.
7. **The tower is stacked boxes, not a roof.** Each 4-row tier is a vertical wall with a flat top. Ref18's spire is a
   roof: sloped faces with their own tile courses, dormers (the "eye" is a dormer), a lip at each tier. Treating the
   cone faces as ROOF (steep tiles, dormer, hips) rather than as wall solves the ziggurat read r4-r6 fought.
8. **The shed has no reason to exist.** A works shed behind a hall should show its function: a stack with a flue, a
   row of roof vents, a glazed north-light or lantern, pipes to the hall, a loading door on the south end. Ours is a
   grey lid with two slots - the viewer cannot tell what it is, so it reads as filler.

---------------------------------------------------------------------------------------------------------------------
## (b) The parametric ROOF TILING SYSTEM

### B0 Shape vocabulary (Macaw's shapes, all "to verify"; vanilla fallback beside each)
| Shape | Macaw's id pattern | Slope | Vanilla fallback |
|---|---|---|---|
| lower | `mcwroofs:<mat>_lower_roof` | 1 in 2 (26.5 deg), half block high | `<mat>_slab` bottom |
| upper_lower | `mcwroofs:<mat>_upper_lower_roof` | the upper half of that run | `<mat>_slab` top |
| regular | `mcwroofs:<mat>_roof` | 1 in 1 (45 deg) | `<mat>_stairs` |
| steep | `mcwroofs:<mat>_steep_roof` | 2 in 1 (63 deg) | full block + a stair on top |
| upper_steep | `mcwroofs:<mat>_upper_steep_roof` | top half of steep | full block |
| attic | `mcwroofs:<mat>_attic_roof` | the knee where a steep meets a lower pitch | stair |
| top | `mcwroofs:<mat>_top_roof` | ridge cap (two-sided) | a bottom slab |
| gutter | `mcwroofs:gutter_middle` / `gutter_base` (coloured) | - | upside-down stair |
Known-real ids: `mcwroofs:deepslate_steep_roof`, `deepslate_top_roof`, `thatch_steep_roof`, `thatch_top_roof`. Materials
assumed (to verify): `red_terracotta`, `terracotta`, `bricks`, `nether_bricks`, `deepslate`, `blackstone`, `mangrove`,
`spruce`, `dark_oak`, `copper` / `oxidized_copper`. All roof blocks take `facing` (the uphill direction) and, where the
mod has it, `half`.

### B1 Course rule (the "tiling system")
A roof face is a stack of COURSES from eave to ridge. Each course is one row of roof cells at one y. The courses get
their realism from three things a generator can do with a seeded RNG:
- **Tone**: three materials of one colour role: T0 base (60-70 %), T1 darker (20-30 %), T2 paler (5-12 %). Red roofs:
  T0 red_terracotta roof shape, T1 nether_bricks roof shape (or mangrove), T2 terracotta / granite. Slate (shed):
  T0 deepslate, T1 blackstone, T2 polished-deepslate slab / tuff lip.
- **Weathering gradient by course index** (k = 0 at the eave, K at the ridge): P(T1 dark) = 0.35 - 0.25 * k/K (wettest
  at the eave: streaks, moss), P(T2 pale) = 0.03 + 0.09 * k/K (sun-bleached ridge). Clamp so each face holds 55-72 %
  T0. Within a course, runs of T1 are 1-3 cells (never a 4+ stripe, never an alternating chequer: draw a run length,
  then skip a run of 2-5 T0).
- **Valley/junction darkening**: every cell within 1 of a valley line or a wall junction (where a roof meets the tower
  flank or the wing wall) is forced T1 (water concentrates there - that is where real roofs stain).
- **Structure lines** override tone: ridge cap, hips, verges, eave course, dormer frames are fixed materials, not RNG.

### B2 Hall roof cross-section (z 3..7 and 17..21; the front half x 0..5; the back half mirrors to x 10)
Ridge at x 5 (frozen). Pitch: lower / upper_lower pairs = 26.5 deg, the Victorian slate pitch, reaching the ridge
at y 9 (top cap at y 9, cap's own height to 9.5; the envelope allows y 9-10).
```
y10 |  .    .    .    .    .    .
y 9 |  .    .    .    .    .   TOP        TOP   = mcwroofs:red_terracotta_top_roof (ridge cap; fallback: red_nether_brick_slab bottom)
y 8 |  .    .    .   LOW  ULOW  #         LOW   = <T>_lower_roof facing=east (uphill is +x toward the ridge)
y 7 | GUT  LOW  ULOW  #    #    #         ULOW  = <T>_upper_lower_roof facing=east
y 6 | COR  WAL  WAL   #    #    #         #     = solid red_terracotta (roof mass; invisible)
y 5 | PIER WAL  ...                       GUT   = gutter at the eave: mcwroofs:gutter_middle facing=west (fallback: polished_blackstone_brick_stairs facing=east half=top = a drip)
      x0   x1   x2   x3   x4   x5         COR   = the cornice at y 6 = quartz_stairs facing=east half=top (exists today); the gutter sits ON it, 1 block proud of the wall (x 0)
```
- Eave kick: at x 1 y 7 use `lower` (not `regular`): the slope flattens at the eave exactly like a real sprocketed
  eave, and the gutter at x 0 y 7 stands in front of it, so the eave line is 2 cells tall (cornice + gutter) and reads
  from the RTS camera as a shadow band.
- Downpipe: one `polished_blackstone_wall` column (or `decorative_blocks:chain`, to verify) from the gutter at x 0 y 6
  down the outside of every third pier (z 4, 20) to y 1, ending in a `cut_copper_slab` shoe (brass). Real water logic.
- Rib over every pier (keep from r4): the course cell over z 5 / 19 is T1 for its whole run (one dark line down the
  slope = a lead roll / hip-rib).
- Ridge cap: `top_roof` in T0 every cell; every 3rd cell `mcwroofs:terracotta_top_roof` (paler, bleached) - 33 %.
  Finial at both ridge ends (z 3 and z 7): `lightning_rod` on a `waxed_cut_copper` block set INTO the ridge
  (not on top: the rod tip stays at y 11 < the cone).
- Where the hall roof meets the cone flank (z 7 / 17) and the wing (z 2 / 22): a VALLEY course - the two cells on
  each side forced T1 and the junction cell a `polished_blackstone_brick_stairs` (the lead valley gutter).

### B3 Swept wing roof (z 0..2 and 22..24, the cells the swept_half shape produced, tops 14 / 12 / 10 at x 0)
The wing is the camera's nearest roof (south wing). Rules, applied cell by cell on the existing voxel shape:
- Every cell whose top is exposed and whose next cell inward (+z) is lower becomes a Macaw's roof shape facing the
  outer gable (the slope runs DOWN toward the hall): rise 2 per cell = `steep`, rise 1 = `regular`, rise 0 with a
  half-step = `lower`. The swept_half profile gives steep near the ridge (z 0), regular, then lower near the hall:
  that is exactly the concave Macaw's "steep -> regular -> lower" sequence; the voxel sawtooth disappears.
- Verge (the x 0 front edge, the camera's edge): a BARGEBOARD = `mangrove_slab` top-half at the top of every front
  column cell, under it the course cell in T1; a `polished_granite_stairs` facing=west half=top as the under-verge
  drip at x 0 y 7 (string course exists at y 11 today - keep, it is the "raking band" under a gable apex Historic
  England describes).
- Ridge (the outer gable line, z 0 / z 24, y 14 at x 0 falling to the back): `top_roof` T0 with 2 bleached cells; the
  brass finial at the FRONT end (x 0): `waxed_cut_copper` block + `lightning_rod` (tip y 16).
- The pale/red "beaded" rake of r5-r7 goes: a rake is a bargeboard (one tone, mangrove) plus a tiny pale under-cloak
  (`quartz_slab` bottom, 1 cell every 3) - not a chequer.
- Eave at the hall side (z 2 / 22, where the wing roof lands on the hall wall at y 8): a `gutter` cell along the whole
  wing width at x 0..8, y 8, facing the hall.

### B4 Shed gable (x 11..16, z 2..22, eave y 5, ridge y 6; the 40 % of the RTS view)
Slate. Keep the geometry; swap shapes and add the system.
```
y 7 |  .    .    .   VENT  VENT .    .          VENT  = louvred ridge vent every 4th z: a 1-cell `polished_blackstone_brick_wall` with a
y 6 |  .   LOW  ULOW TOP   TOP  ULOW LOW        `deepslate_tile_slab` top cap (y 7); under it at y 5 a `campfire` lit=true
y 5 | GUT  #    #    #     #    #    #   GUT    (smoke rises through the ridge = the works' steam; fallback soul-less: no campfire)
y 4 | WAL  ...                        WAL       LOW / ULOW = mcwroofs:deepslate_lower_roof / _upper_lower_roof facing toward the ridge
      x10  x11  x12  x13   x14  x15  x16 x17    TOP   = mcwroofs:deepslate_top_roof (fallback: polished_deepslate_slab bottom)
                                                GUT   = gutter at x 10 and x 17 at y 5 (x 10 is the yard: allowed overhang)
```
- Courses along z: T0 deepslate (62 %), T1 blackstone (28 %, runs of 1-3, densest in the first course), T2 polished
  deepslate (10 %, densest at the ridge). The r6 "rib every 3rd row" becomes a lead roll (T1 full run) every 6th z.
- ROOF LIGHTS (the glazed iron roof light a real shed has): two 3-long runs per slope at z 6..8 and z 16..18: the
  slope cell at x 12 / x 15 replaced by `glass_pane` (or `mcwwindows:metal_window_pane`, to verify) framed by
  `polished_blackstone_brick_wall` posts at the run ends, with a `lantern` (hanging) on the ceiling under each (interior
  glow at dusk - the only shed light, no slots).
- Stack (x 13..14, z 0..1): a `deepslate_bricks` flue with a `waxed_cut_copper` band at y 9 (exists), a `blackstone`
  SOOT ring at y 10..11 (darkest tone at the top where soot stains), a `polished_blackstone_brick_wall` 1-cell crown on
  each corner, and a `campfire` inside its top (y 11) so it smokes in game.
- South end (z 24, the camera's face): a loading door 3 wide x 3 tall at x 12..14 y 1..3 (recess 1, `dark_oak_trapdoor`
  leaves open against the reveals), a round-headed window above it (y 4, `glass_pane` in a 1-cell reveal), the
  `polished_deepslate` lintel kept, two downpipes at x 11 / 16.

### B5 Tower faces (cone tiers y 7..25; tier faces at x 0 / 1 / 2 / 3 / 4)
Each 4-row tier is treated as ONE steep roof face, not a wall:
```
per tier (rows r0..r3 from the tier's foot):  r0 = `red_terracotta` solid, the course band (a `polished_granite_slab` top = the lip
                                              of the tier BELOW, i.e. the eave of this face sits on the tier under it);
                                              r1, r2 = `mcwroofs:red_terracotta_steep_roof` facing outward (to verify) - the tile plane;
                                              r3 = `mcwroofs:red_terracotta_upper_steep_roof`; its top edge carries the next tier's lip.
```
- Arrises (the frozen black staircase line) stay: `polished_blackstone_brick_wall` at the corner cells = the hip
  rolls of a pyramid roof. The granite tick at each inner corner goes (r6 fault): hips need no tick.
- DORMER (the "eye", y 14..16, z 11..13): a real dormer = a 3-wide gable standing 1 proud of the face at x -0 (the face
  plane of that tier), `polished_granite` cheeks, a `glass_pane` eye, a `mcwroofs:red_terracotta_roof` mini-gable on
  top (2 cells facing each way + a top cap). It explains the eye as a window.
- Top tier (3 wide, y 22..24): all `upper_steep_roof` red to a `waxed_cut_copper` point cell at y 24 under the
  needle (`lightning_rod` y 25 - exists).
- Course tone on the tower: T1 only in the r0 row of each tier (the shadow under the lip), never on the plane.

### B6 Dormers, finials, chimneys, vents - the kit
| Part | Blocks | Where |
|---|---|---|
| Finial | `waxed_cut_copper` + `lightning_rod` | every ridge END (hall z 3/7/17/21, wings x 0, needle) |
| Ridge vent | wall + slab cap + campfire under | shed ridge every 4th cell |
| Dormer | 3 w x 3 h gable, granite cheeks, pane, mini roof | tower eye; one per hall roof slope at z 5 / 19 (y 8) |
| Chimney | deepslate_bricks, copper band, blackstone soot top, wall crown | the stack; a 1 x 1 flue on the south wing back (x 8, z 23) |
| Gutter | mcwroofs gutter (fallback upside-down dark stair) | every eave |
| Downpipe | blackstone wall column, copper shoe | every 3rd pier, the shed corners |
| Bargeboard | mangrove slab (top) + quartz slab under-cloak | every verge |
| Valley | dark stair in the junction, T1 either side | roof-to-tower, roof-to-wing |

---------------------------------------------------------------------------------------------------------------------
## (c) Facade articulation grammar

### C1 Three scales
- **Structure (reads at 36 px, the RTS view)**: plinth, piers, the cornice/gutter shadow band, the roof planes, the
  tower hips, the stack. These must survive the squint: dark/light bands and depth shadows.
- **Openings (reads at 3/4 view)**: the arcade's arch heads and windows, the door, dormers, roof lights, the loading
  door, the vents.
- **Ornament (reads close)**: keystones, rosette, rivet lines, pipes, gauge, finials, bleached course cells, soot.
Budget: structure gets ALL faces; openings one per bay; ornament <= 1 element per 2 x 2 of wall.

### C2 Bay rhythm (west face; pier at every even z 0 2 4 6 18 20 22 24 is frozen; the halls' bays are z 1 3 5 19 21)
A bay is 2 wide (pier 1 + opening 1) at T1 - keep it; the rhythm reads as "A B A B" from the camera. Make the hall
bays and the wing bays DIFFER (ref18: wings are plainer): hall bays get a window, wing bays a blind panel.

### C3 Depth planes - exact offsets (x, 0 = the outermost allowed plane)
| Plane | x | What lives there |
|---|---|---|
| P0 projection | x 0 | piers (full), cornice stair (y 6), gutter (y 7), plinth (y 0) |
| P0.5 half-projection | x 0, thin shapes | pier capitals (upside-down `polished_andesite_stairs`, y 5), sills (`polished_granite_slab` top-half at y 1 in each bay, 1 proud of the panel), the keystone (a `polished_blackstone_brick_wall` cell at the arch head, y 4) |
| P1 wall | x 1 | the bay's arch head (`polished_blackstone_brick_stairs` upside-down, exists), the frame courses |
| P1.5 reveal | x 1, thin shapes | window: `glass_pane` / `mcwwindows:*_window` (to verify) set at x 1 so the pier is a 1-deep reveal |
| P2 recess | x 2 | the panel back (`red_terracotta`), the window's dark interior (`cracked_polished_blackstone_bricks`) |
Every face must show P0, P1 and P2 within any 4-block run (rule F2 below). Thin shapes (walls, slabs, stairs, panes,
trapdoors) count as the half-planes - they are how 1 block of depth becomes 3 visible steps.

### C4 Plinth - string course - cornice
- **Plinth** y 0..1: y 0 `tuff` (exists); y 1 a `polished_andesite` base course on piers (exists) AND in bays
  (continuous: a plinth runs through, it does not stop at each pier).
- **Impost band** y 5: the andesite capital course (exists) - keep it continuous as Historic England's "plain impost
  band from which the arch heads spring".
- **Cornice** y 6: quartz stair (exists) + the gutter at y 7 in front of the lower_roof: the band is now 2 cells and
  carries the shadow.
- **String course on the wings** y 11 (exists) and a second at y 8 as the wing's eave line (`smooth_stone_slab`
  exists on the gable; run it along the x 0 face too).
- **Raking band** under the gable apex: 2 `polished_granite_slab` cells parallel to the rake, 1 cell under it (the
  double raking band of a brick gable).

### C5 Openings - how a brick industrial building is built
- Piers carry the roof; between piers the wall is thinner, so it is RECESSED (x 2). The relieving arch spans pier to
  pier: at T1 a 1-wide bay cannot show a semicircle, so the arch is the upside-down dark stair head at x 1 y 4 (exists)
  plus a 1-cell `polished_blackstone_brick_wall` KEYSTONE in front of it (P0.5) - that cell is the "gauged brick" tone.
- Under the head, a window: y 2..3 `glass_pane` at x 1, `cracked_polished_blackstone_bricks` behind at x 2 (dark
  interior), a `lantern` on the x 2 wall at y 3 inside every second hall bay (z 3, 5, 19, 21): windows glow at dusk,
  the "one candle per bay on the cornice" goes (candles on a cornice are not a thing).
- Sill: `polished_granite_slab` top-half at x 1 y 1 (the lighter red = a stone sill), the panel below it at x 2 y 1
  `red_terracotta`.
- Wing bays (z 1, 23) and the gable bays (south face, x 4 6 8): blind - panel `red_terracotta` with ONE `calcite` cell
  (the diamond, exists) - the ref18 panel.
- The door (frozen 5 x 8): add the industrial logic - a `polished_granite` relieving arch head exists; add a
  `waxed_cut_copper` lintel BEAM at y 9 across the whole lane (the iron beam a wide door needs), rivet it: a
  `polished_blackstone_brick_wall` every 2nd cell under the beam at y 8 inside the reveal (x 1, 3, 5, 7).

### C6 Material depth rules (per face)
- A face is 3+ materials of ONE role, in courses: bricks (blackstone bricks), polished (polished blackstone), rough
  (blackstone) - the piers polished-brick, the recess backs rough/cracked, the capitals polished flat.
- Weathering on walls: a `blackstone` (rough, darker) cell in P2 panels with P = 0.30 at y 1 falling to 0.05 at y 4
  (damp rises from the ground); on P0 piers never (dressed stone stays clean); `tuff` streak 1 cell under every
  downpipe shoe. Soot: `blackstone` cells P = 0.5 within 2 of the stack's top, 0 elsewhere.
- Chipped (to verify, `chipped:red_terracotta_*` / `chipped:polished_blackstone_bricks_*`): if the ids exist, use ONE
  Chipped variant as T1 for each role instead of a different vanilla block, so tone varies without a material change.

---------------------------------------------------------------------------------------------------------------------
## (d) Brassworks identity - ours, not ref18's
Ref18 is a red-and-white palace. Brassworks is an ENGINE SHED: red brick, slate, iron, brass, steam. Brass stays
<= 3 % of the front but is placed where machines are, so it tells a story:
1. **The main steam line**: a `waxed_cut_copper` pipe (full blocks, 1 x 1) from the stack's foot along the shed's west
   wall at y 6 (x 10, the yard side), turning down at z 12 into the hall's back wall; `waxed_cut_copper_stairs` as elbows;
   a `polished_blackstone_brick_wall` collar every 4 cells (flanges). From the RTS camera it is a warm line between
   the grey shed and the red hall - it also explains the shed.
2. **Gauge and valve**: at the door's north reveal (x 1, z 9, y 5): a `waxed_cut_copper` block with an `item_frame`-free
   stand-in: `polished_blackstone_brick_wall` + `cut_copper_slab` = a valve wheel; one `redstone_lamp` off (a dial) is
   allowed in the lane wall (x 9, y 5, z 12) if the pack textures it (else `copper_block` unwaxed is banned - skip).
3. **Rivet lines**: `waxed_cut_copper_slab` bottom-half cells at the FOOT of the tower's arris line every 2nd row (a
   riveted iron hip roll); `polished_blackstone_brick_wall` dots under the door beam (C5).
4. **Steam vents**: campfires under the shed's ridge vents and inside the stack (B4) - real moving smoke in game is the
   cheapest realism we have; at dusk the roof lights glow and the smoke catches it.
5. **Glazed iron roof lights** (B4) and iron-framed windows (C5): `glass_pane` in `polished_blackstone_brick_wall`
   frames = cast-iron glazing; Macaw's `metal_window` variants if present.
6. **Brass finials** on ridge ends and the needle (B6): the only ornament allowed to be bright.
7. **Dark fantasy touch (one, not many)**: the tower's dormer eye glows faintly warm at dusk (a `lantern` behind the
   pane), and the stack's smoke is thick (`campfire` x 2 stacked in the flue = a taller column). No skulls, no soul
   fire - the dark comes from soot, shadow and scale.

---------------------------------------------------------------------------------------------------------------------
## (e) Ranked r8 moves (max 10)
| # | Move | RTS camera effect | 3/4 view effect |
|---|---|---|---|
| 1 | Hall roofs to the B2 system: lower/upper_lower planes, gutter + cornice eave band, ridge cap, valleys, finials | the two red roofs become continuous planes with a dark eave shadow under them - the "roof" read | tile courses, a real eave, drips and downpipes |
| 2 | Shed gable to B4: slate courses with tone, lead rolls, 4 roof-light runs, ridge vents with smoke, soot on the stack | 40 % of the view stops being a grey lid: course texture, glass glints, smoke | the shed explains itself (a works) |
| 3 | Tower tiers as steep-roof faces with hip rolls; the eye as a dormer; no ticks | one red pyramid roof with dark hips, not four boxes | the dormer reads as a window; the lip under each tier reads as an eave |
| 4 | Wing roofs to B3: steep->regular->lower Macaw's sequence, bargeboard verge, finial at the front end, no pale chequer | the nearest roof (south wing) becomes a smooth swept plane with one clean edge | the verge and bleached ridge cells read as weathered slate |
| 5 | Arcade bays get windows + keystones + sills (C5), lanterns inside every 2nd bay, candles off the cornice | at dusk a row of lit windows; by day a darker, deeper arcade band | 3 depths per bay; the building has glazing |
| 6 | Brass steam line + flanges (D1) and stack soot ring | a warm line along the yard, smoke over the stack | the machine story |
| 7 | Weathering RNG (B1 / C6) on roofs and panels: eave-dark, ridge-pale, valley-dark, damp at the plinth | roofs stop being flat colour fields; still 60 %+ T0 so the colour targets hold | believable staining in the right places |
| 8 | Downpipes every 3rd pier + copper shoes; gutters on every eave incl. the wing-to-hall line | thin vertical dark lines break the pier/bay stripe rhythm | explains the gutters |
| 9 | South face (z 24): loading door + round window on the shed end; the wing gable's raking band + bargeboard | the camera's second face gets openings | the emblem sits in a framed gable, not on it |
| 10 | Door beam + rivets, valve at the reveal | - | the door is an engineered gate opening |
Order matters: 1-4 are roof moves (the camera sees roofs), 5 is the one wall move with camera weight (dusk lights).

---------------------------------------------------------------------------------------------------------------------
## (f) Testable generator rules
F1. **Eave overhang**: every roof face's lowest course cell has a roof/gutter cell 1 block OUTSIDE the wall plane under
    it (hall: a cell at x 0 y 7 for every z 3..7 / 17..21; shed: x 10 / x 17 y 5; wings: y 8 at x 0..8). Fail if any
    eave cell is missing.
F2. **Depth within 4**: on every exterior face, every 4-cell horizontal run at y 1..5 contains cells in at least 2 of
    the planes P0 / P1 / P2 (counting thin shapes as the half-plane of the block they sit in). Fail on a 4-run of one
    plane.
F3. **Roof plane continuity**: a roof face (hall, wing, shed, tower tier) has no vanilla `*_stairs` cell whose uphill
    neighbour is a full block at the same y (the sawtooth test) when Macaw's shapes are available; with the fallback,
    stairs must alternate with slabs (no two full-height steps in a row).
F4. **Tone shares per roof face**: T0 55-72 %, T1 20-35 %, T2 5-12 %; T1 density in the eave course >= 1.5 x T1
    density in the ridge course; T2 density at the ridge >= 2 x at the eave. No T1 run > 3 cells in a course; no
    exact alternation (T0 T1 T0 T1 over 4+ cells).
F5. **Structure-ornament binding**: every brass cell (waxed copper family) is within 1 of a ridge end, a ridge cap, the
    door head/beam, the stack, or the steam-line path; any other brass cell fails. Brass <= 3 % of the front.
F6. **Openings**: every hall bay (z 1 3 5 19 21) has >= 1 glass cell at x 1; the shed has >= 4 glass cells per slope;
    the tower has exactly one dormer (3 x 3 at y 14..16). No `white_candle` above y 5.
F7. **Water logic**: every gutter run ends at a downpipe column (a contiguous thin-shape column from the gutter's y to
    y 1); every valley line (roof-to-tower, roof-to-wing) has T1 or a dark stair on both sides for its whole length.
F8. **Frozen envelope**: front outline == FRONT_T1; lane and spawn column air; max y 26; the only cells outside the r7
    massing are at x 0 / x 10 / x 17 eaves and the 1-cell wing eave line - fail on any other new cell.

## (g) ref45 - lovish's reference, Oct 28 22:49 (design/refs/vehicle_labs/ref45_brick_house_slate_roof_round_chimneys.png)
What makes it read as a real building where r7 reads as a toy:
1. **Wall / roof material split.** Warm orange-red brick walls under COOL GREY slate roofs. r7 is red wall under red
   roof - one lump. Rule: roof value and hue differ from the wall (grey/blue-grey slate, ~2 tones), the wall stays brick.
2. **Roof with thickness and overhang.** Continuous ~30-35 degree planes with visible horizontal course lines, a deep
   eave that casts a shadow band on the wall, a dark bargeboard/verge edge. No stair sawtooth.
3. **Layered roofs.** A lower pent (porch) roof wrapping the front under the main roof: two eave lines, two shadow bands.
4. **Round fluted stone chimney stacks** with shingled domed caps rising through the roof - the silhouette's hero.
   For us: brass-banded stone stacks / the steam stack, same idea.
5. **Stone base, brick body.** Pale stone plinth and piers carry the brick; the arcade under the porch is deep and
   lit (lanterns in shadow) - depth, not paint.
6. **Function you can read.** Stairs, porch, a dormer, chimneys: every element has a job. Ornament follows structure.
7. Background town: brick towers with dark-timber lattice tops, all brick + grey slate - a cohesive Brassworks palette.

## (h) ref46 - lovish's reference, Oct 28 22:50 (design/refs/vehicle_labs/ref46_steampunk_machine_works.png)
The Brassworks IDENTITY layer (ref45 is the building, ref46 is what makes it Brassworks):
1. **Machinery is the ornament.** Big copper/orange gears and flywheels, bent pipe runs and elbows, pistons, valve
   wheels, gauges - mounted ON the brick/stone body, not painted on it.
2. **Scale ladder.** Huge (gears 5-9 wide, stacks), medium (pipe runs, boilers, cranes), small (rivets, valves, lamps,
   crates) - all three scales on every face the camera sees.
3. **Open structure in front of solid mass.** Iron scaffolds, gantries, catwalks, ladders, railings and cranes stand
   proud of the walls: see-through layers that make deep shadows and real depth from above.
4. **Palette.** Pale grey stone + orange/terracotta + copper/brass accents + dark iron; tall pale chimneys with
   capped tops. Rust/patina gradients, not flat colour.
5. **Stuff in the yard.** Crates, barrels, coal, carts at the base - the building is working.
Use: T1 gets a restrained dose (one gear/flywheel hero, pipe runs between hall and shed, a gantry on the shed, yard
props); T2-T4 escalate toward ref46's density. Keep RTS readability: the machinery sits on the roofline and the
camera-facing (west/south) faces.

## (i) ref47 - lovish's reference, Oct 28 22:50 (design/refs/vehicle_labs/ref47_industry_parts_arm_cranes.png)
A PARTS KIT for the identity layer (build each as a tested part in tools/brass_lab_parts.py, like bridge_crane):
1. **Robot arm** - stepped copper pedestal on a dark brick plinth, a 2-joint arm with a boxy dark head, a 4-finger claw
   (lightning-rod fingers), a hanging chain. The hero prop by the exit lane / on the yard.
2. **Gantry with drill** - two copper-and-brick portal legs, a beam, chains holding a drill head over a stone pad.
   Fits over the shed's work bay or the vehicle spawn.
3. **Jib crane** - copper column on a stone base, a short boom with a chain and hook/weight. Roofline or yard.
Grammar: copper (orange, unweathered and part-weathered) + dark mud/blackstone brick + pale stone base; every joint
gets a darker block; small lit "gauge" details. Engine limit: the copper grate / chiseled copper / copper bulb look in
the picture is 1.21 - on 1.20.1 use cut copper (+ stairs/slabs), iron trapdoors and iron bars for slats, lightning rods
for claws and pipes, chains from vanilla or Decorative Blocks.

## (j) ref48 - lovish's reference, Oct 28 22:51 (design/refs/vehicle_labs/ref48_isometric_old_industrial.png)
The closest reference to our RTS camera (an isometric 3/4 from above). Small (236 px) - read for structure:
1. **Big arched steel-framed windows** filling each bay between pilasters, a mullion grid (iron bars / Macaw's metal
   windows), a stone arch head - the facade is mostly glazing and pier, not blank wall.
2. **Pilaster every bay + a pediment/stepped gable** on the end walls; a plain cornice under each eave.
3. **Several pitched roofs at different heights** meeting at right angles (main hall, side hall, lean-to), each with
   its own ridge cap, a ridge clerestory/skylight strip, and weathering streaks running DOWN the slope.
4. **Worn, not clean.** Patched roof cells, stained plaster/brick near the base and under eaves, debris and crates
   at the walls' feet. Weathering density highest at eaves, valleys and the ground line.
5. **Big service doors** (timber, planked) on the sheds - the vehicle doors ARE the facade's focus.

## (k) ref49 - lovish's reference, Oct 28 22:51 (design/refs/vehicle_labs/ref49_long_brick_factory_chimneys.png)
Realism through RESTRAINT (small, 299 px):
1. **Few materials, one rhythm.** Red brick + dark window frames + a dark roof band; the same bay repeated the whole
   length reads as a real factory, not a collage. Ornament budget is low - the rhythm is the detail.
2. **Long window bands,** two storeys, dark mullions, warm lit interiors at dusk - glazing carries the facade.
3. **Tall square brick chimneys** grouped at one end as the vertical counterpoint to a long low body.
4. Real proportions: storey height ~4-5 blocks, windows ~2/3 of the bay, parapet/roof band thin.
Lesson for us: refs 45-49 all keep walls to ONE brick + ONE stone + ONE dark trim, roofs to grey slate, and spend
detail on structure (bays, windows, eaves, chimneys) and machinery - never on multi-colour panels or chequers.

## (l) ref50 - lovish's reference, Oct 28 22:51 (design/refs/vehicle_labs/ref50_glass_dome_future_base.png)
Not Brassworks: a futuristic white base with glass geodesic domes, a ring walkway with a timber deck, orange accent
strips and twin white stacks. Likely for the new AI/robot faction (replaces Tidewrought) - to confirm with lovish.
What it teaches every faction: one strong primary form (the dome) + a ring that ties the parts together, clean
white/glass/one accent palette, layered terraces stepping down to the ground.
