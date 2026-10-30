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
