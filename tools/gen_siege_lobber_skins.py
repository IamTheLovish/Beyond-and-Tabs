#!/usr/bin/env python3
"""Placeholder skins and portraits for the seven T1 siege lobbers (design/siege_t1.md) until Itachi 1 draws them.

Each one is our own Bombard Crew skin (tools' earlier pass; the Brassworks' from the Mortar Crawler) recoloured into
the faction's palette: every pixel's brightness is mapped onto a three-stop ramp (shadow, cloth, highlight), alpha
kept, so the illager-body UV layout (VillagerUnitModel, 64x64) is unchanged. The *_team.png owner-colour mask is
copied as is. Deterministic; rerun after changing a palette.

usage: python3 tools/gen_siege_lobber_skins.py
  writes bar/.../textures/entities/<id>_unit.png, <id>_unit_team.png and bar/.../textures/mobheads/<id>.png
"""
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "bar", "src", "main", "resources", "assets", "reignofnether", "textures")

# id: (source unit, shadow, cloth, highlight)
LOBBERS = {
    "sunfire_mangonel": ("bombard_crew", (70, 52, 20), (226, 220, 200), (240, 196, 70)),    # white-and-gold tabards
    "skull_hurler": ("bombard_crew", (34, 44, 36), (126, 140, 118), (196, 214, 170)),      # bone-grey and green
    "boulder_chucker": ("bombard_crew", (52, 26, 16), (128, 70, 40), (210, 84, 52)),       # hide and red war paint
    "burl_slinger": ("bombard_crew", (40, 30, 18), (104, 76, 46), (120, 170, 70)),         # bark and moss
    "powder_monkey": ("bombard_crew", (30, 34, 40), (150, 140, 120), (220, 210, 186)),     # striped deck shirt
    "thunderstone_monk": ("bombard_crew", (24, 22, 56), (64, 60, 140), (90, 196, 150)),    # indigo robes, jade
    "steam_lobber": ("mortar_crawler", (40, 34, 26), (176, 138, 58), (230, 200, 120)),     # brass and soot
}


def ramp(v, lo, mid, hi):
    if v < 0.5:
        t, a, b = v / 0.5, lo, mid
    else:
        t, a, b = (v - 0.5) / 0.5, mid, hi
    return tuple(round(a[k] + (b[k] - a[k]) * t) for k in range(3))


def recolour(src, dst, lo, mid, hi):
    im = Image.open(src).convert("RGBA")
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            v = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            px[x, y] = ramp(v, lo, mid, hi) + (a,)
    im.save(dst)


def main():
    for lid, (src, lo, mid, hi) in LOBBERS.items():
        ent = os.path.join(ASSETS, "entities")
        recolour(os.path.join(ent, src + "_unit.png"), os.path.join(ent, lid + "_unit.png"), lo, mid, hi)
        team = os.path.join(ent, src + "_unit_team.png")
        if os.path.exists(team):
            Image.open(team).save(os.path.join(ent, lid + "_unit_team.png"))
        else:   # no owner-colour cloth on the source: an empty mask
            Image.new("RGBA", (64, 64), (0, 0, 0, 0)).save(os.path.join(ent, lid + "_unit_team.png"))
        heads = os.path.join(ASSETS, "mobheads")
        recolour(os.path.join(heads, src + ".png"), os.path.join(heads, lid + ".png"), lo, mid, hi)
        print("wrote", lid)


if __name__ == "__main__":
    main()
