#!/usr/bin/env python3
"""THE gates module: measures design/architecture_rules.md's gates G1-G7 (+ a light floor and a vanilla-id check) on a
built structure, so every generator test asks the same questions the same way. Built on tools/schem_study.py; tests
(each gate's red case first): tools/test_building_gates.py. G6 here is the roof chequer check; the course
rules (T0 share, wet eaves) stay with tools/roof_kit.py's tests.

Frame: y 0 is the foundation / pad, y 1 the base course. The WALL FOOTPRINT is the plan enclosed by the columns that
carry 3 or more full, non-roof-shape, non-thin blocks above y 0, standing from y 1 or 2: a plinth, pier base, sill or
lip 1 proud never moves the wall line, a window reveal never opens it (the wall above and below still counts),
and an oversailing ridge, verge or upper-storey bracket never joins it. The EAVE is the lowest top-of-column over
the footprint's edge (wall) columns: the roof's lowest course.

    python3 tools/building_gates.py FILE.nbt [--faces south,west] [--bay 4]
"""
import os
import sys
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import schem_study as S  # noqa: E402

THIN = "|=*g"
FACE_VEC = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
MATERIALS = ("copper", "deepslate", "blackstone", "red_nether", "nether_brick", "quartz", "prismarine", "mud_brick",
             "end_stone", "purpur", "stone_brick", "cobble", "brick", "terracotta", "concrete", "sandstone", "andesite",
             "diorite", "granite", "calcite", "tuff", "basalt", "smooth_stone", "stone", "gold", "iron")
WOODS = ("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "crimson", "warped", "bamboo")
COLOURS = ("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
           "purple", "blue", "brown", "green", "red", "black")

# ---- vanilla 1.20.1 block ids (no 1.20.3+ / 1.21 blocks: no tuff or polished-tuff shapes, no copper doors /
# grates, `grass` not `short_grass`). Checked against every minecraft: id our committed structures ship.
_STONE_SSW = {   # full block -> stairs / slab / wall stem
    "cobblestone": "cobblestone", "mossy_cobblestone": "mossy_cobblestone", "stone_bricks": "stone_brick",
    "mossy_stone_bricks": "mossy_stone_brick", "granite": "granite", "diorite": "diorite", "andesite": "andesite",
    "bricks": "brick", "mud_bricks": "mud_brick", "nether_bricks": "nether_brick", "red_nether_bricks": "red_nether_brick",
    "sandstone": "sandstone", "red_sandstone": "red_sandstone", "end_stone_bricks": "end_stone_brick",
    "prismarine": "prismarine", "blackstone": "blackstone", "polished_blackstone": "polished_blackstone",
    "polished_blackstone_bricks": "polished_blackstone_brick", "cobbled_deepslate": "cobbled_deepslate",
    "polished_deepslate": "polished_deepslate", "deepslate_bricks": "deepslate_brick", "deepslate_tiles": "deepslate_tile"}
_STONE_SS = {"stone": "stone", "polished_granite": "polished_granite", "polished_diorite": "polished_diorite",
             "polished_andesite": "polished_andesite", "smooth_sandstone": "smooth_sandstone",
             "smooth_red_sandstone": "smooth_red_sandstone", "quartz_block": "quartz", "smooth_quartz": "smooth_quartz",
             "prismarine_bricks": "prismarine_brick", "dark_prismarine": "dark_prismarine", "purpur_block": "purpur"}
_SLAB_ONLY = ("smooth_stone_slab", "cut_sandstone_slab", "cut_red_sandstone_slab", "petrified_oak_slab")
_COPPER = [w + a + b for w in ("", "waxed_") for a in ("", "exposed_", "weathered_", "oxidized_")
           for b in ("cut_copper", "cut_copper_stairs", "cut_copper_slab")]
_COPPER += ["copper_block", "exposed_copper", "weathered_copper", "oxidized_copper", "waxed_copper_block",
            "waxed_exposed_copper", "waxed_weathered_copper", "waxed_oxidized_copper", "lightning_rod", "copper_ore",
            "deepslate_copper_ore", "raw_copper_block"]
_WOOD_PARTS = ("planks", "stairs", "slab", "fence", "fence_gate", "door", "trapdoor", "pressure_plate", "button",
               "sign", "wall_sign", "hanging_sign", "wall_hanging_sign")
_TREES = ("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry")
_COLOUR_PARTS = ("wool", "carpet", "terracotta", "glazed_terracotta", "concrete", "concrete_powder", "stained_glass",
                 "stained_glass_pane", "bed", "banner", "wall_banner", "candle", "candle_cake", "shulker_box")
_CORALS = ("tube", "brain", "bubble", "fire", "horn")
_POTTABLE = ("oak_sapling", "spruce_sapling", "birch_sapling", "jungle_sapling", "acacia_sapling", "dark_oak_sapling",
             "cherry_sapling", "mangrove_propagule", "fern", "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet",
             "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley",
             "wither_rose", "red_mushroom", "brown_mushroom", "dead_bush", "cactus", "bamboo", "crimson_fungus",
             "warped_fungus", "crimson_roots", "warped_roots", "azalea_bush", "flowering_azalea_bush", "torchflower")
_MISC = """air cave_air void_air structure_void stone granite diorite andesite grass_block dirt coarse_dirt podzol
rooted_dirt mud packed_mud bedrock water lava sand red_sand suspicious_sand suspicious_gravel gravel gold_ore
deepslate_gold_ore iron_ore deepslate_iron_ore coal_ore deepslate_coal_ore nether_gold_ore lapis_ore deepslate_lapis_ore
diamond_ore deepslate_diamond_ore redstone_ore deepslate_redstone_ore emerald_ore deepslate_emerald_ore
nether_quartz_ore ancient_debris sponge wet_sponge glass tinted_glass glass_pane lapis_block dispenser dropper
note_block powered_rail detector_rail activator_rail rail sticky_piston piston piston_head moving_piston cobweb grass
fern dead_bush seagrass tall_seagrass tall_grass large_fern dandelion poppy blue_orchid allium azure_bluet red_tulip
orange_tulip white_tulip pink_tulip oxeye_daisy cornflower wither_rose lily_of_the_valley torchflower torchflower_crop
pitcher_plant pitcher_crop pink_petals sunflower lilac rose_bush peony brown_mushroom red_mushroom brown_mushroom_block
red_mushroom_block mushroom_stem gold_block iron_block diamond_block emerald_block netherite_block coal_block
redstone_block chiseled_quartz_block quartz_pillar quartz_bricks amethyst_block budding_amethyst amethyst_cluster
large_amethyst_bud medium_amethyst_bud small_amethyst_bud tuff calcite dripstone_block pointed_dripstone tnt bookshelf
chiseled_bookshelf obsidian crying_obsidian torch wall_torch soul_torch soul_wall_torch redstone_torch
redstone_wall_torch fire soul_fire spawner chest trapped_chest ender_chest redstone_wire crafting_table wheat farmland
furnace blast_furnace smoker ladder lever stone_pressure_plate polished_blackstone_pressure_plate
light_weighted_pressure_plate heavy_weighted_pressure_plate iron_door iron_trapdoor iron_bars chain stone_button
polished_blackstone_button snow snow_block powder_snow ice packed_ice blue_ice frosted_ice cactus clay sugar_cane
jukebox pumpkin carved_pumpkin jack_o_lantern netherrack soul_sand soul_soil basalt polished_basalt smooth_basalt
glowstone nether_portal cake repeater comparator cracked_stone_bricks chiseled_stone_bricks infested_stone
infested_cobblestone infested_stone_bricks infested_mossy_stone_bricks infested_cracked_stone_bricks
infested_chiseled_stone_bricks infested_deepslate melon melon_stem attached_melon_stem pumpkin_stem
attached_pumpkin_stem vine glow_lichen mycelium lily_pad nether_wart enchanting_table brewing_stand cauldron
water_cauldron lava_cauldron powder_snow_cauldron end_portal end_portal_frame end_stone dragon_egg redstone_lamp cocoa
tripwire_hook tripwire beacon flower_pot carrots potatoes beetroots skeleton_skull skeleton_wall_skull
wither_skeleton_skull wither_skeleton_wall_skull zombie_head zombie_wall_head player_head player_wall_head creeper_head
creeper_wall_head dragon_head dragon_wall_head piglin_head piglin_wall_head anvil chipped_anvil damaged_anvil
daylight_detector hopper observer slime_block honey_block honeycomb_block barrier light hay_block terracotta
sea_lantern end_rod chorus_plant chorus_flower purpur_pillar end_gateway magma_block nether_wart_block
warped_wart_block bone_block structure_block jigsaw command_block chain_command_block repeating_command_block
shulker_box kelp kelp_plant dried_kelp_block turtle_egg sniffer_egg sea_pickle conduit bamboo_sapling bamboo scaffolding
loom barrel smithing_table fletching_table cartography_table grindstone lectern stonecutter bell lantern soul_lantern
campfire soul_campfire sweet_berry_bush shroomlight weeping_vines weeping_vines_plant twisting_vines
twisting_vines_plant target beehive bee_nest respawn_anchor lodestone gilded_blackstone chiseled_polished_blackstone
cracked_polished_blackstone_bricks chiseled_nether_bricks cracked_nether_bricks candle candle_cake moss_block
moss_carpet cave_vines cave_vines_plant spore_blossom azalea flowering_azalea azalea_leaves flowering_azalea_leaves
big_dripleaf big_dripleaf_stem small_dripleaf hanging_roots deepslate chiseled_deepslate cracked_deepslate_bricks
cracked_deepslate_tiles reinforced_deepslate sculk sculk_vein sculk_catalyst sculk_shrieker sculk_sensor
calibrated_sculk_sensor frogspawn ochre_froglight verdant_froglight pearlescent_froglight mangrove_roots
muddy_mangrove_roots mangrove_propagule decorated_pot raw_iron_block raw_gold_block chiseled_sandstone cut_sandstone
chiseled_red_sandstone cut_red_sandstone smooth_stone dirt_path bubble_column nether_sprouts crimson_nylium
warped_nylium crimson_fungus warped_fungus crimson_roots warped_roots crimson_stem warped_stem stripped_crimson_stem
stripped_warped_stem crimson_hyphae warped_hyphae stripped_crimson_hyphae stripped_warped_hyphae bamboo_block
stripped_bamboo_block bamboo_mosaic bamboo_mosaic_stairs bamboo_mosaic_slab nether_brick_fence""".split()
VANILLA = set(_MISC) | set(_COPPER) | set(_SLAB_ONLY)
VANILLA |= {n for full, st in _STONE_SSW.items() for n in (full, st + "_stairs", st + "_slab", st + "_wall")}
VANILLA |= {n for full, st in _STONE_SS.items() for n in (full, st + "_stairs", st + "_slab")}
VANILLA |= {w + "_" + p for w in WOODS for p in _WOOD_PARTS}
VANILLA |= {n for t in _TREES
            for n in (t + "_log", "stripped_" + t + "_log", t + "_wood", "stripped_" + t + "_wood", t + "_leaves")}
VANILLA |= {t + "_sapling" for t in _TREES if t != "mangrove"}
VANILLA |= {c + "_" + p for c in COLOURS for p in _COLOUR_PARTS}
VANILLA |= {d + c + s for c in _CORALS for d in ("", "dead_")
            for s in ("_coral_block", "_coral", "_coral_fan", "_coral_wall_fan")}
VANILLA |= {"potted_" + p for p in _POTTABLE}


def _frame(b):
    """(footprint columns, edge columns, eave y) - see the module doc."""
    def body_block(n):
        return n not in S.AIR and n not in S.GROUND and not S.is_roof_shape(n) and S.glyph(n) not in THIN
    ys = {}
    for (x, y, z), (n, _) in b.blocks.items():
        if y >= 1 and body_block(n):
            ys.setdefault((x, z), set()).add(y)
    body = {c for c, s in ys.items() if len(s) >= 3 and min(s) <= 2}
    fp = S._footprint(body)
    edge = {(x, z) for (x, z) in fp if any((x + a, z + c) not in fp for a, c in FACE_VEC.values())}
    top = {}
    for (x, y, z), (n, _) in b.blocks.items():
        if (x, z) in edge and n not in S.AIR and y > top.get((x, z), -1):
            top[(x, z)] = y
    eave = min(top.values()) if top else 0
    return fp, edge, eave


def overhang(b):
    """G1: (ring columns that carry an oversailing piece, ring columns) - the ring is every column 1 block (Chebyshev)
    outside the wall footprint; an oversailing piece is any non-thin block at or above the wall top (eave - 1):
    an eave course, a gutter, a verge, a cornice lip. Passing needs got == need: the whole eave + verge perimeter."""
    fp, _, eave = _frame(b)
    ring = {(x + a, z + c) for (x, z) in fp for a in (-1, 0, 1) for c in (-1, 0, 1)} - fp
    over = {(x, z) for (x, y, z), (n, _) in b.blocks.items()
            if (x, z) in ring and y >= eave - 1 and n not in S.AIR and n not in S.GROUND and S.glyph(n) not in THIN}
    return len(over), len(ring)


def shares(b):
    """G2 / G3 / G4: thin share (glyphs | = * g), the top block and its share, the wall band's and the roof's main
    material families. Ground blocks never count."""
    cells = {q: n for q, (n, _) in b.blocks.items() if n not in S.AIR and n not in S.GROUND}
    c = Counter(cells.values())
    total = max(1, len(cells))
    top, k = c.most_common(1)[0] if c else ("", 0)
    _, _, eave = _frame(b)

    def fam(n):
        n = n[len("waxed_"):] if n.startswith("waxed_") else n
        for w in WOODS:
            if n.startswith(w + "_") or n.startswith("stripped_" + w):
                return "wood"
        return next((m for m in MATERIALS if m in n), n)
    walls = Counter(fam(n) for (x, y, z), n in cells.items()
                    if 2 <= y < eave - 1 and not S.is_roof_shape(n) and S.glyph(n) not in THIN)
    roofs = Counter(fam(cells[q]) for q in S.roof_cells(b) if q in cells and q[1] >= eave)
    return {"thin": sum(v for n, v in c.items() if S.glyph(n) in THIN) / total, "top": top, "top_share": k / total,
            "wall_family": walls.most_common(1)[0][0] if walls else None,
            "roof_family": roofs.most_common(1)[0][0] if roofs else None}


def depth(b, face):
    """G5 on the WALL BAND only (y 1 up to under the cap line, eave - 2): {plane: share} seen from `face`, ok when no
    plane holds over 45 % and >= 3 planes hold >= 10 %. A roof that steps out above a blind wall earns nothing.
    Relief only (r10): lights (glyph *) and glass in its frame's own plane (_flush_glass) are left out - glass set
    flush is infill, not relief: a pane set forward to the pilasters' face so the 3/4 camera sees it (r9's set-back
    panes were invisible) added a "plane" the wall never had. A pane recessed 1 behind its frame is a real reveal
    and still counts. window_glass_set() guards the other way: every bay's glass within 1 of its frame's plane."""
    _, _, eave = _frame(b)
    infill = _flush_glass(b)
    band = S.Build({q: v for q, v in b.blocks.items() if 1 <= q[1] <= eave - 2 and q not in infill
                    and S.glyph(v[0]) != "*"})
    d = S.depth_layers(band, face)
    tot = max(1, sum(d.values()))
    planes = {k: v / tot for k, v in d.items()}
    ok = bool(planes) and max(planes.values()) <= 0.45 and sum(1 for v in planes.values() if v >= 0.10) >= 3
    return {"ok": ok, "planes": planes}


def _flush_glass(b):
    """Glass cells in their frame's own plane (window_glass_set's frame depth 0): {cell}."""
    return {q for q, d in _glass_frames(b) if d == 0}


def window_glass_set(b):
    """[(pane, [frame depths])] for each glass pane NOT within 1 block of its frame's plane - see _glass_frames."""
    return sorted((q, ds) for q, ds in _glass_frames(b, all_depths=True) if not any(d is not None and 0 <= d <= 1
                                                                                for d in ds))


def _glass_frames(b, all_depths=False):
    """[(pane, depth)] for every glass pane in a wall (an open side - air, or a railing the eye looks through - with
    a solid block behind it): along each such facing, the frame is the first non-glass block beside the glass run
    either way along the wall and over / under it, and its depth how far that frame's contiguous face stands out of
    the pane's plane. all_depths: every facing's depth (window_glass_set's list); else the smallest depth in 0..1,
    None when there is none. window_glass_set fails a pane with no depth in 0..1: sunk 2+ in its reveal (r9's panes,
    2-3 behind their pilasters, invisible to the 3/4 camera), proud of its frame, or frameless - so glass set
    forward for the camera stays framed, never a flush, flat facade."""
    cells = {q: n for q, (n, _) in b.blocks.items() if n not in S.AIR}

    def solid(q):                                # a frame: not glass or a light (a wall-block pilaster is one)
        return q in cells and S.glyph(cells[q]) not in "g*"
    bad = []
    for q, n in cells.items():
        if S.glyph(n) != "g":
            continue
        def at(a, t):
            return (q[0] + (t if a == 0 else 0), q[1], q[2] + (t if a == 2 else 0))
        # a facing: an open side (air, or a railing / post the eye looks through) with masonry or a room behind
        faces = [(a, sg) for a, sg in ((0, -1), (2, 1), (0, 1), (2, -1))      # behind it masonry or a lit room
                 if (at(a, sg) not in cells or S.glyph(cells[at(a, sg)]) == "|") and at(a, -sg) in cells
                 and S.glyph(cells[at(a, -sg)]) != "g"]
        if not faces:
            continue                             # free-standing glass, not a window in a wall
        tried = []
        for a, sg in faces:
            side = 2 if a == 0 else 0
            frames = []
            for axis, d in ((side, -1), (side, 1), (1, 1), (1, -1)):
                k = list(q)
                for _ in range(4):
                    k[axis] += d
                    if tuple(k) not in cells or S.glyph(cells[tuple(k)]) != "g":
                        break
                def f(t):
                    return solid(tuple(k[i] + (sg * t if i == a else 0) for i in range(3)))
                start = next((t for t in range(-2, 3) if f(t)), None)    # the frame's back in the wall ...
                if start is None:
                    continue
                t = start                                                  # ... and its face, contiguous with it
                while t < 3 and f(t + 1):
                    t += 1
                frames.append(t)
            tried.append(max(frames) if frames else None)
        if all_depths:
            bad.append((q, tried))
        else:
            ok = [p for p in tried if p is not None and 0 <= p <= 1]
            bad.append((q, min(ok) if ok else None))
    return bad


def base_cap(b):
    """G7: every straight wall run (the footprint's edge, per side, split where it breaks) has a distinct base course
    at y 1 (in the wall or 1 proud: a block other than the run's body block) and a cap line (a non-thin piece 1 proud
    at or above the wall top) along >= 80 % of its length."""
    fp, edge, eave = _frame(b)
    n = {q: v[0] for q, v in b.blocks.items() if v[0] not in S.AIR and v[0] not in S.GROUND}
    runs = []
    for face, (dx, dz) in FACE_VEC.items():
        cols = sorted((x, z) for (x, z) in edge if (x + dx, z + dz) not in fp)
        groups = {}
        for x, z in cols:
            groups.setdefault(x if dx else z, []).append(z if dx else x)
        for at, ks in groups.items():
            ks.sort()
            seg = [[ks[0]]]
            for k in ks[1:]:
                (seg[-1].append(k) if k == seg[-1][-1] + 1 else seg.append([k]))
            for s in seg:
                qs = [(at, k) if dx else (k, at) for k in s]
                body = Counter(n.get((x, y, z)) for x, z in qs for y in range(2, max(3, eave - 1))
                               if n.get((x, y, z)) and not S.is_roof_shape(n[(x, y, z)])).most_common(1)
                body = body[0][0] if body else None
                base = sum(1 for x, z in qs if any(n.get(q) not in (None, body) and S.glyph(n[q]) not in THIN
                                                   for q in ((x, 1, z), (x + dx, 1, z + dz))))
                cap = sum(1 for x, z in qs if any(n.get((x + dx, y, z + dz)) and S.glyph(n[(x + dx, y, z + dz)])
                                                  not in THIN for y in range(eave - 1, eave + 40)))
                runs.append({"face": face, "at": at, "len": len(s), "base": base >= 0.8 * len(s),
                             "cap": cap >= 0.8 * len(s)})
    return {"ok": bool(runs) and all(r["base"] and r["cap"] for r in runs), "runs": runs}


def lights(b, faces=("south", "west"), bay=4):
    """The light floor: each camera face, cut into bays of `bay` cells (the remainder joins the last), has >= 1 light
    (glyph *) per bay within 2 in front of / 3 behind its wall line, below the eave."""
    fp, _, eave = _frame(b)
    out = {}
    for face in faces:
        dx, dz = FACE_VEC[face]
        along = [x if dz else z for x, z in fp]
        line = max((x * dx + z * dz) for x, z in fp)
        a0, width = min(along), max(along) - min(along) + 1
        nb = max(1, width // bay)
        lit = set()
        for (x, y, z), (nm, _) in b.blocks.items():
            if S.glyph(nm) == "*" and 1 <= y < eave and -3 <= (x * dx + z * dz) - line <= 2:
                lit.add(min(nb - 1, max(0, ((x if dz else z) - a0) // bay)))
        out[face] = {"bays": nb, "lit": len(lit)}
    return {"ok": all(v["lit"] >= v["bays"] for v in out.values()), "faces": out}


CHEQUER_MAX = 0.1      # roof_kit's own courses never reach it; an alternating roof is ~1.0


def roof_chequer(b):
    """G6: the share of the visible roof cells (roof shapes on top of their column, at or above the eave) that sit in
    a 3 x 2 alternation a b a / b a b seen from above - a chequer. A diagonal line (a hip drawn one cell wide) makes
    2 x 2 alternations all along it but never three in a row, so it is a line, not a chequer."""
    _, _, eave = _frame(b)
    top = {}
    for (x, y, z), (n, _) in b.blocks.items():
        if n not in S.AIR and y > top.get((x, z), (-10 ** 9, None))[0]:
            top[(x, z)] = (y, n)
    g = {c: n for c, (y, n) in top.items() if S.is_roof_shape(n) and y >= eave}
    bad = set()
    for (x, z), a in g.items():
        for dx, dz in ((1, 0), (0, 1)):               # a b a over b a b, along x or along z
            ox, oz = dz, dx
            cells = [(x + k * dx + j * ox, z + k * dz + j * oz) for j in (0, 1) for k in (0, 1, 2)]
            want = [a if (k + j) % 2 == 0 else None for j in (0, 1) for k in (0, 1, 2)]
            got = [g.get(q) for q in cells]
            b = got[1]
            if b is None or b == a:
                continue
            if all(v == (a if w is not None else b) for v, w in zip(got, want)):
                bad |= set(cells)
    return len(bad) / len(g) if g else 0.0


VIEWS = {   # the camera looking in: a picture cell of q, and its nearness to the camera (the larger, the nearer)
    "top": (lambda q: (q[0], q[2]), lambda q: q[1]),
    "south": (lambda q: (q[0], q[1]), lambda q: q[2]),
    "north": (lambda q: (q[0], q[1]), lambda q: -q[2]),
    "east": (lambda q: (q[2], q[1]), lambda q: q[0]),
    "west": (lambda q: (q[2], q[1]), lambda q: -q[0]),
    "34s": (lambda q: (q[0], q[1] - q[2]), lambda q: q[1] + q[2]),     # the 3/4 still: from the south, 45 deg up
    "34w": (lambda q: (q[2], q[1] + q[0]), lambda q: q[1] - q[0]),     # the same from the west
}
PALE = ("quartz", "calcite", "diorite", "sandstone", "birch", "bone", "white", "end_stone", "snow", "smooth_stone",
        "stone_brick")   # r5: grey cut stone is the pale dressing against brick, deepslate and slate
DARK = ("deepslate", "blackstone", "dark_oak", "nether_brick", "basalt", "black", "obsidian", "coal", "gray", "iron_bars",
        "chain")


def camera(b, view):
    """What a camera sees of `b` from one side, from above or from the 3/4 still: {picture cell: (q, name, props)} -
    the FIRST non-air block met along each ray. A part's intent tests ('8 ribs', 'passes G6') can all be green while
    the camera sees a chequer; this is the picture the critic looks at."""
    key, near = VIEWS[view]
    best = {}
    for q, (n, pr) in b.blocks.items():
        if n in S.AIR:
            continue
        k = key(q)
        if k not in best or near(q) > near(best[k][0]):
            best[k] = (q, n, pr)
    return best


# ---- the eyes stills (EyesDirector scene 15, bar/.../matchstart/EyesDirector.java sceneStructures) --------------
# r10 (the r9 critic, realism 5 / detail 6: every gate green, every picture failed - "more parts, not more building"):
# the gates measured the generator's cells, not the picture. STILLS rebuilds the four structure stills so a test can
# ask what each one shows: the structure stamped with its origin at the pad (structure y 0 on the pad's top), the RTS
# camera orthographic (OrthoviewClientEvents.getOrthographicProjection: the view `zoom` blocks tall at the 1600 x 900
# aspect), its yaw CAMROTX_DEFAULT 135 (looking +x -z, from the south-west), its pitch the zoom's tilt (basePitchDeg:
# 55 deg at zoom 10 to 75 at 135, linear in log zoom), centred on the footprint's middle at 0.3 of the height
# (centreCameraOnPosInstant(structureMiddle(0.3))); rts at zoom 30, rts_close at min(26, max(12, 1.25 (big + 0.6 h)));
# 34 and dusk (the same view at day time 13000) first-person from the south-west, 30 deg up, 1.5 x the largest size
# (>= 12) from the middle at 0.4 of the height, the client's 70 deg vertical field of view x 1.1 in
# creative flight, 77 (standThreeQuarter).
STILLS = ("rts", "rts_close", "34", "dusk")
SCREEN = (1600, 900)
# the 3/4 still's vertical field of view: the client's 70 times the creative-flight FOV modifier (1.1) - r12 (Oct 30): at
# 70 the model drew the building ~1.2x too big on run 03:35Z's r11 34 / dusk stills (the copper mask landed up-left of
# the roof, the glass mask above the windows); 77 puts the outline and the windows on the pixels
# (tools/engine_shed_measure.py's overlays, five landmarks and the dusk window fit agree)
FOV = 77.0
FACE_NAMES = {(0, -1): "west", (0, 1): "east", (1, -1): "down", (1, 1): "up", (2, -1): "north", (2, 1): "south"}


def rts_pitch(zoom):
    """OrthoviewClientEvents.basePitchDeg with the default config (camera_pitch_close_v2 55, far_v2 75, ZOOM_MIN 10,
    ZOOM_TILT_FAR 135)."""
    import math
    t = (math.log(max(zoom, 10)) - math.log(10)) / (math.log(135) - math.log(10))
    return 55 + 20 * min(max(t, 0.0), 1.0)


def still_camera(size, still):
    """(kind, eye or centre, forward, right, up, half-width, half-height) of one still for a structure of `size`
    (x, y, z) - in structure blocks, y 0 the structure's bottom layer."""
    import numpy as np
    sx, sy, sz = size
    big = max(sx, sz)
    if still in ("rts", "rts_close"):
        zoom = 30.0 if still == "rts" else min(26.0, max(12.0, 1.25 * (big + 0.6 * sy)))
        p = np.radians(rts_pitch(zoom))
        f = np.array([np.cos(p) / np.sqrt(2), -np.sin(p), -np.cos(p) / np.sqrt(2)])
        c = np.array([sx / 2.0, 0.3 * sy, sz / 2.0])
        half_h = zoom / 2
        kind = "ortho"
    else:
        d = max(12.0, 1.5 * max(sx, sy, sz))
        aim = np.array([sx / 2.0, 0.4 * sy, sz / 2.0])
        h, up = d * np.cos(np.radians(30)), d * np.sin(np.radians(30))
        c = np.array([aim[0] - h / np.sqrt(2), max(aim[1] + up, 2.5), aim[2] + h / np.sqrt(2)])
        f = (aim - c) / np.linalg.norm(aim - c)
        half_h = np.tan(np.radians(FOV / 2))
        kind = "persp"
    r = np.cross(f, [0.0, 1.0, 0.0])
    r /= np.linalg.norm(r)
    u = np.cross(r, f)
    return kind, c, f, r, u, half_h * SCREEN[0] / SCREEN[1], half_h


def box(name, props, open_x=True):
    """The block's shape as one box in its cell, (lo, hi) in sixteenths: full cubes and stairs whole; a bottom / top
    slab its half; a lantern, candle, chain, button, rail, carpet or campfire its small box; a wall, fence or post its
    8-wide stem; a glass pane or iron bars a 2-thick plane facing its open side (along x when open_x); a glass block
    a clear cube."""
    n = name
    if n.endswith("_slab"):
        t = props.get("type", "bottom")
        return ((0, 0, 0), (16, 8, 16)) if t == "bottom" else ((0, 8, 0), (16, 16, 16)) if t == "top" else \
            ((0, 0, 0), (16, 16, 16))
    if "lantern" in n:
        return ((5, 0, 5), (11, 9, 11)) if props.get("hanging") != "true" else ((5, 7, 5), (11, 16, 11))
    if n.endswith("candle"):
        return (6, 0, 6), (10, 6, 10)
    if n == "chain":
        return (6, 0, 6), (10, 16, 10)
    if n.endswith(("_button",)):
        return (5, 0, 5), (11, 2, 11)
    if n.endswith("rail") or n.endswith("_carpet"):
        return (0, 0, 0), (16, 1, 16)
    if "campfire" in n:
        return (0, 0, 0), (16, 7, 16)
    if n.endswith(("_wall", "_fence")) or n == "lightning_rod" or n.endswith("_rod"):
        return (4, 0, 4), (12, 16, 12)
    if S.glyph(n) == "g" and not n.endswith("_pane"):
        return (0, 0, 0), (16, 16, 16)          # a glass block: a clear cube
    if S.glyph(n) == "g" or n == "iron_bars":
        return ((7, 0, 0), (9, 16, 16)) if open_x else ((0, 0, 7), (16, 16, 9))
    return (0, 0, 0), (16, 16, 16)


def _march(grid, see_through, o, d, lo, hi):
    """A voxel DDA per ray (o, d: n x 3) through grid (block ids, 0 air), each cell's block the box lo..hi (per cell,
    in blocks): (id, cell, entry axis, entry sign) of the first box the ray passes through, id 0 where it leaves the
    grid. Ids flagged see_through are passed through."""
    import numpy as np
    size = np.array(grid.shape)
    n = len(o)
    with np.errstate(divide="ignore", invalid="ignore"):
        inv = 1.0 / d
        t0, t1 = (0 - o) * inv, (size - o) * inv
        tlo = np.where(np.isnan(np.minimum(t0, t1)), -np.inf, np.minimum(t0, t1))
        thi = np.where(np.isnan(np.maximum(t0, t1)), np.inf, np.maximum(t0, t1))
    tn = np.maximum(tlo.max(axis=1), 0)
    alive = thi.min(axis=1) > tn
    vox = np.clip(np.floor(o + d * (tn[:, None] + 1e-7)).astype(int), 0, size - 1)
    stp = np.where(d > 0, 1, -1)
    with np.errstate(divide="ignore", invalid="ignore"):
        tmax = np.where(d != 0, (vox + (stp > 0) - o) / d, np.inf)
        tdel = np.where(d != 0, np.abs(1.0 / d), np.inf)
    hit = np.zeros(n, dtype=np.int32)
    axis = np.zeros(n, dtype=int)
    sign = np.zeros(n, dtype=int)
    for _ in range(int(size.sum()) + 4):
        idx = np.nonzero(alive)[0]
        if not len(idx):
            break
        v = vox[idx]
        bid = grid[v[:, 0], v[:, 1], v[:, 2]]
        cand = np.nonzero((bid > 0) & ~see_through[bid])[0]
        stop = np.zeros(len(idx), dtype=bool)
        if len(cand):
            rows = idx[cand]
            vc = v[cand]
            b0 = vc + lo[vc[:, 0], vc[:, 1], vc[:, 2]]
            b1 = vc + hi[vc[:, 0], vc[:, 1], vc[:, 2]]
            with np.errstate(divide="ignore", invalid="ignore"):
                ta, tb = (b0 - o[rows]) / d[rows], (b1 - o[rows]) / d[rows]
                ta = np.where(np.isnan(ta), -np.inf, ta)
                tb = np.where(np.isnan(tb), np.inf, tb)
            near, far = np.minimum(ta, tb), np.maximum(ta, tb)
            enter, leave = near.max(axis=1), far.min(axis=1)
            ok = (enter <= leave) & (leave >= 0)
            stop[cand[ok]] = True
            ax = np.argmax(near, axis=1)
            axis[rows[ok]] = ax[ok]
            sign[rows[ok]] = -stp[rows[ok], ax[ok]]
        hit[idx[stop]] = bid[stop]
        alive[idx[stop]] = False
        idx = idx[~stop]
        ax = np.argmin(tmax[idx], axis=1)
        vox[idx, ax] += stp[idx, ax]
        tmax[idx, ax] += tdel[idx, ax]
        gone = ((vox[idx] < 0) | (vox[idx] >= size)).any(axis=1)
        alive[idx[gone]] = False
    return hit, vox, axis, sign


def shoot(b, still, step=4):
    """Every `step`-th pixel of a still, cast into `b` (a voxel DDA, each block its box(): slabs half, lanterns,
    posts, chains and rails small, panes and bars thin planes): {(px, py): hit} with hit = (cell, name, face, behind),
    face the side of the block's box the ray entered by (FACE_NAMES), behind (cell, name) of the first non-glass
    block past a glass hit, else None - glass is seen AND seen through. Pixels that miss the structure are left out."""
    import numpy as np
    cells = {q: v for q, v in b.blocks.items() if v[0] not in S.AIR}
    if not cells:
        return {}
    size = tuple(max(q[i] for q in cells) + 1 for i in range(3))
    ids, names = {}, [None]
    grid = np.zeros(size, dtype=np.int32)
    lo = np.zeros(size + (3,))
    hi = np.ones(size + (3,))
    for q, (n, pr) in cells.items():
        if n not in ids:
            ids[n] = len(names)
            names.append(n)
        grid[q] = ids[n]
        runs_x = pr.get("east") == "true" or pr.get("west") == "true"       # a pane's plane runs the way it
        runs_z = pr.get("north") == "true" or pr.get("south") == "true"     # connects; else it faces its open side
        if runs_x != runs_z:
            open_x = runs_z
        else:
            open_x = any(not (0 <= q[0] + e < size[0]) or (q[0] + e, q[1], q[2]) not in cells for e in (-1, 1))
        a, c = box(n, pr, open_x)
        lo[q], hi[q] = np.array(a) / 16, np.array(c) / 16
    glass = np.array([False] + [S.glyph(n) == "g" for n in names[1:]])
    kind, c, f, r, u, hw, hh = still_camera(size, still)
    W, H = SCREEN
    px, py = np.meshgrid(np.arange(0, W, step), np.arange(0, H, step))
    px, py = px.ravel(), py.ravel()
    a = ((px + 0.5) / W * 2 - 1)[:, None]
    v = (1 - (py + 0.5) / H * 2)[:, None]
    if kind == "ortho":
        o = c + a * hw * r + v * hh * u - 200 * f
        d = np.tile(f, (len(px), 1))
    else:
        d = f + a * hw * r + v * hh * u
        d /= np.linalg.norm(d, axis=1)[:, None]
        o = np.tile(c, (len(px), 1))
    hit, vox, axis, sign = _march(grid, np.zeros(len(names), dtype=bool), o, d, lo, hi)
    got = np.nonzero(hit)[0]
    gl = got[glass[hit[got]]]
    behind = {}
    if len(gl):
        h2, v2, _, _ = _march(grid, glass, o[gl], d[gl], lo, hi)
        for i, h, w in zip(gl, h2, v2):
            if h:
                behind[i] = (tuple(int(x) for x in w), names[h])
    out = {}
    for i in got:
        q = tuple(int(x) for x in vox[i])
        out[(int(px[i]), int(py[i]))] = (q, names[hit[i]], FACE_NAMES[(int(axis[i]), int(sign[i]))], behind.get(i))
    return out


def seen(b, stills=STILLS, step=4):
    """{still: {cell: pixels}} - how many sampled pixels of each still show each cell."""
    out = {}
    for st in stills:
        cnt = Counter(h[0] for h in shoot(b, st, step).values())
        out[st] = dict(cnt)
    return out


def tone(name):
    """A block's luminance class from its id: pale, dark, glass, light or mid."""
    if S.glyph(name) == "g":
        return "glass"
    if S.glyph(name) == "*":
        return "light"
    if any(k in name for k in DARK):
        return "dark"
    if any(k in name for k in PALE):
        return "pale"
    return "mid"


def alternations(grid):
    """Cells of a picture {(u, v): shade} that sit in a CHEQUER: an a b a b a run along either axis, or a 3 x 2 block
    a b a / b a b. One line crossing a field is a b a and two lines meeting at 45 degrees a b a b - both drawn
    lines, not a chequer; two full alternations are."""
    bad = set()
    for (u, v), a in grid.items():
        for du, dv in ((1, 0), (0, 1)):
            run = [(u + k * du, v + k * dv) for k in range(5)]
            t = [grid.get(c) for c in run]
            if None not in t and t[1] != a and t[2] == t[4] == a and t[3] == t[1]:
                bad |= set(run)
            blk = [(u + k * du + j * dv, v + k * dv + j * du) for j in (0, 1) for k in (0, 1, 2)]
            t = [grid.get(c) for c in blk]
            if None not in t and t[1] != a and all(s == (a if (i % 3 + i // 3) % 2 == 0 else t[1])
                                                   for i, s in enumerate(t)):
                bad |= set(blk)
    return bad


def camera_chequer(b, views=("top", "south", "west")):
    """{view: chequered cells} in the camera pictures, each visible cell shaded by its block id - material AND shape,
    since a stair, a slab and a full block of one stone catch the light differently."""
    return {view: len(alternations({k: v[1] for k, v in camera(b, view).items()})) for view in views}


def unknown_ids(names):
    """Sorted ids (minecraft: optional) that are not vanilla 1.20.1 blocks; any other namespace is unknown here."""
    return sorted({n for n in names if (n[10:] if n.startswith("minecraft:") else n) not in VANILLA})


def gates(b, faces=("south", "west"), bay=4):
    """Every gate at once: {gate: {"ok": bool, ...numbers}}."""
    got, need = overhang(b)
    s = shares(b)
    d = {f: depth(b, f) for f in faces}
    bad = unknown_ids({n for n, _ in b.blocks.values()})
    return {"G1": {"ok": need > 0 and got >= need, "got": got, "need": need},
            "G2": {"ok": s["thin"] >= 0.08, "thin": round(s["thin"], 3)},
            "G3": {"ok": s["top_share"] <= 0.25, "top": s["top"], "share": round(s["top_share"], 3)},
            "G4": {"ok": s["roof_family"] != s["wall_family"], "wall": s["wall_family"], "roof": s["roof_family"]},
            "G5": {"ok": all(v["ok"] for v in d.values()),
                   "planes": {f: {k: round(v, 2) for k, v in sorted(x["planes"].items())} for f, x in d.items()}},
            "G6": {"ok": roof_chequer(b) <= CHEQUER_MAX, "chequer": round(roof_chequer(b), 3)},
            "G7": base_cap(b), "light": lights(b, faces, bay), "ids": {"ok": not bad, "unknown": bad}}


if __name__ == "__main__":
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("file")
    ap.add_argument("--faces", default="south,west")
    ap.add_argument("--bay", type=int, default=4)
    a = ap.parse_args()
    for k, v in gates(S.load(a.file), tuple(a.faces.split(",")), a.bay).items():
        print(k, "ok" if v["ok"] else "FAIL", {x: y for x, y in v.items() if x not in ("ok", "runs")})
