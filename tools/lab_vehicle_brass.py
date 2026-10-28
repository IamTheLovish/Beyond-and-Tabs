#!/usr/bin/env python3
"""CONCEPT (round 2): the Brassworks VEHICLE LAB, THE ERECTING SHOP - the Victorian hall where the steam machines are
built (the Mortar Crawler, the Steam Runner - a boiler on stilt legs - and the Flak Cart). Writes
design/vehicle_labs/brass_l1.nbt and brass_l3.nbt from the part library tools/brass_lab_parts.py.

Round 1 scored 3/10: "a pile of copper-and-blackstone boxes, not a Victorian erecting shop" - no readable gate, two
landmarks fighting, no calm wall, a glazed roof that read as scaffolding, a grey slab for a clock, light everywhere,
taller than the capitol. Round 2 is rebuilt around HIERARCHY (the amendment's rule 2), from lovish's references:

THE REFERENCE, STUDIED
  - ref18 (a red-brick hall, white stone banding, a front arcade, dormer rows, ONE central tower over the great door):
    the composition. Brick is the field; stone appears only as plinth, string course, voussoir, sill, coping and quoin.
  - ref09 (one colossal arch punched through the main mass): the exit is a 7 x 9 round arch in a FLAT wall, the biggest
    and darkest thing on the west face, framed by two full-height piers and a stone voussoir ring with a keystone.
  - ref12 (Victorian trim on every edge): the gauge turret at the south-west corner - stone quoins, sills, heads, a
    corbelled cornice, a verdigris cone with a finial; the office range's bay window under a mansard-like slate pitch.
  - ref17 (a tall shaft is never plain): the boiler-house stack is broken by corbel rings and an oversailing cap.
  - ref01 / ref20: detail ORDERED in repeating bays; one pyramidal composition climbing to a single top.
  - Swindon / Crewe erecting shops: a long hall under one slate gable with a glazed strip in each pitch and a louvred
    smoke slot along the ridge; crane runways on internal piers carry a travelling bridge crane over the pits.

THE COMPOSITION (17 x 17, x 0..16 west -> east, z 0..16 north -> south; the RTS camera sees the roofs, the west face
and the south face). As the camera reads it:
  (a) THE CLOCK TOWER, the one landmark, centred over the gate on the west front: a 7 x 5 brick shaft off the gatehouse
      block, the round 5 x 5 dial (white face, black ring, gold hands standing proud) filling its west face, a verdigris
      hip at L1 (top y 19); at L3 an open belfry with the bell and the flared verdigris cap (top y 27).
  (b) THE GREAT WEST ARCH: 7 wide, 9 tall, in a flat brick wall between two full-height piers; stone voussoirs and a
      chiseled keystone; the roller-shutter drum in brass inside the arch head; rails run out under it.
  (c) secondary masses stepping down: the ERECTING HALL under one slate gable (ridge y 15, a glass strip in each
      pitch, the open smoke slot over the spawn column showing the crane), the OFFICE ARCADE along the south front
      (three lit round-arched bays, a bay window, a slate pitch, the clerestory above it), the GAUGE TURRET at the
      south-west corner under its cone, the BOILER HOUSE lean-to on the east with its ringed STACK at the north-east
      (top y 14, well under the tower) and the FURNACE PIER at the south-east corner (twin lit fire slots).
  (d) a calm ground story: a stone plinth all round, dark setts, the rails.
  ~70% of every face is plain red brick between a stone string course every 4-5 courses. Light: the arcade's one lit
  row, the furnace slots, the stack top - nothing else.

ANCHORS (structure-local block coords x, y, z; in game these become client-drawn moving parts):
  crane runways    (4..12, 10, 5) and (4..12, 10, 11)  soot girders on corbels off the hall's inner pilasters (static)
  gantry bridge    (10, 11, 5..11) + (12, 11, 5..11)   the travelling bridge crane, PARKED east of the spawn column;
                                   travels along x 4..12 but never stops over x 8 (the spawn column stays open)
  gantry trolley   (11, 12, 8)     the brass crab across the girders; travels along z 6..10
  hoist            (11, 10, 8)     the hook block on its chain (11, 11, 8); the locomotive frame slung under it at y 9
                                   (x 10..12, z 6..10) over the lane's 8-high clearance; lowers to the floor beside
                                   the spawn while a unit is built
  spawn column     (8, 1..top, 8)  air to the sky at every level: the open smoke slot in the ridge (z 8, x 6..12)
  gate leaf        (3, 9, 8)       the roller shutter rolled up in its drum (x 3..4, y 9, z 6..10) inside the arch
                                   head; unrolls to y 1 down the guide channels at (3, 1..8, 5) and (3, 1..8, 11)
  clock hands      (0, 15, 8)      the west dial's hub; hour hand (0, 15, 9..10), minute hand (0, 16..17, 8)
  stack top        (15, 15, 1) at L1, (15, 21, 1) at L3: the smoke source
  fire slots       (14, 2..4, 16) and (16, 2..4, 16): the furnace glow at the boiler house's base
  L3 boiler bridge (6..7, 11, 5..11): a second bridge west of the column, the boiler barrel slung at y 9 (x 6)
  L3 bell          (3, 20, 8)      in the belfry
  spawn cell       (8, 1, 8)       the hall floor between the rails (a brass boss)

Usage: python3 tools/lab_vehicle_brass.py   (writes design/vehicle_labs/brass_l1.nbt, brass_l3.nbt and checks them)
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs           # noqa: E402
import brass_lab_parts as BP          # noqa: E402
from brass_lab_parts import new, chain, rod, hsh   # noqa: E402

SX = SZ = 17
H1, H3 = 20, 28
SPAWN = (8, 1, 8)
LANE_Z = range(6, 11)
LANE_X = range(0, 12)
LANE_H = 8
OUT = "design/vehicle_labs"

# the hall's roof profile: |dz| from the ridge (z 8) -> the course of its stair row (eaves at the wall tops, y 9)
ROOF = {6: 10, 5: 10, 4: 11, 3: 12, 2: 13, 1: 14}
GLASS_ROWS = (2, 3)             # |dz| of the glazed strip in each pitch
WALL_TOP = 9


def brick_box(p, x0, x1, y0, y1, z0, z1, hollow=True, bands=(), band_role="stone"):
    """A brick mass: skin from y0 to y1; the interior filled to y 2 (no ground pocket) and hollow above when `hollow`;
    stone string courses on the skin at the `bands` courses."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            skin = x in (x0, x1) or z in (z0, z1)
            for y in range(y0, y1 + 1):
                if skin or not hollow or y <= 2:
                    p.set(x, y, z, band_role if y in bands else "wall")


def quoin(p, x, y, z):
    """A stone quoin block on alternate courses, brick between."""
    p.set(x, y, z, "stone" if y % 2 == 0 else "wall")


def hip(p, x0, x1, z0, z1, y, role_st="verd_st", role="verd", levels=99):
    """A hipped cap stepping in one course at a time (verdigris). Returns the first free course."""
    k = 0
    while x0 <= x1 and z0 <= z1 and k < levels:
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                ex, ez = x in (x0, x1), z in (z0, z1)
                if x0 == x1 or z0 == z1 or (ex and ez):
                    p.set(x, y, z, role)
                elif ex:
                    p.stair(x, y, z, "east" if x == x0 else "west", "bottom", role_st)
                elif ez:
                    p.stair(x, y, z, "south" if z == z0 else "north", "bottom", role_st)
                else:
                    p.set(x, y, z, role)
        x0, x1, z0, z1, y, k = x0 + 1, x1 - 1, z0 + 1, z1 - 1, y + 1, k + 1
    return y


# ============================================================================================ L1
def ground(p):
    """Layer 0: dark setts (deepslate tiles with a few cobbled ones), the road out of the hall - two iron rails on
    dark-oak sleepers between blackstone - a brass boss under the spawn."""
    for x in range(SX):
        for z in range(SZ):
            r = hsh(x, z, 3) % 9
            p.set(x, 0, z, "cobbled_deepslate" if r < 2 else "deepslate_tiles" if r < 7 else "polished_deepslate")
    for x in range(0, 13):
        for z in range(5, 12):
            if z in (7, 9):
                p.set(x, 0, z, "iron" if x % 4 == 0 else "polished_deepslate")
            elif z in (5, 11):
                p.set(x, 0, z, "base")
            elif x % 2:
                p.set(x, 0, z, "post", axis="z")
            else:
                p.set(x, 0, z, "polished_blackstone")
    p.set(8, 0, 8, "brass_b")


def plinth(p):
    """The stone plinth along the two camera faces: a stone course and a battered stair proud of every recessed wall."""
    for z in range(0, 3):
        p.stair(0, 1, z, "east", "bottom", "stone_st")
    for z in range(14, 17):
        p.stair(0, 1, z, "east", "bottom", "stone_st")


def gatehouse(p):
    """The GATEHOUSE: the hall's west gable wall (x 1..2, z 3..13) to y 10 with the GREAT ARCH through it - the opening
    z 5..11, straight jambs to y 6, a round head to y 9; a stone voussoir ring in the x 1 plane with a chiseled
    keystone; two full-height brick PIERS standing proud at x 0 (z 3..4 and z 12..13) with stone string courses, a
    stone cornice along the top at y 11 and small verdigris hips on the piers; the brass shutter DRUM inside the arch
    head, iron guide channels down the jambs; the lid at y 10..11 the tower stands on."""
    cy = 6

    def d(z, y):
        return math.hypot(z - 8, y - cy) if y > cy else abs(z - 8)

    for x in (1, 2):
        for z in range(3, 14):
            for y in range(1, 11):
                dd = d(z, y)
                if dd < 3.5:
                    continue                                   # the void
                if x == 1 and 3.5 <= dd < 4.5 and y > cy:
                    p.set(x, y, z, "stone")                    # the voussoirs
                else:
                    p.set(x, y, z, "wall")
    p.set(1, 10, 8, "stone_carve")                             # the keystone
    p.set(0, 10, 8, "stone_carve")
    # the piers, proud at x 0: brick, stone plinth / string course / cap, a verdigris hip
    for z in (3, 4, 12, 13):
        for y in range(1, 12):
            p.set(0, y, z, "stone" if y in (1, 6, 11) else "wall")
    for z in (3, 12):
        p.stair(0, 12, z, "south", "bottom", "verd_st")
        p.stair(0, 12, z + 1, "north", "bottom", "verd_st")
    # the hood stairs where the ring meets the piers, and the cornice at y 11 across the front
    for z in (5, 11):
        p.stair(0, 10, z, "east", "top", "stone_st")
    for z in range(5, 12):
        p.stair(0, 11, z, "east", "top", "stone_st")
    # the lid: brick at y 10 over x 3..5 (the tower's floor); the wings' flat roofs: slate slabs behind a stone coping
    for x in range(1, 6):
        for z in range(3, 14):
            if x >= 3:
                p.set(x, 10, z, "wall")
            if 5 <= z <= 11:
                p.set(x, 11, z, "wall")
            elif x == 1 or z in (3, 13):
                p.set(x, 11, z, "stone")
            else:
                p.slab(x, 11, z, "bottom", "slate_slab")
    # the drum in the arch head and the guide channels
    for z in range(5, 12):
        if 6 <= z <= 10:
            p.stair(3, 9, z, "west", "top", "brass_st")
            p.set(4, 9, z, "verd" if z == 8 else "brass_b")
        else:
            p.set(3, 9, z, "blue")
            p.set(4, 9, z, "blue")
    for z in (5, 11):
        for y in range(1, 9):
            p.set(3, y, z, "iron" if y % 3 == 0 else "blue_carve" if y % 3 == 1 else "blue")


def tower(p):
    """THE CLOCK TOWER's shaft (x 1..5, z 5..11, y 11..17): brick with stone quoins; the DIAL on the west face - a
    black ring 7 tall with its corners cut so it reads round, a white 5 x 5 face, black hands flush in the face and a
    brass hub standing proud; a tall stone-dressed arched window on the south face; a stone cornice proud all round at
    y 18 and a two-course verdigris hip (top y 19 - the tallest thing at L1)."""
    for x in range(1, 6):
        for z in range(5, 12):
            if x in (1, 5) or z in (5, 11):
                for y in range(11, 18):
                    p.set(x, y, z, "wall")
    for y in range(11, 18):
        for (x, z) in ((1, 5), (1, 11), (5, 5), (5, 11)):
            quoin(p, x, y, z)
    for z in range(5, 12):                                     # the dial
        for y in range(11, 18):
            dz, dy = z - 8, y - 14
            r = max(abs(dz), abs(dy))
            if r == 3 and abs(dz) == abs(dy):
                continue
            p.set(1, y, z, "ring" if r == 3 else "dial")
    for y in (15, 16):
        p.set(1, y, 8, "ring")                                 # the minute hand, up
    p.set(1, 14, 9, "ring")                                    # the hour hand
    p.set(0, 14, 8, "brass_b")                                 # the hub, proud
    p.set(2, 12, 11, "stone")                                  # the south window
    p.set(3, 12, 11, "stone")
    p.set(4, 12, 11, "stone")
    for y in range(13, 16):
        p.set(3, y, 11, "pane_dim")
        p.set(3, y, 10, "wall")
    p.stair(2, 15, 11, "west", "top", "stone_st")
    p.stair(4, 15, 11, "east", "top", "stone_st")
    p.set(3, 16, 11, "stone_carve")
    cornice(p, 0, 6, 4, 12, 18)
    for x in range(1, 6):
        for z in range(5, 12):
            p.set(x, 18, z, "wall")
    hip(p, 1, 5, 5, 11, 18, levels=1)
    for z in range(6, 11):                                     # the ridge along z
        p.stair(2, 19, z, "east", "bottom", "verd_st")
        p.set(3, 19, z, "verd")
        p.stair(4, 19, z, "west", "bottom", "verd_st")
    p.stair(3, 19, 5, "south", "bottom", "verd_st")
    p.stair(3, 19, 11, "north", "bottom", "verd_st")


def cornice(p, x0, x1, z0, z1, y):
    """A stone cornice proud of a mass whose skin is one cell inside x0..x1 / z0..z1: upside-down stairs on the four
    sides, blocks at the corners."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            ex, ez = x in (x0, x1), z in (z0, z1)
            if ex and ez:
                p.set(x, y, z, "stone")
            elif ex:
                p.stair(x, y, z, "east" if x == x0 else "west", "top", "stone_st")
            elif ez:
                p.stair(x, y, z, "south" if z == z0 else "north", "top", "stone_st")


def hall(p):
    """THE ERECTING HALL (x 3..13, z 3..13): brick side walls to y 9, the east gable wall, inner pilasters at z 4 / 12
    carrying the crane runways on corbels, the clerestory (clear panes) in the south wall above the office pitch, and
    ONE SLATE GABLE ROOF ridged along x: slate stair rows two courses thick, a glass strip in each pitch, the open smoke
    slot along the ridge over the spawn column with verdigris lips, the gable's stone coping."""
    for x in range(3, 14):
        for z in (3, 13):
            for y in range(1, WALL_TOP + 1):
                p.set(x, y, z, "wall")
    for z in range(3, 14):
        for y in range(1, WALL_TOP + 1):
            p.set(13, y, z, "wall")
    for x in (3, 4, 7, 10):                                    # the inner pilasters and the runway corbels
        for z in (4, 12):
            for y in range(1, WALL_TOP + 1):
                p.set(x, y, z, "wall")
        if x == 3:
            continue
        p.stair(x, 9, 5, "south", "top", "stone_st")
        p.stair(x, 9, 11, "north", "top", "stone_st")
    for z in (5, 11):
        BP.runway(p, 4, 12, 10, z)
    for x in (5, 6, 8, 9, 11, 12):                             # the clerestory over the office pitch
        for y in (8, 9):
            p.set(x, y, 13, "glass")
    # the roof (its eave row k = 6 is a flatter sprocketed kick proud of the walls, shading the clerestory)
    for z in range(2, 15):
        k = abs(z - 8)
        if k == 0:
            continue
        y = ROOF[k]
        face = "south" if z < 8 else "north"
        x0 = 3 if k >= 4 else 6
        if k == 6:
            x0 = 4
        for x in range(x0, 13):
            if k == 6:
                p.stair(x, y, z, face, "bottom", "slate_st")
            elif k in GLASS_ROWS:
                p.set(x, y, z, "glass")
                p.set(x, y - 1, z, "glass")
            else:
                p.stair(x, y, z, face, "bottom", "slate_st")
                if y - 1 > WALL_TOP and k < 5:
                    p.set(x, y - 1, z, "slate")
        if k == 1:                                             # the ridge lips of the smoke slot
            for x in range(6, 13):
                p.stair(x, y + 1, z, face, "bottom", "verd_st")
    # the east gable: brick following the pitch, stone coping stairs on its edge, the verdigris ridge post
    for z in range(3, 14):
        k = abs(z - 8)
        top = ROOF.get(k, 15) if k else 15
        for y in range(WALL_TOP + 1, top):
            p.set(13, y, z, "wall")
        if k:
            p.stair(13, top, z, "south" if z < 8 else "north", "bottom", "stone_st")
    p.set(13, 15, 8, "verd")


def office(p):
    """THE OFFICE ARCADE along the south front (x 4..12, z 14..16): stone plinth, brick pilasters at z 16 every third
    cell with stone caps, round-arched LIT windows between them (the one lit row), the middle bay a square BAY WINDOW
    flush with the pilasters under a stone lintel, a stone cornice, a steep slate pitch and a verdigris deck meeting the
    hall wall under the clerestory."""
    for x in range(4, 13):
        for z in (14, 15):
            for y in range(1, 6):
                p.set(x, y, z, "stone" if y in (1, 5) else "wall")
    for x in (4, 7, 10):                                       # the pilasters
        for y in range(1, 6):
            p.set(x, y, 16, "stone" if y in (1, 5) else "wall")
    for x in (5, 6, 11, 12):                                   # the arched windows, recessed at z 15
        for y in (2, 3):
            p.set(x, y, 15, "pane")
            p.set(x, y, 14, "light")
        p.stair(x, 4, 15, "west" if x in (5, 11) else "east", "top", "stone_st")
        p.set(x, 1, 16, "stone")
    for x in (5, 6, 11, 12):
        p.stair(x, 5, 16, "north", "top", "stone_st")         # the cornice between the pilasters
    for x in (8, 9):                                           # the bay window, flush at z 16
        p.set(x, 1, 16, "stone")
        for y in (2, 3):
            p.set(x, y, 16, "pane")
            p.set(x, y, 15, "light")
        p.slab(x, 4, 16, "bottom", "stone_slab")
        p.set(x, 5, 15, "stone")
    # the pitch and the deck
    for x in range(4, 13):
        p.stair(x, 6, 15, "north", "bottom", "slate_st")
        p.set(x, 6, 14, "slate")
        p.stair(x, 7, 14, "north", "bottom", "verd_st")
    p.set(13, 10, 13, "stone")                                 # the hall-end pilaster's cap (x 13, z 14..16 below)
    for z in (14, 15, 16):
        for y in range(1, 10):
            p.set(13, y, z, "stone" if y in (1, 5) else "wall")
        p.set(13, 10, z, "stone")


def turret(p):
    """THE GAUGE TURRET at the south-west corner (x 1..3, z 14..16, ref12): stone plinth, brick with stone quoins on
    alternate courses, two storeys of 1-wide windows with stone sills and heads on the camera faces, a string course,
    a corbelled stone cornice (proud at x 0), a verdigris cone with a brass finial."""
    for x in range(1, 4):
        for z in range(14, 17):
            for y in range(1, 11):
                if x in (1, 3) or z in (14, 16) or y <= 2:
                    p.set(x, y, z, "stone" if y in (1, 2, 6, 10) else "wall")
    for y in range(3, 10):
        for (x, z) in ((1, 14), (1, 16), (3, 14), (3, 16)):
            if y not in (6,):
                quoin(p, x, y, z)
    for (x, z) in ((1, 15), (2, 16)):
        for y in (3, 4, 7, 8):
            p.set(x, y, z, "pane_dim")
        for y in (5, 9):
            p.set(x, y, z, "stone_carve")
    for z in (14, 15, 16):
        p.stair(0, 10, z, "east", "top", "stone_st")
    for y in (2, 6):
        p.stair(0, y, 15, "east", "top", "stone_st")           # the sills, proud
    for x in range(1, 4):
        for z in range(14, 17):
            p.set(x, 11, z, "stone")
    hip(p, 1, 3, 14, 16, 12)
    p.set(2, 13, 15, "verd")
    p.set(2, 14, 15, "brass_b")
    rod(p, 2, 15, 15)


def north_range(p):
    """The smiths' shops along the north (x 1..13, z 0..2): low brick under a slate lean-to that rises into the hall's
    north pitch; a stone-dressed window on its west end."""
    brick_box(p, 1, 13, 1, 6, 0, 2, bands=(1, 6))
    for x in range(1, 14):
        for k, z in enumerate((0, 1, 2)):
            p.stair(x, 7 + k, z, "south", "bottom", "slate_st")
            for y in range(7, 7 + k):
                p.set(x, y, z, "slate")
    p.set(1, 3, 1, "pane_dim")
    p.set(1, 4, 1, "pane_dim")
    p.set(1, 5, 1, "stone_carve")
    p.set(1, 2, 1, "stone")


def boiler_house(p):
    """THE BOILER HOUSE on the east (x 14..16, z 3..13): brick under a slate lean-to rising to the hall's east wall, a
    brass steam main along its top; the ringed STACK at the north-east; the FURNACE PIER at the south-east corner."""
    brick_box(p, 14, 16, 1, 8, 3, 13, bands=(1, 8))
    for z in range(3, 14):
        for k, x in enumerate((16, 15, 14)):
            p.stair(x, 9 + k, z, "west", "bottom", "slate_st")
            for y in range(9, 9 + k):
                p.set(x, y, z, "slate")
    for z in range(3, 13):
        p.set(14, 12, z, "brass" if z % 3 else "brass_b")       # the steam main
    stack(p, 14, 0, 14, rings=(7, 11))
    furnace_pier(p)


def stack(p, x0, z0, h, rings=()):
    """The works chimney x0..x0+2, z0..z0+2 from y 1 to its top course h: stone plinth, brick shaft with a stone band
    every 4, corbel rings (ref17) of stone stairs and slabs proud on the two sides inside the footprint, an oversailing
    stone cap, the glowing throat and a lit campfire on top."""
    for y in range(1, h + 1):
        for x in range(x0, x0 + 3):
            for z in range(z0, z0 + 3):
                edge = x in (x0, x0 + 2) or z in (z0, z0 + 2)
                if not edge:
                    p.set(x, y, z, "blue")
                    continue
                p.set(x, y, z, "stone" if y in (1, 2) or y in rings or y == h else "wall")
    for yr in list(rings) + [h - 1]:
        for i in range(3):
            p.stair(x0 - 1, yr, z0 + i, "east", "top", "stone_st")
            p.stair(x0 + i, yr, z0 + 3, "north", "top", "stone_st")
        p.slab(x0 - 1, yr + 1, z0 + 3, "bottom", "stone_slab")
        for i in range(3):
            p.slab(x0 - 1, yr + 1, z0 + i, "bottom", "stone_slab")
            p.slab(x0 + i, yr + 1, z0 + 3, "bottom", "stone_slab")
    p.set(x0 + 1, h, z0 + 1, "light")
    p.set(x0 + 1, h + 1, z0 + 1, "campfire", **BP.CAMPFIRE)


def furnace_pier(p):
    """The FURNACE PIER (x 14..16, z 14..16): soot brick with stone bands, the twin lit fire slots on the south face -
    the one firebox glow - a stone cap."""
    for x in range(14, 17):
        for z in range(14, 17):
            for y in range(1, 13):
                p.set(x, y, z, "stone" if y in (1, 6, 12) else "blue")
    for x in (14, 16):
        for y in (2, 3, 4):
            p.set(x, y, 16, "pane_dim")
            p.set(x, y, 15, "light")
        p.set(x, 5, 16, "stone_carve")
    p.set(15, 13, 15, "blue_carve")


def crane(p):
    """The travelling bridge crane on the runways, parked east of the spawn column: twin girders at x 10 / 12, the brass
    crab, the hook block and the locomotive frame slung under it at y 9."""
    p.place(BP.bridge_crane(span=7, chassis=True, girder_gap=2), 10, 10, 5)


def clear_lane(p):
    for x in LANE_X:
        for z in LANE_Z:
            for y in range(1, LANE_H + 1):
                p.clear(x, y, z)
    for y in range(1, H3 + 1):
        p.clear(SPAWN[0], y, SPAWN[2])


def l1(p):
    ground(p)
    plinth(p)
    north_range(p)
    hall(p)
    boiler_house(p)
    office(p)
    gatehouse(p)
    tower(p)
    turret(p)
    crane(p)
    clear_lane(p)


# ============================================================================================ L3
def tower_l3(p):
    """THE ROYAL WORKS' tower: over the L1 hip (y 18..19, now the belfry floor), an open BELFRY (stone corner piers,
    iron-barred openings, the bell within, y 20..22), a stone cornice proud all round at y 23 and the FLARED verdigris
    cap - three courses stepping in from a 7 x 9 base wider than the shaft, a brass course, a brass finial (top y 27)."""
    for x in range(1, 6):
        for z in range(5, 12):
            corner = x in (1, 5) and z in (5, 11)
            side = x in (1, 5) or z in (5, 11)
            for y in range(20, 23):
                if corner:
                    p.set(x, y, z, "stone")
                elif side:
                    p.set(x, y, z, "bars" if y < 22 else "wall")
            if not side:
                p.set(x, 19, z, "stone")
    p.set(3, 20, 8, "bell", attachment="floor", facing="north", powered="false")
    cornice(p, 0, 6, 4, 12, 23)
    for x in range(1, 6):
        for z in range(5, 12):
            p.set(x, 23, z, "stone")
    y = hip(p, 0, 6, 4, 12, 24, levels=3)
    p.set(3, y, 8, "brass_b")


def crane_l3(p):
    """A second, double-girder bridge at x 6 / 7 carrying a brass boiler barrel at y 9 on two chains."""
    for z in range(5, 12):
        p.set(7, 11, z, "blue_carve" if z in (5, 11) else "brass_b" if z % 2 else "brass")
        p.set(6, 11, z, "blue_carve" if z in (5, 11) else "brass")
    for z in (7, 8, 9):
        p.set(6, 12, z, "brass_e" if z != 8 else "verd")
        p.set(7, 12, z, "brass_e")
    for z in (6, 10):
        chain(p, 6, 10, z)
    for z in range(5, 12):
        p.set(6, 9, z, "blue" if z == 5 else "brass_b" if z in (8, 11) else "brass_e")


def dormers_l3(p):
    """Two dormers in the south pitch over the glass strip (ref18's dormer rows): a pane under a small slate gablet
    with a verdigris ridge."""
    for x in (8, 11):
        p.set(x, 13, 10, "pane_dim")
        p.set(x, 13, 9, "wall")
        p.stair(x - 1, 13, 10, "west", "bottom", "slate_st")
        p.stair(x + 1, 13, 10, "east", "bottom", "slate_st")
        p.slab(x, 14, 10, "bottom", "verd_slab")


def l3(p):
    l1(p)
    tower_l3(p)
    stack(p, 14, 0, 20, rings=(7, 11, 14))
    crane_l3(p)
    dormers_l3(p)
    for z in (3, 12):                                          # the piers' hips grow brass finials
        p.set(0, 13, z, "brass_b")
        rod(p, 0, 14, z)
    clear_lane(p)


# ============================================================================================ output and checks
def to_rig(p, h, tag):
    rig = gs._SunRig(SX, h, SZ)
    for (x, y, z), (n, props) in p.b.items():
        if n in ("air", "cave_air") or not (0 <= x < SX and 0 <= z < SZ and 0 <= y < h):
            continue
        rig.set(x, y, z, n, **props)
    rig.bury_grass()
    rig.settle()
    rig.check_support("erecting shop " + tag)
    return rig


FINE = ("_stairs", "_slab", "_wall", "_fence", "_pane", "iron_bars", "_trapdoor", "_button", "chain", "lantern",
        "candle", "rod", "_door", "_carpet", "_banner", "flower_pot", "campfire", "fern", "grass", "bell")
FORBIDDEN = ("water", "lava", "sand", "gravel", "_concrete_powder", "redstone", "magma", "torch", "anvil",
             "scaffolding", "tnt", "piston", "tuff_bricks", "polished_tuff", "lightning_rod")


def stats(rig):
    n = {p: b[0] for p, b in rig.b.items()}

    def opaque(q):
        nm = n.get(q)
        return nm is not None and not any(f in nm for f in FINE) and "glass" not in nm
    vis = [q for q in n if not all(opaque(c) for c in ((q[0], q[1] + 1, q[2]), (q[0], q[1], q[2] + 1),
                                                        (q[0] - 1, q[1], q[2])))]
    fine = [q for q in n if any(f in n[q] for f in FINE)]
    vfine = [q for q in vis if any(f in n[q] for f in FINE)]
    return len(n), len(fine) / len(n), len(vis), len(vfine) / max(1, len(vis))


def floating(rig):
    n = rig.b
    return [(q, n[q][0]) for q in n if q[1] > 0 and not any(
        (q[0] + a, q[1] + b, q[2] + c) in n for a, b, c in
        ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)))]


def check(rig, path, tag, prev=None):
    import audit_structure_pockets as ap
    n = {q: b[0] for q, b in rig.b.items()}
    assert all((x, 0, z) in n for x in range(SX) for z in range(SZ)), "%s: a hole in the foundation" % tag
    busy = [(x, y, z, n[(x, y, z)]) for x in LANE_X for z in LANE_Z for y in range(1, LANE_H + 1) if (x, y, z) in n]
    assert not busy, "%s: the exit lane is narrowed: %s" % (tag, busy[:8])
    bad = sorted({v for v in n.values() if any(f in v for f in FORBIDDEN)
                  or ("copper" in v and not v.startswith("waxed_"))})
    assert not bad, "%s: forbidden %s" % (tag, bad)
    lone = floating(rig)
    assert not lone, "%s: floating %s" % (tag, lone[:6])
    for (x, y, z), v in n.items():
        if v == "campfire":
            assert y >= 12, "%s: a campfire within reach at %s" % (tag, (x, y, z))
    col = [y for y in range(1, rig.size[1]) if (SPAWN[0], y, SPAWN[2]) in n]
    assert not col, "%s: the spawn column (8, 8) is blocked at %s" % (tag, col)
    if prev is not None:
        lost = [q for q in prev if q not in n]
        assert not lost, "%s drops %d cells of L1, e.g. %s" % (tag, len(lost), lost[:6])
    one_way, ground_, roof, rooms, note = ap.audit(tag, path)
    assert not one_way and not ground_ and "TRAPPED" not in note, (tag, one_way[:8], ground_[:8], note)
    pad = ap.Pad(ap.load_nbt(path))
    for (x, y, z) in [SPAWN] + [(x, y, z) for x in range(SX) for z in range(SZ) for y in (1, 2)
                                if pad.walkable(x, y, z)]:
        seen, stack_, out = {(x, y, z)}, [(x, y, z)], False
        while stack_:
            c = stack_.pop()
            if c[0] < 0 or c[2] < 0 or c[0] >= SX or c[2] >= SZ:
                out = (c[0] < 0 and c[2] in LANE_Z) or (x, y, z) != SPAWN
                if out:
                    break
            for nb in ap.neighbours(pad, *c):
                if nb not in seen and -3 <= nb[0] <= SX + 2 and -3 <= nb[2] <= SZ + 2 and 1 <= nb[1] <= rig.size[1]:
                    seen.add(nb)
                    stack_.append(nb)
        assert out, "%s: a unit at %s does not walk out%s" % (tag, (x, y, z), " west" if (x, y, z) == SPAWN else "")
    tot, fine, vis, vfine = stats(rig)
    return "%s %dx%dx%d: %d blocks, fine %.0f%% (visible %d, fine %.0f%%), lane clear, no pockets (%d roof-only), " \
           "spawn walks out west" % (tag, SX, rig.size[1], SZ, tot, 100 * fine, vis, 100 * vfine, len(roof))


def build(level):
    p = new()
    (l1 if level == 1 else l3)(p)
    h = max(y for (x, y, z), v in p.b.items() if v[0] not in ("air", "cave_air") and 0 <= x < SX and 0 <= z < SZ) + 1
    assert h <= (H1 if level == 1 else H3), "L%d is %d tall" % (level, h)
    return to_rig(p, h, "L%d" % level)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    r1, r3 = build(1), build(3)
    p1, p3 = os.path.join(OUT, "brass_l1.nbt"), os.path.join(OUT, "brass_l3.nbt")
    r1.structure().write(p1)
    r3.structure().write(p3)
    print(check(r1, p1, "brass_l1"))
    print(check(r3, p3, "brass_l3", prev=set(r1.b)))
