#!/usr/bin/env python3
"""A PART LIBRARY for the gothic capitol (and every gothic / castle building after it).

lovish, Oct 20 2026: the capitol is "not reaching the level of detail and sophistication this game needs"; "increase
block vocabulary, scale the buildings bigger if you have to, add more size". So the capitol is no longer drawn block by
block in one function: it is ASSEMBLED from parts, each a small articulated building element made once and made well -
a tracery window bay, a buttress with stacked pinnacles, a turret with a corbelled gallery, a jettied timber storey, a
needle spire with lucarnes, a curtain wall, a gatehouse, a crag face... - placed at an origin with a quarter-turn
rotation. His references (inspiration only, never traced): the gothic castle-cathedral on its hill, the cathedral
fronts thick with tracery, the grey castles with curtain walls and red cones, the tall slate needles over half-timbered
galleries, the timber-and-slate gables stacked up a keep, the arched stone bridge.

CONVENTIONS
  - A part is a Part: a dict of cells (x, y, z) -> (block id, props), in its own frame. Its FRONT faces SOUTH (+z),
    toward the RTS camera; y = 0 is its floor. Part.place(other, ox, oy, oz, rot) copies another part in, turned `rot`
    quarter turns clockwise seen from above (facings, axes and side flags are turned too).
  - Every block goes through a ROLE: the part asks for "wall", "trim_st", "roof"... and PALETTE maps the role to a block
    id. Macaw's Roofs / Windows / Fences and Chipped are becoming required dependencies: when they land, their ids go into
    the palette (a namespaced id like "mcwroofs:..." passes straight through to the .nbt) and every part picks them up.
    ROOFS holds the roof variants (red tile, dark slate, teal copper) the sheet compares.
  - Every part stays walkable or sealed: a space a unit can enter is at least 4 wide and 8 tall (the wide bodies are
    3.5 wide and ~7 tall), anything smaller is behind glass, bars or a closed door. Lit rooms sit behind the windows.
  - to_rig() turns a part into a settled gen_structures._SunRig (stair shapes and wall posts as the game makes them;
    nothing that would pop off), ready to write as an .nbt or to render with tools/structure_render.py.

Usage: python3 tools/gothic_parts.py OUT_DIR   -> one .nbt per part in the catalogue (the parts sheet renders them)
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs   # noqa: E402

# ============================================================================================ the palette
PALETTE = {
    # pale stone in calm zones (the White Angel), grey-blue trims, darker footings
    "wall": "quartz_bricks", "wall_alt": "calcite", "trim": "smooth_quartz", "trim_st": "smooth_quartz_stairs",
    "trim_slab": "smooth_quartz_slab", "carve": "chiseled_quartz_block", "pillar": "quartz_pillar",
    "quartz_st": "quartz_stairs", "rail": "diorite_wall",
    "band": "polished_andesite", "band_st": "polished_andesite_stairs", "band_slab": "polished_andesite_slab",
    "base": "stone_bricks", "base_st": "stone_brick_stairs", "base_slab": "stone_brick_slab",
    "base_wall": "stone_brick_wall", "base_alt": "mossy_stone_bricks",
    # timber and plaster (the jettied galleries and gables)
    "timber": "stripped_dark_oak_log", "timber_beam": "dark_oak_log", "timber_st": "dark_oak_stairs",
    "timber_slab": "dark_oak_slab", "timber_fence": "dark_oak_fence", "plaster": "white_terracotta",
    "shutter": "spruce_trapdoor", "door": "dark_oak_door",
    # glass, tracery, light
    "glass": "glass_pane", "glass_rose_a": "blue_stained_glass", "glass_rose_b": "red_stained_glass",
    "glass_rose_c": "yellow_stained_glass", "slit": "black_stained_glass_pane", "bars": "iron_bars",
    "light": "shroomlight", "lantern": "lantern", "candle": "white_candle", "floor": "dark_oak_planks",
    "carpet": "red_carpet", "shelf": "bookshelf", "spike": "end_rod", "gold": "gold_block", "chain": "chain",
    "banner": "blue_banner", "banner_top": "blue_banner", "glass_lit": "glass_pane",
    # tower windows: a warm pane for a lit room (a shroomlight behind it), a grey one for a dark room
    "win_lit": "yellow_stained_glass_pane", "win_dark": "gray_stained_glass_pane",
    # rock and green (the crag)
    "rock1": "stone", "rock2": "andesite", "rock3": "tuff", "rock4": "calcite", "rock5": "cobblestone",
    "grass": "grass_block", "moss": "moss_block", "leaves": "azalea_leaves", "leaves2": "flowering_azalea_leaves",
    "log": "oak_log", "vine": "vine", "roots": "hanging_roots", "path": "polished_andesite",
}
ROOFS = {
    "red": {"roof": "bricks", "roof_st": "brick_stairs", "roof_slab": "brick_slab", "roof_wall": "brick_wall"},
    "slate": {"roof": "deepslate_tiles", "roof_st": "deepslate_tile_stairs", "roof_slab": "deepslate_tile_slab",
              "roof_wall": "deepslate_tile_wall"},
    "teal": {"roof": "waxed_oxidized_cut_copper", "roof_st": "waxed_oxidized_cut_copper_stairs",
             "roof_slab": "waxed_oxidized_cut_copper_slab", "roof_wall": "deepslate_tile_wall"},
}
# the needle spires are slate in every variant (his tall dark needles); the cones take the chosen roof
NEEDLE = {"needle": "deepslate_tiles", "needle_st": "deepslate_tile_stairs", "needle_slab": "deepslate_tile_slab",
          "needle_wall": "deepslate_tile_wall"}


STONES = {
    # the White Angel's pale stone: quartz bricks, calcite zones, smooth quartz quoins, stone-brick plinth
    "pale": {"quoin": "smooth_quartz", "wall_moss": "mossy_stone_bricks"},
    # the grey stone of his castle references
    "grey": {"wall": "stone_bricks", "wall_alt": "cracked_stone_bricks", "quoin": "polished_andesite",
             "trim": "polished_andesite", "trim_st": "polished_andesite_stairs", "trim_slab": "polished_andesite_slab",
             "carve": "chiseled_stone_bricks", "pillar": "polished_basalt", "rail": "andesite_wall",
             "band": "stone_bricks", "band_st": "stone_brick_stairs", "band_slab": "stone_brick_slab",
             "base": "cobblestone", "base_st": "cobblestone_stairs", "base_slab": "cobblestone_slab",
             "base_wall": "cobblestone_wall", "base_alt": "mossy_cobblestone", "wall_moss": "mossy_stone_bricks",
             "path": "stone_bricks"},
}


def palette(roof="red", stone="pale", **over):
    p = dict(PALETTE)
    p.update(STONES[stone])
    p.update(ROOFS[roof])
    p.update(NEEDLE)
    p.update(over)
    return p


DIRS = ("north", "east", "south", "west")          # clockwise seen from above
VEC = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
LEAF = dict(persistent="true", distance="1", waterlogged="false")
LANTERN = dict(hanging="false", waterlogged="false")


def cw(f, rot):
    return DIRS[(DIRS.index(f) + rot) % 4] if f in DIRS else f


def hsh(x, z, s=0):
    return ((x + 31 + s * 7) * 73856093 ^ (z + 17 - s * 3) * 19349663) % 101


# ============================================================================================ the part
class Part:
    def __init__(self, pal=None):
        self.b = {}
        self.pal = pal or palette()

    # ---- writing
    def name(self, role):
        return self.pal.get(role, role)

    def set(self, x, y, z, role, **p):
        self.b[(x, y, z)] = (self.name(role), p)

    def clear(self, x, y, z):
        self.b.pop((x, y, z), None)

    def get(self, x, y, z):
        return self.b.get((x, y, z), ("air", {}))[0]

    def stair(self, x, y, z, facing, half="bottom", role="trim_st"):
        self.set(x, y, z, role, facing=facing, half=half, shape="straight", waterlogged="false")

    def slab(self, x, y, z, typ="bottom", role="trim_slab"):
        self.set(x, y, z, role, type=typ, waterlogged="false")

    def fill(self, x0, x1, y0, y1, z0, z1, role, **p):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, role, **p)

    def lantern(self, x, y, z):
        self.set(x, y, z, "lantern", **LANTERN)

    # ---- placing parts in parts
    def rotated(self, rot):
        rot %= 4
        out = Part(self.pal)
        for (x, y, z), (n, p) in self.b.items():
            for _ in range(rot):
                x, z = -z, x                       # a quarter turn clockwise: north -> east
            q = dict(p)
            if "facing" in q:
                q["facing"] = cw(q["facing"], rot)
            if "axis" in q and rot % 2 == 1 and q["axis"] in ("x", "z"):
                q["axis"] = "z" if q["axis"] == "x" else "x"
            sides = {d: q.pop(d) for d in DIRS if d in q}
            for d, v in sides.items():
                q[cw(d, rot)] = v
            out.b[(x, y, z)] = (n, q)
        return out

    def place(self, other, ox=0, oy=0, oz=0, rot=0):
        for (x, y, z), v in other.rotated(rot).b.items():
            self.b[(x + ox, y + oy, z + oz)] = v
        return self

    # ---- output
    def bbox(self):
        xs = [p[0] for p in self.b]
        ys = [p[1] for p in self.b]
        zs = [p[2] for p in self.b]
        return min(xs), max(xs), min(ys), max(ys), min(zs), max(zs)

    def to_rig(self, label="part"):
        x0, x1, y0, y1, z0, z1 = self.bbox()
        rig = gs._SunRig(x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1)
        for (x, y, z), (n, p) in self.b.items():
            if n not in ("air", "cave_air"):
                rig.set(x - x0, y - y0, z - z0, n, **p)
        rig.bury_grass()
        rig.settle()
        rig.check_support(label)
        return rig


# ============================================================================================ geometry helpers
def ring(r, cx=0, cz=0):
    return [(x, z) for x in range(cx - r - 1, cx + r + 2) for z in range(cz - r - 1, cz + r + 2)
            if abs(math.hypot(x - cx, z - cz) - r) < 0.5]


def disc(r, cx=0, cz=0):
    return [(x, z) for x in range(cx - r - 1, cx + r + 2) for z in range(cz - r - 1, cz + r + 2)
            if math.hypot(x - cx, z - cz) < r + 0.5]


def octagon(r, cx=0, cz=0):
    """The cells of a regular-ish octagon's outline of 'radius' r (corners cut at r // 2)."""
    cut = max(1, (r + 1) // 2)
    out = []
    for x in range(-r, r + 1):
        for z in range(-r, r + 1):
            inside = abs(x) <= r and abs(z) <= r and abs(x) + abs(z) <= r + cut
            edge = inside and (abs(x) == r or abs(z) == r or abs(x) + abs(z) == r + cut)
            if edge:
                out.append((cx + x, cz + z))
    return out


def octagon_fill(r, cx=0, cz=0):
    cut = max(1, (r + 1) // 2)
    return [(cx + x, cz + z) for x in range(-r, r + 1) for z in range(-r, r + 1) if abs(x) + abs(z) <= r + cut]


def toward(x, z, cx=0, cz=0):
    dx, dz = cx - x, cz - z
    if abs(dx) >= abs(dz):
        return "east" if dx > 0 else "west"
    return "south" if dz > 0 else "north"


def lit_room(p, x0, x1, z0, z1, yf, yc):
    """A lit, sealed room behind windows: plank floor, a red runner, shelves, lanterns on tables, a glowing ceiling."""
    p.fill(x0, x1, yf, yf, z0, z1, "floor")
    p.fill(x0, x1, yc, yc, z0, z1, "floor")
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if (x + z) % 2 == 0:
                p.set(x, yc, z, "light")
    xm = (x0 + x1) // 2
    for z in range(z0, z1 + 1):
        p.set(xm, yf + 1, z, "carpet")
    if x1 - x0 >= 2:
        p.set(x0, yf + 1, z0, "floor")
        p.lantern(x0, yf + 2, z0)
        p.set(x1, yf + 1, z0, "shelf")
        p.set(x1, yf + 2, z0, "shelf")


# ============================================================================================ (a) tracery window bay
def window_bay(w=3, h=7, depth=2, lit=True, pal=None):
    """A tall tracery window in a wall panel, the panel (w + 4) wide: a pointed arch w wide and h tall, mullions every
    other cell, a quatrefoil of bars in the head, a moulded sill, a hood moulding with label stops, jamb shafts one
    cell proud, and a lit room `depth` deep behind. The wall face is z = 0, the outside +z."""
    p = Part(pal)
    W = w + 4
    x0 = -(W // 2)
    head = (w + 1) // 2                      # the arch's rise above the springing
    top = h + head + 2
    for x in range(x0, x0 + W):
        for y in range(0, top + 1):
            p.set(x, y, 0, "wall")
    xl, xr = -(w // 2), w // 2
    # the opening: glass with mullions, then the pointed head stepping in
    for y in range(1, h + 1):
        for x in range(xl, xr + 1):
            p.set(x, y, 0, "rail" if (w >= 3 and (x - xl) % 2 == 1) else "glass")
    for k in range(1, head + 1):
        for x in range(xl + k, xr - k + 1):
            p.set(x, h + k, 0, "glass")
        if xl + k - 1 <= xr - k + 1:
            p.stair(xl + k - 1, h + k, 0, "east", "top")       # the arch's soffit steps in
            p.stair(xr - k + 1, h + k, 0, "west", "top")
    # the quatrefoil in the head: a ring of bars round a carved boss
    yq = h + max(1, head - 1)
    if w >= 3:
        for (dx, dy) in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            if xl <= dx <= xr:
                p.set(dx, yq + dy, 0, "bars")
        p.set(0, yq, 0, "carve")
    p.set(0, h + head + 1, 0, "carve")                         # the keystone
    # sill, jamb shafts and the hood moulding, all one cell proud
    for x in range(xl - 1, xr + 2):
        p.slab(x, 0, 1, "top", "band_slab")
    for x in (xl - 1, xr + 1):
        for y in range(1, h + 1):
            p.set(x, y, 1, "pillar", axis="y")
        p.set(x, h + 1, 1, "carve")
    for k in range(0, head + 1):
        p.stair(xl - 1 + k, h + 1 + k, 1, "east" if k < head else "east", "bottom", "band_st")
        p.stair(xr + 1 - k, h + 1 + k, 1, "west", "bottom", "band_st")
    p.set(0, h + head + 2, 1, "band")
    # the room behind
    if lit:
        for z in range(-depth, 0):
            for x in range(x0, x0 + W):
                p.set(x, 0, z, "floor")
                p.set(x, top, z, "wall")
            for y in range(1, top):
                p.set(x0, y, z, "wall")
                p.set(x0 + W - 1, y, z, "wall")
        for x in range(x0, x0 + W):
            for y in range(0, top + 1):
                p.set(x, y, -depth - 1, "wall")
        lit_room(p, x0 + 1, x0 + W - 2, -depth, -1, 0, top - 1)
    return p


# ============================================================================================ (b) buttress
def buttress(h=18, depth=3, flyer=True, pal=None):
    """A buttress standing out from a wall at z = 0: three set-offs climbing back (weathered with stairs), a carved
    niche, a stacked pinnacle on every set-off (bars and walls, an end rod), and a flying arch from the outer pier's top
    back over to the wall. The wall itself is not part of it."""
    p = Part(pal)
    stages = [(d, h - 4 * (depth - d)) for d in range(1, depth + 1)]
    for d, top in stages:
        z = d
        for y in range(0, top + 1):
            for x in (-1, 0, 1) if d == 1 else (0,):
                p.set(x, y, z, "base" if y < 2 else "wall")
        p.stair(0, top + 1, z, "north", "bottom", "trim_st")           # the weathering
        if d > 1:
            pin = top + 2 if d == depth else top + 1
            _pinnacle(p, 0, pin if d == depth else top + 2, z, tall=(d == depth))
    zo = depth
    p.set(0, 5, zo, "carve")
    p.set(0, 6, zo + 1, "lantern", **LANTERN) if False else None
    for y in range(1, 3):
        p.set(0, y, zo + 1, "base_st" if False else "base")
    p.stair(0, 3, zo + 1, "north", "bottom", "base_st")
    if flyer:
        top_out = stages[-1][1]
        ya = top_out - 1
        for k in range(0, depth):
            z = depth - 1 - k
            if z <= 0:
                break
            p.stair(0, ya + k, z, "north", "bottom", "trim_st")
            p.stair(0, ya + k - 1, z, "south", "top", "trim_st")
    return p


def _pinnacle(p, x, y, z, tall=False):
    """An openwork pinnacle: carved base, bars, a wall shaft, bars, a spike (taller: two shafts and a gold knop)."""
    p.set(x, y, z, "carve")
    p.set(x, y + 1, z, "bars")
    p.set(x, y + 2, z, "rail")
    k = 3
    if tall:
        p.set(x, y + 3, z, "bars")
        p.set(x, y + 4, z, "rail")
        k = 5
    p.set(x, y + k, z, "bars")
    p.set(x, y + k + 1, z, "spike", facing="up")


# ============================================================================================ cones and needles
def cone(p, cx, cz, r, y0, courses=4, role="roof", finial=True, lucarnes=False):
    """A steep round cone: `courses` courses per ring (full blocks, then stairs climbing in on the last), a finial. With
    lucarnes, little gabled dormers with lit windows stand out on the four sides two rings up."""
    y = y0
    rst = role + "_st"
    for rr in range(r, 0, -1):
        for k in range(courses - 1):
            for x, z in disc(rr, cx, cz):
                p.set(x, y + k, z, role)
        for x, z in disc(rr - 1, cx, cz):
            p.set(x, y + courses - 1, z, role)
        for x, z in ring(rr, cx, cz):
            p.stair(x, y + courses - 1, z, toward(x, z, cx, cz), "bottom", rst)
        if lucarnes and rr == r - 1 and r >= 3:
            for f, (vx, vz) in VEC.items():
                lx, lz = cx + vx * (rr + 1), cz + vz * (rr + 1)
                p.set(lx, y, lz, "trim")
                p.set(lx, y + 1, lz, "glass")
                p.set(lx - vx * 1, y + 1, lz - vz * 1, "light")
                p.stair(lx, y + 2, lz, OPP[f], "bottom", rst)
                for s in (-1, 1):
                    sx, sz = lx + s * vz, lz + s * vx
                    p.stair(sx, y + 1, sz, cw(f, 1 if s > 0 else 3) if False else (
                        "east" if (vz != 0 and s < 0) else "west" if vz != 0 else
                        "south" if s < 0 else "north"), "bottom", rst)
        y += courses
    p.set(cx, y, cz, role)
    top = y
    if finial:
        p.set(cx, y + 1, cz, role + "_wall")
        p.set(cx, y + 2, cz, role + "_wall")
        p.set(cx, y + 3, cz, "gold")
        p.set(cx, y + 4, cz, "spike", facing="up")
        top = y + 4
    return top


def needle(p, cx, cz, r, y0, h, lucarne_at=None):
    """A tall slate needle: an octagonal broach from radius r, tapering every h // (r + 1) courses, little lucarnes
    (lit gabled windows) partway up, a long finial."""
    steps = r + 1
    per = max(3, h // steps)
    y = y0
    for i, rr in enumerate(range(r, -1, -1)):
        cells = octagon_fill(rr, cx, cz) if rr > 0 else [(cx, cz)]
        for k in range(per):
            for (x, z) in cells:
                p.set(x, y + k, z, "needle")
        if rr > 0:
            for (x, z) in octagon(rr, cx, cz):
                p.stair(x, y + per - 1, z, toward(x, z, cx, cz), "bottom", "needle_st")
        if lucarne_at is not None and i == lucarne_at and rr >= 2:
            for f, (vx, vz) in VEC.items():
                lx, lz = cx + vx * (rr + 1), cz + vz * (rr + 1)
                p.set(lx, y, lz, "needle")
                p.set(lx, y + 1, lz, "glass")
                p.set(lx - vx, y + 1, lz - vz, "light")
                p.stair(lx, y + 2, lz, OPP[f], "bottom", "needle_st")
        y += per
    for k in range(5):
        p.set(cx, y + k, cz, "needle_wall")
    p.set(cx, y + 5, cz, "gold")
    p.set(cx, y + 6, cz, "needle_wall")
    p.set(cx, y + 7, cz, "spike", facing="up")
    return y + 7


# ============================================================================================ (e) crocketed gable + pinnacle row
def crocketed_gable(w=11, pal=None):
    """A gable wall w wide (odd) with a crocketed rake (stairs climbing either edge, little leaves of slab on them), a
    carved finial with a spike at the apex, a blind arcade (trefoil-headed panels) across its foot, and a row of
    pinnacles along its base on the eaves line."""
    p = Part(pal)
    half = w // 2
    for k in range(half + 1):
        y = 2 * k
        for x in range(-half + k, half - k + 1):
            p.set(x, y, 0, "wall")
            p.set(x, y + 1, 0, "wall")
        if k <= half:
            p.stair(-half + k - 1, y + 1, 0, "east", "bottom", "trim_st")
            p.stair(half - k + 1, y + 1, 0, "west", "bottom", "trim_st")
            if k % 2 == 1:
                p.set(-half + k - 1, y + 2, 0, "rail")                      # crockets
                p.set(half - k + 1, y + 2, 0, "rail")
    apex = 2 * half + 2
    p.set(0, apex, 0, "carve")
    p.set(0, apex + 1, 0, "bars")
    p.set(0, apex + 2, 0, "spike", facing="up")
    for x in range(-half + 1, half, 2):                                     # the blind arcade, one cell proud
        p.set(x, 1, 1, "pillar", axis="y")
        p.set(x, 2, 1, "pillar", axis="y")
        p.stair(x, 3, 1, "west", "top")
        if x + 1 <= half - 1:
            p.stair(x + 1, 3, 1, "east", "top")
    p.set(0, 2 * half - 2, 1, "carve")                                       # a carved boss in the gable
    for x in range(-half - 1, half + 2, 2):
        _pinnacle(p, x, 0, 2, tall=(x in (-half - 1, half + 1)))
    for x in range(-half - 1, half + 2):
        p.set(x, -1, 2, "trim") if False else None
    return p


# ============================================================================================ (f) rose window wall
def rose_wall(r=5, pal=None):
    """A wall with a great rose: a ring of carved tracery, twelve spokes, an inner quatrefoil ring, coloured glass, a
    gold boss; a hood moulding of stairs one cell proud round the upper half, label stops; lit from a room behind."""
    p = Part(pal)
    W = 2 * r + 5
    x0 = -(W // 2)
    cy = r + 2
    for x in range(x0, x0 + W):
        for y in range(0, 2 * r + 5):
            p.set(x, y, 0, "wall")
    for x in range(-r, r + 1):
        for y in range(cy - r, cy + r + 1):
            dx, dy = x, y - cy
            d = math.hypot(dx, dy)
            if d > r + 0.5:
                continue
            a = math.atan2(dy, dx)
            spoke = min(abs(((a / (math.pi / 6)) % 1) - 0.5) for _ in (0,)) > 0.38 and d > 1.5
            if abs(d - r) < 0.5 or abs(d - r / 2) < 0.45 or d < 0.8 or spoke:
                p.set(x, y, 0, "carve")
            else:
                p.set(x, y, 0, ("glass_rose_a", "glass_rose_b", "glass_rose_c")[(int(d) + int((a + 4) * 2)) % 3])
    p.set(0, cy, 0, "gold")
    for x in range(-r - 1, r + 2):
        for y in range(cy, cy + r + 2):
            if abs(math.hypot(x, y - cy) - (r + 1)) < 0.5:
                p.stair(x, y, 1, "north", "top", "band_st")
    for x in (-r - 1, r + 1):
        p.set(x, cy - 1, 1, "carve")
    for z in (-1, -2):
        for x in range(x0, x0 + W):
            p.set(x, 0, z, "floor")
    for x in range(-r, r + 1, 2):
        p.set(x, cy - r, -1, "light")
        p.set(x, cy + r, -1, "light")
    for x in range(x0, x0 + W):
        for y in range(0, 2 * r + 5):
            p.set(x, y, -3, "wall")
    return p


# ============================================================================================ (g) portal
def portal(w=5, h=8, orders=3, pal=None):
    """A great portal: an opening w wide and h tall (a wide body walks through: w >= 4, h >= 8), nested pointed arches
    stepping out `orders` cells (each order a column shaft and an arch of stairs), a tympanum with a carved boss over
    the doors, a pair of closed doors (the hall is sealed until the game opens it), a crocketed gable over the whole."""
    p = Part(pal)
    half = w // 2
    for o in range(orders + 1):
        z = o
        hw = half + o
        for x in (-hw - 1, hw + 1):
            for y in range(0, h + 1):
                p.set(x, y, z, "pillar" if o > 0 else "wall", **({"axis": "y"} if o > 0 else {}))
            p.set(x, 0, z, "base")
            p.set(x, h + 1, z, "carve")
        rise = hw + 1
        for k in range(rise + 1):
            xl, xr = -hw - 1 + k, hw + 1 - k
            y = h + 1 + k
            if xl < xr:
                p.stair(xl, y, z, "east", "bottom", "trim_st")
                p.stair(xr, y, z, "west", "bottom", "trim_st")
                for x in range(xl + 1, xr):
                    if o == 0:
                        p.set(x, y, z, "wall")
            else:
                p.set(xl, y, z, "carve")
    # the tympanum (the doors' arch filled), its boss, the doors below it - closed, sealed until the game opens them
    for x in range(-half, half + 1):
        for y in range(h - 1, h + 2):
            p.set(x, y, 0, "wall")
    p.set(0, h, 0, "carve")
    p.set(0, h + 1, 0, "gold")
    for x in range(-half, half + 1):
        for y in range(1, h - 1):
            p.set(x, y, -1, "base") if False else None
    for x in range(-half, half + 1):
        for y in range(1, h - 1):
            p.set(x, y, 0, "bars" if (x + y) % 3 == 0 else "timber" if abs(x) == half else "floor")
    for x in (-half - orders - 2, half + orders + 2):
        p.set(x, 1, orders + 1, "rail")
        p.set(x, 2, orders + 1, "lantern", **LANTERN)
    return p


# ============================================================================================ (h) machicolated parapet + bartizan
def parapet(length=13, pal=None):
    """The crown of a wall: corbels (two courses of upside-down stairs) carrying a machicolated gallery a cell proud,
    brackets of wall between them (the drop holes), a crenellated parapet (merlons with arrow slits, embrasures), and a
    bartizan at each end - a little round turret hung on corbels with a cone and finial."""
    p = Part(pal)
    L = length
    for x in range(0, L):
        for y in range(0, 4):
            p.set(x, y, 0, "wall")
        p.set(x, 1, 1, "band_st" if False else "air") if False else None
        p.stair(x, 2, 1, "north", "top", "band_st")
        if x % 2 == 0:
            p.set(x, 1, 1, "base_wall")
        p.stair(x, 3, 2, "north", "top", "band_st")
        p.set(x, 3, 1, "trim")
        p.set(x, 4, 1, "trim")
        p.set(x, 4, 0, "trim")
        merlon = (x // 2) % 2 == 0
        for y in (5, 6):
            p.set(x, y, 2, "wall" if merlon else "air") if merlon else None
        if merlon:
            p.set(x, 5, 2, "wall")
            p.set(x, 6, 2, "wall")
            p.slab(x, 7, 2, "bottom", "band_slab")
            if x % 4 == 1:
                p.set(x, 5, 2, "slit")
        else:
            p.slab(x, 5, 2, "bottom", "band_slab")
    for x0 in (-2, L + 1):
        for x, z in ring(1, x0, 2):
            for y in range(2, 9):
                p.set(x, y, z, "wall_alt")
            p.stair(x, 1, z, toward(x, z, x0, 2), "top", "band_st")
        for x, z in ring(2, x0, 2):
            p.stair(x, 9, z, toward(x, z, x0, 2), "top", "band_st")
        p.set(x0, 2, 2, "trim")
        p.set(x0, 5, 3, "glass")
        p.set(x0, 5, 2, "light")
        cone(p, x0, 2, 2, 10, courses=3)
    return p


# ============================================================================================ (i) crag face
def crag(w=24, d=14, h=26, seed=1, pal=None):
    """A piece of mountain: stacked cliff faces in strata (stone, andesite, tuff, a calcite band), ledges every few
    courses whose edges wander, overhanging lips with roots hanging under them, ivy on the camera faces, moss and grass
    on the ledges, a tree or two on them. The cliffs face south and west; a part to set at a castle's foot."""
    p = Part(pal)
    tiers = []
    y = 0
    k = 0
    while y < h:
        th = 4 + (hsh(k, seed) % 3)
        tiers.append((y, min(h, y + th), k))
        y += th
        k += 1
    for x in range(0, w):
        for z in range(0, d):
            # the column's height: the tier whose edge (wandering with x) still covers z
            top = 0
            for (y0, y1, k) in tiers:
                edge = d - 1 - k * 2 - int(1.6 * math.sin(x * 0.45 + k * 1.3 + seed)) - (hsh(x // 3, k, seed) % 2)
                if z <= edge:
                    top = y1
            for y in range(0, top):
                band = (y + (hsh(x // 4, z // 4, seed) % 2)) // 3
                p.set(x, y, z, ("rock1", "rock2", "rock3", "rock1", "rock4")[band % 5])
            if top > 0:
                p.set(x, top, z, "moss" if hsh(x, z, seed) % 3 == 0 else "grass", **(
                    {} if hsh(x, z, seed) % 3 == 0 else {"snowy": "false"}))
    tops = {}
    for (x, y, z), (n, _) in p.b.items():
        tops[(x, z)] = max(tops.get((x, z), -1), y)
    # overhangs: a lip one out over the cliff below where the tier above juts, roots hanging under
    for (x, z), t in list(tops.items()):
        if (x, z + 1) in tops and tops[(x, z + 1)] < t - 3 and hsh(x, z, seed + 2) % 3 == 0:
            p.set(x, t - 1, z + 1, "rock2")
            p.set(x, t - 2, z + 1, "roots", waterlogged="false")
    # ivy on south and west faces, trees on ledges
    trees = []
    for (x, z), t in tops.items():
        for (nx, nz, face) in ((x, z + 1, "north"), (x - 1, z, "east")):
            nt = tops.get((nx, nz), -1)
            for y in range(nt + 1, t):
                if hsh(x * 3 + y, z, seed) % 5 == 0 and p.get(nx, y, nz) == "air":
                    p.set(nx, y, nz, "vine", **{face: "true"})
        if 4 < t < h - 2 and hsh(x * 7, z * 5, seed) % 29 == 0 and \
                not any(abs(x - a) <= 3 and abs(z - b) <= 3 for a, b in trees):
            trees.append((x, z))
            for y in range(t + 1, t + 5):
                p.set(x, y, z, "log", axis="y")
            for (lx, ly, lz) in ((0, 5, 0), (1, 4, 0), (-1, 4, 0), (0, 4, 1), (0, 4, -1), (1, 5, 0), (-1, 5, 0),
                                 (0, 5, 1), (0, 5, -1), (0, 6, 0), (1, 4, 1), (-1, 4, -1)):
                if p.get(x + lx, t + ly, z + lz) == "air":
                    p.set(x + lx, t + ly, z + lz, "leaves2" if (lx + lz) % 2 else "leaves", **LEAF)
    return p


# ============================================================================================ (j) curtain wall
def curtain_wall(length=16, h=10, pal=None):
    """A curtain wall segment along x: three thick, a battered plinth, arrow slits, corbels carrying a crenellated
    wall-walk (the walk four wide on top, so a wide body could stand there - reached only from towers), merlons with
    slits and capping slabs."""
    p = Part(pal)
    for x in range(0, length):
        for z in (0, 1, 2):
            for y in range(0, h + 1):
                p.set(x, y, z, "base" if y < 2 else "wall")
        p.stair(x, 1, 3, "north", "bottom", "base_st")
        p.stair(x, 0, 3, "north", "bottom", "base_st") if False else None
        p.set(x, 0, 3, "base")
        if x % 4 == 2:
            for y in (4, 5):
                p.set(x, y, 2, "slit")
        p.stair(x, h, 3, "north", "top", "band_st")
        if x % 2 == 0:
            p.set(x, h - 1, 3, "base_wall")
        for z in (0, 1, 2, 3):
            p.set(x, h + 1, z, "path")
        for z in (-1,):
            p.set(x, h + 1, z, "wall")
            p.set(x, h + 2, z, "rail")
        merlon = (x // 2) % 2 == 0
        if merlon:
            p.set(x, h + 2, 3, "wall")
            p.set(x, h + 3, 3, "wall")
            p.slab(x, h + 4, 3, "bottom", "band_slab")
        else:
            p.slab(x, h + 2, 3, "bottom", "band_slab")
    for x in range(1, length, 6):
        p.set(x, h + 2, 1, "rail")
        p.set(x, h + 3, 1, "lantern", **LANTERN)
    return p


# ============================================================================================ (l) gatehouse
def gatehouse(pal=None):
    """Two round towers flanking a gate passage 5 wide and 9 tall (a wide body walks through and turns in it), a vault
    of stone over the passage, a raised portcullis (bars in the slot above the arch), machicolations over the gate, a
    crenellated top, cones on the towers, banners, lanterns at the arch."""
    from gothic_towers import wall_tower
    p = Part(pal)
    t = wall_tower(4, 18, pal)
    p.place(t, -8, 0, 0)
    p.place(t, 8, 0, 0)
    for x in range(-4, 5):
        for z in range(-3, 4):
            p.set(x, 0, z, "path")
        for z in (-3, 3):
            for y in range(0, 16):
                if abs(x) <= 2 and y <= 9:
                    continue
                p.set(x, y, z, "base" if y < 2 else "wall")
        for z in range(-3, 4):
            p.set(x, 13, z, "wall")
            p.set(x, 14, z, "wall")
        for y in range(0, 14):
            for z in range(-3, 4):
                if abs(x) >= 3:
                    p.set(x, y, z, "wall")
    for z in (-3, 3):
        for k in range(3):
            p.stair(-2 + k, 10 + k, z, "east", "top", "trim_st") if k < 2 else None
            p.stair(2 - k, 10 + k, z, "west", "top", "trim_st") if k < 2 else None
        p.set(0, 12, z, "carve")
        p.set(0, 11, z, "wall")
        p.set(-1, 11, z, "wall")
        p.set(1, 11, z, "wall")
    for x in (-2, -1, 0, 1, 2):
        p.set(x, 12, 1, "bars")                       # the raised portcullis in its slot
    for x in range(-5, 6):
        p.stair(x, 15, 4, "north", "top", "band_st")
        if x % 2 == 0:
            p.set(x, 14, 4, "base_wall")
        p.set(x, 16, 4, "trim")
        for z in range(-3, 5):
            p.set(x, 16, z, "path")
        if (x // 2) % 2 == 0:
            p.set(x, 17, 4, "wall")
            p.slab(x, 18, 4, "bottom", "band_slab")
        else:
            p.slab(x, 17, 4, "bottom", "band_slab")
    for x in (-3, 3):
        p.set(x, 4, 4, "rail")
        p.set(x, 5, 4, "lantern", **LANTERN)
    p.set(0, 17, 1, "banner", rotation="0")
    return p


# ============================================================================================ (m) hipped roof with dormers
def hipped_roof(w=11, l=15, pal=None):
    """A hipped tile roof over a w x l wing (on its wall plate at y = 0): steep, two courses per step in, hips all
    round, a crested ridge with finials at its ends, gabled dormers with lit windows on the long sides, a chimney."""
    p = Part(pal)
    hw, hl = w // 2, l // 2
    k = 0
    y = 0
    while hw - k >= 0 and hl - k >= 0:
        x0, x1, z0, z1 = -hw - 1 + k, hw + 1 - k, -hl - 1 + k, hl + 1 - k
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                edge = x in (x0, x1) or z in (z0, z1)
                p.set(x, y, z, "roof")
                if not edge or x0 == x1:
                    p.set(x, y + 1, z, "roof")
                else:
                    f = "east" if x == x0 else "west" if x == x1 else "south" if z == z0 else "north"
                    p.stair(x, y + 1, z, f, "bottom", "roof_st")
        k += 1
        y += 2
    ridge_y = y - 1
    for z in range(-hl + hw, hl - hw + 1):
        p.set(0, ridge_y + 1, z, "roof_wall")
    for z in (-hl + hw, hl - hw):
        p.set(0, ridge_y + 2, z, "gold")
        p.set(0, ridge_y + 3, z, "spike", facing="up")
    # dormers on both long sides: a little gabled front with a lit window, two along each side
    for side in (-1, 1):
        for zc in (-hl // 2, hl // 2):
            x = side * (hw - 1)
            fx = side
            p.set(x + fx, 2, zc, "plaster")
            p.set(x + fx, 3, zc, "glass")
            p.set(x, 3, zc, "light")
            for dz in (-1, 1):
                p.set(x + fx, 2, zc + dz, "timber", axis="y")
                p.set(x + fx, 3, zc + dz, "timber", axis="y")
                p.stair(x + fx, 4, zc + dz, "south" if dz < 0 else "north", "bottom", "roof_st")
            p.set(x + fx, 4, zc, "timber", axis="x")
            p.set(x + fx, 5, zc, "roof")
            for dz in (-1, 0, 1):
                p.set(x, 4, zc + dz, "roof")
    for y in range(2, ridge_y + 3):                     # the chimney
        p.set(hw - 2, y, -hl + 2, "base")
    p.set(hw - 2, ridge_y + 3, -hl + 2, "base_wall")
    return p


# ============================================================================================ (p) timber gable / dormer cluster
def timber_gables(pal=None):
    """A cluster of steep half-timbered gables stacked like his slate-and-timber keep: a stone ground storey, jettied
    timber storeys over it, a big front gable with a smaller one beside and a dormer between, crossed braces, small lit
    windows, steep roofs (the chosen roof), finials."""
    p = Part(pal)

    def gable_house(ox, oz, w, d, h0, storeys, roof_up=True):
        hw = w // 2
        for x in range(ox - hw, ox + hw + 1):
            for z in range(oz - d, oz + 1):
                for y in range(0, h0):
                    edge = x in (ox - hw, ox + hw) or z in (oz - d, oz)
                    if edge:
                        p.set(x, y, z, "base")
        y = h0
        jut = 0
        for s in range(storeys):
            jut = s + 1
            zf = oz + jut
            for x in range(ox - hw - jut, ox + hw + jut + 1):
                p.stair(x, y, zf, "north", "top", "timber_st")
            for x in range(ox - hw - jut, ox + hw + jut + 1):
                for z in range(oz - d, zf + 1):
                    p.set(x, y + 1 if False else y, z, "floor") if z < zf else None
            for yy in range(y + 1, y + 5):
                for x in range(ox - hw - jut, ox + hw + jut + 1):
                    for z in (zf,):
                        post = (x - (ox - hw - jut)) % 3 == 0
                        rail = yy in (y + 1, y + 4)
                        if post or rail:
                            p.set(x, yy, z, "timber", axis="y" if post else "x")
                        else:
                            brace = (x + yy) % 3 == 0
                            win = yy in (y + 2, y + 3) and (x - ox) % 3 == 0
                            p.set(x, yy, z, "glass" if win else "timber_st" if False else "plaster")
                    for z in range(oz - d, zf):
                        for x2 in (ox - hw - jut, ox + hw + jut):
                            p.set(x2, yy, z, "plaster" if (z + yy) % 3 else "timber", **(
                                {} if (z + yy) % 3 else {"axis": "y"}))
                        if yy == y + 4:
                            for x2 in range(ox - hw - jut, ox + hw + jut + 1):
                                p.set(x2, yy + 1, z, "light" if (x2 + z) % 3 == 0 else "floor")
            y += 5
        # the steep gable roof (along z), stone ridge, a stepped timber gable front
        half = hw + jut + 1
        zf = oz + jut + 1
        for k in range(half + 1):
            yy = y + 2 * k
            xl, xr = ox - half + k, ox + half - k
            for z in range(oz - d - 1, zf + 1):
                for x in range(xl, xr + 1):
                    front = z == zf - 1
                    p.set(x, yy, z, "roof" if (x in (xl, xr) or not front) else "plaster")
                    p.set(x, yy + 1, z, "roof" if (not front or x in (xl, xr)) else "plaster")
                if xl < xr:
                    p.stair(xl, yy + 1, z, "east", "bottom", "roof_st")
                    p.stair(xr, yy + 1, z, "west", "bottom", "roof_st")
            if xl < xr - 1 and k > 0:
                p.set(ox, yy, zf - 1, "timber", axis="y")
            if xl == xr:
                break
        top = y + 2 * half
        p.set(ox, top + 1, zf - 1, "roof_wall")
        p.set(ox, top + 2, zf - 1, "gold")
        p.set(ox, top + 3, zf - 1, "spike", facing="up")
        p.set(ox, y + 3, zf - 1, "glass")
        p.set(ox, y + 3, zf - 2, "light")

    gable_house(0, 0, 7, 8, 5, 2)
    gable_house(-9, -2, 5, 6, 3, 2)
    gable_house(8, -3, 5, 5, 7, 1)
    return p


# ============================================================================================ (q) arched bridge segment
def bridge(spans=2, span=7, h=10, pal=None):
    """An arched stone viaduct along x: piers with cutwaters, pointed arches between them (stairs stepping in), a deck
    five wide (a wide body crosses it), parapets of wall with merlon posts, lanterns on the posts."""
    p = Part(pal)
    L = spans * (span + 3) + 3
    for x in range(0, L):
        pier = (x % (span + 3)) < 3
        for z in range(-3, 4):
            if pier:
                for y in range(0, h + 1):
                    p.set(x, y, z, "base" if y < 3 else "wall")
            else:
                i = x % (span + 3) - 3                      # 0 .. span - 1 across the arch
                rise = min(i + 1, span - i)
                for y in range(h - 3 + min(rise, 3), h + 1):
                    p.set(x, y, z, "wall")
                if rise <= 3:
                    f = "west" if i < span // 2 else "east"
                    p.stair(x, h - 4 + rise, z, f, "top", "base_st")
        for z in range(-2, 3):
            p.set(x, h + 1, z, "path")
        for z in (-3, 3):
            p.set(x, h + 1, z, "base")
            p.set(x, h + 2, z, "base_wall")
            if x % 4 == 0:
                p.set(x, h + 2, z, "base")
                p.set(x, h + 3, z, "lantern", **LANTERN)
    for x in range(0, L, span + 3):                       # cutwaters on the piers
        for k in range(0, h - 2):
            p.stair(x + 1, k, 4, "north", "bottom", "base_st")
            p.stair(x + 1, k, -4, "south", "bottom", "base_st")
    return p


def catalogue():
    """Every part for the sheet, the towers first (they are what lovish judges first)."""
    from gothic_towers import TOWERS
    return TOWERS + CATALOGUE


CATALOGUE = [
    ("a_window_bay", "(a) tracery window bay", lambda pal: window_bay(5, 9, pal=pal)),
    ("b_buttress", "(b) buttress, pinnacles, flying arch", lambda pal: buttress(20, 3, pal=pal)),
    ("e_crocketed_gable", "(e) crocketed gable, pinnacle row", lambda pal: crocketed_gable(13, pal=pal)),
    ("f_rose_wall", "(f) rose window wall", lambda pal: rose_wall(5, pal=pal)),
    ("g_portal", "(g) portal, nested arches", lambda pal: portal(5, 9, 3, pal=pal)),
    ("h_parapet", "(h) machicolated parapet, bartizans", lambda pal: parapet(13, pal=pal)),
    ("i_crag", "(i) crag face", lambda pal: crag(24, 14, 26, pal=pal)),
    ("j_curtain_wall", "(j) curtain wall, wall-walk", lambda pal: curtain_wall(16, 10, pal=pal)),
    ("l_gatehouse", "(l) gatehouse (5 x 9 passage)", lambda pal: gatehouse(pal=pal)),
    ("m_hipped_roof", "(m) hipped roof, dormers", lambda pal: hipped_roof(11, 15, pal=pal)),
    ("p_timber_gables", "(p) timber gable cluster", lambda pal: timber_gables(pal=pal)),
    ("q_bridge", "(q) arched bridge (5-wide deck)", lambda pal: bridge(2, 7, 10, pal=pal)),
]


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "/tmp"
    pal = palette(os.environ.get("BAT_ROOF", "red"), os.environ.get("BAT_STONE", "pale"))
    for key, label, make in catalogue():
        part = make(pal)
        rig = part.to_rig(key)
        path = os.path.join(out, key + ".nbt")
        rig.structure().write(path)
        print("%-22s %-40s %s blocks %s" % (key, label, len(rig.b), "x".join(map(str, rig.size))))
