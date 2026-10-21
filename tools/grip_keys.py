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
HAL_TICKS = 18                # the clip's length in the lab; the game plays it over the unit's swing (Lane A sets that)
HAL_HIT = 0.27
LEG = ((("left_thigh", "left_shin", "left_foot"), 1.7), (("right_thigh", "right_shin", "right_foot"), -1.7))

# Per key: p, body (the pelvis: lean, yaw: + = the left side forward, roll), chest (the trunk over it, added), hips move (y, z: px, + forward), the haft's
# WORLD direction (pitch up, yaw in: + = the point toward the centre line), the rear elbow's flex, its wrist's bend,
# d (the front fist up the haft from the rear grip, px), the lead ankle (dy up, dz forward of its rest), tassel x
# (it lags the haft: back as the point drives, forward as it stops), tab (tabard front, back: the panels lag the
# hips and are pushed by the lead thigh).
BLADE_UP = 10.0              # the axe blade this far from straight up (toward outward): the halberd's own roll
BLADE_ROLL = -12.0           # the axe blade canted from outward toward DOWN (the fist rolled so the haft lies inside the forearm's line)
EO = (-7.2, 15.8, 3.6)        # the rear elbow: out beside the hip and a little behind (chest frame)
HAL_KEYS = (
    # THE MECHANIC (round 6, lovish): a shove-thrust - the LEAD hand is a guide ring that moves only with the body, the
    # REAR hand drives forward and UP along the haft from the hip toward it, so the haft slides through the lead fist
    # and the gap between the fists CLOSES (9.6 px in the guard -> 2.0 at the hit); the rear fist keeps its own grip.
    # Round 7: the stance is BLADED in the guard (shoulders 36 deg, the lead shoulder to the target) and UNWINDS
    # toward square at the hit; the hips keep going after contact with the rear heel lifting, then recover.
    # Round 8: Meyer's guard is butt LOW, point HIGH - the rear fist at the rear hip in height too, the haft 24 deg
    # up, levelling at the hit; both arms EXTEND at the hit (the rear straight, the lead ~150 deg); the tabard
    # drags back through the drive and overswings FORWARD when the body stops (t10-12).
    dict(p=0.0,  body=(-4, 16, 0), chest=(-3, 20, 0), hips=(-0.6, 0.0),  haft=(18, 24), flex=95, slide=0.0, d=9.0,
         lead=(0, 0), tas=0, plume=0, tab=(0, 0), head=-2),
    # the windup (one tick): the hips LOAD BACK and sink, the trunk turns further, the rear hand draws back a touch
    # (the haft back through the guide fist), the point dips; the lead foot comes off the ground
    dict(p=0.06, body=(-2, 18, 0), chest=(-1, 22, 0), hips=(-0.9, -0.6), haft=(16, 27), flex=102, slide=-0.6,
         d=9.0, lead=(0.8, 0.4), tas=6, plume=-2, tab=(2, 2), head=-2),
    # the launch: the hips go first, the head leads, the shoulders start to unwind, the rear hand just starting
    dict(p=0.12, body=(-5, 16, 0), chest=(-3, 18, 0), hips=(-1.1, 1.0),  haft=(15, 24), flex=92, slide=-0.2,
         d=8.6, lead=(1.3, 2.0), tas=-6, plume=3, tab=(5, -4), head=-3),
    # the drive, spread over t3-t6: the hips travelling, the rear leg straightening, the lead foot swinging forward
    # in the air, the shoulders unwinding through, the rear fist driving up the haft toward the guide fist, the
    # tabard dragging back
    dict(p=0.19, body=(-10, 13, 0), chest=(-7, 12, 0), hips=(-1.4, 2.8),  haft=(13, 21), flex=74, slide=0.0, d=6.5,
         roll=-25, lead=(1.3, 4.4), tas=-16, plume=7, tab=(8, -10), head=-5),
    dict(p=0.25, body=(-14, 11, 0), chest=(-10, 7, 0), hips=(-1.6, 4.8), haft=(12, 19), flex=50, slide=0.2, d=4.2,
         roll=-40, lead=(0.9, 7.0), tas=-22, plume=11, tab=(10, -14), head=-6),
    # full extension (the hit at p=0.27 lands in this segment at the elbow's peak speed): the rear arm straight, the
    # lead arm ~150 deg, the fists together out in front of the chest, the haft level, the trunk 28 deg forward with
    # its eyes on the point, the lead foot just landing
    dict(p=0.31, body=(-17, 10, 0), chest=(-11, 6, 0), hips=(-1.8, 6.3), haft=(10, 18), flex=24, slide=0.3, d=2.2,
         roll=-50, lead=(0.2, 10.0), tas=-10, plume=12, tab=(6, -12), head=-6),
    # after contact the hips KEEP GOING (the shove): the rear heel lifts off the planted toe, the point dips a hair
    # (a visible one-tick overshoot), the shoulders square
    dict(p=0.39, body=(-18, 8, 0), chest=(-11, 5, 0), hips=(-1.9, 7.6), haft=(7, 17), flex=18, slide=0.3, d=2.0,
         roll=-50, lead=(0, 10.4), rear=(0.8, 0.5, -14), tas=6, plume=4, tab=(2, -8), head=-6),
    dict(p=0.47, body=(-15, 9, 0), chest=(-9, 6, 0),  hips=(-1.7, 8.2), haft=(10, 18), flex=32, slide=0.2, d=2.6,
         roll=-48, lead=(0, 10.4), rear=(0.9, 0.6, -16), tas=14, plume=-8, tab=(-2, 0), head=-5),
    # the recovery (twice the thrust's length): the weight comes back off the lead foot, the rear heel comes down,
    # the arms draw back in, the point rises, the spine unfolds; the cloth and the plume OVERSWING forward past
    # the stopping body (t10-12)
    dict(p=0.58, body=(-9, 10, 0), chest=(-5, 10, 0), hips=(-1.2, 4.2),  haft=(14, 21), flex=66, slide=0.0, d=5.6,
         roll=-30, lead=(0.8, 6.2), rear=(0.3, 0.08, -6), tas=8, plume=-6, tab=(-6, 24), head=-4),
    # the lead foot steps back home
    dict(p=0.70, body=(-5, 14, 0), chest=(-3, 17, 0), hips=(-0.8, 0.8),  haft=(16, 23), flex=88, slide=0.0, d=8.2,
         lead=(0.9, 1.4), tas=3, plume=1, tab=(-2, 8), head=-2),
    # the overshoot home, staggered so nothing stops together: the trunk and hips peak first (p=0.78), then the
    # point and the hands (0.84: 4 deg of point past the guard, the rear hand a touch far back), then the cloth and
    # the plume (0.90) - each eases the last ticks home
    dict(p=0.78, body=(-2.5, 17.5, 0), chest=(-1.5, 22, 0), hips=(-0.45, -0.35), haft=(20.5, 25.5), flex=97,
         slide=-0.2, d=9.0, lead=(0, 0), tas=2, plume=0.8, tab=(0, -2), head=-1),
    dict(p=0.84, body=(-3, 17, 0),  chest=(-2, 21, 0), hips=(-0.5, -0.25), haft=(22, 26), flex=100, slide=-0.4,
         d=9.0, lead=(0, 0), tas=1, plume=0.4, tab=(0, -1), head=-1.3),
    dict(p=0.90, body=(-3.5, 16.5, 0), chest=(-2.5, 20.5, 0), hips=(-0.55, -0.15), haft=(20.5, 25.2), flex=98,
         slide=-0.3, d=9.0, lead=(0, 0), tas=0.5, plume=0.2, tab=(0, 0), head=-1.6),
    dict(p=1.0),                                                   # = the guard
)


def rodrigues(axis, deg):
    k, a = unit(axis), math.radians(deg)
    K = np.array([[0, -k[2], k[1]], [k[2], 0, -k[0]], [-k[1], k[0], 0]])
    return np.eye(3) + math.sin(a) * K + (1 - math.cos(a)) * (K @ K)


def hal_rear(J):
    """The rear fist as built on the right forearm (gen_halberdier: gauntlet_grip on right_fore, round 4). In the
    forearm's rest-ROTATED frame: the haft's direction q and the blade's b (the fist is rigid with the forearm bone:
    posing the forearm poses the halberd), the forearm's own direction f0 (elbow -> wrist), and the grip point C in
    the forearm's own (unrotated) space - the haft slides through the fist along q."""
    B = bones_of(J)
    fr = frames(J, {})
    M0, t0 = fr["right_fore"]
    el, wr = at(fr, "right_fore", B["right_fore"]["pivot"]), at(fr, "right_fore", HAL_WRIST)
    return {"C": M0.T @ (np.array(B["halberd"]["pivot"], dtype=float) - t0), "R_f0": M0,
            "q": M0.T @ [0, 1.0, 0], "b": M0.T @ [-1.0, 0, 0], "f0": M0.T @ unit(wr - el)}


def hal_key(J, rear, body, chest, pitch, yaw, flex, slide, elbow_at, prev_psi=None, roll=None):
    """The rear arm and the halberd in it. The haft's WORLD direction (pitch up, yaw in) and the blade up fix the
    fist's - the forearm bone's - rotation outright (the fist is rigid on it), so the forearm's direction follows
    (REAR_GRIP off the haft). The elbow then lies on a cone round that line: its swivel is chosen to bring it
    nearest `elbow_at` (chest frame) and nearest the last key's; the forearm's rotation under the upper arm comes
    out with the flex about the hinge AND a pronation about its own axis - as a real forearm turns. The halberd
    takes the fist's rotation and is moved so its grip point sits in the fist pushed `slide` px forward along the
    haft (the fist that much nearer the butt). Returns the pose and the haft's direction."""
    B = bones_of(J)
    fr = frames(J, {"body": list(body), "chest": list(chest)})
    Mp = fr["halberd_arm"][0]
    Ma = fr["halberd_arm"][0]
    S = at(fr, "right_arm", B["right_arm"]["pivot"])
    upper = np.linalg.norm(np.subtract(B["right_fore"]["pivot"], B["right_arm"]["pivot"]))
    y = np.array([0, 1.0, 0])
    h = unit((math.sin(math.radians(yaw)) * math.cos(math.radians(pitch)), math.sin(math.radians(pitch)),
              -math.cos(math.radians(yaw)) * math.cos(math.radians(pitch))))
    # the fist's roll about the haft sets where the forearm comes from (REAR_GRIP off the haft, on a cone round it)
    # and which way the axe blade faces: the roll is chosen between the two - the elbow near its target and the
    # blade near outward-canted-up (BLADE_ROLL; the planted halberd carries the blade outward), the elbow's own
    # swivel round the forearm free on top; both near the last key's (one branch along the clip)
    out = np.array([-1.0, 0, 0])
    bl0 = rodrigues(h, BLADE_ROLL if roll is None else roll) @ unit(out - h * (h @ out))
    q, b = rear["q"], rear["b"]
    tb = at(fr, "chest", elbow_at)
    best = None
    for rho in range(-90, 91, 3):
        bl = rodrigues(h, rho) @ bl0
        R_fist = np.stack([bl, h, np.cross(bl, h)], axis=1) @ np.stack([b, q, np.cross(b, q)], axis=1).T
        f = unit(R_fist @ rear["f0"])                          # the forearm's direction, elbow -> wrist
        bend0 = unit(np.cross(f, [0, 0, 1.0]))
        for psi in range(0, 360, 4):
            bend = rodrigues(f, psi) @ bend0
            u = turn(f, bend, -flex)
            E = S + upper * u
            dd = float(np.linalg.norm(E - tb)) + 0.04 * abs(rho)
            if prev_psi is not None:
                dd += 0.01 * (min(abs(psi - prev_psi[0]), 360 - abs(psi - prev_psi[0])) + abs(rho - prev_psi[1]))
            if best is None or dd < best[0]:
                best = (dd, R_fist, f, bend, u, psi, rho)
    _, R_fist, f, bend, u, psi, rho = best
    rest_arm = B["right_arm"].get("rotation", [0, 0, 0])
    rest_fore = B["right_fore"].get("rotation", [0, 0, 0])
    R_arm = arm_frame(u, np.cross(bend, u))
    arm = local_euler(Ma, R_arm)
    fore = local_euler(R_arm, R_fist)
    pose = {"body": list(body), "chest": list(chest),
            "right_arm": r1([arm[k] - rest_arm[k] for k in range(3)]),
            "right_fore": r1([fore[k] - rest_fore[k] for k in range(3)])}
    fr2 = frames(J, pose, {})                                 # the arm as the game plays it
    Rh = fr2["right_fore"][0] @ rear["R_f0"].T                # the halberd rigid with the fist ...
    h_act = Rh @ y
    # ... then COUNTER-ROLLED about its own axis so the axe blade stands up (BLADE_UP degrees from straight up toward
    # outward): a round haft turns inside a closed fist, so the hand's roll (which places the arm) and the blade's
    # (which the side view reads: axe, spike and hook in profile) are decoupled (round 5)
    up = unit(y - h_act * (h_act @ y))
    want = rodrigues(h_act, -BLADE_UP) @ up
    blade = Rh @ [-1.0, 0, 0]
    gam = math.degrees(math.atan2(float(np.cross(blade, want) @ h_act), float(blade @ want)))
    Rh = rodrigues(h_act, gam) @ Rh
    hal = local_euler(Mp, Rh)
    C_w = at(fr2, "right_fore", rear["C"])
    fr3 = frames(J, dict(pose, halberd=hal), {})
    move = Mp.T @ (C_w + slide * h_act - at(fr3, "halberd", B["halberd"]["pivot"]))   # the grip point `slide` px toward the butt
    pose["halberd"] = r1(hal)
    pose["_move"] = {"halberd": [round(float(v), 2) for v in move]}
    pose["_rho"] = (psi, rho)
    return pose, h_act


def hal_measure(J, rear, pose):
    """(the haft's miss through the rear fist px, the slide px, elbow flex deg, elbow point: degrees from straight
    back toward out)"""
    B = bones_of(J)
    fr = frames(J, {k: v for k, v in pose.items() if not k.startswith("_")}, pose.get("_move", {}))
    C_w = at(fr, "right_fore", rear["C"])
    piv = at(fr, "halberd", B["halberd"]["pivot"])
    h = fr["halberd"][0] @ [0, 1.0, 0]
    miss = float(np.linalg.norm(np.cross(C_w - piv, h)))
    slide = float((piv - C_w) @ h)
    el = at(fr, "right_fore", B["right_fore"]["pivot"])
    sh = at(fr, "right_arm", B["right_arm"]["pivot"])
    wr = at(fr, "right_fore", HAL_WRIST)
    flex = 180.0 - angle(sh - el, wr - el)                    # the true elbow angle, whatever the Euler split
    pt = fr["chest"][0].T @ (fr["right_fore"][0] @ [0, 0, 1.0])
    return miss, slide, flex, math.degrees(math.atan2(-pt[0], pt[2]))


def hal_left_fist(J):
    """The front fist as built on the left forearm (gen_halberdier.arm hand="fist"): its centre and axis in the
    forearm's rest space (rebuilt on a scratch bone with the generator's own numbers)."""
    m = _gm.Model("scratch", "sunforged", "", {}, {})
    th = math.radians(_gh.LEFT_GRIP)
    g = _gm.grip_hand(_gm.Bone(m, "_s", [0, 0, 0]), None, (0, -math.cos(th), -math.sin(th)), _gh.GRIP_R, 1, "plate",
                      back=(0, 1, 0), wrist=HAL_LWRIST)
    return np.array(g["centre"], dtype=float), unit(g["axis"])


def hal_left(J, fist, pose, d, start=None, lflex=None, rear=None):
    """The front arm onto the haft: left_arm (x, y, z) and the elbow's flex so the fist's centre sits d px up the haft
    from the rear grip with its axis along the haft (the wrist within WRIST_FREE), the elbow kept down and out.
    Returns the deltas and (miss px, axis off the haft deg, flex)."""
    from scipy.optimize import minimize
    B = bones_of(J)
    C_l, a_l = fist
    base = {k: v for k, v in pose.items() if not k.startswith("_")}
    move = pose.get("_move", {})
    fr0 = frames(J, base, move)
    grip = at(fr0, "right_fore", rear["C"]) if rear else at(fr0, "halberd", B["halberd"]["pivot"])   # the rear FIST
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
        lock = (flex - lflex) ** 2 if lflex is not None else 0.0   # a key that wants the elbow locked (or set)
        if lflex is not None:                                   # a straight arm puts its elbow where the line goes
            up = inb = 0.0
        return 20 * miss + 0.1 * off ** 2 + pen + 2 * up + 2 * inb + 0.003 * cont + 0.01 * lock
    best = None
    for s in ([start] if start else []) + [[60, 0, 0, 20], [40, 30, -10, 40], [25, 0, -20, 8], [45, 20, -30, 12],
                                           [15, -15, 0, 5]]:
        r = minimize(cost, s, method="Nelder-Mead", options=dict(xatol=0.01, fatol=1e-7, maxiter=4000))
        if best is None or r.fun < best.fun:
            best = r
    v = best.x
    c, ax, el = parts(v)
    out = {"left_arm": r1(v[:3]), "left_fore": [round(float(v[3]), 1), 0.0, 0.0]}
    return out, (float(np.linalg.norm(c - target)), angle(ax, haft), rest_fore + float(v[3]))


def hal_legs(J, pose, lead, rear=(0.0, 0.0, 0.0)):
    """Both legs by 2-link IK under the POSED pelvis (the thighs hang from "body" since round 3, so the pelvis's turn
    and travel carry the hip joints): the rear ankle at its rest place (planted), the lead ankle `lead` (dy, dz
    forward) off its rest place, the leg's swing plane kept sagittal in the WORLD (the feet point ahead while the
    pelvis turns over them), the soles kept level. Returns the thigh (x, y, z), shin x and foot x deltas."""
    B = bones_of(J)
    fr0 = frames(J, {})
    base = {k: v for k, v in pose.items() if not k.startswith("_")}
    fr = frames(J, base, pose.get("_move", {}))
    Mb = fr["body"][0]
    out = {}
    for (th, sh, ft), x in LEG:
        H = at(fr, th, B[th]["pivot"])
        A0 = at(fr0, sh, B[ft]["pivot"])
        A = A0 + ([0, lead[0], -lead[1]] if x > 0 else [0, rear[0], -rear[1]])   # the rear: a heel lift
        L1 = np.linalg.norm(np.subtract(B[sh]["pivot"], B[th]["pivot"]))
        L2 = np.linalg.norm(np.subtract(B[ft]["pivot"], B[sh]["pivot"]))
        v = A - H
        D = min(float(np.linalg.norm(v)), L1 + L2 - 1e-3)
        ud = unit(v)                                          # hip -> ankle
        f = np.array([0, 0, -1.0])
        fwd = unit(f - ud * (ud @ f))                         # world forward, square to the hip-ankle line
        ca = (L1 * L1 + D * D - L2 * L2) / (2 * L1 * D)
        a1 = math.acos(max(-1.0, min(1.0, ca)))
        cb = (L1 * L1 + L2 * L2 - D * D) / (2 * L1 * L2)
        knee = 180.0 - math.degrees(math.acos(max(-1.0, min(1.0, cb))))
        u = ud * math.cos(a1) + fwd * math.sin(a1)            # the thigh: the knee ahead of the hip-ankle line
        front = unit(np.array([0, 0, -1.0]) - u * (u @ [0, 0, -1.0]))
        rot = local_euler(Mb, arm_frame(u, front))
        rest_t = B[th].get("rotation", [0, 0, 0])
        rs = B[sh].get("rotation", [0, 0, 0])[0]
        dth = [rot[k] - rest_t[k] for k in range(3)]
        dsh = -knee - rs
        out[th] = r1(dth)
        out[sh] = [round(dsh, 1), 0.0, 0.0]
        # the sole LEVEL in the world (round 4: under the leaned pelvis the toe went 1 px into the ground): the foot's
        # pitch scanned so its forward edge is as level as at rest
        ex = dict(base, **{th: out[th], sh: out[sh]})
        want = float((fr0[ft][0] @ [0, 0, -1.0])[1])
        best = None
        for dx in [x / 4.0 for x in range(-320, 321)]:
            ex[ft] = [dx, 0.0, 0.0]
            fz = frames(J, ex, pose.get("_move", {}))[ft][0] @ [0, 0, -1.0]
            err = abs(float(fz[1]) - want)
            if best is None or err < best[0]:
                best = (err, dx)
        out[ft] = [round(best[1] + (0.0 if x > 0 else rear[2]), 1), 0.0, 0.0]   # the heel up: the toe stays down
    return out


def hal_between(J, rear, fist, clip, n=48):
    """The in-betweens as the game plays them (anim_lab.sample, the same monotone cubic): the worst the front fist
    leaves the haft, the rear wrist its cuff, and the planted rear ankle slides."""
    import anim_lab
    B = bones_of(J)
    fr0 = frames(J, {})
    rest_ankle = at(fr0, "right_shin", B["right_foot"]["pivot"])
    worst = {"front fist off the haft (px)": 0.0, "front axis off the haft (deg)": 0.0,
             "haft off the rear fist (px)": 0.0, "rear ankle slid (px)": 0.0, "feet off level (deg)": 0.0}
    for i in range(n + 1):
        s = anim_lab.sample(clip, i / n)
        pose = {b: v for b, v in s.items() if "@" not in b}
        move = {b[:-5]: v for b, v in s.items() if "@" in b}
        fr = frames(J, pose, move)
        grip = at(fr, "right_fore", rear["C"])
        haft = fr["halberd"][0] @ [0, 1.0, 0]
        c = at(fr, "left_fore", fist[0])
        e, _, _, _ = hal_measure(J, rear, dict(pose, _move=move))
        lvl = max(abs(math.degrees(math.asin(float((fr[ft][0] @ [0, 0, -1.0])[1])))) for ft in ("left_foot", "right_foot"))
        vals = (float(np.linalg.norm(np.cross(c - grip, haft))), angle(fr["left_fore"][0] @ fist[1], haft), e,
                float(np.linalg.norm(at(fr, "right_shin", B["right_foot"]["pivot"]) - rest_ankle)), lvl)
        for k, v in zip(worst, vals):
            worst[k] = max(worst[k], v)
    print("  in-betweens (%d samples): " % n + ", ".join("%s %.2f" % (k, v) for k, v in worst.items()))


def halberdier(a):
    J = load(HAL)
    rear = hal_rear(J)
    fist = hal_left_fist(J)
    print("== %s: the rear fist's grip point %s; the front fist centre %s axis %s" % (
        HAL, np.round(rear["C"], 2), np.round(fist[0], 2), np.round(fist[1], 2)))
    keys = []
    prev_left = prev_rho = None
    for spec in HAL_KEYS:
        if "body" not in spec:                    # the loop closes on the guard
            keys.append(dict(keys[0], p=spec["p"]))
            continue
        body, chest = list(spec["body"]), list(spec["chest"])
        pose, h = hal_key(J, rear, body, chest, spec["haft"][0], spec["haft"][1], spec["flex"], spec["slide"], EO,
                          prev_rho, spec.get("roll"))
        prev_rho = pose.pop("_rho")
        hips = spec["hips"]
        pose["_move"]["body"] = [0.0, hips[0], -hips[1]]
        left, (lm, loff, lflex) = hal_left(J, fist, pose, spec["d"], prev_left, spec.get("lflex"), rear)
        prev_left = list(left["left_arm"]) + [left["left_fore"][0]]
        pose.update(left)
        # the eyes stay on the point: the head counters the trunk's turn - and LEADS it (it counters the NEXT key's
        # turn, so it turns a beat before the shoulders); `head` = the helm's WORLD pitch (- = down), so the chin
        # comes up as the trunk folds
        nxt = HAL_KEYS[min(len(HAL_KEYS) - 2, HAL_KEYS.index(spec) + 1)]
        nb, nc = nxt.get("body", HAL_KEYS[0]["body"]), nxt.get("chest", HAL_KEYS[0]["chest"])
        turn_now, turn_next = body[1] + chest[1], nb[1] + nc[1]
        pose["head"] = [round(spec["head"] - (body[0] + chest[0]), 1),
                        round(-0.75 * (0.4 * turn_now + 0.6 * turn_next), 1), 0.0]
        pose["tassel"] = [float(spec["tas"]), 0.0, 0.0]
        pose["plume"] = [float(spec["plume"]), 0.0, 0.0]
        pose["tabard_front"] = [float(spec["tab"][0]), 0.0, 0.0]
        pose["tabard_back"] = [float(spec["tab"][1]), 0.0, 0.0]
        legs = hal_legs(J, pose, spec["lead"], spec.get("rear", (0.0, 0.0, 0.0)))
        miss, slide, flex, pt = hal_measure(J, rear, pose)
        full = {k: v for k, v in pose.items() if not k.startswith("_")}
        full.update(legs)
        c = contacts(J, dict(full, _move=pose["_move"]),
                     only=("right_fore", "right_arm", "left_fore", "left_arm", "halberd"))
        print("  p=%.2f     haft %.2f px off the rear fist, slid %+.1f px, elbow flex %3.0f, point %+4.0f deg out   %s"
              % (spec["p"], miss, slide, flex, pt, c))
        print("             front fist %.2f px off the haft, axis %4.1f deg off it, elbow flex %3.0f; legs %s" % (
            lm, loff, lflex, {k: v[0] for k, v in legs.items()}))
        if not (miss < 0.1 and abs(slide - spec["slide"]) < 0.1):
            print("  !! rear hand off at p=%.2f" % spec["p"])
        if not (lm < 0.35 and loff <= WRIST_FREE + 0.5):
            print("  !! front hand off at p=%.2f" % spec["p"])
        key = {"p": spec["p"]}
        for b in ("body", "chest", "halberd", "right_arm", "right_fore", "left_arm", "left_fore", "head", "tassel",
                  "plume", "tabard_front", "tabard_back"):
            key[b] = [float(x) for x in pose[b]]
        key["halberd@move"] = pose["_move"]["halberd"]
        key["body@move"] = pose["_move"]["body"]
        for k, v in legs.items():
            key[k] = v
        keys.append(key)
    with open(HAL_ANIM) as f:
        data = json.load(f)
    ready = {k: v for k, v in keys[0].items() if k != "p"}
    hal_between(J, rear, fist, {"keys": keys})
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
