#!/usr/bin/env python3
"""Generates concept models (design/models/*.json, DSL: design/models/README.md) from code.

Why a generator: the UNIT BAR (claude/art-direction.md) asks for real anatomy, layered plates and one signature
part per unit - 60-120 cubes, many of them angled. Typing that by hand is where models turn into "a villager with
a hat". Here a body is a few dozen lines: fanned pauldron lames, halo rings, tapered blades and flared faulds are
helpers, arms are solved so the hands land on the weapon (forward kinematics, the same maths as the renderer), and
the feet are stood on the ground by computation rather than by eye.

    python3 tools/gen_models.py            # writes the generated models into design/models/
    python3 tools/render_concept.py --check design/models/sunforged_paladin_*.json

Space and rotations are the DSL's (y up, unit faces -Z, +x = the unit's left, rotation x then y then z).
Every rotated cube maps to a one-cube child ModelPart in the game, so nothing here is beyond the engine.
"""
import json
import math
import os
import sys

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "design", "models")


# ------------------------------------------------------------------ maths (mirrors render_concept.py) ---------
def rot_mat(rx, ry, rz):
    """Minecraft rotation (degrees): x first, then y, then z; right-handed about +x, +y, +z."""
    cx, sx = math.cos(math.radians(rx)), math.sin(math.radians(rx))
    cy, sy = math.cos(math.radians(ry)), math.sin(math.radians(ry))
    cz, sz = math.cos(math.radians(rz)), math.sin(math.radians(rz))
    Rx = ((1, 0, 0), (0, cx, -sx), (0, sx, cx))
    Ry = ((cy, 0, sy), (0, 1, 0), (-sy, 0, cy))
    Rz = ((cz, -sz, 0), (sz, cz, 0), (0, 0, 1))

    def mul(A, B):
        return tuple(tuple(sum(A[i][k] * B[k][j] for k in range(3)) for j in range(3)) for i in range(3))
    return mul(Rz, mul(Ry, Rx))


def apply(M, v):
    return tuple(sum(M[i][k] * v[k] for k in range(3)) for i in range(3))


def rotate_about(pivot, rot, p):
    if not any(rot):
        return tuple(p)
    M = rot_mat(*rot)
    d = apply(M, (p[0] - pivot[0], p[1] - pivot[1], p[2] - pivot[2]))
    return (pivot[0] + d[0], pivot[1] + d[1], pivot[2] + d[2])


WRIST_FREE = 20.0      # degrees a wrist may bend before solve_arm's point_at starts to cost


def mat_mul(A, B):
    return tuple(tuple(sum(A[i][k] * B[k][j] for k in range(3)) for j in range(3)) for i in range(3))


def transpose(A):
    return tuple(tuple(A[j][i] for j in range(3)) for i in range(3))


def euler(R):
    """The DSL rotation (degrees, x then y then z) of a rotation matrix: rot_mat's inverse (away from gimbal lock)."""
    ry = math.asin(max(-1.0, min(1.0, -R[2][0])))
    rx = math.atan2(R[2][1], R[2][2])
    rz = math.atan2(R[1][0], R[0][0])
    return [round(math.degrees(rx), 3), round(math.degrees(ry), 3), round(math.degrees(rz), 3)]


# ------------------------------------------------------------------ model ---------------------------------------
class Bone:
    def __init__(self, model, name, pivot, parent=None, rotation=(0, 0, 0), upgrade=None):
        self.model, self.name, self.pivot, self.parent = model, name, list(pivot), parent
        self.rotation, self.upgrade, self.cubes = list(rotation), upgrade, []

    def cube(self, frm, size, color, inflate=0.0, rotation=None, pivot=None, upgrade=None):
        c = {"from": [round(v, 3) for v in frm], "size": [round(v, 3) for v in size], "color": color}
        if inflate:
            c["inflate"] = round(inflate, 3)
        if rotation and any(rotation):
            c["rotation"] = [round(v, 3) for v in rotation]
            if pivot is not None:
                c["pivot"] = [round(v, 3) for v in pivot]
        if upgrade:
            c["upgrade"] = upgrade
        self.cubes.append(c)
        return c

    # a box given by its centre (handy for rings and rays)
    def box_at(self, centre, size, color, **kw):
        return self.cube([centre[a] - size[a] / 2 for a in range(3)], size, color, **kw)


class Model:
    def __init__(self, name, faction, role, palette, emissive):
        self.name, self.faction, self.role = name, faction, role
        self.palette, self.emissive = palette, list(emissive)
        self.limb_ok = []          # bones whose pose deliberately breaks a tools/limb_check.py rule (e.g. a raised arm)
        self.contact_ok = []       # bone pairs meant to interpenetrate (tools/contact_check.py): a grip in a gauntlet
        self.flat_light = []       # bone name prefixes painted WITHOUT their own baked occlusion (a coat of tiles)
        self.bones, self.pose = {}, {}

    def bone(self, name, pivot, parent=None, rotation=(0, 0, 0), upgrade=None):
        b = Bone(self, name, pivot, parent, rotation, upgrade)
        self.bones[name] = b
        return b

    # ---- forward kinematics: where a rest-pose point of a bone ends up once every rotation is applied
    def fk(self, bone, p):
        b = self.bones[bone]
        while b is not None:
            rot = [b.rotation[a] + self.pose.get(b.name, [0, 0, 0])[a] for a in range(3)]
            p = rotate_about(b.pivot, rot, p)
            b = self.bones[b.parent] if b.parent else None
        return p

    def cube_corners(self, bone, c):
        lo = [c["from"][a] - c.get("inflate", 0) for a in range(3)]
        hi = [c["from"][a] + c["size"][a] + c.get("inflate", 0) for a in range(3)]
        pts = [(x, y, z) for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])]
        rot = c.get("rotation")
        if rot:
            piv = c.get("pivot") or [c["from"][a] + c["size"][a] / 2 for a in range(3)]
            pts = [rotate_about(piv, rot, p) for p in pts]
        return [self.fk(bone, p) for p in pts]

    def posed_min_y(self, bone):
        b = self.bones[bone]
        return min(p[1] for c in b.cubes for p in self.cube_corners(bone, c))

    def frame(self, bone):
        """The rotation matrix of a bone's frame in the posed model (its own rotation and every ancestor's)."""
        M = ((1, 0, 0), (0, 1, 0), (0, 0, 1))
        b = self.bones[bone]
        while b is not None:
            rot = [b.rotation[a] + self.pose.get(b.name, [0, 0, 0])[a] for a in range(3)]
            M = mat_mul(rot_mat(*rot), M)
            b = self.bones[b.parent] if b.parent else None
        return M

    def level_rotation(self, parent, yaw=0.0):
        """The rotation for a child bone of `parent` that leaves the child LEVEL in the posed model, turned `yaw`
        degrees about the vertical (positive turns its -z front toward -x, the unit's right). A foot that rides on
        its shin tilts with every degree of thigh and knee - heels up, toes dug in ("the feet look odd", lovish
        Oct 15); a foot bone with this rotation stands flat, toes turned out like a person's."""
        R = mat_mul(transpose(self.frame(parent)), rot_mat(0, yaw, 0))
        rot = euler(R)
        check = mat_mul(self.frame(parent), rot_mat(*rot))
        want = rot_mat(0, yaw, 0)
        assert max(abs(check[i][j] - want[i][j]) for i in range(3) for j in range(3)) < 1e-3, "level_rotation"
        return rot

    def ground_leg(self, foot, also=()):
        """Stands a foot bone on y = 0 in the posed model and moves the bones it hangs from (`also`, e.g. its shin)
        down by the same amount, so foot and shin stay joined (ground() alone would part them at the ankle). Each
        bone's cubes move by the world drop expressed in that bone's own frame, so a tilted leg drops straight down
        instead of sliding."""
        lift = self.posed_min_y(foot)
        if abs(lift) < 1e-6:
            return
        drop = (0.0, -lift, 0.0)

        def shift(cubes, t):
            for c in cubes:
                c["from"] = [round(c["from"][k] + t[k], 3) for k in range(3)]
                if "pivot" in c:
                    c["pivot"] = [round(c["pivot"][k] + t[k], 3) for k in range(3)]
        fb = self.bones[foot]
        t = apply(transpose(self.frame(fb.parent)), drop) if fb.parent else drop
        shift(fb.cubes, t)
        fb.pivot = [round(fb.pivot[k] + t[k], 3) for k in range(3)]
        for name in also:
            shift(self.bones[name].cubes, apply(transpose(self.frame(name)), drop))
        assert abs(self.posed_min_y(foot)) < 1e-2, "ground_leg"

    def ground(self, foot_bones):
        """Stands each foot bone on y = 0 in the POSED model: its cubes are lowered by the lift the leg's rotations
        gave it (the README's "a shin that stands where the knee lands", done by arithmetic)."""
        for name in foot_bones:
            lift = self.posed_min_y(name)
            if abs(lift) < 1e-6:
                continue
            for c in self.bones[name].cubes:
                c["from"][1] = round(c["from"][1] - lift, 3)
                if "pivot" in c:
                    c["pivot"][1] = round(c["pivot"][1] - lift, 3)

    def solve_arm(self, upper, fore, hand_rest, target, elbow_out=0.0, elbow_down=0.0, bend=1, point_at=None,
                  avoid=(), avoid_margin=1.0, avoid_elbow=False, elbow_at=None, elbow_w=0.3):
        """Rotates the upper arm (x, y, z) and bends the forearm (x) so the hand's rest point lands on target.
        Coarse grid, then a fine one round the best; elbow_out keeps the elbow near its shoulder's x (out of the
        chest - not "outward" past the shoulder).

        An arm reaching a point has a free swivel - the elbow can sit anywhere on a circle round the shoulder-hand
        line - and a pure distance search takes whichever it meets first: the Crossbow Levy's first solve raised the
        elbow forward ABOVE the shoulder with the forearm folded back 102 degrees ("the arms look bent", lovish Oct 15).
        elbow_down > 0 makes the elbow hang: it is penalised for rising above a line 1 px under the lower of the
        shoulder and the hand (people carry things with the elbows down and in, unless the pose says otherwise).

        bend: the elbow flexes one way only. In the DSL a positive x swings a hanging part forward, so a forearm's
        natural flexion is x > 0 (the hand comes forward and up). Until Oct 15 this search ran x over -140..0 - every
        solved elbow bent BACKWARD in its own frame, and the solver made it reach by raising the upper arm level in
        front and letting the forearm hang from it (the "chicken wing" on the Paladin and the Levy). bend=-1 keeps the
        pre-Oct-15 range - a hyperextended elbow, for a deliberate pose only (it does not reproduce old outputs:
        the fine search never crosses straight either way).

        point_at: the centre of what the hand closes round (a shaft, a grip). The forearm must POINT at it - a
        straight wrist - and the cost rises past WRIST_FREE degrees of bend: without it the forearm arrived level
        with the fist hanging under the wrist, bent 66-90 degrees, and the shaft ran through the sleeve's end (the
        elbow fix's twin, Oct 15 review). Give `target` as the wrist point on the side the forearm comes in from.

        avoid: bones the forearm must stay out of (the torso, a tabard). Pointing the forearm at a grip close in front
        of the body sent it across the belly - 1.5-2.2 px into the Paladin's plackart and the Levy's chest (Oct 15
        re-check); points along the forearm's axis are kept avoid_margin (about the forearm's half-thickness) clear of
        each sizeable cube's posed bounding box. The avoided bones must not move while the arm is solved.
        avoid_elbow (off by default: the solves made before it are unchanged) keeps the elbow itself and the couter
        round it out of them too - the forearm samples start 30% down the forearm, so an elbow tucked behind the back
        plate went unseen (the Halberdier's new gauntlet grip, Oct 16: its couter 0.6-0.8 px in the chest at rest).
        """
        if bend not in (1, -1):
            raise ValueError("bend is +1 (natural flexion) or -1 (hyperextension)")
        U, F = self.bones[upper], self.bones[fore]
        best, best_d = None, 1e9
        shoulder = self.fk(upper, U.pivot)        # the upper arm's own rotation does not move its pivot
        boxes = []
        for name in avoid:
            for c in self.bones[name].cubes:
                if c["size"][0] * c["size"][1] * c["size"][2] < 3.0:
                    continue                      # rivets, studs, hem strips: too small to sink into (a thin
                    #                               cloth panel like a tabard still counts - it is wide)
                pts = self.cube_corners(name, c)
                boxes.append(([min(q[a] for q in pts) - avoid_margin for a in range(3)],
                              [max(q[a] for q in pts) + avoid_margin for a in range(3)]))

        def dist(ux, uy, uz, fx):
            U.rotation = [ux, uy, uz]
            F.rotation = [fx, 0, 0]
            p = self.fk(fore, hand_rest)
            d = sum((p[a] - target[a]) ** 2 for a in range(3))
            if boxes:
                e = self.fk(upper, F.pivot)
                for t in ((-0.15, 0.0, 0.15, 0.3, 0.55, 0.8) if avoid_elbow else (0.3, 0.55, 0.8)):
                    # t < 0: up the upper arm from the elbow; t >= 0: down the forearm toward the hand
                    q = [e[a] + ((p[a] - e[a]) * t if t >= 0 else (shoulder[a] - e[a]) * -t) for a in range(3)]
                    for lo, hi in boxes:
                        if lo[0] < q[0] < hi[0] and lo[1] < q[1] < hi[1] and lo[2] < q[2] < hi[2]:
                            depth = min(min(q[a] - lo[a], hi[a] - q[a]) for a in range(3))
                            d += (depth + 0.1) ** 2 * 2.0
            if point_at is not None:
                e = self.fk(upper, F.pivot)
                f = [p[a] - e[a] for a in range(3)]
                h = [point_at[a] - p[a] for a in range(3)]
                nf, nh = math.sqrt(sum(v * v for v in f)), math.sqrt(sum(v * v for v in h))
                if nf > 1e-6 and nh > 1e-6:
                    c = max(-1.0, min(1.0, sum(f[a] * h[a] for a in range(3)) / (nf * nh)))
                    over = math.degrees(math.acos(c)) - WRIST_FREE
                    if over > 0:
                        d += over * over * 0.01
            if elbow_at is not None:
                # where a person's elbow would be for this hold (lovish, Oct 16: "the elbows don't match human
                # anatomy" - the Levy's upper arms reached 40 degrees forward to a crossbow held out in front): the
                # cost of the elbow's distance from it, so the arm hangs from the shoulder and the forearm does the work
                e = self.fk(upper, F.pivot)
                d += elbow_w * sum((e[a] - elbow_at[a]) ** 2 for a in range(3))
            if elbow_out or elbow_down:
                e = self.fk(upper, F.pivot)
                if elbow_out:
                    d += elbow_out * (shoulder[0] - e[0]) ** 2 * 0.02   # keep the elbow near the shoulder's x
                if elbow_down:
                    over = e[1] - (min(shoulder[1], target[1]) - 1.0)
                    if over > 0:
                        d += elbow_down * over * over * 0.25
            return d
        for ux in range(-100, 171, 10):        # to 170: a hand behind the head needs the arm past vertical
            for uy in range(-70, 71, 10):
                for uz in range(-50, 51, 10):
                    for fx in (range(0, 141, 10) if bend > 0 else range(-140, 1, 10)):
                        d = dist(ux, uy, uz, fx)
                        if d < best_d:
                            best, best_d = (ux, uy, uz, fx), d
        ux, uy, uz, fx = best
        for a in range(ux - 10, ux + 11, 2):
            for b2 in range(uy - 10, uy + 11, 2):
                for c2 in range(uz - 10, uz + 11, 2):
                    for d2 in range(fx - 10, fx + 11, 2):
                        if d2 * bend < 0:
                            continue
                        d = dist(a, b2, c2, d2)
                        if d < best_d:
                            best, best_d = (a, b2, c2, d2), d
        ux, uy, uz, fx = best
        U.rotation = [ux, uy, uz]
        F.rotation = [fx, 0, 0]
        # the HAND's miss in px - not the search's cost (which carries the elbow penalties too: printed as "wrist
        # error" it read 2.04 px for a hand 0.08 px off, Oct 15 review)
        p = self.fk(fore, hand_rest)
        return math.sqrt(sum((p[a] - target[a]) ** 2 for a in range(3)))

    def to_json(self):
        bones = []
        for b in self.bones.values():
            d = {"name": b.name, "pivot": [round(v, 3) for v in b.pivot]}
            if b.parent:
                d["parent"] = b.parent
            if any(b.rotation):
                d["rotation"] = [round(v, 3) for v in b.rotation]
            if b.upgrade:
                d["upgrade"] = b.upgrade
            d["cubes"] = b.cubes
            bones.append(d)
        out = {"name": self.name, "faction": self.faction, "role": self.role, "scale_px_per_block": 16,
               "palette": self.palette, "emissive": self.emissive, "bones": bones, "pose": self.pose}
        if self.limb_ok:
            out["limb_ok"] = list(self.limb_ok)
        if self.contact_ok:
            out["contact_ok"] = [list(p) for p in self.contact_ok]
        if self.flat_light:
            out["flat_light"] = list(self.flat_light)
        return out


# ------------------------------------------------------------------ shape helpers --------------------------------
def pauldron(bone, side, shoulder, n, length, depth, droop0, droop_step, color, trim=None, thick=1.3, upgrade_spike=None):
    """n overlapping lames fanned from the shoulder point outward and down: the outer end of each droops more.
    side +1 = the unit's left (+x). The top lame can carry a trim rib and an upgrade-tagged spike."""
    sx, sy, sz = shoulder
    for i in range(n):
        droop = droop0 + droop_step * i
        w = length - 0.5 * i
        d = depth - 0.4 * i
        x0 = sx if side > 0 else sx - w
        bone.cube([x0, sy - thick * (i + 1), sz - d / 2], [w, thick, d], color,
                  rotation=[0, 0, -side * droop], pivot=[sx, sy, sz])
        if i == 0 and trim:
            bone.cube([x0, sy - 0.2, sz - 0.7], [w, 0.9, 1.4], trim, rotation=[0, 0, -side * droop], pivot=[sx, sy, sz])
            if upgrade_spike:
                tip = x0 + w - 1.2 if side > 0 else x0 + 0.2
                bone.cube([tip, sy, sz - 0.5], [1, 4, 1], trim, rotation=[0, 0, -side * droop], pivot=[sx, sy, sz],
                          upgrade=upgrade_spike)


def fauld(bone, top_y, width, depth_front, n, color, flare0=8, flare_step=8, height=2.4, widen=2.0, z_back=None):
    """Flared skirt plates front and back: each lower lame is wider and tilts further out."""
    for i in range(n):
        top = top_y - (height - 0.3) * i
        w = width + widen * i
        zf = depth_front - 0.15 * i
        bone.cube([-w / 2, top - height, -zf - 0.7], [w, height, 0.7], color,
                  rotation=[flare0 + flare_step * i, 0, 0], pivot=[0, top, -zf - 0.35])
        zb = (z_back if z_back is not None else depth_front) - 0.15 * i
        bone.cube([-w / 2, top - height, zb], [w, height, 0.7], color,
                  rotation=[-(flare0 + flare_step * i), 0, 0], pivot=[0, top, zb + 0.35])


def foot(m, pre, hx, y0, w, length, back, color, sole, toe_out=8, cap=None, strap=None):
    """A foot as its own bone under the shin ("<pre>_foot"), LEVEL in the posed model and turned out toe_out degrees
    (Model.level_rotation) - built like a foot, not a stub on a tube (lovish Oct 15: "the feet look odd"): a heel
    `back` px behind the ankle, the instep over it sloping down in two steps to a rounded toe, a sole a hair wider than
    the foot all round; `cap` colours the toe block (a sabaton's gilt cap), `strap` adds a band over the instep.
    y0 is the sole's underside in rest space, hx the leg's centre; length runs toe to heel. Returns (foot, shin) for
    Model.ground_leg. Cubes are proportioned from w, so a slim boot and a broad sabaton keep the same anatomy."""
    side = 1 if hx > 0 else -1
    shin = pre + "_shin"
    ft = m.bone(pre + "_foot", [hx, y0 + 1.0, 0], parent=shin, rotation=m.level_rotation(shin, yaw=-side * toe_out))
    zt, zh = -(length - back), back                       # the toe's tip and the heel's back
    zm = zt * 0.45                                         # where the instep meets the toe
    s = 0.4                                                # the sole's thickness
    ft.cube([hx - w / 2, y0 + s, zm], [w, 0.75 * w, zh - zm], color)                                  # heel and instep
    ft.cube([hx - w / 2 + 0.05, y0 + s, zt + 0.4], [w - 0.1, 0.5 * w, zm - zt - 0.2], cap or color)   # the toe
    ft.cube([hx - 0.375 * w, y0 + s, zt], [0.75 * w, 0.38 * w, 0.5], color)                           # its round
    if strap:
        ft.cube([hx - w / 2 - 0.05, y0 + s + 0.75 * w - 0.25, zm + 0.25], [w + 0.1, 0.3, 0.55], strap)
    ft.cube([hx - w / 2 - 0.1, y0, zt - 0.1], [w + 0.2, s, zh - zt + 0.2], sole)                      # the sole
    return ft.name, shin


def split_above(m, name, y, to):
    """Moves everything of bone `name` above height y (rest space) onto bone `to`, cutting the cubes that cross it:
    a cloth panel hung from a hinge at its middle swings its top half INTO the chest when the bottom swings out (the
    Levy's and Halberdier's tabards went 2.5 px into the torso on every stride - tools/contact_check.py, Oct 15). So
    the bib stays on the torso and only the skirt below the hinge swings. Cubes with their own rotation move whole
    when their centre is above y (an emblem), else stay."""
    src, dst = m.bones[name], m.bones[to]
    keep = []
    for c in src.cubes:
        y0, h = c["from"][1], c["size"][1]
        if c.get("rotation"):
            (dst.cubes if y0 + h / 2 > y else keep).append(c)
        elif y0 >= y:
            dst.cubes.append(c)
        elif y0 + h > y:
            top = dict(c, **{"from": [c["from"][0], round(y, 3), c["from"][2]],
                             "size": [c["size"][0], round(y0 + h - y, 3), c["size"][2]]})
            c["size"] = [c["size"][0], round(y - y0, 3), c["size"][2]]
            keep.append(c)
            dst.cubes.append(top)
        else:
            keep.append(c)
    src.cubes = keep


def rounded(bone, cx, cz, y0, y1, w, d, color, k=3, **kw):
    """An upright box with ROUND vertical edges: k nested boxes whose corners lie on the ellipse w x d (k=2 is an
    octagon; k=3 a 12-gon - from the RTS camera and close up it reads as turned, not boxed). lovish, Oct 16: "the
    helmets could curve a bit more ... they are currently a bit boxy"."""
    A, B = w / 2, d / 2
    # the outermost corners sit at cos/sin of (0.5/k) x 90 deg: scale up so the shape keeps its full width and depth
    s = 1.0 / math.cos(0.5 / k * math.pi / 2)
    for i in range(k):
        t = (i + 0.5) / k * math.pi / 2
        hw, hd = A * s * math.cos(t), B * s * math.sin(t)
        bone.cube([cx - hw, y0, cz - hd], [2 * hw, y1 - y0, 2 * hd], color, **kw)


def front_band(bone, cx, cz, y0, y1, w, d, color, k=3, t=0.3, **kw):
    """A band laid over the FRONT of a rounded() shape of the same w x d: one thin plate on each nested box's front
    face, so a visor slit or a brow band follows the curve instead of floating flat in front of it."""
    A, B = w / 2, d / 2
    s = 1.0 / math.cos(0.5 / k * math.pi / 2)
    for i in range(k):
        tt = (i + 0.5) / k * math.pi / 2
        hw, hd = A * s * math.cos(tt), B * s * math.sin(tt)
        bone.cube([cx - hw - 0.02, y0, cz - hd - t / 2], [2 * hw + 0.04, y1 - y0, t], color, **kw)


def dome(bone, cx, cz, y0, h, w, d, color, layers=3, top=0.42, k=3, **kw):
    """A rounded cap rising from y0 by h: `layers` rounded slices whose size follows a quarter circle from the full
    w x d down to `top` of it (a helmet skull, a kettle hat's crown)."""
    for j in range(layers):
        t0, t1 = j / layers, (j + 1) / layers
        tm = (t0 + t1) / 2
        r = top + (1 - top) * math.sqrt(max(0.0, 1 - tm * tm))
        rounded(bone, cx, cz, y0 + t0 * h, y0 + t1 * h + (0.02 if j < layers - 1 else 0), w * r, d * r, color, k=k, **kw)


def lathe(bone, cx, cz, rx, rz, bands, color, n=12, t=0.5, **kw):
    """A turned surface - a dome's shoulder, a kettle hat's crown - out of n tilted slabs per band. bands: (y0, f0, y1,
    f1): the surface runs from f0 x (rx, rz) at y0 to f1 x (rx, rz) at y1. Each slab is turned about y to face out and
    leaned in by the band's slope, so the outline really curves (stacked boxes read as a ziggurat - the Paladin
    helm's sixth-pass lesson); the seams overlap a little."""
    for (y0, f0, y1, f1) in bands:
        ym = (y0 + y1) / 2
        for k in range(n):
            th = 2 * math.pi * (k + 0.5) / n
            s, c = math.sin(th), math.cos(th)
            r0 = math.hypot(rx * s, rz * c) * f0
            r1 = math.hypot(rx * s, rz * c) * f1
            rm = (r0 + r1) / 2
            lean = math.atan2(r0 - r1, y1 - y0)
            length = math.hypot(r0 - r1, y1 - y0) + 0.12
            width = 2 * rm * math.tan(math.pi / n) + 0.18
            # the slab's centre, a half thickness inside the surface (along its leaned normal)
            ri = rm - (t / 2) * math.cos(lean)
            yc = ym - (t / 2) * math.sin(lean)
            centre = (cx - ri * s, yc, cz - ri * c)
            bone.box_at(centre, (width, length, t), color, rotation=[math.degrees(lean), math.degrees(th), 0],
                        pivot=list(centre), **kw)


# ------------------------------------------------------------------ oriented boxes, poles and hands ---------------
def _add(*vs):
    return tuple(sum(v[i] for v in vs) for i in range(3))


def _mul(v, k):
    return (v[0] * k, v[1] * k, v[2] * k)


def _dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def _cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def _unit(v):
    n = math.sqrt(_dot(v, v))
    if n < 1e-9:
        raise ValueError("zero vector")
    return (v[0] / n, v[1] / n, v[2] / n)


def orient(ex, ey, ez):
    """The DSL rotation (degrees) that turns a box's own x, y and z axes onto the unit vectors ex, ey, ez (orthonormal,
    right-handed): euler() of the matrix with those columns, the gimbal case (a long axis straight along x) handled."""
    R = ((ex[0], ey[0], ez[0]), (ex[1], ey[1], ez[1]), (ex[2], ey[2], ez[2]))
    if abs(R[2][0]) < 0.9995:
        rot = euler(R)
    else:      # ry = -/+90: only rz - rx is defined; rx = 0
        rot = [0.0, -90.0 if R[2][0] > 0 else 90.0, round(math.degrees(math.atan2(-R[0][1], R[1][1])), 3)]
    M = rot_mat(*rot)
    assert max(abs(M[i][j] - R[i][j]) for i in range(3) for j in range(3)) < 5e-3, "orient"
    return rot


def slab(bone, centre, along, across, size, color, **kw):
    """A box centred on `centre` whose length size[2] runs along `along`, its width size[0] along `across` (made square
    to `along`) and its thickness size[1] the third way - any direction, as one rotated cube (box_at + orient)."""
    ez = _unit(along)
    ex = _unit(_add(across, _mul(ez, -_dot(across, ez))))
    ey = _cross(ez, ex)
    rot = orient(ex, ey, ez)
    if not any(abs(a) > 1e-3 for a in rot):
        return bone.box_at(list(centre), list(size), color, **kw)
    return bone.box_at(list(centre), list(size), color, rotation=rot, pivot=list(centre), **kw)


def octagon_axle(bone, c, length, w, color, **kw):
    """A short regular-octagon cylinder along x centred on c (the four boxes of octagon_pole, about x): a hinge's
    joint - an elbow or a knee bending about its bone's x looks the same at every angle (the Levy's elbows were two
    tubes overlapping, the sleeve's corner poking out past the bend, Oct 16)."""
    side = w * math.tan(math.pi / 8)
    bone.box_at(list(c), [length, w, side], color, **kw)
    bone.box_at(list(c), [length, side, w], color, **kw)
    for a in (45, -45):
        bone.box_at(list(c), [length, w, side], color, rotation=[a, 0, 0], pivot=list(c), **kw)


def ngon_prism(bone, cx, cz, y0, y1, w, color, n=16, **kw):
    """An upright prism of REGULAR n-gon section (n even), w across the flats: n/2 bands, each w long and
    w*tan(pi/n) wide, turned by 360/n degrees steps about y. Their union is EXACTLY the polygon - no stepped corners
    (rounded()'s nested boxes) and no star points (rotated squares stick out to w/sqrt(2)). For discs and rings seen
    from above: a hat's brim, a shield boss, a round pauldron (lovish, Oct 16: "The blocks your using are sticking out
    from the characters making them look blocky and edges sticking out")."""
    if n % 2 or n < 4:
        raise ValueError("ngon_prism: n must be even and >= 4")
    side = w * math.tan(math.pi / n)
    yc, h = (y0 + y1) / 2, y1 - y0
    for k in range(n // 2):
        a = 360.0 / n * k
        if abs(a) < 1e-9:
            bone.box_at([cx, yc, cz], [w, h, side], color, **kw)
        elif abs(a - 90.0) < 1e-9:
            bone.box_at([cx, yc, cz], [side, h, w], color, **kw)
        else:
            bone.box_at([cx, yc, cz], [w, h, side], color, rotation=[0, a, 0], pivot=[cx, yc, cz], **kw)


def octagon_pole(bone, cx, cz, y0, y1, w, color, **kw):
    """An upright pole of REGULAR octagonal section, w across the flats: four boxes - the bands between opposite flats,
    two square to the axes and two turned 45 degrees about the pole. Their union is exactly the octagon (each band is
    w x w*tan(22.5)), so the outline has no steps and no star points: eight faces lit in eight shades read as a
    turned, ROUND pole (a square section's one light and one dark face read as two thin poles side by side - the
    Halberdier's haft, lovish Oct 16: "looks like seperate poles"). One piece from y0 to y1."""
    side = w * math.tan(math.pi / 8)
    yc, h = (y0 + y1) / 2, y1 - y0
    bone.box_at([cx, yc, cz], [w, h, side], color, **kw)
    bone.box_at([cx, yc, cz], [side, h, w], color, **kw)
    for a in (45, -45):
        bone.box_at([cx, yc, cz], [w, h, side], color, rotation=[0, a, 0], pivot=[cx, yc, cz], **kw)


# The four fingers, index to little: (proximal, middle, distal) lengths, where the knuckle sits ahead of the palm's
# contact line (the knuckles form an arc: the middle one furthest forward) and the width factor, for a hand 0.46 px a
# finger (1.85 px across the knuckles).
FINGERS = (((1.00, 0.62, 0.48), 0.56, 1.00), ((1.08, 0.68, 0.50), 0.62, 1.04),
           ((1.03, 0.64, 0.48), 0.56, 0.97), ((0.86, 0.54, 0.44), 0.44, 0.88))


def grip_hand(bone, centre, axis, r, side, color, accent=None, back=(0.0, 1.0, 0.0), fingers=4, thumb=True, curl=1.0,
              spacing=None, finger_w=0.46, finger_t=0.42, palm_t=0.62, palm_len=1.7, cuff=None, cuff_len=0.65,
              knuckle=None, heel=None, wrist=None, lames=2, knuckles=True, **kw):
    """A hand closed round a cylinder of radius r whose axis is the unit vector `axis` through `centre` (any direction:
    an upright haft, a level stock, a tilted grip). lovish, Oct 16: "the fingers are also lacking apperance looks like a
    block mesh for hands ... when he puts his hand around the weapon it doesnt look natural".

    Anatomy of the fist, in the plane square to the axis (u: out of the back of the hand, v: the way the knuckles
    point): the back of the hand lies on the outside of the cylinder, the palm against it; four fingers - index at the
    +axis end, tapering to the little finger, pressed together - wrap it the long way round (out, front, inside), each
    in three phalanges every one TANGENT to the cylinder (the chain is a tangential polygon: each segment touches at
    its middle, so the joints stand out as knuckles); a knuckle cap on each first joint; the heel of the palm behind the
    cylinder; the thumb from the ball of the thumb round the OTHER way, over the curled index finger; a cuff stub from
    the back of the palm toward `back` (where the forearm comes from - its wrist end is returned).

    side: +1 = the unit's left hand (+x), -1 = the right (it fixes which side the palm faces). color: the glove (the
    finger lames, knuckles, metacarpal lames); accent: the middle phalanges and the back plate under the lames (steel
    lames in two tones: "plate" / "plate_dark"), default color; cuff / knuckle / heel: their colours (default accent /
    color / accent). lames: transverse plates over the back of the hand (a gauntlet's metacarpal lames; 0 for a glove);
    knuckles: a cap on each first joint (a gauntlet's knuckle plates; a soft glove can do without).
    curl < 1 opens the hand (each joint bends curl x as far; a free, relaxed hand: curl ~0.45 round a thin r).
    wrist: if given, the hand is moved so its wrist point lands there (centre is then ignored) - a free hand placed
    from its forearm. finger_w / finger_t / palm_t / palm_len size the hand (keep the fingers >= 0.35 px for the
    texture density; the small cubes go into the "lod" detail, the back plate and the heel stay - a fist at distance).
    Every box is one rotated cube (slab).

    Returns {"wrist": the cuff's far end (put the forearm's wrist point here), "aim": a point 2 px further along the
    hand's line (solve_arm's point_at), "u", "v", "axis": the hand's frame, "centre"}."""
    a = _unit(axis)
    bb = _unit(back)
    bp = _add(bb, _mul(a, -_dot(bb, a)))
    v = _mul(_unit(bp), -1.0)
    u = _unit(_mul(_cross(a, v), -side))
    accent = accent or color
    knuckle = knuckle or color
    heel = heel or accent
    cuff = cuff or accent
    s = finger_w / 0.46                                     # the hand's scale against the 1.85 px gauntlet
    n = max(1, min(4, fingers))
    pitch = spacing or finger_w
    hw = n * pitch + 0.02                                   # the hand's width along the axis
    Rc = r + finger_t / 2 + 0.02                            # the fingers' centre line round the cylinder
    boxes = []                                              # (centre (u, v, h) local, along (world), size, colour)

    def world(p):
        return _add(_mul(u, p[0]), _mul(v, p[1]), _mul(a, p[2]))

    def seg(p, q, w, t, col, ext0=0.0, ext1=0.0):
        d = (q[0] - p[0], q[1] - p[1], q[2] - p[2])
        L = math.sqrt(_dot(d, d))
        dn = _mul(d, 1.0 / L)
        c = _mul(_add(_add(p, _mul(dn, -ext0)), _add(q, _mul(dn, ext1))), 0.5)
        boxes.append((c, world(dn), (max(0.35, w), max(0.35, t), L + ext0 + ext1), col))

    # the fingers: a turtle from each knuckle, the bends those of the tangential polygon round Rc (times curl)
    knuck = []
    for i in range(n):
        lens, s0, wf = FINGERS[i if n == 4 else min(3, round(i * 3 / max(1, n - 1)))]
        lens = [L * s for L in lens]
        t = [s0 * s]
        for L in lens:
            t.append(max(0.05, L - t[-1]))                  # tangent lengths at each joint (L = t_j + t_j+1)
        h = ((n - 1) / 2.0 - i) * pitch
        pos = (Rc, t[0])
        psi = math.pi / 2                                   # the palm's heading: along +v
        w0 = finger_w * wf + 0.01
        knuck.append((pos, 2 * math.atan(t[0] / Rc) * curl, h, w0))
        for j, L in enumerate(lens):
            psi += 2 * math.atan(t[j] / Rc) * curl
            q = (pos[0] + L * math.cos(psi), pos[1] + L * math.sin(psi))
            seg((pos[0], pos[1], h), (q[0], q[1], h), w0 * (1.0, 0.96, 0.92)[j], finger_t * (1.0, 0.97, 0.94)[j],
                accent if j == 1 else color, ext0=0.07 if j else 0.0, ext1=0.07 if j < 2 else 0.0)
            pos = q
    # the fist's core: a block under the fingers across the front of the cylinder, hidden by them close up - the
    # phalanges are "lod" detail (dsl_to_game.is_detail), and without it the far camera saw a plate beside an unheld
    # pole; only where it is big enough to stay at distance itself (a gauntlet, not a small glove)
    core = (hw - 0.04, 0.36, 0.95 * s)
    if curl >= 0.7 and core[0] * core[1] * core[2] > 0.62:
        ca = math.radians(95)
        cr = Rc - 0.1                                       # deep enough that no corner shows between the fingers
        boxes.append(((cr * math.cos(ca), cr * math.sin(ca), 0.0), world((-math.sin(ca), math.cos(ca), 0.0)), core,
                      color))
    # the back of the hand: a plate from the wrist to the knuckle line on the outside of the cylinder, and over it
    # `lames` transverse plates stepping up toward the wrist like roof tiles (the gauntlet's articulation)
    s0m = sum(k[0][1] for k in knuck) / n
    vw = s0m - palm_len * s
    uc = r + 0.02 + palm_t / 2
    boxes.append(((uc, (vw + s0m) / 2, 0.0), v, (hw, palm_t, s0m - vw), accent if lames else color))
    if lames:
        span = (s0m - 0.12 - (vw + 0.35)) / lames
        for k in range(lames):
            v1 = s0m - 0.12 - k * span
            boxes.append(((uc + palm_t / 2 + 0.07 + 0.05 * k, v1 - span / 2 - 0.06, 0.0), v,
                          (hw - 0.1, 0.22, span + 0.1), color))
    # a knuckle cap on each first joint, laid along that joint's bisector, a little proud of the finger
    for (pos, bend, h, w0) in (knuck if knuckles else ()):
        nu, nv = math.cos(bend / 2), math.sin(bend / 2)
        lift = finger_t * 0.5 + 0.04
        cc = (pos[0] + nu * (lift - finger_t * 0.5 + 0.04), pos[1] + nv * (lift - finger_t * 0.5 + 0.04), h)
        boxes.append((cc, world((-nv, nu, 0.0)), (w0 + 0.02, finger_t + 0.12, 0.5 * s + 0.05), knuckle))
    # the heel of the palm behind the cylinder, under the little-finger half (the ball of the thumb takes the top)
    ht = 0.46 * s
    if curl >= 0.7:                                         # (an open hand has nothing to close it round)
        u0, u1 = -r * 0.55, uc
        boxes.append((((u0 + u1) / 2, -(r + 0.02 + ht / 2), -hw * 0.17), u, (hw * 0.66, ht, u1 - u0), heel))
    # the thumb: from the ball of the thumb round the OTHER way, over the curled index finger; on an open hand
    # (curl < 0.7) it lies off the palm alongside the index finger instead, its tip by the index's middle joint
    if thumb:
        tt = max(0.38, 0.44 * s)
        hi = (n - 1) / 2.0 * pitch + 0.05 * s
        lens = (1.0 * s, 0.76 * s, 0.6 * s)                 # metacarpal (the ball of the thumb), proximal, distal
        widths = ((1.3, 1.2), (1.0, 1.0), (0.94, 0.94))
        cols = (heel, color, color)
        if curl >= 0.7:
            # round the back of the cylinder on it, then pressed down onto the curled index finger
            radii = (r + 0.4 * s, r + tt / 2 + 0.05, r + finger_t + tt / 2 - 0.14, r + finger_t + tt / 2 - 0.14)
            ang = math.radians(-35.0)
            prev = (radii[0] * math.cos(ang), radii[0] * math.sin(ang))
            for j, L in enumerate(lens):
                ra, rb = radii[j], radii[j + 1]
                cd = max(-1.0, min(1.0, (ra * ra + rb * rb - L * L) / (2 * ra * rb)))
                ang -= math.acos(cd) * (curl if j else 1.0)
                q = (rb * math.cos(ang), rb * math.sin(ang))
                seg((prev[0], prev[1], hi), (q[0], q[1], hi), tt * widths[j][0], tt * widths[j][1], cols[j],
                    ext0=0.06 if j else 0.0, ext1=0.06 if j < 2 else 0.0)
                prev = q
        else:
            prev = (r + 0.1, vw + 0.45 * s, hi - 0.05)
            psi = math.radians(90 + 22)                     # down the palm, standing off it toward the palm side
            for j, L in enumerate(lens):
                q = (prev[0] + L * math.cos(psi), prev[1] + L * math.sin(psi), prev[2] + L * 0.16)
                seg(prev, q, tt * widths[j][0], tt * widths[j][1], cols[j], ext0=0.06 if j else 0.0,
                    ext1=0.06 if j < 2 else 0.0)
                prev = q
                psi += math.radians(4 + 10 * curl)
    # the cuff: a stub from the back of the palm toward the forearm (along `back` itself, so a forearm coming in at an
    # angle to the hand meets it square)
    root = (uc - 0.05, vw + 0.1, 0.0)
    bb_l = (_dot(bb, u), _dot(bb, v), _dot(bb, a))
    boxes.append((_add(root, _mul(bb_l, cuff_len / 2 - 0.1)), bb, (hw * 0.97, palm_t * 1.45, cuff_len), cuff))
    W = _add(root, _mul(bb_l, cuff_len - 0.1))
    if wrist is not None:
        centre = _add(wrist, _mul(world(W), -1.0))
    centre = tuple(centre)
    for (c, along, size, col) in boxes:
        slab(bone, _add(centre, world(c)), along, a, size, col, **kw)
    Ww = _add(centre, world(W))
    return {"wrist": Ww, "aim": _add(Ww, _mul(bb, -2.0)), "u": u, "v": v, "axis": a, "centre": centre}


def ring(bone, centre, radius, n, thick, depth, color, upgrade=None):
    """A ring in the x-y plane (a halo seen from the front) out of n rotated segments."""
    seg = 2 * radius * math.tan(math.pi / n) + 0.3
    for k in range(n):
        a = 2 * math.pi * k / n
        cx, cy = centre[0] + radius * math.cos(a), centre[1] + radius * math.sin(a)
        bone.box_at((cx, cy, centre[2]), (seg, thick, depth), color,
                    rotation=[0, 0, math.degrees(a) + 90], pivot=[cx, cy, centre[2]], upgrade=upgrade)


def rays(bone, centre, radius, n, length, thick, depth, color, upgrade=None):
    """n short bars pointing out from a centre (the sun emblem), in the x-y plane."""
    for k in range(n):
        a = 2 * math.pi * k / n + math.pi / n
        cx, cy = centre[0] + radius * math.cos(a), centre[1] + radius * math.sin(a)
        bone.box_at((cx, cy, centre[2]), (length, thick, depth), color,
                    rotation=[0, 0, math.degrees(a)], pivot=[cx, cy, centre[2]], upgrade=upgrade)


def disc(bone, centre, rows, row_h, depth, color, rim, rim_w=1.0):
    """A round plate out of stacked slabs (pixel-art circle) with a rim on every slab end."""
    cx, cy, cz = centre
    n = len(rows)
    y0 = cy - n * row_h / 2
    for i, w in enumerate(rows):
        y = y0 + i * row_h
        bone.cube([cx - w / 2, y, cz], [w, row_h, depth], color)
        bone.cube([cx - w / 2 - rim_w, y, cz - 0.15], [rim_w, row_h, depth + 0.3], rim)
        bone.cube([cx + w / 2, y, cz - 0.15], [rim_w, row_h, depth + 0.3], rim)
        if i == 0:
            bone.cube([cx - w / 2 - rim_w, y - rim_w, cz - 0.15], [w + 2 * rim_w, rim_w, depth + 0.3], rim)
        if i == n - 1:
            bone.cube([cx - w / 2 - rim_w, y + row_h, cz - 0.15], [w + 2 * rim_w, rim_w, depth + 0.3], rim)


def blade(bone, base, length, widths, thick, color, tip_color=None, fuller=None):
    """A tapered vertical blade: stacked sections of decreasing width and a diamond tip (a cube turned 45)."""
    bx, by, bz = base
    n = len(widths)
    sec = length / n
    for i, w in enumerate(widths):
        bone.cube([bx - w / 2, by + i * sec, bz - thick / 2], [w, sec + 0.05, thick], color)
    if fuller:
        bone.cube([bx - 0.3, by + 0.5, bz - thick / 2 - 0.1], [0.6, length - 2, thick + 0.2], fuller)
    t = widths[-1] * 0.9
    bone.box_at((bx, by + length + t * 0.6, bz), (t, t, thick * 0.9), tip_color or color,
                rotation=[0, 0, 45], pivot=[bx, by + length + t * 0.6, bz])


# ------------------------------------------------------------------ the Sunforged kit ----------------------------
PALETTE = {
    "plate": "#e9e4d8", "gold": "#d4a63c", "tabard": "#2f5fb3", "leather": "#5b3f2a", "steel": "#8c8f96",
    "sun": "#ffd96a", "glow": "#fff1b0", "dark": "#3b3630",
}
EMISSIVE = ["sun", "glow"]


def leg(m, side, hip, thigh_len, shin_len, width, depth, thigh_rot, shin_rot, foot_rot=None, foot_len=None,
        toe="gold", knee_cop=True, sabaton_h=2.2):
    """Thigh (hip pivot) -> shin (knee pivot, child) -> optional foot bone (ankle pivot, child). side +1 = left."""
    hx, hy = hip
    x0 = hx - width / 2
    knee_y = hy - thigh_len
    ankle_y = knee_y - shin_len
    pre = "left" if side > 0 else "right"
    thigh = m.bone(pre + "_thigh", [hx, hy, 0], rotation=thigh_rot)
    thigh.cube([x0, knee_y, -depth / 2], [width, thigh_len, depth], "plate")
    thigh.cube([x0 + 0.3, knee_y + 2, -depth / 2 - 0.4], [width - 0.6, thigh_len - 3, 0.9], "plate", inflate=0.15)   # cuisse
    thigh.cube([x0 - 0.1, hy - 1.6, -depth / 2 - 0.2], [width + 0.2, 1.2, depth + 0.4], "leather")                      # strap
    shin = m.bone(pre + "_shin", [hx, knee_y, 0], parent=pre + "_thigh", rotation=shin_rot)
    shin.cube([x0, ankle_y, -depth / 2], [width, shin_len, depth], "plate")
    shin.cube([x0 + 0.3, ankle_y + 1, -depth / 2 - 0.4], [width - 0.6, shin_len - 2.5, 0.9], "plate", inflate=0.15)   # greave
    if knee_cop:
        shin.cube([x0 - 0.25, knee_y - 1.8, -depth / 2 - 0.5], [width + 0.5, 3.4, depth + 0.6], "plate", inflate=0.25)
        shin.cube([x0 + width / 2 - 0.5, knee_y - 0.2, -depth / 2 - 1.0], [1, 1.2, 0.8], "gold")                        # knee rivet
    fl = foot_len or depth + 1.5
    if foot_rot is None:
        host, fy = shin, ankle_y
    else:
        host = m.bone(pre + "_foot", [hx, ankle_y, 0], parent=pre + "_shin", rotation=foot_rot)
        fy = ankle_y
    host.cube([x0 - 0.3, fy - sabaton_h, -fl + depth / 2], [width + 0.6, sabaton_h, fl], "plate")
    host.cube([x0 - 0.3, fy - sabaton_h, -fl + depth / 2 - 0.3], [width + 0.6, sabaton_h * 0.55, 1.3], toe)
    return pre + ("_foot" if foot_rot is not None else "_shin")


def arm(m, side, shoulder, upper_len, fore_len, width, depth, hand=3.6):
    """Upper arm (shoulder pivot) -> forearm (elbow pivot, child) with couter, vambrace and gauntlet.
    Returns (upper, fore, hand_centre_rest)."""
    sx, sy, sz = shoulder
    x0 = sx - width / 2
    pre = "left" if side > 0 else "right"
    elbow_y = sy - upper_len
    wrist_y = elbow_y - fore_len
    up = m.bone(pre + "_arm", [sx, sy, sz])
    up.cube([x0, elbow_y, sz - depth / 2], [width, upper_len, depth], "plate")
    up.cube([x0 + 0.2, elbow_y + 1.5, sz - depth / 2 - 0.3], [width - 0.4, upper_len - 2.5, 0.8], "leather")        # strap
    fo = m.bone(pre + "_fore", [sx, elbow_y, sz], parent=pre + "_arm")
    fo.cube([x0, wrist_y, sz - depth / 2], [width, fore_len, depth], "plate")
    fo.cube([x0 - 0.3, elbow_y - 1.6, sz - depth / 2 - 0.35], [width + 0.6, 3.0, depth + 0.7], "plate", inflate=0.2)  # couter
    fo.cube([x0 + 0.2, wrist_y + 1, sz - depth / 2 - 0.45], [width - 0.4, fore_len - 3.5, 0.9], "plate", inflate=0.15)  # vambrace
    fo.cube([x0 - 0.2, wrist_y - hand + 0.8, sz - depth / 2 - 0.3], [width + 0.4, hand, depth + 0.6], "leather")     # gauntlet
    fo.cube([x0 - 0.3, wrist_y - 0.4, sz - depth / 2 - 0.45], [width + 0.6, 1.2, depth + 0.9], "gold")                # cuff
    hand_centre = (sx, wrist_y - hand / 2 + 0.8, sz)
    return pre + "_arm", pre + "_fore", hand_centre


def torso(m, hip_y, waist_w, waist_h, chest_w, chest_h, depth, tabard=True, chevron=False):
    """Pelvis + belt + chest with a raised breastplate, gorget trim, back plate; the body bone."""
    body = m.bone("body", [0, hip_y, 0])
    chest_y = hip_y + waist_h
    top = chest_y + chest_h
    body.cube([-waist_w / 2, hip_y, -depth / 2 + 0.5], [waist_w, waist_h, depth - 1], "plate")                    # pelvis
    body.cube([-waist_w / 2 - 0.4, chest_y - 1.4, -depth / 2 + 0.2], [waist_w + 0.8, 1.6, depth - 0.4], "leather")  # belt
    body.cube([-1.1, chest_y - 1.5, -depth / 2 - 0.4], [2.2, 1.8, 0.7], "sun")                                       # buckle
    body.cube([-chest_w / 2, chest_y, -depth / 2], [chest_w, chest_h, depth], "plate")                                # chest
    if chevron:   # two plates meeting at the centre line, the outer ends a little lower: a V breastplate
        pw = chest_w / 2 - 0.6
        for s in (1, -1):
            x0 = 0 if s > 0 else -pw
            body.cube([x0, chest_y + 1.5, -depth / 2 - 0.55], [pw, chest_h - 3.5, 0.8], "plate", inflate=0.1,
                      rotation=[0, 0, -s * 10], pivot=[0, chest_y + 1.5 + (chest_h - 3.5), -depth / 2 - 0.15])
    else:
        body.cube([-chest_w / 2 + 1, chest_y + 1.5, -depth / 2 - 0.55], [chest_w - 2, chest_h - 3.5, 0.8], "plate", inflate=0.15)
    body.cube([-chest_w / 2 + 1, chest_y + 1.5, depth / 2 - 0.25], [chest_w - 2, chest_h - 3.5, 0.8], "plate", inflate=0.1)  # back
    body.cube([-chest_w / 2 - 0.2, top - 1.3, -depth / 2 - 0.3], [chest_w + 0.4, 1.3, depth + 0.6], "gold")           # gorget
    body.cube([-chest_w / 2 - 0.2, chest_y - 0.2, -depth / 2 - 0.25], [chest_w + 0.4, 0.8, depth + 0.5], "gold")      # lower trim
    if tabard:
        tw = max(4, chest_w * 0.45)
        body.cube([-tw / 2, chest_y + 0.6, -depth / 2 - 0.95], [tw, chest_h - 2.5, 0.5], "tabard")
        body.cube([-tw / 2, chest_y + 0.6, depth / 2 + 0.45], [tw, chest_h - 2.5, 0.5], "tabard")
    return body, chest_y, top


def helm_great(m, head_y, w, h, d, crest=True, wings_upgrade=None, slit="dark"):
    """A faceless great helm: smooth front plate, one slit, brow band, a crest ridge; an aventail collar below."""
    head = m.bone("head", [0, head_y, 0], parent="body")
    head.cube([-w / 2, head_y, -d / 2], [w, h, d], "plate")
    head.cube([-w / 2 + 0.4, head_y + 0.8, -d / 2 - 0.5], [w - 0.8, h - 1.6, 0.7], "plate", inflate=0.1)      # faceplate
    head.cube([-w / 2 + 1, head_y + h * 0.45, -d / 2 - 0.8], [w - 2, 0.8, 0.5], slit)                           # the slit
    head.cube([-w / 2 - 0.2, head_y + h - 1.5, -d / 2 - 0.3], [w + 0.4, 1.1, d + 0.6], "gold")                  # brow band
    head.cube([-w / 2 - 0.1, head_y - 1.8, -d / 2 - 0.3], [w + 0.2, 2.2, d + 0.6], "leather")                   # aventail
    if crest:
        head.cube([-0.6, head_y + h, -d / 2 + 0.5], [1.2, 1.8, d - 1], "gold")
        head.cube([-0.8, head_y + h + 1.2, -1.4], [1.6, 2.2, 2.8], "sun")                                       # sun disc on the crest
    if wings_upgrade:
        for s in (1, -1):
            x0 = w / 2 if s > 0 else -w / 2 - 1
            head.cube([x0, head_y + h - 2.5, -0.8], [1, 5, 1.6], "gold", rotation=[0, 0, -s * 20],
                      pivot=[x0 + (0 if s > 0 else 1), head_y + h - 2.5, 0], upgrade=wings_upgrade)
    return head


# ------------------------------------------------------------------ 1. TEMPLAR ----------------------------------
def templar():
    """Closest to human of the three (2 of 5 on the faction dial): a guardian at rest, hands on a sword planted
    point-down before him, a heater tower shield strapped to the left arm, a halo of eight segments behind a
    faceless great helm. Taller and narrower than a player body (44 px), long shins, fanned pauldrons."""
    m = Model("Paladin - Templar", "sunforged", "tank (direction 1 of 3)", PALETTE, EMISSIVE)
    HIP, CHEST_H, WAIST_H, DEPTH = 20, 9, 5, 6
    body, chest_y, top = torso(m, HIP, 9, WAIST_H, 11, CHEST_H, DEPTH)
    fauld(body, HIP, 9, DEPTH / 2, 3, "plate")
    body.cube([-2.5, 8, -DEPTH / 2 - 1.0], [5, 12, 0.5], "tabard", rotation=[14, 0, 0], pivot=[0, HIP, -DEPTH / 2 - 0.75])   # tabard skirt
    body.cube([-2.5, 9, DEPTH / 2 + 0.5], [5, 11, 0.5], "tabard", rotation=[-8, 0, 0], pivot=[0, HIP, DEPTH / 2 + 0.75])
    # shoulders: three fanned lames each, gold rib on the top lame
    for s in (1, -1):
        pauldron(body, s, (s * 6.0, top + 0.4, 0), 3, 6.0, 6.4, 14, 16, "plate", trim="gold")
    head = helm_great(m, top + 1.2, 7, 7.2, 7.2, crest=True, wings_upgrade="sun_ward")
    # the halo: a ring behind the helm, and an inner ring once Sun Ward is researched
    hc = (0, top + 1.2 + 3.8, 5.6)
    ring(head, hc, 6.2, 8, 0.9, 0.5, "sun")
    ring(head, hc, 4.0, 8, 0.7, 0.4, "glow", upgrade="sun_ward")
    # legs: braced (right forward), shins vertical, feet grounded by arithmetic
    fl = leg(m, -1, (-3.2, HIP), 9, 9, 4.6, 5, thigh_rot=[26, 0, 0], shin_rot=[-26, 0, 0])
    fr = leg(m, 1, (3.2, HIP), 9, 9, 4.6, 5, thigh_rot=[-12, 0, 0], shin_rot=[12, 0, 0])
    # arms
    ra, rf, rhand = arm(m, -1, (-7.2, top - 0.6, 0), 8, 8, 3.6, 4.2)
    la, lf, lhand = arm(m, 1, (7.2, top - 0.6, 0), 8, 8, 3.6, 4.2)
    # the sword, planted point-down in front of the right leg; the right hand rests on its grip
    sx, sz = -6.2, -7.8
    sword = m.bone("sword", [sx, 22, sz], parent="body")
    sword.cube([sx - 0.6, 18.5, sz - 0.6], [1.2, 4.5, 1.2], "leather")             # grip
    sword.cube([sx - 1.1, 23, sz - 1.1], [2.2, 2.0, 2.2], "gold")                  # pommel
    sword.cube([sx - 3.6, 17.2, sz - 0.8], [7.2, 1.4, 1.6], "gold")                # crossguard
    sword.cube([sx - 0.9, 17.4, sz - 0.9], [1.8, 1.0, 1.8], "sun")                 # sun at the ricasso
    for i, w in enumerate((2.6, 2.2, 1.8, 1.3)):                                   # blade, point down, tapering
        y0 = 17.2 - (i + 1) * 4.1
        sword.cube([sx - w / 2, y0, sz - 0.45], [w, 4.15, 0.9], "steel")
    sword.box_at((sx, 0.75, sz), (1.3, 1.3, 0.8), "steel", rotation=[0, 0, 45], pivot=[sx, 0.75, sz])   # the point
    err = m.solve_arm(ra, rf, rhand, (sx, 21.0, sz - 0.2))
    # the shield: strapped across the left forearm (arm forward, forearm level, shield hanging from it)
    m.bones[la].rotation = [35, 0, 0]
    m.bones[lf].rotation = [-35, 0, 0]
    CX = 4.0                                                                        # the shield's centre line
    shield = m.bone("shield", [CX, lhand[1] + 2, -7.5], parent=lf, rotation=[-5, 0, 0])
    SW, SH, SZ, SY = 13, 23, -8.6, 5.0
    sxl = CX - SW / 2
    shield.cube([sxl, SY, SZ], [SW, SH, 1.4], "plate")                             # the board: knees to chest
    for i, w in enumerate((10.5, 7.5, 4.5, 1.8)):                                   # stepped point at the top
        shield.cube([CX - w / 2, SY + SH + i * 1.3, SZ], [w, 1.35, 1.4], "plate")
    peak = SY + SH + 4 * 1.3
    for s in (1, -1):                                                               # gold gable bars to the peak
        x0 = CX if s > 0 else CX - 7.6
        shield.cube([x0, peak - 1.0, SZ - 0.25], [7.6, 1.0, 1.9], "gold", rotation=[0, 0, -s * 33], pivot=[CX, peak, SZ + 0.7])
    shield.cube([sxl - 0.4, SY, SZ - 0.25], [1.0, SH + 0.3, 1.9], "gold")           # rims
    shield.cube([sxl + SW - 0.6, SY, SZ - 0.25], [1.0, SH + 0.3, 1.9], "gold")
    shield.cube([sxl - 0.4, SY - 0.4, SZ - 0.25], [SW + 0.8, 1.0, 1.9], "gold")
    shield.cube([CX - 0.6, SY + 3, SZ - 0.5], [1.2, 16, 0.7], "gold")               # the cross
    shield.cube([CX - 4.5, SY + 12.5, SZ - 0.5], [9, 1.2, 0.7], "gold")
    shield.box_at((CX, SY + 13.1, SZ - 0.6), (3.4, 3.4, 0.8), "sun")               # the boss
    rays(shield, (CX, SY + 13.1, SZ - 0.55), 3.0, 8, 1.9, 0.7, 0.5, "sun", upgrade="sun_ward")
    # the left gauntlet shows under the shield's top edge: fine. Feet to the ground.
    m.ground([fl, fr])
    m.pose = {"head": [-4, 0, 0]}
    return m, err


# ------------------------------------------------------------------ 2. RADIANT ----------------------------------
def radiant():
    """The furthest of the three (still 2 of 5 - a person-shaped thing, not a person): 48 px tall, digitigrade
    hint in the legs, narrow waist, four-lame pauldrons, a faceless helm with one glowing slit and a swept crest,
    a cape of light, a large halo, and a greatsword of light held upright in both hands - the vertical signature."""
    m = Model("Paladin - Radiant", "sunforged", "tank (direction 2 of 3)", PALETTE, EMISSIVE)
    HIP, CHEST_H, WAIST_H, DEPTH = 22, 10, 4, 6
    body, chest_y, top = torso(m, HIP, 6, WAIST_H, 10, CHEST_H, DEPTH, chevron=True)
    fauld(body, HIP, 6.5, DEPTH / 2 - 0.5, 3, "plate", flare0=10, flare_step=10, widen=1.6)
    body.cube([-2, 11, -DEPTH / 2 - 0.9], [4, 11, 0.45], "tabard", rotation=[12, 0, 0], pivot=[0, HIP, -DEPTH / 2 - 0.7])
    for s in (1, -1):
        pauldron(body, s, (s * 5.4, top + 0.6, 0), 4, 6.2, 6.6, 8, 12, "plate", trim="gold", thick=1.25)
    # cape of light: three sheets from the shoulders, flaring back
    # one sheet wider than the body, long to the knee, and a brighter inner sheet a hair in front of it
    for i, (w, L, rx, col) in enumerate(((13, 24, -11, "sun"), (10.5, 21, -11, "glow"))):
        z = DEPTH / 2 + 0.9 - i * 0.45
        body.cube([-w / 2, top - 1 - L, z], [w, L, 0.4], col, rotation=[rx, 0, 0], pivot=[0, top - 1, z])
    body.cube([-6.8, top - 2.2, DEPTH / 2 + 0.4], [13.6, 1.4, 1.2], "gold")                                     # the cape's clasp bar
    # head: small, faceless, one glowing slit, swept crest and side fins
    hy, hw, hh, hd = top + 1.2, 6, 7, 7
    head = m.bone("head", [0, hy, 0], parent="body")
    head.cube([-hw / 2, hy, -hd / 2], [hw, hh, hd], "plate")
    head.cube([-hw / 2 + 0.3, hy + 0.6, -hd / 2 - 0.5], [hw - 0.6, hh - 1.2, 0.7], "plate", inflate=0.1)
    head.cube([-0.4, hy + 2.2, -hd / 2 - 0.85], [0.8, 3.4, 0.5], "glow")                                      # the slit
    head.cube([-hw / 2 - 0.2, hy + hh - 1.4, -hd / 2 - 0.3], [hw + 0.4, 1.0, hd + 0.6], "gold")                # brow band
    head.cube([-hw / 2 - 0.1, hy - 1.6, -hd / 2 - 0.3], [hw + 0.2, 2.0, hd + 0.6], "leather")                  # aventail
    head.cube([-0.55, hy + hh, -2.0], [1.1, 1.6, 7.5], "gold", rotation=[-28, 0, 0], pivot=[0, hy + hh, -2.0])   # crest, swept back
    head.cube([-0.4, hy + hh + 1.2, -1.0], [0.8, 1.2, 8.5], "sun", rotation=[-28, 0, 0], pivot=[0, hy + hh, -2.0], upgrade="sun_ward")
    for s in (1, -1):                                                                                          # side fins
        x0 = hw / 2 if s > 0 else -hw / 2 - 0.8
        head.cube([x0, hy + 2.5, 0.5], [0.8, 2.4, 4.0], "gold", rotation=[0, -s * 25, 0], pivot=[x0 + (0 if s > 0 else 0.8), hy + 3.7, 0.5])
    hc = (0, hy + 3.6, 5.8)
    ring(head, hc, 8.4, 8, 1.0, 0.5, "sun")
    ring(head, hc, 5.4, 8, 0.7, 0.4, "glow", upgrade="sun_ward")
    # legs: long, slim, a digitigrade hint (knee forward, ankle back), feet flat and grounded
    fl = leg(m, -1, (-3.0, HIP), 10, 10, 3.8, 4.2, thigh_rot=[16, 0, 0], shin_rot=[-30, 0, 0], foot_rot=[14, 0, 0], foot_len=6.5)
    fr = leg(m, 1, (3.0, HIP), 10, 10, 3.8, 4.2, thigh_rot=[6, 0, 0], shin_rot=[-18, 0, 0], foot_rot=[12, 0, 0], foot_len=6.5)
    # arms and the greatsword of light, upright before the chest, both hands on the grip
    ra, rf, rhand = arm(m, -1, (-6.6, top - 0.4, 0), 8.5, 8.5, 3.2, 3.8, hand=3.4)
    la, lf, lhand = arm(m, 1, (6.6, top - 0.4, 0), 8.5, 8.5, 3.2, 3.8, hand=3.4)
    gx, gz = -3.4, -8.2
    sword = m.bone("sword", [gx, 30, gz], parent="body")
    sword.cube([gx - 0.65, 23.5, gz - 0.65], [1.3, 8.5, 1.3], "leather")           # grip (two hands)
    sword.cube([gx - 1.2, 21.6, gz - 1.2], [2.4, 2.0, 2.4], "gold")                # pommel
    sword.cube([gx - 4.6, 32.0, gz - 0.9], [9.2, 1.5, 1.8], "gold")                # crossguard
    for s in (1, -1):                                                              # guard tips turned up
        x0 = gx + 3.4 if s > 0 else gx - 5.2
        sword.cube([x0, 32.3, gz - 0.7], [1.8, 1.2, 1.4], "gold", rotation=[0, 0, s * 35], pivot=[gx + s * 4.3, 32.9, gz])
    sword.box_at((gx, 32.75, gz - 0.2), (2.0, 2.0, 1.4), "sun", rotation=[0, 0, 45], pivot=[gx, 32.75, gz])   # sun at the ricasso
    blade(sword, (gx, 33.5, gz), 20.5, (3.4, 3.0, 2.5, 1.9), 1.5, "glow", tip_color="sun", fuller="sun")
    e1 = m.solve_arm(la, lf, lhand, (gx, 29.2, gz), elbow_out=1)
    e2 = m.solve_arm(ra, rf, rhand, (gx, 25.7, gz), elbow_out=1)
    m.ground([fl, fr])
    m.pose = {"head": [-3, 0, 0]}
    return m, max(e1, e2)


# ------------------------------------------------------------------ 3. SENTINEL ---------------------------------
def sentinel():
    """The widest (26 px): a walking statue. Short thick legs in a wide stance, a pedestal skirt that steps out,
    cathedral-roof pauldrons, a low chin-forward helm, a warhammer held upright at the right side with a glowing
    head, and a round sun-disc shield (stacked slabs, gold rim, emissive sun with eight rays) on the left."""
    m = Model("Paladin - Sentinel", "sunforged", "tank (direction 3 of 3)", PALETTE, EMISSIVE)
    HIP, CHEST_H, WAIST_H, DEPTH = 18, 10, 4, 7
    body, chest_y, top = torso(m, HIP, 12, WAIST_H, 14, CHEST_H, DEPTH)
    # pedestal skirt: three lames each side, stepping out
    for i in range(3):
        t = HIP - 2.2 * i
        w = 12 + 3 * i
        body.cube([-w / 2, t - 2.6, -DEPTH / 2 - 0.8], [w, 2.6, 0.8], "plate", rotation=[7 + 7 * i, 0, 0], pivot=[0, t, -DEPTH / 2 - 0.4])
        body.cube([-w / 2, t - 2.6, DEPTH / 2], [w, 2.6, 0.8], "plate", rotation=[-(7 + 7 * i), 0, 0], pivot=[0, t, DEPTH / 2 + 0.4])
        for s in (1, -1):
            x0 = 6 + 0.2 * i if s > 0 else -6 - 0.2 * i - 0.8
            body.cube([x0, t - 2.6, -DEPTH / 2 + 0.3], [0.8, 2.6, DEPTH - 0.6], "plate",
                      rotation=[0, 0, -s * (7 + 7 * i)], pivot=[s * 6, t, 0])
    body.cube([-3, 8.5, -DEPTH / 2 - 1.1], [6, 10, 0.5], "tabard", rotation=[10, 0, 0], pivot=[0, HIP, -DEPTH / 2 - 0.85])
    body.cube([-1.1, chest_y + 2.5, -DEPTH / 2 - 0.9], [2.2, 5.5, 0.4], "gold")                                   # chest keystone
    body.cube([-3.6, chest_y + 5.2, -DEPTH / 2 - 0.9], [7.2, 1.2, 0.4], "gold")
    for s in (1, -1):
        pauldron(body, s, (s * 7.4, top + 0.6, 0), 2, 7.5, 8.0, 18, 18, "plate", trim="gold", thick=1.7, upgrade_spike="sun_ward")
    # head: low and forward, horizontal visor, brow ridge
    hy, hw, hh, hd = top - 0.4, 8, 7, 9
    head = m.bone("head", [0, hy, 0], parent="body")
    head.cube([-hw / 2, hy, -hd / 2 - 0.6], [hw, hh, hd], "plate")
    head.cube([-hw / 2 + 0.4, hy + 0.8, -hd / 2 - 1.15], [hw - 0.8, hh - 1.6, 0.7], "plate", inflate=0.1)
    head.cube([-3, hy + 3.4, -hd / 2 - 1.45], [6, 0.9, 0.5], "dark")                                              # visor
    head.cube([-hw / 2 - 0.2, hy + hh - 1.4, -hd / 2 - 0.9], [hw + 0.4, 1.1, hd + 0.6], "gold")                   # brow
    head.cube([-0.7, hy + hh, -hd / 2 + 0.6], [1.4, 1.6, hd - 1.2], "gold")                                       # ridge
    head.box_at((0, hy + hh + 2.0, -0.6), (2.2, 2.2, 1.6), "sun", rotation=[0, 0, 45], pivot=[0, hy + hh + 2.0, -0.6])
    # legs: short, thick, wide stance
    fl = leg(m, -1, (-5.0, HIP), 8, 8, 5.4, 6, thigh_rot=[0, 0, -7], shin_rot=[0, 0, 7], toe="gold", sabaton_h=2.6)
    fr = leg(m, 1, (5.0, HIP), 8, 8, 5.4, 6, thigh_rot=[0, 0, 7], shin_rot=[0, 0, -7], toe="gold", sabaton_h=2.6)
    # arms
    ra, rf, rhand = arm(m, -1, (-9.3, top - 0.8, 0), 8, 8, 4.2, 4.8, hand=4.0)
    la, lf, lhand = arm(m, 1, (9.3, top - 0.8, 0), 8, 8, 4.2, 4.8, hand=4.0)
    # the warhammer, upright at the right side, head at shoulder height; the right hand holds the haft
    m.bones[ra].rotation = [4, 0, -15]
    m.bones[rf].rotation = [-6, 0, 0]
    hx, hy_, hz = m.fk(rf, rhand)
    hammer = m.bone("hammer", [round(hx, 2), round(hy_, 2), round(hz, 2)], parent=rf)
    # the haft runs through the hand (rest pose coordinates of the forearm bone: the hand's rest centre)
    cx, cy, cz = rhand
    hammer.cube([cx - 0.65, cy - 8, cz - 0.65], [1.3, 30, 1.3], "leather")
    hammer.cube([cx - 0.9, cy - 8.6, cz - 0.9], [1.8, 1.4, 1.8], "gold")                                          # butt cap
    hy0 = cy + 16.5
    hammer.cube([cx - 4.2, hy0, cz - 2.7], [7.4, 7.0, 5.4], "steel")                                              # the head
    hammer.cube([cx - 4.4, hy0 + 2.6, cz - 2.9], [7.8, 1.6, 5.8], "gold")                                          # band
    hammer.cube([cx - 4.5, hy0 + 0.5, cz - 2.9], [7.9, 0.9, 5.8], "sun", upgrade="sun_ward")                        # glow bands
    hammer.cube([cx - 4.5, hy0 + 5.4, cz - 2.9], [7.9, 0.9, 5.8], "sun", upgrade="sun_ward")
    hammer.box_at((cx - 4.3, hy0 + 3.5, cz), (0.6, 3.2, 3.2), "sun")                                              # sun on the striking face
    hammer.cube([cx + 3.2, hy0 + 1.2, cz - 1.2], [3.0, 4.6, 2.4], "steel", rotation=[0, 0, -8], pivot=[cx + 3.2, hy0 + 3.5, cz])  # back spike
    # the sun-disc shield, strapped across the left forearm
    m.bones[la].rotation = [35, 0, 0]
    m.bones[lf].rotation = [-35, 0, 0]
    shield = m.bone("shield", [1.5, lhand[1] + 2, -8.2], parent=lf, rotation=[-4, 0, 0])
    dc = (1.5, 19.5, -9.6)
    disc(shield, dc, (6, 10, 13, 15, 16, 15, 13, 10, 6), 2.0, 1.2, "gold", rim="plate")
    shield.box_at((dc[0], dc[1], dc[2] - 0.5), (5.2, 5.2, 0.8), "sun", rotation=[0, 0, 45], pivot=[dc[0], dc[1], dc[2] - 0.5])
    rays(shield, (dc[0], dc[1], dc[2] - 0.45), 5.2, 8, 3.2, 1.0, 0.6, "sun")
    m.ground([fl, fr])
    m.pose = {"head": [2, 0, 0]}
    return m, 0.0


MODELS = {
    "sunforged_paladin_templar": templar,
    "sunforged_paladin_radiant": radiant,
    "sunforged_paladin_sentinel": sentinel,
}


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
        print(f"wrote {path}: {len(m.bones)} bones, {nc} cubes, hand error {err:.2f} px")


if __name__ == "__main__":
    main()
