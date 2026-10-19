#!/usr/bin/env python3
"""Generates the building-level structures (design/building_upgrades.md, design/building_detail.md): each T1 army lab's
Level 2 and Level 3 (<lab>_l2.nbt, <lab>_l3.nbt) and each faction's lab annex (lab_annex<variant>.nbt). Run from the
repo root; writes into BOTH structure trees (assets/ for the client, data/ for the server), like gen_structures.py.

lovish (Oct 12): "upgraded buildings can change in shape or fundamental design ... make upgraded buildings stand out
for the opponent; simulated buildings could get much bigger or have cooler, more intricate designs". So every faction
draws its own L2 and L3 here - new wings, towers and a landmark the L1 doesn't have - instead of a shared crown:

  Sunforged barracks      -> L2 fortified keep (corner towers, curtain walls, gatehouse, banners)
                          -> L3 cathedral-barracks (a quartz chapel with a rose window and a 3x3 bell tower)
  Gravebound graveyard    -> L2 catacomb gate (two crypt wings with skull niches, a gatehouse)
                          -> L3 bone spire (a chapel with a green rose window, a bone-ribbed tower and spire);
                             the L1 and Overflowing Graveyard are drawn in tools/lab_graveyard.py
  Horde military portal   -> L2 war camp (two spiked bastions flanking the portal, braziers, trophies)
                          -> L3 war-fortress (horned towers and a gilded crown over the portal)
  Verdant grove           -> L2 living hall (a third tree, a canopy bridge, a root arch, lanterns)
                          -> L3 great tree (the back tree becomes a towering elder with a wide crown)
  Tidewrought slipway     -> L2 a hull on the slip (keel, deck, a mast with a sail, the crane hoisting cargo)
                          -> L3 war galleon (two masts, gun ports, a stern castle and a lighthouse crane)
  Stormcallers academy    -> L2 pagoda hall (a jade-roofed hall at the back, a two-tier gate)
                          -> L3 jade pagoda (a three-tier tower with a lightning-rod finial, drum towers)
  Brassworks shop / works -> L2 workshop (twin smokestacks, a sawtooth north-light roof, pipes)
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


def sun_barracks():
    """Barracks 10x8: L2 a fortified keep, L3 a cathedral-barracks. Front (gate) faces -z; the RTS camera sees the
    back (+z) and the west (-x) faces, so the rose window, the bell tower and most banners are on those faces."""
    c = Canvas.from_structure("barracks")
    SB, QB, QP = b("stone_bricks"), b("quartz_bricks"), b("quartz_pillar", axis="y")
    wall_top = b("stone_brick_wall", east="none", north="none", south="none", up="true", west="none", waterlogged="false")
    # four 2x2 corner towers: stone brick, a white quartz band (the Sunforged plate), battlements
    towers = [(1, 1), (7, 1), (1, 5), (7, 5)]
    for (x, z) in towers:
        solid_tower(c, x, z, x + 1, z + 1, 1, 3, SB)
        c.fill(x, 4, z, x + 1, 4, z + 1, QB)
        c.set(x, 5, z, wall_top)
        c.set(x + 1, 5, z + 1, wall_top)
        c.set(x + 1, 5, z, slab("smooth_quartz_slab"))
        c.set(x, 5, z + 1, slab("smooth_quartz_slab"))
    # curtain walls between the towers (the old bell post and target dummies are walled in)
    for z in (3, 4):
        c.fill(1, 1, z, 1, 3, z, SB)
        c.fill(8, 1, z, 8, 3, z, SB)
    c.set(1, 4, 3, wall_top)
    c.set(1, 4, 4, slab("smooth_quartz_slab"))
    c.set(8, 4, 3, wall_top)                      # was a stair of the watch post
    c.set(8, 5, 4, wall_top)
    c.set(8, 6, 4, b("polished_andesite"))
    c.set(8, 7, 4, lantern())                     # the watch post keeps its light
    c.set(1, 4, 5, QB)                            # the old bell roof's trapdoor, now the tower band
    for x in range(3, 7):
        c.fill(x, 1, 6, x, 2, 6, SB)
        if x % 2 == 0:
            c.set(x, 3, 6, wall_top)
        else:
            c.set(x, 3, 6, slab("stone_brick_slab"))
    c.set(4, 2, 6, b("target", power="0"))       # the archery butts, now set in the wall
    c.set(6, 2, 6, b("target", power="0"))
    # gatehouse on the front: two piers, a lintel, a carpet where the fletching tables stood (the upgrade can only
    # replace them, never clear them). The lintel is a TOP slab, not upside-down stairs: the carpet lifts the floor
    # 1/16 and a 1.95-tall unit then met the stairs' step at 3.0 - the gate was 1.94 high and every unit trained in
    # the keep's yard was shut in (lovish's clip, Oct 14 16:52). A top slab starts at 3.5: 2.44 of head room.
    for x in (3, 6):
        c.fill(x, 1, 1, x, 3, 1, SB)
    c.set(4, 1, 1, b("yellow_carpet"))
    c.set(5, 1, 1, b("yellow_carpet"))
    c.set(4, 3, 1, slab("stone_brick_slab", "top"))
    c.set(5, 3, 1, slab("stone_brick_slab", "top"))
    c.fill(3, 4, 1, 6, 4, 1, QB)
    c.set(3, 5, 1, wall_top)
    c.set(6, 5, 1, wall_top)
    c.set(4, 5, 1, b("gold_block"))
    c.set(5, 5, 1, b("gold_block"))
    c.set(4, 3, 0, wall_banner(SUN_BANNER, "north"))
    c.set(5, 3, 0, wall_banner(SUN_BANNER, "north"))
    # banners on the faces the camera sees, lanterns on every tower top, torches round the yard
    for (x, z) in ((1, 6), (2, 6), (7, 6), (8, 6)):
        c.set(x, 3, z + 1, wall_banner(SUN_BANNER, "south"))
    for z in (1, 2, 5, 6):
        c.set(0, 3, z, wall_banner("white" if z in (2, 5) else SUN_BANNER, "west"))
    for (x, z) in towers:
        c.set(x, 6, z, lantern())
        c.set(x + 1, 6, z + 1, lantern())
    c.set(3, 2, 2, wall_torch("east"))
    c.set(6, 2, 2, wall_torch("west"))
    c.set(2, 2, 3, wall_torch("east"))
    c.set(7, 2, 3, wall_torch("west"))
    c.set(2, 2, 4, b("bell", attachment="single_wall", facing="west", powered="false"))   # the bell, hung on the wall
    l2 = c.build()

    # ---- L3: cathedral-barracks
    c = c.next_level()
    # the chapel over the back of the yard (x3..6, z5..7; the yard's middle stays open for the spawn): quartz walls
    # on a stone plinth, quartz pillar corners, a slate gable roof whose ridge runs front to back
    c.fill(3, 1, 5, 6, 4, 7, QB)
    for (x, z) in ((3, 5), (6, 5), (3, 7), (6, 7)):
        c.fill(x, 1, z, x, 4, z, QP)
    c.fill(3, 1, 5, 6, 1, 7, SB)
    # the west door (the hall's entrance from the yard, faced to the gate) and its chiseled lintel
    c.set(4, 1, 5, b("polished_andesite"))
    c.set(5, 1, 5, b("polished_andesite"))
    c.set(4, 2, 4, wall_torch("north"))
    c.set(5, 2, 4, wall_torch("north"))
    c.set(4, 4, 5, b("chiseled_quartz_block"))
    c.set(5, 4, 5, b("chiseled_quartz_block"))
    for x in (4, 5):
        c.set(x, 2, 5, b("dark_oak_planks"))
        c.set(x, 3, 5, b("dark_oak_trapdoor", facing="north", half="top", open="true", powered="false", waterlogged="false"))
    # the rose window on the back gable (the camera's side): yellow glass round a gold heart
    for (x, y) in ((4, 2), (5, 2), (4, 3), (5, 3)):
        c.set(x, y, 7, b("yellow_stained_glass"))
    c.set(3, 2, 7, b("chiseled_quartz_block"))
    c.set(6, 2, 7, b("chiseled_quartz_block"))
    gable_x(c, 2, 7, 5, 7, 5, "deepslate_tile_stairs", QB, cap=b("gold_block"))
    c.set(4, 6, 7, b("gold_block"))              # the sun disc over the rose window
    c.set(5, 6, 7, b("gold_block"))
    c.set(4, 6, 5, b("gold_block"))
    c.set(5, 6, 5, b("gold_block"))
    c.set(4, 8, 7, b("end_rod", facing="up"))
    c.set(5, 8, 5, b("end_rod", facing="up"))
    # the yard keeps two training dummies by the chapel steps
    for x in (3, 6):
        c.set(x, 1, 4, b("oak_fence", east="false", north="false", south="false", west="false", waterlogged="false"))
        c.set(x, 2, 4, b("carved_pumpkin", facing="north"))
    # the bell tower: 3x3 at the back-west corner (x0..2, z5..7), seen first from the camera
    solid_tower(c, 0, 5, 2, 7, 1, 3, SB)
    solid_tower(c, 0, 5, 2, 7, 4, 7, QB, corner=QP)
    for y in (2, 5):
        c.set(0, y, 6, b("yellow_stained_glass"))   # lancets on the west and south faces
        c.set(1, y, 7, b("yellow_stained_glass"))
    c.fill(0, 8, 5, 2, 8, 7, b("smooth_quartz"))
    for (x, z) in ((0, 5), (2, 5), (0, 7), (2, 7)):
        c.fill(x, 9, z, x, 10, z, QP)
    c.set(1, 10, 6, b("bell", attachment="ceiling", facing="west", powered="false"))
    c.fill(0, 11, 5, 2, 11, 7, b("smooth_quartz"))
    hip(c, 0, 2, 5, 7, 12, "deepslate_tile_stairs", b("gold_block"))
    c.set(1, 14, 6, b("lightning_rod", facing="up", powered="false", waterlogged="false"))
    c.set(0, 9, 6, banner(SUN_BANNER, 4))
    c.set(1, 9, 7, banner(SUN_BANNER, 0))
    for (x, z) in ((0, 5), (2, 5), (0, 7), (2, 7)):
        c.set(x, 12, z, lantern()) if c.empty(x, 12, z) else None
    # the front towers rise into gilded turrets
    for (x, z) in ((1, 1), (7, 1)):
        c.fill(x, 5, z, x + 1, 6, z + 1, QB)
        hip(c, x, x + 1, z, z + 1, 7, "deepslate_tile_stairs", b("gold_block"))
    c.set(1, 5, 0, wall_banner(SUN_BANNER, "north"))
    c.set(8, 5, 0, wall_banner(SUN_BANNER, "north"))
    c.set(0, 5, 1, wall_banner(SUN_BANNER, "west"))
    c.set(0, 5, 2, wall_banner("white", "west"))
    # the back-east tower carries a flying buttress onto the chapel and a lantern
    c.fill(7, 5, 5, 8, 5, 6, QB)
    c.set(7, 6, 6, stair("quartz_stairs", "west"))
    c.set(7, 6, 5, stair("quartz_stairs", "west"))
    c.set(8, 6, 6, lantern())
    return l2, c.build()


# ---------------------------------------------------------------------------------------------- Gravebound
# the graveyard's levels live in tools/lab_graveyard.py


# ---------------------------------------------------------------------------------------------- Horde
def horde_portal():
    """Military portal 8x5 (z0..4): the obsidian frame (x2..5, y1..5, z2) and its opening are never touched; units
    step out of it to the front (z0..1) and the back (z3..4), which stay open. L2 a war camp of two spiked bastions,
    L3 a war-fortress with horned towers and a gilded crown over the portal."""
    c = Canvas.from_structure("portal_military")
    NB, BS = b("nether_bricks"), b("polished_blackstone_bricks")
    spike = b("nether_brick_fence", east="false", north="false", south="false", west="false", waterlogged="false")
    # bastions: x0..1 and x6..7, z1..3 - blackstone bricks with a nether brick crown and fence spikes
    for x0 in (0, 6):
        solid_tower(c, x0, 1, x0 + 1, 3, 1, 3, BS)
        c.fill(x0, 4, 1, x0 + 1, 4, 3, NB)
        for (x, z) in ((x0, 1), (x0 + 1, 3), (x0, 3), (x0 + 1, 1)):
            c.set(x, 5, z, spike)
        c.set(x0, 2, 2, b("gilded_blackstone"))
        c.set(x0 + 1, 2, 2, b("gilded_blackstone"))
    # braziers on the bastions and trophies on the faces the camera sees
    for x in (0, 7):
        c.set(x, 5, 2, b("campfire", lit="true", facing="south", signal_fire="false", waterlogged="false"))
    c.set(1, 5, 2, b("magma_block"))
    c.set(6, 5, 2, b("magma_block"))
    c.set(1, 6, 2, b("piglin_head", rotation="4"))
    c.set(6, 6, 2, b("piglin_head", rotation="12"))
    for x in (0, 1, 6, 7):
        c.set(x, 3, 4, wall_banner("red" if x in (1, 6) else "black", "south"))
    c.set(0, 3, 0, wall_banner("red", "north")) if c.empty(0, 3, 0) else None
    c.set(7, 3, 0, wall_banner("red", "north")) if c.empty(7, 3, 0) else None
    l2 = c.build()

    # ---- L3: war-fortress
    c = c.next_level()
    for x0 in (0, 6):
        solid_tower(c, x0, 1, x0 + 1, 3, 4, 6, BS)
        c.fill(x0, 7, 1, x0 + 1, 7, 3, NB)
        c.set(x0, 5, 2, b("shroomlight"))
        c.set(x0 + 1, 5, 2, b("shroomlight"))
    # horns: bone tusks curving up and out from the tower tops
    for (x, out) in ((0, -1), (7, 1)):
        c.set(x, 8, 1, b("bone_block", axis="y"))
        c.set(x, 9, 1, b("bone_block", axis="y"))
        c.set(x, 10, 1, b("bone_block", axis="y"))
        c.set(x, 8, 3, b("bone_block", axis="y"))
        c.set(x, 9, 3, b("bone_block", axis="y"))
    for x in (1, 6):
        c.set(x, 8, 2, b("campfire", lit="true", facing="south", signal_fire="false", waterlogged="false"))
        c.set(x, 8, 1, spike)
        c.set(x, 8, 3, spike)
    # the crown over the portal: a nether brick lintel on the frame, gilded teeth, a magma eye and a red banner
    c.fill(1, 6, 2, 6, 6, 2, NB)
    c.fill(2, 7, 2, 5, 7, 2, b("gilded_blackstone"))
    c.set(3, 8, 2, b("magma_block"))
    c.set(4, 8, 2, b("magma_block"))
    c.set(2, 8, 2, spike)
    c.set(5, 8, 2, spike)
    c.set(3, 9, 2, b("piglin_head", rotation="0"))
    c.set(4, 9, 2, b("piglin_head", rotation="0"))
    c.set(3, 6, 3, wall_banner("red", "south"))
    c.set(4, 6, 3, wall_banner("red", "south"))
    c.set(3, 6, 1, wall_banner("black", "north"))
    c.set(4, 6, 1, wall_banner("black", "north"))
    for x in (0, 1, 6, 7):
        c.set(x, 6, 4, wall_banner("red", "south")) if c.empty(x, 6, 4) else None
    c.set(0, 6, 0, wall_banner("black", "north"))
    c.set(7, 6, 0, wall_banner("black", "north"))
    return l2, c.build()


# ---------------------------------------------------------------------------------------------- Verdant
def verdant_grove():
    """Grove 9x8: two oaks at the front-sides (x1/x7), a back post (x4, z6) and an azalea canopy at y4..7. L2 grows
    the back post into a third tree, a root arch over the entrance and a canopy bridge with lanterns; L3 turns the
    back tree into a towering elder with a wide flowering crown and owl-perch branches."""
    c = Canvas.from_structure("grove")
    LOG = b("oak_log", axis="y")
    LEAF = b("azalea_leaves")
    FLEAF = b("flowering_azalea_leaves")
    # the back post becomes a trunk to y7 with a crown
    for y in range(1, 8):
        c.set(4, y, 6, LOG)
    for (x, z) in ((3, 6), (5, 6), (4, 5), (4, 7)):
        c.set(x, 1, z, b("oak_wood", axis="y")) if (x, z) != (4, 5) else None
    for x in range(2, 7):
        for z in range(4, 8):
            for y in (7, 8):
                if abs(x - 4) + abs(z - 6) + (y - 7) <= 3 and c.empty(x, y, z):
                    c.set(x, y, z, FLEAF if (x * 3 + z + y) % 4 == 0 else LEAF)
    c.set(4, 9, 6, FLEAF)
    # a root arch over the entrance (front, z0): mangrove roots up the two inner posts, a log lintel at y4
    for x in (3, 5):
        c.set(x, 1, 0, b("mangrove_roots", waterlogged="false"))
        c.set(x, 2, 0, b("mangrove_roots", waterlogged="false"))
        c.set(x, 3, 0, b("oak_log", axis="y"))
    c.set(4, 4, 0, b("oak_log", axis="x"))
    c.set(4, 3, 0, lantern(hanging=True))
    # the canopy bridge between the two front crowns (y5..6, x3..5), lanterns hung under it
    for x in range(3, 6):
        for z in (1, 2, 3):
            c.put(x, 6, z, LEAF if (x + z) % 3 else FLEAF)
    c.set(4, 5, 2, b("oak_log", axis="x"))
    c.set(3, 5, 2, b("oak_log", axis="x"))
    c.set(5, 5, 2, b("oak_log", axis="x"))
    c.set(4, 4, 2, lantern(hanging=True))
    for (x, z) in ((0, 3), (8, 3), (0, 6), (8, 6)):
        c.set(x, 4, z, lantern(hanging=True)) if c.empty(x, 4, z) and not c.empty(x, 5, z) else None
    # the back corner posts grow into silver birch sentinels that break through the canopy with their own small
    # crowns: three crowns over the back skyline instead of one flat canopy
    for x in (1, 7):
        for y in range(1, 10):
            c.set(x, y, 6, b("birch_log", axis="y"))
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                for y in (9, 10):
                    if abs(dx) + abs(dz) + (y - 9) <= 2 and c.empty(x + dx, y, 6 + dz):
                        c.set(x + dx, y, 6 + dz, FLEAF if (dx + dz + y) % 2 else LEAF)
        c.set(x, 11, 6, LEAF)
        c.set(x, 8, 7, lantern(hanging=True)) if c.empty(x, 8, 7) else None
    c.set(4, 10, 6, LOG)
    c.set(4, 11, 6, FLEAF)
    for (x, z) in ((3, 6), (5, 6), (4, 5), (4, 7)):
        c.put(x, 10, z, LEAF)
    # froglights on the trunks' feet on the camera's side, moss carpet on the yard
    c.set(4, 1, 7, b("verdant_froglight", axis="y"))
    for (x, z) in ((3, 4), (5, 4), (4, 3)):
        c.put(x, 1, z, b("moss_carpet"))
    l2 = c.build()

    # ---- L3: the elder tree
    c = c.next_level()
    # a 2x2 trunk (x4..5? no: keep the trunk on the back post and thicken it to the west/back) to y12
    for y in range(1, 13):
        c.set(4, y, 6, LOG)
        c.set(3, y, 6, b("oak_wood", axis="y")) if y <= 9 else None
        c.set(4, y, 7, b("oak_wood", axis="y")) if y <= 10 else None
        c.set(3, y, 7, b("oak_wood", axis="y")) if y <= 6 else None
    # buttress roots
    for (x, z) in ((2, 6), (2, 7), (5, 7)):
        c.set(x, 1, z, b("oak_wood", axis="y"))
    c.set(2, 2, 7, b("mangrove_roots", waterlogged="false"))
    # the crown: a wide flattened dome y10..14 over x0..8 z2..7, leaves with flowering spots and froglights inside
    for x in range(0, 9):
        for z in range(1, 8):
            for y in range(10, 15):
                r = ((x - 4) / 4.4) ** 2 + ((z - 5.5) / 3.6) ** 2 + ((y - 11) / 3.2) ** 2
                if r <= 1.0 and c.empty(x, y, z):
                    c.set(x, y, z, FLEAF if (x * 7 + z * 3 + y) % 5 == 0 else LEAF)
    for (x, y, z) in ((2, 11, 4), (6, 11, 4), (4, 12, 3), (1, 10, 6), (7, 10, 6)):
        c.set(x, y, z, b("verdant_froglight", axis="y"))
    # branches out to the crown
    for (x, y, z, ax) in ((5, 9, 6, "x"), (6, 9, 6, "x"), (2, 9, 6, "x"), (1, 9, 6, "x"), (4, 9, 5, "z"),
                          (4, 9, 4, "z"), (4, 10, 3, "z")):
        c.set(x, y, z, b("oak_log", axis=ax))
    # lanterns hung from the low branches: the court's silver light at night
    for (x, z) in ((1, 6), (6, 6), (4, 4)):
        c.set(x, 8, z, lantern(hanging=True)) if c.empty(x, 8, z) else None
    # owl perch: a fence post with a lantern on the crown's top
    c.set(4, 15, 6, b("oak_fence", east="false", north="false", south="false", west="false", waterlogged="false"))
    c.set(4, 16, 6, lantern())
    return l2, c.build()


# ---------------------------------------------------------------------------------------------- Tidewrought
def tide_slipway():
    """Slipway 9x9: a sandstone slip with a mangrove cradle (x2/x6), a keel of dark prismarine (x4, z2..6), fence
    ribs and plank sides (x3/x5, y2), and a crane mast at (7, z7). L2 lays a hull on the slip with a deck, a mast and
    a sail; L3 makes it a war galleon: two masts, gun ports, a stern castle and a lighthouse lamp on the crane."""
    c = Canvas.from_structure("slipway")
    PL, DK = b("spruce_planks"), b("dark_oak_planks")
    WOOL = b("white_wool")
    # the hull: solid timber over the keel, x3..5, z2..6, y2..3, bowed in at the ends
    c.fill(3, 2, 2, 5, 3, 6, PL)
    c.fill(4, 2, 2, 4, 3, 6, DK)
    c.set(4, 2, 1, stair("dark_oak_stairs", "south"))        # the bow's forefoot
    c.set(4, 3, 1, DK)
    c.set(4, 2, 7, DK)                                       # stern post (was a plank of the cradle)
    c.set(4, 3, 7, DK)
    # the deck at y4: spruce slabs inside a rail of fences
    c.fill(3, 4, 2, 5, 4, 6, slab("spruce_slab"))
    for z in range(2, 7):
        c.set(3, 4, z, b("spruce_fence", east="false", north="false", south="false", west="false", waterlogged="false"))
        c.set(5, 4, z, b("spruce_fence", east="false", north="false", south="false", west="false", waterlogged="false"))
    c.set(4, 4, 1, b("spruce_fence", east="false", north="false", south="false", west="false", waterlogged="false"))
    # a copper band (the waterline) down both sides
    for z in range(2, 7):
        c.set(3, 2, z, b("waxed_cut_copper"))
        c.set(5, 2, z, b("waxed_cut_copper"))
    # the mast and its sail (square sail across the ship at z4)
    for y in range(5, 11):
        c.set(4, y, 4, b("stripped_spruce_log", axis="y"))
    c.set(4, 4, 4, b("stripped_spruce_log", axis="y"))
    for x in range(2, 7):
        c.set(x, 9, 4, b("stripped_spruce_log", axis="x")) if x != 4 else None
        for y in (6, 7, 8):
            if x != 4:
                c.set(x, y, 4, WOOL)
    c.set(4, 11, 4, b("cyan_banner", rotation="4"))
    # the crane hoists a crate of cargo over the slip
    c.set(5, 5, 7, b("chain", axis="y"))
    c.set(5, 4, 7, b("barrel", facing="up", open="false"))
    for x in (0, 8):
        c.set(x, 1, 0, lantern()) if c.empty(x, 1, 0) else None
        c.set(x, 1, 8, lantern()) if c.empty(x, 1, 8) else None
    l2 = c.build()

    # ---- L3: war galleon
    c = c.next_level()
    # stern castle: a raised deck at the back, x3..5 z5..6 y4..5, with lanterns and a cabin window
    c.fill(3, 4, 5, 5, 5, 6, DK)
    c.set(4, 4, 6, b("glass_pane", east="false", north="false", south="false", west="false", waterlogged="false"))
    c.fill(3, 6, 5, 5, 6, 6, slab("dark_oak_slab"))
    c.set(3, 6, 6, lantern())                    # stern lanterns on the castle's back corners
    c.set(5, 6, 6, lantern())
    c.set(4, 4, 7, b("lantern", hanging="false", waterlogged="false"))      # the stern lantern
    # gun ports: dispensers on the hull sides facing out (the camera sees the west battery)
    for z in (3, 5):
        c.set(3, 3, z, b("dispenser", facing="west", triggered="false"))
        c.set(5, 3, z, b("dispenser", facing="east", triggered="false"))
    # the foremast and a second sail
    for y in range(5, 10):
        c.set(4, y, 2, b("stripped_spruce_log", axis="y"))
    c.set(4, 4, 2, b("stripped_spruce_log", axis="y"))
    for x in (3, 5):
        for y in (6, 7):
            c.set(x, y, 2, WOOL)
        c.set(x, 8, 2, b("stripped_spruce_log", axis="x"))
    c.set(4, 10, 2, b("cyan_banner", rotation="4"))
    # the mainmast grows a crow's nest and a teal-striped topsail
    c.set(4, 11, 4, b("stripped_spruce_log", axis="y"))
    c.set(4, 12, 4, b("stripped_spruce_log", axis="y"))
    for (x, z) in ((3, 4), (5, 4), (4, 3), (4, 5)):
        c.set(x, 11, z, b("spruce_trapdoor", facing="north", half="top", open="false", powered="false", waterlogged="false"))
    c.set(4, 13, 4, b("cyan_banner", rotation="4"))
    for x in (2, 3, 5, 6):
        c.set(x, 7, 4, b("cyan_wool"))
    # figurehead: a copper prow and a sea lantern eye
    c.set(4, 4, 0, b("waxed_cut_copper"))
    c.set(4, 5, 0, b("sea_lantern"))
    # the crane mast becomes a lighthouse: a prismarine lamp room and a sea lantern
    c.set(7, 7, 7, b("prismarine_bricks"))
    c.set(7, 8, 7, b("sea_lantern"))
    c.set(7, 9, 7, slab("dark_prismarine_slab"))
    c.set(6, 6, 7, stair("prismarine_brick_stairs", "east", "top")) if c.empty(6, 6, 7) else None
    return l2, c.build()


# ---------------------------------------------------------------------------------------------- Stormcallers
def storm_academy():
    """Lotus academy 9x9: a walled courtyard (stone brick walls x0/x8, z8), a red gate with a jade beam at the front
    (z0), an altar at the back (z7). L2 raises a jade-roofed hall over the back (an open pavilion: units walk under
    it) and a second roof tier on the gate; L3 grows a three-tier pagoda out of the hall and drum towers on the back
    corners."""
    c = Canvas.from_structure("lotus_academy")
    RT, GOLD = b("red_terracotta"), b("gold_block")
    JADE, JADE_ST = b("waxed_oxidized_cut_copper"), "waxed_oxidized_cut_copper_stairs"
    # the hall: red posts at x1/x4/x7 on z5 and the wall, a jade hip roof over x0..8 z4..8 starting at y4
    for x in (1, 4, 7):
        c.fill(x, 1, 5, x, 3, 5, RT)
    for x in (1, 7):
        c.fill(x, 1, 7, x, 3, 7, RT)
    c.fill(1, 4, 5, 7, 4, 7, b("spruce_planks"))
    c.fill(1, 4, 5, 7, 4, 5, b("dark_oak_planks"))        # the painted beam over the hall front
    hip(c, 0, 8, 4, 8, 5, JADE_ST, JADE)
    for (x, z) in ((0, 4), (8, 4), (0, 8), (8, 8)):
        c.set(x, 5, z, GOLD)                               # upturned gold corners
        c.set(x, 6, z, b("lightning_rod", facing="up", powered="false", waterlogged="false"))
    for x in (2, 6):
        c.set(x, 3, 5, lantern(hanging=True))
    # paper lanterns along the walls
    for z in (2, 6):
        for x in (0, 8):
            c.set(x, 2, z, b("stone_brick_wall", east="none", north="low", south="low", up="true", west="none",
                             waterlogged="false"))
            c.set(x, 3, z, lantern())
    # the gate grows a second roof tier: a jade slab roof on gold brackets above the beam
    for x in (0, 8):
        c.set(x, 5, 0, GOLD)
    c.fill(1, 5, 0, 7, 5, 0, RT)
    c.set(0, 6, 0, stair(JADE_ST, "east"))
    c.set(8, 6, 0, stair(JADE_ST, "west"))
    c.fill(1, 6, 0, 7, 6, 0, slab("waxed_oxidized_cut_copper_slab"))
    c.set(4, 6, 0, b("green_glazed_terracotta", facing="north"))
    c.set(4, 5, 0, b("green_glazed_terracotta", facing="north"))
    l2 = c.build()

    # ---- L3: the jade pagoda
    c = c.next_level()
    # three shrinking tiers over the hall roof's ridge (centre x4, z6)
    c.fill(2, 7, 5, 6, 8, 7, RT)
    c.fill(3, 7, 6, 5, 8, 6, b("ochre_froglight", axis="y"))  # the glowing core seen through the lattice
    for (x, z) in ((2, 5), (6, 5), (2, 7), (6, 7)):
        c.fill(x, 7, z, x, 8, z, b("stripped_dark_oak_log", axis="y"))
    hip(c, 1, 7, 4, 8, 9, JADE_ST, JADE)
    c.fill(3, 10, 5, 5, 11, 7, RT)
    hip(c, 2, 6, 4, 8, 12, JADE_ST, JADE)
    c.fill(4, 13, 6, 4, 14, 6, RT)
    hip(c, 3, 5, 5, 7, 15, JADE_ST, GOLD)
    c.set(4, 16, 6, GOLD)
    c.set(4, 17, 6, b("lightning_rod", facing="up", powered="false", waterlogged="false"))
    for (x, z) in ((1, 4), (7, 4), (1, 8), (7, 8)):
        c.set(x, 10, z, GOLD) if c.name(x, 9, z).endswith("stairs") else None
    for (x, z) in ((2, 4), (6, 4), (2, 8), (6, 8)):
        c.set(x, 13, z, GOLD) if c.name(x, 12, z).endswith("stairs") else None
    # drum towers on the front corners of the courtyard wall (the gate's flanks)
    for x in (0, 8):
        c.fill(x, 1, 1, x, 4, 2, b("stone_bricks"))
        c.set(x, 5, 1, RT)
        c.set(x, 5, 2, RT)
        c.set(x, 6, 1, b("lantern", hanging="false", waterlogged="false"))
        c.set(x, 6, 2, b("note_block", instrument="basedrum", note="0", powered="false"))
    # hanging lanterns under the tiers' eaves on the camera's side
    for (x, y, z) in ((1, 8, 8), (7, 8, 8), (2, 11, 8), (6, 11, 8)):
        c.set(x, y, z, lantern(hanging=True)) if c.empty(x, y, z) and not c.empty(x, y + 1, z) else None
    return l2, c.build()


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
    "barracks": sun_barracks,
    "graveyard": lambda: __import__("lab_graveyard").levels(),   # the Ossuary Yard and its levels (Oct 19)
    "portal_military": horde_portal,
    "grove": verdant_grove,
    "slipway": tide_slipway,
    "lotus_academy": storm_academy,
    "machine_shop": lambda: brass_lab("machine_shop"),
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
        if "graveyard" in built:   # the Ossuary Yard redraws the L1 and its overflowing state too
            import lab_graveyard
            g, og = lab_graveyard.bases()
            g.write(f"{out}/graveyard.nbt")
            og.write(f"{out}/overflowing_graveyard.nbt")
        if not only:
            for variant, p in ANNEXES.items():
                annex(p).write(f"{out}/lab_annex{variant}.nbt")
    print("wrote", len(built) * 2, "lab levels into", OUT, "and", OUT_DATA)
