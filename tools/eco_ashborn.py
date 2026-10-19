#!/usr/bin/env python3
"""The Ashborn economy set (structure variant "_nether"; Piglins / HORDE in code): wind_generator_nether,
energy_converter_nether and storage_vault_nether, redrawn as one family with the Stone Giant (the extractor,
tools/mex_ashborn.py) and the Ember Gate (the military portal, tools/lab_portal_military.py).

lovish, Oct 19 2026: "I want the buildings to look like they match each other so when bases get bigger they look
cohesive and beautiful". Players build these three in rows, so side by side they must read as one set, and each must
be recognisable at a glance: the generator is energy, the converter turns energy into metal, the vault stores.

THE ASHBORN STYLE KIT (the Stone Giant's; see tools/mex_ashborn.py). The owner renamed the faction so it does not look
like Reign of Nether's Piglins: no pigs, gold, gilded blackstone, nether brick, netherrack, crimson / warped wood.
  - main blocks: basalt (polished basalt piers, smooth basalt walls), plain tuff, obsidian sparingly (bowl plinths);
  - accent: the glowing crack (shroomlight / ochre froglight set flush; magma only where no unit can stand);
  - trim: BRONZE = waxed cut copper / waxed exposed cut copper and their stairs / slabs;
  - light: fire bowls (lit campfires on obsidian);
  - silhouette: the stepped volcano-temple (every top steps back in stairs);
  - signature: the glowing crack, and the Stone Giant (his hooded glowing face, his bronze hammer, the glowing ore).

THE SHARED BODY (so a row of them reads as a set, plinth()): the same base under every one - a battered foot of dark
stairs leaning in on all four sides (the volcano's skirt), a course of tuff between polished basalt corner piers with a
magma crack in the west (camera) face, and a bronze cornice at y 3 (upside-down bronze
stairs, a corbelled band). Above it each one steps in toward its top, and every top carries the kit's fire bowl and
bronze spikes (lightning rods, the war camp's teeth: the extractor's crown, the portal's flanks). What differs is what
stands on the plinth - three silhouettes at three heights, so each reads at a glance in a row:

  WIND GENERATOR - THE EMBER MILL (3 x 11 x 3, the tallest). A stepped volcano: its west (camera) slope steps up to the
  east in dark stairs to a peak with a bronze band, a crater with the fire bowl and two spikes; a glowing crack breaks
  out of the south face under the peak. The SAILS (WindmillRenderClientEvents draws them, not blocks) turn on a
  polished basalt hub at the foot of the west slope between two bronze bearing slabs, and lean back over the steps:
  the cone is cut under their tilted disc (checked by sampling renderSails' geometry), so they sweep the slope and
  never touch it. Energy = fire on a mountain, with sails.
  MOTION KEPT: sails (WindGenerator.SAIL_HUBS). The Ashborn and Sunforged mills are the two rotors the sail GameTest
  counts (mills >= 2), and wind on a volcano's flank is the faction's story. The rotor moves from the north (back)
  face to the WEST face: the RTS camera looks from the south-west, and a rotor tilted back 40 degrees on a camera face
  is seen nearly face-on (its disc's normal about 35 degrees off the view), where a rotor on the north face is seen
  from behind the tower at a grazing angle (about 70 degrees off its face). Hub (1,4,0) NORTH 2.6 -> (0,5,1) WEST 2.7. The renderer's "_nether" spars
  were bone (no bone in the kit): bronze spars (waxed cut copper) on the brown cloth are proposed.

  ENERGY CONVERTER - THE GIANT'S CRUCIBLE (3 x 9 x 3). Fire goes in at the foot (a fire mouth: a campfire set in the
  south face's foot, the energy) and metal comes out at the top: the Stone Giant, waist-deep in the back row with a
  magma crack in his chest, reaches his stone forearms forward along the sides and closes his fists round a crucible
  brim-full of molten ore (two ochre froglights, open to the camera - the brightest thing on the set). His hooded
  head bows over it, the face glowing, bronze pauldrons with spikes; his crown is the fire bowl.

  STORAGE VAULT - THE EMBER HOARD (3 x 7 x 3, the lowest). A squat strongroom: a bronze door on a bronze threshold
  step under the cornice in the south face, and on its stepped top (dark stairs rising on all four sides) the hoard
  heaped up - raw copper (bronze ore), raw iron, bronze ingots spilled on the steps, the glowing ore crowning it -
  with spikes on three corners and the fire bowl on the fourth. Storage = a treasure heap on a strongbox.

THE BUILDING STANDARD at 3 x 3: three depths per camera face (the skirt, the pier / wall plane, the cornice, and each
top stepping back); a broken top (the peak and spikes, the head and crown fire, the heap and spikes); 3-4 materials
per zone (basalt / tuff / deepslate stairs / bronze); the kit's lights (the cracks, the molten crucible, the ore, the
fire bowls).

GAME RULES checked in check() (run by every builder): footprint exactly 3 x 3 with the whole y 0 layer filled (these
classes have no startingBlockTypes), the box ends at the top block, at most 150 blocks, nothing pops off
(_SunRig.check_support), no pockets (tools/audit_structure_pockets.py audit() on the written nbt), no unit can climb
onto any of them and nothing reachable stands on fire or magma (the audit's own forward flood), no water / lava /
gravity blocks / redstone / Piglin blocks, and for the mill the sail hub (a check_sail_hubs.HUB_BLOCKS log on the
rotor's axis, air outside it, blades that clear the ground) and the sails' swept volume clear of every block.

Usage: python3 tools/eco_ashborn.py [OUT_DIR]   (writes wind_generator_nether.nbt, energy_converter_nether.nbt,
       storage_vault_nether.nbt and checks them; default OUT_DIR: the scratch folder - shipping is the coordinator's)
"""
import math
import os
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs   # noqa: E402

DRY = dict(waterlogged="false")
CAMPFIRE = dict(lit="true", facing="south", signal_fire="false", waterlogged="false")
ROD_UP = dict(facing="up", powered="false", waterlogged="false")
BASALT = "polished_basalt"
SMOOTH = "smooth_basalt"
TUFF = "tuff"
DARK_ST = "polished_deepslate_stairs"
DARK_SL = "polished_deepslate_slab"
BRONZE = "waxed_cut_copper"
BRONZE_W = "waxed_exposed_cut_copper"
BRONZE_ST = "waxed_cut_copper_stairs"
BRONZE_SL = "waxed_cut_copper_slab"
GLOW = "shroomlight"
ORE = "ochre_froglight"

# the mill's rotor: WindGenerator.SAIL_HUBS "_nether" (hub cell, outward face, blade length)
HUB = (0, 5, 1)
HUB_OUTWARD = "WEST"
BLADE = 2.7

# what the Ashborn never use (the owner's rule: nothing of Reign of Nether's Piglins), and what the game must never get
FORBIDDEN = ("water", "lava", "sand", "red_sand", "gravel", "_concrete_powder", "anvil", "redstone", "repeater",
             "comparator", "piston", "observer", "lever", "tnt", "gold_block", "raw_gold_block", "gilded_blackstone",
             "piglin_head", "nether_brick", "nether_bricks", "red_nether_bricks", "netherrack", "_banner", "crimson_",
             "warped_", "nether_portal", "soul_fire", "soul_campfire", "bone_block", "skeleton_skull")


class Rig(gs._SunRig):
    """The _SunRig canvas (settle() joins stairs and walls) with the kit's repeated pieces."""

    def pier(self, x, y, z):
        self.set(x, y, z, BASALT, axis="y")

    def bowl(self, x, y, z):
        """A fire bowl: a lit campfire on an obsidian plinth (the kit's light)."""
        self.set(x, y, z, "obsidian")
        self.set(x, y + 1, z, "campfire", **CAMPFIRE)

    def rod(self, x, y, z):
        self.set(x, y, z, "lightning_rod", **ROD_UP)


def plinth(r):
    """The shared body, y 0..3: the pad; a battered foot of dark stairs leaning in on every side (the volcano's skirt,
    the first step of the temple); a course of tuff between polished basalt corner piers; the bronze cornice
    (upside-down bronze stairs facing in: their lower half steps back, a corbelled band). Units can't climb it: every
    stair of the foot has the wall over it. Each building adds its own crack to the course."""
    for x in range(3):
        for z in range(3):
            corner = x != 1 and z != 1
            r.set(x, 0, z, BASALT if corner else SMOOTH, **(dict(axis="y") if corner else {}))
            if corner:
                r.pier(x, 2, z)
                r.pier(x, 3, z)
            elif (x, z) == (1, 1):
                for y in (1, 2, 3):
                    r.set(x, y, z, SMOOTH)
            else:
                r.set(x, 2, z, TUFF)
    for (x, z, f) in ((1, 0, "south"), (1, 2, "north"), (0, 1, "east"), (2, 1, "west")):
        r.stair(x, 1, z, f, name=DARK_ST)
        r.stair(x, 3, z, f, "top", name=BRONZE_ST)
    # the foot's corners: stairs facing in along x (settle() turns them into outer corners)
    for (x, z) in ((0, 0), (2, 0), (0, 2), (2, 2)):
        r.stair(x, 1, z, "east" if x == 0 else "west", name=DARK_ST)


# ---------------------------------------------------------------------------------------------------- the Ember Mill
def _mill(r):
    plinth(r)
    r.set(1, 2, 2, "magma_block")              # the crack in the south face (walled over: nothing stands on it)...
    r.set(0, 2, 1, "magma_block")              # ...forking round onto the west face
    # y 4: the cone's last full course, its corners chamfered in dark stairs (the taper starts)
    for x in range(3):
        for z in range(3):
            if x != 1 and z != 1:
                r.stair(x, 4, z, "east" if x == 0 else "west", name=DARK_ST)
            else:
                r.set(x, 4, z, SMOOTH)
    # y 5, the west (camera) slope's foot: THE HUB (polished basalt, axis x: WindGenerator.SAIL_HUBS) between two
    # bronze bearing slabs (bottom slabs: the blades' lower edge skims their cells' top half)
    r.set(0, 5, 1, BASALT, axis="x")
    r.set(0, 5, 0, BRONZE_SL, type="bottom", **DRY)
    r.set(0, 5, 2, BRONZE_SL, type="bottom", **DRY)
    for z in range(3):
        r.set(1, 5, z, TUFF if z != 1 else SMOOTH)
        r.pier(2, 5, z) if z != 1 else r.set(2, 5, z, SMOOTH)
    r.set(1, 5, 2, GLOW)                       # the crack climbs the south face
    # y 6: the cone steps up to the east in dark stairs (cut under the sails' tilted disc)
    for z in range(3):
        r.stair(1, 6, z, "east", name=DARK_ST)
        r.pier(2, 6, z) if z != 1 else r.set(2, 6, z, TUFF)
    # y 7-9: the summit ridge along the east edge, a bronze band, its ends stepped down in stairs: a peak, not a wall
    r.pier(2, 7, 0)
    r.set(2, 7, 1, SMOOTH)
    r.pier(2, 7, 2)
    r.set(2, 6, 2, GLOW)                       # the crack breaks out under the peak
    r.stair(2, 8, 0, "south", "top", name=BRONZE_ST)
    r.set(2, 8, 1, BRONZE)
    r.stair(2, 8, 2, "north", "top", name=BRONZE_ST)
    r.stair(2, 9, 0, "south", name=DARK_ST)
    r.stair(2, 9, 2, "north", name=DARK_ST)
    # the crater: the fire bowl on top, bronze spikes either side
    r.bowl(2, 9, 1)
    r.rod(2, 10, 0)
    r.rod(2, 10, 2)


# ---------------------------------------------------------------------------------------------- the Giant's Crucible
def _crucible(r):
    plinth(r)
    # the fire mouth (energy in): a campfire set into the south face's foot under the tuff. Nothing can stand on it -
    # the cell above is solid, and a 2-high unit can't get into a 1-high niche - and its smoke leaks up the face
    r.set(1, 1, 2, "campfire", **CAMPFIRE)
    r.set(0, 2, 1, "magma_block")              # the crack forks onto the west face
    # y 4: the Stone Giant, waist-deep in the north row, his forearms reaching forward along the sides to the crucible
    # he cradles: molten ore (energy out as metal), the brightest thing on the set
    r.set(0, 4, 0, BASALT, axis="y")
    r.set(1, 4, 0, "magma_block")              # the crack in his chest (covered: nothing stands on it)
    r.set(2, 4, 0, BASALT, axis="y")
    r.set(0, 4, 1, BASALT, axis="z")           # forearms
    r.set(2, 4, 1, BASALT, axis="z")
    r.stair(0, 4, 2, "east", name=DARK_ST)     # stone fists closing round the crucible's front
    r.stair(2, 4, 2, "west", name=DARK_ST)
    r.set(1, 4, 1, ORE)                        # the molten ore fills the crucible to its brim, two cells long,
    r.set(1, 4, 2, ORE)                        # open to the camera at the front
    r.set(1, 3, 1, GLOW)
    # y 5: the crucible's bronze side lips, his shoulders behind it with bronze spikes
    r.set(0, 5, 1, BRONZE_SL, type="bottom", **DRY)
    r.set(2, 5, 1, BRONZE_SL, type="bottom", **DRY)
    r.set(0, 5, 0, BRONZE_W)                   # bronze pauldrons
    r.set(2, 5, 0, BRONZE_W)
    r.set(1, 5, 0, SMOOTH)
    r.rod(0, 6, 0)
    r.rod(2, 6, 0)
    # y 6-8: the hooded head bowed over the crucible, the face glowing, his crown a fire bowl (the kit's light)
    r.set(1, 6, 0, GLOW)
    r.stair(1, 7, 0, "north", "top", name=DARK_ST)    # the hood's brow juts over the face
    r.set(1, 8, 0, "campfire", **CAMPFIRE)


# ------------------------------------------------------------------------------------------------- the Ember Hoard
def _hoard(r):
    plinth(r)
    # the strongroom door: a bronze plate in the south face; the crack forks on the west
    r.set(1, 2, 2, BRONZE_W)                   # the bronze door, on a bronze threshold step, under the cornice
    r.stair(1, 1, 2, "north", name="waxed_exposed_cut_copper_stairs")
    r.set(0, 2, 1, "magma_block")
    # y 4: the stepped top: a step of dark stairs rising to the heap on every side, piers on the corners
    for (x, z, f) in ((1, 2, "north"), (1, 0, "south"), (0, 1, "east"), (2, 1, "west")):
        r.stair(x, 4, z, f, name=DARK_ST)
    r.pier(2, 4, 0)
    r.pier(0, 4, 2)
    r.pier(2, 4, 2)
    r.set(0, 4, 0, "obsidian")                 # the fire bowl at the back corner
    r.set(0, 5, 0, "campfire", **CAMPFIRE)
    r.rod(2, 5, 0)
    r.rod(0, 5, 2)
    r.rod(2, 5, 2)
    # the hoard: bronze ore and iron heaped in the middle, ingots spilled on the steps, the glowing ore on top
    r.set(1, 4, 1, "raw_copper_block")
    r.set(1, 5, 1, "raw_copper_block")
    r.set(1, 6, 1, ORE)
    r.set(1, 5, 2, BRONZE_SL, type="bottom", **DRY)
    r.set(0, 5, 1, "iron_block")   # not raw iron: that is the metal-patch marker the quick-build scans for
    r.set(1, 5, 0, "raw_copper_block")


# ---------------------------------------------------------------------------------------------------- checks
def _sail_cells():
    """The cells the four sails sweep, sampled from WindmillRenderClientEvents.renderSails: the rotor centre 0.95
    outside the hub's outward face, tilted back 40 degrees (top toward the tower), four blades of BLADE_WIDTH 0.8 from
    0.35 to 0.35 + length and a 0.18-wide spar from 0 to length + 0.4, 0.14 (+0.04) thick."""
    o = {"NORTH": (0, 0, -1), "SOUTH": (0, 0, 1), "WEST": (-1, 0, 0), "EAST": (1, 0, 0)}[HUB_OUTWARD]
    lat = (1, 0, 0) if o[0] == 0 else (0, 0, 1)                  # the rotor plane's horizontal axis
    t40 = math.radians(40)
    up = (-o[0] * math.sin(t40), math.cos(t40), -o[2] * math.sin(t40))     # blade "up", leaning toward the tower
    nrm = (o[0] * math.cos(t40), math.sin(t40), o[2] * math.cos(t40))      # the disc's face
    c = tuple(HUB[i] + 0.5 + 0.95 * o[i] for i in range(3))
    cells = {}
    for step in range(360):
        a = math.radians(step)
        for (r0, r1, half_w) in ((0.35, 0.35 + BLADE, 0.4), (0.0, BLADE + 0.4, 0.09)):
            for i in range(41):
                rad = r0 + (r1 - r0) * i / 40
                for w in (-half_w, 0, half_w):
                    for t in (-0.09, 0.09):
                        u = rad * math.sin(a) + w * math.cos(a)
                        v = rad * math.cos(a) - w * math.sin(a)
                        p = [c[k] + u * lat[k] + v * up[k] + t * nrm[k] for k in range(3)]
                        cell = tuple(math.floor(q) for q in p)
                        cells[cell] = min(cells.get(cell, 1.0), p[1] - cell[1])
    return cells   # cell -> the lowest point the sails reach inside it (0..1 of its height)


def _names(s):
    return {p: s.palette[i][0] for p, i in s.blocks.items() if s.palette[i][0] != "air"}


def check(s, tag, mill=False):
    """The game's rules on one finished structure (see the module docstring). Returns a report line."""
    import audit_structure_pockets as ap
    n = _names(s)
    sx, sy, sz = s.size
    assert (sx, sz) == (3, 3), "%s: footprint %s, must be 3 x 3" % (tag, s.size)
    assert all((x, 0, z) in n for x in range(3) for z in range(3)), "%s: a hole in the ground layer" % tag
    assert max(p[1] for p in n) == sy - 1, "%s: the box is taller than its blocks" % tag
    assert len(n) <= 150, "%s: %d blocks (keep it under ~150)" % (tag, len(n))
    bad = sorted({nm for nm in n.values() if nm == "fire" or any(
        nm == f or nm.endswith(f) or (f.endswith("_") and nm.startswith(f)) for f in FORBIDDEN)})
    assert not bad, "%s: forbidden blocks %s" % (tag, bad)
    with tempfile.TemporaryDirectory() as d:
        path = os.path.join(d, tag + ".nbt")
        s.write(path)
        one_way, ground, roof, rooms, note = ap.audit(tag, path)
        assert not one_way and not ground and "TRAPPED" not in note, "%s: pockets %s %s %s" % (
            tag, one_way, ground, note)
        pad = ap.Pad(ap.load_nbt(path))
        # nothing a unit can walk onto burns it: the forward flood from the open ground round the building
        m = ap.MARGIN
        stack = [(x, 1, z) for x in range(-m, sx + m) for z in range(-m, sz + m)
                 if (x < 0 or z < 0 or x >= sx or z >= sz) and pad.walkable(x, 1, z)]
        seen = set(stack)
        while stack:
            for nb in ap.neighbours(pad, *stack.pop()):
                if -m <= nb[0] < sx + m and -m <= nb[2] < sz + m and 1 <= nb[1] <= sy + 1 and nb not in seen:
                    seen.add(nb)
                    stack.append(nb)
        on_top = sorted(c for c in seen if 0 <= c[0] < sx and 0 <= c[2] < sz)
        assert not on_top, "%s: units can climb onto the building at %s" % (tag, on_top)
        hot = sorted(c for c in seen if ap.base(pad.at(c[0], c[1] - 1, c[2])[0]) in ap.FIRE_FLOORS)
        assert not hot, "%s: units can stand on fire at %s" % (tag, hot)
    extra = ""
    if mill:
        # check_sail_hubs.py's rules for the "_nether" SAIL_HUBS entry, on this structure
        hx, hy, hz = HUB
        st = s.palette[s.blocks[HUB]]
        import check_sail_hubs
        assert st[0] in check_sail_hubs.HUB_BLOCKS and dict(st[1]).get("axis") == check_sail_hubs.AXIS[HUB_OUTWARD], \
            "%s: hub %s is %s, not a hub log on the rotor's axis" % (tag, HUB, st)
        dx, dz = check_sail_hubs.STEP[HUB_OUTWARD]
        assert (hx + dx, hy, hz + dz) not in n, "%s: the block outside the hub is part of the mill" % tag
        drop = (0.35 + BLADE) * math.cos(math.radians(40))
        assert drop <= hy + 0.5, "%s: blades reach %.2f below a hub %.1f above the base" % (tag, drop, hy + 0.5)
        assert 1.5 <= BLADE <= 3.2
        props = {p: dict(s.palette[i][1]) for p, i in s.blocks.items()}
        # a bottom slab fills only the lower half of its cell: the sails may pass over it
        swept = sorted(c for c, low in _sail_cells().items() if c in n and c != HUB and not (
            n[c].endswith("_slab") and props[c].get("type") == "bottom" and low > 0.55))
        assert not swept, "%s: the sails sweep through %s" % (tag, [(c, n[c]) for c in swept])
        extra = ", sail hub %s ok, sails clear" % (HUB,)
    return "%s 3x%dx3, %d blocks: no pockets, nothing climbable or burning%s" % (tag, sy, len(n), extra)


def _build(fn, tag):
    r = Rig(3, 16, 3)
    fn(r)
    r.b = {p: v for p, v in r.b.items() if v[0] != "air"}
    r.size = (3, max(p[1] for p in r.b) + 1, 3)      # the box ends at the top block
    r.bury_grass()
    r.settle()
    r.check_support(tag)
    return r.structure()


def wind():
    """The Ember Mill: a stepped volcano with the sails on its west slope and the fire bowl in its crater. 3 x 11 x 3."""
    s = _build(_mill, "ashborn mill")
    check(s, "wind_generator_nether", mill=True)
    return s


def converter():
    """The Giant's Crucible: a fire mouth below, the Stone Giant above cradling a crucible of molten ore. 3 x 9 x 3."""
    s = _build(_crucible, "ashborn crucible")
    check(s, "energy_converter_nether")
    return s


def vault():
    """The Ember Hoard: a bronze-doored strongroom with the hoard of bronze ore and glowing ore heaped on its top.
    3 x 7 x 3."""
    s = _build(_hoard, "ashborn hoard")
    check(s, "storage_vault_nether")
    return s


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else \
        "/tmp/claude-0/-home-claude/9aa3ec76-5285-5d94-a971-fdcb4e858c09/scratchpad/eco_ash"
    os.makedirs(out, exist_ok=True)
    for name, fn, mill in (("wind_generator_nether", wind, True), ("energy_converter_nether", converter, False),
                           ("storage_vault_nether", vault, False)):
        s = fn()
        s.write(os.path.join(out, name + ".nbt"))
        print(check(s, name, mill))
