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

BASE = dict(WALL=M.WALL, CROWN_WALL=M.CROWN_WALL, ROOF=M.ROOF, ROOFS=M.ROOFS, TOWER_TOP=M.TOWER_TOP)
PALE = dict(ROOF="polished_diorite", ROOFS="polished_diorite_stairs", CROWN_WALL="polished_diorite")
OPTIONS = {
    "paleroof": dict(PALE),
    "cross": dict(PALE, TOWER_TOP=8),
    "whitetomb": dict(PALE, WALL="polished_diorite"),
}


def build(name):
    for k, v in BASE.items():
        setattr(M, k, v)
    for k, v in OPTIONS[name].items():
        setattr(M, k, v)
    try:
        return M.build()
    finally:
        for k, v in BASE.items():
            setattr(M, k, v)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    os.makedirs(out, exist_ok=True)
    for n in OPTIONS:
        s = build(n)
        s.write(os.path.join(out, "mausoleum_%s.nbt" % n))
        print(n, "written")
