# Ashborn vehicle lab (Ember Pens) - expected numbers, written BEFORE each round's stills land

Measured with the camera model (tools/test_ashborn_lab.py helpers on building_gates.shoot, FOV 77) and the camera
table (tools/camera_colour.py, `--add-only`). Bands: the designer pass (itachi2-api/runs/20261031-001548-ashborn-design).

## Detail round 1 (A2 + detail), expected (Oct 31, before eyes)
Model numbers on the generator (rts_close / 34; colours from the table where measured):
- plinth (polished basalt columns, deep joints every 4th, dark tile pilasters, light lintels over the pens):
  0.238 / 0.241 (band 0.22-0.32)
- walls (deepslate tiles, slots every 4th): 0.177 / 0.177 (band 0.12-0.20); plinth - walls 0.061 (gap >= 0.06: tight)
- roofs (Macaw's deepslate cones): 0.224 (band 0.15-0.25)
- paving (stone bricks): 0.350 / 0.356 (band 0.32-0.44); paving - walls 0.17 (>= 0.14)
- caps (andesite walls): NOT measured yet - expect up 0.50-0.54 (andesite up 0.518, andesite slab up 0.545);
  caps - paving expected ~0.16 (>= 0.06)
- lights (value >= 0.50): expect ~5 % of the rts pixels (andesite-wall tops 3 392 px + the heart's magma 883 px of
  84 844) if the wall tops read >= 0.50; under 4 % if they read 0.48 -> then round 2 widens the T1 cap
- glow: the heart's magma 883 rts px = 1.0 % (band 0.5-2 %), all in the gate; magma NOT measured yet - expect hue
  15-30, sat >= 0.6, value 0.6-0.8 on its west face; the up face (the heart's floor = the gate's head) unknown
- landmark lead 8 (keep needle 25 over the crest 17), on the gate's axis (13, 8)
- gate 5 x 8, 316 34-still pixels inside it (>= 200)
- thin blocks: up 0.40 / 0.44, west 0.33 / 0.34, south 0.32 / 0.35 (band 0.15-0.45)
- tier edges capped 88 of 93 (>= 80 %)
- black wool (the pens' hides) not measured yet
