#!/usr/bin/env python3
"""PILOT (Oct 28): the Brassworks VEHICLE LAB as an ENGINE SHED imitating ONE picture, lovish's ref18
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

Usage: python3 tools/lab_vehicle_brass.py            (writes design/vehicle_labs/brass_l3.nbt and brass_l1.nbt, checks)
       python3 tools/lab_vehicle_brass.py --greybox  (the approved r3 greybox: design/vehicle_labs/brass_l3_greybox.nbt)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np                    # noqa: E402

import gen_structures as gs           # noqa: E402
import shape_kit as sk                # noqa: E402

SX, SZ, H_MAX = 13, 21, 22
SPAWN = (6, 1, 10)
LANE_X, LANE_Z, LANE_H = range(0, 7), range(8, 13), 8
OUT = "design/vehicle_labs/brass_l3_greybox.nbt"

WALL, ROOF, HOLE, GROUND, LAWN = "terracotta", "red_nether_bricks", "obsidian", "stone_bricks", "grass_block"
SHED = "deepslate_bricks"     # the works behind the hall (a second, quieter tone so the front range reads on its own)
# shape_kit's roles: a full cell takes the role, a stair cell role + "_st", a slab cell role + "_slab"
PAL = {"roof": ROOF, "roof_st": "red_nether_brick_stairs", "roof_slab": "red_nether_brick_slab"}

# the massing, round 3 (tuned against the FRONT elevation, column by column; see PASSES above)
HALL_X = (1, 7)       # the hall range is shallow: a long low band along the front, the works shed behind it
HALL_TOP = 5          # hall walls y 1..5; a straight-pitched roof over x 0.5..7.5 from y 6 to its ridge at y 9 (cell 8)
ARCH_H = 4            # arcade openings y 1..4
ARCH_Z = (0, 2, 4, 6, 14, 16, 18, 20)   # 4 bays each side of the tower, running under the wings as in the picture
WING_X, WING_Z = (1, 6), ((0, 3), (17, 20))     # end wings' walls, y 1..WING_TOP
WING_TOP = 6
# the wings' roofs: half a swept gable each, its ridge on the outer end (z -0.5 / 20.5) at y 13 (cell 12), falling
# concave toward the hall (steep at the outer end, flattening inward) - the picture's up-swept ends
WING_ROOF = dict(h=6.8, y0=7.0, half=4.5, sweep=1.8, kick=0.0)
# the tower: ONE steep pyramid over a base block, centred on (TX, TZ); half-widths at its foot; apex at y 22
TX, TZ, THX, THZ = 3.0, 10.0, 3.5, 4.5           # foot x -0.5..6.5 (cells 0..6), z 5.5..14.5 (cells 6..14)
T_FOOT, T_APEX = 6.0, 23.0                       # the pyramid rises from the hall's roof line to the apex
SHED_X, SHED_TOP = (8, 12), 4                   # the works shed: x 8..12, flat roof at y 4
STACK = (10, 11, 0, 1, 10)                      # one smokestack behind the north wing's high end, hidden from the front


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
    g.fill(SHED_X[0], SHED_X[1], 1, SHED_TOP, 0, 20, SHED)
    x0, x1, z0, z1, top = STACK
    g.fill(x0, x1, SHED_TOP + 1, top, z0, z1, SHED)
    # the hall range: low walls under a shallow straight pitch
    g.fill(HALL_X[0], HALL_X[1], 1, HALL_TOP, 0, 20, WALL)
    add(g, pitched(HALL_X[0] - 0.5, HALL_X[1] + 0.5, -0.5, 20.5, HALL_TOP + 1, HALL_TOP + 4))
    # the end wings: a little over the hall, under half-swept roofs rising to their outer ends
    for (z0, z1), (ridge, toward) in zip(WING_Z, ((-0.5, 1), (20.5, -1))):
        g.fill(WING_X[0], WING_X[1], 1, WING_TOP, z0, z1, WALL)
        add(g, swept_half(WING_X[0] - 1.5, WING_X[1] + 0.5, ridge, toward, **WING_ROOF))
        for z in range(z0 + 1, z1):
            g.set(WING_X[0], WING_TOP - 1, z, HOLE)
    for x in range(2, HALL_X[1] + 1, 2):                # the camera's south end: ground bays
        g.fill(x, x, 1, ARCH_H, SZ - 1, SZ - 1, HOLE)
    for z in ARCH_Z:                                    # the arcade: a dark ground band
        g.fill(HALL_X[0], HALL_X[0], 1, ARCH_H, z, z, HOLE)
    # the tower: a base block carrying ONE steep pyramid, flanks straight from the roof line to the apex
    g.fill(0, 6, 1, int(T_FOOT) - 1, 6, 14, WALL)
    add(g, pyramid(TX, TZ, THX, THZ, T_FOOT, T_APEX))
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


# ============================================================================================ the detail (L3)
# block families: a painted cell keeps its shape (full / stairs / slab) and takes the family's block of that shape
FAM = {
    "roof": ("red_nether_bricks", "red_nether_brick_stairs", "red_nether_brick_slab"),
    "white": ("quartz_block", "quartz_stairs", "quartz_slab"),
    "dark": ("nether_bricks", "nether_brick_stairs", "nether_brick_slab"),
}
OUT_L3, OUT_L1 = "design/vehicle_labs/brass_l3.nbt", "design/vehicle_labs/brass_l1.nbt"
PILASTERS_Z = (0, 3, 17, 20)           # the front arcade's pilasters (the tower's dark base piers at z 6 / 14)
BAY_Z0 = (1, 4, 15, 18)                # each 2-wide bay's first column
LIGHTS_Z = (0, 2, 4, 16, 18, 20)       # the tiny lights under the eave, every 2 blocks


def shape_of(name):
    return 1 if name.endswith("_stairs") else 2 if name.endswith("_slab") else 0


def paint(g, q, fam):
    """Recolour a cell in a family, keeping its shape and its stair / slab state."""
    if q in g.b:
        n, p = g.b[q]
        if n in ROLE_OF:
            g.b[q] = (FAM[fam][shape_of(n)], p)


ROLE_OF = {n for f in FAM.values() for n in f}


def put(g, q, name, props=None, **kw):
    """A full (or thin) block: may replace anything. props: a stair / slab state (from stair() / slab())."""
    g.set(*q, name, **dict(props or {}, **kw))


def stair(fam, facing, half="bottom"):
    return FAM[fam][1], dict(facing=facing, half=half, shape="straight", waterlogged="false")


def slab(fam, t="bottom"):
    return FAM[fam][2], dict(type=t, waterlogged="false")


# ---- round 3 (critics on r2: 4 / 4 - the tower still read as stacked stages): ONE continuous taper, by numbers
# measured on the LIT RENDER, not by block: polished blackstone renders mid grey, so the near-black share is nether
# brick (65,37,40 - ref18's dark crimson-brown) and deepslate tiles (58 grey, dark under the sun's angle)
RED, BLACK, CREAM = "red_nether_bricks", "nether_bricks", "quartz_block"
DARK_GLASS = "tinted_glass"
PILASTER = "polished_granite"          # the arcade's pilasters: paler than the dark bays, warm (red-dominant pixels)
T_CX, T_CZ, T_BACK = 3, 10, 5          # the spire's axis (x, z) and its back column (x 6 = the spawn column)
T_Y0, T_APEX, T_HW0 = 9, 22.0, 3.5      # the line: half-width 3.5 at the hall's roof line (y 9) to 0 at y 22
# THE LINE. ref18's flank, measured on its sky mask in this build's block grid (230 px -> 21 blocks, ground..tip ->
# 0..22), is straight: half-width 2.47 at the hall roof (y 9.5), 1.55 at y 15.8, 0.23 at y 21.5 (hw = 4.07 - 0.169 y).
# The approved r3 massing is 7 wide at the hall roof (both critics: the massing reads right at RTS zoom), so the spire
# runs from r3's width there to the apex: hw(y) = 3.5 (22 - y) / 13 - steeper than ref18's by 0.1 a block, up to 1.0
# block wider at the foot, equal at y ~20.
# QUANTIZING (front view), per HALF row: a row's lower half can only end on a cell edge (lo = k + 0.5); its upper half
# on that edge or half a block in (hi = lo - 0.5: the outer ring a stair, tall half facing in); the next row's lower
# half no wider than that upper half, so the outline never widens going up (pass 8 let a full row follow a stair row at
# the same width: notches). width_table() takes the monotone outline with the smallest summed square deviation from
# the line (then the smallest worst one) - one line of half steps, no shoulders.


def hw(y):
    return max(0.0, T_HW0 * (T_APEX - y) / (T_APEX - T_Y0))


def width_table():
    """THE WIDTH TABLE: row y -> (line half-width at the lower / upper half's middle, lower / upper outline)."""
    ys = list(range(T_Y0, int(T_APEX)))
    best = {9.0: (0.0, 0.0, [])}                 # state: the last half row's outline -> (worst, sum sq, outlines)
    for y in ys:
        lo_t, hi_t = hw(y + .25), hw(y + .75)
        nxt = {}
        for prev, (sq, worst, path) in best.items():
            for lo in [k + .5 for k in range(5) if k + .5 <= prev]:
                for hi in [w for w in (lo, lo - .5) if w > 0]:
                    e1, e2 = abs(lo - lo_t), abs(hi - hi_t)
                    cand = (sq + e1 * e1 + e2 * e2, max(worst, e1, e2), path + [(y, lo, hi)])
                    if hi not in nxt or cand[:2] < nxt[hi][:2]:
                        nxt[hi] = cand
        best = nxt
    path = min(best.values(), key=lambda c: c[:2])[2]
    return {y: (round(hw(y + .25), 2), round(hw(y + .75), 2), lo, hi) for y, lo, hi in path}


HW_TABLE = width_table()
TIP_Y0 = 20           # from here the point is a brick wall post (the line's half-width < 0.4)


def spire_cells():
    """{(x, y, z): ("full",) | ("stairs", inward)} from HW_TABLE: square rings of Chebyshev distance d round the axis
    (cut at the back column); the outer ring d = lo - 0.5 is full, or a stair facing in where the upper half steps in."""
    out = {}
    for y, (_, _, lo, hi) in HW_TABLE.items():
        if y >= TIP_Y0:
            continue
        dmax = int(lo - .5)
        for x in range(max(0, T_CX - dmax), T_BACK + 1):
            for z in range(T_CZ - dmax, T_CZ + dmax + 1):
                d = max(abs(x - T_CX), abs(z - T_CZ))
                if d > dmax:
                    continue
                if d < dmax or hi == lo or d == 0:
                    out[(x, y, z)] = ("full",)
                else:
                    side = abs(z - T_CZ) == d                # a flank cell (z) or a front / back cell (x)
                    out[(x, y, z)] = ("stairs", ("south" if z < T_CZ else "north") if side else
                                      ("east" if x < T_CX else "west"))
    return out


def tower(g):
    """The tower, replacing the greybox pyramid: the base block over the great door (y 1..8, x 0..6, z 6..14) and ONE
    continuous spire from the hall's roof line to the apex, voxelized with its stair edges (low side out) so each
    flank reads as a line. Dark red, near-black bands, cream only as dashed single cells on the two front arrises."""
    for q in [q for q in g.b if q[0] <= 6 and 6 <= q[2] <= 14 and q[1] >= 1]:
        x, y, z = q
        if y >= 9 or (y >= 6 and z in (6, 7, 13, 14)) or (x == 0 and z in (6, 7, 13, 14)):
            del g.b[q]
    for z in (6, 7, 13, 14):
        for x in range(0, 7):
            for y in range(1, 9):
                put(g, (x, y, z), BLACK if x == 0 and y <= 1 else RED)
    cells = spire_cells()
    for (x, y, z), c in cells.items():
        d = max(abs(x - T_CX), abs(z - T_CZ))
        outer = d == int(HW_TABLE[y][2] - .5) and d > 0
        fam = "dark" if outer and abs(z - T_CZ) == d and abs(x - T_CX) < d else "roof"   # near-black flank faces
        if c[0] == "stairs":
            put(g, (x, y, z), *stair(fam, c[1]))
        elif c[0] == "slab":
            put(g, (x, y, z), *slab(fam))
        else:
            put(g, (x, y, z), FAM[fam][0])
    for y in range(TIP_Y0, int(T_APEX)):                       # the point: thin brick posts (half a block wide),
        put(g, (T_CX, y, T_CZ), "red_nether_brick_wall")       # where the line is under a quarter block
    for (x, y, z), c in cells.items():                 # cream: one cell per side where the flank line steps in -
        d = int(HW_TABLE[y][2] - .5)                   # the stair on the front arris, a half-block wedge lying ON
        if c[0] == "stairs" and d > 0 and x == T_CX - d and abs(z - T_CZ) == d:   # the line: dashed, no "ears"
            paint(g, (x, y, z), "white")
    # the door head: corbelled inward with upside-down stairs to a point; ONE warm light framed in its centre
    def front(y, z):
        return min(x for x in range(0, 7) if (x, y, z) in g.b)
    for y, zs in ((9, (9, 10, 11)), (10, (10,))):
        for z in zs:
            g.clear(front(y, z) if (0, y, z) not in g.b else 0, y, z)
    for y, (zn, zs) in ((9, (8, 12)), (10, (9, 11))):
        put(g, (0, y, zn), *stair("roof", "north", "top"))
        put(g, (0, y, zs), *stair("roof", "south", "top"))
    for z in (9, 10, 11):
        put(g, (1, 9, z), BLACK)
    put(g, (1, 10, 10), "shroomlight")
    # the brass clock: one cell on the spire's face, as the picture's medallion
    put(g, (front(14, 10), 14, 10), "waxed_copper_block")
    # the great door's interior: dark, the far wall deep red banded near-black
    for z in LANE_Z:
        for y in range(1, LANE_H + 1):
            put(g, (7, y, z), BLACK if y == 4 else RED)          # deep red in the door's shadow, one dark band


def detail():
    """L3, round 3: the r3 massing, the tower as one continuous spire, the arcade as 2-wide dark bays between paler
    pilasters with a row of tiny lights under a cream eave, the wings as a red plane under that one cream eave line."""
    g = greybox()
    base = dict(g.b)
    for q, (n, _) in base.items():
        if n in (WALL, HOLE):
            g.b[q] = (RED, {})
        elif n == SHED:
            g.b[q] = ("deepslate_tiles", {})
    tower(g)
    for z in LANE_Z:
        for x in LANE_X:
            put(g, (x, 0, z), "polished_blackstone" if z in (9, 11) else "deepslate_tiles")
    hall_z = [z for z in range(SZ) if not 6 <= z <= 14]
    # ---- THE EAVE: one cream line, an overhanging upside-down stair along the whole front at the wall top
    for z in hall_z:
        put(g, (0, 6, z), *stair("white", "east", "top"))
        n = g.b.get((1, 6, z), ("air",))[0]
        if shape_of(n) == 0:
            put(g, (1, 6, z), RED)
        put(g, (1, 5, z), BLACK)                               # the dark frieze the lights hang in
    # ---- THE ARCADE: 2-wide dark bays (tinted glass behind a round head) between paler pilasters; tiny lights hung
    # every 2 blocks directly under the eave
    for z in PILASTERS_Z:
        for y in range(1, 5):
            put(g, (0, y, z), PILASTER)
        put(g, (1, 4, z), PILASTER)
    for z0 in BAY_Z0:
        for i, z in enumerate((z0, z0 + 1)):
            for y in (1, 2):
                g.clear(1, y, z)
            put(g, (1, 3, z), *stair("dark", "north" if i == 0 else "south", "top"))
            put(g, (2, 3, z), DARK_GLASS)                      # dark glass in the arch's head
            for y in (1, 2):
                put(g, (2, y, z), "red_stained_glass")         # the bay's lower panes glow dark crimson,
                put(g, (3, y, z), RED)                         # red brick behind them (ref18's crimson bays)
    for z in LIGHTS_Z:
        put(g, (0, 5, z), "end_rod", facing="down")
    # ---- the ends (z 0 and z 20, the camera's second face): the same order, the lights in notches under the eave
    for zf, zin in ((0, 1), (SZ - 1, SZ - 2)):
        for x in range(1, 8):
            for y in range(1, 6):
                put(g, (x, y, zf), BLACK if y == 5 else RED)
            put(g, (x, 6, zf), CREAM)
        for x in (1, 4, 7):
            for y in range(1, 5):
                put(g, (x, y, zf), PILASTER)
        for i, x in enumerate((2, 3, 5, 6)):
            for y in (1, 2):
                g.clear(x, y, zf)
                put(g, (x, y, zin), DARK_GLASS)
            put(g, (x, 3, zf), *stair("dark", "west" if x in (2, 5) else "east", "top"))
            put(g, (x, 3, zin), DARK_GLASS)
        for x in (2, 4, 6):
            put(g, (x, 5, zf), "end_rod", facing="down")
    # ---- the wing roofs: r3's swept mass, each course's eave turned into stairs stepping in across x to a 1-wide
    # ridge at x 3 (so the end reads as a gable, not a slab wall), a 1-block overhanging eave of upside-down cream
    # stairs at the wall top on the outer end, a small brick finial on the ridge
    for (z0, z1) in ((0, 4), (16, 20)):
        for z in range(z0, z1 + 1):
            for y in range(WING_TOP + 1, H_MAX):
                reach = max(0, 3 - (y - WING_TOP - 1))        # the gable: 3 out from the ridge at y 7, 1 less a course
                for x in range(0, 7):
                    q = (x, y, z)
                    if q not in g.b or g.b[q][0] not in ROLE_OF:
                        continue
                    off = abs(x - 3)
                    if off > reach:
                        del g.b[q]
                    elif off == reach and off > 0:
                        put(g, q, *stair("roof", "east" if x < 3 else "west"))
    for zf, inward in ((0, "south"), (SZ - 1, "north")):     # the eave lip on the wings' outer ends: one cream line
        for x in range(1, 7):
            put(g, (x, WING_TOP, zf), *stair("white", inward, "top"))
    for zr in (0, SZ - 1):
        top = max(y for y in range(H_MAX) if (3, y, zr) in g.b)
        put(g, (3, top + 1, zr), "red_nether_brick_wall")
    # ---- the works: blackstone, the stack red with a dark cap, hidden behind the north wing's crest
    x0, x1, z0, z1, top = STACK
    for x in (x0, x1):
        for z in (z0, z1):
            for y in range(SHED_TOP + 1, top + 1):
                put(g, (x, y, z), BLACK if y in (7, top) else RED)
            g.clear(x, top, z) if z == 1 else None
    return g


# ============================================================================================ level 1
def level1(l3):
    """The first stage on the same footprint, taken out of L3: the arcade hall and pavilion walls as at L3, the hall
    under its plain roof, low pavilion roofs capped at y 8, the tower's base and first stage (the arch and the first
    cream lip, to y 11). Only cells L3 also fills."""
    g = G()
    for q, b in l3.b.items():
        x, y, z = q
        if y <= 8 and not (x in (10, 11) and z in (0, 1) and y > 8):
            g.b[q] = b
        elif y <= 11 and x <= 6 and 6 <= z <= 14:
            g.b[q] = b
    # the pavilions' roofs end at y 8 under a course of red slabs, cream on the front edge
    for q in list(g.b):
        x, y, z = q
        if y == 8 and (z <= 4 or z >= 16) and x <= 7 and (x, 9, z) in l3.b and shape_of(g.b[q][0]) == 0 \
                and g.b[q][0] in ROLE_OF:
            g.b[q] = slab("white" if x == 0 else "roof")
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


if __name__ == "__main__":
    if "--greybox" in sys.argv:
        print(check(write(greybox())))
    else:
        l3 = detail()
        r3 = write(l3, OUT_L3)
        print(check(r3, OUT_L3))
        l1 = level1(l3)
        r1 = write(l1, OUT_L1)
        print(check(r1, OUT_L1, 12))
        lost = [q for q in r1.b if q not in r3.b]
        assert not lost, "L1 cells that L3 leaves empty: %s" % lost[:8]
        print("L1 is a subset of L3 (%d of %d cells)" % (len(r1.b), len(r3.b)))
