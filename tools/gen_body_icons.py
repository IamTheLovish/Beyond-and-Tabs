#!/usr/bin/env python3
"""HUD portraits (textures/mobheads/<icon>.png) of the generated unit bodies, drawn from the same models the game
renders - so a unit's button, selection portrait and control-group icon show the body it now has.

Why (the Oct 15 audit, claude/audit-oct15.md): the five White Angel bodies (tools/export_bodies.py) replaced the
vanilla mobs in the game, but their icons still showed the old faces - three raw vanilla heads (militia, iron golem,
witch) and two framed portraits of the old illagers (pillager, vindicator). Each portrait here is the body's head
and shoulders from the front (tools/game_preview.py's renderer; the head found by rendering with and without the
head bone), in the faction's house frame: a blue gradient and a gold border, 32 x 32 art shown at 64 x 64 like the
other framed icons.

    python3 tools/gen_body_icons.py            # writes the five icons
    python3 tools/gen_body_icons.py --check    # exit 1 if a committed icon differs from what this draws
    python3 tools/gen_body_icons.py --sheet OUT.png   # the five side by side at 2x, for a look first
"""
import argparse
import os
import sys

import numpy as np
from PIL import Image, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import dsl_to_game as d2g      # noqa: E402
import export_bodies           # noqa: E402
import game_preview as gp      # noqa: E402
import render_concept          # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
HEADS = os.path.join(ROOT, "bar", "src", "main", "resources", "assets", "reignofnether", "textures", "mobheads")
# body texture -> the icon the game looks up (MiscUtil.getEntityIconName: the entity type id without "_unit")
ICON = {"radiant_paladin_unit": "iron_golem", "field_medic_unit": "witch", "halberdier_unit": "vindicator",
        "crossbow_levy_unit": "pillager", "squire_runner_unit": "militia"}
TEAM = (58 / 255, 96 / 255, 190 / 255)              # the HUD shows the owner's blue
BG_TOP, BG_BOTTOM, FRAME = (88, 118, 170), (30, 40, 70), (226, 186, 80)   # the framed icons' house colours
CROP = 1.4                                            # the square crop's side, in head sizes (the house icons: head ~70%)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def bust(model_path, team):
    """The body's head and shoulders from the front, cropped square round the head (RGBA, transparent background)."""
    m = render_concept.load_model(model_path)
    m["_path"] = os.path.abspath(model_path)
    _java, imgs, info = d2g.build(m, "Preview", team.split(","), "preview", d2g.DENSITY)
    pose = {n: d2g.mc_rot(r) for n, r in m["pose"].items()}
    view = gp.VIEWS["front"]
    im, box, mask, _ = gp.render(gp.faces_of(info["root"], pose, True, False), imgs, d2g.DENSITY, view, 512, TEAM,
                                 None, with_mask=True)
    _, _, without, _ = gp.render(gp.faces_of(info["root"], pose, True, False, hide={"head"}), imgs, d2g.DENSITY,
                                 view, 512, TEAM, box, with_mask=True)
    head = mask & ~without                                # the head and whatever hangs from it (helm, halo, hood)
    ys, xs = np.where(head)
    side = int(max(ys.max() - ys.min(), xs.max() - xs.min()) * CROP)
    cx, top = (xs.min() + xs.max()) // 2, ys.min() - int(side * 0.08)
    a = np.asarray(im.convert("RGBA")).copy()
    a[..., 3] = mask * 255
    return Image.fromarray(a).crop((cx - side // 2, top, cx - side // 2 + side, top + side))


def portrait(crop):
    """32 x 32: the gradient, the bust (28 px, lightly sharpened so the helm edges hold), a gold frame with a dark
    inner line; shown at 64 x 64 (nearest), like the other framed icons."""
    c = Image.new("RGBA", (32, 32))
    px = c.load()
    for y in range(32):
        col = mix(BG_TOP, BG_BOTTOM, y / 31) + (255,)
        for x in range(32):
            px[x, y] = col
    small = crop.resize((28, 28), Image.LANCZOS).filter(ImageFilter.UnsharpMask(radius=1, percent=60, threshold=2))
    c.alpha_composite(small, (2, 3))
    dark = tuple(int(v * 0.55) for v in FRAME) + (255,)
    for i in range(32):
        for x, y in ((i, 0), (i, 31), (0, i), (31, i)):
            px[x, y] = FRAME + (255,)
    for i in range(1, 31):
        for x, y in ((i, 1), (i, 30), (1, i), (30, i)):
            px[x, y] = dark
    return c.resize((64, 64), Image.NEAREST)


def icons():
    return {ICON[tex]: portrait(bust(model, team)) for model, _cls, tex, team in export_bodies.BODIES}


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--sheet")
    a = ap.parse_args()
    out = icons()
    if a.sheet:
        sheet = Image.new("RGBA", (len(out) * 136 + 4, 136), (60, 60, 60, 255))
        for i, (name, im) in enumerate(out.items()):
            sheet.alpha_composite(im.resize((128, 128), Image.NEAREST), (i * 136 + 4, 4))
        sheet.convert("RGB").save(a.sheet)
        print("wrote", a.sheet, list(out))
        return
    bad = []
    for name, im in out.items():
        path = os.path.join(HEADS, name + ".png")
        if a.check:
            old = np.asarray(Image.open(path).convert("RGBA")) if os.path.exists(path) else None
            if old is None or old.shape != (64, 64, 4) or not np.array_equal(old, np.asarray(im)):
                bad.append(name)
        else:
            im.save(path, optimize=True)
            print("wrote", path)
    if a.check:
        print("body icons: %d of %d match the bodies%s" % (len(out) - len(bad), len(out),
                                                           (" - differ: " + ", ".join(bad)) if bad else ""))
        sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
