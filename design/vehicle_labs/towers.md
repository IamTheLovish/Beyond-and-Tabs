# Stacks and towers - measured on the study builds (Oct 29 2026)

lovish, Oct 29 20:00 (on old a): "the towers on this building need more refinement". The r11 stacks and clock tower are
built to these numbers, not by eye. Measured with tools/schem_study.py on the study builds (study only: never
committed, never copied block for block - only the numbers below). Heights are above the ground course.

## Factory stacks
| measure | Victorian Factory (koin) | Industrial Area (sveinkb) | rule taken |
|---|---|---|---|
| top vs the hall | 57 over halls at ~29-32: ~1.8-2 x | 44-54 over a 2-12 yard | stack >= 1.7 x the main ridge, >= 2 x the median roof |
| base | sits on a 13 x 12 brick boiler / engine house to y 19 (33 % of the height) | the shaft grows out of a 12 x 13 block to 27 | the stack stands on a boiler house, never a free plinth |
| step-ins | 5 x 6 foot (28-29) -> a 5 x 5 stair weathering (30) -> 3 x 4 shaft (31-55) | 12 -> 9 (29) -> 7 (40): two step-ins | foot -> stair weathering -> slimmer shaft: at least one step-in |
| bands | iron bands at 29, 35, 50 (every ~15 courses, a thin full ring) | slab rings at 38 and 44 | a band every 5-6 courses on our 25-course stack |
| crown | an umber brick wall ring at 56 under a dark cut-deepslate cap at 57 | a slab ring 2 wider than the shaft at 44 | an oversailing top course one wider each side, a dark cap, smoke |
| shaft : height | 3 x 4 at 57 tall (1 : 15) | 7 at 44-54 (1 : 7) | our 2 x 2 at 25 (1 : 12) |

## Towers
| measure | 1890 Station (seuchendoktor) | Gare de Minecraft (cheesystring) | rule taken |
|---|---|---|---|
| tower vs the halls | 29 over halls at 11-12: ~2.5 x | the clock towers top 59 over a 32 median roof (1.8 x) | ours (a's height): 18 over the bay ridge 14 - a clock tower, not a landmark |
| stages | base to 6 / shaft (glazed) to 15 / a banded stage 16-21 / steps to a lantern 28-29 | shaft to 45 / clock stage + cornice 50 / open lantern 51-53 / cap 56 / finial to 62 | base, shaft with openings, clock stage, cornice, roof, finial - 4+ distinct stages |
| string courses | stone-brick stair bands at 16, 19, 21, 25 (every 2-4) | a stair cornice ring round the clock stage | a pale course at the base and under the clock stage, a cornice ring at its top |
| roof | stair-stepped crown narrowing 13 -> 11 -> 9 -> 7 -> 5 | 5 x 5 cap then a fence spire 3 x 3 -> 1 x 1 | a low copper pyramid (stairs) and an iron finial |

## Applied (r11 af, tools/engine_shed_massing.py build_af; detail in tools/engine_shed_r11.py)
- Stacks 25 and 22 (a's), on brick boiler houses (eave 6, slate gables): a 4 x 4 foot to 12, a stone-brick stair
  weathering at 13, a 2 x 2 shaft, an oversailing stair crown at top - 1, a blackstone cap; ratio 25 / 14 = 1.8 x the
  bay ridge, 2.8 x the median roof (9). Detail: dark banding every 5 courses, smoke.
- Clock tower 3 x 3 (x 1..3, z 5..7), engaged in the corner of the erecting bay (z 8) and the shop (x 4): pale base
  course (1-2), brick shaft with slit lights (3-12), a pale clock stage with dark dials (13-14), an oversailing stair
  cornice (15), a weathered-copper stair pyramid (16-17), an iron finial (18) - 4 over the bay ridge (a's relationship).

## r12 (Oct 30): lovish's picks replace the banding
- Stacks: lovish (~00:30 ET, on r11 round 3) "probably both" - a smooth tapered shaft and ONE corbelled crown. The band
  rule above ("a band every 5-6 courses") is retired for the Engine Shed; r12 keeps the foot and the heights and builds
  foot -> Macaw's stone wash -> brick-wall-sleeved lower shaft -> Macaw's brick wash -> plain 2 x 2 -> one crown of our
  cornice moulding (reignofnether:cornice_brick) under a blackstone cap.
- Tower roof: "less stepped" - a steep spire (2 rise per run, Macaw's prismarine-brick steep roof, apex 20, finial 21)
  in place of the 16-18 copper stair pyramid; the clock stage and cornice unchanged.
