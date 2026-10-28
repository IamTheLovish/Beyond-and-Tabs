#!/usr/bin/env python3
"""The shared human figure for the Sunforged men who wore the villager body until Oct 21 (lovish, 21:20: "every human
unit moves off the Minecraft villager body onto the realistic human body - no villager faces or big noses anywhere"):
the Dome Cleric (gen_cleric.py), the Royal Architect (gen_architect.py), the Field Mason (gen_mason.py) and the Dawn
Paladin (gen_dawn_paladin.py). One body under four costumes, so they stand beside the Levy, the Squire, the Medic and
the Halberdier as one faction:

  - the FIGURE is the Crossbow Levy's line-soldier canon (tools/gen_levy.py, the Oct 16 anatomy pass): the hip joints
    at .52 and the knees at .28 of a 29.2 px stature, the shoulder joints 7.2 px apart at .80, the elbow at 55% of the
    arm, the head 16.5% of him - so gen_levy's legs and boots (turned hose, four-leather ankle boots) fit unchanged;
  - a BARE head (bare_head): the neck, a turned skull, the ears, hair round the back and sides and a face of facets
    (brow ridge over shaded sockets, the nose's bridge and tip, cheekbones, lips, a jaw) - a man's face at the line's
    scale, no villager nose. Hats and helms are built on it by each unit, then the whole head is shrunk as one
    (finish_head: HEAD_S, gen_levy.head's construction);
  - arms (cloth_arm, bell_arm): a turned sleeve, the elbow a joint round its hinge (gen_models.octagon_axle), a
    forearm sleeve or bracer, a cuff; hands closed round what they carry (gen_models.grip_hand) and the arm solved
    onto the hand (hold(): gen_medic's loop - solve, read the forearm, rebuild the hand along it, solve again);
  - a long skirt (skirt_panels): ONE flared cone of planar plates (gen_medic.cone_plates) shared out over four panel
    bones that sway and part for the legs (<prefix>_front / _back / _left / _right) - what tools/human_base.py's
    cloth keys drive.

Rig (tools/human_base.py's convention): "body" at the hips (the trunk), the thighs ROOT bones at the hip joints (the
gait's IK), shins, level feet, "head" on the body, <side>_arm / <side>_fore.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_models import Bone, slab, grip_hand, octagon_axle, lathe, _unit, _add, _mul  # noqa: E402
from gen_levy import ngon, oct_pole, plate, shrink  # noqa: E402
import gen_levy  # noqa: E402
from gen_medic import cone_plates, put_plate, on_plate  # noqa: E402

# ------------------------------------------------------------------ the figure (gen_levy's canon) ------------------
HIP, KNEE = gen_levy.HIP, gen_levy.KNEE                       # 15.2, 8.2
SHOULDER_X, SHOULDER_Y = gen_levy.SHOULDER_X, gen_levy.SHOULDER_Y   # 3.6, 23.4
UPPER, FORE = gen_levy.UPPER, gen_levy.FORE                   # 5.25, 4.25
STATURE = 29.24                                               # sole to the crown (under a hat)
HEAD_S = 0.86                                                 # the head drawn at its own numbers, shrunk as one
HEAD_Y = 23.96                                                # the neck (the head's pivot): gen_levy.HEAD_Y
TRUNK = gen_levy.TRUNK                                        # (y0, y1, width, z front, z back, chamfer) bands
BELT = gen_levy.BELT

# the colours every body here shares (each unit adds its own): skin, hair, the boots' four leathers (gen_levy.boot)
BASE_PALETTE = {
    "skin": "#c69273", "skin_shade": "#8f6149", "socket": "#7c523a", "eye": "#2a1d17", "lip": "#a8685a",
    "boot": "#664631", "boot_sole": "#2f2119", "boot_worn": "#84603f", "boot_turn": "#8f6e4e",
    "leather_dark": "#4a3220", "steel_dark": "#7c828d", "wood_dark": "#5e4129",
    "gold": "#d4a63c", "gold_dark": "#a87f24", "sun": "#ffd96a",
}


# ------------------------------------------------------------------ the head ----------------------------------------
def face(h, hy, beard="beard", moustache=True, beard_len=1.0, fz=-2.56, brow="skin"):
    """A man's face as facets, front to back (gen_levy.face's construction, at the bare head's depth): the centre
    plate, the cheeks turned back toward the temples, the brow ridge overhanging two shaded sockets with the eye in
    each, cheekbones, the nose (bridge and tip), the lips, and a jaw - bearded (beard: the colour; beard_len 0.5 a
    short trimmed beard, 1.0 a full one) or shaven (beard=None: the jaw and chin in skin). Every cube under 1.9 px
    tall (tools/dsl_to_game.py paints a lit rim and a seam round taller ones - stripes on a face)."""
    yb, yt = hy + 1.65, hy + 3.45
    h.box_at((0, (yb + yt) / 2, fz + 0.36), (1.9, yt - yb, 0.72), "skin")                         # the centre
    jaw = beard or "skin"
    for s in (1, -1):
        plate(h, (s * 0.96, fz + 0.04), (s * 1.42, fz + 0.48), yb, yt, 0.72, "skin", ext=0.1)     # the cheeks
        plate(h, (s * 0.6, fz - 0.3 * beard_len), (s * 1.3, fz + 0.42), hy + 0.55 + 0.35 * (1 - beard_len),
              yb + 0.1, 0.72, jaw, ext=0.1)                                                       # the jaw
        plate(h, (s * 0.12, fz - 0.28), (s * 1.15, fz + 0.12), hy + 2.82, hy + 3.22, 0.4, brow, ext=0.06)  # brow
        plate(h, (s * 0.4, fz - 0.12), (s * 1.2, fz + 0.2), hy + 2.05, hy + 2.4, 0.35, "skin", ext=0.04)   # cheekbone
        h.box_at((s * 0.62, hy + 2.62, fz - 0.03), (0.62, 0.38, 0.1), "socket")                   # the socket
        h.box_at((s * 0.6, hy + 2.6, fz - 0.06), (0.36, 0.15, 0.1), "eye")                        # the eye
    h.box_at((0, hy + 2.6, fz - 0.24), (0.44, 1.0, 0.36), "skin", rotation=[13, 0, 0], pivot=[0, hy + 2.6, fz - 0.24])
    h.box_at((0, hy + 2.02, fz - 0.24), (0.7, 0.42, 0.5), "skin")                                  # the nose's tip
    if beard and moustache:
        h.box_at((0, hy + 1.66, fz - 0.14), (1.36, 0.3, 0.34), beard)                             # moustache
    else:
        h.box_at((0, hy + 1.6, fz - 0.08), (0.8, 0.14, 0.2), "lip")                               # the mouth
    if beard:
        d = 0.7 * beard_len
        h.box_at((0, hy + 0.55 + 0.5 * (1 - beard_len) + 0.55 * beard_len, fz - 0.01 + 0.35 * (1 - beard_len)),
                 (1.3, 1.1 * beard_len, max(0.6, d)), beard)                                      # the beard
        if beard_len > 0.7:
            h.box_at((0, hy + 0.62, fz - 0.24), (0.78, 0.6, 0.4), beard)                          # its point
    else:
        h.box_at((0, hy + 0.98, fz + 0.1), (1.0, 0.62, 0.6), "skin")                              # the chin


SKULL = dict(A=1.98, B=2.3, cz=-0.1, n=12)           # the cranium's ellipse (half-width, half-depth) and its facets
# its profile: (height over the neck, scale) - the temples under the ears widening to the parietal, the crown turning in
SKULL_PROFILE = ((1.85, 0.92), (2.4, 0.98), (3.6, 1.0), (4.55, 0.86), (5.15, 0.6), (5.45, 0.3))
JAW = dict(A=1.72, B=2.02, cz=-0.32, y0=0.72, y1=1.9, s0=0.74)   # the jaw under it: narrower, its front forward


def skull_plates(hy, grow=0.0, y_from=None, y_to=None, t=0.5):
    """The cranium's plates band by band (gen_medic.cone_plates on SKULL's ellipse through SKULL_PROFILE); grow
    widens it (the hair over it). Each spec carries "band" (its index) and "th" (degrees from the front, + to the
    left)."""
    out = []
    A, B = SKULL["A"] + grow, SKULL["B"] + grow
    for i, ((ya, sa), (yb, sb)) in enumerate(zip(SKULL_PROFILE, SKULL_PROFILE[1:])):
        y0, y1 = hy + ya, hy + yb
        if y_from is not None and y1 <= hy + y_from + 1e-6:
            continue
        if y_to is not None and y0 >= hy + y_to - 1e-6:
            continue
        ps = cone_plates(0, SKULL["cz"], A, B, y0, sa, y1, sb, SKULL["n"], t, exact_at=y0 if sa > sb else y1)
        for p in ps:
            p["band"] = i
        out += ps
    return out


def bare_head(m, hair="hair", beard="beard", moustache=True, beard_len=1.0, parent="body", hair_top=True,
              hair_line=4.05):
    """The head at its own numbers about the neck (HEAD_Y), as gen_levy.head draws it before the shrink: the neck, a
    cranium TURNED round an ellipse narrower than it is deep (skull_plates: a head is ~0.85 as wide as it is long - the
    octagonal coif shape on a bare head read as a box), the face of facets on its front, the ears, the hair round the
    back and the sides and over the crown (unless a hat covers it: hair_top) down to a hairline on the brow. Returns
    the bone; the unit adds its hat at the same numbers, then finish_head() shrinks it."""
    hy = HEAD_Y
    h = m.bone("head", [0, hy, 0], parent=parent)
    ngon(h, 0, 0.15, hy - 0.9, hy + 1.5, 2.15, "skin", n=8)                                       # the neck
    for p in skull_plates(hy):
        put_plate(h, p, "skin")
    for p in cone_plates(0, JAW["cz"], JAW["A"], JAW["B"], hy + JAW["y0"], JAW["s0"], hy + JAW["y1"], 1.0, 10, 0.5,
                         exact_at=hy + JAW["y1"]):
        th = (p["th"] + 180) % 360 - 180
        put_plate(h, p, beard if (beard and abs(th) < 100 and beard_len > 0.4) else hair if abs(th) >= 125 else "skin")
    face(h, hy, beard=beard, moustache=moustache, beard_len=beard_len, fz=-2.46)
    for s in (1, -1):                                                                              # the ears
        h.box_at((s * 1.93, hy + 2.6, 0.2), (0.34, 1.15, 0.78), "skin", rotation=[0, -s * 14, 0],
                 pivot=[s * 1.93, hy + 2.6, 0.2])
    # the hair: a cap of plates over the cranium - round the back from the nape, the sides from over the ears, over the
    # top from the hairline on the brow
    for p in skull_plates(hy, grow=0.14, y_from=1.6, t=0.34):
        th = abs((p["th"] + 180) % 360 - 180)
        top_band = p["band"] >= 3
        if not hair_top and top_band:
            continue
        if th >= 125 or (p["band"] >= 2 and th >= 45) or (p["band"] >= 2 and hair_line < 3.7) or top_band:
            put_plate(h, p, hair)
    if hair_top:
        ngon(h, 0, SKULL["cz"], hy + 5.3, hy + 5.72, 1.6, hair, n=12, phase=15)
    return h


def finish_head(h):
    """The head shrunk as one by HEAD_S about the neck (gen_levy.head): its hat's top lands on the stature."""
    shrink(h, (0, HEAD_Y, 0), HEAD_S)
    return h


# ------------------------------------------------------------------ the trunk ---------------------------------------
def trunk(b, colour, bands=TRUNK, quilt=None):
    """The trunk hip to neck in gen_levy's five bands (the skirt over the seat, the pinched waist, the ribs, the chest
    standing forward of them, the shoulders narrower than the chest), optionally quilted down the sides."""
    from gen_paladin import octagon
    for (y0, y1, w, zf, zb, ch) in bands:
        octagon(b, y0, y1, w, zb - zf, ch, colour, zf=zf)
    if quilt:
        for band, zs in ((bands[3], (-1.1, 0.2)), (bands[0], (-0.85, 0.55))):
            y0, y1, w = band[0], band[1], band[2]
            for s in (1, -1):
                for zc in zs:
                    b.box_at((s * w / 2, (y0 + y1) / 2, zc), (0.12, y1 - y0 - 0.2, 0.13), quilt)


def skirt_panels(m, prefix, top, hem, A, B, A_hem, colour, hem_colour=None, hem_h=0.6, cz=-0.15, n=12, t=0.32,
                 hinge=None, parent="body"):
    """A flared skirt (a robe, a coat's skirts) as ONE cone of n planar plates from `top` to `hem` (the ellipse A x B at
    the top, A_hem across at the hem), shared over four panel bones hinged at `hinge` that sway and part for the legs
    (gen_medic.robe_panels' construction); a hem band of hem_colour flush below. Returns ({name: bone}, plates, hems):
    the plate specs for laying trim on them (gen_medic.on_plate)."""
    hinge = top - 0.2 if hinge is None else hinge
    s_hem = A_hem / A

    def scale(y):
        return 1.0 + (s_hem - 1.0) * (top - y) / (top - hem)
    zf, zb = cz - B, cz + B
    pivots = {"_front": [0, hinge, zf + 0.3], "_back": [0, hinge, zb - 0.3], "_left": [A - 0.4, hinge, cz],
              "_right": [-(A - 0.4), hinge, cz]}
    bones = {prefix + k: m.bone(prefix + k, pv, parent=parent) for k, pv in pivots.items()}

    def owner(th):
        th = th % 360
        if th < 45 or th >= 315:
            return prefix + "_front"
        if th < 135:
            return prefix + "_left"
        if th < 225:
            return prefix + "_back"
        return prefix + "_right"
    y_band = hem + (hem_h if hem_colour else 0.0)
    body = cone_plates(0, cz, A, B, y_band, scale(y_band), top, 1.0, n, t, exact_at=y_band)
    hems = cone_plates(0, cz, A, B, hem, scale(hem), y_band, scale(y_band), n, t, exact_at=hem) if hem_colour else []
    for i, p in enumerate(body):
        bo = bones[owner(p["th"])]
        put_plate(bo, p, colour)
        if hem_colour:
            put_plate(bo, hems[i], hem_colour)
    return bones, body, hems


def owner_of(prefix, th):
    th = th % 360
    if th < 45 or th >= 315:
        return prefix + "_front"
    if th < 135:
        return prefix + "_left"
    if th < 225:
        return prefix + "_back"
    return prefix + "_right"


# ------------------------------------------------------------------ arms and hands ---------------------------------
def cloth_arm(m, side, parent, sleeve, fore=None, bracer=None, cuff=None, cap=None, cap_hem="gold", elbow=None,
              sleeve_w=1.95, cap_w=2.6):
    """gen_levy.arm's construction in any cloth: a hexagonal sleeve to the elbow, optionally a deltoid cap (cap: its
    colour, falling 22 degrees off the shoulder, a hem round its rim), the elbow a turned joint round its hinge, the
    forearm's sleeve (fore, default the sleeve's), a bracer (or none) and a cuff band at the wrist. Returns (upper,
    fore, wrist point)."""
    pre = "left" if side > 0 else "right"
    sx, sy = side * SHOULDER_X, SHOULDER_Y
    elbow_y, wrist_y = sy - UPPER, sy - UPPER - FORE
    fore = fore or sleeve
    up = m.bone(pre + "_arm", [sx, sy, 0], parent=parent)
    ngon(up, sx, 0, elbow_y + 0.15, sy - (1.2 if cap else 0.55), sleeve_w, sleeve, n=6, phase=30)
    if cap:
        tilt, top, bot, w, out = 22.0, 0.0, 1.6, cap_w, 0.35
        t = math.radians(tilt)
        H = top + bot
        cx, cy = sx + side * out, sy + (top - bot) / 2
        axis = (side * math.sin(t), math.cos(t))
        oct_pole(up, cx, 0, cy - H / 2, cy + H / 2, w, cap, tilt=-side * tilt)
        if cap_hem:
            hc = (cx - axis[0] * (H / 2 + 0.14), cy - axis[1] * (H / 2 + 0.14))
            oct_pole(up, hc[0], 0, hc[1] - 0.2, hc[1] + 0.2, w + 0.12, cap_hem, tilt=-side * tilt)
    fo = m.bone(pre + "_fore", [sx, elbow_y, 0], parent=pre + "_arm")
    octagon_axle(fo, (sx, elbow_y, 0.0), sleeve_w - 0.09, sleeve_w + 0.03, elbow or sleeve)
    if bracer:
        from gen_squire import oct_at
        oct_at(fo, sx, 0, elbow_y - 1.6, elbow_y - 0.15, 1.7, 1.85, 0.45, fore)
        ngon(fo, sx, 0, wrist_y, elbow_y - 1.4, 1.75, bracer, n=6, phase=30)
    else:
        ngon(fo, sx, 0, wrist_y + (0.45 if cuff else 0.0), elbow_y - 0.15, 1.72, fore, n=6, phase=30)
        if cuff:
            ngon(fo, sx, 0, wrist_y, wrist_y + 0.5, 1.82, cuff, n=6, phase=30)
    return pre + "_arm", pre + "_fore", (sx, wrist_y + 0.2, 0)


BELL_FLARE = 1.42


def bell_arm(m, side, parent, sleeve, hem="gold", sleeve_w=1.95, fore_r=0.92):
    """A robed arm (gen_medic.sleeve_arm at the man's numbers): a turned sleeve to the elbow, the joint round its
    hinge, the forearm's sleeve flaring into a six-plate bell with a hem at the wrist."""
    from gen_medic import cone
    from gen_models import octagon_pole
    pre = "left" if side > 0 else "right"
    sx, sy = side * SHOULDER_X, SHOULDER_Y
    elbow_y, wrist_y = sy - UPPER, sy - UPPER - FORE
    up = m.bone(pre + "_arm", [sx, sy, 0], parent=parent)
    octagon_pole(up, sx, 0, elbow_y + 0.05, sy - 0.5, sleeve_w, sleeve)
    fo = m.bone(pre + "_fore", [sx, elbow_y, 0], parent=pre + "_arm")
    octagon_axle(fo, (sx, elbow_y, 0.0), sleeve_w - 0.25, sleeve_w + 0.25, sleeve)
    bell_top, bell_bot = elbow_y - 0.3, wrist_y + 0.35
    hem_y = bell_bot + 0.5
    cone(fo, sx, 0, fore_r, fore_r, hem_y, BELL_FLARE, bell_top, 1.0, 6, 0.3, sleeve, exact_at=hem_y, phase=0.5)
    if hem:
        cone(fo, sx, 0, fore_r, fore_r, bell_bot, BELL_FLARE + 0.05, hem_y, BELL_FLARE, 6, 0.3, hem, exact_at=bell_bot,
             phase=0.5)
    return pre + "_arm", pre + "_fore", (sx, wrist_y + 0.55, 0)


def glove(colour="skin", accent="skin_shade", cuff=None, w=0.36):
    """grip_hand's sizes for a bare hand or a soft glove at the line's scale (the Levy's)."""
    return dict(finger_w=w, finger_t=w, palm_t=0.5, palm_len=1.6, cuff_len=0.55, lames=0, knuckles=False,
                accent=accent, heel=accent, cuff=cuff or accent)


def free_hand(m, fore, side, wrist, colour="skin", accent="skin_shade", cuff=None, curl=0.42):
    """A hand hanging easy from its forearm: half open round a thin imaginary rod, palm to the thigh, thumb forward
    (gen_halberdier.arm's free hand)."""
    g = glove(colour, accent, cuff)
    return grip_hand(m.bones[fore], None, (0, 0, -1), 0.5, side, colour, back=(0, 1, 0), curl=curl, wrist=wrist, **g)


def hold(m, upper, fore, wrist_rest, prop_bone, centre, axis, r, side, elbow_at, back, colour="skin",
         accent="skin_shade", cuff=None, avoid=("body",), margin=1.0, on_fore=False):
    """The hand closed round a prop (centre, axis, radius r - world, rest pose) and the arm solved onto the hand's cuff
    (gen_medic's loop: the hand's frame follows the forearm, so solve, read the forearm's direction, rebuild the hand
    along it, solve again). The hand is built on prop_bone (it rides the prop), or on the forearm (on_fore: the prop
    then rides the hand - built in the forearm's posed frame). Returns the wrist error (px)."""
    from gen_models import apply, transpose
    g_kw = glove(colour, accent, cuff)
    e = 0.0
    used = _unit(back)
    for _ in range(4):
        used = back
        g = grip_hand(Bone(m, "_scratch", [0, 0, 0]), centre, axis, r, side, colour, back=used, **g_kw)
        e = m.solve_arm(upper, fore, wrist_rest, g["wrist"], elbow_out=1, elbow_down=1, point_at=g["aim"],
                        avoid=avoid, avoid_margin=margin, avoid_elbow=True, elbow_at=elbow_at)
        el, wr = m.fk(fore, m.bones[fore].pivot), m.fk(fore, wrist_rest)
        back = _unit(tuple(0.5 * used[k] + 0.5 * (el[k] - wr[k]) / math.dist(el, wr) for k in range(3)))
    m.bones.pop("_scratch", None)
    if on_fore:
        Rm = m.frame(fore)
        t0 = m.fk(fore, (0.0, 0.0, 0.0))
        c_loc = apply(transpose(Rm), tuple(centre[k] - t0[k] for k in range(3)))
        grip_hand(m.bones[fore], c_loc, apply(transpose(Rm), axis), r, side, colour,
                  back=apply(transpose(Rm), used), **g_kw)
    else:
        grip_hand(m.bones[prop_bone], centre, axis, r, side, colour, back=used, **g_kw)
    return e


def legs(m):
    """gen_levy's legs at the same figure: the turned thigh ("cloth_dark": the unit's palette says what it is), the
    hose ("hose"), the boot's turned-down cuff and the four-leather ankle boot. Returns [(foot, shin)]."""
    return gen_levy.legs(m)


def attach(m, bone, parent, ref, turn=None):
    """A prop built at its place in the WORLD (the posed rest model: upright, where the hand holds it) handed to a
    posed parent bone (a forearm): its cubes are moved into the parent's rest space and the bone turned by the inverse
    of the parent's posed frame about `ref` (a world point on the prop, its grip), so it is drawn exactly where it was
    built and then rides the parent - a hand's prop follows the arm in every key. turn: (x, y, z) DSL degrees the prop
    is turned by about `ref` in the world as well (a shield built facing ahead, hung on the arm's outside)."""
    from gen_models import apply, transpose, euler
    R = m.frame(parent)
    t0 = m.fk(parent, (0.0, 0.0, 0.0))
    loc = apply(transpose(R), tuple(ref[k] - t0[k] for k in range(3)))
    d = tuple(loc[k] - ref[k] for k in range(3))
    for c in bone.cubes:
        c["from"] = [round(c["from"][a] + d[a], 4) for a in range(3)]
        if "pivot" in c:
            c["pivot"] = [round(c["pivot"][a] + d[a], 4) for a in range(3)]
    bone.parent = parent
    bone.pivot = [round(v, 4) for v in loc]
    from gen_models import rot_mat, mat_mul
    W = transpose(R) if turn is None else mat_mul(transpose(R), rot_mat(*turn))
    bone.rotation = [round(v, 3) for v in euler(W)]
    return bone


def axis_pole(bone, a, b, w, colour, n=8, **kw):
    """A turned rod from point a to point b (an exact n-gon w across the flats): a rolled plan, a haft at any angle."""
    from gen_medic import axis_ngon
    c = tuple((a[k] + b[k]) / 2 for k in range(3))
    axis_ngon(bone, c, tuple(b[k] - a[k] for k in range(3)), w, math.dist(a, b), colour, n=n, **kw)
