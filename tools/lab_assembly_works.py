#!/usr/bin/env python3
"""The Brassworks T2 lab, the ASSEMBLY WORKS, redrawn at all three levels - assembly_works.nbt (L1, THE WORKS),
assembly_works_l2.nbt (THE ENGINE WORKS) and assembly_works_l3.nbt (THE CLOCKWORK WORKS).

Why (Nov 6 2026): the all-factions blind Fable sweep (eyes on main jar 42137577884, 62 buildings) scored the old Works
3 / 10 (massing 2, detail 2): "one flat sheet of cut copper is ~70% of the silhouette at RTS; the rest is a deepslate box
with four windows", "a box with a lid"; at dusk "the copper roof turns a flat dull mauve and the building vanishes".
The old Works also spawned its units inside the gantry beam (the column over the centre held a chain, a lantern, the
beam and the deck).

THE REFERENCE: design/refs/vehicle_labs/ref04_stacked_timber_village.png, read for its Brassworks lesson - mass by
ACCRETION: sheds and workshops grown onto the main hall, each volume with its own roof, mixed pitches and heights, never
one big box. (ref18 is the Engine Shed's; this is a different Brassworks archetype: a north-light factory, not a hall
with a central tower.) The faction kit: soot (polished blackstone, deepslate) and brick, brass as the accent stripe,
furnace orange in the openings.

THE PLAN (11 x 11, the old footprint; x 0 west .. 10 east, z 0 north .. 10 south; the RTS camera looks from the
south-west and sees the roofs, the west face x 0 and the south face z 10):
  - the HALL (x 3..8, z 1..9): deepslate brick walls to y 7 between blackstone-brick corner piers capped in chiseled
    blackstone, a blackstone-brick course at y 5 on every face; a 3-wide, 4-high door on the south (x 4..6) under a
    lintel and an orange clerestory (y 6); a lit blast furnace on the back wall (5, 1, 2) glows straight out of the door.
    The ROOF is a SAWTOOTH of three north-light teeth (z 1..3, 4..6, 7..9), each in three courses:
        z n     : north-light glass y 8..9, a blackstone-brick RIDGE at y 10 (dark cap)
        z n + 1 : waxed cut copper stairs at y 9 (the brass stripe)
        z n + 2 : waxed EXPOSED cut copper stairs at y 8 (the weathered brass gutter course, a darker tone)
    with blackstone-brick stair VERGES (bargeboards) at x 3 and x 8 over deepslate-brick gables, and a deepslate-tile
    DRIP slab one proud of the south eave (y 7, z 10). From the camera: dark ridge / bright brass / weathered brass, three times - a jagged
    skyline instead of one copper plane.
  - the CHIMNEY (x 0..2, z 0..2, north-west): a 3 x 3 blackstone-plinthed brick base to y 4, a stair taper at y 5, a
    1 x 1 brick shaft to y 12 with ONE brass band (y 11) under ONE corbelled crown (upside-down blackstone-brick
    stairs, y 12) and a lit campfire's smoke on top (y 13): the landmark, the tallest thing on the plot.
  - the WORKSHOP lean-to (x 0..2, z 3..7, west, the camera's face): brick walls to y 3 between blackstone posts, an
    orange window, a deepslate-tile lean-to roof stepping y 4 -> 6 against the hall, open into the hall at y 1..3.
  - the OFFICE (x 0..2, z 8..10, south-west corner): a solid brick block to y 8 on a blackstone plinth, blackstone
    quoins, a pale polished-andesite course at y 5 (the Brassworks light group), orange windows at y 3 and y 7, a
    deepslate-tile hip at y 9 under a brass finial and a lightning rod (y 10..11).
  - the BOILER HOUSE lean-to (x 9..10, z 2..9, east): solid brick to y 3, a lit blast furnace in its south face, a
    deepslate-tile lean-to roof (y 4..5); the brass TANK (x 9..10, z 0..1) behind it.
  - the APRON (z 10): lantern posts flanking the way out (x 3 and x 7).
  Height bands at L1: lean-tos 4..6, hall 8..10, office 9..11, chimney 13.
  L2 THE ENGINE WORKS: the chimney's base climbs as a 3 x 3 brick stack to y 12 (a brass ring at y 9) and the shaft
     goes on to a new crown at y 16 (smoke at y 17); the office's upper windows, the hall's clerestory centre and the
     boiler house window light up (shroomlight); rails run out of the door; the tank grows; steam vents on the
     boiler roof.
  L3 THE CLOCKWORK WORKS: the office rises into a clock tower (lit clock faces west and south, a brass band, a
     deepslate-tile hip and a brass finial to y 17); the chimney's stack climbs to y 16 and its shaft to a crown at
     y 20 (smoke at y 21); more lit windows; brass vents on the hall's ridges.

GAME RULES (asserted in check_levels and tools/test_lab_assembly_works.py):
  - 11 x 11 at every level; the box ends at the top block; L1 <= 16 tall, L3 <= 32 (the T2 size ladder);
  - AssemblyWorks.startingBlockTypes = polished_blackstone, deepslate_bricks: both in L1's layer 0;
  - the chain: every non-air cell of a level is non-air in the next (changeStructure only adds or replaces);
  - the spawn: ProductionBuilding.getIndoorSpawnPoint walks down from centrePos, the middle cell (5, 5) of the box at
    y = (height - 1) / 2; an upgrade keeps L1's, but a saved game reloaded at L2 / L3 measures it off that level's
    taller box, so the column is open from y 1 to one above each level's own centre height (a hole in the middle
    tooth's brass course at (5, 9, 5)),
    and a 3-wide way (x 4..6, y 1..3) runs from it out of the south door to the plot's edge (the Steam Juggernaut,
    spawnRadiusOffset 2); no pockets (tools/audit_structure_pockets.py);
  - no fluids, gravity blocks (no anvils), doors or leaves; every block is a Brassworks BiomePalette decision (shared
    keeps: blackstone, deepslate, copper, glass, lights; the faction keeps polished andesite; bricks as the old Works).

Usage: python3 tools/lab_assembly_works.py [OUT_DIR ...]   (default: both shipped copies)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs   # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHIPPED = [os.path.join(ROOT, "bar", "src", "main", "resources", d, "reignofnether", "structures")
           for d in ("assets", "data")]
FILES = ("assembly_works.nbt", "assembly_works_l2.nbt", "assembly_works_l3.nbt")
S = 11                      # the footprint, x and z
HEIGHT = {1: 14, 2: 18, 3: 22}
SPAWN = (5, 5)
START = ("polished_blackstone", "deepslate_bricks")
FORBIDDEN = ("water", "lava", "sand", "red_sand", "gravel", "_concrete_powder", "anvil", "_door", "_leaves")

SOOT, SOOT_B, SOOT_CH = "polished_blackstone", "polished_blackstone_bricks", "chiseled_polished_blackstone"
SOOT_ST, SOOT_SL = "polished_blackstone_brick_stairs", "polished_blackstone_brick_slab"
DSB, TILE, TILE_ST, TILE_SL = "deepslate_bricks", "deepslate_tiles", "deepslate_tile_stairs", "deepslate_tile_slab"
BRICK, PALE = "bricks", "polished_andesite"
BRASS, BRASS_ST, BRASS_SL, BRASS_BLOCK = "waxed_cut_copper", "waxed_cut_copper_stairs", "waxed_cut_copper_slab", "waxed_copper_block"
BRASS_OLD_ST = "waxed_exposed_cut_copper_stairs"   # the second, darker brass tone (detail rule 7: two tones per plane)
ORANGE, GLASS, LIT = "orange_stained_glass", "glass", "shroomlight"
TEETH = (1, 4, 7)          # the sawtooth's ridge rows (z)
CH = (1, 1)                # the chimney shaft's column
OF = (1, 9)                # the office's centre column


def stair(s, x, y, z, name, facing, half="bottom"):
    s.set(x, y, z, name, facing=facing, half=half, shape="straight", waterlogged="false")


def slab(s, x, y, z, name, kind="bottom"):
    s.set(x, y, z, name, type=kind, waterlogged="false")


def toward(cx, cz, x, z):
    """The stair facing whose high back is on the (cx, cz) side of (x, z)."""
    if x < cx:
        return "east"
    if x > cx:
        return "west"
    return "south" if z < cz else "north"


def ring(cx, cz):
    """The 8 cells round (cx, cz): (x, z, is_corner)."""
    return [(cx + dx, cz + dz, dx != 0 and dz != 0) for dx in (-1, 0, 1) for dz in (-1, 0, 1) if dx or dz]


def taper(s, y):
    """The chimney's stair taper at y round the 1 x 1 shaft: stairs on the sides, slabs on the corners."""
    for x, z, corner in ring(*CH):
        if corner:
            slab(s, x, y, z, SOOT_SL)
        else:
            stair(s, x, y, z, SOOT_ST, toward(CH[0], CH[1], x, z))


def crown(s, y):
    """ONE corbelled crown at y: the shaft block and upside-down stairs round it (corners top slabs), smoke above."""
    s.set(CH[0], y, CH[1], SOOT_B)
    for x, z, corner in ring(*CH):
        if corner:
            slab(s, x, y, z, SOOT_SL, "top")
        else:
            stair(s, x, y, z, SOOT_ST, toward(CH[0], CH[1], x, z), half="top")
    s.set(CH[0], y + 1, CH[1], "campfire", **gs.CAMPFIRE)


def shaft(s, y0, y1, band):
    for y in range(y0, y1 + 1):
        s.set(CH[0], y, CH[1], BRASS if y == band else BRICK)


def stack(s, y0, y1, band):
    """The chimney's 3 x 3 brick stack from y0 to y1 with a brass ring at `band`."""
    for y in range(y0, y1 + 1):
        for x, z, corner in ring(*CH):
            s.set(x, y, z, BRASS if y == band and not corner else BRICK)
        s.set(CH[0], y, CH[1], BRICK)


def lantern_post(s, x, z):
    s.set(x, 1, z, "polished_blackstone_wall", up="true", north="none", south="none", east="none", west="none",
          waterlogged="false")
    s.set(x, 2, z, "lantern", hanging="false", waterlogged="false")


def draw_l1(s):
    # ground: the deepslate-brick kerb round a polished-blackstone floor (the foundation queue's starting types)
    for x in range(S):
        for z in range(S):
            s.set(x, 0, z, DSB if x in (0, S - 1) or z in (0, S - 1) else SOOT)

    # ---- the HALL (x 3..8, z 1..9)
    for x in range(3, 9):
        for z in range(1, 10):
            if 3 < x < 8 and 1 < z < 9:
                continue                            # the hall floor stays open
            corner = x in (3, 8) and z in (1, 9)
            for y in range(1, 8):
                if corner:
                    s.set(x, y, z, SOOT_CH if y == 7 else SOOT_B)
                elif y == 5:
                    s.set(x, y, z, SOOT_B)          # the course line on every face
                else:
                    s.set(x, y, z, DSB)
    # the south face: the 3-wide door under its lintel (the course), an orange clerestory over it; a 2-high window
    # strip at x 7 on a blackstone sill
    for x in (4, 5, 6):
        for y in range(1, 5):
            s.blocks.pop((x, y, 9), None)
        s.set(x, 6, 9, ORANGE)
    s.set(7, 1, 9, SOOT)
    s.set(7, 2, 9, ORANGE)
    s.set(7, 3, 9, ORANGE)
    s.set(7, 6, 9, ORANGE)
    # the west face opens into the workshop at y 1..3 between posts at z 3 and z 7; a lintel course at y 4
    for z in (4, 5, 6):
        for y in (1, 2, 3):
            s.blocks.pop((3, y, z), None)
        s.set(3, 4, z, SOOT_B)
    for z in (3, 7):
        for y in range(1, 8):
            s.set(3, y, z, SOOT_B)
    # the back wall: a lit blast furnace (it glows out of the door), the boiler drum over it, smithing tables
    s.set(5, 1, 2, "blast_furnace", facing="south", lit="true")
    s.set(5, 2, 2, BRASS_BLOCK)
    s.set(5, 3, 2, BRASS_BLOCK)
    s.set(4, 1, 2, "smithing_table")
    s.set(6, 1, 2, "smithing_table")
    # the SAWTOOTH roof: three north-light teeth
    for zn in TEETH:
        for x in range(3, 9):
            verge = x in (3, 8)
            for y in (8, 9):
                s.set(x, y, zn, DSB if verge else GLASS)
            s.set(x, 10, zn, SOOT_B)                                    # the ridge
            if verge:
                s.set(x, 8, zn + 1, DSB)                                # the gable under the verge
                stair(s, x, 9, zn + 1, SOOT_ST, "north")
                stair(s, x, 8, zn + 2, SOOT_ST, "north")
            else:
                stair(s, x, 9, zn + 1, BRASS_ST, "north")               # the brass stripe
                stair(s, x, 8, zn + 2, BRASS_OLD_ST, "north")           # the weathered brass gutter course
    for x in range(3, 9):
        slab(s, x, 7, 10, TILE_SL, "top")                               # the drip, one proud of the south eave
    # the spawn's smoke hole: a reloaded L2 / L3 measures centrePos off its taller box (up to y 10), so the middle
    # tooth's brass course stays open over (5, 5) at every level, or units would spawn on the roof
    s.blocks.pop((SPAWN[0], 9, SPAWN[1]))

    # ---- the CHIMNEY (x 0..2, z 0..2)
    for x, z, _ in ring(*CH) + [(CH[0], CH[1], False)]:
        s.set(x, 1, z, SOOT_B)
        for y in (2, 3, 4):
            s.set(x, y, z, BRICK)
    taper(s, 5)
    shaft(s, 5, 11, band=11)
    crown(s, 12)

    # ---- the WORKSHOP lean-to (x 0..2, z 3..7)
    for z in range(3, 8):
        for y in (1, 2, 3):
            s.set(0, y, z, SOOT_B if z in (3, 7) else BRICK)
        stair(s, 0, 4, z, SOOT_ST, "east")                              # the eave course
        stair(s, 1, 5, z, TILE_ST, "east")
        stair(s, 2, 6, z, TILE_ST, "east")
    s.set(0, 2, 5, ORANGE)
    s.set(1, 1, 4, "smithing_table")
    s.set(1, 1, 6, "grindstone", face="floor", facing="east")

    # ---- the OFFICE (x 0..2, z 8..10): a solid block, quoins, a pale course, windows, a hip and a brass finial
    for x in range(0, 3):
        for z in range(8, 11):
            corner = x in (0, 2) and z in (8, 10)
            s.set(x, 1, z, SOOT_B)
            for y in range(2, 9):
                s.set(x, y, z, SOOT_B if corner else PALE if y == 5 else BRICK)
    for (x, z) in ((0, 9), (1, 10)):
        s.set(x, 3, z, ORANGE)
        s.set(x, 7, z, ORANGE)
    for x, z, corner in ring(*OF):
        if corner:
            slab(s, x, 9, z, TILE_SL)
        else:
            stair(s, x, 9, z, TILE_ST, toward(OF[0], OF[1], x, z))
    s.set(OF[0], 9, OF[1], TILE)
    s.set(OF[0], 10, OF[1], BRASS)
    s.set(OF[0], 11, OF[1], "lightning_rod", facing="up", powered="false", waterlogged="false")

    # ---- the BOILER HOUSE lean-to (x 9..10, z 2..9) and the TANK (x 9..10, z 0..1)
    for x in (9, 10):
        for z in range(2, 10):
            for y in (1, 2, 3):
                post = x == 10 and z in (2, 9)
                s.set(x, y, z, SOOT_B if post else BRICK)
            stair(s, 10, 4, z, SOOT_ST, "west")
            stair(s, 9, 5, z, TILE_ST, "west")
    s.set(9, 1, 9, "blast_furnace", facing="south", lit="true")
    s.set(9, 2, 9, ORANGE)
    for x in (9, 10):
        for z in (0, 1):
            s.set(x, 1, z, BRASS_BLOCK)
            s.set(x, 2, z, BRASS_BLOCK)
            slab(s, x, 3, z, BRASS_SL)

    # ---- the APRON: lantern posts flanking the way out
    lantern_post(s, 3, 10)
    lantern_post(s, 7, 10)


def draw_l2(s):
    # the chimney's stack climbs to y 12 with a brass ring at y 9; the shaft goes on to a new crown at y 16
    stack(s, 5, 12, band=9)
    taper(s, 13)
    shaft(s, 13, 15, band=15)
    crown(s, 16)
    # lit windows: the office's upper pair, the clerestory's centre, the boiler house window
    s.set(0, 7, 9, LIT)
    s.set(1, 7, 10, LIT)
    s.set(5, 6, 9, LIT)
    s.set(9, 2, 9, LIT)
    # rails out of the door to the plot's edge (no rail in the spawn column)
    for z in range(6, 11):
        s.set(5, 1, z, "rail", shape="north_south", waterlogged="false")
    # the tank grows a storey; steam vents on the boiler house's roof
    for x in (9, 10):
        for z in (0, 1):
            s.set(x, 3, z, BRASS_BLOCK)
            slab(s, x, 4, z, BRASS_SL)
    for z in (3, 6):
        s.set(9, 6, z, "lightning_rod", facing="up", powered="false", waterlogged="false")


def draw_l3(s):
    # the office rises into the clock tower
    for x, z, corner in ring(*OF) + [(OF[0], OF[1], False)]:
        for y in range(9, 14):
            s.set(x, y, z, SOOT_B if corner else BRICK)
        s.set(x, 14, z, BRASS if not corner else SOOT_B)
    s.set(0, 12, 9, LIT)                                                # the clock faces, lit
    s.set(1, 12, 10, LIT)
    for x, z, corner in ring(*OF):
        if corner:
            slab(s, x, 15, z, TILE_SL)
        else:
            stair(s, x, 15, z, TILE_ST, toward(OF[0], OF[1], x, z))
    s.set(OF[0], 15, OF[1], TILE)
    s.set(OF[0], 16, OF[1], BRASS)
    s.set(OF[0], 17, OF[1], "lightning_rod", facing="up", powered="false", waterlogged="false")
    # the chimney's stack climbs to y 16 (a second ring at y 15); the shaft goes on to the crown at y 20
    stack(s, 13, 16, band=15)
    taper(s, 17)
    shaft(s, 17, 19, band=19)
    crown(s, 20)
    # more lit windows; brass vents on the hall's ridges
    s.set(4, 6, 9, LIT)
    s.set(6, 6, 9, LIT)
    s.set(0, 2, 5, LIT)
    for zn in TEETH:
        for x in (4, 7):
            s.set(x, 11, zn, "lightning_rod", facing="up", powered="false", waterlogged="false")


def build(level):
    s = gs.Structure(S, HEIGHT[level], S)
    draw_l1(s)
    if level >= 2:
        draw_l2(s)
    if level >= 3:
        draw_l3(s)
    gs.fill_air(s)
    return s


def l1():
    return build(1)


def levels():
    return build(2), build(3)


def names(s):
    return {p: s.palette[i][0] for p, i in s.blocks.items() if s.palette[i][0] not in ("air", "cave_air")}


def check_levels(s1, s2, s3, paths=None):
    """The rules the game needs, on the three finished Structures."""
    import audit_structure_pockets as ap
    levels_ = (("L1", s1), ("L2", s2), ("L3", s3))
    for tag, s in levels_:
        n = names(s)
        assert (s.size[0], s.size[2]) == (S, S), "%s: footprint %s, the Works' is 11 x 11" % (tag, s.size)
        assert max(p[1] for p in n) == s.size[1] - 1, "%s: the box is taller than its blocks" % tag
        assert all((x, 0, z) in n for x in range(S) for z in range(S)), "%s: a hole in the foundation" % tag
        bad = sorted({nm for nm in n.values() if any(nm == f or nm.endswith(f) for f in FORBIDDEN)})
        assert not bad, "%s: forbidden blocks %s" % (tag, bad)
    assert s1.size[1] <= 16 and s3.size[1] <= 32, "the T2 size ladder: %d / %d" % (s1.size[1], s3.size[1])
    layer0 = {nm for p, nm in names(s1).items() if p[1] == 0}
    assert set(START) <= layer0, "L1 layer 0 lacks a starting type: %s" % sorted(layer0)
    for (ta, a), (tb, b) in ((levels_[0], levels_[1]), (levels_[1], levels_[2])):
        lost = sorted(set(names(a)) - set(names(b)))
        assert not lost, "%s -> %s would leave blocks behind: %s" % (ta, tb, lost[:8])
    for tag, s in levels_:
        n = names(s)
        ytop = (s.size[1] - 1) // 2 + 1         # centrePos off this level's own box (a reload measures it so)
        blocked = [y for y in range(1, ytop + 1) if (SPAWN[0], y, SPAWN[1]) in n]
        assert not blocked, "%s: the spawn column is blocked at %s" % (tag, blocked)
        way = [(x, y, z) for x in (4, 5, 6) for y in (1, 2, 3) for z in range(SPAWN[1], S)
               if (x, y, z) in n and n[(x, y, z)] != "rail"]
        assert not way, "%s: the way out south is blocked at %s" % (tag, way[:6])
    report = []
    if paths:
        for (tag, s), path in zip(levels_, paths):
            one_way, ground, roof, rooms, note = ap.audit(tag, path)
            assert not one_way and not ground and "TRAPPED" not in note, "%s: pockets %s %s %s" % (
                tag, one_way[:6], ground[:6], note)
            pad = ap.Pad(ap.load_nbt(path))
            start = (SPAWN[0], 1, SPAWN[1])
            seen, todo, out = {start}, [start], False
            while todo and not out:
                c = todo.pop()
                if c[0] < 0 or c[2] < 0 or c[0] >= S or c[2] >= S:
                    out = True
                    break
                for nb in ap.neighbours(pad, *c):
                    if nb not in seen and -3 <= nb[0] <= S + 2 and -3 <= nb[2] <= S + 2 and 1 <= nb[1] <= s.size[1] + 1:
                        seen.add(nb)
                        todo.append(nb)
            assert out, "%s: a unit spawned at %s can't walk out" % (tag, start)
            report.append("%s %dx%dx%d, %d blocks: no pockets (%d roof-only cells), the spawn walks out" % (
                tag, s.size[0], s.size[1], s.size[2], len(names(s)), len(roof)))
    return report


if __name__ == "__main__":
    outs = sys.argv[1:] or SHIPPED
    l2, l3 = levels()
    built = (l1(), l2, l3)
    for out in outs:
        os.makedirs(out, exist_ok=True)
        for s, f in zip(built, FILES):
            s.write(os.path.join(out, f))
            print(os.path.join(out, f))
    for line in check_levels(*built, paths=[os.path.join(outs[0], f) for f in FILES]):
        print(line)
