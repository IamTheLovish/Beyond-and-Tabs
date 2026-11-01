#!/usr/bin/env python3
"""THE TIDEWROUGHT DOCKYARD PART LIBRARY - the parts the Careening Hard (tools/lab_vehicle_tide.py) is assembled from,
and every Tidewrought dockyard building after it (the Slipway's family: tools/lab_slipway.py, the Admiral's Beacon
tools/capitol_wreck_harbour.py).

CONVENTIONS (tools/gothic_parts.py's): a part is a gothic_parts.Part - a dict of cells (x, y, z) -> (block, props) in
its own frame, its FRONT facing SOUTH (+z, the RTS camera), y = 0 its floor. Part.place(other, ox, oy, oz, rot) copies a
part in turned `rot` quarter turns clockwise (rot 1 turns a south front to face WEST: a local cell (lx, lz) lands at
(ox - lz, oz + lx)). Roles go through TIDE (a role that is not in it is a block id and passes straight through).

THE KIT, WITH DISCIPLINE (round 2 - the critic called round 1 "a stacked block-sampler"):
  ground storey   ONE base material - sandstone (cut sandstone body, smooth sandstone sills, a dark-prismarine course
                  at the waterline where the wet has got into it) - and ONE accent, prismarine-brick quoins / bands.
                  No nether brick, no terracotta, no copper below eye level.
  upper storeys   wreck timber: stripped dark-oak posts and dark-oak girts, spruce weatherboard between, jettied out on
                  upside-down-stair brackets; cyan glass in dark frames; mangrove planks for hulls and the slipway floor.
  roofs           oxidised copper (waxed, so it never changes) in stair courses with weathered-copper patches, a dark-oak
                  ridge beam, stair eaves thrown a cell past the wall, dormers on the bays.
  light           the lighthouse lantern, a line of lit loft windows, the Sloop's two eyes - nothing else.
  rigging         rope = dark-oak fences, chain = chain; sails white wool with the fleet's light-blue stripe.

THE PARTS
  lighthouse      the banded octagonal lighthouse (plinth, shaft, timber gallery rings on corbels, corbelled stone
                  gallery, lamp room, flared stepped cap, copper ball, vane) - the landmark, unlit but for its lamp
  trestle         a braced timber dockyard trestle tower (the gantry's legs)
  gantry_beam     the gantry beam, trolley, rope fall and cargo net between two trestles
  sloop           the Mudskipper Sloop on her cradle - the boat-creature the lab builds, head to the slip mouth
  hard_hull       a ship hove down on the hard in a mangrove-log cradle: a clean sheer, a rounded copper bottom
  waterwheel      the tide-pump wheel facing south: rim, spokes, copper hub, bucket boards
  roof_ridge_z    a two-tone gable roof (oxidised copper or dark-prismarine "slate") whose ridge runs along z, a
                  solid body under the stair courses, a dark-oak ridge beam, eaves thrown out, gable fill
  roof_ridge_x    the same with the ridge along x (a `skip` set leaves an oculus / spawn column open)
  dormer_south    a lit, glazed dormer standing on a south-facing slope
  bracket_row     a row of jetty brackets (upside-down dark-oak stairs) under an overhanging storey
  props           bollard, barrel stack, timber stack

Usage: python3 tools/tide_lab_parts.py   (builds every part once and prints its size and block count)
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gothic_parts import Part  # noqa: E402

DRY = dict(waterlogged="false")
TIDE = {
    "post": "stripped_dark_oak_log", "post2": "stripped_spruce_log", "beam": "dark_oak_log", "beam2": "spruce_log",
    "mlog": "mangrove_log", "mpost": "stripped_mangrove_log",
    "plank": "dark_oak_planks", "plank2": "spruce_planks", "hullp": "mangrove_planks",
    "tst": "dark_oak_stairs", "tst2": "spruce_stairs", "hst": "mangrove_stairs",
    "tsl": "dark_oak_slab", "tsl2": "spruce_slab", "hsl": "mangrove_slab",
    "rope": "spruce_fence", "rope2": "dark_oak_fence", "hfence": "mangrove_fence",
    "shut": "spruce_trapdoor", "shut2": "dark_oak_trapdoor", "hshut": "mangrove_trapdoor",
    "pb": "prismarine_bricks", "pr": "prismarine", "dp": "dark_prismarine",
    "pbst": "prismarine_brick_stairs", "dpst": "dark_prismarine_stairs", "prst": "prismarine_stairs",
    "pbsl": "prismarine_brick_slab", "dpsl": "dark_prismarine_slab", "prsl": "prismarine_slab", "prw": "prismarine_wall",
    "ss": "smooth_sandstone", "cs": "cut_sandstone", "chs": "chiseled_sandstone", "sa": "sandstone",
    "sast": "sandstone_stairs", "ssst": "smooth_sandstone_stairs", "cssl": "cut_sandstone_slab",
    "sssl": "smooth_sandstone_slab", "saw": "sandstone_wall",
    "cu": "waxed_cut_copper", "cux": "waxed_exposed_cut_copper", "cuw": "waxed_weathered_cut_copper",
    "cuo": "waxed_oxidized_cut_copper", "cust": "waxed_cut_copper_stairs", "cuxst": "waxed_exposed_cut_copper_stairs",
    "cuwst": "waxed_weathered_cut_copper_stairs", "cuost": "waxed_oxidized_cut_copper_stairs",
    "cusl": "waxed_cut_copper_slab", "cuwsl": "waxed_weathered_cut_copper_slab", "cuosl": "waxed_oxidized_cut_copper_slab",
    "cublk": "waxed_copper_block", "cuoblk": "waxed_oxidized_copper",
    "glass": "cyan_stained_glass", "pane": "cyan_stained_glass_pane", "pane2": "light_blue_stained_glass_pane",
    "lamp": "sea_lantern", "sail": "white_wool", "stripe": "light_blue_wool",
}


class TPart(Part):
    """A Part with the dockyard's shorthands (raw ids or TIDE roles)."""

    def __init__(self):
        super().__init__(dict(TIDE))

    def st(self, x, y, z, facing, half="bottom", role="tst"):
        self.set(x, y, z, role, facing=facing, half=half, shape="straight", **DRY)

    def sl(self, x, y, z, role="tsl", typ="bottom"):
        self.set(x, y, z, role, type=typ, **DRY)

    def log(self, x, y, z, axis="y", role="post"):
        self.set(x, y, z, role, axis=axis)

    def chain(self, x, y, z, axis="y"):
        self.set(x, y, z, "chain", axis=axis, **DRY)

    def trap(self, x, y, z, facing, half="bottom", open_=True, role="shut"):
        self.set(x, y, z, role, facing=facing, half=half, open="true" if open_ else "false", powered="false", **DRY)

    def box(self, x0, x1, y0, y1, z0, z1, role, **p):
        self.fill(x0, x1, y0, y1, z0, z1, role, **p)

    def has(self, x, y, z):
        return (x, y, z) in self.b


def hsh(x, z, s=0):
    return ((x + 31 + s * 7) * 73856093 ^ (z + 17 - s * 3) * 19349663) % 101


def roof_tone(x, y, z, st=True, tone="copper"):
    """A roof's two tones: oxidised copper with weathered patches (about a quarter), or dark prismarine with
    prismarine-brick patches (the lighthouse cap's slate), so a slope is never one flat colour from above but still
    reads as one quiet plane. The two tones alternate between masses for rhythm (ref15)."""
    patch = hsh(x + z, y, 5) % 4 == 0
    if tone == "copper":
        if st:
            return "cuwst" if patch else "cuost"
        return "cuwsl" if patch else "cuosl"
    if st:
        return "pbst" if patch else "dpst"
    return "pbsl" if patch else "dpsl"


# ============================================================================================ roofs
def roof_block(tone):
    return "cuoblk" if tone == "copper" else "dp"


def roof_ridge_z(p, x0, x1, z0, z1, y_eave, gable_ends=(), fill="plank2", overhang=True, tone="copper"):
    """A gable roof over x0..x1 (the ridge along z at the middle, x0..x1 odd in count), z0..z1, the eave course at
    y_eave: stair courses climbing from both eaves to a dark-oak ridge beam with a slab cap; with `overhang` the eave
    stairs are thrown one cell past x0 / x1. `gable_ends` are z values whose triangle is filled (weatherboard between
    a dark-oak bargeboard drawn as the end column's stairs). Returns the ridge y."""
    n = (x1 - x0) // 2
    xm = x0 + n
    for k in range(n):
        y = y_eave + k
        for z in range(z0, z1 + 1):
            p.st(x0 + k, y, z, "east", "bottom", roof_tone(x0 + k, y, z, tone=tone))
            p.st(x1 - k, y, z, "west", "bottom", roof_tone(x1 - k, y, z, tone=tone))
            for x in (x0 + k, x1 - k):
                if k > 0 and not p.has(x, y - 1, z):
                    p.set(x, y - 1, z, roof_block(tone))         # the roof's body under its surface
    ridge = y_eave + n
    for z in range(z0, z1 + 1):
        p.log(xm, ridge, z, "z", "beam")
        p.sl(xm, ridge + 1, z, "tsl")
    if overhang:
        for z in range(z0, z1 + 1):
            p.st(x0 - 1, y_eave - 1, z, "east", "top", roof_tone(x0 - 1, y_eave - 1, z, tone=tone))
            p.st(x1 + 1, y_eave - 1, z, "west", "top", roof_tone(x1 + 1, y_eave - 1, z, tone=tone))
    for z in gable_ends:
        for k in range(n + 1):
            for x in range(x0 + k + 1, x1 - k):
                if not p.has(x, y_eave + k, z):
                    p.set(x, y_eave + k, z, fill if (x + k) % 2 else "plank")
    return ridge


def roof_ridge_x(p, z0, z1, x0, x1, y_eave, gable_ends=(), fill="plank2", overhang=True, skip=(), tone="copper"):
    """roof_ridge_z turned: the ridge along x at the middle of z0..z1. `skip` cells are left out (an oculus or a
    spawn column)."""
    n = (z1 - z0) // 2
    zm = z0 + n
    for k in range(n):
        y = y_eave + k
        for x in range(x0, x1 + 1):
            if (x, z0 + k) not in skip:
                p.st(x, y, z0 + k, "south", "bottom", roof_tone(x, y, z0 + k, tone=tone))
            if (x, z1 - k) not in skip:
                p.st(x, y, z1 - k, "north", "bottom", roof_tone(x, y, z1 - k, tone=tone))
            for z in (z0 + k, z1 - k):
                if k > 0 and (x, z) not in skip and not p.has(x, y - 1, z):
                    p.set(x, y - 1, z, roof_block(tone))         # the roof's body under its surface
    ridge = y_eave + n
    for x in range(x0, x1 + 1):
        if (x, zm) in skip:
            continue
        p.log(x, ridge, zm, "x", "beam")
        p.sl(x, ridge + 1, zm, "tsl")
    if overhang:
        for x in range(x0, x1 + 1):
            p.st(x, y_eave - 1, z0 - 1, "south", "top", roof_tone(x, y_eave - 1, z0 - 1, tone=tone))
            p.st(x, y_eave - 1, z1 + 1, "north", "top", roof_tone(x, y_eave - 1, z1 + 1, tone=tone))
    for x in gable_ends:
        for k in range(n + 1):
            for z in range(z0 + k + 1, z1 - k):
                if not p.has(x, y_eave + k, z):
                    p.set(x, y_eave + k, z, fill if (z + k) % 2 else "plank")
    return ridge


def dormer_south(p, x0, x1, z, y, lit=True):
    """A dormer standing on a south-facing slope whose course at z is at y: cyan panes at y+1 between stripped posts,
    a shroomlight behind each when `lit` (the room in the roof), a copper hood of upside-down stairs over them and a
    slab cap stepping back onto the slope. Never overwrites the roof's own courses."""
    for x in range(x0, x1 + 1):
        p.set(x, y + 1, z, "pane")
        if not p.has(x, y + 1, z - 1):
            p.set(x, y + 1, z - 1, "shroomlight" if lit else "plank2")
        p.st(x, y + 2, z, "north", "top", "cuost")
        if not p.has(x, y + 3, z - 1):
            p.sl(x, y + 3, z - 1, "cuosl")
    for x in (x0 - 1, x1 + 1):
        p.log(x, y + 1, z, "y", "post")
        if not p.has(x, y + 1, z - 1):
            p.set(x, y + 1, z - 1, "plank2")
        p.st(x, y + 2, z, "north", "top", "cuost")


def bracket_row(p, x, y, z0, z1, facing="east"):
    """Jetty brackets: upside-down dark-oak stairs under an overhang, their backs to the wall (`facing` is the way the
    stair faces: east = the bracket hangs off a wall to its east, the overhang to the west)."""
    for z in range(z0, z1 + 1):
        p.st(x, y, z, facing, "top", "tst")


# ============================================================================================ the lighthouse
def lighthouse(shaft_top=11, cx=0, cz=0, galleries=()):
    """The banded lighthouse on an octagonal plan 5 across: a square sandstone plinth with a stepped foot; the shaft in
    bands of three courses (sandstone / prismarine, dark prismarine on the cut corners); the shaft BROKEN at each y in
    `galleries` by a ring of WOODEN GALLERY on corbels (upside-down stairs, a plank deck, a rope rail, a boarded band
    above) - ref17's lesson, never a plain shaft; the corbelled stone gallery with a rope rail at the top; the lamp
    room (prismarine-wall mullions, cyan glass, a sea-lantern heart - the yard's one signature glow); the FLARED
    stepped cap (an overhanging ring of stairs, then stepping in), a copper ball and an end-rod vane. Dark slit
    windows (no light but the lamp). Returns (part, top y)."""
    p = TPart()
    oct_ = [(x, z) for x in range(-2, 3) for z in range(-2, 3) if abs(x) + abs(z) <= 3]
    rim = [(x, z) for (x, z) in oct_ if abs(x) == 2 or abs(z) == 2 or abs(x) + abs(z) == 3]
    ring3 = [(x, z) for x in range(-3, 4) for z in range(-3, 4) if (x, z) not in oct_ and abs(x) + abs(z) <= 4]

    def out_f(x, z):
        if abs(x) >= abs(z):
            return "east" if x < 0 else "west"
        return "south" if z < 0 else "north"

    for x in range(-2, 3):
        for z in range(-2, 3):
            p.set(cx + x, 1, cz + z, "cs")
            if (x, z) in oct_:
                p.set(cx + x, 2, cz + z, "ss")
            else:
                p.st(cx + x, 2, cz + z, out_f(x, z), "bottom", "sast")
    for y in range(3, shaft_top + 1):
        stripe = ((y - 3) // 3) % 2 == 1
        for (x, z) in oct_:
            if (x, z) not in rim:
                p.set(cx + x, y, cz + z, "plank2")
            elif abs(x) + abs(z) == 3:
                p.set(cx + x, y, cz + z, "dp" if stripe else "cs")
            else:
                p.set(cx + x, y, cz + z, "pr" if stripe else "ss")
    # dark slit windows on the west and south faces, a drip slab over each
    for y in range(5, shaft_top - 1, 4):
        if any(abs(y - g) <= 1 for g in galleries):
            continue
        for (x, z) in ((-2, 0), (0, 2)):
            p.set(cx + x, y, cz + z, "black_stained_glass_pane")
            p.sl(cx + x + (-1 if x else 0), y + 1, cz + z + (1 if z else 0), "dpsl")
    for g in galleries:
        for (x, z) in ring3:
            p.st(cx + x, g, cz + z, out_f(x, z), "top", "tst")
            p.set(cx + x, g + 1, cz + z, "rope2")
        for (x, z) in rim:
            p.set(cx + x, g, cz + z, "plank")
            p.set(cx + x, g + 1, cz + z, "plank2")
            p.log(cx + x, g + 2, cz + z, "y" if abs(x) + abs(z) == 3 else "x" if abs(z) == 2 else "z", "beam")
    g = shaft_top + 1
    for (x, z) in oct_:
        p.set(cx + x, g, cz + z, "dp")
    for (x, z) in ring3:
        p.st(cx + x, g - 1, cz + z, out_f(x, z), "top", "dpst")
        p.sl(cx + x, g, cz + z, "dpsl", "top")
        p.set(cx + x, g + 1, cz + z, "rope")
    lr = g + 1
    for y in range(lr, lr + 3):
        for x in range(-1, 2):
            for z in range(-1, 2):
                if (x, z) == (0, 0):
                    p.set(cx, y, cz, "lamp")
                elif abs(x) == 1 and abs(z) == 1:
                    p.set(cx + x, y, cz + z, "prw")
                else:
                    p.set(cx + x, y, cz + z, "glass")
    c = lr + 3
    for (x, z) in oct_:
        if (x, z) in rim:
            p.st(cx + x, c, cz + z, out_f(x, z), "bottom", "dpst")
        else:
            p.set(cx + x, c, cz + z, "dp")
    for x in range(-1, 2):
        for z in range(-1, 2):
            if (x, z) != (0, 0):
                f = "east" if x == -1 else "west" if x == 1 else "south" if z == -1 else "north"
                p.st(cx + x, c + 1, cz + z, f, "bottom", "dpst")
    p.set(cx, c + 1, cz, "dp")
    p.set(cx, c + 2, cz, "cublk")
    p.set(cx, c + 3, cz, "end_rod", facing="up")
    return p, c + 3


# ============================================================================================ gantry
def trestle(h=11, w=2, d=2):
    """A braced timber trestle tower w wide (x 0..w-1) and d deep (z 0..d-1): corner posts on a sandstone plinth, cross
    braces of stairs on the front face, a ledger every 4 courses, a cap of slabs. Its interior is boarded (sealed)."""
    p = TPart()
    for x in range(w):
        for z in range(d):
            p.set(x, 1, z, "cs")
    for y in range(2, h + 1):
        for x in range(w):
            for z in range(d):
                post = x in (0, w - 1) and z in (0, d - 1)
                if post:
                    p.log(x, y, z, "y", "post")
                elif y % 4 == 1:
                    p.log(x, y, z, "x", "beam")
                else:
                    p.set(x, y, z, "plank2")
    for y in range(2, h + 1):                                      # the front face bracing
        for x in range(w):
            if y % 4 == 1:
                p.sl(x, y, d, "tsl", "top")
            elif y % 4 in (2, 0):
                p.st(x, y, d, "north", "top" if y % 4 == 0 else "bottom", "tst")
    for x in range(w):
        for z in range(d):
            p.sl(x, h + 1, z, "tsl")
    return p


def gantry_beam(span, trolley_at, y=11, lowest=8):
    """The gantry between two trestles, along +z (z 0..span-1, the beam on x 0): a dark-oak beam at y with a slab rail,
    upside-down stair haunches at its ends, a waxed-copper trolley under it at z = trolley_at, the rope fall (chain),
    the CARGO NET (mangrove roots round a barrel) with its lowest block at y = `lowest`."""
    p = TPart()
    for z in range(span):
        p.log(0, y, z, "z", "beam")
        p.sl(0, y + 1, z, "tsl")
    p.st(0, y - 1, 1, "north", "top", "tst")
    p.st(0, y - 1, span - 2, "south", "top", "tst")
    t = trolley_at
    p.set(0, y - 1, t, "cublk")
    p.st(0, y - 1, t - 1, "south", "top", "cust")
    p.st(0, y - 1, t + 1, "north", "top", "cust")
    for yy in range(lowest + 2, y - 1):
        p.chain(0, yy, t)
    p.set(0, lowest + 1, t, "mangrove_roots", **DRY)
    p.set(0, lowest, t, "barrel", facing="up", open="false")
    for dz in (-1, 1):
        p.set(0, lowest + 1, t + dz, "mangrove_roots", **DRY)
        p.set(0, lowest, t + dz, "mangrove_roots", **DRY)
    return p


# ============================================================================================ the sloop in the slip
def sloop(n=6):
    """THE MUDSKIPPER SLOOP on her cradle in the covered slip, the BOW toward the slip mouth (x = 0, west), length along
    +x, beam z -2..2: a sealed cradle (dark-prismarine bed, spruce chocks) at y 1 over z -3..3; the keel and copper
    garboards at y 2, the copper bilge at y 3, mangrove topsides at y 4 with a deck of slabs and a rope rail at y 5; at
    the bow the creature's HEAD - a blunt copper snout, two goggling sea-lantern EYES under dark brows (the slip's one
    glow) - and a fan tail at the stern."""
    p = TPart()
    for x in range(-1, n + 1):
        for z in range(-2, 3):
            edge = abs(z) == 2 or x in (-1, n)
            if edge:
                f = "east" if x == -1 else "west" if x == n else ("south" if z < 0 else "north")
                p.st(x, 1, z, f, "bottom", "dpst")
            elif z % 2 == 0:
                p.log(x, 1, z, "x", "mlog")
            else:
                p.set(x, 1, z, "dp")
    for x in range(0, n):
        bow = x == 0
        w = 1 if bow else 2
        p.set(x, 2, 0, "cu" if not bow else "cublk")
        for s in (-1, 1):
            p.st(x, 2, s, "north" if s > 0 else "south", "top", "cust")
        for z in range(-w + 1, w):
            p.set(x, 3, z, "cu" if hsh(x, z, 4) % 4 else "cux")
        if w == 2:
            for s in (-1, 1):
                p.st(x, 3, 2 * s, "north" if s > 0 else "south", "top", "cust")
        for z in range(-w, w + 1):
            p.set(x, 4, z, "hullp" if abs(z) == w else "plank2")
        for z in range(-w + 1, w):
            p.sl(x, 5, z, "tsl2")
        if w == 2:
            for s in (-1, 1):
                p.set(x, 5, 2 * s, "hfence")
    p.st(-1, 3, 0, "east", "top", "cust")
    p.st(-1, 4, 0, "east", "bottom", "cust")
    for s in (-1, 1):
        p.set(1, 6, s, "lamp")
        p.sl(1, 7, s, "dpsl")
        p.st(0, 6, s, "east", "bottom", "dpst")
    p.set(1, 5, 0, "dp")
    for y in range(4, 8):
        p.trap(n, y, 0, "east", "bottom", True, "hshut")
    p.st(n - 1, 6, 0, "west", "bottom", "hst")
    return p


# ============================================================================================ the hull on the hard
def hard_hull(n=7):
    """A SHIP HOVE DOWN ON THE HARD, broadside to the camera: length along x (bow at x 0, west), beam z 0..3 (the camera
    side at z 3). One clean SHEER: the bottom and the rail both sweep up a course at each end (stairs, not steps). The
    ROUNDED COPPER BOTTOM: two courses of one tone of waxed cut copper, the turn of the bilge an upside-down copper
    stair at the keel on both sides, the forefoot and the run curving up under the ends; a dark-oak wale, one course
    of mangrove topsides, a spruce-slab deck and a rope rail; a stem post with the bowsprit stub, a mangrove transom.
    She lies on MANGROVE-LOG cradle baulks across under her keel. Solid inside (no pockets); shores are the yard's."""
    p = TPart()
    prof = {x: 2 + max(0, 2 - min(x, n - 1 - x)) for x in range(n)}      # the sheer: 4, 3, 2 ... 2, 3, 4
    for x, bot in prof.items():
        rail = bot + 5
        if x % 2 == 1:
            for z in range(-1, 5):
                p.log(x, 1, z, "z", "mlog")                   # the cradle baulks, proud of her sides
        for z in (1, 2):
            p.log(x, bot, z, "x", "beam")                     # the keel
        p.st(x, bot, 0, "south", "top", "cust")
        p.st(x, bot, 3, "north", "top", "cust")
        for y in range(bot + 1, rail):
            for z in range(4):
                if z in (1, 2):
                    p.set(x, y, z, "plank2")
                elif y <= bot + 2:
                    p.set(x, y, z, "cu")
                elif y == bot + 3:
                    p.log(x, y, z, "x", "beam")
                else:
                    p.set(x, y, z, "hullp")
        for z in (1, 2):
            p.sl(x, rail, z, "tsl2")
        p.set(x, rail, 0, "rope2")
        p.set(x, rail, 3, "rope2")
        if bot > 2:                                           # the forefoot / run curving up under the end
            f = "east" if x < n // 2 else "west"
            for z in range(4):
                p.st(x, bot - 1, z, f, "top", "cust")
            for z in (0, 3):                                  # the sheer: the rail's step drawn as a stair
                p.st(x, rail - 1, z, f, "bottom", "hst")
    b0, b1 = prof[0], prof[n - 1]
    # the stem (dark oak, raking forward) and the bowsprit
    for y in range(b0, b0 + 7):
        p.log(-1, y, 1, "y", "beam")
        p.log(-1, y, 2, "y", "beam") if y < b0 + 3 else None
    p.st(-1, b0 + 7, 1, "east", "bottom", "tst")
    p.st(-1, b0 - 1, 1, "east", "top", "cust")
    p.st(-1, b0 - 1, 2, "east", "top", "cust")
    p.log(-2, b0 + 5, 1, "x", "post2")
    p.log(-3, b0 + 5, 1, "x", "post2")
    # the transom at the stern, a mangrove sternpost
    for y in range(b1, b1 + 5):
        p.set(n, y, 1, "hullp")
        p.set(n, y, 2, "hullp")
    p.st(n, b1 + 5, 1, "west", "bottom", "hst")
    p.st(n, b1 + 5, 2, "west", "bottom", "hst")
    p.st(n, b1 - 1, 1, "west", "top", "cust")
    p.st(n, b1 - 1, 2, "west", "top", "cust")
    # a stub mast amidships (topmasts struck)
    mx = n // 2
    for y in range(prof[mx] + 5, prof[mx] + 9):
        p.log(mx, y, 1, "y", "beam")
    return p


# ============================================================================================ the waterwheel
def waterwheel(r=3):
    """The tide-pump wheel in the x-y plane at z 0 (its face to the south), hub at (0, r+1, 0): a rim of dark oak
    (logs where the circle turns the corner, so it reads round), spokes of stripped spruce, a waxed-copper hub, bucket
    boards (open trapdoors) on the rim, the axle running north (z -1) into the pump."""
    p = TPart()
    hy = r + 1
    for a in range(0, 360, 6):
        t = math.radians(a)
        x, y = round(r * math.cos(t)), round(hy + r * math.sin(t))
        if (x, y, 0) not in p.b:
            corner = abs(x) == abs(y - hy)
            p.set(x, y, 0, "plank" if not corner else "beam", **({"axis": "z"} if corner else {}))
    for k in range(1, r):
        p.log(k, hy, 0, "x", "post2")
        p.log(-k, hy, 0, "x", "post2")
        p.log(0, hy + k, 0, "y", "post2")
        p.log(0, hy - k, 0, "y", "post2")
    if r >= 3:
        for sx in (-1, 1):
            for sy in (-1, 1):
                p.set(sx * 2, hy + sy * 2, 0, "rope")
    p.set(0, hy, 0, "cublk")
    for (x, y, z) in list(p.b):
        n = p.b[(x, y, z)][0]
        if n in ("dark_oak_planks", "dark_oak_log") and z == 0 and (x + y) % 2 == 0:
            p.trap(x, y, 1, "north", "bottom", True, "shut2")
    p.log(0, hy, -1, "z", "post")
    return p


# ============================================================================================ props
def bollard():
    p = TPart()
    p.set(0, 1, 0, "dp")
    p.set(0, 2, 0, "prw")
    p.sl(0, 3, 0, "dpsl")
    return p


def barrel_stack(n=3):
    """Barrels on their sides against a wall: n in a row, one on top."""
    p = TPart()
    for x in range(n):
        p.set(x, 1, 0, "barrel", facing="south", open="false")
    for x in range(1, n - 1):
        p.set(x, 2, 0, "barrel", facing="south", open="false")
    return p


def timber_stack(length=4, rows=2):
    """Baulks of spruce and mangrove stacked on chocks, along x."""
    p = TPart()
    for x in range(length):
        for r in range(rows):
            p.log(x, 1 + r, r % 2, "x", "post2" if r % 2 else "mpost")
    return p


def catalogue():
    out = {"lighthouse": lighthouse(11, galleries=())[0], "lighthouse_l3": lighthouse(19, galleries=(14,))[0],
           "trestle": trestle(), "gantry_beam": gantry_beam(9, 3), "sloop": sloop(), "hard_hull": hard_hull(9),
           "bollard": bollard(), "barrel_stack": barrel_stack(),
           "timber_stack": timber_stack()}
    r = TPart()
    roof_ridge_z(r, 0, 4, 0, 8, 5, gable_ends=(8,))
    out["roof_ridge_z"] = r
    r = TPart()
    roof_ridge_x(r, 0, 8, 0, 7, 5, gable_ends=(0,))
    dormer_south(r, 2, 3, 7, 6)
    out["roof_ridge_x"] = r
    return out


if __name__ == "__main__":
    for k, v in catalogue().items():
        b = v.bbox()
        print("%-14s %3d x %3d x %3d  %4d blocks" % (k, b[1] - b[0] + 1, b[3] - b[2] + 1, b[5] - b[4] + 1, len(v.b)))
