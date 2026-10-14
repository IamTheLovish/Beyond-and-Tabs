#!/usr/bin/env python3
"""Compose one concept sheet per rendered model (plain python + Pillow, runs after Blender in concepts.yml).

  python3 tools/concept_sheet.py --model design/models/x.json --out out/x

Reads front.png, side.png, quarter.png and silhouette.png from --out (as tools/render_concept.py wrote them),
flattens the transparent silhouette onto white (and saves it back, so the branch holds a white-background PNG),
then writes sheet.png: the three stills and the silhouette in a row, with the unit name and role as the caption.
A model with "upgrade" tags (design/models/README.md) gets a fifth panel, "upgraded", from upgraded.png.
"""
import argparse, json, os
from PIL import Image, ImageDraw, ImageFont

TILE, PAD, CAPTION = 512, 24, 96
FONT_CANDIDATES = ("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "DejaVuSans.ttf")


def font(size):
    for path in FONT_CANDIDATES:
        try:
            return ImageFont.truetype(path, size)
        except OSError:
            pass
    try:
        return ImageFont.load_default(size=size)  # Pillow >= 10.1 scales its built-in font
    except TypeError:
        return ImageFont.load_default()


def tile(path, white_bg=False):
    """Load a render as an RGB square of TILE px; a missing file becomes a grey placeholder (the sheet still builds)."""
    if not os.path.exists(path):
        im = Image.new("RGB", (TILE, TILE), (160, 160, 160))
        ImageDraw.Draw(im).text((20, 20), "missing " + os.path.basename(path), fill=(40, 40, 40), font=font(24))
        return im
    im = Image.open(path).convert("RGBA")
    if white_bg:
        flat = Image.new("RGBA", im.size, (255, 255, 255, 255))
        flat.alpha_composite(im)
        flat.convert("RGB").save(path)  # keep the white-background version on disk too
        im = flat
    return im.convert("RGB").resize((TILE, TILE), Image.LANCZOS)


def has_upgrades(m):
    """True if any bone or cube carries an "upgrade" tag (the renderer then wrote upgraded.png)."""
    for b in m.get("bones", []):
        if b.get("upgrade") or any(c.get("upgrade") for c in b.get("cubes", [])):
            return True
    return False


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--model", required=True)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    with open(a.model) as f:
        m = json.load(f)
    views = [("front", False), ("side", False), ("quarter", False), ("silhouette", True)]
    if has_upgrades(m):
        views.append(("upgraded", False))
    tiles = [tile(os.path.join(a.out, name + ".png"), white) for name, white in views]

    w = PAD + len(tiles) * (TILE + PAD)
    h = CAPTION + TILE + PAD + 36
    sheet = Image.new("RGB", (w, h), (246, 244, 240))
    d = ImageDraw.Draw(sheet)
    d.text((PAD, 22), m["name"], fill=(30, 28, 26), font=font(44))
    d.text((PAD, 70), f"{m['role']}  |  {m['faction']}", fill=(110, 104, 96), font=font(22))
    small = font(18)
    for i, (im, (name, _)) in enumerate(zip(tiles, views)):
        x = PAD + i * (TILE + PAD)
        sheet.paste(im, (x, CAPTION))
        d.rectangle((x, CAPTION, x + TILE - 1, CAPTION + TILE - 1), outline=(200, 196, 190))
        d.text((x + 2, CAPTION + TILE + 8), name, fill=(110, 104, 96), font=small)
    path = os.path.join(a.out, "sheet.png")
    sheet.save(path, optimize=True)
    print("wrote", path, sheet.size)


if __name__ == "__main__":
    main()
