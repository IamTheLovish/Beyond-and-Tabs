#!/usr/bin/env python3
"""THE SUNWAIN COURT PART LIBRARY (round 2): the parts the Sunforged vehicle lab (tools/lab_vehicle_sunforged.py) is
assembled from, in the conventions of tools/gothic_parts.py (a Part is a dict of cells in its own frame, its FRONT faces
SOUTH (+z), y = 0 is its floor, every block goes through a ROLE mapped by the palette, Part.place(other, ox, oy, oz,
rot) copies a part in turned `rot` quarter turns clockwise). It reuses the gothic library: GT.Mason for the ashlar
(quoins, a dark plinth, a little moss low on the windward side), GP.toward / ring / disc.

Round 1 scored 4/10 ("a dense grey box with ideas stuck onto it"): no single landmark, a weak gate, no bay rhythm, a
brown blob on the roof, mixed materials, messy ground. Round 2 is built on HIERARCHY: one ribbed dome with a lantern
and the sun on it, one deep west portal under a crocketed gable, a fixed bay rhythm (buttress - lit lancet - buttress)
with flying buttresses on the camera face, and four material zones only: a DEEPSLATE plinth, warm STONE-BRICK ashlar
walls with lighter polished-andesite clasping buttresses, QUARTZ + GOLD crown and trim (calcite bands), DEEP-SLATE
tile roofs, teal-blue glass. No pink, no purple, no copper.

THE PARTS (each small, made once, made well)
  (1) gate_tower     a 5 x 5 west-front tower: clasping corner buttresses (lighter stone), faces recessed one cell
                     with ONE tall lit lancet each, a sill course and a string course, a corbelled parapet, corner
                     pinnacles and a steep slate pyramid cap with a gold finial (L3: a slate needle instead)
  (2) portal         the deep west portal, drawn for a front facing WEST (x 0 outermost): three pointed orders
                     stepping in (the porch proud of the towers), quartz colonnettes with gold capitals, a gold
                     keystone, a deepslate-dark passage, lit jamb niches, the raised portcullis, and the crocketed
                     WIMPERG with a lit rose breaking the facade's cornice
  (3) nave_wall      the hall's long wall as a bay rhythm: buttress 1 proud - recessed bay with one tall lit
                     lancet (sill, hood, carved apex) - buttress ..., a string course at the cornice, a parapet
  (4) flyer          a flying buttress: a free pier two cells out (battered foot, quoined shaft, pinnacle) and the
                     flyer rising to the wall's buttress head
  (5) gantry         the dark-oak-and-chain gantry over the open bay: quartz piers on the wall heads, a dark oak
                     beam, a chiseled trolley, chains, the cloud cradle (white wool) hanging under it
  (6) chancel_arch   the open pointed arch from the bay into the dome's chamber (lit, a gold sun mosaic floor)
  (7) ribbed_dome    the ribbed dome on an octagonal lit drum: quartz ribs, deep-slate tile courses between, a gold
                     band, corner pinnacles, the crown lantern with the sun on it
  (8) lantern_spire  (L3) the ribbed lantern spire that grows from the crown: an open lit lantern, gold bands, a
                     tapering quartz needle and the SUN DISC (gold, an ochre heart, end-rod rays) at its tip
  (9) lodge          the lean-to lodge against the nave's north wall: a slate lean-to roof with two lit dormers and
                     a chimney
  (10) pinnacle      the lab's pinnacle: carved base, a quartz shaft (walls), a gold knop and rod
  (11) forecourt     the calm paved ground: polished andesite setts with andesite / tuff patches, a kerb, the
                     processional strip out of the gate, the sun mosaic under the cradle
  (12) conifer       a small dark conifer at the plot's corner (ref10's firs against pale stone)
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gothic_parts as GP          # noqa: E402
import gothic_towers as GT         # noqa: E402
from gothic_parts import Part, hsh, toward, OPP   # noqa: E402

PAL = GP.palette(
    "slate", "warm",
    # the four zones: deepslate plinth, stone-brick ashlar with lighter buttress stones, quartz/gold crown, slate roofs
    wall="stone_bricks", wall_alt="stone_bricks", wall_moss="mossy_stone_bricks", quoin="polished_andesite",
    base="deepslate_bricks", base_st="deepslate_brick_stairs", base_slab="deepslate_brick_slab",
    base_wall="deepslate_brick_wall", base_alt="polished_deepslate", dark="polished_deepslate",
    dark_tile="deepslate_tiles",
    trim="smooth_quartz", trim_st="smooth_quartz_stairs", trim_slab="smooth_quartz_slab",
    carve="chiseled_quartz_block", pillar="quartz_pillar", rail="diorite_wall",
    band="polished_andesite", band_st="polished_andesite_stairs", band_slab="polished_andesite_slab",
    white="calcite", white_st="smooth_quartz_stairs", white_slab="smooth_quartz_slab",
    qwall="andesite_wall",
    glass="light_blue_stained_glass_pane", glass_block="light_blue_stained_glass", gb="blue_stained_glass",
    gl="light_blue_stained_glass",
    light="shroomlight", sunheart="ochre_froglight", gold="gold_block", gold_st="smooth_quartz_stairs",
    wool="white_wool", timber="dark_oak_log", timber_st="dark_oak_stairs", timber_slab="dark_oak_slab",
    timber_fence="dark_oak_fence", chain="chain", spike="end_rod",
    pave="polished_andesite", pave_b="andesite", pave_c="tuff", pave_d="cobblestone", strip="polished_diorite",
    leaves="spruce_leaves", log="spruce_log",
)
LEAF = dict(persistent="true", distance="1", waterlogged="false")


def chain(p, x, y, z, axis="y"):
    p.set(x, y, z, "chain", axis=axis, waterlogged="false")


# ============================================================================================ (10) pinnacle
def pinnacle(p, x, y, z, tall=False, gold=False):
    """The lab's pinnacle, slender: a quartz shaft of walls on a carved base cell (tall: a taller shaft, a gold knop
    and a rod - the few that mark the landmark's steps), a slab cap. Height 3 (tall: 5)."""
    p.set(x, y, z, "rail")
    p.set(x, y + 1, z, "rail")
    if tall:
        p.set(x, y + 2, z, "rail")
        p.set(x, y + 3, z, "gold" if gold else "carve")
        p.set(x, y + 4, z, "spike", facing="up")
        return y + 4
    p.slab(x, y + 2, z, "bottom", "trim_slab")
    return y + 2


# ============================================================================================ (1) gate tower
def gate_tower(top=10, seed=0, pal=PAL, lancets=("west", "south"), needle=False):
    """A 5 x 5 tower, x 0..4, z 0..4, floor y 0. Corner clasping buttresses (lighter stone, full to `top` + 1), the
    faces between recessed one cell with ONE tall lit lancet (y 3..7) in each face of `lancets`: a sill slab, the
    glass in the recessed plane, a light behind, a hood stair and a carved apex. A sill course at y 2 and a string
    course at y 8 across the faces, corbels at top bringing the face out, a parapet at top + 1, corner pinnacles,
    a steep slate pyramid cap with a gold finial (needle: a slate needle to top + 10 for L3)."""
    p = Part(pal)
    m = GT.Mason(p, top + 6, seed, plinth=3)
    corners = ((0, 0), (4, 0), (0, 4), (4, 4))
    faces = {"south": ((1, 4), (2, 4), (3, 4)), "north": ((1, 0), (2, 0), (3, 0)),
             "west": ((0, 1), (0, 2), (0, 3)), "east": ((4, 1), (4, 2), (4, 3))}
    inner = {"south": (0, -1), "north": (0, 1), "west": (1, 0), "east": (-1, 0)}
    for y in range(1, top + 1):
        for (x, z) in corners:
            m.put(x, y, z, quoin=True)
        for f, cells in faces.items():
            vx, vz = inner[f]
            for (x, z) in cells:
                if y <= 2:
                    m.put(x, y, z)                                    # the plinth runs round at the outer plane
                m.put(x + vx, y, z + vz)                              # the recessed face
        for x in (2,):                                                # the core: stone, lights behind the lancets
            for z in (2,):
                p.set(x, y, z, "base")
    for f, cells in faces.items():
        vx, vz = inner[f]
        for (x, z) in cells:
            p.stair(x, 3, z, OPP[f], "bottom", "base_st")             # the plinth weathered where the face steps back
            p.stair(x, 8, z, f, "top", "trim_st")                     # the string course, proud, on every face
        if f in lancets:
            x, z = cells[1]
            fx, fz = x + vx, z + vz
            p.slab(x, 2, z, "top", "trim_slab")                       # the sill, proud
            for y in range(3, 8):
                p.set(fx, y, fz, "glass_block")
                p.set(fx + vx, y, fz + vz, "light")
            p.set(fx, 8, fz, "carve")                                 # the apex of the lancet in the string course
            for (sx, sz) in (cells[0], cells[2]):                     # tracery shafts flanking it, in the face plane
                for y in range(3, 8):
                    p.set(sx, y, sz, "rail")
        else:
            x, z = cells[1]
            for y in (4, 5, 6):                                       # a blind lancet on the faces no one lights
                p.set(x + vx, y, z + vz, "rail")
    # the crown: corbels (upside-down stairs) bring the faces out to the buttress plane, a parapet of quartz walls
    for f, cells in faces.items():
        for (x, z) in cells:
            p.stair(x, top, z, OPP[f], "top", "band_st")
            p.set(x, top + 1, z, "trim")
            p.set(x, top + 2, z, "rail")
    for (x, z) in corners:
        p.set(x, top + 1, z, "trim")
        pinnacle(p, x, top + 2, z)
    # the cap: a steep slate spire rising clear of the parapet - a 3 x 3 slate base, two stair rings climbing in,
    # the shaft of walls with a gold knop (needle: three courses taller for L3)
    for x in (1, 2, 3):
        for z in (1, 2, 3):
            p.set(x, top + 1, z, "band")
            if (x, z) == (2, 2):
                p.set(x, top + 2, z, "roof")
            elif abs(x - 2) + abs(z - 2) == 2:
                p.stair(x, top + 2, z, toward(x, z, 2, 2), "bottom", "roof_st")
            else:
                p.set(x, top + 2, z, "roof")
    for (x, z) in ((2, 1), (2, 3), (1, 2), (3, 2)):
        p.stair(x, top + 3, z, toward(x, z, 2, 2), "bottom", "roof_st")
    p.set(2, top + 3, 2, "roof")
    h = 9 if needle else 6
    for y in range(top + 4, top + h):
        p.set(2, y, 2, "roof_wall")
    p.set(2, top + h, 2, "gold")
    p.set(2, top + h + 1, 2, "spike", facing="up")
    return p


# ============================================================================================ (2) the west portal
def portal(p, zc=8, x_tower=1, pal=PAL):
    """Drawn straight into the lab part `p` (it is cut through the facade and laps the towers). The opening z zc-2..
    zc+2 stays clear to y 8 through x 0..5. Orders: A at x 0 (the porch, proud of the towers at x_tower: jambs at
    zc-4 / zc+4 as quartz colonnettes on a deepslate plinth, a pointed arch of quartz soffit stairs from y 9 to a gold
    keystone at y 12, the spandrels calcite), B at x 1 (colonnettes at zc-3 / zc+3 with gold capitals, apex y 11),
    C at x 2 (stone jambs, lit niches, the raised portcullis), x 3..5 the dark vaulted passage. Over A the crocketed
    WIMPERG (y 12..16) with a lit rose, pinnacles at its feet; the facade wall over the passage to y 12 with a
    parapet at 13."""
    m = GT.Mason(p, 20, 7, plinth=3)
    zl, zr = zc - 2, zc + 2

    def arch(x, half, y0, st, key, fill=None):
        k = 0
        while True:
            a, b = zc - half + k, zc + half - k
            y = y0 + k
            if a > b:
                return y - 1
            if a == b:
                p.set(x, y, a, key)
                return y
            p.stair(x, y, a, "south", "top", st)
            p.stair(x, y, b, "north", "top", st)
            for z in range(a + 1, b):
                if fill:
                    p.set(x, y, z, fill)
            k += 1

    # the facade wall x 1..5 over the passage (y 9..12) and beside it, the vault's dark ceiling
    for x in range(1, 6):
        for z in range(zc - 3, zc + 4):
            for y in range(9, 13):
                m.put(x, y, z)
        for z in range(zl, zr + 1):
            p.set(x, 9, z, "dark")
    # x 3..5: the passage reveals dark (deepslate), lit niches
    for x in range(2, 6):
        for z in (zl - 1, zr + 1):
            for y in range(1, 9):
                p.set(x, y, z, "dark" if (x + y) % 3 else "base")
    for x in (3, 5):
        for z in (zl - 1, zr + 1):
            p.set(x, 5, z, "glass_block")
            p.set(x, 6, z, "glass_block")
            p.set(x, 5, z + (-1 if z < zc else 1), "light")
            p.set(x, 7, z, "carve")
            p.slab(x, 4, z, "top", "trim_slab")
    # order C (x 2): stone jambs with a capital, the portcullis drawn up into the head
    for z in (zl - 1, zr + 1):
        for y in range(1, 8):
            m.put(2, y, z, quoin=(y // 2) % 2 == 0)
        p.set(2, 8, z, "carve")
    arch(2, 2, 9, "trim_st", "carve", fill="dark")
    for z in range(zl, zr + 1):
        p.set(2, 9, z, "bars")
    for z in (zc - 1, zc, zc + 1):
        p.set(2, 10, z, "bars")
    p.set(2, 11, zc, "bars")
    # order B (x 1): quartz colonnettes on a deepslate plinth, gold capitals, the arch to y 11
    for z in (zl - 1, zr + 1):
        p.set(1, 1, z, "base")
        for y in range(2, 8):
            p.set(1, y, z, "pillar", axis="y")
        p.set(1, 8, z, "gold")
    for z in (zc - 3, zc + 3):
        for y in range(1, 9):
            m.put(1, y, z, quoin=(y // 2) % 2 == 1)
    arch(1, 2, 9, "white_st", "carve", fill="dark")
    for z in range(zc - 3, zc + 4):
        p.set(1, 12, z, "white") if p.get(1, 12, z) == "air" else None
    # order A (x 0): the porch, one proud of the towers. Jambs at zc-4 / zc+4 (deepslate plinth, quartz colonnettes,
    # gold capitals), the intrados zc-3..zc+3 springing at y 9, gold keystone at y 12, calcite spandrels
    for z in (zc - 4, zc + 4):
        p.set(0, 1, z, "base")
        p.stair(0, 2, z, "east", "bottom", "base_st") if False else p.set(0, 2, z, "base")
        for y in range(3, 8):
            p.set(0, y, z, "pillar", axis="y")
        p.set(0, 8, z, "gold")
        for y in range(9, 13):
            m.put(0, y, z, quoin=True)
    for z in (zc - 3, zc + 3):
        p.set(0, 1, z, "base")
        for y in range(2, 9):
            p.set(0, y, z, "rail")                                   # the inner colonnettes of the porch
    apex = arch(0, 3, 9, "white_st", "gold", fill=None)
    for z in range(zc - 3, zc + 4):                                 # the spandrels over the arch
        for y in range(9, 13):
            if p.get(0, y, z) == "air" and abs(z - zc) >= 12 - y:
                p.set(0, y, z, "white" if abs(z - zc) == 12 - y else "wall")
    # the WIMPERG: a crocketed gable at x 0 over the porch, z zc-4..zc+4, y 12 -> apex y 16, breaking the cornice
    for k in range(5):
        a, b = zc - 4 + k, zc + 4 - k
        y = 12 + k
        for z in range(a + 1, b):
            if p.get(0, y, z) == "air":
                m.put(0, y, z)
        if a < b:
            p.stair(0, y, a, "north", "bottom", "trim_st")
            p.stair(0, y, b, "south", "bottom", "trim_st")
            if k % 2 == 1:
                p.set(0, y + 1, a, "rail")                           # crockets
                p.set(0, y + 1, b, "rail")
        else:
            p.set(0, y, a, "carve")
    p.set(0, 17, zc, "gold")
    p.set(0, 18, zc, "spike", facing="up")
    # the rose in the gable: a ring of glass round a gold boss, lit from behind
    for (dz, dy) in ((-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (0, 1)):
        p.set(0, 14 + dy, zc + dz, "glass_block")
    p.set(0, 14, zc, "gold")
    p.set(0, 13, zc - 2, "carve")                                    # the label stops either side
    p.set(0, 13, zc + 2, "carve")
    # the lights behind the rose, boxed in stone (the gable is two thick) so they show only through the glass
    for dz in (-1, 0, 1):
        p.set(1, 13, zc + dz, "light")
        p.set(1, 14, zc + dz, "light" if dz else "wall")
        p.set(1, 15, zc + dz, "light" if dz == 0 else "wall")
        p.set(1, 16, zc + dz, "wall" if dz == 0 else "air") if dz == 0 else None
        p.slab(1, 16, zc + dz, "bottom", "trim_slab") if dz else None
        for y in (13, 14, 15):
            m.put(2, y, zc + dz)
    for y in (13, 14, 15):
        m.put(1, y, zc - 2)
        m.put(1, y, zc + 2)
    p.set(1, 17, zc, "trim") if False else None
    # pinnacles at the wimperg's feet, on the porch's jambs; the facade parapet at y 13 behind
    for z in (zc - 4, zc + 4):
        pinnacle(p, 0, 13, z, tall=True)
    for z in (zc - 3, zc - 2, zc + 2, zc + 3):
        p.set(1, 13, z, "rail")
    for x in range(2, 6):
        for z in (zc - 3, zc + 3):
            p.set(x, 13, z, "rail") if x in (2, 5) else p.slab(x, 13, z, "bottom", "trim_slab")
    return apex


# ============================================================================================ (3) nave wall
def nave_wall(length, top=10, buttress_at=(0, 4, 8), lancet_at=(2, 6), seed=0, pal=PAL, twin=(), inner=False,
              depth=2, glass_y=(3, 8)):
    """A wall along x (x 0..length-1), two thick (z 0 the inner skin, z 1 the outer face), rising to `top`, in a
    fixed rhythm: buttresses one proud at z 2 at `buttress_at` (quoined, to `top`, a weathered set-off over them;
    depth 2: a lower stage at z 3 to y 5 with its own set-off and a battered foot at z 4), recessed bays between with
    ONE tall lancet at `lancet_at` (twin: two cells wide): a proud sill slab, teal glass from glass_y[0] to glass_y[1]
    in the outer face with a light behind it in the inner skin (inner: the glass in the inner skin, the light in the
    face - for a wall seen from inside), a hood stair, a carved apex; a cornice of corbel stairs at top + 1 on the
    face, a quartz coping and parapet on the inner skin; a pinnacle on every buttress."""
    p = Part(pal)
    m = GT.Mason(p, top + 6, seed, plinth=3)
    y0, y1 = glass_y
    for x in range(length):
        for y in range(1, top + 1):
            m.put(x, y, 0)
            m.put(x, y, 1, quoin=(x in buttress_at and (y // 2) % 2 == 0))
        p.stair(x, top + 1, 1, "north", "top", "band_st")             # the cornice: corbel stairs on the face
        p.slab(x, top + 1, 0, "top", "trim_slab")                     # the coping
        if x % 2 == 0:
            p.set(x, top + 2, 0, "rail")                              # the parapet: posts and slabs
        else:
            p.slab(x, top + 2, 0, "bottom", "trim_slab")
        p.slab(x, 2, 2, "top", "trim_slab") if x not in buttress_at else None     # the sill course
    for x in buttress_at:
        for y in range(1, top + 1):
            m.put(x, y, 2, quoin=(y // 2) % 2 == 1)
        p.stair(x, top + 1, 2, "north", "bottom", "trim_st")           # the set-off, weathered
        if depth >= 2:
            for y in range(1, 6):
                m.put(x, y, 3, quoin=(y // 2) % 2 == 0)
            p.stair(x, 6, 3, "north", "bottom", "trim_st")
            p.stair(x, 1, 4, "north", "bottom", "base_st")             # the battered foot
        p.clear(x, top + 2, 0)
        p.set(x, top + 2, 1, "carve")
        pinnacle(p, x, top + 2, 1)
    gz, lz = (0, 1) if inner else (1, 0)
    for x in lancet_at:
        cells = (x,) if x not in twin else (x, x + 1)
        for cx in cells:
            p.slab(cx, y0 - 1, 2, "top", "trim_slab")                  # the sill, proud
            for y in range(y0, y1 + 1):
                p.set(cx, y, gz, "glass_block")
                p.set(cx, y, lz, "light")
            p.set(cx, y1 + 1, gz, "carve")
            if not inner:
                p.stair(cx, y1 + 1, 2, "south", "top", "trim_st")       # the hood
        if not inner and len(cells) == 1:
            for sx in (x - 1, x + 1):                                   # tracery shafts flanking the lancet
                if 0 <= sx < length and sx not in buttress_at:
                    for y in range(y0, y1):
                        p.set(sx, y, 2, "rail")
                    p.stair(sx, y1, 2, "south", "top", "trim_st")
    return p


# ============================================================================================ (4) flying buttress
def flyer(p, x, z_wall, y_top, s=1, pal=PAL, seed=0):
    """A flying buttress for the wall whose buttress stands at (x, *, z_wall) with its head at y_top (the pinnacle's
    base): a free pier two cells out at z_wall + 2 s (a battered foot, a quoined shaft to y_top - 3, a cap and
    pinnacle), and the flyer: a stair brace from the pier head up to the buttress head, a soffit stair under it."""
    m = GT.Mason(p, y_top + 4, seed, plinth=3)
    zp = z_wall + 2 * s
    zm = z_wall + s
    f_in = "north" if s > 0 else "south"       # toward the wall
    f_out = OPP[f_in]
    p.set(x, 1, zp, "base")
    p.stair(x, 1, zp + s, f_in, "bottom", "base_st")
    p.stair(x, 1, zm, f_out, "bottom", "base_st")
    for y in range(2, y_top - 2):
        m.put(x, y, zp, quoin=(y // 2) % 2 == 0)
    p.set(x, y_top - 2, zp, "trim")
    pinnacle(p, x, y_top - 1, zp)
    p.stair(x, y_top - 1, zm, f_in, "bottom", "trim_st")        # the flyer's coping
    p.stair(x, y_top - 2, zm, f_out, "top", "trim_st")          # its soffit


# ============================================================================================ (5) gantry + cloud
def gantry(p, x, z0, z1, y_beam, pal=PAL):
    """The dark-oak-and-chain gantry over the open bay: quartz piers with gold caps rising from the wall heads at
    (x, *, z0) and (x, *, z1) to the beam, a dark oak beam along z, corbel stairs under its ends, the chiseled
    trolley saddle riding it at the bay's middle, two chains down to the CLOUD CRADLE (lumpy white wool, its bottom
    at y 10 - over the lane's clearance, its crown showing over the nave wall) and the gold sun counterweight on its
    own chain at the south end."""
    zc = (z0 + z1) // 2
    for z in (z0, z1):
        for y in range(y_beam - 3, y_beam):
            p.set(x, y, z, "pillar", axis="y")
        p.set(x, y_beam, z, "gold")
    for z in range(z0 + 1, z1):
        p.set(x, y_beam, z, "timber", axis="z")
    p.stair(x, y_beam - 1, z0 + 1, "south", "top", "timber_st")
    p.stair(x, y_beam - 1, z1 - 1, "north", "top", "timber_st")
    p.set(x, y_beam + 1, zc, "carve")                               # the trolley
    p.slab(x, y_beam + 1, zc - 1, "bottom", "timber_slab")
    p.slab(x, y_beam + 1, zc + 1, "bottom", "timber_slab")
    yc = y_beam - 2                                                 # the cloud's crown
    for z in (zc - 1, zc + 1):
        for y in range(yc + 1, y_beam):
            chain(p, x, y, z)
    # the cloud: bottom yc - 3, a ragged underside, a puffed middle, a crown, x x-1..x, z zc-2..zc+2
    for dz in range(-2, 3):
        for dx in (-1, 0):
            r = math.hypot(dx + 0.5, dz * 0.8)
            if r <= 1.4 or dz == 0:
                p.set(x + dx, yc - 1, zc + dz, "wool")
                p.set(x + dx, yc - 2, zc + dz, "wool")
            if r <= 1.0 and hsh(dx + 3, dz + 5, 2) % 4:
                p.set(x + dx, yc - 3, zc + dz, "wool")
    p.set(x, yc - 3, zc, "wool")
    p.set(x - 1, yc - 3, zc, "wool")
    for dz in (-1, 0, 1):
        p.set(x, yc, zc + dz, "wool")
    p.set(x - 1, yc, zc, "wool")
    # the counterweight: a chain from the beam's south end, a gold sun disc facing south with an ochre heart
    for y in range(yc + 1, y_beam):
        chain(p, x, y, z1 - 1)
    p.set(x, yc, z1 - 1, "gold")
    p.set(x, yc - 1, z1 - 1, "sunheart", axis="z")
    p.set(x, yc - 2, z1 - 1, "gold")


# ============================================================================================ (6) chancel arch
def chancel_arch(p, x, z0, z1, y_spring, pal=PAL, seed=3):
    """The open pointed arch in the plane x from the bay into the dome chamber: jambs at z0 - 1 / z1 + 1 (quoined,
    gold capitals), quartz soffit stairs closing in from y_spring to a carved apex, the wall over it to y 12."""
    m = GT.Mason(p, 16, seed, plinth=3)
    zc = (z0 + z1) // 2
    for z in (z0 - 1, z1 + 1):
        for y in range(1, y_spring):
            m.put(x, y, z, quoin=(y // 2) % 2 == 0)
        p.set(x, y_spring - 1, z, "gold")
    half = (z1 - z0) // 2
    k = 0
    while True:
        a, b = zc - half + k, zc + half - k
        y = y_spring + k
        if a == b:
            p.set(x, y, a, "carve")
            break
        p.stair(x, y, a, "south", "top", "white_st")
        p.stair(x, y, b, "north", "top", "white_st")
        k += 1
    for z in range(z0 - 1, z1 + 2):
        for y in range(y_spring, 13):
            if p.get(x, y, z) == "air":
                m.put(x, y, z)


# ============================================================================================ (7) ribbed dome
def ribbed_dome(p, cx, cz, y0, pal=PAL, lantern_top=19):
    """The ribbed dome over the chamber, standing on the roof at y0 (radius 3): an octagonal DRUM two courses tall
    (quartz pillars on the eight ribs, teal lit glass between, a calcite sill, a gold band on top), then the SHELL in
    explicit courses - ring radius 3 (full), ring 3 (stairs climbing in), ring 2, ring 1, the crown - deep-slate
    tiles between QUARTZ RIBS on the four axes and four diagonals, so eight white meridians climb to the crown; a
    carved crown cell, a quartz lantern base and the SUN (an ochre heart) at lantern_top. Returns the crown's y."""
    def rib(x, z):
        dx, dz = x - cx, z - cz
        return dx == 0 or dz == 0
    rim = GP.ring(3, cx, cz)
    for (x, z) in rim:
        dx, dz = x - cx, z - cz
        for y in (y0 + 1, y0 + 2):
            if rib(x, z) or abs(dx) == abs(dz):
                p.set(x, y, z, "pillar", axis="y")
            else:
                p.set(x, y, z, "glass_block" if y == y0 + 2 else "white")
        p.set(x, y0 + 3, z, "gold")
    for (x, z) in GP.disc(2, cx, cz):
        p.set(x, y0 + 1, z, "base")
        p.set(x, y0 + 2, z, "light" if (x + z) % 2 else "base")
        p.set(x, y0 + 3, z, "base")
    yb = y0 + 3
    # the shell in four courses: ring 3 full, ring 3 stairs, ring 2 stairs (the diagonal ribs step in to (1, 1)),
    # ring 1 stairs; EIGHT quartz ribs - the four axes and the four diagonals - climb to the crown, slate between
    def axis(x, z):
        return x == cx or z == cz
    def diag(x, z):
        return abs(x - cx) == abs(z - cz)
    y = yb + 1
    for (x, z) in GP.ring(3, cx, cz):
        p.set(x, y, z, "trim" if axis(x, z) or diag(x, z) else "roof")
    for (x, z) in GP.disc(2, cx, cz):
        p.set(x, y, z, "roof")
    y += 1
    for (x, z) in GP.ring(3, cx, cz):
        p.stair(x, y, z, toward(x, z, cx, cz), "bottom", "white_st" if axis(x, z) or diag(x, z) else "roof_st")
    for (x, z) in GP.disc(2, cx, cz):
        p.set(x, y, z, "roof")
    y += 1
    for (x, z) in GP.ring(2, cx, cz):
        p.stair(x, y, z, toward(x, z, cx, cz), "bottom", "white_st" if axis(x, z) else "roof_st")
    for (x, z) in GP.disc(1, cx, cz):
        p.set(x, y, z, "trim" if diag(x, z) and (x, z) != (cx, cz) else "roof")
    y += 1
    for (x, z) in GP.ring(1, cx, cz):
        p.stair(x, y, z, toward(x, z, cx, cz), "bottom", "white_st")
    p.set(cx, y, cz, "roof")
    top = y
    p.set(cx, lantern_top, cz, "sunheart", axis="y")
    if lantern_top == top + 1:                                   # L1: the sun in a gold cradle on the crown
        for (x, z) in GP.ring(1, cx, cz):
            if rib(x, z):
                p.stair(x, lantern_top, z, toward(x, z, cx, cz), "bottom", "gold_st")
    return top


# ============================================================================================ (8) lantern spire (L3)
def lantern_spire(p, cx, cz, y0, pal=PAL):
    """L3: from the dome's crown at y0 the ribbed LANTERN SPIRE rises ten courses: an open lantern (quartz pillars on
    the diagonals, lit teal glass on the axes, a shroomlight core) three courses, a gold band with quartz stairs
    climbing in over it (the broach), a quartz shaft, a gold knop, and the SUN DISC at the tip: a 3 x 3 gold disc
    facing south with an ochre heart and end-rod rays up, down and sideways. Returns the top y (y0 + 9)."""
    for y in (y0, y0 + 1, y0 + 2):
        for (dx, dz) in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
            p.set(cx + dx, y, cz + dz, "pillar", axis="y")
        for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            p.set(cx + dx, y, cz + dz, "glass_block" if y != y0 + 2 else "white")
        p.set(cx, y, cz, "light")
    y = y0 + 3
    for dx in (-1, 0, 1):                                            # the gold band
        for dz in (-1, 0, 1):
            if dx or dz:
                p.set(cx + dx, y, cz + dz, "gold")
    p.set(cx, y, cz, "pillar", axis="y")
    y += 1
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):              # the broach: stairs climbing in
        p.stair(cx + dx, y, cz + dz, toward(cx + dx, cz + dz, cx, cz), "bottom", "white_st")
    p.set(cx, y, cz, "white")
    p.set(cx, y + 1, cz, "gold")                                     # the knop under the sun
    dy = y + 3                                                       # the disc's centre
    for dx in (-1, 0, 1):
        for yy in (dy - 1, dy, dy + 1):
            p.set(cx + dx, yy, cz, "gold" if dx or yy != dy else "sunheart", **({} if dx or yy != dy else {"axis": "z"}))
    p.set(cx - 2, dy, cz, "spike", facing="west")
    p.set(cx + 2, dy, cz, "spike", facing="east")
    for dx in (-1, 0, 1):
        p.set(cx + dx, dy + 2, cz, "spike", facing="up")
    for dx in (-1, 1):
        p.set(cx + dx, dy - 2, cz, "spike", facing="down")
    return dy + 2


# ============================================================================================ (9) the lodge
def lodge(p, x0, x1, z_eave, z_wall, y_eave, pal=PAL, seed=5, chimney_x=None, dormers=()):
    """The lean-to lodge against the nave's north wall (z_wall, the wall itself is not drawn): a stone box x0..x1,
    z_eave..z_wall-1 to y_eave - 1, small lit windows every third bay, a slate lean-to roof climbing from the eave
    (z_eave, y_eave) to the wall, two lit dormers, a chimney."""
    m = GT.Mason(p, y_eave + 6, seed, plinth=3)
    for x in range(x0, x1 + 1):
        for z in range(z_eave, z_wall):
            for y in range(1, y_eave):
                edge = z == z_eave or x in (x0, x1)
                if edge:
                    m.put(x, y, z, quoin=(x in (x0, x1) and z == z_eave and (y // 2) % 2 == 0))
                else:
                    p.set(x, y, z, "base")
        if (x - x0) % 3 == 1 and x < x1:
            p.set(x, 4, z_eave, "glass")
            p.set(x, 5, z_eave, "glass")
            p.set(x, 4, z_eave + 1, "light")
            p.slab(x, 3, z_eave - 1, "top", "trim_slab")
        p.stair(x, y_eave - 1, z_eave - 1, "north", "top", "band_st") if False else None
    for x in range(x0, x1 + 1):
        p.stair(x, y_eave - 1, z_eave - 1, "south", "top", "band_st")       # the eaves course, proud
        for k, z in enumerate(range(z_eave - 1, z_wall)):
            y = y_eave + k
            p.stair(x, y, z, "south", "bottom", "roof_st")
            if k:
                p.set(x, y - 1, z, "roof")
    for x in dormers:
        z = z_eave + 1
        y = y_eave + 2
        p.set(x, y, z, "glass_block")
        p.set(x, y, z + 1, "light")
        p.set(x - 1, y, z, "trim")
        p.set(x + 1, y, z, "trim")
        p.stair(x - 1, y + 1, z, "east", "bottom", "roof_st")
        p.stair(x + 1, y + 1, z, "west", "bottom", "roof_st")
        p.set(x, y + 1, z, "roof")
        p.stair(x, y + 2, z, "south", "bottom", "roof_st")
    if chimney_x is not None:
        for y in range(1, y_eave + 6):
            p.set(chimney_x, y, z_wall - 1, "base")
        p.set(chimney_x, y_eave + 6, z_wall - 1, "base_wall")
    return p


# ============================================================================================ (11) forecourt
def forecourt(p, sx, sz, lane_z=(6, 10), pal=PAL):
    """y 0 everywhere: deepslate under the masses is laid by the builders; here the calm paving: polished andesite
    setts with patches of andesite and tuff, a deepslate kerb ring at the plot's edge, the processional strip (polished
    diorite) running from the spawn out of the gate, and the sun mosaic (calcite ring, gold boss) under the cradle."""
    for x in range(sx):
        for z in range(sz):
            h = hsh(x, z, 4) % 12
            p.set(x, 0, z, ("pave", "pave", "pave", "pave_b", "pave", "pave", "pave_c", "pave", "pave", "pave_b",
                            "pave", "pave")[h])
    for x in range(sx):
        for z in (0, sz - 1):
            p.set(x, 0, z, "base")
    for z in range(sz):
        for x in (0, sx - 1):
            p.set(x, 0, z, "base")
            if x == 0 and z in lane_z or lane_z[0] - 1 <= z <= lane_z[1] + 1 and x == 0:
                continue


def sun_mosaic(p, cx, cz, r=2.5):
    for x in range(int(cx - r - 1), int(cx + r + 2)):
        for z in range(int(cz - r - 1), int(cz + r + 2)):
            d = math.hypot(x - cx, z - cz)
            if d <= r:
                p.set(x, 0, z, "white" if d > r - 1 else "strip")
            if (x == cx or z == cz) and 0.5 < d <= r:
                p.set(x, 0, z, "gold")
    p.set(cx, 0, cz, "gold")


# ============================================================================================ (12) conifer
def conifer(p, x, z, h=7, pal=PAL):
    """A small dark fir: a log, leaf tiers (radius 1 crosses) stepping to a tip."""
    for y in range(1, h):
        p.set(x, y, z, "log", axis="y")
    for y in range(3, h + 1):
        rr = 0 if y >= h else 1
        for dx in range(-rr, rr + 1):
            for dz in range(-rr, rr + 1):
                if abs(dx) + abs(dz) <= rr and (dx or dz or y >= h):
                    p.set(x + dx, y, z + dz, "leaves", **LEAF)


CATALOGUE = [
    ("gate_tower", lambda: gate_tower()),
    ("nave_wall", lambda: nave_wall(9)),
    ("lodge", lambda: lodge(Part(PAL), 0, 10, 0, 4, 6, dormers=(3, 7))),
]
