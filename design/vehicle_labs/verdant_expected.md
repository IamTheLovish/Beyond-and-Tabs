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
