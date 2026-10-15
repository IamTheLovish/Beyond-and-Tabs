#!/usr/bin/env python3
"""The contact check (claude/art-theory.md, the technical artist's checks; with tools/limb_check.py): what passes
through what, at rest and at the animation's extremes. Born Oct 15 when four fresh-eyes reviews found what no check
of ours measured: the Squire's pennant pole through his helm at rest, the Paladin's forearms through his helm on every
attack and through his belly at rest, knees through tabards in the brace, a crossbow sunk in a belly while spanning.

    python3 tools/contact_check.py design/models/sunforged_squire_runner_v2.json [--poses @design/poses/x.json]
        [--threshold 0.6] [--joint 1.2]

Every visible cube becomes an oriented box in the posed model; every pair of cubes from DIFFERENT bones that
interpenetrates deeper than --threshold px is reported - except at a joint: between a bone and the bone it hangs from
(its parent or grandparent; a root bone's host is the root whose cubes hold its pivot - the body for the thighs), an
overlap within the joint's radius of the child's pivot is how the joint is built (an upper arm inside its pauldron,
a thigh in the hip, a neck in the gorget). Radii by joint: shoulder 4, hip 3, knee 3, neck 2.5, elbow 2.5, wing
root 2.5, ankle 2, else --joint. Anything else
- a prop through a head, a knee through a tabard, a forearm in a belly - is reported. A model may list bone pairs whose contact is intended in "contact_ok" (a weapon held in its
gauntlets) - Model.contact_ok in tools/gen_models.py writes it. Poses: a JSON {name: {bone: [x, y, z] DSL degrees
added, "_hide": [bones], "_move": {bone: [dx, dy, dz] DSL px}}} or @file; a "rest" entry (e.g. hiding the weapon
the unit is not holding) replaces the plain rest pose, which is otherwise checked first.
Exit status 1 if anything is reported.
"""
import argparse
import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import render_concept  # noqa: E402
from gen_models import rot_mat  # noqa: E402


def _frames(m, extra, move):
    """bone -> (M, t): a rest-space point p of the bone lands at M @ p + t in the posed model."""
    bones = {b["name"]: b for b in m["bones"]}
    pose = m.get("pose", {})
    cache = {}

    def frame(n):
        if n in cache:
            return cache[n]
        b = bones[n]
        rot = [b.get("rotation", [0, 0, 0])[a] + pose.get(n, [0, 0, 0])[a] + extra.get(n, [0, 0, 0])[a]
               for a in range(3)]
        R = np.array(rot_mat(*rot), dtype=float)
        piv = np.array(b["pivot"], dtype=float) + np.array(move.get(n, [0, 0, 0]), dtype=float)
        M, t = R, piv - R @ np.array(b["pivot"], dtype=float)
        if b.get("parent"):
            Mp, tp = frame(b["parent"])
            M, t = Mp @ M, Mp @ t + tp
        cache[n] = (M, t)
        return cache[n]
    return {n: frame(n) for n in bones}


def _hidden(m, hide):
    kids = {}
    for b in m["bones"]:
        kids.setdefault(b.get("parent"), []).append(b["name"])
    out, stack = set(), list(hide)
    while stack:
        n = stack.pop()
        if n not in out:
            out.add(n)
            stack.extend(kids.get(n, []))
    return out


def boxes(m, extra=None, hide=(), move=None):
    """Every visible (non-upgrade) cube as (bone, index, colour, centre, half-axes as matrix columns)."""
    fr = _frames(m, extra or {}, move or {})
    gone = _hidden(m, hide)
    out = []
    for b in m["bones"]:
        if b["name"] in gone or b.get("upgrade"):
            continue
        M, t = fr[b["name"]]
        for i, c in enumerate(b.get("cubes", [])):
            if c.get("upgrade"):
                continue
            g = c.get("inflate", 0.0)
            lo = np.array(c["from"], dtype=float) - g
            hi = lo + np.array(c["size"], dtype=float) + 2 * g
            ctr, half = (lo + hi) / 2, (hi - lo) / 2
            R = np.eye(3)
            if any(c.get("rotation", [0, 0, 0])):
                piv = np.array(c["pivot"], dtype=float) if c.get("pivot") is not None else ctr
                R = np.array(rot_mat(*c["rotation"]), dtype=float)
                ctr = piv + R @ (ctr - piv)
            out.append((b["name"], i, c["color"], M @ ctr + t, (M @ R) * half[None, :]))
    return out, fr


def penetration(ca, A, cb, B):
    """Separating-axis test on the 15 axes: the overlap depth if the boxes intersect, else 0."""
    ax = [A[:, k] for k in range(3) if np.linalg.norm(A[:, k]) > 1e-9]
    bx = [B[:, k] for k in range(3) if np.linalg.norm(B[:, k]) > 1e-9]
    axes = [v / np.linalg.norm(v) for v in ax + bx]
    for u in ax:
        for v in bx:
            w = np.cross(u, v)
            n = np.linalg.norm(w)
            if n > 1e-6:
                axes.append(w / n)
    d, best = cb - ca, 1e9
    for n in axes:
        ov = sum(abs(A[:, k] @ n) for k in range(3)) + sum(abs(B[:, k] @ n) for k in range(3)) - abs(d @ n)
        if ov <= 0:
            return 0.0
        best = min(best, ov)
    return best


def point_box_dist(p, c, A):
    try:
        u = np.linalg.solve(A, p - c)
    except np.linalg.LinAlgError:
        u = np.linalg.lstsq(A, p - c, rcond=None)[0]
    return float(np.linalg.norm(p - (c + A @ np.clip(u, -1, 1))))


JOINT_RADIUS = (("_arm", 4.0), ("_thigh", 3.0), ("_shin", 3.0), ("head", 2.5), ("_fore", 2.5), ("_foot", 2.0),
                ("wing_left", 2.5), ("wing_right", 2.5))


def _radius(name, joint):
    for suffix, r in JOINT_RADIUS:
        if name.endswith(suffix):
            return r
    return joint


def _closest(p, c, A):
    try:
        u = np.linalg.solve(A, p - c)
    except np.linalg.LinAlgError:
        u = np.linalg.lstsq(A, p - c, rcond=None)[0]
    return c + A @ np.clip(u, -1, 1)


def check(m, extra=None, hide=(), move=None, threshold=0.6, joint=1.2):
    """[(depth, bone_a, cube_a, colour_a, bone_b, cube_b, colour_b)] deepest first."""
    bx, fr = boxes(m, extra, hide, move)
    bones = {b["name"]: b for b in m["bones"]}
    ok = {frozenset(p) for p in m.get("contact_ok", [])}
    pivot = {n: fr[n][0] @ np.array(bones[n]["pivot"], dtype=float) + fr[n][1] for n in bones}
    # hosts: the bones a bone hangs from (parent, grandparent); a root's host is the root whose cubes hold its pivot
    by_bone = {}
    for b in bx:
        by_bone.setdefault(b[0], []).append(b)
    hosts = {}
    for n, b in bones.items():
        h = []
        p = b.get("parent")
        for _ in range(2):
            if not p:
                break
            h.append(p)
            p = bones[p].get("parent")
        if not b.get("parent"):
            for r, rb in bones.items():
                if r != n and not rb.get("parent") and any(
                        point_box_dist(pivot[n], c[3], c[4]) < joint for c in by_bone.get(r, [])):
                    h.append(r)
        hosts[n] = set(h)
    n = len(bx)
    if n < 2:
        return []
    C = np.array([b[3] for b in bx])
    ext = np.array([np.abs(b[4]).sum(axis=1) for b in bx])          # AABB half extents
    lo, hi = C - ext, C + ext
    cand = np.all((lo[:, None, :] < hi[None, :, :] - threshold) & (lo[None, :, :] < hi[:, None, :] - threshold), axis=2)
    out = []
    for i, j in zip(*np.nonzero(np.triu(cand, 1))):
        a, b = bx[i], bx[j]
        if a[0] == b[0] or frozenset((a[0], b[0])) in ok:
            continue
        depth = penetration(a[3], a[4], b[3], b[4])
        if depth <= threshold:
            continue
        # a joint: the two bones hang one from the other and the overlap sits by the child's pivot
        qa = _closest((a[3] + b[3]) / 2, a[3], a[4])
        mid = (qa + _closest(qa, b[3], b[4])) / 2
        if (b[0] in hosts[a[0]] and np.linalg.norm(mid - pivot[a[0]]) < _radius(a[0], joint)) or \
                (a[0] in hosts[b[0]] and np.linalg.norm(mid - pivot[b[0]]) < _radius(b[0], joint)):
            continue
        out.append((depth, a[0], a[1], a[2], b[0], b[1], b[2]))
    out.sort(reverse=True)
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("models", nargs="+")
    ap.add_argument("--poses", default="{}", help="JSON or @file: {name: {bone: [x,y,z], _hide: [...], _move: {}}}")
    ap.add_argument("--threshold", type=float, default=0.6)
    ap.add_argument("--joint", type=float, default=1.2)
    ap.add_argument("--max", type=int, default=6, help="lines per pose")
    ap.add_argument("--summary", action="store_true", help="one line per pose: the count and the deepest")
    a = ap.parse_args()
    found = False
    for path in a.models:
        m = render_concept.load_model(path)
        poses = a.poses
        if poses.startswith("@"):
            poses = open(poses[1:]).read()
        poses = json.loads(poses)
        poses = {"rest": poses.pop("rest", {}), **poses}
        print("==", os.path.basename(path))
        for pname, spec in poses.items():
            extra = {k: v for k, v in spec.items() if not k.startswith("_")}
            hits = check(m, extra, spec.get("_hide", ()), spec.get("_move", {}), a.threshold, a.joint)
            if not hits:
                print("  %-14s clean" % pname)
                continue
            found = True
            if a.summary:
                d, ba, ia, ca, bb, ib, cb = hits[0]
                print("  %-14s %2d over %.1f px, deepest %.2f  %s[%d] %s <-> %s[%d] %s" % (
                    pname, len(hits), a.threshold, d, ba, ia, ca, bb, ib, cb))
                continue
            for depth, ba, ia, ca, bb, ib, cb in hits[:a.max]:
                print("  %-14s %.2f px  %s[%d] %s  <->  %s[%d] %s" % (pname, depth, ba, ia, ca, bb, ib, cb))
            if len(hits) > a.max:
                print("  %-14s ... %d more" % (pname, len(hits) - a.max))
    return 1 if found else 0


if __name__ == "__main__":
    sys.exit(main())
