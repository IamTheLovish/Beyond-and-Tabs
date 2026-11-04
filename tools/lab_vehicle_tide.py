#!/usr/bin/env python3
"""The Tidewrought VEHICLE LAB, THE CAREENING HARD - round 2, rebuilt at 17 x 17 from the part library
tools/tide_lab_parts.py. It builds the Mudskipper Sloop (the amphibious boat-creature, on her cradle in the covered
slip) and the Bombard Crew. Family: the Slipway (tools/lab_slipway.py), the Admiral's Beacon
(tools/capitol_wreck_harbour.py) - sandstone apron, prismarine quoins, wreck timber, waxed copper, the lighthouse.

THE REFERENCE, STUDIED
  - CHATHAM No. 3 SLIP COVER (1838, Seppings): a timber-framed shed over one slipway, the water end OPEN so the hull
    slides out, the trusses seen in section at that open gable; one long low roof - the yard's big quiet plane.
  - A LOCK HEAD: two dressed-stone piers either side of the chamber, the timber leaf riding in grooves, lifted by a
    windlass on the header. The exit here IS the lock: a deep framed void, the leaf drawn up, the slipway's ways
    running out of it onto the hard.
  - CAREENING: a ship hove down on the hard beside the yard, bottom scraped and re-coppered, in a log cradle with
    shores against her side; stacked timber, barrels, a jib crane swung over her.
  - lovish's refs (amendment): ref04 / ref11 - mass by accretion of small timber volumes, jettied on brackets, mixed
    wood tones, two-tone stair roofs; ref17 - a tower broken by a gallery ring and a flared cap; ref15 - warm light
    from a few lit windows; ref01 - order: repeated bays, a cornice at every storey, trim on every edge.

HIERARCHY (from the RTS camera, south-west; height cap L1 <= 20, L3 <= 28, the lighthouse the tallest thing):
  (a) LANDMARK: the banded LIGHTHOUSE in the north-east corner, cap at y 19 (L3: 27, with a timber gallery ring) -
      the only tall thing; its sea-lantern lamp room is the signature glow.
  (b) GATE: the LOCK HEAD on the west face - the exit a 5 x 8 void (z 6..10, y 1..8) framed by stripped-dark-oak jambs
      and banded sandstone / prismarine piers, the SLUICE LEAF drawn up over it (y 9..10), a waxed-copper lintel and the
      windlass drum in the gable above; the mangrove-plank slipway floor runs out of it. Everything else on that face
      is quieter: the jettied loft to the north, the hull's bow to the south.
  (c) SECONDARY MASSES stepping down: the gate house's gable roof (ridge y 14), then the COVERED SLIP's long copper roof
      (ridge y 12) over the Sloop, its west gable an open truss onto the hoist well; the jettied SAIL LOFT (ridge 10)
      in the north-west; the hull on the hard (y <= 11) along the south.
  (d) GROUND: one base material - cut sandstone with a dark-prismarine waterline course and prismarine-brick quoins -
      and the smooth-sandstone apron with a cut kerb; props (timber, barrels, a capstan) in the south-east yard.

ANCHORS (client-drawn moving parts; x y z in the 17 x H x 17 box, x west -> east, z north -> south):
  gantry beam      x 7, y 12, z 4..12 (dark oak, axis z; slab rail at y 13) on two trestles (x 6..7: north z 4..5,
                   south z 11..12), beside the spawn column (8, *, 8), never over it
  gantry trolley   (7, 11, 8)  waxed-copper pulley block under the beam (runs along z)
  hoist            (7, 9..10, 7..9)  the cargo net (mangrove roots) round a barrel at (7, 9, 8); lowest block y 9
  gate leaf        (0..1, 9..10, 6..10)  the sluice leaf drawn RAISED (dark-oak boards at x 0, spruce backing at x 1,
                   stiles in the jambs at z 5 / 11); in game it drops to y 1 between builds
  windlass drum    (0, 12, 8)  waxed-copper drum between dark-oak cheeks in the gate gable; turns as the leaf moves
  jib crane        arm (3, 11, 11..15) over the hull on the hard, its chain (3, 9..10, 15) - swings / lowers
  lighthouse lamp  (14, 13..15, 2)  sea lanterns in the lamp room (L3: (14, 21..23, 2)) - a slow beam sweep
  L3 adds          the sail-loft's DRYING MAST (8, 6..23, 2) beside the lighthouse with its yard (y 20, x 5..11) and
                   the fleet's striped sail hung from it (x 5..10, y 13..19, z 3) - it flaps; the hull's topmast

THE GROWTH: L3 (same footprint, only adds): the lighthouse raised by eight courses with a timber gallery ring; the
drying mast with the striped sail beside it (one spire, read together); the hull on the hard gets her topmast and
the fleet's pennant.

RULES (check()): 17 x 17, L1 <= 20 tall, L3 <= 28; layer 0 a full foundation; the EXIT LANE x 0..8, z 6..10, y 1..8
air; the SPAWN COLUMN over (8, 8) air to the top at both levels (amendment 34); every non-air cell of L1 non-air in
L3; vanilla 1.20.1 blocks, waxed copper and dead coral only, no water / gravity / redstone / fire / doors / carpets /
candles; nothing pops off or floats; no pockets (tools/audit_structure_pockets.py audit()); a unit spawned at
(8, 1, 8) walks out west.

Usage: python3 tools/lab_vehicle_tide.py [OUT_DIR]   (default design/vehicle_labs; writes tide_l1.nbt, tide_l3.nbt)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs              # noqa: E402
import tide_lab_parts as TP              # noqa: E402
from tide_lab_parts import TPart    # noqa: E402

S = 17
SPAWN = (8, 8)
LANE = [(x, y, z) for x in range(0, 9) for z in range(6, 11) for y in range(1, 9)]
H1, H3 = 20, 28
LH = (14, 2)                     # the lighthouse's axis
GANTRY_X = 7


def clip(p):
    p.b = {k: v for k, v in p.b.items() if 0 <= k[0] < S and 0 <= k[2] < S and k[1] >= 0}


def quoin(y):
    return "pb" if y % 2 else "cs"


# ============================================================================================ the assembly
def ground(p):
    """Layer 0, calm: a smooth-sandstone apron with a cut-sandstone kerb; the SLIPWAY in the lane - mangrove decking
    with two stripped-spruce ways flush in it - running from under the Sloop out through the lock; a dark-prismarine
    sill across the lock's mouth and under the hull's cradle."""
    for x in range(S):
        for z in range(S):
            corner = x in (0, S - 1) and z in (0, S - 1)
            edge = x in (0, S - 1) or z in (0, S - 1)
            p.set(x, 0, z, "chs" if corner else "cs" if edge else "ss")
    for x in range(0, 16):
        for z in range(6, 11):
            if z in (7, 9):
                p.log(x, 0, z, "x", "post2")
            else:
                p.set(x, 0, z, "hullp")
    for z in range(5, 12):
        p.set(0, 0, z, "dp")
    for x in range(0, 10):
        for z in range(13, 17):
            p.set(x, 0, z, "dp")


def loft(p):
    """The SAIL LOFT, north-west (x 0..4, z 0..4) - ref04 / ref11's accretion, every storey a cell further out: the
    cut-sandstone ground storey at x 2..4 (a dark waterline course, prismarine-brick quoins); storey 2 (y 5..7) JETTIED
    to x 1 on dark-oak brackets - posts, a girt, weatherboard, a lit two-light window to the camera; storey 3 (y 9..10)
    jettied again to x 0 - a band of spruce boards under a dark-oak girt with a lit single light; a copper gable roof
    (ridge along x, y 12) with a dark-oak bargeboard on the west rake. It stays under the gate house's ridge. Solid
    inside."""
    for y in range(1, 5):
        for x in range(2, 5):
            for z in range(0, 5):
                corner = x in (2, 4) and z in (0, 4)
                p.set(x, y, z, "dp" if y == 1 else quoin(y) if corner else "cs")
    for z in range(0, 5):
        p.st(1, 4, z, "east", "top", "tst")                       # the jetty brackets
    for y in range(5, 8):
        for x in range(1, 5):
            for z in range(0, 5):
                face = x == 1
                if face:
                    if z in (0, 4):
                        p.log(x, y, z, "y", "post")
                    elif y == 5:
                        p.log(x, y, z, "z", "beam")
                    elif z in (1, 2):
                        p.set(x, y, z, "pane")
                    else:
                        p.trap(x, y, z, "east", "bottom", True, "shut")
                else:
                    lit = x == 2 and z in (1, 2) and y in (6, 7)
                    p.set(x, y, z, "shroomlight" if lit else "plank2")
    for z in range(0, 5):
        p.st(0, 8, z, "east", "top", "tst")                       # the second jetty's brackets
        p.set(1, 8, z, "plank2") if not p.has(1, 8, z) else None
    for y in (9, 10):
        for x in range(0, 5):
            for z in range(0, 5):
                if x == 0:
                    if z in (0, 4):
                        p.log(x, y, z, "y", "post")
                    elif z == 2 and y == 9:
                        p.set(x, y, z, "pane")
                    elif y == 10:
                        p.log(x, y, z, "z", "beam")
                    else:
                        p.trap(x, y, z, "east", "bottom", True, "shut")
                else:
                    p.set(x, y, z, "shroomlight" if (x == 1 and z == 2 and y == 9) else "plank2")
    TP.roof_ridge_x(p, 0, 4, 0, 4, 11, gable_ends=(0, 4), overhang=False)
    for k in range(2):                                            # the bargeboard: the west rake in dark oak
        y = 11 + k
        for z in (k, 4 - k):
            p.st(0, y, z, "south" if z < 2 else "north", "bottom", "tst")
    p.log(0, 13, 2, "x", "beam")
    p.sl(0, 14, 2, "tsl")


def store(p):
    """The low RIGGING STORE (x 5..11, z 0..4) between the loft and the lighthouse: cut sandstone, a copper gable roof
    (ridge y 8) - it seals the north side of the hoist well and gives the top view its third roof. Solid inside."""
    for y in range(1, 6):
        for x in range(5, 12):
            for z in range(0, 5):
                corner = x in (5, 11) and z in (0, 4)
                p.set(x, y, z, "dp" if y == 1 else quoin(y) if corner else "cs")
    TP.roof_ridge_x(p, 0, 4, 5, 11, 6, gable_ends=(5, 11), overhang=False)


def gate_house(p):
    """THE LOCK HEAD (x 0..4, z 5..11): the exit a 5 x 8 void through it. Piers at z 5 and z 11 - stripped-dark-oak
    jambs on the face, cut sandstone with prismarine-brick quoins behind, a dark waterline course, a prismarine string
    course at y 5; the dark-oak soffit at y 9 over the opening; the SLUICE LEAF drawn up (y 9..10) across the void -
    dark-oak boards proud at x 0 on a spruce backing at x 1, its stiles in the jambs; the waxed-copper LINTEL at y 11;
    the copper gable roof (ridge along x, y 14) with the WINDLASS drum in its west gable; bracket courses under the
    eaves on the south face."""
    for z in (5, 11):
        for x in range(0, 5):
            for y in range(1, 11):
                if x == 0 and y <= 10:
                    p.log(x, y, z, "y", "post")
                elif y == 1:
                    p.set(x, y, z, "dp")
                elif y == 5 and x == 4:
                    p.set(x, y, z, "pb")
                elif x == 4:
                    p.set(x, y, z, quoin(y))
                elif y in (4, 8):
                    p.set(x, y, z, "dp")
                else:
                    p.set(x, y, z, "cs" if y < 9 else "plank2")
    # the string course on the south face (z 12) at y 5 and the bracket row under the eave
    for x in range(0, 5):
        p.st(x, 5, 12, "north", "top", "pbst")
        p.st(x, 9, 12, "north", "top", "tst")
    for x in (2, 3):
        p.set(x, 7, 11, "pane")
    p.log(1, 7, 11, "y", "post")
    p.log(4, 7, 11, "y", "post")
    for x in range(1, 5):
        p.sl(x, 8, 12, "tsl", "top")
    # the soffit and the floor over the piers
    for x in range(1, 5):
        for z in range(5, 12):
            p.set(x, 9, z, "plank" if z in (6, 10) else "plank2") if z in range(6, 11) else None
    for x in range(1, 5):
        for z in (5, 11):
            p.set(x, 9, z, "plank2") if not p.has(x, 9, z) else None
    # the leaf, raised
    for z in range(6, 11):
        for y in (9, 10):
            p.set(1, y, z, "plank2")
            p.trap(0, y, z, "east", "bottom", True, "shut2")
    for y in (9, 10):
        p.set(1, y, 5, "plank2") if not p.has(1, y, 5) else None
    # the interior over the soffit (sealed)
    for x in range(1, 5):
        for z in range(5, 12):
            for y in (10, 11, 12, 13):
                p.set(x, y, z, "plank2") if not p.has(x, y, z) else None
    # the lintel
    for z in range(6, 11):
        p.set(0, 11, z, "cu")
    p.set(0, 11, 5, "beam", axis="z")
    p.set(0, 11, 11, "beam", axis="z")
    TP.roof_ridge_x(p, 5, 11, 0, 4, 12, gable_ends=(4,), overhang=True, tone="slate")
    # the west gable: a timber frame - a dark-oak collar at y 12, the WINDLASS drum centred between cheeks, spruce
    # boards between the studs, the bargeboard (the slope's end course) in dark oak
    for z in range(5, 12):
        p.log(0, 12, z, "z", "beam") if z not in (7, 8, 9) else None
    p.set(0, 12, 8, "cublk")
    p.st(0, 12, 7, "east", "bottom", "tst")
    p.st(0, 12, 9, "west", "bottom", "tst")
    for z in range(6, 11):
        p.set(0, 13, z, "plank2" if z % 2 else "plank")
    p.log(0, 13, 8, "y", "post")
    p.set(0, 14, 8, "plank")
    for k in range(3):
        for z in (5 + k, 11 - k):
            if k < 3:
                p.st(0, 12 + k, z, "south" if z < 8 else "north", "bottom", "tst")


def well(p):
    """THE HOIST WELL (x 5..9) between the gate house and the slip cover, open to the sky over the slipway: low
    sandstone parapets with a dark coping at z 5 and z 11 (sealing the lane's sides), the two braced TRESTLES at
    x 6..7 and the GANTRY beam across at y 12, the trolley and the cargo net hanging beside the spawn column."""
    for x in range(5, 10):
        for z in (5, 11):
            for y in (1, 2, 3):
                p.set(x, y, z, "dp" if y in (1, 3) else "cs")
            p.set(x, 4, z, "prw")
    p.place(TP.trestle(h=11, w=2, d=2), 7, 0, 4, rot=1)        # x 6..7, z 4..5, braced face west (x 5)
    p.place(TP.trestle(h=11, w=2, d=2), 7, 0, 11, rot=1)       # x 6..7, z 11..12
    p.place(TP.gantry_beam(span=9, trolley_at=4, y=12, lowest=9), GANTRY_X, 0, 4)


def slip_cover(p):
    """THE COVERED SLIP (x 10..16, z 5..11): the Sloop's shed, Chatham's slip cover at the yard's scale. A
    cut-sandstone dado (y 1..3) with a dark waterline course; three bays between stripped-dark-oak posts (x 10, 13,
    16) on the south face - the outer bays weatherboarded with a dark-oak girt and a lit two-light window each, the
    MIDDLE bay OPEN above the dado so the Sloop is seen from the camera; the north and east walls boarded; the copper
    roof (ridge along x, y 12), eaves thrown a cell out on dark-oak brackets at every post, a slab fascia; two LIT
    DORMERS on the south slope over the outer bays (the sail-makers' loft in the roof); the west gable OPEN as a
    TRUSS (tie beam, king post, struts) onto the hoist well."""
    for x in range(10, 17):
        for z in (5, 11):
            for y in range(1, 9):
                post = x in (10, 13, 16)
                if post:
                    p.log(x, y, z, "y", "post")
                elif y == 1:
                    p.set(x, y, z, "dp")
                elif y <= 3:
                    p.set(x, y, z, "cs")
                elif z == 5:
                    p.set(x, y, z, "plank2" if y % 2 else "plank")
    for z in range(5, 12):                                         # the east gable wall
        for y in range(1, 9):
            if not p.has(16, y, z):
                p.set(16, y, z, "dp" if y == 1 else "cs" if y <= 3 else "plank2")
    for x in (11, 12, 14, 15):                                     # the outer bays: girt, boards, a lit window
        p.log(x, 4, 11, "x", "beam")
        for y in (5, 6, 7, 8):
            if y in (6, 7):
                p.set(x, y, 11, "pane")
                p.set(x, y, 10, "shroomlight")
            else:
                p.trap(x, y, 11, "north", "bottom", True, "shut")
                p.set(x, y, 10, "plank2")
        p.sl(x, 8, 12, "tsl", "top")
    TP.roof_ridge_x(p, 5, 11, 10, 16, 9, gable_ends=(16,), overhang=True)
    for x in (10, 13, 16):                                         # brackets under the eave at the posts
        p.st(x, 7, 12, "north", "top", "tst")
    # the open west gable: the truss
    for z in range(6, 11):
        p.log(10, 9, z, "z", "beam")
    p.log(10, 10, 8, "y", "post")
    p.log(10, 11, 8, "y", "post")
    p.st(10, 10, 7, "south", "bottom", "tst")
    p.st(10, 10, 9, "north", "bottom", "tst")
    # the dormers over the outer bays
    for x0 in (11, 14):
        TP.dormer_south(p, x0, x0 + 1, 10, 9)


def sloop(p):
    p.place(TP.sloop(n=6), 10, 0, 8)


def lighthouse(p, shaft_top, galleries):
    lh, top = TP.lighthouse(shaft_top=shaft_top, cx=0, cz=0, galleries=galleries)
    for (x, y, z), v in lh.b.items():
        X, Z = x + LH[0], z + LH[1]
        if 0 <= X < S and 0 <= Z < S:
            p.b[(X, y, Z)] = v
    return top


def hard(p):
    """THE SHIP ON THE HARD along the south face (x 1..9, z 12..15), broadside to the camera, hove down on her
    mangrove-log cradle with three spruce shores leaning on her side; the JIB CRANE swung out from the gate house over
    her deck, its chain down to a cask on the deck; the yard to the east: stacked timber against the slip's wall,
    barrels, a capstan, a bollard at the kerb."""
    p.place(TP.hard_hull(n=7), 2, 0, 13)
    for x in range(0, 10):                                         # the cradle bed under her, solid (no pockets)
        for z in range(13, 17):
            if not p.has(x, 1, z):
                p.set(x, 1, z, "dp")
    for x in range(0, 10):                                         # the quay wall behind her (z 12)
        for y in (1, 2, 3):
            if not p.has(x, y, 12):
                p.set(x, y, 12, "dp" if y != 2 else "cs")
    # the jib crane off the gate house's south-east corner, its chain to a cask on her deck
    for z in range(11, 17):
        p.log(3, 11, z, "z", "beam")
    p.st(3, 10, 12, "south", "top", "tst")
    p.chain(3, 10, 16)
    p.chain(3, 9, 16)
    p.set(3, 8, 16, "barrel", facing="up", open="false")
    # the yard
    p.place(TP.timber_stack(length=3, rows=2), 12, 0, 13)
    p.place(TP.barrel_stack(2), 10, 0, 15)
    p.place(TP.bollard(), 16, 0, 15)
    p.set(14, 1, 15, "dp")
    p.log(14, 2, 15, "y", "beam")
    p.set(14, 3, 15, "cublk")


def fit_out(p):
    """L3: the DRYING MAST beside the lighthouse (x 9, z 2) - the sail loft airs the fleet's striped sail from it -
    so lighthouse and mast read as one spire (mast truck y 24, lamp room y 21..23, cap 27); the sail hangs from a
    yard at y 20 down to the store's roof, its foot bellied one cell south; chain stays; the hull's topmast + pennant.
    The mast foots in the store's roof ridge (the store is solid)."""
    mx, mz = 8, 2
    for y in range(6, 24):
        p.log(mx, y, mz, "y", "beam")
    for x in range(5, 12):                                    # the yard
        if x != mx:
            p.log(x, 20, mz, "x", "post2")
    for x in range(5, 11):                                    # the sail, a flat sheet; the fleet's two stripes
        for y in range(13, 20):
            p.set(x, y, mz + 1, "stripe" if x in (6, 10) else "sail")
    for x in (5, 11):                                         # the lifts (chain) from the yard arms to the roof
        for y in range(10, 20):
            if not p.has(x, y, mz):
                p.chain(x, y, mz)
    p.set(mx, 24, mz, "rope2")
    p.set(mx, 25, mz, "end_rod", facing="up")
    # the hull's topmast, a lashed spar at its head
    for y in range(11, 15):
        p.log(5, y, 14, "y", "post2")
    p.set(5, 15, 14, "rope2")


def draw(level=1):
    p = TPart()
    ground(p)
    loft(p)
    store(p)
    slip_cover(p)
    sloop(p)
    gate_house(p)
    well(p)
    hard(p)
    lighthouse(p, 11, (8,))
    if level >= 3:
        lighthouse(p, 19, (8, 15))
        fit_out(p)
    clip(p)
    return p


# ============================================================================================ output + checks
FORBIDDEN = ("lava", "water", "sand", "red_sand", "gravel", "_concrete_powder", "anvil", "redstone", "repeater",
             "comparator", "observer", "piston", "lever", "_button", "_pressure_plate", "tnt", "fire", "campfire",
             "scaffolding", "pointed_dripstone", "_door", "_carpet", "candle", "sea_pickle", "kelp", "seagrass",
             "lightning_rod", "_sapling", "grass_block", "tuff_bricks", "polished_tuff", "nether_brick", "terracotta")
FINE = ("_stairs", "_slab", "_wall", "_fence", "_pane", "iron_bars", "_trapdoor", "_button", "chain", "lantern",
        "candle", "end_rod", "lightning_rod", "_door", "_carpet", "_banner", "mangrove_roots")


def to_structure(p, label):
    p.b = {k: v for k, v in p.b.items() if v[0] not in ("air", "cave_air")}
    top = max(y for (_, y, _) in p.b)
    r = gs._SunRig(S, top + 1, S)
    for (x, y, z), (n, pr) in p.b.items():
        r.set(x, y, z, n, **pr)
    r.settle()
    r.check_support(label)
    s = gs.Structure(S, top + 1, S)
    for (x, y, z), (n, pr) in sorted(r.b.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
        s.set(x, y, z, n, **pr)
    return s


def blocks_of(s):
    return {pos: (s.palette[i][0], dict(s.palette[i][1])) for pos, i in s.blocks.items()
            if s.palette[i][0] not in ("air", "cave_air")}


def is_fine(n):
    return n.endswith(FINE) or n in FINE


def stats(s):
    """(blocks, fine %, visible blocks, fine % of the visible) - visible = a face the RTS camera sees (up, south, west)
    is open."""
    b = blocks_of(s)
    vis = [p for p in b if any((p[0] + dx, p[1] + dy, p[2] + dz) not in b or is_fine(b.get(
        (p[0] + dx, p[1] + dy, p[2] + dz), ("air",))[0]) for dx, dy, dz in ((0, 1, 0), (0, 0, 1), (-1, 0, 0)))]
    fine = [p for p in b if is_fine(b[p][0])]
    vfine = [p for p in vis if is_fine(b[p][0])]
    return len(b), 100.0 * len(fine) / len(b), len(vis), 100.0 * len(vfine) / max(1, len(vis))


def floating(b):
    return [p for p in b if p[1] > 0 and not any((p[0] + dx, p[1] + dy, p[2] + dz) in b for dx, dy, dz in
                                                  ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)))]


def check(s, path, tag, hmax):
    import audit_structure_pockets as ap
    b = blocks_of(s)
    assert (s.size[0], s.size[2]) == (S, S) and s.size[1] <= hmax, s.size
    assert all((x, 0, z) in b for x in range(S) for z in range(S)), "a hole in the foundation"
    names = {n for n, _ in b.values()}
    bad = sorted(n for n in names if any(n == f or n.endswith(f) for f in FORBIDDEN)
                 or "nether_brick" in n or "terracotta" in n)
    assert not bad, "forbidden blocks %s" % bad
    assert not [n for n in names if "copper" in n and not n.startswith("waxed_")], "unwaxed copper"
    assert not [n for n in names if "coral" in n and not n.startswith("dead_")], "live coral"
    shut = [p for p in LANE if p in b]
    assert not shut, "%s: the exit lane is narrowed at %s" % (tag, shut[:10])
    assert all((GANTRY_X, 12, z) in b for z in range(4, 13)), "the gantry beam is broken"
    col = [y for y in range(1, s.size[1]) if (SPAWN[0], y, SPAWN[1]) in b]
    assert not col, "%s: the spawn column over %s is blocked at y %s" % (tag, SPAWN, col)
    lone = floating(b)
    assert not lone, "%s: floating %s" % (tag, lone[:8])
    one_way, ground_p, roof, rooms, note = ap.audit(tag, path)
    assert not one_way and not ground_p and "TRAPPED" not in note, "%s pockets %s %s %s" % (
        tag, one_way[:8], ground_p[:8], note)
    pad = ap.Pad(ap.load_nbt(path))
    start = (SPAWN[0], 1, SPAWN[1])
    assert pad.walkable(*start), "the spawn cell is not standable"
    seen, stack, out = {start}, [start], False
    while stack and not out:
        c = stack.pop()
        if c[0] < 0:
            out = True
        for n in ap.neighbours(pad, *c):
            if n not in seen and -3 <= n[0] <= pad.sx + 2 and -3 <= n[2] <= pad.sz + 2 and 1 <= n[1] <= pad.sy + 1:
                seen.add(n)
                stack.append(n)
    assert out, "a unit spawned at %s can't walk out west" % (start,)
    n, fpc, nv, vfpc = stats(s)
    return "%s %dx%dx%d, %d blocks, %.0f%% fine (%.0f%% of %d visible): lane clear, walks out west, no pockets " \
           "(%d roof-only)%s" % (tag, s.size[0], s.size[1], s.size[2], n, fpc, vfpc, nv, len(roof), note)


def build_all(out):
    os.makedirs(out, exist_ok=True)
    s1 = to_structure(draw(1), "tide_l1")
    s3 = to_structure(draw(3), "tide_l3")
    b1, b3 = blocks_of(s1), blocks_of(s3)
    lost = [p for p in b1 if p not in b3]
    assert not lost, "L3 drops L1 cells: %s" % lost[:10]
    p1, p3 = os.path.join(out, "tide_l1.nbt"), os.path.join(out, "tide_l3.nbt")
    s1.write(p1)
    s3.write(p3)
    print(check(s1, p1, "tide_l1", H1))
    print(check(s3, p3, "tide_l3", H3))


if __name__ == "__main__":
    build_all(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                                                                  "design", "vehicle_labs"))
