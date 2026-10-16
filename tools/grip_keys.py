#!/usr/bin/env python3
"""Grip keys: the game's weapon keys posed so the hand holds the weapon the way the hand is built - a straight wrist,
an elbow that bends the way a person's does - constructed here and printed as the Java tables (lovish, Oct 16: "when
he puts his hand around the weapon it doesnt look natural", "the elbows don't match human anatomy").

    python3 tools/grip_keys.py halberdier [--write-poses] [--solver-check]
    python3 tools/grip_keys.py squire [--write-poses]

Why. The fists (gen_models.grip_hand) are built round each weapon's REST hold, and the review of the Oct 16 hands
found the game's keys still turning the weapon 77-130 degrees against them:
  - Halberdier (HalberdierUnitModel): the gauntlet rides the halberd bone. The guard, the draw-back and the thrust
    tilted the haft 100-130 degrees about the grip while the forearm kept its rest pose: the cuff pointed up, away
    from the forearm (a broken wrist), its end 2.6-2.9 px from the forearm's.
  - Squire Runner (SquireRunnerUnitModel): the fist rides the forearm. The strike and the follow turned the sword
    77-87 degrees inside it: the grip no longer crossed the palm and the blade ran straight on from the forearm.

What the hands allow. Each fist holds its weapon at one angle to the forearm - the halberd's point 116 degrees from
the forearm's line (the cuff 64 degrees off the haft), the sword's blade 65 degrees off it, both on the thumb side - and
a wrist bends ~20 degrees from there before it reads broken; an elbow only flexes (DSL x >= 0, tools/limb_check.py).
  - Halberdier: with the haft pointing forward (15-19 degrees up: the old keys' angles, kept exactly) the forearm must
    stand near VERTICAL. Held out at the chest as before, the elbow would have to rise to the shoulder (or bend
    backward) to reach the cuff - a 6.2-9 px reach for a 5.2 px upper arm. So the arm hangs, the halberd held at the
    trail with the fist by the hip, and the halberd is MOVED into the fist (its grip offset; "_move" in the poses) -
    the one-handed hold a halberdier really takes. Per key: the elbow's flex and the wrist's bend go in, the hand's
    place comes out. The old rig turn (halberd_arm) is folded into the haft's own angle: only the walk swings the rig.
  - Squire: a fist at 45-85 degrees cannot point a blade forward-DOWN from a forearm held out forward (it would have
    to hang straight down). The strike lowers the arm - the forearm ~50 degrees down - and snaps the wrist (the sword
    -20 in the fist: the grip 45 degrees to the forearm, a cut's ulnar snap), the blade level at the waist and a little
    across the body (the cut follows the body's turn); the follow carries it on down. Per key: the upper arm's
    direction and the way its elbow faces, the elbow's flex, the wrist's snap. The windup (13 in the fist) and the
    bow's AIM keys are not touched.
Both are exact constructions - the upper arm's frame from its direction and its bend's axis, Euler angles out - not
grid searches: Model.solve_arm's 2-degree grid leaves the upper arm's twist loose when the elbow is nearly straight
(on the thrust's targets it lands the wrist but twists the upper arm 32 degrees off, the elbow's point turned in; with
its default elbow weight it turned the guard's elbow 25 degrees out). --solver-check runs it for comparison.

Printed: per key the hand on the weapon (the wrist's miss, the forearm off the cuff / the grip in the fist), the elbow
(flex, where its point faces), the contacts (tools/contact_check.py), the in-between frames (the game lerps the keys'
numbers), and the Java table. --write-poses puts the keys into design/poses/<model>.json - the contact sweep of
tools/game_preview.py --art-check. Change the design numbers below and the Java together.
"""
import argparse
import json
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import contact_check  # noqa: E402
from gen_models import Model, euler, rot_mat  # noqa: E402

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
WRIST_FREE = 20.0          # degrees a wrist may bend before it reads broken (gen_models.WRIST_FREE)


# ------------------------------------------------------------------ shared ------------------------------------------
def load(name):
    with open(os.path.join(ROOT, "design", "models", name + ".json")) as f:
        return json.load(f)


def bones_of(J):
    return {b["name"]: b for b in J["bones"]}


def frames(J, extra, move=None):
    """bone -> (M, t): a rest-space point p of the bone lands at M @ p + t (contact_check's posing: rest + model pose +
    extra DSL degrees added per axis, as the game's dsl() adds them; move: DSL px on a bone's offset, as
    ModelPart.x/y/z)."""
    return contact_check._frames(J, extra, move or {})


def at(fr, bone, p):
    M, t = fr[bone]
    return M @ np.asarray(p, dtype=float) + t


def unit(v):
    v = np.asarray(v, dtype=float)
    return v / np.linalg.norm(v)


def angle(a, b):
    return math.degrees(math.acos(float(np.clip(unit(a) @ unit(b), -1.0, 1.0))))


def turn(v, axis, deg):
    """v turned deg degrees about the unit axis (right-handed)."""
    k, a = unit(axis), math.radians(deg)
    v = np.asarray(v, dtype=float)
    return v * math.cos(a) + np.cross(k, v) * math.sin(a) + k * (k @ v) * (1.0 - math.cos(a))


def arm_frame(u, front):
    """An upper arm's rotation: it hangs along its local -y (shoulder to elbow) and its forearm flexes about its local
    +x, a positive x swinging the hand toward local -z (gen_models.solve_arm's convention). -y onto u, -z onto `front`
    (the way the elbow folds the hand; square to u)."""
    y, z = -unit(u), -unit(front)
    return np.stack([np.cross(y, z), y, z], axis=1)


def local_euler(parent, R):
    """The DSL rotation (x, y, z) of a bone whose world rotation is R under a parent at `parent`."""
    L = parent.T @ R
    rot = euler(tuple(tuple(float(v) for v in row) for row in L))
    assert np.abs(np.array(rot_mat(*rot)) - L).max() < 2e-3, "local_euler: gimbal lock"
    return rot


def r1(v):
    return [round(float(x), 1) for x in v]


def lerp_pose(a, b, f):
    out, moves = {}, {}
    for k in set(a) | set(b):
        if k.startswith("_"):
            continue
        va, vb = a.get(k, [0, 0, 0]), b.get(k, [0, 0, 0])
        out[k] = [va[i] + (vb[i] - va[i]) * f for i in range(3)]
    ma, mb = a.get("_move", {}), b.get("_move", {})
    for k in set(ma) | set(mb):
        va, vb = ma.get(k, [0, 0, 0]), mb.get(k, [0, 0, 0])
        moves[k] = [va[i] + (vb[i] - va[i]) * f for i in range(3)]
    if moves:
        out["_move"] = moves
    return out


def contacts(J, pose, hide=(), only=None):
    """tools/contact_check.py on a pose: the count over 0.6 px and the deepest - of the pairs touching `only` (the bones
    the keys move) if given, with the model's other contacts (e.g. a rest pose's own) counted apart."""
    hits = contact_check.check(J, {k: v for k, v in pose.items() if not k.startswith("_")}, hide, pose.get("_move", {}))
    mine = [h for h in hits if only is None or h[1] in only or h[4] in only]
    rest = len(hits) - len(mine)
    if not mine:
        text = "clean"
    else:
        d, ba, ia, ca, bb, ib, cb = mine[0]
        text = "%d over 0.6 px, deepest %.2f %s[%d] %s <-> %s[%d] %s" % (len(mine), d, ba, ia, ca, bb, ib, cb)
    return text + (" (+%d elsewhere)" % rest if only is not None and rest else "")


def jnum(v, nd=1):
    """A Java float literal in the style of the unit models (whole numbers bare)."""
    v = round(float(v), nd)
    if abs(v - round(v)) < 1e-9:
        return "%d" % round(v)
    return ("%." + str(nd) + "f") % v + "f"


def jvec(v, nd=1):
    return "{" + ", ".join(jnum(x, nd) for x in v) + "}"


def _floats(v):
    if isinstance(v, dict):
        return {k: _floats(x) for k, x in v.items()}
    if isinstance(v, (list, tuple)):
        return [_floats(x) for x in v]
    return float(v) if isinstance(v, (int, float)) and not isinstance(v, bool) else v


def write_poses(name, entries):
    """Sets the named entries of design/poses/<name>.json (numbers as floats, like the rest of the file); the other
    entries and their order stay."""
    path = os.path.join(ROOT, "design", "poses", name + ".json")
    with open(path) as f:
        poses = json.load(f)
    for k, v in entries.items():
        poses[k] = _floats(v)
    with open(path, "w") as f:
        json.dump(poses, f, indent=1)
    print("wrote", os.path.relpath(path, ROOT), "(%s)" % ", ".join(entries))


# ------------------------------------------------------------------ the Halberdier ----------------------------------
HAL = "sunforged_halberdier_v2"
HAL_WRIST = (-4.6, 13.7, 0.0)     # the forearm's wrist point (gen_halberdier.arm: wrist_y + 0.3 on the arm's axis)
# Per key: (pose name, body - KEY_BODY, the haft - the old keys' rig and pole folded into one rotation of "halberd" in
# the body's frame (READY: rig 45/0/10 then pole -110 = -65/0/10; draw-back 25/0/6 + -100; thrust 70/0/5 + -130),
# the elbow's flex, the wrist's bend forward off the cuff). The flex sets how far back the elbow rides (more: the hand
# draws back); the bend tips the forearm forward of the cuff (the hand forward; <= WRIST_FREE).
HAL_KEYS = (("ready", (-6, 0, 0), (-65, 0, 10), 18, 10),
            ("draw_back", (0, 0, 0), (-75, 0, 6), 34, 5),
            ("thrust", (-12, 0, 0), (-60, 0, 5), 12, 15))
HAL_SEQ = ("ready", "draw_back", "thrust", "ready")      # HalberdierUnitModel.KEY_P = {0, 0.25, 0.55, 1}


def hal_cuff(J):
    """The gauntlet's cuff (gen_models.grip_hand: the stub from the back of the palm toward the forearm, a slab of the
    halberd bone, its 0.65 px length along its own z): the short slab one of whose ends lies at the rest forearm's
    wrist (the back plate of the hand runs the same way but is 1.7 px long). Returns its end toward the elbow -
    grip_hand's "wrist", where the forearm's wrist goes - and its axis from the hand toward the elbow, in the halberd's
    rest frame."""
    B = bones_of(J)
    fr = frames(J, {})
    Mh, th = fr["halberd"]
    to_h = lambda p: Mh.T @ (p - th)                                   # noqa: E731
    wrist = to_h(at(fr, "right_fore", HAL_WRIST))
    elbow = to_h(at(fr, "right_fore", B["right_fore"]["pivot"]))
    best = None
    for i, c in enumerate(B["halberd"]["cubes"]):
        lo, size = np.array(c["from"], dtype=float), np.array(c["size"], dtype=float)
        if c.get("upgrade") or size[2] > 1.0:
            continue
        ctr, R = lo + size / 2, np.eye(3)
        if any(c.get("rotation", [0, 0, 0])):
            R = np.array(rot_mat(*c["rotation"]))
            piv = np.array(c["pivot"], dtype=float) if c.get("pivot") is not None else ctr
            ctr = piv + R @ (ctr - piv)
        ax = R[:, 2] * size[2] / 2
        d = min(np.linalg.norm(ctr + ax - wrist), np.linalg.norm(ctr - ax - wrist))
        if best is None or d < best[0]:
            best = (d, i, ctr, ax)
    d, i, ctr, ax = best
    assert d < 0.5, "no cuff at the rest wrist (%.2f px)" % d
    far = ctr + ax if np.linalg.norm(ctr + ax - elbow) < np.linalg.norm(ctr - ax - elbow) else ctr - ax
    return far, unit(far - ctr), i


def hal_key(J, cuff, body, haft, flex, wrist):
    """One key: the arm hung by anatomy, the halberd moved so its cuff ends at the wrist. Returns the pose (DSL deltas
    on the rest, rounded as the Java gets them) and the targets."""
    B = bones_of(J)
    W_l, axis_l = cuff
    fr = frames(J, {"body": list(body), "halberd": list(haft)})
    Mh = fr["halberd"][0]
    Mp = fr["halberd_arm"][0]                  # the arm's and the halberd's parent (the rig, unturned)
    Mb = fr["body"][0]
    S = at(fr, "right_arm", B["right_arm"]["pivot"])
    upper = np.linalg.norm(np.subtract(B["right_fore"]["pivot"], B["right_arm"]["pivot"]))
    fore = np.linalg.norm(np.subtract(HAL_WRIST, B["right_fore"]["pivot"]))
    cuff = Mh @ axis_l                         # from the hand toward the elbow, posed
    bend = unit(np.cross(-cuff, Mb @ [0, 0, -1.0]))   # the elbow's and the wrist's axis: square to forearm and ahead
    f = turn(-cuff, bend, wrist)               # elbow -> wrist
    u = turn(f, bend, -flex)                   # shoulder -> elbow
    E = S + upper * u
    Wt = E + fore * f
    move = Mp.T @ (Wt - at(fr, "halberd", W_l))
    rest_arm = B["right_arm"].get("rotation", [0, 0, 0])
    rest_fore = B["right_fore"].get("rotation", [0, 0, 0])
    arm = local_euler(Mp, arm_frame(u, np.cross(bend, u)))
    pose = {"body": list(body), "halberd": list(haft),
            "right_arm": r1([arm[k] - rest_arm[k] for k in range(3)]),
            "right_fore": [round(flex - rest_fore[0], 1), 0.0, 0.0],
            "_move": {"halberd": [round(float(v), 2) for v in move]}}
    return pose, {"W": Wt, "E": E, "aim": Wt - 2.0 * cuff}


def hal_measure(J, cuff, pose):
    """(wrist miss px, forearm off the cuff deg, elbow flex deg, elbow point: degrees from straight back toward out)"""
    B = bones_of(J)
    W_l, axis_l = cuff
    fr = frames(J, {k: v for k, v in pose.items() if not k.startswith("_")}, pose.get("_move", {}))
    W = at(fr, "halberd", W_l)
    cuff = fr["halberd"][0] @ axis_l
    wr, el = at(fr, "right_fore", HAL_WRIST), at(fr, "right_fore", B["right_fore"]["pivot"])
    rest_fore = B["right_fore"].get("rotation", [0, 0, 0])[0]
    flex = rest_fore + pose.get("right_fore", [0, 0, 0])[0]
    pt = fr["body"][0].T @ (fr["right_fore"][0] @ [0, 0, 1.0])     # the elbow's point (the forearm's back) in the body
    return np.linalg.norm(W - wr), angle(el - wr, cuff), flex, math.degrees(math.atan2(-pt[0], pt[2]))


KEY_LINE = "  %-10s wrist %.2f px off the cuff, forearm %4.1f deg off it, elbow flex %3.0f, point %+4.0f deg out   %s"


def halberdier(a):
    J = load(HAL)
    W_l, axis_l, ci = hal_cuff(J)
    cuff = (W_l, axis_l)
    print("== %s: the gauntlet's cuff is halberd[%d]; its wrist end %s" % (HAL, ci, np.round(W_l, 2)))
    e, off, flex, pt = hal_measure(J, cuff, {})
    print(KEY_LINE % ("rest", e, off, flex, pt, contacts(J, {})))
    keys, targets = {}, {}
    for name, body, haft, kflex, kwrist in HAL_KEYS:
        keys[name], targets[name] = hal_key(J, cuff, body, haft, kflex, kwrist)
        e, off, flex, pt = hal_measure(J, cuff, keys[name])
        print(KEY_LINE % (name, e, off, flex, pt, contacts(J, keys[name])))
        assert e < 0.1 and off <= WRIST_FREE + 0.5, name
    if a.solver_check:
        m = Model(J["name"], J["faction"], J["role"], J["palette"], J["emissive"])
        for b in J["bones"]:
            m.bone(b["name"], b["pivot"], b.get("parent"), b.get("rotation", [0, 0, 0])).cubes = b["cubes"]
        rest = {n: list(m.bones[n].rotation) for n in ("right_arm", "right_fore")}
        for name, body, haft, _f, _w in HAL_KEYS:
            m.pose = dict(J.get("pose", {}), body=list(body))
            tg = targets[name]
            err = m.solve_arm("right_arm", "right_fore", HAL_WRIST, tuple(tg["W"]), point_at=tuple(tg["aim"]),
                              avoid=("body",), avoid_margin=1.3, avoid_elbow=True, elbow_at=tuple(tg["E"]), elbow_w=3.0)
            got = [m.bones["right_arm"].rotation[k] - rest["right_arm"][k] for k in range(3)]
            gf = m.bones["right_fore"].rotation[0] - rest["right_fore"][0]
            print("  solve_arm  %-10s arm %s fore %+d (built %s %+.1f), wrist %.2f px" % (
                name, got, gf, keys[name]["right_arm"], keys[name]["right_fore"][0], err))
            for n in rest:
                m.bones[n].rotation = list(rest[n])
    # the in-betweens: the game lerps every number between neighbouring keys (and from rest by the READY blend)
    worst = (0.0, 0.0)
    for (p, q) in [({}, keys["ready"])] + [(keys[HAL_SEQ[i]], keys[HAL_SEQ[i + 1]]) for i in range(len(HAL_SEQ) - 1)]:
        for f in (0.25, 0.5, 0.75):
            mid = lerp_pose(p, q, f)
            e, off, _, _ = hal_measure(J, cuff, mid)
            worst = (max(worst[0], e), max(worst[1], off))
            c = contacts(J, mid)
            if not c.startswith("clean"):
                print("  in-between %.2f: %s" % (f, c))
    print("  in-betweens (the READY blend and the thrust's legs, 1/4 1/2 3/4): wrist up to %.2f px off the cuff, "
          "forearm up to %.1f deg" % worst)
    R = keys["ready"]
    get = {"POLE": (lambda k: k["halberd"], 1), "GRIP": (lambda k: k["_move"]["halberd"], 2),
           "RARM": (lambda k: k["right_arm"], 1), "BODY": (lambda k: k["body"], 1)}

    def row(field):        # the guard's own entries by name, as the Java has them
        fn, nd = get[field]
        return ", ".join("READY_" + field if n == "ready" else jvec(fn(keys[n]), nd) for n in HAL_SEQ)
    print("\nHalberdierUnitModel (DSL degrees; the grip in px):")
    print("    private static final float[] READY_POLE = %s, READY_GRIP = %s;" % (
        jvec(R["halberd"]), jvec(R["_move"]["halberd"], 2)))
    print("    private static final float[] READY_RARM = %s, READY_BODY = %s;" % (
        jvec(R["right_arm"]), jvec(R["body"])))
    print("    private static final float READY_RFORE = %s;" % jnum(R["right_fore"][0]))
    for field in ("POLE", "GRIP", "RARM"):
        print("    private static final float[][] KEY_%s = {%s};" % (field, row(field)))
    print("    private static final float[] KEY_RFORE = {%s};" % ", ".join(
        "READY_RFORE" if n == "ready" else jnum(keys[n]["right_fore"][0]) for n in HAL_SEQ))
    print("    private static final float[][] KEY_BODY = {%s};" % row("BODY"))
    if a.write_poses:
        write_poses(HAL, keys)


# ------------------------------------------------------------------ the Squire Runner -------------------------------
SQ = "sunforged_squire_runner_v2"
# Per designed key: (pose name, KEY_P index, body - KEY_BODY, the upper arm: degrees below level and toward his left
# (in the world), the way its elbow folds the hand turned toward his left from straight up (an inward turn), the
# elbow's flex, the sword in the fist - KEY_SWORD, within +-WRIST_FREE: -20 brings the blade 20 degrees toward the
# forearm's line, a cut's snap of the wrist)
SQ_KEYS = (("swing_strike", 2, (-14, -10, 0), 55, 8, 15, 12, -20),
           ("swing_follow", 3, (-12, -8, 0), 72, 12, 20, 10, -20))
# the keys kept (KEY_ARM, KEY_FORE, KEY_SWORD, KEY_BODY at KEY_P index): the rest at both ends and the windup (its 13
# degrees in the fist are within a wrist's reach)
SQ_KEPT = {0: ([0, 0, 0], 0, 0, [0, 0, 0]), 1: ([150, 0, -10], 30, 13, [6, 18, 0]), 4: ([0, 0, 0], 0, 0, [0, 0, 0])}
# the strike and the follow before Oct 16 (for the silhouette comparison only)
SQ_OLD = {2: ([62, 0, 10], 0, -87), 3: ([30, 0, 15], 8, -77)}
SQ_BLADE = 10.75          # from the fist's centre to the point (gen_squire.sword: 1.55 + 9.2)
SQ_MOVED = ("right_arm", "right_fore", "sword", "sword_rune")


def sq_pose(arm, fore, sword, body):
    return {"right_arm": list(arm), "right_fore": [fore, 0.0, 0.0], "sword": [sword, 0.0, 0.0], "body": list(body)}


def sq_key(J, body, alpha, psi, tau, flex, grip):
    B = bones_of(J)
    Mb = frames(J, {"body": list(body)})["body"][0]
    al, ps = math.radians(alpha), math.radians(psi)
    u = np.array([math.sin(ps) * math.cos(al), -math.sin(al), -math.cos(ps) * math.cos(al)])
    up = np.array([0.0, 1.0, 0.0])
    assert alpha < 85, "an arm hanging straight down has no 'up' to fold toward: give the fold another way"
    front = turn(unit(up - (up @ u) * u), u, tau)          # tau > 0 turns the fold toward +x (his left)
    arm = local_euler(Mb, arm_frame(u, front))             # right_arm hangs from the body
    rest_arm = B["right_arm"].get("rotation", [0, 0, 0])
    rest_fore = B["right_fore"].get("rotation", [0, 0, 0])[0]
    return sq_pose(r1([arm[k] - rest_arm[k] for k in range(3)]), round(flex - rest_fore, 1), float(grip), body)


def sq_measure(J, pose):
    """(the fist's centre, the blade's direction, the blade off the forearm deg, elbow flex, elbow point: degrees from
    straight back toward out)"""
    B = bones_of(J)
    fr = frames(J, pose)
    fist = np.array(B["sword"]["pivot"], dtype=float)
    P, el = at(fr, "sword", fist), at(fr, "right_fore", B["right_fore"]["pivot"])
    d = fr["sword"][0] @ [0, 0, -1.0]
    flex = B["right_fore"].get("rotation", [0, 0, 0])[0] + pose.get("right_fore", [0, 0, 0])[0]
    pt = fr["body"][0].T @ (fr["right_fore"][0] @ [0, 0, 1.0])
    return P, d, angle(P - el, d), flex, math.degrees(math.atan2(-pt[0], pt[2]))


def elev_yaw(d):
    return math.degrees(math.asin(float(np.clip(d[1], -1, 1)))), math.degrees(math.atan2(d[0], -d[2]))


def squire(a):
    J = load(SQ)
    with open(os.path.join(ROOT, "design", "poses", SQ + ".json")) as f:
        sweep = json.load(f)
    hide = sweep.get("swing_strike", {}).get("_hide", ["bow", "sheathed", "arrow", "sword_rune"])
    print("== %s: the blade %.0f deg off the forearm at rest (the fist's grip)" % (SQ, sq_measure(J, {})[2]))
    seq = {k: sq_pose(v[0], v[1], v[2], v[3]) for k, v in SQ_KEPT.items()}
    for name, idx, body, alpha, psi, tau, flex, grip in SQ_KEYS:
        seq[idx] = sq_key(J, body, alpha, psi, tau, flex, grip)
    names = {2: "swing_strike", 3: "swing_follow", 1: "swing_windup"}
    for idx in (1, 2, 3):
        P, d, off, flex, pt = sq_measure(J, seq[idx])
        ev = elev_yaw(d)
        line = ("  %-12s the sword %+5.1f in the fist (blade %2.0f deg off the forearm), elbow flex %3.0f, point %+4.0f"
                " deg out" % (names[idx], seq[idx]["sword"][0], off, flex, pt))
        if idx in SQ_OLD:
            o = SQ_OLD[idx]
            P0, d0, off0, _, _ = sq_measure(J, sq_pose(o[0], o[1], o[2], seq[idx]["body"]))
            e0 = elev_yaw(d0)
            tip, tip0 = P + SQ_BLADE * d, P0 + SQ_BLADE * d0
            line += ("\n               before: the sword %+.0f in the fist (blade %.0f off the forearm); now the fist"
                     " %.1f px from there (%s -> %s), the point %.1f px; the blade %+.0f -> %+.0f deg up,"
                     " %+.0f -> %+.0f across (toward his left)\n               contacts before: %s" % (
                         o[2], off0, np.linalg.norm(P - P0), np.round(P0, 1), np.round(P, 1),
                         np.linalg.norm(tip - tip0), e0[0], ev[0], e0[1], ev[1],
                         contacts(J, sq_pose(o[0], o[1], o[2], seq[idx]["body"]), hide, SQ_MOVED)))
        print(line)
        print("               contacts now:    %s" % contacts(J, seq[idx], hide, SQ_MOVED))
        assert abs(seq[idx]["sword"][0]) <= WRIST_FREE + 1e-6, names[idx]
    for i in range(4):
        for f in (0.25, 0.5, 0.75):
            c = contacts(J, lerp_pose(seq[i], seq[i + 1], f), hide, SQ_MOVED)
            if not c.startswith("clean"):
                print("  in-between %d-%d at %.2f: %s" % (i, i + 1, f, c))
    order = [seq[i] for i in range(5)]
    print("\nSquireRunnerUnitModel (DSL degrees; KEY_P = {0, 0.3, 0.55, 0.75, 1}):")
    print("    private static final float[][] KEY_ARM = {%s};" % ", ".join(jvec(k["right_arm"]) for k in order))
    print("    private static final float[] KEY_FORE = {%s};" % ", ".join(jnum(k["right_fore"][0]) for k in order))
    print("    private static final float[] KEY_SWORD = {%s};" % ", ".join(jnum(k["sword"][0]) for k in order))
    print("    private static final float[][] KEY_BODY = {%s};" % ", ".join(jvec(k["body"]) for k in order))
    if a.write_poses:
        out = {}
        for idx in (2, 3):
            entry = dict(sweep.get(names[idx], {}))       # the pennant and the hidden bones stay; the arm is set
            entry.update(seq[idx])
            out[names[idx]] = entry
        write_poses(SQ, out)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("unit", choices=("halberdier", "squire"))
    ap.add_argument("--write-poses", action="store_true", help="put the keys into design/poses/<model>.json")
    ap.add_argument("--solver-check", action="store_true", help="(halberdier) gen_models.Model.solve_arm on the same "
                    "targets, for comparison (about a minute)")
    a = ap.parse_args()
    halberdier(a) if a.unit == "halberdier" else squire(a)


if __name__ == "__main__":
    main()
