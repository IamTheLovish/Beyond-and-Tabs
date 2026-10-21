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
# Oct 21 - the two-handed POLEARM guard and the lunge thrust (lovish: "looks odd the way he's holding that spear like
# a purse dangling, his arm looks stiff"; animations "realistic, alive, and detailed"). Keys are CONSTRUCTED from the
# figure and written as data to design/anims/halberdier.json (tools/anim_lab.py plays, measures and generates the Java
# from it):
#   - the right (rear) hand: hal_key as before - the arm hung by anatomy from the fist's cuff (gen_halberdier.REAR_GRIP,
#     the haft diagonal across the palm) and the halberd MOVED into the fist; the elbow's flex and the wrist's bend
#     per key, the hand's place comes out;
#   - the left (front) hand: the fist is built on the forearm (gen_halberdier.arm hand="fist"), so each key solves the
#     left arm (upper arm 3 axes, elbow hinge) to put the fist's centre ON the haft `d` px up from the rear grip with
#     the fist's axis along the haft (the wrist within WRIST_FREE) - in the thrust d shrinks: the haft slides through
#     the front fist as the rear hand drives;
#   - the legs: the lead (left) foot steps and the rear foot stays PLANTED - each leg is a 2-link IK from the moved
#     hip to its ankle target, the sole kept level;
#   - the body goes first (hips sink and turn in the load, travel forward in the drive), the arms follow, the point
#     arrives last; the tassel lags the haft 1-3 ticks.
# References (stills): Muybridge, "Animal Locomotion" (1887), the fencing/lunge and staff-thrust plates - the hips lead,
# the rear leg straightens, the weapon arrives last; Joachim Meyer, "Gruendtliche Beschreibung der Kunst des Fechtens"
# (1570), the halberd plates: rear hand at the hip with the elbow bent, front hand forward on the haft, the point at
# chest height, the left foot forward.
HAL = "sunforged_halberdier_v2"
HAL_ANIM = os.path.join(ROOT, "design", "anims", "halberdier.json")
import gen_halberdier as _gh  # noqa: E402
import gen_models as _gm  # noqa: E402
HAL_WRIST = (-_gh.SHOULDER_X, _gh.SHOULDER_Y - _gh.UPPER - _gh.FORE + 0.3, 0.0)
HAL_LWRIST = (_gh.SHOULDER_X, _gh.SHOULDER_Y - _gh.UPPER - _gh.FORE + 0.3, 0.0)
HAL_TICKS = 12                # the clip's length in the lab; the game plays it over the unit's swing (Lane A sets that)
HAL_HIT = 0.46
LEG = ((("left_thigh", "left_shin", "left_foot"), 1.7), (("right_thigh", "right_shin", "right_foot"), -1.7))

# Per key: p, body (lean, yaw: + = the left shoulder forward, roll), hips move (y, z: px, + forward), the haft's
# WORLD direction (pitch up, yaw in: + = the point toward the centre line), the rear elbow's flex, its wrist's bend,
# d (the front fist up the haft from the rear grip, px), the lead ankle (dy up, dz forward of its rest), tassel x
# (it lags the haft: back as the point drives, forward as it stops), tab (tabard front, back: the panels lag the
# hips and are pushed by the lead thigh).
EO = (-7.0, 19.5, 1.5)        # the rear elbow: out beside the hip and a little behind (body frame)
HAL_KEYS = (
    # the guard: knees soft, the left side forward, the point ahead at chest height; the hands a forearm apart
    dict(p=0.0,  body=(-6, 24, 0),  hips=(-0.5, 0.0),  haft=(16, 24), flex=95, wrist=8,  out=EO, d=5.5, lead=(0, 0),
         tas=0, tab=(0, 0)),
    # the load (anticipation): the hips sink and turn a touch further, the rear hand draws back - the haft slides
    # back through the front fist, the point dips; the lead foot comes off the ground
    dict(p=0.2,  body=(-4, 28, 0),  hips=(-0.9, 0.3),  haft=(12, 26), flex=100, wrist=4, out=EO, d=7.0,
         lead=(1.0, 0.8), tas=6, tab=(2, 3)),
    # the launch: the hips go first - already moving while the arms have barely started (the body before the arms)
    dict(p=0.28, body=(-7, 25, 0),  hips=(-1.0, 0.9),  haft=(11, 22), flex=88, wrist=7, out=EO, d=4.6,
         lead=(0.6, 2.2), tas=-10, tab=(6, -4)),
    # the drive: the hips travelling, the lead foot lands ahead, the shoulders turn in, the rear arm half out
    dict(p=0.36, body=(-9, 22, 0),  hips=(-1.1, 1.6),  haft=(11, 18), flex=74, wrist=10, out=EO, d=3.3,
         lead=(0, 3.5), tas=-22, tab=(10, -8)),
    # full extension (just after the hit at p=0.5 - the blow lands at speed): both arms straight along the haft, the
    # fists together, the right shoulder in behind the point, the rear leg straight
    dict(p=0.54, body=(-14, 25, 0), hips=(-1.3, 3.0),  haft=(10, 15), flex=40, wrist=14, out=EO, d=1.3,
         lead=(0, 3.5), tas=-8, tab=(6, -11)),
    # the recovery: the weight comes back off the lead foot, the rear hand draws back first, the point rises
    dict(p=0.76, body=(-8, 20, 0),  hips=(-0.9, 1.6),  haft=(13, 20), flex=75, wrist=8,  out=EO, d=3.6,
         lead=(0, 3.5), tas=14, tab=(2, 5)),
    # the lead foot steps back home
    dict(p=0.9,  body=(-6, 22, 0),  hips=(-0.6, 0.5),  haft=(15, 22), flex=88, wrist=8,  out=EO, d=5.0,
         lead=(0.8, 1.2), tas=3, tab=(0, 2)),
    dict(p=1.0),                                                   # = the guard
)


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
    assert d < 0.8, "no cuff at the rest wrist (%.2f px)" % d
    far = ctr + ax if np.linalg.norm(ctr + ax - elbow) < np.linalg.norm(ctr - ax - elbow) else ctr - ax
    return far, unit(far - ctr), i


def hal_key(J, cuff, body, haft, flex, wrist, out=0.0):
    """The rear arm: hung by anatomy, the halberd moved so its cuff ends at the wrist. `out`: the elbow's swivel about
    the forearm - degrees from the vertical plane, or a point in the body's frame the elbow should come nearest to (a
    rear hand at the hip carries its elbow out and back beside the hip, not tucked in behind the spine). Returns the pose (DSL deltas on the rest, rounded as the Java gets them) and the
    targets."""
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
    if isinstance(out, (tuple, list)):         # an elbow TARGET (body frame, rest px): the swivel that comes nearest
        tb = at(fr, "body", out)
        best = None
        for o in range(-180, 180, 3):
            b2 = turn(bend, -cuff, o)
            f2 = turn(-cuff, b2, wrist)
            E2 = S + upper * turn(f2, b2, -flex)
            dd = float(np.linalg.norm(E2 - tb))
            if best is None or dd < best[0]:
                best = (dd, o)
        out = best[1]
    bend = turn(bend, -cuff, out)              # swivelled about the forearm: the elbow out from the body
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


def hal_haft(J, body, pitch, yaw):
    """The halberd's DSL rotation (x, y, z=0) that points it `pitch` degrees up and `yaw` degrees in toward the centre
    line, in the WORLD, under the turned body."""
    from scipy.optimize import minimize
    want = unit((math.sin(math.radians(yaw)) * math.cos(math.radians(pitch)), math.sin(math.radians(pitch)),
                 -math.cos(math.radians(yaw)) * math.cos(math.radians(pitch))))

    def cost(v):
        fr = frames(J, {"body": list(body), "halberd": [v[0], v[1], 0.0]})
        return float(np.sum((fr["halberd"][0] @ [0, 1.0, 0] - want) ** 2))
    r = minimize(cost, [-70.0, -yaw], method="Nelder-Mead", options=dict(xatol=0.01, fatol=1e-9))
    assert r.fun < 1e-5, "haft aim"
    return [round(float(r.x[0]), 1), round(float(r.x[1]), 1), 0.0]


def hal_left_fist(J):
    """The front fist as built on the left forearm (gen_halberdier.arm hand="fist"): its centre and axis in the
    forearm's rest space (rebuilt on a scratch bone with the generator's own numbers)."""
    m = _gm.Model("scratch", "sunforged", "", {}, {})
    th = math.radians(_gh.LEFT_GRIP)
    g = _gm.grip_hand(_gm.Bone(m, "_s", [0, 0, 0]), None, (0, -math.cos(th), -math.sin(th)), _gh.GRIP_R, 1, "plate",
                      back=(0, 1, 0), wrist=HAL_LWRIST)
    return np.array(g["centre"], dtype=float), unit(g["axis"])


def hal_left(J, fist, pose, d, start=None):
    """The front arm onto the haft: left_arm (x, y, z) and the elbow's flex so the fist's centre sits d px up the haft
    from the rear grip with its axis along the haft (the wrist within WRIST_FREE), the elbow kept down and out.
    Returns the deltas and (miss px, axis off the haft deg, flex)."""
    from scipy.optimize import minimize
    B = bones_of(J)
    C_l, a_l = fist
    base = {k: v for k, v in pose.items() if not k.startswith("_")}
    move = pose.get("_move", {})
    fr0 = frames(J, base, move)
    grip = at(fr0, "halberd", B["halberd"]["pivot"])
    haft = fr0["halberd"][0] @ [0, 1.0, 0]
    target = grip + d * haft
    rest_fore = B["left_fore"].get("rotation", [0, 0, 0])[0]
    S = at(fr0, "left_arm", B["left_arm"]["pivot"])

    def parts(v):
        ex = dict(base, left_arm=[v[0], v[1], v[2]], left_fore=[v[3], 0.0, 0.0])
        fr = frames(J, ex, move)
        c = at(fr, "left_fore", C_l)
        ax = fr["left_fore"][0] @ a_l
        el = at(fr, "left_fore", B["left_fore"]["pivot"])
        return c, ax, el

    def cost(v):
        c, ax, el = parts(v)
        miss = np.sum((c - target) ** 2)
        off = max(0.0, angle(ax, haft) - 10.0)
        flex = rest_fore + v[3]
        pen = max(0.0, 5.0 - flex) ** 2 + max(0.0, flex - 140.0) ** 2
        up = max(0.0, el[1] - S[1] + 1.0) ** 2            # the elbow below the shoulder
        inb = max(0.0, S[0] - el[0] + 0.5) ** 2            # and not in past it
        ahead = max(0.0, el[2] - (S[2] - 2.6)) ** 2        # and ahead of the chest (the arm crosses it, not through)
        cont = sum((v[i] - start[i]) ** 2 for i in range(4)) if start else 0.0   # the same branch as the last key
        return 20 * miss + 0.1 * off ** 2 + pen + 2 * up + 2 * inb + 0.003 * cont
    best = None
    for s in ([start] if start else []) + [[60, 0, 0, 20], [40, 30, -10, 40]]:
        r = minimize(cost, s, method="Nelder-Mead", options=dict(xatol=0.01, fatol=1e-7, maxiter=4000))
        if best is None or r.fun < best.fun:
            best = r
    v = best.x
    c, ax, el = parts(v)
    out = {"left_arm": r1(v[:3]), "left_fore": [round(float(v[3]), 1), 0.0, 0.0]}
    return out, (float(np.linalg.norm(c - target)), angle(ax, haft), rest_fore + float(v[3]))


def hal_legs(J, hips, lead):
    """Both legs by 2-link IK from the moved hips: the rear ankle at its rest place (planted), the lead ankle `lead`
    (dy, dz forward) off its rest place; the soles kept level. Returns the deltas (thigh x, shin x, foot x) and the
    hips' move for the thigh roots."""
    B = bones_of(J)
    fr = frames(J, {})
    out = {}
    dy, dz = hips
    for (th, sh, ft), x in LEG:
        H = np.array(B[th]["pivot"], dtype=float) + [0, dy, -dz]
        A0 = at(fr, sh, B[ft]["pivot"])
        A = A0 + ([0, lead[0], -lead[1]] if x > 0 else [0, 0, 0])
        L1 = np.linalg.norm(np.subtract(B[sh]["pivot"], B[th]["pivot"]))
        L2 = np.linalg.norm(np.subtract(B[ft]["pivot"], B[sh]["pivot"]))
        v = A - H
        D = min(math.hypot(v[1], v[2]), L1 + L2 - 1e-3)
        # the thigh's absolute angle from straight down, + forward (-z); the knee ahead of the hip-ankle line
        down = math.degrees(math.atan2(-v[2], -v[1]))
        ca = (L1 * L1 + D * D - L2 * L2) / (2 * L1 * D)
        a1 = math.degrees(math.acos(max(-1.0, min(1.0, ca))))
        cb = (L1 * L1 + L2 * L2 - D * D) / (2 * L1 * L2)
        knee = 180.0 - math.degrees(math.acos(max(-1.0, min(1.0, cb))))
        thigh = down + a1
        shin = -knee
        rt, rs = B[th].get("rotation", [0, 0, 0])[0], B[sh].get("rotation", [0, 0, 0])[0]
        dth, dsh = thigh - rt, shin - rs
        out[th] = [round(dth, 1), 0.0, 0.0]
        out[sh] = [round(dsh, 1), 0.0, 0.0]
        out[ft] = [round(-(dth + dsh), 1), 0.0, 0.0]
        out[th + "@move"] = [0.0, round(dy, 2), round(-dz, 2)]
    return out


def hal_between(J, cuff, fist, clip, n=48):
    """The in-betweens as the game plays them (anim_lab.sample, the same monotone cubic): the worst the front fist
    leaves the haft, the rear wrist its cuff, and the planted rear ankle slides."""
    import anim_lab
    B = bones_of(J)
    fr0 = frames(J, {})
    rest_ankle = at(fr0, "right_shin", B["right_foot"]["pivot"])
    worst = {"front fist off the haft (px)": 0.0, "front axis off the haft (deg)": 0.0,
             "rear wrist off the cuff (px)": 0.0, "rear forearm off the cuff (deg)": 0.0,
             "rear ankle slid (px)": 0.0}
    for i in range(n + 1):
        s = anim_lab.sample(clip, i / n)
        pose = {b: v for b, v in s.items() if "@" not in b}
        move = {b[:-5]: v for b, v in s.items() if "@" in b}
        fr = frames(J, pose, move)
        grip = at(fr, "halberd", B["halberd"]["pivot"])
        haft = fr["halberd"][0] @ [0, 1.0, 0]
        c = at(fr, "left_fore", fist[0])
        e, off, _, _ = hal_measure(J, cuff, dict(pose, _move=move))
        vals = (float(np.linalg.norm(np.cross(c - grip, haft))), angle(fr["left_fore"][0] @ fist[1], haft), e, off,
                float(np.linalg.norm(at(fr, "right_shin", B["right_foot"]["pivot"]) - rest_ankle)))
        for k, v in zip(worst, vals):
            worst[k] = max(worst[k], v)
    print("  in-betweens (%d samples): " % n + ", ".join("%s %.2f" % (k, v) for k, v in worst.items()))


def halberdier(a):
    J = load(HAL)
    W_l, axis_l, ci = hal_cuff(J)
    cuff = (W_l, axis_l)
    fist = hal_left_fist(J)
    print("== %s: the rear cuff is halberd[%d], its wrist end %s; the front fist centre %s axis %s" % (
        HAL, ci, np.round(W_l, 2), np.round(fist[0], 2), np.round(fist[1], 2)))
    e, off, flex, pt = hal_measure(J, cuff, {})
    print(KEY_LINE % ("rest", e, off, flex, pt, contacts(J, {})))
    keys = []
    prev_left = None
    for spec in HAL_KEYS:
        if "body" not in spec:                    # the loop closes on the guard
            keys.append(dict(keys[0], p=spec["p"]))
            continue
        body = list(spec["body"])
        haft = hal_haft(J, body, *spec["haft"])
        pose, tg = hal_key(J, cuff, body, haft, spec["flex"], spec["wrist"], spec.get("out", 0.0))
        hips = spec["hips"]
        pose["_move"]["body"] = [0.0, hips[0], -hips[1]]
        left, (lm, loff, lflex) = hal_left(J, fist, pose, spec["d"], prev_left)
        prev_left = list(left["left_arm"]) + [left["left_fore"][0]]
        pose.update(left)
        pose["head"] = [0.0, round(-0.8 * body[1], 1), 0.0]        # the eyes stay on the target
        pose["tassel"] = [float(spec["tas"]), 0.0, 0.0]
        pose["tabard_front"] = [float(spec["tab"][0]), 0.0, 0.0]
        pose["tabard_back"] = [float(spec["tab"][1]), 0.0, 0.0]
        legs = hal_legs(J, hips, spec["lead"])
        e, off, flex, pt = hal_measure(J, cuff, pose)
        full = {k: v for k, v in pose.items() if not k.startswith("_")}
        full.update({k: v for k, v in legs.items() if "@" not in k})
        c = contacts(J, dict(full, _move=dict(pose["_move"], **{k[:-5]: v for k, v in legs.items() if "@" in k})),
                     only=("right_fore", "right_arm", "left_fore", "left_arm", "halberd"))
        print(KEY_LINE % ("p=%.2f" % spec["p"], e, off, flex, pt, c))
        print("             front fist %.2f px off the haft, axis %4.1f deg off it, elbow flex %3.0f; legs %s" % (
            lm, loff, lflex, {k: v[0] for k, v in legs.items() if "@" not in k}))
        if not (e < 0.1 and off <= WRIST_FREE + 0.5):
            print("  !! rear hand off at p=%.2f" % spec["p"])
        if not (lm < 0.35 and loff <= WRIST_FREE + 0.5):
            print("  !! front hand off at p=%.2f" % spec["p"])
        key = {"p": spec["p"]}
        for b in ("body", "halberd", "right_arm", "right_fore", "left_arm", "left_fore", "head", "tassel",
                  "tabard_front", "tabard_back"):
            key[b] = [float(x) for x in pose[b]]
        key["halberd@move"] = pose["_move"]["halberd"]
        key["body@move"] = pose["_move"]["body"]
        for k, v in legs.items():
            key[k] = v
        keys.append(key)
    with open(HAL_ANIM) as f:
        data = json.load(f)
    ready = {k: v for k, v in keys[0].items() if k != "p"}
    hal_between(J, cuff, fist, {"keys": keys})
    data["poses"] = {"ready": ready}
    data["clips"] = {"thrust": {"ticks": HAL_TICKS, "hit_p": HAL_HIT, "strike": "right_fore", "from": "ready",
                                "keys": keys}}
    with open(HAL_ANIM, "w") as f:
        json.dump(data, f, indent=1)
    print("wrote", os.path.relpath(HAL_ANIM, ROOT), "(%d keys)" % len(keys))
    if a.write_poses:
        def as_pose(key):      # the contact sweep's form: "_move": {bone: px}
            out = {k: v for k, v in key.items() if k != "p" and "@" not in k}
            out["_move"] = {k[:-5]: v for k, v in key.items() if k.endswith("@move")}
            return out
        write_poses(HAL, {"ready": as_pose(keys[0]), "draw_back": as_pose(keys[1]), "thrust": as_pose(keys[3])})


KEY_LINE = "  %-10s wrist %.2f px off the cuff, forearm %4.1f deg off it, elbow flex %3.0f, point %+4.0f deg out   %s"

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
