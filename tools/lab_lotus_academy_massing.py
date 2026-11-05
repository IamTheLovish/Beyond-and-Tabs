#!/usr/bin/env python3
"""Lotus Academy (Stormcallers T1 lab) L1 massing options, Nov 2026 - judged blind against the shipped Lotus Court.

Baseline (eyes 41263811904, two fresh critics 4 / 4): "flat yard, half a jade roof"; "half teal roof over pink yard";
four competing accents (teal, pink, gold, red). Footprint 9 x 9 (BuildingLevels), spawn column x 4, z 4 open to the sky.
  A  TWO-EAVE HALL: the hall double-eaved already at L1 (the shipped L2 hall), the court kept
  B  LOTUS TOWER: the shipped hall + a two-roof lotus tower on the south-west corner (the camera's corner), crowned
  C  CLOISTER: roofed galleries on west and east, a two-tier gate pavilion on the south over the way in
The kit's materials (jade, dark oak, red lacquer, gold); detail is not judged here.

Usage: python3 tools/lab_lotus_academy_massing.py OUTDIR
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lab_lotus_academy as la  # noqa: E402

JADE, OAK, PAPER, GOLD = la.JADE, la.OAK, la.PAPER, la.GOLD


def clear(r, cells, y0, y1):
    for (x, z) in cells:
        for y in range(y0, y1 + 1):
            r.b.pop((x, y, z), None)


def option_a(r):
    chimes = la.draw_l1(r)
    la.hall_l2(r, chimes)
    return chimes


def tower(r, x1, x2, z1, z2, tiers, chimes):
    """A pavilion body [x1..x2] x [z1..z2] with a curved roof lip one cell out (clamped to the plot) every 3 rows."""
    clear(r, [(x, z) for x in range(x1 - 1, x2 + 2) for z in range(z1 - 1, z2 + 2)], 1, 30)
    y = 1
    for t, lip in enumerate(tiers):
        top = lip
        for yy in range(y, top):
            for x in range(x1, x2 + 1):
                for z in range(z1, z2 + 1):
                    edge = x in (x1, x2) and z in (z1, z2)
                    r.set(x, yy, z, la.RED if edge else (PAPER if yy % 3 == 0 else OAK),
                          **(dict(axis="y") if edge else {}))
        lx1, lx2 = max(0, x1 - 1), min(la.SX - 1, x2 + 1)
        lz1, lz2 = max(0, z1 - 1), min(la.SZ - 1, z2 + 1)
        la.curved_roof(r, lx1, lx2, lz1, lz2, lip, chimes, flush_north=False)
        y = lip + 1
    return y


def option_b(r):
    chimes = la.draw_l1(r)
    clear(r, [(x, z) for x in range(0, 4) for z in range(4, 9)], 1, 30)   # the lotus sculpture goes up the tower
    y = tower(r, 1, 2, 6, 7, (4, 8), chimes)
    # the crown: a gold boss, cherry petals round a glowing heart, a rod
    for x in (1, 2):
        for z in (6, 7):
            r.set(x, y, z, OAK)
            r.set(x, y + 1, z, GOLD)
    r.set(1, y + 2, 6, "pearlescent_froglight", axis="y")
    for (x, z, f) in ((2, 6, "east"), (1, 7, "south"), (2, 7, "east")):
        r.st(x, y + 2, z, f, name=la.PETAL_ST)
    r.rod(1, y + 3, 6)
    return chimes


def option_c(r):
    chimes = la.draw_l1(r)
    clear(r, [(x, z) for x in range(9) for z in range(4, 9)], 1, 30)
    clear(r, [(x, z) for x in (0, 8) for z in range(0, 4)], 2, 3)
    # west and east galleries x 0..1 / 7..8, z 4..8: red posts, a curved roof lip at y 4
    for x0, x1 in ((0, 1), (7, 8)):
        for z in range(4, 9):
            for x in (x0, x1):
                r.set(x, 1, z, "stone")
            outer = x0 if x0 == 0 else x1
            for y in (2, 3):
                r.set(outer, y, z, OAK if z % 2 else la.RED, **({} if z % 2 else dict(axis="y")))
        la.curved_roof(r, x0, x1, 3, 8, 4, chimes, flush_north=False)
    # the gate pavilion on the south x 2..6, z 7..8: red posts, two curved roofs
    for x in (2, 6):
        for z in (7, 8):
            for y in range(1, 5):
                r.red(x, y, z)
    r.fill(2, 4, 7, 6, 4, 8, OAK)
    for y in (2, 3, 4):
        for x in (3, 5):
            r.b.pop((x, y, 8), None)
    la.curved_roof(r, 2, 6, 6, 8, 5, chimes, flush_north=False)
    r.fill(3, 6, 7, 5, 7, 7, OAK)
    la.curved_roof(r, 3, 5, 6, 8, 8, chimes, flush_north=False)
    r.set(4, 9, 7, GOLD)
    r.rod(4, 10, 7)
    return chimes


def build(draw, name):
    r = la.Rig(30)
    chimes = draw(r)
    la.hang_chimes(r, chimes)
    for p, (n, _) in list(r.b.items()):            # leaves cut from their trunk become plain (greybox only)
        if n.endswith("_leaves"):
            r.b[p] = (n, dict(persistent="true", distance="1", waterlogged="false"))
    r.settle()
    assert all(r.get(4, y, 4)[0] == "air" for y in range(1, 30)), name + ": spawn column covered"
    r.size = (la.SX, max(p[1] for p in r.b) + 1, la.SZ)
    return r.structure()


if __name__ == "__main__" and len(sys.argv) == 2:
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    for n, f in (("a", option_a), ("b", option_b), ("c", option_c)):
        s = build(f, n)
        s.write(os.path.join(out, "massing_%s.nbt" % n))
        print(n, s.size, sum(1 for i in s.blocks.values() if s.palette[i][0] != "air"))


# ------------------------------------------------------------------------------------------- round 3 roof options
# Round 1 (6 / 6) and round 2 (5 / 5.5, reverted): both critics, both rounds - "stepped flat plates read as a
# ziggurat / mini capitol; no roof reads as curved". From the steep RTS camera a curve reads in the PLAN OUTLINE: the
# eave's corners reach out and its middle recedes. Two plans on the round-1 L1, judged blind against it:
#   S  SWEPT WINGS: both hall roofs' front eaves recede in the middle (x 3..5) and reach out at the corners (the first
#      roof's wings come forward over the galleries), so from above the hall's front line is a bow
#   G  ONE GREAT ROOF: the second storey goes; one deep roof (lip y 7, swept wings) climbs in stair courses to a long
#      jade ridge with lifted ends and the lotus on it - a hall, not a tiered pagoda


def wings(r, y, x_out, x_in, z):
    """Lip cells reaching forward to row z on [x_out..x_in] (one wing): the outer corner jade with a lifted slab, the
    cell next to it lifted too, the rest a stair whose low edge faces the camera."""
    lo, hi = min(x_out, x_in), max(x_out, x_in)
    for x in range(lo, hi + 1):
        if x == x_out:
            r.set(x, y, z, JADE)
            r.slab(x, y + 1, z)
            r.st(x, y + 2, z, "east" if x_out == 0 else "west", "top", la.JADE_ST)   # the upturned tip
        elif abs(x - x_out) == 1:
            r.set(x, y, z, JADE)
            r.slab(x, y + 1, z)
        else:
            r.st(x, y, z, "north")
    for x in range(lo, hi + 1):                    # the old lip behind the wing becomes band
        r.set(x, y, z - 1, JADE)
        r.b.pop((x, y + 1, z - 1), None)


def recess(r, y, x1, x2, z):
    """The eave's middle steps back one row: lip cells at row z go, row z - 1 becomes the lip."""
    for x in range(x1, x2 + 1):
        r.b.pop((x, y, z), None)
        r.b.pop((x, y + 1, z), None)
        r.b.pop((x, y - 1, z), None)
        r.st(x, y, z - 1, "north")


def option_s(r):
    chimes = la.draw_l1(r)
    wings(r, la.R1, 0, 2, 4)
    wings(r, la.R1, 8, 6, 4)
    recess(r, la.R2, 3, 5, 3)
    return chimes


def option_g(r):
    chimes = la.draw_l1(r)
    for (x, y, z) in [q for q in r.b if q[2] <= 3 and q[1] >= 5]:
        r.b.pop((x, y, z))
    chimes[:] = [c for c in chimes if c[2] >= 4]
    for y in (5, 6):                               # the hall's storey rises to y 6 under one roof
        for x in range(1, 8):
            r.set(x, y, 0, OAK)
            if x in (1, 4, 7):
                r.red(x, y, 1)
            else:
                r.set(x, y, 1, PAPER if y == 5 else OAK)
        for x in la.COLS:
            r.red(x, y, 2)
    for x in (2, 4, 6):
        r.slab(x, 6, 2, la.OAK_SLAB, "top")
    for x in la.COLS:
        r.set(x, 6, 2, OAK)
    la.curved_roof(r, 0, 8, 0, 3, 7, chimes)
    wings(r, 7, 0, 2, 4)
    wings(r, 7, 8, 6, 4)
    for x in range(SXR):
        r.st(x, 6, 3, "north", "top", la.OAK_ST)    # rafters under the lip
    la.curved_roof(r, 1, 7, 0, 2, 8, chimes)       # the courses climb
    for x in range(2, 7):
        r.st(x, 9, 1, "north")
        r.set(x, 9, 0, JADE)
    for x in range(2, 7):                          # the long ridge
        r.set(x, 10, 0, JADE)
    for x in (2, 6):
        r.slab(x, 11, 0)
    r.set(C, 10, 1, JADE)
    la.lotus_crown(r, 11, C, 1)
    return chimes


SXR = la.SX
C = la.C


def build3(draw, name):
    return build(draw, name)


if __name__ == "__main__" and len(sys.argv) > 2 and sys.argv[2] == "r3":
    for n, f in (("s", option_s), ("g", option_g)):
        s = build(f, n)
        s.write(os.path.join(sys.argv[1], "r3_%s.nbt" % n))
