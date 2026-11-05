#!/usr/bin/env python3
"""The Tithe Binder's clips as code: the spec the Java rig (TitheBinderUnitModel + its renderer) ports 1:1.
Design sheet .scratch/unit1/design-sheet.md section 5 (clips), 4.3 (springs), 3.2-3.3 (the wheel), with the r2/r3
review changes (one flat ring, the mass moves, a 10 px hover).

    state = initial_state(n, seed)                  # a plain dict: phases, springs, the wheel, every slot's machine
    state, pose = step(state, inputs)               # one tick (20 tps); pure: the input state is not modified
    python tools/tithe_binder_anim.py --fixture F   # writes the sampled-pose fixture (every 5th tick of every clip)

inputs: {"clip": "idle"|"fidget"|"move"|"volley"|"bind"|"hit"|"death", "t": tick inside the clip (0 = its first),
         "speed": 0..1 (move: v / 0.25), "target": [x, y, z] DSL px (volley: the victim's chest; bind: the corpse),
         "hit_dir": [x, z] (hit: where the blow comes from, unit vector)}
pose:   {"bones": {name: {"rot": [x, y, z] DSL degrees ADDED to the rest pose, "off": [x, y, z] DSL px}},
         "slots": {k: {"angle", "radial", "y", "pitch", "roll"}}  - a DOCKED weapon in the ring's frame: azimuth (deg,
                   the slot's yaw: 0 points -z, + turns toward -x), pommel radius px, lift px, its own pitch / roll,
         "flying": {k: {"state", "pos": pommel [x, y, z] model px, "rot": world DSL degrees, "scale": part scale}},
         "hidden": [bones], "alpha": body alpha (death dissolve), "lantern": glow 0..2.5,
         "ribbons": [[hub, end, sag px, width px]]  - the soul chains (renderer quads)}

Conventions (design/models/README.md): DSL degrees, +x swings a hanging part forward / tips a standing part back,
+y turns the front toward -x (the unit's right), rotation order x then y then z. Rest pose = the model json
(tools/gen_tithe_binder.py); every rotation here is a DELTA on it. Springs (sheet 4.3): theta'' = -k (theta - target)
- c theta' + drive, integrated once per tick (omega += a; theta += omega), drive = the root's acceleration in px/tick^2
x 6 deg. Easing: smoothstep s(x) = x x (3 - 2 x). Randomness: one seeded LCG in the state (the idle fidget timer).
"""
import argparse
import copy
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_tithe_binder as G  # noqa: E402
from gen_models import rot_mat, mat_mul, transpose, euler  # noqa: E402

# ------------------------------------------------------------------ constants (the Java port copies these) ---------
K_CHAIN, C_CHAIN = 0.22, 0.18          # sheet 4.3: chains, tatters, cape, lantern
K_CHAIN_B = 0.30                       # a chain's second segment
K_RADIAL, C_RADIAL = 0.25, 0.20        # a docked weapon's radial spring (px)
K_ROLL, C_ROLL = 0.15, 0.12            # its roll / pitch-kick spring (deg)
K_LEAN, C_LEAN = 0.20, 0.25            # the body's lean in the move (late start, overshoot on stop)
K_SPACE, C_SPACE = 0.18, 0.25          # slot angles re-spacing after a bind
K_TILT, C_TILT = 0.15, 0.12            # the ring's tilt (kicked by hits)
DRIVE = 6.0                            # deg per px/tick^2
CLAMP = {"hem_front": (0.0, 40.0), "hem_back": (-40.0, 0.0), "hem_b": (-40.0, 40.0), "tatter": (-60.0, 10.0),
         "lantern": (-6.0, 25.0), "cape": (-50.0, 5.0), "shackle": (-80.0, 80.0)}
GRAVITY = 1.28                         # px/tick^2 (0.08 blocks/tick^2) - falling weapons at death
DEATH_DROP = 10.5                      # px: the crumple lands the robe on the ground (tools/tithe_binder_poses.py)
HUB = (0.0, 27.0 + G.LIFT, 0.0)        # where the soul chains leave the ribs
RING_PIVOT, RING_R, RING_TILT = G.RING_PIVOT, G.RING["r"], G.RING_TILT
LENGTH = {k: G.length_of(G.prims_of(k)) for k in range(1, 11)}        # flight length (scale 1.0), px
TUMBLE = {1: ("tumble", 40), 2: ("tumble", 60), 3: ("tumble", 50), 4: ("roll", 20), 5: ("roll", 15),
          6: ("tumble", 30), 7: ("tumble", 25), 8: ("flat", 30), 9: ("roll", 10), 10: ("tumble", 20)}
VOLLEY_RELEASE, RELEASE_GAP = 14, 2
SLING, EMBED, REEL, DOCK_OVERSHOOT = 2, 3, 8, 2.0
CLIP_TICKS = {"idle": 60, "fidget": 30, "move": 50, "volley": 60, "bind": 32, "hit": 16, "death": 50}
TATTERS = ("tatter_f", "tatter_b", "tatter_l", "tatter_r")


def smooth(x):
    x = max(0.0, min(1.0, x))
    return x * x * (3.0 - 2.0 * x)


def keys(t, ks):
    """Piecewise smoothstep through [(tick, value)] (values may be lists); before the first / after the last: held."""
    if t <= ks[0][0]:
        return ks[0][1]
    for (t0, v0), (t1, v1) in zip(ks, ks[1:]):
        if t <= t1:
            u = smooth((t - t0) / float(t1 - t0)) if t1 > t0 else 1.0
            if isinstance(v0, (list, tuple)):
                return [a + (b - a) * u for a, b in zip(v0, v1)]
            return v0 + (v1 - v0) * u
    return ks[-1][1]


def spring(s, target, k, c, drive=0.0, lo=None, hi=None):
    """One tick of an angular (or linear) spring; s = [theta, omega]. Returns the new [theta, omega]."""
    th, om = s
    om = om + (-k * (th - target) - c * om + drive)
    th = th + om
    if lo is not None and th < lo:
        th, om = lo, 0.0
    if hi is not None and th > hi:
        th, om = hi, 0.0
    return [th, om]


def lcg(seed):
    """The seeded random source: (next_seed, value 0..1)."""
    seed = (seed * 1103515245 + 12345) & 0x7FFFFFFF
    return seed, seed / float(0x7FFFFFFF)


# ------------------------------------------------------------------ quaternions (the flight's orientation) --------
def q_from_axis(ax, deg):
    n = math.sqrt(sum(a * a for a in ax)) or 1.0
    h = math.radians(deg) / 2
    return (math.cos(h),) + tuple(a / n * math.sin(h) for a in ax)


def q_mul(a, b):
    w1, x1, y1, z1 = a
    w2, x2, y2, z2 = b
    return (w1 * w2 - x1 * x2 - y1 * y2 - z1 * z2, w1 * x2 + x1 * w2 + y1 * z2 - z1 * y2,
            w1 * y2 - x1 * z2 + y1 * w2 + z1 * x2, w1 * z2 + x1 * y2 - y1 * x2 + z1 * w2)


def q_from_mat(R):
    tr = R[0][0] + R[1][1] + R[2][2]
    if tr > 0:
        s = math.sqrt(tr + 1.0) * 2
        return (0.25 * s, (R[2][1] - R[1][2]) / s, (R[0][2] - R[2][0]) / s, (R[1][0] - R[0][1]) / s)
    i = max(range(3), key=lambda a: R[a][a])
    j, k = (i + 1) % 3, (i + 2) % 3
    s = math.sqrt(1.0 + R[i][i] - R[j][j] - R[k][k]) * 2
    q = [0.0, 0.0, 0.0, 0.0]
    q[0] = (R[k][j] - R[j][k]) / s
    q[1 + i] = 0.25 * s
    q[1 + j] = (R[j][i] + R[i][j]) / s
    q[1 + k] = (R[k][i] + R[i][k]) / s
    return tuple(q)


def q_to_mat(q):
    w, x, y, z = q
    return ((1 - 2 * (y * y + z * z), 2 * (x * y - w * z), 2 * (x * z + w * y)),
            (2 * (x * y + w * z), 1 - 2 * (x * x + z * z), 2 * (y * z - w * x)),
            (2 * (x * z - w * y), 2 * (y * z + w * x), 1 - 2 * (x * x + y * y)))


def q_slerp(a, b, u):
    d = sum(x * y for x, y in zip(a, b))
    if d < 0:
        b, d = tuple(-v for v in b), -d
    if d > 0.9995:
        q = tuple(x + (y - x) * u for x, y in zip(a, b))
    else:
        th = math.acos(d)
        s0, s1 = math.sin((1 - u) * th) / math.sin(th), math.sin(u * th) / math.sin(th)
        q = tuple(x * s0 + y * s1 for x, y in zip(a, b))
    n = math.sqrt(sum(v * v for v in q))
    return tuple(v / n for v in q)


def frame_along(T, up=(0.0, 1.0, 0.0)):
    """The world rotation whose local -z points along T (a flat blade: local y as near world up as it can be)."""
    n = math.sqrt(sum(v * v for v in T)) or 1.0
    ez = tuple(-v / n for v in T)
    ex = (up[1] * ez[2] - up[2] * ez[1], up[2] * ez[0] - up[0] * ez[2], up[0] * ez[1] - up[1] * ez[0])
    nx = math.sqrt(sum(v * v for v in ex))
    if nx < 1e-6:
        ex = (1.0, 0.0, 0.0)
    else:
        ex = tuple(v / nx for v in ex)
    ey = (ez[1] * ex[2] - ez[2] * ex[1], ez[2] * ex[0] - ez[0] * ex[2], ez[0] * ex[1] - ez[1] * ex[0])
    return tuple(tuple((ex, ey, ez)[j][i] for j in range(3)) for i in range(3))


def bezier(p0, p1, p2, p3, u):
    a, b, c, d = (1 - u) ** 3, 3 * (1 - u) ** 2 * u, 3 * (1 - u) * u * u, u ** 3
    return [a * p0[i] + b * p1[i] + c * p2[i] + d * p3[i] for i in range(3)]


def bezier_d(p0, p1, p2, p3, u):
    a, b, c = 3 * (1 - u) ** 2, 6 * (1 - u) * u, 3 * u * u
    return [a * (p1[i] - p0[i]) + b * (p2[i] - p1[i]) + c * (p3[i] - p2[i]) for i in range(3)]


# ------------------------------------------------------------------ state --------------------------------------------
def layout_angles(n):
    return [G.BASE_YAW + 360.0 / n * i for i in range(n)]


def initial_state(n=10, seed=1):
    seed, r = lcg(seed)
    springs = {b: [0.0, 0.0] for b in ("hem_chain_1", "hem_chain_2", "hem_chain_3", "hem_chain_1_b", "hem_chain_2_b",
                                        "hem_chain_3_b", "lantern_chain", "cape", "body_lean", "ring_tilt_x",
                                        "ring_tilt_z", "right_shackle_x", "right_shackle_z", "left_shackle_x",
                                        "left_shackle_z") + TATTERS}
    slots = {k: {"angle": a, "ang_v": 0.0, "radial": [0.0, 0.0], "roll": [0.0, 0.0], "kick": [0.0, 0.0],
                 "mode": "DOCKED", "since": 0, "data": {}} for k, a in zip(range(1, n + 1), layout_angles(n))}
    return {"n": n, "seed": seed, "tick": 0, "bob": 0.0, "breath": 0.0, "look": 0.0, "fidget_in": 200 + int(200 * r),
            "fidget_t": -1, "ring_yaw": 0.0, "ring_yaw_v": 0.6, "springs": springs, "slots": slots,
            "root": [[0.0, 0.0, 0.0], [0.0, 0.0, 0.0], [0.0, 0.0, 0.0]], "root_z": 0.0, "speed": 0.0,
            "lean_x": 0.0, "lean_v": 0.0, "lantern": 1.0, "order": {}, "falling": {}}


# ------------------------------------------------------------------ geometry -----------------------------------------
def ring_matrix(tilt_x, yaw, tilt_z):
    return rot_mat(RING_TILT + tilt_x, yaw, tilt_z)


def slot_local(angle, radial=0.0, y=0.0):
    a = math.radians(angle)
    r = RING_R + radial
    return [-math.sin(a) * r, y, -math.cos(a) * r]


def slot_world(st, angle, radial=0.0):
    """A slot's pommel in model space from the CURRENT ring transform (state['ring_now'])."""
    R, o = st["ring_now"]
    p = slot_local(angle, radial)
    return [o[i] + sum(R[i][j] * p[j] for j in range(3)) for i in range(3)]


def slot_rot_world(st, angle, pitch=0.0):
    R, _o = st["ring_now"]
    return mat_mul(R, mat_mul(rot_mat(0, angle, 0), rot_mat(pitch, 0, 0)))


# ------------------------------------------------------------------ the body per clip -------------------------------
def body_targets(st, clip, t, inp):
    """Direct (eased) bone deltas for this tick + targets for the springs + ring/lantern channels."""
    n = st["n"]
    B = {}

    def put(bone, rot=None, off=None):
        e = B.setdefault(bone, {"rot": [0.0, 0.0, 0.0], "off": [0.0, 0.0, 0.0]})
        if rot:
            e["rot"] = [e["rot"][i] + rot[i] for i in range(3)]
        if off:
            e["off"] = [e["off"][i] + off[i] for i in range(3)]

    ch = {"tatter": -0.0, "cape": 0.0, "ring_tilt": [0.0, 0.0], "ring_off": [0.0, 0.0, 0.0], "ring_spin": 0.6,
          "cock": 0.0, "lantern": 1.0, "alpha": 1.0, "hidden": [], "lean_target": 0.0, "speed": 0.0}
    # ---- the idle layer (always on, faded by the clips that own the body)
    idle_w = {"idle": 1.0, "fidget": 1.0, "move": 1.0, "volley": 1.0, "bind": 1.0, "hit": 1.0, "death": 0.0}[clip]
    if clip == "volley":
        idle_w = 1.0 - keys(t, [(0, 0.0), (4, 1.0), (st["f_end"], 1.0), (st["f_end"] + 6, 0.0)])
    if clip == "death":
        idle_w = 1.0 - keys(t, [(0, 0.0), (3, 1.0)])
    bob, br, look = st["bob"], st["breath"], st["look"]
    amp = 2.0 - 0.5 * st["speed"]
    put("hover", off=[0, amp * math.sin(bob) * idle_w, 0])
    put("body", rot=[2 * math.sin(br + 0.5) * idle_w, 0, 0])
    put("chest", rot=[3 * math.sin(br) * idle_w, 0, 0])
    put("mantle", off=[0, 0.5 * max(0.0, math.sin(br)) * idle_w, 0])
    put("head", rot=[0, 10 * math.sin(look) * idle_w, 0])
    put("right_arm", rot=[5 * math.sin(bob + math.pi / 2) * idle_w, 0, 3 * math.sin(bob) * idle_w])
    put("left_arm", rot=[5 * math.sin(bob + 3 * math.pi / 2) * idle_w, 0, 3 * math.sin(bob) * idle_w])
    curl = (10 + 10 * math.sin(2 * math.pi * st["tick"] / 40.0)) * idle_w
    put("right_hand", rot=[curl, 0, 0])
    put("left_hand", rot=[curl, 0, 0])
    tat = [4 * math.sin(2 * math.pi * st["tick"] / 30.0 + i * math.pi / 2) * idle_w for i in range(4)]
    ch["tatter_add"] = tat

    if clip == "fidget":       # "counting": the left hand lifts, the wheel spins one fast turn, the weapons rattle
        put("left_arm", rot=[keys(t, [(0, 0), (6, 45), (9, 45), (15, 0)]), 0, 0])
        ch["ring_spin"] = keys(t, [(0, 0.6), (8, 12.0), (20, 0.6)])
        ch["rattle"] = 15.0 if t == 8 else 0.0

    if clip == "move":
        v = inp.get("speed", 0.0)
        ch["speed"] = v
        ch["lean_target"] = -10.0 * v
        put("hover", off=[0, -2.0 * v, 0])
        put("head", rot=[10 * v, 0, 0])
        for side in ("right", "left"):
            put(side + "_arm", rot=[-40 * v, 0, 0])
            put(side + "_forearm", rot=[-25 * v, 0, 0])
            put(side + "_hand", rot=[-15 * v, 0, 0])
        ch["tatter"] = -35.0 * v + 6 * math.sin(2 * math.pi * t / 12.0) * v
        ch["cape"] = -30.0 * v
        ch["ring_tilt"] = [-8.0 * v, 0.0]
        ch["ring_spin"] = 0.6 + 0.6 * v
        ch["trail"] = 10.0 * v

    if clip == "volley":
        fe = st["f_end"]
        last = st["last_release"]
        put("hover", off=[0, keys(t, [(0, 0), (11, 3), (14, 3), (15, -1), (fe, 0)]),
                          keys(t, [(0, 0), (11, 3), (14, 3), (15, -4), (fe, 0)])])
        put("body", rot=[keys(t, [(0, 0), (11, 14), (14, 14), (15, -11), (fe, 0)]),
                         keys(t, [(0, 0), (11, 30), (14, 30), (15, -20), (fe, 0)]), 0])
        put("head", rot=[keys(t, [(0, 0), (11, -20), (14, -20), (15, 0), (fe + 2, 0)]),
                         keys(t, [(0, 0), (11, -10), (14, -10), (15, 15), (fe + 2, 0)]), 0])
        put("right_arm", rot=[keys(t, [(0, 0), (11, 180), (14, 180), (15, 40), (max(last + 2, 16), 40), (fe, 0)]), 0,
                              keys(t, [(0, 0), (11, 42), (14, 42), (15, 0)])])
        fx = keys(t, [(0, 0), (11, 25), (14, 25), (15, -25), (fe, 0)])
        fz = 0.0
        if n >= 3 and 15 < t <= last + 1:       # one crack of the whip per release
            ph = 2 * math.pi * (t - 14) / 4.0
            fx, fz = 25 + 20 * math.sin(ph) - 25, 15 * math.sin(ph)
        put("right_forearm", rot=[fx, 0, fz])
        put("right_hand", rot=[keys(t, [(0, 0), (11, 20), (14, 20), (15, 0)]), 0, 0])
        put("left_arm", rot=[keys(t, [(0, 0), (11, 64), (max(last + 2, 16), 64), (fe, 0)]),
                             keys(t, [(0, 0), (11, 10), (max(last + 2, 16), 10), (fe, 0)]), 0])
        put("left_forearm", rot=[keys(t, [(0, 0), (11, -30), (max(last + 2, 16), -30), (fe, 0)]), 0, 0])
        put("mantle", off=[0, keys(t, [(0, 0), (11, 1), (14, 1), (15, 0)]), 0])
        ch["tatter"] = keys(t, [(0, 0), (11, -8), (14, -8), (15, -15), (fe, 0)])
        ch["cape"] = keys(t, [(0, 0), (11, -6), (14, -6), (15, -12), (fe, 0)])
        ch["ring_tilt"] = [keys(t, [(0, 0), (11, 12), (14, 12), (15, -18), (fe, 0)]), 0.0]
        ch["ring_off"] = keys(t, [(0, [0, 0, 0]), (11, [0, 1, 4]), (14, [0, 1, 4]), (15, [0, -2, -5]), (fe, [0, 0, 0])])
        ch["ring_spin"] = keys(t, [(0, 0.6), (11, 6.0), (last, 6.0), (last + 20, 0.6)])
        ch["cock"] = keys(t, [(0, 0), (11, 25), (14, 25), (15, 10), (fe, 0)])
        released = len([k for k, o in st["order"].items() if VOLLEY_RELEASE + RELEASE_GAP * o <= t])
        ch["lantern"] = keys(t, [(0, 1.0), (11, 1.6), (last, 1.6), (fe, 1.0)])
        if t <= last:
            ch["lantern"] = max(1.0, ch["lantern"] - 0.1 * released)      # -0.1 per release

    if clip == "bind":         # the corpse at the front-right (inputs["target"]); n -> n + 1
        put("head", rot=[keys(t, [(0, 0), (6, 0), (15, 16), (18, 16), (31, 0)]),
                         keys(t, [(0, 0), (3, 25), (15, 12), (18, 12), (31, 0)]), 0])
        put("body", rot=[keys(t, [(0, 0), (6, 0), (15, 8), (18, 8), (31, 0)]),
                         keys(t, [(0, 0), (3, 10), (15, 25), (18, 25), (31, 0)]), 0])
        put("chest", rot=[keys(t, [(0, 0), (15, 4), (18, 4), (31, 0)]), 0, 0])
        put("left_arm", rot=[keys(t, [(0, 0), (6, 0), (15, -60), (18, -60), (31, 0)]), 0,
                             keys(t, [(0, 0), (6, 0), (15, 24), (18, 24), (31, 0)])])
        put("left_forearm", rot=[keys(t, [(0, 0), (15, -15), (18, -15), (31, 0)]), 0, 0])
        put("mantle", off=[0, keys(t, [(0, 0), (15, 1), (18, 1), (31, 0)]), 0])
        put("hover", off=[0, keys(t, [(0, 0), (15, 2), (16, 2), (17, 5), (19, 2), (31, 0)]), 0])
        ch["ring_tilt"] = [0.0, keys(t, [(0, 0), (6, 0), (15, 10), (18, 10), (31, 0)])]
        ch["ring_off"] = keys(t, [(0, [0, 0, 0]), (15, [-2, 0, -2]), (18, [-2, 0, -2]), (31, [0, 0, 0])])
        ch["lantern"] = keys(t, [(0, 1.8), (16, 2.0), (17, 2.0), (31, 1.0)])

    if clip == "hit":          # sheet 5.5 (a blow from inputs["hit_dir"], default the front)
        st_ = 1.0 if t >= 0 else 0.0
        put("hover", off=[0, keys(t, [(0, -3), (2, -3), (8, 0)]) * st_, 0])
        put("head", rot=[keys(t, [(0, 15), (2, 15), (8, 0)]), 0, 0])
        ch["lean_kick"] = 12.0 if t == 0 else 0.0
        ch["tilt_kick"] = 18.0 if t == 0 else 0.0
        ch["rattle"] = 15.0 if t == 0 else 0.0
        ch["pitch_kick"] = 30.0 if t == 0 else 0.0
        ch["tatter_kick"] = -6.0 if t == 0 else 0.0

    if clip == "death":        # sheet 5.6
        fall = keys(t, [(0, 0.0), (6, 0.0)])
        if 6 <= t <= 16:
            u = (t - 6) / 10.0
            fall = -DEATH_DROP * u * u                                  # gravity ease-in
        elif t > 16:
            fall = -DEATH_DROP + keys(t, [(16, 0.0), (17, 1.0), (19, 0.0)])
        put("hover", off=[0, fall, 0])
        put("head", rot=[keys(t, [(0, 0), (3, 35), (6, 35), (16, 35), (30, -36)]), 0, 0])
        put("body", rot=[keys(t, [(0, 0), (3, 8), (6, 8), (16, 8), (30, -26)]), 0, 0])
        put("chest", rot=[keys(t, [(0, 0), (3, 6), (6, 6), (16, 6), (30, -40)]), 0, 0])
        put("right_arm", rot=[keys(t, [(0, 0), (6, 0), (16, 0), (30, 50)]), 0,
                              keys(t, [(0, 0), (3, -58), (6, -58), (16, -58), (30, 12)])])
        put("left_arm", rot=[keys(t, [(0, 0), (6, 0), (16, 0), (30, 50)]), 0,
                             keys(t, [(0, 0), (3, 58), (6, 58), (16, 58), (30, -12)])])
        for side in ("right", "left"):
            put(side + "_forearm", rot=[keys(t, [(0, 0), (3, -25), (16, -25), (30, -35)]), 0, 0])
        fold = keys(t, [(0, 0.0), (16, 0.0), (30, 1.0)])
        put("tatter_f", rot=[80 * fold, 0, 0])
        put("tatter_b", rot=[-80 * fold, 0, 0])
        put("tatter_l", rot=[0, 0, 80 * fold])
        put("tatter_r", rot=[0, 0, -80 * fold])
        ch["tatter_hold"] = fold > 0
        ch["lantern"] = keys(t, [(0, 1.0), (1, 2.5), (6, 2.5), (8, 0.3), (30, 0.3), (40, 0.0)])
        ch["alpha"] = keys(t, [(0, 1.0), (35, 1.0), (50, 0.0)])
        ch["ring_spin"] = 0.6
    return B, ch


# ------------------------------------------------------------------ the step ---------------------------------------
def start_clip(st, clip, inp):
    """Clip-entry bookkeeping (t == 0): the volley's release order, the bind's new slot, the death's fall list."""
    n = st["n"]
    if clip == "volley":
        tgt = inp.get("target", [0.0, 20.0, -80.0])
        bearing = math.degrees(math.atan2(-tgt[0], -tgt[2]))           # the slot-yaw convention
        diff = {k: ((s["angle"] + st["ring_yaw"] + 11 * 6.0 - bearing + 180) % 360) - 180   # ~ the yaw at t14
                for k, s in st["slots"].items()}
        first = min(diff, key=lambda k: abs(diff[k]))
        left = sorted([k for k in diff if diff[k] > diff[first]], key=lambda k: diff[k])
        right = sorted([k for k in diff if diff[k] < diff[first]], key=lambda k: -diff[k])
        order, i = [first], 0
        while left or right:
            src = left if (i % 2 == 0 and left) or not right else right
            order.append(src.pop(0))
            i += 1
        st["order"] = {k: o for o, k in enumerate(order)}
        st["last_release"] = VOLLEY_RELEASE + RELEASE_GAP * (len(order) - 1)
        st["f_end"] = st["last_release"] + 2 + 10
        st["target"] = list(tgt)
    if clip == "bind":
        k = n + 1
        st["slots"][k] = {"angle": G.BASE_YAW + 360.0 / k * (k - 1), "ang_v": 0.0, "radial": [0.0, 0.0],
                          "roll": [0.0, 0.0], "kick": [0.0, 0.0], "mode": "FORGING", "since": 0, "data": {}}
        st["n"] = k
    if clip == "death":
        st["falling"] = {}


def step(state, inp):
    st = copy.deepcopy(state)
    clip, t = inp["clip"], inp["t"]
    if t == 0:
        start_clip(st, clip, inp)
    n = st["n"]
    v = inp.get("speed", 0.0) if clip == "move" else 0.0
    # ---- phases (kept as state; the move shortens the bob to 40 ticks)
    st["speed"] = v
    st["bob"] = (st["bob"] + 2 * math.pi / (60.0 - 20.0 * v)) % (2 * math.pi)
    st["breath"] = (st["breath"] + 2 * math.pi / 80.0) % (2 * math.pi)
    st["look"] = (st["look"] + 2 * math.pi / 160.0) % (2 * math.pi)
    st["seed"], _r = lcg(st["seed"])
    B, ch = body_targets(st, clip, t, inp)
    # ---- the root: velocity / acceleration in model space (drives the springs)
    hov = B.get("hover", {"off": [0, 0, 0]})["off"]
    root_v = [0.0, 0.0, -v * 4.0]                                       # 0.25 blocks/tick = 4 px/tick forward
    prev = st["root"]
    acc = [(hov[i] - 2 * prev[0][i] + prev[1][i]) + (root_v[i] - prev[2][i]) for i in range(3)]
    st["root"] = [list(hov), list(prev[0]), root_v]
    drive_x = DRIVE * acc[2]            # accelerating toward -z swings hanging parts back (-x)
    drive_z = -DRIVE * acc[0]
    S = st["springs"]
    # ---- the body's lean (move: starts late, overshoots on the stop; hit: kicked)
    if ch.get("lean_kick"):
        S["body_lean"] = [ch["lean_kick"], 0.0]
    S["body_lean"] = spring(S["body_lean"], ch["lean_target"], K_LEAN, C_LEAN)
    b = B.setdefault("body", {"rot": [0.0, 0.0, 0.0], "off": [0.0, 0.0, 0.0]})
    b["rot"][0] += S["body_lean"][0]
    lean = b["rot"][0] + B.get("chest", {"rot": [0, 0, 0]})["rot"][0]
    # ---- cloth and chains
    for i, tb in enumerate(TATTERS):
        if ch.get("tatter_hold"):
            continue
        if ch.get("tatter_kick"):
            S[tb][1] += ch["tatter_kick"]
        tgt = ch["tatter"] + ch["tatter_add"][i] - 0.5 * b["rot"][0]
        S[tb] = spring(S[tb], tgt, K_CHAIN, C_CHAIN, drive_x, *CLAMP["tatter"])
        B.setdefault(tb, {"rot": [0.0, 0.0, 0.0], "off": [0.0, 0.0, 0.0]})["rot"][0] += S[tb][0]
    S["cape"] = spring(S["cape"], ch["cape"] - 0.5 * b["rot"][0], K_CHAIN, C_CHAIN, drive_x, *CLAMP["cape"])
    B.setdefault("cape", {"rot": [0.0, 0.0, 0.0], "off": [0.0, 0.0, 0.0]})["rot"][0] += S["cape"][0]
    for k in (1, 2, 3):
        a, bb = "hem_chain_%d" % k, "hem_chain_%d_b" % k
        lo, hi = CLAMP["hem_front"] if k < 3 else CLAMP["hem_back"]
        trail = -ch.get("trail", 0.0) * 3 if k == 3 else 0.0
        S[a] = spring(S[a], -b["rot"][0] + trail, K_CHAIN, C_CHAIN, drive_x, lo, hi)
        # the second segment follows the first (target 0 in its frame), pushed back by the first's swing: the tip
        # lags the root by ~3 ticks
        S[bb] = spring(S[bb], 0.0, K_CHAIN_B, C_CHAIN, drive_x - 0.5 * S[a][1], *CLAMP["hem_b"])
        B.setdefault(a, {"rot": [0.0, 0.0, 0.0], "off": [0.0, 0.0, 0.0]})["rot"][0] += S[a][0]
        B.setdefault(bb, {"rot": [0.0, 0.0, 0.0], "off": [0.0, 0.0, 0.0]})["rot"][0] += S[bb][0]
    S["lantern_chain"] = spring(S["lantern_chain"], -lean, K_CHAIN, C_CHAIN, drive_x, *CLAMP["lantern"])
    B.setdefault("lantern_chain", {"rot": [0.0, 0.0, 0.0], "off": [0.0, 0.0, 0.0]})["rot"][0] += S["lantern_chain"][0]
    for side in ("right", "left"):
        arm = B.get(side + "_arm", {"rot": [0, 0, 0]})["rot"]
        fore = B.get(side + "_forearm", {"rot": [0, 0, 0]})["rot"]
        tx = -(lean + arm[0] + fore[0])
        tz = -arm[2]
        S[side + "_shackle_x"] = spring(S[side + "_shackle_x"], tx, K_CHAIN, C_CHAIN, drive_x, *CLAMP["shackle"])
        S[side + "_shackle_z"] = spring(S[side + "_shackle_z"], tz, K_CHAIN, C_CHAIN, drive_z, *CLAMP["shackle"])
        B.setdefault(side + "_shackle_chain", {"rot": [0.0, 0.0, 0.0], "off": [0.0, 0.0, 0.0]})["rot"] = [
            S[side + "_shackle_x"][0], 0.0, S[side + "_shackle_z"][0]]
    # ---- the wheel
    st["ring_yaw_v"] = ch["ring_spin"]
    st["ring_yaw"] = (st["ring_yaw"] + st["ring_yaw_v"]) % 360.0
    # the tilt: the clip's keyed tilt + a spring perturbation (kicked by hits, driven by the root's acceleration)
    if ch.get("tilt_kick"):
        S["ring_tilt_x"][1] += ch["tilt_kick"] * 0.6
    S["ring_tilt_x"] = spring(S["ring_tilt_x"], 0.0, K_TILT, C_TILT, -0.5 * drive_x)
    S["ring_tilt_z"] = spring(S["ring_tilt_z"], 0.0, K_TILT, C_TILT, -0.5 * drive_z)
    tilt = [ch["ring_tilt"][0] + S["ring_tilt_x"][0], ch["ring_tilt"][1] + S["ring_tilt_z"][0]]
    ring_off = [hov[i] + ch["ring_off"][i] for i in range(3)]
    B["ring"] = {"rot": [tilt[0], st["ring_yaw"], tilt[1]], "off": list(ch["ring_off"])}
    st["ring_now"] = (ring_matrix(tilt[0], st["ring_yaw"], tilt[1]),
                      [RING_PIVOT[i] + ring_off[i] for i in range(3)])
    st["hub_now"] = HUB_now(B)
    # ---- every slot
    targets = {k: a for k, a in zip(sorted(st["slots"]), layout_angles(n))}
    slots_out, flying, hidden, ribbons = {}, {}, list(ch["hidden"]), []
    for k, s in st["slots"].items():
        if clip == "bind":
            # re-space: every angle springs to the new even spacing (about one overshoot)
            s["ang_v"] += -K_SPACE * (s["angle"] - targets[k]) - C_SPACE * s["ang_v"]
            s["angle"] += s["ang_v"]
            if t == 18:
                s["radial"][1] += 1.0                                   # the wheel pulses out 2 px and back
        if ch.get("rattle"):
            s["roll"][1] += ch["rattle"] * (1 if k % 2 else -1) * 0.5
        if ch.get("pitch_kick"):
            s["kick"][1] += ch["pitch_kick"] * (1 if k % 2 else -1) * 0.5
        s["radial"] = spring(s["radial"], 0.0, K_RADIAL, C_RADIAL)
        s["roll"] = spring(s["roll"], 0.0, K_ROLL, C_ROLL)
        s["kick"] = spring(s["kick"], 0.0, K_ROLL, C_ROLL)
        bob_k = math.sin(2 * math.pi * st["tick"] / 40.0 + math.radians(36 * k))
        pitch = ch["cock"] + s["kick"][0] + 3 * bob_k
        roll = s["roll"][0] + 8 * bob_k
        radial = s["radial"][0] + bob_k
        mode = s["mode"]
        if clip == "volley" and k in st["order"]:
            mode = weapon_flight(st, s, k, t, flying, ribbons)
        if clip == "death" and t >= 6 + 2 * (k - 1):
            mode = weapon_fall(st, s, k, t, flying, hidden)
        if mode == "FORGING":
            if t < 18:
                hidden.append("weapon_%d" % k)
            else:
                g = smooth((t - 18) / 6.0)
                s["data"]["forge"] = 0.57 + 0.43 * g
                if t >= 24:
                    s["mode"] = "DOCKED"
                    mode = "DOCKED"
        if mode in ("DOCKED", "FORGING") and ("weapon_%d" % k) not in hidden:
            slots_out[k] = {"angle": round(s["angle"], 3), "radial": round(radial, 3), "y": 0.0,
                            "pitch": round(pitch, 3), "roll": round(roll, 3),
                            "scale": round(s["data"].get("forge", 1.0), 3) if mode == "FORGING" else 1.0}
            if clip != "death" or t < 6:
                ribbons.append([list(st["hub_now"]), slot_world(st, s["angle"], radial), 2.0, 1.2])
    st["tick"] += 1
    pose = {"bones": {k: {"rot": [round(x, 3) for x in e["rot"]], "off": [round(x, 3) for x in e["off"]]}
                      for k, e in B.items()},
            "slots": slots_out, "flying": flying, "hidden": sorted(set(hidden)), "alpha": round(ch["alpha"], 3),
            "lantern": round(ch["lantern"], 3), "ribbons": [[[round(x, 2) for x in r[0]], [round(x, 2) for x in r[1]],
                                                            r[2], r[3]] for r in ribbons]}
    if ch["lantern"] < 0.5:
        pose["hidden"] = sorted(set(pose["hidden"]) | {"lantern_core"})
    return st, pose


def HUB_now(B):
    h = B.get("hover", {"off": [0, 0, 0]})["off"]
    return [HUB[i] + h[i] for i in range(3)]


def weapon_flight(st, s, k, t, flying, ribbons):
    """One weapon of the volley: DOCKED -> SLUNG (2) -> FLYING -> EMBED (3) -> REEL (8) -> DOCKED (+2 px overshoot)."""
    tr = VOLLEY_RELEASE + RELEASE_GAP * st["order"][k]
    tau = t - tr
    if tau < 0:
        return "DOCKED"
    D = s["data"]
    tgt = st["target"]
    d_blocks = math.sqrt(sum(v * v for v in tgt[::2])) / 16.0
    F = int(round(6 + 0.4 * d_blocks))
    full = 1.0 / G.DOCK_SCALE[k]
    if tau == 0:
        p0 = slot_world(st, s["angle"], s["radial"][0])
        a = math.radians(s["angle"] + st["ring_yaw"])
        radial = [-math.sin(a), 0.0, -math.cos(a)]
        tangent = [-math.cos(a), 0.0, math.sin(a)]
        D.update(p0=p0, radial=radial, tangent=tangent, q0=q_from_mat(slot_rot_world(st, s["angle"])))
    p0, radial, tangent = D["p0"], D["radial"], D["tangent"]
    sling_end = [p0[i] + (tangent[i] * 3 + radial[i] * 3) for i in range(3)]
    sling_end[1] += 1.0
    L = LENGTH[k] * 0.8
    P1 = [sling_end[i] + radial[i] * 32 for i in range(3)]
    P1[1] += 24
    P2 = list(tgt)
    P2[1] += (1.5 + 0.1 * d_blocks) * 16
    T_end = [tgt[i] - P2[i] for i in range(3)]
    nT = math.sqrt(sum(x * x for x in T_end))
    T_end = [x / nT for x in T_end]
    P3 = [tgt[i] - T_end[i] * L for i in range(3)]
    scale = 1.0 + (full - 1.0) * smooth(tau / 4.0)
    kind, rate = TUMBLE[k]
    if tau < SLING:
        u = smooth((tau + 1) / float(SLING))
        pos = [p0[i] + (sling_end[i] - p0[i]) * u for i in range(3)]
        q = D["q0"]
        state = "SLUNG"
    elif tau < SLING + F:
        u = (tau - SLING + 1) / float(F)
        pos = bezier(sling_end, P1, P2, P3, u)
        T = bezier_d(sling_end, P1, P2, P3, u)
        base = q_from_mat(frame_along(T))
        ang = rate * (tau - SLING + 1)
        if tau >= SLING + F - 2:            # the last 2 ticks: turn point-first
            ang0 = rate * (F - 2)
            ang = ang0 + ((math.ceil(ang0 / 360.0) * 360.0) - ang0) * smooth((tau - (SLING + F - 3)) / 2.0)
        if kind == "roll":
            q = q_mul(base, q_from_axis((0, 0, 1), ang))
        elif kind == "flat":
            q = q_mul(q_from_axis((0, 1, 0), ang), base)
        else:
            q = q_mul(base, q_from_axis((1, 0, 0), ang))
        state = "FLYING"
        D["q_end"], D["p_end"] = q, pos
    elif tau < SLING + F + EMBED:
        pos, q = D["p_end"], D["q_end"]
        state = "EMBED"
    elif tau < SLING + F + EMBED + REEL:
        u = (tau - (SLING + F + EMBED) + 1) / float(REEL)
        e = u * u                                                     # slow start, fast arrival
        dock = slot_world(st, s["angle"], 0.0)
        mid = [(D["p_end"][i] + dock[i]) / 2 for i in range(3)]
        mid[1] += 8.0
        pos = [(1 - e) ** 2 * D["p_end"][i] + 2 * (1 - e) * e * mid[i] + e * e * dock[i] for i in range(3)]
        qd = q_from_mat(slot_rot_world(st, s["angle"]))
        q = q_mul(q_slerp(D["q_end"], qd, e), q_from_axis(D["tangent"], 360.0 * e))
        scale = full + (1.0 - full) * e
        state = "REEL"
        if u >= 1.0:
            s["radial"] = [DOCK_OVERSHOOT, 0.0]
            s["mode"] = "DOCKED"
            return "DOCKED"
    else:
        return "DOCKED"
    R = q_to_mat(q)
    flying[k] = {"state": state, "pos": [round(x, 3) for x in pos], "rot": euler(R), "scale": round(scale, 3)}
    hub = st["hub_now"]
    sag = 0.0 if state == "EMBED" else 0.15 * math.sqrt(sum((pos[i] - hub[i]) ** 2 for i in range(3))) * (
        1.0 - min(1.0, max(0.0, (tau - SLING) / float(F))))
    ribbons.append([list(hub), [round(x, 2) for x in pos], round(sag, 2), 1.6 * (1.3 if tau == SLING + F else 1.0)])
    return state


def weapon_fall(st, s, k, t, flying, hidden):
    """Death: the weapon lets go (one every 2 ticks in ring order), flung outward, falls, shatters on the ground."""
    F = st["falling"]
    t0 = 6 + 2 * (k - 1)
    if k not in F:
        p = slot_world(st, s["angle"], 0.0)
        a = math.radians(s["angle"] + st["ring_yaw"])
        vel = [-math.sin(a) * 1.2 - math.cos(a) * 0.6, 0.0, -math.cos(a) * 1.2 + math.sin(a) * 0.6]
        F[k] = {"p": p, "v": vel, "q": q_from_mat(slot_rot_world(st, s["angle"])), "ground": None}
    f = F[k]
    if f["ground"] is not None and t - f["ground"] >= 3:
        hidden.append("weapon_%d" % k)
        return "GONE"
    if f["ground"] is None:
        f["v"][1] -= GRAVITY
        f["p"] = [f["p"][i] + f["v"][i] for i in range(3)]
        f["q"] = q_mul(q_from_axis((1, 0, 0), 25.0), f["q"])
        if f["p"][1] <= 1.0:
            f["p"][1] = 1.0
            f["ground"] = t
    flying[k] = {"state": "FALLING" if f["ground"] is None else "SHATTER", "pos": [round(x, 3) for x in f["p"]],
                 "rot": euler(q_to_mat(f["q"])), "scale": 1.0}
    del t0
    return "FALLING"


# ------------------------------------------------------------------ clips as runs --------------------------------
CLIPS = {
    "idle": dict(clip="idle", n=10, ticks=60),
    "fidget": dict(clip="fidget", n=10, ticks=30),
    "move": dict(clip="move", n=10, ticks=50),
    "volley_N5": dict(clip="volley", n=5, ticks=60, target=[0.0, 20.0, -80.0]),
    "volley_N10": dict(clip="volley", n=10, ticks=60, target=[0.0, 20.0, -80.0]),
    "bind": dict(clip="bind", n=5, ticks=32, target=[-40.0, 4.0, -56.0]),
    "hit": dict(clip="hit", n=10, ticks=16),
    "death": dict(clip="death", n=10, ticks=50),
}


def move_speed(t):
    """The move clip's speed profile: start (10 ticks), full speed, stop at t30 (4 ticks), then settle."""
    return keys(t, [(0, 0.0), (10, 1.0), (30, 1.0), (34, 0.0)])


def run(name, seed=1, warm=40):
    """[(t, pose)] for one clip: the state is warmed up in idle first so the springs and phases are live."""
    c = CLIPS[name]
    st = initial_state(c["n"], seed)
    for i in range(warm):
        st, _ = step(st, {"clip": "idle", "t": i + 1})
    out = []
    for t in range(c["ticks"]):
        inp = {"clip": c["clip"], "t": t, "target": c.get("target", [0.0, 20.0, -80.0])}
        if c["clip"] == "move":
            inp["speed"] = move_speed(t)
        st, pose = step(st, inp)
        out.append((t, pose))
    return out


def fixture(every=5):
    return {"generator": "tools/tithe_binder_anim.py", "every": every,
            "clips": {name: {"n": CLIPS[name]["n"], "ticks": CLIPS[name]["ticks"],
                             "poses": {str(t): p for t, p in run(name) if t % every == 0}} for name in CLIPS}}


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--fixture", help="write the sampled-pose fixture (JSON) here")
    ap.add_argument("--every", type=int, default=5)
    a = ap.parse_args()
    if a.fixture:
        with open(a.fixture, "w") as f:
            json.dump(fixture(a.every), f, separators=(",", ":"))
        print("wrote", a.fixture)


if __name__ == "__main__":
    main()
