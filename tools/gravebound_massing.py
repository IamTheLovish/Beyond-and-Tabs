#!/usr/bin/env python3
"""Gravebound vehicle lab (the Charnel Works) - massing options for the blind greybox comparison (Oct 31 2026).

The r12 method (PR #411, the Engine Shed 3-4/10 -> RTS 7; PR #456, the Sunwain Court 4 -> 5.5): ONE primary reference
whose scale fits the plot, imitated closely; the silhouette settled by a blind critic on real eyes stills with the old
build as the CONTROL; detail only after. Reference: ref24 (design/refs/vehicle_labs/ref24_black_fortress_red_heart.png)
- a near-black gothic front that climbs as one pyramid to ONE central needle, two lower flanking needles, ONE glowing
heart at the centre of the front over the gate, buttress rakes, dead trees. Gravebound plays it in soul green with
pale bone as the frame; the designer pass (itachi2-api/runs/20261030-211330-gravebound-r1-design) studied it and wrote
options A, B, C (and keeps ref26's pale crag for the skirt: dark stone growing out of rock, no plinth line).

Greybox only: role blocks, no ornament. Roles: deepslate bricks the wall field, polished blackstone bricks the
dressings (tower crowns, pier caps), bone blocks the frame (gate posts, arch ring, heart ring, rib-vault, pinnacles),
Macaw's deepslate steep roofs, tuff the crag skirt, polished deepslate the paving, green stained glass with soul lanterns
behind it the heart (the one light).

Plot 17 x 17 (x 0..16 west -> east, z 0..16 north -> south; the RTS camera looks north-east from the south-west and
sees the roofs, the west face x 0 - the front - and the south face z 16). Hard rules (asserted): height <= 26 (y 0..25,
the Engine Shed T1's cap); the exit lane x 0..8, z 6..10, y 1..8 empty; the spawn column (8, y, 8) air to the sky; no
floating blocks; no sealed pockets (tools/audit_structure_pockets.py).

  A  ref24's front: a frontispiece x 1..6 (face x 1) with a steep gable, ridge along x, eaves y 12 at z 1 / 15, the
     crest y 19; the LANDMARK spire behind the heart on the lane's axis - a 5 x 5 shaft x 2..6 z 6..10 to y 19, a 3 x 3
     stage to 22, a needle to 25; flanking towers x 0..3 at z 0..3 / 13..16 to 16 with needle caps to 20 (~80 %, ref24's
     ~75 %); buttresses at x 0 z 4 / 12 with bone pinnacles; the HEART a 5 x 5 rose (corners off) in a bone ring at
     x 0, green glass a plane back at x 1, y 11..15; the gate z 6..10 y 1..8 between bone posts x 0..1 at z 5 / 11
     (y 1..10) under a bone arch ring y 9..10; the spawn well x 7..8 open to the sky; the vault hall x 9..16 z 2..14
     (walls to 9, the south wall at z 14 a plane back from the crag at z 15..16) under bone ribs at x 9 / 11 / 13 / 15,
     apex 18 at z 8.
  B  ref43's corner keep: the landmark keep at the SOUTH-WEST corner (x 0..5, z 11..16; the corner both camera faces
     see) to 20 with a steep spire to 25 and a green lancet band (the heart) at 17..19; the west range x 1..6 z 1..10
     to 12 with the gate; NW tower to 17, NE tower to 14; the rib hall x 9..16 z 3..13, apex 17.
  C  vault first, spire behind: the rib-vault over a hall x 3..14 z 2..14 (walls to 7, ribs at x 3 / 5 / 7 / 9 / 11 / 13,
     apex 19; the gap at x 8 is the spawn), a low west front x 0..2 to 10 with the gate and a 5 x 3 heart over it, the
     spire at the far north-east corner x 12..16 z 0..4 to 25, a pale crag skirt on the south edge.
  control: design/vehicle_labs/gravebound_l3.nbt (round 1, tools/lab_vehicle_gravebound.py, 4/10, 17 x 36 x 17).

    python3 tools/gravebound_massing.py          # writes design/vehicle_labs/massing/gravebound_{a,b,c}.nbt, checks
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs  # noqa: E402

SX = SZ = 17
H_MAX = 26
LANE_X, LANE_Z, LANE_H = range(0, 9), range(6, 11), 8
SPAWN = (8, 1, 8)
OUT = "design/vehicle_labs/massing/gravebound_%s.nbt"

WALL = "deepslate_bricks"
DRESS = "polished_blackstone_bricks"
DRESS_ST = "polished_blackstone_brick_stairs"
BONE = "bone_block"
ROOF = "mcwroofs:deepslate"
CORE = "deepslate_tiles"
CRAG = "tuff"
PAVE = "polished_deepslate"
GLASS = "green_stained_glass"
SOUL = "soul_lantern"
NEEDLE = "polished_blackstone_wall"
DRY = dict(waterlogged="false")
WALLP = dict(up="true", north="none", south="none", east="none", west="none", **DRY)


class Grid:
    def __init__(self):
        self.b = {}

    def set(self, x, y, z, name, **props):
        assert 0 <= x < SX and 0 <= z < SZ and 0 <= y < H_MAX, ((x, y, z), name)
        self.b[(x, y, z)] = (name, props)

    def get(self, x, y, z):
        return self.b.get((x, y, z), ("air",))[0]

    def clear(self, x, y, z):
        self.b.pop((x, y, z), None)

    def box(self, x0, x1, y0, y1, z0, z1, name, **props):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.set(x, y, z, name, **props)

    def stair(self, x, y, z, facing, half="bottom", name=DRESS_ST):
        self.set(x, y, z, name, facing=facing, half=half, shape="straight", **DRY)

    def steep(self, x, y, z, facing, shape="straight"):
        """One Macaw's steep-roof step: the lower piece at y, the upper at y + 1 (2 rise per run), rising `facing`."""
        self.set(x, y, z, ROOF + "_steep_roof", facing=facing, half="bottom", shape=shape)
        self.set(x, y + 1, z, ROOF + "_upper_steep_roof", facing=facing, half="bottom", shape=shape)


# ------------------------------------------------------------------------------------------------- shared pieces
def ring_cells(x0, x1, z0, z1):
    """The edge cells of a rectangle with (facing of the high side, stair shape) for a roof rising inward."""
    out = []
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x0 < x < x1 and z0 < z < z1:
                continue
            if x in (x0, x1) and z in (z0, z1):
                f = "east" if x == x0 else "west"
                toward = "south" if z == z0 else "north"
                right = {"east": "south", "west": "north"}[f]
                out.append(((x, z), f, "outer_right" if right == toward else "outer_left"))
            elif x in (x0, x1):
                out.append(((x, z), "east" if x == x0 else "west", "straight"))
            else:
                out.append(((x, z), "south" if z == z0 else "north", "straight"))
    return out


def cone(g, x0, x1, z0, z1, y):
    """A steep spired cap on the rectangle x0..x1 z0..z1 from y: rings of steep steps closing inward, a needle of
    blackstone wall on the last cell (or a 1-cell top); returns the top y."""
    while x1 - x0 >= 2 and z1 - z0 >= 2:
        for (x, z), f, sh in ring_cells(x0, x1, z0, z1):
            g.steep(x, y, z, f, sh)
        for x in range(x0 + 1, x1):
            for z in range(z0 + 1, z1):
                g.set(x, y, z, CORE)
                g.set(x, y + 1, z, CORE)
        x0, x1, z0, z1, y = x0 + 1, x1 - 1, z0 + 1, z1 - 1, y + 2
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            g.set(x, y, z, NEEDLE, **WALLP)
    return y


def needle(g, x, z, y0, y1, tip=NEEDLE):
    for y in range(y0, y1 + 1):
        g.set(x, y, z, tip, **WALLP)
    return y1


def edge(x0, x1, z0, z1):
    return [(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1) if x in (x0, x1) or z in (z0, z1)]


def tower(g, x0, x1, z0, z1, top, crown=True):
    """A shaft x0..x1 z0..z1 to `top` with a blackstone crown course (the top course in the dressing)."""
    g.box(x0, x1, 1, top, z0, z1, WALL)
    if crown:
        for (x, z) in edge(x0, x1, z0, z1):
            g.set(x, top, z, DRESS)
    return top


def ground(g):
    for x in range(SX):
        for z in range(SZ):
            g.set(x, 0, z, PAVE)


def crag_feet(g, cells, h=2):
    """The pale crag: the lowest courses of the outer walls in tuff (the walls grow out of the rock, ref26)."""
    for (x, z) in cells:
        for y in range(1, h + 1):
            if g.get(x, y, z) in (WALL, DRESS, CORE):
                g.set(x, y, z, CRAG)


def rib(g, x, z0, z1, y_foot, apex):
    """A bone rib across z0..z1 at x: feet at y_foot on the walls, rising to `apex` at the middle (a pointed arc drawn
    as connected columns so no bone floats)."""
    zc = (z0 + z1) / 2.0
    half = (z1 - z0) / 2.0
    ys = [int(round(y_foot + (apex - y_foot) * (1 - (abs(z - zc) / half) ** 2))) for z in range(z0, z1 + 1)]
    for i, z in enumerate(range(z0, z1 + 1)):
        lo = ys[i]
        for j in (i - 1, i + 1):            # the column joins its lower neighbour face to face
            if 0 <= j < len(ys) and ys[j] < ys[i]:
                lo = min(lo, ys[j])
        for yy in range(lo, ys[i] + 1):
            g.set(x, yy, z, BONE)


def heart_rose(g, x, zc, yc, glass_x):
    """The heart: a 5 x 5 rose minus its corners - the bone ring at x, the green glass a plane back at glass_x, soul
    lanterns behind it (the one light)."""
    for dz in range(-2, 3):
        for dy in range(-2, 3):
            if abs(dz) == 2 and abs(dy) == 2:
                continue
            if abs(dz) == 2 or abs(dy) == 2:
                g.set(x, yc + dy, zc + dz, BONE)
            else:
                if x != glass_x:
                    g.clear(x, yc + dy, zc + dz)
                g.set(glass_x, yc + dy, zc + dz, GLASS)
                g.set(glass_x + 1, yc + dy, zc + dz, SOUL if dy == -1 else CORE)


def gate(g, xs=(0, 1), zl=5, zr=11, top=10, arch_x=(0, 1)):
    """Bone posts at z zl / zr, y 1..top, the bone arch ring over the lane at y 9..10."""
    for x in xs:
        for z in (zl, zr):
            for y in range(1, top + 1):
                g.set(x, y, z, BONE)
    for x in arch_x:
        for z in range(zl + 1, zr):
            g.set(x, 9, z, BONE if z in (zl + 1, zr - 1) or x == arch_x[0] else CORE)
            g.set(x, 10, z, BONE)


def clear_lane_and_spawn(g):
    for x in LANE_X:
        for z in LANE_Z:
            for y in range(1, LANE_H + 1):
                g.clear(x, y, z)
    for y in range(1, H_MAX):
        g.clear(SPAWN[0], y, SPAWN[2])


# ------------------------------------------------------------------------------------------------- the options
def build_a():
    g = Grid()
    ground(g)
    # the frontispiece: walls x 1..6 z 1..15 to y 10; the steep gable over x 2..6 (ridge along x) from the eaves at
    # y 11 (z 1 / 15) to the crest 17 (z 4..12); the west gable end a wall at x 1 (ref24's steep front), coped
    g.box(1, 6, 1, 10, 1, 15, WALL)
    for i, (za, zb) in enumerate(((1, 15), (2, 14), (3, 13))):
        y = 11 + 2 * i
        for x in range(2, 7):
            g.steep(x, y, za, "south")
            g.steep(x, y, zb, "north")
            for z in range(za + 1, zb):
                g.set(x, y, z, CORE)
                g.set(x, y + 1, z, CORE)
        for z in (za, zb):
            g.box(1, 1, 11, y + 1, z, z, WALL)
    for x in range(2, 7):
        for z in range(4, 13):
            g.set(x, 17, z, ROOF + "_top_roof", part="switched_90")
    g.box(1, 1, 11, 17, 4, 12, WALL)
    # builder's change to the designer's A: the designer's crest (19) buried the 5 x 5 shaft (to 19), so only the
    # 3 x 3 stage and the needle showed over the roof; the crest comes down to 17 and the shaft rises 2 courses out of
    # it, the stage carries a steep cap so the landmark reads as a spire, not stacked boxes (LESSONS, pilot rounds)
    g.box(2, 6, 11, 19, 6, 10, WALL)
    for (x, z) in edge(2, 6, 6, 10):
        g.set(x, 19, z, DRESS)
    g.box(3, 5, 20, 21, 7, 9, WALL)
    cone(g, 3, 5, 7, 9, 22)
    needle(g, 4, 8, 24, 25)
    # the flanking towers x 0..3 at z 0..3 / 13..16 to 16, steep caps to 19 (~76 %, ref24's ~75 %)
    for z0 in (0, 13):
        tower(g, 0, 3, z0, z0 + 3, 16)
        cone(g, 0, 3, z0, z0 + 3, 17)
    # buttresses one plane proud at x 0, z 4 / 12, a bone pinnacle on each
    for z in (4, 12):
        g.box(0, 0, 1, 12, z, z, DRESS)
        g.set(0, 13, z, BONE)
        g.set(0, 14, z, BONE)
    # the gate: bone posts x 0..1 at z 5 / 11 to 10, the arch ring at 9..10; the heart over it
    for z in (5, 11):
        for y in range(11, 16):
            g.set(0, y, z, WALL)
    g.box(0, 0, 11, 16, 6, 10, WALL)
    gate(g)
    heart_rose(g, 0, 8, 13, 1)
    # the vault hall x 9..16 z 2..14, walls to 9, the inside a yard open to the sky; the crag at z 15..16
    for (x, z) in edge(9, 16, 2, 14):
        if x == 9 and 4 <= z <= 12:
            continue
        for y in range(1, 10):
            g.set(x, y, z, WALL)
    for (x, z) in edge(9, 16, 2, 14):
        if g.get(x, 9, z) == WALL:
            g.set(x, 9, z, DRESS)
    g.box(7, 16, 1, 1, 15, 16, CRAG)
    g.box(9, 16, 2, 2, 15, 15, CRAG)
    for x in (9, 11, 13, 15):
        rib(g, x, 2, 14, 10, 18)
    # the well's side walls x 7..8 (north z 2..5, south z 11..14) low, linking frontispiece and hall
    for z in list(range(1, 6)) + list(range(11, 16)):
        for x in (7, 8):
            for y in range(1, 8):
                g.set(x, y, z, WALL)
    crag_feet(g, [(0, z) for z in range(SZ)] + [(x, 16) for x in range(SX)] + [(1, z) for z in range(SZ)])
    clear_lane_and_spawn(g)
    return g, dict(landmark=(4, 8), top=25)


def build_b():
    g = Grid()
    ground(g)
    # the west range x 1..6 z 1..10 to 12, a steep lean-to roof rising east
    g.box(1, 6, 1, 11, 1, 10, WALL)
    for x, y in ((1, 12), (2, 14)):
        for z in range(1, 11):
            g.steep(x, y, z, "east")
    g.box(3, 6, 12, 15, 1, 10, CORE)
    for x in range(3, 7):
        for z in range(1, 11):
            g.set(x, 16, z, ROOF + "_top_roof", part="switched_90") if x == 3 else None
    # the landmark keep at the SW corner x 0..5 z 11..16 to 20, the steep spire to 25, the green lancet band 17..19
    tower(g, 0, 5, 11, 16, 20)
    for y in (17, 18, 19):
        for z in (12, 14):
            g.set(0, y, z, GLASS)
            g.set(1, y, z, SOUL if y == 17 else CORE)
        for x in (2, 4):
            g.set(x, y, 16, GLASS)
            g.set(x, y, 15, SOUL if y == 17 else CORE)
    cone(g, 0, 5, 11, 16, 21)
    # the NW tower x 0..3 z 0..3 to 14 + a cone to 17; the NE tower x 13..16 z 0..3 to 11 + cone to 14
    tower(g, 0, 3, 0, 3, 13)
    cone(g, 0, 3, 0, 3, 14)
    tower(g, 13, 16, 0, 3, 10)
    cone(g, 13, 16, 0, 3, 11)
    # the gate in the west range
    gate(g, zl=5, zr=11)
    # the rib hall x 9..16 z 3..13, walls to 9, ribs x 9..15 apex 17
    for (x, z) in edge(9, 16, 3, 13):
        if x == 9 and 5 <= z <= 11:
            continue
        if g.get(x, 1, z) != "air":
            continue
        for y in range(1, 10):
            g.set(x, y, z, WALL)
    for x in (9, 11, 13, 15):
        rib(g, x, 3, 13, 10, 17)
    for z in list(range(1, 6)) + list(range(11, 14)):
        for x in (7, 8):
            for y in range(1, 8):
                g.set(x, y, z, WALL)
    g.box(6, 16, 1, 1, 14, 16, CRAG)
    crag_feet(g, [(0, z) for z in range(SZ)] + [(x, 16) for x in range(SX)])
    clear_lane_and_spawn(g)
    return g, dict(landmark=(2, 13), top=25)


def build_c():
    g = Grid()
    ground(g)
    # the hall x 3..14 z 2..14, walls to 7 (the lane runs through its west end); the rib-vault over it
    for (x, z) in edge(3, 14, 2, 14):
        for y in range(1, 8):
            g.set(x, y, z, WALL)
    for (x, z) in edge(3, 14, 2, 14):
        g.set(x, 7, z, DRESS)
    for x in (3, 5, 7, 9, 11, 13):
        rib(g, x, 2, 14, 8, 19)
    # the low west front x 0..2 z 2..14 to 10, the gate and a 5 x 3 heart at 11..13 in a gable to 14
    g.box(0, 2, 1, 10, 2, 14, WALL)
    for y in range(11, 15):
        g.box(0, 2, y, y, 2 + (y - 10), 14 - (y - 10), WALL)
    gate(g)
    for z in range(6, 11):
        for y in range(11, 14):
            if z in (6, 10) or y == 11 and z in (7, 9):
                g.set(0, y, z, BONE)
            else:
                g.set(0, y, z, GLASS)
                g.set(1, y, z, SOUL if y == 12 else CORE)
    # the spire at the far NE corner x 12..16 z 0..4: shaft to 17, cone to 25
    tower(g, 12, 16, 0, 4, 17)
    cone(g, 12, 16, 0, 4, 18)
    needle(g, 14, 2, 23, 25)
    # the pale crag skirt on the south edge, stepping up toward the hall
    g.box(0, 16, 1, 2, 15, 16, CRAG)
    g.box(0, 16, 3, 3, 16, 16, CRAG)
    crag_feet(g, [(x, 14) for x in range(SX)] + [(0, z) for z in range(SZ)], 3)
    clear_lane_and_spawn(g)
    return g, dict(landmark=(14, 2), top=25)


BUILDERS = {"a": build_a, "b": build_b, "c": build_c}


# ------------------------------------------------------------------------------------------------- write + check
def write(g, out):
    h = max(y for (_, y, _) in g.b) + 1
    assert h <= H_MAX, "%s is %d tall" % (out, h)
    rig = gs._SunRig(SX, h, SZ)
    for (x, y, z), (n, props) in g.b.items():
        rig.set(x, y, z, n, **props)
    rig.settle()
    os.makedirs(os.path.dirname(out), exist_ok=True)
    rig.structure().write(out)
    return rig


def floating(n):
    todo = [q for q in n if q[1] == 0]
    seen = set(todo)
    while todo:
        x, y, z = todo.pop()
        for a, b, c in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            q = (x + a, y + b, z + c)
            if q in n and q not in seen:
                seen.add(q)
                todo.append(q)
    return sorted(q for q in n if q not in seen)


def check(rig, out, meta):
    import audit_structure_pockets as ap
    name = os.path.splitext(os.path.basename(out))[0]
    n = {q: b[0] for q, b in rig.b.items()}
    assert rig.size[1] <= H_MAX, (name, rig.size)
    assert all((x, 0, z) in n for x in range(SX) for z in range(SZ)), "a hole in the foundation"
    busy = [(x, y, z) for x in LANE_X for z in LANE_Z for y in range(1, LANE_H + 1) if (x, y, z) in n]
    assert not busy, "the exit lane is narrowed: %s" % busy[:8]
    col = [y for y in range(1, rig.size[1]) if (SPAWN[0], y, SPAWN[2]) in n]
    assert not col, "the spawn column is blocked at %s" % col
    assert not floating(n), "floating %s" % floating(n)[:6]
    one_way, ground_, roof, rooms, note = ap.audit(name, out)
    assert not one_way and not ground_ and "TRAPPED" not in note, (one_way[:8], ground_[:8], note)
    top = {}
    for (x, y, z) in n:
        top[(x, z)] = max(top.get((x, z), -1), y)
    lm, h = meta["landmark"], max(top.values())
    rest = max(y for c, y in top.items() if max(abs(c[0] - lm[0]), abs(c[1] - lm[1])) > 3)
    return "%s %s, %d blocks, no pockets%s | landmark top %d, next outside its zone %d (lead %d)" % (
        name, "x".join(map(str, rig.size)), len(n), note, h, rest, h - rest)


def main(argv):
    for o in (argv or sorted(BUILDERS)):
        g, meta = BUILDERS[o]()
        out = OUT % o
        rig = write(g, out)
        print(check(rig, out, meta))


if __name__ == "__main__":
    main(sys.argv[1:])
