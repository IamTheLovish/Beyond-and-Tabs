#!/usr/bin/env python3
"""Foundry Hall massing greyboxes (Nov 2026, buildings lane): three silhouettes for the blind massing round.

Both baseline critics (runs 20261031-224047/-224049): the Grand Foundry reads as an "orange stepped pyramid" - the
church/temple read lovish rejected on the Engine Shed ("doesnt necessarily look like a factory") - and its orange
copper is team-like. Every option here recolours the copper to verdigris (exposed/cut -> oxidized) and keeps the
hall, towers, yard and golems; they differ in what stands on the hall:
  works  - a factory hall: a pitched verdigris roof with a raised clerestory MONITOR along the ridge, four fat 2 x 2
           smokestacks of different heights (the row of stacks is the factory icon), no clock dome;
  kiln   - a round BLAST FURNACE drum (radius 3, soot with verdigris bands) rising from a flat soot roof, one glowing
           throat on top, the same four fat stacks;
  recolour - the shipped hall and clock dome, only recoloured (the control for "is it massing or colour?").
Usage: python tools/foundry_hall_massing.py OUTDIR
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import capitol_foundry_hall as F   # noqa: E402
import capitol_town_centre as tc   # noqa: E402

CORNERS, O4, inward = tc.CORNERS, tc.O4, tc.inward
STACKS = {(-1, 1): 15, (1, 1): 18, (-1, -1): 18, (1, -1): 21}   # (-1, 1) is the corner nearest the camera


def recolour():
    F.CU = F.CU_E = "waxed_oxidized_cut_copper"


def dark_gable_eyes(c):
    """The two lit gable windows facing the camera read as a pair of eyes: dark iron instead."""
    for o in O4.values():
        c.put(o, 4, 0, 10, "iron_bars")
        c.put(o, 3, 0, 10, F.DO)


def fat_stacks(c):
    for (sx, sz), ty in STACKS.items():
        for qx in (4, 5):
            for qz in (4, 5):
                for y in range(11, ty):
                    c.set(sx * qx, y, sz * qz, "waxed_oxidized_cut_copper" if y % 4 == 0 else F.SB)
                c.set(sx * qx, ty, sz * qz, "campfire", **F.CAMPFIRE)


def works_roof(c):
    cu = "waxed_oxidized_cut_copper"
    for x in range(-3, 4):
        for s in (-1, 1):
            c.stair(x, 9, 4 * s, "north" if s > 0 else "south", name=cu + "_stairs")
            c.set(x, 9, 3 * s, F.DO)
            c.stair(x, 10, 3 * s, "north" if s > 0 else "south", name=cu + "_stairs")
            for y in (9, 10, 11):
                c.set(x, y, 2 * s, F.DO)
            for y in (12, 13):
                c.set(x, y, 2 * s, "gray_stained_glass")
        for z in range(-2, 3):
            c.slab(x, 14, z, cu + "_slab")
    for s in (-1, 1):                      # the gable ends on x = +-4
        for z in range(-3, 4):
            top = {3: 10, 2: 13}.get(abs(z), 13)
            for y in range(9, top + 1):
                c.set(4 * s, y, z, F.SB)
        for z in range(-2, 3):
            c.slab(4 * s, 14, z, cu + "_slab")


def kiln(c):
    cu = "waxed_oxidized_cut_copper"
    for dx, dz in c.cells(4):
        d2 = dx * dx + dz * dz
        if max(abs(dx), abs(dz)) >= 4 and abs(dx) >= 4 and abs(dz) >= 4:
            continue
        if d2 > 12:
            c.set(dx, 9, dz, F.DT)
        elif d2 >= 6:
            for y in range(9, 18):
                c.set(dx, y, dz, cu if y in (12, 16) else F.SB)
        elif d2 >= 3:
            c.set(dx, 18, dz, F.SB)
            c.set(dx, 17, dz, F.SB)
        else:
            c.set(dx, 16, dz, "shroomlight")
    for dx, dz in c.cells(4):
        if 6 <= dx * dx + dz * dz <= 12:
            c.set(dx, 18, dz, cu + "_slab", type="bottom", waterlogged="false")


def base(c):
    F.yard(c)
    F.plinth(c)
    F.hall(c)
    F.buttresses(c)
    F.towers(c)


def finish(c, name):
    F.crane(c)
    F.nave(c)
    F.golems(c)
    F.yard_dressing(c)
    c.rig.settle()
    c.rig.check_support("foundry_hall_" + name)
    return c.rig.structure()


def option(name):
    recolour()
    c = tc.Cap(F.R, F.H)
    if name == "recolour":
        base(c)
        F.roof(c)
        F.clock_tower(c)
        F.gables(c)
        dark_gable_eyes(c)
        F.pipes(c)
        F.pipe_bridges(c)
        F.gallery(c)
        return finish(c, name)
    base(c)
    fat_stacks(c)
    if name == "works":
        works_roof(c)
    else:
        kiln(c)
    return finish(c, name)


# ---- round 1 (both massing critics 6 / 6 picked `works`: the roof is one full-block plane - give it a works profile;
# one great stack leads; fewer, thinner bands; the teal roof is the biggest saturated area - break it / measure it) ----
OX = "waxed_oxidized_cut_copper"
STONE_ST, STONE_SL = "stone_stairs", "stone_slab"
RIBS = True


def clear_stacks(c):
    for (sx, sz) in CORNERS:
        for qx in (4, 5):
            for qz in (4, 5):
                for y in range(11, F.H):
                    c.set(sx * qx, y, sz * qz, "air")


def lead_stacks(c):
    """One GREAT STACK (2 x 2, the far corner, to the top, one hoop and a collar, the one big plume); a 1 x 1 stack
    on the left corner with the second plume; a short capped vent on the right; the near corner keeps its parapet."""
    clear_stacks(c)
    for (sx, sz) in CORNERS:
        out_x, out_z = ("east" if sx > 0 else "west"), ("south" if sz > 0 else "north")
        c.stair(sx * 4, 11, sz * 4, out_z, name=F.SBS)
        c.stair(sx * 5, 11, sz * 4, out_z, name=F.SBS)
        c.stair(sx * 4, 11, sz * 5, out_x, name=F.SBS)
        c.stair(sx * 5, 11, sz * 5, out_x, name=F.SBS)
    for qx in (4, 5):
        for qz in (-4, -5):
            for y in range(11, 21):
                c.set(qx, y, qz, OX if y in (15, 19) else F.SB)
            c.set(qx, 21, qz, "campfire", **F.CAMPFIRE)
    for y in range(11, 17):
        c.set(-5, y, -5, OX if y == 15 else F.SB)
    c.set(-5, 17, -5, "campfire", **F.CAMPFIRE)
    for y in range(11, 14):
        c.set(5, y, 5, F.SB)
    c.slab(5, 14, 5, OX + "_slab")


def risers(c):
    """The orange-copper accent kept from the shipped kit: the pipes climbing the towers' outer faces and the pipe under
    each cornice."""
    for (sx, sz) in CORNERS:
        for (x, z) in ((sx * 6, sz * 4), (sx * 4, sz * 6)):
            for y in range(2, 10):
                c.set(x, y, z, "lightning_rod", facing="up", **F.ROD)
    for o in O4.values():
        for t in range(-3, 4):
            if abs(t) != 2:
                c.put(o, 5, t, 8, "lightning_rod", facing=tc.along(o, 1), **F.ROD)


def monitor_roof(c, slope, cap):
    """Pitched side roofs (z +-4 / +-3) with two dark-oak ribs, a raised clerestory MONITOR (glazed z +-2, y12-13)
    carrying its own pitched roof and a verdigris ridge with rod finials; gable ends on x +-4 with a brass gear."""
    def stair(x, y, z, f, nm):
        c.stair(x, y, z, f, name=nm)
    for x in range(-3, 4):
        rib = abs(x) == 2 and RIBS
        for s in (-1, 1):
            f = "north" if s > 0 else "south"
            stair(x, 9, 4 * s, f, F.DOS if rib else slope + "_stairs" if slope == OX else STONE_ST if slope == "stone"
                  else slope)
            c.set(x, 9, 3 * s, F.DO)
            stair(x, 10, 3 * s, f, F.DOS if rib else slope + "_stairs" if slope == OX else STONE_ST)
            for y in (9, 10, 11):
                c.set(x, y, 2 * s, F.DO)
            for y in (12, 13):
                c.set(x, y, 2 * s, "gray_stained_glass")
            stair(x, 14, 2 * s, f, cap + "_stairs" if cap == OX else STONE_ST)
            c.set(x, 14, 1 * s, F.DO)
            c.slab(x, 15, 1 * s, cap + "_slab" if cap == OX else STONE_SL)
        c.set(x, 15, 0, OX)
    for s in (-1, 1):
        x = 4 * s
        for z in range(-3, 4):
            top = {3: 10, 2: 13, 1: 14, 0: 15}[abs(z)]
            for y in range(9, top + 1):
                c.set(x, y, z, F.SB)
        c.stair(x, 14, 2, "north", name=F.SBS)
        c.stair(x, 14, -2, "south", name=F.SBS)
        c.stair(x, 15, 1, "north", name=F.SBS)
        c.stair(x, 15, -1, "south", name=F.SBS)
        c.set(x, 16, 0, "lightning_rod", facing="up", **F.ROD)
        xo = 5 * s                                  # the gear on the gable end: a brass diamond round an iron hub
        for (z, y) in ((0, 10), (0, 12), (-1, 11), (1, 11)):
            c.set(xo, y, z, F.CUB)
        c.set(xo, 11, 0, "iron_block")


def round1(slope, cap):
    recolour()
    c = tc.Cap(F.R, F.H)
    base(c)
    lead_stacks(c)
    risers(c)
    monitor_roof(c, slope, cap)
    return finish(c, "r1")


# ---- round 2 (round-1 critics 6 / 6 picked r1b - verdigris slopes, grey monitor roof): the gable's copper plus reads
# as a church cross -> an iron louvre; the grey roof's stone mottle reads as rubble -> polished andesite; banded stacks
# = stripes -> one collar under the plume; four-corner symmetry = chapel/mosque -> both stacks on the east end (a
# works line), a crane mast at the west; the buttress lanterns read as eyes -> unlit; the dark body -> (r2a) hall
# panels in pale-mid polished andesite between the dark-oak posts ----
PA = "polished_andesite"


def r2_stacks(c):
    clear_stacks(c)
    for (sx, sz) in CORNERS:
        out_x, out_z = ("east" if sx > 0 else "west"), ("south" if sz > 0 else "north")
        for (qx, qz, f) in ((4, 4, out_z), (5, 4, out_z), (4, 5, out_x), (5, 5, out_x)):
            c.stair(sx * qx, 11, sz * qz, f, name=F.SBS)
    for qx in (4, 5):
        for qz in (-4, -5):
            for y in range(11, 21):
                c.set(qx, y, qz, OX if y == 20 else F.SB)
            c.set(qx, 21, qz, "campfire", **F.CAMPFIRE)
    for y in range(11, 17):
        c.set(5, y, 5, OX if y == 16 else F.SB)
    c.set(5, 17, 5, "campfire", **F.CAMPFIRE)
    for y in range(11, 15):                          # the crane's mast
        c.set(-5, y, -5, F.DOL, axis="y")


def r2_gable_louvre(c):
    for s in (-1, 1):
        for (z, y) in ((0, 10), (0, 12), (-1, 11), (1, 11)):
            c.set(5 * s, y, z, "air")
        c.set(5 * s, 11, 0, "air")
        for z in (-1, 0, 1):
            for y in (11, 12):
                c.set(4 * s, y, z, "iron_bars")
        for z in (-1, 0, 1):
            c.set(3 * s, 11, z, F.DO) if c.get(3 * s, 11, z)[0] in ("air", "cave_air") else None
            c.set(3 * s, 12, z, F.DO) if c.get(3 * s, 12, z)[0] in ("air", "cave_air") else None


def round2(pale_walls):
    recolour()
    c = tc.Cap(F.R, F.H)
    base(c)
    r2_stacks(c)
    risers(c)
    global STONE_ST, STONE_SL
    STONE_ST, STONE_SL = PA + "_stairs", PA + "_slab"
    monitor_roof(c, OX, "stone")
    STONE_ST, STONE_SL = "stone_stairs", "stone_slab"
    r2_gable_louvre(c)
    for o in O4.values():
        for t in range(-3, 4):
            if abs(t) != 2:
                c.put(o, 5, t, 8, F.SBW)                 # a soot pipe under the cornice, not orange rods
        for s_ in (-1, 1):
            c.put(o, 5, 2 * s_, 7, "air")              # the buttress lanterns (eyes) go
            if pale_walls:
                for y in range(2, 9):
                    for t in (-3, -1, 0, 1, 3):
                        if c.get(*tc.Cap.side(o, 4, t)[:1], y, tc.Cap.side(o, 4, t)[1])[0] == F.DO:
                            c.put(o, 4, t, y, PA)
    if pale_walls:                                   # the gable ends and the monitor's sill course too
        for sx in (-4, 4):
            for z in range(-3, 4):
                for y in range(9, 16):
                    if c.get(sx, y, z)[0] == F.SB:
                        c.set(sx, y, z, PA)
        for x in range(-3, 4):
            for z in (-2, 2):
                c.set(x, 11, z, PA)
    return finish(c, "r2")


# ---- round 3 (round-2 critics: r2a 6.5 / 6, r2b 6 / 6.5 - r2a's pale gables and vent grille kept, its value plan the
# clearer): one side becomes the WORKS side - the east gable gets the furnace (a row of lit blast furnaces under a
# hood: the one warm focal glow), a coal heap and rail spur in the east yard; the symmetric glows go dark (tower
# smokers, golem faces); the orange rod "pins/ladders" stay only on the two east towers; no dark-oak rib stripes; the
# teal corner "toy lumps" become pale andesite towers with a verdigris band; the crane ("gallows") goes ----
def round3(deep=False):
    """The Works (Nov 2026; deep=True: round 4's roof - tools/capitol_foundry_hall.build() returns that). The
    generator's copper constants are recoloured for the draw and restored after it."""
    global RIBS
    saved = (F.CU, F.CU_E)
    RIBS = False
    try:
        return _round3(deep)
    finally:
        F.CU, F.CU_E = saved
        RIBS = True


# ---- round 4 (Fable review of PR #485, Nov 4: at the RTS camera 55 % of the Works' pixels are up-faces and the roof
# zone 38 % of them, yet the roof was two stair courses flush with the pier faces - G1 20 / 84, no eave shadow band -
# under a pale monitor box that read as a second storey; design/architecture_rules.md 9 "the roof is the fifth
# facade", 2 "measured: eave overhang 1 block everywhere"): a 45-degree verdigris slope FOUR courses deep from an EAVE
# that oversails the piers (ring 6), its eave course in wet, darker weathered copper (the gutter line),
# the courses above clean oxidized - the monitor sunk into the slope (two courses of glass
# over its top course, the pale cap kept), a VERGE one proud on both gables. Nothing else moves (one change per
# element per round) ----
WET = "waxed_weathered_cut_copper"          # pack luma 0.46 against oxidized 0.55: the wet eave tone
COURSES = {6: 9, 5: 10, 4: 11, 3: 12}       # ring -> the course's y, eave to monitor


def deep_roof(c):
    for x in range(-5, 6):                   # clear round 3's roof between the towers and both gable ends (the
        for z in range(-6, 7):               # cells go, not explicit air: the nbt stays as lean as round 3's)
            if abs(x) <= 3 or abs(z) <= 3:
                for y in range(9, F.H):
                    c.rig.b.pop((c.R + x, y, c.R + z), None)
    for s in (-1, 1):
        f = "north" if s > 0 else "south"    # the tall side inward: the slope climbs to the ridge
        for r, y in COURSES.items():
            for i, x in enumerate(range(-3, 4)):
                # the eave course is the wet line - the pack's weathered copper carries rust blooms, which read as
                # a rusty gutter in one continuous course and as brown speckle when scattered in runs (tried first)
                c.stair(x, y, s * r, f, name=(WET if r == 6 else OX) + "_stairs")
                for yy in range(9, y):       # sarking under the course (unseen, the roof's own colour)
                    c.set(x, yy, s * r, OX)
        for x in range(-3, 4):               # the monitor wall sunk into the slope: dark oak under it, a sill, glass, cap
            for y in (9, 10, 11):
                c.set(x, y, 2 * s, F.DO)
            c.set(x, 12, 2 * s, PA)
            for y in (13, 14):
                c.set(x, y, 2 * s, "gray_stained_glass")
            c.stair(x, 15, 2 * s, f, name=PA + "_stairs")
            c.set(x, 15, s, F.DO)
            c.slab(x, 16, s, PA + "_slab")
    for x in range(-3, 4):
        c.set(x, 16, 0, OX)                  # the ridge
    for sx in (-1, 1):
        x4, x5 = 4 * sx, 5 * sx
        for z in range(-3, 4):               # round 3's string course under the gable, then the gable wall
            c.stair(x5, 9, z, "east" if sx < 0 else "west", "top", OX + "_stairs")
            for y in range(9, {3: 11, 2: 14, 1: 15, 0: 16}[abs(z)] + 1):
                c.set(x4, y, z, PA)
        for z in (-3, 3):                    # the slope's top course over the gable wall, and the verge one proud
            f = "north" if z > 0 else "south"
            c.stair(x4, COURSES[3], z, f, name=OX + "_stairs")
            c.stair(x5, COURSES[3], z, f, name=OX + "_stairs")
        for (z, y) in ((2, 15), (-2, 15), (1, 16), (-1, 16)):   # the monitor gable's rake and its verge
            f = "north" if z > 0 else "south"
            c.stair(x4, y, z, f, name=F.SBS)
            c.stair(x5, y, z, f, name=PA + "_stairs")
        c.set(x5, 16, 0, PA)
        c.set(x4, 17, 0, "lightning_rod", facing="up", **F.ROD)
        for z in (-1, 0, 1):                 # the vent louvre (round 2's grille) in the gable, dark oak behind it
            for y in (12, 13):
                c.set(x4, y, z, "iron_bars")
                c.set(3 * sx, y, z, F.DO)


def _round3(deep=False):
    recolour()
    c = tc.Cap(F.R, F.H)
    base(c)
    r2_stacks(c)
    for y in range(11, 15):
        c.set(-5, y, -5, "air")
    for (sx, sz) in CORNERS:                        # pale towers, a verdigris band at the top, unlit smokers
        for qx in (4, 5):
            for qz in (4, 5):
                for y in range(3, 11):
                    if c.get(sx * qx, y, sz * qz)[0].startswith("waxed_"):
                        c.set(sx * qx, y, sz * qz, OX if y == 10 else PA)
        for (x, z) in ((sx * 5, sz * 4), (sx * 4, sz * 5)):
            nm, pr = c.get(x, 2, z)
            if nm == "smoker":
                c.set(x, 2, z, "smoker", facing=dict(pr)["facing"], lit="false")
            for y in (4, 8):
                c.set(x, y, z, PA)
        if sx > 0:                                  # the east towers keep the copper pipes
            for (x, z) in ((sx * 6, sz * 4), (sx * 4, sz * 6)):
                for y in range(2, 10):
                    c.set(x, y, z, "lightning_rod", facing="up", **F.ROD)
    global STONE_ST, STONE_SL
    STONE_ST, STONE_SL = PA + "_stairs", PA + "_slab"
    monitor_roof(c, OX, "stone")
    STONE_ST, STONE_SL = "stone_stairs", "stone_slab"
    r2_gable_louvre(c)
    for o in O4.values():
        for t in range(-3, 4):
            if abs(t) != 2:
                c.put(o, 5, t, 8, F.SBW)
        for k in (-1, 1):
            c.put(o, 5, 2 * k, 7, "air")
            for y in range(2, 9):
                for t in (-3, -1, 0, 1, 3):
                    x, z = tc.Cap.side(o, 4, t)
                    if c.get(x, y, z)[0] == F.DO:
                        c.put(o, 4, t, y, PA)
    for sx in (-4, 4):
        for z in range(-3, 4):
            for y in range(9, 16):
                if c.get(sx, y, z)[0] == F.SB:
                    c.set(sx, y, z, PA)
    for x in range(-3, 4):
        for z in (-2, 2):
            c.set(x, 11, z, PA)
    if deep:
        deep_roof(c)
    # the works side: the east portal's head becomes a furnace hood - three lit blast furnaces at y5-6 under a soot
    # hood (the steam spot, BuildingAmbienceClientEvents), a coal heap and a rail spur running out of the portal
    for z in (-1, 0, 1):
        c.set(4, 6, z, "blast_furnace", facing="east", lit="true")
        c.stair(5, 7, z, "west", "top", F.SBS)
    for (x, z) in ((6, -3), (6, -2), (7, -3), (6, 2), (7, 3)):
        c.set(x, 1, z, "coal_block")
    for x in (5, 6, 7):
        c.set(x, 1, 0, "rail", shape="east_west", waterlogged="false")
    F.nave(c)
    F.golems(c)
    for (sx, sz) in CORNERS:
        c.set(sx * 6, 4, sz * 6, OX)                # the golem faces unlit (the dusk "eyes")
    F.yard_dressing(c)
    for x in (5, 6, 7):
        for z in (-1, 0, 1):
            if c.get(x, 1, z)[0] in ("barrel", "dark_oak_fence", "lantern") and z != 0:
                c.set(x, 1, z, "air")
    c.rig.settle()
    c.rig.check_support("foundry_hall_r4" if deep else "foundry_hall_r3")
    return c.rig.structure()


if __name__ == "__main__":
    out = sys.argv[1]
    for nm in sys.argv[2:] or ("works", "kiln", "recolour"):
        s = round1("stone", OX) if nm == "r1a" else round1(OX, "stone") if nm == "r1b" \
            else round2(nm == "r2a") if nm in ("r2a", "r2b") else round3() if nm == "r3" \
            else round3(deep=True) if nm == "r4" else option(nm)
        s.write(os.path.join(out, nm + ".nbt"))
        print(nm, sum(1 for i in s.blocks.values() if s.palette[i][0] != "air"))
