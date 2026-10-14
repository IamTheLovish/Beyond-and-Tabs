#!/usr/bin/env python3
"""Concept-sheet renderer for the box models in design/models/ (DSL: design/models/README.md).

Two ways to run it:
  python3 tools/render_concept.py --check design/models/*.json
      no Blender needed: validates the files and prints bone/cube counts and the bounding box in blocks.
  blender -b -P tools/render_concept.py -- --model design/models/x.json --out out/x [--samples 48] [--only ...]
      inside Blender 4.2 (CPU only): front / side / three-quarter stills (Cycles), a transparent black silhouette
      (Workbench, flattened onto white by tools/concept_sheet.py) and the 24 turntable frames (EEVEE under xvfb,
      or Workbench). Everything stays flat-coloured: no textures, no pip packages, nothing ships in the jar.
      A model whose cubes or bones carry "upgrade" tags (the researched parts, unit/UpgradeVisuals in the mod) is
      rendered without them everywhere, plus one extra still upgraded.png (the three-quarter camera, all tagged
      parts shown) that the sheet puts in a fifth panel.

Spaces: the DSL is Minecraft model space (pixels, y up, the unit faces -Z). Blender is z up, so a DSL point
(x, y, z) becomes (x, -z, y) / 16: a proper rotation, no mirroring, and the unit's front ends up at Blender +Y.
Bone rotations [rx, ry, rz] (degrees, x applied first as in ModelPart) become Blender Euler 'XZY' = (rx, -rz, ry).
"""
import argparse, glob, json, math, os, sys

try:
    import bpy
    from mathutils import Matrix
except ImportError:  # plain python: only --check works
    bpy = None

HEX = "0123456789abcdefABCDEF"


# ---------------------------------------------------------------- DSL ---------------------------------------------
def _vec(v, n, what):
    if not (isinstance(v, list) and len(v) == n and all(isinstance(c, (int, float)) for c in v)):
        raise ValueError(f"{what} must be a list of {n} numbers, got {v!r}")
    return [float(c) for c in v]


def load_model(path):
    """Read + validate one DSL file. Returns the dict with numbers normalised to floats."""
    with open(path) as f:
        m = json.load(f)
    for key in ("name", "faction", "role"):
        if not isinstance(m.get(key), str) or not m[key]:
            raise ValueError(f"'{key}' must be a non-empty string")
    m["scale_px_per_block"] = float(m.get("scale_px_per_block", 16))
    pal = m.get("palette")
    if not isinstance(pal, dict) or not pal:
        raise ValueError("'palette' must map names to '#rrggbb'")
    for k, v in pal.items():
        if not (isinstance(v, str) and len(v) == 7 and v[0] == "#" and all(c in HEX for c in v[1:])):
            raise ValueError(f"palette.{k}: {v!r} is not '#rrggbb'")
    m["emissive"] = list(m.get("emissive", ["sun"]))
    for e in m["emissive"]:
        if e not in pal:
            raise ValueError(f"emissive colour {e!r} is not in the palette")
    bones = m.get("bones")
    if not isinstance(bones, list) or not bones:
        raise ValueError("'bones' must be a non-empty list")
    names = [b.get("name") for b in bones]
    if len(set(names)) != len(names) or not all(isinstance(n, str) and n for n in names):
        raise ValueError("every bone needs a unique non-empty 'name'")
    for b in bones:
        b["pivot"] = _vec(b.get("pivot"), 3, f"bone {b['name']} pivot")
        b["rotation"] = _vec(b.get("rotation", [0, 0, 0]), 3, f"bone {b['name']} rotation")
        if b.get("parent") is not None and b["parent"] not in names:
            raise ValueError(f"bone {b['name']}: parent {b['parent']!r} does not exist")
        if not isinstance(b.get("cubes", []), list):
            raise ValueError(f"bone {b['name']}: 'cubes' must be a list")
        _upgrade_tag(b, f"bone {b['name']}")
        for i, c in enumerate(b.get("cubes", [])):
            c["from"] = _vec(c.get("from"), 3, f"{b['name']}.cubes[{i}].from")
            c["size"] = _vec(c.get("size"), 3, f"{b['name']}.cubes[{i}].size")
            if min(c["size"]) <= 0:
                raise ValueError(f"{b['name']}.cubes[{i}]: size must be positive")
            c["inflate"] = float(c.get("inflate", 0))
            if c.get("color") not in pal:
                raise ValueError(f"{b['name']}.cubes[{i}]: colour {c.get('color')!r} is not in the palette")
            _upgrade_tag(c, f"{b['name']}.cubes[{i}]")
            # a cube of its own angle (a fanned pauldron lame, a halo segment, a tilted plate): in the game this is
            # a one-cube child ModelPart with that rotation, so nothing drawn here is beyond the engine
            c["rotation"] = _vec(c.get("rotation", [0, 0, 0]), 3, f"{b['name']}.cubes[{i}].rotation")
            c["pivot"] = _vec(c.get("pivot") or [c["from"][a] + c["size"][a] / 2 for a in range(3)], 3,
                              f"{b['name']}.cubes[{i}].pivot")
    # the parent chain must end at the root (no cycles)
    parent = {b["name"]: b.get("parent") for b in bones}
    for n in names:
        seen, cur = set(), n
        while cur is not None:
            if cur in seen:
                raise ValueError(f"bone {n}: parent chain loops")
            seen.add(cur)
            cur = parent[cur]
    m["pose"] = m.get("pose", {}) or {}
    for n, r in m["pose"].items():
        if n not in names:
            raise ValueError(f"pose: bone {n!r} does not exist")
        m["pose"][n] = _vec(r, 3, f"pose.{n}")
    # every cube's effective upgrade tag: its own, else the nearest tagged ancestor bone's (resolved here once, so
    # the renderer and the check read one field)
    by_name = {b["name"]: b for b in bones}
    for b in bones:
        tag, cur = None, b
        while cur is not None and tag is None:
            tag = cur.get("upgrade")
            cur = by_name.get(cur.get("parent")) if cur.get("parent") else None
        for c in b.get("cubes", []):
            c["upgrade"] = c.get("upgrade") or tag
    m["upgrades"] = sorted({c["upgrade"] for b in bones for c in b.get("cubes", []) if c["upgrade"]})
    return m


def _upgrade_tag(node, what):
    """Optional "upgrade": "<name>" on a cube or bone: the part only shows once that upgrade is researched."""
    tag = node.get("upgrade")
    if tag is not None and (not isinstance(tag, str) or not tag.strip()):
        raise ValueError(f"{what}: 'upgrade' must be a non-empty string naming the upgrade")
    node["upgrade"] = tag.strip() if isinstance(tag, str) else None


def bbox_px(m):
    """Unrotated bounding box of all cubes (inflate included), in pixels: (lo, hi)."""
    lo, hi = [1e9] * 3, [-1e9] * 3
    for b in m["bones"]:
        for c in b.get("cubes", []):
            for a in range(3):
                lo[a] = min(lo[a], c["from"][a] - c["inflate"])
                hi[a] = max(hi[a], c["from"][a] + c["size"][a] + c["inflate"])
    return lo, hi


def check(paths):
    ok = True
    for p in paths:
        try:
            m = load_model(p)
        except (ValueError, OSError, json.JSONDecodeError) as e:
            print(f"FAIL {p}: {e}")
            ok = False
            continue
        lo, hi = bbox_px(m)
        s = m["scale_px_per_block"]
        nb, nc = len(m["bones"]), sum(len(b.get("cubes", [])) for b in m["bones"])
        nu = sum(1 for b in m["bones"] for c in b.get("cubes", []) if c["upgrade"])
        nr = sum(1 for b in m["bones"] for c in b.get("cubes", []) if any(c["rotation"]))
        up = f"; upgrades: {', '.join(m['upgrades'])} ({nu} tagged cubes)" if m["upgrades"] else ""
        up += f"; {nr} rotated cubes" if nr else ""
        print(f"OK   {p}: {m['name']} ({m['faction']}, {m['role']}): {nb} bones, {nc} cubes, "
              f"height {(hi[1] - lo[1]) / s:.2f} width {(hi[0] - lo[0]) / s:.2f} depth {(hi[2] - lo[2]) / s:.2f} blocks"
              f" (x {lo[0]:g}..{hi[0]:g}, y {lo[1]:g}..{hi[1]:g}, z {lo[2]:g}..{hi[2]:g} px){up}")
    return ok


# ---------------------------------------------------------------- Blender -----------------------------------------
def hex_linear(h):
    """'#rrggbb' (sRGB) -> linear RGBA for Blender colour sockets."""
    out = []
    for i in (1, 3, 5):
        c = int(h[i:i + 2], 16) / 255.0
        out.append(c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4)
    return (*out, 1.0)


def mc_to_bl(v, s):
    return (v[0] / s, -v[2] / s, v[1] / s)


def link(obj):
    bpy.context.scene.collection.objects.link(obj)
    return obj


# names of palette colours that are metal: a little sheen (metallic + lower roughness) instead of chalk
METAL_HINTS = ("plate", "gold", "steel", "visor", "iron", "brass", "silver", "bronze", "copper")


def painted_shading(mat, bsdf, rgba, name):
    """The painted-texture look the game's textures will carry, so a sheet is judged the way the unit will look
    (lovish, Oct 14 18:23: 'smoothing out the mesh a bit so parts of the armor blend'): every cube gets a soft
    bottom-to-top gradient (darker underneath, lighter on top, like hand-painted Minecraft textures), its edges are
    rounded in the shading (Cycles Bevel normal - the edge highlight a painted texture draws as a lighter rim), and
    ambient occlusion darkens the seams where plates overlap, which is what makes separate parts read as one
    sculpted form. Metal colours get some sheen. Cycles only for the bevel; EEVEE/Workbench ignore what they can't do."""
    nt = mat.node_tree
    if any(h in name for h in METAL_HINTS):
        bsdf.inputs["Metallic"].default_value = 0.35
        bsdf.inputs["Roughness"].default_value = 0.42
    bev = nt.nodes.new("ShaderNodeBevel")
    bev.inputs["Radius"].default_value = 0.012          # ~0.2 px at 16 px per block
    nt.links.new(bev.outputs["Normal"], bsdf.inputs["Normal"])
    tc = nt.nodes.new("ShaderNodeTexCoord")
    sep = nt.nodes.new("ShaderNodeSeparateXYZ")
    nt.links.new(tc.outputs["Generated"], sep.inputs[0])
    grad = nt.nodes.new("ShaderNodeMapRange")
    grad.inputs["From Min"].default_value = 0.0
    grad.inputs["From Max"].default_value = 1.0
    grad.inputs["To Min"].default_value = 0.80
    grad.inputs["To Max"].default_value = 1.05
    nt.links.new(sep.outputs["Z"], grad.inputs["Value"])
    tint = nt.nodes.new("ShaderNodeVectorMath")
    tint.operation = "SCALE"
    tint.inputs[0].default_value = (rgba[0], rgba[1], rgba[2])
    nt.links.new(grad.outputs["Result"], tint.inputs["Scale"])
    ao = nt.nodes.new("ShaderNodeAmbientOcclusion")
    ao.inputs["Distance"].default_value = 0.06
    occl = nt.nodes.new("ShaderNodeMapRange")
    occl.inputs["From Min"].default_value = 0.0
    occl.inputs["From Max"].default_value = 1.0
    occl.inputs["To Min"].default_value = 0.55
    occl.inputs["To Max"].default_value = 1.0
    nt.links.new(ao.outputs["AO"], occl.inputs["Value"])
    shade = nt.nodes.new("ShaderNodeVectorMath")
    shade.operation = "SCALE"
    nt.links.new(tint.outputs["Vector"], shade.inputs[0])
    nt.links.new(occl.outputs["Result"], shade.inputs["Scale"])
    nt.links.new(shade.outputs["Vector"], bsdf.inputs["Base Color"])


def make_material(name, hexcol, emissive):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    rgba = hex_linear(hexcol)
    mat.diffuse_color = rgba  # what Workbench draws
    bsdf = mat.node_tree.nodes.get("Principled BSDF")
    bsdf.inputs["Base Color"].default_value = rgba
    bsdf.inputs["Roughness"].default_value = 0.65
    if not emissive:
        try:
            painted_shading(mat, bsdf, rgba, name.lower())
        except Exception as e:   # a node API change must cost the shading, never the sheet
            print("painted shading skipped for", name, ":", e)
    if emissive:
        for key in ("Emission Color", "Emission"):  # 4.x name, then the 3.x name
            if key in bsdf.inputs:
                bsdf.inputs[key].default_value = rgba
                break
        bsdf.inputs["Emission Strength"].default_value = 3.0
    return mat


def make_box(name, lo, hi, mat):
    """One mesh cube spanning lo..hi (Blender coordinates), flat shaded."""
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    verts = [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0),
             (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]
    faces = [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], faces)
    me.update()
    me.materials.append(mat)
    return link(bpy.data.objects.new(name, me))


UPGRADE_BOXES = []   # the tagged cubes' objects, hidden in every base render and shown for upgraded.png
CUBE_PIVOTS = []     # (empty, rotation) per rotated cube, rotated in pass 3 like a bone


def build_model(m):
    """Empties per bone (parented, pivots honoured), one cube per DSL cube. Returns the root empty."""
    s = m["scale_px_per_block"]
    mats = {k: make_material("mat_" + k, v, k in m["emissive"]) for k, v in m["palette"].items()}
    root = link(bpy.data.objects.new("model_root", None))
    empties = {}
    UPGRADE_BOXES.clear()
    CUBE_PIVOTS.clear()
    for b in m["bones"]:  # pass 1: empties at their pivots, world space, no rotation yet
        e = link(bpy.data.objects.new("bone_" + b["name"], None))
        e.empty_display_size = 0.1
        e.location = mc_to_bl(b["pivot"], s)
        empties[b["name"]] = e
    for b in m["bones"]:  # pass 2: hierarchy. The inverse keeps every child where it is in world space.
        e = empties[b["name"]]
        parent = empties[b["parent"]] if b.get("parent") else root
        e.parent = parent
        e.matrix_parent_inverse = Matrix.Translation(parent.location).inverted()
        for i, c in enumerate(b.get("cubes", [])):
            lo = [c["from"][a] - c["inflate"] for a in range(3)]
            hi = [c["from"][a] + c["size"][a] + c["inflate"] for a in range(3)]
            blo, bhi = mc_to_bl(lo, s), mc_to_bl(hi, s)
            box = make_box(f"{b['name']}_{i}", [min(a, b2) for a, b2 in zip(blo, bhi)],
                           [max(a, b2) for a, b2 in zip(blo, bhi)], mats[c["color"]])
            holder = e
            if any(c["rotation"]):
                # the cube's own pivot: an empty under the bone, rotated in pass 3 like a bone (a one-cube child
                # ModelPart in the game)
                holder = link(bpy.data.objects.new(f"pivot_{b['name']}_{i}", None))
                holder.empty_display_size = 0.05
                holder.location = mc_to_bl(c["pivot"], s)
                holder.parent = e
                holder.matrix_parent_inverse = Matrix.Translation(e.location).inverted()
                CUBE_PIVOTS.append((holder, c["rotation"]))
            box.parent = holder
            box.matrix_parent_inverse = Matrix.Translation(holder.location).inverted()
            if c["upgrade"]:
                box.hide_render = True   # base renders: the unit before research
                UPGRADE_BOXES.append(box)
    for b in m["bones"]:  # pass 3: rotation + idle pose, about the pivot
        r = [b["rotation"][a] + m["pose"].get(b["name"], [0, 0, 0])[a] for a in range(3)]
        e = empties[b["name"]]
        e.rotation_mode = "XZY"
        e.rotation_euler = (math.radians(r[0]), math.radians(-r[2]), math.radians(r[1]))
    for holder, r in CUBE_PIVOTS:   # the rotated cubes, same Euler order as a bone (x, then y, then z)
        holder.rotation_mode = "XZY"
        holder.rotation_euler = (math.radians(r[0]), math.radians(-r[2]), math.radians(r[1]))
    return root


def track(obj, target):
    con = obj.constraints.new("TRACK_TO")
    con.target, con.track_axis, con.up_axis = target, "TRACK_NEGATIVE_Z", "UP_Y"


def add_light(name, pos, target, energy, color=(1, 1, 1), size=3.0):
    ld = bpy.data.lights.new(name, "AREA")
    ld.energy, ld.size, ld.color = energy, size, color
    ob = link(bpy.data.objects.new(name, ld))
    ob.location = pos
    track(ob, target)
    return ob


def add_camera(name, pos, target, fov_deg=30.0, ortho_scale=None):
    cd = bpy.data.cameras.new(name)
    if ortho_scale:
        cd.type, cd.ortho_scale = "ORTHO", ortho_scale
    else:
        cd.angle = math.radians(fov_deg)
    ob = link(bpy.data.objects.new(name, cd))
    ob.location = pos
    track(ob, target)
    return ob


def unit(v, d):
    n = math.sqrt(sum(c * c for c in v))
    return [c / n * d for c in v]


def setup_scene(m, out, size):
    """Empty scene, the model, a ground plane, three-point light with a faction rim. Returns (root, cams, extras)."""
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.resolution_x = sc.render.resolution_y = size
    sc.render.resolution_percentage = 100
    sc.render.image_settings.file_format = "PNG"
    sc.render.image_settings.color_mode = "RGBA"
    sc.view_settings.view_transform = "Standard"  # keep the palette as drawn, black stays black
    if sc.world is None:
        sc.world = bpy.data.worlds.new("World")
    sc.world.use_nodes = True
    bg = sc.world.node_tree.nodes.get("Background")
    bg.inputs[0].default_value = (0.72, 0.72, 0.74, 1)
    bg.inputs[1].default_value = 0.5
    sc.world.color = (1, 1, 1)

    root = build_model(m)
    s = m["scale_px_per_block"]
    lo, hi = bbox_px(m)
    c_mc = [(lo[a] + hi[a]) / 2 for a in range(3)]
    center = mc_to_bl(c_mc, s)
    ext = [(hi[a] - lo[a]) / s for a in range(3)]  # width, height, depth in blocks
    radius = 0.5 * math.sqrt(sum(e * e for e in ext))
    target = link(bpy.data.objects.new("target", None))
    target.location = center
    ground = make_box("ground", (-30, -30, -0.02), (30, 30, 0), make_material("mat_ground", "#cfcac2", False))

    pal = m["palette"]
    rim = hex_linear(pal.get("rim") or pal.get("tabard") or "#ffffff")[:3]
    d = radius * 3.0
    light_pos = lambda v: [center[i] + unit(v, d)[i] for i in range(3)]  # Blender frame: +Y is the front
    add_light("key", light_pos((-1.0, 1.2, 1.4)), target, 900 * d * d / 36, size=3.0)
    add_light("fill", light_pos((1.6, 1.0, 0.6)), target, 250 * d * d / 36, size=4.0)
    add_light("rim", light_pos((0.6, -1.2, 1.0)), target, 700 * d * d / 36, color=rim, size=1.5)

    fov = 30.0
    dist = radius / math.tan(math.radians(fov / 2)) * 1.12
    cam_pos = lambda v: [center[i] + unit(v, dist)[i] for i in range(3)]
    cams = {
        "front": add_camera("cam_front", cam_pos((0, 1, 0.18)), target, fov),
        "side": add_camera("cam_side", cam_pos((1, 0, 0.18)), target, fov),
        "quarter": add_camera("cam_quarter", cam_pos((1, 1, 0.5)), target, fov),
        "silhouette": add_camera("cam_silhouette", cam_pos((0, 1, 0)), target, ortho_scale=max(ext[0], ext[1]) * 1.1),
    }
    return root, cams, ground


def use_cycles(sc, samples):
    sc.render.engine = "CYCLES"
    sc.cycles.device = "CPU"
    sc.cycles.samples = samples
    sc.cycles.use_adaptive_sampling = True
    sc.cycles.use_denoising = True
    sc.cycles.denoiser = "OPENIMAGEDENOISE"
    sc.render.film_transparent = False


def use_workbench(sc, silhouette):
    sc.render.engine = "BLENDER_WORKBENCH"
    sh = sc.display.shading
    sc.display.render_aa = "8"
    if silhouette:
        sh.light, sh.color_type, sh.single_color = "FLAT", "SINGLE", (0, 0, 0)
        sh.show_shadows = sh.show_cavity = sh.show_object_outline = False
        sc.render.film_transparent = True
    else:
        sh.light, sh.color_type = "STUDIO", "MATERIAL"
        sh.show_shadows, sh.show_cavity = True, False
        sc.render.film_transparent = False


def use_eevee(sc):
    ids = [e.identifier for e in bpy.types.RenderSettings.bl_rna.properties["engine"].enum_items]
    for cand in ("BLENDER_EEVEE_NEXT", "BLENDER_EEVEE"):  # 4.2 calls it NEXT, 4.3+ dropped the suffix
        if cand in ids:
            sc.render.engine = cand
            break
    else:
        raise SystemExit("no EEVEE engine in this Blender: " + ", ".join(ids))
    sc.eevee.taa_render_samples = 16
    sc.render.film_transparent = False


def render_to(sc, cam, path):
    sc.camera = cam
    sc.render.filepath = path
    bpy.ops.render.render(write_still=True)
    print("wrote", path)


def main_blender(args):
    m = load_model(args.model)
    os.makedirs(args.out, exist_ok=True)
    only = set(args.only.split(","))
    root, cams, ground = setup_scene(m, args.out, args.size)
    sc = bpy.context.scene
    if "stills" in only:
        use_cycles(sc, args.samples)
        for name in ("front", "side", "quarter"):
            render_to(sc, cams[name], os.path.join(args.out, name + ".png"))
        if UPGRADE_BOXES:   # the researched look: every tagged part shown, same camera as the three-quarter still
            for box in UPGRADE_BOXES:
                box.hide_render = False
            render_to(sc, cams["quarter"], os.path.join(args.out, "upgraded.png"))
            for box in UPGRADE_BOXES:
                box.hide_render = True
    if "silhouette" in only:
        ground.hide_render = True
        use_workbench(sc, silhouette=True)
        render_to(sc, cams["silhouette"], os.path.join(args.out, "silhouette.png"))
        ground.hide_render = False
    if "turntable" in only:
        if args.engine == "eevee":
            use_eevee(sc)
        else:
            use_workbench(sc, silhouette=False)
        sc.render.resolution_x = sc.render.resolution_y = args.size // 2
        sc.render.use_persistent_data = True
        for i in range(args.frames):
            root.rotation_euler = (0, 0, math.radians(360.0 * i / args.frames))
            render_to(sc, cams["quarter"], os.path.join(args.out, "turntable", f"frame_{i:03d}.png"))
    with open(os.path.join(args.out, "model.json"), "w") as f:
        json.dump(m, f, indent=1)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", nargs="+", metavar="JSON", help="validate these files (no Blender needed)")
    ap.add_argument("--model", help="DSL json to render")
    ap.add_argument("--out", help="output folder")
    ap.add_argument("--samples", type=int, default=48, help="Cycles samples for the stills")
    ap.add_argument("--size", type=int, default=1024, help="still size in px (turntable frames are half)")
    ap.add_argument("--frames", type=int, default=24)
    ap.add_argument("--only", default="stills,silhouette,turntable", help="comma list of passes to run")
    ap.add_argument("--engine", choices=("eevee", "workbench"), default="workbench", help="turntable engine")
    args = ap.parse_args(argv)
    if args.check:
        paths = [p for pat in args.check for p in sorted(glob.glob(pat))] or args.check
        sys.exit(0 if check(paths) else 1)
    if not (args.model and args.out):
        ap.error("--model and --out are required to render (or use --check)")
    if bpy is None:
        ap.error("rendering needs Blender: blender -b -P tools/render_concept.py -- --model ... --out ...")
    main_blender(args)


if __name__ == "__main__":
    main()
