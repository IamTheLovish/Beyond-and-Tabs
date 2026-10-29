#!/usr/bin/env python3
"""ENGINE SHED T1 (Oct 28, session E): the Brassworks vehicle lab's TIER 1 model, 1 of 4 (one model per tier,
design/building_tiers.md). lovish kept round 3's massing - tower over the door, arcade halls, swept end wings - and
grew the plot to the T1 lab size, 17 x 25, h <= 26 (the pilot stalled on a 13 x 21 plot: a size problem, not an art
problem). greybox() is r3 rescaled; its front outline, point, symmetry and door are exact tables in
tools/test_lab_vehicle_brass.py. T1 is detailed and cohesive at its own size - not ref18's palace detail, which is
saved for T3 / T4. Keep the skeleton so T2-T4 can grow from it.

The history below is the 13 x 21 pilot (rounds 1-5), kept for its lessons; its detail code is at commit 4121f12.

PILOT (Oct 28): the Brassworks VEHICLE LAB as an ENGINE SHED imitating ONE picture, lovish's ref18
(design/refs/vehicle_labs/ref18_brick_hall_central_tower.png). Brief: design/vehicle_labs/briefs/brass_pilot.md with
its "Changes sent to the builder" (T1 footprint x 0..12, z 0..20, spawn (6, 10), exit lane z 8..12 x 8 tall out the
WEST face, L3 <= 22 tall, L1 <= 12). lovish approved the r3 GREYBOX (greybox() below, kept exactly); detail() paints it
region by region after the picture (lovish: "keep studying it and learning how to make it super detailed and
intricate"), and level1() takes L1 out of L3.

THE PICTURE, STUDIED (230 x 180 px, a frontal shader shot; measured on the silhouette, ground line at ~y 153)
  - THE WHOLE: very wide and low, symmetric about ONE central tower. Left to right: end pavilion | 4-5 arcade bays |
    tower | 4-5 arcade bays | end pavilion. Width : tower height ~ 230 : 139, so the building is ~1.7x wider than the
    tower is tall, and the hall is ~4x wider than it is tall.
  - THE HALL (x 35..90 and 140..195 in the picture): a ground arcade of dark round-headed arches (y 128..153, the
    darkest band of the picture - squinted it reads as a dark strip along the ground), a lit string course on top
    (y ~128), then a red roof (y 95..119) carrying a row of dormers. Hall to roof top ~58 px = 0.42 of the tower; the
    walls (ground to eaves) ~34 px = 0.25 of the tower; the roof is ~40% of the hall's height, a steep visible plane.
  - THE END PAVILIONS (x 0..35 and 195..230): a little taller than the hall - walls to ~y 100, steep pyramid roofs to
    y 66..77 (~0.6 of the tower) - flaring outward at the outer edges; they close the composition at both ends.
  - THE TOWER (x 88..142 at its foot, ~23% of the width): rises from the arcade in STEPPED STAGES, each set back -
    stage 1 to ~y 95 (just above the hall roof, the glowing great door at its base y 128..153), stage 2 to ~y 55
    (x 95..135), stage 3 to ~y 30 (x 103..128), then a short pointed spire to the tip at y 14. Tip ~139 px = 2.4x the
    hall's roof top. Squinted: one tall stepped triangle on a long low dark band, with two lesser humps at the ends.

THE PICTURE, REGION BY REGION (detail round 1: each region cropped from ref18 and blown up 4x / 8x NEAREST; 1 block of
the build ~ 11 px across, ~6.3 px up in the picture)
  - COLOUR, measured (10-colour quantize of the building): dark crimson-brown 33,12,13 and 71,47,47 dominate; mid red
    93,45,43 and bright tile red 132,40,39 on the roofs and the tower's heart; the "white" is a warm pale stone
    149,118,110 - NOT bright white - in thin lines; grey stone 45-80 in the arcade. So: few pale cells, never planes.
  - ARCADE (y 128..153 px = ~4 blocks): a near-black band. Grey-stone piers (~50,60,58) with a pale plinth, between them
    deep CRIMSON bays (35..80, 0..10, 10..30 - red glass glowing) under dark heads; ~12 bays each side in the picture,
    our 3 a side at 2-block pitch (pier, bay, pier). On top a grey string course carrying a DOTTED LINE OF SMALL WHITE
    LIGHTS (y ~131 px, one per bay) - the brightest marks on the whole front; then a dark band to the eaves.
  - HALL ROOF (y 95..125 px, ~4 courses): red tile rows striped by pale ribs that run down the slope (one per bay) -
    a grid of red panes in pale frames: the "dormer rows". Our hall roof is only 2 bays wide between tower and wing,
    so: a pale rib over each pier, a lit dormer over the bay, pale eave slabs.
  - END WINGS (x 0..45 and 185..230 px): steep tiers swept UP to the outer end, each tier a red band with a pale lip
    (the eave line of every step is the white line), pale ribs up the middle and outer edges, a cross-like pale
    pattern on the gable face. -> every slab/stair edge of the swept roof pale, the crest pale, a pale tier line on the
    gable courses 9 and 11, a white rib up the slope's middle (x 3), brass finial on the high end.
  - TOWER BASE / GREAT DOOR (x 100..130, y 110..153 px): flanking piers of dark stone with pale quoins, a round-headed
    opening; INSIDE, stacked red and pale bands (a vault seen in perspective) and ONE warm glow at y ~118 px - high in the
    door, not a lit floor. -> dark jambs, a white arch (stair haunches, chiseled keystone, glass tympanum lit), the far
    wall striped red / pale (red nether brick / granite) with the glow high (y 5..6) and a lit doorway at its foot.
  - TOWER MID STAGES (y 55..100 px): dark red-brown flanks (nether brick), a bright red heart down the centre, a round
    medallion (the clock) at y ~75 px with pale tracery, set-backs marked by thin pale ledges. -> flank = nether brick,
    heart = red nether brick, the brass clock 3 x 2 at stage 2, white ledges only on the front-most step of courses 9 /
    13 / 17.
  - SPIRE (y 14..55 px): a narrow triangle, red heart, dark flanks, the four ARRISES picked out in pale steps climbing to
    the tip; lit slots in the red. -> white only where a stair sits on the outline's corners (the arrises), a lit belfry
    slot (glass on ochre) at y 16..17, brass finial + rod at the tip.
  - LIGHT: only the arcade's dotted line, the great door's glow, two lit bays, the dormers and the belfry - nothing loose.

THE MASSING, round 3 (blocks; x = depth west -> east, z = length north -> south, y 0 = ground; front faces WEST).
  Slopes are tools/shape_kit.py shapes voxelized with its stair / slab edge pass, so flanks read as lines.
  ground      y 0, the whole 13 x 21 pad; lawn strip x 0 in front; dark setts in the lane.
  hall range  SHALLOW and LOW: x 1..7, z 0..20, walls y 1..5, a straight pitch from y 6 to its ridge at y 9 (x 4).
              Arcade: dark bays 1 wide x 4 tall at z 0, 2, 4, 6 and 14, 16, 18, 20.
  works shed  behind the hall: x 8..12, z 0..20, flat roof at y 4, a darker tone; one stack x 10..11, z 0..1 to y 10,
              hidden behind the north wing's high end from the front.
  end wings   x 1..6, z 0..3 and 17..20, walls y 1..6, under HALF-SWEPT roofs: the ridge on the outer end at y 13
              (cell 12), concave toward the hall - the picture's up-swept ends.
  tower       ONE steep pyramid: a base block x 0..6, z 6..14, y 1..5 (the great door through it), then the pyramid
              from y 6 (half-widths 3.5 in x, 4.5 in z: 9 wide along the front at the hall's roof line) with straight
              flanks to its apex at y 23 (top cell 21 - 22 tall). No ledges, no needle.

ANCHORS (structure-local block coords x, y, z)
  spawn cell      (6, 1, 10)       the hall floor at the inner end of the lane
  spawn column    (6, 1..21, 10)   air to the sky through the ridge vent at every level
  exit lane       x 0..6, z 8..12, y 1..8 - 5 wide x 8 tall, out of the west face under the tower
  great door      (0, 1..8, 8..12) the tower's door opening; its arch head at y 9 (detail stage)
  clock face      (1, 12..14, 10)  stage 2's west face, centred over the door (detail stage)
  spire tip       (3, 21, 10)      the finial
  pavilion tips   (PAV_D//2, top, 2) and (PAV_D//2, top, 18)

PASSES (the sheets: design/vehicle_labs/brass_greybox_vs_ref.png = round 1, corner view only;
        brass_greybox_vs_ref_r2.png = round 2, tools/ref_compare.py --front: the WEST ELEVATION beside ref18, its
        squint, then the corner view). Front profile = the tallest course seen from the west per z column:
  ref18 (tip 22) 13.5 12.6 12.1 10.8  9.2  9.2  9.2  9.7 14.3 18.8 21.9 18.2 12.2  9.2  9.2  9.2  9.2 10.9 12.3 12.8 13.7
  round 1 (corner-view squint only; a 12-deep hall with a 4-high roof, a 6-deep tower)
  1-5  walls 8 -> 6, roof 12 -> 10, pavilions 6 -> 4 wide, stage ledges; profile within ~1 block, but the corner view
       read as a chunky block: the deep hall roof outweighed the tower (coordinator's read).
  round 2 (front elevation first, then squint, then the corner view)
  1-4  hall 7 deep, works shed behind, pavilions with eave kicks, stages 11 / 14 / 17 + needle:
       11 13 13 11 9 9 9 11 14 18 21 18 14 11 9 9 9 11 13 13 11 - the outline within ~1.5 blocks, but the tower read
       as a wedding cake of set-backs, not the picture's one triangle (coordinator's read).
  round 3 (tools/shape_kit.py; sheet brass_greybox_vs_ref_r3.png)
  1  a shape_kit pyramid from y 6, a pitched hall roof, swept wing roofs with a kick: the shapes overwrote each other
     (a slab from one cut the next one's solid roof: light holes in the hall roof); the kick read as a notch.
  2  shapes MERGED (a full cell stays full), no kick (the concave fall alone reads as up-swept), apex y 23:
     11 11 11 8 8 8 8 11 15 19 21 19 15 11 8 8 8 8 8 9 11 - one triangle; the stack poked over the north wing.
  3  wing ridge to 13 (cell 12), the stack hidden behind it, the lane's far wall in the wall tone:
     12 10 8 8 8 8 8 11 15 19 21 19 15 11 8 8 8 8 8 10 12 - the tower within 1 block of ref18 in every column;
     the hall 1 lower than the picture (as asked); the wings' fall 1-2 lower at z 2..3 / 17..18.

DETAIL PASSES (sheet design/vehicle_labs/brass_detail_vs_ref_r1.png: the west elevation beside ref18 stretched to the
same 21-column grid, both squints, the corner view, and seven region crops side by side on that grid)
  1  quartz on every edge + ledges: the whole front read WHITE - the picture's pale is thin lines, not planes.
  2  grey stone flanks / piers: the tower turned into a grey castle; the picture's flanks are dark red-brown.
  3  measured palette: nether-brick flanks, red heart, sea-lantern dots; still a white staircase on every step.
  4  white only on arrises (|rx - rz| test) + ledges; but the pyramid's every stair is an arris at this size.
  5  arcade darkened (deepslate piers, crimson bays, two lit), stone string course; outline rim white -> still heavy.
  6  white only on the outline's CORNER cells (the four arrises) and the front step of the ledges: the triangle now
     reads as dark red with white lines climbing it, like the picture; the door glow cut to one high light.
  7  the door's far wall in stacked red / pale bands (the picture's vault), wing tiers striped with a white middle rib,
     the gable lancets dropped (they read as two orange blobs); finials 1 lower (outline within 1 block of r3).
  L1 (2 passes): the tower's first stage capped by a white-rimmed ledge; the far wall kept so the door is not a hole.
DETAIL ROUND 2 (critic on r1: resemblance 4/10, quality 5/10, silhouette WORSE than r3; sheet
design/vehicle_labs/brass_detail_vs_ref_r2.png, lit). The tower is now built as explicit stages (tower(): base block,
arch frontispiece, stages 7 / 5 / 3 wide set back one block each, a brick point), NOT painted onto the pyramid's stair
edges - that is what made r1's flares and lantern box.
  1  outline: per-column tops equal to r3 (13 11 9 9 9 9 9 12 16 20 22 20 16 12 9 9 9 9 9 11 13); no stair flares, no
     pole; the wing finials gone. Palette cut to red nether brick / polished blackstone / quartz; read the tower as a
     cream column with a snowman face (cream on every stage corner + a ring round the clock).
  2  cream as one-course lines only: dashed arris quoins, front lips; the clock's ring dropped; the light dots recessed.
  3  stage faces red with dark flanks (was dark with a red stripe); the hall frieze red; the door's far wall deep red
     banded dark. Front balance 42 / 37 / 18 (red / black / cream).
  4  stage lips dashed (cream at the arrises and centre only); the works stack's top hidden behind the wing crest.
     Balance 44 / 37 / 16 + 3% light, outline still equal to r3 column for column.
DETAIL ROUND 3 (critic on r2: 4 / 4 - the tower still read as stacked stages; sheet brass_detail_vs_ref_r3.png, lit).
Fixed BY NUMBERS first. The spire is ONE line, HW_TABLE (row -> the line's half-width at each half row and the outline
that quantizes it), half-width 3.5 at the hall roof (the r3 base, 7 wide) to 0 at y 22; a stair (tall half in) wherever
the outline steps in half a block. Colour measured on the RENDERED PIXELS (scratch tool: near-black = luma < 62 and
chroma < 36, red = red-dominant above it): ref18's building reads 42 / 38 / 2 (red / near-black / pale).
  1  spire from shape_kit (a sampled pyramid): rows 9-13 came out one width - a box shoulder; max deviation 1.0 block.
  2  spire from the table, quantized per half row (best monotone outline); cream single cells on the front arrises.
     Blackstone rendered GREY, not black (pixels: 39 red / 37 black / 14 other) -> the darks are nether brick
     (65,37,40) and deepslate tiles.
  3  pilasters polished granite (warm, paler than the bays), the bays red glass over red brick under a tinted-glass
     head, end rods hung under the eave every 2 blocks; elevation 53 / 38 / 8, corner 59 / 27 / 6.
  4  wing roofs gabled: each course steps in 1 toward a 1-wide ridge with a brick finial, the outer end's eave an
     overhanging cream stair.
  5  cream moved to mid-run cells (no step "ears"), then back to the step stairs (single wedges ON the line); a pass
     that let a full row follow a stair at the same width made notches - the outline must never widen going up.
  Spire vs ref18's line: max 1.17 blocks at the foot (r3's 7-wide base against ref18's 5), <= 0.6 from y 11 up,
  <= 0.5 against its own line everywhere.
DETAIL ROUND 4 (critic on r3: 4 / 4, one dark-red lump; three critics had asked for three colour schemes, so the art
director read a FIXED material map off ref18 itself: a GRID - grey / dark-grey frames 1 cell wide, bright red panels
1-2 cells wide set into them, pale stone only as ledge lines, tiny white lights; sheet brass_detail_vs_ref_r4.png).
Blocks picked by rendered-pixel distance to ref18's red-class mean (104, 49, 46): red terracotta d 5, mangrove planks
d 7 (its stairs / slabs are the red shapes), red nether brick d 54 (dropped). Dark = polished blackstone bricks (84 %
near-black pixels; deepslate tiles only 51 %). Shares = rendered pixels of the lit west elevation, ref18 red / grey /
dark / pale 49 / 18 / 31 / 2.
  1  every region re-skinned in grey lattice: 12 / 43 / 35 / 10 - a grey castle; the spire solid grey (each stair
     row is the outline, so "grey on stair rows" painted it all).
  2  stair rows red, arrises grey, flank mullions every 3rd cell, roof ribs every 3rd column: 24 / 28 / 35 / 13.
  3  arcade as dark piers / red panels / pale light ledge / candles: 29 / 32 / 31 / 8; grey still 14 over (deepslate
     tiles render mid grey).
  4  dark = blackstone bricks, wing ribs only as bands: 32 / 23 / 37 / 8.
  5-8  the r3 cream cornice turned into the ONE pale ledge carrying the lights; the hall eave a red stair course; the
     door's far wall a red vault band over dark: 38 / 20 / 35 / 7.
  9-10 roof ridge course grey, rib eaves grey, the ornament a pale diamond round a red heart, arrises dashed:
     39 / 20 / 35 / 7 (corner view 38 / 25 / 27 / 9). Outline: every column within 1 of the r3 greybox; the 1-wide
     point is 2 rows (pale + a brass slab), the width table forbids 1 wide below y 20.
DETAIL ROUND 5 (critic on r4: 4 / 5 - the spire came back as 7 / 5 / 3 boxes, the second round running: r4's re-quantized
table made rows 15-19 one 3-wide box, and "within 1 block of r3" let it pass). So the wins are now HARD CHECKS run on
every build (assert): check_tower() - each row's front outline (lower / upper half-width, from the blocks' real shape
boxes) equals R3_TABLE, round 3's table, frozen, and the front face sits where the table puts it, 1 wide on <= 2
rows; check_banned() - no cobblestone / mossy cobblestone; check_greybox() - --greybox (and every build, in a temp
file) reproduces git HEAD's brass_l3_greybox.nbt cell for cell, 22 tall (it had regenerated 21 tall: greybox() read the
global T_APEX that round 3 re-bound to 22.0 for the spire; now GB_APEX = 23.0). Shares measured on main's lit renderer.
  1  r3's spire back (rows 9-19, point at 19-20), grey = polished andesite (smoothest grey), red bands over the arcade,
     portal + lanterns + dormers: 36 / 14 / 38 / 13 (pale and dark over).
  2  dark arrises dashed, pale rake on the wing gables, pale hall-roof ribs: 37 / 16 / 36 / 11.
  3-4  the narrow spire rows red; roof ribs grey (pale ribs read as planes), door vault red: 40 / 17 / 32 / 11.
  5  the wings' pale rake beaded pale / red (solid, it read as a white column from the front): 41 / 17 / 32 / 10.
  6  a grey frame on the 3-wide spire rows turned the spire 52 % grey - reverted.
  7-8  arcade panels 3 tall, a dark belfry slot in the upper spire: 42 / 15 / 33 / 10 (corner 37 / 27 / 27 / 10).

Usage: python3 tools/lab_vehicle_brass.py   (writes the T1 greybox, design/vehicle_labs/brass_t1_greybox.nbt, and checks)
Tests: python3 -m unittest discover -s tools -p 'test_lab_vehicle_brass.py'
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np                    # noqa: E402

import gen_structures as gs           # noqa: E402
import roof_kit as R                  # noqa: E402
import shape_kit as sk                # noqa: E402

SX, SZ, H_MAX = 17, 25, 26          # the T1 lab plot (design/building_tiers.md): x = depth, z = the front's width
SPAWN = (8, 1, 12)
LANE_X, LANE_Z, LANE_H = range(0, 9), range(10, 15), 8      # the great door: 5 wide, 8 tall, through the tower
# (r4, Oct 28: 5 x 8, the lane every other lab uses - T1 Brassworks vehicles fit it. The plot rescale's 7 x 10 took 7
# of the cone's 9 foot columns, so the door was the tower's whole foot; ref18's door is ~55 % of the foot's width and
# ~31 % of the tower's height, 5 / 9 and 8 / 25)
OUT = "design/vehicle_labs/brass_t1_greybox.nbt"

WALL, ROOF, HOLE, GROUND, LAWN = "terracotta", "red_nether_bricks", "obsidian", "stone_bricks", "grass_block"
SHED = "deepslate_bricks"     # the works behind the hall (a second, quieter tone so the front range reads on its own)
# shape_kit's roles: a full cell takes the role, a stair cell role + "_st", a slab cell role + "_slab"
PAL = {"roof": ROOF, "roof_st": "red_nether_brick_stairs", "roof_slab": "red_nether_brick_slab"}

# the massing: round 3's (tower over the door, arcade halls, swept end wings), rescaled to the T1 plot so the front
# reads wing 3 / hall 5 / tower 9 / hall 5 / wing 3 columns (r3: 2 / 5 / 7 / 5 / 2 of 21). Each value's r3 one after #.
HALL_X = (1, 9)       # (1, 7) the hall range: a long low band along the front, the works shed behind it
HALL_TOP = 6          # 5    hall walls y 1..6; a straight pitch over x 0.5..9.5 from y 7 to its ridge at y 10 (cell 9)
ARCH_H = 5            # 4    arcade openings y 1..5
ARCH_Z = (0, 2, 4, 6, 18, 20, 22, 24)   # (0, 2, 4, 6, 14, 16, 18, 20): 4 bays each side of the tower, under the wings
WING_X, WING_Z = (1, 8), ((0, 4), (20, 24))     # (1, 6), ((0, 3), (17, 20)): the end wings' walls, y 1..WING_TOP
WING_TOP = 7          # 6
# the wings' roofs: half a swept gable each, its ridge on the outer end (z -0.5 / 24.5), falling concave toward the
# hall (steep at the outer end, flattening inward) - the picture's up-swept ends. Front tops 14 / 12 / 10.
WING_ROOF = dict(h=7.5, y0=8.0, half=5.5, sweep=1.8, kick=0.0)  # dict(h=6.8, y0=7.0, half=4.5, ...)
# the tower: ONE straight cone (a square pyramid) standing on the hall roof over a base block, centred on (TX, TZ)
TX, TZ, THX, THZ = 4.0, 12.0, 5.0, 5.0           # 3, 10, 3.5, 4.5: cone cells x 0..8, z 8..16 at the eave
GB_FOOT, GB_APEX = 7.5, 27.0                     # 6, 23: from the hall's eave to a point at y 25; the apex sits one
                                                 # block over the plot's top, as r3's did (a point exactly at the top is
                                                 # zero-wide there and voxelizes one row short)
# Re-mass r3 (lovish chose option b, Oct 28): the T1 tower was a pyramid 11 wide at y 7 (half-width 5.5 at y 7.0)
# over a base block, so its foot rose vertically over the hall roof and its columns stepped 4 3 4 2 - squinted, a
# block under a spire. Half-width 5.0 at y 7.5 to the apex at 27 voxelizes to tops 11 15 19 23 25 (4 rows a column)
# with the x and z steps in phase - each 4-row tier one face plane (x 0 / 1 / 2 / 3 / 4) - a straight cone whose foot
# (z 8..16) stands on the hall roof; the hall roofs (z 3..7, 17..21) run on into its flanks.
BASE_Z = (7, 17)                                 # the base block under the eave: the door's piers and buttresses
SHED_X, SHED_TOP = (11, 16), 5                   # (8, 12), 4: the works shed, flat roofed
GABLE_Z, SHED_SLOTS = (2, 22), (12, 15)          # r6: its shallow gable's run, and the south wall's two lit slots
STACK = (13, 14, 0, 1, 12)                       # (10, 11, 0, 1, 10): one smokestack behind the north wing's high end,
                                                 # its top = the wing's second column (12), so the front never sees it


class G:
    def __init__(self):
        self.b = {}

    def set(self, x, y, z, n, **props):
        if 0 <= x < SX and 0 <= z < SZ and 0 <= y < H_MAX:
            self.b[(x, y, z)] = (n, props)

    def fill(self, x0, x1, y0, y1, z0, z1, n):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.set(x, y, z, n)

    def clear(self, x, y, z):
        self.b.pop((x, y, z), None)


def pyramid(cx, cz, hx, hz, y0, y1):
    """A rectangular pyramid: half-widths hx, hz at y0, straight flanks to the apex at y1."""
    def f(x, y, z):
        t = np.clip((y - y0) / (y1 - y0), 0, 1)
        rho = np.maximum(np.abs(x - cx) * hz / hx, np.abs(z - cz))
        return np.maximum(rho - hz * (1 - t), np.maximum(y0 - y, y - y1))
    return sk.Shape(f, (cx - hx, cx + hx, y0, y1, cz - hz, cz + hz))


def pitched(x0, x1, z0, z1, y0, y1):
    """A straight-pitched gable roof over x0..x1 (continuous), ridge along z at y1, eaves at y0."""
    cx, hw = (x0 + x1) / 2, (x1 - x0) / 2

    def f(x, y, z):
        top = y0 + (y1 - y0) * (1 - np.abs(x - cx) / hw)
        return np.maximum(np.maximum(y - top, y0 - y), np.maximum(np.abs(x - cx) - hw,
                                                                np.maximum(z0 - z, z - z1)))
    return sk.Shape(f, (x0, x1, y0, y1, z0, z1))


def swept_half(x0, x1, z_ridge, toward, h, y0, half, sweep, kick):
    """Half a swept gable: ridge along x at z_ridge (height y0 + h), the slope running `toward` (+1 / -1 in z) for
    `half` blocks, concave, its last quarter kicking up by `kick`."""
    def f(x, y, z):
        s = np.clip((z - z_ridge) * toward / half, 0, None)
        lift = kick * np.clip((s - 0.75) / 0.25, 0, 1) ** 2
        top = y0 + h * np.clip(1 - s, 0, 1) ** sweep + lift
        return np.maximum(np.maximum(y - top, y0 - y), np.maximum(np.maximum(x0 - x, x - x1), s - 1))
    zz = sorted((z_ridge, z_ridge + toward * half))
    return sk.Shape(f, (x0, x1, y0, y0 + h, zz[0], zz[1]))


def add(g, shape):
    """shape_kit's cells merged into g in the roof tone: a cell already full stays full (a slab or stair from one
    shape never cuts into another's solid roof)."""
    tmp = G()
    sk.build(shape, "roof", tmp, pal=PAL)
    for q, blk in tmp.b.items():
        old = g.b.get(q)
        if old is None or (old[0] in PAL.values() and old[0] != ROOF) or blk[0] == ROOF:
            g.b[q] = blk


def greybox():
    g = G()
    for x in range(SX):
        for z in range(SZ):
            g.set(x, 0, z, LAWN if x == 0 else GROUND)
    # the works shed behind (flat, low) and its stack
    g.fill(SHED_X[0], SHED_X[1], 1, SHED_TOP, 0, SZ - 1, SHED)
    x0, x1, z0, z1, top = STACK
    g.fill(x0, x1, SHED_TOP + 1, top, z0, z1, SHED)
    # the hall range: low walls under a shallow straight pitch
    g.fill(HALL_X[0], HALL_X[1], 1, HALL_TOP, 0, SZ - 1, WALL)
    add(g, pitched(HALL_X[0] - 0.5, HALL_X[1] + 0.5, -0.5, SZ - 0.5, HALL_TOP + 1, HALL_TOP + 4))
    # the end wings: a little over the hall, under half-swept roofs rising to their outer ends
    for (z0, z1), (ridge, toward) in zip(WING_Z, ((-0.5, 1), (SZ - 0.5, -1))):
        g.fill(WING_X[0], WING_X[1], 1, WING_TOP, z0, z1, WALL)
        add(g, swept_half(WING_X[0] - 1.5, WING_X[1] + 0.5, ridge, toward, **WING_ROOF))
        for z in range(z0 + 1, z1):
            g.set(WING_X[0], WING_TOP - 1, z, HOLE)
    for x in range(2, HALL_X[1] + 1, 2):                # the camera's south end: ground bays
        g.fill(x, x, 1, ARCH_H, SZ - 1, SZ - 1, HOLE)
    for z in ARCH_Z:                                    # the arcade: a dark ground band
        g.fill(HALL_X[0], HALL_X[0], 1, ARCH_H, z, z, HOLE)
    # the tower: a base block carrying ONE steep pyramid, flanks straight from the roof line to the apex
    g.fill(0, int(2 * TX), 1, HALL_TOP, BASE_Z[0], BASE_Z[1], WALL)
    g.fill(0, int(2 * TX), HALL_TOP + 1, HALL_TOP + 1, int(TZ - THZ + 1), int(TZ + THZ - 1), WALL)   # the cone's foot
    add(g, pyramid(TX, TZ, THX, THZ, GB_FOOT, GB_APEX))                                          # course, y 7
    # the great door: the exit lane through the tower's base, backed by the hall's dark back wall
    for x in LANE_X:
        for z in LANE_Z:
            for y in range(1, LANE_H + 1):
                g.clear(x, y, z)
            g.set(x, 0, z, "deepslate_tiles")
    for z in LANE_Z:     # the passage's far wall: wall tone, so the door reads as a deep opening, not a black slab
        g.fill(HALL_X[1], HALL_X[1], 1, LANE_H, z, z, WALL)
    # the spawn column: air to the sky (a vent shaft up the tower's back flank)
    for y in range(1, H_MAX):
        g.clear(SPAWN[0], y, SPAWN[2])
    return g


# ============================================================================================ the detail
# Rounds 1-5 detailed the 13 x 21 plot (code at 4121f12, scores in design/vehicle_labs/SCORES.md). That plot is
# gone (T1 lab plot 17 x 25, design/building_tiers.md): the detail rounds rebuild on the T1 greybox above, against
# the fixed per-region checklist design/vehicle_labs/briefs/brass_t1_checklist.md (regions R1-R9; facade_audit over
# design/vehicle_labs/brass_t1_regions.json measures its rules), not against ref18's palace detail.
# Detail round 1 (Oct 28): every cell repainted from the checklist's palette roles; relief is subtractive (recessed
# bays, panels, an emblem, a lit slot) or a shape change of a greybox cell, so the front outline never moves.
BANNED = ("cobblestone",)      # cobblestone / mossy cobblestone and their shapes read as rubble at 32 px (r4 critic)
OUT_T1 = "design/vehicle_labs/brass_t1.nbt"
# a family: (full, stairs, slab, wall) - a greybox cell repainted keeps its shape and state
FAM = {
    "red": ("red_terracotta", "mangrove_stairs", "mangrove_slab", None),       # RED: smooth panels, courses
    "mangrove": ("mangrove_planks", "mangrove_stairs", "mangrove_slab", None),
    "granite": ("polished_granite", "polished_granite_stairs", "polished_granite_slab", None),   # lighter red lips
    "nether": ("red_nether_bricks", "red_nether_brick_stairs", "red_nether_brick_slab", "red_nether_brick_wall"),
    "dark": ("polished_blackstone_bricks", "polished_blackstone_brick_stairs", "polished_blackstone_brick_slab",
             "polished_blackstone_brick_wall"),                                  # DARK: frames, arrises
    "flat": ("polished_blackstone", "polished_blackstone_stairs", "polished_blackstone_slab", "polished_blackstone_wall"),
    "rough": ("blackstone", "blackstone_stairs", "blackstone_slab", "blackstone_wall"),
    "andesite": ("polished_andesite", "polished_andesite_stairs", "polished_andesite_slab", None),  # GREY: trim, caps
    "deep": ("polished_deepslate", "polished_deepslate_stairs", "polished_deepslate_slab", "polished_deepslate_wall"),
    "tiles": ("deepslate_tiles", "deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tile_wall"),
    "works": ("deepslate_bricks", "deepslate_brick_stairs", "deepslate_brick_slab", "deepslate_brick_wall"),  # R9
    "quartz": ("quartz_bricks", "quartz_stairs", "quartz_slab", None),            # PALE: lines and single cells
    "diorite": ("polished_diorite", "polished_diorite_stairs", "polished_diorite_slab", None),
    "brass": ("waxed_cut_copper", "waxed_cut_copper_stairs", "waxed_cut_copper_slab", None),   # BRASS <= 3 %
}
LIGHT, LAMP = "white_candle", "lantern"
# r8 (lovish, Oct 28 22:59): red BRICK walls on a pale stone plinth and string course, grey SLATE roofs (roof_kit) on
# the halls / wings / shed, weathered green COPPER (roof_kit) on the cone with dark ribs and ledges
FAM["brick"] = ("bricks", "brick_stairs", "brick_slab", "brick_wall")
FAM["stone"] = ("smooth_stone", "smooth_stone_slab", "smooth_stone_slab", None)
BRICK, PLINTH, STRING = "bricks", "polished_andesite", "smooth_stone"
BAYS = (2, 4, 6, 18, 20, 22)            # the arcade's bays in the x 1 wall plane (r7's z 0 / 24 are the verges' ends)
PIPE = "lightning_rod"                  # a copper downpipe (vanilla 1.20.1's lightning rod does not oxidize)
# THE ALLOWLIST: the palette roles and their shapes, roof_kit's slate and copper, the lights, the lawn in front
ALLOWED = ({n for f in FAM.values() for n in f if n and not n.startswith("mangrove")} | {
    n for m in (R.SLATE, R.COPPER) for k in ("tones", "ridge", "verge") for f in ((m[k],) if k != "tones" else m[k])
    for n in f} | {R.SLATE["gutter"], R.COPPER["gutter"]} | {
    "chiseled_polished_blackstone", "cracked_polished_blackstone_bricks", "tuff", "stone_bricks",
    "smooth_stone_slab", "calcite", "quartz_block", "bone_block", LIGHT, LAMP, LAWN, "deepslate_tiles", "glass_pane",
    PIPE, "blackstone", "stone_brick_stairs"})
ROOF_NAMES = set(PAL.values())


def shape_of(name):
    return 1 if name.endswith("_stairs") else 2 if name.endswith("_slab") else 3 if name.endswith("_wall") else 0


def paint(g, q, fam):
    """Recolour a cell in a family, keeping its shape and its stair / slab state."""
    if q in g.b:
        n, p = g.b[q]
        g.b[q] = (FAM[fam][shape_of(n)], p)


def put(g, q, name, **props):
    g.set(*q, name, **props)


def stair(g, q, fam, facing, half="bottom"):
    put(g, q, FAM[fam][1], facing=facing, half=half, shape="straight", waterlogged="false")


def stamp(g, part, at, rot):
    """A brass_lab_parts part (front facing south, rot quarter turns clockwise) into g at `at`; its air cells carve."""
    for (x, y, z), (n, p) in part.rotated(rot).b.items():
        q = (x + at[0], y + at[1], z + at[2])
        if n == "air":
            g.clear(*q)
        else:
            g.b[q] = (n, p)


def candle(g, q):
    put(g, q, LIGHT, candles="1", lit="true", waterlogged="false")


def side_maps(s):
    """s 0 = the north half (as written), 1 = its mirror about the tower's axis (z -> 24 - z)."""
    flip = {"north": "south", "south": "north"}
    return (lambda z: z if not s else SZ - 1 - z), (lambda f: flip.get(f, f) if s else f)


def base_paint(g):
    """The greybox's stand-in blocks to the palette: walls bricks (r8; r1-r7 red terracotta), holes the darkest
    recess brick, the works deepslate bricks, roof cells slate tiles until a roof is laid over them, the foundation
    tuff (R1)."""
    for q, (n, p) in list(g.b.items()):
        if n == WALL:
            g.b[q] = (BRICK, {})
        elif n == HOLE:
            g.b[q] = ("cracked_polished_blackstone_bricks", {})
        elif n in ROOF_NAMES:
            paint(g, q, "tiles")
        elif n == GROUND:
            g.b[q] = ("tuff", {})


def arcade(g):
    """R1 + R2 + R3, r8: the west wall stands in the x 1 plane under the eave (x 0 is the eave's shadow, only the
    downpipes stand in it - grammar C3 / recipes R1). Base - body - cap (architecture_rules G7): a polished andesite
    plinth course at y 1 the whole run (piers and bays), brick piers on the odd z, a smooth stone string course at
    y 5 (the impost the bay heads spring from), a darker top course at y 6 (red terracotta, the wet line) under the
    copper gutter. Each bay (BAYS) a
    window in a reveal: open at x 1 y 2..3 under a dark upside-down stair head, an andesite sill at x 2 with the bay's
    one candle on it, a glass pane over it, a dark interior behind (x 3) - lit windows at dusk instead of r7's candles
    on the cornice."""
    for z in range(SZ):
        if z not in LANE_Z:
            put(g, (0, 0, z), "tuff")
    for z in [z for z in range(1, SZ - 1) if z < 7 or z > 17]:
        put(g, (1, 1, z), PLINTH)
        if z in BAYS:
            for y in (2, 3):
                g.clear(1, y, z)
            stair(g, (1, 4, z), "dark", "east", "top")
            put(g, (2, 1, z), PLINTH)
            candle(g, (2, 2, z))
            put(g, (2, 3, z), "glass_pane")
            put(g, (2, 4, z), "cracked_polished_blackstone_bricks")
            for y in (2, 3):
                put(g, (3, y, z), "cracked_polished_blackstone_bricks")
        else:
            for y in (2, 3, 4):
                put(g, (1, y, z), BRICK)
        put(g, (1, 5, z), STRING)
        put(g, (1, 6, z), "red_terracotta")    # the wall's top course the darker tone: the wet line under the eave
        #                                        lip (recipes R15) - red terracotta only here and in the bay panels


def gables(g):
    """R5's gable ends, r8: the wing and hall walls stop at z 1 / 23 so the roof's verge oversails them (z 0 / 24, the
    RTS camera's south end). The face: a polished andesite plinth at y 1, brick piers on the odd x and bays recessed
    one (x 2 4 6 8: a window under a dark head, a red panel and calcite eye behind - ref18's panel), the smooth stone
    string course at y 5, brick above to the roof, the pavilion emblem in the gable (y 6..10) under the slate."""
    import brass_lab_parts as B
    for s in (0, 1):
        Z, F = side_maps(s)
        zf, zi = Z(1), Z(2)
        for x in range(1, HALL_X[1] + 1):
            put(g, (x, 1, zf), PLINTH)
            if x % 2 or x == 2:                         # x 2: the corner, the arcade's end bay is behind it
                for y in (2, 3, 4):
                    put(g, (x, y, zf), BRICK)
            else:
                for y in (2, 3):
                    g.clear(x, y, zf)
                stair(g, (x, 4, zf), "dark", F("south"), "top")
                for y, n in ((1, PLINTH), (2, "red_terracotta"), (3, "calcite")):
                    put(g, (x, y, zi), n)
            put(g, (x, 5, zf), STRING)
            if (x, 6, zf) in g.b and x > 1:            # x 1: the west wall's wet top course turns the corner
                put(g, (x, 6, zf), BRICK)
        stamp(g, B.emblem(recess=False), (4, 8, zf), 2 if s == 0 else 0)


HALL_H = (7.5, 8, 8.5, 9, 9.5, 10, 9.5, 9, 8.5, 8)   # x 0..9: the hall's slate pitch (26.5 degrees, slab pairs), the
#   eave at x 0 oversailing the x 1 wall, the ridge cap at x 5 (y 9, FRONT_T1's 9); the back eave on the x 9 wall


def wing_h(x, z):
    """The wing's half-hip (z 0 = the outer gable): 1.5 up per block across x from the x 0 eave to the apex at x 4,
    2 up per block from the hall toward the gable - tops 15 / 13 / 11 at z 0 / 1 / 2 (FRONT_T1's 14 / 12 / 10)."""
    return 8 if x == 9 else min(9 + 1.5 * min(x, 8 - x), 15 - 2 * z)


def slate_roofs(g):
    """R4 + R5, r8 (lovish 22:59: grey slate on the halls and wings): roof_kit.field over the halls (z 3..7,
    17..21) and the wings' half-hips (z 0..2, 22..24), each roof graded on its own (T0 deepslate tiles 55-72 %, wet
    eaves, bleached ridges), the hall ridge capped in blackstone bricks, the wing gable's verge dark oak (z 0 / 24)
    round a blackstone-brick apex. The eaves oversail the walls by one (x 0 west, z 0 / 24 the ends): waxed copper
    gutters under them, copper downpipes at z 1 / 5 (19 / 23) to the plinth. r1-r7 laid red stair terraces flush on
    the wall tops - no overhang, no gutter, one red lump with the walls (grammar (a) 1-2, ref45)."""
    for q in [q for q in g.b if q[0] <= HALL_X[1] and (q[2] <= 7 or q[2] >= SZ - 8) and (
            q[1] >= HALL_TOP + 1 or (q[2] in (0, SZ - 1) and q[1] >= 1 and q[0] >= 1))]:
        del g.b[q]
    H, regions, fixed = {}, [], {}
    for s in (0, 1):
        Z, _ = side_maps(s)
        hall = {(x, Z(z)) for x in range(HALL_X[1] + 1) for z in range(3, 8)}
        wing = {(x, Z(z)) for x in range(HALL_X[1] + 1) for z in range(3)}
        H.update({c: HALL_H[c[0]] for c in hall})
        H.update({(x, Z(z)): wing_h(x, z) for x in range(HALL_X[1] + 1) for z in range(3)})
        regions += [hall, wing]
        fixed.update({(x, Z(0)): R.SLATE["verge"] for x in range(HALL_X[1] + 1) if x != 4})
    cells = R.field(H, seed=8, mats=R.SLATE, fixed=fixed, regions=regions, along=lambda c: (c[0], c[1]))
    for (x, y, z), (n, p) in list(cells.items()):   # under the dark bargeboard a PALE stone bracket line, not a wall
        if (x, z) in fixed and n == R.SLATE["verge"][0]:     # (recipes R2; pass 2: the studied factory's pale trim line
            cells[(x, y, z)] = ("stone_brick_stairs", dict(  # is what makes its slate read against the brick)
                facing="south" if z == 0 else "north", half="top", shape="straight", waterlogged="false"))
    for q, (n, p) in cells.items():
        put(g, q, n, **p)
    for (x, z) in H:                     # the roof's body: brick from the wall tops up to the slate, inside the walls
        if 1 <= x <= HALL_X[1] and 0 < z < SZ - 1:
            low = min(y for (xx, y, zz) in cells if (xx, zz) == (x, z))
            for y in range(HALL_TOP, low):
                if (x, y, z) not in g.b:
                    put(g, (x, y, z), BRICK)
    gut = dict(facing="east", half="top", shape="straight", waterlogged="false")
    for s in (0, 1):
        Z, _ = side_maps(s)
        for z in range(3, 7):
            put(g, (0, HALL_TOP, Z(z)), R.SLATE["gutter"], **gut)
        for z in range(3):
            put(g, (0, WING_TOP, Z(z)), R.SLATE["gutter"], **gut)
        for z, top in ((1, WING_TOP - 1), (5, HALL_TOP - 1)):
            for y in range(1, top + 1):
                put(g, (0, y, Z(z)), PIPE, facing="up", powered="false", waterlogged="false")


def cone_rows(g):
    """The tower cone's cells per row (y >= HALL_TOP + 1, z 8..16, x <= 8) and, per row, its ARRIS cells: the corners
    where a face meets a flank - a cell at its row's z extreme and its column's x extreme. On the stepped cone this is
    a 1-cell staircase line from the eave to the point (a first pass also darkened each tier's inner step corner: the
    line read 2 cells thick and the 3- and 5-wide tiers turned mostly dark)."""
    zl, zh = int(TZ - THZ + 1), int(TZ + THZ - 1)
    rows = {}
    for (x, y, z) in g.b:
        if y > HALL_TOP and zl <= z <= zh and x <= 2 * TX:
            rows.setdefault(y, set()).add((x, z))
    arris = {}
    for y, cells in rows.items():
        za, zb = min(z for _, z in cells), max(z for _, z in cells)
        a = set()
        for x, z in cells:
            col = [xx for xx, zz in cells if zz == z]
            if z in (za, zb) and x in (min(col), max(col)):
                a.add((x, z))
        arris[y] = a
    return rows, arris


TIER_TOPS = (11, 15, 19, 23)            # the cone's tier changes: each tier's top row, where the next steps in


def tower(g):
    """R7 + R8, r8 (lovish 22:59: weathered green copper on the tower; ref62 / ref66 / ref70): the frozen cone's
    voxels laid in roof_kit COPPER courses (oxidized the base tone, weathered / exposed the wet and bleached ones,
    wettest at the eave), framed by dark structure lines - the four arrises (cone_rows) polished blackstone bricks
    from the eave to the top tier, the hip rolls of a pyramid roof; at each tier change (TIER_TOPS) the ring the next
    tier doesn't cover a dark ledge course (the step reads as a shadow line, ref66), carried on a corbel table in the
    row under it - upside-down dark stairs on every other cell of each face, the cells between cut back one (the air
    teeth are the ornament, recipes R10). All inside the frozen outline. The R7 eye keeps its elevation cells; over the
    top tier a copper needle ring (y 22), a dark ledge ring (y 23), the dark wall post (y 24) and the brass tip."""
    rows, arris = cone_rows(g)
    eye = {(1, 14, 12), (1, 15, 11), (1, 15, 13), (2, 15, 12), (2, 16, 12), (1, 15, 12)}
    struct = set()
    for y in (10, 14, 18):                               # the corbel tables under the ledges
        cells = rows[y]
        for x, z in cells:
            col = [xx for xx, zz in cells if zz == z]
            row = [zz for xx, zz in cells if xx == x]
            if (x, z) in arris[y] or (x, y, z) in eye:
                continue
            for face, off, facing in ((x == min(col), z - int(TZ), "east"), (x == max(col), z - int(TZ), "west"),
                                      (z == min(row), x - int(TX), "south"), (z == max(row), x - int(TX), "north")):
                if not face:
                    continue
                if off % 2:
                    stair(g, (x, y, z), "dark", facing, "top")
                    struct.add((x, y, z))
                elif (x, y, z) in g.b:
                    g.clear(x, y, z)
                break
    rows, arris = cone_rows(g)
    for y, cells in rows.items():
        for x, z in cells:
            q = (x, y, z)
            if q in struct or q in eye:
                continue
            if (x, z) in arris[y] and y <= 21:
                paint(g, q, "dark")
                struct.add(q)
            elif y in TIER_TOPS and (x, z) not in rows.get(y + 1, set()):
                paint(g, q, "dark")
                struct.add(q)
    face = {q: g.b[q] for y, cells in rows.items() if y <= 22 for x, z in cells
            for q in [(x, y, z)] if q not in struct and q not in eye and q in g.b}
    for q, blk in R.retone(face, R.COPPER, seed=8).items():
        g.b[q] = blk
    # the door head (R6, r4: y 9 in the first tier's face, x 0): the arch's voussoirs over the 5-wide lane - granite
    # haunches (upside-down stairs, their step at the jamb side, so the opening's top corners read rounded), quartz
    # bricks either side of a brass keystone slab carrying the ONE door light (a standing lantern set into the
    # face at y 10: check_support rejects hanging ones, and the lane under the head, y 1..8, stays air). Over it the
    # face runs on red. Only 2 pale cells a row (the 30 % row rule): the door's light spot, at the head's centre.
    yh, cz = LANE_H + 1, int(TZ)
    for z in LANE_Z:     # r6: ONE light spot - a quartz keystone in a polished granite head (as the jambs). r4's
        put(g, (0, yh, z), "polished_granite")       # pale jambs + haunches read as a U-frame; r5's pale | brass |
    put(g, (0, yh, cz), "quartz_block")              # pale over a dark vault as an owl face (critics on r4, r5)
    for z in (LANE_Z[0] - 1, LANE_Z[-1] + 1):        # r7: the door's glow - lanterns standing on the jamb tops, either
        put(g, (0, yh, z), LAMP, hanging="false", waterlogged="false")   # side of the keystone, outside the lane: one
    #   on the keystone lit nothing the camera sees (in game at dusk the door cell's luma median was 34, no warm pixel)
    # R7 the eye, at its r2 elevation cells (y 14..16, z 11..13): a granite ring round a recessed deep-red heart, in
    # the second tier's face (x 1, y 12..15) and the third's (x 2, y 16..19)
    for q in ((1, 14, 12), (1, 15, 11), (1, 15, 13)):     # r5: polished granite, +40 over the body - calcite
        put(g, q, "polished_granite")                    # stood +120 and cut the cone into two tiers (critic on r4)
    put(g, (2, 16, 12), "polished_granite")
    g.clear(1, 15, 12)
    put(g, (2, 15, 12), "red_nether_bricks")
    # the needle (r7's critic in game on r6: the top read as a 3 x 3 stub under a copper block): on y 22 every cell
    # but the 1-wide core (x 4, z 12) a copper stair, tall half toward the core; y 23 the same ring as the top
    # ledge, dark; the core blackstone bricks under the wall post (y 24) and the brass tip (y 25)
    for y, mat in ((22, R.COPPER["tones"][0][1]), (23, FAM["dark"][1])):
        for (x, yy, z) in [q for q in g.b if q[1] == y and 3 <= q[0] <= 5 and 11 <= q[2] <= 13]:
            if (x, z) == (4, 12):
                put(g, (x, y, z), FAM["dark"][0])
                continue
            facing = "east" if x < 4 else "west" if x > 4 else "south" if z < 12 else "north"
            put(g, (x, y, z), mat, facing=facing, half="bottom", shape="straight", waterlogged="false")
    put(g, (4, 24, 12), FAM["dark"][3])
    paint(g, (4, 25, 12), "brass")


def door(g):
    """R6 (re-mass r3, lane 5 x 8 in r4): the door is the tower's light spot (ref18's glowing door; r2's lane was a
    dark hole in a black frame) - an arch ring at the mouth, not a pale plane (r3's all-calcite 7-wide far wall read
    as a grey slab under the cone; r4's first pass, pale only mid-wall, as a dark slot in a white frame). With the
    lane 5 wide the cone's face and dark arrises come down to the eave beside it (z 8..9, 15..16). The ring: polished
    granite jambs (z 9 / 15, x 0, r5) on the andesite base course up to the head, its light at the head (tower()). Beside
    them a red bay panel (z 8 / 16, a red cell at y 6 so the lintel cornice stops a cell short of the jamb - run on,
    the two read as one white gateway) and the buttresses (z 7 / 17) like the arcade's piers (andesite base and
    capital, a blackstone-brick shaft, the quartz cornice), capped by one small dark stair AT the eave (ref40's
    pinnacle kept low: r2's rose to y 9 and read as the block's vertical sides). Inside: the reveals (the lane's side
    walls z 9 / 15) quartz-brick pilasters (x 1 4 7) between red fields, deep-red panels recessed at y 2..4, a
    polished diorite string at the lintel line, a quartz course under the head; the far wall (x 9, the gate's back)
    a calcite field on a polished diorite skirting between diorite gate channels (z 10 / 14), red over the lintel
    line, the brass rosette kept. The lane floor smooth stone (top slabs, flush) behind a polished diorite
    threshold and gate line."""
    for zp, zb, zj in ((7, 8, 9), (17, 16, 15)):       # buttress, bay panel, jamb (zb is also the reveal's back)
        for z in (zp, zb, zj):
            put(g, (0, 0, z), "tuff")
            put(g, (0, 1, z), "polished_andesite")    # R1: the base course runs on under the jambs and buttresses
        for y in (2, 3, 4):
            put(g, (0, y, zp), FAM["dark"][0])
        stair(g, (0, 5, zp), "andesite", "east", "top")
        stair(g, (0, HALL_TOP + 1, zp), "dark", "east")   # the buttress cap, at the eave
        for y in range(2, 6):                             # a red bay panel between buttress and jamb, the arcade's
            put(g, (0, y, zb), "red_terracotta")          # rhythm (a dark pier here ran the arris to the ground)
        stair(g, (0, 6, zp), "quartz", "east", "top")     # the cornice ends on the buttress, a red cell short of
        put(g, (0, 6, zb), "red_terracotta")              # the jamb (run on to the door, the lintel line and the
        #                                                   jambs read as one white gateway frame)
        for y in range(2, LANE_H + 1):                    # the jamb: polished granite to the head - r4's calcite
            put(g, (0, y, zj), "polished_granite")        # jambs read as a white U-frame round a dark mouth (critic
        #   on r4); a calcite impost at y 8 was then the door's lightest cell (squint 135 vs the head's 99), at the
        #   sides, so the light gathers at the head's centre instead
        for x in range(1, HALL_X[1]):                     # the reveals, seen through the door
            for y in range(1, LANE_H + 1):
                if y == 1:
                    n = "tuff"
                elif x % 3 == 1:
                    n = "quartz_bricks"
                elif y == 6:
                    n = "polished_diorite"
                elif 2 <= y <= 4:
                    g.clear(x, y, zj)
                    put(g, (x, y, zb), "red_nether_bricks")
                    continue
                else:                                     # r4: red over the panels, a quartz course under
                    n = "quartz_bricks" if y == LANE_H else "red_terracotta"   # the head (all-calcite read as a
                put(g, (x, y, zj), n)                     # white plane from the RTS camera)
    for z in LANE_Z:        # the far wall, the gate's back (the animated gate will hang here)
        d = abs(z - int(TZ))
        for y in range(1, LANE_H + 1):
            # a pale field under a red head (the vault seen in perspective): r3's 7-wide all-pale wall read as a grey
            # slab under the cone; r4's first 5-wide pass (pale only z 11..13) read as a dark slot in a white frame.
            # Over the lintel line red - rows 7 / 8 carry the jambs' pale, the 30 % row rule
            if y >= HALL_TOP:                          # r6: the top a light field round the spot (r5's red band
                n = "calcite"                          # here rendered luma 25 under the head: the owl's dark mask)
            elif y == 1 or d == 2:
                n = "polished_diorite"
            else:
                n = "calcite"
            put(g, (HALL_X[1], y, z), n)
        for x in range(2, 8):                          # the passage ceiling: red and pale bands
            if (x, LANE_H + 1, z) in g.b:
                put(g, (x, LANE_H + 1, z), "red_terracotta" if x % 2 else "quartz_bricks")
        for x in LANE_X:                               # the floor: smooth stone, flush with the lawn, on a tuff
            if x in (LANE_X[0], LANE_X[-1]):           # threshold and gate line (r4's diorite threshold read as a
                put(g, (x, 0, z), "tuff")              # pale strip under the door)
            else:
                put(g, (x, 0, z), "smooth_stone_slab", type="top", waterlogged="false")
    cz = int(TZ)
    for dz, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):  # the rosette: a brass cross round a brass boss (r6: the
        put(g, (HALL_X[1], 6 + dy, cz + dz), FAM["brass"][0])     # chiseled blackstone boss was the front's darkest
    put(g, (HALL_X[1], 6, cz), FAM["brass"][0])                    # cell, luma 18)
    put(g, (HALL_X[1], LANE_H, cz), FAM["brass"][0])


def works(g):
    """R9, r8: the works shed (x 11..16, z 0..24) a roof_kit slate gable at the 26.5 degree slate pitch over brick
    walls y 1..4 - its ridge cap (y 6, x 13..14) under the hall ridge, the yard eave (x 10, y 5) oversailing the x 11
    wall over a copper gutter, dark oak verges at the ends (z 0 / 24); r6-r7's deepslate lid with ribs had no eave
    and read as a black box. The south end (z 24, the camera's): dark corner piers, a polished andesite plinth, brick
    with two lit slots (x 12 / 15) under a smooth stone lintel. The stack (x 13..14, z 0..1) deepslate bricks to y 12,
    a brass band at y 9, a dark cap."""
    x0, x1 = SHED_X
    sx0, sx1, sz0, sz1, top = STACK
    for q in [q for q in g.b if x0 - 1 <= q[0] <= x1 and q[1] >= 1]:
        del g.b[q]
    for x in range(x0, x1 + 1):
        for z in range(SZ):
            for y in range(1, SHED_TOP):
                put(g, (x, y, z), FAM["works"][0] if 0 < z < SZ - 1 and x0 < x < x1 else BRICK)
            if x0 < x < x1:
                put(g, (x, SHED_TOP, z), FAM["works"][0])        # the roof's body under the slate
    for (u, y, v), (n, p) in R.gable(x1 - x0 + 1, SZ - 2, axis="z", seed=9).items():
        put(g, (x0 + u, SHED_TOP + y, 1 + v), n, **p)
    for x in range(sx0, sx1 + 1):
        for z in range(sz0, sz1 + 1):
            for y in range(SHED_TOP, top + 1):
                put(g, (x, y, z), FAM["works"][0])
            put(g, (x, 9, z), FAM["brass"][0])
            put(g, (x, top, z), FAM["dark"][0])
    zs, zi = SZ - 1, SZ - 2
    for x in range(x0, x1 + 1):                         # the south end
        put(g, (x, 1, zs), PLINTH)
        if x in (x0, x1):
            for y in range(2, SHED_TOP):
                put(g, (x, y, zs), FAM["dark"][0])
        else:
            for y in (2, 3):
                if x in SHED_SLOTS:
                    g.clear(x, y, zs)
                    put(g, (x, y, zi), "deepslate_tiles")
                else:
                    put(g, (x, y, zs), BRICK)
            put(g, (x, SHED_TOP - 1, zs), STRING)
    for x in SHED_SLOTS:
        put(g, (x, 2, zs), LAMP, hanging="false", waterlogged="false")
    for z in range(SZ):                                 # the yard between hall and shed: tuff, dark kerbs
        put(g, (HALL_X[1] + 1, 0, z), "polished_deepslate" if z % 3 == 0 else "tuff")


def detail():
    """The Engine Shed T1, detail round 1: the approved greybox painted and carved region by region (R1-R9)."""
    g = greybox()
    base_paint(g)
    slate_roofs(g)
    tower(g)
    arcade(g)
    gables(g)
    door(g)
    works(g)
    return g


# ============================================================================================ output and checks
def write(g, out=OUT):
    h = max(y for (_, y, _) in g.b) + 1
    assert h <= H_MAX, "%s is %d tall" % (out, h)
    rig = gs._SunRig(SX, h, SZ)
    for (x, y, z), (n, props) in g.b.items():
        rig.set(x, y, z, n, **props)
    rig.bury_grass()
    rig.settle()
    os.makedirs(os.path.dirname(out), exist_ok=True)
    rig.structure().write(out)
    return rig


def check(rig, out=OUT, h_max=H_MAX):
    import audit_structure_pockets as ap
    name = os.path.splitext(os.path.basename(out))[0]
    n = {q: b[0] for q, b in rig.b.items()}
    assert rig.size[1] <= h_max, "%s is %d tall (max %d)" % (name, rig.size[1], h_max)
    assert all((x, 0, z) in n for x in range(SX) for z in range(SZ)), "a hole in the foundation"
    busy = [(x, y, z) for x in LANE_X for z in LANE_Z for y in range(1, LANE_H + 1) if (x, y, z) in n]
    assert not busy, "the exit lane is narrowed: %s" % busy[:8]
    col = [y for y in range(1, rig.size[1]) if (SPAWN[0], y, SPAWN[2]) in n]
    assert not col, "the spawn column is blocked at %s" % col
    lone = [q for q in n if q[1] > 0 and not any((q[0] + a, q[1] + b, q[2] + c) in n for a, b, c in
                                                   ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)))]
    assert not lone, "floating %s" % lone[:6]
    bad = sorted({v for v in n.values() if ("copper" in v and not v.startswith("waxed_") and v != "lightning_rod")})
    assert not bad, "unwaxed copper: %s" % bad
    rig.check_support(name)
    one_way, ground_, roof, rooms, note = ap.audit(name, out)
    assert not one_way and not ground_ and "TRAPPED" not in note, (one_way[:8], ground_[:8], note)
    return "%s %dx%dx%d: %d blocks, lane clear, spawn column open, no pockets (%d roof-only)" % (
        name, SX, rig.size[1], SZ, len(n), len(roof))


def check_banned(rig, name):
    """HARD CHECK (round 5): no cobblestone / mossy cobblestone (or their stairs, slabs, walls) - rubble at 32 px."""
    bad = sorted({n for n, _ in rig.b.values() if any(k in n for k in BANNED)})
    assert not bad, "%s uses banned blocks: %s" % (name, bad)


def outline(cells):
    """Per (z, x) column the top cell, plus the size - the greybox's outline."""
    tops = {}
    for x, y, z in cells:
        tops[(x, z)] = max(tops.get((x, z), -1), y)
    return tops


def check_greybox(rig):
    """HARD CHECK (round 5): --greybox reproduces the approved r3 greybox exactly - 22 tall, every cell and block of the
    committed design/vehicle_labs/brass_l3_greybox.nbt (git HEAD's copy, so a regenerated file can't vouch for itself).
    Round 4 found it regenerating 21 tall: greybox() read the module-level T_APEX, which round 3 had re-bound to the
    spire's 22.0 further down the file (the pyramid needs 23.0) - now GB_APEX, the greybox's own name."""
    import subprocess
    import tempfile
    import preview_structure as PS
    try:
        data = subprocess.run(["git", "show", "HEAD:" + OUT], capture_output=True, check=True,
                              cwd=os.path.dirname(os.path.dirname(os.path.abspath(__file__)))).stdout
    except (OSError, subprocess.CalledProcessError):
        return "greybox: no committed copy to compare (not in git)"
    with tempfile.NamedTemporaryFile(suffix=".nbt") as f:
        f.write(data)
        f.flush()
        root = PS.load_nbt(f.name)
    pal = root["palette"]
    want = {tuple(b["pos"]): str(pal[b["state"]]["Name"]).split(":")[-1] for b in root["blocks"]}
    got = {q: n for q, (n, _) in rig.b.items()}
    assert list(root["size"]) == list(rig.size) == [SX, H_MAX, SZ], "greybox size %s, committed %s" % (
        rig.size, root["size"])
    assert outline(got) == outline(want), "the greybox outline differs from the committed greybox"
    diff = sorted(q for q in set(want) | set(got) if want.get(q) != got.get(q))
    assert not diff, "greybox cells differ from the committed greybox: %s" % diff[:8]
    return "greybox identical to the committed greybox (%d cells, %s)" % (len(got), "x".join(map(str, rig.size)))


if __name__ == "__main__":
    rig = write(greybox())                 # the T1 greybox: design/vehicle_labs/brass_t1_greybox.nbt
    print(check(rig))
    check_banned(rig, "brass_t1_greybox")
    print(check_greybox(rig))
    rig = write(detail(), OUT_T1)          # detail round 1: design/vehicle_labs/brass_t1.nbt
    print(check(rig, OUT_T1))
    check_banned(rig, "brass_t1")
