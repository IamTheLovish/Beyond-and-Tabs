#!/usr/bin/env python3
"""The Brassworks Rail Battery (rail_battery.nbt): a coil mast on a blackstone plinth. Tests: tools/test_rail_battery.py.

Main's battery (sweep 3/10): a full 3 x 3 pedestal to y 2 with a heap of copper stairs, rods and a slab on top, top at
y 5, an unlit lamp. Nothing reads at RTS but a copper lump. The footprint stays 3 x 3 (the small-defence size, like the
Gatling Posts); the battery grows up into a slim tower with a crown band, the bar's "slim towers with crown bands".

The plan (x 0..2 west to east, z 0..2 north to south; the camera sees the top, the west face x 0 and the south face z 2):
  y 0     foundation: deepslate bricks, polished blackstone at the corners (both starting types in layer 0)
  y 1     plinth: polished blackstone bricks
  y 2     plinth cap: blackstone brick stairs climbing in on the four sides, slabs at the corners, the brass coil foot
  y 3..7  the mast: four blackstone-brick wall posts at the corners, the sides open, and in the middle the coil stack -
          brass, a lit shroomlight, weathered (teal) brass, a lit shroomlight, brass: the coils glow out of the open sides
  y 8     the crown band: brass corner blocks, blackstone-brick corbels (upside-down stairs) on the sides, a shroomlight
  y 9     the emitter: lanterns on the corners, four lightning-rod prongs on the sides, a brass head
  y 10    the rail: one lightning rod pointing up (RailBatteryPlacement fires from the box's top)

Palette (BiomePalette): blackstone, deepslate, copper, lights, chain and lanterns are kept everywhere; lightning rods
are protected.

Usage: python3 tools/def_rail_battery.py [OUT_DIR ...]   (default: both shipped copies)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs   # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHIPPED = [os.path.join(ROOT, "bar", "src", "main", "resources", d, "reignofnether", "structures")
           for d in ("assets", "data")]
W, H = 3, 11
CORNERS = ((0, 0), (2, 0), (0, 2), (2, 2))
SIDES = ((1, 0), (0, 1), (2, 1), (1, 2))
SOOT, SOOT_B = "polished_blackstone", "polished_blackstone_bricks"
SOOT_ST, SOOT_SL, SOOT_W = "polished_blackstone_brick_stairs", "polished_blackstone_brick_slab", "polished_blackstone_brick_wall"
DSB = "deepslate_bricks"
BRASS, TEAL, LIT = "waxed_cut_copper", "waxed_weathered_cut_copper", "shroomlight"


def toward_centre(x, z):
    """The stair facing whose high back is on the centre side of (x, z)."""
    return "east" if x == 0 else "west" if x == 2 else "south" if z == 0 else "north"


def build():
    s = gs.Structure(W, H, W)
    for x in range(W):
        for z in range(W):
            s.set(x, 0, z, SOOT if (x, z) in CORNERS else DSB)
            s.set(x, 1, z, SOOT_B)
    # plinth cap
    for (x, z) in SIDES:
        s.set(x, 2, z, SOOT_ST, facing=toward_centre(x, z), half="bottom", shape="straight", waterlogged="false")
    for (x, z) in CORNERS:
        s.set(x, 2, z, SOOT_SL, type="bottom", waterlogged="false")
    s.set(1, 2, 1, BRASS)
    # the mast: corner posts, open sides, the coil stack in the middle
    for y in range(3, 8):
        for (x, z) in CORNERS:
            s.set(x, y, z, SOOT_W, up="true", north="none", south="none", east="none", west="none",
                  waterlogged="false")
    for y, n in zip(range(3, 8), (BRASS, LIT, TEAL, LIT, BRASS)):
        s.set(1, y, 1, n)
    # the crown band
    for (x, z) in CORNERS:
        s.set(x, 8, z, BRASS)
    for (x, z) in SIDES:
        s.set(x, 8, z, SOOT_ST, facing=toward_centre(x, z), half="top", shape="straight", waterlogged="false")
    s.set(1, 8, 1, LIT)
    # the emitter and the rail
    for (x, z) in CORNERS:
        s.set(x, 9, z, "lantern", hanging="false", waterlogged="false")
    for (x, z) in SIDES:
        s.set(x, 9, z, "lightning_rod", facing="up", powered="false", waterlogged="false")
    s.set(1, 9, 1, TEAL)
    s.set(1, 10, 1, "lightning_rod", facing="up", powered="false", waterlogged="false")
    gs.fill_air(s)
    return s


if __name__ == "__main__":
    outs = sys.argv[1:] or SHIPPED
    s = build()
    for d in outs:
        p = os.path.join(d, "rail_battery.nbt")
        s.write(p)
        print("wrote", p)
