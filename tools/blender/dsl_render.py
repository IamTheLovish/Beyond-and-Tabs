"""Blender headless critic-sheet renderer for Beyond and Tabs box-model units.

    blender --background --python-exit-code 1 --python tools/blender/dsl_render.py -- <frames.json> <out_dir> [options]

Draws ONLY what export_frames.py wrote (the repo's own bone math, posed through game_preview.faces_of): one mesh
object per cube (or per bone / per unit with --group), 8 corners per cube updated each frame, flat colours from the
palette with the team parts in the owner colour, Minecraft's two entity lights baked per face (game_preview's L0 / L1
formula) and the glow added on emissive cubes as game_preview does (colour * light + 0.8 * glow * colour).
Workbench, FLAT light (so the baked light is the light), cavity on, object outline on, ground shadow on.

Space: the JSON is DSL px (y up, the unit faces -Z, +x = the unit's left). Blender gets (x, z, y) / 16 - one Blender
unit per block. That map is a mirror on purpose: the DSL is a left-handed frame, and the mirror makes a Blender camera
built from game_preview's view axes see exactly what game_preview draws (screen x = right . p, screen y = up . p).

Options (after the two positional arguments):
  --sheets views,turnaround,clips   which sheets (default: all three)
  --clips walk,attack               clips for frame sheets (default: every clip but rest/idle)
  --combine                         put every clip of --clips on ONE frame sheet (default: one sheet per clip)
  --rows profile:42,rts:42,rts:21   frame-sheet rows as view:px_per_block (views from the JSON: front quarter
                                    quarter_l side profile back rts topdown turn_000..turn_315)
  --cols 12                         frames per strip before a row wraps
  --group cube|bone|unit            object granularity (the outline is drawn between objects; default cube)
  --colours palette|game            raw palette or d2g's game-value palette (default palette)
  --deadzone 12                     camera follows the body only once it is this many px from the camera target
  --aa 8                            Workbench render AA samples (OFF FXAA 5 8 11 16 32)
  --light -0.2,0.3,-0.93            Workbench shadow light (Blender space, the direction the light travels)
  --shadow_shift 0.0 --shadow_focus 1.0   Workbench shadow terminator (these keep up-facing ground at full value)
  --no-shadow --no-cavity --no-outline
  --check CLIP:TICK:VIEW            one transparent still (no ground / outline / cavity) for the pose check, 42 px/block
  --tag NAME                        file-name prefix (default: the unit key)
"""
import ctypes
import json
import math
import os
import sys
import time

import bpy
import numpy as np

T0 = time.time()

# ----------------------------------------------------------------------------- arguments
argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
if len(argv) < 2:
    raise SystemExit("usage: blender --background --python dsl_render.py -- frames.json out_dir [options]")
FRAMES, OUT = argv[0], argv[1]
OPT = {"sheets": "views,turnaround,clips", "clips": "", "rows": "profile:42,rts:42,rts:21", "cols": "12",
       "group": "cube", "colours": "palette", "deadzone": "12", "aa": "8", "check": "", "tag": "", "light": "-0.2,0.3,-0.93", "shadow_shift": "0.0", "shadow_focus": "1.0"}
FLAGS = set()
i = 2
while i < len(argv):
    k = argv[i].lstrip("-")
    if k in ("no-shadow", "no-cavity", "no-outline", "combine"):
        FLAGS.add(k)
        i += 1
    else:
        OPT[k] = argv[i + 1]
        i += 2

D = json.load(open(FRAMES))
TAG = OPT["tag"] or D["unit"]
os.makedirs(os.path.join(OUT, "cells"), exist_ok=True)
PX = float(D.get("px_per_block", 16.0))

# side view tilted a little so the ground grid reads; everything else is game_preview's own
VIEWS = dict(D["views"])


def view_basis(name):
    return VIEWS[name]


# ----------------------------------------------------------------------------- maths
MAP = np.array([[1, 0, 0], [0, 0, 1], [0, 1, 0]], dtype=float) / PX        # DSL px -> Blender blocks (a mirror)


def to_b(p):
    return MAP @ np.asarray(p, dtype=float)


def srgb_to_lin(c):
    c = np.clip(c, 0.0, 1.0)
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)


# faces of a box by corner bits (x 4, MC-y 2, z 1), each quad in a loop order
FACES = np.array([[0, 1, 3, 2], [4, 6, 7, 5], [0, 4, 5, 1], [2, 3, 7, 6], [0, 2, 6, 4], [1, 5, 7, 3]])
L0 = np.array(D["lights"]["L0"])
L1 = np.array(D["lights"]["L1"])


def face_light(C):
    """C (N, 8, 3) DSL corners -> (N, 6) game_preview light per face (Minecraft's two entity lights)."""
    q = C[:, FACES]                                    # N, 6, 4, 3
    n = np.cross(q[:, :, 1] - q[:, :, 0], q[:, :, 3] - q[:, :, 0])
    out = q.mean(axis=2) - C.mean(axis=1)[:, None, :]
    s = np.sign(np.einsum("nfk,nfk->nf", n, out))
    s[s == 0] = 1
    n = n * s[..., None]
    ln = np.linalg.norm(n, axis=-1, keepdims=True)
    n = n / np.where(ln < 1e-12, 1, ln)
    return np.minimum(1.0, 0.6 * (np.maximum(0, n @ L0) + np.maximum(0, n @ L1)) + 0.4)


# ----------------------------------------------------------------------------- the cubes' colours
CUBES = D["cubes"]
PAL = D["game_palette"] if OPT["colours"] == "game" else D["palette"]
OWNER = np.array(D["owner_game"] if OPT["colours"] == "game" else D["owner"])
BASE = np.zeros((len(CUBES), 3))
EMIS = np.zeros(len(CUBES))
for c in CUBES:
    if c["team"]:
        BASE[c["id"]] = OWNER * (1.0 if c["team_shade"] == 0 else 190.0 / 255.0)
    else:
        BASE[c["id"]] = PAL[c["color"]]
    EMIS[c["id"]] = 1.0 if c["emissive"] else 0.0

REST = D["clips"]["rest"]["frames"][0]
PRESENT = sorted(int(k) for k in REST["cubes"])          # cubes the rest pose draws (upgrades are absent)


def frame_corners(fr):
    """(N_all, 8, 3) DSL corners; a cube the frame does not draw collapses onto its rest corner 0 (no area)."""
    C = np.zeros((len(CUBES), 8, 3))
    for k in PRESENT:
        cs = fr["cubes"].get(str(k))
        C[k] = cs if cs is not None else np.array(REST["cubes"][str(k)][0])[None, :].repeat(8, axis=0)
    return C


def frame_colours(fr, C):
    lt = face_light(C)                                   # N, 6
    g = EMIS.copy()
    for k, v in fr.get("glow", {}).items():
        g[int(k)] = float(v)
    col = BASE[:, None, :] * lt[..., None] + 0.8 * g[:, None, None] * BASE[:, None, :]
    return np.clip(col, 0, 1)                            # sRGB, N, 6, 3


# ----------------------------------------------------------------------------- scene
bpy.ops.wm.read_factory_settings(use_empty=True)
SC = bpy.context.scene
SC.render.engine = "BLENDER_WORKBENCH"
SH = SC.display.shading


def _set(obj, attr, val):
    try:
        setattr(obj, attr, val)
    except Exception as e:                               # noqa: BLE001
        print("WARN cannot set %s.%s = %r: %s" % (type(obj).__name__, attr, val, e))


_set(SH, "light", "FLAT")
_set(SH, "color_type", "VERTEX")
_set(SH, "show_cavity", "no-cavity" not in FLAGS)
_set(SH, "cavity_type", "BOTH")
_set(SH, "cavity_ridge_factor", 0.6)
_set(SH, "cavity_valley_factor", 1.0)
_set(SH, "curvature_ridge_factor", 0.6)
_set(SH, "curvature_valley_factor", 0.8)
_set(SH, "show_object_outline", "no-outline" not in FLAGS)
_set(SH, "object_outline_color", (0.06, 0.05, 0.07))
_set(SH, "show_shadows", "no-shadow" not in FLAGS)
_set(SH, "shadow_intensity", 0.35)
_set(SH, "show_specular_highlight", False)
_set(SH, "show_backface_culling", False)
_set(SC.display, "light_direction", tuple(float(v) for v in OPT["light"].split(",")))   # Workbench shadow light
_set(SC.display, "shadow_shift", float(OPT["shadow_shift"]))
_set(SC.display, "shadow_focus", float(OPT["shadow_focus"]))
_set(SC.display, "render_aa", OPT["aa"])
_set(SC.view_settings, "view_transform", "Standard")
_set(SC.view_settings, "look", "None")
SC.render.resolution_percentage = 100
SC.render.image_settings.file_format = "PNG"
world = bpy.data.worlds.new("bg")
world.color = tuple(srgb_to_lin(np.array([0.86, 0.86, 0.85])))
SC.world = world


def colour_attr(mesh):
    a = mesh.color_attributes.new("col", "FLOAT_COLOR", "CORNER")
    for setter in (lambda: setattr(mesh.color_attributes, "active_color", a),
                   lambda: setattr(mesh.color_attributes, "render_color_index", 0),
                   lambda: setattr(mesh.attributes, "default_color_name", "col")):
        try:
            setter()
        except Exception:                                # noqa: BLE001
            pass
    return a


# ---- the unit: group meshes
def group_key(c):
    return {"cube": str(c["id"]), "bone": c["bone"], "unit": "unit"}[OPT["group"]]


GROUPS = {}
for k in PRESENT:
    GROUPS.setdefault(group_key(CUBES[k]), []).append(k)

C_REST = frame_corners(REST)
UNIT = []          # (object, mesh, attr, cube ids array, per-cube face flip flags)
for gname, ids in GROUPS.items():
    ids = np.array(ids)
    verts = np.concatenate([np.array([to_b(p) for p in C_REST[k]]) for k in ids])
    faces = []
    for j, k in enumerate(ids):
        Cb = verts[j * 8:(j + 1) * 8]
        q = Cb[FACES[0]]
        n = np.cross(q[1] - q[0], q[3] - q[0])
        outward = (n @ (q.mean(axis=0) - Cb.mean(axis=0))) >= 0
        for f in FACES:
            ff = [int(v) + j * 8 for v in f]
            faces.append(ff if outward else ff[::-1])
    me = bpy.data.meshes.new("m_" + gname)
    me.from_pydata([tuple(v) for v in verts], [], faces)
    me.update()
    at = colour_attr(me)
    ob = bpy.data.objects.new("u_" + gname, me)
    SC.collection.objects.link(ob)
    # loop -> (cube j, face f): from_pydata keeps the polygon order; a reversed quad keeps its 4 loops on one face
    UNIT.append((ob, me, at, ids))


def set_frame(fr):
    C = frame_corners(fr)
    col = frame_colours(fr, C)
    lin = srgb_to_lin(col)
    for ob, me, at, ids in UNIT:
        V = np.einsum("ij,nkj->nki", MAP, C[ids]).reshape(-1)
        me.vertices.foreach_set("co", V.astype(np.float32))
        rgba = np.ones((len(ids), 6, 4, 4), dtype=np.float32)
        rgba[..., :3] = lin[ids][:, :, None, :]
        at.data.foreach_set("color", rgba.reshape(-1))
        me.update()


# ---- the ground: one mesh, light grey with a 1-block grid (one object, so the outline does not trace the lines)
GROUND = {}
G_COL = np.array([0.80, 0.80, 0.78])
L_COL = np.array([0.64, 0.64, 0.62])


def ground_for(ppb, x0, x1, z0, z1, line_px=1.1):
    """A ground mesh in Blender blocks over [x0, x1] x [y0, y1] (Blender x / y), lines line_px wide at ppb."""
    key = ppb
    if key in GROUND:
        return GROUND[key]
    w = line_px / ppb / 2.0
    xs = sorted(set([x0, x1] + [v for i in range(int(math.floor(x0)), int(math.ceil(x1)) + 1) for v in (i - w, i + w)
                               if x0 <= v <= x1]))
    ys = sorted(set([z0, z1] + [v for i in range(int(math.floor(z0)), int(math.ceil(z1)) + 1) for v in (i - w, i + w)
                               if z0 <= v <= z1]))

    def is_line(a, b):
        mid = (a + b) / 2
        return abs(mid - round(mid)) < w + 1e-9
    verts, faces, line = [], [], []
    idx = {}

    def vid(i, j):
        if (i, j) not in idx:
            idx[(i, j)] = len(verts)
            verts.append((xs[i], ys[j], 0.0))
        return idx[(i, j)]
    for i in range(len(xs) - 1):
        lx = is_line(xs[i], xs[i + 1])
        for j in range(len(ys) - 1):
            faces.append((vid(i, j), vid(i + 1, j), vid(i + 1, j + 1), vid(i, j + 1)))
            line.append(lx or is_line(ys[j], ys[j + 1]))
    me = bpy.data.meshes.new("ground_%d" % ppb)
    me.from_pydata(verts, [], faces)
    me.update()
    at = colour_attr(me)
    lin_g, lin_l = srgb_to_lin(G_COL), srgb_to_lin(L_COL)
    rgba = np.ones((len(faces), 4, 4), dtype=np.float32)
    for f, ln in enumerate(line):
        rgba[f, :, :3] = lin_l if ln else lin_g
    at.data.foreach_set("color", rgba.reshape(-1))
    ob = bpy.data.objects.new("ground_%d" % ppb, me)
    SC.collection.objects.link(ob)
    GROUND[key] = ob
    return ob


def show_ground(ppb):
    for k, ob in GROUND.items():
        ob.hide_render = (k != ppb) or ppb is None


# ---- camera
cam_data = bpy.data.cameras.new("cam")
cam_data.type = "ORTHO"
cam_data.clip_start = 0.05
cam_data.clip_end = 400.0
CAM = bpy.data.objects.new("cam", cam_data)
SC.collection.objects.link(CAM)
SC.camera = CAM
CAM_DIST = 60.0 * PX          # px


def place_camera(view, centre_px, w_px, h_px, ppb):
    """Ortho camera on game_preview's view axes, looking at centre_px (DSL); image w_px x h_px at ppb px/block."""
    v = view_basis(view)
    r, u, f = (np.array(v[k]) for k in ("right", "up", "forward"))
    loc = np.asarray(centre_px) - f * CAM_DIST
    M = np.eye(4)
    M[:3, 0] = MAP @ r * PX
    M[:3, 1] = MAP @ u * PX
    M[:3, 2] = -(MAP @ f) * PX
    M[:3, 3] = to_b(loc)
    from mathutils import Matrix
    CAM.matrix_world = Matrix([list(row) for row in M])
    SC.render.resolution_x = int(w_px)
    SC.render.resolution_y = int(h_px)
    cam_data.ortho_scale = max(w_px, h_px) / float(ppb)


def project(view, P, centre):
    v = view_basis(view)
    r, u = np.array(v["right"]), np.array(v["up"])
    d = P - centre
    return d @ r, d @ u


def render_to(path, transparent=False):
    SC.render.film_transparent = transparent
    SC.render.image_settings.color_mode = "RGBA" if transparent else "RGB"
    SC.render.filepath = path
    bpy.ops.render.render(write_still=True)
    return path


# ----------------------------------------------------------------------------- images, bitmap font, sheets
def load_png(path):
    im = bpy.data.images.load(path, check_existing=False)
    w, h = im.size
    buf = np.empty(w * h * 4, dtype=np.float32)
    im.pixels.foreach_get(buf)
    bpy.data.images.remove(im)
    return buf.reshape(h, w, 4)[::-1].copy()


def save_png(arr, path):
    h, w = arr.shape[:2]
    im = bpy.data.images.new("sheet", w, h, alpha=False)
    a = np.ones((h, w, 4), dtype=np.float32)
    a[..., :arr.shape[2]] = arr[..., :4] if arr.shape[2] >= 4 else arr
    a[..., 3] = 1.0
    im.pixels.foreach_set(a[::-1].reshape(-1))
    im.filepath_raw = path
    im.file_format = "PNG"
    im.save()
    bpy.data.images.remove(im)


GLYPH = {
    "0": "0E 11 13 15 19 11 0E", "1": "04 0C 04 04 04 04 0E", "2": "0E 11 01 02 04 08 1F", "3": "1F 02 04 02 01 11 0E",
    "4": "02 06 0A 12 1F 02 02", "5": "1F 10 1E 01 01 11 0E", "6": "06 08 10 1E 11 11 0E", "7": "1F 01 02 04 08 08 08",
    "8": "0E 11 11 0E 11 11 0E", "9": "0E 11 11 0F 01 02 0C", "A": "0E 11 11 1F 11 11 11", "B": "1E 11 11 1E 11 11 1E",
    "C": "0E 11 10 10 10 11 0E", "D": "1C 12 11 11 11 12 1C", "E": "1F 10 10 1E 10 10 1F", "F": "1F 10 10 1E 10 10 10",
    "G": "0E 11 10 17 11 11 0F", "H": "11 11 11 1F 11 11 11", "I": "0E 04 04 04 04 04 0E", "J": "07 02 02 02 02 12 0C",
    "K": "11 12 14 18 14 12 11", "L": "10 10 10 10 10 10 1F", "M": "11 1B 15 15 11 11 11", "N": "11 11 19 15 13 11 11",
    "O": "0E 11 11 11 11 11 0E", "P": "1E 11 11 1E 10 10 10", "Q": "0E 11 11 11 15 12 0D", "R": "1E 11 11 1E 14 12 11",
    "S": "0F 10 10 0E 01 01 1E", "T": "1F 04 04 04 04 04 04", "U": "11 11 11 11 11 11 0E", "V": "11 11 11 11 11 0A 04",
    "W": "11 11 11 15 15 15 0A", "X": "11 11 0A 04 0A 11 11", "Y": "11 11 11 0A 04 04 04", "Z": "1F 01 02 04 08 10 1F",
    " ": "00 00 00 00 00 00 00", "-": "00 00 00 1F 00 00 00", "_": "00 00 00 00 00 00 1F", ".": "00 00 00 00 00 0C 0C",
    ":": "00 0C 0C 00 0C 0C 00", "/": "01 01 02 04 08 10 10", "(": "02 04 08 08 08 04 02", ")": "08 04 02 02 02 04 08",
    "=": "00 00 1F 00 1F 00 00", "+": "00 04 04 1F 04 04 00", "%": "18 19 02 04 08 13 03", ",": "00 00 00 00 0C 04 08",
    ">": "08 04 02 01 02 04 08", "<": "02 04 08 10 08 04 02", "#": "0A 0A 1F 0A 1F 0A 0A", "|": "04 04 04 04 04 04 04",
}


def text_w(s, scale):
    return len(s) * 6 * scale


def draw_text(img, x, y, s, scale=2, col=(0.12, 0.12, 0.14)):
    h, w = img.shape[:2]
    for ch in s.upper():
        rows = GLYPH.get(ch, GLYPH["#"]).split()
        for ry, hexrow in enumerate(rows):
            bits = int(hexrow, 16)
            for rx in range(5):
                if bits & (1 << (4 - rx)):
                    y0, x0 = y + ry * scale, x + rx * scale
                    if 0 <= y0 and y0 + scale <= h and 0 <= x0 and x0 + scale <= w:
                        img[y0:y0 + scale, x0:x0 + scale, :3] = col
        x += 6 * scale


SHEET_BG = (0.96, 0.955, 0.94)


def compose(title, sections, path):
    """sections: [(row title, [(image (h, w, 3|4), cell label)])] -> one sheet PNG, labels burned in."""
    gap, pad, lab_h, head_h, title_h = 6, 10, 0, 22, 30
    rows = []
    for rtitle, cells in sections:
        ch = max(c[0].shape[0] for c in cells)
        rw = sum(c[0].shape[1] for c in cells) + gap * (len(cells) - 1)
        rows.append((rtitle, cells, ch, rw))
    W = max([r[3] for r in rows] + [text_w(title, 2)]) + 2 * pad
    H = title_h + sum(head_h + r[2] + 18 + gap for r in rows) + pad
    S = np.ones((H, W, 3), dtype=np.float32) * np.array(SHEET_BG, dtype=np.float32)
    draw_text(S, pad, 8, title, 2, (0.05, 0.05, 0.06))
    y = title_h
    for rtitle, cells, ch, _rw in rows:
        draw_text(S, pad, y + 5, rtitle, 2, (0.25, 0.20, 0.30))
        y += head_h
        x = pad
        sc = 2 if all(text_w(lab, 2) <= im.shape[1] for im, lab in cells) else 1     # one label size per row
        for im, lab in cells:
            h, w = im.shape[:2]
            S[y:y + h, x:x + w] = im[..., :3]
            draw_text(S, x + 1, y + h + 3, lab, sc)
            x += w + gap
        y += ch + 18 + gap
    save_png(S, path)
    return path


def silhouette(cell_rgba):
    a = cell_rgba[..., 3:4]
    out = np.ones(cell_rgba.shape[:2] + (3,), dtype=np.float32) * 0.93
    return out * (1 - a) + np.array([0.07, 0.07, 0.09]) * a


# ----------------------------------------------------------------------------- framing, follow camera
REST_LO, REST_HI = np.array(D["rest_bbox"]["lo"]), np.array(D["rest_bbox"]["hi"])
EYE_Y = float((REST_LO[1] + REST_HI[1]) / 2)


def follow(frames, deadzone):
    """Camera targets (DSL px): fixed until the body is > deadzone px (horizontal) from the target, then dragged."""
    out = []
    tgt = None
    for fr in frames:
        a = np.array(fr["anchor"], dtype=float)
        if tgt is None:
            tgt = np.array([a[0], EYE_Y, a[2]])
        d = np.array([a[0] - tgt[0], 0.0, a[2] - tgt[2]])
        n = np.linalg.norm(d)
        if n > deadzone:
            tgt = tgt + d * (1 - deadzone / n)
        out.append(tgt.copy())
    return out


def framing(view, frames, targets, ppb, margin_px=6.0):
    """One fixed framing for a row: (offset of the image centre from the target in screen px DSL, w_px, h_px)."""
    xs, ys = [], []
    for fr, t in zip(frames, targets):
        P = np.array([p for cs in fr["cubes"].values() for p in cs])
        sx, sy = project(view, P, t)
        xs += [sx.min(), sx.max()]
        ys += [sy.min(), sy.max()]
    # leave room for a little ground under the feet
    x0, x1, y0, y1 = min(xs) - margin_px, max(xs) + margin_px, min(ys) - margin_px * 1.5, max(ys) + margin_px
    w = int(math.ceil((x1 - x0) / PX * ppb / 2.0)) * 2
    h = int(math.ceil((y1 - y0) / PX * ppb / 2.0)) * 2
    return ((x0 + x1) / 2, (y0 + y1) / 2), max(w, 16), max(h, 16)


def centre_of(view, target, off):
    v = view_basis(view)
    return np.asarray(target) + off[0] * np.array(v["right"]) + off[1] * np.array(v["up"])


ALL_ANCHORS = np.array([fr["anchor"] for c in D["clips"].values() for fr in c["frames"]])


def ensure_ground(ppb, frames_list=None):
    anchors = ALL_ANCHORS          # one ground per scale, covering every clip's travel
    pad = 26.0 * PX
    x0, x1 = anchors[:, 0].min() - pad, anchors[:, 0].max() + pad
    z0, z1 = anchors[:, 2].min() - pad, anchors[:, 2].max() + pad
    b0, b1 = to_b([x0, 0, z0]), to_b([x1, 0, z1])
    return ground_for(ppb, min(b0[0], b1[0]), max(b0[0], b1[0]), min(b0[1], b1[1]), max(b0[1], b1[1]))


TIMES = {}


def peak_ram_mb():
    try:
        class PMC(ctypes.Structure):
            _fields_ = [("cb", ctypes.c_ulong), ("PageFaultCount", ctypes.c_ulong),
                        ("PeakWorkingSetSize", ctypes.c_size_t), ("WorkingSetSize", ctypes.c_size_t),
                        ("QuotaPeakPagedPoolUsage", ctypes.c_size_t), ("QuotaPagedPoolUsage", ctypes.c_size_t),
                        ("QuotaPeakNonPagedPoolUsage", ctypes.c_size_t), ("QuotaNonPagedPoolUsage", ctypes.c_size_t),
                        ("PagefileUsage", ctypes.c_size_t), ("PeakPagefileUsage", ctypes.c_size_t)]
        c = PMC()
        c.cb = ctypes.sizeof(PMC)
        k32 = ctypes.windll.kernel32
        k32.GetCurrentProcess.restype = ctypes.c_void_p
        fn = k32.K32GetProcessMemoryInfo
        fn.argtypes = [ctypes.c_void_p, ctypes.POINTER(PMC), ctypes.c_ulong]
        if not fn(k32.GetCurrentProcess(), ctypes.byref(c), c.cb):
            return None
        return round(c.PeakWorkingSetSize / 2 ** 20, 1)
    except Exception:                                    # noqa: BLE001
        return None


def cell_path(name):
    return os.path.join(OUT, "cells", "%s_%s.png" % (TAG, name))


def idle_frame():
    if "idle" in D["clips"] and D["clips"]["idle"]["frames"]:
        return D["clips"]["idle"]["frames"][0], "idle t0"
    return REST, "rest"


def still_cells(view, ppb, fr, name, with_sil=True):
    """One posed still at a fixed framing on the ground; returns (colour cell, silhouette cell or None)."""
    t = np.array([fr["anchor"][0], EYE_Y, fr["anchor"][2]])
    off, w, h = framing(view, [fr], [t], ppb, margin_px=10.0)
    c = centre_of(view, t, off)
    ensure_ground(ppb, [[fr]])
    show_ground(ppb)
    set_frame(fr)
    place_camera(view, c, w, h, ppb)
    col = load_png(render_to(cell_path(name)))
    sil = None
    if with_sil:
        show_ground(None)
        SH.show_object_outline = False
        sil = silhouette(load_png(render_to(cell_path(name + "_sil"), transparent=True)))
        SH.show_object_outline = "no-outline" not in FLAGS
    return col, sil


def head(extra):
    return "%s (%s, %s) - %s" % (D["name"], D["faction"], D["role"], extra)


# ----------------------------------------------------------------------------- sheets
def sheet_views():
    t = time.time()
    fr, lab = idle_frame()
    specs = [("rts", 42, "rts close 42px/block"), ("rts", 21, "rts tactical 21px/block"),
             ("quarter", 42, "quarter 42px/block")]
    cols, sils = [], []
    for view, ppb, label in specs:
        col, sil = still_cells(view, ppb, fr, "view_%s_%d" % (view, ppb))
        cols.append((col, label))
        sils.append((sil, "silhouette"))
    p = compose(head("views, %s" % lab), [("colour", cols), ("silhouette", sils)],
                os.path.join(OUT, "%s_views.png" % TAG))
    TIMES["views"] = round(time.time() - t, 2)
    print("SHEET %s %.2f s" % (p, TIMES["views"]))


def sheet_turnaround():
    t = time.time()
    fr, lab = idle_frame()
    names = sorted([v for v in VIEWS if v.startswith("turn_")])
    # one framing for all 8 views (the union), so the turnaround keeps one scale and one size
    tg = np.array([fr["anchor"][0], EYE_Y, fr["anchor"][2]])
    boxes = [framing(v, [fr], [tg], 42, margin_px=8.0) for v in names]
    w, h = max(b[1] for b in boxes), max(b[2] for b in boxes)
    ensure_ground(42, [[fr]])
    set_frame(fr)
    cols, sils = [], []
    for v, b in zip(names, boxes):
        c = centre_of(v, tg, b[0])
        show_ground(42)
        place_camera(v, c, w, h, 42)
        cols.append((load_png(render_to(cell_path(v))), "yaw %d" % (VIEWS[v]["yaw"] % 360)))
        show_ground(None)
        SH.show_object_outline = False
        sils.append((silhouette(load_png(render_to(cell_path(v + "_sil"), transparent=True))), "silhouette"))
        SH.show_object_outline = "no-outline" not in FLAGS
    p = compose(head("turnaround, %s, tilt 18, 42px/block" % lab), [("colour", cols), ("silhouette", sils)],
                os.path.join(OUT, "%s_turnaround.png" % TAG))
    TIMES["turnaround"] = round(time.time() - t, 2)
    print("SHEET %s %.2f s" % (p, TIMES["turnaround"]))


def sheet_clips(clip_names, out_name):
    t = time.time()
    rows = [(r.split(":")[0], int(r.split(":")[1])) for r in OPT["rows"].split(",") if r]
    cols_max = int(OPT["cols"])
    dz = float(OPT["deadzone"])
    sections = []
    for cn in clip_names:
        clip = D["clips"][cn]
        frames = clip["frames"]
        targets = follow(frames, dz)
        plan = []
        for view, ppb in rows:
            off, w, h = framing(view, frames, targets, ppb)
            ensure_ground(ppb, [frames])
            plan.append((view, ppb, off, w, h, []))
        for fi, fr in enumerate(frames):
            set_frame(fr)
            for view, ppb, off, w, h, cells in plan:
                show_ground(ppb)
                place_camera(view, centre_of(view, targets[fi], off), w, h, ppb)
                img = load_png(render_to(cell_path("%s_%s_%d_t%03d" % (cn, view, ppb, fr["t"]))))
                lab = "%s t%d" % (cn, fr["t"]) if not fr.get("label") else fr["label"]
                cells.append((img, lab))
        for view, ppb, off, w, h, cells in plan:
            for s in range(0, len(cells), cols_max):
                part = "" if len(cells) <= cols_max else "  (%d/%d)" % (s // cols_max + 1,
                                                                      (len(cells) + cols_max - 1) // cols_max)
                sections.append(("%s | %s %dpx/block | %d ticks%s%s" % (
                    cn, view, ppb, clip["ticks"], ", loop" if clip["loop"] else "", part), cells[s:s + cols_max]))
    p = compose(head("frames every %d ticks, camera follows past %d px" % (D["step"], int(dz))), sections,
                os.path.join(OUT, "%s_%s.png" % (TAG, out_name)))
    TIMES[out_name] = round(time.time() - t, 2)
    print("SHEET %s %.2f s (%d cells)" % (p, TIMES[out_name], sum(len(c) for _, c in sections)))


def check_still(spec):
    """CLIP:TICK:VIEW -> one transparent still, no ground / outline / cavity, for the side-by-side pose check."""
    cn, tick, view = spec.split(":")
    fr = [f for f in D["clips"][cn]["frames"] if f["t"] == int(tick)][0]
    SH.show_object_outline = False
    SH.show_cavity = False
    SH.show_shadows = False
    show_ground(None)
    t = np.array([fr["anchor"][0], EYE_Y, fr["anchor"][2]])
    off, w, h = framing(view, [fr], [t], 42, margin_px=4.0)
    set_frame(fr)
    place_camera(view, centre_of(view, t, off), w, h, 42)
    p = render_to(os.path.join(OUT, "%s_check_%s_t%s_%s.png" % (TAG, cn, tick, view)), transparent=True)
    print("CHECK %s" % p)


if OPT["check"]:
    check_still(OPT["check"])
else:
    want = [s for s in OPT["sheets"].split(",") if s]
    if "views" in want:
        sheet_views()
    if "turnaround" in want:
        sheet_turnaround()
    if "clips" in want:
        names = [c for c in OPT["clips"].split(",") if c] or [c for c in D["clips"] if c not in ("rest", "idle")]
        names = [c for c in names if c in D["clips"]]
        if "combine" in FLAGS:
            sheet_clips(names, "frames_" + "_".join(names))
        else:
            for c in names:
                sheet_clips([c], "frames_" + c)
TIMES["total_s"] = round(time.time() - T0, 2)
TIMES["peak_ram_mb"] = peak_ram_mb()
with open(os.path.join(OUT, "%s_timings.json" % TAG), "a") as fh:
    fh.write(json.dumps(dict(TIMES, argv=argv)) + "\n")
print("DONE %s" % json.dumps(TIMES))
