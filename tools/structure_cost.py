#!/usr/bin/env python3
"""What a building structure costs the game, read from its .nbt (lovish, Oct 21 16:13, on the White Angel capitol:
"try to optimize it all" - measured, keeping every visible detail):

  blocks          non-air cells (each is placed by the builders, stored, sent, and counts toward the building)
  hidden          full opaque cubes all six of whose neighbours are full opaque cubes: nothing of them is ever drawn
  exposed faces   faces of full opaque cubes that touch a non-opaque cell: about the quads the chunk mesher keeps for
                  them (partial blocks - stairs, slabs, walls, glass, plants - are counted apart)
  partial         non-full blocks (their models' quads are drawn whole)
  lights          light-emitting blocks; "dark" ones touch no open cell joined to the outside (buried in masonry, or
                  in a sealed pocket): they light nothing anyone sees and show nothing, and a light source still costs
                  the light engine when it is placed and when its chunk loads
  block entities  blocks that carry a block entity (banners...) - each ticks or renders through its own renderer
  random ticks    blocks that take random ticks (vines spread, grass spreads or dies, non-persistent leaves decay)
  entities        entities saved in the structure

    python3 tools/structure_cost.py a.nbt [b.nbt ...]           one row per structure
    python3 tools/structure_cost.py --compare old_dir new_dir     the same names side by side, with the change
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# light levels of the emitters these structures use (vanilla's, and our own wa_tracery's 10)
LIGHT = {"shroomlight": 15, "glowstone": 15, "sea_lantern": 15, "lantern": 15, "soul_lantern": 10, "torch": 14,
         "wall_torch": 14, "soul_torch": 10, "jack_o_lantern": 15, "campfire": 15, "end_rod": 14, "light": 15,
         "ochre_froglight": 15, "verdant_froglight": 15, "pearlescent_froglight": 15, "redstone_lamp": 15,
         "wa_tracery": 10, "wa_tracery_head": 10, "beacon": 15, "conduit": 15, "magma_block": 3, "crying_obsidian": 10}
BLOCK_ENTITY_SUFFIXES = ("_banner", "_wall_banner", "_sign", "_hanging_sign", "_head", "_skull", "_bed")
BLOCK_ENTITY_NAMES = {"chest", "trapped_chest", "barrel", "campfire", "soul_campfire", "lectern", "bell", "beehive",
                      "bee_nest", "decorated_pot", "chiseled_bookshelf", "brewing_stand", "furnace", "smoker",
                      "blast_furnace", "hopper", "dispenser", "dropper", "jukebox", "spawner", "enchanting_table",
                      "beacon", "conduit", "ender_chest", "shulker_box", "suspicious_sand", "suspicious_gravel"}
RANDOM_TICK = {"vine", "grass_block", "mycelium", "farmland", "sugar_cane", "cactus", "bamboo", "kelp", "cave_vines",
               "sweet_berry_bush", "pointed_dripstone", "ice", "snow", "turtle_egg", "twisting_vines",
               "weeping_vines", "chorus_flower", "nether_wart", "wheat", "carrots", "potatoes", "beetroots",
               "copper_block", "cut_copper", "cut_copper_stairs", "cut_copper_slab", "exposed_copper"}
# not full opaque cubes (light and sight pass, or the model is partial)
PARTIAL_SUFFIXES = ("_stairs", "_slab", "_wall", "_fence", "_fence_gate", "_pane", "_glass", "_door", "_trapdoor",
                    "_carpet", "_banner", "_leaves", "_button", "_pressure_plate", "_sign", "_torch", "_bars", "_rod",
                    "_flower", "_tulip", "_sapling", "_head", "_skull", "_candle", "_lantern")
PARTIAL_NAMES = {"lantern", "soul_lantern", "chain", "iron_bars", "vine", "fern", "grass", "tall_grass", "poppy",
                 "dandelion", "oxeye_daisy", "hanging_roots", "glass", "ladder", "scaffolding", "dirt_path", "farmland",
                 "wa_finial", "wa_finial_gold", "wa_gargoyle", "azalea", "moss_carpet",
                 "flowering_azalea", "light", "end_rod", "campfire", "bell", "lectern", "cobweb"}


def base(name):
    return name.split(":", 1)[-1]


def is_full_opaque(name):
    b = base(name)
    if b in ("air", "cave_air", "void_air") or b in PARTIAL_NAMES:
        return False
    if any(b.endswith(s) for s in PARTIAL_SUFFIXES):
        return False
    if name.startswith(("mcwroofs:", "mcwwindows:", "mcwfences:")):
        return False          # Macaw's roofs, windows and fences are partial models
    return True


def outside_air(cells, full, size):
    """The open cells (not full opaque) joined to the outside: a flood from a one-cell margin round the box."""
    sx, sy, sz = size
    seen = set()
    stack = [(-1, -1, -1)]
    nb = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
    while stack:
        x, y, z = stack.pop()
        for dx, dy, dz in nb:
            q = (x + dx, y + dy, z + dz)
            if q in seen or not (-1 <= q[0] <= sx and -1 <= q[1] <= sy and -1 <= q[2] <= sz) or q in full:
                continue
            seen.add(q)
            stack.append(q)
    return seen


def dark_lights(cells, full, size):
    """The light sources that touch no open cell joined to the outside."""
    air = outside_air(cells, full, size)
    nb = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
    out = []
    for p, (nm, props, _) in cells.items():
        if LIGHT.get(base(nm), 0) and not any((p[0] + d[0], p[1] + d[1], p[2] + d[2]) in air for d in nb):
            out.append(p)
    return out


def measure(path):
    from preview_structure import load_nbt     # here, not at the top: the capitol generator imports this module for
    root = load_nbt(path)                      # dark_lights, and CI's bare python3 (check_parts) has no PIL
    pal = root["palette"]
    cells = {}
    for b in root["blocks"]:
        st = pal[b["state"]]
        nm = st["Name"]
        if base(nm) in ("air", "cave_air", "void_air", "structure_void"):
            continue
        cells[tuple(b["pos"])] = (nm, st.get("Properties", {}), "nbt" in b)
    full = {p for p, (nm, _, _) in cells.items() if is_full_opaque(nm)}
    out = {"blocks": len(cells), "hidden": 0, "exposed faces": 0, "partial": len(cells) - len(full), "lights": 0,
           "dark lights": 0, "light sum": 0, "block entities": 0, "random ticks": 0,
           "entities": len(root.get("entities", []))}
    nbrs = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
    for p, (nm, props, has_nbt) in cells.items():
        b = base(nm)
        x, y, z = p
        if p in full:
            open_faces = sum((x + dx, y + dy, z + dz) not in full for dx, dy, dz in nbrs)
            out["exposed faces"] += open_faces
            if open_faces == 0:
                out["hidden"] += 1
        lv = LIGHT.get(b, 0)
        if b == "redstone_lamp" and props.get("lit") != "true":
            lv = 0
        if lv:
            out["lights"] += 1
            out["light sum"] += lv
        if has_nbt or b in BLOCK_ENTITY_NAMES or (not b.startswith("wa_") and any(b.endswith(s) for s in BLOCK_ENTITY_SUFFIXES)):
            out["block entities"] += 1
        if b in RANDOM_TICK or (b.endswith("_leaves") and props.get("persistent") != "true"):
            out["random ticks"] += 1
    out["dark lights"] = len(dark_lights(cells, full, root["size"]))
    return out


KEYS = ("blocks", "hidden", "exposed faces", "partial", "lights", "dark lights", "light sum", "block entities",
        "random ticks", "entities")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("paths", nargs="*")
    ap.add_argument("--compare", nargs=2, metavar=("OLD_DIR", "NEW_DIR"))
    a = ap.parse_args()
    if a.compare:
        old, new = a.compare
        names = sorted(n for n in os.listdir(new) if n.endswith(".nbt") and os.path.exists(os.path.join(old, n)))
        print("%-28s " % "structure" + " ".join("%17s" % k for k in KEYS))
        for n in names:
            mo, mn = measure(os.path.join(old, n)), measure(os.path.join(new, n))
            print("%-28s " % n[:-4] + " ".join("%17s" % ("%d->%d" % (mo[k], mn[k])) for k in KEYS))
        return
    print("%-28s " % "structure" + " ".join("%14s" % k for k in KEYS))
    for p in a.paths:
        m = measure(p)
        print("%-28s " % os.path.basename(p)[:-4] + " ".join("%14d" % m[k] for k in KEYS))


if __name__ == "__main__":
    main()
