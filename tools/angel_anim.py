#!/usr/bin/env python3
"""The Sunforged angels' clips as code (the Angel Caller's three summons: mender / archer / sword): THE spec the Java
rig ports 1:1 and checks against design/fixtures/angel_clips.json. Design sheet .scratch/angels/design-sheet.md
section 3 (clips and timings), the studio's motion lessons (below), body b2 (tools/gen_angels.py; b3 / b4 share the
bones and every rest rotation used here).

    st = State(kind, seed)            # once per unit ("mender" | "archer" | "sword"); arrive=True plays the summon
    inp = Inputs(speed=..., ...)      # every game tick (20 tps)
    step(st, inp)                     # advances the state one tick (pure maths, no Minecraft, no numpy)
    pose(st)                          # {bone: [rx, ry, rz] DSL degrees ADDED to the rest pose,
                                      #  "_move": {bone: [dx, dy, dz] DSL px}, "_scale": {bone: [sx, sy, sz]},
                                      #  "_fx": {"white": 0..1, "alpha": 0..1, "halo_alpha": 0..1, "glow": x}}
    python tools/angel_anim.py --fixture design/fixtures/angel_clips.json   # every 2nd tick of every clip per kind
    python tools/angel_anim.py --stats [--model-key b2]                     # per clip: joint step, tip arc, bob

Inputs (what the client knows, game-side): speed (blocks/tick, horizontal), yaw_rate (deg/tick, the entity's yRot
change: + turns RIGHT, Minecraft's convention), attack_seq (a cue counter the server bumps ON the contact tick: the
sword's blow, the archer's release, the mender's heal), attack_in (ticks until the next contact as the client
predicts it from the synced cooldown / windup, -1 = none due), hurt_time (vanilla hurtTime, 10 on a hit counting
down), dying (vanilla deathTime > 0, or the synced "fading" flag 20 ticks before lifeTicks ends).

Contact on the server's timing: a clip starts when 0 <= attack_in <= EVENT[kind] (so its event key lands on the
predicted contact tick); the cue (attack_seq bumped) is the contact itself - a clip that is early or late is retimed
onto it there (an unpredicted cue jumps straight to the contact pose: the first blow of a fight when no cooldown is
synced; see the report's known defects). The game feeds attack_in from: Sword - the melee cooldown (the blow lands
on vanilla's swing tick); Archer - the 4-tick windup plus the shot cooldown; Mender - SupportBrain's >= 40-tick heal.

Motion rules (mandatory studio lessons): the wing phase is STATE advanced by 1 / beatTicks every tick (never age /
period), beatTicks eased between states; the hover bob rides the wing phase (rises on the downstroke, sinks on the
upstroke, 3 ticks late); the downstroke is 48% of the cycle, slow and fully spread; on the upstroke the wrist folds
first, the elbow after it, the tips trail on a spring; banking rolls the body into the turn (cap 35, critically
damped) while the head stays level and leads; every attack holds its anticipation 2-3 ticks, lands on the cue, and
follows through with cloth / feather lag on spring-dampers; idle is never still (breath, head glances) and every
slow beat in a state counts WING BEATS (one cadence per state, no unrelated timers); per-unit tempo +-8% from the
seed (one LCG in the state).

Conventions (design/models/README.md): DSL degrees, rotation order x then y then z (M = Rz Ry Rx), +x swings a
hanging part forward / tips a standing part's top BACK, +y turns the front toward -x (the unit's right), +z rolls
the +x side (the unit's left) up. A wing's elevation E is rz = side * E (side +1 left, -1 right); its forward sweep
is ry = side * S; a fold back is ry = -side * F. Springs: v += k (target - x) - c v; x += v, once per tick.
"""
import argparse
import json
import math
import os

# ------------------------------------------------------------------ constants (the Java port copies these) ---------
KINDS = ("mender", "archer", "sword")
HOVER_T = {"mender": 30.0, "archer": 26.0, "sword": 34.0}    # sheet 3.2: hover beat (ticks)
FLY_T = {"mender": 22.0, "archer": 20.0, "sword": 24.0}      # sheet 3.3: fly beat
BOB = {"mender": 1.2, "archer": 1.5, "sword": 2.0}           # px, hover bob (half the travel)
FLY_PITCH = {"mender": -35.0, "archer": -35.0, "sword": -42.0}
EVENT = {"mender": 10, "archer": 16, "sword": 10}           # the attack clip's contact tick (archer: m2 hold to 16)
STROKE = {"mender": (10, 5), "archer": (16, 5), "sword": (9, 4)}   # the attack's own downstroke (start tick, ticks)
ATTACK_LEN = {"mender": 24, "archer": 33, "sword": 25}
HIT_LEN, ARRIVE_LEN, DEATH_LEN = 10, 30, 20
SUPINATE = -15.0          # m2: the upstroke (and the death's fold) turns the outer wing about its span (the fore's
#                           rx) so the flights never show the RTS / quarter camera a pure down face (Minecraft lights a
#                           down face 0.4: brown). b4's chord already rises 20 to the trailing edge; a sweep of the lit
#                           share of the wing faces over hover / death / arrive picked -15 (trailing edge down)
# sheet 3.1: arm elevation at the top / bottom. The sheet's +50 top is cut per kind by the b2 halo: a higher top
# lifts the inner wing (the elbow, 6 px out) through the ring's side (archer / sword: OD 10.5 at y +5.8 over the
# head pivot) or the Mender's low disc (9 px, its rim beside the crown)
UP = {"mender": 30.0, "archer": 30.0, "sword": 30.0}
DN = -35.0
DOWN0, DOWN1, BOTTOM1 = 0.08, 0.56, 0.60                     # phase marks: top hold | downstroke | bottom | up
CRUISE = 0.35                                                # blocks/tick: full flight pose
TEMPO_SPREAD = 0.08
BEAT_EASE = 0.12                                             # beatTicks += (target - beatTicks) * BEAT_EASE
# upstroke fold back (deg): the sheet's hand 55 + elbow 30 is cut per kind by the planform's own sweep (the b2 hand
# already points 25 / 45 / 51 deg back): a hand folded past straight back crosses the spine behind the shoulders
FOLD_HAND = {"mender": 40.0, "archer": 40.0, "sword": 40.0}
FOLD_FORE = {"mender": 20.0, "archer": 20.0, "sword": 20.0}
FOLD_TIPS = 4.0          # m3: was 15 - the tips folded 15 past the hand opened a 20-25 deg gap at the wrist (blind5's
#                          "comb"); 4 keeps neighbouring flights 10-15 apart (the b4 fan itself is 11-14)
LAG_FORE, LAG_HAND, LAG_BOB, LAG_PITCH = 2.0, 3.0, 3.0, 3.0  # ticks
GLIDE_TICKS, GLIDE_AFTER = 20, 2                             # fly: a 20-tick glide after every 2 beats
GLIDE_BOB, GLIDE_DIH = 1.2, 4.0          # m3: the glide breathes (px half-travel, deg dihedral) once per glide: no
#                                          pixel-identical hold (blind5: bank ends, fly t44-t60)
STROKE_LAG = {"mender": (1.0, 2.0), "archer": (0.5, 1.0), "sword": (1.0, 2.0)}   # attack downstroke: fore / hand lag
#                                          (ticks); m3: the archer's wings follow the release through at once
PITCH_CAP = 45.0         # m3: no flight turns more than this about its own long axis (body frame; blind5: the upstroke
#                          pitched the primaries ~90 into a vertical comb that flickered solid / striped at RTS)
# b4 flight cubes (tools/gen_angels.py, left side; the right mirrors to (rx, -ry, -rz)): their rotations in the bone
FEATHERS = {
    "mender": {"hand": ((-20.7, 14.5, 3.6),),
               "tips": ((-69.1, 68.5, -58.7), (-37.8, 56.1, -23.8), (-27.4, 42.0, -10.1), (-22.9, 28.6, -2.5))},
    "archer": {"hand": ((-20.3, 9.4, 6.5), (-20.5, 11.8, 5.6)),
               "tips": ((-37.8, 56.1, -22.8), (-27.4, 42.0, -9.2), (-22.1, 24.7, 0.4))},
    "sword": {"hand": ((-20.1, 6.0, 9.8), (-20.1, 4.4, 10.4)),
              "tips": ((-33.1, 51.2, -14.9), (-25.5, 37.5, -4.2), (-22.1, 24.6, 2.4), (-20.6, 13.1, 7.1))},
}
ROLL_PER_YAW, ROLL_CAP, ROLL_A = 6.0, 35.0, 0.45             # bank: deg roll per deg/tick, cap, 2-pole filter
BREATH_BEATS = 4                                             # the breath: one per 4 wing beats
GLANCE_MIN, GLANCE_MAX = 3, 5                                # beats between head glances (seeded)
GLANCE_YAW = 18.0
# springs (k, c): cloth, hem (one more link), feather tips, halo, legs, lantern pendulum, hit kick
K_CLOTH, C_CLOTH = 0.15, 0.30          # m2: lags ~2 ticks with an overshoot
K_HEM, C_HEM = 0.15, 0.30
K_TIPS, C_TIPS = 0.25, 0.32            # m2: the tips trail the hand 1-2 ticks and settle
K_HALO, C_HALO = 0.45, 0.50            # m2: 1-2 ticks behind the head, a small settle
K_LANT, C_LANT = 0.10, 0.10            # m2: the lantern's own swing on the chain (a second pendulum link)
K_LEG, C_LEG = 0.14, 0.40
K_PEND, C_PEND = (2.0 * math.pi / 24.0) ** 2, 0.06            # sheet: lantern period 24 ticks
# rest rotations (tools/gen_angels.py b2 == b4) of the bones posed in a parent frame, and the offsets that move
REST = {
    "mender": {"left_arm": (-18.0, -12.0, 42.0), "left_fore": (96.0, 0.0, 0.0), "right_arm": (32.0, -32.0, -10.0),
               "right_fore": (36.0, 0.0, 0.0), "chain": (-74.94, -38.49, -21.767), "halo": (35.0, 0.0, 0.0)},
    "archer": {"left_arm": (18.0, -8.0, 14.0), "left_fore": (56.0, 0.0, 0.0), "right_arm": (4.0, 6.0, 0.0),
               "right_fore": (54.0, 0.0, 0.0), "bow": (-73.84, -11.264, -11.557), "halo": (25.0, 0.0, 0.0),
               "string_top": (0.0, 0.0, -20.0), "string_bot": (0.0, 0.0, 160.0), "arrow": (-58.141, -3.175, 5.094),
               "tabard_front": (6.0, 0.0, 0.0), "tabard_back": (-6.0, 0.0, 0.0), "left_thigh": (-14.0, 0.0, 0.0),
               "right_thigh": (-14.0, 0.0, 0.0), "left_shin": (-12.0, 0.0, 0.0), "right_shin": (-12.0, 0.0, 0.0)},
    "sword": {"left_arm": (10.0, 8.0, 14.0), "left_fore": (70.0, 0.0, 0.0), "right_arm": (-8.0, 2.0, -18.0),
              "right_fore": (102.0, 0.0, 0.0), "sword": (-94.206, 18.094, 0.696),
              "shield": (-176.301, -17.882, 28.197), "halo": (25.0, 0.0, 0.0),
              "tabard_front": (6.0, 0.0, 0.0), "tabard_back": (-6.0, 0.0, 0.0), "left_thigh": (-14.0, 0.0, 0.0),
              "right_thigh": (-14.0, 0.0, 0.0), "left_shin": (-12.0, 0.0, 0.0), "right_shin": (-12.0, 0.0, 0.0)},
}
HALO_PITCH = 1.0        # m2: the halo rides the head in full (its motion parented to the head, 1-2 ticks late)
HEAD_COUNTER = 0.35     # the flying head counter-pitches this share of the body's pitch (m1 0.7: with the halo on
#                         the head, 0.7 tipped the ring onto the wing roots)
SWORD_CARRY = (-30.0, 0.0, 10.0)   # body-frame tilt of the resting blade about the grip: upright before the shoulder,
#                                    clear of the right wing's stroke (b2 rests it 40 deg back, inside the stroke)
HALO_OFF = {"mender": (0.0, 3.576, 4.914), "archer": (0.0, 5.8, 4.514), "sword": (0.0, 5.8, 4.514)}  # halo - head
# two-bone arms (b2 == b4 to 0.2 px): shoulder pivot S, elbow pivot E, the hand point G (the held prop's pivot, or
# the open hand's fingertips) in the fore's rest space, all model px; the fore is a hinge about its own x
ARM = {
    "mender": {"l": ((4.5, 25.196, 0.0), (4.5, 19.596, 0.0), (4.405, 14.186, -0.477)),
               "r": ((-4.5, 25.196, 0.0), (-4.5, 19.596, 0.0), (-3.424, 12.17, -0.223))},
    "archer": {"l": ((4.5, 26.524, 0.0), (4.5, 20.924, 0.0), (3.886, 15.418, -0.585)),
               "r": ((-4.5, 26.524, 0.0), (-4.5, 20.924, 0.0), (-3.424, 13.498, -0.223))},
    "sword": {"l": ((4.5, 26.524, 0.0), (4.5, 20.924, 0.0), (4.975, 14.11, -0.152)),
              "r": ((-4.5, 26.524, 0.0), (-4.5, 20.924, 0.0), (-4.232, 16.052, -1.255))},
}
# b4 archer (tools/gen_angels.py bow_string_b4): the string halves' pivots (the bow tips, model px) and the bow's
# rest long axis / belly direction in the body frame (its world rotation at rest is the identity)
STRING_TIP = {"string_top": (9.7, 31.392, 1.115), "string_bot": (-1.928, -0.557, 1.115)}
BOW_LONG, BOW_FACE = (0.342, 0.94, 0.0), (0.0, 0.0, -1.0)
AIM = (0.0, -0.342, -0.94)               # the archer's shot: -20 deg below the facing
SWORD_CUT = [(7, 0.0), (8, 0.15), (9, 0.40), (10, 0.63), (11, 0.81), (12, 0.93)]   # the cut: (tick, share of
#   the arc); the guard is reached 2 ticks before the first (held), contact stays t10. m3: spread over 5 ticks (was
#   8-12, 0.25 / 0.53 / 0.79 / 0.95): the blade turned 49 deg/tick in the world with the torso twist; now <= 43
SWORD_BLADE = (-0.16, 0.766, 0.643)     # the blade (pommel -> tip) in the sword bone's rest space (b2: 40 back)
# the Mender's column robe: robe panels hang from y 16.8 (pivot offsets from the skirt centre C), the hem 7.15 lower
SKIRT = {"robe_front": (0.0, 0.0, -2.15), "robe_back": (0.0, 0.0, 1.85), "robe_left": (2.8, 0.0, -0.15),
         "robe_right": (-2.8, 0.0, -0.15)}
HEM = {"hem_front": (0.0, -7.15, -3.45), "hem_back": (0.0, -7.15, 3.15), "hem_left": (4.6, -7.15, -0.15),
       "hem_right": (-4.6, -7.15, -0.15)}
FLARE = {"front": (1.0, 0.0), "back": (-1.0, 0.0), "left": (0.0, 1.0), "right": (0.0, -1.0)}   # (rx, rz) signs
WING = ("arm", "fore", "hand", "tips")


# ------------------------------------------------------------------ maths -------------------------------------------
def clamp(x, lo, hi):
    return lo if x < lo else hi if x > hi else x


def smooth(x):
    x = clamp(x, 0.0, 1.0)
    return x * x * (3.0 - 2.0 * x)


def lerp(a, b, t):
    return a + (b - a) * t


def keys(t, ks):
    """A monotone cubic (Fritsch-Carlson) through [(tick, value), ...]: the motion runs on THROUGH a key that keeps
    its direction, stops at a key where it turns (a zero tangent: no overshoot), a repeated value is a hold, both
    ends ease in / out; clamped outside the keys."""
    n = len(ks)
    if t <= ks[0][0]:
        return ks[0][1]
    if t >= ks[-1][0]:
        return ks[-1][1]
    i = 0
    while ks[i + 1][0] < t:
        i += 1
    h = [ks[j + 1][0] - ks[j][0] for j in range(n - 1)]
    d = [(ks[j + 1][1] - ks[j][1]) / h[j] for j in range(n - 1)]

    def tangent(j):
        if j == 0 or j == n - 1 or d[j - 1] * d[j] <= 0.0:
            return 0.0
        w1, w2 = 2.0 * h[j] + h[j - 1], h[j] + 2.0 * h[j - 1]
        return (w1 + w2) / (w1 / d[j - 1] + w2 / d[j])
    t0, v0 = ks[i]
    v1 = ks[i + 1][1]
    u = (t - t0) / h[i]
    m0, m1 = tangent(i) * h[i], tangent(i + 1) * h[i]
    u2, u3 = u * u, u * u * u
    return (2 * u3 - 3 * u2 + 1) * v0 + (u3 - 2 * u2 + u) * m0 + (-2 * u3 + 3 * u2) * v1 + (u3 - u2) * m1


def spring(s, target, k, c, drive=0.0):
    s[1] += k * (target - s[0]) - c * s[1] + drive
    s[0] += s[1]
    return s[0]


def rmat(r):
    """DSL rotation matrix (degrees, x then y then z: Rz Ry Rx)."""
    cx, sx = math.cos(math.radians(r[0])), math.sin(math.radians(r[0]))
    cy, sy = math.cos(math.radians(r[1])), math.sin(math.radians(r[1]))
    cz, sz = math.cos(math.radians(r[2])), math.sin(math.radians(r[2]))
    return ((cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx),
            (sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx),
            (-sy, cy * sx, cy * cx))


def mmul(A, B):
    return tuple(tuple(A[i][0] * B[0][j] + A[i][1] * B[1][j] + A[i][2] * B[2][j] for j in range(3)) for i in range(3))


def mT(A):
    return tuple(tuple(A[j][i] for j in range(3)) for i in range(3))


def mv(A, v):
    return tuple(A[i][0] * v[0] + A[i][1] * v[1] + A[i][2] * v[2] for i in range(3))


def axis_mat(ax, deg):
    n = math.sqrt(ax[0] ** 2 + ax[1] ** 2 + ax[2] ** 2)
    x, y, z = ax[0] / n, ax[1] / n, ax[2] / n
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    C = 1.0 - c
    return ((c + x * x * C, x * y * C - z * s, x * z * C + y * s),
            (y * x * C + z * s, c + y * y * C, y * z * C - x * s),
            (z * x * C - y * s, z * y * C + x * s, c + z * z * C))


def euler(R):
    ry = math.asin(clamp(-R[2][0], -1.0, 1.0))
    return (math.degrees(math.atan2(R[2][1], R[2][2])), math.degrees(ry), math.degrees(math.atan2(R[1][0], R[0][0])))


def wrap(a):
    while a > 180.0:
        a -= 360.0
    while a < -180.0:
        a += 360.0
    return a


def delta_on_rest(rest, R_new):
    """The pose extra (degrees added to the rest Euler) that makes a bone's rotation R_new."""
    e = euler(R_new)
    return [wrap(e[i] - rest[i]) for i in range(3)]


# ------------------------------------------------------------------ the wing cycle (sheet 3.1) ----------------------
def warp(p):
    """Phase -> stroke angle (0..1): the downstroke DOWN0..BOTTOM1 is the first half, the upstroke the second."""
    p = p % 1.0
    if p < DOWN0:
        p += 1.0
    if p < BOTTOM1:
        return 0.5 * (p - DOWN0) / (BOTTOM1 - DOWN0)
    return 0.5 + 0.5 * (p - BOTTOM1) / (1.0 + DOWN0 - BOTTOM1)


def elev0(p, up):
    """The arm's elevation through one beat: top hold, a downstroke easing off the top and striking into the bottom,
    a short bottom, the upstroke rising last."""
    p = p % 1.0
    if p < DOWN0:
        return up
    if p < DOWN1:
        u = (p - DOWN0) / (DOWN1 - DOWN0)
        return up + (DN - up) * u * u * (2.2 - 1.2 * u)
    if p < BOTTOM1:
        return DN
    return DN + (up - DN) * smooth((p - BOTTOM1) / (1.0 - BOTTOM1))


def fold0(p):
    """0 spread .. 1 folded: the wrist folds at the start of the upstroke and opens again for the top hold."""
    p = p % 1.0
    if p < 0.57:
        return 0.0
    if p < 0.71:
        return smooth((p - 0.57) / 0.14)
    if p < 0.86:
        return 1.0
    return 1.0 - smooth((p - 0.86) / 0.14)


def bob0(p):
    """-1 at the top hold .. +1 at the bottom (the body rides the stroke: it rises while the wings push down)."""
    return -math.cos(2.0 * math.pi * warp(p))


def wing_cycle(k, p, B, amp, sweep_amp, droop=0.0, fold_extra=0.0, amp_up=None, fold_k=1.0):
    """One side's wing channels at phase p (beat B ticks): arm (elev, sweep), fore / hand (elev relative to the
    parent, fold back), the fold of the tips; the tips' own elevation is a spring (step). amp scales the stroke
    about its middle; amp_up (default amp) scales the upper half alone (the archer's aim: half-open at the top, a
    full downstroke after the release)."""
    au = min(1.0, amp) if amp_up is None else amp_up         # a deeper stroke goes DOWN (the halo caps the top)
    mid = 0.5 * (UP[k] + DN)

    def e(q):
        v = elev0(q, UP[k]) - mid
        return mid + (au if v > 0.0 else amp) * v
    ea, ef, eh = e(p), e(p - LAG_FORE / B), e(p - LAG_HAND / B)
    sweep = sweep_amp * (1.0 - 11.0 * math.cos(2.0 * math.pi * warp(p)))
    return {"arm": (ea + droop, sweep), "sup": SUPINATE * fold0(p - 1.0 / B),
            "fore": (ef - ea, fold_k * FOLD_FORE[k] * fold0(p - 1.5 / B)),
            "hand": (eh - ef, fold_k * FOLD_HAND[k] * fold0(p) + fold_extra),
            "tips_fold": fold_k * FOLD_TIPS * fold0(p - 2.0 / B)}


def feather_pitch(R):
    """How far a flight (its cube's rotation R in the body frame) is turned about its own long axis (local z) off
    lying flat, signed (right-handed about that axis, -180..180): 0 = its top face up (the b4 flights rest at 0-19),
    +-90 = edge-on (a comb tooth). A flight standing on end (its axis within 10 deg of vertical) reads 0."""
    n, l_ = mv(R, (0.0, 1.0, 0.0)), mv(R, (0.0, 0.0, 1.0))
    if abs(l_[1]) > 0.985:
        return 0.0
    n0 = unit((-l_[1] * l_[0], 1.0 - l_[1] * l_[1], -l_[1] * l_[2]))
    return math.degrees(math.atan2(dot(cross(n0, n), l_), dot(n0, n)))


def cap_pitch(P, L, cubes):
    """m3: the bone's own rotation L (under the parent's P, body frame) twisted back about its flights' mean long axis
    so no flight pitches past PITCH_CAP either way; the very matrix L when none does."""
    Rc = [rmat(c) for c in cubes]
    ax = unit(tuple(sum(mv(R, (0.0, 0.0, 1.0))[i] for R in Rc) for i in range(3)))
    for _ in range(3):
        PL = mmul(P, L)
        s = max((feather_pitch(mmul(PL, R)) for R in Rc), key=abs)
        if abs(s) <= PITCH_CAP + 0.25:
            break
        L = mmul(L, axis_mat(ax, -(s - math.copysign(PITCH_CAP, s))))
    return L


# m3: the death / arrival cocoon (blind5 R: the wings closed up 60-70 deg over the shoulders inside the light): raised
# 60 and 5 forward, the elbow 10 and wrist 5 up: the tips stand 57-69 deg over the shoulder (the m2 flame, 30 up,
# read as a slump). A contact sweep picked it: higher, or swept back, crosses the two hands / tips behind the back or
# runs the elbows through the halo by > 2.7 px; this one adds <= 2.6 px (the spine blocks, as the hover does)
COCOON = {"arm": (60.0, 5.0), "fore": (10.0, 0.0), "hand": (5.0, 0.0), "tips_fold": 0.0}
# m2: the death's wings fold UP and back (raised 30, swept back 10, the elbow and wrist folded behind) with the halo
# kept on the head; m1's tall V (55 up, hands curling in) only cleared the halo with a 6-11 px lift off the head,
# and a higher fold (38 up, 30 back) crossed the two wings' tips behind the back (2.5-3 px in a contact sweep)
# (a contact sweep over raised poses: higher, or the hands closing to meet, runs the inner wings through the b2 halo
#  or through each other - the flame is a tall V whose hands curl in, the tips 8-12 px apart over the halo)


# ------------------------------------------------------------------ state ------------------------------------------
class Inputs:
    def __init__(self, speed=0.0, yaw_rate=0.0, attack_seq=0, attack_in=-1, hurt_time=0, dying=False):
        self.speed, self.yaw_rate = speed, yaw_rate
        self.attack_seq, self.attack_in = attack_seq, attack_in
        self.hurt_time, self.dying = hurt_time, dying


class State:
    def __init__(self, kind, seed=1, arrive=False):
        self.kind = kind
        self.rng = seed & 0x7fffffff
        self.tempo = 1.0 + TEMPO_SPREAD * (2.0 * self.rand() - 1.0)
        self.beat = HOVER_T[kind] * self.tempo
        self.phase = 0.0
        self.beats = 0                       # wing beats completed (the slow beats count these)
        self.glance_at = 2                   # the beat the next head glance starts on
        self.glance_t = -1                   # ticks into the glance (-1 none)
        self.glance_yaw = 0.0
        self.fly_w = 0.0                     # 0 hover .. 1 cruise (eased)
        self.speed_e = 0.0                   # eased speed (acceleration = speed - speed_e)
        self.roll1 = self.roll2 = 0.0        # the bank's 2-pole filter
        self.glide = 0                       # ticks of glide left (fly)
        self.glide_w = 0.0                   # the glide pose's weight (eased)
        self.fly_beats = 0                   # beats since the last glide
        self.attack_t = -1                   # ticks into the attack clip (-1 none)
        self.attack_seq = 0
        self.cuts = 0                        # sword: cuts made (every 3rd is the rising backhand)
        self.variant = 0
        self.hit_t = -1
        self.since_hit = 99
        self.hurt_last = 0
        self.arrive_t = 0 if arrive else -1
        self.death_t = -1
        self.age = 0
        self.sp = {}                         # springs: name -> [x, v]
        self.prev = {}                       # last tick's values for velocities
        self.out = {}

    def rand(self):
        self.rng = (self.rng * 1103515245 + 12345) & 0x7fffffff
        return self.rng / 2147483648.0

    def s(self, name, x0=0.0):
        if name not in self.sp:
            self.sp[name] = [x0, 0.0]
        return self.sp[name]


# ------------------------------------------------------------------ clips ------------------------------------------
def vkeys(t, ks):
    """keys() per component for [(tick, (x, y, z)), ...]."""
    return tuple(keys(t, [(k, v[i]) for k, v in ks]) for i in range(3))


def unit(v):
    n = math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
    return (v[0] / n, v[1] / n, v[2] / n)


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def frame(x, y):
    """Columns x^, y^ (y made square to x), z^ = x^ cross y^."""
    x = unit(x)
    d = dot(y, x)
    y = unit((y[0] - d * x[0], y[1] - d * x[1], y[2] - d * x[2]))
    z = cross(x, y)
    return ((x[0], y[0], z[0]), (x[1], y[1], z[1]), (x[2], y[2], z[2]))


def align(a, b):
    """The smallest rotation turning direction a onto direction b."""
    a, b = unit(a), unit(b)
    ax = cross(a, b)
    s_ = math.sqrt(dot(ax, ax))
    c = clamp(dot(a, b), -1.0, 1.0)
    if s_ < 1e-9:
        return ((1.0, 0.0, 0.0), (0.0, 1.0, 0.0), (0.0, 0.0, 1.0))
    return axis_mat(ax, math.degrees(math.atan2(s_, c)))


def rest_hand(kind, side):
    """(posed hand point, posed elbow direction from the shoulder) of the rest pose, body frame."""
    S, E, G = ARM[kind][side]
    rest = REST[kind]
    arm, fore = ("left_arm", "left_fore") if side == "l" else ("right_arm", "right_fore")
    Ra = rmat(rest[arm])
    a = tuple(E[i] - S[i] for i in range(3))
    b = tuple(G[i] - E[i] for i in range(3))
    fb = mv(rmat(rest[fore]), b)
    q = tuple(a[i] + fb[i] for i in range(3))
    hq = mv(Ra, q)
    return tuple(S[i] + hq[i] for i in range(3)), mv(Ra, a)


def ik_arm(kind, side, target, pole):
    """Two-bone IK: the upper arm's rotation (body frame) and the fore's hinge angle (deg, about its x) that put the
    hand point on target with the elbow toward pole. Out of reach: the arm points at the target, fully stretched."""
    S, E, G = ARM[kind][side]
    fx = REST[kind]["left_fore" if side == "l" else "right_fore"][0]
    a = tuple(E[i] - S[i] for i in range(3))
    b = tuple(G[i] - E[i] for i in range(3))
    tv = tuple(target[i] - S[i] for i in range(3))
    la, lb = math.sqrt(dot(a, a)), math.sqrt(dot(b, b))
    D = clamp(math.sqrt(dot(tv, tv)), abs(la - lb) + 0.05, la + lb - 0.02)
    # |a + Rx(phi) b|^2 = la^2 + lb^2 + 2 (c0 + A cos phi + B sin phi)
    c0 = a[0] * b[0]
    A_ = a[1] * b[1] + a[2] * b[2]
    B_ = a[2] * b[1] - a[1] * b[2]
    R_ = math.hypot(A_, B_)
    g = math.atan2(B_, A_)
    cphi = clamp(((D * D - la * la - lb * lb) / 2.0 - c0) / R_, -1.0, 1.0)
    roots = [g + math.acos(cphi), g - math.acos(cphi)]
    ref = math.radians(fx)
    phi = min(roots, key=lambda r: abs(wrap(math.degrees(r - ref))))
    fb = mv(rmat((math.degrees(phi), 0.0, 0.0)), b)
    q = tuple(a[i] + fb[i] for i in range(3))
    return mmul(frame(tv, pole), mT(frame(q, a))), math.degrees(phi)


def attack_tracks(kind, t, variant):
    """Scripted channels of the attack clip at tick t: body / head deltas, the hands' paths (IK targets, body frame
    model px, with elbow poles) and the props' aims; sheet 3.4."""
    E = EVENT[kind]
    o = {}
    if kind == "mender":
        # 0-6 gather (the lantern drawn up to the chest in both hands), 6-9 HOLD, 10 release (thrust at the
        # target), 11-16 overshoot and back, 16-24 settle into the hover
        L0, _ = rest_hand(kind, "l")
        R0, _ = rest_hand(kind, "r")
        o["body_dy"] = keys(t, [(0, 0.0), (6, 1.0), (9, 1.0), (11, 2.6), (16, 1.4), (24, 0.0)])
        o["body_rx"] = keys(t, [(0, 0.0), (6, 4.0), (9, 4.0), (10, -6.0), (14, -2.0), (24, 0.0)])
        o["head_rx"] = keys(t, [(0, 0.0), (6, -6.0), (9, -6.0), (11, -10.0), (24, 0.0)])
        o["ik_l"] = vkeys(t, [(0, L0), (6, (2.4, 21.2, -9.2)), (9, (2.4, 21.2, -9.2)), (10, (2.6, 23.0, -10.2)),
                              (12, (2.6, 23.6, -10.4)), (16, (2.6, 23.0, -10.2)), (24, L0)])
        o["ik_r"] = vkeys(t, [(0, R0), (6, (0.2, 20.0, -9.8)), (9, (0.2, 20.0, -9.8)), (10, (0.0, 22.2, -11.6)),
                              (12, (0.0, 22.8, -12.0)), (16, (0.0, 22.2, -11.6)), (24, R0)])
        _, eL = rest_hand(kind, "l")
        _, eR = rest_hand(kind, "r")
        o["pole_l"] = vkeys(t, [(0, eL), (6, (1.0, -0.6, 0.6)), (16, (1.0, -0.6, 0.6)), (24, eL)])
        o["pole_r"] = vkeys(t, [(0, eR), (6, (-1.0, -0.6, 0.6)), (16, (-1.0, -0.6, 0.6)), (24, eR)])
        o["kick"] = 4.0 if t == E else 0.0              # the lantern's pendulum kicked forward by the thrust
        o["glow"] = keys(t, [(0, 1.0), (6, 1.0), (9, 1.3), (10, 1.6), (18, 1.0)])
        o["wing_w"] = keys(t, [(0, 0.0), (6, 1.0), (16, 1.0), (24, 0.0)])
        o["cup"] = keys(t, [(0, 0.0), (6, 8.0), (9, 8.0), (11, 0.0)])
    elif kind == "archer":
        # m2: 0-4 the draw hand comes up to the string (the arrow appears on it at t3), 4-12 the draw (the bow arm
        # pushes out to the aim, -20 deg below the facing; the string hand pulls to the cheek; the body turns side-on
        # 35), 12-16 HOLD, 16 release in ONE tick (the string snaps, the hand flies back), 16-18 the limbs recoil,
        # 18-26 return (the hand passes the quiver at 23: the next arrow), 26-33 settle
        L0, _ = rest_hand(kind, "l")
        R0, _ = rest_hand(kind, "r")
        _, eL = rest_hand(kind, "l")
        _, eR = rest_hand(kind, "r")
        tw = keys(t, [(0, 0.0), (4, 10.0), (12, 35.0), (16, 35.0), (18, 32.0), (26, 8.0), (33, 0.0)])
        o["body_ry"], o["head_ry"] = tw, -tw
        o["body_dz"] = keys(t, [(0, 0.0), (16, 0.0), (18, 0.8), (26, 0.2), (33, 0.0)])
        d35 = mv(rmat((0.0, -35.0, 0.0)), AIM)
        cheek = (-1.6, 26.6, -3.4)
        g12 = tuple(cheek[i] + 8.4 * d35[i] for i in range(3))
        o["ik_l"] = vkeys(t, [(0, L0), (4, (5.6, 23.5, -7.6)), (12, g12), (16, g12),
                              (17, tuple(g12[i] + 0.3 * d35[i] for i in range(3))), (18, g12),
                              (26, (5.8, 22.4, -8.2)), (33, L0)])
        o["ik_r"] = vkeys(t, [(0, R0), (4, (4.4, 24.0, -5.8)), (12, cheek), (16, cheek), (17, (-3.0, 28.0, -4.4)),
                              (18, (-3.2, 28.0, -4.6)), (21, (-5.0, 25.0, -3.0)), (24, (-5.6, 22.6, 1.2)), (33, R0)])
        o["pole_l"] = vkeys(t, [(0, eL), (4, (1.0, -0.5, 0.2)), (26, (1.0, -0.5, 0.2)), (33, eL)])
        o["pole_r"] = vkeys(t, [(0, eR), (4, (-0.6, -1.0, 0.3)), (12, (-1.0, 0.15, 0.6)), (18, (-1.0, 0.1, 0.5)),
                                (24, (-1.0, -0.3, 0.2)), (33, eR)])
        o["aim"] = mv(rmat((0.0, -tw, 0.0)), AIM)                       # the shot in the body frame
        o["bow"] = keys(t, [(0, 0.0), (4, 0.25), (12, 1.0), (18, 1.0), (26, 0.15), (33, 0.0)])
        o["recoil"] = keys(t, [(0, 0.0), (16, 0.0), (17, 1.0), (18, 0.0)])
        o["string"] = keys(t, [(0, 0.0), (3.9, 0.0), (4.6, 1.0), (16, 1.0), (17, -0.12), (18, 0.05), (19, 0.0)])
        o["arrow"] = 3 <= t <= 15
        o["amp_up"] = keys(t, [(0, 1.0), (4, 1.0), (12, 0.4), (20, 0.4), (30, 1.0)])   # aim: half-open, held
        o["wing_w"] = keys(t, [(0, 0.0), (8, 1.0), (16, 1.0)])
        o["cup"] = 0.0
    else:
        # m2: 0-6 wind-up (the blade raised high over the right shoulder, the shield forward, the body coils), 6-8
        # HOLD, 8-12 the diagonal cut right-high to left-low broken over 4 ticks (~28 / 58 / 86 / 100% of the arc;
        # the torso's twist 1 tick ahead), 10 impact, 12-13 the blade overshoots ~10 deg, 12-16 hold past the left
        # hip (the shield swung clear), 16-25 recover in front. Variant 1 (every 3rd cut): the rising backhand.
        R0, _ = rest_hand(kind, "r")
        _, eR = rest_hand(kind, "r")
        pts = [(-8.0, 30.0, -2.5), (-3.0, 24.5, -8.8), (-0.8, 21.8, -8.2), (0.5, 20.5, -6.5)]
        dirs = [(-0.3, 0.93, 0.1), (-0.05, 0.45, -0.9), (0.4, -0.1, -0.9), (0.5, -0.8, -0.35)]
        if variant == 1:
            pts, dirs = pts[::-1], dirs[::-1]
            pts[1], dirs[1] = (-1.5, 23.0, -9.0), (0.1, 0.4, -0.9)
        cum = [0.0]
        for a, b in zip(pts, pts[1:]):
            cum.append(cum[-1] + math.sqrt(sum((b[i] - a[i]) ** 2 for i in range(3))))
        fr = [c / cum[-1] for c in cum]

        def along(seq, s_):
            for n_ in range(len(fr) - 1):
                if s_ <= fr[n_ + 1] or n_ == len(fr) - 2:
                    u = clamp((s_ - fr[n_]) / (fr[n_ + 1] - fr[n_]), 0.0, 1.0)
                    return tuple(lerp(seq[n_][i], seq[n_ + 1][i], u) for i in range(3))
        S_ = SWORD_CUT
        over = unit(tuple(dirs[3][i] + 0.22 * (dirs[3][i] - dirs[2][i]) for i in range(3)))
        D0 = mv(rmat(SWORD_CARRY), SWORD_BLADE)
        o["ik_r"] = vkeys(t, [(0, R0), (S_[0][0] - 2, pts[0])] + [(tk, along(pts, sv)) for tk, sv in S_] +
                          [(13, pts[3]), (16, pts[3]), (20, (-3.0, 20.5, -8.0)), (25, R0)])
        o["blade"] = unit(vkeys(t, [(0, D0), (S_[0][0] - 2, dirs[0])] +
                                [(tk, unit(along(dirs, sv))) for tk, sv in S_] +
                                [(13, over), (14, dirs[3]), (16, dirs[3]), (20, (0.0, 0.3, -0.95)),
                                 (25, D0)]))
        o["pole_r"] = vkeys(t, [(0, eR), (6, (-1.0, -0.5, 0.4)), (20, (-1.0, -0.5, 0.4)), (25, eR)])
        hd = unit(dirs[0])
        o["plane"] = (hd, unit(cross(hd, unit(dirs[1]))))
        sgn = 1.0 if variant == 0 else -1.0
        o["body_rx"] = keys(t, [(0, 0.0), (6, 10.0), (8, 10.0), (10, -6.0), (12, -12.0), (16, -6.0), (25, 0.0)])
        o["body_ry"] = sgn * keys(t, [(0, 0.0), (6, 15.0), (8, 15.0), (9, 2.0), (10, -16.0), (11, -25.0),
                                      (14, -25.0), (16, -16.0), (25, 0.0)])
        o["body_dz"] = keys(t, [(0, 0.0), (8, 0.0), (10, -1.5), (12, -3.0), (16, -2.0), (25, 0.0)])
        o["body_dy"] = keys(t, [(0, 0.0), (6, 0.6), (8, 0.6), (10, -0.8), (12, -2.0), (16, -1.2), (25, 0.0)])
        o["head_ry"] = -0.6 * o["body_ry"]
        o["la"] = (keys(t, [(0, 0.0), (6, 26.0), (8, 26.0), (11, 6.0), (16, 6.0), (25, 0.0)]),
                   keys(t, [(0, 0.0), (6, -10.0), (8, -10.0), (25, 0.0)]),
                   keys(t, [(0, 0.0), (6, 4.0), (8, 4.0), (11, 34.0), (16, 30.0), (25, 0.0)]))
        o["wing_w"] = keys(t, [(0, 0.0), (6, 1.0), (9, 1.0)])
        o["cup"] = keys(t, [(0, 0.0), (6, 8.0), (8, 8.0), (10, 0.0)])
    return o


def arrive_tracks(k, t):
    """m3 (blind5: R's staging on S's descent): the unit comes down 14 px from the light, braking into the hover point
    by 20 (the body leaning back, the legs reaching), a flat light silhouette with the wings in the COCOON to 14; the
    light gives way to the shaded body over 10 ticks (10-20, no pop); the unfurl's push (16-24) bobs it up 1.5 px,
    the settle (24-29) lets it down onto the hover. The halo stays on the head; its opacity is the body's."""
    o = {}
    u = clamp(t / 20.0, 0.0, 1.0)
    o["dy"] = ARRIVE_DROP * (1.0 - u) * (1.0 - u) + keys(t, [(0, 0.0), (17, 0.0), (23, 1.5), (29, 0.0)])
    o["scale"] = 0.85 + 0.15 * smooth(t / 12.0)
    o["white"] = 1.0 - smooth((t - 10.0) / 10.0)
    o["alpha"] = smooth(t / 6.0)
    o["halo_alpha"] = o["alpha"]
    o["body_rx"] = keys(t, [(0, 0.0), (8, 6.0), (16, 10.0), (22, 4.0), (30, 0.0)])
    o["head_rx"] = keys(t, [(0, -6.0), (20, -10.0), (30, 0.0)])
    o["legs"] = keys(t, [(0, 0.0), (8, 0.3), (16, 1.0), (22, 1.0), (30, 0.0)])
    return o


ARRIVE_DROP, DEATH_LIFT = 14.0, 14.0     # m3: px down into the hover / up out of it (blind4: 12-15)
UNFURL_LEVEL = 5.0                       # m3: the arm's elevation the unfurl opens to (the wings spread out level)


def arrive_wing(k, t, B=30.0):
    """m3: the cocoon to 14 (the flights stirring 2 deg in the light), the unfurl 14-23 (the arm 60 -> 5, ~6 deg/tick,
    9 at its fastest, the elbow and wrist opening), running on through 10 deg of overshoot to 25, then the settle up
    into the hover's top hold, phase-matched (the state is at phase 0 on 29)."""
    top = wing_cycle(k, 0.0, B, 1.0, 1.0)
    w = smooth((t - 14.0) / 9.0)
    out = {n: [lerp(COCOON[n][i], top[n][i], w) for i in range(2)] for n in ("arm", "fore", "hand")}
    out["arm"][0] = keys(t, [(14, COCOON["arm"][0]), (23, UNFURL_LEVEL), (25, UNFURL_LEVEL - 10.0),
                             (29, top["arm"][0])])
    out["arm"][0] += 2.0 * math.sin(2.0 * math.pi * t / 10.0) * (1.0 - smooth((t - 8.0) / 6.0))
    out["tips_fold"] = lerp(COCOON["tips_fold"], top["tips_fold"], w)
    out["sup"] = top["sup"] * w
    return out


def death_tracks(k, t):
    """m3: the arrival's mirror - the head droops 0-3 then lifts, the arms open, the wings fold UP into the COCOON
    2-8, the body lifts 14 px (2-18) as it turns to light (6-14: 8 ticks) and fades (9-20); the halo stays on the
    head and fades with the body."""
    o = {}
    o["dy"] = DEATH_LIFT * smooth((t - 2.0) / 16.0)
    o["stretch"] = 1.0 + 0.06 * smooth((t - 4.0) / 12.0)
    o["white"] = smooth((t - 6.0) / 8.0)
    o["alpha"] = 1.0 - smooth((t - 9.0) / 11.0)
    o["halo_alpha"] = o["alpha"]
    o["head_rx"] = keys(t, [(0, 0.0), (3, -12.0), (8, -4.0), (14, 8.0), (20, 10.0)])
    o["arms_open"] = keys(t, [(0, 0.0), (5, 1.0)])
    o["wing_w"] = keys(t, [(0, 0.0), (2, 0.0), (8, 1.0)])
    return o


# ------------------------------------------------------------------ the tick ---------------------------------------
def step(st, inp):
    k = st.kind
    st.age += 1
    # -- cues -------------------------------------------------------------------------------------------------------
    if inp.dying and st.death_t < 0:
        st.death_t = 0
    cue = inp.attack_seq != st.attack_seq
    st.attack_seq = inp.attack_seq
    if st.death_t < 0 and st.arrive_t < 0:
        if st.attack_t < 0 and 0 <= inp.attack_in <= EVENT[k]:
            st.attack_t = EVENT[k] - inp.attack_in                    # the event key lands on the predicted contact
            st.variant = 1 if (k == "sword" and st.cuts % 3 == 2) else 0
        if cue:
            if st.attack_t < 0:
                st.variant = 1 if (k == "sword" and st.cuts % 3 == 2) else 0
            st.attack_t = EVENT[k]                                    # retimed onto the server's contact
            st.cuts += 1
    if inp.hurt_time > st.hurt_last and st.since_hit >= 6 and st.death_t < 0:
        st.hit_t, st.since_hit = 0, 0
    st.hurt_last = inp.hurt_time
    # -- locomotion -------------------------------------------------------------------------------------------------
    acc = inp.speed - st.speed_e
    st.speed_e += 0.25 * acc
    st.fly_w += (smooth(st.speed_e / CRUISE) - st.fly_w) * 0.15
    fw = st.fly_w
    roll_t = clamp(inp.yaw_rate * ROLL_PER_YAW, -ROLL_CAP, ROLL_CAP)
    st.roll1 += ROLL_A * (roll_t - st.roll1)
    st.roll2 += ROLL_A * (st.roll1 - st.roll2)
    roll = st.roll2
    # -- the wing phase: STATE, advanced by 1 / beatTicks; beatTicks eased between states --------------------------
    target_beat = lerp(HOVER_T[k], FLY_T[k], fw) * st.tempo
    st.beat += (target_beat - st.beat) * BEAT_EASE
    B = st.beat
    if st.arrive_t >= 0:
        # m3: the arrival keys its own wings; the phase runs on to land on 0 (the top hold) at its last tick, so the
        # bob and pitch it fades in are the cycle's own and nothing snaps at the hand-over
        st.phase = ((st.arrive_t - (ARRIVE_LEN - 1)) / B) % 1.0
    elif st.glide > 0:
        st.glide -= 1
    else:
        st.phase += 1.0 / B
    if st.phase >= 1.0:
        st.phase -= 1.0
        st.beats += 1
        st.fly_beats = st.fly_beats + 1 if fw > 0.8 else 0
        if st.fly_beats >= GLIDE_AFTER and st.attack_t < 0 and abs(roll_t) < 1.0:
            st.glide, st.fly_beats = GLIDE_TICKS, 0
        if st.beats >= st.glance_at and st.glance_t < 0:
            st.glance_t = 0
            st.glance_yaw = GLANCE_YAW * (1.0 if st.rand() < 0.5 else -1.0)
            st.glance_at = st.beats + GLANCE_MIN + int(st.rand() * (GLANCE_MAX - GLANCE_MIN + 1))
    p = st.phase
    gl = st.glide > 0
    st.glide_w += ((1.0 if gl else 0.0) - st.glide_w) * 0.3
    gw = st.glide_w
    # -- clip clocks ------------------------------------------------------------------------------------------------
    at = st.attack_t
    A = attack_tracks(k, at, st.variant) if at >= 0 else None
    s0, sl = STROKE[k]
    if A is not None and at == s0 + sl:
        # the attack's own downstroke (s0 .. s0 + sl) hands the wings back to the cycle at the bottom
        st.phase, st.glide = BOTTOM1, 0
        p = st.phase
    hit = st.hit_t
    # m2 hit react: back 20 by t2, held, recovering t4-t10 through a 5 deg overshoot at t6
    HK = [(0, 0.0), (2, 1.0), (4, 0.9), (6, -0.25), (10, 0.0)]
    hk = keys(hit, HK) if hit >= 0 else 0.0
    Ar = arrive_tracks(k, st.arrive_t) if st.arrive_t >= 0 else None
    D = death_tracks(k, st.death_t) if st.death_t >= 0 else None
    # -- body -------------------------------------------------------------------------------------------------------
    breath = math.sin(2.0 * math.pi * (st.beats + p) / BREATH_BEATS)
    bob_amp = BOB[k] * lerp(1.0, 0.6, fw)
    bob = bob_amp * bob0(p - LAG_BOB / B) * (1.0 - gw)
    gth = 2.0 * math.pi * (GLIDE_TICKS - st.glide) / GLIDE_TICKS            # m3: the glide's own breath (one wave)
    bob -= GLIDE_BOB * math.cos(gth) * gw              # rises while the dihedral comes down (the push), sinks after
    if st.arrive_t >= 0:
        bob *= smooth((st.arrive_t - 24) / 6.0)                     # the arrival carries its own bob to 24
    brake = clamp(-acc / 0.08, 0.0, 1.0)
    surge = clamp(acc / 0.08, 0.0, 1.0)
    pitch = lerp(-6.0, FLY_PITCH[k], fw) - 8.0 * surge + 15.0 * brake
    pitch += (0.5 - 3.5 * math.cos(2.0 * math.pi * warp(p - LAG_PITCH / B))) * (1.0 - 0.5 * fw) + 1.0 * breath
    st.s("pitch", pitch)
    pitch = spring(st.sp["pitch"], pitch, 0.3, 0.75)
    body = [pitch, 0.0, roll]
    dmove = [0.0, bob, 0.0]
    head = [-HEAD_COUNTER * (pitch + 6.0) - 0.6 * breath, clamp(inp.yaw_rate * 3.0, -20.0, 20.0), -0.35 * roll]
    if st.glance_t >= 0:
        g = st.glance_t
        head[1] += st.glance_yaw * keys(g, [(0, 0.0), (10, 1.0), (30, 1.0), (42, 0.0)])
        head[0] += -4.0 * keys(g, [(0, 0.0), (10, 1.0), (30, 1.0), (42, 0.0)])
        st.glance_t = g + 1 if g < 42 else -1
    if A is not None:
        body[0] += A.get("body_rx", 0.0)
        body[1] += A.get("body_ry", 0.0)
        dmove[1] += A.get("body_dy", 0.0)
        dmove[2] += A.get("body_dz", 0.0)
        head[0] += A.get("head_rx", 0.0)
        head[1] += A.get("head_ry", 0.0)
    if hk:
        body[0] += 20.0 * hk
        head[0] += 15.0 * keys(hit - 1, HK)
    fx = {"white": 0.0, "alpha": 1.0, "halo_alpha": 1.0, "glow": 1.0}
    scale = {}
    if Ar is not None:
        dmove[1] += Ar["dy"]
        head[0] += Ar["head_rx"]
        body[0] += Ar["body_rx"]
        fx["white"], fx["alpha"], fx["halo_alpha"] = Ar["white"], Ar["alpha"], Ar["halo_alpha"]
        scale["body"] = [Ar["scale"]] * 3
    if D is not None:
        dmove[1] += D["dy"]
        head[0] += D["head_rx"]
        fx["white"], fx["alpha"], fx["halo_alpha"] = D["white"], D["alpha"], D["halo_alpha"]
        scale["body"] = [1.0, D["stretch"], 1.0]
    if A is not None and "glow" in A:
        fx["glow"] = A["glow"]
    # -- wings ------------------------------------------------------------------------------------------------------
    out = {}
    amp = lerp(1.0, 1.15, fw)
    sweep_amp = 1.0 - fw
    bank = roll / ROLL_CAP                                            # + = banked right (right wing inner)
    for side, lr in ((1, "l"), (-1, "r")):
        inner = clamp(-side * bank, 0.0, 1.0)                         # this wing on the inside of the turn
        outer = clamp(side * bank, 0.0, 1.0)
        a_side = amp * (1.0 - 0.3 * inner + 0.1 * outer)
        w = wing_cycle(k, p, B, a_side, sweep_amp, droop=-12.0 * inner, fold_extra=15.0 * inner,
                       amp_up=(a_side * A["amp_up"]) if (A is not None and "amp_up" in A) else None)
        w = {n: (list(v) if isinstance(v, tuple) else v) for n, v in w.items()}
        sup = w["sup"] * (1.0 - gw)
        if gw > 0.001:
            # the glide: arms held at +8 dihedral, the hand -6, the tips rippling 3 deg on a 30-tick wave
            gp = {"arm": (8.0 + GLIDE_DIH * math.sin(gth), 0.0), "fore": (0.0, 0.0),
                  "hand": (-6.0 + 3.0 * math.sin(2.0 * math.pi * st.glide / 30.0), 0.0)}
            for n in ("arm", "fore", "hand"):
                w[n] = [lerp(w[n][i], gp[n][i], gw) for i in range(2)]
            w["tips_fold"] *= 1.0 - gw
        w["arm"][1] += 20.0 * brake
        if A is not None and at < s0 + sl:
            top = wing_cycle(k, 0.04, B, a_side, sweep_amp,
                             amp_up=(a_side * A["amp_up"]) if "amp_up" in A else None)
            if at < s0:
                # anticipation: the wings rise to the top hold (cupped forward) and hold until the stroke
                tw = A.get("wing_w", 0.0)
                for n in ("arm", "fore", "hand"):
                    for i in range(2):
                        w[n][i] = lerp(w[n][i], top[n][i], tw)
                w["tips_fold"] = lerp(w["tips_fold"], 0.0, tw)
                sup *= 1.0 - tw
            else:
                # the attack's own downstroke from the top hold: eases off the top, strikes into the bottom (its
                # fastest tick ~60% in: the sword's at t11); the fore / hand 1 and 2 ticks behind the arm
                bot = wing_cycle(k, BOTTOM1 - 0.001, B, a_side, sweep_amp)["arm"][0]
                ut = (at - s0 + 1.0) / sl

                def es(u):
                    u = clamp(u, 0.0, 1.0)
                    return top["arm"][0] + (bot - top["arm"][0]) * u * u * (2.2 - 1.2 * u)
                lf, lh = STROKE_LAG[k]
                ea, ef, eh = es(ut), es(ut - lf / sl), es(ut - lh / sl)
                w["arm"] = [ea, top["arm"][1] * (1.0 - ut)]
                w["fore"] = [ef - ea, 0.0]
                w["hand"] = [eh - ef, 0.0]
                w["tips_fold"], sup = 0.0, 0.0
            w["arm"][1] += A.get("cup", 0.0)
        if hk:
            w["arm"][0] += 30.0 * hk
            w["tips_fold"] += 10.0 * hk
        if Ar is not None:
            aw = arrive_wing(k, st.arrive_t, B)
            for n in ("arm", "fore", "hand"):
                w[n] = list(aw[n])
            w["tips_fold"] = aw["tips_fold"]
            sup = aw.get("sup", 0.0)
        if D is not None:
            for n in ("arm", "fore", "hand"):
                w[n] = [lerp(w[n][i], COCOON[n][i], D["wing_w"]) for i in range(2)]
            w["tips_fold"] = lerp(w["tips_fold"], COCOON["tips_fold"], D["wing_w"])
            sup = lerp(sup, SUPINATE, D["wing_w"])
        # the tips: a spring on the hand's absolute elevation (they trail up on the downstroke, flick at the bottom)
        hand_abs = w["arm"][0] + w["fore"][0] + w["hand"][0]
        ts = st.s("tips_" + lr, hand_abs)
        tips_rel = clamp(spring(ts, hand_abs, K_TIPS, C_TIPS) - hand_abs, -20.0, 20.0)
        out["wing_%s_arm" % lr] = [0.0, side * w["arm"][1], side * w["arm"][0]]
        out["wing_%s_fore" % lr] = [sup, -side * w["fore"][1], side * w["fore"][0]]   # the supination: outer wing
        out["wing_%s_hand" % lr] = [0.0, -side * w["hand"][1], side * w["hand"][0]]
        out["wing_%s_tips" % lr] = [0.0, -side * w["tips_fold"], side * tips_rel]
        # m3: the pitch cap - the hand, then the tips, twisted back about their flights so none stands edge-on
        P = mmul(rmat(out["wing_%s_arm" % lr]), rmat(out["wing_%s_fore" % lr]))
        for bn in ("hand", "tips"):
            nm = "wing_%s_%s" % (lr, bn)
            L = rmat(out[nm])
            cubes = [(c[0], side * c[1], side * c[2]) for c in FEATHERS[k][bn]]
            L2 = cap_pitch(P, L, cubes)
            if L2 is not L:
                out[nm] = list(euler(L2))
            P = mmul(P, L2)
    # -- arms and props ---------------------------------------------------------------------------------------------
    rest = REST[k]
    la_d = (0.0, 0.0, 0.0)
    ra_d = (0.0, 0.0, 0.0)
    breath_arm = 1.5 * breath
    if A is not None and "la" in A:
        la_d = A["la"]
    if D is not None:
        o = D["arms_open"]
        la_d = (la_d[0] + 20.0 * o, la_d[1], la_d[2] + 30.0 * o)
        ra_d = (ra_d[0] + 20.0 * o, ra_d[1], ra_d[2] - 30.0 * o)
    if hk:
        if k == "sword":
            la_d = (la_d[0] + 30.0 * hk, la_d[1], la_d[2])           # the shield arm comes up (block reflex)
        elif k == "mender":
            la_d = (la_d[0], la_d[1], la_d[2] - 20.0 * hk)           # the lantern arm drops
        else:
            la_d = (la_d[0] - 8.0 * hk, la_d[1], la_d[2] + 6.0 * hk)
        ra_d = (ra_d[0] - 8.0 * hk, ra_d[1], ra_d[2] - 6.0 * hk)
    la_d = (la_d[0] + breath_arm, la_d[1], la_d[2] + 0.5 * fw * 10.0)
    ra_d = (ra_d[0] + breath_arm, ra_d[1], ra_d[2] - 0.5 * fw * 10.0)
    arms = {}
    for side, arm, fore, dl in (("l", "left_arm", "left_fore", la_d), ("r", "right_arm", "right_fore", ra_d)):
        if A is not None and ("ik_" + side) in A:
            # the hand on its path: two-bone IK, the elbow toward the clip's pole (keyed from the rest elbow)
            R_a, phi = ik_arm(k, side, A["ik_" + side], A["pole_" + side])
            R_a = mmul(rmat(dl), R_a)
        else:
            R_a, phi = mmul(rmat(dl), rmat(rest[arm])), rest[fore][0]
        arms[side] = (R_a, rmat((phi, 0.0, 0.0)))
        out[arm] = delta_on_rest(rest[arm], R_a)
        out[fore] = [wrap(phi - rest[fore][0]), 0.0, 0.0]
    R_la, R_lf = arms["l"]
    R_ra, R_rf = arms["r"]
    R_body = rmat(body)
    if k == "mender":
        # the lantern hangs from the hand in the WORLD (a pendulum, period 24, driven by the body's motion)
        R_chain_w0 = mmul(mmul(rmat(rest["left_arm"]), rmat(rest["left_fore"])), rmat(rest["chain"]))
        pv = st.prev.get("pitch_v", 0.0)
        pitch_v = body[0] - st.prev.get("pitch", body[0])
        st.prev["pitch"], st.prev["pitch_v"] = body[0], pitch_v
        # driven by the body's acceleration: the pitch's, the bob's (the beat), the hit's jolt and the heal's thrust
        bvv = bob - st.prev.get("bob", bob)
        drive = -0.35 * (pitch_v - pv) - 0.9 * (bvv - st.prev.get("bob_v", bvv)) + (A or {}).get("kick", 0.0)
        drive += 6.0 if hit == 0 else 0.0
        st.prev["bob"], st.prev["bob_v"] = bob, bvv
        sw = st.s("pend_x")
        ax0 = sw[1]
        spring(sw, -18.0 * fw, K_PEND, C_PEND, drive)
        sw[0] = clamp(sw[0], -40.0, 40.0)
        sz = st.s("pend_z")
        az0 = sz[1]
        spring(sz, 0.6 * roll, K_PEND, C_PEND, 3.0 if hit == 0 else 0.0)      # swings OUT of the turn
        R_sw = rmat((sw[0], 0.0, sz[0]))
        R_par = mmul(R_body, mmul(R_la, R_lf))
        out["chain"] = delta_on_rest(rest["chain"], mmul(mT(R_par), mmul(R_sw, R_chain_w0)))
        # the lantern on the chain's end: a second link, kicked by the chain's angular acceleration
        lx, lz = st.s("lant_x"), st.s("lant_z")
        spring(lx, 0.0, K_LANT, C_LANT, -0.9 * (sw[1] - ax0))
        spring(lz, 0.0, K_LANT, C_LANT, -0.9 * (sz[1] - az0))
        out["lantern"] = [clamp(lx[0], -30.0, 30.0), 0.0, clamp(lz[0], -30.0, 30.0)]
    if k == "archer":
        # m2: drawn, the bow is aimed - its long axis upright across the shot, its belly toward the target (AIM: -20
        # below the facing, the body side-on) - and the limbs recoil 2 ticks after the release; at rest it is the
        # generator's lean. The string's halves (b4) run from the tips to the draw hand while drawn, snap back in one
        # tick; the arrow (b4) rides the draw hand from the nock to the release, pointed at the bow's grip.
        bw = A.get("bow", 0.0) if A is not None else 0.0
        aim = A["aim"] if A is not None else AIM
        W0 = mmul(mmul(rmat(rest["left_arm"]), rmat(rest["left_fore"])), rmat(rest["bow"]))
        up = (0.0, 1.0, 0.0)
        du = dot(up, aim)
        up_t = unit((up[0] - du * aim[0], up[1] - du * aim[1], up[2] - du * aim[2]))
        R_t = mmul(frame(up_t, aim), mT(frame(BOW_LONG, BOW_FACE)))
        ang = math.degrees(math.acos(clamp((R_t[0][0] + R_t[1][1] + R_t[2][2] - 1.0) / 2.0, -1.0, 1.0)))
        ax = (R_t[2][1] - R_t[1][2], R_t[0][2] - R_t[2][0], R_t[1][0] - R_t[0][1])
        R_b = axis_mat(ax, bw * ang) if ang > 1e-6 and dot(ax, ax) > 1e-12 else rmat((0.0, 0.0, 0.0))
        rk = A.get("recoil", 0.0) if A is not None else 0.0
        R_b = mmul(axis_mat(cross(up_t, aim), -6.0 * rk), R_b)
        R_par = mmul(R_la, R_lf)
        L_bow = mmul(mT(R_par), mmul(R_b, W0))
        out["bow"] = delta_on_rest(rest["bow"], L_bow)
        Sl, El, Gl = ARM[k]["l"]
        Sr, Er, Gr = ARM[k]["r"]

        def x_fore_l(q):
            r = tuple(El[i] + mv(R_lf, tuple(q[n] - El[n] for n in range(3)))[i] for i in range(3))
            return tuple(Sl[i] + mv(R_la, tuple(r[n] - Sl[n] for n in range(3)))[i] for i in range(3))

        def x_bow(q):
            return x_fore_l(tuple(Gl[i] + mv(L_bow, tuple(q[n] - Gl[n] for n in range(3)))[i] for i in range(3)))
        hr = tuple(Er[i] + mv(R_rf, tuple(Gr[n] - Er[n] for n in range(3)))[i] for i in range(3))
        hand = tuple(Sr[i] + mv(R_ra, tuple(hr[n] - Sr[n] for n in range(3)))[i] for i in range(3))
        tips = {n: x_bow(q) for n, q in STRING_TIP.items()}
        mid = tuple(0.5 * (tips["string_top"][i] + tips["string_bot"][i]) for i in range(3))
        sw_ = A.get("string", 0.0) if A is not None else 0.0
        tgt = tuple(mid[i] + sw_ * (hand[i] - mid[i]) for i in range(3))
        R_pb = mmul(R_par, L_bow)
        for n, tip in tips.items():
            W_cur = mmul(R_pb, rmat(rest[n]))
            cur = mv(W_cur, (0.0, -1.0, 0.0))
            want = tuple(tgt[i] - tip[i] for i in range(3))
            out[n] = delta_on_rest(rest[n], mmul(mT(R_pb), mmul(align(cur, want), W_cur)))
            L0 = math.sqrt(sum((mid[i] - tip[i]) ** 2 for i in range(3)))
            scale[n] = [1.0, math.sqrt(dot(want, want)) / L0, 1.0]
        grip = x_fore_l(Gl)
        adir = aim if (A is not None and A.get("bow", 0.0) > 0.0) else tuple(grip[i] - hand[i] for i in range(3))
        out["arrow"] = delta_on_rest(rest["arrow"], mmul(mT(mmul(R_ra, R_rf)), align((0.0, 0.0, -1.0), adir)))
        if not (A is not None and A.get("arrow")):
            out["_hide"] = ["arrow"]
    if k == "sword":
        # the blade aimed in the body frame (rest: carried upright before the shoulder, SWORD_CARRY)
        W0 = mmul(mmul(rmat(rest["right_arm"]), rmat(rest["right_fore"])), rmat(rest["sword"]))
        W_carry = mmul(rmat(SWORD_CARRY), W0)
        D0 = mv(rmat(SWORD_CARRY), SWORD_BLADE)
        if A is not None:
            # the cut turns the blade about ONE axis (the plane through the high guard and the cut's middle), a
            # small correction on top keeps it on its keyed direction: never the ambiguous half-turn of align()
            hd, n_ = A["plane"]
            bl = A["blade"]
            th = math.degrees(math.atan2(dot(cross(hd, bl), n_), dot(hd, bl)))
            R_cut = axis_mat(n_, th)
            W = mmul(align(mv(R_cut, hd), bl), mmul(R_cut, mmul(align(D0, hd), W_carry)))
        else:
            W = W_carry
        out["sword"] = delta_on_rest(rest["sword"], mmul(mT(mmul(R_ra, R_rf)), W))
    # -- cloth, legs, halo: spring-dampers that lag the body (secondary motion) ------------------------------------
    vy = bob - st.prev.get("bob_c", bob)
    st.prev["bob_c"] = bob
    # the cloth hangs in the world: it lags the body's pitch / roll, flares on the bob and trails in flight
    cs_x = st.s("cloth_x", body[0])
    cs_z = st.s("cloth_z", body[2])
    spring(cs_x, body[0], K_CLOTH, C_CLOTH)
    spring(cs_z, body[2], K_CLOTH, C_CLOTH)
    sx = 1.5 * clamp(cs_x[0] - body[0], -30.0, 30.0) - 25.0 * fw
    sz_ = clamp(cs_z[0] - body[2], -25.0, 25.0) * 0.7
    fl = st.s("flare")
    flare = spring(fl, clamp(-vy * 9.0, -6.0, 6.0) + 6.0 * hk, K_CLOTH, C_CLOTH)
    if k == "mender":
        # the column robe swings as ONE skirt about its centre (the panels stay closed), each panel flares on its
        # own pivot; the hem is the next link (lags 3), the stoles hang from the chest
        R_sk = rmat((sx, 0.0, sz_))
        hx, hz = st.s("hem_x", sx), st.s("hem_z", sz_)
        spring(hx, sx, K_HEM, C_HEM)
        spring(hz, sz_, K_HEM, C_HEM)
        R_hem = rmat((hx[0], 0.0, hz[0]))
        mv_ = {}
        for n, off in SKIRT.items():
            f = FLARE[n.split("_")[1]]
            out[n] = [sx + f[0] * flare, 0.0, sz_ + f[1] * flare]
            d = mv(R_sk, off)
            mv_[n] = [d[i] - off[i] for i in range(3)]
        for n, off in HEM.items():
            f = FLARE[n.split("_")[1]]
            out[n] = [hx[0] + 1.6 * f[0] * max(0.0, flare), 0.0, hz[0] + 1.6 * f[1] * max(0.0, flare)]
            d = mv(R_sk, off)
            mv_[n] = [d[i] - off[i] for i in range(3)]
        for n, fwd, back in (("stole_front", 1.1, 0.0), ("stole_back", 0.0, 1.1)):
            a = sx * (fwd if sx > 0 else back)
            fl_ = flare if n == "stole_front" else -flare
            out[n] = [a + (max(0.0, fl_) if n == "stole_front" else min(0.0, fl_)) * 0.6, 0.0, 0.5 * sz_]
        dmove_extra = mv_
    else:
        dmove_extra = {}
        for n in ("tabard_front", "tabard_back", "tabard_left", "tabard_right"):
            f = FLARE[n.split("_")[1]]
            tr = {"tabard_front": 0.6, "tabard_back": 0.3 if k == "archer" else 1.0}.get(n, 1.0)   # the quiver
            out[n] = [sx * tr + f[0] * flare, 0.0, sz_ + f[1] * flare]
        for side, lr in ((1, "left"), (-1, "right")):
            ls = st.s("leg_" + lr, body[0])
            spring(ls, body[0], K_LEG, C_LEG)
            lag = clamp(ls[0] - body[0], -25.0, 25.0)
            kick = 0.8 * flare
            reach = Ar["legs"] if Ar is not None else 0.0                 # the arrival's legs reach for the ground
            out[lr + "_thigh"] = [lag - 25.0 * fw - 6.0 * hk + kick * side * 0.3 + 22.0 * reach, 0.0,
                                  side * 2.0 * fw]
            out[lr + "_shin"] = [0.5 * lag - 10.0 * fw - 6.0 * hk + 8.0 * reach, 0.0, 0.0]
    # the halo rides the head 2 ticks late (it hangs from the body bone): rotation and the pivot it is carried to
    hs = [st.s("halo_" + a, head[i]) for i, a in enumerate("xyz")]
    for i in range(3):
        spring(hs[i], head[i], K_HALO, C_HALO)
    wob = 6.0 * keys(hit - 2, [(0, 0.0), (1, 1.0), (2, -1.0), (3, 0.6), (4, 0.0)]) if hit >= 2 else 0.0
    halo_r = [HALO_PITCH * hs[0][0], hs[1][0], HALO_PITCH * hs[2][0] + wob]
    Hm = rmat(halo_r)
    off = HALO_OFF[k]
    d = mv(Hm, off)
    breath_late = 0.3 * math.sin(2.0 * math.pi * ((st.beats + p) / BREATH_BEATS - 0.1))     # the halo rises late
    halo_mv = [d[0] - off[0], d[1] - off[1] + breath_late, d[2] - off[2]]
    out["body"] = body
    out["head"] = head
    out["halo"] = halo_r
    move = {"body": dmove, "halo": halo_mv}
    move.update(dmove_extra)
    out["_move"] = move
    if scale:
        out["_scale"] = scale
    out["_fx"] = fx
    st.out = out
    # -- clocks -----------------------------------------------------------------------------------------------------
    if st.attack_t >= 0:
        st.attack_t += 1
        if st.attack_t >= ATTACK_LEN[k]:
            st.attack_t = -1
    if st.hit_t >= 0:
        st.hit_t += 1
        if st.hit_t > HIT_LEN:
            st.hit_t = -1
    st.since_hit += 1
    if st.arrive_t >= 0:
        st.arrive_t += 1
        if st.arrive_t >= ARRIVE_LEN:
            st.arrive_t, st.phase = -1, 0.0
    if st.death_t >= 0 and st.death_t < DEATH_LEN:
        st.death_t += 1
    return st


def pose(st):
    """The pose step() last wrote: {bone: [rx, ry, rz]} plus "_move" / "_scale" / "_fx" (see the module doc)."""
    return st.out


# ------------------------------------------------------------------ the clip harness (the fixture) ------------------
WARM = 40
CLIPS = ("hover", "fly", "bank", "attack", "hit", "arrive", "death")


def clip_len(kind, clip):
    return {"hover": 32, "fly": 64, "bank": 32, "attack": ATTACK_LEN[kind], "hit": 12, "arrive": ARRIVE_LEN,
            "death": DEATH_LEN}[clip]


def run(kind, clip, seed=1, after=0):
    """[(tick, pose)] for every tick of the clip (tick 0 = its first), after the warm-up the clip needs; after: that
    many ticks more with the inputs back at the clip's steady state (the hand-back, not in the fixture)."""
    st = State(kind, seed, arrive=(clip == "arrive"))
    inp = Inputs()
    frames = []
    if clip != "arrive":
        for _ in range(WARM):
            step(st, inp)
        if clip == "bank":
            inp.speed = CRUISE
            for _ in range(30):
                step(st, inp)
    n = clip_len(kind, clip)
    E = EVENT[kind]
    for t in range(n + after):
        if clip == "fly":
            inp.speed = CRUISE
        elif clip == "bank":
            inp.speed = CRUISE
            inp.yaw_rate = 6.0 if t < 16 else 0.0
        elif clip == "attack":
            inp.attack_in = E - t if t <= E else -1
            if t == E:
                inp.attack_seq += 1
        elif clip == "hit":
            inp.hurt_time = max(0, 10 - t)
        elif clip == "death":
            inp.dying = True
        step(st, inp)
        frames.append((t, json.loads(json.dumps(pose(st)))))
    return frames


def rnd(x):
    if isinstance(x, float):
        return round(x, 3) + 0.0
    if isinstance(x, list):
        return [rnd(v) for v in x]
    if isinstance(x, dict):
        return {k: rnd(v) for k, v in x.items()}
    return x


def fixture(every=2, seed=1):
    out = {"generator": "tools/angel_anim.py", "every": every, "seed": seed, "warm": WARM,
           "inputs": "see run(): fly speed %.2f; bank yaw 6 deg/t for 16 t; attack cue on the event tick; hit "
                     "hurtTime 10 at t0; death dying from t0; arrival from a fresh State(arrive=True)" % CRUISE,
           "kinds": {}}
    for kind in KINDS:
        ck = {}
        for clip in CLIPS:
            fr = run(kind, clip, seed)
            ck[clip] = {"ticks": clip_len(kind, clip), "event": EVENT[kind] if clip == "attack" else None,
                        "poses": {str(t): rnd(p) for t, p in fr if t % every == 0 or t == len(fr) - 1}}
        out["kinds"][kind] = ck
    return out


# ------------------------------------------------------------------ stats (needs the model json for the FK) ---------
def load_model(path):
    m = json.load(open(path))
    return {b["name"]: b for b in m["bones"]}


def fk(bones, pz):
    """bone -> (R, t) in the model's space for pose pz (rest + extra, _move, _scale ignored for the stats)."""
    mvd = pz.get("_move", {})
    cache = {}

    def frame(n):
        if n in cache:
            return cache[n]
        b = bones[n]
        r = [b.get("rotation", [0, 0, 0])[a] + pz.get(n, [0, 0, 0])[a] for a in range(3)]
        R = rmat(r)
        pv = [b["pivot"][i] + mvd.get(n, [0, 0, 0])[i] for i in range(3)]
        t = [pv[i] - mv(R, b["pivot"])[i] for i in range(3)]
        if b.get("parent"):
            Rp, tp = frame(b["parent"])
            R, t = mmul(Rp, R), [mv(Rp, t)[i] + tp[i] for i in range(3)]
        cache[n] = (R, t)
        return cache[n]
    return frame


def far_point(b, pivot):
    """The bone's cube corner farthest from pivot (cube rotations applied), in the bone's rest space."""
    best, bd = pivot, -1.0
    for c in b.get("cubes", []):
        ctr = [c["from"][i] + c["size"][i] / 2.0 for i in range(3)]
        cp = c.get("pivot") or ctr
        Rc = rmat(c.get("rotation", [0, 0, 0]))
        for dx in (0, 1):
            for dy in (0, 1):
                for dz in (0, 1):
                    p0 = [c["from"][0] + dx * c["size"][0] - cp[0], c["from"][1] + dy * c["size"][1] - cp[1],
                          c["from"][2] + dz * c["size"][2] - cp[2]]
                    q = mv(Rc, p0)
                    p = [q[i] + cp[i] for i in range(3)]
                    d = sum((p[i] - pivot[i]) ** 2 for i in range(3))
                    if d > bd:
                        best, bd = p, d
    return best


def geo(Ra, Rb):
    R = mmul(mT(Ra), Rb)
    return math.degrees(math.acos(clamp((R[0][0] + R[1][1] + R[2][2] - 1.0) / 2.0, -1.0, 1.0)))


LOCKED = ("bow", "arrow", "string_top", "string_bot", "chain", "lantern", "sword", "halo")   # aimed in the world


def stats(model_key="b2", seed=1):
    """Per clip: the max joint step per tick (local rotation, the rig's joints; the world-aimed props - bow, arrow,
    string, lantern chain, blade, halo - by their step in the body frame), the wing tip's elevation range and travel,
    the bob, and the hand-back: how far any bone tip jumps on the tick after the clip (inputs back to the clip's
    steady state) against the clip's own last tick."""
    here = os.path.dirname(os.path.abspath(__file__))
    rows = []
    for kind in KINDS:
        bones = load_model(os.path.join(here, "..", "design", "models",
                                        "sunforged_angel_%s_%s.json" % (model_key, kind)))
        tipL = far_point(bones["wing_l_tips"], bones["wing_l_arm"]["pivot"])
        tips = {n: far_point(b, b["pivot"]) for n, b in bones.items()}
        sh = bones["wing_l_arm"]["pivot"]
        for clip in CLIPS:
            fr = run(kind, clip, seed, after=1)
            worst, wb, wt = 0.0, "", 0
            prevR, prevH, prevP = None, set(), None
            elevs, tys, bobs, moves = [], [], [], []
            for t, pz in fr:
                f = fk(bones, pz)
                hid = set(pz.get("_hide", []))
                Rs = {}
                for n in bones:
                    if n in LOCKED:
                        Rs[n] = f(n)[0]
                    else:
                        Rs[n] = rmat([bones[n].get("rotation", [0, 0, 0])[a] + pz.get(n, [0, 0, 0])[a]
                                      for a in range(3)])
                P = {n: [mv(f(n)[0], tips[n])[i] + f(n)[1][i] for i in range(3)] for n in bones if n not in hid}
                if prevR is not None:
                    if t < len(fr) - 1:
                        for n in bones:
                            if n in hid or n in prevH:
                                continue
                            g = geo(prevR[n], Rs[n])
                            if g > worst:
                                worst, wb, wt = g, n, t
                    moves.append(max(math.sqrt(sum((P[n][i] - prevP[n][i]) ** 2 for i in range(3)))
                                     for n in P if n in prevP))
                prevR, prevH, prevP = Rs, hid, P
                if t >= len(fr) - 1:
                    continue
                R, tr = f("wing_l_tips")
                tp = [mv(R, tipL)[i] + tr[i] for i in range(3)]
                Rb, tb = f("body")
                sh_w = [mv(Rb, sh)[i] + tb[i] for i in range(3)]
                rel = mv(mT(Rb), [tp[i] - sh_w[i] for i in range(3)])
                elevs.append(math.degrees(math.atan2(rel[1], math.hypot(rel[0], rel[2]))))
                tys.append(tp[1])
                bobs.append(pz["_move"]["body"][1])
            rows.append((kind, clip, worst, wb, wt, min(elevs), max(elevs), max(tys) - min(tys),
                         (max(bobs) - min(bobs)) / 2.0, moves[-2], moves[-1]))
    print("%-7s %-7s %9s %-17s %13s %8s %8s %16s" % ("kind", "clip", "max step", "(bone @ tick)", "tip elev deg",
                                                    "tip dy", "bob amp", "last/hand-back"))
    for kind, clip, w, wb, wt, e0, e1, dy, ba, ml, mh in rows:
        print("%-7s %-7s %7.1f d  %-17s %5.0f..%-5.0f %7.1fpx %6.2fpx %6.1f / %4.1f px" % (
            kind, clip, w, "%s@%d" % (wb, wt), e0, e1, dy, ba, ml, mh))
    return rows


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--fixture", help="write the sampled-pose fixture here (design/fixtures/angel_clips.json)")
    ap.add_argument("--every", type=int, default=2)
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--stats", action="store_true", help="per clip: max joint step / tick, wing-tip arc, bob")
    ap.add_argument("--model-key", default="b2")
    ap.add_argument("--poses", help="write {kind}_{clip}.json pose files (game_preview --poses @file) here")
    a = ap.parse_args()
    if a.fixture:
        fx = fixture(a.every, a.seed)
        os.makedirs(os.path.dirname(os.path.abspath(a.fixture)), exist_ok=True)
        with open(a.fixture, "w", newline="\n") as f:
            json.dump(fx, f, separators=(",", ":"), sort_keys=True)
            f.write("\n")
        print("wrote", a.fixture)
    if a.poses:
        os.makedirs(a.poses, exist_ok=True)
        for kind in KINDS:
            for clip in CLIPS:
                fr = run(kind, clip, a.seed)
                with open(os.path.join(a.poses, "%s_%s.json" % (kind, clip)), "w", newline="\n") as f:
                    json.dump({"t%02d" % t: rnd(p) for t, p in fr}, f, separators=(",", ":"))
    if a.stats or a.fixture:
        stats(a.model_key, a.seed)


if __name__ == "__main__":
    main()
