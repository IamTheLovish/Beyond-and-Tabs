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


# ---- r11b (lovish, Oct 29 19:33: "I cant decide by a or c can we make them better than even that?") - three revised
# greyboxes on one builder, measured against the factory refs (tools/schem_study.py on the study builds, study only):
# the Victorian Factory's stacks top out at 57 over a median roof of 29 (~2 x) and 1.2 x its highest hall; weaving-shed
# north lights pitch 4-5 per tooth at ~45 deg; a works gate is about as tall as wide (Midland loco-shed door ~1 : 1).
# Here: stacks 22 / 20 over hall ridges 12-13 (1.7-1.8 x) and a median roof of ~9 (2.3 x); teeth 4 long, rise 4;
# gates 7 wide x 7-8 tall under a segmental iron head.
#
# (c3) C' - the critic's pick (c) with the critic's three required changes (fresh critic, Oct 29 ~23:15: c = factory
# read 8, massing 7) and the two borrows: (1) the hall projects 7 in front of the wings - a long shed with the bay door
# in its end, ridge 2 over the wings; (2) the gate tower beside the gate, 4 over the ridge, the back-left (north) wing
# one row lower and smaller; (3) the two stacks 2 apart on one boiler house, both north of the hall; (a)'s wide bay-door
# gable with a tall ground-level gate; (b)'s open-frame water tank, here in the back yard behind the hall.
# (a2) A' - (a) with its faults fixed: the stacks in opposite corners fought for the landmark (now two on one boiler
# house, back-north), the teal-topped tower was a dead cube (now a slim clock tower beside the gate, 3 over the ridge);
# kept: the broad north-light field (teeth along z, the glass facing south to the camera) and the wide bay-door gable;
# the erecting bay stretched into a long shed (14 x 9).
# (ac) AC - (c)'s hierarchy (a tall central hall > sawtooth wings > gate tower) with (a)'s broad north-light spread
# (teeth along z on both flanks, glass to the south / camera) and its bay-door end; the hall the full depth.
def saw_cells(box, skip=lambda x, z: False):
    x0, x1, z0, z1 = box
    return [(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1) if not skip(x, z)]


def revised(spec):
    g = L.G()
    shafts = [s for s, _ in spec["shafts"]]
    in_shaft = lambda x, z: any(a <= x <= b and d <= z <= e for a, b, d, e in shafts)   # noqa: E731
    bx0, bx1, bz0, bz1 = spec["boiler"]
    in_boiler = lambda x, z: bx0 <= x <= bx1 and bz0 <= z <= bz1                       # noqa: E731
    hx0, hx1, hz0, hz1 = spec["hall"]
    in_hall = lambda x, z: hx0 <= x <= hx1 and hz0 <= z <= hz1                         # noqa: E731
    for w in spec["wings"]:
        cells = saw_cells(w["box"], lambda x, z: in_boiler(x, z) or in_hall(x, z))
        for (x, z) in cells:
            body(g, x, x, z, z, w["eave"])
        saw(g, cells, w["axis"], w["starts"], w["eave"], w["face"])
    body(g, hx0, hx1, hz0, hz1, spec["hall_eave"])
    gable_x(g, hx0, hx1, hz0, hz1, spec["hall_eave"])
    for x in spec.get("hall_windows", ()):                        # tall windows in the hall's exposed flanks
        for z in (hz0, hz1):
            g.fill(x, x, 2, spec["hall_eave"] - 1, z, z, GLASS)
    body(g, bx0, bx1, bz0, bz1, 6)                                # the boiler house and its two stacks
    gable_x(g, bx0, bx1, bz0, bz1, 6, skip=in_shaft)
    for (a, b, d, e), top in spec["shafts"]:
        g.fill(a, b, 1, top - 2, d, e, STACK)
        g.fill(a, b, top - 1, top, d, e, IRON)
    tx0, tx1, tz0, tz1 = spec["tower"]                            # the clock tower beside the gate
    top = spec["tower_top"]
    body(g, tx0, tx1, tz0, tz1, top - 3)
    g.fill(tx0, tx1, top - 2, top - 1, tz0, tz1, COPPER)
    g.set(tx0 + (tx1 - tx0) // 2, top, tz0 + (tz1 - tz0) // 2, COPPER)
    dz = spec["door_z"]                                           # the gate: 7 wide, 7 tall at the edges, 8 at the crown
    for x in range(0, LANE_X[-1] + 1):
        for z in dz:
            for y in range(1, 9 if z in dz[1:-1] else 8):
                g.clear(x, y, z)
    for y in range(1, 10):
        g.set(0, y, dz[0] - 1, IRON)
        g.set(0, y, dz[-1] + 1, IRON)
    for z in dz:
        g.set(0, 9 if z in dz[1:-1] else 8, z, IRON)
    mid = dz[3]
    for y, zs in ((10, range(mid - 1, mid + 2)), (11, (mid,))):  # the fan-light in the gable over the gate
        for z in zs:
            g.set(0, y, z, GLASS)
    if spec.get("tank"):                                          # the water tank on four legs, in the back yard
        kx0, kx1, kz0, kz1 = spec["tank"]
        ty = spec["tank_y"]
        for x in (kx0, kx1):
            for z in (kz0, kz1):
                g.fill(x, x, 1, ty - 1, z, z, BASE)
        g.fill(kx0, kx1, ty, ty + 2, kz0, kz1, COPPER)
    return finish(g, lane_apron(0) | {(x, z) for x in range(0, 3) for z in range(8, 17)})


SPECS = {
    "c3": dict(hall=(0, 13, 8, 16), hall_eave=7, hall_windows=(4, 6),
               wings=[dict(name="south", box=(7, 16, 17, 23), eave=6, axis="x", starts=(7, 11, 15), face=-1,
                           sections=[(20, [7, 11, 15], (7, 16))]),
                      dict(name="north", box=(7, 12, 1, 7), eave=5, axis="x", starts=(7, 11), face=-1,
                           sections=[(4, [7, 11], (7, 12))])],
               boiler=(13, 16, 0, 7), shafts=[((14, 15, 1, 2), 22), ((14, 15, 5, 6), 20)],
               tower=(0, 2, 17, 19), tower_top=16, door_z=range(9, 16), tank=(14, 16, 10, 12), tank_y=9),
    "a2": dict(hall=(0, 13, 8, 16), hall_eave=7, hall_windows=(3, 5),
               wings=[dict(name="field", box=(7, 16, 0, 24), eave=6, axis="z", starts=range(1, SZ, 4), face=+1,
                           sections=[(8, [4, 20, 24], (1, 24)), (15, [8, 12, 16, 20, 24], (5, 24))])],
               boiler=(12, 16, 0, 7), shafts=[((13, 14, 1, 2), 22), ((13, 14, 5, 6), 20)],
               tower=(0, 2, 17, 19), tower_top=16, door_z=range(9, 16)),
    "ac": dict(hall=(0, 16, 8, 16), hall_eave=9, hall_windows=(2, 4),
               wings=[dict(name="south", box=(5, 16, 17, 24), eave=4, axis="z", starts=(17, 21), face=+1,
                           sections=[(10, [20, 24], (17, 24))]),
                      dict(name="north", box=(5, 11, 0, 7), eave=4, axis="z", starts=(-1, 3, 7), face=+1,
                           sections=[(8, [2, 6], (0, 7))])],
               boiler=(12, 16, 0, 7), shafts=[((13, 14, 1, 2), 22), ((13, 14, 5, 6), 20)],
               tower=(0, 2, 17, 19), tower_top=17, door_z=range(9, 16)),
}


def build_c3():
    return revised(SPECS["c3"])


def build_a2():
    return revised(SPECS["a2"])


def build_ac():
    return revised(SPECS["ac"])


# ---- (af) lovish's pick (Oct 29 19:5x): OLD a, small fixes only - from the blind critic's diagnosis of a (read 7,
# massing 6: the tower a detached post, the south stack's pale plinth box on a camera face, a blank left flank) and
# lovish 20:00: "the towers on this building need more refinement". Everything else is a's: the erecting bay and its
# gate, the north-light field, the stacks' places and tops (the south one moved 2 north, off the south face).
# Stack and tower proportions measured on the study builds: design/vehicle_labs/towers.md.
AF = dict(bay=(0, 9, 8, 16), tower=(1, 3, 5, 7), flank_x=4,
          boilers=[(12, 16, 0, 4), (12, 16, 18, 24)],    # the south one to the plot edge: a blind critic (af vs a)
          #                                                saw the 1-wide saw strip left at z 24 as an open lean-to
          stacks=[dict(base=(13, 16, 0, 3), shaft=(14, 15, 1, 2), top=25),
                  dict(base=(13, 16, 19, 22), shaft=(14, 15, 20, 21), top=22)])
CORNICE = "stone_brick_stairs"
CLOCK = "smooth_stone"


def build_af():
    g = L.G()
    bay = AF["bay"]
    tx0, tx1, tz0, tz1 = AF["tower"]
    in_bay = lambda x, z: bay[0] <= x <= bay[1] and bay[2] <= z <= bay[3]          # noqa: E731
    in_tower = lambda x, z: tx0 <= x <= tx1 and tz0 <= z <= tz1                    # noqa: E731
    in_boiler = lambda x, z: any(a <= x <= b and c <= z <= d for a, b, c, d in AF["boilers"])   # noqa: E731
    shop = [(x, z) for x in range(4, SX) for z in range(SZ) if not in_bay(x, z) and not in_boiler(x, z)]
    for (x, z) in shop:
        body(g, x, x, z, z, 6)
    saw(g, shop, "z", range(1, SZ, 4), 6, +1)                   # a's six teeth, glass facing south (camera)
    fx = AF["flank_x"]                                           # fix 3: the shop's west flank in the teeth's beat -
    for z in [z for z in range(SZ) if z % 4 in (1, 2) and not in_bay(fx, z) and not in_boiler(fx, z)
              and not tz0 - 1 <= z <= tz1 + 1]:                  # a 2-wide window under every tooth, 2-wide piers
        g.fill(fx, fx, 2, 5, z, z, GLASS)
    body(g, *bay, 10)
    gable_x(g, *bay, 10)
    door(g, 0)
    for (bx0, bx1, bz0, bz1) in AF["boilers"]:                   # fix 2: each stack on a brick boiler house
        body(g, bx0, bx1, bz0, bz1, 6)
        gable_x(g, bx0, bx1, bz0, bz1, 6)
    for s in AF["stacks"]:                                       # 4 x 4 foot to 12, a step-in, a 2 x 2 shaft, a
        x0, x1, z0, z1 = s["base"]                               # corbelled crown one course under the cap
        sx0, sx1, sz0, sz1 = s["shaft"]
        top = s["top"]
        g.fill(x0, x1, 2, 12, z0, z1, STACK)
        for x in range(x0, x1 + 1):                              # the weathering: stairs stepping in to the shaft
            for z in range(z0, z1 + 1):
                if not (sx0 <= x <= sx1 and sz0 <= z <= sz1):
                    f = "west" if x < sx0 else "east" if x > sx1 else "north" if z < sz0 else "south"
                    g.set(x, 13, z, "stone_brick_stairs", facing={"west": "east", "east": "west",
                                                                   "north": "south", "south": "north"}[f],
                          half="bottom", shape="straight", waterlogged="false")
        g.fill(sx0, sx1, 13, top - 1, sz0, sz1, STACK)
        for x in range(x0, x1 + 1):                              # the crown: an oversailing course at top - 1
            for z in range(z0, z1 + 1):
                if not (sx0 <= x <= sx1 and sz0 <= z <= sz1):
                    f = "west" if x < sx0 else "east" if x > sx1 else "north" if z < sz0 else "south"
                    g.set(x, top - 1, z, "stone_brick_stairs", facing={"west": "east", "east": "west",
                                                                        "north": "south", "south": "north"}[f],
                          half="top", shape="straight", waterlogged="false")
        g.fill(sx0, sx1, top, top, sz0, sz1, IRON)
    # fix 1: the clock tower engaged in the corner of the bay (z 8) and the shop (x 4): a pale base, a brick shaft with
    # slit lights, a pale clock stage, an oversailing cornice, a copper pyramid roof and an iron finial (a's top, 18)
    body(g, tx0, tx1, tz0, tz1, 12)
    g.fill(tx0, tx1, 2, 2, tz0, tz1, BASE)
    for y in (6, 7, 9, 10):
        g.set(tx0, y, (tz0 + tz1) // 2, GLASS)
    g.fill(tx0, tx1, 13, 14, tz0, tz1, CLOCK)
    for x, z in ((tx0, (tz0 + tz1) // 2), ((tx0 + tx1) // 2, tz0)):
        g.set(x, 13, z, IRON)                                     # the clock faces (west, north) - dark dials
    for x in range(tx0 - 1, tx1 + 2):
        for z in range(tz0 - 1, tz1 + 2):
            if in_tower(x, z):
                g.set(x, 15, z, CLOCK)
                continue
            f = "west" if x < tx0 else "east" if x > tx1 else "north" if z < tz0 else "south"
            g.set(x, 15, z, CORNICE, facing={"west": "east", "east": "west", "north": "south",
                                             "south": "north"}[f], half="top", shape="straight", waterlogged="false")
    for x in range(tx0, tx1 + 1):
        for z in range(tz0, tz1 + 1):
            if (x, z) == ((tx0 + tx1) // 2, (tz0 + tz1) // 2):
                g.set(x, 16, z, COPPER)
                continue
            f = "west" if x < (tx0 + tx1) // 2 else "east" if x > (tx0 + tx1) // 2 else \
                "north" if z < (tz0 + tz1) // 2 else "south"
            g.set(x, 16, z, COPPER_ST, facing={"west": "east", "east": "west", "north": "south", "south": "north"}[f],
                  half="bottom", shape="straight", waterlogged="false")
    g.set((tx0 + tx1) // 2, 17, (tz0 + tz1) // 2, COPPER)
    g.set((tx0 + tx1) // 2, 18, (tz0 + tz1) // 2, "polished_blackstone_wall")
    return finish(g, lane_apron(0))


BUILDERS = {"a": build_a, "b": build_b, "c": build_c, "c2": build_c2, "c3": build_c3, "a2": build_a2,
            "ac": build_ac, "af": build_af}
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


def revised_meta(o):
    """What the tests hold a revised option to (derived from SPECS, the saw sections given there): door, hall (box,
    ridge), wings, boiler, stacks, tower, tank, sawtooth sections."""
    sp = SPECS[o]
    hall = dict(box=sp["hall"], ridge=sp["hall_eave"] + 1 + (sp["hall"][3] - sp["hall"][2]) // 2)
    saws = [dict(axis=w["axis"], at=at, face=w["face"], teeth=teeth, run=run, peaks=(w["eave"] + 4, w["eave"] + 4))
            for w in sp["wings"] for at, teeth, run in w["sections"]]
    return dict(door_x=0, door_z=sp["door_z"], eave=sp["hall_eave"], hall=hall,
                wings=[dict(name=w["name"], box=w["box"], eave=w["eave"]) for w in sp["wings"]],
                boiler=dict(box=sp["boiler"]), tank=dict(box=sp["tank"], y=sp["tank_y"]) if sp.get("tank") else None,
                stacks=[dict(box=s, shaft=s, top=t) for s, t in sp["shafts"]], tower=dict(box=sp["tower"]),
                tower_top=sp["tower_top"], sawtooth=saws)


for _o in SPECS:
    OPTIONS[_o] = revised_meta(_o)
OPTIONS["af"] = dict(door_x=0, eave=10, bay=AF["bay"], flank_x=AF["flank_x"],
                     stacks=[dict(box=s["base"], shaft=s["shaft"], base=s["base"], top=s["top"], above_roof=10)
                             for s in AF["stacks"]],
                     tower=dict(box=AF["tower"], clock=(13, 15), cornice=15),
                     sawtooth=[dict(axis="z", at=11, face=+1, teeth=list(range(4, SZ, 4)), run=(0, 24))])


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
