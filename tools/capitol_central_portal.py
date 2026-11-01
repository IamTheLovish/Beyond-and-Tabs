#!/usr/bin/env python3
"""The Ashborn (Ironhide Horde) capitol, the Central Portal, redrawn - the last Reign of Nether capitol in the game.

lovish, Oct 19 2026 (design/building-revamp-progress.md): Ashborn "must NOT resemble Reign of Nether's Piglins: no
pigs, gold, gilded blackstone, nether brick, portals"; its kit is basalt + tuff + obsidian, magma cracks, bronze trim,
fire bowls, "stepped volcano-temples"; capitols "a lot bigger", "the center piece of the base". The shipped Central
Portal is still RoN's nether-brick portal arch (11 x 16 x 9) with an overlay: two fresh critics on eyes run
41263811904 scored it 2/10 ("red nether portal on a red pad"), the lowest of the 14 capitols and labs.

This file draws the replacement at the other capitols' size (15 x 22 x 15): first three greybox massings for a blind
critic pick against the shipped portal (the r12 method: silhouette before detail), then the building.

Massing pick (eyes 41266607714, two blind critics, runs *-central-portal-massing-critic-a / -b): A, the stepped
volcano-temple, ranked 3 > 4 > 1 > 2 by both (RTS 7 / 7, tops 8 / 8, value 8 / 8; the shipped portal last). Their
shared faults on A, which build() answers: the tiers were equal vertical boxes (now unequal heights, a sloped foot of
stairs under every tier, recessed dark bays between basalt piers); the gate was an unframed notch off the axis (now an
obsidian mouth between pylons, on one axis with a stair climbing the tiers to the crater); the crown was a small box
(now a tapered cone with corner posts, the crater glow seen through notches from the south-west, a smoking fire).

Rules the drawing keeps (CentralPortal.java):
- CentralPortal.tick lights FIRE at centrePos + (-1, 0, 0) (rotations 0 / 180) or (0, -1, 0) (90 / 270) when that cell
  is air. centrePos = the box's middle: x = z = 7 here, y = 10 or 11 (an odd box height and the int truncation of
  negative world y). Every option keeps those cells - FIRE_CELLS - solid, so no stray fire is lit.
- The foundation layer (y 0) holds the starting block type (CentralPortal.startingBlockTypes).

    python3 tools/capitol_central_portal.py greybox OUT_DIR    -> OUT_DIR/central_portal_massing_{a,b,c}.nbt
    python3 tools/capitol_central_portal.py                    -> the game's central_portal.nbt (both trees)
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


# ---- the building (round 1) ----
# Values by how the blocks LOOK on camera (design/vehicle_labs/camera_colours.json, gen_blocks TARGETS):
PLINTH = "polished_blackstone_bricks"          # on screen 0.13-0.17: the dark foot
PIER = "polished_basalt"                         # pack 0.36-0.38: the ordered basalt columns of the tiers
BAY = "polished_blackstone"                      # recessed bays between piers: dark slots
BATTER = "deepslate_tile_stairs"                 # the sloped foot under each tier (0.21-0.26 on screen)
CAP = "smooth_stone_slab"                        # tier rims: round 1's stone_slab read 0.428 (grass 0.411, eyes
                                                 # 41268686770); greybox A's smooth stone slabs gave p90 0.643
TERRACE = "tuff"                                 # terrace paving, up 0.34
CONE = "basalt"
CROWN = "smooth_basalt"                          # no vertical grain (round 1: "barrel staves")
RIM = "blackstone_stairs"
GLOW = "shroomlight"                             # ONE glow: the crater
GATE = "obsidian"
AXIS_ST = "polished_blackstone_brick_stairs"     # the processional stair on the gate axis
RAIL = "polished_andesite"                       # light rails either side of the stair (0.45 up)

INWARD = {(-1, 0): "east", (1, 0): "west", (0, -1): "south", (0, 1): "north"}
OUTWARD = {(-1, 0): "west", (1, 0): "east", (0, -1): "north", (0, 1): "south"}
SIDES = ((-1, 0), (1, 0), (0, -1), (0, 1))       # west, east, north, south
WEST = (-1, 0)
POST = dict(up="true", north="none", south="none", east="none", west="none", waterlogged="false")


def _at(o, k, t):
    """The cell k out from the centre along side o, t across it."""
    ox, oz = o
    return C + k * ox - t * oz, C + k * oz + t * ox


def _stair(c, x, y, z, name, facing, half="bottom"):
    c.set(x, y, z, name, facing=facing, half=half, shape="straight", waterlogged="false")


def _block(c, x, y, z, name):
    if name.endswith("basalt"):
        c.set(x, y, z, name, axis="y")
    else:
        c.set(x, y, z, name)


def _tier(c, k, y0, y1, piers, wall, bay_rows):
    """A square tier of half-width k from y0 to y1, solid inside; on every side the cells at |t| in `piers` stand
    proud as piers and the rest of the face over bay_rows is a dark bay one block in."""
    for y in range(y0, y1 + 1):
        for dx in range(-k, k + 1):
            for dz in range(-k, k + 1):
                _block(c, C + dx, y, C + dz, wall)
    for o in SIDES:
        for t in range(-k + 1, k):
            if abs(t) in piers:
                continue
            for y in bay_rows:
                x, z = _at(o, k, t)
                c.set(x, y, z, "air")
                x, z = _at(o, k - 1, t)
                c.set(x, y, z, BAY)


def _rim(c, k, y):
    for o in SIDES:
        for t in range(-k, k + 1):
            x, z = _at(o, k, t)
            _slab(c, x, y, z, CAP)


def _batter(c, k, y, name=BATTER, corner="deepslate_tiles"):
    """A ring of stairs at half-width k sloping down and out (the tier above stands at k - 1)."""
    for o in SIDES:
        for t in range(-k + 1, k):
            x, z = _at(o, k, t)
            _stair(c, x, y, z, name, INWARD[o])
    for sx in (-1, 1):
        for sz in (-1, 1):
            _block(c, C + sx * k, y, C + sz * k, corner)


def _paving(c, k, y):
    for dx in range(-k, k + 1):
        for dz in range(-k, k + 1):
            c.set(C + dx, y, C + dz, TERRACE)


def _gate(c):
    # the gate (west, on the axis z 7): an obsidian mouth 3 wide x 3 tall, 3 deep, between two pylons to y 7
    for t in (-2, 2):
        x, z = _at(WEST, 7, t)
        for y in range(1, 6):
            c.set(x, y, z, GATE)
        c.set(x, 6, z, "polished_basalt", axis="y")
        c.set(x, 7, z, "blackstone_wall", **POST)
    for t in (-1, 0, 1):
        x, z = _at(WEST, 7, t)
        c.set(x, 4, z, GATE)                     # the lintel
        for a in (5, 6, 7):
            x, z = _at(WEST, a, t)
            for y in (1, 2):
                c.set(x, y, z, "air")
            c.set(x, 3, z, "air")
        x, z = _at(WEST, 4, t)
        for y in (1, 2, 3):
            c.set(x, y, z, GATE)                 # the mouth's back wall: the darkest face on the building


def build():
    c = Canvas()
    c.fill(0, 0, 0, 14, 0, 14, START)                                   # y 0: the pad, the starting type
    # T1, the plinth (k 7, y 1-3): blackstone-brick piers and dark bays, tuff paving on top, a light rim
    _tier(c, 7, 1, 3, piers=(7, 4, 1), wall=PLINTH, bay_rows=(1, 2))
    _paving(c, 6, 3)
    _rim(c, 7, 4)
    # T2 (k 5, y 4-8) on a sloped foot at k 6, basalt piers and bays
    _tier(c, 5, 4, 8, piers=(5, 3, 0), wall=PIER, bay_rows=(5, 6, 7))
    _batter(c, 6, 4)
    _paving(c, 4, 8)
    _rim(c, 5, 9)
    # T3 (k 3, y 9-12) on a sloped foot at k 4
    _tier(c, 3, 9, 12, piers=(3, 0), wall=PIER, bay_rows=(10, 11))
    _batter(c, 4, 9)
    _rim(c, 3, 13)
    # the crown (round 2): a low, wide crater - a 5 x 5 smooth-basalt body (y 13-14, no grain stripes), a lip of
    # stairs (y 15) round a 3 x 3 crater sunk one block, its floor the ONE glow (y 14) under a smoking fire; the lip
    # notched on the west and south so the glow shows from the camera side. Round 1's 3 x 3 keep with corner posts
    # read as a rook / a face and its one-block glow as a speck (critics a/b, round 1).
    for y in (13, 14):
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                c.set(C + dx, y, C + dz, CROWN)
    for o in SIDES:
        for t in range(-1, 2):
            x, z = _at(o, 2, t)
            _stair(c, x, 15, z, RIM, OUTWARD[o])
    for sx in (-2, 2):
        for sz in (-2, 2):
            c.set(C + sx, 15, C + sz, "blackstone")
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            c.set(C + dx, 14, C + dz, GLOW)
    c.set(7, 15, 7, "campfire", lit="true", facing="north", signal_fire="false", waterlogged="false")
    for o in (WEST, (0, 1)):                       # notches in the lip: the glow seen from the south-west
        x, z = _at(o, 2, 0)
        c.set(x, 15, z, "air")
    _gate(c)
    # the processional stair on the axis: two 1:1 flights cut into the west faces of T2 and T3 (a groove, light rails
    # either side), ending at a dark shrine door in the crown under the crater - gate, flights, door, crater on one line
    flights = (((6, 5), (5, 6), (4, 7), (3, 8)), ((4, 9), (3, 10), (2, 11)))
    tops = (9, 13)                                # the rim course each flight breaks through
    for steps, top in zip(flights, tops):
        for (a, y) in steps:
            for t in (-1, 0, 1):
                x, z = _at(WEST, a, t)
                _stair(c, x, y, z, AXIS_ST, "east")
                for yy in range(y + 1, top + 1):
                    c.set(x, yy, z, "air")
                for yy in range(4, y):              # a solid ramp under the flight
                    if c.get(x, yy, z) == "air":
                        c.set(x, yy, z, PLINTH)
            for t in (-2, 2):
                x, z = _at(WEST, a, t)
                c.set(x, y, z, RAIL)
                if c.get(x, y + 1, z) == "air":
                    c.set(x, y + 1, z, "polished_blackstone_wall", **POST)
    for y in (13, 14):                              # the shrine door
        x, z = _at(WEST, 2, 0)
        c.set(x, y, z, GATE)
    return c


# ---- round 3 massing options (both round-2 critics: "the biggest gain is massing - battered flanks that narrow to
# the crater"; built with the round-2 materials so the blind pick compares shapes, not detail) ----
FLANK_ST = {PLINTH: "polished_blackstone_brick_stairs", PIER: "deepslate_tile_stairs", CROWN: "blackstone_stairs"}


def option_cone():
    """The stepped cone: one continuous slope - the half-width steps in by one every two rows (k 7 at y 1-2 ... k 2
    at y 11-12), the upper row of every step a ring of stairs (the slope), two light ledges (y 4, y 8), a 3 x 3 crater
    glowing at y 11 under a lip of stairs (y 12-13); the gate mouth and the dark stair groove on the west axis."""
    c = Canvas()
    c.fill(0, 0, 0, 14, 0, 14, START)
    for y in range(1, 13):
        k = 7 - (y - 1) // 2
        mat = PLINTH if y <= 4 else (PIER if y <= 8 else CROWN)
        for dx in range(-k, k + 1):
            for dz in range(-k, k + 1):
                _block(c, C + dx, y, C + dz, mat)
        if y % 2 == 0 and k > 2:
            if y in (4, 8):
                _rim(c, k, y)
                for dx in range(-k + 1, k):
                    for dz in range(-k + 1, k):
                        _block(c, C + dx, y, C + dz, mat)
            else:
                _batter(c, k, y, name=FLANK_ST[mat], corner=mat)
    for dx in range(-1, 2):                         # the crater
        for dz in range(-1, 2):
            c.set(C + dx, 12, C + dz, "air")
            c.set(C + dx, 11, C + dz, GLOW)
    for o in SIDES:
        for t in range(-2, 3):
            x, z = _at(o, 2, t)
            if abs(t) < 2:
                _stair(c, x, 13, z, RIM, OUTWARD[o])
            else:
                c.set(x, 13, z, "blackstone")
    for o in (WEST, (0, 1)):
        x, z = _at(o, 2, 0)
        c.set(x, 13, z, "air")
    c.set(7, 12, 7, "campfire", lit="true", facing="north", signal_fire="false", waterlogged="false")
    _gate(c)
    for y in range(4, 13):                          # the stair groove up the west slope
        a = 7 - (y - 1) // 2
        for t in (-1, 0, 1):
            x, z = _at(WEST, a, t)
            _stair(c, x, y, z, AXIS_ST, "east")
        for t in (-2, 2):
            x, z = _at(WEST, a, t)
            c.set(x, y, z, RAIL)
    return c


def option_peak():
    """The temple under a peak: round 2's three tiers, the crown a tapered cone - 5 x 5 (y 13-14), a sloped ring
    (y 15), 3 x 3 (y 15-17) - with a crater lip (y 18) round a sunk glow and a smoking fire."""
    c = build()
    c.clear(4, 13, 4, 10, 21, 10)
    _rim(c, 3, 13)
    for y in (13, 14):
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                c.set(C + dx, y, C + dz, CROWN)
    _batter(c, 2, 15, name="blackstone_stairs", corner=CROWN)
    for y in (15, 16, 17):
        for dx in range(-1, 2):
            for dz in range(-1, 2):
                c.set(C + dx, y, C + dz, CROWN)
    for o in SIDES:
        x, z = _at(o, 1, 0)
        _stair(c, x, 18, z, RIM, OUTWARD[o])
    for sx in (-1, 1):
        for sz in (-1, 1):
            c.set(C + sx, 18, C + sz, "blackstone")
    c.set(7, 17, 7, GLOW)
    c.set(7, 18, 7, "campfire", lit="true", facing="north", signal_fire="false", waterlogged="false")
    for (a, y) in ((2, 13), (2, 14)):               # the flight still meets the crown: a dark door
        x, z = _at(WEST, a, 0)
        c.set(x, y, z, GATE)
    return c


def check(c):
    for q in FIRE_CELLS:
        assert c.get(*q) != "air", ("fire cell is air", q)
    assert any(c.get(x, 0, z) == START for x in range(SX) for z in range(SZ))
    return sum(1 for v in c.b.values() if v[0] != "air")


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "options":
        for tag, fn in (("cone", option_cone), ("peak", option_peak), ("round2", build)):
            fn().structure().write(os.path.join(sys.argv[2], "central_portal_r3_%s.nbt" % tag))
    elif len(sys.argv) >= 3 and sys.argv[1] == "greybox":
        out = sys.argv[2]
        for tag, fn in (("a", greybox_a), ("b", greybox_b), ("c", greybox_c)):
            c = fn()
            print(tag, "blocks", check(c))
            c.structure().write(os.path.join(out, "central_portal_massing_%s.nbt" % tag))
    else:
        c = build()
        print("blocks", check(c))
        root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        for tree in ("assets", "data"):
            c.structure().write(os.path.join(root, "bar", "src", "main", "resources", tree, "reignofnether",
                                             "structures", "central_portal.nbt"))
