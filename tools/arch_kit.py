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

Reuse: R11 chimney wraps brass_lab_parts.stack's shaft, R12 dome wraps gothic_parts.ribbed_dome (recoloured into
the set; the r1 gear wrapped brass_lab_parts.flywheel, whose bars read as a pile at RTS zoom - r2 draws its own);
R2/R3 roofs are tools/roof_kit.py courses (the kit adds the gable infill, verge brackets, downpipes and the ridge
comb round them, never its own tiling).

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
    rib: Fam           # dark iron: glazing ribs, purlins, wheels (r3: fresh orange copper is out of palette)
    glaze: str         # roof glass: a light-grey / tinted pane (clear panes in a roof read as scaffolding)
    pier: str          # pier body: never a glaring white (r1 sheet: pure quartz piers)


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
    dict(R.SLATE, gutter="waxed_weathered_cut_copper_stairs"),
    Fam("waxed_weathered_cut_copper", "waxed_weathered_cut_copper_stairs", "waxed_weathered_cut_copper_slab"),
    ("glass_pane", "glass"), ("lantern", "shroomlight"),
    Timber("stripped_dark_oak_log", "dark_oak_planks", "dark_oak_stairs", "dark_oak_slab", "dark_oak_trapdoor",
           "dark_oak_fence"),
    "white_terracotta",
    Fam("polished_blackstone", "polished_blackstone_stairs", "polished_blackstone_slab", "polished_blackstone_wall"),
    "light_gray_stained_glass_pane", "bricks")

SUNFORGED_ROOF = {   # the angels' deep roofs: dark slate courses, a green-copper ridge, gilt-free (gold is the accent)
    "tones": (("deepslate_tiles", "deepslate_tile_stairs", "deepslate_tile_slab"),
              ("polished_deepslate", "polished_deepslate_stairs", "polished_deepslate_slab"),
              ("deepslate_bricks", "deepslate_brick_stairs", "deepslate_brick_slab")),
    "ridge": ("waxed_oxidized_cut_copper",),
    "verge": ("waxed_oxidized_cut_copper", "waxed_oxidized_cut_copper_stairs", "waxed_oxidized_cut_copper_slab"),
    "gutter": "waxed_weathered_cut_copper_stairs",
}

SUNFORGED = Mats(    # the angels: white quartz walls, warm sandstone dressings, a DARK slate third tone (r3: washed out)
    "sunforged",
    Fam("smooth_quartz", "smooth_quartz_stairs", "smooth_quartz_slab", "diorite_wall"),
    Fam("polished_diorite", "polished_diorite_stairs", "polished_diorite_slab", "diorite_wall"),
    Fam("smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab", "sandstone_wall"),
    Fam("polished_deepslate", "polished_deepslate_stairs", "polished_deepslate_slab", "polished_deepslate_wall"),
    SUNFORGED_ROOF,
    Fam("waxed_oxidized_cut_copper", "waxed_oxidized_cut_copper_stairs", "waxed_oxidized_cut_copper_slab"),
    ("yellow_stained_glass_pane", "yellow_stained_glass"), ("lantern", "glowstone"),
    Timber("stripped_birch_log", "birch_planks", "birch_stairs", "birch_slab", "birch_trapdoor", "birch_fence"),
    "calcite",
    Fam("deepslate_tiles", "deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tile_wall"),
    "white_stained_glass_pane", "calcite")

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
    "bone_block",
    Fam("polished_blackstone", "polished_blackstone_stairs", "polished_blackstone_slab", "polished_blackstone_wall"),
    "gray_stained_glass_pane", "deepslate_bricks")

SETS = (BRASSWORKS, SUNFORGED, GRAVEBOUND)
UNIVERSAL = {"iron_bars", "chain", "lever", "cauldron", "lightning_rod", "campfire", "grindstone"}
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


def cresting(roof, m, every=4):
    """R3: a copy of `roof` whose ridge is a PALE stone coping (trim) carrying a 1-block iron comb along its whole
    length: iron bars, a chain every `every` cells, copper lightning rods at both ends and every 2 * `every` (r1
    sheet: dark ridge blocks with a bar every other cell read as battlements)."""
    p = blank()
    p.b = dict(roof.b)
    ridge = sorted(q for q, (n, _) in roof.b.items() if n in m.roof["ridge"])
    if not ridge:
        return p
    along = 0 if len({q[0] for q in ridge}) > 1 else 2
    run = sorted(ridge, key=lambda q: q[along])
    for i, (x, y, z) in enumerate(run):
        p.set(x, y, z, m.trim.full)
        if i in (0, len(run) - 1) or i % (2 * every) == every:
            p.set(x, y + 1, z, "lightning_rod", facing="up", powered="false", waterlogged="false")
        elif i % every == 0:
            p.set(x, y + 1, z, "chain", axis="y", waterlogged="false")
        else:
            p.set(x, y + 1, z, "iron_bars")
    return p


def hip_roof(m, w, seed=0):
    """R3: a pyramid hip roof over a w x w tower (walls x, z 0..w-1), 1 proud all round, in slab pairs (26.5 deg).
    Every face cell is the roof's base tone T0; the HIPS are a 1-wide line of the dark T1 down the diagonals and the
    eave ring is T1 too (wet), so the roof reads as four planes meeting on dark lines - no timber anywhere (r2: a
    brown X in a brown frame) and no chequer (G6). A ridge-block apex with a lightning-rod finial, gutters under the
    eaves. `seed` is kept for the generators' call signature (the courses are structural, not random)."""
    p = blank()
    lo, hi = -1, w
    c = (w - 1) / 2
    t0, t1 = m.roof["tones"][0][2], m.roof["tones"][1][2]
    for x in range(lo, hi + 1):
        for z in range(lo, hi + 1):
            d = min(x - lo, hi - x, z - lo, hi - z)
            y, typ = d // 2, ("bottom" if d % 2 == 0 else "top")
            hip = abs(x - c) == abs(z - c) and (x, z) != (c, c)
            slab(p, x, y, z, t1 if (hip or d == 0) else t0, typ)
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
    """R4: a 3-wide dormer eye x -1..1 that rides up through a slope behind it and reads as a little roofed house: a
    pale sill course with a proud sill slab, 3 x 2 panes set one back between timber cheeks (x -2 / 2), a lantern
    behind the glass; over the head a GABLE in the roof's own stairs - eave stairs 1 past the cheeks (x -3 / 3), then
    rake rows narrowing to a ridge block and a slab cap - oversailing the face by 1 (its own eaves) and running back
    3 cells into the main slope (r2: a box)."""
    p = blank()
    t0, t1 = m.roof["tones"][0], m.roof["tones"][1]
    for x in range(-2, 3):
        p.set(x, 0, 0, m.trim.full)
    for x in range(-1, 2):
        slab(p, x, 0, 1, m.trim.slab, "top")
        for y in (1, 2):
            p.set(x, y, 0, "air")
            p.set(x, y, -1, m.glass[0])
        p.set(x, 3, 0, m.wall2.full)
    for z in range(-3, 2):
        if z <= 0:
            for x in (-2, 2):
                for y in (1, 2, 3):
                    p.set(x, y, z, m.timber.planks)
            for x in (-1, 0, 1):
                p.set(x, 3, z, m.wall2.full)
        stair(p, -3, 3, z, t1[1], "east")
        stair(p, 3, 3, z, t1[1], "west")
        stair(p, -2, 4, z, t0[1], "east")
        stair(p, 2, 4, z, t0[1], "west")
        if z <= 0:
            p.set(-1, 4, z, m.wall2.full)
            p.set(1, 4, z, m.wall2.full)
            p.set(0, 4, z, m.wall2.full)
        stair(p, -1, 5, z, t0[1], "east")
        stair(p, 1, 5, z, t0[1], "west")
        p.set(0, 5, z, m.roof["ridge"][0])
        slab(p, 0, 6, z, t1[2])
    p.set(0, 0, -2, m.trim.full)
    _lantern(p, 0, 1, -2, m)
    return p


def dormer_on_roof(m, seed=0):
    """A dormer standing in a roof_kit slope (for the sheet and the silhouette test): the slope rises north (-z) from
    an eave at z 1; the dormer's sill sits on the slope at z -2 and its gable breaks the roof line."""
    p = blank()
    roof = R.gable(9, 9, axis="x", seed=seed, mats=m.roof)
    for (x, y, z), v in roof.items():
        if z <= 4:
            p.b[(x - 4, y, z - 4)] = v
    for (x, y, z), v in dormer(m).b.items():
        p.b[(x, y + 1, z - 2)] = v
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
    2 in) glazed in the set's tinted GLAZE panes, a dark-iron RIB across the whole profile every 3rd cell (and at the
    far end), a dark PURLIN along the crown; weathered copper only at the eave course (y 0). r1: a clear-glass grid
    read as scaffolding, verdigris ribs as vines; r2: fresh orange copper ribs were out of palette."""
    prof = {-3: (0, 1), -2: (2,), -1: (3,), 0: (3,), 1: (3,), 2: (2,), 3: (0, 1)}
    p = blank()
    for x in range(L):
        rib = x % 3 == 0 or x == L - 1
        for z, ys in prof.items():
            for y in ys:
                if y == 0:
                    name = m.metal.full
                elif rib or z == 0:
                    name = m.rib.full
                else:
                    name = m.glaze
                p.set(x, y, z, name)
    return p


# ============================================================================================ R6-R9 walls and openings
def cornice(m, L):
    """R6: a 3-row cornice in the PALE trim (never the wall's own material) that steps out as it rises: y 0 a flush
    second-tone course with pale DENTILS half proud every other cell; y 1 a run of upside-down pale stairs 1 proud
    (the bracket - its shadow is the point); y 2 a pale slab ledge over them, 1 proud, on a pale course. Bracket and
    ledge run one cell past each end so runs meeting at a corner close it."""
    p = blank()
    for x in range(L):
        p.set(x, 0, 0, m.wall2.full)
        if x % 2 == 0:
            p.set(x, 0, 1, m.trim.wall)
        p.set(x, 1, 0, m.wall.full)
        p.set(x, 2, 0, m.trim.full)
    for x in range(-1, L + 1):
        stair(p, x, 1, 1, m.trim.stairs, "north", "top")
        slab(p, x, 2, 1, m.trim.slab, "bottom")
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
    bottom, y h-1 top: a bulge); the pier body in the set's pier stone banded with the second tone every 2nd row.
    lantern: a lantern standing on an upside-down stair bracket in front of the pilaster."""
    p = blank()
    p.set(0, 0, 0, m.trim.full)
    p.set(0, 0, 1, m.trim.full)
    for y in range(1, h):
        p.set(0, y, 0, m.wall2.full if y % 2 == 0 and y < h - 1 else m.pier)
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
    reveal is air at z 0, the glass panes sit one back (z -1) in front of a backing, the SILL a pale stone slab (top)
    1 proud, the HEAD the other stone: tall (2 x 3 panes) = springer stairs curving off the jambs; medium (3 x 2) =
    a flush head course over paired lights split by a thin pale mullion; small (1 x 2) = a stair head. Never doors or
    shutters (r1 sheet: open trapdoors read as doors). lit: a lantern 2 back behind the glass."""
    W, H = KINDS[kind]
    p = blank()
    top = H - 2
    for x in range(W):
        p.set(x, 0, 0, m.wall.full)
        p.set(x, 0, -1, m.wall.full)
        slab(p, x, 0, 1, m.trim.slab, "top")
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
    narrower each side, each in its own NON-pale stone (dark -> wall -> second tone) so the pale reveals read against
    it (r2: pale on pale). Every order's
    opening is lined with PALE stairs - jamb stairs stepping down into the opening (a chamfered reveal) and an
    upside-down stair soffit under its head - so the arch reads as three concentric pale rings, from the front and
    from above (r1 sheet: a square hole). Over the outer order a stepped pale HOOD (a little pediment, 3 rows); the
    door plane (timber, a centre post) behind the last order; a pale step row 1 proud across the whole front,
    lanterns on pale posts each side."""
    p = blank()
    mats = [m.dark, m.wall, m.wall2] + [m.wall2] * max(0, orders - 3)      # never pale: the reveals are
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
        for y in range(1, hk[k]):                                   # the pale reveal down both jambs
            stair(p, -half[k] - 1, y, z, m.trim.stairs, "west")
            stair(p, half[k] + 1, y, z, m.trim.stairs, "east")
        for x in range(-half[k], half[k] + 1):                      # the soffit under the head
            stair(p, x, hk[k], z, m.trim.stairs, "north", "top")
        stair(p, -half[k], hk[k] - 1, z, m.trim.stairs, "west", "top")    # springers
        stair(p, half[k], hk[k] - 1, z, m.trim.stairs, "east", "top")
    for r in range(3):                                              # the stepped hood over the outer order
        y = top + 1 + r
        a = X - 1 - r
        for x in range(-a, a + 1):
            if abs(x) == a and a > 0:
                stair(p, x, y, 0, m.trim.stairs, "east" if x < 0 else "west")
            elif r == 2:
                slab(p, x, y, 0, m.trim.slab)
            else:
                p.set(x, y, 0, m.trim.full)
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
    for s_ in (-1, 1):
        x = s_ * (X + 1)
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
    """R11: brass_lab_parts.stack's shaft (y 0..h-1, its corbelled cap ring at h-1) in the set's materials, plus: a
    plinth a block wider with thin corners, THIN CORNER walls up the shaft so it reads octagonal, a PALE band at h-3,
    and a new crown: the fire SEALED inside the flue at h-1 (walled on all four sides), air over it, a vent course of
    thin dark corner posts with open sides, a dark slab CAP over the whole flue - smoke only, no flame to see (r1
    sheet: an exposed campfire on the stack). At `roof_y`: a pale stair flashing skirt round it, a metal band above."""
    p = recolour(bp.stack(h, w), _brass_map(m))
    for q in [q for q in p.b if q[1] >= h]:
        del p.b[q]
    c = w // 2
    for y in range(2, h - 3):
        for x, z in ((0, 0), (0, w - 1), (w - 1, 0), (w - 1, w - 1)):
            p.set(x, y, z, m.dark.wall if y % 4 == 0 else m.wall.wall)
    for x in range(w):
        for z in range(w):
            if x in (0, w - 1) or z in (0, w - 1):
                p.set(x, h - 3, z, m.trim.full)
    for y in (0, 1):
        for x in range(-1, w + 1):
            for z in range(-1, w + 1):
                if x in (-1, w) or z in (-1, w):
                    corner = x in (-1, w) and z in (-1, w)
                    p.set(x, y, z, m.dark.wall if corner else m.dark.full)
    p.set(c, h - 2, c, m.trim.full)                                # the flue floor
    p.set(c, h - 1, c, "campfire", lit="true", facing="south", signal_fire="false", waterlogged="false")
    for x in range(w):
        for z in range(w):
            corner = x in (0, w - 1) and z in (0, w - 1)
            if corner:
                p.set(x, h, z, m.dark.wall)
    for x in range(-1, w + 1):                                   # a pale ring under the cap, proud (r2: a dark mushroom)
        for z in range(-1, w + 1):
            if x in (-1, w) or z in (-1, w):
                slab(p, x, h, z, m.trim.slab, "top")
    for x in range(w):
        for z in range(w):
            slab(p, x, h + 1, z, m.dark.slab)
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
    """R12: a needle spire on the axis (0, 0), ~22 tall: a 5 x 5 tower stub (y 0..3, a pale base course, a lit slit
    each face), a CORBEL ring (upside-down dark stairs at y 4) carrying an oversailing pale course 7 x 7 (y 5),
    then the needle in the set's metal: a 7 x 7 broach for 2 rows, 5 x 5 for 4, 3 x 3 for 6, 1 x 1 for 6, each
    step-in finished with a ring of stairs (tall back to the axis), dark-iron rib cells on the meridians, a dark collar under the tip, a lightning rod (r2: a stepped pyramid on
    a cube)."""
    p = blank()
    for y in range(4):
        for x, z in _ring(2):
            p.set(x, y, z, m.trim.full if y == 0 else m.wall.full)
    for f, (x, z) in (("south", (0, 2)), ("north", (0, -2)), ("east", (2, 0)), ("west", (-2, 0))):
        p.set(x, 2, z, m.glass[0])
    p.set(0, 1, 0, m.trim.full)
    _lantern(p, 0, 2, 0, m)
    for x, z in _ring(3):
        if max(abs(x), abs(z)) == 3 and not (abs(x) == 3 and abs(z) == 3):
            stair(p, x, 4, z, m.dark.stairs, gp.toward(x, z), "top")
    for x, z in _ring(2) + _ring(1) + [(0, 0)]:
        p.set(x, 4, z, m.wall.full)
    for x, z in _ring(3) + _ring(2) + _ring(1) + [(0, 0)]:
        p.set(x, 5, z, m.trim.full)
    y = 6
    for w, rows in ((3, 2), (2, 4), (1, 6), (0, 6)):
        for k in range(rows):
            for x, z in (_ring(w) if w else [(0, 0)]):
                if k == rows - 1 and w > 0:
                    stair(p, x, y, z, m.metal.stairs, gp.toward(x, z))
                else:
                    p.set(x, y, z, m.rib.full if (w and (x == 0 or z == 0) and k % 3 == 1) else m.metal.full)
            for q in range(w - 1, -1, -1):
                for x, z in (_ring(q) if q else [(0, 0)]):
                    p.set(x, y, z, m.metal.full)
            y += 1
    p.set(0, y, 0, m.dark.wall)
    p.set(0, y + 1, 0, "lightning_rod", facing="up", powered="false", waterlogged="false")
    return p


def dome(m, r=4):
    """R12: a ribbed dome on a stone drum, axis (0, 0). The DRUM (y 0..4): a dark base ring, the wall stone banded in
    the second tone with lit windows on the 8 meridians, a pale stair cornice 1 proud (y 5) and a pale slab ring. The
    DOME (y 6..): a hemisphere in the roof's base tone with 8 continuous meridian RIBS - exactly one pale cell per
    course on each meridian, so every rib is a connected line from the drum to the crown - and a metal crown with a
    lantern and lightning rod; 4 thin pinnacles on the diagonals of the cornice (r2: a chequered 'panda ball' with
    10 candles on a log-and-brick cake)."""
    p = blank()
    D = r + 1
    rows = {}
    for x in range(-D, D + 1):
        for z in range(-D, D + 1):
            d = math.hypot(x, z)
            if d < r + 0.5:
                rows.setdefault("in", []).append((x, z))
            if r - 0.5 <= d < r + 0.5:
                rows.setdefault("rim", []).append((x, z))
            if r + 0.5 <= d < r + 1.5:
                rows.setdefault("out", []).append((x, z))
    mer = {k: (round(math.cos(k * math.pi / 4) * r), round(math.sin(k * math.pi / 4) * r)) for k in range(8)}
    for y in range(5):
        for (x, z) in rows["rim"]:
            if y == 0:
                n = m.dark.full
            elif (x, z) in mer.values() and y in (2, 3):
                n = m.glass[0]
            else:
                n = m.wall2.full if y == 1 else m.wall.full
            p.set(x, y, z, n)
        for (x, z) in rows["in"]:
            if (x, z) not in rows["rim"] and y in (0, 4):
                p.set(x, y, z, m.wall.full)
    p.set(0, 1, 0, m.wall.full)
    _lantern(p, 0, 2, 0, m)
    for (x, z) in rows["out"]:
        stair(p, x, 5, z, m.trim.stairs, gp.toward(x, z), "top")
    for (x, z) in rows["in"]:
        p.set(x, 5, z, m.trim.full)
    for k in (1, 3, 5, 7):                                   # 4 pinnacles on the diagonals
        a = k * math.pi / 4
        x, z = round(math.cos(a) * (r + 1)), round(math.sin(a) * (r + 1))
        p.set(x, 6, z, m.trim.wall)
        p.set(x, 7, z, "lightning_rod", facing="up", powered="false", waterlogged="false")
    y0 = 6
    t0 = m.roof["tones"][0][0]
    radii = [r, r] + [rr for k in range(r - 1, 1, -1) for rr in (k, k)][:2 * (r - 2) - 1]   # 4 4 3 3 2
    for k, rr in enumerate(radii):
        y = y0 + k
        ribs = {(round(math.cos(j * math.pi / 4) * rr), round(math.sin(j * math.pi / 4) * rr)) for j in range(8)}
        for x in range(-rr, rr + 1):
            for z in range(-rr, rr + 1):
                if math.hypot(x, z) < rr + 0.5:
                    p.set(x, y, z, m.trim.full if (x, z) in ribs else t0)
    top = y0 + len(radii) - 1
    for (x, z) in [(0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)]:
        p.set(x, top + 1, z, m.metal.full)
    p.set(0, top + 2, 0, m.metal.full)
    p.set(0, top + 3, 0, "lightning_rod", facing="up", powered="false", waterlogged="false")
    return p


# ============================================================================================ R13-R16 railings ... machinery
def balustrade(m, L, every=4, panel="balusters"):
    """R13: a pale bottom rail (y 0) along x 0..L-1; at y 1 the panel - stone BALUSTERS (thin pale walls) every
    other cell with air between, or an iron-bar panel ("bars"), or a solid wall-material parapet ("solid"); a pale
    slab top rail (y 2); NEWEL posts (pale walls) every `every` cells and at both ends standing through the rail
    (r1 sheet: the solid run read as a row of boxes)."""
    p = blank()
    for x in range(L):
        p.set(x, 0, 0, m.trim.full)
        newel = x % every == 0 or x == L - 1
        if newel:
            p.set(x, 1, 0, m.trim.wall)
            p.set(x, 2, 0, m.trim.wall)
            continue
        if panel == "bars":
            p.set(x, 1, 0, "iron_bars")
        elif panel == "solid":
            p.set(x, 1, 0, m.wall.full)
        elif x % 2 == 0:
            p.set(x, 1, 0, m.trim.wall)
        slab(p, x, 2, 0, m.trim.slab)
    return p


def half_timber(m, L):
    """R14: a two-storey front x 0..L-1. Lower storey: a pale base course and the wall material (z 0, y 1..3) - the
    brick shows. The JETTY: a horizontal log plate 1 proud at the floor line (y 4) carried on upside-down timber stair
    BRACKETS under every post. Upper storey (y 5..7): the timber is the FRAME, not the fill - 1-wide posts (z 1)
    every 4 cells and at the corners, the pale RENDER infill one block back between them (timber <= 30 % of the
    panel; the jetty brackets are the only braces), glass panes in the middle bay; a log top plate (y 8)
    under a pale slab coping (r1: a timber box read as a dark crate; r2: timber was still ~60 % of the panel)."""
    p = blank()
    posts = sorted(set(range(0, L, 4)) | {L - 1})
    if L - 1 - (posts[-2] if len(posts) > 1 else 0) < 2:
        posts.remove(posts[-2])
    for x in range(L):
        p.set(x, 0, 0, m.trim.full)
        for y in (1, 2, 3):
            p.set(x, y, 0, m.wall.full)
        p.set(x, 4, 1, m.timber.log, axis="x")
        p.set(x, 4, 0, m.timber.planks)
        p.set(x, 8, 1, m.timber.log, axis="x")
        p.set(x, 8, 0, m.render)
        slab(p, x, 9, 0, m.trim.slab)
        slab(p, x, 9, 1, m.trim.slab)
        for y in (5, 6, 7):
            p.set(x, y, 0, m.render)
            if x in posts:
                p.set(x, y, 1, m.timber.log, axis="y")
    for x in posts:
        stair(p, x, 3, 1, m.timber.stairs, "north", "top")
    bays = [(a + 1, b - 1) for a, b in zip(posts, posts[1:]) if b - a > 1]
    a, b = bays[len(bays) // 2 if len(bays) >= 3 else 0]
    for x in range(a, b + 1):                                     # the glazed bay
        for y in (6, 7):
            p.set(x, y, 0, m.glass[0])
    return p


def tone_wall(m, L, H, seed=0):
    """R15: a wall face x 0..L-1, y 0..H-1 with its tones where the water goes (r2: pale patches mid-wall). A dark
    plinth (y 0); the darker second tone in WET runs of 2-3 at the foot (y 1), where splash and damp rise; a window
    sill (pale slab 1 proud) every 5 cells at y 3 with a second-tone DRIP cell under each; the body plain; the top
    course (y H-1) pale, bleached under the lip. Never scattered, never a chequer; the second tone 10-25 % of the
    body (y 1..H-2)."""
    rng = random.Random(seed)
    p = blank()
    for x in range(L):
        p.set(x, 0, 0, m.dark.full)
        for y in range(1, H):
            p.set(x, y, 0, m.trim.full if y == H - 1 else m.wall.full)
    sills = list(range(2, L - 1, 5))
    for x in sills:
        slab(p, x, 3, 1, m.trim.slab, "top")
        p.set(x, 2, 0, m.wall2.full)
    near = {c + d for c in sills for d in (-1, 0, 1)}
    segs, run = [], []
    for x in range(L + 1):
        if x < L and x not in near:
            run.append(x)
        else:
            if len(run) >= 2:
                segs.append(run)
            run = []
    rng.shuffle(segs)
    need = int(0.12 * L * (H - 2)) - len(sills)
    for seg in segs:
        if need <= 0:
            break
        k = max(2, min(len(seg), need))
        a = rng.randint(0, len(seg) - k)
        for c in seg[a:a + k]:
            p.set(c, 1, 0, m.wall2.full)
        need -= k
    return p


def machinery(m, L=9):
    """R16: a machinery band against a wall (z 1 is the first cell in front of the wall plane), 5 high: a dark
    HOUSING plinth along the foot (y 0, slab top), a pipe RUN along y 3 with ELBOWS at both ends and a drop to the
    housing at x 0, a 2 x 2 dark-metal TANK on the housing with a weathered-copper band and a VALVE (lever) on the
    run beside its joint, a cauldron, a grindstone pump, a second thin pipe at y 4, a lightning-rod VENT on the far
    elbow - one machine, not a few sticks (r2: unreadable at RTS zoom)."""
    p = blank()
    for x in range(L):
        p.set(x, 0, 1, m.dark.full)
    for x in range(1, L - 1):
        p.set(x, 3, 1, m.dark.wall)
        if x % 2 == 0:
            p.set(x, 4, 1, "chain", axis="x", waterlogged="false")
    p.set(0, 3, 1, m.dark.full)
    p.set(L - 1, 3, 1, m.dark.full)
    p.set(0, 4, 1, m.dark.full)
    p.set(L - 1, 4, 1, m.dark.full)
    for y in (1, 2):
        p.set(0, y, 1, m.dark.wall)
    t = 2
    for x in (t, t + 1):
        p.set(x, 1, 1, m.rib.full)
        p.set(x, 2, 1, m.metal.full)
    p.set(t + 2, 2, 1, "lever", face="floor", facing="south", powered="false")
    p.set(t + 2, 1, 1, m.dark.wall)
    p.set(t + 3, 1, 1, m.dark.wall)
    p.set(t + 3, 2, 1, m.dark.wall)
    p.set(t + 4, 1, 1, "cauldron")
    p.set(t + 5, 1, 1, "grindstone", face="floor", facing="south")
    p.set(t + 5, 2, 1, m.dark.wall)
    p.set(L - 1, 5, 1, "lightning_rod", facing="up", powered="false", waterlogged="false")
    return p


def gear(m, r=3):
    """R16: ONE big wheel in front of a machinery band (r2: a lone orange donut). The band (z 1, against the wall):
    a dark housing course along the foot, a pipe run across behind the wheel's axle, longer than the wheel, with
    elbows. The wheel (z 2): a rim of dark-iron blocks on the four axes and dark-iron STAIRS between them turned so it
    reads round, thin chain spokes on the four axes, a GRINDSTONE hub on a dark bearing block behind it."""
    p = blank()
    L = 2 * r + 5
    for x in range(-L // 2, L // 2 + 1):
        p.set(x, -r - 1, 1, m.dark.full)
        p.set(x, 0, 1, m.dark.wall)
    p.set(-L // 2, 0, 1, m.dark.full)
    p.set(L // 2, 0, 1, m.dark.full)
    p.set(0, 0, 1, m.dark.full)
    for x in range(-r - 1, r + 2):
        for y in range(-r - 1, r + 2):
            d = math.hypot(x, y)
            if r - 0.5 <= d < r + 0.6:
                if x == 0 or y == 0:
                    p.set(x, y, 2, m.rib.full)
                else:
                    stair(p, x, y, 2, m.rib.stairs, "west" if x > 0 else "east", "bottom" if y > 0 else "top")
    for k in range(1, r):
        for (x, y, ax) in ((k, 0, "x"), (-k, 0, "x"), (0, k, "y"), (0, -k, "y")):
            p.set(x, y, 2, "chain", axis=ax, waterlogged="false")
    p.set(0, 0, 2, "grindstone", face="wall", facing="south")
    return p


# ============================================================================================ the demo bay
def _facade(m, L, kind="tall"):
    """A wall run x 0..L-1 (plane z 0, the building's y 1 = row 0): a pale plinth course, a flush second-tone band
    under the sill line (the proud sills are its drip), bays of pier + window (piers on every bay line and both
    ends), a pale cornice, a frieze course at the eave."""
    W, H = KINDS[kind]
    bayw = W + 1
    p = blank()
    for x in range(L):
        p.set(x, 0, 0, m.trim.full)
    for x in range(L):                  # a flush second-tone band: the sills above it are the drip (no doubled ledge)
        p.set(x, 1, 0, m.wall2.full)
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
    p.place(cresting(oversail(m, d, w), m), 0, top + 2, 0)
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
    ("gear", "R16", lambda m: gear(m, 3)),
]
