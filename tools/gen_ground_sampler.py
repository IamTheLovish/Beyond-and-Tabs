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


def height_at(x, z, layered):
    """Ground surface y. AFTER: a rise to the north-west and a hollow to the south-east, flat round the building."""
    if not layered:
        return BASE
    d_rise = math.hypot(x - 12, z - 12)
    d_hollow = math.hypot(x - 44, z - 44)
    # broad and low: a 1-block step every ~6 blocks reads as a slope from the RTS camera, not a platform
    h = BASE + 2.4 * max(0.0, 1 - d_rise / 17.0) ** 1.2 - 1.6 * max(0.0, 1 - d_hollow / 9.0) ** 1.2
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
    ground = {}
    for x in range(N):
        for z in range(N):
            if (x, z) in foot:
                continue
            h = height_at(x, z, layered)
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

    def dist_to_foot(x, z):
        dx = max(ox - x, 0, x - (ox + bx - 1))
        dz = max(oz - z, 0, z - (oz + bz - 1))
        return max(dx, dz), dx, dz

    trees = [(gen_trees.oak(3), 0, 40), (gen_trees.oak(11, old=True), 38, 36), (gen_trees.birch(5), 6, 2)]
    half = gen_trees.SIZE // 2
    trunks = [(tx + half, tz + half) for _, tx, tz in trees]
    no_plants = set()
    if layered:
        # 1. earth patches (r2): smooth fields, 2-3 tones from a low-frequency noise, a feathered 1-block ring
        def shaded(x, z):                                         # the north and east wall feet, unless the door is there
            return (z < oz and side != "north") or (x > ox + bx - 1 and side != "east")

        def skirt_field(x, z):                                    # trodden at the wall foot, thinning out by ring 4
            d, dx, dz = dist_to_foot(x, z)
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
                      (ring_field(x, z, 12, 12, 6.0, 0.55, 0.9, 46), "crest"), (damp, "damp")]
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
        # 2. the approach track (r2): the door apron, then a trodden core 1-2 wide that wears less the further it goes
        cx, cz = ox + bx // 2, oz + bz // 2
        step = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}[side]
        lat = (1, 0) if step[0] == 0 else (0, 1)
        px, pz, prev, i0 = cx, cz, None, None
        for i in range(60):
            px, pz = px + step[0], pz + step[1]
            if (px, pz) in foot:
                continue
            if not (0 <= px < N and 0 <= pz < N):
                break
            apron = dist_to_foot(px, pz)[0] <= 4
            if not apron and i0 is None:
                i0 = i
            k = 0 if apron else i - i0                            # steps out from the apron
            wob = int(round(2.5 * math.sin(k / 6.0)))             # from 0, one block at a time: no jump off the apron
            # wear: 1 on the apron; out on the track ~0.97 falling to ~0.8 at the sampler's edge, in 4-block sections
            wear = 1.0 if apron else max(0.75, min(1.0, 0.97 - 0.15 * k / 25.0 + 0.2 * (vnoise(k, 0, 4.0, 81) - 0.5)))
            if apron:
                lanes = {w: "core" if abs(w) <= 1 else "edge" for w in range(-2, 3)}
            else:
                shoulder = 1 if vnoise(k, 0, 5.0, 86) > 0.5 else -1     # the worn shoulder's side, by stretches
                lanes = {wob - shoulder: "fleck", wob + shoulder: "shoulder", wob: "core"}
                if prev is not None and prev != wob:
                    lanes[prev] = "core"                          # bridge a wobble step: no corner-to-corner break
                prev = wob
            for off, role in sorted(lanes.items()):
                q = (px + lat[0] * off, pz + lat[1] * off)
                if q not in ground:
                    continue
                blk = None
                if role == "core":
                    no_plants.add(q)                              # trampled: no plants on the core either way
                    if apron:
                        blk = pick_tone("skirt", q[0], q[1], 0.75)
                    elif (k * 0.618034 + 0.3) % 1.0 < wear:       # a 1-D ordered dither: grass creeps in, spread out
                        blk = ("gravel" if hash01(q[0], q[1], 83) < 0.07 else
                               "dirt" if vnoise(k, 0, 3.5, 82) < 0.5 else "coarse_dirt")
                elif role == "edge":
                    blk = TONES["skirt"][-1][1] if bayer(*q) < 0.4 else None
                elif role == "shoulder":                          # ~45% by the apron, ~10-25% far out
                    blk = ("coarse_dirt" if hash01(q[0], q[1], 87) < 0.6 else "dirt") \
                        if (k * 0.618034 + 0.75) % 1.0 < 1.6 * (wear - 0.7) else None
                elif hash01(q[0], q[1], 84) < 0.08:               # a fleck in the lawn
                    blk = "gravel" if hash01(q[0], q[1], 85) < 0.4 else "coarse_dirt"
                if blk:
                    s.set(q[0], ground[q], q[1], blk)
                    no_plants.add(q)
        for (rx, rz) in ((40, 14),):                                   # a rock outcrop with gravel scree
            for (x, z), h in ground.items():
                d = math.hypot(x - rx, z - rz)
                if d < 2.3:
                    for y in range(h + 1, h + 1 + (3 if d < 1.2 else 2 if d < 1.9 else 1)):
                        s.set(x, y, z, "stone" if hash01(x, y + z, 49) < 0.6 else "andesite")
                    no_plants.add((x, z))
                elif d < 4.2 and hash01(x, z, 50) < 0.55:
                    s.set(x, h, z, "gravel")
                    no_plants.add((x, z))
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
    return s, side, ground


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


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, layered in (("ground_before", False), ("ground_after", True)):
        s, side, ground = build(layered)
        s.write(os.path.join(OUT, name + ".nbt"))
        print(name, "entrance:", side)
        print("  ", summary(s, ground))


if __name__ == "__main__":
    main()
