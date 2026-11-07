#!/usr/bin/env python3
"""The Light Grid's T1 worker, the BUILDER DRONE (design/ai_faction.md section 2, PR 379: "hover constructor; draws
grid patches"; lovish, Oct 28: "drones and robots", "kinda like a Tron Legacy faction with AI robots and mecha";
"glossy black and dark-grey bodies, clean hard geometry, thin glowing light lines on every edge, light as the only
ornament" - inspired by, never copied from: no logo, no recognisable design).

    python3 tools/gen_builder_drone.py           # writes design/models/lumen_builder_drone.json
    python3 tools/dsl_to_game.py design/models/lumen_builder_drone.json --class BuilderDroneModel \\
        --texture builder_drone_unit --team team_paint,line_glow

The body, read from the RTS camera first (BAT readable-at-any-zoom): a faceted KITE-shaped hull pointing where it
goes (a disc would read as a target), outlined by one continuous light line in the owner's colour along its shoulder
- from above that outline IS the unit - a raised deck with a team chevron and a light spine, a V-tail of two canted
fins (the second shape that says "craft" in silhouette), and a small nanolathe turret on the nose with one visor
slit (one slit, never two: two dark slits read as a face). Close up: two folded manipulator arms under the nose
with welding tips, a hover ring of light under the belly, recessed thrust vents, panel insets and seams. It hovers
with its hull bottom 10 px (0.6 block) over the ground, so its own shadow under it says "hover" at every zoom.

Rig (tools/../unit/modelling/animations/Hover.java reads these names): `body` the hull (the living army flinches it,
the hover death drops it - DeathFall#poseHover), `head` the turret (it looks at the job), `arm_l` / `arm_r` the
manipulators (HoverMotion swings them about x), `thrust` the hover ring (it sputters out on death). The nanolathe's
muzzle sits where BarFx.nano starts the build beam (1.07 blocks up, 0.45 ahead of the centre: y 17.2, z -7.2).

Materials (tools/materials.py): `carbon` the glossy composite (no grain, lit bevels), `carbon_mid` its dark-grey
panels, `joint_steel` the actuators (plate), `vent` the recesses, `team_paint` the owner's chevron (cloth mask),
`line_glow` the owner's light (a TEAM LIGHT: emissive + --team, GeneratedBodies.teamGlow), `lens_glow` the white
visor and welding tips.
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from gen_models import OUT, Model, ngon_prism, octagon_pole, slab  # noqa: E402

PALETTE = {
    "carbon": "#15171b",
    "carbon_mid": "#2a2f37",
    "joint_steel": "#6a717b",
    "vent": "#0a0b0d",
    "team_paint": "#3a6fd1",
    "line_glow": "#1c2026",   # the base paint under the team light: dark, so the owner's colour reads pure
    "lens_glow": "#f4fbff",
}
EMISSIVE = ["line_glow", "lens_glow"]

HOVER = 10.0          # the hull's bottom (the belly) above the ground at rest, px
HALF_WIDTH = 6.4      # the hull's half width at the shoulders, px (the hover death's crash edge)
# the plan of the main hull, the unit's LEFT half (x >= 0), nose (-z) to tail; mirrored for the right
PLAN = [(2.6, -8.8), (5.8, -3.4), (6.4, 1.0), (6.2, 4.2), (3.9, 7.6)]
MAIN_TOP, DECK_TOP = 13.6, 14.9
# BarFx.nano starts the build beam 1.07 blocks up, 0.45 ahead (y 17.2, z -7.2): the lathe's muzzle sits just under it
MUZZLE = (0.0, 16.6, -7.2)


def polygon(half):
    """The whole plan, clockwise seen from above: the left half, then the right half mirrored, nose first."""
    return [(x, z) for x, z in half] + [(-x, z) for x, z in reversed(half)]


def inset(poly, d):
    """The convex polygon moved in by d on every edge (each vertex along its bisector)."""
    n = len(poly)
    out = []
    cx = sum(p[0] for p in poly) / n
    cz = sum(p[1] for p in poly) / n
    for i in range(n):
        a, b, c = poly[i - 1], poly[i], poly[(i + 1) % n]
        n1 = _normal(a, b, (cx, cz))
        n2 = _normal(b, c, (cx, cz))
        bx, bz = n1[0] + n2[0], n1[1] + n2[1]
        L = math.hypot(bx, bz)
        bx, bz = bx / L, bz / L
        cos_half = bx * n1[0] + bz * n1[1]
        k = d / max(0.2, cos_half)
        out.append((b[0] - bx * k, b[1] - bz * k))
    return out


def _normal(a, b, centre):
    """The outward unit normal of edge a-b (in plan)."""
    ex, ez = b[0] - a[0], b[1] - a[1]
    L = math.hypot(ex, ez)
    nx, nz = ez / L, -ex / L
    mx, mz = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
    if (mx - centre[0]) * nx + (mz - centre[1]) * nz < 0:
        nx, nz = -nx, -nz
    return nx, nz


def edges(poly):
    n = len(poly)
    cx = sum(p[0] for p in poly) / n
    cz = sum(p[1] for p in poly) / n
    for i in range(n):
        a, b = poly[i], poly[(i + 1) % n]
        yield a, b, _normal(a, b, (cx, cz))


def half_width_at(poly, z):
    """The polygon's half width (x) at depth z (it is symmetric about x = 0)."""
    best = 0.0
    for a, b, _n in edges(poly):
        if a[0] < 0 or b[0] < 0:
            continue
        z0, z1 = sorted((a[1], b[1]))
        if z0 - 1e-6 <= z <= z1 + 1e-6 and abs(b[1] - a[1]) > 1e-6:
            t = (z - a[1]) / (b[1] - a[1])
            best = max(best, a[0] + (b[0] - a[0]) * t)
    return best


def band(bone, poly, y0, y1, color, wall=1.6, step=1.6):
    """A faceted prism of plan `poly` from y0 to y1: one wall slab on every edge (its outer face on the edge, so the
    facets meet clean at every convex corner) round a core of z slices."""
    h = y1 - y0
    for a, b, (nx, nz) in edges(poly):
        L = math.hypot(b[0] - a[0], b[1] - a[1])
        cx = (a[0] + b[0]) / 2 - nx * wall / 2
        cz = (a[1] + b[1]) / 2 - nz * wall / 2
        slab(bone, (cx, (y0 + y1) / 2, cz), (b[0] - a[0], 0, b[1] - a[1]), (nx, 0, nz), (wall, h, L), color)
    zs = [p[1] for p in poly]
    z, zmax = min(zs), max(zs)
    while z < zmax - 1e-6:
        z1 = min(zmax, z + step)
        hw = min(half_width_at(poly, z + 0.01), half_width_at(poly, z1 - 0.01)) - wall * 0.6
        if hw > 0.3:
            bone.cube([-hw, y0, z], [2 * hw, h, z1 - z], color)
        z = z1


def edge_lines(bone, poly, y, color, t=0.62, out=0.12, skip=()):
    """A light line along every edge of `poly` at height y, hugging the outside of the wall (team light)."""
    for i, (a, b, (nx, nz)) in enumerate(edges(poly)):
        if i in skip:
            continue
        L = math.hypot(b[0] - a[0], b[1] - a[1]) + t * 0.6
        cx = (a[0] + b[0]) / 2 + nx * (out - t / 2 + 0.05)
        cz = (a[1] + b[1]) / 2 + nz * (out - t / 2 + 0.05)
        slab(bone, (cx, y, cz), (b[0] - a[0], 0, b[1] - a[1]), (nx, 0, nz), (t, t, L), color)


def _norm(v):
    L = math.sqrt(sum(c * c for c in v))
    return tuple(c / L for c in v)


def _cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def builder_drone():
    m = Model("Builder Drone", "lumen", "constructor (T1 worker)", PALETTE, EMISSIVE)
    plan = polygon(PLAN)
    body = m.bone("body", [0, 12.4, 0])

    # ---- the hull: a low faceted kite - belly, main band, a stepped deck (one hard chamfer read from above) ----
    belly = inset(plan, 1.7)
    deck = inset(plan, 1.5)
    band(body, belly, HOVER, 10.7, "carbon_mid", wall=1.2)
    band(body, plan, 10.7, MAIN_TOP, "carbon")
    band(body, deck, MAIN_TOP, DECK_TOP, "carbon", wall=1.4)
    # the shoulder line: ONE continuous light outline round the hull - what the RTS camera reads as the unit
    edge_lines(body, plan, MAIN_TOP, "line_glow", t=0.7)
    # the belly line (the side and 3/4 views): front and flanks only, the tail carries its vents
    edge_lines(body, belly, 10.45, "line_glow", t=0.5, skip=(4, 5))
    # the rear deck: a raised spine plate behind the turret with a light spine, the owner's chevron round it
    body.cube([-1.5, DECK_TOP, -0.6], [3.0, 0.6, 6.6], "carbon_mid")
    body.cube([-0.35, DECK_TOP + 0.6, -0.2], [0.7, 0.3, 5.8], "line_glow")
    for side in (1, -1):
        # the chevron's arms, flat on the deck, pointing at the nose
        slab(body, (side * 2.25, DECK_TOP + 0.12, 2.0), (side * 2.5, 0, 3.6), (1, 0, 0), (1.3, 0.3, 4.4),
             "team_paint")
        # recessed side vents on the main band, rear half (three slats each)
        for k in range(3):
            body.cube([side * 6.3 - 0.25, 11.2 + k * 0.75, 0.9], [0.5, 0.35, 2.4], "vent")
        # a panel seam across each flank of the deck
        body.cube([side * 3.6 - 0.9, DECK_TOP - 0.05, -2.6], [1.8, 0.15, 0.25], "vent")

    # ---- the V-tail: two small fins canted out at ~45 degrees, light on their leading edges ----
    for side in (1, -1):
        root = (side * 2.1, DECK_TOP - 0.3, 5.0)
        span = _norm((side * 0.72, 0.62, 0.30))
        normal = _norm((side * 0.65, -0.76, 0.0))
        chord = _norm(_cross(span, normal))
        if chord[2] < 0:
            chord = tuple(-c for c in chord)
        L = 3.4
        mid = tuple(r + c * L / 2 for r, c in zip(root, span))
        slab(body, mid, span, normal, (0.5, 2.6, L), "carbon")
        lead = tuple(mc - cc * 1.2 for mc, cc in zip(mid, chord))
        slab(body, lead, span, normal, (0.62, 0.45, L + 0.2), "line_glow")
        body.box_at((side * 2.3, DECK_TOP - 0.1, 5.2), (1.4, 0.7, 2.0), "joint_steel")    # the fin's root fairing

    # ---- the tail: twin thrust vents with a light bar between them ----
    for side in (1, -1):
        body.cube([side * 1.9 - 1.0, 11.0, 7.0], [2.0, 2.0, 0.8], "vent")
        body.cube([side * 1.9 - 1.2, 10.8, 6.9], [2.4, 0.3, 0.9], "joint_steel")
        body.cube([side * 1.9 - 1.2, 13.0, 6.9], [2.4, 0.3, 0.9], "joint_steel")
    body.cube([-0.35, 11.2, 7.45], [0.7, 1.6, 0.35], "line_glow")
    # a short sensor mast on the tail deck
    octagon_pole(body, 0.0, 5.6, DECK_TOP, DECK_TOP + 1.6, 0.55, "joint_steel")
    body.box_at((0.0, DECK_TOP + 1.8, 5.6), (0.8, 0.45, 0.8), "lens_glow")

    # ---- the nanolathe turret (head): a low octagonal pod on the nose deck, one visor slit, the lathe barrel ----
    head = m.bone("head", [0, DECK_TOP, -3.8], parent="body")
    ngon_prism(head, 0.0, -3.6, DECK_TOP - 0.1, DECK_TOP + 0.5, 4.4, "joint_steel", n=8)
    # a low wedge, not a box: 2.4 px tall, its front a steep glacis the visor slit sits in
    top = DECK_TOP + 2.9
    head.cube([-2.2, DECK_TOP + 0.5, -5.2], [4.4, top - DECK_TOP - 0.5, 4.6], "carbon")
    for side in (1, -1):    # the pod's long top edges chamfered (it reads turned, not boxed)
        slab(head, (side * 2.05, top - 0.15, -3.0), (0, 0, 1), (side * 1.0, 1.0, 0), (0.9, 0.9, 4.6), "carbon")
    # the glacis: one plate from the pod's top edge down to the barrel, leaned back 50 degrees
    slab(head, (0.0, DECK_TOP + 1.75, -5.7), (1, 0, 0), (0, 0.64, -0.77), (1.2, 2.4, 4.0), "carbon")
    head.cube([-1.6, top, -4.8], [3.2, 0.3, 3.6], "carbon_mid")
    slab(head, (0.0, DECK_TOP + 2.0, -6.08), (1, 0, 0), (0, 0.64, -0.77), (0.3, 0.42, 3.0), "lens_glow")  # the slit
    # the lathe barrel along -z; its muzzle is the build beam's start
    bz0, bz1 = -6.2, MUZZLE[2] + 0.2
    for across in ((1, 0, 0), (1, 1, 0)):
        slab(head, (0.0, MUZZLE[1], (bz0 + bz1) / 2), (0, 0, -1), across, (1.2, 1.2, bz0 - bz1), "joint_steel")
    head.box_at((0.0, MUZZLE[1], MUZZLE[2]), (1.0, 1.0, 0.4), "line_glow")      # the muzzle ring, the owner's light
    head.box_at((0.0, MUZZLE[1], MUZZLE[2] - 0.15), (0.5, 0.5, 0.3), "lens_glow")

    # ---- the manipulators: folded forward along the belly, welding tips ahead of the nose ----
    for side, name in ((1, "arm_l"), (-1, "arm_r")):
        shoulder = (side * 4.6, 10.6, -2.6)
        elbow = (side * 4.4, 9.5, -6.2)
        tip = (side * 3.5, 9.7, -9.1)
        arm = m.bone(name, list(shoulder), parent="body")
        arm.box_at(shoulder, (1.5, 1.6, 1.6), "joint_steel")
        arm.box_at(shoulder, (1.6, 1.0, 1.0), "carbon_mid")
        up = tuple(e - sh for e, sh in zip(elbow, shoulder))
        mid = tuple((sh + e) / 2 for sh, e in zip(shoulder, elbow))
        slab(arm, mid, up, (1, 0, 0), (1.1, 1.2, math.dist(shoulder, elbow) + 0.4), "carbon")
        slab(arm, (mid[0] + side * 0.58, mid[1] + 0.1, mid[2]), up, (1, 0, 0), (0.3, 0.4, math.dist(shoulder, elbow)),
             "line_glow")
        arm.box_at(elbow, (1.3, 1.3, 1.3), "joint_steel")
        fore = tuple(t - e for e, t in zip(elbow, tip))
        fm = tuple((e + t) / 2 for e, t in zip(elbow, tip))
        slab(arm, fm, fore, (1, 0, 0), (0.95, 1.0, math.dist(elbow, tip) + 0.3), "carbon")
        for k in (1, -1):    # two claw prongs round the welding tip
            slab(arm, (tip[0] + k * 0.42, tip[1] - 0.05, tip[2] - 0.55), (k * 0.35, -0.15, -1), (1, 0, 0),
                 (0.32, 0.45, 1.5), "joint_steel")
        arm.box_at(tip, (0.55, 0.55, 0.55), "lens_glow")

    # ---- the hover ring under the belly (the owner's light) and four emitter pads ----
    thrust = m.bone("thrust", [0, HOVER - 0.3, 0.6], parent="body")
    n, r = 12, 3.0
    seg = 2 * r * math.tan(math.pi / n) + 0.25
    for k in range(n):
        a = 2 * math.pi * k / n
        cx, cz = r * math.cos(a), 0.6 + r * math.sin(a)
        thrust.box_at((cx, HOVER - 0.35, cz), (0.6, 0.5, seg), "line_glow",
                      rotation=[0, -math.degrees(a), 0], pivot=[cx, HOVER - 0.35, cz])
    thrust.box_at((0.0, HOVER - 0.3, 0.6), (3.6, 0.4, 3.6), "vent")
    for side in (1, -1):
        for z in (-4.6, 4.2):
            body.box_at((side * 3.4, HOVER - 0.2, z), (1.4, 0.4, 1.4), "joint_steel")
            body.box_at((side * 3.4, HOVER - 0.45, z), (0.7, 0.12, 0.7), "lens_glow")
    return m


def main():
    m = builder_drone()
    path = os.path.normpath(os.path.join(OUT, "lumen_builder_drone.json"))
    with open(path, "w") as f:
        json.dump(m.to_json(), f, indent=1)
    nc = sum(len(b.cubes) for b in m.bones.values())
    print(f"wrote {path}: {len(m.bones)} bones, {nc} cubes")


if __name__ == "__main__":
    main()
