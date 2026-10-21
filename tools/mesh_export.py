#!/usr/bin/env python3
"""Mesh bodies, step 1 (inbox/spec-1-mesh-bodies-2026-10-17.md): a concept model's cubes written as the triangle mesh
the game draws through unit/modelling/mesh/MeshCube - the "no new art" gate. The mesh Paladin must draw pixel for pixel
like the cube Paladin before any sculpt exists, so this file does NOT approximate vanilla: every vertex is computed
exactly as Minecraft 1.20.1's ModelPart.Cube constructor computes it, in float32, in the same order, with the same UV
division - and the JUnit test MeshBodyTest renders both trees into one VertexConsumer and demands equal bits.

    python3 tools/mesh_export.py --from-dsl design/models/sunforged_paladin_radiant_winged.json
        -> bar/src/main/resources/assets/reignofnether/meshes/radiant_paladin_unit.bmesh   (not in git: CI runs this
           before the build, build.yml job "bar"; the file is 450 KB of arithmetic on the DSL)

What goes in: the DSL model (design/models/README.md) through the SAME steps dsl_to_game.py takes - its atlas packer
(the UV islands), its build_tree (one ModelPart per bone, one child part per rotated cube, "lod" and "upg_<tag>"
parts) and its Java float formatting (f(): 4 decimals, what the generated class really holds at run time). The part
tree is therefore the generated class's tree part for part, name for name, so RadiantPaladinUnitModel (and every
animation subclass, SplitPasses, lodParts, upgradeParts) addresses it unchanged. A sculpted body (step 2) writes
fewer parts (the DSL bones) through the same format.

The .bmesh (little-endian):
    "BMSH"  u16 version (1)  u16 atlasW  u16 atlasH  u16 partCount
    per part, pre-order (a parent before its children, children in source order):
        u8 nameLen, name (UTF-8), i16 parentIndex (-1: a child of the root),
        f32 x y z         PartPose offset from the parent's pivot, model px, Minecraft part space (y down)
        f32 xRot yRot zRot   PartPose rotation, radians, Minecraft signs
        u8 lodCount, per LOD:
            u32 vertexCount, per vertex: f32 x y z (model px, part-local), f32 nx ny nz, f32 u v (atlas 0..1)
            u32 quadCount, per quad: u32 i0 i1 i2 i3 (a triangle repeats its third index: a b c c)
            f32 minX minY minZ maxX maxY maxZ (model px)
Floats, not the spec's i8 normals and u16 UVs: on the 2048-texel Paladin atlas a u16 UV is 0.03 texel off vanilla's
float, and nearest-texel sampling flips pixels at that distance - the gate would not close.

--check-java (on by default when the generated class exists) parses the class's createBodyLayer and fails if the DSL
and the class disagree - a DSL edited without re-running dsl_to_game would otherwise fail MeshBodyTest with a less
useful message.
"""
import argparse
import math
import os
import re
import struct
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import dsl_to_game  # noqa: E402  (the pipeline's own packer, tree builder and float formatter)
import render_concept  # noqa: E402

ROOT = dsl_to_game.ROOT
JAVA_DIR = dsl_to_game.JAVA_DIR
MESH_DIR = os.path.join(ROOT, "bar/src/main/resources/assets/reignofnether/meshes")
MAGIC = b"BMSH"
VERSION = 1
F32 = np.float32

# Direction.step() of each face, in the order ModelPart.Cube emits its polygons
FACES = ("down", "up", "west", "north", "east", "south")
NORMALS = {"down": (0, -1, 0), "up": (0, 1, 0), "west": (-1, 0, 0), "north": (0, 0, -1), "east": (1, 0, 0),
           "south": (0, 0, 1)}


def jf(v):
    """The float32 the generated Java holds for v: dsl_to_game.f() writes 4 decimals and javac parses them."""
    s = dsl_to_game.f(v)
    assert s.endswith("F")
    return F32(float(s[:-1]))


def layout(m):
    """Mirrors step 1 of dsl_to_game.build(): the UV island of every cube and the atlas size. Keep in step with it;
    --check-java catches drift."""
    cubes = []
    for b in m["bones"]:
        for i, c in enumerate(b.get("cubes", [])):
            dims = [max(1, int(math.ceil(v - 1e-6))) for v in c["size"]]
            cubes.append((b["name"], i, c, dims))
    width = 128
    while True:
        atlas = dsl_to_game.Atlas(width)
        uvs = {}
        for (bn, i, c, (w, h, d)) in sorted(cubes, key=lambda t: (-(t[3][2] + t[3][1]), t[0], t[1])):
            uvs[(bn, i)] = atlas.place(2 * (w + d), d + h)
        if atlas.height() <= width or width >= 1024:
            break
        width *= 2
    tw, th = width, 1 << max(5, math.ceil(math.log2(max(32, atlas.height()))))
    assert len(set(uvs.values())) == len(uvs), "two cubes share an atlas place"
    return uvs, tw, th


def vanilla_cube(tex_u, tex_v, x, y, z, w, h, d, gx, gy, gz, mirror, tex_w, tex_h):
    """ModelPart.Cube(texU, texV, x, y, z, w, h, d, gx, gy, gz, mirror, texW, texH, ALL_VISIBLE), 1.20.1, float32 step
    by step. Returns the six faces in vanilla's order, each [(px, py, pz, nx, ny, nz, u, v) x 4] in vanilla's vertex
    order (after Polygon's UV remap and the mirror reversal)."""
    x, y, z, w, h, d, gx, gy, gz = (F32(v) for v in (x, y, z, w, h, d, gx, gy, gz))
    tex_w, tex_h = F32(tex_w), F32(tex_h)
    f = x + w
    f1 = y + h
    f2 = z + d
    x = x - gx
    y = y - gy
    z = z - gz
    f = f + gx
    f1 = f1 + gy
    f2 = f2 + gz
    if mirror:
        x, f = f, x
    v0, v1, v2, v3 = (x, y, z), (f, y, z), (f, f1, z), (x, f1, z)
    v4, v5, v6, v7 = (x, y, f2), (f, y, f2), (f, f1, f2), (x, f1, f2)
    f4 = F32(tex_u)
    f5 = f4 + d
    f6 = f5 + w
    f7 = f6 + w
    f8 = f6 + d
    f9 = f8 + w
    f10 = F32(tex_v)
    f11 = f10 + d
    f12 = f11 + h
    zero_w, zero_h = F32(0) / tex_w, F32(0) / tex_h
    polys = [
        ("down", (v5, v4, v0, v1), f5, f10, f6, f11),
        ("up", (v2, v3, v7, v6), f6, f11, f7, f10),
        ("west", (v0, v4, v7, v3), f4, f11, f5, f12),
        ("north", (v1, v0, v3, v2), f5, f11, f6, f12),
        ("east", (v5, v1, v2, v6), f6, f11, f8, f12),
        ("south", (v4, v5, v6, v7), f8, f11, f9, f12),
    ]
    faces = []
    for face, verts, u0, v0_, u1, v1_ in polys:
        # Polygon: vertices[0] (u1, v0), [1] (u0, v0), [2] (u0, v1), [3] (u1, v1), each / the atlas size
        uv = [(u1 / tex_w - zero_w, v0_ / tex_h + zero_h), (u0 / tex_w + zero_w, v0_ / tex_h + zero_h),
              (u0 / tex_w + zero_w, v1_ / tex_h - zero_h), (u1 / tex_w - zero_w, v1_ / tex_h - zero_h)]
        vs = list(zip(verts, uv))
        nx, ny, nz = NORMALS[face]
        if mirror:
            vs.reverse()
            nx = -nx
        faces.append([(p[0], p[1], p[2], F32(nx), F32(ny), F32(nz), t[0], t[1]) for p, t in vs])
    return faces


class MeshPart:
    def __init__(self, name, parent, offset, rot):
        self.name, self.parent, self.offset, self.rot = name, parent, offset, rot
        self.boxes = []      # the vanilla cube parameters, for --check-java: (texU, texV, x, y, z, w, h, d, g)
        self.lods = []       # [(verts, quads)]: verts = [(px, py, pz, nx, ny, nz, u, v)], quads = [(a, b, c, d)]


def parts_from_tree(root, tex_w, tex_h):
    """The dsl_to_game Part tree, pre-order, each part's cubes as ONE mesh (cube order, face order: vanilla's)."""
    out = []

    def emit(p, parent_index):
        part = MeshPart(p.name, parent_index, tuple(jf(v) for v in p.offset), tuple(jf(v) for v in p.rot))
        verts, quads = [], []
        for (x, y, z, w, h, d, g, uv) in p.boxes:
            box = (uv[0], uv[1], jf(x), jf(y), jf(z), jf(w), jf(h), jf(d), jf(g))
            part.boxes.append(box)
            for face in vanilla_cube(box[0], box[1], *box[2:8], box[8], box[8], box[8], False, tex_w, tex_h):
                base = len(verts)
                verts.extend(face)
                quads.append((base, base + 1, base + 2, base + 3))
        part.lods.append((verts, quads))
        index = len(out)
        out.append(part)
        for c in p.children:
            emit(c, index)

    for c in root.children:
        emit(c, -1)
    return out


def bounds(verts):
    if not verts:
        return (0.0,) * 6
    xs, ys, zs = (sorted(v[k] for v in verts) for k in range(3))
    return (xs[0], ys[0], zs[0], xs[-1], ys[-1], zs[-1])


def write_bmesh(path, parts, tex_w, tex_h):
    out = bytearray(MAGIC)
    out += struct.pack("<HHHH", VERSION, tex_w, tex_h, len(parts))
    for p in parts:
        name = p.name.encode("utf-8")
        if len(name) > 255:
            raise SystemExit("part name too long: %r" % p.name)
        out += struct.pack("<B", len(name)) + name
        out += struct.pack("<h", p.parent)
        out += struct.pack("<3f", *(float(v) for v in p.offset))
        out += struct.pack("<3f", *(float(v) for v in p.rot))
        out += struct.pack("<B", len(p.lods))
        for verts, quads in p.lods:
            out += struct.pack("<I", len(verts))
            for v in verts:
                out += struct.pack("<8f", *(float(c) for c in v))
            out += struct.pack("<I", len(quads))
            for q in quads:
                out += struct.pack("<4I", *q)
            out += struct.pack("<6f", *(float(c) for c in bounds(verts)))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(out)
    return len(out)


def read_bmesh(path):
    """The reader, mirroring MeshBody.read: returns (tex_w, tex_h, parts) with the same MeshPart shape as the writer's."""
    data = open(path, "rb").read()
    pos = [0]

    def take(fmt):
        v = struct.unpack_from(fmt, data, pos[0])
        pos[0] += struct.calcsize(fmt)
        return v

    if data[:4] != MAGIC:
        raise SystemExit("%s: not a .bmesh" % path)
    pos[0] = 4
    version, tex_w, tex_h, n = take("<HHHH")
    if version != VERSION:
        raise SystemExit("%s: version %d, this tool reads %d" % (path, version, VERSION))
    parts = []
    for _ in range(n):
        (ln,) = take("<B")
        name = data[pos[0]:pos[0] + ln].decode("utf-8")
        pos[0] += ln
        (parent,) = take("<h")
        offset = tuple(F32(v) for v in take("<3f"))
        rot = tuple(F32(v) for v in take("<3f"))
        part = MeshPart(name, parent, offset, rot)
        (lods,) = take("<B")
        for _l in range(lods):
            (nv,) = take("<I")
            verts = [tuple(F32(c) for c in take("<8f")) for _v in range(nv)]
            (nq,) = take("<I")
            quads = [take("<4I") for _q in range(nq)]
            take("<6f")
            part.lods.append((verts, quads))
        parts.append(part)
    if pos[0] != len(data):
        raise SystemExit("%s: %d trailing bytes" % (path, len(data) - pos[0]))
    return tex_w, tex_h, parts


def same_parts(a, b):
    """Bit-equal part lists (names, parents, poses, every vertex, every quad)."""
    if len(a) != len(b):
        return "part count %d != %d" % (len(a), len(b))
    for i, (p, q) in enumerate(zip(a, b)):
        if p.name != q.name or p.parent != q.parent:
            return "part %d: %s/%d != %s/%d" % (i, p.name, p.parent, q.name, q.parent)
        for k in range(3):
            if p.offset[k].tobytes() != q.offset[k].tobytes() or p.rot[k].tobytes() != q.rot[k].tobytes():
                return "part %s: pose differs" % p.name
        if len(p.lods) != len(q.lods):
            return "part %s: lod count" % p.name
        for (va, qa), (vb, qb) in zip(p.lods, q.lods):
            if len(va) != len(vb) or len(qa) != len(qb):
                return "part %s: %d/%d verts, %d/%d quads" % (p.name, len(va), len(vb), len(qa), len(qb))
            for n, (x, y) in enumerate(zip(va, vb)):
                if any(F32(x[k]).tobytes() != F32(y[k]).tobytes() for k in range(8)):
                    return "part %s: vertex %d differs: %s vs %s" % (p.name, n, x, y)
            if any(tuple(x) != tuple(y) for x, y in zip(qa, qb)):
                return "part %s: quads differ" % p.name
    return None


# ------------------------------------------------------------- the generated Java, for the self-check -----------------
LINE = re.compile(r'^\s*(?:PartDefinition\s+(\w+)\s*=\s*)?(\w+)\.addOrReplaceChild\("([^"]+)",\s*CubeListBuilder\.create\(\)'
                  r'(.*?),\s*(PartPose\.\w+(?:\([^;]*\))?)\);\s*$')
BOX = re.compile(r'\.texOffs\((\d+),\s*(\d+)\)\.addBox\(([^,]+),\s*([^,]+),\s*([^,]+),\s*([^,]+),\s*([^,]+),\s*([^,]+),'
                 r'\s*new CubeDeformation\(([^)]+)\)\)')
NUM = re.compile(r'-?[0-9.]+F')


def java_float(lit):
    lit = lit.strip()
    assert lit.endswith("F"), lit
    return F32(float(lit[:-1]))


def parts_from_java(path, tex_w, tex_h):
    """createBodyLayer() of a dsl_to_game class, read back as MeshParts (the same shape as parts_from_tree)."""
    src = open(path).read()
    i = src.index("createBodyLayer()")
    j = src.index("return LayerDefinition.create(", i)
    m = re.search(r"LayerDefinition\.create\(mesh,\s*(\d+),\s*(\d+)\)", src[j:])
    jw, jh = int(m.group(1)), int(m.group(2))
    if (jw, jh) != (tex_w, tex_h):
        raise SystemExit("%s: atlas %dx%d, the DSL lays out %dx%d" % (path, jw, jh, tex_w, tex_h))
    var_index = {"root": -1}
    parts = []
    for line in src[i:j].splitlines():
        if ".addOrReplaceChild(" not in line:
            continue
        mm = LINE.match(line)
        if not mm:
            raise SystemExit("%s: cannot read line: %s" % (path, line.strip()[:120]))
        var, parent_var, name, boxes, pose = mm.groups()
        nums = [java_float(n) for n in NUM.findall(pose)]
        if pose.startswith("PartPose.ZERO"):
            offset, rot = (F32(0),) * 3, (F32(0),) * 3
        elif pose.startswith("PartPose.offsetAndRotation"):
            offset, rot = tuple(nums[:3]), tuple(nums[3:6])
        elif pose.startswith("PartPose.offset"):
            offset, rot = tuple(nums[:3]), (F32(0),) * 3
        else:
            raise SystemExit("%s: unknown pose %s" % (path, pose))
        part = MeshPart(name, var_index[parent_var], offset, rot)
        verts, quads = [], []
        for b in BOX.finditer(boxes):
            tu, tv = int(b.group(1)), int(b.group(2))
            x, y, z, w, h, d, g = (java_float(b.group(k)) for k in range(3, 10))
            part.boxes.append((tu, tv, x, y, z, w, h, d, g))
            for face in vanilla_cube(tu, tv, x, y, z, w, h, d, g, g, g, False, tex_w, tex_h):
                base = len(verts)
                verts.extend(face)
                quads.append((base, base + 1, base + 2, base + 3))
        part.lods.append((verts, quads))
        if var:
            var_index[var] = len(parts)
        parts.append(part)
    return parts


def find_java_for(dsl_path):
    """The generated class written from this DSL file (its header names the source)."""
    rel = os.path.relpath(os.path.abspath(dsl_path), ROOT).replace(os.sep, "/")
    if not os.path.isdir(JAVA_DIR):
        return None
    for fn in sorted(os.listdir(JAVA_DIR)):
        if fn.endswith(".java"):
            p = os.path.join(JAVA_DIR, fn)
            head = open(p).read(600)
            if "from " + rel + " " in head:
                return p
    return None


def texture_name(java_path):
    m = re.search(r'"([a-z0-9_]+)_layer"\)', open(java_path).read())
    return m.group(1) if m else None


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--from-dsl", required=True, metavar="MODEL.json", help="the concept model (design/models)")
    ap.add_argument("--texture", default=None, help="output name: meshes/<texture>.bmesh (default: the generated "
                                                     "class's texture name)")
    ap.add_argument("--out", default=None, help="write here instead of the mod's assets")
    ap.add_argument("--no-check-java", action="store_true", help="skip the comparison with the generated class")
    ap.add_argument("--check-only", action="store_true", help="build and check, write nothing")
    a = ap.parse_args()

    m = render_concept.load_model(a.from_dsl)
    uvs, tw, th = layout(m)
    root = dsl_to_game.build_tree(m, uvs, set(m.get("emissive", [])))
    parts = parts_from_tree(root, tw, th)
    java = find_java_for(a.from_dsl)
    texture = a.texture or (java and texture_name(java))
    if not texture:
        raise SystemExit("--texture: no generated class names this model, so the output name is needed")
    if java and not a.no_check_java:
        why = same_parts(parts, parts_from_java(java, tw, th))
        if why:
            raise SystemExit("%s and %s disagree (%s): run tools/dsl_to_game.py again first" % (
                os.path.relpath(java, ROOT), a.from_dsl, why))
    nparts = len(parts)
    nverts = sum(len(v) for p in parts for v, _ in p.lods)
    nquads = sum(len(q) for p in parts for _, q in p.lods)
    ncubes = sum(len(p.boxes) for p in parts)
    print("%s: %d cubes in %d parts -> %d vertices, %d quads (%d LOD); atlas %dx%d UV%s" % (
        texture, ncubes, nparts, nverts, nquads, max(len(p.lods) for p in parts), tw, th,
        "; matches " + os.path.relpath(java, ROOT) if java and not a.no_check_java else ""))
    if a.check_only:
        return 0
    out = a.out or os.path.join(MESH_DIR, texture + ".bmesh")
    size = write_bmesh(out, parts, tw, th)
    rw, rh, back = read_bmesh(out)
    why = (rw, rh) != (tw, th) and "atlas size" or same_parts(parts, back)
    if why:
        raise SystemExit("%s does not read back as written (%s)" % (out, why))
    print("wrote %s (%d bytes), read back equal" % (os.path.relpath(out, ROOT), size))
    return 0


if __name__ == "__main__":
    sys.exit(main())
