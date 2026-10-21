#!/usr/bin/env python3
"""The White Angel capitol's statue, v3: a SMOOTH MARBLE SCULPTURE - the first sculpted mesh body (lovish, Oct 21:
"more fluidity", "I don't like the face, it doesn't look like a Roman face or sculpting", "I thought we added a way
to add smooth mesh"). Sculpted as signed-distance fields (tools/sdf_mesh.py), meshed with surface nets, coloured by a
bake (solid marble veins and carved-depth occlusion in the vertex colours), and written as an OBJ + rig that
tools/mesh_import.py turns into the .bmesh the game draws (StatueRenderClientEvents on a MeshBody; the cube statue v2
stays the fallback when the mesh file is missing).

What the sculpt follows (claude/capitol-next-brief.md - techniques, not copies):
  - BERNINI'S PONTE SANT'ANGELO ANGELS: one S-curve through the whole figure; drapery blown by a wind nobody else
    feels, undercut so its folds stand free with shadow behind; wings in one continuous sweep, feathers carved deep.
  - THE WINGED VICTORY OF SAMOTHRACE: "wet" drapery pressed to the striding leg by the wind, folds streaming off
    behind; the belt high under the chest, the cloth blousing over it.
  - CONTRAPPOSTO (the Augustus of Prima Porta): the weight on the right leg, the right hip up and the right shoulder
    down, the left knee bent forward with its heel lifted; the head bowed and turned to the engaged side.
  - THE ROMAN HEAD (Augustus, the Apollo Belvedere): the "Greek profile" (brow and nose one straight line), a firm
    brow ridge over deep-set, heavy-lidded almond eyes with smooth (uncarved) eyeballs, full calm lips, a rounded
    chin; the hair in thick comma locks laid like shingles with running-drill eyes in every curl.
  - THE SMILING ANGEL OF REIMS: calm, the mouth's corners lifted a hair - the gothic angel of a gothic capitol.
  - MATERIAL FINISHES (Bernini's contrast of surfaces): polished skin, softer cloth, rougher feathers - per-vertex
    regions that pick the atlas cell (albedo, LabPBR _n/_s smoothness) and the bake's grain.

Space: the DSL's - px, y up from the soles, the figure facing -z before its rest yaw (FACING), +x its left. Stature
50 px; BuildingParts.STATUES scales the mesh's own height (MODEL_PX) to 20 blocks. Parts (what the game moves - the
cube statue's bone names, so StatueRenderClientEvents folds a mesh angel with the same code): body (with the head and
the drapery), wing_left / wing_right (their pivot the shoulder blade) with wing_left_hand / wing_right_hand (the
primaries, pivot the wrist), halo and halo_outer (shown from L6).

    python3 tools/sculpt_angel.py [--out local/sculpt] [--lods 0,1,2]
      -> <out>/white_angel_statue.obj (+ .lod1.obj, .lod2.obj) and white_angel_statue.rig.json, and the atlas
         bar/src/main/resources/assets/reignofnether/textures/entities/white_angel_mesh.png (+ _n, _s)
    python3 tools/mesh_import.py <out>/white_angel_statue.obj --rig <out>/white_angel_statue.rig.json
         --lod <out>/white_angel_statue.lod1.obj --lod <out>/white_angel_statue.lod2.obj --name white_angel_statue
"""
import argparse
import json
import math
import os
import sys
import time

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import sdf_mesh as S  # noqa: E402

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
TEX_DIR = os.path.join(ROOT, "bar/src/main/resources/assets/reignofnether/textures/entities")
TEXTURE = "white_angel_mesh"
ATLAS = 64

STATURE = 50.0
# the body's rest yaw: 0 = the statue faces the structure's front (+z: the hall's gable, the great stair, the gate),
# and the placement's rotation turns it with the building (lovish, Oct 21 14:20: "the angel should be pointing where
# the capital is pointing" - v2-v3 r5 turned it 45 degrees to face the RTS camera's quarter instead)
FACING = 0.0

# ------------------------------------------------------------------ the pose: landmarks ---------------------------
# the engaged leg is the RIGHT (-x): hip up, knee locked; the free LEFT leg's knee forward, its heel lifted behind
HIP_R, KNEE_R, ANKLE_R = (-2.45, 25.9, 0.45), (-2.05, 13.7, -0.25), (-1.75, 2.35, 0.55)
HIP_L, KNEE_L, ANKLE_L = (2.65, 25.0, 0.45), (2.95, 14.3, -2.75), (3.85, 3.15, 2.45)
SHOULDER_R, SHOULDER_L = (-5.55, 40.55, 0.75), (5.35, 41.3, 0.75)
ELBOW_R, ELBOW_L = (-5.7, 32.1, -2.55), (6.65, 33.35, -1.6)
WRIST_R, WRIST_L = (-0.75, 26.75, -6.25), (1.95, 29.45, -6.2)
# the skull's centre: r10 raised it 0.45 px - the chin hung 1.4 px over the pit of the neck (a real neck gives the
# chin a third of a head's height over the shoulders' line; the bow takes some of it back)
HEAD_C = np.array([-0.45, 47.4, 0.05])
HEAD_BOW, HEAD_TURN = -12.0, 10.0            # bowed (top forward), turned toward the engaged side
SWORD_POINT, SWORD_TOP = np.array([0.95, 0.35, -6.55]), np.array([0.62, 28.55, -6.9])
WIND = S.normalize((-0.55, 0.0, 0.85))        # where the wind carries the cloth: back and to the figure's right


def lerp(a, b, t):
    return np.asarray(a, dtype=float) + (np.asarray(b, dtype=float) - np.asarray(a, dtype=float)) * t


def axis_rot(axis, deg):
    a = S.normalize(axis)
    c, s_ = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    K = np.array([[0, -a[2], a[1]], [a[2], 0, -a[0]], [-a[1], a[0], 0]])
    return np.eye(3) + s_ * K + (1 - c) * K @ K


def sword_at(y):
    """The sword's axis at height y (a point, or (n, 3) points for an array of heights)."""
    t = (np.asarray(y, dtype=float) - SWORD_POINT[1]) / (SWORD_TOP[1] - SWORD_POINT[1])
    if np.ndim(t):
        return SWORD_POINT[None, :] + (SWORD_TOP - SWORD_POINT)[None, :] * t[:, None]
    return SWORD_POINT + (SWORD_TOP - SWORD_POINT) * t


# ------------------------------------------------------------------ the body under the cloth ------------------------
def legs(P):
    d = S.round_cone(P, HIP_R, KNEE_R, 2.55, 1.75)
    d = S.smin(d, S.round_cone(P, KNEE_R, ANKLE_R, 1.7, 1.05), 1.0)
    d = S.smin(d, S.round_cone(P, HIP_L, KNEE_L, 2.5, 1.75), 1.2)
    d = S.smin(d, S.round_cone(P, KNEE_L, ANKLE_L, 1.7, 1.05), 1.0)
    d = S.smin(d, S.ellipsoid(P, lerp(KNEE_L, (3.0, 14.6, -4.4), 0.6), (1.35, 1.45, 0.9)), 0.8)   # the knee cap
    # the calf's swell on each shin
    d = S.smin(d, S.ellipsoid(P, lerp(KNEE_R, ANKLE_R, 0.25) + (0, 0, 0.8), (1.5, 2.8, 1.4)), 1.0)
    d = S.smin(d, S.ellipsoid(P, lerp(KNEE_L, ANKLE_L, 0.25) + (0, 0, 0.8), (1.5, 2.8, 1.4)), 1.0)
    return d


def feet(P):
    """The right foot flat, the left on its toes (the heel lifted behind the stride) - sandalled, toes carved."""
    d = S.round_cone(P, (-1.75, 1.15, 1.35), (-2.05, 0.75, -3.95), 1.0, 0.8)
    d = S.smin(d, S.ellipsoid(P, (-2.05, 0.55, -3.6), (1.15, 0.5, 1.0)), 0.5)
    d = S.smin(d, S.round_cone(P, ANKLE_R, (-1.75, 1.15, 1.35), 1.0, 0.95), 0.6)
    d = S.smin(d, S.round_cone(P, (3.95, 2.3, 2.95), (4.45, 0.6, -0.95), 0.95, 0.8), 0.5)
    d = S.smin(d, S.ellipsoid(P, (4.5, 0.55, -0.75), (1.05, 0.48, 0.95)), 0.45)
    d = S.smin(d, S.round_cone(P, ANKLE_L, (3.95, 2.3, 2.95), 1.0, 0.95), 0.6)
    # toes: grooves between them
    for fx, fz, rot in ((-2.05, -4.3, 0.0), (4.5, -1.55, 0.0)):
        for k in (-0.55, -0.15, 0.25):
            d = S.smax(d, -S.capsule(P, (fx + k, 0.95, fz - 0.4), (fx + k, 0.95, fz + 0.5), 0.08), 0.06)
    # sandals (lovish via the main session: "sandal/foot detail"): a sole under each foot, straps over the instep
    # and round the ankle, a thong between the first toes
    for heel, toe, ank in (((-1.75, 0.3, 1.5), (-2.05, 0.3, -4.55), ANKLE_R), ((3.9, 1.6, 3.05), (4.55, 0.3, -1.65), ANKLE_L)):
        heel, toe, ank = np.array(heel), np.array(toe), np.array(ank)
        sole = S.round_cone(P, heel, toe, 1.05, 0.95)
        sole = np.maximum(sole, np.abs((P - (heel + toe) / 2) @ S.normalize(np.cross(toe - heel, (1.0, 0.0, 0.0)))) - 0.17)
        d = S.smin(d, sole, 0.08)
        for t in (0.42, 0.62):
            c = lerp(heel, toe, t) + (0.0, 0.62, 0.0)
            d = S.smin(d, S.torus(P, c, 1.02, 0.11, S.rot_x(90 - 18)), 0.05)
        d = S.smin(d, S.torus(P, ank + (0.0, -0.35, 0.15), 1.08, 0.12, S.rot_x(90)), 0.05)
        d = S.smin(d, S.capsule(P, lerp(heel, toe, 0.86) + (0.0, 0.5, 0.0), lerp(heel, toe, 0.5) + (0.0, 1.25, 0.0), 0.1), 0.05)
    d = np.maximum(d, -P[:, 1] + 0.02)                      # flat on the plinth
    return d


def torso(P):
    d = S.ellipsoid(P, (-0.45, 26.8, 0.5), (4.95, 3.3, 3.5), S.rot_z(-8))           # the pelvis, the right hip up and out
    d = S.smin(d, S.ellipsoid(P, (0.0, 31.1, 0.55), (4.25, 3.9, 3.15)), 2.0)      # the abdomen
    d = S.smin(d, S.ellipsoid(P, (0.15, 37.0, 0.5), (5.4, 4.6, 3.45), S.rot_z(5)), 2.0)    # the thorax, swung back left
    for sx, sh in ((-1, SHOULDER_R), (1, SHOULDER_L)):
        d = S.smin(d, S.ellipsoid(P, (sx * 2.4 - 0.1, 38.2 + 0.3 * sx, -2.1), (2.4, 1.5, 1.0), S.rot_x(-12)), 1.2)
        d = S.smin(d, S.ellipsoid(P, sh + np.array((sx * -0.3, -0.4, 0.0)), (1.75, 1.9, 1.65)), 1.8)   # the deltoids
    d = S.smin(d, S.round_cone(P, (-0.3, 40.6, 0.65), (-0.35, 41.9, 0.5), 1.7, 1.5), 1.0)    # the neck's root
    # the collarbones: from the pit of the neck out to the shoulders, bowed forward at their inner two thirds (an
    # S seen from above) - the bodice drapes over them; inside the neckline their heads show beside the pit
    for sx, sh in ((-1, SHOULDER_R), (1, SHOULDER_L)):
        d = S.smin(d, S.tube(P, [(-0.35 + sx * 0.5, 41.4, -1.05), (-0.35 + sx * 1.6, 41.55, -1.22),
                                 (-0.35 + sx * 3.0, 41.75, -0.85), (sh[0] * 0.86, sh[1] + 0.85, 0.15)],
                             [0.27, 0.25, 0.23, 0.25]), 0.4)
    for sx, sh in ((-1, SHOULDER_R), (1, SHOULDER_L)):                       # the trapezius: the shoulders' slope
        d = S.smin(d, S.capsule(P, (sx * 1.1 - 0.3, 42.2, 1.0), (sh[0] * 0.78, sh[1] + 0.7, 0.9), 1.15), 1.2)
    return d


def arms(P):
    d = S.round_cone(P, SHOULDER_R, ELBOW_R, 1.65, 1.2)
    d = S.smin(d, S.round_cone(P, ELBOW_R, WRIST_R, 1.2, 0.78), 0.6)
    d = S.smin(d, S.ellipsoid(P, lerp(ELBOW_R, WRIST_R, 0.25), (1.35, 1.45, 1.3)), 0.9)     # the forearm's swell
    d = S.smin(d, S.ellipsoid(P, lerp(SHOULDER_R, ELBOW_R, 0.55), (1.6, 2.4, 1.6)), 1.0)    # the biceps
    d = S.smin(d, S.round_cone(P, SHOULDER_L, ELBOW_L, 1.65, 1.2), 0.6)
    d = S.smin(d, S.round_cone(P, ELBOW_L, WRIST_L, 1.2, 0.78), 0.6)
    d = S.smin(d, S.ellipsoid(P, lerp(ELBOW_L, WRIST_L, 0.25), (1.35, 1.45, 1.3)), 0.9)
    d = S.smin(d, S.ellipsoid(P, lerp(SHOULDER_L, ELBOW_L, 0.55), (1.6, 2.4, 1.6)), 1.0)
    return d


def hands(P):
    """The right fist round the grip, the left hand laid over the pommel - fingers carved with drill-fine grooves."""
    g = sword_at(26.45)
    fist = S.ellipsoid(P, g + (-0.05, 0.0, -0.1), (1.05, 0.95, 1.05))
    fist = S.smin(fist, S.round_cone(P, WRIST_R, g + (-0.3, 0.05, 0.05), 0.72, 0.85), 0.4)
    fist = S.smin(fist, S.capsule(P, g + (-0.55, 0.75, -0.75), g + (0.45, 0.9, -0.95), 0.32), 0.15)   # the thumb
    # the four fingers wrapped round the grip: rolls one above the other, each a knuckle proud, the grooves between
    # them drilled (r1-r8 had only grooves on a mitten)
    for i, k in enumerate((-0.5, -0.12, 0.26, 0.6)):
        fing = S.tube(P, [g + (-0.55, k, -0.95), g + (0.1, k + 0.02, -1.22), g + (0.75, k, -0.85), g + (0.95, k - 0.04, -0.2)],
                      [0.2, 0.22, 0.21, 0.18])
        fist = S.smin(fist, fing, 0.05)
    for k in (-0.31, 0.07, 0.43):
        fist = S.smax(fist, -S.capsule(P, g + (-0.4, k, -1.2), g + (0.9, k, -0.9), 0.05), 0.03)
    top = SWORD_TOP + (0.0, 0.55, 0.0)
    palm = S.ellipsoid(P, top + (0.25, 0.42, 0.15), (1.15, 0.5, 1.1), S.rot_z(-12))
    palm = S.smin(palm, S.round_cone(P, WRIST_L, top + (0.6, 0.45, 0.1), 0.72, 0.7), 0.4)
    for k in (-0.55, -0.18, 0.18, 0.52):                                     # the fingers curling over the front
        palm = S.smin(palm, S.tube(P, [top + (k, 0.55, -0.25), top + (k - 0.05, 0.25, -1.05), top + (k - 0.1, -0.45, -1.2)],
                                   [0.24, 0.22, 0.19]), 0.1)
    return S.smin(fist, palm, 0.2)


def sword(P):
    """A guardian's greatsword planted point-down: a lens-section blade with a fuller, a drooping cross with knobs,
    the grip, and the pommel under the left hand."""
    ax = S.normalize(SWORD_TOP - SWORD_POINT)
    guard_y = 22.9
    g = sword_at(guard_y)
    d = S.capsule(P, g, sword_at(27.9), 0.5)                                      # the grip, wire-wrapped
    q0 = P - g
    along = q0 @ ax
    wrap = np.abs(np.sin(along * 7.5 + np.arctan2(q0[:, 0], q0[:, 2]))) * 0.06
    d = d + wrap * (along > 0.2) * (along < 4.8)
    # the pommel: a wheel with a raised rim and a boss at its centre
    pom = S.torus(P, SWORD_TOP + (0, 0.25, 0), 0.58, 0.32, S.rot_x(90) @ S.rot_z(90))
    pom = S.smin(pom, S.ellipsoid(P, SWORD_TOP + (0, 0.25, 0), (0.42, 0.62, 0.62)), 0.1)
    d = S.smin(d, pom, 0.15)
    d = S.smin(d, S.tube(P, [g + (-3.5, -0.55, 0.0), g + (-1.8, -0.05, 0.0), g, g + (1.8, -0.05, 0.0),
                             g + (3.5, -0.55, 0.0)], [0.42, 0.36, 0.48, 0.36, 0.42]), 0.25)
    d = S.smin(d, S.sphere(P, g + (-3.6, -0.6, 0.0), 0.55), 0.2)
    d = S.smin(d, S.sphere(P, g + (3.6, -0.6, 0.0), 0.55), 0.2)
    # the blade: a section 2.5 px wide, 0.7 thick, tapering to the point; a fuller groove down its middle
    q = P - SWORD_POINT
    t = q @ ax
    rel = q - t[:, None] * ax
    L = (g - SWORD_POINT) @ ax
    half_w = 1.55 * np.clip(t / L, 0, 1) ** 0.3 * np.clip(1.0 - 0.18 * (t / L), 0, 1) + 0.05
    across = np.abs(rel[:, 0])
    thick = np.abs(rel[:, 2])
    lens = np.maximum(across - half_w, thick + across * 0.2 - 0.42)
    blade = np.maximum(lens, np.maximum(-t, t - L))
    # the fuller: a groove down both faces of the blade's upper two thirds
    fuller = np.maximum(np.abs(across) - 0.28 * np.clip(t / L, 0, 1) ** 0.3, np.maximum(-(t - L * 0.3), t - L * 0.97))
    blade = blade + 0.09 * np.clip(-fuller / 0.1, 0, 1)
    # the guard's block (the quillon block) where the cross meets the blade
    d = S.smin(d, S.box(P, g + (0.0, -0.15, 0.0), (0.75, 0.5, 0.42), r=0.12), 0.1)
    return S.smin(d, blade, 0.2)


# ------------------------------------------------------------------ the drapery -----------------------------------
def fold_profile(s):
    """Folds as a carver cuts them: round ridges, sharp valleys (the running drill's channel)."""
    return 2.0 * np.abs(np.cos(s / 2.0)) - 1.0


def bodice(P):
    """The chiton above the belt: clinging to the chest ("wet"), fine pleats fanning from each shoulder pin to the
    high belt; short sleeves buttoned along the upper arm."""
    d = torso(P) - 0.32
    y = P[:, 1]
    lx = P[:, 0] - 2.8
    rx = P[:, 0] + 3.0
    phl = np.arctan2(lx, 42.4 - y)
    phr = np.arctan2(rx, 42.0 - y)
    w = np.clip((P[:, 0] + 0.2) / 1.5, -1, 1) * 0.5 + 0.5
    pleat = w * fold_profile(17.0 * phl) + (1 - w) * fold_profile(17.0 * phr)
    band = np.clip((y - 35.3) / 1.0, 0, 1) * np.clip((41.4 - y) / 1.2, 0, 1)
    d = d - 0.2 * pleat * band
    # the neckline: the cloth stops round the neck (a hole the neck rises through, scooped lower at the front) - no
    # flat cut across the shoulders (r3's shelf)
    hole = S.capsule(P, (-0.35, 41.2, 0.2), (-0.45, 47.0, 0.0), 2.0) - 0.9 * np.clip(-(P[:, 2] - 0.2) / 2.5, 0, 1)
    d = S.smax(d, -hole, 0.25)
    d = np.maximum(d, P[:, 1] - 44.0)
    for sh, el in ((SHOULDER_R, ELBOW_R), (SHOULDER_L, ELBOW_L)):
        sleeve = S.round_cone(P, lerp(sh, el, 0.16), lerp(sh, el, 0.4), 1.55, 1.42)
        sleeve = S.smax(sleeve, -S.round_cone(P, lerp(sh, el, 0.4), lerp(sh, el, 0.6), 1.15, 1.05), 0.1)
        d = S.smin(d, sleeve, 0.5)
    return d


def belt(P):
    """The girdle under the breasts (Samothrace's high belt): a broad flat band, beaded along both edges, tied at the
    front in a Hercules knot whose two ends hang (lovish via the main session: "a belt/cincture")."""
    pts = []
    for k in range(33):
        a = 2 * math.pi * k / 32
        pts.append((-0.15 + 4.8 * math.sin(a), 35.0 + 0.25 * math.sin(a), 0.55 - 3.62 * math.cos(a)))
    pts = np.array(pts)
    band = S.tube(P * np.array([1.0, 0.42, 1.0]), pts * np.array([1.0, 0.42, 1.0]), 0.3) * 0.75   # flat: 0.7 tall
    y = P[:, 1]
    rel = P - np.array([-0.15, 0.0, 0.55])
    th = np.arctan2(rel[:, 0], -rel[:, 2])
    beads = np.abs(np.abs(y - 35.0 - 0.25 * np.sin(th)) - 0.52) - 0.09
    beads = np.maximum(beads, np.abs(np.sin(th * 30.0)) * 0.25 - 0.06)
    d = S.smin(band, np.maximum(beads, band - 0.14), 0.04)
    knot = S.torus(P, (-0.4, 35.0, -3.45), 0.42, 0.2, S.rot_x(90) @ S.rot_y(30))
    knot = S.smin(knot, S.torus(P, (-0.4, 35.0, -3.45), 0.42, 0.2, S.rot_x(90) @ S.rot_y(-30)), 0.08)
    d = S.smin(d, knot, 0.1)
    d = S.smin(d, S.tube(P, [(-0.6, 34.7, -3.5), (-0.9, 33.0, -3.65), (-0.75, 31.4, -3.45)], [0.24, 0.21, 0.25]), 0.1)
    d = S.smin(d, S.tube(P, [(-0.2, 34.7, -3.5), (0.35, 33.2, -3.7), (0.7, 31.9, -3.5)], [0.24, 0.21, 0.25]), 0.1)
    return d


def kolpos(P):
    """The cloth blousing out over the belt: a soft roll hanging just below it, gathered in loose vertical folds."""
    y = P[:, 1]
    rel = P - np.array([-0.1, 0.0, 0.55])
    th = np.arctan2(rel[:, 0], -rel[:, 2])
    roll = 0.75 * np.exp(-((y - 33.9) / 0.95) ** 2)
    d = torso(P) - 0.35 - roll - 0.12 * roll * fold_profile(11.0 * th)
    return np.maximum(d, np.maximum(y - 34.8, 32.6 - y))


def skirt_hull(P):
    """The skirt's mass: the cloth pressed to each leg in front (wet drapery: the legs show through) and a drape
    hanging behind them, carried back toward WIND as it nears the hem."""
    y = P[:, 1]
    over = S.round_cone(P, HIP_R, KNEE_R, 2.95, 2.3)
    over = S.smin(over, S.round_cone(P, KNEE_R, (-1.9, 0.2, 0.7), 2.25, 2.7), 1.2)
    over = S.smin(over, S.round_cone(P, HIP_L, KNEE_L, 2.9, 2.25), 1.2)
    over = S.smin(over, S.round_cone(P, KNEE_L, (3.5, 0.2, 1.4), 2.2, 2.6), 1.2)
    w = np.clip((28.0 - y) / 28.0, 0, 1) ** 1.3
    c = np.stack([-0.2 + WIND[0] * 2.6 * w, y, 1.6 + WIND[2] * 3.4 * w], axis=1)
    rx = 4.0 + 1.5 * w
    rz = 2.6 + 1.9 * w
    q = P - c
    drape = (np.sqrt((q[:, 0] / rx) ** 2 + (q[:, 2] / rz) ** 2) - 1.0) * np.minimum(rx, rz) * 0.9
    drape = np.maximum(drape, y - 30.0)
    drape = S.smax(drape, (0.2 + 0.4 * w) - P[:, 2], 1.0)            # behind the legs' front only
    hull = S.smin(over, drape, 1.6)
    # the cloth bridging the legs in front (it hangs from the knee across, never into a slot between them)
    hull = S.smin(hull, S.round_cone(P, (0.3, 22.0, -0.9), (0.8, 0.6, -1.0), 1.9, 2.4), 1.2)
    hull = S.smin(hull, S.ellipsoid(P, (-0.45, 27.6, 0.6), (5.15, 4.5, 3.75), S.rot_z(-8)), 1.8)   # over the hips
    return hull


def _catmull(pts, per=4):
    out = []
    for k in range(len(pts) - 1):
        p0, p1, p2, p3 = pts[max(0, k - 1)], pts[k], pts[k + 1], pts[min(len(pts) - 1, k + 2)]
        for t in np.linspace(0, 1, per, endpoint=False):
            t2, t3 = t * t, t * t * t
            out.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3))
    out.append(pts[-1])
    return np.array(out)


FLYING = []      # the flying hem's folds (skirt_folds fills it): exempt from the hem's cut
DRILLS = []      # the running drill's channels in the valleys between the skirt's folds (skirt_folds fills it)


def skirt_folds():
    """Fold tubes over the hull. Behind and to the lee: wide folds streaming toward WIND, swelling to the hem. On
    the engaged leg's outer side: straight columnar folds (the leg is a column). Across the free leg: catenaries
    hanging from the knee and folds pulled taut over the thigh. Between the legs: folds falling from the crotch."""
    tubes = []
    rng = np.random.RandomState(3)
    # the lee: from the back of the waist round to the right side - big and small folds alternating (Bernini never
    # repeats a fold), and in every valley between two of them the running drill's channel (DRILLS): the dark line
    # that makes a marble fold read at any distance
    lee = []
    n = 17
    for i in range(n):
        f = i / (n - 1.0)
        a = math.radians(-70 - 150 * f)                       # -70 deg (right side) round to -220 (the back, left)
        sx, sz = math.sin(a), -math.cos(a)
        pts = []
        for k in range(9):
            t = k / 8.0
            y = 32.5 - 32.0 * t
            wv = t ** 1.4
            rr = 3.9 + 1.0 * wv
            x = -0.3 + sx * rr * 1.15 + WIND[0] * (2.6 + 1.6 * (1 - abs(f - 0.4))) * wv + 0.4 * math.sin(4.0 * t + 2.0 * f)
            z = 1.0 + sz * rr * 0.9 + WIND[2] * (3.0 + 2.0 * (1 - abs(f - 0.4))) * wv
            pts.append((x, y, z))
        big = 1.0 if i % 2 == 0 else 0.62
        r1 = (0.8 + 0.5 * (1 - abs(f - 0.4)) + 0.15 * rng.uniform()) * big
        lee.append((np.array(pts), np.linspace(0.3 * big + 0.05, r1, 9), a))
        tubes.append(lee[-1][:2])
    for (p0, r0, a0), (p1, r1, a1) in zip(lee[:-1], lee[1:]):
        mid = (p0 + p1) / 2
        inward = np.stack([-np.sin((a0 + a1) / 2) * np.ones(len(mid)), np.zeros(len(mid)), np.cos((a0 + a1) / 2) * np.ones(len(mid))], axis=1)
        DRILLS.append((mid + inward * 0.35, np.linspace(0.12, 0.26, len(mid))))
    # the engaged leg's outer side: columns
    for i in range(3):
        a = math.radians(-82 + 16 * i)
        pts = [(HIP_R[0] + math.sin(a) * 2.9 - 0.1 * k, 27.0 - 3.3 * k, HIP_R[2] - math.cos(a) * 2.6 + 0.12 * k) for k in range(9)]
        tubes.append((np.array(pts), np.linspace(0.3, 0.75, 9)))
    # catenaries hanging from the free knee toward the engaged shin
    cat = []
    for k, (dy, sag) in enumerate(((0.6, 1.3), (1.7, 1.8), (3.0, 2.3), (4.4, 2.75), (5.9, 3.1), (7.5, 3.4), (9.2, 3.6))):
        a = np.array(KNEE_L) + (0.3, -1.0 - dy, -1.25 + 0.1 * k)
        b = np.array(KNEE_R) + (-0.6, -1.6 - dy * 1.3, -1.05 + 0.1 * k)
        pts = [lerp(a, b, t) + (0.0, -sag * math.sin(math.pi * t), 0.5 * math.sin(math.pi * t)) for t in np.linspace(0, 1, 7)]
        cat.append(np.array(pts))
        tubes.append((cat[-1], np.full(7, 0.56 + 0.05 * k)))
    for p0, p1 in zip(cat[:-1], cat[1:]):                       # drilled between the catenaries
        DRILLS.append(((p0 + p1) / 2 + (0.0, 0.0, 0.3), np.full(7, 0.13)))
    # pulled taut over the free thigh from the engaged hip (the thigh shows through)
    for k in range(4):
        a = np.array((-2.4, 30.2 - 1.0 * k, -2.75))
        b = np.array(KNEE_L) + (0.9 - 0.3 * k, 1.4 + 0.5 * k, -1.7)
        pts = [lerp(a, b, t) + (0.0, -0.8 * math.sin(math.pi * t), -0.25 * math.sin(math.pi * t)) for t in np.linspace(0, 1, 6)]
        tubes.append((np.array(pts), np.full(6, 0.27)))
    # THE FLYING HEM (Bernini, Samothrace): on the lee side the skirt's lower folds lift off the legs and stream out
    # behind on the wind, curling up at their ends - the cloth's undercut shows its inside
    for k in range(6):
        f = (k - 2.5) / 2.5
        a = math.radians(-118 - 22 * f)                     # round the back-right
        sx, sz = math.sin(a), -math.cos(a)
        st = np.array((-0.3 + sx * 5.6, 13.0 - 1.2 * abs(f), 1.2 + sz * 4.6))
        out = np.array((WIND[0], 0.0, WIND[2]))
        side = np.array((sz, 0.0, -sx))
        pts = [st, st + out * 2.4 + side * f * 0.6 + (0, -4.5, 0), st + out * 5.0 + side * f * 1.1 + (0, -8.2, 0),
               st + out * 7.0 + side * f * 1.5 + (0, -9.2 + 1.4 * (1 - abs(f)) ** 2, 0),
               st + out * 8.0 + side * f * 1.6 + (0, -7.6 + 1.4 * (1 - abs(f)) ** 2, 0)]
        FLYING.append((_catmull(np.array(pts), 3), np.array([0.8, 0.95, 1.1, 1.2, 1.25, 1.25, 1.2, 1.1, 1.0, 0.9, 0.8, 0.7, 0.6])))
    # diagonal folds across the front from the high (engaged) hip toward the free foot
    for k in range(4):
        a = np.array((-3.4 + 0.3 * k, 27.5 - 2.2 * k, -2.55))
        b = np.array((3.6, 6.0 - 1.3 * k, -2.2 + 0.2 * k))
        pts = [lerp(a, b, t) + (0.0, 0.0, -0.6 * math.sin(math.pi * t) + 0.35) for t in np.linspace(0, 1, 7)]
        tubes.append((np.array(pts), np.full(7, 0.4)))
    # falling from the crotch between the legs
    for k in range(2):
        x0 = 0.15 + 0.9 * (k - 0.5)
        pts = [(x0 + 0.2 * t, 22.0 - 21.4 * t, -2.9 - 0.9 * t + 0.25 * math.sin(3 * t)) for t in np.linspace(0, 1, 8)]
        tubes.append((np.array(pts), np.linspace(0.3, 0.7, 8)))
    return tubes


FOLDS = skirt_folds()


def hem_height(P):
    """The hem's height above the plinth by direction: brushing the plinth at the front, lifted by the wind on the
    lee side (where the inside of the cloth shows, cut deep)."""
    rel = P - np.array([0.2, 0.0, 1.0])
    th = np.arctan2(rel[:, 0], -rel[:, 2])
    lee = np.maximum(0.0, np.sin(th) * WIND[0] + -np.cos(th) * WIND[2])
    return 0.35 + 0.35 * (1 + np.sin(6.0 * th + 0.8)) + 4.2 * lee ** 2.0


def skirt(P):
    d = skirt_hull(P)
    prims = []
    for pts, radii in FOLDS:
        lo, hi = pts.min(axis=0), pts.max(axis=0)
        prims.append((lambda Q, pts=pts, radii=radii: S.tube(Q, pts, radii), (lo + hi) / 2, np.linalg.norm(hi - lo) / 2 + 2.5))
    d = S.bounded_smin(P, prims, 0.32, base=d)
    drills = []
    for pts, radii in DRILLS:
        lo, hi = pts.min(axis=0), pts.max(axis=0)
        drills.append((lambda Q, pts=pts, radii=radii: S.tube(Q, pts, radii), (lo + hi) / 2, np.linalg.norm(hi - lo) / 2 + 1.0))
    d = S.smax(d, -S.bounded_min(P, drills), 0.08)
    # a shell near the hem (the cloth's thickness, so the lifted hem shows its inside), and the hem itself
    y = P[:, 1]
    shell = np.maximum(d, -(d + 1.4))
    d = np.where((y < 8.0) & (hem_height(P) > 1.5), shell, d)
    d = S.smax(d, hem_height(P) - y, 0.15)
    fly = []
    for pts, radii in FLYING:
        lo, hi = pts.min(axis=0), pts.max(axis=0)
        fly.append((lambda Q, pts=pts, radii=radii: S.tube(Q, pts, radii), (lo + hi) / 2, np.linalg.norm(hi - lo) / 2 + 2.0))
    d = S.bounded_smin(P, fly, 0.45, base=d)
    # the hem's border (lovish via the main session: "embroidery trim"): a band between two incised lines a little
    # above the hem, a running wave carved in it - cut into whatever surface the cloth has there
    rel = P - np.array([0.2, 0.0, 1.0])
    th = np.arctan2(rel[:, 0], -rel[:, 2])
    above = y - hem_height(P)
    lines = np.minimum(np.abs(above - 0.75), np.abs(above - 1.95)) - 0.07
    wave = np.abs(above - 1.35 - 0.33 * np.sin(th * 22.0)) - 0.07
    trim = np.clip(-np.minimum(lines, wave) / 0.07, 0, 1) * (y < 9.0)
    d = d + 0.07 * trim
    return np.maximum(d, y - 33.6)


def mantle_paths():
    """The himation: over the left shoulder, down the back diagonally to the right hip, round it, and its end flying
    off behind the right side on the wind - a bundle of folds that fan apart and TWIST as the cloth turns in the air
    (Bernini's flying drapery: the sheet shows its face, then its edge, then curls up at the end)."""
    centre = np.array([(4.0, 39.4, -2.3), (5.0, 42.2, -0.2), (4.3, 41.4, 3.0), (1.0, 35.0, 4.35), (-3.6, 28.6, 3.9),
                       (-6.0, 24.8, 2.2), (-7.8, 19.4, 3.8), (-10.6, 15.4, 6.8), (-13.6, 14.4, 9.8), (-15.4, 16.6, 12.6),
                       (-15.8, 20.2, 14.6)], dtype=float)
    width = np.array([(0.35, 0.5, 0.0), (0.25, 0.2, 0.5), (0.5, 0.5, 0.0), (0.9, 1.6, 0.0), (0.9, 2.0, 0.0),
                      (0.6, 1.8, 0.0), (0.6, 2.0, 0.3), (0.5, 2.4, 0.8), (0.3, 2.9, 1.2), (0.2, 3.3, 1.6),
                      (0.1, 3.4, 1.8)], dtype=float)
    twist = np.array([0, 0, 0, 0, 0, 0, 10, 35, 70, 110, 150], dtype=float)
    tang = np.gradient(centre, axis=0)
    for k in range(len(centre)):
        if twist[k]:
            width[k] = axis_rot(tang[k], twist[k]) @ width[k]
    out = []
    n = 7
    for i in range(n):
        f = (i - (n - 1) / 2) / ((n - 1) / 2)              # -1 .. 1 across the cloth's width
        pts = centre + width * f
        sm = _catmull(pts)
        u = np.linspace(0, 1, len(sm))
        sm[:, 1] += 0.55 * np.sin(10.0 * u + 1.9 * f) * np.clip((u - 0.55) / 0.2, 0, 1)
        radii = 0.42 + 0.2 * np.clip(u / 0.25, 0, 1) + 0.2 * np.clip((u - 0.5) / 0.3, 0, 1) - 0.3 * np.clip((u - 0.88) / 0.12, 0, 1) ** 2
        radii = radii * (1.18 if i % 2 == 0 else 0.72)          # ridges and valleys: the sheet carries its folds
        if abs(f) > 0.99:
            radii = radii * 0.85
        out.append((sm, radii))
    return out


MANTLE = mantle_paths()


def swag_paths():
    """The himation's second turn: a rolled band of cloth swept across the front of the hips from the right hip to
    the left thigh and hanging down its side - the great diagonal of the Samothrace Victory and Bernini's angels, a
    mass of deep folds across the front where r1-r7 had only the wet skirt (a columnar dress from the front)."""
    centre = np.array([(-5.3, 28.4, 0.9), (-4.7, 27.0, -2.3), (-2.4, 25.4, -3.95), (0.6, 24.5, -4.25), (3.1, 23.6, -3.6),
                       (4.75, 22.3, -1.9), (5.35, 19.8, -0.7), (5.5, 16.2, -0.3), (5.3, 12.8, -0.6)], dtype=float)
    width = np.array([(0.0, 0.9, 0.3), (0.0, 1.3, 0.2), (0.0, 1.5, 0.0), (0.0, 1.5, 0.0), (0.2, 1.4, 0.0),
                      (0.6, 1.0, 0.4), (0.9, 0.3, 0.9), (1.0, 0.0, 1.0), (1.1, 0.0, 0.9)], dtype=float)
    out = []
    n = 6
    for i in range(n):
        f = (i - (n - 1) / 2) / ((n - 1) / 2)
        sm = _catmull(centre + width * f)
        u = np.linspace(0, 1, len(sm))
        sm[:, 2] -= 0.7 * np.sin(math.pi * u) * (1 - 0.6 * abs(f))       # the band bellies out between its ends
        radii = (0.62 + 0.18 * np.sin(math.pi * u)) * (1.3 if i % 2 == 0 else 0.62)
        radii = radii * (1.0 - 0.35 * np.clip((u - 0.85) / 0.15, 0, 1))
        out.append((sm, radii))
    return out


SWAG = swag_paths()


def swag(P):
    prims = []
    for pts, radii in SWAG:
        for k in range(0, len(pts) - 1, 4):
            seg, rr = pts[k:k + 5], radii[k:k + 5]
            lo, hi = seg.min(axis=0), seg.max(axis=0)
            prims.append((lambda Q, seg=seg, rr=rr: S.tube(Q, seg, rr), (lo + hi) / 2, np.linalg.norm(hi - lo) / 2 + 1.5))
    return S.bounded_smin(P, prims, 0.28)


def mantle(P):
    prims = []
    for pts, radii in MANTLE:
        for k in range(0, len(pts) - 1, 4):
            seg, rr = pts[k:k + 5], radii[k:k + 5]
            lo, hi = seg.min(axis=0), seg.max(axis=0)
            prims.append((lambda Q, seg=seg, rr=rr: S.tube(Q, seg, rr), (lo + hi) / 2, np.linalg.norm(hi - lo) / 2 + 1.5))
    return S.bounded_smin(P, prims, 0.3)


# ------------------------------------------------------------------ the head: a Roman portrait -----------------------
def head_frame():
    return S.rot_y(HEAD_TURN) @ S.rot_x(HEAD_BOW)


# THE FACE, r10 (lovish, Oct 21 16:13: "the face could be more realistic and better"): rebuilt from the anatomy a
# carver blocks out before he cuts a feature - the skull's masses at their measured places, then the features ON
# them. r1-r9 stacked features on a ball: the nose a beak in profile, the mouth recessed behind it, the jaw a mask's
# edge, the eyes slits with no eyeball, no ears, a column for a neck.
#
# The canon (head-local px: y up, -z the face's front, x to the figure's left; m = |x| for the mirrored features):
# the head is 3.5 units from the chin to the skull's top (Loomis; the Doryphoros' 1:7 figure), one unit = 2.06 px:
# chin -3.7 | nose base -1.64 | brow line 0.42 | hairline 2.48 | skull 3.5, the eyes on the half (-0.1). Widths
# against the Augustus of Prima Porta and the Antinous Mondragone, as real anthropometry gives them (Farkas): the
# eyes one eye-width apart (inner corners 0.48, outer 1.48, the pupil 0.98); the nose's wings as wide as the inner
# corners; the mouth as wide as the pupils are apart; the cheekbones 2.3; the jaw's angle 1.8 (a strong classical
# jaw), the chin 0.65. Depths (Ricketts' E-line, the Greek profile): the glabella 0.45 ahead of the cornea, the
# brow and nose one straight line, the nose's tip 0.8 proud of the brow-chin line, the lips just behind the line
# from the nose's tip to the chin (upper 0.13, lower 0.07), the chin firm under the brow. The ear's top at the brow
# line, its lobe at the nose's base, set behind the jaw's ramus and tilted back 15 degrees.
FACE_CHIN, FACE_NOSE, FACE_BROW, FACE_HAIRLINE = -3.7, -1.64, 0.42, 2.48
EYE_C = np.array([0.98, -0.1, -2.22])         # the eyeball's centre (mirrored space)
EYE_R = 0.46                                 # a real eyeball (24 mm) in a 7.2 px head is 0.4; a touch more to read
# the gaze: down and a touch in (the head is bowed over the plinth's edge; the eyes meet the viewer below)
GAZE = S.normalize((-0.06, -0.15, -1.0))     # mirrored space: -x is toward the nose


def eye_frame():
    """Columns: the eye's right (+x in mirrored space, away from the nose), up, and the gaze."""
    g = GAZE
    r = S.normalize(np.cross((0.0, 1.0, 0.0), g))
    if r[0] < 0:
        r = -r
    u = np.cross(g, r)
    if u[1] < 0:
        u = -u
    return np.stack([r, u, g], axis=1)


def _skull(q, m):
    """The bone masses under the skin: the cranium's ovoid, the forehead's upright front with its two frontal
    eminences, the temples' planes, the cheekbones and their arches back to the ear, the upper jaw (the maxilla that
    carries the nose and the dental arch) and the mandible - its body a rounded edge from the chin to the angle, the
    ramus rising to the ear."""
    x, y, z = m[:, 0], m[:, 1], m[:, 2]
    d = S.ellipsoid(q, (0.0, 0.8, 0.45), (2.5, 2.72, 3.08))                                  # the cranium
    d = S.smin(d, S.ellipsoid(q, (0.0, 1.32, -1.55), (1.95, 1.3, 1.25)), 0.7)                # the forehead's front
    # the forehead's front turning to the temples in a plane (the temporal line), and the temple a flat plane above
    # the cheekbone's arch (r9's ball of a skull read soft)
    d = S.smax(d, 0.8 * x - 0.6 * z - 2.95 - 0.1 * np.clip(y - 1.8, 0, 3) ** 2, 0.35)
    d = S.smax(d, x - 2.42 + 0.06 * np.clip(-y + 0.4, 0, 3) - 0.04 * np.clip(z - 0.5, 0, 3), 0.3)
    # the upper jaw: the block the nose sits on and the dental arch (a cylinder - the mouth's corners turn back on it)
    d = S.smin(d, S.ellipsoid(q, (0.0, -1.05, -1.55), (1.72, 1.15, 1.42)), 0.6)
    d = S.smin(d, S.ellipsoid(q, (0.0, -2.28, -1.9), (1.12, 0.78, 1.3)), 0.45)               # the dental arch
    # the cheekbone: its body under the eye's outer half, sloping back; the arch running back to the ear
    d = S.smin(d, S.ellipsoid(m, (1.55, -0.68, -1.72), (0.66, 0.42, 0.56), S.rot_y(-28)), 0.45)
    d = S.smin(d, S.capsule(m, (1.95, -0.62, -1.2), (2.3, -0.5, 0.25), 0.26), 0.4)
    # the mandible: the lower border from the chin to the angle (rounded, not a blade - r9's mask edge), the angle
    # rounded, the ramus up to the condyle before the ear; the chin's mound and its two tubercles (a squared chin)
    jaw = S.tube(m, [(0.0, -3.4, -2.5), (0.62, -3.34, -2.25), (1.12, -3.0, -1.45), (1.42, -2.62, -0.38),
                     (1.46, -2.42, 0.22)], [0.36, 0.37, 0.38, 0.38, 0.36])
    jaw = S.smin(jaw, S.capsule(m, (1.46, -2.42, 0.22), (1.95, -0.9, 0.42), 0.34), 0.35)
    d = S.smin(d, jaw, 0.9)
    d = S.smin(d, S.ellipsoid(q, (0.0, -3.08, -2.62), (0.82, 0.62, 0.58)), 0.45)             # the chin
    d = S.smin(d, S.ellipsoid(m, (0.42, -3.38, -2.6), (0.34, 0.3, 0.32)), 0.3)               # its tubercles
    return d


def _soft(q, m, d):
    """The flesh over the bone: young, full cheeks (the malar pad under the eye, the buccal fullness over the
    molars), the masseter over the jaw's angle, the fullness round the mouth (the orbicularis) and the knot of muscle
    beside each corner of the mouth (the modiolus)."""
    d = S.smin(d, S.ellipsoid(m, (1.08, -1.3, -2.25), (0.56, 0.6, 0.42), S.rot_z(-12)), 0.55)   # the malar pad
    d = S.smin(d, S.ellipsoid(m, (1.18, -2.0, -1.6), (0.5, 0.82, 0.75)), 0.55)                # the buccal fullness
    d = S.smin(d, S.ellipsoid(m, (1.52, -1.75, -0.15), (0.34, 0.95, 0.65), S.rot_x(12)), 0.45)  # the masseter
    # behind the ramus to the ear and the mastoid: the parotid's fullness - the side of the face runs straight back
    # to the ear (r10c's slices: the head narrowed behind the cheek, so the cheek's turn stood as a ridge and the
    # ear's lobe floated off the head)
    d = S.smin(d, S.ellipsoid(m, (1.62, -1.15, 0.55), (0.48, 1.0, 0.85)), 0.55)
    d = S.smin(d, S.ellipsoid(m, (1.55, -1.25, 1.35), (0.45, 0.8, 0.6)), 0.5)                 # the mastoid
    # the masseter's front edge and the buccal fat before it: no hollow under the cheekbone on a youth (r10d's
    # slices: a waist between the cheek and the parotid read as a vertical groove)
    d = S.smin(d, S.ellipsoid(m, (1.48, -1.55, -1.0), (0.42, 0.9, 0.72)), 0.5)
    d = S.smin(d, S.ellipsoid(q, (0.0, -2.12, -2.62), (0.98, 0.58, 0.62)), 0.35)              # round the mouth
    d = S.smin(d, S.ellipsoid(m, (0.98, -2.2, -2.72), (0.17, 0.22, 0.15)), 0.12)              # the modiolus
    return d


def _brow_and_sockets(q, m, d):
    """The brow ridge (the supraorbital margin) from the glabella out to the zygomatic process, its lower edge a
    crisp shelf over the socket - the shadow line of every classical head; the socket cut up under it, deepest at
    its top inner corner (where the brow meets the nose) and shallow at the cheek."""
    x, y, z = m[:, 0], m[:, 1], m[:, 2]
    brow = S.tube(m, [(0.0, 0.44, -2.74), (0.55, 0.5, -2.7), (1.1, 0.52, -2.6), (1.6, 0.4, -2.25), (1.98, 0.12, -1.72)],
                  [0.3, 0.3, 0.27, 0.22, 0.2])
    d = S.smin(d, brow, 0.4)
    # the socket: a smooth dish pressed into the face - deepest under the brow's inner end, nothing at its rim (r10's
    # boolean cut left a convex lip round the hole: goggles); the brow's underside a soft shelf back to the lid
    def dish(c, radii, depth, R=None):
        rho2 = np.sum((S.frame(m, c, R) / np.asarray(radii)) ** 2, axis=1)
        return depth * np.clip(1.0 - rho2, 0, 1) ** 2
    d = d + dish((0.92, 0.04, -2.72), (0.78, 0.58, 0.75), 0.3, S.rot_z(-6))
    d = d + dish((0.62, 0.12, -2.8), (0.32, 0.3, 0.4), 0.1)          # the inner corner, by the nose's root
    d = d + dish((0.95, 0.26, -2.62), (0.62, 0.2, 0.5), 0.07, S.rot_z(-8))
    return d


def _eye(m, d):
    """The eyeball under its lids. The upper lid a thick band wrapping the ball (Greek marble shows its thickness: a
    ledge 0.1 proud of the ball, a line of shadow under it), heavy and half lowered over the iris - the serene,
    inward look of the Antinous; above it the crease, and the fold of flesh rising from the crease to the brow's
    shelf (fading at the outer corner under the brow). The lower lid thinner, lowest a little outside the pupil,
    sliding down into the cheek. The corners: the inner with its caruncle's notch, the outer higher and set back.
    The iris a shallow dish with a soft rim and the pupil drilled under the upper lid as the Hadrianic carvers did
    (a bean of shadow), so the eye keeps a dark accent in vanilla's flat light without r6-r9's fine ring (which
    decimated into a crumpled target).
    The lids are solid tissue, not shells: each fills from the ball out to the face's surface beyond its margin
    (r10a's shells left the socket's hollow showing as a dark ring round a bare ball)."""
    E = eye_frame()
    e = S.frame(m, EYE_C, E)                         # (out from the nose, up, along the gaze)
    rel = m - EYE_C
    rr = np.linalg.norm(rel, axis=1)
    ball = rr - EYE_R
    rho = np.sqrt(e[:, 0] ** 2 + e[:, 1] ** 2)
    front = np.clip(e[:, 2] / EYE_R, 0, 1)
    dish = 0.028 * np.clip(1.0 - (rho / 0.2) ** 4, 0, 1) * front                        # the iris' dish
    ball = ball + dish
    # the pupil: a shallow bean drilled under the upper lid (r10e's 0.3 px wedge read as a hole)
    pupil = S.ellipsoid(e, (0.0, 0.06, EYE_R + 0.02), (0.085, 0.065, 0.1))
    pupil = S.smin(pupil, S.ellipsoid(e, (0.0, 0.09, EYE_R + 0.02), (0.12, 0.045, 0.08)), 0.03)
    ball = S.smax(ball, -pupil, 0.03)
    d = S.smin(d, ball, 0.03)
    # the orbit behind the ball filled (the socket's cut left closed voids above the ball: hidden surfaces)
    d = S.smin(d, S.smax(S.sphere(m, EYE_C + np.array([0.0, 0.08, 0.22]), EYE_R + 0.12), EYE_C[2] - 0.02 - m[:, 2], 0.05), 0.05)
    # where a point sits round the eye: across (a: -1 the inner corner .. +1 the outer) and its elevation round the
    # eye's lateral axis (0 along the gaze)
    a = e[:, 0] / 0.5
    ac = np.clip(a, -1.0, 1.0)
    elev = np.arctan2(e[:, 1], np.maximum(e[:, 2], 1e-3))
    # the margins: an almond meeting at the corners, the outer corner a touch higher
    up_m = 0.4 * (1.0 - ac * ac) * (1.0 - 0.25 * ac) + 0.05 * a
    lo_m = -0.46 * (1.0 - ac * ac) * (1.0 + 0.25 * ac) + 0.05 * a
    t_u, t_l = 0.1, 0.06
    # the upper lid: its band (R + t_u) from the margin to the crease, then the fold swelling out to the brow's shelf
    above = elev - up_m
    # (beyond the margin the lid covers the ball at a constant thickness, so the eyeball's round shows through the
    # lid as it does in life and the face blends into the cover: r10c-h's tissue swelled to a plateau proud of the
    # face - bags and a ring - and r10i's sank into it, leaving the ball popping out of a recess)
    r_u = EYE_R + t_u
    lid_u = S.smax(rr - r_u, -above * EYE_R, 0.03)
    # the lower lid: a thinner band, then the cheek's surface rising from below it
    below = lo_m - elev
    r_l = EYE_R + t_l
    lid_l = S.smax(rr - r_l, -below * EYE_R, 0.03)
    lids = np.minimum(lid_u, lid_l)
    # the corners close (past a = +-1 both margins are crossed: the lids are one solid there)
    lids = S.smax(lids, -e[:, 2] - 0.05, 0.1)          # only round the front of the ball
    d = S.smin(d, lids, 0.08)
    # the crease between the lid's band and the fold: a soft groove two cells wide pressed into the surface (r10c-e
    # cut it as a slot finer than the 0.065 px cells: a dashed line round each eye), fading at the outer third under
    # the brow's fleshy fold and at the inner corner
    fade = np.clip(1.0 - (a - 0.35) / 0.5, 0, 1) * np.clip((a + 0.95) / 0.3, 0, 1)
    groove = np.clip(1.0 - np.abs(above - 0.44) * EYE_R / 0.075, 0, 1) ** 2
    near = np.clip(1.0 - np.abs(rr - EYE_R - t_u - 0.05) / 0.2, 0, 1) * np.clip(e[:, 2] / 0.1, 0, 1)
    d = d + 0.045 * groove * fade * near
    # the caruncle's notch at the inner corner
    d = S.smax(d, -S.sphere(m, EYE_C + E @ np.array([-0.5, -0.03, EYE_R * 0.6]), 0.05), 0.03)
    return d


def _nose(q, m, d):
    """The nose: the bridge narrow at the root and continuing the forehead's line (the Greek profile), its sides
    flat planes stepping down to the cheek; the tip (the lobule) a rounded wedge, the wings (alae) as their own
    rounded lobes set back from the tip with the alar groove round them; the columella under the tip and the
    nostrils drilled up from below (the statue is seen from below)."""
    x, y, z = m[:, 0], m[:, 1], m[:, 2]
    # the bridge: a wedge from the root to the tip
    root, tip = np.array([0.0, 0.3, -2.72]), np.array([0.0, -1.18, -3.45])
    nose = S.round_cone(q, root, tip, 0.25, 0.27)
    ax = S.normalize(tip - root)
    t = np.clip((q - root) @ ax / np.linalg.norm(tip - root), 0, 1)
    axis_z = root[2] + (tip[2] - root[2]) * t
    nose = S.smax(nose, x * 1.25 + (z - axis_z) * 0.42 - 0.2 - 0.12 * t, 0.06)   # the flat sides
    d = S.smin(d, nose, 0.22)
    # the side walls: a wedge filling from the bridge down to the cheek at the inner eye corner and the wing
    wall = S.smax(S.ellipsoid(q, (0.0, -0.75, -2.85), (0.62, 0.95, 0.6)), x * 0.9 + (z + 2.6) * 0.8 - 0.35, 0.15)
    d = S.smin(d, wall, 0.3)
    d = S.smin(d, S.ellipsoid(q, (0.0, -1.3, -3.5), (0.21, 0.22, 0.21)), 0.16)              # the lobule
    ala = S.tube(m, [(0.16, -1.52, -3.5), (0.33, -1.44, -3.34), (0.42, -1.5, -3.16), (0.36, -1.62, -3.06)],
                 [0.09, 0.14, 0.13, 0.09])
    d = S.smin(d, ala, 0.14)
    # the alar groove: round the wing's top and back, where it meets the cheek
    d = S.smax(d, -S.tube(m, [(0.26, -1.24, -3.4), (0.5, -1.33, -3.2), (0.6, -1.52, -3.04), (0.5, -1.7, -3.0)], 0.04), 0.05)
    # the underside: from the tip back to the lip, the columella between the two nostrils
    d = S.smax(d, -S.ellipsoid(m, (0.19, -1.6, -3.34), (0.09, 0.045, 0.13), S.rot_x(-25)), 0.03)
    d = S.smax(d, -S.ellipsoid(m, (0.17, -1.61, -3.29), (0.06, 0.07, 0.07)), 0.02)
    return d


MOUTH_Y = -2.27            # the line between the lips (the stomion)
MOUTH_W = 0.8              # the corners' x
MOUTH_Z = -3.2             # the stomion's depth at the middle


def _mouth_arc(x):
    """The line between the lips across the dental arch: (y, z) at each x - turning back to the corners, the
    corners level (serene, not smiling: lovish, Oct 21 16:13 - "noble", not "nice")."""
    u = np.clip(np.abs(x) / MOUTH_W, 0, 1.3)
    return MOUTH_Y + 0.045 * u * u, MOUTH_Z + 0.4 * u ** 2.1      # r11: the corners a hair up (r10 read melancholy)


def _unbend(q):
    """Points moved into the mouth's straightened frame: the dental arch's curve taken out, so a lip is an ellipsoid
    there and wraps the arch here."""
    y0, z0 = _mouth_arc(np.zeros(1))
    ay, az = _mouth_arc(q[:, 0])
    out = q.copy()
    out[:, 1] -= ay - y0[0]
    out[:, 2] -= az - z0[0]
    return out


def _mouth(q, m, d):
    """The philtrum (two ridges and the groove between from the nose to the lip), the upper lip's vermilion - a plane
    sloping back to the line between the lips, its top a crisp edge (the carver's "white roll") bowed in two peaks
    over the philtrum's ridges, the tubercle at its middle - the fuller lower lip rounding forward and under into its
    shadow, the line between them deepest at the corners (drilled), the hollow under the lower lip (the mentolabial
    sulcus) and the nasolabial fold from the nose's wing toward the corner of the mouth - faint, as on a youth."""
    x, y, z = m[:, 0], m[:, 1], m[:, 2]
    # the philtrum: the ridges just proud of the lip's skin, the groove between
    d = S.smin(d, S.capsule(m, (0.15, -1.68, -3.17), (0.19, -1.97, -3.26), 0.045), 0.06)
    d = S.smax(d, -S.capsule(q, (0.0, -1.7, -3.22), (0.0, -1.96, -3.3), 0.04), 0.05)
    u = _unbend(q)
    # the upper lip: a lens tilted so its front faces down and forward; the bow cut into its top edge
    up = S.ellipsoid(u, (0.0, MOUTH_Y + 0.13, MOUTH_Z - 0.07), (0.86, 0.16, 0.14), S.rot_x(-28))
    bow = 0.045 * np.exp(-(np.abs(u[:, 0]) - 0.19) ** 2 / 0.012) - 0.03 * np.exp(-u[:, 0] ** 2 / 0.005)
    up = S.smax(up, (u[:, 1] - (MOUTH_Y + 0.26 + bow)) * 1.5, 0.03)
    up = S.smin(up, S.ellipsoid(u, (0.0, MOUTH_Y + 0.08, MOUTH_Z - 0.12), (0.13, 0.08, 0.07)), 0.04)   # tubercle
    d = S.smin(d, up, 0.07)
    # the lower lip: fuller, its front round, a faint groove down its middle
    lo = S.ellipsoid(u, (0.0, MOUTH_Y - 0.16, MOUTH_Z - 0.04), (0.74, 0.19, 0.15), S.rot_x(18))
    lo = S.smax(lo, -S.capsule(u, (0.0, MOUTH_Y - 0.08, MOUTH_Z - 0.2), (0.0, MOUTH_Y - 0.24, MOUTH_Z - 0.2), 0.03), 0.03)
    d = S.smin(d, lo, 0.08)
    # the line between the lips: a V groove, closing to nothing at its root, drilled at the corners
    ay, az = _mouth_arc(x)
    # the line between the lips: a V pressed in along the arc, 0.07 deep at the middle and deeper at the corners,
    # wide enough for the cells to carry (r10c-e's slot was finer than a cell: a dashed line)
    along = np.clip(1.0 - (x / MOUTH_W) ** 6, 0, 1)            # r11: ends at the corner (r10's ran past it: a crack)
    v = np.clip(1.0 - np.abs(y - ay) / 0.075, 0, 1) ** 1.6
    depth = 0.07 + 0.05 * (x / MOUTH_W) ** 4
    d = d + depth * v * along * np.clip((az + 0.3 - z) / 0.3, 0, 1)
    cy, cz = _mouth_arc(np.array([MOUTH_W]))
    pit = np.clip(1.0 - np.linalg.norm(m - np.array([MOUTH_W - 0.04, cy[0] + 0.01, cz[0] - 0.05]), axis=1) / 0.09, 0, 1) ** 2
    d = d + 0.035 * pit
    # the hollow under the lower lip, and the chin's mound below it
    d = S.smax(d, -S.capsule(q, (-0.45, -2.74, -3.16), (0.45, -2.74, -3.16), 0.09), 0.16)
    # the nasolabial fold: a soft crease from above the nose's wing toward the corner of the mouth
    d = S.smax(d, -S.tube(m, [(0.56, -1.36, -3.05), (0.78, -1.75, -2.96), (0.98, -2.12, -2.8)], 0.032), 0.06)
    return d


def _ears(q, m, d):
    """The ears: the top at the brow line, the lobe at the nose's base, behind the jaw's ramus, tilted back 15 degrees
    and standing out from the head at the back edge - the rim (helix) rolled round from the crus over the top and
    down the back, the antihelix's ridge inside it, the bowl (concha) cut deep, the tragus over the canal, the lobe."""
    # the ear's frame: u forward (the face's way), v up, w out from the head
    u_ax = S.normalize((0.0, -0.26, -0.97))            # tilted back: the top sits behind the bottom
    w_ax = S.normalize((1.0, 0.0, -0.55))              # the ear's face looks out and forward: its back edge stands off
    v_ax = np.cross(w_ax, u_ax)
    if v_ax[1] < 0:
        v_ax = -v_ax
    u_ax = np.cross(v_ax, w_ax)
    R = np.stack([u_ax, v_ax, w_ax], axis=1)
    o = np.array([2.26, -0.62, 0.72])
    e = S.frame(m, o, R)
    plate = S.ellipsoid(e, (-0.12, 0.1, 0.16), (0.5, 0.98, 0.2))
    helix = S.tube(e, [(0.14, 0.32, 0.12), (0.05, 0.78, 0.16), (-0.25, 0.98, 0.2), (-0.55, 0.8, 0.24),
                       (-0.66, 0.35, 0.24), (-0.58, -0.15, 0.22), (-0.38, -0.55, 0.18)], [0.08, 0.1, 0.11, 0.11, 0.11, 0.1, 0.09])
    lobe = S.ellipsoid(e, (-0.18, -0.72, 0.12), (0.28, 0.3, 0.12))
    ear = S.smin(S.smin(plate, helix, 0.08), lobe, 0.12)
    # the scapha (the groove inside the rim) and the concha (the bowl), the antihelix between them
    ear = S.smax(ear, -S.tube(e, [(0.0, 0.62, 0.3), (-0.3, 0.76, 0.32), (-0.48, 0.42, 0.33), (-0.45, -0.05, 0.3)], 0.07), 0.04)
    ear = S.smax(ear, -S.ellipsoid(e, (0.0, -0.08, 0.3), (0.26, 0.36, 0.2)), 0.06)
    ear = S.smin(ear, S.ellipsoid(e, (0.24, -0.16, 0.18), (0.1, 0.13, 0.08)), 0.04)          # the tragus
    ear = S.smax(ear, -e[:, 2] - 0.05, 0.1)                                                   # its back on the head
    return S.smin(d, ear, 0.1)


def face(q):
    """A classical portrait head (the Augustus of Prima Porta's planes, the Antinous' youth, the Apollo Belvedere's
    profile): the skull's masses, the flesh over them, the brow and sockets, the eyes under their lids, the nose,
    the mouth, the ears - each at its measured place (the canon above), then the neck (body space: neck_field)."""
    m = S.mirror_x(q)
    d = _skull(q, m)
    d = _soft(q, m, d)
    d = _brow_and_sockets(q, m, d)
    d = _eye(m, d)
    d = _nose(q, m, d)
    d = _mouth(q, m, d)
    d = _ears(q, m, d)
    # the neck's top under the skull and behind the jaw (the rest of the neck is neck_field, in body space)
    d = S.smin(d, S.round_cone(q, (0.0, -1.6, 0.75), (0.0, -3.9, 0.85), 1.38, 1.45), 0.9)
    return d


HAIR_C, HAIR_R = np.array([0.0, 0.62, 0.62]), np.array([3.05, 3.1, 3.5])
CROWN = np.array([0.15, 1.0, 0.25])         # the whorl's direction from the hair's centre (a little behind the top)


def hair_theta_end(az):
    """How far down the hair comes (the polar angle from the top) by azimuth (0 = the brow): high on the forehead,
    curving up round the top of each ear (r10: the ears show, as on the Augustus - r1-r9's hair hid them), down to
    the nape at the back."""
    az = np.asarray(az)
    base = 0.9 + 1.3 * np.abs(np.sin(az / 2)) ** 0.6
    return base - 0.5 * np.exp(-((np.abs(az) - 1.66) / 0.34) ** 2)


def hair_surf(th, az):
    return HAIR_C + HAIR_R * np.array([math.sin(th) * math.sin(az), math.cos(th), -math.sin(th) * math.cos(az)])


HAIR_LOCKS = 26             # locks round the head at the hairline


def crown_frame():
    """Columns: the hair's axes with the whorl (CROWN) as the pole - x across, the pole, z toward the brow's back."""
    c = S.normalize(CROWN)
    f = S.normalize(np.array([0.0, 0.0, -1.0]) - c * (-c[2]))      # the brow's direction, square to the pole
    return np.stack([np.cross(c, f), c, f], axis=1)


_CF = crown_frame()


def lock_coords(q):
    """Every point's place in the combed hair: (s, t, th, az) - s the lock it lies in (an integer per lock, its
    fraction across it), t how far down the lock from the whorl (0 at the whorl, 1 at the hairline), th / az its polar
    angle and azimuth on the cap (az 0 = the brow). The locks are the lines of a stream combed out from the whorl,
    spiralling a little; toward the hairline each one bends to the side into its comma (the hook), the brow's locks
    parting at the middle and flicking out to the temples - the Augustus of Prima Porta's fork and pincer."""
    rel = (q - HAIR_C) / HAIR_R
    rn = np.maximum(np.linalg.norm(rel, axis=1), 1e-6)
    u = rel / rn[:, None]
    th = np.arccos(np.clip(u[:, 1], -1, 1))
    az = np.arctan2(u[:, 0], -u[:, 2])
    c = u @ _CF                                                  # in the whorl's frame
    thc = np.arccos(np.clip(c[:, 1], -1, 1))
    azc = np.arctan2(c[:, 0], -c[:, 2])
    end = hair_theta_end(az)
    t = np.clip(th / end, 0, 1.2)
    hook = np.clip((t - 0.6) / 0.4, 0, 1) ** 2
    side = np.sign(np.sin(az) + 1e-6)
    front = np.clip(1.0 - np.abs(az) / 1.1, 0, 1)
    psi = azc + 0.35 * thc + 0.07 * np.sin(5.0 * thc + 2.0 * azc)          # the stream: a gentle spiral
    psi = psi + 0.05 * np.sin(9.0 * thc + 1.3) * np.clip(thc / 0.5, 0, 1)   # each lock waves as it runs
    psi = psi + hook * (0.5 * side * front + 0.26 * (1 - front))           # the hooks: out at the brow, back elsewhere
    s = psi / (2 * math.pi) * HAIR_LOCKS
    return s, t, th, az


HAIR_ROWS = 2.7            # rows of tufts down a lock from the whorl to the hairline


def hair_local(q):
    """The hair as a Roman carver cuts it, subtractively: thick comma-shaped tufts laid in shingled rows down locks
    combed from the whorl - each tuft rising from under the tip of the one above to its own crisp tip, which turns
    aside into its comma and stands proud over the next row (a step of shadow); the RUNNING DRILL's channels deep and
    sharp between the locks (deepest near the face); the hairline scalloped by the last row's tips, each comma's eye
    drilled. Lines and steps, not lumps: r1-r5's locks were tubes laid on a ball and read as blobs (lovish, Oct 21
    14:20: "hair curls read as blobs")."""
    s, t, th, az = lock_coords(q)
    col = np.floor(s)
    # the rows: brick-bonded (every other lock half a row down), each tuft's tip curved across the lock
    fr0 = s - col
    across0 = np.abs(fr0 - 0.5) * 2
    rowpos = t * HAIR_ROWS + 0.5 * (col % 2) + 0.28 * across0 ** 2
    v = rowpos - np.floor(rowpos)                           # 0 at a tuft's root .. 1 at its tip
    side = np.where(np.sin(az) >= 0, 1.0, -1.0)
    fr = fr0 + 0.22 * v ** 2.2 * side * np.clip(t / 0.3, 0, 1)          # the tip turns aside: the comma
    across = np.clip(np.abs(fr - 0.5) * 2, 0, 1)
    live = np.clip(t / 0.22, 0, 1)                           # calm at the whorl
    # each tuft rises to its tip, then drops steeply (continuously: a jump in the field frays the edge) to the next
    # tuft's root - the step of shadow under every comma's tip
    tuft = 0.23 * v ** 0.8 * np.clip((1.0 - v) / 0.16, 0, 1) * live
    # r11: the locks over the brow lie flatter to the forehead - their tufts stood out at the hairline like a visor in
    # profile (the Augustus' fringe is cut close to the brow)
    brow = np.clip(1.0 - np.abs(az) / 0.9, 0, 1) * np.clip((t - 0.55) / 0.35, 0, 1)
    tuft = tuft * (1.0 - 0.6 * brow)
    ridge = 0.1 * (1 - across ** 2)
    w = 0.16 + 0.06 * np.clip(t, 0, 1)
    depth = (0.16 + 0.24 * np.clip(t, 0, 1) ** 1.5) * live
    chan = depth * np.clip(1 - (1 - across0) / w, 0, 1) ** 1.5      # the channels follow the locks unhooked
    # r11: the cap an even shell over the cranium (its own ellipsoid stood 0.08 px over the crown - the channels sank
    # into the skull, a mop - and 0.45 px out at the sides); fuller on the crown, close at the sides, flush with the
    # forehead at the hairline
    cap = S.ellipsoid(q, (0.0, 0.8, 0.45), (2.78, 3.1, 3.38))
    d = (cap - ridge - tuft + chan) / 1.7
    # the hairline: scalloped by the lock tips
    tip = np.cos(math.pi * np.clip(fr - 0.5, -0.5, 0.5))
    edge = (th - (hair_theta_end(az) - 0.12 + 0.14 * tip)) * 2.6
    d = S.smax(d, edge, 0.1)
    # the drilled eye of every comma: in the hook, just short of each tuft's tip
    drill = 0.16 - np.hypot((fr - 0.5 - 0.3 * side) * 0.55, (v - 0.8) * 0.5 / HAIR_ROWS * 4.0)
    d = d + np.maximum(drill, 0) * 1.2 * live
    return d


def head_local(q):
    return S.smin(face(q), hair_local(q), 0.15)


def head_point(p):
    """A head-local point (the face's canon) in body space."""
    return HEAD_C + head_frame() @ np.asarray(p, dtype=float)


NECK_BASE = np.array([-0.35, 41.15, 0.45])        # the neck's root between the shoulders
STERNAL_NOTCH = np.array([-0.35, 41.3, -1.12])    # the pit of the neck, between the collarbones' heads


def neck_field(P):
    """The neck in body space (it joins the bowed head to the chest): a column leaning forward with the bow, the
    two sternocleidomastoids running from behind each ear (the mastoid) down and forward to the pit of the neck -
    the V every classical neck shows - each splitting a clavicular head off toward its collarbone; the windpipe's
    soft ridge and the larynx between them; the trapezius' slope behind (torso). r1-r9: a plain column."""
    top = head_point((0.0, -2.4, 0.72))
    d = S.round_cone(P, NECK_BASE, top, 1.62, 1.5)
    for sx in (-1.0, 1.0):
        mast = head_point((sx * 1.88, -1.55, 0.95))
        stern = STERNAL_NOTCH + np.array([sx * 0.34, 0.12, 0.08])
        mid = (mast + stern) / 2 + np.array([sx * 0.18, 0.0, -0.22])
        scm = S.tube(P, [mast, mid, stern], [0.4, 0.4, 0.24])
        clav = S.tube(P, [lerp(mast, stern, 0.55) + np.array([sx * 0.3, 0.0, 0.1]),
                          stern + np.array([sx * 0.95, 0.12, 0.3])], [0.22, 0.17])
        d = S.smin(d, S.smin(scm, clav, 0.15), 0.35)
    # the throat: the windpipe's ridge from under the chin to the pit, the larynx a soft swell (a youth's)
    chin_under = head_point((0.0, -3.55, -1.6))
    d = S.smin(d, S.capsule(P, chin_under + np.array([0.0, -0.3, 0.45]), STERNAL_NOTCH + np.array([0.0, 0.45, 0.25]), 0.48), 0.3)
    d = S.smin(d, S.ellipsoid(P, lerp(chin_under, STERNAL_NOTCH, 0.42) + np.array([0.0, 0.0, 0.12]), (0.38, 0.45, 0.32)), 0.2)
    # the pit itself: a hollow between the two tendons
    d = S.smax(d, -S.sphere(P, STERNAL_NOTCH + np.array([0.0, 0.3, -0.2]), 0.2), 0.2)
    return d


def head_field(P):
    """The head and the neck in body space; outside a 9 px ball only its bound (nothing out there sees it)."""
    out = S.sphere(P, HEAD_C + np.array([0.0, -2.0, 0.0]), 7.0) + 2.0
    near = out < 3.0
    if near.any():
        q = S.frame(P[near], HEAD_C, head_frame())
        out[near] = S.smin(head_local(q), neck_field(P[near]), 0.5)
    return out


# ------------------------------------------------------------------ the body part ----------------------------------
NECK_CUT = 42.6     # the head part (finer cells) and the body part meet inside the neck here (r10: below the
                    # bowed chin - 43.6 ran through it once the head rose)


def body_field(P):
    d = S.smin(legs(P), feet(P), 0.5)
    d = S.smin(d, torso(P), 1.5)
    d = S.smin(d, skirt(P), 0.35)
    d = S.smin(d, kolpos(P), 0.3)
    d = S.smin(d, bodice(P), 0.35)
    d = S.smin(d, belt(P), 0.06)
    d = S.smin(d, mantle(P), 0.35)
    d = S.smin(d, swag(P), 0.3)
    d = S.smin(d, arms(P), 0.5)
    d = S.smin(d, hands(P), 0.25)
    d = S.smin(d, sword(P), 0.15)
    return d


def body_part(P):
    """The body below the neck's seam (the head is its own part above it)."""
    d = body_field(P)
    near = np.abs(P[:, 1] - NECK_CUT) < 3.0
    if near.any():
        d[near] = S.smin(d[near], head_field(P[near]), 0.6)
    # the body tucks 0.45 px under the head part's lowest band (whose surface is this same field, finer): no ring of
    # the coarse body's cut shows round the neck
    d = d + 0.3 * np.clip((P[:, 1] - (NECK_CUT - 1.0)) / 0.8, 0, 1)
    return np.maximum(d, P[:, 1] - NECK_CUT)


def head_part(P):
    """The head and its neck alone (finer cells): the neck runs down inside the bodice to 40.8, so the seam with the
    body part is a ring on the neck where the body's own neck is tucked under it - never a band of cloth cut off
    by the head's box (r3-r4's collar)."""
    d = head_field(P)
    return np.maximum(d, 40.8 - P[:, 1])


# ------------------------------------------------------------------ the wings --------------------------------------
WING_ROOT_L = np.array([2.3, 39.4, 2.95])
# THE WING'S LINE (lovish via the main session, Oct 21 14:20: "from the RTS camera the wings form a raised V that
# reads like a bat or a gargoyle from above"): the plane stands nearly square across the back (swept back, the far
# wing went edge-on from the RTS camera's 45 degrees once the statue faces the capitol's front) and CUPS FORWARD
# toward the tips, so from above the two wings are arcs round the figure ( ) and both show their faces at 45
# degrees; the leading edge is one arc - out from the shoulder at 40 degrees, the hand at 60, the outermost primary
# continuing it - not a V of two fans
WING_SWEEP, WING_LEAN = -7.0, 11.0        # the wing's plane barely turned back, its top reclined
WING_CUP = 0.008                          # the arm's gentle forward curl (px per px squared)
# the hand's REST YAW: the primaries swung forward about the wrist at rest (the rig's rest rotation; the game adds the
# fold to it) - the ( ) arc from above lives here, not in a baked curl, so the closed wing (L1) lies flat along the
# back (a baked curl folded with the wing splayed the closed primaries 20 px out to the sides)
HAND_REST_YAW = 16.0
WRIST_UV = np.array([12.6, 11.4])         # the wrist in the wing's plane: the hand part's pivot
BONES = [(0.0, 0.0), (4.6, 3.4), (8.8, 7.0), (WRIST_UV[0], WRIST_UV[1]), (15.4, 17.0), (17.0, 21.6)]


def wing_R(side):
    R = S.rot_y(side * WING_SWEEP) @ S.rot_x(WING_LEAN)
    if side < 0:
        R = R @ np.diag([-1.0, 1.0, 1.0])
    return R


def wing_q(P, side):
    """Body space -> the wing's own (u out, v up, w back) with the cupping (the tips curl forward round the figure)
    and a twist that turns the outer wing's face toward the front."""
    q = S.frame(P, WING_ROOT_L * np.array([side, 1, 1]), wing_R(side))
    u, v = q[:, 0], q[:, 1]
    q = q.copy()
    q[:, 2] = q[:, 2] + WING_CUP * u * u + 0.0026 * np.maximum(v, 0) ** 2 * np.clip(u / 12.0, 0, 1)
    return q


def feather_set():
    """(name, part, base uv, angle deg, length, width, layer w, bend) for every feather of the left wing; the right
    mirrors it. Primaries fan from the hand like a held hand of cards, the outermost continuing the leading edge's arc;
    secondaries hang from the forearm, tertials by the body; greater and median coverts overlap them like shingles;
    the alula at the wrist."""
    F = []
    # primaries (the hand part): bases along the hand, the outermost pointing up and out along the leading edge
    for i in range(12):
        t = i / 11.0                                           # 0 = P1 (by the wrist) .. 1 = the outermost (the tip)
        base = lerp(WRIST_UV, BONES[-1], t)
        ang = -16.0 + 92.0 * t ** 0.85
        L = 18.5 + 7.0 * math.sin(math.pi * min(1.0, 0.12 + t * 0.92)) - 1.5 * t
        F.append(("primary", "hand", base, ang, L, 6.0 - 1.4 * t, 0.11 * i, 1.0))
    # secondaries along the forearm, the outer ones swept out, the inner hanging
    for i in range(12):
        t = i / 11.0                                           # 0 = by the wrist .. 1 = by the body
        base = lerp(WRIST_UV, BONES[2], t * 1.0) if t < 0.5 else lerp(BONES[2], BONES[1], (t - 0.5) / 0.5)
        ang = -24.0 - 60.0 * t
        L = 16.5 + 1.5 * math.sin(math.pi * min(1.0, t * 1.2)) - 2.5 * t
        F.append(("secondary", "arm", base, ang, L, 3.2, 0.06 * (11 - i), 0.8))
    for i in range(4):                                          # tertials by the body
        t = i / 3.0
        base = lerp(BONES[1], (1.2, 1.0), t)
        F.append(("tertial", "arm", base, -86.0 - 5.0 * t, 13.0 - 1.5 * t, 3.4, -0.25, 0.5))
    # greater coverts over the secondaries' bases, median coverts above them
    for i in range(13):
        t = i / 12.0
        base = lerp(BONES[3], BONES[1], t) + (0.0, 0.3)
        F.append(("gcovert", "arm", base, -26.0 - 58.0 * t, 7.4 + 1.6 * math.sin(math.pi * t), 2.9, -0.55 - 0.03 * i, 0.45))
    for i in range(11):
        t = i / 10.0
        base = lerp(BONES[3], BONES[1], t) + (-0.4, 1.1)
        F.append(("mcovert", "arm", base, -30.0 - 54.0 * t, 4.4 + 1.0 * math.sin(math.pi * t), 2.4, -0.95 - 0.03 * i, 0.3))
    # the back of the wing: dorsal coverts in two shingled rows over the backing (seen from behind and in the fold)
    for i in range(13):
        t = i / 12.0
        base = lerp(BONES[3], BONES[1], t) + (0.0, 0.2)
        F.append(("dcovert", "arm", base, -26.0 - 58.0 * t, 7.6 + 1.4 * math.sin(math.pi * t), 3.0, 0.95 + 0.03 * i, 0.3))
    for i in range(11):
        t = i / 10.0
        base = lerp(BONES[3], BONES[1], t) + (-0.4, 1.0)
        F.append(("dcovert", "arm", base, -30.0 - 54.0 * t, 4.6 + 1.0 * math.sin(math.pi * t), 2.5, 1.35 + 0.03 * i, 0.2))
    for i in range(7):
        t = i / 6.0
        F.append(("dcovert", "hand", lerp(WRIST_UV, BONES[-1], t), -14.0 + 84.0 * t, 8.0, 3.0, 0.95 + 0.03 * i, 0.3))
    # primary coverts on the hand and the alula at the wrist
    for i in range(7):
        t = i / 6.0
        base = lerp(WRIST_UV, BONES[-1], t)
        F.append(("pcovert", "hand", base, -14.0 + 84.0 * t, 7.8, 2.9, -0.55 - 0.03 * i, 0.4))
    F.append(("alula", "hand", WRIST_UV + (0.2, 0.6), 62.0, 5.8, 1.6, -0.9, 0.3))
    return F


FEATHERS = feather_set()


def feather_fn(base, ang, L, W, layer, bend, tmin, kind="covert", outer=0.0):
    """One feather in the wing plane: a tapered vane curving back along its length, its rachis (the shaft) raised on
    the front, and a backing behind its inner three quarters (widened, so neighbours close up and no daylight shows
    through the wing: ART-FAULTS "gaps between parts"). The flight feathers (primaries, secondaries, tertials) carry
    the carver's detail: barbs cut as fine grooves slanting from the shaft toward the tip, and pointed tips - the
    outer primaries narrowing over their last third (emarginated), so their tips stand apart like an eagle's (lovish
    via the main session: "layered primaries with separated tips", "feather shafts and barbs"). Every thickness >=
    tmin, so a coarse LOD keeps the vane whole."""
    a = math.radians(ang)
    d2 = np.array([math.cos(a), math.sin(a)])
    n2 = np.array([-d2[1], d2[0]])
    flight = kind in ("primary", "secondary", "tertial")
    point = 0.5 if kind == "primary" else 0.12 if flight else 0.0

    def vane(s, r, Lx, Wx, pt=0.0, em=0.0):
        sn = np.clip(s / Lx, 0, 1)
        hw = Wx / 2 * np.clip(sn / 0.25, 0.3, 1.0) ** 0.6 * (1.0 - 0.25 * sn * sn)
        hw = hw * (1.0 - pt * 0.7 * np.clip((sn - 0.7) / 0.3, 0, 1) ** 1.5)          # the pointed tip
        hw = hw * (1.0 - em * 0.4 * np.clip((sn - 0.62) / 0.16, 0, 1))              # the emargination
        tip = s - (Lx - hw)
        d_tip = np.sqrt(np.maximum(tip, 0) ** 2 + r * r) - hw
        return np.where(tip > 0, d_tip, np.maximum(np.abs(r) - hw, -s)), sn

    def slab(d2d, dz):
        return np.where((d2d > 0) & (dz > 0), np.sqrt(d2d ** 2 + dz ** 2), np.maximum(d2d, dz))

    def f(q):
        rel = q[:, :2] - base
        s = rel @ d2
        r = rel @ n2
        d2d, sn = vane(s, r, L, W, point, outer)
        wc = layer + bend * sn * sn
        th = np.maximum(0.62 * (1.0 - 0.3 * sn), tmin)
        face = np.abs(q[:, 2] - wc) - th / 2
        # r10: the vane is a clean plane - the barb grooves r6-r9 cut into it decimated into lumps in close first
        # person (lovish, Oct 21 16:13: "feather surfaces lumpy"); a carved marble feather is a smooth vane and a
        # crisp shaft, its barbs left to the light
        out = slab(d2d, face)
        # the rachis (the shaft): a rounded ridge standing proud of the vane's front, broad at the quill and tapering
        # to the tip - crisp (a small blend) and bold enough for the LOD budgets to keep
        rw = (0.3 if flight else 0.2) * (1 - 0.6 * sn) + 0.04
        rach = np.sqrt(np.maximum(np.abs(r) - rw * 0.25, 0) ** 2 + (q[:, 2] - (wc - th * 0.5)) ** 2) - rw * 0.75
        rach = np.maximum(rach, np.maximum(-s, s - L * 0.93))
        out = S.smin(out, rach, 0.04)
        b2d, bsn = vane(s, r, L * 0.72, W * 1.45)
        back = slab(b2d, np.abs(q[:, 2] - (layer + 0.42 + bend * bsn * bsn * 0.7)) - max(0.5, tmin) / 2)
        return np.minimum(out, back)
    return f


def wing_field(P, side, part, tmin=0.0):
    """The left (side +1) or right wing's arm part (bones, coverts, secondaries, tertials) or hand part (primaries,
    their coverts, the alula)."""
    q = wing_q(P, side)
    prims = []
    for name, prt, base, ang, L, W, layer, bend in FEATHERS:
        if prt != part:
            continue
        a = math.radians(ang)
        mid = np.array([base[0] + math.cos(a) * L / 2, base[1] + math.sin(a) * L / 2, layer])
        outer = max(0.0, (ang - 44.0) / 32.0) if name == "primary" else 0.0      # the outer primaries' slots
        prims.append((feather_fn(np.asarray(base, float), ang, L, W, layer, bend, tmin, name, outer), mid,
                      L / 2 + W + 1.2))
    d = S.bounded_smin(q, prims, 0.07)
    if part == "arm":
        bones = np.array([(u, v, -0.45) for u, v in BONES[:4]])
        arm = S.tube(q * np.array([1.0, 1.0, 1.6]), bones * np.array([1.0, 1.0, 1.6]), [1.9, 1.5, 1.2, 0.95]) / 1.26
        # the lesser coverts: scallops along the arm's lower edge
        for i in range(9):
            t = i / 8.0
            c = lerp(BONES[3], BONES[0], t)
            arm = S.smin(arm, S.ellipsoid(q, (c[0] + 0.6, c[1] - 1.2, -0.85), (1.3, 1.05, 0.55), S.rot_z(-40 - 30 * t)), 0.25)
        d = S.smin(d, arm, 0.35)
        d = S.smin(d, S.ellipsoid(q, (0.6, 0.4, 0.2), (2.6, 2.4, 2.0)), 0.8)          # the root in the back
    else:
        hand = S.tube(q * np.array([1.0, 1.0, 1.45]), np.array([(u, v, -0.4 * 1.45) for u, v in BONES[3:]]),
                      [1.05, 0.85, 0.6]) / 1.2
        d = S.smin(d, hand, 0.3)
    return d


def wing_world(side, uv, w=0.0):
    """A point of the wing's plane in body space (inverting wing_q without the cupping: pivots only)."""
    R = wing_R(side)
    return WING_ROOT_L * np.array([side, 1, 1]) + R @ np.array([uv[0], uv[1], w])


# ------------------------------------------------------------------ the halo ----------------------------------------
HALO_C = HEAD_C + head_frame() @ np.array([0.0, 0.7, 2.9])


def halo_field(P):
    R = head_frame() @ S.rot_x(90)
    return S.torus(P, HALO_C, 4.1, 0.3, R)


def halo_outer_field(P):
    R = head_frame() @ S.rot_x(90)
    return S.torus(P, HALO_C, 5.6, 0.2, R)


# ------------------------------------------------------------------ the pedestal -------------------------------------
# THE PEDESTAL (r10 - lovish, Oct 21 16:13: "the stepped-block crown under the smooth statue - give it a worthy base"):
# a carved marble pedestal under the soles, drawn with the statue in place of r1-r9's three courses of stair blocks -
# an Attic base (a plinth slab, a torus, the scotia's shadow, a smaller torus), a round die with eight sunk panels, a
# gilt astragal, a cornice flaring to the top face the figure stands on. Three blocks tall (the block plinth it replaces
# - the capitol keeps a 3 x 3 core of stone inside it so the anchor stands on stone), 3 blocks' radius at its foot on
# the dome's top layer. Sculpt px: 3.89 a block (blocks_per_px).
PED_H = 11.67               # three blocks
PED_R = 11.6                # the base slab's radius (just under 3 blocks: the dome's top layer)
# r11: a flared foot (a cavetto) from the base slab down one more block to 4.1 blocks' radius, resting on the dome's
# top ring - it hides the stepped edge between the pedestal and the dome (the capitol clears the top layer's four
# corner cells for it: capitol_white_angel_v5.statue_plinth)
FOOT_H, FOOT_R = 3.89, 16.0


def _ring(P, y, R, r):
    """A torus round the vertical axis at height y."""
    q = np.stack([np.hypot(P[:, 0], P[:, 2]) - R, P[:, 1] - y], axis=1)
    return np.linalg.norm(q, axis=1) - r


def _drum(P, R, y0, y1, rr=0.12):
    """A round slab from y0 to y1, its edges rounded."""
    rad = np.hypot(P[:, 0], P[:, 2]) - (R - rr)
    hy = np.abs(P[:, 1] - (y0 + y1) / 2) - ((y1 - y0) / 2 - rr)
    return np.minimum(np.maximum(rad, hy), 0) + np.hypot(np.maximum(rad, 0), np.maximum(hy, 0)) - rr


def pedestal_field(P):
    y = P[:, 1]
    rad = np.hypot(P[:, 0], P[:, 2])
    b = -PED_H
    d = _drum(P, PED_R - 2.6, b - FOOT_H, 0.0)                                # the solid core (tori are rings)
    # the foot: its radius flaring out and down from the slab (a cavetto), a thin lip at its rim
    t = np.clip((b - y) / FOOT_H, 0, 1)
    foot = np.maximum(rad - (PED_R - 0.3 + (FOOT_R - PED_R + 0.3) * t ** 2.2), np.maximum(y - b - 0.05, (b - FOOT_H) - y))
    d = np.minimum(d, foot)
    d = np.minimum(d, _drum(P, PED_R, b, b + 0.75))                           # the plinth slab
    d = S.smin(d, _ring(P, b + 1.25, PED_R - 0.75, 0.55), 0.12)               # the lower torus
    d = S.smin(d, _drum(P, PED_R - 1.55, b + 1.6, b + 2.35), 0.05)           # the scotia's core
    d = S.smax(d, -_ring(P, b + 1.98, PED_R - 0.85, 0.36), 0.08)              # its hollow
    d = S.smin(d, _ring(P, b + 2.62, PED_R - 1.3, 0.4), 0.1)                  # the upper torus
    die_r = PED_R - 2.1
    die = _drum(P, die_r, b + 2.9, -2.45, 0.08)
    # the die's eight sunk panels, each a framed recess with bevelled edges
    th = np.arctan2(P[:, 2], P[:, 0])
    k = np.round((th - math.pi / 8) / (math.pi / 4))
    dth = (th - math.pi / 8 - k * math.pi / 4) * die_r
    inside = np.minimum(3.1 - np.abs(dth), np.minimum(y - (b + 4.0), (-3.5) - y))
    die = die + 0.28 * np.clip(inside / 0.3, 0, 1) * np.clip((rad - (die_r - 1.0)) / 0.5, 0, 1)
    d = S.smin(d, die, 0.1)
    d = S.smin(d, _ring(P, -2.2, die_r + 0.05, 0.27), 0.06)                   # the gilt astragal
    # the cornice: a cyma flaring from the die to the top's edge, then the crown slab the figure stands on
    t = np.clip((y + 1.95) / 1.25, 0, 1)
    cyma = rad - (die_r + 0.2 + (PED_R - 0.4 - die_r) * (3 * t * t - 2 * t * t * t))
    cyma = np.maximum(cyma, np.maximum(-(y + 1.95), y + 0.7))
    d = S.smin(d, cyma, 0.08)
    d = S.smin(d, _drum(P, PED_R - 0.3, -0.7, 0.0, 0.1), 0.06)
    return d


def _dp(pts, tol):
    """Douglas-Peucker: the indices of a polyline's points that keep it within tol."""
    keep = {0, len(pts) - 1}
    stack = [(0, len(pts) - 1)]
    while stack:
        i, j = stack.pop()
        if j <= i + 1:
            continue
        a, b = pts[i], pts[j]
        ab = b - a
        L = np.linalg.norm(ab)
        seg = pts[i + 1:j] - a
        dist = np.abs(seg[:, 0] * ab[1] - seg[:, 1] * ab[0]) / L if L > 1e-9 else np.linalg.norm(seg, axis=1)
        k = int(np.argmax(dist))
        if dist[k] > tol:
            keep.add(i + 1 + k)
            stack += [(i, i + 1 + k), (i + 1 + k, j)]
    return sorted(keep)


PED_LATHE = {0: (6, 0.02), 1: (4, 0.05), 2: (2, 0.14)}    # LOD -> (angles per panel sector, profile tolerance px)


def lathe_pedestal(lod):
    """The pedestal as a lathe (r10, the optimisation pass): rings of real quads round the axis at angles that land on
    the die's panel edges, the profile read off pedestal_field by bisection along horizontal rays and simplified to
    the LOD's tolerance - exact circles and crisp mouldings in 1/3 of the polygons a decimated sampling needed (its
    3000 triangles still left jagged rims). The foot's underside is left open: it sits on the dome's top layer."""
    per, tol = PED_LATHE[min(lod, 2)]
    ys = np.linspace(-PED_H - FOOT_H, 0.0, 800)
    half = 3.1 / (PED_R - 2.1)                     # the panels' half-width (radians) on the die
    angs = []
    for k in range(8):
        c = math.pi / 8 + k * math.pi / 4
        if per >= 4:
            offs = [-math.pi / 8, -half, -half + 0.035] + ([-half / 2] if per >= 6 else []) + [0.0] + \
                   ([half / 2] if per >= 6 else []) + [half - 0.035, half]
        else:
            offs = [-math.pi / 8, 0.0]
        angs += [c + o for o in offs]
    angs = np.array(sorted(set(np.round(np.mod(angs, 2 * math.pi), 6))))

    def radius(th):
        d = np.array([math.cos(th), 0.0, math.sin(th)])
        lo = np.zeros(len(ys))
        hi = np.full(len(ys), FOOT_R + 0.6)
        for _ in range(30):
            mid = (lo + hi) / 2
            P = np.stack([mid * d[0], ys, mid * d[2]], axis=1)
            inside = pedestal_field(P) < 0
            lo = np.where(inside, mid, lo)
            hi = np.where(inside, hi, mid)
        return lo
    R = np.array([radius(th) for th in angs])          # (angles, heights)
    # the profile's kept heights: the union of every angle's simplified profile, so a panel's edge rows exist in all
    keep = set()
    for row in R:
        keep.update(_dp(np.stack([row, ys], axis=1), tol))
    keep = sorted(keep)
    R, Y = R[:, keep], ys[keep]
    # the top face: two rings in from the cornice's edge (so the flat top's normals stay up - one fan from the
    # rounded edge to the centre streaked it), then the centre on the axis
    edge = R[:, -1:]
    R = np.concatenate([R, edge - 0.45, edge * 0.5, np.zeros((len(angs), 1))], axis=1)
    Y = np.concatenate([Y, [0.0, 0.0, 0.0]])
    na, ny = R.shape
    V = np.stack([R * np.cos(angs)[:, None], np.broadcast_to(Y, R.shape), R * np.sin(angs)[:, None]], axis=2).reshape(-1, 3)
    idx = np.arange(na * ny).reshape(na, ny)
    Q = []
    for i in range(na):
        j = (i + 1) % na
        for k in range(ny - 2):
            Q.append((idx[i, k], idx[i, k + 1], idx[j, k + 1], idx[j, k]))        # wound outward, as surface nets
        # the top ring meets on the axis: a triangle a b c c whose first three corners are distinct (Oculus takes a
        # quad's tangent from them)
        Q.append((idx[i, ny - 2], idx[i, ny - 1], idx[j, ny - 2], idx[j, ny - 2]))
    return V, np.array(Q)


LATHES = {"pedestal": lathe_pedestal}


# ------------------------------------------------------------------ the parts ---------------------------------------
# name: (field(P, h), box lo, box hi, (the fine cell, triangles per LOD), parent, pivot (body space)). Every part is
# sampled ONCE at its fine cell and then decimated (quadric error metrics: fast_simplification) to each LOD's budget -
# the error metric keeps a lid's edge, a drill channel or a fold's ridge and spends nothing on the plain planes, so a
# drilled pupil survives at LOD0 and the folds' shadows at LOD1 (r1-r5 sampled each LOD on a uniform grid: the face's
# drill-fine cuts were finer than its cell). Budgets: LOD0 <= 80k, LOD1 <= 26k, LOD2 <= 9k (CI's --max-quads; a
# triangle is one quad a b c c in the .bmesh).
def _parts():
    wl, wr = WING_ROOT_L, WING_ROOT_L * np.array([-1, 1, 1])
    hl, hr = wing_world(1, WRIST_UV), wing_world(-1, WRIST_UV)
    # the wings sampled at 0.17 px (r10; 0.23 before): a feather's shaft is ~0.4 px wide, and at 0.23 px cells it
    # aliased into dashes - the decimation keeps the same budgets, spent on cleaner planes
    wing = (0.17, (7500, 3000, 1100))
    hand = (0.17, (8500, 3100, 1150))
    return {
        "body": (lambda P, h: body_part(P), (-17.5, -0.2, -9.5), (8.5, 43.5, 19.0), (0.22, (22000, 7600, 2700)), None,
                 (0.0, 0.0, 0.0)),
        "head": (lambda P, h: head_part(P), (-5.6, 40.6, -5.2), (4.2, 52.6, 4.6), (0.065, (20500, 4400, 1100)), "body",
                 (0.0, 0.0, 0.0)),
        "wing_left": (lambda P, h: wing_field(P, 1, "arm", 1.9 * h), (-2.0, 20.0, -6.0), (24.0, 62.0, 16.0), wing,
                      "body", tuple(wl)),
        "wing_left_hand": (lambda P, h: wing_field(P, 1, "hand", 1.9 * h), (4.0, 38.0, -12.0), (42.0, 92.0, 20.0), hand,
                           "wing_left", tuple(hl)),
        "wing_right": (lambda P, h: wing_field(P, -1, "arm", 1.9 * h), (-24.0, 20.0, -6.0), (2.0, 62.0, 16.0), wing,
                       "body", tuple(wr)),
        "wing_right_hand": (lambda P, h: wing_field(P, -1, "hand", 1.9 * h), (-42.0, 38.0, -12.0), (-4.0, 92.0, 20.0),
                            hand, "wing_right", tuple(hr)),
        "pedestal": (lambda P, h: pedestal_field(P), (-FOOT_R - 0.5, -PED_H - FOOT_H - 0.3, -FOOT_R - 0.5),
                     (FOOT_R + 0.5, 0.3, FOOT_R + 0.5),
                     (0.1, (3000, 1400, 600)), "body", (0.0, 0.0, 0.0)),
        "halo": (lambda P, h: halo_field(P), HALO_C - 5.0, HALO_C + 5.0, (0.2, (900, 420, 200)), "body", tuple(HALO_C)),
        "halo_outer": (lambda P, h: halo_outer_field(P), HALO_C - 6.5, HALO_C + 6.5, (0.18, (700, 320, 150)), "body",
                       tuple(HALO_C)),
    }


PARTS = _parts()


# ------------------------------------------------------------------ materials and the bake ------------------------
# atlas cells (8 x 8 texels each in a 64 x 64 atlas): the finish of every surface region
REGIONS = {"skin": (0, 0), "cloth": (1, 0), "feather": (2, 0), "hair": (3, 0), "gold": (0, 1), "blade": (1, 1),
           "stone": (2, 1)}
SMOOTH = {"skin": 0.78, "cloth": 0.5, "feather": 0.34, "hair": 0.45, "gold": 0.74, "blade": 0.82, "stone": 0.66}
MARBLE = np.array([0.93, 0.905, 0.86])          # warm Carrara white
GOLD = np.array([0.86, 0.66, 0.27])


def material_of(name, V):
    """Per-vertex region: skin on the face, neck, arms, hands and feet; hair; cloth; the blade; gold on the halo,
    the sword's guard and pommel and the belt's cord."""
    m = np.full(len(V), "cloth", dtype=object)
    if name.startswith("wing"):
        m[:] = "feather"
        return m
    if name.startswith("halo"):
        m[:] = "gold"
        return m
    if name == "pedestal":
        m[:] = "stone"
        m[np.abs(V[:, 1] + 2.2) < 0.36] = "gold"
        return m
    if name == "head":
        q = S.frame(V, HEAD_C, head_frame())
        m[:] = "skin"
        m[hair_local(q) < 0.06] = "hair"
        return m
    y = V[:, 1]
    sk = np.minimum(arms(V), hands(V)) < 0.05
    sk &= ~(bodice(V) < 0.05)
    m[sk] = "skin"
    m[(feet(V) < 0.05) & (y < 3.2)] = "skin"
    m[(y > 41.0)] = np.where(bodice(V[y > 41.0]) < 0.03, "cloth", "skin")
    sw = sword(V) < 0.05
    m[sw] = "blade"
    q = V - SWORD_POINT
    gy = sword_at(22.9)[1]
    m[sw & ((y > gy - 0.55) & (np.abs(V[:, 0] - sword_at(y)[:, 0]) < 4.4))] = "gold"    # the cross, not the blade
    m[sw & (y > 27.6)] = "gold"
    return m


def marble_albedo(V, mat):
    """Solid marble: veins as thin turbulent sheets running through the block (continuous across every part, as in
    a real carving), a soft large-scale cloudiness, and the finish's tint per region."""
    p = V * 0.11
    turb = (np.sin(p[:, 0] * 1.7 + p[:, 1] * 0.6) + 0.5 * np.sin(p[:, 1] * 3.1 - p[:, 2] * 2.3 + 1.1)
            + 0.25 * np.sin(p[:, 2] * 6.3 + p[:, 0] * 4.7 + 2.0) + 0.12 * np.sin((p[:, 0] - p[:, 1]) * 13.0 + 0.4))
    vein = np.abs(np.sin(p[:, 0] * 0.9 + p[:, 1] * 2.2 - p[:, 2] * 0.7 + 2.2 * turb))
    vein = np.exp(-(vein / 0.07) ** 2) * 0.055 + np.exp(-(vein / 0.25) ** 2) * 0.025
    # a vein across a face reads as a crack or a scar: the carver chose his block so the face is clean
    vein = vein * np.where(mat == "skin", 0.35, 1.0)
    cloud = 0.02 * np.sin(p[:, 0] * 0.7 + 1.3) * np.sin(p[:, 1] * 0.5 + 0.2) * np.sin(p[:, 2] * 0.6 + 2.0)
    col = MARBLE[None, :] * (1.0 - vein - cloud)[:, None]
    col = col * np.array([1.0, 0.995, 0.985])[None, :] ** (vein * 20)[:, None]     # the veins a touch grey-blue
    tint = {"skin": (1.012, 1.004, 0.99), "cloth": (0.985, 0.985, 0.985), "feather": (1.0, 0.996, 0.99),
            "hair": (0.97, 0.96, 0.95), "blade": (0.98, 0.985, 1.0)}
    for k, tv in tint.items():
        col[mat == k] *= np.array(tv)
    g = mat == "gold"
    col[g] = GOLD[None, :] * (0.95 + 0.05 * np.clip(turb[g], -1, 1))[:, None]
    return col


class SceneField:
    """The whole statue's field on a coarse grid (spread wings) - what the occlusion bake samples."""

    def __init__(self, h=0.45):
        lo = np.array([-36.0, -16.5, -17.0])
        hi = np.array([36.0, 88.0, 21.0])

        def f(P):
            d = body_part(P)
            d = np.minimum(d, head_part(P))
            d = np.minimum(d, pedestal_field(P))
            for side in (1, -1):
                d = np.minimum(d, wing_field(P, side, "arm", 0.6))
                d = np.minimum(d, wing_field(P, side, "hand", 0.6))
            return d
        t = time.time()
        self.field, self.origin, self.h = S.sample(f, lo, hi, h, band=4, lipschitz=1.6)
        print("  scene field for the bake: %s cells, %.1fs" % ("x".join(map(str, self.field.shape)), time.time() - t))

    def __call__(self, P):
        g = (P - self.origin) / self.h
        n = np.array(self.field.shape) - 1
        inside = np.all((g >= 0) & (g <= n - 1e-6), axis=1)
        out = np.full(len(P), 4.0)
        if inside.any():
            out[inside] = S._trilinear(self.field, g[inside])
        return out


def occlusion(scene, V, N, mat, own=None):
    """Carved-depth occlusion: the running drill's channels and the undercuts go dark, broad hollows dim softly.
    Sampled along the normal and a ring of cones round it (Inigo Quilez's SDF occlusion, multi-direction). The near
    samples read the part's own exact field (a lip's crease or a curl's drilled eye is finer than the scene grid);
    the far ones the whole statue's coarse grid (a wing over the back)."""
    up = np.where(np.abs(N[:, 1:2]) < 0.9, np.array([[0.0, 1.0, 0.0]]), np.array([[1.0, 0.0, 0.0]]))
    a = np.cross(up, N)
    a /= np.maximum(np.linalg.norm(a, axis=1), 1e-9)[:, None]
    b = np.cross(N, a)
    dirs = [N]
    for k in range(6):
        ang = 2 * math.pi * k / 6
        dirs.append(S.normalize_rows(N * 0.62 + (a * math.cos(ang) + b * math.sin(ang)) * 0.78))
    occ = np.zeros(len(V))
    wsum = 0.0
    for j, D in enumerate(dirs):
        wd = 1.6 if j == 0 else 1.0
        for i, dist in enumerate((0.25, 0.55, 1.0, 1.7, 2.8, 4.4)):
            Pq = V + D * dist
            s = scene(Pq) if (own is None or dist > 1.2) else np.minimum(own(Pq), scene(Pq) + 0.5)
            occ += wd * np.clip((dist - s) / dist, 0, 1) * (0.86 ** i)
            wsum += wd * (0.86 ** i)
    ao = 1.0 - occ / wsum
    return np.clip(ao, 0, 1)


def bake(name, V, N, scene, own=None):
    mat = material_of(name, V)
    col = marble_albedo(V, mat)
    ao = occlusion(scene, V, N, mat, own) if scene is not None else np.ones(len(V))
    deep = np.where(mat == "hair", 0.5, 0.78)              # the hair's pits stay grey, not black holes
    shade = 1.0 - deep * (1.0 - ao) ** 1.5
    col = col * shade[:, None]
    return col, mat


def vertex_normals(V, T):
    """Area-weighted vertex normals of a triangle mesh (its own geometry)."""
    fn = np.cross(V[T[:, 1]] - V[T[:, 0]], V[T[:, 2]] - V[T[:, 0]])
    N = np.zeros_like(V)
    for k in range(3):
        np.add.at(N, T[:, k], fn)
    return N / np.maximum(np.linalg.norm(N, axis=1), 1e-12)[:, None]


def transfer_colours(V, N, T, Vr, Nr, Cr):
    """A coarse LOD's vertex colours as the AREA AVERAGE of the finest LOD's bake round each vertex (r10): a coarse
    vertex baked on its own samples the occlusion at one point - in the crevice between two feathers it went black
    and painted a whole large triangle dark (LOD1/LOD2's blotchy wings at the RTS camera, 21% / 43% of the statue's
    pixels off LOD0's by more than 12/255). Averaging what LOD0 bakes over the patch a coarse vertex stands for is the
    low-pass the eye applies at the distance a coarse LOD is drawn. Weights fall off over the vertex's own edge length
    and need the normals to agree (the two faces of a thin feather do not bleed into each other). The normals come
    across the same way: a coarse vertex sits a little off the true surface, and between thin layered feathers the
    field's gradient there can point at the next feather - whole facets shaded wrong. Returns (colours, normals), NaN
    where no LOD0 vertex was near."""
    N = vertex_normals(V, T)
    E = np.concatenate([T[:, [0, 1]], T[:, [1, 2]], T[:, [2, 0]]])
    L = np.linalg.norm(V[E[:, 0]] - V[E[:, 1]], axis=1)
    rad = np.bincount(E[:, 0], L, len(V)) / np.maximum(np.bincount(E[:, 0], None, len(V)), 1)
    rad = np.clip(rad * 0.75, 0.12, 2.5)
    c = float(np.median(rad))
    key = lambda P: np.floor(P / c).astype(np.int64)                          # noqa: E731
    kr = key(Vr)
    h = (kr[:, 0] * 73856093) ^ (kr[:, 1] * 19349663) ^ (kr[:, 2] * 83492791)
    order = np.argsort(h)
    hs = h[order]
    acc = np.zeros((len(V), 3))
    nacc = np.zeros((len(V), 3))
    wsum = np.zeros(len(V))
    kv = key(V)
    reach = np.ceil(rad / c).astype(int)
    R = int(min(3, reach.max()))
    for dx in range(-R, R + 1):
        for dy in range(-R, R + 1):
            for dz in range(-R, R + 1):
                k = kv + np.array([dx, dy, dz])
                hq = (k[:, 0] * 73856093) ^ (k[:, 1] * 19349663) ^ (k[:, 2] * 83492791)
                lo = np.searchsorted(hs, hq, "left")
                hi = np.searchsorted(hs, hq, "right")
                n = hi - lo
                if n.sum() == 0:
                    continue
                vi = np.repeat(np.arange(len(V)), n)
                ri = order[np.concatenate([np.arange(a, b) for a, b in zip(lo[n > 0], hi[n > 0])])]
                d = np.linalg.norm(Vr[ri] - V[vi], axis=1)
                w = np.clip(1.0 - d / rad[vi], 0, 1) * (np.sum(Nr[ri] * N[vi], axis=1) > 0.3)
                np.add.at(acc, vi, Cr[ri] * w[:, None])
                np.add.at(nacc, vi, Nr[ri] * w[:, None])
                np.add.at(wsum, vi, w)
    ok = (wsum > 1e-6)[:, None]
    nn = nacc / np.maximum(np.linalg.norm(nacc, axis=1), 1e-12)[:, None]
    return np.where(ok, acc / np.maximum(wsum, 1e-9)[:, None], np.nan), np.where(ok, nn, np.nan)


_REF = {}       # part name -> LOD0's (V, N, C): the bake a coarse LOD's colours are averaged from


def uv_for(V, N, mat):
    """Tri-planar UVs inside each region's 8 x 8 cell: the atlas is a calm micro-grain per finish (the marble's
    colour and depth live in the vertex colours); the spread keeps a shader pack's tangents well defined."""
    UV = np.zeros((len(V), 2))
    ax = np.argmax(np.abs(N), axis=1)
    a = np.where(ax == 0, V[:, 2], V[:, 0]) * 0.25
    b = np.where(ax == 1, V[:, 2], V[:, 1]) * 0.25
    fu, fv = a - np.floor(a), b - np.floor(b)
    for k, (cx, cy) in REGIONS.items():
        sel = mat == k
        UV[sel, 0] = (cx * 8 + 1 + fu[sel] * 6) / ATLAS
        UV[sel, 1] = (cy * 8 + 1 + fv[sel] * 6) / ATLAS
    return UV


# ------------------------------------------------------------------ build ---------------------------------------------
class Mesh:
    def __init__(self, name, V, N, C, UV, Q, mat):
        self.name, self.V, self.N, self.C, self.UV, self.Q, self.mat = name, V, N, C, UV, Q, mat

    def smoothness(self):
        return np.array([SMOOTH[m] for m in self.mat])


_FINE = {}       # part name -> (V, T, h): the fine sampling, shared by every LOD of one run


def fine_mesh(name, verbose=True):
    """A part sampled at its fine cell and meshed (surface nets), as triangles - once per run."""
    if name not in _FINE:
        field, lo, hi, (h, _), parent, pivot = PARTS[name]
        t0 = time.time()
        V, N, Q = S.mesh(lambda P: field(P, h), lo, hi, h)
        T = np.concatenate([Q[:, [0, 1, 2]], Q[:, [0, 2, 3]]], axis=0)
        _FINE[name] = (V, T, h)
        if verbose:
            print("  %-16s fine h=%.3f: %7d triangles  %.1fs" % (name, h, len(T), time.time() - t0))
    return _FINE[name]


def decimate(V, T, target):
    """Quadric decimation to `target` triangles (no-op when the mesh is already under it), unreferenced vertices
    dropped."""
    if len(T) > target:
        import fast_simplification
        V, T = fast_simplification.simplify(V.astype(np.float32), T.astype(np.int32), target_count=int(target))
        V = V.astype(np.float64)
        T = np.asarray(T, dtype=np.int64)
    used = np.unique(T)
    remap = np.full(len(V), -1, dtype=np.int64)
    remap[used] = np.arange(len(used))
    return V[used], remap[T]


def face_region(V):
    """The head's vertices on the face itself - the skin in front of the ears and below the hairline (head-local):
    where a viewer reads a portrait, so it keeps more of the head's budget than the hair's shingles do."""
    q = S.frame(V, HEAD_C, head_frame())
    return (q[:, 2] < -0.6) & (q[:, 1] < 2.0) & (q[:, 1] > -4.2) & (np.abs(q[:, 0]) < 2.15) & (hair_local(q) > 0.05)


# a part's share of its LOD budget kept for a region (the rest of the part gets the rest): the head's face takes
# 55% of the head's triangles at LOD0 and LOD1 - a uniform decimation spent most of them on the hair's drill channels
# and crumpled the eyes and the mouth (r10's close-ups)
REGION_SPLIT = {"head": (face_region, (0.55, 0.55, None))}


def decimate_split(V, T, target, region, share):
    """Quadric decimation with a region kept apart: the triangles whose three corners are in `region` and the rest
    are decimated separately to share / 1 - share of the target, each with its open border held (fast_simplification's
    preserve_border: no edge touching the seam collapses, so both halves keep the seam's vertices where they were),
    then welded back on the seam - one watertight mesh, the region finer."""
    inside = region(V)
    tri_in = inside[T].all(axis=1)
    out_V, out_T = [], []
    base = 0
    for sel, tgt in ((tri_in, target * share), (~tri_in, target * (1.0 - share))):
        Ts = T[sel]
        used = np.unique(Ts)
        remap = np.full(len(V), -1, dtype=np.int64)
        remap[used] = np.arange(len(used))
        Vs, Ts = V[used], remap[Ts]
        if len(Ts) > tgt:
            import fast_simplification
            Vs, Ts = fast_simplification.simplify(Vs.astype(np.float64), Ts.astype(np.int32), target_count=int(tgt),
                                                  preserve_border=True)
        out_V.append(np.asarray(Vs, dtype=np.float64))
        out_T.append(np.asarray(Ts, dtype=np.int64) + base)
        base += len(Vs)
    V2, T2 = np.concatenate(out_V), np.concatenate(out_T)
    # weld the seam: its vertices were held, so the two halves' copies are bit-equal
    key, first, inv = np.unique(V2, axis=0, return_index=True, return_inverse=True)
    T2 = inv.reshape(-1)[T2]
    T2 = T2[(T2[:, 0] != T2[:, 1]) & (T2[:, 1] != T2[:, 2]) & (T2[:, 0] != T2[:, 2])]
    used = np.unique(T2)
    remap = np.full(len(key), -1, dtype=np.int64)
    remap[used] = np.arange(len(used))
    return key[used], remap[T2]


def pair_triangles(V, T):
    """Triangles paired into quads, losslessly (r10's optimisation): two triangles sharing an edge, (a b c) and
    (a c d), become the quad (a b c d) - which Minecraft draws as (a b c) and (c d a), the same two triangles, the
    same per-vertex normals and colours. A lone triangle stays a b c c. The game writes every corner of every quad
    each frame (MeshCube.compile), so this halves the statue's vertex stream at no cost in the picture. Greedy, the
    most nearly coplanar pairs first: a shader pack takes a quad's tangent from its first three corners, so the
    second triangle shares the first's - alike when the two are nearly flat to each other."""
    T = np.asarray(T, dtype=np.int64)
    n = len(T)
    fn = np.cross(V[T[:, 1]] - V[T[:, 0]], V[T[:, 2]] - V[T[:, 0]])
    fn /= np.maximum(np.linalg.norm(fn, axis=1), 1e-12)[:, None]
    owner = {}
    for t in range(n):
        for k in range(3):
            owner[(T[t, k], T[t, (k + 1) % 3])] = (t, k)
    cands = []
    for (a, b), (t, k) in owner.items():
        o = owner.get((b, a))
        if o is not None and o[0] > t:
            cands.append((1.0 - float(fn[t] @ fn[o[0]]), t, k, o[0]))
    cands.sort()
    used = np.zeros(n, dtype=bool)
    quads = []
    for _, t, k, u in cands:
        if used[t] or used[u]:
            continue
        used[t] = used[u] = True
        # t's edge k runs T[t,k] -> T[t,k+1]; rotate t so that edge is its last (c -> a): (a, b, c) = (k+1, k+2, k)
        a, b, c = T[t, (k + 1) % 3], T[t, (k + 2) % 3], T[t, k]
        d = [v for v in T[u] if v != a and v != c][0]
        quads.append((a, b, c, d))
    lone = T[~used]
    Q = np.concatenate([np.array(quads, dtype=np.int64).reshape(-1, 4), np.concatenate([lone, lone[:, 2:3]], axis=1)])
    return Q


def field_normals(f, V, h):
    """Unit normals from the field's gradient at the vertices (central differences) - smooth, and exact where the
    decimated mesh's own facets are coarse."""
    eps = max(0.02, h * 0.5)
    G = np.stack([f(V + np.eye(3)[k] * eps) - f(V - np.eye(3)[k] * eps) for k in range(3)], axis=1)
    ln = np.linalg.norm(G, axis=1)
    return G / np.where(ln > 1e-12, ln, 1.0)[:, None]


def build(lod=0, only=None, scene=None, verbose=True):
    out = []
    for name, (field, lo, hi, (h, budgets), parent, pivot) in PARTS.items():
        if only and name not in only:
            continue
        t0 = time.time()
        if name in LATHES:
            V, Q = LATHES[name](lod)
            N = field_normals(lambda P: field(P, h), V, h)
            C, mat = bake(name, V, N, scene, lambda P: field(P, h))
            UV = uv_for(V, N, mat)
            if verbose:
                print("  %-16s LOD%d: %6d verts %6d quads (lathe)  %.1fs" % (name, lod, len(V), len(Q), time.time() - t0))
            out.append(Mesh(name, V, N, C, UV, Q, mat))
            continue
        Vf, Tf, h = fine_mesh(name, verbose)
        target = budgets[min(lod, len(budgets) - 1)]
        split = REGION_SPLIT.get(name)
        share = split[1][min(lod, len(split[1]) - 1)] if split else None
        if share:
            V, T = decimate_split(Vf, Tf, target, split[0], share)
        else:
            V, T = decimate(Vf, Tf, target)
        N = field_normals(lambda P: field(P, h), V, h)
        C, mat = bake(name, V, N, scene, lambda P: field(P, h))
        if lod == 0:
            _REF[name] = (V, N, C)
        else:
            if name not in _REF:                               # a coarse LOD built alone: LOD0's bake first
                build(0, only=[name], scene=scene, verbose=False)
            Ct, Nt = transfer_colours(V, N, T, *_REF[name])
            C = np.where(np.isnan(Ct), C, Ct)
            N = np.where(np.isnan(Nt), N, Nt)
        UV = uv_for(V, N, mat)
        Q = pair_triangles(V, T)                                   # two triangles a quad (a lone one a b c c)
        if verbose:
            print("  %-16s LOD%d: %6d verts %6d triangles  %.1fs" % (name, lod, len(V), len(T), time.time() - t0))
        out.append(Mesh(name, V, N, C, UV, Q, mat))
    return out


# ------------------------------------------------------------------ the rig and posing --------------------------------
WING_FOLD_MAX = 70.0     # BuildingParts.WING_FOLD_MAX
WING_DROOP = 0.45        # BuildingParts.WING_DROOP
WING_HAND = -1.1         # BuildingParts.WING_HAND


def wing_open_at(level):
    return 0.2 * (max(1, min(6, level)) - 1)


def fold_degrees(level):
    return (1.0 - wing_open_at(level)) * WING_FOLD_MAX


# the mesh angel's fold (r10: KEYED - lovish, Oct 21 16:13: "the wings read lopsided from the RTS camera, one up, one
# sideways"). Why keys: the RTS camera looks down 50 degrees from a diagonal, so a point's screen height is 0.64 y +
# 0.54 (x + z) - a wing reaching out sideways is SHEARED up on one side of the figure and pressed flat on the other;
# only a reach that is mostly UP reads the same from every diagonal. r6-r9 folded with one straight Euler sweep from
# the spread wings to the closed ones, which swung each wing out sideways on its way (L2-L5 lopsided by 60-100
# degrees on screen). Now the wings unfold as a bird lifts them: from closed down the back (L1) they RISE up behind
# the shoulders (the raised key, a near-vertical plane - balanced from every diagonal), then open into a tall V (L6),
# itself raised and swept back from the sculpt's plane (WING_REST). The game interpolates the same keys linearly in
# Euler degrees (StatueRenderClientEvents.applyFold); BuildingParts.MESH_* holds these numbers and main() fails CI if
# they drift.
def euler_dsl(D):
    """DSL degrees [x, y, z] whose ModelPart rotation (mc_rot, then rotationZYX in y-down part space) is the DSL-space
    rotation matrix D."""
    F = np.diag([1.0, -1.0, 1.0])
    M = F @ D @ F
    ry = math.asin(max(-1.0, min(1.0, -M[2, 0])))
    rx = math.atan2(M[2, 1], M[2, 2])
    rz = math.atan2(M[1, 0], M[0, 0])
    return [-math.degrees(rx), math.degrees(ry), -math.degrees(rz)]


# the spread wings' attitude (L6) against the sculpt's plane: raised about the figure's forward axis and swept back
WING_RAISE_DEG, WING_SWEEP_BACK = 22.0, 18.0
# the RAISED key: the wing standing up behind the shoulder - the arm (shoulder blade -> wrist) up and back, the
# feathered face out to the side; the primaries folded part way, continuing the arm upward
RAISE_U = (0.3, 0.85, 0.4)
RAISE_FACE = (0.6, 0.0, -0.8)       # out and forward: both front diagonals see both wings' faces
RAISE_HAND = -60.0                  # the primaries half folded: a compact pair at L2 that opens level by level
# the raised key's place in the fold (a fraction of WING_FOLD_MAX from spread): 0.8 is L2's settled fold, so every
# settled level from L2 up has its wings UP - "back", like "sideways", is sheared by the diagonal camera (the screen
# height takes 0.54 (x + z)); the closed -> raised swing through "back" happens only in L1 -> L2's 6 s unfold
WING_RAISE_AT = 0.8
# the closed wing (L1): its arm rising back from the shoulder blade, its feathered face turned out and forward (the
# RTS camera reads it as a wing, not an edge), the feathers hanging down behind the body; the hand folded down
FOLD_U = (0.12, -0.6, 0.8)         # shoulder blade -> wrist: straight back along the back, a little down
FOLD_FACE = (0.85, 0.0, -0.5)       # where the wing's front face looks: out and forward (close to the raised key's,
                                    # so the swing between them is about the lateral axis, not a twist)
FOLD_TUCK = 0.0                     # the primaries swung in behind the back (about the wing's up axis): the forward cup
                                    # that wraps the spread wing round the figure would splay a closed wing outward
FOLD_HAND = -120.0                  # the primaries' turn about the wing's normal, toward the ground


def _wing_frame(u, face):
    """The rotation taking the sculpted left wing's plane (wing_R) to a wing whose arm runs along u with its front
    face looking toward face."""
    Rr = wing_R(1)
    u = S.normalize(u)
    w = -S.normalize(face)
    w = S.normalize(w - (w @ u) * u)
    v = np.cross(w, u)
    return np.stack([u, v, w], axis=1) @ Rr.T


def _rest_rot():
    """The spread wing's rest rotation (DSL space): raised about the forward axis, then swept back about the vertical."""
    return S.rot_y(-WING_SWEEP_BACK) @ S.rot_z(WING_RAISE_DEG)


def _fold_targets():
    """(the left wing's rest, raised and closed DSL degrees; the left hand's raised and closed) - the keys relative to
    each part's rest, as the game adds them to the rest pose."""
    Rr = wing_R(1)
    rest = euler_dsl(_rest_rot())
    raised = euler_dsl(_wing_frame(RAISE_U, RAISE_FACE))
    closed = euler_dsl(_wing_frame(FOLD_U, FOLD_FACE))
    hrest = HAND_REST[0]
    h_raised = euler_dsl(axis_rot(Rr[:, 2], RAISE_HAND))
    h_closed = euler_dsl(axis_rot(Rr[:, 1], FOLD_TUCK) @ axis_rot(Rr[:, 2], FOLD_HAND))
    rel = lambda e, r: tuple(a - b for a, b in zip(e, r))      # noqa: E731
    return (tuple(rest), rel(raised, rest), rel(closed, rest), rel(h_raised, hrest), rel(h_closed, hrest))


HAND_REST = ([0.0, HAND_REST_YAW, 0.0], [0.0, -HAND_REST_YAW, 0.0])     # left, right (the mirror: x, -y, -z)


def _solve():
    global WING_REST, WING_RESTS, MESH_WING_RAISED, MESH_WING_CLOSED, MESH_HAND_RAISED, MESH_HAND_CLOSED
    WING_REST, MESH_WING_RAISED, MESH_WING_CLOSED, MESH_HAND_RAISED, MESH_HAND_CLOSED = _fold_targets()
    WING_RESTS = (list(WING_REST), [WING_REST[0], -WING_REST[1], -WING_REST[2]])


_solve()


def quat_of(R):
    """A unit quaternion (w, x, y, z) of a rotation matrix."""
    w = math.sqrt(max(0.0, 1.0 + R[0, 0] + R[1, 1] + R[2, 2])) / 2
    x = math.copysign(math.sqrt(max(0.0, 1.0 + R[0, 0] - R[1, 1] - R[2, 2])) / 2, R[2, 1] - R[1, 2])
    y = math.copysign(math.sqrt(max(0.0, 1.0 - R[0, 0] + R[1, 1] - R[2, 2])) / 2, R[0, 2] - R[2, 0])
    z = math.copysign(math.sqrt(max(0.0, 1.0 - R[0, 0] - R[1, 1] + R[2, 2])) / 2, R[1, 0] - R[0, 1])
    q = np.array([w, x, y, z])
    return q / np.linalg.norm(q)


def mat_of(q):
    w, x, y, z = q
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                     [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                     [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])


def slerp(a, b, t):
    """Spherical interpolation of unit quaternions (the short way round) - JOML's Quaternionf.slerp."""
    c = float(a @ b)
    if c < 0:
        b, c = -b, -c
    if c > 0.9995:
        q = a + (b - a) * t
        return q / np.linalg.norm(q)
    th = math.acos(c)
    return (math.sin((1 - t) * th) * a + math.sin(t * th) * b) / math.sin(th)


def mc_quat(deg):
    """The ModelPart rotation (Minecraft part space: rotationZYX of mc_rot(deg)) of DSL degrees, as a quaternion."""
    return quat_of(_mrot(-math.radians(deg[0]), math.radians(deg[1]), -math.radians(deg[2])))


def dsl_of_mc_quat(q):
    """DSL degrees whose ModelPart rotation is the Minecraft-space quaternion q (the inverse of mc_quat)."""
    M = mat_of(q)
    ry = math.asin(max(-1.0, min(1.0, -M[2, 0])))
    rx = math.atan2(M[2, 1], M[2, 2])
    rz = math.atan2(M[1, 0], M[0, 0])
    return [-math.degrees(rx), math.degrees(ry), -math.degrees(rz)]


def key_quats():
    """The ModelPart rotations (Minecraft part space, quaternions w x y z, the LEFT side) at the three keys - spread
    (the rest), raised, closed - for the wing and its hand: what BuildingParts.MESH_* holds and the game slerps."""
    out = {}
    for part, rest, raised, closed in (("wing", WING_RESTS[0], MESH_WING_RAISED, MESH_WING_CLOSED),
                                       ("hand", HAND_REST[0], MESH_HAND_RAISED, MESH_HAND_CLOSED)):
        out[part] = [mc_quat(rest), mc_quat([r + e for r, e in zip(rest, raised)]),
                     mc_quat([r + e for r, e in zip(rest, closed)])]
    return out


def keyed_quat(keys, f):
    """The quaternion at fold f (degrees): slerped spread -> raised over [0, WING_RAISE_AT], raised -> closed after -
    the short way round between keys (r10: a straight Euler sweep between raised and closed swung each wing out
    sideways on its way down - L2 and L3 lopsided again)."""
    fr = WING_RAISE_AT * WING_FOLD_MAX
    if f <= fr:
        return slerp(keys[0], keys[1], f / fr)
    return slerp(keys[1], keys[2], min(1.0, (f - fr) / (WING_FOLD_MAX - fr)))


def fold_pose(level, f=None):
    """The rest-relative DSL rotations (degrees) of the moving parts at a capitol level - exactly what
    StatueRenderClientEvents gives the mesh angel's ModelParts (BuildingParts.statueWingFold, the MESH_* key
    quaternions slerped; the right side mirrors the left: w x -y -z)."""
    f = fold_degrees(level) if f is None else f
    kq = key_quats()
    out = {}
    for part, rest_l, rest_r, name in (("wing", WING_RESTS[0], WING_RESTS[1], "wing"),
                                       ("hand", HAND_REST[0], HAND_REST[1], "wing_%s_hand")):
        q = keyed_quat(kq[part], f)
        for side, rest, qq in (("left", rest_l, q), ("right", rest_r, q * np.array([1.0, 1.0, -1.0, -1.0]))):
            e = dsl_of_mc_quat(qq)
            nm = "wing_" + side if part == "wing" else "wing_%s_hand" % side
            out[nm] = [a - b for a, b in zip(e, rest)]
    return out


def rig():
    """The sidecar tools/mesh_import.py reads: each part's parent, pivot (DSL px, body space) and rest rotation (DSL
    degrees). Vertices are written in body space; the importer makes them part-local."""
    parts = {}
    for name, (field, lo, hi, cells, parent, pivot) in PARTS.items():
        rot = [0.0, FACING if name == "body" else 0.0, 0.0]
        if name == "wing_left_hand":
            rot = list(HAND_REST[0])
        elif name == "wing_right_hand":
            rot = list(HAND_REST[1])
        elif name == "wing_left":
            rot = list(WING_RESTS[0])
        elif name == "wing_right":
            rot = list(WING_RESTS[1])
        parts[name] = {"parent": parent, "pivot": [float(v) for v in pivot], "rot": rot}
    return {"space": "dsl", "atlas": [ATLAS, ATLAS], "texture": TEXTURE, "parts": parts,
            "note": "written by tools/sculpt_angel.py"}


def _mrot(rx, ry, rz):
    """ModelPart.translateAndRotate's rotation: rotationZYX(z, y, x) - x first, then y, then z (radians)."""
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def posed(meshes, level=6, facing=0.0, show_outer=None):
    """The meshes posed as the game poses them at a capitol level (ModelPart's own arithmetic in Minecraft's y-down
    part space: offset from the parent's pivot, rotationZYX of the DSL rotation through dsl_to_game.mc_rot), back in
    DSL space. facing: the body's rest yaw (FACING in the game; 0 to judge the sculpt square on)."""
    pose = fold_pose(level)
    R = rig()["parts"]
    world = {}

    def to_mc(p):
        return np.array([p[0], 24.0 - p[1], p[2]])

    def mc_rot(deg):
        return (-math.radians(deg[0]), math.radians(deg[1]), -math.radians(deg[2]))

    def xf(name):
        if name in world:
            return world[name]
        info = R[name]
        rot = list(info["rot"])
        if name == "body":
            rot[1] = facing
        extra = pose.get(name, [0.0, 0.0, 0.0])
        rot = [rot[i] + extra[i] for i in range(3)]
        M = _mrot(*mc_rot(rot))
        piv = to_mc(info["pivot"])
        if info["parent"] is None:
            A, t = np.eye(3), np.zeros(3)
            off = piv
        else:
            A, t = xf(info["parent"])
            off = piv - to_mc(R[info["parent"]]["pivot"])
        # child: p_world = A @ (off + M @ local) + t, local = p_mc - piv (rest: the part's vertices about its pivot)
        world[name] = (A @ M, A @ off + t)
        return world[name]

    out = []
    for m in meshes:
        if m.name == "halo_outer" and not (level >= 6 if show_outer is None else show_outer):
            continue
        A, t = xf(m.name)
        piv = to_mc(R[m.name]["pivot"])
        local = np.stack([m.V[:, 0], 24.0 - m.V[:, 1], m.V[:, 2]], axis=1) - piv
        pw = local @ A.T + t
        nl = np.stack([m.N[:, 0], -m.N[:, 1], m.N[:, 2]], axis=1)
        nw = nl @ A.T
        V = np.stack([pw[:, 0], 24.0 - pw[:, 1], pw[:, 2]], axis=1)
        N = np.stack([nw[:, 0], -nw[:, 1], nw[:, 2]], axis=1)
        out.append(Mesh(m.name, V, N, m.C, m.UV, m.Q, m.mat))
    return out


# ------------------------------------------------------------------ the atlas -----------------------------------------
def paint_atlas(out_dir=TEX_DIR):
    """A 64 x 64 atlas of 8 x 8 cells: near-white with the finest grain per finish (the marble's colour, veins and
    carved depth are in the vertex colours); LabPBR _n (a calm micro-relief: feather barbs, the cloth's weave) and
    _s (smoothness per finish: polished skin, softer cloth, rough feathers; gold's F0)."""
    import materials
    from PIL import Image
    alb = np.ones((ATLAS, ATLAS, 4), dtype=np.float32)
    height = np.full((ATLAS, ATLAS), 0.5, dtype=np.float32)
    smooth = np.zeros((ATLAS, ATLAS), dtype=np.float32)
    f0 = np.full((ATLAS, ATLAS), 10.0, dtype=np.float32)
    pore = np.full((ATLAS, ATLAS), 70.0, dtype=np.float32)
    yy, xx = np.mgrid[0:8, 0:8].astype(np.float32)
    for name, (cx, cy) in REGIONS.items():
        x0, y0 = cx * 8, cy * 8
        if name == "feather":
            h = 0.5 + 0.18 * np.sin(yy * 2 * np.pi / 3.0)
        elif name == "cloth":
            h = 0.5 + 0.06 * np.sin((xx + yy) * 2 * np.pi / 4.0)
        elif name == "hair":
            h = 0.5 + 0.1 * np.sin(xx * 2 * np.pi / 4.0)
        else:
            h = np.full((8, 8), 0.5, dtype=np.float32)
        height[y0:y0 + 8, x0:x0 + 8] = h
        alb[y0:y0 + 8, x0:x0 + 8, :3] = 1.0
        smooth[y0:y0 + 8, x0:x0 + 8] = SMOOTH[name] * 255
        if name == "gold":
            f0[y0:y0 + 8, x0:x0 + 8] = materials.MATERIALS["gold"]["f0"]
            pore[y0:y0 + 8, x0:x0 + 8] = 0
    nx, ny, nz = materials.height_normals(height, 1.0, wrap=False)
    n = materials.encode_normal(nx, ny, np.ones_like(height), height)
    s = materials.encode_specular(smooth, f0, pore, 255)
    probs = materials.check_labpbr(n, s)
    if probs:
        raise SystemExit("atlas LabPBR: " + "; ".join(probs))
    os.makedirs(out_dir, exist_ok=True)
    Image.fromarray((alb * 255).astype(np.uint8), "RGBA").save(os.path.join(out_dir, TEXTURE + ".png"))
    Image.fromarray(n, "RGBA").save(os.path.join(out_dir, TEXTURE + "_n.png"))
    Image.fromarray(s, "RGBA").save(os.path.join(out_dir, TEXTURE + "_s.png"))


# ------------------------------------------------------------------ output --------------------------------------------
def write_obj(path, meshes, comment):
    """One OBJ: an 'o' per part, vertex colours after the position (the common 'v x y z r g b' extension Blender and
    MeshLab read), normals, UVs, quads. DSL space (y up, px)."""
    with open(path, "w") as fh:
        fh.write("# %s\n" % comment)
        bv = 1
        for m in meshes:
            fh.write("o %s\n" % m.name)
            for p, c in zip(m.V, m.C):
                fh.write("v %.4f %.4f %.4f %.4f %.4f %.4f\n" % (p[0], p[1], p[2], c[0], c[1], c[2]))
            for n in m.N:
                fh.write("vn %.4f %.4f %.4f\n" % tuple(n))
            for t in m.UV:
                fh.write("vt %.5f %.5f\n" % (t[0], 1.0 - t[1]))
            for q in m.Q:
                fh.write("f " + " ".join("%d/%d/%d" % (i + bv, i + bv, i + bv) for i in q) + "\n")
            bv += len(m.V)


MODEL_PX = 80.9            # BuildingParts.STATUES' modelPx (the cube statue's height): the game draws the mesh at its
                           # scale, 20 blocks per MODEL_PX
# the sculpt's px -> the written mesh's px: fixed (r9's, when the spread wing tips were MODEL_PX high), so the figure
# keeps its size whatever the wings do - r10's raised wings reach higher than r9's (87.7 written px, 21.7 blocks), and
# normalising their tips to MODEL_PX would have shrunk the body 8%
EXPORT_SCALE = 80.9 / 77.8
STATUE_BOX_HEADROOM = 1.12  # BuildingParts.STATUE_BOX_HEADROOM: the culling box's height over the row's 20 blocks
STATUE_HALF_SPAN = 12.6     # BuildingParts.STATUES' halfSpan (blocks): the culling box's reach round the anchor


def blocks_per_px():
    """Blocks per sculpt px in game (the export scale times the row's 20 blocks per MODEL_PX)."""
    return EXPORT_SCALE * 20.0 / MODEL_PX


def check_reach(meshes):
    """The posed mesh at every fold must stay inside the statue's culling box (StatueRenderClientEvents: the row's
    halfSpan round the anchor, its height times STATUE_BOX_HEADROOM above it). Returns (top, reach) in blocks and the
    problems."""
    top = reach = 0.0
    fd = fold_degrees
    try:
        for f in np.arange(0.0, WING_FOLD_MAX + 0.01, 2.0):
            globals()["fold_degrees"] = lambda level, f=f: f
            V = np.concatenate([m.V for m in posed(meshes, level=6)])
            top = max(top, V[:, 1].max())
            reach = max(reach, np.hypot(V[:, 0], V[:, 2]).max())
    finally:
        globals()["fold_degrees"] = fd
    top, reach = top * blocks_per_px(), reach * blocks_per_px()
    bad = []
    if top > 20.0 * STATUE_BOX_HEADROOM:
        bad.append("the statue rises %.2f blocks, its culling box %.2f" % (top, 20.0 * STATUE_BOX_HEADROOM))
    if reach > STATUE_HALF_SPAN:
        bad.append("the statue reaches %.2f blocks out, its culling half span is %.2f" % (reach, STATUE_HALF_SPAN))
    return top, reach, bad
BUILDING_PARTS = os.path.join(ROOT, "bar/src/main/java/com/solegendary/reignofnether/building/BuildingParts.java")


def fold_constants():
    """The numbers BuildingParts holds for the mesh angel's keyed fold: name -> value."""
    kq = key_quats()
    out = {"MESH_WING_RAISE_AT": WING_RAISE_AT}
    for part, key in (("WING", "wing"), ("HAND", "hand")):
        for k, qn in zip(kq[key], ("SPREAD", "RAISED", "CLOSED")):
            for comp, v in zip("WXYZ", k):
                out["MESH_%s_%s_%s" % (part, qn, comp)] = float(v)
    return out


def check_fold_constants():
    """BuildingParts' mesh fold keys must be what the sculpt solves (the game slerps the mesh angel's wings with them)."""
    import re
    src = open(BUILDING_PARTS).read()
    bad = []
    for name, v in fold_constants().items():
        m = re.search(r"\b%s = (-?[\d.]+)f" % name, src)
        if not m or abs(float(m.group(1)) - v) > 2e-5:
            bad.append("%s: BuildingParts has %s, the sculpt solves %.5f" % (name, m.group(1) if m else "nothing", v))
    return bad


def scaled(meshes, k):
    return [Mesh(m.name, m.V * k, m.N, m.C, m.UV, m.Q, m.mat) for m in meshes]


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", default=os.path.join(ROOT, "local", "sculpt"))
    ap.add_argument("--lods", default="0,1,2")
    ap.add_argument("--no-atlas", action="store_true")
    a = ap.parse_args()
    bad = check_fold_constants()
    if bad:
        raise SystemExit("sculpt_angel: " + "; ".join(bad))
    os.makedirs(a.out, exist_ok=True)
    scene = SceneField()
    k = EXPORT_SCALE
    for i, lod in enumerate(int(s) for s in a.lods.split(",")):
        meshes = build(lod, scene=scene)
        if i == 0:
            top, reach, bad = check_reach(meshes)
            print("in game: %.2f blocks to the highest wing tip, %.2f blocks' reach (scaled x%.4f)" % (top, reach, k))
            if bad:
                raise SystemExit("sculpt_angel: " + "; ".join(bad))
        name = "white_angel_statue" + (".lod%d" % lod if lod else "")
        path = os.path.join(a.out, name + ".obj")
        write_obj(path, scaled(meshes, k), "White Angel statue v3, tools/sculpt_angel.py LOD%d (DSL space: px, y up, "
                  "faces -z)" % lod)
        print("wrote %s: %d quads" % (path, sum(len(m.Q) for m in meshes)))
    r = rig()
    for part in r["parts"].values():
        part["pivot"] = [v * k for v in part["pivot"]]
    with open(os.path.join(a.out, "white_angel_statue.rig.json"), "w") as fh:
        json.dump(r, fh, indent=1)
    if not a.no_atlas:
        paint_atlas()


if __name__ == "__main__":
    main()
