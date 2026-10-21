#!/usr/bin/env python3
"""Mesh bodies, step 2: a REAL SCULPT into the game - an OBJ (Blender, ZBrush, MeshLab, or tools/sculpt_angel.py)
plus a small rig file in, the .bmesh that unit/modelling/mesh/MeshBody reads out (tools/mesh_export.py documents the
format; this writes version 2, which adds per-vertex colours). Smooth normals and UVs come from the OBJ (or are made:
area-weighted normals, zero UVs); one OBJ object ('o' / 'g') per part; more OBJs with the same part names are the
coarser LODs. Reusable for every sculpted body: the angel statue first, the Bison, the dragon and the creatures after.

    python3 tools/mesh_import.py SCULPT.obj --rig SCULPT.rig.json [--lod SCULPT.lod1.obj ...] --name NAME
        [--blender] [--px-per-unit 16] [--max-quads 60000,20000,6000]
      -> bar/src/main/resources/assets/reignofnether/meshes/NAME.bmesh

The rig (JSON):
    {"space": "dsl", "atlas": [W, H],
     "parts": {"body": {"parent": null, "pivot": [x, y, z], "rot": [x, y, z]},
               "wing_left": {"parent": "body", "pivot": [...], "rot": [0, 0, 0]}, ...}}
  pivot: where the part turns, in the model's space (DSL: px, y up from the soles, the model facing -z); rot: its rest
  rotation in DSL degrees (as dsl_to_game's bones: the game converts them with mc_rot). Parts are written in the rig's
  order (a parent before its children). A part's vertices are given in its UNROTATED frame (the rest rotation is the
  game's to apply), in the model's space.
  --blender: the OBJ is Blender's default export (Y up after Blender's own -Z forward / Y up axis conversion, metres):
  scaled by --px-per-unit to px; a model facing Blender's -Y faces -z here. Without it the OBJ is in DSL space.

Vertex colours: the 'v x y z r g b' extension (Blender, MeshLab and sculpt_angel.py write it), 0..1, become the
vertex's RGBA - the game multiplies them into the entity colour (MeshCube), so a sculpt's baked marble and carved-depth
occlusion read in vanilla light, where entity shading is two flat lights and no shadows. A part with no colours is
written without them (version 2, flag clear: drawn exactly like version 1).

The .bmesh v2 (little-endian), as v1 (tools/mesh_export.py) except per LOD:
    u8 flags (bit 0: vertex colours), u32 vertexCount, per vertex 8 f32 (pos, normal, uv) [+ 4 u8 RGBA if bit 0],
    u32 quadCount, quads (u32 x4; a triangle a b c c), f32 x6 bounds
"""
import argparse
import json
import math
import os
import struct
import sys

import numpy as np

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
MESH_DIR = os.path.join(ROOT, "bar/src/main/resources/assets/reignofnether/meshes")
MAGIC = b"BMSH"
VERSION = 2


class ObjPart:
    def __init__(self, name):
        self.name = name
        self.V = None      # (n, 3) DSL px
        self.N = None      # (n, 3)
        self.UV = None     # (n, 2), v down (atlas convention)
        self.C = None      # (n, 4) uint8, or None
        self.Q = None      # (m, 4) int


def read_obj(path, blender=False, scale=16.0):
    """Parts of an OBJ: each 'o'/'g' its own part, its corners de-duplicated per (v, vt, vn) triple."""
    pos, col, nrm, tex = [], [], [], []
    groups = []
    cur = None
    with open(path) as fh:
        for line in fh:
            if not line or line[0] == "#":
                continue
            t = line.split()
            if not t:
                continue
            k = t[0]
            if k == "v":
                pos.append((float(t[1]), float(t[2]), float(t[3])))
                col.append((float(t[4]), float(t[5]), float(t[6])) if len(t) >= 7 else None)
            elif k == "vn":
                nrm.append((float(t[1]), float(t[2]), float(t[3])))
            elif k == "vt":
                tex.append((float(t[1]), float(t[2]) if len(t) > 2 else 0.0))
            elif k in ("o", "g"):
                name = t[1] if len(t) > 1 else "part%d" % len(groups)
                if cur is not None and not cur[1] and cur[0] == name:
                    continue
                cur = (name, [])
                groups.append(cur)
            elif k == "f":
                if cur is None:
                    cur = ("body", [])
                    groups.append(cur)
                corners = []
                for c in t[1:]:
                    ids = c.split("/")
                    vi = int(ids[0])
                    ti = int(ids[1]) if len(ids) > 1 and ids[1] else 0
                    ni = int(ids[2]) if len(ids) > 2 and ids[2] else 0
                    corners.append((vi if vi > 0 else len(pos) + vi + 1, ti if ti >= 0 else len(tex) + ti + 1,
                                    ni if ni >= 0 else len(nrm) + ni + 1))
                cur[1].append(corners)
    pos = np.array(pos, dtype=np.float64)
    if blender:
        pos = pos * scale
    has_col = any(c is not None for c in col)
    colv = np.array([c if c is not None else (1.0, 1.0, 1.0) for c in col], dtype=np.float64) if has_col else None
    nrm = np.array(nrm, dtype=np.float64) if nrm else None
    tex = np.array(tex, dtype=np.float64) if tex else None
    parts = {}
    for name, faces in groups:
        if not faces:
            continue
        key = {}
        vi_list = []
        quads = []
        for f in faces:
            ids = []
            for c in f:
                if c not in key:
                    key[c] = len(vi_list)
                    vi_list.append(c)
                ids.append(key[c])
            if len(ids) == 3:
                quads.append((ids[0], ids[1], ids[2], ids[2]))
            elif len(ids) == 4:
                quads.append(tuple(ids))
            else:
                for j in range(1, len(ids) - 1):          # a polygon: a fan of triangles
                    quads.append((ids[0], ids[j], ids[j + 1], ids[j + 1]))
        vi = np.array([c[0] for c in vi_list]) - 1
        ti = np.array([c[1] for c in vi_list]) - 1
        ni = np.array([c[2] for c in vi_list]) - 1
        p = ObjPart(name)
        p.V = pos[vi]
        p.Q = np.array(quads, dtype=np.int64)
        if nrm is not None and (ni >= 0).all():
            p.N = nrm[ni]
        else:
            p.N = smooth_normals(p.V, p.Q)
        p.N = p.N / np.maximum(np.linalg.norm(p.N, axis=1), 1e-12)[:, None]
        if tex is not None and (ti >= 0).all():
            p.UV = np.stack([tex[ti, 0], 1.0 - tex[ti, 1]], axis=1)
        else:
            p.UV = np.zeros((len(vi), 2))
        if colv is not None:
            p.C = np.concatenate([np.clip(np.round(colv[vi] * 255), 0, 255), np.full((len(vi), 1), 255)], axis=1).astype(np.uint8)
        parts[name] = p
    return parts


def smooth_normals(V, Q):
    """Area-weighted vertex normals over the faces (a triangle a b c c counts once)."""
    N = np.zeros_like(V)
    for tri in ((0, 1, 2), (0, 2, 3)):
        a, b, c = V[Q[:, tri[0]]], V[Q[:, tri[1]]], V[Q[:, tri[2]]]
        n = np.cross(b - a, c - a)
        for k in tri:
            np.add.at(N, Q[:, k], n)
    return N


def to_part_space(p, pivot):
    """DSL (px, y up) -> Minecraft part space (y down, origin 24 px above the soles), about the part's pivot."""
    V = np.stack([p.V[:, 0], 24.0 - p.V[:, 1], p.V[:, 2]], axis=1) - np.array([pivot[0], 24.0 - pivot[1], pivot[2]])
    N = np.stack([p.N[:, 0], -p.N[:, 1], p.N[:, 2]], axis=1)
    return V.astype(np.float32), N.astype(np.float32)


def mc_rot(deg):
    """dsl_to_game.mc_rot: DSL degrees -> ModelPart radians (x and z negated by the y flip)."""
    return (-math.radians(deg[0]), math.radians(deg[1]), -math.radians(deg[2]))


def write_bmesh(path, rig, lods, atlas):
    """lods: [ {part name: ObjPart} ] finest first. Parts in the rig's order; a part missing from a coarser LOD file
    keeps its finer mesh there (MeshBody.Part.lod clamps)."""
    names = list(rig["parts"].keys())
    index = {n: i for i, n in enumerate(names)}
    out = bytearray(MAGIC)
    out += struct.pack("<HHHH", VERSION, atlas[0], atlas[1], len(names))
    vdt = np.dtype([("p", "<f4", 3), ("n", "<f4", 3), ("uv", "<f4", 2)])
    vdtc = np.dtype([("p", "<f4", 3), ("n", "<f4", 3), ("uv", "<f4", 2), ("c", "u1", 4)])
    stats = []
    for n in names:
        info = rig["parts"][n]
        parent = info.get("parent")
        if parent is not None and (parent not in index or index[parent] >= index[n]):
            raise SystemExit("rig: part %r's parent %r must come before it" % (n, parent))
        piv = np.asarray(info["pivot"], dtype=np.float64)
        if parent is None:
            off = np.array([piv[0], 24.0 - piv[1], piv[2]])    # from the model's origin, 24 px above the soles
        else:
            pp = np.asarray(rig["parts"][parent]["pivot"], dtype=np.float64)
            off = np.array([piv[0] - pp[0], (24.0 - piv[1]) - (24.0 - pp[1]), piv[2] - pp[2]])
        name = n.encode("utf-8")
        out += struct.pack("<B", len(name)) + name
        out += struct.pack("<h", -1 if parent is None else index[parent])
        out += struct.pack("<3f", *off)
        out += struct.pack("<3f", *mc_rot(info.get("rot", [0, 0, 0])))
        chain = []
        last = None
        for lod in lods:
            p = lod.get(n, last)
            if p is None:
                raise SystemExit("part %r is not in the finest LOD's OBJ" % n)
            chain.append(p)
            last = p
        out += struct.pack("<B", len(chain))
        counts = []
        for p in chain:
            V, N = to_part_space(p, piv)
            has_c = p.C is not None
            arr = np.zeros(len(V), dtype=vdtc if has_c else vdt)
            arr["p"], arr["n"], arr["uv"] = V, N, p.UV.astype(np.float32)
            if has_c:
                arr["c"] = p.C
            out += struct.pack("<BI", 1 if has_c else 0, len(V))
            out += arr.tobytes()
            out += struct.pack("<I", len(p.Q))
            out += p.Q.astype("<u4").tobytes()
            lo, hi = (V.min(axis=0), V.max(axis=0)) if len(V) else (np.zeros(3), np.zeros(3))
            out += struct.pack("<6f", *lo, *hi)
            counts.append(len(p.Q))
        stats.append((n, counts))
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(out)
    return len(out), stats


def read_bmesh(path):
    """A v1 or v2 .bmesh back (MeshBody.read's checks): (version, atlas, [(name, parent, pose, [(V, N, UV, C, Q)])])."""
    data = open(path, "rb").read()
    if data[:4] != MAGIC:
        raise SystemExit("%s: not a .bmesh" % path)
    o = 4
    version, aw, ah, count = struct.unpack_from("<HHHH", data, o)
    o += 8
    if version not in (1, 2):
        raise SystemExit("%s: version %d" % (path, version))
    parts = []
    for i in range(count):
        ln = data[o]
        o += 1
        name = data[o:o + ln].decode("utf-8")
        o += ln
        (parent,) = struct.unpack_from("<h", data, o)
        o += 2
        if parent < -1 or parent >= i:
            raise SystemExit("%s: part %s parent %d" % (path, name, parent))
        pose = struct.unpack_from("<6f", data, o)
        o += 24
        nl = data[o]
        o += 1
        lods = []
        for _ in range(nl):
            flags = 0
            if version >= 2:
                flags = data[o]
                o += 1
            (nv,) = struct.unpack_from("<I", data, o)
            o += 4
            stride = 32 + (4 if flags & 1 else 0)
            raw = np.frombuffer(data, dtype=np.uint8, count=nv * stride, offset=o).reshape(nv, stride)
            o += nv * stride
            f = raw[:, :32].copy().view("<f4").reshape(nv, 8)
            C = raw[:, 32:36].copy() if flags & 1 else None
            (nq,) = struct.unpack_from("<I", data, o)
            o += 4
            Q = np.frombuffer(data, dtype="<u4", count=nq * 4, offset=o).reshape(nq, 4).astype(np.int64)
            o += nq * 16
            if nq and Q.max() >= nv:
                raise SystemExit("%s: part %s quad index out of range" % (path, name))
            o += 24
            lods.append((f[:, 0:3], f[:, 3:6], f[:, 6:8], C, Q))
        parts.append((name, parent, pose, lods))
    if o != len(data):
        raise SystemExit("%s: %d trailing bytes" % (path, len(data) - o))
    return version, (aw, ah), parts


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("obj")
    ap.add_argument("--rig", required=True)
    ap.add_argument("--lod", action="append", default=[], help="a coarser LOD's OBJ (finest first, repeatable)")
    ap.add_argument("--name", required=True, help="the body's name: meshes/<name>.bmesh")
    ap.add_argument("--out", default=None, help="the .bmesh path (default: the game's meshes folder)")
    ap.add_argument("--blender", action="store_true", help="the OBJ is a Blender export (metres, Y up)")
    ap.add_argument("--px-per-unit", type=float, default=16.0)
    ap.add_argument("--max-quads", default=None, help="a budget per LOD, comma separated: fail if a LOD is over it")
    a = ap.parse_args()
    with open(a.rig) as fh:
        rig = json.load(fh)
    lods = [read_obj(p, a.blender, a.px_per_unit) for p in [a.obj] + a.lod]
    for i, lod in enumerate(lods):
        extra = set(lod) - set(rig["parts"])
        if extra:
            raise SystemExit("LOD%d has parts the rig does not name: %s" % (i, sorted(extra)))
    out = a.out or os.path.join(MESH_DIR, a.name + ".bmesh")
    size, stats = write_bmesh(out, rig, lods, rig.get("atlas", [64, 64]))
    totals = [sum(c[i] for _, c in stats) for i in range(len(lods))]
    print("wrote %s: %d bytes, %d parts, quads per LOD %s" % (out, size, len(stats), totals))
    version, atlas, parts = read_bmesh(out)                 # read it back as the game will
    assert len(parts) == len(stats)
    if a.max_quads:
        for i, (cap, tot) in enumerate(zip((int(x) for x in a.max_quads.split(",")), totals)):
            if tot > cap:
                raise SystemExit("LOD%d has %d quads, over its budget of %d" % (i, tot, cap))


if __name__ == "__main__":
    main()
