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
FACING = 45.0                       # the body's rest yaw: faces the RTS camera's quarter (as the cube statue v2)

# ------------------------------------------------------------------ the pose: landmarks ---------------------------
# the engaged leg is the RIGHT (-x): hip up, knee locked; the free LEFT leg's knee forward, its heel lifted behind
HIP_R, KNEE_R, ANKLE_R = (-2.45, 25.9, 0.45), (-2.05, 13.7, -0.25), (-1.75, 2.35, 0.55)
HIP_L, KNEE_L, ANKLE_L = (2.65, 25.0, 0.45), (2.95, 14.3, -2.75), (3.85, 3.15, 2.45)
SHOULDER_R, SHOULDER_L = (-5.55, 40.55, 0.75), (5.35, 41.3, 0.75)
ELBOW_R, ELBOW_L = (-5.7, 32.1, -2.55), (6.65, 33.35, -1.6)
WRIST_R, WRIST_L = (-0.75, 26.75, -6.25), (1.95, 29.45, -6.2)
HEAD_C = np.array([-0.45, 46.95, 0.05])      # the skull's centre
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
    d = np.maximum(d, -P[:, 1] + 0.02)                      # flat on the plinth
    return d


def torso(P):
    d = S.ellipsoid(P, (-0.45, 26.8, 0.5), (4.95, 3.3, 3.5), S.rot_z(-8))           # the pelvis, the right hip up and out
    d = S.smin(d, S.ellipsoid(P, (0.0, 31.1, 0.55), (4.25, 3.9, 3.15)), 2.0)      # the abdomen
    d = S.smin(d, S.ellipsoid(P, (0.15, 37.0, 0.5), (5.4, 4.6, 3.45), S.rot_z(5)), 2.0)    # the thorax, swung back left
    for sx, sh in ((-1, SHOULDER_R), (1, SHOULDER_L)):
        d = S.smin(d, S.ellipsoid(P, (sx * 2.4 - 0.1, 38.2 + 0.3 * sx, -2.1), (2.4, 1.5, 1.0), S.rot_x(-12)), 1.2)
        d = S.smin(d, S.ellipsoid(P, sh + np.array((sx * -0.3, -0.4, 0.0)), (1.75, 1.9, 1.65)), 1.8)   # the deltoids
    d = S.smin(d, S.round_cone(P, (-0.3, 41.6, 0.75), (-0.4, 43.4, 0.4), 1.8, 1.6), 1.0)    # the neck
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
    for k in (-0.45, -0.05, 0.35):
        fist = S.smax(fist, -S.capsule(P, g + (0.2, k, -1.25), g + (1.25, k, -0.2), 0.065), 0.04)
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
    d = S.capsule(P, g, sword_at(27.9), 0.5)                                      # the grip
    d = S.smin(d, S.sphere(P, SWORD_TOP + (0, 0.25, 0), 0.82), 0.25)              # the pommel
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
    d = d - 0.1 * pleat * band
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
    """The cord under the breasts (Samothrace's high girdle), knotted at the front, its ends hanging."""
    pts = []
    for k in range(25):
        a = 2 * math.pi * k / 24
        pts.append((-0.15 + 4.75 * math.sin(a), 35.0 + 0.25 * math.sin(a), 0.55 - 3.55 * math.cos(a)))
    d = S.tube(P, pts, 0.24)
    d = S.smin(d, S.ellipsoid(P, (-0.4, 35.0, -3.35), (0.45, 0.38, 0.32)), 0.12)
    d = S.smin(d, S.tube(P, [(-0.4, 34.8, -3.4), (-0.7, 33.2, -3.5), (-0.6, 31.8, -3.3)], [0.22, 0.2, 0.24]), 0.1)
    d = S.smin(d, S.tube(P, [(-0.2, 34.8, -3.4), (0.3, 33.4, -3.55), (0.6, 32.3, -3.4)], [0.22, 0.2, 0.24]), 0.1)
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


def skirt_folds():
    """Fold tubes over the hull. Behind and to the lee: wide folds streaming toward WIND, swelling to the hem. On
    the engaged leg's outer side: straight columnar folds (the leg is a column). Across the free leg: catenaries
    hanging from the knee and folds pulled taut over the thigh. Between the legs: folds falling from the crotch."""
    tubes = []
    rng = np.random.RandomState(3)
    # the lee: from the back of the waist round to the right side
    for i in range(11):
        f = i / 10.0
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
        r1 = 0.95 + 0.45 * (1 - abs(f - 0.4)) + 0.15 * rng.uniform()
        tubes.append((np.array(pts), np.linspace(0.35, r1, 9)))
    # the engaged leg's outer side: columns
    for i in range(3):
        a = math.radians(-82 + 16 * i)
        pts = [(HIP_R[0] + math.sin(a) * 2.9 - 0.1 * k, 27.0 - 3.3 * k, HIP_R[2] - math.cos(a) * 2.6 + 0.12 * k) for k in range(9)]
        tubes.append((np.array(pts), np.linspace(0.3, 0.75, 9)))
    # catenaries hanging from the free knee toward the engaged shin
    for k, (dy, sag) in enumerate(((0.6, 1.3), (2.0, 2.0), (3.7, 2.6), (5.6, 3.1), (7.8, 3.5))):
        a = np.array(KNEE_L) + (0.3, -1.0 - dy, -1.25 + 0.12 * k)
        b = np.array(KNEE_R) + (-0.6, -1.6 - dy * 1.3, -1.05 + 0.12 * k)
        pts = [lerp(a, b, t) + (0.0, -sag * math.sin(math.pi * t), 0.45 * math.sin(math.pi * t)) for t in np.linspace(0, 1, 7)]
        tubes.append((np.array(pts), np.full(7, 0.5 + 0.05 * k)))
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
    for k in range(3):
        a = np.array((-3.4 + 0.3 * k, 27.5 - 2.6 * k, -2.55))
        b = np.array((3.6, 6.0 - 1.5 * k, -2.2 + 0.2 * k))
        pts = [lerp(a, b, t) + (0.0, 0.0, -0.55 * math.sin(math.pi * t) + 0.35) for t in np.linspace(0, 1, 7)]
        tubes.append((np.array(pts), np.full(7, 0.32)))
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
    d = S.bounded_smin(P, prims, 0.55, base=d)
    # a shell near the hem (the cloth's thickness, so the lifted hem shows its inside), and the hem itself
    y = P[:, 1]
    shell = np.maximum(d, -(d + 1.4))
    d = np.where((y < 8.0) & (hem_height(P) > 1.5), shell, d)
    d = S.smax(d, hem_height(P) - y, 0.15)
    fly = []
    for pts, radii in FLYING:
        lo, hi = pts.min(axis=0), pts.max(axis=0)
        fly.append((lambda Q, pts=pts, radii=radii: S.tube(Q, pts, radii), (lo + hi) / 2, np.linalg.norm(hi - lo) / 2 + 2.0))
    d = S.bounded_smin(P, fly, 0.6, base=d)
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
        if abs(f) > 0.99:
            radii = radii * 0.85
        out.append((sm, radii))
    return out


MANTLE = mantle_paths()


def mantle(P):
    prims = []
    for pts, radii in MANTLE:
        for k in range(0, len(pts) - 1, 4):
            seg, rr = pts[k:k + 5], radii[k:k + 5]
            lo, hi = seg.min(axis=0), seg.max(axis=0)
            prims.append((lambda Q, seg=seg, rr=rr: S.tube(Q, seg, rr), (lo + hi) / 2, np.linalg.norm(hi - lo) / 2 + 1.5))
    return S.bounded_smin(P, prims, 0.5)


# ------------------------------------------------------------------ the head: a Roman portrait -----------------------
def head_frame():
    return S.rot_y(HEAD_TURN) @ S.rot_x(HEAD_BOW)


def face(q):
    m = S.mirror_x(q)
    d = S.ellipsoid(q, (0.0, 0.45, 0.55), (2.72, 3.0, 3.3))                                  # the cranium
    d = S.smin(d, S.ellipsoid(q, (0.0, -1.1, -0.95), (1.95, 2.62, 2.1)), 1.3)                # the facial mass
    d = S.smin(d, S.ellipsoid(q, (0.0, 1.35, -1.5), (2.1, 1.35, 1.3)), 0.9)                  # the forehead
    d = S.smin(d, S.capsule(m, (0.3, -3.15, -2.05), (1.82, -1.8, 0.1), 0.7), 0.9)            # the jaw
    d = S.smin(d, S.ellipsoid(q, (0.0, -3.08, -2.6), (0.82, 0.8, 0.68)), 0.6)                # the chin
    d = S.smin(d, S.ellipsoid(m, (1.62, -0.48, -1.78), (0.82, 0.58, 0.78)), 0.8)             # cheekbones
    d = S.smin(d, S.ellipsoid(m, (1.12, -1.55, -2.1), (0.7, 0.8, 0.55)), 1.0)                # the cheeks' planes
    # the brow ridge, flowing into the nose without a dip (the Greek profile)
    d = S.smin(d, S.tube(m, [(0.0, 0.66, -2.86), (0.8, 0.68, -2.84), (1.5, 0.6, -2.6), (2.1, 0.42, -2.08)],
                         [0.36, 0.34, 0.3, 0.24]), 0.28)
    d = S.smax(d, -S.ellipsoid(m, (1.0, -0.02, -2.86), (0.62, 0.36, 0.34)), 0.22)            # the sockets
    ec = np.array([0.98, -0.1, -2.14])
    d = S.smin(d, S.sphere(m, ec, 0.6), 0.05)                                                # smooth eyeballs
    # the lids: shells round the eyeball, the upper heavy and half-lowered, the lower thin; an almond between them
    dx = (m[:, 0] - ec[0]) / 0.62
    upper_line = -0.03 - 0.24 * dx * dx
    lower_line = -0.42 + 0.2 * dx * dx
    shell_u = S.smax(S.sphere(m, ec, 0.74), -S.sphere(m, ec, 0.6), 0.02)
    shell_l = S.smax(S.sphere(m, ec, 0.66), -S.sphere(m, ec, 0.6), 0.02)
    up = S.smax(shell_u, upper_line - m[:, 1], 0.05)
    lo = S.smax(shell_l, m[:, 1] - lower_line, 0.04)
    lids = np.maximum(np.minimum(up, lo), np.abs(m[:, 0] - ec[0]) - 0.72)
    d = S.smin(d, lids, 0.1)
    d = S.smax(d, -S.tube(m, [(0.42, 0.33, -2.62), (0.98, 0.44, -2.72), (1.55, 0.3, -2.5)], 0.06), 0.05)  # the crease
    # the nose: one straight line from the brow to the tip, narrow bridge, the wings at its foot
    nose = S.round_cone(q, (0.0, 0.6, -2.96), (0.0, -1.18, -3.78), 0.27, 0.36)
    nose = S.smax(nose, np.abs(q[:, 0]) * 1.0 + (q[:, 2] + 3.0) * 0.3 - 0.42 - 0.2 * np.clip(-q[:, 1], 0, 2), 0.08)
    d = S.smin(d, nose, 0.2)
    d = S.smin(d, S.sphere(q, (0.0, -1.27, -3.58), 0.36), 0.15)
    d = S.smin(d, S.ellipsoid(m, (0.38, -1.38, -3.2), (0.28, 0.27, 0.34)), 0.2)
    d = S.smax(d, -S.ellipsoid(m, (0.21, -1.58, -3.42), (0.13, 0.07, 0.15)), 0.05)           # the nostrils
    # the mouth: a small muzzle, the philtrum, the lips as two crisp forms (the vermilion's edge carved, not blended):
    # the upper bowed and a touch forward of the lower, which is fuller; the corners lifted (Reims) and drilled
    d = S.smin(d, S.ellipsoid(q, (0.0, -1.9, -2.92), (0.8, 0.5, 0.4)), 0.4)
    d = S.smax(d, -S.capsule(q, (0.0, -1.62, -3.4), (0.0, -1.86, -3.36), 0.075), 0.06)
    up_lip = S.ellipsoid(q, (0.0, -1.99, -3.14), (0.7, 0.19, 0.27), S.rot_x(18))
    up_lip = S.smax(up_lip, -S.sphere(q, (0.0, -1.74, -3.44), 0.12), 0.05)                   # the cupid's bow
    lo_lip = S.ellipsoid(q, (0.0, -2.28, -3.07), (0.55, 0.21, 0.27), S.rot_x(-22))
    d = S.smin(d, up_lip, 0.07)
    d = S.smin(d, lo_lip, 0.08)
    d = S.smax(d, -S.tube(m, [(0.0, -2.11, -3.5), (0.38, -2.1, -3.3), (0.6, -2.06, -3.08), (0.68, -2.04, -2.96)], 0.04), 0.02)
    d = S.smax(d, -S.sphere(m, (0.68, -2.05, -2.98), 0.05), 0.02)
    d = S.smax(d, -S.capsule(q, (-0.45, -2.66, -3.06), (0.45, -2.66, -3.06), 0.09), 0.12)    # under the lower lip
    d = S.smin(d, S.ellipsoid(m, (2.6, -0.45, 0.45), (0.3, 0.82, 0.52), S.rot_y(-15)), 0.3)  # the ears
    d = S.smax(d, -S.ellipsoid(m, (2.82, -0.45, 0.5), (0.15, 0.48, 0.28), S.rot_y(-15)), 0.08)
    d = S.smin(d, S.round_cone(q, (0.0, -2.2, 0.55), (0.0, -6.5, 0.8), 1.6, 1.85), 1.0)       # the neck
    return d


HAIR_C, HAIR_R = np.array([0.0, 0.62, 0.62]), np.array([3.05, 3.1, 3.5])
CROWN = np.array([0.15, 1.0, 0.25])         # the whorl's direction from the hair's centre (a little behind the top)


def hair_theta_end(az):
    """How far down the hair comes (the polar angle from the top) by azimuth (0 = the brow): high on the forehead,
    over the ears, to the nape at the back."""
    return 1.04 + 1.16 * np.abs(np.sin(np.asarray(az) / 2)) ** 0.6


def hair_surf(th, az):
    return HAIR_C + HAIR_R * np.array([math.sin(th) * math.sin(az), math.cos(th), -math.sin(th) * math.cos(az)])


def comma(th, az, r, turn, rng):
    """One lock: a tail combed down from upstream (toward the crown, or the parting at the brow) ending in a spiral
    curl rolled flat on the hair, a running-drill hole at its eye."""
    p = hair_surf(th, az)
    n = S.normalize((p - HAIR_C) / HAIR_R ** 2)
    crown = HAIR_C + HAIR_R * S.normalize(CROWN)
    f = p - crown
    if abs(az) < 1.0 and th < 1.3:                        # the brow: out from the central parting to the temples
        f = f + np.array([math.copysign(2.2, az if az != 0 else 1.0), 0.0, 0.0]) * (1 - abs(az))
    f -= n * (f @ n)
    f /= max(1e-6, np.linalg.norm(f))
    side = np.cross(n, f) * turn
    pts = []
    L = r * rng.uniform(2.0, 2.4)
    for k in range(5):
        t = k / 4.0
        pts.append(p - f * L * (1 - t) + side * 0.2 * r * math.sin(math.pi * t) + n * (0.08 + 0.1 * math.sin(math.pi * t)))
    cen = p + side * r * 0.85
    for k in range(1, 11):
        ph = k / 10 * 1.75 * math.pi
        rr = r * 0.85 * (1 - 0.55 * k / 10)
        pts.append(cen - side * rr * math.cos(ph) + f * rr * math.sin(ph) + n * (0.12 + 0.06 * k / 10))
    pts = np.array(pts)
    radii = np.concatenate([np.linspace(0.26, 0.36, 5) * r / 0.5, np.linspace(0.34, 0.15, 10) * r / 0.5])
    return pts, radii, cen + n * 0.1


def flat_tube(pts, radii, n, flat=2.0):
    """A lock: a tube along pts flattened along the hair's normal n (a ribbon lying on the head, not a worm)."""
    n = S.normalize(n)
    c = pts.mean(axis=0)

    def f(P):
        off = (P - c) @ n
        return S.tube(P + (flat - 1.0) * off[:, None] * n[None, :], pts, radii) / flat ** 0.5
    return f


def radial_tube(pts, radii, flat=2.2):
    """A lock lying on the head: a tube along pts flattened along the radius from the hair's centre at every point
    (so a lock that curves over the crown stays a ribbon on the surface, never a fin)."""
    rad = np.linalg.norm((pts - HAIR_C), axis=1).mean()

    def f(P):
        rel = P - HAIR_C
        r = np.linalg.norm(rel, axis=1)
        n = rel / np.maximum(r, 1e-6)[:, None]
        Q = HAIR_C + n * (rad + (r - rad) * flat)[:, None]
        return S.tube(Q, pts, radii) / flat ** 0.5
    return f


def curl_prims():
    """The hair as a Roman carver cuts it (the Augustus of Prima Porta, the Antinous): broad wavy locks combed from the
    whorl over the crown - flat ribbons with the running drill's channels between them - ending in two rows of comma
    curls that frame the face, the temples and the nape, every curl's eye drilled. Mirrored left and right; the brow
    locks part at the middle and sweep to the temples."""
    prims, holes = [], []
    rng = np.random.RandomState(11)
    crown = S.normalize(CROWN)
    n = 20
    for i in range(n):
        az0 = -math.pi + 2 * math.pi * (i + 0.5) / n
        end = float(hair_theta_end(az0)) - 0.42
        pts = []
        for k in range(10):
            t = k / 9.0
            th = 0.32 + (end - 0.32) * t
            sweep = math.copysign(0.5, az0) * max(0.0, 1 - abs(az0) / 1.3) * t ** 1.3
            az = az0 + sweep + 0.09 * math.sin(2 * math.pi * 1.2 * t + 1.7 * i)
            pts.append(HAIR_C + (HAIR_R - 0.02) * np.array([math.sin(th) * math.sin(az), math.cos(th),
                                                          -math.sin(th) * math.cos(az)]))
        pts = np.array(pts)
        radii = np.linspace(0.42, 0.62, len(pts))
        lo, hi = pts.min(axis=0), pts.max(axis=0)
        prims.append((radial_tube(pts, radii), (lo + hi) / 2, np.linalg.norm(hi - lo) / 2 + 1.0))
    for row, (dth, cnt, r0) in enumerate(((-0.06, 19, 0.6), (-0.33, 16, 0.52))):
        for i in range(cnt):
            az = -math.pi + 2 * math.pi * (i + 0.5 + 0.5 * row) / cnt
            th = float(hair_theta_end(az)) + dth
            r = r0 * rng.uniform(0.92, 1.08)
            pts, radii, hole = comma(th, az, r, 1 if az > 0 else -1, rng)
            nrm = (hair_surf(th, az) - HAIR_C) / HAIR_R ** 2
            lo, hi = pts.min(axis=0), pts.max(axis=0)
            prims.append((flat_tube(pts, radii, nrm), (lo + hi) / 2, np.linalg.norm(hi - lo) / 2 + 0.9))
            holes.append((lambda P, c=hole, r=r: S.sphere(P, c, r * 0.15), hole, 0.5))
    return prims, holes


CURLS, HOLES = curl_prims()


def hair_local(q):
    cap = S.ellipsoid(q, HAIR_C, HAIR_R - 0.06)
    rel = (q - HAIR_C) / HAIR_R
    th = np.arccos(np.clip(rel[:, 1] / np.maximum(np.linalg.norm(rel, axis=1), 1e-6), -1, 1))
    az = np.arctan2(rel[:, 0], -rel[:, 2])
    cap = S.smax(cap, (th - hair_theta_end(az) + 0.15) * 2.6, 0.35)
    h = S.bounded_smin(q, CURLS, 0.14, base=cap)
    return S.smax(h, -S.bounded_min(q, HOLES), 0.04)


def head_local(q):
    return S.smin(face(q), hair_local(q), 0.15)


def head_field(P):
    """The head in body space; outside a 9 px ball only its bound (nothing out there sees it)."""
    out = S.sphere(P, HEAD_C, 6.0) + 2.0
    near = out < 3.0
    if near.any():
        out[near] = head_local(S.frame(P[near], HEAD_C, head_frame()))
    return out


# ------------------------------------------------------------------ the body part ----------------------------------
NECK_CUT = 43.6     # the head part (finer cells) and the body part meet inside the neck here


def body_field(P):
    d = S.smin(legs(P), feet(P), 0.5)
    d = S.smin(d, torso(P), 1.5)
    d = S.smin(d, skirt(P), 0.35)
    d = S.smin(d, kolpos(P), 0.3)
    d = S.smin(d, bodice(P), 0.35)
    d = S.smin(d, belt(P), 0.12)
    d = S.smin(d, mantle(P), 0.35)
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
WING_SWEEP, WING_LEAN = -24.0, 13.0       # the wing's plane turned back, its top reclined
WRIST_UV = np.array([12.4, 17.2])         # the wrist in the wing's plane: the hand part's pivot
BONES = [(0.0, 0.0), (4.2, 4.6), (7.8, 9.6), (WRIST_UV[0], WRIST_UV[1]), (14.8, 22.2), (16.3, 25.6)]


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
    q[:, 2] = q[:, 2] + 0.0075 * u * u + 0.0026 * np.maximum(v, 0) ** 2 * np.clip(u / 12.0, 0, 1)
    return q


def feather_set():
    """(name, part, base uv, angle deg, length, width, layer w, bend) for every feather of the left wing; the right
    mirrors it. Primaries fan from the hand like a held hand of cards; secondaries hang from the forearm, tertials by
    the body; greater and median coverts overlap them like shingles; the alula at the wrist."""
    F = []
    # primaries (the hand part): bases along the hand, outermost (P10) pointing up and out
    for i in range(13):
        t = i / 12.0                                           # 0 = P1 (by the wrist) .. 1 = the outermost (the tip)
        base = lerp(WRIST_UV, BONES[-1], t)
        ang = -8.0 + 68.0 * t ** 0.9
        L = 17.5 + 6.5 * math.sin(math.pi * min(1.0, 0.15 + t * 0.95)) - 1.0 * t
        F.append(("primary", "hand", base, ang, L, 4.2 - 0.6 * t, 0.11 * i, 1.0))
    # secondaries along the forearm, the outer ones swept out, the inner hanging
    for i in range(12):
        t = i / 11.0                                           # 0 = by the wrist .. 1 = by the body
        base = lerp(WRIST_UV, BONES[2], t * 1.0) if t < 0.5 else lerp(BONES[2], BONES[1], (t - 0.5) / 0.5)
        ang = -14.0 - 66.0 * t
        L = 15.5 + 1.5 * math.sin(math.pi * min(1.0, t * 1.2)) - 2.5 * t
        F.append(("secondary", "arm", base, ang, L, 3.1, 0.06 * (11 - i), 0.8))
    for i in range(4):                                          # tertials by the body
        t = i / 3.0
        base = lerp(BONES[1], (1.2, 1.0), t)
        F.append(("tertial", "arm", base, -84.0 - 6.0 * t, 12.5 - 1.5 * t, 3.4, -0.25, 0.5))
    # greater coverts over the secondaries' bases, median coverts above them
    for i in range(13):
        t = i / 12.0
        base = lerp(BONES[3], BONES[1], t) + (0.0, 0.3)
        F.append(("gcovert", "arm", base, -18.0 - 64.0 * t, 7.0 + 1.6 * math.sin(math.pi * t), 2.9, -0.55 - 0.03 * i, 0.45))
    for i in range(11):
        t = i / 10.0
        base = lerp(BONES[3], BONES[1], t) + (-0.4, 1.1)
        F.append(("mcovert", "arm", base, -22.0 - 60.0 * t, 4.2 + 1.0 * math.sin(math.pi * t), 2.4, -0.95 - 0.03 * i, 0.3))
    # the back of the wing: dorsal coverts in two shingled rows over the backing (seen from behind and in the fold)
    for i in range(13):
        t = i / 12.0
        base = lerp(BONES[3], BONES[1], t) + (0.0, 0.2)
        F.append(("dcovert", "arm", base, -18.0 - 64.0 * t, 7.4 + 1.4 * math.sin(math.pi * t), 3.0, 0.95 + 0.03 * i, 0.3))
    for i in range(11):
        t = i / 10.0
        base = lerp(BONES[3], BONES[1], t) + (-0.4, 1.0)
        F.append(("dcovert", "arm", base, -22.0 - 60.0 * t, 4.4 + 1.0 * math.sin(math.pi * t), 2.5, 1.35 + 0.03 * i, 0.2))
    for i in range(7):
        t = i / 6.0
        F.append(("dcovert", "hand", lerp(WRIST_UV, BONES[-1], t), -6.0 + 70.0 * t, 7.8, 3.0, 0.95 + 0.03 * i, 0.3))
    # primary coverts on the hand and the alula at the wrist
    for i in range(7):
        t = i / 6.0
        base = lerp(WRIST_UV, BONES[-1], t)
        F.append(("pcovert", "hand", base, -6.0 + 70.0 * t, 7.6, 2.9, -0.55 - 0.03 * i, 0.4))
    F.append(("alula", "hand", WRIST_UV + (0.2, 0.6), 55.0, 5.5, 1.6, -0.9, 0.3))
    return F


FEATHERS = feather_set()


def feather_fn(base, ang, L, W, layer, bend, tmin):
    """One feather in the wing plane: a tapered vane (narrow at the quill, round at the tip) curving back along its
    length, its rachis raised on the front, and a backing behind its inner three quarters (widened, so neighbours
    close up and no daylight shows through the wing: ART-FAULTS "gaps between parts"). Every thickness >= tmin, so a
    coarse LOD keeps the vane whole."""
    a = math.radians(ang)
    d2 = np.array([math.cos(a), math.sin(a)])
    n2 = np.array([-d2[1], d2[0]])

    def vane(s, r, Lx, Wx):
        sn = np.clip(s / Lx, 0, 1)
        hw = Wx / 2 * np.clip(sn / 0.25, 0.3, 1.0) ** 0.6 * (1.0 - 0.25 * sn * sn)
        tip = s - (Lx - hw)
        d_tip = np.sqrt(np.maximum(tip, 0) ** 2 + r * r) - hw
        return np.where(tip > 0, d_tip, np.maximum(np.abs(r) - hw, -s)), sn

    def slab(d2d, dz):
        return np.where((d2d > 0) & (dz > 0), np.sqrt(d2d ** 2 + dz ** 2), np.maximum(d2d, dz))

    def f(q):
        rel = q[:, :2] - base
        s = rel @ d2
        r = rel @ n2
        d2d, sn = vane(s, r, L, W)
        wc = layer + bend * sn * sn
        th = np.maximum(0.62 * (1.0 - 0.3 * sn), tmin)
        out = slab(d2d, np.abs(q[:, 2] - wc) - th / 2)
        rach = np.maximum(np.abs(r) - 0.13, np.maximum(-s, s - L * 0.9))
        out = S.smin(out, np.maximum(rach, np.abs(q[:, 2] - wc + th * 0.5) - 0.13), 0.08)
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
        prims.append((feather_fn(np.asarray(base, float), ang, L, W, layer, bend, tmin), mid, L / 2 + W + 1.2))
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
    return S.torus(P, HALO_C, 5.6, 0.13, R)


# ------------------------------------------------------------------ the parts ---------------------------------------
# name: (field(P, h), box lo, box hi, cell size per LOD, parent, pivot (body space), material)
def _parts():
    wl, wr = WING_ROOT_L, WING_ROOT_L * np.array([-1, 1, 1])
    hl, hr = wing_world(1, WRIST_UV), wing_world(-1, WRIST_UV)
    return {
        "body": (lambda P, h: body_part(P), (-17.5, -0.2, -9.5), (8.5, 43.5, 19.0), (0.45, 0.8, 1.4), None, (0.0, 0.0, 0.0)),
        "head": (lambda P, h: head_part(P), (-5.6, 40.6, -5.2), (4.2, 52.6, 4.6), (0.14, 0.28, 0.55), "body", (0.0, 0.0, 0.0)),
        "wing_left": (lambda P, h: wing_field(P, 1, "arm", 1.9 * h), (-2.0, 20.0, -6.0), (24.0, 62.0, 16.0),
                      (0.5, 0.7, 1.2), "body", tuple(wl)),
        "wing_left_hand": (lambda P, h: wing_field(P, 1, "hand", 1.9 * h), (4.0, 38.0, -12.0), (42.0, 92.0, 20.0),
                           (0.5, 0.7, 1.2), "wing_left", tuple(hl)),
        "wing_right": (lambda P, h: wing_field(P, -1, "arm", 1.9 * h), (-24.0, 20.0, -6.0), (2.0, 62.0, 16.0),
                       (0.5, 0.7, 1.2), "body", tuple(wr)),
        "wing_right_hand": (lambda P, h: wing_field(P, -1, "hand", 1.9 * h), (-42.0, 38.0, -12.0), (-4.0, 92.0, 20.0),
                            (0.5, 0.7, 1.2), "wing_right", tuple(hr)),
        "halo": (lambda P, h: halo_field(P), HALO_C - 5.0, HALO_C + 5.0, (0.25, 0.45, 0.8), "body", tuple(HALO_C)),
        "halo_outer": (lambda P, h: halo_outer_field(P), HALO_C - 6.5, HALO_C + 6.5, (0.22, 0.4, 0.7), "body",
                       tuple(HALO_C)),
    }


PARTS = _parts()


# ------------------------------------------------------------------ materials and the bake ------------------------
# atlas cells (8 x 8 texels each in a 64 x 64 atlas): the finish of every surface region
REGIONS = {"skin": (0, 0), "cloth": (1, 0), "feather": (2, 0), "hair": (3, 0), "gold": (0, 1), "blade": (1, 1)}
SMOOTH = {"skin": 0.78, "cloth": 0.5, "feather": 0.34, "hair": 0.45, "gold": 0.74, "blade": 0.82}
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
    m[sw & ((y > gy - 1.2) & (np.abs(V[:, 0] - sword_at(y)[:, 0]) < 4.4))] = "gold"
    m[sw & (y > 27.6)] = "gold"
    return m


def marble_albedo(V, mat):
    """Solid marble: veins as thin turbulent sheets running through the block (continuous across every part, as in
    a real carving), a soft large-scale cloudiness, and the finish's tint per region."""
    p = V * 0.11
    turb = (np.sin(p[:, 0] * 1.7 + p[:, 1] * 0.6) + 0.5 * np.sin(p[:, 1] * 3.1 - p[:, 2] * 2.3 + 1.1)
            + 0.25 * np.sin(p[:, 2] * 6.3 + p[:, 0] * 4.7 + 2.0) + 0.12 * np.sin((p[:, 0] - p[:, 1]) * 13.0 + 0.4))
    vein = np.abs(np.sin(p[:, 0] * 0.9 + p[:, 1] * 2.2 - p[:, 2] * 0.7 + 2.2 * turb))
    vein = np.exp(-(vein / 0.08) ** 2) * 0.10 + np.exp(-(vein / 0.25) ** 2) * 0.035
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
        lo = np.array([-36.0, -1.0, -11.0])
        hi = np.array([36.0, 88.0, 21.0])

        def f(P):
            d = body_part(P)
            d = np.minimum(d, head_part(P))
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


def build(lod=0, only=None, scene=None, verbose=True):
    out = []
    for name, (field, lo, hi, hs, parent, pivot) in PARTS.items():
        if only and name not in only:
            continue
        t0 = time.time()
        h = hs[min(lod, len(hs) - 1)]
        V, N, Q = S.mesh(lambda P: field(P, h), lo, hi, h)
        C, mat = bake(name, V, N, scene, lambda P: field(P, h))
        UV = uv_for(V, N, mat)
        if verbose:
            print("  %-16s LOD%d h=%.2f: %6d verts %6d quads  %.1fs" % (name, lod, h, len(V), len(Q), time.time() - t0))
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


# the mesh angel's fold, per degree of BuildingParts.statueWingFold (0 spread .. WING_FOLD_MAX closed): the wing
# turns back about the shoulder blade (y), swings down (z) and leans in (x); the hand folds the primaries down along
# the forearm. BuildingParts.MESH_WING_* holds the same numbers.
def euler_dsl(D):
    """DSL degrees [x, y, z] whose ModelPart rotation (mc_rot, then rotationZYX in y-down part space) is the DSL-space
    rotation matrix D."""
    F = np.diag([1.0, -1.0, 1.0])
    M = F @ D @ F
    ry = math.asin(max(-1.0, min(1.0, -M[2, 0])))
    rx = math.atan2(M[2, 1], M[2, 2])
    rz = math.atan2(M[1, 0], M[0, 0])
    return [-math.degrees(rx), math.degrees(ry), -math.degrees(rz)]


# the closed wing (L1): its arm rising back from the shoulder blade, its feathered face turned out and forward (the
# RTS camera reads it as a wing, not an edge), the feathers hanging down behind the body; the hand folded down
FOLD_U = (0.3, -0.15, 0.95)        # shoulder blade -> wrist: straight back, a little down
FOLD_FACE = (0.85, 0.0, -0.5)       # where the wing's front (covert) face looks
FOLD_HAND = -120.0                  # the primaries' turn about the wing's normal, toward the ground


def _fold_targets():
    Rr = wing_R(1)
    u = S.normalize(FOLD_U)
    w = -S.normalize(FOLD_FACE)
    w = S.normalize(w - (w @ u) * u)
    v = np.cross(w, u)
    Rf = np.stack([u, v, w], axis=1)
    wing = euler_dsl(Rf @ Rr.T)
    hand = euler_dsl(axis_rot(Rr[:, 2], FOLD_HAND))
    return tuple(a / WING_FOLD_MAX for a in wing), tuple(a / WING_FOLD_MAX for a in hand)


MESH_WING, MESH_HAND = _fold_targets()


def fold_pose(level, f=None):
    """The rest-relative DSL rotations (degrees) of the moving parts at a capitol level - exactly what
    StatueRenderClientEvents applies to the mesh angel's ModelParts (BuildingParts.statueWingFold, MESH_WING_*)."""
    f = fold_degrees(level) if f is None else f
    wx, wy, wz = MESH_WING
    hx, hy, hz = MESH_HAND
    return {"wing_left": [wx * f, wy * f, wz * f], "wing_right": [wx * f, -wy * f, -wz * f],
            "wing_left_hand": [hx * f, hy * f, hz * f], "wing_right_hand": [hx * f, -hy * f, -hz * f]}


def rig():
    """The sidecar tools/mesh_import.py reads: each part's parent, pivot (DSL px, body space) and rest rotation (DSL
    degrees). Vertices are written in body space; the importer makes them part-local."""
    parts = {}
    for name, (field, lo, hi, hs, parent, pivot) in PARTS.items():
        parts[name] = {"parent": parent, "pivot": [float(v) for v in pivot],
                       "rot": [0.0, FACING if name == "body" else 0.0, 0.0]}
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


MODEL_PX = 80.9            # BuildingParts.STATUES' modelPx: the mesh is written at this height (soles to wing tips, L6)
BUILDING_PARTS = os.path.join(ROOT, "bar/src/main/java/com/solegendary/reignofnether/building/BuildingParts.java")


def check_fold_constants():
    """BuildingParts.MESH_WING_* / MESH_HAND_* must be what _fold_targets solves (the game folds the mesh with them)."""
    import re
    src = open(BUILDING_PARTS).read()
    bad = []
    for name, vals in (("MESH_WING", MESH_WING), ("MESH_HAND", MESH_HAND)):
        for axis, v in zip("XYZ", vals):
            m = re.search(r"%s_%s = (-?[\d.]+)f" % (name, axis), src)
            if not m or abs(float(m.group(1)) - v) > 2e-5:
                bad.append("%s_%s: BuildingParts has %s, the sculpt solves %.5f" % (name, axis, m.group(1) if m else "nothing", v))
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
    k = None
    for lod in (int(s) for s in a.lods.split(",")):
        meshes = build(lod, scene=scene)
        if k is None:                        # the finest LOD's height at L6 sets the scale for all of them
            height = max(m.V[:, 1].max() for m in posed(meshes, level=6))
            k = MODEL_PX / height
            print("height at L6 %.2f px -> scaled x%.4f to %.1f px" % (height, k, MODEL_PX))
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
