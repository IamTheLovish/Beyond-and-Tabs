#!/usr/bin/env python3
"""THE VERDANT PART LIBRARY - the Rootcradle's parts (tools/lab_vehicle_verdant.py assembles them).

Conventions are tools/gothic_parts.py's: a part is a gothic_parts.Part (cells (x, y, z) -> (block id, props)) in its
own frame, FRONT FACING SOUTH (+z), y = 0 its floor; Part.place(part, ox, oy, oz, rot) turns it a quarter turn
clockwise per `rot` (rot 1 turns a south front to the west - the lab's gate). Roles are plain block ids here (the
Verdant kit is fixed: the PALETTE below names it), so a part reads as what it is.

THE VERDANT KIT (claude/mex-faction-refs, capitol_heartwood_hall.py): MOSSY STONE (mossy / cracked / chiseled stone
bricks, mossy cobble, their stairs, slabs and walls) + LIVING WOOD (oak, dark oak and spruce bark, stripped birch for
the mask and antlers), accent LEAVES (azalea, flowering azalea, cherry - persistent, true distance), light FROGLIGHT and
SHROOMLIGHT, signature the STAG-DRYAD mask. No machinery: the base is a forest.

The parts (Khmer ruin + living tree, after Ta Prohm, Banteay Srei and the root bridges of Meghalaya):
  stag_mask()        the stag-dryad: birch mask, froglight eyes, dark-oak brows, moss crown, birch antlers
  gopura()           the Banteay Srei gate pavilion: pilasters, octagonal colonnettes, blind-door niches, a carved
                     lintel, a stepped flame pediment with naga ends and the mask in its tympanum
  gallery_bay()      one 4-wide Ta Prohm gallery bay: laterite terrace, pillared half-gallery under a lean-to, the
                     outer wall's balustered window, the corridor behind, the corbelled roof with its ridge cresting
  prasat()           a Khmer corner tower: plinth, cella with blind doors, receding tiers with antefixes, ruined top
  timber_hall()      the treant-wright's hall (ref 06 / 11): a mossy stone storey with pilasters and arched windows,
                     a jettied timber storey on brackets, a steep layered shingle roof with moss, a dormer
  turret()           the clinging turret: stone shaft, timber stage, steep cap
  cradle()           the HOIST: a woven root basket holding a sleeping treant, hung on two vine chains
  root_bridge()      the GANTRY: a living bough with a woven root deck and handrail roots (Meghalaya)
  great_tree()       the landmark: a twisting, leaning trunk of many bark tones with bark ridges, flaring root
                     buttresses, boughs and a layered canopy (drawn on the canvas: it grows round what is there)
  root_flare(), strangle(), drape(), canopy_tier()   the organic helpers the tree and the ruins share
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gothic_parts import Part, hsh, toward  # noqa: E402

MSB, CSB, CHB, SB, MC = "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks", "stone_bricks", \
    "mossy_cobblestone"
MSS, SBS, MCS = "mossy_stone_brick_stairs", "stone_brick_stairs", "mossy_cobblestone_stairs"
MSW, SBW, MCW, AW = "mossy_stone_brick_wall", "stone_brick_wall", "mossy_cobblestone_wall", "andesite_wall"
MSL, SBL = "mossy_stone_brick_slab", "stone_brick_slab"
OAK, DOAK, SPR, SOAK, SDOAK, SSPR = "oak_log", "dark_oak_log", "spruce_log", "stripped_oak_log", \
    "stripped_dark_oak_log", "stripped_spruce_log"
BIRCH, BIRCH_W, BIRCH_S, BIRCH_F = "stripped_birch_log", "stripped_birch_wood", "birch_stairs", "birch_fence"
DOS, SPS, OST = "dark_oak_stairs", "spruce_stairs", "oak_stairs"
DOF, SPF = "dark_oak_fence", "spruce_fence"
LEAF, FLEAF, CHERRY = "azalea_leaves", "flowering_azalea_leaves", "cherry_leaves"
GLOW, SHROOM = "verdant_froglight", "shroomlight"
ROOTS = "mangrove_roots"
WIN = "yellow_stained_glass_pane"
LEAFP = dict(persistent="true", distance="7", waterlogged="false")   # the real distance is set at the end
DRY = dict(waterlogged="false")


class VP(Part):
    """A Verdant part: Part plus the kit's verbs."""

    def __init__(self):
        super().__init__({"_": "_"})

    def st(self, x, y, z, facing, half="bottom", name=MSS):
        self.set(x, y, z, name, facing=facing, half=half, shape="straight", waterlogged="false")

    def sl(self, x, y, z, typ="bottom", name=MSL):
        self.set(x, y, z, name, type=typ, waterlogged="false")

    def log(self, x, y, z, axis="y", name=OAK):
        self.set(x, y, z, name, axis=axis)

    def glow(self, x, y, z, name=GLOW):
        self.set(x, y, z, name, **({} if name == SHROOM else {"axis": "y"}))

    def roots(self, x, y, z):
        self.set(x, y, z, ROOTS, **DRY)

    def leaf(self, x, y, z, name=LEAF):
        if (x, y, z) not in self.b:
            self.set(x, y, z, name, **LEAFP)

    def free(self, x, y, z):
        return (x, y, z) not in self.b

    def stone(self, x, y, z, seed=0):
        """Weathered masonry: calm zones of mossy brick, a few cracked stones, moss heavier low down."""
        h = hsh(x * 5 + y, z * 3 + y * 7, seed)
        zone = hsh((x + z + 40) // 3, y // 3, seed + 5) % 10
        if h % 11 == 0:
            return CSB
        if y <= 3 or zone < 6:
            return MSB
        return SB


# ======================================================================================== the stag-dryad mask
def stag_mask(antlers=True, big=False, top=99):
    """The stag-dryad (tools/mex_verdant.py, the Grove's face), front at z = 0 facing south, x -1..1 (big: x -2..2),
    y 0 the jaw. Antlers branch out of the burrs as open birch fence (a pale branching line against the sky)."""
    p = VP()
    if big:
        # the great mask (L3, carved into the trunk): five wide, six tall, the snout one cell proud
        for y in range(0, 6):
            for x in range(-2, 3):
                p.set(x, y, -1, "oak_wood", axis="y")
        p.st(-1, 0, 0, "east", "top", BIRCH_S)
        p.st(1, 0, 0, "west", "top", BIRCH_S)
        p.set(0, 0, 0, BIRCH_W, axis="y")
        p.st(0, 0, 1, "north", "bottom", BIRCH_S)           # the nose over the jaw
        p.set(0, 1, 0, BIRCH_W, axis="y")
        p.set(0, 1, 1, BIRCH_W, axis="y")                   # the snout
        for s in (-1, 1):
            p.set(s, 1, 0, BIRCH_W, axis="y")
            p.set(2 * s, 1, 0, "oak_wood", axis="y")
            p.glow(s, 2, 0)                                 # the eyes
            p.st(2 * s, 2, 0, "west" if s > 0 else "east", "top", BIRCH_S)
            p.st(s, 3, 0, "north", "top", DOS)              # the brows, jutting
            p.st(2 * s, 3, 0, "north", "top", DOS)
            p.set(s, 4, 0, BIRCH_W, axis="y")
            p.log(2 * s, 4, 0, name=BIRCH)                  # the burrs
        p.set(0, 2, 0, BIRCH_W, axis="y")
        p.set(0, 3, 0, BIRCH_W, axis="y")                   # the blaze
        p.set(0, 4, 0, "moss_block")
        p.set(0, 5, 0, "moss_block")
        p.glow(0, 6, 0)                                     # the glowing bud on the moss crown
        if antlers:
            for s in (-1, 1):
                for (x, y) in ((2, 5), (2, 6), (3, 6), (3, 7), (4, 7), (4, 8), (3, 8), (3, 9), (5, 8), (5, 9),
                               (2, 7), (2, 8), (6, 9), (6, 10), (4, 9), (4, 10)):
                    if y <= top:
                        p.set(s * x, y, 0, BIRCH_F)
                p.log(s * 4, 7, 0, name=BIRCH)               # the knuckles
        return p
    p.st(-1, 0, 0, "east", "top", BIRCH_S)          # the jaw line, hollowed under
    p.st(1, 0, 0, "west", "top", BIRCH_S)
    p.set(0, 0, 0, BIRCH_W, axis="y")               # the muzzle
    p.glow(-1, 1, 0)                                # the eyes
    p.glow(1, 1, 0)
    p.set(0, 1, 0, BIRCH_W, axis="y")               # the blaze
    p.st(-1, 2, 0, "north", "top", DOS)             # the brows
    p.st(1, 2, 0, "north", "top", DOS)
    p.set(0, 2, 0, BIRCH_W, axis="y")               # the forehead
    p.log(-1, 3, 0, name=BIRCH)                     # the burrs
    p.log(1, 3, 0, name=BIRCH)
    p.set(0, 3, 0, "moss_block")                    # the moss crown
    p.glow(0, 4, 0)                                 # the glowing bud
    if antlers:
        for s in (-1, 1):
            for (x, y) in ((1, 4), (1, 5), (2, 5), (3, 5), (3, 6), (4, 6), (4, 7), (2, 6), (2, 7), (1, 6),
                           (1, 7), (5, 7), (5, 8), (3, 7), (3, 8)):
                p.set(s * x, y, 0, BIRCH_F)
            p.log(s * 3, 5, 0, name=BIRCH)          # the knuckle where the beam forks
    return p


# ======================================================================================== the gate pavilion
def gopura(seed=0):
    """The Banteay Srei gopura, front at z = 0 facing south, x -5..5, passage x -2..2 (5 wide) by y 1..8 (8 tall)
    through z 0..-2. Three depth planes on the front: pilasters and colonnettes (z 0), the pier face and the blind-door
    niches (z -1), the tympanum (z -1) behind the pediment's frame (z 0).
      - piers x -5..-3 / 3..5: a pilaster on the outer corner (chiseled base and capital), a blind-door niche with a
        lantern on its sill, an octagonal colonnette (a wall) on the jamb with a chiseled capital;
      - the lintel y 9 (the passage's top is y 8): chiseled stones over the colonnettes and at the keystone;
      - the stepped flame pediment y 10..16 with naga ends curling up at its foot (x +-5), its rake in mossy stairs, the
        tympanum recessed, the stag-dryad mask in it (z 0) and its antlers above the apex;
      - behind it the passage vault, corbelled in steps; root curtains (the gate leaf) drawn back on the inner jambs."""
    p = VP()
    for x in range(-5, 6):
        for z in range(-2, 1):
            p.set(x, 0, z, SB if abs(x) <= 2 else MSB)
    for s in (-1, 1):
        for x in (3, 4, 5):
            for z in (-2, -1, 0):
                for y in range(1, 10):
                    p.set(s * x, y, z, p.stone(s * x, y, z, seed))
        # the outer pilaster (z 0, x +-5): base, shaft, capital
        p.set(s * 5, 1, 0, CHB)
        for y in range(2, 8):
            p.set(s * 5, y, 0, SB)
        p.set(s * 5, 8, 0, CHB)
        p.st(s * 5, 9, 0, "north", "top", SBS)
        # the blind-door niche (x +-4): a sill at y 2, a lantern on it, the recess y 3..6 at z 0, its lintel y 7
        p.st(s * 4, 1, 0, "north", "bottom", SBS)
        p.set(s * 4, 2, 0, CHB)
        p.set(s * 4, 3, 0, "lantern", hanging="false", waterlogged="false")
        for y in range(4, 7):
            p.b.pop((s * 4, y, 0), None)
        p.set(s * 4, 4, -1, CHB)                         # the carved panel of the blind door
        p.st(s * 4, 7, 0, "north", "top", SBS)
        p.set(s * 4, 8, 0, CHB)
        # the colonnette on the jamb (x +-3): an octagonal shaft (wall) between chiseled base and capital
        p.set(s * 3, 1, 0, CHB)
        for y in range(2, 8):
            p.set(s * 3, y, 0, SBW)
        p.set(s * 3, 8, 0, CHB)
        # the gate leaf, drawn open: a root curtain on the inner jamb (x +-3, z -1..-2)
        for y in range(1, 9):
            p.roots(s * 3, y, -1)
            if y % 3 != 0:
                p.roots(s * 3, y, -2)
    # the lintel and the passage vault
    for x in range(-3, 4):
        p.set(x, 9, 0, CHB if x in (-3, 0, 3) else MSB)
        p.set(x, 9, -1, MSB)
        p.set(x, 9, -2, MSB)
    for x in range(-5, 6):
        p.set(x, 10, -1, MSB)
        p.set(x, 10, -2, MSB)
    # the stepped flame pediment
    widths = {10: 5, 11: 4, 12: 4, 13: 3, 14: 2, 15: 1, 16: 0}
    for y, w in widths.items():
        for x in range(-w, w + 1):
            edge = abs(x) == w
            if edge and w > 0:
                p.st(x, y, 0, "east" if x < 0 else "west", "bottom", MSS)   # the rake climbs inward
                p.set(x, y, -1, MSB)
            elif w == 0:
                p.set(x, y, 0, CHB)
            else:
                p.set(x, y, -1, MSB if (x + y) % 3 else CHB)              # the tympanum, recessed
        for x in range(-w, w + 1):
            if y > 10:
                p.set(x, y, -2, MSB)
    p.st(-5, 11, 0, "south", "bottom", MSS)       # the naga ends curl up at the foot of the rake
    p.st(5, 11, 0, "south", "bottom", MSS)
    p.set(-5, 11, -1, MSB)
    p.set(5, 11, -1, MSB)
    p.set(0, 17, 0, MSW)                          # the finial
    # the pediment's back slope (seen from the yard): stairs stepping down to the vault
    for y, w in widths.items():
        if w > 0:
            for x in range(-w + 1, w):
                p.st(x, y + 1, -2, "south", "bottom", MCS) if p.free(x, y + 1, -2) else None
    # the mask in the tympanum, antlers above the apex
    p.place(stag_mask(antlers=True), 0, 10, 0)
    return p


# ======================================================================================== the Ta Prohm gallery
def gallery_bay(window=True, lit=False, seed=0):
    """One gallery bay, x 0..3 (the pillar at x 0, the next bay's pillar closes it), front facing south:
    z 2 the terrace face, z 1 the pillared half-gallery under its lean-to, z 0 the outer wall with a balustered window,
    z -1 the corridor (floor y 2, so it is no ground pocket), z -2 the inner wall. The floor is the laterite terrace
    y 1..2 (mossy cobble)."""
    p = VP()
    for x in range(0, 4):
        # terrace: laterite plinth with a moulded edge
        p.set(x, 1, 2, MC if hsh(x, 2, seed) % 3 else MSB)
        p.st(x, 2, 2, "north", "bottom", MCS)
        for z in (1, 0, -1, -2):
            p.set(x, 1, z, MC)
            p.set(x, 2, z, MSB if z != 1 else SB)
        # the outer wall
        for y in range(3, 9):
            p.set(x, y, 0, p.stone(x, y, 0, seed))
        # the inner wall
        for y in range(3, 9):
            p.set(x, y, -2, p.stone(x, y, -2, seed + 1))
        # the corbelled roof: y 9 slopes, the ridge y 10 with its cresting
        p.st(x, 9, 0, "north", "bottom", MSS)
        p.st(x, 9, -2, "south", "bottom", MSS)
        p.set(x, 9, -1, MSB)
        p.st(x, 10, -1, "north" if x % 2 else "south", "bottom", SBS)
        # the lean-to over the half-gallery
        p.st(x, 8, 1, "north", "bottom", MCS)
    # the pillar (x 0): base, square shaft, capital; the balustrade between the pillars
    p.st(0, 3, 1, "south", "bottom", SBS)
    for y in range(4, 7):
        p.set(0, y, 1, SB)
    p.set(0, 7, 1, CHB)
    for x in (1, 2, 3):
        p.set(x, 3, 1, MSW)
    p.set(2, 4, 1, "lantern", hanging="false", waterlogged="false") if lit else None
    p.sl(1, 7, 1, "top", SBL)
    p.sl(2, 7, 1, "top", SBL)
    p.sl(3, 7, 1, "top", SBL)
    # the outer wall's pilaster behind the pillar
    p.set(0, 3, 0, CHB)
    p.set(0, 7, 0, CHB)
    if window:
        # the balustered window: a sill, three turned balusters, a carved lintel; the corridor lit behind it
        for x in (1, 2, 3):
            p.st(x, 3, 0, "south", "top", SBS)
            for y in (4, 5, 6):
                p.set(x, y, 0, AW if x != 2 else SBW)
            p.set(x, 7, 0, CHB if x == 2 else SB)
        if lit:
            p.glow(2, 8, -1, SHROOM)
    return p


def gallery_end(seed=0):
    """The pillar that closes the last bay."""
    p = VP()
    p.set(0, 1, 2, MSB)
    p.st(0, 2, 2, "north", "bottom", MCS)
    for z in (1, 0, -1, -2):
        p.set(0, 1, z, MC)
        p.set(0, 2, z, MSB)
    p.st(0, 3, 1, "south", "bottom", SBS)
    for y in range(4, 7):
        p.set(0, y, 1, SB)
    p.set(0, 7, 1, CHB)
    p.st(0, 8, 1, "north", "bottom", MCS)
    for y in range(3, 9):
        for z in (0, -1, -2):
            p.set(0, y, z, CHB if y in (3, 7) and z == 0 else p.stone(0, y, z, seed))
    p.st(0, 9, 0, "north", "bottom", MSS)
    p.st(0, 9, -2, "south", "bottom", MSS)
    p.set(0, 9, -1, MSB)
    p.set(0, 10, -1, MSW)
    return p


# ======================================================================================== the corner tower
def prasat(ruined=True, seed=0):
    """A Khmer corner tower, 4 x 4 (x 0..3, z -3..0), front facing south (z 0) and its west face at x 0: a laterite
    plinth with a moulded edge, the cella with a blind door on each camera face, a cornice, two receding tiers with
    corner antefixes, the top fallen (a stub and a broken course)."""
    p = VP()
    X, Z = range(0, 4), range(-3, 1)
    for x in X:
        for z in Z:
            p.set(x, 1, z, MC)
            p.set(x, 2, z, MSB)
            for y in range(3, 9):
                p.set(x, y, z, p.stone(x, y, z, seed))
    for x in X:
        p.st(x, 2, 0, "north", "bottom", MCS)
    for z in Z:
        p.st(0, 2, z, "east", "bottom", MCS)
    # corner pilasters, the blind doors (a stepped frame round a carved panel)
    for (x, z) in ((0, 0), (0, -3), (3, 0)):
        p.set(x, 3, z, CHB)
        p.set(x, 8, z, CHB)
    for y in range(3, 7):
        p.set(1, y, 0, MSW if y < 6 else CHB)
        p.set(2, y, 0, MSW if y < 6 else CHB)
        p.set(0, y, -1, MSW if y < 6 else CHB)
        p.set(0, y, -2, MSW if y < 6 else CHB)
    p.st(1, 7, 0, "east", "bottom", SBS)
    p.st(2, 7, 0, "west", "bottom", SBS)
    p.st(0, 7, -1, "south", "bottom", SBS)
    p.st(0, 7, -2, "north", "bottom", SBS)
    # the cornice and the first tier
    for x in X:
        for z in Z:
            edge = x in (0, 3) or z in (-3, 0)
            if edge:
                f = "north" if z == 0 else "south" if z == -3 else "east" if x == 0 else "west"
                p.st(x, 9, z, f, "bottom", SBS)
            else:
                p.set(x, 9, z, MSB)
    for x in (1, 2):
        for z in (-2, -1):
            for y in (10, 11):
                p.set(x, y, z, p.stone(x, y, z, seed + 3))
    for (x, z) in ((0, 0), (3, 0), (0, -3), (3, -3)):
        p.set(x, 10, z, MSW)                       # antefixes on the cornice corners
    p.st(1, 12, -1, "north", "bottom", MSS)
    p.st(2, 12, -1, "west", "bottom", MSS)
    p.set(1, 12, -2, MSB)
    if not ruined:
        p.set(2, 12, -2, MSB)
        p.set(1, 13, -2, MSW)
    else:
        p.st(2, 12, -2, "west", "top", MSS)       # the fallen top: a broken course, a stub
        p.set(1, 13, -2, MSW)
    return p


# ======================================================================================== the treant-wright's hall
def timber_hall(seed=0):
    """The hall the great tree has grown round (ref 06 / ref 11), front facing south: stone storey x 0..7, z -4..0
    (walls y 1..5, floor y 2 so the room is no ground pocket), pilasters at x 0, 3, 6 standing one proud (z 1) with
    two arched lit windows between them; a string course of brackets; the timber storey jettied out to z 1 (y 6..10,
    posts over the pilasters, birch-plank infill, paned windows with shutters); a steep shingle roof (ridge along x)
    in three bark tones with moss rows, eaves dripping azalea, a dormer with a lit window; the west gable (x -1, jettied)
    framed in dark timber with a lit window and bargeboards."""
    p = VP()
    X = range(0, 8)
    for x in X:
        for z in range(-4, 1):
            p.set(x, 1, z, MSB)
            p.set(x, 2, z, "spruce_planks")
            for y in range(3, 6):
                if x in (0, 7) or z in (-4, 0):
                    p.set(x, y, z, p.stone(x, y, z, seed))
            p.set(x, 6, z, "spruce_planks")
    # pilasters standing proud (z 1) and the arched windows between them
    for x in (0, 3, 6):
        p.set(x, 1, 1, CHB)
        for y in range(2, 5):
            p.set(x, y, 1, SB)
        p.set(x, 5, 1, CHB)
    for x0 in (1, 4):
        for x in (x0, x0 + 1):
            p.st(x, 2, 1, "north", "bottom", SBS)          # the sill, one proud
            p.set(x, 3, 0, WIN)
            p.set(x, 4, 0, WIN)
            p.glow(x, 5, -1, SHROOM) if x == x0 else None  # the lit room's ceiling glow
        p.st(x0, 5, 0, "east", "top", SBS)                 # the arch's shoulders
        p.st(x0 + 1, 5, 0, "west", "top", SBS)
    # the bracket course under the jetty (z 1, y 5) between the pilasters
    for x in X:
        if x not in (0, 3, 6):
            p.st(x, 5, 1, "north", "top", DOS)
    # the jettied timber storey (z 1 plane, y 6..10; the room behind at z 0..-4)
    for x in range(-1, 8):
        for z in range(-4, 2):
            p.set(x, 6, z, "spruce_planks")
    for x in range(-1, 8):
        for y in range(7, 11):
            post = x in (-1, 0, 3, 6) or x == 7
            if post:
                p.log(x, y, 1, name=SDOAK)
            elif y in (8, 9) and x in (1, 2, 4, 5):
                p.set(x, y, 1, WIN)
            else:
                p.set(x, y, 1, "birch_planks")
        p.log(x, 11, 1, axis="x", name=DOAK)                  # the wall plate
    for x0 in (1, 4):
        for x in (x0, x0 + 1):
            p.st(x, 7, 2, "north", "top", SPS)                 # window sills
        p.set(x0 - 0, 10, 2, "spruce_trapdoor", facing="south", half="top", open="false", powered="false",
              waterlogged="false")
        p.set(x0 + 1, 10, 2, "spruce_trapdoor", facing="south", half="top", open="false", powered="false",
              waterlogged="false")
    p.glow(2, 10, 0, SHROOM)
    p.glow(5, 10, 0, SHROOM)
    for z in range(-4, 1):                                    # the west wall of the timber storey (x -1, jettied)
        for y in range(7, 11):
            p.log(-1, y, z, name=SDOAK) if z in (-4, -2, 0) else p.set(-1, y, z, "birch_planks")
        p.log(-1, 11, z, axis="z", name=DOAK)
    for z in range(-4, 1):
        for y in range(7, 11):
            p.set(7, y, z, "birch_planks")
    for x in range(-1, 8):
        for y in range(7, 11):
            p.set(x, y, -4, "birch_planks")
    # the steep roof: rows z 2 .. -4, ridge at z -1 / -2 (y 16), cresting above
    tops = {2: 11, 1: 12, 0: 13, -1: 15, -2: 15, -3: 13, -4: 12}
    tone = {2: DOS, 1: SPS, 0: MCS, -1: DOS, -2: DOS, -3: SPS, -4: DOS}
    for x in range(-2, 9):
        for z, t in tops.items():
            f = "north" if z >= -1 else "south"
            nm = tone[z]
            if z in (0, 1) and hsh(x, z, seed + 4) % 4 == 0:
                nm = MCS                                       # moss creeping across the shingles
            below = tops.get(z + 1 if z >= -1 else z - 1, 10)
            if -1 <= x <= 7:
                for y in range(max(11, min(below, t)), t):
                    if p.free(x, y, z):
                        p.set(x, y, z, "dark_oak_planks" if (x + y) % 3 else "moss_block")
            p.st(x, t, z, f, "bottom", nm)
        p.st(x, 16, -1, "north", "bottom", DOS)
        p.st(x, 16, -2, "south", "bottom", DOS)
        if x % 3 == 0:
            p.set(x, 17, -1, DOF)                              # the ridge cresting: carved dark-oak finials
    # the gable end (x -1) under the bargeboards: timber frame, birch infill, a lit window
    for z, t in tops.items():
        for y in range(11, t):
            if z in (-1, -2) and y in (12, 13):
                p.set(-1, y, z, WIN)
            elif z in (-1, -2) or y == 11:
                p.log(-1, y, z, name=SDOAK)
            else:
                p.set(-1, y, z, "birch_planks")
    p.glow(0, 12, -1, SHROOM)
    for z, t in tops.items():
        p.st(-2, t - 1, z, "east", "top", DOS)                 # the bargeboard's soffit
    # eave drips
    for x in range(-2, 9):
        if hsh(x, 3, seed) % 3 == 0:
            p.leaf(x, 10, 2, LEAF)
    # the dormer on the south slope (x 2..4): its own gable, a lit window
    for x in (2, 3, 4):
        for z in (0, 1):
            for y in (12, 13):
                p.set(x, y, z, "birch_planks")
    p.set(3, 12, 1, WIN)
    p.set(3, 13, 1, WIN)
    p.glow(3, 12, 0, SHROOM)
    p.log(2, 12, 1, name=SDOAK)
    p.log(4, 12, 1, name=SDOAK)
    p.log(2, 13, 1, name=SDOAK)
    p.log(4, 13, 1, name=SDOAK)
    for z in (-1, 0, 1, 2):
        p.st(2, 14, z, "east", "bottom", DOS)
        p.st(4, 14, z, "west", "bottom", DOS)
        p.set(3, 14, z, "dark_oak_planks") if z < 2 else p.st(3, 14, z, "north", "top", DOS)
        p.st(3, 15, z, "south", "bottom", SPS) if z < 2 else None
    return p


def turret(seed=0):
    """The clinging turret, 3 x 3 (x 0..2, z -2..0): a stone shaft with quoins and a slit, a timber stage with a lit
    window, a steep cap with a birch finial."""
    p = VP()
    for x in range(3):
        for z in range(-2, 1):
            for y in range(1, 7):
                corner = x in (0, 2) and z in (-2, 0)
                p.set(x, y, z, CHB if corner and y % 2 else p.stone(x, y, z, seed))
            p.set(x, 7, z, "spruce_planks")
            for y in range(8, 12):
                corner = x in (0, 2) and z in (-2, 0)
                p.log(x, y, z, name=SDOAK) if corner else p.set(x, y, z, "birch_planks")
    p.set(0, 4, -1, "black_stained_glass_pane")       # the slit
    p.set(0, 5, -1, "black_stained_glass_pane")
    p.set(1, 4, 0, "black_stained_glass_pane")
    p.set(0, 9, -1, WIN)
    p.set(0, 10, -1, WIN)
    p.set(1, 9, 0, WIN)
    p.glow(1, 9, -1, SHROOM)
    for x in range(3):                                 # the bracketed sill of the timber stage
        p.st(x, 7, 0, "north", "top", DOS) if x != 1 else p.set(x, 7, 0, "spruce_planks")
    # the cap: a stepped stair cone, moss on it, a birch finial with a leaf tuft
    for x in range(3):
        for z in range(-2, 1):
            if (x, z) != (1, -1):
                f = toward(x, z, 1, -1)
                p.st(x, 12, z, f, "bottom", DOS if (x + z) % 2 else SPS)
    p.set(1, 12, -1, "dark_oak_planks")
    p.set(1, 13, -1, "dark_oak_planks")
    p.st(1, 13, 0, "north", "bottom", MCS)
    p.st(0, 13, -1, "east", "bottom", MCS)
    p.st(2, 13, -1, "west", "bottom", DOS)
    p.st(1, 13, -2, "south", "bottom", DOS)
    p.set(1, 14, -1, SPF)
    p.set(1, 15, -1, BIRCH_F)
    p.leaf(1, 16, -1, FLEAF)
    return p


# ======================================================================================== the moving parts
def cradle():
    """The HOIST, centred on (0, 0, 0) = the basket's floor centre: a woven root basket 3 x 3 (mangrove roots at the
    corners, dark-oak lattice between), a sleeping treant in it (bark body, leafy shoulders, a froglight eye looking
    west), hung on two vine chains (chains wound with leaf tufts) rising to the bough above (y +4)."""
    p = VP()
    for x in (-1, 0, 1):
        for z in (-1, 0, 1):
            p.roots(x, 0, z)
            if (x, z) != (0, 0) and abs(x) + abs(z) == 2:
                p.roots(x, 1, z)
            elif (x, z) != (0, 0):
                p.set(x, 1, z, DOF)
    p.log(0, 1, 0, name=DOAK)            # the treant's legs-roots and body
    p.log(0, 2, 0, name=OAK)
    p.log(0, 3, 0, name=SPR)             # head
    p.glow(-1, 3, 0)                     # the eye, looking west (the gate)
    p.leaf(0, 3, -1, FLEAF)              # shoulders of leaf
    p.leaf(0, 3, 1, LEAF)
    p.leaf(0, 4, 0, LEAF)
    for z in (-1, 1):
        p.set(-1, 2, z, "chain", axis="y")
        p.set(-1, 3, z, "chain", axis="y")
        p.set(-1, 4, z, "chain", axis="y")
        p.set(1, 2, z, "chain", axis="y")
        p.set(1, 3, z, "chain", axis="y")
        p.set(1, 4, z, "chain", axis="y")
    return p


def root_bridge(length, seed=0, deck=True):
    """The GANTRY, a living root bridge along +x, x 0..length-1, z -2..2 (centre z 0): the bough itself (two logs,
    z 0 / -1, bark tones alternating) at y 0, a woven deck of roots on z -2..2 at y 0, handrail roots at y 1 on
    z -2 and z 2 dipping in a catenary, side roots dropping from the handrails into the deck, moss and ferns of leaf
    in the weave."""
    p = VP()
    for x in range(length):
        p.log(x, 0, 0, axis="x", name=(OAK, DOAK, OAK, SPR)[(x + seed) % 4])
        if deck:
            for z in (-2, -1, 1, 2):
                if (x + z) % 2 == 0 or z in (-1, 1):
                    p.roots(x, 0, z)
            for z in (-2, 2):
                p.set(x, 1, z, MCW if x % 3 else "oak_fence")
                if x in (0, length - 1):
                    p.set(x, 2, z, MCW)
            if x % 2 == 0:
                p.leaf(x, 1, 0, LEAF if x % 4 else FLEAF)
    return p


# ======================================================================================== organic helpers (on a canvas)
def drape(c, x, y, z, n, name=LEAF):
    """A curtain of leaves hanging from (x, y, z) down n cells, thinning (ref 03: overgrowth that pours)."""
    for k in range(n):
        if c.free(x, y - k, z):
            c.leaf(x, y - k, z, name if k < n - 1 else FLEAF)


def root_flare(c, p0, p1, thick=1, top_stairs=True, name=OAK, skip=lambda q: False):
    """A buttress root from p0 (on the trunk, high) to p1 (in the ground, low): a line of horizontal logs that drops
    as it goes out, stairs on its back climbing toward the trunk, mangrove roots where it grips the ground."""
    (x0, y0, z0), (x1, y1, z1) = p0, p1
    n = max(abs(x1 - x0), abs(z1 - z0), abs(y1 - y0), 1)
    axis = "x" if abs(x1 - x0) >= abs(z1 - z0) else "z"
    back = ("west" if x1 > x0 else "east") if axis == "x" else ("north" if z1 > z0 else "south")
    prev_top = None
    for k in range(n + 1):
        t = k / n
        x = round(x0 + (x1 - x0) * t)
        z = round(z0 + (z1 - z0) * t)
        y = round(y0 + (y1 - y0) * (t ** 0.7))
        for dy in range(0, thick):
            q = (x, y - dy, z)
            if q[1] >= 0 and not skip(q):
                c.log(*q, axis=axis, name=name if k % 3 else DOAK) if q[1] > 0 else c.roots(*q)
        # fill down to the ground under the low half so it reads as a buttress, not a pipe
        if t > 0.45:
            for yy in range(1, y - thick + 1):
                if c.free(x, yy, z) and not skip((x, yy, z)):
                    c.roots(x, yy, z) if (yy + k) % 3 else c.set(x, yy, z, DOF)
        if top_stairs and y + 1 > 0 and c.free(x, y + 1, z) and not skip((x, y + 1, z)) and 0.15 < t < 0.95:
            c.st(x, y + 1, z, back, "bottom", DOS)
        prev_top = y
    return prev_top


def canopy_tier(c, cx, cz, y0, r, seed=0, inside=lambda x, y, z: True, top=(FLEAF, CHERRY), under=LEAF,
                drips=True, thick=2, edge=None, tips=None):
    """One layer of a layered canopy: an irregular disc (the edge wanders), its underside `under` (spruce for a dark
    shadow side), its top `top[0]` with `top[1]` patches, the outer ring `edge` (oak, a paler rim) when given, `tips`
    (cherry) only as sparse single blocks on the very rim, drips hanging off the rim."""
    rr = int(r) + 2
    for x in range(int(cx) - rr, int(cx) + rr + 1):
        for z in range(int(cz) - rr, int(cz) + rr + 1):
            d = math.hypot(x - cx, z - cz)
            wob = 0.9 * math.sin(math.atan2(z - cz, x - cx) * 3 + seed) + (hsh(x, z, seed) % 3 - 1) * 0.35
            if d > r + wob:
                continue
            rim = d > r + wob - 1.4
            for dy in range(thick):
                y = y0 + dy
                if dy == thick - 1 and d > r + wob - 1.2:
                    continue                         # the top layer steps in: a rounded edge
                if not inside(x, y, z):
                    continue
                if dy == 0:
                    nm = under if not (rim and edge) else edge
                else:
                    patch = hsh(x // 2, z // 2, seed + 11) % 5
                    nm = top[1] if patch == 0 else top[0] if patch < 3 else LEAF
                if tips and rim and hsh(x, z, seed + 9) % 7 == 0:
                    nm = tips
                c.leaf(x, y, z, nm)
            if drips and d > r + wob - 1.0 and hsh(x, z, seed + 3) % 4 == 0 and inside(x, y0 - 1, z):
                c.leaf(x, y0 - 1, z, under)
                if hsh(x, z, seed + 5) % 3 == 0 and inside(x, y0 - 2, z):
                    c.leaf(x, y0 - 2, z, LEAF)


TRUNK_TONES = (OAK, DOAK, SPR, OAK, DOAK, "jungle_log")
GREAT_TONES = (SDOAK, DOAK, "mangrove_log", DOAK, "mud_bricks", "stripped_mangrove_log", DOAK, SDOAK)


def trunk(c, path, seed=0, hollow_from=3, tones=TRUNK_TONES, twist=0.22, ridge=DOS, keep=lambda q: True):
    """A twisting trunk: path = [(y, cx, cz, r)]. Bark tones by angle, turning with height (the spiral of ref 06), a
    shell (hollow inside above `hollow_from`), bark ridges (`ridge` stairs) one cell out along the spiral seams.
    `keep(q)` False leaves a cell of the trunk empty (a hollow, a niche)."""
    cells = {}
    for (y, cx, cz, r) in path:
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            for z in range(int(cz - r - 1), int(cz + r + 2)):
                if math.hypot(x - cx, z - cz) <= r and keep((x, y, z)):
                    cells[(x, y, z)] = (cx, cz, r)
    n = len(tones)
    for (x, y, z), (cx, cz, r) in cells.items():
        shell = y < hollow_from or any((x + dx, y + dy, z + dz) not in cells for dx, dy, dz in
                                       ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, 1, 0), (0, -1, 0)))
        if not shell:
            continue
        a = math.atan2(z - cz, x - cx)
        s = (a / (2 * math.pi) * n + y * twist + seed) % n
        nm = tones[int(s)]
        if nm.endswith("_log"):
            c.log(x, y, z, name=nm)
        else:
            c.set(x, y, z, nm)
    # bark ridges: on the spiral seams, a stair against the trunk
    for (x, y, z), (cx, cz, r) in list(cells.items()):
        a = math.atan2(z - cz, x - cx)
        s = (a / (2 * math.pi) * n + y * twist + seed) % n
        if s % 2 < 0.55 and (x, y, z) in c.b:
            dx, dz = round(math.cos(a)), round(math.sin(a))
            q = (x + dx, y, z + dz)
            if q not in cells and c.free(*q):
                c.st(*q, toward(q[0], q[2], x, z), "bottom" if y % 4 else "top", ridge)
    return cells


def branch(c, p0, p1, name=OAK):
    """A bough: a line of logs along its main axis from p0 to p1."""
    (x0, y0, z0), (x1, y1, z1) = p0, p1
    n = max(abs(x1 - x0), abs(z1 - z0), abs(y1 - y0), 1)
    axis = "x" if abs(x1 - x0) >= max(abs(z1 - z0), abs(y1 - y0)) else "z" if abs(z1 - z0) >= abs(y1 - y0) else "y"
    out = []
    prev = None
    for k in range(n + 1):
        t = k / n
        q = (round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t), round(z0 + (z1 - z0) * t))
        steps = [q]
        if prev is not None:                     # keep the bough face-connected: step one axis at a time
            a = list(prev)
            steps = []
            for i in (0, 2, 1):
                if a[i] != q[i]:
                    a[i] = q[i]
                    steps.append(tuple(a))
        for s in steps:
            out.append(s)
            if c.free(*s) or c.get(*s).endswith("_leaves"):
                c.log(*s, axis=axis, name=name)
        prev = q
    return out


def strangle(c, x, y, z, down_to, side, seed=0):
    """A strangler root pouring from (x, y, z) down a face to `down_to`: mangrove roots with bark knuckles, wandering
    one cell sideways as it falls, a flare at its foot. `side` is the axis it wanders along ('x' or 'z')."""
    wx, wz = x, z
    for yy in range(y, down_to - 1, -1):
        if (yy + seed) % 4 == 0:
            if side == "x":
                wx += 1 if hsh(wx, yy, seed) % 2 else -1
            else:
                wz += 1 if hsh(wz, yy, seed) % 2 else -1
        q = (wx, yy, wz)
        if (yy + seed) % 4 == 0 or yy == down_to:
            c.log(*q, name=DOAK)                     # a bark knuckle where the root shifts or grips
        elif (yy + seed) % 4 == 2:
            c.roots(*q)
        else:
            c.set(*q, DOF)                           # the braided strand between (a thin root, light behind it)
    return wx, wz


def spiral(c, cells, turns=1.5, y0=2, y1=14, seed=0):
    """A root ribbon wrapping the trunk (ref 23's spiral, ref 06's twist): a rising helix of dark-oak stairs just
    outside the trunk shell, a mangrove-root knot every few steps, so the trunk shows motion from any angle."""
    ys = {}
    for (x, y, z), v in cells.items():
        ys.setdefault(y, []).append((x, z, v))
    for y in range(y0, y1 + 1):
        if y not in ys:
            continue
        cx, cz, r = ys[y][0][2]
        a = 2 * math.pi * turns * (y - y0) / max(1, y1 - y0) + seed
        for da in (0.0, 0.35):
            x = round(cx + (r + 1) * math.cos(a + da))
            z = round(cz + (r + 1) * math.sin(a + da))
            if (x, y, z) in cells or not c.free(x, y, z):
                continue
            if (y + int(da * 10)) % 4 == 0:
                c.roots(x, y, z)
            else:
                c.st(x, y, z, toward(x, z, round(cx), round(cz)), "bottom" if y % 2 else "top", DOS)


def catalogue():
    return {"stag_mask": stag_mask(), "stag_mask_big": stag_mask(big=True), "gopura": gopura(),
            "gallery_bay": gallery_bay(lit=True), "prasat": prasat(), "timber_hall": timber_hall(),
            "turret": turret(), "cradle": cradle(), "root_bridge": root_bridge(6)}
