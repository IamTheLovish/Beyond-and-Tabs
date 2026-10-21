#!/usr/bin/env python3
"""The dragon rig's animation (unit/modelling/animations/Dragon.java) in Python, frame by frame, so its key moments
can be drawn offline by tools/game_preview.py before a build: the beat at its top, mid-downstroke, bottom and
mid-upstroke, the glide, the idle yawn and head shake, the bite. The same formulas and numbers as the Java (DSL
degrees, y up); keep the two in step when tuning.

    python3 tools/dragon_poses.py > /tmp/dragon_poses.json
    python3 tools/game_preview.py design/models/gravebound_bone_dragon.json --team tabard,tabard_dark \\
        --poses @/tmp/dragon_poses.json --views side,front,rts --out /tmp/dragon_anim.png
"""
import json
import math
import sys

P = dict(beatTicks=40.0, down=0.55, flapUp=40.0, flapDown=-32.0, glideArm=10.0, breathTicks=120.0, neckLag=2.5,
         tailLag=3.0, heavePx=2.2, heaveDeg=2.5, shakeEvery=260.0, yawnEvery=210.0)
NECK_N, TAIL_N = 5, 7
ID = 0


def smooth(x):
    x = max(0.0, min(1.0, x))
    return x * x * (3 - 2 * x)


def offset(i):
    return (i * 37) & 255


def breath(age, i, period):
    p = ((age + offset(i)) / period) % 1.0
    s = p / 0.4 if p < 0.4 else 1 - (p - 0.4) / 0.6
    return smooth(s) * 2 - 1


def wing_beat(age, i, period, down):
    p = ((age + offset(i)) / period) % 1.0
    s = p / down if p < down else 1 - (p - down) / (1 - down)
    return smooth(s) * 2 - 1


def upstroke(age, i, period, down):
    p = ((age + offset(i)) / period) % 1.0
    return 0.0 if p < down else math.sin((p - down) / (1 - down) * math.pi)


def chain(age, i, period, k, lag):
    return math.sin((age + offset(i) - k * lag) / period * math.tau)


def bump(age, i, seed, period, length):
    t = (age + offset(i) + seed * 53) % period
    return math.sin(t / length * math.pi) if t < length else 0.0


def shake(age, i, seed, period, length, cycles):
    t = (age + offset(i) + seed * 53) % period
    if t >= length:
        return 0.0
    return math.sin(t / length * math.pi) * math.sin(t / length * cycles * math.tau)


def wander(age, i, period):
    t = age + offset(i)
    return 0.6 * math.sin(t / period * math.tau) + 0.4 * math.sin(t / (period * 0.37) * math.tau + 1.3)


def pose(age, glide=0.0, idle=1.0, bank=0.0, attack_t=None):
    """{bone: [x, y, z]} DSL degrees plus _move / _scale, as Dragon.animate would add this frame."""
    rot, mv, sc = {}, {}, {}

    def dsl(b, x, y=0.0, z=0.0):
        r = rot.setdefault(b, [0.0, 0.0, 0.0])
        r[0] += x
        r[1] += y
        r[2] += z

    def move(b, x, y, z):
        m = mv.setdefault(b, [0.0, 0.0, 0.0])
        m[0] += x
        m[1] += y
        m[2] += z

    T, D = P["beatTicks"], P["down"]
    beat, beat3, beat6 = wing_beat(age, ID, T, D), wing_beat(age - 3, ID, T, D), wing_beat(age - 6, ID, T, D)
    up = upstroke(age, ID, T, D)
    amp = 1 - 0.85 * glide
    stroke = (beat + 1) * 0.5
    for i, side in enumerate(("left", "right")):
        s = 1 if i == 0 else -1
        armZ = (P["flapUp"] + (P["flapDown"] - P["flapUp"]) * stroke) * amp + P["glideArm"] * glide + 2.5 * glide * math.sin((age + offset(ID)) / 50)
        foreZ = 55 * (beat - beat3) * amp - 16 * up * amp - 3 * glide
        handZ = 70 * (beat3 - beat6) * amp - 10 * up * amp - 5 * glide
        foreY = -28 * up * amp - 3 * glide
        handY = -55 * up * amp - 6 * glide
        dsl("wing_%s_arm" % side, 0, 0, s * armZ)
        dsl("wing_%s_fore" % side, 0, s * foreY, s * foreZ)
        dsl("wing_%s_hand" % side, 0, s * handY, s * handZ)
        dsl("wing_%s_arm" % side, 0, 0, bank * 0.25)
    heave = beat3 * amp
    move("body", 0, P["heavePx"] * heave, 0)
    dsl("body", -P["heaveDeg"] * beat * amp + 3 * glide, 0, bank * 0.8)
    br = breath(age, ID, P["breathTicks"] * (1 - 0.3 * glide))
    swell = 0.5 + 0.5 * br
    sc["ribs"] = [1 + 0.045 * swell, 1 + 0.035 * swell, 1 + 0.012 * swell]
    neckX = neckY = 0.0
    for k in range(NECK_N):
        f = 1 - 0.12 * k
        x = (-1.6 * f * wing_beat(age - P["neckLag"] * (k + 1), ID, T, D) * amp + 1.1 * chain(age, ID, 75, k, 6) * (1 - 0.5 * glide)
             + (1.4 * br if k < 2 else 0) - 1.2 * glide)
        y = 2.4 * chain(age, ID, 110, k, 7) * (1 - 0.6 * glide) - bank * 0.18
        dsl("neck_%d" % (k + 1), x, y, 0)
        neckX += x
        neckY += y
    dsl("head", -0.85 * neckX, -0.85 * neckY, 0)
    dsl("head", -0.6 * br, 0, 0)
    for k in range(TAIL_N):
        f = 0.55 + 0.1 * k
        x = (2.8 * f * wing_beat(age - P["tailLag"] * (k + 1), ID, T, D) * amp + 1.0 * chain(age, ID, 80, k, 6) * (1 - 0.5 * glide)
             - 1.5 * glide * (1 if k < 3 else 0.4))
        y = (2.2 + 0.5 * k) * chain(age, ID, 100, k, 8) * (1 - 0.7 * glide) + bank * 0.12 * (k + 1) / TAIL_N * 2
        dsl("tail_%d" % (k + 1), x, y, 0)
    dsl("spine_1", -0.6 * beat3 * amp, 0.8 * chain(age, ID, 100, 0, 0), 0)
    dsl("spine_2", -0.8 * beat6 * amp, 1.0 * chain(age, ID, 100, 1, 8), 0)
    d4, d7, d10 = (wing_beat(age - t, ID, T, D) * amp for t in (4, 7, 10))
    for i, leg in enumerate(("front_left", "front_right", "hind_left", "hind_right")):
        front = i < 2
        dr = 1 if front else -1
        dsl(leg + "_upper", 2.5 * d4 - 7 * glide * dr)
        dsl(leg + "_lower", 3.5 * d7 * dr)
        dsl(leg + "_foot", 6 * d10 + 3 * wander(age - 5 * i, ID, 90))
    if idle > 0.02:
        sh = shake(age, ID, 2, P["shakeEvery"], 16, 2.5) * idle
        dsl("head", 0, 12 * sh, 6 * sh)
        dsl("neck_%d" % NECK_N, 0, 4 * sh, 0)
        yawn = bump(age, ID, 1, P["yawnEvery"], 34) * idle
        dsl("jaw", -24 * yawn)
        dsl("head", 6 * yawn)
        dsl("neck_%d" % (NECK_N - 1), 3 * yawn)
        look = wander(age, ID, 200) * idle
        for k in range(NECK_N // 2, NECK_N):
            dsl("neck_%d" % (k + 1), 0, 5 * look, 0)
        dsl("head", 0, 6 * look, 0)
        fl = shake(age, ID, 5, 170, 24, 1.5) * idle
        for k in range(TAIL_N // 2, TAIL_N):
            dsl("tail_%d" % (k + 1), 0, 7 * fl, 0)
    if attack_t is not None:
        KEY_P = [0, 0.3, 0.55, 0.75, 1]
        ATTACK = [[0, 0, 0, 0, 0, 0], [-5, -8, 14, -2.0, 3, 10], [7, 10, 34, 5.0, -4, -14], [2, 2, 8, 1.5, -1, -4], [0, 0, 0, 0, 0, 0]]
        k = 0
        while k < len(KEY_P) - 2 and attack_t > KEY_P[k + 1]:
            k += 1
        f = smooth((attack_t - KEY_P[k]) / (KEY_P[k + 1] - KEY_P[k]))
        key = lambda c: ATTACK[k][c] + (ATTACK[k + 1][c] - ATTACK[k][c]) * f
        for i in range(NECK_N):
            dsl("neck_%d" % (i + 1), key(0))
        dsl("head", key(1))
        dsl("jaw", -key(2))
        move("body", 0, 0, -key(3))
        dsl("body", key(4))
        dsl("wing_left_arm", 0, 0, key(5))
        dsl("wing_right_arm", 0, 0, -key(5))
    out = {b: [round(v, 2) for v in r] for b, r in rot.items()}
    out["_move"] = {b: [round(v, 2) for v in m] for b, m in mv.items()}
    out["_scale"] = {b: [round(v, 3) for v in m] for b, m in sc.items()}
    return out


def frames():
    """The moments worth looking at: the beat's four quarters (the phase runs from the entity's offset), a glide, a
    yawn, a head shake mid-swing, the bite's strike."""
    T, D = P["beatTicks"], P["down"]
    base = -offset(ID)                                   # age at which the beat's phase is 0 (the top)
    out = {
        "beat top": pose(base + 0.0 * T, idle=0.0),
        "mid down": pose(base + 0.28 * T, idle=0.0),
        "beat bottom": pose(base + D * T, idle=0.0),
        "mid up": pose(base + (D + (1 - D) * 0.5) * T, idle=0.0),
        "glide": pose(base + 0.3 * T, glide=1.0, idle=0.0, bank=0.0),
        "bank left": pose(base + 0.3 * T, glide=1.0, idle=0.0, bank=-22.0),
    }
    # the yawn's peak: bump(seed 1) peaks at t = 17 into the period
    yawn_age = base - 53 + 17
    out["yawn"] = pose(yawn_age, idle=1.0)
    shake_age = base - 106 + 16 * 0.3
    out["head shake"] = pose(shake_age, idle=1.0)
    out["bite"] = pose(base + 0.3 * T, idle=0.0, attack_t=0.55)
    return out


if __name__ == "__main__":
    which = sys.argv[1:]
    fr = frames()
    if which:
        fr = {k: v for k, v in fr.items() if k in which}
    json.dump(fr, sys.stdout, indent=1)
