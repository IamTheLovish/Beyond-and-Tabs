#!/usr/bin/env python3
"""Block-pack metric gates G1-G6 (round 27, Oct 22 2026; the gates were frozen by the commander before the round).

Written BEFORE the round's texture changes (tests first): it measures eyes stills (matched crops of the terrain sampler
close-ups, tools/gen_material_sampler.py) and the generated PNGs, and prints a PASS/FAIL table.

  G1 noise      plain-lane 4-neighbour Laplacian std of the luma on each matched crop <= main's, per material
  G2 structure  mid-band energy (std of a difference of Gaussians, sigma 2 px minus sigma 6 px, on the crop) >= 0.9 x
                round 26's, per material - so a texture cannot pass G1 by being smoothed
  G3 range      1st and 99th percentile luma (0-255) of each albedo PNG within 55..220 (alpha > 0 texels)
  G4 wallpaper  each top / side albedo laid 4 x 4 the way the GAME lays it (a random pick per block from the
                blockstate's models and turns - see `face_variants`), box-downscaled to ~8 px per block (the RTS pixel
                size), mean removed; the normalized circular autocorrelation at a lag of one block (max of x and y),
                averaged over 64 seeded layouts, must be <= 0.5. (Tiling ONE texture 4 x 4 with no turns is periodic,
                so its autocorrelation at one tile is 1.0 for every texture - that reading is printed as G4plain for
                reference only.)
  G5 grass      the top (biome-tinted, PLAINS_TINT) and the side (dirt + the tinted overlay) mean luma differ by
                >= 0.08; on the side the green (turf) rows end within the top 25% of the height - no green below
  G6 PBR        `_s`: alpha 255 everywhere (no glow), blue (porosity / SSS) < 65, red (smoothness) std <= 8/255

Usage:
  python3 tools/texture_gates.py --set main=STILLS:PACK --set r26=STILLS:PACK --set r27=STILLS:PACK --gate r27
STILLS holds plain/ (and optionally shaders/) with the bat_eyes_15_terrain_sampler_near_*.png stills; PACK is a
generated resourcepacks/BeyondAndTabs-Realistic folder. Either part may be empty ("main=STILLS:" or "=:PACK").
G1's reference is the set named main, G2's the set named r26. --crops DIR saves each crop for a look.
"""
import argparse
import json
import os
import sys

import numpy as np
from PIL import Image

STILL = "bat_eyes_15_terrain_sampler_near_%s.png"
# matched crops on the 1600 x 900 close-up stills (the camera is fixed by the .near points, so the boxes match run to
# run): each box lies inside one material's face, clear of its edges and of other blocks
CROPS = {
    "stone_floor": ("floor_stone", (672, 464, 928, 664)),
    "stone_wall": ("wall_stone", (600, 240, 860, 560)),
    "dirt": ("floor_dirt", (700, 520, 900, 640)),
    "grass_top": ("floor_grass_block", (560, 528, 1040, 768)),
    "grass_side": ("hillside", (450, 285, 760, 365)),
    "basalt": ("wall_basalt", (650, 230, 880, 540)),
}
PLAINS_TINT = (0.57, 0.74, 0.35)      # tools/gen_blocks.py's plains-like biome colour
RTS_PX = 8                            # px per block at the RTS camera for G4
LAYOUTS = 64

# the faces G3 / G4 / G6 look at: (label, texture, face) - face "top" turns with y, "side" only with x or a mirror
FACES = [
    ("stone_top", "stone", "stone", "top"),
    ("stone_side", "stone", "stone", "side"),
    ("dirt_top", "dirt", "dirt", "top"),
    ("grass_top", "grass_block", "top", "top"),
    ("grass_side", "grass_block", "side", "side"),
    ("basalt_side", "basalt", "side", "side"),
]
# vanilla 1.20.1's blockstates for those blocks (used when the pack ships none): (model, x, y) lists, and the
# models' texture slots
VANILLA_STATES = {
    "stone": [("stone", 0, 0), ("stone_mirrored", 0, 0), ("stone", 0, 180), ("stone_mirrored", 0, 180)],
    "dirt": [("dirt", 0, y) for y in (0, 90, 180, 270)],
    "grass_block": [("grass_block", 0, y) for y in (0, 90, 180, 270)],
    "basalt": [("basalt", 0, 0)],
}
VANILLA_MODELS = {
    "stone": {"stone": "stone"}, "stone_mirrored": {"stone": "stone", "mirror": True},
    "dirt": {"dirt": "dirt"},
    "grass_block": {"top": "grass_block_top", "side": "grass_block_side", "overlay": "grass_block_side_overlay"},
    "basalt": {"side": "basalt_side", "end": "basalt_top"},
}


def luma(rgb):
    return rgb[..., 0] * 0.2126 + rgb[..., 1] * 0.7152 + rgb[..., 2] * 0.0722


def load(path):
    return np.asarray(Image.open(path).convert("RGBA"), dtype=np.float64) / 255.0


def gauss(img, sigma):
    r = int(3 * sigma + 0.5)
    x = np.arange(-r, r + 1)
    k = np.exp(-x * x / (2 * sigma * sigma))
    k /= k.sum()
    p = np.pad(img, r, mode="reflect")
    p = np.apply_along_axis(lambda a: np.convolve(a, k, "valid"), 0, p)
    return np.apply_along_axis(lambda a: np.convolve(a, k, "valid"), 1, p)


def still_metrics(stills, crops_dir=None, label=""):
    """G1 / G2 numbers per material on the plain lane (luma 0-255); None where a still is missing."""
    out = {}
    for mat, (shot, box) in CROPS.items():
        p = os.path.join(stills, "plain", STILL % shot)
        if not os.path.isfile(p):
            out[mat] = None
            continue
        im = Image.open(p).convert("RGB").crop(box)
        if crops_dir:
            os.makedirs(crops_dir, exist_ok=True)
            im.save(os.path.join(crops_dir, "%s_%s.png" % (label, mat)))
        L = luma(np.asarray(im, dtype=np.float64))
        lap = 4 * L[1:-1, 1:-1] - L[:-2, 1:-1] - L[2:, 1:-1] - L[1:-1, :-2] - L[1:-1, 2:]
        dog = gauss(L, 2.0) - gauss(L, 6.0)
        m = 12                                           # drop the blur's border
        out[mat] = dict(lap=float(lap.std()), dog=float(dog[m:-m, m:-m].std()), mean=float(L.mean()))
    return out


# ------------------------------------------------------------------ the pack's faces ---------------------------
def block_dir(pack):
    return os.path.join(pack, "assets", "minecraft")


def read_json(path):
    with open(path) as f:
        return json.load(f)


def model_textures(pack, model):
    """Texture slots of a block model: the pack's own model file (following its parent) or vanilla's table."""
    name = model.split("/")[-1]
    p = os.path.join(block_dir(pack), "models", "block", name + ".json")
    if os.path.isfile(p):
        m = read_json(p)
        parent = m.get("parent", "").split("/")[-1]
        base = dict(model_textures(pack, parent)) if parent and parent != name else {}
        for k, v in m.get("textures", {}).items():
            base[k] = v.split("/")[-1]
        if "all" in base:
            base.setdefault("stone", base["all"])
            base.setdefault("dirt", base["all"])
        return base
    return dict(VANILLA_MODELS.get(name, {}))


def states(pack, block):
    """(model, x, y) of each model the blockstate picks at random (equal weight) - the pack's file or vanilla's."""
    p = os.path.join(block_dir(pack), "blockstates", block + ".json")
    if not os.path.isfile(p):
        return VANILLA_STATES[block]
    v = read_json(p)["variants"]
    key = {"grass_block": "snowy=false", "basalt": "axis=y"}.get(block, "")
    entries = v[key]
    entries = entries if isinstance(entries, list) else [entries]
    return [(e["model"].split("/")[-1], e.get("x", 0), e.get("y", 0)) for e in entries]


def face_variants(pack, block, slot, face):
    """[(texture name, quarter turns, mirrored)] the face shows, one per blockstate entry. A y turn spins the TOP
    face's texture; a side face stays upright under y and turns only when x turns the model (a quarter-turn count
    that is exact for the share of each orientation, which is all a random layout needs)."""
    out = []
    for model, x, y in states(pack, block):
        tex = model_textures(pack, model)
        name = tex.get(slot) or tex.get("all")
        mirror = bool(tex.get("mirror"))
        if face == "top":
            k = (y // 90 + x // 90) % 4
        else:
            k = (x // 90) % 4
        out.append((name, k, mirror))
    return out


def albedo(pack, name, tinted=False):
    a = load(os.path.join(block_dir(pack), "textures", "block", name + ".png"))
    rgb = a[..., :3]
    if tinted:
        rgb = rgb * np.array(PLAINS_TINT)
    return rgb, a[..., 3]


def grass_side_rgb(pack, side_name):
    """The grass block's side as drawn: the side texture with the tinted overlay over it (same suffix)."""
    rgb, _ = albedo(pack, side_name)
    ov = side_name.replace("grass_block_side", "grass_block_side_overlay")
    if os.path.isfile(os.path.join(block_dir(pack), "textures", "block", ov + ".png")):
        orgb, oa = albedo(pack, ov, tinted=True)
        rgb = rgb * (1 - oa[..., None]) + orgb * oa[..., None]
    return rgb


def face_rgb(pack, label, name):
    if label == "grass_side":
        return grass_side_rgb(pack, name)
    return albedo(pack, name, tinted=(label == "grass_top"))[0]


def orient(img, k, mirror):
    if mirror:
        img = img[:, ::-1]
    return np.rot90(img, k)


def downscale(img, px):
    n = img.shape[0]
    f = n // px
    return img[:px * f, :px * f].reshape(px, f, px, f).mean(axis=(1, 3))


def autocorr_at_block(field, px):
    f = field - field.mean()
    F = np.fft.fft2(f)
    ac = np.real(np.fft.ifft2(F * np.conj(F)))
    if ac[0, 0] <= 1e-12:
        return 0.0
    return float(max(ac[0, px], ac[px, 0]) / ac[0, 0])


def g4(pack, label, block, slot, face):
    vs = face_variants(pack, block, slot, face)
    small = {}
    for name, _k, _m in vs:
        if name not in small:
            small[name] = downscale(luma(face_rgb(pack, label, name)), RTS_PX)
    rng = np.random.default_rng(27)
    vals = []
    for _ in range(LAYOUTS):
        rows = []
        for _r in range(4):
            row = []
            for _c in range(4):
                name, k, m = vs[rng.integers(len(vs))]
                row.append(orient(small[name], k, m))
            rows.append(np.concatenate(row, 1))
        vals.append(autocorr_at_block(np.concatenate(rows, 0), RTS_PX))
    plain = np.tile(small[vs[0][0]], (4, 4))
    return float(np.mean(vals)), autocorr_at_block(plain, RTS_PX), sorted(set(v[0] for v in vs))


def pack_metrics(pack):
    out = {}
    for label, block, slot, face in FACES:
        g4v, g4p, names = g4(pack, label, block, slot, face)
        rng_lo, rng_hi = 255.0, 0.0
        for nm in names:
            rgb, a = albedo(pack, nm)
            L = luma(rgb)[a > 0.02] * 255
            rng_lo, rng_hi = min(rng_lo, np.percentile(L, 1)), max(rng_hi, np.percentile(L, 99))
        out[label] = dict(g4=g4v, g4plain=g4p, p1=float(rng_lo), p99=float(rng_hi), textures=names)
    # G3 also covers the overlay (its visible texels) and basalt's end
    extra = []
    for _n, nm in [(None, x) for x in out["grass_side"]["textures"]]:
        extra.append(nm.replace("grass_block_side", "grass_block_side_overlay"))
    extra.append("basalt_top")
    for nm in extra:
        p = os.path.join(block_dir(pack), "textures", "block", nm + ".png")
        if os.path.isfile(p):
            rgb, a = albedo(pack, nm)
            L = luma(rgb)[a > 0.02] * 255
            out.setdefault("_g3extra", {})[nm] = (float(np.percentile(L, 1)), float(np.percentile(L, 99)))
    # G5
    tops = out["grass_top"]["textures"]
    sides = out["grass_side"]["textures"]
    top_l = np.mean([luma(albedo(pack, t, tinted=True)[0]).mean() for t in tops])
    side_l = np.mean([luma(grass_side_rgb(pack, s)).mean() for s in sides])
    band = 0.0
    for s in sides:
        rgb = grass_side_rgb(pack, s)
        r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
        mx, mn = rgb.max(-1), rgb.min(-1)
        sat = (mx - mn) / np.maximum(mx, 1e-6)
        green = (g > r * 1.08) & (g > b * 1.2) & (sat > 0.12)
        rows = np.nonzero(green.mean(1) >= 0.01)[0]
        band = max(band, (rows.max() + 1) / rgb.shape[0] if rows.size else 0.0)
    out["_g5"] = dict(top=float(top_l), side=float(side_l), band=float(band))
    # G6: every _s of the faces' textures, the overlays and basalt's end
    names = set(extra)
    for label in ("stone_top", "dirt_top", "grass_top", "grass_side", "basalt_side"):
        names |= set(out[label]["textures"])
    worst = dict(alpha_min=255, blue_max=0, red_std=0.0, worst_red="", worst_blue="")
    for nm in sorted(names):
        p = os.path.join(block_dir(pack), "textures", "block", nm + "_s.png")
        if not os.path.isfile(p):
            continue
        s = np.asarray(Image.open(p).convert("RGBA"), dtype=np.float64)
        a = np.asarray(Image.open(os.path.join(block_dir(pack), "textures", "block", nm + ".png")).convert("RGBA"))[..., 3]
        vis = a > 5
        worst["alpha_min"] = min(worst["alpha_min"], int(s[..., 3].min()))
        if s[..., 2][vis].max() > worst["blue_max"]:
            worst["blue_max"], worst["worst_blue"] = int(s[..., 2][vis].max()), nm
        if s[..., 0][vis].std() > worst["red_std"]:
            worst["red_std"], worst["worst_red"] = float(s[..., 0][vis].std()), nm
    out["_g6"] = worst
    return out


def fmt(v, d=2):
    return "-" if v is None else ("%.*f" % (d, v))


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--set", action="append", default=[], help="label=STILLS:PACK")
    ap.add_argument("--gate", help="the set the PASS/FAIL column judges")
    ap.add_argument("--crops", help="save the crops here")
    a = ap.parse_args()
    sets = {}
    for s in a.set:
        label, rest = s.split("=", 1)
        stills, _, pack = rest.partition(":")
        sets[label] = dict(still=still_metrics(stills, a.crops, label) if stills else None,
                           pack=pack_metrics(pack) if pack else None)
    labels = list(sets)
    ok_all = True
    rows = []

    def verdict(ok):
        nonlocal ok_all
        if ok is None:
            return "n/a"
        ok_all = ok_all and ok
        return "PASS" if ok else "FAIL"

    g = sets.get(a.gate) if a.gate else None
    print("| gate | item | " + " | ".join(labels) + " | limit | %s |" % (a.gate or "-"))
    print("|---|---|" + "---|" * len(labels) + "---|---|")
    for mat in CROPS:
        vals = [(sets[l]["still"] or {}).get(mat) for l in labels]
        ref = (sets.get("main", {}).get("still") or {}).get(mat)
        mine = ((g or {}).get("still") or {}).get(mat)
        ok = None if (ref is None or mine is None) else mine["lap"] <= ref["lap"]
        rows.append(("G1 noise", mat, [fmt(v and v["lap"]) for v in vals],
                     "<= %s" % fmt(ref and ref["lap"]), verdict(ok) if g else ""))
    for mat in CROPS:
        vals = [(sets[l]["still"] or {}).get(mat) for l in labels]
        ref = (sets.get("r26", {}).get("still") or {}).get(mat)
        mine = ((g or {}).get("still") or {}).get(mat)
        ok = None if (ref is None or mine is None) else mine["dog"] >= 0.9 * ref["dog"]
        rows.append(("G2 structure", mat, [fmt(v and v["dog"]) for v in vals],
                     ">= %s" % fmt(ref and 0.9 * ref["dog"]), verdict(ok) if g else ""))
    for label, _b, _s, _f in FACES:
        vals = [(sets[l]["pack"] or {}).get(label) for l in labels]
        mine = ((g or {}).get("pack") or {}).get(label)
        ok = None if mine is None else (mine["p1"] >= 55 and mine["p99"] <= 220)
        rows.append(("G3 range", label, ["%s..%s" % (fmt(v and v["p1"], 0), fmt(v and v["p99"], 0)) for v in vals],
                     "55..220", verdict(ok) if g else ""))
    ex = ((g or {}).get("pack") or {}).get("_g3extra", {})
    for nm in sorted(set(k for l in labels for k in ((sets[l]["pack"] or {}).get("_g3extra") or {}))):
        vals = [((sets[l]["pack"] or {}).get("_g3extra") or {}).get(nm) for l in labels]
        mine = ex.get(nm)
        ok = None if mine is None else (mine[0] >= 55 and mine[1] <= 220)
        rows.append(("G3 range", nm, ["%s..%s" % (fmt(v and v[0], 0), fmt(v and v[1], 0)) for v in vals],
                     "55..220", verdict(ok) if g else ""))
    for label, _b, _s, _f in FACES:
        vals = [(sets[l]["pack"] or {}).get(label) for l in labels]
        mine = ((g or {}).get("pack") or {}).get(label)
        ok = None if mine is None else mine["g4"] <= 0.5
        rows.append(("G4 wallpaper", label, ["%s (%d tex)" % (fmt(v and v["g4"]), len(v["textures"])) if v else "-"
                                             for v in vals], "<= 0.50", verdict(ok) if g else ""))
    vals = [(sets[l]["pack"] or {}).get("_g5") for l in labels]
    mine = ((g or {}).get("pack") or {}).get("_g5")
    ok = None if mine is None else abs(mine["top"] - mine["side"]) >= 0.08
    rows.append(("G5 grass", "top - side luma", ["%s (%s / %s)" % (fmt(v and v["top"] - v["side"], 3),
                                                                     fmt(v and v["top"], 3), fmt(v and v["side"], 3))
                                                  if v else "-" for v in vals], ">= 0.080", verdict(ok) if g else ""))
    ok = None if mine is None else mine["band"] <= 0.25
    rows.append(("G5 grass", "turf band (no green below)", [fmt(v and v["band"]) for v in vals], "<= 0.25",
                 verdict(ok) if g else ""))
    vals = [(sets[l]["pack"] or {}).get("_g6") for l in labels]
    mine = ((g or {}).get("pack") or {}).get("_g6")
    for key, lim, test, d in (("alpha_min", "= 255", lambda v: v == 255, 0), ("blue_max", "< 65", lambda v: v < 65, 0),
                              ("red_std", "<= 8", lambda v: v <= 8, 2)):
        ok = None if mine is None else test(mine[key])
        rows.append(("G6 PBR", "_s " + key, [fmt(v and v[key], d) for v in vals], lim, verdict(ok) if g else ""))
    for r in rows:
        print("| %s | %s | %s | %s | %s |" % (r[0], r[1], " | ".join(r[2]), r[3], r[4]))
    if g:
        print("G4plain (one texture tiled, no turns - periodic, always 1.0): " + ", ".join(
            "%s %.2f" % (k, v["g4plain"]) for k, v in (g["pack"] or {}).items() if not k.startswith("_")))
        print("ALL PASS" if ok_all else "SOME GATES FAIL")
    return 0 if (ok_all or not g) else 1


if __name__ == "__main__":
    sys.exit(main())
