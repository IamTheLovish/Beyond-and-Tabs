#!/usr/bin/env python3
"""PILOT (Oct 28): the Brassworks VEHICLE LAB as an ENGINE SHED imitating ONE picture, lovish's ref18
(design/refs/vehicle_labs/ref18_brick_hall_central_tower.png). Brief: design/vehicle_labs/briefs/brass_pilot.md with
its "Changes sent to the builder" (T1 footprint x 0..12, z 0..20, spawn (6, 10), exit lane z 8..12 x 8 tall out the
WEST face, L3 <= 22 tall, L1 <= 12). lovish approved the r3 GREYBOX (greybox() below, kept exactly); detail() paints it
region by region after the picture (lovish: "keep studying it and learning how to make it super detailed and
intricate"), and level1() takes L1 out of L3.

THE PICTURE, STUDIED (230 x 180 px, a frontal shader shot; measured on the silhouette, ground line at ~y 153)
  - THE WHOLE: very wide and low, symmetric about ONE central tower. Left to right: end pavilion | 4-5 arcade bays |
    tower | 4-5 arcade bays | end pavilion. Width : tower height ~ 230 : 139, so the building is ~1.7x wider than the
    tower is tall, and the hall is ~4x wider than it is tall.
  - THE HALL (x 35..90 and 140..195 in the picture): a ground arcade of dark round-headed arches (y 128..153, the
    darkest band of the picture - squinted it reads as a dark strip along the ground), a lit string course on top
    (y ~128), then a red roof (y 95..119) carrying a row of dormers. Hall to roof top ~58 px = 0.42 of the tower; the
    walls (ground to eaves) ~34 px = 0.25 of the tower; the roof is ~40% of the hall's height, a steep visible plane.
  - THE END PAVILIONS (x 0..35 and 195..230): a little taller than the hall - walls to ~y 100, steep pyramid roofs to
    y 66..77 (~0.6 of the tower) - flaring outward at the outer edges; they close the composition at both ends.
  - THE TOWER (x 88..142 at its foot, ~23% of the width): rises from the arcade in STEPPED STAGES, each set back -
    stage 1 to ~y 95 (just above the hall roof, the glowing great door at its base y 128..153), stage 2 to ~y 55
    (x 95..135), stage 3 to ~y 30 (x 103..128), then a short pointed spire to the tip at y 14. Tip ~139 px = 2.4x the
    hall's roof top. Squinted: one tall stepped triangle on a long low dark band, with two lesser humps at the ends.

THE PICTURE, REGION BY REGION (detail round 1: each region cropped from ref18 and blown up 4x / 8x NEAREST; 1 block of
the build ~ 11 px across, ~6.3 px up in the picture)
  - COLOUR, measured (10-colour quantize of the building): dark crimson-brown 33,12,13 and 71,47,47 dominate; mid red
    93,45,43 and bright tile red 132,40,39 on the roofs and the tower's heart; the "white" is a warm pale stone
    149,118,110 - NOT bright white - in thin lines; grey stone 45-80 in the arcade. So: few pale cells, never planes.
  - ARCADE (y 128..153 px = ~4 blocks): a near-black band. Grey-stone piers (~50,60,58) with a pale plinth, between them
    deep CRIMSON bays (35..80, 0..10, 10..30 - red glass glowing) under dark heads; ~12 bays each side in the picture,
    our 3 a side at 2-block pitch (pier, bay, pier). On top a grey string course carrying a DOTTED LINE OF SMALL WHITE
    LIGHTS (y ~131 px, one per bay) - the brightest marks on the whole front; then a dark band to the eaves.
  - HALL ROOF (y 95..125 px, ~4 courses): red tile rows striped by pale ribs that run down the slope (one per bay) -
    a grid of red panes in pale frames: the "dormer rows". Our hall roof is only 2 bays wide between tower and wing,
    so: a pale rib over each pier, a lit dormer over the bay, pale eave slabs.
  - END WINGS (x 0..45 and 185..230 px): steep tiers swept UP to the outer end, each tier a red band with a pale lip
    (the eave line of every step is the white line), pale ribs up the middle and outer edges, a cross-like pale
    pattern on the gable face. -> every slab/stair edge of the swept roof pale, the crest pale, a pale tier line on the
    gable courses 9 and 11, a white rib up the slope's middle (x 3), brass finial on the high end.
  - TOWER BASE / GREAT DOOR (x 100..130, y 110..153 px): flanking piers of dark stone with pale quoins, a round-headed
    opening; INSIDE, stacked red and pale bands (a vault seen in perspective) and ONE warm glow at y ~118 px - high in the
    door, not a lit floor. -> dark jambs, a white arch (stair haunches, chiseled keystone, glass tympanum lit), the far
    wall striped red / pale (red nether brick / granite) with the glow high (y 5..6) and a lit doorway at its foot.
  - TOWER MID STAGES (y 55..100 px): dark red-brown flanks (nether brick), a bright red heart down the centre, a round
    medallion (the clock) at y ~75 px with pale tracery, set-backs marked by thin pale ledges. -> flank = nether brick,
    heart = red nether brick, the brass clock 3 x 2 at stage 2, white ledges only on the front-most step of courses 9 /
    13 / 17.
  - SPIRE (y 14..55 px): a narrow triangle, red heart, dark flanks, the four ARRISES picked out in pale steps climbing to
    the tip; lit slots in the red. -> white only where a stair sits on the outline's corners (the arrises), a lit belfry
    slot (glass on ochre) at y 16..17, brass finial + rod at the tip.
  - LIGHT: only the arcade's dotted line, the great door's glow, two lit bays, the dormers and the belfry - nothing loose.

THE MASSING, round 3 (blocks; x = depth west -> east, z = length north -> south, y 0 = ground; front faces WEST).
  Slopes are tools/shape_kit.py shapes voxelized with its stair / slab edge pass, so flanks read as lines.
  ground      y 0, the whole 13 x 21 pad; lawn strip x 0 in front; dark setts in the lane.
  hall range  SHALLOW and LOW: x 1..7, z 0..20, walls y 1..5, a straight pitch from y 6 to its ridge at y 9 (x 4).
              Arcade: dark bays 1 wide x 4 tall at z 0, 2, 4, 6 and 14, 16, 18, 20.
  works shed  behind the hall: x 8..12, z 0..20, flat roof at y 4, a darker tone; one stack x 10..11, z 0..1 to y 10,
              hidden behind the north wing's high end from the front.
  end wings   x 1..6, z 0..3 and 17..20, walls y 1..6, under HALF-SWEPT roofs: the ridge on the outer end at y 13
              (cell 12), concave toward the hall - the picture's up-swept ends.
  tower       ONE steep pyramid: a base block x 0..6, z 6..14, y 1..5 (the great door through it), then the pyramid
              from y 6 (half-widths 3.5 in x, 4.5 in z: 9 wide along the front at the hall's roof line) with straight
              flanks to its apex at y 23 (top cell 21 - 22 tall). No ledges, no needle.

ANCHORS (structure-local block coords x, y, z)
  spawn cell      (6, 1, 10)       the hall floor at the inner end of the lane
  spawn column    (6, 1..21, 10)   air to the sky through the ridge vent at every level
  exit lane       x 0..6, z 8..12, y 1..8 - 5 wide x 8 tall, out of the west face under the tower
  great door      (0, 1..8, 8..12) the tower's door opening; its arch head at y 9 (detail stage)
  clock face      (1, 12..14, 10)  stage 2's west face, centred over the door (detail stage)
  spire tip       (3, 21, 10)      the finial
  pavilion tips   (PAV_D//2, top, 2) and (PAV_D//2, top, 18)

PASSES (the sheets: design/vehicle_labs/brass_greybox_vs_ref.png = round 1, corner view only;
        brass_greybox_vs_ref_r2.png = round 2, tools/ref_compare.py --front: the WEST ELEVATION beside ref18, its
        squint, then the corner view). Front profile = the tallest course seen from the west per z column:
  ref18 (tip 22) 13.5 12.6 12.1 10.8  9.2  9.2  9.2  9.7 14.3 18.8 21.9 18.2 12.2  9.2  9.2  9.2  9.2 10.9 12.3 12.8 13.7
  round 1 (corner-view squint only; a 12-deep hall with a 4-high roof, a 6-deep tower)
  1-5  walls 8 -> 6, roof 12 -> 10, pavilions 6 -> 4 wide, stage ledges; profile within ~1 block, but the corner view
       read as a chunky block: the deep hall roof outweighed the tower (coordinator's read).
  round 2 (front elevation first, then squint, then the corner view)
  1-4  hall 7 deep, works shed behind, pavilions with eave kicks, stages 11 / 14 / 17 + needle:
       11 13 13 11 9 9 9 11 14 18 21 18 14 11 9 9 9 11 13 13 11 - the outline within ~1.5 blocks, but the tower read
       as a wedding cake of set-backs, not the picture's one triangle (coordinator's read).
  round 3 (tools/shape_kit.py; sheet brass_greybox_vs_ref_r3.png)
  1  a shape_kit pyramid from y 6, a pitched hall roof, swept wing roofs with a kick: the shapes overwrote each other
     (a slab from one cut the next one's solid roof: light holes in the hall roof); the kick read as a notch.
  2  shapes MERGED (a full cell stays full), no kick (the concave fall alone reads as up-swept), apex y 23:
     11 11 11 8 8 8 8 11 15 19 21 19 15 11 8 8 8 8 8 9 11 - one triangle; the stack poked over the north wing.
  3  wing ridge to 13 (cell 12), the stack hidden behind it, the lane's far wall in the wall tone:
     12 10 8 8 8 8 8 11 15 19 21 19 15 11 8 8 8 8 8 10 12 - the tower within 1 block of ref18 in every column;
     the hall 1 lower than the picture (as asked); the wings' fall 1-2 lower at z 2..3 / 17..18.

DETAIL PASSES (sheet design/vehicle_labs/brass_detail_vs_ref_r1.png: the west elevation beside ref18 stretched to the
same 21-column grid, both squints, the corner view, and seven region crops side by side on that grid)
  1  quartz on every edge + ledges: the whole front read WHITE - the picture's pale is thin lines, not planes.
  2  grey stone flanks / piers: the tower turned into a grey castle; the picture's flanks are dark red-brown.
  3  measured palette: nether-brick flanks, red heart, sea-lantern dots; still a white staircase on every step.
  4  white only on arrises (|rx - rz| test) + ledges; but the pyramid's every stair is an arris at this size.
  5  arcade darkened (deepslate piers, crimson bays, two lit), stone string course; outline rim white -> still heavy.
  6  white only on the outline's CORNER cells (the four arrises) and the front step of the ledges: the triangle now
     reads as dark red with white lines climbing it, like the picture; the door glow cut to one high light.
  7  the door's far wall in stacked red / pale bands (the picture's vault), wing tiers striped with a white middle rib,
     the gable lancets dropped (they read as two orange blobs); finials 1 lower (outline within 1 block of r3).
  L1 (2 passes): the tower's first stage capped by a white-rimmed ledge; the far wall kept so the door is not a hole.
DETAIL ROUND 2 (critic on r1: resemblance 4/10, quality 5/10, silhouette WORSE than r3; sheet
design/vehicle_labs/brass_detail_vs_ref_r2.png, lit). The tower is now built as explicit stages (tower(): base block,
arch frontispiece, stages 7 / 5 / 3 wide set back one block each, a brick point), NOT painted onto the pyramid's stair
edges - that is what made r1's flares and lantern box.
  1  outline: per-column tops equal to r3 (13 11 9 9 9 9 9 12 16 20 22 20 16 12 9 9 9 9 9 11 13); no stair flares, no
     pole; the wing finials gone. Palette cut to red nether brick / polished blackstone / quartz; read the tower as a
     cream column with a snowman face (cream on every stage corner + a ring round the clock).
  2  cream as one-course lines only: dashed arris quoins, front lips; the clock's ring dropped; the light dots recessed.
  3  stage faces red with dark flanks (was dark with a red stripe); the hall frieze red; the door's far wall deep red
     banded dark. Front balance 42 / 37 / 18 (red / black / cream).
  4  stage lips dashed (cream at the arrises and centre only); the works stack's top hidden behind the wing crest.
     Balance 44 / 37 / 16 + 3% light, outline still equal to r3 column for column.
DETAIL ROUND 3 (critic on r2: 4 / 4 - the tower still read as stacked stages; sheet brass_detail_vs_ref_r3.png, lit).
Fixed BY NUMBERS first. The spire is ONE line, HW_TABLE (row -> the line's half-width at each half row and the outline
that quantizes it), half-width 3.5 at the hall roof (the r3 base, 7 wide) to 0 at y 22; a stair (tall half in) wherever
the outline steps in half a block. Colour measured on the RENDERED PIXELS (scratch tool: near-black = luma < 62 and
chroma < 36, red = red-dominant above it): ref18's building reads 42 / 38 / 2 (red / near-black / pale).
  1  spire from shape_kit (a sampled pyramid): rows 9-13 came out one width - a box shoulder; max deviation 1.0 block.
  2  spire from the table, quantized per half row (best monotone outline); cream single cells on the front arrises.
     Blackstone rendered GREY, not black (pixels: 39 red / 37 black / 14 other) -> the darks are nether brick
     (65,37,40) and deepslate tiles.
  3  pilasters polished granite (warm, paler than the bays), the bays red glass over red brick under a tinted-glass
     head, end rods hung under the eave every 2 blocks; elevation 53 / 38 / 8, corner 59 / 27 / 6.
  4  wing roofs gabled: each course steps in 1 toward a 1-wide ridge with a brick finial, the outer end's eave an
     overhanging cream stair.
  5  cream moved to mid-run cells (no step "ears"), then back to the step stairs (single wedges ON the line); a pass
     that let a full row follow a stair at the same width made notches - the outline must never widen going up.
  Spire vs ref18's line: max 1.17 blocks at the foot (r3's 7-wide base against ref18's 5), <= 0.6 from y 11 up,
  <= 0.5 against its own line everywhere.
DETAIL ROUND 4 (critic on r3: 4 / 4, one dark-red lump; three critics had asked for three colour schemes, so the art
director read a FIXED material map off ref18 itself: a GRID - grey / dark-grey frames 1 cell wide, bright red panels
1-2 cells wide set into them, pale stone only as ledge lines, tiny white lights; sheet brass_detail_vs_ref_r4.png).
Blocks picked by rendered-pixel distance to ref18's red-class mean (104, 49, 46): red terracotta d 5, mangrove planks
d 7 (its stairs / slabs are the red shapes), red nether brick d 54 (dropped). Dark = polished blackstone bricks (84 %
near-black pixels; deepslate tiles only 51 %). Shares = rendered pixels of the lit west elevation, ref18 red / grey /
dark / pale 49 / 18 / 31 / 2.
  1  every region re-skinned in grey lattice: 12 / 43 / 35 / 10 - a grey castle; the spire solid grey (each stair
     row is the outline, so "grey on stair rows" painted it all).
  2  stair rows red, arrises grey, flank mullions every 3rd cell, roof ribs every 3rd column: 24 / 28 / 35 / 13.
  3  arcade as dark piers / red panels / pale light ledge / candles: 29 / 32 / 31 / 8; grey still 14 over (deepslate
     tiles render mid grey).
  4  dark = blackstone bricks, wing ribs only as bands: 32 / 23 / 37 / 8.
  5-8  the r3 cream cornice turned into the ONE pale ledge carrying the lights; the hall eave a red stair course; the
     door's far wall a red vault band over dark: 38 / 20 / 35 / 7.
  9-10 roof ridge course grey, rib eaves grey, the ornament a pale diamond round a red heart, arrises dashed:
     39 / 20 / 35 / 7 (corner view 38 / 25 / 27 / 9). Outline: every column within 1 of the r3 greybox; the 1-wide
     point is 2 rows (pale + a brass slab), the width table forbids 1 wide below y 20.

Usage: python3 tools/lab_vehicle_brass.py            (writes design/vehicle_labs/brass_l3.nbt and brass_l1.nbt, checks)
       python3 tools/lab_vehicle_brass.py --greybox  (the approved r3 greybox: design/vehicle_labs/brass_l3_greybox.nbt)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np                    # noqa: E402

import gen_structures as gs           # noqa: E402
import shape_kit as sk                # noqa: E402

SX, SZ, H_MAX = 13, 21, 22
SPAWN = (6, 1, 10)
LANE_X, LANE_Z, LANE_H = range(0, 7), range(8, 13), 8
OUT = "design/vehicle_labs/brass_l3_greybox.nbt"

WALL, ROOF, HOLE, GROUND, LAWN = "terracotta", "red_nether_bricks", "obsidian", "stone_bricks", "grass_block"
SHED = "deepslate_bricks"     # the works behind the hall (a second, quieter tone so the front range reads on its own)
# shape_kit's roles: a full cell takes the role, a stair cell role + "_st", a slab cell role + "_slab"
PAL = {"roof": ROOF, "roof_st": "red_nether_brick_stairs", "roof_slab": "red_nether_brick_slab"}

# the massing, round 3 (tuned against the FRONT elevation, column by column; see PASSES above)
HALL_X = (1, 7)       # the hall range is shallow: a long low band along the front, the works shed behind it
HALL_TOP = 5          # hall walls y 1..5; a straight-pitched roof over x 0.5..7.5 from y 6 to its ridge at y 9 (cell 8)
ARCH_H = 4            # arcade openings y 1..4
ARCH_Z = (0, 2, 4, 6, 14, 16, 18, 20)   # 4 bays each side of the tower, running under the wings as in the picture
WING_X, WING_Z = (1, 6), ((0, 3), (17, 20))     # end wings' walls, y 1..WING_TOP
WING_TOP = 6
# the wings' roofs: half a swept gable each, its ridge on the outer end (z -0.5 / 20.5) at y 13 (cell 12), falling
# concave toward the hall (steep at the outer end, flattening inward) - the picture's up-swept ends
WING_ROOF = dict(h=6.8, y0=7.0, half=4.5, sweep=1.8, kick=0.0)
# the tower: ONE steep pyramid over a base block, centred on (TX, TZ); half-widths at its foot; apex at y 22
TX, TZ, THX, THZ = 3.0, 10.0, 3.5, 4.5           # foot x -0.5..6.5 (cells 0..6), z 5.5..14.5 (cells 6..14)
T_FOOT, T_APEX = 6.0, 23.0                       # the pyramid rises from the hall's roof line to the apex
SHED_X, SHED_TOP = (8, 12), 4                   # the works shed: x 8..12, flat roof at y 4
STACK = (10, 11, 0, 1, 10)                      # one smokestack behind the north wing's high end, hidden from the front


class G:
    def __init__(self):
        self.b = {}

    def set(self, x, y, z, n, **props):
        if 0 <= x < SX and 0 <= z < SZ and 0 <= y < H_MAX:
            self.b[(x, y, z)] = (n, props)

    def fill(self, x0, x1, y0, y1, z0, z1, n):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.set(x, y, z, n)

    def clear(self, x, y, z):
        self.b.pop((x, y, z), None)


def pyramid(cx, cz, hx, hz, y0, y1):
    """A rectangular pyramid: half-widths hx, hz at y0, straight flanks to the apex at y1."""
    def f(x, y, z):
        t = np.clip((y - y0) / (y1 - y0), 0, 1)
        rho = np.maximum(np.abs(x - cx) * hz / hx, np.abs(z - cz))
        return np.maximum(rho - hz * (1 - t), np.maximum(y0 - y, y - y1))
    return sk.Shape(f, (cx - hx, cx + hx, y0, y1, cz - hz, cz + hz))


def pitched(x0, x1, z0, z1, y0, y1):
    """A straight-pitched gable roof over x0..x1 (continuous), ridge along z at y1, eaves at y0."""
    cx, hw = (x0 + x1) / 2, (x1 - x0) / 2

    def f(x, y, z):
        top = y0 + (y1 - y0) * (1 - np.abs(x - cx) / hw)
        return np.maximum(np.maximum(y - top, y0 - y), np.maximum(np.abs(x - cx) - hw,
                                                                np.maximum(z0 - z, z - z1)))
    return sk.Shape(f, (x0, x1, y0, y1, z0, z1))


def swept_half(x0, x1, z_ridge, toward, h, y0, half, sweep, kick):
    """Half a swept gable: ridge along x at z_ridge (height y0 + h), the slope running `toward` (+1 / -1 in z) for
    `half` blocks, concave, its last quarter kicking up by `kick`."""
    def f(x, y, z):
        s = np.clip((z - z_ridge) * toward / half, 0, None)
        lift = kick * np.clip((s - 0.75) / 0.25, 0, 1) ** 2
        top = y0 + h * np.clip(1 - s, 0, 1) ** sweep + lift
        return np.maximum(np.maximum(y - top, y0 - y), np.maximum(np.maximum(x0 - x, x - x1), s - 1))
    zz = sorted((z_ridge, z_ridge + toward * half))
    return sk.Shape(f, (x0, x1, y0, y0 + h, zz[0], zz[1]))


def add(g, shape):
    """shape_kit's cells merged into g in the roof tone: a cell already full stays full (a slab or stair from one
    shape never cuts into another's solid roof)."""
    tmp = G()
    sk.build(shape, "roof", tmp, pal=PAL)
    for q, blk in tmp.b.items():
        old = g.b.get(q)
        if old is None or (old[0] in PAL.values() and old[0] != ROOF) or blk[0] == ROOF:
            g.b[q] = blk


def greybox():
    g = G()
    for x in range(SX):
        for z in range(SZ):
            g.set(x, 0, z, LAWN if x == 0 else GROUND)
    # the works shed behind (flat, low) and its stack
    g.fill(SHED_X[0], SHED_X[1], 1, SHED_TOP, 0, 20, SHED)
    x0, x1, z0, z1, top = STACK
    g.fill(x0, x1, SHED_TOP + 1, top, z0, z1, SHED)
    # the hall range: low walls under a shallow straight pitch
    g.fill(HALL_X[0], HALL_X[1], 1, HALL_TOP, 0, 20, WALL)
    add(g, pitched(HALL_X[0] - 0.5, HALL_X[1] + 0.5, -0.5, 20.5, HALL_TOP + 1, HALL_TOP + 4))
    # the end wings: a little over the hall, under half-swept roofs rising to their outer ends
    for (z0, z1), (ridge, toward) in zip(WING_Z, ((-0.5, 1), (20.5, -1))):
        g.fill(WING_X[0], WING_X[1], 1, WING_TOP, z0, z1, WALL)
        add(g, swept_half(WING_X[0] - 1.5, WING_X[1] + 0.5, ridge, toward, **WING_ROOF))
        for z in range(z0 + 1, z1):
            g.set(WING_X[0], WING_TOP - 1, z, HOLE)
    for x in range(2, HALL_X[1] + 1, 2):                # the camera's south end: ground bays
        g.fill(x, x, 1, ARCH_H, SZ - 1, SZ - 1, HOLE)
    for z in ARCH_Z:                                    # the arcade: a dark ground band
        g.fill(HALL_X[0], HALL_X[0], 1, ARCH_H, z, z, HOLE)
    # the tower: a base block carrying ONE steep pyramid, flanks straight from the roof line to the apex
    g.fill(0, 6, 1, int(T_FOOT) - 1, 6, 14, WALL)
    add(g, pyramid(TX, TZ, THX, THZ, T_FOOT, T_APEX))
    # the great door: the exit lane through the tower's base, backed by the hall's dark back wall
    for x in LANE_X:
        for z in LANE_Z:
            for y in range(1, LANE_H + 1):
                g.clear(x, y, z)
            g.set(x, 0, z, "deepslate_tiles")
    for z in LANE_Z:     # the passage's far wall: wall tone, so the door reads as a deep opening, not a black slab
        g.fill(HALL_X[1], HALL_X[1], 1, LANE_H, z, z, WALL)
    # the spawn column: air to the sky (a vent shaft up the tower's back flank)
    for y in range(1, H_MAX):
        g.clear(SPAWN[0], y, SPAWN[2])
    return g


# ============================================================================================ the detail (L3)
# ROUND 4: one material language, read from ref18 by the art director - a GRID: grey / dark-grey stone frames 1 cell
# wide with bright red panels 1-2 cells wide set into them, pale stone only as ledge lines, tiny white lights.
# Block picks measured on our pack's rendered pixels against ref18's red-class mean (104, 49, 46):
#   red_terracotta (104, 51, 41) d 5, mangrove planks (104, 45, 40) d 7 [its stairs / slabs are the red shapes],
#   red nether brick (78, 14, 14) d 54 (maroon: r3's dark lump - not used). Dark-grey: polished blackstone bricks render
#   84 % near-black pixels; deepslate tiles only 51 % (they read mid grey in the lit render, like r2's blackstone).
# A painted cell keeps its shape (full / stairs / slab) and takes its family's block of that shape.
FAM = {
    "red": ("red_terracotta", "mangrove_stairs", "mangrove_slab"),
    "grey": ("stone_bricks", "stone_brick_stairs", "stone_brick_slab"),
    "dark": ("polished_blackstone_bricks", "polished_blackstone_brick_stairs", "polished_blackstone_brick_slab"),
    "pale": ("calcite", "smooth_quartz_stairs", "smooth_quartz_slab"),
    "frame": ("deepslate_bricks", "deepslate_brick_stairs", "deepslate_brick_slab"),
}
OUT_L3, OUT_L1 = "design/vehicle_labs/brass_l3.nbt", "design/vehicle_labs/brass_l1.nbt"
ROLE_OF = {n for f in FAM.values() for n in f} | set(PAL.values()) | {WALL, HOLE, SHED}
ARC_Z = [z for z in range(SZ) if not 6 <= z <= 14]       # the arcade's columns on the west face (hall + wings)
LIGHT = "white_candle"


def shape_of(name):
    return 1 if name.endswith("_stairs") else 2 if name.endswith("_slab") else 0


def paint(g, q, fam):
    """Recolour a cell in a family, keeping its shape and its stair / slab state."""
    if q in g.b:
        n, p = g.b[q]
        g.b[q] = (FAM[fam][shape_of(n)], p)


def put(g, q, name, props=None, **kw):
    g.set(*q, name, **dict(props or {}, **kw))


def stair(fam, facing, half="bottom"):
    return FAM[fam][1], dict(facing=facing, half=half, shape="straight", waterlogged="false")


def slab(fam, t="bottom"):
    return FAM[fam][2], dict(type=t, waterlogged="false")


# ---- the tower: r3's continuous taper (THE LINE below), the 1-wide top now at most 2 rows
T_CX, T_CZ, T_BACK = 3, 10, 5          # the spire's axis (x, z) and its back column (x 6 = the spawn column)
T_Y0, T_APEX, T_HW0 = 9, 22.0, 3.5      # the line: half-width 3.5 at the hall's roof line (y 9) to 0 at y 22
TOP_1W = 20                            # round 4: the outline may be 1 wide only from here (rows 20, 21)
# THE LINE (r3). ref18's flank, measured on its sky mask in this build's block grid (230 px -> 21 blocks, ground..tip ->
# 0..22), is straight: half-width 2.47 at the hall roof (y 9.5), 1.55 at y 15.8, 0.23 at y 21.5. The approved r3 massing
# is 7 wide at the hall roof, so the spire runs from r3's width there to the apex: hw(y) = 3.5 (22 - y) / 13.
# QUANTIZING (front view), per HALF row: a row's lower half ends on a cell edge (lo = k + 0.5); its upper half on that
# edge or half a block in (hi = lo - 0.5: the outer ring a stair, tall half facing in); the outline never widens going
# up. width_table() takes the monotone outline with the smallest summed square deviation from the line; round 4 adds
# one rule: 1 wide (lo 0.5) only from TOP_1W up - no needle.


def hw(y):
    return max(0.0, T_HW0 * (T_APEX - y) / (T_APEX - T_Y0))


def width_table():
    """THE WIDTH TABLE: row y -> (line half-width at the lower / upper half's middle, lower / upper outline)."""
    best = {9.0: (0.0, 0.0, [])}
    for y in range(T_Y0, int(T_APEX)):
        lo_t, hi_t = hw(y + .25), hw(y + .75)
        nxt = {}
        for prev, (sq, worst, path) in best.items():
            for lo in [k + .5 for k in range(5) if k + .5 <= prev and (k > 0 or y >= TOP_1W)]:
                for hi in [w for w in (lo, lo - .5) if w > 0]:
                    e1, e2 = abs(lo - lo_t), abs(hi - hi_t)
                    cand = (sq + e1 * e1 + e2 * e2, max(worst, e1, e2), path + [(y, lo, hi)])
                    if hi not in nxt or cand[:2] < nxt[hi][:2]:
                        nxt[hi] = cand
        best = nxt
    path = min(best.values(), key=lambda c: c[:2])[2]
    return {y: (round(hw(y + .25), 2), round(hw(y + .75), 2), lo, hi) for y, lo, hi in path}


HW_TABLE = width_table()


def spire_cells():
    """{(x, y, z): ("full",) | ("stairs", inward)} from HW_TABLE: square rings of Chebyshev distance d round the axis
    (cut at the back column); the outer ring d = lo - 0.5 is full, or a stair facing in where the upper half steps in."""
    out = {}
    for y, (_, _, lo, hi) in HW_TABLE.items():
        dmax = int(lo - .5)
        for x in range(max(0, T_CX - dmax), T_BACK + 1):
            for z in range(T_CZ - dmax, T_CZ + dmax + 1):
                d = max(abs(x - T_CX), abs(z - T_CZ))
                if d > dmax:
                    continue
                if d < dmax or hi == lo or d == 0:
                    out[(x, y, z)] = ("full",)
                else:
                    side = abs(z - T_CZ) == d
                    out[(x, y, z)] = ("stairs", ("south" if z < T_CZ else "north") if side else
                                      ("east" if x < T_CX else "west"))
    return out


def tower_fam(x, y, z, d, dmax, stair_row):
    """The tower's skin: grey lattice on the arrises (dashed: on the even rows, where the ledges turn the corner) and on every
    stair row (the set-back ledges), dark-grey mullions on the flanks every 3rd cell, red panels between; inner cells
    red (unseen)."""
    if d < dmax:
        return "red"
    if d == 0:
        return "grey"
    front = x == T_CX - d
    flank = abs(z - T_CZ) == d
    if (front and flank) or (flank and x == T_BACK):
        return "grey" if y % 2 == 0 else "red"       # dashed: the ledge rows carry the line round the corner
    if front:
        return "red"
    return "dark" if (x - (T_CX - d)) % 3 == 0 else "red"


def tower(g):
    """The tower over the great door: base block (y 1..8, x 0..6, z 6..14) and ONE continuous spire from the hall's
    roof line, skinned as a grey lattice with red panels, a projecting grey ledge every 2-3 rows, a pale ornament over
    the door with one warm glow under it, a pale + brass point."""
    for q in [q for q in g.b if q[0] <= 6 and 6 <= q[2] <= 14 and q[1] >= 1]:
        x, y, z = q
        if y >= 9 or (y >= 6 and z in (6, 7, 13, 14)) or (x == 0 and z in (6, 7, 13, 14)):
            del g.b[q]
    # the base's flanks beside the door: dark corner piers (z 6 / 14), grey jambs (z 7 / 13), red panels on the sides
    for z in (6, 7, 13, 14):
        for x in range(0, 7):
            for y in range(1, 9):
                if x == 0:
                    fam = "grey" if z in (7, 13) or y in (4, 8) else "dark" if y == 1 else "red"
                else:
                    fam = "red" if (x % 2 == 1 and y % 3 != 0) else "grey" if y % 3 == 0 else "dark"
                put(g, (x, y, z), FAM[fam][0])
    cells = spire_cells()
    for (x, y, z), c in cells.items():
        lo, hi = HW_TABLE[y][2:]
        dmax = int(lo - .5)
        d = max(abs(x - T_CX), abs(z - T_CZ))
        fam = tower_fam(x, y, z, d, dmax, hi < lo)
        if y >= TOP_1W:
            continue
        put(g, (x, y, z), *(stair(fam, c[1]) if c[0] == "stairs" else (FAM[fam][0], {})))
    # the point: 2 rows at most, pale then brass (no needle)
    put(g, (T_CX, 20, T_CZ), FAM["pale"][0])
    put(g, (T_CX, 21, T_CZ), "waxed_cut_copper_slab", type="bottom", waterlogged="false")
    # projecting grey ledges on the front face (front outline untouched): over the ornament (y 14) and mid-spire (y 16)
    for z in range(8, 13):                                # the ledge over the ornament: a projecting grey stair
        put(g, (0, 14, z), *stair("grey", "east", "top"))
    for y in (16, 18):                                     # and a thin projecting ledge every 2 rows above it
        for z in range(9, 12):
            put(g, (1, y, z), *slab("grey"))

    # the great door's round head: pale voussoirs (haunch stairs at y 9, a crown of three at y 10), a dark lunette
    # with ONE warm glow in it
    for z in (9, 10, 11):
        g.clear(0, 9, z)
        put(g, (1, 9, z), FAM["dark"][0])
    put(g, (0, 9, 8), *stair("pale", "north", "top"))
    put(g, (0, 9, 12), *stair("pale", "south", "top"))
    put(g, (0, 10, 9), *stair("pale", "north", "top"))
    put(g, (0, 10, 11), *stair("pale", "south", "top"))
    put(g, (0, 10, 10), FAM["pale"][0])
    put(g, (1, 9, 10), "shroomlight")
    # the pale ornament above the door: a calcite / quartz diamond on the 5-wide face (x 1, y 11..13, z 9..11)
    for (y, z) in ((12, 9), (12, 11)):                  # the diamond: two pale points either side,
        put(g, (1, y, z), FAM["pale"][0])
    put(g, (1, 13, 10), *slab("pale"))                  # half-cell points above and below (red shows behind)
    put(g, (1, 11, 10), *slab("pale", "top"))
    put(g, (1, 12, 10), FAM["red"][0])                  # the rosette's red heart, ringed by pale
    for (y, z) in ((11, 9), (11, 11), (13, 9), (13, 11)):
        put(g, (1, y, z), FAM["red"][0])
    # the passage: a dark recess (side walls, far wall, lintel underside)
    for z in LANE_Z:                 # the far wall: dark below, a red vault band above (ref18's red-lit interior)
        for y in range(1, LANE_H + 1):
            put(g, (7, y, z), FAM["red" if y >= 5 and z != 10 and y != 7 else "dark"][0])
        for x in range(1, 7):
            if (x, 9, z) in g.b and x > 1:
                put(g, (x, 9, z), FAM["dark"][0])
    for z in (7, 13):
        for x in range(1, 7):
            for y in range(1, 9):
                put(g, (x, y, z), FAM["dark"][0])


def rib(z):
    """The grid's column rule, symmetric about the tower: a grey rib / pier where min(z, 20 - z) % 3 == 0 (z 0, 3, 17,
    20), red panels in the two columns between."""
    return min(z, SZ - 1 - z) % 3 == 0


def arcade(g):
    """The arcade storey on the west face (hall + wings, z 0..5 / 15..20): dark-grey piers projecting at x 0 on every
    2nd column, red panels set back at x 1 between them over a dark recess at their foot under a dark lintel; on top
    the r3 cream cornice recoloured into the grid as ONE pale ledge line (top slabs, projecting) carrying a tiny white
    light (a candle) over every pier; above it a red / grey frieze and the red roof's eave."""
    for z in ARC_Z:
        pier = z % 2 == 0
        for y in range(1, 6):
            g.clear(0, y, z)
        if pier:
            for y in (1, 2, 3):
                put(g, (0, y, z), FAM["dark"][0])
            for y in (1, 2, 3, 4):
                put(g, (1, y, z), FAM["dark"][0])
            put(g, (0, 5, z), LIGHT, candles="1", lit="true", waterlogged="false")
        else:
            g.clear(1, 1, z)
            put(g, (2, 1, z), FAM["dark"][0])
            for y in (2, 3):
                put(g, (1, y, z), FAM["red"][0])
            put(g, (1, 4, z), FAM["dark"][0])
        put(g, (0, 4, z), *slab("pale", "top"))
        put(g, (1, 5, z), FAM["grey" if pier else "red"][0])
        put(g, (0, 6, z), *stair("grey" if rib(z) else "red", "east"))


def ends(g):
    """The two short faces (z 0 north, z 20 south - the corner view's second face) and the works shed: the same grid,
    dark piers on odd x, red panels on even x over a dark recess, a grey lintel course; the wing's outer eave a pale
    lip (the cornice line turning the corner); the shed's flat roof red panels in a grey grid."""
    for zf, zin, inward in ((0, 1, "south"), (SZ - 1, SZ - 2, "north")):
        for x in range(1, SX):
            top = 6 if x <= 6 else 5 if x == 7 else SHED_TOP
            pier = x % 2 == 1 or x == 12
            for y in range(1, top + 1):
                if pier:
                    fam = "dark" if y < top else "grey"
                elif y == 1:
                    g.clear(x, 1, zf)
                    put(g, (x, 1, zin), FAM["dark"][0])
                    continue
                else:
                    fam = "grey" if y in (4, top) else "red"
                put(g, (x, y, zf), FAM[fam][0])
        for x in range(1, 7):
            put(g, (x, 6, zf), *stair("pale", inward, "top"))
    for x in range(SHED_X[0], SHED_X[1] + 1):
        for z in range(SZ):
            grid = rib(z) or x == SHED_X[1]
            put(g, (x, SHED_TOP, z), FAM["dark" if grid else "red"][0])
    x0, x1, z0, z1, top = STACK
    for x in (x0, x1):
        for z in (z0, z1):
            for y in range(SHED_TOP + 1, top + 1):
                put(g, (x, y, z), FAM["grey" if y in (7, top) else "dark"][0])
            if z == 1:
                g.clear(x, top, z)


def roofs(g):
    """The hall roof and the wing roofs as rows of red rectangles (ref18's dormer rows): red stair / slab courses, a
    grey rib after every 2 red cells along the eave (z % 3 == 0) and a grey band after every 2 red courses; the wings
    swept up to their outer ends, stepped in across x toward a ridge at x 3, each tier's edge a red stair, the ridge
    capped by a small pale crest."""
    for z in range(SZ):                                 # the hall roof (y 6..8 over x 1..7, ridge x 4)
        for y in (6, 7, 8):
            for x in range(0, 8):
                q = (x, y, z)
                if q not in g.b or g.b[q][0] not in ROLE_OF or (6 <= z <= 14 and x <= 7):
                    continue
                paint(g, q, "grey" if rib(z) or y == 8 else "red")
    for (z0, z1) in ((0, 4), (16, 20)):                 # the wings
        for z in range(z0, z1 + 1):
            for y in range(WING_TOP + 1, H_MAX):
                reach = max(0, 3 - (y - WING_TOP - 1))
                band = (y - WING_TOP) % 3 == 0          # y 9, 12: a grey band after every 2 red courses
                for x in range(0, 7):
                    q = (x, y, z)
                    if q not in g.b or g.b[q][0] not in ROLE_OF:
                        continue
                    off = abs(x - 3)
                    if off > reach:
                        del g.b[q]
                        continue
                    if off == reach and off > 0:
                        put(g, q, *stair("red", "east" if x < 3 else "west"))
                    paint(g, q, "grey" if band else "red")
    for zr in (0, SZ - 1):
        top = max(y for y in range(H_MAX) if (3, y, zr) in g.b)
        put(g, (3, top + 1, zr), *slab("pale"))


def detail():
    """L3, round 4: the r3 massing in ref18's grid language - grey lattice, red panels, pale ledge lines, white lights."""
    g = greybox()
    for q, (n, _) in list(g.b.items()):
        if n in (WALL, HOLE):
            g.b[q] = (FAM["red"][0], {})
        elif n == SHED:
            g.b[q] = (FAM["dark"][0], {})
        elif n in PAL.values():
            paint(g, q, "red")
    tower(g)
    roofs(g)
    arcade(g)
    ends(g)
    for z in LANE_Z:
        for x in LANE_X:
            put(g, (x, 0, z), "polished_andesite" if z in (8, 12) else FAM["dark"][0])
    return g


# ============================================================================================ level 1
def level1(l3):
    """The first stage on the same footprint, taken out of L3: the arcade, hall and shed as at L3, the wings' roofs
    capped at y 8, the tower's base with its door head and first set-back ledge (to y 10), capped at y 11 by a grey
    slab course. Only cells L3 also fills."""
    g = G()
    for q, b in l3.b.items():
        x, y, z = q
        if y <= 8 and not (x in (10, 11) and z in (0, 1) and y > 6):
            g.b[q] = b
        elif y <= 10 and x <= 6 and 6 <= z <= 14:
            g.b[q] = b
    for q in list(g.b):                                      # the wings' roofs end at y 8 under a slab course
        x, y, z = q
        if y == 8 and (z <= 4 or z >= 16) and x <= 7 and (x, 9, z) in l3.b and shape_of(g.b[q][0]) == 0 \
                and g.b[q][0] in ROLE_OF:
            g.b[q] = slab("grey" if x in (1, 5) or x % 2 == 1 else "red")
    for (x, y, z) in [q for q in l3.b if q[1] == 11 and q[0] <= 6 and 6 <= q[2] <= 14]:
        g.b[(x, 11, z)] = slab("grey")
    return g


# ============================================================================================ output and checks
def write(g, out=OUT):
    h = max(y for (_, y, _) in g.b) + 1
    assert h <= H_MAX, "%s is %d tall" % (out, h)
    rig = gs._SunRig(SX, h, SZ)
    for (x, y, z), (n, props) in g.b.items():
        rig.set(x, y, z, n, **props)
    rig.bury_grass()
    rig.settle()
    os.makedirs(os.path.dirname(out), exist_ok=True)
    rig.structure().write(out)
    return rig


def check(rig, out=OUT, h_max=H_MAX):
    import audit_structure_pockets as ap
    name = os.path.splitext(os.path.basename(out))[0]
    n = {q: b[0] for q, b in rig.b.items()}
    assert rig.size[1] <= h_max, "%s is %d tall (max %d)" % (name, rig.size[1], h_max)
    assert all((x, 0, z) in n for x in range(SX) for z in range(SZ)), "a hole in the foundation"
    busy = [(x, y, z) for x in LANE_X for z in LANE_Z for y in range(1, LANE_H + 1) if (x, y, z) in n]
    assert not busy, "the exit lane is narrowed: %s" % busy[:8]
    col = [y for y in range(1, rig.size[1]) if (SPAWN[0], y, SPAWN[2]) in n]
    assert not col, "the spawn column is blocked at %s" % col
    lone = [q for q in n if q[1] > 0 and not any((q[0] + a, q[1] + b, q[2] + c) in n for a, b, c in
                                                   ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)))]
    assert not lone, "floating %s" % lone[:6]
    bad = sorted({v for v in n.values() if ("copper" in v and not v.startswith("waxed_") and v != "lightning_rod")})
    assert not bad, "unwaxed copper: %s" % bad
    rig.check_support(name)
    one_way, ground_, roof, rooms, note = ap.audit(name, out)
    assert not one_way and not ground_ and "TRAPPED" not in note, (one_way[:8], ground_[:8], note)
    return "%s %dx%dx%d: %d blocks, lane clear, spawn column open, no pockets (%d roof-only)" % (
        name, SX, rig.size[1], SZ, len(n), len(roof))


if __name__ == "__main__":
    if "--greybox" in sys.argv:
        print(check(write(greybox())))
    else:
        l3 = detail()
        r3 = write(l3, OUT_L3)
        print(check(r3, OUT_L3))
        l1 = level1(l3)
        r1 = write(l1, OUT_L1)
        print(check(r1, OUT_L1, 12))
        lost = [q for q in r1.b if q not in r3.b]
        assert not lost, "L1 cells that L3 leaves empty: %s" % lost[:8]
        print("L1 is a subset of L3 (%d of %d cells)" % (len(r1.b), len(r3.b)))
