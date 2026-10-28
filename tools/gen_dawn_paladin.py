#!/usr/bin/env python3
"""The Dawn Paladin (dawn_paladin_unit, the Sunforged HERO) on the human body (tools/human_kit.py) - until Oct 21 a
repainted villager (PR #114's tools/hd_villager.py skin, whose outfit is this brief).

The faction's champion on foot - the man the Radiant Paladin's order is named for: a BASCINET with a high pointed
skull over a mail AVENTAIL that falls to the shoulders, the face open in its opening (a lean, clean-shaven face in
the steel's shade), a gilt brow band with the sun; a SURCOAT in the owner's colour over the harness, a great gilt SUN
on its breast and gold at every edge, split front and back below the belt; plate on the arms and legs - spaulders in
lames, couters, vambraces, cuisses, poleyns, greaves and sabatons, all turned (no box corners); a mail hauberk's skirt
under the surcoat. An arming sword in the right hand, a HEATER SHIELD on the left arm in the owner's colour with the
sun in gold (his Shield Charge).

Rig (DawnPaladinBodyUnitModel / tools/human_base.py "dawn_paladin"): the human base; the surcoat's skirts in two
panels (surcoat_front / surcoat_back) that swing with the thighs; the sword rides the right forearm, the shield the
left.

    python3 tools/gen_dawn_paladin.py && python3 tools/game_preview.py design/models/sunforged_dawn_paladin_v2.json \\
        --team tabard,tabard_dark --out /tmp/dawn_paladin.png
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import human_kit as hk  # noqa: E402
from human_kit import HIP, KNEE, SHOULDER_X, SHOULDER_Y, UPPER, FORE, HEAD_Y, TRUNK, BELT  # noqa: E402
from gen_models import Model, OUT, slab, lathe, foot, octagon_axle, octagon_pole, split_above, _unit  # noqa: E402
from gen_levy import ngon  # noqa: E402
from gen_halberdier import shell  # noqa: E402
from gen_paladin import octagon, loft  # noqa: E402

PALETTE = dict(hk.BASE_PALETTE, **{
    "tabard": "#2f5fb3", "tabard_dark": "#244a8c",
    "plate": "#d8d1c2", "plate_dark": "#a39b8a", "mail": "#8f939a",
    "steel": "#a3aab6", "steel_edge": "#d0d6df",
    "leather": "#5b3f2a", "hair": "#3e2c20", "wood": "#7a5536",
    "skin": "#c4906f",
})
EMISSIVE = ["sun"]

CZ = -0.15
SUR_HEM = 9.6                  # the surcoat's hem: a hand over the knee
SUR_HINGE = sum(BELT) / 2


def harness(m):
    """The trunk in mail (the hauberk) with its skirt over the hips; the surcoat over it all from the shoulders to the
    knee - the breast part on the trunk, the skirts split front and back on their own bones (split_above)."""
    b = m.bone("body", [0, HIP, 0])
    bands = list(TRUNK)
    hk.trunk(b, "mail", bands=bands)
    # the hauberk's skirt reaches lower than the gambeson's: to mid-thigh, under the surcoat's skirts
    octagon(b, 9.9, 11.0, 6.3, 4.7, 0.85, "mail", zf=-2.5)
    # the surcoat's body: a sleeveless bib over the chest, front and back, to the belt (gen_levy.tabard's bib, wider)
    ct = TRUNK[3][1]
    for name, z, rot, s in (("surcoat_front", -3.05, [5, 0, 0], -1), ("surcoat_back", 2.25, [-5, 0, 0], 1)):
        p = m.bone(name, [0, SUR_HINGE, z], parent="body", rotation=rot)
        W = 6.2
        # the cloth in two leaves turned 8 degrees off the centre line: it wraps the body (a flat board read as a sign)
        for sx in (1, -1):
            p.box_at((sx * W / 4, (ct + SUR_HEM) / 2, z + s * 0.17), (W / 2 + 0.06, ct - SUR_HEM, 0.45), "tabard",
                     rotation=[0, sx * s * 8, 0], pivot=[0, (ct + SUR_HEM) / 2, z])
        p.box_at((0, SUR_HEM + 0.22, z), (W + 0.1, 0.44, 0.55), "gold")                          # the hem
        for x in (-1, 1):                                                                       # the side edges
            p.box_at((x * (W / 2 - 0.05), (ct + SUR_HEM) / 2, z), (0.26, ct - SUR_HEM, 0.55), "gold")
        # folds on the skirt: a ridge and its shadow
        for fx, fh in ((-1.6, 4.4), (1.5, 3.4)) if s < 0 else ((0.4, 5.0), (-1.8, 3.0)):
            p.box_at((fx, SUR_HEM + 0.45 + fh / 2, z + s * 0.25), (0.55, fh, 0.32), "tabard")
            p.box_at((fx + 0.42, SUR_HEM + 0.5 + fh / 2, z + s * 0.2), (0.26, fh - 0.4, 0.14), "tabard_dark")
        if s < 0:
            sun(p, (0, 20.0, z - 0.3))
        else:
            # the back: a gilt cross of the order between the shoulder blades
            p.box_at((0, 20.2, z + 0.3), (0.7, 3.4, 0.16), "gold")
            p.box_at((0, 20.9, z + 0.3), (2.4, 0.7, 0.16), "gold")
        split_above(m, name, SUR_HINGE, "body")
    # the yoke over the shoulders: the surcoat's top turned back over the chest to the shoulder line (gen_levy.tabard)
    for z0, z1 in ((-3.05, TRUNK[4][3] - 0.05), (2.25, TRUNK[4][4] + 0.05)):
        yb, yt = ct - 0.1, TRUNK[4][1] + 0.05
        L = math.hypot(yt - yb, z1 - z0) + 0.1
        lean = [math.degrees(math.atan2(z1 - z0, yt - yb)), 0, 0]
        b.box_at((0, (yb + yt) / 2, (z0 + z1) / 2), (5.2, L, 0.4), "tabard", rotation=lean,
                 pivot=[0, (yb + yt) / 2, (z0 + z1) / 2])
    # the sword belt over the surcoat, low on the hips, a gilt buckle; the scabbard's locket on the left hip
    octagon(b, BELT[0] - 0.3, BELT[1] - 0.3, 6.5, 5.9, 1.0, "leather", zf=-3.45)
    b.box_at((0, sum(BELT) / 2 - 0.3, -3.55), (1.2, 1.2, 0.3), "gold")
    b.box_at((0, sum(BELT) / 2 - 0.3, -3.72), (0.5, 0.5, 0.14), "sun")
    sw = (3.55, BELT[0] - 0.4, -0.2)
    rot = [-22, 0, 6]
    b.cube([sw[0] - 0.4, sw[1] - 7.6, sw[2] - 0.6], [0.8, 7.6, 1.2], "leather", rotation=rot, pivot=list(sw))
    b.cube([sw[0] - 0.45, sw[1] - 8.1, sw[2] - 0.65], [0.9, 0.6, 1.3], "gold", rotation=rot, pivot=list(sw))
    b.cube([sw[0] - 0.45, sw[1] - 0.8, sw[2] - 0.65], [0.9, 0.8, 1.3], "gold_dark", rotation=rot, pivot=list(sw))
    m.contact_ok += [("body", n) for n in ("left_thigh", "right_thigh")]
    return b


def sun(bone, c, k=1.0, depth=0.2):
    """The order's sun in gold: a diamond with a bright core and eight rays round it."""
    x, y, z = c
    bone.box_at((x, y, z), (1.9 * k, 1.9 * k, depth), "gold", rotation=[0, 0, 45], pivot=[x, y, z])
    bone.box_at((x, y, z - 0.12), (0.95 * k, 0.95 * k, depth), "sun", rotation=[0, 0, 45], pivot=[x, y, z - 0.12])
    for i in range(8):
        a = i * math.pi / 4
        L = (1.2 if i % 2 == 0 else 0.8) * k
        r = 1.45 * k + L / 2
        cx, cy = x + r * math.cos(a), y + r * math.sin(a)
        bone.box_at((cx, cy, z), (L, 0.32 * k, depth), "gold", rotation=[0, 0, math.degrees(a)], pivot=[cx, cy, z])


SPAULDER = ((-1.15, 2.1, 1.48, 26, "tabard"), (0.3, 1.6, 1.4, 44, "plate_dark"), (0.75, 1.3, 1.33, 60, "plate"))


def plate_arm(m, side, parent):
    """Plate over mail: a spaulder of three lames bent round the shoulder (gen_squire's), the mail sleeve, the
    rerebrace and vambrace turned, the couter a joint round the elbow's hinge with its fan, a bell cuff."""
    pre = "left" if side > 0 else "right"
    sx, sy = side * SHOULDER_X, SHOULDER_Y
    elbow_y, wrist_y = sy - UPPER, sy - UPPER - FORE
    up = m.bone(pre + "_arm", [sx, sy, 0], parent=parent)
    ngon(up, sx, 0, sy - 2.6, sy - 0.5, 2.05, "mail", n=6, phase=30)                           # the mail sleeve
    octagon_pole(up, sx, 0, elbow_y + 0.4, sy - 2.3, 2.1, "plate")                             # rerebrace
    P = (sx, sy - 1.5, 0.0)
    for (a, L, r, droop, col) in SPAULDER:
        x0 = P[0] + side * a if side > 0 else P[0] + side * (a + L)
        for phi in (-54, 0, 54):
            c = 2 * r * math.tan(math.radians(27)) + 0.06
            up.cube([x0, P[1] + r - 0.32, -c / 2], [L, 0.32, c], col, rotation=[phi, 0, -side * droop], pivot=list(P))
    # the spaulder's gilt edge on its lowest lame
    fo = m.bone(pre + "_fore", [sx, elbow_y, 0], parent=pre + "_arm")
    octagon_axle(fo, (sx + side * 0.1, elbow_y, 0), 2.15, 2.3, "plate")                        # couter
    fo.box_at((sx + side * 1.18, elbow_y, 0.1), (0.28, 1.55, 1.55), "plate_dark", rotation=[45, 0, 0],
              pivot=[sx + side * 1.18, elbow_y, 0.1])
    octagon_pole(fo, sx, 0, wrist_y + 0.4, elbow_y - 0.4, 1.9, "plate")                        # vambrace
    octagon_pole(fo, sx, 0, wrist_y - 0.05, wrist_y + 0.8, 2.3, "plate_dark")                  # bell cuff
    return pre + "_arm", pre + "_fore", (sx, wrist_y + 0.3, 0)


GAUNT = dict(finger_w=0.42, finger_t=0.42, palm_t=0.6, palm_len=1.65, cuff_len=0.7, accent="plate_dark",
             cuff="plate_dark", knuckle="plate", heel="plate_dark")
CUISSE = ((HIP + 0.8, -1.65, 1.3, 1.6), (KNEE + 0.25, -1.0, 1.0, 1.08))
GREAVE = ((KNEE + 0.3, -0.92, 0.88, 0.95), (KNEE - 2.4, -0.84, 1.3, 1.12), (1.3, -0.72, 0.66, 0.76))


def plate_legs(m):
    """Plate turned over the leg (gen_halberdier's, at this figure, the thighs root bones for the gait's IK): the
    cuisse, the poleyn a joint round the knee's hinge with a gilt rivet, the greave with its calf, the sabaton."""
    feet = []
    for side, thigh, shin in ((1, [5, 0, 2], [-7, 0, 0]), (-1, [-4, 0, -2], [-3, 0, 0])):
        pre = "left" if side > 0 else "right"
        hx = side * 1.6
        th = m.bone(pre + "_thigh", [hx, HIP, 0], rotation=thigh)
        loft(th, hx, CUISSE, "plate", n=6, phase=0.0)
        sh = m.bone(pre + "_shin", [hx, KNEE, 0], parent=pre + "_thigh", rotation=shin)
        octagon_axle(sh, (hx, KNEE, -0.2), 2.2, 2.2, "plate")                                    # poleyn
        sh.box_at((hx, KNEE, -1.32), (0.4, 0.4, 0.14), "gold", rotation=[0, 0, 45], pivot=[hx, KNEE, -1.32])
        loft(sh, hx, GREAVE, "plate")
        feet.append(foot(m, pre, hx, 0.0, 1.95, 4.7, 1.2, "plate", "plate_dark", strap="plate_dark"))
    return feet


def bascinet(m):
    """The bare head under a bascinet: a high skull rising to a point over the back of the head (a lathe turned to a
    point), the face open, a gilt brow band with the sun; the mail aventail laced to the helm's edge and falling over
    the neck and the shoulders (a shell round the back and sides, open at the face, closing under the chin)."""
    h = hk.bare_head(m, hair="hair", beard=None, beard_len=0.0, hair_top=False)
    hy = HEAD_Y
    cz = -0.05
    # the aventail: from the helm's lower edge round the face, the cheeks and the neck down onto the shoulders
    shell(h, 0, cz, hy - 0.5, 3.0, hy + 1.6, 2.5, -150, 150, 10, 0.38, "mail")
    shell(h, 0, cz, hy + 1.55, 2.5, hy + 3.15, 2.42, 42, 318, 9, 0.38, "mail")
    shell(h, 0, cz, hy + 0.2, 2.62, hy + 1.65, 2.5, -42, 42, 3, 0.38, "mail")                  # under the chin
    # the skull: from the brow (its edge round the face) up to the point
    shell(h, 0, cz, hy + 3.1, 2.48, hy + 4.4, 2.42, -180, 180, 12, 0.5, "steel")
    lathe(h, 0, cz + 0.25, 2.42, 2.42, ((hy + 4.35, 1.0, hy + 5.4, 0.8), (hy + 5.35, 0.8, hy + 6.3, 0.46),
                                         (hy + 6.25, 0.46, hy + 6.95, 0.12)), "steel", t=0.55)
    h.box_at((0, hy + 6.95, cz + 0.45), (0.45, 0.45, 0.45), "steel", rotation=[45, 45, 0],
             pivot=[0, hy + 6.95, cz + 0.45])                                                    # the point
    # the gilt brow band round the helm's edge, the sun at its front
    shell(h, 0, cz, hy + 3.3, 2.56, hy + 3.75, 2.53, -180, 180, 12, 0.24, "gold")
    sp = (0, hy + 3.95, cz - 2.62)
    h.box_at(sp, (1.0, 1.0, 0.22), "gold", rotation=[0, 0, 45], pivot=list(sp))
    h.box_at((0, sp[1], sp[2] - 0.12), (0.5, 0.5, 0.14), "sun", rotation=[0, 0, 45], pivot=[0, sp[1], sp[2] - 0.12])
    # the vervelles: the aventail's lacing studs along the helm's edge
    for th in (-120, -90, -60, 60, 90, 120, 180):
        a = math.radians(th)
        x, z = -2.6 * math.sin(a), cz - 2.6 * math.cos(a)
        h.box_at((x, hy + 3.15, z), (0.28, 0.28, 0.28), "steel_dark")
    hk.finish_head(h)
    return h


def heater(m, fore, centre):
    """The heater shield on the left forearm: a board in the owner's colour, its outline a heater's (a flat top, the
    sides curving to a point), a steel rim, the sun in gold, slightly curved (two leaves)."""
    x, y, z = centre
    sb = m.bone("shield", list(centre))
    W, H = 6.2, 7.6
    t = 0.45
    # the field: the upper rectangle and the point built as rows narrowing (rotated plates give the curve)
    sb.box_at((x, y + H * 0.18, z), (W, H * 0.64, t), "tabard")
    for i, (yy, ww) in enumerate(((y - H * 0.2, W * 0.86), (y - H * 0.31, W * 0.62), (y - H * 0.4, W * 0.34))):
        sb.box_at((x, yy, z), (ww, H * 0.12, t), "tabard")
    # the rim: the top edge and the sides running down into the point
    sb.box_at((x, y + H * 0.5 - 0.15, z - 0.04), (W + 0.2, 0.36, t + 0.14), "steel")
    pts = ((W / 2 + 0.05, H * 0.5), (W / 2 + 0.05, -H * 0.12), (W * 0.36, -H * 0.3), (0.0, -H * 0.47))
    for s in (1, -1):
        for (xa, ya), (xb, yb) in zip(pts, pts[1:]):
            a = (x + s * xa, y + ya, z - 0.04)
            b = (x + s * xb, y + yb, z - 0.04)
            slab(sb, tuple((a[k] + b[k]) / 2 for k in range(3)), tuple(b[k] - a[k] for k in range(3)), (0, 0, 1),
                 (0.36, t + 0.14, math.dist(a, b) + 0.3), "steel")
    sun(sb, (x, y + 0.6, z - t / 2 - 0.12), k=1.05)
    return sb


def arming_sword(bone, grip, axis):
    """An arming sword through the fist along `axis` (the blade's way): leather grip, a gilt cross and pommel with the
    sun, a steel blade with a fuller and a bright edge."""
    a = _unit(axis)
    up = (0.0, 1.0, 0.0) if abs(a[1]) < 0.9 else (1.0, 0.0, 0.0)
    flat = _unit(tuple(up[k] - a[k] * sum(up[j] * a[j] for j in range(3)) for k in range(3)))

    def P(t):
        return tuple(grip[k] + a[k] * t for k in range(3))
    hk.axis_pole(bone, P(-1.2), P(1.1), 0.55, "leather")
    slab(bone, P(1.35), flat, a, (0.5, 0.55, 3.0), "gold")                                       # the cross
    hk.axis_pole(bone, P(-1.75), P(-1.15), 0.95, "gold")                                       # pommel
    hk.axis_pole(bone, P(-1.9), P(-1.7), 0.5, "sun")
    L = 9.6
    slab(bone, P(1.6 + (L - 1.4) / 2), a, flat, (1.2, 0.32, L - 1.4), "steel")
    slab(bone, P(1.6 + L - 0.9), a, flat, (0.8, 0.32, 1.0), "steel")
    slab(bone, P(1.6 + L - 0.25), a, flat, (0.34, 0.3, 0.5), "steel")
    slab(bone, P(1.6 + (L - 2.0) / 2 + 0.3), a, flat, (0.3, 0.42, L - 3.0), "steel_edge")       # the fuller's light


def dawn_paladin():
    m = Model("Dawn Paladin (v2)", "sunforged", "hero - vanguard (on the human body)", PALETTE, EMISSIVE)
    harness(m)
    bascinet(m)
    feet = plate_legs(m)
    ra, rf, rw = plate_arm(m, -1, "body")
    la, lf, lw = plate_arm(m, 1, "body")
    # the sword: in the right fist at the hip, the blade forward and a little down (a guard at rest)
    C = (-(SHOULDER_X + 0.55), 14.6, -2.6)
    ax = _unit((0.05, 0.25, -1.0))
    sw = m.bone("sword", list(C))
    arming_sword(sw, C, ax)
    e1 = hk.hold(m, ra, rf, rw, "right_fore", C, ax, 0.32, -1, elbow_at=(-(SHOULDER_X + 0.8), 18.3, 0.8),
                 back=_unit((0.0, 0.2, 1.0)), colour="plate", accent="plate_dark", cuff="plate_dark",
                 avoid=("body", "surcoat_front"), on_fore=True)
    hk.attach(m, sw, rf, C)
    # the shield on the left forearm: the arm bent, the forearm across the front of the body, the board before it
    m.bones[la].rotation = [22, 0, 10]
    m.bones[lf].rotation = [62, 0, 0]
    hk.free_hand(m, lf, 1, lw, colour="plate", accent="plate_dark", cuff="plate_dark", curl=0.95)
    fore_mid = m.fk(lf, (SHOULDER_X, SHOULDER_Y - UPPER - FORE * 0.55, 0.0))
    sc = (fore_mid[0] + 1.0, fore_mid[1] - 0.4, fore_mid[2] - 0.9)
    sb = heater(m, lf, sc)
    hk.attach(m, sb, lf, sc, turn=(0, -32, 0))
    m.contact_ok += [("sword", "right_fore"), ("shield", "left_fore"), ("shield", "left_arm")]
    for ft, shin in feet:
        m.ground_leg(ft, also=(shin,))
    m.pose = {"head": [-3, 0, 0]}
    return m, e1


MODELS = {"sunforged_dawn_paladin_v2": dawn_paladin}


def main():
    only = sys.argv[1:]
    for name, build in MODELS.items():
        if only and name not in only:
            continue
        m, err = build()
        path = os.path.normpath(os.path.join(OUT, name + ".json"))
        with open(path, "w") as f:
            json.dump(m.to_json(), f, indent=1)
        nc = sum(len(b.cubes) for b in m.bones.values())
        print(f"wrote {path}: {len(m.bones)} bones, {nc} cubes, wrist error {err:.2f} px")


if __name__ == "__main__":
    main()
