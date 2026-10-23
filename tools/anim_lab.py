#!/usr/bin/env python3
"""The animation lab (Lane B, lovish Oct 19 spec "test animations over and over until fluid and lifelike"; Oct 21:
"animations top priority") - a unit's animation keys as DATA (design/anims/<unit>.json) that the game and these tools
both play, so a clip can be judged and measured offline in seconds instead of a CI run per tweak.

    python3 tools/anim_lab.py design/anims/halberdier.json --gen-java     # writes the model's generated key table
    python3 tools/anim_lab.py design/anims/halberdier.json --check        # the table in the repo == the data? (CI)
    python3 tools/anim_lab.py design/anims/halberdier.json --motion       # numbers per clip; exit 1 on a FAIL
    python3 tools/anim_lab.py design/anims/halberdier.json --sheet OUT_DIR [--clip thrust] [--frames 10] [--ref IMG]
                                                                          # ONE readable png per clip (<= 2000 px)
    python3 tools/anim_lab.py design/anims/halberdier.json --play OUT_DIR # frame strips + GIFs per clip and view
    python3 tools/anim_lab.py design/anims/halberdier.json --blend OUT_DIR  # walk -> ready -> thrust -> ready as the game blends

The data file:
    {"model": DSL model path, "java_class": generated class name, "java_bones": {data bone: Java constant stem},
     "clips": {name: {"ticks": game ticks the clip lasts, "hit_p": the progress where the blow lands (attacks),
                      "loop": true for a cycle (an idle, a walk: the tangents wrap round the seam, so nothing stops
                      at p = 0; its first and last keys must be the same pose), "phase": a walk's cycles per vanilla
                      limbSwing cycle (the stride length the feet were keyed for),
                      "keys": [{"p": 0..1, bone: [x, y, z] DSL degrees, bone + "@move": [x, y, z] DSL px}, ...]}},
     "poses": {name: {bone: [...], ...}}}            # held poses (the guard a clip starts and ends in)
Angles are DSL degrees ADDED to the model's own pose, exactly what a model subclass adds each frame (a part takes
-x, +y, -z radians); moves are px (y up) added to the part's offset. Between keys the game plays a monotone cubic
Hermite (Fritsch-Carlson tangents: speed carries through a key, a turn or a hold stops cleanly, no overshoot) -
--motion reports where every bone still comes to a full stop mid-clip.

Motion checks (per clip, sampled 8x per game tick):
  snap      a bone's angular speed changes by more than SNAP_DEG_PER_TICK2 between two sub-ticks at a key (a jerk);
  stop      every moving bone comes to rest at an inner key (smoothstep per segment does that - reads robotic);
  speed     a bone turns faster than MAX_DEG_PER_TICK (past what a human limb can);
  range     a joint outside its human range of motion (ROM, below) at any sample;
  loop      a clip's first and last keys differ from the pose it starts and ends in (a pop on blend);
  hit       the blow (hit_p) is not in the clip's fastest segment of the striking bone (the hit lands on a slow frame).
"""
import argparse
import json
import math
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GEN_DIR = os.path.join(ROOT, "bar", "src", "main", "java", "com", "solegendary", "reignofnether", "unit", "modelling",
                       "models", "gen")
SUB = 8                       # samples per game tick
IDLE_FADE = 0.15              # the idle's drift off the guard fades out over this much of the thrust, and back in at its end
#                               (HalberdierUnitModel.IDLE_FADE: the same number - the thrust starts from the guard the
#                               idle is wandering round, not from a snap to it)
MAX_DEG_PER_TICK = 90.0       # 1800 deg/s: a fast human strike peaks around 1000-1500 deg/s at the shoulder
SNAP_DEG_PER_TICK2 = 120.0    # a change of angular speed this large between two sub-ticks reads as a snap
# human range of motion, DSL degrees ADDED to the rest pose (x = flex/extend forward/back, z = out/in). Wide on purpose:
# it catches broken joints, not style. Sources: AAOS / Norkin & White joint ROM tables.
ROM = {
    "right_arm": {"x": (-60, 180), "z": (-45, 180)}, "left_arm": {"x": (-60, 180), "z": (-180, 45)},
    "body": {"x": (-45, 30), "y": (-45, 45), "z": (-30, 30)},
    "head": {"x": (-60, 50), "y": (-80, 80)},
}
# the elbow only flexes: its ABSOLUTE flex (the model's rest rotation + the key) stays in this range - the rest flex
# differs per rig (the Halberdier's right forearm rests at 126, up on the planted haft), so a delta range cannot say it
ROM_ABS = {"right_fore": {"x": (-5, 150)}, "left_fore": {"x": (-5, 150)}}


def smooth(x):
    return x * x * (3 - 2 * x)


def load(path):
    with open(path) as f:
        return json.load(f)


def bones_of(clip):
    out = []
    for k in clip["keys"]:
        for b in k:
            if b != "p" and b not in out:
                out.append(b)
    return out


def tangents(ps, vs, loop=False):
    """Fritsch-Carlson monotone cubic tangents (per tick of p) through (p, v): speed carries through a key when the
    motion continues the same way, a key at a turn gets a zero tangent (no overshoot, a hold stays a hold), the ends
    ease in and out - or, for a loop, wrap: the last segment feeds the first key's tangent (and the last key's, the
    same pose), so a cycle runs through its seam. The game's HalberdierUnitModel.tangents plays the same curve -
    change both together."""
    n = len(ps)
    d = [(vs[i + 1] - vs[i]) / (ps[i + 1] - ps[i]) for i in range(n - 1)]
    m = [0.0] * n

    def fc(a, b):
        if a * b <= 0:
            return 0.0
        t = (a + b) / 2
        al, be = t / a, t / b
        if al * al + be * be > 9:
            t *= 3 / math.sqrt(al * al + be * be)
        return t
    for i in range(1, n - 1):
        m[i] = fc(d[i - 1], d[i])
    if loop:
        m[0] = m[n - 1] = fc(d[n - 2], d[0])
    return m


def sample(clip, p):
    """The pose at progress p (0..1): {bone: [x, y, z]} - a monotone cubic Hermite through every key (see tangents),
    as the game plays it. Before Oct 21 each segment eased with smoothstep: every bone stopped dead at every key."""
    keys = clip["keys"]
    ps = [k["p"] for k in keys]
    k = 0
    while k < len(keys) - 2 and p > ps[k + 1]:
        k += 1
    h = ps[k + 1] - ps[k]
    t = min(1.0, max(0.0, (p - ps[k]) / h))
    t2, t3 = t * t, t * t * t
    h00, h10, h01, h11 = 2 * t3 - 3 * t2 + 1, t3 - 2 * t2 + t, -2 * t3 + 3 * t2, t3 - t2
    out = {}
    cache = clip.setdefault("_tan", {})
    for bone in bones_of(clip):
        if bone not in cache:
            cols = [[kk.get(bone, [0, 0, 0])[i] for kk in keys] for i in range(3)]
            cache[bone] = (cols, [tangents(ps, c, bool(clip.get("loop"))) for c in cols])
        cols, tans = cache[bone]
        out[bone] = [h00 * cols[i][k] + h10 * h * tans[i][k] + h01 * cols[i][k + 1] + h11 * h * tans[i][k + 1]
                     for i in range(3)]
    return out


# ------------------------------------------------------------------ Java ---------------------------------------
def fnum(v):
    """A Java float literal: whole numbers bare (they widen), the rest with an f."""
    v = float(v)
    return "%d" % v if v == int(v) else repr(v) + "f"


def jarr(vs):
    return "{" + ", ".join(fnum(v) for v in vs) + "}"


def gen_java(data):
    """The generated key table: one class of static arrays per unit (unit/modelling/models/gen/, never hand-edited)."""
    cls = data["java_class"]
    lines = ["package com.solegendary.reignofnether.unit.modelling.models.gen;", "",
             "/**", " * GENERATED by tools/anim_lab.py from %s - do not edit; change the data and run" % data["_rel"],
             " * python3 tools/anim_lab.py %s --gen-java" % data["_rel"], " */",
             "public final class %s {" % cls, "    private %s() {" % cls, "    }", ""]
    lines.append("    /** The data's bones, the order of every _ROT / _MOVE table. */")
    lines.append("    public static final String[] BONES = {%s};" % ", ".join('"%s"' % b for b in data["java_bones"]))
    for pname, pose in data.get("poses", {}).items():
        for bone, stem in data["java_bones"].items():
            for suffix, field in (("", ""), ("@move", "_MOVE")):
                v = pose.get(bone + suffix)
                if v is not None:
                    lines.append("    public static final float[] %s_%s%s = %s;" % (pname.upper(), stem, field, jarr(v)))
    for cname, clip in data["clips"].items():
        C = cname.upper()
        lines.append("")
        lines.append("    public static final int %s_TICKS = %d;" % (C, clip["ticks"]))
        lines.append("    public static final boolean %s_LOOP = %s;" % (C, "true" if clip.get("loop") else "false"))
        if "phase" in clip:
            lines.append("    public static final float %s_PHASE = %s;" % (C, fnum(clip["phase"])))
        if "hit_p" in clip:
            lines.append("    public static final float %s_HIT_P = %s;" % (C, fnum(clip["hit_p"])))
        lines.append("    public static final float[] %s_P = %s;" % (C, jarr([k["p"] for k in clip["keys"]])))
        for bone, stem in data["java_bones"].items():
            for suffix, field in (("", ""), ("@move", "_MOVE")):
                if any(bone + suffix in k for k in clip["keys"]):
                    rows = ", ".join(jarr(k.get(bone + suffix, [0, 0, 0])) for k in clip["keys"])
                    lines.append("    public static final float[][] %s_%s%s = {%s};" % (C, stem, field, rows))
        # the clip as one table over BONES (null where a bone has no keys / no move), for a shared player
        # (animations/HumanBase) that takes a clip whole instead of a bone list per unit
        rot_rows = ", ".join("%s_%s" % (C, stem) if any(bone in k for k in clip["keys"]) else "null"
                             for bone, stem in data["java_bones"].items())
        mov_rows = ", ".join("%s_%s_MOVE" % (C, stem) if any(bone + "@move" in k for k in clip["keys"]) else "null"
                             for bone, stem in data["java_bones"].items())
        lines.append("    public static final float[][][] %s_ROT = {%s};" % (C, rot_rows))
        lines.append("    public static final float[][][] %s_MOVE = {%s};" % (C, mov_rows))
    lines.append("}")
    return "\n".join(lines) + "\n"


# ------------------------------------------------------------------ motion -------------------------------------
def rotation_bones(clip):
    return [b for b in bones_of(clip) if "@" not in b]


def motion(data):
    problems, report = [], []
    poses = data.get("poses", {})
    rest = {}
    try:
        with open(os.path.join(ROOT, data["model"])) as f:
            rest = {b["name"]: b.get("rotation", [0, 0, 0]) for b in json.load(f)["bones"]}
    except (OSError, KeyError):
        pass
    for cname, clip in data["clips"].items():
        ticks = clip["ticks"]
        n = ticks * SUB
        samples = [sample(clip, i / n) for i in range(n + 1)]
        dt = 1.0 / SUB                                    # ticks per sample
        bones = rotation_bones(clip)
        speed = {b: [] for b in bones}                    # deg per tick, per sample step
        for b in bones:
            for i in range(n):
                a, c = samples[i][b], samples[i + 1][b]
                speed[b].append(math.sqrt(sum((c[j] - a[j]) ** 2 for j in range(3))) / dt)
        # speed / snap
        for b in bones:
            peak = max(speed[b]) if speed[b] else 0
            report.append("%-10s %-14s peak %6.1f deg/tick (%5.0f deg/s)" % (cname, b, peak, peak * 20))
            if peak > MAX_DEG_PER_TICK:
                problems.append("FAIL speed %s %s: %.0f deg/tick > %.0f" % (cname, b, peak, MAX_DEG_PER_TICK))
            for i in range(1, len(speed[b])):
                jump = abs(speed[b][i] - speed[b][i - 1]) / dt
                if jump > SNAP_DEG_PER_TICK2:
                    problems.append("FAIL snap %s %s at p=%.3f: speed changes %.0f deg/tick per tick"
                                    % (cname, b, i / n, jump))
                    break
        # full stops at inner keys (the speed AT the key, from a tiny step either side)
        eps = 1e-4
        for k in clip["keys"][1:-1]:
            moving = [b for b in bones if max(speed[b]) > 1.0]

            def at_key(b, p=k["p"]):
                lo, hi = sample(clip, p - eps)[b], sample(clip, p + eps)[b]
                return math.sqrt(sum((hi[j] - lo[j]) ** 2 for j in range(3))) / (2 * eps * ticks)
            stopped = [b for b in moving if at_key(b) < 0.05 * max(speed[b])]
            if moving and len(stopped) == len(moving):
                problems.append("WARN stop %s at p=%.2f: every moving bone comes to rest (per-segment easing - "
                                "reads robotic; carry speed through inner keys)" % (cname, k["p"]))
        # range of motion
        for i, s in enumerate(samples):
            for b, lim in list(ROM.items()) + list(ROM_ABS.items()):
                if b not in s:
                    continue
                for ax, (lo, hi) in lim.items():
                    v = s[b]["xyz".index(ax)] + (rest.get(b, [0, 0, 0])["xyz".index(ax)] if b in ROM_ABS else 0)
                    if not lo <= v <= hi:
                        problems.append("FAIL range %s %s.%s = %.0f at p=%.2f (human %d..%d%s)"
                                        % (cname, b, ax, v, i / n, lo, hi, ", with the rest" if b in ROM_ABS else ""))
                        break
        # loop / blend: the clip starts and ends in its pose
        start = clip.get("from")
        if start and start in poses:
            for edge, key in (("start", clip["keys"][0]), ("end", clip["keys"][-1])):
                for b, v in poses[start].items():
                    if key.get(b) != v:
                        problems.append("FAIL loop %s %s %s != pose %s (a pop on the blend)" % (cname, edge, b, start))
        if clip.get("loop"):
            first, last = clip["keys"][0], clip["keys"][-1]
            for b in bones_of(clip):
                if first.get(b) != last.get(b):
                    problems.append("FAIL loop %s: %s differs at p=0 and p=1 (a hitch at the cycle's seam)" % (cname, b))
        # the blow lands in the striking bone's fastest segment
        if "hit_p" in clip and clip.get("strike"):
            b = clip["strike"]
            peak = max(speed[b])
            lo, hi = sample(clip, clip["hit_p"] - eps)[b], sample(clip, clip["hit_p"] + eps)[b]
            at = math.sqrt(sum((hi[j] - lo[j]) ** 2 for j in range(3))) / (2 * eps * ticks)
            report.append("%-10s hit at p=%.2f: %s at %.0f%% of its peak speed" % (cname, clip["hit_p"], b,
                                                                                 100 * at / max(peak, 1e-9)))
            if at < 0.5 * peak:
                problems.append("WARN hit %s: the blow (p=%.2f) lands at %.0f%% of %s's peak speed - a slow frame"
                                % (cname, clip["hit_p"], 100 * at / max(peak, 1e-9), b))
    return problems, report


# ------------------------------------------------------------------ rig + play ---------------------------------
OWNER = (0.23, 0.44, 0.82)      # the team-colour mask is empty here (no team bones), so this tints nothing; render() wants one
CACHE_SOURCES = ("dsl_to_game.py", "materials.py", "unit_light.py", "render_concept.py", "gen_models.py",
                 "preview_model.py")
WEAPON_BONES = ("halberd", "greatsword", "sword", "spear", "staff", "bow", "crossbow", "axe", "mace", "hammer", "weapon")


def cache_dir():
    d = os.environ.get("ANIM_LAB_CACHE") or os.path.join(os.path.expanduser("~"), ".cache", "beyond-and-tabs", "anim_lab")
    os.makedirs(d, exist_ok=True)
    return d


class Rig:
    """A unit's model as the game draws it, ready to pose: the generated body and painted atlas. Painting the atlas is
    11 s of pure Python (dsl_to_game.build, 2142 faces x a PBR pass each) and does not depend on the clip, so it is
    cached on disk keyed by the model file and the sources that paint it (ANIM_LAB_CACHE or ~/.cache/...): the first
    sheet of a session pays it, the rest do not (Oct 22: halberdier --play took > 5 min, most of it repainting)."""

    def __init__(self, model_rel):
        here = os.path.dirname(os.path.abspath(__file__))
        sys.path.insert(0, here)
        import hashlib
        import pickle
        import render_concept
        import dsl_to_game as d2g
        path = os.path.join(ROOT, model_rel)
        self.m = render_concept.load_model(path)
        self.m["_path"] = path
        h = hashlib.sha1()
        with open(path, "rb") as f:
            h.update(f.read())
        for src in CACHE_SOURCES:
            with open(os.path.join(here, src), "rb") as f:
                h.update(f.read())
        h.update(repr(d2g.DENSITY).encode())
        cache = os.path.join(cache_dir(), "%s-%s.pkl" % (os.path.splitext(os.path.basename(path))[0], h.hexdigest()[:16]))
        got = None
        if os.path.exists(cache):
            try:
                with open(cache, "rb") as f:
                    got = pickle.load(f)
            except Exception as e:                       # a torn / stale file: rebuild, never fail on the cache
                print("anim_lab: cache %s unreadable (%s), rebuilding" % (cache, e))
        if got is None:
            _java, imgs, info = d2g.build(self.m, "Preview", [], "preview", d2g.DENSITY)
            got = ({k: imgs[k] for k in ("base", "team", "glow")}, info["root"])
            tmp = cache + ".%d.tmp" % os.getpid()
            with open(tmp, "wb") as f:
                pickle.dump(got, f, protocol=pickle.HIGHEST_PROTOCOL)
            os.replace(tmp, cache)
        self.imgs, self.root = got
        self.density = d2g.DENSITY
        self.base_mc = {nm: d2g.mc_rot(r) for nm, r in self.m["pose"].items()}

    def faces(self, pose, tags=None):
        """The model's faces with a clip pose (DSL degrees ADDED to the model's own pose, moves in px) applied."""
        import dsl_to_game as d2g
        import game_preview as gp
        pmc = dict(self.base_mc)
        move = {}
        for b, v in pose.items():
            if b.endswith("@move"):
                move[b[:-5]] = v
                continue
            cur = pmc.get(b, (0.0, 0.0, 0.0))
            add = d2g.mc_rot(v)
            pmc[b] = tuple(cur[j] + add[j] for j in range(3))
        return gp.faces_of(self.root, pmc, True, False, (), move, tags=tags)

    def framing(self, faces_list, view, frame=None):
        """One framing for a whole clip in one view: the union of every pose's box (no figure shrinks or jumps between
        frames), the band `frame` = (y0, y1) of DSL height cropping it (the Halberdier's 58 px halberd otherwise
        shrinks him to a third of the cell)."""
        import game_preview as gp
        box = None
        for fs in faces_list:
            bb = gp.bbox(fs, gp.VIEWS[view])
            box = bb if box is None else (min(box[0], bb[0]), max(box[1], bb[1]), min(box[2], bb[2]), max(box[3], bb[3]))
        if frame:                                  # the view's y is DSL height (the views are level, y up); the
            box = (box[0], box[1], max(box[2], frame[0]), min(box[3], frame[1]))   # full width: tip to butt
        return box

    def draw(self, fs, view, size, box):
        import game_preview as gp
        return gp.render(fs, self.imgs, self.density, gp.VIEWS[view], size, OWNER, box)[0].convert("RGB")

    @staticmethod
    def crop_of(w, h):
        """A w x h cell is a square render of the longer side with the middle w x h cut out (the box is centred in the
        square): (square size, left, top)."""
        side = max(w, h)
        return side, (side - w) // 2, (side - h) // 2

    def draw_cell(self, fs, view, w, h, box):
        side, left, top = self.crop_of(w, h)
        return self.draw(fs, view, side, box).crop((left, top, left + w, top + h))


def pick_clips(data, names):
    """[(name, clip)] for the requested clip names (all when none); an unknown name is an error that lists the real ones."""
    if not names:
        return list(data["clips"].items())
    out = []
    for n in names:
        if n not in data["clips"]:
            raise SystemExit("anim_lab: no clip %r in %s; clips: %s" % (n, data.get("_rel", "the data"), ", ".join(data["clips"])))
        out.append((n, data["clips"][n]))
    return out


def play(data, out_dir, views=("side", "quarter", "front"), size=260, per_tick=2, frame=None, clips=None):
    """Frame strips and GIFs per clip: the model posed at every half tick through game_preview (the game's own
    generated body and painted texture), one fixed framing per view. frame = (y0, y1) crops the framing to that band
    of DSL height: the data's "frame" or --frame Y0:Y1. A long clip (an 80-tick idle) is played at one frame a tick so
    a strip stays a strip. For a picture to LOOK at use --sheet: this strip is 9000+ px wide and gets downsized."""
    from PIL import Image, ImageDraw
    os.makedirs(out_dir, exist_ok=True)
    rig = Rig(data["model"])
    written = []
    frame = frame or data.get("frame")
    for cname, clip in pick_clips(data, clips):
        pt = per_tick if clip["ticks"] <= 30 else 1
        n = clip["ticks"] * pt
        frames_faces = [rig.faces(sample(clip, i / n)) for i in range(n + 1)]
        rows = []
        for vname in views:
            box = rig.framing(frames_faces, vname, frame)
            frames = [rig.draw(fs, vname, size, box) for fs in frames_faces]
            gif = os.path.join(out_dir, "%s_%s.gif" % (cname, vname))
            frames[0].save(gif, save_all=True, append_images=frames[1:], duration=int(1000 / 20 / pt * 4), loop=0)
            written.append(gif)
            rows.append(frames)
        cols = len(rows[0])
        strip = Image.new("RGB", (cols * size, len(rows) * size + 18), (24, 26, 24))
        d = ImageDraw.Draw(strip)
        d.text((4, 3), "%s: %d ticks, every %s, views %s%s" % (
            cname, clip["ticks"], "half tick" if pt == 2 else "tick", "/".join(views),
            ("  (hit p=%.2f)" % clip["hit_p"]) if "hit_p" in clip else ""), fill=(220, 220, 210))
        for r, frames in enumerate(rows):
            for c, im in enumerate(frames):
                strip.paste(im, (c * size, 18 + r * size))
        sp = os.path.join(out_dir, "%s_strip.png" % cname)
        strip.save(sp)
        written.append(sp)
    return written


def trail_specs(data, clip):
    """{label: (bone, 'far'|'low')} to follow in a clip's motion trail: the data's "trail" ({label: [bone, kind]}), else
    the weapon (the first bone of WEAPON_BONES the clip moves) and the feet (bones named *foot*)."""
    if data.get("trail"):
        return {k: tuple(v) for k, v in data["trail"].items()}
    bones = bones_of(clip)
    out = {}
    for b in bones:
        if b in WEAPON_BONES:
            out[b + " tip"] = (b, "far")
            break
    for b in bones:
        if "foot" in b and "@" not in b:
            out[b.replace("_", " ")] = (b, "low")
    return out


def sheet(data, out_dir, clips=None, n_frames=10, views=("side", "quarter", "front"), frame=None, ref=None, trails=True):
    """ONE png per clip, built to be READ BACK by a Claude session (see tools/anim_sheet.py): key frames of the clip
    in a grid <= 2000 x 2000 px, one view per row, figures 240+ px, the progress and tick under every frame, an orange
    frame on the blow, and a last row of cells with the sweep of the key frames and the paths of the weapon tip and
    the feet. `ref` = a reference image (a frame strip from a video) shown under the grid."""
    import anim_sheet as S
    import game_preview as gp
    from PIL import Image
    os.makedirs(out_dir, exist_ok=True)
    rig = Rig(data["model"])
    frame = frame or data.get("frame")
    ref_img = Image.open(ref) if ref else None
    written = []
    rest_tags = []
    rest = rig.faces({}, tags=rest_tags)
    for cname, clip in pick_clips(data, clips):
        loop = bool(clip.get("loop"))
        ps, hit = S.key_frames(n_frames, loop=loop, hit_p=clip.get("hit_p"))
        specs = trail_specs(data, clip) if trails else {}
        refs = {}
        for label, (bone, kind) in specs.items():
            try:
                refs[label] = S.pick_tracked(rest, rest_tags, bone, kind)
            except KeyError as e:
                print("anim_lab: no trail for %s: %s" % (label, e))
        steps = clip["ticks"] * 2                                    # every half tick: smooth paths, one framing
        dense = [rig.faces(sample(clip, i / steps)) for i in range(steps + 1)]
        keyed = [rig.faces(sample(clip, p)) for p in ps]
        boxes = {v: rig.framing(dense + keyed, v, frame) for v in views}
        # each view's cells take the shape of its figure's box (a side view of a halberdier is 2x wider than tall)
        aspects = [min(1.25, max(0.4, (bx[3] - bx[2]) / max(1e-6, bx[1] - bx[0]))) for bx in (boxes[v] for v in views)]
        lines = [len(ps)] * len(views) + ([len(views)] if refs else [])
        lay = S.layout(lines, ref_lines=1 if ref_img is not None else 0,
                       aspects=aspects + ([max(aspects)] if refs else []))
        rows, trail_cells = [], []
        for vi, vname in enumerate(views):
            box = boxes[vname]
            cells = [rig.draw_cell(fs, vname, lay.cell, lay.heights[vi], box) for fs in keyed]
            rows.append((vname, cells))
            if refs:
                view = gp.VIEWS[vname]
                th = lay.heights[len(views)]
                side, left, top = rig.crop_of(lay.cell, th)

                def px(fs, r, view=view, box=box, side=side, left=left, top=top):
                    x, y = S.to_pixel(S.tracked_point(fs, r), view, box, side)
                    return x - left, y - top
                paths = {lab: [px(fs, r) for fs in dense] for lab, r in refs.items()}
                marks = {lab: [px(fs, r) + (i + 1,) for i, fs in enumerate(keyed)] for lab, r in refs.items()}
                tcell = S.onion(cells if th == lay.heights[vi] else
                                [rig.draw_cell(fs, vname, lay.cell, th, box) for fs in keyed])
                trail_cells.append(S.draw_trails(tcell, paths, marks))
        labels = [S.frame_label(i, p, clip["ticks"], hit == i) for i, p in enumerate(ps)]
        title = "%s / %s   %d ticks (%.2f s)%s%s   views %s   %d frames" % (
            data.get("unit", os.path.basename(data["_rel"])), cname, clip["ticks"], clip["ticks"] / 20.0,
            "  loop" if loop else "", ("   blow at p=%.2f" % clip["hit_p"]) if "hit_p" in clip and not loop else "",
            "/".join(views), len(ps))
        legend = "p = progress through the clip, t = game ticks (20 a second)." + (
            "  Orange frame = the blow.  Last row: sweep of the frames + paths:" if refs else "")
        sh = S.compose(title, legend, rows, lay, labels, hit_index=hit, trail_cells=trail_cells or None,
                       trail_key=list(refs), ref=ref_img)
        out = os.path.join(out_dir, "%s_sheet.png" % cname)
        sh.save(out)
        written.append(out)
    return written


def blend_strip(data, out_dir, views=("side", "quarter"), size=220, ticks=None):
    """The game's blend as one strip: walk (amt 1) -> fading to the idle guard over 6 ticks -> the thrust -> the idle,
    HalberdierUnitModel's own mix (idle by age, walk by the stride, the thrust over the swing), one frame a tick, so
    the hand-offs can be read for a pop. Returns the written paths; the per-tick pose jump of every bone is printed
    (a pop would show as a spike at a hand-off)."""
    from PIL import Image, ImageDraw
    os.makedirs(out_dir, exist_ok=True)
    idle, walk, thrust = data["clips"]["idle"], data["clips"]["walk"], data["clips"]["thrust"]
    phase = walk.get("phase", 1.0)
    # the script: 12 ticks of walk, 8 ticks easing the stride out (amt 1 -> 0), 6 ticks of guard, the thrust
    # (THRUST ticks), 10 ticks of guard
    script = []
    for i in range(12):
        script.append(("walk", 1.0, i))
    for i in range(8):
        script.append(("fade", 1.0 - (i + 1) / 8.0, 12 + i))
    for i in range(6):
        script.append(("idle", 0.0, 20 + i))
    for i in range(thrust["ticks"]):
        script.append(("thrust", 0.0, 26 + i))
    for i in range(10):
        script.append(("idle", 0.0, 26 + thrust["ticks"] + i))
    poses = []
    for kind, amt, tick in script:
        a = sample(idle, (tick / idle["ticks"]) % 1.0)
        if kind in ("walk", "fade"):
            q = (tick * 0.4 * 0.6662 / (2 * math.pi) * phase) % 1.0   # vanilla: limbSwing += 4 x blocks a tick; 0.1 b/t
            b = sample(walk, q)
            wk = smooth(amt)
            a = {k: [a[k][i] + (b.get(k, a[k])[i] - a[k][i]) * wk for i in range(3)] for k in a}
        elif kind == "thrust":
            q = (tick - 26) / thrust["ticks"]
            b = sample(thrust, q)
            ready = sample(idle, 0.0)
            k = 1.0 - smooth(min(1.0, q / IDLE_FADE, (1.0 - q) / IDLE_FADE))   # the idle's drift fades out / in
            a = {kk: [b[kk][i] + (a[kk][i] - ready[kk][i]) * k for i in range(3)] for kk in b}
        poses.append((kind, a))
    # the per-tick jump: the largest angle any bone moves between two frames, per hand-off
    for i in range(1, len(poses)):
        pa, pb = poses[i - 1][1], poses[i][1]
        worst = max((math.sqrt(sum((pb[k][j] - pa[k][j]) ** 2 for j in range(3))), k) for k in pa if "@" not in k)
        if poses[i][0] != poses[i - 1][0] or i == 1:
            print("  blend %-6s -> %-6s at tick %2d: biggest step %5.1f deg (%s)" % (
                poses[i - 1][0], poses[i][0], i, worst[0], worst[1]))
    rig = Rig(data["model"])
    frames_faces = [rig.faces(pose) for kind, pose in poses]
    frame = data.get("frame")
    rows = []
    for vname in views:
        box = rig.framing(frames_faces, vname, frame if vname in ("side", "quarter", "front") else None)
        rows.append([rig.draw(fs, vname, size, box) for fs in frames_faces])
    cols = len(poses)
    strip = Image.new("RGB", (cols * size, len(rows) * size + 18), (24, 26, 24))
    d = ImageDraw.Draw(strip)
    d.text((4, 3), "blend: walk -> (stride fades) -> ready idle -> thrust -> ready idle, one frame a tick, views %s"
           % "/".join(views), fill=(220, 220, 210))
    for r, frames in enumerate(rows):
        for c, im in enumerate(frames):
            strip.paste(im, (c * size, 18 + r * size))
            if r == 0:
                d.text((c * size + 3, 20), poses[c][0], fill=(240, 240, 230))
    sp = os.path.join(out_dir, "blend_strip.png")
    strip.save(sp)
    gif = os.path.join(out_dir, "blend_side.gif")
    rows[0][0].save(gif, save_all=True, append_images=rows[0][1:], duration=100, loop=0)
    return [sp, gif]


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("data")
    ap.add_argument("--gen-java", action="store_true")
    ap.add_argument("--check", action="store_true", help="exit 1 if the generated Java differs from the data")
    ap.add_argument("--motion", action="store_true")
    ap.add_argument("--play", metavar="OUT_DIR")
    ap.add_argument("--sheet", metavar="OUT_DIR", help="ONE png per clip to look at (<= 2000 px): key frames, one view a "
                                                       "row, the blow marked, motion trails; <clip>_sheet.png")
    ap.add_argument("--clip", action="append", help="only this clip (repeat for several); default every clip")
    ap.add_argument("--frames", type=int, default=10, help="key frames per view on a sheet (default 10)")
    ap.add_argument("--ref", metavar="IMAGE", help="a reference image (a frame strip) shown under the sheet")
    ap.add_argument("--no-trail", action="store_true", help="leave the motion-trail row off the sheet")
    ap.add_argument("--frame", metavar="Y0:Y1", help="crop the strips to this band of DSL height (default: all)")
    ap.add_argument("--blend", metavar="OUT_DIR", help="the game's walk -> ready -> thrust -> ready blend as a strip")
    ap.add_argument("--views", default="side,quarter,front", help="views for --play / --blend (game_preview.VIEWS)")
    a = ap.parse_args()
    data = load(a.data)
    data["_rel"] = os.path.relpath(os.path.abspath(a.data), ROOT)
    java_path = os.path.join(GEN_DIR, data["java_class"] + ".java")
    rc = 0
    if a.gen_java:
        with open(java_path, "w") as f:
            f.write(gen_java(data))
        print("wrote", os.path.relpath(java_path, ROOT))
    if a.check:
        cur = open(java_path).read() if os.path.exists(java_path) else ""
        if cur != gen_java(data):
            print("FAIL %s differs from %s - run --gen-java" % (os.path.relpath(java_path, ROOT), data["_rel"]))
            rc = 1
        else:
            print("ok   %s matches %s" % (os.path.relpath(java_path, ROOT), data["_rel"]))
    if a.motion:
        problems, report = motion(data)
        for line in report:
            print("     " + line)
        for p in problems:
            print(p)
        if any(p.startswith("FAIL") for p in problems):
            rc = 1
    views = tuple(v for v in a.views.split(",") if v)
    frame = tuple(float(v) for v in a.frame.split(":")) if a.frame else None
    if a.sheet:
        for p in sheet(data, a.sheet, clips=a.clip, n_frames=a.frames, views=views, frame=frame, ref=a.ref,
                       trails=not a.no_trail):
            print("wrote", p)
    if a.play:
        for p in play(data, a.play, views=views, frame=frame, clips=a.clip):
            print("wrote", p)
    if a.blend:
        for p in blend_strip(data, a.blend, views=views):
            print("wrote", p)
    sys.exit(rc)


if __name__ == "__main__":
    main()
