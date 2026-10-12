#!/usr/bin/env python3
"""Generates the building-level structures (design/building_upgrades.md): each T1 army lab's Level 2 and Level 3
(<lab>_l2.nbt, <lab>_l3.nbt) and each faction's lab annex (lab_annex<variant>.nbt). Run from the repo root; writes
into BOTH structure trees (assets/ for the client, data/ for the server), like tools/gen_structures.py.

Levels are grown on the lab's own base structure, never redrawn:
- the footprint (x, z size) never changes - an in-place upgrade must not collide with its neighbours;
- every base block is kept (so the level is a superset: the upgrade only ever ADDS blocks, which the placement
  queues and raises on top of the standing lab);
- L2 grows a crown: corner pillars with lights on the roof's four extreme columns, battlements every other block
  round the roof's edge and an accent block at the roof's centre;
- L3 grows the pillars taller and adds a central spire with a faction tip, so an L3 lab reads from across the map.
Additions only go on top of existing roof columns (at least 3 blocks up), where no ground unit can stand, so they
never make a pocket (check with tools/audit_structure_pockets.py; the GameTest is the truth).

The Graveyard's levels grow on the Overflowing Graveyard (a levelled graveyard overflows too).
Base blocks' block-entity NBT is dropped (the only one, the Barracks' bell, needs none: the game makes a fresh one).
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from preview_structure import load_nbt   # noqa: E402
from gen_structures import Structure, OUT, OUT_DATA   # noqa: E402

BASE_DIR = "bar/src/main/resources/data/reignofnether/structures"

# lab structure name -> (base structure it grows on, faction palette key)
LABS = {
    "barracks": ("barracks", "sun"),
    "graveyard": ("overflowing_graveyard", "grave"),
    "portal_military": ("portal_military", "horde"),
    "grove": ("grove", "verdant"),
    "slipway": ("slipway", "tide"),
    "lotus_academy": ("lotus_academy", "storm"),
    "machine_shop": ("machine_shop", "brass"),
    "assembly_works": ("assembly_works", "brass"),   # the Brassworks T2 lab takes the same levels (slice 2)
    "great_forge": ("great_forge", "brass"),         # and the Brassworks T3 lab (slice 4)
}

# pillar, battlement, roof-centre accent, pillar-top light, spire body, spire tip (each a (name, props) pair)
def b(name, **props):
    return (name, props)

PALETTES = {
    "sun": dict(pillar=b("quartz_pillar", axis="y"), crenel=b("smooth_quartz"), accent=b("gold_block"),
                light=b("sea_lantern"), spire=b("quartz_pillar", axis="y"), tip=b("end_rod", facing="up"),
                spire_cap=b("gold_block")),
    "grave": dict(pillar=b("bone_block", axis="y"), crenel=b("polished_blackstone_bricks"), accent=b("crying_obsidian"),
                  light=b("soul_lantern", hanging="false"), spire=b("polished_basalt", axis="y"),
                  tip=b("soul_lantern", hanging="false"), spire_cap=b("crying_obsidian")),
    "horde": dict(pillar=b("chiseled_polished_blackstone"), crenel=b("nether_bricks"), accent=b("gilded_blackstone"),
                  light=b("shroomlight"), spire=b("crimson_stem", axis="y"), tip=b("campfire", lit="true", facing="north"),
                  spire_cap=b("magma_block")),
    "verdant": dict(pillar=b("oak_log", axis="y"), crenel=b("azalea_leaves", persistent="true"),
                    accent=b("moss_block"), light=b("verdant_froglight", axis="y"), spire=b("oak_log", axis="y"),
                    tip=b("flowering_azalea_leaves", persistent="true"), spire_cap=b("verdant_froglight", axis="y")),
    "tide": dict(pillar=b("prismarine_bricks"), crenel=b("dark_prismarine_slab", type="bottom"), accent=b("dark_prismarine"),
                 light=b("sea_lantern"), spire=b("prismarine_wall"), tip=b("sea_lantern"), spire_cap=b("dark_prismarine")),
    "storm": dict(pillar=b("red_terracotta"), crenel=b("spruce_slab", type="bottom"), accent=b("gold_block"),
                  light=b("ochre_froglight", axis="y"), spire=b("red_terracotta"), tip=b("lightning_rod", facing="up"),
                  spire_cap=b("gold_block")),
    "brass": dict(pillar=b("waxed_cut_copper"), crenel=b("deepslate_brick_slab", type="bottom"), accent=b("gold_block"),
                  light=b("glowstone"), spire=b("waxed_copper_block"), tip=b("campfire", lit="true", facing="north"),
                  spire_cap=b("deepslate_bricks")),
}

ROOF_MIN = 3   # a roof column's top is at least this high (y=0 is the foundation layer in the ground)


def load_base(name):
    nbt = load_nbt(os.path.join(BASE_DIR, name + ".nbt"))
    pal = nbt["palette"]
    blocks = {}
    for blk in nbt["blocks"]:
        st = pal[blk["state"]]
        nm = st["Name"]
        props = dict(st.get("Properties", {}))
        blocks[tuple(blk["pos"])] = (nm, props)
    return list(nbt["size"]), blocks


def is_air(nm):
    return nm.split(":")[-1] in ("air", "cave_air", "void_air", "structure_void")


def grow(base_name, pal, level):
    (sx, sy, sz), blocks = load_base(base_name)
    top = {}
    for (x, y, z), (nm, _) in blocks.items():
        if not is_air(nm):
            top[(x, z)] = max(top.get((x, z), -1), y)
    roof = {c for c, t in top.items() if t >= ROOF_MIN}
    if len(roof) < 4:
        roof = {c for c, t in top.items() if t >= ROOF_MIN - 1}
    add = {}

    def put(x, y, z, blk):
        if (x, y, z) in blocks and not is_air(blocks[(x, y, z)][0]):
            return
        if (x, y, z) in add:
            return
        add[(x, y, z)] = blk

    corners = []
    for key in (lambda c: c[0] + c[1], lambda c: -(c[0] + c[1]), lambda c: c[0] - c[1], lambda c: c[1] - c[0]):
        c = min(roof, key=lambda c: (key(c), c))
        if c not in corners:
            corners.append(c)
    pillar_h = 3 if level == 2 else 5
    for (x, z) in corners:
        t = top[(x, z)]
        for y in range(t + 1, t + 1 + pillar_h):
            put(x, y, z, pal["pillar"])
        put(x, t + 1 + pillar_h, z, pal["light"])
    # battlements: every other roof column on the roof's edge
    for (x, z) in sorted(roof):
        edge = any((x + dx, z + dz) not in roof for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge and (x + z) % 2 == 0 and (x, z) not in corners:
            put(x, top[(x, z)] + 1, z, pal["crenel"])
    # the roof's centre column
    cx, cz = (sx - 1) / 2, (sz - 1) / 2
    centre = min(roof, key=lambda c: ((c[0] - cx) ** 2 + (c[1] - cz) ** 2, c))
    t = top[centre]
    if level == 2:
        put(centre[0], t + 1, centre[1], pal["accent"])
    else:
        h = max(top.values()) + 4
        for y in range(t + 1, h):
            put(centre[0], y, centre[1], pal["spire"])
        put(centre[0], h, centre[1], pal["spire_cap"])
        put(centre[0], h + 1, centre[1], pal["tip"])
    height = max([sy] + [y + 1 for (_, y, _) in add])
    s = Structure(sx, height, sz)
    for (x, y, z), (nm, props) in sorted(blocks.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
        s.set(x, y, z, nm, **props)
    for (x, y, z), (nm, props) in sorted(add.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
        s.set(x, y, z, nm, **props)
    return s


# ---- annexes: a 3x3 solid core two blocks tall (nothing to stand in or on), then the faction's machinery ----
ANNEXES = {
    "": dict(pad=b("smooth_stone"), wall=b("quartz_bricks"), corner=b("quartz_pillar", axis="y"),
             accent=b("gold_block"), light=b("lantern", hanging="false"), top=b("end_rod", facing="up"),
             decor=b("smithing_table")),
    "_dark": dict(pad=b("polished_deepslate"), wall=b("bone_block", axis="y"), corner=b("polished_basalt", axis="y"),
                  accent=b("crying_obsidian"), light=b("soul_lantern", hanging="false"), top=b("soul_campfire", lit="true", facing="north"),
                  decor=b("loom", facing="north")),
    "_nether": dict(pad=b("blackstone"), wall=b("nether_bricks"), corner=b("basalt", axis="y"),
                    accent=b("magma_block"), light=b("shroomlight"), top=b("campfire", lit="true", facing="north"),
                    decor=b("blast_furnace", facing="north", lit="false")),
    "_verdant": dict(pad=b("moss_block"), wall=b("oak_wood", axis="y"), corner=b("oak_log", axis="y"),
                     accent=b("verdant_froglight", axis="y"), light=b("flowering_azalea_leaves", persistent="true"),
                     top=b("azalea_leaves", persistent="true"), decor=b("beehive", facing="north", honey_level="0")),
    "_tide": dict(pad=b("sandstone"), wall=b("prismarine_bricks"), corner=b("dark_prismarine"),
                  accent=b("sea_lantern"), light=b("lantern", hanging="false"), top=b("lightning_rod", facing="up"),
                  decor=b("loom", facing="north")),
    "_storm": dict(pad=b("stone_bricks"), wall=b("smooth_sandstone"), corner=b("red_terracotta"),
                   accent=b("gold_block"), light=b("lantern", hanging="false"), top=b("lightning_rod", facing="up"),
                   decor=b("lodestone")),
    "_brass": dict(pad=b("polished_blackstone"), wall=b("deepslate_bricks"), corner=b("waxed_cut_copper"),
                   accent=b("gold_block"), light=b("glowstone"), top=b("campfire", lit="true", facing="north"),
                   decor=b("piston", facing="north", extended="false")),
}


def annex(p):
    s = Structure(3, 6, 3)
    s.fill(0, 0, 0, 2, 0, 2, p["pad"][0], **p["pad"][1])
    for y in (1, 2):
        for x in range(3):
            for z in range(3):
                corner = x != 1 and z != 1
                blk = p["corner"] if corner else p["wall"]
                s.set(x, y, z, blk[0], **blk[1])
    s.set(1, 2, 0, p["decor"][0], **p["decor"][1])    # the face of the machine, on the front
    for (x, z) in ((0, 0), (2, 0), (0, 2), (2, 2)):
        s.set(x, 3, z, p["corner"][0], **p["corner"][1])
        s.set(x, 4, z, p["light"][0], **p["light"][1])
    s.set(1, 3, 1, p["accent"][0], **p["accent"][1])
    s.set(1, 4, 1, p["wall"][0], **p["wall"][1])
    s.set(1, 5, 1, p["top"][0], **p["top"][1])
    return s


if __name__ == "__main__":
    for out in (OUT, OUT_DATA):
        os.makedirs(out, exist_ok=True)
        for lab, (base, key) in LABS.items():
            for level in (2, 3):
                grow(base, PALETTES[key], level).write(f"{out}/{lab}_l{level}.nbt")
        for variant, p in ANNEXES.items():
            annex(p).write(f"{out}/lab_annex{variant}.nbt")
    print("wrote", len(LABS) * 2, "lab levels and", len(ANNEXES), "annexes into", OUT, "and", OUT_DATA)
