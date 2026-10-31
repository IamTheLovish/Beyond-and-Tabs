#!/usr/bin/env python3
"""The Stormcallers VEHICLE LAB - THE THUNDER ARSENAL - at L1 and L3, the r12 method (Oct 31 2026). Here the Rocket
Battery (a hwacha fire-arrow cart) and the later Stormcallers vehicles are built in a walled yard and rolled out of the
west gate.

Writes design/vehicle_labs/storm_l1.nbt and _l3.nbt only - NOT into the game's structure trees (the game does not load
them; wiring comes after lovish approves the look). Tests (written first, 12 red on the old lab): tools/test_storm_lab.py.
The old lab (4/10 from round 1's critic, Oct 28: "a tidy toy: one roof slab on a 17x17 box") is kept as
design/vehicle_labs/storm_l3_round1.nbt and in git history (this file before Oct 31).

THE REFERENCE, STUDIED (ONE picture: ref08, design/refs/vehicle_labs/ref08_crag_compound_from_above.png; the designer
pass, itachi2-api run 20261030-234157-storm-design)
  - a walled compound seen from high above, as our camera sees it: the ranges one storey, their hip roofs about half
    the visible height; a PAVILION at the wall angles, two-tier, taller than the ranges and lower than the keep;
  - ONE KEEP, about a quarter of the compound's width and twice the ranges' height, in stacked hip tiers (light roof
    fields, dark eave bands, dark timber) - the landmark; ref42's pagoda gives its stacked eaves and finial;
  - the GATE a narrow dark passage in the front range, the darkest spot; the COURT about a quarter of the area, warm
    paving, open to the sky; conifers breaking the outline; every mass a different height.
  Parts only: ref42 (the keep's eaves), ref27 (the open armillary crown at L3).

THE MASSING (A: the designer's pick and both blind critics' first, eyes 41166006117, critics 20261031-001159 /
-001205 - A 37 / 39 of 60, B 37 / 39 (close; B's keep better), C 33 / 38, the old lab 21 / 22) - revised on their
shared faults: the keep "a bump" with equal tiers and a ring floating on a pole -> taller (point 19, lead 7), lips
7 / 5 / 5 on bodies 5 / 3 / 3, the crown standing on the point; the gate "lighter than its wall", no road -> a dark
passage and a dark road from the gate across the court to the keep's door, two stacked gate eaves; the court "a strip"
-> the north and south ranges pulled back (the court 8 x 9 between the gate and the keep).
Plot 17 x 17 (x 0..16 west -> east, z 0..16 north -> south; the RTS camera looks north-east from the south-west and
sees the roofs, the WEST face x 0 - the front - and the SOUTH face z 16).
  gatehouse   x 0..1, z 4..12, the 5 x 8 gate (z 6..10), a lower eave (lip x 0..2 at 9), the upper storey (x 1, y 10),
              the upper hip (lip x 0..2, z 5..11 at 11) to its ridge at 12
  court       x 2..9, z 4..12 open to the sky, mud-brick paving, a blackstone road z 7..9 from the gate to the keep
  keep        x 10..16, z 5..11, centre (13, 8): body 5 x 5 (y 1..6), eave 7 x 7 (lip 7, a ring at 8), body 3 x 3
              (9..11), eave 5 x 5 (12), body 3 x 3 (13..16), the top hip 5 x 5 (17) to its point (19); L3 the crown
  N range     x 5..10, z 0..2 (lip x 4..11, z 0..3 at 7, ridge 8); NW pavilion x 0..3, z 0..2 (eaves 6 and 9, ridge 10)
  S range     x 4..12, z 13..15, the KEPT colonnade on z 15 (lip x 3..13, z 12..16 at 5, ridge 7); SW pavilion
              x 0..2, z 13..15 (lip 5, ridge 6); SE pavilion x 13..15, z 13..15 (eaves 6 and 9, point 11)
  garden      x 10..16, z 0..3: two spruces breaking the north-east outline, a low wall on z 4

THE DETAIL GRAMMAR (round 1 - one grammar on every camera face): a battered first course (stone-brick stairs), a fret
course (chiseled stone bricks between top stairs) at y 2, then lacquer POSTS every other cell with white paper panels
between them (light behind: a shroomlight one cell in), an inverted-stair lintel between the posts on the top course;
flared hip roofs in prismarine brick (the field) with upturned dark-prismarine corner tips and a dark-prismarine
underside one deep under every lip (the eave's shadow band).
Palette by look (camera_colours.json): prismarine-brick field 0.54-0.62 / sat 0.32 / hue 160 (the old oxidized copper,
sat 0.43, "toy mint", is gone); POLISHED GRANITE lacquer posts (texture hue 14, sat 0.38 - a muted red-orange;
stripped mangrove reads sat 0.55-0.57 on camera, team-colour territory); spruce timber (dark oak reads sat 0.54-0.60 =
loud); stone-brick plinth; mud-brick court (0.53, warm); a polished-blackstone-brick road and gate passage; no gold.

ANCHORS (structure-local x, y, z; in game these become client-drawn moving parts later):
  gate        (0..1, 1..8, 6..10) the passage; the gate leaves fold against z 5 / z 11
  spawn cell  (8, 1, 8) on the road in the court; the column above it is air to the sky
  storm vane  (13, 22, 8) the crown's core (a sea lantern) - arcs drawn round its rings at L3

EXIT LANE: x 0..9, z 6..10, y 1..8 clear, straight out west.

THE GROWTH (one footprint; L3 only adds or replaces): L1 (15 tall) - everything but the keep's third body, its top
hip and the crown (a temporary hip caps the second eave: 12 -> point 14); L3 (26 tall) - the keep's third body, the
top hip, the crown.

Usage: python3 tools/lab_vehicle_storm.py [OUT_DIR]   (default design/vehicle_labs: storm_l1.nbt, storm_l3.nbt)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import storm_lab_parts as P  # noqa: E402

SX = SZ = 17
H1, H3 = 16, 26
LANE_X, LANE_Z, LANE_TOP = range(0, 10), range(6, 11), 8
SPAWN = (8, 8)
KEEP = (13, 8)
DRY = dict(waterlogged="false")
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
INWARD = {"west": (1, 0), "south": (0, -1)}

FIELD_ST, FIELD, FIELD_SL = "prismarine_brick_stairs", "prismarine_bricks", "prismarine_brick_slab"
LIP_ST = "dark_prismarine_stairs"            # round 2: the lip ring = the dark eave line
UNDER = "dark_prismarine_slab"
TIMBER = "spruce_planks"
TIMBER_ST = "spruce_stairs"
POST = "polished_granite"
PANEL = "white_stained_glass"
LIGHT = "shroomlight"
SB, SB_ST, CSB = "stone_bricks", "stone_brick_stairs", "chiseled_stone_bricks"
MOSS = "mossy_stone_bricks"
PAVE = "stone_bricks"
COURT = "mud_bricks"
ROAD = "polished_andesite"                # round 2 (was polished blackstone bricks, half width)
KERB = "stone_bricks"
THRESHOLD = "obsidian"                   # the gate's floor: the darkest spot (designer: gate interior 0.05-0.18)
PASSAGE = "polished_blackstone_bricks"
PASSAGE_CEIL = "deepslate_tiles"
RING = "prismarine_wall"
RING_FLAT = "dark_prismarine_slab"
CORE = "sea_lantern"
POST_UP = dict(up="true", north="none", south="none", east="none", west="none", waterlogged="false")


def guard(x, y, z):
    return (x in LANE_X and z in LANE_Z and 1 <= y <= LANE_TOP) or ((x, z) == SPAWN and y >= 1)


# ================================================================================================ parts
def core(c, x1, x2, z1, z2, y1, y2):
    """A solid mass (no rooms, no pockets): stone bricks to y 2, spruce timber above; drawn only into free cells."""
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            for y in range(y1, y2 + 1):
                if c.free(x, y, z):
                    c.put(x, y, z, SB if y <= 2 else TIMBER)


def face_cell(face, plane, u, y):
    return (plane, y, u) if face == "west" else (u, y, plane)


def dress(c, face, plane, u0, u1, y0, y1, plinth=True):
    """One camera face (u0..u1 on the plane, courses y0..y1): from the ground a battered course (stairs) and the fret
    (chiseled bricks between top stairs); then posts every other cell (and at both ends) with paper panels between,
    a shroomlight one cell in behind each panel, an inverted-stair lintel between the posts on the top course."""
    inward = OPP[face]
    dx, dz = INWARD[face]
    for u in range(u0, u1 + 1):
        post = (u - u0) % 2 == 0 or u == u1
        for y in range(y0, y1 + 1):
            q = face_cell(face, plane, u, y)
            if plinth and y == y0 and y0 == 1:
                c.st(*q, inward, "bottom", SB_ST)
            elif plinth and y == y0 + 1 and y0 == 1:
                if post:
                    c.put(*q, MOSS if P.hsh(q[0], q[2]) % 3 == 0 else CSB)
                else:
                    c.st(*q, inward, "top", SB_ST)
            elif post:
                c.put(*q, POST)
            elif y == y1:
                c.st(*q, inward, "top", TIMBER_ST)
            else:
                c.put(*q, PANEL)
                b = (q[0] + dx, q[1], q[2] + dz)
                if not guard(*b):
                    c.put(*b, LIGHT)


def hip(c, x1, x2, z1, z2, y, rings=99, dark_lip=None):
    """A flared hip roof, its lip the ring [x1..x2] x [z1..z2] at y (clipped to the plot; drawn only into free cells):
    on a pent eave (rings <= 2) the lip ring in dark-prismarine stairs - the eave line between the tiers (round 2;
    round 1's upturned corner tips, full-looking cubes, read as "studs / teeth / battlements" to both critics, and "no
    dark eave line at RTS"); every ring in one higher in prismarine-brick stairs, a
    dark-prismarine underside one deep under the lip (the eave's shadow band), a ridge line / peaked
    pair / point at the top (a ridge line in slabs, so it never reads as a run of full blocks); `rings` stops it early (a pent eave round a body rising through it). Inside each ring a
    hidden timber fill. Returns the top y."""
    def put(x, yy, z, name, **p):
        if c.free(x, yy, z):
            c.put(x, yy, z, name, **p)

    k, yy, top = 0, y, y
    while k < rings:
        a1, a2, b1, b2 = x1 + k, x2 - k, z1 + k, z2 - k
        if a1 > a2 or b1 > b2:
            break
        w, d = a2 - a1 + 1, b2 - b1 + 1
        top = yy
        if min(w, d) == 1:
            for x in range(a1, a2 + 1):
                for z in range(b1, b2 + 1):
                    if w * d == 1:
                        put(x, yy, z, FIELD)
                    else:
                        c.put(x, yy - 1, z, TIMBER) if c.free(x, yy - 1, z) else None
                        put(x, yy, z, FIELD_SL, type="bottom", **DRY)
            break
        if min(w, d) == 2:
            for x in range(a1, a2 + 1):
                for z in range(b1, b2 + 1):
                    f = ("north" if z == b1 else "south") if w >= d else ("west" if x == a1 else "east")
                    put(x, yy, z, FIELD_ST, facing=OPP[f], half="bottom", shape="straight", **DRY)
            break
        st = LIP_ST if k == 0 and (rings <= 2 if dark_lip is None else dark_lip) else FIELD_ST
        for (x, z, f) in P.rect_ring(a1, a2, b1, b2):
            if f is None:
                fz = "south" if z == b1 else "north"
                put(x, yy, z, st, facing=fz, half="bottom", shape="straight", **DRY)
            else:
                put(x, yy, z, st, facing=OPP[f], half="bottom", shape="straight", **DRY)
            if k == 0 and c.free(x, yy - 1, z):
                c.slab(x, yy - 1, z, UNDER, "top")
        for x in range(a1 + 1, a2):
            for z in range(b1 + 1, b2):
                put(x, yy, z, TIMBER)
        k += 1
        yy += 1
    return top


def crown(c, cx, cz, y0):
    """L3: the armillary (ref27) standing on the keep's point: ONE vertical ring (radius 2, the x-y plane) and a wide
    flat ring (radius 3, 7 across - wider than the 5 x 5 top eave) at the centre height, round a sea-lantern core hung
    from the ring's top (the one glow, storm-white). Round 2: round 1's two crossed rings read as "an X in a box" from
    the RTS camera and its soul lantern showed no glow at dusk (both critics)."""
    cy = y0 + 2
    ring = [(-2, -1), (-2, 0), (-2, 1), (2, -1), (2, 0), (2, 1), (-1, -2), (0, -2), (1, -2), (-1, 2), (0, 2), (1, 2)]
    for (u, v) in ring:
        c.put(cx + u, cy + v, cz, RING, **POST_UP)
    wide = [(-3, -1), (-3, 0), (-3, 1), (3, -1), (3, 0), (3, 1), (-1, -3), (0, -3), (1, -3), (-1, 3), (0, 3), (1, 3),
            (-2, -2), (2, -2), (-2, 2), (2, 2)]
    for (u, w) in wide:
        c.put(cx + u, cy, cz + w, RING, **POST_UP)
    for (u, w) in ((-2, -1), (-2, 1), (2, -1), (2, 1)):
        c.put(cx + u, cy, cz + w, RING, **POST_UP)
    c.put(cx, cy, cz, CORE)
    c.chain(cx, cy + 1, cz)


# ================================================================================================ the masses
def ground(c):
    """Layer 0: stone-brick paving; the court in mud brick; the road (blackstone bricks) z 7..9 from the gate's
    threshold to the keep's door; the gate's floor (x 0..3, z 5..11) obsidian; grass in the garden. Round 2: the road
    the lane's full width (z 6..10, polished andesite between stone-brick kerbs) from the gate to the keep's door, the
    obsidian only under the gate's eaves (x 0..2) - round 1's half-width dark road read as "a half-width dark field, not a
    lane", the court "split orange / black", the black threshold outside the gate as "a pit" (critics)."""
    for x in range(SX):
        for z in range(SZ):
            name = PAVE
            if 2 <= x <= 9 and 4 <= z <= 12:
                name = COURT
            if x >= 10 and z <= 3:
                name = "grass_block"
            c.put(x, 0, z, name)
    for x in range(3, 12):
        for z in range(6, 11):
            c.put(x, 0, z, ROAD)
        for z in (5, 11):
            c.put(x, 0, z, KERB)
    for x in range(0, 3):
        for z in range(5, 12):
            c.put(x, 0, z, THRESHOLD)


def gatehouse(c):
    """THE GATE (x 0..1, z 4..12): granite jambs round the 5 x 8 passage (blackstone-brick walls inside, a deepslate
    ceiling), paper panels on its west face, the LOWER EAVE (lip x 0..2, z 4..12 at 9 - its west row is the gate's
    head), the upper storey on x 1 (y 10) and the UPPER HIP (lip x 0..2, z 5..11 at 11) to its ridge at 12."""
    core(c, 0, 1, 4, 5, 1, 8)
    core(c, 0, 1, 11, 12, 1, 8)
    for z in (5, 11):
        for y in range(1, 9):
            c.put(1, y, z, PASSAGE)
            c.put(0, y, z, POST if y > 1 else CSB)
    for z in (4, 12):
        c.st(0, 1, z, "east", "bottom", SB_ST)
        for y in range(2, 9):
            c.put(0, y, z, POST if y in (2, 8) else PANEL)
    for z in range(6, 11):
        c.put(1, 9, z, PASSAGE_CEIL)
    hip(c, 0, 2, 4, 12, 9, rings=1)
    for z in range(5, 12):
        c.put(1, 10, z, POST if z % 2 else PANEL)
    hip(c, 0, 2, 5, 11, 11)


def keep(c, level):
    """THE KEEP (centre (13, 8)): body 5 x 5 (y 1..6) under a 7 x 7 eave (lip 7, a second ring at 8); body 3 x 3
    (9..11) under a second 7 x 7 eave (lip 12, a ring at 13 - round 2: was 5 x 5, "the top two tiers equal", "lips
    narrow next to ref42"); at L3 body 3 x 3 (14..16), the top hip (lip 5 x 5 at 17, point 19) and the crown - the
    eaves 7 / 7 / 5; at L1 a hip on the second eave (12 -> point 15). A dark door niche on the road's axis."""
    core(c, 11, 15, 6, 10, 1, 6)
    dress(c, "west", 11, 6, 10, 1, 6)
    dress(c, "south", 10, 11, 15, 1, 6)
    for z in (7, 8, 9):
        for y in (1, 2, 3):
            c.put(11, y, z, "air")
            c.put(12, y, z, PASSAGE)
    c.st(11, 4, 8, "east", "top", TIMBER_ST)
    hip(c, 10, 16, 5, 11, 7, rings=2)
    core(c, 12, 14, 7, 9, 9, 11)
    quiet(c, 12, 14, 7, 9, 9, 11)
    if level < 3:
        hip(c, 10, 16, 5, 11, 12)
        return
    hip(c, 10, 16, 5, 11, 12, rings=2)
    eave_band(c)
    core(c, 12, 14, 7, 9, 14, 16)
    quiet(c, 12, 14, 7, 9, 14, 16)
    top = hip(c, 11, 15, 6, 10, 17)
    crown(c, KEEP[0], KEEP[1], top + 1)


def quiet(c, x1, x2, z1, z2, y1, y2):
    """Round 2: a keep's upper body (3 x 3) as a QUIET storey - dark spruce-log corner posts, spruce-plank walls, ONE
    lit paper window in the middle of each camera face under a stair lintel (round 1's post / panel / post on a 3-wide
    face read as "candy stripes", "a striped lighthouse", both critics)."""
    for face, plane, u0, u1 in (("west", x1, z1, z2), ("south", z2, x1, x2)):
        dx, dz = INWARD[face]
        for u in range(u0, u1 + 1):
            for y in range(y1, y2 + 1):
                q = face_cell(face, plane, u, y)
                if u in (u0, u1):
                    c.log(*q, "spruce_log")
                elif y == y2:
                    c.st(*q, OPP[face], "top", TIMBER_ST)
                elif y == y2 - 1 or (y2 - y1 >= 3 and y == y2 - 2):
                    c.put(*q, PANEL)
                    c.put(q[0] + dx, q[1], q[2] + dz, LIGHT)
                else:
                    c.put(*q, TIMBER)


def eave_band(c):
    """Round 2, dusk: a warm band under the keep's second eave (rubric 9: "a warm under-eave band on the keep plus the
    crown core") - lanterns on short chains from its underside every other cell along the camera sides (x 10, z 11)."""
    for (x, z) in [(10, z) for z in range(5, 12, 2)] + [(x, 11) for x in range(12, 17, 2)]:
        c.chain(x, 10, z)
        c.lantern(x, 9, z, hanging=True)


def north(c):
    """The NW pavilion (x 0..3, z 0..2: eaves 6 and 9, ridge 10) and the north range (x 5..10, z 0..2, ridge 8)."""
    core(c, 0, 3, 0, 2, 1, 5)
    dress(c, "west", 0, 0, 2, 1, 5)
    dress(c, "south", 2, 0, 3, 1, 5)
    hip(c, 0, 4, 0, 3, 6, rings=1)
    core(c, 1, 3, 1, 2, 7, 8)
    hip(c, 0, 4, 0, 3, 9)
    core(c, 5, 10, 0, 2, 1, 6)
    dress(c, "south", 2, 5, 10, 1, 6)
    hip(c, 4, 11, 0, 3, 7)


def south(c):
    """The SW pavilion (x 0..2, z 13..15, ridge 6), the SE pavilion (x 13..15, z 13..15: eaves 6 and 9, point 11),
    the south range (x 4..12, z 13..15) with the KEPT colonnade on its south face."""
    core(c, 0, 2, 13, 15, 1, 4)
    dress(c, "west", 0, 13, 15, 1, 4)
    dress(c, "south", 15, 0, 2, 1, 4)
    hip(c, 0, 3, 12, 16, 5)
    core(c, 13, 15, 13, 15, 1, 5)
    dress(c, "south", 15, 13, 15, 1, 5)
    hip(c, 12, 16, 12, 16, 6, rings=1)
    core(c, 13, 15, 13, 15, 7, 8)
    dress(c, "south", 15, 13, 15, 7, 8, plinth=False)
    hip(c, 12, 16, 12, 16, 9)
    core(c, 4, 12, 13, 15, 1, 4)
    dress(c, "south", 15, 4, 12, 1, 4)
    hip(c, 3, 13, 12, 16, 5)


def yard(c):
    """Round 2: the WORKSHOP in the court, off the lane (both critics: "nothing in the court says vehicle lab"): a
    half-built Rocket Battery on the north strip (x 3..7, z 3..5) - spruce wheels, a stripped-spruce axle and frame, a
    granite-cheeked rack with fire-arrow tubes (lightning rods) aimed west - and on the south strip (z 11) barrels of
    tubes, a rack of arrows and a spare wheel. Nothing full above y 1 (the court stays open to the sky)."""
    for x in (4, 6):
        for z in (3, 5):
            c.put(x, 1, z, "spruce_trapdoor", facing="north" if z == 3 else "south", half="bottom", open="true",
                  powered="false", **DRY)
    for x in range(3, 8):
        c.log(x, 1, 4, "stripped_spruce_log", axis="x")
    for x in (4, 5, 6):
        c.slab(x, 2, 4, "spruce_slab", "bottom")
    for z in (3, 5):
        c.st(5, 2, z, "west", "bottom", "granite_stairs")
        c.st(6, 2, z, "west", "bottom", "granite_stairs")
    for x in (5, 6):
        c.rod(x, 3, 4, "west")
    c.put(2, 1, 4, "spruce_fence")
    for x in (3, 5, 8):
        c.put(x, 1, 11, "barrel", facing="up", open="false")
        c.rod(x, 2, 11)
    c.put(4, 1, 11, "spruce_fence")
    c.put(4, 2, 11, "spruce_fence")
    c.rod(4, 3, 11, "west")
    c.put(6, 1, 11, "spruce_trapdoor", facing="south", half="bottom", open="true", powered="false", **DRY)


def garden(c):
    """The north-east garden: two spruces breaking the outline at the plot's edge, a low wall on z 4 (x 10..16)."""
    for x in range(10, 17):
        if x % 2:
            c.st(x, 1, 4, "north", "bottom", SB_ST)
        else:
            c.put(x, 1, 4, CSB)
    P.spruce_tree(c, 15, 1, 1, h=9)
    P.spruce_tree(c, 11, 1, 2, h=7)


# ================================================================================================ build
def draw(c, level):
    ground(c)
    keep(c, level)
    gatehouse(c)
    north(c)
    south(c)
    yard(c)
    garden(c)


def build(level):
    c = P.Canvas(SX, H3, SZ, guard=guard)
    draw(c, level)
    c.leaves()
    c.settle()
    c.check_support("storm L%d" % level)
    c.size = (SX, max(p[1] for p in c.b) + 1, SZ)
    return c


def floating(b):
    return [q for q in b if q[1] > 0 and not any((q[0] + dx, q[1] + dy, q[2] + dz) in b for dx, dy, dz in (
        (1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)))]


def check(c1, c3):
    n1 = {p: v[0] for p, v in c1.b.items()}
    n3 = {p: v[0] for p, v in c3.b.items()}
    assert all((x, 0, z) in n1 for x in range(SX) for z in range(SZ)), "a hole in layer 0"
    assert c1.size[1] <= H1 and c3.size[1] <= H3, (c1.size, c3.size)
    lost = [p for p in n1 if p not in n3]
    assert not lost, "L3 drops L1 cells: %s" % lost[:8]
    for tag, c, n in (("L1", c1, n1), ("L3", c3, n3)):
        h = c.size[1]
        busy = [(x, y, z) for x in LANE_X for z in LANE_Z for y in range(1, LANE_TOP + 1) if (x, y, z) in n]
        assert not busy, "%s: lane blocked %s" % (tag, busy[:6])
        col = [y for y in range(1, h) if (SPAWN[0], y, SPAWN[1]) in n]
        assert not col, "%s: spawn column blocked at %s" % (tag, col)
        assert not floating(n), "%s: floating %s" % (tag, floating(n)[:8])
        bad = [v for v in n.values() if v in ("tuff_bricks", "polished_tuff", "water", "lava", "fire", "sand", "gravel",
                                               "calcite", "quartz_block", "dark_oak_planks")
               or ("copper" in v and not v.startswith("waxed_"))]
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
        print("%s: %dx%dx%d, %d blocks; pockets one-way %s ground %s, roof-only %d%s" % (
            path, c.size[0], c.size[1], c.size[2], len(c.b), one_way[:6], grnd[:6], len(roof), note))
        assert not one_way and not grnd and "TRAPPED" not in note, "pockets"


if __name__ == "__main__":
    main()
