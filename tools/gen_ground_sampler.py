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
# r3 relief, laid out for the eyes RTS camera (it looks north-east, ~65 degrees down, and sees only the south and west
# faces of a step): a scarp facing south-south-west - its line through SCARP's point, heading SCARP's angle from east
# toward south (25: off the diagonal, so it rounds into irregular steps, not a regular crenellation), wandering
# +-1.2 blocks - with high ground behind it, SCARP_H blocks at the edge falling to the plain over SCARP_BACK blocks,
# running from the sampler's west edge to x SCARP_END, where it slopes down eastward short of the building. Both those
# slopes go down away from the camera (north-north-east, east), so their steps' faces are hidden and they show no
# terrace lines - only the scarp's face does. The tor stands in front of it (jointed pillars: x, z, half-length,
# half-width, angle, height); the stone strata from the plain up.
SCARP, SCARP_H, SCARP_BACK, SCARP_END = (13.9, 19.5, 25.0), 5.0, 11.0, 20.5
TOR = ((16.2, 28.8, 2.2, 1.5, 40.0, 5.0), (18.4, 30.1, 1.6, 1.2, 40.0, 3.6), (14.3, 30.6, 1.3, 1.0, -15.0, 2.4))
STRATA = ((1.5, "tuff"), (3.0, "stone"), (99.0, "andesite"))
STAIRS = {"tuff": "cobblestone_stairs", "stone": "stone_stairs", "andesite": "andesite_stairs",
          "cobblestone": "cobblestone_stairs", "mossy_cobblestone": "mossy_cobblestone_stairs"}


def rnoise(x, z, scale, salt, angle=22.5):
    """vnoise on a lattice turned by `angle`: a square lattice's gradients run ~2x stronger along the axes than the
    diagonals, and the 45-degree RTS camera shows that as diagonal streaks (bug hunt r34)."""
    c, s = math.cos(math.radians(angle)), math.sin(math.radians(angle))
    return vnoise(x * c - z * s + 7.3, x * s + z * c + 3.1, scale, salt)


def scarp_front(x, z):
    """Blocks in front of the scarp's line (toward the camera, south-south-west; negative = behind it), the line
    wandering by a 7-block noise."""
    px, pz, ang = SCARP
    c, s = math.cos(math.radians(ang)), math.sin(math.radians(ang))
    return (x + 0.5 - px) * -s + (z + 0.5 - pz) * c - 2.4 * (rnoise(x, z, 7.0, 33) - 0.5)


def scarp_height(x, z, d_foot):
    """The high ground's smooth height above the plain (0 = none). Behind the scarp line it falls from SCARP_H to 0
    over SCARP_BACK blocks; in front it drops within a quarter block (a clean cliff: 0.6 left a mid-height step in
    front of half the edge, a crenellation) or, by 6-block stretches, over up to 3.25 (a steep broken slope). It slopes down eastward to nothing at x SCARP_END (a slope the camera sees no step faces
    of - r3's first taper ran south-east and showed its steps as terrace lines) and stays 3+ blocks off the building.
    6- and 7-block noise only - no per-block noise in the heights."""
    w = scarp_front(x, z)
    if w <= 0:
        f = max(0.0, 1 + w / SCARP_BACK) ** 0.7
    else:
        front = 0.25 + 3.0 * max(0.0, rnoise(x, z, 6.0, 35) - 0.55) / 0.45
        f = max(0.0, 1 - w / front)
    end = min(1.0, max(0.0, (SCARP_END - x - 0.5) / 3.5))
    near = min(1.0, max(0.0, (d_foot - 3) / 4.0))
    return SCARP_H * f * end * near


def tor_height(x, z):
    """The tor's height above the ground under it (0 = none): the highest of its jointed pillars, each a flat top over
    the inner 72% of an ellipse and a ledge at 55% round it, the top uneven by a 3-block noise - crags of different
    heights with sheer sides (r3's first try, a dome, read as a ziggurat)."""
    best = 0.0
    for bx, bz, half_l, half_w, ang, top in TOR:
        c, s = math.cos(math.radians(ang)), math.sin(math.radians(ang))
        lx, lz = x + 0.5 - bx, z + 0.5 - bz
        r = math.hypot((lx * c + lz * s) / half_l, (-lx * s + lz * c) / half_w)
        if r < 1:
            best = max(best, (top if r < 0.72 else 0.55 * top) + 1.0 * (rnoise(x, z, 3.0, 34) - 0.5))
    return int(best) if best >= 1 else 0


def rock_at(x, y, z, top=False):
    """Stone in strata by height above the plain (wavy by a 6-block noise): tuff low, stone, then andesite; a cobble
    block now and then, mossy cobble on some tops."""
    band = y - BASE + 1.2 * (rnoise(x, z, 6.0, 37) - 0.5)
    blk = next(b for lim, b in STRATA if band < lim)
    if top and rnoise(x, z, 3.0, 38) > 0.6:
        return "mossy_cobblestone"
    return "cobblestone" if hash01(x, y * 31 + z, 39) < 0.14 else blk


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


def height_at(x, z, layered, d_foot=99):
    """Ground surface y. AFTER: high ground behind a scarp west of the building (r3), a hollow to the south-east, flat
    round the building (d_foot: blocks to the building's footprint)."""
    if not layered:
        return BASE
    k = scarp_height(x, z, d_foot)
    if k > 0.15:
        return BASE + int(round(k + 0.6 * (rnoise(x, z, 4.0, 31) - 0.5)))
    d_hollow = math.hypot(x - 44, z - 44)
    h = BASE - 1.6 * max(0.0, 1 - d_hollow / 9.0) ** 1.2
    # r2b (critic r31: "a clean staircase"): wavy terrace edges from a 3-block noise, only on the relief itself - r2's
    # per-block dither pitted the whole flat field with 1-block holes and bumps (eyes run 40739254107)
    if abs(h - BASE) < 0.15:
        return BASE
    return int(round(h + 0.7 * (vnoise(x, z, 3.0, 31) - 0.5)))


def build(layered):
    s = Structure(N, HGT, N)
    (bx, by, bz), blocks = load_building()
    ox, oz = (N - bx) // 2, (N - bz) // 2
    foot = {(ox + x, oz + z) for (x, y, z), n, _ in blocks if y == 0}
    side = entrance_side((bx, by, bz), blocks)

    def dist_to_foot(x, z):
        dx = max(ox - x, 0, x - (ox + bx - 1))
        dz = max(oz - z, 0, z - (oz + bz - 1))
        return max(dx, dz), dx, dz

    ground = {}
    for x in range(N):
        for z in range(N):
            if (x, z) in foot:
                continue
            h = height_at(x, z, layered, dist_to_foot(x, z)[0])
            for y in range(0, h):
                s.set(x, y, z, "dirt")
            ground[(x, z)] = h
            s.set(x, h, z, "grass_block", snowy="false")
    for (x, y, z), n, props in blocks:                          # the building, its foundation at the ground level
        if n != "air" and n != "structure_void":
            s.set(ox + x, BASE + y, oz + z, n, **props)
    for (x, z) in foot:
        for y in range(0, BASE):
            s.set(x, y, z, "dirt")

    trees = [(gen_trees.oak(3), 0, 40), (gen_trees.oak(11, old=True), 38, 36), (gen_trees.birch(5), 6, 2)]
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
        def row_cell(t, lane):                                    # row t off the wall, lateral block `lane`
            return {"south": (lane, oz + bz + t), "north": (lane, oz - 1 - t),
                    "east": (ox + bx + t, lane), "west": (ox - 1 - t, lane)}[side]
        mid = ox + bx / 2.0 if side in ("south", "north") else oz + bz / 2.0
        rows = []                                                 # per row: the centre, the core lanes (first, width)
        for t in range(N):
            if not any(row_cell(t, la) in ground for la in range(N)):
                break
            centre = mid + 2.6 * math.sin(max(0, t - 2) / 7.0)  # a slow bend: under 0.4 of a block a row
            width = (5, 4, 3)[t] if t < 3 else 2
            rows.append((centre, int(math.floor(centre - width / 2.0 + 0.5)), width))
        last = len(rows) - 1
        thin = set()                                              # rows whose outer lane is let go to the fringe
        for t in range(10, last):
            centre, first, width = rows[t]
            inner = first if abs(first + 0.5 - centre) <= abs(first + 1.5 - centre) else first + 1
            pf, pw = rows[t - 1][1:]
            nf, nw = rows[t + 1][1:]
            joins = pf <= inner < pf + pw and nf <= inner < nf + nw     # the inner lane alone still meets both rows
            if joins and t - 1 not in thin and (t * 0.618034 + 0.2) % 1.0 < 0.3 * (t - 10) / max(1, last - 10):
                thin.add(t)
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

        # 3. RELIEF (r3; critic r34: "the stair-step shelves at left are flat terraces... rock outcrop not visible from
        # the RTS camera"): the scarp shows rock in strata under a turf lip, its broken stretches gravel and rock steps
        # with a stair at their foot, talus along its foot; then the tor and its scree fan
        def nbrs(x, z):
            return [q for q in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)) if q in ground]
        surf = dict(ground)                                       # the top y per column, the tor included
        rocky, talus = set(), set()
        for (x, z), h in ground.items():
            if h <= BASE or (x, z) in track:
                continue
            low = min(ground[q] for q in nbrs(x, z))
            if h - low >= 2:                                      # the cliff: rock under a turf (or dry) lip
                for y in range(low + 1, h):
                    s.set(x, y, z, rock_at(x, y, z))
                if rnoise(x, z, 3.0, 40) > 0.62:
                    s.set(x, h, z, "coarse_dirt" if hash01(x, z, 41) < 0.6 else "gravel")
                    no_plants.add((x, z))
                rocky.add((x, z))
                talus |= {q for q in nbrs(x, z) if ground[q] <= h - 2}
            elif (h - low == 1 and rnoise(x, z, 6.0, 35) > 0.55 and abs(scarp_front(x, z)) < 2.5
                  and any(ground[q] < h for q in ((x, z + 1), (x - 1, z)) if q in ground)):
                # a broken stretch of the scarp (a face the camera sees): rock steps and gravel
                if rnoise(x, z, 3.0, 42) > 0.45:
                    s.set(x, h, z, rock_at(x, h, z, top=True))
                    rocky.add((x, z))
                else:
                    s.set(x, h, z, "gravel" if hash01(x, z, 43) < 0.5 else "coarse_dirt")
                no_plants.add((x, z))
        tor = {}
        for (x, z), h in ground.items():
            t = tor_height(x, z)
            if t and (x, z) not in track:
                for y in range(h, h + t + 1):
                    s.set(x, y, z, rock_at(x, y, z, top=y == h + t))
                tor[(x, z)] = t
                surf[(x, z)] = h + t
                rocky.add((x, z))
        no_plants |= rocky
        stairs = slabs = 0
        for (x, z) in sorted(rocky):                              # a rock stair on a 1-block step's foot: a 45-degree
            for q in nbrs(x, z):                                  # edge (on the tor's ledges too)
                if q in track or surf[q] != surf[(x, z)] - 1 or (q[0], surf[q] + 1, q[1]) in s.blocks:
                    continue
                if (x, z) in tor or ground[(x, z)] - ground[q] == 1:
                    if bayer(*q) < 0.45:
                        facing = {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}[
                            (x - q[0], z - q[1])]
                        base = rock_at(x, surf[q] + 1, z)
                        s.set(q[0], surf[q] + 1, q[1], STAIRS.get(base, "stone_stairs"), facing=facing,
                              half="bottom", shape="straight", waterlogged="false")
                        no_plants.add(q)
                        stairs += 1
        for q in sorted(talus - rocky - track):                   # talus along the cliff's foot
            x, z = q
            h = ground[q]
            r = hash01(x, z, 44)
            if r < 0.7:
                s.set(x, h, z, "gravel" if r < 0.3 else "cobblestone" if r < 0.42 else "andesite" if r < 0.52
                      else "coarse_dirt")
            if (x, h + 1, z) not in s.blocks and hash01(x, z, 45) < 0.3:
                s.set(x, h + 1, z, "cobblestone_slab" if hash01(x, z, 46) < 0.5 else "andesite_slab", type="bottom",
                      waterlogged="false")
                slabs += 1
            no_plants.add(q)
        if tor:                                                   # the scree: a fan downhill (south) of the tor
            tor_z = sum(b for a, b in tor) / len(tor)
            for (x, z), h in ground.items():
                if (x, z) in rocky or (x, z) in track or (x, z) in talus or dist_to_foot(x, z)[0] <= 2:
                    continue
                d = min(math.hypot(x - a, z - b) for a, b in tor)
                if d > 3.6:
                    continue
                p = max(0.0, 0.8 - 0.22 * d) * (1.25 if z >= tor_z else 0.5)   # more of it downhill (south)
                if hash01(x, z, 50) >= p:
                    continue
                r = hash01(x, z, 51)
                s.set(x, h, z, "gravel" if r < 0.4 else "cobblestone" if r < 0.55 else "andesite" if r < 0.68
                      else "coarse_dirt")
                no_plants.add((x, z))
                if d <= 2.3 and (x, h + 1, z) not in s.blocks and hash01(x, z, 52) < 0.35:   # a loose slab of rock
                    r = hash01(x, z, 53)
                    s.set(x, h + 1, z, "cobblestone_slab" if r < 0.35 else "andesite_slab" if r < 0.6 else
                          "mossy_cobblestone_slab" if r < 0.8 else "stone_slab", type="bottom", waterlogged="false")
                    slabs += 1
        stats["relief"] = (rocky, tor, stairs, slabs, surf)
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
        if p is None:
            continue
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
    cores, breaks = [], 0
    for t, (centre, first, width) in enumerate(rows):
        lanes = [la for la in range(first, first + width)
                 if not (t in thin and la == max(range(first, first + width), key=lambda v: abs(v + 0.5 - centre)))]
        cores.append(lanes)
        if any(top(row_cell(t, la)) not in EARTH for la in lanes if row_cell(t, la) in ground):
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
            f"{breaks} rows broken; fringe earth 1 / 2 / 3 out: "
            + " / ".join(f"{100 * a / max(1, b):.0f}%" for a, b in fr))


def relief_summary(ground, stats):
    """r3: the high ground's height, the rock-stepped cells, the tor's size and height, the stairs and loose slabs."""
    rocky, tor, stairs, slabs, surf = stats["relief"]
    high = [h for h in ground.values() if h > BASE]
    return (f"relief: high ground y {BASE + 1}-{max(high)} over {len(high)} cells (plain {BASE}); rock-stepped cells "
            f"{len(rocky) - len(tor)}; tor {len(tor)} cells, {max(tor.values(), default=0)} high (top y "
            f"{max((surf[p] for p in tor), default=BASE)}); rock stairs {stairs}, loose slabs {slabs}")


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, layered in (("ground_before", False), ("ground_after", True)):
        s, side, ground, stats = build(layered)
        s.write(os.path.join(OUT, name + ".nbt"))
        print(name, "entrance:", side)
        print("  ", summary(s, ground))
        if stats:
            print("  ", track_summary(s, ground, stats))
            print("  ", relief_summary(ground, stats))


if __name__ == "__main__":
    main()
