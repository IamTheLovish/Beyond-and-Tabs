#!/usr/bin/env python3
"""THE ARCHITECTURE KIT: faction-agnostic detail parts, one per recipe R1-R16 of
design/vehicle_labs/studies/detail_recipes.md, so every building generator (7 factions, 4 tiers) places real
architecture - eaves that oversail, piers with bases and capitals, windows with reveals, sills and heads, receding
portals, corbel tables, banded chimneys - instead of flat walls (lovish, Oct 28-29 2026: "not looking realistic
enough", "become a master architect", "lets keep improving buildings"). The rulebook: design/architecture_rules.md;
its gates are measured by tools/building_gates.py. Tests (written first): tools/test_arch_kit.py.

CONVENTIONS - a part is a tools/gothic_parts.Part (the same one tools/brass_lab_parts.py builds on): cells
{(x, y, z): (block id, props)} in the part's own frame. Its FRONT faces SOUTH (+z); the wall's outer plane is z = 0
(proud z > 0, recessed z < 0); x runs along the face from 0; y = 0 is the part's floor. Stamp a part with
host.place(part, ox, oy, oz, rot) - rot quarter turns clockwise seen from above (rot 1 turns a south front WEST,
2 NORTH, 3 EAST). Every part takes a MATERIAL SET (Mats): wall / wall2 / trim (pale) / dark families (full, stairs,
slab, wall), a roof_kit roof dict, metal, glass, light, timber and render, so the same part serves the Brassworks'
brick, the angels' white stone and the Gravebound's deepslate. Parts never name a block outside their set except the
universal fittings in UNIVERSAL. Proportions follow the rulebook: storey 4-5, openings 1/2-2/3 of a bay, piers 1.

Reuse: R11 chimney wraps brass_lab_parts.stack, R12 dome wraps gothic_parts.ribbed_dome, R16 gear wraps
brass_lab_parts.flywheel (recoloured into the set); R2/R3 roofs are tools/roof_kit.py courses (the kit adds the
gable infill, verge brackets, downpipes and the ridge comb round them, never its own tiling).

    python3 tools/arch_kit_sheet.py      -> design/arch_kit/kit_sheet.nbt (every part, plus the demo bay)
"""
import math
import os
import random
import sys
from typing import NamedTuple

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import brass_lab_parts as bp  # noqa: E402
import gothic_parts as gp  # noqa: E402
import roof_kit as R  # noqa: E402

Part = gp.Part


# ============================================================================================ material sets
class Fam(NamedTuple):
    full: str
    stairs: str
    slab: str
    wall: str = None


class Timber(NamedTuple):
    log: str
    planks: str
    stairs: str
    slab: str
    trapdoor: str
    fence: str


class Mats(NamedTuple):
    name: str
    wall: Fam          # the body (80 %)
    wall2: Fam         # the second tone: bands, heads, strings, the course under a lip (10-20 %)
    trim: Fam          # PALE dressings: plinth, lips, sills, pilasters, copings
    dark: Fam          # the dark family: pipes, posts, corbels, outer portal order
    roof: dict         # a tools/roof_kit.py roof (tones T0/T1/T2, ridge, verge, gutter)
    metal: Fam         # copper / brass: gutters, bands, tanks, domes
    glass: tuple       # (pane, full block)
    light: tuple       # (lantern, glow block)
    timber: Timber
    render: str        # half-timber infill


GRAVE_ROOF = {
    "tones": (("blackstone", "blackstone_stairs", "blackstone_slab"),
              ("polished_blackstone", "polished_blackstone_stairs", "polished_blackstone_slab"),
              ("polished_blackstone_bricks", "polished_blackstone_brick_stairs", "polished_blackstone_brick_slab")),
    "ridge": ("chiseled_polished_blackstone",),
    "verge": ("polished_deepslate", "polished_deepslate_stairs", "polished_deepslate_slab"),
    "gutter": "waxed_oxidized_cut_copper_stairs",
}

BRASSWORKS = Mats(   # red brick, pale diorite dressings, blue-brick (blackstone) dark, grey slate roofs, green copper
    "brassworks",
    Fam("bricks", "brick_stairs", "brick_slab", "brick_wall"),
    Fam("polished_granite", "polished_granite_stairs", "polished_granite_slab", "granite_wall"),
    Fam("polished_diorite", "polished_diorite_stairs", "polished_diorite_slab", "diorite_wall"),
    Fam("polished_blackstone_bricks", "polished_blackstone_brick_stairs", "polished_blackstone_brick_slab",
        "polished_blackstone_brick_wall"),
    R.SLATE,
    Fam("waxed_weathered_cut_copper", "waxed_weathered_cut_copper_stairs", "waxed_weathered_cut_copper_slab"),
    ("glass_pane", "glass"), ("lantern", "shroomlight"),
    Timber("stripped_dark_oak_log", "dark_oak_planks", "dark_oak_stairs", "dark_oak_slab", "dark_oak_trapdoor",
           "dark_oak_fence"),
    "white_terracotta")

SUNFORGED = Mats(    # the angels: white quartz, warm sandstone dressings, diorite second tone, green copper roofs, gold
    "sunforged",
    Fam("smooth_quartz", "smooth_quartz_stairs", "smooth_quartz_slab", "diorite_wall"),
    Fam("polished_diorite", "polished_diorite_stairs", "polished_diorite_slab", "diorite_wall"),
    Fam("smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab", "sandstone_wall"),
    Fam("stone_bricks", "stone_brick_stairs", "stone_brick_slab", "stone_brick_wall"),
    R.COPPER,
    Fam("waxed_cut_copper", "waxed_cut_copper_stairs", "waxed_cut_copper_slab"),
    ("yellow_stained_glass_pane", "yellow_stained_glass"), ("lantern", "glowstone"),
    Timber("stripped_birch_log", "birch_planks", "birch_stairs", "birch_slab", "birch_trapdoor", "birch_fence"),
    "calcite")

GRAVEBOUND = Mats(   # deepslate walls, bone dressings, blackstone roofs, verdigris, sickly green light
    "gravebound",
    Fam("deepslate_bricks", "deepslate_brick_stairs", "deepslate_brick_slab", "deepslate_brick_wall"),
    Fam("cobbled_deepslate", "cobbled_deepslate_stairs", "cobbled_deepslate_slab", "cobbled_deepslate_wall"),
    Fam("smooth_quartz", "smooth_quartz_stairs", "smooth_quartz_slab", "diorite_wall"),
    Fam("polished_blackstone_bricks", "polished_blackstone_brick_stairs", "polished_blackstone_brick_slab",
        "polished_blackstone_brick_wall"),
    GRAVE_ROOF,
    Fam("waxed_oxidized_cut_copper", "waxed_oxidized_cut_copper_stairs", "waxed_oxidized_cut_copper_slab"),
    ("lime_stained_glass_pane", "lime_stained_glass"), ("soul_lantern", "verdant_froglight"),
    Timber("dark_oak_log", "dark_oak_planks", "dark_oak_stairs", "dark_oak_slab", "dark_oak_trapdoor",
           "dark_oak_fence"),
    "bone_block")

SETS = (BRASSWORKS, SUNFORGED, GRAVEBOUND)
UNIVERSAL = {"iron_bars", "chain", "lever", "cauldron", "lightning_rod", "campfire"}
DRY = {"waterlogged": "false"}
_ID = {"__kit__": "__kit__"}       # an identity palette: parts set real block ids (gothic Part maps roles otherwise)
_AXIS_OK = ("_log", "_wood", "_stem", "_hyphae", "chain", "basalt", "pillar", "bone_block", "hay_block")


# ============================================================================================ helpers
def blank():
    return Part(dict(_ID))


def normalised(p):
    """The part shifted so its lowest x, y and z are 0 (rotations of a part compare equal this way)."""
    x0, _, y0, _, z0, _ = p.bbox()
    out = blank()
    out.b = {(x - x0, y - y0, z - z0): v for (x, y, z), v in p.b.items()}
    return out


def stair(p, x, y, z, name, facing, half="bottom"):
    p.set(x, y, z, name, facing=facing, half=half, shape="straight", waterlogged="false")


def slab(p, x, y, z, name, typ="bottom"):
    p.set(x, y, z, name, type=typ, waterlogged="false")


def _lantern(p, x, y, z, m):
    p.set(x, y, z, m.light[0], hanging="false", waterlogged="false")


def recolour(src, mapping):
    """A kit Part with src's cells, each block id swapped through mapping (props kept; an axis is dropped from blocks
    that have none)."""
    out = blank()
    for q, (n, pr) in src.b.items():
        n = mapping.get(n, n)
        if "axis" in pr and not any(k in n for k in _AXIS_OK):
            pr = {k: v for k, v in pr.items() if k != "axis"}
        out.b[q] = (n, dict(pr))
    return out


def _brass_map(m):
    """brass_lab_parts' resolved BRASS ids -> the set's."""
    B = bp.BRASS
    return {B["wall"]: m.wall.full, B["blue"]: m.dark.full, B["blue_st"]: m.dark.stairs, B["blue_slab"]: m.dark.slab,
            B["brass"]: m.metal.full, B["brass_b"]: m.trim.full, B["light"]: m.light[1], B["coal"]: m.dark.full,
            B["iron"]: m.dark.full, B["post"]: m.timber.log, B["bars"]: "iron_bars"}


# ============================================================================================ R1-R3 eaves and roofs
def eave(m, L):
    """R1 hall eave band, a wall run x 0..L-1 at z 0: y 0 the darker course (wall2) under y 1 the pale top course; the
    pale LIP (trim slab, top) 1 proud at y 1 and the copper GUTTER (stair, half top, tall side to the wall) on it at
    y 2. Lip and gutter run one cell past each end so two runs meeting at a corner close it."""
    p = blank()
    for x in range(L):
        p.set(x, 0, 0, m.wall2.full)
        p.set(x, 1, 0, m.trim.full)
    for x in range(-1, L + 1):
        slab(p, x, 1, 1, m.trim.slab, "top")
        stair(p, x, 2, 1, m.metal.stairs, "north", "top")
    return p


def oversail(m, span, length, drop=0, seed=0):
    """R2: a tools/roof_kit.py gable (the set's roof) over walls at z 0..span-1 (u -> z), x 0..length-1 (the ridge
    along x), its eave course at y 0 - eaves and verges 1 proud - plus what the kit adds round it: the gable
    triangles filled in the wall material under the slope, upside-down pale stair BRACKETS under the verge over the
    wall corners and the middle, and `drop`-long dark DOWNPIPES under the gutters at the four corners (drop = the wall
    height, so they reach y 1 of the building)."""
    p = blank()
    roof = R.gable(span, length, axis="x", seed=seed, mats=m.roof)
    for q, v in roof.items():
        p.b[q] = v
    for z in range(span):                                   # gable infill under every slope cell at the end walls
        top = max(y for (x, y, zz) in roof if x == 0 and zz == z)
        for x in (0, length - 1):
            for y in range(0, top):
                p.set(x, y, z, m.wall.full)
    mid = (span - 1) // 2
    for x, f in ((-1, "east"), (length, "west")):          # brackets under the verge (tall side against the gable)
        for z in sorted({0, span - 1, mid}):
            ys = [y for (xx, y, zz), (n, _) in roof.items() if xx == x and zz == z and n in m.roof["verge"]]
            if ys and (x, min(ys) - 1, z) not in p.b:
                stair(p, x, min(ys) - 1, z, m.trim.stairs, f, "top")
    if drop:
        for x in (0, length - 1):
            for z in (-1, span):
                for y in range(-2, -drop - 1, -1):
                    p.set(x, y, z, m.dark.wall)
    return p


def cresting(roof, m, every=1):
    """R3: a copy of `roof` with an iron comb on its ridge (every `every` cells) and a lightning-rod finial at both
    ends of the ridge."""
    p = blank()
    p.b = dict(roof.b)
    ridge = sorted(q for q, (n, _) in roof.b.items() if n in m.roof["ridge"])
    if not ridge:
        return p
    along = 0 if len({q[0] for q in ridge}) > 1 else 2
    ends = {min(ridge, key=lambda q: q[along]), max(ridge, key=lambda q: q[along])}
    for i, (x, y, z) in enumerate(sorted(ridge, key=lambda q: q[along])):
        if (x, y, z) in ends:
            p.set(x, y + 1, z, "lightning_rod", facing="up", powered="false", waterlogged="false")
        elif i % every == 0:
            p.set(x, y + 1, z, "iron_bars")
    return p


def hip_roof(m, w, seed=0):
    """R3: a pyramid hip roof over a w x w tower (walls x, z 0..w-1), 1 proud all round, in slab pairs (26.5 deg):
    faces in roof_kit tones, HIPS (the diagonals) in the roof's verge tone (a line, like the ridge), a ridge-block
    apex with a lightning-rod finial, gutters under the eaves."""
    p = blank()
    lo, hi = -1, w
    c = (w - 1) / 2
    rings = (hi - lo) // 2 + 1
    g = R.tones(rings, 4 * (hi - lo), seed)              # one course per ring, read round the ring
    for x in range(lo, hi + 1):
        for z in range(lo, hi + 1):
            d = min(x - lo, hi - x, z - lo, hi - z)
            y, typ = d // 2, ("bottom" if d % 2 == 0 else "top")
            a = int((math.atan2(z - c, x - c) + math.pi) / (2 * math.pi) * 4 * (hi - lo)) % (4 * (hi - lo))
            if abs(x - c) == abs(z - c) and (x, z) != (c, c):
                name = m.roof["verge"][2]                # the hips: the verge (bargeboard) tone, like a ridge line
            else:
                name = m.roof["tones"][g[min(d, rings - 1)][a]][2]
            slab(p, x, y, z, name, typ)
            if d == 0 and not (x in (lo, hi) and z in (lo, hi)):
                f = "south" if z == lo else "north" if z == hi else "east" if x == lo else "west"
                stair(p, x, -1, z, m.roof["gutter"], f, "top")
    if c == int(c):
        top = max(y for (x, y, z) in p.b if (x, z) == (c, c))
        p.set(int(c), top, int(c), m.roof["ridge"][0])
        p.set(int(c), top + 1, int(c), "lightning_rod", facing="up", powered="false", waterlogged="false")
    return p


# ============================================================================================ R4-R5 roof openings
def dormer(m):
    """R4: a 3-wide dormer eye x -2..2 riding up through a slope behind it: a pale sill course with a proud sill
    stair, 3 x 2 panes set one back between timber cheeks, a little gable in the ROOF's own stairs (rake facing in)
    running back 3 cells into the main slope, a slab cap, a lantern behind the glass."""
    p = blank()
    t0 = m.roof["tones"][0]
    for x in range(-2, 3):
        p.set(x, 0, 0, m.trim.full)
    for x in range(-1, 2):
        stair(p, x, 0, 1, m.trim.stairs, "north", "top")
        for y in (1, 2):
            p.set(x, y, 0, "air")
            p.set(x, y, -1, m.glass[0])
        p.set(x, 3, 0, m.timber.planks)
    for z in range(-3, 1):
        for x in (-2, 2):
            for y in (1, 2):
                p.set(x, y, z, m.timber.planks)
        stair(p, -2, 3, z, t0[1], "east")
        stair(p, 2, 3, z, t0[1], "west")
        stair(p, -1, 4, z, t0[1], "east")
        stair(p, 1, 4, z, t0[1], "west")
        p.set(0, 4, z, t0[0])
        slab(p, 0, 5, z, t0[2])
    p.set(0, 0, -2, m.trim.full)
    _lantern(p, 0, 1, -2, m)
    return p


def clerestory(m, L):
    """R5: a monitor strip of windows x 0..L-1 between a lower and an upper roof: dark posts every 3rd cell, 2-pane
    lights between them on a dark stair SILL (half bottom) under a dark stair HEAD (half top), a pale slab cap 1
    proud."""
    p = blank()
    for x in range(L):
        post = x % 3 == 0
        for y in range(4):
            if post:
                p.set(x, y, 0, m.dark.full)
            elif y == 0:
                stair(p, x, 0, 0, m.dark.stairs, "north", "bottom")
            elif y == 3:
                stair(p, x, 3, 0, m.dark.stairs, "north", "top")
            else:
                p.set(x, y, 0, m.glass[0])
        p.set(x, 4, 0, m.dark.full)
        slab(p, x, 4, 1, m.trim.slab, "top")
    return p


def roof_light(m, L):
    """R5: a glazed barrel roof x 0..L-1 over a lane z -3..3: a stepped quarter-circle each side (1 up, 1 in, 1 up,
    2 in) of FULL glass with panes at the foot, a flat 3-wide crown, metal ribs across the whole profile every 4th
    cell (the crown cell of each rib is the purlin)."""
    prof = {-3: (0, 1), -2: (2,), -1: (3,), 0: (3,), 1: (3,), 2: (2,), 3: (0, 1)}
    p = blank()
    for x in range(L):
        rib = x % 4 == 0 or x == L - 1
        for z, ys in prof.items():
            for y in ys:
                name = m.metal.full if rib else (m.glass[0] if (abs(z) == 3 and y == 0) else m.glass[1])
                p.set(x, y, z, name)
    return p


# ============================================================================================ R6-R9 walls and openings
def cornice(m, L):
    """R6: a 3-row cornice that steps OUT as it rises: y 0 a flush second-tone string (upside-down stair, the notch
    out), y 1 a frieze with thin pale DENTILS half proud every other cell, y 2 a pale ledge 1 proud (top slab: a
    shadow under it, a gutter sits on it) over a pale course."""
    p = blank()
    for x in range(L):
        stair(p, x, 0, 0, m.wall2.stairs, "north", "top")
        p.set(x, 1, 0, m.wall.full)
        if x % 2 == 0:
            p.set(x, 1, 1, m.trim.wall)
        p.set(x, 2, 0, m.trim.full)
        slab(p, x, 2, 1, m.trim.slab, "top")
    return p


def string_course(m, L):
    """R6: a storey line - a whole course of the second tone with a pale drip (upside-down stair) 1 proud."""
    p = blank()
    for x in range(L):
        p.set(x, 0, 0, m.wall2.full)
        stair(p, x, 0, 1, m.trim.stairs, "north", "top")
    return p


def pier(m, h=5, lantern=False):
    """R7: a 1-wide pier x 0 in the wall plane (z 0) with its pilaster strip in front (z 1): a pale base a block
    proud (y 0), the thin pale pilaster face (y 1..h-3), the capital a pale stair pair at the beam line (y h-2
    bottom, y h-1 top: a bulge); the pier body in the wall material banded with the second tone every 2nd row.
    lantern: a lantern standing on an upside-down stair bracket in front of the pilaster."""
    p = blank()
    p.set(0, 0, 0, m.trim.full)
    p.set(0, 0, 1, m.trim.full)
    for y in range(1, h):
        p.set(0, y, 0, m.wall2.full if y % 2 == 0 and y < h - 1 else m.wall.full)
    for y in range(1, h - 2):
        p.set(0, y, 1, m.trim.wall)
    stair(p, 0, h - 2, 1, m.trim.stairs, "north", "bottom")
    stair(p, 0, h - 1, 1, m.trim.stairs, "north", "top")
    if lantern:
        stair(p, 0, h - 3, 2, m.trim.stairs, "north", "top")
        _lantern(p, 0, h - 2, 2, m)
    return p


KINDS = {"tall": (2, 5), "medium": (3, 4), "small": (1, 4)}      # (width, height sill-to-head)


def window(m, kind="tall", lit=True):
    """R8: an opening x 0..W-1 (KINDS) with its sill at y 0 and its head at y H-1 in a wall whose plane is z 0: the
    reveal is air at z 0, the panes sit one back (z -1) in front of a backing, the SILL is an upside-down stair 1
    proud, the HEAD the other stone: tall = springer stairs curving off the jambs (2 x 3 panes); medium = a flush
    head course over paired lights split by a thin pale mullion; small = one 1 x 2 light under a stair head with
    open timber shutters each side. lit: a lantern 2 back behind the glass."""
    W, H = KINDS[kind]
    p = blank()
    top = H - 2
    for x in range(W):
        p.set(x, 0, 0, m.wall.full)
        p.set(x, 0, -1, m.wall.full)
        stair(p, x, 0, 1, m.trim.stairs if kind != "medium" else m.wall.stairs, "north",
              "top" if kind != "medium" else "bottom")
        for y in range(1, top + 1):
            p.set(x, y, 0, "air")
            mull = kind == "medium" and x == 1
            p.set(x, y, -1, m.trim.wall if mull else m.glass[0])
        p.set(x, H - 1, -1, m.wall.full)
    if kind == "tall":
        stair(p, 0, H - 1, 0, m.wall2.stairs, "west", "top")
        stair(p, W - 1, H - 1, 0, m.wall2.stairs, "east", "top")
    else:
        for x in range(W):
            stair(p, x, H - 1, 0, m.wall2.stairs, "north", "top")
    if kind == "small":
        for x in (-1, W):
            for y in range(1, top + 1):
                p.set(x, y, 1, m.timber.trapdoor, facing="south", half="bottom", open="true", powered="false",
                      waterlogged="false")
    if lit:
        c = (W - 1) // 2
        p.set(c, 0, -2, m.wall.full)
        _lantern(p, c, 1, -2, m)
    return p


def bay(m, kind="tall"):
    """R7 + R8: one facade bay - a pier with its lantern at x 0, the window at x 1..W."""
    W, H = KINDS[kind]
    p = blank()
    p.place(pier(m, H, lantern=True), 0, 0, 0)
    p.place(window(m, kind), 1, 0, 0)
    for (x, y, z), v in window(m, kind).b.items():           # keep the reveal's air (place() skips air)
        if v[0] == "air":
            p.b[(x + 1, y, z)] = v
    return p


def portal(m, w=3, h=4, orders=3):
    """R9: a receding portal in a wall plane z 0: `orders` nested orders, each ONE deeper (z 0, -1, ...) and one
    narrower each side, each in a finer stone (dark -> second tone -> pale trim, then the trim again), its head a
    course over upside-down springer stairs at the shoulders; the door plane (timber, a centre post) behind the last
    order; a pale step row 1 proud across the whole front, lanterns on pale posts each side."""
    p = blank()
    mats = [m.dark, m.wall2, m.trim] + [m.trim] * max(0, orders - 3)
    half = [(w - 1) // 2 + (orders - 1 - k) for k in range(orders)]
    hk = [h + (orders - 1 - k) for k in range(orders)]
    X = half[0] + 1
    top = hk[0] + 1
    for k in range(orders):
        z = -k
        f = mats[k]
        for x in range(-X, X + 1):
            for y in range(0, top + 1):
                inside = abs(x) <= half[k] and 1 <= y < hk[k]
                if not inside:
                    p.set(x, y, z, m.trim.full if (y == 0 and abs(x) <= half[k]) else f.full)
        stair(p, -half[k], hk[k] - 1, z, f.stairs, "west", "top")
        stair(p, half[k], hk[k] - 1, z, f.stairs, "east", "top")
    zd = -orders
    for x in range(-half[-1], half[-1] + 1):
        for y in range(0, hk[-1]):
            if y == 0:
                p.set(x, 0, zd, m.trim.full)
            elif x == 0:
                p.set(x, y, zd, m.timber.log, axis="y")
            else:
                p.set(x, y, zd, m.timber.planks)
    for x in range(-X, X + 1):
        slab(p, x, 0, 1, m.trim.slab)
    for s in (-1, 1):
        x = s * (X + 1)
        p.set(x, 0, 1, m.trim.full)
        p.set(x, 1, 1, m.trim.wall)
        _lantern(p, x, 2, 1, m)
    return p


# ============================================================================================ R10-R12 towers
def corbel_stage(m, L):
    """R10: a tower stage foot over a lower wall x 0..L-1 at z 0 (y 0..1): CORBELS (upside-down dark stairs) every
    other cell at y 1 z 1, air teeth between; the OVERSAIL course 1 proud at y 2 running a cell past each end (pale,
    one wet second-tone cell in 6); the next stage stands on it a block wider (z 1, y 3..4), its own pale base
    course at y 3."""
    p = blank()
    for x in range(L):
        for y in range(5):
            p.set(x, y, 0, m.wall.full)
        if x % 2 == 0:
            stair(p, x, 1, 1, m.dark.stairs, "north", "top")
    for x in range(-1, L + 1):
        p.set(x, 2, 1, m.wall2.full if x % 6 == 3 else m.trim.full)
        p.set(x, 3, 1, m.trim.full)
        p.set(x, 4, 1, m.wall.full)
    return p


def chimney(m, h=10, w=3, roof_y=None):
    """R11: brass_lab_parts.stack in the set's materials (plinth, banded shaft, metal band, corbelled oversailing cap,
    glowing throat, a lit campfire smoking) plus: a plinth a block wider with thin corners, THIN CORNER walls up the
    shaft so it reads octagonal, and at `roof_y` a pale stair FLASHING skirt round it with a metal band above."""
    p = recolour(bp.stack(h, w), _brass_map(m))
    for y in range(2, h - 3):
        for x, z in ((0, 0), (0, w - 1), (w - 1, 0), (w - 1, w - 1)):
            p.set(x, y, z, m.dark.wall if y % 4 == 0 else m.wall.wall)
    for y in (0, 1):
        for x in range(-1, w + 1):
            for z in range(-1, w + 1):
                if x in (-1, w) or z in (-1, w):
                    corner = x in (-1, w) and z in (-1, w)
                    p.set(x, y, z, m.dark.wall if corner else m.dark.full)
    if roof_y is not None:
        for x in range(-1, w + 1):
            for z in range(-1, w + 1):
                if x in (-1, w) or z in (-1, w):
                    stair(p, x, roof_y, z, m.trim.stairs, gp.toward(x, z, (w - 1) / 2, (w - 1) / 2))
        for x in range(w):
            for z in range(w):
                if x in (0, w - 1) or z in (0, w - 1):
                    p.set(x, roof_y + 1, z, m.metal.full)
    return p


def _ring(w):
    return [(x, z) for x in range(-w, w + 1) for z in range(-w, w + 1) if max(abs(x), abs(z)) == w]


def spire(m):
    """R12: a copper spire on the axis (0, 0): a bulb (rings of half-width 2, copper faces, pale RIBS on the four
    cardinal meridians, a stair shoulder), a NECK of three thin rings (pale top-slab cornice, dark wall collar), a
    second small bulb, a needle with a dark collar, a lightning-rod tip."""
    p = blank()

    def ring(y, w, face, rib, st=False):
        for x, z in _ring(w):
            cardinal = x == 0 or z == 0
            n = rib if cardinal else face
            if st:
                f = gp.toward(x, z)
                stair(p, x, y, z, (m.trim.stairs if cardinal else m.metal.stairs), f)
            else:
                p.set(x, y, z, n)
    ring(0, 2, m.metal.full, m.trim.full)
    ring(1, 2, m.metal.full, m.trim.full)
    ring(2, 2, None, None, st=True)
    for x, z in _ring(1) + [(0, 0)]:
        p.set(x, 2, z, m.metal.full)
    for x, z in _ring(1):
        slab(p, x, 3, z, m.trim.slab, "top")
        p.set(x, 4, z, m.dark.wall)
    p.set(0, 3, 0, m.dark.full)
    p.set(0, 4, 0, m.dark.full)
    ring(5, 1, m.metal.full, m.trim.full)
    p.set(0, 5, 0, m.metal.full)
    ring(6, 1, None, None, st=True)
    p.set(0, 6, 0, m.metal.full)
    p.set(0, 7, 0, m.metal.full)
    p.set(0, 8, 0, m.dark.wall)
    p.set(0, 9, 0, m.metal.full)
    p.set(0, 10, 0, "lightning_rod", facing="up", powered="false", waterlogged="false")
    return p


def gothic_pal(m):
    """gothic_parts roles -> the set (for the parts the kit wraps from it)."""
    return {"band": m.dark.full, "band_st": m.dark.stairs, "pillar": m.timber.log, "win_lit": m.glass[0],
            "wall": m.wall.full, "light": m.light[1], "trim": m.trim.full, "trim_st": m.trim.stairs,
            "carve": m.wall2.full, "bars": "iron_bars", "rail": m.trim.wall, "spike": "lightning_rod",
            "needle": m.roof["tones"][0][0], "gold": m.metal.full}


def dome(m, r=4):
    """R12: gothic_parts.ribbed_dome in the set: a drum of columns and lit windows, a cornice, pinnacles, the dome in
    the roof's T0 with pale ribs, a metal band at its foot and a metal knop on top."""
    d, _ = gp.ribbed_dome(r, pal=gothic_pal(m))
    return recolour(d, {})


# ============================================================================================ R13-R16 railings ... machinery
def balustrade(m, L, every=4, panel="bars"):
    """R13: posts + panel + coping along x 0..L-1: pale wall POSTS every `every` cells and at both ends standing
    through the coping; between them an iron-bar panel (or a solid wall-material parapet) under a pale slab coping."""
    p = blank()
    for x in range(L):
        if x % every == 0 or x == L - 1:
            p.set(x, 0, 0, m.trim.wall)
            p.set(x, 1, 0, m.trim.wall)
        else:
            p.set(x, 0, 0, "iron_bars" if panel == "bars" else m.wall.full)
            slab(p, x, 1, 0, m.trim.slab)
    return p


def half_timber(m, L):
    """R14: a two-storey timber front x 0..L-1: a pale-based lower storey in the wall material (z 0, y 0..3); the
    JETTY - a horizontal log plate 1 proud at the floor line (y 4) on timber brackets; the upper storey's FRAME 1
    proud (z 1, y 5..7: posts every 3 cells and at the corners) with the render infill set back between (z 0), a
    glazed middle bay, knee braces (stairs) in the outer bays; a top plate (y 8)."""
    p = blank()
    posts = sorted(set(range(0, L, 3)) | {L - 1})
    for x in range(L):
        p.set(x, 0, 0, m.trim.full)
        for y in (1, 2, 3):
            p.set(x, y, 0, m.wall.full)
        p.set(x, 4, 1, m.timber.log, axis="x")
        p.set(x, 8, 1, m.timber.log, axis="x")
        p.set(x, 4, 0, m.timber.planks)
        p.set(x, 8, 0, m.timber.planks)
        for y in (5, 6, 7):
            if x in posts:
                p.set(x, y, 1, m.timber.log, axis="y")
                p.set(x, y, 0, m.render)
            else:
                p.set(x, y, 0, m.render)
    for x in posts:
        stair(p, x, 3, 1, m.timber.stairs, "north", "top")
    bays = [(a + 1, b - 1) for a, b in zip(posts, posts[1:]) if b - a > 1]
    for i, (a, b) in enumerate(bays):
        if i == len(bays) // 2 and len(bays) >= 3:
            for x in range(a, b + 1):
                for y in (6, 7):
                    p.set(x, y, 0, m.glass[0])
        else:
            stair(p, a, 7, 1, m.timber.stairs, "west", "top")
            if b != a:
                stair(p, b, 7, 1, m.timber.stairs, "east", "top")
    return p


def tone_wall(m, L, H, seed=0):
    """R15: a wall face x 0..L-1, y 0..H-1 whose second tone goes where the structure and the water are - a pale
    plinth course (y 0), ONE whole band course of the second tone under the top (y H-1: the course under a lip),
    and wet runs of 2 at the wall foot (y 1) - never scattered, never a chequer; the second tone stays 10-25 % of
    the body."""
    rng = random.Random(seed)
    p = blank()
    for x in range(L):
        p.set(x, 0, 0, m.trim.full)
        for y in range(1, H):
            p.set(x, y, 0, m.wall2.full if y == H - 1 else m.wall.full)
    body = L * (H - 1)
    room = int(0.25 * body) - L
    x = rng.randint(0, 3)
    while room >= 2 and x <= L - 2:
        p.set(x, 1, 0, m.wall2.full)
        p.set(x + 1, 1, 0, m.wall2.full)
        room -= 2
        x += rng.randint(4, 8)
    return p


def machinery(m, L=9):
    """R16: a greeble band against a wall (the part's z 1 is the first cell in front of the wall plane): every
    greeble is a run + a joint + a fitting - a dark pipe RUN along y 3, ELBOWS (full blocks) at both ends, a drop
    to the ground at x 0, a branch down to a 2 x 2 metal TANK with a VALVE (lever) on it beside the joint, a
    cauldron, a pipe bracket, a lightning-rod VENT on the far elbow."""
    p = blank()
    for x in range(1, L - 1):
        p.set(x, 3, 1, m.dark.wall)
    p.set(0, 3, 1, m.dark.full)
    p.set(L - 1, 3, 1, m.dark.full)
    for y in range(3):
        p.set(0, y, 1, m.dark.wall)
    t = L - 4
    for x in (t, t + 1):
        for y in (0, 1):
            p.set(x, y, 1, m.metal.full)
    p.set(t, 2, 1, m.dark.full)
    p.set(t + 1, 2, 1, "lever", face="floor", facing="south", powered="false")
    p.set(t + 2, 0, 1, "cauldron")
    stair(p, 2, 2, 1, m.trim.stairs, "north", "top")
    p.set(L - 1, 4, 1, "lightning_rod", facing="up", powered="false", waterlogged="false")
    return p


def gear(m, r=2):
    """R16: brass_lab_parts.flywheel in the set (a metal rim, bar spokes, a dark hub, its axle in a timber bearing)."""
    return recolour(bp.flywheel(r), _brass_map(m))


# ============================================================================================ the demo bay
def _facade(m, L, kind="tall"):
    """A wall run x 0..L-1 (plane z 0, the building's y 1 = row 0): a pale plinth course, a string course, bays of
    pier + window (piers on every bay line and both ends), a cornice, a frieze course at the eave."""
    W, H = KINDS[kind]
    bayw = W + 1
    p = blank()
    for x in range(L):
        p.set(x, 0, 0, m.trim.full)
    p.place(string_course(m, L), 0, 1, 0)
    for x in range(L):
        if x % bayw == 0 or x == L - 1:
            p.place(pier(m, H, lantern=True), x, 2, 0)
    for x0 in range(1, L - W + 1, bayw):
        w = window(m, kind)
        for (x, y, z), v in w.b.items():
            p.b[(x0 + x, 2 + y, z)] = v
    p.place(cornice(m, L), 0, 2 + H, 0)
    for x in range(L):
        p.set(x, 5 + H, 0, m.wall.full)
    return p


def demo_bay(m, w=10, d=7, plain=False):
    """A small hall x 0..w-1, z 0..d-1 on a dark foundation (y 0): four kit facades (plinth, string course, pilastered
    piers with lanterns, tall windows, cornice, frieze) under an R2 oversailing roof_kit gable (ridge along x, R3
    cresting). plain: the same massing with bare walls - the red case."""
    p = blank()
    for x in range(w):
        for z in range(d):
            p.set(x, 0, z, m.dark.full)
    _, H = KINDS["tall"]
    top = 5 + H                                      # the facade's frieze row; the building's y = top + 1
    if plain:
        for x in range(w):
            for z in range(d):
                if x in (0, w - 1) or z in (0, d - 1):
                    for y in range(1, top + 2):
                        p.set(x, y, z, m.wall.full)
    else:
        p.place(_facade(m, w), 0, 1, d - 1, 0)       # south
        p.place(_facade(m, d), 0, 1, 0, 1)           # west
        p.place(_facade(m, w), w - 1, 1, 0, 2)       # north
        p.place(_facade(m, d), w - 1, 1, d - 1, 3)   # east
    p.place(cresting(oversail(m, d, w), m, every=2), 0, top + 2, 0)
    return p


# ============================================================================================ the catalogue
CATALOGUE = [
    ("eave", "R1", lambda m: eave(m, 7)),
    ("oversail", "R2", lambda m: oversail(m, 7, 9, drop=5)),
    ("cresting", "R3", lambda m: cresting(oversail(m, 7, 9), m)),
    ("hip_roof", "R3", lambda m: hip_roof(m, 7)),
    ("dormer", "R4", dormer),
    ("clerestory", "R5", lambda m: clerestory(m, 10)),
    ("roof_light", "R5", lambda m: roof_light(m, 9)),
    ("cornice", "R6", lambda m: cornice(m, 8)),
    ("string_course", "R6", lambda m: string_course(m, 8)),
    ("pier", "R7", lambda m: pier(m, 5, lantern=True)),
    ("window_tall", "R8", lambda m: window(m, "tall")),
    ("window_medium", "R8", lambda m: window(m, "medium")),
    ("window_small", "R8", lambda m: window(m, "small")),
    ("bay_tall", "R8", lambda m: bay(m, "tall")),
    ("portal", "R9", portal),
    ("corbel_stage", "R10", lambda m: corbel_stage(m, 5)),
    ("chimney", "R11", lambda m: chimney(m, 10, roof_y=4)),
    ("spire", "R12", spire),
    ("dome", "R12", lambda m: dome(m, 4)),
    ("balustrade", "R13", lambda m: balustrade(m, 9)),
    ("parapet", "R13", lambda m: balustrade(m, 9, panel="solid")),
    ("half_timber", "R14", lambda m: half_timber(m, 10)),
    ("tone_wall", "R15", lambda m: tone_wall(m, 12, 6)),
    ("machinery", "R16", lambda m: machinery(m, 9)),
    ("gear", "R16", lambda m: gear(m, 2)),
]
