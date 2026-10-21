"""Ashborn (structure variant "_nether", Piglins in code) metal extractor: THE STONE GIANT (lovish, Oct 19 2026: "the
metal extractors could be fountains with angels or something more creative, the current metal extractor building looks
really ugly"; the owner chose the Stone Giant for the Ashborn).

The story: the Ashborn are the humans of a volcanic island cult, and they say their first smith was a giant of cooled
lava. Their mine is his statue: a basalt smith built waist-deep into a stepped volcano forge, a bronze hammer RAISED
HIGH in his right fist, about to strike the glowing ore on an anvil-stone over the vent. His left fist holds the ore on
the anvil with chain tongs. Under a dark hood his face glows like the vent, and a magma crack splits his chest. Nothing
floats: the arms grow out of the pauldrons and the hammer stands on its haft in the fist. There is no real lava. The
vent is shroomlight set flush, and the only magma is the chest crack, where no unit can stand.

ASHBORN STYLE KIT (cohesion: every Ashborn building uses it, so a growing base reads as one place). The owner renamed the
faction so they don't look like Reign of Nether's Piglins, so the kit has no pigs or boars, no gold or gilded
blackstone, no nether brick, netherrack, bastions, portals or crimson/warped wood.
- main blocks: basalt (polished basalt, smooth basalt), plain tuff (no 1.21 tuff bricks), obsidian used sparingly
- accent: glowing magma cracks (shroomlight / ochre froglight set flush; magma_block only where no unit stands)
- trim: BRONZE = waxed cut copper / waxed exposed cut copper and their stairs / slabs
- light: fire bowls (lit campfires on obsidian or bronze)
- silhouette motif: the stepped volcano-temple (here the mound the giant rises from steps up in stairs)
- signature detail: the glowing crack (the vent under the anvil, the crack up the mound, the chest, the face)

THE BUILDING STANDARD (design/building-revamp-progress.md) on 5 x 5:
1. Three depths: on the south face the anvil and the tongs fist stand proud (z 3), the chest stairs and toe stairs step
   back (z 2), the torso and mound behind them (z 0-1). On the west face the pauldron and the hammer arm stand out from
   the torso, with the stepped mound below.
2. Broken top: the raised hammer (the highest point, the first thing the RTS camera sees), the hood and the crown
   (spikes at T2) over the pauldrons.
3. Mixed materials: basalt / smooth basalt / tuff / deepslate stairs for the stone, two bronze tones for the trim, chains
   and lightning rods (the hammer haft) as thin members.
5. Dressing: fire bowls at the front corners, the tongs chain, glowing cracks in the pad.
6. T2 grows the same giant: a spiked bronze crown, bronze plates on both shoulders, fire bowls on the back steps, the
   front bowls raised on bronze, and more cracks in the pad. The anvil gets a bronze band, the waxed_copper_block tier
   marker.
7. Lights: the ore (ochre froglight on the anvil, the shared "one glowing ore core" tell), the face, the vent and the
   cracks glow warm orange, and the campfire bowls flicker.

Tier 1 holds no copper_block / cut_copper / waxed_copper_block. Checked: check_mex_tiers,
_SunRig.check_support, tools/audit_structure_pockets.py (no pockets).
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs  # noqa: E402

DRY = dict(waterlogged="false")
CAMPFIRE = gs.CAMPFIRE
ROD_UP = dict(facing="up", powered="false", waterlogged="false")
BASALT = "polished_basalt"
SMOOTH = "smooth_basalt"
TUFF = "tuff"
DARK_ST = "polished_deepslate_stairs"
BRONZE = "waxed_cut_copper"
BRONZE_W = "waxed_exposed_cut_copper"
BRONZE_ST = "waxed_cut_copper_stairs"
BRONZE_SL = "waxed_cut_copper_slab"
H = 14


def _t1(r):
    # the pad: basalt corner posts, a smooth basalt kerb, a tuff floor, and one glowing crack (shroomlight, flush, safe to
    # walk on) running from the vent out to the south edge: the signature detail at the camera's feet
    for x in range(5):
        for z in range(5):
            corner = x in (0, 4) and z in (0, 4)
            edge = x in (0, 4) or z in (0, 4)
            if corner:
                r.set(x, 0, z, BASALT, axis="y")
            else:
                r.set(x, 0, z, SMOOTH if edge else TUFF)
    r.set(3, 0, 4, "shroomlight")
    # the volcano mound the giant rises from: he is built waist-deep into the forge, so his bulk sits on stone and the
    # mound steps up in stairs (the Ashborn stepped volcano-temple). A 5-wide statue on a 5 x 5 footprint has no room
    # for legs that read, so the mound replaces them and gives the shoulders and the hammer the height instead
    for x in range(5):
        r.set(x, 1, 0, TUFF)
        if x in (0, 4):
            r.set(x, 1, 1, TUFF)
        else:
            r.set(x, 1, 1, BASALT, axis="y")
    for x in range(5):
        r.stair(x, 1, 2, "north", name=DARK_ST)
    r.set(2, 1, 2, "shroomlight")                         # the vent mouth glowing under the anvil
    for x in range(5):
        r.set(x, 2, 0, SMOOTH)
    r.stair(0, 2, 1, "east", name=DARK_ST)
    r.stair(4, 2, 1, "west", name=DARK_ST)
    for x in (1, 2, 3):
        r.set(x, 2, 1, SMOOTH)
    r.set(2, 2, 1, "shroomlight")                         # a glowing crack up the mound's face
    r.stair(0, 3, 0, "east", name=DARK_ST)
    r.stair(4, 3, 0, "west", name=DARK_ST)
    for x in (1, 2, 3):
        r.set(x, 3, 0, TUFF)
        r.stair(x, 3, 1, "north", name=DARK_ST)
    # the giant: a bronze belt, a smooth basalt torso, and a magma crack in the chest (covered above and walled round,
    # so no unit ever stands on it)
    for x in (1, 2, 3):
        r.set(x, 4, 0, SMOOTH)
        r.set(x, 4, 1, BRONZE)
        for y in (5, 6):
            r.set(x, y, 0, SMOOTH)
            r.set(x, y, 1, SMOOTH)
    r.set(2, 5, 1, "magma_block")
    r.set(2, 6, 1, BASALT, axis="y")
    r.stair(1, 6, 2, "north", "top", name=DARK_ST)        # the chest juts forward
    r.stair(3, 6, 2, "north", "top", name=DARK_ST)
    # broad shoulders: bronze pauldrons on the outer columns, their front edges upside-down stairs (depth plus trim)
    for z in (0, 1):
        r.set(0, 7, z, BRONZE_W)
        r.set(4, 7, z, BRONZE_W)
        r.set(1, 7, z, SMOOTH)
        r.set(3, 7, z, SMOOTH)
    r.stair(0, 7, 2, "north", "top", name=BRONZE_ST)
    r.stair(4, 7, 2, "north", "top", name=BRONZE_ST)
    r.set(2, 7, 0, SMOOTH)
    r.set(2, 7, 1, BASALT, axis="y")
    # hooded head: one block wide over the broad shoulders (the giant's bulk is in the shoulders, so the head stays
    # small), the face a glowing slit in the hood's shadow, the hood's cheeks stairs, a bronze crown on top
    r.set(2, 8, 1, "shroomlight")
    r.set(2, 8, 0, BASALT, axis="y")
    r.stair(1, 8, 1, "east", name=DARK_ST)
    r.stair(3, 8, 1, "west", name=DARK_ST)
    r.stair(2, 9, 1, "north", "top", name=DARK_ST)
    r.set(2, 9, 0, BASALT, axis="y")
    r.set(2, 10, 1, BRONZE_SL, type="bottom", **DRY)
    r.set(2, 10, 0, BRONZE_SL, type="bottom", **DRY)
    # hammer arm (west): upper arm up from the pauldron, the forearm bent forward, the fist at the haft
    r.set(0, 8, 1, BASALT, axis="y")
    r.set(0, 9, 1, BASALT, axis="y")
    r.set(0, 10, 1, BASALT, axis="z")
    r.set(0, 10, 2, TUFF)
    r.set(0, 11, 2, "lightning_rod", **ROD_UP)
    r.set(0, 12, 2, "lightning_rod", **ROD_UP)
    for z in (1, 2, 3):
        r.set(0, 13, z, BRONZE if z == 2 else BRONZE_W)
    # the other arm (east) hangs straight and reaches forward: a bronze-gloved fist holding the tongs on the ore
    r.set(4, 6, 1, BASALT, axis="y")
    r.set(4, 5, 1, BASALT, axis="y")
    r.set(4, 4, 1, BASALT, axis="y")
    r.set(4, 4, 2, BASALT, axis="z")
    r.set(4, 4, 3, BRONZE_W)
    r.set(3, 4, 3, "chain", axis="x", **DRY)
    r.set(3, 3, 3, "chain", axis="y", **DRY)
    # the anvil-stone in front of the vent: a basalt block stepped out with deepslate stairs (a waisted anvil shape),
    # and on it THE ORE, an ochre froglight. It is the glowing core every extractor shows the camera
    r.set(2, 1, 3, BASALT, axis="y")
    r.stair(1, 1, 3, "east", name=DARK_ST)
    r.stair(3, 1, 3, "west", name=DARK_ST)
    r.stair(1, 2, 3, "east", "top", name=DARK_ST)
    r.set(2, 2, 3, SMOOTH)
    r.stair(3, 2, 3, "west", "top", name=DARK_ST)
    r.set(2, 3, 3, "ochre_froglight", axis="y")
    # fire bowls on obsidian plinths at the front corners (the kit's light): the obsidian is used sparingly
    for x in (0, 4):
        r.set(x, 1, 4, "obsidian")
        r.set(x, 2, 4, "campfire", **CAMPFIRE)


def _t2(r):
    """T2 over T1 (only adds or replaces): the giant grows a spiked bronze crown and armoured shoulders, the forge gets
    more fire bowls and more glowing cracks, and the anvil gets its bronze band (the tier marker)."""
    _t1(r)
    # the tier marker: a bronze band on the anvil, right under the glowing ore
    r.set(2, 2, 3, "waxed_copper_block")
    # the crown: a solid bronze circlet with three spikes (lightning rods), a broken top the camera picks out
    r.set(2, 10, 1, BRONZE)
    r.set(2, 10, 0, BRONZE)
    r.stair(1, 10, 1, "east", name=BRONZE_ST)
    r.stair(3, 10, 1, "west", name=BRONZE_ST)
    r.set(2, 11, 1, "lightning_rod", **ROD_UP)
    r.set(1, 11, 1, "lightning_rod", **ROD_UP)
    r.set(3, 11, 1, "lightning_rod", **ROD_UP)
    # armoured shoulders: a bronze plate behind the hammer arm, and stepped bronze plates over the tongs shoulder
    r.set(0, 8, 0, BRONZE_W)
    r.stair(4, 8, 0, "west", name=BRONZE_ST)
    r.stair(4, 8, 1, "west", name=BRONZE_ST)
    # fire bowls on the mound's back steps, which also raises the temple's stepped silhouette
    for x in (0, 4):
        r.set(x, 3, 0, TUFF)
        r.set(x, 4, 0, "campfire", **CAMPFIRE)
    # a wider crack in the pad, and the front bowls raised on bronze (taller flames)
    r.set(1, 0, 4, "shroomlight")
    r.set(2, 0, 4, "shroomlight")
    for x in (0, 4):
        r.set(x, 2, 4, BRONZE_W)
        r.set(x, 3, 4, "campfire", **CAMPFIRE)


def _build(fn):
    """The Sunforged rig canvas from gen_structures (it settles stairs / walls / chains into their joined shapes)."""
    r = gs._SunRig(5, H, 5)
    fn(r)
    r.settle()
    return r


def t1():
    """T1 'Stone Giant': a basalt smith built into a volcano forge, his bronze hammer raised over the glowing ore.
    5 x 14 x 5."""
    r = _build(_t1)
    r.check_support("ashborn T1")
    return r.structure()


def t2():
    """T2 'Crowned Giant': the T1 with a spiked bronze crown, armoured shoulders, four fire bowls and a bronze-banded
    anvil. Every T1 block stays a block (check_mex_tiers). 5 x 14 x 5."""
    r = _build(_t2)
    r.check_support("ashborn T2")
    s = r.structure()
    gs.check_mex_tiers(t1(), s, "ashborn")
    return s


if __name__ == "__main__":
    # python3 tools/mex_ashborn.py OUT_DIR -> OUT_DIR/mex_ashborn_t1.nbt, mex_ashborn_t2.nbt, prints the block counts
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    for name, fn in (("t1", t1), ("t2", t2)):
        s = fn()
        s.write(os.path.join(out, "mex_ashborn_%s.nbt" % name))
        print(name, sum(1 for i in s.blocks.values() if s.palette[i][0] != "air"))
