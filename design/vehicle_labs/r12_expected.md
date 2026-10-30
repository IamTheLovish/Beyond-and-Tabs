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
