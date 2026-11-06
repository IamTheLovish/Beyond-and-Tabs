#!/usr/bin/env python3
"""Box-model units -> per-frame world-space cubes, for dsl_render.py (Blender).

The bone math lives in ONE place: the repo's own tools. This script imports them read-only by path and calls
  - tools/dsl_to_game.py  build_tree / mc_rot   (the game's ModelPart tree: translate, then rotate Z*Y*X, parents first)
  - tools/game_preview.py faces_of / VIEWS / L0 / L1 / view_matrix   (the posed boxes, _move and _scale applied
                                                                      exactly like ModelPart.translateAndRotate)
  - tools/arachnid_anim.py clips_for / run      (the spider clip drivers; world travel from clip.world_z())
and writes nothing it computed itself except bookkeeping: which cube a box is, its colour, flags and the 8 corners
read back from game_preview's faces. Blender only draws what is in the JSON.

    python tools/blender/export_frames.py --model design/models/gravebound_spider_queen.json
        --clips anim --names idle,walk,attack --step 2 --out out/frames/queen.json [--verify] [--repo <checkout>]
(--repo: the checkout whose tools/ do the bone math; default: the repo this file is in)

Clip sources (--clips):
  anim            run tools/arachnid_anim.py's clips live (has world travel: walk / run / charge / pounce)
  fixture[:PATH]  the sampled poses in bar/src/test/resources/arachnid/arachnid_anim_fixture.json (no world travel)
  poses           tools/arachnid_poses.py key poses, one frame each (a "clip" named "poses")
  rest            the model's own rest pose only
Ticks: --step N (every Nth tick, default 2) or --ticks "0,4,8" / "0-20". Always also writes a "rest" frame.

--verify recomputes every frame without _scale through a second route (tools/contact_check.py boxes, which is
tools/gen_models.py rot_mat forward kinematics in DSL space) and prints the worst corner distance in px.

Output space: DSL px, y up, the unit faces -Z, +x = the unit's left (design/models/README.md). Corner order: bit 4 =
the box's x max, bit 2 = its MC-model y max (the visual BOTTOM), bit 1 = its z max.
"""
import argparse
import json
import math
import os
import sys
import time

REPO_DEFAULT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))   # this repo
TEAM_DEFAULT = "tabard,tabard_dark"
OWNER_DEFAULT = "#3a6fd1"          # game_preview's default owner colour
U_STRIDE = 4096                     # the fake UV u of cube k is k * U_STRIDE (box UVs never span that far)

# glow layers (arachnid_anim Pose.glow) -> which emissive cubes they scale. The Java side owns the real mapping; this
# is the v1 reading of the layer names: (layer, bone-name test). The first match wins; "soul" scales every glow.
GLOW_LAYERS = (
    ("eye_bar", lambda bn: bn == "eyes" or bn.startswith("crown")),
    ("fang_tips", lambda bn: bn.startswith("fang")),
    ("sac_1", lambda bn: bn.startswith("sac_1")),
    ("sac_2", lambda bn: bn.startswith("sac_2")),
    ("sac_3", lambda bn: bn.startswith("sac_3")),
    ("sac_4", lambda bn: bn.startswith("sac_4")),
    ("heart_line", lambda bn: bn == "abdomen"),
    ("cracks", lambda bn: bn == "abdomen"),
)


def hex_rgb(h):
    return [int(h[i:i + 2], 16) / 255.0 for i in (1, 3, 5)]


def parse_ticks(spec, n):
    if spec in (None, "", "all"):
        return None
    out = set()
    for part in spec.split(","):
        if "-" in part:
            a, b = part.split("-")
            out.update(range(int(a), int(b) + 1))
        elif part:
            out.add(int(part))
    return sorted(t for t in out if 0 <= t <= n)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--model", required=True, help="design/models/<unit>.json")
    ap.add_argument("--clips", default="anim", help="anim | fixture[:path] | poses | rest")
    ap.add_argument("--names", default="", help="comma list of clip names (default: all)")
    ap.add_argument("--step", type=int, default=2, help="sample every Nth tick (default 2)")
    ap.add_argument("--ticks", default="", help="explicit ticks: '0,4,8' or '0-20' (overrides --step)")
    ap.add_argument("--unit", default="", help="arachnid key (default: from the file name gravebound_spider_<key>)")
    ap.add_argument("--repo", default=REPO_DEFAULT)
    ap.add_argument("--team", default=TEAM_DEFAULT, help="palette names painted in the owner colour")
    ap.add_argument("--owner", default=OWNER_DEFAULT)
    ap.add_argument("--upgrade", action="store_true", help="show upgrade-tagged parts")
    ap.add_argument("--verify", action="store_true", help="cross-check corners against contact_check FK")
    ap.add_argument("--out", required=True)
    a = ap.parse_args()

    tools = os.path.join(a.repo, "tools")
    sys.path.insert(0, tools)
    t_start = time.time()
    import render_concept                     # noqa: E402  (repo, read-only)
    import dsl_to_game as d2g                  # noqa: E402
    import game_preview as gp                  # noqa: E402
    from preview_model import view_matrix      # noqa: E402

    model_path = os.path.abspath(a.model)
    m = render_concept.load_model(model_path)
    emissive = set(m.get("emissive", []))
    team = [t for t in a.team.split(",") if t]
    key = a.unit or os.path.splitext(os.path.basename(model_path))[0].replace("gravebound_spider_", "")

    # ---- cubes: the game's part tree with each box's u standing for its cube id ----
    cubes, uvs = [], {}
    for b in m["bones"]:
        for i, c in enumerate(b.get("cubes", [])):
            k = len(cubes)
            uvs[(b["name"], i)] = (k * U_STRIDE, 0)
            col = c["color"]
            glow_layer = None
            if col in emissive:
                for layer, test in GLOW_LAYERS:
                    if test(b["name"]):
                        glow_layer = layer
                        break
            cubes.append(dict(id=k, bone=b["name"], index=i, color=col, rgb=hex_rgb(m["palette"][col]),
                              emissive=col in emissive, team=col in team, team_shade=(team.index(col) if col in team
                                                                                     else None),
                              upgrade=bool(c.get("upgrade") or b.get("upgrade")), glow_layer=glow_layer))
    root = d2g.build_tree(m, uvs, emissive)
    rest_mc = {n: d2g.mc_rot(r) for n, r in m["pose"].items()}

    def corners_of(extra):
        """{cube id: 8 DSL corners} for one pose dict (game_preview --poses form), via game_preview.faces_of."""
        pose_mc = dict(rest_mc)
        for bone, r in extra.items():
            if bone.startswith("_"):
                continue
            cur = pose_mc.get(bone, (0.0, 0.0, 0.0))
            add = d2g.mc_rot(r)
            pose_mc[bone] = tuple(cur[i] + add[i] for i in range(3))
        fs = gp.faces_of(root, pose_mc, True, extra.get("_upgrade", a.upgrade), set(extra.get("_hide", [])),
                         extra.get("_move"), extra.get("_scale"))
        out = {}
        assert len(fs) % 6 == 0
        for j in range(0, len(fs), 6):
            f0, f1 = fs[j][0], fs[j + 1][0]            # DOWN (v6, v5, v1, v2), UP (v3, v4, v8, v7)
            k = int(min(uv[0] for uv in fs[j][1]) // U_STRIDE)
            v1, v2, v5, v6 = f0[2], f0[3], f0[1], f0[0]
            v3, v4, v8, v7 = f1[0], f1[1], f1[2], f1[3]
            mc = [v1, v5, v4, v8, v2, v6, v3, v7]      # bits: x hi 4, (MC) y hi 2, z hi 1
            out[k] = [[float(p[0]), 24.0 - float(p[1]), float(p[2])] for p in mc]
        return out

    # ---- clips ----
    clips = {}                 # name -> dict(ticks, loop, frames: [(t, pose dict, world_z)])
    src = a.clips
    names = [n for n in a.names.split(",") if n]
    if src == "anim":
        import arachnid_anim as aa             # noqa: E402
        _b, C = aa.clips_for(key)
        for name, clip in C.items():
            if names and name not in names:
                continue
            rows = aa.run(clip)
            want = parse_ticks(a.ticks, clip.ticks)
            fr = [(t, pose.to_dict(6), wz) for (t, pose, _f, wz) in rows
                  if (t in want if want is not None else t % a.step == 0)]
            clips[name] = dict(ticks=clip.ticks, loop=bool(clip.loop), frames=fr)
    elif src.startswith("fixture"):
        path = src.split(":", 1)[1] if ":" in src else os.path.join(
            a.repo, "bar", "src", "test", "resources", "arachnid", "arachnid_anim_fixture.json")
        fx = json.load(open(path))
        for name, c in fx["units"][key].items():
            if names and name not in names:
                continue
            want = parse_ticks(a.ticks, c["ticks"])
            fr = [(int(t), d, 0.0) for t, d in sorted(c["samples"].items(), key=lambda kv: int(kv[0]))
                  if (int(t) in want if want is not None else int(t) % a.step == 0)]
            clips[name] = dict(ticks=c["ticks"], loop=c["loop"], frames=fr)
    elif src == "poses":
        import arachnid_poses as ap_            # noqa: E402
        _rig, P, _rep = ap_.unit_poses(key)
        clips["poses"] = dict(ticks=len(P) - 1, loop=False,
                              frames=[(i, dict(d, _label=n), 0.0) for i, (n, d) in enumerate(P.items())])
    elif src != "rest":
        raise SystemExit("unknown --clips %r" % src)

    # ---- frames ----
    rest_corners = corners_of({})
    all_pts = [p for cs in rest_corners.values() for p in cs]
    lo = [min(p[i] for p in all_pts) for i in range(3)]
    hi = [max(p[i] for p in all_pts) for i in range(3)]
    rest_centre = [(lo[i] + hi[i]) / 2 for i in range(3)]

    def frame(t, d, wz):
        cs = corners_of(d)
        if wz:
            for k in cs:
                cs[k] = [[p[0], p[1], p[2] + wz] for p in cs[k]]
        mv = d.get("_move", {}).get("body", [0.0, 0.0, 0.0])
        anchor = [rest_centre[0] + mv[0], rest_centre[1] + mv[1], rest_centre[2] + mv[2] + wz]
        glow = d.get("_glow", {})
        g = {}
        for c in cubes:
            if c["emissive"] and c["id"] in cs:
                v = glow.get(c["glow_layer"], 1.0) if c["glow_layer"] else 1.0
                if c["glow_layer"] and c["glow_layer"].startswith("sac_") and "sacs" in glow:
                    v = glow.get(c["glow_layer"], glow["sacs"])
                v *= glow.get("soul", 1.0)
                if abs(v - 1.0) > 1e-6:
                    g[c["id"]] = round(v, 4)
        return dict(t=t, world_z=round(wz, 4), anchor=[round(v, 4) for v in anchor],
                    label=d.get("_label"), glow=g, fx=d.get("_fx", []),
                    cubes={str(k): [[round(v, 4) for v in p] for p in cs[k]] for k in sorted(cs)})

    out_clips = {"rest": dict(ticks=0, loop=False, frames=[frame(0, {}, 0.0)])}
    for name, c in clips.items():
        out_clips[name] = dict(ticks=c["ticks"], loop=c["loop"], frames=[frame(t, d, wz) for (t, d, wz) in c["frames"]])

    # ---- cameras: game_preview's views + an 8-view turnaround, as camera bases in DSL space ----
    def basis(yaw, tilt):
        R = view_matrix(yaw, tilt)                 # view = R @ dsl; the viewer looks along +z, x right, y up
        col = lambda r: [R[r][0], R[r][1], R[r][2]]  # noqa: E731  rows of R = the view axes in DSL space
        return dict(yaw=yaw, tilt=tilt, right=col(0), up=col(1), forward=col(2))
    views = {n: basis(*v) for n, v in gp.VIEWS.items()}
    views["profile"] = basis(90, 12)           # the side view raised 12 degrees so the ground grid and contacts read
    for k in range(8):
        views["turn_%03d" % (k * 45)] = basis(-40 + k * 45, 18)

    worst = None
    if a.verify:
        import contact_check                   # noqa: E402
        import numpy as np                      # noqa: E402
        worst = (0.0, None)
        for name, c in clips.items():
            for (t, d, wz) in c["frames"]:
                if d.get("_scale"):
                    d = dict(d, _scale={})       # contact_check has no scale: compare the unscaled pose
                extra = {k: v for k, v in d.items() if not k.startswith("_")}
                bx, _fr = contact_check.boxes(m, extra, d.get("_hide", ()), d.get("_move", {}))
                mine = corners_of(d)
                ids = [cb["id"] for cb in cubes if not cb["upgrade"]]
                for (bn, i, _col, ctr, A), k in zip(bx, ids):
                    cs = np.array(mine[k])
                    # corner (sx, sy, sz) of contact_check = ctr + A @ s; MC y hi is DSL y LOW
                    for idx in range(8):
                        s = np.array([1 if idx & 4 else -1, -1 if idx & 2 else 1, 1 if idx & 1 else -1], dtype=float)
                        dist = float(np.linalg.norm(ctr + A @ s - cs[idx]))
                        if dist > worst[0]:
                            worst = (dist, "%s t%d %s[%d] corner %d" % (name, t, bn, i, idx))
        print("verify: worst corner distance %.6f px (%s)" % worst)

    team_rgb = hex_rgb(a.owner)
    doc = dict(
        about="export_frames.py: DSL px, y up, unit faces -Z, +x = unit's left; corners from game_preview.faces_of",
        unit=key, name=m["name"], faction=m["faction"], role=m["role"], model=model_path, clip_source=src,
        step=a.step, px_per_block=m["scale_px_per_block"],
        palette={k: hex_rgb(v) for k, v in m["palette"].items()},
        game_palette={k: [v / 255.0 for v in (d2g.hex_rgb(h) if k in emissive else d2g.game_value(
            d2g.realistic_colour(k, d2g.hex_rgb(h))))] for k, h in m["palette"].items()},
        team_names=team, owner=team_rgb, owner_dark=[v * 190 / 255.0 for v in team_rgb],
        owner_game=[float(v) for v in d2g.owner_dye(team_rgb)],
        lights=dict(L0=[float(v) for v in gp.L0], L1=[float(v) for v in gp.L1]),
        rest_bbox=dict(lo=lo, hi=hi), cubes=cubes, views=views, clips=out_clips,
        verify_worst_px=(worst[0] if worst else None))
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    with open(a.out, "w") as fh:
        json.dump(doc, fh, separators=(",", ":"))
    n = sum(len(c["frames"]) for c in out_clips.values())
    print("wrote %s: %d cubes, %d clips, %d frames in %.1f s" % (a.out, len(cubes), len(out_clips), n,
                                                                 time.time() - t_start))


if __name__ == "__main__":
    main()
