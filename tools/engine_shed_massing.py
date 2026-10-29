"""Engine Shed (Brassworks T1 vehicle lab) r11 MASSING: three greybox options that read "vehicle factory" from the RTS
camera - sawtooth north-light roofs, chimney stacks as the landmark, a big hangar door on the exit lane, the old
stepped copper tower shrunk to a clock / water tower (lovish, Oct 29 16:58). Greybox only: role blocks, no detail.

Plot (kept): x 0..16 = depth (the front / door face is the west face, x 0), z 0..24 = width, height <= 26. The exit
lane x 0..8, z 10..14, y 1..8 stays air; the spawn column (8, *, 12) is air to the sky (a smoke-vent shaft at the
back of the passage, as every round since r4).

Roles: bricks = body, polished andesite = base course (y 1) and plinths, deepslate tiles = slate roofs, glass = north
lights / clerestory, polished blackstone = the door's iron frame and the stacks' sooty caps, stone bricks = stacks,
waxed oxidized copper = the one accent (clock cap, water tank), dark oak = ridge smoke louvres.

Rationale, plans and heights: design/vehicle_labs/massing/r11_massing.md. Tests: tools/test_engine_shed_massing.py.
Usage: python3 tools/engine_shed_massing.py   (writes design/vehicle_labs/massing/r11_{a,b,c,c2}.nbt and prints plans)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lab_vehicle_brass as L          # noqa: E402  - G (the cell grid), write(), check()

SX, SZ, H_MAX = L.SX, L.SZ, L.H_MAX
LANE_X, LANE_Z, LANE_H = L.LANE_X, L.LANE_Z, L.LANE_H
SPAWN = L.SPAWN
OUT = "design/vehicle_labs/massing/r11_%s.nbt"

WALL, BASE, GROUND, YARD = "bricks", "polished_andesite", "stone_bricks", "coarse_dirt"
SLATE, SLATE_ST, SLATE_SLAB = "deepslate_tiles", "deepslate_tile_stairs", "deepslate_tile_slab"
GLASS, IRON, STACK, LOUVRE = "glass", "polished_blackstone", "stone_bricks", "dark_oak_planks"
COPPER, COPPER_ST = "waxed_oxidized_cut_copper", "waxed_oxidized_cut_copper_stairs"


def stair(g, x, y, z, facing, n=SLATE_ST):
    g.set(x, y, z, n, facing=facing, half="bottom", shape="straight", waterlogged="false")


def body(g, x0, x1, z0, z1, eave):
    """A walled block: base course at y 1, brick body to the eave."""
    g.fill(x0, x1, 1, 1, z0, z1, BASE)
    g.fill(x0, x1, 2, eave, z0, z1, WALL)


def gable_x(g, x0, x1, z0, z1, eave, skip=lambda x, z: False):
    """A 45-degree slate gable over x0..x1, ridge along x mid-z; brick gable ends."""
    mid = (z0 + z1) / 2
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if skip(x, z):
                continue
            top = eave + 1 + int(min(z - z0, z1 - z))
            g.fill(x, x, eave + 1, top - 1, z, z, WALL)
            if abs(z - mid) < 0.75:
                g.set(x, top, z, SLATE)
            else:
                stair(g, x, top, z, "south" if z < mid else "north")


def saw(g, cells, axis, starts, eave, face, rise=4):
    """Sawtooth north-light roof over `cells` ((x, z) columns walled to the eave). Teeth `rise` long step along `axis`;
    each tooth's slate slope climbs toward `face` (+1 / -1 along the axis) to a peak column whose steep face is glass
    (y eave+1 .. eave+rise-1) under a slate cap."""
    fac = {("z", 1): "south", ("z", -1): "north", ("x", 1): "east", ("x", -1): "west"}[(axis, face)]
    for (x, z) in cells:
        u = z if axis == "z" else x
        for s in starts:
            k = (u - s) if face > 0 else (s + rise - 1 - u)        # 0 at the low end .. rise-1 at the peak
            if 0 <= k < rise:
                break
        else:
            continue
        top = eave + 1 + k
        if k == rise - 1:
            g.fill(x, x, eave + 1, top - 1, z, z, GLASS)
            g.set(x, top, z, SLATE)
        else:
            g.fill(x, x, eave + 1, top - 1, z, z, WALL)
            stair(g, x, top, z, fac)


def stack(g, sx, sz, top, plinth):
    """A 2 x 2 chimney shaft from the ground to `top` on an andesite plinth (x sx-1..sx+2) to y `plinth`; a 2-course
    blackstone cap."""
    g.fill(sx - 1, sx + 2, 1, plinth, sz - 1, sz + 2, BASE)
    g.fill(sx, sx + 1, plinth + 1, top - 2, sz, sz + 1, STACK)
    g.fill(sx, sx + 1, top - 1, top, sz, sz + 1, IRON)


def door(g, dx, arch=False):
    """The hangar door: carve the lane, iron jambs and lintel in the door plane x = dx."""
    for x in LANE_X:
        for z in LANE_Z:
            for y in range(1, LANE_H + 1):
                g.clear(x, y, z)
    if arch:                                     # a segmental head: the middle three columns one row higher
        for x in range(dx, LANE_X[-1] + 1):
            for z in LANE_Z[1:-1]:
                g.clear(x, LANE_H + 1, z)
    for y in range(1, LANE_H + 2):
        g.set(dx, y, LANE_Z[0] - 1, IRON)
        g.set(dx, y, LANE_Z[-1] + 1, IRON)
    for z in LANE_Z:
        y = LANE_H + (2 if arch and z in LANE_Z[1:-1] else 1)
        g.set(dx, y, z, IRON)


def finish(g, apron):
    for y in range(1, H_MAX):                    # the spawn column / smoke-vent shaft
        g.clear(SPAWN[0], y, SPAWN[2])
    for x in range(SX):
        for z in range(SZ):
            busy = (x, 1, z) in g.b
            g.set(x, 0, z, GROUND if busy or (x, z) in apron else YARD)
    return g


def lane_apron(x0):
    return {(x, z) for x in range(x0, LANE_X[-1] + 1) for z in range(LANE_Z[0] - 1, LANE_Z[-1] + 2)}


# ---- (a) North-light works: a gabled erecting bay with the hangar door, sawtooth shops round it, two stacks ----------
def build_a():
    g = L.G()
    bay = (0, 9, 8, 16)                                          # the erecting bay: x0, x1, z0, z1
    in_bay = lambda x, z: bay[0] <= x <= bay[1] and bay[2] <= z <= bay[3]   # noqa: E731
    shop = [(x, z) for x in range(4, SX) for z in range(SZ) if not in_bay(x, z)]
    for (x, z) in shop:
        body(g, x, x, z, z, 6)
    saw(g, shop, "z", range(1, SZ, 4), 6, +1)                   # 6 teeth, z 1..24, glass facing south (camera)
    body(g, *bay, 10)
    gable_x(g, *bay, 10)
    door(g, 0)
    stack(g, 14, 1, 25, 11)                                       # the landmark: back-north corner, 25
    stack(g, 14, 22, 22, 11)                                      # its smaller twin, back-south, 22
    body(g, 1, 3, 3, 5, 15)                                       # the clock tower, front-north: brick to 15,
    g.fill(1, 3, 16, 16, 3, 5, COPPER)                            # a copper clock stage and a pointed cap
    g.fill(2, 2, 17, 18, 4, 4, COPPER)
    for z, f in ((3, "south"), (5, "north")):
        stair(g, 2, 17, z, f, COPPER_ST)
    for x, f in ((1, "east"), (3, "west")):
        stair(g, x, 17, 4, f, COPPER_ST)
    return finish(g, lane_apron(0))


# ---- (b) The engine shed: a long gabled shed (door in the gable), smoke-vent ridge, boiler house + one stack, tank ----
def build_b():
    g = L.G()
    body(g, 2, 16, 7, 17, 9)
    gable_x(g, 2, 16, 7, 17, 9)
    g.fill(4, 14, 15, 16, 11, 13, LOUVRE)                         # the smoke-vent lantern along the ridge
    g.fill(4, 14, 17, 17, 11, 13, SLATE)
    door(g, 2, arch=True)
    body(g, 7, 15, 18, 23, 6)                                     # the boiler house, south (camera) side
    gable_x(g, 7, 15, 18, 23, 6)
    stack(g, 13, 20, 25, 10)                                      # its stack: the one landmark, 25
    body(g, 9, 16, 1, 6, 5)                                       # stores, north
    gable_x(g, 9, 16, 1, 6, 5)
    for x in (3, 7):                                              # the water tank on four legs, front-north
        for z in (1, 5):
            g.fill(x, x, 1, 9, z, z, BASE)
    g.fill(3, 7, 10, 13, 1, 5, COPPER)
    g.fill(4, 6, 14, 14, 2, 4, SLATE_SLAB)
    return finish(g, lane_apron(0))


# ---- (c) The locomotive works: a tall clerestory nave with the gate, sawtooth aisles, twin stacks, a clock turret -----
def build_c():
    g = L.G()
    aisles = [(x, z) for x in range(3, SX) for z in list(range(2, 9)) + list(range(16, 23))]
    for (x, z) in aisles:
        body(g, x, x, z, z, 4)
    saw(g, aisles, "x", (4, 8, 12), 4, -1)                       # 3 teeth each aisle, glass facing west (camera)
    for (x, z) in aisles:                                         # the back bay x 16: a low slate lean-off
        if x == 16:
            g.clear(x, 5, z)
            g.set(x, 5, z, SLATE_SLAB, type="bottom", waterlogged="false")
    body(g, 0, 16, 9, 15, 11)                                     # the nave
    gable_x(g, 0, 16, 9, 15, 11)
    for x in range(2, 16, 2):                                     # its clerestory over the aisle roofs
        for z in (9, 15):
            g.fill(x, x, 9, 10, z, z, GLASS)
    door(g, 0)
    g.fill(0, 2, 15, 18, 11, 13, WALL)                            # the clock turret on the gate gable
    g.fill(0, 2, 19, 19, 11, 13, COPPER)
    g.set(1, 20, 12, COPPER)
    stack(g, 14, 6, 24, 9)                                        # twin stacks flanking the nave's back end
    stack(g, 14, 17, 24, 9)
    return finish(g, lane_apron(0))


# ---- (c2) the critic's pick (c), revised (Oct 29): no turret over the gate, stacks 22 / 19 (the short one rising from
# a boiler house on the south flank), a 7 x 8 door under a segmental iron arch with a fan-light, saw peaks to 10 -----
def build_c2():
    g = L.G()
    boiler = (6, 11, 20, 24)                                      # x0, x1, z0, z1: mid-depth, out on the south face
    plinth = (13, 16, 1, 4)                                       # the landmark stack's plinth, back-north

    def taken(x, z):
        return (boiler[0] <= x <= boiler[1] and boiler[2] <= z <= boiler[3]) or \
               (plinth[0] <= x <= plinth[1] and plinth[2] <= z <= plinth[3])
    aisles = [(x, z) for x in range(3, SX) for z in list(range(1, 8)) + list(range(17, 24)) if not taken(x, z)]
    for (x, z) in aisles:
        body(g, x, x, z, z, 5)
    saw(g, aisles, "x", (3, 8, 13), 5, -1, rise=5)               # 3 teeth each aisle, peaks 10, glass facing west
    body(g, 0, 16, 8, 16, 10)                                     # the nave: eave 10, ridge 15
    gable_x(g, 0, 16, 8, 16, 10)
    for x in range(2, 16, 2):                                     # clerestory over the aisle roofs
        for z in (8, 16):
            g.fill(x, x, 9, 10, z, z, GLASS)
    stack(g, 14, 2, 22, 9)                                        # the landmark, 22 (24 cropped at the RTS frame top)
    body(g, *boiler, 6)                                           # the boiler house and its stack, 19
    shaft = lambda x, z: 8 <= x <= 9 and 22 <= z <= 23            # noqa: E731
    gable_x(g, *boiler, 6, skip=shaft)
    g.fill(8, 9, 1, 17, 22, 23, STACK)
    g.fill(8, 9, 18, 19, 22, 23, IRON)
    body(g, 0, 2, 21, 23, 13)                                     # the clock, front-south corner: copper stage, cap 16
    g.fill(0, 2, 14, 15, 21, 23, COPPER)
    g.set(1, 16, 22, COPPER)
    # the gate: 7 wide (z 9..15) x 8 tall, the crown (z 10..14) one row higher - a segmental arch - 9 deep
    for x in range(0, 9):
        for z in range(9, 16):
            for y in range(1, 9 + (1 if 10 <= z <= 14 else 0)):
                g.clear(x, y, z)
    for y in range(1, 10):
        g.set(0, y, 8, IRON)
        g.set(0, y, 16, IRON)
    for z in range(9, 16):
        g.set(0, 10 if 10 <= z <= 14 else 9, z, IRON)
    for y, zs in ((11, range(10, 15)), (12, range(11, 14))):     # the fan-light in the gable over the arch
        for z in zs:
            g.set(0, y, z, GLASS)
    return finish(g, lane_apron(0) | {(x, z) for x in range(0, 3) for z in range(8, 17)})


BUILDERS = {"a": build_a, "b": build_b, "c": build_c, "c2": build_c2}
# what the tests hold each option to: the door plane, the stacks (plinth box + shaft + top), the tower (box), the
# dominant mass's eave, and where the sawtooth is (section plane, teeth's glazed columns, facing, run)
OPTIONS = {
    "a": dict(door_x=0, eave=10,
              stacks=[dict(box=(13, 16, 0, 3), shaft=(14, 15, 1, 2), top=25),
                      dict(box=(13, 16, 21, 24), shaft=(14, 15, 22, 23), top=22)],
              tower=dict(box=(1, 3, 3, 5)),
              sawtooth=[dict(axis="z", at=11, face=+1, teeth=list(range(4, SZ, 4)), run=(0, 24))]),
    "b": dict(door_x=2, eave=9,
              stacks=[dict(box=(12, 15, 19, 22), shaft=(13, 14, 20, 21), top=25)],
              tower=dict(box=(3, 7, 1, 5)),
              sawtooth=[]),
    "c": dict(door_x=0, eave=11,
              stacks=[dict(box=(13, 16, 5, 8), shaft=(14, 15, 6, 7), top=24),
                      dict(box=(13, 16, 16, 19), shaft=(14, 15, 17, 18), top=24)],
              tower=dict(box=(0, 2, 11, 13)),
              sawtooth=[dict(axis="x", at=z, face=-1, teeth=[4, 8, 12], run=(3, 16)) for z in (4, 20)]),
    "c2": dict(door_x=0, eave=10,
               stacks=[dict(box=(13, 16, 1, 4), shaft=(14, 15, 2, 3), top=22),
                       dict(box=(8, 9, 22, 23), shaft=(8, 9, 22, 23), top=19)],
               tower=dict(box=(0, 2, 21, 23)),
               sawtooth=[dict(axis="x", at=z, face=-1, teeth=[3, 8, 13], run=(3, 16), peaks=(9, 10))
                         for z in (5, 18)]),
}


def build(o):
    return BUILDERS[o]()


def plan(g):
    """Top-down ASCII: rows = x (front x 0 at the top), columns = z 0..24; each cell its top height (base 36)."""
    tops = {}
    for (x, y, z) in g.b:
        tops[(x, z)] = max(tops.get((x, z), 0), y)
    digits = "0123456789abcdefghijklmnopq"
    rows = ["     z " + "".join(str(z % 10) for z in range(SZ))]
    for x in range(SX):
        rows.append("x %2d   " % x + "".join("." if tops.get((x, z), 0) == 0 else digits[tops[(x, z)]]
                                           for z in range(SZ)))
    return "\n".join(rows)


if __name__ == "__main__":
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    for o in BUILDERS:
        g = build(o)
        out = os.path.join(root, OUT % o)
        rig = L.write(g, out)
        print(L.check(rig, out))
        L.check_banned(rig, "r11_" + o)
        print(plan(g))
