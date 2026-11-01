# Vehicle lab concept - REBUILD brief (shared by all 7 factions)

Repo: /root/bat, branch claude/vehicle-labs (checked out). Do NOT commit, push or switch branches. Seven agents work in
this checkout at once: touch ONLY your own files (named in your prompt). Read-only use of everything else.

## Why this is a rebuild
lovish saw the first concepts (11x11, ~800 blocks, e.g. design/vehicle_labs/storm_l1.png) and said: "wow the current
labs look really bad" and "the buildings need to be as detailed as our reference art". The bar is the White Angel
capitol v4: open design/buildings/white_angel_capitol_v4.png and LOOK at it - his reference photo in the middle, our
build on the left. That is the level: real massing, a ground story (crag, terraces, yard), clustered silhouette,
tracery and bays, deep walls, lit windows, materials varied in zones. It was made by tools/capitol_white_angel_v4.py
from a PART LIBRARY (tools/gothic_parts.py, tools/gothic_towers.py): read their docstrings and conventions first.
The North Star (bar is "the best game in the world", hyper-realism FIRST with a touch of dark fantasy; judge against
the best real reference, never "good for Minecraft").

## Method (copy v4's)
1. STUDY your real-world reference(s) (given in your prompt). Write a short "THE REFERENCE, STUDIED" section at the top
   of your script, like capitol_white_angel_v4.py does: masses, proportions, materials, how the ground meets the
   building, what gives it depth, its silhouette. Use WebSearch/WebFetch to read about the real place (dimensions,
   construction, details) - text only, no copying of anyone's builds.
2. PART LIBRARY: build your faction's parts as small, well-made, rotatable elements (Part-style: own frame, front faces
   south +z, y=0 floor, roles -> palette). Reuse gothic_parts / gothic_towers parts directly where they fit (Sunforged
   and Gravebound especially) and mine the faction's capitol script and T1 lab script for its kit. Aim for 8-12 parts
   (e.g. bay, buttress/pier, roof/eave module, tower, gate, gantry, hoist, chimney/spire, ground/crag, props).
3. ASSEMBLE L1 and L3 of the lab from the parts (L3 adds, never removes - every non-air cell of L1 stays non-air in L3;
   same footprint; it shows how the lab grows: bigger gantry tier + a landmark).
4. RENDER with the textured renderer (generate the pack once if missing: `python3 tools/gen_blocks.py`, already done):
   `python3 tools/structure_render.py <your L1.nbt> <your L3.nbt> <faction capitol name> -o /tmp/<you>_r<N>.png --px 18`
   and the flat preview `python3 tools/preview_structure.py <L1.nbt> -o /tmp/<you>_p<N>.png --rts --cell 9`.
5. FRESH CRITIC each round: spawn a NEW general-purpose subagent (model opus) that has not seen your work, give it ONLY
   the two PNG paths, design/buildings/white_angel_capitol_v4.png and the reference description, and ask it to score 1-10
   "as detailed and real as the reference art" and list the 5 biggest faults, harshly. Fix them. Repeat until the
   critic scores >= 8 (or 7 rounds), and record each round's score.

## Hard numbers (L1)
- Footprint 17 x 17 (x 0..16 west->east, z 0..16 north->south). L1 height <= 26, L3 <= 36. Ground layer convention as
  tools/lab_grove.py.
- At least 3000 blocks; at least 40% "fine" blocks (stairs, slabs, walls, fences, panes, bars, trapdoors, buttons,
  chains, lanterns, candles, rods, doors, carpets, banners). No flat wall run longer than 4 cells without articulation
  (pier, bay, string course, window, trim). Camera faces (west x=0, south z=16, roofs) have >= 3 depth planes.
  Lit windows / warm light at dusk. A ground story (terrace, crag, yard, props) - it must not sit on a flat slab.
  A clustered, readable silhouette from the RTS camera (south-west, looking north-east).
- EXIT LANE: clear 5 wide (z 6..10) and 8 tall from the spawn cell (x 8, z 8) straight out through the WEST face.
  Every other walkable gap >= 4 wide x 8 tall, or sealed. No pockets: `python3 tools/audit_structure_pockets.py`
  (check how it takes a file). Pits/fire sealed from walkable floor. Nothing that pops off (supported blocks).
- GANTRY + HOIST over the bay at y >= 9 on piers outside the lane; the GATE LEAF drawn open. List anchors at the top of
  the script ("ANCHORS: ..."): these become client-drawn moving parts in game.
- Vanilla 1.20.1 blocks only (no tuff bricks / polished tuff - 1.21), states that stay as placed, waxed copper only,
  dead coral only, persistent leaves with true distance.

## Your outputs (exact names in your prompt)
tools/lab_vehicle_<f>.py (writes both .nbt; may import gothic_parts etc.), tools/<f>_parts.py if you make a library,
design/vehicle_labs/<f>_l1.nbt, design/vehicle_labs/<f>_l3.nbt, and final renders design/vehicle_labs/<f>.png
(structure_render: L1 + L3 side by side, --px 18) and design/vehicle_labs/<f>_rts.png (preview_structure L1 --rts).

## Report back (under 250 words)
Files, sizes and block counts, fine-block %, the critic's score per round, a 4-line description (reference used, look,
gate, motion anchors), and the 2 weaknesses still left.
