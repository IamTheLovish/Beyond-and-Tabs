# Verdant lab (Rooting Hollow): expected numbers, written before each round's stills land

Numbers on camera (eyes plain lane, named noon), predicted from tools/test_verdant_lab.py's camera model and the
camera table (design/vehicle_labs/camera_colours.json). The reading is compared with these after the stills land, so
it can't drift to fit the hope.

## Detail round 1 (massing A + the critics' shared faults + the first grammar)
Written Oct 31 ~01:35Z, before eyes.

| Item | Band (test) | Expected | Why |
|---|---|---|---|
| Tree lead over built | 5..10 | 8 (tree 25, hall ridge 17) | hall narrowed to z 4..12, ridge 17 |
| Canopy top value / sat / hue (rts) | 0.42-0.60 / 0.20-0.45 / outside 41-161 | 0.73 / 0.23 / 338 | cherry tops (up 0.85) with ~11 % spruce gaps (band re-set to 0.60-0.82 in aae55756) |
| Canopy share (rts) | 0.30-0.50 | 0.40 | |
| Trunk + roots share (rts_close / 34) | 0.15-0.28 | 0.22 / 0.18 | roots pour over the gallery and down its south face |
| Trunk value (rts_close / 34) | 0.32-0.45 | 0.34 / 0.35 | stripped birch bark, dark oak grooves, oak-log tops |
| Walls (rts_close / 34) | 0.30-0.42 | 0.33 | mud bricks unmeasured: expected west ~0.36, south ~0.45 (light tan) |
| Roofs (rts) | 0.34-0.46 | 0.38 | spruce steep roof 0.39, gallery slab 0.40 |
| Gate seen-into (34 px) | >= 200 | ~200 | splayed outer order |
| Loud pixels (rts) | <= 3 % | ~0 % | no azalea, no moss blocks, no dark oak planks |
| Lights | <= 2 clusters, <= 6 | 1 cluster, 2 | the heart-glow only |

### Measured, round 1 (eyes 41155254248, camera table updated --add-only from its verdant_l3 / verdant_l1 stills)
| Item | Expected | Measured | |
|---|---|---|---|
| Tree lead | 8 | 8 (25 / 17) | as expected |
| Canopy top (rts) | 0.73 / 0.23 / 338 | 0.731 / 0.228 / 338 | in the re-set band |
| Canopy share (rts) | 0.40 | 0.398 | |
| Trunk + roots share (rts_close / 34) | 0.22 / 0.18 | 0.221 / 0.178 | |
| Trunk value (rts_close / 34) | 0.34 / 0.35 | 0.338 / 0.346 | |
| Walls (rts_close / 34) | 0.33 | 0.312 / 0.309 | mud bricks darker than guessed: west 0.30, south 0.42 (sat 0.37) |
| Roofs (rts) | 0.38 | 0.382 | |
| Gate seen-into (34, model) | ~200 | passes (>= 200) | |
| Loud (rts) | ~0 % | 0 % | |
| Lights | 1 cluster, 2 | 1 cluster, 2 | |
All 17 camera tests green.

## Detail round 2 (round 1's critics: both 5/10; one change per element)
Written Oct 31 ~02:25Z, before eyes. Elements: limb (an arch over the well onto the ridge, 2 cells of sky under it;
the old west limb gone), gate (mangrove roots under the jetty brackets frame it; the dark apron outside removed),
canopy (all-cherry tops, clumps with valleys; tier 1 kept off the air over the hall), trunk (smooth taper, a gentle
spiral, grooves in runs >= 4 rows; one root pour instead of three, single tone), glow (moved to the trunk's south
foot, where the cameras look).

| Item | Band (test) | Expected | Why |
|---|---|---|---|
| Canopy top value / sat / hue (rts) | 0.60-0.82 / 0.20-0.45 | 0.80 / 0.25 / 336 | pure cherry tops (table); in game the clumps' shade may lower it |
| Canopy top one tone | >= 0.97 | 1.00 | |
| Canopy share (rts) | 0.30-0.50 | 0.33 | valleys and the kept-open air over the hall |
| Trunk + roots share (rts_close / 34) | 0.15-0.28 | 0.24 / 0.20 | |
| Trunk value (rts_close / 34) | 0.32-0.45 | 0.33 / 0.34 | |
| Sky under the limb: cells / px (rts_close, 34) | >= 8 / >= 40 | >= 8 / > 40 | |
| Heart-glow px (rts_close, 34) | >= 20 | > 20 | four shroomlights on the trunk's south foot |
| Walls / roofs | 0.30-0.42 / 0.34-0.46 | 0.31 / 0.38 | unchanged |

### Measured, round 2 (eyes 41157566607; table --add-only: shroomlight up 0.96 / sat 0.71 added - its older south /
west entries, 0.26 / 0.28 hue 225, came from another run and look like a covered light; left as they are, add-only)
Camera-model numbers on the round-2 table: canopy 0.800 / 0.247 / 336 (expected 0.80), one tone 1.00, canopy share
0.334, trunk share 0.236 / 0.198, trunk value 0.328 / 0.337, walls 0.311, roofs 0.382; the glow is in the stills
(orange on the trunk's south foot in rts and 34). All 21 camera tests green.
