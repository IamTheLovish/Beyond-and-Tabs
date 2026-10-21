#!/usr/bin/env python3
"""The White Angel capitol's statue, v3: a SMOOTH MARBLE SCULPTURE - the first sculpted mesh body (lovish, Oct 21:
"more fluidity", "the face doesn't look like a Roman face or sculpting", "I thought we added a way to add smooth
mesh"). Sculpted as signed-distance fields (tools/sdf_mesh.py) and meshed with surface nets; written as an OBJ with
one object per bone, which tools/mesh_import.py turns into the .bmesh the game draws (StatueRenderClientEvents on a
MeshBody; the cube statue v2 stays the fallback when the mesh file is missing).

What the sculpt follows (the references of claude/capitol-next-brief.md, techniques not copies):
  - BERNINI'S PONTE SANT'ANGELO ANGELS: the whole figure one S-curve; drapery blown by a wind nobody else feels,
    undercut so folds stand free with shadow behind them; the wings one continuous sweep from the shoulder to the tip.
  - THE WINGED VICTORY OF SAMOTHRACE: wet drapery clinging to the leg that takes the weight, deep windswept folds
    streaming off the free side; the belt high under the chest.
  - CONTRAPPOSTO (Polykleitos, the Prima Porta Augustus): the weight on the right leg, the right hip up, the right
    shoulder down, the left knee bent forward; the head turned a little toward the engaged side and bowed.
  - THE ROMAN / GRECO-ROMAN HEAD (Augustus, the Apollo Belvedere): the nose line continuing the brow line (the "Greek
    profile"), a strong brow ridge, deep-set heavy-lidded eyes, full calm lips, a rounded chin, the hair in carved
    curls with running-drill holes at their centres and drill channels between them.
  - THE SMILING ANGEL OF REIMS: calm, a hint of a smile at the lips' corners, the gothic angel on a gothic capitol.
  - MATERIAL FINISHES (Bernini): polished skin, softer cloth, rougher feathers - three regions of the atlas
    (tools/sculpt_angel.py paints them: albedo, LabPBR normal and specular maps).
Space: the DSL's - px, y up from the soles, the figure facing -z before its yaw, +x its left. Stature 50 px, the
wing tips at ~81 px (tools/gen_angel_statue.SCALE_BLOCKS: 20 blocks soles to tips; BuildingParts.STATUES keeps
modelPx = the mesh's height). Parts (bones the game moves, as the cube statue's): body (with the head, arms, robe,
sword), wing_left / wing_right (pivot at the shoulder blade: BuildingParts.statueWingFold turns them back and droops
them), halo and halo_outer (shown from L6).

    python3 tools/sculpt_angel.py [--out local/sculpt] [--lod 0,1] [--preview sheet.png]
      -> white_angel_statue.obj (LOD0), white_angel_statue.lod1.obj, white_angel_statue.rig.json, and the atlas
         bar/src/main/resources/assets/reignofnether/textures/entities/white_angel_mesh.png (+ _n, _s)
    then: python3 tools/mesh_import.py local/sculpt/white_angel_statue.obj --rig ... --lod ... --out meshes/...
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
ATLAS = 512

STATURE = 50.0
FACING = 45.0                 # the body's rest yaw: faces the RTS camera's quarter (as the cube statue v2)
HEAD_C = (-0.3, 46.9, 0.0)    # the skull's centre
HEAD_BOW, HEAD_TURN = -11.0, 9.0
WING_ROOT_L = (2.6, 39.0, 2.9)
HALO_C = (-0.3, 47.6, 2.1)

# the atlas: 4 x 4 cells of 128 px, one material finish each (tools/materials.py names)
REGIONS = {"skin": (0, 0), "cloth": (1, 0), "feather": (2, 0), "hair": (3, 0), "gold": (0, 1), "blade": (1, 1),
           "stone": (2, 1)}
CELL = ATLAS // 4


# ------------------------------------------------------------------ the figure's line -------------------------------
def cx(y):
    """The centre line's x by height: the contrapposto S - the pelvis over the right (engaged, -x) foot, the chest
    swung back left, the head centred again."""
    pts = [(0.0, -1.0), (14.0, -1.3), (25.5, -1.4), (31.0, -0.4), (36.5, 0.5), (41.5, 0.2), (46.0, -0.3), (52.0, -0.4)]
    return np.interp(y, [p[0] for p in pts], [p[1] for p in pts])


def half_widths(y):
    """The robe's elliptic cross-section half-widths (along x, along z) by height - the body's silhouette plus the
    cloth: the high belt at 33, the hem flaring to the plinth."""
    pts = [(0.0, 8.6, 7.4), (2.0, 8.3, 7.1), (6.0, 7.5, 6.2), (10.0, 6.9, 5.5), (16.0, 6.3, 4.9), (22.0, 6.0, 4.5),
           (26.0, 6.1, 4.4), (29.5, 5.6, 4.1), (32.0, 5.1, 3.8), (33.3, 4.8, 3.6), (35.0, 5.6, 4.1), (37.5, 6.3, 4.4),
           (40.0, 6.6, 4.3), (42.0, 5.4, 3.6), (43.5, 3.0, 2.4)]
    ys = [p[0] for p in pts]
    return np.interp(y, ys, [p[1] for p in pts]), np.interp(y, ys, [p[2] for p in pts])


# ------------------------------------------------------------------ the body ---------------------------------------
def legs(P):
    """The right leg straight and bearing (-x); the left knee bent and pushed forward, its foot trailing."""
    d = S.round_cone(P, (-2.4, 26.0, 0.4), (-2.5, 14.5, -0.5), 2.6, 1.9)             # right thigh
    d = S.smin(d, S.round_cone(P, (-2.5, 14.5, -0.5), (-2.3, 2.4, -0.8), 1.9, 1.15), 1.2)  # right shin
    d = S.smin(d, S.round_cone(P, (3.1, 25.4, 0.6), (3.8, 14.6, -3.4), 2.6, 1.9), 1.5)      # left thigh, forward
    d = S.smin(d, S.round_cone(P, (3.8, 14.6, -3.4), (3.9, 2.4, -0.4), 1.9, 1.15), 1.2)     # left shin back
    d = S.smin(d, S.sphere(P, (3.9, 14.9, -3.7), 2.1), 0.9)                                  # the knee cap
    # feet: heel to toe, the left one turned out a little
    d = S.smin(d, S.round_cone(P, (-2.3, 1.3, 0.6), (-2.0, 0.9, -4.9), 1.25, 1.35), 0.5)
    d = S.smin(d, S.round_cone(P, (3.9, 1.3, 1.2), (5.2, 0.9, -4.0), 1.25, 1.35), 0.5)
    d = S.smin(d, S.ellipsoid(P, (-2.2, 0.75, -4.9), (1.5, 0.75, 1.0)), 0.4)
    d = S.smin(d, S.ellipsoid(P, (5.3, 0.75, -4.0), (1.5, 0.75, 1.0), S.rot_y(-16)), 0.4)
    return d


def torso(P):
    d = S.ellipsoid(P, (cx(25.5), 25.6, 0.3), (5.5, 3.2, 3.7), S.rot_z(-5))                  # the pelvis, right hip up
    d = S.smin(d, S.ellipsoid(P, (cx(30.5), 30.5, 0.3), (4.7, 4.3, 3.2)), 2.0)              # the abdomen
    d = S.smin(d, S.ellipsoid(P, (cx(36.5), 36.6, 0.2), (6.0, 5.2, 3.7), S.rot_z(3)), 2.2)   # the thorax (shoulders tilt)
    for sx in (-1, 1):
        d = S.smin(d, S.ellipsoid(P, (cx(37.4) + sx * 2.8, 37.3, -2.5), (2.7, 1.7, 1.5), S.rot_x(-20)), 1.2)  # pectorals
        d = S.smin(d, S.sphere(P, (cx(41.3) + sx * 6.0, 41.3 - sx * 0.5, 0.1), 2.25), 1.4)   # deltoids
    d = S.smin(d, S.round_cone(P, (cx(42.3), 42.3, 0.4), (cx(44.6), 44.8, 0.0), 1.85, 1.55), 1.2)   # the neck
    return d


def arms(P):
    """Both hands on the sword's grip before the waist; the right arm (-x) straighter, the left elbow out."""
    d = S.round_cone(P, (-6.3, 41.4, 0.0), (-7.4, 33.6, -2.0), 1.8, 1.45)
    d = S.smin(d, S.round_cone(P, (-7.4, 33.6, -2.0), (-2.6, 27.4, -5.8), 1.45, 1.15), 1.0)
    d = S.smin(d, S.round_cone(P, (6.5, 41.0, 0.0), (8.4, 33.2, -1.0), 1.8, 1.45), 1.0)
    d = S.smin(d, S.round_cone(P, (8.4, 33.2, -1.0), (2.4, 29.6, -5.8), 1.45, 1.15), 1.0)
    # hands wrapped round the grip: a rounded block each with a thumb over the top
    d = S.smin(d, S.ellipsoid(P, (-1.1, 26.4, -6.3), (1.45, 1.7, 1.3), S.rot_z(20)), 0.6)
    d = S.smin(d, S.ellipsoid(P, (1.0, 28.9, -6.3), (1.45, 1.7, 1.3), S.rot_z(-20)), 0.6)
    d = S.smin(d, S.capsule(P, (-1.9, 27.6, -7.2), (0.1, 27.9, -7.4), 0.42), 0.3)
    d = S.smin(d, S.capsule(P, (1.9, 30.1, -7.2), (-0.1, 30.3, -7.4), 0.42), 0.3)
    return d


def sword(P):
    """A guardian's greatsword planted point-down on the plinth: grip, pommel, a drooping cross, a blade with a
    central ridge (a rhombus section) tapering to the point."""
    c = (0.0, 0.0, -6.3)
    d = S.capsule(P, (c[0], 24.0, c[2]), (c[0], 30.4, c[2]), 0.6)
    d = S.smin(d, S.sphere(P, (c[0], 31.2, c[2]), 1.05), 0.3)
    d = S.smin(d, S.box(P, (c[0], 23.5, c[2]), (3.4, 0.38, 0.55), r=0.2), 0.2)
    d = S.smin(d, S.sphere(P, (-3.5, 23.2, c[2]), 0.55), 0.3)
    d = S.smin(d, S.sphere(P, (3.5, 23.2, c[2]), 0.55), 0.3)
    # the blade: the distance in a section flattened 3:1, so a round cone reads as a lens with a ridge
    Q = P.copy()
    Q[:, 2] = (Q[:, 2] - c[2]) * 2.6 + c[2]
    blade = S.round_cone(Q, (c[0], 23.4, c[2]), (c[0], 0.6, c[2]), 1.25, 0.22) / 1.6
    ridge = np.abs(P[:, 2] - c[2]) * 0.9 + np.abs(P[:, 0] - c[0]) * 0.55 - 0.78
    blade = np.maximum(blade, ridge)                                                         # the rhombus section
    return S.smin(d, blade, 0.25)


def robe(P):
    """The chiton: an elliptic column round the body with deep folds that grow toward the hem, pressed flat against
    the weight-bearing right leg by the wind and streaming off the left-rear in wide swinging folds; fine pleats
    gathered above the high belt; a blouse of cloth (the kolpos) hanging over it."""
    y = P[:, 1]
    xo = P[:, 0] - cx(y)
    zo = P[:, 2] - 0.3
    ex, ez = half_widths(y)
    th = np.arctan2(zo, xo)
    # the wind blows from the figure's right-front (-x, -z): the lee is its left-rear (+x, +z)
    lee = 0.5 + 0.5 * np.cos(th - math.radians(40))
    wind = 1.0 - lee
    # the folds: deep and wide below the belt, swinging with height (the phase drifts), finer above it
    below = np.clip((33.0 - y) / 31.0, 0.0, 1.0)
    amp = (0.35 + 2.3 * below ** 1.25) * (0.42 + 0.58 * lee) * (1.0 - 0.6 * np.clip((y - 40.5) / 3.0, 0, 1))
    phase = 0.11 * y + 0.9 * below * below
    folds = np.sin(7.0 * th + phase) + 0.45 * np.sin(13.0 * th - 1.7 * phase + 0.8) + 0.22 * np.sin(23.0 * th + 2.1)
    folds = folds / 1.67
    above = np.clip((y - 33.3) / 7.5, 0.0, 1.0) * (1.0 - np.clip((y - 41.0) / 2.5, 0, 1))
    pleats = 0.28 * np.sin(17.0 * th - 0.35 * y) * above
    # the lee flare: the hem lifts and streams out behind the left leg
    flare = 3.6 * below ** 2.2 * lee ** 2.0
    # the windward press: the cloth hugs the right thigh and shin (the "wet" leg)
    press = -1.2 * wind ** 2 * np.clip((30.0 - y) / 26.0, 0, 1) * np.clip((y - 3.0) / 6.0, 0, 1)
    # the kolpos: a roll of cloth blousing over the belt, and the belt's own groove
    kolpos = 0.9 * np.exp(-((y - 34.6) / 1.1) ** 2) - 0.55 * np.exp(-((y - 33.1) / 0.45) ** 2)
    r = 1.0 + (amp * folds + pleats + flare + press + kolpos) / np.maximum(ex, 1e-6)
    rho = np.sqrt((xo / ex) ** 2 + (zo / ez) ** 2)
    d = (rho - r) * np.minimum(ex, ez) * 0.85
    # the chiton stops at the shoulders (pinned on each deltoid) and at the plinth
    d = np.maximum(d, y - 42.2)
    d = np.maximum(d, 0.35 - y)
    # the neckline: a shallow V scooped out of the front between the shoulder pins
    neck = S.ellipsoid(P, (cx(41.5), 42.4, -3.6), (3.0, 2.0, 2.6))
    d = S.smax(d, -neck, 0.5)
    return d


def mantle(P):
    """The himation: a roll over the left shoulder, swept across the back and flying off the left hip in a wind-caught
    sheet with rippled edges (Bernini's flying drapery; Samothrace's streaming cloth)."""
    roll = S.tube(P, [(5.0, 42.6, -2.4), (7.0, 42.2, 0.2), (6.4, 39.5, 3.4), (2.0, 34.0, 4.6), (-3.0, 28.6, 4.3),
                      (-5.8, 25.0, 2.6)], [1.1, 1.25, 1.3, 1.25, 1.15, 1.0])
    # the flying tail: a rippling sheet from the left hip out and up to the left-rear
    R = S.rot_y(-52) @ S.rot_x(14) @ S.rot_z(28)
    c = (6.2, 25.6, 3.0)
    poly = [(0.0, -2.8), (5.0, -3.6), (10.0, -3.0), (14.5, -3.8), (18.5, -2.6), (21.5, -3.2), (23.5, -1.0),
            (22.0, 1.6), (18.0, 2.0), (14.0, 3.2), (9.0, 3.0), (4.0, 3.6), (0.0, 3.0)]
    q = S.frame(P, c, R)
    ripple = 0.9 * np.sin(0.55 * q[:, 0] + 0.8) * np.sin(0.9 * q[:, 1]) + 0.55 * np.sin(1.3 * q[:, 0] - 0.6 * q[:, 1])
    q2 = q.copy()
    q2[:, 2] = q[:, 2] - ripple * np.clip(q[:, 0] / 6.0, 0, 1)
    thick = 0.95 - 0.35 * np.clip(q[:, 0] / 22.0, 0, 1)
    d2 = S.polygon2d(q2[:, :2], poly)
    dz = np.abs(q2[:, 2]) - thick / 2.0
    tail = np.where((d2 > 0) & (dz > 0), np.sqrt(d2 * d2 + dz * dz), np.maximum(d2, dz))
    # a rolled hem along the tail's lower edge
    hem = S.tube(P, [c + np.array(R @ (x, yv, 0.0)) for x, yv in poly[:7]], 0.62)
    return S.smin(S.smin(roll, tail, 0.9), hem, 0.5)


def body_field(P):
    d = legs(P)
    d = S.smin(d, torso(P), 1.6)
    d = S.smin(d, robe(P), 0.75)            # wet drapery: the body's masses show through the cloth
    d = S.smin(d, mantle(P), 0.7)
    d = S.smin(d, arms(P), 0.9)
    d = S.smin(d, sword(P), 0.25)
    d = S.smin(d, head_field(P), 0.8)
    return np.maximum(d, P[:, 1] - NECK_CUT)       # the head is its own part above the seam


def body_material(V):
    """Which atlas region each body vertex paints: skin above the robe and on the limbs that show, cloth elsewhere."""
    m = np.full(len(V), "cloth", dtype=object)
    y = V[:, 1]
    # the sword, metal
    sw = (np.abs(V[:, 0]) < 4.2) & (V[:, 2] < -4.9) & (y < 31.8)
    m[sw] = "blade"
    # the head, neck, arms and hands
    hd = S.ellipsoid(V, HEAD_C, (4.4, 5.0, 4.6)) < 0
    m[hd] = "skin"
    neck = (y > 41.9) & (np.abs(V[:, 0] - cx(y)) < 2.6) & (np.abs(V[:, 2]) < 2.6) & ~hd
    m[neck] = "skin"
    arm = np.minimum(S.round_cone(V, (-6.3, 41.4, 0.0), (-7.4, 33.6, -2.0), 2.3, 2.0),
                     S.round_cone(V, (-7.4, 33.6, -2.0), (-1.2, 26.4, -6.3), 2.0, 2.2))
    arm = np.minimum(arm, np.minimum(S.round_cone(V, (6.5, 41.0, 0.0), (8.4, 33.2, -1.0), 2.3, 2.0),
                                     S.round_cone(V, (8.4, 33.2, -1.0), (1.0, 28.9, -6.3), 2.0, 2.2)))
    m[(arm < 0) & ~sw] = "skin"
    feet = (y < 2.6) & ((V[:, 2] < -2.0) | (np.abs(V[:, 0] - 3.9) < 2.0) | (np.abs(V[:, 0] + 2.3) < 2.0))
    m[feet & (V[:, 2] < -1.5)] = "skin"
    # the halo is its own part; the belt cord gold
    belt = (np.abs(y - 33.1) < 0.4) & ~hd & ~sw & (arm >= 0)
    m[belt] = "gold"
    return m


# ------------------------------------------------------------------ the head: a Roman portrait -----------------------
def head_frame():
    """The head's local axes (columns): bowed HEAD_BOW about x, turned HEAD_TURN toward the engaged side."""
    return S.rot_y(HEAD_TURN) @ S.rot_x(HEAD_BOW)


def face_local(q):
    """The head in its own frame: origin the skull's centre, y up, the face toward -z. All numbers in px of a 50 px
    stature (the head 7.2 px tall with its hair: the classical eighth)."""
    # the cranium and the mass of the face
    d = S.ellipsoid(q, (0.0, 0.35, 0.35), (3.0, 3.45, 3.35))
    d = S.smin(d, S.ellipsoid(q, (0.0, -0.9, -0.6), (2.6, 2.9, 2.5)), 1.6)
    # the forehead: a full, nearly vertical plane down to the brow (the profile's line runs on into the nose)
    d = S.smin(d, S.ellipsoid(q, (0.0, 1.5, -1.6), (2.4, 1.6, 1.25)), 1.0)
    # the jaw: two lines from the chin to the angles under the ears, a rounded chin
    d = S.smin(d, S.capsule(q, (0.0, -3.2, -2.0), (-2.4, -1.5, 0.55), 0.95), 0.9)
    d = S.smin(d, S.capsule(q, (0.0, -3.2, -2.0), (2.4, -1.5, 0.55), 0.95), 0.9)
    d = S.smin(d, S.sphere(q, (0.0, -3.05, -2.2), 1.0), 0.7)
    # the cheekbones, high and wide; the temples hollowed a touch
    d = S.smin(d, S.sphere(q, (-1.95, -0.7, -1.6), 1.05), 0.9)
    d = S.smin(d, S.sphere(q, (1.95, -0.7, -1.6), 1.05), 0.9)
    # the brow ridge: a bar across the forehead bowed forward at the glabella, strong (the Roman brow)
    d = S.smin(d, S.tube(q, [(-2.3, 0.72, -2.1), (-1.2, 0.86, -2.72), (0.0, 0.92, -2.98), (1.2, 0.86, -2.72),
                             (2.3, 0.72, -2.1)], 0.5), 0.5)
    # the eye sockets under the brow; the eyeballs; heavy upper lids folding over them, thinner lower ones
    for sx in (-1, 1):
        d = S.smax(d, -S.ellipsoid(q, (sx * 1.4, 0.15, -2.85), (0.95, 0.72, 0.9)), 0.3)
        d = S.smin(d, S.sphere(q, (sx * 1.35, 0.1, -2.45), 0.8), 0.12)
        d = S.smin(d, S.tube(q, [(sx * 0.55, 0.3, -2.95), (sx * 1.3, 0.62, -3.12), (sx * 2.1, 0.38, -2.8)], 0.3), 0.2)
        d = S.smin(d, S.tube(q, [(sx * 0.6, -0.3, -2.92), (sx * 1.35, -0.42, -3.02), (sx * 2.1, -0.22, -2.76)], 0.19), 0.16)
    # the nose: one straight line from the brow to the tip (the Greek profile), the bridge narrow, the tip rounded, the alae
    d = S.smin(d, S.round_cone(q, (0.0, 0.9, -2.9), (0.0, -1.45, -3.72), 0.4, 0.56), 0.28)
    d = S.smin(d, S.sphere(q, (0.0, -1.6, -3.58), 0.52), 0.22)
    d = S.smin(d, S.sphere(q, (-0.56, -1.62, -3.2), 0.4), 0.28)
    d = S.smin(d, S.sphere(q, (0.56, -1.62, -3.2), 0.4), 0.28)
    d = S.smax(d, -S.sphere(q, (-0.3, -1.95, -3.5), 0.16), 0.08)
    d = S.smax(d, -S.sphere(q, (0.3, -1.95, -3.5), 0.16), 0.08)
    # the philtrum, the lips (the upper one bowed, the lower fuller), the mouth's line, the corners lifted a touch
    d = S.smax(d, -S.capsule(q, (0.0, -1.85, -3.4), (0.0, -2.1, -3.34), 0.15), 0.1)
    d = S.smin(d, S.tube(q, [(-0.95, -2.18, -2.95), (-0.35, -2.08, -3.24), (0.0, -2.16, -3.27), (0.35, -2.08, -3.24),
                             (0.95, -2.18, -2.95)], 0.26), 0.2)
    d = S.smin(d, S.tube(q, [(-0.8, -2.6, -3.0), (0.0, -2.6, -3.2), (0.8, -2.6, -3.0)], 0.32), 0.2)
    d = S.smax(d, -S.tube(q, [(-1.0, -2.3, -2.95), (0.0, -2.38, -3.32), (1.0, -2.3, -2.95)], 0.07), 0.03)
    # the chin's fold under the lower lip
    d = S.smax(d, -S.capsule(q, (-0.55, -3.0, -3.05), (0.55, -3.0, -3.05), 0.1), 0.06)
    # the ears
    for sx in (-1, 1):
        d = S.smin(d, S.ellipsoid(q, (sx * 3.0, -0.2, 0.35), (0.32, 0.95, 0.62)), 0.35)
        d = S.smax(d, -S.ellipsoid(q, (sx * 3.2, -0.2, 0.35), (0.2, 0.55, 0.35)), 0.1)
    # the neck joins below
    d = S.smin(d, S.round_cone(q, (0.0, -3.0, 0.4), (0.0, -7.0, 0.6), 1.55, 1.85), 0.9)
    return d


HAIR_C = np.array([0.0, 0.85, 0.5])
HAIR_R = np.array([3.15, 3.2, 3.45])


def hairline(x, z):
    """The hairline's height on the skull: high on the brow, dipping at the temples, down over the nape."""
    front = np.clip(-z / 3.2, 0.0, 1.0)
    return 1.7 * front + 0.3 * (1 - front) - 1.2 * np.clip(z / 3.0, 0, 1) - 0.8 * np.clip((np.abs(x) - 2.0) / 1.0, 0, 1)


def hair_local(q):
    """The hair as a thin cap above the hairline, carved into locks: each a flat comma-shaped lobe lying along the
    flow (combed forward from the crown, down the sides and nape), with a running-drill hole at its centre and the
    channel between locks (the Roman portrait technique); a row of small curls turned forward over the brow."""
    cap = S.ellipsoid(q, HAIR_C, HAIR_R)
    cap = np.maximum(cap, hairline(q[:, 0], q[:, 2]) - q[:, 1])
    locks = np.full(len(q), np.inf)
    holes = np.full(len(q), np.inf)
    rows = ((0.35, 7), (0.72, 13), (1.08, 17), (1.42, 20))   # polar angle from the crown, locks round the ring
    k = 0
    for phi, n in rows:
        for i in range(n):
            theta = 2 * math.pi * (i + 0.5 * (k % 2)) / n + 0.15 * math.sin(3.1 * i + k)
            dirv = np.array([math.sin(phi) * math.cos(theta), math.cos(phi), math.sin(phi) * math.sin(theta)])
            c = HAIR_C + dirv * HAIR_R
            if c[1] < hairline(c[0], c[2]) + 0.2:
                continue
            # the lock lies along the flow (down and forward over the skull) and bulges 0.45 px above the cap
            flow = np.array([0.0, -1.0, -0.6 if c[2] < 0.5 else 0.6])
            flow = flow - dirv * (flow @ dirv)
            flow = flow / max(1e-6, np.linalg.norm(flow))
            side = np.cross(dirv, flow)
            R = np.stack([flow, dirv, side], axis=1)
            L = 0.95 + 0.25 * ((i * 5 + k) % 3) / 2.0
            locks = np.minimum(locks, S.ellipsoid(q, c + dirv * 0.05, (L, 0.5, 0.62), R))
            holes = np.minimum(holes, S.sphere(q, c + dirv * 0.5 + flow * 0.35, 0.17))
            k += 1
    hair = S.smin(cap, locks, 0.2)
    hair = S.smax(hair, -holes, 0.04)
    # the brow row: small curls turned forward, left and right of a central fork
    for i in range(8):
        x = -2.3 + i * 0.66
        c = (x, 1.9 - 0.1 * abs(i - 3.5), -2.55 + 0.12 * abs(i - 3.5))
        hair = S.smin(hair, S.torus(q, c, 0.3, 0.19, S.rot_y(90) @ S.rot_x(25 * (i - 3.5))), 0.12)
    # locks curling down the nape
    for i in range(5):
        x = -1.5 + i * 0.75
        hair = S.smin(hair, S.tube(q, [(x, 0.3, 3.1), (x * 1.15, -0.7, 3.25), (x * 1.3, -1.6, 2.9)], [0.45, 0.4, 0.28]), 0.25)
    return hair


NECK_CUT = 43.4    # the head part and the body part meet inside the neck here (each capped inside the other)


def head_field(P):
    """The head, evaluated only near it (the hair's locks are many primitives): outside a 9.5 px sphere the field is
    the sphere's distance plus a margin wider than any blend, so nothing out there sees it."""
    out = S.sphere(P, HEAD_C, 9.5) + 4.0
    near = out < 4.0
    if near.any():
        q = S.frame(P[near], HEAD_C, head_frame())
        out[near] = S.smin(face_local(q), hair_local(q), 0.12)
    return out


def head_part(Pl):
    """The head as its own part (finer cells than the body): local to HEAD_C, cut below the neck's seam."""
    d = head_field(Pl + np.array(HEAD_C))
    return np.maximum(d, (NECK_CUT - 0.3) - (Pl[:, 1] + HEAD_C[1]))


def head_material(Vl):
    V = Vl + np.array(HEAD_C)
    q = S.frame(V, HEAD_C, head_frame())
    m = np.full(len(V), "skin", dtype=object)
    hair = (q[:, 1] > hairline(q[:, 0], q[:, 2]) + 0.1) & (hair_local(q) < 0.08)
    m[hair] = "hair"
    return m


# ------------------------------------------------------------------ the wings ---------------------------------------
# one continuous sweep: the leading edge a cubic from the shoulder blade up and out to the tip above the head
WING_EDGE = ((0.0, 0.0), (9.0, 17.0), (24.0, 36.0), (38.5, 43.0))
WING_SPAN_TIP = 38.5


def wing_outline(n=60):
    """The wing's silhouette in its own plane (u out from the root, v up): the leading edge, then the feathered
    trailing edge back to the root - primaries long at the tip, secondaries and tertials shorter toward the body,
    every feather tip a rounded scallop."""
    t = np.linspace(0, 1, n + 1)
    p0, p1, p2, p3 = (np.array(p, dtype=float) for p in WING_EDGE)
    L = ((1 - t) ** 3)[:, None] * p0 + (3 * (1 - t) ** 2 * t)[:, None] * p1 + (3 * (1 - t) * t ** 2)[:, None] * p2 + (t ** 3)[:, None] * p3
    # the feathers hang down from the edge, swinging back toward the tip; their length peaks at the primaries
    ang = np.radians(-95 + 30 * t)                            # from straight down near the body to swept back at the tip
    length = 9.0 + 14.5 * np.sin(np.pi * np.clip((t - 0.1) / 0.95, 0, 1)) ** 0.8 * (0.55 + 0.45 * t) - 4.0 * np.clip((t - 0.9) / 0.1, 0, 1)
    nf = 15
    scallop = 1.1 * np.abs(np.sin(nf * np.pi * t)) ** 0.5
    T = L + np.stack([np.cos(ang), np.sin(ang)], 1) * (length + scallop)[:, None]
    poly = np.concatenate([L, T[::-1]], axis=0)
    return poly, L, T


def wing_local(q):
    """The wing in its plane: the sheet, thick at the leading edge (the bones and coverts) thinning to the tips, with
    three rows of coverts stepping down like shingles and a quill ridge per feather - carved depth, not paint."""
    poly, L, T = wing_outline()
    u, v, w = q[:, 0], q[:, 1], q[:, 2]
    d2 = S.polygon2d(q[:, :2], poly)
    # where along the edge (t) and how far down the feathers (s, 0 at the edge .. 1 at the trailing scallops)
    # nearest edge point by u (the edge runs monotonically out)
    t = np.interp(u, L[:, 0], np.linspace(0, 1, len(L)))
    Lv = np.interp(u, L[:, 0], L[:, 1])
    Tv = np.interp(u, T[:, 0][np.argsort(T[:, 0])], T[:, 1][np.argsort(T[:, 0])])
    s = np.clip((Lv - v) / np.maximum(Lv - Tv, 1e-3), 0.0, 1.0)
    thick = 2.4 * (1 - s) ** 1.3 + 0.55
    # coverts: three overlapping rows (lesser, median, greater), each a step with a scalloped lower edge
    for row, (s0, h) in enumerate(((0.18, 0.55), (0.38, 0.45), (0.58, 0.35))):
        edge = s0 + 0.035 * np.abs(np.sin((15 + 4 * row) * np.pi * t))
        thick += h * (1.0 - np.clip((s - edge) / 0.03, 0, 1))
    # quill ridges along each feather, and the vanes' slight cupping between them
    quill = 0.22 * np.maximum(0.0, np.cos(15 * np.pi * t)) ** 6
    thick += quill * (0.3 + 0.7 * s)
    # the trailing third of each primary curls up a little (the wind in the feathers)
    w2 = w - 0.9 * np.clip((s - 0.72) / 0.28, 0, 1) ** 2 * (0.5 + 0.5 * t)
    dz = np.abs(w2) - thick / 2.0
    d = np.where((d2 > 0) & (dz > 0), np.sqrt(d2 * d2 + dz * dz), np.maximum(d2, dz))
    # the shoulder: a round muscle where the wing roots in the back
    d = S.smin(d, S.ellipsoid(q, (1.5, 1.0, 0.0), (3.4, 2.6, 2.2)), 1.2)
    return d


def wing_frame(side):
    """The wing plane's axes in body space (columns of R): u out to the side and a little back, v up, the plane
    reclined (tops back) and swept so the tips curve forward round the figure."""
    R = S.rot_y(-side * 28) @ S.rot_x(-14) @ S.rot_z(side * 8)
    if side < 0:
        R = R @ np.diag([-1.0, 1.0, 1.0])          # the right wing: its u runs to -x
    return R


def wing_field(P, side):
    """side +1: the left wing (+x). Points go to the wing's frame, then the sweep: the plane cups forward with u
    squared and twists so the tip presents its face to the front."""
    q = S.frame(P, (0.0, 0.0, 0.0), wing_frame(side))
    u, v = q[:, 0], q[:, 1]
    cup = 0.0045 * u * u - 0.004 * v * v * np.clip(u / 10.0, 0, 1)
    tw = np.radians(14.0) * np.clip(u / WING_SPAN_TIP, 0, 1)
    ca, sa = np.cos(tw), np.sin(tw)
    q2 = q.copy()
    q2[:, 2] = q[:, 2] + cup
    v2 = ca * q2[:, 1] - sa * q2[:, 2]
    w2 = sa * q2[:, 1] + ca * q2[:, 2]
    q2[:, 1], q2[:, 2] = v2, w2
    return wing_local(q2)


# ------------------------------------------------------------------ the halo ----------------------------------------
def halo_field(P):
    R = head_frame() @ S.rot_x(90)
    return S.torus(P, (0.0, 0.0, 0.0), 5.3, 0.33, R)


def halo_outer_field(P):
    R = head_frame() @ S.rot_x(90)
    return S.torus(P, (0.0, 0.0, 0.0), 7.1, 0.2, R)


# ------------------------------------------------------------------ parts and UVs -----------------------------------
PARTS = {
    # name: (field, lo, hi, h per LOD, parent, pivot in body space, material or a per-vertex function)
    "body": (body_field, (-14.0, -0.5, -9.5), (23.0, 44.5, 14.5), (0.42, 0.9), None, (0.0, 0.0, 0.0), body_material),
    "head": (head_part, (-6.0, -4.5, -6.0), (6.0, 5.5, 6.0), (0.18, 0.45), "body", HEAD_C, head_material),
    "wing_left": (lambda P: wing_field(P, 1), (-6.0, -8.0, -16.0), (44.0, 48.0, 12.0), (0.5, 1.0), "body", WING_ROOT_L,
                  "feather"),
    "wing_right": (lambda P: wing_field(P, -1), (-44.0, -8.0, -16.0), (6.0, 48.0, 12.0), (0.5, 1.0), "body",
                   (-WING_ROOT_L[0], WING_ROOT_L[1], WING_ROOT_L[2]), "feather"),
    "halo": (halo_field, (-6.0, -6.0, -6.0), (6.0, 6.0, 6.0), (0.28, 0.6), "body", HALO_C, "gold"),
    "halo_outer": (halo_outer_field, (-8.0, -8.0, -8.0), (8.0, 8.0, 8.0), (0.3, 0.7), "body", HALO_C, "gold"),
}


def uv_for(V, N, material):
    """Planar UVs into the material's atlas cell: a tri-planar pick per vertex (the plane its normal faces most), at
    0.06 texture repeats per px so the marble's veins run at the right scale across a 50 px figure."""
    cxr, cyr = REGIONS[material]
    ax = np.argmax(np.abs(N), axis=1)
    a = np.where(ax == 0, V[:, 2], V[:, 0]) * 0.055
    b = np.where(ax == 1, V[:, 2], V[:, 1]) * 0.055
    fu, fv = a - np.floor(a), b - np.floor(b)
    pad = 2.0 / ATLAS
    u = (cxr * CELL + pad * ATLAS + fu * (CELL - 4)) / ATLAS
    v = (cyr * CELL + pad * ATLAS + fv * (CELL - 4)) / ATLAS
    return np.stack([u, v], axis=1)


def build(lod=0, only=None, verbose=True):
    """Every part meshed: [(name, V, N, UV, Q, materials per vertex)] in body space (the part's pivot subtracted when
    written: see write)."""
    out = []
    for name, (field, lo, hi, hs, parent, pivot, mat) in PARTS.items():
        if only and name not in only:
            continue
        t0 = time.time()
        h = hs[min(lod, len(hs) - 1)]
        pv = np.array(pivot)
        V, N, Q = S.mesh(field, lo, hi, h)          # a child part's field and box are local to its pivot
        if name != "body":
            V = V + pv
        mats = mat(V) if callable(mat) else np.full(len(V), mat, dtype=object)
        UV = np.zeros((len(V), 2))
        for m in set(mats):
            sel = mats == m
            UV[sel] = uv_for(V[sel], N[sel], m)
        if verbose:
            print("  %-11s LOD%d h=%.2f: %6d verts %6d quads  %.1fs" % (name, lod, h, len(V), len(Q), time.time() - t0))
        out.append((name, V, N, UV, Q, mats))
    return out


def rig():
    """The sidecar tools/mesh_import.py reads: parents, pivots (DSL px) and rest rotations (DSL degrees)."""
    parts = {}
    for name, (field, lo, hi, hs, parent, pivot, mat) in PARTS.items():
        parts[name] = {"parent": parent, "pivot": list(pivot), "rot": [0.0, FACING if name == "body" else 0.0, 0.0]}
    return {"space": "dsl", "atlas": [ATLAS, ATLAS], "texture": TEXTURE, "parts": parts,
            "height_px": 81.0, "note": "written by tools/sculpt_angel.py"}


# ------------------------------------------------------------------ the atlas: three finishes --------------------------
def paint_atlas(out_dir=TEX_DIR):
    """The marble's finishes per region (Bernini's contrast of surfaces): polished skin, a softer cloth with the weave
    just visible, feathers with barb grooves, hair with the drill's dark eyes; gold for the halo and belt; the blade
    honed. Albedo + LabPBR _n (height-derived normals) + _s (smoothness / F0 / porosity) through tools/materials.py."""
    import materials
    from PIL import Image
    rng = np.random.RandomState(7)
    alb = np.zeros((ATLAS, ATLAS, 4), dtype=np.float32)
    height = np.zeros((ATLAS, ATLAS), dtype=np.float32)
    smooth = np.zeros((ATLAS, ATLAS), dtype=np.float32)
    f0 = np.zeros((ATLAS, ATLAS), dtype=np.float32)
    pore = np.zeros((ATLAS, ATLAS), dtype=np.float32)
    yy, xx = np.mgrid[0:CELL, 0:CELL].astype(np.float32)

    def veins(seed, scale, strength):
        r = np.random.RandomState(seed)
        acc = np.zeros((CELL, CELL), dtype=np.float32)
        for k in range(4):
            fx, fy = r.uniform(0.6, 1.6, 2) * scale
            ph = r.uniform(0, 6.3, 2)
            acc += np.sin(xx * fx * 2 * np.pi / CELL + ph[0] + 1.3 * np.sin(yy * fy * 2 * np.pi / CELL + ph[1])) / (k + 1)
        acc = np.abs(acc) / 2.0
        return (1.0 - np.clip(acc, 0, 1) ** 0.6) * strength

    def noise(seed, scale):
        r = np.random.RandomState(seed)
        small = r.uniform(-1, 1, (max(2, CELL // scale), max(2, CELL // scale)))
        big = np.asarray(Image.fromarray(((small + 1) * 127.5).astype(np.uint8)).resize((CELL, CELL), Image.BILINEAR),
                         dtype=np.float32) / 127.5 - 1
        return big

    specs = {
        "skin": dict(base=(0.905, 0.875, 0.835), vein=0.05, grain=0.012, mat="stone", sm=0.70, hgt=0.3),
        "cloth": dict(base=(0.86, 0.835, 0.79), vein=0.04, grain=0.03, mat="stone", sm=0.45, hgt=0.6),
        "feather": dict(base=(0.915, 0.9, 0.865), vein=0.03, grain=0.035, mat="stone", sm=0.36, hgt=1.0),
        "hair": dict(base=(0.84, 0.81, 0.765), vein=0.03, grain=0.04, mat="stone", sm=0.42, hgt=0.8),
        "gold": dict(base=(0.86, 0.68, 0.26), vein=0.0, grain=0.02, mat="gold", sm=0.72, hgt=0.2),
        "blade": dict(base=(0.88, 0.86, 0.83), vein=0.02, grain=0.01, mat="stone", sm=0.78, hgt=0.2),
        "stone": dict(base=(0.80, 0.78, 0.74), vein=0.05, grain=0.04, mat="stone", sm=0.4, hgt=0.7),
    }
    for name, (cxr, cyr) in REGIONS.items():
        sp = specs[name]
        x0, y0 = cxr * CELL, cyr * CELL
        base = np.array(sp["base"], dtype=np.float32)
        v = veins(hash(name) % 1000, 2.5, sp["vein"])
        g = noise(hash(name) % 977 + 3, 6) * sp["grain"] + noise(hash(name) % 977 + 9, 2) * sp["grain"] * 0.5
        shade = 1.0 - v + g
        h = (1.0 - v * 2.0) * 0.5 + g * 2.0
        if name == "feather":        # barb grooves: fine lines running across the vane
            lines = 0.5 * (np.sin(yy * 2 * np.pi / 4.0 + 0.6 * np.sin(xx / 9.0)) > 0.3)
            shade = shade - 0.04 * lines
            h = h - 0.25 * lines
        if name == "cloth":          # the weave: the faintest diagonal
            w = 0.5 * (np.sin((xx + yy) * 2 * np.pi / 3.0) > 0.0)
            shade = shade - 0.012 * w
            h = h - 0.06 * w
        if name == "hair":           # the drill: dark eyes
            r = np.random.RandomState(5)
            for _ in range(60):
                px, py = r.randint(4, CELL - 4, 2)
                rr = (xx - px) ** 2 + (yy - py) ** 2
                shade = shade - 0.35 * np.exp(-rr / 3.0)
                h = h - 0.8 * np.exp(-rr / 3.0)
        col = np.clip(base[None, None, :] * shade[..., None], 0, 1)
        alb[y0:y0 + CELL, x0:x0 + CELL, :3] = col
        alb[y0:y0 + CELL, x0:x0 + CELL, 3] = 1.0
        height[y0:y0 + CELL, x0:x0 + CELL] = np.clip(h * sp["hgt"] + 0.5, 0, 1)
        m = materials.MATERIALS[sp["mat"]]
        smooth[y0:y0 + CELL, x0:x0 + CELL] = sp["sm"] * 255
        f0[y0:y0 + CELL, x0:x0 + CELL] = m["f0"]
        pore[y0:y0 + CELL, x0:x0 + CELL] = m["pore"]
    nx, ny, nz = materials.height_normals(height, 2.0, wrap=False)
    n = materials.encode_normal(nx, ny, np.ones_like(height), height)
    s = materials.encode_specular(smooth, f0, pore, 255)
    probs = materials.check_labpbr(n, s)
    if probs:
        raise SystemExit("atlas LabPBR: " + "; ".join(probs))
    os.makedirs(out_dir, exist_ok=True)
    Image.fromarray((alb * 255).astype(np.uint8), "RGBA").save(os.path.join(out_dir, TEXTURE + ".png"))
    Image.fromarray(n, "RGBA").save(os.path.join(out_dir, TEXTURE + "_n.png"))
    Image.fromarray(s, "RGBA").save(os.path.join(out_dir, TEXTURE + "_s.png"))
    return alb


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", default=os.path.join(ROOT, "local", "sculpt"))
    ap.add_argument("--lod", default="0,1")
    ap.add_argument("--no-atlas", action="store_true")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    for lod in (int(s) for s in a.lod.split(",")):
        groups = build(lod)
        name = "white_angel_statue" + (".lod%d" % lod if lod else "")
        path = os.path.join(a.out, name + ".obj")
        S.write_obj(path, [(n, V, N, UV, Q) for n, V, N, UV, Q, m in groups],
                    "White Angel statue v3, tools/sculpt_angel.py LOD%d (DSL space: px, y up, faces -z)" % lod)
        tot = sum(len(g[4]) for g in groups)
        print("wrote %s: %d quads" % (path, tot))
    with open(os.path.join(a.out, "white_angel_statue.rig.json"), "w") as fh:
        json.dump(rig(), fh, indent=1)
    if not a.no_atlas:
        paint_atlas()
        print("atlas:", os.path.join(TEX_DIR, TEXTURE + ".png"))


if __name__ == "__main__":
    main()
