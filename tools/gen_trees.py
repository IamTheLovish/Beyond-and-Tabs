#!/usr/bin/env python3
"""Our own map trees (Lane B, lovish Oct 21 2026: "tree shapes can be modified to fit the game") - original tree
templates to replace the vanilla TreeFeatures that maps/MapBuilder.decorate() places, which read as cube blobs from the
RTS camera (design/ART-FAULTS.md "blob canopies / stiff trunks").

Each tree: a trunk that leans and tapers in steps (a root flare at the foot), branches that leave the trunk at angles
as horizontal logs, and the crown built from SEVERAL leaf clumps at the branch ends and the top - so light falls between
the clumps and the silhouette breaks up, instead of one ellipsoid of leaves. Within design/maps.md's map rules: short
(trees are reclaim lanes, never a canopy over a base or a main lane), logs + leaves only so resources/TreeClearing still
fells them, and every leaf within 6 of a log with its `distance` state written (persistent=false): nothing decays, and
nothing floats once the logs are felled.

    python3 tools/gen_trees.py      # writes design/trees/<species>_<n>.nbt and design/trees/tree_sampler_a/b.nbt

Deterministic (fixed seeds). Templates leave every unset position as structure void, so placing one never erases the
ground around it. y = 0 is the first block above the ground - except the bare trees (bare_oak, dead_tree, giant_bare,
r23): their y = 0 is the ground layer itself (a soil ring and surface roots that replace the grass), so they are placed
with their origin ON the surface block. bare_sampler (80 x 31 x 44) holds all 8 bare trees; small_bare_sampler
(40 x 16 x 24, r24) the 6 small ones only, for photo runners that do not load the big pad's chunks.
r25: dead trees are a grey acacia_wood pole on a dark oak foot (both wood for ResourceSources), one leaning
trunk with stubby limbs and a snag top, a mossy fallen log beside it; bare oaks fork at the crown; no twig hooks.
r27 (oak pilot, critic r33 trees 3/10): the oak is rebuilt as 4-6 lumpy lobes on face-joined dark oak limbs, three
forms (spread, twin, tall; old = a 2x2 stump splitting into two leaders), three leaf tones per lobe (oak()).
r28 (critic r34: lime tops, the tree vanishes into the lawn): the oak crown's colour only, shapes as r27 - no azalea,
a dark oak body, oak on the sunlit patches' cores, spruce under (oak_tones()).
r29 (critic r35b: black-green tops, V too low, sat too high): the crown's top skin is birch sun patches in spruce -
the two leaves with their own greyer colours - so it is lighter AND less saturated; sides dark oak, underside spruce."""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_structures import Structure  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "design", "trees")
SIZE = 15            # template footprint (blocks); the trunk stands at the centre
H = 16               # template height


class Tree:
    def __init__(self, log, leaves):
        self.log, self.leaves = log, leaves
        # limbs use the species' WOOD block (bark on all six faces): a horizontal log shows its end grain, which read as
        # orange squares through the spruce skirts (reviewer, eyes runs 39552449667/39552446960). Wood is in
        # BlockTags.LOGS, so resources/TreeClearing still fells it with the tree.
        self.wood = log.replace("_log", "_wood")
        self.logs = {}            # (x, y, z) -> axis
        self.leaf = set()
        self.plants = {}          # (x, y, z) -> block: the grass / flower skirt at the foot

    def put_log(self, p, axis="y"):
        x, y, z = p
        if 0 <= x < SIZE and 0 <= y < H and 0 <= z < SIZE:
            if self.logs.get((x, y, z)) == "y" and axis != "y":
                return                            # the trunk keeps its upright log where a limb leaves it
            self.logs[(x, y, z)] = axis
            self.leaf.discard((x, y, z))

    def line(self, a, b, horizontal_axis=True, faces=False):
        """Logs from a to b, one block per step; a step that moves sideways lies along that axis (a branch), a step
        up stands upright. faces=True (r27 oak): a step that moves on all three axes gets two corner blocks, so every
        block shares a FACE with the last (the two-axis fill below left those joined at an edge only - 6 of 47 logs
        face-joined to the foot on a sweep of r27 spread oaks)."""
        (x0, y0, z0), (x1, y1, z1) = a, b
        n = max(abs(x1 - x0), abs(y1 - y0), abs(z1 - z0), 1)
        prev = a
        for i in range(n + 1):
            t = i / n
            p = (round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t), round(z0 + (z1 - z0) * t))
            d = (p[0] - prev[0], p[1] - prev[1], p[2] - prev[2])
            if faces and all(d):
                self.put_log((p[0], prev[1], prev[2]), "w")             # over in x, then in z, then up
                self.put_log((p[0], prev[1], p[2]), "w")
            elif sum(1 for c in d if c) > 1:
                # a diagonal step touches the last block only at an edge - the oak's crown read as floating above its
                # stump (reviewer); fill the corner so every step shares a face
                self.put_log((p[0], prev[1], p[2]) if d[1] else (p[0], p[1], prev[2]), "w")
            axis = "y"
            if horizontal_axis and d[1] == 0 and (d[0] or d[2]):
                axis = "w"
            self.put_log(p, axis)
            prev = p
        return prev

    def clump(self, c, rx, ry, rz, rng, holes=0.10, dome=0.0):
        """An irregular leaf clump: an ellipsoid with a ragged edge (noise on the radius) and a few holes. dome > 0
        squashes the underside (a real canopy is a dome over a flatter base): below the centre the vertical radius is
        ry * (1 - dome)."""
        cx, cy, cz = c
        for x in range(int(cx - rx - 1), int(cx + rx + 2)):
            for y in range(int(cy - ry - 1), int(cy + ry + 2)):
                for z in range(int(cz - rz - 1), int(cz + rz + 2)):
                    ryy = ry * (1 - dome) if y < cy else ry
                    d = ((x - cx) / rx) ** 2 + ((y - cy) / max(ryy, 0.5)) ** 2 + ((z - cz) / rz) ** 2
                    if d <= 1.0 + 0.35 * (rng.random() - 0.5) and rng.random() > holes * d:
                        if 0 <= x < SIZE and 0 <= y < H and 0 <= z < SIZE and (x, y, z) not in self.logs:
                            self.leaf.add((x, y, z))

    def round_out(self, rng, chance=0.75, prune=True):
        """Round 9 (top-down review: small clumps read as plus-shaped crosses from the RTS camera): fill the inner
        corner of every L of leaves (an empty cell with two ORTHOGONAL leaf neighbours at its height), so crosses
        become rounded lobes; random, so outlines stay ragged."""
        add = set()
        for (x, y, z) in self.leaf:
            for dx, dz in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
                q = (x + dx, y, z + dz)
                if q in self.leaf or q in self.logs:
                    continue
                if (x + dx, y, z) in self.leaf and (x, y, z + dz) in self.leaf and rng.random() < chance:
                    add.add(q)
        self.leaf |= {q for q in add if 0 <= q[0] < SIZE and 0 <= q[2] < SIZE}
        # round 10 (review: 1-block plus arms and a detached leaf block): drop leaves touching fewer than 2 others
        # round 11 (review: the 1-block arms survived - they had a leaf above or below): count SIDE neighbours only; a
        # leaf needs 2 beside it (or a log beside/below it), repeated to a fixpoint
        H4 = ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1))
        while prune:
            lonely = {p for p in self.leaf
                      if sum((p[0] + dx, p[1], p[2] + dz) in self.leaf for dx, _, dz in H4) < 2
                      and not any((p[0] + dx, p[1] + dy, p[2] + dz) in self.logs for dx, dy, dz in NB)}
            if not lonely:
                break
            self.leaf -= lonely

    def write(self, s, ox=0, oz=0):
        # leaf distance: steps to the nearest log through leaves (vanilla's rule); drop leaves further than 6
        dist = {}
        frontier = [p for p in self.leaf if any((p[0] + dx, p[1] + dy, p[2] + dz) in self.logs
                                              for dx, dy, dz in NB)]
        for p in frontier:
            dist[p] = 1
        k = 0
        while k < len(frontier):
            p = frontier[k]
            k += 1
            for dx, dy, dz in NB:
                q = (p[0] + dx, p[1] + dy, p[2] + dz)
                if q in self.leaf and q not in dist and dist[p] < 6:
                    dist[q] = dist[p] + 1
                    frontier.append(q)
        for (x, y, z), axis in self.logs.items():
            # an upright log whose top is open (nothing above, or leaves) shows its cut end grain - an orange square in
            # the crown from the RTS camera (round 3 stills): those become bark-all-round wood too
            if axis == "y" and self.logs.get((x, y + 1, z)) != "y":
                axis = "w"
            if axis == "w":
                s.set(ox + x, y, oz + z, self.wood, axis="y")
            else:
                s.set(ox + x, y, oz + z, self.log, axis=axis)
        second, mixed = getattr(self, "second", (None, set()))
        kinds = getattr(self, "kinds", {})             # r27: a leaf kind per position (the oak's three-tone lobes)
        for p, d in dist.items():
            b = kinds.get(p) or (second if p in mixed else self.leaves)
            s.set(ox + p[0], p[1], oz + p[2], b, distance=str(d), persistent="false", waterlogged="false")
        sx, sy, sz = s.size
        for (x, y, z), b in self.plants.items():
            if (x, y, z) in self.logs or (x, y, z) in dist or not (0 <= ox + x < sx and 0 <= oz + z < sz and y < sy):
                continue
            if isinstance(b, tuple):
                s.set(ox + x, y, oz + z, b[0], **b[1])
            else:
                s.set(ox + x, y, oz + z, b)
        return len(self.leaf) - len(dist)


NB = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
C = SIZE // 2


def dome_rings(t, c, rng, rings, bite=0.25):
    """Round 16: a crown cap built as stacked discs (dy, radius) over c - each ring narrower than the one below, so the
    top is a dome with 3-4 height steps and the apex above the rim; random bites only on each ring's outer edge."""
    cx, cy, cz = c
    for dy, r in rings:
        y = int(cy) + dy
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            for z in range(int(cz - r - 1), int(cz + r + 2)):
                d = math.hypot(x - cx, z - cz)
                top_ring = (dy, r) == rings[-1]                   # r20: a bite in the top ring read as a pit
                if d > r + 0.25 or (d > r - 0.9 and r > 1 and not top_ring and rng.random() < bite):
                    continue
                if 0 <= x < SIZE and 0 <= y < H and 0 <= z < SIZE and (x, y, z) not in t.logs:
                    t.leaf.add((x, y, z))


def shade(t, rng, second, chance=0.55, rim=None):
    """Round 16 (review: leaves and grass sat at the same value under the shader): leaves with leaves above them - the
    crown's shaded interior and underside - become a darker leaf kind at random, so the dome reads lit-top, dark-under."""
    # r18 (eyes: single top bumps turned the leaf under them into a bright-green spot): shaded = 2 leaves above it
    under = {p for p in t.leaf if (p[0], p[1] + 1, p[2]) in t.leaf and (p[0], p[1] + 2, p[2]) in t.leaf}
    mixed = {p for p in under if rng.random() < chance}
    if rim:                                                     # r17 (review: the camera never sees the buried leaves -
        cx, cz, r = rim                                         # oak top 106 v grass 103): the TOP surface's outer ring
        # r19 (eyes 39586091997: a hard 2.6 rim darkened most of the top - grey camo with green blots): the chance
        # ramps with distance, 0 at r to 0.85 at r + 2.5, so the cap fades lit-to-dark toward the edge
        mixed |= {p for p in t.leaf if rng.random() < min(0.85, max(0.0, (math.hypot(p[0] - cx, p[2] - cz) - r) / 2.5))}
    t.second = (second, mixed)


def skirt(t, rng, radius=2.6, density=0.55, flowers=("poppy", "dandelion", "oxeye_daisy", "azure_bluet")):
    """A ring of grass, fern and a few flowers round the foot (lovish's Tibetan cherry reference, Oct 21): the ground
    under a tree is never bare lawn. 1.20.1 ids ("grass" is the short grass)."""
    for x in range(int(C - radius - 1), int(C + radius + 2)):
        for z in range(int(C - radius - 1), int(C + radius + 2)):
            d = math.hypot(x - C, z - C)
            if 0.9 < d <= radius + 0.6 * (rng.random() - 0.5) and rng.random() < density * (1.2 - d / (radius + 1)):
                r = rng.random()
                t.plants[(x, 0, z)] = rng.choice(flowers) if r < 0.15 else "fern" if r < 0.35 else "grass"


def claw_roots(t, rng, n, top=2, reach=3):
    """Roots that leave the trunk a little above the ground and reach out and DOWN into it like claws (lovish's bonsai
    reference sheet, Oct 21): each root a diagonal run of wood from (trunk, y=top) to the ground 2-3 blocks out."""
    a0 = rng.uniform(0, 2 * math.pi)
    for k in range(n):
        a = a0 + k * 2 * math.pi / n + rng.uniform(-0.3, 0.3)
        r = reach * rng.uniform(0.8, 1.1)
        t.line((C, top, C), (round(C + r * math.cos(a)), 0, round(C + r * math.sin(a))))


def roots(t, rng, n=3, log=None):
    for k in rng.sample(range(4), n):
        dx, dz = ((1, 0), (-1, 0), (0, 1), (0, -1))[k]
        t.put_log((C + dx, 0, C + dz), "w")


OAK_TONES = True    # False = the greybox: one leaf kind, the shapes judged alone (r27 pilot)


def lobe(t, c, r, up, down, rng, ragged=0.4):
    """r27: one crown MASS - a dome over a flatter base with a lumpy outline (three harmonics on the radius: from above a
    rounded lobe with bays, never a disc or a plus) and a stepped top (each column's height jittered, so the top reads
    as clustered leaf masses, not a smooth cap)."""
    cx, cy, cz = c
    ph = [rng.uniform(0, 2 * math.pi) for _ in range(3)]
    amp = (rng.uniform(0.10, 0.20), rng.uniform(0.06, 0.14), rng.uniform(0.0, 0.08))
    R = int(r * 1.45) + 2
    for x in range(int(cx) - R, int(cx) + R + 1):
        for z in range(int(cz) - R, int(cz) + R + 1):
            dx, dz = x - cx, z - cz
            d, th = math.hypot(dx, dz), math.atan2(dz, dx)
            rr = r * (1 + amp[0] * math.sin(2 * th + ph[0]) + amp[1] * math.sin(3 * th + ph[1])
                      + amp[2] * math.sin(5 * th + ph[2]))
            if d > rr + 0.35:
                continue
            k = math.sqrt(max(0.0, 1 - (d / (rr + 0.35)) ** 2))
            top = cy + up * k + rng.uniform(-ragged, ragged)
            bot = min(top, cy - down * k + rng.uniform(-ragged, ragged) * 0.5)
            for y in range(int(round(bot)), int(round(top)) + 1):
                if 0 <= x < SIZE and 0 <= y < H and 0 <= z < SIZE and (x, y, z) not in t.logs:
                    t.leaf.add((x, y, z))


def oak_tones(t, rng, lobes, light="oak_leaves", body="dark_oak_leaves", dark="spruce_leaves", core=1.7,
              skin="spruce_leaves", cool="oak_leaves"):
    """r27 (critic r33: flat-shaded one-tone leaf cubes; r32: "two leaf tints, a darker inner shell"): each lobe gets
    the values a lit leaf mass has - sunlit patches on its top, a body, a darker underside and inner shell (spruce).
    Patches round a few spots per lobe, not pepper, so the tones read as light on a mass.
    r28 (critic r34 trees 3/10: "the top has gone lime... the tree vanishes into the grass from the RTS camera"): in
    game under the shipped shader preset the crown measured hue 92, V 0.50-0.52 against the lawn's hue 74, V 0.61, and
    r27's azalea tops (51-62% of each template's top, seen from above) hue 80, V 0.72-0.80 - lighter than the lawn, at
    its hue. Azalea is not biome-tinted: our pack draws it at a fixed yellow-green, luma 103 (0.40), where oak and dark
    oak are greyscale under the foliage colour (77 and 70) and spruce under the fixed evergreen colour (62). So the
    crown is now a darker mass: no azalea; a dark oak body; the sunlit patches oak (the lighter tinted leaf) and only
    within `core` of a spot's centre (19-34% of the top, was 51-62% azalea); the spruce underside and shell as r27.
    The random draws are r27's, in r27's order: the patches lie where they did and only their kinds change, and the
    skirt drawn after them is unchanged - the before/after is the colour alone.
    r29 (critic r35b oak 5/10: "lit crown V 0.30 (hue 93-97, sat 63-65) vs target 0.38-0.45... black-green blobs; raise
    the lit-face value to ~0.40 by DESATURATING (65 -> ~50) rather than darkening, keep the darker undersides"; lovish's
    villa reference: foliage hue ~99, sat ~35). Measured on that still (eyes run 40812245168, tree_sampler_a_rts, my
    HSV): the oak tops' lit faces hue 91, sat 72, V 0.48 against the lawn's hue 80, sat 63, V 0.53-0.56 - the plains
    foliage colour (#77AB2F, sat 0.73) tints oak and dark oak alike, so no mix of those two can desaturate. The two
    leaves with their own, greyer colours do: birch (#80A755, sat 0.49; lit in game hue 80, sat 61, V 0.57) and spruce
    (#619961, sat 0.37; lit hue 107, sat 51, V 0.44), whose hues straddle the target. So only the TOP skin - the faces
    the RTS camera sees lit - changes: r27's sun patches (whole, `core` 1.7) and its old dark-oak spots become birch,
    the rest of the top spruce; the sides stay dark oak and the underside spruce. Seen from above (240-oak sweep):
    birch 67%, spruce 33% (r28: dark oak 63%, oak 29%, spruce 9%); the linear mix of the measured lit colours predicts
    the tops at hue 86, sat 58, V 0.53 (r28 predicted by the same model: hue 92, sat 70, V 0.48, as measured).
    No new random draws: positions, skirt and logs stay r28's.
    r30 (Oct 29, critic r38: "birch vs oak identity: zero at the crown" - r29's oak tops WERE birch leaves): the sun
    patches and the cool spots back to oak leaves. The pack's r37 leaves and GrassTint's CANOPY now make the tinted
    oak leaf the darker, cooler crown r29 reached for through birch (plain lane V ~0.26 against the lawn's 0.34), so
    the oak reads as a dark blue-green mass and the birch as the light, yellow-green one."""
    top_of = {}
    for (x, y, z) in t.leaf:
        top_of[(x, z)] = max(y, top_of.get((x, z), -1))
    spots = []                                                   # per lobe: its light spots and its dark-oak spots
    for (cx, cy, cz), r, up, down in lobes:
        lit = [(cx + rng.uniform(-0.6, 0.6) * r, cz + rng.uniform(-0.6, 0.6) * r) for _ in range(rng.randint(2, 3))]
        mid = [(cx + rng.uniform(-0.9, 0.9) * r, cz + rng.uniform(-0.9, 0.9) * r) for _ in range(2)]
        spots.append((lit, mid))
    kinds = {}
    for p in t.leaf:
        x, y, z = p
        k = min(range(len(lobes)), key=lambda i: ((x - lobes[i][0][0]) ** 2 + (z - lobes[i][0][2]) ** 2) / lobes[i][1] ** 2
                + ((y - lobes[i][0][1]) / max(lobes[i][2], 1)) ** 2)
        (cx, cy, cz), r, up, down = lobes[k]
        lit, mid = spots[k]
        under = (x, y + 1, z) in t.leaf and (x, y + 2, z) in t.leaf
        bottom = (x, y - 1, z) not in t.leaf and y <= cy + 0.5
        kinds[p] = body
        if under and rng.random() < 0.6:
            kinds[p] = dark
        elif bottom and rng.random() < 0.75:
            kinds[p] = dark
        elif top_of[(x, z)] == y and any(math.hypot(x - sx, z - sz) < 1.7 for sx, sz in lit) and rng.random() < 0.85:
            # r27's lit patch (it was all azalea): its inner cells take the lighter leaf, its rim the top's skin
            kinds[p] = light if any(math.hypot(x - sx, z - sz) < core for sx, sz in lit) else skin
        elif any(math.hypot(x - sx, z - sz) < 1.5 for sx, sz in mid) and rng.random() < 0.6:
            if top_of[(x, z)] == y:
                kinds[p] = cool  # r29: r27's dark-oak patches, on the top: cool spruce patches in the lit skin
        elif top_of[(x, z)] == y:
            kinds[p] = skin     # r29: the rest of the top - the lit faces the RTS camera sees - takes the skin leaf
    t.kinds = kinds


OAK_FORMS = ("spread", "twin", "tall")


def oak(seed, old=False, form="spread"):
    """r27 oak pilot (lovish Oct 28: "one reference, an oak pilot to 7/10, then the maps place our own trees"). The
    reference: his giant mossy oak (Oct 14 batch 3, #10) - a broad sunlit crown of a few HEAVY separate masses on big
    limbs, a flared foot, flowers at the base - at map-tree size (12-13 high, so a reclaim lane, never a canopy).
    Fixes the r33 critic (3/10: "vanilla-shaped blobs, flat-shaded leaf cubes, a grey-blue log that reads as
    stone/ice, opaque canopies"):
      - the crown is 4-6 LOBES (lobe(): lumpy domes) on limbs that fork from a visible trunk, at different heights and
        reaches, the crown's weight thrown to one side; gaps of 1-2 blocks between some lobes let light through to
        the limbs and the ground;
      - the foot flares and tapers (old: a 2x2 core that SPLITS into two leaders) - never a box column;
      - bark is dark oak: the measured grey-blue trunk was oak bark (sat 0.34) in the crown's shade under the
        shader's sky ambient (hue 199, sat 0.19 at noon; hue 25 at dusk); dark oak bark (sat 0.49) should keep its
        brown there - to be measured in game;
      - three leaf tones per lobe (oak_tones; r28: darker than the lawn, no azalea).
    form: "spread" (one heavy reaching limb, two side limbs, a raised leader), "twin" (two stems from a low fork, a
    gap of sky between the heads), "tall" (a taller trunk, lobes spiralling up it)."""
    rng = random.Random(seed)
    t = Tree("dark_oak_log", "oak_leaves")
    lobes = []
    lean_a = rng.uniform(0, 2 * math.pi)

    def line(a, b):
        return t.line(a, b, faces=True)                           # every wood block face-joined to the last

    def reach_to(a, ang, reach, r, y):
        """The point reach blocks from a along ang at height y, pulled in so a lobe of radius r stays in the box."""
        lim = 6.5 - 1.15 * r
        x = max(C - lim, min(C + lim, a[0] + reach * math.cos(ang)))
        z = max(C - lim, min(C + lim, a[2] + reach * math.sin(ang)))
        return (round(x), y, round(z))

    def limb(a, end, knee=0.55):
        """A limb from a to end: out and a little up to a knee, then on and up - a bough that climbs as it reaches."""
        kx, kz = a[0] + (end[0] - a[0]) * knee, a[2] + (end[2] - a[2]) * knee
        e = line(a, (round(kx), a[1] + max(1, (end[1] - a[1]) // 3), round(kz)))
        return line(e, end)

    def add_lobe(end, r, up, down, lift=1.0, sat=None):
        line(end, (end[0], end[1] + int(lift), end[2]))            # the limb climbs into its lobe (leaves <= 6 away)
        c = (end[0] + rng.uniform(-0.3, 0.3), end[1] + lift, end[2] + rng.uniform(-0.3, 0.3))
        lobe(t, c, r, up, down, rng)
        lobes.append((c, r, up, down))
        if sat is not None:
            # a satellite: a twig on past the lobe's edge, out to one side and a block lower, carrying a small lobe of
            # its own - the crown's edge breaks into a separate mass instead of one round rim
            sa = sat + rng.choice((1, -1)) * rng.uniform(0.5, 0.9)
            rs = rng.uniform(1.2, 1.4)
            tip = reach_to(end, sa, r + 0.6, rs, end[1] - 1)
            if tip != (end[0], end[1] - 1, end[2]):
                line(end, tip)
                c = (tip[0], tip[1] + 0.5, tip[2])
                lobe(t, c, rs, 1.0, 0.6, rng, ragged=0.3)
                lobes.append((c, rs, 1.0, 0.6))

    # ---- the foot: flared and tapering (r33: the old oak's 2x2 column read as a stone pillar)
    back = min(((1, 0), (-1, 0), (0, 1), (0, -1)), key=lambda d: d[0] * math.cos(lean_a) + d[1] * math.sin(lean_a))
    if old:
        # a 2x2 core only 2 high under a light flare; two of its diagonal columns climb on as the two leaders, so the
        # old trunk reads as a V splitting off a stump, not a box (first r27 pass: a 3-high core read as a stump block)
        core = ((0, 0), (1, 0), (0, 1), (1, 1))
        for dx, dz in core:
            line((C + dx, 0, C + dz), (C + dx, 1, C + dz))
        ring = [(-1, 0), (-1, 1), (2, 0), (2, 1), (0, -1), (1, -1), (0, 2), (1, 2)]
        for dx, dz in rng.sample(ring, 3):
            t.put_log((C + dx, 0, C + dz), "w")
    else:
        sides = [back] + rng.sample([d for d in ((1, 0), (-1, 0), (0, 1), (0, -1)) if d != back], 2)
        for dx, dz in sides:
            t.put_log((C + dx, 0, C + dz), "w")
        t.put_log((C + back[0], 1, C + back[1]), "w")            # the buttress on the side the trunk leans from

    if form == "twin":
        # two stems from a low fork (old: two of the 2x2 core's columns climb on, each its own way), a gap of sky
        # between the two heads, each stem with a lower side limb
        a1 = lean_a
        sign = rng.choice((1, -1))
        a2 = a1 + sign * rng.uniform(2.4, 2.9)
        roots_ = ((C, 1, C), (C + 1, 1, C + 1)) if old else ((C, 1, C), (C, 1, C))
        if not old:
            line((C, 0, C), (C, 1, C))
        for i, (a, ang) in enumerate(((roots_[0], a1), (roots_[1], a2))):
            r = rng.uniform(2.0, 2.2) if i == 0 else rng.uniform(1.8, 2.0)
            top = reach_to(a, ang, rng.uniform(3.0, 3.4) if i == 0 else rng.uniform(2.5, 2.9), r,
                           rng.randint(7, 8) if i == 0 else rng.randint(6, 7))
            mid = (round(a[0] + (top[0] - a[0]) * 0.4), a[1] + 2, round(a[2] + (top[2] - a[2]) * 0.4))
            m = line(a, mid)
            line(m, top)
            add_lobe(top, r, rng.uniform(1.5, 1.8), rng.uniform(0.9, 1.1), sat=ang)
            # the side limb: lower, shorter, its own lobe - on the far side from the other stem, so the crotch between
            # the two stems stays open (first r27 pass: a side lobe in the crotch hid the high stem - a floating head)
            sa = ang + (-sign if i == 0 else sign) * rng.uniform(1.0, 1.4)
            rs = rng.uniform(1.5, 1.7)
            side_from = (m[0], m[1] + 1, m[2])
            if side_from in t.logs:
                end = reach_to(side_from, sa, rng.uniform(2.8, 3.3), rs, side_from[1] + 1)
                limb(side_from, end)
                add_lobe(end, rs, 1.3, 0.8, lift=0.6)
    elif form == "tall":
        h = rng.randint(9, 10)
        mid = line((C, 0, C) if not old else (C, 1, C), (C + round(0.9 * math.cos(lean_a)), 4,
                                                            C + round(0.9 * math.sin(lean_a))))
        top = line(mid, (mid[0], h, mid[2]))
        ang = lean_a + rng.uniform(-0.5, 0.5)
        for k, (y0, reach, r) in enumerate(((3, 3.8, 1.9), (5, 3.4, 1.8), (7, 2.8, 1.6))):
            ang += rng.choice((1, -1)) * rng.uniform(2.0, 2.6) if k else 0
            a = (mid[0], y0, mid[2]) if y0 <= 4 else (top[0], y0, top[2])
            if a not in t.logs:
                continue
            end = reach_to(a, ang, reach, r, y0 + 2)
            limb(a, end)
            add_lobe(end, r, rng.uniform(1.4, 1.7), rng.uniform(0.8, 1.0), lift=0.8, sat=ang if k == 0 else None)
        add_lobe(top, rng.uniform(1.8, 2.0), rng.uniform(1.5, 1.8), rng.uniform(0.9, 1.1))
    else:                                                          # spread
        f = rng.randint(3, 4) + (1 if old else 0)
        fork = line((C, 0, C) if not old else (C, 1, C),
                      (C + round(0.8 * math.cos(lean_a)), f, C + round(0.8 * math.sin(lean_a))))
        # the heavy limb: furthest, lowest - the crown's weight thrown to the lean's side
        r = rng.uniform(1.9, 2.1)
        a = (fork[0], f - 1, fork[2]) if (fork[0], f - 1, fork[2]) in t.logs else fork
        ang0 = lean_a + rng.uniform(-0.3, 0.3)
        end = reach_to(a, ang0, 4.8, r, f + 2)
        limb(a, end)
        add_lobe(end, r, rng.uniform(1.5, 1.8), rng.uniform(0.9, 1.1), sat=ang0)
        for s in (1, -1):                                          # two side limbs, higher and shorter, wide apart
            r = rng.uniform(1.7, 1.9)
            ang = lean_a + s * rng.uniform(2.2, 2.6)
            end = reach_to(fork, ang, rng.uniform(3.8, 4.2), r, f + rng.randint(3, 4))
            limb(fork, end)
            add_lobe(end, r, rng.uniform(1.4, 1.7), rng.uniform(0.8, 1.0), sat=ang if rng.random() < 0.5 else None)
        r = rng.uniform(1.7, 1.9)                                  # the leader: back over the trunk, the crown's top
        end = reach_to(fork, lean_a + math.pi + rng.uniform(-0.6, 0.6), rng.uniform(1.0, 1.4), r, f + rng.randint(5, 6))
        line(fork, end)
        add_lobe(end, r, rng.uniform(1.5, 1.8), rng.uniform(0.9, 1.0))
    t.round_out(rng, chance=0.5)
    if OAK_TONES:
        oak_tones(t, rng, lobes)
    skirt(t, rng, 3.0)
    t.lobes = lobes
    return t


def birch(seed, form="straight"):
    """Slim and pale: 3-4 blocks of white trunk, then a narrow crown of two or three stacked clumps with a gap of light
    between them. form: "straight", "lean" (the trunk leans a block halfway up) or "fork" (two stems from knee height)."""
    rng = random.Random(seed)
    t = Tree("birch_log", "birch_leaves")
    h = rng.randint(10, 11)                                      # round 13: 6-7 blocks of white trunk below the crown
    stems = []
    if form == "fork":
        knee = t.line((C, 0, C), (C, 2, C))
        d = rng.choice(((1, 0), (0, 1)))
        stems.append(t.line(knee, (C - d[0], h, C - d[1])))
        stems.append(t.line(knee, (C + d[0], h - 1, C + d[1])))
    elif form == "lean":
        mid = t.line((C, 0, C), (C, 3, C))
        d = rng.choice(((1, 0), (-1, 0), (0, 1), (0, -1)))
        stems.append(t.line(mid, (C + d[0], h, C + d[1])))
    else:
        stems.append(t.line((C, 0, C), (C, h, C)))
    for top in stems:
        y = 7
        ang = rng.uniform(0, 2 * math.pi)
        while y < top[1]:
            ang += rng.uniform(1.8, 2.6)                         # twigs spiral round the stem
            e = t.line((top[0], y, top[2]), (round(top[0] + 1.2 * math.cos(ang)), y, round(top[2] + 1.2 * math.sin(ang))))
            # round 5: 1.3-wide clumps every 3 left the white trunk showing through the crown from above (a lollipop
            # with holes) - fuller clumps every 2, still a little light between them from the side
            # round 8 (review: cube-cluster crowns, bark barely visible): smaller, more numerous lobes from 5 up, so
            # 4-5 blocks of white trunk show below the crown
            # round 12 (review: stacked pancakes, height > width): widest in the middle of the crown, narrow at top
            mid = 1 - abs((y - 6) / max(1, top[1] - 6) - 0.45) * 1.6
            # round 14 (review: cube-lobes with flat tops): a birch crown is narrow and ragged - hanging COLUMNS of
            # leaves, 1 wide, 2-5 tall, offset 0-1 from each twig, plus a thin core
            # r20 (review: 1-wide dark columns hung 3-4 blocks down the trunk - a beard): columns only in the crown
            for _ in range(rng.randint(1, 2) if y >= top[1] - 3 else 0):
                cx, cz = e[0] + rng.choice((-1, 0, 0, 1)), e[2] + rng.choice((-1, 0, 0, 1))
                for yy in range(e[1] - rng.randint(1, 2), e[1] + rng.randint(1, 3)):
                    if (cx, yy, cz) not in t.logs and yy > 3:
                        t.leaf.add((cx, yy, cz))
            if mid > 0.3:
                t.clump((e[0], e[1] + 0.4, e[2]), 1.1, 0.8, 1.1, rng, 0.35)
            y += 1
        # round 15 (review: so sparse the trunk showed down the middle from above - "plucked broccoli"): an opaque
        # cap over the stem, the hanging columns round its rim
        # round 16 (review: the cap was a 1-thick plate with pits - C/hook shapes from above, no apex; 2-3 block rim
        # columns read as stool legs): a dome of stacked rings, bites only on the outer ring, a 1-block rim hang
        # r17 (review: the hem + dark underside hid the white trunk to ~2 blocks; a flat + plateau on top): no hem, the
        # rings one block higher, and the top is 3-4 single-leaf bumps instead of a + stamp
        dome_rings(t, top, rng, ((1, 2.4), (2, 2.3), (3, 1.6)))
        for _ in range(rng.randint(3, 4)):
            a, r = rng.uniform(0, 2 * math.pi), rng.uniform(0, 1.3)
            t.leaf.add((round(top[0] + r * math.cos(a)), top[1] + 4, round(top[2] + r * math.sin(a))))
        t.line(top, (top[0], top[1] + 1, top[2]))                            # the stem carries the raised crown
    t.round_out(rng, chance=0.3, prune=False)        # r14: the 1-wide hanging columns are the birch look - keep them
    shade(t, rng, "oak_leaves")                      # r16: greener oak leaves in the shadowed cells give the dome form
    skirt(t, rng, 2.2, flowers=("oxeye_daisy", "azure_bluet", "dandelion"))
    return t


def spruce(seed, height=None):
    """A conifer in tiers: rings of short horizontal limbs that shrink up the trunk, each ring a flat skirt of
    needles, a gap of light between the skirts, a spire on top."""
    rng = random.Random(seed)
    t = Tree("spruce_log", "spruce_leaves")
    h = height or rng.randint(9, 11)
    t.line((C, 0, C), (C, h - 1, C))                             # the trunk stops under the needle tip
    roots(t, rng, 2)
    # whorls 3 apart from 2 blocks up (bare trunk at the foot), shrinking upward, a gap of light between them (round 3
    # had them 2 apart with drooping edges filling the gap: a solid dark cone)
    tiers = [(y, max(1.2, 3.2 * (1 - (y - 2) / (h - 1)))) for y in range(2, h - 2, 3)]
    for y, r in tiers:
        for k in range(4 if r >= 2.2 else 0):            # no limbs in the small top tiers (they showed through)
            ang = k * math.pi / 2 + rng.uniform(-0.3, 0.3) + (y % 4) * math.pi / 4
            reach = max(1, round(r - 1.8))        # limbs stay inside the skirt (they poked through, r1)
            t.line((C, y, C), (round(C + reach * math.cos(ang)), y, round(C + reach * math.sin(ang))))
        # round 5: every tier was the same centred "+" slab - a stacked pagoda from above; each tier now sits a little
        # off the trunk and has its own width and oval
        r = r * rng.uniform(0.85, 1.15)
        off = (rng.uniform(-0.5, 0.5), rng.uniform(-0.5, 0.5))
        t.clump((C + off[0], y + 0.6, C + off[1]), r * rng.uniform(0.85, 1.0), 1.0, r * rng.uniform(0.85, 1.0), rng, 0.0)
        bite = rng.uniform(0, 2 * math.pi)                       # a missing chunk on one side: not a perfect cone
        for x in range(int(C - r - 1), int(C + r + 2)):
            for z in range(int(C - r - 1), int(C + r + 2)):
                if math.hypot(x - C, z - C) > r - 0.9 and math.cos(math.atan2(z - C, x - C) - bite) > 0.85:
                    for yy in (y, y + 1):
                        t.leaf.discard((x, yy, z))
    t.clump((C, h - 0.5, C), 1.3, 1.8, 1.3, rng, 0.0)       # needles round the top of the trunk, not a bare spire
    t.clump((C, h + 0.5, C), 0.9, 1.4, 0.9, rng, 0.0)
    if (C, h + 2, C) not in t.leaf and h + 2 < H:              # round 5: a one-block tip, not a blunt top
        t.leaf.add((C, h + 2, C))
    skirt(t, rng, 2.4, 0.4, flowers=("fern",))                   # needles and ferns under a spruce, no flowers
    return t


def acacia(seed):
    """Savanna: a trunk that bends away, forks, and holds two or three flat umbrella crowns at different heights."""
    rng = random.Random(seed)
    t = Tree("acacia_log", "acacia_leaves")
    lean = rng.choice(((1, 0), (-1, 0), (0, 1), (0, -1)))
    mid = t.line((C, 0, C), (C, 3, C))
    roots(t, rng, 2)
    # a real fork: two arms leaving the trunk in different directions, each carrying its own pad (<= ~7 wide overall)
    side = (-lean[1], lean[0]) if rng.random() < 0.5 else (lean[1], -lean[0])
    for k, (dx, dz, up) in enumerate(((lean[0] * 2, lean[1] * 2, 3), (side[0] * 2, side[1] * 2, 2))):
        out = t.line(mid, (mid[0] + dx, mid[1] + 1, mid[2] + dz))          # the arm reaches out first...
        e = t.line(out, (out[0] + dx // 2, out[1] + up, out[2] + dz // 2))    # ...then rises
        r = rng.uniform(1.8, 2.2)
        t.clump((e[0], e[1] + 1, e[2]), r, 0.7, r, rng, 0.04)                  # the pad, and a smaller pad on top
        t.clump((e[0] + rng.choice((-1, 0, 1)), e[1] + 2, e[2] + rng.choice((-1, 0, 1))), r * 0.55, 0.6, r * 0.55, rng)
    return t


def pine(seed):
    """A tall pine (lovish's 'cute pine' reference, Oct 21): a long bare trunk with a few dead fence stubs, the crown only
    in the top third - short limbs, each holding its own flat needle pad, sky between the pads."""
    rng = random.Random(seed)
    t = Tree("spruce_log", "spruce_leaves")
    h = rng.randint(10, 12)                                     # the crown must fit the 16-high template
    t.line((C, 0, C), (C, h, C))
    roots(t, rng, 2)
    for y in range(3, int(h * 0.55)):                              # dead stubs on the bare trunk
        if rng.random() < 0.35:
            dx, dz = rng.choice(((1, 0), (-1, 0), (0, 1), (0, -1)))
            side = {(1, 0): "west", (-1, 0): "east", (0, 1): "north", (0, -1): "south"}[(dx, dz)]
            props = {"north": "false", "south": "false", "east": "false", "west": "false", "waterlogged": "false"}
            props[side] = "true"                                  # joined to the trunk
            t.plants[(C + dx, y, C + dz)] = ("spruce_fence", props)
    y = int(h * 0.55)
    ang = rng.uniform(0, 2 * math.pi)
    while y <= h - 1:
        ang += rng.uniform(1.9, 2.7)
        reach = rng.choice((1, 2, 2))
        e = t.line((C, y, C), (round(C + reach * math.cos(ang)), y + 1, round(C + reach * math.sin(ang))))
        t.clump((e[0], e[1] + 0.5, e[2]), rng.uniform(1.5, 2.1), 0.8, rng.uniform(1.5, 2.1), rng, 0.04, dome=0.7)
        y += 1
    t.clump((C, h + 0.6, C), 1.5, 0.9, 1.5, rng, 0.05, dome=0.6)  # the crown's top pad
    skirt(t, rng, 2.0, 0.35, flowers=("fern",))
    return t


def cherry(seed):
    """Cherry (lovish's Tibetan-cherry and cherry-blossom references, Oct 21): 2-3 stems that part from the foot and
    climb in diagonal steps, each ending in a few SEPARATE round blossom puffs with sky between them; pink petals and
    grass on the ground under it."""
    rng = random.Random(seed)
    t = Tree("cherry_log", "cherry_leaves")
    t.line((C, 0, C), (C, 1, C))
    n = rng.choice((2, 3, 3))
    a0 = rng.uniform(0, 2 * math.pi)
    for k in range(n):
        a = a0 + k * 2 * math.pi / n + rng.uniform(-0.35, 0.35)
        out = rng.uniform(1.8, 2.6)
        h = rng.randint(5, 7)
        top = t.line((C, 1, C), (round(C + out * math.cos(a)), h, round(C + out * math.sin(a))))
        for j in range(rng.randint(3, 4)):                         # puffs round the stem's end, apart from each other
            b = a + rng.uniform(-1.3, 1.3)
            r = rng.uniform(1.0, 1.6)
            tw = t.line(top, (round(top[0] + r * math.cos(b)), top[1] + rng.randint(0, 1), round(top[2] + r * math.sin(b))))
            # puffs a little smaller and more numerous so sky shows between them (first offline pass read as pink slabs)
            t.clump((tw[0], tw[1] + 0.8, tw[2]), rng.uniform(1.1, 1.5), rng.uniform(0.9, 1.2), rng.uniform(1.1, 1.5), rng,
                    0.08, dome=0.4)
    skirt(t, rng, 3.0, 0.5, flowers=("pink_petals",))
    for (x, y, z), b in list(t.plants.items()):
        if b == "pink_petals":                                    # a few petals on each texel, facing at random
            t.plants[(x, y, z)] = ("pink_petals", {"flower_amount": str(rng.randint(2, 4)),
                                                    "facing": rng.choice(("north", "south", "east", "west"))})
    return t


SPECIES = {"oak": oak, "birch": birch, "spruce": spruce, "acacia": acacia, "pine": pine, "cherry": cherry}
VARIANTS = 4


# ---- ground scatter (Oct 21): small pieces that break up flat grass between trees - the realism list's "ground
# clutter" at map scale. Each piece is low (<= 3 blocks) so units stay visible from the RTS camera, and every block is
# one that resources/TreeClearing or a builder can clear like terrain.

def bush(seed):
    """A low shrub: a short wood stub under a squat, uneven leaf mound (2-4 wide, 1-2 high)."""
    rng = random.Random(seed)
    leaves = rng.choice(("dark_oak_leaves", "dark_oak_leaves", "spruce_leaves"))   # round 7: oak/azalea read lime
    t = Tree("oak_log", leaves)
    second = "spruce_leaves" if leaves == "dark_oak_leaves" else "dark_oak_leaves"
    t.put_log((C, 0, C), "w")
    r = rng.uniform(1.6, 2.3)
    # centred low so the mound sits ON the grass round its stub (at 0.8 high it floated as a slab over a bare stub)
    # round 7 (review: 1-block-thick plates): a 2-high domed mound on a 3x3+ base
    t.put_log((C, 1, C), "w")
    t.clump((C + rng.uniform(-0.3, 0.3), 1.0, C + rng.uniform(-0.3, 0.3)), r, 1.6, r * rng.uniform(0.8, 1.0), rng, 0.04,
            dome=0.0)
    if rng.random() < 0.6:                                       # a second, smaller lobe: not a round ball
        a = rng.uniform(0, 2 * math.pi)
        t.clump((C + 1.5 * math.cos(a), 0.6, C + 1.5 * math.sin(a)), r * 0.75, 1.2, r * 0.75, rng, 0.06, dome=0.0)
    # round 8 (review: each bush a hard-edged box): knock the top corners off, a few 1-block outliers on the ground
    top = max(y for (_, y, _) in t.leaf) if t.leaf else 1
    for (x, y, z) in list(t.leaf):
        if y == top and math.hypot(x - C, z - C) > r * 0.75:
            t.leaf.discard((x, y, z))
    for _ in range(rng.randint(2, 4)):
        a = rng.uniform(0, 2 * math.pi)
        q = (round(C + (r + 0.8) * math.cos(a)), 0, round(C + (r + 0.8) * math.sin(a)))
        t.leaf.add(q)
    t.second = (second, {p for p in t.leaf if rng.random() < 0.3})   # two leaf kinds mixed through the mound
    return t


def boulder(seed, scatter=None):
    """A weathered boulder half sunk in the grass: a lumpy ellipsoid of mixed stone (mossy on the shaded north side),
    with one or two loose stones beside it. Returns {(x, y, z): block}."""
    rng = random.Random(seed)
    rx, ry, rz = rng.uniform(2.0, 2.6), rng.uniform(1.4, 1.9), rng.uniform(1.8, 2.4)   # r7: smaller read as plus shapes
    cy = -0.3                                                    # sunk: the widest part sits at grass level
    out = {}
    mix = ("stone", "stone", "stone", "andesite", "andesite", "tuff")   # r7: cobblestone read peppery
    for x in range(int(C - rx - 1), int(C + rx + 2)):
        for y in range(0, int(cy + ry + 2)):
            for z in range(int(C - rz - 1), int(C + rz + 2)):
                d = ((x - C) / rx) ** 2 + ((y - cy) / ry) ** 2 + ((z - C) / rz) ** 2
                if d <= 1.08 + 0.2 * (rng.random() - 0.5):
                    north = (z - C) < -0.3 * rz                   # -z: the side the sun never reaches in the scenes
                    b = rng.choice(mix)
                    if north and rng.random() < 0.6:
                        b = "mossy_cobblestone"
                    out[(x, y, z)] = b
    for _ in range(rng.randint(1, 2)):                           # loose stones
        a = rng.uniform(0, 2 * math.pi)
        p = (round(C + (rx + 1.6) * math.cos(a)), 0, round(C + (rz + 1.6) * math.sin(a)))
        out[p] = rng.choice(("cobblestone", "andesite", "mossy_cobblestone"))
    return out


def fallen_log(seed):
    """A fallen trunk lying in the grass with a broken stump nearby, moss on its top, a few leaves where the crown was."""
    rng = random.Random(seed)
    wood = rng.choice(("oak", "birch", "spruce"))
    t = Tree(wood + "_log", wood + "_leaves")
    L = rng.randint(4, 5)
    along_x = rng.random() < 0.5
    x0 = C - 3                                                   # the whole piece stays inside its 9 x 9 footprint
    for k in range(L):
        p = (x0 + k, 0, C) if along_x else (C, 0, x0 + k)
        t.put_log(p, "x" if along_x else "z")
    stump = (x0 - 1, 0, C) if along_x else (C, 0, x0 - 1)          # r7: at the log's broken end, not apart
    t.put_log((stump[0], 1, stump[2]), "w")                       # a 2-high broken stump
    t.put_log(stump, "w")                                         # bark all round: no cut ring showing on top
    end = (x0 + L, 0, C) if along_x else (C, 0, x0 + L)
    t.clump((end[0], 0.4, end[2]), 1.5, 0.9, 1.5, rng, 0.2, dome=0.3)   # what is left of the crown
    return t


SCATTER = {"bush": (bush, 4), "boulder": (boulder, 4), "fallen_log": (fallen_log, 3)}


# ---- bare trees (lovish's references, Oct 21 03:08: a dark gnarled dead tree at night and a giant bare tree with a
# "normal tree" beside it for scale). What makes them read: limbs that THIN as they fork - full wood, then fence twigs
# at the tips - and limbs that climb in diagonal steps rather than straight runs. Leafless, so the twigs carry the shape.

def bare_tree(seed, wood="spruce", giant=False, dead=False, dead_wood="acacia_wood", dead_base=None,
              dead_base_h=0, dead_fence="dark_oak", fallen=True):
    """Returns ({(x, y, z): (block, props)}, size, height). Trunk 2x2 (giant) or 1x1, limbs that fork 3-4 times, the
    last forks fence twigs. Positions are inside a size x height x size box, trunk at its centre.

    r23: y = 0 is the GROUND layer - place a bare tree with its origin ON the surface block, not above it. The trunk,
    the surface roots and a ring of soil (podzol / coarse dirt) replace the grass there, so the foot reads from the RTS
    camera; the skirt (fern, tall grass, dead bush) stands on that ring at y = 1-2. Everything else is structure void.
    dead (r25): ONE leaning trunk in a weathered grey bark (dead_wood, a full block id: acacia_wood - the pack's
    silvered, cracked bark; it must be a WOOD block ResourceSources counts as wood, mushroom_stem is a FOOD source),
    its lowest dead_base_h blocks in dead_base (the rotted dark foot),
    2-3 stubby limbs of wood with nothing on them, a snapped top, no twigs; a mossy fallen log lies on the coarse-dirt
    ring. bare oak keeps its forked crown, so the two never share a silhouette (lead on r24: indistinguishable)."""
    rng = random.Random(seed)
    size, height = (27, 31) if giant else (15, 17)
    c = size // 2
    blocks = {}
    # r24: the lead on dead_crop.png - stripped spruce read as a stack of pale planks / a pallet from above (every
    # stripped log is a striped timber block). r25 (lead on the _34 / dusk stills: dead and bare sat at the same bark
    # value): the dead tree is a different VALUE - grey acacia bark through the limbs with a dark rotted base. Only
    # blocks ResourceSources counts as WOOD may build a trunk (lead): birch_wood was pale but its spots read as a live
    # birch; acacia is the one silvered bark (trees_r25/dead_candidates.png).
    # dead_wood / dead_base / dead_fence stay parameters so candidates can be judged side by side (trees_r25).
    W, F = (dead_wood, dead_fence + "_fence") if dead else (wood + "_wood", wood + "_fence")
    links = set()       # pairs of blocks that follow each other along one limb: fences join ONLY along their own limb
                        # (round 7 review: fences auto-joined every neighbour into trellis / cage grids)
    H4 = ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1))
    H4_XZ = ((1, 0), (-1, 0), (0, 1), (0, -1))

    def link(a, b):
        links.add(frozenset((a, b)))

    # r26 (lead on r25: the 3-high dark oak foot read as a separate brown lump under a grey heap): the flare and the
    # whole trunk are ONE wood - dead_base is only for a judged transition and defaults to the trunk's own block
    B = (dead_base or W) if dead else W
    THICK = {W, B}

    def put(p, b):
        x, y, z = p
        if 0 <= x < size and 0 <= y < height and 0 <= z < size:
            if blocks.get(p) in THICK and b not in THICK:
                return                                            # thick wins where a twig meets a limb
            if blocks.get(p) == B and b == W:
                return                                            # the dark base stays under a pale limb root
            blocks[p] = b

    def game_joins(p):
        """The blocks a fence at p would auto-join in game: every horizontal neighbour, fence or solid (r23, lead on
        crops.png: side twigs + hooks beside a run closed into square picture frames)."""
        return [(p[0] + dx, p[1], p[2] + dz) for dx, _, dz in H4 if (p[0] + dx, p[1], p[2] + dz) in blocks]

    def fence_ok(p, prev):
        """A fence may stand at p only if its sole horizontal neighbour is prev (or none when prev is below it), so
        every twig is one open line."""
        if not (0 <= p[0] < size and 0 <= p[1] < height and 0 <= p[2] < size) or p in blocks:
            return False
        js = game_joins(p)
        if not all(q == prev for q in js) or len(js) > 1:
            return False
        return blocks.get(prev) != F or len(game_joins(prev)) < 2    # a fence takes at most 2 joins (no T, no cross)

    def crowded(p, prev, start):
        """True if p has a neighbour (the 8 around it at its height, and above/below) that is not this twig's own
        previous block or the block it grew from."""
        x, y, z = p
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    q = (x + dx, y + dy, z + dz)
                    if q == p or q == prev or q == start:
                        continue
                    if q in blocks and (abs(q[0] - prev[0]) > 1 or abs(q[1] - prev[1]) > 1 or abs(q[2] - prev[2]) > 1
                                        or dy != 0 or (dx != 0 and dz != 0)):
                        return True
        return False

    def run(a, d, n, b):
        """n steps from a along direction d (a unit vector); every step shares a face with the last. r23: a climbing
        step on a wood limb stacks a wood block under the new one (stairs showed plank tops from the RTS camera)."""
        x, y, z = a
        prev = (round(x), round(y), round(z))
        start = prev
        for _ in range(n):
            x, y, z = x + d[0], y + d[1], z + d[2]
            p = (round(x), round(y), round(z))
            if p == prev:
                continue
            if b == F and (crowded(p, prev, start) or not fence_ok(p, prev)):
                # round 8 (eyes 39566783481: twigs from different limbs ran side by side into a lattice from above):
                # a twig stops where it would touch another branch - real twigs fill gaps, they don't cross
                break
            dd = [p[i] - prev[i] for i in range(3)]
            if sum(1 for v in dd if v) > 1:                       # diagonal: fill a corner so the limb stays joined
                q = list(prev)
                for i in (1, 0, 2):
                    if dd[i]:
                        q[i] = p[i]
                        if tuple(q) != p:
                            put(tuple(q), b)
                            link(prev, tuple(q))
                            link(tuple(q), p)
                        break
            else:
                link(prev, p)
            put(p, b)
            prev = p
        return (x, y, z)

    def limb(a, ang, rise, length, level, maxlevel):
        b = F if (giant and level >= maxlevel - 1) else W     # r20: small trees - wood only, the fences were clutter
        thick = giant and level <= GIANT_THICK_LEVEL                 # round 11: giants' main limbs 2 wide, then taper
        if b == F:
            # round 10: long fence runs drew a pale lattice box from 3/4 distance. r26: a twig is 1-3 blocks, its
            # length its own, so sibling twigs never line up as a rake
            length = min(length, rng.choice((1, 2, 2, 3)))
            # round 9 (eyes 39568590526: twigs read as floating posts): a fence joins its neighbours only SIDEWAYS, so
            # a twig that climbs leaves posts hanging in the air - fence twigs run level, out from the wood they grow on
            rise = 0.0
            a = (round(a[0]), round(a[1]), round(a[2]))
        if giant and level > 0:
            # r25 (lead on r24 rts_close: the upper limb was a zigzag of steps - a staircase): sub-limbs follow a
            # compass line, so from above each is a straight or a clean diagonal run, not a wobble. r26 (lead on r25:
            # the 8 lines gave a stiff asterisk and parallel limbs grew fence twigs in comb rows): 16 lines (a 22.5 deg
            # run is out-out-over, still one clean line) and each limb is nudged off its line by its own seed, so no
            # two limbs are parallel and the crown is a tangle, not a star
            ang = round(ang / (math.pi / 8)) * (math.pi / 8) + rng.uniform(-0.08, 0.08)
        h = math.hypot(1, rise)
        d = (math.cos(ang) / h, rise / h, math.sin(ang) / h)
        e = run(a, d, int(length), b)
        if thick:                                                   # a second, parallel run beside the first
            side = (-math.sin(ang), 0, math.cos(ang))
            # round 13 (review: limbs stay 2 wide to the tip): the second run covers only the inner half
            # r16 (review: 1-wide limbs slice into confetti from any high angle): 2 wide for the first blocks so a main
            # limb reads as one mass from above. r25: 1 deep (the two stacked runs over it were the dark overhang mass
            # at the giant's base) and only the main limbs (level 0); the next level is 1 wide, then fence - a taper
            n2 = min(5, max(2, int(length) - 3))
            run((a[0] + side[0], a[1], a[2] + side[2]), d, n2, b)
        if level < maxlevel:
            forks = (GIANT_FORKS if giant and level < 2 else rng.choice((2, 2, 3)) if giant else 2) if level < maxlevel - 2 else 2
            for k in range(forks):                  # r14: giants split into 3 rising sub-branches (a web from above)
                ang2 = ang + rng.uniform(0.35, 1.05) * (1 if k % 2 else -1) + rng.uniform(-0.2, 0.2)
                # twigs reach OUT more than up: a rise that compounded per fork stood the fence tips upright like
                # candles (first offline sheet)
                # round 13 (review: giants read as a flat stain from above): wood limbs keep climbing, so the outer
                # third rises 1-3 blocks and casts its own shadow
                # r25: a shallower rise (1 in 3, not 1 in 1-2) - a long run with a climb now and then, not stairs
                rise2 = min(0.4, max(0.1, rise * 0.5 + rng.uniform(0.0, 0.15))) if giant else \
                    min(0.6, max(-0.15, rise * 0.6 + rng.uniform(-0.2, 0.3)))
                # r26: sub-limb lengths vary more (0.45-0.8 of the parent) - one long reach and one short stub
                limb(e, ang2, rise2, max(3, length * rng.uniform(0.45, 0.8)), level + 1, maxlevel)
        # r25: no hook on the tip - the L-turn with a post read as built fence (lead on r24)

    # ---- r22: the small trees' own limbs (eyes round 21, bat_eyes_15_bare_sampler_rts_close: limbs were flat 2-wide
    # plus-shaped slabs that never thinned, almost no twigs, the 4/6/8 stagger invisible from above - one broccoli
    # clump; the giant beside them, 1-wide limbs stepping diagonally with fence twigs fanning at the tips, is the look).
    # r23 (lead on crops.png: the stair under each step showed plank tops - a staircase of crates): a small limb is a
    # 4-connected line of WOOD only - out, out, up, out, out... (an up step is a wood block stacked on the last, so from
    # above the limb is one 1-wide line that climbs 1 in 2-3) - then a fence post on the wood tip and 1-2 fence blocks
    # running out, ending in a side twig or a hook up. Every fence has exactly one horizontal neighbour: its own
    # predecessor (fence_ok), so no twig can close into a frame. Nothing is ever 2 wide.
    FACE = {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}

    def steps4(ang, n):
        """n unit steps (dx, dz), each along one axis, that follow the angle - a 4-connected path with no corners."""
        tx, tz, px, pz, out = n * math.cos(ang), n * math.sin(ang), 0, 0, []
        for i in range(1, n + 1):
            ex, ez = tx * i / n - px, tz * i / n - pz
            d = (int(math.copysign(1, ex)), 0) if abs(ex) >= abs(ez) else (0, int(math.copysign(1, ez)))
            px, pz = px + d[0], pz + d[1]
            out.append(d)
        return out

    def clear(p, own):
        """True if nothing but this twig's own blocks sits in the 26 cells round p (round 8: twigs stop short of
        another branch rather than cross it)."""
        return not any((p[0] + dx, p[1] + dy, p[2] + dz) in blocks and (p[0] + dx, p[1] + dy, p[2] + dz) not in own
                       for dx in (-1, 0, 1) for dy in (-1, 0, 1) for dz in (-1, 0, 1))

    def fence_run(a, path, own):
        """Level fence blocks from a (a fence or wood block) along path; stops short of another branch (round 8) and
        wherever the fence would auto-join anything but its predecessor (r23)."""
        prev = a
        for dx, dz in path:
            p = (prev[0] + dx, prev[1], prev[2] + dz)
            if not fence_ok(p, prev) or not clear(p, own):
                break
            own.add(p)
            put(p, F)
            link(prev, p)
            prev = p
        return prev

    def small_limb(start, ang, wood_n, fence_n, ups=None, stub=False):
        """wood_n blocks of wood out from start along ang, climbing one block after the out-steps listed in ups
        (default: after the 1st and 3rd), then the twig. stub: a broken limb - the wood ends, nothing on it (dead)."""
        prev = start
        own = {start}
        path = steps4(ang, wood_n + fence_n + 2)
        if ups is None:
            ups = (0, 2) if wood_n >= 3 else (0,)
        for i, (dx, dz) in enumerate(path[:wood_n]):
            p = (prev[0] + dx, prev[1], prev[2] + dz)
            if not (0 <= p[0] < size and 0 <= p[2] < size):
                break
            put(p, W)
            link(prev, p)
            own.add(p)
            prev = p
            if i in ups and i < wood_n - 1:                         # climb: a wood block on top of this one
                p = (prev[0], prev[1] + 1, prev[2])
                put(p, W)
                link(prev, p)
                own.add(p)
                prev = p
        if stub or fence_n <= 0:
            return
        # r25 (lead on r24 rts_close: every tip turned 90 deg with a fence post, so the twigs read as built fence):
        # the twig runs LEVEL out of the wood tip's side along the limb's own line - no post up, no side fork, no hook.
        # steps4 keeps the path 4-connected along the angle, so the last blocks are a straight run or one short
        # diagonal step (out, over, out); fence_run stops short of anything else.
        d = path[wood_n - 1] if wood_n else path[0]
        if fence_n >= 3 and rng.random() < 0.5:                   # out, one step over, out again: a diagonal, never an L
            perp = rng.choice(((d[1], d[0]), (-d[1], -d[0])))
            twig = [d, perp] + [d] * (fence_n - 2)
        else:
            twig = [d] * fence_n                                  # a straight run along the limb's own line
        fence_run(prev, twig, own)

    def flare(foot):
        """A flared base (r23): 2-4 buttress roots - a wood block beside the trunk one up, then 1-2 wood tiles flush
        with the ground (they replace the grass, so they read as surface roots from above and never as crates)."""
        x, y, z = foot
        sides = rng.sample(list(FACE), rng.randint(2, 4))
        for dx, dz in sides:
            put((x + dx, 1, z + dz), B)                           # the buttress, beside the trunk, a block up
            put((x + dx, 0, z + dz), B)
            if rng.random() < 0.4:                                # a kinked root: turns before it runs on
                k = rng.choice((1, -1))
                put((x + dx + dz * k, 0, z + dz + dx * k), B)
            else:
                put((x + 2 * dx, 0, z + 2 * dz), B)
                if rng.random() < 0.5:
                    put((x + 3 * dx, 0, z + 3 * dz), B)

    plants, soil = {}, {}
    h = rng.randint(18, 21) if giant else rng.randint(9, 11) if dead else rng.randint(10, 11)   # r20: small ones lay flat from above at 6-8
    DEAD_SWAY = 0.2                                               # r26: a tall leaning snag, not r25's 0.32 heap
    lean = rng.uniform(0, 2 * math.pi)
    last = None
    for y in range(h):
        sway = (0.12 if giant else DEAD_SWAY if dead else 0.15) * y    # r25: a dead trunk leans - one tilted pole
        x0, z0 = c + sway * math.cos(lean), c + sway * math.sin(lean)
        cur = (round(x0), y, round(z0))
        TW = B if (dead and y < dead_base_h) else W               # r25: the dead trunk's rotted dark foot
        if last and (cur[0], cur[2]) != (last[0], last[2]):
            put((cur[0], y - 1, cur[2]), TW)                    # round 10: the lean steps over, not corner-to-corner
            if cur[0] != last[0] and cur[2] != last[2]:          # r20: a diagonal lean step needs a face-joined corner
                put((cur[0], y - 1, last[2]), TW)
        put(cur, TW)
        last = cur
        if giant and y < h * 0.55:                                # a 2x2 bole that narrows to one block
            for dx, dz in ((1, 0), (0, 1), (1, 1)):
                put((round(x0) + dx, y, round(z0) + dz), W)
        elif giant and y < h * 0.55 + 2:                          # r26: the bole steps down to 1 wide, not a flat ledge
            put((round(x0) + 1, y, round(z0)), W)
        if not giant and y == 0:                                  # r22: a 1-wide trunk on a flared root base (the r20
            flare(cur)                                            # 2x2 foot read as a straight box)
        if giant and y < 4:                                       # r17: a 3x3 foot, then 2x2 - a taper (r25: 4 high, was 7 -
                                                                  # the ledge and the flare were a mass of dark overhangs)
            for dx, dz in ((-1, -1), (-1, 0), (-1, 1), (0, -1), (1, -1)):
                put((round(x0) + dx, y, round(z0) + dz), W)
        if giant and y < 2:                                       # root flare (r25: 2 high and sparser)
            for dx, dz in ((-1, 0), (0, -1), (2, 0), (0, 2), (2, 1), (1, 2)):
                if rng.random() < 0.5 or y == 0:
                    put((round(x0) + dx, y, round(z0) + dz), W)
        if giant and y == 0:                                      # r23: surface roots, flush with the ground
            for dx, dz in ((-2, 0), (0, -2), (3, 0), (0, 3), (3, 1), (1, 3), (-2, -1), (-1, -2), (-2, 2), (3, -1)):
                if rng.random() < 0.6:
                    put((round(x0) + dx, y, round(z0) + dz), W)
                    if rng.random() < 0.5:
                        put((round(x0) + dx + (dx > 0) - (dx < 0), y, round(z0) + dz + (dz > 0) - (dz < 0)), W)
    top = (c + 0.12 * h * math.cos(lean) if giant else c + 0.15 * h * math.cos(lean), h - 1,
           c + 0.12 * h * math.sin(lean) if giant else c + 0.15 * h * math.sin(lean))
    n = rng.randint(*GIANT_LIMBS) if giant else rng.randint(3, 4)       # r16: one more giant limb - no wide gaps in the crown
    if not giant and dead:
        # r25 (lead on r24: dead and bare shared the corkscrew silhouette): a dead tree is ONE leaning pole, no twigs.
        # r26 (lead on r25: the hard lean plus low stubs read as a heap of grey rubble / a crate from above, the dark
        # oak foot as a separate brown lump): a TALLER snag - the pole climbs 6-8 blocks over the flare with a gentler
        # lean, bare for its lower half, then 1-2 BROKEN upper limbs (2-3 wood, one climbing step, a snapped end) in
        # its top third and a 2-high snapped leader off the top - a dead tree's silhouette is the tall stick.
        ang, turn = rng.uniform(0, 2 * math.pi), rng.choice((1, -1))
        for k in range(rng.randint(1, 2)):
            y0 = h - 4 + 2 * k if k else h - rng.randint(3, 4)
            y0 = min(h - 2, y0)
            sw = DEAD_SWAY * y0
            start = (round(c + sw * math.cos(lean)), y0, round(c + sw * math.sin(lean)))
            small_limb(start, ang, rng.randint(2, 3), 0, ups=(0,) if rng.random() < 0.7 else (1,), stub=True)
            ang += turn * math.radians(rng.uniform(110, 160))
        snag = (last[0], last[1] + 1, last[2])
        put(snag, W)                                               # the snapped top: 1-2 blocks up off the lean's end
        if rng.random() < 0.5:
            snag2 = (snag[0], snag[1] + 1, snag[2])
            put(snag2, W)
            snag = snag2
        small_limb(snag, ang + rng.uniform(-0.5, 0.5), 1, 0, ups=(), stub=True)
        n = 0
    elif not giant:
        # r22: 3-4 limbs at y 4 / 6 / 8 (/ 9), each pointing 90-140 degrees from the last so the stagger shows from
        # above (one limb per quadrant, not a layer), each 3-4 wood then 2 fence. r25: the crown FORKS - two leaders
        # off the top block, 120-180 deg apart, each 2 wood climbing then a twig - a Y against the dead tree's pole.
        ang, turn = rng.uniform(0, 2 * math.pi), rng.choice((1, -1))
        for k in range(n):
            y0 = min(h - 2, 4 + 2 * k)
            sw = 0.15 * y0
            start = (round(c + sw * math.cos(lean)), y0, round(c + sw * math.sin(lean)))
            # r26 (lead on r25: every limb ended in the same fence nub): the twig is 0-2 fence, each limb's own
            small_limb(start, ang, rng.randint(3, 4), rng.choice((0, 1, 1, 2, 2)))   # reach 3-6: a line from above
            ang += turn * math.radians(rng.uniform(90, 140))     # winds round the trunk, 90-140 deg per limb
        a1 = ang + rng.uniform(-0.5, 0.5)
        a2 = a1 + turn * math.radians(rng.uniform(120, 180))
        small_limb(last, a1, 2, rng.choice((0, 1, 2)), ups=(0,))   # the forked leaders, off the trunk's top block
        small_limb(last, a2, 2, rng.choice((1, 2)), ups=(0,))
        n = 0
    for k in range(n):                                            # main limbs leave at different heights round the bole
        ang = k * 2 * math.pi / n + rng.uniform(-0.4, 0.4)
        y0 = h - 2 - (k * 2) % 6                                  # r16: -2, the 2-deep limb root stays inside the bole
        sw = 0.12 * y0                                            # r14: start ON the leaning trunk at this height - from
        start = (round(c + sw * math.cos(lean)), y0, round(c + sw * math.sin(lean)))   # the top's x/z, low limbs began
                                                                # off the trunk and the connectivity pass deleted them
        limb(start, ang, rng.uniform(0.35, 0.8), GIANT_LEN * rng.uniform(0.7, 1.25), 0, GIANT_LEVELS)   # r26: lengths vary
    if giant:
        # r26 (lead on r25: the trunk top was a flat square column): a BROKEN, forked leader - the trunk climbs two
        # more blocks and snaps, one leader limb leaves the top at a lean, a second short stub leans the other way
        # one block lower, so from above the top is a Y with a jagged end, not a square
        la = rng.uniform(0, 2 * math.pi)
        tip = (round(top[0]), int(top[1]), round(top[2]))
        for k in (1, 2):
            put((tip[0], tip[1] + k, tip[2]), W)
        snap = (tip[0], tip[1] + 2, tip[2])
        limb(snap, la, 1.2, 4, 1, GIANT_LEVELS)                   # the leader
        stub_d = (round(math.cos(la + 2.6)), 1, round(math.sin(la + 2.6)))
        run((tip[0], tip[1] + 1, tip[2]), (stub_d[0] or 0, 1, stub_d[2] or 0) if (stub_d[0] or stub_d[2]) else (1, 1, 0), 2, W)
    placed = len(blocks)
    # r23: the game joins a fence to EVERY horizontal neighbour, fence or solid, whatever the saved props say. Delete any
    # fence that would join a block it was not grown from, any fence with more than 2 joins (a T or cross), and any
    # fence on a closed ring of joins (a picture frame) - the lead's crops.png fault 1 - repeated to a fixpoint.
    def unloop():
        while True:
            bad = set()
            fences = [p for p, b in blocks.items() if b == F]
            for p in fences:
                js = game_joins(p)
                if len(js) > 2 or any(frozenset((p, q)) not in links for q in js):
                    bad.add(p)
            if not bad:
                for p in fences:                                  # a cycle through p: p's two joins reach each other
                    js = game_joins(p)                            # without p (through fences or solid blocks)
                    if len(js) == 2:
                        seen, todo = {p, js[0]}, [js[0]]
                        while todo:
                            q = todo.pop()
                            for r in game_joins(q):
                                if r == js[1]:
                                    bad.add(p)
                                    todo = []
                                    break
                                if r not in seen:
                                    seen.add(r)
                                    todo.append(r)
                        if p in bad:
                            break
            if not bad:
                return
            for p in bad:
                del blocks[p]
    unloop()
    # round 9: prune fence pieces that would float - no sideways join along their limb and nothing above or below -
    # repeated, since removing one can strand the next
    def joined(p):
        x, y, z = p
        for dx, dz in ((0, -1), (0, 1), (-1, 0), (1, 0)):
            q = (x + dx, y, z + dz)
            if q in blocks and frozenset((p, q)) in links:
                return True
        return (x, y - 1, z) in blocks or (x, y + 1, z) in blocks
    while True:
        loose = [p for p, b in blocks.items() if b == F and not joined(p)]
        if not loose:
            break
        for p in loose:
            del blocks[p]
    # round 10 (review: orphan L-shaped fence pieces and single blocks floating beside the trunks): keep only what is
    # joined to the trunk's foot through face-adjacent blocks - everything else is deleted
    root = min((p for p in blocks if p[1] == 0), key=lambda p: (p[0] - c) ** 2 + (p[2] - c) ** 2)
    seen, todo = {root}, [root]
    while todo:
        x, y, z = todo.pop()
        here = blocks[(x, y, z)]
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    n = abs(dx) + abs(dy) + abs(dz)
                    if n != 1:
                        continue                                   # round 12: faces only - edge-joined wood read as
                                                                   # floating fragments from above (eyes 39575471457)
                    q = (x + dx, y + dy, z + dz)
                    if q not in blocks or q in seen:
                        continue
                    if (here == F and blocks[q] == F and dy == 0
                            and frozenset(((x, y, z), q)) not in links):
                        continue                                   # side-by-side fences of different limbs
                    seen.add(q)
                    todo.append(q)
    pruned = [p for p in blocks if p not in seen]
    for p in pruned:
        del blocks[p]
    if not giant and pruned:
        # r22: a small tree is built 4-connected on purpose - anything the connectivity pass deletes is a bug
        print("  bare_tree(%d, %s): %d of %d blocks pruned (floating or edge-joined)" % (seed, wood, placed - len(blocks), placed))
    # r23: the foot - a ring of soil replacing the grass round the trunk (dead: coarse dirt with podzol; bare: podzol
    # with coarse dirt), and a denser skirt on it: ferns, 2-high tall grass / large ferns, dead bushes under dead trees
    # (lead on crops.png fault 3: the r22 skirt was invisible specks from RTS height)
    fx, fz = c, c
    rad = 5.2 if giant else 3.0
    # r26 (lead on r25: the lobed / patchy rings scattered single dirt squares 4-6 blocks out into the lawn and stamped
    # a plus pattern - decals): back to r24's tight anchored ring. Solid for the inner two thirds, the radius pushed
    # out ~1 block on the SHADOW side (the sun travels -x / +z in the game, so the ring sits under the trunk's shadow
    # like a real tree's bare patch), a soft edge in the last block only, and never an isolated tile.
    SHX, SHZ = -0.94, 0.33                                        # the ring's long side: where the shadow falls
    for px in range(int(fx - rad - 2), int(fx + rad + 3)):
        for pz in range(int(fz - rad - 2), int(fz + rad + 3)):
            d = math.hypot(px - fx, pz - fz)
            if (px, 0, pz) in blocks or not (0 <= px < size and 0 <= pz < size):
                continue
            toward = ((px - fx) * SHX + (pz - fz) * SHZ) / d if d else 0.0
            r_here = rad + (0.9 if giant else 0.8) * max(0.0, toward) - 0.3 * max(0.0, -toward)
            if d > r_here + 0.5:
                continue
            if d > r_here * 0.67 and d > r_here - 1.0 and rng.random() > (r_here + 0.5 - d):
                continue                                            # the soft edge: the last block only
            r = rng.random()
            if dead:
                soil[(px, 0, pz)] = "coarse_dirt" if r < 0.7 or d < 1.6 else ("podzol", {"snowy": "false"})
            else:
                soil[(px, 0, pz)] = ("podzol", {"snowy": "false"}) if r < 0.6 or d < 1.6 else "coarse_dirt"
    while True:                                                   # no lone tile: every soil block has a soil / wood
        lone = [p for p in soil if not any((p[0] + dx, 0, p[2] + dz) in soil or (p[0] + dx, 0, p[2] + dz) in blocks
                                           for dx, dz in H4_XZ)]  # face neighbour on the ground
        if not lone:
            break
        for p in lone:
            del soil[p]
    inner = 2.2 if giant else 1.2
    for (px, _, pz) in list(soil):
        d = math.hypot(px - fx, pz - fz)
        if d < inner or (px, 1, pz) in blocks:
            continue
        r = rng.random()
        edge = d > rad - 1.2
        if dead:
            # r25: fewer dead bushes (they read as dropped orange sticks) - mostly short grass, one or two bushes
            kind = None if r > 0.4 else "dead_bush" if r < 0.08 else "grass" if r < 0.3 else "tall_grass"
        else:
            kind = None if r > 0.7 else "fern" if r < 0.25 else "large_fern" if r < 0.45 else \
                "tall_grass" if r < 0.6 else "grass"
        if kind in ("tall_grass", "large_fern"):
            if (px, 2, pz) in blocks or edge and rng.random() < 0.5:
                kind = "fern" if kind == "large_fern" else "grass"
            else:
                plants[(px, 1, pz)] = (kind, {"half": "lower"})
                plants[(px, 2, pz)] = (kind, {"half": "upper"})
                continue
        if kind:
            plants[(px, 1, pz)] = kind
    # r25: a real fallen log under a dead tree (lead on r24: the 3-4 flush tiles and the bushes read as loose sticks):
    # 4-5 blocks of the dead tree's own bark lying ON the ring (y = 1, axis along its run, so bark shows on top and it
    # casts a shadow), a moss carpet on one or two of them, its root end a block from the trunk
    fallen_blocks = {}
    if dead and fallen:
        dx, dz = rng.choice(((1, 0), (-1, 0), (0, 1), (0, -1)))
        side = rng.choice((1, -1))
        px, pz = c + dx * 2 + dz * side, c + dz * 2 + dx * side
        L = rng.randint(4, 5)
        if W.endswith("_wood"):
            log_block, log_props = W[:-5] + "_log", {"axis": "x" if dx else "z"}
        else:
            log_block, log_props = W, {}
        for i in range(L):
            q = (px + dx * i, 1, pz + dz * i)
            if q in blocks or (q[0], 0, q[2]) in blocks or not (0 <= q[0] < size and 0 <= q[2] < size):
                break
            fallen_blocks[q] = (log_block, log_props)
            if (q[0], 0, q[2]) not in soil:
                soil[(q[0], 0, q[2])] = "coarse_dirt"           # the log lies on soil, never on bare lawn
            plants.pop((q[0], 1, q[2]), None)
            plants.pop((q[0], 2, q[2]), None)
        # r26 (lead on r25: moss on blocks 1 and L-2 alternated grey / green - a checker stripe): moss is ONE
        # continuous strip of 2-3 blocks along the top, or none
        laid = sorted(q for q in fallen_blocks)
        if laid and rng.random() < 0.75:
            m = min(len(laid), rng.randint(2, 3))
            s0 = rng.randint(0, len(laid) - m)
            for q in laid[s0:s0 + m]:
                fallen_blocks[(q[0], 2, q[2])] = ("moss_carpet", {})
    # fences connect to the fences and solid wood beside them
    out = {}
    for (x, y, z), b in blocks.items():
        props = {}
        if b == F:
            for side, (dx, dz) in (("north", (0, -1)), ("south", (0, 1)), ("west", (-1, 0)), ("east", (1, 0))):
                q = (x + dx, y, z + dz)
                props[side] = "true" if q in blocks and frozenset(((x, y, z), q)) in links else "false"
            props["waterlogged"] = "false"
        elif b.endswith(("_wood", "_log")):
            props["axis"] = "y"
        out[(x, y, z)] = (b, props)                                 # r25: a non-log dead block keeps its default state
    for src in (soil, plants, fallen_blocks):
        for p, b in src.items():                                   # the ground ring and the skirt, where no wood sits
            if p not in blocks and 0 <= p[0] < size and 0 <= p[2] < size:
                out[p] = (b[0], b[1]) if isinstance(b, tuple) else (b, {})
    return out, size, height


# r24 giant crown knobs (lead on r23: a lattice with see-through holes, a black mass at dusk): fewer, thicker limbs
# r23 was (5, 6), 3, 6, 4, 0: 5-6 limbs each splitting in 3 twice. r24: 4 limbs, each splitting in 2, a third longer,
# 2 wide for the first two levels - a few solid limbs with daylight between them instead of a mesh of 1-wide sticks.
GIANT_LIMBS, GIANT_FORKS, GIANT_LEN, GIANT_LEVELS, GIANT_THICK_LEVEL = (4, 4), 2, 8, 4, 0
# r25: THICK_LEVEL 0 - 2 wide (1 deep) on the main limbs only, 1 wide for two levels, fence tips: the taper the lead asked for
BARE = {"bare_oak": dict(wood="spruce"), "dead_tree": dict(wood="birch", dead=True),   # dead: acacia_wood (r25)
        "giant_bare": dict(wood="spruce", giant=True)}


def main():
    os.makedirs(OUT, exist_ok=True)
    trees = []
    for name, fn in SPECIES.items():
        for v in range(VARIANTS):
            seed = 1000 + 17 * v + 101 * list(SPECIES).index(name)
            tree = fn(seed, (8, 10, 12, 9)[v]) if name == "spruce" else \
                fn(seed, old=(v == 3), form=("spread", "twin", "tall", "twin")[v]) if name == "oak" else \
                fn(seed, ("straight", "fork", "lean", "straight")[v]) if name == "birch" else fn(seed)
            s = Structure(SIZE, H, SIZE)
            dropped = tree.write(s)
            s.write(os.path.join(OUT, "%s_%d.nbt" % (name, v + 1)))
            if dropped:
                print("  %s_%d: %d leaves further than 6 from a log were left out" % (name, v + 1, dropped))
            trees.append(tree)
    # two samplers for the eyes `structure` lane (one big grid was framed off-centre with trees cut off, reviewer):
    # tree_sampler_a = oak + birch rows, tree_sampler_b = spruce + acacia rows, 3 variants each, on grass
    gap = 10
    for half, label in ((0, "a"), (1, "b"), (2, "c")):     # c = pine + cherry
        s = Structure(VARIANTS * gap + 2, H, 2 * gap + 2)
        s.fill(0, 0, 0, VARIANTS * gap + 1, 0, 2 * gap + 1, "grass_block", snowy="false")
        for k, tree in enumerate(trees[half * 2 * VARIANTS:(half + 1) * 2 * VARIANTS]):
            row, col = divmod(k, VARIANTS)
            ox, oz = col * gap + 1 + gap // 2 - C, row * gap + 1 + gap // 2 - C
            shifted = Tree(tree.log, tree.leaves)
            shifted.logs = {(x, y + 1, z): a for (x, y, z), a in tree.logs.items()}
            shifted.leaf = {(x, y + 1, z) for (x, y, z) in tree.leaf}
            shifted.plants = {(x, y + 1, z): b for (x, y, z), b in tree.plants.items()}
            if hasattr(tree, "second"):
                shifted.second = (tree.second[0], {(x, y + 1, z) for (x, y, z) in tree.second[1]})
            if hasattr(tree, "kinds"):
                shifted.kinds = {(x, y + 1, z): b for (x, y, z), b in tree.kinds.items()}
            _write_clipped(s, shifted, ox, oz)
        s.write(os.path.join(OUT, "tree_sampler_%s.nbt" % label))
    # ground scatter: one .nbt per piece (7 x 4 x 7 footprint, the piece centred) and a sampler with all of them
    PS, PH = 9, 4
    pieces = []
    for name, (fn, count) in SCATTER.items():
        for v in range(count):
            seed = 5000 + 31 * v + 211 * list(SCATTER).index(name)
            piece = fn(seed)
            s = Structure(PS, PH, PS)
            off = C - PS // 2
            _write_piece(s, piece, -off, 0)
            s.write(os.path.join(OUT, "%s_%d.nbt" % (name, v + 1)))
            pieces.append(piece)
    cols, gap = 6, 7
    rows = (len(pieces) + cols - 1) // cols
    s = Structure(cols * gap + 2, PH + 1, rows * gap + 2)
    s.fill(0, 0, 0, cols * gap + 1, 0, rows * gap + 1, "grass_block", snowy="false")
    for k, piece in enumerate(pieces):
        row, col = divmod(k, cols)
        _write_piece(s, piece, col * gap + 1 + gap // 2 - C, row * gap + 1 + gap // 2 - C, lift=1)
    s.write(os.path.join(OUT, "scatter_sampler.nbt"))
    # bare trees: 3 variants of each small kind, 2 giants, each in its own .nbt; one sampler with all of them
    bare = []
    for name, kw in BARE.items():
        for v in range(2 if kw.get("giant") else 3):
            blocks, size, height = bare_tree(7000 + 37 * v + 307 * list(BARE).index(name), **kw)
            s = Structure(size, height, size)
            for (x, y, z), (b, props) in blocks.items():
                s.set(x, y, z, b, **props)
            s.write(os.path.join(OUT, "%s_%d.nbt" % (name, v + 1)))
            bare.append((blocks, size))
    s = Structure(80, 31, 44)
    s.fill(0, 0, 0, 79, 0, 43, "grass_block", snowy="false")
    x = 1
    for blocks, size in bare:                                    # small ones in a row at the front, giants behind
        if size > 15:
            continue
        for (bx, by, bz), (b, props) in blocks.items():
            if 0 <= x + bx < 80 and by < 31 and 29 + bz < 44:   # r23: y = 0 replaces the slab (the soil ring)
                s.set(x + bx, by, 29 + bz, b, **props)
        x += 13
    x = 6
    for blocks, size in bare:
        if size <= 15:
            continue
        for (bx, by, bz), (b, props) in blocks.items():
            if 0 <= x + bx < 80 and by < 31 and 0 <= bz < 44:        # r23: giants whole (their twigs were cut off)
                s.set(x + bx, by, bz, b, **props)
        x += 32
    s.write(os.path.join(OUT, "bare_sampler.nbt"))
    # r24: a small sampler for the CI photo runner - the big one (80 x 31 x 44) lost most of its ground to unloaded
    # chunks (limbs hung over white void). The 3 bare oaks (back row) + 3 dead trees (front row) only, in a 3 x 2 grid
    # 13 apart on a 40 x 16 x 24 grass pad - the trees use x 1-12 / z 1-12 of their 15 boxes, so nothing overlaps.
    s = Structure(40, 16, 24)
    s.fill(0, 0, 0, 39, 0, 23, "grass_block", snowy="false")
    small = [(blocks, size) for blocks, size in bare if size <= 15]
    for i, (blocks, size) in enumerate(small):
        ox, oz = -1 + 13 * (i % 3), -1 if i < 3 else 11
        clipped = 0
        for (bx, by, bz), (b, props) in blocks.items():
            if 0 <= ox + bx < 40 and by < 16 and 0 <= oz + bz < 24:
                s.set(ox + bx, by, oz + bz, b, **props)
            else:
                clipped += 1
        if clipped:
            print("  small_bare_sampler: tree %d lost %d blocks to the pad edge" % (i, clipped))
    s.write(os.path.join(OUT, "small_bare_sampler.nbt"))


def _write_piece(s, piece, ox, oz, lift=0):
    if isinstance(piece, dict):                                  # a boulder: plain blocks
        sx, sy, sz = s.size
        for (x, y, z), b in piece.items():
            if 0 <= ox + x < sx and 0 <= y + lift < sy and 0 <= oz + z < sz:
                s.set(ox + x, y + lift, oz + z, b)
        return
    shifted = Tree(piece.log, piece.leaves)
    shifted.logs = {(x, y + lift, z): a for (x, y, z), a in piece.logs.items()}
    shifted.leaf = {(x, y + lift, z) for (x, y, z) in piece.leaf}
    shifted.plants = {(x, y + lift, z): b for (x, y, z), b in piece.plants.items()}
    if hasattr(piece, "second"):
        shifted.second = (piece.second[0], {(x, y + lift, z) for (x, y, z) in piece.second[1]})
    if hasattr(piece, "kinds"):
        shifted.kinds = {(x, y + lift, z): b for (x, y, z), b in piece.kinds.items()}
    _write_clipped(s, shifted, ox, oz)


def _write_clipped(s, tree, ox, oz):
    sx, sy, sz = s.size
    keep = lambda p: 0 <= ox + p[0] < sx and 0 <= p[1] < sy and 0 <= oz + p[2] < sz
    tree.logs = {p: a for p, a in tree.logs.items() if keep(p)}
    tree.leaf = {p for p in tree.leaf if keep(p)}
    tree.plants = {p: b for p, b in tree.plants.items() if keep(p)}
    global H
    old = H
    H = sy
    tree.write(s, ox, oz)
    H = old


if __name__ == "__main__":
    main()
