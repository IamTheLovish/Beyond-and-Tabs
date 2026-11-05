#!/usr/bin/env python3
"""The Quarry Saint's clips as code: the spec the Java rig (QuarrySaintUnitModel + its renderer) ports 1:1.
Design sheet .scratch/unit2/design-sheet.md section 3 (clips), 3.0 (springs, grind, weight rule), with the r1-r3
review changes (the sweep winds up on the LEFT, the walk carries the maul over the right shoulder, knees capped).

    state = initial_state(seed)                     # a plain dict: phases, springs, timers, the walk, death pieces
    state, pose = step(state, inputs)               # one tick (20 tps); pure: the input state is not modified
    python tools/quarry_saint_anim.py --keys poses.json    # rebuild design/fixtures/quarry_saint_keys.json (the
                                                           # solved key poses of tools/quarry_saint_poses.py + ankles)
    python tools/quarry_saint_anim.py --fixture design/fixtures/quarry_saint_clips.json   # the sampled-pose fixture
    python tools/quarry_saint_anim.py --measure out.json   # per clip: hands on the haft, planted-foot slip, knees,
                                                           # feet under the ground (FK of the DSL model)

inputs: {"clip": "idle"|"walk"|"sweep"|"slam"|"gatefall"|"glance"|"stagger"|"death", "t": tick inside the clip
         (0 = its first), "speed": 0..1 (walk: v / VMAX), "hit_dir": [x, z] (glance / stagger / death: where the blow
         comes FROM, unit vector), "event": optional "shift" | "replant" (idle: fire now; otherwise seeded timers)}
pose:   {"bones": {name: {"rot": [x, y, z] DSL degrees ADDED to the rest pose, "off": [x, y, z] DSL px in the
                   parent's frame}},
         "glow": crack glow g 0..2.2, "glow_disc": the maul's disc, "planted": ["r", "l"] feet on the ground,
         "root": [x, y, z] px the unit moved this clip (walk: -z forward), "fx": [[kind, strength, where]]}

How the body moves: the key poses (design/fixtures/quarry_saint_keys.json, every limb SOLVED offline so both fists
sit on the haft and the feet on y = 0) are blended by tick tables with smoothstep (S), linear (L, the strikes),
gravity (G, t^2, falls) or hold (H). The legs are NOT blended: every tick each standing foot's ankle target (the
keys' ankles, or the walk's stride) is reached by a closed-form two-bone IK (leg_ik) and the foot is levelled, so a
planted foot cannot slide. On top: the stone breath, the weight shift, the grind (stepped joint motion while a joint
turns faster than 40 deg/s), springs for the stole, the belt tabard, the votive chain, the maul's bob, the mass lag
and the shards (sheet 3.0). Randomness: one seeded LCG in the state (the idle timers). Conventions
(design/models/README.md): DSL degrees, +x swings a hanging part forward / tips a standing part back, +y turns the
front toward -x, order x then y then z; every rotation here is a DELTA on the model json's rest.
"""
import argparse
import copy
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, ".."))
MODEL = os.path.join(ROOT, "design", "models", "sunforged_quarry_saint.json")
KEYS_FILE = os.path.join(ROOT, "design", "fixtures", "quarry_saint_keys.json")

# ------------------------------------------------------------------ constants (the Java port copies these) ---------
VMAX = 1.6                     # px/tick at speed 1 (0.1 blocks/tick: the sheet's ~0.22 movement speed)
STEP = 12.0                    # px per step (r3: the legs reach +-6 px from the hip with the knee <= 35)
LIFT = 1.4                     # px: the swing plinth's lift (heavy: barely clears)
STANCE_KNEE = 14.0             # deg: the stance knee's bend at mid-stance (sets the hip height)
KNEE_CAP = 35.0                # deg: no standing knee bends past this
SWING_KNEE_CAP = 50.0          # deg: the swinging leg (to lift the plinth at all)
K_MASS, C_MASS = 0.25, 0.55    # sheet 3.0: the torso follows the pelvis 2 ticks late, 0.4 px overshoot
K_BOB, C_BOB = 0.18, 0.50      # the maul's bob
K_STOLE, C_STOLE = 0.12, 0.35  # stole drops (gravity-plumb target, clamp 35)
K_CHAIN, C_CHAIN = 0.20, 0.30  # votive chain (clamp 50)
K_HIT, C_HIT = 0.35, 0.60      # the stone barely flinches (projectiles)
K_LOOK, C_LOOK = 0.06, 0.40    # the slow glance
SHARD_G = 0.18                 # px/tick^2
DRIVE = 6.0                    # deg per px/tick^2 of root acceleration
GRIND_SPEED = 2.0              # deg/tick (40 deg/s): faster than this a joint grinds
GRIND_AMP = 0.6                # deg, a square wave every 2 ticks
GRIND_BONES = ("torso", "shoulder_r", "shoulder_l", "thigh_r", "thigh_l", "head")
BREATH_TICKS = 80
CLIP_TICKS = {"idle": 160, "walk": 100, "sweep": 50, "slam": 50, "gatefall": 64, "glance": 8, "stagger": 24,
              "death": 60}

# the clips' key tables: (tick, key, ease, extra) - ease of the segment ENDING at this entry
SWEEP = [(0, "ready", "S"), (3, "ready", "S"), (13, "sweep_windup", "S"), (15, "sweep_windup", "H"),
         (16, "sweep_contact", "L"), (19, "sweep_contact", "H"), (30, "sweep_follow", "S"),
         (36, "sweep_recover", "S"), (46, "ready", "S"), (50, "ready", "H")]
SLAM = [(0, "ready", "S"), (4, "slam_step", "S"), (16, "slam_windup", "S"), (19, "slam_windup", "H"),
        (20, "slam_mid", "L"), (21, "slam_contact", "L"), (25, "slam_contact", "H"), (34, "slam_contact", "S", 1.5),
        (38, "slam_wrench", "S"), (40, "slam_pop", "L"), (46, "slam_step", "S"), (49, "ready", "S"),
        (50, "ready", "H")]
GATEFALL = [(0, "ready", "S"), (8, "gatefall_brace", "S"), (30, "gatefall_windup", "S"), (33, "gatefall_windup", "H"),
            (34, "gatefall_contact", "L"), (52, "gatefall_contact", "H"), (62, "gatefall_brace", "S"),
            (64, "ready", "S")]
STAGGER = [(0, "rest", "S"), (4, "stagger", "S"), (8, "stagger", "H"), (24, "rest", "S")]
DEATH = [(0, "rest", "S"), (4, "death_hit", "S"), (8, "death_hit", "H"), (20, "death_kneel", "G"),
         (34, "death_topple", "G"), (60, "death_topple", "H")]
REPLANT = [(0, "rest", "S"), (8, "rest_lift", "S"), (10, "rest_lift", "H"), (12, "rest", "G")]
TABLES = {"sweep": SWEEP, "slam": SLAM, "gatefall": GATEFALL, "stagger": STAGGER, "death": DEATH}
# glow (g, g_disc) keyed per clip: [(tick, g, g_disc)], smoothstep between
GLOW = {"sweep": [(0, .45, .45), (3, .45, .45), (13, .45, 1.0), (16, .45, 1.6), (18, .45, 1.6), (19, .45, 1.0),
                  (46, .45, .45)],
        "slam": [(0, .45, .45), (4, .45, .45), (16, 1.2, 1.2), (21, 1.8, 1.8), (23, 1.8, 1.8), (24, 1.0, 1.0),
                 (46, .45, .45)],
        "gatefall": [(0, .45, .45), (8, .45, .45), (30, 2.0, 2.0), (31, 2.0, 2.0), (32, 2.0, 2.2), (33, 2.0, 2.0),
                     (38, 2.0, 2.0), (48, .2, .3), (64, .2, .3)],
        "death": [(0, 1.0, 1.0), (4, 2.0, 2.0), (8, 2.0, 2.0), (10, 0.0, 0.0), (60, 0.0, 0.0)]}
GROUND_HITS = {"sweep": [(16, 0.3)], "slam": [(4, 0.3), (21, 1.0), (25, 0.4), (29, 0.4), (40, 0.5), (49, 0.3)],
               "gatefall": [(4, 0.5), (8, 0.5), (34, 1.2), (38, 0.6), (44, 0.6)],
               "stagger": [(8, 0.6)], "death": [(20, 0.7), (24, 0.8), (34, 1.0)]}


# ------------------------------------------------------------------ maths (plain, portable) -----------------------
def smooth(x):
    x = max(0.0, min(1.0, x))
    return x * x * (3.0 - 2.0 * x)


def ease(kind, u):
    u = max(0.0, min(1.0, u))
    if kind == "L":
        return u
    if kind == "G":
        return u * u
    if kind == "H":
        return 1.0
    return smooth(u)


def spring(s, target, k, c, drive=0.0, lo=None, hi=None):
    """One tick of a spring; s = [x, v]. Returns the new [x, v]."""
    x, v = s
    v = v + (-k * (x - target) - c * v + drive)
    x = x + v
    if lo is not None and x < lo:
        x, v = lo, 0.0
    if hi is not None and x > hi:
        x, v = hi, 0.0
    return [x, v]


def lcg(seed):
    seed = (seed * 1103515245 + 12345) & 0x7FFFFFFF
    return seed, seed / float(0x7FFFFFFF)


def rot_mat(rx, ry, rz):
    cx, sx = math.cos(math.radians(rx)), math.sin(math.radians(rx))
    cy, sy = math.cos(math.radians(ry)), math.sin(math.radians(ry))
    cz, sz = math.cos(math.radians(rz)), math.sin(math.radians(rz))
    Rx = ((1, 0, 0), (0, cx, -sx), (0, sx, cx))
    Ry = ((cy, 0, sy), (0, 1, 0), (-sy, 0, cy))
    Rz = ((cz, -sz, 0), (sz, cz, 0), (0, 0, 1))
    return mul(Rz, mul(Ry, Rx))


def mul(A, B):
    return tuple(tuple(sum(A[i][k] * B[k][j] for k in range(3)) for j in range(3)) for i in range(3))


def tr(A):
    return tuple(tuple(A[j][i] for j in range(3)) for i in range(3))


def app(M, v):
    return tuple(sum(M[i][k] * v[k] for k in range(3)) for i in range(3))


def euler(R):
    ry = math.asin(max(-1.0, min(1.0, -R[2][0])))
    if math.cos(ry) > 1e-4:
        rx, rz = math.atan2(R[2][1], R[2][2]), math.atan2(R[1][0], R[0][0])
    else:
        rx, rz = 0.0, math.atan2(-R[0][1], R[1][1])
    return [math.degrees(rx), math.degrees(ry), math.degrees(rz)]


def wrap(a):
    return (a + 180.0) % 360.0 - 180.0


# ------------------------------------------------------------------ the model's joints and the keys ----------------
def _load():
    m = json.load(open(MODEL))
    bones = {b["name"]: b for b in m["bones"]}
    keys = json.load(open(KEYS_FILE)) if os.path.exists(KEYS_FILE) else {}
    return m, bones, keys


MODEL_JSON, BONES, KEYS = _load()
REST_ROT = {n: list(b.get("rotation", [0, 0, 0])) for n, b in BONES.items()}
PELVIS_PIV = tuple(BONES["pelvis"]["pivot"])
HIP = {k: tuple(BONES["thigh_" + k]["pivot"]) for k in ("r", "l")}
KNEE = {k: tuple(BONES["shin_" + k]["pivot"]) for k in ("r", "l")}
ANKLE = {k: tuple(BONES["foot_" + k]["pivot"]) for k in ("r", "l")}
THIGH_LEN = math.dist(HIP["r"], KNEE["r"])
SHIN_VEC = tuple(ANKLE["r"][i] - KNEE["r"][i] for i in range(3))          # knee -> ankle at rest (0, -6.5, -1)
SHIN_LEN = math.sqrt(sum(v * v for v in SHIN_VEC))
SHIN_DELTA = math.degrees(math.atan2(-SHIN_VEC[2], -SHIN_VEC[1]))         # its forward lean at rest (deg)


def key(name):
    return KEYS[name]


def key_rot(name, bone):
    return KEYS[name].get(bone, [0.0, 0.0, 0.0])


def blend_keys(table, t):
    """The tick table at t: ({bone: rot delta}, pelvis move [x, y, z], ankles {r, l}, extra sag, the two keys, u)."""
    if t <= table[0][0]:
        a = b = table[0]
        u = 1.0
    else:
        a, b = table[0], table[-1]
        u = 1.0
        for e0, e1 in zip(table, table[1:]):
            if t <= e1[0]:
                a, b = e0, e1
                u = ease(e1[2], (t - e0[0]) / float(e1[0] - e0[0]))
                break
    ka, kb = KEYS[a[1]], KEYS[b[1]]
    rot = {}
    for n in set(ka) | set(kb):
        if n.startswith("_"):
            continue
        ra, rb = ka.get(n, [0.0, 0.0, 0.0]), kb.get(n, [0.0, 0.0, 0.0])
        rot[n] = [ra[i] + (wrap(rb[i] - ra[i])) * u for i in range(3)]
    ma = ka.get("_move", {}).get("pelvis", [0.0, 0.0, 0.0])
    mb = kb.get("_move", {}).get("pelvis", [0.0, 0.0, 0.0])
    move = [ma[i] + (mb[i] - ma[i]) * u for i in range(3)]
    sag = (a[3] if len(a) > 3 else 0.0) + ((b[3] if len(b) > 3 else 0.0) - (a[3] if len(a) > 3 else 0.0)) * u
    ank = {}
    for k in ("r", "l"):
        pa, pb = ka["_ankle"][k], kb["_ankle"][k]
        p = [pa[i] + (pb[i] - pa[i]) * u for i in range(3)]
        dist = math.dist(pa, pb)
        if dist > 0.5 and a[1] != b[1]:                      # a foot that changes place steps (lifts), never slides
            p[1] += min(1.5, dist * 0.5) * 4.0 * u * (1.0 - u)
        ank[k] = p
    return rot, move, ank, sag, (a[1], b[1], u)


# ------------------------------------------------------------------ the legs: closed-form two-bone IK ---------------
def leg_reach(s_deg):
    """Hip -> ankle distance with the knee bent s_deg (DSL x of the shin, <= 0)."""
    a = math.radians(s_deg + SHIN_DELTA)
    L2 = (SHIN_LEN * math.sin(a), SHIN_LEN * math.cos(a))
    return math.hypot(L2[0], THIGH_LEN + L2[1])


D_MAX = leg_reach(0.0) - 0.01
D_CAP = leg_reach(-KNEE_CAP) + 0.01


def pelvis_fit(ank, pel_rot, move, planted):
    """The pelvis height that lets every planted leg reach its foot with the knee inside [0, KNEE_CAP]: each planted
    foot allows a band of hip heights; the move's y is clamped into their intersection (the legs are short: the band
    is ~0.3 px tall at the 35 deg cap, so the sheet's drops shrink to what the knees allow)."""
    Rp = rot_mat(*pel_rot)
    lo_all, hi_all = -1e9, 1e9
    for k in planted:
        h = app(Rp, [HIP[k][i] - PELVIS_PIV[i] for i in range(3)])
        hip = [h[i] + PELVIS_PIV[i] + move[i] for i in range(3)]
        hz2 = (hip[0] - ank[k][0]) ** 2 + (hip[2] - ank[k][2]) ** 2
        vhi = math.sqrt(max(0.0, D_MAX ** 2 - hz2))
        vlo = math.sqrt(max(0.0, D_CAP ** 2 - hz2))
        lo_all = max(lo_all, ank[k][1] + vlo - hip[1])
        hi_all = min(hi_all, ank[k][1] + vhi - hip[1])
    if lo_all > hi_all:
        dy = (lo_all + hi_all) / 2.0
    else:
        dy = min(max(0.0, lo_all), hi_all)
    return [move[0], move[1] + dy, move[2]]


def leg_ik(k, ankle_world, pelvis_rot, pelvis_move, cap=KNEE_CAP):
    """thigh [x, 0, z], shin [x, 0, 0], foot rotation (level in the world): the ankle on ankle_world (model px).
    Returns (thigh, shin, foot, knee flex deg, reach miss px)."""
    Rp = rot_mat(*pelvis_rot)
    q = [ankle_world[i] - pelvis_move[i] - PELVIS_PIV[i] for i in range(3)]
    q = app(tr(Rp), q)
    a = [q[i] + PELVIS_PIV[i] - HIP[k][i] for i in range(3)]          # hip -> ankle in the pelvis frame
    gamma = math.degrees(math.atan2(a[0], -a[1]))                      # the leg's sideways tilt (about z)
    w = math.sqrt(a[0] * a[0] + a[1] * a[1])                           # down, in the leg's plane
    f = -a[2]                                                          # forward
    d = math.hypot(f, w)
    lo, hi = abs(THIGH_LEN - SHIN_LEN) + 1e-6, THIGH_LEN + SHIN_LEN - 1e-6
    dd = max(lo, min(hi, d))
    tv = math.degrees(math.atan2(f, w))
    beta = math.degrees(math.acos(max(-1.0, min(1.0, (THIGH_LEN ** 2 + dd ** 2 - SHIN_LEN ** 2) / (2 * THIGH_LEN * dd)))))
    best = None
    for alpha in (tv - beta, tv + beta):
        kf = f * dd / d if d > 1e-9 else 0.0
        kw = w * dd / d if d > 1e-9 else dd
        a2 = math.degrees(math.atan2(kf - THIGH_LEN * math.sin(math.radians(alpha)),
                                     kw - THIGH_LEN * math.cos(math.radians(alpha))))
        s = wrap(a2 - alpha - SHIN_DELTA)
        if s <= 0.5 and (best is None or s > best[1]):
            best = (alpha, s)
    alpha, s = best
    s = max(s, -cap)                                                   # the cap wins over the reach (miss measured)
    thigh = [alpha, 0.0, gamma]
    shin = [s, 0.0, 0.0]
    Rleg = mul(Rp, mul(rot_mat(*thigh), rot_mat(*shin)))
    foot = euler(tr(Rleg))                                             # level: the foot cancels the chain above it
    return thigh, shin, foot, -s, abs(d - dd)


# ------------------------------------------------------------------ the state ---------------------------------------
SPRINGS = ("mass_y", "maul_bob", "stole_fl0", "stole_fl1", "stole_fr0", "stole_fr1", "stole_b0", "stole_b1",
           "tabard_f0", "tabard_f1", "chain_0", "chain_1", "chain_2", "hit_x", "hit_z", "look_yaw")


def initial_state(seed=1, first_shift=None, first_replant=None):
    s = seed & 0x7FFFFFFF
    s, r1 = lcg(s)
    s, r2 = lcg(s)
    return {"seed": s, "tick": 0, "breath": 0,
            "shift_next": first_shift if first_shift is not None else int(120 + r1 * 160),   # 6-14 s
            "shift_t": -1, "shift_side": 1.0,
            "replant_next": first_replant if first_replant is not None else int(200 + r2 * 200),   # 10-20 s
            "replant_t": -1,
            "phase": 0.0, "dist": 0.0, "v": 0.0, "v_prev": 0.0, "moving": 0, "stopped": 99,
            "feet": {"r": list(ANKLE["r"]), "l": list(ANKLE["l"])},
            "springs": {n: [0.0, 0.0] for n in SPRINGS},
            "shards": [[0.0, 0.0], [0.0, 0.0], [0.0, 0.0]], "shard_kick": [],
            "prev_base": {}, "pieces": {}, "root": [0.0, 0.0, 0.0], "carry": 0.0}


# ------------------------------------------------------------------ one tick ---------------------------------------
def step(state, inp):
    st = copy.deepcopy(state)
    clip, t = inp["clip"], int(inp["t"])
    st["tick"] += 1
    st["breath"] = (st["breath"] + 1) % BREATH_TICKS
    fx = []
    planted = ["r", "l"]
    glow, glow_disc = 0.45, 0.45
    sag = 0.0
    ik = True
    ank = {k: list(ANKLE[k]) for k in ("r", "l")}
    pel_rot, move = [0.0, 0.0, 0.0], [0.0, 0.0, 0.0]
    rot = {}
    off = {}
    # ---------------- the base: key tables, or the idle / walk / glance machines
    if clip in TABLES:
        rot, move, ank, sag, _ab = blend_keys(TABLES[clip], t)
        bake = KEYS.get("_bake", {}).get(clip, {}).get(str(t))
        if bake:
            for n, r in bake.items():
                rot[n] = list(r)
        pel_rot = rot.pop("pelvis", [0.0, 0.0, 0.0])
        for k in ("thigh_r", "thigh_l", "shin_r", "shin_l", "foot_r", "foot_l"):
            if clip == "death" and t >= 8:
                continue                                    # the kneel / topple keep their solved legs
            rot.pop(k, None)
        if clip == "death" and t >= 8:
            ik = False
            planted = []
        if clip == "stagger":
            planted = ["l"] if 1 <= t <= 8 else ["r", "l"]
        g = GLOW.get(clip)
        if g:
            glow, glow_disc = table1(g, t)
        for (tt, sth) in GROUND_HITS.get(clip, []):
            if t == tt:
                ground_hit(st, sth, fx)
        move = [move[0], move[1] - sag, move[2]]
    elif clip == "walk":
        rot, move, ank, pel_rot, planted = walk(st, inp, t, fx)
        glow = 0.45
    else:                                                    # idle / glance: the rest stance and its life
        rot = {n: list(v) for n, v in KEYS["rest"].items() if not n.startswith("_")}
        rot.pop("pelvis", None)
        for k in ("thigh_r", "thigh_l", "shin_r", "shin_l", "foot_r", "foot_l"):
            rot.pop(k, None)
        if clip == "idle":
            glow = idle(st, inp, t, rot, off, fx)
            pel_rot = rot.pop("pelvis_roll", [0.0, 0.0, 0.0])
            move = rot.pop("pelvis_move", [0.0, 0.0, 0.0])
        else:
            glow = 0.45
    for n in ("stole_front_l_0", "stole_front_l_1", "stole_front_r_0", "stole_front_r_1", "stole_back_0",
              "stole_back_1", "tabard_front_0", "tabard_front_1"):
        rot.pop(n, None)                                     # the drops are the springs' (below), not the keys'
    # ---------------- the glance / hit react (projectiles barely move the stone)
    S = st["springs"]
    if clip == "glance" and t == 1:
        hd = inp.get("hit_dir", [0.0, -1.0])                 # the blow comes from the front by default
        S["hit_x"][1] += -1.5 * hd[1] * K_HIT * 2.2           # away from the hit: a hit from the front tips back
        S["hit_z"][1] += 1.5 * hd[0] * K_HIT * 2.2
        fx.append(["glance", 0.25, "torso"])
        glow += 0.2
    S["hit_x"] = spring(S["hit_x"], 0.0, K_HIT, C_HIT)
    S["hit_z"] = spring(S["hit_z"], 0.0, K_HIT, C_HIT)
    add(rot, "torso", [S["hit_x"][0], 0.0, S["hit_z"][0]])
    # ---------------- the legs: a foot whose target moved this tick is stepping (not planted); the pelvis height is
    # fitted to the planted legs' knee window, then each leg is solved
    if ik and clip != "walk":
        prev_a = st.get("ank_prev") or {}
        planted = [k for k in planted if k not in prev_a or math.dist(prev_a[k], ank[k]) < 0.05] \
            if t > 0 else planted
    st["ank_prev"] = {k: list(ank[k]) for k in ("r", "l")}
    if ik and planted:
        move = pelvis_fit(ank, pel_rot, move, planted)
    if ik:
        for k in ("r", "l"):
            cap = KNEE_CAP if k in planted else SWING_KNEE_CAP
            thigh, shin, foot, knee, miss = leg_ik(k, ank[k], pel_rot, move, cap)
            rot["thigh_" + k], rot["shin_" + k], rot["foot_" + k] = thigh, shin, foot
    rot["pelvis"] = list(pel_rot)
    # ---------------- the mass lag: the torso follows the pelvis's height 2 ticks late
    S["mass_y"] = spring(S["mass_y"], move[1], K_MASS, C_MASS)
    off_t = off.setdefault("torso", [0.0, 0.0, 0.0])
    off_t[1] += S["mass_y"][0] - move[1]
    # ---------------- the grind: a joint turning faster than 40 deg/s steps (a 0.6 deg square wave every 2 ticks)
    for n in GRIND_BONES:
        cur = rot.get(n, [0.0, 0.0, 0.0])
        prev = st["prev_base"].get(n, cur)
        dv = [cur[i] - prev[i] for i in range(3)]
        sp = max(abs(v) for v in dv)
        st["prev_base"][n] = list(cur)
        if sp > GRIND_SPEED and not (clip == "death" and t >= 8):
            i = max(range(3), key=lambda j: abs(dv[j]))
            sq = GRIND_AMP if (st["tick"] // 2) % 2 == 0 else -GRIND_AMP
            r = list(cur)
            r[i] += sq
            rot[n] = r
            if st["tick"] % 4 == 0:
                fx.append(["grind_dust", 0.2, n])
    # ---------------- the stole, the belt tabard, the chain, the maul's bob
    acc = st["v"] - st["v_prev"]                               # px/tick^2 forward (walk)
    torso_x = rot.get("torso", [0.0, 0.0, 0.0])[0] + pel_rot[0]
    plumb = -torso_x                                           # brings a hanging drop back to vertical
    rest_front = REST_ROT["stole_front_l_0"][0]
    for k in ("l", "r"):
        n0 = "stole_f%s0" % k
        S[n0] = spring(S[n0], max(-rest_front, min(35.0, plumb)), K_STOLE, C_STOLE, -DRIVE * acc,
                       -rest_front, 35.0)
        n1 = "stole_f%s1" % k
        S[n1] = spring(S[n1], 0.0, K_STOLE, C_STOLE, -0.5 * S[n0][1], -20.0, 25.0)
        add(rot, "stole_front_%s_0" % k, [S[n0][0], 0.0, 0.0])
        add(rot, "stole_front_%s_1" % k, [S[n1][0], 0.0, 0.0])
    S["stole_b0"] = spring(S["stole_b0"], max(-35.0, min(0.0, plumb)), K_STOLE, C_STOLE, -DRIVE * acc, -35.0, 0.0)
    S["stole_b1"] = spring(S["stole_b1"], 0.0, K_STOLE, C_STOLE, -0.5 * S["stole_b0"][1], -20.0, 20.0)
    add(rot, "stole_back_0", [S["stole_b0"][0], 0.0, 0.0])
    add(rot, "stole_back_1", [S["stole_b1"][0], 0.0, 0.0])
    push = max(rot["thigh_r"][0], rot["thigh_l"][0], 0.0) * 0.6 if "thigh_r" in rot else 0.0
    S["tabard_f0"] = spring(S["tabard_f0"], min(35.0, push), K_STOLE, C_STOLE, -DRIVE * acc, -5.0, 35.0)
    S["tabard_f1"] = spring(S["tabard_f1"], 0.0, K_STOLE, C_STOLE, -0.5 * S["tabard_f0"][1], -20.0, 20.0)
    add(rot, "tabard_front_0", [S["tabard_f0"][0], 0.0, 0.0])
    add(rot, "tabard_front_1", [S["tabard_f1"][0], 0.0, 0.0])
    arm_v = rot.get("shoulder_r", [0.0, 0.0, 0.0])[0] - st["prev_base"].get("_arm", 0.0)
    st["prev_base"]["_arm"] = rot.get("shoulder_r", [0.0, 0.0, 0.0])[0]
    for i, n in enumerate(("chain_0", "chain_1", "chain_2")):
        S[n] = spring(S[n], 0.0, K_CHAIN, C_CHAIN, -0.4 * arm_v / (i + 1), -50.0, 50.0)
        add(rot, n, [S[n][0], 0.0, 0.0])
    S["maul_bob"] = spring(S["maul_bob"], 0.0, K_BOB, C_BOB)
    add(rot, "hand_r", [S["maul_bob"][0], 0.0, 0.0])
    # ---------------- the shards hop on every ground hit (1-tick stagger between them)
    kicks = []
    for (when, i, v0) in st["shard_kick"]:
        if when <= 0:
            st["shards"][i][1] += v0
        else:
            kicks.append((when - 1, i, v0))
    st["shard_kick"] = kicks
    for i, sh in enumerate(st["shards"]):
        y, v = sh[0] + sh[1], sh[1] - SHARD_G
        if y <= 0.0:
            y, v = 0.0, 0.0
        st["shards"][i] = [y, v]
    # ---------------- death: the pieces separate along their own bones (no new geometry), decelerating x0.8
    if clip == "death":
        death_pieces(st, t, rot, off, fx)
    # ---------------- the pose
    bones = {}
    for n, r in rot.items():
        bones[n] = {"rot": [round(v, 4) for v in r], "off": [0.0, 0.0, 0.0]}
    for n, o in off.items():
        bones.setdefault(n, {"rot": [0.0, 0.0, 0.0], "off": [0.0, 0.0, 0.0]})
        bones[n]["off"] = [round(bones[n]["off"][i] + o[i], 4) for i in range(3)]
    bones.setdefault("pelvis", {"rot": [0.0, 0.0, 0.0], "off": [0.0, 0.0, 0.0]})
    bones["pelvis"]["off"] = [round(v, 4) for v in move]
    for i, n in enumerate(("shard_0", "shard_1", "shard_2")):
        if n in BONES:
            b = bones.setdefault(n, {"rot": [0.0, 0.0, 0.0], "off": [0.0, 0.0, 0.0]})
            b["off"][1] = round(b["off"][1] + st["shards"][i][0], 4)
    st["v_prev"] = st["v"]
    if clip != "walk":
        st["v"] = 0.0
    pose = {"bones": bones, "glow": round(glow, 4), "glow_disc": round(glow_disc, 4), "planted": planted,
            "root": [round(v, 4) for v in st["root"]], "fx": fx}
    return st, pose


def add(rot, n, d):
    r = rot.get(n, [0.0, 0.0, 0.0])
    rot[n] = [r[i] + d[i] for i in range(3)]


def table1(tab, t):
    if t <= tab[0][0]:
        return tab[0][1], tab[0][2]
    for a, b in zip(tab, tab[1:]):
        if t <= b[0]:
            u = smooth((t - a[0]) / float(b[0] - a[0]))
            return a[1] + (b[1] - a[1]) * u, a[2] + (b[2] - a[2]) * u
    return tab[-1][1], tab[-1][2]


def ground_hit(st, strength, fx, where="body"):
    """The weight rule: every contact with the ground hops the shards (1-tick stagger) and kicks the maul's bob."""
    for i in range(3):
        st["shard_kick"].append((i, i, 0.5 + 0.4 * min(1.0, strength)))
    st["springs"]["maul_bob"][1] += -2.5 * strength * 0.4
    fx.append(["ground_hit", round(strength, 3), where])


# ------------------------------------------------------------------ idle -------------------------------------------
def breath(b):
    """0..1 over the 80-tick cycle: in over 20 (smoothstep), hold 10, out over 30, rest 20."""
    if b < 20:
        return smooth(b / 20.0)
    if b < 30:
        return 1.0
    if b < 60:
        return 1.0 - smooth((b - 30) / 30.0)
    return 0.0


def idle(st, inp, t, rot, off, fx):
    br = breath(st["breath"])
    add(rot, "torso", [-0.8 * br, 0.0, 0.0])
    off.setdefault("torso", [0.0, 0.0, 0.0])[1] += 0.6 * br
    glow = 0.40 + 0.20 * br
    # the weight shift (seeded every 6-14 s, or on an event): pelvis 2 px sideways and rolled 4 over 24 ticks
    if inp.get("event") == "shift" or (st["shift_t"] < 0 and st["tick"] >= st["shift_next"]):
        st["shift_t"] = 0
        st["seed"], r = lcg(st["seed"])
        st["shift_side"] = 1.0 if r < 0.5 else -1.0
        st["seed"], r = lcg(st["seed"])
        st["shift_next"] = st["tick"] + int(120 + r * 160)
    pel_move, pel_roll = [0.0, 0.0, 0.0], [0.0, 0.0, 0.0]
    if st["shift_t"] >= 0:
        u = st["shift_t"]
        w = smooth(u / 24.0) if u < 24 else (1.0 - smooth((u - 48) / 24.0) if u >= 48 else 1.0)
        sd = st["shift_side"]
        pel_move = [2.0 * sd * w, -0.4 * w, 0.0]
        pel_roll = [0.0, 0.0, 4.0 * sd * w]
        add(rot, "torso", [0.0, 0.0, -2.0 * sd * w])
        if u in (24, 26, 28):                                     # the grind settle: 3 x 0.4 px drops 2 ticks apart
            fx.append(["settle", 0.2, "pelvis"])
        if u >= 24:
            pel_move[1] -= 0.4 * min(3, (u - 22) // 2)
        st["shift_t"] += 1
        if st["shift_t"] > 72:
            st["shift_t"] = -1
    rot["pelvis_move"] = pel_move
    rot["pelvis_roll"] = pel_roll
    # the maul re-plant (seeded every 10-20 s, or on an event): lift 3 px, hold, drop GRAV, ground_hit(0.4)
    if inp.get("event") == "replant" or (st["replant_t"] < 0 and st["tick"] >= st["replant_next"]):
        st["replant_t"] = 0
        st["seed"], r = lcg(st["seed"])
        st["replant_next"] = st["tick"] + int(200 + r * 200)
    if st["replant_t"] >= 0:
        u = st["replant_t"]
        r2, _m, _a, _s, _ab = blend_keys(REPLANT, min(u, 12))
        for n in ("shoulder_r", "forearm_r", "hand_r", "chain_0"):
            if n in r2:
                rot[n] = r2[n]
        add(rot, "torso", [-2.0 * smooth(u / 8.0) * (1.0 - smooth((u - 12) / 6.0)), 0.0, 0.0])
        if u == 12:
            ground_hit(st, 0.4, fx, "maul")
            fx.append(["dust_ring", 0.4, "maul"])
        st["replant_t"] += 1
        if st["replant_t"] > 18:
            st["replant_t"] = -1
    return glow


# ------------------------------------------------------------------ walk -------------------------------------------
def walk(st, inp, t, fx):
    """The heavy stride: phase by distance (no foot slide), the stance foot drawn back under the hip at the ground
    speed, the swing foot carried forward on a 1.4 px arc, the hip height from the stance leg (lowest at contact, high
    at passing), roll over the stance leg, counter-twist; the maul swung up onto the right shoulder in the first 10
    ticks (CARRY), the left arm swinging against the legs."""
    v = max(0.0, min(1.0, inp.get("speed", 0.0))) * VMAX
    st["v"] = v
    if t == 0:
        st["carry"] = 0.0
        st["phase"] = 0.25           # the right foot at mid-stance (under the hip) and the left at mid-swing: both
        #                              at their rest place, so the first stride starts without a jump
        st["moving"] = 0
    st["carry"] = min(1.0, st["carry"] + 0.1)
    c = smooth(st["carry"])
    # the start: lean 6 deg for 6 ticks before the feet move (mass first)
    if v > 0.0:
        st["moving"] += 1
    else:
        st["moving"] = 0
    lean = -6.0 * smooth(st["moving"] / 6.0) if 0 < st["moving"] <= 12 else (-3.0 if st["moving"] > 12 else 0.0)
    advance = v if st["moving"] > 6 else 0.0
    st["dist"] += advance
    st["root"][2] -= advance
    prev = st["phase"]
    st["phase"] = (st["phase"] + advance / (2.0 * STEP)) % 1.0
    p = st["phase"]
    rot = {}
    ka, kc = KEYS["rest"], KEYS["carry"]
    for n in set(ka) | set(kc):
        if n.startswith("_") or n in ("pelvis", "thigh_r", "thigh_l", "shin_r", "shin_l", "foot_r", "foot_l"):
            continue
        ra, rc = ka.get(n, [0.0, 0.0, 0.0]), kc.get(n, [0.0, 0.0, 0.0])
        rot[n] = [ra[i] + wrap(rc[i] - ra[i]) * c for i in range(3)]
    ank = {}
    planted = []
    stance_z = 0.0
    moving = st["dist"] > 0.0 and (advance > 0.0 or st["moving"] > 0)
    for k, ph in (("r", p), ("l", (p + 0.5) % 1.0)):
        base = list(ANKLE[k])
        if not moving and st["dist"] == 0.0:
            ank[k] = base
            planted.append(k)
            continue
        if ph < 0.5:                                         # stance: forward -> back at the ground speed
            z = -STEP / 2.0 + STEP * (ph / 0.5)
            ank[k] = [base[0], base[1], base[2] + z]
            planted.append(k)
            stance_z = z
        else:                                                # swing: back -> forward on an arc
            u = (ph - 0.5) / 0.5
            z = STEP / 2.0 - STEP * smooth(u)
            lift = LIFT * math.sin(math.pi * u) if advance > 0.0 else 0.0     # stopped: it sets down where it is
            ank[k] = [base[0], base[1] + lift, base[2] + z]
            if advance <= 0.0:
                planted.append(k)
        # a contact: the plinth lands (ground_hit, shards hop, the maul's bob kicked)
    for k, ph0, ph1 in (("r", prev, p), ("l", (prev + 0.5) % 1.0, (p + 0.5) % 1.0)):
        if advance > 0.0 and ph1 < ph0:
            ground_hit(st, 0.5, fx, "foot_" + k)
            fx.append(["footfall", 0.5, "foot_" + k])
    # the hip height from the stance leg (bent STANCE_KNEE at mid-stance): an inverted pendulum
    le = math.sqrt(THIGH_LEN ** 2 + SHIN_LEN ** 2 + 2 * THIGH_LEN * SHIN_LEN * math.cos(math.radians(STANCE_KNEE)))
    rest_d = math.dist(HIP["r"], ANKLE["r"])
    h = math.sqrt(max(1.0, le * le - stance_z * stance_z)) if moving else rest_d
    drop = (h - rest_d) if moving else 0.0
    w = 1.0 if moving else 0.0
    # the weight over the stance leg (sheet 3.3: x -1 at contact, -2 passing, +1 at the other contact): continuous
    sway = math.sin(2 * math.pi * p + math.pi / 6.0)
    twist = math.cos(2 * math.pi * p)
    move = [-2.0 * sway * w, drop, 0.0]
    pel_rot = [0.0, -3.0 * twist * w, 3.0 * sway * w]
    add(rot, "torso", [lean, 3.0 * twist * w, -2.0 * sway * w])
    add(rot, "shoulder_l", [12.0 * w * twist, 0.0, 0.0])     # the left arm against the legs
    add(rot, "head", [0.0, -1.5 * twist * w, 0.0])
    return rot, move, ank, pel_rot, planted


# ------------------------------------------------------------------ death pieces ------------------------------------
PIECES = {"forearm_r": ([0.0, -4.0, 0.0], [30.0, 0.0, 0.0]), "forearm_l": ([0.0, -3.0, 0.0], [-25.0, 0.0, 20.0]),
          "head": ([0.0, -2.0, -12.0], [40.0, 60.0, 0.0]), "shard_0": ([8.0, -1.0, 4.0], [0.0, 30.0, 0.0]),
          "shard_1": ([-6.0, -1.0, 7.0], [0.0, -40.0, 0.0])}


def death_pieces(st, t, rot, off, fx):
    """t36-50: each piece slides out along its own bone and turns, its speed decaying x0.8 a tick (initial speed
    0.2 x the distance, so it travels the distance in all)."""
    if t == 8:
        fx.append(["sparks_ring", 1.0, "torso"])
    if t == 34:
        fx.append(["crumble", 1.0, "body"])
    if t < 36:
        return
    n = t - 35
    f = 1.0 - 0.8 ** n                                        # the share of the distance travelled after n ticks
    for name, (d, r) in PIECES.items():
        if name not in BONES:
            continue
        o = off.setdefault(name, [0.0, 0.0, 0.0])
        for i in range(3):
            o[i] += d[i] * f
        add(rot, name, [r[i] * f for i in range(3)])


# ------------------------------------------------------------------ runs, fixture, keys -----------------------------
CLIPS = {
    "idle": dict(clip="idle", ticks=160, every=2),
    "walk": dict(clip="walk", ticks=100, every=1),
    "sweep": dict(clip="sweep", ticks=50, every=1),
    "slam": dict(clip="slam", ticks=50, every=1),
    "gatefall": dict(clip="gatefall", ticks=64, every=1),
    "glance": dict(clip="glance", ticks=8, every=1, hit_dir=[0.0, -1.0]),
    "stagger": dict(clip="stagger", ticks=24, every=1),
    "death": dict(clip="death", ticks=60, every=1),
}


def walk_speed(t):
    """The walk's speed profile: still while the maul goes onto the shoulder (10 ticks), start (6), full, stop t86."""
    if t < 10:
        return 0.0
    if t < 16:
        return smooth((t - 10) / 6.0)
    if t < 82:
        return 1.0
    if t < 86:
        return 1.0 - smooth((t - 82) / 4.0)
    return 0.0


def run(name, seed=1):
    c = CLIPS[name]
    st = initial_state(seed, first_shift=20, first_replant=100)
    out = []
    for t in range(c["ticks"]):
        inp = {"clip": c["clip"], "t": t}
        if name == "walk":
            inp["speed"] = walk_speed(t)
        if "hit_dir" in c:
            inp["hit_dir"] = c["hit_dir"]
        st, pose = step(st, inp)
        out.append((t, pose))
    return out


def fixture():
    return {"generator": "tools/quarry_saint_anim.py", "model": "design/models/sunforged_quarry_saint.json",
            "keys": "design/fixtures/quarry_saint_keys.json",
            "clips": {n: {"ticks": c["ticks"], "every": c["every"],
                          "poses": {str(t): p for t, p in run(n) if t % c["every"] == 0}} for n, c in CLIPS.items()}}


BAKE_ARMS = ("shoulder_r", "forearm_r", "hand_r", "shoulder_l", "forearm_l", "hand_l", "chain_0")


def maul_axis(dsl, extra, move):
    """World grip point and haft direction of the maul for a key (contact_check FK of the DSL model)."""
    import contact_check
    import numpy as np
    fr = contact_check._frames(dsl, extra, move)
    M, tv = fr["maul"]
    g = BONES["maul"]["pivot"]
    G = M @ np.array(g, float) + tv
    H = M @ np.array([g[0], g[1], g[2] - 10.0], float) + tv
    d = (H - G) / np.linalg.norm(H - G)
    return [float(v) for v in G], [float(v) for v in d]


def nslerp(a, b, u):
    dot = max(-1.0, min(1.0, sum(a[i] * b[i] for i in range(3))))
    om = math.acos(dot)
    if om < 1e-4:
        return list(a)
    s0, s1 = math.sin((1 - u) * om) / math.sin(om), math.sin(u * om) / math.sin(om)
    return [a[i] * s0 + b[i] * s1 for i in range(3)]


def bake_clip(clip):
    """Offline: both arms solved for every tick of a two-hand clip (the sheet: two-hand keys baked with solve_arm, no
    IK at runtime). The base (pelvis, torso, head, move) is the clip's own blend; the right fist follows the maul's grip
    and haft direction interpolated between the two keys (position by the table's ease, direction by slerp), the left
    fist closes on the haft 11 px up from it."""
    import gen_quarry_saint as q
    import render_concept
    dsl = render_concept.load_model(MODEL)
    bind, _ = q.build()
    table = TABLES[clip]
    out = {}
    for t in range(CLIP_TICKS[clip]):
        rot, move, ank, sag, (ka, kb, u) = blend_keys(table, t)
        move = [move[0], move[1] - sag, move[2]]
        if u in (0.0, 1.0) or ka == kb:
            continue                                          # at a key: its own solved arms
        m = copy.deepcopy(bind)
        for n in ("pelvis", "torso", "head"):
            r = rot.get(n, [0.0, 0.0, 0.0])
            m.bones[n].rotation = [bind.bones[n].rotation[i] + r[i] for i in range(3)]
        ea = {n: v for n, v in KEYS[ka].items() if not n.startswith("_")}
        eb = {n: v for n, v in KEYS[kb].items() if not n.startswith("_")}
        Ga, da = maul_axis(dsl, ea, KEYS[ka].get("_move", {}))
        Gb, db = maul_axis(dsl, eb, KEYS[kb].get("_move", {}))
        G = [Ga[i] + (Gb[i] - Ga[i]) * u for i in range(3)]
        d = nslerp(da, db, u)
        T = tuple(move)
        q.place_right(m, G, tuple(d), T)
        q.place_left(m, T)
        q.chain_plumb(m)
        out[str(t)] = {n: [round(wrap(m.bones[n].rotation[i] - bind.bones[n].rotation[i]), 4) for i in range(3)]
                       for n in BAKE_ARMS}
        print("baked %s t%d (%s -> %s, u %.2f)" % (clip, t, ka, kb, u), file=sys.stderr)
    return out


def build_keys(poses_path):
    """design/fixtures/quarry_saint_keys.json: the solved key poses (tools/quarry_saint_poses.py) with each foot's
    ankle position (model px, pelvis move included) - the IK targets the clips blend."""
    sys.path.insert(0, HERE)
    import contact_check
    import render_concept
    poses = json.load(open(poses_path))
    dsl = render_concept.load_model(MODEL)
    out = {}
    for name, p in poses.items():
        extra = {k: v for k, v in p.items() if not k.startswith("_")}
        move = p.get("_move", {})
        fr = contact_check._frames(dsl, extra, move)
        ank = {}
        for k in ("r", "l"):
            M, tv = fr["foot_" + k]
            piv = BONES["foot_" + k]["pivot"]
            ank[k] = [round(sum(M[i][j] * piv[j] for j in range(3)) + tv[i], 4) for i in range(3)]
        out[name] = dict(p, _ankle=ank)
    os.makedirs(os.path.dirname(KEYS_FILE), exist_ok=True)
    json.dump(out, open(KEYS_FILE, "w"), indent=1)
    return KEYS_FILE


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--keys", help="rebuild the key file from a tools/quarry_saint_poses.py output")
    ap.add_argument("--fixture", help="write the sampled-pose fixture (JSON) here")
    ap.add_argument("--bake", help="offline: solve both arms every tick of a two-hand clip (sweep|slam|gatefall) and "
                                   "write them to this JSON (merge with --merge-bake)")
    ap.add_argument("--merge-bake", nargs="*", help="merge baked clip files into the key file")
    a = ap.parse_args()
    if a.keys:
        print("wrote", build_keys(a.keys))
    if a.bake:
        clip, path = a.bake.split(":")
        json.dump({clip: bake_clip(clip)}, open(path, "w"))
        print("wrote", path)
    if a.merge_bake:
        keys = json.load(open(KEYS_FILE))
        keys["_bake"] = {}
        for f in a.merge_bake:
            keys["_bake"].update(json.load(open(f)))
        json.dump(keys, open(KEYS_FILE, "w"), indent=1)
        print("merged", a.merge_bake)
    if a.fixture:
        with open(a.fixture, "w") as f:
            json.dump(fixture(), f, separators=(",", ":"))
        print("wrote", a.fixture)


if __name__ == "__main__":
    main()
