#!/usr/bin/env python3
"""Ground layering pilot (Itachi 1, Oct 28 2026; lovish 06:11 "we also need to layer it to add depth so the landscape
matches the intricate buildings", "lets do it" 06:20): a BEFORE / AFTER pair for the eyes `structure` lane, so the
idea is judged in game before any game code changes.

    python3 tools/gen_ground_sampler.py     # design/eyes/ground_before.nbt, design/eyes/ground_after.nbt
    gh api -X POST repos/IamTheLovish/Beyond-and-Tabs/actions/workflows/eyes.yml/dispatches -f ref=<branch> \\
        -f 'inputs[structure]=design/eyes/ground_before.nbt,design/eyes/ground_after.nbt'

Both: 56 x 56, a White Angel barracks (data/reignofnether/structures/barracks.nbt) in the middle, the meadow's plant
cover, two oaks and a birch. BEFORE is the ground as the maps build it today: flat grass to the walls.

AFTER - the plan (written before building, from design/maps-spec D1-D4 and the world-that-remembers notes):
 1. The building's foot (skirt), measured from its footprint:
    - ring 1: trodden - coarse dirt / dirt path / grass dappled (no plants: feet and carts);
    - the entrance side: a 3-wide dirt-path apron 4 blocks out, then a worn path that wanders to the sampler's edge;
    - shaded walls (north and east): moss at the wall foot, a few loose stones (cobble / mossy cobble slabs);
    - outside corners: a tuft of tall grass and ferns where nobody walks (the unmown corner);
    - ring 2-3: grass with coarse-dirt flecks fading out; plants thin out toward the building.
 2. The ground's own layers (D1): gentle relief (a 2-block rise to the north-west, a hollow to the south-east), coarse
    dirt flecks on the rise's crest (dry), moss and a damp mud edge in the hollow, podzol under each tree, a rock
    outcrop with gravel scree at its foot (D4).
Everything here is vanilla blocks, so the pilot runs without the new mods; This Rocks! / Immersive Weathering blocks
replace the stand-ins (slabs, moss carpet) once they are in the pack.

ROUND 2 (critic r33, "after" 6/10: "the dirt is one orange tone and sits as hard-edged squares; the sand path reads
like a vanilla village"). Measured first, on the preset still P_ground_after_rts (8 px tile medians; HSV / Rec.601):
  - the "sand" squares are dirt_path, not sand: hue 35 / sat 38 / luma ~175 against ~147 for the coarse dirt and dirt
    round them (same hue). Their smooth light top is what reads "village path".
  - "one orange tone" is right: every earth tile in the crop sits at hue 33-37 / sat ~40, and in our pack dirt, coarse
    dirt and podzol are within luma 83-94 / hue 27-33 of each other (resourcepacks/...: dirt 90, coarse 94, podzol 83).
    Mud is the one earth tone that differs (in game hue ~25 / sat ~19 / luma ~98, half the saturation).
  - the squares come from choosing every earth block by a per-block hash: lone blocks and stair-stepped edges.
So, one change per element (B before is untouched):
 1. Earth patches (skirt, tree litter, the dry crest, the damp hollow): each is a smooth field (distance + a 3-4 block
    value noise, never a per-block hash; the skirt runs 1-4 wide along a wall, so no frame), its blocks take 2-3 tones
    (TONES: coarse dirt / dirt where dry, podzol under crowns, mud where damp) from a low-frequency tone noise mixed
    with how deep in the patch they are, and every edge gets a 1-block feather ring where earth and grass alternate
    (ordered dither) with a gradient - ~90% at the core's own edge, 12-95% just outside it by how many of the block's
    sides touch the core - so notches fill, spurs and lone squares melt into the lawn. The hollow's moss is laid the
    same way under its mud; a tree standing in the hollow takes the damp tones.
 2. The approach track: no dirt path. Off the 3-wide door apron, a 1-wide trodden core of dirt or coarse dirt in 3-4
    block sections with a gravel fleck now and then, worn less the further it gets from the door (grass creeps into
    ~3% of the core by the door, up to ~20% at the edge); a worn shoulder on one side, by stretches (~45% by the door,
    ~10-25% far out), a rare coarse-dirt / gravel fleck on the other; the wobble starts from the apron's line and a
    wobble step is bridged, so the core never breaks at a corner.
The printed summary counts the top blocks, the lone earth squares (nothing earth in the 8 round them) and the share of
the earth/grass border in straight runs of 3+ blocks (the "hard square" edge). r1 -> r2: earth 207 -> 203 blocks,
dirt path 19 -> 0, border in straight runs 23% -> 7%, lone squares 6 -> 5.

ROUND 3 (critic r34, "after" 5/10: "the dirt wear now fragments into coarse diagonal blobs rather than A's more
continuous track... the promised relief and rock outcrop are not visible from the RTS camera (the stair-step shelves
at left are flat terraces)... no gravel/coarse-dirt gradient between dirt and lawn"). Measured first: r2's track was
one shape only to z 44 of 55 (11 rows broken: grass let into the core) and 1-2 wide; the frame's ground stood at
y 2-6 with 95 camera-facing riser faces (1.8% of the frame) and the old outcrop at the frame's top edge. The eyes RTS
camera looks north-east 64.8 degrees down at 30 px a block, so a 1-block step shows only 0.43 of a block of face and
the frame sees the band from ~15 blocks south-west of the aim to ~18 north-east of it (north-east of the building is
mostly cropped or behind it). Two changes, kept apart in the code (TRACK, RELIEF):
 1. TRACK: one shape from the door to the edge. A 5-4-3 wide apron funnels into a 2-wide trodden core (dirt, coarse
    dirt in sections) that is never broken: its centre bends slowly (under a block a row, so rows always share a
    block) and the outer lane thins only where the inner one still joins both neighbouring rows. Each side has a
    3-block fringe fading into the lawn - coarse dirt next to the core, gravel flecks further out (~60 / 30 / 10%).
    The skirt's earth patches stay off the entrance side, so nothing beside the track breaks it into blobs.
 2. RELIEF: r2's low north-west rise (2.4 blocks over 17: a 1-block step every ~6 blocks, terraces from above)
    gives way to high ground west of the building behind a scarp that faces the camera. The camera sees only a step's
    south and west faces, so slopes going down away from it (north-east, east) show no step lines: the high ground's
    back and its east end fall that way, and only the scarp's face shows - 4-5 blocks of rock in strata (tuff low,
    stone, andesite; cobble accents) under a turf lip with gravel and coarse dirt along it, its line 25 degrees off
    the diagonal so it rounds into irregular steps. By stretches it breaks into a steep slope of rock and gravel
    steps; a rock stair sits at the foot of some 1-block rock steps (a 45-degree edge), talus of gravel, cobble,
    andesite and loose slabs along the cliff's foot. A tor of jointed pillars (2-5 high) stands in front of it, ~5
    blocks off the building's west wall, with a scree fan downhill. Heights only from smooth profiles and 4-7 block
    noise. The old outcrop (north-east, at the frame's top edge) is gone; the hollow is unchanged.

ROUND 4 (lovish, Oct 28 night, approving a terraced ground from his reference: terraces held by stone retaining walls,
stone stairs climbing between them, a cliff wall behind, plants spilling over every edge - foreground, middle and
background everywhere; its foliage deep, hue ~99 / sat ~35 / V ~0.24, never lime). r3's scarp and tor give way to
TERRACES; the track, fringe, skirt and hollow patches stay. AFTER only (BEFORE is byte-identical, tests pin it):
 1. LEVELS: the lowest terrace (y 2) in the south-west, a middle one (6), the building's pad (9), an upper terrace
    behind it (12) and the cliff top at the north edge (18) - walls 4 / 3 / 3 high, the cliff 6. Each wall follows a
    stair_line: 4-10 block east-west runs (south faces) stepping south by 1-6 block jogs (west faces), never a 1-block
    staircase (a comb from the camera). The level never rises to the south or west (terrace_map makes it monotone),
    so every riser faces the RTS camera, which sees only south and west faces; nothing south-west of the pad stands
    above it, and across the track the walls run straight so a flight fits.
 2. MASONRY: each riser column is a retaining wall - dressed stone in 2.5-4 block patches of stone bricks, mossy and
    cracked bricks, rubble (cobble, mossy cobble) and andesite, moss rising from the damp foot (no per-block pepper)
    - under a cap row of polished andesite / stone bricks / smooth stone; the cliff is rock in strata (tuff, stone,
    andesite) under a mossy lip.
 3. FLIGHTS: where the track drops a level, 4-wide stone-brick stairs step down into the lower terrace on a masonry
    body (3 and 4 rows), and a 4-wide flight climbs from the pad to the upper terrace west of the building. Units
    climb only by stairs: tests flood-fill every terrace from the door and look for 1-wide slots at wall feet.
 4. GREEN: dark oak leaf bushes (persistent) along ~half the wall tops in 6-block stretches, some two high with one
    behind, spilling over the cap and down the face (2 on the cliff); vines down ~40% of the faces in 5-block
    stretches; ferns, large ferns and tall grass crowd the wall feet. The birch moves to the middle terrace.
The structure is 34 tall (was 28) for the pad's 6 extra blocks and the cliff; the eyes rig aims at its middle.
"""
import collections
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_trees  # noqa: E402
import preview_structure as PS  # noqa: E402
from gen_meadow_sampler import hash01, plant_at, vnoise  # noqa: E402
from gen_structures import Structure  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "design", "eyes")
BUILDING = os.path.join(ROOT, "bar", "src", "main", "resources", "data", "reignofnether", "structures", "barracks.nbt")
N, HGT = 56, 28
BASE = 3                              # ground surface level in the sampler (room below for the relief to go down)
EARTH = ("dirt", "coarse_dirt", "podzol", "mud", "dirt_path", "rooted_dirt", "packed_mud", "gravel")
# r2: per patch kind, its tones from the patch's heart to its rim as (upper bound of pick_tone's u, block); the last
# one is also the feather ring's earth
TONES = {
    "skirt": ((0.5, "coarse_dirt"), (9.0, "dirt")),                          # sunlit wall foot: trodden, dry
    "shade": ((0.3, "mud"), (0.55, "podzol"), (9.0, "dirt")),                # north / east wall foot: damp under moss
    "tree": ((0.45, "podzol"), (0.65, "dirt"), (9.0, "coarse_dirt")),        # leaf litter under the crown
    "crest": ((0.5, "coarse_dirt"), (9.0, "dirt")),                          # the rise's dry top
    "damp": ((0.55, "mud"), (9.0, "podzol")),                                # the hollow
}
BAYER4 = ((0, 8, 2, 10), (12, 4, 14, 6), (3, 11, 1, 9), (15, 7, 13, 5))
# r4 terraces, laid out for the eyes RTS camera (it looks north-east ~65 degrees down and sees only the south and west
# faces of a step): the ground's level never rises to the south or west, so every retaining wall and the cliff face the
# camera. Levels (top block y): the lowest terrace, the middle one, the building's pad, the cliff top behind.
HGT_AFTER = 34                        # AFTER only (BEFORE keeps HGT): the pad stands 7 over the lowest terrace
T0, T1, PAD, UP, CLIFF = 2, 6, 9, 12, 18
LEVELS = (T0, T1, PAD, UP, CLIFF)
# the walls' lines in plan: (anchor x, its z, run lengths, jog sizes, salt) for stair_line - straight east-west runs
# (south faces) broken by north-south jogs (west faces), never a 1-block staircase (a crenellated comb from the camera)
WALL_T1 = (29, 38.5, (4, 8), (3, 6), 95)      # the pad's wall: higher ground north-east of it; T1 to the south-west
WALL_T0 = (30, 49.5, (4, 8), (3, 6), 96)      # T1's wall over the lowest terrace
UP_LINE = (10, 15.5, (5, 10), (1, 3), 93)     # the upper terrace behind the pad (z under the line)
CLIFF_LINE = (10, 7.5, (5, 11), (0, 2), 92)   # the cliff's foot at the back edge
UP_FLIGHT = range(14, 18)             # the x lanes of the stairs from the pad up to the upper terrace
WALL_STONE = frozenset(("stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "cobblestone",
                        "mossy_cobblestone", "andesite", "polished_andesite", "smooth_stone"))
CAP_STONE = frozenset(("polished_andesite", "smooth_stone", "stone_bricks"))
ROCK_STONE = frozenset(("tuff", "stone", "andesite", "cobblestone", "mossy_cobblestone"))
STRATA = ((1.5, "tuff"), (3.5, "stone"), (99.0, "andesite"))
BUSH_LEAVES = ("dark_oak_leaves",)                    # deep green (the reference: hue ~99, sat ~35, V ~0.24)
FLIGHT_STAIRS = "stone_brick_stairs"


def rnoise(x, z, scale, salt, angle=22.5):
    """vnoise on a lattice turned by `angle`: a square lattice's gradients run ~2x stronger along the axes than the
    diagonals, and the 45-degree RTS camera shows that as diagonal streaks (bug hunt r34)."""
    c, s = math.cos(math.radians(angle)), math.sin(math.radians(angle))
    return vnoise(x * c - z * s + 7.3, x * s + z * c + 3.1, scale, salt)


def rock_at(x, y, z, base, top=False):
    """Cliff rock in strata by height above the cliff's foot `base` (wavy by a 6-block noise): tuff low, stone, then
    andesite; cobble in 3-block patches, mossy cobble on some tops."""
    band = y - base + 1.2 * (rnoise(x, z, 6.0, 37) - 0.5)
    blk = next(b for lim, b in STRATA if band < lim)
    if top and rnoise(x, z, 3.0, 38) > 0.55:
        return "mossy_cobblestone"
    return "cobblestone" if rnoise(x, y * 3.0 + z, 2.5, 39) > 0.72 else blk


def wall_stone(x, y, z, low):
    """A retaining wall's face: dressed stone in courses with rubble patches, moss rising from the damp foot. Patches
    from 2.5-4 block noise (never a per-block hash: pepper dots at RTS scale, ART-FAULTS)."""
    m = rnoise(x + 0.5 * y, z - 0.5 * y, 3.0, 101) + 0.35 * max(0.0, 2.0 - (y - low)) / 2.0
    r = rnoise(x - 0.7 * y, z + 0.7 * y, 4.0, 102)
    if r > 0.66:
        return "mossy_cobblestone" if m > 0.55 else "cobblestone"
    if r < 0.2:
        return "andesite"
    if m > 0.62:
        return "mossy_stone_bricks"
    return "cracked_stone_bricks" if rnoise(x, y * 2.0 + z, 2.0, 103) > 0.78 else "stone_bricks"


def bayer(x, z):
    """An ordered-dither threshold in (0, 1): at 50% neighbours alternate, where a per-block hash clumps into squares."""
    return (BAYER4[z % 4][x % 4] + 0.5) / 16.0


def tone_noise(x, z):
    """Which tone a patch block takes: 3.5- and 7-block value noise (tone areas of 3+ blocks), and a small ordered
    dither so two tones interleave for a block where they meet instead of meeting on a straight line."""
    return 0.65 * vnoise(x, z, 3.5, 71) + 0.35 * vnoise(x, z, 7.0, 72) + 0.12 * (bayer(x, z) - 0.5)


def pick_tone(kind, x, z, e):
    """A patch block's tone: u is 55% tone noise and 45% how far from the patch's heart it is (field e 1 -> 0.5), so
    each patch holds its tones in rough rings (podzol at the trunk, coarse dirt toward the drip line...) broken up by
    the noise, rather than one tone per patch."""
    u = 0.55 * tone_noise(x, z) + 0.45 * (1 - min(1.0, max(0.0, (e - 0.5) / 0.5)))
    return next(b for top, b in TONES[kind] if u < top)


def feather(field, ground, solid=frozenset()):
    """A patch from a smooth field (value > 0.5 = core): {(x, z): True (earth) / False (keep the lawn)} for the core and
    its 1-block feather ring. Core blocks are earth, thinned only on the core's own edge (a straight edge loses ~1 in
    10, a convex corner ~3 in 10); spurs and lone squares with fewer than two core sides go mostly to the lawn. Outside
    blocks touching the core are earth by the ordered dither, at a share that grows with how many of their 4 sides
    touch the core (12% corner-only, 40% on a straight edge - every other or every fourth block - and 60 / 80 / 95% in
    notches, so stair steps fill). `solid` blocks off the ground (the building's footprint, for the skirt) count as
    core, so the wall foot is not an edge."""
    core = {p for p in ground if field(*p) > 0.5}

    def is_core(q):
        return q in core or q in solid
    out = {}
    for (x, z) in ground:
        n4 = sum(is_core(q) for q in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)))
        b = bayer(x, z)
        if (x, z) in core:
            out[(x, z)] = True if n4 == 4 else b >= 0.1 if n4 == 3 else b >= 0.3 if n4 == 2 else b < 0.2 + 0.2 * n4
        elif n4 or any(is_core((x + dx, z + dz)) for dx in (-1, 1) for dz in (-1, 1)):
            out[(x, z)] = b < (0.12, 0.4, 0.6, 0.8, 0.95)[n4]
    return out


def load_building():
    r = PS.load_nbt(BUILDING)
    pal = r["palette"]
    blocks = [(tuple(b["pos"]), pal[b["state"]]["Name"].replace("minecraft:", ""), pal[b["state"]].get("Properties", {}))
              for b in r["blocks"]]
    return tuple(r["size"]), blocks


def entrance_side(size, blocks):
    """The footprint side with the most air at the first storey (y = 1): the open front."""
    sx, _, sz = size
    solid = {(x, z) for (x, y, z), n, _ in blocks if y == 1 and n != "air"}
    sides = {"north": [(x, 0) for x in range(sx)], "south": [(x, sz - 1) for x in range(sx)],
             "west": [(0, z) for z in range(sz)], "east": [(sx - 1, z) for z in range(sz)]}
    return max(sides, key=lambda k: sum(1 for p in sides[k] if p not in solid))


def track_rows(ox, oz, bx, bz, side, cells):
    """r3's track plan, before any heights: rows t = 0, 1, 2... off the entrance wall, each (centre, first core lane,
    core width) - the apron funnels 5-4-3 wide into a 2-wide core whose centre bends under 0.4 of a block a row - the
    rows whose outer lane is let go to the fringe, and row_cell(t, lane) -> (x, z)."""
    def row_cell(t, lane):
        return {"south": (lane, oz + bz + t), "north": (lane, oz - 1 - t),
                "east": (ox + bx + t, lane), "west": (ox - 1 - t, lane)}[side]
    mid = ox + bx / 2.0 if side in ("south", "north") else oz + bz / 2.0
    rows = []
    for t in range(N):
        if not any(row_cell(t, la) in cells for la in range(N)):
            break
        centre = mid + 2.6 * math.sin(max(0, t - 2) / 7.0)
        width = (5, 4, 3)[t] if t < 3 else 2
        rows.append((centre, int(math.floor(centre - width / 2.0 + 0.5)), width))
    last = len(rows) - 1
    thin = set()
    for t in range(10, last):
        centre, first, width = rows[t]
        inner = first if abs(first + 0.5 - centre) <= abs(first + 1.5 - centre) else first + 1
        pf, pw = rows[t - 1][1:]
        nf, nw = rows[t + 1][1:]
        joins = pf <= inner < pf + pw and nf <= inner < nf + nw     # the inner lane alone still meets both rows
        if joins and t - 1 not in thin and (t * 0.618034 + 0.2) % 1.0 < 0.3 * (t - 10) / max(1, last - 10):
            thin.add(t)
    return rows, thin, row_cell


def stair_line(spec):
    """A wall's line in plan, z per x from -1 to N: runs of 4-10 blocks along x, each stepping south by a jog at its
    east end (so the line never goes north eastward, and the level it bounds stays monotone), anchored to pass the
    spec's (x, z)."""
    ax, az, runs, jogs, salt = spec
    zs, z, i, x = {}, 0.0, 0, -1
    while x <= N:
        run = runs[0] + int(hash01(i, 0, salt) * (runs[1] - runs[0] + 1))
        for _ in range(run):
            zs[x] = z
            x += 1
        z += jogs[0] + int(hash01(i, 1, salt) * (jogs[1] - jogs[0] + 1))
        i += 1
    shift = az - zs[ax]
    return {k: v + shift for k, v in zs.items()}


def terrace_map(cells, foot, near_pad, rows, row_cell):
    """r4: every column's ground level (top block y) - one of LEVELS - and the row where the stairs up to the upper
    terrace start. In front, two walls on stair_lines (east-west runs with jogs south, following the camera's
    horizontal), straightened across the track (a flight needs a straight wall across it); behind the pad an upper
    terrace and the cliff band at the north edge; the building's pad (near_pad) level. Then made monotone - the level
    never rises to the south or west (a max over everything south-west of a column), so every riser faces the camera -
    and nothing south-west of the pad stands above it."""
    zone = {}                                                    # (x, z) -> (row, lateral distance from the centre)
    for t, (centre, first, width) in enumerate(rows):
        for la in range(N):
            zone[row_cell(t, la)] = (t, abs(la + 0.5 - centre))

    w1, w0, wu, wc = (stair_line(sp) for sp in (WALL_T1, WALL_T0, UP_LINE, CLIFF_LINE))
    lv = {}
    for x in range(N):
        for z in range(N):
            if (x, z) in foot or near_pad(x, z):
                lv[(x, z)] = PAD
                continue
            lx = x
            if (x, z) in zone and zone[(x, z)][1] <= 5.5:            # across the track: the line at its centre
                t = zone[(x, z)][0]
                lx = int(row_cell(t, rows[t][0])[0])
            lv[(x, z)] = T0 if z > w0[lx] else T1 if z > w1[lx] else PAD
            if z < wu[x]:
                lv[(x, z)] = UP
            if z < wc[x]:
                lv[(x, z)] = CLIFF

    def close():                                                 # monotone: max over the south-west quadrant
        for x in range(N):
            for z in range(N - 1, -1, -1):
                if x:
                    lv[(x, z)] = max(lv[(x, z)], lv[(x - 1, z)])
                if z < N - 1:
                    lv[(x, z)] = max(lv[(x, z)], lv[(x, z + 1)])
    close()
    pad = [p for p in lv if p in foot or near_pad(*p)]
    px, pz = max(p[0] for p in pad), min(p[1] for p in pad)
    for (x, z) in lv:                                            # nothing south-west of the pad above it
        if x <= px and z >= pz:
            lv[(x, z)] = min(lv[(x, z)], PAD)
    # the upper terrace's wall runs straight across the stairs' lanes (raise, then close again: still monotone)
    up_row = max(min(z for z in range(N) if lv[(x, z)] < UP) for x in UP_FLIGHT)
    for x in UP_FLIGHT:
        for z in range(up_row):
            lv[(x, z)] = max(lv[(x, z)], UP)
    close()
    return {p: v for p, v in lv.items() if p in cells}, up_row


def terraces(s, ground, rows, row_cell, side, track, no_plants, up_row):
    """r4: the terraces' masonry, flights, cliff rock, vines and leaf bushes on a ground already levelled by
    terrace_map. Returns {"levels", "flights": [stair cells per flight], "risers", "bushes", "vines"}."""
    sides = {"south": (0, 1), "west": (-1, 0)}                  # the faces the camera sees, as (dx, dz) to the lower cell
    up = {"south": "north", "north": "south", "east": "west", "west": "east"}[side]   # a flight climbs toward the door
    flights, flight_cells = [], set()

    def lay(steps, hi, facing):
        """A flight: steps[j] are the cells of step j out from the wall, its stair at hi - j on masonry."""
        cells = []
        for j, row in enumerate(steps):
            y = hi - j
            for q in row:
                if q not in ground or ground[q] >= y:
                    continue
                for yy in range(ground[q] + 1, y):               # the flight's body: masonry under each step
                    s.set(q[0], yy, q[1], wall_stone(q[0], yy, q[1], ground[q]))
                s.set(q[0], y, q[1], FLIGHT_STAIRS, facing=facing, half="bottom", shape="straight", waterlogged="false")
                cells.append((q[0], y, q[1]))
                flight_cells.add(q)
                no_plants.add(q)
        flights.append(cells)
    # down the track: where its centre lane drops a level, k stair rows step down into the lower terrace, 4 wide
    for t in range(len(rows) - 1):
        centre, first, width = rows[t]
        hi, lo = ground[row_cell(t, int(centre))], ground[row_cell(t + 1, int(centre))]
        if hi > lo:
            lanes = range(rows[t + 1][1] - 1, rows[t + 1][1] + rows[t + 1][2] + 1)
            lay([[row_cell(t + 1 + j, la) for la in lanes] for j in range(hi - lo)], hi, up)
    # from the pad up to the upper terrace, west of the building
    lay([[(x, up_row + j) for x in UP_FLIGHT] for j in range(UP - PAD)], UP, "north")
    # risers: a column standing 2+ over its south or west neighbour shows that face to the camera
    risers = {}
    for (x, z), h in ground.items():
        low = min(ground.get((x + dx, z + dz), h) for dx, dz in sides.values())
        if h - low >= 2:
            risers[(x, z)] = low
    for (x, z), low in risers.items():
        h = ground[(x, z)]
        if h == CLIFF:                                           # the cliff: rock in strata, a mossy rock lip
            for y in range(low + 1, h + 1):
                s.set(x, y, z, rock_at(x, y, z, low, top=y == h))
        else:                                                    # a retaining wall under a cap row
            for y in range(low + 1, h):
                s.set(x, y, z, wall_stone(x, y, z, low))
            c = rnoise(x, z, 4.0, 104)
            s.set(x, h, z, "polished_andesite" if c < 0.55 else "stone_bricks" if c < 0.8 else "smooth_stone")
        no_plants.add((x, z))
    # vines down the faces, by stretches; never on the track or a flight
    vines = 0
    for (x, z), low in sorted(risers.items()):
        h = ground[(x, z)]
        for face, (dx, dz) in sides.items():
            q = (x + dx, z + dz)
            if q not in ground or ground[q] > h - 2 or q in flight_cells or q in track:
                continue
            if rnoise(x, z, 5.0, 111 if face == "south" else 112) < 0.6:
                continue
            room = h - ground[q] - 1                              # air above the lower cell, up to the cap's height
            n = max(1, min(room, int(0.3 + 0.8 * room * rnoise(x, z, 2.0, 113) + hash01(x, z, 114))))
            attach = {"south": "north", "west": "east"}[face]
            props = {k: "true" if k == attach else "false" for k in ("east", "north", "south", "up", "west")}
            for y in range(h, h - n, -1):
                if (q[0], y, q[1]) not in s.blocks:
                    s.set(q[0], y, q[1], "vine", **props)
                    vines += 1
    # leaf bushes along the wall tops, spilling over the edge (dark leaves, persistent, never on the walking line)
    keep_clear = set(track) | flight_cells
    for (x, z) in list(flight_cells):
        keep_clear |= {(x + dx, z + dz) for dx in (-1, 0, 1) for dz in (-2, -1, 0, 1)}
    bushes = 0

    def leaf(x, y, z, salt):
        nonlocal bushes
        if (x, y, z) in s.blocks or not 0 <= y < s.size[1]:
            return
        s.set(x, y, z, BUSH_LEAVES[0], persistent="true", distance="7", waterlogged="false")
        bushes += 1
    for (x, z), low in sorted(risers.items()):
        h = ground[(x, z)]
        if (x, z) in keep_clear or rnoise(x, z, 6.0, 121) < 0.5:
            continue
        leaf(x, h + 1, z, 122)
        no_plants.add((x, z))
        if rnoise(x, z, 3.0, 123) > 0.55:                         # a fuller bush: a second block up, one in behind
            leaf(x, h + 2, z, 122)
            for b in ((x, z - 1), (x + 1, z)):
                if b in ground and ground[b] == h and b not in keep_clear and b not in risers:
                    leaf(b[0], h + 1, b[1], 122)
                    no_plants.add(b)
        for dx, dz in sides.values():                             # the spill: over the cap, down the face
            q = (x + dx, z + dz)
            if q not in ground or q in keep_clear or h - ground[q] < 3:
                continue
            if hash01(x, z, 124) < 0.75:
                leaf(q[0], h, q[1], 125)
                if h - ground[q] >= 5 and hash01(x, z, 126) < 0.6:  # the cliff: a longer drape
                    leaf(q[0], h - 1, q[1], 125)
    return {"levels": LEVELS, "flights": flights, "risers": risers, "bushes": bushes, "vines": vines}


def build(layered):
    s = Structure(N, HGT_AFTER if layered else HGT, N)
    (bx, by, bz), blocks = load_building()
    ox, oz = (N - bx) // 2, (N - bz) // 2
    foot = {(ox + x, oz + z) for (x, y, z), n, _ in blocks if y == 0}
    side = entrance_side((bx, by, bz), blocks)

    def dist_to_foot(x, z):
        dx = max(ox - x, 0, x - (ox + bx - 1))
        dz = max(oz - z, 0, z - (oz + bz - 1))
        return max(dx, dz), dx, dz

    cells = {(x, z) for x in range(N) for z in range(N)} - foot
    rows, thin, row_cell = track_rows(ox, oz, bx, bz, side, cells)
    if layered:
        levels, up_row = terrace_map(cells, foot, lambda x, z: dist_to_foot(x, z)[0] <= 2, rows, row_cell)
    floor = PAD if layered else BASE
    ground = {}
    for x in range(N):
        for z in range(N):
            if (x, z) in foot:
                continue
            h = levels[(x, z)] if layered else BASE
            for y in range(0, h):
                s.set(x, y, z, "dirt")
            ground[(x, z)] = h
            s.set(x, h, z, "grass_block", snowy="false")
    for (x, y, z), n, props in blocks:                          # the building, its foundation at the ground level
        if n != "air" and n != "structure_void":
            s.set(ox + x, floor + y, oz + z, n, **props)
    for (x, z) in foot:
        for y in range(0, floor):
            s.set(x, y, z, "dirt")

    # r4: AFTER's birch stands on the middle terrace in the north-west (its old spot is the cliff's edge now)
    trees = [(gen_trees.oak(3), 0, 40), (gen_trees.oak(11, old=True), 38, 36),
             (gen_trees.birch(5), -3, 12) if layered else (gen_trees.birch(5), 6, 2)]
    half = gen_trees.SIZE // 2
    trunks = [(tx + half, tz + half) for _, tx, tz in trees]
    no_plants = set()
    stats = {}
    if layered:
        # 1. earth patches (r2): smooth fields, 2-3 tones from a low-frequency noise, a feathered 1-block ring
        def shaded(x, z):                                         # the north and east wall feet, unless the door is there
            return (z < oz and side != "north") or (x > ox + bx - 1 and side != "east")

        def front(x, z):                                          # off the entrance side (r3: the track's own ground)
            return {"south": z > oz + bz - 1, "north": z < oz, "east": x > ox + bx - 1, "west": x < ox}[side]

        def skirt_field(x, z):                                    # trodden at the wall foot, thinning out by ring 4
            d, dx, dz = dist_to_foot(x, z)
            if front(x, z):                                       # r3: no patches beside the track to break it up
                return -1.0
            base = {1: 0.8, 2: 0.5, 3: 0.3, 4: 0.12}.get(d, -1.0)
            if d >= 2 and dx > 0 and dz > 0:
                base -= 0.35                                      # the unmown corner stays lawn
            return base + 1.2 * (vnoise(x, z, 4.0, 61) - 0.5)    # 1 to 4 wide along a wall, never a frame

        def ring_field(x, z, cx, cz, radius, peak, amp, salt):    # a disc with a 3-block-noise edge
            r = math.hypot(x - cx, z - cz)
            return -1.0 if r >= radius else peak * (1 - r / radius) + amp * (vnoise(x, z, 3.0, salt) - 0.5)

        def earth_kind(x, z):
            damp = ring_field(x, z, 44, 44, 4.5, 1.0, 0.6, 47)
            fields = [(skirt_field(x, z), "shade" if shaded(x, z) else "skirt"),
                      (ring_field(x, z, 14.0, 15.0, 3.5, 0.55, 0.9, 46), "crest"), (damp, "damp")]
            fields += [(ring_field(x, z, tx, tz, 3.8, 1.15, 0.6, 55), "tree") for tx, tz in trunks]
            e, kind = max(fields)
            return e, "damp" if damp >= 0.3 else kind             # a tree in the hollow stands in mud, not litter

        moss = feather(lambda x, z: ring_field(x, z, 44, 44, 8.5, 1.0, 0.6, 48), ground)   # the hollow's moss, under
        for (x, z), on in moss.items():                                                   # its mud
            if on:
                s.set(x, ground[(x, z)], z, "moss_block")
        earth = feather(lambda x, z: earth_kind(x, z)[0], ground, foot)
        for (x, z), on in earth.items():
            if on:
                e, kind = earth_kind(x, z)
                blk = pick_tone(kind, x, z, e) if e > 0.5 else TONES[kind][-1][1]
                s.set(x, ground[(x, z)], z, blk, **({"snowy": "false"} if blk == "podzol" else {}))
                no_plants.add((x, z))
        for (x, z), h in ground.items():                          # the wall foot: trodden; moss and loose stones
            d, dx, dz = dist_to_foot(x, z)
            if d != 1:
                continue
            no_plants.add((x, z))
            corner = dx > 0 and dz > 0
            if shaded(x, z) and not corner and hash01(x, z, 42) < 0.7:
                s.set(x, h + 1, z, "moss_carpet")
            elif shaded(x, z) and hash01(x, z, 43) < 0.25:
                s.set(x, h + 1, z, "mossy_cobblestone_slab" if hash01(x, z, 44) < 0.5 else "cobblestone_slab",
                      type="bottom", waterlogged="false")
        # 2. TRACK (r3; critic r34: "fragments into coarse diagonal blobs... one continuous path with a coarse-dirt
        # fringe"): rows t = 0, 1, 2... off the entrance wall; the apron funnels 5-4-3 wide into a 2-wide core that is
        # never broken, then a 3-block fringe each side, coarse dirt to gravel flecks to lawn
        track = set()
        for t, (centre, first, width) in enumerate(rows):
            lanes = list(range(first, first + width))
            if t in thin:
                outer = max(lanes, key=lambda la: abs(la + 0.5 - centre))
                lanes.remove(outer)
            for la in lanes:
                q = row_cell(t, la)
                if q not in ground:
                    continue
                if t < 3:                                         # the apron: the skirt's dry tones
                    blk = pick_tone("skirt", q[0], q[1], 0.75)
                elif abs(la + 0.5 - centre) < 0.5:                # the inner lane: dirt, coarse dirt in sections
                    blk = "coarse_dirt" if rnoise(q[0], q[1], 3.5, 82) > 0.62 else "dirt"
                else:                                             # the outer lane: dirt, coarse dirt in sections
                    blk = "coarse_dirt" if rnoise(q[0], q[1], 3.5, 83) > 0.55 else "dirt"
                s.set(q[0], ground[q], q[1], blk)
                no_plants.add(q)
                track.add(q)
            for sgn, salt in ((-1, 88), (1, 89)):                 # the fringe, each side worn by stretches
                stretch = 0.6 + 0.6 * vnoise(t, 0, 5.0, salt)
                edge = first if sgn < 0 else first + width - 1
                for d in (1, 2, 3):
                    q = row_cell(t, edge + sgn * d)
                    if q not in ground or q in track:
                        continue
                    if hash01(q[0], q[1], 84) < (0.55, 0.27, 0.09)[d - 1] * stretch:
                        gravel = hash01(q[0], q[1], 85) < (0.35, 0.6, 0.7)[d - 1]
                        s.set(q[0], ground[q], q[1], "gravel" if gravel else "coarse_dirt")
                        no_plants.add(q)
                    elif d == 1 or hash01(q[0], q[1], 86) < 0.5:  # trampled lawn: no plants
                        no_plants.add(q)
                    track.add(q)
            if t in thin:                                         # the let-go outer lane: fringe tones or lawn
                q = row_cell(t, outer)
                if q in ground and hash01(q[0], q[1], 84) < 0.7:
                    s.set(q[0], ground[q], q[1], "coarse_dirt" if hash01(q[0], q[1], 85) >= 0.3 else "gravel")
                no_plants.add(q)
                track.add(q)
        stats["track"] = (rows, thin, row_cell)

        # 3. TERRACES (r4; lovish's reference: terraces held by stone retaining walls, stone stairs between levels, a
        # cliff behind, plants spilling over every edge)
        stats["terraces"] = terraces(s, ground, rows, row_cell, side, track, no_plants, up_row)
    for piece, tx, tz in trees:
        th = ground.get((tx + half, tz + half), BASE)
        gen_trees._write_piece(s, piece, tx, tz, lift=th + 1)
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                p = (tx + half + dx, tz + half + dz)
                if p in ground and math.hypot(dx, dz) <= 3.2:
                    no_plants.add(p)
    for (x, z), h in ground.items():
        if (x, z) in no_plants or (x, z, h + 1) in s.blocks or h + 2 >= HGT:
            continue
        p = plant_at(x, z)
        if layered:
            d, dx, dz = dist_to_foot(x, z)
            if d <= 3 and not (dx > 0 and dz > 0):                      # trampled near the walls
                p = None if hash01(x, z, 52) < 0.7 else p
            if d <= 2 and dx > 0 and dz > 0 and hash01(x, z, 53) < 0.8:   # the unmown corner
                p = "tall_grass" if hash01(x, z, 54) < 0.6 else "fern"
            # r4: a wall's foot (the face north or east of the cell) is lush - ferns and tall grass against the stone
            if any(ground.get(q, -99) - h >= 2 for q in ((x, z - 1), (x + 1, z))) and hash01(x, z, 55) < 0.7:
                r = hash01(x, z, 56)
                p = "large_fern" if r < 0.3 else "tall_grass" if r < 0.55 else "fern"
        if p is None:
            continue
        if layered and p in ("tall_grass", "large_fern") and (x, h + 2, z) in s.blocks:
            p = "fern"                                            # r4: a vine or leaf hangs there
        if p in ("tall_grass", "large_fern"):
            s.set(x, h + 1, z, p, half="lower")
            s.set(x, h + 2, z, p, half="upper")
        else:
            s.set(x, h + 1, z, p)
    return s, side, ground, stats


def summary(s, ground):
    """The ground's top blocks, the earth among them, lone earth squares (no earth in the 8 round it) and the earth / lawn
    border: unit edges, and the share of them in straight runs of 3+ with the earth on the same side (hard squares)."""
    names = [n for n, _ in s.palette]
    top = {p: names[s.blocks[(p[0], h, p[1])]] for p, h in ground.items()}
    earth = {p for p, n in top.items() if n in EARTH}
    sides = ((1, 0), (-1, 0), (0, 1), (0, -1))
    lone = sum(1 for (x, z) in earth if not any((x + dx, z + dz) in earth for dx in (-1, 0, 1) for dz in (-1, 0, 1)
                                                if dx or dz))
    edges = {(x, z, dx, dz) for (x, z) in earth for dx, dz in sides
             if (x + dx, z + dz) in ground and (x + dx, z + dz) not in earth}
    hard = 0
    for (x, z, dx, dz) in edges:
        tx, tz = (0, 1) if dx else (1, 0)
        run = 1 + sum(next(k for k in range(1, N + 1) if (x + g * k * tx, z + g * k * tz, dx, dz) not in edges) - 1
                      for g in (1, -1))
        hard += run >= 3
    tops = collections.Counter(top.values())
    tones = ", ".join(f"{n} {c}" for n, c in tops.most_common() if n in EARTH)
    return (f"top: {', '.join(f'{n} {c}' for n, c in tops.most_common() if n not in EARTH)}; earth {len(earth)} "
            f"({tones or 'none'}); lone earth squares {lone}; earth/lawn border {len(edges)} edges, "
            f"{100 * hard / max(1, len(edges)):.0f}% in straight runs of 3+")


def track_summary(s, ground, stats):
    """r3: the track as one shape - rows from the wall to the sampler's edge, the core's width per row, rows where the
    core is not all earth or shares no block with the next row (a break), and the fringe's earth share 1-3 out."""
    rows, thin, row_cell = stats["track"]
    names = [n for n, _ in s.palette]

    def top(q):
        return names[s.blocks[(q[0], ground[q], q[1])]] if q in ground else None
    cores, breaks, heads = [], 0, 0
    for t, (centre, first, width) in enumerate(rows):
        lanes = [la for la in range(first, first + width)
                 if not (t in thin and la == max(range(first, first + width), key=lambda v: abs(v + 0.5 - centre)))]
        cores.append(lanes)
        tops = [top(row_cell(t, la)) for la in lanes if row_cell(t, la) in ground]
        if tops and all(n in EARTH or n in CAP_STONE for n in tops) and any(n in CAP_STONE for n in tops):
            heads += 1                                            # r4: the cap row at a flight's head
        elif any(n not in EARTH for n in tops):
            breaks += 1
        elif t and not set(lanes) & set(cores[t - 1]):
            breaks += 1
    fr = [[0, 0] for _ in range(3)]
    for t, lanes in enumerate(cores):
        if t < 3:
            continue
        for sgn in (-1, 1):
            edge = min(lanes) if sgn < 0 else max(lanes)
            for d in (1, 2, 3):
                q = row_cell(t, edge + sgn * d)
                if q in ground:
                    fr[d - 1][0] += top(q) in EARTH
                    fr[d - 1][1] += 1
    widths = collections.Counter(len(la) for la in cores[3:])
    return (f"track: {len(rows)} rows wall to edge, core widths {dict(sorted(widths.items()))} (apron 5-4-3), "
            f"{breaks} rows broken, {heads} flight heads (cap stone); fringe earth 1 / 2 / 3 out: "
            + " / ".join(f"{100 * a / max(1, b):.0f}%" for a, b in fr))


def terrace_summary(ground, stats):
    """r4: cells per level, risers (wall / cliff columns), the flights and their steps, leaves and vines."""
    tr = stats["terraces"]
    per = collections.Counter(ground.values())
    walls = sum(1 for p in tr["risers"] if ground[p] != CLIFF)
    return (f"terraces: cells per level {', '.join(f'y {h}: {per[h]}' for h in tr['levels'])}; riser columns "
            f"{walls} wall + {len(tr['risers']) - walls} cliff; flights {len(tr['flights'])} "
            f"({', '.join(str(len(f)) for f in tr['flights'])} stairs); bush leaves {tr['bushes']}, vines {tr['vines']}")


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, layered in (("ground_before", False), ("ground_after", True)):
        s, side, ground, stats = build(layered)
        s.write(os.path.join(OUT, name + ".nbt"))
        print(name, "entrance:", side)
        print("  ", summary(s, ground))
        if stats:
            print("  ", track_summary(s, ground, stats))
            print("  ", terrace_summary(ground, stats))


if __name__ == "__main__":
    main()
