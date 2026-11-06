#!/usr/bin/env python3
"""The Angel Caller's three summoned angels (Mender, Seraph Archer, Sword Seraph) in the three massing directions of
the design sheet (White Angels lane, round 1): A "Choir Seraph" (tall robed, wings raised vertical), B "Ordo Seraph"
(armoured, wings spread and swept), C "Herald" (small, a sun disc, four wings). GREYBOX level: stature, wing span /
shape / rest pose, head and neck, halo, body mass, the kind's signature prop, the team areas (palette names `tabard` /
`tabard_dark`, so `--team tabard,tabard_dark` dyes them) and at most two glow colours (`sun` the halo, `glow` the
kind's light). Flat colours; the values follow the sheet (cream plate and feathers, gold halo, team panels).

    python3 tools/gen_angels.py --massing b --kind archer    # design/models/sunforged_angel_b_archer.json
    python3 tools/gen_angels.py --massing b2 --kind sword   # round 2: B raised to a body (sunforged_angel_b2_sword.json)
    python3 tools/gen_angels.py --all                        # all twelve (round 1's a, b, c unchanged + b2)
    python3 tools/render_concept.py --check design/models/sunforged_angel_*.json

Space is the DSL's (design/models/README.md): px, y up, the unit faces -Z, +x is its left. Hover pose: the model is
settled so its lowest point (hem tip, toe or lantern) is at y = 0; the game lifts it by the flight height.

Every wing is a bone chain the motion rounds can drive: wing_<l|r>_arm (pivot on the scapula) -> _fore (elbow) ->
_hand (wrist) -> _tips (the outer primaries); C's lower pair is wing_<l|r>_low_arm -> _low_tips. Feathers are built in
the wing's own plane: a planform in (u out, v up) px, the plane reclined about x and rolled about z (dihedral), so A's
upright flame wings, B's spread swept wings and C's raised V share one builder. Props are built where the hand holds
them and handed to the forearm (human_kit.attach), the arm solved onto the grip (Model.solve_arm).
"""
import argparse
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_models import (Model, OUT, ring, rays, rounded, dome, ngon_prism, octagon_pole, rot_mat, apply,  # noqa: E402
                        _add, _mul, _dot, _cross, _unit, grip_hand)
from gen_angel_statue import slab  # noqa: E402  (gen_models.slab, nudged off gimbal lock)
from human_kit import skirt_panels, attach, owner_of  # noqa: E402
from gen_medic import cone_plates, put_plate  # noqa: E402

PALETTE = {
    "feather": "#efe8da", "feather_mid": "#d9cfbc", "feather_dark": "#b9ad96", "robe": "#e6dfd0",
    "robe_shade": "#c9bfac", "plate": "#d8d1c2", "plate_dark": "#a39b8a", "skin": "#dcc0a6", "skin_shade": "#b08d74",
    "hair": "#d9c08a", "eye": "#3a2e26", "lip": "#b07a6a", "gold": "#d4a63c", "gold_dark": "#a87f24",
    "sun_gold": "#f2cb57", "tabard": "#2f5fb3", "tabard_dark": "#244a8c", "sun": "#ffd96a", "glow": "#fff1b0",
    "hollow": "#4f4a44",
}
KIND_PALETTE = {
    "mender": {"linen": "#f1ece0", "brass": "#b98d3a"},
    "archer": {"bow": "#e2d6bd", "bow_dark": "#b8a782", "string": "#f6f0dc", "leather": "#6b4c33"},
    "sword": {"steel_bright": "#a3aab6", "steel_edge": "#d0d6df", "leather": "#6b4c33"},
}
EMISSIVE = ["sun", "glow"]
NAMES = {"mender": "Mender Angel", "archer": "Seraph Archer", "sword": "Sword Seraph"}
ROLES = {"mender": "healer (summon)", "archer": "ranged (summon)", "sword": "melee engager (summon)"}
MASSING = {"a": "Choir Seraph", "b": "Ordo Seraph", "c": "Herald", "b2": "Ordo Seraph r2"}
KIND_SCALE = {"mender": 0.92, "archer": 1.00, "sword": 1.15}      # the game's render scale (not baked in here)
RTS_EYE = (0.0, 0.79, -0.62)                                      # toward the RTS camera (52 deg down, in front)


def sub(a, b):
    return tuple(a[i] - b[i] for i in range(3))


def lerp(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(len(a)))


def mirror(p, side):
    return (p[0] * side, p[1], p[2])


# ------------------------------------------------------------------ wings ---------------------------------------
def feather(bone, base, tip, width, thick, colour, nf, offset=0.0, pointed=False):
    """One feather (or a bar) from base to tip lying in the wing plane (nf its normal toward the camera side), lifted
    `offset` px off the plane; pointed: a square turned 45 degrees closes the vane (the scalloped trailing edge)."""
    d = sub(tip, base)
    L = math.sqrt(_dot(d, d))
    ez = _unit(d)
    across = _unit(_cross(nf, ez))
    lift = _mul(nf, offset)
    vane = L - (0.5 * width if pointed else 0.0)
    c = _add(base, _mul(ez, vane / 2), lift)
    slab(bone, c, ez, across, (width, thick, vane + 0.02), colour)
    if pointed:
        s = 0.7 * width
        end = _add(base, _mul(ez, vane), lift)
        d45 = _unit(_add(ez, across))
        q45 = _unit(sub(across, ez))
        slab(bone, end, d45, q45, (s, thick, s), colour)


def wing(m, side, spec, prefix="wing", low=False):
    """One wing as a bone chain from a planform. spec: root (world, left wing), rho (recline: + tips the plane's top
    FORWARD, so in-plane 'down' trails back), delta (dihedral roll), joints [(u, v)...] R E W H (or R K for a lower
    wing), sec / prim / cov: lists of ((u, v) base, (u, v) tip, width) and colours. Returns the bone names."""
    lr = "l" if side > 0 else "r"
    M = rot_mat(-spec["rho"], 0, spec["delta"])
    R0 = spec["root"]

    def W(uv, w=0.0):
        p = apply(M, (uv[0], uv[1], w))
        return mirror((R0[0] + p[0], R0[1] + p[1], R0[2] + p[2]), side)

    n0 = apply(M, (0.0, 0.0, 1.0))
    n0 = mirror(n0, side)
    nf = n0 if _dot(n0, RTS_EYE) > 0 else _mul(n0, -1.0)
    J = spec["joints"]
    if low:
        names = [f"{prefix}_{lr}_low_arm", f"{prefix}_{lr}_low_tips"]
        pivots = [W(J[0]), W(J[1])]
    else:
        names = [f"{prefix}_{lr}_arm", f"{prefix}_{lr}_fore", f"{prefix}_{lr}_hand", f"{prefix}_{lr}_tips"]
        pivots = [W(J[0]), W(J[1]), W(J[2]), W(J[3])]
    bones = []
    for i, (n, pv) in enumerate(zip(names, pivots)):
        bones.append(m.bone(n, pv, parent=spec.get("parent", "body") if i == 0 else names[i - 1]))
    col = spec["colours"]
    # the leading edge: one bar per segment (arm, forearm, hand), proud of the plane on the camera side
    for i in range(len(J) - 1):
        feather(bones[min(i, len(bones) - 1)], W(J[i]), W(J[i + 1]), spec.get("bar_w", 1.7), 1.4, col["bar"], nf,
                offset=0.55)
    # flights: the secondaries on the arm / forearm by where they root, the primaries on the hand (inner half) and the
    # tips bone (outer half)
    for k, (b, t, w, seg) in enumerate(spec["sec"]):
        feather(bones[seg], W(b), W(t), w, 0.5, col["sec"], nf, offset=-0.05 * (k % 2), pointed=spec.get("sec_pt", False))
    n = len(spec["prim"])
    for k, (b, t, w, seg) in enumerate(spec["prim"]):
        feather(bones[seg], W(b), W(t), w, 0.5, col["prim"], nf, offset=0.07 * k - 0.2, pointed=True)
    for k, (b, t, w, seg) in enumerate(spec["cov"]):
        pt = spec.get("cov_pt", True) and math.dist(b, t) > 1.2 * w
        feather(bones[seg], W(b), W(t), w, 0.8, col["cov"], nf, offset=0.42, pointed=pt)
    return names


def polyline(J, t):
    """A point at parameter t along the joint polyline (t in 0..len(J)-1)."""
    i = min(len(J) - 2, max(0, int(t)))
    return lerp(J[i], J[i + 1], t - i)


def std_wing(J, chord, sec_dir, prim_tips, n_sec, wid_prim=2.4, cov_frac=0.45, sec_lean=0.0):
    """Feather lists for a 4-joint wing: n_sec secondaries off R..W pointing sec_dir (planform), lengths chord(t);
    primaries rooted from W to H with the given tips; one covert row along R..W."""
    sec, prim, cov = [], [], []
    for k in range(n_sec):
        t = 0.12 + 1.8 * k / max(1, n_sec - 1)
        b = polyline(J[:3], t)
        L = chord(t / 2.0)
        d = _unit((sec_dir[0] + sec_lean * t / 2.0, sec_dir[1], 0.0))
        w = 1.85 * math.dist(J[0], J[2]) / n_sec + 0.5
        sec.append((b, (b[0] + d[0] * L, b[1] + d[1] * L), w, 0 if t < 1.0 else 1))
    n = len(prim_tips)
    for k, tip in enumerate(prim_tips):
        f = k / max(1, n - 1)
        b = lerp(J[2], J[3], f)
        prim.append((b, tip, wid_prim, 2 if f < 0.5 else 3))
    for k in range(4):
        t = 0.25 + 1.65 * k / 3
        b = polyline(J[:3], t)
        L = chord(t / 2.0) * cov_frac
        d = _unit((sec_dir[0] + sec_lean * t / 2.0, sec_dir[1], 0.0))
        w = 1.15 * math.dist(J[0], J[2]) / 4 + 0.5
        cov.append((b, (b[0] + d[0] * L, b[1] + d[1] * L), w, 0 if t < 1.0 else 1))
    # a covert over the hand (the alula's place): the hand's root reads as one piece
    hb = lerp(J[2], J[3], 0.35)
    ht = (hb[0] + sec_dir[0] * chord(1.0) * 0.4, hb[1] + sec_dir[1] * chord(1.0) * 0.4)
    cov.append((hb, ht, 2.4, 2))
    return sec, prim, cov


def arc_tips(Ti, T, n, bulge, outward):
    out = []
    o = _unit((outward[0], outward[1], 0.0))
    for k in range(n):
        f = k / max(1, n - 1)
        p = lerp(Ti, T, f)
        s = bulge * math.sin(math.pi * f)
        out.append((p[0] + o[0] * s, p[1] + o[1] * s))
    return out


def wings_a(m, kind):
    """A: raised vertical, the leading edge on the outside rising to the wrist beside the halo, primaries up (a flame),
    secondaries hanging in toward the spine; the plane reclined 18 (statue lesson). Team: the leading-edge band."""
    tip_v = {"mender": 24.5, "archer": 26.5, "sword": 26.0}[kind]
    out = {"mender": 6.6, "archer": 6.4, "sword": 7.4}[kind]
    J = [(0.0, 0.0), (2.7, 7.4), (5.3, 15.8), (6.3, 19.6)]
    ch = {"mender": (2.6, 7.4), "archer": (2.4, 6.0), "sword": (2.6, 7.0)}[kind]
    tips = arc_tips((0.6, tip_v - 3.5), (J[3][0] + out - 4.0, tip_v), 5 if kind != "sword" else 6,
                    {"mender": 1.6, "archer": 0.6, "sword": 1.0}[kind], (0.0, 1.0))
    sec, prim, cov = std_wing(J, lambda t: ch[0] + (ch[1] - ch[0]) * min(1.0, 1.6 * t), (-0.85, -0.2), tips, 5,
                              wid_prim=2.2)
    spec = dict(root=(2.4, 25.5, 3.1), rho=-18.0, delta=0.0, joints=J, sec=sec, prim=prim, cov=cov, bar_w=2.2,
                colours=dict(bar="tabard_dark", sec="feather_mid", prim="feather", cov="feather"))
    for s in (1, -1):
        wing(m, s, spec)


def wings_b(m, kind):
    """B: spread wide and swept back from the wrist (a stooping falcon), dihedral; the plane tipped 70 forward so the
    chord trails back 20 below level - the top-down arrowhead. Team: the greater coverts row."""
    S = {"mender": 22.0, "archer": 23.5, "sword": 26.5}[kind]               # half-span (world)
    delta = {"mender": 12.0, "archer": 15.0, "sword": 19.0}[kind]
    sweep = {"mender": 6.0, "archer": 25.0, "sword": 33.0}[kind]
    c_root, c_hand = {"mender": (9.5, 8.5), "archer": (8.0, 5.5), "sword": (10.0, 7.0)}[kind]
    root = (2.6, 23.0, 3.0)
    u_tip = (S - root[0]) / math.cos(math.radians(delta))
    u_w = 0.46 * u_tip
    J = [(0.0, 0.0), (u_w * 0.5, 0.4), (u_w, -1.2), (u_w + 3.6 * math.cos(math.radians(sweep)),
                                                     -1.2 - 3.6 * math.sin(math.radians(sweep)))]
    T = (u_tip, -1.2 - (u_tip - u_w) * math.tan(math.radians(sweep)) - (1.5 if kind == "archer" else 0.0))
    Ti = (u_w + 1.0, -1.2 - c_hand - 1.2)
    n = {"mender": 6, "archer": 6, "sword": 7}[kind]
    tips = arc_tips(Ti, T, n, {"mender": 3.2, "archer": 0.8, "sword": 2.0}[kind], (0.55, -0.85))
    sec, prim, cov = std_wing(J, lambda t: c_root + (c_hand - c_root) * t, (0.0, -1.0), tips, 5, cov_frac=0.66,
                              wid_prim=2.6 if kind != "archer" else 2.2)
    team = "tabard"
    spec = dict(root=root, rho=70.0, delta=delta, joints=J, sec=sec, prim=prim, cov=cov,
                colours=dict(bar="feather", sec="feather_mid", prim="feather", cov=team))
    for s in (1, -1):
        wing(m, s, spec)


def wings_c(m, kind):
    """C: four wings - a large upper pair raised in a V (rounded, primaries fanned ~60 degrees, the plane reclined 25)
    and a small lower pair angled down and back (two bones, team colour)."""
    root = (2.0, 20.0, 3.2)
    J = [(0.0, 0.0), (3.4, 4.6), (7.8, 10.8), (10.2, 12.6)]
    L = {"mender": 8.6, "archer": 8.8, "sword": 9.0}[kind]
    a0, a1 = {"mender": (40.0, -38.0), "archer": (26.0, -30.0), "sword": (34.0, -34.0)}[kind]
    n = 6
    tips = []
    for k in range(n):
        f = k / (n - 1)
        a = math.radians(a1 + (a0 - a1) * f)
        Lk = L * (0.78 + 0.22 * f) if kind != "archer" else L * (0.7 + 0.42 * f * f)
        tips.append((J[3][0] - 1.6 * (1 - f) + math.cos(a) * Lk, J[3][1] - 1.2 * (1 - f) + math.sin(a) * Lk))
    sec, prim, cov = std_wing(J, lambda t: 3.4 + 3.6 * min(1.0, 1.6 * t), (0.18, -1.0), tips, 5, wid_prim=2.4)
    spec = dict(root=root, rho=-12.0, delta=0.0, joints=J, sec=sec, prim=prim, cov=cov,
                colours=dict(bar="feather", sec="feather_mid", prim="feather", cov="feather"))
    for s in (1, -1):
        wing(m, s, spec)
    # the lower pair: down and out to tips near (12, 4, 5); the plane tipped only 10 forward - the sheet's 40 (tips
    # back at z 12) lay along the RTS camera's line of sight and the team-coloured pair went edge-on
    K = (4.6, -3.6)
    low_sec = []
    for k, (bf, tip) in enumerate(((0.25, (0.6, -6.2)), (0.6, (3.4, -8.0)), (0.95, (6.4, -9.6)))):
        b = lerp((0.0, 0.0), K, bf)
        low_sec.append((b, tip, 2.6, 0))
    low_prim = [(K, (7.8, -11.4), 2.4, 1), (K, (9.4, -10.6), 2.4, 1), (K, (10.6, -9.0), 2.3, 1),
                (K, (10.8, -7.0), 2.2, 1)]
    low_cov = [((0.4, -0.3), (2.2, -3.4), 2.8, 0), (lerp((0.0, 0.0), K, 0.75), (5.4, -6.2), 2.6, 1)]
    spec_low = dict(root=(2.0, 15.0, 3.3), rho=10.0, delta=0.0, joints=[(0.0, 0.0), K], sec=low_sec,
                    prim=low_prim, cov=low_cov, bar_w=1.5, sec_pt=True,
                    colours=dict(bar="tabard_dark", sec="tabard", prim="tabard", cov="tabard_dark"))
    for s in (1, -1):
        wing(m, s, spec_low, low=True)


# ------------------------------------------------------------------ head and halo --------------------------------
def head(m, chin, H, circlet=False):
    """A bare, serene head at greybox level: the neck, a skull rounded in plan, a hair cap and fall, the eyes, a
    mouth; no helm (helms say Paladin). chin: the jaw's y; H: chin to crown."""
    h = m.bone("head", [0, chin - 0.6, 0.0], parent="body")
    octagon_pole(h, 0, 0.25, chin - 1.6, chin + 0.7, 0.42 * H, "skin")                              # the neck
    w, d = 0.76 * H, 0.86 * H
    rounded(h, 0, 0.1, chin + 0.15 * H, chin + 0.72 * H, w, d, "skin", k=2)                         # skull + face
    rounded(h, 0, -0.1, chin, chin + 0.2 * H, w * 0.72, d * 0.78, "skin", k=2)                      # the jaw
    dome(h, 0, 0.25, chin + 0.62 * H, 0.38 * H, w + 0.3, d + 0.35, "hair", layers=2, k=2)          # hair cap
    h.box_at((0, chin + 0.45 * H, 0.1 + d / 2 + 0.05), (w * 0.9, 0.62 * H, 0.6), "hair")            # the fall
    fz = 0.1 - d / 2
    for s in (1, -1):
        h.box_at((s * 0.2 * H, chin + 0.5 * H, fz - 0.05), (0.16 * H, 0.08 * H, 0.15), "eye")
    h.box_at((0, chin + 0.25 * H, fz - 0.03), (0.16 * H, 0.05 * H, 0.1), "lip")
    if circlet:
        rounded(h, 0, 0.15, chin + 0.68 * H, chin + 0.68 * H + 0.55, w + 0.45, d + 0.45, "gold", k=2)
    return h


def halo_ring(m, centre, diameter, thick, depth, tilt, n=10):
    hb = m.bone("halo", list(centre), parent="body", rotation=[tilt, 0, 0])
    ring(hb, centre, diameter / 2 - thick / 2, n, thick, depth, "sun")
    return hb


def halo_disc(m, centre, diameter, tilt):
    """C's sun disc: a cream face, six sun_gold rays round it, the rim the glowing ring (the halo's light)."""
    hb = m.bone("halo", list(centre), parent="body", rotation=[tilt, 0, 0])
    cx, cy, cz = centre
    r = diameter / 2
    # the face: an octagonal plate in the x-y plane (three stacked boxes, a pixel circle)
    for w, h in ((r * 2 - 2.6, r * 2 - 1.0), (r * 2 - 1.0, r * 2 - 2.6), (r * 2 - 1.9, r * 2 - 1.9)):
        hb.box_at((cx, cy, cz + 0.2), (w, h, 0.5), "robe")
    rays(hb, (cx, cy, cz - 0.15), r * 0.52, 6, 2.6, 0.9, 0.3, "sun_gold")
    hb.box_at((cx, cy, cz - 0.15), (2.0, 2.0, 0.3), "sun_gold")
    ring(hb, (cx, cy, cz), r - 0.45, 12, 0.9, 1.0, "sun")
    return hb


# ------------------------------------------------------------------ arms ----------------------------------------
def arm(m, side, shoulder, up_len, fore_len, w, sleeve, fore, cuff, hand="skin"):
    """A greybox arm: upper (shoulder pivot) -> forearm (elbow pivot) with a cuff and a fist. Returns (upper, fore,
    the fist's centre in rest space)."""
    pre = "left" if side > 0 else "right"
    sx, sy, sz = shoulder
    ey, wy = sy - up_len, sy - up_len - fore_len
    up = m.bone(pre + "_arm", [sx, sy, sz], parent="body")
    rounded(up, sx, sz, ey - 0.2, sy + 0.6, w, w, sleeve, k=2)
    fo = m.bone(pre + "_fore", [sx, ey, sz], parent=pre + "_arm")
    rounded(fo, sx, sz, wy + 0.3, ey + 0.2, w * 0.88, w * 0.88, fore, k=2)
    if cuff:
        fo.box_at((sx, wy + 0.55, sz), (w * 0.98, 0.6, w * 0.98), cuff)
    hs = 0.82 * w
    fo.box_at((sx, wy - hs * 0.5, sz), (hs * 0.85, hs * 1.15, hs), hand)
    return pre + "_arm", pre + "_fore", (sx, wy - hs * 0.5, sz)


def solve(m, arm_bones, target, **kw):
    up, fo, rest = arm_bones
    e = m.solve_arm(up, fo, rest, target, elbow_out=1.0, elbow_down=kw.pop("elbow_down", 1.0), avoid=("body",),
                    avoid_margin=0.7, **kw)
    return e


# ------------------------------------------------------------------ props ---------------------------------------
def lantern(m, hand_pt, fore, chain_len, cage=(3.5, 4.5)):
    """The sun-lantern: a brass hexagonal cage with a glowing core on a chain from the right hand (bones chain ->
    lantern, a pendulum)."""
    hx, hy, hz = hand_pt
    top = hy - 0.6 - chain_len
    ch = m.bone("chain", [hx, hy, hz])
    ch.box_at((hx, hy - 0.6 - chain_len / 2, hz), (0.45, chain_len + 0.4, 0.45), "gold_dark")
    ch.box_at((hx, top + 0.2, hz), (1.0, 0.7, 0.3), "brass")                                 # the ring
    w, h = cage
    ln = m.bone("lantern", [hx, top, hz])
    ngon_prism(ln, hx, hz, top - 0.7, top - 0.1, w, "brass", n=6)                            # cap
    ln.box_at((hx, top + 0.05, hz), (1.2, 0.6, 1.2), "brass")                                # finial
    ngon_prism(ln, hx, hz, top - h, top - h + 0.6, w, "brass", n=6)                          # base
    ln.box_at((hx, top - h / 2 - 0.05, hz), (w * 0.58, h - 1.4, w * 0.58), "glow")           # the core
    for a in (0, 60, 120):
        c = (hx, top - h / 2, hz)
        ln.box_at(c, (w * 0.95, h - 1.0, 0.35), "brass", rotation=[0, a, 0], pivot=list(c))  # the cage's bars
    attach(m, ch, fore, (hx, hy, hz))
    attach(m, ln, "chain", (hx, top, hz))


def bow(m, grip, length, lean_out, fore, depth=2.6, fix=True):
    """A recurve bow held at its grip: the long axis leaning `lean_out` degrees outward (+x), the limbs bowed toward
    the target (-z), horn-gold tips flicked forward, a string between the tips."""
    b = m.bone("bow", list(grip))
    Lx = (math.sin(math.radians(lean_out)), math.cos(math.radians(lean_out)), 0.0)
    F = (0.0, 0.0, -1.0)
    half = length / 2

    def P(t):
        rec = 1.4 * max(0.0, abs(t) - 0.78) / 0.22
        return _add(grip, _mul(Lx, t * half), _mul(F, depth * (1 - t * t) - depth + rec))
    ts = [-1.0, -0.78, -0.55, -0.3, -0.1, 0.1, 0.3, 0.55, 0.78, 1.0]
    for a, c in zip(ts, ts[1:]):
        col = "leather" if (a, c) == (-0.1, 0.1) else ("bow_dark" if max(abs(a), abs(c)) > 0.99 else "bow")
        pa, pc = P(a), P(c)
        slab(b, lerp(pa, pc, 0.5), sub(pc, pa), (1.0, 0.0, 0.0) if abs(lean_out) < 80 else (0, 1, 0),
             (1.2, 1.6, math.dist(pa, pc) + 0.3), col)
    for t in (-1.0, 1.0):
        b.box_at(P(t), (1.1, 1.1, 1.1), "gold")
    s0, s1 = _add(P(-1.0), (0, 0, 0.5)), _add(P(1.0), (0, 0, 0.5))
    slab(b, lerp(s0, s1, 0.5), sub(s1, s0), (1.0, 0.0, 0.0), (0.4, 0.4, math.dist(s0, s1)), "string")
    if fix:
        attach(m, b, fore, grip)
    return b, Lx


def quiver(m, base, top):
    q = m.bone("quiver", list(base), parent="body")
    slab(q, lerp(base, top, 0.5), sub(top, base), (1.0, 0.0, 0.0), (1.8, 1.8, math.dist(base, top)), "leather")
    d = _unit(sub(top, base))
    for k, dx in enumerate((-0.45, 0.0, 0.45)):
        p = _add(top, _mul(d, 0.6 + 0.3 * k), (dx, 0, 0))
        slab(q, p, d, (1.0, 0.0, 0.0), (0.5, 0.5, 1.8), "sun_gold")


def sword(m, grip, direction, blade_len, fore, width=1.9, name="sword", fix=True):
    """An arming sword built along `direction` from the grip: pommel, grip, a gold cross-guard, a steel blade with a
    glowing fuller (0.5 px, the kind's light) and a diamond point."""
    s = m.bone(name, list(grip))
    d = _unit(direction)
    across = _unit(_cross(d, (0.0, 0.0, 1.0))) if abs(d[2]) < 0.9 else (1.0, 0.0, 0.0)
    slab(s, _add(grip, _mul(d, -2.1)), d, across, (1.2, 1.2, 1.0), "gold")                   # pommel
    slab(s, _add(grip, _mul(d, -0.6)), d, across, (0.8, 0.8, 3.0), "leather")                # grip
    g = _add(grip, _mul(d, 1.2))
    slab(s, g, d, across, (5.2, 1.0, 0.9), "gold")                                           # cross-guard
    b0 = _add(g, _mul(d, 0.45))
    slab(s, _add(b0, _mul(d, blade_len / 2)), d, across, (width, 0.55, blade_len), "steel_edge")
    slab(s, _add(b0, _mul(d, blade_len / 2 - 0.3)), d, across, (0.5, 0.7, blade_len - 3.5), "glow")
    tip = _add(b0, _mul(d, blade_len))
    q = width * 0.7
    slab(s, tip, _unit(_add(d, across)), _unit(sub(across, d)), (q, 0.5, q), "steel_bright")
    if fix:
        attach(m, s, fore, grip)
    return s


def shield(m, centre, diameter, fore, side=1, yaw=-22.0, n=8):
    """A round shield on the forearm: a gold rim, a plate face, the team field (5 px) and a sun_gold boss. Built lying
    flat (ngon_prism is upright) and turned to face front-outward by attach's turn."""
    sh = m.bone("shield", list(centre))
    cx, cy, cz = centre
    ngon_prism(sh, cx, cz, cy - 0.5, cy + 0.1, diameter, "gold", n=n)
    ngon_prism(sh, cx, cz, cy + 0.05, cy + 0.55, diameter - 1.1, "plate", n=n)
    ngon_prism(sh, cx, cz, cy + 0.5, cy + 0.8, 5.0, "tabard", n=n)
    sh.box_at((cx, cy + 0.95, cz), (1.6, 0.5, 1.6), "sun_gold")
    attach(m, sh, fore, centre, turn=(-90.0, side * yaw, 0.0))


# ------------------------------------------------------------------ bodies --------------------------------------
def stole(m, strips_front, strips_back, colour="tabard", w=2.4):
    """Two team strips front and back, following the given surface points [(x, y, z), ...] down to the hem."""
    for name, strips in (("stole_front", strips_front), ("stole_back", strips_back)):
        b = m.bone(name, list(strips[0][0]), parent="body")
        for pts in strips:
            for a, c in zip(pts, pts[1:]):
                slab(b, lerp(a, c, 0.5), sub(c, a), (1.0, 0.0, 0.0), (w, 0.35, math.dist(a, c) + 0.25), colour)


def leg(m, side, hip, thigh_len, shin_len, w, rot_thigh, rot_shin, c_thigh, c_shin, c_toe):
    pre = "left" if side > 0 else "right"
    hx, hy = hip
    ky, ay = hy - thigh_len, hy - thigh_len - shin_len
    th = m.bone(pre + "_thigh", [hx, hy, 0.0], parent="body", rotation=[rot_thigh, 0, 0])
    rounded(th, hx, 0.0, ky - 0.2, hy + 0.4, w, w * 1.08, c_thigh, k=2)
    sh = m.bone(pre + "_shin", [hx, ky, 0.0], parent=pre + "_thigh", rotation=[rot_shin, 0, 0])
    rounded(sh, hx, 0.0, ay, ky + 0.2, w * 0.86, w * 0.92, c_shin, k=2)
    # the pointed foot (no sabaton: nothing to stand on): the instep along the shin, a narrow toe beyond it
    sh.box_at((hx, ay - 1.1, -0.35), (w * 0.72, 2.4, w * 0.9), c_toe, rotation=[-14, 0, 0], pivot=[hx, ay, -0.2])
    sh.box_at((hx, ay - 2.6, -0.75), (w * 0.5, 1.2, w * 0.55), c_toe, rotation=[-14, 0, 0], pivot=[hx, ay, -0.2])


def taper_panels(m, prefix, top, hem, A, B, A_hem, colour, n=10, t=0.32, cz=-0.15, parent="body"):
    """A cone that NARROWS downward (A's candle hem) shared over four panel bones like human_kit.skirt_panels, but
    with the plates exact at the WIDE top: skirt_panels sizes them at the hem, which for a taper left grass showing
    between the plates (round 1's first render: a bird cage under the robe)."""
    s_hem = A_hem / A
    zf, zb = cz - B, cz + B
    hinge = top - 0.2
    pivots = {"_front": [0, hinge, zf + 0.3], "_back": [0, hinge, zb - 0.3], "_left": [A - 0.4, hinge, cz],
              "_right": [-(A - 0.4), hinge, cz]}
    bones = {prefix + k: m.bone(prefix + k, pv, parent=parent) for k, pv in pivots.items()}
    for p in cone_plates(0, cz, A, B, hem, s_hem, top, 1.0, n, t, exact_at=top):
        put_plate(bones[owner_of(prefix, p["th"])], p, colour)
    return bones


def body_a(m, kind):
    """A: the tall robed seraph - V torso, a gold cincture, a robe flaring to 10.5 px at y 11 and tapering to a 2.5 px
    point at y 0 (a candle body, no feet); the stole in team colour front and back."""
    b = m.bone("body", [0, 22.0, 0])
    rounded(b, 0, 0, 23.0, 27.6, 9.0, 5.5, "robe", k=2)                     # chest
    rounded(b, 0, 0.1, 27.2, 28.2, 6.6, 4.4, "robe_shade", k=2)             # yoke
    rounded(b, 0, 0, 20.2, 23.4, 6.2, 4.4, "robe", k=2)                     # waist
    rounded(b, 0, 0, 19.9, 20.9, 6.3, 4.7, "gold", k=2)                     # cincture
    b.box_at((0, 20.4, -2.5), (1.4, 1.4, 0.5), "sun_gold", rotation=[0, 0, 45], pivot=[0, 20.4, -2.5])
    skirt_panels(m, "robe", 20.3, 11.0, 2.95, 2.05, 5.25, "robe", n=10)
    taper_panels(m, "hem", 11.05, 0.0, 5.25, 3.65, 1.25, "robe", n=10)
    # the stole: two strips over the shoulders, down the chest, across the cincture, down the robe to the hem
    def fr(y, x, s):
        if y >= 23:
            z = 2.95
        elif y >= 20.3:
            z = 2.45
        elif y >= 11:
            z = 2.05 + (3.65 - 2.05) * (20.3 - y) / 9.3
        else:
            z = 3.65 - (3.65 - 0.9) * (11 - y) / 11
        return (x, y, s * (z + 0.25))
    front, back = [], []
    for x0 in (1.5, -1.5):
        xs = lambda y: x0 * (1.0 if y > 11 else max(0.45, y / 11))  # noqa: E731
        front.append([fr(y, xs(y), -1) for y in (27.9, 23.2, 20.4, 11.0, 2.2)])
        back.append([fr(y, xs(y), 1) for y in (27.9, 23.2, 20.4, 11.0, 2.2)])
    stole(m, front, back)
    return dict(shoulder=(4.3, 27.0, 0.0), up=6.0, fore=5.0, arm_w=2.0, sleeve="robe", fore_c="robe",
                cuff="gold", chin=29.0, H=5.0, front_z=-2.75, waist=20.4, back_z=2.75)


def body_b(m, kind):
    """B: the lean armoured angel - a cuirass over a short split tabard (four panels) and trailing legs in slim
    greaves; the Mender wears a robe to the ankles under the cuirass instead (the stole in team colour)."""
    b = m.bone("body", [0, 21.0, 0])
    rounded(b, 0, 0, 20.6, 25.4, 9.4, 6.0, "plate", k=2)                    # cuirass
    rounded(b, 0, 0.2, 25.0, 26.0, 6.2, 4.6, "gold", k=2)                   # gorget rim
    rounded(b, 0, 0, 17.0, 20.8, 5.8, 4.5, "plate", k=2)                    # waist
    rounded(b, 0, 0, 16.6, 17.5, 6.4, 4.9, "gold", k=2)                     # belt
    rounded(b, 0, 0, 14.6, 16.8, 6.4, 4.4, "plate_dark", k=2)               # hips
    b.box_at((0, 23.2, -3.05), (2.2, 2.2, 0.4), "sun_gold", rotation=[0, 0, 45], pivot=[0, 23.2, -3.05])
    if kind == "sword":
        for s, n in ((1, "pauldron_l"), (-1, "pauldron_r")):
            p = m.bone(n, [s * 4.6, 25.2, 0], parent="body")
            dome(p, s * 4.9, 0.0, 23.2, 3.0, 3.8, 4.4, "plate", layers=2, k=2)
            rounded(p, s * 4.9, 0.0, 22.7, 23.3, 4.0, 4.6, "gold", k=2)
    if kind == "mender":
        skirt_panels(m, "robe", 16.8, 2.6, 3.2, 2.3, 4.6, "robe", hem_colour="robe_shade", hem_h=0.9, n=10)
        for s in (1, -1):                                                    # the pointed toes under the hem
            b.box_at((s * 1.2, 1.7, -0.6), (1.3, 3.0, 1.5), "skin", rotation=[-20, 0, 0], pivot=[s * 1.2, 3.0, -0.6])
        front, back = [], []
        for x0 in (1.5, -1.5):
            front.append([(x0, 25.6, -3.3), (x0, 20.7, -3.25), (x0, 16.8, -2.6), (x0 * 1.2, 3.5, -3.45)])
            back.append([(x0, 25.6, 3.3), (x0, 20.7, 3.25), (x0, 16.8, 2.6), (x0 * 1.2, 3.5, 3.45)])
        stole(m, front, back)
    else:
        hem = 14.0 if kind == "archer" else 12.6
        for name, piv, size, col, rot in (
                ("tabard_front", (0, 17.2, -2.6), (4.4, 17.2 - hem, 0.5), "tabard", 6),
                ("tabard_back", (0, 17.2, 2.6), (4.4, 17.2 - hem, 0.5), "tabard", -6),
                ("tabard_left", (3.3, 17.2, 0), (0.5, 17.2 - hem - 0.8, 3.0), "tabard_dark", 0),
                ("tabard_right", (-3.3, 17.2, 0), (0.5, 17.2 - hem - 0.8, 3.0), "tabard_dark", 0)):
            t = m.bone(name, list(piv), parent="body", rotation=[rot, 0, 0] if rot else (0, 0, 0))
            t.box_at((piv[0], piv[1] - size[1] / 2, piv[2]), size, col)
        for s in (1, -1):
            leg(m, s, (s * 1.65, 15.0), 7.4, 6.6, 2.5, -14.0, -12.0, "robe_shade", "plate", "plate")
    return dict(shoulder=(4.5, 25.0, 0.0), up=5.6, fore=4.6, arm_w=1.9, sleeve="plate", fore_c="plate",
                cuff="gold", chin=27.2, H=4.8, front_z=-3.0, waist=18.0, back_z=3.0)


def body_c(m, kind):
    """C: the slim small herald - a light torso, a knee tunic in four panels, a team sash, bare legs trailing 25."""
    b = m.bone("body", [0, 17.0, 0])
    rounded(b, 0, 0, 16.4, 21.4, 7.6, 4.5, "robe", k=2)                     # chest
    rounded(b, 0, 0, 13.4, 16.6, 4.8, 3.8, "robe", k=2)                     # waist
    rounded(b, 0, 0, 13.6, 14.4, 5.0, 4.1, "gold", k=2)                     # girdle
    skirt_panels(m, "tunic", 13.6, 9.8, 2.55, 2.0, 3.7, "robe", hem_colour="gold", hem_h=0.5, n=8)
    sash = m.bone("sash", [0, 21.0, 0], parent="body")
    for z in (-2.42, 2.42):                                                  # left shoulder to right hip, both faces
        a, c = (2.9, 21.4, z), (-2.4, 13.9, z * 0.92)
        slab(sash, lerp(a, c, 0.5), sub(c, a), (0.6, 0.45, 0.0),
             (2.2, 0.35, math.dist(a, c) + 0.6), "tabard")
    for s in (1, -1):
        leg(m, s, (s * 1.25, 13.0), 6.4, 6.0, 1.75, -18.0, -14.0, "skin", "skin", "skin")
    return dict(shoulder=(3.8, 21.0, 0.0), up=4.8, fore=4.0, arm_w=1.5, sleeve="robe", fore_c="skin", cuff="gold",
                chin=22.4, H=4.6, front_z=-2.25, waist=14.4, back_z=2.25)


# ------------------------------------------------------------------ round 2: option B2 (B raised to a body) -------
# The blind critic picked B (39/60) with three flaws; round 2 changes ONE thing per element so each can be credited:
#  1 wing plan  - a wrist: the leading edge breaks 22 deg FORWARD at 55% of the half-span, chord root 1 : wrist 1.2 :
#                 tip ~0.4, dihedral 9-12, the tip split into 3-4 primary fingers (slots between them); per kind:
#                 Mender broad rounded dove tip, Archer pointed falcon tip, Sword the broadest, most swept wing
#  2 wing root  - a humerus block 0.35 body-widths wide rising 30 deg up-and-back off the top of the breastplate,
#                 the leading edge thickest at the root (1.6 px) thinning to 0.9 on the hand, fingers 0.55
#  3 team       - a solid team band along the leading edge (~30% chord, the humerus block in tabard_dark) + the
#                 tabard (Mender: a front/back panel down the robe); round 1's covert zigzag is gone
#  4 halo       - the ring's tube 2.1 px (2-3 px at the 36 px far view); the Mender wears a flat gold disc (from C)
#  5 grafts     - C's two covert rows on the upper surface (a light row over a darker row: they read as layered
#                 feathers); A's legless column robe for the MENDER only; the lantern 0.4 body-widths further out
#  6 underside  - the flight feathers in a warm cream (feather_warm) a step under the top coverts, not the grey
#                 feather_mid of round 1's front view
#  7 hands      - gen_models.grip_hand closed on each grip, the arm solved onto the hand's wrist (human_kit.hold's
#                 loop); the head stays round 1's (human_kit.bare_head alone is 138 cubes: over the 120-170 budget)
B2_WING = {
    # S half-span (world px from the midline), c root chord, sweep of the hand's leading edge (deg back), dihedral,
    # fingers: width, slot, how far each inner finger's tip stands back from the outer one (the tip's shape),
    # feather counts on the inner wing (secondaries, covert row 1, covert row 2)
    "mender": dict(S=22.0, c=8.6, sweep=8.0, delta=9.0, fw=2.5, gap=0.9, recede=(0.0, 0.5, 1.3, 2.4),
                   n_sec=3, n_cov1=2, n_cov2=3),
    "archer": dict(S=23.5, c=7.0, sweep=28.0, delta=10.0, fw=2.0, gap=0.75, recede=(0.0, 1.9, 4.0),
                   n_sec=4, n_cov1=3, n_cov2=3),
    "sword": dict(S=26.0, c=9.4, sweep=34.0, delta=12.0, fw=2.5, gap=0.9, recede=(0.0, 0.9, 2.0, 3.4),
                  n_sec=3, n_cov1=2, n_cov2=3),
}
HUM_BASE = (2.4, 24.6, 2.4)          # the wing's shoulder joint on the top-back of the breastplate (left wing)
HUM_DIR = (0.7, 0.55, 0.65)          # out, 30 deg up and back
HUM_LEN, HUM_W, HUM_T = 4.0, 3.3, 2.2
WRIST_FWD = 22.0                     # the leading edge's forward break at the wrist
RHO_B2 = 70.0                        # the plane tipped 70 forward (as round 1): the chord trails back 20 below level


def wings_b2(m, kind):
    P = B2_WING[kind]
    d_h = _unit(HUM_DIR)
    R = _add(HUM_BASE, _mul(d_h, HUM_LEN))
    M = rot_mat(-RHO_B2, 0, P["delta"])
    cd = math.cos(math.radians(P["delta"]))
    u_tip = (P["S"] - R[0]) / cd
    u_w = (0.55 * P["S"] - R[0]) / cd
    Wr = (u_w, u_w * math.tan(math.radians(WRIST_FWD)))
    E = (0.5 * Wr[0], 0.5 * Wr[1] + 0.25)
    T = (u_tip, Wr[1] - (u_tip - u_w) * math.tan(math.radians(P["sweep"])))
    c = P["c"]
    hand_len = math.dist(Wr, T)
    a_h = ((T[0] - Wr[0]) / hand_len, (T[1] - Wr[1]) / hand_len)
    b_h = (a_h[1], -a_h[0])                                       # square to the hand, toward the trailing edge
    LE = [(0.0, 0.0), E, Wr]

    def le(t):
        return polyline(LE, t)

    def chord(t):                                                 # t 0..2 along root-elbow-wrist: 1.0 -> 1.2
        return c * (1.0 + 0.1 * t)

    def P2(p, q, k):                                              # p + k q (planform)
        return (p[0] + q[0] * k, p[1] + q[1] * k)
    fw, gap, rec = P["fw"], P["gap"], P["recede"]
    nfing = len(rec)
    tips_f, roots_f = [], []
    for k in range(nfing):
        tips_f.append(P2(P2(T, b_h, fw / 2 + k * (fw + gap)), a_h, -rec[k]))
        roots_f.append(P2(P2(Wr, a_h, hand_len * (0.5 - 0.12 * k)), b_h, fw / 2 + 0.35 * k))
    te_w = (Wr[0], Wr[1] - chord(2.0))
    span_px = math.dist(LE[0], E) + math.dist(E, Wr)
    for side in (1, -1):
        lr = "l" if side > 0 else "r"

        def Wp(uv, w=0.0):
            p = apply(M, (uv[0], uv[1], w))
            return mirror((R[0] + p[0], R[1] + p[1], R[2] + p[2]), side)
        n0 = mirror(apply(M, (0.0, 0.0, 1.0)), side)
        nf = n0 if _dot(n0, RTS_EYE) > 0 else _mul(n0, -1.0)
        names = ["wing_%s_arm" % lr, "wing_%s_fore" % lr, "wing_%s_hand" % lr, "wing_%s_tips" % lr]
        pivots = [mirror(HUM_BASE, side), Wp(E), Wp(Wr), Wp(P2(Wr, a_h, 0.3 * hand_len))]
        bones = [m.bone(n, list(pv), parent="body" if i == 0 else names[i - 1]) for i, (n, pv) in
                 enumerate(zip(names, pivots))]
        arm_b, fore_b, hand_b, tips_b = bones
        # 2: the humerus block, out of the breastplate up-and-back to the wing's root (team dark: the band's start)
        hc = _add(HUM_BASE, _mul(d_h, HUM_LEN / 2 + 0.25))
        across = _unit(_cross(d_h, (0.0, 1.0, 0.0)))
        slab(arm_b, mirror(hc, side), mirror(d_h, side), mirror(across, side), (HUM_W, HUM_T, HUM_LEN + 0.5),
             "tabard_dark")
        # 6: flights in warm cream - secondaries off the arm and forearm, straight back to the trailing edge
        ns = P["n_sec"]
        for k in range(ns):
            t = -0.25 + 2.2 * k / (ns - 1)
            b = P2(le(t), (0.0, -1.0), 0.15 * c)
            dd = _unit((0.1 * t, -1.0, 0.0))
            tip = P2(le(t), dd, chord(t))
            w = 1.3 * (2.2 / (ns - 1)) * span_px / 2 + 0.3
            feather(arm_b if t < 1.0 else fore_b, Wp(b), Wp(tip), w, 0.5, "feather_warm", nf,
                    offset=-0.05 * (k % 2), pointed=True)
        # 1: the hand - two inner primaries filling wrist to fingers, then the split tip
        for i in range(2):
            b = P2(P2(Wr, a_h, hand_len * (0.08 + 0.14 * i)), b_h, 0.25 * c)
            tip = P2(lerp(te_w, tips_f[-1], (i + 1) / 3.0), b_h, 0.7)
            feather(hand_b, Wp(b), Wp(tip), fw + 0.7, 0.5, "feather_warm", nf, offset=-0.12 + 0.04 * i, pointed=True)
        for k in range(nfing):
            feather(tips_b, Wp(roots_f[k]), Wp(tips_f[k]), fw, 0.55, "feather_warm", nf, offset=-0.04 - 0.05 * k,
                    pointed=True)
        # 5: C's layered coverts - a light row tucked under the band, a darker row over the flights' roots
        n1 = P["n_cov1"]
        for k in range(n1):
            t = 0.15 + 1.65 * k / max(1, n1 - 1)
            b, tip = P2(le(t), (0.0, -1.0), 0.2 * chord(t)), P2(le(t), (0.0, -1.0), 0.52 * chord(t))
            w = 1.25 * (1.65 / max(1, n1 - 1)) * span_px / 2 + 0.4
            feather(arm_b if t < 1.0 else fore_b, Wp(b), Wp(tip), w, 0.6, "feather", nf, offset=0.5)
        n2 = P["n_cov2"]
        for k in range(n2):
            t = -0.1 + 1.95 * k / (n2 - 1)
            b, tip = P2(le(t), (0.0, -1.0), 0.36 * chord(t)), P2(le(t), (0.0, -1.0), 0.8 * chord(t))
            w = min(1.25 * (1.95 / (n2 - 1)) * span_px / 2 + 0.4, 1.2 * 0.44 * chord(t))   # a point, not a diamond
            feather(arm_b if t < 1.0 else fore_b, Wp(b), Wp(tip), w, 0.6, "feather_mid", nf, offset=0.3,
                    pointed=True)
        hb = P2(P2(Wr, a_h, 0.3 * hand_len), b_h, 0.28 * c)        # the primary coverts over the hand's root
        feather(hand_b, Wp(hb), Wp(P2(hb, b_h, 0.42 * c)), 0.42 * hand_len, 0.6, "feather_mid", nf, offset=0.3)
        # 3: the team band - the leading edge itself, ~30% chord, thickest at the root
        for bone, p0, p1, wb, th in ((arm_b, LE[0], E, 0.32 * chord(0.5), 1.6),
                                     (fore_b, E, Wr, 0.32 * chord(1.5), 1.25),
                                     (hand_b, Wr, P2(Wr, a_h, 0.55 * hand_len), 0.3 * c, 0.9)):
            s_ = _unit((p1[0] - p0[0], p1[1] - p0[1], 0.0))
            bk = (s_[1], -s_[0])
            q0 = P2(P2(p0, s_, -0.3), bk, wb / 2)
            q1 = P2(P2(p1, s_, 0.3), bk, wb / 2)
            feather(bone, Wp(q0), Wp(q1), wb, th, "tabard", nf, offset=0.45)
    return dict(R=R, wrist=Wr, tip=T, u_tip=u_tip)


def body_b2(m, kind):
    """B's cuirass; the Mender in A's legless column robe (a flared skirt then a taper to a point, no feet) with a
    team panel down its front and back; the Archer and the Sword in a wider split tabard over trailing legs."""
    b = m.bone("body", [0, 21.0, 0])
    rounded(b, 0, 0, 20.6, 25.4, 9.4, 6.0, "plate", k=2)                    # cuirass
    rounded(b, 0, 0.2, 25.0, 26.0, 6.2, 4.6, "gold", k=2)                   # gorget rim
    rounded(b, 0, 0, 17.0, 20.8, 5.8, 4.5, "plate", k=2)                    # waist
    rounded(b, 0, 0, 16.6, 17.5, 6.4, 4.9, "gold", k=2)                     # belt
    b.box_at((0, 23.2, -3.05), (2.2, 2.2, 0.4), "sun_gold", rotation=[0, 0, 45], pivot=[0, 23.2, -3.05])
    if kind == "sword":
        for s, n in ((1, "pauldron_l"), (-1, "pauldron_r")):
            p = m.bone(n, [s * 4.6, 25.2, 0], parent="body")
            dome(p, s * 4.9, 0.0, 22.8, 3.2, 4.0, 4.6, "plate", layers=1, top=0.75, k=2)
    if kind == "mender":
        skirt_panels(m, "robe", 16.8, 9.6, 3.2, 2.3, 5.0, "robe", n=8)
        taper_panels(m, "hem", 9.65, 0.0, 5.0, 3.6, 1.2, "robe", n=8)
        front = [[(0, 25.5, -3.2), (0, 20.7, -3.1), (0, 16.8, -2.6), (0, 9.6, -3.9), (0, 3.6, -2.2)]]
        back = [[(0, 25.5, 3.2), (0, 20.7, 3.1), (0, 16.8, 2.3), (0, 9.6, 3.6), (0, 3.6, 1.9)]]
        stole(m, front, back, w=3.0)
    else:
        rounded(b, 0, 0, 14.6, 16.8, 6.4, 4.4, "plate_dark", k=2)           # hips
        hem = 13.6 if kind == "archer" else 12.2
        for name, piv, size, col, rot in (
                ("tabard_front", (0, 17.2, -2.6), (5.4, 17.2 - hem, 0.5), "tabard", 6),
                ("tabard_back", (0, 17.2, 2.6), (5.4, 17.2 - hem, 0.5), "tabard", -6),
                ("tabard_left", (3.3, 17.2, 0), (0.5, 17.2 - hem - 0.8, 3.4), "tabard_dark", 0),
                ("tabard_right", (-3.3, 17.2, 0), (0.5, 17.2 - hem - 0.8, 3.4), "tabard_dark", 0)):
            t = m.bone(name, list(piv), parent="body", rotation=[rot, 0, 0] if rot else (0, 0, 0))
            t.box_at((piv[0], piv[1] - size[1] / 2, piv[2]), size, col)
        for s in (1, -1):
            leg_b2(m, s, (s * 1.65, 15.0), 7.4, 6.6, 2.5, -14.0, -12.0, "robe_shade", "plate", "plate")
    return dict(shoulder=(4.5, 25.0, 0.0), up=5.6, fore=4.6, arm_w=1.9, sleeve="plate", fore_c="plate",
                cuff="gold", chin=27.2, H=4.8, front_z=-3.0, waist=18.0, back_z=3.0)


def leg_b2(m, side, hip, thigh_len, shin_len, w, rot_thigh, rot_shin, c_thigh, c_shin, c_toe):
    """Round 1's trailing leg with the pointed foot in one tapered box (the cube budget)."""
    pre = "left" if side > 0 else "right"
    hx, hy = hip
    ky, ay = hy - thigh_len, hy - thigh_len - shin_len
    th = m.bone(pre + "_thigh", [hx, hy, 0.0], parent="body", rotation=[rot_thigh, 0, 0])
    rounded(th, hx, 0.0, ky - 0.2, hy + 0.4, w, w * 1.08, c_thigh, k=2)
    sh = m.bone(pre + "_shin", [hx, ky, 0.0], parent=pre + "_thigh", rotation=[rot_shin, 0, 0])
    rounded(sh, hx, 0.0, ay, ky + 0.2, w * 0.86, w * 0.92, c_shin, k=2)
    sh.box_at((hx, ay - 1.6, -0.5), (w * 0.6, 3.4, w * 0.75), c_toe, rotation=[-14, 0, 0], pivot=[hx, ay, -0.2])


def head_b2(m, chin, H):
    """Round 1's serene bare head at 13 cubes: a turned neck, skull, jaw, a one-slice hair cap and its fall, the
    eyes, the gilt circlet (no lip: below the texture's reach at 4.8 px)."""
    h = m.bone("head", [0, chin - 0.6, 0.0], parent="body")
    rounded(h, 0, 0.25, chin - 1.6, chin + 0.7, 0.42 * H, 0.42 * H, "skin", k=2)               # the neck
    w, d = 0.76 * H, 0.86 * H
    rounded(h, 0, 0.1, chin + 0.15 * H, chin + 0.72 * H, w, d, "skin", k=2)                     # skull + face
    rounded(h, 0, -0.1, chin, chin + 0.2 * H, w * 0.72, d * 0.78, "skin", k=2)                  # the jaw
    dome(h, 0, 0.25, chin + 0.62 * H, 0.36 * H, w + 0.3, d + 0.35, "hair", layers=1, top=0.7, k=2)  # hair cap
    h.box_at((0, chin + 0.45 * H, 0.1 + d / 2 + 0.05), (w * 0.9, 0.62 * H, 0.6), "hair")        # the fall
    fz = 0.1 - d / 2
    for s in (1, -1):
        h.box_at((s * 0.2 * H, chin + 0.5 * H, fz - 0.05), (0.16 * H, 0.08 * H, 0.15), "eye")
    rounded(h, 0, 0.15, chin + 0.68 * H, chin + 0.68 * H + 0.55, w + 0.45, d + 0.45, "gold", k=2)
    return h


def arm_b2(m, side, shoulder, up_len, fore_len, w, sleeve, fore, cuff):
    """Round 1's arm without the block fist: the hand is grip_hand's. Returns (upper, fore, the wrist in rest)."""
    pre = "left" if side > 0 else "right"
    sx, sy, sz = shoulder
    ey, wy = sy - up_len, sy - up_len - fore_len
    up = m.bone(pre + "_arm", [sx, sy, sz], parent="body")
    rounded(up, sx, sz, ey - 0.2, sy + 0.6, w, w, sleeve, k=2)
    fo = m.bone(pre + "_fore", [sx, ey, sz], parent=pre + "_arm")
    rounded(fo, sx, sz, wy + 0.3, ey + 0.2, w * 0.88, w * 0.88, fore, k=2)
    fo.box_at((sx, wy + 0.55, sz), (w * 0.98, 0.6, w * 0.98), cuff)
    return pre + "_arm", pre + "_fore", (sx, wy + 0.1, sz)


def hand_kw(fingers):
    from human_kit import glove
    g = glove("skin", "skin_shade", "skin")
    g["fingers"] = fingers
    return g


def grip_hold(m, arm_bones, prop_bone, centre, axis, r, side, back, elbow_at=None, fingers=4, iters=2, on_fore=False):
    """human_kit.hold's loop with a finger count: the hand closed round (centre, axis, r), the arm solved onto its
    wrist with the forearm pointing into the grip; the hand built on the prop's bone (it rides the prop, which is then
    handed to the forearm) or on the forearm (on_fore). Returns the wrist's miss (px)."""
    from gen_models import Bone, transpose
    upper, fore, wrist_rest = arm_bones
    g_kw = hand_kw(fingers)
    used, e = _unit(back), 0.0
    for _ in range(iters):
        used = back
        g = grip_hand(Bone(m, "_scratch", [0, 0, 0]), centre, axis, r, side, "skin", back=used, **g_kw)
        e = m.solve_arm(upper, fore, wrist_rest, g["wrist"], elbow_out=1, elbow_down=1, point_at=g["aim"],
                        avoid=("body",), avoid_margin=0.8, avoid_elbow=True, elbow_at=elbow_at)
        el, wr = m.fk(fore, m.bones[fore].pivot), m.fk(fore, wrist_rest)
        back = _unit(tuple(0.5 * used[k] + 0.5 * (el[k] - wr[k]) / math.dist(el, wr) for k in range(3)))
    if on_fore:
        Rm = m.frame(fore)
        t0 = m.fk(fore, (0.0, 0.0, 0.0))
        c_loc = apply(transpose(Rm), tuple(centre[k] - t0[k] for k in range(3)))
        grip_hand(m.bones[fore], c_loc, apply(transpose(Rm), axis), r, side, "skin",
                  back=apply(transpose(Rm), used), **g_kw)
    else:
        grip_hand(m.bones[prop_bone], centre, axis, r, side, "skin", back=used, **g_kw)
    return e


def open_hand(m, arm_bones, side, fingers=3):
    """A hand hanging easy from the forearm (human_kit.free_hand with fewer fingers), built in rest space."""
    upper, fore, wrist_rest = arm_bones
    grip_hand(m.bones[fore], None, (0, 0, -1), 0.5, side, "skin", back=(0, 1, 0), curl=0.42, wrist=wrist_rest,
              **hand_kw(fingers))


def lantern_b2(m, grip, fore, chain_len, cage=(3.5, 4.5)):
    """The sun-lantern hung from a bar the right fist closes on: chain -> lantern (a pendulum)."""
    hx, hy, hz = grip
    ch = m.bone("chain", [hx, hy, hz])
    ch.box_at((hx, hy, hz), (2.0, 0.6, 0.6), "brass")                                          # the bar in the fist
    ch.box_at((hx, hy - 0.4 - chain_len / 2, hz), (0.45, chain_len, 0.45), "gold_dark")
    top = hy - 0.4 - chain_len
    w, h = cage
    ln = m.bone("lantern", [hx, top, hz])
    ngon_prism(ln, hx, hz, top - 0.7, top - 0.1, w, "brass", n=6)                            # cap
    ln.box_at((hx, top + 0.05, hz), (1.2, 0.6, 1.2), "brass")                                # finial
    ln.box_at((hx, top - h + 0.3, hz), (w * 0.9, 0.6, w * 0.9), "brass")                     # base
    ln.box_at((hx, top - h / 2 - 0.05, hz), (w * 0.58, h - 1.4, w * 0.58), "glow")           # the core
    for a in (0, 60, 120):
        c0 = (hx, top - h / 2, hz)
        ln.box_at(c0, (w * 0.95, h - 1.0, 0.35), "brass", rotation=[0, a, 0], pivot=list(c0))
    attach(m, ch, fore, (hx, hy, hz))
    attach(m, ln, "chain", (hx, top, hz))


def halo_disc_b2(m, centre, diameter, tilt):
    """The Mender's flat gold disc (C's, without the rays): a gold face, the glowing rim."""
    hb = m.bone("halo", list(centre), parent="body", rotation=[tilt, 0, 0])
    cx, cy, cz = centre
    r = diameter / 2
    for w, h in ((r * 2 - 2.4, r * 2 - 0.9), (r * 2 - 0.9, r * 2 - 2.4)):
        hb.box_at((cx, cy, cz + 0.15), (w, h, 0.5), "gold")
    ring(hb, (cx, cy, cz), r - 0.55, 8, 1.1, 0.9, "sun")
    return hb


def build_b2(kind):
    pal = dict(PALETTE)
    pal.update(KIND_PALETTE[kind])
    pal["feather_warm"] = "#eadab4"            # 6: the flights' warm cream (the underside), a step under "feather"
    m = Model("%s - %s (massing B2)" % (NAMES[kind], MASSING["b2"]), "sunforged", ROLES[kind] + ", round 2 body",
              pal, EMISSIVE)
    body = body_b2(m, kind)
    wings_b2(m, kind)
    chin, H = body["chin"], body["H"]
    head_b2(m, chin, H)
    back = 0.1 + 0.86 * H / 2 + 0.35
    if kind == "mender":
        halo_disc_b2(m, (0.0, chin + 0.62 * H, back + 2.4), 9.0, 35.0)
    else:
        # 4: the tube 2.1 px, the ring widened to 10.5 and raised 1 px so its hole stays open over the crown (the
        # first r2 render: a 9 px ring with the thick tube closed into a blob with the head at far)
        halo_ring(m, (0.0, chin + H + 0.4, back + 2.0), 10.5, 2.1, 1.2, 25.0, n=8)
    sx, sy, sz = body["shoulder"]
    la = arm_b2(m, 1, (sx, sy, sz), body["up"], body["fore"], body["arm_w"], body["sleeve"], body["fore_c"],
                body["cuff"])
    ra = arm_b2(m, -1, (-sx, sy, sz), body["up"], body["fore"], body["arm_w"], body["sleeve"], body["fore_c"],
                body["cuff"])
    fz, wy = body["front_z"], body["waist"]
    errs = []
    if kind == "mender":
        # 5: the lamp held up and out beside the shoulder on a short chain, ~0.45 body-widths further out than r1: hung
        # low at the hip it still crossed the robe's hem from the RTS camera's 35 degree yaw (first r2 render)
        g = (-9.0, wy + 3.0, fz - 0.6)
        errs.append(grip_hold(m, ra, None, g, (1.0, 0.0, 0.0), 0.35, -1, (0.5, 0.6, 0.45),
                              elbow_at=(-7.6, wy + 4.4, 0.2), fingers=3, on_fore=True))
        lantern_b2(m, g, ra[1], 3.6)
        errs.append(solve(m, la, (1.6, wy - 0.4, fz - 3.6)))
        open_hand(m, la, 1)
        m.contact_ok += [("right_fore", "chain")]
    elif kind == "archer":
        grip = (6.4, 19.0, -7.2)
        bw, Lx = bow(m, grip, 34.0, 20.0, la[1], fix=False)
        errs.append(grip_hold(m, la, "bow", grip, Lx, 0.62, 1, (-0.1, 0.4, 0.9), elbow_at=(6.4, 20.8, -2.6)))
        attach(m, bw, la[1], grip)
        errs.append(solve(m, ra, (-sx - 0.4, wy - 1.0, fz - 1.2)))
        open_hand(m, ra, -1)
        bz = body["back_z"] + 0.6
        quiver(m, (-0.6, 12.8, bz), (-1.4, 20.2, bz))
    else:
        grip = (-5.6, 21.2, -4.0)
        d = (-0.16, math.cos(math.radians(40)), math.sin(math.radians(40)))
        sw = sword(m, grip, d, 17.0, ra[1], width=1.9, fix=False)
        du = _unit(d)
        errs.append(grip_hold(m, ra, "sword", _add(grip, _mul(du, -0.6)), du, 0.4, -1, (-0.2, -0.5, 0.85),
                              elbow_at=(-6.2, 20.4, 0.2), fingers=3))
        attach(m, sw, ra[1], grip)
        hc = (4.6, wy + 0.5, fz - 3.6)
        errs.append(grip_hold(m, la, None, hc, (0.0, 1.0, 0.0), 0.35, 1, (0.0, 0.3, 1.0),
                              elbow_at=(5.8, wy + 2.6, -0.6), fingers=2, on_fore=True))   # behind the shield
        shield(m, (hc[0] + 0.9, hc[1], hc[2] - 1.1), 9.0, la[1], n=6)
        m.contact_ok += [("left_fore", "shield")]
    m.contact_ok += [("body", "head"), ("body", "left_arm"), ("body", "right_arm"), ("body", "wing_l_arm"),
                     ("body", "wing_r_arm")]
    m.pose = {}
    lo = settle(m)
    return m, errs, lo


# ------------------------------------------------------------------ assembly ------------------------------------
def settle(m):
    """Moves the whole model so its lowest posed point is at y = 0 (the hover pose's reference)."""
    lo = min(p[1] for b in m.bones.values() for c in b.cubes for p in m.cube_corners(b.name, c))
    for b in m.bones.values():
        b.pivot[1] = round(b.pivot[1] - lo, 4)
        for c in b.cubes:
            c["from"][1] = round(c["from"][1] - lo, 4)
            if "pivot" in c:
                c["pivot"][1] = round(c["pivot"][1] - lo, 4)
    return lo


def extent(m, names=None):
    pts = [p for b in m.bones.values() if names is None or names(b.name) for c in b.cubes
           for p in m.cube_corners(b.name, c)]
    return ([min(p[a] for p in pts) for a in range(3)], [max(p[a] for p in pts) for a in range(3)])


def build(massing, kind):
    if massing == "b2":
        return build_b2(kind)
    pal = dict(PALETTE)
    pal.update(KIND_PALETTE[kind])
    m = Model("%s - %s (massing %s)" % (NAMES[kind], MASSING[massing], massing.upper()), "sunforged",
              ROLES[kind] + ", greybox round 1", pal, EMISSIVE)
    body = {"a": body_a, "b": body_b, "c": body_c}[massing](m, kind)
    {"a": wings_a, "b": wings_b, "c": wings_c}[massing](m, kind)
    chin, H = body["chin"], body["H"]
    hd = head(m, chin, H, circlet=(massing == "b"))
    back = 0.1 + 0.86 * H / 2 + 0.35
    if massing == "a":
        halo_ring(m, (0.0, 37.5, back + 1.6), 8.0, 0.9, 1.2, 0.0)
    elif massing == "b":
        halo_ring(m, (0.0, chin + H - 0.6, back + 2.0), 7.0, 0.9, 1.1, 25.0)
    else:
        halo_disc(m, (0.0, 25.0, 8.0), 12.0, 40.0)
    sx, sy, sz = body["shoulder"]
    la = arm(m, 1, (sx, sy, sz), body["up"], body["fore"], body["arm_w"], body["sleeve"], body["fore_c"], body["cuff"])
    ra = arm(m, -1, (-sx, sy, sz), body["up"], body["fore"], body["arm_w"], body["sleeve"], body["fore_c"],
             body["cuff"])
    fz, wy = body["front_z"], body["waist"]
    errs = []
    if kind == "mender" and massing != "c":
        hand = (-2.3, wy - 2.0, fz - 3.6)
        errs.append(solve(m, ra, hand))
        chain_len = 6.0 if massing == "a" else 7.0
        errs.append(solve(m, la, (1.3, wy - 1.0, fz - 3.3)))
        lantern(m, m.fk(ra[1], ra[2]), ra[1], chain_len)
    elif kind == "mender":
        core = (0.0, wy + 1.6, fz - 2.4)
        errs.append(solve(m, la, (1.25, core[1] - 0.2, core[2] + 0.1), elbow_down=2.0))
        errs.append(solve(m, ra, (-1.25, core[1] - 0.2, core[2] + 0.1), elbow_down=2.0))
        lb = m.bone("light", list(core), parent="body")
        lb.box_at(core, (2.2, 2.2, 2.2), "glow")
        lb.box_at(core, (2.2, 2.2, 2.2), "glow", rotation=[45, 45, 0], pivot=list(core))
    elif kind == "archer":
        length = {"a": 38.0, "b": 34.0, "c": 24.0}[massing]
        lean = {"a": 22.0, "b": 20.0, "c": 18.0}[massing]
        grip = {"a": (7.4, 21.0, -6.4), "b": (6.4, 19.0, -7.2), "c": (5.0, 15.6, -5.6)}[massing]
        errs.append(solve(m, la, grip))
        bow(m, m.fk(la[1], la[2]), length, lean, la[1])
        errs.append(solve(m, ra, (-sx - 0.4, wy - 1.0, fz - 1.2)))
        bz = body["back_z"] + 0.6
        qb = {"a": ((-1.0, 15.0, bz + 0.6), (-2.0, 22.5, bz + 0.2)), "b": ((-0.6, 12.8, bz), (-1.4, 20.2, bz)),
              "c": ((0.0, 10.5, bz + 0.2), (-0.4, 16.8, bz + 0.2))}[massing]
        quiver(m, *qb)
    else:   # sword
        if massing == "a":       # the blade raised upright before the face: a cross with the halo
            grip, d, L = (-0.6, 23.4, -6.4), (0.0, 1.0, 0.02), 17.0
        elif massing == "b":     # resting back over the right shoulder at 40 degrees, the tip above the wing line
            grip, d, L = (-5.6, 21.2, -4.0), (-0.16, math.cos(math.radians(40)), math.sin(math.radians(40))), 17.0
        else:                    # a slim blade held forward
            grip, d, L = (-3.4, 15.2, -5.6), (0.05, 0.45, -0.9), 14.5
        errs.append(solve(m, ra, grip))
        sword(m, m.fk(ra[1], ra[2]), d, L, ra[1], width=1.9 if massing != "c" else 1.4)
        if massing in ("a", "b"):
            sc = (5.4, wy + 0.8, fz - 5.0) if massing == "b" else (5.6, wy + 0.8, fz - 4.6)
            errs.append(solve(m, la, (4.4, sc[1] - 0.4, sc[2] + 1.3)))
            hp = m.fk(la[1], la[2])
            shield(m, (hp[0] + 0.9, hp[1], hp[2] - 0.9), 9.0, la[1])
        else:
            errs.append(solve(m, la, (sx + 0.6, wy - 0.6, fz - 1.0)))
    m.contact_ok += [("body", "head"), ("body", "left_arm"), ("body", "right_arm")]
    m.pose = {}
    lo = settle(m)
    return m, errs, lo


def report(m, path):
    nc = sum(len(b.cubes) for b in m.bones.values())
    lo, hi = extent(m)
    body_hi = extent(m, lambda n: not (n.startswith("wing") or n in ("halo", "bow", "sword", "shield")))[1][1]
    span = 2 * max(abs(lo[0]), abs(hi[0]))
    wl, wh = extent(m, lambda n: n.startswith("wing"))
    wspan = wh[0] - wl[0]
    print("%s: %d bones, %d cubes; bbox x %.1f..%.1f y %.1f..%.1f z %.1f..%.1f px; crown %.1f px; wing span %.1f "
          "(%.2fx stature)" % (os.path.basename(path), len(m.bones), nc, lo[0], hi[0], lo[1], hi[1], lo[2], hi[2],
                               body_hi, wspan, wspan / body_hi))
    return nc, (lo, hi)


def write(massing, kind):
    m, errs, _ = build(massing, kind)
    path = os.path.normpath(os.path.join(OUT, "sunforged_angel_%s_%s.json" % (massing, kind)))
    with open(path, "w") as f:
        json.dump(m.to_json(), f, indent=1)
    report(m, path)
    if errs and max(errs) > 0.6:
        print("  WARNING: hand misses (px, per solve): %s" % ", ".join("%.2f" % e for e in errs))
    return path


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--massing", choices=("a", "b", "c", "b2"),
                    help="a / b / c: round 1's greyboxes (kept as the A/B record); b2: round 2's B")
    ap.add_argument("--kind", choices=("mender", "archer", "sword"))
    ap.add_argument("--all", action="store_true")
    a = ap.parse_args()
    if a.all:
        for ms in ("a", "b", "c", "b2"):
            for k in ("mender", "archer", "sword"):
                write(ms, k)
    elif a.massing and a.kind:
        write(a.massing, a.kind)
    else:
        ap.error("give --massing and --kind, or --all")


if __name__ == "__main__":
    main()
