#!/usr/bin/env python3
"""The dragon rig's animation (unit/modelling/animations/Dragon.java, pose()) in Python, frame by frame, so its key
moments can be drawn offline by tools/game_preview.py before a build: the beat at its top, mid-downstroke, bottom
and mid-upstroke, the glide, the bank, the idle yawn and head shake, the bite. The same formulas and numbers as the
Java (DSL degrees, y up); keep the two in step when tuning.

    python3 tools/dragon_poses.py > /tmp/dragon_poses.json
    python3 tools/game_preview.py design/models/gravebound_bone_dragon.json --team tabard,tabard_dark \\
        --poses @/tmp/dragon_poses.json --views side,front,rts --out /tmp/dragon_anim.png
"""
import json
import math
import sys

P = dict(beatTicks=36.0, down=0.64, flapUp=32.0, flapDown=-38.0, glideArm=7.0, breathTicks=90.0, neckLag=2.0,
         tailLag=3.0, neckWave=6.0, tailWave=7.0, riseP=9.0, dropPx=6.0, pitchUp=10.0, pitchDown=6.0, shakeEvery=260.0, yawnEvery=210.0)
NECK_N, TAIL_N = 5, 7
ID = 0
KEY_P = [0, 0.2, 0.5, 0.62, 1]
STRIKE = [1.0, 0.6, 0.0, -0.9, -1.3]
ATTACK = [[0, 0, 0, 0, 0], [-3, -8, 20, -1.5, 2], [6, 6, 35, 4.0, -3], [5, 4, 2, 3.5, -2], [0, 0, 0, 0, 0]]


def smooth(x):
    x = max(0.0, min(1.0, x))
    return x * x * (3 - 2 * x)


def offset(i):
    return (i * 37) & 255


def breath(age, i, period):
    p = ((age + offset(i)) / period) % 1.0
    s = p / 0.4 if p < 0.4 else 1 - (p - 0.4) / 0.6
    return smooth(s) * 2 - 1




HOLD = 0.08


def stroke(age, i, period, down):
    """Dragon.stroke: a hold at the top, a fast power stroke that arrives early, a softer recovery."""
    p = ((age + offset(i)) / period) % 1.0
    if p < HOLD:
        return -1.0 - 0.10 * math.sin(p / HOLD * math.pi)
    if p < down:
        u = (p - HOLD) / (down - HOLD)
        return -1 + 2 * (1 - (1 - u) ** 2.4)
    u = (p - down) / (1 - down)
    return 1 - 2 * smooth(u)


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
    """{bone: [x, y, z]} DSL degrees plus _move / _scale, as Dragon.pose would add this frame."""
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
    beat, beat3, beat6 = stroke(age, ID, T, D), stroke(age - 3, ID, T, D), stroke(age - 6, ID, T, D)
    lagH = T * 0.25
    beatH, beatH3 = stroke(age - lagH, ID, T, D), stroke(age - lagH - 3, ID, T, D)
    up0, up2, upH, upF = (upstroke(age - t, ID, T, D) for t in (0, 2, lagH, lagH + 2))
    amp = 1 - 0.85 * glide
    st = (beat + 1) * 0.5
    for i, side in enumerate(("left", "right")):
        s = 1 if i == 0 else -1
        armZ = (P["flapUp"] + (P["flapDown"] - P["flapUp"]) * st) * amp + P["glideArm"] * glide + 2.5 * glide * math.sin((age + offset(ID)) / 50)
        armY = -8 * up0 * amp
        foreZ = 55 * (beat - beat3) * amp - 16 * up2 * amp - 3 * glide
        handZ = 60 * (beatH - beatH3) * amp - 10 * upH * amp - 5 * glide
        foreY = -35 * up2 * amp - 3 * glide
        handY = -60 * upH * amp - 6 * glide
        foreX = -34 * (beat - beat3) * amp
        handX = -45 * (beatH - beatH3) * amp
        dsl("wing_%s_arm" % side, 0, s * armY, s * armZ + bank * 0.25)
        dsl("wing_%s_fore" % side, foreX, s * foreY, s * foreZ)
        dsl("wing_%s_hand" % side, handX, s * handY, s * handZ)
        fill1, fill1b = stroke(age - 2, ID, T, D) - beat, stroke(age - 4, ID, T, D) - stroke(age - 2, ID, T, D)
        fill2, fill2b = stroke(age - 3, ID, T, D) - beat3, stroke(age - 6, ID, T, D) - stroke(age - 3, ID, T, D)
        fill3, fill3b = stroke(age - lagH - 3, ID, T, D) - beatH, stroke(age - lagH - 6, ID, T, D) - stroke(age - lagH - 3, ID, T, D)
        belly, bellyH = 9 * beat * amp, 9 * beatH * amp
        flut, w0 = 4 * up0 * amp, (age + offset(ID)) * 1.9
        dsl("mem_%s_1" % side, 28 * fill1 * amp + 0.5 * belly - 4 * glide)
        dsl("mem_%s_1b" % side, 30 * fill1b * amp + belly + flut * math.sin(w0))
        dsl("mem_%s_2" % side, 34 * fill2 * amp + 0.5 * belly - 5 * glide)
        dsl("mem_%s_2b" % side, 34 * fill2b * amp + belly + flut * math.sin(w0 + 1.1))
        for k in range(4):
            dsl("web_%s_%d" % (side, k + 2), 36 * fill3 * amp + 0.5 * bellyH - 5 * glide)
            dsl("web_%s_%db" % (side, k + 2), 36 * fill3b * amp + bellyH + flut * math.sin(w0 + 2.2 + 0.8 * k))
        dsl("mem_%s_0" % side, 14 * fill1 * amp + 0.4 * belly - 3 * glide)
        for k in range(3):
            dsl("wing_%s_finger_%d" % (side, k + 2), 0, s * (18 + 14 * k) * upF * amp, 0)
    heave = beat3 * amp
    move("body", 0, P["riseP"] * heave if heave > 0 else P["dropPx"] * heave, 0)
    pitch0 = (P["pitchUp"] if beat > 0 else P["pitchDown"]) * beat * amp
    dsl("body", pitch0 + 3 * glide, 0, bank * 0.8)
    br = breath(age, ID, P["breathTicks"] * (1 - 0.3 * glide))
    swell = 0.5 + 0.5 * br
    sc["ribs"] = [1 + 0.10 * swell, 1 + 0.08 * swell, 1 + 0.02 * swell]
    neckX = neckY = 0.0
    for k in range(NECK_N):
        f = 1 - 0.1 * k
        x = (-2.6 * f * stroke(age - P["neckLag"] * (k + 1), ID, T, D) * amp + P["neckWave"] * chain(age, ID, 64, k, P["neckLag"]) * (1 - 0.5 * glide)
             + (2.5 * br if k < 2 else 0) - (0.35 * pitch0 if k < 2 else 0) - 1.2 * glide)
        y = 2.4 * chain(age, ID, 110, k, 7) * (1 - 0.6 * glide) - bank * 0.18
        dsl("neck_%d" % (k + 1), x, y, 0)
        neckX += x
        neckY += y
    dsl("head", -0.85 * neckX, -0.85 * neckY, 0)
    dsl("head", -0.6 * br, 0, 0)
    for k in range(TAIL_N):
        f = 0.55 + 0.1 * k
        x = (5 * f * stroke(age - T * 0.25 - P["tailLag"] * k, ID, T, D) * amp + 1.0 * chain(age, ID, 80, k, 6) * (1 - 0.5 * glide)
             - 2.5 * glide * (1 if k < 3 else 0.5))
        y = (P["tailWave"] + 0.6 * k) * chain(age, ID, 72, k, P["tailLag"]) * (1 - 0.6 * glide) + bank * 0.3 * (k + 1) / TAIL_N
        x -= 0.12 * pitch0
        dsl("tail_%d" % (k + 1), x, y, 0)
    dsl("spine_1", -0.6 * beat3 * amp, 1.0 * chain(age, ID, 72, 0, 0), 0)
    dsl("spine_2", -0.8 * beat6 * amp, 1.4 * chain(age, ID, 72, 1, P["tailLag"]), 0)
    d4, d7, d10 = (stroke(age - t, ID, T, D) * amp for t in (4, 7, 10))
    swingG = 5 * wander(age, ID, 160) * glide
    for i, leg in enumerate(("front_left", "front_right", "hind_left", "hind_right")):
        front = i < 2
        dr = 1 if front else -1
        dsl(leg + "_upper", -4 * d4 - 6 * glide * dr + swingG)
        dsl(leg + "_lower", -5 * d7 * dr)
        dsl(leg + "_foot", 6 * d10 + 3 * wander(age - 5 * i, ID, 90))
    if idle > 0.02:
        sh = shake(age, ID, 2, P["shakeEvery"], 16, 2.5) * idle
        sh1 = shake(age - 1, ID, 2, P["shakeEvery"], 16, 2.5) * idle
        dsl("head", 0, 25 * sh, 6 * sh)
        dsl("neck_%d" % NECK_N, 0, 8 * sh1, 0)
        dsl("neck_%d" % (NECK_N - 1), 0, 4 * sh1, 0)
        yawn = bump(age, ID, 1, P["yawnEvery"], 34) * idle
        dsl("jaw", -50 * yawn)
        dsl("head", 12 * yawn)
        for k in range(NECK_N // 2, NECK_N):
            dsl("neck_%d" % (k + 1), 4 * yawn)
        look = wander(age, ID, 200) * idle
        for k in range(NECK_N // 2, NECK_N):
            dsl("neck_%d" % (k + 1), 0, 5 * look, 0)
        dsl("head", 0, 6 * look, 0)
        fl = shake(age, ID, 5, 170, 24, 1.5) * idle
        for k in range(TAIL_N // 2, TAIL_N):
            dsl("tail_%d" % (k + 1), 0, 7 * fl, 0)
    if attack_t is not None and 0 <= attack_t < 1:
        k = 0
        while k < len(KEY_P) - 2 and attack_t > KEY_P[k + 1]:
            k += 1
        f = smooth((attack_t - KEY_P[k]) / (KEY_P[k + 1] - KEY_P[k]))
        key = lambda c: ATTACK[k][c] + (ATTACK[k + 1][c] - ATTACK[k][c]) * f
        for i in range(NECK_N):
            dsl("neck_%d" % (i + 1), key(0) * STRIKE[min(i, len(STRIKE) - 1)])
        dsl("head", key(1))
        dsl("jaw", -key(2))
        move("body", 0, 0, -key(3))
        dsl("body", key(4))
    out = {b: [round(v, 2) for v in r] for b, r in rot.items()}
    out["_move"] = {b: [round(v, 2) for v in m] for b, m in mv.items()}
    out["_scale"] = {b: [round(v, 3) for v in m] for b, m in sc.items()}
    return out


def _at_phase(phase, pred, span=40):
    """The first age at beat phase `phase` (0 = top) where pred(age) is largest over `span` beats."""
    T = P["beatTicks"]
    base = -offset(ID) + phase * T
    return max((base + n * T for n in range(span)), key=pred)


def strip(n=8):
    """n evenly spaced frames across one beat (phase 0 = the top of the hold), so the hold, the snap and the
    recovery can be read side by side."""
    T = P["beatTicks"]
    base = -offset(ID)
    return {"beat %d/%d" % (i, n): pose(base + T * i / n, idle=0.0) for i in range(n)}


def frames():
    """The moments worth looking at: the beat's four quarters, a glide, a bank, then the idles and the bite drawn at
    the same mid-downstroke phase (the wings level) so what the head, neck and jaw do is what differs."""
    T, D = P["beatTicks"], P["down"]
    base = -offset(ID)                                   # age at which the beat's phase is 0 (the top)
    out = {
        "beat top": pose(base, idle=0.0),
        "mid down": pose(base + (HOLD + (D - HOLD) * 0.55) * T, idle=0.0),
        "beat bottom": pose(base + D * T, idle=0.0),
        "mid up": pose(base + (D + (1 - D) * 0.5) * T, idle=0.0),
        "glide": pose(base + 0.3 * T, glide=1.0, idle=0.0),
        "bank left": pose(base + 0.3 * T, glide=1.0, idle=0.0, bank=-22.0),
    }
    yawn_age = _at_phase(0.3, lambda a: bump(a, ID, 1, P["yawnEvery"], 34))
    out["yawn"] = pose(yawn_age, idle=1.0)
    shake_age = _at_phase(0.3, lambda a: shake(a, ID, 2, P["shakeEvery"], 16, 2.5))
    out["head shake"] = pose(shake_age, idle=1.0)
    out["bite"] = pose(base + 0.3 * T, idle=0.0, attack_t=0.5)
    return out


if __name__ == "__main__":
    which = sys.argv[1:]
    if which == ["--strip"]:
        json.dump(strip(), sys.stdout, indent=1)
        sys.exit(0)
    fr = frames()
    if which:
        fr = {k: v for k, v in fr.items() if k in which}
    json.dump(fr, sys.stdout, indent=1)
