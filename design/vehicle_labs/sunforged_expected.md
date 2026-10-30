# Sunforged lab (Sunwain Court) - expected numbers, written BEFORE each round's stills land

The rubric: the designer pass (itachi2-api runs 20261030-162547-sunforged-design + -163025-sunforged-design-cont), seven
axes as the Engine Shed's so scores compare: RTS read, close read, landmark/towers, gate, roofs, materials/weathering,
dusk light. Measured numbers come from tools/camera_colour.py on the stills of ONE eyes run (dusk differs between runs).

## Massing (eyes run 41124572036, build e7b4e94, plain lane, branch pack, keep_only, times 6000)
Two blind fresh critics (runs 20261030-165241-sunforged-massing-critic-a, -165245-...-critic-b) on shuffled stills:
| option | RTS | landmark | gate | roofs | resemblance | rank |
|---|---|---|---|---|---|---|
| A (ref21 keep) | 6 / 7 | 5 / 6 | 5 / 5 | 6 / 6 | 6 / 7 | 1st / 1st |
| B (SE campanile) | 5 / 6 | 4 / 5 | 5 / 5 | 5 / 6 | 5 / 6 | 3rd / 2nd |
| C (cloister) | 5 / 5 | 7 / 7 | 6 / 6 | 5 / 5 | 4 / 4 | 2nd / 3rd |
| control (round 2 L3) | 5 / 5 | 4 / 4 | 3 / 3 | 4 / 3 | 3 / 3 | 4th / 4th |
Shared faults on A: the spire flattens to a salmon square at the RTS camera and barely tops the towers; four equal
towers compete; the gate is not the darkest opening (the south wing's dark tiles and the light well beat it).
Measured: granite steep roof hue 16, sat 0.36-0.39, value 0.55 (salmon); polished andesite walls west 0.29 / south 0.38.
Control on the camera table: snow share 0.25 of the rts pixels (calcite / quartz / diorite).

## Massing revision A2 (expected, before the stills)
- landmark lead 25 - 16 = 9 (A: 7); the spire's tower shaft rises 4 clear of the terrace (A: 2, hidden by the crowns).
- the gate: tools/test_sunforged_lab.py's 34-still count inside the gate >= 300 (A2 in the model: measured below); its
  mouth lined dark (blackstone bricks, value 0.13-0.18 on camera) - the darkest large area on the west face.
- no dark area on the south face larger than the gate (the wing roof pale smooth-stone slab 0.42-0.54).
- critics: landmark >= 6.5 (A 5 / 6), gate >= 6 (A 5 / 5), RTS >= 7.

## A2 measured by eye (eyes run 41127459982, A and A2 in one run)
The spire now stands on its shaft in the 3/4 still (a tower with a cone, not a cone on the roof); from the RTS camera
the cone is still mostly a salmon square, the shaft's south face shows under it. The front / back tower hierarchy
reads. The gold halo ring is a bright square on the terrace - a second focal point risk (left for the critics).
No critic run on A vs A2: both critics' diagnoses were applied one change per element; detail round 1 builds on A2.

## Detail round 1 (expected, before eyes run 41138964754; control = round 2 in the same run)
- camera tests: 14 / 14 green once the mouldings are in the camera table (12 / 14 now: colour coverage only).
- wall field value (rts_close, 34): 0.30-0.36 (andesite + stone-brick quoins + string courses), inside 0.28-0.50.
- snow share: < 0.02 (no calcite / quartz / diorite; control 0.25).
- critics vs round 1's 4/10 and the massing A scores: RTS 7, close 6, landmark 6, gate 6, roofs 6, materials 5, dusk 5.

## Detail round 1 measured (eyes run 41138964754, round 1 and the control in one run)
- camera tests 14 / 14 green after the mouldings were added to the table (camera_colour.py --add-only).
- snow share (rts / 34): round 1 0.000 / 0.000, control 0.248 / 0.185 (expected < 0.02: met).
- wall area value (west + south hits, rts / 34): round 1 0.348 / 0.347, control 0.418 / 0.435 (expected 0.30-0.36: met)
  - and that is the fault both critics named: inside the ground's mid band 0.28-0.50 (Art method: lights 0.50+ belong to
  buildings). Wall pixels: dressings (stone bricks + their stairs) 0.44, the andesite field 0.11.
- critics 5 / 5 overall (control 4 / 4); expected RTS 7, landmark 6, gate 6 - RTS and gate missed by 2 and 1.

## Detail round 2 (expected, before its eyes run; one change per element)
Wall stone light (field polished diorite on vertical faces only - its top reads as snow, so crowns and merlons stay stone brick; quoins stone brick / diorite), spire dark slate, halo removed,
lancets grey glass, wing / keep parapets plain coping.
- wall field value (polished diorite; pack-texture prediction scaled by the measured andesite / stone-brick / calcite
  ratios west 0.59, south 0.79): west 0.45, south 0.60, field area 0.50-0.55; dressings 0.30-0.35 (gap >= 0.15).
- snow share < 0.02 (diorite south 0.60 < 0.70).
- gold share of the rts pixels 0.031 -> 0.005 in the camera model (finial + rose boss only).
- spire value 0.18-0.28 (deepslate; granite was 0.55): the darkest large area at the top, over the 0.47-0.54 terrace.
- critics: RTS 6+, landmark 6+, materials 6, dusk unchanged (5 / 3).
- snow check on the stills: polished diorite's SOUTH face is not in the camera table yet (the lookup falls back to its
  up face, 0.769 = snow); measure it on round 2's stills against smooth quartz (south 0.718) and calcite (0.706) and the
  snow cut (value >= 0.70, sat <= 0.12) before trusting the wall. Expected south 0.55-0.62 (diorite wall south 0.553).
  If it reads as snow: fall back to a light stone that does not, and log it.

## Detail round 2 measured (eyes run 41145225614, round 2 and round 1 in one run)
- polished diorite in that run: west 0.435, south 0.592, sat <= 0.026 - under the snow cut (0.70) and under smooth
  quartz (0.718) and calcite (0.706): not snow. Its UP face 0.769 (snow): no diorite top is seen (camera model 0 % up).
  (The table keeps the earlier diorite west entry, 0.373 / n 180 - --add-only never overwrites.)
- snow share of the building's real pixels (rts / 34): round 2 0.008 / 0.003, round 1 0.000 / 0.000.
- all wall pixels (rts / 34): 0.384 / 0.368 (round 1 0.329 / 0.317) - the dressings keep the mean in the mids.
- spire: deepslate 0.17-0.20 (granite 0.55). Gold 0.031 -> 0.005 of the rts pixels.
- critics 5 / 5 again (no gain): expected RTS 6+, landmark 6+, materials 6 - landmark met (6 / 6), materials missed (4 / 4).

## Detail round 3 (expected, before its eyes run; the last detail round - stop after 2 without gains)
- corners in the field stone: test_r3_no_ladder_stripes 0 flips (round 2: red); field share of the wall pixels up.
- spire Macaw's spruce planks steep roof: value 0.33-0.40 (pack texture 0.42 x the measured up-face ratio 0.86),
  sat 0.25-0.33, hue 25-35 (a weathered timber brown) - inside SPIRE_VAL 0.30-0.45 / SPIRE_SAT 0.10-0.35.
- halo: 12 stone-brick slabs (up 0.467) in the terrace (0.549): a quiet ring, delta about 0.08.
- critics: materials 5+, roofs 5+, landmark holds 6.
