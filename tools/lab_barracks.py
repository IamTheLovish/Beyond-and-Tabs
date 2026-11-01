#!/usr/bin/env python3
"""The Sunforged (White Angel) T1 army lab, the Barracks, redrawn at all three levels - L1 THE KEEP AND THE COURT, L2 THE
KNIGHTS' CLOISTER, L3 THE CATHEDRAL OF ARMS - so it belongs to the family the Temple of the Sun (the capitol,
tools/capitol_town_centre.py build_grand) and the Angel Fountain (the extractor, gen_structures._sun_fountain_t1) set.

lovish, Oct 19 2026: buildings must "look like they match each other so when bases get bigger they look cohesive and
beautiful"; "upgraded buildings can change in shape or fundamental design ... make upgraded buildings stand out for the
opponent; ... much bigger or have cooler, more intricate designs". The White Angel is classical at tiers 1-2 and gothic
at tier 3 (refs 84-86, 92: cathedral tracery and buttresses), so L1 and L2 are a classical temple court and L3 turns
it into a white cathedral.

THE SUNFORGED STYLE KIT (the capitol's and the fountain's):
  - main blocks: white stone and quartz (smooth / bricks / pillar / chiseled quartz, calcite, polished diorite);
  - trim: gold (the column capitals, the bands, the crowns), lapis on the axes (the frieze triglyphs);
  - accent: BLUE GLASS (lancets, the drums, the rose window) and blue banners;
  - light: lanterns on posts, pinnacles and crowns, candles on the balustrade;
  - silhouette motif: TIERED DOMES (rings of stairs climbing inward) with a gold crown;
  - family figure: the angel - on the bell turret (L1-L2), gilded on top of the belfry spire (L3);
  - edge: a garden (grass and flowers) at the yard's corners.

THE PLAN (10 x 8, the shipped footprint; redrawn Nov 2026 after blind massing rounds, PR body). The RTS camera looks
from the south-west: it sees the roofs, the west face (x = 0) and the south face (z = 7). The Training Hall that shipped
before (a bell turret and a hall of equal boxes over a sand yard, lapis bands, blue banners, two targets) read as "a
white box house" (critics 5 / 5). Now a DRILL COURT UNDER A KEEP:
  L1 THE KEEP AND THE COURT (10 x 13 x 8)
  - the KEEP on the north-east corner (x 6..9, z 0..3), the mass that leads: quartz brick walls to y 8 between quartz
    pillar corners, a deep gate in its south face (three high, two deep: the court's dark), and the kit's tiered crown
    - a ring of stairs climbing in, pillar pinnacles with gold on the corners, a pillar drum, a 2 x 2 gold crown, the
    lantern;
  - arcaded WINGS with one pier / arch beat close the court on the north (x 0..5), the west (z 2..3) and the east
    (z 5..6); the court opens to the south-west, the camera's side and the units' way out;
  - the COURT in the mids (polished andesite) with a calcite drill ring round the spawn, a rack of lances (end rods),
    lantern posts on the wings' ends. No lapis, no blue, no target.
  L2 THE KNIGHTS' CLOISTER: the north wing gets an upper gallery, the east wing a small domed pavilion with a gold
    crown, the keep's lantern becomes the family's ANGEL on a gold boss.
  L3 THE CATHEDRAL OF ARMS: the north wing rises into a clerestory under a sloped roof, the west wing's corner a slim
    tower with a gold knop, the angel is gilded on a gold needle.

GAME RULES (asserted in check_levels):
  - every level keeps the 10 x 8 footprint (BuildingLevels has no larger footprint); a level may grow taller;
  - the chain: every non-air cell of L1 is non-air in L2, and of L2 in L3 (changeStructure only adds or replaces):
    L2 is drawn over L1 and L3 over L2, and that is asserted;
  - Barracks.startingBlockTypes = polished_andesite_stairs: in layer 0 (the kerb), nowhere above;
  - the unit spawn: ProductionBuilding.getIndoorSpawnPoint walks down from centrePos, the L1 box's centre. centrePos is
    (int) of ((minX + maxX) / 2f, ...) in WORLD coordinates, so on this even footprint it is the cell x 4 or 5 and z 3
    or 4 depending on the sign of the world coordinate and on the placement's rotation, at y = L1 maxY / 2 (or one
    up below y = 0); changeStructure never moves it, so L2 and L3 spawn there too. The four central columns
    (x 4..5, z 3..4) are yard, open from the floor to above that height at every level, and units walk out;
  - nothing pops off (lanterns standing / hung, candles, banners, the bell, flowers); no pockets
    (tools/audit_structure_pockets.py on all three); no water, lava, gravity blocks, redstone, campfires, doors, leaves.

Usage: python3 tools/lab_barracks.py [OUT_DIR]   (writes barracks.nbt, barracks_l2.nbt, barracks_l3.nbt and checks them;
       default OUT_DIR: the scratch folder - shipping them is the coordinator's step)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs   # noqa: E402

SX, SZ = 10, 8
CENTRE = [(4, 3), (5, 3), (4, 4), (5, 4)]   # the cells the spawn can start in (see the docstring)
FLOWERS = ("cornflower", "lily_of_the_valley", "azure_bluet", "oxeye_daisy", "allium")
START_TYPE = "polished_andesite_stairs"       # Barracks.startingBlockTypes
CORNERS3 = ((0, 0), (2, 0), (0, 2), (2, 2))


class Rig(gs._SunRig):
    """The Sunforged rig (gen_structures._SunRig: settle() shapes stairs and joins walls like the game does) with
    the support check this lab needs: _SunRig's own refuses any hung bell and any wall banner, this lab has both."""

    def __init__(self, sy):
        super().__init__(SX, sy, SZ)

    def st(self, x, y, z, facing, half="bottom", name="quartz_stairs"):
        self.stair(x, y, z, facing, half, name)

    def slab(self, x, y, z, name="smooth_quartz_slab", typ="bottom"):
        self.set(x, y, z, name, type=typ, waterlogged="false")

    def fill(self, x1, y1, z1, x2, y2, z2, name, **p):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.set(x, y, z, name, **p)

    def pillar(self, x, y, z):
        self.set(x, y, z, "quartz_pillar", axis="y")

    def lantern(self, x, y, z, hanging=False):
        self.set(x, y, z, "lantern", hanging="true" if hanging else "false", waterlogged="false")

    def candle(self, x, y, z, n=3):
        self.set(x, y, z, "white_candle", candles=str(n), lit="true", waterlogged="false")

    def check_support(self, tier):
        bad = []
        for pos, (n, p) in self.b.items():
            below, above = self.nb(pos, "down"), self.nb(pos, "up")
            if n in ("lantern", "soul_lantern"):
                if p.get("hanging") == "true":
                    ok = gs._sun_sturdy(above, "down") or above[0] == "chain"
                else:
                    ok = gs._sun_centre_up(below)
            elif n.endswith("candle"):
                ok = gs._sun_centre_up(below)
            elif n.endswith("_wall_banner"):
                behind = self.nb(pos, gs._SUN_OPP[p["facing"]])
                ok = gs._sun_solid(behind) and not gs._sun_is(behind, *gs._SUN_THIN)
            elif n.endswith("_banner"):
                ok = gs._sun_solid(below)
            elif n == "bell":     # vanilla BellBlock.canSurvive: a ceiling bell needs a full face over it
                a = p.get("attachment")
                ok = (a == "ceiling" and gs._sun_sturdy(above, "down")) or \
                    (a == "floor" and gs._sun_sturdy(below, "up"))
            elif n in FLOWERS:
                ok = below[0] in ("grass_block", "dirt", "moss_block", "coarse_dirt", "podzol")
            elif n.endswith(("_torch", "_button", "ladder", "_carpet")) or n in ("torch", "soul_torch"):
                ok = False    # none are drawn on this lab
            else:
                continue
            if not ok:
                bad.append((pos, n))
        assert not bad, "barracks %s: would pop off / hang: %s" % (tier, sorted(bad))


def ring(r, x1, x2, z1, z2, y, name="quartz_stairs", half="bottom"):
    """A ring of stairs climbing inward over [x1..x2] x [z1..z2]: one tier of the kit's dome (settle() turns the
    corners)."""
    for x in range(x1, x2 + 1):
        r.st(x, y, z1, "south", half, name)
        r.st(x, y, z2, "north", half, name)
    for z in range(z1 + 1, z2):
        r.st(x1, y, z, "east", half, name)
        r.st(x2, y, z, "west", half, name)


def angel(r, x, y, z, gold=False):
    """The family's angel (the fountain's, the capitol's plaza angels), feet at y, facing south: a quartz robe, a
    calcite head, and wings spread east-west from the shoulders and swept UP past the head (the fountain's wing: an
    upside-down stair for the hanging feathers, a full quill, a stair for the tip curling in) so the figure reads as a
    V of wings round the lantern it holds up on a gold boss - not a cross. Gilded on the L3 spire."""
    r.set(x, y, z, "gold_block") if gold else r.pillar(x, y, z)
    r.set(x, y + 1, z, "gold_block" if gold else "calcite")
    for dx, out in ((-1, "west"), (1, "east")):
        r.st(x + dx, y + 1, z, out, "top", name="smooth_quartz_stairs")          # the hanging feathers
        r.set(x + dx, y + 2, z, "gold_block" if gold else "smooth_quartz")       # the quill
        r.st(x + dx, y + 3, z, gs._SUN_OPP[out], name="smooth_quartz_stairs")    # the tip, curling in
    r.set(x, y + 2, z, "gold_block")
    r.lantern(x, y + 3, z)


# ======================================================================================================== the plan
# THE DRILL COURT UNDER THE KEEP (Nov 2026 rounds, PR body): the keep on the north-east corner (x 6..9, z 0..3) leads,
# the court is open to the south-west (the camera's side and the units' way out), arcaded wings close it on the north,
# west and east with a regular pier / arch beat.
KX0, KX1, KZ0, KZ1 = 6, 9, 0, 3          # the keep
KW = 8                                   # the keep's wall top; the crown's tiers sit on it
GATE = (7, 8)                            # the deep gate in the keep's south face (z 2..3, y 1..3)


def floor_l1(r):
    """Layer 0: a polished andesite court (the mids) in its kerb, a calcite drill ring round the spawn, the start
    type's steps across the south edge."""
    for x in range(SX):
        for z in range(SZ):
            r.set(x, 0, z, "polished_andesite")
    for x in range(3, 7):
        for z in range(2, 6):
            if x in (3, 6) or z in (2, 5):
                r.set(x, 0, z, "calcite")
    for x in range(1, 9):
        r.st(x, 0, SZ - 1, "north", name=START_TYPE)


DARK = "dark_oak_planks"                 # the kit's trim that stays dark (BiomePalette keeps DARK_OAK): the shadows


def keep_body(r):
    """The keep's body: quartz brick walls to y 8 between quartz pillar corners, a chiseled string course at y 5, the
    deep gate (three high, two deep under a chiseled lintel) lined with dark oak at its back and ceiling."""
    for x in range(KX0, KX1 + 1):
        for z in range(KZ0, KZ1 + 1):
            corner = x in (KX0, KX1) and z in (KZ0, KZ1)
            edge = x in (KX0, KX1) or z in (KZ0, KZ1)
            for y in range(1, KW + 1):
                if y == 5 and edge:
                    r.set(x, y, z, "chiseled_quartz_block")
                elif corner:
                    r.pillar(x, y, z)
                else:
                    r.set(x, y, z, "quartz_bricks" if y > 1 else "smooth_quartz")
    for x in GATE:
        for z in (KZ1 - 1, KZ1):
            for y in (1, 2, 3):
                r.b.pop((x, y, z), None)
        r.set(x, 4, KZ1, "chiseled_quartz_block")
        r.set(x, 4, KZ1 - 1, DARK)
        for y in (1, 2, 3):
            r.set(x, y, KZ1 - 2, DARK)


def crown(r):
    """The crown (round 3's blind pick over battlements and a gold belt): the keep's corners chamfered (stairs at y 8 instead of the pillar tops), a ring of stairs climbing
    in on the eight face cells at y 9, a pillar drum, a 2 x 2 gold crown at y 11, the lantern."""
    for x in (KX0, KX1):
        for z in (KZ0, KZ1):
            r.b.pop((x, KW, z), None)
            r.st(x, KW, z, "east" if x == KX0 else "west")
    for x in range(KX0, KX1 + 1):
        for z in range(KZ0, KZ1 + 1):
            corner = x in (KX0, KX1) and z in (KZ0, KZ1)
            if corner:
                continue
            if x == KX0:
                r.st(x, KW + 1, z, "east")
            elif x == KX1:
                r.st(x, KW + 1, z, "west")
            elif z == KZ0:
                r.st(x, KW + 1, z, "south")
            elif z == KZ1:
                r.st(x, KW + 1, z, "north")
            else:
                r.set(x, KW + 1, z, "smooth_quartz")
                r.pillar(x, KW + 2, z)
                r.set(x, KW + 3, z, "gold_block")
    r.lantern(7, KW + 4, 1)


def keep_l1(r):
    keep_body(r)
    crown(r)


def arcade_x(r, x0, x1, z_back, z_front, top, piers):
    """A wing along x: the back wall at z_back, the front at z_front: pillars at `piers`, arches (air y 1..2 under an
    upside-down stair lintel) between them, a smooth quartz deck at `top` and a slab parapet over the front."""
    for x in range(x0, x1 + 1):
        for y in range(1, top + 1):
            r.set(x, y, z_back, "quartz_bricks")
        if x in piers:
            for y in range(1, top):
                r.pillar(x, y, z_front)
        else:
            r.st(x, top - 1, z_front, "north" if z_front > z_back else "south", "top", name="quartz_stairs")
        r.set(x, top, z_front, "smooth_quartz")
        r.slab(x, top + 1, z_front)


def arcade_z(r, z0, z1, x_back, x_front, top, piers):
    """A wing along z (see arcade_x)."""
    for z in range(z0, z1 + 1):
        for y in range(1, top + 1):
            r.set(x_back, y, z, "quartz_bricks")
        if z in piers:
            for y in range(1, top):
                r.pillar(x_front, y, z)
        else:
            r.st(x_front, top - 1, z, "west" if x_front > x_back else "east", "top", name="quartz_stairs")
        r.set(x_front, top, z, "smooth_quartz")
        r.slab(x_front, top + 1, z)


def wings_l1(r):
    """North wing x 0..5 (z 0 wall, z 1 arcade, deck y 4), west wing z 2..3 (x 0 wall, x 1 arcade, deck y 4),
    east wing z 5..6 (x 9 wall, x 8 arcade, deck y 4); lantern posts on the wings' ends; a rack of lances."""
    arcade_x(r, 0, 5, 0, 1, 4, (0, 2, 4))
    for x in (1, 3, 5):                     # the arches' backs in dark oak: shadow the camera can see
        for y in (1, 2):
            r.set(x, y, 0, DARK)
    arcade_z(r, 2, 3, 0, 1, 4, (3,))
    arcade_z(r, 5, 6, KX1, KX1 - 1, 4, (6,))
    r.set(0, 5, 3, "diorite_wall")
    r.lantern(0, 6, 3)
    r.lantern(9, 5, 6)
    for x in (5, 6, 7):
        r.set(x, 1, 6, "end_rod", facing="up")


def draw_l1(r):
    floor_l1(r)
    keep_l1(r)
    wings_l1(r)


# ======================================================================================================== L2
def draw_l2(r):
    """THE KNIGHTS' CLOISTER: L1 + an upper gallery on the north wing (y 5..7, a pillar beat, a slab parapet), the
    east wing raised into a domed pavilion (a ring of stairs, a gold crown), the keep's lantern swapped for the angel
    on a gold boss (the family figure, wings spread east-west)."""
    draw_l1(r)
    for x in range(0, 6):
        for y in (5, 6):
            r.set(x, y, 0, "quartz_bricks")
            if x % 2 == 0:
                r.pillar(x, y, 1)
            else:
                r.set(x, y, 1, "chiseled_quartz_block" if y == 6 else "smooth_quartz")
        r.set(x, 7, 0, "smooth_quartz")
        r.set(x, 7, 1, "smooth_quartz")
        r.slab(x, 8, 1)
    for z in (5, 6):
        for y in (5, 6):
            r.set(8, y, z, "quartz_pillar", axis="y") if y == 5 else r.set(8, y, z, "smooth_quartz")
            r.set(9, y, z, "quartz_bricks")
    ring(r, 8, 9, 5, 6, 7)
    r.set(9, 8, 6, "gold_block")
    r.lantern(9, 9, 6)
    r.set(7, KW + 4, 1, "gold_block")      # the lantern's cell gilded: the angel stands on it
    angel(r, 7, KW + 5, 1)


# ======================================================================================================== L3
def draw_l3(r):
    """THE CATHEDRAL OF ARMS: L2 + the north wing raised into a clerestory (y 8..10, the pillar beat carried up) under
    a sloped roof with a slab ridge (y 12), the west wing's corner a slim tower to y 9 with a gold knop, the
    angel gilded and set on a gold needle."""
    draw_l2(r)
    for x in range(0, 6):
        for y in (8, 9, 10):
            r.set(x, y, 0, "quartz_bricks")
            if x % 2 == 0:
                r.pillar(x, y, 1)
            else:
                r.set(x, y, 1, "smooth_quartz")
        r.st(x, 11, 1, "north", name="quartz_stairs")
        r.set(x, 11, 0, "smooth_quartz")
        r.slab(x, 12, 0)
    for y in range(5, 9):
        r.pillar(1, y, 2)
    r.set(1, 9, 2, "gold_block")
    angel(r, 7, KW + 5, 1, gold=True)
    r.set(7, KW + 8, 1, "gold_block")
    r.set(7, KW + 9, 1, "end_rod", facing="up")


L1_SY = KW + 5                # the lantern on the crown is the top (y 12)
L2_SY = KW + 9                # the angel's lantern (y 16)
L3_SY = KW + 10               # the needle (y 17)


def build(draw, sy, tier):
    r = Rig(sy)
    draw(r)
    r.settle()
    r.check_support(tier)
    return r.structure()


def l1():
    return build(draw_l1, L1_SY, "L1")


def l2():
    return build(draw_l2, L2_SY, "L2")


def l3():
    return build(draw_l3, L3_SY, "L3")


def base():
    """The L1 the game ships as barracks.nbt (gen_lab_levels writes it, like lab_graveyard.bases())."""
    return l1()


def levels():
    """(L2, L3) for gen_lab_levels.LABS["barracks"], like lab_graveyard.levels()."""
    return l2(), l3()


def names(s):
    return {p: s.palette[i][0] for p, i in s.blocks.items() if s.palette[i][0] not in ("air", "cave_air")}


FORBIDDEN = ("water", "lava", "sand", "red_sand", "gravel", "_concrete_powder", "anvil", "redstone", "repeater",
             "comparator", "observer", "piston", "lever", "_button", "_pressure_plate", "tnt", "fire", "campfire",
             "dragon_egg", "scaffolding", "pointed_dripstone", "_door", "_leaves", "_carpet")


def check_levels(s1, s2, s3, paths=None):
    """The rules the game needs, on the three finished Structures (support already ran in build())."""
    import audit_structure_pockets as ap
    levels = (("L1", s1), ("L2", s2), ("L3", s3))
    for tag, s in levels:
        assert (s.size[0], s.size[2]) == (SX, SZ), "%s: footprint %s, the Barracks' is 10 x 8" % (tag, s.size)
        n = names(s)
        assert max(p[1] for p in n) == s.size[1] - 1, "%s: the box is taller than its blocks" % tag
        assert all((x, 0, z) in n for x in range(SX) for z in range(SZ)), "%s: a hole in the foundation" % tag
        bad = sorted({nm for nm in n.values() if any(nm == f or nm.endswith(f) for f in FORBIDDEN)})
        assert not bad, "%s: forbidden blocks %s" % (tag, bad)
        layer0 = [p for p, nm in n.items() if nm == START_TYPE and p[1] == 0]
        above = [p for p, nm in n.items() if nm == START_TYPE and p[1] > 0]
        assert len(layer0) >= 6 and not above, "%s: starting type %s: %d in layer 0, %d above" % (
            tag, START_TYPE, len(layer0), len(above))
    # the chain: an upgrade only adds or replaces (BuildingPlacement.changeStructure / refreshBlocks)
    for (ta, a), (tb, b) in ((levels[0], levels[1]), (levels[1], levels[2])):
        lost = sorted(set(names(a)) - set(names(b)))
        assert not lost, "%s -> %s would leave blocks behind: %s" % (ta, tb, lost[:8])
    # the spawn: the four central columns open from the floor to one above L1's centre height at every level
    ytop = (s1.size[1] - 1) // 2 + 1
    for tag, s in levels:
        n = names(s)
        for (x, z) in CENTRE:
            assert (x, 0, z) in n, "%s: no floor under the spawn cell %d/%d" % (tag, x, z)
            blocked = [y for y in range(1, ytop + 1) if (x, y, z) in n]
            assert not blocked, "%s: the spawn column %d/%d is blocked at %s" % (tag, x, z, blocked)
    # pockets: tools/audit_structure_pockets.py on the written files, and every spawn cell walks out at every level
    report = []
    if paths:
        for (tag, s), path in zip(levels, paths):
            one_way, ground, roof, rooms, note = ap.audit(tag, path)
            assert not one_way and not ground and "TRAPPED" not in note, "%s: pockets %s %s %s" % (
                tag, one_way[:6], ground[:6], note)
            pad = ap.Pad(ap.load_nbt(path))
            for (x, z) in CENTRE:
                start = (x, 1, z)
                seen, stack, out = {start}, [start], False
                while stack and not out:
                    c = stack.pop()
                    if c[0] < 0 or c[2] < 0 or c[0] >= SX or c[2] >= SZ:
                        out = True
                        break
                    for nb in ap.neighbours(pad, *c):
                        if nb not in seen and -3 <= nb[0] <= SX + 2 and -3 <= nb[2] <= SZ + 2 \
                                and 1 <= nb[1] <= s.size[1] + 1:
                            seen.add(nb)
                            stack.append(nb)
                assert out, "%s: a unit spawned at %s can't walk out" % (tag, start)
            report.append("%s %dx%dx%d, %d blocks: no pockets (%d roof-only cells nothing can reach), the four spawn "
                          "cells walk out" % (tag, s.size[0], s.size[1], s.size[2], len(names(s)), len(roof)))
    return report


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else \
        "/tmp/claude-0/-home-claude/9aa3ec76-5285-5d94-a971-fdcb4e858c09/scratchpad/lab_barracks"
    os.makedirs(out, exist_ok=True)
    built = (l1(), l2(), l3())
    paths = [os.path.join(out, f) for f in ("barracks.nbt", "barracks_l2.nbt", "barracks_l3.nbt")]
    for s, path in zip(built, paths):
        s.write(path)
    for line in check_levels(*built, paths=paths):
        print(line)
