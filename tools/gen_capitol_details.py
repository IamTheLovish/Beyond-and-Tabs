#!/usr/bin/env python3
"""Detail pass over the three Reign of Nether capitols (design/biome_bases.md section 4): the Town Centre (Sunforged),
the Mausoleum (Gravebound) and the Central Portal (Horde) are RoN's hand-made structures, the only capitols the
building-detail pass left untouched. This draws a detail layer over each - layering, lights, banners, braziers, a
moving part - to the level of the other capitols, and writes them into BOTH structure trees (assets/ and data/).

Usage: python3 tools/gen_capitol_details.py [town_centre] [mausoleum] [central_portal]   (default: all three)

The pristine RoN structures are kept in tools/capitol_bases/ and every run starts from them, so a run is
reproducible and an edit here is never applied twice. Rules each overlay keeps (the GameTests are the truth):
- the footprint AND the bounding box stay exactly as RoN drew them: every block goes inside the original size, so
  minCorner, maxCorner and centrePos - the spawn point, the Central Portal's lighting fire, the war banner's mount -
  never move;
- build time is unchanged: TownCentre / Mausoleum / CentralPortal set buildTimeBlocks to RoN's block counts
  (RON_BLOCKS below); their HP is a fixed maxHealth, so the extra blocks change neither;
- the starting block types (the instant foundation) stay where they were, and nothing new is one of them in the
  foundation layers;
- nothing pops off (Canvas.unsupported) and no new cell is a pocket (tools/audit_structure_pockets.py);
- the Central Portal's obsidian frame and the air inside it are untouched (the portal has to light).
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from preview_structure import load_nbt   # noqa: E402
from gen_structures import OUT, OUT_DATA   # noqa: E402
from gen_lab_levels import Canvas, b, wall_banner, is_air   # noqa: E402

BASE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "capitol_bases")
# RoN's non-air block counts: the capitols' buildTimeBlocks (their build time must not change with the detail)
RON_BLOCKS = {"town_centre": 305, "mausoleum": 364, "central_portal": 314}

CAMPFIRE = dict(lit="true", facing="north", signal_fire="false", waterlogged="false")
WALL_POST = dict(up="true", north="none", east="none", south="none", west="none", waterlogged="false")
# standing skull rotation (16ths of a turn): 0 faces south, 4 west, 8 north, 12 east
FACES_OUT = {"north": "8", "south": "0", "west": "4", "east": "12"}


def base(name):
    nbt = load_nbt(os.path.join(BASE_DIR, name + ".nbt"))
    pal = nbt["palette"]
    blocks = {}
    for blk in nbt["blocks"]:
        st = pal[blk["state"]]
        # vanilla ids short (the overlays compare "nether_bricks"), any other namespace kept: stripped, a mod's block
        # was written back as "minecraft:<name>" - a block nobody registers, which loads as air (PR 28's
        # structure_palettes_name_only_registered_blocks)
        ns, _, short = st["Name"].rpartition(":")
        blocks[tuple(blk["pos"])] = (short if ns in ("", "minecraft") else st["Name"], dict(st.get("Properties", {})))
    non_air = sum(1 for nm, _ in blocks.values() if not is_air(nm))
    assert non_air == RON_BLOCKS[name], "%s: the base has %d blocks, RON_BLOCKS says %d" % (name, non_air, RON_BLOCKS[name])
    return Canvas(tuple(nbt["size"]), blocks)


def finish(c, name):
    """Leaves get their real distance, nothing may pop off, the box stays RoN's; returns the Structure to write."""
    c.fix_leaves()
    loose = c.unsupported()
    assert not loose, "%s: these would pop off: %s" % (name, loose)
    size = (c.sx, c.sy0, c.sz)
    assert c.height() == c.sy0, "%s grew above RoN's box (%d > %d)" % (name, c.height(), c.sy0)
    from gen_structures import Structure
    s = Structure(*size)
    for (x, y, z), (nm, props) in sorted(c.blocks.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
        s.set(x, y, z, nm, **props)
    return s


def expect(c, x, y, z, *names):
    """Asserts a cell holds what the overlay expects to replace (catches an edit drifting off RoN's geometry)."""
    nm = c.name(x, y, z)
    assert nm in names, "(%d,%d,%d) holds %s, expected %s" % (x, y, z, nm, names)


# ---------------------------------------------------------------------------------------------- Town Centre
def mausoleum():
    """The Gravebound capitol is no longer an overlay on RoN's crypt: it is the Necropolis Cathedral
    (tools/capitol_mausoleum.py, Oct 19), 15 x 22 x 15 - the owner's "a lot bigger, a lot taller, a lot more detailed"."""
    import capitol_mausoleum
    return capitol_mausoleum.build()


def central_portal():
    """The Horde's Central Portal: RoN's towering obsidian portal in a nether-brick frame on a netherrack platform,
    turned into the war camp's heart:
    - braziers on the platform's four corner piers: blackstone posts with lit campfires (smoke columns, firelight);
    - red war banners down the frame pillars' camera-facing (+z) side;
    - a gilded crown: gilded blackstone in the crown's corners, a gold block at its heart, magma eyes glowing over the
      portal front and back;
    - skull totems (bone posts with wither skulls) at three platform corners, a bone post under RoN's stray
      netherwart block at the fourth;
    - crimson growth: two nylium patches with crimson roots and a fungus on the platform.
    The obsidian frame and the air inside it are untouched (the portal must light). Ambience: embers off the braziers.
    11 x 16 x 9, as RoN's."""
    c = base("central_portal")
    frame = {(x, y, 4) for x in (3, 7) for y in range(2, 12)} | {(x, 12, 4) for x in (4, 5, 6)} \
        | {(x, 1, 4) for x in (4, 5, 6)}
    inside = {(x, y, 4) for x in (4, 5, 6) for y in range(2, 12)}
    before = {p: c.get(*p) for p in frame | inside}
    for (x, z) in ((1, 2), (9, 2), (1, 6), (9, 6)):
        expect(c, x, 1, z, "chiseled_nether_bricks")
        c.set(x, 2, z, b("polished_blackstone_wall", **WALL_POST))
        c.set(x, 3, z, b("campfire", **CAMPFIRE))
    for x in (3, 7):
        for y in (6, 10):
            expect(c, x, y, 5, "chiseled_nether_bricks")
            c.set(x, y, 6, wall_banner("red", "south"))
    expect(c, 5, 13, 4, "nether_bricks")
    c.set(5, 13, 4, b("gold_block"))
    for (x, z) in ((3, 3), (7, 3), (3, 5), (7, 5)):
        expect(c, x, 13, z, "chiseled_nether_bricks")
        c.set(x, 13, z, b("gilded_blackstone"))
    for z in (3, 5):
        assert c.empty(5, 12, z), (5, 12, z)
        c.set(5, 12, z, b("magma_block"))
    for (x, z) in ((0, 1), (10, 1), (10, 7)):
        assert c.empty(x, 1, z) and c.empty(x, 2, z), (x, z)
        c.set(x, 1, z, b("bone_block", axis="y"))
        c.set(x, 2, z, b("wither_skeleton_skull", rotation=FACES_OUT["west" if x == 0 else "east"]))
    # RoN's stray block names "reignofnether:decayable_netherwart_block", an id its BlockRegistrar never had (it
    # registers decayable_nether_wart_block): it was always air. A vanilla nether wart block takes its place - the
    # decayable one is for growth that should rot away, not a capitol's ornament.
    assert c.empty(0, 1, 7) and c.name(0, 2, 7) == "reignofnether:decayable_netherwart_block"
    c.set(0, 2, 7, b("nether_wart_block"))
    c.set(0, 1, 7, b("bone_block", axis="y"))
    for (x, z, plant) in ((2, 6, "crimson_fungus"), (3, 6, "crimson_roots"), (8, 2, "crimson_roots")):
        expect(c, x, 1, z, "netherrack")
        c.set(x, 1, z, b("crimson_nylium"))
        c.set(x, 2, z, b(plant))
    for p, blk in before.items():
        assert c.get(*p) == blk, "the portal frame changed at %s" % (p,)
    return finish(c, "central_portal")


def town_centre():
    """The Sunforged capitol is no longer an overlay on RoN's house: it is the Temple of the Sun
    (tools/capitol_town_centre.py, Oct 19), 15 x 20 x 15 - the owner's "a lot bigger, a lot taller, a lot more detailed"."""
    import capitol_town_centre
    return capitol_town_centre.build_grand()


CAPITOLS = {"town_centre": town_centre, "mausoleum": mausoleum, "central_portal": central_portal}

if __name__ == "__main__":
    names = sys.argv[1:] or list(CAPITOLS)
    for n in names:
        if n not in CAPITOLS:
            sys.exit("unknown capitol %s (one of %s)" % (n, ", ".join(CAPITOLS)))
    built = {n: CAPITOLS[n]() for n in names}
    for out in (OUT, OUT_DATA):
        os.makedirs(out, exist_ok=True)
        for n, s in built.items():
            s.write(f"{out}/{n}.nbt")
