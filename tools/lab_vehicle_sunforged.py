#!/usr/bin/env python3
"""The Sunforged (White Angel) VEHICLE LAB - THE SUNWAIN COURT - at L1 and L3 (round 2).
Here the DAWNRIDER (a light-wielder riding a cloud, a fast raider) is made: a gothic cathedral-workshop whose west bay
is open to the sky - the cloud is spun in a cradle hung from a dark-oak gantry, under the ribbed dome where the sun is
kept - then lowered and ridden out of the great west portal.

Round 1 scored 4/10: "a dense grey box with ideas stuck onto it" - no landmark, a weak gate, no rhythm, a brown blob on
the roof, mixed materials, messy ground. Round 2 is built on HIERARCHY (brief amendment, item 2): (a) ONE landmark,
the ribbed dome with the sun on its crown (L3: the ribbed lantern spire rising from it to the sun disc), every other
top a small pinnacle stepping up to it; (b) the EXIT GATE as the biggest, darkest opening on the west face, a deep
three-order portal under a crocketed wimperg that breaks the cornice, between two gate towers; (c) the secondary
masses stepping down - the gate towers, the nave with its flying buttresses, the lodge lean-to; (d) a calm paved
forecourt. Four material zones: a DEEPSLATE plinth, warm STONE-BRICK ashlar with lighter clasping buttresses, QUARTZ
+ GOLD crown and trim, DEEP-SLATE tile roofs, teal-blue glass; warm light only from the lancets, the gate and the sun.
Writes design/vehicle_labs/sunforged_l1.nbt and _l3.nbt only - NOT into the game's structure trees.

THE REFERENCE, STUDIED
  - LOVISH'S CASTLE ON THE CRAG (design/buildings/white_angel_capitol_v4.png, tools/capitol_white_angel_v4.py): warm
    light grey ashlar, dark slate roofs, gold finials; every wall broken into bays by buttresses ending in pinnacles;
    a cluster that climbs to one needle; warm light in deep-set lancets.
  - ref10 (gothic ribbed dome): pale stone, dark blue-slate roofs, a great RIBBED DOME on a lit drum, flying buttresses
    along the nave, pinnacles on every buttress, tall lancets in every bay, dark firs around - this lab's massing:
    nave + dome + towers.
  - ref05 (cathedral front): pale stone with teal glass in every bay, twin towers framing a central gable with a rose,
    pinnacles stepping down, the grand approach - this lab's west front, the porch's gable carrying the rose.
  - ref28 (dense gothic cathedral): a pinnacle on every buttress, vertical rhythm every 2-3 cells, nothing flat.
  - ref20 (pyramidal spires): outer masses low, climbing to the one central spire; dark base, light tops.
  - ref01 (palace detail): every bay framed, a cornice and string course at every storey, every window with a sill, a
    surround and a head; calm because the detail is ORDERED.
  - ref21 (gothic tower): corbelled crown, pinnacles on the corners, a steep spire from the centre.
  - COLOGNE'S WEST PORTAL: orders stepping in, each a jamb with colonnettes and capitals and an arch of voussoirs; a
    crocketed gable (wimperg) with tracery over the outer order; pinnacles on the buttresses either side.
  What gives these depth: buttresses one proud of the wall and the glass one recessed (three planes on every face),
  portal orders stepping in three planes, a crown corbelled out, a porch proud of the towers, flyers two cells out.

THE PLAN (17 x 17: x 0..16 west -> east, z 0..16 north -> south; the RTS camera looks north-east from the south-west,
so it sees the roofs, the WEST face x 0 and the SOUTH face z 16; y 0 is the paving layer as in tools/lab_grove.py)
  - the WEST FRONT: the PORCH at x 0 (one proud of the towers), its three-order pointed portal with a gold keystone,
    the crocketed WIMPERG with the rose over it; the NORTH gate tower (x 1..5, z 0..4) and the SOUTH gate tower
    (x 1..5, z 12..16): clasping corner buttresses, one lit lancet per face, corbelled parapets, corner pinnacles and
    a steep slate pyramid cap; the passage x 1..5, z 6..10 vaulted dark, lit niches in its jambs, the portcullis up.
  - the OPEN BAY (x 6..9, z 6..10): walled by the nave's two walls, open to the sky; the dark-oak GANTRY crosses it at
    x 7 on quartz piers; the CLOUD CRADLE hangs under it (x 6..7, y 9..11); the sun mosaic on the floor; the spawn
    column (8, *, 8) is air to the top at both levels (amendment item 34).
  - the CHANCEL ARCH (x 10) into the DOME CHAMBER (x 11..15, z 6..10), under the RIBBED DOME (centre 13, 8): a lit
    octagonal drum on the chamber's roof terrace, quartz ribs, slate courses, the crown with the sun (L1) / the ribbed
    lantern spire ending in the SUN DISC (L3).
  - the NAVE WALLS (z 4..5 north, z 11..12 south, x 6..16): buttress - lit lancet bay - buttress, a sill course, a
    string course at the cornice, a quartz parapet; two FLYING BUTTRESSES on the south wall (x 10, 13) to piers at
    z 15; the east wall x 16 with a lancet.
  - the LODGE (x 6..16, z 1..3): a lean-to against the nave's north wall, a slate roof, a chimney.
  - the FORECOURT: polished andesite setts, a deepslate kerb, the processional strip from the spawn out of the gate,
    a dark fir at the south-east corner.

ANCHORS (structure-local block coords x, y, z; in game these become client-drawn moving parts):
  gantry beam      (7, 13, 6..10)  the dark oak beam across the bay on the quartz piers (7, 11..13, 5) / (7, 11..13, 11)
  gantry trolley   (7, 14, 8)      chiseled saddle riding the beam, slab hangers (7, 14, 7) / (7, 14, 9); slides z 6..10
  hoist            (7, 9, 8)       the CLOUD CRADLE (white wool, x 6..7, z 6..10, y 9..11, bottom at y 9 - over the
                                   lane's 8-high clearance) on chains (7, 12, 7) / (7, 12, 9); lowers to y 1
  counterweight    (7, 11, 10)     the gold sun counterweight (7, 10..12, 10) on its chain (7, 12, 10)
  gate leaf        (2, 10, 8)      the PORTCULLIS drawn RAISED: iron bars in order C's head (x 2: y 9 z 6..10, y 10
                                   z 7..9, y 11 z 8); drops to y 1..8 across z 6..10
  sun              (13, 19, 8)     L1: the ochre sun on the dome's crown; L3: the SUN DISC (x 12..14, y 25..27, z 8)
                                   facing south at the tip of the lantern spire, rays (11, 26, 8) / (15, 26, 8); it
                                   turns while the queue runs
  spawn cell       (8, 1, 8)       the gold boss of the sun mosaic, beside the cradle; the column above it is air

EXIT LANE: x 0..9, z 6..10, y 1..8 clear, the floor paved (the processional strip) - straight out west.

THE GROWTH (one 17 x 17 footprint; L3 only adds or replaces):
  - L1 the court as above (height 20).
  - L3 the ribbed LANTERN SPIRE grows from the dome's crown to the SUN DISC (height 28); the gate towers' caps
    become slate needles; the pinnacles gain gold knops.

Usage: python3 tools/lab_vehicle_sunforged.py   (writes both .nbt and checks them)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs                    # noqa: E402
import gothic_parts as GP                      # noqa: E402
import gothic_towers as GT                     # noqa: E402
import sunforged_lab_parts as SP               # noqa: E402
from gothic_parts import Part                  # noqa: E402

PAL = SP.PAL
SX = SZ = 17
LANE_X = range(0, 10)
LANE_Z = range(6, 11)
LANE_H = 8
SPAWN = (8, 1, 8)
DOME = (13, 8)
OUT = "design/vehicle_labs"
H1, H3 = 20, 28
FINE = ("_stairs", "_slab", "_wall", "_fence", "_pane", "iron_bars", "_trapdoor", "_button", "chain", "lantern",
        "candle", "_rod", "_door", "_carpet", "_banner", "bell")


# ============================================================================================ L1
def west_front(p):
    """The two gate towers (x 1..5) and the porch / portal at x 0..5, z 4..12."""
    p.place(SP.gate_tower(top=10, seed=1, lancets=("west",)), 1, 0, 0)
    p.place(SP.gate_tower(top=10, seed=2, lancets=("west", "south")), 1, 0, 12)
    SP.portal(p, zc=8)
    # the towers' faces against the passage (z 4 of the north tower, z 12 of the south) are the passage's walls: solid
    for x in range(1, 6):
        for z in (4, 12):
            for y in range(1, 13):
                if p.get(x, y, z) == "air":
                    p.set(x, y, z, "wall")


def nave(p):
    """The two long walls in the bay rhythm, the chancel arch, the east wall, the flyers, the chamber's roof terrace."""
    south = SP.nave_wall(11, top=10, buttress_at=(0, 4, 7, 10), lancet_at=(2, 5, 8), twin=(5, 8), seed=4, depth=1)
    p.place(south, 6, 0, 11)
    north = SP.nave_wall(11, top=10, buttress_at=(0, 3, 6, 10), lancet_at=(7,), twin=(7,), seed=6, inner=True,
                         depth=1)
    p.place(north, 16, 0, 5, rot=2)
    SP.chancel_arch(p, 10, 6, 10, 9)
    # the east wall x 16, z 5..11: the chamber's back, a lancet at z 8, the string course, the parapet
    m = GT.Mason(p, 16, 8, plinth=3)
    for z in range(5, 12):
        for y in range(1, 11):
            m.put(16, y, z, quoin=(z in (5, 11) and (y // 2) % 2 == 0))
        p.set(16, 11, z, "trim")
        p.set(16, 12, z, "rail") if z % 2 else p.slab(16, 12, z, "bottom", "trim_slab")
    for y in range(3, 9):
        p.set(16, y, 8, "glass_block")
        p.set(15, y, 8, "light")
    p.set(16, 9, 8, "carve")
    for z in range(6, 11):
        p.stair(16, 10, z, "east", "top", "band_st") if False else None
    # the chamber's roof terrace at y 11 (x 10..16, z 5..11) and its floor
    for x in range(10, 17):
        for z in range(5, 12):
            if p.get(x, 11, z) == "air":
                p.set(x, 11, z, "roof")

    for x in range(11, 16):
        for z in range(6, 11):
            p.set(x, 0, z, "strip" if (x + z) % 2 else "pave")
    # flying buttresses on the south wall's chamber buttresses
    for x in (10, 13):
        SP.flyer(p, x, 13, 11, s=1, seed=x)
    # pinnacles on the chamber's roof corners (the terrace's edge over the east wall)
    SP.pinnacle(p, 16, 12, 5)
    SP.pinnacle(p, 16, 12, 11)


def dome(p):
    SP.ribbed_dome(p, DOME[0], DOME[1], 11, lantern_top=19)


def gantry(p):
    SP.gantry(p, 7, 5, 11, 15)


def lodge(p):
    SP.lodge(p, 6, 16, 1, 4, 6, chimney_x=15)


def ground(p):
    SP.forecourt(p, SX, SZ)
    SP.sun_mosaic(p, 8, 8, r=2.5)
    for x in range(0, 8):                                    # the lane floor: setts, the processional strip, kerbs
        for z in LANE_Z:
            p.set(x, 0, z, "pave")
        p.set(x, 0, 8, "strip")
        if x < 6:
            p.set(x, 0, 6, "base")
            p.set(x, 0, 10, "base")
    for x in range(SX):                                      # a kerb of slabs round the plot (not across the lane)
        for z in (0, SZ - 1):
            if p.get(x, 1, z) == "air":
                p.slab(x, 1, z, "bottom", "base_slab")
    for z in range(SZ):
        if p.get(SX - 1, 1, z) == "air":
            p.slab(SX - 1, 1, z, "bottom", "base_slab")
        if p.get(0, 1, z) == "air" and z not in LANE_Z and z not in (5, 11):
            p.slab(0, 1, z, "bottom", "base_slab")
    for x in range(7, 16):                                   # flagstones laid over the setts in the south yard
        for z in range(13, 16):
            if p.get(x, 1, z) == "air" and GP.hsh(x, z, 8) % 3 == 0:
                p.slab(x, 1, z, "bottom", "base_slab")
    SP.conifer(p, 15, 15, h=6)


def batter(p):
    """The ground story: every outer wall's first course is a weathered batter (deepslate stairs, the step outward),
    where a wall cell has open ground outside it - never in the lane, never under a colonnette."""
    for (x, y, z), (n, _) in list(p.b.items()):
        if y != 1 or not n.endswith(("_bricks", "polished_deepslate", "polished_andesite")):
            continue
        for f, (vx, vz) in GP.VEC.items():
            ox, oz = x + vx, z + vz
            if not (0 <= ox < SX and 0 <= oz < SZ) or p.get(ox, 1, oz) != "air" or p.get(ox, 2, oz) != "air":
                continue
            if oz in LANE_Z and ox <= 9:
                continue
            if p.get(x, 2, z) == "air":
                continue
            p.stair(x, 1, z, GP.OPP[f], "bottom", "base_st")
            break


def draw_l1(p):
    ground(p)
    lodge(p)
    nave(p)
    west_front(p)
    dome(p)
    gantry(p)
    batter(p)


# ============================================================================================ L3
def draw_l3(p):
    """The ribbed lantern spire from the dome's crown to the sun disc; the gate towers' caps become slate needles."""
    cx, cz = DOME
    p.clear(cx, 19, cz)
    p.clear(cx, 18, cz)
    for (x, z) in GP.ring(1, cx, cz):
        p.clear(x, 19, z)
    SP.lantern_spire(p, cx, cz, 18)
    for (tx, tz) in ((3, 2), (3, 14)):                       # the towers' caps grow into needles
        for y in range(16, 19):
            p.set(tx, y, tz, "roof_wall")
        p.set(tx, 19, tz, "gold")
        p.set(tx, 20, tz, "spike", facing="up")


# ============================================================================================ build + checks
def to_rig(p, sy, label):
    rig = gs._SunRig(SX, sy, SZ)
    for (x, y, z), (n, props) in p.b.items():
        if n in ("air", "cave_air"):
            continue
        assert 0 <= x < SX and 0 <= z < SZ and 0 <= y < sy, ((x, y, z), n, label)
        rig.set(x, y, z, n, **props)
    rig.bury_grass()
    rig.settle()
    rig.check_support(label)
    return rig


def build(level):
    p = Part(PAL)
    draw_l1(p)
    if level >= 3:
        draw_l3(p)
    ys = [y for (x, y, z), v in p.b.items() if v[0] not in ("air", "cave_air")]
    return to_rig(p, max(ys) + 1, "sunwain L%d" % level)


def is_fine(n):
    return n.endswith(FINE) or n in FINE


def stats(rig):
    """(blocks, fine %, visible blocks, fine % of the visible): visible = a face the RTS camera sees (up, south, west)
    is open or looks through a fine block."""
    b = rig.b
    vis = [q for q in b if any((q[0] + dx, q[1] + dy, q[2] + dz) not in b or is_fine(b.get(
        (q[0] + dx, q[1] + dy, q[2] + dz), ("air",))[0]) for dx, dy, dz in ((0, 1, 0), (0, 0, 1), (-1, 0, 0)))]
    fine = [q for q in b if is_fine(b[q][0])]
    vfine = [q for q in vis if is_fine(b[q][0])]
    return len(b), 100.0 * len(fine) / len(b), len(vis), 100.0 * len(vfine) / max(1, len(vis))


def floating(b):
    return [q for q in b if q[1] > 0 and not any((q[0] + dx, q[1] + dy, q[2] + dz) in b for dx, dy, dz in (
        (1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)))]


def check(rig, path, label):
    import audit_structure_pockets as ap
    n = {q: v[0] for q, v in rig.b.items()}
    assert all((x, 0, z) in n for x in range(SX) for z in range(SZ)), "a hole in the foundation"
    busy = [(x, y, z, n[(x, y, z)]) for x in LANE_X for z in LANE_Z for y in range(1, LANE_H + 1) if (x, y, z) in n]
    assert not busy, "the exit lane is narrowed: %s" % busy[:8]
    col = [(y, n[(SPAWN[0], y, SPAWN[2])]) for y in range(1, rig.size[1]) if (SPAWN[0], y, SPAWN[2]) in n]
    assert not col, "the spawn column is not air: %s" % col
    assert not floating(n), "floating: %s" % floating(n)[:8]
    bad = sorted({v for v in n.values() if any(f in v for f in (
        "water", "lava", "sand", "gravel", "_concrete_powder", "fire", "redstone", "tuff_brick", "polished_tuff",
        "sapling", "torch", "cobweb", "scaffolding", "pink", "purple", "magenta"))
        or ("copper" in v and not v.startswith("waxed_"))})
    assert not bad, "forbidden: %s" % bad
    one_way, ground_, roof, rooms, note = ap.audit(label, path)
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
    blocks, fine, vis, vfine = stats(rig)
    return "%s %s, %d blocks, %.0f%% fine (%.0f%% of %d visible): lane clear, spawn column clear, no pockets (%d " \
           "roof-only cells)%s" % (label, "x".join(map(str, rig.size)), blocks, fine, vfine, vis, len(roof), note)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    r1, r3 = build(1), build(3)
    assert r1.size[1] <= H1 and r3.size[1] <= H3, (r1.size, r3.size)
    n1 = {q: v[0] for q, v in r1.b.items()}
    n3 = {q: v[0] for q, v in r3.b.items()}
    lost = [q for q in n1 if q not in n3]
    assert not lost, "L3 empties L1 cells: %s" % lost[:8]
    for rig, name in ((r1, "sunforged_l1"), (r3, "sunforged_l3")):
        path = os.path.join(OUT, name + ".nbt")
        rig.structure().write(path)
        print(check(rig, path, name))
