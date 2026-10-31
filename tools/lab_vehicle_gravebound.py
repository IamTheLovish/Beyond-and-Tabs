#!/usr/bin/env python3
"""The Gravebound VEHICLE LAB, THE CHARNEL WORKS - round 2 of the concept (Oct 31 2026), on the r12 method: ONE primary
reference imitated closely, the silhouette settled by a blind greybox comparison with round 1 as the control, camera
tests first, then detail rounds measured on the eyes stills. Writes design/vehicle_labs/gravebound_l1.nbt and
gravebound_l3.nbt only - NOT into the game's structure trees (the game does not load them). Round 1 (4/10) is kept as
design/vehicle_labs/gravebound_l1_round1.nbt / gravebound_l3_round1.nbt (the control) and in git history.

THE PICTURE (ref24, design/refs/vehicle_labs/ref24_black_fortress_red_heart.png; designer pass
itachi2-api/runs/20261030-211330-gravebound-r1-design): a near-black gothic front climbing as one pyramid to ONE central
needle, two lower flanking needles (~75 %), ONE glowing heart at the centre of the front over the gate, vertical ribs
on every face, dead trees at its feet. Gravebound plays it in soul green with pale bone as the frame (ref26's pale crag
under the dark walls: tuff, no plinth line).

THE MASSING (greybox A, tools/gravebound_massing.py; both blind critics ranked it first over round 1 and B / C, runs
20261030-214043 / -214044, 6 / 6 / 6-7 / 6 / 6 on RTS / landmark / gate / roofs / resemblance). Their shared massing
faults and what this round does about each:
  1. "the central landmark is a stub", "three similar square pyramids": the spire now tapers in stages - a 5 x 5 shaft
     with bone arris pilasters to 18, a crown, a 5 x 5 then 3 x 3 steep cap, a needle to 25 - and the flanks echo it
     smaller (5 x 5 towers, 5 x 5 / 3 x 3 caps, needle 17).
  2. "the front is sheer boxes with caps, not a climbing pyramid": the front steps up - towers 17, the heart wall and
     its coping 18, the spire 25 - and the gate posts, arch ring and heart ring are one bone portal 7 wide.
  3. "the light focus sits on the vault", "a flat grey deck", "the gate floor reads lighter than the walls", "no heart
     glow": the hall gets a pitched slate roof (ridge along z, a gable on the south face), three bone ribs (not four)
     capped with dark slabs (bone tops read as snow, 0.839); a 7 x 7 heart rose of lime glass lit from behind; the lane
     floor in deepslate tiles (0.22, under the paving's 0.30) and the hall's west wall closed behind the lane.

THE PLAN (x 0..16 west -> east, z 0..16 north -> south; the RTS camera looks from the south-west at the roofs, the
west face x 0 and the south face z 16):
  towers   x 0..4, z 0..4 and z 12..16: walls to 12, a slate cornice, steep caps 13..16, needle 17 (L1: 8 / 9..12 / 13)
  front    x 1..6, z 5..11, walls to 10 over the lane; bone gate posts x 0..1 at z 5 / 11 (y 1..10), the bone arch ring
           y 9..10; the HEART a 7 x 7 rose at x 0 (y 11..17, L1 5 x 5 at 11..15): a bone ring, lime glass, verdant
           froglights behind it (the one light); a coped heart wall (18)
  spire    x 2..6, z 6..10 (the landmark on the lane's axis): shaft 11..18 with bone arris pilasters, a crown 19, steep
           caps 20..23, needle 24..25 (L1: a crown 11, steep caps 12..15)
  wings    x 5..6 at z 0..4 / 12..16: lean-tos against the towers
  well     x 7..8: open to the sky (the spawn column (8, y, 8)); low links at z 0..4 / 12..14
  hall     x 9..16, z 2..14: walls to 9, a steep slate roof ridge along z (x 12..13, 16), a gable on the south face; bone
           ribs at z 5 / 8 / 11 across it, apex 19 (L1: walls to 6, a low roof to 10, ribs z 5 / 11 apex 14)
  crag     tuff at the feet of every camera face (a battered course, then a tuff course) and the terraces z 15..16
Faces: vertical ribs of polished blackstone wall every 2-3 columns (ref24's ribbing), lit lime lancets with froglights
behind, tuff feet. Light: only the heart and the lancets (froglights behind glass); no lava, no bare lanterns.

Usage: python3 tools/lab_vehicle_gravebound.py   (writes both .nbt and checks them)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs  # noqa: E402

SX = SZ = 17
H_MAX, H_L1 = 26, 16
LANE_X, LANE_Z, LANE_H = range(0, 9), range(6, 11), 8
SPAWN = (8, 1, 8)
OUT1 = "design/vehicle_labs/gravebound_l1.nbt"
OUT3 = "design/vehicle_labs/gravebound_l3.nbt"

WALL = "deepslate_bricks"
CORE = "deepslate_tiles"
DRESS = "polished_blackstone_bricks"
DRESS_ST = "polished_blackstone_brick_stairs"
DRESS_SL = "polished_blackstone_brick_slab"
RIB_POST = "polished_blackstone_brick_wall"
NEEDLE = "polished_blackstone_wall"
BONE = "bone_block"
ROOF = "mcwroofs:deepslate"
CRAG = "tuff"
CRAG_ST = "tuff_stairs"
CRAG_SL = "tuff_slab"
PAVE = "polished_deepslate"
LANE_FLOOR = "deepslate_tiles"
GLASS = "lime_stained_glass"
LANCET_GLASS = "gray_stained_glass"     # the lancets: smoke glass lit from behind (green only in the heart: the one accent)
GLOW = "verdant_froglight"
CORNICE = "reignofnether:cornice_slate"
DRY = dict(waterlogged="false")
POST = dict(up="true", north="none", south="none", east="none", west="none", **DRY)
INWARD = {"west": (1, 0), "south": (0, -1)}


class Grid:
    def __init__(self, h):
        self.h = h
        self.b = {}

    def set(self, x, y, z, name, **props):
        assert 0 <= x < SX and 0 <= z < SZ and 0 <= y < self.h, ((x, y, z), name)
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

    def slab(self, x, y, z, name=DRESS_SL, kind="bottom"):
        self.set(x, y, z, name, type=kind, **DRY)

    def steep(self, x, y, z, facing, shape="straight"):
        """A Macaw's steep-roof step: the lower piece at y, the upper at y + 1 (2 rise per run), rising `facing`."""
        self.set(x, y, z, ROOF + "_steep_roof", facing=facing, half="bottom", shape=shape)
        self.set(x, y + 1, z, ROOF + "_upper_steep_roof", facing=facing, half="bottom", shape=shape)

    def roof(self, x, y, z, facing):
        self.set(x, y, z, ROOF + "_roof", facing=facing, half="bottom", shape="straight")


def face_cell(face, plane, u, y):
    """West faces run along z (u = z) at x = plane; south faces along x (u = x) at z = plane."""
    return (plane, y, u) if face == "west" else (u, y, plane)


def behind(face, q, k=1):
    dx, dz = INWARD[face]
    return (q[0] + dx * k, q[1], q[2] + dz * k)


# ============================================================================================ pieces
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


def cone(g, x0, x1, z0, z1, y, needle_to=None):
    """A staged steep cap: rings of steep steps closing inward 2 rows each (5 x 5 -> 3 x 3), then a needle of blackstone
    wall on the last cell up to `needle_to`; returns the top y."""
    while x1 - x0 >= 2 and z1 - z0 >= 2:
        for (x, z), f, sh in ring_cells(x0, x1, z0, z1):
            g.steep(x, y, z, f, sh)
        for x in range(x0 + 1, x1):
            for z in range(z0 + 1, z1):
                g.set(x, y, z, CORE)
                g.set(x, y + 1, z, CORE)
        x0, x1, z0, z1, y = x0 + 1, x1 - 1, z0 + 1, z1 - 1, y + 2
    top = needle_to if needle_to is not None else y
    for yy in range(y, top + 1):
        g.set(x0, yy, z0, NEEDLE, **POST)
    return top


def lancet(g, face, plane, u, y0, y1):
    """A 1-wide lit lancet: smoke glass y0..y1 in the face (full blocks: past a pane's thin plane the camera saw the lamp), verdant froglights one cell behind (the room's light - the
    3/4 camera never sees a lamp more than one block behind a pane), dark slab head and sill. Smoke glass: lime in the
    lancets too put the green over the designer's 4 % (5.3 % rts on the pane's camera colour) - green is the heart's."""
    for y in range(y0, y1 + 1):
        q = face_cell(face, plane, u, y)
        g.set(*q, LANCET_GLASS)
        g.set(*behind(face, q), GLOW)
    g.slab(*face_cell(face, plane, u, y1 + 1), DRESS_SL, "top")
    g.slab(*face_cell(face, plane, u, y0 - 1), DRESS_SL, "top")


def ribbed_face(g, face, plane, u0, u1, y_top, ribs, lancets=(), y_lancet=(5, 8), crag=True, cornice=True):
    """Dress one camera face in its plane: tuff feet (a battered stair course y 1, a tuff course y 2), the wall field in
    deepslate bricks, vertical ribs of blackstone wall at the `ribs` columns (y 2..top - 1), lit lancets at the
    `lancets` columns, a slate cornice on the top course."""
    for u in range(u0, u1 + 1):
        for y in range(1, y_top + 1):
            q = face_cell(face, plane, u, y)
            if crag and y == 1:
                g.stair(*q, {"west": "east", "south": "north"}[face], "bottom", CRAG_ST)
            elif crag and y == 2 and u not in ribs:
                g.set(*q, CRAG)
            elif u in ribs and y < y_top:
                g.set(*q, RIB_POST, **POST)
                g.set(*behind(face, q), WALL)
            else:
                g.set(*q, WALL)
        if cornice:
            g.set(*face_cell(face, plane, u, y_top), CORNICE, facing=face, shape="straight")
    for u in lancets:
        lancet(g, face, plane, u, *y_lancet)


def rib(g, z, x0, x1, y_foot, apex):
    """A bone rib across x0..x1 at z: an arc of bone columns, every column capped with a dark slab (bone tops read as
    snow on camera: up 0.839); each column reaches down to its lower neighbour's cap, so they touch face to face through
    the slab (nothing floats) and no course runs 4 full bone blocks along the rib."""
    xc, half = (x0 + x1) / 2.0, (x1 - x0) / 2.0
    ys = [int(round(y_foot + (apex - y_foot) * (1 - ((x - xc) / half) ** 2))) for x in range(x0, x1 + 1)]
    for i, x in enumerate(range(x0, x1 + 1)):
        lo = ys[i]
        for j in (i - 1, i + 1):
            if 0 <= j < len(ys) and ys[j] < ys[i]:
                lo = min(lo, ys[j] + 1)          # down to the lower neighbour's cap: they touch through its slab
        for yy in range(lo, ys[i] + 1):
            g.set(x, yy, z, BONE, axis="y")
        g.slab(x, ys[i] + 1, z)


# ============================================================================================ the masses
def ground(g):
    for x in range(SX):
        for z in range(SZ):
            g.set(x, 0, z, LANE_FLOOR if (z in LANE_Z and x <= 8) or (x in (7, 8) and 5 <= z <= 11) else PAVE)


def tower(g, z0, l3):
    """A flanking tower x 0..4, z0..z0+4: ribbed west face (and south face for the south tower), a lit lancet, the
    slate cornice, a staged steep cap and a needle."""
    top = 12 if l3 else 8
    g.box(0, 4, 1, top, z0, z0 + 4, WALL)
    ribbed_face(g, "west", 0, z0, z0 + 4, top, ribs=(z0 + 1, z0 + 3), lancets=(z0 + 2,),
                y_lancet=(6, 9) if l3 else (4, 6))
    if z0 == 12:
        ribbed_face(g, "south", 16, 0, 4, top, ribs=(1, 3), lancets=(2,), y_lancet=(6, 9) if l3 else (4, 6))
    cone(g, 0, 4, z0, z0 + 4, top + 1, needle_to=top + 5)


def front(g, l3):
    """The frontispiece x 1..6, z 5..11 over the lane; the bone portal (posts, arch ring, heart ring); the lean-to
    strips beside the spire."""
    g.box(1, 6, 1, 10, 5, 11, WALL)
    for z, f in ((5, "south"), (11, "north")):
        for x in range(2, 7):
            g.steep(x, 11, z, f)
        g.set(1, 11, z, WALL)
        g.set(1, 12, z, WALL)
    # the gate passage is 7 wide (z 5..11) inside the 5-wide lane rule, so the 3/4 camera, which looks in at 45 degrees,
    # sees deep into it (round 1: 60 pixels, greybox A: 71); the bone posts stand in the towers' inner columns
    for x in range(0, 9):
        for z in range(5, 12):
            for y in range(1, 9):
                g.clear(x, y, z)
    for x in (0, 1):                     # the bone portal: posts x 0..1 at z 4 / 12 (y 1..10), a dark arch head 9..10
        for z in (4, 12):
            for y in range(1, 11):
                g.set(x, y, z, BONE, axis="y")
        for z in range(5, 12):
            g.stair(x, 9, z, "north" if z < 8 else "south" if z > 8 else "east", "top")
            g.stair(x, 10, z, "east", "top")
    for x in range(2, 9):                # the passage's side walls ribbed: a blackstone post every second cell
        for z, zf in ((4, 5), (12, 11)):
            for y in range(1, 9):
                if g.get(x, y, z) != "air" and x % 2 == 0:
                    g.set(x, y, z, RIB_POST, **POST)
    heart(g, l3)


def heart(g, l3):
    """The heart over the gate at x 0: a bone rose ring (7 x 7 at L3, y 11..17; 5 x 5 at L1, y 11..15), lime glass in
    it, verdant froglights behind (x 1) - the one glowing core; rounded corners in stairs; blackstone rib posts at
    z 5 / 11 on the L1 heart wall; the coping."""
    r = 3 if l3 else 2
    yc = 11 + r
    for dz in range(-r, r + 1):
        for dy in range(-r, r + 1):
            z, y = 8 + dz, yc + dy
            if abs(dz) == r and abs(dy) == r:
                g.stair(0, y, z, "north" if dz < 0 else "south", "top" if dy < 0 else "bottom")
            elif dz == 0 and abs(dy) == r:
                g.stair(0, y, z, "east", "top" if dy < 0 else "bottom")     # the ring's keystones
            elif abs(dz) == r or abs(dy) == r or (abs(dz) == r - 1 and abs(dy) == r - 1):
                g.set(0, y, z, BONE, axis="y")
            else:
                g.set(0, y, z, GLASS)
                g.set(1, y, z, GLOW)
            if g.get(1, y, z) == "air":
                g.set(1, y, z, WALL)
    if not l3:
        for z in (5, 11):
            for y in range(11, 16):
                g.set(0, y, z, RIB_POST, **POST)
                g.set(1, y, z, WALL)
    else:
        for z in range(5, 12):                                  # the coping over the 7 x 7 ring
            g.stair(0, 18, z, "east")
            g.set(1, 18, z, WALL)


def spire(g, l3):
    """The landmark on the lane's axis, x 2..6, z 6..10: a shaft with bone arris pilasters, a crown, a staged steep cap,
    a needle; one lit lancet on its south face."""
    top = 18 if l3 else 10
    g.box(2, 6, 11, max(top, 11), 6, 10, WALL)
    for x in (2, 6):
        for z in (6, 10):
            for y in range(11, top + 1):
                g.set(x, y, z, BONE, axis="y")
    if l3:
        for x in (3, 5):                                         # south face ribs between the arrises
            for y in range(13, top + 1):
                g.set(x, y, 10, RIB_POST, **POST)
                g.set(x, y, 9, WALL)
        lancet(g, "south", 10, 4, 14, 16)
        for x in range(2, 7):
            for z in range(6, 11):
                g.set(x, 19, z, CORE)
        for x in range(2, 7):
            g.set(x, 19, 10, CORNICE, facing="south", shape="straight")
        for z in range(6, 11):
            g.set(2, 19, z, CORNICE, facing="west", shape="straight")
        cone(g, 2, 6, 6, 10, 20, needle_to=25)
    else:
        for x in range(2, 7):
            for z in range(6, 11):
                g.set(x, 11, z, DRESS if x in (2, 6) or z in (6, 10) else CORE)
        cone(g, 2, 6, 6, 10, 12, needle_to=15)


def wings(g, l3):
    """Lean-tos x 5..6 against the towers, at z 0..4 and 12..16; the south one's face ribbed."""
    top = 8 if l3 else 6
    for z0 in (0, 12):
        g.box(5, 6, 1, top, z0, z0 + 4, WALL)
        for z in range(z0, z0 + 5):
            g.steep(6, top + 1, z, "west")
            g.set(5, top + 1, z, CORE)
            g.set(5, top + 2, z, CORE)
            g.steep(5, top + 3, z, "west")
    ribbed_face(g, "south", 16, 5, 6, top, ribs=())


def links(g):
    """Low walls x 7..8 at z 0..4 and 12..14 between the front and the hall, round the open well."""
    for zs in (range(0, 5), range(12, 15)):
        for z in zs:
            for x in (7, 8):
                for y in range(1, 6):
                    g.set(x, y, z, WALL)
                g.slab(x, 6, z)
    ribbed_face(g, "south", 14, 7, 8, 5, ribs=(), cornice=False)


def hall(g, l3):
    """The vault hall x 9..16, z 2..14: walls, a steep slate roof ridge along z (L1: a low roof), the gable on the south
    face (z 14) ribbed and lit, the bone ribs across it."""
    top = 9 if l3 else 6
    g.box(9, 16, 1, top, 2, 14, WALL)
    if l3:
        prof = {9: 10, 10: 12, 11: 14, 14: 14, 15: 12, 16: 10}
        for x, y in prof.items():
            for z in range(2, 15):
                if y > top + 1:
                    g.box(x, x, top + 1, y - 1, z, z, CORE)
                g.steep(x, y, z, "east" if x < 12 else "west")
        for x in (12, 13):
            for z in range(2, 15):
                g.box(x, x, top + 1, 15, z, z, CORE)
                g.set(x, 16, z, ROOF + "_top_roof", part="switched_90")
    else:
        prof = {9: 7, 10: 8, 11: 9, 14: 9, 15: 8, 16: 7}
        for x, y in prof.items():
            for z in range(2, 15):
                if y > top + 1:
                    g.box(x, x, top + 1, y - 1, z, z, CORE)
                g.roof(x, y, z, "east" if x < 12 else "west")
        for x in (12, 13):
            for z in range(2, 15):
                g.box(x, x, top + 1, 9, z, z, CORE)
                g.set(x, 10, z, ROOF + "_top_roof", part="switched_90")
    ribbed_face(g, "south", 14, 9, 16, top, ribs=(11, 14), lancets=(12, 13) if l3 else (12,),
                y_lancet=(4, 7) if l3 else (3, 4))
    if l3:                                                       # the south gable: ribs run up it, a coped rake
        for x, y in prof.items():
            for yy in range(top + 1, y + 2):
                if x in (11, 14):
                    g.set(x, yy, 14, RIB_POST, **POST)
                else:
                    g.set(x, yy, 14, WALL)
            g.stair(x, y + 2, 14, "east" if x < 12 else "west")
        for x in (12, 13):
            for yy in range(top + 1, 17):
                g.set(x, yy, 14, WALL)
            g.slab(x, 17, 14)
    for z in (6, 8, 10, 12):                                     # the hall's west wall, seen down the well: ribbed
        for y in range(1, top):
            g.set(9, y, z, RIB_POST, **POST)
            g.set(10, y, z, WALL)
    for z in range(4, 14):
        g.set(9, top, z, CORNICE, facing="west", shape="straight")
    for z in ((5, 8, 11) if l3 else (5, 11)):
        rib(g, z, 9, 16, top + 1, 19 if l3 else 14)


def crag(g):
    """The pale crag in front of the setbacks: a tuff step at z 15 (x 7..16)."""
    for x in range(7, 17):
        g.slab(x, 1, 15, CRAG_SL)
    for x in range(9, 17):
        for z in (0, 1):
            g.set(x, 1, z, CRAG)


def clear_lane_and_spawn(g):
    for x in LANE_X:
        for z in LANE_Z:
            for y in range(1, LANE_H + 1):
                g.clear(x, y, z)
    for y in range(1, g.h):
        g.clear(SPAWN[0], y, SPAWN[2])


def draw(level):
    l3 = level == 3
    g = Grid(H_MAX if l3 else H_L1)
    ground(g)
    hall(g, l3)
    links(g)
    wings(g, l3)
    for z0 in (0, 12):
        tower(g, z0, l3)
    front(g, l3)
    spire(g, l3)
    crag(g)
    clear_lane_and_spawn(g)
    return g


def to_rig(g):
    h = max(y for (_, y, _) in g.b) + 1
    rig = gs._SunRig(SX, h, SZ)
    for (x, y, z), (n, props) in g.b.items():
        rig.set(x, y, z, n, **props)
    return rig


def levels():
    """(L1 grid, L3 grid): L3 keeps every L1 cell non-air (it only adds or replaces)."""
    g1 = draw(1)
    g3 = draw(3)
    for q, v in g1.b.items():
        if q not in g3.b:
            g3.b[q] = v
    return g1, g3


def build_level(level):
    """The rig of one level, written nowhere (tools/test_gravebound_lab.py builds the lab through this)."""
    g1, g3 = levels()
    rig = to_rig(g1 if level == 1 else g3)
    rig.settle()
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


def check(rig, path, tag, h_max):
    import audit_structure_pockets as ap
    n = {k: v[0] for k, v in rig.b.items()}
    assert rig.size[1] <= h_max, (tag, rig.size)
    assert all((x, 0, z) in n for x in range(SX) for z in range(SZ)), "a hole in the foundation"
    busy = [(x, y, z, n[(x, y, z)]) for x in LANE_X for z in LANE_Z for y in range(1, LANE_H + 1) if (x, y, z) in n]
    assert not busy, "%s: the exit lane is narrowed: %s" % (tag, busy[:8])
    col = [y for y in range(1, rig.size[1]) if (SPAWN[0], y, SPAWN[2]) in n]
    assert not col, "%s: the spawn column is blocked at %s" % (tag, col)
    assert not floating(n), "%s: floating %s" % (tag, floating(n)[:8])
    bad = sorted({v for v in n.values() if any(f in v for f in ("lava", "magma", "fire", "torch", "dirt", "podzol",
                                                                "_log", "copper", "calcite", "diorite", "quartz"))})
    assert not bad, "forbidden: %s" % bad
    one_way, ground_, roof, rooms, note = ap.audit(tag, path)
    assert not one_way and not ground_ and "TRAPPED" not in note, (one_way[:8], ground_[:8], note)
    pad = ap.Pad(ap.load_nbt(path))
    seen, stack, out_west = {SPAWN}, [SPAWN], False
    while stack:
        c = stack.pop()
        if c[0] < 0 and c[2] in LANE_Z:
            out_west = True
            break
        for nb in ap.neighbours(pad, *c):
            if nb not in seen and -3 <= nb[0] <= SX + 2 and -3 <= nb[2] <= SZ + 2 and 1 <= nb[1] <= rig.size[1]:
                seen.add(nb)
                stack.append(nb)
    assert out_west, "the spawn does not walk out of the west face"
    return "%s %s, %d blocks: lane clear, spawn column clear, nothing floats, no pockets (%d roof-only cells)%s" % (
        tag, "x".join(map(str, rig.size)), len(n), len(roof), note)


def build():
    os.makedirs(os.path.dirname(OUT1), exist_ok=True)
    g1, g3 = levels()
    out = []
    for g, path, tag, hm in ((g1, OUT1, "gravebound_l1", H_L1), (g3, OUT3, "gravebound_l3", H_MAX)):
        rig = to_rig(g)
        rig.settle()
        rig.structure().write(path)
        out.append(check(rig, path, tag, hm))
    lost = [q for q in g1.b if q not in g3.b]
    assert not lost, "L3 drops L1 cells: %s" % lost[:8]
    print("\n".join(out))


if __name__ == "__main__":
    build()
