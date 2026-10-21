#!/usr/bin/env python3
"""The White Angel (Sunforged) capitol v4: THE CASTLE ON THE CRAG, assembled from the gothic part library.

lovish, Oct 20 2026, 20:54: "I think we need to try to imitate the picture more" - the picture is his reference
9cfaf83e (a pale-stone gothic castle-cathedral crowning a steep rocky hill). v4 imitates it as closely as a Minecraft
build can, at the big scale (59 x 59 reserved, 112 tall), in the warm light grey stone he chose ("middle is fine"),
copper-orange needles as in the picture and dark slate on the big roofs, built from tools/gothic_parts.py and
tools/gothic_towers.py.

THE PICTURE, STUDIED (it is seen from low and a little left of front, looking up; image x left -> right, y top -> down)
  - THE MOUNTAIN takes the lower ~45% of the image: a broad foot with trees and bushes on gentle lower slopes (some
    bare red-brown earth among them, bottom left), then a STEEP rock cone, grey-beige with green ledges and ivy
    everywhere; on the RIGHT it is a sheer dark-grey cliff, almost vertical, from the castle down to the trees.
    The castle does not sit on a flat top: its walls grow out of the rock and step down the front slope.
  - A dark ARCHED OPENING in the rock face, front left of centre at about the castle's foot (a gate or cave).
  - THE CENTRAL BLOCK: a tall gabled front facing the camera, the gable very steep (rise ~1.5x its half width), its
    face rich with orange-warm tracery (an arch-and-rose pattern), under it a grid of tall lancets in two or three
    tiers; behind it a steep DARK roof running back. It is the dominant mass, a little left of the image centre.
  - THE TALLEST SPIRE: behind the central block, a little right of centre (cut off by the image top).
  - TOWERS (seven visible, all slender, all with steep orange-copper needles and gold-ish finials):
      1. just LEFT of the central gable, polygonal, needle top ~ level with the gable apex (very tall)
      2. RIGHT of the gable, polygonal, a little lower than 1, a gallery stage under the needle
      3. FAR LEFT, round, much lower (needle top ~25% down), a dark collar (gallery) under its needle, its shaft
         dropping behind the left walls
      4. LEFT, between 1 and 3, a small needle behind (top ~27% down)
      5. FAR RIGHT, round, a long slender shaft standing on the dark cliff, needle top ~20% down
      6. the back spire (above), 7. small pinnacles/turrets all over (buttress pinnacles)
  - FRONT BAYS: under the towers 1 and 2, smaller gabled bays project forward, each with its own lit pointed window
    and orange-edged gable; between them, at the castle's foot, a TERRACE with orange-red flowers.
  - LEFT WALLS step down the slope with crenellations and a small turret (far left, low).
  - BUTTRESS / PINNACLE RHYTHM: every wall is broken into bays by buttresses ending in pinnacles above the eaves.
  - COLOUR: pale warm stone; orange-copper on the needles, gable edges and tracery; dark slate on the big roof and
    behind; green ivy and moss over the rock; warm light in the windows.

THE BUILD (coordinates from the reservation's centre; +z = south = toward the camera; y = 0 is the ground course)
  - hill: a heightfield (radial from the peak at (0, -4): summit at y 40, steep rock 1.6 a cell, a spur (lobe) under every building, slopes with
    trees to the edge; the east side a sheer dark cliff), hollow inside (a 2-3 thick shell), strata in big zones,
    grass and moss on the ledges, ivy on the faces the camera sees, roots under a few lips, coarse earth among trees.
  - hall: the central gabled block, 13 wide x 13 deep x 18 to the eaves, a steep slate roof (rise 3 a step), two
    tiers of tracery windows (part a), a copper-traced rose in the gable, buttresses with pinnacles.
  - towers 1-5: tools/gothic_towers.stone_tower in the shapes and heights the picture shows, slim tapering copper
    needles, an openwork pinnacle on every collar corner, gable apex and rake (his second ref 0016fdb9: a forest of
    pinnacles); the CROWN where the picture has its tallest spire: a square tower, a dark ribbed dome on a drum ringed
    with pinnacles, and on it the WINGED ANGEL statue (13 x 13, wings spread, gold halo, sword) - the faction's emblem
    on its highest point (gothic_parts.angel, gothic_parts.ribbed_dome).
  - front bays: smaller gabled blocks with a tracery window and a copper-edged gable.
  - the terrace with flowers; the left curtain walls stepping down with a wall turret; the dark east cliff wall.
  - the GATE: the spawn apron at the hill's foot, south, open to the sky and 13 wide (wide bodies), a sealed portal
    into the hill at its end, lanterns. The picture's mid-hill arch is a sealed (barred) recess above it.

THE GROWTH (one origin, 59 x 104 x 59 at every level; a level only adds or replaces blocks):
  - L1  the crag itself (all of it), the gate and apron, and the angel tower - its dome and the angel from the start
  - L2  the hall: the gabled block with its tracery front, rose and steep slate roof
  - L3  towers 1 and 2 beside the gable, the two front bays, the terrace and its flowers, the rim parapet
  - L4  the west: tower 4, tower 3 on its curtain wall, the walls stepping down the slope with a wall turret
  - L5  the east: the dark cliff wall and tower 5 standing on it, the barred mid-hill arch
  - L6  the crown: flying buttresses and pinnacles along the hall, ridge cresting, lanterns, the last finials

RULES (check()): fixed box every level; each level keeps every non-plant cell of the one before; no forbidden blocks;
nothing pops off; the spawn cell on the apron is open on a floor; the centre probe lands in solid stone (the game's
safeSpawnPoint moves it); the pocket audit (tools/audit_structure_pockets.py) finds no one-way or ground pockets.
Not wired into the game.

Usage: python3 tools/capitol_white_angel_v4.py OUT_DIR   -> OUT_DIR/white_angel_capitol_v4_l1.nbt .. _l6.nbt
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
PL = 40                     # the summit (castle floor)
PEAK = (0, -4)
PAL = palette("copper", "warm")
PLANTS = {"vine", "orange_tulip", "red_tulip", "poppy", "cornflower", "azure_bluet", "oxeye_daisy", "grass",
          "fern", "hanging_roots"}
SPAWN = (0, 1, 24)          # on the apron, open to the sky
APRON = (-6, 6, 17, 29)     # x0 x1 z0 z1, floor at y 0 (13 wide: wide bodies turn in it)


# ============================================================================================ the hill
def radii(x, z):
    dx, dz = x - PEAK[0], z - PEAK[1]
    th = math.atan2(dz, dx)
    east = max(0.0, math.cos(th)) ** 2 * (1.0 if dz < 6 else 0.4)
    wob = 1.1 * math.sin(3 * th + 0.7) + 0.7 * math.sin(7 * th + 2.0)
    rp = 9.5 + 5.0 * east + wob * 0.5
    rs = rp + 17.5 - 11.0 * east + wob
    return math.hypot(dx, dz), rp, rs


HS = 5                     # the steep rock's foot


# the crag's spurs: under every building a lobe of rock whose top is the building's foot (less its masonry), falling
# away 1.6 a cell - so the castle sits on outcrops of the mountain, as in the picture, never on stilts of wall.
# (x0, x1, z0, z1, top)
LOBES = [
    (-8, 8, -15, -1, PL),              # the hall
    (-5, 5, -1, 9, PL - 5),            # the terrace (its retaining wall 5 high)
    (-14, -4, -2, 5, PL - 9),          # the left front bay (4 courses of wall)
    (7, 17, -2, 5, PL - 8),            # the right front bay
    (-15, -7, -10, -2, PL),            # tower 1
    (7, 15, -8, 0, PL),                # tower 2
    (-18, -10, -15, -7, PL - 4),       # tower 4
    (-21, -15, -6, 0, PL - 12),        # tower 3's wall block (6 courses of wall)
    (17, 25, -1, 7, PL - 16),          # tower 5 on the east cliff's edge
    (-1, 9, -20, -10, PL - 2),         # the back tower
]


def lobe_h(x, z):
    best = 0
    for (x0, x1, z0, z1, top) in LOBES:
        dx = max(x0 - x, 0, x - x1)
        dz = max(z0 - z, 0, z - z1)
        best = max(best, int(top - 1.6 * math.hypot(dx, dz)))
    return best


def hill_h(x, z):
    d, rp, rs = radii(x, z)
    if APRON[0] <= x <= APRON[1] and APRON[2] <= z:
        return 0
    edge = min(int((R + 0.5 - max(abs(x), abs(z))) * 2.2) + 1,  # the mountain's foot runs out inside the box,
               int((R + 5 - math.hypot(x, z)) * 1.8) + 1)            # round at the corners
    return min(edge, max(_hill_h(x, z), min(PL, lobe_h(x, z))))


def _hill_h(x, z):
    d, rp, rs = radii(x, z)
    if d <= rp:
        return PL
    if d <= rs:
        t = (d - rp) / (rs - rp)
        h = PL - (PL - HS) * t ** 0.85
        return int(h)
    rg = 29.5
    if d >= rg:
        return 1
    return max(1, int(round(HS * (1 - (d - rs) / (rg - rs)))))


def rock_name(x, y, z):
    d, rp, rs = radii(x, z)
    dark = x > 8 and z < 10
    band = (y + (hsh(x // 5, z // 5, 3) % 3)) // 5
    if dark:
        return ("deepslate", "tuff", "cobbled_deepslate", "tuff", "deepslate")[band % 5]
    return ("andesite", "stone", "tuff", "calcite", "stone", "dripstone_block", "andesite")[band % 7]


def build_hill(p):
    hm = {(x, z): hill_h(x, z) for x in range(-R, R + 1) for z in range(-R, R + 1)}

    def h(x, z):
        return hm.get((x, z), 0)
    for (x, z), t in hm.items():
        mn = min(h(x + a, z + b) for a in range(-2, 3) for b in range(-2, 3))
        d, rp, rs = radii(x, z)
        for y in range(0, t):
            if y < 3 or y >= mn - 2:
                p.set(x, y, z, rock_name(x, y, z) if y > 0 or d < rs else "dirt")
        if t <= 0:
            continue
        top = t - 1
        if d > rs:                                   # the gentle slopes: grass, some bare earth among the trees
            p.set(x, top, z, "coarse_dirt" if hsh(x // 2, z // 2, 5) % 7 == 0 and x < 0 else "grass_block",
                  **({} if hsh(x // 2, z // 2, 5) % 7 == 0 and x < 0 else {"snowy": "false"}))
        elif d > rp:                                 # ledges on the steep rock
            r = hsh(x, z, 6) % 10
            if r < 6:
                p.set(x, top, z, "grass_block", snowy="false")
            elif r < 8:
                p.set(x, top, z, "moss_block")
        else:
            p.set(x, top, z, "path")
    # the apron: paved floor, ground course
    for x in range(APRON[0], APRON[1] + 1):
        for z in range(APRON[2], R + 1):
            p.set(x, 0, z, "path")
    # ivy on the faces the camera sees, roots under a few lips
    for (x, z), t in hm.items():
        for (nx, nz, face) in ((x, z + 1, "north"), (x - 1, z, "east")):
            nt = hm.get((nx, nz), 0)
            for y in range(max(nt, 1), t - 1):
                if hsh(x * 3 + z * 7, y // 4, 9) % 5 == 0 and p.get(nx, y, nz) == "air":
                    p.set(nx, y, nz, "vine", **{face: "true"})
            if nt >= 14 and t - nt >= 4 and hsh(x, z, 12) % 4 == 0 and (nx, nz) in hm:
                p.set(nx, t - 1, nz, rock_name(nx, t - 1, nz))
                p.set(nx, t - 2, nz, "hanging_roots", waterlogged="false")
    return hm


def trees(p, hm):
    # bushes and ivy clumps on the steep rock's ledges (out of reach of units: the rock is too steep to climb)
    for (x, z), t in hm.items():
        d, rp, rs = radii(x, z)
        if rp + 1.5 < d < rs - 0.5 and hsh(x, z, 21) % 7 == 0:
            for (a, b, c) in ((0, 0, 0), (1, 0, 0), (0, 0, 1), (-1, 0, 0), (0, 0, -1), (0, 1, 0)):
                if p.get(x + a, t + c, z + b) == "air" and hm.get((x + a, z + b), 99) <= t + c:
                    p.set(x + a, t + c, z + b, "leaves2" if hsh(x + a, z + b, 22) % 3 == 0 else "leaves", **LEAF)
    placed = []
    # the big trees at the mountain's front foot (the picture's foreground), either side of the apron
    for (x, z) in ((-11, 23), (11, 22), (-17, 18), (17, 19), (-23, 9), (23, 8), (-6, 27), (7, 27)):
        if x in range(APRON[0] - 1, APRON[1] + 2):
            continue
        t = hm[(x, z)]
        if t <= 0 or t > 14:
            continue
        placed.append((x, z))
        _tree(p, x, z, t, big=True)
    for gx in range(-R + 2, R - 1, 5):
        for gz in range(-R + 2, R - 1, 5):
            x = gx + hsh(gx, gz, 1) % 3 - 1
            z = gz + hsh(gz, gx, 2) % 3 - 1
            d, rp, rs = radii(x, z)
            if not (rp + 11 < d < 28) or abs(x) > R - 3 or abs(z) > R - 3:
                continue
            if APRON[0] - 4 <= x <= APRON[1] + 4 and z >= APRON[2] - 3:
                continue
            if any(abs(x - a) < 5 and abs(z - b) < 5 for a, b in placed):
                continue
            t = hm[(x, z)]
            placed.append((x, z))
            _tree(p, x, z, t)


def _tree(p, x, z, t, big=False):
    """A round-crowned broadleaf: a trunk 4-8 tall, a canopy of azalea leaves (some flowering) from 3 up."""
    trunk = 4 + hsh(x, z, 3) % 3 + (2 if big else 0)
    for y in range(t, t + trunk):
        p.set(x, y, z, "log", axis="y")
    cy = t + trunk
    rr = 2.6 + (hsh(x, z, 4) % 2) * 0.6 + (0.9 if big else 0)
    for a in range(-4, 5):
        for b in range(-4, 5):
            for c in range(-2, 3):
                if a * a + b * b + (c * 1.5) ** 2 <= rr * rr and p.get(x + a, cy + c, z + b) == "air" \
                        and cy + c >= t + 3:
                    p.set(x + a, cy + c, z + b, "leaves2" if hsh(x + a, z + b, cy + c) % 4 == 0 else "leaves",
                          **LEAF)
    p.set(x, cy, z, "log", axis="y")


def gate(p):
    """The spawn apron's end: a sealed portal into the rock, lanterns on posts, the barred arch of the picture above."""
    pt = GP.portal(5, 9, 3, pal=PAL)
    z = APRON[2] - 4                                    # its doors at the apron's end
    p.place(pt, 0, 0, z)
    for s in (-1, 1):
        p.set(s * 4, 1, APRON[2] + 3, "rail")
        p.lantern(s * 4, 2, APRON[2] + 3)


# ============================================================================================ the hall and the bays
def lancet(p, x, y, z, f, h=5):
    """A lit lancet in a wall face at (x, z) facing f: a deep reveal, a pointed head, a sill and a hood."""
    vx, vz = VEC[f]
    for k in range(h):
        p.set(x, y + k, z, "air")
        p.set(x - vx, y + k, z - vz, "win_lit" if (x + y) % 4 else "win_dark")
        p.set(x - 2 * vx, y + k, z - 2 * vz, "light")
    p.set(x, y + h, z, "carve")
    p.slab(x + vx, y - 1, z + vz, "top", "trim_slab")
    p.stair(x + vx, y + h, z + vz, OPP[f], "top", "accent_st")


def pier(p, x, z, f, y0, y1, pin=True):
    """A buttress pier against a wall, one wide and two deep out along f, weathered half way and at the top, a
    pinnacle on it above the eaves."""
    vx, vz = VEC[f]
    m = GT.Mason(p, y1 + 4, hsh(x, z) % 5)
    mid = (y0 + y1) // 2
    for y in range(y0, y1 + 1):
        m.put(x + vx, y, z + vz, quoin=(y // 2) % 2 == 0)
        if y < mid:
            m.put(x + 2 * vx, y, z + 2 * vz, quoin=(y // 2) % 2 == 1)
    p.stair(x + 2 * vx, mid, z + 2 * vz, OPP[f], "bottom", "band_st")
    p.stair(x + vx, y1 + 1, z + vz, OPP[f], "bottom", "band_st")
    if pin:
        GP._pinnacle(p, x + vx, y1 + 2, z + vz, tall=True)


def gable_block(w, d, h, per=3, rose=0, tiers=2, roof="needle", seed=0):
    """A gabled block in local coordinates: x -hw..hw, z -d+1..0 (the front face z = 0, toward the camera), walls
    from y 0 to h. Masonry walls two thick with quoins; tracery window bays across the front in `tiers`; buttress
    piers on the sides; a steep roof (`per` courses a step, eaves one out) with a copper-edged crocketed rake on the
    front, a crested ridge and finials; a copper-traced rose of radius `rose` in the gable."""
    p = Part(PAL)
    hw = w // 2
    z0, z1 = -d + 1, 0
    m = GT.Mason(p, h + 6, seed)
    for y in range(0, h + 1):
        for x in range(-hw, hw + 1):
            for z in range(z0, z1 + 1):
                if x in (-hw, -hw + 1, hw - 1, hw) or z in (z0, z0 + 1, z1 - 1, z1):
                    m.put(x, y, z, quoin=(abs(x) == hw and z in (z0, z1) and (y // 2) % 2 == 0))
    for x in range(-hw, hw + 1):                              # the eaves course
        p.stair(x, h, z1 + 1, "north", "top", "band_st")
    # the front: tracery bays
    bw = 3 if w >= 13 else 3
    cols = [-(hw // 2) - 1, hw // 2 + 1] if w >= 13 else [0]
    tier_h = (h - 2) // tiers
    for t in range(tiers):
        wh = max(5, tier_h - 6)
        for cx in cols:
            p.place(GP.window_bay(bw, wh, depth=2, pal=PAL), cx, 1 + t * tier_h, 0)
    if len(cols) > 1:
        for y in range(1, h):
            p.set(0, y, 1, "pillar", axis="y")
        p.set(0, h - 1, 1, "carve")
    # side walls: piers and lancets
    for s, f in ((-1, "west"), (1, "east")):
        x = s * hw
        for z in range(z0 + 1, z1, 4):
            pier(p, x, z, f, 0, h - 1)
        for z in range(z0 + 3, z1 - 1, 4):
            for yb in range(4, h - 6, 10):
                lancet(p, x, yb, z, f, h=5)
    # corner piers on the front, diagonal-ish (two piers per corner)
    for s in (-1, 1):
        pier(p, s * hw, 0, "south", 0, h - 1)
    # the roof along z, the gable walls, the rake
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
        if xl < xr:                                           # the copper rake, proud of the front
            for c in range(per):
                p.set(xl, y + c, z1 + 1, "accent") if c < per - 1 else p.stair(xl, y + c, z1 + 1, "east", "bottom",
                                                                                "accent_st")
                p.set(xr, y + c, z1 + 1, "accent") if c < per - 1 else p.stair(xr, y + c, z1 + 1, "west", "bottom",
                                                                                "accent_st")
            if k % 3 == 1:                                    # pinnacles riding the rake (his forest of them)
                GP._pinnacle(p, xl, y + per, z1 + 1)
                GP._pinnacle(p, xr, y + per, z1 + 1)
            elif k % 3 == 2:
                p.set(xl, y + per, z1 + 1, "rail")
                p.set(xr, y + per, z1 + 1, "rail")
    p.set(0, ytop, z1 + 1, "carve")                           # the apex: a tall pinnacle with a gold knop
    GP._pinnacle(p, 0, ytop + 1, z1 + 1, tall=True)
    p.set(0, ytop + 4, z1 + 1, "gold")
    for z in range(z0, z1 + 1, 2):                            # ridge cresting
        p.set(0, ytop + 1, z, "bars")
    p.set(0, ytop + 1, z0, "gold")
    p.set(0, ytop + 2, z0, "spike", facing="up")
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


# ============================================================================================ the levels
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


def l1(p):
    hm = build_hill(p)
    trees(p, hm)
    gate(p)
    angel_tower(p)
    return hm


def angel_tower(p, x=4, z=-15, base=PL - 2, h=36):
    """The crown of the capitol and its highest point (back, a little right of centre, as in the picture): a square
    stone tower with bartizans, a dark ribbed dome on a drum ringed with pinnacles, and on it the WINGED ANGEL - the
    White Angel's emblem, readable from the RTS camera (lovish, 21:12)."""
    t = GT.stone_tower("square", 4, h=h, setoff=0.5, crown="plain", roof="none", lucarnes=False, bartizans=True,
                       door_at_foot=False, rock=False, seed=3, pal=PAL)
    x0, x1, _, _, z0, z1 = t.bbox()
    found(p, x + x0 + 1, x + x1 - 1, z + z0 + 1, z + z1 - 1, base)
    p.place(t, x, base, z)
    dome, dtop = GP.ribbed_dome(5, pal=PAL)
    p.place(dome, x, base + h + 1, z)
    p.place(GP.angel(pal=PAL), x, base + h + 1 + dtop + 1, z)


HALL = dict(w=13, d=13, h=18, per=3, rose=4, tiers=2, roof="needle", seed=1)
HALL_Z = -2                  # its front face


def l2(p):
    found(p, -8, 8, HALL_Z - 13, HALL_Z + 1, PL)
    p.place(gable_block(**HALL), 0, PL, HALL_Z)
    for y in range(PL, 54):                                  # a solid newel at the centre: the centre probe lands on
        p.set(0, y, 0, "wall")                               # stone, never in the sealed hall


def l3(p):
    # tower 1 just left of the gable (slim, copper needle) and tower 2 right of it (a gallery stage under its needle)
    place_tower(p, -11, -6, PL, 92, shape="oct", r=2, setoff=None, crown="none", roof="cone", per=4, seed=11,
                lucarnes=False, eaves=False, roof_r="taper")
    place_tower(p, 11, -4, PL, 88, shape="oct", r=2, setoff=None, crown="timber", roof="cone", per=4, seed=12, roof_r="taper",
                eaves=False, lucarnes=False)
    # the front bays under them, forward and lower, on foundations out of the rock
    for x, h, by in ((-9, 11, PL - 5), (12, 12, PL - 4)):
        found(p, x - 5, x + 5, -2, 5, by, wall_depth=4)
        p.place(gable_block(9, 8, h, per=2, rose=0, tiers=1, roof="needle", seed=4 + x), x, by, 5)
    # the terrace before the hall: paving on foundations, a balustrade, beds of orange and red flowers
    found(p, -5, 5, HALL_Z + 1, 9, PL, wall_depth=5)
    for x in range(-5, 6):
        for z in range(HALL_Z + 1, 10):
            p.set(x, PL - 1, z, "path")
        p.set(x, PL, 9, "rail")
    for z in range(HALL_Z + 1, 10):
        for x in (-5, 5):
            p.set(x, PL, z, "rail")
    for x in range(-4, 5):
        for z in (6, 7):
            if (x + z) % 3:
                p.set(x, PL - 1, z, "grass_block", snowy="false")
                p.set(x, PL, z, ("orange_tulip", "red_tulip", "poppy")[(x * 3 + z) % 3])
    for x in (-4, 4):
        p.set(x, PL, 8, "base")
        p.lantern(x, PL + 1, 8)


def l4(p):
    # tower 4: a small needle behind on the left
    place_tower(p, -14, -11, PL - 4, 74, shape="oct", r=2, setoff=None, crown="none", roof="cone", per=3,
                lucarnes=False, seed=13, eaves=False, roof_r="taper")
    # tower 3: far left, round, low, a dark timber collar under its needle, standing on the west walls
    b3 = PL - 6
    found(p, -21, -15, -6, 0, b3, wall_depth=6)
    place_tower(p, -18, -3, b3, 72, shape="round", r=2, setoff=None, crown="timber", roof="cone", per=3, roof_r="taper", eaves=False,
                lucarnes=False, pils=False, seed=14)
    # the walls stepping down the west-front slope, a wall turret at their foot
    seg = GP.curtain_wall(6, 6, pal=PAL)
    for (x, z) in ((-12, 6), (-16, 9), (-20, 12)):
        y = ground_at(p, x, z + 2)
        found(p, x - 3, x + 2, z, z + 3, y)
        p.place(seg, x - 3, y, z)
    tx, tz = -23, 15
    p.place(GT.stone_tower("round", 3, 9, setoff=None, crown="crenel", roof="cone", per=2, roof_r="taper",
                           lucarnes=False, rock=False, door_at_foot=False, seed=6, pal=PAL),
            tx, ground_at(p, tx, tz), tz)


def l5(p):
    # the dark east cliff: a face of dark stone over the rock, crowned by a parapet
    for x in range(10, R + 1):
        for z in range(-14, 8):
            d, rp, rs = radii(x, z)
            if rp - 0.5 <= d <= rp + 1.5:
                top = ground_at(p, x, z)
                for y in range(8, min(top, PL)):
                    p.set(x, y, z, ("polished_deepslate", "deepslate_bricks", "deepslate_tiles")[
                        (y // 4 + hsh(x // 3, z // 3, 1)) % 3] if (y + x) % 9 else "cracked_deepslate_bricks")
    place_tower(p, 21, 3, PL - 16, 76, shape="round", r=2, setoff=None, crown="timber", roof="cone",
                per=4, seed=15, pils=False, roof_r="taper", eaves=False)
    # the barred arch in the rock, front left, above the gate (on the rock face the camera sees)
    ax = -8
    az = max(z for z in range(0, R) if hill_h(ax, z) >= 26)
    for y in range(20, 27):
        for x in range(ax - 2, ax + 3):
            p.set(x, y, az, "polished_blackstone" if abs(x - ax) <= 1 and y < 25 else "base")
    for x in (ax - 1, ax, ax + 1):
        for y in range(20, 25):
            p.set(x, y, az + 1, "bars")
    for y in range(20, 25):
        p.set(ax - 2, y, az + 1, "base")
        p.set(ax + 2, y, az + 1, "base")
    p.stair(ax - 1, 25, az + 1, "west", "top", "trim_st")
    p.stair(ax + 1, 25, az + 1, "east", "top", "trim_st")
    p.set(ax, 25, az + 1, "carve")


def l6(p):
    # flying buttresses along the hall's west side, pinnacles on the terrace rim, lanterns
    fb = GP.buttress(16, pal=PAL)
    for z in (-14, -9):
        p.place(fb, -8, PL, z, rot=1)
    for x in range(-4, 5, 4):
        GP._pinnacle(p, x, PL + 1, 9, tall=(x == 0))
    for x, z in ((-3, 9), (3, 9)):
        p.set(x, PL, z, "rail")
        p.lantern(x, PL + 1, z)


STAGES = [l1, l2, l3, l4, l5, l6]


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
    rig.check_support("white angel capitol v4 L%d" % n)
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
        counts.append(len(names))
        prev = names
    return counts


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "/tmp"
    only = int(sys.argv[2]) if len(sys.argv) > 2 else None
    if only:
        rig = level(only)
        rig.structure().write(os.path.join(out, "white_angel_capitol_v4_l%d.nbt" % only))
        print("L%d" % only, len(rig.b))
        sys.exit(0)
    rigs = [level(n) for n in range(1, LEVELS + 1)]
    print("blocks per level:", check(rigs))
    for n, rig in enumerate(rigs, 1):
        rig.structure().write(os.path.join(out, "white_angel_capitol_v4_l%d.nbt" % n))
