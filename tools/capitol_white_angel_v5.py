#!/usr/bin/env python3
"""The White Angel (Sunforged) capitol v5: THE CASTLE-CATHEDRAL ON THE CRAG, finished - the v4 refinement list done.

lovish's verdicts: v4 "wow I really like the picture on the left" (Oct 20, 21:21), the gothic spired castle-on-a-hill
is the "right direction but better"; capitols must be far more intricate and bigger, growing visibly at every level,
cohesive, realistic, articulated. The refinements he asked for after v4 (claude/faction-building-references.md), all
done here, in his words' order:
  1. THE HILL IS A WILD ROCK CRAG, not stepped terraces: two sheer cliff bands with narrow broken ledges between them,
     rock fins running down the faces, overhanging lips with roots, a talus of fallen stone at the foot, the east side
     one dark deepslate precipice; grass only where a ledge is wide enough to hold soil.
  2. WARMER STONE AND RICHER GABLE ORNAMENT, the Emberheart technique palette (SixWings' castle, studied for its
     techniques, never copied): spires and cones in warm BRICK (the "copper" of the picture), the big roofs and needles
     in DEEPSLATE, the walls in the White Angel's warm light grey (polished andesite, stone zones, stone-brick quoins),
     mossy stone bricks low down; the gables carry crocketed rakes, blind arcades, a rose, finials.
  3. MORE PINNACLES: one on every buttress pier, on every tower collar, riding every rake, ringing the drum.
  4. THE WALL BAY RHYTHM (his cathedral standard): every bay framed by a buttress pier ending in a pinnacle; each bay a
     tall pointed lancet with a mullion and stained glass under a small crocketed gable (wimperg); TWO storeys of bays
     with a string course between; the CENTRAL TOWER with a TRACERY LANTERN stage and the crown above it.
  5. TOWERS THAT LOOK REAL (tools/gothic_towers.py): taper, battered feet, quoins, weathering, functional windows that
     climb with the stair, corbelled crowns, eaves that overhang, lucarnes in the cones.
  6. LIGHT AS DETAIL: lit windows on every storey, lanterns on every route - the great stair, the bridge, the terrace,
     the gate, the wall-walks.
  7. CLEAR GROWTH L1 -> L6 (below): every level visibly bigger and richer from the RTS camera.
  The crown stays: the dark ribbed dome on its drum of pinnacles, the clean plinth, the drawn marble angel on it
  (tools/gen_angel_statue.py; BuildingParts.STATUES) - STATUE_ANCHOR re-verified at every level by check().

THE MASSING (coordinates from the reservation's centre; +z = south = toward the RTS camera; y = 0 the ground course)
  - THE CRAG: summit plateau at PL (y 36) ~30 across, its edge wandering; cliff band 1 (13 down) to a ledge, cliff
    band 2 (11 down) to a second ledge, a talus slope to the grass apron; rock spurs (LOBES) rise under every building
    that stands off the plateau so walls grow out of rock, never stand on stilts.
  - THE HALL (the nave): 15 x 21, 20 to the eaves, the gable front facing south over the terrace with a rose of
    radius 5 over a great portal; two storeys of lancet bays on both flanks in the rhythm above; a steep deepslate roof
    with lucarnes, ridge cresting and gold finials.
  - THE CROSSING TOWER (the crown): rising out of the hall's back half: a square shaft with quoins and bartizans, a
    TRACERY LANTERN stage (an octagon of tall glazed lancets between pinnacled corner piers), the drum ringed with
    eight pinnacles, the dark ribbed dome, the plinth and the angel - the capitol's highest point, a little behind
    the gable as the picture's tallest spire is.
  - TOWERS: T1 and T2 either side of the gable (octagonal, slim brick needles with lucarnes, T2 with a jettied timber
    gallery under its needle), the two FRONT CHAPELS forward and lower under them, T4 (a small needle behind on the
    left), T3 (round, a dark timber collar) on the west wall, the west curtain wall stepping down the slope with a
    wall turret, the dark east cliff wall with T5 (a long slender shaft) standing on its edge.
  - THE ROUTE: the terrace with its balustrade and flower beds; the GREAT STAIR cut down the south face to a
    landing, an ARCHED BRIDGE over the gully, a second flight down to the GATEHOUSE (two round towers flanking the
    sealed portal into the rock) over the spawn apron - lanterns all the way (Emberheart's bridge & staircases
    technique); the timber cottages of the town at the castle's foot (L5).

THE GROWTH (one origin, 59 x 104 x 59 at every level; a level only adds or replaces blocks):
  - L1  the crag, the apron and the gatehouse, the crossing tower with its lantern, dome and the angel on its plinth
  - L2  the hall: gable, rose, portal, both flanks' two storeys of bays, the roof; the terrace
  - L3  T1 and T2, the two front chapels, the great stair and its landing (lanterns on the route)
  - L4  the west: T4, T3 on the west wall, the curtain wall and its turret stepping down the slope, the bridge
  - L5  the east: the dark cliff wall with T5, the barred arch in the rock, the cottages at the foot
  - L6  the crown: flying buttresses on both flanks, the pinnacle forest, banners, gold on the dome's ribs, more lanterns

RULES (check()): fixed box every level; each level keeps every non-plant cell of the one before; no forbidden blocks;
nothing pops off; the spawn cell on the apron is open on a floor; the statue's anchor is air on the plinth with open
sky above and nothing within the statue's reach; the pocket audit (tools/audit_structure_pockets.py) finds no one-way
or ground pockets. Structures export as `capitol_white_angel` (L1) and `capitol_white_angel_l2` .. `_l6`;
tools/check_parts.py keeps BuildingParts.STATUES' anchor equal to STATUE_ANCHOR here.

Usage: python3 tools/capitol_white_angel_v5.py OUT_DIR   -> OUT_DIR/capitol_white_angel.nbt, _l2.nbt .. _l6.nbt
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs                                                 # noqa: E402
import gothic_parts as GP                                                   # noqa: E402
import gothic_towers as GT                                                  # noqa: E402
from gothic_parts import Part, palette, hsh, toward, VEC, OPP, LEAF          # noqa: E402

R = 29                      # 59 x 59
H = 104
LEVELS = 6
PL = 36                     # the summit (the castle floor)
PEAK = (0, -3)
# the Emberheart palette direction over the White Angel's warm light grey: brick cones, deepslate roofs and needles
PAL = palette("red", "warm")
PLANTS = {"vine", "orange_tulip", "red_tulip", "poppy", "cornflower", "azure_bluet", "oxeye_daisy", "grass",
          "fern", "hanging_roots", "moss_carpet"}
SPAWN = (0, 1, 24)          # on the apron, open to the sky
APRON = (-6, 6, 17, 29)     # x0 x1 z0 z1, floor at y 0 (13 wide: wide bodies turn in it)
GATE_Z = APRON[2] - 4       # the portal's doors


# ============================================================================================ the crag
def _angle(x, z):
    return math.atan2(z - PEAK[1], x - PEAK[0])


def plateau_r(th):
    """The summit's edge: wandering with two harmonics, bulging east (the precipice side carries T5)."""
    east = max(0.0, math.cos(th)) ** 2
    west = max(0.0, -math.cos(th)) ** 2
    return 14.5 + 1.8 * math.sin(2 * th + 0.4) + 1.0 * math.sin(5 * th + 1.9) + 4.5 * east + 1.5 * west


def fin(th):
    """Rock fins: narrow buttress ribs of rock running down the cliff faces every ~40 degrees."""
    return 1.6 * max(0.0, math.cos(9 * th + 0.8)) ** 3 + 0.9 * max(0.0, math.cos(14 * th + 2.6)) ** 5


def is_precipice(th):
    """The east face: one sheer dark drop (deepslate), from north-east round to south-east."""
    return -0.7 < th < 0.55


# the cliff bands outward from the plateau's edge: (drop, width of the band, width of the ledge under it)
BANDS = ((13, 2.4, 1.6), (11, 2.8, 2.2))
TALUS = (9, 1.9)            # the lowest cliff: 9 down over 2 cells, boulders at its foot (no unit climbs it)
FOOT = 2                    # the grass slope starts here and runs out to the box edge


def _profile(d, th):
    """The crag's height along a ray: plateau, cliff bands with ledges, talus, the grass foot."""
    r = plateau_r(th) + fin(th)
    if d <= r:
        return PL
    y = PL
    if is_precipice(th):                                   # the precipice: one drop, nearly vertical, to the talus
        w = 1.8
        if d <= r + w:
            t = (d - r) / w
            return int(round(PL - (PL - 10) * (1 - (1 - t) ** 3)))
        r += w
        y = 10
        if d <= r + 3.0:
            return int(round(y - (y - FOOT) * (d - r) / 3.0))
        return _foot(d, r + 3.0, th)
    for drop, bw, lw in BANDS:
        bw += 0.6 * fin(th)                                 # a fin spreads the band (a rib), not the ledge
        if d <= r + bw:
            t = (d - r) / bw
            return int(round(y - drop * (1 - (1 - t) ** 3)))  # vertical at the top, easing at the foot
        r += bw
        y -= drop
        if d <= r + lw:
            return y                                       # the ledge
        r += lw
    drop, w = TALUS
    w += 0.4 * math.sin(3 * th + 0.9)                      # the talus spreads a little unevenly round the foot
    if d <= r + w:
        t = (d - r) / w
        return int(round(y - drop * t ** 0.8 + 0.8 * math.sin(4 * th + 2.0) * math.sin(math.pi * t)))
    return _foot(d, r + w, th)


def _foot(d, r, th):
    """The grass foot: a gentle convex slope that rolls - low rises and hollows wander round the mountain so no
    contour runs in a ring."""
    west = max(0.0, -math.cos(th))                        # the west foot runs out further: the forest side
    base = FOOT - (FOOT - 1) * min(1.0, (d - r) / (8.0 + 6.0 * west))
    roll = 0.9 * math.sin(2.3 * d + 2.1 * th) + 0.6 * math.sin(0.7 * d - 3.1 * th + 1.0)
    return max(1, int(round(base + roll * min(1.0, (d - r) / 3.0))))


HS = 5


RAVINE = ((-13.0, 19.0), (-14.5, 29.5), 1.6, 5)     # from, to, half width, floor: a cleft in the south-west foot


def ravine_depth(x, z):
    """How far a cell lies inside the ravine (0 outside): the cleft's walls are its own cliff faces."""
    (ax, az), (bx, bz), hw, _ = RAVINE
    vx, vz = bx - ax, bz - az
    t = max(0.0, min(1.0, ((x - ax) * vx + (z - az) * vz) / (vx * vx + vz * vz)))
    d = math.hypot(x - (ax + t * vx), z - (az + t * vz))
    return max(0.0, hw + 0.5 - d)


def hill_h(x, z):
    if APRON[0] <= x <= APRON[1] and APRON[2] <= z:
        return 0
    if ravine_depth(x, z) > 0:
        return min(RAVINE[3], _hill_h(x, z))
    return _hill_h(x, z)


def _hill_h(x, z):
    d = math.hypot(x - PEAK[0], z - PEAK[1])
    th = _angle(x, z)
    # the jitter: a cell's own hash wobbles the band edges by up to one cell, so no contour runs straight
    dj = d + (hsh(x, z, 31) % 3 - 1) * 0.35
    edge = min(int((R + 0.5 - max(abs(x), abs(z))) * 2.2) + 1,  # the mountain's foot runs out inside the box,
               int((R + 5 - math.hypot(x, z)) * 1.8) + 1)            # round at the corners
    h = _profile(dj, th)
    return min(edge, max(h, min(PL, lobe_h(x, z))))


# the crag's spurs: under every building a lobe of rock whose top is the building's foot (less its masonry), falling
# away 1.6 a cell - so the castle sits on outcrops of the mountain, as in the picture, never on stilts of wall.
# (x0, x1, z0, z1, top)
LOBES = [
    (-10, 10, -22, 3, PL),             # the hall and the crossing tower
    (-5, 5, 2, 9, PL - 5),             # the terrace (its retaining wall 5 high)
    (-16, -6, 0, 6, PL - 9),           # the left front chapel (4 courses of wall)
    (8, 18, 0, 6, PL - 8),             # the right front chapel
    (-17, -9, -8, 0, PL),              # tower 1
    (9, 17, -7, 1, PL),                # tower 2
    (-23, -15, -18, -10, PL - 4),      # tower 4
    (-22, -16, -7, 1, PL - 12),        # tower 3's wall block (6 courses of wall)
    (18, 25, -2, 7, PL - 14),          # tower 5 on the east cliff's edge
    (-22, -19, 9, 12, PL - 14),        # the stair's landing
    (-23, -18, 19, 23, PL - 24),       # the bridge's far pier
]


def lobe_h(x, z):
    best = 0
    for (x0, x1, z0, z1, top) in LOBES:
        dx = max(x0 - x, 0, x - x1)
        dz = max(z0 - z, 0, z - z1)
        best = max(best, int(top - 2.2 * math.hypot(dx, dz)))
    return best


def rock_name(x, y, z):
    """Strata in big calm zones: dark at the foot (deepslate, tuff), the body in stone and andesite, pale calcite veins
    high up; the east precipice all deepslate."""
    th = _angle(x, z)
    dark = is_precipice(th) and math.hypot(x - PEAK[0], z - PEAK[1]) > plateau_r(th) - 1 and y > 4
    band = (y + (hsh(x // 6, z // 6, 3) % 3)) // 4
    if dark:
        return ("deepslate", "cobbled_deepslate", "deepslate", "tuff", "deepslate", "polished_deepslate")[band % 6]
    damp = x < -4 and hsh(x // 4, y // 3, 17) % 5 == 0           # the west faces, in the rain's lee: mossy patches
    if y < 9:
        return ("tuff", "cobblestone", "cobbled_deepslate", "tuff", "andesite", "mossy_cobblestone" if damp else "stone")[band % 6]
    if y > PL - 8:
        return ("stone", "andesite", "calcite", "stone", "dripstone_block", "stone")[band % 6]
    if damp:
        return "mossy_cobblestone"
    return ("andesite", "stone", "tuff", "dripstone_block", "andesite", "stone", "cobblestone")[band % 7]


def build_hill(p):
    hm = {(x, z): hill_h(x, z) for x in range(-R, R + 1) for z in range(-R, R + 1)}

    def h(x, z):
        return hm.get((x, z), 0)
    for (x, z), t in hm.items():
        mn = min(h(x + a, z + b) for a in range(-2, 3) for b in range(-2, 3))
        d = math.hypot(x - PEAK[0], z - PEAK[1])
        th = _angle(x, z)
        foot = d > plateau_r(th) + sum(b[1] + b[2] for b in BANDS) + TALUS[1]
        for y in range(0, t):
            if y < 3 or y >= mn - 2:                        # a hollow shell: 2-3 thick (the game never sees inside)
                p.set(x, y, z, rock_name(x, y, z) if (y > 0 or not foot) else "dirt")
        if t <= 0:
            continue
        top = t - 1
        # what grows on top: grass on the foot and on a ledge wide enough to hold soil (its neighbours level with it),
        # moss in the damp spots, bare rock on the cliff faces and the fins
        level_nb = sum(1 for a in (-1, 0, 1) for b in (-1, 0, 1) if abs(h(x + a, z + b) - t) <= 1)
        if foot:
            bare = hsh(x // 3, z // 3, 5) % 11 == 0
            p.set(x, top, z, "coarse_dirt" if bare else "grass_block", **({} if bare else {"snowy": "false"}))
            k = hsh(x, z, 13) % 19
            near_cliff = d < plateau_r(th) + fin(th) + sum(b[1] + b[2] for b in BANDS) + TALUS[1] + 4
            if k == 0 and t < 8:                            # a fallen boulder, bigger near the cliff's foot
                p.set(x, t, z, "andesite" if hsh(x, z, 14) % 2 else "cobblestone")
                if hsh(x, z, 15) % 2 or near_cliff:
                    p.set(x + 1, t, z, "stone")
                    if near_cliff:
                        p.set(x, t, z + 1, "cobblestone")
                        p.set(x, t + 1, z, "andesite")
            elif k in (1, 2) and p.get(x, top, z) == "grass_block":
                p.set(x, t, z, "fern" if k == 1 else "grass")
        elif d <= plateau_r(th) + fin(th) and t >= PL:
            r = hsh(x // 3, z // 3, 6) % 10                 # the summit: turf in patches, moss in clusters, rock
            if r < 6:
                p.set(x, top, z, "grass_block", snowy="false")
            elif r < 8:
                p.set(x, top, z, "moss_block")
        elif level_nb >= 7 and not is_precipice(th):
            r = hsh(x // 2, z // 2, 6) % 10
            if r < 5:
                p.set(x, top, z, "grass_block", snowy="false")
            elif r < 8:
                p.set(x, top, z, "moss_block")
        elif level_nb >= 5 and hsh(x // 2, z // 2, 8) % 4 == 0 and not is_precipice(th):
            p.set(x, top, z, "moss_block")
    # the apron: paved floor, ground course
    for x in range(APRON[0], APRON[1] + 1):
        for z in range(APRON[2], R + 1):
            p.set(x, 0, z, "path")
    # overhanging lips at the top of the cliff bands, roots under them; ivy on the faces the camera sees
    for (x, z), t in hm.items():
        for (nx, nz, face) in ((x, z + 1, "north"), (x - 1, z, "east")):
            nt = hm.get((nx, nz), 0)
            if nt >= 6 and t - nt >= 6 and hsh(x, z, 12) % 3 == 0 and (nx, nz) in hm and t < PL:
                p.set(nx, t - 1, nz, rock_name(nx, t - 1, nz))
                p.set(nx, t - 2, nz, "hanging_roots", waterlogged="false")
                if hsh(x, z, 19) % 2 and t - nt >= 9:              # a deeper lip: two out, roots under both
                    nx2, nz2 = nx + (nx - x), nz + (nz - z)
                    if hm.get((nx2, nz2), 99) < t - 3:
                        p.set(nx2, t - 1, nz2, rock_name(nx2, t - 1, nz2))
                        p.set(nx2, t - 2, nz2, "hanging_roots", waterlogged="false")
            for y in range(max(nt, 1), t - 1):
                if hsh(x * 3 + z * 7, y // 3, 9) % 4 == 0 and p.get(nx, y, nz) == "air":
                    p.set(nx, y, nz, "vine", **{face: "true"})
    return hm


def trees(p, hm):
    """Bushes on the ledges; articulated broadleaf trees at the mountain's foot."""
    for (x, z), t in hm.items():
        d = math.hypot(x - PEAK[0], z - PEAK[1])
        th = _angle(x, z)
        r = plateau_r(th) + fin(th)
        if r + 1.5 < d < r + 7 and hsh(x, z, 21) % 9 == 0 and p.get(x, t - 1, z) in ("grass_block", "moss_block"):
            for (a, b, c) in ((0, 0, 0), (1, 0, 0), (0, 0, 1), (-1, 0, 0), (0, 0, -1), (0, 1, 0)):
                if p.get(x + a, t + c, z + b) == "air" and hm.get((x + a, z + b), 99) <= t + c:
                    p.set(x + a, t + c, z + b, "leaves2" if hsh(x + a, z + b, 22) % 3 == 0 else "leaves", **LEAF)
    placed = []
    for (x, z) in ((-12, 24), (12, 23), (-19, 17), (19, 18), (-25, 8), (25, 10), (-8, 27), (8, 27), (-26, -8),
                   (26, -12), (-14, -26), (14, -26), (0, -27), (-26, 24), (26, 25)):
        if APRON[0] - 1 <= x <= APRON[1] + 1 and z >= APRON[2] - 2:
            continue
        t = hm[(x, z)]
        if t <= 0 or t > 12:
            continue
        placed.append((x, z))
        _tree(p, x, z, t, big=abs(z) > 20 or abs(x) > 20)
    return placed


def _tree(p, x, z, t, big=False):
    """A broadleaf with a real trunk and branches: the trunk 5-8 tall, two or three branches leaving it as logs laid
    sideways, each carrying its own leaf mass, a crown over the top - layered, not a blob."""
    trunk = 5 + hsh(x, z, 3) % 3 + (2 if big else 0)
    for y in range(t, t + trunk):
        p.set(x, y, z, "log", axis="y")
    masses = [(x, t + trunk, z, 2.2 + (0.6 if big else 0)), (x + 1, t + trunk - 2, z - 1, 2.0),
              (x - 1, t + trunk - 3, z + 1, 1.8)]
    for k in range(2 + (hsh(x, z, 4) % 2)):
        a = k * 2.1 + hsh(x, z, 5) % 3
        bx, bz = int(round(math.cos(a) * 2)), int(round(math.sin(a) * 2))
        by = t + trunk - 2 - k
        p.set(x + (bx // 2 or bx), by, z + (bz // 2 or bz), "log", axis="x" if abs(bx) >= abs(bz) else "z")
        p.set(x + bx, by, z + bz, "log", axis="x" if abs(bx) >= abs(bz) else "z")
        masses.append((x + bx, by + 1, z + bz, 1.9))
    for (cx, cy, cz, rr) in masses:
        for a in range(-4, 5):
            for b in range(-4, 5):
                for c in range(-2, 3):
                    if a * a + b * b + (c * 1.4) ** 2 <= rr * rr and p.get(cx + a, cy + c, cz + b) == "air" \
                            and cy + c >= t + 3:
                        p.set(cx + a, cy + c, cz + b, "leaves2" if hsh(cx + a, cz + b, cy + c) % 5 == 0 else "leaves",
                              **LEAF)


# ============================================================================================ the gatehouse and the route
def gatehouse(p):
    """The spawn apron's end: the sealed portal into the rock (nested arches, a wimperg, closed doors) between two
    round gate towers, crenellated, with cones; lanterns on posts before it."""
    pt = GP.portal(5, 9, 3, pal=PAL)
    p.place(pt, 0, 0, GATE_Z)
    for s in (-1, 1):
        tx = s * 10
        t = GT.stone_tower("round", 3, 14, setoff=None, crown="crenel", roof="cone", per=3, lucarnes=False,
                           rock=False, door_at_foot=False, seed=6 + s, pal=PAL, pils=False)
        p.place(t, tx, 0, GATE_Z + 1)
        p.set(s * 4, 1, APRON[2] + 3, "rail")
        p.lantern(s * 4, 2, APRON[2] + 3)


def stair_run(p, x0, y0, z0, dx, dz, n, width=2, lamps=4):
    """A flight of stone steps n long from (x0, y0, z0) going (dx, dz) and DOWN one a step, `width` wide (across the
    run), a parapet of walls on the outer edge with a lantern post every `lamps` steps; masonry under every step down
    to the rock. Returns the foot's (x, y, z)."""
    ax, az = dz, -dx                                            # across the run (the outer side)
    f = {(1, 0): "west", (-1, 0): "east", (0, 1): "north", (0, -1): "south"}[(dx, dz)]   # a stair faces up-slope
    x, y, z = x0, y0, z0
    for i in range(n):
        for w in range(width):
            cx, cz = x + ax * w, z + az * w
            p.stair(cx, y, cz, f, "bottom", "base_st")
            found(p, cx, cx, cz, cz, y, wall_depth=99)
        px, pz = x + ax * width, z + az * width              # the parapet
        found(p, px, px, pz, pz, y + 1, wall_depth=99)
        p.set(px, y + 1, pz, "rail")
        if i % lamps == 1:
            p.set(px, y + 2, pz, "rail")
            p.lantern(px, y + 3, pz)
        x, y, z = x + dx, y - 1, z + dz
    return x, y + 1, z


def arch_bridge(p, x0, x1, y, z0, width=2):
    """A level stone bridge along x from x0 to x1 (deck at y), `width` wide from z0: a pointed arch of stairs under
    the deck between piers that go down to the rock, parapets both sides, lantern posts at both ends."""
    n = x1 - x0 + 1
    top = min(3, (n - 1) // 2)                                   # the arch's rise from its springing to its crown
    for i, x in enumerate(range(x0, x1 + 1)):
        rise = min(i, n - 1 - i, top)
        soffit = y - 2 - (top - rise)                            # the arch's underside: low at the ends, high mid-span
        for w in range(width):
            z = z0 + w
            p.set(x, y - 1, z, "path")
            for yy in range(soffit + 1, y - 1):                   # the spandrel between the arch and the deck
                p.set(x, yy, z, "wall")
            p.stair(x, soffit, z, "east" if i < n // 2 else "west", "top", "base_st")
            if i in (0, n - 1):                                   # the piers, down to the rock
                for yy in range(1, soffit):
                    if p.get(x, yy, z) in ("air", "vine", "hanging_roots"):
                        p.set(x, yy, z, "base" if yy > soffit - 3 else "wall")
        for z in (z0 - 1, z0 + width):
            p.set(x, y - 1, z, "base")
            p.set(x, y, z, "rail")
            if i in (0, n - 1):
                p.set(x, y + 1, z, "rail")
                p.lantern(x, y + 2, z)


def landing(p, x0, x1, z0, z1, y, lamp=True):
    """A paved landing with a parapet all round, a lantern at its outer corner."""
    found(p, x0, x1, z0, z1, y, wall_depth=99)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            p.set(x, y - 1, z, "path")
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            if x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1):
                found(p, x, x, z, z, y, wall_depth=99)
                p.set(x, y, z, "rail")
    if lamp:
        p.set(x0 - 1, y + 1, z1 + 1, "rail")
        p.lantern(x0 - 1, y + 2, z1 + 1)
    # a battered plinth of rock two cells wider round the masonry's foot: a slot of ground between a landing's wall
    # and the slope is a pocket a unit drops into and never leaves (the pocket audit found one at L3, round 5)
    for x in range(x0 - 3, x1 + 4):
        for z in range(z0 - 3, z1 + 4):
            g = ground_at(p, x, z, top=y - 2)
            if 1 < g < y - 2:
                for yy in range(g, min(y - 2, g + 3)):
                    p.set(x, yy, z, rock_name(x, yy, z))


def great_stair(p):
    """The route down the crag from the terrace to the gatehouse (Emberheart's staircases technique): a flight west
    along the south face from the terrace's corner, a landing on the first ledge, a flight south down the west gully,
    a landing, and a last flight east to the postern door of the west gate tower - lanterns all the way. Decorative:
    it never meets the apron floor (units stay on the apron)."""
    fx, fy, fz = stair_run(p, -8, PL, 11, -1, 0, 8, width=2)
    landing(p, fx - 3, fx, fz - 1, fz + 2, fy)
    fx, fy, fz = stair_run(p, fx - 2, fy - 1, fz + 3, 0, 1, 6, width=2)
    landing(p, fx - 2, fx + 1, fz, fz + 2, fy)
    arch_bridge(p, fx + 2, fx + 8, fy - 1, fz + 1, width=2)
    landing(p, fx + 9, fx + 10, fz, fz + 2, fy - 1, lamp=False)


# ============================================================================================ the hall
def lancet_bay(p, x, y, z, f, w=3, h=7, mullion=True, glass="win_lit"):
    """A tall pointed lancet in a wall face at (x, z) (its centre cell) facing f: the opening w wide and h tall cut
    into the two-thick wall, stained glass one in with a mullion, a glow behind, a moulded sill proud of the face, a
    pointed head of stairs stepping in, a carved keystone and a hood moulding; over it a WIMPERG - a small crocketed
    gable one cell proud with a finial."""
    vx, vz = VEC[f]
    ax, az = -vz, vx                                            # along the face
    hw = w // 2
    for k in range(h):
        for s in range(-hw, hw + 1):
            cx, cz = x + ax * s, z + az * s
            p.set(cx, y + k, cz, "air")
            if mullion and s == 0:
                p.set(cx - vx, y + k, cz - vz, "pillar", axis="y")
            else:
                p.set(cx - vx, y + k, cz - vz, ("glass_rose_a", glass, "glass_rose_c", glass)[(k // 2 + s) % 4]
                      if k % 5 == 2 else glass)
            p.set(cx - 2 * vx, y + k, cz - 2 * vz, "light" if (k + s) % 3 == 0 else "floor")
    # the pointed head: stairs stepping in over the opening, a keystone
    for k in range(hw + 1):
        for s in (-(hw - k), (hw - k)):
            cx, cz = x + ax * s, z + az * s
            if hw - k > 0:
                p.stair(cx, y + h + k, cz, cw_face(f, s), "top", "trim_st")
            else:
                p.set(cx, y + h + k, cz, "carve")
    # the sill, the jamb shafts and the hood, all one cell proud
    for s in range(-hw - 1, hw + 2):
        p.slab(x + ax * s + vx, y - 1, z + az * s + vz, "top", "trim_slab")
    for s in (-hw - 1, hw + 1):
        for k in range(0, h):
            p.set(x + ax * s + vx, y + k, z + az * s + vz, "rail")
        p.set(x + ax * s + vx, y + h, z + az * s + vz, "carve")
    for k in range(hw + 2):
        for sgn in (-1, 1):
            s = sgn * (hw + 1 - k)
            cx, cz = x + ax * s + vx, z + az * s + vz
            if hw + 1 - k > 0:
                p.stair(cx, y + h + k, cz, cw_face(f, s), "bottom", "band_st")
            else:
                p.set(cx, y + h + k, cz, "band")
    # the wimperg: a crocketed gable over the hood, its rake climbing to a finial
    yb = y + h + 1
    for k in range(hw + 2):
        for sgn in (-1, 1):
            s = sgn * (hw + 1 - k)
            cx, cz = x + ax * s + vx, z + az * s + vz
            if hw + 1 - k > 0:
                p.stair(cx, yb + k + 1, cz, cw_face(f, s), "bottom", "accent_st")
                if k % 2 == 1:
                    p.set(cx, yb + k + 2, cz, "rail")                          # crockets
            else:
                p.set(cx, yb + k + 1, cz, "carve")
                p.set(cx, yb + k + 2, cz, "bars")
                p.set(cx, yb + k + 3, cz, "spike", facing="up")
        for s in range(-(hw - k), hw - k + 1):                                  # the gable's field: pierced tracery
            cx, cz = x + ax * s + vx, z + az * s + vz
            if hw + 1 - k > 0 and p.get(cx, yb + k + 1, cz) == "air":
                p.set(cx, yb + k + 1, cz, "bars" if (s + k) % 2 else "accent")


def cw_face(f, s):
    """The facing of a stair stepping IN from the side s of a face f (s < 0 the left end seen from outside)."""
    vx, vz = VEC[f]
    ax, az = -vz, vx
    ex, ez = (-ax, -az) if s < 0 else (ax, az)                 # the stair faces toward the opening's centre
    return {(1, 0): "west", (-1, 0): "east", (0, 1): "north", (0, -1): "south"}[(ex, ez)]


def pier(p, x, z, f, y0, y1, pin=True, depth=2):
    """A buttress pier against a wall at (x, z) facing f: `depth` deep at the foot stepping to one, weathered set-offs,
    a pinnacle above the eaves (the bay rhythm: pier - bay - pier)."""
    vx, vz = VEC[f]
    m = GT.Mason(p, y1 + 4, hsh(x, z) % 5)
    mid = y0 + (y1 - y0) * 3 // 5
    for y in range(y0, y1 + 1):
        m.put(x + vx, y, z + vz, quoin=(y // 2) % 2 == 0)
        if depth >= 2 and y < mid:
            m.put(x + 2 * vx, y, z + 2 * vz, quoin=(y // 2) % 2 == 1)
    if depth >= 2:
        p.stair(x + 2 * vx, mid, z + 2 * vz, OPP[f], "bottom", "band_st")
    p.stair(x + vx, y1 + 1, z + vz, OPP[f], "bottom", "band_st")
    if pin:
        GP._pinnacle(p, x + vx, y1 + 2, z + vz, tall=True)


def hall(w=15, d=21, h=20, per=3, rose=5, roof="needle", seed=1):
    """The nave: local x -hw..hw, z -d+1..0 (the front face z = 0 toward the camera), walls y 0..h. Masonry two thick
    with quoins; the flanks in the BAY RHYTHM (pier + pinnacle, lancet + wimperg, two storeys, a string course); the
    front a great portal under the rose with lancets either side, buttress piers at the corners, a crocketed rake with
    pinnacles riding it, a blind arcade; a steep deepslate roof with lucarnes, ridge cresting and gold finials; a lit
    sealed nave inside."""
    p = Part(PAL)
    hw = w // 2
    z0, z1 = -d + 1, 0
    m = GT.Mason(p, h + 6, seed)
    for y in range(0, h + 1):
        for x in range(-hw, hw + 1):
            for z in range(z0, z1 + 1):
                if x in (-hw, -hw + 1, hw - 1, hw) or z in (z0, z0 + 1, z1 - 1, z1):
                    m.put(x, y, z, quoin=(abs(x) == hw and z in (z0, z1) and (y // 2) % 2 == 0))
    # the nave inside: a plank floor, a runner, a glowing vault
    GP.lit_room(p, -hw + 2, hw - 2, z0 + 2, z1 - 2, 0, h - 1)
    for x in range(-hw + 2, hw - 1):
        for z in range(z0 + 2, z1 - 1):
            if (x + z) % 2:
                p.set(x, h - 1, z, "light")
    # the string course between the two storeys and the eaves course
    ys = 10
    for x in range(-hw, hw + 1):
        p.stair(x, h, z1 + 1, "north", "top", "band_st")
    # the flanks: piers every four cells, a lancet in every bay, two storeys
    for s, f in ((-1, "west"), (1, "east")):
        x = s * hw
        zs = list(range(z0 + 2, z1 - 1, 4))
        for z in zs:
            pier(p, x, z, f, 0, h - 1)
        for z in zs:
            zc = z + 2
            if zc > z1 - 2:
                continue
            lancet_bay(p, x, 2, zc, f, w=3, h=6)
            lancet_bay(p, x, ys + 2, zc, f, w=3, h=5)
        for z in range(z0, z1 + 1):
            p.stair(x + s, ys, z, OPP[f], "top", "band_st")     # the string course
    # the front: a portal, lancets either side, corner piers, the blind arcade under the rose
    pt = GP.portal(5, 9, 2, pal=PAL)
    p.place(pt, 0, 0, z1)
    for s in (-1, 1):
        pier(p, s * hw, z1, "south", 0, h - 1)
        pier(p, s * hw, z0, "north", 0, h - 1)
        lancet_bay(p, s * (hw - 1), 3, z1, "south", w=1, h=6, mullion=False)
        lancet_bay(p, s * (hw - 1), ys + 3, z1, "south", w=1, h=5, mullion=False)
    # the roof along z, the gable walls, the rake proud of the front
    half = hw + 1
    ytop = h + 1
    for k in range(half + 1):
        xl, xr = -half + k, half - k
        y = h + 1 + k * per
        for z in range(z0 - 1, z1 + 2):
            if xl < xr:
                for c in range(per - 1):
                    p.set(xl, y + c, z, roof)
                    p.set(xr, y + c, z, roof)
                p.stair(xl, y + per - 1, z, "east", "bottom", roof + "_st")
                p.stair(xr, y + per - 1, z, "west", "bottom", roof + "_st")
            else:
                p.set(xl, y, z, roof)
                p.set(xl, y + 1, z, roof + "_wall")
                ytop = y + 1
        for z in (z0, z1):                                    # gable walls (front and back)
            for x in range(xl + 1, xr):
                for c in range(per):
                    m.put(x, y + c, z, quoin=False)
        if xl < xr:                                           # the rake, proud of the front, crocketed
            for c in range(per):
                for xx, fc in ((xl, "east"), (xr, "west")):
                    if c < per - 1:
                        p.set(xx, y + c, z1 + 1, "accent")
                    else:
                        p.stair(xx, y + c, z1 + 1, fc, "bottom", "accent_st")
            if k % 2 == 1:                                    # pinnacles riding the rake
                GP._pinnacle(p, xl, y + per, z1 + 1)
                GP._pinnacle(p, xr, y + per, z1 + 1)
            else:
                p.set(xl, y + per, z1 + 1, "rail")
                p.set(xr, y + per, z1 + 1, "rail")
    p.set(0, ytop, z1 + 1, "carve")                           # the apex: a tall pinnacle with a gold knop
    GP._pinnacle(p, 0, ytop + 1, z1 + 1, tall=True)
    p.set(0, ytop + 6, z1 + 1, "gold")
    for z in range(z0, z1 + 1, 2):                            # ridge cresting
        p.set(0, ytop + 1, z, "bars")
    p.set(0, ytop + 1, z0, "gold")
    p.set(0, ytop + 2, z0, "spike", facing="up")
    # lucarnes on both slopes: little lit gables standing out of the roof
    for s in (-1, 1):
        for z in (z0 + 4, z0 + 10, z1 - 4):
            k = 2
            x = s * (half - k)
            y = h + 1 + k * per
            f = "west" if s < 0 else "east"
            for dz in (-1, 0, 1):
                p.set(x + s, y, z + dz, "trim")
                p.set(x + s, y + 1, z + dz, "win_lit" if dz == 0 else "trim")
                p.set(x, y + 1, z + dz, "light" if dz == 0 else roof)
                p.stair(x + s, y + 2, z + dz, "south" if dz < 0 else "north", "bottom", roof + "_st") if dz else \
                    p.set(x + s, y + 2, z + dz, roof)
            p.set(x + s, y + 3, z, roof + "_wall")
            p.set(x + s, y + 4, z, "spike", facing="up")
    if rose:
        cy = h + 2 + rose + 1
        for x in range(-rose, rose + 1):
            for y in range(cy - rose, cy + rose + 1):
                dd = math.hypot(x, y - cy)
                if dd > rose + 0.5:
                    continue
                a = math.atan2(y - cy, x)
                spoke = abs(((a / (math.pi / 6)) % 1) - 0.5) > 0.38 and dd > 1.5
                if abs(dd - rose) < 0.5 or abs(dd - rose / 2) < 0.45 or spoke:
                    p.set(x, y, z1, "accent")
                elif dd < 0.8:
                    p.set(x, y, z1, "gold")
                else:
                    p.set(x, y, z1, ("glass_rose_a", "glass_rose_c", "glass_rose_b")[(int(dd) + int((a + 4) * 2)) % 3])
                p.set(x, y, z1 - 1, "light")
        for x in range(-rose - 1, rose + 2):
            for y in range(cy, cy + rose + 2):
                if abs(math.hypot(x, y - cy) - (rose + 1)) < 0.5:
                    p.stair(x, y, z1 + 1, "north", "top", "accent_st")
    return p


# ============================================================================================ towers
def tower_to(tip, base_y, **kw):
    """A stone tower whose finial tip lands at `tip` when its foot is at base_y (h searched)."""
    kw.setdefault("rock", False)
    kw.setdefault("door_at_foot", False)
    kw.setdefault("pal", PAL)
    best = None
    for h in range(12, 100):
        t = GT.stone_tower(h=h, **kw)
        top = t.bbox()[3] + base_y
        if best is None or abs(top - tip) < abs(best[0] - tip):
            best = (top, t)
        if top > tip:
            break
    return best[1]


def found(p, x0, x1, z0, z1, y, wall_depth=0):
    """Foundations: `wall_depth` courses of masonry under a footprint (a wall rising out of the rock), then rock down
    to the crag - which already has a spur (a LOBE, see hill_h) waiting under every building."""
    m = GT.Mason(p, 60, 2)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            yy = y - 1
            while yy >= 1 and p.get(x, yy, z) in ("air", "vine", "hanging_roots", "leaves", "leaves2") \
                    and yy > y - 45:
                p.set(x, yy, z, m.stone(x, yy + 20, z) if y - yy <= wall_depth else rock_name(x, yy, z))
                yy -= 1


def ground_at(p, x, z, top=None):
    y = top if top is not None else H - 1
    while y > 0 and p.get(x, y - 1, z) in ("air", "vine", "hanging_roots"):
        y -= 1
    return y


def place_tower(p, x, z, base_y, tip, **kw):
    t = tower_to(tip, base_y, **kw)
    x0, x1, _, _, z0, z1 = t.bbox()
    found(p, x + x0 + 1, x + x1 - 1, z + z0 + 1, z + z1 - 1, base_y)
    p.place(t, x, base_y, z)


# ============================================================================================ the crown
ANGEL_TOWER = dict(x=0, z=-13, base=PL, h=40)
LANTERN_H = 9                # the tracery lantern stage over the square shaft
DOME_R = 5
_DOME_TOP = GP.ribbed_dome(DOME_R, pal=PAL)[1]
PLINTH_R = 2


def _plinth_y0():
    t = ANGEL_TOWER
    return t["base"] + t["h"] + 1 + LANTERN_H + 1 + _DOME_TOP


# the statue's anchor: the air cell centred on the plinth's top face, in the structure's own block space (the box's
# north-west corner at x = z = 0, its floor at y = 0) - where BuildingParts.STATUES stands the drawn angel
STATUE_ANCHOR = (ANGEL_TOWER["x"] + R, _plinth_y0() + 3, ANGEL_TOWER["z"] + R)
STATUE_REACH = 11            # blocks round the anchor that must stay empty above the plinth (the wings' span)


def lantern_stage(p, x, z, y0, r=4, h=LANTERN_H):
    """The TRACERY LANTERN: an octagon of tall glazed lancets between slim piers on the square shaft's head - the
    light of the crossing seen from everywhere - corner pinnacles, a cornice; the drum and dome stand on it."""
    cells = GT.plan("oct", r)
    ol = GT.outline(cells)
    cs = GT.corners("oct", cells)
    for y in range(y0, y0 + h):
        for (cx, cz) in ol:
            corner = (cx, cz) in cs
            if corner or y in (y0, y0 + h - 1):
                p.set(x + cx, y, z + cz, "quoin" if corner else "trim")
            elif y == y0 + h - 2 and (cx + cz) % 2 == 0:
                p.stair(x + cx, y, z + cz, toward(cx, cz), "top", "trim_st")
            else:
                p.set(x + cx, y, z + cz, "win_lit" if (cx * 3 + cz) % 4 else "glass_rose_c")
        for (cx, cz) in cells - ol:
            if y == y0:
                p.set(x + cx, y, z + cz, "floor")
            elif y == y0 + 3 and (cx + cz) % 2 == 0:
                p.set(x + cx, y, z + cz, "light")
            elif y == y0 + h - 1:
                p.set(x + cx, y, z + cz, "trim")
    for (cx, cz) in GT.ring_of("oct", r + 1, r):
        p.stair(x + cx, y0 + h - 1, z + cz, toward(cx, cz), "top", "band_st")   # the cornice
        p.stair(x + cx, y0, z + cz, toward(cx, cz), "top", "band_st")           # the foot's drip
    for (cx, cz) in cs:
        GP._pinnacle(p, x + cx, y0 + h, z + cz, tall=True)


def angel_tower(p, x=ANGEL_TOWER["x"], z=ANGEL_TOWER["z"], base=ANGEL_TOWER["base"], h=ANGEL_TOWER["h"]):
    """The crown of the capitol and its highest point: a square stone tower with bartizans rising out of the hall's
    back half, the tracery lantern stage, a dark ribbed dome on a drum ringed with pinnacles, and on its crown the
    statue's plinth - the drawn WINGED ANGEL stands there (STATUE_ANCHOR)."""
    t = GT.stone_tower("square", 4, h=h, setoff=0.5, crown="plain", roof="none", lucarnes=False, bartizans=True,
                       door_at_foot=False, rock=False, seed=3, pal=PAL)
    x0, x1, _, _, z0, z1 = t.bbox()
    found(p, x + x0 + 1, x + x1 - 1, z + z0 + 1, z + z1 - 1, base)
    p.place(t, x, base, z)
    y = base + h + 1
    lantern_stage(p, x, z, y)
    y += LANTERN_H + 1
    dome, dtop = GP.ribbed_dome(DOME_R, pal=PAL)
    p.place(dome, x, y, z)
    statue_plinth(p, x, y + dtop, z)


def statue_plinth(p, x, y0, z):
    """A clean flat plinth on the dome's crown for the drawn statue: three courses of pale stone 5 across (a disc of
    radius PLINTH_R, its corners cut) - a splayed foot of stairs round the lowest, a plain drum, a cornice of upturned
    stairs round the top - so the statue stands on a flat 5 x 5 top face at y0 + 3 with nothing on it."""
    for (a, b) in GP.disc(PLINTH_R):
        p.set(x + a, y0, z + b, "trim")
        p.set(x + a, y0 + 1, z + b, "band")
        p.set(x + a, y0 + 2, z + b, "trim")
    for (a, b) in GP.ring(PLINTH_R + 1):
        f = toward(a, b)
        p.stair(x + a, y0, z + b, f, "bottom", "trim_st")
        p.stair(x + a, y0 + 2, z + b, f, "top", "trim_st")


# ============================================================================================ the levels
HALL = dict(w=17, d=21, h=20, per=2, rose=5, roof="needle", seed=1)
HALL_Z = 2                   # its front face


def l1(p):
    hm = build_hill(p)
    trees(p, hm)
    gatehouse(p)
    angel_tower(p)
    return hm


def terrace(p):
    found(p, -7, 7, HALL_Z + 1, 10, PL, wall_depth=6)
    for x in range(-7, 8):
        for z in range(HALL_Z + 1, 11):
            p.set(x, PL - 1, z, "path")
        p.set(x, PL, 10, "rail")
    for z in range(HALL_Z + 1, 11):
        for x in (-7, 7):
            p.set(x, PL, z, "rail")
    for x in range(-5, 6):
        for z in (6, 7, 8):
            if (x + z) % 3 and abs(x) != 2:
                p.set(x, PL - 1, z, "grass_block", snowy="false")
                p.set(x, PL, z, ("orange_tulip", "red_tulip", "poppy", "oxeye_daisy")[(x * 3 + z) % 4])
    for x in (-5, 0, 5):
        p.set(x, PL, 10, "base")
        p.set(x, PL + 1, 10, "rail")
        p.lantern(x, PL + 2, 10)
    for x in (-3, 3):
        p.set(x, PL - 1, 5, "base")
        p.lantern(x, PL, 5)


def parapet(p):
    """A low crenellated wall along the summit's cliff edge: the castle's walls grow straight out of the rock (the
    picture's lesson). Every edge cell of the plateau: a wall post, a capped merlon on every third."""
    for x in range(-R, R + 1):
        for z in range(-R, R + 1):
            d = math.hypot(x - PEAK[0], z - PEAK[1])
            th = _angle(x, z)
            r = plateau_r(th) + fin(th)
            if r - 1.2 < d <= r and hill_h(x, z) >= PL and (hill_h(x, z + 1) < PL or hill_h(x + 1, z) < PL
                                                                or hill_h(x - 1, z) < PL or hill_h(x, z - 1) < PL):
                if p.get(x, PL, z) != "air":
                    continue
                p.set(x, PL - 1, z, "base")
                p.set(x, PL, z, "base")
                p.set(x, PL + 1, z, "base_wall")
                if (x * 3 + z * 5) % 3 == 0:
                    p.slab(x, PL + 2, z, "bottom", "base_slab")


def l2(p):
    found(p, -9, 9, HALL_Z - 21, HALL_Z + 1, PL)
    p.place(hall(**HALL), 0, PL, HALL_Z)
    for y in range(PL, PL + 20):                             # a solid newel at the centre: the centre probe lands on
        p.set(0, y, 0, "wall")                               # stone, never in the sealed hall
    transepts(p)
    terrace(p)
    parapet(p)


def transepts(p):
    """The transept arms either side of the crossing (the plan cruciform, as a cathedral's): each a gabled wing 9 deep
    standing out five from the nave's flank, lower than the nave, its gable end carrying a pair of lancets under a
    small rose, corner piers with pinnacles, a deepslate roof whose ridge runs into the nave's."""
    hw = HALL["w"] // 2
    for s_ in (-1, 1):
        arm = transept(9, 6, 15, seed=7 + s_)
        found(p, min(s_ * (hw + 1), s_ * (hw + 6)), max(s_ * (hw + 1), s_ * (hw + 6)), -18, -10, PL)
        p.place(arm, s_ * (hw + 6), PL, -14, rot=1 if s_ < 0 else 3)   # the gable end outward, the arm into the nave


def transept(w, d, h, seed=0):
    """A transept arm in local space: its gable end at z = 0 (toward the camera), w wide, running d deep into the
    nave (the nave's own wall closes it off), h to the eaves."""
    p = Part(PAL)
    hw = w // 2
    z0, z1 = -d + 1, 0
    m = GT.Mason(p, h + 4, seed)
    for y in range(0, h + 1):
        for x in range(-hw, hw + 1):
            for z in range(z0, z1 + 1):
                if x in (-hw, -hw + 1, hw - 1, hw) or z in (z1 - 1, z1):
                    m.put(x, y, z, quoin=(abs(x) == hw and z == z1 and (y // 2) % 2 == 0))
                elif y == 0:
                    p.set(x, y, z, "floor")
                elif y == h - 1 and (x + z) % 2:
                    p.set(x, y, z, "light")
                elif y == h:
                    p.set(x, y, z, "floor")
    for s_ in (-1, 1):
        lancet_bay(p, s_ * 2, 2, z1, "south", w=1, h=6, mullion=False)
        pier(p, s_ * hw, z1, "south", 0, h - 1)
        lancet_bay(p, s_ * hw, 3, z0 + 4, "west" if s_ < 0 else "east", w=1, h=5, mullion=False)
    # a small rose in the gable end
    cy = h + 2 + 3
    for x in range(-2, 3):
        for y in range(cy - 2, cy + 3):
            dd = math.hypot(x, y - cy)
            if dd > 2.5:
                continue
            p.set(x, y, z1, "accent" if abs(dd - 2) < 0.5 or dd < 0.6 else "glass_rose_a" if (x + y) % 2 else "glass_rose_c")
            p.set(x, y, z1 - 1, "light")
    # the roof along z (toward the nave), the gable, the rake with crockets
    half = hw + 1
    per = 2
    ytop = h + 1
    for k in range(half + 1):
        xl, xr = -half + k, half - k
        y = h + 1 + k * per
        for z in range(z0, z1 + 2):
            if xl < xr:
                p.set(xl, y, z, "needle")
                p.set(xr, y, z, "needle")
                p.stair(xl, y + 1, z, "east", "bottom", "needle_st")
                p.stair(xr, y + 1, z, "west", "bottom", "needle_st")
            else:
                p.set(xl, y, z, "needle")
                p.set(xl, y + 1, z, "needle_wall")
                ytop = y + 1
        for x in range(xl + 1, xr):
            for c in range(per):
                m.put(x, y + c, z1, quoin=False)
        if xl < xr:
            for xx, fc in ((xl, "east"), (xr, "west")):
                p.set(xx, y, z1 + 1, "accent")
                p.stair(xx, y + 1, z1 + 1, fc, "bottom", "accent_st")
            if k % 2:
                p.set(xl, y + 2, z1 + 1, "rail")
                p.set(xr, y + 2, z1 + 1, "rail")
    p.set(0, ytop, z1 + 1, "carve")
    GP._pinnacle(p, 0, ytop + 1, z1 + 1, tall=True)
    return p


def l3(p):
    # tower 1 just left of the gable (slim, brick needle) and tower 2 right of it (a timber gallery under its needle)
    place_tower(p, -13, -3, PL, 96, shape="oct", r=3, setoff=0.4, crown="none", roof="cone", per=5, seed=11,
                lucarnes=False, eaves=False, roof_r="taper")
    place_tower(p, 13, -2, PL, 90, shape="oct", r=3, setoff=0.4, crown="timber", roof="cone", per=5, seed=12,
                roof_r="taper", eaves=False, lucarnes=False)
    # the front chapels under them, forward and lower, on foundations out of the rock
    for x, h, by in ((-11, 12, PL - 5), (13, 13, PL - 4)):
        found(p, x - 5, x + 5, 0, 7, by, wall_depth=4)
        p.place(chapel(9, 8, h, seed=4 + x), x, by, 7)
    great_stair(p)


def chapel(w, d, h, seed=0):
    """A small gabled chapel: masonry, a lancet bay on its front and flanks, corner piers with pinnacles, a brick
    roof with a crocketed rake and a finial."""
    p = Part(PAL)
    hw = w // 2
    z0, z1 = -d + 1, 0
    m = GT.Mason(p, h + 4, seed)
    for y in range(0, h + 1):
        for x in range(-hw, hw + 1):
            for z in range(z0, z1 + 1):
                if x in (-hw, -hw + 1, hw - 1, hw) or z in (z0, z0 + 1, z1 - 1, z1):
                    m.put(x, y, z, quoin=(abs(x) == hw and z in (z0, z1) and (y // 2) % 2 == 0))
    GP.lit_room(p, -hw + 2, hw - 2, z0 + 2, z1 - 2, 0, h - 1)
    lancet_bay(p, 0, 2, z1, "south", w=3, h=5)
    for s, f in ((-1, "west"), (1, "east")):
        lancet_bay(p, s * hw, 2, (z0 + z1) // 2, f, w=1, h=4, mullion=False)
        pier(p, s * hw, z1, "south", 0, h - 1)
        pier(p, s * hw, z0, "north", 0, h - 1)
    half = hw + 1
    per = 3
    ytop = h + 1
    for k in range(half + 1):
        xl, xr = -half + k, half - k
        y = h + 1 + k * per
        for z in range(z0 - 1, z1 + 2):
            if xl < xr:
                for c in range(per - 1):
                    p.set(xl, y + c, z, "roof")
                    p.set(xr, y + c, z, "roof")
                p.stair(xl, y + per - 1, z, "east", "bottom", "roof_st")
                p.stair(xr, y + per - 1, z, "west", "bottom", "roof_st")
            else:
                p.set(xl, y, z, "roof")
                p.set(xl, y + 1, z, "roof_wall")
                ytop = y + 1
        for z in (z0, z1):
            for x in range(xl + 1, xr):
                for c in range(per):
                    m.put(x, y + c, z, quoin=False)
        if xl < xr:
            for xx, fc in ((xl, "east"), (xr, "west")):
                for c in range(per - 1):
                    p.set(xx, y + c, z1 + 1, "accent")
                p.stair(xx, y + per - 1, z1 + 1, fc, "bottom", "accent_st")
            if k % 2:
                p.set(xl, y + per, z1 + 1, "rail")
                p.set(xr, y + per, z1 + 1, "rail")
    p.set(0, ytop, z1 + 1, "carve")
    GP._pinnacle(p, 0, ytop + 1, z1 + 1)
    return p


def l4(p):
    # tower 4: a small needle behind on the left
    place_tower(p, -19, -14, PL - 4, 76, shape="oct", r=2, setoff=None, crown="none", roof="cone", per=4,
                lucarnes=False, seed=13, eaves=False, roof_r="taper")
    # tower 3: far left, round, a dark timber collar under its needle, standing on the west walls
    b3 = PL - 6
    found(p, -22, -16, -7, 1, b3, wall_depth=6)
    place_tower(p, -19, -3, b3, 74, shape="round", r=2, setoff=None, crown="timber", roof="cone", per=4,
                roof_r="taper", eaves=False, lucarnes=False, pils=False, seed=14)
    curtain_wall(p)


def curtain_wall(p, a0=95, a1=235):
    """The curtain wall of L4: along the summit's cliff edge from the south-south-west round the west to the
    north-west, three thick, seven high, machicolated under a crenellated wall-walk, lanterns every eighth cell -
    growing straight out of the precipice, with T3 standing on it."""
    cells = []
    for x in range(-R, R + 1):
        for z in range(-R, R + 1):
            d = math.hypot(x - PEAK[0], z - PEAK[1])
            th = math.degrees(_angle(x, z)) % 360
            r = plateau_r(_angle(x, z)) + fin(_angle(x, z))
            if a0 <= th <= a1 and r - 3.2 < d <= r and hill_h(x, z) >= PL:
                cells.append((x, z, d, r))
    m = GT.Mason(p, 12, 4)
    for (x, z, d, r) in cells:
        outer = d > r - 1.2
        for y in range(PL - 1, PL + 7):
            if p.get(x, y, z) == "air" or p.get(x, y, z) in ("grass_block", "moss_block", PAL["base_wall"],
                                                              PAL["base_slab"], PAL["base"]):
                m.put(x, y, z, quoin=False)
        if outer:
            p.stair(x, PL + 6, z, toward(x, z, PEAK[0], PEAK[1]) if False else _outward(x, z), "top", "band_st")
            if (x * 3 + z * 5) % 2 == 0:
                p.set(x, PL + 5, z, "base_wall")
            p.set(x, PL + 7, z, "trim")
            if (x * 7 + z * 3) % 3:
                p.set(x, PL + 8, z, "wall")
                p.slab(x, PL + 9, z, "bottom", "band_slab")
            else:
                p.slab(x, PL + 8, z, "bottom", "band_slab")
            if (x * 5 + z * 11) % 8 == 0:
                p.set(x, PL + 8, z, "rail")
                p.lantern(x, PL + 9, z)
        else:
            p.set(x, PL + 7, z, "path")                      # the wall-walk


def _outward(x, z):
    """The facing of a corbel stair hung on the outer face of a wall cell: toward the hill's centre (a 'top' stair
    whose high side points inward)."""
    return toward(x, z, PEAK[0], PEAK[1])


def bridge(p):
    """The arched bridge of the route: from the landing on the first ledge over the gully southward to a far pier on
    its own spur, a parapet with lantern posts (GP.bridge turned along z)."""
    b = GP.bridge(1, 7, 10, pal=PAL)
    p.place(b, -22, 12, 9, rot=1)


def l5(p):
    # the dark east cliff: a face of dark stone over the rock, crowned by a parapet
    for x in range(10, R + 1):
        for z in range(-14, 8):
            th = _angle(x, z)
            d = math.hypot(x - PEAK[0], z - PEAK[1])
            rp = plateau_r(th) + fin(th)
            if rp - 0.5 <= d <= rp + 1.5 and is_precipice(th):
                top = ground_at(p, x, z)
                for y in range(8, min(top, PL)):
                    p.set(x, y, z, ("polished_deepslate", "deepslate_bricks", "deepslate_tiles")[
                        (y // 4 + hsh(x // 3, z // 3, 1)) % 3] if (y + x) % 9 else "cracked_deepslate_bricks")
    place_tower(p, 22, 3, PL - 14, 78, shape="round", r=2, setoff=None, crown="timber", roof="cone",
                per=4, seed=15, pils=False, roof_r="taper", eaves=False)
    # the barred arch in the rock, front left, above the gate (on the rock face the camera sees)
    ax = -9
    az = max(z for z in range(0, R) if hill_h(ax, z) >= 24)
    for y in range(18, 25):
        for x in range(ax - 2, ax + 3):
            p.set(x, y, az, "polished_blackstone" if abs(x - ax) <= 1 and y < 23 else "base")
    for x in (ax - 1, ax, ax + 1):
        for y in range(18, 23):
            p.set(x, y, az + 1, "bars")
    for y in range(18, 23):
        p.set(ax - 2, y, az + 1, "base")
        p.set(ax + 2, y, az + 1, "base")
    p.stair(ax - 1, 23, az + 1, "west", "top", "trim_st")
    p.stair(ax + 1, 23, az + 1, "east", "top", "trim_st")
    p.set(ax, 23, az + 1, "carve")
    cottages(p)


def cottages(p):
    """The timber town at the castle's foot: two half-timbered cottages either side of the gate road."""
    for (x, z, rot) in ((-22, 22, 0), (21, 23, 0), (-26, 12, 1)):
        c = cottage(7, 6, seed=x)
        y = ground_at(p, x, z)
        found(p, x - 3, x + 3, z - 5, z, y)
        p.place(c, x, y, z, rot=rot)
    # the road: a strip of path from the apron's mouth along the foot to the cottages, a well at the fork
    for (x, z) in [(x, 27) for x in range(-20, -6)] + [(x, 27) for x in range(7, 20)] + [(-21, z) for z in range(14, 28)]:
        y = ground_at(p, x, z)
        if 0 < y < 6 and p.get(x, y - 1, z) in ("grass_block", "coarse_dirt", "dirt", "moss_block"):
            p.set(x, y - 1, z, "dirt_path")
    wx, wz = 20, 27
    y = ground_at(p, wx, wz)
    for (a, b) in ((0, 0), (1, 0), (0, 1), (1, 1)):
        p.set(wx + a, y, wz + b, "base")
    for (a, b) in ((-1, 0), (2, 1)):
        p.set(wx + a, y, wz + b, "timber", axis="y")
        p.set(wx + a, y + 1, wz + b, "timber", axis="y")
        p.set(wx + a, y + 2, wz + b, "timber", axis="y")
    for a in range(-1, 3):
        p.set(wx + a, y + 3, wz, "timber_beam", axis="x")
        p.set(wx + a, y + 3, wz + 1, "timber_beam", axis="x")
    p.lantern(wx, y + 1, wz) if False else None


def cottage(w, d, seed=0):
    """A half-timbered cottage: a stone ground storey with a door and a lit window, a jettied timber storey, a steep
    brick roof with a finial and a chimney."""
    p = Part(PAL)
    hw = w // 2
    z0, z1 = -d + 1, 0
    for y in range(0, 4):
        for x in range(-hw, hw + 1):
            for z in range(z0, z1 + 1):
                if x in (-hw, hw) or z in (z0, z1):
                    p.set(x, y, z, "base")
                else:                                        # solid inside (a hollow at ground level is a pocket a
                    p.set(x, y, z, "light" if y == 2 else "floor")   # unit built over could land in); lit behind

    p.set(0, 1, z1, "door", facing="north", half="lower", hinge="left", open="false", powered="false")
    p.set(0, 2, z1, "door", facing="north", half="upper", hinge="left", open="false", powered="false")
    for x in (-2, 2):
        p.set(x, 2, z1, "win_lit")
    for x in range(-hw - 1, hw + 2):
        p.stair(x, 4, z1 + 1, "north", "top", "timber_st")
    for y in range(4, 8):
        for x in range(-hw - 1, hw + 2):
            for z in range(z0, z1 + 2):
                on_x = x in (-hw - 1, hw + 1)
                on_z = z in (z0, z1 + 1)
                if on_x or on_z:
                    # half-timbering: corner posts and a post every third cell, sill and head beams, a mid rail,
                    # white plaster panels, a lit window in the middle of every panel on the front
                    corner = on_x and on_z
                    post = corner or (on_z and (x + hw + 1) % 3 == 0) or (on_x and (z - z0) % 3 == 0)
                    beam = y in (4, 7)
                    win = y in (5, 6) and not post and z == z1 + 1 and (x + hw + 1) % 3 == 1
                    if post:
                        p.set(x, y, z, "timber", axis="y")
                    elif beam:
                        p.set(x, y, z, "timber_beam", axis="x" if on_z else "z")
                    elif win:
                        p.set(x, y, z, "win_lit")
                    else:
                        p.set(x, y, z, "plaster")
                else:
                    p.set(x, y, z, "light" if y in (5, 6) else "floor")
    half = hw + 2
    for k in range(half + 1):
        xl, xr = -half + k, half - k
        y = 8 + 2 * k
        for z in range(z0 - 1, z1 + 3):
            if xl < xr:
                p.set(xl, y, z, "roof")
                p.set(xr, y, z, "roof")
                p.stair(xl, y + 1, z, "east", "bottom", "roof_st")
                p.stair(xr, y + 1, z, "west", "bottom", "roof_st")
            else:
                p.set(xl, y, z, "roof")
                p.set(xl, y + 1, z, "roof_wall")
        for z in (z0, z1 + 1):
            for x in range(xl + 1, xr):
                p.set(x, y, z, "plaster" if (x + y) % 3 else "timber", **({} if (x + y) % 3 else {"axis": "y"}))
                p.set(x, y + 1, z, "plaster" if (x + y + 1) % 3 else "timber", **({} if (x + y + 1) % 3 else {"axis": "y"}))
    for y in range(8, 8 + 2 * half + 1):
        p.set(hw - 1, y, z0 + 2, "base")
    p.set(hw - 1, 8 + 2 * half + 1, z0 + 2, "base_wall")
    return p


def l6(p):
    # flying buttresses along both flanks of the hall, pinnacles on the terrace rim and the chapels, banners, lanterns
    fb = GP.buttress(16, pal=PAL)
    for z in (HALL_Z - 16, HALL_Z - 10):
        p.place(fb, -8, PL, z, rot=1)
        p.place(fb, 8, PL, z, rot=3)
    for x in range(-7, 8, 7):
        GP._pinnacle(p, x, PL + 1, 10, tall=True)
    for x, z in ((-4, 10), (4, 10)):
        p.set(x, PL, z, "rail")
        p.lantern(x, PL + 1, z)
    # gold on the dome's ribs: the crown lights up at the last level
    x_, z_ = ANGEL_TOWER["x"], ANGEL_TOWER["z"]
    y0 = ANGEL_TOWER["base"] + ANGEL_TOWER["h"] + 1 + LANTERN_H + 1
    for (cx, cy, cz), (name, props) in list(p.b.items()):
        if abs(cx - x_) <= DOME_R and abs(cz - z_) <= DOME_R and y0 + 1 <= cy <= y0 + 3 and name == PAL["trim"]:
            p.set(cx, cy, cz, "gold")                        # a gilded band at the ribs' feet
    # gilded ridge cresting along the nave, a gold knop under every spike round the drum
    hw = HALL["w"] // 2
    ridge = PL + HALL["h"] + 1 + (hw + 1) * HALL["per"] + 2
    for z in range(HALL_Z - 20, HALL_Z + 1, 6):
        if p.get(0, ridge, z) in ("air", PAL["bars"]):
            p.set(0, ridge, z, "gold")
    for (cx, cy, cz), (name, props) in list(p.b.items()):
        if name == PAL["spike"] and abs(cx - x_) <= DOME_R + 2 and abs(cz - z_) <= DOME_R + 2 and cy > y0 - 8:
            p.set(cx, cy - 1, cz, "gold")
    # banners in the faction's blue hung from T1 and T2's collars, facing the camera
    for (bx, bz) in ((-13, 1), (13, 2)):
        y = ground_at(p, bx, bz)
        if p.get(bx, y - 1, bz) != "air" and y < PL + 60:
            p.set(bx, y, bz, "banner", rotation="0")
    # the pinnacle forest: a pinnacle between every pair of piers along both flanks' eaves, and on the chapels
    hw = HALL["w"] // 2
    for s_ in (-1, 1):
        for z in range(HALL_Z - 18, HALL_Z - 1, 4):
            GP._pinnacle(p, s_ * (hw + 1), PL + HALL["h"] + 1, z)


STAGES = [l1, l2, l3, l4, l5, l6]

# close crops for the review sheet (tools/capitol_sheet.py): label, centre in the structure's own space
FOCAL_POINTS = [
    ("the crown, L6 (18 px/block)", (ANGEL_TOWER["x"] + R, _plinth_y0() + 7, ANGEL_TOWER["z"] + R), 18),
    ("the hall front and the bay rhythm, L6", (R, PL + 14, HALL_Z - 2 + R)),
    ("the gate and the great stair, L6", (-5 + R, 12, 16 + R)),
]


def level(n):
    p = Part(PAL)
    for st in STAGES[:n]:
        st(p)
    rig = gs._SunRig(2 * R + 1, H, 2 * R + 1)
    for (x, y, z), (name, props) in p.b.items():
        if name in ("air", "cave_air"):
            continue
        if abs(x) > R or abs(z) > R or y < 0 or y >= H:
            continue
        rig.set(x + R, y, z + R, name, **props)
    # vines only where they hang on something
    for (x, y, z), (name, props) in list(rig.b.items()):
        if name == "vine":
            f = next(k for k in ("north", "south", "east", "west") if props.get(k) == "true")
            ox, oz = VEC[f]
            if rig.b.get((x + ox, y, z + oz), ("air",))[0] in ("air", "vine"):
                del rig.b[(x, y, z)]
        elif name in PLANTS and name not in ("vine", "hanging_roots"):
            if rig.b.get((x, y - 1, z), ("air",))[0] not in ("grass_block", "moss_block"):
                del rig.b[(x, y, z)]
        elif name == "hanging_roots" and rig.b.get((x, y + 1, z), ("air",))[0] in ("air",):
            del rig.b[(x, y, z)]
    # a lantern a later part built over loses its table: it goes (they are inside sealed rooms or on posts)
    for (x, y, z), (name, props) in list(rig.b.items()):
        if name == "lantern" and not gs._sun_centre_up(rig.b.get((x, y - 1, z), ("air", {}))):
            del rig.b[(x, y, z)]
    rig.bury_grass()
    rig.settle()
    rig.check_support("white angel capitol v5 L%d" % n)
    return rig


FORBIDDEN = ("lava", "sand", "gravel", "_concrete_powder", "anvil", "redstone", "repeater", "comparator",
             "observer", "piston", "lever", "_button", "_pressure_plate", "tnt", "scaffolding")


def check(rigs):
    prev = None
    counts = []
    for n, rig in enumerate(rigs, 1):
        assert rig.size == (2 * R + 1, H, 2 * R + 1), rig.size
        names = {p: v[0] for p, v in rig.b.items()}
        bad = sorted({nm for nm in names.values() if any(nm == f or nm.endswith(f) for f in FORBIDDEN)
                      or nm in ("fire", "soul_fire", "water")})
        assert not bad, "L%d forbidden: %s" % (n, bad)
        if prev is not None:
            lost = [p for p, nm in prev.items() if p not in names and nm not in PLANTS]
            assert not lost, "L%d empties %d cells of L%d, e.g. %s" % (n, len(lost), n - 1,
                                                                     [(p, prev[p]) for p in lost[:6]])
        x, y, z = SPAWN
        cell = (x + R, y, z + R)
        assert (cell[0], y - 1, cell[2]) in names and cell not in names and \
            (cell[0], y + 1, cell[2]) not in names, "L%d: the spawn cell must be open on a floor" % n
        ax, ay, az = STATUE_ANCHOR                          # the drawn statue's anchor: air, on the plinth, open sky
        assert (ax, ay, az) not in names and (ax, ay - 1, az) in names, "L%d: the statue anchor must be air on the plinth" % n
        over = [(x, y, z) for (x, y, z) in names if y >= ay and math.hypot(x - ax, z - az) <= STATUE_REACH]
        assert not over, "L%d: %d blocks in the statue's space over its plinth, e.g. %s" % (n, len(over), over[:4])
        counts.append(len(names))
        prev = names
    return counts


def export_name(n):
    return "capitol_white_angel" + ("" if n == 1 else "_l%d" % n)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "/tmp"
    only = int(sys.argv[2]) if len(sys.argv) > 2 else None
    if only:
        rig = level(only)
        rig.structure().write(os.path.join(out, export_name(only) + ".nbt"))
        print("L%d" % only, len(rig.b))
        sys.exit(0)
    rigs = [level(n) for n in range(1, LEVELS + 1)]
    print("blocks per level:", check(rigs))
    print("statue anchor (structure space):", STATUE_ANCHOR)
    for n, rig in enumerate(rigs, 1):
        rig.structure().write(os.path.join(out, export_name(n) + ".nbt"))
