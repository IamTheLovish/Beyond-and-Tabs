# Stormcallers lab (Thunder Arsenal) - expected numbers, written BEFORE each round's stills land

Art method: "write the expected number before the stills land - the reading can't drift to fit the hope". Values are
on camera (the eyes stills, plain lane, named noon), from tools/camera_colour.py's table where the block is measured,
else from our pack's texture (marked "tex").

## Detail round 1 (massing A + the massing critics' shared faults) - expected
| Item | Expected | Source |
|---|---|---|
| roof field (prismarine brick stairs, up) | value 0.54, sat 0.32, hue 160 | table (massing run 41166006117) |
| eave underside (dark prismarine slab, west / south) | 0.20 / 0.26 | table |
| lacquer posts (polished granite, west / south) | 0.33 / 0.35, sat 0.32-0.33, hue 12 | table (sunforged runs) |
| red share of the building's rts pixels | 4.2 % (band 4-10) - at the lower edge | camera model |
| gate floor (obsidian, up) | 0.05-0.10 | tex 0.04 |
| gate interior mean (34) | 0.11-0.14 (band 0.05-0.18) | obsidian ~0.07 + blackstone-brick walls 0.17 |
| white paper panels (white stained glass, south) | 0.49, sat 0.09 - not snow | table |
| spruce stair lintels | 0.25-0.35, sat 0.30 | tex (spruce planks 0.37) |
| keep lead (mass) | point 19 over the gatehouse ridge 12 = 7 | plan |
| dusk | one cyan-blue glow (the soul-lantern core), paper panels warm | plan |

## Detail round 1 - measured (eyes 41168410538)
roof field (prismarine brick stairs, up) 0.541 / sat 0.32 / hue 160 (as expected); obsidian up 0.086, west 0.043 (as
expected); white panels west 0.494 / sat 0.13; spruce stairs west 0.29 / sat 0.23; 17 / 17 tests green.

## Detail round 2 (one change per element) - expected
| Element | Change | Expected |
|---|---|---|
| roofs | no cube tips; pent eaves' lip ring dark prismarine | the eave line 0.33 (up) between 0.54 fields; field still the commonest roof stem (rts 25.7k vs 2.5k) |
| keep tiers | second eave 7 x 7 (was 5 x 5): eaves 7 / 7 / 5 | the top tier visibly narrower than the two below |
| keep walls | upper bodies quiet: spruce-log corners, one paper window per face | no stripes on the keep in 34; spruce log west 0.14 / south 0.18 |
| crown | one meridian + a flat ring radius 3 (7 across), sea-lantern core | a ring wider than the top eave from the RTS camera, not an X; one bright core at dusk |
| dusk | lanterns under the keep's second eave (x 10, z 11) | a warm band at dusk; 2 glow groups (band + core) |
| court | the road the lane's full width (polished andesite 0.45, stone-brick kerbs); workshop props on the strips | a lane from the gate to the keep in rts; a half-built cart on the north strip |
| gate | obsidian only under the gate's eaves (x 0..2) | gate interior mean ~0.15 (band 0.05-0.18); no black square in the court |

## Detail round 2 - measured (eyes 41170130456)
sea lantern up 0.90 (the core is the brightest cell); prismarine walls (the crown) up 0.62 / sat 0.40 (animated
texture: teal / ice-blue between stills, critic b); 17 / 17 green. Critics 6 / 6.5 vs round 1 rescored 5.5 / 5.5.

## Detail round 3 (the last detail round; one change per element) - expected
| Element | Change | Expected |
|---|---|---|
| court lane | polished deepslate road z 6..10 (no kerbs), mud strips z 4..5 / 11..12 | lane 0.30 (up) vs court 0.53: a dark-grey lane from the black gate (0.09) to the keep |
| props | the cart in open sky on the north strip (x 5..8, z 4..5), iron tubes (no copper rods) | the cart seen whole in rts_close; no orange in the court |
| roofs | a dark-prismarine slab upturn on each lip corner | small dark corner lifts (sub-blocks), no cubes |
| crown | a flat deepslate-brick ring 7 across on spokes round the sea-lantern core on a post (no vertical ring) | an "O" with a bright dot from the RTS camera, no cross / letters; deepslate brick wall up 0.32 (dark ring on light roofs); no flicker |
