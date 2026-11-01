#!/usr/bin/env python3
"""The Ashborn (Ironhide Horde) capitol, the Central Portal, redrawn - the last Reign of Nether capitol in the game.

lovish, Oct 19 2026 (design/building-revamp-progress.md): Ashborn "must NOT resemble Reign of Nether's Piglins: no
pigs, gold, gilded blackstone, nether brick, portals"; its kit is basalt + tuff + obsidian, magma cracks, bronze trim,
fire bowls, "stepped volcano-temples"; capitols "a lot bigger", "the center piece of the base". The shipped Central
Portal is still RoN's nether-brick portal arch (11 x 16 x 9) with an overlay: two fresh critics on eyes run
41263811904 scored it 2/10 ("red nether portal on a red pad"), the lowest of the 14 capitols and labs.

This file draws the replacement at the other capitols' size (15 x 22 x 15). Stage 1 (this commit): three greybox
massings for a blind critic pick against the shipped portal (the r12 method: silhouette before detail).

Rules the drawing keeps (CentralPortal.java):
- CentralPortal.tick lights FIRE at centrePos + (-1, 0, 0) (rotations 0 / 180) or (0, -1, 0) (90 / 270) when that cell
  is air. centrePos = the box's middle: x = z = 7 here, y = 10 or 11 (an odd box height and the int truncation of
  negative world y). Every option keeps those cells - FIRE_CELLS - solid, so no stray fire is lit.
- The foundation layer (y 0) holds the starting block type (CentralPortal.startingBlockTypes).

    python3 tools/capitol_central_portal.py greybox OUT_DIR    -> OUT_DIR/central_portal_massing_{a,b,c}.nbt
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_structures import Structure  # noqa: E402

SX, SY, SZ = 15, 22, 15
C = 7
FIRE_CELLS = [(6, 10, 7), (6, 11, 7), (8, 10, 7), (8, 11, 7), (7, 9, 7), (7, 10, 7), (7, 11, 7)]
START = "polished_blackstone_bricks"     # the foundation / starting type

# greybox role blocks (plain, no ornament: the critic judges silhouette and value groups)
G_PLINTH = "blackstone"
G_MASS = "polished_deepslate"
G_CAP = "smooth_stone_slab"
G_CONE = "basalt"
G_GLOW = "shroomlight"


class Canvas:
    def __init__(self):
        self.b = {}

    def set(self, x, y, z, name, **p):
        assert 0 <= x < SX and 0 <= y < SY and 0 <= z < SZ, (x, y, z, name)
        self.b[(x, y, z)] = (name, p)

    def get(self, x, y, z):
        return self.b.get((x, y, z), ("air", {}))[0]

    def fill(self, x1, y1, z1, x2, y2, z2, name, **p):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.set(x, y, z, name, **p)

    def clear(self, x1, y1, z1, x2, y2, z2):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.b.pop((x, y, z), None)

    def structure(self):
        s = Structure(SX, SY, SZ)
        for x in range(SX):          # every cell, air too: the box is the building's (RoN's structures carry air)
            for y in range(SY):
                for z in range(SZ):
                    n, p = self.b.get((x, y, z), ("air", {}))
                    s.set(x, y, z, n, **p)
        return s


def _slab(c, x, y, z, name=G_CAP):
    c.set(x, y, z, name, type="bottom", waterlogged="false")


def _cap_ring(c, lo, hi, y, name=G_CAP):
    for x in range(lo, hi + 1):
        for z in range(lo, hi + 1):
            if x in (lo, hi) or z in (lo, hi):
                _slab(c, x, y, z, name)


def _pad(c):
    c.fill(0, 0, 0, SX - 1, 0, SZ - 1, START)


def greybox_a():
    """A - stepped volcano-temple: three square tiers (15, 11, 7) each ringed by a light cap, a 5 x 5 crater cone on
    top with ONE glow in the crater; the gate a 5 wide, 5 tall mouth cut 3 deep into the west face of tier 1 under a
    pointed crest on tier 2."""
    c = Canvas()
    _pad(c)
    c.fill(0, 1, 0, 14, 5, 14, G_PLINTH)
    _cap_ring(c, 0, 14, 6)
    c.fill(2, 6, 2, 12, 10, 12, G_MASS)
    _cap_ring(c, 2, 12, 11)
    c.fill(4, 11, 4, 10, 14, 10, G_MASS)
    _cap_ring(c, 4, 10, 15)
    c.fill(5, 15, 5, 9, 17, 9, G_CONE)
    c.fill(6, 18, 6, 8, 20, 8, G_CONE)
    c.set(7, 20, 7, "air")
    c.set(7, 19, 7, G_GLOW)
    # the gate: a 5 x 5 mouth into tier 1, its crest a stepped point on tier 2's west face
    c.clear(0, 1, 5, 2, 5, 9)
    for y, (z1, z2) in zip(range(6, 13), ((4, 10), (4, 10), (5, 9), (5, 9), (6, 8), (6, 8), (7, 7))):
        c.fill(1, y, z1, 1, y, z2, G_CONE)
    return c


def greybox_b():
    """B - one volcano cone (ref30's form): a round cone from radius 7.3 at the foot to 2.4 at the rim (y 20), a crater
    with ONE glow, two lava channels running down the camera faces (west, south) from the rim to the gate; the gate an
    arched mouth in the west foot."""
    c = Canvas()
    _pad(c)
    top = 20
    for y in range(1, top + 1):
        r = 7.3 - (7.3 - 2.4) * (y - 1) / (top - 1)
        for x in range(SX):
            for z in range(SZ):
                d = math.hypot(x - C, z - C)
                if d <= r:
                    c.set(x, y, z, G_PLINTH if y <= 4 else (G_MASS if y <= 12 else G_CONE))
    for y in range(17, top + 1):           # the crater
        c.set(7, y, 7, "air")
    c.set(7, 17, 7, G_GLOW)
    # lava channels: the outermost cell of the cone along the west and south axes, from the rim down
    for y in range(4, top):
        r = 7.3 - (7.3 - 2.4) * (y - 1) / (top - 1)
        k = int(math.floor(r))
        c.set(C - k, y, C, G_GLOW)          # west
        c.set(C, y, C + k, G_GLOW)          # south
    c.clear(0, 1, 5, 2, 4, 9)               # the west mouth
    c.clear(1, 5, 6, 2, 5, 8)
    return c


def greybox_c():
    """C - citadel (ref29's plan): a 2-thick outer wall (6 tall) with light caps, four 3 x 3 corner towers to 10 under
    dark cones, a sunken court on the west holding ONE glow pool, and a 5 x 5 keep in the middle rising to 18 under a
    cone to 21; the gate 3 wide in the west wall on the keep's axis."""
    c = Canvas()
    _pad(c)
    c.fill(0, 1, 0, 14, 6, 14, G_PLINTH)
    c.clear(2, 1, 2, 12, 6, 12)           # the court
    c.fill(2, 1, 2, 12, 1, 12, G_MASS)    # court floor
    _cap_ring(c, 0, 14, 7)
    for (x0, z0) in ((0, 0), (12, 0), (0, 12), (12, 12)):
        c.fill(x0, 1, z0, x0 + 2, 10, z0 + 2, G_MASS)
        c.fill(x0, 11, z0, x0 + 2, 11, z0 + 2, G_CONE)
        c.set(x0 + 1, 12, z0 + 1, G_CONE)
        c.set(x0 + 1, 13, z0 + 1, G_CONE)
    c.fill(5, 1, 5, 9, 18, 9, G_MASS)      # the keep (covers FIRE_CELLS)
    _cap_ring(c, 5, 9, 19)
    c.fill(6, 19, 6, 8, 20, 8, G_CONE)
    c.set(7, 21, 7, G_CONE)
    c.fill(2, 1, 6, 4, 1, 8, G_GLOW)       # the glow pool on the gate axis
    c.clear(0, 1, 6, 1, 5, 8)              # the west gate
    return c


def check(c):
    for q in FIRE_CELLS:
        assert c.get(*q) != "air", ("fire cell is air", q)
    assert any(c.get(x, 0, z) == START for x in range(SX) for z in range(SZ))
    return sum(1 for v in c.b.values() if v[0] != "air")


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "greybox":
        out = sys.argv[2]
        for tag, fn in (("a", greybox_a), ("b", greybox_b), ("c", greybox_c)):
            c = fn()
            print(tag, "blocks", check(c))
            c.structure().write(os.path.join(out, "central_portal_massing_%s.nbt" % tag))
    else:
        print(__doc__)
