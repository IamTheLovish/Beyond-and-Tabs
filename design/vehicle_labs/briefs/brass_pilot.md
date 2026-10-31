# PILOT: Brassworks ENGINE SHED imitating lovish's ref18 (level 1 only)

Why a pilot: seven labs x two rounds stalled at 3-4/10 because they BLENDED many references into a generic box
(read /root/bat/design/vehicle_labs/LESSONS.md). This round IMITATES ONE PICTURE as closely as blocks allow, the way
tools/capitol_white_angel_v4.py imitated its reference (read its "THE PICTURE, STUDIED" docstring for the method).
lovish lifted the size limits: the building is sized to fit the picture.

THE PICTURE: /root/bat/design/refs/vehicle_labs/ref18_brick_hall_central_tower.png (open it with Read; it is small, so
also look at it upscaled: python3 -c "from PIL import Image;im=Image.open(P);im.resize((im.width*4,im.height*4),Image.NEAREST).save(OUT)").
What it shows: a very wide, symmetric, fairly low red-brick hall; a GROUND ARCADE of dark round-headed arches between
red-brick piers with WHITE STONE banding (imposts, string courses, cornice); a row of windows above the arcade; a red
roof carrying ROWS OF WHITE-FRAMED DORMERS; END PAVILIONS at both ends, taller, with steep pyramid roofs and white
bands; ONE CENTRAL TOWER ~3x the hall height rising in STEPPED STAGES to a tall pointed SPIRE, red with WHITE LINES up
its edges and stage ledges, a warm glowing doorway at its base. Red + white only; calm lawn in front. That tower IS the
composition; everything else is long, calm and repeated.

## THE PLAN (Opus builds this; the art director's coordinates)
Footprint x 0..16 (depth, west -> east), z 0..32 (length, north -> south), y 0 = ground layer. Box centre = spawn
(8, 16). The long arcaded FRONT faces WEST (x = 0) - the face the RTS camera (from the south-west) sees best; the south
end pavilion is the camera's second face.
- GROUND y 0: a lawn strip x 0..1 in front (grass, a few flower clumps, a paved path to the gate), stone plinth course
  (polished diorite / calcite = the white stone) under every wall.
- HALL BODY x 2..16, z 6..26. Front wall at x 2 with an ARCADE projecting to x 1: round-headed arches 3 wide, piers 1
  wide (red brick, white stone base and impost blocks), repeating every 4 along z, dark recess 2 deep behind each arch
  with a lit door or window in some bays. Arcade storey y 1..9 (white string course at y 9). Upper storey y 10..12: a
  pair of tall windows (white stone surrounds, sills, lit behind) above each arch; cornice at y 13 (white stone,
  stair-corbelled). Back (east) wall plain brick with bays and buttresses.
- HALL ROOF y 13..20: red pitched roof (red nether brick stairs/slabs for the tile tone, or another red that reads as
  tile against the brick), ridge running N-S at x 9; ROWS OF DORMERS on the west pitch, one over every arch, white
  quartz frames, small pitched caps, glass panes lit behind. Along the ridge an OPEN SMOKE VENT / glazed lantern slot
  (x 7..10) so the SPAWN COLUMN over (8, 16) is air from the floor to the sky; the bridge crane inside shows through it.
- END PAVILIONS z 0..5 and z 27..32, full depth x 2..16: walls to y 16, white quoins and storey bands, tall lit
  windows, steep PYRAMID roofs to ~y 26 with a finial (brass = waxed cut copper). The south pavilion's south face is the
  camera's right-hand face: give it the same order as the front.
- THE TOWER, centred on the front over the gate: base x 0..6, z 12..20 (9 wide), projecting from the arcade.
  Stage 1 y 1..13: the GREAT DOOR - lane opening 5 wide (z 14..18) x 8 tall (y 1..8) under a round arch with white
  voussoirs and keystone, a warm glow from inside (lit vault), flanking stepped buttress piers with white quoins; rails
  run out of it to the lawn. Stage 2 y 14..23 (set back to 7 wide): a brass (waxed cut copper) CLOCK FACE on the west
  side, lit windows, white bands. Stage 3 y 24..31 (5 wide): a belfry with lit openings, corner pinnacles at each set
  back. SPIRE y 32..42+: a steep pointed spire, red with WHITE EDGE RIBS (quartz stairs/walls on the four arrises), small
  lucarnes, a brass finial. Stepped edges between stages, exactly the picture's "white lines climbing the tower".
- THE WORKS (Brassworks identity, quiet, behind): a bridge crane on rails inside the hall at y 11 (seen through the vent
  slot), and ONE brick smokestack with corbel rings at the back (east) of the north pavilion, clearly lower than the
  tower. No other machinery on the front.
- LIGHT: the great door, the arcade doors, the dormers and the tower's openings - lit rooms behind glass. Nothing
  scattered on roofs.
- PALETTE: red brick (bricks), white stone (polished diorite, calcite, quartz for trim), red roof tile, dark window
  recesses (deepslate / black glass), brass only on the clock, finials and crane. Nothing else.

## SHORT RULES (only these)
- Exit lane 5 wide (z 14..18) x 8 tall (y 1..8) clear from the spawn (8, 16) straight out the west face.
- Spawn column over (8, 16) air from the lane floor to the top of the structure.
- Any other walkable gap >= 4 wide x 8 tall, or sealed. No pockets: python3 tools/audit_structure_pockets.py (check how
  it takes a path). Nothing floats or pops off. Vanilla 1.20.1 blocks; waxed copper only.
- Level 1 only (no L3 this round).

## FILES (repo /root/bat, branch claude/vehicle-labs - do NOT commit, push or switch branches)
- tools/lab_vehicle_brass.py: rewrite for this plan (you may reuse tools/brass_lab_parts.py or start fresh; put "THE
  PICTURE, STUDIED" and "ANCHORS: ..." at the top).
- design/vehicle_labs/brass_l1.nbt, and remove the old brass_l3.nbt (no longer matches).
- design/vehicle_labs/brass.png: tools/structure_render.py at --px 32.
- design/vehicle_labs/brass_vs_ref.png: the render and ref18 (upscaled to a similar height) side by side, so a critic
  can compare them directly.

## HOW TO WORK
Build, render at --px 32, put it beside ref18, and ask honestly: does it look like THAT picture? Fix the biggest
difference, repeat - at least 5 passes. Proportion first (hall length vs height, tower height vs hall, stage set-backs),
then rhythm (arch/dormer spacing), then trim. Report (under 200 words): files, size, block count, how close it is to
ref18 and where it still differs most.

## Changes sent to the builder during the run (apply all)
1. (04:13) Two levels on one reserved footprint: L3 = the full ref18 imitation; L1 = its first stage; every L1 cell
   stays non-air in L3.
2. (04:32) TIER 1 IS SMALL: reserved footprint x 0..12, z 0..20; spawn (6, 10); exit lane 5 wide (z 8..12) x 8 tall out
   the WEST face; spawn column open at both levels; L3 <= 22 tall (hall walls ~9-10, roof to ~14-15 with the dormer
   row, end pavilions a little taller with pyramid roofs, tower ~2x the hall to ~22); L1 <= 12 (arcade hall under a
   plain roof, low end wings, the tower's first stage over the great door); about 4-5 arches each side of the tower.
3. (04:36) GREYBOX FIRST: write brass_l3_greybox.nbt (massing only: one wall tone, one roof tone, black openings), run
   tools/ref_compare.py against ref18, match the squint shape, then STOP for lovish's OK before any detail.
