# Our own moulding blocks: measured profiles (slice 1, Oct 29 2026)

lovish (Oct 29, 16:55-17:05 ET): the Engine Shed "looks blocky and toy"; approved "our own architectural blocks
(custom models)". Slice 1 = cornice / string course, window sill + lintel (optional keystone), arch ring. Generator:
`tools/gen_arch_blocks.py` (models, blockstates, loot, names, Java shapes, demo wall); tests:
`tools/test_arch_blocks.py`, `ArchBlocksGameTests.java`; camera gate `tools/anti_toy.py`.

## What the reference builds do (measured, study only)

Measured with `tools/schem_study.py` + `building_gates.camera()` on lovish's reference builds (planetminecraft
downloads, study only, never committed or copied). "Proud" = in front of the wall plane; 1 block = 16 px.

| Feature | Ref | Measured |
|---|---|---|
| Eave cornice | gare-de-minecraft (cheesystring) | one course of upside-down stairs **2 blocks proud** over the whole facade (y 19 and y 40), with a railing behind; corner piers step 1 more |
| Eave cornice | victorian-factory (koin) | slab-top lip **1 proud**, then 2-3 stair courses stepping back as the roof (a stepped eave, 3 courses) |
| Eave / corbel table | chapel-of-brittany (ragnar_le_rouge) | stair / slab alternation 1 proud at the eave (y 21): a corbel table, 1 course |
| Corbelled stage | gothic-castle (nighthearted) | upside-down cobblestone stairs **1-2 proud**, every stage, 1 course |
| String course | gare-de-minecraft | polished andesite course **1 proud, 1 course tall**, 3 of them (y 16, 28, 31): every 3-12 blocks |
| String course | victorian-factory | one slab-top course at the storey line across the whole front |
| Sill | train-station-1890 (seuchendoktor) | stone-brick wall posts / bottom stairs **flush** with the pier face (the wall itself 1 back) |
| Sill | gare-de-minecraft | upside-down stone-brick stair **2 proud** under 5-wide windows |
| Sill | victorian-factory | bottom stair (deepslate tile / polished tuff) **1 proud**, 5 wide |
| Lintel / head | victorian-factory | upside-down stair row over 5-wide windows (the head and the sill are the same stair, flipped) |
| Lintel / head | train-station-1890 | a bottom-slab course over the upper windows, 1 back of the piers |
| Keystone | none of the five | no block-scale keystones: at 16 px they would be a whole block - our 4-8 px keystone is new |
| Arch | train-station-1890 | windows **span 3, rise 1** (row widths 1/3/3): stairs round the head; main entrance **span 9, rise 2** (3/7/9) |
| Arch | gare-de-minecraft | upper windows **span 5, rise 3** (1/3/3/5), andesite ring 1 proud; rose arch **span 15, rise 5** (5/9/11/13/13/15) |
| Arch | gothic-castle | pointed **span 5, rise 2** (1/3/5/3/1); span 3, rise 1 |
| Portal | chapel-of-brittany | receding orders, **6 orders 1 block deep each** |

Rise / span in the refs: 0.2-0.6 (segmental station heads 0.2, round / pointed 0.4-0.6). Every ref builds these at
whole- or half-block steps (stairs, slabs, walls): the 8 px step is the finest relief any of them has, which is
exactly what lesson 6 calls "blocky / toy" at RTS distance.

## Our profiles (px; models face north, back on the wall face)

| Block | Profile | Proud | Height | Why |
|---|---|---|---|---|
| `cornice_*` (stone, brick, slate, copper) | fillet 2, ovolo 3-5, dentils (2 px teeth, 2 px gaps) 8, corona (the drip) 13, cyma 12, top 10 / 7 | 13 | 16 (one course) | the gare's 2-block-proud stair cornice and the factory's 3-course stepped eave folded into ONE course at 1-3 px steps; the corona's shadow line is the point (G1) |
| `string_course_*` (stone, brick, slate) | fillet 2, bead 3, band 5, drip 4, fillet 2 | 5 | 7 (y 4-11) | the gare's 1-proud andesite course at a third of the projection: a storey line, not a ledge |
| `sill_*` (stone, brick, slate) | drip lip 1 px with a groove behind, body, weathered top stepping back | 5 | 6 (top of the cell) | the factory's 1-proud stair sill at under a third of the projection (r2 critic: 7 px read as shelves), with a real drip |
| `lintel_*` (stone, brick, slate) | chamfered soffit, face 3 proud; keystone 4-8 px wide tapering up, 6 proud with a fillet | 3 / 6 | 16 | the station's flat heads; the keystone is new (none in the refs at block scale) |
| `arch_ring_*` (stone, brick) | segmental: span 48 (3 blocks), rise 10 (0.21, the station's ratio), ring 6, 3 proud; keystone 6 wide, 5 proud | 3 / 5 | 16 | the 1890 station's span-3 heads without the stair steps; haunches and crown sit IN the wall row (the soffit shows), springers hang on the piers |

Corners: cornice and string course have `shape` straight / outer / inner (mitred, one-handed models: the mirrored
corner is the other facing). Materials are vanilla textures only (smooth_stone, bricks, polished_deepslate,
weathered_cut_copper), so the realistic resource pack retextures them.

## Slice 2 (Oct 30 2026): pilaster, quoin, corbel bracket, corbel course

Designed by a fresh API designer pass (itachi2-api runs arch2-design-b and -c, Command Center); lovish chose
**every material that reads** (10 blocks, 104 states, 26 models) and **quoins that replace the corner block**.
Far reads come from value contrast between materials, lit tops of steps and silhouette - never from cast shadow (the
plain lane has none): vanilla shades up 1.0, north/south 0.8, east/west 0.6, down 0.5.

| Block | Profile (px, facing north, back on the wall at z 16) | States | Far / RTS read | What makes it carving |
|---|---|---|---|---|
| `pilaster_{stone,brick,slate}` | shaft x 2-14, 4 proud; base: plinth x 0-16 6 proud y 0-4, torus x 1-15 5 proud y 4-6; capital: astragal 5 y 7-9, neck 4 y 9-11, echinus 5 y 11-13, abacus x 0-16 6 proud y 13-16; single = base + capital in one | `part` base / shaft / capital / single (16) | a pale stripe ~24 screen px wide splitting the brick into bays, wider at foot and head | the 2 px flare each side at both ends, the recessed neck |
| `quoin_stone` | a FULL smooth stone cube (occludes; replaces the wall's corner block) + two stones 2 proud, y 1-15 (2 px joints): the long one 26 px (wraps the arris, 8 px over the next block), the short 16 | `long_side` front / side (8); placed by hand it alternates by course | a pale zipper up each corner against brick | the long / short alternation - outline, not relief; each stone shows smooth stone's frame once |
| `corbel_{stone,brick,copper}` | three steps 8 / 10 / 12 proud that widen as they rise (6 / 8 / 10 px wide, y 6-10 / 10-13 / 13-16; the foot 6 px clear of the row below); then up into the cornice's cell to the corona's soffit (y 26), 1 px behind its face | facing (4) | evenly spaced pale teeth hanging from the cornice band: the cornice gets legs (slice 1's "lid") | no free top: it carries the cornice; every other block (a 32 px pitch), capitals take their slots |
| `corbel_course_{brick,stone,slate}` | steps of the shaft's brick (bricks / stone_bricks / deepslate_bricks) 2 / 4 / 6 proud, 4 px each; smooth stone coping 8 proud y 12-18 (its top 2 px above the shaft's: a pale rim round a recessed flue) | `shape` straight / outer / inner (12), like the cornice | a 48 px crown on a 32 px stack (1.5x flare) with a pale rim | thin 2 px steps under a thicker pale coping |

Measured in the reference builds (Oct 30, study only, nothing copied; tools/schem_study.py, a corner / stack scan):

| Feature | Ref | Measured |
|---|---|---|
| Tall stack | victorian-factory | 3 x 4 shaft, 28 tall, on a 5 x 5 stair plinth (1 block out each side); its top is a row of brick WALL blocks (8 px in, not out): no flared crown |
| Short stack | victorian-factory | 2 x 1 shaft, 14 tall, widening to 4 x 3 in its top 2 layers: a corbelled crown 1 block out each side (2x flare at block scale; ours is 1.5x at 8 px) |
| Corner | gare-de-minecraft | a full-height contrasting column (polished andesite on brick, andesite walls on polished andesite) - a pier, not alternating stones |
| Corner | victorian-factory | a wall post on the arris (polished cut granite wall on brick, 18 columns; stone brick wall on stone bricks, 16): a bevelled corner |
| Corner | chapel-of-brittany | stone brick corners on quartz walls (22 columns), cobblestone wall posts on stone brick (32) |
| Long / short quoins | all of them | almost none at block scale (13 of 243 corner columns in the factory, 6 of 37 in the gare, most of them noise): at 16 px a quoin is a whole block, so the zipper is new, like slice 1's keystone |

A ring round a 2x2 shaft is 8 straights + 4 outer corners (`tools/gen_arch_blocks.crown_cells`); a 3x3 shaft takes
12 straights. The Engine Shed set: `pilaster_stone`, `quoin_stone`, `corbel_stone`, `corbel_course_brick`.

Joins with slice 1: the capital's abacus (6 proud) stands 4 px past the cornice's lowest fillet (2) - a lit ledge
under it; the string course dies against the pilaster's base and the quoin's long stone. No two visible faces of the
demo walls are coplanar (`gen_arch_blocks.zfights`; it found the slice-1 arch crown's keystone sharing a top face with
its neighbouring voussoirs - fixed). Faces of parts that leave their cell (the bracket's rise, the crown's rim, the arch
ring's proud ring) no longer cull against the cell's neighbour.

Stills: `demo_wall_2.nbt` (a 21 x 8 facade for a FRONT-on still, the eyes `_front` view) and `sampler_2.nbt` (the
other materials).

## Next slice (not built)

Candidates: a chimney taper block (3x3 foot to 2x2 crown), inner-corner quoins, rusticated ashlar, a balcony.
