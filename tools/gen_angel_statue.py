#!/usr/bin/env python3
"""The White Angel capitol's statue, v2: THE GUARDIAN - a sculpted marble angel drawn by the game like a unit model.

lovish, Oct 20: "the angel looks bad" (the 13 x 13 block angel); he chose a real statue drawn as a model. v1 (Oct 20)
was that model's first pass; its faults, from the self-review and design/ART-FAULTS.md, are what v2 fixes:
  - CRUDE FACE -> a readable, serene face: a modelled brow, closed eyes under their lids, a nose with a bridge,
    cheekbones, lips, a chin; hair in locks flowing back under a gold circlet; the head bowed (the guardian ref).
  - COLUMNAR ROBE -> an ARMOURED angel (his angel reference #1: armoured, greatsword planted, head bowed, huge wings,
    a cloth skirt over plate): a cuirass with a ridge and a gorget, lamed pauldrons, vambraces, a belt with a buckle
    and a fauld; over it a CLOTH SKIRT of hanging panels that swing, fold and break the outline, a tabard down the
    front with a gold sun; greaves and sabatons planted apart on the plinth.
  - PLANK FEATHERS WITH GAPS -> wings built row over row as a real wing is: lesser, median and greater coverts over
    the roots of the tertials, secondaries and primaries, every feather overlapping the next (spacing under its
    width, a shingle step in depth), vanes with a rounded tip and a shaft ridge - no daylight through them.
  - ONE WING EDGE-ON / BROKEN AT THE RTS ANGLE -> the statue FACES THE RTS CAMERA (south-west: the "body" bone's
    rest yaw), so both wings present their faces equally and the figure reads frontally in every match; the wings
    are reclined and cupped forward so they have depth from above.
  - L1 vs L6 THE SAME -> two bones a wing: the root turns back and droops and the HAND bone folds the primaries
    along the forearm (BuildingParts.statueWingFold, StatueRenderClientEvents), so at L1 the wings are closed behind
    the shoulders and at L6 fully spread; the halo gains its outer ring and rays at L6 ("halo_outer").

Space is the DSL's (design/models/README.md): px, y up from the soles, the figure facing -Z before its yaw, +x its
left. Built at a person's proportions (stature 50 px) and drawn SCALED (BuildingParts.STATUES: SCALE_BLOCKS from the
soles to the wing tips). Material: warm white marble in three values (materials.py "stone"); gold only on the halo,
the circlet, the sword's guard and pommel, the belt buckle and the sun.

Bones the game moves: "wing_left" / "wing_right" (yaw back + droop with the fold), "wing_left_hand" /
"wing_right_hand" (the primaries fold along the forearm), "halo_outer" (shown from L6). fold_pose(level) gives the
rest-relative DSL rotations the preview applies and the renderer mirrors.

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
from gen_models import grip_hand, apply, transpose, _unit, _cross, pauldron                   # noqa: E402
import gen_models                                                                             # noqa: E402

PALETTE = {
    # warm white marble in three values (the lit stone, the shade of a fold, the deep cut); the armour a hair cooler
    # and the feathers a hair lighter so plate, cloth and wing read as three masses; gold on the halo, the circlet,
    # the guard, the pommel, the buckle and the sun only
    "marble": "#e6dfd2", "marble_shade": "#cdc4b4", "marble_deep": "#a99f8e",
    "plate": "#d9d3c7", "plate_shade": "#c0b8aa",
    "feather_marble": "#ece6da", "feather_marble_shade": "#d5ccbd",
    "gold": "#d4a63c",
}
EMISSIVE = []

STATURE = 50.0
SHOULDER_Y, SHOULDER_X = 38.0, 5.8
UPPER, FORE = 9.2, 7.8
WING_ROOT = (2.8, 35.8, 3.2)      # the left wing's root on the shoulder blade (mirrored for the right)
WING_LEAN = 16.0                  # the wings' tops reclined (a positive x tips an upright part's top back)
WING_CUP = 0.0035                 # px forward per px^2 out: the tips curve forward round the figure
SCALE_BLOCKS = 20.0               # soles to wing tips in the game (BuildingParts.STATUES' height)
FACING = 0.0                      # the body's rest yaw: 0 = the statue faces the structure's front (+z, the great stair and the gate); the placement's rotation turns it with the building (lovish, Oct 21 14:20: "the angel should be pointing where the capital is pointing")
HEAD_BOW = -14.0                  # the head bowed (a negative x tips an upright part's top forward)

# the fold (BuildingParts.statueWingFold gives f in 0 .. WING_FOLD_MAX degrees, the yaw back); the droop and the
# hand's fold follow it - the same three numbers StatueRenderClientEvents applies
WING_FOLD_MAX = 70.0
WING_DROOP = 0.45                 # degrees of droop (the wing's z) per degree of fold
WING_HAND = -1.1                  # degrees the hand folds along the forearm per degree of fold (negative: the primaries swing down)


def wing_open_at(level):
    """BuildingParts.wingOpenAt: closed on the L1 tower, fully spread on the L6 crown."""
    lv = max(1, min(6, level))
    return 0.2 * (lv - 1)


def fold_degrees(level):
    return (1.0 - wing_open_at(level)) * WING_FOLD_MAX


def fold_pose(level):
    """The rest-relative DSL rotations (degrees) of the moving bones at a capitol level - what the preview applies
    through dsl_to_game.mc_rot and StatueRenderClientEvents mirrors on the ModelParts."""
    f = fold_degrees(level)
    return {"wing_left": [0.0, -f, WING_DROOP * f], "wing_right": [0.0, f, -WING_DROOP * f],
            "wing_left_hand": [0.0, 0.0, WING_HAND * f], "wing_right_hand": [0.0, 0.0, -WING_HAND * f]}


def slab(bone, centre, along, across, size, color, **kw):
    """gen_models.slab, nudged off gimbal lock: a slab whose width runs within a degree or two of z can land just
    inside orient()'s regular branch where the 3-decimal Euler angles miss by more than its tolerance - turn its width
    a few degrees about its length and it is exact again (invisible on a fold or a feather)."""
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
    """A turned surface of elliptical sections (y, rx, rz, cz) - a cuirass, a sleeve's bell: n leaned slabs per band
    (the gen_models.lathe construction with its own ellipse at every section), so the outline swells and narrows with
    no stairs. Seams overlap a little."""
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
            out = _unit((s * (rz0 + rz1), 0.0, -c * (rx0 + rx1)))
            centre = tuple((a[i] + b[i]) / 2 - out[i] * t / 2 for i in range(3))
            across = _unit((c * (rx0 + rx1), 0.0, s * (rz0 + rz1)))
            slab(bone, centre, along, across, (w, t, L), color, **kw)


def seg(bone, a, b, w, t, color, across=None, **kw):
    """A slab from point a to point b, w wide, t thick."""
    along = tuple(b[i] - a[i] for i in range(3))
    if across is None:
        across = (1.0, 0.0, 0.0) if abs(along[0]) < 0.9 * math.dist(a, b) else (0.0, 0.0, 1.0)
    c = tuple((a[i] + b[i]) / 2 for i in range(3))
    return slab(bone, c, along, across, (w, t, math.dist(a, b) + 0.1), color, **kw)


# ------------------------------------------------------------------ legs, skirt, armour -----------------------------
HIP_Y, WAIST_Y, CHEST_Y = 26.0, 29.0, 36.4
HEM_Y = 8.5


def legs(m):
    """Greaves and sabatons planted apart on the plinth, toes a little out; the thighs rise under the skirt."""
    body = m.bone("body", [0, 24.0, 0], rotation=[0, FACING, 0])
    for side in (1, -1):
        x = side * 2.7
        pre = "left" if side > 0 else "right"
        lg = m.bone(pre + "_leg", [x, HIP_Y, 0], parent="body")
        octagon_pole(lg, x, 0.2, 13.5, HIP_Y + 0.5, 3.6, "marble")                         # the thigh
        octagon_axle(lg, (x, 13.8, -0.1), 3.8, 3.6, "plate")                                # the knee cop
        lg.box_at((x, 14.6, -2.1), (2.2, 1.6, 0.8), "plate_shade", rotation=[22, 0, 0], pivot=[x, 14.6, -2.1])
        octagon_pole(lg, x, 0.1, 2.2, 13.9, 3.3, "plate")                                   # the greave
        seg(lg, (x, 12.5, -1.9), (x, 3.0, -2.1), 1.4, 0.5, "plate_shade")                   # its shin ridge
        # the sabaton: a heel block, the instep sloping to a pointed toe turned a little out
        ya = math.radians(side * 9)
        lg.box_at((x, 1.2, 0.6), (3.2, 2.4, 3.4), "plate", rotation=[0, side * 9, 0], pivot=[x, 1.2, 0.6])
        toe = (x - math.sin(ya) * 2.6, 0.9, 0.6 - math.cos(ya) * 2.6)
        lg.box_at(toe, (3.0, 1.8, 2.6), "plate", rotation=[0, side * 9, 0], pivot=list(toe))
        tip = (x - math.sin(ya) * 4.4, 0.6, 0.6 - math.cos(ya) * 4.4)
        lg.box_at(tip, (2.0, 1.2, 1.6), "plate_shade", rotation=[0, side * 9, 0], pivot=list(tip))
        lg.box_at((x, 0.25, 0.4), (3.5, 0.5, 4.4), "marble_deep", rotation=[0, side * 9, 0], pivot=[x, 0.25, 0.4])
    return body


def skirt(body):
    """The cloth skirt over the plate: hanging panels round the hips, each flaring from the belt to a hem of its own
    length, swung a few degrees about its own line, every other one a step deeper - folds that break the outline the
    way a column never does; a ridge of cloth down each panel; a tabard with a gold sun down the front."""
    n = 12
    # each panel: (hem offset, flare, swing degrees, depth step) - a sculptor's drape, not a lathe's: the hems run
    # ragged, the panels kick out over the planted feet and fall straighter at the back, a few swing sideways as if
    # caught in the wind off the crag
    DRAPE = ((2.4, 1.3, 6, 0.0), (-1.8, 1.0, -5, 0.45), (0.6, 1.25, 4, 0.0), (-2.6, 0.85, -7, 0.5),
             (1.6, 1.15, 3, 0.0), (-0.8, 1.0, -4, 0.4), (3.0, 1.35, 7, 0.0), (-2.2, 0.9, -6, 0.5),
             (0.9, 1.2, 5, 0.0), (-1.5, 1.05, -3, 0.45), (2.0, 1.3, 4, 0.0), (-3.0, 0.8, -8, 0.5))
    for k in range(n):
        th = 2 * math.pi * (k + 0.5) / n
        s, c = math.sin(th), math.cos(th)
        front = c > 0.2
        hem_off, flare, swing_deg, deep = DRAPE[k]
        rx0, rz0 = 5.4 - deep, 4.1 - deep                  # at the belt
        hem = HEM_Y + hem_off + (1.2 if front else -0.6)
        rx1, rz1 = (7.6 + 1.6 * (1 if front else 0.3)) * flare - deep * 0.5, (6.0 + 0.8 * (1 if front else 0.3)) * flare - deep * 0.5
        swing = math.radians(swing_deg)
        a = (rx0 * s, WAIST_Y - 0.4, -rz0 * c)
        b = (rx1 * math.sin(th + swing * 0.6), hem, -rz1 * math.cos(th + swing * 0.6))
        rm = (math.hypot(rx0 * s, rz0 * c) + math.hypot(rx1 * s, rz1 * c)) / 2
        w = 2 * rm * math.tan(math.pi / n) + 1.0
        out = _unit((s * (rz0 + rz1), 0.0, -c * (rx0 + rx1)))
        across = _unit((c, 0.0, s))
        centre = tuple((a[i] + b[i]) / 2 for i in range(3))
        along = tuple(b[i] - a[i] for i in range(3))
        slab(body, centre, along, across, (w, 0.75, math.dist(a, b) + 0.3), "marble" if k % 2 else "marble_shade")
        # the fold ridge down the panel's middle, proud of it, drifting sideways toward the hem
        a2 = tuple(a[i] + across[i] * 0.3 * (1 if k % 2 else -1) for i in range(3))
        cr = tuple((a2[i] + b[i]) / 2 + out[i] * 0.5 for i in range(3))
        slab(body, cr, tuple(b[i] - a2[i] for i in range(3)), across, (w * 0.34, 0.6, math.dist(a2, b) * 0.92), "marble")
        # a hem band catching the light at the panel's foot, and a deeper cut under it
        hb = tuple(b[i] + out[i] * 0.2 for i in range(3))
        slab(body, (hb[0], hb[1] + 0.5, hb[2]), along, across, (w, 0.9, 1.3), "marble_shade")
        slab(body, (hb[0] - out[0] * 0.35, hb[1] - 0.1, hb[2] - out[2] * 0.35), along, across, (w * 0.8, 0.6, 0.6),
             "marble_deep")
    # two diagonal drape folds crossing the panels, the cloth gathered toward the sword hand
    for (y0, y1, sgn) in ((25.5, 16.0, 1), (22.0, 11.5, -1)):
        pts = []
        for j in range(5):
            th = math.radians(-70 * sgn + 35 * sgn * j)
            t = j / 4
            y = y0 + (y1 - y0) * t
            r_x = 5.6 + 2.0 * t
            r_z = 4.3 + 1.6 * t
            pts.append((r_x * math.sin(th), y, -r_z * math.cos(th) - 0.3))
        for p0, p1 in zip(pts, pts[1:]):
            seg(body, p0, p1, 1.1, 0.7, "marble", across=(0.0, 1.0, 0.0))
    # the tabard: a long panel down the front, a gold sun on the chest
    a, b = (0.0, CHEST_Y - 1.5, -4.6), (0.0, HEM_Y - 2.2, -6.9)
    seg(body, a, b, 5.6, 0.7, "marble", across=(1.0, 0.0, 0.0))
    seg(body, (0.0, HEM_Y - 2.0, -6.9), (0.0, HEM_Y - 3.0, -7.2), 5.4, 0.9, "marble_shade", across=(1.0, 0.0, 0.0))
    for s_ in (1, -1):
        seg(body, (s_ * 1.7, CHEST_Y - 2.0, -4.8), (s_ * 2.0, HEM_Y - 1.5, -7.0), 0.8, 0.9, "marble_shade",
            across=(1.0, 0.0, 0.0))
    ring(body, (0.0, 32.6, -5.1), 1.5, 8, 0.45, 0.4, "gold")
    for k in range(8):
        a_ = k * math.pi / 4
        cx, cy = 1.95 * math.cos(a_), 32.6 + 1.95 * math.sin(a_)
        body.box_at((cx, cy, -5.15), (0.9, 0.3, 0.35), "gold", rotation=[0, 0, math.degrees(a_)], pivot=[cx, cy, -5.15])


def armour(body):
    """The cuirass: a turned breastplate swelling to the chest with a ridge down it, a gorget at the neck, lamed
    pauldrons over the shoulders, a belt with a gold buckle and a fauld of two lames under it."""
    loft(body, ((WAIST_Y - 0.6, 5.3, 3.8, 0.0), (32.5, 5.9, 4.3, 0.0), (CHEST_Y, 6.5, 4.5, -0.1),
                (SHOULDER_Y + 0.9, 6.2, 4.0, 0.1)), "plate", n=16, t=0.8)
    seg(body, (0.0, SHOULDER_Y + 0.4, -4.1), (0.0, WAIST_Y, -3.9), 1.3, 0.7, "plate_shade", across=(1.0, 0.0, 0.0))
    for s_ in (1, -1):                                       # the pectoral curves, carved into the plate
        seg(body, (s_ * 1.2, CHEST_Y - 0.4, -4.55), (s_ * 4.6, CHEST_Y - 2.4, -3.9), 0.7, 0.5, "plate_shade")
    ngon_prism(body, 0, 0.1, SHOULDER_Y + 0.6, SHOULDER_Y + 2.4, 5.4, "plate", n=10)      # the gorget
    ngon_prism(body, 0, 0.1, SHOULDER_Y + 2.3, SHOULDER_Y + 2.8, 5.9, "plate_shade", n=10)
    for side in (1, -1):
        pauldron(body, side, (side * SHOULDER_X, SHOULDER_Y + 2.2, 0.0), 3, 4.6, 5.0, 10, 11, "plate",
                 trim="plate_shade", thick=1.2)
    # the belt and its buckle, the fauld
    loft(body, ((WAIST_Y - 1.1, 5.55, 4.05, 0.0), (WAIST_Y + 0.5, 5.5, 4.0, 0.0)), "marble_deep", n=16, t=0.5)
    body.box_at((0.0, WAIST_Y - 0.3, -4.35), (1.8, 1.6, 0.5), "gold")
    body.box_at((0.0, WAIST_Y - 0.3, -4.5), (0.9, 0.8, 0.4), "marble_deep")
    for i in range(2):
        y = WAIST_Y - 1.2 - 1.9 * i
        loft(body, ((y, 5.8 + 0.5 * i, 4.3 + 0.4 * i, 0.0), (y - 2.0, 6.3 + 0.6 * i, 4.7 + 0.5 * i, 0.0)),
             "plate" if i == 0 else "plate_shade", n=14, t=0.55)


# ------------------------------------------------------------------ head, hair, halo -------------------------------
def head(m):
    """A serene carved face under a circlet, built the way a sculptor blocks a head: the skull a turned oval, the
    face a plane set forward of it with the features MODELLED IN RELIEF deep enough to shade - a brow ridge over
    eye sockets cut back into shadow, the closed eyes as lids under it, the nose a wedge standing off the face with
    its bridge from the brow and a base over the lip, cheekbones, hollows under them, the lips, a chin; the hair in
    locks swept back from the brow and falling behind the ears, the face itself clear; a gold circlet. The head is
    bowed (HEAD_BOW). The halo on its own bone, tipped forward for the camera above; its outer ring from L6."""
    neck_y = SHOULDER_Y + 1.4
    h = m.bone("head", [0, neck_y, -0.2], parent="body")
    octagon_pole(h, 0, -0.2, neck_y - 1.2, neck_y + 2.6, 2.9, "marble")
    hy = neck_y + 2.2                                     # the jaw's underside
    F = -2.45                                             # the face plane (z): the features stand off it
    ngon_prism(h, 0, -0.1, hy + 1.2, hy + 5.1, 4.9, "marble", n=10)                       # the skull
    ngon_prism(h, 0, -0.7, hy - 0.1, hy + 1.6, 3.6, "marble", n=8)                        # the jaw
    h.box_at((0, hy + 0.4, F + 0.1), (1.9, 1.1, 0.9), "marble")                            # the chin, forward
    h.box_at((0, hy + 0.05, F + 0.55), (1.4, 0.5, 0.6), "marble_shade")                    # ... its underside
    # the hair: a cap of locks swept back from the brow, parted at the centre, falling behind the ears; the face clear
    lathe(h, 0, 0.3, 2.75, 2.9, ((hy + 4.4, 1.0, hy + 5.7, 0.82), (hy + 5.6, 0.82, hy + 6.7, 0.25)),
          "marble_shade", n=10, t=0.75)
    ngon_prism(h, 0, 0.7, hy + 1.4, hy + 5.0, 5.0, "marble_shade", n=10)                  # the mass behind the ears
    for e in (1, -1):
        for k, (dx, dz, L, w) in enumerate(((2.5, -0.2, 5.0, 1.2), (2.2, 1.4, 6.6, 1.4), (1.2, 2.6, 7.2, 1.3),
                                            (0.0, 3.0, 6.6, 1.5))):
            top = (e * dx, hy + 4.6, dz)
            bot = (e * (dx + 0.6), hy + 4.6 - L, dz + 1.0)
            slab(h, tuple((top[i] + bot[i]) / 2 for i in range(3)), tuple(bot[i] - top[i] for i in range(3)),
                 (0, 0, 1), (w, 1.1, L), "marble_shade" if k % 2 else "marble")
        # a wave over each temple, swept back from the parting
        slab(h, (e * 1.5, hy + 4.9, -1.6), (e * 1.0, -0.15, 0.55), (0, 1, 0), (1.5, 0.9, 2.4), "marble")
        slab(h, (e * 2.3, hy + 4.0, -0.9), (e * 0.6, -0.8, 0.5), (0, 1, 0), (1.1, 0.8, 2.0), "marble_shade")
    # THE FACE, in relief off the plane F (toward -z = forward)
    slab(h, (0, hy + 3.55, F - 0.35), (1, 0, 0), (0, 1, -0.4), (0.75, 0.8, 3.6), "marble")    # the brow ridge
    for e in (1, -1):
        h.box_at((e * 1.05, hy + 2.95, F + 0.35), (1.35, 0.9, 0.5), "marble_deep")           # the socket, cut back
        h.box_at((e * 1.0, hy + 3.0, F + 0.05), (1.1, 0.3, 0.35), "marble_shade")             # the closed lid
        h.box_at((e * 1.0, hy + 2.75, F + 0.12), (0.9, 0.2, 0.3), "marble")                   # the lower lid's rim
        slab(h, (e * 1.9, hy + 2.3, F + 0.55), (e * 0.45, 0.0, -0.9), (0, 1, 0), (0.8, 0.9, 1.3), "marble")  # cheekbone
        h.box_at((e * 1.55, hy + 1.6, F + 0.45), (1.0, 0.9, 0.4), "marble_shade")             # the hollow under it
    # the nose: a wedge from the brow down to the base, standing well off the face
    slab(h, (0, hy + 2.3, F - 0.55), (0, 1.0, -0.33), (1, 0, 0), (0.72, 0.95, 2.3), "marble")
    slab(h, (0, hy + 2.3, F - 0.3), (0, 1.0, -0.33), (1, 0, 0), (1.1, 0.5, 2.0), "marble_shade")   # its sides
    h.box_at((0, hy + 1.55, F - 0.45), (1.25, 0.55, 0.75), "marble")                            # the base and nostrils
    h.box_at((0, hy + 1.5, F - 0.25), (1.5, 0.3, 0.4), "marble_shade")
    # the mouth: an upper lip, the dark parting, a fuller lower lip
    h.box_at((0, hy + 1.15, F - 0.1), (1.35, 0.3, 0.45), "marble_shade")
    h.box_at((0, hy + 0.95, F - 0.05), (1.2, 0.18, 0.4), "marble_deep")
    h.box_at((0, hy + 0.75, F - 0.12), (1.1, 0.32, 0.5), "marble")
    # the circlet: a gold band round the brow, a boss at its front
    ring(h, (0, hy + 4.25, -0.2), 2.55, 12, 0.4, 0.5, "gold")
    h.box_at((0, hy + 4.4, F - 0.35), (0.8, 0.8, 0.45), "gold", rotation=[0, 0, 45], pivot=[0, hy + 4.4, F - 0.35])
    # the halo: a broad gold ring behind the head on its own bone, tipped forward so the camera above sees its face,
    # with a thin inner ring; the outer ring with its long rays only at the crown's last level
    hc = (0, hy + 4.8, 4.8)
    halo = m.bone("halo", list(hc), parent="head", rotation=[-16, 0, 0])
    ring(halo, hc, 5.6, 16, 1.0, 0.6, "gold")
    ring(halo, hc, 3.9, 16, 0.35, 0.4, "gold")
    outer = m.bone("halo_outer", list(hc), parent="halo")
    ring(outer, hc, 7.6, 20, 0.5, 0.45, "gold")
    for k in range(8):
        a = k * math.pi / 4
        L = 4.0 if k % 2 == 0 else 2.6
        cx, cy = hc[0] + (7.6 + L / 2) * math.cos(a), hc[1] + (7.6 + L / 2) * math.sin(a)
        outer.box_at((cx, cy, hc[2]), (L, 0.55, 0.45), "gold", rotation=[0, 0, math.degrees(a)], pivot=[cx, cy, hc[2]])
    return h, hy


# ------------------------------------------------------------------ the wings --------------------------------------
# the leading edge in the wing's plane: (u out from the root, v up) - shoulder, elbow, wrist, the hand's tip
ARM = ((0.0, 0.0), (8.0, 6.0), (17.0, 14.0), (23.0, 22.0))


def _arm_at(t):
    i = min(2, int(t))
    f = t - i
    (u0, v0), (u1, v1) = ARM[i], ARM[i + 1]
    return (u0 + (u1 - u0) * f, v0 + (v1 - v0) * f)


def wings(m):
    """Spread wings, raised, each on two bones: the root on the shoulder blade (turned back and drooped by the fold)
    and the HAND at the wrist (the primaries fold along the forearm). Built feather by feather, row over row - ten
    primaries fanned from the hand (the outer ones long, swept up past the head, the inner turning down), eleven
    secondaries hanging from the forearm, four tertials from the arm, then greater, median and lesser coverts over
    their roots, each row thicker and a step in front of the one it covers. Every feather overlaps its neighbour
    (spacing under its width, a shingle step in depth) and has a rounded tip and a shaft ridge."""
    for side in (1, -1):
        rx, ry, rz = side * WING_ROOT[0], WING_ROOT[1], WING_ROOT[2]
        wg = m.bone("wing_left" if side > 0 else "wing_right", [rx, ry, rz], parent="body",
                    rotation=[WING_LEAN, 0, 0])
        wu, wv = ARM[2]
        hand = m.bone(("wing_left" if side > 0 else "wing_right") + "_hand",
                      [rx + side * wu, ry + wv, rz - WING_CUP * wu * wu], parent=wg.name)

        def P(u, v, w=0.0):
            return (rx + side * u, ry + v, rz + w - WING_CUP * u * u)

        def feather(bone, u, v, ang, L, wd, thick, w, col, louvre=0.0, shaft=True):
            """A feather from (u, v) pointing `ang` degrees (0 = straight out, 90 = up, -90 = down) on the surface:
            a vane, a rounded tip (two turned squares) and a shaft ridge on its face."""
            a = math.radians(ang)
            du, dv = math.cos(a), math.sin(a)
            vane = L - 0.55 * wd
            top, end = P(u, v, w), P(u + du * vane, v + dv * vane, w)
            d = _unit(tuple(end[k] - top[k] for k in range(3)))
            um = u + du * vane / 2
            side_pt = P(um - dv, v + dv * vane / 2 + du, w)
            mid = P(um, v + dv * vane / 2, w)
            q = tuple(side_pt[k] - mid[k] for k in range(3))
            q = _unit(tuple(q[k] - d[k] * sum(q[j] * d[j] for j in range(3)) for k in range(3)))
            n = _cross(d, q)
            if louvre:
                lv = math.radians(louvre)
                q = tuple(q[k] * math.cos(lv) + n[k] * math.sin(lv) for k in range(3))
                n = _cross(d, q)
            vane = math.dist(top, end)
            c = tuple((top[k] + end[k]) / 2 for k in range(3))
            slab(bone, c, d, q, (wd, thick, vane + 0.02), col)
            s = 0.62 * wd                                                       # the pointed tip: one turned square
            d45 = tuple((d[k] + q[k]) / math.sqrt(2) for k in range(3))        # (its diagonal under the vane's width,
            q45 = tuple((q[k] - d[k]) / math.sqrt(2) for k in range(3))        # so the fan's edge is points, not teeth)
            slab(bone, end, d45, q45, (s, thick, s), col)
            if shaft:                                                           # the shaft: a ridge down the vane's face
                cs = tuple(c[k] - n[k] * (thick / 2 + 0.12) for k in range(3))
                slab(bone, cs, d, q, (0.42, 0.3, vane * 0.9), "marble_shade")

        # primaries from the hand (t 2..3): the outermost swept up, the innermost turning down and out
        for k in range(10):
            t = 3.0 - k * 0.1
            u, v = _arm_at(t)
            ang = 72 - k * 9.0
            L = 26.0 - k * 0.7
            feather(hand, u, v - 0.5, ang, L, 3.6, 0.5, 0.0 + 0.09 * k, "feather_marble", louvre=7)
        # secondaries from the forearm (t 1..2), hanging down and a little out; tertials from the arm (t 0..1)
        for k in range(11):                                     # shorter and steeper toward the body, so the
            t = 1.95 - k * 0.09                                  # wing's trailing edge curves up to the shoulder
            u, v = _arm_at(t)
            feather(wg, u, v - 0.7, -56 - k * 2.6, 20.0 - k * 0.6, 3.3, 0.5, 0.9 + 0.09 * k, "feather_marble",
                    louvre=7)
        for k in range(4):
            t = 0.95 - k * 0.24
            u, v = _arm_at(t)
            feather(wg, u + 0.3, v - 0.8, -84 - k * 6, 13.0 - k * 1.4, 3.0, 0.5, 1.9 + 0.1 * k, "feather_marble",
                    louvre=6)
        # the greater coverts over the flights' roots, the medians over theirs, the lessers over those (each row a
        # step in front: -w, and thicker)
        for row, (n, t0, t1, L, wd, thick, w0, dv, da) in enumerate((
                (15, 2.9, 0.05, 10.5, 3.0, 0.8, -0.35, -0.9, 8),
                (13, 2.75, 0.1, 7.0, 2.7, 1.0, -0.75, -0.6, 12),
                (12, 2.6, 0.15, 4.4, 2.4, 1.2, -1.1, -0.3, 16))):
            for k in range(n):
                t = t0 - k * (t0 - t1) / (n - 1)
                u, v = _arm_at(t)
                base = (74 - (t - 2) * 90) if t > 2 else (-60 - (2 - t) * 22)
                bone = hand if t > 2.02 else wg
                feather(bone, u, v + dv, base + da, L, wd, thick, w0 - 0.02 * k, "feather_marble_shade" if row != 1
                        else "feather_marble", shaft=row == 0)
        # the arm itself: the leading edge, a rounded bar of carved down along the top of the coverts
        for i, ((u0, v0), (u1, v1)) in enumerate(zip(ARM, ARM[1:])):
            bone = hand if i == 2 else wg
            A, B = P(u0, v0, -1.1), P(u1, v1, -1.1)
            c = tuple((A[k] + B[k]) / 2 for k in range(3))
            slab(bone, c, tuple(B[k] - A[k] for k in range(3)), (0, 0, 1), (2.8, 2.4, math.dist(A, B) + 1.4), "marble")
        ex, ey, ez = P(*ARM[1], -1.1)
        wg.box_at((ex, ey, ez), (2.6, 2.6, 2.5), "marble", rotation=[0, 0, 45], pivot=[ex, ey, ez])      # the elbow
        wx, wy, wz = P(*ARM[2], -1.1)
        hand.box_at((wx, wy, wz), (2.6, 2.6, 2.5), "marble", rotation=[0, 0, 45], pivot=[wx, wy, wz])    # the wrist
        m.contact_ok += [("body", wg.name), (wg.name, hand.name)]


# ------------------------------------------------------------------ arms, hands, sword -----------------------------
GRIP_R = 0.66
HAND = dict(finger_w=0.64, finger_t=0.54, palm_t=0.8, palm_len=1.95, cuff=None, cuff_len=0.6)
SWORD_X, SWORD_Z = 0.0, -7.6
POMMEL_Y = 31.6         # the pommel's top: the hands under it, at the waist


def sword(m):
    """The greatsword planted point-down before the figure: a gold pommel, a grip bound in a spiral, a GOLD
    cross-guard with quillons sweeping down to knobbed ends, a long broad blade of the darker vein stone with a
    fuller and a ridge, tapering to the point on the plinth."""
    b = m.bone("sword", [SWORD_X, 15.0, SWORD_Z], parent="body")
    gx, gz = SWORD_X, SWORD_Z
    ngon_prism(b, gx, gz, POMMEL_Y - 1.7, POMMEL_Y, 2.6, "gold", n=8)                      # the pommel
    b.box_at((gx, POMMEL_Y + 0.3, gz), (1.2, 0.7, 1.2), "gold", rotation=[0, 45, 0], pivot=[gx, POMMEL_Y + 0.3, gz])
    G = POMMEL_Y - 7.6                                                                     # the guard's top
    octagon_pole(b, gx, gz, G, POMMEL_Y - 1.6, 2 * GRIP_R, "marble_shade")                 # the grip
    for k in range(5):                                                                     # its binding
        y = G + 0.6 + k * 1.15
        b.box_at((gx, y, gz), (1.55, 0.3, 1.55), "marble_deep", rotation=[0, 45, 18], pivot=[gx, y, gz])
    b.cube([gx - 1.5, G - 1.7, gz - 0.95], [3.0, 1.7, 1.9], "gold")                        # the guard's block
    for s in (1, -1):                                                                      # quillons sweeping down
        px, py = gx + s * 1.5, G - 0.85
        for L, t, a in ((2.6, 1.0, 10), (2.2, 0.85, 26)):
            x0 = px if s > 0 else px - L
            b.cube([x0, py - t / 2, gz - 0.62], [L, t, 1.24], "gold", rotation=[0, 0, -s * a], pivot=[px, py, gz])
            px += s * L * math.cos(math.radians(a))
            py -= L * math.sin(math.radians(a))
        b.box_at((px, py, gz), (1.28, 1.28, 1.28), "gold", rotation=[0, 0, 45], pivot=[px, py, gz])
    # the blade: from under the guard down to the point on the plinth, tapering; a fuller and a ridge
    top, tip = G - 1.7, 0.3
    hw0, hw1 = 2.4, 1.5
    for e in (1, -1):
        p0, p1 = (gx + e * hw0 / 2, top), (gx + e * hw1 / 2, tip + 3.0)
        c = ((p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2, gz)
        slab(b, c, (p1[0] - p0[0], p1[1] - p0[1], 0), (1, 0, 0), (hw0 / 2 + 0.1, 0.65, math.dist(p0, p1) + 0.02),
             "marble_deep")
        P0, T = (gx + e * hw1, tip + 3.0), (gx, tip)
        c = ((P0[0] + T[0]) / 2 - e * 0.2, (P0[1] + T[1]) / 2 + 0.2, gz)
        slab(b, c, (T[0] - P0[0], T[1] - P0[1], 0), (1, 0, 0), (0.95, 0.6, math.dist(P0, T)), "marble_deep")
    b.cube([gx - 0.28, tip + 2.4, gz - 0.5], [0.56, top - tip - 2.6, 1.0], "marble")   # the ridge, catching the light
    return b, G


def arm(m, side):
    """An arm in plate: a rerebrace on the upper arm, a couter at the elbow, a vambrace flaring a little to the
    wrist, into the hand (solved onto the grip). Returns (upper, fore, wrist point in rest space)."""
    pre = "left" if side > 0 else "right"
    sx, sy = side * SHOULDER_X, SHOULDER_Y - 0.6
    ey = sy - UPPER
    wy = ey - FORE
    up = m.bone(pre + "_arm", [sx, sy, 0], parent="body")
    ngon_prism(up, sx, 0, ey + 0.4, sy + 0.6, 3.6, "plate", n=8)
    up.box_at((sx, sy + 0.2, 0), (3.7, 1.6, 3.7), "plate_shade", rotation=[0, 22.5, 0], pivot=[sx, sy + 0.2, 0])
    up.box_at((sx, ey + 3.4, 0), (3.8, 1.0, 3.8), "plate_shade", rotation=[0, 22.5, 0], pivot=[sx, ey + 3.4, 0])
    fo = m.bone(pre + "_fore", [sx, ey, 0], parent=pre + "_arm")
    octagon_axle(fo, (sx, ey, 0), 3.6, 3.6, "plate")                                      # the couter
    octagon_pole(fo, sx, 0, wy + 0.4, ey, 2.4, "marble")                                  # the forearm
    loft(fo, ((ey - 1.2, 1.75, 1.75, 0.0), (wy + 2.2, 2.1, 2.1, 0.0)), "plate", n=10, t=0.5)   # the vambrace
    loft(fo, ((wy + 2.6, 2.2, 2.2, 0.0), (wy + 1.9, 2.25, 2.25, 0.0)), "plate_shade", n=10, t=0.5)
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
    m = Model("White Angel statue v2 (capitol crown)", "sunforged", "building statue (the White Angel capitol)",
              PALETTE, EMISSIVE)
    legs(m)
    skirt(m.bones["body"])
    armour(m.bones["body"])
    wings(m)
    head(m)
    _, G = sword(m)
    hands = m.bone("hands", [0, SHOULDER_Y, 0], parent="body")
    m.contact_ok += [("hands", "sword"), ("hands", "left_fore"), ("hands", "right_fore"), ("body", "head"),
                     ("body", "left_leg"), ("body", "right_leg"), ("body", "left_arm"), ("body", "right_arm"),
                     ("body", "sword"), ("head", "halo"), ("halo", "halo_outer")]
    la = arm(m, 1)
    ra = arm(m, -1)
    top_hand = (SWORD_X, POMMEL_Y - 2.9, SWORD_Z)
    low_hand = (SWORD_X, POMMEL_Y - 5.4, SWORD_Z)
    e1 = hold(m, la, "hands", top_hand, 1, _unit((7.2, -2.4, 6.6)), (6.4, 29.5, -1.8))
    e2 = hold(m, ra, "hands", low_hand, -1, _unit((-7.2, 0.2, 6.8)), (-6.4, 29.0, -1.6))
    m.pose = {"head": [HEAD_BOW, 0, 0]}
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
          f"depth {(z1 - z0) * k / 16:.1f}); modelPx {y1 - y0:.1f}, halfSpan {hx * k / 16:.1f} blocks")


if __name__ == "__main__":
    main()
