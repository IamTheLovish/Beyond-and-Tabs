#!/usr/bin/env python3
"""The Ashborn VEHICLE LAB - THE EMBER PENS (L1 and L3), the r12 method's rebuild (Oct 31 2026).

Rounds 1 and 2 (Oct 28) scored 3/10 twice: no landmark (the giant a lump / a pillar with copper blobs), no readable
gate, a cube not a stepped temple, glow scattered everywhere, basalt / deepslate / blackstone one grey, rubbly ground.
KEEP: the ordered basalt-column wall and the beast-pen row with dark pilasters. The round-2 lab stays as the control
(design/vehicle_labs/ashborn_l3_round2.nbt / _l1_round2.nbt; its generator is this file's git history before Oct 31).

THE REFERENCE (designer pass itachi2-api/runs/20261031-001548-ashborn-design): ref29, a dark citadel in stepped tiers
shot from our camera. Its tiers read from above because every tier edge carries a LIGHT cap over a dark wall; its
glow lives only in sunken courts; its plinth is a striated cliff (our basalt columns); one keep under a dark cone
leads. ref25 lends the gate: basalt columns of varied heights rising to a point over the mouth.

THE MASSING (blind greybox pick, eyes 41168313494: both fresh critics ranked ref29's citadel A first, B the volcano
second, the round-2 control and the giant last) with the critics' shared faults folded in (A2):
  - the KEEP on the gate's axis (x 11..15, z 6..10) instead of the back corner: gate -> lane -> court -> forge -> keep
    in one line; its crown a light corbelled parapet round a 5 x 5 dark cone, a needle to 25;
  - THREE tiers, not two: the plinth y 1..6 (cap 7), the ring x 2..14 z 2..14 to 11 (cap 12), the keep's terrace
    x 11..15 z 4..12 to 14 (cap 15);
  - the gate crest a pointed POLISHED-BASALT pylon two columns deep (x 0..1) - not a stair climbing onto the roof;
  - a black lane floor (the darkest stone, blackstone) and a lighter court floor, so the gate is the darkest mouth.

VALUE ROLES (designer bands, measured on camera, tools/camera_colour.py): plinth / pens / crest polished basalt
(0.21 west / 0.28 south) with dark column grooves - band 0.22-0.32 (plain basalt's west face reads 0.18, too dark);
tier walls deepslate tiles (0.13 / 0.18) - band 0.12-0.20; cones Macaw's deepslate steep roofs (0.18-0.20) - band
0.15-0.25; terrace paving tuff (up 0.34) - band 0.32-0.44; caps andesite slabs (up 0.55) - band 0.48-0.58. ONE glow:
the forge (magma behind iron bars) in the keep's foot, facing the gate across the court. No copper, no polished
blackstone (purple-grey, it merges with the deepslate), no calcite / diorite (snow) - by look.

ANCHORS (structure-local, x 0..16 west -> east, z 0..16 north -> south, y 0 = ground layer):
  exit lane   x 0..8, z 6..10, y 1..8 air (the 5 x 8 gate out of the west face, centred on z 8)
  spawn       (8, 1, 8) in the open court x 6..10, z 6..10; the column (8, 1..top, 8) is air at L1 and L3
  forge heart x 0, z 7..9, y 10..13 (a recess), the magma behind at x 1 - over the gate, the one glow the cameras see
  court forge x 11, z 7..9, y 1..3 (bars), the magma behind at x 12 - at the keep's foot, on the same axis

L1 (<= 16): the plinth with the pens, the ring, the gatehouse with a lower crest, the keep's terrace and the keep's
stump under a light cap. L3 (<= 26, adds or replaces only): the crest grows to 17, the keep to its crown and needle,
the two coned towers (NW x 1..3 z 1..3, SE x 13..15 z 13..15).

Usage: python3 tools/lab_vehicle_ashborn.py [OUT_DIR]   (default design/vehicle_labs) -> ashborn_l1.nbt, ashborn_l3.nbt
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs  # noqa: E402

SX = SZ = 17
H_L1, H_L3 = 16, 26
LANE_X, LANE_Z, LANE_H = range(0, 9), range(6, 11), 8
SPAWN = (8, 1, 8)
COURT = [(x, z) for x in range(6, 11) for z in range(6, 11)]
FORECOURT = [(x, z) for x in range(3, 6) for z in range(6, 11)]   # the lane open to the sky behind the gatehouse
OUT_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "design", "vehicle_labs")

PLINTH = "polished_basalt"
WALL = "deepslate_tiles"
PAVE = "stone_bricks"                       # up 0.41 (tuff 0.34 sat on the band's floor once the joints' backs showed)
CAP = "andesite_wall"                      # a parapet: half the top area of a slab (round-1 model: slab caps = 20 % of the rts pixels, band 4-12 %)
CORBEL = "polished_deepslate_stairs"
ROOF = "mcwroofs:deepslate"
NEEDLE = "polished_deepslate_wall"
LANE_FLOOR = "blackstone"
COURT_FLOOR = "polished_deepslate"
FLOOR = "polished_deepslate"
GLOW = "magma_block"
BARS = "iron_bars"
HIDE = "black_wool"
PEN_POST = "deepslate_tiles"               # KEEP: the dark pilasters between the stalls
DRY = dict(waterlogged="false")
POST = dict(up="true", north="none", south="none", east="none", west="none", **DRY)
AXIAL = (PLINTH, "basalt")


class Grid:
    def __init__(self, h):
        self.h = h
        self.b = {}

    def set(self, x, y, z, name, **props):
        assert 0 <= x < SX and 0 <= z < SZ and 0 <= y < self.h, ((x, y, z), name)
        if name in AXIAL and "axis" not in props:
            props = dict(props, axis="y")
        self.b[(x, y, z)] = (name, props)

    def get(self, x, y, z):
        return self.b.get((x, y, z), ("air",))[0]

    def clear(self, x, y, z):
        self.b.pop((x, y, z), None)

    def box(self, x0, x1, y0, y1, z0, z1, name, **props):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.set(x, y, z, name, **props)

    def stair(self, x, y, z, facing, half="bottom", name=CORBEL):
        self.set(x, y, z, name, facing=facing, half=half, shape="straight", **DRY)

    def slab(self, x, y, z, name=CAP, kind="bottom"):
        if name.endswith("_wall"):
            self.set(x, y, z, name, **POST)
        else:
            self.set(x, y, z, name, type=kind, **DRY)

    def steep(self, x, y, z, facing, shape="straight"):
        self.set(x, y, z, ROOF + "_steep_roof", facing=facing, half="bottom", shape=shape)
        self.set(x, y + 1, z, ROOF + "_upper_steep_roof", facing=facing, half="bottom", shape=shape)


def rect(x0, x1, z0, z1):
    return [(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)]


def outer(x0, x1, z0, z1):
    return [(x, z) for (x, z) in rect(x0, x1, z0, z1) if x in (x0, x1) or z in (z0, z1)]


def ring_cells(x0, x1, z0, z1):
    out = []
    for (x, z) in outer(x0, x1, z0, z1):
        if x in (x0, x1) and z in (z0, z1):
            f = "east" if x == x0 else "west"
            toward = "south" if z == z0 else "north"
            right = {"east": "south", "west": "north"}[f]
            out.append(((x, z), f, "outer_right" if right == toward else "outer_left"))
        elif x in (x0, x1):
            out.append(((x, z), "east" if x == x0 else "west", "straight"))
        else:
            out.append(((x, z), "south" if z == z0 else "north", "straight"))
    return out


def cone(g, x0, x1, z0, z1, y, needle_to):
    """A dark staged cone: rings of Macaw's steep roof closing 1 per side every 2 rows, a needle on the last cell."""
    while x1 - x0 >= 2 and z1 - z0 >= 2:
        for (x, z), f, sh in ring_cells(x0, x1, z0, z1):
            g.steep(x, y, z, f, sh)
        for x in range(x0 + 1, x1):
            for z in range(z0 + 1, z1):
                g.set(x, y, z, WALL)
                g.set(x, y + 1, z, WALL)
        x0, x1, z0, z1, y = x0 + 1, x1 - 1, z0 + 1, z1 - 1, y + 2
    for yy in range(y, needle_to + 1):
        g.set(x0, yy, z0, NEEDLE, **POST)


def terrace(g, cells, y_top, edge, stone, cap=CAP, rim=()):
    """A tier's top: tuff paving at y_top on its inner cells (the edge cells keep their wall stone under the cap, so
    no tuff shows on a face), a light cap (slab) on y_top + 1 along its edge cells."""
    edge = set(edge)
    rim = set(rim)
    for (x, z) in cells:
        g.set(x, y_top, z, stone if (x, z) in edge or (x, z) in rim else PAVE)
    for (x, z) in edge:
        g.slab(x, y_top + 1, z, cap)


def sw_edge(x0, x1, z0, z1):
    """The west and south edges of a rectangle: the edges the RTS camera sees against the shadowed face below (the
    north and east edges stay uncapped: a full light ring put the caps at 26 % of the rts pixels, the designer's
    band for the lights is 4-12 %)."""
    return [(x, z) for (x, z) in rect(x0, x1, z0, z1) if x == x0 or z == z1]


# ============================================================================================ the masses
def ground(g):
    g.box(0, 16, 0, 0, 0, 16, FLOOR)
    for x in LANE_X:
        for z in LANE_Z:
            g.set(x, 0, z, LANE_FLOOR)
    for (x, z) in COURT:
        g.set(x, 0, z, COURT_FLOOR)


def plinth(g):
    """T1: the basalt-column cliff over the whole plot, y 1..6, the court cut out; the tuff terrace ring and a light
    cap on its outer edge."""
    for (x, z) in rect(0, 16, 0, 16):
        if (x, z) in COURT:
            continue
        for y in range(1, 7):
            g.set(x, y, z, PLINTH)
    ring_ = [c for c in rect(0, 16, 0, 16) if not (2 <= c[0] <= 14 and 2 <= c[1] <= 14)]
    terrace(g, ring_, 6, sw_edge(0, 16, 0, 16), PLINTH)


def ring(g):
    """T2: the set-back ring x 2..14 z 2..14, y 7..11, round the court; tuff on top, a cap on its outer edges and
    round the court's rim."""
    cells = [c for c in rect(2, 14, 2, 14) if c not in COURT and c not in FORECOURT]
    for (x, z) in cells:
        for y in range(7, 11):
            g.set(x, y, z, WALL)
    terrace(g, cells, 11, sw_edge(2, 14, 2, 14), WALL, rim=[(x, 5) for x in range(3, 11)])    # the court's rim stays uncapped: a light ring round the
    #                                                     court read as the biggest light area (massing critic b)


def keep_terrace(g):
    """T3: the keep's terrace x 11..15 z 4..12, y 12..14 (set back 2 from the ring's south edge), its cap."""
    cells = rect(11, 15, 4, 12)
    for (x, z) in cells:
        for y in range(12, 14):
            g.set(x, y, z, WALL)
    terrace(g, cells, 14, [(x, 12) for x in range(11, 16)], WALL, rim=[(11, z) for z in (4, 5, 11)])   # its south edge; the west side is the keep's


def keep(g, l3):
    """THE KEEP (landmark) x 11..15 z 6..10 on the gate's axis, from the ground (its west face is the court's east
    wall, the forge at its foot). L1: a stump to 14 under a light cap (15). L3: the shaft to 17, a projecting
    deepslate-tile band 7 x 7 (y 18, machicolation gaps on the seen sides) under a light parapet (y 19, the seen
    sides), the 5 x 5 dark cone (19..22) and a needle to
    25."""
    top = 18 if l3 else 14
    for (x, z) in rect(11, 15, 6, 10):
        for y in range(1, top + 1):
            g.set(x, y, z, WALL)
    if not l3:
        for (x, z) in outer(11, 15, 6, 10):
            g.slab(x, 15, z)
        return
    for (x, z) in outer(10, 16, 5, 11):
        seen_side = x == 10 or z == 11          # the west and south sides: the camera's
        if (x + z) % 2 or not seen_side:
            g.set(x, 18, z, WALL)
        # else: a machicolation - a gap under the parapet between the band's blocks (relief by depth, no thin block)
        if seen_side and (x, z) not in ((10, 5), (16, 11)):
            g.slab(x, 19, z)                    # the light parapet on the seen sides, stopping short of the far
            #                                     corners (the 3/4 still's thin-block budget on the tops: 45 %)
    cone(g, 11, 15, 6, 10, 19, 25)


def gatehouse(g, l3):
    """THE GATE (ref25): a pylon of polished basalt columns over the 5 x 8 mouth (z 6..10). Organ pipes: the proud
    columns (x 0) at z 4, 6, 8, 10, 12 rise to a point on the gate's axis (11 / 14 / 17 / 14 / 11), the columns
    between them stand one back (x 1) at 12 / 16 / 16 / 12, so the crest is columns of varied heights and depths, not
    a wall; the jambs z 4..5 / 11..12 full height; a chamfered head over the mouth (upside-down stairs, y 9); the
    FORGE HEART (heart()) between the z 6 and z 10 columns. Behind it the gatehouse x 2 at the ring's height (tuff top).
    L1's crest stops at 14; L3 adds to 17."""
    proud = {4: 11, 6: 14, 8: 17, 10: 14, 12: 11}
    back = {4: 11, 5: 12, 6: 14, 7: 16, 8: 16, 9: 16, 10: 14, 11: 12, 12: 11}
    lim = 17 if l3 else 14
    for z in range(4, 13):
        top = min(back[z], lim)
        for y in range(9, top + 1):
            g.set(1, y, z, PLINTH)
        if z not in LANE_Z:
            for y in range(1, 9):
                g.set(1, y, z, PLINTH)
                g.set(0, y, z, PLINTH)
        if z not in proud:
            g.slab(1, top + 1, z)               # capped where it shows between the proud columns
        if z in (7, 8, 9):
            g.set(0, 9, z, GLOW)                # the heart's glowing floor = the gate's head: light pours out
        elif z in LANE_Z:
            g.stair(0, 9, z, "east", "top", CORBEL)
        elif z in proud:
            g.set(0, 9, z, PLINTH)
        else:
            g.set(0, 9, z, PLINTH)
    for z, h in proud.items():
        top = min(h, lim)
        for y in range(10, top + 1):
            g.set(0, y, z, PLINTH)
        g.slab(0, top + 1, z)
    for z in (7, 9):
        g.stair(0, 14, z, "east", "top", CORBEL)
        g.slab(0, 15, z)
    for z in range(4, 13):
        for y in range(7 if z not in LANE_Z else 9, 12):
            g.set(2, y, z, WALL)
        g.set(2, 11, z, PAVE)


def towers(g):
    """Two small coned towers (L3; ref29's dark cones), subordinate: NW x 1..3 z 1..3 (to 16), SE x 13..15 z 13..15
    (to 15)."""
    g.box(1, 3, 7, 12, 1, 3, WALL)
    cone(g, 1, 3, 1, 3, 13, 16)
    g.box(13, 15, 7, 11, 13, 15, WALL)
    cone(g, 13, 15, 13, 15, 12, 15)
    for y in range(8, 12):                  # a dark arrow slit (a 1-deep recess) in each seen face
        g.clear(1, y, 2)
        g.clear(2, y, 3)
    for y in range(8, 11):
        g.clear(13, y, 14)
        g.clear(14, y, 15)


def pens(g):
    """KEEP: the beast-pen row in the south face (z 16), y 1..3 barred stalls (black hides behind) two wide between
    dark deepslate-tile pilasters at x 2, 5, 8, 11, 14, a light upside-down stair lintel over each stall (y 4)."""
    for x in range(1, 16):
        if x in (2, 5, 8, 11, 14):
            for y in range(1, 5):
                g.set(x, y, 16, PEN_POST)
            continue
        for y in (1, 2, 3):
            g.set(x, y, 16, BARS, **DRY)
            g.set(x, y, 15, HIDE)
        g.stair(x, 4, 16, "north", "top", "polished_andesite_stairs")    # a light lintel over each stall


def heart(g):
    """THE FORGE HEART over the gate (ref24's one glowing core over the mouth): a 3 x 3 recess 1 deep in the crest's
    face (x 0, z 7..9, y 10..13, between the z 6 and z 10 columns, under the chamfered heads at y 14) with the magma
    at its back (x 1) - the glow the cameras see (the court forge at the
    bottom of an 11-deep shaft is never seen from the RTS or 3/4 camera: 0 pixels in the camera model; iron bars in
    front of it read as an opaque plane in the model)."""
    for z in (7, 8, 9):
        for y in (10, 11, 12, 13):
            g.clear(0, y, z)
            g.set(1, y, z, GLOW)


def forge(g):
    """The forge mouth in the keep's foot facing the gate across the court (it lights the court and the lane at
    dusk): iron bars at x 11, magma behind at x 12, z 7..9, y 1..3, under a corbelled lintel."""
    for z in (7, 8, 9):
        for y in (1, 2, 3):
            g.set(11, y, z, BARS, **DRY)
            g.set(12, y, z, GLOW)
        g.stair(11, 4, z, "east", "top", CORBEL)


def grooves(g):
    """THE CAUSEWAY'S JOINTS (KEEP: the ordered basalt-column wall) and the tier walls' rhythm: on every face the
    cameras see (west: air at x - 1; south: air at z + 1), every fourth block along the face is cut back one block -
    a deep joint between basalt columns, a slot in the deepslate - so no run passes 3 blocks and the relief is real
    depth, not more thin blocks (round-1 model: wall-post grooves put the faces' thin-block share at 50 %)."""
    occ = dict(g.b)
    for (x, y, z), (n, _) in sorted(occ.items()):
        if y < 1 or n not in (PLINTH, WALL) or (x <= 1 and 4 <= z <= 12 and (y >= 9 or z in (4, 5, 11, 12))):
            continue                            # the gate pylon and its jambs have their own rhythm (organ pipes)
        for (dx, dz), k in (((-1, 0), z), ((0, 1), x)):
            nx, nz = x + dx, z + dz
            if 0 <= nx < SX and 0 <= nz < SZ and (nx, y, nz) in occ:
                continue
            if k % 4 == 2 and (x - dx, y, z - dz) in occ:
                g.clear(x, y, z)
                if g.get(x - dx, y, z - dz) == PAVE:  # the joint's back is wall stone, not the terrace's tuff
                    g.set(x - dx, y, z - dz, n)
                break


def clear_lane_and_spawn(g):
    for x in LANE_X:
        for z in LANE_Z:
            for y in range(1, LANE_H + 1):
                g.clear(x, y, z)
    for y in range(1, g.h):
        g.clear(SPAWN[0], y, SPAWN[2])


def draw(level):
    l3 = level == 3
    g = Grid(H_L3 if l3 else H_L1)
    ground(g)
    plinth(g)
    ring(g)
    keep_terrace(g)
    keep(g, l3)
    gatehouse(g, l3)
    if l3:
        towers(g)
    pens(g)
    forge(g)
    heart(g)
    clear_lane_and_spawn(g)
    grooves(g)
    return g


# ============================================================================================ build + check
def to_rig(g):
    h = max(y for (_, y, _) in g.b) + 1
    rig = gs._SunRig(SX, h, SZ)
    for (x, y, z), (n, props) in g.b.items():
        rig.set(x, y, z, n, **props)
    return rig


def levels():
    """(L1 grid, L3 grid): L3 keeps every L1 cell non-air (it only adds or replaces)."""
    g1 = draw(1)
    g3 = draw(3)
    for q, v in g1.b.items():
        if q not in g3.b:
            g3.b[q] = v
    return g1, g3


def build_level(level):
    """The rig of one level, written nowhere (tools/test_ashborn_lab.py builds the lab through this)."""
    g1, g3 = levels()
    rig = to_rig(g1 if level == 1 else g3)
    rig.settle()
    return rig


def floating(n):
    todo = [q for q in n if q[1] == 0]
    seen = set(todo)
    while todo:
        x, y, z = todo.pop()
        for a, b, c in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            q = (x + a, y + b, z + c)
            if q in n and q not in seen:
                seen.add(q)
                todo.append(q)
    return sorted(q for q in n if q not in seen)


BANNED = ("gold", "gilded", "nether_brick", "crimson", "warped", "netherrack", "banner", "piglin", "lava", "copper",
          "calcite", "diorite", "quartz", "polished_blackstone", "soul_")


def check(rig, path, tag, h_max):
    import audit_structure_pockets as ap
    n = {k: v[0] for k, v in rig.b.items()}
    assert rig.size[1] <= h_max, (tag, rig.size)
    assert all((x, 0, z) in n for x in range(SX) for z in range(SZ)), "a hole in the foundation"
    busy = [(x, y, z, n[(x, y, z)]) for x in LANE_X for z in LANE_Z for y in range(1, LANE_H + 1) if (x, y, z) in n]
    assert not busy, "%s: the exit lane is narrowed: %s" % (tag, busy[:8])
    col = [y for y in range(1, rig.size[1]) if (SPAWN[0], y, SPAWN[2]) in n]
    assert not col, "%s: the spawn column is blocked at %s" % (tag, col)
    assert not floating(n), "%s: floating %s" % (tag, floating(n)[:8])
    bad = sorted({v for v in n.values() if any(f in v for f in BANNED)})
    assert not bad, "forbidden: %s" % bad
    glow_top = [q for q, v in n.items() if v == GLOW and not any((q[0], y, q[2]) in n for y in range(q[1] + 1, rig.size[1]))]
    assert not glow_top, "glow open to the sky %s" % glow_top
    one_way, ground_, roof, rooms, note = ap.audit(tag, path)
    assert not one_way and not ground_ and "TRAPPED" not in note, (one_way[:8], ground_[:8], note)
    pad = ap.Pad(ap.load_nbt(path))
    seen, stack, out_west = {SPAWN}, [SPAWN], False
    while stack:
        c = stack.pop()
        if c[0] < 0 and c[2] in LANE_Z:
            out_west = True
            break
        for nb in ap.neighbours(pad, *c):
            if nb not in seen and -3 <= nb[0] <= SX + 2 and -3 <= nb[2] <= SZ + 2 and 1 <= nb[1] <= rig.size[1]:
                seen.add(nb)
                stack.append(nb)
    assert out_west, "the spawn does not walk out of the west face"
    return "%s %s, %d blocks: lane clear, spawn column clear, nothing floats, no pockets (%d roof-only cells)%s" % (
        tag, "x".join(map(str, rig.size)), len(n), len(roof), note)


def build(out_dir=OUT_DIR):
    os.makedirs(out_dir, exist_ok=True)
    g1, g3 = levels()
    out = []
    for g, name, hm in ((g1, "ashborn_l1", H_L1), (g3, "ashborn_l3", H_L3)):
        rig = to_rig(g)
        rig.settle()
        path = os.path.join(out_dir, name + ".nbt")
        rig.structure().write(path)
        out.append(check(rig, path, name, hm))
    lost = [q for q in g1.b if q not in g3.b]
    assert not lost, "L3 drops L1 cells: %s" % lost[:8]
    print("\n".join(out))


if __name__ == "__main__":
    build(sys.argv[1] if len(sys.argv) > 1 else OUT_DIR)
