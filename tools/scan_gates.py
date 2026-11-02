#!/usr/bin/env python3
"""Gates for the photo-scan round of the block pack (lovish Oct 22 20:23: "yes switch them and keep improving";
written BEFORE any scan texture - the round's test, AGENTS rule of Oct 22 19:58). Builds on tools/texture_gates.py
(round 27's G1-G6, reused as they are) and adds what a scan needs:

  S1 noise      plain-lane Laplacian std of each matched close-up crop <= main's        (texture_gates G1, per material)
  S2 structure  plain-lane mid-band (DoG sigma 2 - 6 px) >= 0.9 x main's - less noise must not be blur (G2, ref main)
  S3 range      albedo 1st..99th percentile luma within 55..220                         (G3)
  S4 wallpaper  autocorrelation at one block over random game layouts <= 0.5            (G4)
  S5 grass      top - side luma >= 0.08; no green below the top 25% of the side          (G5)
  S6 PBR        _s alpha 255, porosity < 65, smoothness std <= 8                         (G6)
  S7 seams      8 x 8 blocks laid the way the game lays them (each block a random pick of the blockstate's models and
                turns, texture_gates.face_variants): the mean luma step ACROSS block borders / the mean step between
                neighbouring texels inside the blocks, <= 1.25 - at full size (the close-up) and box-downscaled to 8 px
                per block (the RTS camera). A visible grid is a step at the border the inside of a block does not have.
  S8 blades     the grass side's turf (overlay texels, alpha > 0.5): mean squared luma gradient across u / along v
                >= 1.5 - blades and their tips run UP and DOWN the side, never sideways (lovish's direction: grass on
                block sides must not look like it grows sideways). A top-down grass photo on the side scores ~1.0.

Also reports S1/S2 for the three shipped materials (oak planks, gravel, oak bark - PR "ship") and the shader lane's
numbers (not gated). Usage:
  python3 tools/scan_gates.py --set main=STILLS:PACK --set new=STILLS:PACK --gate new
STILLS holds plain/ (and shaders/) with the bat_eyes_15_terrain_sampler_near_*.png stills; PACK a generated
resourcepacks/BeyondAndTabs-Realistic folder; either part may be empty.
"""
import argparse
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import texture_gates as tg  # noqa: E402

# texture_gates' boxes plus the shipped wood and gravel (boxes inside one face on the 1600 x 900 close-ups)
CROPS = dict(tg.CROPS)
CROPS.update({
    "oak_planks": ("wall_oak_planks", (600, 280, 880, 620)),
    "gravel": ("floor_gravel", (680, 460, 920, 660)),
    "oak_bark": ("oak_log", (660, 80, 840, 600)),
})
SCAN = ("stone_floor", "stone_wall", "dirt", "grass_top", "grass_side", "basalt")
LAYOUT = 8          # blocks per side for S7
SEAM_MAX = 1.25
BLADE_MIN = 1.5


def stills(root, lane):
    """S1 / S2 numbers on one lane (plain / shaders), every crop in CROPS - texture_gates.still_metrics' measures."""
    out = {}
    for mat, (shot, box) in CROPS.items():
        p = os.path.join(root, lane, tg.STILL % shot)
        if not os.path.isfile(p):
            out[mat] = None
            continue
        L = tg.luma(np.asarray(Image.open(p).convert("RGB").crop(box), dtype=np.float64))
        lap = 4 * L[1:-1, 1:-1] - L[:-2, 1:-1] - L[2:, 1:-1] - L[1:-1, :-2] - L[1:-1, 2:]
        dog = tg.gauss(L, 2.0) - tg.gauss(L, 6.0)
        m = 12
        out[mat] = dict(lap=float(lap.std()), dog=float(dog[m:-m, m:-m].std()), mean=float(L.mean()))
    return out


def seam_ratio(field, n, blocks):
    """mean |step| across block borders / mean |step| inside blocks, both axes."""
    dx = np.abs(np.diff(field, axis=1))
    dy = np.abs(np.diff(field, axis=0))
    bx = np.zeros(dx.shape[1], bool)
    bx[n - 1::n] = True
    by = np.zeros(dy.shape[0], bool)
    by[n - 1::n] = True
    border = np.concatenate([dx[:, bx].ravel(), dy[by, :].ravel()])
    inside = np.concatenate([dx[:, ~bx].ravel(), dy[~by, :].ravel()])
    return float(border.mean() / max(inside.mean(), 1e-9))


def layout(pack, label, block, slot, face, seed):
    vs = tg.face_variants(pack, block, slot, face)
    tex = {}
    for name, _k, _m in vs:
        if name not in tex:
            tex[name] = tg.luma(tg.face_rgb(pack, label, name))
    rng = np.random.default_rng(seed)
    rows = []
    for _r in range(LAYOUT):
        row = []
        for _c in range(LAYOUT):
            name, k, m = vs[rng.integers(len(vs))]
            row.append(tg.orient(tex[name], k, m))
        rows.append(np.concatenate(row, 1))
    return np.concatenate(rows, 0), tex[vs[0][0]].shape[0]


def s7(pack):
    out = {}
    for label, block, slot, face in tg.FACES:
        full, rts = [], []
        for seed in range(8):
            f, n = layout(pack, label, block, slot, face, seed)
            if face == "side":
                # a wall of side faces: neighbours left and right, and (dirt, stone, basalt) the same block above and
                # below; the grass side has a dirt block under it - the soil must run on into the dirt's side
                if label == "grass_side":
                    dirt = tg.luma(tg.albedo(pack, "dirt")[0])
                    f = np.concatenate([f[:n], np.tile(dirt, (1, LAYOUT))], 0)
            full.append(seam_ratio(f, n, LAYOUT))
            px = tg.RTS_PX
            h, w = f.shape
            sm = f.reshape(h // n, n, w // n, n)
            sm = sm.reshape(h // n, px, n // px, w // n, px, n // px).mean(axis=(2, 5)).reshape(h // n * px, w // n * px)
            rts.append(seam_ratio(sm, px, LAYOUT))
        out[label] = dict(full=float(np.mean(full)), rts=float(np.mean(rts)))
    return out


def s8(pack):
    """Blade direction on the grass side as drawn (side + tinted overlay): E[gx^2] / E[gy^2] over the turf band -
    every row from the top down to the last row with turf (overlay alpha > 0.5 on >= 1% of it), so the ragged lower
    edge counts too. Strands that hang or stand vertically put their edges across u (gx); sideways blades and a
    straight cut edge put them along v (gy)."""
    names = [v[0] for v in tg.face_variants(pack, "grass_block", "side", "side")]
    vals = []
    for nm in sorted(set(names)):
        ov = nm.replace("grass_block_side", "grass_block_side_overlay")
        p = os.path.join(tg.block_dir(pack), "textures", "block", ov + ".png")
        if not os.path.isfile(p):
            continue
        al = tg.load(p)[..., 3]
        rows = np.nonzero((al > 0.5).mean(1) >= 0.01)[0]
        if not rows.size:
            continue
        L = tg.luma(tg.grass_side_rgb(pack, nm))[:rows.max() + 1]
        gx = np.diff(L, axis=1)
        gy = np.diff(L, axis=0)
        vals.append(float((gx ** 2).mean() / max((gy ** 2).mean(), 1e-12)))
    return min(vals) if vals else None


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--set", action="append", default=[], help="label=STILLS:PACK")
    ap.add_argument("--gate", help="the set the PASS/FAIL column judges (S1/S2 against the set named main)")
    a = ap.parse_args()
    sets = {}
    for s in a.set:
        label, rest = s.split("=", 1)
        st, _, pack = rest.partition(":")
        sets[label] = dict(plain=stills(st, "plain") if st else None,
                           shaders=stills(st, "shaders") if st and os.path.isdir(os.path.join(st, "shaders")) else None,
                           pack=tg.pack_metrics(pack) if pack else None,
                           s7=s7(pack) if pack else None, s8=s8(pack) if pack else None)
    labels = list(sets)
    g = sets.get(a.gate)
    ok_all = True
    print("| gate | item | " + " | ".join(labels) + " | limit | %s |" % (a.gate or "-"))
    print("|---|---|" + "---|" * len(labels) + "---|---|")

    def row(gate, item, vals, limit, ok):
        nonlocal ok_all
        v = "" if ok is None or not g else ("PASS" if ok else "FAIL")
        if ok is False and g:
            ok_all = False
        print("| %s | %s | %s | %s | %s |" % (gate, item, " | ".join(vals), limit, v))

    ref = (sets.get("main") or {}).get("plain") or {}
    for key, gate, lim in (("lap", "S1 noise", lambda r: r), ("dog", "S2 structure", lambda r: 0.9 * r)):
        for mat in CROPS:
            vals = [((sets[l]["plain"] or {}).get(mat) or {}).get(key) for l in labels]
            r = (ref.get(mat) or {}).get(key)
            mine = (((g or {}).get("plain") or {}).get(mat) or {}).get(key)
            ok = None if r is None or mine is None else (mine <= lim(r) if key == "lap" else mine >= lim(r))
            shipped = "" if mat in SCAN else " (ship)"
            row(gate, mat + shipped, [tg.fmt(v) for v in vals], ("<= " if key == "lap" else ">= ") + tg.fmt(r and lim(r)),
                ok)
    for key, name in (("lap", "shaders noise (info)"), ("dog", "shaders structure (info)")):
        for mat in CROPS:
            vals = [((sets[l]["shaders"] or {}).get(mat) or {}).get(key) for l in labels]
            if any(v is not None for v in vals):
                row(name, mat, [tg.fmt(v) for v in vals], "-", None)
    for label, _b, _s, _f in tg.FACES:
        vals = [(sets[l]["pack"] or {}).get(label) for l in labels]
        mine = ((g or {}).get("pack") or {}).get(label)
        row("S3 range", label, ["%s..%s" % (tg.fmt(v and v["p1"], 0), tg.fmt(v and v["p99"], 0)) for v in vals],
            "55..220", None if mine is None else (mine["p1"] >= 55 and mine["p99"] <= 220))
    for label, _b, _s, _f in tg.FACES:
        vals = [(sets[l]["pack"] or {}).get(label) for l in labels]
        mine = ((g or {}).get("pack") or {}).get(label)
        row("S4 wallpaper", label, [tg.fmt(v and v["g4"]) for v in vals], "<= 0.50",
            None if mine is None else mine["g4"] <= 0.5)
    vals = [(sets[l]["pack"] or {}).get("_g5") for l in labels]
    mine = ((g or {}).get("pack") or {}).get("_g5")
    row("S5 grass", "top - side luma", [tg.fmt(v and v["top"] - v["side"], 3) for v in vals], ">= 0.080",
        None if mine is None else abs(mine["top"] - mine["side"]) >= 0.08)
    row("S5 grass", "turf band", [tg.fmt(v and v["band"]) for v in vals], "<= 0.25",
        None if mine is None else mine["band"] <= 0.25)
    vals = [(sets[l]["pack"] or {}).get("_g6") for l in labels]
    mine = ((g or {}).get("pack") or {}).get("_g6")
    for key, lim, test, d in (("alpha_min", "= 255", lambda v: v == 255, 0), ("blue_max", "< 65", lambda v: v < 65, 0),
                              ("red_std", "<= 8", lambda v: v <= 8, 2)):
        row("S6 PBR", "_s " + key, [tg.fmt(v and v[key], d) for v in vals], lim, None if mine is None else test(mine[key]))
    for label, _b, _s, _f in tg.FACES:
        for scale in ("full", "rts"):
            vals = [((sets[l]["s7"] or {}).get(label) or {}).get(scale) for l in labels]
            mine = (((g or {}).get("s7") or {}).get(label) or {}).get(scale)
            row("S7 seams", "%s %s" % (label, scale), [tg.fmt(v) for v in vals], "<= %.2f" % SEAM_MAX,
                None if mine is None else mine <= SEAM_MAX)
    vals = [sets[l]["s8"] for l in labels]
    mine = (g or {}).get("s8")
    row("S8 blades", "grass side u/v gradient", [tg.fmt(v) for v in vals], ">= %.1f" % BLADE_MIN,
        None if mine is None else mine >= BLADE_MIN)
    if g:
        print("ALL PASS" if ok_all else "SOME GATES FAIL")
    return 0 if (ok_all or not g) else 1


if __name__ == "__main__":
    sys.exit(main())
