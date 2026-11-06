#!/usr/bin/env python3
"""The Sunforged (villagers) Library, redrawn at both levels - library.nbt (L1, THE SCRIPTORIUM) and library_grand.nbt
(the Grand Library, L2, THE LANTERN OF LEARNING) - in the kit the Barracks (tools/lab_barracks.py), the Temple of the
Sun and the Angel Fountain set: white quartz walls, dark oak roofs and bands, gold only on the crown, lanterns.

Why (Nov 6 2026): the all-factions blind Fable sweep (eyes on main jar 42137577884, 62 buildings) scored RoN's library
3 / 10, among the game's weakest: "no roof, no walls, four bookshelf posts and glass-pane fences; the copper altar is
the wrong faction colour". lovish's bar (the TomatoN castle): layered massing up to one dominant spire, slim towers
with crown bands and dark steep roofs, recessed windows. At our RTS angle the roofs are the main face.

THE PLAN (7 x 7, RoN's footprint; x 0 west .. 6 east, z 0 north .. 6 south; the RTS camera looks from the south-west
and sees the roofs, the west face x 0 and the south face z 6):
  - the HALL (x 0..6, z 0..4): quartz brick walls to y 4 between quartz pillar corners, a dark oak band at y 4 (the
    eave line), bookshelves in recessed bays on the camera faces (the library's own cue at RTS zoom), the enchanting
    table inside; a steep DARK OAK GABLE ROOF over it, ridge east-west at y 7, quartz gable on the west;
  - the TOWER (x 4..6, z 0..2) on the north-east, the mass that leads: quartz to y 9, recessed dark slits on its west
    and south faces at y 7..8, a GOLD CROWN BAND at y 9, a dark oak pyramid roof and a gold finial with the lantern;
  - the PORCH (x 2..4, z 5): two quartz pillars under a pediment, a lantern hung under it, spruce steps (the start
    type) across the south edge, lantern posts on the front corners; the plinth stone bricks (the mids).
  L2 THE GRAND LIBRARY: the tower rises into a glazed LANTERN ROOM (y 10..11) round a glowstone core - the warm light
  at dusk, and the block RoN's Library.getUpgradeLevel looks for - under a second gold band and a taller dark spire.

GAME RULES (asserted in check_levels):
  - both levels 7 x 7 (Library.java and RoN's placement); a level may grow taller;
  - L1 has NO glowstone and the grand has some (Library.getUpgradeLevel: any glowstone = upgraded);
  - Library.startingBlockTypes = oak_log, spruce_stairs: both in layer 0 at L1 (the foundation queue);
  - the chain: every non-air cell of L1 is non-air in the grand (changeStructure only adds or replaces);
  - the enchanting table (the building's portrait) at both levels;
  - nothing pops off (gen_structures._SunRig support rules); no water, lava, gravity blocks, redstone, doors, leaves,
    copper.

Usage: python3 tools/lab_library.py [--layout ne|front] [OUT_DIR ...]   (default: "ne", both shipped copies)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs   # noqa: E402
import lab_barracks as LB     # noqa: E402

SX, SZ = 7, 7
DARK = "dark_oak_planks"
ROOF = "dark_oak_stairs"
START = ("oak_log", "spruce_stairs")
HW = 4                         # the hall's wall top (the dark band)
HZ1 = 4                        # the hall's south wall
TX0, TX1, TZ0, TZ1 = 4, 6, 0, 2   # the tower
TW = 9                         # the tower's wall top (the gold band)
TC = (5, 1)                    # the tower's centre column
PORCH = (2, 4)                 # the porch pillars' x, at z 5
LAYOUT = "ne"
# the tower's place: "ne" behind the hall on the north-east (the Barracks' keep), or "front" over the entrance on the
# south (a west-work: the camera sees its two faces whole, the hall's roof behind it). One variable for a blind round.
LAYOUTS = {"ne": (4, 6, 0, 2), "front": (2, 4, 4, 6)}


def use_layout(name):
    global TX0, TX1, TZ0, TZ1, TC, LAYOUT
    TX0, TX1, TZ0, TZ1 = LAYOUTS[name]
    TC = ((TX0 + TX1) // 2, (TZ0 + TZ1) // 2)
    LAYOUT = name


class Rig(LB.Rig):
    """lab_barracks.Rig (support rules for lanterns, banners, flowers) on the library's 7 x 7."""

    def __init__(self, sy):
        gs._SunRig.__init__(self, SX, sy, SZ)


def plinth(r):
    """Layer 0: stone bricks (the mids), oak logs under the hall's and the tower's corners and the porch pillars (the
    start type, the foundation's frame), a polished andesite threshold, spruce steps across the south edge."""
    for x in range(SX):
        for z in range(SZ):
            r.set(x, 0, z, "stone_bricks")
    for x, z in [(0, 0), (6, 0), (0, HZ1), (6, HZ1), (TX0, TZ0), (TX0, TZ1), (TX1, TZ1)] + [(px, 5) for px in PORCH]:
        r.set(x, 0, z, "oak_log", axis="y")
    r.set(3, 0, 5, "polished_andesite")
    for x in range(1, 6):
        r.st(x, 0, SZ - 1, "north", name="spruce_stairs")


def hall(r):
    """The hall's walls (x 0..6, z 0..4) to y 4: pillar corners, smooth quartz base course, quartz bricks, a dark oak
    band at y 4; bookshelf bays recessed one cell on the two camera faces; the door under the porch; the enchanting
    table and shelves inside."""
    for x in range(SX):
        for z in range(HZ1 + 1):
            if not (x in (0, SX - 1) or z in (0, HZ1)):
                continue
            for y in range(1, HW + 1):
                if x in (0, SX - 1) and z in (0, HZ1):
                    r.pillar(x, y, z)
                elif y == HW:
                    r.set(x, y, z, DARK)
                else:
                    r.set(x, y, z, "smooth_quartz" if y == 1 else "quartz_bricks")
    bays = [((0, 1), (1, 1)), ((0, 3), (1, 3)), ((1, HZ1), (1, HZ1 - 1)), ((5, HZ1), (5, HZ1 - 1))]
    for (fx, fz), (bx, bz) in bays:
        for y in (2, 3):
            r.b.pop((fx, y, fz), None)
            r.set(bx, y, bz, "bookshelf")
    for y in (1, 2):                                  # the door
        r.b.pop((3, y, HZ1), None)
    r.set(3, 3, HZ1, "chiseled_quartz_block")
    r.set(3, 1, 2, "enchanting_table")
    for x in (1, 2):
        r.set(x, 1, 1, "bookshelf")


def hall_roof(r):
    """A steep dark oak gable over the hall, ridge east-west: eaves at y 5 (z 0, z 4), y 6 (z 1, z 3), the ridge at
    y 7 (z 2); the tower's cells skipped; the west gable end in quartz brick with a dark window."""
    rows = {0: (5, "south"), 1: (6, "south"), 3: (6, "north"), 4: (5, "north")}
    for x in range(SX):
        for z in range(HZ1 + 1):
            if TX0 <= x <= TX1 and TZ0 <= z <= TZ1:
                continue
            if z == 2:
                r.set(x, 7, z, DARK)
            else:
                y, f = rows[z]
                r.st(x, y, z, f, name=ROOF)
    for z in (1, 2, 3):                               # the west gable
        r.set(0, 5, z, "quartz_bricks")
    r.set(0, 6, 2, "quartz_bricks")
    r.b.pop((0, 5, 2), None)                          # its window, dark at the back
    r.set(1, 5, 2, DARK)
    r.set(SX - 1, 5, 3, "quartz_bricks")              # the east gable's step under the tower's roof line


def tower_body(r, top):
    """The tower (x 4..6, z 0..2) from y 1 to `top`: pillar corners, quartz brick faces, hollow inside; recessed slits
    on its west and south faces at y 7..8 with dark oak at the back."""
    for x in range(TX0, TX1 + 1):
        for z in range(TZ0, TZ1 + 1):
            if (x, z) == TC:
                continue
            for y in range(1, top + 1):
                if x in (TX0, TX1) and z in (TZ0, TZ1):
                    r.pillar(x, y, z)
                else:
                    r.set(x, y, z, "quartz_bricks")
    for (fx, fz) in ((TX0, TC[1]), (TC[0], TZ1)):
        for y in (7, 8):
            r.b.pop((fx, y, fz), None)
            r.set(TC[0], y, TC[1], DARK)


def crown(r, band, light):
    """A gold crown band (the ring at y `band`), dark oak eaves at band + 1 climbing in, the centre a block higher, a
    gold finial and a lantern: returns the lantern's y."""
    for x in range(TX0, TX1 + 1):
        for z in range(TZ0, TZ1 + 1):
            if (x, z) == TC:
                r.set(x, band + 1, z, DARK)
                r.set(x, band + 2, z, DARK)
                continue
            r.set(x, band, z, "gold_block")
            r.st(x, band + 1, z, "east" if x == TX0 else "west" if x == TX1 else "south" if z == TZ0 else "north",
                 name=ROOF)
    r.set(TC[0], band + 3, TC[1], "gold_block")
    if light:
        r.lantern(TC[0], band + 4, TC[1])
    return band + 4


def porch(r):
    """Two quartz pillars (z 5) under a pediment at y 4 rising to a slab at y 5, a lantern hung under it, lantern
    posts on the plinth's front corners."""
    for px in PORCH:
        for y in (1, 2, 3):
            r.pillar(px, y, 5)
    r.st(2, 4, 5, "east", name="smooth_quartz_stairs")
    r.st(4, 4, 5, "west", name="smooth_quartz_stairs")
    r.set(3, 4, 5, "smooth_quartz")
    r.slab(3, 5, 5)
    r.lantern(3, 3, 5, hanging=True)
    for x in (0, 6):
        r.set(x, 1, 6, "stone_brick_wall")
        r.lantern(x, 2, 6)


def front_door(r):
    """The "front" layout's entrance: the porch is the tower's base - a door through its south face, its hollow and
    the hall's wall, under a chiseled lintel; lantern posts on the plinth's front corners."""
    for y in (1, 2):
        for z in (TZ1, TC[1], HZ1):
            r.b.pop((TC[0], y, z), None)
    r.set(TC[0], 3, TZ1, "chiseled_quartz_block")
    for x in (0, 6):
        r.set(x, 1, 6, "stone_brick_wall")
        r.lantern(x, 2, 6)


def draw_l1(r):
    plinth(r)
    hall(r)
    hall_roof(r)
    tower_body(r, TW - 1)
    crown(r, TW, True)
    if LAYOUT == "front":
        front_door(r)
    else:
        porch(r)


GRAND_BAND = TW + 4            # the grand's second gold band, over the three-high lantern room
PINNACLES = ((0, 0), (0, HZ1), (SX - 1, HZ1))   # the hall's free corners


def draw_grand(r):
    """L1 + the lantern room: the L1 crown's eaves, centre and finial become a glazed room three high (y 10..12) round
    a glowstone core between quartz pillars, then a second gold band, eaves and finial (top y 17, L1's 13); and the
    hall's three free corners rise into quartz pinnacles with a lantern on each, through the roof's eaves (round 2,
    blind r1: the first grand was "one extra gold band, invisible at RTS")."""
    draw_l1(r)
    for x in range(TX0, TX1 + 1):
        for z in range(TZ0, TZ1 + 1):
            for y in range(TW + 1, GRAND_BAND):
                if (x, z) == TC:
                    r.set(x, y, z, "glowstone")
                elif x in (TX0, TX1) and z in (TZ0, TZ1):
                    r.pillar(x, y, z)
                else:
                    r.set(x, y, z, "glass")
    r.set(TC[0], GRAND_BAND, TC[1], "glowstone")
    crown(r, GRAND_BAND, True)
    for x, z in PINNACLES:
        r.pillar(x, HW + 1, z)
        r.lantern(x, HW + 2, z)
    return r


L1_SY = TW + 5                 # the lantern on the finial (y 13)
GRAND_SY = GRAND_BAND + 5      # the grand's lantern (y 17)


def build(draw, sy, tier):
    r = Rig(sy)
    draw(r)
    r.settle()
    r.check_support(tier)
    return r.structure()


def l1():
    return build(draw_l1, L1_SY, "L1")


def grand():
    return build(draw_grand, GRAND_SY, "grand")


def names(s):
    return {p: s.palette[i][0] for p, i in s.blocks.items() if s.palette[i][0] not in ("air", "cave_air")}


FORBIDDEN = LB.FORBIDDEN


def check_levels(s1, s2):
    """The rules the game needs, on the two finished Structures (support already ran in build())."""
    for tag, s in (("L1", s1), ("grand", s2)):
        assert (s.size[0], s.size[2]) == (SX, SZ), "%s: footprint %s, the Library's is 7 x 7" % (tag, s.size)
        n = names(s)
        assert max(p[1] for p in n) == s.size[1] - 1, "%s: the box is taller than its blocks" % tag
        assert all((x, 0, z) in n for x in range(SX) for z in range(SZ)), "%s: a hole in the foundation" % tag
        bad = sorted({nm for nm in n.values() if "copper" in nm or any(nm == f or nm.endswith(f) for f in FORBIDDEN)})
        assert not bad, "%s: forbidden blocks %s" % (tag, bad)
        assert "enchanting_table" in n.values(), "%s: no enchanting table" % tag
    n1, n2 = names(s1), names(s2)
    assert "glowstone" not in n1.values(), "L1 has glowstone: the game would read it as the Grand Library"
    assert "glowstone" in n2.values(), "the grand has no glowstone: the game would not read it as upgraded"
    for t in START:
        assert any(p[1] == 0 and nm == t for p, nm in n1.items()), "L1: no %s in layer 0 (a start type)" % t
    lost = sorted(set(n1) - set(n2))
    assert not lost, "L1 -> grand would leave blocks behind: %s" % lost[:8]
    return ["L1 %dx%dx%d, %d blocks; grand %dx%dx%d, %d blocks" % (s1.size + (len(n1),) + s2.size + (len(n2),))]


ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHIPPED = [os.path.join(ROOT, "bar", "src", "main", "resources", k, "reignofnether", "structures") for k in ("data", "assets")]

if __name__ == "__main__":
    args = sys.argv[1:]
    if args[:1] == ["--layout"]:
        use_layout(args[1])
        args = args[2:]
    outs = args or SHIPPED
    built = (l1(), grand())
    for out in outs:
        os.makedirs(out, exist_ok=True)
        for s, f in zip(built, ("library.nbt", "library_grand.nbt")):
            s.write(os.path.join(out, f))
            print(os.path.join(out, f))
    for line in check_levels(*built):
        print(line)
