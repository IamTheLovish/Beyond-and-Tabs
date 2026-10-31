# Engine Shed r12 - expected numbers, written before the stills (Oct 30 2026)

Measured with `tools/engine_shed_measure.py` (area colour = the mean RGB of the pixels the camera model puts on the
region, at the corrected 3/4 camera, FOV 77). The r11 column is measured on run 03:35Z's r11 stills; the r12 column
is the prediction, written before r12 round 1 was built or photographed.

| Measure | r11 (measured) | r12 round 1 (expected) | Why |
|---|---|---|---|
| Tower roof area saturation, rts_close | 0.213 | 0.20-0.29 | prismarine brick (pack sat 0.33) x the camera / pack ratio the copper showed (0.71-0.97); no grey lead or hip stones left to dilute it |
| Tower roof area saturation, 34 | 0.232 | 0.20-0.30 | as above |
| Tower roof area hue | 143 (rts_close), 143 (34) | 150-180 | prismarine is bluer than weathered copper (verdigris) |
| Dusk window glass, area hue | 271 (blue-violet) | 15-45 (warm orange) | the panes show a brick floor and jambs lit by warm block light; no white glow block behind the glass |
| Dusk window glass, area saturation | 0.126 | 0.25-0.55 | warm-lit brick behind clear glass |
| Dusk window glass, area value | 0.335 (1 % of pixels > 0.75) | 0.18-0.33, <= 1 % > 0.75 (softer) | the room is lit at block light 10-12, not a level-15 face on the pane |
| Gate frame, camera value (table) | 0.13-0.22 (blackstone) | 0.30-0.43 | stone bricks / smooth stone / stone-brick stairs on camera |

Expected critic movement (r11: RTS 6 / close 5 / towers 4 / gate 3 / roofs 6 / weathering 2 / dusk 3): towers +2,
gate +2, weathering +2, dusk +2; RTS +1; roofs unchanged (frozen).

## Round 1 measured (eyes run 41022055197) - see SCORES.md
Spire sat 0.274 / 0.280 (in band), hue 158 / 152, value 0.544 / 0.520 (not predicted: pale). Dusk glass hue 2, sat
0.163, value 0.351 (missed: red, not orange). Gate frame 0.30-0.43 (met).

## Round 2 - expected, written before its stills
| Measure | Round 1 | Round 2 expected | Why |
|---|---|---|---|
| Tower roof area saturation (pixels), rts_close / 34 | 0.274 / 0.280 | 0.19-0.25 | a third of the seen spire becomes grey-green cyan terracotta (vanilla look sat ~0.05-0.1) |
| Tower roof area value | 0.544 / 0.520 | 0.44-0.50 (test band 0.36-0.48: may land on its top edge) | cyan terracotta ~0.36-0.42 |
| Tower roof streaked share (>= 20 % under the median value) | 0.003 | 0.20-0.35 | the drips |
| Dusk window glass hue / sat / value | 2 / 0.163 / 0.351 | 25-45 / 0.25-0.45 / 0.30-0.45 | warm pale birch (pack luma 0.65) under warm block light, not red brick |
| Gantry posts + beam, camera value | 0.13-0.22 | 0.28-0.48 | stone-brick walls, smooth stone slabs |
| Stack between wash and soot, value / sat | bricks_roof 0.525 / 0.5 | brick 0.33-0.41 / 0.31-0.43 | no wash, no sleeve |
Expected critics: towers +1 (one clean step-in, no second ring; spire no seam, not the lightest area), weathering +1
(drips, longer streaks, spire streaks), dusk +1 to +2 (warm panes), gate 0 to +1 (gantry), RTS +0.5.

## Round 2 measured (eyes run 41026048601, same-run control round 1)
Spire sat 0.215 / 0.220, value 0.464 / 0.443 (in the band 0.36-0.48; but the drips read as dark wedges). Dusk glass
unchanged inside the run (hue 271 -> 249, value 0.22 -> 0.23; round 1's own run had read hue 2: dusk differs between
runs). Noon glass hue 7 -> 37 (the birch shows at noon). Gantry beam top 0.51 (over 0.50). Coal stage 0.07-0.09.

## Round 3 - expected, written before its stills (two shapes: r3a spire, r3b cupola = brass_t1_r12.nbt)
| Measure | Round 2 | Round 3 expected | Why |
|---|---|---|---|
| Spire area sat / value, r3a | 0.215 / 0.464 | 0.26-0.29 / 0.51-0.56 | one material again: round 1 measured 0.275 / 0.549 |
| Cupola area sat / value, r3b | - | 0.24-0.30 / 0.50-0.60 | the same prismarine, 45 deg pieces (flatter tops catch more light) |
| Coal stage area value | 0.07-0.09 | 0.20-0.28 | cobbled deepslate facets (pack luma 0.24) over a 2-block coal core |
| Dusk glass hue (area) | 249 | 30-60 on the lamp windows, 200-260 elsewhere; whole-region mean 220-280 (lamps are small) | a lit lamp in 6 rooms, <= 10 % of each pane |
| Dusk pixels > value 0.75 | 0 % | 0.5-3 % | the lamp points |
| Shaft below the soot course | drip 0.19-0.25 | brick 0.33-0.45 | no drip |
| Gantry beam top | 0.51 | 0.45-0.48 | stone slab (measured up 0.471) |
Expected critics: towers +1 (no arrow; cupola or spire chosen side by side), gate +1 to +2 (the coal lump broken -
lovish's own target), dusk +1 (warm points inside), weathering 0, RTS +0.5.

## Round 3 measured (eyes run 41034045196; round 2, spire A, cupola B in one run)
Spire A sat 0.276 / 0.278, value 0.545 / 0.524; cupola B sat 0.298 / 0.291, value 0.565 / 0.479. Dusk glass hue 249 ->
29, sat 0.19 -> 0.13, value 0.23 -> 0.42, bright 0 -> 1.3 %. Both critics picked A (decided overnight).

## Round 4 - expected, written before its stills (spire A + the round-4 designer's five changes)
| Measure | Round 3 A | Round 4 expected | Note |
|---|---|---|---|
| Spire area sat | 0.276 / 0.278 | 0.27-0.30 | dark prismarine (pack sat 0.36) on a quarter of the cells |
| Spire area value | 0.545 / 0.524 | 0.46-0.52 | the round-3 band 0.50-0.62 may fail at its low edge; the designer expected 0.44-0.54 |
| Spire streak share | 0.046 | 0.18-0.30 | r11's band 0.15-0.45 |
| Dusk glass hue / sat / value | 29 / 0.13 / 0.42 | 20-35 / 0.20-0.35 / 0.33-0.45 | acacia (pack sat 0.62) instead of birch (0.33) |
| Gantry share in 0.27-0.50 | 0.36 | 0.75-0.95 | stone-brick slab top ~0.38 |
| Stack soot top-3 / top-4 / below | 0 / 0 / 0 | 0.5 / 0.25 / 0 | built exactly |
| Eave soot y5 / y4 / y3 | (streaks) | 0.50 / 0.22 / 0 | built exactly on the seen piers |
Expected critics: weathering +1 to +2, dusk +0.5 to +1, towers +0 to +0.5, gate 0 to +0.5.
