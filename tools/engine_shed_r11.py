"""Engine Shed (Brassworks T1 vehicle lab) r11 DETAIL on lovish's pick (Oct 29 19:5x: old a, small fixes only -
tools/engine_shed_massing.py build_af). Writes design/vehicle_labs/brass_t1_r11.nbt. Tests (written first, red on r10):
tools/test_engine_shed_r11.py - what the eyes stills SHOW (building_gates.shoot) and the colour each block IS on camera
(tools/camera_colour.py).

lovish: "blocky and toy", "doesnt necessarily look like a factory" (16:55); "the towers on this building need more
refinement" (20:00); palette (Oct 28): red brick walls, grey slate roofs, weathered green copper on the tower roof only.
Rules: design/architecture_rules.md (origin/claude/arch-kit), its DO-NOT-USE list (no dome ribs, no gear on a dark wall,
no half-timber, no tone_wall panels, no copper gutter stripe, no thin pinnacles); stacks and tower to the proportions
measured on the study builds: design/vehicle_labs/towers.md.

Grammar (every camera face: west x low, south z high):
- walls: a pale plinth course (y 1) chamfered under each opening, red brick piers 1-2 wide and 2-wide windows in a
  4-block beat, a stone sill, a brick segmental head (an in-plane upside-down stair: a shadow line), barred bottom
  lights, a lit room behind every pane, a dentilled course at each eave;
- roofs: roof_kit's slate courses (polished deepslate / deepslate tiles / pale stone, pack luma ~0.31), deepslate-brick
  ridges, the north lights glazed in light-grey panes between iron mullions under a slate slab cap;
- the gate: a riveted iron frame (dark jambs, a stepped head with a keystone), a stone string course over it, a glazed
  lunette in the gable; rails out of it, a chassis on rails under a gantry, a coal stage, lamp posts;
- the stacks (towers.md): a pale stone foot with chamfered arrises and a recessed course, a stair weathering, a red
  brick shaft, projecting blue-brick bands, a flush soot course, a corbelled crown with a dark slab cap, smoke;
- the clock tower: plinth / brick shaft with pale bands and a slit light / pale clock stage with dark dials / an
  oversailing cornice / a weathered copper pyramid with dark lead hip rolls / an iron finial.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import engine_shed_massing as M  # noqa: E402
import lab_vehicle_brass as L  # noqa: E402
import roof_kit as RK  # noqa: E402
import schem_study as S  # noqa: E402

OUT = "design/vehicle_labs/brass_t1_r11.nbt"
SX, SZ = L.SX, L.SZ
LANE_X, LANE_Z, LANE_H = L.LANE_X, L.LANE_Z, L.LANE_H
SPAWN = L.SPAWN
TOWER = M.AF["tower"]
CORNICE_Y = 15
META = dict(tower=TOWER, cornice=CORNICE_Y, eave=6, stack_from=7,
            stacks=[dict(base=s["base"], shaft=s["shaft"], top=s["top"]) for s in M.AF["stacks"]])

BRICK, BRICK_ST, BRICK_SL, BRICK_W = "bricks", "brick_stairs", "brick_slab", "brick_wall"
PLINTH, PLINTH_ST = "polished_andesite", "polished_andesite_stairs"
STONE, STONE_ST, STONE_SL, STONE_W = "stone_bricks", "stone_brick_stairs", "stone_brick_slab", "stone_brick_wall"
PALE, PALE_SL = "smooth_stone", "smooth_stone_slab"
IRON, IRON_ST, IRON_SL, IRON_W = ("polished_blackstone_bricks", "polished_blackstone_brick_stairs",
                                  "polished_blackstone_brick_slab", "polished_blackstone_brick_wall")
KEY = "chiseled_polished_blackstone"
PANE, ROOF_PANE = "glass_pane", "light_gray_stained_glass_pane"
GLOW = "shroomlight"
COP_ST, COP = "waxed_weathered_cut_copper_stairs", "waxed_weathered_cut_copper"
LEAD_ST = "deepslate_tile_stairs"   # lead hip rolls: the darker streaks down the copper (slate family)
DRY = {"waterlogged": "false"}
INWARD = {"west": "east", "south": "north"}


def stair(g, q, name, facing, half="bottom"):
    g.set(*q, name, facing=facing, half=half, shape="straight", waterlogged="false")


def slab(g, q, name, typ="bottom"):
    g.set(*q, name, type=typ, waterlogged="false")


def at(face, plane, u, y):
    """The cell of a camera-face wall: west faces are planes x = plane (u = z); south faces z = plane (u = x)."""
    return (plane, y, u) if face == "west" else (u, y, plane)


def out_of(face, q, d=1):
    return (q[0] - d, q[1], q[2]) if face == "west" else (q[0], q[1], q[2] + d)


def in_of(face, q, d=1):
    return out_of(face, q, -d)


# ---------------------------------------------------------------------------------------------------- the walls
def window(g, face, plane, us, y0, y1, barred=True, recess=0):
    """A window in a camera-face wall: columns us, the glass in the wall plane (recess 0 - the 3/4 camera looks ~45
    degrees down: a pane set back 1 under a head is hidden from it, measured on r11's first build), the bottom light
    barred, a stone sill (a top slab: a shadow under it) and a brick segmental head (an in-plane upside-down stair) in
    the wall plane, the plinth under it chamfered; a lit room behind the glass and its far jamb lit (north of a west
    window, east of a south one - the side the south-west 3/4 camera looks across the glass into)."""
    far = -1 if face == "west" else 1

    def lit(q):
        if not (q[0] in LANE_X and q[2] in LANE_Z and 1 <= q[1] <= LANE_H):
            g.set(*q, GLOW)
    for u in us:
        for y in range(y0, y1 + 1):
            if recess:
                g.clear(*at(face, plane, u, y))
            q = in_of(face, at(face, plane, u, y), recess)
            if barred and y == y1:                         # a grille in the top light (r11: a barred BOTTOM
                g.set(*q, "iron_bars", **DRY)              # light stopped the 3/4 camera's down-looking rays)
            else:
                g.set(*q, PANE, **DRY)
            for d in (recess + 1, recess + 2):
                lit(in_of(face, at(face, plane, u, y), d))
                if y == y0 and y0 - 1 >= 2:                # the sill course behind: the light the down-looking
                    lit(in_of(face, at(face, plane, u, y0 - 1), d))   # 3/4 rays reach
        slab(g, at(face, plane, u, y0 - 1), PALE_SL)      # a bottom slab: the 3/4 camera looks down through the
        stair(g, at(face, plane, u, y1 + 1), BRICK_ST, INWARD[face], "top")   # pane over it into the lit room
        if y0 - 2 == 1:
            stair(g, at(face, plane, u, 1), PLINTH_ST, INWARD[face])
    jamb = (max(us) + 1) if far > 0 else (min(us) - 1)
    for y in range(y0, y1 + 1):                        # the far jamb lit: in the reveal (recess) and behind it
        for d in range(1 if recess else 2, recess + 3):
            lit(in_of(face, at(face, plane, jamb, y), d))


def blind(g, face, plane, us, y0, y1):
    """A blind panel (the bay rhythm where a window would look into the tower): the brick set back as in-plane
    upside-down stairs under a segmental head."""
    for u in us:
        for y in range(y0, y1 + 1):
            stair(g, at(face, plane, u, y), BRICK_ST, INWARD[face], "top")
        slab(g, at(face, plane, u, y0 - 1), PALE_SL, "top")


def dentils(g, face, plane, us, y):
    """The eave course: every other brick an in-plane stair - the dentilled eave of a Midland engine shed."""
    for u in us:
        q = at(face, plane, u, y)
        if q in g.b and g.b[q][0] == BRICK and u % 2 == 0:
            stair(g, q, BRICK_ST, INWARD[face], "top")


def walls(g):
    # the shop's west flank (x 4): north of the tower, and south of the bay
    for (x, y, z), (n, _) in list(g.b.items()):           # the greybox's flank glass back to brick, then framed
        if x == 4 and n == "glass" and y <= 6:                # windows in its columns
            g.set(x, y, z, BRICK)
    blind(g, "west", 4, (1, 2), 3, 5)              # behind the tower from the 3/4 camera: blind panels keep the beat
    dentils(g, "west", 4, range(0, 5), 6)
    window(g, "west", 4, (17, 18), 3, 5)
    window(g, "west", 4, (20, 21), 3, 5)
    window(g, "west", 4, (23,), 3, 5)
    dentils(g, "west", 4, range(17, 25), 6)
    # the south face (z 24): the shop x 4..11, the boiler house x 12..16
    window(g, "south", 24, (5, 6, 7), 3, 5)                 # 3-light windows, 1-wide piers (the 3/4 camera looks
    window(g, "south", 24, (9, 10, 11), 3, 5)               # along this face: a 2-light window showed it the jamb)
    for x in (13, 14, 15):                                  # the boiler house (the stack's foot fills it): iron
        for y in (3, 4, 5):                                  # louvres over a stone sill - its draught vents
            g.set(x, y, 24, "iron_trapdoor", facing="north", half="top" if y % 2 else "bottom", open="false",
                  powered="false", waterlogged="false")
        slab(g, (x, 2, 24), PALE_SL)
        stair(g, (x, 1, 24), PLINTH_ST, "north")
    dentils(g, "south", 24, range(4, 17), 6)
    # the bay's south wall (z 16): a tall light at the front, a clerestory over the shop roof
    window(g, "south", 16, (1, 2), 3, 5)                    # the bay's south front: an office light below and an
    window(g, "south", 16, (1, 2), 7, 8, barred=False)       # upper light over a brick transom (two storeys)
    dentils(g, "south", 16, range(0, 10), 10)
    for us in ((5, 6), (8, 9)):
        window(g, "south", 16, us, 8, 9, barred=False, recess=0)
    for x in range(0, 4):                                  # the plinth under the bay's south front
        if g.b.get((x, 1, 16), ("",))[0] == M.BASE:
            g.set(x, 1, 16, PLINTH)


# ---------------------------------------------------------------------------------------------------- the gate
def gate(g):
    """a's gate (lane z 10..14, 8 tall, the iron frame at x 0): dark riveted jambs, a stepped head with a keystone,
    a stone string course over it, a glazed lunette in the gable, a louvred vent at the apex."""
    for y in range(1, 10):
        for z in (9, 15):
            g.set(0, y, z, IRON if y % 3 else KEY)
    for z, n in ((10, "st"), (11, "full"), (12, "key"), (13, "full"), (14, "st")):
        q = (0, 9, z)
        if n == "st":
            stair(g, q, IRON_ST, "south" if z == 10 else "north", "top")
        else:
            g.set(*q, KEY if n == "key" else IRON)
    for z in range(8, 17):                                 # the string course over the gate
        q = (0, 10, z)
        if q in g.b:
            stair(g, q, STONE_ST, "east", "top")
    for y, zs in ((11, (11, 12, 13)), (12, (12,))):          # the lunette
        for z in zs:
            g.set(0, y, z, PANE, **DRY)
            g.set(1, y, z, GLOW)
    for z in (10, 14):                                     # the gable's shoulders: a brick stair step each side
        if (0, 12, z) in g.b and g.b[(0, 12, z)][0] == BRICK:
            stair(g, (0, 12, z), BRICK_ST, "east", "top")
    if (0, 13, 12) in g.b:
        g.set(0, 13, 12, "iron_trapdoor", facing="west", half="top", open="false", waterlogged="false",
              powered="false")


# ---------------------------------------------------------------------------------------------------- the roofs
ROOF = L.SLATE


def roofs(g):
    """Slate courses on every roof (roof_kit.retone): the bay's gable, each saw tooth, the boiler houses. The north
    lights: the glass columns -> light-grey panes, an iron mullion every third column, a slate slab cap."""
    tiles = {"deepslate_tiles", "deepslate_tile_stairs", "deepslate_tile_slab"}
    cells = {q: v for q, v in g.b.items() if v[0] in tiles}
    for q in [q for q, v in g.b.items() if v[0] == "glass"]:
        cap = (q[0], q[1] + 1, q[2])
        if q[1] >= 7:
            g.set(*q, IRON_W if q[0] % 3 == 0 else ROOF_PANE, **DRY)
            if cap in cells and cells[cap][0] == "deepslate_tiles":
                cells[cap] = ("deepslate_tile_slab", {"type": "bottom", "waterlogged": "false"})
    regions = {}
    for q in cells:                                        # one region per roof: the bay, each tooth, each boiler
        x, y, z = q
        if 0 <= x <= 9 and 8 <= z <= 16:
            key = "bay"
        elif any(b[0] <= x <= b[1] and b[2] <= z <= b[3] for b in M.AF["boilers"]):
            key = "boiler%d" % (z > 12)
        else:
            key = "tooth%d" % ((z - 1) // 4)
        regions.setdefault(key, {})[q] = cells[q]
    for i, (key, reg) in enumerate(sorted(regions.items())):
        along = (lambda q: (q[2], q[0])) if key == "bay" or key.startswith("boiler") else (lambda q: (q[0], q[2]))
        out = RK.retone(reg, ROOF, seed=11 + i, along=along)
        g.b.update(out)
    for (x, y, z), (n, _) in list(g.b.items()):            # a coping on bare wall tops at the eave (the shop's
        if n == BRICK and y == 6 and (x, y + 1, z) not in g.b:   # north wall under the first tooth)
            slab(g, (x, y + 1, z), "deepslate_brick_slab")
    tone = {}
    for t, fam in enumerate(ROOF["tones"]):
        for n in fam:
            tone[n] = t
    for _ in range(6):                                     # break any a-b-a-b-a run across courses: the cell to T0
        top = {}
        for (x, y, z), (n, _) in g.b.items():
            if n in tone and y > top.get((x, z), (-1,))[0]:
                top[(x, z)] = (y, n)
        grid = {c: tone[v[1]] for c, v in top.items()}
        import building_gates as GT
        bad = GT.alternations(grid)
        if not bad:
            break
        for c in sorted(bad):
            y, n = top[c]
            if tone[n] != 0:
                q = (c[0], y, c[1])
                g.b[q] = (ROOF["tones"][0][RK.shape(n)], g.b[q][1])
    for (x, y, z), (n, p) in list(g.b.items()):            # the ridges: deepslate brick
        if 0 <= x <= 9 and z == 12 and y == 14 and n in {t[0] for t in ROOF["tones"]}:
            g.set(x, y, z, ROOF["ridge"][0])


# ---------------------------------------------------------------------------------------------------- the stacks
def stacks(g):
    """towers.md: the foot (pale stone, 4 x 4, its arrises chamfered with walls, a recessed course at 10) to 11, a
    stair weathering at 12, a red brick shaft 2 x 2, projecting blue-brick bands, a flush soot course, a corbelled
    brick crown (top - 1) under a dark slab cap with the fire sealed in the flue (smoke only)."""
    for s, bands, soot in zip(M.AF["stacks"], ((15, 19), (14, 16)), (22, 19)):
        x0, x1, z0, z1 = s["base"]
        sx0, sx1, sz0, sz1 = s["shaft"]
        top = s["top"]
        for q in [q for q in g.b if x0 <= q[0] <= x1 and z0 <= q[2] <= z1 and q[1] >= 2 and
                  (q[1] >= 7 or g.b[q][0] == M.STACK)]:
            del g.b[q]
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                corner = x in (x0, x1) and z in (z0, z1)
                for y in range(2, 12):
                    if corner:
                        g.set(x, y, z, STONE_W, **DRY)
                    elif y in (4, 7, 10) and not (sx0 <= x <= sx1 and sz0 <= z <= sz1):    # rusticated courses
                        f = "east" if x == x0 else "west" if x == x1 else "south" if z == z0 else "north"
                        stair(g, (x, y, z), STONE_ST, f, "top")
                    else:
                        g.set(x, y, z, STONE)
                if not (sx0 <= x <= sx1 and sz0 <= z <= sz1):
                    f = "east" if x == x0 else "west" if x == x1 else "south" if z == z0 else "north"
                    if corner:
                        f = ("east" if x == x0 else "west")
                    stair(g, (x, 12, z), STONE_ST, f)
        for y in range(12, top - 1):
            for x in range(sx0, sx1 + 1):
                for z in range(sz0, sz1 + 1):
                    if y == soot:                          # the soot course, set back
                        stair(g, (x, y, z), IRON_ST, "east" if x == sx0 else "west", "top")
                    elif y in bands:                       # under each band the shaft course is set back (a reveal)
                        stair(g, (x, y, z), BRICK_ST, "east" if x == sx0 else "west", "top")
                    else:
                        g.set(x, y, z, BRICK)
        for y in bands + (top - 1,):
            for x in range(x0, x1 + 1):
                for z in range(z0, z1 + 1):
                    if sx0 <= x <= sx1 and sz0 <= z <= sz1:
                        continue
                    if y == top - 1:
                        f = "east" if x == x0 else "west" if x == x1 else "south" if z == z0 else "north"
                        stair(g, (x, y, z), BRICK_ST, f, "top")
                    else:
                        slab(g, (x, y, z), IRON_SL, "top")
        for x in range(sx0, sx1 + 1):
            for z in range(sz0, sz1 + 1):
                g.set(x, top - 1, z, BRICK)
                slab(g, (x, top, z), IRON_SL)
        g.set(sx0, top - 1, sz0, "campfire", lit="true", facing="south", signal_fire="false", waterlogged="false")


# ---------------------------------------------------------------------------------------------------- the tower
def tower(g):
    """towers.md: plinth (1-2), brick shaft with pale bands at 6 and 10 and a slit light (7-8), a pale clock stage
    (13-14) with dark dials west and south, an oversailing cornice (15), a weathered copper pyramid with dark lead hip
    rolls (16-18), an iron finial (19)."""
    x0, x1, z0, z1 = TOWER
    for q in [q for q in g.b if x0 - 1 <= q[0] <= x1 + 1 and z0 - 1 <= q[2] <= z1 + 1 and q[1] >= 1 and (
            (x0 <= q[0] <= x1 and z0 <= q[2] <= z1) or q[1] >= CORNICE_Y)]:
        del g.b[q]
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            face = "west" if x == x0 else "south" if z == z1 else None
            g.set(x, 1, z, PLINTH)
            stair(g, (x, 2, z), STONE_ST, "east" if x == x0 else "north" if z == z1 else "west")
            f = "east" if x == x0 else "north" if z == z1 else "west"
            for y in range(3, 12):
                if y in (6, 9):                            # recessed brick courses: the shaft's relief
                    stair(g, (x, y, z), BRICK_ST, f, "top")
                else:
                    g.set(x, y, z, BRICK)
            stair(g, (x, 12, z), STONE_ST, f, "top")       # the corbel course: the clock stage starts pale
            for y in (13, 14):
                g.set(x, y, z, PALE)
            g.set(x, 16, z, PALE)
            del face
    for y in (7, 8):                                       # the slit light, west
        g.set(x0, y, cz, PANE, **DRY)
        g.set(x0 + 1, y, cz, GLOW)
    g.set(x0, 14, cz, KEY)                                 # the dials, west and south
    g.set(cx, 14, z1, KEY)
    for x in range(x0 - 1, x1 + 2):                        # the cornice ring and the copper eave ring
        for z in range(z0 - 1, z1 + 2):
            if x0 <= x <= x1 and z0 <= z <= z1:
                g.set(x, CORNICE_Y, z, PALE)
                continue
            f = "east" if x < x0 else "west" if x > x1 else "south" if z < z0 else "north"
            stair(g, (x, CORNICE_Y, z), STONE_ST, f, "top")
            corner = x in (x0 - 1, x1 + 1) and z in (z0 - 1, z1 + 1)
            stair(g, (x, 16, z), LEAD_ST if corner else COP_ST, f)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if (x, z) == (cx, cz):
                g.set(x, 17, z, COP)
                continue
            f = "east" if x < cx else "west" if x > cx else "south" if z < cz else "north"
            corner = x != cx and z != cz
            stair(g, (x, 17, z), LEAD_ST if corner else COP_ST, f)
    stair(g, (cx, 18, cz), COP_ST, "north")
    g.set(cx, 19, cz, IRON_W, **DRY)


# ---------------------------------------------------------------------------------------------------- the yard
def yard(g):
    """The front strip (x 0..3) paved stone bricks with a pale kerb at the edge; rails from the gate out through the
    lane to the plot edge, and two lines on the south apron (z 17..24) under a part-built chassis (four grindstone
    wheels on the rails, a dark frame, a gantry with a chain hoist); a coal stage north of the tower; lamp posts either
    side of the gate's apron."""
    for x in range(SX):
        for z in range(SZ):
            if g.b.get((x, 0, z), ("",))[0] in (M.YARD, M.GROUND):
                g.set(x, 0, z, PLINTH if x == 0 else STONE)
    for z in (11, 13):
        for x in LANE_X:
            g.set(x, 0, z, "rail", shape="east_west", **DRY)
    for z in range(17, SZ):                                # one line under the chassis, out to the plot edge
        g.set(1, 0, z, "rail", shape="north_south", **DRY)
    for x in (1, 2):                                       # the chassis on the rails
        for z in (19, 22):
            g.set(x, 1, z, "grindstone", face="floor", facing="north")
        for z in range(19, 23):
            if z in (20, 21):
                g.set(x, 2, z, "iron_bars", **DRY)
            else:
                slab(g, (x, 2, z), "polished_blackstone_slab", "top")
    for z in (18, 23):                                     # the gantry
        for y in range(1, 5):
            g.set(1, y, z, IRON_W, **DRY)
    for z in range(18, 24):
        slab(g, (1, 5, z), "polished_blackstone_slab")
    for y in (3, 4):
        g.set(2, y, 20, "chain", axis="y", **DRY)
    g.set(2, 5, 20, "polished_blackstone_slab", type="bottom", **DRY)
    for x in (1, 2):                                       # the coal stage: a stepped stone stage, coal on it
        for z in (1, 2, 3):
            if z == 2:
                stair(g, (x, 1, z), PLINTH_ST, "east")
            else:
                g.set(x, 1, z, PLINTH)
            g.set(x, 2, z, "coal_block")
    for z in (0, 4):
        g.set(1, 2, z, "iron_bars", **DRY)
        g.set(2, 2, z, "iron_bars", **DRY)
        g.set(1, 1, z, PLINTH)
        g.set(2, 1, z, PLINTH)
    for z in (4, 17):                                      # lamp posts by the gate's apron
        for y in (1, 2):
            g.set(0, y, z, IRON_W, **DRY)
        g.set(0, 3, z, "lantern", hanging="false", **DRY)


# ---------------------------------------------------------------------------------------------------- small life
def life(g):
    """A works railing (iron bars on a stone kerb) along the front either side of the gate's apron, louvre vents (iron
    trapdoors) high in the south boiler house's wall. Ivy up the tower's west foot and the bay's south front, iron shutters on the boiler house light, pots on the
    tower's window ledge, chains on the gate frame (its sliding doors' hangers)."""
    for z in list(range(0, 4)) + list(range(18, SZ)):      # the railing
        if (0, 1, z) not in g.b:
            g.set(0, 1, z, "iron_bars", **DRY)
    for x in (2, 6, 10):                                   # wall lanterns on the south face's piers (life at dusk)
        if g.b.get((x, 4, 24), ("",))[0] == BRICK and (x, 4, SZ) not in g.b:
            pass
    for y in range(1, 7):                                  # ivy on the tower's west face
        for z in (TOWER[2],):                              # the north arris only: the south one hid the office light
            if (0, y, z) not in g.b and (TOWER[0], y, z) in g.b and y not in (4,):
                g.set(0, y, z, "vine", east="true", north="false", south="false", west="false", up="false")
    for y in range(1, 6):                                  # ivy on the bay's south front pier
        if (0, y, 17) not in g.b and (0, y, 16) in g.b:
            g.set(0, y, 17, "vine", north="true", east="false", south="false", west="false", up="false")

    for x in (2, 5):                                       # the lane's walls: a recessed panel every 3
        for z, f in ((9, "north"), (15, "south")):
            for y in range(1, 8):
                n = g.b.get((x, y, z), ("",))[0]
                if n in (BRICK, PLINTH):
                    stair(g, (x, y, z), BRICK_ST if n == BRICK else PLINTH_ST, f, "top")


# ---------------------------------------------------------------------------------------------------- the shell
def hollow(g):
    """Masonry with all 26 neighbours present, y 3 up, goes (the shells and their roof beds stay; the floor y <= 2
    stays solid so no unit is built into a ground pocket)."""
    full = [q for q in g.b if q[1] >= 3 and S.glyph(g.b[q][0]) == "#" and g.b[q][0] != GLOW and all(
        (q[0] + a, q[1] + b, q[2] + c) in g.b for a in (-1, 0, 1) for b in (-1, 0, 1) for c in (-1, 0, 1))]
    for q in full:
        del g.b[q]


def bevel(g):
    """No bare full-block corner against the sky on the camera sides: a full block with air over it and air to its
    west or south becomes its family's slab (roof stone) or an in-plane stair (masonry)."""
    fam = {"deepslate_tiles": "deepslate_tile_slab", "polished_deepslate": "polished_deepslate_slab",
           "stone": "stone_slab", "deepslate_bricks": "deepslate_brick_slab", BRICK: BRICK_SL, STONE: STONE_SL,
           PLINTH: "polished_andesite_slab", PALE: PALE_SL, IRON: IRON_SL}
    for (x, y, z), (n, _) in list(g.b.items()):
        if y < 1 or S.glyph(n) != "#" or (x, y + 1, z) in g.b:
            continue
        if (x - 1, y, z) in g.b and (x, y, z + 1) in g.b:
            continue
        if n in fam:
            slab(g, (x, y, z), fam[n])


def detail():
    g = M.build_af()
    for q, (n, p) in list(g.b.items()):
        if n == M.WALL:
            g.b[q] = (BRICK, {})
        elif n == M.BASE and q[1] == 1:
            g.b[q] = (PLINTH, {})
    hollow(g)
    roofs(g)
    walls(g)
    gate(g)
    stacks(g)
    tower(g)
    yard(g)
    life(g)
    bevel(g)
    for y in range(1, L.H_MAX):                            # the spawn column / smoke-vent shaft
        g.clear(SPAWN[0], y, SPAWN[2])
    return g


if __name__ == "__main__":
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out = os.path.join(root, OUT)
    rig = L.write(detail(), out)
    print(L.check(rig, out))
    L.check_banned(rig, "brass_t1_r11")
