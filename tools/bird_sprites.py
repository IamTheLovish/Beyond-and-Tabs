#!/usr/bin/env python3
"""Bird sprites for the ambient flocks (ambient/AmbientBirdParticle; lovish Oct 28: "butterflies, fireflies, birds").

A bird seen from above, head at the texture top (the particle rolls the sprite to its heading on screen):
    bird_0  wings up (raised wings foreshortened: a short span, tips forward)
    bird_1  wings level (full span, swept back a little: the glide)
    bird_2  wings down (a little short of full span, tips curled back)
    bird_shadow  the level silhouette, soft and dark, for the flat quad on the ground
16 x 16, drawn 8x supersampled and box-filtered down so the wing edges anti-alias. Albedo stays above ~55 (no pure
black: ART-FAULTS kill test); the leading edge of each wing a step lighter so the shape reads at 6-8 px.

    python3 tools/bird_sprites.py      # writes bar/src/main/resources/assets/reignofnether/textures/particle/bird_*.png
"""
import os

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "bar", "src", "main", "resources", "assets", "reignofnether", "textures", "particle")
S = 8                       # supersampling
N = 16
# r2 (critic r31): in game the r1 colours (body luma ~55) drew near-black under the Enchanted grade - "flat black";
# now a dark brown-grey that lands at ~20% luma on screen, with a lighter leading edge
BODY = (98, 88, 80)
WING = (112, 101, 91)
EDGE = (146, 134, 121)


def P(pts):
    return [(x * S, y * S) for x, y in pts]


def wing(d, side, span, sweep, tip_fwd, chord=3.4):
    """One wing from the shoulder (x 8, y 5.4): `span` px out, the tip `sweep` px back (or forward with tip_fwd);
    broad at the root (chord), narrowing to a point - the shape that reads as a bird, not a cross."""
    sx = 8 + side * 0.6
    tipx = 8 + side * span
    tipy = 5.6 + sweep - tip_fwd
    lead = [(sx, 5.0), (8 + side * span * 0.5, 4.7 + sweep * 0.3 - tip_fwd * 0.6), (tipx, tipy)]
    trail = [(8 + side * span * 0.55, 5.0 + chord * 0.75 + sweep * 0.5), (sx, 5.0 + chord)]
    d.polygon(P(lead + trail), fill=WING + (255,))
    d.line(P(lead), fill=EDGE + (255,), width=int(0.9 * S))


def body(d):
    d.ellipse(P([(7.2, 3.0), (8.8, 4.8)]), fill=BODY + (255,))                    # head
    d.ellipse(P([(6.9, 4.0), (9.1, 9.4)]), fill=BODY + (255,))                    # body
    d.polygon(P([(7.4, 8.6), (8.6, 8.6), (9.7, 11.4), (8.0, 11.0), (6.3, 11.4)]), fill=BODY + (255,))  # tail fan


def frame(span, sweep, tip_fwd):
    im = Image.new("RGBA", (N * S, N * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for side in (-1, 1):
        wing(d, side, span, sweep, tip_fwd)
    body(d)
    return im


def down(im):
    return im.resize((N, N), Image.BOX)


def main():
    os.makedirs(OUT, exist_ok=True)
    frames = {
        "bird_0": frame(4.6, -0.2, 1.4),     # up: short span, tips forward
        "bird_1": frame(7.4, 1.4, 0.0),      # level: full span, a little swept
        "bird_2": frame(6.4, 2.6, -0.6),     # down: tips curl back
    }
    for name, im in frames.items():
        down(im).save(os.path.join(OUT, name + ".png"))
    # r2: the r1 shadow (peak alpha 0.45, wide blur) did not show in game - a firmer, less blurred shape
    sh = frames["bird_1"].filter(ImageFilter.GaussianBlur(0.6 * S))
    a = sh.getchannel("A").point(lambda v: int(v * 0.7))
    shadow = Image.new("RGBA", sh.size, (20, 22, 26, 0))
    shadow.putalpha(a)
    down(shadow).save(os.path.join(OUT, "bird_shadow.png"))
    print("wrote", ", ".join(sorted(frames)) + ", bird_shadow", "to", OUT)


if __name__ == "__main__":
    main()
