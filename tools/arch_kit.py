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
import building_gates as G  # noqa: E402
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
    reveal: Fam        # base courses and opening reveals: the tone that reads AGAINST the wall (r3: Sunforged beige on
    #                    beige - its pale sandstone dressings vanished on the pale quartz)
    weather: tuple     # (stain, moss): the wall's darker tones where water runs, a gradient to the foot (R15)


GRAVE_ROOF = {
    "tones": (("blackstone", "blackstone_stairs", "blackstone_slab"),
              ("polished_blackstone", "polished_blackstone_stairs", "polished_blackstone_slab"),
              ("polished_blackstone_bricks", "polished_blackstone_brick_stairs", "polished_blackstone_brick_slab")),
    "ridge": ("chiseled_polished_blackstone",),
    "verge": ("polished_deepslate", "polished_deepslate_stairs", "polished_deepslate_slab"),
    "gutter": "waxed_oxidized_cut_copper_stairs",
}

SMOOTH = Fam("smooth_stone", "stone_brick_stairs", "smooth_stone_slab", "stone_brick_wall")   # pale grey cut stone
#   (r5 critic: calcite / diorite trim read as snow and marshmallow at every zoom - smooth stone and stone-brick shapes)
SLATE_VERGE = ("deepslate_bricks", "deepslate_brick_stairs", "deepslate_brick_slab")   # r5: dark oak verges read orange

BRASSWORKS = Mats(   # red brick, pale smooth-stone dressings, blue-brick (blackstone) dark, grey slate roofs, green copper
    "brassworks",
    Fam("bricks", "brick_stairs", "brick_slab", "brick_wall"),
    Fam("polished_granite", "polished_granite_stairs", "polished_granite_slab", "granite_wall"),
    SMOOTH,
    Fam("polished_blackstone_bricks", "polished_blackstone_brick_stairs", "polished_blackstone_brick_slab",
        "polished_blackstone_brick_wall"),
    dict(R.SLATE, gutter="waxed_weathered_cut_copper_stairs", verge=SLATE_VERGE),
    Fam("waxed_weathered_cut_copper", "waxed_weathered_cut_copper_stairs", "waxed_weathered_cut_copper_slab"),
    ("glass_pane", "glass"), ("lantern", "shroomlight"),
    Timber("stripped_dark_oak_log", "dark_oak_planks", "dark_oak_stairs", "dark_oak_slab", "dark_oak_trapdoor",
           "dark_oak_fence"),
    "white_terracotta",
    Fam("polished_blackstone", "polished_blackstone_stairs", "polished_blackstone_slab", "polished_blackstone_wall"),
    "light_gray_stained_glass_pane", "bricks",
    SMOOTH,
    ("red_nether_bricks", "mossy_cobblestone"))

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
    Fam("smooth_quartz", "smooth_quartz_stairs", "smooth_quartz_slab", "sandstone_wall"),
    SMOOTH,
    Fam("smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab", "sandstone_wall"),
    Fam("polished_deepslate", "polished_deepslate_stairs", "polished_deepslate_slab", "polished_deepslate_wall"),
    SUNFORGED_ROOF,
    Fam("waxed_oxidized_cut_copper", "waxed_oxidized_cut_copper_stairs", "waxed_oxidized_cut_copper_slab"),
    ("yellow_stained_glass_pane", "yellow_stained_glass"), ("lantern", "glowstone"),
    Timber("stripped_dark_oak_log", "dark_oak_planks", "dark_oak_stairs", "dark_oak_slab", "dark_oak_trapdoor",
           "dark_oak_fence"),          # r4: dark posts on the pale render (birch on calcite was unreadable)
    "white_terracotta",               # r5: a warm lime render, never calcite (snow)
    Fam("deepslate_tiles", "deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tile_wall"),
    "white_stained_glass_pane", "smooth_stone",
    Fam("polished_deepslate", "polished_deepslate_stairs", "polished_deepslate_slab", "polished_deepslate_wall"),
    ("polished_andesite", "mossy_stone_bricks"))

GRAVEBOUND = Mats(   # deepslate walls, bone dressings, blackstone roofs, verdigris, sickly green light
    "gravebound",
    Fam("deepslate_bricks", "deepslate_brick_stairs", "deepslate_brick_slab", "deepslate_brick_wall"),
    Fam("cobbled_deepslate", "cobbled_deepslate_stairs", "cobbled_deepslate_slab", "cobbled_deepslate_wall"),
    Fam("smooth_quartz", "smooth_quartz_stairs", "smooth_quartz_slab", "end_stone_brick_wall"),
    Fam("polished_blackstone_bricks", "polished_blackstone_brick_stairs", "polished_blackstone_brick_slab",
        "polished_blackstone_brick_wall"),
    GRAVE_ROOF,
    Fam("waxed_oxidized_cut_copper", "waxed_oxidized_cut_copper_stairs", "waxed_oxidized_cut_copper_slab"),
    ("lime_stained_glass_pane", "lime_stained_glass"), ("soul_lantern", "verdant_froglight"),
    Timber("dark_oak_log", "dark_oak_planks", "dark_oak_stairs", "dark_oak_slab", "dark_oak_trapdoor",
           "dark_oak_fence"),
    "bone_block",
    Fam("polished_blackstone", "polished_blackstone_stairs", "polished_blackstone_slab", "polished_blackstone_wall"),
    "gray_stained_glass_pane", "deepslate_bricks",
    Fam("smooth_quartz", "smooth_quartz_stairs", "smooth_quartz_slab", "end_stone_brick_wall"),
    ("cracked_deepslate_bricks", "mossy_cobblestone"))

SETS = (BRASSWORKS, SUNFORGED, GRAVEBOUND)
GUTTER = "waxed_weathered_cut_copper_slab"   # r6: every set's eave gutter - weathered, never oxidised (r5: teal)
UNIVERSAL = {"iron_bars", "chain", "lever", "cauldron", "campfire", "grindstone", "tinted_glass", GUTTER}
#   never a lightning rod: fresh copper. tinted_glass: the roof lights' panel (r5: pale panes read as snow)
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


def _finial(p, x, y, z, m):
    """A dark iron finial (r3 critic: the orange lightning rods on every tip were out of palette)."""
    p.set(x, y, z, m.rib.wall)


def _disc(r):
    return [(x, z) for x in range(-r, r + 1) for z in range(-r, r + 1) if math.hypot(x, z) < r + 0.5]


def _rim(r):
    """The cells of _disc(r) on its outline (a 4-neighbour outside it)."""
    d = set(_disc(r))
    return [(x, z) for (x, z) in d if any((x + a, z + b) not in d for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))]


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
    pale LIP (trim slab, top) 1 proud at y 1 and the GUTTER on it at y 2 - a weathered cut copper slab in every set
    (r5: the set's oxidised metal stair read teal). Lip and gutter run one cell past each end so two runs meeting at a
    corner close it."""
    p = blank()
    for x in range(L):
        p.set(x, 0, 0, m.wall2.full)
        p.set(x, 1, 0, m.trim.full)
    for x in range(-1, L + 1):
        slab(p, x, 1, 1, m.trim.slab, "top")
        slab(p, x, 2, 1, GUTTER)
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
    length: iron bars, with a PALE post (the trim's wall) at both ends and every `every` cells - no full block and no
    dark post anywhere (r1 sheet: dark ridge blocks read as battlements; r4: the dark finial posts between the bars
    read as pigeons on the ridge)."""
    p = blank()
    p.b = dict(roof.b)
    ridge = sorted(q for q, (n, _) in roof.b.items() if n in m.roof["ridge"])
    if not ridge:
        return p
    along = 0 if len({q[0] for q in ridge}) > 1 else 2
    run = sorted(ridge, key=lambda q: q[along])
    for i, (x, y, z) in enumerate(run):
        p.set(x, y, z, m.trim.full)
        p.set(x, y + 1, z, m.trim.wall if i % every == 0 or i == len(run) - 1 else "iron_bars")
    return p


def hip_roof(m, w, seed=0):
    """HELD FOR MACAW'S ROOFS (lovish, r5): roof SHAPES wait for that mod's block ids, so this part is unchanged.
    R3: a pyramid hip roof over a w x w tower (walls x, z 0..w-1), 1 proud all round, every slope cell a STAIR
    facing up-slope (half-block risers; r3: slab pairs read as a ziggurat of full-block rings). Every face cell is the
    roof's base tone T0; the HIPS are a 1-wide line of the dark T1 down the diagonals, the eave ring is T1 (wet) and so
    is the ring round the apex where the four hips meet (one dark cap - T0 cells between four hip ends read a-b-a-b-a
    to the camera). A ridge-block apex with a dark iron finial, gutters under the eaves. `seed` is kept for the
    generators' call signature (the courses are structural, not random)."""
    p = blank()
    lo, hi = -1, w
    c = (w - 1) / 2
    dmax = (hi - lo) // 2
    t0, t1 = m.roof["tones"][0][1], m.roof["tones"][1][1]
    for x in range(lo, hi + 1):
        for z in range(lo, hi + 1):
            d = min(x - lo, hi - x, z - lo, hi - z)
            hip = abs(x - c) == abs(z - c) and (x, z) != (c, c)
            stair(p, x, d, z, t1 if (hip or d == 0 or d == dmax - 1) else t0, gp.toward(x, z, c, c))
            if d == 0 and not (x in (lo, hi) and z in (lo, hi)):
                f = "south" if z == lo else "north" if z == hi else "east" if x == lo else "west"
                stair(p, x, -1, z, m.roof["gutter"], f, "top")
    if c == int(c):
        top = max(y for (x, y, z) in p.b if (x, z) == (c, c))
        p.set(int(c), top, int(c), m.roof["ridge"][0])
        _finial(p, int(c), top + 1, int(c), m)
    return p


# ============================================================================================ R4-R5 roof openings
def dormer(m):
    """R4: a 3-light dormer x -2..2 that rides up through a slope behind it and reads as a little roofed house (r4: one
    white-blue box; r5: one tall light in a dark stone frame - a bunker): a 3 x 2 face of the set's panes (set one
    back) framed in the WALL's own stone - cheeks x -2 / 2, a lintel course over, a wall course under a pale slab
    sill - and over it its OWN stair gablet in the roof's dark T1 stairs: shoulders on the cheeks, stepping in to a
    slab apex, oversailing the frame face by 1 and running back 3 cells into the main slope. A lantern in a lit pale
    room behind the glass. No timber and no pale face."""
    p = blank()
    t1 = m.roof["tones"][1]
    for z in range(-3, 1):
        for x in (-2, 2):
            for y in range(0, 3):
                p.set(x, y, z, m.wall.full)                       # the cheeks, carried back as the dormer's side walls
        for x in (-1, 0, 1):
            p.set(x, 0, z, m.wall.full)                           # the sill course
            p.set(x, 3, z, m.wall.full)                           # the lintel
        p.set(0, 4, z, m.wall.full)                               # the tympanum
    for z in range(-3, 2):
        stair(p, -2, 3, z, t1[1], "east")                         # the shoulders, on the cheeks
        stair(p, 2, 3, z, t1[1], "west")
        stair(p, -1, 4, z, t1[1], "east")
        stair(p, 1, 4, z, t1[1], "west")
        slab(p, 0, 5, z, t1[2])                                   # the apex
    for x in (-1, 0, 1):
        slab(p, x, 0, 1, m.trim.slab, "top")                      # the sill, 1 proud
        for y in (1, 2):
            p.set(x, y, 0, "air")
            p.set(x, y, -1, m.glass[0])
            p.set(x, y, -2, m.render)                             # a pale lit room behind the glass, never a void
        p.set(x, 0, -2, m.trim.full)
    _lantern(p, 0, 1, -2, m)
    p.set(0, 2, -2, m.render)
    return p


def dormer_on_roof(m, seed=0):
    """A dormer standing in a roof_kit slope (for the sheet and the silhouette test): the slope falls toward the
    camera (south, +z) from the ridge at z -4 to an eave at z 1, so the dormer looks DOWN the roof (r3: it stood in
    the other half, facing up a rising slope, and read as a cut-out); its sill sits on the slope at z -1 and its gable
    breaks the roof line."""
    p = blank()
    roof = R.gable(9, 9, axis="x", seed=seed, mats=m.roof)
    for (x, y, z), v in roof.items():
        if z >= 4:
            p.b[(x - 4, y, z - 8)] = v
    for (x, y, z), v in dormer(m).b.items():
        p.b[(x, y + 1, z - 1)] = v
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


def roof_light(m, n=2):
    """R5: `n` roof lights in a row along x, set in a flat or gently pitched roof: each a 2 x 3 PANEL of tinted glass
    (x 2 wide, z 3 deep) in a kerb of the set's dark stairs, every stair's tall side to the glass (r5: rows of
    light-grey panes on a barrel read as snow on the roof). Panels 1 cell apart, sharing their kerb."""
    p = blank()
    glass = {(3 * i + 1 + a, z) for i in range(n) for a in (0, 1) for z in (1, 2, 3)}
    for x in range(3 * n + 1):
        for z in range(5):
            if (x, z) in glass:
                p.set(x, 0, z, "tinted_glass")
            elif z in (0, 4):
                stair(p, x, 0, z, m.dark.stairs, "south" if z == 0 else "north")
            else:
                stair(p, x, 0, z, m.dark.stairs, "east" if (x + 1, z) in glass else "west")
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
    """R7: a 1-wide pier x 0 in the wall plane (z 0) with its pilaster strip in front (z 1): a base a block proud
    (y 0), the pilaster strip and capital all in the set's REVEAL tone (the one that reads against its wall), the thin pale pilaster face (y 1..h-3), the capital a pale stair pair at the beam line (y h-2
    bottom, y h-1 top: a bulge); the pier body in the set's pier stone banded with the second tone every 2nd row.
    lantern: a lantern standing on an upside-down stair bracket in front of the pilaster."""
    p = blank()
    p.set(0, 0, 0, m.reveal.full)
    p.set(0, 0, 1, m.reveal.full)
    for y in range(1, h):
        p.set(0, y, 0, m.wall2.full if y % 2 == 0 and y < h - 1 else m.pier)
    for y in range(1, h - 2):
        p.set(0, y, 1, m.reveal.wall)
    stair(p, 0, h - 2, 1, m.reveal.stairs, "north", "bottom")
    stair(p, 0, h - 1, 1, m.reveal.stairs, "north", "top")
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
    hd = m.reveal if G.tone(m.wall.full) == "pale" else m.wall2     # a head that reads on a pale wall (r3: Sunforged)
    for x in range(W):
        p.set(x, 0, 0, m.wall.full)
        p.set(x, 0, -1, m.wall.full)
        slab(p, x, 0, 1, m.trim.slab, "top")
        for y in range(1, top + 1):
            p.set(x, y, 0, "air")
            mull = kind == "medium" and x == 1
            p.set(x, y, -1, m.trim.wall if mull else m.glass[0])
            p.set(x, y, -2, m.render)               # a pale lit room behind the glass (r3: black voids)
        p.set(x, H - 1, -1, m.wall.full)
    if kind == "tall":
        stair(p, 0, H - 1, 0, hd.stairs, "west", "top")
        stair(p, W - 1, H - 1, 0, hd.stairs, "east", "top")
    else:
        for x in range(W):
            stair(p, x, H - 1, 0, hd.stairs, "north", "top")
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
    narrower each side, each in its own stone in a tone OTHER than the pale reveals' (dark -> wall -> second tone for
    the brick and deepslate sets; the angels' dark slates, never their white walls) so the reveals read against it (r2:
    pale on pale; r3: the Sunforged portal unreadable). Every order's
    opening is lined with the set's REVEAL stairs - jamb stairs stepping down into the opening (a chamfered reveal)
    and an upside-down stair soffit under its head - so the arch reads as three concentric rings, from the front and
    from above (r1 sheet: a square hole). Over the outer order a stepped HOOD in the reveal stone (3 rows); the
    door plane (timber, a centre post) behind the last order; a pale step row 1 proud across the whole front,
    lanterns on pale posts each side."""
    p = blank()
    rv = m.reveal
    cand, mats = (m.dark, m.wall, m.wall2, m.trim, m.rib) + tuple(Fam(*t) for t in m.roof["tones"]), []
    for f in cand:                   # three distinct bodies in a tone the reveals read against (r3: Sunforged)
        if G.tone(f.full) != G.tone(rv.full) and f.full not in {g.full for g in mats}:
            mats.append(f)
    mats = mats[:3] + [mats[2]] * max(0, orders - 3)
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
                    p.set(x, y, z, rv.full if (y == 0 and abs(x) <= half[k]) else f.full)
        for y in range(1, hk[k]):                                   # the pale reveal down both jambs
            stair(p, -half[k] - 1, y, z, rv.stairs, "west")
            stair(p, half[k] + 1, y, z, rv.stairs, "east")
        for x in range(-half[k], half[k] + 1):                      # the soffit under the head
            stair(p, x, hk[k], z, rv.stairs, "north", "top")
        stair(p, -half[k], hk[k] - 1, z, rv.stairs, "west", "top")    # springers
        stair(p, half[k], hk[k] - 1, z, rv.stairs, "east", "top")
    for r in range(3):                                              # the stepped hood over the outer order
        y = top + 1 + r
        a = X - 1 - r
        for x in range(-a, a + 1):
            if abs(x) == a and a > 0:
                stair(p, x, y, 0, rv.stairs, "east" if x < 0 else "west")
            elif r == 2:
                slab(p, x, y, 0, rv.slab)
            else:
                p.set(x, y, 0, rv.full)
    zd = -orders
    for x in range(-half[-1], half[-1] + 1):
        for y in range(0, hk[-1]):
            if y == 0:
                p.set(x, 0, zd, rv.full)
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


def roof_chimney(m):
    """R11, the house chimney (r3: a pagoda; r4: four projecting pale slabs read as a clover / a snow cap): y 0 is
    where it leaves the roof. A 2 x 1 stack of the wall stone (or the first darker family when the wall is pale),
    y 0..5, its pale band a FULL flush course (y 4) - nothing projects past the stack - and a closed dark trapdoor cap
    (y 6) over the flue. No fire: the smoke stays on the big stack."""
    body = next(f for f in (m.wall, m.wall2, m.reveal, m.dark) if G.tone(f.full) != "pale")
    p = blank()
    for x in (0, 1):
        for y in range(6):
            p.set(x, y, 0, m.trim.full if y == 4 else body.full)
        p.set(x, 6, 0, m.timber.trapdoor, facing="south", half="bottom", open="false", powered="false",
              waterlogged="false")
    return p


def _ring(w):
    return [(x, z) for x in range(-w, w + 1) for z in range(-w, w + 1) if max(abs(x), abs(z)) == w]


def _octagon(r):
    """An octagonal plan of radius r: the square with its corners cut (|x| + |z| <= r + r // 2)."""
    return [(x, z) for x in range(-r, r + 1) for z in range(-r, r + 1) if abs(x) + abs(z) <= r + r // 2]


def spire(m, rows=4):
    """R12: a needle spire on the axis (0, 0) on a ROUND STONE DRUM (r3: square tiers read as a wedding cake): the
    drum, a disc r 3 (y 0..4: a base course in the reveal tone, the wall stone banded in the second tone, a lit slit
    each face); a CORBEL ring (upside-down dark stairs, y 5) carrying a pale coping disc r 4 (y 6). Then ONE straight
    taper on an OCTAGONAL plan in the roof's dark slate STAIRS - octagons r 3, 2 and a 3 x 3 top stage, `rows` courses
    each, every cell a stair, each face's stairs facing out one way (r6: no fill on view) - and only the last 3 blocks in the set's copper under a dark iron finial (r4: square stepped
    stages of full oxidised copper with pale slab rings read as a toy Christmas tree)."""
    p = blank()
    for y in range(5):
        for x, z in _rim(3):
            p.set(x, y, z, m.reveal.full if y == 0 else m.wall2.full if y == 3 else m.wall.full)
    for x, z in ((0, 3), (0, -3), (3, 0), (-3, 0)):
        p.set(x, 2, z, m.glass[0])
    p.set(0, 1, 0, m.trim.full)
    _lantern(p, 0, 2, 0, m)
    inner = set(_disc(3))
    for x, z in _disc(4):
        if (x, z) in inner:
            p.set(x, 5, z, m.wall.full)
            p.set(x, 6, z, m.trim.full)
        else:
            stair(p, x, 5, z, m.dark.stairs, gp.toward(x, z), "top")
            stair(p, x, 6, z, m.trim.stairs, gp.toward(x, z))
    y = 7
    slate = m.roof["tones"][0][1]
    for r in (3, 2, 1):                  # r6: the last stage a 3 x 3 (r5: a plus left the inner fill of the stage
        plan = _octagon(r) if r > 1 else [(x, z) for x in (-1, 0, 1) for z in (-1, 0, 1)]   # under it on view)
        for _ in range(rows):
            for x, z in plan:            # r6: each octagon face ONE facing (r5: gp.toward mixed two on the diagonals)
                f = gp.toward(x, z) if abs(x) == abs(z) else ("east" if x < 0 else "west") if abs(x) > abs(z) \
                    else ("south" if z < 0 else "north")
                stair(p, x, y, z, slate, f)
            y += 1
    for _ in range(3):
        p.set(0, y, 0, m.metal.full)
        y += 1
    _finial(p, 0, y, 0, m)
    return p


def dome(m, r=4):
    """R12: a ribbed dome on a stone drum, axis (0, 0). The DRUM (y 0..4): a dark base ring, the wall stone banded in
    the second tone with lit windows on the 8 meridians, a pale stair cornice 1 proud (y 5) and a pale slab ring; 4 dark
    iron pinnacles on the cornice diagonals. The DOME (y 6..): ONE shell material - the roof's dark base slate, every
    cell the camera can see a stair facing the crown - with 4 RIBS on the axes, each a single line of stairs (one per
    course) in the roof's third slate tone, one shade off the shell, ONE dark iron finial on its centre. Pale ONLY in
    the drum's cornice ring (r2, r3: 8 ribs closing up to a chequer near the crown; r5: pale ribs - a starfish)."""
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
    for k in (1, 3, 5, 7):                                   # 4 dark iron pinnacles on the diagonals
        a = k * math.pi / 4
        x, z = round(math.cos(a) * (r + 1)), round(math.sin(a) * (r + 1))
        p.set(x, 6, z, m.rib.wall)
        p.set(x, 7, z, m.rib.wall)
    y0 = 6
    shell = m.roof["tones"][0]
    rib_st = m.roof["tones"][2][1]           # r6: a slate stair one shade off the shell (r5: pale ribs - a starfish)
    radii = [r, r] + [rr for k in range(r - 1, 1, -1) for rr in (k, k)][:2 * (r - 2) - 1]   # 4 4 3 3 2
    for k, rr in enumerate(radii):
        y = y0 + k
        for (x, z) in _disc(rr):
            rib = (x == 0 or z == 0) and max(abs(x), abs(z)) == rr
            stair(p, x, y, z, rib_st if rib else shell[1], gp.toward(x, z))
    top = y0 + len(radii) - 1
    _finial(p, 0, top + 1, 0, m)                             # r5: one iron finial (r4: a green copper clover crown)
    return p


# ============================================================================================ R13-R16 railings ... machinery
def balustrade(m, L, every=4, panel="balusters"):
    """R13: a pale base rail (y 0) along x 0..L-1; solid pale stone POSTS (y 1..2) every `every` cells and at both ends,
    rising half a block over the coping; between them at y 1 the panel - a run of stone BALUSTERS (the trim's walls,
    one in every cell, so they read as a railing of turned balusters), or an iron-bar panel ("bars"), or a solid
    wall-material parapet ("solid") - under a pale slab COPING (y 2). r1: the solid run read as a row of boxes; r5:
    balusters every other cell between thin wall newels read as a garden wall / bench."""
    p = blank()
    for x in range(L):
        p.set(x, 0, 0, m.trim.full)
        if x % every == 0 or x == L - 1:
            p.set(x, 1, 0, m.trim.full)
            p.set(x, 2, 0, m.trim.full)
            continue
        p.set(x, 1, 0, "iron_bars" if panel == "bars" else m.wall.full if panel == "solid" else m.trim.wall)
        slab(p, x, 2, 0, m.trim.slab)
    return p


def half_timber(m, L):
    """R14: a two-storey front x 0..L-1. Lower storey: a base course in the set's reveal tone and the wall material
    (z 0, y 1..3) - the brick shows. The JETTY at the floor line (y 4): the upper storey 1 proud, its sole a dark
    trapdoor BRESSUMMER (a thin beam, z 1) on upside-down timber stair BRACKETS under the posts. Upper storey (y 5..7):
    the timber is the FRAME, not the fill - 1-wide dark POSTS (z 1) 3 apart, pale RENDER infill one back between them
    (z 0), the middle bay a lit window, panes one behind the render on a pale slab sill;
    a pale stone top plate (y 8) under a pale slab coping (r1: a dark crate; r2: ~60 % timber; r5: 2-wide dark oak
    bands - log plates at the jetty and the head - a storefront)."""
    p = blank()
    posts = list(range(0, L, 3))
    if L - 1 - posts[-1] >= 2:
        posts.append(L - 1)
    elif posts[-1] != L - 1:
        posts[-1] = L - 1
    for x in range(L):
        p.set(x, 0, 0, m.reveal.full)
        for y in (1, 2, 3):
            p.set(x, y, 0, m.wall.full)
        p.set(x, 4, 0, m.wall.full)
        p.set(x, 4, 1, m.timber.trapdoor, facing="south", half="top", open="false", powered="false",
              waterlogged="false")
        p.set(x, 8, 0, m.render)
        p.set(x, 8, 1, m.trim.full)                              # a pale stone top plate the posts carry
        slab(p, x, 9, 0, m.trim.slab)
        slab(p, x, 9, 1, m.trim.slab)
        for y in (5, 6, 7):
            p.set(x, y, 0, m.render)
            if x in posts:
                p.set(x, y, 1, m.timber.log, axis="y")
    for x in posts:
        stair(p, x, 3, 1, m.timber.stairs, "north", "top")
    bays = [(a + 1, b - 1) for a, b in zip(posts, posts[1:]) if b - a > 1]
    a, b = bays[len(bays) // 2]
    for x in range(a, b + 1):                                     # the glazed bay: panes 1 back on a pale sill
        slab(p, x, 5, 1, m.trim.slab, "top")
        for y in (6, 7):
            p.b.pop((x, y, 0), None)
            p.set(x, y, -1, m.glass[0])
            p.set(x, y, -2, m.render)
        p.set(x, 5, -1, m.render)
        p.set(x, 8, -1, m.render)
    p.set(a, 5, -2, m.render)
    _lantern(p, a, 6, -2, m)
    return p


def _runs(rng, L, values):
    """A row of L cells in runs of 2-4 of `values` (never a single cell, so never a-b-a), each value on >= 1/len."""
    while True:
        out = []
        while len(out) < L:
            out += [rng.choice(values)] * rng.randint(2, 4)
        out = out[:L]
        if out[-1] != out[-2]:
            out[-1] = out[-2]
        if all(out.count(v) >= L / len(values) for v in values):
            return out


def tone_wall(m, L, H, seed=0):
    """R15: a wall face x 0..L-1, y 0..H-1 that darkens toward its foot the way water stains it - a GRADIENT of three
    tones by row (r3: one black band at the foot): the clean wall over the upper half (r6: the pale biased to the top),
    the set's darker STAIN through the middle, MOSS and the dark plinth over the foot. Each edge between two tones wanders over THREE rows in ragged
    runs of 2-4 columns (r4: two hard bands) - about a quarter of each band dithered into its neighbour, structured:
    in every column the tone only ever darkens going down, so it can never chequer."""
    rng = random.Random(seed)
    stain, moss = m.weather
    p = blank()
    mid = H // 2                                 # r6: clean over the upper half (r5: the stain reached the top rows)
    top = _runs(rng, L, (mid, mid + 1, mid + 2))  # clean from this row up
    foot = _runs(rng, L, (1, 2, 3))              # moss below this row (over the dark plinth)
    for x in range(L):
        p.set(x, 0, 0, m.dark.full)
        for y in range(1, H):
            p.set(x, y, 0, m.wall.full if y >= top[x] else moss if y < foot[x] else stain)
    return p


def machinery(m, L=9):
    """R16: a machinery band against a wall, 5 high (r3: a deepslate blob - dark parts on a dark ground): a PALE stone
    machine bed (z 1, y 0..4) the dark iron reads against, and on it (z 2) a pipe RUN along y 3 with ELBOWS at both
    ends and a drop at x 0, a 2 x 2 dark-metal TANK with a weathered-copper band, a riser from it to the run with a
    VALVE (lever) beside its joint, a cauldron, a grindstone pump on its own riser and a dark iron VENT on the far
    elbow - each piece with pale stone round it."""
    p = blank()
    for x in range(L):
        for y in range(5):
            p.set(x, y, 1, m.trim.full)
    for x in range(1, L - 1):
        p.set(x, 3, 2, m.dark.wall)
    p.set(0, 3, 2, m.dark.full)
    p.set(L - 1, 3, 2, m.dark.full)
    for y in (0, 1, 2):
        p.set(0, y, 2, m.dark.wall)
    t = 2
    for x in (t, t + 1):
        p.set(x, 0, 2, m.rib.full)
        p.set(x, 1, 2, m.metal.full)
    p.set(t + 2, 2, 2, "lever", face="wall", facing="south", powered="false")
    for y in (0, 1, 2):
        p.set(t + 3, y, 2, m.dark.wall)
    p.set(t + 4, 0, 2, "grindstone", face="floor", facing="south")
    p.set(t + 4, 1, 2, m.dark.wall)
    p.set(t + 4, 2, 2, m.dark.wall)
    p.set(t + 5, 0, 2, "cauldron")
    _finial(p, L - 1, 4, 2, m)
    return p


def gear(m, r=3):
    """R16: a wheel on a wall face (r2: an orange donut; r3: a dark blob; r4: a ring of bars read as a 2 x 2 dark
    square; r5: a hollow ring on a pale disc - a broken horseshoe with pale blobs). A SOLID dark disc (z 1, radius
    r + 0.75); its 8 TEETH single dark stairs on the rim (on the axes and the diagonals), each touching the disc; on
    its face (z 2) four iron-bar SPOKES along the axes round a copper hub (diagonals close up into a diamond of bars)."""
    p = blank()
    disc = {(x, y) for x in range(-r, r + 1) for y in range(-r, r + 1) if math.hypot(x, y) < r + 0.75}
    for x, y in disc:
        p.set(x, y, 1, m.dark.full)
    teeth = {(0, r + 1), (0, -r - 1), (r + 1, 0), (-r - 1, 0)}         # 8 evenly: the axes and the diagonals
    teeth |= {(r * sx, r * sy) for sx in (1, -1) for sy in (1, -1)}
    for x, y in teeth:
        f = "south" if x == 0 else "west" if x > 0 else "east"       # the tall back against the disc
        stair(p, x, y, 1, m.dark.stairs, f, "bottom" if y > 0 or (y == 0 and x != 0) else "top")
    for k in range(1, r):
        for x, y in ((k, 0), (-k, 0), (0, k), (0, -k)):
            p.set(x, y, 2, "iron_bars")
    p.set(0, 0, 2, m.metal.full)
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
        p.set(x, 0, 0, m.reveal.full)
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
    ("roof_light", "R5", lambda m: roof_light(m, 2)),
    ("cornice", "R6", lambda m: cornice(m, 8)),
    ("string_course", "R6", lambda m: string_course(m, 8)),
    ("pier", "R7", lambda m: pier(m, 5, lantern=True)),
    ("window_tall", "R8", lambda m: window(m, "tall")),
    ("window_medium", "R8", lambda m: window(m, "medium")),
    ("window_small", "R8", lambda m: window(m, "small")),
    ("bay_tall", "R8", lambda m: bay(m, "tall")),
    ("portal", "R9", portal),
    ("corbel_stage", "R10", lambda m: corbel_stage(m, 5)),
    ("chimney", "R11", roof_chimney),
    ("stack", "R11", lambda m: chimney(m, 10, roof_y=4)),
    ("spire", "R12", spire),
    ("dome", "R12", lambda m: dome(m, 4)),
    ("balustrade", "R13", lambda m: balustrade(m, 9)),
    ("parapet", "R13", lambda m: balustrade(m, 9, panel="solid")),
    ("half_timber", "R14", lambda m: half_timber(m, 10)),
    ("tone_wall", "R15", lambda m: tone_wall(m, 12, 6)),
    ("machinery", "R16", lambda m: machinery(m, 9)),
    ("gear", "R16", lambda m: gear(m, 3)),
]
