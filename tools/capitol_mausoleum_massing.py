#!/usr/bin/env python3
"""The Mausoleum's value / massing options (Buildings lane, Nov 2026), written for the blind eyes round.

Two fresh critics on the shipped Necropolis Cathedral (eyes 41263811904): 5/10, "dark stepped tomb", "dark grey castle
heap" - value 3-4: the building's median luma 0.246 sits in the darks band (grass 0.41; BAT - Art method: lights 0.50+
belong to buildings), and it shares the square stepped-pyramid outline with three other capitols. The options swap the
generator's value roles (capitol_mausoleum.WALL / CROWN_WALL / ROOF / ROOFS / TOWER_TOP), nothing else:
  paleroof - light top, dark bottom: pale polished diorite roofs, crossing tower and spire cones over the
           deepslate walls, towers and podium (the camera looks down: the roofs are the building's face);
  cross    - paleroof, and the four corner towers cut down to podium chapels (band y8, needles to y12) so the pale
           Greek cross of the four roofs is the outline from the RTS camera, dark corners round it;
  whitetomb - paleroof with polished diorite walls too: a white-marble tomb, blackstone quoins and trim the darks.

Usage: python3 tools/capitol_mausoleum_massing.py OUTDIR
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import capitol_mausoleum as M  # noqa: E402

# BASE: the cathedral that shipped until Nov 2026 (every option is drawn over it, whatever the generator's defaults)
BASE = dict(WALL=M.DB, CROWN_WALL=M.DT, ROOF=M.DT, ROOFS=M.DTS, TOWER_TOP=12, OPEN_CORNERS=False, ARM_ROOF=None,
            ARM_ROOFS=None, ARM_NEEDLES=False, DARK_CORNERS=False, QUIET_GROUND=False, CORNER_NEEDLES=False,
            CROSSING_LIGHTS=False, CROSS_PODIUM=False, NEEDLE_TOP=17, FRONT=dict(M.FRONT), AIR_CORNERS=False,
            PALE_CROWN=False, WALL_PIERS=False, DARK_ROSES=False)
CURRENT = {k: getattr(M, k) for k in BASE}
PALE = dict(ROOF="polished_diorite", ROOFS="polished_diorite_stairs", CROWN_WALL="polished_diorite")
OPTIONS = {
    "paleroof": dict(PALE),
    "cross": dict(PALE, TOWER_TOP=8),
    "whitetomb": dict(PALE, WALL="polished_diorite"),
    # round 1 (massing pick: paleroof, critics 6 / 5.5): open the cross's re-entrant corners; gather the lights
    "opencross": dict(PALE, OPEN_CORNERS=True),
    "opencross_lit": dict(PALE, OPEN_CORNERS=True, CROSSING_LIGHTS=True),
    "opencross_needles": dict(PALE, OPEN_CORNERS=True, CROSSING_LIGHTS=True, CORNER_NEEDLES=True),
    # round 2 (round-1 pick: opencross_lit, critics 6 / 6): needles at the arm ends, dark podium corners, no ground lights
    "r2a": dict(PALE, OPEN_CORNERS=True, CROSSING_LIGHTS=True, ARM_NEEDLES=True, DARK_CORNERS=True, QUIET_GROUND=True),
    #   r2b: and the arms in dark tile roofs over pale walls - the crossing the only pale top (the focal)
    "r2b": dict(PALE, OPEN_CORNERS=True, CROSSING_LIGHTS=True, ARM_NEEDLES=True, DARK_CORNERS=True, QUIET_GROUND=True,
                WALL="polished_diorite", ARM_ROOF=M.DT, ARM_ROOFS=M.DTS),
}
# round 3 (round-2 pick: r2b, critics 5.5 / 6.5): the outline is still a stepped square / X - the cross becomes the
# outline (podium under the arms only, the corners a mossy grave lawn), taller arm needles; a Latin cross; mid roofs
OPTIONS["r3a"] = dict(OPTIONS["r2b"], DARK_CORNERS=False, CROSS_PODIUM=True, NEEDLE_TOP=19)
OPTIONS["r3b"] = dict(OPTIONS["r3a"], FRONT={"south": 5, "west": 3, "north": 3, "east": 3})
OPTIONS["r3c"] = dict(OPTIONS["r3b"], ARM_ROOF="polished_andesite", ARM_ROOFS="polished_andesite_stairs")
# round 4 (round-3 pick: r3a, critics 6.5 / 6): the corner pads redraw the diamond (layer 0 sits a block over the field)
# -> empty corners, the field's lawn runs in; the crossing owns the brightest group; less pale wall; roses unlit (eyes)
OPTIONS["r4"] = dict(OPTIONS["r3a"], AIR_CORNERS=True, PALE_CROWN=True, WALL_PIERS=True, DARK_ROSES=True)


def build(name):
    for k, v in BASE.items():
        setattr(M, k, v)
    for k, v in OPTIONS[name].items():
        setattr(M, k, v)
    try:
        return M.build()
    finally:
        for k, v in CURRENT.items():
            setattr(M, k, v)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    os.makedirs(out, exist_ok=True)
    for n in OPTIONS:
        s = build(n)
        s.write(os.path.join(out, "mausoleum_%s.nbt" % n))
        print(n, "written")
