"""Gravebound metal extractor: THE BONE-RIBBED WELL (the Ossuary Well).

lovish (Oct 19 2026): "the metal extractors could be fountains with angels or something more creative, the current
metal extractor building looks really ugly"; his brainstorm gave the Gravebound "a bone-ribbed well with chains over a
green pit". The old one was an open gallows headframe on four dark pillars - correct, but a grey box of piers from the
RTS camera. This one tells the Death faction's story instead: the dead do not dig, they draw the metal up out of a
grave. A great ribcage rises out of the ground round a deep well whose floor glows green (the ore core, the one
glowing tell every extractor shares); bone ribs bow out over the rim and curl in over the pit, the back rib closes
into a pointed bone arch whose keystone is the winch, and chains haul a bone bucket up out of the green light (the
drawn rig: bone wheel, skip and chain - see "The drawn rig" below). Soul fire burns on the arch, small gothic
spirelets with soul lanterns stand on the corners, skulls watch from the rib tips.

Inspiration (never traced): reference 47 (gargoyles in blocks - the T2's crouched gargoyle), 43 (a blackstone pillar
with soul lanterns - the spirelets), 44 (a dark brick wall with chains and finials - the chains lashed across the ribs
and the bare finial carrying the pennant); the ribcage-out-of-the-ground image is the owner's.

STYLE KIT (Death / Gravebound, design/building-revamp-progress.md "Cohesion"): main blocks deepslate (tiles, bricks,
polished) + blackstone (polished bricks, chiseled, walls); accent bone (bone blocks, smooth quartz stairs as the
bone's rounded bends - the same pale tone); light soul lanterns and soul campfires + a green glow (verdant froglight);
silhouette motif spires; signature detail chains and skulls.

THE BUILDING STANDARD on a 5 x 5 patch:
  1. three depths on the camera faces (west x=0, south z=4): the spirelets and rib roots on the outer ring, the ribs'
     bends one block in over the rim, the pit and the arch behind them;
  2. a broken top: rib tips, the pointed arch with soul fire, two spirelets, the pennant finial (T2: a taller arch, a
     taller spire, a gargoyle) - never a solid mass (the first passes, solid rib walls, read as a block of bone);
  3. mixed materials: bone and quartz-stair bends; deepslate tiles / bricks / polished; blackstone bricks, chiseled,
     walls; chains as thin members;
  4. Gravebound style: dark gothic blackstone and deepslate, pale bone, soul fire, green grave-light;
  5. dressing: skulls (white and wither), a spare bucket (cauldron) on the rim, chains lashed across the ribs;
  6. tier 2 (the Deep Ossuary): the pit glows brighter (a full 3x3 of green light), the back arch rises three blocks
     into a tall pointed arch with soul fire on its crown and a lantern under it, the front ribs grow, more chains, a
     taller spire with a lantern and the pennant finial, a gargoyle crouched on the south-east spirelet; the old
     keystone becomes a waxed copper winch band (the tier-2 marker, read as the hoist's metal band);
  7. lights: the green pit (the ore core), soul lanterns on the spirelets, soul fire on the arch.

Hard rules kept: 5 x 5 footprint, 14 tall; no water, lava, spreading fire or gravity blocks; check_support (nothing
pops off), check_mex_tiers (T1 has no copper marker, T2 keeps every T1 block and has the waxed_copper_block) and
check_rig (the drawn parts' room, as tools/check_parts.py, the GameTest every_extractor_rig_part_has_room_to_move and
gen_structures._grave_rig_check see it) run on every build; tools/audit_structure_pockets.py finds no pocket a unit can
get into. T1 97 blocks, T2 118.

The drawn rig (BuildingParts.RIGS, PartRenderClientEvents) STAYS - the well is exactly where a hoist belongs: the bone
wheel turns in the vertical plane x = 2 between the two ribs, on the west face of the bone keystone (the axle); the
bone bucket rides the shaft x=2, z=2 straight up out of the green core and tips at the top; the pennant flies from the
north-east spirelet's finial. ANCHORS below is in GRAVE_RIG_ANCHORS' format and replaces it (both tiers moved).
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs  # noqa: E402

H = 14                       # canvas height (T1 and T2 share it so check_mex_tiers compares like with like)
DRY = dict(waterlogged="false")
LANTERN = dict(hanging="false", waterlogged="false")
CAMPFIRE = dict(lit="true", facing="south", signal_fire="false", waterlogged="false")
BEND = "smooth_quartz_stairs"   # bone has no stairs: smooth quartz is the same pale tone and rounds the ribs' bends
SKULL = dict(rotation=str(gs.TO_CAMERA))
CHAIN_X = dict(axis="x", waterlogged="false")
PBB, CPB, PBW = "polished_blackstone_bricks", "chiseled_polished_blackstone", "polished_blackstone_wall"

# The drawn moving parts' anchors, in gen_structures.GRAVE_RIG_ANCHORS' format (structure cells, unrotated). The wheel
# (radius 1.5 kept clear = the hub's 3x3 in the plane x = 2) turns on the west face of the axle block east of the hub;
# the skip's shaft runs from the core up to under the hub; the pennant flies from the top face of the finial.
ANCHORS = {
    "T1": {"sheave_hub": (2, 7, 2), "sheave_axis": "x", "sheave_outward": "west", "sheave_radius": 1.5,
           "sheave_axle": (3, 7, 2), "skip_bottom": (2, 1, 2), "skip_top": (2, 5, 2), "pennant_pole_top": (4, 9, 0)},
    "T2": {"sheave_hub": (2, 10, 2), "sheave_axis": "x", "sheave_outward": "west", "sheave_radius": 1.5,
           "sheave_axle": (3, 10, 2), "skip_bottom": (2, 1, 2), "skip_top": (2, 8, 2), "pennant_pole_top": (4, 11, 0)},
}


def _bone(r, x, y, z, axis="y"):
    r.set(x, y, z, "bone_block", axis=axis)


def _rib(r, x, closed, north_top, south_top):
    """A pair of great ribs in the y-z plane at x, rooted on the rim at z = 0 and z = 4. Each rises two blocks, bends
    in over the pit (a rounded quartz shoulder outside, an upside-down stair hollowing the bend underneath - the curve
    a ribcage has) and climbs the pit's edge to its tip, which curls in over the pit. `closed` joins the two tips with
    a bone keystone over the pit (the pointed arch); open, they stop short and the camera looks into the well."""
    for zo, zi, inward, outward, top in ((0, 1, "south", "north", north_top), (4, 3, "north", "south", south_top)):
        _bone(r, x, 1, zo)
        _bone(r, x, 2, zo)
        r.stair(x, 3, zo, inward, name=BEND)
        r.stair(x, 3, zi, outward, "top", name=BEND)
        for y in range(4, top):
            _bone(r, x, y, zi)
        r.stair(x, top, zi, inward, name=BEND)
    if closed:
        _bone(r, x, max(north_top, south_top), 2, "x")


def _spire(r, x, z, top, lamp=True):
    """A gothic spirelet (ref 43): a blackstone plinth, a chiseled die, a thin wall shaft, a soul lantern on top (or
    a bare finial)."""
    r.set(x, 1, z, PBB)
    r.set(x, 2, z, CPB)
    for y in range(3, top):
        r.set(x, y, z, PBW)
    if lamp:
        r.set(x, top, z, "soul_lantern", **LANTERN)
    else:
        r.set(x, top, z, PBW)


# ---- tier 1: the Ossuary Well ----------------------------------------------------------------------------------
# Plan (the camera looks from the south-west: the west face x=0 and the south face z=4 are the show):
#   corners      spirelets (blackstone), the north-east one the pennant's finial
#   x = 1        the front ribs, broken open (the camera looks between them into the pit)
#   x = 2        the shaft: the bucket rides up out of the green core, the wheel turns over it
#   x = 3        the back ribs, closed into a pointed arch; its keystone is the winch (the wheel's axle)
#   x = 4        small ribs on the east rim close the cage behind the pit

def _well_t1(r):
    # the grave plot: blackstone corner stones, a deepslate tile ring, polished deepslate in the well and the green
    # glow of the ore core in a cross under the shaft (the extractor's glowing tell, seen through the ribs)
    for x in range(5):
        for z in range(5):
            corner = x in (0, 4) and z in (0, 4)
            edge = x in (0, 4) or z in (0, 4)
            r.set(x, 0, z, PBB if corner else "deepslate_tiles" if edge else "polished_deepslate")
    for (x, z) in ((2, 2), (1, 2), (3, 2), (2, 1), (2, 3)):
        r.set(x, 0, z, "verdant_froglight", axis="y")
    # the well's lip on the free ring cells: a stair rising toward the pit at the back, low slabs on the camera sides
    # (units step over them; the pit's floor is level with the ground, so nothing traps)
    r.stair(2, 1, 0, "south", name="deepslate_brick_stairs")
    r.stair(4, 1, 2, "west", name="deepslate_brick_stairs")
    r.set(2, 1, 4, "deepslate_tile_slab", type="bottom", **DRY)
    for z in (2, 3):
        r.set(0, 1, z, "deepslate_tile_slab", type="bottom", **DRY)
    # small ribs on the east rim close the ring behind the pit
    for z, top in ((1, 4), (3, 3)):
        for y in range(1, top):
            _bone(r, 4, y, z)
        r.stair(4, top, z, "west", name=BEND)
    # the great ribs: the back pair closed into the arch, the front pair broken open toward the camera
    _rib(r, 3, True, 7, 7)
    _rib(r, 1, False, 6, 5)
    r.set(3, 8, 2, "soul_campfire", **CAMPFIRE)              # soul fire on the arch's crown
    for z in (1, 3):                                         # chains lashed across the ribcage (ref 44)
        r.set(2, 4, z, "chain", **CHAIN_X)
    # skulls on the front rib tips, looking at the camera; a spare bucket on the rim
    r.set(1, 7, 1, "wither_skeleton_skull", **SKULL)
    r.set(1, 6, 3, "skeleton_skull", **SKULL)
    r.set(0, 1, 1, "cauldron")
    # the spirelets: different heights so the top breaks, the north-east one a bare finial for the pennant
    _spire(r, 0, 0, 6)
    _spire(r, 4, 0, 9, lamp=False)
    _spire(r, 4, 4, 5)
    _spire(r, 0, 4, 3)
    r.set(0, 2, 2, "skeleton_skull", **SKULL)               # a skull on the west lip


# ---- tier 2: the Deep Ossuary ------------------------------------------------------------------------------------

def _well_t2(r):
    _well_t1(r)
    # the pit glows brighter: the whole 3x3 floor is green light (the deeper vein)
    for x in (1, 3):
        for z in (1, 3):
            r.set(x, 0, z, "verdant_froglight", axis="y")
    # the back arch rises three blocks into a tall pointed arch; the old keystone becomes the winch's copper band
    # (waxed_copper_block: the tier-2 marker MetalExtractor.getUpgradeLevel looks for) with a lantern lit on it, and
    # the new keystone is the axle the wheel turns on, three blocks higher (the shaft is longer: a deeper well)
    r.set(3, 7, 2, "waxed_copper_block")
    r.set(3, 8, 2, "soul_lantern", **LANTERN)
    for z, f in ((1, "south"), (3, "north")):
        for y in (7, 8, 9):
            _bone(r, 3, y, z)
        r.stair(3, 10, z, f, name=BEND)
    _bone(r, 3, 10, 2, "x")
    r.set(3, 11, 2, "soul_campfire", **CAMPFIRE)
    # the front ribs grow a block, their skulls ride up with them
    _bone(r, 1, 6, 1)
    r.stair(1, 7, 1, "south", name=BEND)
    r.set(1, 8, 1, "wither_skeleton_skull", **SKULL)
    _bone(r, 1, 5, 3)
    r.stair(1, 6, 3, "north", name=BEND)
    r.set(1, 7, 3, "skeleton_skull", **SKULL)
    for z in (1, 3):                                         # a second course of chains
        r.set(2, 6, z, "chain", **CHAIN_X)
    # taller spires: the north-west one carries its lantern three blocks higher, the pennant finial rises too
    for y in range(6, 9):
        r.set(0, y, 0, PBW)
    r.set(0, 9, 0, "soul_lantern", **LANTERN)
    for y in range(9, 12):
        r.set(4, y, 0, PBW)
    # a gargoyle crouched on the south-east spirelet (ref 47): a chiseled body, its folded wings upside-down stairs
    # spread toward the two camera faces, a wither skull for the head watching the camera
    r.set(4, 5, 4, CPB)
    r.stair(4, 6, 4, "north", name="polished_blackstone_stairs")
    r.stair(3, 6, 4, "east", "top", name="polished_blackstone_stairs")
    r.stair(4, 6, 3, "south", "top", name="polished_blackstone_stairs")
    r.set(4, 7, 4, "wither_skeleton_skull", **SKULL)


def _build(fn, tag):
    """The rig canvas from gen_structures (settles stairs / walls / chains into their joined shapes)."""
    r = gs._SunRig(5, H, 5)
    fn(r)
    r.settle()
    r.check_support("grave " + tag)
    return r


def check_rig(s, tier):
    """The drawn parts keep their room in the built structure - the rules of tools/check_parts.py room(), the GameTest
    every_extractor_rig_part_has_room_to_move and gen_structures._grave_rig_check: the wheel's disc (the hub's 3x3 in
    its plane), the skip's shaft and the chain's run up to the hub, and the pennant's 5x5 layer over the pole are air;
    the axle and the pole stand; the axle is behind the wheel's outward face; the spawn column (2, 1..5, 2) is air."""
    a = ANCHORS[tier]
    solid = {p for p, i in s.blocks.items() if s.palette[i][0] not in ("air", "cave_air")}
    hx, hy, hz = a["sheave_hub"]
    assert a["sheave_axis"] == "x"
    clear = [(hx, hy + v, hz + u) for u in (-1, 0, 1) for v in (-1, 0, 1)]
    bx, by, bz = a["skip_bottom"]
    assert (bx, bz) == (hx, hz) and by < a["skip_top"][1] < hy, f"grave rig {tier}: the shaft is not under the hub"
    clear += [(bx, y, bz) for y in range(by, hy + 1)]
    clear += [(2, y, 2) for y in range(1, 6)]
    px, py, pz = a["pennant_pole_top"]
    clear += [(px + dx, py + 1, pz + dz) for dx in range(-2, 3) for dz in range(-2, 3)]
    busy = sorted(c for c in set(clear) if c in solid)
    assert not busy, f"grave rig {tier}: must stay air for the moving parts: {busy}"
    step = {"west": (-1, 0), "east": (1, 0)}[a["sheave_outward"]]
    assert tuple(a["sheave_axle"]) == (hx - step[0], hy, hz - step[1]), f"grave rig {tier}: axle not behind the wheel"
    for k in ("sheave_axle", "pennant_pole_top"):
        assert tuple(a[k]) in solid, f"grave rig {tier}: no {k} block"


def t1():
    """T1 'Ossuary Well': a ribcage of bone round a green-glowing well, a bone arch with soul fire carrying the drawn
    winch, gothic spirelets with soul lanterns, skulls and chains. No copper of any kind. 5 x 14 x 5 (10 tall)."""
    s = _build(_well_t1, "T1").structure()
    check_rig(s, "T1")
    return s


def t2():
    """T2 'Deep Ossuary': the T1 with a brighter pit, a tall pointed arch (the winch three blocks higher, a copper
    winch band - the tier marker), longer ribs, more chains, taller spires and a gargoyle. Every T1 block stays a block
    (check_mex_tiers). 5 x 14 x 5 (12 tall)."""
    s = _build(_well_t2, "T2").structure()
    check_rig(s, "T2")
    gs.check_mex_tiers(t1(), s, "gravebound")
    return s


if __name__ == "__main__":
    # python3 tools/mex_grave.py OUT_DIR -> OUT_DIR/metal_extractor_dark.nbt, metal_extractor_t2_dark.nbt
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    for name, fn in (("metal_extractor_dark", t1), ("metal_extractor_t2_dark", t2)):
        s = fn()
        s.write(os.path.join(out, name + ".nbt"))
        print(name, sum(1 for i in s.blocks.values() if s.palette[i][0] != "air"))
