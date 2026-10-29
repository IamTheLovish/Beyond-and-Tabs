# Engine Shed T1 - the fixed per-region checklist (art director, Oct 28, session E)

Written ONCE, from ref18 (`design/refs/vehicle_labs/ref18_brick_hall_central_tower.png`) and the approved T1 greybox
(`design/vehicle_labs/brass_t1_greybox.nbt`, 17 x 25, h 26), before any detail work. **Every builder builds to it and
every critic scores against it - not its own taste** (pilot lesson 2: three critics asked for three colour schemes).

lovish on the greybox (Oct 28 16:57): "it looks a lot better, just needs more block variety to help with texturing like in
the photo. More dimensions and architecture details." And: "we can always add a gate that animates as if it's opening"
(a follow-up feature, not this round: the door is built as a framed gate opening the gate will later fit).

T1 is detailed and cohesive **at its own size**: ref18's material language and region layout, not its palace-scale
fineness. Colour is judged on rendered pixels; values are decided in game (noon, day time 6000).

## Grid
x = depth from the front (x 0 = the west face the reference shows), z = across the front (0..24, axis z 12), y = up.
Front columns: north wing z 0..2 | north hall z 3..7 | tower z 8..16 | south hall z 17..21 | south wing z 22..24.
The RTS camera looks north-east from the south-west: it sees the roofs, the west face AND the south face (z 24 side,
the south wing's gable end, the shed's south end). Those faces are front faces too.

## Palette roles (only these blocks plus their stairs / slabs / walls; all textured in our pack, measured on pixels)
| Role | Blocks (variety inside a role is structured: courses, frames, panels - never random speckle) |
|---|---|
| RED (body, roofs, panels) | red_terracotta (smooth panels), mangrove_planks / stripped_mangrove_log (roof courses, beams), polished_granite (lighter red: sills, highlights); red_nether_bricks only for deep-shadow red inside recesses |
| DARK (frames, arrises, recesses) | polished_blackstone_bricks (frames), polished_blackstone (flats), blackstone (rough), chiseled_polished_blackstone (motif), cracked_polished_blackstone_bricks (darkest backs of recesses) |
| GREY (trim only) | polished_andesite (trim, caps), tuff (plinth), polished_deepslate / deepslate_tiles (ribs, transitions), smooth_stone slabs (ledges) |
| PALE (lines and single cells) | calcite, quartz_bricks / quartz_block, bone_block (warmer columns), polished_diorite |
| BRASS accent (Brassworks identity, <= 3 % of the front) | waxed_cut_copper and its shapes (bands, finials, the door's rosette) |
| LIGHT (glow budget) | white_candle or lantern: at most one per arcade bay; ONE warm glow at the door |
Banned: cobblestone family (rubble at 32 px), unwaxed copper, any block our pack lacks (it renders flat colour).

## Target shares (measured on ref18's pixels with the same classifier; the critic re-measures ours on the render)
Whole front: red 39 / dark 41 / grey 19 / pale 1. Per region below.
(Corrected Oct 28 after round 1: the first targets, 33 / 35 / 16 / 16, counted ref18's clouds as pale - the
classifier now leaves blue sky out. Region targets re-measured the same way; pale lives in lines and single cells.)

## Regions
**R1 Plinth (y 0..1, every front face).** A 1-high grey course (tuff / polished deepslate) under the arcade piers and
along the south face; the lawn stays in front. Reads as the base line in the reference.

**R2 Arcade storey (halls and wings, x 0..2, y 1..5; bays at z 0 2 4 6 18 20 22 24).** Ref: a dark storey of repeated
bays. Piers project to x 0 (polished blackstone bricks, a polished andesite base and cap); bays sit 1-2 deep, their
backs cracked blackstone bricks, each with a small red panel (red terracotta, y 1..3) carrying a pale cross or diamond
(calcite). Target: dark 63 / grey 28 / red 8 / pale 1.

**R3 Lintel band (y 6, z 0..24 except the tower).** A pale cornice (quartz bricks, upside-down quartz stairs
projecting at x 0) with one tiny white light over each bay. The brightest line on the front.

**R4 Hall roofs (z 3..7 and 17..21, y 7..9).** Red courses stepped in 2-3 tiers with a slab drip at each tier (mangrove
stairs / slabs over red terracotta), a grey or dark rib over every pier (polished deepslate / blackstone brick wall),
a dark ridge course. Target: red 67 / dark 24 / grey 8 / pale 1.

**R5 Wings (z 0..4 and 20..24, x 1..8, roofs to y 14).** The swept roof red (mangrove + red terracotta courses), a pale
rake beaded pale / red along the outer gable edge (solid pale read as a white column in r5), a brass finial at the ridge
end. The gable ends (z 0 face, and the z 24 face the RTS camera sees) carry the pavilion emblem: a cream panel (quartz
bricks) with a red cross / star, framed in blackstone. Target: red 79 / dark 11 / grey 8 / pale 3.

**R6 Door and tower base (x 0..9, z 7..17, y 0..10).** The exit lane is 5 wide x 8 tall (z 10..14, y 1..8, x 0..8),
the lane every other lab uses: **T1 Brassworks vehicles fit 5 x 8** (r4, Oct 28 - the 7 x 10 came only from the plot
rescale and made the door the cone's whole foot; ref18's door is ~55 % of the foot's width, ~31 % of the tower's
height). The door's light is a SPOT at the head's centre, not a frame (r5: r4's calcite jambs read as a white U):
polished granite jambs (z 9 / 15) to the head; the head at y 9 polished granite round ONE quartz keystone carrying ONE
standing lantern (r6: r5's pale | brass | pale over a dark band read as an owl face) (hanging lanterns
fail check_support); beside the jambs the cone's face and dark arrises come down to the eave. The far wall (x 9) a
calcite field between diorite gate channels, calcite to the head (r6: a light field round the spot), the brass rosette where the animated gate will
hang; the reveals quartz-brick pilasters between red fields; the lane floor smooth stone on a tuff threshold.
Buttresses (z 7 / 17) stop under the eave like the arcade piers, capped by one small dark stair AT the eave.

**R7-R8 tower target (measured above the hall): red 56 / dark 25 / grey 17 / pale 3.**

**R7 Tower mid (y 7..18).** ONE straight cone from the hall eave: front outline 11 15 19 23 25 (4 rows a column,
FRONT_T1), standing ON the hall roof - the hall roofs run into its flanks. Red faces, black arrises (polished blackstone
brick stairs / walls) as one unbroken stepped line from y 7 to the point, a granite tick at each step's inner corner;
no full-width pale or dark course, no row of the cone's face over 30 % pale; the diamond "eye" (polished granite ring - r5: calcite stood +113 over the body and split the cone -
red heart) at y 14..16, z 11..13 (kept from r5). Re-mass r3, lovish chose option b, Oct 28.

**R8 Tower upper and point (y 18..25).** At 3-5 wide "3 panels across" cannot exist: one red field (red terracotta,
mangrove shapes) with granite ticks at the step corners; dark only as the arris below y 20 - the 3-wide top tier is all
red (r5: its dark arrises made it a dark cap, a third tier) - and the point (y 24) dark under the brass tip (y 25).

**R9 Backs and RTS-visible sides (the shed x 11..16 roof at y 5, its south end, the stack x 13..14 z 0..1, the halls'
south end z 24).** Quieter, but never blank: every visible face has 2 materials and 2 depth planes - deepslate tiles
roof with blackstone ribs, the stack in deepslate bricks with a brass band and a dark cap. r6: the shed (~40 % of the RTS view) a shallow gable over z 2..22 -
deepslate tile courses, a deepslate-brick rib every 3rd row, eave stairs at y 5, a polished deepslate ridge at y 6 -
and two lit 1 x 3 slots in its south wall; no parapet.

## Part references (lovish, Oct 28 16:58-17:00; ref18 stays the one primary)
Each teaches a part, at T1 size; none replaces ref18's layout or palette.
- **ref39 temple** -> R2 piers as columns: a base, a shaft standing 1 proud of the bay wall, a capital (stairs); R3 as a
  frieze band; R5's gable emblem framed like a pediment; R1 as a stepped base with stairs up to the door.
- **ref40 gothic front** -> R6 / R7: the tower-base buttresses capped with small pinnacles (a wall + a slab or candle
  point), and the face in 3-4 depth planes, not 2.
- **ref41 nave** -> frame vs body by value AND texture: dark brick ribs and pointed arch heads (stepped stairs) over
  lighter fields. Use it for R2's bay heads and R6's vault; the "chandelier on a chain" for the one warm door light.
- **ref42 pagoda** -> R4 / R5 eaves: every roof tier overhangs on a bracket course (upside-down stairs under the
  drip), warm light under the eaves, a tall brass finial (R5 ridge ends, R8 point).
- **ref44 layered gothic front** (signed: study, never trace) -> the relief DENSITY to aim for at T1 size: a string
  course every 3-4 rows on the tower and wing faces, piers in 2-3 stacked planes, niches with arched heads; variety
  inside one value range comes from texture (bricks vs tiles vs polished), which is how a role keeps 3+ blocks
  without speckle.
- **ref43 castle** -> R7 / R8 and R9: light from openings - a lit tall slot or two in the tower and the shed - not
  scattered lamps; paler trim on dark stone.

## Rules for every region (the "more dimensions" ask)
- Every visible face has **at least 2 depth planes** (projecting piers, recessed panels, overhanging cornices).
- **At least 3 blocks** from its roles on every region's face, and **no single block over 40 %** of a face.
- Shapes do the relief: stairs for cornices and drips, slabs for ledges, walls for arrises and mullions.
- Detail is shallow and subtractive inside the greybox; **the front outline stays exactly the greybox's**
  (`FRONT_T1` in tools/test_lab_vehicle_brass.py), the exit lane and spawn column stay clear, nothing floats.

## The critic's score sheet (fresh Fable critic each round; scores in design/vehicle_labs/SCORES.md)
For each region R1-R9: 0-2 for **layout** (matches the reference's region), 0-2 for **relief** (depth planes, shapes),
0-2 for **variety** (3+ blocks, structured, no speckle). Then the whole: **resemblance** to ref18 (0-10) and **quality**
(0-10), judged on the ref_compare `--front` sheet AND the RTS-camera render at game zoom (px 32); squint first, then
the shrink test. Every claim about colour cites a measured share or a hue / saturation / luma median on a named crop.
