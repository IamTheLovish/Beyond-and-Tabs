#!/usr/bin/env python3
"""Verdant vehicle lab (Rooting Hollow) - massing options for the blind greybox comparison (Oct 31 2026).

The method that took the Engine Shed from 3-4/10 to RTS 7 (PR #411) and the Sunforged lab from 4 to 5.5 (PR #456):
ONE primary reference whose scale fits the plot, imitated closely; the silhouette settled by a blind critic on real
eyes stills with the old build as the CONTROL; detail only after. Reference: ref06 (design/refs/vehicle_labs/
ref06_tree_hall.png) - a colossal twisted tree whose trunk spirals and leans, wrapping a steep-roofed timber hall into
itself; roots flaring like buttresses; a huge layered canopy as THE landmark; a sky crescent between the limb and the
hall. The designer pass (API run itachi2-api/runs/20261030-204240-verdant-design) studied it and wrote options A, B, C.

Greybox only: role blocks, no ornament. Roles: mossy stone bricks the stone storeys and galleries, spruce planks the
timber storey, Macaw's spruce steep roof (0.39 on camera), stripped oak wood the trunk with dark oak wood in its spiral
grooves (the designer: "light tan with dark grooves"; dark oak alone, 0.17-0.27 on camera, sinks into the ground's
darks), dark oak wood + mangrove roots the buttress roots, the canopy in tiers (azalea bellies, cherry tops - the
designer's muted answer to ref06's red, which is team-colour territory), ONE shroomlight heart-glow at the trunk's foot.

Plot 17 x 17 (x 0..16 west -> east, z 0..16 north -> south; the RTS camera looks north-east from the south-west and
sees the tops, the west face x 0 - the front - and the south face z 16). Hard rules (asserted): height <= 26
(y 0..25); the exit lane x 0..8, z 6..10, y 1..8 empty; the spawn column (8, y, 8) air to the sky; no floating blocks;
no sealed pockets (tools/audit_structure_pockets.py).

  A  the wrapped hall (the designer's pick): a steep-gabled timber-on-stone hall on the west (x 1..7, z 3..13, ridge
     along x at z 8, y 20) with the gate in its west gable; a clinging stone tower; the south gallery (the control's,
     kept); the trunk rooted in the south-east (13, 11) and leaning north-west round the spawn light well, whose
     open column is ref06's crescent; a west limb over the hall's north slope; three canopy tiers to y 25.
  B  the court tree: a gatehouse on the west (x 0..4, ridge along z), galleries north and south of an open court, a
     near-vertical tree at (13, 8) with a symmetric umbrella canopy (risk: a lollipop).
  C  the corner tree over a gallery ring: the control's galleries round a cloister court, two gate towers with steep
     roofs, the trunk in the north-east corner, canopy draping into the court (ref03).
  control: design/vehicle_labs/verdant_l3.nbt (tools/lab_vehicle_verdant.py, 3/10 from round 1's critic).

    python3 tools/verdant_massing.py          # writes design/vehicle_labs/massing/verdant_{a,b,c}.nbt and checks them
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs  # noqa: E402
import mex_verdant as mv  # noqa: E402
from gothic_parts import hsh  # noqa: E402

SX = SZ = 17
H_MAX = 26
LANE_X, LANE_Z, LANE_H = range(0, 9), range(6, 11), 8
SPAWN = (8, 1, 8)
WELL = {(x, z) for x in (7, 8, 9) for z in (7, 8, 9)}     # the light well: canopy tiers stay out of it
OUT = "design/vehicle_labs/massing/verdant_%s.nbt"

PAVE = "mossy_stone_bricks"
STONE = "mossy_stone_bricks"
HEM = "deepslate_bricks"
DRESS = "stone_bricks"
DRESS_ST = "stone_brick_stairs"
TIMBER = "spruce_planks"
ROOF = "mcwroofs:spruce_planks"
GALLERY_TOP = "mossy_stone_brick_slab"
TRUNK = "stripped_oak_wood"
GROOVE = "dark_oak_wood"
ROOT = "dark_oak_wood"
GRIP = "mangrove_roots"
UNDER, MID, TOP = "azalea_leaves", "azalea_leaves", "cherry_leaves"
GLOW = "shroomlight"
LEAF = dict(persistent="true", distance="7", waterlogged="false")
DRY = dict(waterlogged="false")
TREE = ("_wood", "_log", "_leaves", "mangrove_roots", "shroomlight")


class Grid:
    def __init__(self):
        self.b = {}

    def ok(self, x, y, z):
        return 0 <= x < SX and 0 <= z < SZ and 0 <= y < H_MAX and not (
            x in LANE_X and z in LANE_Z and 1 <= y <= LANE_H) and not ((x, z) == (SPAWN[0], SPAWN[2]) and y >= 1)

    def set(self, x, y, z, name, **props):
        if self.ok(x, y, z):
            self.b[(x, y, z)] = (name, props)

    def put(self, x, y, z, name, **props):
        """Set only where nothing stands yet (the tree grows round what is there)."""
        if (x, y, z) not in self.b:
            self.set(x, y, z, name, **props)

    def get(self, x, y, z):
        return self.b.get((x, y, z), ("air",))[0]

    def clear(self, x, y, z):
        self.b.pop((x, y, z), None)

    def box(self, x0, x1, y0, y1, z0, z1, name, **props):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.set(x, y, z, name, **props)

    def stair(self, x, y, z, facing, half, name=DRESS_ST):
        self.set(x, y, z, name, facing=facing, half=half, shape="straight", **DRY)


# ------------------------------------------------------------------------------------------------- shared pieces
def ground(g):
    for x in range(SX):
        for z in range(SZ):
            g.set(x, 0, z, PAVE)


def trunk(g, path, twist=0.9):
    """A solid trunk: path = [(y, cx, cz, r)]; the shell in stripped oak with dark oak in two spiral grooves that turn
    with height (ref06's twist)."""
    cells = {}
    for (y, cx, cz, r) in path:
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            for z in range(int(cz - r - 1), int(cz + r + 2)):
                if math.hypot(x - cx, z - cz) <= r:
                    cells[(x, y, z)] = (cx, cz)
    for (x, y, z), (cx, cz) in cells.items():
        a = math.atan2(z - cz, x - cx) / (2 * math.pi) * 2 + y * twist / (2 * math.pi)
        g.set(x, y, z, GROOVE if (a % 1.0) < 0.28 else TRUNK)
    return cells


def line(g, p0, p1, name=ROOT, fill_down=False, grip=True):
    """A limb or a buttress root: a face-connected line of wood from p0 to p1; fill_down drops a root wall under its low
    half so it reads as a buttress, not a pipe; mangrove roots where it grips the ground."""
    (x0, y0, z0), (x1, y1, z1) = p0, p1
    n = max(abs(x1 - x0), abs(z1 - z0), abs(y1 - y0), 1)
    prev = None
    for k in range(n + 1):
        t = k / n
        q = (round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * (t ** 0.7 if fill_down else t)),
             round(z0 + (z1 - z0) * t))
        steps = [q]
        if prev is not None:
            a, steps = list(prev), []
            for i in (0, 2, 1):
                if a[i] != q[i]:
                    a[i] = q[i]
                    steps.append(tuple(a))
        for s in steps:
            g.set(*s, name)
            if fill_down and t > 0.5:
                for yy in range(1, s[1]):
                    g.put(s[0], yy, s[2], GRIP if grip and yy == 1 else name)
        prev = q


def canopy(g, cx, cz, y0, y1, r, seed=0, top=TOP, keep=lambda x, y, z: True):
    """One canopy tier: an irregular disc from y0 to y1, the belly azalea, the top layer cherry; the top steps in."""
    rr = int(r) + 2
    for x in range(int(cx) - rr, int(cx) + rr + 1):
        for z in range(int(cz) - rr, int(cz) + rr + 1):
            if (x, z) in WELL:
                continue
            d = math.hypot(x - cx, z - cz)
            wob = 0.7 * math.sin(math.atan2(z - cz, x - cx) * 3 + seed) + (hsh(x, z, seed) % 3 - 1) * 0.3
            for y in range(y0, y1 + 1):
                rim = r + wob - (1.2 if y == y1 else 0)
                if d > rim or not keep(x, y, z):
                    continue
                nm = top if y == y1 else (UNDER if y == y0 else MID)
                if y == y1 - 1 and d > rim - 1.0:
                    nm = top
                g.put(x, y, z, nm, **LEAF)


def steep_gable_x(g, x0, x1, z0, z1, y0, name=ROOF):
    """A steep gable roof with its ridge along x (Macaw's steep pieces: 2 rise per run), slopes facing north and south;
    the gable triangles at x0 and x1 filled with the wall below's timber. Returns the ridge height."""
    runs = (z1 - z0) // 2
    for k in range(runs):
        y = y0 + 2 * k
        for x in range(x0, x1 + 1):
            for z, f in ((z0 + k, "south"), (z1 - k, "north")):
                g.set(x, y, z, name + "_steep_roof", facing=f, half="bottom", shape="straight")
                g.set(x, y + 1, z, name + "_upper_steep_roof", facing=f, half="bottom", shape="straight")
        for x in (x0, x1):
            for z in range(z0 + k + 1, z1 - k):
                for yy in (y, y + 1):
                    g.set(x, yy, z, TIMBER)
    ridge = y0 + 2 * runs
    for x in range(x0, x1 + 1):
        for z in range(z0 + runs, z1 - runs + 1):
            g.set(x, ridge - 2, z, TIMBER)
            g.set(x, ridge - 1, z, TIMBER)
            g.set(x, ridge, z, "spruce_slab", type="bottom", **DRY)
    return ridge


def steep_gable_z(g, x0, x1, z0, z1, y0, name=ROOF):
    """As steep_gable_x with the ridge along z (slopes facing west - the camera - and east)."""
    runs = (x1 - x0) // 2
    for k in range(runs):
        y = y0 + 2 * k
        for z in range(z0, z1 + 1):
            for x, f in ((x0 + k, "east"), (x1 - k, "west")):
                g.set(x, y, z, name + "_steep_roof", facing=f, half="bottom", shape="straight")
                g.set(x, y + 1, z, name + "_upper_steep_roof", facing=f, half="bottom", shape="straight")
        for z in (z0, z1):
            for x in range(x0 + k + 1, x1 - k):
                for yy in (y, y + 1):
                    g.set(x, yy, z, TIMBER)
    ridge = y0 + 2 * runs
    for z in range(z0, z1 + 1):
        for x in range(x0 + runs, x1 - runs + 1):
            g.set(x, ridge - 2, z, TIMBER)
            g.set(x, ridge - 1, z, TIMBER)
            g.set(x, ridge, z, "spruce_slab", type="bottom", **DRY)
    return ridge


def steep_cap(g, x0, x1, z0, z1, y0, name=ROOF):
    """A steep pyramid cap on a small square: rings of steep pieces stepping in, a slab on top."""
    k = 0
    while x0 + k <= x1 - k and z0 + k <= z1 - k:
        y = y0 + 2 * k
        if x0 + k == x1 - k or z0 + k == z1 - k:
            for x in range(x0 + k, x1 - k + 1):
                for z in range(z0 + k, z1 - k + 1):
                    g.set(x, y, z, DRESS)
                    g.set(x, y + 1, z, "stone_brick_slab", type="bottom", **DRY)
            return y + 1
        for x in range(x0 + k, x1 - k + 1):
            for z in range(z0 + k, z1 - k + 1):
                edge = x in (x0 + k, x1 - k) or z in (z0 + k, z1 - k)
                if not edge:
                    g.set(x, y, z, TIMBER)
                    g.set(x, y + 1, z, TIMBER)
                    continue
                f = "east" if x == x0 + k else "west" if x == x1 - k else "south" if z == z0 + k else "north"
                corner = x in (x0 + k, x1 - k) and z in (z0 + k, z1 - k)
                sh = "straight"
                if corner:
                    f = "east" if x == x0 + k else "west"
                    toward = "south" if z == z0 + k else "north"
                    right = {"east": "south", "west": "north"}[f]
                    sh = "outer_right" if right == toward else "outer_left"
                g.set(x, y, z, name + "_steep_roof", facing=f, half="bottom", shape=sh)
                g.set(x, y + 1, z, name + "_upper_steep_roof", facing=f, half="bottom", shape=sh)
        k += 1
    return y0 + 2 * k - 1


def gate_head(g, x, z0=5, z1=11, y=9):
    """The gate's frame in the west face: dressed jambs at z0 / z1, a pointed head over the 5 x 8 opening."""
    for z in (z0, z1):
        for yy in range(1, y + 1):
            g.set(x, yy, z, DRESS)
    for z in range(z0, z1 + 1):
        g.set(x, y + 1, z, DRESS)
    g.stair(x, y, z0 + 1, "north", "top")
    g.stair(x, y, z1 - 1, "south", "top")
    for z in range(z0 + 2, z1 - 1):
        g.set(x, y, z, DRESS)
    g.set(x, y + 2, (z0 + z1) // 2, "chiseled_stone_bricks")


def gallery(g, x0, x1, z0, z1, top=6):
    """A low stone gallery (the control's, kept): a deepslate hem, mossy stone, an arcade of dark openings on its
    camera face, a slab roof with moss patches."""
    g.box(x0, x1, 1, top, z0, z1, STONE)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            g.set(x, 1, z, HEM)
            g.set(x, top + 1, z, GALLERY_TOP if hsh(x, z, 4) % 3 else "moss_block",
                  **({"type": "bottom", "waterlogged": "false"} if hsh(x, z, 4) % 3 else {}))


# ------------------------------------------------------------------------------------------------- option A
def build_a():
    g = Grid()
    ground(g)
    # the hall: stone storey y 1..5, timber y 6..9, steep gable roof y 10..20, ridge along x at z 8
    g.box(1, 7, 1, 5, 3, 13, STONE)
    g.box(1, 7, 6, 9, 3, 13, TIMBER)
    for x in range(1, 8):
        for z in range(3, 14):
            g.set(x, 1, z, HEM)
    g.box(2, 6, 1, 9, 4, 12, "air")                    # hollow inside (the lane runs through it)
    for q in [q for q, v in g.b.items() if v[0] == "air"]:
        g.clear(*q)
    ridge = steep_gable_x(g, 1, 7, 3, 13, 10)
    gate_head(g, 1)
    # the clinging tower, south-west of the hall, under the hall's ridge
    g.box(4, 6, 1, 15, 12, 14, STONE)
    cap = steep_cap(g, 4, 6, 12, 14, 16)
    # the south gallery (kept from the control)
    gallery(g, 1, 9, 14, 16)
    # root buttresses flanking the gate, from the hall's corners down to the ground in front
    for z in (4, 12):
        line(g, (1, 9, z), (0, 1, z), fill_down=True)
    # the trunk: rooted in the south-east, leaning north-west round the well
    path = []
    for (ys, cx, cz, r) in ((range(1, 4), 13, 11, 3), (range(4, 7), 13, 10, 2.5), (range(7, 10), 12.5, 8.2, 2),
                            (range(10, 13), 12, 6.5, 2), (range(13, 16), 11.5, 5, 2), (range(16, 19), 11, 4, 2)):
        path += [(y, cx, cz, r) for y in ys]
    trunk(g, path)
    line(g, (11, 18, 4), (12, 22, 3), name=GROOVE)                     # the main limb up into the crown
    line(g, (10, 17, 4), (5, 17, 3), name=GROOVE)                      # the west limb over the hall's north slope
    for p1 in ((16, 1, 16), (16, 1, 8), (10, 1, 16), (10, 1, 12), (15, 1, 14)):
        line(g, (13, 5, 11), p1, fill_down=True)
    g.set(11, 1, 9, GLOW)                                              # the one heart-glow, at the trunk's foot
    # the canopy: three tiers stepping up to the north-east
    canopy(g, 5, 2, 17, 18, 3.5, seed=1)
    canopy(g, 11, 4, 19, 21, 5.5, seed=2)
    canopy(g, 12, 3, 22, 24, 3.5, seed=3)
    for (x, z) in ((12, 3), (13, 2), (11, 3)):
        g.put(x, 25, z, TOP, **LEAF)
    return g, dict(hall_ridge=ridge, tower=cap)


# ------------------------------------------------------------------------------------------------- option B
def build_b():
    g = Grid()
    ground(g)
    # the gatehouse: x 0..4, z 3..13, stone to 6, timber to 9, a steep gable along z (the west slope faces the camera)
    g.box(0, 4, 1, 6, 3, 13, STONE)
    g.box(0, 4, 7, 9, 3, 13, TIMBER)
    for x in range(0, 5):
        for z in range(3, 14):
            g.set(x, 1, z, HEM)
    ridge = steep_gable_z(g, 0, 4, 3, 13, 10)
    gate_head(g, 0)
    # galleries north and south of the court
    gallery(g, 5, 10, 1, 4)
    gallery(g, 5, 10, 12, 15)
    # the court tree: near vertical, a slight twist, at (13, 8)
    path = [(y, 13, 8, 2.5) for y in range(1, 7)] + [(y, 13 + 0.15 * (y - 7), 8, 2) for y in range(7, 16)]
    trunk(g, path)
    for p1 in ((13, 19, 3), (13, 19, 13), (16, 19, 8)):
        line(g, (13, 15, 8), p1, name=GROOVE)
    for p1 in ((16, 1, 2), (16, 1, 14), (10, 1, 1), (10, 1, 15), (10, 1, 11)):
        line(g, (13, 5, 8), p1, fill_down=True)
    g.set(11, 1, 8, GLOW)
    keep = lambda x, y, z: not (x <= 9 and 7 <= z <= 9)   # noqa: E731
    canopy(g, 13, 8, 17, 19, 5.5, seed=4, keep=keep)
    canopy(g, 13, 8, 20, 21, 4, seed=5, keep=keep)
    canopy(g, 14, 7, 22, 22, 2.5, seed=6, keep=keep)
    return g, dict(hall_ridge=ridge)


# ------------------------------------------------------------------------------------------------- option C
def build_c():
    g = Grid()
    ground(g)
    # the gallery ring round the cloister court (x 5..11, z 5..11)
    for (x0, x1, z0, z1) in ((1, 15, 1, 4), (1, 15, 12, 15), (1, 4, 5, 11), (12, 15, 5, 11)):
        gallery(g, x0, x1, z0, z1, top=7)
    # gate towers flanking the lane
    for (z0, z1) in ((3, 5), (11, 13)):
        g.box(0, 4, 1, 13, z0, z1, STONE)
        steep_gable_x(g, 0, 4, z0, z1, 14)
    gate_head(g, 0)
    # the corner tree in the north-east
    path = [(y, 13 - 0.06 * y, 3 + 0.06 * y, 3 if y < 4 else 2.5 if y < 9 else 2) for y in range(1, 19)]
    for q in [q for q in g.b if math.hypot(q[0] - 13, q[2] - 3) <= 3.5 and q[1] >= 1]:
        g.clear(*q)
    trunk(g, path)
    line(g, (12, 18, 4), (13, 21, 3), name=GROOVE)
    line(g, (12, 16, 4), (9, 17, 7), name=GROOVE)
    for p1 in ((16, 1, 9), (9, 1, 1), (16, 1, 1), (10, 1, 5)):
        line(g, (13, 6, 3), p1, fill_down=True)
    g.set(11, 1, 5, GLOW)
    canopy(g, 12, 4, 16, 18, 5, seed=7)
    canopy(g, 12, 4, 19, 21, 4, seed=8)
    canopy(g, 13, 3, 22, 24, 2.5, seed=9)
    for (x, z) in ((6, 6), (9, 5), (11, 7), (6, 10)):                  # leaves pouring into the court (ref03)
        for y in range(9, 16):
            g.put(x, y, z, MID, **LEAF)
    return g, dict()


BUILDERS = {"a": build_a, "b": build_b, "c": build_c}


# ------------------------------------------------------------------------------------------------- write + check
def prune(g):
    while True:
        lone = [q for q in g.b if q[1] > 0 and not any((q[0] + a, q[1] + b, q[2] + c) in g.b for a, b, c in
                                                       ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1),
                                                        (0, 0, -1)))]
        if not lone:
            return
        for q in lone:
            del g.b[q]


def write(g, out):
    prune(g)
    h = max(y for (_, y, _) in g.b) + 1
    assert h <= H_MAX, "%s is %d tall" % (out, h)
    rig = gs._SunRig(SX, h, SZ)
    for (x, y, z), (n, props) in g.b.items():
        rig.set(x, y, z, n, **props)
    mv._leaf_distances(rig)
    rig.bury_grass()
    rig.settle()
    os.makedirs(os.path.dirname(out), exist_ok=True)
    rig.structure().write(out)
    return rig


def seal(g, out):
    """Ground pockets left between buttress roots and the masses are filled with roots (a real root plate is solid
    there): write a draft, fill each pocket cell, repeat until none."""
    import audit_structure_pockets as ap
    for _ in range(6):
        rig = write(g, out)
        one_way, ground_, roof, rooms, note = ap.audit("draft", out)
        if not one_way and not ground_:
            return rig
        for q in ground_ + one_way:
            g.set(*q, GRIP)
    return rig


def tree_lead(n):
    """(tree top, tallest non-tree column top): the landmark is the tree; everything built must stay under it."""
    tree = max(y for q, v in n.items() for y in [q[1]] if v.endswith(TREE))
    built = max(q[1] for q, v in n.items() if not v.endswith(TREE))
    return tree, built


def check(rig, out):
    import audit_structure_pockets as ap
    name = os.path.splitext(os.path.basename(out))[0]
    n = {q: b[0] for q, b in rig.b.items()}
    assert rig.size[1] <= H_MAX, (name, rig.size)
    assert all((x, 0, z) in n for x in range(SX) for z in range(SZ)), "a hole in the foundation"
    busy = [(x, y, z) for x in LANE_X for z in LANE_Z for y in range(1, LANE_H + 1) if (x, y, z) in n]
    assert not busy, "the exit lane is narrowed: %s" % busy[:8]
    col = [y for y in range(1, rig.size[1]) if (SPAWN[0], y, SPAWN[2]) in n]
    assert not col, "the spawn column is blocked at %s" % col
    lone = [q for q in n if q[1] > 0 and not any((q[0] + a, q[1] + b, q[2] + c) in n for a, b, c in
                                                   ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)))]
    assert not lone, "floating %s" % lone[:6]
    one_way, ground_, roof, rooms, note = ap.audit(name, out)
    assert not one_way and not ground_ and "TRAPPED" not in note, (one_way[:8], ground_[:8], note)
    t, b = tree_lead(n)
    return "%s %s, %d blocks, no pockets%s | tree top %d, built top %d (lead %d)" % (
        name, "x".join(map(str, rig.size)), len(n), note, t, b, t - b)


def main(argv):
    for o in (argv or sorted(BUILDERS)):
        g, meta = BUILDERS[o]()
        out = OUT % o
        rig = seal(g, out)
        print(check(rig, out), meta)


if __name__ == "__main__":
    main(sys.argv[1:])
