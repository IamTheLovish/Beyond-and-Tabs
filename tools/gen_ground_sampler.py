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
"""
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
    # r2 (critic r31: "a clean staircase"): a dithered rounding - each terrace edge breaks every few blocks
    return int(math.floor(h + 0.9 * (vnoise(x, z, 4.0, 31) - 0.5) + 0.5 * hash01(x, z, 30) + 0.25))


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

    no_plants = set()
    if layered:
        # 1. the skirt
        for (x, z), h in ground.items():
            d, dx, dz = dist_to_foot(x, z)
            r = hash01(x, z, 41)
            if d == 1:
                s.set(x, h, z, "coarse_dirt" if r < 0.45 else "dirt_path" if r < 0.7 else "grass_block",
                      **({"snowy": "false"} if r >= 0.7 else {}))
                no_plants.add((x, z))
                shaded = (z < oz and side != "north") or (x > ox + bx - 1 and side != "east")
                corner = dx > 0 and dz > 0
                if shaded and not corner and hash01(x, z, 42) < 0.7:
                    s.set(x, h + 1, z, "moss_carpet")
                elif shaded and hash01(x, z, 43) < 0.25:
                    s.set(x, h + 1, z, "mossy_cobblestone_slab" if hash01(x, z, 44) < 0.5 else "cobblestone_slab",
                          type="bottom", waterlogged="false")
            elif d in (2, 3) and r < (0.60 if d == 2 else 0.25):        # r2: 60% / 25% (was 30 / 12)
                s.set(x, h, z, "coarse_dirt" if r < (0.40 if d == 2 else 0.18) else "rooted_dirt")
                no_plants.add((x, z))
        # the entrance apron and the worn path to the edge
        cx, cz = ox + bx // 2, oz + bz // 2
        step = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}[side]
        px, pz = cx, cz
        for i in range(60):
            px, pz = px + step[0], pz + step[1]
            if (px, pz) in foot:
                continue
            if not (0 <= px < N and 0 <= pz < N):
                break
            d, _, _ = dist_to_foot(px, pz)
            wob = int(round(2.5 * math.sin(i / 6.0))) if d > 4 else 0
            # r2 (critic r31: "a sticker, 3-4 wide, 30% brighter, hard edges"): the apron keeps 3 wide by the door,
            # then a 1-wide worn core with feathered sides - coarse dirt and rooted dirt, dirt path only now and then
            width = 1 if d <= 4 else 0
            for w in range(-width - 1, width + 2):
                edge = abs(w) > width
                qx = px + (w if step[0] == 0 else 0) + (wob if step[0] == 0 else 0)
                qz = pz + (w if step[1] == 0 else 0) + (wob if step[1] == 0 else 0)
                if (qx, qz) in ground and (qx, qz) not in foot:
                    rr = hash01(qx, qz, 45)
                    if edge and rr > 0.5:
                        continue
                    blk = "dirt_path" if rr < 0.25 else "coarse_dirt" if rr < 0.8 else "rooted_dirt"
                    s.set(qx, ground[(qx, qz)], qz, blk)
                    no_plants.add((qx, qz))
        # 2. the ground's layers
        for (x, z), h in ground.items():
            if (x, z) in no_plants:
                continue
            if math.hypot(x - 12, z - 12) < 5 and hash01(x, z, 46) < 0.18:   # the rise's dry crest
                s.set(x, h, z, "coarse_dirt")
                no_plants.add((x, z))
            dh = math.hypot(x - 44, z - 44)
            if dh < 3.5:
                s.set(x, h, z, "mud" if hash01(x, z, 47) < 0.5 else "moss_block")
                no_plants.add((x, z))
            elif dh < 6 and hash01(x, z, 48) < 0.5:
                s.set(x, h, z, "moss_block")
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
    trees = [(gen_trees.oak(3), 0, 40), (gen_trees.oak(11, old=True), 38, 36), (gen_trees.birch(5), 6, 2)]
    half = gen_trees.SIZE // 2
    for piece, tx, tz in trees:
        th = ground.get((tx + half, tz + half), BASE)
        gen_trees._write_piece(s, piece, tx, tz, lift=th + 1)
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                p = (tx + half + dx, tz + half + dz)
                if p in ground and math.hypot(dx, dz) <= 3.2:
                    no_plants.add(p)
                    rag = 0.95 * (1 - math.hypot(dx, dz) / 3.6) + 0.35 * (vnoise(p[0], p[1], 2.0, 55) - 0.5)
                    if layered and hash01(p[0], p[1], 51) < rag:        # podzol under the crown, ragged (r2)
                        s.set(p[0], ground[p], p[1], "podzol", snowy="false")
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
    return s, side


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, layered in (("ground_before", False), ("ground_after", True)):
        s, side = build(layered)
        s.write(os.path.join(OUT, name + ".nbt"))
        print(name, "entrance:", side)


if __name__ == "__main__":
    main()
