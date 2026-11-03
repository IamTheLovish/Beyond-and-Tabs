#!/usr/bin/env python3
"""A PART LIBRARY for the Stormcallers' larger buildings (first user: the Lantern Arsenal vehicle lab,
tools/lab_vehicle_storm.py). The Stormcallers kit (tools/lab_lotus_academy.py, tools/capitol_jade_pagoda.py) drawn as
reusable, well-made elements instead of block-by-block code:

  hip_roof      a flared jade hip roof on any rectangle: upturned gold corners under slab tips, lifted cells beside
                them, dark-oak rafter ends under the lip, a flat band, courses in two jade tones, a ridge with gold ends
  bell_dome     the swelling jade bell cap of ref07 (flare, bulge, shoulder, gold finial, rod)
  bracket_ring  dougong under an eave: stacked corbels (top stairs) on every post, a beam course between
  storey       a lacquered storey on a rectangle: red posts, dark-oak dado and lintel, red paper screens lit from a
                room behind (light comes from windows, not scattered lanterns), calcite plaster friezes
  gallery       a ring of balcony rail (fences) on a slab floor, carried by corbels (ref17's towers)
  plinth        the stone podium with the fret course (meander: chiseled blocks between top stairs), rock-faced on
                the crag sides, hollow under its top course (no ground pockets: a 1-high void is not walkable)
  crag          rock outcrops in zones (andesite / tuff / cobble / moss) with grass and moss on the steps
  stone_lantern the tooro; cherry_tree / spruce_tree with true leaf distances (set by the canvas)
  armillary     interlocking open rings (jade walls + gold) round a floating lodestone - ref27's crown

CONVENTIONS (as gothic_parts): everything goes through a Canvas (a gen_structures._SunRig) in the building's own block
space, x west -> east, z north -> south, y = 0 the ground layer; parts are symmetric or take an explicit `face`
(the side their front looks to), so the same part serves any side; a part never writes outside the box (clipped) and
never into a guarded volume (Canvas.guard: the exit lane), which raises. Nothing pops off: lanterns stand on posts or
hang from chains, rods and chains need nothing, leaves get their true distance.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs              # noqa: E402
import lab_lotus_academy as la           # noqa: E402

DRY = dict(waterlogged="false")
JADE, JADE_ST, JADE_SLAB = "waxed_oxidized_cut_copper", "waxed_oxidized_cut_copper_stairs", "waxed_oxidized_cut_copper_slab"
JADE2, JADE2_ST, JADE2_SLAB = "dark_prismarine", "dark_prismarine_stairs", "dark_prismarine_slab"   # the darker course
OAK, OAK_LOG, OAK_ST, OAK_SLAB, OAK_FENCE = "dark_oak_planks", "dark_oak_log", "dark_oak_stairs", "dark_oak_slab", "dark_oak_fence"
OAK_TD = "dark_oak_trapdoor"
RED = "stripped_mangrove_log"            # red lacquer posts
RED_PL, RED_ST, RED_SLAB, RED_FENCE = "mangrove_planks", "mangrove_stairs", "mangrove_slab", "mangrove_fence"
LACQ = "red_terracotta"                  # lacquered friezes / tablets' ground
PAPER = "red_stained_glass_pane"         # paper screens, lit from inside
PLASTER = "calcite"
GOLD = "gold_block"
LIGHT = "shroomlight"
SB, SB_ST, SB_SLAB, SB_WALL = "stone_bricks", "stone_brick_stairs", "stone_brick_slab", "stone_brick_wall"
CSB = "chiseled_stone_bricks"
OPP = gs._SUN_OPP
DIRS = ("north", "east", "south", "west")


def hsh(x, z, s=0):
    return ((x + 31 + s * 7) * 73856093 ^ (z + 17 - s * 3) * 19349663) % 101


class GuardError(AssertionError):
    pass


class Canvas(gs._SunRig):
    """The building's canvas: _SunRig (settle() shapes stairs, joins walls / fences / panes like the game) plus the
    kit's writers, clipping to the box and a guard on the exit lane."""

    def __init__(self, sx, sy, sz, guard=None):
        super().__init__(sx, sy, sz)
        self.guard = guard or (lambda x, y, z: False)

    def inside(self, x, y, z):
        return 0 <= x < self.size[0] and 0 <= y < self.size[1] and 0 <= z < self.size[2]

    def put(self, x, y, z, name, **p):
        if not self.inside(x, y, z):
            return
        if name not in ("air", "cave_air") and self.guard(x, y, z):
            raise GuardError("drawn into the exit lane: %s at %s" % (name, (x, y, z)))
        if name == "air":
            self.b.pop((x, y, z), None)
            return
        self.b[(x, y, z)] = (name, p)

    def free(self, x, y, z):
        return self.inside(x, y, z) and self.get(x, y, z)[0] == "air" and not self.guard(x, y, z)

    def st(self, x, y, z, facing, half="bottom", name=JADE_ST):
        self.put(x, y, z, name, facing=facing, half=half, shape="straight", **DRY)

    def slab(self, x, y, z, name=JADE_SLAB, typ="bottom"):
        self.put(x, y, z, name, type=typ, **DRY)

    def fill(self, x1, y1, z1, x2, y2, z2, name, **p):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.put(x, y, z, name, **p)

    def log(self, x, y, z, name=OAK_LOG, axis="y"):
        self.put(x, y, z, name, axis=axis)

    def lantern(self, x, y, z, hanging=False):
        self.put(x, y, z, "lantern", hanging="true" if hanging else "false", **DRY)

    def rod(self, x, y, z, facing="up"):
        self.put(x, y, z, "lightning_rod", facing=facing, powered="false", **DRY)

    def chain(self, x, y, z, axis="y"):
        self.put(x, y, z, "chain", axis=axis, **DRY)

    def leaf(self, x, y, z, name="cherry_leaves"):
        if self.free(x, y, z):
            self.put(x, y, z, name, distance="1", persistent="true", **DRY)

    def leaves(self):
        la.Rig.leaves(self)

    def check_support(self, tier):
        la.Rig.check_support(self, tier)


def rect_ring(x1, x2, z1, z2):
    """The perimeter cells of [x1..x2] x [z1..z2] with the side each one faces out to (corners: None)."""
    out = []
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            ex, ez = x in (x1, x2), z in (z1, z2)
            if not (ex or ez):
                continue
            if ex and ez:
                out.append((x, z, None))
            elif ez:
                out.append((x, z, "north" if z == z1 else "south"))
            else:
                out.append((x, z, "west" if x == x1 else "east"))
    return out


# ============================================================================================ roofs
def hip_roof(c, x1, x2, z1, z2, y, band=True, rafters=True, lift=True, finial=True, tones=(JADE_ST, JADE2_ST),
             rings=99, band_slab=JADE_SLAB, lip=JADE_ST, cap=False):
    """A flared jade hip roof with its lip on [x1..x2] x [z1..z2] at y.
    - ring 0 (the lip): jade stairs, low edge out; each corner a gold block under an upturned jade slab tip, and (on
      sides of 5+) the cell beside a corner a jade block under a slab - the eave sags in the middle and sweeps up;
    - dark-oak rafter ends under the lip (every other cell, only into air);
    - ring 1: the flat jade BAND at the lip's height (band=True) - the curve read from the side; then each ring climbs
      one, its stairs alternating between the two jade tones so the roof reads in courses, until the ring is a line
      (the ridge: jade blocks, a gold block at each end with a rod) or a point (a gold boss with a rod).
    Returns the lip corners (for chimes)."""
    k, yy = 0, y
    corners = [(x1, z1), (x2, z1), (x1, z2), (x2, z2)]
    while k < rings:
        a1, a2, b1, b2 = x1 + k, x2 - k, z1 + k, z2 - k
        if a1 > a2 or b1 > b2:
            break
        w, d = a2 - a1 + 1, b2 - b1 + 1
        if k == 1 and band:
            for (x, z, f) in rect_ring(a1, a2, b1, b2):
                c.slab(x, yy, z, band_slab, "top")
        elif min(w, d) == 1:
            # the ridge
            cells = [(x, z) for x in range(a1, a2 + 1) for z in range(b1, b2 + 1)]
            for (x, z) in cells:
                c.put(x, yy, z, JADE)
            if finial:
                ends = [cells[0], cells[-1]] if len(cells) > 1 else [cells[0]]
                for (x, z) in ends:
                    c.put(x, yy, z, GOLD)
                    c.rod(x, yy + 1, z)
            break
        else:
            name = tones[(k + (0 if band else 1)) % 2] if k else lip
            for (x, z, f) in rect_ring(a1, a2, b1, b2):
                if f is None:
                    if k == 0:
                        c.put(x, yy, z, GOLD)
                        c.slab(x, yy + 1, z)
                    else:
                        # the hip: a stair facing into the roof diagonal, settled into an outer corner
                        fz = "south" if z == b1 else "north"
                        c.st(x, yy, z, fz, name=name)
                    continue
                inward = OPP[f]
                near = k == 0 and lift and (
                    (f in ("north", "south") and x in (a1 + 1, a2 - 1) and w >= 5) or
                    (f in ("west", "east") and z in (b1 + 1, b2 - 1) and d >= 5))
                if near:
                    c.put(x, yy, z, JADE)
                    c.slab(x, yy + 1, z)
                else:
                    c.st(x, yy, z, inward, name=name)
                if k == 0 and rafters and (x + z) % 2 == 0 and c.free(x, yy - 1, z):
                    c.st(x, yy - 1, z, inward, "top", OAK_ST)
            if min(w, d) == 2:
                # two rows of stairs meeting: the ridge is their peak; gold ends with rods along the long axis
                if finial:
                    if w >= d:
                        ends = [(a1, b1), (a2, b1)]
                    else:
                        ends = [(a1, b1), (a1, b2)]
                    for (x, z) in ends:
                        c.rod(x, yy + 1, z)
                break
        k += 1
        if not (k == 1 and band):
            yy += 1
    if cap and k == rings and x1 + k <= x2 - k and z1 + k <= z2 - k:
        # a flat-topped hip (the ranges stay under the tower): a dark slab deck with a jade ridge course on its axis
        a1, a2, b1, b2 = x1 + k, x2 - k, z1 + k, z2 - k
        for x in range(a1, a2 + 1):
            for z in range(b1, b2 + 1):
                c.slab(x, yy - 1, z, JADE2_SLAB, "top")
        if a2 - a1 >= b2 - b1:
            zc = (b1 + b2) // 2
            for x in range(a1, a2 + 1):
                c.slab(x, yy, zc, JADE_SLAB)
            for x in (a1, a2):
                c.put(x, yy, zc, GOLD)
                c.rod(x, yy + 1, zc)
    return [(x, y - 1, z) for (x, z) in corners]


def bell_dome(c, cx, cz, y, finial_h=2):
    """ref07's bell cap over a 3 x 3 body centred (cx, cz), its lip 5 x 5 at y: a flared stair lip with gold corners
    and slab tips, a swelling jade drum, a shoulder of stairs, a crown, a gold boss and a rod finial."""
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            x, z = cx + dx, cz + dz
            if abs(dx) == 2 and abs(dz) == 2:
                c.put(x, y, z, GOLD)
                c.slab(x, y + 1, z)
            elif abs(dx) == 2 or abs(dz) == 2:
                f = "east" if dx == -2 else "west" if dx == 2 else "south" if dz == -2 else "north"
                c.st(x, y, z, f)
            else:
                c.put(x, y, z, JADE)
    # the swell (5 x 5 minus corners): full jade with a gold band on the mids
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if abs(dx) == 2 and abs(dz) == 2:
                continue
            ring = abs(dx) == 2 or abs(dz) == 2
            c.put(cx + dx, y + 1, cz + dz, JADE2 if ring and (dx + dz) % 2 else JADE)
    # the shoulder: stairs facing in on the swell's edge, the 3 x 3 full
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if abs(dx) == 2 and abs(dz) == 2:
                continue
            x, z = cx + dx, cz + dz
            if abs(dx) == 2 or abs(dz) == 2:
                f = "east" if dx == -2 else "west" if dx == 2 else "south" if dz == -2 else "north"
                c.st(x, y + 2, z, f)
            else:
                c.put(x, y + 2, z, JADE)
    # the crown: the 3 x 3's edges as stairs, gold corners low, the centre full
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            x, z = cx + dx, cz + dz
            if dx == 0 and dz == 0:
                c.put(x, y + 3, z, JADE)
            elif dx and dz:
                c.slab(x, y + 3, z, JADE2_SLAB)
            else:
                f = "east" if dx == -1 else "west" if dx == 1 else "south" if dz == -1 else "north"
                c.st(x, y + 3, z, f)
    c.put(cx, y + 4, cz, GOLD)
    for i in range(finial_h):
        c.rod(cx, y + 5 + i, cz) if i == finial_h - 1 else c.put(cx, y + 5 + i, cz, "end_rod", facing="up")
    return [(cx + dx, y - 1, cz + dz) for dx in (-2, 2) for dz in (-2, 2)]


# ============================================================================================ walls
def bracket_ring(c, x1, x2, z1, z2, y, posts):
    """Dougong at y under an eave whose lip is one ring out: on every post cell of the ring [x1..x2] x [z1..z2] a
    corbel (top stair, its full back against the wall) stepping out, between them a dark-oak beam (log along the
    side) - a lacquered architrave with bracket heads."""
    for (x, z, f) in rect_ring(x1, x2, z1, z2):
        if f is None:
            c.put(x, y, z, GOLD)
            continue
        axis = "x" if f in ("north", "south") else "z"
        if (x, z) in posts:
            c.put(x, y, z, LACQ)
        else:
            c.log(x, y, z, axis=axis)
        dx, dz = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}[f]
        if (x, z) in posts and c.free(x + dx, y, z + dz):
            c.st(x + dx, y, z + dz, OPP[f], "top", OAK_ST)


def storey(c, x1, x2, z1, z2, y0, y1, posts_every=2, lit=True, plaster_top=True, skip=(), dado=True):
    """A lacquered storey on the ring [x1..x2] x [z1..z2], walls y0..y1: red posts on the corners and every
    `posts_every` cells, between them a dark-oak dado (y0), red paper screens, a calcite plaster frieze under the
    lintel (when the storey is 4+ high), a dark-oak lintel log at y1. Behind every screen a lit room: a shroomlight
    in the ceiling over the floor (y0 - 1 is the floor; it must be solid under the room). Returns the post cells."""
    posts = set()
    for (x, z, f) in rect_ring(x1, x2, z1, z2):
        if (x, z) in skip:
            continue
        along = (x - x1) if f in ("north", "south") else (z - z1)
        span = (x2 - x1) if f in ("north", "south") else (z2 - z1)
        if f is None or along % posts_every == 0 or along == span:
            posts.add((x, z))
            for y in range(y0, y1 + 1):
                c.log(x, y, z, RED)
            continue
        axis = "x" if f in ("north", "south") else "z"
        h = y1 - y0 + 1
        for y in range(y0, y1 + 1):
            if y == y1:
                c.log(x, y, z, axis=axis)
            elif y == y0 and dado and h >= 3:
                c.put(x, y, z, OAK)
            elif y == y1 - 1 and plaster_top and h >= 4:
                c.put(x, y, z, PLASTER)
            else:
                c.put(x, y, z, PAPER)
    # the room behind: a dark-oak ceiling with lights, so every screen glows
    for x in range(x1 + 1, x2):
        for z in range(z1 + 1, z2):
            c.put(x, y1, z, LIGHT if lit and (x + z) % 2 == 0 else OAK)
    if lit and (x2 - x1 <= 2 or z2 - z1 <= 2):
        for x in range(x1 + 1, x2):
            for z in range(z1 + 1, z2):
                c.put(x, y0, z, LIGHT)
    return posts


def gallery(c, x1, x2, z1, z2, y, floor=True, rail=OAK_FENCE, corbels=True, gaps=()):
    """A balcony round a storey: a dark-oak slab floor on the ring [x1..x2] x [z1..z2] at y (top slab, so it reads
    thin), a fence rail on it at y + 1, and corbels (top stairs facing in) under every other cell."""
    for (x, z, f) in rect_ring(x1, x2, z1, z2):
        if floor:
            c.slab(x, y, z, OAK_SLAB, "top")
        if (x, z) not in gaps:
            c.put(x, y + 1, z, rail)
        if corbels and f and (x + z) % 2 == 0 and c.free(x, y - 1, z):
            c.st(x, y - 1, z, OPP[f], "top", OAK_ST)


def plinth(c, x1, x2, z1, z2, top=2, rock_faces=("west", "south"), seed=0, fret=True):
    """The stone podium: rock-faced (andesite / tuff / cobble / mossy) on the crag sides it names, dressed stone
    bricks elsewhere, a fret course on top (chiseled bricks alternating with top stone-brick stairs, the step cut on
    the outer face), a smooth top inside. Hollow under the top course where a 1-high void cannot hold a unit."""
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            edge = x in (x1, x2) or z in (z1, z2)
            for y in range(1, top + 1):
                if y == top:
                    if edge and fret:
                        f = "north" if z == z1 else "south" if z == z2 else "west" if x == x1 else "east"
                        if (x + z) % 2:
                            c.put(x, y, z, CSB)
                        else:
                            c.st(x, y, z, OPP[f], "top", SB_ST)
                    else:
                        if edge:
                            c.put(x, y, z, SB)
                        else:
                            c.slab(x, y, z, "smooth_stone_slab", "top")      # the floor: a slab deck
                elif edge:
                    faces = [f for f, ok in (("west", x == x1), ("east", x == x2), ("north", z == z1),
                                             ("south", z == z2)) if ok]
                    if any(f in rock_faces for f in faces):
                        h = hsh(x, z + y * 5, seed) % 10
                        c.put(x, y, z, ("andesite", "tuff", "cobblestone", "mossy_cobblestone", "stone",
                                        "andesite", "tuff", "mossy_stone_bricks", "cobblestone", "stone")[h])
                    else:
                        c.put(x, y, z, SB if hsh(x, z, y) % 7 else "cracked_stone_bricks")
                elif y < top - 1:
                    c.put(x, y, z, SB)


def crag(c, cells, seed=1):
    """Rock outcrops: for each (x, z, h) a column of rock to y h with moss / grass on its top and stone stairs
    stepping down to its lower neighbours."""
    rock = ("andesite", "tuff", "stone", "cobblestone", "andesite", "mossy_cobblestone", "tuff", "stone")
    hmap = {(x, z): h for (x, z, h) in cells}
    for (x, z, h) in cells:
        for y in range(1, h + 1):
            c.put(x, y, z, rock[hsh(x * 3 + y, z, seed) % len(rock)])
        top = "moss_block" if hsh(x, z, seed + 2) % 3 == 0 else "grass_block"
        c.put(x, h, z, top)
    # the steps: where a column stands over a lower neighbour (or the outside edge), a stone stair steps down
    for (x, z, h) in cells:
        for (dx, dz, f) in ((-1, 0, "west"), (1, 0, "east"), (0, -1, "north"), (0, 1, "south")):
            nh = hmap.get((x + dx, z + dz), 0 if not c.inside(x + dx, 1, z + dz) else None)
            if nh is not None and nh < h and c.free(x, h + 1, z) and hsh(x, z, seed + 7) % 2 == 0:
                c.st(x, h + 1, z, OPP[f], name="mossy_cobblestone_stairs" if hsh(x, z, 1) % 2 else "stone_stairs")
                break


def stone_lantern(c, x, z, y=1):
    """A tooro: a fret foot, a stone-brick wall post, a paper lantern, a slab hood with a jade cap."""
    c.put(x, y, z, CSB)
    c.put(x, y + 1, z, SB_WALL)
    c.lantern(x, y + 2, z)
    c.slab(x, y + 3, z, SB_SLAB)


def cherry_tree(c, x, y, z, lean="west", h=3, crown=2):
    """A blossom tree: a cherry trunk leaning out, a layered crown of cherry leaves with flowering azalea mixed in."""
    dx, dz = {"west": (-1, 0), "east": (1, 0), "south": (0, 1), "north": (0, -1)}[lean]
    c.log(x, y, z, OAK_LOG)
    for i in range(1, h):
        c.log(x, y + i, z, OAK_LOG)
    tx, tz, ty = x + dx, z + dz, y + h - 1
    c.log(tx, ty, tz, OAK_LOG, axis="x" if dx else "z")
    for ox in range(-crown - 1, crown + 2):
        for oz in range(-crown - 1, crown + 2):
            for oy in (-1, 0, 1, 2):
                r = abs(ox) + abs(oz) + abs(oy - 0.5) * 1.6
                if r <= crown + 1.2 and hsh(tx + ox * 3 + oy, tz + oz, 11) % 5:
                    c.leaf(tx + ox, ty + oy, tz + oz,
                           "flowering_azalea_leaves" if hsh(ox, oz + oy, 4) % 7 == 0 else "cherry_leaves")


def spruce_tree(c, x, y, z, h=8):
    """A dark conifer to break the outline: a spruce trunk, tiers of spruce leaves narrowing up."""
    for i in range(h):
        c.log(x, y + i, z, "spruce_log")
    for i in range(2, h + 2):
        r = 2 if i < h - 3 and i % 2 == 0 else 1 if i < h else 0
        for ox in range(-r, r + 1):
            for oz in range(-r, r + 1):
                if abs(ox) + abs(oz) <= r + (1 if r == 2 else 0):
                    c.leaf(x + ox, y + i, z + oz, "spruce_leaves")


def armillary(c, cx, cy, cz, r=3):
    """ref27's crown: three open rings round a floating lodestone - a jade meridian (x-y plane), a jade colure (z-y
    plane) and a gold equator (x-z plane) - wider than the tower, mass-less from above. Walls and chains need no
    support; the arcs between the rings are drawn by the client (ANCHORS)."""
    pts = set()
    for i in range(96):
        a = 2 * math.pi * i / 96
        u, v = round(r * math.cos(a)), round(r * math.sin(a))
        pts.add(("m", u, v))
    for (_, u, v) in pts:
        if (cx + u, cy + v, cz) not in c.b:
            c.put(cx + u, cy + v, cz, "prismarine_wall")
        c.put(cx, cy + v, cz + u, "waxed_cut_copper_slab" if abs(v) == r else "chain",
              **({"type": "top" if v < 0 else "bottom", "waterlogged": "false"} if abs(v) == r else
                 {"axis": "y", "waterlogged": "false"}))
    for (_, u, v) in pts:
        if abs(u) == r or abs(v) == r:
            c.put(cx + u, cy, cz + v, GOLD)
        else:
            c.slab(cx + u, cy, cz + v, "waxed_cut_copper_slab")
    c.put(cx, cy, cz, "lodestone")
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        c.put(cx + dx, cy, cz + dz, "air")
    c.chain(cx, cy + 1, cz)
    c.chain(cx, cy - 1, cz)


def beam_roof(c, x1, x2, zc, y, ridge=JADE):
    """A covered gantry beam (a roofed bridge span): a 3-deep jade gable along x over the beam line zc at y - stairs
    on z zc-1 / zc+1 facing in, a ridge of jade on zc, gold ends with upturned slab tips and rods, dark-oak rafter
    ends under the lips every other cell."""
    for x in range(x1, x2 + 1):
        end = x in (x1, x2)
        for dz, f in ((-1, "south"), (1, "north")):
            if end:
                c.put(x, y, zc + dz, GOLD)
                c.slab(x, y + 1, zc + dz)
            else:
                c.st(x, y, zc + dz, f)
                if x % 2 == 0 and c.free(x, y - 1, zc + dz):
                    c.st(x, y - 1, zc + dz, f, "top", OAK_ST)
        c.put(x, y, zc, ridge if not end else GOLD)
        if not end:
            c.slab(x, y + 1, zc, JADE2_SLAB)
    c.rod(x1, y + 1, zc)
    c.rod(x2, y + 1, zc)
    return [(x, y - 1, zc + dz) for x in (x1, x2) for dz in (-1, 1)]


# ============================================================================================ round 2 parts
# The round-1 critic (4/10): one roof slab on a box, no landmark, no yard, no gate, flat roofs, gold noise, scattered
# light. These parts answer the roof language: a PITCHED hip (stairs climbing one per ring, no flat band), a dark-oak
# UNDERSIDE one deep under the lip and bracket arms in the shadow under the second ring, upturned corner tips (a
# stair under an inverted stair), gold ONLY at the ridge ends; a PAGODA TIER (the same eave on a square body with its
# bracket set and a gallery rail); a SCREEN WALL (red posts, white glass panes two high, light behind them); a plinth
# on any cell set (one fret course round an L); the half-built ROCKET CART for the yard.
WHITE = "white_stained_glass_pane"
DEEP, DEEP_ST, DEEP_SLAB = "polished_deepslate", "polished_deepslate_stairs", "polished_deepslate_slab"
COPPER, COPPER_ST, COPPER_SLAB = "waxed_copper_block", "waxed_cut_copper_stairs", "waxed_cut_copper_slab"
TONES = {"light": (JADE_ST, JADE, JADE_SLAB), "dark": (JADE2_ST, JADE2, JADE2_SLAB)}


def pitched_hip(c, x1, x2, z1, z2, y, tone="light", under=True, brackets=(), tips=True, gold_ends=True, soft=False):
    """A pitched hip roof whose lip is the ring [x1..x2] x [z1..z2] at y: every ring in is one higher (stairs facing
    in, the corners settle to the hip diagonals), the last ring a ridge line of jade blocks (the other tone) with a
    gold block at each end; a 2-wide last ring is a peaked pair of stairs. Under the lip a dark-oak underside (top
    slabs, one deep); in the shadow under the second ring, bracket arms (dark-oak top stairs) at the `brackets`
    (x, z) cells. The corners carry an upturned tip: an inverted jade stair above the corner stair facing out. With
    soft=True nothing already drawn is overwritten (a roof dying into a wall)."""
    st, full, slab = TONES[tone]
    st2, full2, slab2 = TONES["dark" if tone == "light" else "light"]

    def put(x, yy, z, name, **p):
        if soft and not c.free(x, yy, z):
            return
        c.put(x, yy, z, name, **p)

    k, yy = 0, y
    while True:
        a1, a2, b1, b2 = x1 + k, x2 - k, z1 + k, z2 - k
        if a1 > a2 or b1 > b2:
            break
        w, d = a2 - a1 + 1, b2 - b1 + 1
        if min(w, d) == 1:
            cells = [(x, z) for x in range(a1, a2 + 1) for z in range(b1, b2 + 1)]
            for (x, z) in cells:
                put(x, yy, z, full2)
            if gold_ends and len(cells) > 1:
                for (x, z) in (cells[0], cells[-1]):
                    put(x, yy, z, GOLD)
            break
        if min(w, d) == 2:
            # a peaked pair: each row's tall side toward the middle
            for x in range(a1, a2 + 1):
                for z in range(b1, b2 + 1):
                    if w >= d:
                        f = "north" if z == b1 else "south"
                    else:
                        f = "west" if x == a1 else "east"
                    put(x, yy, z, st2, facing=f, half="bottom", shape="straight", **DRY)
            if gold_ends:
                ends = [(a1, b1), (a1, b2), (a2, b1), (a2, b2)] if w >= d else [(a1, b1), (a2, b1), (a1, b2), (a2, b2)]
                for (x, z) in ends:
                    put(x, yy, z, GOLD)
            break
        for (x, z, f) in rect_ring(a1, a2, b1, b2):
            if f is None:
                fz = "south" if z == b1 else "north"
                put(x, yy, z, st, facing=fz, half="bottom", shape="straight", **DRY)
                if k == 0 and tips:
                    fo = "north" if z == b1 else "south"
                    put(x, yy + 1, z, st, facing=fo, half="top", shape="straight", **DRY)
                continue
            put(x, yy, z, st, facing=OPP[f], half="bottom", shape="straight", **DRY)
            if k == 0 and under and c.free(x, yy - 1, z):
                c.slab(x, yy - 1, z, OAK_SLAB, "top")
        if k == 1:
            for (x, z, f) in rect_ring(a1, a2, b1, b2):
                if f and (x, z) in brackets and c.free(x, yy - 1, z):
                    c.st(x, yy - 1, z, OPP[f], "top", OAK_ST)
        k += 1
        yy += 1
    return yy


def screen_wall(c, x1, x2, z1, z2, y0, y1, posts_every=2, lit=True, skip=(), dado=True, open_cells=()):
    """The lacquer colonnade (the critic's keep): red posts at the corners and every `posts_every` cells, between
    them a dark-oak sill (y0), WHITE glass panes two high, a dark-oak lintel (y1); the row behind every pane a
    shroomlight so the screen glows (light behind paper); the ceiling dark oak. Cells in `open_cells` stay open
    (doorways). Returns the post cells."""
    posts = set()
    inner = {}
    for (x, z, f) in rect_ring(x1, x2, z1, z2):
        if (x, z) in skip:
            continue
        along = (x - x1) if f in ("north", "south") else (z - z1)
        span = (x2 - x1) if f in ("north", "south") else (z2 - z1)
        if f is None or along % posts_every == 0 or along == span:
            posts.add((x, z))
            for y in range(y0, y1 + 1):
                c.log(x, y, z, RED)
            continue
        axis = "x" if f in ("north", "south") else "z"
        for y in range(y0, y1 + 1):
            if (x, z) in open_cells and y < y1:
                continue
            if y == y1:
                c.log(x, y, z, axis=axis)
            elif y == y0 and dado and y1 - y0 >= 3:
                c.put(x, y, z, OAK)
            else:
                c.put(x, y, z, WHITE, **DRY)
                if lit:
                    dx, dz = {"north": (0, 1), "south": (0, -1), "west": (1, 0), "east": (-1, 0)}[f]
                    inner[(x + dx, y, z + dz)] = LIGHT
    for (x, y, z), n in inner.items():
        if x1 < x < x2 and z1 < z < z2:
            c.put(x, y, z, n)
    for x in range(x1 + 1, x2):
        for z in range(z1 + 1, z2):
            if c.free(x, y1, z):
                c.put(x, y1, z, OAK)
    return posts


def plinth_cells(c, cells, top=2, seed=0, fret=True):
    """The stone podium on any set of (x, z) cells: a fret course on its outer edge (chiseled bricks alternating with
    top stone-brick stairs), rock-faced (andesite / tuff / cobble / moss) where the edge is the footprint's own edge,
    dressed stone bricks where it faces the yard; a smooth-stone slab deck inside, hollow underneath."""
    cells = set(cells)
    sx, sz = c.size[0], c.size[2]
    for (x, z) in cells:
        out = {"west": (x - 1, z) not in cells, "east": (x + 1, z) not in cells,
               "north": (x, z - 1) not in cells, "south": (x, z + 1) not in cells}
        edge = any(out.values())
        for y in range(1, top + 1):
            if y == top:
                if edge and fret:
                    f = next(d for d in ("south", "west", "north", "east") if out[d])
                    if (x + z) % 2:
                        c.put(x, y, z, CSB)
                    else:
                        c.st(x, y, z, OPP[f], "top", SB_ST)
                elif edge:
                    c.put(x, y, z, SB)
                else:
                    c.slab(x, y, z, "smooth_stone_slab", "top")
            elif edge:
                rim = (out["west"] and x == 0) or (out["east"] and x == sx - 1) or \
                      (out["north"] and z == 0) or (out["south"] and z == sz - 1)
                if rim:
                    h = hsh(x, z + y * 5, seed) % 10
                    c.put(x, y, z, ("andesite", "tuff", "cobblestone", "mossy_cobblestone", "stone", "andesite",
                                    "mossy_stone_bricks", "mossy_stone_bricks", "cobblestone", "stone")[h])
                else:
                    c.put(x, y, z, SB if hsh(x, z, y) % 5 else "mossy_stone_bricks")
            elif y < top - 1:
                c.put(x, y, z, SB)


def rocket_cart(c, x, z, y=1):
    """The half-built ROCKET BATTERY in the yard (a hwacha: a two-wheeled cart with a rack of fire-arrow tubes angled
    up), its nose at (x, z) pointing west: dark-oak wheels (trapdoors) on a stripped-mangrove axle, a dark-oak bed,
    the rack of waxed copper tubes (stairs and slabs tilting up toward the gate) in red lacquer cheeks, one tube
    row still missing (half built); a crate of tubes beside it."""
    # axle and wheels along z at x + 1
    for dz in (0, 2):
        c.put(x + 1, y, z + dz, OAK_TD, facing="east", half="bottom", open="true", powered="false", **DRY)
        c.put(x + 3, y, z + dz, OAK_TD, facing="west", half="bottom", open="true", powered="false", **DRY)
    c.log(x + 1, y, z + 1, RED, axis="x")
    c.log(x + 2, y, z + 1, RED, axis="x")
    c.log(x + 3, y, z + 1, RED, axis="x")
    # the shafts (the pole toward the gate) and the bed
    c.put(x, y, z + 1, OAK_FENCE)
    for dz in (0, 2):
        c.slab(x + 2, y + 1, z + dz, OAK_SLAB, "bottom")
    # the rack: cheeks of red lacquer, tubes of waxed copper tilted up to the west
    for dz in (0, 2):
        c.st(x + 2, y + 1, z + dz, "west", "bottom", RED_ST)
        c.st(x + 3, y + 2, z + dz, "west", "bottom", RED_ST)
    c.st(x + 1, y + 1, z + 1, "west", "bottom", COPPER_ST)
    c.put(x + 2, y + 1, z + 1, COPPER)
    c.st(x + 2, y + 2, z + 1, "west", "bottom", COPPER_ST)
    c.put(x + 3, y + 2, z + 1, COPPER)
    c.slab(x + 3, y + 1, z + 1, COPPER_SLAB, "bottom")
    # the crate of tubes beside the cart (a barrel-like stack): copper slabs on a dark-oak crate
    c.put(x + 4, y, z, OAK)
    c.slab(x + 4, y + 1, z, COPPER_SLAB, "bottom")
    c.put(x + 4, y, z + 2, OAK)
