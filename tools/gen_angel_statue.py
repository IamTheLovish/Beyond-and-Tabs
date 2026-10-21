#!/usr/bin/env python3
"""The White Angel capitol's statue: a SCULPTED marble angel, drawn by the game like a unit model (lovish, Oct 20:
"the angel looks bad" - the 13 x 13 block angel on the dome read as a blocky cross with wings; he chose a real statue
drawn as a model, in stone, on the dome, its wings perhaps unfolding as the capitol levels up).

The figure: a robed angel standing on the plinth (tools/capitol_white_angel_v4.py STATUE_ANCHOR), wings spread wide
and up (feathered: an arm along the leading edge, primaries fanned from the hand, secondaries and tertials hanging
from the forearm, two rows of coverts over their roots), head a little raised, a halo ring behind the head, a long
sword planted point-down before it with both hands on the grip under the pommel - the pose library's "guardian at
rest" (gen_paladin.radiant_winged), solved the same way (Model.solve_arm onto gen_models.grip_hand). Material: warm
white marble (materials.py "stone": a soft cloud, thin veins, worn arrises; feathers keep the feather relief carved
in the same marble); gold only on the halo and the sword's guard.

Space is the DSL's (design/models/README.md): px, y up from the soles, the figure facing -Z, +x its left. It is
built at about a person's proportions (stature 50 px) and drawn SCALED in the game (BuildingParts.STATUES: 16 blocks
from the soles to the wing tips), so the texture density is the units' 8 texels a model px.

Bones the game moves: "wing_left" / "wing_right" (BuildingParts.statueWingFold: turned back about the shoulder blade
by the fold, so the wings unfold as the capitol levels up and breathe slowly), "halo" (still). Everything else is
stone and stays still.

    python3 tools/gen_angel_statue.py     # writes design/models/white_angel_statue.json
    python3 tools/dsl_to_game.py design/models/white_angel_statue.json --class WhiteAngelStatueModel \\
        --texture white_angel_statue --layer white_angel_statue_layer
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_models import Model, Bone, OUT, ring, ngon_prism, octagon_pole, octagon_axle, lathe  # noqa: E402
from gen_models import grip_hand, apply, transpose, _unit, _cross                                      # noqa: E402
import gen_models                                                                                      # noqa: E402

PALETTE = {
    # warm white marble in three values (the lit stone, the shade of a fold, the deep cut), the feathers a hair
    # lighter than the robe so the wings read as their own mass against it; gold on the halo and the sword's guard only
    "marble": "#e6dfd2", "marble_shade": "#cdc4b4", "marble_deep": "#a99f8e",
    "feather_marble": "#ece6da", "feather_marble_shade": "#d5ccbd",
    "gold": "#d4a63c",
}
EMISSIVE = []

STATURE = 50.0
SHOULDER_Y, SHOULDER_X = 38.0, 5.6
UPPER, FORE = 9.2, 7.6
WING_ROOT = (2.4, 35.5, 2.6)      # the left wing's root on the shoulder blade (mirrored for the right)
# the wings' planes: square to the shoulders' line (turned back, the far wing went edge-on from the RTS camera's 45
# degree quarter), their tops RECLINED 18 degrees (a positive x tips an upright part's top back) so the camera, which
# looks down 50 degrees, sees their faces - each wing's projected area at the RTS view: 250 px^2 leaned forward 16,
# 490 upright, 690 reclined 16 (tools/angel_statue_preview.py's view; both wings equal); no cup (a cup turns one wing
# toward that view and the other away)
WING_YAW, WING_LEAN = 0.0, 18.0
WING_CUP = 0.0                    # px forward per px^2 out (kept as a knob: wings())
SCALE_BLOCKS = 16.0               # soles to wing tips in the game (BuildingParts.STATUES' height)


def slab(bone, centre, along, across, size, color, **kw):
    """gen_models.slab, nudged off gimbal lock: a slab whose width runs within a degree or two of z (a fold on the
    figure's flank) can land just inside orient()'s regular branch where the 3-decimal Euler angles miss by more than
    its tolerance - turn its width a few degrees about its length and it is exact again (invisible on a fold or a feather)."""
    try:
        return gen_models.slab(bone, centre, along, across, size, color, **kw)
    except AssertionError:
        ez = _unit(along)
        n = _cross(ez, _unit(across))
        for deg in (3, -3, 6, -6):
            a = math.radians(deg)
            turned = tuple(across[i] * math.cos(a) + n[i] * math.sin(a) for i in range(3))
            try:
                return gen_models.slab(bone, centre, along, turned, size, color, **kw)
            except AssertionError:
                continue
        raise


# ------------------------------------------------------------------ turned forms -----------------------------------
def loft(bone, sections, color, n=14, t=0.6, phase=0.5, **kw):
    """A turned surface of elliptical sections (y, rx, rz, cz) - a robe, a sleeve's bell: n leaned slabs per band (the
    gen_models.lathe construction with its own ellipse at every section), so the outline swells and narrows with no
    stairs. Seams overlap a little."""
    for (y0, rx0, rz0, z0), (y1, rx1, rz1, z1) in zip(sections, sections[1:]):
        for k in range(n):
            th = 2 * math.pi * (k + phase) / n
            s, c = math.sin(th), math.cos(th)
            a = (rx0 * s, y0, z0 - rz0 * c)
            b = (rx1 * s, y1, z1 - rz1 * c)
            along = tuple(b[i] - a[i] for i in range(3))
            L = math.dist(a, b) + 0.14
            rm = (math.hypot(rx0 * s, rz0 * c) + math.hypot(rx1 * s, rz1 * c)) / 2
            w = 2 * rm * math.tan(math.pi / n) + 0.22
            out = _unit((s * (rz0 + rz1), 0.0, -c * (rx0 + rx1)))             # the ellipse's outward normal (approx)
            centre = tuple((a[i] + b[i]) / 2 - out[i] * t / 2 for i in range(3))
            across = _unit((c * (rx0 + rx1), 0.0, s * (rz0 + rz1)))
            slab(bone, centre, along, across, (w, t, L), color, **kw)


ROBE = ((0.0, 7.4, 6.0, 0.4), (2.5, 6.9, 5.5, 0.3), (9.0, 5.9, 4.6, 0.1), (17.0, 5.1, 4.0, 0.0),
        (24.0, 4.9, 3.7, 0.0), (27.0, 4.3, 3.2, -0.1), (31.0, 4.8, 3.6, -0.3), (35.0, 5.5, 3.8, -0.2),
        (38.4, 5.6, 3.4, 0.0))


def robe(m):
    body = m.bone("body", [0, 24.0, 0])
    loft(body, ROBE, "marble", n=16, t=0.8)
    ngon_prism(body, 0, 0.0, 37.6, 38.9, 9.6, "marble", n=12)                      # the yoke over the shoulders
    # folds: ridges falling from the girdle to the hem, proud of the robe, catching the light in a rhythm the RTS camera
    # sees as a carved gown and not a cone (front and sides; the back is under the wings)
    for th_deg in (-125, -95, -62, -30, 0, 28, 58, 90, 122):
        th = math.radians(th_deg)
        pts = []
        for (y, rx, rz, cz) in ((26.0, 4.5, 3.4, 0.0), (17.0, 5.15, 4.05, 0.0), (8.0, 6.05, 4.75, 0.1),
                                (0.3, 7.45, 6.05, 0.4)):
            pts.append((rx * math.sin(th), y, cz - rz * math.cos(th)))
        for a, b in zip(pts, pts[1:]):
            along = tuple(b[i] - a[i] for i in range(3))
            out = _unit((math.sin(th), 0.0, -math.cos(th)))
            c = tuple((a[i] + b[i]) / 2 + out[i] * 0.15 for i in range(3))
            slab(body, c, along, (math.cos(th), 0.0, math.sin(th)), (0.9, 0.7, math.dist(a, b) + 0.3), "marble")
        # the shadow beside each fold: a deep cut on its lee side, low on the gown where the folds part
        a, b = pts[2], pts[3]
        side = (math.cos(th), 0.0, math.sin(th))
        c = tuple((a[i] + b[i]) / 2 + side[i] * 0.75 for i in range(3))
        slab(body, c, tuple(b[i] - a[i] for i in range(3)), side, (0.5, 0.35, math.dist(a, b)), "marble_shade")
    # the hem: a heavier band where the gown pools on the plinth
    loft(body, ((0.0, 7.65, 6.25, 0.4), (1.1, 7.5, 6.1, 0.4)), "marble_shade", n=16, t=0.5)
    # the girdle: a cord round the waist, knotted in front, its two ends falling between the folds
    loft(body, ((26.4, 4.55, 3.45, -0.1), (27.5, 4.45, 3.35, -0.1)), "marble_shade", n=16, t=0.45)
    body.box_at((0.0, 26.9, -3.55), (1.4, 1.4, 1.0), "marble_shade", rotation=[0, 0, 45], pivot=[0.0, 26.9, -3.55])
    for s in (1, -1):
        a, b = (s * 0.4, 26.3, -3.7), (s * 1.1, 17.5, -4.3)
        slab(body, tuple((a[i] + b[i]) / 2 for i in range(3)), tuple(b[i] - a[i] for i in range(3)), (1, 0, 0),
             (0.55, 0.5, math.dist(a, b)), "marble_shade")
    # a mantle over the shoulders and down the back, under the wings: what the RTS camera sees between them
    loft(body, ((38.9, 5.9, 3.7, 0.25), (35.0, 6.2, 4.3, 0.5), (30.0, 5.6, 4.5, 0.8)), "marble", n=14, t=0.5,
         phase=0.5)
    return body


# ------------------------------------------------------------------ head, hair, halo -------------------------------
def head(m):
    """A calm carved face: an oval head on a neck, the brow, the nose and the chin modelled (light and shade carry a
    statue's face from above), hair parted and falling to the shoulders, a fillet across the brow."""
    neck_y = SHOULDER_Y + 0.6
    h = m.bone("head", [0, neck_y, -0.2], parent="body")
    octagon_pole(h, 0, -0.2, neck_y - 0.8, neck_y + 2.4, 2.6, "marble")
    hy = neck_y + 2.0                                     # the jaw
    # the head TURNED, not boxed: a ten-sided skull, a narrower jaw, the crown a turned cap of hair
    ngon_prism(h, 0, -0.3, hy + 0.9, hy + 4.6, 4.6, "marble", n=10)                       # the face and skull
    ngon_prism(h, 0, -0.75, hy - 0.1, hy + 1.2, 3.3, "marble", n=8)                       # the jaw and chin
    lathe(h, 0, -0.1, 2.55, 2.75, ((hy + 3.9, 1.0, hy + 5.3, 0.86), (hy + 5.2, 0.86, hy + 6.3, 0.35)),
          "marble_shade", n=10, t=0.7)                                                     # the hair over the crown
    ngon_prism(h, 0, 0.35, hy + 1.2, hy + 4.6, 4.9, "marble_shade", n=10)                 # ... and behind the ears
    slab(h, (0, hy + 2.05, -2.75), (0, 1.5, -0.4), (1, 0, 0), (0.7, 0.75, 1.65), "marble")  # the nose
    slab(h, (0, hy + 3.0, -2.45), (1, 0, 0), (0, 1, -0.3), (0.5, 0.55, 3.2), "marble")     # the brow
    for e in (1, -1):
        h.box_at((e * 0.9, hy + 2.5, -2.3), (1.0, 0.45, 0.3), "marble_deep")              # the eyes' shade
    h.box_at((0, hy + 1.15, -2.42), (1.1, 0.2, 0.3), "marble_shade")                      # the lips' line
    for e in (1, -1):                                                 # a lock over each temple, parted at the brow
        slab(h, (e * 1.35, hy + 4.2, -2.0), (e * 1.0, -0.35, 0.2), (0, 0, 1), (1.2, 0.8, 2.3), "marble_shade")
    for e in (1, -1):                                                 # locks falling behind the ears to the shoulders
        for k, (dx, dz, L) in enumerate(((2.3, 0.0, 5.6), (1.9, 1.5, 6.3), (0.9, 2.4, 6.6))):
            top = (e * dx, hy + 4.2, dz)
            bot = (e * (dx + 0.45), hy + 4.2 - L, dz + 0.6)
            slab(h, tuple((top[i] + bot[i]) / 2 for i in range(3)), tuple(bot[i] - top[i] for i in range(3)),
                 (0, 0, 1), (1.3, 1.1, L), "marble_shade")
    # the halo: a gold ring behind the head on its own bone, tipped forward so the RTS camera sees its face
    hc = (0, hy + 4.4, 4.4)
    halo = m.bone("halo", list(hc), parent="head", rotation=[-14, 0, 0])
    ring(halo, hc, 5.6, 16, 0.8, 0.55, "gold")
    ring(halo, hc, 4.3, 16, 0.35, 0.4, "gold")
    for k in range(8):                                     # a crown of short rays between the two rings' spokes
        a = k * math.pi / 4 + math.pi / 8
        cx, cy = hc[0] + 6.6 * math.cos(a), hc[1] + 6.6 * math.sin(a)
        halo.box_at((cx, cy, hc[2]), (1.4, 0.45, 0.4), "gold", rotation=[0, 0, math.degrees(a)], pivot=[cx, cy, hc[2]])
    return h, hy


# ------------------------------------------------------------------ the wings --------------------------------------
# the leading edge in the wing's plane: (u out from the root, v up) - shoulder, elbow (the carpal bend), wrist, the
# hand's tip; the primaries fan from the hand, the secondaries hang from the forearm, the tertials from the arm
ARM = ((0.0, 0.0), (7.5, 6.5), (16.5, 15.5), (21.5, 24.5))


def _arm_at(t):
    """The leading edge at t in 0..3 (along its three segments)."""
    i = min(2, int(t))
    f = t - i
    (u0, v0), (u1, v1) = ARM[i], ARM[i + 1]
    return (u0 + (u1 - u0) * f, v0 + (v1 - v0) * f)


def wings(m):
    """Spread wings, raised: each its own bone on the shoulder blade, its plane square to the shoulders (WING_YAW) and
    reclined WING_LEAN, built feather by feather (the gen_paladin wings' construction - a vane and a pointed tip per feather,
    each upper row thicker than the one it covers so it lies over it with a visible step - opened out): an arm along
    the leading edge, nine primaries fanned from the hand (the outer ones long and swept up past the head, the inner
    ones turning down), nine secondaries and three tertials hanging from the forearm and the arm, a row of greater
    coverts over their roots and a row of median coverts over those. The flights are louvred 10 degrees about their
    shafts so each one is its own strip of light."""
    for side in (1, -1):
        rx, ry, rz = side * WING_ROOT[0], WING_ROOT[1], WING_ROOT[2]
        wg = m.bone("wing_left" if side > 0 else "wing_right", [rx, ry, rz], parent="body",
                    rotation=[WING_LEAN, -side * WING_YAW, 0])

        def P(u, v, w=0.0):
            """A point of the wing's surface: u out, v up, w off it toward +z (back); the surface may cup forward
            toward its outer edge (WING_CUP * u^2 px - off: see WING_LEAN)."""
            return (rx + side * u, ry + v, rz + w - WING_CUP * u * u)

        def feather(u, v, ang, L, wd, thick, w, col, louvre=0.0):
            """A feather from (u, v) pointing `ang` degrees (0 = straight out, 90 = up, -90 = down) on the surface."""
            a = math.radians(ang)
            du, dv = math.cos(a), math.sin(a)
            vane = L - 0.5 * wd
            top, end = P(u, v, w), P(u + du * vane, v + dv * vane, w)
            d = _unit(tuple(end[k] - top[k] for k in range(3)))
            um = u + du * vane / 2
            side_pt = P(um - dv, v + dv * vane / 2 + du, w)
            mid = P(um, v + dv * vane / 2, w)
            q = tuple(side_pt[k] - mid[k] for k in range(3))
            q = _unit(tuple(q[k] - d[k] * sum(q[j] * d[j] for j in range(3)) for k in range(3)))
            if louvre:
                n = _cross(d, q)
                lv = math.radians(louvre)
                q = tuple(q[k] * math.cos(lv) + n[k] * math.sin(lv) for k in range(3))
            vane = math.dist(top, end)
            c = tuple((top[k] + end[k]) / 2 for k in range(3))
            slab(wg, c, d, q, (wd, thick, vane + 0.02), col)
            s = 0.7 * wd                                                        # the pointed tip: a square turned 45
            d45 = tuple((d[k] + q[k]) / math.sqrt(2) for k in range(3))
            q45 = tuple((q[k] - d[k]) / math.sqrt(2) for k in range(3))
            slab(wg, end, d45, q45, (s, thick, s), col)

        # primaries from the hand (t 2..3), the outermost swept up, the innermost turning down and out
        for k in range(9):
            t = 3.0 - k * 0.12
            u, v = _arm_at(t)
            ang = 74 - k * 11.5
            L = 19.5 - k * 0.55
            feather(u, v - 0.6, ang, L, 3.2, 0.5, 0.0, "feather_marble", louvre=10)
        # secondaries from the forearm (t 1..2), hanging down and a little out; tertials from the arm (t 0..1)
        for k in range(9):
            t = 1.95 - k * 0.11
            u, v = _arm_at(t)
            feather(u, v - 0.8, -58 - k * 3.2, 17.5 - k * 0.15, 3.0, 0.5, 0.0, "feather_marble", louvre=10)
        for k in range(3):
            t = 0.95 - k * 0.3
            u, v = _arm_at(t)
            feather(u + 0.3, v - 0.8, -86 - k * 2, 14.5 - k * 1.5, 2.8, 0.5, 0.0, "feather_marble", louvre=8)
        # the greater coverts over the flights' roots, then the medians over theirs (in front: -w, thicker)
        for k in range(11):
            t = 2.85 - k * 0.27
            u, v = _arm_at(t)
            ang = (34 - k * 9) if t > 2 else (-62 - (2 - t) * 18)
            feather(u, v - 0.9, ang, 8.0, 2.9, 0.95, -0.35, "feather_marble_shade")
        for k in range(10):
            t = 2.6 - k * 0.27
            u, v = _arm_at(t)
            ang = (20 - k * 9) if t > 2 else (-55 - (2 - t) * 18)
            feather(u, v - 0.5, ang, 4.6, 2.6, 1.25, -0.6, "feather_marble")
        # the arm itself: the leading edge, a rounded bar of carved down along the top of the coverts
        for (u0, v0), (u1, v1) in zip(ARM, ARM[1:]):
            A, B = P(u0, v0, -0.4), P(u1, v1, -0.4)
            c = tuple((A[k] + B[k]) / 2 for k in range(3))
            slab(wg, c, tuple(B[k] - A[k] for k in range(3)), (0, 0, 1), (2.6, 2.3, math.dist(A, B) + 1.2), "marble")
        wx, wy, wz = P(*ARM[2], -0.4)
        wg.box_at((wx, wy, wz), (2.4, 2.4, 2.3), "marble", rotation=[0, 0, 45], pivot=[wx, wy, wz])   # the wrist


# ------------------------------------------------------------------ arms, hands, sword -----------------------------
GRIP_R = 0.62
HAND = dict(finger_w=0.62, finger_t=0.52, palm_t=0.78, palm_len=1.9, cuff=None, cuff_len=0.6)
SWORD_X, SWORD_Z = 0.0, -7.4
POMMEL_Y = 31.0         # the pommel's top: the hands under it, at the waist


def sword(m):
    """The long sword planted point-down before the figure: a turned marble pommel, a grip bound in a spiral, a GOLD
    cross-guard with quillons flaring a little up to diamond ends (curved down they read as a gold belt from the
    front), a long blade of the darker vein stone with a pale ridge, tapering to a point on the plinth."""
    b = m.bone("sword", [SWORD_X, 15.0, SWORD_Z], parent="body")
    gx, gz = SWORD_X, SWORD_Z
    ngon_prism(b, gx, gz, POMMEL_Y - 1.6, POMMEL_Y, 2.4, "marble", n=8)                    # pommel
    b.box_at((gx, POMMEL_Y + 0.25, gz), (1.0, 0.6, 1.0), "marble")
    G = POMMEL_Y - 7.2                                                                     # the guard's top
    octagon_pole(b, gx, gz, G, POMMEL_Y - 1.5, 2 * GRIP_R, "marble_shade")                 # the grip
    for k in range(5):                                                                     # its binding
        y = G + 0.6 + k * 1.15
        b.box_at((gx, y, gz), (1.5, 0.3, 1.5), "marble_shade", rotation=[0, 45, 18], pivot=[gx, y, gz])
    b.cube([gx - 1.4, G - 1.6, gz - 0.9], [2.8, 1.6, 1.8], "gold")                         # the guard's block
    for s in (1, -1):                                                                      # quillons curving down
        px, py = gx + s * 1.4, G - 0.8
        for L, t, a in ((2.3, 0.95, 4), (1.9, 0.8, 16)):
            x0 = px if s > 0 else px - L
            b.cube([x0, py - t / 2, gz - 0.6], [L, t, 1.2], "gold", rotation=[0, 0, s * a], pivot=[px, py, gz])
            px += s * L * math.cos(math.radians(a))
            py += L * math.sin(math.radians(a))
        # (1.08, not 1.1: a corner on an exact x.xx5 rounds either way in dsl_to_game.check's order-free
        # signature and the twin cubes' corners sorted apart - a false 1.56 px "moved")
        b.box_at((px, py, gz), (1.08, 1.08, 1.08), "gold", rotation=[0, 0, 45], pivot=[px, py, gz])
    # the blade: from under the guard down to the point on the plinth, tapering; a ridge down its middle
    top, tip = G - 1.6, 0.3
    hw0, hw1 = 2.1, 1.35
    for e in (1, -1):
        p0, p1 = (gx + e * hw0 / 2, top), (gx + e * hw1 / 2, tip + 2.6)
        c = ((p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2, gz)
        slab(b, c, (p1[0] - p0[0], p1[1] - p0[1], 0), (1, 0, 0), (hw0 / 2 + 0.1, 0.6, math.dist(p0, p1) + 0.02),
             "marble_deep")
        P0, T = (gx + e * hw1, tip + 2.6), (gx, tip)
        c = ((P0[0] + T[0]) / 2 - e * 0.2, (P0[1] + T[1]) / 2 + 0.2, gz)
        slab(b, c, (T[0] - P0[0], T[1] - P0[1], 0), (1, 0, 0), (0.9, 0.55, math.dist(P0, T)), "marble_deep")
    b.cube([gx - 0.25, tip + 2.0, gz - 0.45], [0.5, top - tip - 2.2, 0.9], "marble")   # the ridge, catching the light
    return b, G


def arm(m, side):
    """An arm in a wide sleeve: the upper arm turned, the forearm in a bell sleeve that hangs open from the elbow, a
    bare wrist into the hand (solved onto the grip). Returns (upper, fore, wrist point in rest space)."""
    pre = "left" if side > 0 else "right"
    sx, sy = side * SHOULDER_X, SHOULDER_Y - 0.6
    ey = sy - UPPER
    wy = ey - FORE
    up = m.bone(pre + "_arm", [sx, sy, 0], parent="body")
    ngon_prism(up, sx, 0, ey + 0.4, sy + 0.6, 3.5, "marble", n=8)
    up.box_at((sx, sy + 0.2, 0), (3.6, 1.6, 3.6), "marble", rotation=[0, 22.5, 0], pivot=[sx, sy + 0.2, 0])
    fo = m.bone(pre + "_fore", [sx, ey, 0], parent=pre + "_arm")
    octagon_axle(fo, (sx, ey, 0), 3.5, 3.5, "marble")                                     # the elbow in its sleeve
    octagon_pole(fo, sx, 0, wy + 0.4, ey, 2.2, "marble")                                  # the forearm
    # the bell sleeve: flaring from the elbow to an open cuff two thirds of the way down, its hem a heavier band
    loft(fo, ((ey + 0.6, 1.9, 1.9, 0.0), (ey - 3.0, 2.45, 2.6, 0.25), (wy + 2.4, 3.0, 3.4, 0.5)), "marble",
         n=10, t=0.45)
    loft(fo, ((wy + 2.9, 3.05, 3.45, 0.5), (wy + 2.2, 3.1, 3.5, 0.5)), "marble_shade", n=10, t=0.5)
    return pre + "_arm", pre + "_fore", (sx, wy + 0.55, 0)


def hold(m, arm_bones, hand_bone, centre, side, back, elbow_at):
    """A marble hand closed round the grip at `centre` (gen_models.grip_hand) and the arm solved onto its wrist with
    the elbow drawn toward elbow_at (gen_paladin.hold's loop: the hand follows the solved forearm, solve again)."""
    upper, fore, wrist = arm_bones
    F = m.frame(hand_bone)
    used = back
    err = 0.0
    clear = dict(avoid=("body",), avoid_margin=1.0, avoid_elbow=True)
    for _ in range(4):
        used = back
        g = grip_hand(Bone(m, "_scratch", [0, 0, 0]), centre, (0, 1, 0), GRIP_R, side, "marble",
                      accent="marble_shade", back=used, **HAND)
        err = m.solve_arm(upper, fore, wrist, m.fk(hand_bone, g["wrist"]), elbow_out=1, elbow_down=1,
                          point_at=m.fk(hand_bone, g["aim"]), elbow_at=elbow_at, **clear)
        el, wr = m.fk(fore, m.bones[fore].pivot), m.fk(fore, wrist)
        dl = apply(transpose(F), _unit(tuple(el[k] - wr[k] for k in range(3))))
        back = _unit(tuple(0.5 * used[k] + 0.5 * dl[k] for k in range(3)))
    grip_hand(m.bones[hand_bone], centre, (0, 1, 0), GRIP_R, side, "marble", accent="marble_shade", back=used, **HAND)
    print("  %s hand: wrist %.2f px off, arm %s fore %s" % ("left" if side > 0 else "right", err,
                                                             m.bones[upper].rotation, m.bones[fore].rotation))
    return err


# ------------------------------------------------------------------ the whole -------------------------------------
def statue():
    m = Model("White Angel statue (capitol crown)", "sunforged", "building statue (the White Angel capitol)", PALETTE,
              EMISSIVE)
    robe(m)
    wings(m)
    head(m)
    _, G = sword(m)
    hands = m.bone("hands", [0, SHOULDER_Y, 0], parent="body")
    m.contact_ok += [("hands", "sword"), ("hands", "left_fore"), ("hands", "right_fore"), ("body", "wing_left"),
                     ("body", "wing_right"), ("body", "head")]
    la = arm(m, 1)
    ra = arm(m, -1)
    # both hands on the grip under the pommel, the left above the right (the guardian at rest)
    top_hand = (SWORD_X, POMMEL_Y - 2.6, SWORD_Z)
    low_hand = (SWORD_X, POMMEL_Y - 5.0, SWORD_Z)
    # the elbows hang by the flanks a little forward (a person resting both hands on a planted sword), the forearms
    # doing the reaching
    e1 = hold(m, la, "hands", top_hand, 1, _unit((7.2, -2.4, 6.6)), (6.0, 29.0, -1.6))
    e2 = hold(m, ra, "hands", low_hand, -1, _unit((-7.2, 0.2, 6.8)), (-6.0, 28.6, -1.4))
    m.pose = {"head": [10, 0, 0]}          # the head a little raised (a positive x tips an upright part's top back)
    return m, max(e1, e2)


def extent(m):
    """(min y, max y, max |x|, min z, max z) of the posed model, DSL px."""
    pts = [p for b in m.bones.values() for c in b.cubes for p in m.cube_corners(b.name, c)]
    return (min(p[1] for p in pts), max(p[1] for p in pts), max(abs(p[0]) for p in pts),
            min(p[2] for p in pts), max(p[2] for p in pts))


def main():
    m, err = statue()
    path = os.path.normpath(os.path.join(OUT, "white_angel_statue.json"))
    with open(path, "w") as f:
        json.dump(m.to_json(), f, indent=1)
    nc = sum(len(b.cubes) for b in m.bones.values())
    y0, y1, hx, z0, z1 = extent(m)
    k = SCALE_BLOCKS * 16.0 / (y1 - y0)
    print(f"wrote {path}: {len(m.bones)} bones, {nc} cubes, wrist error {err:.2f} px")
    print(f"extent: y {y0:.1f}..{y1:.1f} px, half span {hx:.1f} px, z {z0:.1f}..{z1:.1f}; at {SCALE_BLOCKS:.0f} blocks "
          f"tall the model scale is {k:.3f} (figure {STATURE * k / 16:.1f} blocks, span {2 * hx * k / 16:.1f} blocks, "
          f"depth {(z1 - z0) * k / 16:.1f})")


if __name__ == "__main__":
    main()
