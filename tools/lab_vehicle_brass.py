#!/usr/bin/env python3
"""CONCEPT (pilot): the Brassworks ENGINE SHED, imitating ONE picture - lovish's ref18
(design/refs/vehicle_labs/ref18_brick_hall_central_tower.png). Tier 1, so small (lovish, Oct 28 04:32): reserved
13 deep x 21 long, L3 at most 22 tall, L1 at most 12.

STAGE: GREYBOX (lovish, 04:36: a greybox he approves before any detail). This script writes the L3 MASSING only, in
plain blocks - stone walls, deepslate roofs, obsidian openings: design/vehicle_labs/brass_l3_greybox.nbt. The detailed
L3, the L1 cut from it and the renders come after his OK.

THE PICTURE, STUDIED (seen from the front, low, dead centre; image 230 x 180 px, ground line ~y 152, sky above)
  - SILHOUETTE, measured against the sky: a long flat band of hall across the whole frame (roof top ~y 95), two END
    PAVILIONS at the frame edges rising to ~y 68 under steep roofs, ONE central TOWER rising to a point at ~y 10.
    Heights as fractions of the tower: hall cornice 0.25, hall roof top 0.40, pavilion peaks 0.59, tower 1.0.
    The tower is a STEPPED CONE, not a shaft with a cap: ~0.39 of its height wide where it leaves the hall roof,
    narrowing about one block a side every three courses to a single finial (widths at 0.45 / 0.62 / 0.70 / 0.76 /
    0.82 / 0.88 / 0.94 of its height: 8.5 / 6.2 / 5.4 / 4.7 / 3.9 / 3.1 / 1.5 blocks at a 22-block tower).
  - RHYTHM: the arcade bays are ~2 blocks apart at that scale; each side of the tower the hall runs ~8 blocks (about
    the tower's width) before the pavilions, which the frame cuts. At 21 long the picture is compressed ~0.6 along
    the front: tower 7, hall 4 a side, pavilions 3 - keeping the 2-block bay rhythm.
  - THE GROUND STOREY (y 117..152): a dark ARCADE - narrow round-headed openings (black, some with red doors) between
    red piers, a row of small white lights at impost height (~y 125), white stone at the pier feet. At squint it is
    the dark band along the bottom; it runs on under the pavilions.
  - THE UPPER STOREY / EAVES (y 100..117): a thin dark band of small windows under the eaves.
  - THE HALL ROOF (y 95..117): bright red, broken into a grid of DORMERS - red caps framed in white, one over each
    arcade bay; the white frames make vertical stripes down the pitch.
  - THE END PAVILIONS: square-ish, taller than the hall, white banded, a red cross motif on the face, steep stepped
    red roofs edged in white rising to a point.
  - THE TOWER: stage 1 (y 95..152) the full hall height, the GREAT DOOR at its foot glowing warm, a lit window above
    it, flanking dark buttress piers with white quoins; stage 2 (y 60..95) a dark band with a round white-red CLOCK;
    stage 3 (y 35..60) a grid of small openings in white frames; the SPIRE (y 10..35) red with white edge ribs; WHITE
    LINES climb every arris and every set-back ledge.
  - COLOUR: red brick and red roof, white stone banding, dark recesses; nothing else. Calm green lawn in front.

ANCHORS (structure-local block coords x, y, z; x 0 = the west front, the RTS camera's face; z 0 = north):
  spawn cell       (6, 1, 10)      the hall floor; the column (6, 1..top, 10) is air (the roof's vent slot over it)
  exit lane        x 0..6, z 8..12, y 1..8 - from the spawn straight out the great door in the tower's west face
  great door       the tower's west face, x 0, z 8..12, its round head at y 9..10
  clock            (0, 12..14, 9..11)  the tower's west face, stage 2 (detail stage)
  crane / stack    detail stage (the crane under the vent slot, the stack at the north pavilion's back)

THE MASSING (y 0 = ground course; the greybox's front elevation matches the picture's measured silhouette:
roof top 0.45 of the tower, pavilion peaks 0.59, tower base 0.32 of its height wide):
  - hall        x 2..12, the whole length z 0..20; an arcade proud at x 1 with openings at z 4, 6, 14, 16 (piers
                between), a band of upper windows at y 6; walls to y 6; roof 1-in-2 from the eaves to the ridge (x 7,
                y 9 + a slab); the vent slot x 6..8, z 8..12 over the spawn; a dormer over each hall bay (x 3..5).
                The south gable (the camera's right-hand face) carries the same arch order.
  - pavilions   x 1..5 on the front corners, z 0..2 and z 18..20; walls to y 8 with two-storey openings on the
                west and end faces; steep hipped roofs (2 courses a ring) to a finial post at y 13.
  - tower       a stepped cone on z 7..13: stage 1 x 0..5 y 1..10 (the great door, 5 x 8, a round head at y 9..10);
                stage 2 in at the sides (z 8..12, y 11..12) then at the front (x 1..5, y 13..15, the clock); stage 3
                z 9..11 (y 16..17, the belfry), then x 2..4 (y 18..19); a ring of stairs on every set-back (the white
                lines), the needle at (3, 10) to a finial at y 21. It stays west of the spawn column.
  - Iterations (greybox squint vs ref18): 1 a pyramid of boxes with a cap read as a castle keep -> the tower became a
    cone stepping in 1 block every 2 courses; 2 the pavilions spanned the full depth and read as a second block ->
    5-deep corner towers; 3 the hall was too tall against the tower -> walls 7 -> 6, ridge 11 -> 9; 4 the arcade
    stopped at the pavilions -> it runs the whole front; 5 the gate read as a light slot -> a dark far wall.

Usage: python3 tools/lab_vehicle_brass.py   (writes design/vehicle_labs/brass_l3_greybox.nbt and checks it)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs           # noqa: E402
from gothic_parts import Part          # noqa: E402

SX, SZ = 13, 21
H3 = 22
SPAWN = (6, 1, 10)
LANE_Z = range(8, 13)
LANE_X = range(0, 7)
LANE_H = 8
OUT = "design/vehicle_labs"

GREY = {
    "wall": "stone", "trim": "stone", "void": "obsidian", "ground": "stone", "lawn": "grass_block",
    "path": "stone", "trim_wall": "stone_brick_wall", "roof": "deepslate_tiles", "roof_st": "deepslate_tile_stairs", "roof_slab": "deepslate_tile_slab",
}
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}

HALL_Z = (0, 20)                   # the hall body runs the whole length; the pavilions stand on its front corners
PAVS = ((0, 2), (18, 20))          # the end pavilions: 3 along the front x 5 deep (x 1..5), steep hipped roofs
PAV_X = (1, 5)
WALL_TOP = 6                       # the hall's top wall course (the picture: cornice at 0.25 of the tower)
RIDGE_X = 7
VENT = (6, 8, 8, 12)               # x0 x1 z0 z1: the open slot over the spawn
PAV_TOP = 8                        # the pavilions' top wall course; their roofs peak at y 12-13 (0.6 of the tower)
TOWER_Z = (7, 13)
ARCH_Z = [1, 4, 6, 14, 16, 19]     # the arcade's openings along the whole front, a pier between each
DORMER_Z = [4, 6, 14, 16]          # one over each hall bay
# the tower's stepped cone (the picture: one block in a side every three or four courses, its front stepping back
# too): (x0, x1, z0, z1, y0, y1); then the needle
STAGES = [(0, 5, 7, 13, 1, 10),     # stage 1: the great door, to a course over the hall's ridge
          (0, 5, 8, 12, 11, 12),    # stage 2 (the clock): in at the sides ...
          (1, 5, 8, 12, 13, 15),    # ... then at the front
          (1, 5, 9, 11, 16, 17),    # stage 3 (the belfry)
          (2, 4, 9, 11, 18, 19)]    # the spire's foot; a ring of stairs round the needle at y 20
SPIRE = (3, 10, 20, 21)            # x, z, y0, y1 (the top course a finial post)


def new(pal):
    return Part(dict(pal))


def roof_h(x):
    """The hall roof's stair course at x: 1-in-2 from the eaves (x 2, y 7) to the ridge (x 7, y 9 + a slab) - the
    picture's roof top at 0.40-0.45 of the tower."""
    return {0: 9, 1: 9, 2: 8, 3: 8, 4: 7, 5: 7}[abs(x - RIDGE_X)]


def hip(p, x0, x1, z0, z1, y, rise=2, finial=True):
    """A hipped roof on the rectangle, each ring `rise` courses above the last: the ring's stairs face inward on top of
    a column that reaches down to the course of the ring outside it (so every ring rests on the one below)."""
    k = 0
    while x0 + k <= x1 - k and z0 + k <= z1 - k:
        xa, xb, za, zb = x0 + k, x1 - k, z0 + k, z1 - k
        top = y + rise * k + rise - 1
        base = y + rise * k - (1 if k else 0)
        cells = [(x, z) for x in range(xa, xb + 1) for z in range(za, zb + 1)
                 if x in (xa, xb) or z in (za, zb)]
        for x, z in cells:
            for yy in range(base, top):
                p.set(x, yy, z, "roof")
            if xa == xb and za == zb:
                p.set(x, top, z, "roof")
                if finial:
                    p.set(x, top + 1, z, "trim_wall")
            elif za == zb or xa == xb:              # the ridge line
                long_x = za == zb
                if long_x and x in (xa, xb):
                    p.stair(x, top, z, "east" if x == xa else "west", "bottom", "roof_st")
                elif not long_x and z in (za, zb):
                    p.stair(x, top, z, "south" if z == za else "north", "bottom", "roof_st")
                else:
                    p.set(x, top, z, "roof")
                    p.slab(x, top + 1, z, "bottom", "roof_slab")
            else:
                f = "east" if x == xa else "west" if x == xb else "south" if z == za else "north"
                p.stair(x, top, z, f, "bottom", "roof_st")
        k += 1


def ground(p):
    for x in range(SX):
        for z in range(SZ):
            p.set(x, 0, z, "ground")
    for z in range(SZ):
        p.set(0, 0, z, "path" if LANE_Z[0] <= z <= LANE_Z[-1] else "lawn")


def arch(p, x, z, top=3):
    """An arcade opening on the face at x: a black recess y 1..top under a black round head at top + 1."""
    for y in range(1, top + 2):
        p.set(x, y, z, "void")


def in_pav(x, z):
    return PAV_X[0] <= x <= PAV_X[1] and any(z0 <= z <= z1 for z0, z1 in PAVS)


def hall(p):
    z0, z1 = HALL_Z
    for z in range(z0, z1 + 1):
        for y in range(1, WALL_TOP + 1):
            p.set(12, y, z, "wall")
            if not in_pav(2, z):
                p.set(2, y, z, "wall")
        if in_pav(1, z) or TOWER_Z[0] <= z <= TOWER_Z[1]:
            continue
        # the arcade: piers proud at x 1 to the string course (y 5); the openings are recesses in the wall at x 2
        for y in range(1, 5):
            p.set(1, y, z, "wall")
        if z in ARCH_Z:
            for y in range(1, 5):
                p.set(1, y, z, "air")
            arch(p, 2, z)
        p.set(2, 6, z, "void")                         # the upper storey's band of windows
        p.stair(1, 5, z, "east", "bottom", "roof_st")  # the arcade's coping / string course
    # the gable ends (the south one faces the camera): arches on the same order
    for z in (z0, z1):
        for x in range(PAV_X[1] + 1, 12):
            for y in range(1, roof_h(x)):
                p.set(x, y, z, "wall")
        for x in (7, 9, 11):
            arch(p, x, z)
            p.set(x, 6, z, "void")
    # the roof
    for x in range(2, 13):
        top = roof_h(x)
        out = roof_h(x - 1) if x > RIDGE_X else roof_h(x + 1) if x < RIDGE_X else top
        for z in range(z0, z1 + 1):
            if VENT[0] <= x <= VENT[1] and VENT[2] <= z <= VENT[3] or in_pav(x, z):
                continue
            for yy in range(WALL_TOP + 1, top):
                if x in (2, 12) or yy >= min(top, out) - 1 or z in (z0, z1):
                    p.set(x, yy, z, "roof")
            if x == RIDGE_X:
                p.set(x, top, z, "roof")
                p.slab(x, top + 1, z, "bottom", "roof_slab")
            else:
                p.stair(x, top, z, "east" if x < RIDGE_X else "west", "bottom", "roof_st")
    # dormers on the west pitch between pavilion and tower: a light at x 3 under a little pitched cap
    for z in DORMER_Z:
        p.set(3, 8, z, "void")
        p.set(4, 8, z, "wall")
        p.stair(3, 9, z, "east", "bottom", "roof_st")
        p.set(4, 9, z, "roof")
        p.slab(5, 9, z, "bottom", "roof_slab")


def pavilion(p, z0, z1):
    """An end pavilion on the hall's front corner: 5 x 5, its west face flush with the arcade piers (x 1), walls to
    y 7, a steep pyramid (2 courses a ring) to a finial - the picture's pavilions, taller than the hall, well under
    the tower."""
    xa, xb = PAV_X
    for x in range(xa, xb + 1):
        for z in range(z0, z1 + 1):
            for y in range(1, PAV_TOP + 1):
                if x in (xa, xb) or z in (z0, z1) or y <= 2:
                    p.set(x, y, z, "wall")
    zf = z0 if z0 == 0 else z1
    for z in ARCH_Z:
        if z0 <= z <= z1:
            arch(p, xa, z)
            p.set(xa, 6, z, "void")
    for x in (xa + 1, xb - 1):
        arch(p, x, zf)
        for y in (6, 7):
            p.set(x, y, zf, "void")
    for z in ARCH_Z:
        if z0 <= z <= z1:
            p.set(xa, 7, z, "void")
    hip(p, xa, xb, z0, z1, PAV_TOP + 1)


def tower(p):
    """The stepped cone: each stage one block in a side (the front too), a ring of stairs on every set-back (the
    picture's white lines climbing the tower), a needle on top. Its back stays west of the spawn column (x 6)."""
    for (x0, x1, z0, z1, y0, y1) in STAGES:
        p.fill(x0, x1, y0, y1, z0, z1, "wall")
    for (x0, x1, z0, z1, y0, y1) in STAGES:
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if (x in (x0, x1) or z in (z0, z1)) and p.get(x, y1 + 1, z) == "air":
                    f = "south" if z == z0 else "north" if z == z1 else "east" if x == x0 else "west"
                    p.stair(x, y1 + 1, z, f, "bottom", "roof_st")
    x, z, y0, y1 = SPIRE
    for y in range(y0, y1):
        p.set(x, y, z, "roof")
    p.set(x, y1, z, "trim_wall")
    # the buttress piers flanking the door: proud of the tower face at x -0 is off the plot, so they rise as the
    # tower's own z 7 / z 13 columns, capped by stairs
    # the great door: the lane cut through stage 1, a round head (black) over it
    for x in LANE_X:
        for z in LANE_Z:
            for y in range(1, LANE_H + 1):
                p.set(x, y, z, "air")
    for z in (9, 10, 11):
        p.set(0, 9, z, "void")
    p.set(0, 10, 10, "void")
    # the clock (stage 2) and the belfry (stage 3): one dark opening each
    p.set(0, 12, 10, "void")
    p.set(1, 14, 10, "void")
    p.set(1, 16, 10, "void")
    p.set(1, 17, 10, "void")
    # the hall's far wall seen through the door: dark, so the gate reads as an opening (the detail stage lights it)
    for z in LANE_Z:
        for y in range(1, WALL_TOP + 1):
            p.set(12, y, z, "void")


def clear_lane(p):
    for x in LANE_X:
        for z in LANE_Z:
            for y in range(1, LANE_H + 1):
                p.clear(x, y, z)
    for y in range(1, H3 + 2):
        p.clear(SPAWN[0], y, SPAWN[2])


def greybox(p):
    ground(p)
    hall(p)
    for z0, z1 in PAVS:
        pavilion(p, z0, z1)
    tower(p)
    clear_lane(p)


# ============================================================================================ output and checks
def to_rig(p, h, tag):
    rig = gs._SunRig(SX, h, SZ)
    for (x, y, z), (n, props) in p.b.items():
        if n in ("air", "cave_air") or not (0 <= x < SX and 0 <= z < SZ and 0 <= y < h):
            continue
        rig.set(x, y, z, n, **props)
    rig.bury_grass()
    rig.settle()
    rig.check_support("engine shed " + tag)
    return rig


def floating(rig):
    n = rig.b
    return [(q, n[q][0]) for q in n if q[1] > 0 and not any(
        (q[0] + a, q[1] + b, q[2] + c) in n for a, b, c in
        ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)))]


def check(rig, path, tag):
    import audit_structure_pockets as ap
    n = {q: b[0] for q, b in rig.b.items()}
    assert all((x, 0, z) in n for x in range(SX) for z in range(SZ)), "%s: a hole in the foundation" % tag
    busy = [(x, y, z, n[(x, y, z)]) for x in LANE_X for z in LANE_Z for y in range(1, LANE_H + 1) if (x, y, z) in n]
    assert not busy, "%s: the exit lane is narrowed: %s" % (tag, busy[:8])
    lone = floating(rig)
    assert not lone, "%s: floating %s" % (tag, lone[:6])
    col = [y for y in range(1, rig.size[1]) if (SPAWN[0], y, SPAWN[2]) in n]
    assert not col, "%s: the spawn column is blocked at %s" % (tag, col)
    one_way, ground_, roof, rooms, note = ap.audit(tag, path)
    assert not one_way and not ground_ and "TRAPPED" not in note, (tag, one_way[:8], ground_[:8], note)
    return "%s %dx%dx%d: %d blocks, lane clear, spawn column open, no pockets (%d roof-only)" % (
        tag, SX, rig.size[1], SZ, len(n), len(roof))


def build(fn, cap, tag, pal):
    p = new(pal)
    fn(p)
    h = max(y for (x, y, z), v in p.b.items() if v[0] not in ("air", "cave_air") and 0 <= x < SX and 0 <= z < SZ) + 1
    assert h <= cap, "%s is %d tall" % (tag, h)
    return to_rig(p, h, tag)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    rg = build(greybox, H3, "L3 greybox", GREY)
    pg = os.path.join(OUT, "brass_l3_greybox.nbt")
    rg.structure().write(pg)
    print(check(rg, pg, "brass_l3_greybox"))
