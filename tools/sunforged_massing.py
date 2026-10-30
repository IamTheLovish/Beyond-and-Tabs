#!/usr/bin/env python3
"""Sunforged vehicle lab (the Sunwain Court) - massing options for the blind greybox comparison (Oct 30 2026).

The method that took the Engine Shed from 3-4/10 to RTS 7 (PR #411): ONE primary reference whose scale fits the plot,
imitated closely; the silhouette settled by a blind critic on real eyes stills with the old build as the CONTROL;
detail only after. Reference: ref21 (design/refs/vehicle_labs/ref21_gothic_tower_bartizans.png) - a square gothic keep,
four corner towers proud of its faces with corbelled crenellated crowns (bartizans), a central front bay with the door
and a ROSE over it, ONE steep copper-orange spire rising behind the bay, a low wing. The designer pass (API run
itachi2-api/runs/20261030-162547-sunforged-design + -163025-sunforged-design-cont) studied it and wrote options A, B, C.

Greybox only: role blocks, no ornament. Roles: polished andesite walls (the White Angel default stone, lovish Oct 20:
"middle is fine"), stone bricks for the dressings (tower corbels and crowns, the bay's frame), smooth stone slab terraces,
Macaw's granite steep-roof spire (a muted warm cone, as ref21's and the capitol v4's orange needles - the final
material is a detail-round choice), deepslate tiles on the wing, polished blackstone bricks the lane threshold, amber
glass the rose (the sun disc), gold the finial.

Plot 17 x 17 (x 0..16 west -> east, z 0..16 north -> south; the RTS camera looks north-east from the south-west and
sees the roofs, the west face x 0 - the front - and the south face z 16). Hard rules (asserted): height <= 26 (y 0..25,
the Engine Shed T1's cap); the exit lane x 0..9, z 6..10, y 1..8 empty; the spawn column (8, y, 8) air to the sky; no
floating blocks; no sealed pockets (tools/audit_structure_pockets.py).

  A  ref21 imitated: keep x 3..15 z 3..13 (terrace 14, merlons 15), four 4 x 4 corner towers to 15 with a corbel ring
     out to 5 x 5 at 16 and crowns 17-18, the front bay x 0..2 z 5..11 to 14 with the 5 x 8 gate and the rose over it,
     the light well x 7..9 z 7..9 open from the lane to the sky, the SPIRE behind the bay on the east half (x 11..15,
     z 6..10): a 5 x 5 base stage to 16, a steep foot ring 17-18, the 3 x 3 steep body 19-22, a needle 23-24, a gold
     finial 25 - 7 over the tower crowns (ref21's spire rises ~1/4 of the height over the towers); a deepslate lean-to
     wing on the south face. (Builder's change to the designer's A: the designer's towers reached 21 and left the spire
     only 3 ahead; the towers come down to 18 so the one landmark leads by 7 - the designer's own band was 3-6 "cone
     body", the critic's fault on the control was "needles compete".)
  B  the campanile: the same keep and three corner towers; the SPIRE is the south-east corner tower itself (x 11..15,
     z 11..15) on the silhouette edge both camera faces show, a gallery stage then the steep spire to 24, finial 25.
  C  the cloister: a low ring of gabled ranges round an open court (the spawn court), the gate through the west range,
     two west bartizans, the SPIRE tower on the east range (x 11..15, z 6..10) rising from the ground - the biggest lead.
  control: design/vehicle_labs/sunforged_l3.nbt (round 2, tools/lab_vehicle_sunforged.py, 4/10 from round 1's critic).

    python3 tools/sunforged_massing.py          # writes design/vehicle_labs/massing/sunforged_{a,b,c}.nbt and checks them
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs  # noqa: E402

SX = SZ = 17
H_MAX = 26
LANE_X, LANE_Z, LANE_H = range(0, 10), range(6, 11), 8
SPAWN = (8, 1, 8)
OUT = "design/vehicle_labs/massing/sunforged_%s.nbt"

WALL = "polished_andesite"
DRESS = "stone_bricks"
DRESS_ST = "stone_brick_stairs"
DRESS_SL = "stone_brick_slab"
TERRACE = "smooth_stone_slab"
PAVE = "polished_andesite"
SPIRE = "mcwroofs:granite"
SPIRE_CORE = "granite"
WING = "deepslate_tiles"
WING_ST = "deepslate_tile_stairs"
SILL = "polished_blackstone_bricks"
ROSE = "orange_stained_glass"
GOLD = "gold_block"
DRY = dict(waterlogged="false")


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

    def stair(self, x, y, z, facing, half, name=DRESS_ST):
        self.set(x, y, z, name, facing=facing, half=half, shape="straight", **DRY)

    def slab(self, x, y, z, name=TERRACE, kind="bottom"):
        self.set(x, y, z, name, type=kind, **DRY)


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


def steep_ring(g, x0, x1, z0, z1, y, pairs=1):
    """A ring of Macaw's steep-roof pieces (lower + upper half = 2 rise per run), `pairs` stacked; the bed filled."""
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
    """The landmark: a 5 x 5 steep foot ring (y_foot, +1), the 3 x 3 steep body (two pairs, +2..+5), a 1 x 1 needle of
    deepslate-tile wall (+6, +7) and the gold finial (+8)."""
    steep_ring(g, cx - 2, cx + 2, cz - 2, cz + 2, y_foot, 1)
    steep_ring(g, cx - 1, cx + 1, cz - 1, cz + 1, y_foot + 2, 2)
    g.set(cx, y_foot + 6, cz, "deepslate_tile_wall", up="true", north="none", south="none", east="none", west="none",
          **DRY)
    g.set(cx, y_foot + 7, cz, "deepslate_tile_wall", up="true", north="none", south="none", east="none", west="none",
          **DRY)
    g.set(cx, y_foot + 8, cz, GOLD)
    return y_foot + 8


def merlons(g, cells, y):
    for (x, z) in cells:
        if (x + z) % 2 == 0:
            g.set(x, y, z, DRESS)
        else:
            g.slab(x, y, z, DRESS_SL)


def edge(x0, x1, z0, z1):
    return [(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1) if x in (x0, x1) or z in (z0, z1)]


def corner_tower(g, x0, z0, top, out_x, out_z, w=4, crown=True):
    """A w x w tower shaft x0..x0+w-1, z0..z0+w-1 to `top`; the corbel ring at top + 1 one out on its two outer faces
    (out_x = -1 west / +1 east, out_z = -1 north / +1 south): upside-down stairs, the crown course top + 2 and merlons
    top + 3 on the (w+1) x (w+1); without `crown`, the merlons sit on the corbel course (top + 2)."""
    x1, z1 = x0 + w - 1, z0 + w - 1
    g.box(x0, x1, 1, top, z0, z1, WALL)
    cx0, cx1 = (x0 - 1, x1) if out_x < 0 else (x0, x1 + 1)
    cz0, cz1 = (z0 - 1, z1) if out_z < 0 else (z0, z1 + 1)
    for x in range(cx0, cx1 + 1):
        for z in range(cz0, cz1 + 1):
            inside = x0 <= x <= x1 and z0 <= z <= z1
            if inside:
                g.set(x, top + 1, z, DRESS)
            else:
                f = "east" if x < x0 else "west" if x > x1 else ("south" if z < z0 else "north")
                g.stair(x, top + 1, z, f, "top")
            if crown:
                g.set(x, top + 2, z, DRESS)
    merlons(g, edge(cx0, cx1, cz0, cz1), top + 2 + crown)
    return top + 2 + crown


def ground(g):
    for x in range(SX):
        for z in range(SZ):
            g.set(x, 0, z, PAVE)
    for x in range(0, 10):                       # the lane's threshold strip, out to the plot edge
        g.set(x, 0, 8, SILL)


def rose(g, x, zc, yc, r=2):
    """The sun disc: a round amber rose in the bay face at x, a stone ring round it (r 2: a 3 x 3 glass inside a 5 x 5
    ring; r 1: a plus of glass inside a 3 x 3 ring)."""
    for dz in range(-r, r + 1):
        for dy in range(-r, r + 1):
            d = dz * dz + dy * dy
            if d <= (2 if r == 2 else 1):
                g.set(x, yc + dy, zc + dz, ROSE)
            elif d <= (5 if r == 2 else 2):
                g.set(x, yc + dy, zc + dz, DRESS)


def lane_and_well(g, well=True):
    for x in LANE_X:
        for z in LANE_Z:
            for y in range(1, LANE_H + 1):
                g.clear(x, y, z)
    if well:
        for x in range(7, 10):
            for z in range(7, 10):
                for y in range(1, H_MAX):
                    g.clear(x, y, z)


def gate_frame(g, x=0):
    """The gate's head: a pointed head course over the 5 x 8 opening (y 9 full across z 5..11, the springers stepped)."""
    for z in range(5, 12):
        g.set(x, 9, z, DRESS)
    for z, f in ((6, "north"), (10, "south")):
        g.stair(x, 9, z, f, "top")


# ------------------------------------------------------------------------------------------------- the options
def keep(g, top=13):
    g.box(3, 15, 1, top, 3, 13, WALL)
    for x in range(3, 16):
        for z in range(3, 14):
            g.slab(x, top + 1, z, TERRACE)
    merlons(g, edge(3, 15, 3, 13), top + 1)


def build_a():
    g = Grid()
    ground(g)
    keep(g, 13)
    tops = [corner_tower(g, x0, z0, 15, ox, oz) for x0, z0, ox, oz in
            ((1, 1, -1, -1), (1, 12, -1, 1), (12, 12, 1, 1), (12, 1, 1, -1))]
    g.box(0, 2, 1, 14, 5, 11, WALL)                                   # the front bay
    merlons(g, edge(0, 2, 5, 11), 15)
    g.box(11, 15, 14, 16, 6, 10, WALL)                                # the spire's base stage over the keep
    for x in range(11, 16):
        for z in range(6, 11):
            if x in (11, 15) or z in (6, 10):
                g.set(x, 16, z, DRESS)
    top = spire(g, 13, 8, 17)
    g.box(5, 11, 1, 6, 14, 15, WALL)                                   # the wing (lean-to on the south face)
    for x in range(5, 12):
        g.stair(x, 7, 15, "north", "bottom", WING_ST)
        g.set(x, 7, 14, WING)
        g.stair(x, 8, 14, "north", "bottom", WING_ST)
    lane_and_well(g)
    gate_frame(g)
    rose(g, 0, 8, 12)
    return g, dict(landmark=(13, 8), top=top, next_top=max(tops), towers=4)


def build_b():
    g = Grid()
    ground(g)
    keep(g, 13)
    tops = [corner_tower(g, x0, z0, 15, ox, oz) for x0, z0, ox, oz in
            ((1, 1, -1, -1), (1, 12, -1, 1), (12, 1, 1, -1))]
    g.box(11, 15, 1, 17, 11, 15, WALL)                                # the campanile shaft (5 x 5)
    for x in range(10, 17):                                           # corbel ring at 18 out to 7 x 7, gallery 19
        for z in range(10, 17):
            inside = 11 <= x <= 15 and 11 <= z <= 15
            if inside:
                g.set(x, 18, z, DRESS)
            elif x == 10 or z == 10:
                continue                                              # flush on the keep sides (north, west)
            else:
                f = "west" if x > 15 else "north"
                g.stair(x, 18, z, f, "top")
    for (x, z) in edge(11, 16, 11, 16):
        if x in (11, 16) or z in (11, 16):
            g.slab(x, 19, z, DRESS_SL)
    for x in range(12, 15):
        for z in range(12, 15):
            g.set(x, 19, z, DRESS)
    # the spire over the gallery: 5 x 5 foot ring 19..20 (inside the gallery rail), 3 x 3 body 21..24, finial 25
    steep_ring(g, 11, 15, 11, 15, 19, 1)
    steep_ring(g, 12, 14, 12, 14, 21, 2)
    g.set(13, 25, 13, GOLD)
    top = 25
    g.box(0, 2, 1, 14, 5, 11, WALL)                                   # the front bay
    merlons(g, edge(0, 2, 5, 11), 15)
    g.box(5, 10, 1, 6, 14, 15, WALL)                                  # the wing
    for x in range(5, 11):
        g.stair(x, 7, 15, "north", "bottom", WING_ST)
        g.set(x, 7, 14, WING)
        g.stair(x, 8, 14, "north", "bottom", WING_ST)
    lane_and_well(g)
    gate_frame(g)
    rose(g, 0, 8, 12)
    return g, dict(landmark=(13, 13), top=top, next_top=max(tops), towers=3)


def gable_range(g, x0, x1, z0, z1, eave, axis):
    """A walled range x0..x1, z0..z1 to `eave` under a deepslate-tile gable roof whose ridge runs along `axis`."""
    g.box(x0, x1, 1, eave, z0, z1, WALL)
    if axis == "z":
        lo, hi = x0, x1
        k = 0
        while lo <= hi:
            for z in range(z0, z1 + 1):
                if lo == hi:
                    g.slab(lo, eave + 1 + k, z, "deepslate_tile_slab")
                else:
                    g.stair(lo, eave + 1 + k, z, "east", "bottom", WING_ST)
                    g.stair(hi, eave + 1 + k, z, "west", "bottom", WING_ST)
                    for x in range(lo + 1, hi):
                        g.set(x, eave + 1 + k, z, WING)
            lo, hi, k = lo + 1, hi - 1, k + 1
    else:
        lo, hi = z0, z1
        k = 0
        while lo <= hi:
            for x in range(x0, x1 + 1):
                if lo == hi:
                    g.slab(x, eave + 1 + k, lo, "deepslate_tile_slab")
                else:
                    g.stair(x, eave + 1 + k, lo, "south", "bottom", WING_ST)
                    g.stair(x, eave + 1 + k, hi, "north", "bottom", WING_ST)
                    for z in range(lo + 1, hi):
                        g.set(x, eave + 1 + k, z, WING)
            lo, hi, k = lo + 1, hi - 1, k + 1


def build_c():
    g = Grid()
    ground(g)
    gable_range(g, 1, 5, 1, 15, 9, "z")                               # the west range (the gatehouse), ridge 12
    gable_range(g, 6, 10, 1, 4, 8, "x")                               # the north range
    gable_range(g, 6, 10, 12, 15, 8, "x")                             # the south range
    gable_range(g, 11, 15, 1, 5, 8, "x")                              # the east range, north and south of the tower
    gable_range(g, 11, 15, 11, 15, 8, "x")
    for (x0, z0) in ((0, 0), (0, 14)):                                # two west bartizans, 3 x 3, to 14
        g.box(x0, x0 + 2, 1, 12, z0, z0 + 2, WALL)
        g.box(x0, x0 + 2, 13, 13, z0, z0 + 2, DRESS)
        merlons(g, edge(x0, x0 + 2, z0, z0 + 2), 14)
    g.box(11, 15, 1, 15, 6, 10, WALL)                                 # the landmark tower from the ground
    for x in range(11, 16):
        for z in range(6, 11):
            g.set(x, 16, z, DRESS)
    top = spire(g, 13, 8, 17)
    lane_and_well(g, well=False)
    for x in range(6, 11):                                            # the open court (the spawn court)
        for z in range(5, 12):
            for y in range(1, H_MAX):
                g.clear(x, y, z)
    for z in range(5, 12):                                            # the gatehouse front over the gate, rose in it
        for y in range(9, 12):
            g.set(0, y, z, WALL)
    gate_frame(g)
    rose(g, 0, 8, 11)
    for z in range(5, 12):
        g.slab(0, 12 if z in (5, 11) else 13, z, DRESS_SL)
    return g, dict(landmark=(13, 8), top=top, next_top=14, towers=2)


def build_a2():
    """A revised on the two blind critics' shared faults (runs 20261030-165241 / -165245-sunforged-massing-critic-a/b,
    both ranked A first, 4 > 3 > 1 > 2 and 4 > 1 > 3 > 2): one change per element.
    1. the spire "flattens to a salmon square at the RTS camera, barely tops the towers": everything else comes down
       (keep terrace 12, front towers 16, back turrets 14) and the spire stands on a 5 x 5 TOWER SHAFT rising 4 clear
       of the terrace with its own crenellated parapet (option C's tower, the best landmark of the four, both critics)
       - the shaft's lit south and west faces show from the RTS camera; lead 25 - 16 = 9;
    2. "four equal towers compete": a hierarchy - two 4 x 4 front towers flank the gate bay (16), the back corners
       become small 3 x 3 corbelled turrets (14);
    3. "the gate is not the darkest opening; the south wing's dark tiles and the light well beat it": the wing's roof
       becomes a pale crenellated terrace (no dark tiles), the well shrinks to the spawn column itself (1 x 1) inside a
       gold halo ring, the gate's mouth is lined dark (blackstone lane floor and back wall) and the bay carries a
       bigger rose (5 x 5 round) over a stepped pointed head."""
    g = Grid()
    ground(g)
    keep(g, 11)                                                         # terrace 12
    tops = [corner_tower(g, 1, z0, 13, -1, oz) for z0, oz in ((1, -1), (12, 1))]          # front towers: top 16
    tops += [corner_tower(g, 13, z0, 12, 1, oz, w=3, crown=False) for z0, oz in ((1, -1), (13, 1))]   # turrets: 14
    g.box(0, 2, 1, 14, 4, 12, WALL)                                     # the front bay, 1 proud of the towers
    merlons(g, edge(0, 2, 4, 12), 15)
    g.box(11, 15, 12, 16, 6, 10, WALL)                                  # the spire's tower shaft, 4 clear of the terrace
    merlons(g, edge(11, 15, 6, 10), 17)
    top = spire(g, 13, 8, 17)
    g.box(5, 11, 1, 5, 14, 15, WALL)                                    # the wing: a pale terrace, no dark tiles
    for x in range(5, 12):
        for z in (14, 15):
            g.slab(x, 6, z, TERRACE)
    merlons(g, [(x, 15) for x in range(5, 12)], 6)
    lane_and_well(g, well=False)
    for y in range(1, H_MAX):                                           # the spawn column: the well is 1 x 1
        g.clear(8, y, 8)
    for (x, z) in edge(7, 9, 7, 9):                                     # the gold halo round it on the terrace
        g.set(x, 12, z, GOLD)
    for x in range(1, 10):                                              # the gate's mouth lined dark
        for z in LANE_Z:
            g.set(x, 0, z, SILL)
    for z in LANE_Z:
        for y in range(1, LANE_H + 1):
            g.set(10, y, z, SILL)
    portal(g)
    rose(g, 0, 8, 14, r=1)
    return g, dict(landmark=(13, 8), top=top, next_top=max(tops), towers=4)


def portal(g):
    """A two-order splayed portal in the bay: the outer order at x 0 is 7 wide (z 5..11) and 9 tall to a pointed head
    (springers at 10, apex 11, keystone 12); the inner order at x 1 is the lane's 5 x 8 under its own head (9). The 3/4
    camera looks in at 45 degrees and sees the splay's inner faces - the dark mouth (A: the 34 still saw 71 pixels in)."""
    for z in range(5, 12):
        for y in range(1, 10):
            g.clear(0, y, z)
    for z in (7, 8, 9):
        g.clear(0, 10, z)
    for z, f in ((6, "north"), (10, "south")):
        g.stair(0, 10, z, f, "top")
    for z, f in ((7, "north"), (9, "south")):
        g.stair(0, 11, z, f, "top")
    g.clear(0, 11, 8)
    g.set(0, 12, 8, "chiseled_stone_bricks")
    for z in range(5, 12):                                              # the inner order's head over the lane
        g.set(1, 9, z, DRESS)
    for z, f in ((6, "north"), (10, "south")):
        g.stair(1, 9, z, f, "top")


BUILDERS = {"a": build_a, "b": build_b, "c": build_c, "a2": build_a2}


# ------------------------------------------------------------------------------------------------- write + check
def write(g, out):
    h = max(y for (_, y, _) in g.b) + 1
    assert h <= H_MAX, "%s is %d tall" % (out, h)
    rig = gs._SunRig(SX, h, SZ)
    for (x, y, z), (n, props) in g.b.items():
        rig.set(x, y, z, n, **props)
    rig.bury_grass()
    rig.settle()
    os.makedirs(os.path.dirname(out), exist_ok=True)
    rig.structure().write(out)
    return rig


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
    return "%s %s, %d blocks, no pockets%s" % (name, "x".join(map(str, rig.size)), len(n), note)


def main(argv):
    for o in (argv or sorted(BUILDERS)):
        g, meta = BUILDERS[o]()
        out = OUT % o
        rig = write(g, out)
        print(check(rig, out), "| landmark top %d, next %d (lead %d)" % (meta["top"], meta["next_top"],
                                                                        meta["top"] - meta["next_top"]))


if __name__ == "__main__":
    main(sys.argv[1:])
