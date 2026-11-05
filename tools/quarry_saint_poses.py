#!/usr/bin/env python3
"""The Quarry Saint's key poses as data (design sheet sections 3.1-3.7): per bone, DSL degrees ADDED to the model's
rest (the convention tools/game_preview.py --poses and tools/pose_preview.py take), plus "_move": {"pelvis": [dx, dy,
dz]} px for the pelvis offset. Every limb is SOLVED, not typed: the legs so each standing plinth lies flat on y = 0
(tools/gen_quarry_saint.plant_leg), the right arm so the fist holds the maul along the key's haft direction, the left
fist onto the haft about 11 px up from the right (arm_on_haft), the chain and the stole drops hung plumb. Where the
sheet's grip point cannot be reached by the stone arms (upper 11.2 + forearm 11 + grip 3.5 px from a shoulder 15 px
off the middle) the grip and the haft direction are moved to the nearest reachable ones (fit_grip) and the shift is
printed - the round log lists it per key.

    python3 tools/quarry_saint_poses.py --out poses.json [--report report.txt] [--only sweep_windup,slam_windup]
    python3 tools/game_preview.py design/models/sunforged_quarry_saint.json --team tabard,tabard_dark \\
        --poses @poses.json --views quarter,side,rts --out strip.png

Measured per key (--report): the fists' distance to the haft's axis and their place along it, the wrist bends, both
elbows' flexion (never negative: an elbow bent backward), the knees, each plinth's underside (0..0 = planted flat),
the maul head's lowest point, and what passes through what (tools/contact_check.py with the joints allowed 3.5 px).
Sign conventions (README): +x swings a hanging part forward and tips an upright part's top back; +y turns a forward
part toward the unit's right (-x); maul yaw 0 = forward, +90 = the unit's right; pitch +90 = straight up.
"""
import argparse
import copy
import json
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_quarry_saint as q  # noqa: E402
import contact_check  # noqa: E402

Z3 = (0.0, 0.0, 0.0)

# ------------------------------------------------------------------ the keys ----------------------------------------------
# torso / head / pelvis: degrees added to the rest; move: the pelvis offset px; feet: ankle offsets from rest (dx, dy,
# dz) and toe yaw (planted unless "lift"); legs: (thigh, shin) degrees for the walk (feet then levelled, the planted one
# set on y = 0 by the pelvis height); grip / yaw / pitch: the right fist and the haft's direction (None = keep the
# rest maul); left: "haft" or {bone: rotation added}; buried: the maul head's lowest point (y) the key asks for.
# r2: the maul carried OVER the right shoulder (haft ~35 deg from level, the head behind the back), not upright
CARRY = dict(grip=(-16.0, 38.0, -11.0), yaw=180.0, pitch=35.0, dir_tol=(10, 5), carry=True)
READY = dict(grip=(-12.0, 22.0, -10.0), yaw=-25.0, pitch=40.0)
HANG_L = {"shoulder_l": [0, 0, 0], "forearm_l": [0, 0, 0]}

KEYS = {
    "rest": dict(),
    # 3.3 walk, CARRY stance (maul on the right disc); shin values are the sheet's knee flexion (a knee bends -x)
    "walk_contact_R": dict(CARRY, move=(-1.0, None, 0.0), pelvis=(0, -3, 3), torso=(0, 3, -2), legs={"r": (22, -4),
                           "l": (-16, -18)}, toe={"l": -15}, planted="r", left={"shoulder_l": [12, 0, 0]}),
    "walk_pass_R": dict(CARRY, move=(-2.0, None, 0.0), pelvis=(0, 0, 2), torso=(0, 0, 0), legs={"r": (0, -2),
                        "l": (4, -34)}, planted="r", left={"shoulder_l": [0, 0, 0]}),
    "walk_contact_L": dict(CARRY, move=(1.0, None, 0.0), pelvis=(0, 3, -3), torso=(0, -3, 2), legs={"l": (22, -4),
                           "r": (-16, -18)}, toe={"r": -15}, planted="l", left={"shoulder_l": [-12, 0, 0]}),
    "walk_pass_L": dict(CARRY, move=(2.0, None, 0.0), pelvis=(0, 0, -2), torso=(0, 0, 0), legs={"l": (0, -2),
                        "r": (4, -34)}, planted="l", left={"shoulder_l": [0, 0, 0]}),
    # 3.4 the sweep (vs units), MIRRORED from the sheet: the right fist is the knob hand (GRIP_R by the pommel, the
    # left 11 px up toward the head), so the swing that keeps the arms uncrossed winds up on the LEFT and sweeps left
    # to right (a left-hander's swing); the sheet's right-to-left sweep crosses the forearms through the chest
    "sweep_windup": dict(grip=(4.0, 22.0, -12.0), yaw=-100.0, pitch=20.0, move=(1.5, -3.0, 0.0), pelvis=(0, 0, 0),
                         torso=(4, -35, 0), head=(0, 25, 0), feet={}, left="haft", head_out_left=14.0,
                         dir_tol=(10, 10)),
    "sweep_contact": dict(grip=(-2.0, 24.0, -15.0), yaw=-50.0, pitch=4.0, move=(0.5, -3.0, 0.0), pelvis=(0, 12, 0),
                          torso=(0, -2, 0), head=(0, -10, 0), feet={}, left="haft"),
    "sweep_follow": dict(grip=(-10.0, 22.0, -16.0), yaw=50.0, pitch=-4.0, move=(-1.0, -3.0, 0.0), pelvis=(0, 12, 0),
                         torso=(-6, 28, 0), head=(0, -12, 0), feet={"r": (0, 0, 0, 10)}, left="haft"),
    # 3.5 the slam (vs buildings)
    "slam_windup": dict(grip=(-4.0, 52.0, -2.0), yaw=180.0, pitch=70.0, move=(0.0, -3.5, 0.0), torso=(18, 0, 0),
                        head=(-15, 0, 0), feet={"l": (0, 0, -3, 0)}, left="haft"),
    "slam_contact": dict(grip=(-6.0, 22.0, -24.0), yaw=0.0, pitch=-35.0, buried=-3.0, move=(0.0, -5.0, -1.0),
                         torso=(-30, 0, 0), head=(10, 0, 0), feet={"l": (0, 0, -3, 0)}, left="haft"),
    "slam_wrench": dict(grip=(-6.0, 26.0, -21.0), yaw=0.0, pitch=-30.0, buried=-3.0, move=(0.0, -5.0, -1.0),
                        torso=(-18, 0, 0), head=(4, 0, 0), feet={"l": (0, 0, -3, 0)}, left="haft"),
    # 3.8 Gatefall
    "gatefall_windup": dict(grip=(-4.0, 54.0, 0.0), yaw=180.0, pitch=85.0, move=(0.0, -4.0, 0.0), torso=(24, 0, 0),
                            head=(-20, 0, 0), feet={"r": (-3, 0, 0, 0), "l": (3, 0, 0, 0)}, left="haft"),
    "gatefall_contact": dict(grip=(-6.0, 20.0, -24.0), yaw=0.0, pitch=-40.0, buried=-4.0, move=(0.0, -6.0, -1.0),
                             torso=(-38, 0, 0), head=(14, 0, 0), feet={"r": (-3, 0, 0, 0), "l": (3, 0, 0, 0)},
                             left="haft"),
    # 3.6 hit reactions (from the rest stance): a projectile glances (1.5 deg away, a hit from the front-left), the
    # stagger's peak (t4: the hit-side right plinth lifted 2 px and 4 px back, the torso rocked 8 deg back)
    "glance": dict(torso=(1.5, 0, -1.0), keep_rest_maul=True, feet={}),
    "stagger": dict(torso=(8, 0, 3), head=(4, 0, 0), move=(0.0, -1.0, 1.0), feet={"r": (0, 2, 4, 0)}, lift={"r"},
                    keep_rest_maul=True),
    # 3.7 death: t20 kneeling on the right knee (the maul still in the hand, its head on the ground: the release is
    # the clip's), t34 toppled forward onto the ground (the maul lying flat beside it, still in the fist)
    "death_kneel": dict(grip=(-22.0, 24.0, -16.0), yaw=15.0, pitch=-40.0, buried=0.0, kneel=True, torso=(-10, 0, 4),
                        head=(8, 0, 0), left={"shoulder_l": [6, 0, 6], "forearm_l": [10, 0, 0]}),
    "death_topple": dict(grip=(-20.0, 8.0, -34.0), yaw=20.0, pitch=0.0, buried=0.0, kneel=True, pelvis=(-25, 0, 4),
                         torso=(-46, 0, 6), head=(30, 0, 8), lie=True, left={"shoulder_l": [80, 0, 20], "forearm_l": [10, 0, 0]}),
}
# the sheet's pelvis drops (2-6 px) on legs of 8 + 6.5 px cost ~15-25 degrees of knee per px (a 3 px drop: an 83
# degree squat in the first solve); the planted keys take half the drop
DROP_SCALE = 0.5
# r2: knees capped (standing and walk keys): the pelvis drop is reduced until no planted knee bends past it
KNEE_CAP = 35.0
# r2: the arm parts that must stay out of the body, and the body they must stay out of (measured per key)
ARM_BONES = ("shoulder_r", "shoulder_l", "forearm_r", "forearm_l", "hand_r", "hand_l")
BODY_BONES = ("torso", "head", "pelvis", "halo")
# bone pairs that overlap by construction at their joint or seat (the contact list leaves them out; counted apart)
JOINT_PAIRS = {frozenset(p) for p in (("pelvis", "thigh_r"), ("pelvis", "thigh_l"), ("thigh_r", "shin_r"),
               ("thigh_l", "shin_l"), ("shin_r", "foot_r"), ("shin_l", "foot_l"), ("torso", "pauldron_r"),
               ("torso", "pauldron_l"), ("torso", "shoulder_r"), ("torso", "shoulder_l"), ("shoulder_r", "forearm_r"),
               ("shoulder_l", "forearm_l"), ("forearm_r", "hand_r"), ("forearm_l", "hand_l"), ("pelvis", "torso"),
               ("pauldron_r", "shoulder_r"), ("pauldron_l", "shoulder_l"), ("maul", "chain_0"), ("chain_0", "chain_1"),
               ("chain_1", "chain_2"), ("torso", "stole_front_l_0"), ("torso", "stole_front_r_0"),
               ("torso", "stole_back_0"), ("stole_front_l_0", "stole_front_l_1"), ("stole_front_r_0", "stole_front_r_1"), ("stole_back_0", "stole_back_1"), ("pelvis", "tabard_front_0"),
               ("tabard_front_0", "tabard_front_1"), ("torso", "cracks_hp66"), ("torso", "cracks_hp33"),
               ("torso", "shard_0"), ("torso", "shard_1"), ("torso", "shard_2"), ("head", "halo"))}
STANDING = {"rest", "sweep_windup", "sweep_contact", "sweep_follow", "slam_windup", "slam_contact", "slam_wrench",
            "gatefall_windup", "gatefall_contact", "glance"}


# ------------------------------------------------------------------ helpers -----------------------------------------------
def add_rot(m, bind, name, r):
    m.bones[name].rotation = [bind.bones[name].rotation[a] + r[a] for a in range(3)]


def body_box_arrays(m, T, extra=()):
    names = [n for n in ("torso", "pelvis", "head", "thigh_r", "thigh_l", "pauldron_r", "pauldron_l", "halo") + tuple(extra)
             if n in m.bones]
    boxes = q.posed_boxes(m, names, 0.0)
    lo = np.array([b[0] for b in boxes]) + np.array(T)
    hi = np.array([b[1] for b in boxes]) + np.array(T)
    return lo, hi


def fit_grip(m, G0, yaw0, pitch0, T, two_hands=True, ground=None, dir_tol=(20, 15), head_out_left=None,
             carry=False, k=1):
    """The reachable grip and haft direction nearest the key's: both shoulders within reach of their fists (11 to 23
    px from the shoulder pivot), the fists, the haft and the head out of the body (torso, pelvis, head, thighs), the
    head's centre not under the ground. Returns (G, yaw, pitch, shift px)."""
    SR = np.array(q.shoulder_world(m, -1, T))
    SL = np.array(q.shoulder_world(m, 1, T))
    lo, hi = body_box_arrays(m, T)
    lo_h, hi_h = body_box_arrays(m, T, ("shoulder_r",)) if carry else (lo, hi)
    cands = []
    ts = np.arange(-9.0, 41.0, 2.5)            # from the pommel's end (t -7) to past the head
    marg = np.where(ts > 27, 6.0, 1.75)[None, :, None, None]
    o = np.arange(-16, 17, 2.0)
    gx, gy, gz = np.meshgrid(o, o, o, indexing="ij")
    G = np.stack([gx.ravel(), gy.ravel(), gz.ravel()], -1) + np.array(G0)

    def inside(P, mg):
        return np.all((P[..., None, :] > lo - mg) & (P[..., None, :] < hi + mg), axis=-1).any(-1)

    base = 0.012 * ((G - np.array(G0)) ** 2).sum(-1)
    rr = np.linalg.norm(G - SR, axis=-1)
    base = base + 3.0 * (np.maximum(0, rr - 23.0) ** 2 + np.maximum(0, 11 - rr) ** 2)
    base = base + 8.0 * inside(G, 4.0)
    # r2: the forearm's path (shoulder -> fist, its last half) kept a forearm's half-thickness out of the body
    for f in (0.55, 0.75, 0.9):
        base = base + 6.0 * inside(SR + (G - SR) * f, 4.5)
    yaws = [d for d in (-20, -10, 0, 10, 20) if abs(d) <= dir_tol[0]]
    pits = [d for d in (-15, -10, -5, 0, 5, 10, 15) if abs(d) <= dir_tol[1]]
    for dyaw in yaws:
        for dp in pits:
            yaw, pitch = yaw0 + dyaw, pitch0 + dp
            d = np.array(q.maul_dir(yaw, pitch))
            c = base + 0.015 * (dyaw ** 2 + dp ** 2)
            if two_hands:
                GL = G + q.LEFT_ON_HAFT * d
                rl = np.linalg.norm(GL - SL, axis=-1)
                c = c + 3.0 * (np.maximum(0, rl - 23.0) ** 2 + np.maximum(0, 11 - rl) ** 2)
                c = c + 8.0 * inside(GL, 4.0)
                for f in (0.55, 0.75, 0.9):
                    c = c + 6.0 * inside(SL + (GL - SL) * f, 4.5)
            P = G[:, None, :] + ts[None, :, None] * d[None, None, :]
            ins = np.all((P[:, :, None, :] > lo_h[None, None] - marg) & (P[:, :, None, :] < hi_h[None, None] + marg),
                         axis=-1)
            c = c + 10.0 * ins.sum((1, 2))
            hc = G + 34.0 * d
            if ground is not None:
                c = c + 2.0 * np.maximum(0, ground - (hc[:, 1] - 6.0)) ** 2
            if head_out_left is not None:
                c = c + 0.5 * np.maximum(0, SL[0] + head_out_left - hc[:, 0]) ** 2
            for i in np.argsort(c)[:12]:
                cands.append((float(c[i]), tuple(float(v) for v in G[i]), yaw, pitch))
    cands.sort(key=lambda x: x[0])
    out = []
    for c in cands:                                   # the k best, at least 4 px or 5 deg apart
        if all(math.dist(c[1], o[1]) >= 4.0 or abs(c[2] - o[2]) + abs(c[3] - o[3]) >= 5 for o in out):
            out.append(c)
        if len(out) >= k:
            break
    res = [(o[1], o[2], o[3], math.dist(o[1], G0)) for o in out]
    return res[0] if k == 1 else res


def legs_planted(m, bind, spec, T):
    lifted = spec.get("lift", set())
    misses = {}
    for s in (-1, 1):
        k = q.sn(s)
        off = spec.get("feet", {}).get(k, (0, 0, 0, 0))
        A = tuple(q.rest_ankle(s)[i] + off[i] for i in range(3))
        misses[k] = q.plant_leg(m, s, A, T, yaw=off[3])
    return misses


def legs_by_angle(m, bind, spec):
    for s in (-1, 1):
        k = q.sn(s)
        th, sh = spec["legs"][k]
        m.bones["thigh_" + k].rotation = [th, 0, 0]
        m.bones["shin_" + k].rotation = [sh, 0, 0]
        m.bones["foot_" + k].rotation = [0, 0, 0]
        m.bones["foot_" + k].rotation = m.level_rotation("shin_" + k)
        toe = spec.get("toe", {}).get(k)
        if toe:
            r = m.bones["foot_" + k].rotation
            m.bones["foot_" + k].rotation = [r[0] + toe, r[1], r[2]]


def kneel(m, T_xz):
    """The right knee on the ground (thigh near plumb, shin lying back), the left foot planted ahead; returns the
    pelvis drop that puts the knee cop on y = 0."""
    m.bones["thigh_r"].rotation = [12, 0, 0]
    m.bones["shin_r"].rotation = [-100, 0, 0]
    m.bones["foot_r"].rotation = [0, 0, 0]
    m.bones["foot_r"].rotation = [m.level_rotation("shin_r")[0] - 20, 0, 0]   # toes curled under, heel up
    low = min(p[1] for c in m.bones["shin_r"].cubes for p in m.cube_corners("shin_r", c))
    low = min(low, min(p[1] for c in m.bones["foot_r"].cubes for p in m.cube_corners("foot_r", c)))
    dy = -low
    T = (T_xz[0], dy, T_xz[2])
    q.plant_leg(m, 1, (q.rest_ankle(1)[0], q.ANKLE_PIV[1], -7.0), T)
    return T


def max_knee(m):
    return max(abs(m.bones["shin_" + k].rotation[0]) for k in ("r", "l"))


def cap_knees(m, bind, spec, T):
    """Less pelvis drop until no knee bends past KNEE_CAP (bisection on the drop)."""
    if max_knee(m) <= KNEE_CAP + 0.5:
        return T
    lo, hi = T[1], 0.0                      # lo: too deep; hi: no drop (straight legs)
    for _ in range(10):
        mid = (lo + hi) / 2
        T2 = [T[0], mid, T[2]]
        legs_planted(m, bind, spec, T2)
        if max_knee(m) > KNEE_CAP:
            lo = mid
        else:
            hi = mid
    T = [T[0], hi, T[2]]
    legs_planted(m, bind, spec, T)
    return T


def arm_hits(dsl, bind, m, T):
    """Arm parts (upper arm, forearm, fist) into the body (torso, head, pelvis, halo): [(depth, arm bone, body bone)]."""
    extra = {k: v for k, v in deltas(bind, m, T).items() if not k.startswith("_")}
    hits = contact_check.check(dsl, extra, (), {"pelvis": list(T)}, threshold=0.6, joint=3.5)
    out = []
    for h in hits:
        a, b = h[1], h[4]
        if a in ARM_BONES and b in BODY_BONES and frozenset((a, b)) not in JOINT_PAIRS:
            out.append((round(h[0], 2), a, b))
        elif b in ARM_BONES and a in BODY_BONES and frozenset((a, b)) not in JOINT_PAIRS:
            out.append((round(h[0], 2), b, a))
    return sorted(out, reverse=True)


def solve_key(bind, name, spec, log, dsl=None):
    m = copy.deepcopy(bind)
    T = list(spec.get("move", Z3))
    for bone, key in (("pelvis", "pelvis"), ("torso", "torso"), ("head", "head")):
        if key in spec:
            add_rot(m, bind, bone, spec[key])
    note = []
    if spec.get("kneel"):
        T = list(kneel(m, (T[0], 0.0, T[2])))
    elif "legs" in spec:
        T[1] = 0.0
        legs_by_angle(m, bind, spec)
        lo, _hi = q.plinth_low(m, spec["planted"])
        T[1] = -lo
        free = "l" if spec["planted"] == "r" else "r"
        toe = spec.get("toe", {}).get(free, 0)
        for _ in range(80):
            low = min(p[1] for c in m.bones["foot_" + free].cubes for p in m.cube_corners("foot_" + free, c)) + T[1]
            if low >= 0.0:
                break
            if m.bones["shin_" + free].rotation[0] > -KNEE_CAP:
                m.bones["shin_" + free].rotation[0] -= 1.0      # more knee (to the cap): the toe lifts off the ground
            elif toe < 0:
                toe = min(0, toe + 1)                            # then less toe-off tilt
            else:
                T[1] -= low                                      # then the whole body a hair higher
                break
            m.bones["foot_" + free].rotation = [0, 0, 0]
            r = m.level_rotation("shin_" + free)
            m.bones["foot_" + free].rotation = [r[0] + toe, r[1], r[2]]
    elif "feet" in spec or T != list(Z3):
        if T[1] is None:
            T[1] = 0.0
        T[1] *= DROP_SCALE
        legs_planted(m, bind, spec, T)
        T = cap_knees(m, bind, spec, T)
    T = tuple(T)
    # the maul
    G_used = None
    if spec.get("keep_rest_maul") or "grip" not in spec:
        pass
    else:
        two = spec.get("left") == "haft"
        ground = spec.get("buried")
        if ground is None:
            ground = 0.5
        cands = fit_grip(m, spec["grip"], spec["yaw"], spec["pitch"], T, two_hands=two, ground=ground,
                         dir_tol=spec.get("dir_tol", (20, 15)), head_out_left=spec.get("head_out_left"),
                         carry=spec.get("carry", False), k=4)
        best = None
        base = copy.deepcopy(m)
        for ci, (G, yaw, pitch, shift) in enumerate(cands):
            mm = copy.deepcopy(base)
            if spec.get("buried") is not None:
                # put the head's lowest point where the key asks (buried / on the ground): walk the pitch
                for _ in range(8):
                    q.place_right(mm, G, q.maul_dir(yaw, pitch), T)
                    low = q.maul_head_low(mm, T)
                    if abs(low - spec["buried"]) < 0.15:
                        break
                    pitch -= math.degrees((low - spec["buried"]) / 34.0) * 0.9
            else:
                q.place_right(mm, G, q.maul_dir(yaw, pitch), T)
            if spec.get("left") == "haft":
                q.place_left(mm, T)
            if dsl is None:
                best = (0.0, ci, mm, G, yaw, pitch, shift, [])
                break
            hits = arm_hits(dsl, bind, mm, T)
            worst = hits[0][0] if hits else 0.0
            off = q.hand_to_haft(mm, "l", T)[0] if spec.get("left") == "haft" else 0.0
            score = worst + 3.0 * off + 0.02 * shift
            if best is None or score < best[0]:
                best = (score, ci, mm, G, yaw, pitch, shift, hits)
            if worst <= 1.0 and off <= 0.5:
                break                                    # clean enough: keep the nearest candidate that is
        _, ci, m, G, yaw, pitch, shift, _h = best
        G_used = (G, yaw, pitch, shift)
        note.append("grip %s yaw %.0f pitch %.1f (key %s yaw %.0f pitch %.0f; grip moved %.1f px; candidate %d)" % (
            tuple(round(v, 1) for v in G), yaw, pitch, spec["grip"], spec["yaw"], spec["pitch"], shift, ci))
    left = spec.get("left")
    if left == "haft":
        pass                                             # solved with the grip above
    elif isinstance(left, dict):
        for b, r in left.items():
            add_rot(m, bind, b, r)
    # the discs follow a third of the upper arm's swing; chain and stole drops hang plumb
    for k in ("r", "l"):
        if "pauldron_" + k not in m.bones:
            continue
        du = [m.bones["shoulder_" + k].rotation[a] - bind.bones["shoulder_" + k].rotation[a] for a in range(3)]
        m.bones["pauldron_" + k].rotation = [round(max(-45, min(45, 0.3 * du[0])), 3), 0,
                                             round(max(-20, min(20, 0.3 * du[2])), 3)]
    q.chain_plumb(m)
    q.stole_plumb(m)
    if spec.get("lie"):
        low = min(p[1] for n, b in m.bones.items() if n not in ("maul", "chain_0", "chain_1", "chain_2")
                  for c in b.cubes if not c.get("upgrade") for p in m.cube_corners(n, c)) + T[1]
        if low < 0:
            T = (T[0], T[1] - low, T[2])
            note.append("lifted %.1f px so the fallen body rests on y 0" % -low)
    return m, T, G_used, note


def wrap(a):
    a = (a + 180.0) % 360.0 - 180.0
    return round(a, 3)


def deltas(bind, m, T):
    out = {}
    for n, b in m.bones.items():
        d = [wrap(b.rotation[a] - bind.bones[n].rotation[a]) for a in range(3)]
        if any(abs(v) > 1e-3 for v in d):
            out[n] = d
    if any(abs(v) > 1e-6 for v in T):
        out["_move"] = {"pelvis": [round(v, 3) for v in T]}
    return out


def measure(name, bind, m, T, dsl):
    r = {}
    for k in ("r", "l"):
        dist, t = q.hand_to_haft(m, k, T)
        r["haft_" + k] = (round(dist, 3), round(t, 2))
        r["wrist_" + k] = round(q.wrist_bend(m, k), 1)
        r["elbow_" + k] = m.bones["forearm_" + k].rotation[0]
        r["knee_" + k] = m.bones["shin_" + k].rotation[0]
        lo, hi = q.plinth_low(m, k, T)
        r["plinth_" + k] = (round(lo, 3), round(hi, 3))
        el = m.fk("forearm_" + k, m.bones["forearm_" + k].pivot)
        sh = m.fk("shoulder_" + k, m.bones["shoulder_" + k].pivot)
        r["elbow_above_shoulder_" + k] = round(el[1] - sh[1], 1)
    r["maul_low"] = round(q.maul_head_low(m, T), 2)
    extra = {k: v for k, v in deltas(bind, m, T).items() if not k.startswith("_")}
    hits = contact_check.check(dsl, extra, (), {"pelvis": list(T)}, threshold=0.6, joint=3.5)
    r["contacts"] = [(round(h[0], 2), h[1], h[2], h[3], h[4], h[5], h[6]) for h in hits
                     if frozenset((h[1], h[4])) not in JOINT_PAIRS]
    r["joint_overlaps"] = sum(1 for h in hits if frozenset((h[1], h[4])) in JOINT_PAIRS)
    r["arm_body"] = arm_hits(dsl, bind, m, T)
    # every cube's lowest point (nothing under the ground but a buried maul head)
    lows = []
    for n, b in m.bones.items():
        for c in b.cubes:
            if c.get("upgrade"):
                continue
            y = min(p[1] for p in m.cube_corners(n, c)) + T[1]
            if y < -0.2:
                lows.append((round(y, 2), n))
    r["under_ground"] = sorted(lows)[:6]
    return r


def solve_all(only=None, massing="control"):
    bind, _ = q.build(massing)
    dsl = json.loads(json.dumps(bind.to_json()))
    import render_concept
    tmp = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".scratch", "_qs_bind_%s.json" % massing)
    os.makedirs(os.path.dirname(tmp), exist_ok=True)
    with open(tmp, "w") as f:
        json.dump(dsl, f)
    dsl = render_concept.load_model(tmp)
    poses, report = {}, {}
    for name, spec in KEYS.items():
        if only and name not in only:
            continue
        log = []
        m, T, G_used, note = solve_key(bind, name, spec, log, dsl)
        poses[name] = deltas(bind, m, T)
        report[name] = measure(name, bind, m, T, dsl)
        report[name]["note"] = note
        report[name]["standing"] = name in STANDING
        print("solved %-17s %s" % (name, "; ".join(note)), file=sys.stderr)
    return poses, report


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", default="-")
    ap.add_argument("--report", default=None)
    ap.add_argument("--only", default="")
    ap.add_argument("--massing", default="control", choices=q.MASSINGS,
                    help="control = the round-1 body; a | b | c = the round-2 massing greyboxes (gen_quarry_saint)")
    a = ap.parse_args()
    poses, report = solve_all([x for x in a.only.split(",") if x], a.massing)
    text = json.dumps(poses, indent=1)
    if a.out == "-":
        print(text)
    else:
        with open(a.out, "w") as f:
            f.write(text)
    if a.report:
        with open(a.report, "w") as f:
            json.dump(report, f, indent=1)


if __name__ == "__main__":
    main()
