#!/usr/bin/env python3
"""The Sunforged Crossbow Levy (pillager_unit in the game) - a T1 skirmisher body through the Paladin pipeline
(claude/paladin-radiant.md, claude/sunforged-t1-silhouettes.md).

Silhouette: an average-height levy in a quilted gambeson under a WIDE-BRIMMED kettle hat (a ring from the RTS
camera), carrying the one oversized prop - a heavy crossbow held across the body, its steel prod wider than his
shoulders (the horizontal bar that names his role) - and a tall pavise slung on his back, painted in team colour
with the sun (the big colour the camera sees from behind and above). A face in the mail coif's opening - a bearded
commoner, his brow, eyes and nose in the brim's shade (face()). Detail where it reads: the quilting channels, the belt
over the tabard, a bolt quiver at the right hip with white fletchings, a pouch at the left, the crossbow's stirrup,
nut, trigger lever, windlass and string.

Forms (lovish, Oct 16 07:14: "the blocks your using are sticking out from the characters"): what is round is an EXACT
polygon - ngon() bands for the hat's 16-gon brim, lip and band, the coif, mantle, shoulder caps and crown lid as
octagons, the sleeves and bracers as hexagons - not two crossed boxes (a plus sign with notched corners); faces are
facets (plate()), trims run flush with what they edge.

Rig for the game: both arms and the crossbow hang from one bone on the shoulder line ("xbow_arms"); the gauntlets
are ON the crossbow (its pivot is the right-hand grip), the arms solved onto them - raising the rig and levelling
the crossbow is the aim; the tabard hangs in a front and a back panel.

    python3 tools/gen_levy.py && python3 tools/game_preview.py design/models/sunforged_crossbow_levy_v2.json ...
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_models import Model, OUT  # noqa: E402
from gen_paladin import octagon  # noqa: E402
from gen_models import lathe, slab, grip_hand, octagon_axle  # noqa: E402
from gen_squire import oct_at  # noqa: E402

PALETTE = {
    "cloth": "#e4dccb", "cloth_dark": "#b3a891", "mail": "#8f939a",
    "steel": "#a3aab6", "steel_dark": "#7c828d", "gold": "#d4a63c", "gold_dark": "#a87f24",
    "tabard": "#2f5fb3", "tabard_dark": "#244a8c",
    "leather": "#6b4a30", "glove": "#b48c5c", "leather_dark": "#4a3220", "wood": "#7a5536", "hose": "#766959", "boot": "#664631",
    # the boots in three leathers (lovish, Oct 16 12:09: "not just a one texture leather boot"): the upper, a darker
    # sole and heel, the toe rubbed lighter, the turned-down cuff showing the leather's paler flesh side
    "boot_sole": "#2f2119", "boot_worn": "#84603f", "boot_turn": "#8f6e4e",
    "string": "#efe6d2", "feather": "#f2eee6", "sun": "#ffd96a",
    "wood_dark": "#5e4129", "glove_dark": "#8e6a46", "cord": "#c9b58c",
    # the face (07:13): weathered skin, a brown beard, the eyes dark in their sockets ("plain" material - no name
    # fragment of tools/materials.py in them)
    "skin": "#c28c66", "beard": "#8a5f3e", "socket": "#7c523a", "eye": "#2a1d17",
}
EMISSIVE = ["sun"]
FRONT = 0.56
PROD = 9.5                    # each half of the prod (the art director: the crossbow, not the hat, is the signature)
# The prod's arc (lovish, Oct 16 04:34: "the crossbow looks like three blocks put together"): each half is an arc of
# radius PROD_R swept back toward him, built from PROD_SEG rotated segments that taper to the tips
PROD_R, PROD_SEG = 16.1, 9
# the mechanism's numbers, shared with CrossbowLevyUnitModel.java (its XB_* constants are these, in crossbow space):
# the prod's centre, the string's drawn point (the nut) and the claw's parking spot, all on the stock's centre line
XB = {}

HIP = 13.0
SHOULDER_X, SHOULDER_Y = 3.9, 22.2
UPPER, FORE = 4.8, 4.4


def torso(m):
    b = m.bone("body", [0, HIP, 0])
    octagon(b, 9.4, 13.6, 6.2, 4.4, 0.8, "cloth", zf=-4.4 * FRONT)                                   # gambeson skirt
    for (y0, y1, w, d, ch) in ((13.2, 16.0, 5.4, 3.9, 0.7), (15.8, 21.4, 6.4, 4.4, 0.9)):
        octagon(b, y0, y1, w, d, ch, "cloth", zf=-d * FRONT)
    # quilting (task Oct 16: "make them read as stitched quilting"): the gambeson's channels - evenly spaced seams
    # running DOWN the sides where the arms, tabard and pavise leave it bare, on the chest and on the skirt (the few
    # odd-length strips across it read as random scratches; two across the back hid behind the pavise)
    for s in (1, -1):
        for zc in (-0.84, 0.36):
            b.box_at((s * 3.2, 18.4, zc), (0.12, 5.2, 0.13), "cloth_dark")
            b.box_at((s * 3.1, 11.45, zc), (0.12, 3.9, 0.13), "cloth_dark")
    # the belt OVER the tabard (it cinches the panels to him and hides the skirt's hinge): it passes in front of the
    # front panel and inside the back one; the buckle on it
    octagon(b, 14.5, 15.55, 6.0, 5.1, 1.0, "leather", zf=-3.3)
    b.box_at((0, 15.02, -3.38), (1.1, 1.25, 0.3), "gold")                                            # buckle
    # the bolt quiver at the right hip, its bolts' white fletchings showing; the mouth's rim flush with the case
    qp = (-3.6, 14.2, 1.0)
    qr = [8, 0, -12]
    b.cube([qp[0] - 0.95, 9.9, qp[2] - 0.75], [1.9, 5.4, 1.5], "leather", rotation=qr, pivot=list(qp))
    b.cube([qp[0] - 1.0, 14.95, qp[2] - 0.8], [2.0, 0.4, 1.6], "leather_dark", rotation=qr, pivot=list(qp))
    for k, (dx, dz) in enumerate(((-0.45, -0.3), (0.42, -0.15), (0.0, 0.38))):     # the bolts point down: their
        b.cube([qp[0] + dx - 0.3, 15.15 + 0.22 * k, qp[2] + dz - 0.05], [0.6, 1.0, 0.1], "feather", rotation=qr,
               pivot=list(qp))                                                      # fletchings stand in the mouth
    # a tool pouch at the left hip (the crank that hung here is now the crossbow's own geared windlass): the flap folds
    # over its top and down its face, no wider than the pouch
    b.cube([3.05, 11.7, -0.95], [0.95, 2.2, 1.9], "leather")
    b.cube([3.05, 13.55, -1.0], [1.0, 0.4, 2.0], "leather_dark")                                         # its flap
    b.cube([3.95, 12.6, -1.0], [0.12, 1.25, 2.0], "leather_dark")
    return b


def tabard(m):
    """The owner's colour down his front: a bib on the chest (the body's own) and two skirt panels below the belt that
    swing (tabard_front / tabard_back, hinged at the belt, under it), gold-edged. The bib turns back at its top as a
    yoke over the upper chest to the shoulder line, under the coif's mantle at the neck - its top edge stood 0.8 px
    off the upper chest as a ledge ("blocks sticking out", lovish Oct 16 07:14). The emblem moved from the chest (the
    strap crosses it now) to the front panel."""
    bottom = 9.0
    for name, z, rot, s in (("tabard_front", -2.75, [4, 0, 0], -1), ("tabard_back", 2.25, [-4, 0, 0], 1)):
        p = m.bone(name, [0, 15.0, z], parent="body", rotation=rot)
        p.cube([-2.6, bottom, z - 0.25], [5.2, 15.0 - bottom, 0.5], "tabard")
        p.cube([-2.65, bottom - 0.05, z - 0.3], [5.3, 0.45, 0.6], "gold")                             # the hem
        for x in (-2.65, 2.35):                                                                        # its long edges
            p.cube([x, bottom + 0.4, z - 0.3], [0.3, 15.0 - bottom - 0.4, 0.6], "gold")
        if s < 0:
            # the sun on the front panel, under the belt: clear of the crossbow and the strap, seen from the front
            # and from the RTS camera between his knees
            ez = z - 0.33
            p.box_at((0, 11.9, ez), (1.5, 1.5, 0.2), "gold", rotation=[0, 0, 45], pivot=[0, 11.9, ez])
            p.box_at((0, 11.9, ez - 0.1), (0.7, 0.7, 0.2), "sun", rotation=[0, 0, 45], pivot=[0, 11.9, ez - 0.1])
    b = m.bones["body"]
    # (the bib's panel stays where the arm solves were made against it: solve_arm avoids it)
    b.cube([-2.6, 15.0, -3.0], [5.2, 6.4, 0.5], "tabard")                                             # the bib
    for x in (-2.65, 2.35):
        b.cube([x, 15.0, -3.05], [0.3, 6.4, 0.6], "gold")                                             # its edges
    # the yoke: from the bib's top, turned back over the upper chest to the top of the shoulders (under the mantle at
    # the neck), its gold edges carried up with it
    yb, yt = 21.3, 23.0
    zb, zt = -2.8, -2.2
    L = math.hypot(yt - yb, zt - zb) + 0.1
    yc, zc = (yb + yt) / 2, (zb + zt) / 2
    lean = [math.degrees(math.atan2(zt - zb, yt - yb)), 0, 0]
    b.box_at((0, yc, zc), (5.2, L, 0.4), "tabard", rotation=lean, pivot=[0, yc, zc])
    for x in (-2.5, 2.5):
        b.box_at((x, yc, zc), (0.3, L, 0.5), "gold", rotation=lean, pivot=[0, yc, zc])


def pavise(body):
    """The tall shield slung on his back: a wooden board, steel-bound, its team-colour face raised on the binding (a
    clean steel border round it; the five-piece rim frame's corners and ends stood out), a ridge, the sun in gold; its
    strap across his chest."""
    c = (0.8, 19.2, 3.4)          # its top under the hat brim (at 20.2 it cut the brim by 0.6-0.8 px, Oct 15 review)
    rot = [-10, 0, -14]
    w, h = 6.8, 13.0
    body.cube([c[0] - w / 2, c[1] - h / 2, c[2]], [w, h, 0.7], "wood", rotation=rot, pivot=list(c))
    body.cube([c[0] - w / 2 + 0.12, c[1] - h / 2 + 0.12, c[2] + 0.6], [w - 0.24, h - 0.24, 0.12], "steel", rotation=rot,
              pivot=list(c))                                                         # the steel binding on its face
    body.cube([c[0] - w / 2 + 0.4, c[1] - h / 2 + 0.4, c[2] + 0.55], [w - 0.8, h - 0.8, 0.4], "tabard", rotation=rot,
              pivot=list(c))
    body.cube([c[0] - 0.6, c[1] - h / 2 + 0.4, c[2] + 0.8], [1.2, h - 0.8, 0.3], "tabard_dark", rotation=rot,
              pivot=list(c))                                                                              # the ridge
    sc = (c[0], c[1] + 3.2, c[2] + 1.05)                         # the sun high on the face, above the shoulders
    body.box_at(sc, (2.4, 2.4, 0.25), "gold", rotation=rot[:2] + [rot[2] + 45], pivot=list(c))
    body.box_at(sc, (1.2, 1.2, 0.35), "sun", rotation=rot[:2] + [rot[2] + 45], pivot=list(c))
    # its strap across the chest, left hip to right shoulder, ON the tabard (its ends floated off the chamfered sides
    # of the chest, and over the shoulder it stuck out of the cap front and back): from under the belt up the bib,
    # then along the yoke into the mantle
    t = 0.22
    p0, p1 = (2.0, 15.3), (-1.3, 21.35)
    z1 = -3.0 - t / 2 - 0.02
    a = math.degrees(math.atan2(p1[1] - p0[1], p1[0] - p0[0])) - 180
    body.box_at(((p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2, z1), (math.hypot(p1[0] - p0[0], p1[1] - p0[1]) + 0.3, 0.62, t),
                "leather_dark", rotation=[0, 0, a], pivot=[(p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2, z1])
    slab(body, (-1.7, 22.175, -2.82), (-0.8, 1.65, 0.58), (1, 0, 0), (0.62, t, 1.9), "leather_dark")


def ngon(bone, cx, cz, y0, y1, w, color, n=16, phase=0.0, tilt=0.0, **kw):
    """gen_models.ngon_prism - an exact regular n-gon prism, w across the flats, n/2 bands turned 360/n apart - with a
    phase (to line a cap's facets up with a lathe's) and each band's long borders marked "open": the bands' coplanar
    top faces then show no darkened border lines across the disc (dsl_to_game paints a dark border round every top
    face - the stepped cross on the old kettle-hat cap was those borders as much as the boxes)."""
    side = w * math.tan(math.pi / n)
    yc, hh = (y0 + y1) / 2, y1 - y0
    for k in range(n // 2):
        a = phase + 360.0 / n * k
        c = bone.box_at([cx, yc, cz], [w, hh, side], color, rotation=[0, a, tilt], pivot=[cx, yc, cz], **kw)
        c["open"] = "z"


def oct_pole(bone, cx, cz, y0, y1, w, color, **kw):
    """A regular octagonal prism (ngon, n=8): an EXACT octagon - oct_at's two crossed boxes are a plus sign whose
    notched corners read as two blocks pushed through each other (lovish, Oct 16 07:14)."""
    ngon(bone, cx, cz, y0, y1, w, color, n=8, **kw)


def plate(bone, p0, p1, y0, y1, t, color, ext=0.0, **kw):
    """An upright plate whose FRONT face runs from p0 to p1 (x, z) at heights y0..y1, t thick behind it (turned about
    y only, so its front stays a side face for the painter): the facets of a face or a curved panel."""
    dx, dz = p1[0] - p0[0], p1[1] - p0[1]
    L = math.hypot(dx, dz)
    nx, nz = dz / L, -dx / L
    if nz > 0:
        nx, nz = -nx, -nz                                     # the normal that faces the front (-z)
    cx, cz = (p0[0] + p1[0]) / 2 - nx * t / 2, (p0[1] + p1[1]) / 2 - nz * t / 2
    a = math.degrees(math.atan2(-dz, dx))
    if a > 90:
        a -= 180
    elif a < -90:
        a += 180
    yc = (y0 + y1) / 2
    return bone.box_at([cx, yc, cz], [L + ext, y1 - y0, t], color, rotation=[0, a, 0], pivot=[cx, yc, cz], **kw)


def face(h, hy):
    """A levy's face in the opening of the coif (lovish, Oct 16 07:13: "the face just being a black blob isn't right
    ... a shadow covering facial features instead"): a weathered commoner with a short beard. Built as facets, front to
    back: the cheeks turn back along the coif's diagonals, the brow ridge overhangs two deep sockets, the nose (bridge
    and tip) stands proud, cheekbones under the eyes, a moustache and a beard on the jaw that tucks into the mail. The
    upper face sits 1-1.6 px under the brim's 2 px overhang: the baked sky light (tools/unit_light.py) puts it in
    shade - at RTS zoom it reads as a darker face, close up as features in shadow."""
    fz = -2.56                                                       # the front of the face (the coif's flat: -2.45)
    yb, yt = hy + 1.65, hy + 3.45                                    # the face plate, upper lip to under the brim
    # (every face cube under 1.9 px tall: dsl_to_game paints a lit rim and a seam round taller ones - stripes on a face)
    h.box_at((0, (yb + yt) / 2, fz + 0.36), (1.9, yt - yb, 0.72), "skin")                         # the centre
    for s in (1, -1):
        # the cheeks: turned back toward the coif's diagonal faces, their outer edge flush with the mail
        plate(h, (s * 0.96, fz + 0.04), (s * 1.42, fz + 0.48), yb, yt, 0.55, "skin", ext=0.1)
        # the jaw under them, bearded, narrowing to the chin
        plate(h, (s * 0.6, fz - 0.3), (s * 1.3, fz + 0.42), hy + 0.55, yb + 0.1, 0.5, "beard", ext=0.1)
        # the brow ridge: from the root of the nose out to the temple, over the socket
        plate(h, (s * 0.12, fz - 0.28), (s * 1.15, fz + 0.12), hy + 2.82, hy + 3.22, 0.4, "skin", ext=0.06)
        # the cheekbone: the socket's lower rim
        plate(h, (s * 0.4, fz - 0.12), (s * 1.2, fz + 0.2), hy + 2.05, hy + 2.4, 0.35, "skin", ext=0.04)
        # the eye socket in the brow's shade (no painted dot: a dark hollow between brow, nose and cheekbone)
        h.box_at((s * 0.62, hy + 2.62, fz - 0.03), (0.62, 0.38, 0.1), "socket")
        h.box_at((s * 0.6, hy + 2.6, fz - 0.06), (0.36, 0.15, 0.1), "eye")                 # the eye, a glint in it
    # the nose: the bridge sloping out from between the brows to the tip, the tip and wings standing proud
    h.box_at((0, hy + 2.6, fz - 0.24), (0.44, 1.0, 0.36), "skin", rotation=[13, 0, 0], pivot=[0, hy + 2.6, fz - 0.24])
    h.box_at((0, hy + 2.02, fz - 0.24), (0.7, 0.42, 0.5), "skin")
    h.box_at((0, hy + 1.66, fz - 0.14), (1.36, 0.3, 0.34), "beard")                                    # moustache
    h.box_at((0, hy + 0.95, fz - 0.06), (1.3, 1.1, 0.6), "beard")                                      # the beard
    h.box_at((0, hy + 0.62, fz - 0.24), (0.78, 0.6, 0.4), "beard")                                     # its point


def head(m):
    hy = SHOULDER_Y + 0.9
    h = m.bone("head", [0, hy, 0], parent="body")
    # the mail coif: an exact octagon (round from every side), the face in its opening, the mantle on the shoulders
    oct_pole(h, 0, -0.15, hy + 0.5, hy + 4.6, 4.6, "mail")
    face(h, hy)
    oct_pole(h, 0, -0.2, hy - 0.8, hy + 0.65, 5.0, "mail")                                            # the aventail
    # the kettle hat: a dome, a team-colour band with the sun, and the wide brim
    # ROUND (lovish, Oct 16: helmets "a bit boxy"; the brim "reads as a stepped cross" from the RTS camera): exact
    # 16-gon band, brim and lip (ngon), a 12-sided crown turned on a lathe, an octagonal lid
    # (slabs 0.53 thick: at 0.5 the upper ring's were 0.585 px^3 - under dsl_to_game's 0.6 "detail" line, so with the
    # lod parts off the dome had a hole round its lid)
    lathe(h, 0, -0.25, 2.45, 2.45, ((hy + 4.85, 1.0, hy + 5.6, 0.82), (hy + 5.55, 0.82, hy + 6.05, 0.45)), "steel",
          t=0.53)
    # the crown's cap: a low octagonal lid over the lathe's top ring (its slabs end at r 1.17: a lid any narrower left
    # their corners standing round its edge)
    oct_pole(h, 0, -0.25, hy + 5.62, hy + 6.14, 2.36, "steel")
    ngon(h, 0, -0.25, hy + 4.05, hy + 4.9, 5.1, "gold_dark")                                           # hat band
    h.box_at((0, hy + 4.5, -2.92), (0.8, 0.8, 0.2), "sun", rotation=[0, 0, 45], pivot=[0, hy + 4.5, -2.92])
    # the brim, painted in the owner's colour (painted kettle hats were a real heraldic practice): from the RTS camera
    # it is the biggest thing he shows, so it carries the team read; the steel lip under it is its hem
    ngon(h, 0, -0.25, hy + 3.7, hy + 4.15, 8.6, "tabard")
    ngon(h, 0, -0.25, hy + 3.45, hy + 3.75, 9.0, "steel_dark")                                          # its lip
    return h


def arm(m, side, parent):
    pre = "left" if side > 0 else "right"
    sx, sy = side * SHOULDER_X, SHOULDER_Y
    elbow_y, wrist_y = sy - UPPER, sy - UPPER - FORE
    up = m.bone(pre + "_arm", [sx, sy, 0], parent=parent)
    # the quilted sleeve: one tube from under the cap to the elbow joint (the upper of its two tubes was under the cap
    # but for 0.6 px, and its seam ring entirely) - an EXACT hexagon, flats front and back: the crossed boxes' notches
    # ran down it as a groove, two blocks pushed together
    ngon(up, sx, 0, elbow_y + 0.15, sy - 1.6, 1.95, "cloth", n=6, phase=30)
    # a padded cap over the shoulder in the owner's colour, a gold hem at its edge: seen from every side and from above
    # (the art director's round: the team colour was a narrow front strip - 14-18% of the far read, the mark is 20%).
    # EXACT octagons (Oct 16, 07:14): the two crossed boxes were a plus sign - from the RTS camera a blue block with
    # notched corners on each shoulder
    # sloping off the shoulder like a pauldron (tilted 14 degrees about its centre - level, it stood as a drum)
    tilt, top = 14.0, 0.75
    oct_pole(up, sx, 0, sy - 1.9, sy + top, 3.0, "tabard", tilt=-side * tilt)
    drop = (1.9 + top) / 2                                   # the cap's rim from its centre: the hem rides on it
    oct_pole(up, sx - side * drop * math.sin(math.radians(tilt)), 0, sy - 2.2, sy - 1.8, 3.12, "gold", tilt=-side * tilt)
    fo = m.bone(pre + "_fore", [sx, elbow_y, 0], parent=pre + "_arm")
    # the elbow (lovish, Oct 16: "the elbows don't match human anatomy"): a padded joint round the hinge - a cylinder
    # along x, the axis the forearm bends about, so it reads as one rounded joint at any bend - and a leather patch on
    # its point, behind, where the quilting wears through
    octagon_axle(fo, (sx, elbow_y, 0.0), 1.86, 1.98, "cloth")
    fo.box_at((sx, elbow_y - 0.05, 0.86), (1.1, 0.95, 0.42), "leather")
    oct_at(fo, sx, 0, elbow_y - 1.6, elbow_y - 0.15, 1.7, 1.85, 0.45, "cloth")
    # the bracer, an exact hexagon like the sleeve (its square cuff ring stuck out of the octagon at all four corners -
    # gone; flats front and back, it sinks less into the tabard where the forearms cross the chest: 0.75 -> 0.67 px)
    ngon(fo, sx, 0, wrist_y, elbow_y - 1.4, 1.75, "leather", n=6, phase=30)
    return pre + "_arm", pre + "_fore", (sx, wrist_y + 0.2, 0)


GLOVE = dict(finger_w=0.36, finger_t=0.36, palm_t=0.5, palm_len=1.6, cuff_len=0.55, lames=0, knuckles=False,
             accent="glove_dark", heel="glove_dark", cuff="leather_dark")


def beam(bone, x, z0, z1, yb, yt, w, color, ch=0.22):
    """A length of stock along z with its four long edges chamfered: a plus-shaped pair of boxes (the outline of a
    shaped tiller, not a plank)."""
    bone.cube([x - w / 2, yb + ch, z0], [w, yt - yb - 2 * ch, z1 - z0], color)
    bone.cube([x - w / 2 + ch, yb, z0], [w - 2 * ch, yt - yb, z1 - z0], color)


def crossbow(m, x, y, z_butt):
    """THE prop, rebuilt as a machine (lovish, Oct 16: "the crossbow looks like three blocks put together", "the
    crossbow should be mechanical and have animations including rebolting"). A shaped tiller: a deep head that houses
    the prod, a slim fore-stock with the bolt groove, the lock between steel plates, the wrist the hands close on and
    a butt that drops; the steel prod a tapering arc lashed to the head by two cord bridles; a D-shaped stirrup at the
    nose; the string on the revolving nut; a long tickler under the stock; a geared windlass on the lock plate whose
    claw rides the rail. Its own bones, animated by CrossbowLevyUnitModel: prod_l / prod_r (the limbs flex),
    string_l / string_r (pivoted at the tips, turned and stretched to the drawn point), claw, crank, nut, trigger and
    bolt (gone when shot, laid back in the groove at the end of the spanning)."""
    gx, gy, gz = x, y, z_butt - 1.2
    cb = m.bone("crossbow", [gx, gy, gz], parent="xbow_arms")
    L = 10.4
    z0 = z_butt - L                                                                                  # the nose
    top = y + 0.6                                                                                    # the rail
    # --- the tiller, nose to butt
    beam(cb, x, z0, z0 + 1.8, y - 0.95, top, 1.5, "wood")                                           # the head
    beam(cb, x, z0 + 1.8, z0 + 6.0, y - 0.7, top - 0.05, 1.2, "wood")                               # fore-stock
    beam(cb, x, z0 + 6.0, z0 + 7.9, y - 0.85, top, 1.3, "wood")                                     # the lock
    beam(cb, x, z0 + 7.9, z_butt - 0.25, y - 0.72, top - 0.05, 1.12, "wood")                        # the wrist
    # the butt drops away under the right hand: a chamfered slab from the wrist's underside down to the butt plate
    for w, t in ((1.12, 0.9), (0.72, 1.3)):
        slab(cb, (x, y - 1.05, z_butt - 1.0), (0, -0.55, 1.0), (1, 0, 0), (w, t, 2.1), "wood")
    cb.cube([x - 0.58, y - 1.75, z_butt - 0.3], [1.16, 2.3, 0.3], "steel_dark")                     # butt plate
    cb.cube([x - 0.16, top - 0.03, z0 + 1.8], [0.32, 0.06, 4.15], "wood_dark")                      # the groove
    for s in (1, -1):                                                                               # lock plates
        cb.cube([x + s * 0.7 - 0.06, y - 0.6, z0 + 6.15], [0.12, 1.05, 1.6], "steel")
        for zz in (z0 + 6.4, z0 + 7.5):                                                              # their rivets
            cb.cube([x + s * 0.78 - 0.08, y + 0.2, zz - 0.08], [0.16, 0.16, 0.16], "gold")
    cb.cube([x - 0.82, y - 1.02, z0 - 0.04], [1.64, 1.69, 0.2], "gold")                            # the nose band
    for zz in (z0 + 0.35, z0 + 1.45):                                                                # cord bridles
        cb.cube([x - 0.82, y - 1.02, zz - 0.13], [1.64, 1.7, 0.26], "cord")
    # --- the stirrup: a D of iron forward of the nose, in the prod's plane, a little below it
    sy0, sy1, zf = y - 0.45, y - 0.8, z0 - 2.0
    for s in (1, -1):
        slab(cb, (x + s * 0.85, (sy0 + sy1) / 2, (z0 + zf + 0.5) / 2), (s * 0.25, sy1 - sy0, zf + 0.5 - z0),
             (0, 1, 0), (0.3, 0.3, abs(zf + 0.5 - z0) + 0.2), "steel")
        slab(cb, (x + s * 0.8, sy1, zf + 0.2), (-s * 0.45, 0, -0.55), (0, 1, 0), (0.3, 0.3, 0.85), "steel")
    cb.box_at((x, sy1, zf - 0.05), (1.3, 0.32, 0.32), "steel")
    # --- the prod: two tapering steel arcs from the head, swept back toward him (drawn), dark nocks at the tips
    pz, py = z0 + 0.9, y + 0.2
    phi = PROD / PROD_R
    tips = {}
    for s, name in ((1, "prod_l"), (-1, "prod_r")):
        pb = m.bone(name, [x, py, pz], parent="crossbow")
        for k in range(PROD_SEG):
            a0, a1 = phi * k / PROD_SEG, phi * (k + 1) / PROD_SEG
            P0 = (x + s * PROD_R * math.sin(a0), pz + PROD_R * (1 - math.cos(a0)))
            P1 = (x + s * PROD_R * math.sin(a1), pz + PROD_R * (1 - math.cos(a1)))
            f = (k + 0.5) / PROD_SEG
            h, t = 0.95 - 0.42 * f, 0.62 - 0.22 * f                                              # height, thickness
            seg = math.hypot(P1[0] - P0[0], P1[1] - P0[1]) + 0.22         # overlapped: no seam shows
            c = slab(pb, ((P0[0] + P1[0]) / 2, py, (P0[1] + P1[1]) / 2), (P1[0] - P0[0], 0, P1[1] - P0[1]),
                     (0, 1, 0), (h, t, seg), "steel")
            c["open"] = "z"                         # a joint in the chain, not an edge (dsl_to_game: no rim there)
        T = (x + s * PROD_R * math.sin(phi), pz + PROD_R * (1 - math.cos(phi)))
        am = phi
        slab(pb, (T[0] - s * 0.15 * math.cos(am), py, T[1] - 0.15 * math.sin(am)), (s * math.cos(am), 0, math.sin(am)),
             (0, 1, 0), (0.62, 0.5, 0.75), "steel_dark")                                          # the nock
        tips[s] = (T[0] - s * 0.3 * math.cos(am), T[1] - 0.3 * math.sin(am))
    # --- the string: one bone per half, pivoted at its nock and laid along -s x (toward the stock) in its own frame;
    # the bone's turn lays it onto the drawn point - the model animates that turn and the half's stretch (xScale)
    sy = top + 0.08
    nut_z = z0 + 6.55
    for s, name, parent in ((1, "string_l", "prod_l"), (-1, "string_r", "prod_r")):
        T = tips[s]
        dx, dz = x - T[0], nut_z - T[1]
        Ls = math.hypot(dx, dz)
        ang = math.degrees(math.atan2(dz, -dx)) if s > 0 else math.degrees(math.atan2(-dz, dx))
        sb = m.bone(name, [T[0], sy, T[1]], parent=parent, rotation=[0, ang, 0])
        sb.cube([T[0] - Ls if s > 0 else T[0], sy - 0.1, T[1] - 0.1], [Ls, 0.2, 0.2], "string")
        XB[name] = dict(tip=list(T), rest_yaw=ang, rest_len=Ls)
    # --- the nut: a revolving steel wheel in the lock, its two horns holding the string (turns forward on release)
    nb = m.bone("nut", [x, top + 0.15, nut_z + 0.45], parent="crossbow")
    nb.box_at((x, top + 0.15, nut_z + 0.45), (0.7, 0.75, 0.75), "steel")
    nb.box_at((x, top + 0.15, nut_z + 0.45), (0.74, 0.5, 0.5), "steel_dark", rotation=[45, 0, 0],
              pivot=[x, top + 0.15, nut_z + 0.45])
    for s in (1, -1):                                                                                 # its horns
        nb.box_at((x + s * 0.24, top + 0.55, nut_z + 0.12), (0.2, 0.42, 0.24), "steel")
    # --- the tickler: a long trigger lever under the stock, pivoted at the lock, its tail under the right hand
    tp = (x, y - 0.95, z0 + 6.9)
    tb = m.bone("trigger", list(tp), parent="crossbow")
    slab(tb, (x, tp[1] - 0.18, (tp[2] + z_butt - 0.6) / 2), (0, -0.25, z_butt - 0.6 - tp[2]), (1, 0, 0),
         (0.3, 0.3, z_butt - 0.6 - tp[2]), "steel_dark")
    tb.box_at((x, tp[1], tp[2]), (0.5, 0.36, 0.36), "steel")                                           # its pin
    # --- the windlass on the right lock plate (the side the RTS camera sees; ahead of the right hand, so the knob's
    # circle clears it): a toothed wheel and its crank (the bone spins about x)
    cp = (x - 0.95, y - 0.05, z0 + 6.2)
    kb = m.bone("crank", list(cp), parent="crossbow")
    kb.box_at(cp, (0.32, 1.25, 0.52), "steel_dark")
    kb.box_at(cp, (0.32, 0.52, 1.25), "steel_dark")
    for a in (45, -45):
        kb.box_at(cp, (0.32, 1.25, 0.52), "steel_dark", rotation=[a, 0, 0], pivot=list(cp))
    for k in range(8):                                                                                # the teeth
        a = math.radians(22.5 + 45 * k)
        tc = (cp[0], cp[1] + 0.72 * math.cos(a), cp[2] + 0.72 * math.sin(a))
        kb.box_at(tc, (0.26, 0.24, 0.24), "gold_dark", rotation=[-(22.5 + 45 * k), 0, 0],
                  pivot=list(tc))
    kb.box_at((cp[0] - 0.24, cp[1], cp[2]), (0.2, 0.42, 0.42), "gold")                               # the hub
    arm_c = (cp[0] - 0.42, cp[1] - 0.75, cp[2] + 0.25)
    slab(kb, arm_c, (0, -1.5, 0.5), (1, 0, 0), (0.24, 0.3, 1.75), "steel")                            # crank arm
    kb.box_at((cp[0] - 0.66, cp[1] - 1.5, cp[2] + 0.5), (0.55, 0.42, 0.42), "wood_dark")              # its knob
    # --- the claw: a steel bridge over the rail with two hooks, parked behind the nut; it fetches the string
    cl = (x, top + 0.42, nut_z + 1.2)
    lb = m.bone("claw", list(cl), parent="crossbow")
    lb.box_at(cl, (1.0, 0.28, 0.6), "steel")
    for s in (1, -1):
        lb.box_at((x + s * 0.42, cl[1] - 0.25, cl[2] - 0.22), (0.18, 0.5, 0.24), "steel_dark")
    # --- the bolt in the groove: a square shaft, a forged head past the nose, two vanes ahead of the nut
    by = top + 0.2
    bz0, bz1 = nut_z - 0.15, z0 - 0.5
    bb = m.bone("bolt", [x, by, (bz0 + bz1) / 2], parent="crossbow")
    bb.cube([x - 0.16, by - 0.16, bz1], [0.32, 0.32, bz0 - bz1], "wood")
    bb.box_at((x, by, bz1 - 0.25), (0.5, 0.5, 0.55), "steel")                                         # the head
    bb.box_at((x, by, bz1 - 0.75), (0.34, 0.34, 0.5), "steel", rotation=[0, 0, 45],
              pivot=[x, by, bz1 - 0.75])
    for s in (1, -1):                                                                                 # the vanes
        bb.cube([x + (0.14 if s > 0 else -0.56), by - 0.03, bz0 - 1.6], [0.42, 0.06, 1.3], "feather")
    XB.update(prod_centre=[x, pz], nut_z=nut_z, claw_z=cl[2], bolt_y=by, phi_deg=math.degrees(phi))
    # the hands close on the wrist of the stock: the right one behind the left, both index fingers toward the nose
    hands = {}
    for side, (hz, hy, back) in ((-1, (z_butt - 1.25, y - 0.1, (-0.7, -0.7, 0.6))),
                                  (1, (z_butt - 2.2, y - 0.1, (0.7, -0.7, 0.3)))):
        hands[side] = grip_hand(cb, (x, hy, hz), (0, 0, -1), 0.62, side, "glove", back=back, **GLOVE)
    return cb, hands


def end_cap(bone, cx, cz, y0, y1, w, color, n=6, **kw):
    """The rounded END of a foot-shaped plan (a toe, a heel): the bands of an n-gon (flats at the sides, w across them,
    centred on (cx, cz)) except its 0-degree band - a box w wide that reaches s/2 = w tan(pi/n) / 2 past (cx, cz)
    covers that band's place exactly (inside the n-gon, |dz| <= s/2 is |x| <= w/2), so box + cap is an exact stadium
    outline: no square corner stands past the round (the old sole's ends, lovish Oct 16 12:09)."""
    side = w * math.tan(math.pi / n)
    yc, hh = (y0 + y1) / 2, y1 - y0
    for k in range(1, n // 2):
        a = 360.0 / n * k
        c = bone.box_at([cx, yc, cz], [w, hh, side], color, rotation=[0, a, 0], pivot=[cx, yc, cz], **kw)
        c["open"] = "z"


# the boot and the hose (lovish, Oct 16 12:09: "The feet of the crossbow men look like wood that got cut with a knife.
# It needs better dimension, more realistic design, not just a one texture leather boot" - each boot was a box shaft
# on a slanted box foot in one dark brown, the legs square tubes with a grey box at the knee). Now a 15th-century
# levy's turned-leather ankle boot over woollen hose, a quilted cuisse over the thigh and knee, every part an exact
# polygon and the boot in four leathers (sole, upper, worn toe, the cuff's flesh side) with an iron buckle:
TOE_OUT = 8.0                 # degrees each foot turns out (gen_models.foot)
SOLE, PROUD = 0.3, 0.09       # the sole's thickness and how far it stands proud of the upper all round
HEEL = 0.5                    # the stacked heel's height (the sole under the forefoot is SOLE)
FOOT_W = 1.76                 # the upper across the ball of the foot
TOE_Z = -2.28                 # the centre of the toe's hexagon: its point (the tip) is FOOT_W / sqrt(3) ahead of it
HEEL_Z = 0.15                 # the centre of the ankle's hexagon, a little behind the leg's line (the heel)
# the ankle rides on the foot bone and the cuff on the shin, and the walk rocks the foot 17 degrees against the shin
# (CrossbowLevyUnitModel: the toe lifts on the swing): the ankle's top stays low over the joint, so it rocks inside
# the cuff instead of standing out of it
ANKLE_TOP = 2.05              # the ankle's top, inside the turned-down cuff on the shin
CUFF = (1.8, 2.8)             # the cuff's bottom and top on the shin
HOSE_W, CUFF_W = 1.6, 1.94    # the hose (shin ridge in front, calf behind) and the cuff, hexagons in step
BALL_Z = -1.45                # the ball of the foot: where the vamp's gentle facet turns up the instep (the strap)
FORE_TOP = 0.75               # the forefoot's top, level with the toe facets' (no corner of it stands above them)
VAMP = (22.0, 38.0)           # the vamp's two facets' rise, degrees
TOE_H, TOE_PITCH = 0.58, 18.0  # the toe box's facets (before the pitch) and their pitch: the crown meets the vamp
TOGGLES = (1.3, 1.85)         # the heights of the two toggles up the ankle's outer side
CUISSE_W, CUISSE_HEM = 1.82, 5.95   # the quilted cuisse over the thigh, and its hem below the knee


def toe_cap(bone, cx, cz, y0, h, w, color, pitch):
    """The toe box's nose: the two front facets of a hexagonal toe (flats at the sides, w across them, centred on
    (cx, cz)) as half-bands from the centre out to each facet, each PITCHED nose-down `pitch` degrees about its
    facet's bottom edge - the facet leans back and the top falls to the tip, so the toe is rounded in profile as well
    as in plan (the old toe was a box under a slab: a wedge). Their bottoms start y0 (buried in the sole a little: the
    rising underside then never opens a slot over it)."""
    s = w * math.tan(math.pi / 6)
    th = math.radians(pitch)
    for sg in (1, -1):
        n = (sg * math.sin(math.pi / 6), -math.cos(math.pi / 6))          # the facet's outward normal (x, z)
        ez = (n[0] * math.cos(th), -math.sin(th), n[1] * math.cos(th))
        ex = (n[1], 0.0, -n[0])
        ey = (ez[1] * ex[2] - ez[2] * ex[1], ez[2] * ex[0] - ez[0] * ex[2], ez[0] * ex[1] - ez[1] * ex[0])
        if ey[1] < 0:
            ex, ey = tuple(-v for v in ex), tuple(-v for v in ey)
        L = w / 2 + 0.06
        P = (cx + n[0] * w / 2, y0, cz + n[1] * w / 2)                   # the facet's bottom edge, its middle
        c = tuple(P[k] - ez[k] * L / 2 + ey[k] * h / 2 for k in range(3))
        slab(bone, c, ez, ex, (s + 0.02, h, L), color)


def vamp(bone, hx, A, B, t, w, color):
    """A slab whose TOP runs from A to B ((z, y) in the foot's side view), t thick under it: a facet of the instep."""
    L = math.hypot(B[0] - A[0], B[1] - A[1])
    d = ((B[0] - A[0]) / L, (B[1] - A[1]) / L)
    c = ((A[0] + B[0]) / 2 + d[1] * t / 2, (A[1] + B[1]) / 2 - d[0] * t / 2)   # half a thickness under the line
    return slab(bone, (hx, c[1], c[0]), (0, d[1], d[0]), (1, 0, 0), (w, t, L + 0.06), color)


def boot(m, pre, hx, side):
    """The foot (its own bone, level and turned out: gen_models.foot). The plan is a stadium - a box between a
    hexagonal toe (a blunt point, the 15th century's) and a hexagonal heel - built twice: the sole, dark and PROUD of
    the upper all round, its heel stacked HEEL high; and on it the upper - the toe box in a lighter, worn leather, its
    nose pitched back (toe_cap); the forefoot; the ankle, a hexagon in step with the cuff above it (flats at the sides,
    flush with the forefoot), rising into it; the vamp in two facets as wide as the foot, so its sides run on in the
    forefoot's (a narrower slab on a box stood out as a plank); a strap over the instep, buckled in iron on the
    outside, and two toggles up the outside of the ankle."""
    shin = pre + "_shin"
    ft = m.bone(pre + "_foot", [hx, 1.0, 0], parent=shin, rotation=m.level_rotation(shin, yaw=-side * TOE_OUT))
    ws = FOOT_W + 2 * PROUD
    t6 = math.tan(math.pi / 6)
    # the sole: first, so tools/limb_check.py measures the level and the turn on its box
    ft.cube([hx - ws / 2, 0.0, TOE_Z - ws * t6 / 2], [ws, SOLE, HEEL_Z - TOE_Z + ws * t6], "boot_sole")
    end_cap(ft, hx, TOE_Z, 0.0, SOLE, ws, "boot_sole")
    end_cap(ft, hx, HEEL_Z, 0.0, HEEL, ws, "boot_sole")                                            # the heel
    # the upper. Every joint between its pieces is marked "open" (dsl_to_game paints no edge there), so the forefoot,
    # the vamp and the ankle read as one leather surface, not planks laid side by side
    s = FOOT_W * t6
    # (the forefoot ends where the ankle's side flats begin: overlapping them it would share their plane - coplanar
    # faces flicker in the game)
    ft.cube([hx - FOOT_W / 2, SOLE, TOE_Z - s / 2], [FOOT_W, FORE_TOP - SOLE, HEEL_Z - TOE_Z], "boot")["open"] = "z"
    toe_cap(ft, hx, TOE_Z, SOLE - 0.14, TOE_H, FOOT_W, "boot_worn", TOE_PITCH)                     # the toe box
    ngon(ft, hx, HEEL_Z, SOLE, ANKLE_TOP, FOOT_W, "boot", n=6)                                      # the ankle
    # the vamp in two facets, a hair narrower than the foot (inside its sides' plane, never on it): from the
    # forefoot's top at the toe box gently up to the ball, then steeply up the instep to under the cuff - over the
    # ankle's front faces, its sides on the ankle's side flats; the turn between them is the crease the strap lies in
    k1, k2 = math.tan(math.radians(VAMP[0])), math.tan(math.radians(VAMP[1]))
    vw = FOOT_W - 0.02
    A = (TOE_Z - 0.45, FORE_TOP)
    ball = (BALL_Z, A[1] + (BALL_Z - A[0]) * k1)
    B = (HEEL_Z, ball[1] + (HEEL_Z - BALL_Z) * k2)
    vamp(ft, hx, A, (ball[0] + 0.3, ball[1] + 0.3 * k1), 0.55, vw, "boot")["open"] = "z"
    vamp(ft, hx, (ball[0] - 0.3, ball[1] - 0.3 * k2), B, 0.6, vw, "boot")["open"] = "z"
    # the strap over the instep in the crease (square to the mean of the two facets), a hair proud of them and of the
    # forefoot's sides, down to the welt; its iron buckle on the outside
    a = math.degrees(math.atan((k1 + k2) / 2))
    top = ball[1] + 0.05
    ft.box_at((hx, (top + SOLE + 0.02) / 2, BALL_Z), (FOOT_W + 0.05, top - SOLE - 0.02, 0.26), "leather_dark",
              rotation=[-a, 0, 0], pivot=[hx, ball[1], BALL_Z])
    ft.box_at((hx + side * (FOOT_W / 2 + 0.06), 0.8, BALL_Z), (0.1, 0.42, 0.4), "steel_dark",
              rotation=[-a, 0, 0], pivot=[hx, ball[1], BALL_Z])
    # the ankle fastens up the outside with two toggles (the side-opening ankle boot of the period)
    for ty in TOGGLES:
        ft.box_at((hx + side * (FOOT_W / 2 + 0.05), ty, HEEL_Z - 0.18), (0.14, 0.17, 0.42), "wood_dark")
    return ft.name, shin


def legs(m):
    feet = []
    # a slight A-stance (thighs splayed 3 degrees): parallel legs read as stilts
    for side, thigh, shin in ((1, [5, 0, 3], [-7, 0, 0]), (-1, [-4, 0, -3], [-3, 0, 0])):
        pre = "left" if side > 0 else "right"
        hx = side * 1.6
        th = m.bone(pre + "_thigh", [hx, HIP, 0], rotation=thigh)
        # the thigh in a quilted cuisse - the padded thigh defence a gambesoned foot soldier wore - from under the
        # skirt down over the knee: the padded knee (the grey box it replaces), its hem the knee's wrap; an exact
        # hexagon, flats at the sides like the hose
        ngon(th, hx, -0.02, CUISSE_HEM, HIP + 0.4, CUISSE_W, "cloth_dark", n=6)
        sh = m.bone(pre + "_shin", [hx, 6.8, 0], parent=pre + "_thigh", rotation=shin)
        # the hose: an exact hexagon, flats at the sides - the ridge of the shin in front, the calf behind
        ngon(sh, hx, 0, CUFF[0] + 0.2, 6.95, HOSE_W, "hose", n=6)
        # the boot's turned-down top, its pale flesh side out, the ankle rising inside it
        ngon(sh, hx, 0, CUFF[0], CUFF[1], CUFF_W, "boot_turn", n=6)
        feet.append(boot(m, pre, hx, side))
    return feet


def levy():
    m = Model("Crossbow Levy (v2)", "sunforged", "skirmisher (T1, through the Paladin pipeline)", PALETTE, EMISSIVE)
    body = torso(m)
    tabard(m)
    pavise(body)
    head(m)
    feet = legs(m)
    m.bone("xbow_arms", [0, SHOULDER_Y, 0], parent="body")
    # the crossbow carried across the waist, pointing ahead and a little to his left
    # the crossbow carried at the ready in front of the chest (butt a hand's width off the gambeson), levelled: from the
    # RTS camera the prod is a wide T in front of him - the role at a glance
    # Oct 15 (lovish: "the arms look bent"): the stock turned 14 degrees the wrong way (Ry > 0 swings the nose to HIS
    # RIGHT), so the left hand reached across the body on a locked-straight arm while the right elbow rose over the
    # shoulder to fold back to a butt 5.7 px away. Now the right-handed carry: the butt at the right breast, the nose
    # angled ahead and to his left, the left hand under the stock in front of the left shoulder, both elbows down
    cb, hands = crossbow(m, -0.3, 18.6, -3.1)
    # intended contact: the forearms run along the stock they hold; the thighs swing inside the gambeson skirt
    m.contact_ok += [("crossbow", "left_fore"), ("crossbow", "right_fore"), ("body", "left_thigh"),
                     ("body", "right_thigh")]
    m.bones["crossbow"].rotation = [0, -20, 0]
    ra, rf, rw = arm(m, -1, "xbow_arms")
    la, lf, lw = arm(m, 1, "xbow_arms")
    # each wrist UNDER and behind its glove (the forearms come up from hanging elbows), the forearm pointing through
    # the glove at the stock: straight wrists (Oct 15 review: aimed above the grip, they bent 74-90 degrees and the
    # stock ran through the bracers)
    # (Oct 16) the wrists onto the hands' cuffs (gen_models.grip_hand), the forearms pointing along the hands; the
    # crossbow 0.5 px nearer the chest and 0.4 higher, the support hand 0.35 nearer the right one, and elbow_at the
    # elbows of a person holding it at the ready - upper arms ~30 degrees forward, elbows flexed ~80 (they reached
    # 30-35 forward with the left elbow open at 56: "the elbows don't match human anatomy", lovish 07:10)
    e1 = m.solve_arm(ra, rf, rw, m.fk("crossbow", hands[-1]["wrist"]), elbow_out=1, elbow_down=1,
                     point_at=m.fk("crossbow", hands[-1]["centre"]), elbow_at=(-3.5, 18.0, -2.2), avoid=("body", "tabard_front"), avoid_margin=0.9)
    e2 = m.solve_arm(la, lf, lw, m.fk("crossbow", hands[1]["wrist"]), elbow_out=1, elbow_down=1,
                     point_at=m.fk("crossbow", hands[1]["centre"]), elbow_at=(3.4, 18.0, -2.6), avoid=("body", "tabard_front"), avoid_margin=0.9)
    for ft, shin in feet:
        m.ground_leg(ft, also=(shin,))
    m.pose = {"head": [-3, 0, 0]}
    return m, max(e1, e2)


MODELS = {"sunforged_crossbow_levy_v2": levy}


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
