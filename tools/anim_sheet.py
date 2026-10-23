#!/usr/bin/env python3
"""The animation review sheet: ONE png a Claude session can read back and judge a clip from (lovish Oct 22 20:23: "you
should find a way to test animations in the cloud somewhere you can simulate them and see them with your own eyes").

Why a sheet and not the old strip: tools/anim_lab.py --play wrote every half tick side by side - 9620 x 798 px, ~60 px
figures - and an image viewer shrinks anything wider than ~2000 px, so the figures came back unreadable. A sheet is at
most 2000 x 2000 px with ~10 key frames at 240+ px each, one VIEW per row (time runs left to right, so a column is the
same instant from three sides), a frame placed exactly on the blow, the progress / tick written under every frame, and
a motion-trail cell per view (onion skin of the key frames plus the path of the weapon tip and the feet).

This module is the maths and the drawing; it knows nothing about models. Two callers feed it:
  - tools/anim_lab.py --sheet      frames rendered offline from a clip's data (design/anims/<unit>.json);
  - tools/anim_eyes.py             frames captured from the REAL game client on a CI runner (EyesAnimScene), any unit.

    key_frames(n, loop, hit_p)   which progress values to show (a frame on the blow; a loop leaves out its seam)
    select_nearest(times, targets)  the captured frames closest to those times (captures are not evenly spaced)
    layout(lines)                the biggest figure size that fits 2000 x 2000, and where every cell goes
    pick_tracked / tracked_point / to_pixel   follow one real vertex (the halberd's tip, a heel) from pose to pose
    compose(...)                 draws the sheet
Tested by tools/test_anim_sheet.py (written first, red, then this).
"""
import math
import os

MAX_SIDE = 2000            # an image viewer downsizes past ~2000 px: the sheet is built to survive that untouched
TARGET_CELL = 240          # px per figure we want at least (the old strip had ~60)
MAX_CELL = 420             # a 3-frame sheet need not be blown up past this


# ------------------------------------------------------------------ which frames --------------------------------
def key_frames(n, loop=False, hit_p=None):
    """(progress values, index of the blow's frame or None) for a sheet of n frames.

    A one-shot (a thrust, a death) shows p = 0 .. 1 in n even steps; a loop (an idle, a walk) shows 0 .. 1 - 1/n, since
    its last key IS its first (showing both wastes a cell on the same pose). `hit_p` (an attack's moment of impact)
    gets a frame EXACTLY on it, taking the place of the nearest even step - unless that step is the first or last
    frame, which stay at 0 and 1 (then that end frame is marked as the blow's)."""
    if n < 2:
        raise ValueError("a sheet needs at least 2 frames, got %d" % n)
    if loop:
        return [i / n for i in range(n)], None
    ps = [i / (n - 1) for i in range(n)]
    if hit_p is None:
        return ps, None
    hit = min(range(n), key=lambda i: (abs(ps[i] - hit_p), i))
    if hit not in (0, n - 1):
        ps[hit] = hit_p
    return ps, hit


def frame_label(index, p, ticks, is_hit=False):
    """'3  p=0.33  t=6.0' - frame number, progress through the clip, game ticks into it (20 a second)."""
    return "%d  p=%.2f  t=%.1f%s" % (index + 1, p, p * ticks, "  HIT" if is_hit else "")


def time_label(index, seconds):
    """'5  +0.55s  (tick 11)' - for frames captured in the game, which have a clock but no clip progress."""
    return "%d  +%.2fs  (tick %d)" % (index + 1, seconds, round(seconds * 20))


def select_nearest(times, targets):
    """Indices into `times` (any order) of the captures nearest to each target time, never the same capture twice,
    in time order. Captures arrive when a software-rendered client gets round to them, so they are unevenly spaced;
    picking by index would make the sheet's time axis lie. With fewer captures than targets, all of them."""
    n, m = len(times), len(targets)
    if n == 0 or m == 0:
        return []
    order = sorted(range(n), key=lambda i: (times[i], i))
    if n <= m:
        return order
    out, prev = [], -1
    for k, t in enumerate(sorted(targets)):
        lo, hi = prev + 1, n - (m - k)               # leave room for the targets still to come
        best = min(range(lo, hi + 1), key=lambda j: (abs(times[order[j]] - t), j))
        out.append(order[best])
        prev = best
    return out


# ------------------------------------------------------------------ where they go -------------------------------
class Layout:
    """The sheet's geometry. `positions[line][k]` is the top-left (x, y) of the k-th cell of logical line `line`
    (a cell is `cell` px wide and `heights[line]` tall, with `label_h` px of caption under it); `ref_boxes` the
    (x, y, w, h) of each full-width reference band; `wrap` how many physical rows a logical line was split into;
    `cols` cells per row."""

    def __init__(self, **kw):
        self.__dict__.update(kw)


def layout(lines, max_side=MAX_SIDE, target_cell=TARGET_CELL, ref_lines=0, header=40, label_h=18, pad=4,
           max_cell=MAX_CELL, aspects=None):
    """Lay out `lines` (a list of cell counts, one logical line per view) on the biggest picture <= max_side square.

    A line stays on ONE row while the figures can be target_cell px wide or more (time reads left to right);
    otherwise the lines are wrapped into the fewest equal rows that reach it (10 frames -> 2 x 5), else into whatever
    gives the biggest figures. `aspects` = the height / width of the cells of each line (default 1: a side view of a
    unit with a long weapon is twice as wide as tall, and a square cell wastes half of itself - short rows leave the
    room to a wider cell). `ref_lines` full-width bands (a reference image) go below, as tall as a square cell."""
    if not lines or any(n < 1 for n in lines):
        raise ValueError("layout needs at least one non-empty line, got %r" % (lines,))
    if aspects is None:
        aspects = [1.0] * len(lines)
    if len(aspects) != len(lines) or any(a <= 0 for a in aspects):
        raise ValueError("aspects needs one positive number per line (%d), got %r" % (len(lines), aspects))
    widest = max(lines)

    def total_height(cell, wrap_rows):
        rows = sum(r * (round(cell * a) + label_h + pad) for r, a in zip(wrap_rows, aspects))
        return header + pad + rows + ref_lines * (cell + label_h + pad)

    best = None
    for wrap in range(1, widest + 1):
        cols = -(-widest // wrap)
        rows = [-(-n // cols) for n in lines]
        by_w = (max_side - pad) // cols - pad
        room = max_side - header - pad - (sum(rows) + ref_lines) * (label_h + pad)
        by_h = int(room / (sum(r * a for r, a in zip(rows, aspects)) + ref_lines))
        cell = max(1, min(by_w, by_h, max_cell))
        while cell > 1 and total_height(cell, rows) > max_side:      # round() of the row heights can overshoot by a px
            cell -= 1
        if best is None or cell > best[0]:
            best = (cell, wrap, cols, rows)
        if cell >= target_cell:
            best = (cell, wrap, cols, rows)
            break
    cell, wrap, cols, rows = best
    heights = [round(cell * a) for a in aspects]
    positions, y = [], header + pad
    for n, r, h in zip(lines, rows, heights):
        positions.append([(pad + (k % cols) * (cell + pad), y + (k // cols) * (h + label_h + pad)) for k in range(n)])
        y += r * (h + label_h + pad)
    width = pad + cols * (cell + pad)
    ref_boxes = []
    for _ in range(ref_lines):
        ref_boxes.append((pad, y, width - 2 * pad, cell + label_h))
        y += cell + label_h + pad
    return Layout(width=width, height=y, cell=cell, heights=heights, cols=cols, wrap=wrap, pad=pad, header=header,
                  label_h=label_h, positions=positions, ref_boxes=ref_boxes, max_side=max_side)


# ------------------------------------------------------------------ following a vertex --------------------------
def pick_tracked(faces, tags, bone, kind):
    """(face index, vertex index) of the vertex of `bone`'s subtree to follow: kind 'far' = the one farthest from the
    bone's own pivot (a weapon's tip, a hand's fingers), 'low' = the lowest at rest (a heel, a paw). Choose it on the
    REST pose and follow it with tracked_point: the faces come in the same order in every pose.
    KeyError when no face belongs to `bone` (a typo must be loud, not an empty trail)."""
    import numpy as np
    if kind not in ("far", "low"):
        raise ValueError("kind must be 'far' or 'low', got %r" % (kind,))
    mine = [i for i, t in enumerate(tags) if bone in t.path]
    if not mine:
        raise KeyError("no part named %r (or under it) in the model; parts: %s"
                       % (bone, sorted({n for t in tags for n in t.path})))
    t0 = tags[mine[0]]
    pivot = np.asarray(t0.pivots[t0.path.index(bone)], dtype=float)
    best, best_key = None, None
    for i in mine:
        for vi, v in enumerate(faces[i][0]):
            v = np.asarray(v, dtype=float)
            key = float(np.linalg.norm(v - pivot)) if kind == "far" else float(v[1])    # model y points DOWN
            if best_key is None or key > best_key + 1e-9:
                best, best_key = (i, vi), key
    return best


def tracked_point(faces, ref):
    """The model-space position of the followed vertex in this pose."""
    fi, vi = ref
    return tuple(float(c) for c in faces[fi][0][vi])


def to_pixel(point, view, box, size):
    """Where a model-space point lands in a render(...) of `size` px framed by `box` (the renderer's own maths)."""
    import numpy as np
    import game_preview as gp
    from preview_model import view_matrix
    R = np.array(view_matrix(view[0], view[1]))
    P = R @ (np.asarray(point, dtype=float) * gp._FLIP + gp._LIFT)
    sc, ox, oy = gp.view_map(box, size)
    return ox + P[0] * sc, oy - P[1] * sc


# ------------------------------------------------------------------ drawing -------------------------------------
BG = (24, 26, 24)
INK = (232, 232, 222)
DIM = (150, 154, 146)
HIT = (255, 170, 40)
TRAIL_COLOURS = [(255, 150, 40), (70, 210, 235), (235, 90, 200), (150, 230, 80), (240, 220, 80)]


def font(px):
    from PIL import ImageFont
    try:
        return ImageFont.load_default(size=px)
    except TypeError:                                   # Pillow < 10.1: the tiny bitmap font is all there is
        return ImageFont.load_default()


def onion(frames, ground=(0.50, 0.62, 0.36)):
    """Every key frame laid over one another, the first faint and the last strong: the figure's sweep in one cell.
    Pixels that are just ground are skipped, so the ground stays clean."""
    import numpy as np
    from PIL import Image
    bg = np.array([round(c * 255) for c in ground], dtype=float)
    out = np.tile(bg, (frames[0].size[1], frames[0].size[0], 1))
    n = len(frames)
    for k, im in enumerate(frames):
        a = np.asarray(im.convert("RGB"), dtype=float)
        body = (np.abs(a - bg).sum(axis=2) > 24)[..., None]
        w = (0.22 + 0.62 * k / max(1, n - 1)) * body
        out = out * (1 - w) + a * w
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8))


def draw_trails(im, paths, marks=(), width=2):
    """Polylines (pixel coordinates, one list per tracked point) over `im`, a dot with the frame number where each
    key frame's point is. `paths` = {name: [(x, y), ...]}; `marks` = {name: [(x, y, frame number), ...]}."""
    from PIL import ImageDraw
    d = ImageDraw.Draw(im)
    f = font(11)
    for ci, (name, pts) in enumerate(paths.items()):
        colour = TRAIL_COLOURS[ci % len(TRAIL_COLOURS)]
        if len(pts) > 1:
            d.line(pts, fill=colour, width=width)
        for (x, y, num) in marks.get(name, ()):
            d.ellipse((x - 4, y - 4, x + 4, y + 4), fill=colour, outline=(0, 0, 0))
            d.text((x + 6, y - 7), str(num), fill=colour, font=f, stroke_width=1, stroke_fill=(0, 0, 0))
    return im


def compose(title, legend, view_rows, lay, labels, hit_index=None, trail_cells=None, trail_key=(), ref=None):
    """Draw the sheet. view_rows = [(view name, [cell images])]; labels = one caption per frame; hit_index = the frame
    with the blow (orange frame); trail_cells = [one image per view] drawn on the line after the frame lines, and
    trail_key = the names of the paths in them, written in their colours after the legend; ref = a PIL image (or
    list) placed in the reference band(s)."""
    from PIL import Image, ImageDraw
    sheet = Image.new("RGB", (lay.width, lay.height), BG)
    d = ImageDraw.Draw(sheet)
    big, small = font(15), font(13)
    d.text((6, 4), title, fill=INK, font=big)
    d.text((6, 22), legend, fill=DIM, font=small)
    kx = 6 + d.textlength(legend, font=small) + 14
    for ci, name in enumerate(trail_key):
        d.text((kx, 22), name, fill=TRAIL_COLOURS[ci % len(TRAIL_COLOURS)], font=small)
        kx += d.textlength(name, font=small) + 12
    for li, (vname, cells) in enumerate(view_rows):
        h = lay.heights[li]
        for k, im in enumerate(cells):
            x, y = lay.positions[li][k]
            sheet.paste(im if im.size == (lay.cell, h) else im.resize((lay.cell, h)), (x, y))
            if hit_index is not None and k == hit_index:
                d.rectangle((x - 1, y - 1, x + lay.cell, y + h), outline=HIT, width=3)
            d.text((x + 3, y + h + 1), labels[k], fill=HIT if k == hit_index else INK, font=small)
        x0, y0 = lay.positions[li][0]
        d.rectangle((x0, y0, x0 + 8 + 8 * len(vname), y0 + 16), fill=(0, 0, 0))
        d.text((x0 + 4, y0 + 1), vname, fill=INK, font=small)
    if trail_cells:
        li = len(view_rows)
        h = lay.heights[li]
        for k, im in enumerate(trail_cells):
            x, y = lay.positions[li][k]
            sheet.paste(im if im.size == (lay.cell, h) else im.resize((lay.cell, h)), (x, y))
            d.text((x + 3, y + h + 1), "%s: sweep + paths" % view_rows[k][0], fill=INK, font=small)
    if ref is not None:
        refs = ref if isinstance(ref, (list, tuple)) else [ref]
        for (bx, by, bw, bh), im in zip(lay.ref_boxes, refs):
            s = min(bw / im.size[0], (bh - lay.label_h) / im.size[1])
            im2 = im.convert("RGB").resize((max(1, int(im.size[0] * s)), max(1, int(im.size[1] * s))))
            sheet.paste(im2, (bx, by))
            d.text((bx + im2.size[0] + 8, by + 4), "reference", fill=DIM, font=small)
    return sheet
