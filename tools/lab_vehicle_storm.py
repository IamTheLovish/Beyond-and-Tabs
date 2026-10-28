#!/usr/bin/env python3
"""The Stormcallers VEHICLE LAB, THE LANTERN ARSENAL (rebuild, Oct 28 2026): where the Rocket Battery (a hwacha-style
fire-arrow rocket cart and its crew) and the later Stormcallers vehicles are built. 17 x 17; L1 20 high, L3 28 high
(the height cap: a lab never out-towers the capitol). Assembled from the Stormcallers part library
tools/storm_lab_parts.py (the kit of tools/lab_lotus_academy.py and tools/capitol_jade_pagoda.py: jade roofs in two
tones, red lacquer, dark oak, calcite plaster, stone plinths with the fret, gold corners, lit paper screens).

ANCHORS (structure-local block coords x, y, z; in game these become client-drawn moving parts):
  gantry rails     (5..9, 10, 6) and (5..9, 10, 10)  dark-oak rails along x from the gantry's west frame (x 5) into
                   the Storm Tower's second eave (x 10); the frame: lacquer piers (5, *, 5) / (5, *, 11) and
                   (9, *, 5) / (9, *, 11) with a red tie (5, 9, 6..10) and bracket arms under the rails' ends
  gantry trolley   (7, 11, 6..10)  the red lacquer trolley across the rails; slides along x 5..9 (never over x 8 z 8)
  hoist            (7, 10, 7) / (7, 10, 9)  two FLOATING LODESTONES slung under the trolley; the vehicle hangs under
                   them on drawn lightning arcs (no chains); their spark: the end rod (7, 10, 8). Bottom y 10, over the
                   lane's 8-high clearance
  gate leaves      (2, 2..8, 5) and (2, 2..8, 11)  the great gate's two leaves drawn OPEN, folded flat beside the posts
                   (dark-oak panels, gold studs); they swing across the lane on the x 2 line
  prayer wheel     (11, 3..5, 8)  the gold-banded jade drum in the Storm Tower's west niche at the end of the lane's
                   axis; spins on y while the production queue runs
  storm vane       (13, 19, 8)  the lightning rod on the tower's bell cap (arcs when a vehicle is done); at L3 the
                   ARMILLARY (centre (13, 23, 8), radius 3, its lowest point on the rod): its three rings turn round the floating lodestone there,
                   arcs drawn between the rod and the stone
  spawn cell       (8, 1, 8)  the box centre: the column x 8, z 8 is air from the floor to the top at both levels (the
                   gantry roof rings round it: the oculus)

THE REFERENCES, STUDIED (text and pictures; inspiration only, nothing traced)
  - KIYOMIZU-DERA (Kyoto): the main hall's great stage (butai) stands out over the slope on 139 keyaki (zelkova)
    pillars about 13 m tall, joined into a square lattice by horizontal through-beams with no nails (kakezukuri); a
    deck of 410+ cedar boards; the hall's deep roof sits back behind the stage edge. It gives a GROUND STORY that is a
    lattice of tall posts and horizontal ties in front of shadowed rock, a railed deck toward the view, and a roof
    stepping back above. -> the south range stands on a Kiyomizu stage: lacquer pillars every other cell on the camera
    faces, a through-tie at y 2, shadowed rock one cell behind, a spruce deck and rail at y 4.
  - TODAI-JI NANDAIMON (Nara): five bays by two, 25.5 m on a stone plinth, eighteen pillars 19 m tall, a DOUBLE
    hip-and-gable roof; the daibutsuyo style pushes bracket arms tier on tier straight out of the pillars, ties run
    through the posts. It gives a gate that is the deepest void on the face, framed by massive posts and stacked arms
    under two roofs. -> the west gate: four lacquer posts on chiseled feet round a 5 x 8 void, a red tie and a gold
    tablet over it, two tiers of bracket arms, the lower roof, a lit upper storey, the upper roof with gold ridge ends.
  - HIMEJI CASTLE: white plastered storeys stacked on a battered stone base, each roof a ring smaller, the silhouette
    climbing to one keep. -> THE LANDMARK, the Storm Tower on the lane's axis: a rock-faced plinth, three stacked eaves
    narrowing 9 / 7 / 5 with calcite plaster friezes, a gallery on the first band (ref17), bracket sets under the
    second (dougong), a jade BELL CAP (ref07) and the storm rod; at L3 an open ARMILLARY round a lodestone (ref27).
  - FUSHIMI INARI: torii rows - red posts and black beams as a rhythm. -> lacquer posts every other cell on every
    face, the gantry frames read as torii across the yard.
  - THE JOSEON HWACHA and arsenal: a two-wheeled cart with a rack of 100-200 singijeon fire-arrows angled up, kept in
    sheds round a yard. -> the courtyard IS the vehicle yard (ref08's compound: keep, ranges, corner pavilions, a
    crag lifting it), fire-arrow racks (lightning rods) in the north verandah.

THE COMPOSITION (the amendment's hierarchy): (a) ONE landmark, the Storm Tower, 20 high (28 with the armillary) -
everything else stops at 15; (b) the GATE, the biggest dark opening on the west face, framed hard by posts, tie,
brackets and two roofs; (c) secondary masses stepping down - the two double-eaved ranges (13 high), the gantry's
ringed roof, two bell-domed corner pavilions (11); (d) a calm ground story - the stage lattice, rock-faced fret
plinths, crags with a blossom tree and a dark spruce on the west corners. Light comes from lit rooms behind paper
screens (shroomlight ceilings), the gate's two lanterns and the hoist's spark.

THE PLAN (x 0..16 west -> east, z 0..16 north -> south; the RTS camera sees the roofs, the west and the south faces)
  - LANE / YARD x 0..9, z 6..10, clear 8 high; the spawn (8, 8) under the oculus.
  - GATE x 0..4, z 4..12; crag gardens x 0..2 at z 0..3 and z 13..16.
  - NORTH RANGE x 3..11, z 0..5 on a fret plinth; SOUTH RANGE x 3..11, z 11..16 on the Kiyomizu stage.
  - STORM TOWER x 10..16, z 4..12; CORNER PAVILIONS x 12..16 at z 0..4 and z 12..16.
  - GANTRY x 4..11, z 5..11: two frames, rails, trolley, hoist; two covered beam roofs, the oculus between.
L3 THE STORM CROWN (adds only): the armillary over the bell cap, a lacquer cross-truss with gold bosses and rods on
the gantry's beam roofs, pavilion finials, house banners on the gate, a second fire-arrow rack on the stage.

Usage: python3 tools/lab_vehicle_storm.py [OUT_DIR]   (default design/vehicle_labs: storm_l1.nbt, storm_l3.nbt)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import storm_lab_parts as P              # noqa: E402
from storm_lab_parts import (JADE, JADE2_ST, OAK, OAK_LOG, OAK_ST, OAK_FENCE, RED, RED_FENCE,  # noqa: E402
                             LACQ, PAPER, GOLD, SB, SB_ST, SB_SLAB, SB_WALL, CSB, OPP)

SX = SZ = 17
L1_H, L3_H = 20, 28
LANE_X, LANE_Z, LANE_TOP = 9, range(6, 11), 8
SPAWN = (8, 8)
TOWER = (13, 8)


def guard(x, y, z):
    if 0 <= x <= LANE_X and z in LANE_Z and 1 <= y <= LANE_TOP:
        return True
    return (x, z) == SPAWN and y >= 1


# ================================================================================================ ground
def ground(c):
    """Layer 0: stone-brick footings, a polished-andesite yard with stone-brick kerbs, a jade-and-diorite path on the
    lane's axis to a gold-and-cherry rosette under the spawn, grass and moss under the crag corners."""
    for x in range(SX):
        for z in range(SZ):
            name = SB
            if x <= 2 and (z <= 3 or z >= 13):
                name = "grass_block" if P.hsh(x, z) % 3 else "moss_block"
            c.put(x, 0, z, name)
    for x in range(0, LANE_X + 1):
        for z in LANE_Z:
            c.put(x, 0, z, "polished_andesite" if z not in (6, 10) else SB)
        c.put(x, 0, 8, JADE if x % 3 else "polished_diorite")
    sx, sz = SPAWN
    c.put(sx, 0, sz, GOLD)
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        c.put(sx + dx, 0, sz + dz, "cherry_planks")
    for dx, dz in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        c.put(sx + dx, 0, sz + dz, JADE)


def garden(c):
    """The two west corners: crags stepping down to the outside (rock in zones, grass, moss and petals on the steps), a
    blossom tree leaning toward the camera on the south-west, a dark spruce breaking the outline north-west."""
    sw = [(0, 13, 1), (1, 13, 2), (2, 13, 3), (0, 14, 1), (1, 14, 2), (2, 14, 3), (0, 15, 1), (1, 15, 1),
          (2, 15, 2), (0, 16, 1), (1, 16, 1), (2, 16, 2)]
    nw = [(0, 0, 1), (1, 0, 2), (2, 0, 3), (0, 1, 1), (1, 1, 2), (2, 1, 3), (0, 2, 1), (1, 2, 1), (2, 2, 2),
          (0, 3, 1), (1, 3, 1), (2, 3, 2)]
    P.crag(c, sw, seed=3)
    P.crag(c, nw, seed=5)
    P.cherry_tree(c, 2, 4, 14, lean="west", h=3, crown=2)
    P.spruce_tree(c, 1, 3, 1, h=9)


# ================================================================================================ the gate
GATE_POSTS = [(1, 5), (3, 5), (1, 11), (3, 11)]


def gate(c, chimes):
    """THE GREAT GATE (Nandaimon): four lacquer posts on chiseled feet (y 2..8); side walls of dark-oak frame and
    panel with a paper light (z 4 / z 12); the leaves open beside the posts; a red tie along z at y 9 on both post
    lines, a lacquer frieze with a gold tablet on the west face; two tiers of bracket arms out of every post; the LOWER
    ROOF (lip x 0..4, z 3..13 at y 10); a lit upper storey (x 1..3, z 5..11, y 11..12); the UPPER ROOF (lip x 0..4,
    z 4..12 at y 13) to a ridge with gold ends and rods."""
    for (x, z) in GATE_POSTS:
        c.put(x, 1, z, CSB)
        for y in range(2, 9):
            c.log(x, y, z, RED)
    for z in (4, 12):
        for x in range(0, 5):
            c.put(x, 1, z, CSB if x in (0, 4) else SB)
            for y in range(2, 10):
                if x in (0, 4):
                    c.log(x, y, z, RED)
                elif y in (2, 9):
                    c.log(x, y, z, OAK_LOG, axis="x")
                elif x == 2 and 4 <= y <= 6:
                    c.put(x, y, z, PAPER)
                else:
                    c.put(x, y, z, OAK)
    for z in (5, 11):
        c.put(2, 1, z, SB)
        for y in range(2, 9):
            c.put(2, y, z, GOLD if y in (3, 7) else OAK)           # a leaf, folded open: panel and gold studs
        c.put(0, 1, z, CSB)
        c.st(0, 2, z, "east", "bottom", SB_ST)
        c.put(4, 1, z, CSB)
    # the tie: red beams along z on both post lines, a dark-oak plate between, the frieze on the west face
    for x in (1, 3):
        for z in range(5, 12):
            c.log(x, 9, z, RED if (x, z) not in GATE_POSTS else OAK_LOG, axis="z" if (x, z) not in GATE_POSTS else "y")
    for z in range(5, 12):
        c.log(2, 9, z, OAK_LOG, axis="z")
        c.put(0, 9, z, GOLD if z == 8 else LACQ)
    # bracket arms (daibutsuyo): out of every post toward the gate's faces, two tiers
    for (x, z) in GATE_POSTS:
        ox = 0 if x == 1 else 4
        c.st(ox, 8, z, "east" if x == 1 else "west", "top", OAK_ST)
    # the lower roof: lip + band, the inside filled
    chimes += P.hip_roof(c, 0, 4, 3, 13, 10, rings=2)
    for z in range(5, 12):
        c.put(2, 10, z, OAK)
    # the upper storey, lit; a gold tablet on its west face
    P.storey(c, 1, 3, 5, 11, 11, 11, posts_every=2, plaster_top=False, dado=False)
    c.put(1, 11, 8, GOLD)
    chimes += P.hip_roof(c, 0, 4, 4, 12, 12)
    # the gate's own light: a paper lantern hung under the lower roof at each side of the void
    for z in (3, 13):
        c.lantern(0, 9, z, hanging=True)


# ================================================================================================ ranges
def north_range(c, chimes):
    """The magazine hall north of the yard: a fret plinth (x 3..11, z 0..5); the hall (x 4..10, z 0..3, y 3..6) of
    lacquer posts, dark-oak dado, lit paper and a calcite frieze; a verandah on lacquer posts (z 4) with fire-arrow
    racks; eave 1 (lip z ..5 at y 7); an upper storey (x 5..9, z 0..3, y 8..9); eave 2 (lip x 4..10, z ..4 at y 10)."""
    P.plinth(c, 3, 11, 0, 5, top=2, rock_faces=("west",))
    P.storey(c, 4, 10, 0, 3, 3, 6)
    for x in (4, 6, 8, 10):
        for y in range(3, 7):
            c.log(x, y, 4, RED)
    for x in (5, 7, 9):
        c.log(x, 6, 4, OAK_LOG, axis="x")
        c.st(x, 3, 4, "north", "top", OAK_ST)                     # the rack
        c.rod(x, 4, 4, "south")                                    # fire-arrows aimed at the yard
        c.rod(x, 5, 4, "up")
    for x in range(4, 11):
        if x % 2 == 0 and c.free(x, 6, 5):
            c.st(x, 6, 5, "north", "top", OAK_ST)                  # bracket arms out of the verandah posts
    chimes += P.hip_roof(c, 3, 11, -1, 5, 7, rings=3, cap=True)


def stage(c):
    """THE KIYOMIZU STAGE under the south range (x 3..11, z 11..16, deck y 4): lacquer pillars every other cell on the
    west (x 3), south (z 16) and yard (z 11) faces with a dark-oak tie at y 2 between them, shadowed rock one cell
    behind the lattice, a spruce deck edged with stripped dark oak."""
    rock = ("tuff", "andesite", "cobblestone", "stone", "tuff", "mossy_cobblestone")
    for x in range(3, 12):
        for z in range(11, 17):
            face = x == 3 or z in (11, 16)
            for y in range(1, 4):
                if face:
                    if (x + z) % 2 == 1 or (x == 3 and z in (11, 16)):
                        c.log(x, y, z, RED)
                    elif y == 2:
                        c.log(x, y, z, OAK_LOG, axis="x" if z in (11, 16) else "z")
                else:
                    c.put(x, y, z, rock[P.hsh(x, z, y) % len(rock)])
            if face:
                c.log(x, 4, z, "stripped_dark_oak_log", axis="z" if x == 3 else "x")
            else:
                c.put(x, 4, z, "spruce_planks")


def south_range(c, chimes):
    """The hall on the stage: a verandah deck all round with a dark-oak / mangrove rail on the camera faces, the hall
    (x 4..10, z 12..15, y 5..8), eave 1 (lip x 3..11, z 11..17 at y 9), the upper storey (x 5..9, z 12..15, y 10..11),
    eave 2 (lip x 4..10, z 11..16 at y 12)."""
    stage(c)
    for x in range(3, 12):
        c.put(x, 5, 16, OAK_FENCE if x % 2 else RED_FENCE)
    for z in range(11, 16):
        c.put(3, 5, z, OAK_FENCE if z % 2 else RED_FENCE)
    P.storey(c, 4, 10, 12, 15, 5, 8)
    P.bracket_ring(c, 4, 10, 12, 15, 8, {(x, z) for x in (4, 6, 8, 10) for z in (12, 15)} | {(4, 13), (4, 14)})
    chimes += P.hip_roof(c, 3, 11, 11, 17, 9, rings=3, cap=True)


# ================================================================================================ tower + pavilions
def tower(c, chimes):
    """THE STORM TOWER, the landmark (centre (13, 8), on the lane's axis): a rock-faced fret plinth (x 10..16,
    z 4..12); storey 1 (7 x 7, y 3..6) with the prayer-wheel niche facing the yard; EAVE 1 (lip 9 x 9 at y 7) with a
    gallery rail on its band; storey 2 (5 x 5, y 8..10) under bracket sets; EAVE 2 (7 x 7 at y 11); storey 3
    (3 x 3, y 12..13); the jade BELL CAP (lip 5 x 5 at y 14) and the storm rod (y 19). The stack sits one cell east of the plinth's west edge so the
    first eave clears the lane."""
    tx, tz = TOWER
    P.plinth(c, 10, 16, 4, 12, top=2, rock_faces=("south", "west"))
    P.storey(c, 11, 16, 5, 11, 3, 6, posts_every=2)
    # the niche: the west wall at z 7..9 steps back to x 12; the wheel stands in it, on the plinth's step at x 10
    for z in (7, 8, 9):
        for y in range(3, 6):
            c.put(11, y, z, "air")
            c.put(12, y, z, PAPER if z != 8 else OAK)
    c.put(11, 3, 8, JADE)
    c.put(11, 4, 8, "waxed_cut_copper")
    c.put(11, 5, 8, GOLD)
    for z in (7, 9):
        c.st(11, 5, z, "east", "top", OAK_ST)
    for z in (5, 11):
        c.lantern(10, 3, z)                                        # the tower's door lamps on the plinth
    chimes += P.hip_roof(c, 10, 17, 4, 12, 7, rings=2)
    for x in range(12, 16):
        for z in range(6, 11):
            if c.get(x, 7, z)[0] == "air":
                c.slab(x, 7, z, typ="top")
    P.gallery(c, 11, 16, 5, 11, 7, floor=False, corbels=False, gaps=((11, 8),))
    P.storey(c, 12, 16, 6, 10, 8, 10, posts_every=2)
    P.bracket_ring(c, 12, 16, 6, 10, 10, {(12, 8), (16, 8), (14, 6), (14, 10)})
    chimes += P.hip_roof(c, 11, 17, 5, 11, 11, rings=2)
    for x in range(13, 16):
        for z in range(7, 10):
            if c.get(x, 11, z)[0] == "air":
                c.slab(x, 11, z, typ="top")
    P.storey(c, 12, 14, 7, 9, 12, 13, posts_every=2, plaster_top=False, dado=False)
    chimes += P.bell_dome(c, tx, tz, 14, finial_h=1)


def pavilion(c, chimes, z1):
    """A corner pavilion (ref08's corner pavilions, ref13's domed caps): a rock-faced fret plinth (x 12..16,
    z z1..z1+4), a 3 x 3 lit lacquer body (y 3..5) inside a rail on the plinth's edge, a jade bell dome (lip y 6)."""
    z2 = z1 + 4
    P.plinth(c, 12, 16, z1, z2, top=2, rock_faces=("south", "east") if z1 else ("north", "east"))
    P.storey(c, 13, 15, z1 + 1, z2 - 1, 3, 5, posts_every=2, plaster_top=False)
    chimes += P.bell_dome(c, 14, z1 + 2, 6, finial_h=1)


# ================================================================================================ gantry
def gantry(c, chimes):
    """THE GANTRY: two torii-like frames - lacquer piers at x 5 and x 9 on z 5 / z 11 (on the north plinth and the
    stage deck) to y 11, a red tie (x 5, y 9) across the lane; dark-oak rails along x at y 10 (z 6, z 10) into the
    tower's second eave, bracket arms under their ends; the trolley (x 7, y 11) with the hoist's two floating
    lodestones and their spark; two covered beam roofs (storm_lab_parts.beam_roof, x 4..11 over z 6 and z 10 at
    y 12) with the oculus between them over the spawn."""
    for x in (5, 9):
        for z, base in ((5, 3), (11, 5)):
            c.put(x, base - 1, z, CSB)
            for y in range(base, 12):
                c.log(x, y, z, RED)
    for z in range(6, 11):
        c.log(5, 9, z, RED, axis="z")
    for z in (6, 10):
        for x in range(5, 10):
            c.log(x, 10, z, OAK_LOG, axis="x")
        c.put(5, 10, z, GOLD)
    for (x, z) in ((5, 5), (9, 5), (5, 11), (9, 11)):
        c.st(x, 9, z + (1 if z == 5 else -1), "north" if z == 5 else "south", "top", OAK_ST) \
            if c.free(x, 9, z + (1 if z == 5 else -1)) else None
    for z in range(6, 11):
        c.log(7, 11, z, RED, axis="z")
    c.put(7, 10, 7, "lodestone")
    c.put(7, 10, 9, "lodestone")
    c.put(7, 10, 8, "end_rod", facing="up")
    for z in (5, 11):
        for x in (5, 9):
            c.log(x, 12, z, RED)
    chimes += P.beam_roof(c, 4, 11, 6, 12)
    chimes += P.beam_roof(c, 4, 11, 10, 12)


# ================================================================================================ L3 additions
def l3_additions(c, chimes):
    """L3 THE STORM CROWN: the armillary round a floating lodestone over the storm rod (chains carry the stone off the
    rod); the gantry's second tier (gold posts on its band, a lantern roof ring, the oculus still open); taller
    pavilion finials; house banners on the gate's upper storey; a second fire-arrow rack on the stage."""
    tx, tz = TOWER
    P.armillary(c, tx, 23, tz, r=3)
    # the gantry's crown: a lacquer cross-truss (x 7) between the two covered beams, a gold boss and a paper lantern
    # on chains at its middle beside the oculus, rods on the ridges' quarter points
    for z in (7, 9):
        c.log(7, 13, z, RED, axis="z")
    c.put(7, 13, 6, GOLD)
    c.put(7, 13, 10, GOLD)
    for z in (6, 10):
        for x in (6, 9):
            c.rod(x, 14, z)
    for z in (2, 14):
        c.rod(14, 12, z)
    for z in (6, 10):
        if c.get(1, 12, z)[0] != "air":
            c.put(0, 12, z, "red_wall_banner", facing="west")
    for x in (5, 7, 9):
        c.rod(x, 6, 16, "south") if c.free(x, 6, 16) else None


# ================================================================================================ build
def draw(c, level):
    chimes = []
    ground(c)
    garden(c)
    north_range(c, chimes)
    south_range(c, chimes)
    pavilion(c, chimes, 0)
    pavilion(c, chimes, 12)
    tower(c, chimes)
    gantry(c, chimes)
    gate(c, chimes)
    if level >= 3:
        l3_additions(c, chimes)
    for (x, y, z) in chimes:
        if y - 1 >= 4 and c.free(x, y, z) and c.free(x, y - 1, z):
            c.chain(x, y, z)
            c.put(x, y - 1, z, "end_rod", facing="down")
    return chimes


def build(level):
    c = P.Canvas(SX, L3_H + 4, SZ, guard=guard)
    draw(c, level)
    c.leaves()
    c.settle()
    c.check_support("storm L%d" % level)
    c.size = (SX, max(p[1] for p in c.b) + 1, SZ)
    return c


FINE = ("_stairs", "_slab", "_wall", "_fence", "_pane", "iron_bars", "_trapdoor", "_button", "chain", "lantern",
        "candle", "_rod", "end_rod", "_door", "_carpet", "_banner", "petals")


def visible(c):
    """Blocks with an air (or see-through / part-height) neighbour: what the camera can ever see."""
    out = []
    for (x, y, z), (n, _) in c.b.items():
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            q = (x + dx, y + dy, z + dz)
            if q[1] < 0:
                continue
            qn = c.b.get(q, ("air", {}))[0]
            if qn == "air" or any(qn.endswith(f) for f in FINE) or "glass" in qn or "leaves" in qn:
                out.append(n)
                break
    return out


def check(c1, c3):
    n1 = {p: v[0] for p, v in c1.b.items()}
    n3 = {p: v[0] for p, v in c3.b.items()}
    assert all((x, 0, z) in n1 for x in range(SX) for z in range(SZ)), "a hole in layer 0"
    assert c1.size[1] <= L1_H and c3.size[1] <= L3_H, (c1.size, c3.size)
    lost = [p for p in n1 if p not in n3]
    assert not lost, "L3 drops L1 cells: %s" % lost[:8]
    for tag, c, n in (("L1", c1, n1), ("L3", c3, n3)):
        h = c.size[1]
        busy = [(x, y, z) for x in range(LANE_X + 1) for z in LANE_Z for y in range(1, LANE_TOP + 1) if (x, y, z) in n]
        assert not busy, "%s: lane blocked %s" % (tag, busy[:6])
        col = [y for y in range(1, h) if (SPAWN[0], y, SPAWN[1]) in n]
        assert not col, "%s: spawn column blocked at %s" % (tag, col)
        top = max(p[1] for p in n)
        tallest = {(x, z) for (x, y, z) in n if y == top}
        assert all(10 <= x <= 16 and 4 <= z <= 12 for (x, z) in tallest), "%s: the tower is not the top" % tag
        bad = [v for v in n.values() if v in ("tuff_bricks", "polished_tuff", "water", "lava", "fire", "sand", "gravel")
               or ("copper" in v and not v.startswith("waxed_")) or (v.endswith("coral") and not v.startswith("dead_"))]
        assert not bad, "%s: forbidden %s" % (tag, sorted(set(bad)))


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "design/vehicle_labs"
    os.makedirs(out, exist_ok=True)
    c1, c3 = build(1), build(3)
    check(c1, c3)
    import audit_structure_pockets as ap
    for tag, c in (("l1", c1), ("l3", c3)):
        path = os.path.join(out, "storm_%s.nbt" % tag)
        c.structure().write(path)
        one_way, grnd, roof, rooms, note = ap.audit("storm_" + tag, path)
        vis = visible(c)
        fine = sum(1 for n in vis if any(n.endswith(f) for f in FINE))
        allfine = sum(1 for (n, _) in c.b.values() if any(n.endswith(f) for f in FINE))
        print("%s: %dx%dx%d, %d blocks, fine %.0f%% of all / %.0f%% of %d visible; pockets one-way %s ground %s, "
              "roof-only %d%s" % (path, c.size[0], c.size[1], c.size[2], len(c.b), 100.0 * allfine / len(c.b),
                                   100.0 * fine / len(vis), len(vis), one_way[:6], grnd[:6], len(roof), note))
        assert not one_way and not grnd and "TRAPPED" not in note, "pockets"


if __name__ == "__main__":
    main()
