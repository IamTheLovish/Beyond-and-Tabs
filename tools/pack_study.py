#!/usr/bin/env python3
"""Numbers-only study of a PBR resource pack (and of our own unit textures, for comparison).

Why (lovish, Oct 15 04:13; claude/realistic-look.md): our realistic look is OUR OWN pack - original textures from
tools/materials.py, 128x + LabPBR maps - calibrated against how a mature realistic pack is built. The free Patrix 32x
(Modrinth, All Rights Reserved) is the reference. Only STATISTICS leave this script: per material group, the albedo's
value and saturation, how much detail sits at each scale (as a fraction of a block, so 32x and 128x compare), how
strong the normal maps are, the height and AO channels, and the LabPBR specular ranges (smoothness, F0 / metals,
porosity / subsurface, emission). No pixel, crop, thumbnail or image of the studied pack is written anywhere.

Usage:
  python3 -I tools/pack_study.py PACK_DIR OUT_DIR [--label NAME] [--ours ENTITY_TEXTURE_DIR --ours-tpb 128]
PACK_DIR is an unpacked resource pack (assets/minecraft/textures/block/...). --ours studies our own unit atlases
(<name>.png with <name>_n.png / <name>_s.png beside it) the same way, so the two sides of every number line up.
Writes OUT_DIR/study.json and OUT_DIR/study.md.
"""
import argparse
import json
import math
import os
import re
import sys

import numpy as np
from PIL import Image

# block texture name -> material group (first match wins). Vanilla names, so nothing here is the pack's content.
GROUPS = (
    # single blocks first (first match wins): the round-3 targets of tools/gen_blocks.py - the blocks that cover the
    # most of the RTS view after grass and water (the eyes ground census: the capitol's polished andesite steps,
    # stone bricks, spruce slabs and stairs, smooth stone)
    ("polished_andesite", r"^polished_andesite$"),
    ("smooth_stone", r"^smooth_stone(_slab_side)?$"),
    ("spruce_planks", r"^spruce_planks$"),
    ("stone_bricks_only", r"^stone_bricks$"),
    # the capitol's and the map's remaining vanilla-looking blocks (eyes stills, Oct 15): the metal resource patches
    # (raw iron), the capitol's gold, its barrels and hay
    ("raw_iron_block", r"^raw_iron_block$"),
    ("gold_block", r"^gold_block$"),
    ("barrel", r"^barrel_(side|top|bottom)$"),
    ("hay_block", r"^hay_block_(side|top)$"),
    # each wood's bark and end grain (the trees in the RTS view and the first-person still)
    ("spruce_log", r"^spruce_log$"), ("spruce_log_top", r"^spruce_log_top$"),
    ("birch_log", r"^birch_log$"), ("birch_log_top", r"^birch_log_top$"),
    ("acacia_log", r"^acacia_log$"), ("acacia_log_top", r"^acacia_log_top$"),
    ("jungle_log", r"^jungle_log$"), ("jungle_log_top", r"^jungle_log_top$"),
    ("dark_oak_log", r"^dark_oak_log$"), ("dark_oak_log_top", r"^dark_oak_log_top$"),
    ("oak_log_only", r"^oak_log$"), ("oak_log_top_only", r"^oak_log_top$"),
    # round 11: the other factions' building blocks (census of all 73 faction structures, Oct 15: polished blackstone
    # ~9%, azalea leaves ~7%, deepslate ~6%, cut copper ~6%, netherrack + nether bricks ~4%, prismarine ~3%, moss)
    ("polished_blackstone", r"^polished_blackstone$"),
    ("polished_blackstone_bricks", r"^polished_blackstone_bricks$"),
    ("deepslate_bricks", r"^deepslate_bricks$"),
    ("deepslate_tiles", r"^deepslate_tiles$"),
    ("deepslate", r"^deepslate$"),
    ("deepslate_top", r"^deepslate_top$"),
    ("polished_deepslate", r"^polished_deepslate$"),
    ("cobbled_deepslate", r"^cobbled_deepslate$"),
    ("cut_copper", r"^cut_copper$"),
    ("oxidized_cut_copper", r"^oxidized_cut_copper$"),
    ("azalea_leaves", r"^azalea_leaves$"),
    ("flowering_azalea_leaves", r"^flowering_azalea_leaves$"),
    ("netherrack", r"^netherrack$"),
    ("nether_bricks", r"^nether_bricks$"),
    ("prismarine_bricks", r"^prismarine_bricks$"),
    ("dark_prismarine", r"^dark_prismarine$"),
    ("moss_block", r"^moss_block$"),
    ("mossy_stone_bricks", r"^mossy_stone_bricks$"),
    ("grass_top", r"^grass_block_top$"),
    ("grass_side", r"^grass_block_side(_overlay)?$"),
    ("dirt", r"^(dirt|coarse_dirt|rooted_dirt|podzol_side|mud|packed_mud|dirt_path_side)$"),
    ("gravel", r"^gravel$"),
    ("sand", r"^(red_)?sand$"),
    ("sandstone", r"sandstone"),
    ("cobblestone", r"cobble"),
    ("stone_bricks", r"(stone_bricks|deepslate_bricks|deepslate_tiles|polished_)"),
    ("bricks", r"^(bricks|mud_bricks|nether_bricks)$"),
    ("stone", r"^(stone|smooth_stone|andesite|diorite|granite|deepslate|tuff|calcite)$"),
    ("ore", r"_ore$"),
    ("log_side", r"_(log|stem)$"),
    ("log_top", r"_(log|stem)_top$"),
    ("planks", r"_planks$"),
    ("leaves", r"_leaves$"),
    ("metal_block", r"^(iron|gold|copper|exposed_copper|weathered_copper|oxidized_copper|netherite)_block$"),
    ("wool", r"_wool$"),
    ("glass", r"glass"),
    ("water", r"^water_(still|flow)$"),
    ("lava", r"^lava_(still|flow)$"),
)
BLOCK_DIR = os.path.join("assets", "minecraft", "textures", "block")
# detail scales, as a fraction of one block's width: 1/64 block is a single texel at 64x, fine grain; 1/4 block is
# a stone-sized blotch. Band k = what a blur of SCALES[k] removes that SCALES[k+1] does not.
SCALES = (1 / 128, 1 / 64, 1 / 32, 1 / 16, 1 / 8, 1 / 4)


def group_of(name):
    for g, rx in GROUPS:
        if re.search(rx, name):
            return g
    return None


MIP = 0   # --mip: measure at this mipmap level (what the RTS camera samples), set in main()


def mip_down(a, levels, is_normal=False):
    """Box-filtered 2x2 reductions, as the game's mipmaps: colour channels averaged; a normal map's xy averaged and
    renormalised (so the tilt shrinks the way a GPU's averaged normals do)."""
    for _ in range(levels):
        h, w = a.shape[:2]
        if h < 2 or w < 2:
            break
        a = a[:h - h % 2, :w - w % 2]
        a = (a[0::2, 0::2] + a[1::2, 0::2] + a[0::2, 1::2] + a[1::2, 1::2]) / 4.0
        if is_normal:
            x, y = a[..., 0] * 2 - 1, a[..., 1] * 2 - 1
            ln = np.sqrt(np.maximum(x * x + y * y + np.maximum(0, 1 - x * x - y * y), 1e-9))
            a = a.copy()
            a[..., 0], a[..., 1] = (x / ln) * 0.5 + 0.5, (y / ln) * 0.5 + 0.5
    return a


def load(path, is_normal=False):
    im = Image.open(path)
    im.load()
    a = np.asarray(im.convert("RGBA"), dtype=np.float64) / 255.0
    h, w = a.shape[:2]
    if h > w and h % w == 0:      # an animated strip: the first frame
        a = a[:w]
    return mip_down(a, MIP, is_normal) if MIP else a


def _blur(img, mask, sigma):
    """Gaussian blur with wrap-around (block textures tile), normalised by the mask so empty atlas areas don't bleed."""
    if sigma <= 0.35:
        return img
    h, w = img.shape
    fy = np.fft.fftfreq(h)[:, None]
    fx = np.fft.fftfreq(w)[None, :]
    g = np.exp(-2 * (math.pi ** 2) * (sigma ** 2) * (fx ** 2 + fy ** 2))
    num = np.real(np.fft.ifft2(np.fft.fft2(img * mask) * g))
    den = np.real(np.fft.ifft2(np.fft.fft2(mask) * g))
    return np.where(den > 1e-3, num / np.maximum(den, 1e-3), 0.0)


def luma(rgb):
    return rgb[..., 0] * 0.2126 + rgb[..., 1] * 0.7152 + rgb[..., 2] * 0.0722


def albedo_stats(a, tpb):
    rgb, mask = a[..., :3], (a[..., 3] > 0.5).astype(np.float64)
    n = mask.sum()
    if n < 16:
        return None
    m = mask > 0
    mx, mn = rgb.max(axis=2), rgb.min(axis=2)
    sat = np.where(mx > 1e-6, (mx - mn) / np.maximum(mx, 1e-6), 0.0)
    L = luma(rgb)
    out = {
        "value_mean": float(mx[m].mean()),
        "luma_mean": float(L[m].mean()),
        "luma_std": float(L[m].std()),
        "luma_p5": float(np.percentile(L[m], 5)),
        "luma_p95": float(np.percentile(L[m], 95)),
        "sat_mean": float(sat[m].mean()),
        "greyscale": bool(sat[m].mean() < 0.04),     # tinted in game (grass top, leaves)
        "alpha_mean": float(a[..., 3].mean()),       # water's translucency, leaves' holes
        "coverage": float(m.mean()),                 # share of texels drawn (alpha > 0.5)
    }
    # detail amplitude per scale band, in luma units: std of (blur(s_k) - blur(s_k+1)) inside the mask
    blurs = [_blur(L, mask, s * tpb) for s in SCALES]
    bands = {}
    prev = L
    for s, b in zip(SCALES, blurs):
        d = (prev - b)[m]
        bands["%.4g" % s] = float(d.std())
        prev = b
    out["detail_by_scale"] = bands     # key = the blur scale in blocks; value = detail finer than it, coarser than the last
    return out


def _mask_for(alb_mask, shape):
    """The albedo's coverage at the map's resolution (a map may be stored smaller than its albedo)."""
    if alb_mask is None:
        return np.ones(shape[:2], bool)
    if alb_mask.shape != shape[:2]:
        im = Image.fromarray((alb_mask > 0.5).astype(np.uint8) * 255)
        alb_mask = np.asarray(im.resize((shape[1], shape[0]), Image.NEAREST), dtype=np.float64) / 255.0
    return alb_mask > 0.5


def normal_stats(n, alb_mask, tpb):
    tpb = tpb * n.shape[1] / alb_mask.shape[1] if alb_mask is not None else tpb   # a smaller map: fewer texels/block
    m = _mask_for(alb_mask, n.shape)
    nx = n[..., 0] * 2 - 1
    ny = n[..., 1] * 2 - 1
    xy = np.clip(nx ** 2 + ny ** 2, 0, 1)
    tilt = np.degrees(np.arccos(np.sqrt(1 - xy)))
    ao = n[..., 2]
    ht = n[..., 3]
    out = {
        "tilt_mean_deg": float(tilt[m].mean()),
        "tilt_p90_deg": float(np.percentile(tilt[m], 90)),
        "ao_mean": float(ao[m].mean()),
        "ao_p5": float(np.percentile(ao[m], 5)),
        "height_mean": float(ht[m].mean()),
        "height_std": float(ht[m].std()),
    }
    # the green convention, measured: the normal leans away from rising height. nx follows -dH/du; if ny follows
    # -dH/dv too, green points along +v (down the texture, what tools/materials.py writes); if it follows +dH/dv,
    # green points along -v (up the texture).
    if out["height_std"] > 0.01:
        # unblurred central differences, away from seams: a blur mixes heights across face / atlas-island edges that
        # the face-by-face normals never saw (that flipped a decimated team map's reading, Oct 15 review)
        H = ht
        step = np.zeros_like(H)
        for ax in (0, 1):
            d = np.abs(H - np.roll(H, 1, ax))
            step = np.maximum(step, np.maximum(d, np.roll(d, -1, ax)))
        seam = step >= 40 / 255

        def corr(p, q, mk):
            p, q = p[mk], q[mk]
            if p.size < 8 or p.std() < 1e-9 or q.std() < 1e-9:
                return None                          # undecided, not a zero reading
            return float(np.corrcoef(p, q)[0, 1])

        # two scales: single-texel differences (our generated maps put their detail there) and ~3% of a block (a
        # painted pack's 8-bit height steps are mostly noise at one texel); keep the clearer reading
        best = (None, None, -1.0)
        for k in sorted({1, max(1, int(round(0.03 * tpb)))}):
            near_seam = seam.copy()
            for sft in range(1, k + 1):
                for ax in (0, 1):
                    near_seam |= np.roll(seam, sft, ax) | np.roll(seam, -sft, ax)
            mk = m & ~near_seam
            du = (np.roll(H, -k, 1) - np.roll(H, k, 1)) / (2 * k)
            dv = (np.roll(H, -k, 0) - np.roll(H, k, 0)) / (2 * k)
            kx, ky = corr(nx, du, mk), corr(ny, dv, mk)
            if kx is not None and ky is not None and min(abs(kx), abs(ky)) > best[2]:
                best = (kx, ky, min(abs(kx), abs(ky)))
        cx, cy = best[0], best[1]
        out["corr_nx_dHdu"] = cx
        out["corr_ny_dHdv"] = cy
        if cx is not None and cy is not None and abs(cx) > 0.15 and abs(cy) > 0.15:
            out["green_along"] = "+v (down the texture)" if (cx < 0) == (cy < 0) else "-v (up the texture)"
    return out


def spec_stats(s, alb_mask):
    m = _mask_for(alb_mask, s.shape)
    R = s[..., 0][m] * 255
    G = np.rint(s[..., 1][m] * 255)
    B = s[..., 2][m] * 255
    A = np.rint(s[..., 3][m] * 255)
    rough = (1 - R / 255) ** 2
    metal = G >= 230      # LabPBR: 230-237 named metals (iron, gold ...), 238-255 a metal whose F0 is its albedo
    out = {
        "smooth_p10": float(np.percentile(R, 10)), "smooth_p50": float(np.percentile(R, 50)),
        "smooth_p90": float(np.percentile(R, 90)), "roughness_mean": float(rough.mean()),
        "metal_frac": float(metal.mean()),
        "f0_dielectric_mean": float(G[~metal].mean()) if (~metal).any() else None,
        "metal_codes": sorted({int(v) if v <= 237 else 238 for v in np.unique(G[metal])}) if metal.any() else [],
        "porosity_mean": float(B[B <= 64].mean()) if (B <= 64).any() else None,
        "sss_frac": float((B >= 65).mean()),
        "emissive_frac": float((A < 255).mean()),
    }
    return out


def study_block_dir(root, tpb_override=None):
    bd = os.path.join(root, BLOCK_DIR)
    if not os.path.isdir(bd):
        raise SystemExit("no %s in %s" % (BLOCK_DIR, root))
    rows = []
    names = sorted(f[:-4] for f in os.listdir(bd) if f.endswith(".png"))
    have = set(names)
    for name in names:
        if name.endswith("_n") or name.endswith("_s"):
            continue
        g = group_of(name)
        if g is None:
            continue
        a = load(os.path.join(bd, name + ".png"))
        tpb = tpb_override or a.shape[1]
        row = {"name": name, "group": g, "texels_per_block": tpb}
        alb = albedo_stats(a, tpb)
        if alb is None:
            continue
        row["albedo"] = alb
        if name + "_n" in have:
            row["normal"] = normal_stats(load(os.path.join(bd, name + "_n.png"), True), a[..., 3], tpb)
        if name + "_s" in have:
            row["specular"] = spec_stats(load(os.path.join(bd, name + "_s.png")), a[..., 3])
        rows.append(row)
    return rows


def study_ours(d, tpb):
    tpb = max(1, tpb >> MIP)       # the atlases are measured at the mip level too
    rows = []
    for f in sorted(os.listdir(d)):
        if not f.endswith(".png") or re.search(r"_(n|s|glow)\.png$", f):
            continue
        base = f[:-4]
        if not (os.path.exists(os.path.join(d, base + "_n.png")) and os.path.exists(os.path.join(d, base + "_s.png"))):
            continue
        a = load(os.path.join(d, f))
        alb = albedo_stats(a, tpb)
        if alb is None:
            continue
        rows.append({"name": base, "group": "ours:" + base, "texels_per_block": tpb, "albedo": alb,
                     "normal": normal_stats(load(os.path.join(d, base + "_n.png"), True), a[..., 3], tpb),
                     "specular": spec_stats(load(os.path.join(d, base + "_s.png")), a[..., 3])})
    return rows


def _mean(vals):
    vals = [v for v in vals if isinstance(v, (int, float)) and not isinstance(v, bool)]
    return float(np.mean(vals)) if vals else None


def aggregate(rows):
    groups = {}
    for r in rows:
        groups.setdefault(r["group"], []).append(r)
    out = {}
    for g, rs in groups.items():
        agg = {"count": len(rs), "names": [r["name"] for r in rs], "texels_per_block": rs[0]["texels_per_block"]}
        for part in ("albedo", "normal", "specular"):
            have = [r[part] for r in rs if part in r]
            if not have:
                continue
            keys = {k for h in have for k in h}
            a = {}
            for k in sorted(keys):
                vs = [h.get(k) for h in have]
                if any(isinstance(v, dict) for v in vs):
                    sub = {}
                    for kk in sorted({kk for v in vs if v for kk in v}):
                        sub[kk] = _mean([v.get(kk) for v in vs if v])
                    a[k] = sub
                elif k == "green_along":
                    a[k] = sorted({v for v in vs if v})
                elif k == "metal_codes":
                    a[k] = sorted({c for v in vs if v for c in v})
                elif k == "greyscale":
                    a[k] = sum(1 for v in vs if v) / len(vs)
                else:
                    a[k] = _mean(vs)
            agg[part] = a
        agg["with_normal"] = sum(1 for r in rs if "normal" in r)
        agg["with_specular"] = sum(1 for r in rs if "specular" in r)
        out[g] = agg
    return out


def fmt(v, d=2):
    return "-" if v is None else ("%.*f" % (d, v))


def markdown(label, meta, agg):
    L = ["# Pack study: %s (numbers only)" % label, "",
         "No pixel of the studied pack is stored; only these statistics. Generated by tools/pack_study.py.", ""]
    for k, v in meta.items():
        L.append("- %s: %s" % (k, v))
    L += ["", "## Albedo", "",
          "| group | n | tpb | value | luma | luma std | p5-p95 | sat | grey | alpha / cover | detail by scale (blocks: luma std) |",
          "|---|---|---|---|---|---|---|---|---|---|---|"]
    for g, a in sorted(agg.items()):
        al = a.get("albedo", {})
        det = al.get("detail_by_scale", {})
        dets = " ".join("%s:%s" % (k, fmt(v, 3)) for k, v in det.items())
        L.append("| %s | %d | %d | %s | %s | %s | %s-%s | %s | %s | %s / %s | %s |" % (
            g, a["count"], a["texels_per_block"], fmt(al.get("value_mean")), fmt(al.get("luma_mean")),
            fmt(al.get("luma_std"), 3), fmt(al.get("luma_p5")), fmt(al.get("luma_p95")), fmt(al.get("sat_mean")),
            fmt(al.get("greyscale")), fmt(al.get("alpha_mean")), fmt(al.get("coverage")), dets))
    L += ["", "## Normal maps (LabPBR _n)", "",
          "| group | with _n | tilt mean | tilt p90 | AO mean | AO p5 | height mean | height std | green along |",
          "|---|---|---|---|---|---|---|---|---|"]
    for g, a in sorted(agg.items()):
        n = a.get("normal")
        if not n:
            continue
        L.append("| %s | %d | %s | %s | %s | %s | %s | %s | %s |" % (
            g, a["with_normal"], fmt(n.get("tilt_mean_deg"), 1), fmt(n.get("tilt_p90_deg"), 1), fmt(n.get("ao_mean")),
            fmt(n.get("ao_p5")), fmt(n.get("height_mean")), fmt(n.get("height_std"), 3),
            ", ".join(n.get("green_along", [])) or "-"))
    L += ["", "## Specular (LabPBR _s)", "",
          "| group | with _s | smooth p10/p50/p90 | roughness | metal frac | metal codes (238 = albedo F0) | F0 dielectric | porosity | SSS frac | emissive frac |",
          "|---|---|---|---|---|---|---|---|---|---|"]
    for g, a in sorted(agg.items()):
        s = a.get("specular")
        if not s:
            continue
        L.append("| %s | %d | %s/%s/%s | %s | %s | %s | %s | %s | %s | %s |" % (
            g, a["with_specular"], fmt(s.get("smooth_p10"), 0), fmt(s.get("smooth_p50"), 0), fmt(s.get("smooth_p90"), 0),
            fmt(s.get("roughness_mean")), fmt(s.get("metal_frac")), s.get("metal_codes") or "-",
            fmt(s.get("f0_dielectric_mean"), 1), fmt(s.get("porosity_mean"), 1), fmt(s.get("sss_frac")),
            fmt(s.get("emissive_frac"))))
    return "\n".join(L) + "\n"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("pack", nargs="?")
    ap.add_argument("out")
    ap.add_argument("--label", default="pack")
    ap.add_argument("--meta", default="{}", help="JSON of facts about the source (file name, version, sha512 prefix)")
    ap.add_argument("--ours", help="our entity texture dir (atlases with _n/_s beside them)")
    ap.add_argument("--ours-tpb", type=int, default=128, help="texels per block of our atlases (8x = 128)")
    ap.add_argument("--mip", type=int, default=0, help="measure at this mipmap level (2 = what the RTS camera samples "
                    "from a 128x texture at the default zoom: 32 texels per block)")
    args = ap.parse_args()
    global MIP
    MIP = args.mip
    os.makedirs(args.out, exist_ok=True)
    meta = json.loads(args.meta)
    rows = study_block_dir(args.pack) if args.pack else []
    meta["mip"] = MIP
    meta["block textures studied"] = len(rows)
    if args.ours:
        ours = study_ours(args.ours, args.ours_tpb)
        meta["our atlases studied"] = len(ours)
        rows += ours
    agg = aggregate(rows)
    json.dump({"label": args.label, "meta": meta, "groups": agg}, open(os.path.join(args.out, "study.json"), "w"),
              indent=1, sort_keys=True)
    open(os.path.join(args.out, "study.md"), "w").write(markdown(args.label, meta, agg))
    print("studied %d textures in %d groups -> %s" % (len(rows), len(agg), args.out))


if __name__ == "__main__":
    sys.exit(main())
