#!/usr/bin/env python3
"""The White Angel (Sunforged) capitol: a GOTHIC CASTLE-CATHEDRAL THAT GROWS OUT OF ITS HILL - six levels.

lovish, Oct 20 2026: the buildings "look pretty bad"; capitols far more intricate and bigger; "these buildings' upgrades
make it more intricate and detailed so it starts off small and gets bigger and bigger"; six levels (L1 placed, L2-L3 at
tech 1, L4-L5 at tech 2, L6 at tech 3); "I'd rather we add as much detail as possible then optimize it over and over";
and his reference, "THIS IS WHAT I'm looking for": a pale-stone gothic castle-cathedral crowning a rocky hill - a
cluster of slender spires and turrets with steep red conical roofs at different heights rising to one tall central
spire, a great gabled front with a rose window and lancets, buttresses and pinnacles, balconies and bartizans, the
building growing out of the rock with greenery on the slopes; strongly vertical, a jagged skyline, layered depth. With
it: "a good way to use light to create detail" (warm light from inside through many windows, lanterns on stairs and
paths, lit doorways) and "if you need the buildings to have insides ... don't let any limitations prevent this".
Inspiration only, never traced.

THE GROWTH (35 x 66 x 35 reserved at placement, one origin; a level only ADDS blocks or replaces some - plants on
paved-over ground aside):
  - L1  the CHAPEL-KEEP on its rock: the hill's core and plateau, the grand stair up the south face lined with
        lanterns, a tall square keep (buttressed corners, lancets lit from rooms inside, a corbelled parapet with
        bartizans) under its first spire - a steep red cone with a gold finial. Its ground hall is open to the plateau.
  - L2  the NAVE: the great hall south of the keep, its gable front to the camera - a pointed portal, the rose window,
        lancets - a true nave inside (two rows of columns, lit), a steep red roof with a crested ridge, buttresses.
  - L3  the FRONT: two round turrets flank the gable (lit storeys, conical roofs), flying buttresses with pinnacles
        along the nave, balconies with lanterns on the keep, a porch of braziers.
  - L4  the HILL RISES: rock spurs to the west and east, terraces of grass and moss, trees, ivy on the faces; the west
        wing on its spur with a tall turret, a bridge from it to the keep.
  - L5  the CLUSTER: the east wing and its turret (another height), turrets on the keep's corners, a second bridge,
        lanterns along both bridges and the path.
  - L6  the CROWN: the tall central spire (a lit belfry stage, a slender cone, a needle and a gold finial), pinnacles
        on every buttress, gold finials on every roof - the full jagged skyline.

THE LOOK: pale stone in calm planes (quartz bricks, smooth quartz trim, smooth-stone string courses - no speckled
stone on large faces), red tile roofs (bricks and brick stairs, steep: two courses per step in), gold finials, blue glass
only in the rose window, and LIGHT as detail - every storey's windows glow from a real room behind them (floor, carpet,
shelves, lanterns, a lit ceiling), lanterns line the stair, the bridges and the balconies.

RULES (check()): the box is 35 x 66 x 35 at every level; each level keeps every non-plant cell of the one before; no
lava, gravity blocks or redstone; campfires only in hearths or on pedestals; nothing pops off
(_SunRig.check_support); the spawn cell (inside the keep / nave, at plateau level) is open and walks out of the south
door to the stair. Units never get into the sealed rooms (glass panes and walls only). Not wired into the game.

Usage: python3 tools/capitol_white_angel.py OUT_DIR   -> OUT_DIR/white_angel_capitol_l1.nbt .. _l6.nbt
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from capitol_town_centre import Cap, LANTERN, FLOWERS, hsh   # noqa: E402

R = 17          # 35 x 35
H = 72
LEVELS = 6
PLANTS = set(FLOWERS) | {"vine"}
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
VEC = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
LEAF = dict(persistent="true", distance="1", waterlogged="false")
CAMPFIRE = dict(lit="true", signal_fire="false", facing="south", waterlogged="false")

WALL = "quartz_bricks"
TRIM = "smooth_quartz"
CARVE = "chiseled_quartz_block"
BAND = "polished_andesite"        # cool grey-blue trims: string courses, corbel tables
BAND_ST = "polished_andesite_stairs"
BASE = "stone_bricks"              # darker footings
TWALL = "calcite"                  # the turrets' pale stone - a second calm zone beside the ashlar
ROOF_STYLES = {"red": ("bricks", "brick_stairs", "brick_wall"),
               "slate": ("deepslate_tiles", "deepslate_tile_stairs", "deepslate_tile_wall")}
ROOF, ROOF_ST, ROOF_WALL = ROOF_STYLES[os.environ.get("BAT_ROOF", "red")]
DOME, DOME_ST, DOME_SLAB = "deepslate_tiles", "deepslate_tile_stairs", "deepslate_tile_slab"   # the crown's dark dome
FLOOR_IN = "dark_oak_planks"

PLAT = 6            # the plateau's top block (people stand at y7)
HC = (0, -2)        # the hill's centre
YOFF = 3            # the castle stands this much higher than the structure's floor: the crag below it is real rock


# =========================================================================================== small helpers
def inside(x, z):
    return -R <= x <= R and -R <= z <= R


def S(c, x, y, z, name, **p):
    if inside(x, z) and 0 <= y + YOFF < H:
        c.set(x, y + YOFF, z, name, **p)


def ST(c, x, y, z, facing, half="bottom", name="smooth_quartz_stairs"):
    if inside(x, z) and 0 <= y + YOFF < H:
        c.stair(x, y + YOFF, z, facing, half, name)


def G(c, x, y, z):
    return c.get(x, y + YOFF, z)


def SLAB(c, x, y, z, typ="bottom", name="smooth_quartz_slab"):
    S(c, x, y, z, name, type=typ, waterlogged="false")


def fill(c, x0, x1, y0, y1, z0, z1, name, **p):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                S(c, x, y, z, name, **p)


def dist(x, z, cx, cz):
    return math.hypot(x - cx, z - cz)


def ring(cx, cz, r):
    return [(x, z) for x in range(cx - r - 1, cx + r + 2) for z in range(cz - r - 1, cz + r + 2)
            if abs(dist(x, z, cx, cz) - r) < 0.5]


def disc(cx, cz, r):
    return [(x, z) for x in range(cx - r - 1, cx + r + 2) for z in range(cz - r - 1, cz + r + 2)
            if dist(x, z, cx, cz) < r + 0.5]


def toward(x, z, cx, cz):
    dx, dz = cx - x, cz - z
    if abs(dx) >= abs(dz):
        return "east" if dx > 0 else "west"
    return "south" if dz > 0 else "north"


def lantern_on(c, x, y, z):
    S(c, x, y, z, "lantern", **LANTERN)


def lamp(c, x, y, z, h=1):
    for k in range(h):
        S(c, x, y + k, z, "diorite_wall")
    lantern_on(c, x, y + h, z)


def finial(c, x, y, z, needle=2):
    for k in range(needle):
        S(c, x, y + k, z, ROOF_WALL)
    S(c, x, y + needle, z, "gold_block")
    S(c, x, y + needle + 1, z, "end_rod", facing="up")


# =========================================================================================== roofs
def cone(c, cx, cz, r, y0, needle=2, eaves=True, courses=3):
    """A steep round cone of red tile: each ring takes two courses (a full course, then stairs climbing in), eaves of
    upside-down stairs one ring out, a needle and a gold finial on top."""
    if eaves:
        for x, z in ring(cx, cz, r + 1):
            ST(c, x, y0 - 1, z, toward(x, z, cx, cz), "top", ROOF_ST)
    y = y0
    for rr in range(r, 0, -1):
        for k in range(courses - 1):
            for x, z in disc(cx, cz, rr):
                S(c, x, y + k, z, ROOF)
        for x, z in disc(cx, cz, rr - 1):
            S(c, x, y + courses - 1, z, ROOF)
        for x, z in ring(cx, cz, rr):
            ST(c, x, y + courses - 1, z, toward(x, z, cx, cz), "bottom", ROOF_ST)
        y += courses
    S(c, cx, y, cz, ROOF)
    finial(c, cx, y + 1, cz, needle)
    return y + needle + 2


def gable_roof(c, x0, x1, z0, z1, y0, front=None):
    """A steep pitched roof over the rectangle x0..x1 (odd width) along z: two courses per step in, eaves one out on
    the long sides, the gable ends' triangles in pale stone (front: the z it faces, which gets stone triangles), a
    crested ridge of brick wall."""
    half = (x1 - x0) // 2 + 1
    xc = (x0 + x1) // 2
    y = y0
    for k in range(half + 1):
        xl, xr = x0 - 1 + k, x1 + 1 - k
        if xl > xr:
            break
        for z in range(z0, z1 + 1):
            gable = z in (z0, z1)
            for x in range(xl, xr + 1):
                edge = x in (xl, xr)
                S(c, x, y, z, ROOF if (edge or not gable) else WALL)
            for x in range(xl + 1, xr):
                S(c, x, y + 1, z, ROOF if not gable else WALL)
            if xl < xr:
                ST(c, xl, y + 1, z, "east", "bottom", ROOF_ST)
                ST(c, xr, y + 1, z, "west", "bottom", ROOF_ST)
            else:
                S(c, xl, y + 1, z, ROOF)
        y += 2
    for z in range(z0, z1 + 1):
        S(c, xc, y, z, ROOF_WALL)
    return y


# =========================================================================================== walls, windows, rooms
def lit_lancet(c, x, z, y0, h, out):
    """A tall lancet in a wall cell column: glass panes, a carved head, a sill outside."""
    for y in range(y0, y0 + h):
        S(c, x, y, z, "glass_pane")
    S(c, x, y0 + h, z, CARVE)
    ox, oz = VEC[out]
    SLAB(c, x + ox, y0 - 1, z + oz, "top")


def room(c, x0, x1, z0, z1, yf, yc, shelves=True):
    """A lit room inside walls (x0..x1, z0..z1 are the INNER cells): plank floor at yf, a red runner, bookshelves on
    the long walls, lanterns on plank tables in the corners, glowing ceiling (shroomlight) at yc."""
    fill(c, x0, x1, yf, yf, z0, z1, FLOOR_IN)
    fill(c, x0, x1, yc, yc, z0, z1, FLOOR_IN)
    xm = (x0 + x1) // 2
    for z in range(z0, z1 + 1):
        S(c, xm, yf + 1, z, "red_carpet")
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if (x - x0) % 2 == 0 and (z - z0) % 2 == 1:
                S(c, x, yc, z, "shroomlight")
    if shelves and x1 - x0 >= 2:
        for z in range(z0 + 1, z1):
            if (z - z0) % 3 != 0:
                S(c, x0, yf + 1, z, "bookshelf")
                S(c, x1, yf + 1, z, "bookshelf")
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        S(c, x, yf + 1, z, "dark_oak_planks")
        lantern_on(c, x, yf + 2, z)


def face_cells(x0, x1, z0, z1, face):
    """The inner cells of a face (corners excluded) and its outward vector."""
    if face == "south":
        return [(x, z1) for x in range(x0 + 1, x1)], (0, 1)
    if face == "north":
        return [(x, z0) for x in range(x0 + 1, x1)], (0, -1)
    if face == "west":
        return [(x0, z) for z in range(z0 + 1, z1)], (-1, 0)
    return [(x1, z) for z in range(z0 + 1, z1)], (1, 0)


def tower_shell(c, x0, x1, z0, z1, y0, y1, floors, win_faces=("south", "west", "east", "north")):
    """A square tower in layers, the way his castle references are drawn: a darker stone footing, pale ashlar, at each
    floor a grey string course with a corbel table under it (upside-down stairs, one cell proud), lancets in a ROW on
    every face of every storey (every other cell, lit from a real room behind), pilaster strips between them standing
    one cell proud (from the second storey up), arrow slits on the ground storey."""
    fill(c, x0, x1, y0, y1, z0, z0, WALL)
    fill(c, x0, x1, y0, y1, z1, z1, WALL)
    fill(c, x0, x0, y0, y1, z0, z1, WALL)
    fill(c, x1, x1, y0, y1, z0, z1, WALL)
    for x in range(x0, x1 + 1):                            # the footing
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                S(c, x, y0, z, BASE)
    for k, yf in enumerate(floors[:-1]):
        yc = floors[k + 1]
        room(c, x0 + 1, x1 - 1, z0 + 1, z1 - 1, yf, yc)
        h = max(2, yc - yf - 2)
        for face in ("south", "north", "west", "east"):
            cells, (ox, oz) = face_cells(x0, x1, z0, z1, face)
            for i, (x, z) in enumerate(cells):
                S(c, x, yc, z, BAND)
                ST(c, x + ox, yc, z + oz, OPP[{(0, 1): "south", (0, -1): "north", (-1, 0): "west",
                                              (1, 0): "east"}[(ox, oz)]], "top", BAND_ST)   # the corbel table
                win = i % 2 == 0
                if face in win_faces and win:
                    if k == 0:
                        S(c, x, yf + 2, z, "black_stained_glass_pane")       # an arrow slit
                        S(c, x, yf + 3, z, "black_stained_glass_pane")
                    else:
                        lit_lancet(c, x, z, yf + 1, h, face)
                elif k > 0 and not win and face in win_faces:
                    for y in range(yf + 1, yc):
                        S(c, x + ox, y, z + oz, TRIM)                       # a pilaster strip
        for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            S(c, x, yc, z, BAND)


def round_tower(c, cx, cz, r, y0, y1, floors_every=5, roof=True, needle=2):
    """A slender round turret like his reference's: pale calcite shaft on a darker footing, a corbel ring and a grey
    band at every storey, lit lancets on four sides of every storey, a sealed lit room inside, machicolations under the
    crown, little openwork pinnacles round the roof's foot and a tall steep cone overhanging the shaft."""
    for y in range(y0, y1 + 1):
        for x, z in ring(cx, cz, r):
            S(c, x, y, z, BASE if y == y0 else TWALL)
    if y0 <= 2:            # a turret rising from the ground: its first storey is solid (no room at walking height)
        for y in range(y0, y0 + floors_every):
            for x, z in disc(cx, cz, r - 1):
                S(c, x, y, z, WALL)
        y0 += floors_every
    yf = y0
    while yf + floors_every <= y1:
        yc = yf + floors_every
        for x, z in disc(cx, cz, r - 1):
            S(c, x, yf, z, FLOOR_IN)
            S(c, x, yc, z, "shroomlight" if (x, z) == (cx, cz) else FLOOR_IN)
        if r >= 2:
            lantern_on(c, cx, yf + 1, cz) if r == 2 else None
        for x, z in ring(cx, cz, r):
            S(c, x, yc, z, BAND)
        for x, z in ring(cx, cz, r + 1):
            if c.get(x, yc + YOFF, z)[0] == "air":
                ST(c, x, yc, z, toward(x, z, cx, cz), "top", BAND_ST)
        for (fx, fz, face) in ((0, 1, "south"), (-1, 0, "west"), (1, 0, "east"), (0, -1, "north")):
            lit_lancet(c, cx + fx * r, cz + fz * r, yf + 2, floors_every - 3, face)
        yf = yc
    for x, z in ring(cx, cz, r + 1):                  # machicolations: a corbel ring, brackets under it
        ST(c, x, y1, z, toward(x, z, cx, cz), "top", BAND_ST)
        if (x + z) % 2 == 0:
            S(c, x, y1 - 1, z, "stone_brick_wall")
    if roof:
        for x, z in disc(cx, cz, r + 1):
            S(c, x, y1 + 1, z, TRIM)
        for x, z in ring(cx, cz, r + 1):
            if abs(x - cx) == abs(z - cz) or x == cx or z == cz:
                if (abs(x - cx) == abs(z - cz)):
                    S(c, x, y1 + 2, z, "iron_bars")
                    S(c, x, y1 + 3, z, "end_rod", facing="up")
        return cone(c, cx, cz, r + 1, y1 + 2, needle, eaves=False, courses=4)
    return y1


def buttress(c, x, z, out, y0, y1, depth=2):
    """A stepped buttress: a pier `depth` deep out from the wall at (x, z) on side `out`, set-offs (stairs) climbing
    back to the wall, a pinnacle on the outer pier."""
    ox, oz = VEC[out]
    for d in range(1, depth + 1):
        top = y1 - 3 * (d - 1)
        for y in range(y0, top + 1):
            S(c, x + ox * d, y, z + oz * d, WALL)
        ST(c, x + ox * d, top + 1, z + oz * d, OPP[out], "bottom", "quartz_stairs")
    return (x + ox * depth, y1 - 3 * (depth - 1) + 1, z + oz * depth)


def pinnacle(c, x, y, z):
    """An openwork pinnacle: a carved base, iron-bar tracery, a wall shaft, more tracery, a spike."""
    S(c, x, y, z, CARVE)
    S(c, x, y + 1, z, "iron_bars")
    S(c, x, y + 2, z, "diorite_wall")
    S(c, x, y + 3, z, "iron_bars")
    S(c, x, y + 4, z, "end_rod", facing="up")


# =========================================================================================== the hill
GROUND = -YOFF       # the structure's floor in the castle's frame


def hill_h1(x, z):
    """The crag in stacked cliff tiers (radial only: the max of terraced cones has no pits a unit could be trapped
    in): the plateau, a cliff down to a ledge, a cliff down to a lower ledge, the ground."""
    d = dist(x, z, *HC)
    a = math.atan2(z - HC[1], x - HC[0])
    # each tier's edge wanders with the angle (low frequency, so the field stays free of pits): jutting buttresses of
    # rock and bays, never a wedding cake of rings; the plateau never shrinks under the castle
    r_plat = 9.6 + max(0.0, 1.3 * math.sin(3 * a + 0.7))
    r_mid = 11.4 + 1.4 * math.sin(2 * a + 2.1) + 0.6 * math.sin(5 * a)
    r_low = 13.6 + 1.3 * math.sin(3 * a + 4.0) + 0.5 * math.sin(7 * a + 1)
    if d <= r_plat:
        return PLAT
    if d <= max(r_mid, r_plat + 1):
        return 2
    if d <= max(r_low, r_mid + 1):
        return -1
    if d <= 16.4:
        return GROUND          # the foot: grass round the crag, where the trees grow
    return GROUND - 1          # nothing (the world's own ground)


def hill_h4(x, z):
    h = hill_h1(x, z)
    d = dist(x, z, *HC)
    # the west spur (the west wing's rock) and the east spur (the east turret's crag)
    dw = dist(x, z, -11, -6)
    if dw <= 5.5:
        h = max(h, PLAT if dw <= 3.6 else PLAT - int(math.ceil((dw - 3.6) * 2)))
    return h


def hill_h5(x, z):
    """L5: the east crag rises for its turret (it comes with the turret: alone its steep sides left a one-way cell)."""
    h = hill_h4(x, z)
    de = dist(x, z, 11, -8)
    if de <= 5.5:
        h = max(h, PLAT + 3 if de <= 2.6 else PLAT + 3 - int(math.ceil((de - 2.6) * 2.4)))
    return h


def rock_name(x, y, z):
    """Strata: horizontal bands two or three high, each one stone (calm zones that read as stacked rock, not pepper)."""
    band = (y + YOFF + (hsh(x // 4, z // 4) % 2)) // 3
    return ("stone", "andesite", "tuff", "stone", "calcite")[band % 5]


def in_stair(x, z):
    """The grand stair's corridor (and a cell either side): the hill never builds over it, nothing grows on it."""
    return -4 <= x <= 4 and z >= HC[1] + 9


def build_hill(c, hfun, prev=None):
    """Solid rock from y0 to the height field, the top a terrace of grass and moss (plateau: pale paving), ivy hanging
    on south and west faces, bushes and the odd tree on the terraces."""
    for x in range(-R, R + 1):
        for z in range(-R, R + 1):
            h = hfun(x, z)
            if h < GROUND:
                continue
            if prev is not None and (h <= prev(x, z) or in_stair(x, z)):
                continue
            for y in range(GROUND, h):
                S(c, x, y, z, rock_name(x, y, z))
            if h == PLAT and dist(x, z, *HC) <= 9.6:
                terrace = -3 <= x <= 3 and 4 <= z <= 8
                S(c, x, h, z, "polished_andesite" if terrace else "moss_block" if hsh(x, z) % 4 == 0 else
                  "grass_block", **({} if terrace or hsh(x, z) % 4 == 0 else {"snowy": "false"}))
            elif hsh(x, z) % 5 == 0 and h > GROUND:
                S(c, x, h, z, rock_name(x, h, z))       # bare rock on the ledge
            else:
                S(c, x, h, z, "moss_block" if hsh(x, z) % 3 == 0 else "grass_block",
                  **({} if hsh(x, z) % 3 == 0 else {"snowy": "false"}))


def overhangs(c, hfun):
    """Lips of rock over the upper ledge where the plateau's cliff juts out, roots hanging under them."""
    for x in range(-R, R + 1):
        for z in range(-R, R + 1):
            if hfun(x, z) != 2 or hsh(x * 5, z * 3) % 3 or in_stair(x, z):
                continue
            inward = [(x + ox, z + oz) for ox, oz in ((1, 0), (-1, 0), (0, 1), (0, -1))]
            if any(inside(a, b) and hfun(a, b) == PLAT for a, b in inward):
                S(c, x, PLAT, z, rock_name(x, PLAT, z))
                S(c, x, PLAT - 1, z, "hanging_roots", waterlogged="false")


def greenery(c, hfun):
    """Ivy on the camera faces of the rock, azalea bushes and small trees on the terraces."""
    trunks = []
    for x in range(-R, R + 1):
        for z in range(-R, R + 1):
            h = hfun(x, z)
            if h <= GROUND:
                h = GROUND
            for (face, ox, oz) in (("north", 0, 1), ("east", -1, 0)):   # cell south / west of the rock
                nx, nz = x + ox, z + oz
                if not inside(nx, nz):
                    continue
                nh = hfun(nx, nz)
                for y in range(max(nh + 1, GROUND + 1), h):
                    if hsh(x * 3 + y, z) % 6 == 0:
                        S(c, nx, y, nz, "vine", **{face: "true"})
            top = G(c, x, h, z)[0]
            near_path = -6 <= x <= 6 and z >= HC[1] + 7
            if near_path:
                continue
            if top in ("grass_block", "moss_block") and GROUND <= h < PLAT and G(c, x, h + 1, z)[0] == "air":
                k = hsh(x * 7, z * 5) % (9 if h == GROUND else 17)   # trees thick round the foot, fewer on ledges
                if k == 0 and not any(abs(x - tx) <= 2 and abs(z - tz) <= 2 for tx, tz in trunks):
                    trunks.append((x, z))     # spaced: three trunks and a rock face boxed a cell in
                    for y in (h + 1, h + 2, h + 3, h + 4):
                        S(c, x, y, z, "birch_log" if (x + z) % 2 else "oak_log", axis="y")
                    for (lx, lz, ly) in ((0, 0, 5), (1, 0, 4), (-1, 0, 4), (0, 1, 4), (0, -1, 4), (1, 0, 5),
                                         (-1, 0, 5), (0, 1, 5), (0, -1, 5), (0, 0, 6)):
                        if G(c, x + lx, h + ly, z + lz)[0] == "air":
                            S(c, x + lx, h + ly, z + lz, "flowering_azalea_leaves" if (lx + lz) % 2 else
                              "azalea_leaves", **LEAF)
                elif k in (3, 9, 15) and h > GROUND:     # bushes on ledges only: at the foot they boxed cells in
                    S(c, x, h + 1, z, "azalea_leaves", **LEAF)


# =========================================================================================== L1: the keep on its rock
KX0, KX1, KZ0, KZ1 = -3, 3, -10, -4      # the keep's walls (7 x 7)
K_FLOORS = (PLAT + 1, 12, 17, 22)         # storey floors; parapet above 22
K_TOP = 25


def stair_path(c):
    """The grand stair up the south face: 3 wide from the foot to the plateau's edge, lanterns on posts either side."""
    zt = HC[1] + 10            # the plateau's south edge (z 8)
    for k in range(PLAT - GROUND + 1):
        z = zt + k
        y = PLAT - k
        for x in (-2, -1, 0, 1, 2):
            for yy in range(GROUND, y):
                S(c, x, yy, z, "stone_bricks")
            if y >= GROUND and k > 0:
                ST(c, x, y, z, "north", "bottom", "stone_brick_stairs")
        for x in (-3, 3):                                  # the cut's walls: rock, a lantern in a niche every other step
            for yy in range(GROUND, y + 1):
                S(c, x, yy, z, rock_name(x, yy, z) if yy < y else BASE)
            if k % 2 == 1:
                lamp(c, x, y + 1, z, 1)
    # the landing at the top: a paved porch in front of the keep / nave
    for x in range(-3, 4):
        S(c, x, PLAT, zt, "polished_andesite")


def l1_keep(c):
    build_hill(c, hill_h1)
    overhangs(c, hill_h1)
    stair_path(c)
    # the keep's footing: a battered plinth of stone bricks, then the shell
    for x in range(KX0 - 1, KX1 + 2):
        for z in range(KZ0 - 1, KZ1 + 2):
            if x in (KX0 - 1, KX1 + 1) or z in (KZ0 - 1, KZ1 + 1):
                ST(c, x, PLAT + 1, z, toward(x, z, 0, (KZ0 + KZ1) // 2), "bottom", "stone_brick_stairs")
    tower_shell(c, KX0, KX1, KZ0, KZ1, PLAT + 1, K_TOP - 1, K_FLOORS)
    # the ground hall is open: a pointed door 3 wide x 3 high on the south face, a lit doorway (lanterns inside)
    for x in (-1, 0, 1):
        for y in (PLAT + 1, PLAT + 2, PLAT + 3):
            S(c, x, y, KZ1, "iron_bars")          # a portcullis: the hall is seen, never entered
    for x in range(KX0 + 1, KX1):                      # the ground hall: open floor, no furniture in the way
        for z in range(KZ0 + 1, KZ1):
            for y in (PLAT + 1, PLAT + 2, PLAT + 3, PLAT + 4):
                S(c, x, y, z, "air")
            S(c, x, PLAT, z, "polished_andesite")
    ST(c, -1, PLAT + 4, KZ1, "west", "top")
    ST(c, 1, PLAT + 4, KZ1, "east", "top")
    S(c, 0, PLAT + 4, KZ1, CARVE)
    S(c, 0, PLAT + 5, KZ1, "gold_block")
    for (x, z) in ((KX0 + 1, KZ0 + 1), (KX1 - 1, KZ0 + 1)):
        S(c, x, PLAT + 1, z, "chiseled_quartz_block")
        lantern_on(c, x, PLAT + 2, z)
    S(c, 0, K_FLOORS[1], (KZ0 + KZ1) // 2, "shroomlight")
    # corner buttresses (two deep, set-offs) on the keep's four corners, both faces
    for (x, z, faces) in ((KX0, KZ1, ("west", "south")), (KX1, KZ1, ("east", "south")),
                          (KX0, KZ0, ("west", "north")), (KX1, KZ0, ("east", "north"))):
        for f in faces:
            buttress(c, x, z, f, PLAT + 1, 18, depth=2)
    # the parapet: a corbel course of upside-down stairs, crenels, bartizans (round r1 turrets) on the corners
    for x in range(KX0 - 1, KX1 + 2):
        for z in range(KZ0 - 1, KZ1 + 2):
            if x in (KX0 - 1, KX1 + 1) or z in (KZ0 - 1, KZ1 + 1):
                ST(c, x, K_TOP - 1, z, toward(x, z, 0, (KZ0 + KZ1) // 2), "top")
                S(c, x, K_TOP, z, WALL)
                if (x + z) % 2 == 0:
                    S(c, x, K_TOP + 1, z, WALL)
    fill(c, KX0, KX1, K_TOP, K_TOP, KZ0, KZ1, TRIM)
    for (x, z) in ((KX0 - 1, KZ0 - 1), (KX1 + 1, KZ0 - 1), (KX0 - 1, KZ1 + 1), (KX1 + 1, KZ1 + 1)):
        for y in (K_TOP, K_TOP + 1, K_TOP + 2):
            S(c, x, y, z, WALL)
        S(c, x, K_TOP + 3, z, ROOF)
        ST(c, x, K_TOP + 4, z, "north", "bottom", ROOF_ST)
        S(c, x, K_TOP + 4, z, ROOF)
        finial(c, x, K_TOP + 5, z, 1)
    # the first spire: a steep cone r3 on a drum of trim, gold finial
    for x, z in disc(0, (KZ0 + KZ1) // 2, 3):
        S(c, x, K_TOP + 1, z, TRIM)
    cone(c, 0, (KZ0 + KZ1) // 2, 3, K_TOP + 2, needle=3)
    # light on the plateau: lamps on the terrace's corners (outside every later wall)
    for x in (-7, 7):
        lamp(c, x, PLAT + 1, 6, 1)


# =========================================================================================== L2: the nave
NX0, NX1, NZ0, NZ1 = -4, 4, -3, 3         # the nave's walls (9 wide, 7 long), the gable front on z = 3; the terrace
                                          # in front of it (z 4..7, 7 wide) is the open court a wide body turns in
N_TOP = 20                                # the wall plate


def l2_nave(c):
    # walls
    fill(c, NX0, NX1, PLAT + 1, N_TOP, NZ1, NZ1, WALL)
    fill(c, NX0, NX0, PLAT + 1, N_TOP, NZ0, NZ1, WALL)
    fill(c, NX1, NX1, PLAT + 1, N_TOP, NZ0, NZ1, WALL)
    for x in range(NX0, NX1 + 1):                       # string courses
        S(c, x, 12, NZ1, BAND)
    for z in range(NZ0, NZ1 + 1):
        S(c, NX0, 12, z, BAND)
        S(c, NX1, 12, z, BAND)
    # the nave inside: paved floor, two rows of columns, a lit vault (shroomlight in a stone ceiling), open to the portal
    for x in range(NX0 + 1, NX1):
        for z in range(NZ0, NZ1):
            S(c, x, PLAT, z, "polished_andesite" if x == 0 else BAND)
            for y in range(PLAT + 1, N_TOP):
                pass          # the nave's volume stays as it is (empty, or the keep's foot where they meet)
            S(c, x, N_TOP, z, "shroomlight" if (x == 0 and z % 2 == 0) else TRIM)
    for z in range(NZ0 + 1, NZ1, 2):
        for x in (-2, 2):
            S(c, x, PLAT + 1, z, CARVE)
            for y in range(PLAT + 2, N_TOP - 1):
                S(c, x, y, z, "quartz_pillar", axis="y")
            S(c, x, N_TOP - 1, z, "gold_block")
    for z in (NZ0 + 2, NZ1 - 2):                         # lanterns on pillar-bases down the aisles
        for x in (-3, 3):
            S(c, x, PLAT + 1, z, CARVE)
            lantern_on(c, x, PLAT + 2, z)
    # the keep's south wall: the portcullis stays (the keep's hall and the nave are seen through bars)
    # side lancets (lit by the nave), two storeys
    for z in range(NZ0 + 1, NZ1, 2):
        lit_lancet(c, NX0, z, PLAT + 3, 4, "west")
        lit_lancet(c, NX1, z, PLAT + 3, 4, "east")
        lit_lancet(c, NX0, z, 13, 3, "west")
        lit_lancet(c, NX1, z, 13, 3, "east")
    # the portal: pointed, 3 wide x 4 high, recessed one cell in a carved frame, a gold keystone, lamps either side
    for x in (-1, 0, 1):
        for y in range(PLAT + 1, PLAT + 5):
            S(c, x, y, NZ1, "iron_bars")      # the portal's portcullis: the lit nave seen, never entered
    for x in (-2, 2):
        for y in range(PLAT + 1, PLAT + 6):
            S(c, x, y, NZ1 + 1, CARVE if y == PLAT + 5 else TRIM)
    ST(c, -1, PLAT + 5, NZ1 + 1, "west", "top")
    ST(c, 1, PLAT + 5, NZ1 + 1, "east", "top")
    S(c, 0, PLAT + 5, NZ1 + 1, "gold_block")
    ST(c, -1, PLAT + 4, NZ1, "west", "top")
    ST(c, 1, PLAT + 4, NZ1, "east", "top")
    for x in (-3, 3):
        lamp(c, x, PLAT + 1, NZ1 + 1, 2)
    # the ROSE WINDOW: a disc r3 centred on the gable front at y14 - carved tracery ring, spokes, coloured glass,
    # lit from the nave behind
    rc = (0, 15)
    for x in range(-4, 5):
        for y in range(11, 20):
            d = math.hypot(x - rc[0], y - rc[1])
            if d < 4.5:
                tr = abs(d - 4) < 0.5 or x == 0 or y == rc[1] or abs(abs(x) - abs(y - rc[1])) == 0 or d < 0.8
                S(c, x, y, NZ1, CARVE if tr else ("blue_stained_glass" if (x + y) % 2 else
                                                   "yellow_stained_glass" if abs(x) == 1 else
                                                   "red_stained_glass"))
    S(c, 0, 15, NZ1, "gold_block")
    for x, y in ((x, y) for x in range(-5, 6) for y in range(15, 21)):     # the rose's hood moulding, proud
        if abs(math.hypot(x, y - rc[1]) - 5) < 0.5:
            ST(c, x, y, NZ1 + 1, "north", "top", "quartz_stairs")
    # TRACERY on the gable (his cathedral-front reference): a pointed arch of openwork wall standing one cell proud of
    # the front round the rose, a nested inner arch of iron bars, crockets (top stairs) up its rake, a carved finial
    z = NZ1 + 1
    # (the arch stands one cell in front of the hood: z + 1)
    z = NZ1 + 2
    outer = [(-5, y) for y in range(PLAT + 6, 21)] + [(5, y) for y in range(PLAT + 6, 21)] + \
            [(-4, 21), (4, 21), (-4, 22), (4, 22), (-3, 23), (3, 23), (-2, 24), (2, 24), (-1, 25), (1, 25), (0, 26)]
    for (x, y) in outer:
        S(c, x, y, z, "diorite_wall")
    for (x, y) in ((-4, 20), (4, 20), (-3, 22), (3, 22), (-2, 23), (2, 23), (-1, 24), (1, 24)):
        S(c, x, y, z, "iron_bars")
    for (x, y, f) in ((-4, 23, "east"), (4, 23, "west"), (-3, 24, "east"), (3, 24, "west"), (-2, 25, "east"),
                      (2, 25, "west")):
        ST(c, x, y, z, f, "bottom", "quartz_stairs")        # crockets up the rake
    S(c, 0, 27, z, CARVE)
    S(c, 0, 28, z, "iron_bars")
    S(c, 0, 29, z, "end_rod", facing="up")
    for x in (-5, 5):                                       # pinnacles crowning the arch's legs
        pinnacle(c, x, 21, z)
    for y in range(PLAT + 6, 20):                           # the inner lights of the arch: bars either side
        for x in (-4, 4):
            S(c, x, y, z, "iron_bars")
    # buttresses on the side walls between the lancets
    for z in range(NZ0 + 2, NZ1 + 1, 2):
        if z == NZ1:
            continue
        buttress(c, NX0, z, "west", PLAT + 1, 16, depth=2)
        buttress(c, NX1, z, "east", PLAT + 1, 16, depth=2)
    # the steep red roof, crested
    top = gable_roof(c, NX0, NX1, NZ0, NZ1, N_TOP + 1)
    # a small lit oculus in the gable triangle
    S(c, 0, N_TOP + 4, NZ1, "glass_pane")
    S(c, 0, N_TOP + 4, NZ1 - 1, "shroomlight")
    S(c, 0, top + 1, NZ1, "gold_block")
    S(c, 0, top + 2, NZ1, "end_rod", facing="up")


# =========================================================================================== L3: the front
def l3_front(c):
    # two round turrets flank the gable (r2 at the front corners), lit storeys, cones at different heights
    # beside the nave, behind the gable's line, so the rose and the portal stay in view from the RTS camera
    round_tower(c, -7, 0, 2, PLAT + 1, 26, roof=True, needle=2)
    round_tower(c, 7, 0, 2, PLAT + 1, 29, roof=True, needle=3)
    # flying buttresses: from the outer piers an arch of stairs up to the nave wall at the clerestory, pinnacles
    for z in range(NZ0 + 2, NZ1, 2):
        for (x, out, toward_wall) in ((NX0 - 2, "west", "east"), (NX1 + 2, "east", "west")):
            pinnacle(c, x, 17, z)
            mid = x + (1 if out == "west" else -1)
            ST(c, mid, 17, z, OPP[toward_wall], "bottom", "quartz_stairs")
            ST(c, mid, 16, z, toward_wall, "top", "quartz_stairs")
    # balconies on the keep's south face at the third storey: a slab ledge on brackets, a wall rail, lanterns
    for x in range(KX0, KX1 + 1):
        if x in (KX0, KX1):
            continue
    for x in (KX0 - 1, KX1 + 1):
        for z in range(KZ0, KZ1 + 1):
            ST(c, x, 16, z, "east" if x < 0 else "west", "top")
            SLAB(c, x, 17, z, "bottom")
    for (x, z) in ((KX0 - 1, KZ0 + 2), (KX1 + 1, KZ0 + 2)):
        S(c, x, 17, z, "smooth_quartz")
        lantern_on(c, x, 18, z)
    # a porch of braziers at the stair head (on pedestals two high)
    for x in (-3, 3):
        S(c, x, PLAT + 1, HC[1] + 10, CARVE)
        S(c, x, PLAT + 2, HC[1] + 10, "gold_block")
        S(c, x, PLAT + 3, HC[1] + 10, "campfire", **CAMPFIRE)


# =========================================================================================== L4: the hill rises, west wing
WX0, WX1, WZ0, WZ1 = -14, -9, -9, -3     # the west wing on its spur


def l4_west(c):
    build_hill(c, hill_h4, prev=hill_h1)
    greenery(c, hill_h4)
    tower_shell(c, WX0, WX1, WZ0, WZ1, PLAT + 1, 15, (PLAT + 1, 11, 15), win_faces=("south", "west"))
    for x in range(WX0, WX1 + 1):
        for z in range(WZ0, WZ1 + 1):
            S(c, x, 16, z, TRIM)
    # a hipped red roof (a long cone) over the wing
    cone(c, (WX0 + WX1) // 2, (WZ0 + WZ1) // 2, 3, 17, needle=1)
    # the wing's tall turret on the spur's outer corner
    round_tower(c, -14, -11, 2, 1, 27, needle=3)
    # a bridge from the wing's roof walk to the keep's west face at y16: an arch of stone with a rail and lanterns
    for x in range(WX1 + 1, KX0 - 2):
        for z in (-7, -6):
            S(c, x, 15, z, WALL)
            ST(c, x, 14, z, "north" if z == -7 else "south", "top")
        S(c, x, 16, -8, "diorite_wall")
        S(c, x, 16, -5, "diorite_wall")
        if x % 2 == 0:
            lantern_on(c, x, 17, -8)
            lantern_on(c, x, 17, -5)


# =========================================================================================== L5: east wing, the cluster
def l5_east(c):
    build_hill(c, hill_h5, prev=hill_h4)
    # the east crag's turret: taller, slimmer, on the rock
    round_tower(c, 11, -8, 2, PLAT + 4, 33, needle=4)
    # a lower east wing between the crag and the nave, lit
    tower_shell(c, 6, 9, -4, 1, PLAT + 1, 14, (PLAT + 1, 11, 14), win_faces=("south", "east"))
    cone(c, 7, -1, 2, 16, needle=1)
    for x in range(6, 10):
        for z in range(-4, 2):
            S(c, x, 15, z, TRIM)
    # turrets on the keep's corners rise (replacing the bartizans' caps): r1 shafts to different heights
    for (x, z, top) in ((KX0 - 1, KZ0 - 1, 33), (KX1 + 1, KZ0 - 1, 31), (KX0 - 1, KZ1 + 1, 30), (KX1 + 1, KZ1 + 1, 32)):
        for y in range(K_TOP, top):
            S(c, x, y, z, WALL)
        S(c, x, K_TOP + 4, z, WALL)
        S(c, x, top, z, ROOF)
        finial(c, x, top + 1, z, 2)
    # a second bridge: the east wing's roof to the crag turret, lanterns
    for x in range(10, 11):
        for z in (-5, -4):
            S(c, x, 14, z, WALL)
    # two slender turrets between the keep and the wings, at staggered heights - the cluster thickens
    round_tower(c, -6, -6, 1, PLAT + 1, 31, roof=True, needle=2)
    round_tower(c, 6, -7, 1, PLAT + 1, 34, roof=True, needle=3)
    # lanterns along the path round the plateau (posts on the plateau's rim)
    for (x, z) in ((-8, 4), (8, 4), (-9, -2), (9, -2), (-7, -10), (7, -10)):
        lamp(c, x, PLAT + 1, z, 1)


# =========================================================================================== L6: the crown
CROWN_DOME_RD = 4.7


def l6_crown(c):
    """The radial crown (his domed-cathedral reference, seen from above like the RTS camera): over the keep a lit drum,
    a DARK ribbed dome (deepslate tiles, pale quartz ribs - the light-wall / dark-roof contrast), and a lantern-spire
    rising from it in diminishing tiers to a needle and a gold finial, ringed by the keep's corner turrets. The air
    above the needle is left clear for the drawn glyph circles."""
    kc = (0, (KZ0 + KZ1) // 2)
    y0 = K_TOP + 2
    # the drum: r3 ring of ashlar with a lit lancet on every face, a lit room inside (solid floor and ceiling)
    for y in range(y0, y0 + 5):
        for x, z in disc(*kc, 4):
            edge = abs(dist(x, z, *kc) - 4) < 0.5
            S(c, x, y, z, WALL if edge else ("shroomlight" if (x, z) == kc and y == y0 + 4 else FLOOR_IN
                                             if y in (y0, y0 + 4) else "shroomlight"))   # a glowing core
    for (fx, fz, face) in ((0, 1, "south"), (-1, 0, "west"), (1, 0, "east"), (0, -1, "north")):
        lit_lancet(c, kc[0] + 4 * fx, kc[1] + 4 * fz, y0 + 1, 3, face)
    for (dx, dz) in ((3, 3), (3, -3), (-3, 3), (-3, -3)):
        lit_lancet(c, kc[0] + dx, kc[1] + dz, y0 + 1, 3, "south" if dz > 0 else "north")
    for x, z in ring(*kc, 5):
        ST(c, x, y0 + 4, z, toward(x, z, *kc), "top", BAND_ST)   # the drum's cornice
    # the dark dome: a height field over r3, deepslate tiles with a half slab where the curve passes halfway, pale
    # quartz ribs on the eight directions
    db = y0 + 5
    top = db
    for x, z in disc(*kc, 5):
        d = dist(x, z, *kc)
        if d > CROWN_DOME_RD:
            continue
        h = math.sqrt(CROWN_DOME_RD ** 2 - d ** 2)
        n, frac = int(h), h - int(h)
        dx, dz = x - kc[0], z - kc[1]
        rib = dx == 0 or dz == 0 or abs(dx) == abs(dz)
        for y in range(db, db + n):
            S(c, x, y, z, "smooth_quartz" if rib else DOME)
        if frac >= 0.5:
            SLAB(c, x, db + n, z, name="smooth_quartz_slab" if rib else DOME_SLAB)
        top = max(top, db + n)
    # the lantern-spire, diminishing: tier 1 (r2) lit arcade, a dark cap ring; tier 2 (r1) lit; a needle; gold finial
    y = top + 1
    for x, z in disc(*kc, 2):
        S(c, x, y - 1, z, TRIM)
    for k in range(4):
        for x, z in disc(*kc, 2):
            edge = abs(dist(x, z, *kc) - 2) < 0.5
            corner = edge and abs(x - kc[0]) == abs(z - kc[1])
            if edge:
                S(c, x, y + k, z, "quartz_pillar" if corner else "glass_pane", **({"axis": "y"} if corner else {}))
            else:
                S(c, x, y + k, z, "shroomlight" if (x, z) == kc else FLOOR_IN)
    y += 4
    for x, z in ring(*kc, 3):
        ST(c, x, y, z, toward(x, z, *kc), "top", DOME_ST)
    for x, z in disc(*kc, 2):
        S(c, x, y, z, DOME)
    for x, z in ring(*kc, 2):
        ST(c, x, y + 1, z, toward(x, z, *kc), "bottom", DOME_ST)
    for x, z in disc(*kc, 1):
        S(c, x, y + 1, z, DOME)
    y += 2
    for k in range(3):
        for x, z in disc(*kc, 1):
            edge = (x, z) != kc
            S(c, x, y + k, z, ("glass_pane" if (x == kc[0] or z == kc[1]) else "quartz_pillar") if edge else
              "shroomlight", **({"axis": "y"} if edge and not (x == kc[0] or z == kc[1]) else {}))
    y += 3
    for x, z in ring(*kc, 1):
        ST(c, x, y, z, toward(x, z, *kc), "bottom", DOME_ST)
    S(c, kc[0], y, kc[1], DOME)
    for k in range(1, 12):              # the needle: the tallest thing for twenty blocks round
        S(c, kc[0], y + k, kc[1], "deepslate_tile_wall")
    S(c, kc[0], y + 12, kc[1], "gold_block")
    S(c, kc[0], y + 13, kc[1], "deepslate_tile_wall")
    S(c, kc[0], y + 14, kc[1], "end_rod", facing="up")
    global CROWN_TIP
    CROWN_TIP = (kc[0], y + 14, kc[1])
    # pinnacles on every nave buttress step and a flèche on the nave ridge's keep end
    for z in range(NZ0 + 2, NZ1, 2):
        for x in (NX0 - 1, NX1 + 1):
            if G(c, x, 15, z)[0] == "air":
                pinnacle(c, x, 15, z)
    # more light: lanterns in the keep parapet's crenels
    for x in range(KX0 - 1, KX1 + 2):
        for z in (KZ0 - 1, KZ1 + 1):
            if G(c, x, K_TOP + 1, z)[0] == "air":
                lantern_on(c, x, K_TOP + 1, z)


CROWN_TIP = None


STEPS = (l1_keep, l2_nave, l3_front, l4_west, l5_east, l6_crown)


def level(n):
    """The capitol at level n (1..6) as a settled _SunRig."""
    assert 1 <= n <= LEVELS, n
    c = Cap(R, H)
    for step in STEPS[:n]:
        step(c)
    b = c.rig.b
    for p in [p for p, v in b.items() if v[0] == "air"]:
        del b[p]
    for (x, y, z), (name, props) in list(b.items()):
        if name in FLOWERS and b.get((x, y - 1, z), ("air",))[0] not in ("grass_block", "moss_block"):
            del b[(x, y, z)]
        if name == "vine":
            f = next(k for k in ("north", "south", "east", "west") if props.get(k) == "true")
            ox, oz = VEC[f]
            behind = b.get((x + ox, y, z + oz), ("air",))[0]
            if behind in ("air", "vine") or (x, y, z) in b and False:
                del b[(x, y, z)]
    c.rig.bury_grass()
    c.rig.settle()
    c.rig.check_support("white angel capitol L%d" % n)
    return c.rig


FORBIDDEN = ("lava", "sand", "gravel", "_concrete_powder", "anvil", "redstone", "repeater", "comparator",
             "observer", "piston", "lever", "_button", "_pressure_plate", "tnt", "scaffolding")
SPAWN = (0, PLAT + 1, 6)      # the open terrace before the gable: 7 wide, open to the sky, the stair 5 wide below


def check(rigs):
    prev = None
    counts = []
    sx = 2 * R + 1
    for n, rig in enumerate(rigs, 1):
        assert rig.size == (sx, H, sx), rig.size
        names = {p: v[0] for p, v in rig.b.items()}
        bad = sorted({nm for nm in names.values() if any(nm == f or nm.endswith(f) for f in FORBIDDEN)
                      or nm in ("fire", "soul_fire", "water")})
        assert not bad, "L%d forbidden: %s" % (n, bad)
        for (x, y, z), nm in names.items():
            if nm == "campfire":
                assert names.get((x, y - 1, z)) and names.get((x, y - 2, z)) and \
                    names.get((x, y - 2, z)) not in ("grass_block", "moss_block"), \
                    "L%d: a campfire at %s not on a pedestal" % (n, (x, y, z))
        if prev is not None:
            lost = [p for p, nm in prev.items() if p not in names and nm not in PLANTS]
            assert not lost, "L%d empties %d cells of L%d, e.g. %s" % (n, len(lost), n - 1,
                                                                     [(p, prev[p]) for p in lost[:6]])
        x, y, z = SPAWN
        y += YOFF
        cell = (x + R, y, z + R)
        assert (cell[0], y - 1, cell[2]) in names and (cell[0], y, cell[2]) not in names \
            and (cell[0], y + 1, cell[2]) not in names, "L%d: the spawn cell must be open on a floor" % n
        counts.append(len(names))
        prev = names
    return counts


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "/tmp"
    rigs = [level(n) for n in range(1, LEVELS + 1)]
    print("blocks per level:", check(rigs))
    for n, rig in enumerate(rigs, 1):
        path = os.path.join(out, "white_angel_capitol_l%d.nbt" % n)
        rig.structure().write(path)
        print("wrote", path)
