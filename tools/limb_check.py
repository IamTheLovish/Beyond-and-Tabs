#!/usr/bin/env python3
"""The anatomy check (claude/art-theory.md, the technical artist's checks): numbers for what the colour, value and
silhouette checks never measured - how the limbs are posed. Born Oct 15 after lovish saw what no metric did: "the arms
look bent", "the feet look odd" - every solved elbow bent BACKWARD in its own frame (the arm solver searched the
wrong sign) and every foot rode its shin, tilting with the leg, toes locked straight ahead.

    python3 tools/limb_check.py design/models/sunforged_*_v2.json design/models/sunforged_paladin_radiant_winged.json

Per arm ("<side>_arm" + "<side>_fore"): where the elbow sits against the shoulder, the bend, the reach against the
arm's length. Per foot ("<side>_foot", else the "<side>_shin" that carries it): the sole's pitch and the toe's turn.
Flags (the exit status is 1 if any FAIL):
  FAIL elbow backward  - the forearm's own x < 0: in the DSL x > 0 swings a hanging part forward, so natural flexion
                         is positive; negative is a hyperextended elbow (the "chicken wing" when the upper arm is
                         raised to make up for it)
  FAIL elbow high      - the elbow above its shoulder at rest (a raised arm belongs in an animation key, not the
                         rest pose; a pose that truly wants it lists the bone in the model's "limb_ok")
  FAIL foot tilted     - the sole more than 3 degrees off level (heel up or toe dug in)
  warn arm locked      - the hand at >= 98% of the arm's length with under 5 degrees of bend (a rod, not an arm)
  warn toes parallel   - both feet turned out less than 3 degrees (people stand toes out; parallel reads robotic)
The game's animation adds to these rest angles, so the rest pose is what has to be right.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import render_concept  # noqa: E402
from gen_models import rotate_about  # noqa: E402


def _bones(m):
    return {b["name"]: b for b in m["bones"]}


def _world(m, bones, name, p):
    """A rest-space point of a bone in the posed model (its rotation and every ancestor's, plus the model's pose)."""
    b = bones[name]
    pose = m.get("pose", {})
    while b is not None:
        rot = [b.get("rotation", [0, 0, 0])[a] + pose.get(b["name"], [0, 0, 0])[a] for a in range(3)]
        p = rotate_about(b["pivot"], rot, p)
        b = bones.get(b.get("parent"))
    return p


def _sub(a, b):
    return [a[i] - b[i] for i in range(3)]


def _norm(v):
    return math.sqrt(sum(x * x for x in v))


def check(m):
    """[(level, text)] for one loaded model; level is "ok", "warn" or "FAIL"."""
    bones = _bones(m)
    ok_list = set(m.get("limb_ok", []))
    pose = m.get("pose", {})
    out = []
    for side in ("left", "right"):
        up, fo = side + "_arm", side + "_fore"
        if up not in bones or fo not in bones or not bones[fo].get("cubes"):
            continue
        S = _world(m, bones, up, bones[up]["pivot"])
        E = _world(m, bones, fo, bones[fo]["pivot"])
        # the wrist: the bottom of the forearm's own cubes - thin props hung from the hand (a vial, a bolt: under 1 px
        # across in both x and z) do not lengthen the arm (the Medic's vial read her arm as 12.8 px against 9.7); an
        # octagon limb's crossed boxes are thin one way only and still count
        limb = [c for c in bones[fo]["cubes"] if max(c["size"][0], c["size"][2]) >= 1.0] or bones[fo]["cubes"]
        wy = min(c["from"][1] for c in limb)
        W = _world(m, bones, fo, (bones[fo]["pivot"][0], wy, bones[fo]["pivot"][2]))
        u, f = _sub(E, S), _sub(W, E)
        lu, lf = _norm(u), _norm(f)
        bend = math.degrees(math.acos(max(-1.0, min(1.0, sum(u[i] * f[i] for i in range(3)) / (lu * lf)))))
        fx = bones[fo].get("rotation", [0, 0, 0])[0] + pose.get(fo, [0, 0, 0])[0]
        reach = _norm(_sub(W, S))
        text = "%-5s arm  elbow %+.1f px from the shoulder, bend %3.0f, reach %.1f of %.1f" % (
            side, E[1] - S[1], bend, reach, lu + lf)
        level = "ok"
        if fx < -0.5:
            level, text = "FAIL", text + "  <- elbow backward (forearm x %.0f)" % fx
        elif E[1] > S[1] and up not in ok_list:
            level, text = "FAIL", text + "  <- elbow high"
        elif reach >= 0.98 * (lu + lf) and bend < 5:
            level, text = "warn", text + "  <- arm locked"
        out.append((level, text))
    turns = []
    for side in ("left", "right"):
        name = side + "_foot" if side + "_foot" in bones else side + "_shin"
        if name not in bones or not bones[name].get("cubes"):
            continue
        lo = min(bones[name]["cubes"], key=lambda c: c["from"][1])
        (x0, y0, z0), (sx, _sy, sz) = lo["from"], lo["size"]
        heel = _world(m, bones, name, (x0 + sx / 2, y0, z0 + sz))
        toe = _world(m, bones, name, (x0 + sx / 2, y0, z0))
        pitch = math.degrees(math.atan2(heel[1] - toe[1], heel[2] - toe[2]))
        turn = math.degrees(math.atan2(toe[0] - heel[0], heel[2] - toe[2])) * (1 if side == "left" else -1)
        turns.append(turn)
        text = "%-5s foot sole %+.1f deg (heel up +), toes out %+.1f deg" % (side, pitch, turn)
        level = "ok"
        if abs(pitch) > 3.0 and name not in ok_list:
            level, text = "FAIL", text + "  <- foot tilted"
        out.append((level, text))
    if len(turns) == 2 and max(turns) < 3.0:
        out.append(("warn", "feet  toes parallel (people stand toes out)"))
    return out


def main():
    paths = sys.argv[1:]
    if not paths:
        print(__doc__)
        return 2
    failed = False
    for path in paths:
        m = render_concept.load_model(path)
        print("==", os.path.basename(path))
        for level, text in check(m):
            print("  %-4s %s" % (level, text))
            failed |= level == "FAIL"
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
