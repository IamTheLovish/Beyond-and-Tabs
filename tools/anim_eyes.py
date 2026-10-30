#!/usr/bin/env python3
"""One command to LOOK at a unit's animation (lovish Oct 22 20:23: "find a way to test animations in the cloud somewhere
you can simulate them and see them with your own eyes").

    python3 tools/anim_eyes.py <unit> <clip>            # offline: units with a data file in design/anims/ (seconds)
    python3 tools/anim_eyes.py <unit> <clip> --ci       # the REAL game on a CI runner: any unit (~7 min, one job)

prints  `sheet <path>`  for each sheet; Read the png. A sheet is one image <= 2000 x 2000 px: ~10 frames per view, one
view (take) per row, the time under every frame (tools/anim_sheet.py draws it).

unit   an entity id (bone_dragon_unit), or the short name (halberdier -> halberdier_unit; the offline data also knows
       the unit by its design name)
clip   idle | walk (fly is the same) | attack | turn | death
         idle    stands            walk    ordered far away, photographed at cruise
         attack  a training dummy beside it (AI off), attack-move, a new dummy whenever one dies; the label has the
                 unit's swing progress (sw=0..1)
         turn    three legs of a route, a left turn then a right one (a flier banks into each)
         death   killed through the game's own kill path; the frames end when the corpse is removed
       offline, `attack` is the data file's clip with a blow (thrust) and the clip names of the file work as they are

--ci   dispatch .github/workflows/eyes.yml (input `anim`) on the current branch (--branch to change; it must be pushed
       and its PR build must have a jar), wait, fetch the sheets from branch eyes-anim (folder anim-<run id>). The client plays EyesAnimScene.java: a real
       entity doing the clip on a flat pad, a first-person camera locked to it, a screenshot every few game ticks.
       The jar tested is the branch's PR build (artifact bar-mod), so push first.
--views side+front+rear   which takes (rows); default all three (each is one more unit on the pad, ~1 min of CI)
--dist N   camera distance in blocks (default from the unit's size)      --ticks N   the recording window
--frames N   frames per row on the sheet (default 10)                     --crop F   share of the screen height kept (0.6)
--out DIR    where sheets go (default $ANIM_EYES_OUT or /tmp/anim-eyes)

The runner calls the same file:  --collect FRAMES_DIR --spec SPEC [--log latest.log] --out DIR  (frames + log -> sheets).
Tested by tools/test_anim_eyes.py (written first, red, then this).
"""
import argparse
import datetime
import json
import os
import re
import subprocess
import sys
import tempfile
import time
from typing import NamedTuple, Optional

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)

CLIPS = ("idle", "walk", "attack", "turn", "death")
VIEWS = ("side", "front", "rear")
KNOWN_VIEWS = VIEWS + ("rts",)   # rts: fixed to the world, asked for by name only (EyesAnimScene)
# the recording window per clip in game ticks - the same numbers as EyesAnimScene.parse
DEFAULT_TICKS = {"idle": 120, "walk": 80, "attack": 100, "turn": 240, "death": 44}
FRAMES_PER_RUN = 36           # EyesAnimScene.FRAMES: captures per take
TICKS_PER_SECOND = 20.0
WORKFLOW = "eyes.yml"
SPEC_OK = re.compile(r"^[a-z0-9_:+.,]+$")


# ------------------------------------------------------------------ the property both sides parse --------------
class Run(NamedTuple):
    unit: str
    clip: str
    views: tuple
    dist: float
    rec: int


def unit_id(name):
    """halberdier -> halberdier_unit; reignofnether:zombie_unit -> zombie_unit (the entity registry path)."""
    n = name.strip().lower()
    if ":" in n:
        n = n.split(":", 1)[1]
    return n if n.endswith("_unit") else n + "_unit"


def parse_spec(spec):
    """-Dbat.eyes.anim=unit:clip[:views[:dist[:ticks]]],... as EyesAnimScene.parse reads it (kept identical: the run
    numbers in the log are positions in this expansion)."""
    runs = []
    for entry in spec.split(","):
        entry = entry.strip()
        if not entry:
            continue
        p = entry.split(":")
        i = 0
        unit = p[i]
        i += 1
        if unit == "reignofnether" and len(p) > 1:
            unit = p[i]
            i += 1
        clip = p[i] if i < len(p) else "idle"
        i += 1
        if clip == "fly":
            clip = "walk"
        if clip not in CLIPS:
            raise ValueError("unknown clip %r in %r; clips: %s" % (clip, entry, ", ".join(CLIPS)))
        views = tuple(v for v in (p[i] if i < len(p) else "side+front+rear").split("+") if v in KNOWN_VIEWS)
        i += 1
        views = views or VIEWS
        dist, rec = 0.0, 0
        if i < len(p) and p[i]:
            dist = float(p[i])
        i += 1
        if i < len(p) and p[i]:
            rec = int(p[i])
        runs.append(Run(unit, clip, views, dist, rec if rec > 0 else DEFAULT_TICKS[clip]))
    return runs


def steps(runs):
    """[(run index, Run, view)] in the order the scene plays them: step number = position in this list."""
    return [(ri, r, v) for ri, r in enumerate(runs) for v in r.views]


def build_spec(unit, clip, views=VIEWS, dist=0.0, ticks=0):
    return "%s:%s:%s:%s:%d" % (unit_id(unit), clip, "+".join(views), ("%g" % dist) if dist else "0", ticks)


def safe_spec(spec):
    """The spec goes into a JVM flag on the runner: only the characters a spec is made of."""
    return bool(SPEC_OK.fullmatch(spec))      # fullmatch: "$" would let a trailing newline through


# ------------------------------------------------------------------ the scene's log ---------------------------
class Frame(NamedTuple):
    step: int
    view: str
    idx: int
    file: str
    t: float                       # game time in ticks (with the partial tick)
    swing: Optional[float]
    limb: Optional[float]
    yaw: Optional[float]


class Log:
    def __init__(self):
        self.frames = {}           # step -> [Frame] in capture order
        self.runs = {}             # step -> {"unit", "clip", "view", "window", "start"}
        self.notes = []            # the scene's own lines (problems among them)


_FRAME = re.compile(r"\[Eyes\] anim-frame (\d+) (\w+) (\d+) (\S+) t=(-?[\d.,]+)(.*)$")
_RUN = re.compile(r"\[Eyes\] anim-run (\d+) (\S+) (\S+) (\S+) window (\d+) ticks from game tick (-?\d+)")
_NOTE = re.compile(r"\[Eyes\] (20_anim.*|anim-run-end.*|anim-leg.*)$")


def _num(text, key):
    m = re.search(r"\b%s=(-?[\d.,]+)" % key, text)
    if not m:
        return None
    try:
        return float(m.group(1).replace(",", "."))
    except ValueError:
        return None


def parse_log(text):
    log = Log()
    seen = set()
    for ln in text.splitlines():
        m = _FRAME.search(ln)
        if m:
            step, idx = int(m.group(1)), int(m.group(3))
            if (step, idx) in seen:
                continue
            seen.add((step, idx))
            rest = m.group(6)
            log.frames.setdefault(step, []).append(Frame(
                step, m.group(2), idx, m.group(4), float(m.group(5).replace(",", ".")),
                _num(rest, "swing"), _num(rest, "limb"), _num(rest, "yaw")))
            continue
        m = _RUN.search(ln)
        if m:
            log.runs[int(m.group(1))] = {"unit": m.group(2), "clip": m.group(3), "view": m.group(4),
                                         "window": int(m.group(5)), "start": int(m.group(6))}
        m = _NOTE.search(ln)
        if m and m.group(1) not in log.notes:
            log.notes.append(m.group(1))
    return log


# ------------------------------------------------------------------ which frames, how cropped ------------------
def pick(frames, n):
    """n captures spread evenly over the window by their game time (captures are unevenly spaced on a software GL
    client, so picking by index would make the sheet's time axis lie); all of them when there are fewer."""
    import anim_sheet as S
    if not frames:
        return []
    ts = [f.t for f in frames]
    t0, t1 = min(ts), max(ts)
    targets = [t0 + (t1 - t0) * k / max(1, n - 1) for k in range(n)]
    return [frames[i] for i in S.select_nearest(ts, targets)]


def seconds_of(frame, first):
    return (frame.t - first.t) / TICKS_PER_SECOND


def crop_box(size, frac):
    """The centre square of the screen holding `frac` of its height: the camera aims at the unit's middle."""
    w, h = size
    side = max(1, min(min(w, h), round(min(w, h) * frac)))
    left, top = (w - side) // 2, (h - side) // 2
    return (left, top, left + side, top + side)


class CollectError(Exception):
    def __init__(self, msg, sheets=()):
        super().__init__(msg)
        self.sheets = list(sheets)


_FILE = re.compile(r"^bat_eyes_20_anim_r(\d+)_(\w+)_f(\d+)\.png$")


def _frames_for(frames_dir, step, view, log, rec):
    """The log's frames of a step that exist on disk; with no log lines, the files themselves at an even spacing."""
    got = [f for f in log.frames.get(step, []) if os.path.exists(os.path.join(frames_dir, f.file))]
    if got:
        return got, False
    out = []
    for name in sorted(os.listdir(frames_dir)):
        m = _FILE.match(name)
        if m and int(m.group(1)) == step and m.group(2) == view:
            idx = int(m.group(3))
            out.append(Frame(step, view, idx, name, idx * rec / FRAMES_PER_RUN, None, None, None))
    return out, bool(out)


def collect(frames_dir, spec, log_text, out_dir, n=10, crop=0.6):
    """Frames + the scene's log -> one sheet per (unit, clip) in out_dir. Returns the sheet paths; a take that produced
    nothing raises CollectError at the end (naming what the scene logged about it) with .sheets = the good ones."""
    import anim_sheet as S
    from PIL import Image
    os.makedirs(out_dir, exist_ok=True)
    runs = parse_spec(spec)
    log = parse_log(log_text)
    allsteps = steps(runs)
    sheets, problems, warnings, used = [], [], [], set()
    for ri, run in enumerate(runs):
        rows = []
        for si, (rj, _r, view) in enumerate(allsteps):
            if rj != ri:
                continue
            fs, assumed = _frames_for(frames_dir, si, view, log, run.rec)
            if not fs:
                problems.append("run %d (%s %s, view %s): no frames were captured" % (si, run.unit, run.clip, view))
                for note in log.notes:
                    if re.search(r"\brun %d\b" % si, note) or run.unit in note:
                        problems.append("    scene: " + note)
                continue
            if assumed:
                warnings.append("run %d: no log lines, frame times assumed evenly spaced" % si)
            chosen = pick(sorted(fs, key=lambda f: (f.t, f.idx)), n)
            rows.append((view, chosen, assumed))
        if not rows:
            continue
        # a sheet's geometry: rows x n, square cells
        count = max(len(c) for _v, c, _a in rows)
        lay = S.layout([count] * len(rows))
        view_rows, cap_rows = [], []
        base = "%s_%s" % (run.unit, run.clip)
        name, k = base, 1
        while name in used:
            k += 1
            name = "%s_%d" % (base, k)
        used.add(name)
        frame_dir = os.path.join(out_dir, name)
        os.makedirs(frame_dir, exist_ok=True)
        for view, chosen, _assumed in rows:
            cells, caps = [], []
            first = chosen[0]
            for ci, f in enumerate(chosen):
                im = Image.open(os.path.join(frames_dir, f.file)).convert("RGB")
                c = im.crop(crop_box(im.size, crop))
                cell = c.resize((lay.cell, lay.cell), Image.LANCZOS)
                cells.append(cell)
                cap = S.time_label(ci, seconds_of(f, first))
                if run.clip == "attack" and f.swing is not None:
                    cap += "  sw=%.1f" % f.swing
                caps.append(cap)
                c.resize((min(c.size[0], 540),) * 2, Image.LANCZOS).save(
                    os.path.join(frame_dir, "%s_f%02d.png" % (view, ci + 1)))
            view_rows.append((view, cells))
            cap_rows.append(caps)
        window = next((log.runs[si]["window"] for si, (rj, _r, _v) in enumerate(allsteps) if rj == ri and si in log.runs), run.rec)
        title = "%s / %s   real game client   window %d ticks (%.1f s)   views %s   %d frames" % (
            run.unit, run.clip, window, window / TICKS_PER_SECOND, "/".join(v for v, _c, _a in rows), count)
        legend = ("Each row is a separate take (a column is NOT the same instant); the camera follows the unit.  "
                  "t = game ticks into the window (20 a second)." + ("  sw = swing progress." if run.clip == "attack" else ""))
        sheet = S.compose(title, legend, view_rows, lay, cap_rows)
        path = os.path.join(out_dir, name + "_sheet.png")
        sheet.save(path)
        with open(os.path.join(out_dir, name + "_sheet.json"), "w") as f:
            json.dump({"title": title, "views": [v for v, _c, _a in rows], "frames": count, "labels": cap_rows[0],
                       "labels_by_row": cap_rows, "size": list(sheet.size), "warnings": warnings}, f, indent=1)
        sheets.append(path)
    with open(os.path.join(out_dir, "anim-lines.txt"), "w") as f:
        f.write("\n".join(log.notes + warnings) + "\n")
    if problems:
        raise CollectError("; ".join(problems) if len(problems) < 2 else "\n" + "\n".join(problems), sheets)
    return sheets


def describe(sheet_path):
    """What collect wrote about a sheet (title, views, frames, captions): the sidecar next to it."""
    with open(sheet_path[:-len(".png")] + ".json") as f:
        meta = json.load(f)
    return meta


# ------------------------------------------------------------------ CI: dispatch, wait, download --------------
class CiError(Exception):
    pass


def real_gh(args):
    p = subprocess.run(["gh"] + list(args), capture_output=True, text=True, timeout=300, cwd=ROOT)
    if p.returncode != 0:
        raise RuntimeError((p.stderr or p.stdout).strip() or "gh exited %d" % p.returncode)
    return p.stdout


def dispatch_args(spec, ref):
    if not safe_spec(spec):
        raise ValueError("unsafe animation spec %r (letters, digits, _ : + . , only)" % spec)
    return ["workflow", "run", WORKFLOW, "--ref", ref, "-f", "anim=" + spec]


def list_args(ref):
    return ["run", "list", "--workflow", WORKFLOW, "--branch", ref, "--event", "workflow_dispatch", "--limit", "15",
            "--json", "databaseId,createdAt,status,conclusion,url"]


def pick_run(runs, since_iso):
    """The first run created at or after `since_iso` (ours: the earliest dispatch after we asked)."""
    later = [r for r in runs if r["createdAt"] >= since_iso]
    return min(later, key=lambda r: r["createdAt"]) if later else None


def wait_run(run_id, gh=real_gh, sleep=time.sleep, timeout=2700, poll=20, now=time.time):
    start = now()
    while True:
        info = json.loads(gh(["run", "view", str(run_id), "--json", "status,conclusion,url"]))
        if info.get("status") == "completed":
            return info
        if now() - start > timeout:
            raise CiError("run %s still %s after %d s: %s" % (run_id, info.get("status"), timeout, info.get("url", "")))
        sleep(poll)


BRANCH = "eyes-anim"          # where the runner publishes folder anim-<run id>/ (eyes.yml)


def list_folder_args(run_id):
    return ["api", "repos/{owner}/{repo}/contents/anim-%d?ref=%s" % (run_id, BRANCH)]


def raw_args(path):
    return ["api", "-H", "Accept: application/vnd.github.raw", "repos/{owner}/{repo}/contents/%s?ref=%s" % (path, BRANCH)]


def real_gh_raw(args):
    p = subprocess.run(["gh"] + list(args), capture_output=True, timeout=300, cwd=ROOT)
    if p.returncode != 0:
        raise RuntimeError((p.stderr or p.stdout).decode("utf-8", "replace").strip() or "gh exited %d" % p.returncode)
    return p.stdout


def download(run_id, dest, gh=real_gh, raw=real_gh_raw):
    """Fetch the run's published folder from branch eyes-anim (the Actions artifact host is not reachable from a Claude
    session's sandbox; the contents API is). Top-level files only - the sheets, their json, the log; the kept frames
    (sub-folders) are on the branch for anyone who wants one. Returns the local paths."""
    try:
        listing = json.loads(gh(list_folder_args(run_id)))
    except Exception as e:
        raise CiError("no folder anim-%d on branch %s (%s) - the job died before it published; its log has why"
                      % (run_id, BRANCH, e)) from e
    os.makedirs(dest, exist_ok=True)
    out = []
    for ent in listing:
        if ent.get("type") != "file":
            continue
        try:
            data = raw(raw_args(ent["path"]))
        except Exception as e:
            raise CiError("could not fetch %s from %s: %s" % (ent["path"], BRANCH, e)) from e
        path = os.path.join(dest, ent["name"])
        with open(path, "wb") as f:
            f.write(data)
        out.append(path)
    return out


def _utc_now():
    return datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def run_ci(spec, branch, out_dir, gh=real_gh, timeout_min=45):
    since = (datetime.datetime.now(datetime.timezone.utc) - datetime.timedelta(seconds=3)).strftime("%Y-%m-%dT%H:%M:%SZ")
    gh(dispatch_args(spec, branch))
    print("dispatched eyes.yml anim=%s on %s" % (spec, branch), flush=True)
    run = None
    for _ in range(30):
        time.sleep(4)
        run = pick_run(json.loads(gh(list_args(branch))), since)
        if run:
            break
    if not run:
        raise CiError("the dispatch did not show up as a run of eyes.yml on %s after 2 min (is the branch pushed, "
                      "and does the workflow file there have the `anim` input?)" % branch)
    rid = run["databaseId"]
    print("run %d  %s" % (rid, run.get("url", "")), flush=True)
    t0 = time.time()
    info = wait_run(rid, gh, timeout=timeout_min * 60)
    print("finished: %s after %.1f min" % (info.get("conclusion"), (time.time() - t0) / 60), flush=True)
    dest = os.path.join(out_dir, "ci-%d" % rid)
    download(rid, dest, gh)
    return rid, info, dest


# ------------------------------------------------------------------ units with offline data -------------------
def offline_data(unit):
    """Path of the design/anims/*.json for this unit (by file name, or by the entity id in its "unit" text)."""
    uid = unit_id(unit)
    stem = uid[:-len("_unit")]
    anims = os.path.join(ROOT, "design", "anims")
    if not os.path.isdir(anims):
        return None
    for fn in sorted(os.listdir(anims)):
        if not fn.endswith(".json"):
            continue
        path = os.path.join(anims, fn)
        if fn[:-5] == stem:
            return path
        try:
            with open(path) as f:
                name = json.load(f).get("unit", "")
        except (OSError, ValueError):
            continue
        if "(%s)" % uid in name:
            return path
    return None


def offline_clip(data, clip):
    clips = data["clips"]
    if clip in clips:
        return clip
    if clip == "attack":
        return next((n for n, c in clips.items() if "hit_p" in c), None)
    return None


# ------------------------------------------------------------------ command line ------------------------------
def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("unit", nargs="?")
    ap.add_argument("clip", nargs="?", default="idle")
    ap.add_argument("--ci", action="store_true", help="play it in the real game on a CI runner (any unit)")
    ap.add_argument("--branch", help="the pushed branch to dispatch on (default: the current one)")
    ap.add_argument("--views", default="+".join(VIEWS))
    ap.add_argument("--dist", type=float, default=0.0)
    ap.add_argument("--ticks", type=int, default=0)
    ap.add_argument("--frames", type=int, default=10)
    ap.add_argument("--crop", type=float, default=0.6)
    ap.add_argument("--out", default=os.environ.get("ANIM_EYES_OUT") or os.path.join(tempfile.gettempdir(), "anim-eyes"))
    ap.add_argument("--timeout", type=int, default=45, help="minutes to wait for the run (default 45)")
    ap.add_argument("--collect", metavar="FRAMES_DIR", help="(the runner) frames + --spec + --log -> sheets in --out")
    ap.add_argument("--spec")
    ap.add_argument("--log")
    a = ap.parse_args(argv)

    if a.collect:
        if not a.spec:
            ap.error("--collect needs --spec")
        text = ""
        if a.log and os.path.exists(a.log):
            with open(a.log, errors="replace") as f:
                text = f.read()
        try:
            sheets = collect(a.collect, a.spec, text, a.out, n=a.frames, crop=a.crop)
            err = None
        except CollectError as e:
            sheets, err = e.sheets, e
        for s in sheets:
            print("sheet", s)
        if err:
            print("problems: %s" % err, file=sys.stderr)
            return 1
        return 0

    if not a.unit:
        ap.error("a unit is needed (e.g. halberdier idle, or bone_dragon_unit turn --ci)")
    uid = unit_id(a.unit)
    if a.ci:
        if a.clip not in CLIPS and a.clip != "fly":
            print("anim_eyes: unknown clip %r; clips: %s" % (a.clip, ", ".join(CLIPS)), file=sys.stderr)
            return 2
        views = tuple(v for v in a.views.split("+") if v in KNOWN_VIEWS) or VIEWS
        spec = build_spec(uid, "walk" if a.clip == "fly" else a.clip, views, a.dist, a.ticks)
        branch = a.branch or subprocess.run(["git", "rev-parse", "--abbrev-ref", "HEAD"], capture_output=True, text=True,
                                            cwd=ROOT).stdout.strip()
        os.makedirs(a.out, exist_ok=True)
        try:
            rid, info, dest = run_ci(spec, branch, a.out, timeout_min=a.timeout)
        except (CiError, RuntimeError, ValueError) as e:
            print("anim_eyes: %s" % e, file=sys.stderr)
            return 1
        found = []
        for dp, _dn, fns in os.walk(dest):
            found += [os.path.join(dp, f) for f in sorted(fns) if f.endswith("_sheet.png")]
        for s in found:
            print("sheet", s)
        if info.get("conclusion") != "success":
            print("note: the run ended %s (%s); the frames are real, read the annotations on the run for what failed"
                  % (info.get("conclusion"), info.get("url", "")), file=sys.stderr)
        if not found:
            print("anim_eyes: the run's eyes-anim folder held no sheet - see %s" % dest, file=sys.stderr)
            return 1
        return 0

    path = offline_data(uid)
    if path is None:
        have = sorted(fn[:-5] for fn in os.listdir(os.path.join(ROOT, "design", "anims")) if fn.endswith(".json"))
        print("anim_eyes: no offline animation data for %s (design/anims has: %s). Units animated in Java are played by "
              "the real game: add --ci" % (uid, ", ".join(have)), file=sys.stderr)
        return 2
    import anim_lab
    data = anim_lab.load(path)
    data["_rel"] = os.path.relpath(path, ROOT)
    name = offline_clip(data, a.clip)
    if name is None:
        print("anim_eyes: %s has no clip %r offline (clips: %s); add --ci to play it in the real game"
              % (os.path.basename(path), a.clip, ", ".join(data["clips"])), file=sys.stderr)
        return 2
    os.makedirs(a.out, exist_ok=True)
    for p in anim_lab.sheet(data, a.out, clips=[name], n_frames=a.frames):
        print("sheet", p)
    return 0


if __name__ == "__main__":
    sys.exit(main())
