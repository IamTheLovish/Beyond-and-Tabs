# Ember Pens (Ashborn T1 vehicle lab) - brief for the next round (written Nov 4 2026, after the Fable review)

Give this to the next builder AND to the next critic. It replaces nothing in `tools/test_ashborn_lab.py` (21 camera
tests, every threshold pinned - never lowered) and adds to `design/vehicle_labs/ashborn_expected.md`.

## What the building is
The Ashborn vehicle lab: a dark basalt citadel in three stepped tiers (ref29, `design/refs/vehicle_labs/ref29_dark_
tiered_lava_fortress.png` - imitate THAT picture, no blend of references), its ground storey a row of beast pens in the
south face, its ember held in sunken pools on the terraces and in the forge heart over the gate. One glow story on the
gate axis: gate heart -> court -> pools -> forge at the keep's foot. Plot 17 x 17, L3 <= 26, L1 <= 16 (L1 is the first
stage of the same design: every L1 cell survives into L3). Lane x 0..8 z 6..10 y 1..8 and the spawn column (8, y, 8)
stay empty; no pockets (`tools/audit_structure_pockets.py`); nothing floats.

## Where it stands (review build, branch claude/review-ashborn; control = `design/vehicle_labs/ashborn_l3_r3.nbt`)
Offline 5.5 / 10 against the north star (critique in PR). Done and tested: the pens are 2-deep mouths behind inset
bars under proud lintels (chains in the dark); two 1 x 3 ember pools flank the court; three capped piers stand proud
of the ring's south face. Camera-model numbers: glow 1.39 % (band 0.5-2), lights 5.9 % (4-12), plinth 0.252 / walls
0.189 / paving 0.333 / caps 0.503 (gaps 0.063 / 0.144 / 0.170), thin blocks 34 up 0.439 (ceiling 0.45).

## The next round's one job: the TOP (the roof is the fifth facade; from the RTS camera it is most of the building)
Faults every critic has named and this review left: (a) the keep's Macaw's cone reads as a black pyramid / an X from
above; (b) identical white merlon posts on every tier edge (plinth, ring, crest, keep band) are crenellation noise
with no hierarchy; (c) the keep terrace's tuff fuses with the keep (no edge between them); (d) the cones of the two
towers read as an X too. Pick ONE of these per round, in this order of value:
1. **Cap hierarchy.** Merlons stay on the ring's outer edge and the keep's parapet (where men fight); the plinth's
   parapet and the crest's column caps become a plain coping. BEFORE building: the caps band (0.48-0.58) is the MEAN of
   every cap top the rts_close / 34 stills see and the light share (value >= 0.50) must stay 4-12 % of the rts pixels -
   a coping darker than 0.48 pulls the mean down, a full slab coping doubles the top area and pushes the light share
   over 12 % (round 1 measured slab caps at 20 %). So: choose a coping block, get its up / west / south faces into
   `camera_colours.json` (`tools/camera_colour.py --add-only`, from an eyes still - never by guessing), then run the
   model numbers (`tools/test_ashborn_lab.py` helpers) and write the expected caps mean, light share and 34-up thin
   share into ashborn_expected.md BEFORE the eyes run. Candidate: a stair coping (polished andesite stairs, up 0.455)
   on the plinth only - the model then says whether the mean holds.
2. **The keep's crown.** Replace the staged cone's lower ring with a drum (one full-block stage, dark, with a string
   course) so the cone starts one stage higher and smaller; or give the cone a lit lantern stage ONLY if it stays
   inside the light zone (x <= 12, z 4..12, y <= 17) - it does not (y 19+), so no light up there: shape only. Keep
   the lead 6-10 over everything outside the keep's zone and the two tower needles as the only other needles.
3. **A terrace edge for the keep.** The keep terrace's west and south rim already carries a cap; the problem is its
   north / east inner edge against the keep: a 1-wide dark walk (deepslate tiles at y 14) round the keep's foot inside
   the paving would draw the edge - paving mean stays 0.32-0.44 (check: the dark walk is an up face of a full block
   on top of its column at y >= 5, so it COUNTS as paving; keep it under ~20 % of the paving pixels).

## What to measure (every claim a number, written before the stills land)
- `python3 -m unittest tools.test_ashborn_lab` 21/21; the numbers from the test helpers for rts / rts_close / 34:
  role means (plinth, walls, roofs, paving, caps) and the three gaps; light share; glow share and per-pool pixels;
  thin-block share per face (34 up is at 0.439 of 0.45 - any new slab / stair / wall on a top must be paid for by
  removing one elsewhere); the landmark lead; bars seen in 34 (46 of > 40 - the mouths' lintels hide the bars' tops:
  do not deepen the lintel).
- Offline: `python3 tools/structure_render.py design/vehicle_labs/ashborn_l3.nbt design/vehicle_labs/ashborn_l3_r3.nbt
  -o sheet.png --ground 6 --px 28` (lit; never --flat) - before beside after on ONE sheet; plus the camera-model rts
  paint (the review's scratch script in the PR) for the tops, since the 2:1 iso renderer hides 1-deep pools behind
  a 2-high rim.
- Eyes: ONE run, the control first as a sacrificial slot (the first structure of a run can be shot with a harness
  fault), L1 then L3, `rts`, `rts_close`, `34`, `dusk`; two fresh critics, scores per axis (RTS, close, landmark,
  gate, roofs / tops, materials, dusk) and overall, the control rescored in the same run. Dusk is where the pools pay:
  compare dusk only inside one run.

## What to avoid (paid for already)
- Lights outside the zone x <= 12, z 4..12 (court y <= 12 / gate y <= 17): `test_one_glow_focus` fails on one stray.
- Magma as a glow (reads dark red, hue 12, value 0.31); shroomlight's west / south table entries are another lab's
  mis-measure (hue 225) - only its UP face counts, so a glow must be a top (a pool, a floor), never a wall.
- Saturated blocks (sat >= 0.35: copper, mud bricks, red terracotta, dark oak): the loud cap is 2 % of the rts pixels.
- Polished blackstone (purple-grey), calcite / diorite (snow), slabs on tops (the 34 thin-block ceiling), stone
  bricks as paving (matched the caps), notches in a top course (dark squares), a 1-wide end stall (its mouth left the
  west corner a 1-thick wall), a pier adjoining the SE tower (a 4-run).
- An air pocket at ground level behind bars: bars block a unit's body; hang chains in it or do not open it.
- Judging the tops on the 2:1 iso renderer alone; judging flat; comparing dusk across runs; three colour schemes
  from three critics - score against the bands in ashborn_expected.md, not taste.
