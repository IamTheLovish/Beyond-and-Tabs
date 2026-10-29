#!/usr/bin/env python3
"""The dragon rig's animation (unit/modelling/animations/Dragon.java, pose()) in Python, frame by frame, so its key
moments can be drawn offline by tools/game_preview.py before a build. The same formulas and numbers as the Java (DSL
degrees, y up); keep the two in step when tuning. r13 (lovish, Oct 22: "the dragon animation was bad"): the phase
tables in design/anims/bone_dragon_flight.md - a heavy 40 % downstroke and a slow folding recovery, flap bursts
between glides, three-strip sails that billow, an eight-link neck that holds an S from above, a ten-link tail as
rudder and counterweight, hover / flare / take-off as their own states.

    python3 tools/dragon_poses.py > /tmp/dragon_poses.json            # the key frames
    python3 tools/dragon_poses.py --strip > /tmp/strip.json            # one cruise rhythm, a frame every 6 ticks
    python3 tools/dragon_poses.py --turn > /tmp/turn.json              # a synthetic turn (the bank)
    python3 tools/game_preview.py design/models/gravebound_bone_dragon.json --team tabard,tabard_dark \\
        --poses @/tmp/dragon_poses.json --views side,rts,back --translucent "mem_,web_:0.88" --out /tmp/dragon_anim.png
"""
import json
import math
import sys

P = dict(beatTicks=52.0, down=0.34, hold=0.06, flapUp=38.0, flapDown=-42.0, glideArm=6.0,
         burstBeats=3.0, glideBeats=2.0,
         hoverRate=0.85, takeoffRate=0.8,
         breathTicks=90.0, neckLag=2.5, tailLag=5.0, neckSway=12.0, tailWave=6.5,
         riseP=3.3, dropPx=2.0, pitchUp=9.0, pitchDown=6.0,
         billow=13.0,
         shakeEvery=260.0, yawnEvery=210.0, attackTicks=22.0, attackWindup=8.0,
         bankMax=50.0, bankPerDeg=5.0, bankOmega=0.5, headingTau=5.0, tailBankLag=4.0, bankHover=0.45, bankGround=0.12,
         climbSpeed=0.06)
NECK_N, TAIL_N = 8, 10
ID = 0
# the bite's keys in ticks from the swing moment (Dragon.KEY_T): the wind-up before 0, the hold and snap after
KEY_T = [-8, -2, 0, 6, 10, 22]
# {neck x (per link, spread by STRIKE), head x, jaw open, body forward px, body x, wing (0 none, + up to the top, - down)}
ATTACK = [[0, 0, 0, 0, 0, 0], [-5, -16, 30, -2.0, 4, 1.0], [7, 10, 44, 4.0, -4, -1.0], [7, 16, 44, 4.0, -4, -0.6],
          [5, 8, 2, 3.0, -2, 0.0], [0, 0, 0, 0, 0, 0]]
# how the bite's neck key spreads down the eight links: the base rises, the far links drop, so the S unrolls forward
STRIKE = [0.8, 0.6, 0.35, 0.1, -0.2, -0.5, -0.75, -0.9]
# the neck's lateral S: the wave's phase step per link (radians) - about one wavelength over the eight links, so a
# travelling wave always leaves an S from above, never a straight rod
NECK_K = 0.8
# the sail's three chord strips: how much of the billow each hinge takes (they add up - the hem curls the most)
STRIP_W = [0.45, 0.8, 1.0]


def smooth(x):
    x = max(0.0, min(1.0, x))
    return x * x * (3 - 2 * x)


def offset(i):
    return (i * 37) & 255


def breath(age, i, period):
    p = ((age + offset(i)) / period) % 1.0
    s = p / 0.4 if p < 0.4 else 1 - (p - 0.4) / 0.6
    return smooth(s) * 2 - 1


def shape(p):
    """Dragon.shape: the stroke over one beat, -1 (top) .. 1 (bottom), p in [0, 1): a short hold at the top (a
    little more rise - the anticipation), the HEAVY downstroke (snaps off the top, loads, ~34 % of the beat), and a
    slow recovery (60 %) that lingers low - the wrist folds first and the arm rises last."""
    H, D = P["hold"], P["down"]
    p %= 1.0
    if p < H:
        return -1.0 - 0.08 * math.sin(p / H * math.pi)
    if p < D:
        u = (p - H) / (D - H)
        return -1.0 + 2.0 * (u * u * (2.2 - 1.2 * u))        # eases off the top, strikes INTO the bottom
    u = (p - D) / (1.0 - D)
    return 1.0 - 2.0 * smooth(u)


def up_of(p):
    """0..1 through the recovery (the folds), 0 on the downstroke."""
    p %= 1.0
    D = P["down"]
    return math.sin((p - D) / (1 - D) * math.pi) if p >= D else 0.0


def down_of(p):
    """0..1 through the power stroke."""
    p %= 1.0
    H, D = P["hold"], P["down"]
    return math.sin((p - H) / (D - H) * math.pi) if H <= p < D else 0.0


def rhythm(ph):
    """Dragon.rhythm: the cruise's glide weight at beat-phase ph (beats): burstBeats beats of flapping, then a glide
    of glideBeats beat-lengths. The glide fades in over the last recovery (the wing comes up only to the glide angle)
    and out over the last 0.3 beat (the arm rises from the glide to the top - the anticipation of the first beat)."""
    B, G = P["burstBeats"], P["glideBeats"]
    c = ph % (B + G)
    if c < B - 0.45:
        return 0.0
    if c < B:
        return smooth((c - (B - 0.45)) / 0.45)
    if c < B + G - 0.3:
        return 1.0
    return 1.0 - smooth((c - (B + G - 0.3)) / 0.3)


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


def wrap(d):
    return (d + 180.0) % 360.0 - 180.0


def new_state():
    """Dragon.newState's bank part: [bank, heading, bank velocity, tail A, tail B, seeded]."""
    return dict(bank=0.0, heading=0.0, vel=0.0, tailA=0.0, tailB=0.0, seeded=False)


def bank_step(st, yaw, dt, scale=1.0):
    """Dragon.bankStep: the heading filter, the critically damped roll, the tail's two lags. Same numbers."""
    if not st["seeded"]:
        st["heading"], st["seeded"] = yaw, True
    if dt <= 0:
        return st["bank"]
    d = wrap(yaw - st["heading"])
    a = 1 - math.exp(-dt / P["headingTau"])
    st["heading"] = wrap(st["heading"] + d * a)
    rate = d * a / dt
    want = max(-P["bankMax"], min(P["bankMax"], rate * P["bankPerDeg"])) * scale
    n = max(1, math.ceil(dt / 0.5))
    h, w = dt / n, P["bankOmega"]
    x, v = st["bank"], st["vel"]
    for _ in range(n):
        v += (w * w * (want - x) - 2 * w * v) * h
        x += v * h
    st["bank"], st["vel"] = x, v
    b = 1 - math.exp(-dt / P["tailBankLag"])
    st["tailA"] += (x - st["tailA"]) * b
    st["tailB"] += (st["tailA"] - st["tailB"]) * b
    return x


def pose(ph, age, glide=0.0, idle=0.0, bank=0.0, tailA=None, tailB=None, climb=0.0, flare=0.0, takeoff=0.0,
         attack_t=None, hurt=0, death=0, look_yaw=0.0, look_pitch=0.0, perch=0.0):
    """{bone: [x, y, z]} DSL degrees plus _move / _scale, as Dragon.pose adds them this frame.
    ph: the beat phase in beats (Dragon keeps it per entity: it advances dt / the current beat length, so a change of
    rate never jumps the wings); age: ticks (breath, sway, idle); glide 0..1 (speed), idle 0..1 (hovering), bank deg,
    tailA / tailB the tail's lagged banks, climb -1..1, flare 0..1 (braking into a hover), takeoff 0..1, attack_t the
    ticks since the bite's swing moment (negative in the wind-up), perch 0..1 (on the ground after a dive)."""
    rot, mv, sc = {}, {}, {}
    tailA = bank if tailA is None else tailA
    tailB = bank if tailB is None else tailB

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

    T = P["beatTicks"] * (1 + (P["hoverRate"] - 1) * idle) * (1 + (P["takeoffRate"] - 1) * takeoff)
    lag = lambda ticks: ph - ticks / T                         # the beat phase `ticks` ago
    # the glide weight: the cruise rhythm (only cruising - a hover, a climb, a take-off and a flare beat on), and the
    # speed's own glide with a corrective beat every ~8 s
    cruise = (1 - idle) * (1 - max(0.0, climb)) * (1 - takeoff) * (1 - flare)
    corrective = bump(age, ID, 6, 160, 44)
    gs = glide * (1 - corrective)

    def gw_at(p):
        return max(rhythm(p) * cruise, gs)

    gw0 = gw_at(ph)
    amp = (1 + 0.3 * max(0.0, climb)) * (1 + 0.25 * takeoff) * (1 - 0.15 * idle) * (1 - 0.35 * flare) * (1 - 0.85 * perch)
    s0 = shape(ph)
    bankF = min(1.0, abs(bank) / P["bankMax"])
    ripple = lambda j, k: math.sin((age + offset(ID)) / 26.0 * math.tau - 0.9 * j - 0.7 * k)   # the glide's slow billow

    # the attack's wing override (wind-up: up to the top and held; strike: a downstroke lands with the bite)
    atk_w, atk_arm = 0.0, 0.0
    if attack_t is not None and -P["attackWindup"] <= attack_t < P["attackTicks"]:
        t = attack_t * (-KEY_T[0] / P["attackWindup"]) if attack_t < 0 else attack_t * (KEY_T[-1] / P["attackTicks"])
        k = 0
        while k < len(KEY_T) - 2 and t > KEY_T[k + 1]:
            k += 1
        f = smooth((t - KEY_T[k]) / (KEY_T[k + 1] - KEY_T[k]))
        wkey = ATTACK[k][5] + (ATTACK[k + 1][5] - ATTACK[k][5]) * f
        atk_w = min(1.0, abs(wkey) * 1.5) * 0.8
        atk_arm = P["flapUp"] if wkey > 0 else P["flapDown"]

    for i, side in enumerate(("left", "right")):
        s = 1 if i == 0 else -1
        inner = bankF if s * bank < 0 else 0.0
        outer = bankF if s * bank > 0 else 0.0
        a = amp * (1 - 0.30 * inner + 0.10 * outer)
        # the arm leads; the elbow 3 ticks behind; the wrist (hand) 0.15 beat behind; the fingers 0.08 beat more
        sA, sF, sH, sT = s0, shape(lag(4)), shape(ph - 0.18), shape(ph - 0.28)
        gA, gF, gH, gT = gw0, gw_at(lag(4)), gw_at(ph - 0.18), gw_at(ph - 0.28)
        up0, upF, upH, upT = up_of(ph), up_of(lag(4)), up_of(ph - 0.18), up_of(ph - 0.28)
        dn0 = down_of(ph)
        flapZ = (P["flapUp"] + (P["flapDown"] - P["flapUp"]) * (sA + 1) * 0.5) * a
        armZ = flapZ * (1 - gA) + (P["glideArm"] + 4.0 * math.sin((age + offset(ID)) / 50)) * gA
        armZ = armZ * (1 - atk_w) + atk_arm * atk_w
        armZ += -16 * inner + 6 * outer + 16 * flare + 4.0 * gA * math.sin((age + offset(ID)) / 70.0 + 0.6) * s
        # the stroke plane: cruising the arm sweeps back a little on the recovery; hovering it beats FORWARD and down
        # and comes back on the recovery (the hover's figure of eight); a flare holds the wings forward as brakes
        armY = (-8 * up0 * (1 - idle) + idle * (18 * dn0 - 14 * up0)) * a * (1 - gA) + 22 * flare
        # the elbow bends against the stroke (it trails the arm by 3 ticks) and folds back through the recovery
        foreZ = (40 * (sA - sF) - 7 * upF) * a * (1 - gF) - 3 * gF - 7 * inner + 4 * outer
        foreY = -22 * upF * a * (1 - gF) - 3 * gF - 22 * inner + 2 * outer + 10 * flare
        # the wrist trails the elbow (the tips still rising as the downstroke begins), folds back hardest on the
        # recovery so the wing comes up narrow, and opens again at the top
        handZ = (40 * (sF - sH) - 4 * upH) * a * (1 - gH) - (6 - 4 * math.sin((age + offset(ID)) / 50 - 1.2)) * gH - 5 * inner + 3 * outer + 6 * flare
        handY = -36 * upH * a * (1 - gH) - 5 * gH - 20 * inner + 3 * outer + 12 * flare
        # the twist (pronation on the power stroke, supination on the recovery)
        foreX = -15 * (sA - sF) * a * (1 - gF)
        handX = -20 * (sF - sH) * a * (1 - gH)
        dsl("wing_%s_arm" % side, 0, s * armY, s * armZ + bank * 0.15)
        dsl("wing_%s_fore" % side, foreX, s * foreY, s * foreZ)
        dsl("wing_%s_hand" % side, handX, s * handY, s * handZ)
        # the fingers: closed toward the leading one on the recovery (latest of all), spread in a flare, and each
        # tip trails its finger - the trailing fingers lag more (a fan that ripples open)
        for k in range(3):
            spread = (9 + 8 * k) * upT * a * (1 - gT) - (4 + 3 * k) * flare
            lift = 12 * (sH - sT) * a * (1 - gT) * (1 + 0.4 * k)
            dsl("wing_%s_finger_%d" % (side, k + 2), 0, s * spread, s * lift)
        # the sail: each chord strip hinges on the one ahead; the load (the stroke's speed, a tick or two late per
        # strip) billows it UP behind the power stroke and cups it DOWN behind the recovery; the glide holds it
        # lifted with a slow ripple; the flare cups it hard (air brakes). x > 0 tips a strip's far edge down.
        def sail(name_fmt, lead, k, wbase=1.0):
            for j, suf in enumerate(("", "b", "c")):
                p1, p0 = lead - (1 + 1.5 * j) / T, lead - (3 + 1.5 * j) / T
                v = (shape(p1) - shape(p0)) / 0.30                   # ~ +1 at the power stroke's peak speed
                g = gw_at(p1)
                x = (-P["billow"] * v * a * (1 - g) - 8 * g + 6.0 * g * ripple(j, k) + 7 * flare - 3 * (1 - g) * (1 - idle)) * STRIP_W[j] * wbase
                x += 4.0 * up_of(p1) * (1 - g) * math.sin((age + offset(ID)) * 1.9 + j + k) * (j == 2)   # the hem flutters
                dsl(name_fmt + suf, x)
        sail("mem_%s_1" % side, ph, 0, 0.9)
        sail("mem_%s_2" % side, lag(4), 1)
        for k in range(4):
            name = "web_%s_%d" % (side, k + 2)
            p1, p0 = ph - 0.18 - (2 + k) / T, ph - 0.18 - (4 + k) / T
            v = (shape(p1) - shape(p0)) / 0.30
            g = gw_at(p1)
            dsl(name, (-0.9 * P["billow"] * v * a * (1 - g) - 3 * g + 3 * g * ripple(1, k + 2) + 5 * flare))
            dsl(name + "b", (-1.4 * P["billow"] * v * a * (1 - g) - 6 * g + 6 * g * ripple(2, k + 2) + 7 * flare
                             + 4.0 * up_of(p1) * (1 - g) * math.sin((age + offset(ID)) * 1.9 + 2.2 + 0.8 * k)))
        v1 = (shape(lag(2)) - shape(lag(4))) / 0.30
        dsl("mem_%s_0" % side, -0.5 * P["billow"] * v1 * a * (1 - gw0) - 3 * gw0 + 3 * gw0 * ripple(0, 0))

    # ---- the body: rises on the downstroke a quarter beat late and sinks through the recovery; nose up at the
    # bottom; the states pitch it (a climb, a hover, a take-off, a flare: nose up); the bank rolls it
    sB = shape(lag(4))
    heave = sB * amp * (1 - gw_at(lag(4)))
    move("body", 0, P["riseP"] * heave if heave > 0 else P["dropPx"] * heave, 0)
    pitch0 = (P["pitchUp"] if s0 > 0 else P["pitchDown"]) * s0 * amp * (1 - gw0)
    roll = bank * 0.85 + 4.0 * gw0 * math.sin((age + offset(ID)) / 70.0)   # a glide rocks: small roll corrections
    statePitch = 10 * climb + 16 * idle + 25 * takeoff * (1 - idle) + 26 * flare + 2 * gw0
    dsl("body", pitch0 + statePitch, 0, roll)

    # ---- breath, always ------------------------------------------------------------------------------------------
    br = breath(age, ID, P["breathTicks"] * (1 - 0.3 * glide))
    swell = 0.5 + 0.5 * br
    sc["ribs"] = [1 + 0.10 * swell, 1 + 0.08 * swell, 1 + 0.02 * swell]

    # ---- the neck: eight links. Vertical: the heave lags down the chain (the base flexes down as the body rises, the
    # head counters it and stays level). Lateral: a travelling wave about one wavelength long, so from above it is
    # ALWAYS an S (r12's five links swayed together and read as a rod). The bank: counter-roll, and a curve INTO the
    # turn that grows toward the head (the head leads). Hover: up in a tall S, the head looking down.
    neckX = neckY = 0.0
    neckRoll = -roll * 0.6 / NECK_N
    t0 = (age + offset(ID)) / 120.0 * math.tau
    for k in range(NECK_N):
        f = 1 - 0.06 * k
        sk = shape(lag(P["neckLag"] * (k + 1)))
        x = (-2.2 * f * sk * amp * (1 - gw_at(lag(P["neckLag"] * (k + 1))))
             + (2.0 * br if k < 3 else 0) - (0.3 * pitch0 if k < 3 else 0)
             - 1.0 * gw0 - 0.6 * statePitch / NECK_N * (1.6 if k < 4 else 0.4)
             + (3.0 * idle if k < 3 else -2.0 * idle))
        y = P["neckSway"] * (1 - 0.4 * gw0) * math.sin(t0 - NECK_K * k) + bank * 0.20 * (k + 1) / NECK_N
        dsl("neck_%d" % (k + 1), x, y, neckRoll)
        neckX += x
        neckY += y
    dsl("head", -0.65 * neckX, -0.85 * neckY, 0)
    dsl("head", -0.6 * br - 8 * idle, bank * 0.18, -roll * 0.3)

    # ---- the tail: ten links. Lateral: a travelling wave, slower than the neck's, half a wavelength over the tail,
    # its base swinging AGAINST the neck's base (the counterweight); the rudder curls it into a bank on the two lagged
    # banks. Vertical: the heave reaches it a quarter beat late and runs down it; it streams level in a glide, hangs
    # low in a hover, a climb, a take-off and a flare.
    for k in range(TAIL_N):
        f = 0.5 + 0.08 * k
        sk = shape(ph - 0.25 - P["tailLag"] * k / T)
        x = (6.0 * f * sk * amp * (1 - gw_at(ph - 0.25))
             + 1.2 * chain(age, ID, 90, k, 6) * (1 - 0.5 * gw0)
             - 2.0 * gw0 * (1 if k < 4 else 0.5)
             + (1.8 * idle + 2.2 * flare + 1.5 * takeoff + 2.0 * max(0.0, climb)) * (1.2 if k < 4 else 0.6))
        x -= 0.12 * pitch0
        tb = tailA if k < TAIL_N // 2 else tailB
        y = ((P["tailWave"] + 0.4 * k) * (1 - 0.5 * gw0) * math.sin(t0 * 120 / 150 + math.pi - 0.45 * k)
             - tb * 0.45 * (k + 1) / TAIL_N)
        dsl("tail_%d" % (k + 1), x, y, -roll * 0.05)
    dsl("spine_1", -0.6 * shape(lag(4)) * amp * (1 - gw0), 1.2 * math.sin(t0 * 120 / 150 + math.pi + 0.45), 0)
    dsl("spine_2", -0.8 * shape(lag(6)) * amp * (1 - gw0), 1.4 * math.sin(t0 * 120 / 150 + math.pi + 0.2), 0)

    # ---- the legs: tucked in flight, trailing the heave; dangling in a hover / take-off, reaching in a flare -------
    hang = max(max(idle, takeoff, flare) * (1 - perch), 0.7 * perch)   # perched: stood on them
    d4, d7 = (shape(lag(t)) * amp * (1 - gw0) for t in (4, 7))
    swingG = 4 * wander(age, ID, 160) * gw0
    for i, leg in enumerate(("front_left", "front_right", "hind_left", "hind_right")):
        front = i < 2
        sway = (4 * wander(age - 9 * i, ID, 70) + 12 * shape(lag(6 + 2 * i))) * hang
        if front:
            dsl(leg + "_upper", -4 * d4 - 6 * gw0 + swingG + 40 * hang + sway)
            dsl(leg + "_lower", -5 * d7 - 70 * hang)
            dsl(leg + "_foot", 6 * d4 + 3 * wander(age - 5 * i, ID, 90) - 10 * hang)
        else:
            dsl(leg + "_upper", -4 * d4 + 6 * gw0 + swingG + 55 * hang + 15 * flare + sway)
            dsl(leg + "_lower", 5 * d7 + 25 * hang)
            dsl(leg + "_foot", 6 * d4 + 3 * wander(age - 5 * i, ID, 90) - 55 * hang)

    # ---- perched (on the ground after a dive): the wings fold along the flanks, the body levels, the neck rises ----
    if perch > 0.01:
        for side, s in (("left", 1), ("right", -1)):
            dsl("wing_%s_arm" % side, 0, s * -10 * perch, s * -22 * perch)
            dsl("wing_%s_fore" % side, 0, s * -55 * perch, s * 14 * perch)
            dsl("wing_%s_hand" % side, 0, s * -65 * perch, 0)
            for k in range(3):
                dsl("wing_%s_finger_%d" % (side, k + 2), 0, s * (16 + 8 * k) * perch, 0)
        dsl("body", -statePitch * perch, 0, 0)
        for k in range(min(3, NECK_N)):
            dsl("neck_%d" % (k + 1), 5 * perch, 0, 0)

    # ---- idle behaviours (fade with speed) --------------------------------------------------------------------------
    if idle > 0.02:
        sh = shake(age, ID, 2, P["shakeEvery"], 16, 2.5) * idle
        sh1 = shake(age - 1, ID, 2, P["shakeEvery"], 16, 2.5) * idle
        dsl("head", 0, 25 * sh, 6 * sh)
        dsl("neck_%d" % NECK_N, 0, 6 * sh1, 0)
        dsl("neck_%d" % (NECK_N - 1), 0, 4 * sh1, 0)
        dsl("neck_%d" % (NECK_N - 2), 0, 2 * sh1, 0)
        yawn = bump(age, ID, 1, P["yawnEvery"], 34) * idle
        dsl("jaw", -50 * yawn)
        dsl("head", 12 * yawn)
        for k in range(NECK_N // 2, NECK_N):
            dsl("neck_%d" % (k + 1), 3 * yawn)
        look = wander(age, ID, 200) * idle
        for k in range(NECK_N // 2, NECK_N):
            dsl("neck_%d" % (k + 1), 0, 3.5 * look, 0)
        dsl("head", 0, 6 * look, 0)
        fl = shake(age, ID, 5, 170, 24, 1.5) * idle
        for k in range(TAIL_N // 2, TAIL_N):
            dsl("tail_%d" % (k + 1), 0, 5 * fl, 0)

    # ---- the look: the last links turn first, the head finishes it ------------------------------------------------
    yaw, pitch = max(-55, min(55, look_yaw)), max(-35, min(35, look_pitch))
    for k in range(max(0, NECK_N - 4), NECK_N):
        dsl("neck_%d" % (k + 1), -pitch * 0.09, -yaw * 0.11, 0)
    dsl("head", -pitch * 0.45, -yaw * 0.5, 0)

    # ---- the bite -------------------------------------------------------------------------------------------------
    if attack_t is not None and -P["attackWindup"] <= attack_t < P["attackTicks"]:
        t = attack_t * (-KEY_T[0] / P["attackWindup"]) if attack_t < 0 else attack_t * (KEY_T[-1] / P["attackTicks"])
        k = 0
        while k < len(KEY_T) - 2 and t > KEY_T[k + 1]:
            k += 1
        f = smooth((t - KEY_T[k]) / (KEY_T[k + 1] - KEY_T[k]))
        key = lambda c: ATTACK[k][c] + (ATTACK[k + 1][c] - ATTACK[k][c]) * f
        for n in range(NECK_N):
            dsl("neck_%d" % (n + 1), key(0) * STRIKE[n])
        dsl("head", key(1))
        dsl("jaw", -key(2))
        move("body", 0, 0, -key(3))
        dsl("body", key(4))

    # ---- the hit flinch and the death fold ------------------------------------------------------------------------
    if hurt > 0 and death == 0:
        h = math.sin(hurt / 10 * math.pi)
        dsl("head", 4 * h, -14 * h, 6 * h)
        dsl("neck_1", -5 * h, -4 * h, 0)
        dsl("body", 0, 0, 5 * h)
    if death > 0:
        d = smooth(min(1.0, (death + 1) / 14))
        for i, side in enumerate(("left", "right")):
            s = 1 if i == 0 else -1
            dsl("wing_%s_arm" % side, 0, 0, s * -30 * d)
            dsl("wing_%s_fore" % side, 0, s * -60 * d, s * 20 * d)
            dsl("wing_%s_hand" % side, 0, s * -70 * d, 0)
            for k in range(3):
                dsl("wing_%s_finger_%d" % (side, k + 2), 0, s * (20 + 10 * k) * d, 0)
        for k in range(NECK_N):
            dsl("neck_%d" % (k + 1), -6 * d, 2 * d, 0)
        dsl("head", 10 * d)
        dsl("jaw", -18 * d)
        for k in range(TAIL_N):
            dsl("tail_%d" % (k + 1), 2 * d)

    out = {b: [round(v, 2) for v in r] for b, r in rot.items()}
    out["_move"] = {b: [round(v, 2) for v in m] for b, m in mv.items()}
    out["_scale"] = {b: [round(v, 3) for v in m] for b, m in sc.items()}
    return out


AGE0 = 100.0   # any age; the phase is what places the wings


def frames():
    """The key frames of every state (the phase tables' rows)."""
    out = {}
    for name, p in (("top", 0.03), ("early down", 0.14), ("mid down", 0.24), ("bottom", 0.42), ("early up", 0.58),
                    ("mid up", 0.74), ("late up", 0.90)):
        out["flap " + name] = pose(p, AGE0 + p * P["beatTicks"])
    out["glide"] = pose(3.6, AGE0 + 3.6 * P["beatTicks"])
    out["glide exit"] = pose(4.85, AGE0 + 4.85 * P["beatTicks"])
    out["bank right"] = pose(0.24, AGE0, bank=40.0, tailA=30.0, tailB=20.0)
    out["bank left glide"] = pose(3.6, AGE0, bank=-40.0, tailA=-30.0, tailB=-20.0)
    out["hover down"] = pose(0.24, AGE0, idle=1.0)
    out["hover up"] = pose(0.70, AGE0, idle=1.0)
    out["attack windup"] = pose(0.7, AGE0, idle=1.0, attack_t=-3.0)
    out["attack strike"] = pose(0.2, AGE0, idle=1.0, attack_t=3.0)
    out["takeoff"] = pose(0.30, AGE0, takeoff=1.0)
    out["flare"] = pose(0.10, AGE0, flare=1.0, idle=0.3)
    out["perched"] = pose(0.30, AGE0, idle=1.0, perch=1.0)
    return out


def strip(state="cruise", every=4.0, n=None):
    """A frame every `every` ticks: cruise = one whole rhythm (bursts then the glide), hover = two beats."""
    T = P["beatTicks"]
    if state == "hover":
        T *= P["hoverRate"]
        n = n or int(2 * T / every)
        return {"t%03d" % i: pose(i * every / T, AGE0 + i * every, idle=1.0) for i in range(n)}
    n = n or int((P["burstBeats"] + P["glideBeats"]) * T / every)
    return {"t%03d" % i: pose(i * every / T, AGE0 + i * every) for i in range(n)}


def turn(yaw_rate=6.0, hold=24, n=12, every=4):
    """A synthetic turn: the heading turns `yaw_rate` deg/tick for `hold` ticks then stops; a frame every `every`
    ticks through the roll-in, the hold and the settle (what the game's bankStep sees). Bank per tick to stderr."""
    T = P["beatTicks"]
    st, yaw, out = new_state(), 0.0, {}
    bank_step(st, yaw, 0.0)
    for t in range(n * every):
        if t < hold:
            yaw += yaw_rate
        bank_step(st, yaw, 1.0, 1.0)
        print("t %2d yaw %6.1f bank %6.2f tailA %6.2f tailB %6.2f" % (t + 1, yaw, st["bank"], st["tailA"], st["tailB"]), file=sys.stderr)
        if (t + 1) % every == 0:
            out["turn t%02d" % (t + 1)] = pose((t + 1) / T, AGE0 + t + 1, bank=st["bank"], tailA=st["tailA"], tailB=st["tailB"])
    return out


if __name__ == "__main__":
    which = sys.argv[1:]
    if which[:1] == ["--turn"]:
        json.dump(turn(*[float(a) for a in which[1:]]) if len(which) > 1 else turn(), sys.stdout, indent=1)
        sys.exit(0)
    if which[:1] == ["--strip"]:
        json.dump(strip(*which[1:2]), sys.stdout, indent=1)
        sys.exit(0)
    fr = frames()
    if which:
        fr = {k: v for k, v in fr.items() if k in which}
    json.dump(fr, sys.stdout, indent=1)
