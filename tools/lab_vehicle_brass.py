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
import shape_kit as sk                # noqa: E402

SX, SZ, H_MAX = 17, 25, 26          # the T1 lab plot (design/building_tiers.md): x = depth, z = the front's width
SPAWN = (8, 1, 12)
LANE_X, LANE_Z, LANE_H = range(0, 9), range(9, 16), 10      # the great door: 7 wide, 10 tall, through the tower
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
# the tower: ONE steep pyramid over a base block, centred on (TX, TZ); half-widths at its foot
TX, TZ, THX, THZ = 4.0, 12.0, 4.5, 5.5           # 3, 10, 3.5, 4.5: foot cells x 0..8, z 7..17
GB_FOOT, GB_APEX = 7.0, 27.0                     # 6, 23: from the hall's roof line to a point at y 25; the apex sits
                                                 # one block over the plot's top, as r3's did (a point exactly at the
                                                 # top is zero-wide there and voxelizes one row short)
SHED_X, SHED_TOP = (11, 16), 5                   # (8, 12), 4: the works shed, flat roofed
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
    g.fill(0, int(2 * TX), 1, int(GB_FOOT) - 1, int(TZ - THZ + 0.5), int(TZ + THZ - 0.5), WALL)
    add(g, pyramid(TX, TZ, THX, THZ, GB_FOOT, GB_APEX))
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
# THE ALLOWLIST: the checklist's palette roles and their shapes, the lights, and the lawn in front
ALLOWED = ({n for f in FAM.values() for n in f if n} | {
    "stripped_mangrove_log", "chiseled_polished_blackstone", "cracked_polished_blackstone_bricks", "tuff",
    "smooth_stone_slab", "calcite", "quartz_block", "bone_block", LIGHT, LAMP, LAWN, "deepslate_tiles"})
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
    """The greybox's stand-in blocks to the palette: wall red terracotta, holes the darkest recess brick, the works
    deepslate bricks, roof cells red terracotta / mangrove shapes, the foundation tuff (R1)."""
    for q, (n, p) in list(g.b.items()):
        if n == WALL:
            g.b[q] = (FAM["red"][0], {})
        elif n == HOLE:
            g.b[q] = ("cracked_polished_blackstone_bricks", {})
        elif n in ROOF_NAMES:
            paint(g, q, "red")
        elif n == GROUND:
            g.b[q] = ("tuff", {})


def arcade(g):
    """R1 + R2 + R3 on the west face, halls and wings (z 0..6, 18..24): pier columns 1 proud at x 0 on the odd
    columns (a tuff plinth, an andesite base, a blackstone-brick shaft, an andesite capital - ref39), a recessed bay
    on every ARCH_Z column (a tuff sill, open y 2..4, its back a red terracotta panel with a calcite eye, a dark
    stair head); over it the pale cornice (upside-down quartz stairs at x 0) with one white light per bay, and the
    frieze above the cornice where the wings show it (red, a chiseled blackstone motif over every pier)."""
    import brass_lab_parts as B
    for z in [z for z in range(SZ) if z < 7 or z > 17]:
        if z in ARCH_Z:
            put(g, (1, 1, z), "polished_blackstone")      # the bay's sill: dark, the grey plinth runs under piers
            for y in (2, 3, 4):
                g.clear(1, y, z)
            for y, n in ((2, "red_terracotta"), (3, "calcite"), (4, "cracked_polished_blackstone_bricks")):
                put(g, (2, y, z), n)
            stair(g, (1, 5, z), "dark", "east", "top")
            candle(g, (0, 7, z))
        else:
            stamp(g, B.pilaster(5), (0, 1, z), 1)
            put(g, (0, 0, z), "tuff")                   # the pier's footing; the lawn stays in front of the bays
        stair(g, (0, 6, z), "quartz", "east", "top")
        if (z < 3 or z > SZ - 4) and (1, 7, z) in g.b and g.b[(1, 7, z)][0] == FAM["red"][0]:   # wings only
            put(g, (1, 7, z), "chiseled_polished_blackstone" if z % 2 else "red_terracotta")


def gables(g):
    """R5's gable ends (z 0 north, z 24 south - the RTS camera's side): the ground storey in plane piers (x 3 5 7 9,
    andesite capitals) and bays recessed 1 (x 4 6 8, the arcade's red / calcite panels under dark heads) on a tuff
    plinth, the pale lintel band, the frieze; on the roof end red courses (terracotta / mangrove planks) round the
    pavilion emblem (a cream panel with a red cross, recessed in a blackstone frame - ref39's pediment), a dark back
    edge."""
    import brass_lab_parts as B
    for s in (0, 1):
        Z, F = side_maps(s)
        zf, zi = Z(0), Z(1)
        for x in range(1, HALL_X[1] + 1):
            put(g, (x, 1, zf), "tuff")
            if x >= 3 and x % 2 == 1:                   # a pier in the face plane
                for y in (2, 3, 4):
                    put(g, (x, y, zf), FAM["dark"][0])
                stair(g, (x, 5, zf), "andesite", F("south"), "top")
            elif x >= 4:                                # a bay, recessed one
                for y in (2, 3, 4):
                    g.clear(x, y, zf)
                for y, n in ((2, "red_terracotta"), (3, "calcite"), (4, "red_terracotta")):
                    put(g, (x, y, zi), n)
                stair(g, (x, 5, zf), "dark", F("south"), "top")
            elif x == 2:                                # the corner bay's panel, seen in plane from this side
                put(g, (2, 5, zf), FAM["dark"][0])
            put(g, (x, 6, zf), FAM["quartz"][0])        # the lintel band, round the corner
            if x <= WING_X[1]:
                put(g, (x, 7, zf), "chiseled_polished_blackstone" if x % 2 else "red_terracotta")
        for y in range(WING_TOP + 1, 14):               # the roof end's courses
            for x in range(1, WING_X[1] + 1):
                q = (x, y, zf)
                if q in g.b and shape_of(g.b[q][0]) == 0:
                    g.b[q] = ("red_terracotta" if y % 2 == 0 else "mangrove_planks", {})
        stamp(g, B.emblem(), (4, 10, zf), 2 if s == 0 else 0)


def wing_roofs(g):
    """R5's swept roofs (z 0..2, 22..24): red courses - mangrove shapes, a polished granite lip on the middle tier,
    red terracotta / stripped mangrove beam courses on the front end (x 0) - a grey rib up the middle (x 4), a dark
    back edge (x 8), the ridge along the outer gable beaded pale / red, the front rake's top cells beaded, a brass
    finial at the ridge's front end."""
    for s in (0, 1):
        Z, F = side_maps(s)
        for zz in (0, 1, 2):
            z = Z(zz)
            tops = {x: max([y for y in range(WING_TOP + 1, H_MAX) if (x, y, z) in g.b], default=None)
                    for x in range(0, WING_X[1] + 1)}
            for (x, y, zq), (n, p) in list(g.b.items()):
                if zq != z or y <= WING_TOP or x > WING_X[1] or n not in {f for v in FAM.values() for f in v}:
                    continue
                q, top = (x, y, z), y == tops[x]
                if x == 0:
                    if top:
                        paint(g, q, ("brass", "quartz", "mangrove")[zz])
                    elif y == 11:                       # a string course across the front end (ref44)
                        paint(g, q, "andesite")
                    elif shape_of(n) == 0:
                        g.b[q] = ("red_terracotta", {}) if y % 2 == 0 else ("stripped_mangrove_log", {"axis": "z"})
                elif x == WING_X[1]:
                    paint(g, q, "dark")
                elif x == 4 and top:
                    paint(g, q, "deep")
                elif top and zz == 0:
                    paint(g, q, "quartz" if x % 2 else "mangrove")
                elif top and zz == 1:
                    paint(g, q, "granite")
                elif shape_of(n):
                    paint(g, q, "mangrove")


def hall_roofs(g):
    """R4 (z 3..7, 17..21): red courses - red terracotta tiers, mangrove stair / slab drips - a grey rib over the
    hall pier (polished deepslate, z 5 / 19), a dark ridge course (x 5) between polished granite lips."""
    for (x, y, z), (n, p) in list(g.b.items()):
        if not (3 <= z <= 7 or 17 <= z <= 21) or y < HALL_TOP + 1 or x > HALL_X[1] or n not in (
                FAM["red"][0], FAM["red"][1], FAM["red"][2]):
            continue
        q = (x, y, z)
        if y == HALL_TOP + 3 and x == 5:              # the ridge course: dark, a polished granite lip either side
            paint(g, q, "dark")
        elif y == HALL_TOP + 3:
            paint(g, q, "granite")
        elif z in (5, SZ - 1 - 5):
            paint(g, q, "deep")


def tower(g):
    """R6 frame, R7, R8: the tower body (z 8..16 over y 10) red fields (terracotta, mangrove shapes) with dark
    arrises on every corner of every course, an andesite string course on the set-back rows 13 and 19; on the front
    the pale diamond eye with a recessed red heart (R7), the red panel grid in a dark lattice with a lit slot and a
    dark point under a brass tip (R8)."""
    rows = {}
    for (x, y, z) in g.b:
        if y >= 10 and 8 <= z <= 16 and x <= 8:
            a = rows.setdefault(y, [x, x, z, z])
            a[:] = [min(a[0], x), max(a[1], x), min(a[2], z), max(a[3], z)]
    for (x, y, z), (n, p) in list(g.b.items()):
        if y not in rows or not 8 <= z <= 16 or x > 8:
            continue
        xa, xb, za, zb = rows[y]
        edge_x, edge_z = x in (xa, xb), z in (za, zb)
        if edge_x and edge_z:
            paint(g, (x, y, z), "dark")
        elif y in (13, 19) and (edge_x or edge_z):    # string courses: pale on the first set-back, grey above
            paint(g, (x, y, z), "diorite" if y == 13 else "andesite")
    # the door head (R6): the jambs' tops dark, a pale voussoir line over a dark hood mould
    for z in range(8, 17):
        for y in (10, 11, 12):
            if (1, y, z) in g.b and z in (8, 16):
                paint(g, (1, y, z), "dark")
        if z in LANE_Z:
            paint(g, (1, 12, z), "quartz" if z % 2 else "mangrove")
            stair(g, (0, LANE_H + 1, z), "dark", "east", "top")
    # R7 the eye: calcite ring round a recessed deep-red heart, front face x 2, y 14..16, z 11..13
    for q in ((2, 14, 12), (2, 15, 11), (2, 15, 13)):
        put(g, q, "calcite")
    paint(g, (2, 16, 12), "diorite")
    g.clear(2, 15, 12)
    put(g, (3, 15, 12), "red_nether_bricks")
    # R8 the panel grid (front face x 3, y 19..22): a dark lattice row, a row of three red panels with the lit slot
    # in the middle, a lattice row, two red stair panels either side of a dark mullion; the point dark, a brass tip
    for z in (11, 12, 13):
        put(g, (3, 19, z), "polished_blackstone")
        paint(g, (3, 21, z), "flat")
        paint(g, (3, 22, z), "dark" if z == 12 else "mangrove")
    put(g, (3, 20, 11), "red_terracotta")
    put(g, (3, 20, 13), "red_terracotta")
    g.clear(3, 20, 12)
    put(g, (4, 20, 12), LAMP, hanging="false", waterlogged="false")
    put(g, (4, 23, 12), "red_terracotta")
    put(g, (4, 24, 12), "polished_blackstone")
    paint(g, (4, 25, 12), "brass")


def door(g):
    """R6: dark brick jambs on the tower piers (z 8 / 16) to x 0 on a tuff plinth, stepped back in dark stairs;
    the buttresses (z 7 / 17) red with an andesite string course at the arcade's capital line, capped by a pinnacle
    (a dark wall, a brass point - ref40); the far wall (x 9) the gate's back: polished deepslate guide channels at
    z 9 / 15 where the animated gate will run, a tuff threshold, cracked / polished blackstone courses, the brass
    rosette, and the ONE warm light in a niche high in the door; the passage ceiling banded red / pale."""
    import brass_lab_parts as B
    for zj, zb in ((8, 7), (16, 17)):
        put(g, (0, 1, zj), "tuff")
        for y in range(2, 8):
            put(g, (0, y, zj), FAM["dark"][0])
        for y in (8, 9):
            paint(g, (0, y, zj), "dark")
        put(g, (0, 1, zb), "tuff")
        put(g, (0, 5, zb), "polished_andesite")
        put(g, (0, 0, zj), "tuff")
        put(g, (0, 0, zb), "tuff")
        for x in range(1, HALL_X[1]):                  # the passage's side walls, seen through the door: dark piers
            for y in range(1, LANE_H + 1):             # (x 1 4 7), red courses between on a tuff base with a pale
                if x % 3 == 1:                         # string at the arcade's lintel line, a deep-red panel recessed
                    n = FAM["dark"][0]                 # one at y 2..4
                elif y == 1:
                    n = "tuff"
                elif y == 6:
                    n = "quartz_bricks"
                elif 2 <= y <= 4:
                    g.clear(x, y, zj)
                    put(g, (x, y, zb), "red_nether_bricks")
                    continue
                else:
                    n = "red_terracotta" if y % 2 else "mangrove_planks"
                put(g, (x, y, zj), n)
        g.clear(0, 8, zb)
        stamp(g, B.pinnacle(2), (0, 8, zb), 1)
    for z in LANE_Z:
        for y in range(1, LANE_H + 1):
            if z in (LANE_Z[0], LANE_Z[-1]):
                n = "polished_deepslate"
            elif y == 1:
                n = "tuff"
            elif y == LANE_H:
                n = FAM["dark"][0]
            else:
                n = "cracked_polished_blackstone_bricks" if y % 2 == 0 else "polished_blackstone"
            put(g, (HALL_X[1], y, z), n)
        for x in range(2, 8):                          # the passage ceiling: red and pale bands
            if (x, LANE_H + 1, z) in g.b:
                put(g, (x, LANE_H + 1, z), "red_terracotta" if x % 2 else "quartz_bricks")
    c = TZ
    cz = int(c)
    for dz, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):  # the rosette: a brass cross round a chiseled boss
        put(g, (HALL_X[1], 6 + dy, cz + dz), FAM["brass"][0])
    put(g, (HALL_X[1], 6, cz), "chiseled_polished_blackstone")
    put(g, (HALL_X[1], 8, cz), FAM["brass"][0])
    put(g, (HALL_X[1], 9, cz), LAMP, hanging="false", waterlogged="false")
    put(g, (HALL_X[1] + 1, 9, cz), "cracked_polished_blackstone_bricks")     # the niche's back: no sky through it


def works(g):
    """R9: the shed roof in bays - blackstone-brick ribs every 3rd row and polished deepslate copings at the eaves
    (y 5), the bays sunk one (y 4) in deepslate tile and polished blackstone courses; the south end: dark corner
    piers, a tuff plinth, a recessed bay with ONE lit slot, a polished deepslate lintel; the hall's south-east corner
    (x 9) a dark quoin under the lintel band; the stack in deepslate bricks with a brass band and a dark cap."""
    x0, x1 = SHED_X
    sx0, sx1, sz0, sz1, top = STACK
    for z in range(SZ):
        for x in range(x0, x1 + 1):
            if sx0 <= x <= sx1 and sz0 <= z <= sz1:
                continue
            if x in (x0, x1):
                put(g, (x, SHED_TOP, z), "polished_deepslate")
            elif z % 3 == 0:
                put(g, (x, SHED_TOP, z), FAM["dark"][0])
            else:
                g.clear(x, SHED_TOP, z)
                put(g, (x, SHED_TOP - 1, z), "deepslate_tiles" if z % 3 == 1 else "polished_blackstone")
    for x in range(sx0, sx1 + 1):
        for z in range(sz0, sz1 + 1):
            put(g, (x, 9, z), FAM["brass"][0])
            put(g, (x, top, z), FAM["dark"][0])
    zs, zi = SZ - 1, SZ - 2
    for x in range(x0, x1 + 1):
        put(g, (x, 1, zs), "tuff")
        if x in (x0, x1):
            for y in range(2, SHED_TOP + 1):
                put(g, (x, y, zs), FAM["dark"][0])
        else:
            for y in (2, 3, 4):
                g.clear(x, y, zs)
                put(g, (x, y, zi), "deepslate_tiles")
            put(g, (x, SHED_TOP, zs), "polished_deepslate")
    put(g, (13, 2, zi), LAMP, hanging="false", waterlogged="false")
    xc = HALL_X[1]
    for y, n in ((1, "tuff"), (2, "polished_blackstone"), (3, "polished_blackstone"), (4, "polished_blackstone")):
        put(g, (xc, y, zs), n)
    stair(g, (xc, 5, zs), "andesite", "north", "top")
    put(g, (xc, 6, zs), FAM["quartz"][0])
    for x in range(1, SX):                              # the yard between hall and shed: tuff, dark kerbs
        if (x, 0, 0) in g.b and g.b[(x, 0, 0)][0] == "tuff" and x == HALL_X[1] + 1:
            for z in range(SZ):
                put(g, (x, 0, z), "polished_deepslate" if z % 3 == 0 else "tuff")


def detail():
    """The Engine Shed T1, detail round 1: the approved greybox painted and carved region by region (R1-R9)."""
    g = greybox()
    base_paint(g)
    hall_roofs(g)
    wing_roofs(g)
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
