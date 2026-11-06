#!/usr/bin/env python3
"""The Quarry Saint's measurements and game_preview colour stills (the game's own atlas, team mask, glow and
two-light shading - Blender's flat light misjudges colour values, so these stand beside the Blender sheets).

    python tools/render/quarry_saint/stills.py --out OUT [measure] [stills]

measure -> OUT/numbers.json and OUT/summary.md: per clip of tools/quarry_saint_anim.py (FK of the DSL model, every
tick): the left fist to the haft's axis and its place along the haft (fists overlap under 11 px), planted-foot slip in
the WORLD frame (root travel included), the lowest foot, the planted knee, arm into body (contact_check, joints 3.5
px) and the clearance miss (forearms / fists grown 0.5 px); the halo's visible share at the slam hold (the maul
drawn vs hidden, rts camera); the top-view gold share (topdown, rest).
stills -> OUT/stills_body.png, OUT/stills_keys.png (game_preview look at fixed px per block).
"""
import argparse
import json
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import render_concept  # noqa: E402
import dsl_to_game as d2g  # noqa: E402
import game_preview as gp  # noqa: E402
import contact_check  # noqa: E402
from preview_model import view_matrix  # noqa: E402

MODEL = os.path.join(ROOT, "design", "models", "sunforged_quarry_saint.json")
RED, BLUE = (0xd1 / 255, 0x3a / 255, 0x3a / 255), (0x2f / 255, 0x5f / 255, 0xb3 / 255)
LAWN = (0x6f / 255, 0x8f / 255, 0x4a / 255)
TWO_HAND = ("sweep", "slam", "gatefall")
ARM = ("shoulder_r", "shoulder_l", "forearm_r", "forearm_l", "hand_r", "hand_l")
BODY = ("torso", "head", "pelvis", "halo")
SLIP_GOAL, GOLD_GOAL, HALO_GOAL = 0.02, 6.0, 0.80


def font(size, bold=False):
    for name in (("DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"), ("arialbd.ttf" if bold else "arial.ttf")):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


FONT, FONT_B = font(14), font(17, True)


class Body:
    def __init__(self, path, team=("tabard", "tabard_dark")):
        self.m = render_concept.load_model(path)
        self.m["_path"] = os.path.abspath(path)
        _java, self.imgs, info = d2g.build(self.m, "Preview", list(team), "preview", d2g.DENSITY)
        self.root = info["root"]

    def faces(self, extra=None, detail=True, upgrade=False, hide=()):
        extra = extra or {}
        pose_mc = {n: d2g.mc_rot(r) for n, r in self.m["pose"].items()}
        for bone, r in extra.items():
            if bone.startswith("_"):
                continue
            cur = pose_mc.get(bone, (0.0, 0.0, 0.0))
            add = d2g.mc_rot(r)
            pose_mc[bone] = tuple(cur[i] + add[i] for i in range(3))
        return gp.faces_of(self.root, pose_mc, detail, upgrade, hide, extra.get("_move"))


def box_for(view, centre, ppb, size):
    R = np.array(view_matrix(*gp.VIEWS[view]))
    c = R @ np.array(centre, float)
    w = size / (ppb / 16.0) / 1.08
    return (c[0] - w / 2, c[0] + w / 2, c[1] - w / 2, c[1] + w / 2)


def cell(body, view, extra=None, ppb=42, size=360, centre=(0, 26, -10), owner=RED, detail=True, bg=None,
         upgrade=False, hide=()):
    fs = body.faces(extra, detail, upgrade, hide)
    box = box_for(view, centre, ppb, size)
    im, _, mask, _t = gp.render(fs, body.imgs, d2g.DENSITY, gp.VIEWS[view], size, owner, box, with_mask=True)
    if bg is not None:
        a = np.asarray(im, dtype=np.float32) / 255.0
        a[~mask] = bg
        im = Image.fromarray((a * 255).astype(np.uint8))
    return im, mask


def labelled(im, text, pad=22):
    out = Image.new("RGB", (im.width, im.height + pad), (245, 243, 239))
    out.paste(im, (0, 0))
    ImageDraw.Draw(out).text((4, im.height + 3), text, fill=(30, 30, 30), font=FONT)
    return out


def sheet(rows, title, sub, gap=8):
    W = max(max(sum(c.width for c in r) + gap * (len(r) + 1) for r in rows), 900)
    probe = ImageDraw.Draw(Image.new("RGB", (8, 8)))
    lines, cur = [], ""
    for word in sub.split(" "):
        t = (cur + " " + word).strip()
        if probe.textlength(t, font=FONT) > W - 2 * gap and cur:
            lines.append(cur)
            cur = word
        else:
            cur = t
    lines.append(cur)
    head = 30 + 19 * len(lines)
    H = sum(max(c.height for c in r) for r in rows) + gap * (len(rows) + 1) + head
    out = Image.new("RGB", (W, H), (245, 243, 239))
    d = ImageDraw.Draw(out)
    d.text((gap, 6), title, fill=(10, 10, 10), font=FONT_B)
    for i, ln in enumerate(lines):
        d.text((gap, 28 + 19 * i), ln, fill=(60, 60, 60), font=FONT)
    y = head + gap
    for r in rows:
        x = gap
        for c in r:
            out.paste(c, (x, y))
            x += c.width + gap
        y += max(c.height for c in r) + gap
    return out


def extra_of(pose):
    ex, mv = {}, {}
    for n, b in pose["bones"].items():
        if any(abs(v) > 1e-9 for v in b["rot"]):
            ex[n] = b["rot"]
        if any(abs(v) > 1e-9 for v in b["off"]):
            mv[n] = b["off"]
    return ex, mv


def to_preview(pose):
    ex, mv = extra_of(pose)
    d = dict(ex)
    d["_move"] = mv
    return d


def clear_dsl(dsl, grow=0.5):
    import copy
    d = copy.deepcopy(dsl)
    for b in d["bones"]:
        if b["name"] in ("forearm_r", "forearm_l", "hand_r", "hand_l"):
            for c in b["cubes"]:
                c["inflate"] = c.get("inflate", 0.0) + grow
    return d


def arm_body(dsl, ex, mv):
    worst = (0.0, None)
    for h in contact_check.check(dsl, ex, (), mv, threshold=0.05, joint=3.5):
        a_, b_ = h[1], h[4]
        if (a_ in ARM and b_ in BODY) or (b_ in ARM and a_ in BODY):
            if h[0] > worst[0]:
                worst = (round(float(h[0]), 2), "%s/%s" % (a_, b_))
    return worst


# ------------------------------------------------------------------ measurement ---------------------------------------
def measure(A, runs):
    dsl = render_concept.load_model(MODEL)
    dslc = clear_dsl(dsl)
    bones = {b["name"]: b for b in dsl["bones"]}
    gr = bones["maul"]["pivot"]
    gl = [-gr[0], gr[1], gr[2]]
    out = {}
    for name, frames in runs.items():
        rec = {"haft_l_max": 0.0, "along_min": None, "along_max": None, "slip_max": 0.0, "slip_total": 0.0,
               "ground_min": None, "knee_planted_max": 0.0, "arm_body_max": [0.0, None], "clear_miss_max": [0.0, None]}
        prev = {}
        for t, pose in frames:
            ex, mv = extra_of(pose)
            fr = contact_check._frames(dsl, ex, mv)

            def W(n, p):
                M, tv = fr[n]
                return M @ np.array(p, float) + tv
            root = np.array(pose["root"], float)
            if name in TWO_HAND:
                G, H = W("maul", gr), W("maul", (gr[0], gr[1], gr[2] - 10.0))
                d = (H - G) / np.linalg.norm(H - G)
                w = W("hand_l", gl) - G
                along = float(w @ d)
                dist = float(np.linalg.norm(w - along * d))
                rec["haft_l_max"] = max(rec["haft_l_max"], dist)
                rec["along_min"] = along if rec["along_min"] is None else min(rec["along_min"], along)
                rec["along_max"] = along if rec["along_max"] is None else max(rec["along_max"], along)
            for k in ("r", "l"):
                a = W("foot_" + k, bones["foot_" + k]["pivot"]) + root
                if k in pose["planted"] and k in prev:
                    s = float(np.linalg.norm(a - prev[k]))
                    rec["slip_max"] = max(rec["slip_max"], s)
                    rec["slip_total"] += s
                if k in pose["planted"]:
                    prev[k] = a
                    rec["knee_planted_max"] = max(rec["knee_planted_max"], -pose["bones"]["shin_" + k]["rot"][0])
                    for c in bones["foot_" + k]["cubes"]:
                        lo = np.array(c["from"], float)
                        hi = lo + np.array(c["size"], float)
                        y = min(float(W("foot_" + k, (x, yy, z))[1]) for x in (lo[0], hi[0]) for yy in (lo[1], hi[1])
                                for z in (lo[2], hi[2]))
                        rec["ground_min"] = y if rec["ground_min"] is None else min(rec["ground_min"], y)
                else:
                    prev.pop(k, None)
            if t % 2 == 0 and name != "death":
                ab = arm_body(dsl, ex, mv)
                if ab[0] > rec["arm_body_max"][0]:
                    rec["arm_body_max"] = [ab[0], "t%d %s" % (t, ab[1])]
                cm = arm_body(dslc, ex, mv)
                if cm[0] > rec["clear_miss_max"][0]:
                    rec["clear_miss_max"] = [cm[0], "t%d %s" % (t, cm[1])]
        out[name] = {k: (round(v, 4) if isinstance(v, float) else v) for k, v in rec.items()}
        print(name, out[name], flush=True)
    return out


def halo_share(qs, extra, size=600, ppb=80):
    """The share of the halo's visible pixels that stay visible with the maul drawn (rts camera): pixels the halo
    changes with the maul drawn / pixels it changes with the maul hidden."""
    def img(hide):
        im, _m = cell(qs, "rts", extra, ppb=ppb, size=size, centre=(0, 44, 0), hide=hide)
        return np.asarray(im).astype(int)
    a, b = img(()), img(("halo",))
    c, d = img(("maul",)), img(("maul", "halo"))
    vis = int(np.any(a != b, axis=-1).sum())
    ref = int(np.any(c != d, axis=-1).sum())
    return round(vis / float(max(1, ref)), 4), vis, ref


def gold_share(qs, upgrade=False):
    im, mask = cell(qs, "topdown", None, ppb=96, size=600, centre=(-4, 24, -18), owner=(0, 0, 1.0), upgrade=upgrade)
    a = np.asarray(im).astype(int)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    team = (b > r + 40) & (b > g + 20) & mask
    gold = (r > g + 15) & (g > b + 35) & mask & ~team
    return round(100.0 * gold.sum() / max(1, mask.sum()), 2)


def key_report(out):
    p = os.path.join(out, "key_report.json")
    if not os.path.exists(p):
        return {}
    rep = json.load(open(p))
    return {k: {"haft_l": v["haft_l"], "arm_body": (v["arm_body"][:1] or [[0]])[0],
                "arm_body_clear": (v.get("arm_body_clear", [])[:1] or [[0]])[0]} for k, v in rep.items()}


def summary(nums, timings):
    c = nums["clips"]
    walk = c["walk"]
    worst_arm = max(((v["arm_body_max"][0], n, v["arm_body_max"][1]) for n, v in c.items()), key=lambda x: x[0])
    worst_clear = max(((v["clear_miss_max"][0], n, v["clear_miss_max"][1]) for n, v in c.items()), key=lambda x: x[0])
    two = {n: c[n] for n in TWO_HAND}
    along_min = min(v["along_min"] for v in two.values())
    haft_max = max(v["haft_l_max"] for v in two.values())
    hs = nums["halo_slam_hold"]
    lines = ["## Quarry Saint r3b - measured", "",
             "| fix | goal | measured |", "|---|---|---|",
             "| walk: planted-foot slip, world frame | <= %.2f px/tick | max %.4f px/tick, %.3f px over %d ticks |" % (
                 SLIP_GOAL, walk["slip_max"], walk["slip_total"], nums["walk_ticks"]),
             "| arms into torso / head (all clips but death) | 0 px | max %.2f px (%s %s) |" % worst_arm,
             "| ... with 0.5 px clearance (forearms, fists grown 0.5) | 0 px | max %.2f px (%s %s) |" % worst_clear,
             "| halo visible at the slam hold (rts, maul drawn vs hidden) | >= %d%% | %.0f%% (%d / %d px) |" % (
                 HALO_GOAL * 100, hs[0] * 100, hs[1], hs[2]),
             "| top-view gold (rest, topdown) | <= %.0f%% | %.2f%% (upgraded %.2f%%) |" % (
                 GOLD_GOAL, nums["gold_top_pct"]["base"], nums["gold_top_pct"]["upgrade"]),
             "| fists: left fist place along the haft (two-hand clips) | >= 11 px | min %.2f px; off the haft max "
             "%.2f px |" % (along_min, haft_max), "",
             "| step | seconds |", "|---|---|"]
    for k, v in timings.items():
        lines.append("| %s | %s |" % (k, v))
    return "\n".join(lines) + "\n"


def stills(qs, A, runs, out):
    rows = []
    for owner, on in ((RED, "#d13a3a"), (BLUE, "#2f5fb3")):
        rows.append([labelled(cell(qs, v, None, ppb=80, size=380, centre=(-6, 26, -18), owner=owner)[0],
                              "%s - owner %s - rest" % (v, on)) for v in ("front", "quarter", "side", "rts", "topdown")])
    sheet(rows, "Quarry Saint r3b - colour stills (game_preview: the game's atlas, team mask, glow, two lights)",
          "rest at 80 px per block, detail on; Blender's flat light misjudges colour values, judge colour here"
          ).save(os.path.join(out, "stills_body.png"))
    keys = (("slam_windup", "rts", (-4, 42, 0)), ("sweep_windup", "rts", (6, 28, -10)), ("carry", "quarter", (-4, 32, -4)),
            ("slam_contact", "rts", (-4, 22, -20)), ("gatefall_windup", "rts", (-4, 44, 0)),
            ("sweep_follow", "rts", (-4, 26, -14)))
    row = [labelled(cell(qs, v, A.KEYS[k], ppb=50, size=340, centre=c)[0], "%s - %s" % (k, v)) for k, v, c in keys]
    sheet([row[:3], row[3:]], "Quarry Saint r3b - key poses, game_preview colour, 50 px per block",
          "solved keys (design/fixtures/quarry_saint_keys.json), owner #d13a3a").save(os.path.join(out, "stills_keys.png"))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("what", nargs="*", default=["measure", "stills"])
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    out = os.path.abspath(a.out)
    os.makedirs(out, exist_ok=True)
    import quarry_saint_anim as A
    runs = {n: A.run(n) for n in A.CLIPS}
    qs = Body(MODEL)
    if "measure" in a.what:
        nums = {"clips": measure(A, runs), "walk_ticks": A.CLIPS["walk"]["ticks"]}
        slam = dict(runs["slam"])
        nums["halo_slam_hold"] = halo_share(qs, to_preview(slam[17]))
        nums["halo_slam_windup_key"] = halo_share(qs, A.KEYS["slam_windup"])
        nums["halo_rest"] = halo_share(qs, A.KEYS["rest"])
        nums["gold_top_pct"] = {"base": gold_share(qs), "upgrade": gold_share(qs, True)}
        nums["keys"] = key_report(out)
        json.dump(nums, open(os.path.join(out, "numbers.json"), "w"), indent=1)
        tp = os.path.join(out, "timings.json")
        timings = json.load(open(tp)) if os.path.exists(tp) else {}
        open(os.path.join(out, "summary.md"), "w").write(summary(nums, timings))
        print(open(os.path.join(out, "summary.md")).read())
    if "stills" in a.what:
        stills(qs, A, runs, out)


if __name__ == "__main__":
    main()
