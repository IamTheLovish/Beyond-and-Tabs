#!/usr/bin/env python3
"""Tabs - Itadori's avatar (claude/avatar.md): an ORIGINAL builder-warrior from the world of Beyond and Tabs, in
the design/models DSL, one JSON per reaction pose under design/avatar/. He shares Itadori's heart, not anyone's
face: dark-teal messy hair with one sun-gold streak, amber eyes, a scar across the left eyebrow, a sleeveless
charcoal work tunic, parchment forearm wraps, a long sunset-orange scarf whose tail glows when he is fired up, a
utility belt with pouches, boots, and a short builder's hammer.

    python3 tools/gen_avatar.py            # writes design/avatar/tabs_<pose>.json
    python3 tools/preview_model.py design/avatar/tabs_cheer.json --out /tmp/tabs.png

Same space as gen_models.py: y up, he faces -Z, +x is HIS left. ~34 px tall (a young man, a head shorter than the
Paladins), a slightly large head for a readable face at sheet size. Every pose is the same body: the builder sets
bone rotations, the hands are solved onto their targets with gen_models.Model.solve_arm.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_models import Model  # noqa: E402

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "design", "avatar")

PALETTE = {
    "skin": "#d39b74", "skin_dark": "#b97f5a", "hair": "#1f5c61", "hair_dark": "#163f43", "streak": "#e8b84a",
    "eye_white": "#f3eee4", "eye": "#e3a32e", "pupil": "#2a1d12", "glint": "#ffffff", "lash": "#2a1d12",
    "brow": "#163f43", "mouth": "#6e2f26", "teeth": "#f2ece0", "tongue": "#c9584a",
    "scar": "#e8c0a2", "tunic": "#3a3d44", "tunic_dark": "#2b2d33", "wrap": "#e6dcc3", "wrap_dark": "#c4b795",
    "trousers": "#5a5045", "patch": "#6f6352",
    "scarf": "#e8692c", "scarf_dark": "#c4501d", "ember": "#ffc35a", "belt": "#6b4a2e", "pouch": "#8a6440",
    "buckle": "#c9a24a", "boot": "#4a3324", "sole": "#2e2017", "steel": "#8c8f96", "steel_dark": "#5e6168",
    "wood": "#8a5a33", "paper": "#4a7bd0", "ink": "#dfe9ff", "crate": "#9a6b3c", "crate_dark": "#6e4a28",
    "tube": "#5b3a22", "tube_dark": "#3e2716", "sweat": "#bfe6ff",
}
EMISSIVE = ["ember"]

HIP = 13.0          # top of the legs
LEG_W, LEG_D = 3.6, 3.6
THIGH, SHIN = 6.5, 6.5
WAIST_H, CHEST_H = 3.5, 8.0
CHEST_W, DEPTH = 9.0, 4.6        # across the shoulders
WAIST_W = 7.4                    # the tunic at the waist: a V, not Steve's slab
LOW_H = 3.6                      # the narrow lower torso, under the chest
NECK = HIP + WAIST_H + CHEST_H     # 24.5: the head bone's pivot
HEAD_W, HEAD_H, HEAD_D = 8.2, 8.4, 7.6
JAW_H = 2.8                      # the jaw is narrower than the cranium: a face that narrows, not a cube
ARM_W, ARM_D = 3.0, 3.2
UPPER, FORE, HAND = 5.6, 5.2, 2.6
SHOULDER_X = CHEST_W / 2 + ARM_W / 2 - 0.3


def body(m):
    b = m.bone("body", [0, HIP, 0])
    cy = HIP + WAIST_H
    b.cube([-3.6, HIP, -DEPTH / 2 + 0.3], [7.2, WAIST_H, DEPTH - 0.6], "trousers")                          # hips
    b.cube([-WAIST_W / 2, cy, -DEPTH / 2], [WAIST_W, LOW_H, DEPTH], "tunic")                                   # lower torso
    b.cube([-CHEST_W / 2, cy + LOW_H, -DEPTH / 2 - 0.1], [CHEST_W, CHEST_H - LOW_H, DEPTH + 0.2], "tunic")     # chest
    b.cube([-WAIST_W / 2 + 0.6, cy + 0.8, -DEPTH / 2 - 0.3], [WAIST_W - 1.2, CHEST_H - 2.4, 0.4], "tunic_dark")  # front panel
    b.cube([-0.25, cy + 1.2, -DEPTH / 2 - 0.45], [0.5, CHEST_H - 3.0, 0.3], "tunic")                         # seam
    # trapezius: the slope from the neck out to each shoulder, so the head sits on shoulders and not on a box
    for s in (1, -1):
        b.cube([s * 3.0 - 1.9, NECK - 1.0, -DEPTH / 2 + 0.4], [3.8, 1.5, DEPTH - 0.8], "tunic",
               rotation=[0, 0, -s * 20], pivot=[s * 1.6, NECK - 0.3, 0])
    b.cube([-1.7, NECK - 1.4, -1.5], [3.4, 2.2, 3.0], "skin")                                                # neck
    # belt with buckle, two pouches and a loop for the hammer
    b.cube([-3.9, cy - 1.2, -DEPTH / 2 - 0.15], [7.8, 1.5, DEPTH + 0.3], "belt")
    b.cube([-0.9, cy - 1.35, -DEPTH / 2 - 0.4], [1.8, 1.8, 0.4], "buckle")
    b.cube([1.6, cy - 2.6, -DEPTH / 2 - 0.9], [2.0, 2.2, 1.2], "pouch")
    b.cube([-3.6, cy - 2.4, -DEPTH / 2 - 0.7], [1.6, 1.9, 1.0], "pouch")
    b.cube([1.7, cy - 0.7, -DEPTH / 2 - 1.0], [1.8, 0.5, 0.3], "buckle")                                     # pouch clasp
    # the blueprint case across his back: a leather tube whose brass cap stands above his left (+x) shoulder - the
    # silhouette's second mark, opposite the scarf tail - and its strap across the chest, shoulder to hip
    tube_c, tube_l = (1.6, 21.6, DEPTH / 2 + 1.2), 13.0
    b.cube([tube_c[0] - 1.0, tube_c[1] - tube_l / 2, tube_c[2] - 1.0], [2.0, tube_l, 2.0], "tube",
           rotation=[0, 0, -30], pivot=list(tube_c))
    for end, h in ((tube_l / 2 - 1.4, 1.6), (-tube_l / 2 - 0.2, 1.2)):                                     # leather caps
        b.cube([tube_c[0] - 1.2, tube_c[1] + end, tube_c[2] - 1.2], [2.4, h, 2.4], "tube_dark",
               rotation=[0, 0, -30], pivot=list(tube_c))
    b.cube([tube_c[0] - 1.3, tube_c[1] + tube_l / 2 - 1.3, tube_c[2] - 1.3], [2.6, 0.4, 2.6], "buckle",
           rotation=[0, 0, -30], pivot=list(tube_c))                                                         # a brass ring
    b.cube([tube_c[0] - 1.25, tube_c[1] + 1.0, tube_c[2] - 1.25], [2.5, 0.6, 2.5], "belt",
           rotation=[0, 0, -30], pivot=list(tube_c))                                                         # the band
    strap_c = (0.1, 20.4, -DEPTH / 2 - 0.62)
    b.cube([strap_c[0] - 0.55, strap_c[1] - 5.3, strap_c[2]], [1.1, 10.6, 0.35], "belt",
           rotation=[0, 0, -41], pivot=list(strap_c))
    b.cube([strap_c[0] - 0.6, strap_c[1] + 1.4, strap_c[2] - 0.15], [1.2, 1.2, 0.3], "buckle",
           rotation=[0, 0, -41], pivot=list(strap_c))                                                        # its ring
    return b


def spike(h, base, size, tip, color, rot):
    """A hair spike: a base block and a narrower tip stacked on it, turned together about the base's foot."""
    x, y, z = base
    w, ht, d = size
    h.cube([x - w / 2, y, z - d / 2], [w, ht, d], color, rotation=rot, pivot=[x, y, z])
    tw, th, td = tip
    h.cube([x - tw / 2, y + ht - 0.2, z - td / 2], [tw, th, td], color, rotation=rot, pivot=[x, y, z])


def eye_arc(h, ex, ay, z, up):
    """A closed eye drawn as an arc: up = joy (^), else content (a soft u)."""
    a = 28 if up else -18
    h.cube([ex - 1.05, ay - 0.2, z], [1.1, 0.4, 0.2], "lash", rotation=[0, 0, a], pivot=[ex, ay, z])
    h.cube([ex - 0.05, ay - 0.2, z], [1.1, 0.4, 0.2], "lash", rotation=[0, 0, -a], pivot=[ex, ay, z])


def head(m, mood):
    h = m.bone("head", [0, NECK, 0], parent="body")
    y0, fz = NECK + 0.4, -HEAD_D / 2
    jz = fz + 0.2                                                                                          # the jaw's front
    h.cube([-HEAD_W / 2, y0 + JAW_H, fz], [HEAD_W, HEAD_H - JAW_H, HEAD_D], "skin")                          # cranium
    h.cube([-HEAD_W / 2 + 0.6, y0, jz], [HEAD_W - 1.2, JAW_H + 0.01, HEAD_D - 0.6], "skin")                  # jaw
    h.cube([-1.6, y0 - 0.25, jz - 0.15], [3.2, 1.2, 2.4], "skin")                                           # chin
    h.cube([-0.5, y0 + 2.2, fz - 0.65], [1.0, 1.5, 0.7], "skin")                                            # nose
    h.cube([-0.5, y0 + 2.2, fz - 0.67], [1.0, 0.3, 0.3], "skin_dark")                                       # its underside
    h.cube([-3.6, y0 + 4.6, fz - 0.35], [7.2, 0.7, 0.4], "skin")                                            # brow ridge
    for s in (1, -1):
        h.cube([s * (HEAD_W / 2) - (0.25 if s > 0 else 0.15), y0 + 3.0, -0.8], [0.4, 2.0, 1.6], "skin_dark")  # ears
    # hair: a cap, a back mass, sideburns, a fringe swept toward his left, crown spikes that stand up and back -
    # the silhouette's top - and the sun-gold streak on his RIGHT (-x), so the scar over his left brow shows
    h.cube([-HEAD_W / 2 - 0.4, y0 + HEAD_H - 2.2, fz - 0.3], [HEAD_W + 0.8, 2.6, HEAD_D + 0.7], "hair")
    h.cube([-HEAD_W / 2 + 0.4, y0 + HEAD_H + 0.2, fz + 0.8], [HEAD_W - 0.8, 0.8, HEAD_D - 1.0], "hair")
    h.cube([-HEAD_W / 2 - 0.4, y0 + 2.0, -fz - 2.0], [HEAD_W + 0.8, HEAD_H - 2.5, 2.4], "hair")
    h.cube([-1.6, y0 + 0.9, -fz - 0.2], [3.2, 2.0, 1.0], "hair_dark", rotation=[22, 0, 0],
           pivot=[0, y0 + 2.9, -fz])                                                                          # nape tuft
    for s in (1, -1):
        x0 = HEAD_W / 2 - 0.1 if s > 0 else -HEAD_W / 2 - 0.5
        h.cube([x0, y0 + 3.6, fz + 0.4], [0.6, HEAD_H - 3.6, 2.6], "hair")                                   # sideburn
        h.cube([x0, y0 + 4.6, fz + 3.0], [0.6, HEAD_H - 4.6, HEAD_D - 3.2], "hair")
    # the fringe: locks of different widths, lengths and angles, each a base and a narrower tip (a strand, not a
    # tile), at staggered heights, with a short second layer in front so no row of equal squares reads
    top = y0 + HEAD_H - 0.2
    fringe = [(-3.3, 3.4, 1.6, -6, -16, 0.0, "streak"), (-2.2, 2.6, 2.0, 24, -22, 0.35, "hair"),
              (-0.9, 3.1, 1.5, 12, -12, -0.2, "hair_dark"), (0.3, 2.3, 2.2, 30, -26, 0.45, "hair"),
              (1.5, 2.9, 1.6, 18, -14, 0.0, "hair_dark"), (2.6, 2.2, 1.9, 36, -20, 0.25, "hair"),
              (3.6, 2.7, 1.3, 6, -10, -0.15, "hair_dark")]
    for x, L, w, rz, rx, dy, col in fringe:
        t = top + dy
        piv = [x, t, fz - 0.2]
        h.cube([x - w / 2, t - L * 0.6, fz - 0.75], [w, L * 0.6, 1.2], col, rotation=[rx, 0, rz], pivot=piv)
        h.cube([x - w * 0.28, t - L, fz - 0.7], [w * 0.55, L * 0.45, 1.0], col, rotation=[rx, 0, rz], pivot=piv)
    for x, L, rz in ((-1.5, 1.8, -22), (1.0, 1.6, 26)):                                                    # front layer
        piv = [x, top + 0.7, fz - 0.6]
        h.cube([x - 0.8, top + 0.7 - L, fz - 1.15], [1.6, L, 1.0], "hair", rotation=[-30, 0, rz], pivot=piv)
    h.cube([-3.9, top - 2.6, fz - 0.95], [1.0, 2.0, 0.8], "streak", rotation=[-10, 0, 8],
           pivot=[-3.4, top - 0.6, fz - 0.5])                                                                 # the streak's tip
    crown = [((-2.9, -1.4), 28, 22, "hair"), ((-0.7, -0.4), 8, 30, "hair_dark"), ((1.5, -1.0), -14, 24, "hair"),
             ((3.3, 0.4), -32, 16, "hair_dark"), ((-1.5, 2.2), 12, 58, "hair"), ((1.6, 2.4), -16, 52, "hair_dark")]
    for (x, z), rz, rx, col in crown:
        spike(h, (x, y0 + HEAD_H - 0.2, z), (2.2, 2.4, 2.4), (1.2, 1.7, 1.2), col, [rx, 0, rz])
    # the face (on -z): brows, three-layer eyes (white, amber iris, pupil with a glint) under a lash line, the scar
    # over his LEFT brow (+x), a mouth per mood
    ez = fz - 0.06
    for s in (1, -1):
        ex = s * 1.9
        ix = ex - s * 0.25 + {"thinking": 0.45, "oops": 0.0}.get(mood, 0.0)                                 # iris centre
        iy = y0 + 3.05 + (0.25 if mood == "thinking" else 0.0)
        if mood == "cheer" or (mood == "fist_bump" and s < 0):
            eye_arc(h, ex, y0 + 3.7, ez - 0.1, up=True)                                                        # joy (and a wink)
        elif mood == "tired":
            eye_arc(h, ex, y0 + 3.5, ez - 0.1, up=False)
        else:
            h.cube([ex - 1.0, y0 + 3.0, ez], [2.0, 1.45, 0.1], "eye_white")
            iw = 0.9 if mood == "oops" else 1.1
            h.cube([ix - iw / 2, iy, ez - 0.06], [iw, 1.3, 0.08], "eye")
            h.cube([ix - 0.3, iy + 0.15, ez - 0.1], [0.6, 0.8, 0.06], "pupil")
            h.cube([ix + 0.05, iy + 0.75, ez - 0.13], [0.35, 0.35, 0.05], "glint")
            h.cube([ex - 1.1, y0 + 4.3, ez - 0.15], [2.2, 0.35, 0.2], "lash")
            if mood == "determined":
                h.cube([ex - 1.05, y0 + 3.95, ez - 0.17], [2.1, 0.45, 0.1], "skin")                          # lids down
                h.cube([ex - 1.1, y0 + 3.8, ez - 0.2], [2.2, 0.25, 0.1], "lash")
            elif mood == "thumbs_up":
                h.cube([ex - 1.0, y0 + 2.95, ez - 0.17], [2.0, 0.3, 0.1], "skin")                             # a smile in the eyes
        brow_rz = {"determined": s * 14, "oops": -s * 12, "thinking": (s * 10 if s > 0 else 0)}.get(mood, 0)
        brow_dy = {"oops": 0.35, "thinking": (0.45 if s > 0 else 0.0), "cheer": 0.3}.get(mood, 0.0)
        h.cube([ex - 1.1, y0 + 4.95 + brow_dy, fz - 0.5], [2.2, 0.55, 0.25], "brow", rotation=[0, 0, brow_rz],
               pivot=[ex, y0 + 5.2 + brow_dy, fz - 0.4])
    h.cube([1.25, y0 + 4.2, fz - 0.42], [0.35, 1.3, 0.12], "scar", rotation=[0, 0, -26], pivot=[1.4, y0 + 4.9, fz - 0.4])
    h.cube([1.55, y0 + 5.2, fz - 0.1], [0.35, 1.0, 0.12], "scar", rotation=[0, 0, -26], pivot=[1.7, y0 + 5.7, fz - 0.1])
    mz = jz - 0.1
    if mood in ("cheer", "fist_bump"):
        h.cube([-1.7, y0 + 1.25, mz], [3.4, 0.85, 0.2], "mouth")                                              # open grin
        h.cube([-1.2, y0 + 0.6, mz], [2.4, 0.7, 0.2], "mouth")
        h.cube([-1.5, y0 + 1.7, mz - 0.05], [3.0, 0.38, 0.2], "teeth")
        h.cube([-0.8, y0 + 0.65, mz - 0.04], [1.6, 0.45, 0.2], "tongue")
    elif mood == "oops":
        h.cube([-1.6, y0 + 0.95, mz], [3.2, 0.95, 0.2], "mouth")                                              # a grimace
        h.cube([-1.4, y0 + 1.1, mz - 0.05], [2.8, 0.65, 0.2], "teeth")
        for k in (-0.7, 0.0, 0.7):
            h.cube([k - 0.05, y0 + 1.1, mz - 0.07], [0.1, 0.65, 0.2], "mouth")
        h.cube([-HEAD_W / 2 - 0.6, y0 + 5.2, -2.6], [0.6, 1.3, 0.8], "sweat")                                # sweat drop
        h.cube([-HEAD_W / 2 - 0.55, y0 + 6.3, -2.45], [0.5, 0.5, 0.5], "sweat")
    elif mood == "determined":
        h.cube([-1.1, y0 + 1.3, mz], [2.2, 0.45, 0.2], "mouth")
        h.cube([-1.3, y0 + 1.15, mz], [0.4, 0.35, 0.2], "mouth")
        h.cube([0.9, y0 + 1.15, mz], [0.4, 0.35, 0.2], "mouth")
    elif mood == "thinking":
        h.cube([0.1, y0 + 1.3, mz], [1.5, 0.45, 0.2], "mouth", rotation=[0, 0, 12], pivot=[0.8, y0 + 1.5, mz])
    else:   # thumbs_up, tired: a closed smile
        h.cube([-1.3, y0 + 1.25, mz], [2.6, 0.45, 0.2], "mouth")
        for s in (1, -1):
            h.cube([s * 1.35 - 0.25, y0 + 1.5, mz], [0.5, 0.5, 0.2], "mouth")
    return h


def scarf(m, flow):
    """Wrapped twice round the neck (under the chin, not over it), a long knot end hanging in front, and the tail off
    his right shoulder in three chained segments that curl a little more each - flow = degrees the tail streams back
    (negative: hangs slack). The tail also sweeps out to his right (-x) as the wind rises, so it reads from the FRONT
    too: the silhouette's signature, opposite the blueprint case. The tail's end glows (ember) - the 'fired up' tell."""
    s = m.bone("scarf", [0, NECK, DEPTH / 2], parent="body")
    s.cube([-(CHEST_W + 0.2) / 2, NECK - 1.8, -DEPTH / 2 - 0.6], [CHEST_W + 0.2, 1.6, DEPTH + 1.2], "scarf")
    s.cube([-(CHEST_W - 1.4) / 2, NECK - 0.8, -DEPTH / 2 - 0.4], [CHEST_W - 1.4, 1.5, DEPTH + 0.8], "scarf_dark")
    knot = m.bone("scarf_knot", [-0.6, NECK - 1.4, -DEPTH / 2 - 0.6], parent="scarf",
                  rotation=[-min(max(flow, 0), 40) * 0.25, 0, 8 + max(flow, 0) * 0.12])
    knot.cube([-1.8, NECK - 1.6, -DEPTH / 2 - 1.0], [2.4, 1.4, 1.0], "scarf_dark")                         # the knot
    knot.cube([-1.6, NECK - 5.4, -DEPTH / 2 - 0.85], [2.2, 4.0, 0.6], "scarf")                              # its long end
    knot.cube([-1.5, NECK - 6.4, -DEPTH / 2 - 0.8], [0.8, 1.1, 0.5], "scarf")                               # frayed
    knot.cube([-0.4, NECK - 6.2, -DEPTH / 2 - 0.8], [0.8, 0.9, 0.5], "scarf")
    x0, z0 = -2.4, DEPTH / 2 + 0.2
    y, parent = NECK - 0.8, "scarf"
    glow = "ember" if flow >= 25 else "scarf_dark"     # the tail glows only when he is fired up
    sweep = -min(14.0, 0.3 * max(flow - 15, 0))
    segs = ((6.0, 2.7, "scarf"), (5.6, 2.9, "scarf"), (4.6, 3.1, "ember_tip"))   # widening toward the end: cloth
    for i, (L, w, kind) in enumerate(segs):
        bend = flow * (0.9, 0.35, 0.3)[i] if flow >= 0 else flow * 0.5
        rz = sweep * (1.0, 0.4, 0.3)[i]
        seg = m.bone("scarf_tail_%d" % i, [x0, y, z0], parent=parent, rotation=[-bend, 0, rz])
        if kind == "ember_tip":
            seg.cube([x0 - w / 2, y - L + 2.0, z0 - 0.3], [w, L - 2.0, 0.6], "scarf")
            seg.cube([x0 - w / 2, y - L + 1.2, z0 - 0.32], [w, 0.9, 0.64], glow)                             # the glow band
            for k, (dx, ln) in enumerate(((-w / 2, 2.0), (0.15, 2.6))):                                      # a forked end
                seg.cube([x0 + dx, y - L + 1.2 - ln, z0 - 0.28], [w / 2 - 0.15, ln, 0.56],
                         glow if k else "scarf", rotation=[0, 0, (8 if k else -8)], pivot=[x0 + dx + 0.6, y - L + 1.2, z0])
        else:
            seg.cube([x0 - w / 2, y - L, z0 - 0.3], [w, L, 0.6], "scarf")
            seg.cube([x0 - w / 2, y - L + 1.2, z0 + 0.25], [w, 0.4, 0.15], "scarf_dark")                     # a fold line
        parent, y = seg.name, y - L
    return s


def arm(m, side):
    """Shoulder cap, upper arm with a biceps, a forearm that narrows from the elbow to the wrist under rolled
    wraps, and a fist: palm, curled fingers on the inner side with the splits drawn, the flats of the knuckles
    underneath, a thumb down the front. m.thumbs[side] is the thumb's cube (a pose may replace it)."""
    pre = "left" if side > 0 else "right"
    sx, sy = side * SHOULDER_X, NECK - 0.8
    x0 = sx - ARM_W / 2
    elbow_y = sy - UPPER
    wrist_y = elbow_y - FORE
    up = m.bone(pre + "_arm", [sx, sy, 0], parent="body")
    up.cube([x0, elbow_y, -ARM_D / 2], [ARM_W, UPPER, ARM_D], "skin")                                        # bare upper arm
    up.cube([x0 - 0.35, sy - 2.4, -ARM_D / 2 - 0.3], [ARM_W + 0.7, 2.8, ARM_D + 0.6], "skin")                 # deltoid
    up.cube([x0 + 0.5, elbow_y + 0.9, -ARM_D / 2 - 0.3], [ARM_W - 1.0, 2.4, 0.4], "skin")                     # biceps
    fo = m.bone(pre + "_fore", [sx, elbow_y, 0], parent=pre + "_arm")
    fo.cube([x0 - 0.05, elbow_y - 2.0, -ARM_D / 2 - 0.05], [ARM_W + 0.1, 2.0, ARM_D + 0.1], "skin")          # by the elbow
    fo.cube([x0 + 0.25, wrist_y, -ARM_D / 2 + 0.2], [ARM_W - 0.5, FORE - 2.0, ARM_D - 0.4], "skin")           # to the wrist
    fo.cube([x0 + 0.05, wrist_y - 0.1, -ARM_D / 2 - 0.05], [ARM_W - 0.1, FORE - 1.7, ARM_D + 0.1], "wrap")    # wraps
    for k in range(2):
        fo.cube([x0 + 0.0, wrist_y + 0.7 + k * 1.3, -ARM_D / 2 - 0.1], [ARM_W, 0.3, ARM_D + 0.2], "wrap_dark")
    ox = x0 + ARM_W - 0.2 if side > 0 else x0 - 0.3                                                         # outer side
    fo.cube([ox, wrist_y + 0.4, -0.4], [0.5, 1.5, 0.8], "wrap", rotation=[0, 0, side * 14],
            pivot=[ox + 0.25, wrist_y + 1.9, 0])                                                              # loose end
    hy = wrist_y - HAND
    inner = x0 if side > 0 else x0 + ARM_W                                                                    # toward his centre
    fo.cube([x0 + 0.15, hy, -ARM_D / 2 + 0.15], [ARM_W - 0.3, HAND, ARM_D - 0.3], "skin")                    # palm
    fx = inner - 0.25 if side > 0 else inner - 0.35
    fo.cube([fx, hy - 0.1, -ARM_D / 2 + 0.1], [0.6, HAND - 0.4, ARM_D - 0.2], "skin")                        # curled fingers
    for k in range(1, 4):
        z = -ARM_D / 2 + 0.1 + k * (ARM_D - 0.2) / 4
        fo.cube([fx + (-0.06 if side > 0 else 0.56), hy - 0.1, z - 0.05], [0.1, HAND - 0.4, 0.1], "skin_dark")
        fo.cube([x0 + 0.1, hy - 0.16, z - 0.05], [ARM_W - 0.2, 0.1, 0.1], "skin_dark")                       # knuckle flats
    tx = inner - 0.1 if side > 0 else inner - 1.0
    thumb = fo.cube([tx, hy + 0.4, -ARM_D / 2 - 0.3], [1.1, HAND - 0.2, 0.7], "skin")                       # thumb
    if not hasattr(m, "thumbs"):
        m.thumbs = {}
    m.thumbs[side] = (fo, thumb)
    return pre + "_arm", pre + "_fore", (sx, wrist_y - HAND / 2, 0)


def leg(m, side, thigh_rot, shin_rot):
    pre = "left" if side > 0 else "right"
    hx = side * 1.9
    x0 = hx - LEG_W / 2
    knee_y = HIP - THIGH
    ankle_y = knee_y - SHIN
    th = m.bone(pre + "_thigh", [hx, HIP, 0], rotation=thigh_rot)
    th.cube([x0, knee_y, -LEG_D / 2], [LEG_W, THIGH, LEG_D], "trousers")
    sh = m.bone(pre + "_shin", [hx, knee_y, 0], parent=pre + "_thigh", rotation=shin_rot)
    sh.cube([x0, ankle_y + 3.2, -LEG_D / 2], [LEG_W, SHIN - 3.2, LEG_D], "trousers")
    sh.cube([x0 - 0.2, ankle_y, -LEG_D / 2 - 0.2], [LEG_W + 0.4, 3.6, LEG_D + 0.4], "boot")                    # boot shaft
    sh.cube([x0 - 0.3, ankle_y + 3.2, -LEG_D / 2 - 0.3], [LEG_W + 0.6, 0.7, LEG_D + 0.6], "boot")             # cuff
    sh.cube([x0 - 0.2, ankle_y - 0.1, -LEG_D / 2 - 1.6], [LEG_W + 0.4, 1.6, LEG_D + 1.8], "boot")             # toe
    sh.cube([x0 - 0.25, ankle_y - 0.6, -LEG_D / 2 - 1.7], [LEG_W + 0.5, 0.6, LEG_D + 1.9], "sole")
    sh.cube([x0 - 0.1, ankle_y + 1.4, -LEG_D / 2 - 0.35], [LEG_W + 0.2, 0.4, 0.2], "belt")                   # boot strap
    if side > 0:   # a patched knee: he kneels to build
        sh.cube([x0 + 0.5, knee_y - 2.0, -LEG_D / 2 - 0.15], [LEG_W - 1.0, 2.2, 0.25], "patch")
        th.cube([x0 + 0.5, knee_y + 0.1, -LEG_D / 2 - 0.15], [LEG_W - 1.0, 0.9, 0.25], "patch")
    return pre + "_shin"


def hammer(m, parent, grip):
    """A short builder's hammer, its grip centred on `grip` (rest space of `parent`), head up."""
    gx, gy, gz = grip
    hm = m.bone("hammer", [gx, gy, gz], parent=parent)
    hm.cube([gx - 0.45, gy - 2.5, gz - 0.45], [0.9, 8.5, 0.9], "wood")
    hm.cube([gx - 0.55, gy - 1.2, gz - 0.55], [1.1, 2.2, 1.1], "belt")                                        # leather grip
    hm.cube([gx - 2.6, gy + 5.6, gz - 1.0], [5.2, 2.2, 2.0], "steel")
    hm.cube([gx + 2.4, gy + 5.8, gz - 0.8], [0.7, 1.8, 1.6], "steel_dark")                                    # striking face
    hm.cube([gx - 3.6, gy + 6.0, gz - 0.6], [1.2, 1.2, 1.2], "steel_dark", rotation=[0, 0, -20],
            pivot=[gx - 2.6, gy + 6.6, gz])                                                                       # claw
    return hm


def blueprint_roll(m, parent, at, open_=False):
    x, y, z = at
    bp = m.bone("blueprint", [x, y, z], parent=parent)
    if open_:
        bp.cube([x - 3.2, y - 0.2, z - 4.2], [6.4, 0.3, 4.6], "paper", rotation=[-55, 0, 0], pivot=[x, y, z])
        for k in range(3):
            bp.cube([x - 2.6 + k * 1.8, y + 0.15, z - 3.6], [1.2, 0.1, 3.4], "ink", rotation=[-55, 0, 0], pivot=[x, y, z])
        for s in (1, -1):
            bp.cube([x + s * 3.2 - 0.6, y - 0.6, z - 4.4], [1.2, 1.2, 5.0], "paper", rotation=[-55, 0, 0], pivot=[x, y, z])
    else:
        bp.cube([x - 0.7, y - 4.0, z - 0.7], [1.4, 8.0, 1.4], "paper")
        bp.cube([x - 0.75, y - 0.6, z - 0.75], [1.5, 0.5, 1.5], "belt")
    return bp


def base(name, mood, flow=10, thigh=((0, 0, 0), (0, 0, 0)), shin=((0, 0, 0), (0, 0, 0))):
    m = Model("Tabs - " + name, "avatar", "Itadori's avatar (claude/avatar.md)", PALETTE, EMISSIVE)
    body(m)
    head(m, mood)
    scarf(m, flow)
    la, lf, lh = arm(m, 1)
    ra, rf, rh = arm(m, -1)
    fl = leg(m, 1, thigh[0], shin[0])
    fr = leg(m, -1, thigh[1], shin[1])
    return m, (la, lf, lh), (ra, rf, rh), (fl, fr)


def belt_hammer(m):
    hm = hammer(m, "body", (-3.2, HIP + 0.6, -DEPTH / 2 - 1.0))
    hm.rotation = [0, 0, 160]   # hanging head-down from the belt loop


def rest_axis_toward(m, bone, rest_pt, want):
    """The rest-space axis of `bone` that points most nearly along the world direction `want` once posed."""
    a = m.fk(bone, rest_pt)
    best = None
    for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
        b = m.fk(bone, tuple(rest_pt[i] + d[i] for i in range(3)))
        dot = sum((b[i] - a[i]) * want[i] for i in range(3))
        if best is None or dot > best[0]:
            best = (dot, d)
    return best[1]


def thumb_up(m, side):
    """Replaces the curled thumb with one standing up in the WORLD: it grows out of where the curled one lay, along
    the fist's axis that looks most nearly up once posed (never back into the wrist)."""
    fo, old = m.thumbs[side]
    fo.cubes.remove(old)
    c = [old["from"][i] + old["size"][i] / 2 for i in range(3)]
    a = m.fk(fo.name, c)
    best = None
    for d in ((1, 0, 0), (-1, 0, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
        b = m.fk(fo.name, tuple(c[i] + d[i] for i in range(3)))
        dot = b[1] - a[1]
        if best is None or dot > best[0]:
            best = (dot, d)
    d = best[1]
    size = [1.1 if d[i] == 0 else 2.6 for i in range(3)]
    centre = [c[i] + d[i] * 1.1 for i in range(3)]
    fo.cube([centre[i] - size[i] / 2 for i in range(3)], size, "skin")
    tip = [c[i] + d[i] * 2.1 for i in range(3)]
    fo.cube([tip[i] - (0.5 if d[i] == 0 else 0.3) for i in range(3)],
            [1.0 if d[i] == 0 else 0.6 for i in range(3)], "skin_dark")                                   # the nail end


def rest_arm(m, arm_, side, swing=6):
    a, f, _ = arm_
    m.bones[a].rotation = [swing, 0, -side * 6]
    m.bones[f].rotation = [-8, 0, 0]


# ------------------------------------------------------------------ the seven poses ------------------------------
def cheer():
    m, L, R, feet = base("Cheer", "cheer", flow=35, thigh=((-6, 0, 4), (8, 0, -4)), shin=((10, 0, 0), (-4, 0, 0)))
    belt_hammer(m)
    e = m.solve_arm(R[0], R[1], R[2], (-6.5, NECK + 11.5, -1.0))              # fist up high
    rest_arm(m, L, 1, swing=-10)
    m.bones["body"].rotation = [0, 0, 3]
    m.ground(feet)
    m.pose = {"head": [-12, 0, 0]}
    return m, e


def thumbs_up():
    m, L, R, feet = base("Thumbs up", "thumbs_up", flow=12)
    belt_hammer(m)
    m.bones[R[0]].rotation = [48, 0, 12]                                       # elbow out front, forearm level:
    m.bones[R[1]].rotation = [42, 0, 0]                                        # the fist held out at the viewer
    e = 0.0
    thumb_up(m, -1)
    rest_arm(m, L, 1)
    m.ground(feet)
    m.pose = {"head": [-4, 10, 0]}
    return m, e


def determined():
    m, L, R, feet = base("Determined", "determined", flow=55, thigh=((4, 0, 6), (-4, 0, -6)))
    grip = (-SHOULDER_X - 0.4, NECK - 0.4, -3.2)
    e = m.solve_arm(R[0], R[1], R[2], grip, elbow_out=2)                                     # fist in front of the shoulder
    hm = hammer(m, "body", m.fk(R[1], R[2]))                                                # turned about where the fist IS
    hm.rotation = [52, 0, 14]                                                               # head up, laid back over the shoulder
    rest_arm(m, L, 1, swing=2)
    m.bones[L[0]].rotation = [0, 0, -14]
    m.ground(feet)
    m.pose = {"head": [-6, -8, 0]}
    return m, e


def oops():
    m, L, R, feet = base("Oops", "oops", flow=8, thigh=((2, 0, 0), (-2, 0, 0)))
    belt_hammer(m)
    e = m.solve_arm(R[0], R[1], R[2], (-2.5, NECK + 6.5, 3.5), elbow_out=2)     # hand behind his head
    rest_arm(m, L, 1, swing=0)
    m.bones["body"].rotation = [4, 0, -4]
    m.ground(feet)
    m.pose = {"head": [6, 0, -8]}
    return m, e


def fist_bump():
    m, L, R, feet = base("Fist bump", "fist_bump", flow=25, thigh=((-14, 0, 0), (14, 0, 0)), shin=((18, 0, 0), (0, 0, 0)))
    belt_hammer(m)
    e = m.solve_arm(R[0], R[1], R[2], (-2.0, NECK - 3.0, -12.0))               # straight at the viewer
    rest_arm(m, L, 1, swing=-14)
    m.bones["body"].rotation = [8, -12, 0]
    m.ground(feet)
    m.pose = {"head": [-4, 8, 0]}
    return m, e


def tired():
    """Sitting on a crate, scarf loose, hands on his knees."""
    m, L, R, feet = base("Tired but happy", "tired", flow=-4, thigh=((82, 0, -6), (82, 0, 6)), shin=((-84, 0, 0), (-80, 0, 0)))
    belt_hammer(m)
    crate = m.bone("crate", [0, 0, 0])
    crate.cube([-5.5, 0, -2.5], [11, 7.0, 7], "crate")
    for x in (-5.6, 4.6):
        crate.cube([x, 0, -2.6], [1.0, 7.0, 7.2], "crate_dark")
    crate.cube([-5.6, 3.0, -2.6], [11.2, 1.0, 7.2], "crate_dark")
    # the seated body: his seat on the crate top (thighs horizontal at y = HIP drop)
    drop = HIP - 7.6
    for b in ("body", "left_thigh", "right_thigh"):
        bone = m.bones[b]
        bone.pivot[1] -= drop
        for c in bone.cubes:
            c["from"][1] -= drop
            if "pivot" in c:
                c["pivot"][1] -= drop
    for name, bone in m.bones.items():
        if name in ("body", "left_thigh", "right_thigh", "crate"):
            continue
        bone.pivot[1] -= drop
        for c in bone.cubes:
            c["from"][1] -= drop
            if "pivot" in c:
                c["pivot"][1] -= drop
    knee_l = m.fk("left_shin", [1.9, HIP - THIGH - drop, 0])
    knee_r = m.fk("right_shin", [-1.9, HIP - THIGH - drop, 0])
    hl = (L[2][0], L[2][1] - drop, L[2][2])
    hr = (R[2][0], R[2][1] - drop, R[2][2])
    e1 = m.solve_arm(L[0], L[1], hl, (knee_l[0] + 0.5, knee_l[1] + 2.4, knee_l[2] + 1.0))
    e2 = m.solve_arm(R[0], R[1], hr, (knee_r[0] - 0.5, knee_r[1] + 2.4, knee_r[2] + 1.0))
    m.bones["body"].rotation = [10, 0, 0]
    m.pose = {"head": [8, 0, 6]}
    return m, max(e1, e2)


def thinking():
    m, L, R, feet = base("Thinking", "thinking", flow=6, thigh=((3, 0, 2), (-3, 0, -2)))
    belt_hammer(m)
    chin = (0.6, NECK + 1.4, -HEAD_D / 2 - 1.2)
    e1 = m.solve_arm(L[0], L[1], L[2], chin, elbow_out=1)                      # his left hand on his chin
    e2 = m.solve_arm(R[0], R[1], R[2], (-4.0, NECK - 6.0, -6.5), elbow_out=1)  # right hand holds the open plan
    blueprint_roll(m, R[1], (R[2][0] + 1.5, R[2][1] + 0.6, R[2][2] - 1.0), open_=True)
    m.ground(feet)
    m.pose = {"head": [10, 14, 6]}
    return m, max(e1, e2)


POSES = {
    "tabs_cheer": cheer, "tabs_thumbs_up": thumbs_up, "tabs_determined": determined, "tabs_oops": oops,
    "tabs_fist_bump": fist_bump, "tabs_tired": tired, "tabs_thinking": thinking,
}


def main():
    os.makedirs(OUT, exist_ok=True)
    only = sys.argv[1:]
    for name, build in POSES.items():
        if only and name not in only:
            continue
        m, err = build()
        path = os.path.normpath(os.path.join(OUT, name + ".json"))
        with open(path, "w") as f:
            json.dump(m.to_json(), f, indent=1)
        nc = sum(len(b.cubes) for b in m.bones.values())
        print(f"wrote {path}: {len(m.bones)} bones, {nc} cubes, hand error {err:.2f} px")


if __name__ == "__main__":
    main()
