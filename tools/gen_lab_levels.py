#!/usr/bin/env python3
"""Generates the building-level structures (design/building_upgrades.md, design/building_detail.md): each T1 army lab's
Level 2 and Level 3 (<lab>_l2.nbt, <lab>_l3.nbt) and each faction's lab annex (lab_annex<variant>.nbt). Run from the
repo root; writes into BOTH structure trees (assets/ for the client, data/ for the server), like gen_structures.py.

lovish (Oct 12): "upgraded buildings can change in shape or fundamental design ... make upgraded buildings stand out
for the opponent; simulated buildings could get much bigger or have cooler, more intricate designs". So every faction
draws its own L2 and L3 here - new wings, towers and a landmark the L1 doesn't have - instead of a shared crown:

  Sunforged barracks      -> L2 knights' cloister (open domed corner pavilions, an arcaded gate, a taller dome)
                          -> L3 cathedral of arms (a rose-window gable, flying buttresses, a belfry spire);
                             the L1 Training Hall is drawn in tools/lab_barracks.py too
  Gravebound graveyard    -> L2 catacomb gate (two crypt wings with skull niches, a gatehouse)
                          -> L3 bone spire (a chapel with a green rose window, a bone-ribbed tower and spire);
                             the L1 and Overflowing Graveyard are drawn in tools/lab_graveyard.py
  Horde military portal   -> L2 crater forge (the gate's flanks climb into a stepped volcano with a glowing crater)
                          -> L3 the giant's gate (the Stone Giant rises from the crater, hammer raised); the L1 Ember
                             Gate is drawn in tools/lab_portal_military.py too (the Ashborn kit: basalt, tuff, bronze)
  Verdant grove           -> L2 the rooted court (owl and hive towers, root arches, the first canopy tier)
                          -> L3 the elder grove (a layered canopy, a cherry crown, an antler crown); the L1 Stag
                             Glade is drawn in tools/lab_grove.py too
  Tidewrought slipway     -> L2 the fitting-out (a planked hull, the mainmast, a stern castle)
                          -> L3 the launch (sails set, a figurehead, the striped lighthouse); the L1 Slip is drawn
                             in tools/lab_slipway.py too
  Stormcallers academy    -> L2 hall of winds (a double-eaved hall, a roofed torii with jade guardians)
                          -> L3 thousand-lotus pagoda (four curved roofs to a gilded lotus crown);
                             the L1 Lotus Court is drawn in tools/lab_lotus_academy.py
  Brassworks machine shop -> L2 engine works / L3 clockwork foundry; the L1 is drawn in tools/lab_machine_shop.py
  Brassworks works / forge -> L2 workshop (twin smokestacks, a sawtooth north-light roof, pipes)
                          -> L3 engine house (a clock tower over the roof, a gantry crane, a boiler dome)

Rules every level obeys (the GameTest lab_level_structures_ship_and_have_no_pockets is the truth):
- the footprint (x, z size) never changes - BuildingLevels has no larger footprint, and an in-place upgrade must not
  collide with its neighbours; a level may grow taller;
- the upgrade only ADDS or REPLACES blocks: BuildingPlacement.changeStructure / refreshBlocks place what is missing
  and never remove anything, so every non-air position of the level below stays non-air (it may become a different
  block - the upgrade overwrites it - e.g. a fletching table in a new gateway becomes a carpet). Canvas.build()
  asserts it, for L1 -> L2 and for L2 -> L3 (a saved L3 restores from L1, which the chain covers too);
- pocket-free: wings and towers are solid masses or open pavilions, gateways stay open, nothing ground units can climb
  onto leads to a drop they can't take (check with tools/audit_structure_pockets.py);
- vanilla blocks only, and blocks whose state stays as placed (RoN's isPlaced compares states): leaves get their true
  log distance, no growing plants, no hay under campfires.
The Graveyard's levels grow on the Overflowing Graveyard (a levelled graveyard overflows too).
Base blocks' block-entity NBT is dropped (the only one, the Barracks' bell, needs none: the game makes a fresh one).
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from preview_structure import load_nbt   # noqa: E402
from gen_structures import Structure, OUT, OUT_DATA   # noqa: E402

BASE_DIR = "bar/src/main/resources/data/reignofnether/structures"


def b(name, **props):
    return (name, props)


AIR = b("air")
DRY = dict(waterlogged="false")


def stair(name, facing, half="bottom"):
    return b(name, facing=facing, half=half, shape="straight", waterlogged="false")


def slab(name, kind="bottom"):
    return b(name, type=kind, waterlogged="false")


def lantern(hanging=False, soul=False):
    return b("soul_lantern" if soul else "lantern", hanging="true" if hanging else "false", waterlogged="false")


def wall_torch(facing, soul=False):
    return b("soul_wall_torch" if soul else "wall_torch", facing=facing)


def wall_banner(colour, facing):
    return b(colour + "_wall_banner", facing=facing)


def banner(colour, rotation):
    return b(colour + "_banner", rotation=str(rotation))


def trapdoor(name, facing, half="bottom", open_=False):
    return b(name, facing=facing, half=half, open="true" if open_ else "false", powered="false", waterlogged="false")


def is_air(nm):
    return nm.split(":")[-1] in ("air", "cave_air", "void_air", "structure_void")


def load_base(name):
    nbt = load_nbt(os.path.join(BASE_DIR, name + ".nbt"))
    pal = nbt["palette"]
    blocks = {}
    for blk in nbt["blocks"]:
        st = pal[blk["state"]]
        nm = st["Name"].split(":")[-1]
        blocks[tuple(blk["pos"])] = (nm, dict(st.get("Properties", {})))
    return list(nbt["size"]), blocks


LOGS = ("_log", "_wood", "_stem", "_hyphae")
FACING = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0)}
THIN = ("_fence", "_wall", "_pane", "iron_bars", "chain", "_trapdoor", "_carpet", "lantern", "torch", "_banner",
        "_door", "end_rod", "lightning_rod", "_skull", "_head", "campfire", "bell", "air", "_button", "_plate",
        "grass", "fern", "flower", "_coral", "_coral_fan", "_roots", "rose", "bluet", "daisy", "allium", "cornflower")


def sturdy(blk, face):
    """Roughly vanilla's isFaceSturdy: can this block hold a lantern / torch / banner on that face ('up', 'down' or a
    side)? Fences, walls and chains hold a lantern on top or under them (the centre)."""
    nm, props = blk
    if nm.endswith("_slab"):
        t = props.get("type", "bottom")
        return t == "double" or (face == "up" and t == "top") or (face == "down" and t == "bottom")
    if nm.endswith("_stairs"):
        h = props.get("half", "bottom")
        return (face == "down" and h == "bottom") or (face == "up" and h == "top")
    if nm.endswith(("_fence", "_wall", "chain")) and nm != "cobblestone_wall_x":
        return face in ("up", "down")
    if nm.endswith("_leaves") or nm.endswith("glass"):
        return True
    return not any(nm.endswith(t) or t in nm for t in THIN)


class Canvas:
    """A level drawn over the level below it. `below` is every position that must stay non-air."""

    def __init__(self, size, blocks, ymax=24):
        self.sx, self.sy0, self.sz = size
        self.blocks = dict(blocks)
        self.below = {p for p, (nm, _) in blocks.items() if not is_air(nm)}
        self.below_blocks = dict(blocks)
        self.ymax = ymax

    @classmethod
    def from_structure(cls, name):
        size, blocks = load_base(name)
        return cls(size, blocks)

    def next_level(self):
        return Canvas((self.sx, self.height(), self.sz), self.blocks, self.ymax)

    def set(self, x, y, z, blk):
        assert 0 <= x < self.sx and 0 <= z < self.sz and 0 <= y < self.ymax, (x, y, z, blk)
        self.blocks[(x, y, z)] = blk

    def get(self, x, y, z):
        return self.blocks.get((x, y, z), AIR)

    def name(self, x, y, z):
        return self.get(x, y, z)[0]

    def empty(self, x, y, z):
        return is_air(self.name(x, y, z))

    def put(self, x, y, z, blk):
        """Set only where nothing stands yet (decoration that must not overwrite the building)."""
        if self.empty(x, y, z):
            self.set(x, y, z, blk)

    def fill(self, x1, y1, z1, x2, y2, z2, blk):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.set(x, y, z, blk)

    def height(self):
        top = max([y for (x, y, z), (nm, _) in self.blocks.items() if not is_air(nm)] + [0])
        return max(self.sy0, top + 1)

    def fix_leaves(self):
        """Leaves carry their real distance to a log (vanilla recomputes it on every neighbour update, and RoN's
        isPlaced compares block states: a wrong distance reads as a missing block)."""
        dist = {}
        frontier = [p for p, (nm, _) in self.blocks.items() if nm.endswith(LOGS)]
        for p in frontier:
            dist[p] = 0
        d = 0
        while frontier and d < 7:
            d += 1
            nxt = []
            for (x, y, z) in frontier:
                for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                    q = (x + dx, y + dy, z + dz)
                    if q in dist:
                        continue
                    nm = self.blocks.get(q, AIR)[0]
                    if nm.endswith("_leaves"):
                        dist[q] = d
                        nxt.append(q)
            frontier = nxt
        for p, (nm, props) in list(self.blocks.items()):
            if nm.endswith("_leaves"):
                props = dict(props)
                props["distance"] = str(dist.get(p, 7))
                props["persistent"] = "true"
                props.setdefault("waterlogged", "false")
                self.blocks[p] = (nm, props)

    def unsupported(self):
        """Attachments that would pop off when placed (block updates): standing lanterns, torches, banners and
        carpets need a floor, hanging lanterns a ceiling, wall torches / banners / bells a wall."""
        bad = []
        for (x, y, z), (nm, props) in self.blocks.items():
            if (x, y, z) in self.below and self.below_blocks.get((x, y, z)) == (nm, props):
                continue      # the level below's own blocks stand as they did
            need = None
            if nm in ("lantern", "soul_lantern"):
                need = [((0, 1, 0), "down")] if props.get("hanging") == "true" else [((0, -1, 0), "up")]
            elif nm in ("torch", "soul_torch") or (nm.endswith("_banner") and "_wall_" not in nm) \
                    or nm.endswith("_carpet") or nm.endswith("_pressure_plate"):
                need = [((0, -1, 0), "up")]
            elif nm.endswith("_wall_torch") or nm.endswith("_wall_banner"):
                dx, dy, dz = FACING[props["facing"]]
                need = [((-dx, 0, -dz), "side")]
            elif nm == "bell":
                a = props.get("attachment")
                if a == "ceiling":
                    need = [((0, 1, 0), "down")]
                elif a == "floor":
                    need = [((0, -1, 0), "up")]
                elif a == "single_wall":      # vanilla: a wall bell hangs from the block it FACES
                    dx, dy, dz = FACING[props["facing"]]
                    need = [((dx, 0, dz), "side")]
            for (dx, dy, dz), face in need or ():
                q = self.get(x + dx, y + dy, z + dz)
                if nm.endswith("_carpet") or nm.endswith("_wall_banner"):   # any solid block holds these
                    if is_air(q[0]) or not sturdy(q, "up") and not q[0].endswith(("_stairs", "_slab")):
                        bad.append((x, y, z, nm))
                elif not sturdy(q, face):
                    bad.append((x, y, z, nm, q[0]))
        return bad

    def build(self):
        self.fix_leaves()
        lost = [p for p in self.below if is_air(self.blocks.get(p, AIR)[0])]
        assert not lost, "the level below has blocks this level would leave behind: %s" % sorted(lost)[:8]
        loose = self.unsupported()
        assert not loose, "these would pop off: %s" % loose
        h = self.height()
        s = Structure(self.sx, h, self.sz)
        for (x, y, z), (nm, props) in sorted(self.blocks.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
            if y < h:
                s.set(x, y, z, nm, **props)
        return s


# ---------------------------------------------------------------------------------------------- shared shapes
def gable_x(c, x1, x2, z1, z2, y, roof, full, cap=None):
    """A gable roof over [x1..x2] x [z1..z2] with its ridge running along z: stairs climb in from both x sides until
    they meet; the gable ends (z1 and z2 rows) are filled with `full` under the stairs."""
    lo, hi, yy = x1, x2, y
    while lo <= hi:
        for z in range(z1, z2 + 1):
            if lo == hi:
                c.set(lo, yy, z, cap or slab(roof.replace("_stairs", "_slab")))
            else:
                c.set(lo, yy, z, stair(roof, "east"))
                c.set(hi, yy, z, stair(roof, "west"))
        # gable infill one layer under the next step
        lo += 1
        hi -= 1
        if lo <= hi:
            for x in range(lo, hi + 1):
                for z in (z1, z2):
                    c.set(x, yy, z, full)
        yy += 1
    return yy


def gable_z(c, x1, x2, z1, z2, y, roof, full, cap=None):
    """As gable_x with the ridge running along x (stairs climb in from the z sides)."""
    lo, hi, yy = z1, z2, y
    while lo <= hi:
        for x in range(x1, x2 + 1):
            if lo == hi:
                c.set(x, yy, lo, cap or slab(roof.replace("_stairs", "_slab")))
            else:
                c.set(x, yy, lo, stair(roof, "south"))
                c.set(x, yy, hi, stair(roof, "north"))
        lo += 1
        hi -= 1
        if lo <= hi:
            for z in range(lo, hi + 1):
                for x in (x1, x2):
                    c.set(x, yy, z, full)
        yy += 1
    return yy


def hip(c, x1, x2, z1, z2, y, roof, cap):
    """A hip roof: rings of stairs facing the middle, a cap where they meet. Returns the cap's height."""
    while x1 < x2 and z1 < z2:
        for x in range(x1, x2 + 1):
            c.set(x, y, z1, stair(roof, "south"))
            c.set(x, y, z2, stair(roof, "north"))
        for z in range(z1 + 1, z2):
            c.set(x1, y, z, stair(roof, "east"))
            c.set(x2, y, z, stair(roof, "west"))
        x1, x2, z1, z2, y = x1 + 1, x2 - 1, z1 + 1, z2 - 1, y + 1
    if x1 <= x2 and z1 <= z2:
        c.fill(x1, y, z1, x2, y, z2, cap)
        return y
    return y - 1


def solid_tower(c, x1, z1, x2, z2, y1, y2, blk, corner=None):
    c.fill(x1, y1, z1, x2, y2, z2, blk)
    if corner:
        for (x, z) in ((x1, z1), (x2, z1), (x1, z2), (x2, z2)):
            c.fill(x, y1, z, x, y2, z, corner)


def crenels(c, x1, z1, x2, z2, y, blk):
    """Every other block round a rectangle's rim (battlements)."""
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            if (x in (x1, x2) or z in (z1, z2)) and (x + z) % 2 == 0:
                c.set(x, y, z, blk)


# ---------------------------------------------------------------------------------------------- Sunforged
SUN_BANNER = "yellow"


# the barracks' levels live in tools/lab_barracks.py


# ---------------------------------------------------------------------------------------------- Gravebound
# the graveyard's levels live in tools/lab_graveyard.py


# ---------------------------------------------------------------------------------------------- Horde
# the military portal's levels live in tools/lab_portal_military.py


# ---------------------------------------------------------------------------------------------- Verdant
# the grove's levels live in tools/lab_grove.py


# ---------------------------------------------------------------------------------------------- Tidewrought
# the slipway's levels live in tools/lab_slipway.py


# ---------------------------------------------------------------------------------------------- Stormcallers
# the academy's levels live in tools/lab_lotus_academy.py


# ---------------------------------------------------------------------------------------------- Brassworks
def brass_lab(base):
    """Machine shop 9x9 / assembly works 11x11: open pavilions under a brass roof. L2 adds twin smokestacks, a
    sawtooth north-light roof and steam pipes; L3 a clock tower over the roof, a gantry crane and a boiler dome."""
    c = Canvas.from_structure(base)
    sx, sz = c.sx, c.sz
    # the roof's height: the highest y of the roof plate (the layer that covers most of the footprint)
    counts = {}
    for (x, y, z), (nm, _) in c.blocks.items():
        if not is_air(nm) and y > 0:
            counts[y] = counts.get(y, 0) + 1
    roof_y = max(counts, key=lambda y: counts[y])
    R = roof_y
    BRICK, SOOT = b("bricks"), b("polished_blackstone_bricks")
    BRASS, BRASS_D = b("waxed_cut_copper"), b("waxed_exposed_cut_copper")
    GLASS = b("orange_stained_glass")
    # smokestacks on the two back corners, rising 5 over the roof, banded in brass, a campfire's smoke on top
    stacks = [(0, sz - 1), (sx - 1, sz - 1)]
    for (x, z) in stacks:
        for y in range(1, R + 6):
            if (x, y, z) in c.below and y <= R:
                continue      # the corner post stays where it is (it may be the base's own chimney)
            c.set(x, y, z, BRICK if y % 3 else BRASS)
        c.set(x, R + 6, z, b("campfire", lit="true", facing="south", signal_fire="false", waterlogged="false"))
    # the sawtooth roof: three north-light ridges across the roof (glass faces north, brass slopes south)
    for i, z0 in enumerate(range(1, sz - 2, 3)):
        for x in range(1, sx - 1):
            c.set(x, R + 1, z0, GLASS)
            c.set(x, R + 1, z0 + 1, stair("waxed_cut_copper_stairs", "north"))
            if x % 4 == 2:
                c.set(x, R + 2, z0, slab("waxed_cut_copper_slab"))
    # steam pipes along the west eave (the camera's side): copper slabs with lightning-rod valves
    for z in range(1, sz - 1):
        c.put(0, R + 1, z, slab("waxed_exposed_cut_copper_slab") if z % 3 else b("lightning_rod", facing="up", powered="false", waterlogged="false"))
    # lanterns hung under the eave at the front corners and the soot-black hoardings
    for x in (1, sx - 2):
        c.set(x, R - 1, 0, lantern(hanging=True)) if c.empty(x, R - 1, 0) else None
    l2 = c.build()

    # ---- L3: engine house
    c = c.next_level()
    cx, cz = sx // 2, sz // 2
    # the clock tower: 3x3, soot-black with brass corners, a clock face of orange glass round a gold hub on the
    # west and south faces (the camera's side), a brass hip roof with a lightning rod
    for y in range(R + 1, R + 7):
        for x in range(cx - 1, cx + 2):
            for z in range(cz - 1, cz + 2):
                corner = x != cx and z != cz
                c.set(x, y, z, BRASS if corner else SOOT)
    for (x, z) in ((cx - 1, cz), (cx, cz + 1)):
        c.set(x, R + 5, z, b("gold_block"))
    c.set(cx - 1, R + 4, cz, GLASS)
    c.set(cx - 1, R + 6, cz, GLASS)
    c.set(cx, R + 4, cz + 1, GLASS)
    c.set(cx, R + 6, cz + 1, GLASS)
    hip(c, cx - 1, cx + 1, cz - 1, cz + 1, R + 7, "waxed_cut_copper_stairs", BRASS)
    c.set(cx, R + 9, cz, b("lightning_rod", facing="up", powered="false", waterlogged="false"))
    c.set(cx - 2, R + 6, cz, b("bell", attachment="single_wall", facing="east", powered="false"))
    # the boiler dome on the front of the roof: a squat brass drum with a soot cap and a pressure valve
    for x in range(cx - 1, cx + 2):
        for z in range(0, 2):
            c.set(x, R + 1, z, BRASS_D)
            c.set(x, R + 2, z, BRASS_D if x == cx else BRASS)
    c.set(cx, R + 3, 0, SOOT)
    c.set(cx, R + 3, 1, SOOT)
    c.set(cx, R + 4, 1, b("lightning_rod", facing="up", powered="false", waterlogged="false"))
    # the gantry crane: a soot beam across the back between the smokestacks, a chain and a hook
    for x in range(1, sx - 1):
        c.set(x, R + 4, sz - 1, b("polished_blackstone_brick_slab", type="top", waterlogged="false") if x % 2 else SOOT)
    c.set(2, R + 3, sz - 1, b("chain", axis="y"))
    c.set(2, R + 2, sz - 1, b("grindstone", face="floor", facing="north"))
    # the stacks grow taller with furnace-glow bands
    for (x, z) in stacks:
        c.set(x, R + 6, z, b("magma_block"))
        c.set(x, R + 7, z, BRICK)
        c.set(x, R + 8, z, b("campfire", lit="true", facing="south", signal_fire="false", waterlogged="false"))
    for z in (2, sz - 3):
        c.set(0, R, z, lantern(hanging=False)) if c.empty(0, R, z) else None
    return l2, c.build()


LABS = {
    # lab structure name -> builder returning (L2, L3)
    "barracks": lambda: __import__("lab_barracks").levels(),   # Training Hall / Knights' Cloister / Cathedral of Arms (Oct 19)
    "graveyard": lambda: __import__("lab_graveyard").levels(),   # the Ossuary Yard and its levels (Oct 19)
    "portal_military": lambda: __import__("lab_portal_military").levels(),   # Ember Gate / Crater Forge / The Giant's Gate (Oct 19)
    "grove": lambda: __import__("lab_grove").levels(),   # The Stag Glade / The Rooted Court / The Elder Grove (Oct 19)
    "slipway": lambda: __import__("lab_slipway").levels(),   # The Slip / The Fitting-Out / The Launch (Oct 19)
    "lotus_academy": lambda: __import__("lab_lotus_academy").levels(),   # Lotus Court / Hall of Winds / Thousand-Lotus Pagoda (Oct 19)
    "machine_shop": lambda: __import__("lab_machine_shop").levels(),   # Machine Shop / Engine Works / Clockwork Foundry (Oct 19)
    "assembly_works": lambda: brass_lab("assembly_works"),   # the Brassworks T2 lab takes the same levels (slice 2)
    "great_forge": lambda: brass_lab("great_forge"),         # and the Brassworks T3 lab (slice 4)
}


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
    only = set(sys.argv[1:])
    built = {}
    for lab, builder in LABS.items():
        if only and lab not in only:
            continue
        built[lab] = builder()
    for out in (OUT, OUT_DATA):
        os.makedirs(out, exist_ok=True)
        for lab, (l2, l3) in built.items():
            l2.write(f"{out}/{lab}_l2.nbt")
            l3.write(f"{out}/{lab}_l3.nbt")
        if "barracks" in built:    # the Training Hall redraws the L1
            import lab_barracks
            lab_barracks.base().write(f"{out}/barracks.nbt")
        if "machine_shop" in built:   # the Machine Shop redraws the L1
            import lab_machine_shop
            lab_machine_shop.base().write(f"{out}/machine_shop.nbt")
        if "portal_military" in built:   # the Ember Gate redraws the L1
            import lab_portal_military
            lab_portal_military.base().write(f"{out}/portal_military.nbt")
        if "lotus_academy" in built:   # the Lotus Court redraws the L1
            import lab_lotus_academy
            lab_lotus_academy.base().write(f"{out}/lotus_academy.nbt")
        if "graveyard" in built:   # the Ossuary Yard redraws the L1 and its overflowing state too
            import lab_graveyard
            g, og = lab_graveyard.bases()
            g.write(f"{out}/graveyard.nbt")
            og.write(f"{out}/overflowing_graveyard.nbt")
        if not only:
            for variant, p in ANNEXES.items():
                annex(p).write(f"{out}/lab_annex{variant}.nbt")
    print("wrote", len(built) * 2, "lab levels into", OUT, "and", OUT_DATA)
