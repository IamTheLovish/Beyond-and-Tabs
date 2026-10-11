#!/usr/bin/env python3
"""Finds cells of a building structure a 1-wide, 2-tall ground unit can stand in but never walk out of.

Usage: python3 tools/audit_structure_pockets.py [names...]   (default: every structure in data/)

This is the quick offline twin of the structure_has_no_unit_pockets GameTest (gametest/SkirmishGameTests.java): the
test runs the real WalkabilityBuilder / GridNeighbors rules on a stamped structure in CI, this approximates them by
block NAME so a structure can be checked before a ~10 minute CI round trip. The structure is stamped on a flat
floor with a margin of open ground; every walkable cell in or over the footprint is flood-filled with the grid's
moves (8 flat, 4 cardinal step-ups, 8 step-downs, multi-block falls) and must reach the margin ring; the
centre-walked-down default spawn point too.

Approximations (the GameTest is the truth): "solid" and "body-blocking" are decided from the block name, thin or
partial blocks are passable or standable from a short list, and glass panes / iron bars count as non-blocking (a
1-wide unit squeezes past a single pane line; a cross of them is rare in these structures).
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from preview_structure import load_nbt   # noqa: E402

STRUCT_DIR = "bar/src/main/resources/data/reignofnether/structures"
MARGIN = 3
MAX_FALL = 3   # PathfinderConfig.MAX_FALL_DROP
# pockets this low are where a unit a building was finished over, or an unstick rescue, lands
GROUND_POCKET_MAX_Y = 2

# ---- block classification by name -----------------------------------------------------------------------------------
PASSABLE_SUFFIXES = ("_carpet", "_torch", "_button", "_pressure_plate", "_sign", "_banner", "_sapling", "_coral",
                     "_coral_fan", "_coral_wall_fan", "_rail", "_head", "_skull")
PASSABLE_NAMES = {"air", "cave_air", "void_air", "torch",  "soul_torch", "redstone_torch",
                   "lever", "tripwire", "tripwire_hook", "string", "rail", "ladder",
                  "vine", "glow_lichen", "sculk_vein", "grass", "tall_grass", "fern", "large_fern", "dead_bush",
                  "seagrass", "tall_seagrass", "kelp", "kelp_plant", "sweet_berry_bush", "sugar_cane", "bamboo_sapling",
                   "cobweb", "snow", "lily_pad", "light", "structure_void", "spore_blossom",
                  "hanging_roots", "moss_carpet", "pink_petals", "small_dripleaf", "big_dripleaf_stem", "nether_sprouts",
                  "crimson_roots", "warped_roots", "twisting_vines", "weeping_vines", "twisting_vines_plant",
                  "weeping_vines_plant", "cave_vines", "cave_vines_plant", "wheat", "carrots", "potatoes", "beetroots",
                  "nether_wart", "attached_pumpkin_stem", "pumpkin_stem", "melon_stem", "fire", "soul_fire",
                  "redstone_wire", "repeater", "comparator", "scaffolding", "powder_snow", "cocoa", "frogspawn",
                   
                  
                   "mangrove_propagule",  "water", "lava"}
PASSABLE_CONTAINS = ("flower", "tulip", "orchid", "allium", "azure_bluet", "dandelion", "poppy", "cornflower",
                     "lily_of_the_valley", "wither_rose", "potted_", "_mushroom", "sunflower", "lilac", "rose_bush",
                     "peony", "torchflower", "pitcher")
# fences, walls and closed gates: never standable, never passable (WalkabilityBuilder.isFenceLike)
FENCE_SUFFIXES = ("_fence", "_wall")
PIT_NAMES = {"cauldron", "water_cauldron", "lava_cauldron", "powder_snow_cauldron", "composter"}
# the floor-of-fire rule: standable but unpleasant (finite cost) - count as floor
FIRE_FLOORS = {"magma_block", "campfire", "soul_campfire"}
# bushes vanilla calls non-solid that block the body all the same (blocksBody)
BODY_BLOCKING_NON_SOLID = {"azalea", "flowering_azalea", "big_dripleaf"}
# anything with a collision box through the middle of the cell stops a unit's body whatever its size, because units
# walk cell centre to cell centre: lanterns, chains, end rods, flower pots, candles, bars and panes
# (WalkabilityBuilder.blocksBody). Not in PASSABLE_NAMES, so they block.
# a floor whose top is this far below the cell top (a bottom slab) can't be stepped off onto a full block one cell
# up: 1.5 blocks of rise is more than a mob's jump (~1.25)
LOW_FLOOR_SUFFIXES = ("_slab",)


def base(name):
    return name.split(":", 1)[1] if ":" in name else name


def is_fence_like(name, props):
    n = base(name)
    if n.endswith("_fence_gate"):
        return props.get("open", "false") != "true"
    return n.endswith(FENCE_SUFFIXES) or n in ("sculk_catalyst", "sculk_shrieker")


def is_leaf(name):
    return base(name).endswith("_leaves")


def is_passable(name, props):
    n = base(name)
    if is_fence_like(name, props) or is_leaf(name) or n in BODY_BLOCKING_NON_SOLID:
        return False
    if n in PASSABLE_NAMES or n.endswith(PASSABLE_SUFFIXES):
        return True
    if any(c in n for c in PASSABLE_CONTAINS):
        return True
    if n.endswith("_trapdoor") and props.get("open", "false") == "true":
        return True
    if n.endswith("_door"):
        return props.get("open", "false") == "true"
    return False


def supports(name, props):
    """A floor block the unit stands on (solid, not fence-like / leaves / cauldron / water)."""
    n = base(name)
    if is_passable(name, props) or is_fence_like(name, props) or is_leaf(name) or n in PIT_NAMES:
        return False
    if n in BODY_BLOCKING_NON_SOLID:
        return False   # vanilla: not solid, so no floor support
    return True


def lava(name):
    return base(name) in ("lava", "lava_cauldron")


class Pad:
    """The structure stamped on a flat floor of 'stone' at y=-1 (so the structure's y=0 is the ground layer, which
    for every RoN blueprint holds its foundation), air everywhere else, a MARGIN of open ground around."""

    def __init__(self, nbt):
        self.sx, self.sy, self.sz = nbt["size"]
        pal = [(e["Name"], e.get("Properties", {})) for e in nbt["palette"]]
        self.blocks = {}
        for b in nbt["blocks"]:
            x, y, z = b["pos"]
            self.blocks[(x, y, z)] = pal[b["state"]]

    def at(self, x, y, z):
        b = self.blocks.get((x, y, z))
        if b is not None:
            return b
        # the ground: the structure's y=0 layer is its foundation and sits IN the ground layer (a placed building's
        # minCorner is the ground block), so the open ground around it is at y=0 too, over bedrock-ish y=-1
        if y == -1 or (y == 0 and (x < 0 or z < 0 or x >= self.sx or z >= self.sz)):
            return ("minecraft:stone", {})
        return ("minecraft:air", {})

    def kind(self, x, y, z):
        """WalkabilityBuilder.classify: None = blocked, 'lava', or 'ok' (land/water/fire/tidepool)."""
        feet, head, floor = self.at(x, y, z), self.at(x, y + 1, z), self.at(x, y - 1, z)
        if not is_passable(*feet) or not is_passable(*head):
            return None
        if lava(feet[0]):
            return "lava"
        if base(feet[0]) == "water":
            return "ok"
        if supports(*floor):
            return "ok"
        if base(floor[0]) == "water":
            return "ok"
        return None

    def walkable(self, x, y, z):
        return self.kind(x, y, z) == "ok"

    def door_beside(self, x, y, z):
        """A door in a cardinal neighbour at feet or head height: the pocket is a room."""
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for dy in (0, 1):
                if base(self.at(x + dx, y + dy, z + dz)[0]).endswith("_door"):
                    return True
        return False

    def floor_top(self, x, y, z):
        """Height of the standing surface of a walkable cell above the cell's bottom: 0 on a full floor, -0.5 on a
        bottom slab (the unit's feet sit inside the floor cell), 0 when the unit floats in water."""
        name, props = self.at(x, y - 1, z)
        if base(name).endswith(LOW_FLOOR_SUFFIXES) and props.get("type", "bottom") == "bottom":
            return -0.5
        return 0.0

    def solid(self, x, y, z):
        return not is_passable(*self.at(x, y, z))


DX = [1, -1, 0, 0, 1, -1, 1, -1]
DZ = [0, 0, 1, -1, 1, -1, -1, 1]


def neighbours(pad, x, y, z):
    """The grid's moves for a 1-wide, 2-tall unit (GridAStar main loop + fall pass), yielding target cells."""
    for d in range(8):
        nx, nz = x + DX[d], z + DZ[d]
        diag = DX[d] != 0 and DZ[d] != 0
        for ny in (y, y + 1, y - 1):
            if ny == y + 1 and diag:
                continue
            if not pad.walkable(nx, ny, nz):
                continue
            if ny == y + 1 and pad.solid(x, y + 2, z):
                continue   # riseBlocked
            if ny == y + 1 and 1 + pad.floor_top(nx, ny, nz) - pad.floor_top(x, y, z) > 1.25:
                continue   # a jump a mob can't make (off a bottom slab onto a full block one cell up)
            if diag and (not pad.walkable(x + DX[d], ny, z) or not pad.walkable(x, ny, z + DZ[d])):
                continue   # diagonalBlocked
            yield (nx, ny, nz)
        # the fall pass: step off an edge into a clear column and land on the first standable cell below
        if pad.solid(nx, y, nz) or pad.solid(nx, y + 1, nz):
            continue
        if diag and (not pad.walkable(x + DX[d], y, z) or not pad.walkable(x, y, z + DZ[d])):
            continue
        if pad.solid(nx, y - 1, nz) or pad.walkable(nx, y - 1, nz):
            continue
        for drop in range(2, MAX_FALL + 1):
            ly = y - drop
            if pad.solid(nx, ly + 1, nz):
                break
            if pad.walkable(nx, ly, nz):
                yield (nx, ly, nz)
                break


def audit(name, path):
    pad = Pad(load_nbt(path))
    lo_x, hi_x = -MARGIN, pad.sx - 1 + MARGIN
    lo_z, hi_z = -MARGIN, pad.sz - 1 + MARGIN

    def outside(c):
        x, y, z = c
        return x < 0 or z < 0 or x >= pad.sx or z >= pad.sz

    # reverse flood: cells that can reach the outside ring. BFS over inverse edges would need the inverse move set,
    # so instead flood forward from every inside cell with memoisation of "escapes".
    escapes = {}
    behind_door = {}

    def can_escape(start):
        if start in escapes:
            return escapes[start]
        seen = {start}
        stack = [start]
        found = False
        while stack:
            c = stack.pop()
            if outside(c) or escapes.get(c):
                found = True
                break
            x, y, z = c
            for n in neighbours(pad, x, y, z):
                nx, ny, nz = n
                if nx < lo_x or nx > hi_x or nz < lo_z or nz > hi_z or ny < 1 or ny > pad.sy + 1:
                    continue
                if n not in seen:
                    seen.add(n)
                    stack.append(n)
        escapes[start] = found
        if not found:
            # everything reachable from a trapped cell is trapped too (moves are one-way only downwards); a pocket
            # with a door on it is a room the structure's designer closed on purpose, not an accident
            roomy = any(pad.door_beside(*c) for c in seen)
            for c in seen:
                escapes[c] = False
                behind_door[c] = roomy
        return found

    # cells the outside can walk INTO (forward flood from the margin ring at ground level)
    entered = set()
    stack = [(x, 1, z) for x in range(lo_x, hi_x + 1) for z in range(lo_z, hi_z + 1)
             if outside((x, 1, z)) and pad.walkable(x, 1, z)]
    entered.update(stack)
    while stack:
        x, y, z = stack.pop()
        for n in neighbours(pad, x, y, z):
            nx, ny, nz = n
            if nx < lo_x or nx > hi_x or nz < lo_z or nz > hi_z or ny < 1 or ny > pad.sy + 1 or n in entered:
                continue
            entered.add(n)
            stack.append(n)

    one_way, ground, roof, rooms = [], [], [], []
    for x in range(pad.sx):
        for z in range(pad.sz):
            for y in range(1, pad.sy + 1):
                if pad.walkable(x, y, z) and not can_escape((x, y, z)):
                    if (x, y, z) in entered:
                        one_way.append((x, y, z))      # a unit walks or falls in and never gets out
                    elif behind_door.get((x, y, z)):
                        rooms.append((x, y, z))        # a closed room (doors are walls to the grid)
                    elif y <= GROUND_POCKET_MAX_Y:
                        ground.append((x, y, z))       # a unit built over / rescued into it is stuck
                    else:
                        roof.append((x, y, z))         # only reachable by spawning there
    # the default indoor spawn point (ProductionBuilding.getIndoorSpawnPoint): the footprint's centre at mid-height,
    # walked down while the block below is air - so it lands on a roof as readily as on the floor
    cx, cz = pad.sx // 2, pad.sz // 2
    sy = (pad.sy - 1) // 2
    while sy > 0 and base(pad.at(cx, sy - 1, cz)[0]) == "air":
        sy -= 1
    spawn_note = ""
    if not pad.walkable(cx, sy, cz):
        spawn_note = " (centre spawn %d/%d/%d not standable: safeSpawnPoint moves it)" % (cx, sy, cz)
    elif not can_escape((cx, sy, cz)):
        spawn_note = " SPAWN %d/%d/%d IS TRAPPED" % (cx, sy, cz)
    return one_way, ground, roof, rooms, spawn_note


def fmt(cells):
    shown = ", ".join("%d/%d/%d" % c for c in cells[:8])
    return shown + ("" if len(cells) <= 8 else " +%d more" % (len(cells) - 8))


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("-")]
    show_roofs = "--roofs" in sys.argv
    names = args or sorted(f[:-4] for f in os.listdir(STRUCT_DIR) if f.endswith(".nbt"))
    bad = 0
    for name in names:
        if name.startswith("bridge_") or name == "gametest_arena":
            continue
        one_way, ground, roof, rooms, spawn_note = audit(name, os.path.join(STRUCT_DIR, name + ".nbt"))
        if one_way or ground or "TRAPPED" in spawn_note:
            bad += 1
            if one_way:
                print("%-28s %3d one-way pocket cell(s): %s" % (name, len(one_way), fmt(one_way)))
            if ground:
                print("%-28s %3d ground pocket cell(s): %s" % (name, len(ground), fmt(ground)))
        if roof and show_roofs:
            print("%-28s %3d roof-only pocket cell(s): %s" % (name, len(roof), fmt(roof)))
        if rooms and show_roofs:
            print("%-28s %3d closed-room cell(s): %s" % (name, len(rooms), fmt(rooms)))
        if spawn_note and (show_roofs or "TRAPPED" in spawn_note):
            print("%-28s%s" % (name, spawn_note))
    print("%d structure(s) with pockets a unit can get into" % bad)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
