#!/usr/bin/env python3
"""Concept model -> game model (lovish, Oct 14 18:13: "all the concept art should help with detail, creation and
implementation of things for the game"). What is approved on a concept sheet is what walks in the game: this turns a
design/models DSL file into

  1. a Java model class (vanilla LayerDefinition / ModelPart, the same tree the sheet was drawn from):
       bar/src/main/java/com/solegendary/reignofnether/unit/modelling/models/gen/<Class>.java
     - every DSL bone becomes a ModelPart named after it (so animation code addresses "cape_1", "halo", "arms");
     - a cube with its own rotation becomes a one-cube child part (exactly as render_concept.py draws it);
     - cubes tagged with an upgrade go into a child part "upg_<tag>" per bone, hidden until the unit has it;
     - small detail cubes (thin lines, rivets, engravings; never a glowing one) go into a child part "lod" per bone,
       which the renderer hides when the camera is far (a 400-cube body is fine close up, wasteful as a dot at RTS
       zoom);
  2. a painted texture atlas at DENSITY texels per model pixel (the layer keeps UVs in model pixels, so a 4x texture
     simply draws finer - the same trick as an HD skin): every cube its own box-UV island at the exact float place the
     engine samples, each face shaded like a hand-painted Minecraft texture - lighter at the top, darker at the
     bottom, a lit rim along the upper edge and a dark seam along the lower one, a soft sheen band on polished metal,
     a fine weave on cloth - plus a grey-scale team mask (TeamColourLayer multiplies it by the owner's colour) and an
     emissive mask (drawn additively, full-bright, on top):
       bar/src/main/resources/assets/reignofnether/textures/entities/<texture>.png, _team.png, _glow.png

    python3 tools/dsl_to_game.py design/models/sunforged_paladin_radiant_v2.json --class RadiantPaladinModel \\
        --texture radiant_paladin_unit --team tabard,tabard_dark

Coordinates: the DSL is y-up from the feet with the unit facing -Z and +x its left; a Minecraft entity model is the
same with y flipped and the origin 24 px above the feet (model y = 24 - dsl y). Flipping y conjugates every
rotation: (x, y, z) degrees in the DSL -> (-x, +y, -z) radians in a PartPose, same Z*Y*X order.

Every export is CHECKED before anything is written: the part tree is posed the way Minecraft's ModelPart does it
(translate, then Z*Y*X, children inside parents) and every cube's eight corners must land within 0.01 px of where
the concept renderer puts them (tools/preview_model.posed_cubes) - a wrong sign or pivot fails the export, it does
not ship as a twisted unit.
"""
import argparse
import math
import os
import random
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import render_concept  # noqa: E402  (bpy-free when imported outside Blender)
from preview_model import posed_cubes  # noqa: E402

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
JAVA_DIR = os.path.join(ROOT, "bar/src/main/java/com/solegendary/reignofnether/unit/modelling/models/gen")
TEX_DIR = os.path.join(ROOT, "bar/src/main/resources/assets/reignofnether/textures/entities")
PACKAGE = "com.solegendary.reignofnether.unit.modelling.models.gen"

LOD_MAX_VOLUME = 0.6      # px^3: a cube this small is detail (a rivet, an engraved line)
LOD_MAX_THIN = 0.36       # px: or this thin in two axes (a split, a fluting line)
DENSITY = 4               # texels per model pixel in the painted atlas
RIM_MIN_HEIGHT = 1.9      # px: a cube at least this tall gets a lit rim and a seam (thinner ones are slices of a form)
METAL_HINTS = render_concept.METAL_HINTS
CLOTH_HINTS = ("tabard", "cloth", "cape", "sash", "leather", "linen", "wool", "silk")


def hex_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def f(v):
    """A Java float literal."""
    s = ("%.4f" % v).rstrip("0").rstrip(".")
    if s in ("-0", ""):
        s = "0"
    return s + "F"


def ident(name):
    out = "".join(c if c.isalnum() else "_" for c in name)
    return out if not out[0].isdigit() else "_" + out


# ------------------------------------------------------------------ texture atlas ----------------------------------
class Atlas:
    """Shelf packer for box-UV islands, in model pixels: an island for a cube of w x h x d (rounded up) is 2(w+d)
    wide and d+h tall."""

    def __init__(self, width):
        self.w, self.x, self.y, self.row_h = width, 0, 0, 0

    def place(self, iw, ih):
        if self.x + iw > self.w:
            self.x, self.y, self.row_h = 0, self.y + self.row_h, 0
        u, v = self.x, self.y
        self.x += iw
        self.row_h = max(self.row_h, ih)
        return u, v

    def height(self):
        return self.y + self.row_h


def clamp8(v):
    return max(0, min(255, int(round(v))))


def shade(rgb, k):
    """Brightness k applied the way paint darkens: multiply, and for k > 1 lift toward a warm white."""
    if k <= 1.0:
        return tuple(clamp8(c * k) for c in rgb) + (255,)
    t = min(1.0, (k - 1.0) * 1.6)
    return tuple(clamp8(c + (255 - c) * t * 0.55) for c in rgb) + (255,)


class Painter:
    """Paints islands at DENSITY, at the float places ModelPart.Cube samples (box UV: top at (u+d, v), bottom at
    (u+d+w, v), then the four sides from (u, v+d): -x, front -z, +x, back +z; row 0 of a side is its upper edge)."""

    def __init__(self, img, density):
        self.px, self.D = img.load(), density
        self.W, self.H = img.size

    def rect(self, a0, b0, a1, b1):
        D = self.D
        x0, y0 = int(math.floor(a0 * D + 1e-6)), int(math.floor(b0 * D + 1e-6))
        x1, y1 = max(x0 + 1, int(math.ceil(a1 * D - 1e-6))), max(y0 + 1, int(math.ceil(b1 * D - 1e-6)))
        return x0, y0, min(x1, self.W), min(y1, self.H)

    def fill(self, a0, b0, a1, b1, colour):
        x0, y0, x1, y1 = self.rect(a0, b0, a1, b1)
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.px[x, y] = colour

    def face(self, a0, b0, a1, b1, rgb, kind, material, rng, span=None, rims=True):
        """span: (cube top y, cube bottom y, bone top y, bone bottom y) in DSL px - the side faces then shade by HEIGHT
        IN THE BONE, so a stack of slices (a cuirass, a dome) is one continuous gradient instead of a stripe per cube;
        rims: the lit upper rim and the dark seam, only on cubes tall enough to be a plate of their own."""
        x0, y0, x1, y1 = self.rect(a0, b0, a1, b1)
        fw, fh = x1 - x0, y1 - y0
        for j in range(fh):
            for i in range(fw):
                edge_x = (i == 0 or i == fw - 1) and fw > 2
                t = j / max(1, fh - 1)
                if span is not None:
                    yy = span[0] - (j + 0.5) / fh * (span[0] - span[1])
                    tb = (span[2] - yy) / max(1e-6, span[2] - span[3])     # 0 at the bone's top, 1 at its bottom
                else:
                    tb = t
                if kind == "top":
                    if span is not None and not rims:
                        # the ledge of a slice in a stack (a cuirass, a dome): Minecraft lights an upward face ~1.35x a
                        # side, so a ledge painted like its side flashes as a bright stripe from the RTS camera;
                        # painted at 0.74 of the side it reads as the same curved surface continuing
                        k = (1.07 - 0.20 * tb) * 0.74
                    else:
                        k = 1.10 - 0.08 * (tb if span is not None else 0.0)
                        if (edge_x or ((j == 0 or j == fh - 1) and fh > 2)):
                            k -= 0.08
                elif kind == "bottom":
                    k = 0.70
                else:
                    k = 1.07 - 0.20 * tb - (0.05 * t if span is not None else 0.0)
                    if material == "metal":
                        k += 0.07 * math.exp(-((t - 0.28) / 0.14) ** 2)      # the polish band
                    if rims and fh > 3 and j == 0:
                        k = max(k, 1.0) * 1.12                                # the lit upper rim
                    elif rims and fh > 3 and j == fh - 1:
                        k *= 0.72                                             # the seam underneath
                    if edge_x:
                        k *= 0.93
                if material == "cloth":
                    k *= 0.96 + 0.06 * rng.random() - (0.03 if (i + j) % 2 == 0 else 0.0)   # a fine weave
                elif material == "metal" and kind == "side":
                    k *= 0.985 + 0.03 * rng.random()                          # brushed, barely
                self.px[x0 + i, y0 + j] = shade(rgb, k)

    def island(self, u, v, w, h, d, rgb, material="plain", seed=0, flat=False, span=None):
        """w, h, d are the cube's float sizes; (u, v) its integer texOffs. flat: one colour, no shading (masks).
        span: see face()."""
        rng = random.Random(seed)
        uw, uh, ud = (int(math.ceil(s - 1e-6)) or 1 for s in (w, h, d))
        base = (tuple(rgb) + (255,)) if flat else shade(rgb, 0.92)
        # a safety net under the whole packed rectangle: whatever a sliver of a face samples is this cube's colour
        self.fill(u, v, u + 2 * (uw + ud), v + ud + uh, base)
        if flat:
            return
        rims = h >= RIM_MIN_HEIGHT
        top_span = None if span is None else (span[0], span[0], span[2], span[3])
        self.face(u + d, v, u + d + w, v + d, rgb, "top", material, rng, top_span, rims)
        self.face(u + d + w, v, u + d + 2 * w, v + d, rgb, "bottom", material, rng, None, rims)
        for a0, a1 in ((u, u + d), (u + d, u + d + w), (u + d + w, u + 2 * d + w), (u + 2 * d + w, u + 2 * d + 2 * w)):
            self.face(a0, v + d, a1, v + d + h, rgb, "side", material, rng, span, rims)


def material_of(name):
    n = name.lower()
    if any(hh in n for hh in METAL_HINTS):
        return "metal"
    if any(hh in n for hh in CLOTH_HINTS):
        return "cloth"
    return "plain"


# ------------------------------------------------------------------ the part tree ----------------------------------
class Part:
    """A ModelPart to be: offset from the parent's pivot (model space), rotation in radians (MC signs), cubes as
    (x, y, z, w, h, d, inflate, uv) relative to its own pivot, children."""

    def __init__(self, name, offset, rot):
        self.name, self.offset, self.rot = name, tuple(offset), tuple(rot)
        self.boxes, self.children = [], []

    def child(self, name, offset=(0.0, 0.0, 0.0), rot=(0.0, 0.0, 0.0)):
        p = Part(name, offset, rot)
        self.children.append(p)
        return p


def is_detail(c, emissive):
    if c["color"] in emissive:
        return False          # light is what reads at a distance
    sx, sy, sz = c["size"]
    vol = sx * sy * sz
    thin = sum(1 for v in (sx, sy, sz) if v <= LOD_MAX_THIN)
    return vol <= LOD_MAX_VOLUME or thin >= 2


def to_model(p):
    return (p[0], 24.0 - p[1], p[2])


def mc_rot(deg):
    return (-math.radians(deg[0]), math.radians(deg[1]), -math.radians(deg[2]))


def build_tree(m, uvs, emissive):
    by_parent = {}
    for b in m["bones"]:
        by_parent.setdefault(b.get("parent"), []).append(b)
    root = Part("root", (0, 0, 0), (0, 0, 0))

    def box(c, origin, uv):
        x0, y0, z0 = c["from"]
        sx, sy, sz = c["size"]
        return (x0 - origin[0], (24.0 - (y0 + sy)) - origin[1], z0 - origin[2], sx, sy, sz, c["inflate"], uv)

    def emit(b, parent_part, parent_pivot):
        pv = to_model(b["pivot"])
        part = parent_part.child(b["name"], tuple(pv[a] - parent_pivot[a] for a in range(3)), mc_rot(b["rotation"]))
        lod = None
        upg = {}
        for i, c in enumerate(b.get("cubes", [])):
            uv = uvs[(b["name"], i)]
            tag = c.get("upgrade") or b.get("upgrade")
            holder = part
            if tag:
                if tag not in upg:
                    upg[tag] = part.child("upg_" + tag)
                holder = upg[tag]
            elif is_detail(c, emissive):
                if lod is None:
                    lod = part.child("lod")
                holder = lod
            if any(abs(a) > 1e-9 for a in c["rotation"]):
                cp = to_model(c["pivot"])
                r = holder.child("%s_%s%d" % (b["name"], "u" if tag else "r", i),
                                 tuple(cp[a] - pv[a] for a in range(3)), mc_rot(c["rotation"]))
                r.boxes.append(box(c, cp, uv))
            else:
                holder.boxes.append(box(c, pv, uv))
        for ch in by_parent.get(b["name"], []):
            emit(ch, part, pv)

    for b in by_parent.get(None, []):
        emit(b, root, (0.0, 0.0, 0.0))
    return root


# ------------------------------------------------------------------ the check -----------------------------------------
def matmul(A, B):
    return tuple(tuple(sum(A[i][k] * B[k][j] for k in range(3)) for j in range(3)) for i in range(3))


def mat_zyx(r):
    cx, sx = math.cos(r[0]), math.sin(r[0])
    cy, sy = math.cos(r[1]), math.sin(r[1])
    cz, sz = math.cos(r[2]), math.sin(r[2])
    Rx = ((1, 0, 0), (0, cx, -sx), (0, sx, cx))
    Ry = ((cy, 0, sy), (0, 1, 0), (-sy, 0, cy))
    Rz = ((cz, -sz, 0), (sz, cz, 0), (0, 0, 1))
    return matmul(Rz, matmul(Ry, Rx))


def mc_corners(root, pose):
    """Every box's 8 corners, posed exactly like ModelPart.render: translate(offset) then rotate Z*Y*X, parents first.
    pose: part name -> extra (x, y, z) radians added to the part's rotation (restPose)."""
    out = []
    I = ((1, 0, 0), (0, 1, 0), (0, 0, 1))

    def walk(p, M, t):
        rot = p.rot
        if p.name in pose:
            rot = tuple(rot[a] + pose[p.name][a] for a in range(3))
        t2 = tuple(t[i] + sum(M[i][k] * p.offset[k] for k in range(3)) for i in range(3))
        M2 = matmul(M, mat_zyx(rot))
        for (x, y, z, w, h, d, g, _uv) in p.boxes:
            lo, hi = (x - g, y - g, z - g), (x + w + g, y + h + g, z + d + g)
            pts = []
            for cx in (lo[0], hi[0]):
                for cy in (lo[1], hi[1]):
                    for cz in (lo[2], hi[2]):
                        v = (cx, cy, cz)
                        pts.append(tuple(t2[i] + sum(M2[i][k] * v[k] for k in range(3)) for i in range(3)))
            out.append(pts)
        for c in p.children:
            walk(c, M2, t2)

    walk(root, I, (0.0, 0.0, 0.0))
    return out


def check(m, root):
    """The game tree must put every cube where the concept renderer does (upgrade parts included)."""
    pose = {n: mc_rot(r) for n, r in m["pose"].items()}
    game = mc_corners(root, pose)
    concept = [pts for pts, _ in posed_cubes(m, upgraded=True)]
    if len(game) != len(concept):
        raise SystemExit("check: %d cubes in the game tree, %d in the concept" % (len(game), len(concept)))

    def key(pts):
        # model space -> DSL space, then an order-free signature (sorted corners)
        return sorted((round(p[0], 2), round(24.0 - p[1], 2), round(p[2], 2)) for p in pts)

    worst = 0.0
    g_sorted = sorted(key(p) for p in game)
    c_sorted = sorted(sorted((round(p[0], 2), round(p[1], 2), round(p[2], 2)) for p in pts) for pts in concept)
    for a, b in zip(g_sorted, c_sorted):
        for pa, pb in zip(a, b):
            worst = max(worst, max(abs(pa[i] - pb[i]) for i in range(3)))
    if worst > 0.02:
        raise SystemExit("check: the game tree moves a cube corner %.3f px away from the concept - not written" % worst)
    return worst


# ------------------------------------------------------------------ Java ------------------------------------------------
def java_tree(root):
    lines = []

    def pose(p):
        o, r = p.offset, p.rot
        if any(abs(a) > 1e-9 for a in r):
            return "PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s)" % (f(o[0]), f(o[1]), f(o[2]), f(r[0]), f(r[1]), f(r[2]))
        if any(abs(a) > 1e-9 for a in o):
            return "PartPose.offset(%s, %s, %s)" % (f(o[0]), f(o[1]), f(o[2]))
        return "PartPose.ZERO"

    counter = [0]

    def emit(p, parent_var):
        cubes = "CubeListBuilder.create()" + "".join(
            ".texOffs(%d, %d).addBox(%s, %s, %s, %s, %s, %s, new CubeDeformation(%s))" % (
                uv[0], uv[1], f(x), f(y), f(z), f(w), f(h), f(d), f(g)) for (x, y, z, w, h, d, g, uv) in p.boxes)
        call = '%s.addOrReplaceChild("%s", %s, %s)' % (parent_var, p.name, cubes, pose(p))
        if p.children:
            counter[0] += 1
            var = "p%d_%s" % (counter[0], ident(p.name))
            lines.append("PartDefinition %s = %s;" % (var, call))
            for c in p.children:
                emit(c, var)
        else:
            lines.append(call + ";")

    for c in root.children:
        emit(c, "root")
    return lines


def collect_lines(root, m):
    """Java that files every bone, "lod" and "upg_<tag>" part by its path from the root."""
    bone_names = {b["name"] for b in m["bones"]}
    out = []

    def walk(p, expr):
        for c in p.children:
            e = '%s.getChild("%s")' % (expr, c.name)
            if c.name in bone_names:
                out.append('bones.put("%s", %s);' % (c.name, e))
                walk(c, e)
            elif c.name == "lod":
                out.append("lodParts.add(%s);" % e)
            elif c.name.startswith("upg_"):
                out.append('upgradeParts.computeIfAbsent("%s", k -> new ArrayList<>()).add(%s);' % (c.name[4:], e))
    walk(root, "root")
    return out


def count_parts(root):
    return 1 + sum(count_parts(c) for c in root.children)


def build(m, cls, team_colours, layer, density):
    pal = {k: hex_rgb(v) for k, v in m["palette"].items()}
    emissive = set(m.get("emissive", []))

    # 1. UV islands: one per cube, packed in model pixels
    cubes = []
    for b in m["bones"]:
        for i, c in enumerate(b.get("cubes", [])):
            dims = [max(1, int(math.ceil(v - 1e-6))) for v in c["size"]]
            cubes.append((b["name"], i, c, dims))
    width = 128
    while True:
        atlas = Atlas(width)
        uvs = {}
        for (bn, i, c, (w, h, d)) in sorted(cubes, key=lambda t: (-(t[3][2] + t[3][1]), t[0], t[1])):
            uvs[(bn, i)] = atlas.place(2 * (w + d), d + h)
        if atlas.height() <= width or width >= 1024:
            break
        width *= 2
    tw, th = width, 1 << max(5, math.ceil(math.log2(max(32, atlas.height()))))
    D = density
    imgs = {k: Image.new("RGBA", (tw * D, th * D), (0, 0, 0, 0)) for k in ("base", "team", "glow")}
    paint = {k: Painter(img, D) for k, img in imgs.items()}
    team_grey = {col: ((255, 255, 255) if k == 0 else (190, 190, 190)) for k, col in enumerate(team_colours)}
    # each bone's vertical extent (its unrotated cubes): the side faces shade by height in the bone
    extent = {}
    for b in m["bones"]:
        ys = [(c["from"][1], c["from"][1] + c["size"][1]) for c in b.get("cubes", []) if not any(c["rotation"])]
        if ys:
            extent[b["name"]] = (max(y1 for _, y1 in ys), min(y0 for y0, _ in ys))
    for n, (bn, i, c, _dims) in enumerate(cubes):
        u, v = uvs[(bn, i)]
        w, h, d = c["size"]
        col = c["color"]
        span = None
        if not any(c["rotation"]) and bn in extent and extent[bn][0] - extent[bn][1] > 2.0:
            span = (c["from"][1] + h, c["from"][1], extent[bn][0], extent[bn][1])
        paint["base"].island(u, v, w, h, d, pal[col], material_of(col), seed=n, span=span)
        if col in team_grey:
            # the mask carries the same light and shade as the paint, in grey, so the owner's colour is shaded too
            paint["team"].island(u, v, w, h, d, team_grey[col], material_of(col), seed=n, span=span)
        if col in emissive:
            paint["glow"].island(u, v, w, h, d, pal[col], flat=True)

    # 2. the part tree, checked against the concept renderer
    root = build_tree(m, uvs, emissive)
    worst = check(m, root)

    pose_lines = []
    for name, r in m["pose"].items():
        rr = mc_rot(r)
        pose_lines.append('add("%s", %s, %s, %s);' % (name, f(rr[0]), f(rr[1]), f(rr[2])))
    upgrade_tags = sorted({c.get("upgrade") or b.get("upgrade") for b in m["bones"] for c in b.get("cubes", [])
                           if c.get("upgrade") or b.get("upgrade")})
    bone_names = [b["name"] for b in m["bones"]]
    java = JAVA_TEMPLATE.format(
        package=PACKAGE, cls=cls, layer=layer, src=os.path.relpath(m["_path"], ROOT).replace(os.sep, "/"), name=m["name"],
        defs="\n        ".join(java_tree(root)), tw=tw, th=th,
        part_names=", ".join('"%s"' % n for n in bone_names),
        pose="\n        ".join(pose_lines) if pose_lines else "// no rest pose",
        upgrades=", ".join('"%s"' % t for t in upgrade_tags), density=D,
        cubes=len(cubes), parts=count_parts(root) - 1, collect="\n        ".join(collect_lines(root, m)))
    return java, imgs, dict(root=root, tw=tw, th=th, cubes=len(cubes), parts=count_parts(root) - 1, worst=worst,
                            lod=sum(len(p.boxes) for p in walk_parts(root) if p.name == "lod" or in_lod(root, p)))


def walk_parts(p):
    yield p
    for c in p.children:
        yield from walk_parts(c)


def in_lod(root, target):
    def find(p, inside):
        if p is target:
            return inside
        for c in p.children:
            r = find(c, inside or p.name == "lod")
            if r is not None:
                return r
        return None
    return bool(find(root, False))


JAVA_TEMPLATE = '''package {package};

// GENERATED by tools/dsl_to_game.py from {src} ("{name}") - do not edit by hand: change the concept model (its
// generator) and run the tool again. {cubes} cubes in {parts} parts; the painted texture is {density}x (UVs stay in
// model pixels). The animation lives in a hand-written subclass, not here.

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class {cls}<T extends Entity> extends HierarchicalModel<T> {{
    public static final ModelLayerLocation LAYER_LOCATION =
        new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "{layer}"), "main");
    /** The concept model's bones, by name (the same names as the DSL file). */
    public static final List<String> BONES = List.of({part_names});
    /** Upgrade tags whose parts ("upg_<tag>" under a bone) stay hidden until the unit has the upgrade. */
    public static final List<String> UPGRADES = List.of({upgrades});

    private final ModelPart root;
    private final List<ModelPart> allParts;
    /** Bone name -> part. */
    public final Map<String, ModelPart> bones = new HashMap<>();
    /** Every "lod" part (the small detail), and every "upg_<tag>" part by tag. */
    public final List<ModelPart> lodParts = new ArrayList<>();
    public final Map<String, List<ModelPart>> upgradeParts = new HashMap<>();

    public {cls}(ModelPart root) {{
        this.root = root;
        this.allParts = root.getAllParts().toList();   // once: restPose runs every frame for every unit
        collect(root);
    }}

    /** The tree's named parts, by their generated paths (no lookups by search at run time). */
    private void collect(ModelPart root) {{
        {collect}
    }}

    public ModelPart bone(String name) {{
        return bones.get(name);
    }}

    public static LayerDefinition createBodyLayer() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        {defs}
        return LayerDefinition.create(mesh, {tw}, {th});
    }}

    @Override
    public ModelPart root() {{
        return root;
    }}

    private void add(String bone, float x, float y, float z) {{
        ModelPart p = bones.get(bone);
        if (p != null) {{
            p.xRot += x;
            p.yRot += y;
            p.zRot += z;
        }}
    }}

    /** Back to the concept's rest pose: the bones' own angles are baked into the parts, "pose" adds the idle tilt. */
    public void restPose() {{
        for (int i = 0, n = allParts.size(); i < n; i++)
            allParts.get(i).resetPose();
        {pose}
    }}

    /** The small detail on or off (off when the camera is far). */
    public void setDetail(boolean on) {{
        for (ModelPart p : lodParts)
            p.visible = on;
    }}

    /** An upgrade's parts on or off. */
    public void setUpgrade(String tag, boolean on) {{
        for (ModelPart p : upgradeParts.getOrDefault(tag, List.of()))
            p.visible = on;
    }}

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {{
        restPose();
    }}
}}
'''


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("model")
    ap.add_argument("--class", dest="cls", required=True)
    ap.add_argument("--texture", required=True)
    ap.add_argument("--team", default="", help="palette names drawn as team colour (first = full, others = darker)")
    ap.add_argument("--layer", default=None)
    ap.add_argument("--density", type=int, default=DENSITY)
    ap.add_argument("--check-only", action="store_true", help="build and check, write nothing")
    a = ap.parse_args()
    m = render_concept.load_model(a.model)
    m["_path"] = os.path.abspath(a.model)
    team = [t for t in a.team.split(",") if t]
    for t in team:
        if t not in m["palette"]:
            raise SystemExit("--team: %r is not in the palette" % t)
    java, imgs, info = build(m, a.cls, team, a.layer or a.texture + "_layer", a.density)
    print("%s: %d cubes in %d parts (%d detail cubes in lod parts); atlas %dx%d UV at %dx = %dx%d px; "
          "check: worst corner %.4f px" % (a.cls, info["cubes"], info["parts"], info["lod"], info["tw"], info["th"],
                                           a.density, info["tw"] * a.density, info["th"] * a.density, info["worst"]))
    if a.check_only:
        return 0
    os.makedirs(JAVA_DIR, exist_ok=True)
    with open(os.path.join(JAVA_DIR, a.cls + ".java"), "w") as fh:
        fh.write(java)
    imgs["base"].save(os.path.join(TEX_DIR, a.texture + ".png"), optimize=True)
    if team:
        imgs["team"].save(os.path.join(TEX_DIR, a.texture + "_team.png"), optimize=True)
    imgs["glow"].save(os.path.join(TEX_DIR, a.texture + "_glow.png"), optimize=True)
    print("wrote %s.java, %s.png%s, _glow.png" % (a.cls, a.texture, ", _team.png" if team else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
