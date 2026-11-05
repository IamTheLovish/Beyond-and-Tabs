#!/usr/bin/env python3
"""The Tithe Binder's key poses as data (design sheet .scratch/unit1/design-sheet.md section 5), for the hand-written
model subclass to match and for tools/game_preview.py / tools/contact_check.py to draw and check before any Java.

Every pose is {bone: [x, y, z]} in DSL degrees ADDED to the model's rest rotations (what game_preview --poses takes),
plus "_move": {bone: [dx, dy, dz] DSL px} (a part offset in its parent's frame: the hover bob, the mantle lift) and
"_hide": [bones]. The sheet gives most angles as ABSOLUTE targets ("right_arm x 20 -> +200"); this module reads the
rest pose from tools/gen_tithe_binder.py and converts, so the numbers below read like the sheet.

DSL signs (design/models/README.md), checked on the stills: on a hanging part +x swings it FORWARD (-z), on a standing
part (body, chest, head) +x tips it BACK; +y turns the front toward -x (the unit's right). On the RIGHT arm a hanging
hand moves inward (+x) with +z - but once the arm is raised past horizontal the same +z swings it OUTWARD. So the
sheet's "z -40" on the over-head sling (and the rest "z -12" carried into the over-head throw) would put the elbow into
the hood; the raised arms below use the outward sign. Trailing cloth and chains (move) swing BACK, which is -x (the
sheet writes +35 / +30).

The chains are springs in the game; here each takes the angle its spring would settle to: hanging plumb (lantern
chain, shackle chains) unless that puts it into the body, then the nearest free swing (`_free`); the
hem chains rest on the skirt's flare, never through it. The wheel's yaw in a still is its free spin phase: the arms
reach the wheel's planes in several poses, so each pose names the phase it is drawn at (`ring` y).

    python tools/tithe_binder_poses.py > p.json                     # all key poses, N = 10
    python tools/tithe_binder_poses.py --weapons 5 --only rest,volley_windup > p.json
    python tools/game_preview.py design/models/gravebound_tithe_binder.json --team tabard,tabard_dark \\
        --poses @p.json --views quarter,rts --translucent "weapon_2,...,weapon_10:0.55" --out strip.png
"""
import argparse
import json
import math
import os
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_tithe_binder as G  # noqa: E402
import render_concept  # noqa: E402
import contact_check  # noqa: E402
from gen_models import rot_mat, mat_mul, transpose, euler  # noqa: E402

ORDER = ["rest", "idle_peak", "move", "volley_windup", "volley_strike", "bind_receive", "hit_react",
         "death_t6", "death_t30", "hook_windup"]

TATTERS = ("tatter_f", "tatter_b", "tatter_l", "tatter_r")
# where a free chain may swing to, in order of preference (world rotations about x then z, degrees)
SWINGS = [(0, 0), (12, 0), (-12, 0), (0, 12), (0, -12), (24, 0), (-24, 0), (0, 24), (0, -24), (36, 0), (-36, 0),
          (12, 12), (12, -12), (-12, 12), (-12, -12), (48, 0), (0, 36), (0, -36)]


class Rig:
    def __init__(self, n):
        self.m = G.build(n)
        self.phases = {}
        fd, path = tempfile.mkstemp(suffix=".json")
        with os.fdopen(fd, "w") as f:
            json.dump(self.m.to_json(), f)
        try:
            self.j = render_concept.load_model(path)
        finally:
            os.remove(path)

    def rest(self, bone):
        return self.m.bones[bone].rotation

    def absolute(self, bone, target):
        """A delta that brings `bone` to the ABSOLUTE DSL rotation `target` (None keeps an axis at rest)."""
        rest = self.rest(bone)
        return [0.0 if t is None else round(t - rest[a], 3) for a, t in enumerate(target)]

    def set(self, pose):
        self.m.pose = {k: list(v) for k, v in pose.items() if not k.startswith("_")}

    def world(self, pose, bone, R):
        """The delta that gives `bone` the WORLD rotation matrix R, every other bone of `pose` applied."""
        self.set(pose)
        target = euler(mat_mul(transpose(self.m.frame(self.m.bones[bone].parent)), R))
        rest = self.rest(bone)
        return [round(target[a] - rest[a], 3) for a in range(3)]

    def depth(self, pose, bones):
        """The deepest interpenetration (px, over 0.6) between any of `bones` and anything else, in `pose`."""
        extra = {k: v for k, v in pose.items() if not k.startswith("_")}
        hits = contact_check.check(self.j, extra, pose.get("_hide", ()), pose.get("_move", {}))
        d = [h[0] for h in hits if h[1] in bones or h[4] in bones]
        return max(d) if d else 0.0

    def phase(self, name, pose, ks, layout, hide_first=False):
        """The wheel's spin phase for a still: the ring yaw (added to the pose's own, in 6-degree steps over one
        72-degree spoke period) where the arms and head pass between the spokes. Records how many phases are clean
        (self.phases[name] = (yaw, clean, tried, worst px)), so a still is never the only clean moment unreported."""
        weapons = tuple(b for b in self.m.bones if b.startswith("weapon_") or b.startswith("slot_"))
        base = list(pose.get("ring", [0, 0, 0]))
        best, clean, worst = None, 0, 0.0
        for dy in [0, 6, -6, 12, -12, 18, -18, 24, -24, 30, -30, 36]:
            trial = dict(pose)
            trial["ring"] = [base[0], base[1] + dy, base[2]]
            if hide_first:
                trial["_hide"] = list(pose.get("_hide", [])) + ["weapon_%d" % self.first(ks, layout, base[1] + dy)]
            d = self.depth(trial, weapons)
            clean += d == 0.0
            worst = max(worst, d)
            if best is None or d < best[0] - 1e-6:
                best = (d, trial, base[1] + dy)
        self.phases[name] = (best[2], clean, 12, round(worst, 2), round(best[0], 2))
        pose.clear()
        pose.update(best[1])
        return pose

    @staticmethod
    def first(ks, layout, ring_yaw):
        """The volley's first weapon: the spoke pointing nearest the target bearing (straight ahead, yaw 0)."""
        return min(ks, key=lambda k: abs(((layout[k] + ring_yaw + 180) % 360) - 180))

    def low(self, pose, bones=None, skip=()):
        """The lowest posed point (DSL y, the hover _move included) of `bones` (default: every bone not hidden and
        not under a name in `skip`) - for landing the crumple on the ground."""
        self.set(pose)
        dy = pose.get("_move", {}).get("hover", [0, 0, 0])[1]
        hidden = set(pose.get("_hide", ()))
        out = 1e9
        for b in self.m.bones.values():
            chain, x = [], b
            while x is not None:
                chain.append(x.name)
                x = self.m.bones[x.parent] if x.parent else None
            if hidden & set(chain) or (bones is not None and b.name not in bones) or any(
                    c.startswith(s) for c in chain for s in skip):
                continue
            for c in b.cubes:
                if not c.get("upgrade"):
                    out = min(out, min(p[1] for p in self.m.cube_corners(b.name, c)) + dy)
        return out

    def free(self, pose, bone, kids=(), base=(0, 0)):
        """Hangs a chain: plumb (world), else the first swing in SWINGS (added to `base`) that is clear of the body."""
        best = None
        for (sx, sz) in SWINGS:
            R = mat_mul(rot_mat(0, 0, base[1] + sz), rot_mat(base[0] + sx, 0, 0))
            trial = dict(pose)
            trial[bone] = self.world(pose, bone, R)
            for k in kids:
                trial[k] = self.world(trial, k, R)
            d = self.depth(trial, (bone,) + tuple(kids))
            d += max(0.0, -self.low(trial, (bone,) + tuple(kids)))      # never through the ground
            if best is None or d < best[0] - 1e-6:
                best = (d, trial)
            if d == 0.0:
                break
        pose.update(best[1])
        return pose

    def settle(self, pose, trail=None):
        """The chains of a pose take their settled angles (see the module docstring). trail: {chain: (x, z)} world
        degrees a chain swings off plumb (the back hem chain trailing in the move)."""
        trail = trail or {}
        hidden = set(pose.get("_hide", ()))
        # (r2: the flail's head lies along its haft while docked, so it is no longer hung plumb here)
        for b in ("lantern_chain", "right_shackle_chain", "left_shackle_chain"):
            if b in self.m.bones and b not in pose and b not in hidden:
                self.free(pose, b, base=trail.get(b, (0, 0)))
        for k in (1, 2, 3):
            a, bb = "hem_chain_%d" % k, "hem_chain_%d_b" % k
            if a in pose:
                continue
            out = -1 if k < 3 else 1
            bx, bz = trail.get(a, (0, 0))
            d = self.world(pose, a, mat_mul(rot_mat(0, 0, bz), rot_mat(bx, 0, 0)))
            rel = self.rest(a)[0] + d[0]
            # rest on the flare: a front chain keeps at least its rest swing forward, the back chain backward
            rel = max(rel, self.rest(a)[0]) if out < 0 else min(rel, self.rest(a)[0])
            d = [round(rel - self.rest(a)[0], 3), d[1], d[2]]
            pose[a] = d
            self.free(pose, bb, base=(bx, bz))
        return pose


def _hook_in_hand(rig, pose, side="right"):
    """Moves slot_1 (the Grave Hook) into the hand for the Grave Hook ability: the haft through the closed fingers,
    across the palm (the fist's own x), the pommel out of the little-finger side, the blade out of the thumb side.
    slot_1's _move and rotation are expressed in the ring's posed frame, so the ring may be at any yaw."""
    m = rig.m
    for k in ("slot_1", "weapon_1"):
        pose.pop(k, None)
    if "_move" in pose:
        pose["_move"] = {k: v for k, v in pose["_move"].items() if k != "slot_1"}
    rig.set(pose)
    hb = m.bones[side + "_hand"]
    grip = m.fk(hb.name, [hb.pivot[0], hb.pivot[1] - 3.0, 0.0])              # through the fingers' middle
    F = m.frame(hb.name)
    xl = tuple(F[i][0] for i in range(3))
    yl = tuple(F[i][1] for i in range(3))
    thumb = 1.0 if side == "right" else -1.0                    # the thumb is on the inner side: +x for the right hand
    ez = tuple(-thumb * v for v in xl)                          # the blade (local -z) leaves by the thumb
    ey = yl
    ex = tuple(ey[(i + 1) % 3] * ez[(i + 2) % 3] - ey[(i + 2) % 3] * ez[(i + 1) % 3] for i in range(3))
    Rt = tuple(tuple((ex, ey, ez)[j][i] for j in range(3)) for i in range(3))   # columns ex, ey, ez
    pommel = [grip[a] + ez[a] * 2.4 for a in range(3)]          # 2.4 px down the haft from the fist's centre
    Rr = m.frame("ring")                                        # the ring hangs from the hover root (no _move here)
    rp = m.bones["ring"].pivot
    local = [sum(Rr[k][i] * (pommel[k] - rp[k]) for k in range(3)) for i in range(3)]   # Rr^T (pommel - ring pivot)
    slot = m.bones["slot_1"]
    pose["_move"] = dict(pose.get("_move", {}))
    pose["_move"]["slot_1"] = [round(local[a] - (slot.pivot[a] - rp[a]), 3) for a in range(3)]
    rot = euler(mat_mul(transpose(Rr), Rt))
    pose["slot_1"] = [round(rot[a] - slot.rotation[a], 3) for a in range(3)]
    pose["weapon_1"] = [round(-v, 3) for v in m.bones["weapon_1"].rotation]
    return pose


def key_poses(n=10):
    rig = Rig(n)
    m = rig.m
    A = rig.absolute
    ks = sorted(int(b[7:]) for b in m.bones if b.startswith("weapon_") and b[7:].isdigit())
    layout = {k: yaw for (k, _t, yaw, _p, _pt) in G.slot_layout(n)}
    P = {"rest": {}}

    # idle bob peak (5.1 at t = 15 of the 60-tick bob): hover +2, breath in, the conductor's weave
    t = 15.0
    p = {"body": [round(2 * math.sin(2 * math.pi * t / 80 + 0.5), 2), 0, 0],
         "chest": [round(3 * math.sin(2 * math.pi * t / 80), 2), 0, 0],
         "head": [0, round(10 * math.sin(2 * math.pi * t / 160), 2), 0],
         "right_arm": [round(5 * math.sin(2 * math.pi * t / 60 + math.pi / 2), 2), 0,
                       round(3 * math.sin(2 * math.pi * t / 60), 2)],
         # the opposite phase: as the right hand closes in, the left opens out (+z on the left arm is outward)
         "left_arm": [round(5 * math.sin(2 * math.pi * t / 60 + math.pi / 2 + math.pi), 2), 0,
                      round(3 * math.sin(2 * math.pi * t / 60), 2)],
         "right_hand": [12, 0, 0], "left_hand": [12, 0, 0],
         "ring": [0, 9, 0],
         "_move": {"hover": [0, 2.0, 0], "mantle": [0, 0.5, 0]}}
    for i, tb in enumerate(TATTERS):
        p[tb] = [round(4 * math.sin(2 * math.pi * t / 30 + i * math.pi / 2), 2), 0, 0]
    for k in ks:
        ph = 2 * math.pi * t / 40 + math.radians(k * 36)
        p["weapon_%d" % k] = [round(3 * math.sin(ph), 2), 0, round(8 * math.sin(ph + 1.0), 2)]
    P["idle_peak"] = rig.settle(rig.phase("idle_peak", p, ks, layout))

    # move at full speed (5.2): the lean, arms swept back, cloth and the back chain trailing (trailing = -x)
    p = {"body": A("body", [-14, 0, 0]), "head": [10, 0, 0],
         "right_arm": A("right_arm", [-20, None, None]), "left_arm": A("left_arm", [-20, None, None]),
         "right_forearm": A("right_forearm", [10, 0, 0]), "left_forearm": A("left_forearm", [10, 0, 0]),
         "right_hand": [-15, 0, 0], "left_hand": [-15, 0, 0],
         "cape": [-30, 0, 0], "ring": [-8, 0, 0],
         "_move": {"hover": [0, -2.0, 0]}}
    for tb in TATTERS:
        p[tb] = [-35, 0, 0]
    for k in ks:
        s = math.sin(math.radians(layout[k]))
        # each weapon's tip lags 10 toward the back (+z): +y turns -z toward -x, so the sign follows the slot's side
        p["weapon_%d" % k] = [0, round(10 * (1 if s > 0.05 else -1 if s < -0.05 else 0), 2), 0]
    P["move"] = rig.settle(rig.phase("move", p, ks, layout), trail={"hem_chain_3": (-30, 0), "lantern_chain": (-10, 0),
                                     "right_shackle_chain": (-20, 0), "left_shackle_chain": (-20, 0)})

    # volley wind-up extreme (5.3, t11-13 hold): the right arm UP and BEHIND the head, the left hand aimed at the
    # target (ahead), every weapon's tip cocked up 25, mantle +1. r2 (the review: wind-up, strike and bind looked
    # alike from 52 deg - only thin arms moved): the MASS winds up - the body twists 30 to its right and leans back,
    # the whole Binder draws back 3 px and up 3, the ring is pulled back 4 px and cocked (its front edge up 12; the
    # sheet's dip toward the target moves to the strike)
    windup = {"body": A("body", [10, 30, 0]), "head": [-20, -10, 0],
              "right_arm": A("right_arm", [200, 0, 30]), "right_forearm": A("right_forearm", [60, 0, 0]),
              "right_hand": [20, 0, 0],
              "left_arm": A("left_arm", [84, 10, 12]), "left_forearm": A("left_forearm", [5, 0, 0]),
              "ring": [12, 0, 0], "cape": [-6, 0, 0],
              "_move": {"hover": [0, 3.0, 3.0], "ring": [0, 1.0, 4.0], "mantle": [0, 1.0, 0]}}
    for tb in TATTERS:
        windup[tb] = [-8, 0, 0]
    for k in ks:
        windup["weapon_%d" % k] = [25, 0, 0]
    windup = rig.phase("volley_windup", windup, ks, layout)
    P["volley_windup"] = rig.settle(dict(windup), trail={"lantern_chain": (12, 0)})

    # volley strike (t15): the whip forward, the body pitched -15 and untwisted through to -20, the lunge (4 px
    # forward, 1 down); the ring snaps toward the target (5 px forward, its front edge dipped 18); the first weapon of
    # the volley (the spoke nearest the target bearing) has left its slot
    p = {"body": A("body", [-15, -20, 0]), "head": [0, 15, 0],
         "right_arm": A("right_arm", [60, 0, -12]), "right_forearm": A("right_forearm", [10, 0, 0]),
         "left_arm": windup["left_arm"], "left_forearm": windup["left_forearm"],
         "ring": [-18, windup["ring"][1], 0], "cape": [-12, 0, 0],
         "_move": {"hover": [0, -1.0, -4.0], "ring": [0, -2.0, -5.0]}}
    for tb in ("tatter_f", "tatter_l", "tatter_r"):
        p[tb] = [-15, 0, 0]
    p["tatter_b"] = [-4, 0, 0]
    for k in ks:
        p["weapon_%d" % k] = [10, 0, 0]
    P["volley_strike"] = rig.settle(rig.phase("volley_strike", p, ks, layout, hide_first=True))

    # bind receive (5.4, t10), the corpse at the front-right: r2 - the whole body turns 25 to the corpse and leans
    # back 8 (receiving), the head tilted back and turned further, the left arm opened back and out; the ring tilts
    # down toward the corpse (its right side dips 10) and leans 2 px toward it; hover +2
    p = {"body": [8, 25, 0], "head": [16, 12, 0], "chest": [4, 0, 0], "left_arm": A("left_arm", [-40, 0, 36]),
         "left_forearm": A("left_forearm", [20, 0, 0]), "left_hand": [-10, 0, 0], "cape": [-8, 0, 0],
         "ring": [0, 0, 10],
         "_move": {"hover": [0, 2.0, 0], "ring": [-2.0, 0, -2.0], "mantle": [0, 1.0, 0]}}
    P["bind_receive"] = rig.settle(rig.phase("bind_receive", p, ks, layout), trail={"lantern_chain": (12, 0)})

    # hit react (5.5, t1, a hit from the front): body back 12, head 15, hover down 2, the wheel kicked away from
    # the hit (its front up 10), every docked weapon rattling (roll 15, alternating), the cloth lagging (inertia)
    p = {"body": [12, 0, 0], "head": [15, 0, 0], "ring": [10, 0, 0],
         "_move": {"hover": [0, -2.0, 0]}}
    for tb in TATTERS:
        p[tb] = [-8, 0, 0]
    for k in ks:
        p["weapon_%d" % k] = [0, 0, 15 if k % 2 else -15]
    P["hit_react"] = rig.settle(rig.phase("hit_react", p, ks, layout), trail={"lantern_chain": (12, 0)})

    # death t6 (5.6, the held jolt): head back 35, both arms flung out to 70, the body arched back
    p = {"head": [35, 0, 0], "body": [8, 0, 0], "chest": [6, 0, 0],
         "right_arm": A("right_arm", [None, None, -70]), "left_arm": A("left_arm", [None, None, 70]),
         "right_forearm": A("right_forearm", [10, 0, 0]), "left_forearm": A("left_forearm", [10, 0, 0]),
         }
    for tb in TATTERS:
        p[tb] = [-6, 0, 0]
    P["death_t6"] = rig.settle(rig.phase("death_t6", p, ks, layout), trail={"lantern_chain": (14, 0)})

    # death t30 (the crumple): fallen 5 px onto the ground, folded forward into the robe, the tatters spread on the
    # ground, the arms hanging limp (plumb), every weapon gone (shattered)
    # r6: a sideways slump with less fold (the r2-r5 -30 / -40 fold read as a red crate and sank the chest into the
    # waist) - the driver's death clip ends here too
    p = {"body": A("body", [-24, 0, 18]), "chest": [-18, 0, 0], "head": [-21, 0, 0],
         "tatter_f": [80, 0, 0], "tatter_b": [-80, 0, 0], "tatter_l": [0, 0, 80], "tatter_r": [0, 0, -80],
         "right_forearm": A("right_forearm", [0, 0, 0]), "left_forearm": A("left_forearm", [0, 0, 0]),
         "_move": {"hover": [0, -5.0, 0]}, "_hide": ["ring"]}
    p["right_arm"] = [30, 0, 5]          # r6: limp along the body (plumb arms swung into the chest of the slump)
    p["left_arm"] = [30, 0, 5]
    # landed: the lowest cloth / bone touches y = 0 (the sheet's "falls 5 px"; the forward fold sets the exact drop)
    p["_move"]["hover"][1] = 0.0
    p["_move"]["hover"][1] = round(-rig.low(p, skip=("hem_chain",)), 2)
    P["death_t30"] = rig.settle(p)

    # Grave Hook wind-up (5.7, t10): the hook in the right fist, the arm up and OUT to the side (a side-arm sling),
    # the body twisted 35 away from the target, the head held on it, the left hand pointing
    p = {"body": [0, 35, 0], "head": [-6, -30, 0],
         "right_arm": A("right_arm", [180, 0, 56]), "right_forearm": A("right_forearm", [30, 0, 0]),
         "left_arm": A("left_arm", [70, -25, 12]), "left_forearm": A("left_forearm", [10, 0, 0])}
    # the wheel's phase with the hook re-placed in the fist at every trial yaw (slot_1 rides the ring)
    weapons = tuple(b for b in m.bones if b.startswith("weapon_") or b.startswith("slot_"))
    best, clean, worst = None, 0, 0.0
    for dy in [0, 6, -6, 12, -12, 18, -18, 24, -24, 30, -30, 36]:
        trial = _hook_in_hand(rig, dict(p, ring=[0, dy, 0]))
        d = rig.depth(trial, weapons)
        clean += d == 0.0
        worst = max(worst, d)
        if best is None or d < best[0] - 1e-6:
            best = (d, trial, dy)
    rig.phases["hook_windup"] = (best[2], clean, 12, round(worst, 2), round(best[0], 2))
    P["hook_windup"] = rig.settle(best[1])
    return {k: P[k] for k in ORDER}, rig


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--weapons", type=int, default=10)
    ap.add_argument("--only", default="", help="comma list of pose names")
    ap.add_argument("--report", action="store_true", help="print the wheel-phase search per pose to stderr")
    a = ap.parse_args()
    P, rig = key_poses(a.weapons)
    if a.report:
        for k, (yaw, clean, tried, worst, got) in rig.phases.items():
            sys.stderr.write("phase %-14s ring yaw %+4d: %2d of %d spin phases clean (worst %.2f px, drawn %.2f px)\n"
                             % (k, yaw, clean, tried, worst, got))
    if a.only:
        P = {k: P[k] for k in a.only.split(",")}
    json.dump(P, sys.stdout, indent=1)
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()
