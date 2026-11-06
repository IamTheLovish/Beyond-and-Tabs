#!/usr/bin/env python3
"""Grove (Verdant T1 army lab) L1 massing options, Nov 2026 - judged blind against the shipped Stag Glade.

Baseline (eyes 41263811904, two fresh critics 4 / 4): "mossy garden lost in grass", "small mossy ruin, leaf clump"
(dL -0.006 vs the lawn, hue matches the lawn). The shipped tree is a leaf curtain two cells deep on the north edge:
from the steep RTS camera a strip, not a tree. Footprint 9 x 8 (BuildingLevels), L1 <= 14 high, the spawn columns
(4, 3) and (4, 4) open to the sky, the Treant room x 3..5 z 2..5 y 1..3 clear, the glade open west and south.
  A  CRESCENT: the trunk on the north, a horseshoe canopy over the north band and down both sides, open over the glade
  B  GREAT DOME: one round canopy over most of the plot from a north trunk, notched only in front of the spawn
  C  TREE AND RUIN: the tree on the north-east corner, a tall broken shrine tower on the west, the glade south-west
All three wear the Court's crown (cherry on top, azalea under, like the capitol #478); plain blocks, no detail.

Usage: python3 tools/lab_grove_massing.py OUTDIR
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lab_grove as lg  # noqa: E402

SX, SZ = lg.SX, lg.SZ
TOP = 13                                   # L1's highest row (size 14, the shipped L1's height)


def floor(r):
    for x in range(SX):
        for z in range(SZ):
            fringe = x in (0, SX - 1) and z >= 2 or z == SZ - 1
            r.set(x, 0, z, "moss_block" if fringe else lg.MSB)


def trunk(r, xs, zs, y1):
    for x in xs:
        for z in zs:
            for y in range(1, y1 + 1):
                r.log(x, y, z)


def leaf(r, x, y, z):
    if 0 <= x < SX and 0 <= z < SZ and (x, z) not in lg.CENTRE and (x, y, z) not in r.b and 0 < y <= TOP:
        r.set(x, y, z, "azalea_leaves", persistent="true", distance="1", waterlogged="false")


def crown(r):
    """The topmost leaf of every column becomes cherry (the crown seen from the RTS camera)."""
    tops = {}
    for (x, y, z), (n, _) in r.b.items():
        if n.endswith("_leaves") and y > tops.get((x, z), -1):
            tops[(x, z)] = y
    for (x, z), y in tops.items():
        r.set(x, y, z, "cherry_leaves", persistent="true", distance="1", waterlogged="false")


def pier(r, x0, x1, z0, z1, top):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(1, top + 1):
                r.set(x, y, z, lg.MSB)


def option_a(r):
    """CRESCENT: trunk x 3..5 z 0..1 to y 9; a horseshoe canopy y 8..12 (north band z 0..2, wings x 0..2 / 6..8 to
    z 6) on low piers under the wings."""
    floor(r)
    pier(r, 0, 1, 0, 1, 5)
    pier(r, 7, 8, 0, 1, 5)
    trunk(r, (3, 4, 5), (0, 1), 9)
    for x in range(SX):
        for z in range(SZ):
            wing = (x <= 2 or x >= 6) and z <= 6
            band = z <= 2
            if not (wing or band):
                continue
            dz = max(0, z - 2) / 4.5
            lo, hi = (8, 11) if not band else (8, 12)
            if x in (0, 8) and z >= 5:
                lo, hi = 8, 10
            for y in range(lo, hi + 1):
                if not (y == hi and dz > 0.8):
                    leaf(r, x, y, z)
    for y in range(10, 13):                 # the crown over the trunk
        for x in range(2, 7):
            for z in (0, 1):
                leaf(r, x, y + 1 if y == 12 and 3 <= x <= 5 else y, z)


def option_b(r):
    """GREAT DOME: trunk x 3..5 z 0..1 to y 8; one ellipsoid canopy round (4, 1.5) radius 5 x 4.5, y 7..13 - the
    plot's roof - notched over the spawn columns only (x 4, z 3..)."""
    floor(r)
    trunk(r, (3, 4, 5), (0, 1), 8)
    for x in range(SX):
        for z in range(SZ):
            for y in range(7, TOP + 1):
                d = ((x - 4) / 5.0) ** 2 + ((z - 1.5) / 4.6) ** 2 + ((y - 9) / 4.2) ** 2
                if d <= 1.0 and not (x == 4 and z >= 3):
                    leaf(r, x, y, z)


def option_c(r):
    """TREE AND RUIN: trunk x 6..7 z 0..1 to y 9; a round canopy round (6, 2) radius 3.6, y 8..13; a broken shrine
    tower on the west (x 0..1, z 0..2) to y 9 with a stepped-down arm to x 2; the glade open to the south-west."""
    floor(r)
    trunk(r, (6, 7), (0, 1), 9)
    pier(r, 0, 1, 0, 2, 9)
    pier(r, 2, 2, 0, 1, 6)
    r.set(0, 10, 0, lg.MSB)
    for x in range(SX):
        for z in range(SZ):
            for y in range(8, TOP + 1):
                d = ((x - 6.2) / 3.7) ** 2 + ((z - 2.0) / 3.7) ** 2 + ((y - 10) / 3.2) ** 2
                if d <= 1.0:
                    leaf(r, x, y, z)


# ---- round 1 (on A, the blind winner 6.5 / 6.5): the C / hook top, no tree read from above, the shrine hidden, the
# moss rim at grass value
LOBES = ((4.0, 1.0, 10.6, 3.4, 2.3, 2.6), (1.5, 2.7, 9.3, 2.2, 2.7, 2.1), (7.1, 2.7, 9.2, 2.2, 2.7, 2.1))


def rim(r):
    """A dark podzol rim on the fringe (the plot owns its edge against the lawn)."""
    for x in range(SX):
        for z in range(SZ):
            if x in (0, SX - 1) or z in (0, SZ - 1):
                r.set(x, 0, z, "podzol", snowy="false")
    r.set(2, 0, 6, "moss_block")


def lobed_crown(r):
    """One crown of three rounded lobes (a high centre over the trunk, a lower lobe each side reaching z 4), oak boughs
    from the trunk's top to each side lobe showing in the gaps."""
    trunk(r, (3, 4, 5), (0, 1), 9)
    for y in range(7, 10):                    # boughs climbing out to the side lobes
        k = y - 7
        r.log(2 - k, y, 1 + k // 2, axis="x")
        r.log(6 + k, y, 1 + k // 2, axis="x")
    for (cx, cz, cy, rx, rz, ry) in LOBES:
        for x in range(SX):
            for z in range(SZ):
                for y in range(6, TOP + 1):
                    if ((x - cx) / rx) ** 2 + ((z - cz) / rz) ** 2 + ((y - cy) / ry) ** 2 <= 1.0:
                        leaf(r, x, y, z)


def option_a1(r):
    """A1 CROWN AND SHRINE GATE: the lobed crown; two broken shrine piers in front of the side lobes (west x 0..1
    z 4..5 to y 7, east x 7..8 z 4..5 to y 5); the podzol rim."""
    floor(r)
    rim(r)
    lobed_crown(r)
    pier(r, 0, 1, 4, 5, 7)
    r.set(1, 8, 4, lg.MSB)
    pier(r, 7, 8, 4, 5, 5)


def option_a2(r):
    """A2 CROWN AND RUIN TOWER: the lobed crown; a tall shrine tower on the west (x 0..1, z 3..4) to y 11, past the
    canopy's edge; a low broken wall on the east (x 8, z 3..6, 2..3 high); the podzol rim."""
    floor(r)
    rim(r)
    lobed_crown(r)
    pier(r, 0, 1, 3, 4, 10)
    r.set(0, 11, 3, lg.MSB)
    r.set(0, 11, 4, lg.MSB)
    for z, h in ((3, 3), (4, 3), (5, 2), (6, 1)):
        pier(r, 8, 8, z, z, h)


# ---- round 2 (on A1, the blind winner 6.5 / 6.5 over A2 6 / 6): still a C top with no centre, no trunk / roots
# from the RTS camera, two equal pillars read as a gate / table, the dirt rim as a planter, green bands at grass value
def kerb(r, beds=()):
    """A stone kerb on the plot's edge (chiseled at the corners, mossy and cracked between), podzol only in the root
    beds."""
    for x in range(SX):
        for z in range(SZ):
            if x in (0, SX - 1) or z in (0, SZ - 1):
                corner = x in (0, SX - 1) and z in (0, SZ - 1)
                r.set(x, 0, z, lg.CHB if corner else (lg.CSB if (x + z) % 3 == 0 else lg.MSB))
    for (x, z) in beds:
        r.set(x, 0, z, "podzol", snowy="false")
    r.set(2, 0, 6, "moss_block")


def dome(r, lobes):
    for (cx, cz, cy, rx, rz, ry) in lobes:
        for x in range(SX):
            for z in range(SZ):
                for y in range(6, TOP + 1):
                    if ((x - cx) / rx) ** 2 + ((z - cz) / rz) ** 2 + ((y - cy) / ry) ** 2 <= 1.0:
                        leaf(r, x, y, z)


def dark_hem(r):
    """The lowest leaf of every column at least two deep becomes dark oak leaves (the underside in shadow)."""
    cols = {}
    for (x, y, z), (n, _) in r.b.items():
        if n.endswith("_leaves"):
            cols.setdefault((x, z), []).append(y)
    for (x, z), ys in cols.items():
        if len(ys) >= 2:
            r.set(x, min(ys), z, "dark_oak_leaves", persistent="true", distance="1", waterlogged="false")


def roots(r, cells):
    for (x, y, z) in cells:
        if (x, y, z) not in lg.CLEAR:
            r.log(x, y, z, axis="y" if y > 1 else "z", name=lg.WOOD)


def option_r2a(r):
    """R2a TREE TO THE WEST: trunk x 1..2 z 0..2 + x 3 z 0..1 to y 9 (the SW camera sees its west and south faces),
    root flare over the west fringe; one closed crown with its high point over the trunk; a broken east pier (x 7..8,
    z 4, to y 6/7) and a stub (7, 6); kerb rim, podzol in the root bed; dark underside."""
    floor(r)
    kerb(r, beds=((0, 1), (0, 2), (0, 3), (1, 3), (0, 4)))
    for y in range(1, 10):
        for (x, z) in ((1, 0), (2, 0), (1, 1), (2, 1), (1, 2), (2, 2), (3, 0), (3, 1)):
            if y <= 9 - (x == 3) * 2:
                r.log(x, y, z)
    roots(r, ((0, 1, 1), (0, 1, 2), (0, 2, 2), (0, 1, 3), (1, 1, 3), (0, 1, 4), (3, 1, 2), (4, 1, 1), (4, 2, 1),
              (0, 2, 0), (0, 3, 0)))
    for y in range(7, 10):                    # a bough out east to the second lobe
        r.log(3 + (y - 7) * 2, y, 1, axis="x")
        r.log(4 + (y - 7) * 2, y, 1, axis="x")
    dome(r, ((2.8, 1.5, 10.6, 3.7, 2.7, 2.9), (6.9, 1.8, 9.6, 2.2, 2.3, 1.9)))
    pier(r, 7, 8, 4, 4, 6)
    r.set(8, 7, 4, lg.MSB)
    pier(r, 7, 7, 6, 6, 2)


def option_r2b(r):
    """R2b CENTRED DOME WITH A BITE: trunk x 3..5 z 0..1 to y 9, a root foot creeping south-west over the west half
    (x 0..2, z 1..3); one round crown, high point over the trunk, only the spawn strip bitten out; a broken east pier
    (x 7..8, z 4, to y 6/7), a low west stub (x 0, z 5, y 1..3); kerb rim, podzol in the root bed; dark underside."""
    floor(r)
    kerb(r, beds=((0, 2), (0, 3), (0, 1)))
    trunk(r, (3, 4, 5), (0, 1), 9)
    roots(r, ((2, 1, 0), (2, 2, 0), (2, 1, 1), (1, 1, 1), (1, 1, 2), (0, 1, 2), (0, 1, 3), (2, 2, 1), (6, 1, 0),
              (6, 1, 1), (6, 2, 0)))
    dome(r, ((4.0, 1.4, 10.7, 4.0, 2.8, 2.9),))
    pier(r, 7, 8, 4, 4, 6)
    r.set(8, 7, 4, lg.MSB)
    pier(r, 0, 0, 5, 5, 3)


def build(draw, name):
    r = lg.Rig(lg.YMAX)
    draw(r)
    crown(r)
    if name.startswith("r2"):
        dark_hem(r)
    for p in lg.CLEAR:
        r.b.pop(p, None)
    r.settle()
    for (x, z) in lg.CENTRE:
        assert all((x, y, z) not in r.b for y in range(1, lg.YMAX)), name + ": spawn column covered"
    r.size = (SX, max(p[1] for p in r.b) + 1, SZ)
    return r.structure()


if __name__ == "__main__" and len(sys.argv) == 2:
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    for n, f in (("a", option_a), ("b", option_b), ("c", option_c), ("a1", option_a1), ("a2", option_a2),
                 ("r2a", option_r2a), ("r2b", option_r2b)):
        s = build(f, n)
        s.write(os.path.join(out, "massing_%s.nbt" % n))
        print(n, s.size, sum(1 for i in s.blocks.values() if s.palette[i][0] != "air"))
