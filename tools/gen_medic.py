#!/usr/bin/env python3
"""The Sunforged Field Medic (witch_unit in the game) - a T1 support body through the Paladin's pipeline
(claude/paladin-radiant.md): concept -> tools/dsl_to_game.py -> the game.

Silhouette (claude/sunforged-t1-silhouettes.md, the Oct 14 lineup verdict "the Medic doesn't read - one oversized prop
each"): a slim robed healer, hood up, carrying a staff half again her height crowned with a SUN DISC that floats in a
gilded ring and glows - "the glowing one", a bright dot on a pole from the RTS camera. The floating disc is her one
impossible thing.

Round 2 (lovish, Oct 16: "everything about the designs need to be sleaker, with more detail, and depth"; "the face
just being a black blob isn't right ... a shadow covering facial features instead"; "the fingers ... looks like a block
mesh for hands"; "the elbows don't match human anatomy"; "The blocks your using are sticking out from the characters
making them look blocky and edges sticking out"). What is round is an EXACT polygon now - no two crossed boxes (a plus
sign with notched corners) and no stacked rows (a staircase):
  - the robe one flared skirt of twelve planar plates round an elliptic cone (gen_squire.cone_shell's maths), split
    over the four panel bones that sway, a flush gold hem band at its foot, the team scapular hanging over it;
  - the bodice, belt and the two-tier shoulder mantle the same way (eight plates each);
  - the hood a turned cowl (gen_halberdier.shell arcs, a gen_models.lathe crown, every ring on the same ten facets)
    deeper than the head, its opening a pointed arch; her face (FACE, A/B below) in facets in its shade;
  - the sleeves exact octagons, an octagon_axle at each elbow, a flared six-plate bell from the elbow to the wrist;
  - gloved hands closed round what they hold (gen_models.grip_hand): the staff, and a potion by its neck;
  - the staff's ring and disc exact 12-gons in the disc's plane (no stepped rows), its fittings turned hexagons.

Rig for the game (FieldMedicUnitModel): the right arm and the staff hang from one bone on the right shoulder
("staff_arm"); the staff's pivot is the wrist (turning the staff alone bends the hand at the sleeve's mouth) and the
right glove rides on it; the disc is its own bone ("sun_disc": it spins and bobs), the ribbons too ("staff_ribbon");
the robe hangs in four panels from the hips (robe_front / back / left / right: they sway and part for the legs). Bone
names are the ones the Java and design/poses/sunforged_field_medic_v2.json use.

The figure (the Oct 16 anatomy pass - HIP, SHOULDER_X, BODICE, HOOD_R... below): a woman's proportions measured
against Winter / Drillis-Contini - hips at .52 of her stature, shoulder joints .24 apart, a waist at .62 between the hips
and the bust, the head a face in a hood rather than a dome.

    python3 tools/gen_medic.py && python3 tools/game_preview.py design/models/sunforged_field_medic_v2.json ...
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_models import Model, Bone, OUT, foot, slab, grip_hand, octagon_pole, octagon_axle  # noqa: E402
from gen_models import _unit, _cross, _add, _mul  # noqa: E402
from gen_halberdier import shell  # noqa: E402
from gen_levy import ngon, plate  # noqa: E402

PALETTE = {
    "robe": "#ece6d8", "robe_dark": "#b7ad98", "hood_dark": "#2a2420", "hood_lining": "#5e5046", "linen": "#f1ece2",
    "tabard": "#2f5fb3", "tabard_dark": "#244a8c", "gold": "#d4a63c",
    "leather": "#6b4a30", "leather_dark": "#4a3220", "staff_wood": "#7a5536", "boot": "#5b3f2a",
    # buff gloves, lighter than the sleeves' gold and the staff: at 0.36 px a finger, dark leather on dark wood sank
    # into one mass (the Squire's lesson)
    "glove": "#b48c5c", "glove_dark": "#8e6a46",
    "sun": "#ffd96a", "glow": "#fff1b0",
    "potion_red": "#ff5a5a", "potion_gold": "#ffcf4a",
    # the face (FACE "open"): skin, the eyes' hollows, the eyes - "plain" names (no material fragment of
    # tools/materials.py in them); the mask (FACE "mask"): ivory, its shade
    "skin": "#c99a7a", "socket": "#8c5c46", "eye": "#2a1d17", "lip": "#b06e5e",
    "ivory": "#efe7d6", "ivory_shade": "#b9ab92",
}
EMISSIVE = ["sun", "glow", "potion_red", "potion_gold"]

FACE = os.environ.get("MEDIC_FACE", "open")      # "open" or "mask": the A/B of Oct 16 (see hood())

# The figure (the anatomy audit of Oct 16 - lovish: "The units need more realism and texture, look at human anatomy
# could help"): a woman's, measured against Winter / Drillis-Contini as fractions of her stature S = 28.1 (sole to the
# hood's crown). The hips were at .46 S (canon .53) under a trunk of .34 S (canon .29) - short legs under a long body;
# the shoulder joints 8.4 apart (.30 S, wider than the Paladin's) - a man's shoulders on the only woman of the line.
HIP = 14.6                    # .52 S (13.0 was .46): the legs longer by what the trunk gives up; the shoulders stay
KNEE = 7.9                    # .28 S, 54% of the way up to the hip (7.0)
SHOULDER_X, SHOULDER_Y = 3.4, 22.6     # joints 6.8 apart (.24 S; 4.2 was .30), the sleeves just off the waist
UPPER, FORE = 5.0, 4.25       # the elbow 54% down the arm, upper : forearm 1.18 (4.6: 1.09; canon 1.27)
HEM = 2.6
CZ = -0.2                     # the body's centre line in z (the front is -z)
BELT = (HIP + 0.8, HIP + 2.0)  # the hip belt: over the skirt's top and the bodice's foot (it rose with the hips)
BELT_AB = (3.14, 2.44)        # its half-widths (x, z): round the skirt's top
BELT_TOP_S = 0.93             # its top drawn in toward the waist (0.97: a 0.7 px shelf over the waisted bodice)
SKIRT_TOP = HIP + 1.0         # the skirt's top edge, under the belt (0.2 above the panels' hinge at HIP + 0.8: the part
#                               above a hinge swings INTO the body when the panel swings out - gen_models.split_above)
# the skirt's half-widths at its top (x, z) and its x half-width at the hem; plates 0.3 thick (at 0.4 the panels'
# plates crossed 0.7 px at their seams as they parted for the legs - tools/contact_check.py over the walk keys)
SKIRT = dict(A=3.0, B=2.3, A_hem=4.7)
SKIRT_T = 0.3
# the bodice (A x B half-widths at scale 1): a waist at .62 S between the hips under the belt and the bust under the
# mantle - it was one cone narrowest at its foot and widening straight up to the shoulders, a man's V
BODICE = (2.62, 1.9)
WAIST_Y, BUST_Y = 17.4, 20.4
WAIST_S, BUST_S, HIPS_S = 0.86, 1.05, 1.06   # its scale at the waist, the bust, and at the belt's foot
MANTLE = dict(A1=SHOULDER_X + 0.75, B1=2.9, A0=2.0)
# the scapular in the owner's colour: the bib on the bodice and the strip down the skirt, front and back - wider as
# the mantle narrowed, so the team colour keeps its share of her from the RTS camera (2.2 and 2.3)
BIB_W, SCAPULAR_W = 2.3, 2.85
THIGH_W, THIGH_D = 2.1, 2.2   # (1.7 square: the boot was wider than the thigh - the leg widened toward the foot)
CALF_W, CALF_D = 1.7, 1.8     # the hose from the boot's top to the knee
BOOT_W, BOOT_TOP = 1.45, 3.7  # the boot narrower than the calf, its top under the robe (2.0, as wide as the thigh)


# ------------------------------------------------------------------ exact forms ------------------------------------
def cone_plates(cx, cz, A, B, y0, s0, y1, s1, n, t, exact_at=None, phase=0.0):
    """The plates of gen_squire.cone_shell - n flat plates round the elliptic cone A x B (half-widths) scaled s0 at y0
    and s1 at y1, each EXACTLY planar (a cone is developable) - as specs instead of cubes, so one cone can be shared
    out over several bones (the robe's four panels). Each spec: dict(k, th (degrees from the front, + toward the
    unit's left, +x), centre, up, across, nrm, width, L, t) - centre is the plate's centre (half its thickness inside
    the surface), width its width exact at `exact_at` (default the middle; at the wide end the corners meet, narrower
    up the cone each plate's ends stand a hair past the crease - put the wide end where it shows)."""
    def tangent(k):
        th = 2 * math.pi * (k + phase) / n
        N = (math.sin(th) / A, -math.cos(th) / B)
        L = math.hypot(*N)
        N = (N[0] / L, N[1] / L)
        return th, N, (A * math.sin(th) * N[0] - B * math.cos(th) * N[1])

    def corner(k):
        (_, n0, d0), (_, n1, d1) = tangent(k)[0:3], tangent(k + 1)[0:3]
        det = n0[0] * n1[1] - n0[1] * n1[0]
        return ((d0 * n1[1] - d1 * n0[1]) / det, (n0[0] * d1 - n1[0] * d0) / det)
    out = []
    ye = (y0 + y1) / 2 if exact_at is None else exact_at
    se = s0 + (s1 - s0) * (ye - y0) / (y1 - y0)
    for k in range(n):
        th, N, _ = tangent(k)
        p, q = corner(k - 1), corner(k)
        pts = [(cx + p[0] * sc, y, cz + p[1] * sc) for sc, y in ((s0, y0), (s1, y1))] + \
              [(cx + q[0] * sc, y, cz + q[1] * sc) for sc, y in ((s0, y0), (s1, y1))]
        bot = tuple((pts[0][a] + pts[2][a]) / 2 for a in range(3))
        top = tuple((pts[1][a] + pts[3][a]) / 2 for a in range(3))
        up = tuple(top[a] - bot[a] for a in range(3))
        L = math.sqrt(sum(v * v for v in up))
        across = (q[0] - p[0], 0.0, q[1] - p[1])
        width = math.hypot(across[0], across[2]) * se + 0.02
        nrm = _unit(_cross(across, up))
        if nrm[0] * N[0] + nrm[2] * N[1] < 0:
            nrm = tuple(-v for v in nrm)
        mid = tuple((bot[a] + top[a]) / 2 for a in range(3))
        out.append(dict(k=k, th=math.degrees(th), mid=mid, centre=tuple(mid[a] - nrm[a] * t / 2 for a in range(3)),
                        up=_unit(up), across=_unit(across), nrm=nrm, width=width, L=L, t=t))
    return out


def put_plate(bone, p, color, extend=0.04, **kw):
    slab(bone, p["centre"], p["up"], p["across"], (p["width"], p["t"], p["L"] + extend), color, **kw)


def on_plate(bone, p, u, v, size, color, proud=0.02, **kw):
    """A strip laid ON a cone plate: u px across it from its centre line, v px up it from its middle; size = (across,
    up, thickness) - a fold, a scapular, a seam."""
    k = proud + size[2] / 2
    c = tuple(p["mid"][a] + p["across"][a] * u + p["up"][a] * v + p["nrm"][a] * k for a in range(3))
    slab(bone, c, p["up"], p["across"], (size[0], size[2], size[1]), color, **kw)


def cone(bone, cx, cz, A, B, y0, s0, y1, s1, n, t, color, exact_at=None, phase=0.0, **kw):
    """One elliptic cone band of plates on one bone (gen_squire.cone_shell); returns the plate specs."""
    ps = cone_plates(cx, cz, A, B, y0, s0, y1, s1, n, t, exact_at, phase)
    for p in ps:
        put_plate(bone, p, color, **kw)
    return ps


def xy_ngon(bone, c, w, depth, color, n=12, phase=0.0, **kw):
    """A REGULAR n-gon plate in the x-y plane (a disc seen from the front), w across the flats, `depth` thick along z:
    gen_models.ngon_prism turned onto the z axis - n/2 bands turned 360/n apart about z; their union is exactly the
    polygon (the disc was eight stacked rows: a staircase round its rim)."""
    side = w * math.tan(math.pi / n)
    for k in range(n // 2):
        a = phase + 360.0 / n * k
        bone.box_at(list(c), [w, side, depth], color, rotation=[0, 0, a] if abs(a) > 1e-9 else None,
                    pivot=list(c), **kw)


def xy_ring(bone, c, r_out, thick, depth, color, n=12, phase=0.0, **kw):
    """A ring in the x-y plane whose OUTER outline is an exact regular n-gon (flats at r_out): one box a side, as long
    as the outer side, laid inside it - each box's inner corners run on into its neighbour and hide there, so neither
    the outer nor the inner outline shows a step or a corner standing out (gen_models.ring() overlaps its segments by
    0.3 px past the polygon - a tooth at every joint)."""
    L = 2 * r_out * math.tan(math.pi / n)
    for k in range(n):
        a = math.radians(phase) + 2 * math.pi * k / n
        rc = r_out - thick / 2
        cx, cy = c[0] + rc * math.cos(a), c[1] + rc * math.sin(a)
        bone.box_at((cx, cy, c[2]), (thick, L, depth), color, rotation=[0, 0, math.degrees(a)], pivot=[cx, cy, c[2]],
                    **kw)


def axis_ngon(bone, centre, axis, w, length, color, n=8, **kw):
    """A regular n-gon prism (w across the flats) along any axis through `centre`: ngon_prism's bands as slabs."""
    a = _unit(axis)
    ref = (1.0, 0.0, 0.0) if abs(a[0]) < 0.9 else (0.0, 0.0, 1.0)
    p = _unit(_cross(a, ref))
    q = _cross(a, p)
    side = w * math.tan(math.pi / n)
    for k in range(n // 2):
        phi = math.pi * 2 * k / n
        e = _add(_mul(p, math.cos(phi)), _mul(q, math.sin(phi)))
        slab(bone, centre, a, e, (w, side, length), color, **kw)


# ------------------------------------------------------------------ the body ---------------------------------------
def bodice_s(y):
    """The bodice's scale at height y: the hips under the belt narrowing to the waist, then out to the bust."""
    if y <= WAIST_Y:
        return HIPS_S + (WAIST_S - HIPS_S) * (y - BELT[0]) / (WAIST_Y - BELT[0])
    return WAIST_S + (BUST_S - WAIST_S) * (y - WAIST_Y) / (BUST_Y - WAIST_Y)


def torso(m):
    b = m.bone("body", [0, HIP, 0])
    # the bodice: two bands of eight plates meeting at the waist - from the hips under the belt in to the waist, and
    # out from it to the bust under the mantle (two crossed boxes per band were a plus sign at every height: "blocks
    # sticking out"; one band widening from the hips to the shoulders was a man's V). Each band exact at its wide end
    # (cone_plates: narrower, the plates' ends stand a hair past the crease - at the waist, where the two bands meet)
    A, B = BODICE
    top = BUST_Y + 1.0                                    # on under the mantle, the same slope
    cone(b, 0, CZ, A, B, BELT[0], HIPS_S, WAIST_Y, WAIST_S, 8, 0.5, "robe", exact_at=BELT[0])
    cone(b, 0, CZ, A, B, WAIST_Y, WAIST_S, top, bodice_s(top), 8, 0.5, "robe", exact_at=BUST_Y)
    # the scapular's bib in the owner's colour, front and back, hanging from under the mantle to the belt, gold-edged;
    # the order's sun on the breast. A hanging cloth: it lies on the bust and the hips and falls straight between,
    # standing off the waist (along a waisted bodice a flat panel can only touch its two fullest points)
    y0, y1 = BELT[1] - 0.6, BUST_Y + 0.4
    for side in (-1, 1):                                  # -1 the front, +1 the back
        z0 = CZ + side * (B * bodice_s(y0) + 0.2)
        z1 = CZ + side * (B * bodice_s(y1) + 0.2)
        mid, d = (0, (y0 + y1) / 2, (z0 + z1) / 2), (0, y1 - y0, z1 - z0)
        L = math.hypot(d[1], d[2])
        slab(b, mid, d, (1, 0, 0), (BIB_W, 0.3, L), "tabard")
        if side < 0:
            nrm = _unit((0, -d[2], d[1]))                 # out of the front (-z)
            if nrm[2] > 0:
                nrm = _mul(nrm, -1.0)
            for s in (1, -1):
                slab(b, _add(mid, (s * (BIB_W / 2 + 0.02), 0, 0), _mul(nrm, 0.03)), d, (1, 0, 0), (0.22, 0.36, L),
                     "gold")
            # the sun: a gold diamond with a bright core, on the breast
            yb = BUST_Y - 1.5
            cb = _add(mid, _mul(_unit(d), (yb - mid[1]) / (d[1] / L)))
            dg = _unit(_add(_unit(d), (1, 0, 0)))
            slab(b, _add(cb, _mul(nrm, 0.22)), dg, _cross(nrm, dg), (1.3, 0.2, 1.3), "gold")
            slab(b, _add(cb, _mul(nrm, 0.32)), dg, _cross(nrm, dg), (0.62, 0.2, 0.62), "sun")
    # the hip belt: eight plates over the bodice's foot and the skirt's top (it hides the hinge), a gold buckle
    belt = cone(b, 0, CZ, BELT_AB[0], BELT_AB[1], BELT[0], 1.0, BELT[1], BELT_TOP_S, 8, 0.45, "leather")
    bz = belt[0]["mid"][2]
    b.box_at((0, (BELT[0] + BELT[1]) / 2, bz - 0.12), (1.0, 1.0, 0.24), "gold")
    return b, belt


def belt_kit(b, belt):
    """What a field medic carries on the belt (the concept's layered detail where the RTS camera sees it): two vials
    in loops on the right front - their light says "potions" - and a pouch on the right hip, each hung ON a belt plate
    (along its face)."""
    for k, (u, col) in enumerate(((-0.45, "potion_red"), (0.25, "potion_gold"))):
        p = belt[7]                                        # front right (-x): the plate turned 315 = -45 deg
        c = tuple(p["mid"][a] + p["across"][a] * u + p["nrm"][a] * 0.38 for a in range(3))
        b.box_at((c[0], c[1] - 0.55, c[2]), (0.5, 1.15, 0.5), col)
        b.box_at((c[0], c[1] + 0.2, c[2]), (0.36, 0.34, 0.36), "leather_dark")
    # the pouch on the right hip: a body hung on the belt, its flap folded over the top and down the face
    p = belt[6]                                            # right (-x)
    c = tuple(p["mid"][a] + p["nrm"][a] * 0.62 for a in range(3))
    slab(b, (c[0], c[1] - 0.95, c[2]), p["up"], p["across"], (1.5, 0.95, 1.9), "leather")
    slab(b, tuple(c[a] + p["nrm"][a] * 0.08 + (0, 0.05, 0)[a] for a in range(3)), p["up"], p["across"],
         (1.58, 1.1, 0.7), "leather_dark")


def mantle(body):
    """The shoulder mantle in team colour - the biggest colour the RTS camera sees: a yoke from the neck out to the
    shoulder line and a short cape falling from it over the tops of the arms, both eight planar plates round the
    shoulders (it was an octagon of two crossed boxes with flat flaps over the arms - a box with ledges), a gold hem
    flush at its foot (every team panel gets a gold edge, so the colour keeps its outline on any ground - art-theory
    section 15)."""
    # (a woman's shoulders since the anatomy pass: 5.05 x 3.25 spanned 10.4 px, .37 of her stature, and reached her
    # chin; it ends just past the sleeves now, falling ~32 degrees from the neck - and a capelet to the middle of the
    # upper arm: to 19.75 on the narrower shoulders it was a blue drum round her chest, hiding the bust over the waist)
    A1, B1, y1 = MANTLE["A1"], MANTLE["B1"], 22.9      # the shoulder line
    A0, y0 = MANTLE["A0"], 24.25                       # at the neck, under the hood (z follows: one ellipse)
    cone(body, 0, CZ, A1, B1, y1, 1.0, y0, A0 / A1, 8, 0.42, "tabard", exact_at=y1)
    yh, yb, grow = 20.7, 20.2, 0.12                    # the cape's foot (the hem band yb..yh) and its flare there
    s_h = 1.0 + grow * (y1 - yh) / (y1 - yb)
    cape = cone(body, 0, CZ, A1, B1, yh, s_h, y1, 1.0, 8, 0.42, "tabard", exact_at=yh)
    cone(body, 0, CZ, A1, B1, yb, 1.0 + grow, yh, s_h, 8, 0.42, "gold", exact_at=yb)
    return cape


def satchel(body):
    """A leather satchel on the left hip with a sun clasp, its strap across the chest from the right shoulder - one
    slab from the bodice's right shoulder to the left hip, ON the bib."""
    # hung from the belt over the skirt's left-back plate, leaning out with its flare (a box hung plumb sank 0.9 px
    # into the skirt at its foot), behind the arm's swing
    lean = math.degrees(math.atan2(SKIRT["A_hem"] - SKIRT["A"], SKIRT_TOP - HEM))
    c = (3.72, BELT[0] - 2.6, 1.15)       # (at the belt, the left forearm's back swing struck it - walk_C)
    rot = [0, -28, lean]
    body.box_at(c, (1.2, 2.8, 2.5), "leather", rotation=rot, pivot=list(c))
    body.box_at((c[0] + 0.1, c[1] + 1.0, c[2]), (1.36, 0.95, 2.62), "leather_dark", rotation=rot, pivot=list(c))  # flap
    body.box_at((c[0] + 0.72, c[1] + 0.4, c[2] - 0.38), (0.14, 0.6, 0.6), "gold", rotation=[45 + rot[0], rot[1], lean],
                pivot=list(c))
    p0, p1 = (2.7, BELT[1] - 0.45, -2.15), (-2.2, 20.9, -2.62)
    mid = tuple((p0[a] + p1[a]) / 2 for a in range(3))
    d = tuple(p1[a] - p0[a] for a in range(3))
    slab(body, mid, d, (0, 0, -1), (0.62, 0.62, math.sqrt(sum(v * v for v in d)) + 0.1), "leather_dark")


# ------------------------------------------------------------------ the robe ---------------------------------------
def robe_panels(m):
    """The skirt: ONE flared form - twelve planar plates round an elliptic cone from under the belt to the hem (four
    flat boards with gaps between them before) - shared over the four panel bones that sway and part for the legs:
    front, left, back, right three plates each. A gold hem band flush with the plates (the same cone, below the cream),
    the team scapular hanging over the front and back with gold edges, the sun of the order on the front one (the
    linen's painted folds and stitched hem do the rest - tools/materials.py). Rest rotations 0: the flare is in the
    cone (the boards were tilted 7-9 degrees)."""
    top, hem_top = SKIRT_TOP, HEM + 0.6
    A, B = SKIRT["A"], SKIRT["B"]
    s_hem = SKIRT["A_hem"] / A

    # the cone's scale, linear in y (one ellipse A x B scaled: a cone's plates are planar only for one A:B ratio)
    def scale(y):
        f = (top - y) / (top - HEM)
        return 1.0 + (s_hem - 1.0) * f

    pivots = {"robe_front": [0, HIP + 0.8, -1.9], "robe_back": [0, HIP + 0.8, 1.9], "robe_left": [2.6, HIP + 0.8, 0],
              "robe_right": [-2.6, HIP + 0.8, 0]}
    bones = {n: m.bone(n, pv, parent="body") for n, pv in pivots.items()}

    def owner(th):
        th = th % 360
        if th < 45 or th >= 315:
            return "robe_front"
        if th < 135:
            return "robe_left"
        if th < 225:
            return "robe_back"
        return "robe_right"
    n = 12
    cream = cone_plates(0, CZ, A, B, hem_top, scale(hem_top), top, 1.0, n, SKIRT_T, exact_at=hem_top)
    gold = cone_plates(0, CZ, A, B, HEM, scale(HEM), hem_top, scale(hem_top), n, SKIRT_T, exact_at=HEM)
    for p, g in zip(cream, gold):
        bo = bones[owner(p["th"])]
        put_plate(bo, p, "robe")
        put_plate(bo, g, "gold")
    # the scapular over the front and back plates, from under the belt to a hand above the hem
    for k, name in ((0, "robe_front"), (6, "robe_back")):
        p = cream[k]
        L = p["L"] - 0.9
        on_plate(bones[name], p, 0, 0.45, (SCAPULAR_W, L, 0.28), "tabard")
        for s in (1, -1):
            on_plate(bones[name], p, s * (SCAPULAR_W / 2 - 0.02), 0.45, (0.2, L, 0.34), "gold")
        if name == "robe_front":                                                                 # the sun on it
            v = -p["L"] / 2 + 2.6
            c = tuple(p["mid"][a] + p["up"][a] * v + p["nrm"][a] * 0.36 for a in range(3))
            dg = tuple((p["up"][a] + p["across"][a]) / math.sqrt(2) for a in range(3))       # a diamond: turned 45
            slab(bones[name], c, dg, p["nrm"], (0.16, 1.2, 1.2), "gold")
            c2 = tuple(c[a] + p["nrm"][a] * 0.1 for a in range(3))
            slab(bones[name], c2, dg, p["nrm"], (0.12, 0.6, 0.6), "sun")
    return bones


# ------------------------------------------------------------------ the hood and the face --------------------------
HOOD_C = (0.0, -0.75)          # the hood's axis (x, z): ahead of the head's, so its rim stands in front of the face
# (3.0 made it 6 px across a 2.5 px face, and it rose 2.9 px over the brow: a bucket; 2.7 was still a dome 1.5x wider
# than tall round the smallest face of the line - the anatomy pass: a head in a hood)
HOOD_R = 2.15
HOOD_N = 8                     # the crown's facets; the sides and the upper ring take all but the two over the face
OPEN = 360 / HOOD_N            # the opening: +-45 degrees from the front, one facet each side (36 on the old ten: on a
#                                narrower hood its rims cut into her cheeks)
FACE_Z = -1.95                 # the front of the face (behind the rim: in the hood's shade)
FACE_K = 1.1                   # the face scaled about the bridge of the nose (eye to chin 1.53 -> 1.68: an 8-head
#                                woman, the line's face scale - the Levy's and the Squire's imply a 7-8 head skull)


class _Scaled:
    """A bone seen through a uniform scale k about c0: box_at() scales each box's centre, size and pivot (its rotation
    stands) - the face built at one size and placed at another."""
    def __init__(self, bone, c0, k):
        self.bone, self.c0, self.k = bone, c0, k

    def _p(self, p):
        return [self.c0[a] + self.k * (p[a] - self.c0[a]) for a in range(3)]

    def box_at(self, centre, size, color, rotation=None, pivot=None, **kw):
        return self.bone.box_at(self._p(centre), [v * self.k for v in size], color, rotation=rotation,
                                pivot=None if pivot is None else self._p(pivot), **kw)


def face(h, hy, skin, hollow, eye, lip):
    """The face's facets, front to back (gen_levy.face's construction, finer and younger): a centre plate; the cheeks
    turned back toward the hood, running down into a narrow jaw; a soft brow ridge over the eye hollows; cheekbones
    under the eyes; the bridge of the nose sloping out to its tip; the lips; a small chin standing over the wimple.
    Every cube under 1.9 px tall (dsl_to_game paints a lit rim and a seam round taller ones - stripes on a face).
    Built at the size it was drawn at and scaled FACE_K about the bridge of the nose."""
    fz = FACE_Z                                                     # the front of the face
    h = _Scaled(h, (0.0, hy + 2.0, fz), FACE_K)
    yb, yt = hy + 1.3, hy + 2.95
    h.box_at((0, (yb + yt) / 2, fz + 0.36), (1.5, yt - yb, 0.72), skin)                           # the centre
    h.box_at((0, hy + 1.12, fz + 0.32), (1.06, 0.5, 0.6), skin)                                   # the jaw, narrower
    for s in (1, -1):
        plate(h, (s * 0.7, fz + 0.02), (s * 1.18, fz + 0.6), hy + 1.3, yt, 0.5, skin, ext=0.1)    # the cheeks
        # the brow: a fine arch, rising from the root of the nose to the temple
        h.box_at((s * 0.56, hy + 2.75, fz - 0.08), (0.78, 0.16, 0.24), skin, rotation=[0, s * 12, s * 9],
                 pivot=[s * 0.56, hy + 2.75, fz - 0.08])
        plate(h, (s * 0.38, fz - 0.05), (s * 0.98, fz + 0.2), hy + 1.98, hy + 2.2, 0.26, skin, ext=0.04)  # cheekbone
        if hollow:
            h.box_at((s * 0.53, hy + 2.47, fz - 0.03), (0.5, 0.34, 0.1), hollow)                   # the eye's hollow
        h.box_at((s * 0.53, hy + 2.45, fz - 0.06), (0.3 if hollow else 0.48, 0.16 if hollow else 0.28, 0.1), eye)
    h.box_at((0, hy + 2.36, fz - 0.12), (0.28, 0.8, 0.26), skin, rotation=[12, 0, 0], pivot=[0, hy + 2.36, fz - 0.12])
    h.box_at((0, hy + 1.92, fz - 0.15), (0.42, 0.28, 0.34), skin)                                 # the tip
    h.box_at((0, hy + 1.56, fz - 0.04), (0.56, 0.14, 0.16), lip)                                  # the lips
    h.box_at((0, hy + 1.12, fz - 0.06), (0.5, 0.4, 0.3), skin)                                    # the chin
    return h


def face_open(h, hy):
    """A - her face in the hood's opening, in its shade (lovish, Oct 16: "a shadow covering facial features instead"
    of the black blob): a young woman. The upper face sits 0.9 px under the hood's arched brow: the baked sky light
    (tools/unit_light.py) shades it - at RTS zoom a warm face in shadow, close up the features."""
    face(h, hy, "skin", "socket", "eye", "lip")


def face_mask(h, hy):
    """B - a healer's ivory mask in the hood's opening, the order's sun on its brow: a calm face carved in it, the eyes
    two dark holes with the hood's shadow in them - the person there but not shown (human dial 2: "no face")."""
    f = face(h, hy, "ivory", None, "hood_dark", "ivory_shade")
    f.box_at((0, hy + 3.0, FACE_Z - 0.1), (0.5, 0.5, 0.12), "gold", rotation=[0, 0, 45],
             pivot=[0, hy + 3.0, FACE_Z - 0.1])


def hood(m):
    """A turned cowl over the head (it was an octagon of two crossed boxes with a flat dark panel in front): its sides
    and back one arc of plates round an axis ahead of the head's - flaring onto the mantle at its foot - so the rim
    stands 0.35 px in front of the face and the face sits in its shade; over the brow it closes in a full ring leaning
    in to a soft crown; the opening a pointed arch (the order's cathedral arch) edged with a rolled hem, lined in a
    dark wool; the sun at the arch's point; a linen wimple under the chin."""
    hy = SHOULDER_Y + 1.0
    h = m.bone("head", [0, hy, 0], parent="body")
    cx, cz = HOOD_C
    R = HOOD_R
    n = HOOD_N - 2                                     # the plates of the sides and of the upper ring
    # the profile, (height over hy, radius / R): the drape on the shoulders, straight sides to the temples, then the
    # crown turning in over two rings to a small lid - a skull's curve, the top 1.6 px over the brow (the rings began
    # at the brow and ended in a lid half the hood's width: from the side and from the RTS camera, a drum)
    DRAPE, SIDE, UPPER_RING, TOP_RING, LID = (0.6, 1.08), (2.6, 1.0), (3.45, 0.86), (4.3, 0.38), 4.5
    # the sides and back: plates of one facet each, on the crown's facets so the rings meet corner to corner
    shell(h, cx, cz, hy + DRAPE[0], R * DRAPE[1], hy + SIDE[0], R, OPEN, 360 - OPEN, n, 0.42, "robe")
    # the upper ring leaves the opening's arch free (closed over the brow, the hood hid the whole face from the RTS
    # camera's 52 degrees: the head read as a white ball); the top ring closes over it. Both shells - each plate as
    # wide as its ring's wide end (gen_models.lathe's slabs are as wide as the middle: on a steep ring a slit opened at
    # every seam's foot - the first pass's "melon" of dark lines)
    shell(h, cx, cz, hy + SIDE[0] - 0.05, R, hy + UPPER_RING[0], R * UPPER_RING[1], OPEN, 360 - OPEN, n, 0.45, "robe")
    shell(h, cx, cz, hy + UPPER_RING[0] - 0.05, R * UPPER_RING[1], hy + TOP_RING[0], R * TOP_RING[1], 0, 360, HOOD_N,
          0.45, "robe")
    # (gen_levy.ngon's flats face where the rings' plates do - phase 0 on ten facets, 22.5 on eight; half a facet off,
    # its corners stood out of the ring. 0.65 deep, down inside the ring: 0.35 deep, its bands fell under
    # dsl_to_game's 0.6 px^3 "lod" detail and the far camera saw a hole in her crown)
    ngon(h, cx, cz, hy + LID - 0.65, hy + LID, 2 * R * TOP_RING[1] + 0.05, "robe", n=HOOD_N,
         phase=(90 + 180 / HOOD_N) % (360 / HOOD_N))

    def r_at(y):                                       # the crown's radius (flats) at height y over hy
        pts = (SIDE, UPPER_RING, TOP_RING)
        for (y0, f0), (y1, f1) in zip(pts, pts[1:]):
            if y <= y1:
                return R * (f0 + (f1 - f0) * (y - y0) / (y1 - y0))
        return R * TOP_RING[1]
    # the rim: a rolled hem down each side of the opening and the two halves of the pointed arch over it
    rz = cz - R * math.cos(math.radians(OPEN))
    rx = R * math.sin(math.radians(OPEN))
    RIM = 0.52                                         # (0.62 on the wider hood: on this one it hid her cheeks)
    y_spring, y_apex = hy + SIDE[0], hy + 3.7          # where the arch springs from the sides, its point
    z_apex = cz - r_at(3.7) / math.cos(math.radians(180 / HOOD_N)) - 0.06   # on the crown's front crease
    for s in (1, -1):
        h.box_at((s * (rx - 0.08), (hy + 0.6 + y_spring) / 2, rz - 0.06), (RIM, y_spring - hy - 0.6 + 0.3, RIM),
                 "robe", rotation=[0, -s * OPEN, 0], pivot=[s * (rx - 0.08), (hy + 0.6 + y_spring) / 2, rz - 0.06])
        p0 = (s * (rx - 0.08), y_spring - 0.15, rz - 0.06)
        p1 = (0.0, y_apex, z_apex)
        mid = tuple((p0[a] + p1[a]) / 2 for a in range(3))
        d = tuple(p1[a] - p0[a] for a in range(3))
        slab(h, mid, d, (s * math.cos(math.radians(OPEN)), 0, math.sin(math.radians(OPEN))), (RIM, RIM,
             math.sqrt(sum(v * v for v in d)) + 0.34), "robe")
    # the lining in the arch over her forehead: the inside of the hood, a dark wool in its shade (its corners behind
    # the arch's two halves)
    h.box_at((0, hy + 3.3, FACE_Z + 0.4), (1.6, 0.7, 0.6), "hood_lining")
    # the sun at the arch's point
    sp = (0.0, y_apex + 0.14, z_apex - 0.3)
    h.box_at(sp, (0.95, 0.95, 0.24), "gold", rotation=[0, 0, 45], pivot=list(sp))
    h.box_at((0.0, sp[1], sp[2] - 0.12), (0.46, 0.46, 0.14), "sun", rotation=[0, 0, 45], pivot=[0.0, sp[1], sp[2] - 0.12])
    # the wimple: linen round the throat under the chin, inside the opening (0.4 thick: at 0.35 on the narrower hood
    # its plates were "lod" detail - gone at distance)
    shell(h, cx, cz + 0.3, hy + 0.15, R - 0.38, hy + 0.98, R - 0.5, -OPEN - 10, OPEN + 10, 2, 0.4, "linen")
    (face_mask if FACE == "mask" else face_open)(h, hy)
    return h


# ------------------------------------------------------------------ arms and hands ---------------------------------
BELL_FLARE, BELL_MOUTH = 1.36, 0.35   # the bell's half-width at its hem (x the forearm's) and its mouth over the wrist
# the sleeve across its flats and the forearm's half-width: a woman's arm in a robe (1.95 and 0.92 on the old
# shoulders - set 8.4 apart, a man's arms)
SLEEVE_W, FORE_R = 1.75, 0.83


def sleeve_arm(m, side, parent):
    """A robed arm: an exact octagonal sleeve to the elbow (a square tube before), the elbow a turned joint round its
    hinge (gen_models.octagon_axle: one rounded elbow at any bend - lovish, Oct 16: "the elbows don't match human
    anatomy"), the forearm's sleeve flaring into a six-plate bell with a gold hem at the wrist."""
    pre = "left" if side > 0 else "right"
    sx, sy = side * SHOULDER_X, SHOULDER_Y
    elbow_y, wrist_y = sy - UPPER, sy - UPPER - FORE
    up = m.bone(pre + "_arm", [sx, sy, 0], parent=parent)
    # (an exact octagon: as a hexagon a lit and a dark face met in an edge down the back - from behind, a blade)
    octagon_pole(up, sx, 0, elbow_y + 0.05, sy - 0.5, SLEEVE_W, "robe")
    fo = m.bone(pre + "_fore", [sx, elbow_y, 0], parent=pre + "_arm")
    # (0.25 wider than the sleeve: its corners stood out of a joint as wide as it behind the bent elbow; 0.25 shorter,
    # its ends inside the sleeves' sides: flush with them, the flat octagons read as a hinge's drum)
    octagon_axle(fo, (sx, elbow_y, 0.0), SLEEVE_W - 0.25, SLEEVE_W + 0.25, "robe")
    # the bell's mouth a little short of the wrist, the glove's cuff showing under it (at the wrist itself, its gold
    # rim cut the staff's shaft 0.7 px beside the fist when the staff turned in the hand - the walk, the cast)
    bell_top, bell_bot = elbow_y - 0.3, wrist_y + BELL_MOUTH
    hem = bell_bot + 0.5
    cone(fo, sx, 0, FORE_R, FORE_R, hem, BELL_FLARE, bell_top, 1.0, 6, 0.3, "robe", exact_at=hem, phase=0.5)
    cone(fo, sx, 0, FORE_R, FORE_R, bell_bot, BELL_FLARE + 0.05, hem, BELL_FLARE, 6, 0.3, "gold", exact_at=bell_bot,
         phase=0.5)
    return pre + "_arm", pre + "_fore", (sx, wrist_y + 0.55, 0)


GLOVE = dict(finger_w=0.36, finger_t=0.36, palm_t=0.48, palm_len=1.55, cuff_len=0.7, lames=0, knuckles=False,
             accent="glove_dark", heel="glove_dark", cuff="glove_dark")
STAFF_R = 0.5                 # the grip's radius: the shaft is 0.9 across its flats, the fingers 0.05 off it


def staff(m, gx, gz, grip_y):
    """THE prop: a staff half again her height - a turned shaft (one octagonal piece), a gold ferrule and band, a
    gilded neck and cup rising into a twelve-sided ring that holds the floating sun disc (its own bone) with rays round
    it; team ribbons tied under the neck."""
    st = m.bone("staff", [gx, grip_y, gz], parent="staff_arm")
    top = 38.0
    octagon_pole(st, gx, gz, 0.7, top - 0.6, 0.9, "staff_wood")
    ngon(st, gx, gz, -0.2, 1.1, 1.1, "gold", n=6, phase=0)                                         # ferrule
    ngon(st, gx, gz, 29.6, 30.2, 1.16, "gold", n=6, phase=0)                                       # a band
    ngon(st, gx, gz, top - 1.2, top + 0.9, 1.25, "gold", n=6, phase=0)                             # the neck
    c = (gx, top + 5.6, gz)
    xy_ring(st, c, 4.25, 0.72, 0.9, "gold", n=12, phase=15)                                       # the ring
    for k in range(12):                                                                            # rays
        a = k * math.pi / 6
        L = 1.9 if k % 2 == 0 else 1.05
        r = 4.25 + 0.2 + L / 2
        cx, cy = c[0] + r * math.cos(a), c[1] + r * math.sin(a)
        st.box_at((cx, cy, c[2]), (L, 0.48, 0.48), "sun", rotation=[0, 0, math.degrees(a)], pivot=[cx, cy, c[2]])
    # the cradle: two prongs from the cup up into the ring
    for s in (1, -1):
        p0, p1 = (gx + s * 0.55, top + 0.9, gz), (gx + s * 1.75, top + 2.0, gz)
        slab(st, tuple((p0[a] + p1[a]) / 2 for a in range(3)), tuple(p1[a] - p0[a] for a in range(3)), (0, 0, 1),
             (0.62, 0.62, math.dist(p0, p1) + 0.5), "gold")
    st.box_at((gx, top + 5.6 + 4.25 + 0.3, gz), (0.8, 0.8, 0.8), "gold", rotation=[0, 0, 45],
              pivot=[gx, top + 5.6 + 4.25 + 0.3, gz])                                              # the finial
    # two ribbons tied under the neck, team colour: the one prop carries the side too, and they flutter
    rb = m.bone("staff_ribbon", [gx, top - 1.4, gz], parent="staff")
    m.contact_ok.append(("staff", "staff_ribbon"))  # tied round the staff
    rb.box_at((gx, top - 1.65, gz), (1.3, 0.7, 1.3), "tabard_dark")                                # the knot
    for k, (dx, L, tilt) in enumerate(((0.25, 7.0, 10), (-0.55, 5.6, 22))):
        rb.cube([gx + dx - 0.4, top - 1.9 - L, gz + 0.45], [0.8, L, 0.2], "tabard", rotation=[-tilt, 0, 6 - k * 14],
                pivot=[gx + dx, top - 1.9, gz + 0.5])
    # the disc: an exact 12-gon of sunlight (eight stacked rows before: a staircase round its rim), the bright core
    # standing proud of it, both in the ring's plane
    disc = m.bone("sun_disc", list(c), parent="staff")
    xy_ngon(disc, c, 6.4, 0.7, "sun", n=12, phase=15)
    xy_ngon(disc, c, 2.7, 1.3, "glow", n=6, phase=0)
    return st


def potion_hand(m, lf, wrist_local):
    """The left glove closed round the neck of a potion she carries to throw (WitchUnit.throwPotion): the bottle
    upright in the posed model, its round body under the fist, the cork over it. Built in the forearm's frame along the
    world's up as it stands at rest."""
    from gen_models import transpose, apply
    up_local = apply(transpose(m.frame(lf)), (0.0, 1.0, 0.0))
    fo = m.bones[lf]
    g = grip_hand(fo, None, up_local, 0.3, 1, "glove", back=(0.0, 1.0, 0.0), wrist=wrist_local, **GLOVE)
    a, c = g["axis"], g["centre"]
    axis_ngon(fo, _add(c, _mul(a, -1.25)), a, 1.1, 1.0, "potion_red", n=8)                        # the body
    slab(fo, _add(c, _mul(a, -0.15)), a, g["u"], (0.5, 0.5, 1.9), "potion_red")                     # the neck
    slab(fo, _add(c, _mul(a, 0.95)), a, g["u"], (0.6, 0.6, 0.4), "leather_dark")                    # the cork
    return g


# ------------------------------------------------------------------ legs -------------------------------------------
def legs(m):
    """Mostly under the robe: slim legs in dark hose, soft ankle boots (hexagons), the feet level and turned out. The
    leg tapers from the thigh to the ankle (2.1 -> 1.7 -> 1.45; it was a 1.7 tube over a 2.0 boot: wider at the foot),
    and the thigh rises 0.5 px over its pivot into the hip, so its corners stay in the skirt in the stride."""
    m.contact_ok += [("body", n) for n in ("left_thigh", "right_thigh", "left_shin", "right_shin")]   # in the skirt
    feet = []
    for side, thigh, shin in ((1, [4, 0, 0], [-6, 0, 0]), (-1, [-3, 0, 0], [-4, 0, 0])):
        pre = "left" if side > 0 else "right"
        hx = side * 1.5
        th = m.bone(pre + "_thigh", [hx, HIP, 0], rotation=thigh)
        y0, y1 = KNEE - 0.2, HIP + 0.5
        th.box_at((hx, (y0 + y1) / 2, 0), (THIGH_W, y1 - y0, THIGH_D), "robe_dark")
        sh = m.bone(pre + "_shin", [hx, KNEE, 0], parent=pre + "_thigh", rotation=shin)
        y0, y1 = BOOT_TOP - 0.3, KNEE + 0.3
        sh.box_at((hx, (y0 + y1) / 2, 0), (CALF_W, y1 - y0, CALF_D), "robe_dark")
        ngon(sh, hx, 0, 1.1, BOOT_TOP, BOOT_W, "boot", n=6, phase=0)                                 # boot shaft
        # (2.1 x 4.6, a heel 1.3 behind the ankle: .082 x .171 of her stature - the widest, longest feet of the line)
        feet.append(foot(m, pre, hx, 0.0, 1.75, 4.2, 1.1, "boot", "leather_dark"))
    return feet


# ------------------------------------------------------------------ the whole ----------------------------------------
def medic():
    m = Model("Field Medic - Dawn Sister (v2)", "sunforged", "support (T1, through the Paladin pipeline)", PALETTE,
              EMISSIVE)
    body, belt = torso(m)
    belt_kit(body, belt)
    mantle(body)
    robe_panels(m)
    satchel(body)
    hood(m)
    feet = legs(m)
    # the staff and the right arm on one rig at the right shoulder; the left arm carries a potion
    m.bone("staff_arm", [-SHOULDER_X, SHOULDER_Y, 0], parent="body")
    ra, rf, rw = sleeve_arm(m, -1, "staff_arm")
    la, lf, lw = sleeve_arm(m, 1, "body")
    # the grip at the hip, a little forward and out: 2.2 px outside the shoulder joint (it moved in with the shoulders)
    gx, gz, grip_y = -(SHOULDER_X + 2.2), -2.2, 15.4
    st = staff(m, gx, gz, grip_y)
    # the glove closed round the grip and the arm solved onto its cuff (gen_halberdier's loop): the hand's frame follows
    # the forearm, so solve, read the forearm's direction, rebuild the hand along it, solve again
    C = (gx, grip_y, gz)
    ELBOW_AT = (-(SHOULDER_X + 1.3), 18.3, 1.9)    # a hand on a staff close before the hip: the elbow down, drawn back
    back = _unit((0.3, 0.5, 1.0))
    e1 = 0.0
    for _ in range(4):
        used = back
        g = grip_hand(Bone(m, "_scratch", [0, 0, 0]), C, (0, 1, 0), STAFF_R, -1, "glove", back=used, **GLOVE)
        e1 = m.solve_arm(ra, rf, rw, g["wrist"], elbow_out=1, elbow_down=1, point_at=g["aim"], avoid=("body",),
                         avoid_margin=1.0, avoid_elbow=True, elbow_at=ELBOW_AT)
        el, wr = m.fk(rf, m.bones[rf].pivot), m.fk(rf, rw)
        back = _unit(tuple(0.5 * used[k] + 0.5 * (el[k] - wr[k]) / math.dist(el, wr) for k in range(3)))
    grip_hand(st, C, (0, 1, 0), STAFF_R, -1, "glove", back=used, **GLOVE)
    # the staff turns at the WRIST: its pivot the forearm's end, where the glove's cuff meets the sleeve (about the
    # grip's centre - the fist's middle - the cast's 58 degrees swung the cuff ~1.5 px out of the sleeve's mouth)
    st.pivot = [round(v, 3) for v in m.fk(rf, rw)]
    # the left arm: the elbow by her side, the forearm forward, the potion carried before the hip
    m.bones[la].rotation = [6, 0, 6]       # (a little out from her side: the waist shows between)
    m.bones[lf].rotation = [52, 0, 0]
    potion_hand(m, lf, (SHOULDER_X, lw[1], 0.0))
    for ft, shin in feet:
        m.ground_leg(ft, also=(shin,))
    m.pose = {"head": [-5, 0, 0]}
    return m, e1


MODELS = {"sunforged_field_medic_v2": medic}


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
