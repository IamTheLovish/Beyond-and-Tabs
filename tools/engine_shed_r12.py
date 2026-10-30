"""Engine Shed (Brassworks T1 vehicle lab) r12: lovish's four picks on r11 (Oct 30 ~00:30 ET, on r11 round 3: "looking
better"). Writes design/vehicle_labs/brass_t1_r12.nbt. Tests (written first, 12 red on r11): tools/test_engine_shed_r12.py.

One change per element (r11 is tools/engine_shed_r11.py; everything not named here is r11's, untouched - the
footprint, massing, sawtooth roofs and their bands, the brick / slate palette, the plinth, the eave relief, the smoke,
the chimney positions):
1. chimneys ("probably both"): a smooth tapered shaft and ONE corbelled crown, from shaped blocks. The stone foot
   stays (it meets the boiler-house roof); over it a smooth Macaw's stone wash, the plain 2 x 2 shaft, and one crown:
   our own corbelled cornice (#407 reignofnether:cornice_brick, mitred corners) under a dark cap. No bands, no
   reveals. (Round 1 also had a brick-wall sleeve and a Macaw's brick wash: the critics read them as a second ring.)
2. dusk light (warm orange, softer, inside the rooms): r11's pearlescent glow behind every pane goes; each window's room
   is lit by a warm shroomlight hidden in its ceiling over the window head (the cameras look down and never see it),
   so the panes show a lit brick floor and jambs, not a glowing face.
3. the tower roof (less stepped): a smooth steep spire of Macaw's prismarine-brick steep roof pieces (verdigris by look:
   pack sat 0.33, hue blue-green) over r11's clock stage and cornice, 2 rise per 1 run, a pyramid top and an iron
   finial; no dark lead rolls, no hip stones.
4. soot and weathering, the gate: soot at each stack's top (a blackstone cap and drips under the crown) and streaks of
   deepslate brick down the piers under the eaves; the gate - the portal over the rails at the front, NOT the coal
   stage on the left plinth - framed in dressed stone (stone-brick jambs with smooth-stone quoins, a stone head with a
   keystone) instead of r11's near-black riveted iron.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import engine_shed_massing as M  # noqa: E402
import engine_shed_r11 as R  # noqa: E402
import lab_vehicle_brass as L  # noqa: E402
from engine_shed_r11 import (BRICK, PALE, PALE_SL, PLINTH, STONE, STONE_ST, STONE_W, at, in_of, slab, stair)  # noqa: E402

OUT = "design/vehicle_labs/brass_t1_r12.nbt"
SX, SZ = R.SX, R.SZ
TOWER = R.TOWER
META = dict(R.META)
WARM = "shroomlight"
WARM_SURFACE = "birch_planks"
SPIRE = "mcwroofs:prismarine_brick"
DRIP = "mcwroofs:cyan_terracotta"
# the drip cells per ring (eave ring 16-17 over x 0-4 z 4-8, upper ring 18-19 over x 1-3 z 5-7): two-cell clusters on
# each face of the eave ring, one cell on each face of the upper ring above one of them - runs down the slope
DRIPS = ({(0, 5), (0, 6), (2, 8), (3, 8), (1, 4), (4, 6), (4, 7)}, {(1, 5), (3, 7)})
SOOT, SOOT_SL, STREAK = "polished_blackstone_bricks", "polished_blackstone_brick_slab", "deepslate_bricks"
DRY = R.DRY
# r11's windows (tools/engine_shed_r11.py walls()): (face, wall plane, columns, bottom row, top row)
WINDOWS = [("west", 4, (17, 18), 3, 5), ("west", 4, (20, 21), 3, 5), ("west", 4, (23,), 3, 5),
           ("south", 24, (5, 6, 7), 3, 5), ("south", 24, (9, 10, 11), 3, 5),
           ("south", 16, (1, 2), 3, 5), ("south", 16, (1, 2), 7, 8), ("south", 16, (5, 6), 8, 9),
           ("south", 16, (8, 9), 8, 9)]
INWARD = {"west": "east", "east": "west", "north": "south", "south": "north"}


def macaw(g, q, name, facing, shape="straight"):
    """A Macaw's roof piece (mcwroofs: *_roof 45 deg; *_steep_roof / *_upper_steep_roof the lower and upper halves of
    a 2:1 slope), its states as a stair's: facing the high side."""
    g.set(*q, name, facing=facing, half="bottom", shape=shape)


def ring_cells(x0, x1, z0, z1):
    """The cells of the rectangle's edge with (high-side facing, stair shape) for a roof rising inward: sides
    straight, corners outer (Minecraft stair shapes: outer_right turns the step's high corner clockwise of its
    facing)."""
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


# ---------------------------------------------------------------------------------------------------- 1. the stacks
def stacks(g):
    """The stone foot (r11's: 4 x 4, chamfered arrises, rusticated courses) to its top; then, smooth, no rings:
    a Macaw's stone wash (foot + 1), a battered lower shaft - the 2 x 2 in a brick-wall sleeve - a Macaw's brick wash, the
    plain 2 x 2 brick shaft, and ONE corbelled crown at top - 1 (reignofnether:cornice_brick round the shaft, mitred) under
    a blackstone cap; the fire sealed in the flue (smoke only). Soot: the cap and a drip under the crown (weathering())."""
    for s, foot, sleeve in zip(M.AF["stacks"], (11, 10), (4, 2)):
        x0, x1, z0, z1 = s["base"]
        sx0, sx1, sz0, sz1 = s["shaft"]
        top = s["top"]
        for q in [q for q in g.b if x0 <= q[0] <= x1 and z0 <= q[2] <= z1 and q[1] >= 2 and
                  (q[1] >= 7 or g.b[q][0] == M.STACK)]:
            del g.b[q]
        for x in range(x0, x1 + 1):                        # the foot, as r11
            for z in range(z0, z1 + 1):
                corner = x in (x0, x1) and z in (z0, z1)
                for y in range(2, foot + 1):
                    if corner:
                        g.set(x, y, z, STONE_W, **DRY)
                    elif y in (4, 7, 10) and y < foot and not (sx0 <= x <= sx1 and sz0 <= z <= sz1):
                        #     (r12: the foot's top course flush - the south foot's rusticated 10 was a ledge under the wash)
                        f = "east" if x == x0 else "west" if x == x1 else "south" if z == z0 else "north"
                        stair(g, (x, y, z), STONE_ST, f, "top")
                    else:
                        g.set(x, y, z, STONE)

        def shaft(y, name=BRICK):
            for x in range(sx0, sx1 + 1):
                for z in range(sz0, sz1 + 1):
                    g.set(x, y, z, name)
        wash, upper = foot + 1, foot + 2 + sleeve
        for (x, z), f, sh in ring_cells(x0, x1, z0, z1):   # the stone wash: smooth, rising to the shaft
            macaw(g, (x, wash, z), "mcwroofs:stone_bricks_roof", f, sh)
        shaft(wash)
        for y in range(wash + 1, upper + 1):               # round 2: the slim shaft straight off the stone wash -
            shaft(y)                                       # the critics read the lit brick wash (value 0.525, sat
        #     0.5) as a second ring and the brick-wall sleeve measured redder than the wall brick (sat 0.452): one
        #     smooth step-in, as the study chimney (koin: 5 -> 3 over one wash)
        for y in range(upper + 1, top - 1):
            shaft(y)
        shaft(top - 1)                                     # the crown course
        for (x, z), f, sh in ring_cells(sx0 - 1, sx1 + 1, sz0 - 1, sz1 + 1):
            out = {"east": "west", "west": "east", "south": "north", "north": "south"}[f]   # facing away from the shaft
            if sh != "straight":
                out = {(sx0 - 1, sz0 - 1): "west", (sx1 + 1, sz0 - 1): "north", (sx1 + 1, sz1 + 1): "east",
                       (sx0 - 1, sz1 + 1): "south"}[(x, z)]
            g.set(x, top - 1, z, "reignofnether:cornice_brick", facing=out,
                  shape="straight" if sh == "straight" else "outer")
        for x in range(sx0, sx1 + 1):
            for z in range(sz0, sz1 + 1):
                slab(g, (x, top, z), SOOT_SL)
        g.set(sx0, top - 1, sz0, "campfire", lit="true", facing="south", signal_fire="false", waterlogged="false")


# ---------------------------------------------------------------------------------------------------- 2. room light
def room_lights(g):
    """r11's glow behind the glass (pearlescent froglight in the window reveals, the lunette, the tower slit) goes: the
    cells over the floor become the room (air), the floor course stays floor (brick). Each window's room is lit by a
    warm shroomlight in its ceiling, 1-2 in behind the window head, roofed over so no camera sees it."""
    for q, (n, _) in list(g.b.items()):
        if n == R.GLOW:
            if q[1] <= 2:
                g.set(*q, BRICK)
            else:
                g.clear(*q)
    sources = []
    for face, plane, us, _, y1 in WINDOWS:
        mid = sorted(us)[len(us) // 2]
        for d in (1, 2):
            q = in_of(face, at(face, plane, mid, y1 + 1), d)
            if not (q[0] in L.LANE_X and q[2] in L.LANE_Z) and (q[0], q[2]) != (L.SPAWN[0], L.SPAWN[2]):
                g.set(*q, WARM)
                sources.append(q)
                break
    x0, _, _, z1 = TOWER                                   # the tower's slit light: its room over the slit
    sources.append((x0 + 1, 9, (TOWER[2] + z1) // 2))
    g.set(*sources[-1], WARM)
    for q in sources:                                      # roofed: nothing open to the sky over a source
        up = (q[0], q[1] + 1, q[2])
        if up not in g.b:
            g.set(*up, "polished_deepslate")
    warm_surfaces(g)
    return sources


def warm_surfaces(g):
    """Round 2 (the designer; critics r1: "the panes look pale grey, not orange"; measured dusk glass hue 2, sat 0.16 -
    red brick under the block light): every block the eyes cameras (rts, rts_close, 34 / dusk) see through a wall
    window's pane, within 8 blocks of it, becomes warm pale birch (pack luma 0.65, sat 0.33) - the floor and the far
    walls of a lit room. Lights, glass, thin pieces and anything a camera sees straight (not through glass) stay."""
    import building_gates as G
    import schem_study as S
    b = S.Build(dict(g.b))
    shots = [G.shoot(b, still, 2) for still in ("rts", "rts_close", "34")]
    seen = {h[0] for hits in shots for h in hits.values()}             # what a camera sees straight stays as it is
    for hits in shots:
        for h in hits.values():
            if h[1] != R.PANE or not h[3] or h[0][1] > 9:
                continue
            q, n = h[3]
            if q in seen or n == WARM or max(abs(q[i] - h[0][i]) for i in range(3)) > 8:
                continue
            if S.glyph(n) == "#":
                g.set(*q, WARM_SURFACE)
            elif n.endswith("_stairs") and not n.startswith("birch"):          # an inner head or reveal stair
                g.set(*q, "birch_stairs", **g.b[q][1])


# ---------------------------------------------------------------------------------------------------- 3. the spire
def spire(g):
    """Over r11's clock stage and cornice (15): a steep spire of Macaw's prismarine-brick roof - the 5 x 5 eave ring
    (16 steep, 17 upper steep), the 3 x 3 ring (18, 19), a pyramid top (20) and an iron finial (21): 2 rise per run,
    one smooth verdigris surface (no lead rolls, no hip stones). Round 2: weathering drips of grey-green cyan
    terracotta in clusters down the faces (DRIPS - never an a-b-a-b-a run: no chequer), about a third of the seen
    spire: the critics' "uniform copper", and its value 0.52-0.54 down towards 0.36-0.48; the bed the spire's own stone."""
    x0, x1, z0, z1 = TOWER
    for q in [q for q in g.b if x0 - 1 <= q[0] <= x1 + 1 and z0 - 1 <= q[2] <= z1 + 1 and q[1] > R.CORNICE_Y]:
        del g.b[q]
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    for k, (y, (a0, a1, b0, b1)) in enumerate(((16, (x0 - 1, x1 + 1, z0 - 1, z1 + 1)), (18, (x0, x1, z0, z1)))):
        for (x, z), f, sh in ring_cells(a0, a1, b0, b1):
            mat = DRIP if (x, z) in DRIPS[k] else SPIRE
            macaw(g, (x, y, z), mat + "_steep_roof", f, sh)
            macaw(g, (x, y + 1, z), mat + "_upper_steep_roof", f, sh)
        for x in range(a0 + 1, a1):                        # the bed inside the ring (hidden; round 2: the spire's own
            for z in range(b0 + 1, b1):                    # stone - the dark bed showed as a dotted seam at the hip)
                for yy in (y, y + 1):
                    g.set(x, yy, z, "prismarine_bricks")
    g.set(cx, 20, cz, SPIRE + "_top_roof", part="pyramid")
    g.set(cx, 21, cz, "chain", axis="y", **DRY)


# ---------------------------------------------------------------------------------------------------- 4. the gate
def gate(g):
    """r11's gate, its frame re-dressed in stone: the jambs (x 0, z 9 and 15, y 1-9) stone bricks with a smooth-stone
    quoin every third course and a stone-brick stair chamfer on the course over it (the lit edge), the head (y 9) a
    stair springer each side, stone bricks and a chiseled keystone. The opening, the string course, the lunette stay."""
    R.gate(g)
    for y in range(1, 10):
        for z, f in ((9, "north"), (15, "south")):
            if y % 3 == 0:
                g.set(0, y, z, PALE)
            elif y % 3 == 1:
                stair(g, (0, y, z), STONE_ST, f, "top")
            else:
                g.set(0, y, z, STONE)
    for z, n in ((10, "st"), (11, "full"), (12, "key"), (13, "full"), (14, "st")):
        q = (0, 9, z)
        if n == "st":
            stair(g, q, STONE_ST, "south" if z == 10 else "north", "top")
        else:
            g.set(*q, "chiseled_stone_bricks" if n == "key" else STONE)


def gantry(g):
    """Round 2 (critics r1, both: the darkest front mass is the iron gantry over the south apron's rails - "the dark
    H" - likely lovish's "obsidian lump"; lovish asked, Oct 30: "idk"): its posts stone-brick walls, its beam smooth
    stone slabs - a mid-grey frame (camera value 0.28-0.48). The chassis and the chain hoist under it stay dark iron."""
    for z in (18, 23):
        for y in range(1, 5):
            g.set(1, y, z, STONE_W, **DRY)
    for z in range(18, 24):
        slab(g, (1, 5, z), PALE_SL)


# ---------------------------------------------------------------------------------------------------- 4. soot
def weathering(g):
    """Soot. At each stack's top: the cap is blackstone (stacks()), the course under the crown blackened round the
    shaft, and a drip fading down its south-west arris (blackstone, then deepslate brick, 3 courses). Under the eaves:
    a three-course deepslate-brick streak on every second brick pier of the camera faces (y 3-5, under the dentils)."""
    for s in M.AF["stacks"]:
        sx0, sx1, sz0, sz1 = s["shaft"]
        top = s["top"]
        for x in range(sx0, sx1 + 1):
            for z in range(sz0, sz1 + 1):
                g.set(x, top - 2, z, SOOT)
        g.set(sx0, top - 3, sz1, SOOT)                      # the drip down the west / south arris, fading
        g.set(sx0, top - 3, sz0, STREAK)                    # (round 2: the critics read top - 2 / - 3 as a collar)
        g.set(sx1, top - 3, sz1, STREAK)
        for y in (top - 4, top - 5):
            g.set(sx0, y, sz1, STREAK)
    piers = []
    for face, plane, span in (("south", 24, range(4, 17)), ("south", 16, range(0, 10)), ("west", 4, range(16, 25))):
        run = [u for u in span if all(g.b.get(at(face, plane, u, y), ("",))[0] == BRICK for y in (3, 4, 5))
               and at(face, plane, u, 6) in g.b]
        piers += [(face, plane, u) for u in run[::2]]
    for face, plane, u in piers:                           # round 2: three courses (1 x 2 did not read)
        for y in (3, 4, 5):
            g.set(*at(face, plane, u, y), STREAK)
    return piers


def detail():
    g = M.build_af()
    for q, (n, p) in list(g.b.items()):
        if n == M.WALL:
            g.b[q] = (BRICK, {})
        elif n == M.BASE and q[1] == 1:
            g.b[q] = (PLINTH, {})
    R.hollow(g)
    R.roofs(g)
    R.walls(g)
    gate(g)
    stacks(g)
    R.tower(g)
    spire(g)
    R.yard(g)
    gantry(g)
    R.life(g)
    R.gate_interior(g)
    R.bevel(g)
    room_lights(g)
    weathering(g)
    for y in range(1, L.H_MAX):                            # the spawn column / smoke-vent shaft
        g.clear(L.SPAWN[0], y, L.SPAWN[2])
    return g


if __name__ == "__main__":
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out = os.path.join(root, OUT)
    rig = L.write(detail(), out)
    print(L.check(rig, out))
    L.check_banned(rig, "brass_t1_r12")
