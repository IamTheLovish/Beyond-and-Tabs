#!/usr/bin/env python3
"""ONE articulated dragon rig for every winged wyrm (lovish, Oct 21: animations "realistic, alive, and detailed";
dragons should "look like they're breathing and move articulated"), and the first body on it: the Gravebound Bone
Dragon. Our own model - nothing copied from another mod, a vanilla phantom or a game.

The rig (DSL bones; the same names on every dragon, so unit/modelling/animations/Dragon.java animates them all - the
Celestial and Thunder dragons join by building their own body on these bones):

    body                                   the thorax; the root everything hangs from (rises on the downstroke, banks)
      ribs                                 the ribcage alone: swells with the breath without scaling the neck or tail
      neck_1 -> neck_2 -> ... -> neck_5    a chain of five; a wave rolls root to tip, the head counters it
        -> head: jaw, horn_left, horn_right, (crest, ear_left, ear_right: a body carries what it has)
      spine_1 -> spine_2                   the lumbar and the pelvis
        spine_2 -> tail_1 -> ... -> tail_7 a tapering chain of seven, the beat and the sway lag down it
      front_left_upper -> front_left_lower -> front_left_foot       shoulder -> elbow -> wrist, claws on the foot
      front_right_upper -> ...                                      (upper bones hang from body)
      spine_2/hind_left_upper -> hind_left_lower -> hind_left_foot  hip -> knee -> ankle
      spine_2/hind_right_upper -> ...
      wing_left_arm -> wing_left_fore -> wing_left_hand             humerus -> radius -> the hand and its fingers
        wing_left_arm/mem_left_1, wing_left_fore/mem_left_2, wing_left_hand/mem_left_3   membrane panels: fold in stages
      wing_right_arm -> ...

Proportions are a dict (P) so the next dragon changes numbers, not structure. Coordinates: DSL px (16 = 1 block), y up,
the unit faces -z, +x its left (design/models/README.md), its left wing along +x. The Bone Dragon is built at 1x and
drawn x1.4 (BoneDragonRenderer.DRAW_SCALE): a wingspan of ~110 px, nose to tail tip ~150 px, so ~10 blocks across in
the world as the old phantom body was. The body hangs round y 13 (the hitbox's middle at that scale), the legs
tucked under it.

    python3 tools/gen_dragon.py && python3 tools/dsl_to_game.py design/models/gravebound_bone_dragon.json \\
        --class BoneDragonModel --texture bone_dragon_unit --team tabard,tabard_dark
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_models import Model, OUT, slab  # noqa: E402

PALETTE = {
    # r9 (the round-8 review: "beige stone brick", "horns = wood"): smooth ivory, the recesses a warm grey-ivory, no
    # brown; the horns bone with a dark tip only
    "bone": "#e4ddc8", "bone_dark": "#b9b09a", "bone_pale": "#fbf8ee", "bone_old": "#c6bca3",   # r13: knuckles darker, ridges paler
    "tooth": "#f6f2e6", "claw": "#4a443b", "horn": "#dad3be",
    "deepslate": "#4f5055", "deepslate_dark": "#35363b", "blackstone": "#2a2729", "blackstone_edge": "#4b4448",
    # the sail: a saturated dusky lilac (r8's grey-pink dissolved into grass and terracotta from above)
    "membrane": "#73579a", "membrane_dark": "#4f3a6e", "membrane_hem": "#9078b2",   # r11: darker, the sail one dark shape
    # r13: the chord shades dark by the bone -> mid -> a paler, thinner hem (only "_hem" strips get the torn edge and
    # its back-lit rim: tools/materials.py style "hem"), so the sail has facets and an outline at night
    # r10: the spine's thread is its own DIM colour (soul_thread: a desaturated soul-teal, luminance 121 vs
    # soul_glow's 209 = 58%, LabPBR emission 130 not 220 = 59%; the two multiply under a shader's bloom to ~35% of
    # r9's - tools/materials.py "emissive_dim"): under Complementary r9's thread bloomed into a white-cyan neon tube
    # that owned the silhouette from above. The eyes and the ribcage core keep soul_glow
    # r11: ONLY the eye embers are in the _glow pass (EMISSIVE). The thread (and the heart) are painted in the BASE
    # pass alone: lit by the world, LabPBR emission 60 (materials.py emissive_dim). r9 and r10 both painted the thread
    # into _glow.png too, and GeneratedBodies.glow draws that atlas with RenderType.eyes - full-bright, ADDITIVE, on
    # top of the lit base texel, and no _s map of its own: the sum base (43,132,117) + glow (47,143,127) is (90,255,244),
    # the same saturated cyan as r9's, whatever the palette says, and Complementary blooms the eyes program at full
    # strength regardless of the _s alpha (which only the base pass carries). Dimming the texel twice could never fix
    # a pass that adds it to itself full-bright: tools/dsl_to_game.py's glow dump and the r10 tour stills agree
    "soul_glow": "#62f3cb", "soul_core": "#34dba3", "soul_thread": "#2f8f7f", "soul_dim": "#1b6d53", "socket": "#15120f",
    "tabard": "#2f5fb3", "tabard_dark": "#244a8c",
}
EMISSIVE = ["soul_glow"]

# the Bone Dragon's proportions (px at 1x): a long wyrm - a deep ribcage, a five-link neck, a seven-link tail, big
# bat-like wings, the legs light and tucked
DRAGON = dict(
    body_w=13.0, body_h=12.0, body_z0=-13.0, body_z1=7.0, body_y=13.0,     # the thorax barrel
    lumbar_z1=16.0, lumbar_w=10.0, lumbar_h=9.5,                            # spine_1
    pelvis_z1=24.0, pelvis_w=9.5, pelvis_h=8.5,                             # spine_2
    # r13 (lovish, Oct 22: the neck "a stiff straight rod"): more, shorter links so the neck and the tail CURVE - 8 in
    # the neck (r12: 5 x 7 px), 10 in the tail (r12: 7 x 8 px), about the same lengths; the neck a little thinner
    tail_n=10, tail_seg=5.8, tail_w0=5.6, tail_w1=1.4,
    neck_n=8, neck_seg=4.7, neck_w0=6.2, neck_w1=4.0, neck_y=15.5,
    head_len=14.0, head_w=9.2, head_h=6.8, jaw_len=14.0,   # r13: a touch bigger - the head must read from above
    shoulder=(6.5, 9.5, -6.5), f_upper=9.0, f_lower=8.0, f_foot=5.0, f_w=3.0,
    hip=(5.0, 8.5, 20.0), h_upper=9.5, h_lower=8.5, h_foot=6.0, h_w=3.4,
    # r13 (the r12 wing read as a plank with a rake at its end): a bat's proportions - a short humerus, a long forearm,
    # LONG fingers that carry most of the sail; the chord at the elbow reaches back to the hip
    # the leading edge a shallow M from above (the elbow swept back, the wrist forward of it) instead of r12's straight
    # rod - the outline every reference wing has
    wing_root=(5.5, 17.5, -5.0), arm=15.0, fore=22.0, hand=34.0,
    arm_rest=(0, -28, 8), fore_rest=(0, 48, -5), hand_rest=(0, -8, -4),   # the left wing's rest rotations
    sag=11.0,                                                              # the sail's hang below its bones at rest, degrees per strip
)

# the neck's S-curve and the tail's droop at rest (degrees about x per link; x > 0 lifts a forward part's nose and
# drops a backward part's tip)
NECK_REST = [24, 18, 11, 2, -7, -14, -17, -15]   # r13: the same S over eight links - up out of the chest, over, down to the head
HEAD_REST = -2                          # the head carried level (the links sum to 2)
TAIL_REST = [7, 6, 4, 3, 1, 0, -2, -3, -4, -4]
# the legs tucked under the body in flight: shoulder back, elbow folded, wrist dropped; hip forward, knee folded
FRONT_REST = (-70, 160, 10)              # the thigh back ~65, the shin folded flat under it, the foot against the belly
HIND_REST = (-70, -20, 80)               # the hip forward, the shin folded back flat, the foot drawn up under the tail base


def barrel(bone, cy, z0, z1, w, h, color, k=3, cx=0.0, **kw):
    """A box lying along z with ROUND edges: k nested boxes whose corners lie on the ellipse w x h (gen_quadruped)."""
    A, B = w / 2, h / 2
    s = 1.0 / math.cos(0.5 / k * math.pi / 2)
    for i in range(k):
        t = (i + 0.5) / k * math.pi / 2
        hw, hh = A * s * math.cos(t), B * s * math.sin(t)
        bone.cube([cx - hw, cy - hh, z0], [2 * hw, 2 * hh, z1 - z0], color, **kw)


def bar(bone, p0, p1, w, color, plane="xy", ext=0.0, **kw):
    """A straight bone between two points as one rotated box: in the x-y plane (a rib, rotated about z), the y-z
    plane (a leg link seen from the side, about x) or the x-z plane (a wing finger, about y)."""
    x0, y0, z0 = p0
    x1, y1, z1 = p1
    c = [(x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2]
    if plane == "xy":
        L = math.hypot(x1 - x0, y1 - y0) + ext
        a = math.degrees(math.atan2(y1 - y0, x1 - x0))
        return bone.box_at(c, [L, w, w], color, rotation=[0, 0, a], pivot=list(c), **kw)
    if plane == "yz":
        L = math.hypot(y1 - y0, z1 - z0) + ext
        a = math.degrees(math.atan2(z1 - z0, y1 - y0))          # 0 = straight up; x > 0 swings a hanging part forward
        return bone.box_at(c, [w, L, w], color, rotation=[-a, 0, 0], pivot=list(c), **kw)
    L = math.hypot(x1 - x0, z1 - z0) + ext
    a = math.degrees(math.atan2(-(z1 - z0), x1 - x0))           # about y: + turns a +x part toward -z
    return bone.box_at(c, [L, w, w], color, rotation=[0, a, 0], pivot=list(c), **kw)


def thread(bone, c, L, color="soul_thread", tilt=0.0):
    """A thin strip of DIM light along z centred on c (r10: soul_thread, not soul_glow - the r9 review: a neon tube
    under a shader's bloom), as a box along x turned 90 about y: box UV charges a z-long box ~2 L^2 atlas px and an
    x-long one ~4 L."""
    # a box along x turned 90 about y lies along z; the tilt (about x, applied after the turn) slopes it with a
    # leaning fin's upper edge - the DSL applies x first, so the composed rotation goes through euler()
    from gen_models import rot_mat, mat_mul, euler
    rot = [0, 90, 0] if not tilt else euler(mat_mul(rot_mat(tilt, 0, 0), rot_mat(0, 90, 0)))
    bone.box_at(list(c), [L, 0.6, 0.8], color, rotation=rot, pivot=list(c))


def knob(bone, c, r, color, **kw):
    bone.box_at(list(c), [r, r, r], color, **kw)
    bone.box_at(list(c), [r * 0.7, r * 1.3, r * 0.7], color, **kw)


def vertebra(bone, cz, cy, w, L, plate=1.0, disc=True, thread=True, fin=0, back=0.0, lean=28.0):
    """One vertebra lying along z (r9): the centrum a rounded drum a little longer than its slot so the chain has no
    gaps; a narrower ring at its back end (the joint - what makes the neck read as distinct vertebrae); a ridge FIN
    standing on top (r10: a thin plate leaning back `lean` degrees and longer than its slot, a narrower upper half
    set further back - a swept, pointed fin whose rear overlaps the next vertebra's, and a tall / short rhythm by
    `fin` - so from above the ridge is a sawtooth of leaning, overlapping plates, not r9's row of equal cubes); and
    the soul thread (x > 0 tips a fin's top toward +z, the tail): ONE thin strip of dim soul-light along the fins' upper edges, overlapping its neighbours and
    (r10) reaching `back` px past the pivot behind it so a bend at the joint never opens a gap in the line."""
    barrel(bone, cy, cz - L / 2, cz + L / 2 + 0.3, w, w * 0.92, "bone", k=2)
    if disc:
        bone.box_at([0, cy, cz + L / 2 + 0.35], [w * 0.7, w * 0.7, 0.9], "bone_old")
    if plate > 0:
        h = (w * 0.55 * plate + 0.8) * (1.25 if fin % 2 == 0 else 0.95)
        y0 = cy + w / 2 - 0.2
        piv = [0, y0, cz]
        t = max(0.7, w * 0.16)
        # the fin: a lower half the slot long, an upper half shorter and set back (the swept point), both leaning
        bone.box_at([0, y0 + h * 0.3, cz], [t, h * 0.6, L * 1.02], "bone", rotation=[lean, 0, 0], pivot=piv)
        bone.box_at([0, y0 + h * 0.75, cz + L * 0.18], [t * 0.8, h * 0.5, L * 0.62], "bone", rotation=[lean, 0, 0], pivot=piv)
        bone.box_at([0, y0 + h - 0.25, cz + L * 0.22], [t + 0.5, 0.6, L * 0.5], "bone_pale", rotation=[lean, 0, 0], pivot=piv)
        # the thread is the fin's lit upper edge: the pale cap's centre swung back by the lean, the strip tilted with it
        from gen_models import rotate_about
        tc = rotate_about(piv, [lean, 0, 0], [0, y0 + h + 0.1, cz + L * 0.22])
        top = (tc[1], tc[2], lean)
    else:
        top = (cy + w / 2 + 0.05, cz, 0.0)
    if thread:
        globals()["thread"](bone, (0, top[0], top[1] + back / 2), L + 1.0 + abs(back), tilt=top[2])
    return top


def claw_foot(bone, x, y, z, L, w, color="bone", n=3, fwd=-1):
    """A foot: a flat metatarsal block, n toes fanning forward, each ending in a dark claw that hooks down."""
    bone.cube([x - w / 2, y - L * 0.5, z - w * 0.5], [w, L * 0.5 + 0.6, w], color)
    for i in range(n):
        t = (i - (n - 1) / 2) / max(1, n - 1)
        tx = x + t * w * 1.1
        ang = t * 28
        c = (tx, y - L * 0.55, z + fwd * w * 0.55)
        bone.box_at(c, [w * 0.32, L * 0.5, w * 0.9], "bone_old", rotation=[0, -fwd * ang, 0], pivot=[tx, y - L * 0.55, z])
        c2 = (tx, y - L * 0.95, z + fwd * w * 1.0)
        bone.box_at(c2, [w * 0.26, L * 0.5, w * 0.3], "claw", rotation=[fwd * 35, -fwd * ang, 0], pivot=[tx, y - L * 0.75, z + fwd * w])


def leg(m, name, parent, top, upper_len, lower_len, foot_len, w, rest, fwd=-1):
    """upper (pivot top) -> lower -> foot, each link a bone bar with a knuckle at each joint. Light bones: a dragon
    carries its weight on its wings; the knee / elbow is a knob so the fold reads from the camera."""
    x, y, z = top
    up = m.bone(name + "_upper", [x, y, z], parent=parent, rotation=[rest[0], 0, 0])
    knob(up, (x, y, z), w * 1.15, "bone_old")                                        # the ball of the joint
    bar(up, (x, y, z), (x, y - upper_len, z), w, "bone", plane="yz", ext=1.0)
    ky = y - upper_len
    lo = m.bone(name + "_lower", [x, ky, z], parent=name + "_upper", rotation=[rest[1], 0, 0])
    knob(lo, (x, ky, z), w * 1.0, "bone_old")
    bar(lo, (x, ky, z), (x, ky - lower_len, z), w * 0.8, "bone", plane="yz", ext=0.8)
    bar(lo, (x + w * 0.35, ky - 0.5, z + 0.3), (x + w * 0.2, ky - lower_len + 0.5, z - 0.3), w * 0.35, "bone_old", plane="yz")  # the second bone
    fy = ky - lower_len
    ft = m.bone(name + "_foot", [x, fy, z], parent=name + "_lower", rotation=[rest[2], 0, 0])
    knob(ft, (x, fy, z), w * 0.85, "bone_old")
    claw_foot(ft, x, fy, z, foot_len, w * 1.6, fwd=fwd)


def membrane_panel(bone, xr, x0, x1, z_lead, depth0, depth1, y, s, color, dark, seed, steps=3, hole=None, outer=None):
    """A membrane behind a leading-edge bone running along x from x0 to x1 (in the bone's frame): `steps` slabs of
    decreasing depth from z_lead back (depth0 at x0 .. depth1 at x1), each a little lower than the last so they
    overlap, the trailing edge ragged - a notch cut from every other slab, a hole where `hole` says. s = +1 for the
    left wing (panels at +x), -1 for the right; xr is the bone's root x, x0 / x1 distances out along it."""
    rnd = seed
    n = steps
    for i in range(n):
        rnd = (rnd * 1103515245 + 12345) & 0x7fffffff
        xa = x0 + (x1 - x0) * i / n
        xb = x0 + (x1 - x0) * (i + 1) / n
        d = depth0 + (depth1 - depth0) * (i + 0.5) / n
        d *= 1.0 - 0.12 * ((rnd >> 4) % 3)                                 # ragged: some slabs shorter
        col = dark if (rnd >> 7) & 1 and i == n - 1 else color
        cx = (xa + xb) / 2
        w = abs(xb - xa) + 0.3
        X = lambda v: xr + s * v
        # the holes and the scalloped, ragged hem are cut out of the TEXTURE (tools/materials.py _membrane, alpha 0
        # under the entity's cutout render type). Chord-wise the span is two strips: the inner on `bone` (hinged on
        # the leading edge), the outer on `outer` (hinged on the strip boundary, `outer`'s pivot), so the sail can
        # belly and cup, not just tilt
        if outer is None:
            bone.box_at([X(cx), y - 0.1 * i, z_lead + d / 2], [w, 0.7, d], col)
        else:
            # r13: the chord is THREE strips (outer = [middle, hem]), each hinged on the one ahead, so the sail can
            # curve - billow and cup - instead of r12's two flat planes; thick and dark by the bone, thinner and
            # paler toward the hem (the hem's light rim keeps the outline on a night sky - lovish's Oct 22 frame)
            outs = outer if isinstance(outer, (list, tuple)) else [outer]
            zs = [z_lead] + [o.pivot[2] for o in outs] + [max(z_lead + d, outs[-1].pivot[2] + 2.0)]
            bones = [bone] + list(outs)
            tones = ("membrane_dark", "membrane", "membrane_hem") if len(bones) == 3 else (col, col)
            for q, b in enumerate(bones):
                za, zb = zs[q], zs[q + 1]
                b.box_at([X(cx), y - 0.1 * i - 0.05 * q, (za + zb) / 2], [w, 0.7, zb - za + (0.3 if q < len(bones) - 1 else 0.0)], tones[q])
        # the trailing fringe: a tatter hanging off the edge, every other slab
        if i % 2 == 0 and i < 2:          # (r13: one tatter per strip - the atlas)
            bone.box_at([X(cx + (xb - xa) * 0.2), y - 0.6, z_lead + d + 1.2], [w * 0.4, 0.4, 2.8], dark,
                        rotation=[-25, 0, 0], pivot=[X(cx + (xb - xa) * 0.2), y - 0.1 * i, z_lead + d])


def wing(m, P, s):
    """One wing (s = +1 left, -1 right): humerus -> radius+ulna -> the hand with four finger bones fanning back,
    membrane panels under each segment so the wing folds in three stages. All x coordinates are mirrored by s and
    the y / z rotations negated, so the two wings are exact mirrors."""
    side = "left" if s > 0 else "right"
    rx, ry, rz = P["wing_root"]
    rx *= s
    mir = lambda r: [r[0], r[1] * s, r[2] * s]
    arm = m.bone("wing_%s_arm" % side, [rx, ry, rz], parent="body", rotation=mir(P["arm_rest"]))
    A = P["arm"]
    knob(arm, (rx, ry, rz), 4.2, "bone_old")                                                      # the shoulder
    arm.box_at([rx + s * 1.5, ry + 1.6, rz], [4.0, 1.4, 3.2], "bone_old")                         # the scapula plate
    bar(arm, (rx, ry, rz), (rx + s * A, ry, rz), 2.3, "bone", plane="xz", ext=1.0)
    arm.box_at([rx + s * A * 0.5, ry + 0.9, rz - 0.8], [A * 0.6, 0.8, 1.0], "bone_pale")         # the lit top ridge
    # the sail hangs from its bones even at rest (SAG: the far edge of each strip a little lower, so from the front
    # the chord sits a third of a block under the arm at mid-span), and from the flank a body-bound strip bridges
    # the arm panel to the hip so the wing reads as one sheet when the arm lifts
    SAG = P["sag"]
    mem0 = m.bone("mem_%s_0" % side, [rx, ry - 1.3, rz + 0.8], parent="body", rotation=[SAG * 0.6, 0, 0])
    for q, (x0, x1, dd) in enumerate(((-2.5, 5.5, 28.0),)):
        # in three chord pieces: box UV charges a slab ~2 d^2 atlas px, so one 28 px slab cost more than three 9s
        # (r13 kept the atlas at 256x256 UV this way)
        for c in range(3):
            za, zb = dd * c / 3, dd * (c + 1) / 3
            mem0.box_at([rx + s * (x0 + x1) / 2, ry - 1.3 - 0.05 * q, rz + 0.8 + (za + zb) / 2],
                        [x1 - x0 + 0.3, 0.7, zb - za + (0.2 if c < 2 else 0.0)], "membrane" if c < 2 else "membrane_hem")
    mem1 = m.bone("mem_%s_1" % side, [rx, ry - 1.0, rz + 0.8], parent="wing_%s_arm" % side, rotation=[SAG * 0.7, 0, 0])
    mem1b = m.bone("mem_%s_1b" % side, [rx, ry - 1.0, rz + 0.8 + 9.0], parent="mem_%s_1" % side, rotation=[SAG * 0.6, 0, 0])
    mem1c = m.bone("mem_%s_1c" % side, [rx, ry - 1.0, rz + 0.8 + 18.0], parent="mem_%s_1b" % side, rotation=[SAG * 0.5, 0, 0])
    membrane_panel(mem1, rx, -1.5, A + 4.0, rz + 0.8, 29.0, 24.0, ry - 1.0, s, "membrane", "membrane_dark", seed=3 + s, steps=2, outer=[mem1b, mem1c])
    # the elbow: the fore bone's pivot
    ex = rx + s * A
    fore = m.bone("wing_%s_fore" % side, [ex, ry, rz], parent="wing_%s_arm" % side, rotation=mir(P["fore_rest"]))
    F = P["fore"]
    knob(fore, (ex, ry, rz), 3.4, "bone_old")
    bar(fore, (ex, ry, rz), (ex + s * F, ry, rz), 1.9, "bone", plane="xz", ext=0.8)
    bar(fore, (ex + s * 0.5, ry - 0.4, rz + 1.6), (ex + s * (F - 0.5), ry - 0.2, rz + 0.6), 0.9, "bone_old", plane="xz")   # the ulna
    mem2 = m.bone("mem_%s_2" % side, [ex, ry - 1.1, rz + 0.8], parent="wing_%s_fore" % side, rotation=[SAG * 0.7, 0, 0])
    mem2b = m.bone("mem_%s_2b" % side, [ex, ry - 1.1, rz + 0.8 + 8.0], parent="mem_%s_2" % side, rotation=[SAG * 0.6, 0, 0])
    mem2c = m.bone("mem_%s_2c" % side, [ex, ry - 1.1, rz + 0.8 + 16.0], parent="mem_%s_2b" % side, rotation=[SAG * 0.5, 0, 0])
    membrane_panel(mem2, ex, -10.0, F + 1.0, rz + 0.8, 23.0, 21.0, ry - 1.1, s, "membrane", "membrane_dark", seed=11 + s, steps=3, hole=1, outer=[mem2b, mem2c])
    # tattered rags of the owner's colour at the elbow
    fore.box_at([ex + s * 2.0, ry - 2.6, rz + 0.4], [1.4, 4.4, 0.5], "tabard", rotation=[12, 0, s * 15], pivot=[ex + s * 2.0, ry - 0.4, rz + 0.4])
    fore.box_at([ex + s * 3.6, ry - 2.0, rz + 0.6], [1.0, 3.2, 0.5], "tabard_dark", rotation=[18, 0, s * -10], pivot=[ex + s * 3.6, ry - 0.4, rz + 0.6])
    # the hand: the wrist knob and four fingers fanning back from it, the membrane fan between them
    hx = ex + s * F
    hand = m.bone("wing_%s_hand" % side, [hx, ry, rz], parent="wing_%s_fore" % side, rotation=mir(P["hand_rest"]))
    knob(hand, (hx, ry, rz), 3.0, "bone_old")
    hand.box_at([hx + s * 1.5, ry + 1.2, rz - 1.2], [2.2, 2.4, 1.6], "claw", rotation=[0, 0, s * 30], pivot=[hx, ry, rz])   # the thumb claw
    H = P["hand"]
    # r13: the fan opened (r12: 0-78 deg) and the trailing fingers longer, so the hand carries a real sail
    fingers = ((0, 1.0, 1.9), (28, 0.94, 1.6), (56, 0.80, 1.4), (84, 0.62, 1.2))   # (angle back, length share, thickness)
    # a fifth ray is not a bone: the web behind the last finger runs back to it (toward the fore panel) and rides the
    # last finger, closing the gap between the finger flaps and the arm sail
    rays = fingers + ((114, 0.46, 0.0),)
    # the first finger is the hand's own leading edge; the three trailing fingers are bones of their own (pivot at
    # the wrist) so they close toward it on the upstroke, each carrying the web between it and the finger ahead
    for k, (ang, share, th) in enumerate(rays):
        L = H * share
        a = math.radians(ang)
        if th > 0:
            b = hand if k == 0 else m.bone("wing_%s_finger_%d" % (side, k + 1), [hx, ry, rz], parent="wing_%s_hand" % side)
            tip = (hx + s * L * math.cos(a), ry, rz + L * math.sin(a))
            bar(b, (hx, ry, rz), tip, th, "bone" if k < 3 else "bone_old", plane="xz", ext=0.6)
            # a knuckle a third of the way out, a dark hooked tip
            b.box_at([hx + s * L * 0.38 * math.cos(a), ry, rz + L * 0.38 * math.sin(a)], [th * 1.5, th * 1.5, th * 1.5], "bone_old")
            b.box_at([tip[0] + s * 0.4 * math.cos(a), ry - 0.6, tip[2] + 0.4 * math.sin(a)], [th * 0.9, 1.6, th * 0.9], "claw")
        if k == 0:
            continue
        # the web between this ray and the one ahead: slabs along the gap's middle direction from the wrist out,
        # inner short and narrow, outer wide; the texture tears them
        a0, a1 = math.radians(rays[k - 1][0]), a
        L0 = H * rays[k - 1][1]
        am = (a0 + a1) / 2
        Lm = min(L0, L) - 1.5                                         # r11: the web runs out to the finger tips (bare tips read as a rake from the RTS camera)
        web = m.bone("web_%s_%d" % (side, k + 1), [hx, ry - 0.9, rz], parent=b.name, rotation=[SAG * 0.7, s * (90 - math.degrees(am)), 0])
        # the outer half on its own bone hinged half way out (its pivot in the web's unrotated frame, swung into the
        # gap with it), so the web can belly and cup like the big spans
        webb = m.bone("web_%s_%db" % (side, k + 1), [hx, ry - 0.9, rz + 0.5 * Lm], parent=web.name, rotation=[SAG * 0.6, 0, 0])
        # r13: four radial pieces (two per hinge), each as wide as the gap at its own outer end - a tapering fan, not
        # r12's two rectangles, and half the atlas area (box UV grows with the square of a slab's length)
        for q, (r0, r1, wf) in enumerate(((0.0, 0.25, 0.95), (0.25, 0.5, 0.95), (0.5, 0.75, 1.0), (0.75, 1.0, 1.05))):
            rm = (r0 + r1) / 2 * Lm
            width = 2 * (r1 * Lm) * math.tan((a1 - a0) / 2) * wf + 0.4     # r11: the width at the slab's OUTER end, so the web fills the gap to the tips
            # laid along +z from the wrist; the bone's rotation swings it into the gap, and its +z side (where the
            # painter cuts the concave, ragged hem) is the web's outer, trailing edge
            (web if q < 2 else webb).box_at([hx, ry - 0.9 - 0.1 * (q // 2), rz + rm], [width, 0.7, (r1 - r0) * Lm + 0.3],
                                            "membrane_hem" if q == 3 else ("membrane_dark" if q == 0 else "membrane"))
    return arm, fore, hand


def rib_arc(bone, cz, hw, hh, cy, w, n=7, sweep=0.0, k=0):
    """One pair of ribs (r9): a chain of n short bars each side from the spine down and round the chest to the
    sternum - a stepped arc that reads as a curve at RTS distance. r10: the whole rib one light ivory (r9 shaded its
    lower bars darker, which from above read as grey rubble between the pale tops), thin along z, and planar - no
    sweep - so the gaps between neighbouring ribs line up top to bottom and the ground shows through them."""
    pts = []
    for j in range(n + 1):
        t = math.radians(88 - 176 * j / n)
        pts.append((hw * math.cos(t), cy + hh * math.sin(t), cz + sweep * j / n))
    for s in (1, -1):
        for j in range(n):
            p0 = (s * pts[j][0], pts[j][1], pts[j][2])
            p1 = (s * pts[j + 1][0], pts[j + 1][1], pts[j + 1][2])
            col = "bone_pale" if j < 3 else "bone"
            bar(bone, p0, p1, w - 0.06 * j, col, plane="xy", ext=0.5)


def dragon(m, P):
    """The rig's bones and the plain beast on them from the proportions P."""
    bw, bh, by = P["body_w"], P["body_h"], P["body_y"]
    z0, z1 = P["body_z0"], P["body_z1"]
    body = m.bone("body", [0, by, (z0 + z1) / 2])
    # the spine (r9): ONE continuous run of vertebrae along the top of the thorax, their ridge plates overlapping,
    # the soul thread on top of them unbroken - the line the camera reads from the neck to the tail
    n = 5
    sy = by + bh / 2 - 1.2
    tops = []
    for i in range(n):
        cz = z0 + (z1 - z0) * (i + 0.5) / n
        tops.append(vertebra(body, cz, sy, 4.6, (z1 - z0) / n, plate=1.15 if 1 <= i <= 3 else 0.95, disc=i < n - 1, thread=False, fin=i))
    thread_y = sum(t[0] for t in tops) / n
    thread(body, (0, thread_y, (z0 + z1) / 2 + 1.0), z1 - z0 + 3.0)                            # ONE thread over the thorax
    # the sternum: a keel of bone under the chest the ribs meet
    for c in range(2):   # in two pieces (box UV: the atlas cost grows with the square of the length)
        body.cube([-1.6, by - bh / 2 - 0.4, z0 + 1.5 + c * (z1 - z0 - 3.0) / 2], [3.2, 2.0, (z1 - z0 - 3.0) / 2 + (0.2 if c == 0 else 0)], "bone_old")
    body.box_at([0, by - bh / 2 + 0.3, z0 + 2.0], [4.5, 2.6, 4.0], "bone", rotation=[22, 0, 0])
    # the ribcage: its own bone so the breath swells it alone; seven curved arcs, the chest deepest at the third
    ribs = m.bone("ribs", [0, by, (z0 + z1) / 2], parent="body")
    nr = 7
    # r10: seven SEPARATE arcs - thin (1.3 px along z) on a 2.9 px pitch, so a 1.6 px gap of ground / sail shows
    # between neighbours from above (r9's 1.9 px ribs with a sweep ran together into a grey jumble under the spine)
    for i in range(nr):
        cz = z0 + 1.2 + (z1 - z0 - 2.8) * i / (nr - 1)
        f = 1.0 - 0.10 * abs(i - 2.5) / 3.5
        rib_arc(ribs, cz, bw / 2 * f, bh / 2 * f * 0.98, by + 0.3, 1.3, n=6, sweep=0.0, k=i)   # r13: 6 bars an arc (the atlas)
    # the soul inside: a small lit heart low in the chest (r10: smaller and lower than r9's, so the gaps between the
    # ribs open onto the ground, not onto a glowing slab), a dim haze of it along the sternum
    # r11: the heart is NOT in the glow pass (it lit the rib gaps cyan from above); a dim lit ember, like the thread
    ribs.box_at([0, by - 1.5, (z0 + z1) / 2 - 1.0], [3.0, 3.0, 6.0], "soul_dim")
    ribs.box_at([0, by - 0.5, (z0 + z1) / 2 - 2.0], [1.8, 1.8, 3.0], "soul_thread")
    ribs.box_at([0, by - 3.8, (z0 + z1) / 2], [5.0, 0.8, z1 - z0 - 12.0], "soul_dim")
    # the shoulder blades: small ivory plates at the wing roots, clear of the ribs (r9's lay over the top ribs and
    # read as grey rubble from above)
    for s in (1, -1):
        body.box_at([s * (bw / 2 + 0.2), by + bh / 2 - 1.0, z0 + 7.0], [2.6, 0.9, 4.0], "bone_old", rotation=[0, 0, s * -40], pivot=[s * (bw / 2 - 0.8), by + bh / 2 - 0.6, z0 + 7.0])

    # spine_1 (lumbar) and spine_2 (pelvis): shorter vertebrae, the hip bones flaring out
    lz1, pz1 = P["lumbar_z1"], P["pelvis_z1"]
    sp1 = m.bone("spine_1", [0, by, z1], parent="body")
    tops = []
    for i in range(3):
        cz = z1 + (lz1 - z1) * (i + 0.5) / 3
        tops.append(vertebra(sp1, cz, sy - 0.35 * i, 4.2 - 0.2 * i, (lz1 - z1) / 3, plate=0.85, thread=False, fin=i + 1))
    thread(sp1, (0, sum(t[0] for t in tops) / 3, (z1 + lz1) / 2 + 0.6), lz1 - z1 + 3.0)
    sp2 = m.bone("spine_2", [0, by - 0.5, lz1], parent="spine_1")
    tops = []
    for i in range(2):
        cz = lz1 + (pz1 - lz1) * (i + 0.5) / 2
        tops.append(vertebra(sp2, cz, sy - 1.2 - 0.3 * i, 3.8, (pz1 - lz1) / 2, plate=0.9, thread=False, fin=i))
    thread(sp2, (0, sum(t[0] for t in tops) / 2, (lz1 + pz1) / 2 + 0.6), pz1 - lz1 + 3.0)
    for s in (1, -1):                                                        # the ilium: a flared plate each side
        sp2.box_at([s * (P["pelvis_w"] / 2 - 0.8), by - 0.5, lz1 + 2.5], [1.6, 5.5, 6.0], "bone", rotation=[0, 0, s * 22], pivot=[s * 1.5, by + 1.5, lz1 + 2.5])
    sp2.box_at([0, by - 3.0, lz1 + 3.5], [4.0, 2.0, 5.0], "bone_old")                              # the pubis

    # the tail: a tapering chain of seven vertebrae, a blackstone blade at the tip
    tw0, tw1, seg = P["tail_w0"], P["tail_w1"], P["tail_seg"]
    parent, z = "spine_2", pz1
    ty = sy - 1.6
    for k in range(P["tail_n"]):
        t = k / (P["tail_n"] - 1)
        w = tw0 + (tw1 - tw0) * t
        b = m.bone("tail_%d" % (k + 1), [0, ty - 0.25 * k, z], parent=parent, rotation=[TAIL_REST[k], 0, 0])
        vertebra(b, z + seg / 2, ty - 0.25 * k, w, seg, plate=0.9 - 0.1 * k, disc=k < P["tail_n"] - 1 and k % 2 == 0, fin=k, back=2.5)
        if k < 4:
            # the chevron under the tail root: a short bone hanging under each of the first links
            if k < 3:
                b.box_at([0, ty - 0.25 * k - w / 2 - 0.6, z + seg * 0.5], [w * 0.3, 1.6, seg * 0.4], "bone_old")
            # a tattered scrap of the owner's colour hangs from the second and fourth links
            if k in (1, 3):
                b.box_at([0.4, ty - w / 2 - 2.6, z + seg * 0.5], [1.2, 4.0, 0.5], "tabard" if k == 1 else "tabard_dark", rotation=[0, 0, 8], pivot=[0.4, ty - w / 2 - 0.6, z + seg * 0.5])
        parent, z = "tail_%d" % (k + 1), z + seg
    tip = m.bones["tail_%d" % P["tail_n"]]
    tyk = ty - 0.25 * (P["tail_n"] - 1)
    # the tail blade: three blackstone shards fanning from the last link
    for q, (ang, L) in enumerate(((0, 7.0), (28, 5.0), (-28, 5.0))):
        c = [0, tyk - 0.2, z + L / 2 - 0.5]
        tip.box_at(c, [1.0, 2.6 if q == 0 else 1.8, L], "blackstone", rotation=[0, ang, 0], pivot=[0, tyk - 0.2, z - 0.5])
        tip.box_at([0, tyk + 0.9 if q == 0 else tyk + 0.5, z + L * 0.45], [0.5, 0.8, L * 0.6], "blackstone_edge", rotation=[0, ang, 0], pivot=[0, tyk - 0.2, z - 0.5])

    # the neck (r9): five vertebrae climbing forward in an S, each its own bone so the chain rolls; the drums taper
    # toward the head, their joint rings and the cervical ribs under them read as distinct vertebrae, the ridge plates
    # shrink link by link so the dorsal line tapers into the skull
    nw0, nw1, nseg, ny = P["neck_w0"], P["neck_w1"], P["neck_seg"], P["neck_y"]
    parent, z = "body", z0
    for k in range(P["neck_n"]):
        t = k / (P["neck_n"] - 1)
        w = nw0 + (nw1 - nw0) * t
        cy = ny - 0.3 * k
        b = m.bone("neck_%d" % (k + 1), [0, cy, z], parent=parent, rotation=[NECK_REST[k], 0, 0])
        # the neck's thread runs the other way (the links grow toward -z): the overlap reaches forward past the
        # next link's pivot, so a bend at any joint stays lit
        vertebra(b, z - nseg / 2, cy, w, nseg, plate=0.9 - 0.13 * k, disc=k % 2 == 1, fin=k + 1, back=-2.5)
        # the cervical rib: a short bar under each drum angled back, and the throat strap below it
        for sgn in ((1, -1) if k % 2 == 0 else ()):     # r13: on every other link (eight links, one atlas)
            b.box_at([sgn * (w * 0.42), cy - w * 0.35, z - nseg * 0.45], [0.9, w * 0.4, nseg * 0.45], "bone_old", rotation=[25, 0, sgn * 12], pivot=[sgn * w * 0.42, cy - w * 0.2, z - nseg * 0.3])
        b.box_at([0, cy - w / 2 - 0.2, z - nseg / 2], [w * 0.45, 1.0, nseg * 0.7], "bone_old")
        parent, z = "neck_%d" % (k + 1), z - nseg
    head = m.bone("head", [0, ny - 1.5, z], parent=parent, rotation=[HEAD_REST, 0, 0])
    return body, ribs, head, z


def bone_skull(m, P, head, z):
    """The skull off the neck's end at z (facing -z) - r10 (the r9 review: "a long box with flat glowing rectangles
    for sockets"): a wedge - the cranium wide at the hinge, then two snout slabs each turned in about y so the sides
    CONVERGE on a narrow nose (a true taper in plan, what the camera sees from above), the top line stepping down;
    the eye sockets RECESSED - a dark pit set into a cut in the skull's side, its soul-light a small ember deep in it,
    under a heavy brow shelf; two long bone horns from the back corners, swept straight back and out in the skull's
    plane so each reads as one clear line from above; the mouth's interior dark (a black palate under the skull, a
    black floor in the mandible) so the open jaw reads as a hole; the lower jaw its OWN bone hinged under the back
    of the skull."""
    hl, hw, hh = P["head_len"], P["head_w"], P["head_h"]
    hx, hy, hz = head.pivot
    top, bot = hy + hh * 0.55, hy - hh * 0.45
    cy = (top + bot) / 2
    nw = hw * 0.22                                                     # the nose's width: the wedge's point (r11: 0.30 -> 0.22)
    # the cranium: the wide back of the skull, rounded
    barrel(head, cy, hz - hl * 0.40, hz + 1.5, hw, hh, "bone", k=2)
    # the snout: a centre spine slab the full length, and a side slab each side turned in so the plan is a wedge
    # r11: the snout's centre slab is pitched nose-down so the top line falls toward the nose - a wedge in side view
    # as well as in plan; the nose block smaller
    head.box_at([0, cy - 0.9, hz - hl * 0.62], [nw + 0.6, hh * 0.72, hl * 0.62 + 0.4], "bone", rotation=[-7, 0, 0], pivot=[0, cy - 0.6, hz - hl * 0.3])
    head.box_at([0, cy - 1.6, hz - hl - 0.2], [nw, hh * 0.40, 2.4], "bone")                                    # the nose block
    half = (hw - nw) / 2
    ang = math.degrees(math.atan2(half, hl * 0.62))
    for s in (1, -1):
        zc = hz - hl * 0.40 - hl * 0.31
        xc = s * (nw / 2 + half / 2)
        Ls = math.hypot(half, hl * 0.62) + 0.6
        # r11: the side slabs and the roof plates pitch nose-down with the centre slab (the taper in height)
        head.box_at([xc, cy - 0.8, zc], [1.6, hh * 0.70, Ls], "bone", rotation=[-7, s * ang, 0], pivot=[xc, cy - 0.6, zc])   # +y turns a +x slab's nose end toward the centre
        # the skull roof over the side slab: a flat plate turned the same way, closing the top between it and the spine
        head.box_at([xc - s * 0.5, cy - 0.9 + hh * 0.33, zc], [half, 0.9, Ls - 1.6], "bone", rotation=[-7, s * ang, 0], pivot=[xc - s * 0.5, cy - 0.6 + hh * 0.33, zc])
    head.box_at([0, cy + hh * 0.34, hz - hl * 0.62], [nw * 0.8, 0.9, hl * 0.5], "bone_pale", rotation=[-5, 0, 0])   # the nasal ridge, worn pale
    head.box_at([0, top - 0.3, hz - hl * 0.22], [hw * 0.72, 1.0, hl * 0.28], "bone_pale")                        # the lit brow shelf
    thread(head, (0, top + 0.3, hz - hl * 0.2), hl * 0.42)                                                        # the soul thread runs onto the brow
    head.box_at([0, cy - 1.0, hz - hl - 0.6], [nw * 0.6, hh * 0.22, 1.2], "socket")                              # the nares
    # the palate: the dark roof of the mouth under the skull, what shows between the teeth when the jaw drops
    head.box_at([0, bot + 0.2, hz - hl * 0.5], [hw * 0.62, 0.6, hl * 0.8], "socket")
    for s in (1, -1):
        # the eye socket: a notch cut into the skull's side under the brow shelf - the dark pit is INSIDE the skull
        # (its outer face 0.4 px in from the cranium's side, under the brow's overhang), a small ember deep in it
        sx = s * (hw / 2 - 1.2)
        sz = hz - hl * 0.40
        head.box_at([sx, top - 2.6, sz], [2.4, 2.6, 2.8], "socket")
        head.box_at([sx + s * 0.6, top - 2.8, sz + 0.2], [1.0, 1.0, 1.0], "soul_glow")
        # the brow shelf over the socket, overhanging it
        head.box_at([s * (hw / 2 - 1.0), top - 0.9, sz + 0.2], [2.2, 1.0, 3.6], "bone", rotation=[0, 0, s * -10], pivot=[s * (hw / 2 - 1.8), top - 0.6, sz])
        # the cheek: a bone bar under the socket back to the jaw hinge (the zygomatic arch)
        head.box_at([s * (hw / 2 - 0.3), cy - 1.3, hz - hl * 0.14], [1.2, 1.4, hl * 0.4], "bone_old")
        # the horn: BONE (ivory, a dark worn tip only), out of a boss at the skull's back corner, ONE long sweep
        # back and out (two slabs, nearly collinear) rising only a little - a clear line in plan from above
        hb = m.bone("horn_%s" % ("left" if s > 0 else "right"), [s * (hw / 2 - 0.6), top - 0.8, hz - 0.5], parent="head")
        base = (s * (hw / 2 - 0.6), top - 0.8, hz - 0.5)
        hb.box_at(list(base), [2.4, 2.4, 2.4], "bone_old")
        # r11: swept BACK (the r10 tour: it read straight up from the RTS camera) - 14.5 px back, 5.4 out, rising 1.2
        # then drooping, so from above and from the chase camera it is a line trailing the skull
        pts = [base, (base[0] + s * 2.6, base[1] + 1.0, base[2] + 6.5), (base[0] + s * 4.4, base[1] + 1.2, base[2] + 11.5),
               (base[0] + s * 5.4, base[1] + 0.4, base[2] + 14.5)]
        for q, (w, col) in enumerate(((1.9, "horn"), (1.4, "horn"), (0.9, "claw"))):
            p0, p1 = pts[q], pts[q + 1]
            along = (p1[0] - p0[0], p1[1] - p0[1], p1[2] - p0[2])
            L = math.sqrt(sum(v * v for v in along)) + 0.8
            c = ((p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2, (p0[2] + p1[2]) / 2)
            slab(hb, c, along, (0, 1, 0), [w, w, L], col)
            if q < 2:   # growth rings round the horn
                slab(hb, c, along, (0, 1, 0), [w + 0.4, w + 0.4, 0.6], "bone_old")
    # the occipital crest: a low bone fin up from the back of the skull (the ridge line arriving at the head)
    head.box_at([0, top + 0.8, hz - 0.5], [1.0, 2.6, 3.0], "bone", rotation=[50, 0, 0], pivot=[0, top, hz - 0.5])   # r11: leaning back harder
    for i in range(6):
        tz = hz - hl + 1.0 + i * (hl * 0.62) / 5
        wt = nw / 2 + (hw * 0.36 - nw / 2) * i / 5 - 0.1
        for s in (1, -1):
            head.box_at([s * wt, bot - 0.5, tz], [0.7, 1.7 if i % 2 else 1.1, 0.7], "tooth")
    # the jaw: hinged under the back of the skull, a deep mandible (two rami meeting at the chin) with its own teeth,
    # a BLACK floor between them (r10: the mouth's interior is a hole, the soul gullet a faint ember at the back)
    jaw = m.bone("jaw", [0, bot + 0.6, hz - 0.5], parent="head")
    jl = P["jaw_len"]
    for s in (1, -1):   # the rami: a bar each side converging on the chin as the skull's sides do
        jaw.box_at([s * (hw * 0.34), bot - 1.4, hz - jl * 0.3], [1.4, 2.8, jl * 0.5], "bone")
        jaw.box_at([s * (hw * 0.24), bot - 1.6, hz - jl * 0.72], [1.2, 2.0, jl * 0.42], "bone", rotation=[0, s * -10, 0], pivot=[s * hw * 0.34, bot - 1.4, hz - jl * 0.5])
    jaw.box_at([0, bot - 1.9, hz - jl + 0.8], [nw + 0.4, 1.8, 3.0], "bone_old")                       # the chin
    jaw.box_at([0, bot - 2.5, hz - jl * 0.5], [hw * 0.5, 0.9, jl * 0.7], "bone_old")                    # the floor between the rami, bone below
    jaw.box_at([0, bot - 1.7, hz - jl * 0.5], [hw * 0.5, 0.7, jl * 0.72], "socket")                     # ... black inside
    jaw.box_at([0, bot - 1.5, hz - hl * 0.2], [hw * 0.3, 0.5, hl * 0.22], "soul_dim")                  # the gullet's ember
    for i in range(5):
        tz = hz - hl + 1.6 + i * (hl * 0.55) / 4
        wt = nw / 2 + (hw * 0.32 - nw / 2) * i / 4 - 0.2
        for s in (1, -1):
            jaw.box_at([s * wt, bot - 0.2, tz], [0.6, 1.4 if i % 2 == 0 else 0.9, 0.6], "tooth")
    return jaw


def bone_dragon():
    P = DRAGON
    m = Model("Bone Dragon", "gravebound", "T3 flying wyrm, the Gravebound's dead dragon (dragon rig)", PALETTE, EMISSIVE)
    body, ribs, head, hz = dragon(m, P)
    bone_skull(m, P, head, hz)
    for s in (1, -1):
        wing(m, P, s)
    sx, sy, sz = P["shoulder"]
    for s, side in ((1, "left"), (-1, "right")):
        leg(m, "front_" + side, "body", (s * sx, sy, sz), P["f_upper"], P["f_lower"], P["f_foot"], P["f_w"], FRONT_REST)
    hx, hy, hzz = P["hip"]
    for s, side in ((1, "left"), (-1, "right")):
        leg(m, "hind_" + side, "spine_2", (s * hx, hy, hzz), P["h_upper"], P["h_lower"], P["h_foot"], P["h_w"], HIND_REST)
    m.pose = {}
    m.limb_ok = [b for b in m.bones]          # a flier at rest: nothing stands on the ground
    return m


MODELS = {"gravebound_bone_dragon": bone_dragon}


def main():
    only = sys.argv[1:]
    for name, build in MODELS.items():
        if only and name not in only:
            continue
        m = build()
        path = os.path.normpath(os.path.join(OUT, name + ".json"))
        with open(path, "w") as f:
            json.dump(m.to_json(), f, indent=1)
        nc = sum(len(b.cubes) for b in m.bones.values())
        print(f"wrote {path}: {len(m.bones)} bones, {nc} cubes")


if __name__ == "__main__":
    main()
