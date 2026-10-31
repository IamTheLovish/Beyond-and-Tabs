#!/usr/bin/env python3
"""CONCEPT - the Verdant vehicle lab, THE ROOTCRADLE, L1 and L3 (design/vehicle_labs.md; not wired into the game).
Sentinel Treants (walking trees, 1.5 wide x 3 tall, they can Take Root) are grown and woken here. No machinery: the
base is a forest. Assembled from tools/verdant_lab_parts.py (the Verdant part library).

ANCHORS (structure-local block coordinates: x 0..16 west->east, y up, z 0..16 north->south; client-drawn moving parts
in game, drawn here as static blocks):
  spawn         (8, 1, 8)    the waking ring's centre over the floor heart (a verdant froglight at (8, 0, 8)); the
                column over it is air to the top at every level (units drop in from the box centre)
  exit lane     x 0..8, z 6..10, y 1..8, straight out through the WEST face (the gopura's passage)
  gantry beam   the CRADLE BOUGH: a living root bridge along x at z 7, y 13, x 3..12 - its west end gripping the
                gopura's pediment back (x 2), its east end grown into the great tree and standing on a strangler-root
                pier (12, 1..12, 9); woven root deck z 5..9 (an oculus left open over the spawn), handrail roots on
                z 5 / z 9 - static
  trolley       (6, 13, 7)   the bough-knot the cradle hangs from; runs along x 4..11
  hoist         (6, 9, 7)    the ROOT CRADLE: a woven 3 x 3 basket (x 5..7, z 6..8, y 9..10) holding a sleeping treant
                (y 10..13), on four chains (x 5 / 7, z 6 / 8, y 11..13); lowers to the floor beside the spawn, lifts
                back to y 9 (the lane's top is y 8)
  gate leaf     two root curtains hinged at the gopura's inner jambs, vertical axes at (1.5, 1..8, 5.5) north and
                (1.5, 1..8, 11.5) south; drawn OPEN (furled on the jambs, x 1..2, z 5 and z 11); closed they meet at z 8
  heart-glow    (8, 0, 8) the floor heart; (11, 3, 6) the heart in the tree's foot; the stag-dryad's eyes on the
                gopura (0, 11, 7) / (0, 11, 9)
  L3 adds       a second gantry tier: the HIGH BOUGH along x at z 9, y 18, x 2..11, trolley (5, 18, 9), a second cradle
                (5, 14, 9) - basket x 4..6, z 8..10, y 14..15; and the landmark grows: the great tree's crown rises to
                y 27 with the GREAT STAG-DRYAD carved in its upper trunk looking west over the gate (eyes (9, 22, 4) / (9, 22, 6)),
                antlers across the sky

THE REFERENCE, STUDIED
  Ta Prohm (Angkor, 1186, Jayavarman VII): a monastery of concentric enclosures of sandstone galleries on laterite
  footings, left to the jungle on purpose. What makes it read:
  - THE GALLERY: a long low range on a raised laterite terrace (a moulded plinth, one step up), an outer wall pierced by
    windows of TURNED BALUSTERS, a pillared half-gallery (lean-to) in front of it, the corridor behind under a CORBELLED
    roof - stone courses stepping in to a ridge with a cresting. Bays are strongly rhythmic: pillar, window, pillar.
    Corner towers (prasats) mark the angles: square, a cella with blind doors, a cornice, tiers receding, antefixes on
    every corner, the tops often fallen.
  - THE TREES: silk-cotton (Tetrameles, pale bark) and strangler figs (dark, braided) grow ON the galleries; their
    roots pour down the walls like poured wax, braided, wandering, flaring where they meet the ground, prising blocks
    apart; a tree sits on a roof and its roots hold the wall together as much as they break it. Moss and lichen green
    the stone in calm zones; cracked and fallen blocks in places.
  - Banteay Srei (967): the gate pavilions' DOOR FRAMES - octagonal colonnettes on the jambs, a carved lintel, a
    STEPPED FLAME PEDIMENT (frames nesting, the rake ending in curling naga heads), the tympanum recessed and carved,
    blind-door niches and pilasters on the piers. Deep relief = three depth planes on one face.
  - The living root bridges of Meghalaya (Khasi, Ficus elastica): aerial roots led across a gap through hollow betel
    palm guides for 15-30 years, fused into a mesh deck filled with stones, a thick root as each handrail with side
    roots dropping from it into the deck - the model for the gantry (a bough with a woven root deck and handrail roots)
    and the hoist (a woven root cradle).
  - lovish's refs: ref06 (a hall grown inside a colossal twisted tree - many bark tones, ridges spiralling, roots flaring
    like buttresses, a huge layered canopy as THE landmark), ref03 (vegetation POURING over stone), ref11 (stone base,
    jettied timber storey on brackets, steep layered shingle roofs with moss, a clinging turret), ref01 (ordered,
    fully articulated bays), ref15 (warm light from lit rooms and the heart, not dots), ref23 (a spiral round a shaft).
THE BUILD, IN ORDER OF READING (the hierarchy, from the RTS camera in the south-west)
  (a) THE GREAT TREE (north-east, x 10..16 z 0..7): a twisting, leaning trunk of six bark tones with dark-oak ridges on
      its spiral seams, root buttresses flaring to the corners and into the bay, boughs, a three-tier canopy (azalea
      under, flowering azalea and cherry on top) to y 19 - the tallest mass by far; at L3 the crown climbs to y 27 (height caps L1 20, L3 28).
  (b) THE GATE (west face): the Banteay Srei gopura - piers with pilasters, blind-door niches lit by lanterns,
      colonnettes on the jambs, a carved lintel over the 5 x 8 passage, a stepped flame pediment with naga ends, the
      stag-dryad mask in the tympanum, birch antlers over the apex; root curtains (the gate leaf) furled on the jambs.
  (c) SECONDARY MASSES stepping down: the treant-wright's HALL (north, grown into the tree's west flank: stone storey,
      pilasters, lit arched windows, a jettied timber storey on brackets, a steep layered roof with moss, a dormer) and
      its clinging TURRET (north-west); the south GALLERY (Ta Prohm) with a silk-cotton tree on its roof pouring roots
      down the south face; two ruined PRASATS (south-west corner, east side).
  (d) THE GROUND: the bay paved in mossy stone, a calcite waking ring round the heart, root veins (rooted dirt, podzol)
      running out of the trunk's foot, ferns on the soil.

Usage: python3 tools/lab_vehicle_verdant.py [OUT_DIR]   (writes verdant_l1.nbt and verdant_l3.nbt, checks both)
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs            # noqa: E402
import mex_verdant as mv               # noqa: E402  - the true leaf distances
import audit_structure_pockets as ap   # noqa: E402
import verdant_lab_parts as V          # noqa: E402
from verdant_lab_parts import (VP, MSB, CSB, MC, MCS, OAK, DOAK, DOS, FLEAF, CHERRY, GLOW)  # noqa: E402
from gothic_parts import hsh            # noqa: E402

SX, SZ = 17, 17
H1, H3 = 20, 28                 # the height caps (a lab never out-towers the capitol)
SPAWN = (8, 8)
LANE = {(x, y, z) for x in range(0, 9) for z in range(6, 11) for y in range(1, 9)}
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "design", "vehicle_labs")
FINE = ("_stairs", "_slab", "_wall", "_fence", "_pane", "iron_bars", "_trapdoor", "_button", "chain", "lantern",
        "candle", "_rod", "_door", "_carpet", "_banner")
FORBIDDEN = ("water", "lava", "sand", "gravel", "_concrete_powder", "redstone", "piston", "fire", "_door", "_carpet",
             "candle", "vine", "hanging_roots", "spore_blossom", "torch", "sapling", "copper", "tuff_brick",
             "polished_tuff")


class Canvas(VP):
    """The lab's canvas: clipped to the box, never into the lane or the spawn column."""

    def __init__(self, top):
        super().__init__()
        self.top = top

    def ok(self, x, y, z):
        return 0 <= x < SX and 0 <= z < SZ and 0 <= y < self.top and (x, y, z) not in LANE and \
            not ((x, z) == SPAWN and y >= 1)

    def set(self, x, y, z, role, **p):
        if self.ok(x, y, z):
            self.b[(x, y, z)] = (role, p)

    def get(self, x, y, z):
        return self.b.get((x, y, z), ("air", {}))[0]

    def place(self, other, ox=0, oy=0, oz=0, rot=0):
        for (x, y, z), v in other.rotated(rot).b.items():
            self.set(x + ox, y + oy, z + oz, v[0], **v[1])
        return self


# ================================================================================================= the ground
def ground(c):
    """y 0: the bay paved in mossy stone, a calcite waking ring round the floor heart, root veins of rooted dirt and
    podzol running out of the tree's foot toward the spawn, moss and grass at the margins."""
    for x in range(SX):
        for z in range(SZ):
            h = hsh(x, z, 3)
            d = math.hypot(x - 8, z - 8)
            vein = x >= 9 and (abs((z - 8) + (x - 8) * 0.55) < 0.6 or abs(z - 7 - (x - 11) * 0.2) < 0.5)
            if (x, z) == SPAWN:
                n = GLOW
            elif 1.5 <= d <= 2.3 and x <= 10:
                n = "calcite" if h % 4 else MSB
            elif vein and h % 3:
                n = "rooted_dirt" if h % 2 else "podzol"
            elif 6 <= z <= 10 and x <= 8:
                n = MSB if h % 5 else (CSB if h % 2 else "moss_block")
            else:
                n = ("moss_block", MSB, "grass_block", MSB, "podzol", "moss_block", MC, "rooted_dirt")[h % 8]
            c.b[(x, 0, z)] = (n, {"axis": "y"} if n == GLOW else {"snowy": "false"} if n in ("grass_block", "podzol")
                              else {})


def plants(c):
    """Ferns and a few flowers on soil open above, never in the lane."""
    for x in range(SX):
        for z in range(SZ):
            if c.get(x, 0, z) in ("moss_block", "grass_block", "podzol", "rooted_dirt") and c.free(x, 1, z):
                nm = ("fern", "fern", "lily_of_the_valley", "fern", "azure_bluet", None, None)[hsh(x * 3 + 1, z + 5, 2) % 7]
                if nm:
                    c.set(x, 1, z, nm)


# ================================================================================================= the great tree
TRUNK_L1 = [(y, 13.6 - 0.14 * y, 3.6 + 0.07 * y, 3.0 - 0.08 * y) for y in range(1, 15)]
TRUNK_L3 = [(y, 13.6 - 0.14 * y, 3.6 + 0.07 * y, max(1.4, 3.0 - 0.08 * y)) for y in range(15, 24)]


def great_tree(c):
    """The landmark: a twisting, leaning trunk, root buttresses flaring to the corners and into the bay, boughs out to
    the canopy, three canopy tiers with gaps between them so they read as layers."""
    cells = V.trunk(c, TRUNK_L1, seed=1)
    V.spiral(c, cells, turns=1.25, y0=3, y1=13, seed=2.4)
    for (p0, p1) in (((12, 6, 6), (10, 1, 10)), ((15, 6, 5), (16, 1, 8)), ((15, 7, 1), (16, 1, 0)),
                     ((11, 6, 2), (9, 1, 0)), ((14, 5, 6), (13, 1, 8))):
        V.root_flare(c, p0, p1, thick=1)
    c.set(11, 3, 6, GLOW, axis="y")                 # the heart in the trunk's foot, framed by bark
    c.st(11, 4, 7, "north", "top", DOS)
    c.st(10, 3, 6, "east", "bottom", DOS)
    for p1 in ((6, 13, 2), (16, 13, 9), (16, 14, 0), (12, 16, 0), (15, 16, 6), (9, 16, 3)):
        V.branch(c, (11, 12, 5), p1, name=DOAK if p1[0] % 2 else OAK)
    # the tiers: wide and low at the back, stepping in and up; notched over the bay so the bough and the cradle
    # stay in the sky (the gantry reads from the camera), gaps between the tiers so they read as layers
    bay = lambda x, z: x <= 11 and z >= 5            # noqa: E731
    lim = lambda x, y, z: y <= H1 - 1 and not bay(x, z)   # noqa: E731
    V.canopy_tier(c, 11.5, 4.0, 13, 6.4, seed=1, inside=lim)
    V.canopy_tier(c, 12.0, 4.0, 16, 4.6, seed=2, inside=lim)
    V.canopy_tier(c, 12.5, 3.5, 18, 2.6, seed=3, inside=lim, top=(CHERRY, FLEAF))


def great_tree_l3(c):
    """L3: the trunk climbs on, the GREAT STAG-DRYAD is carved in it looking west, two more tiers and a cherry crown."""
    V.trunk(c, TRUNK_L3, seed=1, hollow_from=0)
    for y in range(15, 24):
        c.log(round(13.6 - 0.14 * y), y, round(3.6 + 0.07 * y), name=DOAK)
    c.place(V.stag_mask(big=True, top=7), 9, 20, 5, rot=1)
    for p1 in ((16, 23, 1), (14, 24, 9), (12, 24, 0)):
        V.branch(c, (12, 21, 5), p1, name=OAK)
    behind = lambda x, y, z: x >= 11 and y <= H3 - 1  # noqa: E731
    V.canopy_tier(c, 13.0, 4.5, 22, 4.6, seed=4, inside=behind)
    V.canopy_tier(c, 13.0, 4.5, 24, 3.2, seed=5, inside=behind, top=(CHERRY, CHERRY))
    V.canopy_tier(c, 13.0, 4.5, 26, 1.8, seed=6, inside=behind, top=(CHERRY, FLEAF), drips=False)


# ================================================================================================= the silk-cotton tree
def silk_cotton(c, l3=False):
    """Ta Prohm's tree on the gallery roof: a pale trunk on the ridge, roots pouring over the roof and down the south
    face to the terrace and down the inner wall into the bay; a small canopy."""
    for y in range(10, 16):
        c.log(12, y, 13, name="birch_log")
    for y in range(10, 13):
        c.log(13, y, 13, name="birch_log")
    c.log(11, 10, 13, name="birch_log")
    for x in (10, 11, 12, 13, 14):
        c.roots(x, 10, 14)
    for x in (10, 11, 12, 13, 14):
        c.roots(x, 9, 15)
    for x, s, end in ((11, 1, 3), (13, 2, 3), (10, 13, 5), (14, 17, 5)):
        V.strangle(c, x, 8, 16, end, "x", seed=s)
    c.roots(12, 10, 12)
    c.roots(11, 10, 12)
    V.strangle(c, 12, 9, 11, 1, "x", seed=5)
    V.canopy_tier(c, 12.5, 13.5, 15, 3.2, seed=7, inside=lambda x, y, z: y <= H1 - 1)
    V.canopy_tier(c, 12.5, 13.5, 17, 1.8, seed=8, inside=lambda x, y, z: y <= H1 - 1, top=(CHERRY, FLEAF))
    if l3:
        c.roots(5, 9, 15)
        c.roots(5, 10, 14)
        V.strangle(c, 5, 8, 16, 3, "x", seed=9)
        for x in range(6, 10):
            c.roots(x, 10, 14 if x % 2 else 13)


# ================================================================================================= gantry and hoists
def gantry(c):
    """The Cradle Bough: a living root bridge along x at z 7, y 13, x 3..12, a strangler-root pier under its east end,
    the root cradle hanging from it beside the spawn column."""
    c.place(V.cradle(), 6, 9, 7)
    c.place(V.root_bridge(10, seed=1), 3, 13, 7)
    for z in (5, 6, 7):
        c.log(12, 13, z, axis="z", name=DOAK)
    V.strangle(c, 12, 12, 9, 1, "z", seed=4)
    for z in (6, 7, 8):
        c.roots(3, 12, z)


def gantry_l3(c):
    """L3: the high bough (z 9, y 18, x 2..10) from the gopura's apex into the tree, a second cradle under it."""
    c.place(V.cradle(), 5, 14, 9)
    for x in range(2, 12):
        c.log(x, 18, 9, axis="x", name=(OAK, DOAK)[x % 2])
        if x % 3 == 0:
            c.roots(x, 18, 8)
            c.roots(x, 18, 10)
    c.roots(11, 18, 8)
    c.roots(11, 18, 7)


# ================================================================================================= assembly
def draw(l3=False):
    c = Canvas(H3 if l3 else H1)
    ground(c)
    c.place(V.timber_hall(seed=2), 5, 0, 4)                       # north: the treant-wright's hall
    c.place(V.turret(seed=4), 0, 0, 2)                            # its clinging turret, north-west
    for k in range(3):                                            # south: the Ta Prohm gallery
        c.place(V.gallery_bay(window=True, lit=(k == 1), seed=k), 4 + 4 * k, 0, 14)
    c.place(V.gallery_end(seed=5), 16, 0, 14)
    c.place(V.prasat(ruined=not l3, seed=6), 0, 0, 16)            # south-west corner tower (L3: its top restored)
    c.place(V.prasat(seed=7), 13, 0, 11)                          # the east prasat
    great_tree(c)
    c.place(V.gopura(seed=1), 0, 0, 8, rot=1)                     # the gate, west
    gantry(c)
    silk_cotton(c, l3)
    for z in (11, 12):                                            # a root mound in the nook by the gopura
        c.set(3, 1, z, MC)
    c.st(3, 2, 11, "north", "bottom", MCS)
    c.set(3, 2, 12, "moss_block")
    c.leaf(3, 3, 12, FLEAF)
    for x in (4, 6, 9, 15):                                       # leaves pouring off the lean-to (ref03)
        V.drape(c, x, 7, 16, 2 + x % 2)
    if l3:
        great_tree_l3(c)
        gantry_l3(c)
    false_windows(c)
    articulate(c)
    plants(c)
    prune(c)
    return c


STAIR_OF = {MSB: "mossy_stone_brick_stairs", "stone_bricks": "stone_brick_stairs", CSB: "stone_brick_stairs",
            "chiseled_stone_bricks": "stone_brick_stairs", MC: MCS, "spruce_planks": "spruce_stairs",
            "birch_planks": "birch_stairs", "dark_oak_planks": DOS}
SLAB_OF = {MSB: "mossy_stone_brick_slab", "stone_bricks": "stone_brick_slab", MC: "mossy_cobblestone_slab",
           "dark_oak_planks": "dark_oak_slab", "spruce_planks": "spruce_slab"}
OPEN = ("air", "fern", "lily_of_the_valley", "azure_bluet")


PLAIN = (MSB, "stone_bricks", CSB, MC)


def false_windows(c):
    """Ta Prohm's false windows: wherever a camera face (west or south) has a plain stone panel 2 wide and 5 tall
    with solid stone behind it, carve a blind window into it - a sill (an upside-down stair), three courses of turned
    balusters (walls) standing in front of the dark stone, a chiseled lintel. Panels sit on a 4-cell rhythm so the
    walls read as bays, never at random."""
    for face, (dx, dz) in (("west", (-1, 0)), ("south", (0, 1))):
        for (x, y, z), (n, _) in sorted(c.b.items()):
            u = z if face == "west" else x
            if y != 3 or u % 4 != 1:
                continue
            panel = [((x, yy, z) if face == "west" else (x + k, yy, z)) if face == "south" else (x, yy, z + k)
                     for k in (0, 1) for yy in range(3, 8)]
            ok = all(c.get(*q) in PLAIN and c.get(q[0] + dx, q[1], q[2] + dz) in OPEN and
                     c.get(q[0] - dx, q[1], q[2] - dz) not in OPEN and (q[0] - dx, q[1], q[2] - dz) in c.b
                     and q[1] < c.top for q in panel)
            if not ok:
                continue
            for q in panel:
                yy = q[1]
                if yy == 3:
                    c.b[q] = ("stone_brick_stairs", dict(facing="east" if face == "west" else "north", half="top",
                                                         shape="straight", waterlogged="false"))
                elif yy == 7:
                    c.b[q] = ("chiseled_stone_bricks", {})
                else:
                    c.b[q] = ("mossy_stone_brick_wall" if (q[0] + q[2]) % 2 else "stone_brick_wall", {})


def articulate(c):
    """The masons' last pass on the camera faces (ref01: no plain wall): a drip course every fourth course - the
    wall stone turned to an upside-down stair whose open quarter casts a shadow line - and the flat tops of piers,
    pediment backs and cornices weathered to slabs. Only plain stone or plank cells with exactly one open camera face
    (west or south) take the course; corners and the lane's frame stay whole."""
    for (x, y, z), (n, p) in list(c.b.items()):
        if n not in STAIR_OF or y < 2:
            continue
        w = c.get(x - 1, y, z) in OPEN and x > 0
        s_ = c.get(x, y, z + 1) in OPEN and z < SZ - 1
        up = c.get(x, y + 1, z) in OPEN
        if w != s_ and y % 4 == 3 and not up:
            face = "east" if w else "north"           # the full back against the wall, the open quarter outside
            c.b[(x, y, z)] = (STAIR_OF[n], dict(facing=face, half="top", shape="straight", waterlogged="false"))
        elif up and y >= 8 and n in SLAB_OF and c.get(x, y - 1, z) not in OPEN:
            c.b[(x, y, z)] = (SLAB_OF[n], dict(type="bottom", waterlogged="false"))


def prune(c):
    """Drops what a clipped part leaves touching nothing (a leaf tip cut by the lane or the box edge)."""
    while True:
        lone = [p for p in c.b if p[1] > 0 and not any((p[0] + dx, p[1] + dy, p[2] + dz) in c.b for dx, dy, dz in
                ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)))]
        if not lone:
            return
        for p in lone:
            del c.b[p]


def rig_of(c, height):
    r = gs._SunRig(SX, height, SZ)
    for (x, y, z), (n, p) in c.b.items():
        if n not in ("air", "cave_air"):
            r.set(x, y, z, n, **p)
    mv._leaf_distances(r)
    r.bury_grass()
    r.settle()
    r.check_support("verdant rootcradle")
    return r



def stats(r):
    """Total and fine share; the CAMERA-VISIBLE blocks (a west, south or top face whose ray toward the RTS camera -
    up, west and south - leaves the box unblocked) and their fine share (the amendment's >= 35% rule)."""
    n = {p: v[0] for p, v in r.b.items()}
    sy = max(p[1] for p in n) + 1

    def escapes(x, y, z):
        while 0 <= x < SX and 0 <= z < SZ and y <= sy:
            if (x, y, z) in n:
                return False
            x, y, z = x - 1, y + 1, z + 1
        return True
    vis = [p for p in n if any(escapes(p[0] + dx, p[1] + dy, p[2] + dz)
                               for dx, dy, dz in ((-1, 0, 0), (0, 1, 0), (0, 0, 1)))]
    fine_all = sum(1 for v in n.values() if any(f in v for f in FINE))
    fine_vis = sum(1 for p in vis if any(f in n[p] for f in FINE))
    return len(n), fine_all / len(n), len(vis), fine_vis / max(1, len(vis))


def check(r, path, tag, maxh):
    n = {p: v[0] for p, v in r.b.items()}
    top = max(p[1] for p in n)
    assert top < maxh, "%s taller than %d (%d)" % (tag, maxh, top + 1)
    assert all((x, 0, z) in n for x in range(SX) for z in range(SZ)), "hole in the ground layer"
    assert {"moss_block", MSB} <= {v for (x, y, z), v in n.items() if y == 0}
    busy = [p for p in LANE if p in n]
    assert not busy, "lane blocked: %s" % busy[:8]
    col = [p for p in n if (p[0], p[2]) == SPAWN and p[1] >= 1]
    assert not col, "%s: the spawn column is not open: %s" % (tag, col)
    bad = sorted({v for v in n.values() if any(f in v for f in FORBIDDEN)})
    assert not bad, "forbidden blocks %s" % bad
    lone = [(p, n[p]) for p in n if p[1] > 0 and not any(
        (p[0] + dx, p[1] + dy, p[2] + dz) in n for dx, dy, dz in
        ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)))]
    assert not lone, "%s floating %s" % (tag, lone[:10])
    for p, (nm, props) in r.b.items():
        if nm.endswith("_leaves"):
            assert props.get("persistent") == "true" and props.get("distance") in "1234567"
    one_way, grnd, roof, rooms, note = ap.audit(tag, path)
    assert not one_way and not grnd and "TRAPPED" not in note, (tag, one_way[:8], grnd[:8], note)
    total, fine, nvis, fvis = stats(r)
    assert fvis >= 0.35, "%s: only %.0f%% of the camera-visible blocks are fine" % (tag, 100 * fvis)
    return "%s 17x%dx17, %d blocks, fine %.0f%% (camera-visible %d, fine %.0f%%): lane + spawn column clear, no pockets " \
           "(%d roof-only)%s" % (tag, top + 1, total, 100 * fine, nvis, 100 * fvis, len(roof), note)


def seal_pockets(c, h, out):
    """Ground pockets left between root buttresses and the trunk are filled with roots (a real tree's root plate is
    solid there): audit a draft, fill each pocket cell (and the one over it), repeat until none."""
    tmp = os.path.join(out, ".verdant_draft.nbt")
    for _ in range(6):
        r = rig_of(c, h)
        r.size = (SX, max(y for (_, y, _) in r.b) + 1, SZ)
        r.structure().write(tmp)
        one_way, grnd, roof, rooms, note = ap.audit("draft", tmp)
        if not grnd and not one_way:
            break
        for (x, y, z) in grnd + one_way:
            if y <= 3:
                c.roots(x, y, z)
                c.roots(x, y + 1, z)
    os.remove(tmp)


def main():
    out = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else OUT)
    os.makedirs(out, exist_ok=True)
    c1, c3 = draw(False), draw(True)
    for c, h in ((c1, H1), (c3, H3)):
        seal_pockets(c, h, out)
    r1, r3 = rig_of(c1, H1), rig_of(c3, H3)
    lost = [p for p in r1.b if p not in r3.b]
    assert not lost, "L3 removes L1 cells: %s" % lost[:10]
    for r, tag, h in ((r1, "verdant_l1", H1), (r3, "verdant_l3", H3)):
        r.size = (SX, max(y for (_, y, _) in r.b) + 1, SZ)
        path = os.path.join(out, tag + ".nbt")
        r.structure().write(path)
        print(check(r, path, tag, h))


if __name__ == "__main__":
    main()
