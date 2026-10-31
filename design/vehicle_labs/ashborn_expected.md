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

### Round 1 measured (eyes 41171365873; the control's noon stills in that run spoiled - dark noon - not used)
- caps (andesite wall up) 0.511 (expected 0.50-0.54); lights 3.9 % (expected ~5 %: the magma did not read light)
- magma west 0.31 / up 0.48, hue 0-12: dark red, NOT a glow (expected hue 15-30, value 0.6-0.8) -> glow 0 %
- plinth - walls 0.0598 (expected 0.061: missed by 0.0002); black wool 0.08; 15/18 locally
- critics 20261031-012710 (a) / -012718 (b): 5 / 5.5 vs the control's 3 / 3

## Detail round 2, expected (before eyes; control = round 1, design/vehicle_labs/ashborn_l3_r1.nbt)
One change per element (round 1's critics, both): (1) the heart's glow shroomlight, not magma (vanilla texture mean
hue 27 / sat 0.71 / value 0.94: expect up 0.65-0.85 on camera, glow share 0.5-0.7 % from its up face alone - the
table's shroomlight west / south entries were measured on another lab at hue 225 / 0.28 and stay, --add-only);
(2) paving tuff (0.34), not stone bricks; (3) the joints of each tier's top course a post (dark under a cap, a light
merlon on the uncapped court rim), not a notch - no dark squares in the paving; (4) the plinth's top course on its
west / south edges smooth stone (a light band under the parapet).
Model numbers (rts_close / 34): plinth 0.251 / 0.254, walls 0.183 / 0.183 (gap 0.068), roofs 0.224, paving 0.325 /
0.331, caps 0.502 / 0.507; lights 4.0 % + the heart's up face (~0.6 % if >= 0.50) -> ~4.6 %; thin blocks up 0.40 /
0.45 (34: on the edge), west 0.35 / 0.36, south 0.34 / 0.38; lead 8; gate seen 316.

### Round 2 measured (eyes 41173208608, all three structures clean)
- shroomlight up 0.863 / sat 0.76 / hue 26 (expected 0.65-0.85: slightly over); glow 0.59 % (up face), lights 4.6 %
  (expected ~4.6 %); smooth stone west 0.34 / south 0.43; 18/18 locally
- critics 20261031-015356 (a) / -015404 (b): 6 / 6 vs round 1 5 / 5 - GAIN (tuff paving and the posts better, both;
  the heart brighter but still an L; the smooth-stone band not visible to either)

## Detail round 3 (the last detail round), expected (before eyes; control = round 2, ashborn_l3_r2.nbt)
One change per element (round 2's critics, both): (1) the heart's back wall dark basalt, the glow only on the niche's
floor (one compact lit ledge over the mouth, not an L) - glow 0.59 % (the floor's up face, unchanged); (2) a string
course (top-half deepslate-tile slab) across every seen deepslate face at y 9 (ring) and y 16 (keep) - walls 0.185
(from 0.183), thin blocks west 0.40 / south 0.40-0.43 (from 0.35-0.38); (3) the crest's every column and the recessed
jamb columns end in a light cap, the keep band's far sides are tuff (the paving: 0.334 from 0.325). Lights 5.0 %.
