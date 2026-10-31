#!/usr/bin/env python3
"""The Ashborn VEHICLE LAB - THE EMBER PENS (L1 and L3), round 2, assembled from tools/ashborn_lab_parts.py.
Replaces the Hoglin Stables (Reign of Nether's nether-brick look, banned); builds the war-beasts and Ashborn siege.

ANCHORS (structure-local block coords, x 0..16 west -> east, z 0..16 north -> south, y 0 = ground layer; the client
draws these moving parts, the blocks here are their rest pose):
  gate leaf        x 1, z 6..10, y 0: the bronze door drawn DROPPED flush in the floor slot; in game it rises to fill
                   x 1, y 1..8, z 6..10 between its bronze-banded jambs (x 0..1, z 5 and z 11)
  winch drum       (9, 14, 7..9) axis z on its posts (9, 13..14, 6) and (9, 13..14, 10) on the bridge house's deck
  hoist chain      (9, 10..13, 8) hangs from the drum down the oculus; the claw (9, 9, 8) drops to y 1 beside the spawn
  giant's hammer   the maul x 7..9, y 19, z 1..3 in the giant's raised right fist (8, 18, 2); L3 adds a course (y 20): a slow forge blow
                   down toward the oculus and back
  spawn            (8, 1, 8) on the lane's chiseled hearth-stone; the column (8, 1..top, 8) is AIR at L1 and L3
                   (units spawn at the box centre and drop)

THE REFERENCE, STUDIED
  - BASALT COLUMNS (Giant's Causeway, Fingal's Cave, Reynisfjara; lovish's ref25 column rock arch): fields of
    hexagonal prisms of different heights, so a cliff reads as organ pipes and a shore as a staircase of polygons;
    each column jointed every metre (ball-and-socket cracks), its top cupped or cut. Fingal's Cave is the same columns
    round a dark opening. => the whole ground story (T1) is a column wall with polished-basalt joint courses, and the
    gate is a column pylon: two column fields climbing to the jambs, a lintel, and a crest of columns peaking over the
    mouth (ref25's arch), the forge heart set in the crest's face (ref24's one glowing core over the gate).
  - STEPPED TEMPLES (Teotihuacan's talud-tablero, Borobudur's galleried terraces; refs 02 / 20 for how tiers read
    from above): a battered base course, a framed vertical panel, a cornice, each tier stepped back from the one below
    with a balustrade on its edge; the composition climbs to one peak. => three tiers of 4 courses, each stepped in 2
    on the camera's south face (z 14 -> 12 -> 10), materials zoned by tier (basalt columns / polished blackstone
    bricks / deepslate bricks as the dressed-tuff stand-in), piers every 3 cells on every camera face, a parapet of
    walls on every deck edge; the pyramid climbs to the Stone Giant on the north-east deck.
  - THE COLOSSUS (ref03: a statue several storeys tall standing ON the citadel): the landmark is a figure on top,
    clear of the masses, the brightest material in his raised attribute. => the Stone Giant stands on the top tier at
    the north-east, 11 tall, the bronze maul raised over his head the highest thing on the lab (y 19), the tiers
    stepping down toward the camera from him (ref20's pyramid).
  - LAVA (Erta Ale, Kawah Ijen): glow lives in cracks of a black crust, never a flat orange floor. => one forge heart
    over the gate, the gate mouth lit from inside (cracks in the tunnel walls, the hearth at its end), lit windows in
    the bays, two fire bowls on the jambs - and nothing glowing on any top surface.

THE PLAN (17 x 17; the RTS camera looks north-east from the south-west, so it sees the west face, the south face
and the roofs)
  - THE APRON: the ground y 0 is ash and gravel outside the tiers; a low crater rim of basalt stumps on the south
    edge and the west corners (z 15..16; x 0..1 at z 0..1 and z 15..16).
  - THE FISSURE GATE (x 0..1, z 2..14): the column pylon, the 5 x 8 mouth (z 6..10), the bronze-and-basalt lintel
    at y 9, the heart at y 11..13, the crest to y 15, fire bowls on the jambs.
  - T1 (y 1..4, x 2..16, z 0..14 less the lane): basalt column wall; the PENS in its south face (z 14) - barred
    stalls with the beasts' hides behind the bars between polished-blackstone piers; its deck (y 5) is the pens'
    gallery with a wall parapet.
  - T2 (y 5..8): polished blackstone bricks, a ring round the lane - the north band z 0..5, the south band z 11..12,
    the east block x 12..16 (the HEARTH in its lane face x 12 at the lane's end); its south face (z 12) has lit
    windows in its bays; its deck (y 9) is the GIANT'S COURT on the north-east.
  - T3 (y 9..12): the BRIDGE HOUSE of deepslate bricks over the lane, x 2..11, z 3..10, with the 3 x 3 OCULUS (x 7..9,
    z 7..9) over the spawn, its crater lip and the winch; the lane under it is a dark tunnel from the mouth to the
    hearth.
  - THE STONE GIANT on T2's north-east deck (x 8..16, z 1..4, y 9..19).
  L3 (adds, never removes): the giant raises the hammer higher (a longer haft, a bigger maul, y 21..22), and the
  crater chimney - a stepped stupa-cone flue on the bridge house's north-west corner with a bronze ring, under the
  giant; the gate crest grows two courses.

KIT: basalt / polished basalt / smooth basalt, polished blackstone (bricks), deepslate bricks / tiles / polished
deepslate, chiseled panels, obsidian plinths, bronze = waxed cut copper (the giant, the gate, the hearth, the winch),
coarse dirt / gravel / tuff for the ash; no gold, gilded blackstone, nether brick, crimson / warped wood, netherrack,
banners or piglin heads (asserted in check()).

Usage: python3 tools/lab_vehicle_ashborn.py [OUT_DIR]   (default design/vehicle_labs) -> ashborn_l1.nbt, ashborn_l3.nbt
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs            # noqa: E402
import gothic_parts as gp              # noqa: E402
import ashborn_lab_parts as ap         # noqa: E402

SX = SZ = 17
L1_MAX_H, L3_MAX_H = 20, 28
LANE = [(x, z) for x in range(0, 12) for z in range(6, 11)]
LANE_SET = set(LANE)
SPAWN = (8, 8)
OCULUS = [(x, z) for x in range(7, 10) for z in range(7, 10)]
hsh = gp.hsh
VEC = gp.VEC
OPP = gp.OPP

T1, T2, T3 = (1, 4), (5, 8), (9, 12)          # each tier's bottom and top course
PIERS_X = (2, 4, 7, 10, 13, 16)               # the bay rhythm on the south faces (T1 and T2 share it)


def stone(p, x, y, z, zone, seed=0):
    ap.stone(p, x, y, z, zone, seed)


# ============================================================================================ ground + apron
def apron_cell(x, z):
    """Outside the tiers and the gate: the south strip z 15..16 and the four corner cells by the gate's ends."""
    return z >= 15 or (x <= 1 and (z <= 1 or z >= 15))


def ground(p):
    for x in range(SX):
        for z in range(SZ):
            h = hsh(x, z, 5)
            if (x, z) in LANE_SET:
                if z in (6, 10):
                    p.set(x, 0, z, "pier", axis="x")
                elif z == 8:
                    p.set(x, 0, z, "pbb" if x % 3 else "pd")
                else:
                    p.set(x, 0, z, ("pb", "blk", "pbb_c", "pbb")[h % 4])
            elif apron_cell(x, z):
                p.set(x, 0, z, ("ash", "grav", "tuff", "crag_s", "grav", "ash")[h % 6])
            else:
                p.set(x, 0, z, "blk")
    p.set(SPAWN[0], 0, SPAWN[1], "carve")
    for z in range(6, 11):
        p.set(1, 0, z, "bronze")


def apron(p):
    """The crater rim: basalt stumps of 1..3 courses on the south edge, taller at the corners, gravel between."""
    rim = {(0, 16): 3, (1, 16): 2, (0, 15): 2, (2, 16): 1, (1, 15): 1,
           (16, 16): 3, (15, 16): 2, (16, 15): 2, (14, 16): 1, (15, 15): 1,
           (6, 16): 1, (10, 16): 1, (0, 0): 2, (1, 0): 1, (0, 1): 1, (16, 0): 0, (4, 16): 1, (8, 16): 1,
           (12, 16): 1, (3, 15): 1, (13, 15): 1}
    for (x, z), h in rim.items():
        for y in range(1, h + 1):
            if y == h and h > 1:
                ap.pier(p, x, y, z)
            else:
                p.set(x, y, z, "crag", axis="y")
        if h:
            k = hsh(x, z, 9) % 3
            if k == 0:
                p.slab(x, h + 1, z, "bottom", "pb_slab")
            elif k == 1 and h > 1:
                p.stair(x, h + 1, z, gp.toward(x, z, 8, 8), "bottom", "pb_st")


# ============================================================================================ tiers
def tier_box(p, x0, x1, z0, z1, y0, y1, zone, seed):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(y0, y1 + 1):
                stone(p, x, y, z, zone, seed)


def face(p, cells, inward, y0, y1, piers, st, cap, field=None, string=True):
    """One ORDERED camera face. cells: the face's (x, z) in order; inward: the facing toward the mass; piers: the
    face positions (index) that are piers (full polished basalt y0..y1, a chiseled capital); the bays get a battered
    base (a bottom stair facing in) at y0, a chamfered cornice (a top stair facing in) at y1, and a field between -
    `field(i, y, x, z)` may set the bay cell itself (a lit window, a stall, a panel)."""
    dx, dz = VEC[OPP[inward]]                    # outward
    for i, (x, z) in enumerate(cells):
        if i in piers:
            for y in range(y0, y1 + 1):
                ap.pier(p, x, y, z)
            p.set(x, y1, z, cap)
            continue
        p.stair(x, y0, z, inward, "bottom", st)
        p.stair(x, y1, z, inward, "top", st)
        if field:
            for y in range(y0 + 1, y1):
                field(i, y, x, z)
        if string and (x, y1 - 1, z) in p.b and p.get(x, y1 - 1, z) not in ("orange_stained_glass_pane",):
            p.stair(x, y1 - 1, z, inward, "top", st)


def lit_window(p, x, y, z, inward, tall=2):
    """A lit window: orange panes in the face, shroomlight one cell in (behind the pane, never seen from above)."""
    dx, dz = VEC[inward]
    for k in range(tall):
        p.set(x, y + k, z, "pane")
        p.set(x + dx, y + k, z + dz, "glow")


def parapet(p, cells, y, posts, rail="pbb_wall", post="pbb"):
    for i, (x, z) in enumerate(cells):
        p.set(x, y, z, post if i in posts else rail)


def south_cells(x0, x1, z):
    return [(x, z) for x in range(x0, x1 + 1)]


def west_cells(x, z0, z1):
    return [(x, z) for z in range(z0, z1 + 1)]


def tier1(p):
    """T1: the basalt column wall, x 2..16, z 0..14 (the lane is cut later); the PENS in the south face."""
    tier_box(p, 2, 16, 0, 14, T1[0], T1[1], "basalt_o", 1)
    cells = south_cells(2, 16, 14)
    piers = {i for i, (x, _) in enumerate(cells) if x in PIERS_X}

    def stall(i, y, x, z):
        p.set(x, y, z, "bars")
        p.set(x, y, z - 1, "hide" if (x + y) % 2 else "hide2")
    # the stalls are 3 tall (y 1..3): bars in the face, hides behind; piers of polished blackstone between them
    for i, (x, z) in enumerate(cells):
        if i in piers:
            for y in range(1, 5):
                p.set(x, y, z, "pbb")
            p.set(x, 4, z, "carve2")
            continue
        for y in (1, 2, 3):
            stall(i, y, x, z)
        p.stair(x, 4, z, "north", "top", "pb_st")
    # the other faces: a battered base and a cornice of polished basalt stairs, piers every 3 at the corners' rhythm
    for cells_, inward in ((west_cells(2, 0, 5), "east"), (west_cells(2, 11, 13), "east"),
                           (west_cells(16, 0, 13), "west"), (south_cells(3, 15, 0), "south")):
        pr = {i for i, (x, z) in enumerate(cells_) if (x in (2, 16) and z in (0, 5, 11, 13)) or (z == 0 and x in PIERS_X)}
        face(p, cells_, inward, T1[0], T1[1], pr, "pb_st", "carve2", string=False)
    # the deck's parapet over the pens: posts over the piers, polished-basalt walls between
    parapet(p, cells, 5, piers, rail="pb_wall", post="pbb")


def flute(p):
    """THE CAUSEWAY'S PRISMS on T1's exposed column faces (the one thing round 1's critic liked): a basalt cell with
    open air on one side and solid stone behind becomes a wall shaft (a narrower prism standing proud of the column
    behind), the polished joints stay full - the wall reads as jointed, faceted prisms, not a block."""
    occ = dict(p.b)
    full = {c for c, (n, _) in occ.items() if n != "air" and not any(k in n for k in THIN)}
    for (x, y, z), (n, _) in sorted(occ.items()):
        if n != "basalt" or not (2 <= y <= 8) or (x, z) in LANE_SET or (x, y + 1, z) not in occ:
            continue
        outs = [f for f, (dx, dz) in VEC.items() if (x + dx, y, z + dz) not in occ]
        if len(outs) != 1:
            continue
        dx, dz = VEC[outs[0]]
        if (x - dx, y, z - dz) not in full:
            continue
        p.set(x, y, z, "tile_wall" if (x + z) % 2 else "blk_wall")


def tier2(p):
    """T2: polished blackstone bricks, a ring round the lane: north band z 0..5, south band z 11..12, east block
    x 12..16; lit windows in the south face's bays; a parapet on the deck edges."""
    tier_box(p, 4, 15, 0, 5, T2[0], T2[1], "black_o", 2)
    tier_box(p, 4, 15, 11, 12, T2[0], T2[1], "black_o", 3)
    tier_box(p, 12, 15, 6, 10, T2[0], T2[1], "black_o", 4)
    cells = south_cells(4, 15, 12)
    piers = {i for i, (x, _) in enumerate(cells) if x in PIERS_X}

    def field(i, y, x, z):
        if x in (8, 9, 14, 15):
            lit_window(p, x, 6, z, "north", 2) if y == 6 else None
        elif x in (5, 11) and y == 6:
            p.set(x, y, z, "carve2")
    face(p, cells, "north", T2[0], T2[1], piers, "pb_st", "carve2", field)
    # the west faces (x 4) of both bands: pier at the corner, a lit window in the north band's bay
    for z0, z1 in ((0, 5), (11, 12)):
        w = west_cells(4, z0, z1)
        face(p, w, "east", T2[0], T2[1], {0, len(w) - 1} if z1 - z0 > 1 else {0}, "pb_st", "carve2",
             (lambda i, y, x, z: lit_window(p, x, 6, z, "east", 2) if z == 2 and y == 6 else None))
    for cells_, inward in ((west_cells(15, 0, 12), "west"), (south_cells(5, 14, 0), "south")):
        pr = {i for i, (x, z) in enumerate(cells_) if (x == 15 and z % 3 == 0) or (z == 0 and x in PIERS_X)}
        face(p, cells_, inward, T2[0], T2[1], pr, "pb_st", "carve2")
    # the east block's lane face (x 12, z 6..10) carries the hearth; the ring's inner faces stay plain column walls
    parapet(p, cells, 9, piers)
    east = west_cells(15, 0, 12)
    parapet(p, east, 9, {0, 3, 6, 9, 12})
    north = south_cells(4, 14, 0)
    parapet(p, north, 9, {0, 3, 6, 9, 10})
    parapet(p, west_cells(4, 1, 5), 9, {4})
    parapet(p, west_cells(4, 11, 12), 9, set())
    parapet(p, [(x, 11) for x in range(5, 12)], 9, {6})
    parapet(p, [(x, 5) for x in range(5, 12)], 9, {3, 6})


def tier3(p):
    """T3: the bridge house of deepslate bricks over the lane, x 5..11 at z 5..10 and x 6..7 at z 3..4, with the
    oculus x 7..9, z 7..9; lit windows in its south and west bays; a crater lip round the oculus; a parapet."""
    tier_box(p, 5, 11, 5, 10, T3[0], T3[1], "dress_o", 5)
    tier_box(p, 6, 7, 3, 4, T3[0], T3[1], "dress_o", 5)
    for (x, z) in OCULUS:
        for y in range(T3[0], T3[1] + 1):
            p.clear(x, y, z)
    cells = south_cells(5, 11, 10)
    piers = {i for i, (x, _) in enumerate(cells) if x in (5, 8, 11)}

    def field(i, y, x, z):
        if x in (6, 7, 10) and y == 10:
            lit_window(p, x, 10, z, "north", 2)
        elif x == 9 and y == 10:
            p.set(x, y, z, "carve")
    face(p, cells, "north", T3[0], T3[1], piers, "dress_st", "pd", field)
    west = west_cells(5, 5, 10)
    wp = {0, 3, 5}
    face(p, west, "east", T3[0], T3[1], wp, "dress_st", "pd",
         (lambda i, y, x, z: lit_window(p, x, 10, z, "east", 2) if z in (7, 9) and y == 10 else None))
    for cells_, inward in ((west_cells(11, 5, 9), "west"), (south_cells(8, 10, 5), "south"),
                           (south_cells(6, 7, 3), "south"), (west_cells(6, 3, 4), "east"), (west_cells(7, 3, 4), "west")):
        face(p, cells_, inward, T3[0], T3[1], {0, len(cells_) - 1}, "dress_st", "pd")
    # parapet y 13: south edge, west edge, the north step, the east edge
    parapet(p, cells, 13, piers, rail="dress_wall", post="pd")
    parapet(p, west, 13, wp, rail="dress_wall", post="pd")
    parapet(p, [(6, 3), (7, 3), (6, 4), (7, 4), (7, 5)], 13, {0, 1}, rail="dress_wall", post="pd")
    for z in range(5, 10):
        p.set(11, 13, z, "dress_wall" if z != 5 else "pd")
    for x in (8, 9, 10):
        p.set(x, 13, 5, "dress_wall")
    # the oculus's crater lip: stairs leaning out from the hole on its rim
    for x in range(6, 11):
        for z in range(6, 11):
            if (x, z) in OCULUS:
                continue
            f = gp.toward(x, z, 8, 8)
            if x in (6, 10) and z in (6, 10):
                p.set(x, 13, z, "pd")
            else:
                p.stair(x, 13, z, OPP[f], "bottom", "tile_st")


def corbels(p):
    """A CORBEL TABLE under T2's and T3's cornices on the camera faces (south, west): an upside-down stair hung out
    from the cornice course over the deck below - the projecting cornice of a stepped temple, a shadow line under
    every set-back (one more depth plane)."""
    occ = dict(p.b)
    for (x, y, z), (n, _) in sorted(occ.items()):
        if y not in (T2[1], T3[1]) or "stairs" not in n and n != "polished_basalt" and n != "chiseled_polished_blackstone" \
                and n != "polished_deepslate":
            continue
        for f in ("west", "south"):
            dx, dz = VEC[f]
            c = (x + dx, y, z + dz)
            if c in occ or not (0 <= c[0] < SX and 0 <= c[2] < SZ) or (c[0], c[2]) in LANE_SET:
                continue
            if (c[0], y - 1, c[2]) in occ or (c[0], y + 1, c[2]) in occ:
                continue
            if (x - dx, y, z - dz) not in occ:
                continue
            p.stair(c[0], y, c[2], OPP[f], "top", "pb_st" if y == T2[1] else "dress_st")


def decks(p):
    """Every deck top gets a slab course of its own tier's stone (a quiet plane, half a block of shadow under each
    parapet), except where something stands on it; T1's exposed ledges (x 2..3 and z 13) are column stumps of
    stepped heights with cupped tops - the Causeway's shore in front of the tiers."""
    for (x, z) in [(x, z) for x in range(4, 16) for z in range(1, 12)]:
        if (x, 9, z) not in p.b and (x, 8, z) in p.b and p.get(x, 10, z) == "air":
            p.slab(x, 9, z, "bottom", "pb_slab")
    for (x, z) in [(x, z) for x in range(5, 11) for z in range(4, 10)]:
        if (x, 13, z) not in p.b and (x, 12, z) in p.b and (x, z) not in OCULUS:
            p.slab(x, 13, z, "bottom", "tile_slab")
    for (x, z) in [(x, z) for x in range(2, 16) for z in range(0, 14)]:
        if (x, 5, z) in p.b or (x, 4, z) not in p.b or (x, z) in LANE_SET:
            continue
        k = hsh(x, z, 23) % 5
        if x <= 3 or z == 13:
            if k == 0:
                ap.pier(p, x, 5, z)
                p.slab(x, 6, z, "bottom", "pb_slab")
            elif k == 1:
                p.set(x, 5, z, "crag", axis="y")
                p.stair(x, 6, z, gp.toward(x, z, 8, 7), "bottom", "pb_st")
            elif k == 2:
                p.slab(x, 5, z, "bottom", "pb_slab")
            else:
                p.set(x, 5, z, "crag", axis="y")
        else:
            p.slab(x, 5, z, "bottom", "pb_slab")


def hearth(p):
    ap.put(p, ap.hearth(5, 8), 12, 0, 6, rot=1)


def lane_cut(p):
    for (x, z) in LANE:
        for y in range(1, 9):
            p.clear(x, y, z)
    # the crack-lit tunnel: barred glowing fissures low in both tunnel walls
    for x in (2, 3, 6, 9):
        for y in (2, 3):
            p.set(x, y, 5, "bars")
            p.set(x, y, 4, "magma" if (x + y) % 2 else "glow")
    for x in (5, 8):
        for y in (1, 2):
            p.set(x, y, 11, "bars")
            p.set(x, y, 12, "glow" if y == 1 else "magma")


def gate(p):
    ap.put(p, ap.gate_pylon(flank=(4, 6, 8), crest=(3, 5, 6, 8, 6, 5, 3)), 0, 0, 2)
    # fire bowls on the jambs' tops (y 13): an obsidian plinth, a bronze rim, the fire
    for z in (5, 11):
        p.set(0, 12, z, "obs")
        p.set(0, 13, z, "campfire", lit="true", facing="south", signal_fire="false", waterlogged="false")
        p.set(1, 12, z, "bronze_w")
        p.stair(1, 13, z, "west", "bottom", "bronze_st")


def giant(p):
    ap.put(p, ap.colossus(), 8, 9, 1)


def winch(p):
    for z in (6, 10):
        p.set(9, 13, z, "pbb")
        p.set(9, 14, z, "bronze_w")
    for z in (7, 8, 9):
        ap.pier(p, 9, 14, z, axis="z")
    p.set(9, 14, 7, "bronze")
    for y in range(10, 14):
        ap.chain(p, 9, y, 8)
    p.set(9, 9, 8, "bronze_w")
    for (x, z) in ((7, 7), (7, 9)):
        for y in (11, 12, 13):
            ap.chain(p, x, y, z)


def spawn_column(p):
    for y in range(1, 40):
        p.clear(SPAWN[0], y, SPAWN[1])


# ============================================================================================ L1 / L3
def core(p):
    ground(p)
    apron(p)
    tier1(p)
    tier2(p)
    tier3(p)
    corbels(p)
    hearth(p)
    lane_cut(p)
    gate(p)
    flute(p)
    giant(p)
    winch(p)
    decks(p)
    spawn_column(p)


def draw_l1(p):
    core(p)


def draw_l3(p):
    core(p)
    # (1) THE CRATER CHIMNEY: a stepped stupa-cone flue on the bridge house's north-west corner (centre x 6, z 5),
    # deepslate courses stepping in, a tile chamfer at each set-back, a bronze ring, an obsidian crater with its fire
    # (the one readable thing L3 adds; it stays under the giant's maul)
    for (r, y0, y1) in ((2, 13, 13), (1, 14, 16)):
        for x in range(6 - r - 1, 6 + r + 2):
            for z in range(5 - r - 1, 5 + r + 2):
                d = abs(x - 6) + abs(z - 5)
                if d > r + 1 or (x, z) in OCULUS or (x, 12, z) not in p.b:
                    continue
                if d <= r:
                    for y in range(y0, y1 + 1):
                        stone(p, x, y, z, "dress_o", 8)
                elif (x, y0, z) not in p.b:
                    p.stair(x, y0, z, gp.toward(x, z, 6, 5), "bottom", "tile_st")
    for x in (5, 6, 7):
        for z in (4, 5, 6):
            if abs(x - 6) + abs(z - 5) == 1:
                p.set(x, 16, z, "bronze_w")
    p.set(6, 17, 5, "obs")
    p.set(6, 18, 5, "campfire", lit="true", facing="south", signal_fire="false", waterlogged="false")
    # (2) the giant's maul grows: a course on top and flared cheeks either side (L3 adds, never removes)
    for x in (8, 9, 10):
        for z in (1, 2, 3):
            p.set(x, 20, z, "bronze_block" if (x, z) == (9, 2) else "bronze" if (x + z) % 2 else "bronze_w")
    for x in (7, 11):
        for z in (1, 2, 3):
            p.stair(x, 19, z, "east" if x == 7 else "west", "bottom", "bronze_st")
        p.set(x, 20, 2, "bronze_w")
    # (3) the gate crest grows two courses over the mouth
    for z in range(6, 11):
        for x in (0, 1):
            t = top_at(p, x, z)
            n = p.get(x, t, z)
            if n in ("polished_basalt_slab", "lightning_rod"):
                p.set(x, t, z, "crag", axis="y")
            for y in (t + 1, t + 2):
                p.set(x, y, z, "pier" if y == t + 2 else "crag", axis="y")
            if n == "polished_basalt_slab":
                p.slab(x, t + 3, z, "bottom", "pb_slab")
            elif n == "lightning_rod":
                ap.rod(p, x, t + 3, z)
    spawn_column(p)


def top_at(p, x, z):
    return max([y for (xx, y, zz) in p.b if (xx, zz) == (x, z)] + [0])


# ============================================================================================ build + check
THIN = ("stairs", "slab", "wall", "pane", "bars", "chain", "lantern", "candle", "rod", "campfire", "trapdoor", "door",
        "carpet")
BANNED = ("gold", "gilded", "nether_brick", "crimson", "warped", "netherrack", "banner", "piglin", "water", "lava",
          "redstone", "soul_", "_leaves", "tuff_brick", "polished_tuff")
FINE = ("stairs", "slab", "_wall", "fence", "pane", "bars", "trapdoor", "button", "chain", "lantern", "candle", "rod",
        "door", "carpet")


def hollow(p):
    """Core cells from y 3 up whose whole 3 x 3 x 3 neighbourhood is full blocks: never seen, left out."""
    fulls = {c for c, (n, _) in p.b.items() if n != "air" and not any(k in n for k in THIN)}
    return [(x, y, z) for (x, y, z) in fulls if y >= 3 and all(
        (x + dx, y + dy, z + dz) in fulls for dx in (-1, 0, 1) for dy in (-1, 0, 1) for dz in (-1, 0, 1))]


def to_rig(p, hmax, tag, keep=frozenset()):
    for c in hollow(p):
        if c not in keep:
            p.b.pop(c, None)
    p.b = {c: v for c, v in p.b.items() if v[0] not in ("air", "cave_air")}
    h = max(c[1] for c in p.b) + 1
    assert h <= hmax, "%s is %d tall (max %d)" % (tag, h, hmax)
    r = gs._SunRig(SX, h, SZ)
    for (x, y, z), (n, pr) in p.b.items():
        r.set(x, y, z, n, **pr)
    r.settle()
    r.check_support(tag)
    return r


def check(r, tag):
    blocks = {c: n for c, (n, _) in r.b.items() if n not in ("air", "cave_air")}
    bad = sorted({n for n in blocks.values() if any(b in n for b in BANNED)})
    assert not bad, "%s: banned blocks %s" % (tag, bad)
    assert all((x, 0, z) in blocks for x in range(SX) for z in range(SZ)), "%s: hole in the ground layer" % tag
    shut = [(x, y, z) for (x, z) in LANE for y in range(1, 9) if (x, y, z) in blocks]
    assert not shut, "%s: the lane is narrowed at %s" % (tag, shut[:10])
    col = [(SPAWN[0], y, SPAWN[1]) for y in range(1, r.size[1]) if (SPAWN[0], y, SPAWN[1]) in blocks]
    assert not col, "%s: the spawn column is blocked at %s" % (tag, col)
    lone = [c for c in blocks if c[1] > 0 and not any(
        (c[0] + d[0], c[1] + d[1], c[2] + d[2]) in blocks
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)))]
    assert not lone, "%s: floating blocks %s" % (tag, lone)
    copper = sorted({n for n in blocks.values() if "copper" in n and not n.startswith(("waxed", "raw_"))})
    assert not copper, "%s: unwaxed copper %s" % (tag, copper)
    glow_top = [c for c, n in blocks.items() if n in ap.GLOWS and (c[0], c[1] + 1, c[2]) not in blocks]
    assert not glow_top, "%s: glow on a top surface %s" % (tag, glow_top)
    fine = sum(1 for nm in blocks.values() if any(k in nm for k in FINE))
    # visible: a face open to air (or the box's side) - the share the brief counts
    vis = visible(blocks, r.size)
    vfine = sum(1 for c in vis if any(k in blocks[c] for k in FINE))
    return len(blocks), fine, len(vis), vfine


def visible(blocks, size):
    """Blocks with a face on air that connects to the outside of the box (an air flood from the margin, through
    thin blocks too): the honest "visible" count, not a hollow's inner shell."""
    sx, sy, sz = size
    solid = {c for c, n in blocks.items() if not any(k in n for k in THIN)}
    seen, stack = set(), []
    for x in range(-1, sx + 1):
        for z in range(-1, sz + 1):
            for y in range(1, sy + 1):
                if x in (-1, sx) or z in (-1, sz) or y == sy:
                    stack.append((x, y, z))
    seen.update(stack)
    while stack:
        x, y, z = stack.pop()
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            c = (x + d[0], y + d[1], z + d[2])
            if c in seen or c in solid or not (-1 <= c[0] <= sx and 0 <= c[1] <= sy and -1 <= c[2] <= sz):
                continue
            seen.add(c)
            stack.append(c)
    return {c for c in blocks if c in seen or any((c[0] + d[0], c[1] + d[1], c[2] + d[2]) in seen for d in (
        (1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)))}


def audit(path, tag, sy):
    import audit_structure_pockets as aud
    import lab_portal_military as pm
    one_way, ground_, roof, rooms, note = aud.audit(tag, path)
    pad = aud.Pad(aud.load_nbt(path))
    assert pad.walkable(SPAWN[0], 1, SPAWN[1]), "spawn cell not standable"
    walk = pm.reachable(pad, SX, SZ, sy)
    hot = sorted(c for c in walk if aud.base(pad.at(c[0], c[1] - 1, c[2])[0]) in aud.FIRE_FLOORS | set(ap.GLOWS))
    off = sorted(c for c in walk if 0 <= c[0] < SX and 0 <= c[2] < SZ and (c[1] > 1 or (c[0], c[2]) not in LANE_SET))
    return one_way, ground_, roof, note, hot, off


def build_level(level):
    """The rig of one level (1 or 3), written nowhere - tools/test_ashborn_lab.py builds the lab through this."""
    p1, p3 = ap.new(), ap.new()
    draw_l1(p1)
    draw_l3(p3)
    r1 = to_rig(p1, L1_MAX_H, "ashborn L1")
    if level == 1:
        return r1
    for c in {c for c, (n, _) in r1.b.items() if n not in ("air", "cave_air")}:
        if c not in p3.b:
            p3.b[c] = r1.b[c]
    return to_rig(p3, L3_MAX_H, "ashborn L3", keep=set(r1.b))


def main(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    p1, p3 = ap.new(), ap.new()
    draw_l1(p1)
    draw_l3(p3)
    r1 = to_rig(p1, L1_MAX_H, "ashborn L1")
    a1 = {c for c, (n, _) in r1.b.items() if n not in ("air", "cave_air")}
    for c in a1:
        if c not in p3.b:
            p3.b[c] = r1.b[c]
    r3 = to_rig(p3, L3_MAX_H, "ashborn L3", keep=a1)
    a3 = {c for c, (n, _) in r3.b.items() if n not in ("air", "cave_air")}
    lost = sorted(a1 - a3)
    assert not lost, "L3 removes L1 cells %s" % lost[:10]
    ok = True
    for tag, r in (("l1", r1), ("l3", r3)):
        n, fine, vis, vfine = check(r, tag)
        path = os.path.join(out_dir, "ashborn_%s.nbt" % tag)
        r.structure().write(path)
        one_way, ground_, roof, note, hot, off = audit(path, "ashborn_" + tag, r.size[1])
        print("%s %s: %d blocks, fine %d (%.0f%%), visible %d of which fine %d (%.0f%%); pockets one-way %d %s "
              "ground %d %s roof-only %d%s; hot %s; off-lane reachable %d %s"
              % (tag, r.size, n, fine, 100.0 * fine / n, vis, vfine, 100.0 * vfine / vis, len(one_way), one_way[:6],
                 len(ground_), ground_[:6], len(roof), note, hot[:6], len(off), off[:10]))
        ok &= not one_way and not ground_ and "TRAPPED" not in note and not hot
    assert ok, "audit failed"


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(
        os.path.dirname(os.path.abspath(__file__)), "..", "design", "vehicle_labs"))
