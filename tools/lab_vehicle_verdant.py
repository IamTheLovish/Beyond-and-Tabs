#!/usr/bin/env python3
"""CONCEPT - the Verdant vehicle lab, ROOTING HOLLOW, L1 and L3 (not wired into the game: no Java reference loads
verdant_l1 / _l3; the Verdant T1 army building is the Grove). Sentinel Treants (walking trees, 1.5 wide x 3 tall)
are grown and woken here. No machinery: the base is a forest; the landmark is a great tree.

Round r1 (Oct 31 2026), the r12 method (#411 Engine Shed, #456 Sunforged):
  reference   ONE picture, ref06 (design/refs/vehicle_labs/ref06_tree_hall.png): a colossal twisted tree whose
              trunk spirals and leans, wrapping a steep-roofed timber hall into itself; roots flaring like buttresses;
              a huge layered canopy as THE landmark; sky between the limb and the hall.
  designer    itachi2-api/runs/20261030-204240-verdant-design (massings A/B/C, the 7-axis rubric, the bands).
  massing     A, the wrapped hall (tools/verdant_massing.py): both blind critics' first pick over B, C and the
              round-2 control (eyes 41152348776; runs 20261030-211145-verdant-massing-critic-a / -211153-...-b).
  this round  A + the two critics' shared massing faults, one change per element:
              - the trunk hidden behind the hall and the turret in 34 -> no turret; the trunk stands south-east, its
                roots pour TOWARD the camera over the gallery and down the south face; light bark (stripped birch) with
                dark grooves on the spiral seams;
              - no sky between limb and hall -> the light well (x 8..9) open from the ground to the sky between hall
                and trunk, the west limb arching over it onto the hall's north slope (ref06's crescent);
              - a stepped cone canopy, its north half out of the RTS frame -> broad flat offset tiers centred further
                south, dark spruce bellies, cherry tops;
              - the gate lit and pale, roots not framing it -> a splayed gate (7 wide outer order, 5 wide inner), a
                dark passage (deepslate walls and floor, a ceiling), no light in it; two roots framing it;
              - the turret's cap read as a chimney / second vertical -> gone;
              and the first detail grammar: deepslate plinth, stone storey, jettied mud-brick-and-timber storey,
              string course, a bay rhythm of recessed stairs every third cell, the gallery arcade.
  tests       tools/test_verdant_lab.py (camera tests, written first, red on round 2).

ANCHORS (structure-local: x 0..16 west -> east, y up, z 0..16 north -> south):
  spawn       (8, 1, 8): the light well x 8..9, z 7..9 between hall and trunk, air to the sky at every level
  exit lane   x 0..8, z 6..10, y 1..8: through the hall's west gable (the gate) and out of the well
  heart-glow  (13..14, 1..2, 15): four shroomlights in the trunk's south foot (round 2; round 1's faced the well)

Usage: python3 tools/lab_vehicle_verdant.py [OUT_DIR]   (writes verdant_l1.nbt and verdant_l3.nbt, checks both)
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs            # noqa: E402
import mex_verdant as mv               # noqa: E402  - the true leaf distances
import audit_structure_pockets as ap   # noqa: E402
from gothic_parts import hsh           # noqa: E402

SX, SZ = 17, 17
H1, H3 = 16, 26                 # lovish: "tier 1 buildings should be small" - the Engine Shed T1 / Sunforged caps
SPAWN = (8, 8)
LANE = {(x, y, z) for x in range(0, 9) for z in range(6, 11) for y in range(1, 9)}
WELL = {(x, z) for x in (7, 8, 9) for z in (7, 8, 9)}
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "design", "vehicle_labs")
DRY = dict(waterlogged="false")
LEAF = dict(persistent="true", distance="7", waterlogged="false")

# the palette by role (camera values from design/vehicle_labs/camera_colours.json, west / south / up)
PAVE = "mossy_stone_bricks"            # 0.25 / 0.34 / 0.41
LANE_FLOOR = "mud"                     # the passage floor: dark and smooth (round 2's deepslate tiles read as "a grate")
PLINTH = "deepslate_bricks"            # 0.18 / 0.26
STONE = "stone_bricks"                 # 0.29 / 0.32
MOSSY = "mossy_stone_bricks"
INFILL = "mud_bricks"                  # the jettied storey's infill (warm, light; unmeasured)
POST = "spruce_planks"                 # its posts and rails (0.23 / 0.27; dark oak planks read sat 0.60, loud)
DARK = "deepslate_bricks"              # the passage walls
CEIL = "deepslate_tiles"
ROOF = "mcwroofs:spruce_planks"        # 0.39 / 0.40
FILL = "spruce_planks"                 # hidden cores (attic, ridge)
STRING = "reignofnether:string_course_stone"   # 0.43
CORNICE = "reignofnether:cornice_stone"        # 0.40 / 0.41
GALLERY_TOP = "mossy_stone_brick_slab"         # up 0.40
BARK = "stripped_birch_wood"           # 0.43 / 0.54 / 0.62, sat 0.35: the trunk's light bark
GROOVE = "dark_oak_wood"               # 0.15 / 0.19 / 0.24: the spiral grooves
GRIP = "mangrove_roots"
BELLY, TOP = "spruce_leaves", "cherry_leaves"  # spruce 0.15-0.26 dark bellies; cherry up 0.85 (sat 0.26)
GLOW = "shroomlight"
STAIR_OF = {"mossy_stone_bricks": "mossy_stone_brick_stairs", "stone_bricks": "stone_brick_stairs",
            "deepslate_bricks": "deepslate_brick_stairs", "mud_bricks": "mud_brick_stairs",
            "spruce_planks": "spruce_stairs", "dark_oak_planks": "dark_oak_stairs",
            "cracked_stone_bricks": "stone_brick_stairs"}
TREE = ("_log", "_wood", "_leaves")


class Canvas:
    """The lab's canvas: clipped to the box, never into the lane or the spawn column."""

    def __init__(self, top):
        self.b = {}
        self.top = top

    def ok(self, x, y, z):
        return 0 <= x < SX and 0 <= z < SZ and 0 <= y < self.top and (x, y, z) not in LANE and \
            not ((x, z) == SPAWN and y >= 1)

    def set(self, x, y, z, name, **p):
        if self.ok(x, y, z):
            self.b[(x, y, z)] = (name, p)

    def put(self, x, y, z, name, **p):
        if (x, y, z) not in self.b:
            self.set(x, y, z, name, **p)

    def get(self, x, y, z):
        return self.b.get((x, y, z), ("air",))[0]

    def clear(self, x, y, z):
        self.b.pop((x, y, z), None)

    def stair(self, x, y, z, facing, half="bottom", name="stone_brick_stairs"):
        self.set(x, y, z, name, facing=facing, half=half, shape="straight", **DRY)

    def slab(self, x, y, z, name, kind="bottom"):
        self.set(x, y, z, name, type=kind, **DRY)


# ================================================================================================= ground
def ground(c):
    """y 0: the lawn runs in under the tree (grass, the ground's own value: round 2's full grey pad read as "a model
    on a plinth"); mossy stone paving only where feet go - the gate's approach strip (x 0..1), round the hall and the
    gallery, the well; the passage floor dark under its ceiling (round 1's dark apron outside read as a grate)."""
    for x in range(SX):
        for z in range(SZ):
            paved = x <= 1 or (x <= 9 and 2 <= z <= 13) or z >= 13 and x <= 10
            c.b[(x, 0, z)] = (LANE_FLOOR if 2 <= x <= 7 and 6 <= z <= 10 else PAVE if paved else "grass_block",
                              {"snowy": "false"} if not paved else {})


# ================================================================================================= the hall
HALL_X, HALL_Z = (1, 7), (4, 12)
EAVE = 9


def hall(c, l3):
    """The treant-wright's hall (ref06's steep-roofed hall, wrapped by the tree): x 1..7, z 4..12. A deepslate plinth
    (y 1), a stone storey (y 2..5) under a string course, a mud-brick-and-timber storey (y 6..8), its gable jettied
    out to x 0 over the gate from y 9, a steep spruce gable roof with its ridge along x (y 9..17). The lane runs
    through it: a dark passage (deepslate walls, a dark-oak ceiling at y 9) from the splayed gate to the light well."""
    (x0, x1), (z0, z1) = HALL_X, HALL_Z
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(1, EAVE):
                if x0 < x < x1 and z0 < z < z1:
                    continue                                  # the shell; the passage is cut below
                nm = PLINTH if y == 1 else (MOSSY if hsh(x, y, z) % 3 == 0 else STONE) if y <= 5 else \
                    POST if (x in (x0, x1) and z in (z0, z1)) or (x + z) % 3 == 0 else INFILL
                c.set(x, y, z, nm)
    for z in (z0, z1):                                        # the string course at the storey line
        c.set(x0 - 1, 5, z, STRING, facing="west", shape="straight")
    for x in range(x0 + 1, x1 + 1):                           # the passage: dark walls, a dark ceiling
        for y in range(1, EAVE):
            for z in (5, 11):
                c.set(x, y, z, DARK)
        for z in range(5, 12):
            c.set(x, EAVE, z, CEIL)
    for x in range(x0 + 1, x1):                               # solid rooms beside the passage (no pockets)
        for z in (z0 + 1, z1 - 1):
            for y in range(1, EAVE):
                c.set(x, y, z, STONE)
    for y in range(1, EAVE - 1):                              # the splayed gate: outer order 7 wide at x 1
        for z in (5, 11):
            c.clear(x0, y, z)
    c.stair(x0, EAVE - 1, 5, "north", "top", "deepslate_brick_stairs")
    c.stair(x0, EAVE - 1, 11, "south", "top", "deepslate_brick_stairs")
    for z in range(5, 12):
        c.set(x0, EAVE, z, "chiseled_stone_bricks" if z == 8 else STONE)
    for z in (z0, z1):                                        # the jetty's brackets, held up by mangrove roots
        c.stair(0, EAVE - 1, z, "east", "top", "spruce_stairs")  # that frame the gate (round 1: "no roots frame it")
        for y in range(1, EAVE - 1):
            c.set(0, y, z, "mangrove_roots")
    return gable_roof(c, 0, x1, z0, z1, EAVE, cap=None if l3 else H1 - 1)


def gable_roof(c, x0, x1, z0, z1, y0, cap=None):
    """A steep gable roof, ridge along x (Macaw's steep pieces: 2 rise per run), slopes facing north and south; the
    gable at x0 (the jettied west gable, mud brick and timber) and x1 filled; the attic solid (no pockets). `cap`
    stops it at that height with a flat slab top (L1's roof, a subset of L3's)."""
    runs = (z1 - z0) // 2
    for k in range(runs):
        y = y0 + 2 * k
        for x in range(x0, x1 + 1):
            for z, f in ((z0 + k, "south"), (z1 - k, "north")):
                if cap is not None and y + 1 > cap:
                    c.slab(x, cap, z, "spruce_slab")
                    continue
                c.set(x, y, z, ROOF + "_steep_roof", facing=f, half="bottom", shape="straight")
                c.set(x, y + 1, z, ROOF + "_upper_steep_roof", facing=f, half="bottom", shape="straight")
            for z in range(z0 + k + 1, z1 - k):
                for yy in (y, y + 1):
                    if cap is not None and yy >= cap:
                        c.slab(x, cap, z, "spruce_slab")
                        continue
                    c.set(x, yy, z, (POST if (z + yy) % 3 == 0 else INFILL) if x == x0 else FILL)
    ridge = y0 + 2 * runs
    if cap is None:
        for x in range(x0, x1 + 1):
            for z in range(z0 + runs, z1 - runs + 1):
                c.set(x, ridge - 2, z, POST if x == x0 else FILL)
                c.set(x, ridge - 1, z, POST if x == x0 else FILL)
                c.slab(x, ridge, z, "spruce_slab")
    return ridge if cap is None else cap


# ================================================================================================= the gallery
def gallery(c):
    """The south gallery (kept from the control): x 1..9, z 14..16, y 1..6, an arcade of dark bays on its camera face
    (piers at x 1, 4, 7; bays x 2..3, 5..6, 8..9 one deep), a string course at y 6, a slab roof at y 7."""
    for x in range(1, 10):
        for z in range(14, 17):
            for y in range(1, 7):
                c.set(x, y, z, PLINTH if y == 1 else MOSSY if hsh(x, y, z) % 3 == 0 else STONE)
            c.slab(x, 7, z, GALLERY_TOP)
    for x in range(1, 10):
        if x in (1, 4, 7):
            continue
        for y in (3, 4):                                      # windows, not tall arches (round 2: the
            c.clear(x, y, 16)                                 # arcade competed with the gate's darkness)
        c.stair(x, 5, 16, "west" if x in (2, 5, 8) else "east", "top")       # the bay's arched head
        c.set(x, 6, 16, STRING, facing="south", shape="straight")
    for x in (1, 4, 7):
        c.set(x, 6, 16, CORNICE, facing="south", shape="straight")
    for z in range(14, 17):
        c.set(0, 6, z, STRING, facing="west", shape="straight")


# ================================================================================================= the great tree
def _path(keys, ys):
    """The trunk's centre and radius per row, interpolated between key rows (a smooth taper and lean: round 1's
    stepped radii read as "a staircase or a crate pile")."""
    out = []
    for y in ys:
        for (ya, xa, za, ra), (yb, xb, zb, rb) in zip(keys, keys[1:]):
            if ya <= y <= yb:
                t = (y - ya) / (yb - ya)
                out.append((y, xa + (xb - xa) * t, za + (zb - za) * t, ra + (rb - ra) * t))
                break
    return out


TRUNK_KEYS = [(1, 13.0, 12.2, 3.1), (4, 13.0, 11.3, 2.6), (8, 12.4, 9.6, 2.1), (13, 12.0, 8.0, 1.9),
              (17, 11.0, 6.8, 1.7), (22, 10.3, 6.6, 1.4)]
TRUNK_PATH = _path(TRUNK_KEYS, range(1, 13))
TRUNK_L3 = _path(TRUNK_KEYS, range(13, 23))


def trunk(c, path, twist=0.28):
    """The trunk: solid, light bark (stripped birch) with dark oak in two spiral grooves turning with height."""
    for (y, cx, cz, r) in path:
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            for z in range(int(cz - r - 1), int(cz + r + 2)):
                if math.hypot(x - cx, z - cz) <= r:
                    a = math.atan2(z - cz, x - cx) / (2 * math.pi) * 2 + y * twist / (2 * math.pi)
                    c.set(x, y, z, GROOVE if (a % 1.0) < 0.22 else BARK)


def line(c, p0, p1, name=BARK, fill_down=False):
    """A limb or buttress root: a face-connected line of wood; fill_down drops a root wall under its low half;
    mangrove roots where it grips the ground."""
    (x0, y0, z0), (x1, y1, z1) = p0, p1
    n = max(abs(x1 - x0), abs(z1 - z0), abs(y1 - y0), 1)
    prev = None
    for k in range(n + 1):
        t = k / n
        q = (round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * (t ** 0.7 if fill_down else t)),
             round(z0 + (z1 - z0) * t))
        steps = [q]
        if prev is not None:
            a, steps = list(prev), []
            for i in (0, 2, 1):
                if a[i] != q[i]:
                    a[i] = q[i]
                    steps.append(tuple(a))
        for s in steps:
            c.set(*s, name)
            if fill_down and t > 0.5:
                for yy in range(1, s[1]):
                    c.put(s[0], yy, s[2], GRIP if yy == 1 else name)
        prev = q


def canopy(c, cx, cz, y0, y1, r, seed=0, keep=lambda x, y, z: True, valleys=True, shaft=False):
    """One canopy tier: a broad irregular disc, the belly (y0) spruce, the top all cherry (round 1's scattered spruce
    gaps read as "spots", "a chequer"), clumps with one-deep valleys between them on 3-thick tiers (shadow, not a
    lid), drips of spruce under the rim; the top steps in by a block (a rounded edge)."""
    rr = int(r) + 2
    for x in range(int(cx) - rr, int(cx) + rr + 1):
        for z in range(int(cz) - rr, int(cz) + rr + 1):
            if (x, z) in WELL and not (shaft and (x, z) != SPAWN):
                continue                                      # shaft: only the 1 x 1 spawn column stays open
            d = math.hypot(x - cx, z - cz)
            wob = 0.7 * math.sin(math.atan2(z - cz, x - cx) * 3 + seed) + (hsh(x, z, seed) % 3 - 1) * 0.3
            for y in range(y0, y1 + 1):
                rim = r + wob - (1.0 if y == y1 and y1 > y0 else 0)
                if d > rim or not keep(x, y, z):
                    continue
                if valleys and y == y1 and y1 - y0 >= 2 and hsh(x // 2, z // 2, seed + 13) % 3 == 0:
                    continue                                  # a valley between clumps (shadow), cherry below it
                under = y == y0 and y1 > y0 and d <= r + wob - 2.0      # deep under the top: the dark belly
                nm = BELLY if under else TOP
                c.put(x, y, z, nm, **LEAF)
            if r + wob - 1.0 < d <= r + wob and hsh(x, z, seed + 3) % 3 == 0 and keep(x, y0 - 1, z):
                c.put(x, y0 - 1, z, TOP, **LEAF)             # cherry drips (round 2: green bands)


def smooth_grooves(c):
    """Each column of bark keeps a tone for >= 4 rows (a groove or a bark run shorter than that takes the tone
    below it): the spiral reads as long seams, never as bands (round 1: "candy-striped poles")."""
    cols = {}
    for (x, y, z), (n, _) in c.b.items():
        if n in (BARK, GROOVE):
            cols.setdefault((x, z), []).append(y)
    for (x, z), ys in cols.items():
        ys.sort()
        prev, run = None, []
        for y in ys:
            n = c.b[(x, y, z)][0]
            if prev is not None and y == run[-1] + 1 and n != prev and len(run) < 4:
                c.b[(x, y, z)] = (prev, {})
                n = prev
            if n != prev or (run and y != run[-1] + 1):
                run = []
            run.append(y)
            prev = n


def bark_tops(c):
    """Every bark cell open to the sky shows end grain / grey bark (oak log, up 0.33, sat 0.30) instead of the
    stripped bark's sunlit top (stripped oak 0.63, sat 0.46 - a loud tan from above; stripped birch 0.62)."""
    for (x, y, z), (n, p) in list(c.b.items()):
        if n == BARK and (x, y + 1, z) not in c.b and y <= 13:    # the limb keeps its pale top (round 2: log
            # tops on its diagonal read as "a staircase of slabs")
            c.b[(x, y, z)] = ("oak_log", {"axis": "y"})


def great_tree(c, l3):
    trunk(c, TRUNK_PATH)
    # roots: one pours toward the camera over the gallery roof and down its south face (round 2: one, not three -
    # the trunk and the great limb's aerial roots carry the read), one flares south, one east, one west to the
    # well's edge, one north
    line(c, (12, 6, 14), (10, 8, 16))
    for y in range(1, 8):
        c.set(10, y, 16, GRIP if y == 1 else BARK)
    for p0, p1 in (((14, 5, 14), (15, 1, 16)), ((15, 5, 11), (16, 1, 8)), ((10, 4, 11), (9, 1, 12)),
                   ((12, 6, 8), (12, 1, 4)), ((15, 4, 13), (16, 1, 15))):
        line(c, p0, p1, fill_down=True)
    for x in (13, 14):                                       # the heart-glow in the trunk's south foot, where the
        for y in (1, 2):                                     # cameras look (round 1's faced the well: never seen)
            c.set(x, y, 15, GLOW)
            c.clear(x, y, 16)
    canopy(c, 12.0, 8.0, 13, 15, 3.6, seed=1, valleys=False)  # the L1 tier (L3 keeps it; round 2's valleys: a ring)
    if not l3:
        return
    trunk(c, TRUNK_L3)
    # the great limb (ref06's wrap and crescent): out of the trunk, arching over the well and onto the hall's ridge
    # with two cells of sky between it and the roof, its tip curling down onto the west end of the ridge (round 1:
    # "the tree does not wrap the hall", "no sky between limb and hall")
    line(c, (11, 14, 7), (9, 17, 7))
    line(c, (11, 15, 7), (9, 18, 7))
    line(c, (11, 16, 8), (9, 19, 7))
    line(c, (9, 19, 7), (8, 20, 7))
    for z in (7, 8):
        line(c, (7, 20, z), (4, 20, z))
    line(c, (7, 21, 7), (5, 21, 7))
    line(c, (4, 20, 8), (3, 19, 8))
    c.set(2, 18, 8, BARK)
    line(c, (11, 19, 6), (14, 21, 8))                        # the east bough under tier 2
    sky = lambda x, y, z: not (x <= 7 and y < 21)            # noqa: E731  - leave the air over the hall open
    canopy(c, 12.5, 6.5, 17, 18, 6.2, seed=2, keep=sky)
    canopy(c, 9.5, 7.5, 21, 23, 5.8, seed=3, shaft=True, valleys=False)  # over the hall and the limb: canopy and roof
    #                                                        interlock; no valleys in the crown (they read as a lattice);
    canopy(c, 10.0, 6.5, 24, 25, 3.0, seed=4, shaft=True)    # the crown closed over the well but the spawn shaft
    for (x, z) in ((11, 5), (12, 5), (11, 6)):
        c.put(x, 25, z, TOP, **LEAF)


# ================================================================================================= relief
def relieve(c):
    """The masons' pass on the camera faces: every third cell along a built wall row (west faces: z % 3 == 0; south
    faces: x % 3 == 0) that shows exactly one camera face is set back as an upside-down stair (its open quarter a
    shadow line) - a bay rhythm, so no row runs more than 2 full blocks."""
    def open_(x, y, z):
        return c.get(x, y, z) == "air" or c.get(x, y, z).endswith(TREE)
    for (x, y, z), (n, p) in list(c.b.items()):
        if n not in STAIR_OF or y < 1:
            continue
        w = x == 0 or open_(x - 1, y, z)
        s = z == SZ - 1 or open_(x, y, z + 1)
        if w == s:
            continue
        if (z if w else x) % 3 == 0:
            c.b[(x, y, z)] = (STAIR_OF[n], dict(facing="east" if w else "north", half="top", shape="straight", **DRY))


# ================================================================================================= assembly
def draw(l3=False):
    c = Canvas(H3 if l3 else H1)
    ground(c)
    hall(c, l3)
    gallery(c)
    great_tree(c, l3)
    smooth_grooves(c)
    bark_tops(c)
    relieve(c)
    prune(c)
    return c


def prune(c):
    """Drops what a clipped part leaves touching nothing."""
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
    r.check_support("verdant rooting hollow")
    return r


def seal_pockets(c, h, out):
    """Ground pockets left between buttress roots and the masses are filled with roots: audit a draft, fill each
    pocket cell, repeat until none."""
    tmp = os.path.join(out, ".verdant_draft.nbt")
    for _ in range(6):
        r = rig_of(c, h)
        r.size = (SX, max(y for (_, y, _) in r.b) + 1, SZ)
        r.structure().write(tmp)
        one_way, grnd, roof, rooms, note = ap.audit("draft", tmp)
        if not grnd and not one_way:
            break
        for q in grnd + one_way:
            c.set(*q, GRIP)
    os.remove(tmp)


def check(r, path, tag, maxh):
    n = {p: v[0] for p, v in r.b.items()}
    top = max(p[1] for p in n)
    assert top < maxh, "%s taller than %d (%d)" % (tag, maxh, top + 1)
    assert all((x, 0, z) in n for x in range(SX) for z in range(SZ)), "hole in the ground layer"
    busy = [p for p in LANE if p in n]
    assert not busy, "lane blocked: %s" % busy[:8]
    col = [p for p in n if (p[0], p[2]) == SPAWN and p[1] >= 1]
    assert not col, "%s: the spawn column is not open: %s" % (tag, col)
    lone = [(p, n[p]) for p in n if p[1] > 0 and not any(
        (p[0] + dx, p[1] + dy, p[2] + dz) in n for dx, dy, dz in
        ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)))]
    assert not lone, "%s floating %s" % (tag, lone[:10])
    for p, (nm, props) in r.b.items():
        if nm.endswith("_leaves"):
            assert props.get("persistent") == "true" and props.get("distance") in "1234567"
    one_way, grnd, roof, rooms, note = ap.audit(tag, path)
    assert not one_way and not grnd and "TRAPPED" not in note, (tag, one_way[:8], grnd[:8], note)
    tree = max(p[1] for p, v in n.items() if v.endswith(TREE) or v == GRIP)
    built = max(p[1] for p, v in n.items() if not (v.endswith(TREE) or v == GRIP))
    return "%s 17x%dx17, %d blocks: lane + spawn column clear, no pockets (%d roof-only)%s | tree %d, built %d" % (
        tag, top + 1, len(n), len(roof), note, tree, built)


def build(level=3):
    """The rig of one level (1 or 3), sealed and sized - what main() writes (the camera tests build from this)."""
    import tempfile
    l3 = level == 3
    c, h = draw(l3), (H3 if l3 else H1)
    with tempfile.TemporaryDirectory() as tmp:
        seal_pockets(c, h, tmp)
    r = rig_of(c, h)
    r.size = (SX, max(y for (_, y, _) in r.b) + 1, SZ)
    return r


def main():
    out = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else OUT)
    os.makedirs(out, exist_ok=True)
    r1, r3 = build(1), build(3)
    lost = [p for p in r1.b if p not in r3.b]
    assert not lost, "L3 removes L1 cells: %s" % lost[:10]
    for r, tag, h in ((r1, "verdant_l1", H1), (r3, "verdant_l3", H3)):
        path = os.path.join(out, tag + ".nbt")
        r.structure().write(path)
        print(check(r, path, tag, h))


if __name__ == "__main__":
    main()
