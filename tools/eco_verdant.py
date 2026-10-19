#!/usr/bin/env python3
"""The Verdant Court economy set, redrawn as one family with the World-Tree Court (the capitol,
tools/capitol_heartwood_hall.py), the Root Spring (the extractor, tools/mex_verdant.py) and the Stag Glade (the T1
lab, tools/lab_grove.py): THE BLOSSOM STAG (wind_generator_verdant), THE ROOTSTILL (energy_converter_verdant) and THE
SEED CACHE (storage_vault_verdant).

lovish, Oct 19 2026: "I want the buildings to look like they match each other so when bases get bigger they look
cohesive and beautiful". Players build these three in rows, so they must read as one set side by side and each at a
glance as what it is: the generator = energy, the converter = energy turned into metal, the vault = storage. The old
three did not belong to the Court the capitol now shows: a pink cherry lollipop, an azalea box over a campfire and a
moss cube with barrels - no mossy stone, no birch silver, no stag, no layered canopy.

THE VERDANT STYLE KIT (the capitol's, the extractor's and the grove's - the same on all three):
  - main blocks: MOSSY STONE (mossy / cracked / chiseled stone bricks, mossy cobble, their stairs and walls) and
    LIVING WOOD (oak logs and bark, stripped birch - the Court's silver - for the mask and the antlers);
  - accent: LEAVES (azalea, flowering azalea, a cherry-blossom crown), persistent with their TRUE distance to the wood
    (mex_verdant._leaf_distances: vanilla recomputes it, RoN compares placed states);
  - light: FROGLIGHTS (verdant eyes and buds, ochre / pearlescent fruit) and lanterns standing on mossy walls;
  - silhouette motif: ROOTS AND CANOPIES - oak root claws over the pad, canopies in layered tiers with gaps;
  - signature detail: the STAG-DRYAD - a pale birch mask, verdant froglight eyes under dark-oak brows, a moss crown with
    a glowing bud, and birch antlers that are the tree's own branches. Every one of the three wears it on its SOUTH
    face (the RTS camera looks from the south-west: it sees the tops, the west face x = 0 and the south face z = 2),
    so a row of them is a row of the same spirit;
  - shared pad: the Root Spring's ruined court in small - mossy cobble corners, a broken kerb of mossy and cracked
    stone, moss in the middle - and a ruined stone pier or a lantern post on the camera's front corners.

THE THREE (each 3 x 3, the class footprint; heights 15 / 9 / 7 so a row steps down from tree to kiln to store):
  - THE BLOSSOM STAG (generator = energy): the Root Spring's tree standing free. Oak root claws on the pad, a ruined pier
    on the front-west corner and a lantern post on the front-east, a PEARLESCENT HEART glowing in the trunk under the
    stag's head; the head on the trunk looks south (birch mask, froglight eyes, dark-oak brows, a moss crown and a
    glowing bud), its birch antlers rise in front of a lower azalea tier, bare leader and tines between the tiers, and
    a CHERRY-BLOSSOM CROWN on top - the blossom is the energy: WindmillRenderClientEvents spirals petals down out of it
    (WindGenerator.MOTIONS "_verdant" PETALS, anchored on the crown's underside leaf (1, 11, 1)).
  - THE ROOTSTILL (converter = energy into metal): the stag's head carved in MOSSY STONE, a kiln. The green energy is a
    verdant froglight BUD held up between the birch antlers on its crown; a root of the tree behind runs down its back;
    its MOUTH is a glowing hearth (shroomlight under a birch muzzle) and at its chin sits the metal it gives - an IRON
    BLOCK. Read top to bottom: green glow, the face, the forge, the metal.
  - THE SEED CACHE (vault = storage): a squat store of mossy stone piers and oak beams, BARRELS set in every face and
    hay sheaves stacked in the loft, under a low two-tier leaf thatch; the stag's mask is the gable's crest over the
    door beam with two short antler tines - a small spirit guarding the hoard.

GAME RULES (asserted in check(); the coordinator wires these, this file edits nothing):
  - footprint exactly 3 x 3 with a full layer 0 (BuildingPlacement and the bots assume it); generator <= 16 high,
    converter and vault <= 10; under 150 blocks each;
  - the classes have no startingBlockTypes (layer 0 is a full pad all the same);
  - nothing pops off (lanterns stand on walls / full tops, plants on soil, nothing hangs: the build runs bottom-up), no
    candles, carpets or slabs where a unit could step, no water, lava, fire, gravity blocks or redstone, no chest
    (BiomePalette.PROTECTED: chests identify stockpiles) and no raw iron (MetalPatches.PATCH_BLOCK: the quick-build
    hover would read it as a patch) - the converter's metal is an iron block;
  - no grass under full blocks (bury_grass), leaves persistent with their true distance;
  - no pockets (tools/audit_structure_pockets.py audit() on the written nbt): a 3 x 3 is ground units walk round;
  - the generator's PETALS anchor is a leaf block with a block above it (a bare top leaf turns to snow in a snowy biome:
    the snowy test wants the crown's TOP capped, not the anchor) - check_motion() mirrors tools/check_sail_hubs.py.

Usage: python3 tools/eco_verdant.py [OUT_DIR]   (writes the three .nbt and checks them; default OUT_DIR: ./eco_out)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs   # noqa: E402
import mex_verdant as mv      # noqa: E402  - the leaf-distance pass shared with the Root Spring, capitol and grove

S = 3                                   # the 3 x 3 footprint
MAX_H = {"wind_generator_verdant": 16, "energy_converter_verdant": 10, "storage_vault_verdant": 10}
MAX_BLOCKS = 150
PETALS = (1, 11, 1)                      # WindGenerator.MOTIONS "_verdant": the crown's underside leaf
PETAL_RADIUS = 1.7

DRY = dict(waterlogged="false")
MSB, CSB, CHB, MC = "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks", "mossy_cobblestone"
MSS, MSW = "mossy_stone_brick_stairs", "mossy_stone_brick_wall"
LOG, WOOD, OST = "oak_log", "oak_wood", "oak_stairs"
BIRCH, BIRCH_W, BIRCH_S, BIRCH_F = "stripped_birch_log", "stripped_birch_wood", "birch_stairs", "birch_fence"
DOS = "dark_oak_stairs"
LEAF, FLEAF, CHERRY = "azalea_leaves", "flowering_azalea_leaves", "cherry_leaves"
GLOW, FRUIT, PEARL, SHROOM = "verdant_froglight", "ochre_froglight", "pearlescent_froglight", "shroomlight"
SOIL = ("moss_block", "grass_block", "rooted_dirt", "podzol")
PLANTS = ("fern", "pink_petals", "flowering_azalea", "azalea", "lily_of_the_valley", "azure_bluet")


class Rig(gs._SunRig):
    """gen_structures._SunRig (settle() shapes stairs and joins walls / fences as the game does) with the Court's
    helpers; the canvas is drawn tall and cropped to its top block."""

    def __init__(self, sy=16):
        super().__init__(S, sy, S)

    def st(self, x, y, z, facing, half="bottom", name=MSS):
        self.stair(x, y, z, facing, half, name)

    def log(self, x, y, z, axis="y", name=LOG):
        self.set(x, y, z, name, axis=axis)

    def lantern(self, x, y, z):
        self.set(x, y, z, "lantern", hanging="false", waterlogged="false")

    def glow(self, x, y, z, name=GLOW):
        self.set(x, y, z, name, axis="y")

    def leaf(self, x, y, z, name=LEAF):
        """A leaf (its distance is fixed once all the wood stands); never overwrites, clipped to the box."""
        if 0 <= x < S and 0 <= z < S and 0 <= y < self.size[1] and (x, y, z) not in self.b:
            self.set(x, y, z, name, persistent="true", distance="7", waterlogged="false")

    def check_support(self, tag):
        bad = []
        for pos, (n, p) in self.b.items():
            below = self.nb(pos, "down")
            if n in ("lantern", "soul_lantern"):
                ok = p.get("hanging") != "true" and gs._sun_centre_up(below)
            elif n.endswith(("candle", "_carpet", "_banner")) or n == "bell":
                ok = False         # none in this set: no candles or carpets where units step (the Court's lesson)
            elif n in PLANTS:
                ok = below[0] in SOIL
            elif "wall_" in n or n.endswith(("_button", "ladder", "torch")) or n == "vine":
                ok = False
            else:
                continue
            if not ok:
                bad.append((pos, n, below[0]))
        assert not bad, "verdant eco %s: would pop off / hang: %s" % (tag, sorted(bad))


# ---- shared pieces ----------------------------------------------------------------------------------------------

def pad(r):
    """y0, the Root Spring's ruined court in small: mossy cobble corners (rooted dirt at the back-east one, where the
    roots broke the flags), a broken kerb of mossy and cracked stone on the edges, moss in the middle."""
    for x in range(S):
        for z in range(S):
            corner = x in (0, 2) and z in (0, 2)
            edge = x in (0, 2) or z in (0, 2)
            r.set(x, 0, z, MC if corner else (MSB, CSB)[(x + z * 2) % 2] if edge else "moss_block")
    r.set(2, 0, 0, "rooted_dirt")


def stag_face(r, y, eye=GLOW, mask=BIRCH_W, jaw=OST):
    """The STAG-DRYAD's face on the south plane z = 2, three wide (the kit's signature detail): a jaw line of top
    stairs hollowed under at y, the eyes flanking the birch muzzle at y + 1, dark-oak brows over the eyes and the birch
    blaze at y + 2."""
    r.st(0, y, 2, "east", "top", jaw)
    r.st(2, y, 2, "west", "top", jaw)
    r.set(1, y, 2, mask, axis="y")
    r.glow(0, y + 1, 2, eye)
    r.glow(2, y + 1, 2, eye)
    r.set(1, y + 1, 2, mask, axis="y")
    r.st(0, y + 2, 2, "south", "top", DOS)
    r.st(2, y + 2, 2, "south", "top", DOS)
    r.set(1, y + 2, 2, mask, axis="y")


# ---- THE BLOSSOM STAG: the wind generator ------------------------------------------------------------------------

def wind_draw(r):
    pad(r)
    # the trunk's foot and its claws: oak bark in the middle, oak stairs rising toward it on the west, east and north
    # (root claws - the kit's motif), a mangrove root at the back-west corner
    r.set(1, 1, 1, WOOD, axis="y")
    r.st(0, 1, 1, "east", name=OST)
    r.st(2, 1, 1, "west", name=OST)
    r.st(1, 1, 0, "south", name=OST)
    r.set(0, 1, 0, "mangrove_roots", **DRY)
    r.set(2, 1, 0, "fern")                               # on the rooted dirt where the roots broke in
    for y in (2, 3):
        r.log(1, y, 1)
    # the energy: a pearlescent heart glowing in the trunk's front, under the head, on a root
    r.st(1, 1, 2, "north", name=OST)
    r.glow(1, 2, 2, PEARL)
    r.st(1, 3, 2, "south", "top", OST)                   # the throat over the heart
    # the old shrine the tree grew from: the stub of a pier on the camera corner (kept low so the trunk and its heart
    # show), and a lantern post on the front-east corner
    r.set(0, 1, 2, CHB)                                  # the pier snapped at its chiseled foot
    r.set(2, 1, 2, MSW)
    r.lantern(2, 2, 2)
    # the head: an oak skull (z 0..1), the face on the south plane (y 4..6)
    for x in range(3):
        for y in (4, 5, 6):
            if x == 1 or y > 4:
                r.set(x, y, 1, WOOD, axis="y")
    r.set(1, 4, 0, WOOD, axis="y")
    stag_face(r, 4)
    # the crown of the head: moss and a glowing bud between the antler burrs; the leader rises behind
    r.set(1, 7, 2, "moss_block")
    r.glow(1, 8, 2)
    for y in range(5, 11):
        if (1, y, 1) not in r.b:
            r.log(1, y, 1)
    r.log(1, 5, 0)
    # the antlers ARE boughs: birch burrs over the brows, a fence beam, a knuckle and bare tines standing in front of
    # the lower tier and up past it, beside the blossom crown
    for x in (0, 2):
        r.log(x, 7, 2, name=BIRCH)
        r.set(x, 8, 2, BIRCH_F)
        r.log(x, 9, 2, name=BIRCH)                       # the knuckle
        r.set(x, 10, 2, BIRCH_F)
        r.set(x, 11, 2, BIRCH_F)
        r.set(x, 10, 1, BIRCH_F)                         # a back tine forking off it, bare in the gap between tiers
    # the lower tier: azalea on boughs behind the antlers (z 0..1, y 7..9) - the leaf cloak of the Root Spring's T2
    r.log(0, 8, 1, "x")
    r.log(2, 8, 1, "x")
    for x in range(3):
        for z in (0, 1):
            for y in (7, 8, 9):
                if not (y == 9 and (x, z) in ((0, 0), (2, 0))):
                    r.leaf(x, y, z, gs.leaf_for(x, y, z, 3))
    r.glow(0, 8, 0, FRUIT)                                # glowing fruit in the tier's west face
    # the gap: bare leader (y 10) - the tiers read as layers; then the CHERRY-BLOSSOM crown (y 11..13) and a tuft
    for x in range(3):
        for z in range(3):
            if (x, z) not in ((0, 2), (2, 2)):            # the antler tines rise through the front corners
                r.leaf(x, 11, z, CHERRY)
    for (x, z) in ((1, 0), (0, 1), (1, 1), (2, 1), (0, 0), (2, 0)):
        r.leaf(x, 12, z, CHERRY)
    r.glow(1, 12, 2, PEARL)                               # a pearl of blossom-light toward the camera
    for (x, z) in ((1, 0), (0, 1), (1, 1), (2, 1), (1, 2)):
        r.leaf(x, 13, z, CHERRY)
    r.leaf(1, 14, 1, CHERRY)
    for x in (0, 2):
        r.set(x, 12, 2, BIRCH_F)                          # the tines' tips stand out beside the blossom


# ---- THE ROOTSTILL: the energy converter -------------------------------------------------------------------------

def converter_draw(r):
    pad(r)
    # the kiln: the stag's head carved in mossy stone, y 1..4 (mixed mossy / cracked / cobble courses)
    for x in range(3):
        for z in (0, 1):
            for y in (1, 2, 3):
                r.set(x, y, z, (MSB, CSB, MC)[(x * 2 + y + z) % 3])
    r.set(0, 1, 2, CHB)
    r.set(2, 1, 2, CHB)
    r.set(1, 1, 2, "iron_block")                         # the metal it gives, at its chin
    # the mouth: the hearth glowing between the jaws, the forge the energy goes into
    r.set(1, 2, 2, SHROOM)
    r.st(0, 2, 2, "east", "top", MSS)
    r.st(2, 2, 2, "west", "top", MSS)
    r.glow(0, 3, 2)
    r.glow(2, 3, 2)
    r.set(1, 3, 2, BIRCH_W, axis="y")
    r.st(0, 4, 2, "south", "top", DOS)
    r.st(2, 4, 2, "south", "top", DOS)
    r.set(1, 4, 2, BIRCH_W, axis="y")
    r.st(0, 4, 1, "south", name=MSS)                     # the stone skull's crown, sloping back
    r.st(2, 4, 1, "south", name=MSS)
    r.set(1, 4, 1, "moss_block")
    # the tree behind it: a trunk up the head's back; a root claws down the west face (the camera's) to the pad
    for y in range(1, 8):
        r.log(1, y, 0)
    r.set(0, 1, 0, "mangrove_roots", **DRY)
    r.set(0, 2, 0, WOOD, axis="y")
    r.set(0, 3, 0, WOOD, axis="y")
    r.st(0, 4, 0, "east", name=OST)
    r.st(2, 4, 0, "west", name=OST)
    r.st(0, 2, 1, "east", name=MSS)                      # a niche stepped into the west cheek
    # the antlers on the crown, holding up the green bud (the energy the still turns)
    for x in (0, 2):
        r.log(x, 5, 1, name=BIRCH)
        r.set(x, 6, 1, BIRCH_F)
        r.set(x, 7, 1, BIRCH_F)
        r.set(x, 6, 2, BIRCH_F)                          # the brow tine, forking toward the camera
    r.set(1, 5, 1, "moss_block")
    r.glow(1, 6, 1)
    # the leaf cloak behind the antlers
    for x in range(3):
        for y in (5, 6, 7):
            r.leaf(x, y, 0, gs.leaf_for(x, y, 0, 3))
    r.leaf(1, 8, 0, FLEAF)


# ---- THE SEED CACHE: the storage vault ---------------------------------------------------------------------------

def vault_draw(r):
    pad(r)
    # piers on the corners: chiseled feet, mossy courses
    for (x, z) in ((0, 0), (2, 0), (0, 2), (2, 2)):
        r.set(x, 1, z, CHB)
        r.set(x, 2, z, MSB if (x + z) % 4 else CSB)
    # barrels in every face, two high on the camera faces; hay sheaves stacked inside
    for (x, z, f) in ((1, 2, "south"), (0, 1, "west"), (1, 0, "north"), (2, 1, "east")):
        r.set(x, 1, z, "barrel", facing=f, open="false")
    r.set(1, 2, 2, "barrel", facing="south", open="false")
    r.set(0, 2, 1, "barrel", facing="west", open="false")
    r.set(1, 1, 1, "hay_block", axis="y")
    r.set(1, 2, 1, "hay_block", axis="y")
    r.set(1, 2, 0, "hay_block", axis="z")
    r.set(2, 2, 1, "barrel", facing="east", open="false")
    # the beam course: oak beams on the faces, bark over the piers; the stag's eyes and muzzle set in the south beam
    for x in range(3):
        for z in range(3):
            if x in (0, 2) and z in (0, 2):
                r.set(x, 3, z, WOOD, axis="y")
            elif z in (0, 2):
                r.log(x, 3, z, "x")
            elif x in (0, 2):
                r.log(x, 3, z, "z")
            else:
                r.set(x, 3, z, "hay_block", axis="y")
    r.glow(0, 3, 2)
    r.glow(2, 3, 2)
    r.set(1, 3, 2, BIRCH_W, axis="y")
    # no brow row here (the converter's face is the tall one): the mask is a lintel, so the rick shows over it -
    # short antler tines on the eyes, the moss crown between them
    r.set(0, 4, 2, BIRCH_F)
    r.set(2, 4, 2, BIRCH_F)
    r.set(1, 4, 2, "moss_block")
    # the loft: the harvest stacked as a stepped rick of hay sheaves behind the mask - the storage read from the
    # camera, yellow over the grey and brown - with a little oak growing out of its back corner (a flowering tuft)
    for x in range(3):
        for z in (0, 1):
            r.set(x, 4, z, "hay_block", axis="x" if z else "z")
    for (x, z) in ((0, 0), (1, 0), (0, 1), (1, 1)):
        r.set(x, 5, z, "hay_block", axis="y")
    r.log(2, 5, 0)
    r.leaf(2, 5, 1, LEAF)
    r.leaf(2, 6, 0, FLEAF)
    r.leaf(1, 6, 0, LEAF)


# ---- build and check ---------------------------------------------------------------------------------------------

def build(draw, tag):
    r = Rig()
    draw(r)
    mv._leaf_distances(r)
    r.bury_grass()
    r.settle()
    r.check_support(tag)
    r.size = (S, max(y for (_, y, _) in r.b) + 1, S)
    return r.structure()


def wind():
    """wind_generator_verdant, THE BLOSSOM STAG: the Court's spirit tree - stag head on a living trunk with a glowing
    pearl heart, birch antlers before an azalea tier, a cherry-blossom crown the petals fall from."""
    s = build(wind_draw, "wind_generator_verdant")
    check(s, "wind_generator_verdant")
    return s


def converter():
    """energy_converter_verdant, THE ROOTSTILL: the stag's head carved as a mossy stone kiln - a green bud held in its
    antlers, a glowing hearth for a mouth, an iron block (the metal) at its chin."""
    s = build(converter_draw, "energy_converter_verdant")
    check(s, "energy_converter_verdant")
    return s


def vault():
    """storage_vault_verdant, THE SEED CACHE: mossy piers and oak beams round barrels and hay sheaves under a leaf
    thatch, the stag's mask as the gable crest."""
    s = build(vault_draw, "storage_vault_verdant")
    check(s, "storage_vault_verdant")
    return s


def names(s):
    return {p: s.palette[i][0] for p, i in s.blocks.items() if s.palette[i][0] not in ("air", "cave_air")}


FORBIDDEN = ("water", "lava", "sand", "gravel", "_concrete_powder", "anvil", "redstone", "repeater", "comparator",
             "observer", "piston", "lever", "_button", "_pressure_plate", "tnt", "fire", "campfire", "scaffolding",
             "pointed_dripstone", "_door", "_carpet", "candle", "_slab", "vine", "hanging_roots", "spore_blossom",
             "bell", "_banner", "torch", "sea_pickle", "chest", "raw_iron", "copper")


def floating(s):
    n = names(s)
    return [((x, y, z), nm) for (x, y, z), nm in n.items() if y > 0 and not any(
        (x + dx, y + dy, z + dz) in n for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1),
                                                         (0, 0, -1)))]


def check(s, name):
    n = names(s)
    assert (s.size[0], s.size[2]) == (S, S), "%s: footprint %s, must be 3 x 3" % (name, s.size)
    assert s.size[1] <= MAX_H[name], "%s: %d high, over %d" % (name, s.size[1], MAX_H[name])
    assert len(n) <= MAX_BLOCKS, "%s: %d blocks" % (name, len(n))
    assert all((x, 0, z) in n for x in range(S) for z in range(S)), "%s: a hole in layer 0" % name
    bad = sorted({nm for nm in n.values() if any(f in nm for f in FORBIDDEN)})
    assert not bad, "%s: forbidden blocks %s" % (name, bad)
    lone = floating(s)
    assert not lone, "%s: floating %s" % (name, lone)
    for p, i in s.blocks.items():
        nm, props = s.palette[i]
        if nm.endswith("_leaves"):
            assert dict(props).get("persistent") == "true", (name, p)
    if name == "wind_generator_verdant":
        check_motion(s)


def check_motion(s):
    """tools/check_sail_hubs.py check_motion for PETALS, on the Structure: the anchor is inside the footprint and is a
    leaf block; the spiral radius is in 0.5..2.5. Plus: something stands on the anchor (not a bare top a snow cap
    takes) and the fall below it (WindmillRenderClientEvents: down to y - 0.9) is inside the box."""
    n = names(s)
    x, y, z = PETALS
    assert 0 <= x < S and 0 <= z < S and 0 <= y < s.size[1], "petal anchor outside the footprint"
    assert n.get(PETALS, "air").endswith("_leaves"), "petals fall from %s, which is %s" % (PETALS, n.get(PETALS))
    assert 0.5 <= PETAL_RADIUS <= 2.5
    assert (x, y + 1, z) in n, "the petal anchor is a bare top: a snowy biome would cap it"


def audit(path, name):
    """tools/audit_structure_pockets.py on the written nbt: no cell a unit walks into and can't leave, no ground cell
    with no way out, and the centre spawn walk-down is harmless."""
    import audit_structure_pockets as ap
    one_way, ground, roof, rooms, note = ap.audit(name, path)
    assert not one_way and not ground and not rooms, "%s: pockets %s %s %s" % (name, one_way, ground, rooms)
    return roof, note


def count(s):
    return len(names(s))


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "eco_out"
    os.makedirs(out, exist_ok=True)
    for nm, fn in (("wind_generator_verdant", wind), ("energy_converter_verdant", converter),
                   ("storage_vault_verdant", vault)):
        st = fn()
        p = os.path.join(out, nm + ".nbt")
        st.write(p)
        roof, note = audit(p, nm)
        print("%-26s %dx%dx%d %4d blocks  roofs %s%s" % (nm, st.size[0], st.size[1], st.size[2], count(st), roof, note))
