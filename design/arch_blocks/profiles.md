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
| `sill_*` (stone, brick, slate) | drip lip 1 px with a groove behind, body, weathered top stepping back | 7 | 7 (top of the cell) | the factory's 1-proud stair sill at half the projection, with a real drip |
| `lintel_*` (stone, brick, slate) | chamfered soffit, face 4 proud; keystone 4-8 px wide tapering up, 6 proud with a fillet | 4 / 6 | 16 | the station's flat heads; the keystone is new (none in the refs at block scale) |
| `arch_ring_*` (stone, brick) | segmental: span 48 (3 blocks), rise 10 (0.21, the station's ratio), ring 6, 3 proud; keystone 6 wide, 5 proud | 3 / 5 | 16 | the 1890 station's span-3 heads without the stair steps; haunches and crown sit IN the wall row (the soffit shows), springers hang on the piers |

Corners: cornice and string course have `shape` straight / outer / inner (mitred, one-handed models: the mirrored
corner is the other facing). Materials are vanilla textures only (smooth_stone, bricks, polished_deepslate,
weathered_cut_copper), so the realistic resource pack retextures them.

## Next slice (not built)

Three more profiles - candidates: pilaster with base and capital, quoin / rusticated corner, corbel / bracket.
