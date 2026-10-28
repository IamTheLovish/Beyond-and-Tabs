#!/usr/bin/env python3
"""PILOT (Oct 28): the Brassworks VEHICLE LAB as an ENGINE SHED imitating ONE picture, lovish's ref18
(design/refs/vehicle_labs/ref18_brick_hall_central_tower.png). Brief: design/vehicle_labs/briefs/brass_pilot.md with
its "Changes sent to the builder" (T1 footprint x 0..12, z 0..20, spawn (6, 10), exit lane z 8..12 x 8 tall out the
WEST face, L3 <= 22 tall, L1 <= 12). THIS STAGE IS THE GREYBOX ONLY: massing in one wall tone, one roof tone and black
openings, matched to the reference's squint before any detail (lovish's OK comes first).

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

Usage: python3 tools/lab_vehicle_brass.py   (writes design/vehicle_labs/brass_l3_greybox.nbt and checks it)
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


# ============================================================================================ output and checks
def write(g):
    h = max(y for (_, y, _) in g.b) + 1
    assert h <= H_MAX, "greybox is %d tall" % h
    rig = gs._SunRig(SX, h, SZ)
    for (x, y, z), (n, props) in g.b.items():
        rig.set(x, y, z, n, **props)
    rig.bury_grass()
    rig.settle()
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    rig.structure().write(OUT)
    return rig


def check(rig):
    import audit_structure_pockets as ap
    n = {q: b[0] for q, b in rig.b.items()}
    assert all((x, 0, z) in n for x in range(SX) for z in range(SZ)), "a hole in the foundation"
    busy = [(x, y, z) for x in LANE_X for z in LANE_Z for y in range(1, LANE_H + 1) if (x, y, z) in n]
    assert not busy, "the exit lane is narrowed: %s" % busy[:8]
    col = [y for y in range(1, rig.size[1]) if (SPAWN[0], y, SPAWN[2]) in n]
    assert not col, "the spawn column is blocked at %s" % col
    lone = [q for q in n if q[1] > 0 and not any((q[0] + a, q[1] + b, q[2] + c) in n for a, b, c in
                                                   ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)))]
    assert not lone, "floating %s" % lone[:6]
    one_way, ground_, roof, rooms, note = ap.audit("brass_l3_greybox", OUT)
    assert not one_way and not ground_ and "TRAPPED" not in note, (one_way[:8], ground_[:8], note)
    return "brass_l3_greybox %dx%dx%d: %d blocks, lane clear, spawn column open, no pockets (%d roof-only)" % (
        SX, rig.size[1], SZ, len(n), len(roof))


if __name__ == "__main__":
    print(check(write(greybox())))
