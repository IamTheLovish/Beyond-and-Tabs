#!/usr/bin/env python3
"""Ashborn vehicle lab (the Ember Pens) - massing options for the blind greybox comparison (Oct 31 2026).

The r12 method (PR #411 Engine Shed 3 -> 7; #456 Sunforged 4 -> 5.5; #457 Verdant 3 -> 6; #458 Gravebound 4 -> 6): ONE
primary reference whose scale fits the plot, imitated closely; the silhouette settled by a blind critic on real eyes
stills with the old build as the CONTROL; detail only after. The designer pass
(itachi2-api/runs/20261031-001548-ashborn-design) picked ref29 (design/refs/vehicle_labs/ref29_dark_tiered_lava_fortress
.png): a dark citadel in stepped tiers shot from our camera, every tier edge carrying a light parapet cap (that is why
its tiers step from above), lava only in sunken courts, a striated cliff plinth (our KEEP: the ordered basalt-column
wall), one central keep under a dark cone. ref25 lends the gate crest (basalt columns rising to a point over the mouth).

Greybox only: role blocks, no ornament. Roles (the designer's value bands, by look): basalt the plinth / pens / gate
crest (0.22-0.32, to be measured on the control's stills), deepslate tiles the tier walls and keep (0.12-0.20),
Macaw's deepslate steep roofs the cones (0.15-0.25), tuff the terrace paving (0.32-0.44), andesite slabs the caps on
every tier edge (0.48-0.58), polished deepslate the court and lane floor, magma behind iron bars the ONE glow (the forge
in the court's east wall, on the gate's axis), iron bars + black wool the pen stalls between deepslate-tile pilasters.

Plot 17 x 17 (x 0..16 west -> east, z 0..16 north -> south; the RTS camera looks north-east from the south-west and
sees the roofs, the west face x 0 - the front - and the south face z 16). Hard rules (asserted): height <= 26 (y 0..25);
the exit lane x 0..8, z 6..10, y 1..8 empty; the spawn column (8, y, 8) air to the sky; no floating blocks; no sealed
pockets (tools/audit_structure_pockets.py).

  A  ref29's stepped citadel (designer's pick): a basalt plinth y 1..6 over the whole plot (pens on its south face),
     a set-back ring x 2..14 z 2..14 to 12 (+ cap 13), the open court x 6..10 z 6..10 (the spawn shaft, the forge in
     its east wall), a gatehouse x 0..4 z 4..12 with a column crest rising to 17 on z 8, the LANDMARK keep x 11..15
     z 1..5 to 19 under a 3 x 3 dark cone and a spire to 25, small coned towers NW (x 1..3 z 1..3, 16) and SE
     (x 13..15 z 13..15, 15).
  B  ref30's volcano with an open crater: terraces 5 / 9 / 13 / 17 / 21 climbing to a peak at the north-east (23, the
     centre 25), the crater = the court x 6..10 z 6..10 open to the south-west, the gate a cut through the west slope
     under a lintel and a crest at 13.
  C  the old round-3 plan in its fairest form: a ring of wings to 9 round the court x 5..11 z 5..11, a gatehouse x 0..4
     z 3..13 to 12 with a crest to 16 on z 8, a north tier x 5..9 z 0..4 to 13, the forge GIANT on the north-east
     (legs, belt, torso, 7-wide shoulders, a 3 x 3 head to 23, the hammer raised over the west shoulder to 25).
  control: design/vehicle_labs/ashborn_l3_round2.nbt (the round-2 lab, tools/lab_vehicle_ashborn.py before this
     rebuild, 3/10, 17 x 23 x 17).

    python3 tools/ashborn_massing.py          # writes design/vehicle_labs/massing/ashborn_{a,b,c}.nbt, checks
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs  # noqa: E402

SX = SZ = 17
H_MAX = 26
LANE_X, LANE_Z, LANE_H = range(0, 9), range(6, 11), 8
SPAWN = (8, 1, 8)
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "design", "vehicle_labs", "massing",
                   "ashborn_%s.nbt")

CRAG = "basalt"
WALL = "deepslate_tiles"
ROOF = "mcwroofs:deepslate"
PAVE = "tuff"
CAP = "andesite_slab"
FLOOR = "polished_deepslate"
GLOW = "magma_block"
BARS = "iron_bars"
HIDE = "black_wool"
FIGURE = "polished_basalt"
NEEDLE = "polished_deepslate_wall"
DRY = dict(waterlogged="false")
POST = dict(up="true", north="none", south="none", east="none", west="none", **DRY)


class Grid:
    def __init__(self):
        self.b = {}

    def set(self, x, y, z, name, **props):
        assert 0 <= x < SX and 0 <= z < SZ and 0 <= y < H_MAX, ((x, y, z), name)
        if name in (CRAG, FIGURE) and not props:
            props = dict(axis="y")
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

    def steep(self, x, y, z, facing, shape="straight"):
        """One Macaw's steep-roof step: the lower piece at y, the upper at y + 1, rising toward `facing`."""
        self.set(x, y, z, ROOF + "_steep_roof", facing=facing, half="bottom", shape=shape)
        self.set(x, y + 1, z, ROOF + "_upper_steep_roof", facing=facing, half="bottom", shape=shape)

    def slab(self, x, y, z, name=CAP):
        self.set(x, y, z, name, type="bottom", **DRY)


def ring_cells(x0, x1, z0, z1):
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


def cone(g, x0, x1, z0, z1, y, needle_to):
    """A dark steep cone on x0..x1 z0..z1 from y (rings of steep steps closing 1 per side per 2 rows), a needle on the
    last cell up to needle_to."""
    while x1 - x0 >= 2 and z1 - z0 >= 2:
        for (x, z), f, sh in ring_cells(x0, x1, z0, z1):
            g.steep(x, y, z, f, sh)
        for x in range(x0 + 1, x1):
            for z in range(z0 + 1, z1):
                g.set(x, y, z, WALL)
                g.set(x, y + 1, z, WALL)
        x0, x1, z0, z1, y = x0 + 1, x1 - 1, z0 + 1, z1 - 1, y + 2
    for yy in range(y, needle_to + 1):
        g.set(x0, yy, z0, NEEDLE, **POST)


def terrace(g, cells, y_top, edge):
    """The top of a tier: tuff paving on its cells at y_top, an andesite cap (slab) on y_top + 1 along its `edge` cells
    (the light parapet ref29's tiers read by)."""
    for (x, z) in cells:
        g.set(x, y_top, z, PAVE)
    for (x, z) in edge:
        g.slab(x, y_top + 1, z)


def rect(x0, x1, z0, z1):
    return [(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)]


def outer(x0, x1, z0, z1):
    return [(x, z) for (x, z) in rect(x0, x1, z0, z1) if x in (x0, x1) or z in (z0, z1)]


def ground(g):
    g.box(0, 16, 0, 0, 0, 16, FLOOR)


def pens(g, z=16, y_top=4, xs=range(1, 16), piers=(2, 5, 8, 11, 14)):
    """The beast-pen row (KEEP) in the south face: deepslate-tile pilasters, barred stalls with black hides behind."""
    for x in xs:
        if x in piers:
            for y in range(1, y_top + 1):
                g.set(x, y, z, WALL)
            continue
        for y in range(1, y_top):
            g.set(x, y, z, BARS, **DRY)
            g.set(x, y, z - 1, HIDE)


def forge(g, x, zs=(7, 8, 9), ys=(1, 2, 3)):
    """The ONE glow: magma behind iron bars in the court's east wall, on the gate's axis (seen through the gate and from
    above into the court)."""
    for z in zs:
        for y in ys:
            g.set(x, y, z, BARS, **DRY)
            g.set(x + 1, y, z, GLOW)


def clear(g, cells, y0, y1):
    for (x, z) in cells:
        for y in range(y0, y1 + 1):
            g.clear(x, y, z)


def clear_lane_and_spawn(g):
    clear(g, [(x, z) for x in LANE_X for z in LANE_Z], 1, LANE_H)
    clear(g, [(SPAWN[0], SPAWN[2])], 1, H_MAX - 1)


# ------------------------------------------------------------------------------------------------- A: ref29 citadel
def build_a():
    g = Grid()
    ground(g)
    court = rect(6, 10, 6, 10)
    g.box(0, 16, 1, 6, 0, 16, CRAG)
    clear(g, court, 1, 6)
    t1_ring = [c for c in rect(0, 16, 0, 16) if not (2 <= c[0] <= 14 and 2 <= c[1] <= 14)]
    terrace(g, t1_ring, 6, outer(0, 16, 0, 16))
    # T2: the set-back ring
    for (x, z) in rect(2, 14, 2, 14):
        if (x, z) in court:
            continue
        for y in range(7, 13):
            g.set(x, y, z, WALL)
    t2 = [c for c in rect(2, 14, 2, 14) if c not in court]
    terrace(g, t2, 12, [c for c in outer(2, 14, 2, 14)] + outer(5, 11, 5, 11))
    # the gatehouse with the ref25 column crest (x 0..2 the crest profile along z, x 3..4 a 13 block)
    crest = {4: 12, 5: 13, 6: 14, 7: 15, 8: 17, 9: 15, 10: 14, 11: 13, 12: 12}
    for z, top in crest.items():
        for x in (0, 1, 2):
            for y in range(7, top + 1):
                g.set(x, y, z, CRAG)
        for x in (3, 4):
            for y in range(7, 14):
                g.set(x, y, z, CRAG)
    # the keep (landmark) x 11..15 z 1..5 to 19, cap ring, 3 x 3 cone 20..21, spire to 25
    g.box(11, 15, 7, 18, 1, 5, WALL)
    for (x, z) in outer(11, 15, 1, 5):
        g.set(x, 19, z, "polished_andesite")
    g.box(12, 14, 19, 19, 2, 4, WALL)
    cone(g, 12, 14, 2, 4, 20, 25)
    # small coned towers NW and SE
    g.box(1, 3, 7, 12, 1, 3, WALL)
    cone(g, 1, 3, 1, 3, 13, 16)
    g.box(13, 15, 7, 11, 13, 15, WALL)
    cone(g, 13, 15, 13, 15, 12, 15)
    pens(g)
    forge(g, 11)
    clear_lane_and_spawn(g)
    return g, {"landmark": (13, 3)}


# ------------------------------------------------------------------------------------------------- B: ref30 volcano
def build_b():
    g = Grid()
    ground(g)
    crater = rect(6, 10, 6, 10)
    tiers = [  # (cells, top, wall)
        (rect(0, 16, 0, 16), 5, CRAG),
        (rect(1, 15, 1, 15), 9, WALL),
        (rect(3, 15, 1, 13), 13, WALL),
        ([c for c in rect(6, 15, 1, 10)], 17, WALL),
        (rect(11, 15, 1, 5), 21, WALL),
    ]
    prev_top = 0
    for i, (cells, top, mat) in enumerate(tiers):
        for (x, z) in cells:
            if (x, z) in crater:
                continue
            for y in range(prev_top + 1, top + 1):
                g.set(x, y, z, mat)
        prev_top = top
    for i, (cells, top, mat) in enumerate(tiers):
        nxt = set(tiers[i + 1][0]) if i + 1 < len(tiers) else set()
        own = [c for c in cells if c not in nxt and c not in crater]
        xs = [c[0] for c in cells]
        zs = [c[1] for c in cells]
        edge = [c for c in own if c[0] in (min(xs), max(xs)) or c[1] in (min(zs), max(zs))]
        terrace(g, own, top, edge)
    # crater rim caps
    for c in outer(5, 11, 5, 11):
        if g.get(c[0], 17, c[1]) == PAVE:
            g.slab(c[0], 18, c[1])
    # the peak (landmark) x 12..14 z 2..4: a dark cone 22..23, the centre to 25
    cone(g, 12, 14, 2, 4, 22, 25)
    # the gate cut through the west slope: basalt jambs x 0..1 at z 4..5 / 11..12 to 11, a lintel x 0..2 over the lane
    # y 9..11, a crest (1, 12..13, 8)
    for z in (4, 5, 11, 12):
        for x in (0, 1):
            for y in range(1, 12):
                g.set(x, y, z, CRAG)
    for z in range(6, 11):
        for x in (0, 1, 2):
            for y in range(9, 12):
                g.set(x, y, z, CRAG)
    for y in (12, 13):
        g.set(1, y, 8, CRAG)
    pens(g, y_top=4)
    forge(g, 11)
    clear_lane_and_spawn(g)
    return g, {"landmark": (13, 3)}


# ------------------------------------------------------------------------------------------------- C: ring + giant
def build_c():
    g = Grid()
    ground(g)
    court = rect(5, 11, 5, 11)
    g.box(0, 16, 1, 6, 0, 16, CRAG)
    for (x, z) in rect(0, 16, 0, 16):
        for y in (7, 8):
            g.set(x, y, z, WALL)
    clear(g, court, 1, 8)
    terrace(g, [c for c in rect(0, 16, 0, 16) if c not in court], 9,
            outer(0, 16, 0, 16) + outer(4, 12, 4, 12))
    crest = {3: 12, 4: 12, 5: 13, 6: 13, 7: 14, 8: 16, 9: 14, 10: 13, 11: 13, 12: 12, 13: 12}
    for z, top in crest.items():
        for x in (0, 1):
            for y in range(10, top + 1):
                g.set(x, y, z, CRAG)
        for x in (2, 3, 4):
            for y in range(10, 13):
                g.set(x, y, z, CRAG)
    g.box(5, 9, 10, 13, 0, 4, WALL)
    # the giant x 10..16 z 1..3 on the wings' roof (y 10): legs, belt, torso, shoulders, head, hammer
    for (x0, x1) in ((11, 12), (14, 15)):
        g.box(x0, x1, 10, 14, 1, 2, FIGURE)
    g.box(11, 15, 15, 15, 1, 3, FIGURE)
    g.box(11, 15, 16, 19, 1, 3, FIGURE)
    g.box(10, 16, 20, 20, 1, 3, FIGURE)
    g.box(12, 14, 21, 23, 1, 3, FIGURE)
    for y in range(21, 24):
        g.set(10, y, 2, FIGURE)
    g.box(10, 10, 24, 25, 1, 3, "andesite")
    pens(g, y_top=4)
    forge(g, 12)
    clear_lane_and_spawn(g)
    return g, {"landmark": (13, 2)}


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
