#!/usr/bin/env python3
"""Shrinks the CI jar below the device bridge's 30 MB file limit for an install on lovish's PC - the install route in
claude/status.md, step 2, as one checked command instead of a hand-run recipe.

    python3 tools/slim_jar.py IN.jar OUT.jar [--limit-mib 30] [--jobs N] [--keep-oggs]

What changes, and the only things that may (every other entry is copied byte for byte, then checked):
  1. the five big menu / ambience oggs are left out (the game logs a missing sound and plays on) - --keep-oggs keeps them;
  2. every PNG goes through oxipng level 4 and is kept only when its DECODED pixels are identical (lossless);
  3. the MENU art only (title panoramas, map-vote pictures, the splash window; over 100 KB) becomes a 256-colour
     palette PNG with Floyd-Steinberg dithering - unit, block, HUD and icon textures are never touched by this step.
The output keeps the entry order with META-INF/MANIFEST.MF first, is tested entry by entry, compared with the input,
and its size, sha256 and what changed are printed. Exit status 1 when the result is still over the limit.
"""
import argparse
import hashlib
import io
import os
import sys
import zipfile
from concurrent.futures import ProcessPoolExecutor

from PIL import Image

DROP_OGGS = ("main_menu", "soul_resonance_calm", "sharpened_grassblades_calm", "life_scourge_calm", "blood_moon")
MENU_ART = ("/textures/gui/title/background/", "/textures/mapvote/", "/textures/gui/splash/window.png")
MENU_MIN_BYTES = 100_000


def dropped(name, keep_oggs):
    if keep_oggs or not name.endswith(".ogg"):
        return False
    return os.path.splitext(os.path.basename(name))[0] in DROP_OGGS


def is_menu_art(name, size):
    return name.endswith(".png") and size > MENU_MIN_BYTES and any(m in name for m in MENU_ART)


def pixels(data):
    im = Image.open(io.BytesIO(data))
    im.load()
    return im.convert("RGBA").tobytes(), im.size


def slim_png(job):
    """One PNG -> (name, new bytes or None, how). Lossless unless it is menu art."""
    name, data = job
    import oxipng
    best, how = data, None
    try:
        out = oxipng.optimize_from_memory(data, level=4)
        if len(out) < len(best) and pixels(out) == pixels(data):
            best, how = out, "oxipng"
    except Exception as e:                       # a PNG oxipng cannot read stays as it is
        return name, None, f"kept ({type(e).__name__})"
    if is_menu_art(name, len(data)):
        im = Image.open(io.BytesIO(data)).convert("RGBA")
        alpha = im.getchannel("A")
        pal = im.convert("RGB").quantize(colors=256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.FLOYDSTEINBERG)
        if alpha.getextrema() != (255, 255):     # menu art is opaque; anything with alpha stays lossless
            pal = None
        if pal is not None:
            buf = io.BytesIO()
            pal.save(buf, "PNG", optimize=True)
            if buf.tell() < len(best):
                best, how = buf.getvalue(), "palette"
    return name, (best if how else None), how


def main(argv):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("src")
    ap.add_argument("dst")
    ap.add_argument("--limit-mib", type=float, default=30.0, help="the bridge refuses files over 30 MiB")
    ap.add_argument("--jobs", type=int, default=os.cpu_count() or 2)
    ap.add_argument("--keep-oggs", action="store_true")
    a = ap.parse_args(argv)

    zin = zipfile.ZipFile(a.src)
    infos = zin.infolist()
    names = [i.filename for i in infos]
    if "META-INF/MANIFEST.MF" in names:          # the manifest first, as jar readers expect
        names.remove("META-INF/MANIFEST.MF")
        names.insert(0, "META-INF/MANIFEST.MF")
    data = {n: zin.read(n) for n in names}

    jobs = [(n, data[n]) for n in names if n.endswith(".png")]
    changed, hows = {}, {}
    with ProcessPoolExecutor(max_workers=a.jobs) as pool:
        for name, new, how in pool.map(slim_png, jobs, chunksize=8):
            if new is not None:
                changed[name] = new
            hows[how] = hows.get(how, 0) + 1

    left_out = [n for n in names if dropped(n, a.keep_oggs)]
    with zipfile.ZipFile(a.dst, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as zout:
        for n in names:
            if n in left_out:
                continue
            zi = zipfile.ZipInfo(n, date_time=zin.getinfo(n).date_time)
            zi.compress_type = zipfile.ZIP_STORED if n.endswith("/") else zipfile.ZIP_DEFLATED
            zi.external_attr = zin.getinfo(n).external_attr
            zout.writestr(zi, changed.get(n, data[n]))

    # check: every entry readable; unchanged entries byte-identical; lossless PNGs pixel-identical
    zchk = zipfile.ZipFile(a.dst)
    bad = zchk.testzip()
    if bad:
        print(f"CORRUPT entry {bad}")
        return 1
    out_names = zchk.namelist()
    assert out_names[0] == names[0], "manifest is not first"
    assert set(out_names) == set(names) - set(left_out), "entry list differs"
    palette = 0
    for n in out_names:
        b = zchk.read(n)
        if n not in changed:
            assert b == data[n], f"{n} changed"
        elif is_menu_art(n, len(data[n])) and pixels(b) != pixels(data[n]):
            palette += 1                          # the only lossy step, menu art only
        else:
            assert pixels(b) == pixels(data[n]), f"{n} pixels differ"
    size = os.path.getsize(a.dst)
    sha = hashlib.sha256(open(a.dst, "rb").read()).hexdigest()
    print(f"{a.src}: {os.path.getsize(a.src) / 2**20:.1f} MiB -> {a.dst}: {size / 2**20:.2f} MiB (limit {a.limit_mib} MiB)")
    print(f"  left out: {', '.join(os.path.basename(n) for n in left_out) or 'nothing'}")
    print(f"  PNGs: {len(jobs)}; " + ", ".join(f"{k or 'unchanged'} {v}" for k, v in sorted(hows.items(), key=str)))
    print(f"  palettised menu images: {palette}; every other entry byte- or pixel-identical")
    print(f"  sha256 {sha}")
    return 0 if size <= a.limit_mib * 2**20 else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
