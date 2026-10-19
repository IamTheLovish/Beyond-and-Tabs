#!/usr/bin/env python3
"""The Ironhide Horde's T1 army lab, the Military Portal (structure portal_military; Piglins in code, renamed the
ASHBORN by the owner), redrawn at all three levels - L1 THE EMBER GATE, L2 THE CRATER FORGE, L3 THE GIANT'S GATE - so
it belongs to the family the Stone Giant (the extractor, tools/mex_ashborn.py, variant "_nether") set.

lovish, Oct 19 2026: buildings must "look like they match each other so when bases get bigger they look cohesive and
beautiful"; "upgraded buildings can change in shape or fundamental design ... make upgraded buildings stand out for the
opponent; ... much bigger or have cooler, more intricate designs". And for this faction (design/building-revamp-
progress.md, his brainstorm): the Ashborn "must NOT resemble Reign of Nether's Piglins: no pigs, gold, gilded
blackstone, nether brick, portals". This building IS a Reign of Nether portal (its obsidian frame is game mechanics),
so the portal is dressed as the fire-door of a volcano: the Ashborn's warriors march out of the mountain's forge.

THE ASHBORN STYLE KIT (the Stone Giant's):
  - main blocks: basalt (polished basalt posts, smooth basalt), plain tuff, obsidian used sparingly (the frame and the
    fire bowls' plinths);
  - accent: glowing magma cracks - shroomlight / ochre froglight flush in faces and floor, magma_block only where no
    unit can stand (the giant's chest and waist);
  - trim: BRONZE = waxed cut copper / waxed exposed cut copper, their stairs and slabs (bands, lintel, pauldrons, helm);
  - light: fire bowls (lit campfires on obsidian or bronze plinths);
  - silhouette motif: the STEPPED VOLCANO-TEMPLE - the flanks step up toward the gate;
  - signature detail: the glowing crack (pad, faces, the crater, the giant's chest); the family figure is the Stone
    Giant himself, raising his bronze hammer, the glowing ore in his other fist.
  Nothing here is gold, gilded blackstone, nether brick (bar the four hidden starting cells, below), crimson / warped
  wood, a banner or a piglin head. Bronze spikes (lightning rods) and the horned helm are the war camp's teeth.

THE PLAN (8 x 5, Reign of Nether's portal footprint, x 0..7, z 0..4). The obsidian frame stands across the middle
(x 2..5, z 2) facing south; the RTS camera looks from the south-west, so the gate faces it and the west flank (x 0..2)
is the corner it meets first. Everything is drawn symmetric about the gate's axis except the forecourt and the arms.
  L1 THE EMBER GATE (8 x 7 x 5, 132 blocks)
  - the frame in a fissure between two stepped basalt flanks that rise toward it: x0 two high under a stair slope with
    bronze spikes, x1 with a bronze band, x2 four high behind the frame's side carrying a fire bowl beside the lintel;
    a step of dark stairs in front (the first terrace);
  - a bronze lintel over the frame; glowing cracks in the west and south faces and across the pad from the portal's
    foot to the camera's edge (and out the back).
  L2 THE CRATER FORGE (8 x 11 x 5, 193 blocks) - the gate becomes a volcano:
  - the flanks climb into a stepped cone (x1 to a bronze band at y6 with basalt piers, x2 to the crater's rim at y8)
    with the fire bowls on the rims; between them over the lintel the crater, its floor glowing under a bronze lip;
  - the forecourt: the smith's anvil-stone with the glowing ore on it at the camera's corner (the extractor's ore
    core), a fire bowl on a bronze plinth at the far corner, the crack spreading.
  L3 THE GIANT'S GATE (8 x 20 x 5, 322 blocks) - the Stone Giant rises waist-deep out of the crater:
  - a basalt torso over the gate with a bronze belt, jutting chest and a magma crack through chest and waist; bronze
    pauldrons on the cone's shoulders; a hooded head whose face is a glowing slit under a jutting brow, a horned
    bronze helm;
  - his west (camera-side) arm raises the bronze hammer (a bracer, a lightning-rod haft) - the landmark, 20 high; his
    east fist holds the glowing ore out on a chain over the forecourt; fire bowls on the cone's front terraces.

GAME RULES (asserted in check_levels; grep PortalPlacement, AbstractPortal, PortalMilitary, BuildingPlacement):
  - footprint 8 x 5 at every level (BuildingLevels has no larger footprint, and the Basic Portal's research swaps its
    structure in place, PortalPlacement.changePortalStructure); levels grow taller only;
  - the chain: portal_basic -> L1 -> L2 -> L3 only add or replace (changeStructure / refreshBlocks never remove):
    every non-air cell of each is non-air in the next;
  - the PORTAL: the obsidian frame (x 2..5 / y 1..5 at z 2, the corners free) stays obsidian at every level - fire at
    centrePos lights a nether portal in it (PortalPlacement.tick, BaseFireBlock's portal shape), canDestroyBlock
    spares obsidian and the portal, ClientModEvents tints its portal red - and the opening (x 3..4, y 2..4) stays
    air; no other obsidian could close a second frame (the plinths are single blocks);
  - centrePos is the L1 box's middle in WORLD coordinates, truncated: x 3 or 4, z 2 and y = (2 * originY + 6) / 2 = 3
    for this 7-high L1 (an 8-high box would put it at 4 for a negative originY, a 10-high one on the frame's top), so
    the L1 stays at most 7 high: the fire and the spawn land in the opening. Upgrades keep the L1's centre
    (changeStructure doesn't move it). getIndoorSpawnPoint walks down onto the frame's sill (y 2); both spawn cells
    walk out through the passage, open three high in front of and behind the opening (x 3..4, z 0..1 and 3..4);
  - AbstractPortal.startingBlockTypes = nether_bricks: exactly the shipped four cells, in layer 0 under the frame's
    sill (hidden from the camera), nowhere else;
  - nothing pops off (the chain hangs from the bronze fist), no pockets (tools/audit_structure_pockets.py on all
    three), no fire where units walk (every fire bowl and the magma sit where no reachable cell stands on them, checked
    on the audit's own flood), no water, lava, gravity blocks or redstone. BiomePalette skips PortalPlacement (no
    biome swaps), but every block is decided for the HORDE table (the coverage GameTest reads every structure).

Usage: python3 tools/lab_portal_military.py [OUT_DIR]   (writes portal_military.nbt, portal_military_l2.nbt,
       portal_military_l3.nbt and checks them; default OUT_DIR: the scratch folder - shipping is the coordinator's step)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs   # noqa: E402

SX, SZ = 8, 5
DRY = dict(waterlogged="false")
CAMPFIRE = dict(lit="true", facing="south", signal_fire="false", waterlogged="false")
ROD_UP = dict(facing="up", powered="false", waterlogged="false")

BASALT = "polished_basalt"      # posts (axis y)
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

# the portal: Reign of Nether's frame, never moved (PortalPlacement, BaseFireBlock's portal shape)
FRAME = [(3, 1, 2), (4, 1, 2), (3, 5, 2), (4, 5, 2)] + [(x, y, 2) for x in (2, 5) for y in (2, 3, 4)]
OPENING = [(x, y, 2) for x in (3, 4) for y in (2, 3, 4)]
START_TYPE = "nether_bricks"
START_CELLS = [(x, 0, 2) for x in (2, 3, 4, 5)]
MIRROR = {"east": "west", "west": "east", "north": "north", "south": "south"}


class Rig(gs._SunRig):
    """gen_structures._SunRig (settle() gives stairs and walls the shapes the game gives them) drawing the building
    symmetric about x = 3.5 - the gate's axis - with a support check for what this building hangs (the ore's chain)."""

    def __init__(self, sy):
        super().__init__(SX, sy, SZ)

    # every flank is drawn once for the west side; mirror=True draws it at x -> 7 - x as well
    def put(self, x, y, z, name, mirror=True, **p):
        self.set(x, y, z, name, **p)
        if mirror:
            q = dict(p)
            if "facing" in q:
                q["facing"] = MIRROR.get(q["facing"], q["facing"])
            self.set(SX - 1 - x, y, z, name, **q)

    def st(self, x, y, z, facing, half="bottom", name=DARK_ST, mirror=True):
        self.put(x, y, z, name, mirror, facing=facing, half=half, shape="straight", waterlogged="false")

    def post(self, x, y, z, mirror=True):
        self.put(x, y, z, BASALT, mirror, axis="y")

    def slab(self, x, y, z, name=DARK_SL, typ="bottom", mirror=True):
        self.put(x, y, z, name, mirror, type=typ, waterlogged="false")

    def fire(self, x, y, z, mirror=True):
        self.put(x, y, z, "campfire", mirror, **CAMPFIRE)

    def rod(self, x, y, z, mirror=True):
        self.put(x, y, z, "lightning_rod", mirror, **ROD_UP)

    def fill(self, x1, y1, z1, x2, y2, z2, name, mirror=True, **p):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.put(x, y, z, name, mirror, **p)

    def check_support(self, tier):
        bad = []
        for pos, (n, p) in self.b.items():
            below, above = self.nb(pos, "down"), self.nb(pos, "up")
            if n in ("lantern", "soul_lantern"):
                ok = (gs._sun_sturdy(above, "down") or above[0] == "chain") if p.get("hanging") == "true" \
                    else gs._sun_centre_up(below)
            elif n == "chain":
                ok = gs._sun_sturdy(above, "down") or above[0] == "chain" or gs._sun_centre_up(below)
            elif n.endswith(("_torch", "_button", "ladder", "_carpet", "_banner", "candle")) or n in ("torch",):
                ok = False
            else:
                continue
            if not ok:
                bad.append((pos, n))
        assert not bad, "portal %s: would pop off / hang: %s" % (tier, sorted(bad))


# ---------------------------------------------------------------------------------------------------------- L1
def pad(r):
    for x in range(SX):
        for z in range(SZ):
            corner = x in (0, SX - 1) and z in (0, SZ - 1)
            edge = x in (0, SX - 1) or z in (0, SZ - 1)
            if corner:
                r.set(x, 0, z, BASALT, axis="y")
            else:
                r.set(x, 0, z, SMOOTH if edge else TUFF)
    for (x, y, z) in START_CELLS:
        r.set(x, y, z, START_TYPE)
    # the glowing crack: from the portal's foot out to the camera's edge and out the back
    for (x, z) in ((3, 3), (4, 4), (4, 1), (3, 0)):
        r.set(x, 0, z, GLOW)


def frame(r):
    for c in FRAME:
        r.set(*c, "obsidian")
    r.post(2, 1, 2)
    r.put(2, 5, 2, BRONZE)


def mass(r, x, z, y1, y2):
    for y in range(y1, y2 + 1):
        r.put(x, y, z, TUFF if (x + y + z) % 3 == 0 else SMOOTH)


def flank_l1(r):
    # the volcano's flank, stepping UP toward the gate: x0 two high under a stair slope, x1 three high with the bronze
    # band, x2 four high behind the frame's side, its summit carrying the fire bowl beside the lintel
    for z in (0, 1, 2):
        mass(r, 0, z, 1, 2)
        r.st(0, 3, z, "east")
        mass(r, 1, z, 1, 2)
        r.put(1, 3, z, BRONZE_W)
        r.st(1, 4, z, "east")
    for z in (0, 2):
        r.post(0, 1, z)
        r.post(0, 2, z)
    r.st(1, 4, 2, "north")
    r.put(0, 2, 1, GLOW)                         # the crack in the camera's west face
    r.put(1, 2, 2, GLOW)                         # and in the south face
    for z in (0, 1):
        mass(r, 2, z, 1, 4)
    r.put(2, 5, 0, "obsidian")                   # the summit's fire bowl
    r.fire(2, 6, 0)
    r.st(2, 5, 1, "south")
    r.rod(0, 4, 0)                               # bronze spikes on the first terrace (the war camp's palisade)
    r.rod(0, 4, 2)
    # the skirt: one step of dark stairs in front (the volcano's first terrace; the flank behind is two higher, so
    # nothing climbs it)
    for x in (0, 1, 2):
        r.st(x, 1, 3, "north")


def lintel_l1(r):
    # the bronze lintel over the frame
    r.st(2, 6, 2, "east", name=BRONZE_ST)
    r.put(3, 6, 2, BRONZE_W, mirror=False)
    r.put(4, 6, 2, BRONZE_W, mirror=False)


def draw_l1(r):
    pad(r)
    frame(r)
    flank_l1(r)
    lintel_l1(r)


# ---------------------------------------------------------------------------------------------------------- L2
def flank_l2(r):
    # the cone climbs: x1 to y6 (bronze band at y6), x2 to y8, the rim of the crater
    for z in (0, 1, 2):
        mass(r, 1, z, 4, 5)
        r.put(1, 6, z, BRONZE_W)
        r.st(1, 7, z, "east")
    r.st(1, 7, 2, "north")
    for y in (4, 5):
        r.post(1, y, 0)
        r.post(1, y, 2)
    r.put(1, 3, 2, BRONZE_W)
    r.put(1, 5, 2, GLOW)
    for z in (0, 1):
        mass(r, 2, z, 5, 8)
    r.put(2, 6, 2, SMOOTH)
    r.put(2, 7, 2, SMOOTH)
    r.st(2, 8, 2, "north")
    r.put(2, 9, 0, "obsidian")
    r.fire(2, 10, 0)
    r.st(2, 9, 1, "south")


def crater_l2(r):
    # between the rims: the arch over the lintel, the crater with its glowing floor
    for x in (3, 4):
        for z in (0, 1):
            r.put(x, 6, z, SMOOTH, mirror=False)
            r.put(x, 7, z, TUFF, mirror=False)
        r.put(x, 7, 2, SMOOTH, mirror=False)
        r.put(x, 8, 1, GLOW, mirror=False)
        r.put(x, 8, 0, SMOOTH, mirror=False)
        r.put(x, 8, 2, BRONZE_SL, mirror=False, type="bottom", **DRY)


def forecourt_l2(r):
    # the camera's corner gets the smith's anvil-stone with the glowing ore on it (the Stone Giant's, the extractor's
    # "one glowing ore core"); the far front corner a fire bowl on obsidian and bronze, two high so no unit (not even
    # one on the skirt) can step onto the flame
    r.put(1, 1, 4, BASALT, mirror=False, axis="y")
    r.st(0, 1, 4, "east", mirror=False)
    r.st(2, 1, 4, "west", mirror=False)
    r.put(1, 2, 4, ORE, mirror=False, axis="y")
    r.put(7, 1, 4, "obsidian", mirror=False)
    r.put(7, 2, 4, BRONZE_W, mirror=False)
    r.put(7, 3, 4, "campfire", mirror=False, **CAMPFIRE)


def draw_l2(r):
    draw_l1(r)
    flank_l2(r)
    crater_l2(r)
    forecourt_l2(r)


# ---------------------------------------------------------------------------------------------------------- L3
def giant_l3(r):
    # waist-deep in the crater: the torso x2..5, z0..2, y9..12, a bronze belt, the magma crack in the chest
    for x in (2, 3):
        for z in (0, 1, 2):
            for y in (9, 10, 11, 12):
                r.put(x, y, z, SMOOTH)
        r.put(x, 9, 2, BRONZE)
    r.put(3, 10, 2, "magma_block", mirror=False)
    r.put(2, 10, 1, "magma_block")               # the crack runs round the waist (a solid block over it: no one stands)
    r.put(4, 11, 2, "magma_block", mirror=False)
    r.put(3, 12, 2, BASALT, mirror=False, axis="y")
    r.st(2, 12, 3, "north", "top")
    r.st(3, 12, 3, "north", "top")
    # shoulders: bronze pauldrons on x1, their slopes outward
    for z in (0, 1, 2):
        r.put(1, 12, z, BRONZE_W)
        r.st(1, 13, z, "east", name=BRONZE_ST)
        r.put(2, 13, z, SMOOTH)
    r.st(1, 12, 3, "north", "top", name=BRONZE_ST)
    # head: x3..4, three deep, the face on the front (z2): a chin, a glowing slit for eyes under the hood's brow
    for z in (0, 1, 2):
        r.put(3, 13, z, SMOOTH)
        r.put(3, 14, z, BASALT, axis="y")
        r.put(3, 15, z, SMOOTH)
        r.st(2, 14, z, "east")                   # the hood falls from the head to the shoulders
    r.put(3, 14, 2, GLOW)
    r.st(3, 15, 2, "north", "top")               # the brow, jutting out over the eyes
    r.st(3, 15, 3, "north", "top")
    r.st(2, 15, 1, "east")
    r.st(2, 15, 0, "east")
    r.st(2, 15, 2, "east", "top")
    # the horned helm: a bronze circlet and two bronze horns curving up and out (the war horn's beast)
    r.st(3, 16, 2, "north", name=BRONZE_ST)
    r.put(3, 16, 1, BRONZE)
    r.st(3, 16, 0, "south", name=BRONZE_ST)
    r.st(2, 16, 1, "west", name=BRONZE_ST)
    r.rod(1, 17, 1)


def arms_l3(r):
    # the raised hammer in the west (camera-side) fist: upper arm up from the pauldron, the forearm forward, the haft
    for y in (13, 14, 15):
        r.put(0, y, 1, BASALT, mirror=False, axis="y")
    r.put(0, 14, 1, BRONZE_W, mirror=False)          # a bronze bracer breaks the long arm
    r.put(0, 16, 1, BASALT, mirror=False, axis="z")
    r.put(0, 16, 2, TUFF, mirror=False)
    for y in (17, 18):
        r.put(0, y, 2, "lightning_rod", mirror=False, **ROD_UP)
    for z in (1, 2, 3):
        r.put(0, 19, z, BRONZE if z == 2 else BRONZE_W, mirror=False)
    # the east arm hangs and reaches forward: the bronze fist holds the glowing ore up on a chain
    r.put(7, 12, 1, BASALT, mirror=False, axis="y")
    r.put(7, 11, 1, BRONZE_W, mirror=False)
    r.put(7, 10, 1, BASALT, mirror=False, axis="z")
    r.put(7, 10, 2, BASALT, mirror=False, axis="z")
    r.put(7, 10, 3, BRONZE_W, mirror=False)
    r.put(7, 9, 3, "chain", mirror=False, axis="y", **DRY)
    r.put(7, 8, 3, ORE, mirror=False, axis="y")


def bowls_l3(r):
    # the rim's fire bowls went under the giant's waist: two new ones burn on the cone's front terrace, under the
    # pauldrons, in the camera's view
    r.put(1, 8, 2, "obsidian")
    r.fire(1, 9, 2)


def draw_l3(r):
    draw_l2(r)
    giant_l3(r)
    arms_l3(r)
    bowls_l3(r)


# ---------------------------------------------------------------------------------------------------------- build
def build(draw, sy, tier):
    r = Rig(sy)
    draw(r)
    r.settle()
    r.check_support(tier)
    s = r.structure()
    top = max(p[1] for p, (n, _) in r.b.items() if n != "air")
    assert top == sy - 1, "%s: box %d high, blocks reach y %d" % (tier, sy, top)
    return s


def l1():
    return build(draw_l1, 7, "L1")


def l2():
    return build(draw_l2, 11, "L2")


def l3():
    return build(draw_l3, 20, "L3")


def base():
    return l1()


def levels():
    """(L2, L3) for gen_lab_levels.LABS["portal_military"]."""
    return l2(), l3()


def names(s):
    return {p: s.palette[i][0] for p, i in s.blocks.items() if s.palette[i][0] != "air"}


def props(s):
    return {p: s.palette[i] for p, i in s.blocks.items() if s.palette[i][0] != "air"}


# the Ashborn kit leaves out everything that reads as Reign of Nether's Piglins; nether bricks only as the hidden
# starting cells under the frame. And what the game must never get: fluids, gravity blocks, redstone
FORBIDDEN = ("water", "lava", "sand", "red_sand", "gravel", "_concrete_powder", "anvil", "redstone", "repeater",
             "comparator", "piston", "observer", "lever", "tnt", "gold_block", "gilded_blackstone", "piglin_head",
             "_banner", "crimson_", "warped_", "nether_portal", "soul_fire", "soul_campfire", "_leaves")
L1_MAX_HEIGHT = 7   # centrePos.y = (int) of the box's mid-height in WORLD coordinates: a 7-high box puts it at y 3


def reachable(pad, sx, sz, sy):
    """Every cell a ground unit can walk into from the open ground round the building (the audit's forward flood)."""
    import audit_structure_pockets as ap
    m = ap.MARGIN
    stack = [(x, 1, z) for x in range(-m, sx + m) for z in range(-m, sz + m)
             if (x < 0 or z < 0 or x >= sx or z >= sz) and pad.walkable(x, 1, z)]
    seen = set(stack)
    while stack:
        for n in ap.neighbours(pad, *stack.pop()):
            if -m <= n[0] < sx + m and -m <= n[2] < sz + m and 1 <= n[1] <= sy + 1 and n not in seen:
                seen.add(n)
                stack.append(n)
    return seen


def check_levels(s1, s2, s3, paths=None):
    """The rules the game needs, on the three finished Structures (support already ran in build())."""
    import audit_structure_pockets as ap
    from gen_lab_levels import load_base
    levels = (("L1", s1), ("L2", s2), ("L3", s3))
    assert s1.size[1] <= L1_MAX_HEIGHT, "L1 is %d high: the portal's centre (the fire and the spawn) leaves the " \
        "opening" % s1.size[1]
    for tag, s in levels:
        n = names(s)
        assert (s.size[0], s.size[2]) == (SX, SZ), "%s: footprint %s, the portal's is 8 x 5" % (tag, s.size)
        assert max(p[1] for p in n) == s.size[1] - 1, "%s: the box is taller than its blocks" % tag
        assert all((x, 0, z) in n for x in range(SX) for z in range(SZ)), "%s: a hole in the foundation" % tag
        bad = sorted({nm for nm in n.values() if nm == "fire" or any(
            nm == f or nm.endswith(f) or (f.endswith("_") and nm.startswith(f)) for f in FORBIDDEN)})
        assert not bad, "%s: forbidden blocks %s" % (tag, bad)
        nb = sorted(p for p, nm in n.items() if nm.startswith("nether_brick"))
        assert nb == sorted(START_CELLS), "%s: nether bricks at %s (only the starting cells %s)" % (tag, nb, START_CELLS)
        # the portal: the obsidian frame where Reign of Nether's is, its opening open (the fire lights it there)
        lost = [c for c in FRAME if n.get(c) != "obsidian"]
        assert not lost, "%s: the frame lost its obsidian at %s" % (tag, lost)
        blocked = [c for c in OPENING if c in n]
        assert not blocked, "%s: the portal opening is blocked at %s" % (tag, blocked)
        obs = sorted(p for p, nm in n.items() if nm == "obsidian" and p not in FRAME)
        for (x, y, z) in obs:      # no second obsidian ring a fire could light: bowls' plinths are single blocks
            assert (x, y, z) not in OPENING
        # the passage: the two cells in front of and behind the opening stay open three high (units step out both ways)
        shut = [(x, y, z) for x in (3, 4) for z in (0, 1, 3, 4) for y in (1, 2, 3) if (x, y, z) in n]
        assert not shut, "%s: the gate passage is blocked at %s" % (tag, shut)
    # the chain: an upgrade only adds or replaces (BuildingPlacement.changeStructure / refreshBlocks); the Basic
    # Portal's research (PortalPlacement.changePortalStructure) also swaps portal_basic for the L1 in place
    _, basic = load_base("portal_basic")
    basic_cells = {p for p, (nm, _) in basic.items() if nm != "air"}
    chain = (("portal_basic", basic_cells), ) + tuple((t, set(names(s))) for t, s in levels)
    for (ta, a), (tb, b) in zip(chain, chain[1:]):
        lost = sorted(a - b)
        assert not lost, "%s -> %s would leave blocks behind: %s" % (ta, tb, lost[:8])
    report = []
    if paths:
        for (tag, s), path in zip(levels, paths):
            one_way, ground, roof, rooms, note = ap.audit(tag, path)
            assert not one_way and not ground and "TRAPPED" not in note, "%s: pockets %s %s %s" % (
                tag, one_way[:6], ground[:6], note)
            pad = ap.Pad(ap.load_nbt(path))
            walk = reachable(pad, SX, SZ, s.size[1])
            # nothing a unit walks onto burns it: no reachable cell stands on a fire bowl or the chest's magma
            hot = sorted(c for c in walk if ap.base(pad.at(c[0], c[1] - 1, c[2])[0]) in ap.FIRE_FLOORS)
            assert not hot, "%s: units can stand on fire at %s" % (tag, hot)
            # the spawn: centrePos is x 3 or 4 (an even width, truncated in world coordinates), z 2, y 2..3 of the
            # L1 box; ProductionBuilding.getIndoorSpawnPoint walks it down onto the frame's sill at y 2. A unit there
            # walks out (the portal's own blocks are passable to the grid)
            for x in (3, 4):
                start = (x, 2, 2)
                assert pad.walkable(*start), "%s: the spawn cell %s is not standable" % (tag, start)
                seen, stack, out = {start}, [start], False
                while stack and not out:
                    c = stack.pop()
                    if c[0] < 0 or c[2] < 0 or c[0] >= SX or c[2] >= SZ:
                        out = True
                        break
                    for nb in ap.neighbours(pad, *c):
                        if nb not in seen and -3 <= nb[0] <= SX + 2 and -3 <= nb[2] <= SZ + 2 \
                                and 1 <= nb[1] <= s.size[1] + 1:
                            seen.add(nb)
                            stack.append(nb)
                assert out, "%s: a unit spawned at %s can't walk out" % (tag, start)
            report.append("%s %dx%dx%d, %d blocks: no pockets (%d roof-only cells nothing can reach), nothing "
                          "reachable burns, both spawn cells walk out%s" % (tag, s.size[0], s.size[1], s.size[2],
                                                                            len(names(s)), len(roof), note))
    return report


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else \
        "/tmp/claude-0/-home-claude/9aa3ec76-5285-5d94-a971-fdcb4e858c09/scratchpad/lab_portal"
    os.makedirs(out, exist_ok=True)
    built = (l1(), l2(), l3())
    paths = [os.path.join(out, f) for f in ("portal_military.nbt", "portal_military_l2.nbt", "portal_military_l3.nbt")]
    for st, path in zip(built, paths):
        st.write(path)
    for line in check_levels(*built, paths=paths):
        print(line)
