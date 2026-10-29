# Architecture rules - how a master builder makes a building read as real (Oct 29 2026)

lovish, Oct 28: "think more like an architecture professional and study how they make buildings", "its not looking
realistic enough", 23:44 "become a master architect". This is the rulebook every building generator and art round is
held to. Part 1 is architecture (why buildings look the way they do). Part 2 is measured: 26 downloaded builds
(Itachi 3's planetminecraft set, study only - never committed, never copied) run through `tools/schem_study.py`, next to
our Engine Shed r7. Part 3 is the gates a generator test checks. Refs: design/refs/vehicle_labs/ref45-70 and the notes in
design/vehicle_labs/engine_shed_detail_grammar.md.

## 1. Principles (the architect's view)
1. **Tectonics - show how it stands up.** Piers and columns carry; lintels, arches and beams span; walls are infill
   between them; roofs sit on walls and oversail them. Every element a viewer sees should have a job. Ornament goes
   where structure happens (a capital where a pier meets a beam, a corbel where an upper storey oversails), never as
   paint on a flat wall (r1-r7's chequers and coloured panels).
2. **Base - body - cap.** Every building and every tower stage: a heavier, paler stone base at the ground; a body of
   the main material; a cap (cornice, eave, parapet, roof). Three bands, each with its own edge line.
3. **Hierarchy of masses.** One dominant mass (a tower, the big roof), secondary masses (halls, wings) clearly
   subordinate, tertiary pieces (porches, dormers, lean-tos, chimneys) that break the outline. Never several equals.
4. **Rhythm and bays.** A facade is a repeated bay (pier + opening) with a clear beat; break it only at the centre or
   the ends, on purpose. Real proportion: storey 4-5 blocks, openings ~1/2-2/3 of a bay, piers 1-2 blocks.
5. **Three scales of detail on every face the camera sees:** structure (bays, storeys, roofs), openings (windows,
   doors, arches with reveals and sills), ornament/texture (trim lines, brackets, railings, lights, plants).
6. **Depth, not colour.** A real wall has at least 3 planes: recess (windows, doors), wall, projection (piers,
   cornices, sills, eaves). Shadow is what makes Minecraft read as real - especially under shaders.
7. **Material by role, and contrast between wall and roof.** Walls one main material (+ a 10-20 % second tone, never a
   chequer); a pale stone base and trim; roofs a different family from walls (warm brick under cool slate or green
   copper). One accent colour at most.
8. **Water logic = weathering logic.** Roofs shed water to gutters and downpipes; stains, moss and darker tones gather
   at eaves, valleys, wall feet and under sills; ridges and high parts bleach. Wear sits where water and hands go.
9. **The roof is the fifth facade.** From the RTS camera the roof is 40-80 % of the building: courses, ridges,
   hips, valleys, dormers, chimneys, vents, roof lights - and it should say what the building is (ref60).
10. **Thresholds and ground.** Doors are framed by receding layers (ref67), lit at the sides, with a step, an apron
    and a change of paving. The ground round a building is designed (yards, paths, planting, props) - ref61.
11. **Life.** Light inside at dusk, smoke, plants growing on the building, props of the trade (crates, coal,
    tools), small moving parts. A building that works.

## 2. Measured (26 builds vs our r7)
| measure | good builds | Engine Shed r7 |
|---|---|---|
| eave overhang | 1 block everywhere (Victorian Factory 123 / 123 eave cells; every medieval house 1-2) | **none** |
| thin / detail blocks (walls, fences, bars, chains, trapdoors, glass, lights) | 9-20 % of blocks (houses), 14 % (factory), 5 % (big stone halls) | **0 %** |
| stairs / slabs | stairs 5-15 %, slabs 5-12 % | stairs 10 %, slabs 4 % |
| most-used (non-terrain) block | 10-25 % | **41 %** (red terracotta) |
| roof materials | 2-3 by role: e.g. factory 38 % pale trim / 32 % dark slate / 21 % green copper | one red family |
| facade depth (camera faces) | spread over 4-8 planes, none above ~25 % (factory west face) | 55 % on one plane |
| roof form | cross gables, dormers, hips meeting - many ridges | stepped cone + flat-ish shed |

Visual notes: the factory (63 x 58 x 84) = brick body with pale cut-stone base and string courses, green-copper
shingle roof with a row of dormer windows, a glazed barrel roof over the main hall, brick chimneys banded in pale
stone with dark caps, pilasters with a light on every pier. The 1890 station = pale stone piers framing tall windows
in brick infill, layered cornices, roofs stepping down with the wings, iron railings along the platforms. The medieval
houses (2012-13, still good) = cross-gabled roofs with 1-block eaves, half-timber posts at every corner, glass panes,
fence balustrades, a stone plinth.

## 3. Gates (a generator test checks these on the built structure, with tools/schem_study.py)
- G1 eaves: every camera-facing eave and verge oversails its wall by >= 1 block (`eave_overhang`).
- G2 detail density: thin / detail blocks >= 8 % of the building's blocks (glyphs `| = * g`).
- G3 no single block over 25 % of the building's (non-ground) blocks.
- G4 wall / roof contrast: roof cells' main family differs from the wall's main family.
- G5 depth: on the two camera faces no depth plane holds more than 45 % of the face cells, >= 3 planes over 10 %.
- G6 roof tiling: roof_kit courses (T0 55-72 %, no chequer, wet eaves) + ridge, verges and gutters.
- G7 base-body-cap: a distinct base course at y 1 and a cap line (cornice / eave) on every wall run.
