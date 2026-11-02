#!/usr/bin/env python3
"""The shared human base clips (design/ANIMATION-STANDARD: one rig convention, shared base clips, per-unit parameters,
every clip starting and ending in its weapon class's ready stance) - built here for every Sunforged human body on our
rig and written as design/anims/<unit>.json, which tools/anim_lab.py plays, measures and generates into Java
(unit/modelling/models/gen/<Unit>Anim.java), and which HumanBase.java plays in the game.

    python3 tools/human_base.py [squire levy paladin medic] [--gen-java] [--write-poses]

Why one construction (lovish, Oct 21: a "living army", every soldier breathes, shifts weight, walks with planted feet,
"extremely realistic and cohesive"): until now each unit model hand-wrote its own walk and idle - four compass walks
(straight legs scissoring under a bobbing trunk, the knee bending only on the swing: design/anatomy_audit.md section
6), each a little different, each foot sliding - and the Halberdier's round 10 (branch claude/halberdier-guard) keyed a
real one for him alone. Here the gait is ONE spec and the units differ only in their parameters (UNITS below):
stride length (a fraction of the leg), the swing's lift, the trunk's attitude (the knight heavy and upright, the
runner light and leaning), the pelvis's sway and bob, and the weapon class, which says what the arms do.

The gait (Perry & Burnfield, Gait Analysis, 2010; Muybridge's plates for the trunk): heel strike at p = 0 (left) and
0.5 (right); the STANCE ankle travels straight back under the hip at the walking speed (so a planted foot never
slides in the body frame - the game plays the cycle by the stride, WALK_PHASE cycles per vanilla limbSwing cycle,
which is exactly the stride the feet were keyed for), the heel lifting in the last quarter of stance; the SWING ankle
lifts over in 40% of the cycle, toe up through, the knee flexed at toe-off and near straight at the strike (both from
the 2-link IK of the ankle path, never a sine on the knee); the pelvis is highest at mid-stance and lowest at double
support, sways over the stance foot, twists with the stride (the lead hip forward) and rolls; the head counters the
trunk's twist (the eyes stay ahead); free arms counter-swing the legs (the sword arm a little less, shoulder and elbow
both: Muybridge), a weapon held in both hands holds them (crossbow, planted greatsword) and the staff hand keeps its
staff upright. Cloth hung from the belt (tabards, robe panels, a cape, a pennant) is keyed a few ticks behind the legs.

The idle: the weapon class's ready stance held - a slow weight shift over one foot and back (the pelvis 0.3-0.5 px
across, the thighs following by IK so the soles stay planted), a glance over the shoulder once a cycle, a small drift
of the free arms; the breath is the shared Life layer in the game (not keyed), and each unit plays at its own phase.
Every idle key 0 is the READY stance: the attack clips the models add on top start and end at zero, so a blow starts
from the guard and returns to it, and the walk crossfades to it by the stride.

Constructed from the figure (tools/contact_check's frames - the game's posing), the legs by IK: a human has no walk
angles, it has foot paths. Change the spec or a unit's parameters here, rerun, then
python3 tools/anim_lab.py design/anims/<unit>.json --gen-java (or --gen-java here).
"""
import argparse
import json
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import contact_check  # noqa: E402
from gen_models import euler, rot_mat  # noqa: E402

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
ANIMS = os.path.join(ROOT, "design", "anims")

# ------------------------------------------------------------------ the shared spec -----------------------------
IDLE_TICKS = 80            # one weight shift and a glance; the game offsets each unit's phase (Life.offset)
WALK_TICKS = 24            # the lab's nominal cycle; the game plays it by the stride (phase)
SWING = 0.4                # the swing's share of the cycle (Perry & Burnfield: 60% stance, 40% swing)
HEEL_UP = 14.0             # the heel's lift in the last quarter of stance (deg)
WALK_KEYS = 20             # keys per cycle (+ the seam): toe-off (p = 0.1, 0.6) and the strikes land on keys
# vanilla: limbSwing += 4 x the blocks moved a tick; the model's cycle is 2 pi / 0.6662 of limbSwing
VANILLA_CYCLE_BLOCKS = 2 * math.pi / 0.6662 / 4

# the per-unit parameters: nothing else differs between the units' clips
#   model/java_class/renderer scale; weapon: sword | crossbow | greatsword | staff (what the arms do);
#   stride_f/stride_b: the ankle ahead of / behind the hip at the strike / toe-off, as fractions of the leg;
#   lift: the swing's clearance (px); lean: the trunk's forward lean on the walk (DSL x, - = forward);
#   sway: the pelvis's lateral travel (px) - its vertical bob is not a parameter: the pelvis is as high as the stance
#   leg holds it, so at double support it drops by what the spread legs cost (walk_bob); twist/roll: the pelvis's
#   yaw / roll amplitude (deg);
#   arm: the free arms' swing (deg) at the shoulder; cloth: {bone: (push, stream)} DSL degrees: a panel pushed by the
#   legs it hangs over - push > 0 a front panel riding the forward thigh (push = its lift per 30 deg of thigh), push < 0
#   a back one on the trailing thigh and the bent knee - plus a cape's stream (-) held through the walk; extra: {bone: (x, y, z)} held through the walk (the planted greatsword carried low);
#   idle_sway/idle_glance: the weight shift (px) and the glance (deg, - = his right)
UNITS = {
    "squire": dict(model="design/models/sunforged_squire_runner_v2.json", java_class="SquireAnim", scale=0.95,
                   weapon="sword", stride_f=0.50, stride_b=0.44, lift=2.4, lean=-8, sway=0.35, twist=5,
                   roll=2.0, arm=24, cloth={}, extra={}, idle_sway=0.35, idle_glance=-12,
                   note="the runner: light and leaning, a long quick stride, both arms pumping (the sword arm less)"),
    "levy": dict(model="design/models/sunforged_crossbow_levy_v2.json", java_class="LevyAnim", scale=1.0,
                 weapon="crossbow", stride_f=0.46, stride_b=0.40, lift=2.0, lean=-3, sway=0.4, twist=4,
                 roll=1.5, arm=0, cloth={"tabard_front": (30, 0), "tabard_back": (-22, 0)}, extra={},
                 idle_sway=0.4, idle_glance=-14,
                 note="the skirmisher: the crossbow held at the chest in both hands, the rig riding the trunk"),
    "paladin": dict(model="design/models/sunforged_paladin_radiant_winged.json", java_class="PaladinAnim", scale=0.92,
                    weapon="greatsword", stride_f=0.40, stride_b=0.34, lift=2.6, lean=-1, sway=0.6, twist=3,
                    roll=1.5, arm=0,
                    cloth={"tabard_front_0": (28, 0), "tabard_back_0": (-22, 0), "sash": (18, 0), "cape_0": (0, -20),
                           "cape_1": (0, -7), "cape_2": (0, -6)},
                    extra={"arms": (28, 0, 0)}, idle_sway=0.5, idle_glance=-3,
                    note="the knight: heavy and upright, a shorter measured stride, the planted sword carried low; the "
                         "glance is a turn of the whole helm by a few degrees only - the halo rides the head and meets "
                         "the folded wing past 6 (contact sweep)"),
    "medic": dict(model="design/models/sunforged_field_medic_v2.json", java_class="MedicAnim", scale=1.0,
                  weapon="staff", stride_f=0.44, stride_b=0.36, lift=2.0, lean=-2, sway=0.35, twist=4,
                  roll=1.5, arm=22, cloth={"robe_front": (36, 0), "robe_back": (-24, 0), "robe_left": (3, 0), "robe_right": (3, 0)}, extra={},
                  idle_sway=0.35, idle_glance=-12,
                  note="the healer: light and short, the staff kept upright as its arm swings, the free arm swinging"),
    # Oct 21 (lovish 21:20: every human unit off the villager body): the four Sunforged men rebuilt on the human body
    # (tools/human_kit.py's figure - the Levy's canon)
    "cleric": dict(model="design/models/sunforged_cleric_v2.json", java_class="ClericAnim", scale=1.0,
                   weapon="carry", stride_f=0.38, stride_b=0.32, lift=2.0, lean=-2, sway=0.35, twist=3,
                   roll=1.2, arm=16,
                   cloth={"robe_front": (9, 0), "robe_back": (-7, 0), "robe_left": (3, 0), "robe_right": (3, 0),
                          "chasuble_front": (8, 0), "chasuble_back": (-6, 0)}, extra={},
                   idle_sway=0.4, idle_glance=-12,
                   note="the priest: an older man's short measured step, the book held to the chest, the free hand "
                        "swinging a little"),
    "architect": dict(model="design/models/sunforged_architect_v2.json", java_class="ArchitectAnim", scale=1.0,
                      weapon="carry", stride_f=0.44, stride_b=0.38, lift=2.0, lean=-2, sway=0.4, twist=4,
                      roll=1.5, arm=20,
                      cloth={"coat_front": (22, 0), "coat_back": (-18, 0), "coat_left": (3, 0), "coat_right": (3, 0)},
                      extra={}, idle_sway=0.4, idle_glance=-14,
                      note="the master builder: a brisk walk, the rolled plan carried like a baton, the free arm "
                           "swinging"),
    "mason": dict(model="design/models/sunforged_mason_v2.json", java_class="MasonAnim", scale=1.0,
                  weapon="sword", stride_f=0.46, stride_b=0.40, lift=2.2, lean=-4, sway=0.5, twist=4,
                  roll=1.8, arm=20, cloth={"apron": (30, 0)}, extra={}, idle_sway=0.45, idle_glance=-12,
                  note="the sapper: a heavy-shouldered stride, the hammer carried at the ready, the free arm swinging"),
    "dawn_paladin": dict(model="design/models/sunforged_dawn_paladin_v2.json", java_class="DawnPaladinAnim",
                         scale=1.1, weapon="sword_shield", stride_f=0.42, stride_b=0.36, lift=2.6, lean=-1,
                         sway=0.55, twist=3, roll=1.5, arm=18,
                         cloth={"surcoat_front": (30, 0), "surcoat_back": (-22, 0)}, extra={},
                         idle_sway=0.5, idle_glance=-12,
                         note="the hero: upright and measured in plate, the sword arm swinging a little, the shield "
                              "arm held"),
}
# what each weapon class's arms do (DSL degrees per key): "swing" = counter-swing with the legs at k x the arm
# amplitude (shoulder) and the elbow folding a little as the arm comes forward; "hold" = the rig holds them
ARMS = {
    "sword": {"left_arm": ("swing", 1.0), "left_fore": ("fold", 0.5), "right_arm": ("swing", 0.6),
              "right_fore": ("fold", 0.2)},
    "crossbow": {"xbow_arms": ("hold", 0.0)},
    "greatsword": {"arms": ("hold", 0.0)},
    "staff": {"left_arm": ("swing", 1.0), "left_fore": ("fold", 0.5), "staff_arm": ("swing", 0.5),
              "staff": ("counter", 0.4)},
    # a thing carried in the left hand (a book to the chest, a rolled plan): that arm nearly still, the right swinging
    "carry": {"right_arm": ("swing", 1.0), "right_fore": ("fold", 0.5), "left_arm": ("swing", 0.25),
              "left_fore": ("fold", 0.1)},
    # a sword in the right hand and a shield on the left arm: the sword arm swings a little, the shield arm less
    "sword_shield": {"right_arm": ("swing", 0.6), "right_fore": ("fold", 0.2), "left_arm": ("swing", 0.3),
                     "left_fore": ("fold", 0.1)},
}
LEGS = (("left_thigh", "left_shin", "left_foot"), ("right_thigh", "right_shin", "right_foot"))


# ------------------------------------------------------------------ the figure --------------------------------------
def bones_of(J):
    return {b["name"]: b for b in J["bones"]}


def frames(J, extra, move=None):
    return contact_check._frames(J, extra, move or {})


def at(fr, bone, p):
    M, t = fr[bone]
    return M @ np.asarray(p, dtype=float) + t


def unit(v):
    v = np.asarray(v, dtype=float)
    return v / np.linalg.norm(v)


def arm_frame(u, front):
    """A limb's rotation: it hangs along its local -y and flexes about local +x toward local -z (gen_models.solve_arm)."""
    y, z = -unit(u), -unit(front)
    return np.stack([np.cross(y, z), y, z], axis=1)


def local_euler(parent, R):
    L = parent.T @ R
    rot = euler(tuple(tuple(float(v) for v in row) for row in L))
    assert np.abs(np.array(rot_mat(*rot)) - L).max() < 2e-3, "local_euler: gimbal lock"
    return rot


def r1(v):
    return [round(float(x), 1) for x in v]


def leg_len(J):
    B = bones_of(J)
    th, sh, ft = LEGS[0]
    return (float(np.linalg.norm(np.subtract(B[sh]["pivot"], B[th]["pivot"])))
            + float(np.linalg.norm(np.subtract(B[ft]["pivot"], B[sh]["pivot"]))))


def foot_pitch_of(J, pose, move, ft):
    """How far the sole's forward edge is off level (sin of its pitch)."""
    return float((frames(J, pose, move)[ft][0] @ [0, 0, -1.0])[1])


def legs_ik(J, pose, move, targets, under_hip=False):
    """Both legs by 2-link IK from the hip (the thighs are root bones on these rigs: the hip is the thigh's pivot plus
    its move) to each ankle target (dy, dz forward, foot pitch: + heel up / - toe up) off the ankle's rest place - or,
    under_hip, off the place straight under the hip at the rest ankle's height: a walk's strides are measured from
    the hip, so a rest stance with one foot ahead (the Squire's, weight on the front foot) does not offset the two
    feet's paths; the idle keeps the model's own stance. The knee ahead of the hip-ankle line, the leg's plane
    sagittal in the world, the sole scanned level (plus the pitch).
    Returns {thigh: (x, y, z), shin: (x, 0, 0), foot: (x, 0, 0)} DSL deltas."""
    B = bones_of(J)
    fr0 = frames(J, {})
    out = {}
    for th, sh, ft in LEGS:
        d = targets[th]
        H = np.array(B[th]["pivot"], dtype=float) + np.array(move.get(th, [0, 0, 0]), dtype=float)
        A0 = at(fr0, sh, B[ft]["pivot"])
        if under_hip:
            A0 = np.array([A0[0], A0[1], B[th]["pivot"][2]], dtype=float)
        A = A0 + np.array([0.0, d[0], -d[1]])
        L1 = float(np.linalg.norm(np.subtract(B[sh]["pivot"], B[th]["pivot"])))
        L2 = float(np.linalg.norm(np.subtract(B[ft]["pivot"], B[sh]["pivot"])))
        v = A - H
        D = min(float(np.linalg.norm(v)), L1 + L2 - 1e-3)
        ud = unit(v)
        f = np.array([0, 0, -1.0])
        fwd = unit(f - ud * (ud @ f))
        ca = (L1 * L1 + D * D - L2 * L2) / (2 * L1 * D)
        a1 = math.acos(max(-1.0, min(1.0, ca)))
        cb = (L1 * L1 + L2 * L2 - D * D) / (2 * L1 * L2)
        knee = 180.0 - math.degrees(math.acos(max(-1.0, min(1.0, cb))))
        u = ud * math.cos(a1) + fwd * math.sin(a1)
        front = unit(np.array([0, 0, -1.0]) - u * (u @ [0, 0, -1.0]))
        rot = local_euler(np.eye(3), arm_frame(u, front))
        rest_t = B[th].get("rotation", [0, 0, 0])
        rs = B[sh].get("rotation", [0, 0, 0])[0]
        out[th] = r1([rot[k] - rest_t[k] for k in range(3)])
        out[sh] = [round(-knee - rs, 1), 0.0, 0.0]
        ex = dict(pose, **{th: out[th], sh: out[sh]})
        want = foot_pitch_of(J, {}, {}, ft)
        best = None
        for step, lo, hi in ((2.0, -80.0, 80.0), (0.1, None, None)):
            if lo is None:
                lo, hi = best[1] - 2.0, best[1] + 2.0
            x = lo
            while x <= hi + 1e-9:
                ex[ft] = [x, 0.0, 0.0]
                err = abs(foot_pitch_of(J, ex, move, ft) - want)
                if best is None or err < best[0]:
                    best = (err, x)
                x += step
        out[ft] = [round(best[1] + d[2], 1), 0.0, 0.0]
    return out


# ------------------------------------------------------------------ the gait ----------------------------------------
def gait_foot(p, strike, F, Bk, lift):
    """One ankle's (dy, dz forward, pitch) at cycle progress p for a foot whose heel strikes at `strike`."""
    q = (p - strike) % 1.0
    stance = 1.0 - SWING
    if q < stance:
        s = q / stance
        pitch = 0.0 if s < 0.75 else HEEL_UP * (s - 0.75) / 0.25
        return (0.0, F + (Bk - F) * s, pitch)
    s = (q - stance) / SWING
    z = Bk + (F - Bk) * (s * s * (3 - 2 * s))
    y = lift * math.sin(math.pi * s)
    pitch = HEEL_UP * (1 - s) ** 2 - 8.0 * math.sin(math.pi * s) - 5.0 * s * s
    return (y, z, pitch)


def walk_bob(J, u, L):
    """The pelvis's drop at double support (px): the hip is as high as a stance leg nearly straight under it holds
    it (mid-stance, the rest height) and at the strike the spread legs - the front one reaching stride_f ahead -
    hold it lower by the geometry, less the little the pelvis's twist buys (the lead hip travels forward with it, so
    the leg reaches the strike from nearer) and the heel's rocker. Not a parameter: a longer stride dips more, as it
    does (Perry & Burnfield: the pelvis's vertical excursion is ~4-5% of leg length in a brisk walk)."""
    B = bones_of(J)
    th, sh, ft = LEGS[0]
    fr0 = frames(J, {})
    h = float(B[th]["pivot"][1] - at(fr0, sh, B[ft]["pivot"])[1])          # the hip over the ankle at rest
    reach = max(u["stride_f"], u["stride_b"]) * L
    return round(0.8 * (h - math.sqrt(max(0.0, L * L - reach * reach))), 2)


def walk_phase(u, L):
    """Cycles per vanilla limbSwing cycle for the stride the feet were keyed for (the renderer's scale -> blocks)."""
    stride_px = (u["stride_f"] + u["stride_b"]) * L / (1 - SWING)
    return round(VANILLA_CYCLE_BLOCKS / (stride_px * u["scale"] / 16), 2)


def walk_key(J, u, p, L, bob_px):
    """The walk's key at p: the pelvis, the head, the arms by class, the cloth, the legs by IK."""
    c2, s2 = math.cos(2 * math.pi * p), math.sin(2 * math.pi * p)   # +1 at the left strike, -1 at the right
    c4 = math.cos(4 * math.pi * p)                                   # +1 at both strikes (double support)
    key = {"p": round(p, 4)}
    # the pelvis: the lean, a dip at double support (c4), the lead hip forward (twist with c2... the left hip leads at
    # the left strike), a roll onto the stance side; its travel: the bob (lowest at double support) and the sway over
    # the stance foot (over the left foot in left stance: p in 0..0.6, so a sine a little behind the strike)
    body = (u["lean"] - 0.8 * c4, u["twist"] * c2, u["roll"] * s2)
    bob = -bob_px * (0.5 + 0.5 * c4)
    sway = u["sway"] * math.sin(2 * math.pi * (p - 0.1))
    move = {"body": [round(sway, 2), round(bob, 2), 0.0]}
    key["body"] = r1(body)
    key["body@move"] = move["body"]
    for th, _, _ in LEGS:
        key[th + "@move"] = list(move["body"])
        move[th] = move["body"]
    # the head counters the twist and the lean a little (the eyes level and ahead), a nod with the step
    key["head"] = r1((-0.4 * body[0] - 0.5 * c4, -0.75 * body[1], -0.4 * body[2]))
    # the arms by weapon class: counter-swing (the left arm forward at the RIGHT strike: c2 < 0 -> left_arm x > 0)
    for bone, (kind, k) in ARMS[u["weapon"]].items():
        side = 1.0 if bone.startswith("left") else -1.0
        if kind == "swing":
            key[bone] = r1((-side * u["arm"] * k * c2, 0.0, 0.0))
        elif kind == "fold":
            key[bone] = r1((u["arm"] * k * max(0.0, -side * c2) * 0.6 + u["arm"] * k * 0.25, 0.0, 0.0))
        elif kind == "counter":   # the staff hand keeps the staff upright against its arm's swing
            key[bone] = r1((side * u["arm"] * k * 0.5 * 0.8 * c2, 0.0, 0.0))
        else:
            key[bone] = [0.0, 0.0, 0.0]
    for bone, v in u["extra"].items():
        key[bone] = r1(v)
    # the legs: the ankle paths, scaled to this body's leg
    F, Bk = u["stride_f"] * L, -u["stride_b"] * L
    targets = {LEGS[0][0]: gait_foot(p, 0.0, F, Bk, u["lift"]), LEGS[1][0]: gait_foot(p, 0.5, F, Bk, u["lift"])}
    pose = {k: v for k, v in key.items() if k != "p" and "@" not in k}
    legs = legs_ik(J, pose, move, targets, under_hip=True)
    key.update(legs)
    # the cloth hung from the belt follows the legs it hangs over, a beat late: a front panel rides out on the
    # forward thigh (its push a share of the thigh's flexion past the rest, plus a little for the knee it clears), a
    # back panel is pushed by the trailing thigh and the bent knee behind; a cape streams. Keyed from the legs'
    # own angles, not a sine of the stride, so a longer stride lifts the cloth further (until Oct 21 the front
    # panels were sines tuned to one stride and the thighs went 1.8-2.2 px through them at the new strides).
    B = bones_of(J)
    fwd = max(legs[th][0] + B[th].get("rotation", [0, 0, 0])[0] for th, _, _ in LEGS)
    back = max(-(legs[th][0] + B[th].get("rotation", [0, 0, 0])[0]) for th, _, _ in LEGS)
    knee = max(-(legs[sh][0] + B[sh].get("rotation", [0, 0, 0])[0]) for _, sh, _ in LEGS)
    for bone, (push, stream) in u["cloth"].items():
        if bone.endswith("_left") or bone.endswith("_right"):   # a side panel: flares out over its own lifted thigh
            th = ("left" if bone.endswith("_left") else "right") + "_thigh"
            lift = max(0.0, legs[th][0] + B[th].get("rotation", [0, 0, 0])[0])
            v = push * lift / 30.0
            key[bone] = r1((0.0, 0.0, v if bone.endswith("_left") else -v))
            continue
        if push > 0:
            v = push * max(0.0, fwd) / 30.0 + 0.15 * knee * (fwd > 10) + stream
        elif push < 0:
            v = push * max(0.0, back) / 30.0 - 0.25 * knee + stream
        else:
            v = stream
        key[bone] = r1((v, 0.0, 0.0))
    return key


def idle_key(J, u, p, spec):
    """The idle's key: the ready stance plus the weight shift `w` (-1..1, + over his left foot), the glance `g` (0..1),
    the free arms' drift `a` (-1..1) and a nod `n` (-1..1, + = chin down)."""
    w, g, a, n = spec
    key = {"p": round(p, 4)}
    body = (0.0 - 0.6 * abs(w), 2.5 * w, 1.2 * w)
    move = {"body": [round(u["idle_sway"] * w, 2), round(-0.15 * abs(w), 2), 0.0]}
    key["body"] = r1(body)
    key["body@move"] = move["body"]
    for th, _, _ in LEGS:
        key[th + "@move"] = list(move["body"])
        move[th] = move["body"]
    key["head"] = r1((-0.4 * body[0] - 1.5 * g - 3.0 * n, -0.75 * body[1] + u["idle_glance"] * g, -0.5 * body[2]))
    for bone, (kind, k) in ARMS[u["weapon"]].items():
        if kind == "swing":
            key[bone] = r1((2.0 * k * a, 0.0, 1.0 * (1 if bone.startswith("left") else -1) * abs(w) * k))
        elif kind == "fold":
            key[bone] = r1((1.5 * k * a, 0.0, 0.0))
        elif kind == "counter":
            key[bone] = r1((-1.6 * k * a, 0.0, 0.0))
        else:   # a rig held in both hands: it settles a little as the weight shifts, and stirs with the arms' drift
            key[bone] = r1((0.8 * a, 0.0, 0.0))
    for bone in u["cloth"]:
        key[bone] = [0.0, 0.0, 0.0] if bone.endswith(("_left", "_right")) else r1((0.8 * w, 0.0, 0.0))
    for bone, v in u["extra"].items():
        key[bone] = [0.0, 0.0, 0.0]
    pose = {k: v for k, v in key.items() if k != "p" and "@" not in k}
    targets = {LEGS[0][0]: (0.0, 0.0, 0.0), LEGS[1][0]: (0.0, 0.0, 0.0)}
    key.update(legs_ik(J, pose, move, targets))
    return key


# the idle's cycle, 8 keys, key 0 = the READY stance exactly (everything zero there): the weight shift over the left
# foot and back (a slow sine), the free arms drifting an eighth of a cycle behind it, the nod three eighths behind,
# and one glance to the right in the second half as the weight comes back. Every turning point lands ON a key (a
# sampled sine whose extremum falls between two equal keys gives both a zero tangent: a hold) and no key is a turn
# for every moving bone at once (that is a dead stop - anim_lab's "stop" warning; a body never does that).
# (p, weight shift, glance 0..1, arm drift, nod); the loop wraps (the last key = the first)
IDLE_KEYS = 8


def idle_spec(p):
    tau = 2 * math.pi
    w = math.sin(tau * p)
    a = 0.8 * math.sin(tau * (p - 0.125))
    n = 0.6 * math.sin(tau * (p - 0.375))
    g = max(0.0, math.sin(tau * (p - 0.375)))                # the glance peaks at p = 0.625: the legs turn at 0.25 and 0.75, the head looks between
    return (w, g, a, n)


IDLE_SPEC = tuple((i / IDLE_KEYS,) + idle_spec(i / IDLE_KEYS) for i in range(IDLE_KEYS))


def measure(J, u, clip, L):
    """What the lab's motion check cannot see from angles alone: the stance ankle's slide along its straight path
    (px, in the body frame against the gait's own path), the soles' pitch off the ground on stance, the swing's
    clearance. Printed; the slide is the planted-feet number."""
    import anim_lab
    B = bones_of(J)
    fr0 = frames(J, {})
    worst = {"stance ankle off its path (px)": 0.0, "stance sole off level (deg)": 0.0, "lowest swing sole (px)": 9.0}
    F, Bk = u["stride_f"] * L, -u["stride_b"] * L
    n = 48
    for i in range(n + 1):
        p = i / n
        s = anim_lab.sample(clip, p)
        pose = {b: v for b, v in s.items() if "@" not in b}
        move = {b[:-5]: v for b, v in s.items() if "@" in b}
        fr = frames(J, pose, move)
        for (th, sh, ft), strike in zip(LEGS, (0.0, 0.5)):
            want = gait_foot(p, strike, F, Bk, u["lift"]) if "phase" in clip else (0.0, 0.0, 0.0)
            rest = at(fr0, sh, B[ft]["pivot"])
            if "phase" in clip:
                rest = np.array([rest[0], rest[1], B[th]["pivot"][2]])
            ank = at(fr, sh, B[ft]["pivot"]) - rest
            q = (p - strike) % 1.0
            on_ground = q < 1.0 - SWING or "phase" not in clip
            if on_ground:
                worst["stance ankle off its path (px)"] = max(worst["stance ankle off its path (px)"],
                                                              abs(ank[2] + want[1]), abs(ank[1] - want[0]))
                if q < 0.75 * (1.0 - SWING):
                    lvl = abs(math.degrees(math.asin(foot_pitch_of(J, pose, move, ft))))
                    worst["stance sole off level (deg)"] = max(worst["stance sole off level (deg)"], lvl)
            else:
                M, t = fr[ft]
                lo = min((M @ (np.array(c["from"]) + np.array(c["size"]) * [dx, 0, dz]) + t)[1]
                         for c in B[ft]["cubes"] for dx in (0, 1) for dz in (0, 1))
                worst["lowest swing sole (px)"] = min(worst["lowest swing sole (px)"], lo)
    return worst


def build(name, u, gen_java=False, write_poses=False):
    J = json.load(open(os.path.join(ROOT, u["model"])))
    L = leg_len(J)
    phase = walk_phase(u, L)
    print("== %s (%s): leg %.1f px, stride %+.1f/%.1f px, walk phase x%.2f - %s" % (
        name, u["weapon"], L, u["stride_f"] * L, -u["stride_b"] * L, phase, u["note"]))
    idle = [idle_key(J, u, spec[0], spec[1:]) for spec in IDLE_SPEC]
    idle.append(dict(idle[0], p=1.0))
    bob_px = walk_bob(J, u, L)
    print("  the pelvis dips %.2f px at double support" % bob_px)
    walk = [walk_key(J, u, i / WALK_KEYS, L, bob_px) for i in range(WALK_KEYS)]
    walk.append(dict(walk[0], p=1.0))
    ready = {k: v for k, v in idle[0].items() if k != "p"}
    bones = [b for b in ready if "@" not in b]
    stems = {}
    for b in bones:
        stem = b.upper().replace("LEFT_", "L").replace("RIGHT_", "R").replace("_", "")
        stems[b] = stem
    data = {
        "unit": name, "model": u["model"], "java_class": u["java_class"], "java_bones": stems,
        "_note": "GENERATED by tools/human_base.py (the shared human base clips: one gait and idle spec, this unit's "
                 "parameters) - change the spec or the parameters there and rerun. idle key 0 = the %s class's READY "
                 "stance; the walk is a loop the game plays by the stride (phase); the attack clips the model adds "
                 "start and end at zero on top of READY. %s" % (u["weapon"], u["note"]),
        "params": {k: v for k, v in u.items() if k not in ("note",)},
        "poses": {"ready": ready},
        "clips": {"idle": {"ticks": IDLE_TICKS, "loop": True, "from": "ready", "keys": idle},
                  "walk": {"ticks": WALK_TICKS, "loop": True, "phase": phase, "keys": walk}},
    }
    for cname in ("idle", "walk"):
        w = measure(J, u, data["clips"][cname], L)
        print("  %-5s " % cname + ", ".join("%s %.2f" % kv for kv in w.items()))
    path = os.path.join(ANIMS, name + ".json")
    with open(path, "w") as f:
        json.dump(data, f, indent=1)
    print("  wrote", os.path.relpath(path, ROOT), "(idle %d keys, walk %d keys)" % (len(idle), len(walk)))
    if write_poses:
        import grip_keys

        def as_pose(key):
            out = {k: v for k, v in key.items() if k != "p" and "@" not in k}
            out["_move"] = {k[:-5]: v for k, v in key.items() if k.endswith("@move")}
            return out
        grip_keys.write_poses(os.path.splitext(os.path.basename(u["model"]))[0],
                              {"base_ready": as_pose(idle[0]), "base_shift": as_pose(idle[2]),
                               "base_walk_strike": as_pose(walk[0]), "base_walk_pass": as_pose(walk[3])})
    if gen_java:
        os.system("%s %s %s --gen-java" % (sys.executable, os.path.join(ROOT, "tools", "anim_lab.py"), path))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("units", nargs="*", default=list(UNITS))
    ap.add_argument("--gen-java", action="store_true")
    ap.add_argument("--write-poses", action="store_true", help="the keys into design/poses/<model>.json (contact sweep)")
    a = ap.parse_args()
    for name in a.units:
        build(name, UNITS[name], a.gen_java, a.write_poses)


if __name__ == "__main__":
    main()
