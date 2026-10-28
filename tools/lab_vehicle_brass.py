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
    "brick": ("bricks", "brick_stairs", "brick_slab"),
    "roof": ("red_nether_bricks", "red_nether_brick_stairs", "red_nether_brick_slab"),
    "white": ("quartz_block", "quartz_stairs", "quartz_slab"),
    "stone": ("polished_diorite", "polished_diorite_stairs", "polished_diorite_slab"),
    "dark": ("polished_blackstone_bricks", "polished_blackstone_brick_stairs", "polished_blackstone_brick_slab"),
    "brass": ("waxed_cut_copper", "waxed_cut_copper_stairs", "waxed_cut_copper_slab"),
    "works": ("deepslate_bricks", "deepslate_brick_stairs", "deepslate_brick_slab"),
    "grey": ("stone_bricks", "stone_brick_stairs", "stone_brick_slab"),
    "flank": ("nether_bricks", "nether_brick_stairs", "nether_brick_slab"),
}
BODY = "brick"                         # the walls: red brick
PIER = "deepslate_bricks"              # the picture's dark grey stone: arcade piers
FLANK = "nether_bricks"                # the tower's dark red-brown flanks between the white ribs
LEDGES = (9, 13, 17)                   # the tower's stage ledges (white courses)
LIT_BAYS = (3, 17)                     # the arcade bays lit from inside (one each side)
DOT = "sea_lantern"                    # the dotted white lights over the arcade (behind glass)
BAND = "calcite"                       # the flat white stone courses
GLOW, GLOW2 = "shroomlight", "ochre_froglight"     # warm lit rooms (behind glass) / paler lit windows
OUT_L3, OUT_L1 = "design/vehicle_labs/brass_l3.nbt", "design/vehicle_labs/brass_l1.nbt"
PIERS_Z = (0, 2, 4, 16, 18, 20)        # front arcade piers (pilasters project to x 0)
BAYS_Z = (1, 3, 5, 15, 17, 19)         # front arcade bays: one round-headed arch each, a dormer / window over each


def shape_of(name):
    return 1 if name.endswith("_stairs") else 2 if name.endswith("_slab") else 0


def paint(g, q, fam):
    """Recolour a cell in a family, keeping its shape and its stair / slab state."""
    if q in g.b:
        n, p = g.b[q]
        if n in ROLE_OF:
            g.b[q] = (FAM[fam][shape_of(n)], p)


ROLE_OF = {n for f in FAM.values() for n in f} | {WALL, ROOF, HOLE, SHED, "red_nether_brick_stairs",
                                                      "red_nether_brick_slab"}


def put(g, q, name, props=None, **kw):
    """A full (or thin) block: may replace anything. props: a stair / slab state (from stair() / slab())."""
    g.set(*q, name, **dict(props or {}, **kw))


def put_part(g, q, name, props=None):
    """A slab or stair: never replaces a full cell (the merge rule)."""
    old = g.b.get(q)
    if old is None or shape_of(old[0]) != 0:
        g.set(*q, name, **(props or {}))


def stair(fam, facing, half="bottom"):
    return FAM[fam][1], dict(facing=facing, half=half, shape="straight", waterlogged="false")


def slab(fam, t="bottom"):
    return FAM[fam][2], dict(type=t, waterlogged="false")


def in_tower(x, y, z):
    """The tower pyramid's cells (the greybox's pyramid, by its own formula) and its base block."""
    if 0 <= x <= 6 and 6 <= z <= 14 and 1 <= y < T_FOOT:
        return True
    if y < T_FOOT:
        return False
    t = min(max((y + 0.5 - T_FOOT) / (T_APEX - T_FOOT), 0), 1)
    rho = max(abs(x - TX) * THZ / THX, abs(z - TZ))
    return rho <= THZ * (1 - t) + 0.75


def exposed(g, q):
    x, y, z = q
    return any((x + a, y + b, z + c) not in g.b for a, b, c in
               ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, 0, 1), (0, 0, -1)))


def tower_cell(q):
    x, y, z = q
    return in_tower(x, y, z) and y >= T_FOOT and x <= 6 and 5 <= z <= 15


def detail():
    """L3: the r3 massing (greybox(), untouched) painted region by region after the picture (see THE DETAIL above).
    Every step only recolours a cell or replaces it; a full cell never becomes a slab or stair."""
    g = greybox()
    base = dict(g.b)
    expo = {q for q in base if exposed(g, q)}
    # ---- the base coats: walls red brick, roofs red tile, the works dark brick
    for q, (n, _) in base.items():
        if n in (WALL, HOLE):
            g.b[q] = (FAM[BODY][0], {})
        elif n == SHED:
            g.b[q] = (FAM["works"][0], {})

    # ---- GROUND: the lawn, a paved apron in the door with pale kerbs
    for z in LANE_Z:
        for x in LANE_X:
            put(g, (x, 0, z), "polished_blackstone" if z in (9, 11) else "deepslate_tiles")
    for x in LANE_X:
        put(g, (x, 0, 7), "polished_diorite")
        put(g, (x, 0, 13), "polished_diorite")

    # ---- THE ARCADE: grey stone piers on pale bases under pale imposts, dark bays glowing deep red, a pale string
    # course carrying a row of small white lights (the picture's dotted line), a dark band under the eaves
    hall_z = [z for z in range(SZ) if not 6 <= z <= 14]
    for z in hall_z:
        for y in range(1, 6):
            put(g, (1, y, z), FAM[BODY][0])
    for z in PIERS_Z:
        put(g, (0, 1, z), "polished_diorite")                  # pale plinth
        put(g, (0, 2, z), PIER)
        put(g, (0, 3, z), "chiseled_stone_bricks")             # the impost
        put(g, (1, 1, z), PIER)
        put(g, (1, 2, z), PIER)
        put(g, (1, 3, z), PIER)
    for z in BAYS_Z:
        for y in (1, 2, 3):
            g.clear(1, y, z)
        lit = z in LIT_BAYS
        put(g, (2, 1, z), "red_nether_bricks")
        put(g, (2, 2, z), "red_stained_glass")
        put(g, (3, 2, z), GLOW if lit else "red_nether_bricks")   # crimson panels; two bays lit from inside
        put(g, (2, 3, z), "red_nether_bricks")
        put(g, (1, 3, z), *stair("dark", "west", "top"))      # the arch's head: a dark hood over the opening
    for z in hall_z:
        put(g, (0, 4, z), *slab("grey", "top"))               # the string course, projecting
        put(g, (1, 5, z), FAM["dark"][0])                      # the dark band under the eaves
    for z in BAYS_Z:                                           # the dotted line of lights in the string course
        put(g, (1, 4, z), "glass")
        put(g, (2, 4, z), DOT)
    for z in PIERS_Z:
        put(g, (1, 4, z), "stone_bricks")

    # ---- END PAVILION ENDS (north and south faces, the camera's second face): the front's order turned the corner
    for zf, zin in ((SZ - 1, SZ - 2), (0, 1)):
        for x in range(1, 7):
            put(g, (x, 1, zf), "polished_diorite")
            put(g, (x, 4, zf), "polished_diorite")           # the string course (full: the wall stays whole)
            put(g, (x, 5, zf), FAM["dark"][0])
            put(g, (x, 6, zf), BAND)
        for x in (1, 3, 5):
            put(g, (x, 2, zf), PIER)
            put(g, (x, 3, zf), "chiseled_stone_bricks")
        for x in (2, 4, 6):
            for y in (1, 2, 3):
                g.clear(x, y, zf)
            put(g, (x, 1, zin), FAM["dark"][0])
            put(g, (x, 2, zin), "red_stained_glass")
            put(g, (x, 3, zin), "red_nether_bricks")
            put(g, (x, 3, zf), *stair("dark", "north" if zf == 0 else "south", "top"))
            if (x, 2, zin + (zin - zf)) in g.b:
                put(g, (x, 2, zin + (zin - zf)), GLOW)
            put(g, (x, 5, zf), "glass")
            put(g, (x, 5, zin), DOT)
        for x in range(7, 8):
            for y in (1, 2, 3, 4):
                put(g, (x, y, zf), FAM[BODY][0])
    for (z0, z1) in WING_Z:                                   # the pavilions' storey band on the front
        for z in range(z0, z1 + 1):
            put(g, (1, 6, z), BAND)

    # ---- PAVILION ROOFS: red tile tiers; the swept rakes (every stair / slab edge) pale; pale ribs at the ends and
    # the middle; a tier line every second course on the gable; brass finials on the high ends
    for q, (n, _) in base.items():
        x, y, z = q
        if y <= WING_TOP or not (z <= 3 or z >= 17) or x > 6:
            continue
        if q not in expo:
            continue
        n = g.b[q][0]
        top = (x, y + 1, z) not in base
        if shape_of(n) == 2 or (top and x in (0, 6)):
            paint(g, q, "white")                               # the eave lips and the rake's crest: white lines
        elif shape_of(n) == 0 and (x in (0, 6) or z in (0, SZ - 1)) and y in (9, 11):
            paint(g, q, "stone")                               # pale tier lines across the gable and the end
        elif shape_of(n) == 1 and x == 3:
            paint(g, q, "white")                               # the central rib up the swept slope
    for zr in (0, SZ - 1):
        put(g, (3, 12, zr), "waxed_cut_copper")                 # the brass finial on the ridge's high end
        put(g, (3, 13, zr), "lightning_rod", facing="up")

    # ---- HALL ROOF: red tile, pale ribs over the piers, a white-framed dormer over each arch, a pale eave
    for q, (n, _) in base.items():
        x, y, z = q
        if not (4 <= z <= 5 or 15 <= z <= 16) or y < 6 or x > 7 or tower_cell(q):
            continue
        if z in (4, 16) and q in expo:
            paint(g, q, "white" if shape_of(g.b[q][0]) else "stone")   # a pale rib over the pier
    for z in (4, 5, 15, 16):
        put_part(g, (1, 6, z), *slab("stone"))
    for z in (5, 15):
        put(g, (2, 7, z), "glass")
        put(g, (3, 7, z), GLOW2)
        put_part(g, (2, 8, z), *stair("stone", "east"))

    # ---- THE TOWER: dark red-brown flanks, a red heart, its outline in white (the white lines climbing the triangle)
    tw = [q for q in base if tower_cell(q)]
    rim = set()
    for y in {q[1] for q in tw}:
        row = [q for q in tw if q[1] == y]
        zs = [q[2] for q in row]
        xs = [q[0] for q in row]
        rim |= {q for q in row if q[2] in (min(zs), max(zs)) and q[0] in (min(xs), max(xs))}   # the arrises
    for q in base:
        if not tower_cell(q) or q not in expo:
            continue
        x, y, z = q
        n = g.b[q][0]
        heart = abs(z - TZ) <= 1 or abs(x - TX) <= 1          # the red heart down the middle of each face
        if shape_of(n) != 0:
            ledge = y in LEDGES and x == min(c[0] for c in tw if c[1] == y and c[2] == z)
            paint(g, q, "white" if q in rim or ledge else "flank")   # white lines up the arrises and the stages
        else:
            put(g, q, "red_nether_bricks" if heart else FLANK)
    # the base: buttress piers of grey stone with pale quoins either side of the great door
    for z in (6, 7, 13, 14):
        for y in range(1, 6):
            quoin = z in (6, 14) and y % 2 == 0
            put(g, (0, y, z), "polished_diorite" if quoin else PIER)
        put(g, (0, 1, z), "polished_diorite")
    for z in (7, 13):
        for y in range(6, 9):
            if shape_of(g.b.get((0, y, z), ("air",))[0]) == 0:
                put(g, (0, y, z), PIER)
    # the great door: a round arch - pale haunches, glass tympanum with the warm glow over the door's head
    put(g, (1, 9, 8), *stair("white", "north", "top"))
    put(g, (1, 9, 12), *stair("white", "south", "top"))
    for z in (9, 11):
        put(g, (1, 9, z), "red_stained_glass")
        put(g, (2, 9, z), GLOW)
    put(g, (1, 9, 10), "glass")
    put(g, (2, 9, 10), GLOW)
    put(g, (1, 10, 10), "chiseled_quartz_block")
    for z in (9, 11):
        put(g, (1, 10, z), "quartz_block")
    # through the door: a dark passage, the far wall dark red with one lit doorway into the works
    for z in LANE_Z:                                           # the far wall: the picture's stacked red and pale bands
        for y in range(1, LANE_H + 1):
            band = "red_nether_bricks" if y % 2 == 0 else "granite"
            put(g, (7, y, z), FAM["dark"][0] if y <= 1 or z in (8, 12) else band)
    for y in (1, 2, 3):                                        # a lit doorway into the works
        put(g, (7, y, 10), "glass" if y > 1 else FAM["dark"][0])
        put(g, (8, y, 10), GLOW)
    for y in (5, 6):                                           # and the glow high in the door, as in the picture
        put(g, (7, y, 10), "glass")
        put(g, (8, y, 10), GLOW)
    # stage 2: the brass clock, framed pale
    for y in (12, 13):
        for z in (9, 10, 11):
            put(g, (1, y, z), "waxed_cut_copper")
    put(g, (1, 12, 10), "waxed_copper_block")
    # stage 3: the belfry's lit opening between pale colonnettes
    for y in (16, 17):
        put(g, (2, y, 10), "glass")
        put(g, (3, y, 10), GLOW2)
    # the spire's brass finial
    put(g, (3, 20, 10), "waxed_cut_copper")
    put(g, (3, 21, 10), "lightning_rod", facing="up")

    # ---- THE WORKS: a corbelled cap on the stack
    x0, x1, z0, z1, top = STACK
    for x in (x0, x1):
        for z in (z0, z1):
            put(g, (x, top, z), "polished_blackstone_bricks")
    return g


# ============================================================================================ level 1
L1_TOP = 11            # the highest course at L1 (12 tall)


def level1(l3):
    """The first stage on the same footprint: the arcade hall and the pavilions' walls as at L3, the hall under its plain
    roof, the pavilions under low roofs, the tower's first stage over the great door. Only cells L3 also fills."""
    g = greybox()
    gb = dict(g.b)
    g.b = {}
    for q, b in l3.b.items():
        x, y, z = q
        if y <= 6:
            g.b[q] = b
        elif in_tower(x, y, z) and y <= 10 or x in (7, 8) and 8 <= z <= 12 and y <= 8:
            g.b[q] = b                     # the tower's first stage, the lit far wall seen through the great door
        elif y <= 8 and q in gb and gb[q][0] in PAL.values():
            # roofs: the plain greybox roof (no dormers), coloured as L3 (white rakes and eaves)
            g.b[q] = b if b[0] in ROLE_OF else gb[q]
        elif x in (10, 11) and z in (0, 1) and y <= 8:
            g.b[q] = b
    # caps: the pavilions' roofs end in a ridge course of slabs; the tower's first stage in a white ledge of slabs
    for q in list(g.b):
        x, y, z = q
        if (y == 8 and (z <= 3 or z >= 17) and (x, y + 1, z) not in g.b and q in gb and x <= 6
                and shape_of(g.b[q][0]) == 0):
            g.b[q] = slab("roof")
    for x in range(0, 7):
        for z in range(5, 16):
            if (x, 10, z) in g.b and in_tower(x, 10, z) and (x, 11, z) in l3.b and (x, 11, z) not in g.b:
                rim = x in (0, 6) or z in (6, 14) or (x, 11, z) in l3.b and shape_of(l3.b[(x, 11, z)][0]) == 0
                put(g, (x, 11, z), *slab("white" if rim and (x in (0, 1, 5, 6) or z in (6, 7, 13, 14)) else "roof"))
    for x in (10, 11):
        for z in (0, 1):
            g.set(x, 8, z, "polished_blackstone_bricks")
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
