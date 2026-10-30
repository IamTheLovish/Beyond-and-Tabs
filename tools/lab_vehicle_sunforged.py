#!/usr/bin/env python3
"""The Sunforged (White Angel) VEHICLE LAB - THE SUNWAIN COURT - at L1 and L3, r1 of the r12 method (Oct 30 2026).
Here the DAWNRIDER (a light-wielder riding a small cloud, a fast raider) is made and rolled out of the west gate.

Writes design/vehicle_labs/sunforged_l1.nbt and _l3.nbt only - NOT into the game's structure trees (the game does not
load them yet; wiring comes after lovish approves the look). Tests (written first, 6 red on round 2):
tools/test_sunforged_lab.py. Round 2 (the CONTROL, 4/10 from round 1's critic) is kept as sunforged_l{1,3}_round2.nbt
and in git history (this file before Oct 30).

THE REFERENCE, STUDIED (ONE picture: ref21, design/refs/vehicle_labs/ref21_gothic_tower_bartizans.png; the designer
pass, itachi2-api runs 20261030-162547-sunforged-design + -163025-sunforged-design-cont)
  - a square gothic KEEP of warm grey ashlar, crenellated flat tops;
  - CORNER TOWERS proud of the keep, wider than the bay, each face split by pilaster strips into narrow panels with one
    lancet per panel; crowns CORBELLED out one course and crenellated (bartizans), the crown ~1/8 of the tower;
  - the FRONT BAY, ~1/4 of the front, projecting furthest: a pointed door on the axis, a round ROSE over it (our sun
    disc), a crenellated top;
  - ONE steep SPIRE springing behind the bay at about bay-top height - clearly the tallest thing;
  - a low WING; depth from three planes on the front (bay, towers, keep) and the shadow band under each corbelled crown.

THE MASSING (A2: the blind pick A, revised on both massing critics' shared faults; tools/sunforged_massing.py build_a2,
eyes run 41124572036, critics 20261030-165241 / -165245)
Plot 17 x 17 (x 0..16 west -> east, z 0..16 north -> south; the RTS camera looks north-east from the south-west and
sees the roofs, the WEST face x 0 - the front - and the SOUTH face z 16).
  keep         x 3..15, z 3..13, walls to 11, a smooth-stone terrace at 12 inside a crenellated parapet
  front towers x 1..4 at z 1..4 (NW) and z 12..15 (SW): shafts to 13, the corbel table at 14 one out on the outer
               faces, the crown course 15 with arrow loops, merlons 16 - they frame the bay
  back turrets x 13..15 at z 1..3 (NE) and z 13..15 (SE): 3 x 3 to 12, corbel 13, merlons 14 - smaller (hierarchy)
  front bay    x 0..2, z 4..12, to 15, merlons 16: the two-order splayed PORTAL (outer x 0: 7 wide, 9 tall to a
               pointed head and keystone at 12; inner x 1: the lane's 5 x 8) and the ROSE over it (centre y 14)
  spire tower  x 11..15, z 6..10: the shaft rises 4 clear of the terrace (13..16) with belfry lancets, then the SPIRE -
               a 5 x 5 steep foot (17, 18), the 3 x 3 steep body (19..22), a needle (23, 24), the gold finial (25)
  wing         x 5..11, z 14..15, to 5, a pale crenellated terrace at 6
  the well     the spawn column (8, y, 8) only, open to the sky through the terrace inside a GOLD HALO ring (y 12)
  the mouth    the lane x 0..9, z 6..10, y 1..8: blackstone-brick floor, deepslate-brick passage walls and ceiling

THE DETAIL GRAMMAR (round 1 - one grammar on every camera face):
  - a battered first course (stone-brick stairs) round every outer wall;
  - stone-brick QUOINS at every corner, long and short (stone bricks / polished andesite);
  - STOREYS of four courses: two of glass (a 2-wide lancet per panel, 1-wide on the turret), a pointed head (two
    upside-down stairs), our STRING COURSE (#407 reignofnether:string_course_stone) at the storey line; the cornice
    (reignofnether:cornice_stone) at the top course;
  - lit rooms behind the glass (a standing lantern 1 behind each lancet, warm birch above it): light INSIDE the rooms,
    never a light block in the wall face (lovish's dusk rule on the Engine Shed, Oct 30);
  - crowns: an upside-down stair corbel table, arrow loops (iron bars) in the crown course, alternating merlons.
  Palette (by look, camera_colours.json): polished andesite walls (west 0.29 / south 0.38), stone-brick dressings
  (0.29-0.41), smooth-stone-slab terraces (0.47-0.54), deepslate-brick mouth (dark), a muted warm spire (Macaw's
  granite steep roof, hue 16, sat 0.36-0.39 - lovish's call open: the enemy relation colour), gold the one accent.

ANCHORS (structure-local x, y, z; in game these become client-drawn moving parts later):
  gate        (0..1, 1..9, 5..11) the portal; the animated gate leaf hangs in the inner order (x 1)
  spawn cell  (8, 1, 8) the lane floor under the well; the column above it is air to the sky
  sun         (13, 25, 8) the gold finial; (0, 14, 8) the rose (lit behind)

EXIT LANE: x 0..9, z 6..10, y 1..8 clear, straight out west.

THE GROWTH (one footprint; L3 only adds or replaces): L1 (16 tall) - the keep, the bay with its portal and rose, the
towers to their shafts with plain merlons, the wing; L3 (26 tall) - the corbelled crowns, the turrets' corbels, the
bay's merlons and the spire tower with its spire.

Usage: python3 tools/lab_vehicle_sunforged.py   (writes both .nbt and checks them)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs  # noqa: E402

SX = SZ = 17
LANE_X = range(0, 10)
LANE_Z = range(6, 11)
LANE_H = 8
SPAWN = (8, 1, 8)
OUT = "design/vehicle_labs"
H1, H3 = 16, 26
DRY = dict(waterlogged="false")
POST_UP = dict(up="true", north="none", south="none", east="none", west="none", waterlogged="false")

WALL = "polished_andesite"
DRESS = "stone_bricks"
DRESS_ST = "stone_brick_stairs"
DRESS_SL = "stone_brick_slab"
POST = "stone_brick_wall"
QUOIN = ("stone_bricks", "polished_andesite")
TERRACE = "smooth_stone_slab"
PAVE = "polished_andesite"
MOUTH_FLOOR = "polished_blackstone_bricks"
MOUTH_WALL = "deepslate_bricks"
MOUTH_CEIL = "deepslate_tiles"
STRING = "reignofnether:string_course_stone"
CORNICE = "reignofnether:cornice_stone"
GLASS = "glass_pane"
ROSE = "orange_stained_glass"
ROOM = "birch_planks"
SPIRE = "mcwroofs:granite"
SPIRE_CORE = "granite"
GOLD = "gold_block"
LOOP = "iron_bars"
KEY = "chiseled_stone_bricks"

INWARD = {"west": (1, 0), "south": (0, -1)}


class Grid:
    def __init__(self):
        self.b = {}

    def set(self, x, y, z, name, **props):
        assert 0 <= x < SX and 0 <= z < SZ and 0 <= y < H3, ((x, y, z), name)
        self.b[(x, y, z)] = (name, props)

    def clear(self, x, y, z):
        self.b.pop((x, y, z), None)

    def box(self, x0, x1, y0, y1, z0, z1, name, **props):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.set(x, y, z, name, **props)

    def stair(self, x, y, z, facing, half, name=DRESS_ST):
        self.set(x, y, z, name, facing=facing, half=half, shape="straight", **DRY)

    def slab(self, x, y, z, name=TERRACE, kind="bottom"):
        self.set(x, y, z, name, type=kind, **DRY)


def edge(x0, x1, z0, z1):
    return [(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1) if x in (x0, x1) or z in (z0, z1)]


def merlons(g, cells, y):
    for (x, z) in cells:
        if (x + z) % 2 == 0:
            g.set(x, y, z, DRESS)
        else:
            g.slab(x, y, z, DRESS_SL)


# ============================================================================================ the face grammar
def face_cell(face_, plane, u, y):
    """The cell of a face: west faces run along z (u = z) at x = plane; south faces along x (u = x) at z = plane."""
    return (plane, y, u) if face_ == "west" else (u, y, plane)


def behind(face_, q, k=1):
    dx, dz = INWARD[face_]
    return (q[0] + dx * k, q[1], q[2] + dz * k)


def pane_props(face_):
    """A glass pane running along the face's plane."""
    if face_ == "west":
        return dict(north="true", south="true", east="false", west="false", waterlogged="false")
    return dict(east="true", west="true", north="false", south="false", waterlogged="false")


def lamp_column(face_, cols):
    """The lancet column whose room holds the lantern: the camera's rays run +x and -z, so the lamp sits at the lancet's
    lowest z on a west face and its highest x on a south face (each pane then has the lamp within its view)."""
    return min(cols) if face_ == "west" else max(cols)


def lancet(g, face_, plane, cols, yg):
    """Two glass courses in the columns `cols` at yg, yg + 1, the pointed head at yg + 2, the lit room behind."""
    lamp = lamp_column(face_, cols)
    for u in cols:
        for y in (yg, yg + 1):
            q = face_cell(face_, plane, u, y)
            g.set(*q, GLASS, **pane_props(face_))
            if y == yg and u == lamp:
                g.set(*behind(face_, q), "lantern", hanging="false", **DRY)
            else:
                g.set(*behind(face_, q), ROOM)
    a, b = min(cols), max(cols)
    lo, hi = ("north", "south") if face_ == "west" else ("west", "east")
    if a == b:
        g.slab(*face_cell(face_, plane, a, yg + 2), DRESS_SL, "top")
    else:
        g.stair(*face_cell(face_, plane, a, yg + 2), lo, "top")
        g.stair(*face_cell(face_, plane, b, yg + 2), hi, "top")


def face(g, face_, plane, u0, u1, y0, top, lancets, storeys, batter=True):
    """Dress one camera face (u0..u1 at the plane, courses y0..top): the batter course, long-and-short quoins at u0 and
    u1, per storey (glass bottom yg) a lancet in each column group and a string course at yg + 3, the cornice at the
    top course. Everything sits IN the face plane, the wall behind it."""
    for u in range(u0, u1 + 1):
        for y in range(y0, top + 1):
            q = face_cell(face_, plane, u, y)
            g.set(*q, QUOIN[(y + (u == u1)) % 2] if u in (u0, u1) else WALL)
    if batter and y0 == 1:
        for u in range(u0, u1 + 1):
            g.stair(*face_cell(face_, plane, u, 1), {"west": "east", "south": "north"}[face_], "bottom")
    for yg in storeys:
        for cols in lancets:
            lancet(g, face_, plane, cols, yg)
        if yg + 3 < top:
            for u in range(u0, u1 + 1):
                g.set(*face_cell(face_, plane, u, yg + 3), STRING, facing=face_, shape="straight")
    for u in range(u0, u1 + 1):
        g.set(*face_cell(face_, plane, u, top), CORNICE, facing=face_, shape="straight")


# ============================================================================================ the masses
def ground(g):
    for x in range(SX):
        for z in range(SZ):
            g.set(x, 0, z, PAVE)
    for x in range(0, 10):
        for z in LANE_Z:
            g.set(x, 0, z, MOUTH_FLOOR)


def keep(g):
    g.box(3, 15, 1, 11, 3, 13, WALL)
    for x in range(3, 16):
        for z in range(3, 14):
            g.slab(x, 12, z, TERRACE)
    merlons(g, edge(3, 15, 3, 13), 12)


def loop_props(along_z):
    s, t = str(along_z).lower(), str(not along_z).lower()
    return dict(north=s, south=s, east=t, west=t, waterlogged="false")


def corner_tower(g, x0, z0, top, out_x, out_z, w=4, crown=True):
    """A w x w shaft x0..x0+w-1, z0..z0+w-1 to `top`; the corbel table at top + 1 one out on its two outer faces
    (upside-down stairs), on a tower the crown course at top + 2 (arrow loops mid-side), merlons above."""
    x1, z1 = x0 + w - 1, z0 + w - 1
    g.box(x0, x1, 1, top, z0, z1, WALL)
    cx0, cx1 = (x0 - 1, x1) if out_x < 0 else (x0, x1 + 1)
    cz0, cz1 = (z0 - 1, z1) if out_z < 0 else (z0, z1 + 1)
    for x in range(cx0, cx1 + 1):
        for z in range(cz0, cz1 + 1):
            if x0 <= x <= x1 and z0 <= z <= z1:
                g.set(x, top + 1, z, DRESS)
            else:
                f = "east" if x < x0 else "west" if x > x1 else ("south" if z < z0 else "north")
                g.stair(x, top + 1, z, f, "top")
            if crown:
                g.set(x, top + 2, z, DRESS)
    if crown:
        mid_x, mid_z = (cx0 + cx1) // 2, (cz0 + cz1) // 2
        for (x, z) in ((cx0, mid_z), (cx1, mid_z), (mid_x, cz0), (mid_x, cz1)):
            g.set(x, top + 2, z, LOOP, **loop_props(x in (cx0, cx1)))
    merlons(g, edge(cx0, cx1, cz0, cz1), top + 2 + crown)
    return top + 2 + crown


def towers(g):
    corner_tower(g, 1, 1, 13, -1, -1)
    corner_tower(g, 1, 12, 13, -1, 1)
    corner_tower(g, 13, 1, 12, 1, -1, w=3, crown=False)
    corner_tower(g, 13, 13, 12, 1, 1, w=3, crown=False)
    storeys = (2, 6, 10)
    face(g, "west", 1, 1, 3, 1, 13, [(2, 3)], storeys)                 # NW tower, its west face (z 4 is the bay)
    face(g, "west", 1, 12, 15, 1, 13, [(13, 14)], storeys)             # SW tower, west face
    face(g, "south", 15, 1, 4, 1, 13, [(2, 3)], storeys)               # SW tower, south face
    face(g, "south", 15, 13, 15, 1, 12, [(14,)], (2, 6))               # SE turret, south face


def bay(g, level):
    g.box(0, 2, 1, 15, 4, 12, WALL)
    for z in (4, 12):                                                   # the bay's corners: quoins
        for y in range(1, 16):
            g.set(0, y, z, QUOIN[y % 2])
    for z in range(4, 13):
        g.stair(0, 1, z, "east", "bottom")
    for z in (5, 11):                                                   # colonnettes framing the rose
        for y in range(11, 16):
            g.set(0, y, z, POST, **POST_UP)
    if level >= 3:
        merlons(g, edge(0, 2, 4, 12), 16)


def portal(g):
    """Three orders, splayed: the outer at x 0 is 7 wide (z 5..11) and 8 tall, its pointed head springing at 9 (stairs
    over z 6 / 10, open over z 7..9) to the apex at 10 (stairs over z 7 / 9, the keystone over z 8); the middle at x 1
    is 7 wide too under a flat head (9); the inner at x 2 is the lane's 5 x 8, colonnettes in its jambs (z 5, 11) and
    stair springers. Three planes stepping in (ref21's door; the 3/4 camera looks in at 45 degrees). Behind: the mouth -
    deepslate-brick passage walls and back wall, a deepslate-tile ceiling."""
    for z in range(5, 12):
        for y in range(1, 9):
            g.clear(0, y, z)
    for z in (7, 8, 9):
        g.clear(0, 9, z)
    for z, f in ((6, "north"), (10, "south")):
        g.stair(0, 9, z, f, "top")
    for z, f in ((7, "north"), (9, "south")):
        g.stair(0, 10, z, f, "top")
    g.set(0, 10, 8, KEY)
    for z in (5, 11):                                                   # the middle order: 7 wide, flat head
        for y in range(1, 9):
            g.clear(1, y, z)
    for z in range(5, 12):
        g.set(1, 9, z, DRESS)
    for z in (5, 11):                                                   # the inner order: colonnettes, its head
        for y in range(1, 9):
            g.set(2, y, z, POST, **POST_UP)
    for z, f in ((6, "north"), (10, "south")):
        g.stair(2, 9, z, f, "top")
    for x in range(3, 10):
        for y in range(1, 9):
            for z in (5, 11):
                g.set(x, y, z, MOUTH_WALL)
        for z in LANE_Z:
            g.set(x, 9, z, MOUTH_CEIL)
    for z in (7, 8, 9):
        g.set(2, 9, z, MOUTH_CEIL)
    for z in LANE_Z:
        for y in range(1, LANE_H + 1):
            g.set(10, y, z, MOUTH_WALL)


ROSE_C = (13, 8)        # (y, z) of the rose's centre on the bay face x 0


def rose(g):
    """The sun disc: a round 5 x 5 rose over the portal - a gold boss, eight amber panes round it, a stone ring whose
    curved quarters are stairs (the corners left as wall), lit behind by three shroomlights (the glow is the rose's
    own, through its glass)."""
    yc, zc = ROSE_C
    for dz in range(-2, 3):
        for dy in range(-2, 3):
            d = dz * dz + dy * dy
            y, z = yc + dy, zc + dz
            if d == 0:
                g.set(0, y, z, GOLD)
            elif d <= 2:
                g.set(0, y, z, ROSE)
            elif d == 4:
                g.set(0, y, z, DRESS)
            elif d == 5:
                g.stair(0, y, z, "north" if dz < 0 else "south", "bottom" if dy < 0 else "top")
    for y in (yc - 1, yc, yc + 1):
        g.set(1, y, zc - 1, "shroomlight")


def wing(g):
    g.box(5, 11, 1, 5, 14, 15, WALL)
    for x in range(5, 12):
        for z in (14, 15):
            g.slab(x, 6, z, TERRACE)
    merlons(g, [(x, 15) for x in range(5, 12)], 6)
    face(g, "south", 15, 5, 11, 1, 5, [(6, 7), (9, 10)], (2,))
    face(g, "south", 13, 5, 12, 6, 11, [(6, 7), (10, 11)], (7,), batter=False)     # the keep's south face over it
    for x in range(5, 13):                                              # its base: a string course on the wing roof
        g.set(x, 6, 13, STRING, facing="south", shape="straight")


def spire_tower(g):
    g.box(11, 15, 12, 16, 6, 10, WALL)
    for (u0, f, plane) in ((6, "west", 11), (11, "south", 10)):
        u1, mid = u0 + 4, u0 + 2
        for u in range(u0, u1 + 1):
            g.stair(*face_cell(f, plane, u, 12), {"west": "east", "south": "north"}[f], "bottom")   # its plinth
            g.set(*face_cell(f, plane, u, 13), STRING, facing=f, shape="straight")
            g.set(*face_cell(f, plane, u, 16), CORNICE, facing=f, shape="straight")
            for y in (14, 15):
                q = face_cell(f, plane, u, y)
                if u in (u0, u1):
                    g.set(*q, QUOIN[y % 2])
                elif u == mid:
                    g.set(*q, POST, **POST_UP)
        for cols in ((u0 + 1,), (u1 - 1,)):
            lancet(g, f, plane, cols, 14)
            g.set(*face_cell(f, plane, cols[0], 16), CORNICE, facing=f, shape="straight")
    spire(g, 13, 8, 17)


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


def steep_ring(g, x0, x1, z0, z1, y, pairs=1):
    """Macaw's steep-roof pieces round a rectangle (lower + upper half: 2 rise per run), `pairs` stacked, bed filled."""
    for k in range(pairs):
        yy = y + 2 * k
        for (x, z), f, sh in ring_cells(x0, x1, z0, z1):
            g.set(x, yy, z, SPIRE + "_steep_roof", facing=f, half="bottom", shape=sh)
            g.set(x, yy + 1, z, SPIRE + "_upper_steep_roof", facing=f, half="bottom", shape=sh)
        for x in range(x0 + 1, x1):
            for z in range(z0 + 1, z1):
                g.set(x, yy, z, SPIRE_CORE)
                g.set(x, yy + 1, z, SPIRE_CORE)


def spire(g, cx, cz, y_foot):
    steep_ring(g, cx - 2, cx + 2, cz - 2, cz + 2, y_foot, 1)
    steep_ring(g, cx - 1, cx + 1, cz - 1, cz + 1, y_foot + 2, 2)
    for y in (y_foot + 6, y_foot + 7):
        g.set(cx, y, cz, "deepslate_tile_wall", **POST_UP)
    g.set(cx, y_foot + 8, cz, GOLD)


def well(g):
    for y in range(1, H3):
        g.clear(SPAWN[0], y, SPAWN[2])
    for (x, z) in edge(7, 9, 7, 9):
        g.set(x, 12, z, GOLD)


def lane(g):
    for x in LANE_X:
        for z in LANE_Z:
            for y in range(1, LANE_H + 1):
                g.clear(x, y, z)


# ============================================================================================ L1 / L3
def draw(level):
    g = Grid()
    ground(g)
    keep(g)
    towers(g)
    wing(g)
    bay(g, level)
    portal(g)
    rose(g)
    spire_tower(g)
    lane(g)
    well(g)
    if level < 3:
        to_l1(g)
    return g


def to_l1(g):
    """L1: the towers and turrets stop at their shafts with plain merlons, the spire tower is not built yet, the bay has
    no merlons (L3 adds or replaces only)."""
    for q in [q for q in g.b if q[1] >= 13 and 11 <= q[0] <= 15 and 6 <= q[2] <= 10]:
        del g.b[q]
    for (x0, z0, w, top) in ((1, 1, 4, 13), (1, 12, 4, 13), (13, 1, 3, 12), (13, 13, 3, 12)):
        for q in [q for q in g.b if q[1] > top and x0 - 1 <= q[0] <= x0 + w and z0 - 1 <= q[2] <= z0 + w
                  and not (q[0] <= 2 and 4 <= q[2] <= 12)]:
            del g.b[q]
        merlons(g, edge(x0, x0 + w - 1, z0, z0 + w - 1), top + 1)
    for q in [q for q in g.b if q[1] >= H1]:
        del g.b[q]


# ============================================================================================ build + checks
def to_rig(g, sy, label):
    rig = gs._SunRig(SX, sy, SZ)
    for (x, y, z), (n, props) in g.b.items():
        rig.set(x, y, z, n, **props)
    rig.bury_grass()
    rig.settle()
    rig.check_support(label)
    return rig


def build(level):
    g = draw(level)
    return to_rig(g, max(y for (_, y, _) in g.b) + 1, "sunwain L%d" % level)


def floating(b):
    return [q for q in b if q[1] > 0 and not any((q[0] + dx, q[1] + dy, q[2] + dz) in b for dx, dy, dz in (
        (1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)))]


def check(rig, path, label):
    import audit_structure_pockets as ap
    n = {q: v[0] for q, v in rig.b.items()}
    assert all((x, 0, z) in n for x in range(SX) for z in range(SZ)), "a hole in the foundation"
    busy = [(x, y, z, n[(x, y, z)]) for x in LANE_X for z in LANE_Z for y in range(1, LANE_H + 1) if (x, y, z) in n]
    assert not busy, "the exit lane is narrowed: %s" % busy[:8]
    col = [(y, n[(SPAWN[0], y, SPAWN[2])]) for y in range(1, rig.size[1]) if (SPAWN[0], y, SPAWN[2]) in n]
    assert not col, "the spawn column is not air: %s" % col
    assert not floating(n), "floating: %s" % floating(n)[:8]
    bad = sorted({v for v in n.values() if any(f in v for f in (
        "water", "lava", "sand", "gravel", "_concrete_powder", "fire", "redstone", "tuff_brick", "polished_tuff",
        "sapling", "torch", "cobweb", "scaffolding", "pink", "purple", "magenta", "calcite", "quartz", "diorite"))
        or ("copper" in v and not v.startswith("waxed_"))})
    assert not bad, "forbidden: %s" % bad
    one_way, ground_, roof, rooms, note = ap.audit(label, path)
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
    return "%s %s, %d blocks: lane clear, spawn column clear, no pockets (%d roof-only cells)%s" % (
        label, "x".join(map(str, rig.size)), len(n), len(roof), note)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    r1, r3 = build(1), build(3)
    assert r1.size[1] <= H1 and r3.size[1] <= H3, (r1.size, r3.size)
    n1 = {q: v[0] for q, v in r1.b.items()}
    n3 = {q: v[0] for q, v in r3.b.items()}
    lost = [q for q in n1 if q not in n3]
    assert not lost, "L3 empties L1 cells: %s" % lost[:8]
    for rig, name in ((r1, "sunforged_l1"), (r3, "sunforged_l3")):
        path = os.path.join(OUT, name + ".nbt")
        rig.structure().write(path)
        print(check(rig, path, name))
