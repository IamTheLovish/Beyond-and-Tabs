# Tools brief (Opus builder, started Oct 28 04:36): lighting in the renderer + a shape kit
TASK 1 - opt-in `--light` in tools/structure_render.py (default off; tools/ref_compare.py uses it by default): per-face
ambient occlusion (Minecraft smooth-lighting style) + a directional low SUN shadow (match the eyes rig
LIGHT="-0.85,-0.35,0.30" if found; voxel ray-march from each visible face toward the sun); light sources stay bright;
<= ~30 s for 30x40x30. Verify on design/vehicle_labs/sunforged_l1.nbt and structures/jade_pagoda.nbt with/without.
TASK 2 - tools/shape_kit.py: SDF primitives (box, cylinder, cone, ellipsoid, onion dome, pointed/ogee spire, torus,
lofted taper) + union/subtract/intersect; voxelize into palette roles with an edge pass placing STAIRS/SLABS (correct
facing/half) on slopes; hollow shells; compatible with gen_structures.Structure / gothic_parts Part; demo writing an
onion dome, ribbed dome, spire, tapering round tower with cone, swept curved roof; unit tests (counts, stair facing,
hollow shell). ruff clean. Render demos at --px 32 --light and check curves read smooth.
