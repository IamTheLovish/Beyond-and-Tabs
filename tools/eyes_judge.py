#!/usr/bin/env python3
"""Judge the CI "eyes" structure stills with proof and numbers, not impressions (Oct 28 2026).

Hours were lost because critics judged stills that did not show the structure, and because their colour claims
("olive", "too dark") were wrong when measured. The rule: NO PROOF THAT THE SUBJECT IS IN THE STILL -> NO CRITIQUE, and
every colour claim is a measured number. Input: local clones of the eyes branches (folders holding the PNGs and
eyes-lines.txt), e.g.

    git clone -q --depth=1 -b eyes-structures-shaders https://github.com/IamTheLovish/Beyond-and-Tabs es-shaders
    python3 tools/eyes_judge.py es-shaders --sheet-dir sheets --save round-12.json
    python3 tools/eyes_judge.py plain=es-plain shaders=es-shaders --against round-12.json

1. PROOF GATE, per RTS still (`bat_eyes_<step>_<name>_rts.png` and `_rts_close.png`) and per structure close-up
   (`bat_eyes_15_<name>_close.png`, first person among the plants: crop CLOSE_CROP, no star count). EyesDirector.logAim writes, per
   still, three lines into eyes-lines.txt:
       [Eyes] 15_meadow_sampler_rts: aimed at -271, 76, 41 - the camera at -276, 91, 46 looks at -271, 41 (28 frames ...)
       [Eyes] 15_meadow_sampler_rts: the structure's box holds 3648 non-air blocks on the client
       [Eyes] 15_meadow_sampler_rts: ... and 3648 on the server        (queued to the server thread: may land later)
   A still FAILS when a line is missing, a count is 0, the client holds fewer than 0.9 x the server's blocks (the
   structure is not loaded / meshed on the client: the "unmeshed void" stills) or the camera's look-at point is more
   than 4 blocks from the aim point. Any failure -> exit 1, and the still gets NO numbers and a red banner on the sheet.
   First-person stills (`_34`, `_dusk`) log no such lines: they are listed as "not judged" (the tour's own stills
   are ignored).
2. MEASUREMENTS on a crop of each passing still (default (900,80)-(1400,300); the meadow still has open meadow in the table;
   `--crop [NAME=]X0,Y0,X1,Y1` override it): median hue (degrees), median HSV saturation (0-100), median Rec.601 luma
   (0-255 and 0-1) and "dark share" = the fraction of pixels under 0.8 x the median luma. The definitions are those of
   the Oct 28 measure.py, so the numbers stay comparable with past rounds.
3. ROUND COMPARE: `--save round.json` writes everything; `--against prev.json` prints the change per still and metric.
4. CRITIC SHEET (`--sheet-dir DIR`): one PNG per lane, each RTS still labelled with its proof and numbers, the crop
   outlined on it, and a 2x nearest-neighbour zoom of exactly the measured crop - a critic sees the pixels that were
   measured. Under 2500 px wide.
5. A markdown summary on stdout; the FAIL lines on stderr.

Exit code: 0 all stills have proof; 1 a still (or a lane) failed the gate; 2 usage error (bad folder, bad crop).
Pillow + stdlib only.
"""
import argparse
import colorsys
import json
import math
import os
import re
import statistics
import subprocess
import sys

from PIL import Image, ImageDraw, ImageFont

MIN_CLIENT_SHARE = 0.9          # the client must hold at least this share of the server's non-air blocks
AIM_TOLERANCE = 4.0             # blocks between where the camera was told to look and where it looks
DARK_FRACTION = 0.8             # "dark" = under this share of the median luma
DEFAULT_CROP = (900, 80, 1400, 300)
CROPS = {                       # per still, by short name (the key without its step prefix); the rest take the default
    # open meadow right of the path. Rounds before r33 (Oct 28) used (900,80)-(1400,300), which holds a tree's
    # shadow in its corner and swamps the tufts' dark share: re-measure old rounds with --crop to compare.
    "meadow_sampler_rts": (1000, 250, 1415, 383),
}
# 15_<name>_close stands 5 blocks from the footprint's middle, looking slightly down: the ground in front of the feet is
# the lower middle of the 1600x900 still (the RTS default crop is the horizon there)
CLOSE_CROP = (500, 520, 1100, 820)
METRICS = ("hue", "sat", "luma", "dark_share", "stars",      # the compared metrics (luma01 is luma / 255)
           "mass_share", "dark_share_open", "stars_open")
SHEET_MAX_W = 2500
THUMB_W = 800
PAD = 12

# the RTS stills, and the structure photos' first-person close still (`15_<name>_close`, #332): it logs the same proof
# lines. The tour's own close-ups (07_army_close, 07b_paladin_close ...) log none and are not structure photos.
STILL_RE = re.compile(r"^bat_eyes_(?P<key>.+_rts(?:_close)?|15[a-z]?_.+_close)\.png$")
KEY_RE = re.compile(r"^(?:.+_rts(?:_close)?|15[a-z]?_.+_close)$")
FIRST_PERSON_RE = re.compile(r"^bat_eyes_\d+[a-z]?_.+_(?:34|dusk)\.png$")     # the structure photos that log no proof
STEP_RE = re.compile(r"^\d+[a-z]?_")
_N = r"-?\d+"
AIM_RE = re.compile(rf"\[Eyes\] (?P<key>\S+): aimed at (?P<ax>{_N}), (?P<ay>{_N}), (?P<az>{_N}) - the camera at "
                    rf".*? looks at (?P<lx>{_N}), (?P<lz>{_N}) \(")
CLIENT_RE = re.compile(r"\[Eyes\] (?P<key>\S+): the structure's box holds (?P<n>\d+) non-air blocks on the client")
SERVER_RE = re.compile(r"\[Eyes\] (?P<key>\S+): \.\.\. and (?P<n>\d+) on the server")
BUILD_RE = re.compile(r"Screenshots from build (?P<build>\S+) at (?P<at>\S+)")


# ----------------------------------------------------------------------------------------------------------- stills

def still_key(filename):
    """`bat_eyes_15_meadow_sampler_rts.png` -> `15_meadow_sampler_rts`; None for anything that is not an RTS still."""
    m = STILL_RE.match(os.path.basename(filename))
    return m.group("key") if m else None


def is_first_person_close(key):
    """`15_meadow_sampler_close` (the structure photos' first-person close-up), not `..._rts_close`."""
    return key.endswith("_close") and not key.endswith("_rts_close")


def short_name(key):
    """`15_meadow_sampler_rts` -> `meadow_sampler_rts`."""
    return STEP_RE.sub("", key, count=1)


# ------------------------------------------------------------------------------------------------------ proof gate

def parse_lines(text):
    """eyes-lines.txt -> {still key: {aimed_at, looks_at, client, server}}; a missing line stays None. The last line of
    a kind wins (a still shot twice). The server's line is queued to the server thread, so it may come after the
    client's, after the clip-mark, or after the next still's lines: it is matched by name, never by position."""
    found = {}

    def entry(key):
        return found.setdefault(key, {"aimed_at": None, "looks_at": None, "client": None, "server": None})

    for line in text.splitlines():
        if "[Eyes]" not in line:
            continue
        m = AIM_RE.search(line)
        if m:
            e = entry(m.group("key"))
            e["aimed_at"] = (int(m.group("ax")), int(m.group("ay")), int(m.group("az")))
            e["looks_at"] = (int(m.group("lx")), int(m.group("lz")))
            continue
        m = CLIENT_RE.search(line)
        if m:
            entry(m.group("key"))["client"] = int(m.group("n"))
            continue
        m = SERVER_RE.search(line)
        if m:
            entry(m.group("key"))["server"] = int(m.group("n"))
    return found


def check_proof(p, min_share=MIN_CLIENT_SHARE, aim_tol=AIM_TOLERANCE):
    """The reasons a still has no proof that its subject is in the picture (empty list = proof)."""
    if p is None:
        return ["no evidence lines for this still in eyes-lines.txt ('aimed at ... looks at ...', the box counts)"]
    problems = []
    if p["aimed_at"] is None or p["looks_at"] is None:
        problems.append("the 'aimed at ... looks at ...' line is missing")
    else:
        ax, _, az = p["aimed_at"]
        lx, lz = p["looks_at"]
        off = math.hypot(ax - lx, az - lz)
        if off > aim_tol:
            problems.append(f"the camera looks at {lx}, {lz}, {off:.0f} blocks from the aim point {ax}, {az} "
                            f"(limit {aim_tol:g}): the structure is not in the middle of the picture")
    client, server = p["client"], p["server"]
    if client is None:
        problems.append("the 'box holds N non-air blocks on the client' line is missing")
    elif client == 0:
        problems.append("the structure's box holds 0 non-air blocks on the client")
    if server is None:
        problems.append("the '... and N on the server' line is missing")
    elif server == 0:
        problems.append("the structure's box holds 0 non-air blocks on the server")
    if client and server and client < min_share * server - 1e-9:
        problems.append(f"the client holds only {client} of the server's {server} non-air blocks "
                        f"({client / server:.0%}, need {min_share:.0%}): the structure is not fully there on the client")
    return problems


# ----------------------------------------------------------------------------------------------------- measurements

def _check_box(box, size):
    x0, y0, x1, y1 = box
    if not (0 <= x0 < x1 <= size[0] and 0 <= y0 < y1 <= size[1]):
        raise ValueError(f"crop {x0},{y0},{x1},{y1} is outside the still ({size[0]}x{size[1]}) or empty")


def measure(im, box):
    """Hue / sat / luma medians and dark share on a crop - the Oct 28 measure.py's definitions, exactly: hue in
    degrees and sat 0-100 from colorsys' HSV, luma 0-255 Rec.601, dark share = pixels under 0.8 x the median luma."""
    _check_box(box, im.size)
    data = im.convert("RGB").crop(box).tobytes()        # (Image.getdata is deprecated in Pillow 12; same values)
    hs, ss, ls = [], [], []
    for r, g, b in zip(data[0::3], data[1::3], data[2::3]):
        h, s, _v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
        hs.append(h * 360)
        ss.append(s * 100)
        ls.append(0.299 * r + 0.587 * g + 0.114 * b)
    ml = statistics.median(ls)
    dark = sum(1 for v in ls if v < DARK_FRACTION * ml) / len(ls)
    # the open-ground numbers (Oct 29, r40: a darker oak canopy in the crop counted as dark share and as stars - the
    # judge punished the fix): the same definitions over the pixels outside the big dark masses (mass_mask)
    mask = mass_mask(im, box)
    open_ls = [v for v, m in zip(ls, mask) if not m]
    if open_ls:
        mo = statistics.median(open_ls)
        dark_open = sum(1 for v in open_ls if v < DARK_FRACTION * mo) / len(open_ls)
    else:
        dark_open = None
    return {"hue": statistics.median(hs), "sat": statistics.median(ss), "luma": ml, "luma01": ml / 255,
            "dark_share": dark, "stars": count_stars(im, box),
            "mass_share": sum(mask) / len(mask), "dark_share_open": dark_open,
            "stars_open": count_stars(im, box, mask) if open_ls else None}


MASS_BLUR = 6                  # px: a canopy's clumps and a tree's shadow blur into one mass; tuft stars fade out
MASS_FRACTION = 0.85           # "dark" for a mass: under this share of the blurred crop's median luma
MASS_MIN = 1500                # px: a connected dark region at least this big is a canopy / shadow / wall, not lawn
MASS_PAD = 4                   # px grown round each mass: its soft, half-dark rim belongs to it


def _regions(flags, w, h):
    """4-connected regions of the truthy cells of a w x h bytearray -> lists of indices (flags is consumed)."""
    out = []
    for start in range(w * h):
        if not flags[start]:
            continue
        flags[start] = 0
        stack, cells = [start], []
        while stack:
            i = stack.pop()
            cells.append(i)
            x, y = i % w, i // w
            for j, ok in ((i - 1, x > 0), (i + 1, x < w - 1), (i - w, y > 0), (i + w, y < h - 1)):
                if ok and flags[j]:
                    flags[j] = 0
                    stack.append(j)
        out.append(cells)
    return out


def mass_mask(im, box):
    """1 per crop pixel that lies in a big dark MASS - a tree crown, a tree's or a wall's shadow - else 0 (row-major,
    the crop's size). After a MASS_BLUR px blur, the 4-connected regions under MASS_FRACTION x the median luma with
    at least MASS_MIN px, grown by MASS_PAD px. The lawn's grain and its tuft stars are far smaller and stay open."""
    from PIL import ImageFilter
    g = im.convert("L").crop(box).filter(ImageFilter.GaussianBlur(MASS_BLUR))
    w, h = g.size
    px = g.tobytes()
    cut = MASS_FRACTION * statistics.median(px)
    keep = bytearray(w * h)
    for cells in _regions(bytearray(1 if v < cut else 0 for v in px), w, h):
        if len(cells) >= MASS_MIN:
            for i in cells:
                keep[i] = 255
    if MASS_PAD and any(keep):
        keep = bytearray(Image.frombytes("L", (w, h), bytes(keep)).filter(ImageFilter.MaxFilter(2 * MASS_PAD + 1))
                         .tobytes())
    return bytearray(1 if v else 0 for v in keep)


STAR_BLUR = 2                  # px: smooths lawn grain away before the threshold
STAR_FRACTION = 0.75           # "dark" for a star: under this share of the blurred crop's median luma
STAR_MIN, STAR_MAX = 15, 300   # px: a tuft's shadow star at the RTS zoom; bigger is a tree / wall / building shadow


def count_stars(im, box, mask=None):
    """Tuft-sized dark spots in the crop (the Oct 28 grass-tuft "stars"): after a STAR_BLUR px Gaussian blur, the
    4-connected regions under STAR_FRACTION x the median luma whose size is STAR_MIN..STAR_MAX px. Big static
    shadows (trees, walls, the battlefield-wall smears) and lawn grain do not count, unlike in the dark share.
    mask (mass_mask): only the open ground - the median is taken over it and a spot touching a mass is not a star
    (a dark canopy's clump pockets are star-sized)."""
    from PIL import ImageFilter
    g = im.convert("L").crop(box).filter(ImageFilter.GaussianBlur(STAR_BLUR))
    w, h = g.size
    px = g.tobytes()
    ref = [v for v, m in zip(px, mask) if not m] if mask is not None else px
    cut = STAR_FRACTION * statistics.median(ref)
    dark = bytearray(1 if v < cut else 0 for v in px)
    return sum(1 for cells in _regions(dark, w, h)
               if STAR_MIN <= len(cells) <= STAR_MAX and not (mask is not None and any(mask[i] for i in cells)))


def parse_box(text):
    try:
        x0, y0, x1, y1 = (int(v) for v in text.split(","))
    except ValueError:
        raise ValueError(f"crop '{text}' is not X0,Y0,X1,Y1 (four whole numbers)") from None
    if x0 < 0 or y0 < 0 or x1 <= x0 or y1 <= y0:
        raise ValueError(f"crop '{text}' is empty or negative (need 0 <= X0 < X1 and 0 <= Y0 < Y1)")
    return x0, y0, x1, y1


def crop_for(key, overrides):
    """(box, where it came from): a flag naming this still, else a bare flag, else the built-in table, else the default."""
    named = [box for name, box in overrides if name is not None and name in (key, short_name(key))]
    if named:
        return named[-1], "flag"
    bare = [box for name, box in overrides if name is None]
    if bare:
        return bare[-1], "flag"
    if short_name(key) in CROPS:
        return CROPS[short_name(key)], "table"
    if is_first_person_close(key):
        return CLOSE_CROP, "close"
    return DEFAULT_CROP, "default"


# ----------------------------------------------------------------------------------------------------------- lanes

def split_lane_arg(arg):
    """`label=path` -> (label, path); a plain path (even one holding '=' after a separator) -> (None, path)."""
    if "=" in arg:
        left, right = arg.split("=", 1)
        if left and not any(c in left for c in "/\\:"):
            return left, right
    return None, arg


def lane_label(path):
    """The branch a clone has checked out (so rounds match whatever the folder is called), else the folder's name."""
    path = os.path.abspath(path)
    if os.path.exists(os.path.join(path, ".git")):
        try:
            r = subprocess.run(["git", "-C", path, "rev-parse", "--abbrev-ref", "HEAD"], capture_output=True,
                               text=True, timeout=20)
            name = r.stdout.strip()
            if r.returncode == 0 and name and name != "HEAD":
                return name
        except (OSError, subprocess.SubprocessError):
            pass
    return os.path.basename(path.rstrip("/\\")) or path


def natural_key(key):
    return [int(t) if t.isdigit() else t for t in re.split(r"(\d+)", key)]


def judge_lane(lane_dir, label, crops, min_share=MIN_CLIENT_SHARE, aim_tol=AIM_TOLERANCE):
    """Gate and measure every RTS still of one lane folder -> a JSON-ready dict (see the --save file)."""
    names = sorted(os.listdir(lane_dir))
    lane = {"label": label, "build": None, "built_at": None, "problems": [], "stills": {}, "not_judged": []}
    readme = os.path.join(lane_dir, "README.md")
    if os.path.isfile(readme):
        with open(readme, encoding="utf-8", errors="replace") as f:
            m = BUILD_RE.search(f.read())
        if m:
            lane["build"], lane["built_at"] = m.group("build"), m.group("at")
    lines_path = os.path.join(lane_dir, "eyes-lines.txt")
    proofs = {}
    if os.path.isfile(lines_path):
        with open(lines_path, encoding="utf-8", errors="replace") as f:
            proofs = parse_lines(f.read())
    else:
        lane["problems"].append("eyes-lines.txt is missing from the lane folder: no still has proof")
    files = {still_key(n): n for n in names if still_key(n)}
    lane["not_judged"] = [n[len("bat_eyes_"):-len(".png")] for n in names if FIRST_PERSON_RE.match(n)]
    keys = set(files) | {k for k in proofs if KEY_RE.match(k)}
    if not files:
        lane["problems"].append("no RTS still (bat_eyes_*_rts.png, *_rts_close.png) in the lane folder: nothing to judge")
    for key in sorted(keys, key=natural_key):
        p = proofs.get(key)
        problems = check_proof(p, min_share, aim_tol)
        box, source = crop_for(key, crops)
        image = None
        if key not in files:
            problems.append(f"bat_eyes_{key}.png is missing (the log says it was shot): the PNG was not published")
        else:
            try:
                with Image.open(os.path.join(lane_dir, files[key])) as im:
                    image = im.convert("RGB")
            except OSError as e:
                problems.append(f"the PNG cannot be read ({e})")
        metrics = None
        if not problems and image is not None:
            try:
                metrics = {k: None if v is None else round(v, 5) for k, v in measure(image, box).items()}
                if is_first_person_close(key):
                    # a star is a tuft's shadow at the RTS zoom (STAR_MIN..STAR_MAX px); 5 blocks away the same
                    # tuft is many times bigger, so the count would mean nothing: no number rather than a wrong one
                    metrics["stars"] = metrics["stars_open"] = None
            except ValueError as e:
                raise ValueError(f"{label}/{key}: {e}") from None
        lane["stills"][key] = {
            "file": files.get(key),
            "proof": {"ok": not problems, "problems": problems,
                      "client": p["client"] if p else None, "server": p["server"] if p else None,
                      "aimed_at": list(p["aimed_at"]) if p and p["aimed_at"] else None,
                      "looks_at": list(p["looks_at"]) if p and p["looks_at"] else None},
            "crop": list(box), "crop_source": source, "metrics": metrics,
        }
    return lane


def lane_failed(lane):
    return bool(lane["problems"]) or any(not s["proof"]["ok"] for s in lane["stills"].values())


# ------------------------------------------------------------------------------------------------- round to round

def compare(prev, cur):
    """Rows {lane, still, metric, old, new, delta, note} for every still of `cur`'s lanes (and the stills `prev` had
    that are gone). delta is None, with a note, when either side has no numbers or the crop changed."""
    rows = []

    def add(lane, key, metric, old, new, delta, note):
        rows.append({"lane": lane, "still": key, "metric": metric, "old": old, "new": new, "delta": delta,
                     "note": note})

    for label, lane in cur.get("lanes", {}).items():
        before = prev.get("lanes", {}).get(label)
        for key, st in lane["stills"].items():
            old = before["stills"].get(key) if before else None
            note = ""
            if old is None:
                note = "new in this round" if before else "new in this round (lane not in the earlier file)"
            elif old.get("metrics") is None:
                note = "no numbers in the earlier round (no proof)"
            elif st["metrics"] is None:
                note = "no numbers this round (no proof)"
            elif old.get("crop") != st["crop"]:
                note = f"crop changed {old.get('crop')} -> {st['crop']}: not comparable"
            for metric in METRICS:
                a = old["metrics"].get(metric) if old and old.get("metrics") else None     # rounds before stars
                b = st["metrics"].get(metric) if st["metrics"] else None                  # have no stars key
                delta = None
                if not note and (a is None or b is None):
                    note_m = "not measured in the earlier round" if a is None else "not measured this round"
                    add(label, key, metric, a, b, None, note_m)
                    continue
                if not note:
                    delta = b - a
                    if metric == "hue":
                        delta = (b - a + 180) % 360 - 180
                add(label, key, metric, a, b, delta, note)
        for key, old in (before["stills"] if before else {}).items():
            if key not in lane["stills"]:
                for metric in METRICS:
                    add(label, key, metric, old["metrics"].get(metric) if old.get("metrics") else None, None, None,
                        "gone this round")
    return rows


# ------------------------------------------------------------------------------------------------------------ sheet

def _font(size):
    try:
        return ImageFont.load_default(size=size)
    except TypeError:       # Pillow < 10.1: the fixed bitmap font
        return ImageFont.load_default()


def _numbers(st):
    m = st["metrics"]
    return (f"hue {m['hue']:.0f} deg   sat {m['sat']:.0f}   luma {m['luma']:.0f} ({m['luma01']:.2f})   "
            f"dark share {m['dark_share']:.3f}   stars {'-' if m['stars'] is None else m['stars']}"
            + (f"   | open ground (masses {m['mass_share']:.0%} masked): dark {_cell(m.get('dark_share_open'), '.3f')}"
               f"   stars {_cell(m.get('stars_open'), 'd')}" if m.get("mass_share") is not None else ""))


def build_sheet(lane_dir, label, lane, max_width=SHEET_MAX_W):
    """The critic sheet of one lane -> (PIL image, tiles). Per RTS still: a label strip (proof, crop, numbers), the still
    (<= 800 px wide) with the measured crop outlined, and the crop at 2x nearest-neighbour beside it (stacked under the
    still, at 1x if need be, when it does not fit in max_width). A still without proof gets a red banner and no zoom."""
    font, small = _font(18), _font(15)
    head_h, strip_h = 34, 52
    tiles, rows = [], []
    for key, st in lane["stills"].items():
        im = None
        if st["file"]:
            try:
                with Image.open(os.path.join(lane_dir, st["file"])) as f:
                    im = f.convert("RGB")
            except OSError:
                im = None
        ok = st["proof"]["ok"] and im is not None
        tw = min(THUMB_W, im.width) if im else 0
        th = round(im.height * tw / im.width) if im else 0
        box = tuple(st["crop"])
        cw, ch = box[2] - box[0], box[3] - box[1]
        side = max_width - tw - 3 * PAD
        zoom, stacked = 0, False
        if ok:       # 2x beside the still, else 2x under it, else 1x beside, else 1x under (whatever its width)
            fits = ((2, False, side), (2, True, max_width - 2 * PAD), (1, False, side), (1, True, max_width - 2 * PAD))
            zoom, stacked = next(((z, s) for z, s, room in fits if cw * z <= room), (1, True))
        rows.append((key, st, im, ok, tw, th, box, cw, ch, zoom, stacked))
    width = max([2 * PAD + 600] + [2 * PAD + (max(tw, cw * zoom) if stacked else tw + PAD + cw * zoom)
                                   for key, st, im, ok, tw, th, box, cw, ch, zoom, stacked in rows])
    foot = [f"not judged (first-person stills log no proof lines): {', '.join(lane['not_judged'])}"] \
        if lane["not_judged"] else []
    heights = []
    for key, st, im, ok, tw, th, box, cw, ch, zoom, stacked in rows:
        body = (th + PAD + ch * zoom if stacked else max(th, ch * zoom)) if ok else max(th, 60)
        heights.append(strip_h + body + PAD)
    total_h = head_h + sum(heights) + (24 if foot else 0) + PAD
    sheet = Image.new("RGB", (width, max(total_h, head_h + PAD)), (24, 24, 28))
    d = ImageDraw.Draw(sheet)
    verdict = "PROOF GATE FAILED" if lane_failed(lane) else "proof gate passed"
    d.rectangle((0, 0, width, head_h), fill=(150, 30, 30) if lane_failed(lane) else (30, 90, 50))
    d.text((PAD, 7), f"{label}   build {lane['build'] or '?'}   {lane['built_at'] or ''}   {verdict}", fill="white",
           font=font)
    y = head_h
    for (key, st, im, ok, tw, th, box, cw, ch, zoom, stacked), h in zip(rows, heights):
        pr = st["proof"]
        d.rectangle((0, y, width, y + strip_h - 4), fill=(60, 20, 20) if not ok else (36, 36, 44))
        counts = f"client {pr['client']} / server {pr['server']} non-air blocks"
        if ok:
            d.text((PAD, y + 4), f"{key}   PROOF OK ({counts})", fill=(150, 230, 150), font=font)
            d.text((PAD, y + 28), f"crop ({box[0]},{box[1]})-({box[2]},{box[3]}) [{st['crop_source']}]   "
                                  f"{_numbers(st)}", fill=(225, 225, 225), font=small)
        else:
            d.text((PAD, y + 4), f"{key}   NO PROOF - DO NOT CRITIQUE   ({counts})", fill=(255, 120, 120), font=font)
            d.text((PAD, y + 28), "; ".join(pr["problems"])[:260], fill=(255, 190, 190), font=small)
        top = y + strip_h
        tile = {"key": key, "crop": list(box), "zoom": zoom, "zoom_box": None, "no_proof": not ok,
                "thumb_box": None}
        if im is not None:
            thumb = im.resize((tw, th), Image.LANCZOS) if tw != im.width else im.copy()
            td = ImageDraw.Draw(thumb)
            if ok:
                k = tw / im.width
                td.rectangle((box[0] * k - 1, box[1] * k - 1, box[2] * k, box[3] * k), outline=(255, 0, 255), width=2)
            sheet.paste(thumb, (PAD, top))
            tile["thumb_box"] = (PAD, top, tw, th)
            if not ok:
                d.rectangle((PAD - 2, top - 2, PAD + tw + 1, top + th + 1), outline=(220, 40, 40), width=3)
        if ok:
            zim = im.crop(box).resize((cw * zoom, ch * zoom), Image.NEAREST)
            zx, zy = (PAD, top + th + PAD) if stacked else (PAD + tw + PAD, top)
            sheet.paste(zim, (zx, zy))
            tile["zoom_box"] = (zx, zy, cw * zoom, ch * zoom)
        tiles.append(tile)
        y += h
    if foot:
        d.text((PAD, y + 2), foot[0][:300], fill=(170, 170, 170), font=small)
    return sheet, tiles


# ------------------------------------------------------------------------------------------------------ A/B pair

def pair_rows(a, b):
    """One row per still in either lane: both lanes' metrics and B - A per metric (hue circular). A still in one lane
    only, or without numbers on a side, gets delta None and a note saying why."""
    rows = []
    for key in sorted(set(a["stills"]) | set(b["stills"]), key=natural_key):
        sa, sb = a["stills"].get(key), b["stills"].get(key)
        ma, mb = sa and sa["metrics"], sb and sb["metrics"]
        note, delta = "", None
        if sa is None or sb is None:
            note = f"only in {a['label'] if sb is None else b['label']}"
        elif ma is None or mb is None:
            note = "no numbers in " + " and ".join(lb["label"] for lb, m in ((a, ma), (b, mb)) if m is None) + " (no proof)"
        elif sa["crop"] != sb["crop"]:
            note = f"crop differs {sa['crop']} / {sb['crop']}: not comparable"
        else:
            delta = {}
            for m in METRICS + ("luma01",):
                x, y = ma.get(m), mb.get(m)
                if x is None or y is None:
                    delta[m] = None
                elif m == "hue":
                    delta[m] = (y - x + 180) % 360 - 180
                else:
                    delta[m] = y - x
        rows.append({"still": key, "a": ma, "b": mb, "delta": delta, "note": note})
    return rows


def _pair_text(row):
    if row["delta"] is None:
        return row["note"]
    d = row["delta"]
    parts = []
    for m, f in (("hue", "+.0f"), ("sat", "+.0f"), ("luma", "+.0f"), ("dark_share", "+.3f"), ("stars", "+.0f"),
                 ("dark_share_open", "+.3f"), ("stars_open", "+.0f")):
        if d.get(m) is not None:
            parts.append(f"{m} {format(d[m], f)}")
    return "B - A:  " + "   ".join(parts)


def build_pair_sheet(dir_a, a, dir_b, b, max_width=SHEET_MAX_W):
    """Every still of either lane as a row: A's still on the left, B's on the right (the measured crop outlined),
    each side's numbers above it and B - A under the row. Under max_width wide."""
    font, small = _font(18), _font(15)
    rows = pair_rows(a, b)
    half = (max_width - 3 * PAD) // 2
    head_h, strip_h, foot_h = 34, 52, 26
    tiles = []
    for row in rows:
        ims = []
        for d, lane in ((dir_a, a), (dir_b, b)):
            st = lane["stills"].get(row["still"])
            im = None
            if st and st["file"]:
                try:
                    with Image.open(os.path.join(d, st["file"])) as f:
                        im = f.convert("RGB")
                except OSError:
                    im = None
            ims.append((st, im))
        tw = min(half, max([im.width for _, im in ims if im] or [half]))
        th = max([round(im.height * tw / im.width) for _, im in ims if im] or [60])
        tiles.append((row, ims, tw, th))
    width = max(2 * PAD + 600, max([3 * PAD + 2 * t[2] for t in tiles] or [0]))
    height = head_h + sum(strip_h + th + foot_h + PAD for _, _, _, th in tiles) + PAD
    sheet = Image.new("RGB", (width, height), (24, 24, 28))
    d = ImageDraw.Draw(sheet)
    d.rectangle((0, 0, width, head_h), fill=(40, 60, 90))
    d.text((PAD, 7), f"A = {a['label']} (build {a['build'] or '?'})    B = {b['label']} (build {b['build'] or '?'})",
           fill="white", font=font)
    y = head_h
    for row, ims, tw, th in tiles:
        d.rectangle((0, y, width, y + strip_h - 4), fill=(36, 36, 44))
        d.text((PAD, y + 4), row["still"], fill=(230, 230, 230), font=font)
        for i, (st, im) in enumerate(ims):
            x = PAD + i * (tw + PAD)
            label = "AB"[i]
            if st is None:
                txt, colour = f"{label}: not in this run", (255, 190, 190)
            elif not st["proof"]["ok"]:
                txt, colour = f"{label}: NO PROOF - DO NOT CRITIQUE", (255, 120, 120)
            elif st["metrics"]:
                txt, colour = f"{label}: {_numbers(st)}", (200, 220, 200)
            else:
                txt, colour = f"{label}:", (200, 200, 200)
            d.text((x, y + 28), txt[:110], fill=colour, font=small)
            if im is not None:
                h = round(im.height * tw / im.width)
                thumb = im.resize((tw, h), Image.LANCZOS) if tw != im.width else im.copy()
                if st and st["proof"]["ok"]:
                    k = tw / im.width
                    c = st["crop"]
                    ImageDraw.Draw(thumb).rectangle((c[0] * k - 1, c[1] * k - 1, c[2] * k, c[3] * k),
                                                    outline=(255, 0, 255), width=2)
                sheet.paste(thumb, (x, y + strip_h))
        d.text((PAD, y + strip_h + th + 4), _pair_text(row)[:200], fill=(255, 220, 140), font=small)
        y += strip_h + th + foot_h + PAD
    return sheet


def pair_table(a, b, rows):
    out = [f"### {a['label']} vs {b['label']} (B - A)", "",
           "| still | hue A / B | sat A / B | luma A / B | dark share A / B | stars A / B | open dark A / B "
           "| open stars A / B | note |",
           "|---|---:|---:|---:|---:|---:|---:|---:|---|"]

    def ab(row, m, f):
        x = row["a"] and row["a"].get(m)
        y = row["b"] and row["b"].get(m)
        return f"{_cell(x, f)} / {_cell(y, f)}"

    for r in rows:
        out.append(f"| {r['still']} | {ab(r, 'hue', '.0f')} | {ab(r, 'sat', '.0f')} | {ab(r, 'luma', '.0f')} | "
                   f"{ab(r, 'dark_share', '.3f')} | {ab(r, 'stars', 'd')} | {ab(r, 'dark_share_open', '.3f')} | "
                   f"{ab(r, 'stars_open', 'd')} | {r['note'] or _pair_text(r)} |")
    return out


# --------------------------------------------------------------------------------------------------------- output

def _cell(v, fmt):
    return "-" if v is None else format(v, fmt)


def table(lanes):
    out = ["| lane | still | proof | client / server | crop | hue (deg) | sat (0-100) | luma (0-255) | luma (0-1) "
           "| dark share | stars | mass share | dark share open | stars open |",
           "|---|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|"]
    for label, lane in lanes.items():
        for key, st in lane["stills"].items():
            pr, m, c = st["proof"], st["metrics"], st["crop"]
            out.append("| {} | {} | {} | {} / {} | ({},{})-({},{}) {} | {} | {} | {} | {} | {} | {} | {} | {} | {} |".format(
                label, key, "PASS" if pr["ok"] else "FAIL", _cell(pr["client"], "d"), _cell(pr["server"], "d"),
                c[0], c[1], c[2], c[3], st["crop_source"],
                _cell(m and m["hue"], ".0f"), _cell(m and m["sat"], ".0f"), _cell(m and m["luma"], ".0f"),
                _cell(m and m["luma01"], ".2f"), _cell(m and m["dark_share"], ".3f"), _cell(m and m["stars"], "d"),
                _cell(m and m.get("mass_share"), ".3f"), _cell(m and m.get("dark_share_open"), ".3f"),
                _cell(m and m.get("stars_open"), "d")))
    return out


def delta_table(rows):
    out = ["| lane | still | metric | before | after | change | note |", "|---|---|---|---:|---:|---:|---|"]
    for r in rows:
        f = {"dark_share": ".3f", "stars": ".0f", "mass_share": ".3f", "dark_share_open": ".3f",
             "stars_open": ".0f"}.get(r["metric"], ".1f")
        out.append("| {} | {} | {} | {} | {} | {} | {} |".format(
            r["lane"], r["still"], r["metric"], _cell(r["old"], f), _cell(r["new"], f),
            "-" if r["delta"] is None else format(r["delta"], "+" + f), r["note"]))
    return out


def _safe(name):
    return re.sub(r"[^\w.-]+", "_", name)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0],
                                 epilog="Exit 0: every RTS still has proof. 1: the proof gate failed. 2: usage error.")
    ap.add_argument("lanes", nargs="+", metavar="[LABEL=]DIR",
                    help="a clone of an eyes branch (PNGs + eyes-lines.txt); LABEL defaults to the checked-out branch, "
                         "else the folder name")
    ap.add_argument("--crop", action="append", default=[], metavar="[NAME=]X0,Y0,X1,Y1",
                    help="the measured crop: for the still NAME (e.g. meadow_sampler_rts_close), or for every still "
                         "without a NAME (default: the built-in table, else 900,80,1400,300)")
    ap.add_argument("--save", metavar="FILE", help="write this round's numbers as JSON")
    ap.add_argument("--against", metavar="FILE", help="a JSON of an earlier round: print the change per still and metric")
    ap.add_argument("--sheet-dir", metavar="DIR", help="write one critic contact sheet PNG per lane here")
    ap.add_argument("--pair", action="store_true",
                    help="exactly two lanes A B: print A vs B per still and, with --sheet-dir, write <A>-vs-<B>.png "
                         "(both stills side by side, B - A under each row)")
    ap.add_argument("--min-client-share", type=float, default=MIN_CLIENT_SHARE,
                    help="fail when the client holds less than this share of the server's blocks (default 0.9)")
    ap.add_argument("--aim-tolerance", type=float, default=AIM_TOLERANCE,
                    help="fail when the camera looks more than this many blocks from the aim point (default 4)")
    args = ap.parse_args(argv)
    try:
        if args.pair and len(args.lanes) != 2:
            raise ValueError(f"--pair needs exactly two lanes (A B), got {len(args.lanes)}")
        crops = []
        for c in args.crop:
            name, _, box = c.rpartition("=")
            crops.append((name or None, parse_box(box)))
        prev = None
        if args.against:
            try:
                with open(args.against, encoding="utf-8") as f:
                    prev = json.load(f)
            except (OSError, ValueError) as e:
                raise ValueError(f"cannot read --against {args.against}: {e}") from None
        lanes, dirs = {}, {}
        for arg in args.lanes:
            label, path = split_lane_arg(arg)
            if not os.path.isdir(path):
                raise ValueError(f"lane folder not found: {path}")
            label = label or lane_label(path)
            if label in lanes:
                raise ValueError(f"two lanes are called '{label}': name them with LABEL=DIR")
            dirs[label] = path
            lanes[label] = judge_lane(path, label, crops, args.min_client_share, args.aim_tolerance)
    except ValueError as e:
        print(f"eyes_judge: {e}", file=sys.stderr)
        return 2

    doc = {"tool": "eyes_judge", "version": 1,
           "gate": {"min_client_share": args.min_client_share, "aim_tolerance": args.aim_tolerance},
           "lanes": lanes}
    if args.save:
        with open(args.save, "w", encoding="utf-8") as f:
            json.dump(doc, f, indent=1)
            f.write("\n")
    if args.sheet_dir:
        os.makedirs(args.sheet_dir, exist_ok=True)
        for label, lane in lanes.items():
            sheet, _ = build_sheet(dirs[label], label, lane)
            sheet.save(os.path.join(args.sheet_dir, f"{_safe(label)}-sheet.png"))
        if args.pair:
            (la, a), (lb, b) = lanes.items()
            build_pair_sheet(dirs[la], a, dirs[lb], b).save(
                os.path.join(args.sheet_dir, f"{_safe(la)}-vs-{_safe(lb)}.png"))

    out = ["## Eyes judge", ""]
    for label, lane in lanes.items():
        out.append(f"- {label}: build {lane['build'] or '?'} ({lane['built_at'] or 'time unknown'})")
    out += [""] + table(lanes)
    for label, lane in lanes.items():
        if lane["not_judged"]:
            out += ["", f"{label}: not judged (first-person stills log no proof lines): {', '.join(lane['not_judged'])}"]
    if args.pair:
        (la, a), (lb, b) = lanes.items()
        out += [""] + pair_table(a, b, pair_rows(a, b))
    if prev is not None:
        out += ["", f"### Change since {os.path.basename(args.against)}", ""]
        for label, lane in lanes.items():
            before = prev.get("lanes", {}).get(label)
            if before:
                out.append(f"- {label}: build {before.get('build') or '?'} -> {lane['build'] or '?'}")
        out += [""] + delta_table(compare(prev, doc))
    print("\n".join(out))

    failed = 0
    total = 0
    for label, lane in lanes.items():
        for problem in lane["problems"]:
            print(f"FAIL {label}: {problem}", file=sys.stderr)
        for key, st in lane["stills"].items():
            total += 1
            if not st["proof"]["ok"]:
                failed += 1
                for problem in st["proof"]["problems"]:
                    print(f"FAIL {key} ({label}): {problem}", file=sys.stderr)
    if failed or any(lane["problems"] for lane in lanes.values()):
        print(f"eyes_judge: the proof gate FAILED for {failed} of {total} RTS stills"
              f"{' and a lane problem' if any(lane['problems'] for lane in lanes.values()) else ''} - "
              "no critique of a still without proof, and no colour claim without its number; fix the run first.",
              file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
