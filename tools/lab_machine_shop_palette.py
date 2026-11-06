#!/usr/bin/env python3
"""The Machine Shop's palette pass (Nov 2026): three recolours of the shipped massing, compared on the eyes camera.

Both baseline critics (eyes 41263811904): read 7 / 7, shape 6 / 6 but colour 3 / 3, value 4 / 5 - "orange-roofed
workshop": ~39 % of the RTS camera's pixels are orange-looking copper (team orange) and the luma median 0.339 sits under
the grass 0.409. The massing stays; the copper weathers to verdigris and polished andesite (the Works capitol's light
group, tools/capitol_foundry_hall.py) gives the light top. The brass - copper blocks (the gear rim, the boiler), the
lightning rods, the furnace glass - stays as the faction's one accent.

Usage: python3 tools/lab_machine_shop_palette.py OUT_DIR   (writes <option>.nbt, <option>_l2.nbt, <option>_l3.nbt)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lab_machine_shop as M  # noqa: E402

OX, WE, EX, CU = ("waxed_oxidized_cut_copper", "waxed_weathered_cut_copper", "waxed_exposed_cut_copper",
                  "waxed_cut_copper")
PA = "polished_andesite"


def swap(pairs):
    """{old: new} over each block and its _stairs / _slab."""
    out = {}
    for a, b in pairs:
        out[a] = b
        out[a + "_stairs"] = b + "_stairs"
        out[a + "_slab"] = b + "_slab"
    return out


OPTIONS = {
    # V1 verdigris: the unweathered and exposed copper weathers to oxidized; the weathered walls stay
    "verdigris": swap([(CU, OX), (EX, OX)]),
    # V2 pale roofs: every roof stair / slab is polished andesite (the light top), the walls and trims verdigris
    "pale_roofs": dict(swap([(CU, OX), (EX, OX), (WE, OX)]), **{
        CU + "_stairs": PA + "_stairs", CU + "_slab": PA + "_slab", EX + "_stairs": PA + "_stairs",
        EX + "_slab": PA + "_slab", WE + "_stairs": PA + "_stairs"}),
    # V3 pale engine house: the engine house's hip and every ridge cap polished andesite (the dominant mass, the light
    # top), the shop's gable and the walls verdigris - two value groups on the roofs
    "pale_engine": dict(swap([(CU, OX), (EX, OX), (WE, OX)]), **{
        CU + "_stairs": PA + "_stairs", CU + "_slab": PA + "_slab"}),
}


def make(name):
    saved = M.PALETTE, M.BOILER, M.BOILER_STRAP, M.DIAL_CORNER
    M.PALETTE = OPTIONS[name]
    M.BOILER, M.BOILER_STRAP = OX, (M.SB, M.SBS)
    M.DIAL_CORNER = "iron_block"    # the boiler drum verdigris on soot straps (the dials keep the brass)
    try:
        s1, s2, s3 = M.l1(), M.l2(), M.l3()
        M.check_levels(s1, s2, s3)
        return s1, s2, s3
    finally:
        M.PALETTE, M.BOILER, M.BOILER_STRAP, M.DIAL_CORNER = saved


if __name__ == "__main__":
    out = sys.argv[1]
    for name in OPTIONS:
        s1, s2, s3 = make(name)
        for s, suf in ((s1, ""), (s2, "_l2"), (s3, "_l3")):
            s.write(os.path.join(out, name + suf + ".nbt"))
        print("wrote", name)
