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
         "ribbons": [[hub, ctrl, end, width px]]  - the soul chains: quadratic Bezier hub -> end with control point
                   ctrl (r4; r3 gave [hub, end, sag, width]),
         "fx": {"halo": dots lit (the count, hood level), bind only: "lash": [a, b], "orb": p, "flash": p,
                "forge": [a, b]}}
r4: the ring bone carries the tilt only (rot [tilt_x, 0, tilt_z]); the wheel's spin is inside every slot's angle.

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
DEATH_DROP = 10.9                      # px: the crumple lands the robe on the ground (r6 slump: tithe_binder_poses.py death_t30 lands at -10.86)
HUB = (0.0, 27.0 + G.LIFT, 0.0)        # where the soul chains leave the ribs
LANTERN = (0.0, 23.0 + G.LIFT, -6.5)    # the lantern's core at rest (the bind's orb flies into it)
RING_PIVOT, RING_R, RING_TILT = G.RING_PIVOT, G.RING["r"], G.RING_TILT
LENGTH = {k: G.length_of(G.prims_of(k)) for k in range(1, 11)}        # flight length (scale 1.0), px
TUMBLE = {1: ("tumble", 40), 2: ("tumble", 60), 3: ("tumble", 50), 4: ("roll", 20), 5: ("roll", 15),
          6: ("tumble", 30), 7: ("tumble", 25), 8: ("flat", 30), 9: ("roll", 10), 10: ("tumble", 20)}
# r4 volley (the review: r3's weapons flew a straight hub -> target line on taut chains, a conveyor, and grew to full
# size in flight): the strike at t16, a release every 3 ticks, sling 1, flight 6, each hit held 5 ticks (a beat),
# reel 4; lateral arcs alternating sides (apex ~12 px off the bearing), mostly flat so the 52-deg camera keeps them;
# flight scale capped at 1.25 x the docked size. N = 10: last release t43, hit t50, embed to t55, docked t59 < 60.
VOLLEY_RELEASE, RELEASE_GAP = 13, 3   # r5: the strike at t13 (r4 t16): t4-8 sink + contract, t8-10 raise, hold t10-12
SLING, FLIGHT, EMBED, REEL, DOCK_OVERSHOOT = 1, 6, 5, 4, 2.0
FLIGHT_SCALE = 1.5                     # part scale in flight (r5; r4 1.25 read as a 2-3 px speck at 21 px/block)
CONTRACT, FLARE = 0.8, 1.3             # r5: every docked spoke's scale at the wind-up crouch / at the strike (springs back)
HIT_KICK_R, HIT_DROP = 2.7, 3.0        # r5 hit react, t1: every spoke kicks out 2.7 px (15% of r 18) and drops 3 px
K_KICK, C_KICK = 0.10, 0.30            # r5: their own soft spring (back in ~8 ticks; the radial spring took 3)
SWAY_PHASE = 137.5                     # r5: the idle sway's phase step per spoke (deg; r4 36 read as one unison sway)
MOVE_TILT, MOVE_LAG = 10.0, 3          # r5: the ring tilts back 10 at full speed (r6: the lag is the K_RMOVE spring;
#                                        MOVE_LAG is no longer used)
IMPACT_TICKS, IMPACT_R = 4, 6.0        # r5: the impact flash on contact (renderer quad, px)
LASH_SAG = 3.0                         # r5: the bind lash sags (px at its middle)
TRAIL = 3                              # r5: positions kept per flying weapon for its streak (renderer)
# r6: the wheel grows with N - pommel radius and docked spoke size per count (a tight hook at 1, the full wheel at 10)
RING_R_MIN, SPOKE_MIN = 15.0, 0.6      # at N = 1 (N = 10: RING_R and 1.0 - the model's own geometry)
HOVER_PULSE = 1.0                      # r6: px the hover dips at each release of the train (back in 2 ticks)
K_RMOVE, C_RMOVE = 0.15, 0.35          # r6: the move's ring tilt as a spring (lags ~3 ticks, no pop)
FLUTTER, FLUTTER_T = 9.0, 24.0         # r6: tatter flutter amplitude (deg) / period (ticks) (r5 4 / 30: read static)
SHACKLE_DRIVE = 0.8                    # r6: a shackle chain is kicked back by its arm's angular velocity
HALO_DIP = 2.0                         # r6: the count halo dips (px) for 3 ticks on a hit (renderer)


def ring_r_n(n):
    """The pommel radius for N bound weapons (r6)."""
    return RING_R_MIN + (RING_R - RING_R_MIN) * (max(1, min(10, n)) - 1) / 9.0


def spoke_n(n):
    """The docked spoke size (part scale on the model's N = 10 geometry) for N bound weapons (r6)."""
    return SPOKE_MIN + (1.0 - SPOKE_MIN) * (max(1, min(10, n)) - 1) / 9.0
LATERAL, ARC_UP = 16.0, (8.0, 6.0)     # px: the arc's control points off the bearing, and up
VICTIM_R = 5.0                         # px: the tip stops 2 px inside a victim of this radius (r3: at its centre)
HUB_R = 6.0                            # px: a soul chain leaves the ribs this far out toward its weapon
WEAVE = (12.0, 6.0, 10.0)              # r4 idle conductor weave: arm x, arm z, forearm x amplitudes (sheet 5, 3, 0)
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
                                        "hem_chain_3_b", "lantern_chain", "cape", "body_lean", "ring_move", "ring_tilt_x",
                                        "ring_tilt_z", "right_shackle_x", "right_shackle_z", "left_shackle_x",
                                        "left_shackle_z") + TATTERS}
    slots = {k: {"angle": a, "ang_v": 0.0, "radial": [0.0, 0.0], "roll": [0.0, 0.0], "kick": [0.0, 0.0],
                 "drop": [0.0, 0.0],
                 "mode": "DOCKED", "since": 0, "data": {}} for k, a in zip(range(1, n + 1), layout_angles(n))}
    return {"n": n, "seed": seed, "tick": 0, "bob": 0.0, "breath": 0.0, "look": 0.0, "fidget_in": 200 + int(200 * r),
            "fidget_t": -1, "ring_yaw": 0.0, "ring_yaw_v": 0.6, "springs": springs, "slots": slots,
            "arm_prev": {},
            "root": [[0.0, 0.0, 0.0], [0.0, 0.0, 0.0], [0.0, 0.0, 0.0]], "root_z": 0.0, "speed": 0.0,
            "lean_x": 0.0, "lean_v": 0.0, "lantern": 1.0, "order": {}, "falling": {}}


# ------------------------------------------------------------------ geometry -----------------------------------------
def ring_matrix(tilt_x, tilt_z):
    """r4: the ring bone carries the TILT only; the spin is added to every slot's angle (r3 put the yaw in the ring
    bone after its x tilt, so the tilt spun with the wheel)."""
    return rot_mat(RING_TILT + tilt_x, 0.0, tilt_z)


def slot_local(angle, radial=0.0, y=0.0):
    a = math.radians(angle)
    r = RING_R + radial
    return [-math.sin(a) * r, y, -math.cos(a) * r]


def slot_world(st, angle, radial=0.0):
    """A slot's pommel in model space from the CURRENT ring transform (state['ring_now']); angle includes the spin."""
    R, o = st["ring_now"]
    p = slot_local(angle, radial + st.get("n_off", 0.0))        # r6: + the N-dependent radius offset
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
          "cock": 0.0, "lantern": 1.0, "alpha": 1.0, "hidden": [], "lean_target": 0.0, "speed": 0.0,
          "spoke_scale": 1.0}
    # ---- the idle layer (always on, faded by the clips that own the body)
    idle_w = {"idle": 1.0, "fidget": 1.0, "move": 1.0, "volley": 1.0, "bind": 1.0, "hit": 1.0, "death": 0.0}[clip]
    if clip == "volley":
        idle_w = 1.0 - keys(t, [(0, 0.0), (4, 1.0), (st["f_end"], 1.0), (st["f_end"] + 6, 0.0)])
    if clip == "death":
        idle_w = 1.0 - keys(t, [(0, 0.0), (3, 1.0)])
    if clip == "move":
        idle_w = 1.0 - 0.8 * inp.get("speed", 0.0)                  # r6: the weave gives way to the sweep
    bob, br, look = st["bob"], st["breath"], st["look"]
    amp = 2.0 - 0.5 * st["speed"]
    put("hover", off=[0, amp * math.sin(bob) * idle_w, 0])
    put("body", rot=[2 * math.sin(br + 0.5) * idle_w, 0, 0])
    put("chest", rot=[3 * math.sin(br) * idle_w, 0, 0])
    put("mantle", off=[0, 0.5 * max(0.0, math.sin(br)) * idle_w, 0])
    put("head", rot=[0, 10 * math.sin(look) * idle_w, 0])
    # the conductor's weave (r4: x12 / z6 / forearm 10 - the sheet's 5 / 3 never showed from above)
    put("right_arm", rot=[WEAVE[0] * math.sin(bob + math.pi / 2) * idle_w, 0, WEAVE[1] * math.sin(bob) * idle_w])
    put("left_arm", rot=[WEAVE[0] * math.sin(bob + 3 * math.pi / 2) * idle_w, 0, WEAVE[1] * math.sin(bob) * idle_w])
    put("right_forearm", rot=[WEAVE[2] * math.sin(bob) * idle_w, 0, 0])
    put("left_forearm", rot=[-WEAVE[2] * math.sin(bob) * idle_w, 0, 0])
    curl = (10 + 10 * math.sin(2 * math.pi * st["tick"] / 40.0)) * idle_w
    put("right_hand", rot=[curl, 0, 0])
    put("left_hand", rot=[curl, 0, 0])
    tat = [FLUTTER * math.sin(2 * math.pi * st["tick"] / FLUTTER_T + i * math.pi / 2) for i in range(4)]
    ch["tatter_add"] = tat

    if clip == "fidget":       # "counting": the wheel spins one fast turn, its spokes flare, the weapons rattle
        # (r5: the left hand's lift is cut - unreadable from above; the spokes flare 15% with the spin instead)
        ch["ring_spin"] = keys(t, [(0, 0.6), (8, 12.0), (20, 0.6)])
        ch["spoke_scale"] = keys(t, [(0, 1.0), (8, 1.15), (20, 1.0)])
        ch["rattle"] = 15.0 if t == 8 else 0.0

    if clip == "move":
        v = inp.get("speed", 0.0)
        ch["speed"] = v
        # r6 (the review: "idle + ring tilt"): a deeper lean (abs -18), the arms swept well back, the cloth trailing
        ch["lean_target"] = -14.0 * v
        put("hover", off=[0, -2.0 * v, 0])
        put("head", rot=[14 * v, 0, 0])
        for side in ("right", "left"):
            put(side + "_arm", rot=[-55 * v, 0, 0])
            put(side + "_forearm", rot=[-30 * v, 0, 0])
            put(side + "_hand", rot=[-20 * v, 0, 0])
        ch["tatter"] = -45.0 * v + 6 * math.sin(2 * math.pi * t / 12.0) * v
        ch["cape"] = -35.0 * v
        ch["ring_move"] = MOVE_TILT * v          # r6: through a spring (K_RMOVE): lags ~3 ticks without r5's pop
        ch["ring_spin"] = 0.6 + 0.6 * v
        ch["trail"] = 10.0 * v

    if clip == "volley":
        # r5 timing (the review: ten dead ticks, then a 1-frame wind-up): t0-4 turn to the target, t4-8 SINK 2 px with
        # every spoke CONTRACTING to 0.8 and the ring cocking back, t8-10 the arm rises, t10-12 HOLD, t13 SNAP: the
        # strike, the ring dips -18 in one tick and the spokes FLARE to 1.3 (back to 1 by t16)
        fe = st["f_end"]
        last = st["last_release"]
        b = st["bearing"]
        sb, cb = math.sin(math.radians(b)), math.cos(math.radians(b))
        back = lambda d: [d * sb, 0.0, d * cb]                      # noqa: E731  (away from the target, px)
        # r6: the strike pose is HELD through the release train (to `last`), the caster pulsing 1 px per release,
        # then recovers over 12 ticks (r5 popped back to rest by t16 and idled while the weapons dripped out)
        hy = keys(t, [(0, 0), (4, 0), (8, -2), (10, 3), (12, 3), (13, -1), (last, -1), (fe, 0)])
        rel = [VOLLEY_RELEASE + RELEASE_GAP * o for o in st["order"].values()]
        since = [t - r for r in rel if 0 <= t - r < 2]
        if since:
            hy -= HOVER_PULSE * (1.0 - min(since) / 2.0)
        hb = keys(t, [(0, 0), (4, 0), (8, 1), (10, 3), (12, 3), (13, -4), (last, -4), (fe, 0)])
        put("hover", off=[back(hb)[0], hy, back(hb)[2]])
        by = keys(t, [(0, 0), (4, b), (8, b + 8), (10, b + 25), (12, b + 25), (13, b - 15), (last, b - 15), (fe, b)])
        put("body", rot=[keys(t, [(0, 0), (4, 0), (8, -6), (10, 14), (12, 14), (13, -11), (last, -11), (fe, 0)]), by, 0])
        put("head", rot=[keys(t, [(0, 0), (8, 0), (10, -20), (12, -20), (13, -8), (last, -8), (fe + 2, 0)]), b - by, 0])
        # the whip: through to delta 40 at t13, then down to 20 (abs 40) for the release train, so the whip circles
        # run under the ring (r5 clearance: at delta 40 the circling hand came within 0.8 px of the spokes)
        put("right_arm", rot=[keys(t, [(0, 0), (8, 0), (10, 180), (12, 180), (13, 40), (15, 20),
                                       (max(last + 2, 15), 20), (fe, 0)]), 0,
                              # the outward z only once the arm is up (r5: swung out on the way up, the hand
                              # passed the spokes at 0.9 px)
                              keys(t, [(0, 0), (9, 0), (10, 42), (12, 42), (13, 0)])])
        fx = keys(t, [(0, 0), (8, -10), (10, 50), (12, 50), (13, -25), (fe, 0)])
        fz = 0.0
        if n >= 3 and 13 < t <= last + 1:       # one crack of the whip per release
            ph = 2 * math.pi * (t - VOLLEY_RELEASE) / float(RELEASE_GAP)
            fx, fz = 20 * math.sin(ph), 15 * math.sin(ph)
        put("right_forearm", rot=[fx, 0, fz])
        put("right_hand", rot=[keys(t, [(0, 0), (8, 0), (10, 20), (12, 20), (13, 0)]), 0, 0])
        # the aim hand: abs x 67 (under the dipped front spokes: r5 clearance 2.3 px at abs 75), facing the target
        put("left_arm", rot=[keys(t, [(0, 0), (4, 30), (10, 47), (max(last + 2, 15), 47), (fe, 0)]), 0, 0])
        put("left_forearm", rot=[keys(t, [(0, 0), (10, -30), (max(last + 2, 15), -30), (fe, 0)]), 0, 0])
        put("mantle", off=[0, keys(t, [(0, 0), (8, 0), (10, 1), (12, 1), (13, 0)]), 0])
        ch["tatter"] = keys(t, [(0, 0), (8, 4), (10, -8), (12, -8), (13, -15), (last, -15), (fe, 0)])
        ch["cape"] = keys(t, [(0, 0), (8, 2), (10, -6), (12, -6), (13, -12), (last, -12), (fe, 0)])
        tl = keys(t, [(0, 0), (4, 0), (8, 12), (12, 12), (13, -18), (last, -18), (fe, 0)])
        ch["ring_tilt"] = [tl * cb, -tl * sb]                        # dips toward the target's bearing
        ro = keys(t, [(0, 0), (4, 0), (8, 4), (12, 4), (13, -5), (last, -5), (fe, 0)])
        ch["ring_off"] = [back(ro)[0], keys(t, [(0, 0), (4, 0), (8, 1), (12, 1), (13, -2), (last, -2), (fe, 0)]),
                          back(ro)[2]]
        # the spin peaks at the snap and decays across the release train (each release takes some of it)
        ch["ring_spin"] = keys(t, [(0, 0.6), (10, 6.0), (13, 6.0), (last, 1.5), (fe, 0.6)])
        ch["cock"] = keys(t, [(0, 0), (8, 25), (12, 25), (13, 10), (last, 10), (fe, 0)])
        ch["spoke_scale"] = keys(t, [(0, 1.0), (4, 1.0), (8, CONTRACT), (12, CONTRACT), (13, FLARE), (16, 1.0)])
        released = len([k for k, o in st["order"].items() if VOLLEY_RELEASE + RELEASE_GAP * o <= t])
        ch["lantern"] = keys(t, [(0, 1.0), (10, 1.6), (last, 1.6), (fe, 1.0)])
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
        # r6 (the review: a ~4 px kick, the hood's peak at t6): keyed, the peak at t1-2, settled by t11 - the body
        # thrown back 18 and pushed 2 px back, the hood with it, the ring's front kicked up 18 (no spring lag)
        put("hover", off=[0, keys(t, [(0, 0), (1, -3), (3, -3), (8, 0)]), keys(t, [(0, 0), (1, 2), (3, 2), (9, 0)])])
        put("body", rot=[keys(t, [(0, 0), (1, 18), (2, 18), (5, 4), (8, -2), (11, 0)]), 0, 0])
        put("head", rot=[keys(t, [(0, 0), (1, 15), (2, 15), (8, 0)]), 0, 0])
        ch["ring_tilt"] = [keys(t, [(0, 0), (1, 18), (3, 14), (7, -3), (11, 0)]), 0.0]
        ch["halo_dip"] = HALO_DIP if 1 <= t <= 3 else 0.0
        ch["rattle"] = 15.0 if t == 0 else 0.0
        ch["pitch_kick"] = 30.0 if t == 0 else 0.0
        ch["tatter_kick"] = -6.0 if t == 0 else 0.0
        ch["hit_kick"] = t == 1                 # r5: every spoke kicks out and drops (slot springs, ~8 ticks back)
        if t in (1, 2):
            ch["lantern"] = 0.0                 # the lantern gutters 2 ticks

    if clip == "death":        # sheet 5.6; r4: a landing squash held t16-19 before the crumple (t19-33)
        fall = 0.0
        if 6 <= t <= 16:
            u = (t - 6) / 10.0
            fall = -DEATH_DROP * u * u                                  # gravity ease-in
        elif t > 16:
            # r5: after the crumple lands (t33), a settle bounce (-1, +0.4, 0) - no static hold
            fall = -DEATH_DROP + keys(t, [(16, -1.5), (19, -1.5), (21, 0.0), (33, 0.0), (35, -1.0), (37, 0.4), (40, 0.0)])
        put("hover", off=[0, fall, 0])
        # r5: the slumped hood rolls and turns (it read as an upright red crate)
        put("head", rot=[keys(t, [(0, 0), (3, 35), (6, 35), (16, 35), (19, 28), (33, -21)]),
                         0, keys(t, [(0, 0), (19, 0), (33, 8), (36, 12), (40, 8)])])
        # r6: a sideways slump (body roll 18, less fold: the r5 -26 / -40 fold read as a red crate with the hood on
        # top, and put the chest into the waist)
        put("body", rot=[keys(t, [(0, 0), (3, 8), (6, 8), (16, 8), (17, -8), (19, -8), (33, -20)]), 0,
                         keys(t, [(0, 0), (19, 0), (33, 18)])])
        put("chest", rot=[keys(t, [(0, 0), (3, 6), (6, 6), (16, 6), (17, -4), (19, -4), (33, -18)]), 0, 0])
        # the arms fall limp and IN, along the body (r3: the left hand poked out from under the hood at t30)
        put("right_arm", rot=[keys(t, [(0, 0), (6, 0), (19, 0), (33, 30)]), 0,
                              keys(t, [(0, 0), (3, -58), (6, -58), (16, -58), (19, -40), (33, 5)])])
        put("left_arm", rot=[keys(t, [(0, 0), (6, 0), (19, 0), (33, 30)]), 0,
                             keys(t, [(0, 0), (3, 58), (6, 58), (16, 58), (19, 40), (33, 5)])])
        for side in ("right", "left"):
            put(side + "_forearm", rot=[keys(t, [(0, 0), (3, -25), (19, -25), (33, -35)]), 0, 0])
        fold = keys(t, [(0, 0.0), (19, 0.0), (33, 1.0)])
        splay = keys(t, [(0, 0.0), (16, 0.0), (17, 1.0), (19, 1.0), (21, 0.0)])    # the squash pushes the hem out
        put("tatter_f", rot=[80 * fold + 15 * splay, 0, 0])
        put("tatter_b", rot=[-80 * fold - 15 * splay, 0, 0])
        put("tatter_l", rot=[0, 0, 80 * fold + 15 * splay])
        put("tatter_r", rot=[0, 0, -80 * fold - 15 * splay])
        ch["tatter_hold"] = t >= 16
        ch["lantern"] = keys(t, [(0, 1.0), (1, 2.5), (6, 2.5), (8, 0.3), (30, 0.3), (40, 0.0)])
        ch["alpha"] = keys(t, [(0, 1.0), (32, 1.0), (50, 0.0)])     # r5: the dissolve starts at t32 (r4 t35)
        ch["ring_spin"] = 0.6
    return B, ch


# ------------------------------------------------------------------ the step ---------------------------------------
def start_clip(st, clip, inp):
    """Clip-entry bookkeeping (t == 0): the volley's release order, the bind's new slot, the death's fall list."""
    n = st["n"]
    if clip == "volley":
        tgt = inp.get("target", [0.0, 20.0, -80.0])
        bearing = math.degrees(math.atan2(-tgt[0], -tgt[2]))           # the slot-yaw convention
        diff = {k: ((s["angle"] + st["ring_yaw"] + 10 * 3.5 - bearing + 180) % 360) - 180   # ~ the spin at t13
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
        st["bearing"] = bearing
    if clip == "bind":
        st["n_prev"] = n
        k = n + 1
        st["slots"][k] = {"angle": G.BASE_YAW + 360.0 / k * (k - 1), "ang_v": 0.0, "radial": [0.0, 0.0],
                          "roll": [0.0, 0.0], "kick": [0.0, 0.0], "drop": [0.0, 0.0], "mode": "FORGING",
                          "since": 0, "data": {}}
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
    S.setdefault("ring_move", [0.0, 0.0])
    S["ring_move"] = spring(S["ring_move"], ch.get("ring_move", 0.0), K_RMOVE, C_RMOVE)
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
        prev = st.setdefault("arm_prev", {}).get(side, arm[0] + fore[0])
        st["arm_prev"][side] = arm[0] + fore[0]
        swing = -SHACKLE_DRIVE * ((arm[0] + fore[0]) - prev)       # r6: kicked back by the arm's own swing
        S[side + "_shackle_x"] = spring(S[side + "_shackle_x"], tx, K_CHAIN, C_CHAIN, drive_x + swing,
                                        *CLAMP["shackle"])
        S[side + "_shackle_z"] = spring(S[side + "_shackle_z"], tz, K_CHAIN, C_CHAIN, drive_z, *CLAMP["shackle"])
        B.setdefault(side + "_shackle_chain", {"rot": [0.0, 0.0, 0.0], "off": [0.0, 0.0, 0.0]})["rot"] = [
            S[side + "_shackle_x"][0], 0.0, S[side + "_shackle_z"][0]]
    # ---- the wheel
    st["ring_yaw_v"] = ch["ring_spin"]
    st["ring_yaw"] = (st["ring_yaw"] + st["ring_yaw_v"]) % 360.0
    # the tilt: the clip's keyed tilt + a spring perturbation (kicked by hits, driven by the root's acceleration)
    if ch.get("tilt_kick"):
        S["ring_tilt_x"][1] += ch["tilt_kick"] * 0.6
    S["ring_tilt_x"] = spring(S["ring_tilt_x"], 0.0, K_TILT, C_TILT, -0.2 * drive_x)   # r6: 0.2 (r5 0.5: a pop)
    S["ring_tilt_z"] = spring(S["ring_tilt_z"], 0.0, K_TILT, C_TILT, -0.2 * drive_z)
    tilt = [ch["ring_tilt"][0] + S["ring_tilt_x"][0] + S["ring_move"][0], ch["ring_tilt"][1] + S["ring_tilt_z"][0]]
    ch["ring_off"] = [ch["ring_off"][0], ch["ring_off"][1], ch["ring_off"][2] + 0.2 * S["ring_move"][0]]
    ring_off = [hov[i] + ch["ring_off"][i] for i in range(3)]
    B["ring"] = {"rot": [tilt[0], 0.0, tilt[1]], "off": list(ch["ring_off"])}
    st["ring_now"] = (ring_matrix(tilt[0], tilt[1]), [RING_PIVOT[i] + ring_off[i] for i in range(3)])
    st["hub_now"] = HUB_now(B)
    # ---- r6: the wheel's size for the current count (the bind eases old -> new over its forge, t18-28)
    n_from = st.get("n_prev", n) if clip == "bind" else n
    u_n = smooth((t - 18) / 10.0) if clip == "bind" else 1.0
    st["n_off"] = (ring_r_n(n_from) + (ring_r_n(n) - ring_r_n(n_from)) * u_n) - RING_R
    st["n_scale"] = spoke_n(n_from) + (spoke_n(n) - spoke_n(n_from)) * u_n
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
        s.setdefault("drop", [0.0, 0.0])
        s.setdefault("kick_r", [0.0, 0.0])
        if ch.get("hit_kick"):
            s["kick_r"] = [HIT_KICK_R, 0.0]
            s["drop"] = [-HIT_DROP, 0.0]
        s["radial"] = spring(s["radial"], 0.0, K_RADIAL, C_RADIAL)
        s["kick_r"] = spring(s["kick_r"], 0.0, K_KICK, C_KICK)
        s["drop"] = spring(s["drop"], 0.0, K_KICK, C_KICK)
        s["roll"] = spring(s["roll"], 0.0, K_ROLL, C_ROLL)
        s["kick"] = spring(s["kick"], 0.0, K_ROLL, C_ROLL)
        bob_k = math.sin(2 * math.pi * st["tick"] / 40.0 + math.radians(SWAY_PHASE * k))
        pitch = ch["cock"] + s["kick"][0] + 3 * bob_k
        roll = s["roll"][0] + 8 * bob_k
        radial = s["radial"][0] + s["kick_r"][0] + bob_k
        mode = s["mode"]
        if clip == "volley" and k in st["order"]:
            mode = weapon_flight(st, s, k, t, flying, ribbons)
        if clip == "death" and t >= 6 + 2 * (k - 1):
            mode = weapon_fall(st, s, k, t, flying, hidden)
        sc = ch["spoke_scale"] * st["n_scale"]
        if mode == "FORGING":
            if t < 18:
                hidden.append("weapon_%d" % k)
            else:
                g = smooth((t - 18) / 6.0)
                s["data"]["forge"] = (0.4 + 0.6 * g) * st["n_scale"]     # sheet 5.4: outline 0.4 -> 0.7 docked
                if t >= 24:
                    s["mode"] = "DOCKED"
                    mode = "DOCKED"
        if mode in ("DOCKED", "FORGING") and ("weapon_%d" % k) not in hidden:
            slots_out[k] = {"angle": round(s["angle"] + st["ring_yaw"], 3), "radial": round(radial + st["n_off"], 3),
                            "y": round(s["drop"][0], 3), "pitch": round(pitch, 3), "roll": round(roll, 3),
                            "scale": round(s["data"].get("forge", 1.0) if mode == "FORGING" else sc, 3)}
            if clip != "death" or t < 6:
                end = slot_world(st, s["angle"] + st["ring_yaw"], radial)
                end[1] += s["drop"][0]
                hub = hub_toward(st, end)
                ribbons.append([hub, sag_ctrl(hub, end, 2.0), end, 1.2])
    st["tick"] += 1
    pose = {"bones": {k: {"rot": [round(x, 3) for x in e["rot"]], "off": [round(x, 3) for x in e["off"]]}
                      for k, e in B.items()},
            "slots": slots_out, "flying": flying, "hidden": sorted(set(hidden)), "alpha": round(ch["alpha"], 3),
            "lantern": round(ch["lantern"], 3),
            "ribbons": [[[round(x, 2) for x in r[0]], [round(x, 2) for x in r[1]], [round(x, 2) for x in r[2]], r[3]]
                        for r in ribbons],
            "fx": bind_fx(st, clip, t, inp, B) if clip == "bind" else {}}
    if st.get("impacts_now"):
        pose["fx"]["impacts"] = st.pop("impacts_now")
    if ch.get("halo_dip"):
        pose["fx"]["halo_dip"] = ch["halo_dip"]
    pose["fx"]["halo"] = len(slots_out) + len([k for k, f in flying.items() if f["state"] != "SHATTER"]) if (
        clip != "death" or t < 6) else max(0, n - max(0, (t - 6) // 2 + 1))
    if ch["lantern"] < 0.5:
        pose["hidden"] = sorted(set(pose["hidden"]) | {"lantern_core"})
    return st, pose


def hub_toward(st, end):
    """r4: where a soul chain leaves the ribs - HUB_R px from the hub toward its weapon (horizontal), not one point."""
    h = st["hub_now"]
    dx, dz = end[0] - h[0], end[2] - h[2]
    d = math.sqrt(dx * dx + dz * dz) or 1.0
    return [h[0] + HUB_R * dx / d, h[1], h[2] + HUB_R * dz / d]


def sag_ctrl(a, b, sag):
    """The quadratic ribbon's control point for a chain sagging `sag` px between a and b."""
    return [(a[0] + b[0]) / 2, (a[1] + b[1]) / 2 - 2 * sag, (a[2] + b[2]) / 2]


def bind_fx(st, clip, t, inp, B):
    """r4 sheet 5.4: the lash from the lantern to the corpse (t0-3), the soul orb lifted (t4-5) and dragged along it
    to the lantern (t6-15), the flash (t16-17), the forge line lantern -> the new slot (t18-23)."""
    h = B.get("hover", {"off": [0, 0, 0]})["off"]
    lan = [LANTERN[i] + h[i] for i in range(3)]
    c = list(inp.get("target", [-40.0, 4.0, -56.0]))
    top = [c[0], c[1] + 16.0, c[2]]
    fx = {}
    if t <= 16:
        u = smooth((t + 1) / 3.0)
        end = [lan[i] + (c[i] - lan[i]) * u for i in range(3)]
        # r5: the lash sags LASH_SAG px, and whips (double sag) as the orb arrives (t14-16)
        sag = LASH_SAG * (2.0 if 14 <= t <= 16 else 1.0)
        fx["lash"] = [lan, sag_ctrl(lan, end, sag / 2.0), end]
    if 4 <= t <= 5:
        fx["orb"] = [c[0], c[1] + 16.0 * smooth((t - 3) / 2.0), c[2]]
    elif 6 <= t <= 16:
        # dragged along the lash, then a 2-tick overshoot past the lantern (t15 at 1.15, t16 back at 1.0)
        u = smooth((t - 5) / 9.0) if t <= 14 else (1.15 if t == 15 else 1.0)
        fx["orb"] = [top[i] + (lan[i] - top[i]) * u for i in range(3)]
    if 16 <= t <= 17:
        fx["flash"] = lan
    if 18 <= t <= 23:
        k = st["n"]
        s = st["slots"][k]
        fx["forge"] = [lan, slot_world(st, s["angle"] + st["ring_yaw"], 0.0)]
    return fx


def HUB_now(B):
    h = B.get("hover", {"off": [0, 0, 0]})["off"]
    return [HUB[i] + h[i] for i in range(3)]


def weapon_flight(st, s, k, t, flying, ribbons):
    """One weapon of the volley (r4): DOCKED -> SLUNG (1) -> FLYING (6, a lateral arc on alternating sides) -> EMBED
    (5, held: each hit a beat) -> REEL (4) -> DOCKED (+2 px radial overshoot)."""
    o = st["order"][k]
    tr = VOLLEY_RELEASE + RELEASE_GAP * o
    tau = t - tr
    if tau < 0:
        return "DOCKED"
    D = s["data"]
    tgt = st["target"]
    lat_sign = 1.0 if o % 2 == 0 else -1.0
    if tau == 0:
        ang = s["angle"] + st["ring_yaw"]
        p0 = slot_world(st, ang, s["radial"][0])
        a = math.radians(ang)
        radial = [-math.sin(a), 0.0, -math.cos(a)]
        tangent = [-math.cos(a), 0.0, math.sin(a)]
        hx, hz = tgt[0] - st["hub_now"][0], tgt[2] - st["hub_now"][2]
        hd = math.sqrt(hx * hx + hz * hz) or 1.0
        dh = [hx / hd, 0.0, hz / hd]                                  # the bearing, horizontal
        lat = [-dh[2], 0.0, dh[0]]                                    # its left-hand perpendicular
        sling_end = [p0[i] + tangent[i] * 2 + radial[i] * 2 for i in range(3)]
        sling_end[1] += 1.0
        size = LENGTH[k] * G.DOCK_SCALE[k] * FLIGHT_SCALE
        surf = [tgt[0] - dh[0] * VICTIM_R, tgt[1], tgt[2] - dh[2] * VICTIM_R]
        # the last leg comes in from the arc's side: its direction sets where the pommel stops (tip 2 px in)
        P2 = [sling_end[i] + (surf[i] - sling_end[i]) * 0.75 + lat[i] * lat_sign * LATERAL for i in range(3)]
        P2[1] += ARC_UP[1]
        T = [surf[i] - P2[i] for i in range(3)]
        nT = math.sqrt(sum(x * x for x in T))
        T = [x / nT for x in T]
        P3 = [surf[i] + T[i] * 2.0 - T[i] * size for i in range(3)]
        P1 = [sling_end[i] + (P3[i] - sling_end[i]) * 0.33 + lat[i] * lat_sign * LATERAL for i in range(3)]
        P1[1] += ARC_UP[0]
        D.update(p0=p0, radial=radial, tangent=tangent, sling_end=sling_end, P=[sling_end, P1, P2, P3], surf=surf,
                 hist=[],
                 q0=q_from_mat(slot_rot_world(st, ang)))
    p0, Pc = D["p0"], D["P"]
    s0 = st.get("n_scale", 1.0)
    scale = s0 + (FLIGHT_SCALE - s0) * smooth((tau + 1) / float(SLING + 1))
    kind, rate = TUMBLE[k]
    hub = hub_toward(st, D.get("p_end", Pc[3]))
    if tau < SLING:
        u = smooth((tau + 1) / float(SLING))
        pos = [p0[i] + (Pc[0][i] - p0[i]) * u for i in range(3)]
        q = D["q0"]
        state = "SLUNG"
        ctrl = sag_ctrl(hub, pos, 2.0)
    elif tau < SLING + FLIGHT:
        u = (tau - SLING + 1) / float(FLIGHT)
        pos = bezier(*Pc, u)
        T = bezier_d(*Pc, u)
        base = q_from_mat(frame_along(T))
        ang = rate * (tau - SLING + 1)
        if tau >= SLING + FLIGHT - 2:            # the last 2 ticks: turn point-first
            ang0 = rate * (FLIGHT - 2)
            ang = ang0 + ((math.ceil(ang0 / 360.0) * 360.0) - ang0) * smooth((tau - (SLING + FLIGHT - 3)) / 2.0)
        if kind == "roll":
            q = q_mul(base, q_from_axis((0, 0, 1), ang))
        elif kind == "flat":
            q = q_mul(q_from_axis((0, 1, 0), ang), base)
        else:
            q = q_mul(base, q_from_axis((1, 0, 0), ang))
        state = "FLYING"
        if u >= 1.0:
            q = base
        D["q_end"], D["p_end"] = q, pos
        # the chain pays out along the flown path: the quadratic through the path's halfway point
        mid = bezier(*Pc, u / 2.0)
        ctrl = [2 * mid[i] - (hub[i] + pos[i]) / 2 for i in range(3)]
    elif tau < SLING + FLIGHT + EMBED:
        pos, q = D["p_end"], D["q_end"]
        state = "EMBED"
        ctrl = sag_ctrl(hub, pos, 0.0)                                # taut: the hit
        age = tau - (SLING + FLIGHT)
        if age < IMPACT_TICKS:                                        # r5: the impact flash on contact
            tip = [D["surf"][i] for i in range(3)]
            st.setdefault("impacts_now", []).append([[round(x, 2) for x in tip], age])
    elif tau < SLING + FLIGHT + EMBED + REEL:
        u = (tau - (SLING + FLIGHT + EMBED) + 1) / float(REEL)
        e = u * u                                                     # slow start, fast arrival
        ang = s["angle"] + st["ring_yaw"]
        dock = slot_world(st, ang, 0.0)
        mid = [(D["p_end"][i] + dock[i]) / 2 for i in range(3)]
        mid[1] += 6.0
        pos = [(1 - e) ** 2 * D["p_end"][i] + 2 * (1 - e) * e * mid[i] + e * e * dock[i] for i in range(3)]
        qd = q_from_mat(slot_rot_world(st, ang))
        q = q_mul(q_slerp(D["q_end"], qd, e), q_from_axis(D["tangent"], 360.0 * e))
        scale = FLIGHT_SCALE + (s0 - FLIGHT_SCALE) * e
        state = "REEL"
        ctrl = sag_ctrl(hub, pos, 3.0)
        if u >= 1.0:
            s["radial"] = [DOCK_OVERSHOOT, 0.0]
            s["mode"] = "DOCKED"
            D.pop("p_end", None)
            return "DOCKED"
    else:
        return "DOCKED"
    R = q_to_mat(q)
    hist = D.setdefault("hist", [])
    flying[k] = {"state": state, "pos": [round(x, 3) for x in pos], "rot": euler(R), "scale": round(scale, 3),
                 "trail": [list(h) for h in hist] if state in ("FLYING", "REEL") else []}   # r5: the streak behind it
    hist.append([round(x, 2) for x in pos])
    del hist[:-TRAIL]
    ribbons.append([hub, ctrl, [round(x, 2) for x in pos], 1.6 * (1.3 if tau == SLING + FLIGHT else 1.0)])
    return state


def weapon_fall(st, s, k, t, flying, hidden):
    """Death: the weapon lets go (one every 2 ticks in ring order), flung outward, falls, shatters on the ground."""
    F = st["falling"]
    t0 = 6 + 2 * (k - 1)
    if k not in F:
        p = slot_world(st, s["angle"] + st["ring_yaw"], 0.0)
        a = math.radians(s["angle"] + st["ring_yaw"])
        vel = [-math.sin(a) * 1.2 - math.cos(a) * 0.6, 0.0, -math.cos(a) * 1.2 + math.sin(a) * 0.6]
        F[k] = {"p": p, "v": vel, "q": q_from_mat(slot_rot_world(st, s["angle"] + st["ring_yaw"])), "ground": None}
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
    # r4: the victim 5 blocks out at a 30-deg bearing to the front-right, so the turn to the target shows
    "volley_N5": dict(clip="volley", n=5, ticks=60, target=[-40.0, 20.0, -69.28]),
    "volley_N10": dict(clip="volley", n=10, ticks=60, target=[-40.0, 20.0, -69.28]),
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
