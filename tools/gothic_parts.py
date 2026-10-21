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
    "angel_robe": "smooth_quartz", "angel_skin": "quartz_block", "angel_wing": "white_concrete",
    "light": "shroomlight", "lantern": "lantern", "candle": "white_candle", "floor": "dark_oak_planks",
    "carpet": "red_carpet", "shelf": "bookshelf", "spike": "end_rod", "gold": "gold_block", "chain": "chain",
    "banner": "blue_banner", "banner_top": "blue_banner", "glass_lit": "glass_pane", "finial_gold": "end_rod",
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
    # the warm orange-copper needles of his castle-on-the-crag reference (9cfaf83e); waxed so they never turn green
    "copper": {"roof": "waxed_cut_copper", "roof_st": "waxed_cut_copper_stairs", "roof_slab": "waxed_cut_copper_slab",
               "roof_wall": "brick_wall"},
}
# warm orange accents (tracery in the gables, hood mouldings) for the copper-roofed variant
ACCENT = {"accent": "waxed_cut_copper", "accent_st": "waxed_cut_copper_stairs", "accent_slab": "waxed_cut_copper_slab"}
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
    # THE WHITE ANGEL DEFAULT (lovish, Oct 20: "middle is fine"). Warm light grey, between the two (his ref
    # 9cfaf83e): polished andesite walls with zones of plain
    # stone, darker stone-brick quoins and plinth, warm polished-diorite sills, lintels and drip courses
    "warm": {"wall": "polished_andesite", "wall_alt": "stone", "quoin": "stone_bricks",
             "trim": "polished_diorite", "trim_st": "polished_diorite_stairs", "trim_slab": "polished_diorite_slab",
             "carve": "chiseled_stone_bricks", "pillar": "polished_basalt", "rail": "andesite_wall",
             "band": "stone_bricks", "band_st": "stone_brick_stairs", "band_slab": "stone_brick_slab",
             "base": "stone_bricks", "base_st": "stone_brick_stairs", "base_slab": "stone_brick_slab",
             "base_wall": "stone_brick_wall", "base_alt": "mossy_stone_bricks", "wall_moss": "mossy_stone_bricks",
             "path": "polished_andesite"},
}


def palette(roof="red", stone="warm", **over):
    p = dict(PALETTE)
    p.update(STONES[stone])
    p.update(ROOFS[roof])
    p.update(NEEDLE)
    p.update(ACCENT)
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
        """Copies `other` in; its air cells are skipped (a part never carves what is already there)."""
        for (x, y, z), v in other.rotated(rot).b.items():
            if v[0] not in ("air", "cave_air"):
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
def _mason(p, h, seed=0):
    from gothic_towers import Mason
    return Mason(p, h, seed, plinth=2)


def _gablet(p, x0, x1, y, z, f="south"):
    """A little crocketed gablet over a set-off, w = x1 - x0 + 1 wide, on the face plane z: stairs climbing in from
    both ends, a carved apex with a spike, crockets (walls) on the rake."""
    half = (x1 - x0) // 2
    for k in range(half + 1):
        xl, xr = x0 + k, x1 - k
        for x in range(xl + 1, xr):
            p.set(x, y + k, z, "wall")
        if xl < xr:
            p.stair(xl, y + k, z, "east", "bottom", "trim_st")
            p.stair(xr, y + k, z, "west", "bottom", "trim_st")
        else:
            p.set(xl, y + k, z, "carve")
    p.set(x0 + half, y + half + 1, z, "rail")
    p.set(x0 + half, y + half + 2, z, "spike", facing="up")


def buttress(h=24, pal=None):
    """A flying buttress unit against a wall at z = 0 (the wall is not part of it): an attached buttress three wide,
    stepping out in four set-offs (each weathered with stairs, a drip under it, a crocketed gablet on its face), a
    statue niche with a lantern; a free-standing pier beyond it, battered, with a pinnacle cluster (a tall centre
    pinnacle and four small ones); and a flying arch two thick from the pier's head up to the wall high above the
    attached buttress - soffit stairs under, coping stairs on top, a row of little pinnacles riding it."""
    p = Part(pal)
    m = _mason(p, h)
    # the attached buttress: stages stepping down as they come out
    tops = {1: h - 6, 2: h - 10, 3: h - 14}
    for d, top in tops.items():
        for x in (-1, 0, 1):
            for y in range(0, top + 1):
                m.put(x, y, d, quoin=(abs(x) == 1 and (y // 2) % 2 == 0))
            p.stair(x, top + 1, d, "north", "bottom", "band_st")
            p.stair(x, top, d + 1, "north", "top", "trim_st") if d == max(tops) or tops.get(d + 1, 0) < top else None
        _gablet(p, -1, 1, top - 2, d + 1) if d < 3 else None
    p.stair(0, tops[3] + 2, 3, "north", "bottom", "band_st")
    for x in (-1, 0, 1):                                      # battered plinth
        p.stair(x, 1, 4, "north", "bottom", "base_st")
        p.set(x, 0, 4, "base")
    # the niche in the outer face: a recess under a hood, a lit lantern on a ledge
    p.clear(0, 5, 3)
    p.clear(0, 6, 3)
    p.slab(0, 4, 3, "top", "trim_slab")
    p.lantern(0, 5, 3)
    p.stair(0, 7, 4, "north", "top", "trim_st")
    p.set(0, 7, 3, "carve")
    # the free-standing pier, three by three, battered, with its pinnacle cluster
    pz = 7
    ptop = h - 4
    for x in (-1, 0, 1):
        for z in (pz - 1, pz, pz + 1):
            for y in range(0, ptop + 1):
                m.put(x, y, z, quoin=(abs(x) == 1 and abs(z - pz) == 1 and (y // 2) % 2 == 0))
    for x in range(-2, 3):
        for z in range(pz - 2, pz + 3):
            if abs(x) == 2 or abs(z - pz) == 2:
                p.set(x, 0, z, "base")
                p.stair(x, 1, z, toward(x, z, 0, pz), "bottom", "base_st")
    for x in (-1, 0, 1):
        p.stair(x, ptop - 6, pz + 2, "north", "top", "trim_st")       # a drip on the pier
    _gablet(p, -1, 1, ptop - 4, pz + 2)
    for x in (-1, 0, 1):
        for z in (pz - 1, pz, pz + 1):
            p.set(x, ptop + 1, z, "trim")
    _pinnacle(p, 0, ptop + 2, pz, tall=True)
    p.set(0, ptop + 9, pz, "rail")
    p.set(0, ptop + 10, pz, "spike", facing="up")
    for (sx, sz) in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
        _pinnacle(p, sx, ptop + 2, pz + sz)
    # the flying arch: from the pier's inner face (z = pz - 2) rising to the wall (z = 1) above the buttress
    z0, z1 = pz - 2, 1
    y_lo, y_hi = ptop - 1, h + 2
    n = z0 - z1
    for i in range(n + 1):
        z = z0 - i
        yc = y_lo + round(i * (y_hi - y_lo) / n)
        nxt = y_lo + round((i + 1) * (y_hi - y_lo) / n) if i < n else yc
        for y in range(yc - 1, max(nxt, yc + 1)):
            p.set(0, y, z, "wall")
        p.stair(0, max(nxt, yc + 1), z, "north", "bottom", "trim_st")   # coping
        p.stair(0, yc - 2, z, "south", "top", "trim_st")                 # soffit
        if i % 2 == 1:
            p.set(0, max(nxt, yc + 1) + 1, z, "bars")
            p.set(0, max(nxt, yc + 1) + 2, z, "spike", facing="up")
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
def portal(w=5, h=9, orders=4, pal=None):
    """A great west portal: an opening w wide and h tall (a wide body walks through when the doors open), four orders
    of nested pointed arches stepping out and up - each order a jamb shaft of alternating stones with a carved capital
    and an arch of voussoirs (stairs over a carved course) - a tympanum with a carved roundel, a pair of plank doors
    with dark stiles and a lintel, a crocketed gable (wimperg) over the whole with an openwork rose in it, buttress
    piers with pinnacles either side, lanterns on posts and three steps before it."""
    p = Part(pal)
    m = _mason(p, h + 12)
    half = w // 2
    depth = orders + 1
    # the wall the portal is cut through (z = 0 face, thick), its masonry
    X = half + orders + 3
    top = h + half + orders + 4
    for x in range(-X, X + 1):
        for z in range(-2, 1):
            for y in range(0, top + 1):
                m.put(x, y, z)
    # orders: each a jamb and an arch, stepping out to the front (z) and widening
    for o in range(orders + 1):
        z = o + 1 if o else 0
        hw = half + o
        for s in (-1, 1):
            x = s * (hw + 1)
            p.set(x, 0, z, "base")
            for y in range(1, h):
                p.set(x, y, z, "quoin")                             # the order's jamb, one stone face
            p.set(x, h, z, "carve")                                 # capital
            if o:                                                   # a colonette in the re-entrant angle
                p.slab(s * hw, 1, z, "bottom", "base_slab")
                for y in range(2, h):
                    p.set(s * hw, y, z, "rail")
                p.set(s * hw, h, z, "carve")
        rise = hw + 1
        for k in range(rise + 1):
            xl, xr = -hw - 1 + k, hw + 1 - k
            y = h + 1 + k
            if xl < xr:
                p.stair(xl, y, z, "east", "bottom", "trim_st")
                p.stair(xr, y, z, "west", "bottom", "trim_st")
                p.set(xl, y - 1, z, "carve" if o % 2 else "trim") if k else None
                p.set(xr, y - 1, z, "carve" if o % 2 else "trim") if k else None
                for x in range(xl + 1, xr):
                    if o == 0:
                        p.set(x, y, z, "wall")
            else:
                p.set(xl, y, z, "carve")
    # infill so each order's arch has stone behind it (the wall face at z = 0 continues up between the orders)
    for o in range(1, orders + 1):
        for z in range(1, o + 1):
            hw = half + o
            for y in range(h + 1, h + hw + 3):
                for x in range(-hw - 1, hw + 2):
                    if p.get(x, y, z) == "air" and abs(x) <= hw + 1 - (y - h - 1) and abs(x) > half + z - 1:
                        p.set(x, y, z, "wall")
    # tympanum and doors
    for x in range(-half, half + 1):
        for y in range(h - 1, h + half + 1):
            if abs(x) <= half - (y - h) or y < h:
                p.set(x, y, 0, "wall")
    p.set(0, h + 1, 1, "bars")
    for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        p.set(dx, h + 1 + dy, 0, "carve")
    p.set(0, h + 1, 0, "gold")
    for x in range(-half, half + 1):
        p.set(x, h - 2, 0, "trim")                                 # the lintel
        for y in range(1, h - 2):
            p.set(x, y, 0, "timber" if abs(x) == half or x == 0 else "floor", **(
                {"axis": "y"} if abs(x) == half or x == 0 else {}))
    # the wimperg: a crocketed gable over the outer order, an openwork rose in it
    zf = orders + 1
    hw = half + orders + 1
    yb = h + hw + 1
    for k in range(hw + 1):
        xl, xr = -hw + k, hw - k
        for x in range(xl + 1, xr):
            p.set(x, yb + k, zf, "wall")
        if xl < xr:
            p.stair(xl, yb + k, zf, "east", "bottom", "trim_st")
            p.stair(xr, yb + k, zf, "west", "bottom", "trim_st")
            if k % 2:
                p.set(xl, yb + k + 1, zf, "rail")
                p.set(xr, yb + k + 1, zf, "rail")
        else:
            p.set(xl, yb + k, zf, "carve")
    p.set(0, yb + hw + 1, zf, "rail")
    p.set(0, yb + hw + 2, zf, "spike", facing="up")
    yr = yb + 2
    for (dx, dy) in ((0, 0), (1, 1), (-1, 1), (0, 2), (1, 0), (-1, 0)):
        p.set(dx, yr + dy, zf, "bars" if (dx, dy) != (0, 1) else "carve")
    p.set(0, yr + 1, zf, "carve")
    # buttress piers either side, with pinnacles; steps; lanterns on posts
    for s in (-1, 1):
        x = s * (hw + 1)
        for z in range(0, zf + 1):
            for y in range(0, yb + 2):
                m.put(x, y, z, quoin=(y // 2) % 2 == 0)
        for z in range(0, zf + 1):
            p.stair(x, yb + 2, z, "north" if z < zf else "north", "bottom", "band_st") if z else None
        _pinnacle(p, x, yb + 2, zf, tall=True)
        p.set(x, yb + 2, 0, "trim")
        p.set(s * (hw + 2), 1, zf + 1, "rail")
        p.lantern(s * (hw + 2), 2, zf + 1)
    for k, z in enumerate(range(zf + 1, zf + 4)):
        for x in range(-half - 1, half + 2):
            p.stair(x, 0 - 0, z, "north", "bottom", "base_st") if k == 2 else p.set(x, 0, z, "path")
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
def hipped_roof(w=13, l=21, pal=None):
    """A hipped tile roof over a w x l wing, shown on the wing's top storey: a stone wall head with a corbel table,
    EAVES overhanging it, the roof steep (two courses a step) with hips all round and a crested ridge with finials; a
    cross gable on the front with a half-timbered gable end (posts, a rail, braces, a lit window, bargeboards); three
    timber dormers a side, each with its own little gabled roof and finial; two stone chimneys with capped pots."""
    p = Part(pal)
    m = _mason(p, 12)
    hw, hl = w // 2, l // 2
    # the top storey: walls, windows, a corbel table under the eaves
    for x in range(-hw, hw + 1):
        for z in range(-hl, hl + 1):
            if abs(x) == hw or abs(z) == hl:
                for y in range(0, 5):
                    m.put(x, y, z, quoin=(abs(x) == hw and abs(z) == hl and (y // 2) % 2 == 0))
    for z in range(-hl + 2, hl - 1, 3):
        for x in (-hw, hw):
            p.set(x, 2, z, "win_lit" if hsh(x, z) % 3 else "win_dark")
            p.set(x, 3, z, "win_lit" if hsh(x, z) % 3 else "win_dark")
            p.set(x, 1, z, "trim")
            p.set(x, 4, z, "trim")
    for x in range(-hw - 1, hw + 2):
        for z in range(-hl - 1, hl + 2):
            if abs(x) == hw + 1 or abs(z) == hl + 1:
                f = toward(x, z) if not (abs(x) == hw + 1 and abs(z) == hl + 1) else None
                if f:
                    p.stair(x, 4, z, f, "top", "band_st")
    y0 = 5
    # the hipped roof: steps in one cell every two courses from an eaves ring two cells out
    k = 0
    while True:
        x0, x1, z0, z1 = -hw - 2 + k, hw + 2 - k, -hl - 2 + k, hl + 2 - k
        if x0 > x1:
            break
        y = y0 + 2 * k - 1 if k else y0 - 1
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                edge = x in (x0, x1) or z in (z0, z1)
                if not edge:
                    continue
                if k == 0:
                    p.stair(x, y, z, toward(x, z) if abs(x) != abs(z) or True else None, "bottom", "roof_st")
                    continue
                p.set(x, y, z, "roof")
                if x0 == x1:
                    p.set(x, y + 1, z, "roof")
                else:
                    f = "east" if x == x0 else "west" if x == x1 else "south" if z == z0 else "north"
                    p.stair(x, y + 1, z, f, "bottom", "roof_st")
        k += 1
    ridge = y0 + 2 * (k - 1)
    for z in range(-hl + hw, hl - hw + 1):
        p.set(0, ridge + 1, z, "roof_wall")
        if z % 2 == 0:
            p.set(0, ridge + 2, z, "bars")
    for z in (-hl + hw, hl - hw):
        p.set(0, ridge + 2, z, "gold")
        p.set(0, ridge + 3, z, "spike", facing="up")
    # the cross gable on the front (+z), its ridge along z at the main ridge's height
    gw = 4
    for k in range(gw + 1):
        y = y0 + 2 * k - 1
        for z in range(hl - 3, hl + 3):
            for x in (-(gw + 1) + k, gw + 1 - k):
                if x != 0 and abs(x) <= gw + 1:
                    p.set(x, y, z, "roof")
                    p.stair(x, y + 1, z, "east" if x < 0 else "west", "bottom", "roof_st")
            for x in range(-gw + k, gw - k + 1):
                if z < hl + 2:
                    p.set(x, y, z, "roof")
                    p.set(x, y + 1, z, "roof")
        for x in range(-gw + k, gw - k + 1):                     # the gable end: timber frame and plaster
            for y in (y0 + 2 * k - 1, y0 + 2 * k):
                post = x in (-gw + k, gw - k) or x == 0
                p.set(x, y, hl + 2, "timber" if post else "plaster", **({"axis": "y"} if post else {}))
    for z in range(hl - 3, hl + 3):
        p.set(0, y0 + 2 * gw, z, "roof")
        p.set(0, y0 + 2 * gw + 1, z, "roof_wall")
    for x in range(-gw, gw + 1):
        p.set(x, y0 + 1, hl + 2, "timber_beam", axis="x")
    p.set(0, y0 + 2, hl + 2, "win_lit")
    p.set(0, y0 + 3, hl + 2, "win_lit")
    p.set(0, y0 + 2, hl + 1, "light")
    for s in (-1, 1):
        p.set(s * 1, y0 + 3, hl + 2, "win_lit")
        p.set(s * 1, y0 + 3, hl + 1, "light")
    p.set(0, y0 + 2 * gw + 2, hl + 2, "gold")
    p.set(0, y0 + 2 * gw + 3, hl + 2, "spike", facing="up")
    # dormers: three a side on the long slopes, each with its own gable roof
    for side in (-1, 1):
        for zc in (-hl + 3, -2, hl - 7):
            x = side * (hw - 1)
            fx = side
            f = "east" if side > 0 else "west"
            yb = y0 + 3
            for dz in (-1, 0, 1):
                for y in (yb, yb + 1):
                    p.set(x + fx, y, zc + dz, "timber" if dz else ("win_lit" if y > yb else "plaster"),
                          **({"axis": "y"} if dz else {}))
                p.set(x, yb, zc + dz, "roof")
                p.set(x, yb + 1, zc + dz, "light" if dz == 0 else "roof")
            p.stair(x + fx, yb + 2, zc - 1, "south", "bottom", "roof_st")
            p.stair(x + fx, yb + 2, zc + 1, "north", "bottom", "roof_st")
            p.set(x + fx, yb + 2, zc, "roof")
            p.set(x + fx, yb + 3, zc, "roof_wall")
            for xi in (x, x - fx):
                p.stair(xi, yb + 2, zc - 1, "south", "bottom", "roof_st")
                p.stair(xi, yb + 2, zc + 1, "north", "bottom", "roof_st")
                p.set(xi, yb + 2, zc, "roof")
            p.slab(x + 2 * fx, yb - 1, zc, "top", "trim_slab")
    # chimneys
    for (cx, cz) in ((-hw + 2, -hl + 3), (hw - 2, -2)):
        step = min(cx + hw + 2, hw + 2 - cx, cz + hl + 2, hl + 2 - cz)    # its ring on the roof
        for y in range(0, y0 + 2 * step + 3):
            p.set(cx, y, cz, "base")
        p.set(cx, y0 + 2 * step + 3, cz, "base_wall")
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


# ============================================================================================ the angel and the dome
# The White Angel's emblem: a winged figure in pale stone, wings spread, a gold halo - the crown of every capitol.
# Front view (x across, rows from the top); Q robe and body, H head, G gold, W wing, F wing tip feathers, P plinth.
ANGEL_ROWS = [
    "     GGG     ",
    "      H      ",
    "F    QQQ    F",
    "WF  QQQQQ  FW",
    "WWW QQQQQ WWW",
    " WWWWQQQWWWW ",
    "  WWWQQQWWW  ",
    "   WWQQQWW   ",
    "     QQQ     ",
    "    QQQQQ    ",
    "    QQQQQ    ",
    "   QQQQQQQ   ",
    "    PPPPP    ",
]


def angel(pal=None):
    """The winged angel statue (13 wide, 13 tall, facing south): a robed body three deep, a head, a gold halo, wings
    spread wide in a plane behind the shoulders (one deep, feathered tips up and swept back), a sword held point-up
    before the body, a gold-banded plinth. Readable from the RTS camera: a pale winged figure on the skyline."""
    p = Part(pal)
    n = len(ANGEL_ROWS)
    for i, row in enumerate(ANGEL_ROWS):
        y = n - 1 - i
        for j, ch in enumerate(row):
            x = j - 6
            if ch == "Q":
                for z in (-1, 0, 1):
                    if abs(x) < 2 or z == 0 or y < 4:
                        p.set(x, y, z, "angel_robe")
            elif ch == "H":
                p.set(x, y, 0, "angel_skin")
                p.set(x, y, -1, "angel_robe")
            elif ch == "G":
                p.set(x, y, 0, "gold")
            elif ch in "WF":
                p.set(x, y, -1, "angel_wing")
                if ch == "F" and i == 2:
                    p.set(x, y + 1, -1, "rail")
            elif ch == "P":
                for z in (-1, 0, 1):
                    p.set(x, y, z, "gold" if abs(x) == 2 else "trim")
    for s in (-1, 1):                                       # the wing ends swept back
        for y in (8, 9):
            p.set(s * 6, y, -2, "angel_wing")
    p.set(0, 6, 2, "gold")                                  # the sword's hilt, its blade point-up
    for y in range(7, 11):
        p.set(0, y, 2, "bars")
    p.set(0, 11, 2, "spike", facing="up")
    return p


def ribbed_dome(r=5, pal=None, drum_h=6, ribs=8):
    """A dark ribbed dome on a drum: the drum round, radius r, alternating columns and lit windows, a cornice, eight
    slender pinnacles round its rim; the dome a hemisphere of dark slate with pale ribs, a gold band at its foot and a
    plinth at its crown. Returns (part, y of the plinth top)."""
    p = Part(pal)
    for (x, z) in disc(r):
        p.set(x, 0, z, "band")
    for (x, z) in ring(r + 1):
        p.stair(x, 0, z, toward(x, z), "top", "band_st")
    rim = sorted(ring(r), key=lambda c: math.atan2(c[1], c[0]))
    for y in range(1, drum_h):
        for i, (x, z) in enumerate(rim):
            if i % 2 == 0:
                p.set(x, y, z, "pillar", axis="y")
            else:
                p.set(x, y, z, "win_lit" if 1 < y < drum_h - 1 else "wall")
        for (x, z) in disc(r - 1):
            if y in (1, drum_h - 1):
                p.set(x, y, z, "wall")
            elif y == 3 and (x + z) % 3 == 0:
                p.set(x, y, z, "light")
    for (x, z) in ring(r + 1):
        p.stair(x, drum_h, z, toward(x, z), "top", "trim_st")
    for (x, z) in disc(r):
        p.set(x, drum_h, z, "trim")
    for k in range(8):
        a = k * math.pi / 4
        _pinnacle(p, int(round(math.cos(a) * (r + 1))), drum_h + 1, int(round(math.sin(a) * (r + 1))), tall=True)
    y0 = drum_h + 1
    for (x, z) in disc(r):
        p.set(x, y0, z, "gold" if (x, z) in ring(r) else "needle")
    for x in range(-r, r + 1):
        for z in range(-r, r + 1):
            for y in range(0, r + 1):
                d = math.sqrt(x * x + z * z + (y * 1.15) ** 2)
                if r - 0.75 < d < r + 0.45:
                    a = math.atan2(z, x)
                    rib = min(abs(math.remainder(a - k * 2 * math.pi / ribs, 2 * math.pi)) for k in range(ribs))
                    p.set(x, y0 + 1 + y, z, "trim" if rib * max(1.0, math.hypot(x, z)) < 0.55 else "needle")
    top = max(y for (x, y, z) in p.b if (x, z) == (0, 0))
    p.set(0, top + 1, 0, "gold")
    return p, top + 1


CATALOGUE = [
    ("a_window_bay", "(a) tracery window bay", lambda pal: window_bay(5, 9, pal=pal)),
    ("b_buttress", "(b) flying buttress: set-offs, gablets, niche, pier", lambda pal: buttress(24, pal=pal)),
    ("e_crocketed_gable", "(e) crocketed gable, pinnacle row", lambda pal: crocketed_gable(13, pal=pal)),
    ("f_rose_wall", "(f) rose window wall", lambda pal: rose_wall(5, pal=pal)),
    ("g_portal", "(g) portal, nested arches", lambda pal: portal(5, 9, 4, pal=pal)),
    ("h_parapet", "(h) machicolated parapet, bartizans", lambda pal: parapet(13, pal=pal)),
    ("i_crag", "(i) crag face", lambda pal: crag(24, 14, 26, pal=pal)),
    ("j_curtain_wall", "(j) curtain wall, wall-walk", lambda pal: curtain_wall(16, 10, pal=pal)),
    ("l_gatehouse", "(l) gatehouse (5 x 9 passage)", lambda pal: gatehouse(pal=pal)),
    ("m_hipped_roof", "(m) hipped roof: cross gable, dormers, chimneys", lambda pal: hipped_roof(13, 21, pal=pal)),
    ("p_timber_gables", "(p) timber gable cluster", lambda pal: timber_gables(pal=pal)),
    ("q_bridge", "(q) arched bridge (5-wide deck)", lambda pal: bridge(2, 7, 10, pal=pal)),
]


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "/tmp"
    pal = palette(os.environ.get("BAT_ROOF", "red"), os.environ.get("BAT_STONE", "warm"))
    for key, label, make in catalogue():
        part = make(pal)
        rig = part.to_rig(key)
        path = os.path.join(out, key + ".nbt")
        rig.structure().write(path)
        print("%-22s %-40s %s blocks %s" % (key, label, len(rig.b), "x".join(map(str, rig.size))))
