"""Engine Shed T1 r12: lovish's four picks on r11 (Oct 30 ~00:30 ET, after r11 round 3: "looking better"), as camera
tests on tools/engine_shed_r12.py's build. Written BEFORE the r12 changes and run red on r11 (engine_shed_r12.detail
as a stub returning r11's build). Run: python3 -m unittest tools/test_engine_shed_r12.py

lovish's picks (his words, as the coordinator passed them on):
1. chimneys, "probably both": a smooth tapered shaft and ONE corbelled crown, from shaped blocks (our arch blocks #407,
   Macaw's, FramedBlocks / Domum #398); the stone foot meets the roof;
2. dusk light warm orange and softer - the light inside the rooms, not glowing faces;
3. the tower roof less stepped - a smooth spire or a clock cupola; copper saturation 0.16-0.30 (camera_colour);
4. soot and weathering, and the gate must not read as an obsidian lump.
Out of scope: the ridge posts; the footprint, massing and roof bands are frozen.

Every r11 test runs on r12 unchanged (EngineShedR12 subclasses EngineShedR11), except the ones that encode a look lovish
rejected on Oct 30 - each retired below with a docstring naming him. Thresholds unchanged; tone checks are bands.
"""
import os
import sys
import unittest
from collections import Counter, deque

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import camera_colour as CC  # noqa: E402
import schem_study as S  # noqa: E402
from test_engine_shed_r11 import HAVE_NUMPY, EngineShedR11, full  # noqa: E402

if HAVE_NUMPY:
    import engine_shed_r12 as E  # noqa: E402
    import lab_vehicle_brass as L  # noqa: E402
    from test_engine_shed_r10 import SLATE_FAMILY, Pictures, base, light  # noqa: E402

EAVE = 6
# lights whose own colour reads warm (orange / amber) - the pick is "warm orange"; pearlescent froglight reads
# white-pink, a sea lantern / end rod cold
WARM_LIGHTS = ("shroomlight", "ochre_froglight", "lantern", "glowstone", "torch", "candle", "campfire")
ROOM_LIT = 10          # block light on a surface seen through a window: a lit room at dusk (15 at the source)
GLOW_SEEN = 0.10       # at most this share of a window's glass pixels show a light block straight behind the pane
ROOM_SEEN = 0.50       # at least this share show a lit room surface
FRAME_VAL = (0.27, 0.50)   # the gate frame's camera value: mid stone - not black (blackstone 0.13-0.22), not snow
SOOT_DARK = 0.80       # a soot cell reads at most 0.8 x the value of brick on the same face, and grey (sat <= 0.15)


def moulding(n):
    return n.startswith("reignofnether:")


def sloped(n):
    """A roof-mod slope (Macaw's mcwroofs:*): a smooth incline, not a stair step."""
    return n.startswith("mcwroofs:")


@unittest.skipUnless(HAVE_NUMPY, "numpy not installed")
class EngineShedR12(EngineShedR11):
    @classmethod
    def setUpClass(cls):
        import tempfile
        cls.tmp = tempfile.TemporaryDirectory()
        cls.out = os.path.join(cls.tmp.name, "brass_t1_r12.nbt")
        cls.rig = L.write(E.detail(), cls.out)
        cls.p = Pictures(cls.out)
        cls.m = E.META
        cls.table = CC.load()
        cls.light = block_light(cls.p.n)

    # ------------------------------------------------------------------------------------------- retired on r12
    @unittest.skip("retired, lovish Oct 30 ~00:30: chimneys 'probably both' - a smooth tapered shaft, ONE crown")
    def test_stacks_banded(self):
        """r11 asked for >= 2 projecting bands on each stack (towers.md, the study builds' iron bands). lovish rejected
        the ring stack on Oct 30 ("probably both": a smooth tapered shaft and one corbelled crown); the critic counted
        6 and 5 rings. Replaced by test_r12_one_crown_no_rings."""

    @unittest.skip("retired, lovish Oct 30 ~00:30: one corbelled crown, no bands to space")
    def test_r3_stack_bands_spaced(self):
        """r11 round 3 spaced the stacks' bands >= 3 courses apart; with lovish's single crown there are no bands."""

    @unittest.skip("retired, lovish Oct 30 ~00:30: 'warm orange' dusk light")
    def test_r3_lit_rooms_not_orange(self):
        """r11 round 3 banned shroomlight (the round-2 critic's "orange sparks"); lovish asked for the dusk light back to
        warm orange, softer, inside the rooms. The sparks stay banned as what they were - a light block seen through
        the glass: test_r12_windows_show_lit_rooms_not_glowing_faces."""

    @unittest.skip("retired, lovish Oct 30 ~00:30: light inside the rooms, not glowing faces")
    def test_every_camera_window_shows_a_light_behind(self):
        """r10 (e) asked that >= 25 % of each window's glass pixels show a LIGHT BLOCK first behind the pane - the white
        glowing pane lovish turned down ("the light inside the rooms, not glowing faces"). Replaced by
        test_r12_windows_show_lit_rooms_not_glowing_faces (a lit room surface behind the glass, the source hidden)."""

    def test_tower_and_stack_faces_have_relief(self):
        """r11's no-run-of-more-than-3-full-blocks rule, on the TOWER only (threshold unchanged). The stacks part is
        retired: lovish (Oct 30) asked for a smooth shaft - a band or reveal every 3 courses is the ring stack he turned
        down. The stacks' faces are judged by test_r12_stack_shaft_is_smooth instead."""
        bad = []
        for still in ("rts_close", "34"):
            for face, a in (("west", 2), ("south", 0)):
                cells = {h[0] for h in self.p.hits[still].values() if h[2] == face and full(self.p.n[h[0]])
                         and self.in_tower(h[0], 0)}
                for axis in (a, 1):
                    for q in cells:
                        prev = list(q)
                        prev[axis] -= 1
                        if tuple(prev) in cells:
                            continue
                        k, r = list(q), 0
                        while tuple(k) in cells:
                            r += 1
                            k[axis] += 1
                        if r > 3:
                            bad.append((still, face, "row" if axis != 1 else "column", q, r))
        self.assertEqual(sorted(bad)[:10], [], len(bad))

    def test_tower_shows_three_stages(self):
        """r11's base / shaft / clock stage / roof test, thresholds unchanged; its material classifier reads the roof
        by look (lesson 2): a Macaw's slope of prismarine brick is the weathered-copper roof, not brick."""
        def fam(n):
            if "copper" in n or sloped(n) or base(n) in SLATE_FAMILY and "stone_brick" not in n:
                return "roof"
            if S.glyph(n) in "g*":
                return "glass"
            if "brick" in n and "stone" not in n and "blackstone" not in n and "deepslate" not in n:
                return "brick"
            if "blackstone" in n or "deepslate" in n:
                return "dark"
            return "pale"
        for still in ("rts", "34"):
            cells = {h[0] for h in self.p.hits[still].values() if self.in_tower(h[0], 0) or
                     (self.in_tower(h[0]) and h[0][1] >= self.m["cornice"])}
            rows = {}
            for q in cells:
                rows.setdefault(q[1], Counter())[fam(self.p.n[q])] += 1
            seq = [rows[y].most_common(1)[0][0] for y in sorted(rows)]
            runs = []
            for f in seq:
                if runs and runs[-1][0] == f:
                    runs[-1][1] += 1
                else:
                    runs.append([f, 1])
            self.assertGreaterEqual(len({f for f, k in runs if k >= 2}), 3, (still, runs))
            self.assertTrue([q for q in cells if q[1] == self.m["cornice"] and not self.in_tower(q, 0)],
                            (still, "cornice oversail"))

    def test_build_is_sound(self):
        L.check(self.rig, self.out)
        L.check_banned(self.rig, "brass_t1_r12")

    def test_committed_nbt_is_current(self):
        """design/vehicle_labs/brass_t1_r12.nbt (what the eyes run photographs) is this build, cell for cell."""
        import preview_structure as PS
        path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), E.OUT)
        self.assertTrue(os.path.exists(path), path)
        want, got = PS.load_nbt(path), PS.load_nbt(self.out)
        cw = {tuple(b["pos"]): str(want["palette"][b["state"]]["Name"]) for b in want["blocks"]}
        cg = {tuple(b["pos"]): str(got["palette"][b["state"]]["Name"]) for b in got["blocks"]}
        self.assertEqual(cw, cg, "stale - rerun python3 tools/engine_shed_r12.py")

    # ------------------------------------------------------------------------------------------- pick 1: chimneys
    def stack_parts(self, s):
        """(foot top, ring(y), plan width(y)) of a stack: the foot is the 4 x 4 stone base; ring(y) the cells round the
        2 x 2 shaft footprint; width(y) its plan area in blocks (full 1, stair 0.75, slab / slope / wall / moulding 0.5)."""
        p = self.p
        x0, x1, z0, z1 = s["base"]
        sx0, sx1, sz0, sz1 = s["shaft"]
        foot = max(y for y in range(self.m["stack_from"], s["top"]) if all(     # full stone blocks: a stair or a
            full(p.n.get((x, y, z), "air")) and "stone" in p.n[(x, y, z)]       # roof-mod wash over it is not foot
            and "blackstone" not in p.n[(x, y, z)] for x in (x0, x1) for z in range(z0 + 1, z1)))

        def ring(y):
            return [(x, y, z) for x in range(sx0 - 1, sx1 + 2) for z in range(sz0 - 1, sz1 + 2)
                    if not (sx0 <= x <= sx1 and sz0 <= z <= sz1)]

        def weight(n):
            return 0 if n is None else 1.0 if full(n) else 0.75 if S.glyph(n) == "s" else 0.5

        def width(y):
            return sum(weight(p.n.get((x, y, z))) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1))
        return foot, ring, width

    def test_r12_one_crown_no_rings(self):
        """lovish: ONE corbelled crown, not a stack of rings (the critic counted 6 and 5). Above each stack's stone foot
        exactly one course projects from the shaft with nothing round the shaft over and under it - the crown, at
        top - 1."""
        p = self.p
        for s in self.m["stacks"]:
            foot, ring, _ = self.stack_parts(s)
            bands = [y for y in range(foot + 1, s["top"] + 1) if sum(q in p.n for q in ring(y)) >= 6 and all(
                not any((q[0], y + d, q[2]) in p.n for q in ring(y)) for d in (-1, 1))]
            self.assertEqual(bands, [s["top"] - 1], (s["top"], foot))

    def test_r12_crown_corbelled_from_moulding_blocks(self):
        """The crown is our own corbelled cornice (#407: reignofnether:cornice_*, a 13 px corbel at 1-3 px steps), not
        brick stairs: >= 8 of the crown ring's cells, every one of its blocks a moulding; the 34 still sees it."""
        p = self.p
        s34 = {h[0] for h in p.hits["34"].values()}
        for s in self.m["stacks"]:
            _, ring, _ = self.stack_parts(s)
            crown = [q for q in ring(s["top"] - 1) if q in p.n]
            self.assertGreaterEqual(len(crown), 8, (s["top"], crown))
            self.assertTrue(all(moulding(p.n[q]) and "cornice" in p.n[q] for q in crown),
                            (s["top"], Counter(p.n[q] for q in crown)))
            self.assertTrue(set(crown) & s34, (s["top"], "crown not seen in 34"))

    def test_r12_stack_tapers_smoothly(self):
        """A tapered shaft: from the foot up to the crown each stack's plan width never grows, narrows at least twice
        (foot -> lower shaft -> upper shaft), and every narrowing is made by sloped pieces (a Macaw's roof wash on the
        narrowing course or the one under it), with no stair ledge on either."""
        p = self.p
        for s in self.m["stacks"]:
            foot, ring, width = self.stack_parts(s)
            ys = list(range(foot, s["top"] - 1))
            w = [width(y) for y in ys]
            self.assertTrue(all(b <= a for a, b in zip(w, w[1:])), (s["top"], list(zip(ys, w))))
            drops = [y for y, a, b in zip(ys[1:], w, w[1:]) if b < a]
            self.assertGreaterEqual(len(drops), 2, (s["top"], list(zip(ys, w))))
            for y in drops:
                at = [p.n[(x, yy, z)] for yy in (y - 1, y) for x in range(s["base"][0], s["base"][1] + 1)
                      for z in range(s["base"][2], s["base"][3] + 1) if (x, yy, z) in p.n]
                self.assertTrue(any(sloped(n) for n in at), (s["top"], y, Counter(at)))
                self.assertFalse([n for n in at if S.glyph(n) == "s"], (s["top"], y, "a stair ledge"))

    def test_r12_stack_shaft_is_smooth(self):
        """The shaft itself carries no reveals: every cell of the 2 x 2 shaft from the foot's top up to the crown is a
        full block (brick, or its soot) - no set-back stair courses (r11's relief every 3)."""
        p = self.p
        for s in self.m["stacks"]:
            foot, _, _ = self.stack_parts(s)
            sx0, sx1, sz0, sz1 = s["shaft"]
            bad = [(q, p.n.get(q)) for y in range(foot + 1, s["top"] - 1) for x in range(sx0, sx1 + 1)
                   for z in range(sz0, sz1 + 1) for q in [(x, y, z)] if not (q in p.n and full(p.n[q]))]
            self.assertEqual(bad, [], s["top"])

    # ------------------------------------------------------------------------------------------- pick 2: dusk light
    def test_r12_windows_show_lit_rooms_not_glowing_faces(self):
        """lovish: dusk light warm and softer, "inside the rooms, not glowing faces". Per camera window (the r10 window
        groups), of the glass pixels the dusk still shows: <= 10 % have a light block as the first block behind the pane
        (a glowing face), >= 50 % a room surface lit to block light >= 10 (the source hidden from the camera)."""
        groups = self.camera_windows()
        self.assertGreaterEqual(len(groups), 6)
        hits = [h for h in self.p.hits["dusk"].values() if S.glyph(h[1]) == "g"]
        bad = []
        for g in groups:
            mine = [h for h in hits if h[0] in g]
            if not mine:
                bad.append((min(g), "no glass seen"))
                continue
            glow = sum(1 for h in mine if h[3] and light(h[3][1]))
            room = sum(1 for h in mine if h[3] and not light(h[3][1]) and surface_light(self.light, self.p.n, h[3][0])
                       >= ROOM_LIT)
            if glow > GLOW_SEEN * len(mine) or room < ROOM_SEEN * len(mine):
                bad.append((min(g), len(mine), glow, room))
        self.assertEqual(bad, [], "%d windows" % len(groups))

    def test_r12_room_light_is_warm(self):
        """The rooms' light sources read warm by their own colour: every light block within 4 blocks of a camera
        window's glass is a warm one (WARM_LIGHTS - shroomlight, ochre froglight, lantern ...), none pearlescent
        froglight (the white-pink r11 glow)."""
        glass = {q for g in self.camera_windows() for q in g}
        near = {q: n for q, n in self.p.n.items() if light(n) and any(
            max(abs(q[0] - a[0]), abs(q[1] - a[1]), abs(q[2] - a[2])) <= 4 for a in glass)}
        self.assertTrue(near)
        cold = sorted({n for n in near.values() if not any(k in n for k in WARM_LIGHTS)})
        self.assertEqual(cold, [])

    # ------------------------------------------------------------------------------------------- pick 3: tower roof
    def tower_roof_hits(self, still):
        return [h for h in self.p.hits[still].values() if self.in_tower(h[0]) and h[0][1] > self.m["cornice"]
                and S.glyph(h[1]) not in "|*"]

    def test_r12_tower_roof_smooth_not_stepped(self):
        """lovish: the tower roof "less stepped" (the critic: 4-5 concentric steps). Of the tower roof's pixels in
        rts_close and 34, >= 80 % are smooth slopes (Macaw's roof pieces) and <= 10 % stairs."""
        for still in ("rts_close", "34"):
            hits = self.tower_roof_hits(still)
            self.assertGreater(len(hits), 100, still)
            slope = sum(sloped(h[1]) for h in hits) / len(hits)
            stair = sum(S.glyph(h[1]) == "s" for h in hits) / len(hits)
            self.assertGreaterEqual(slope, 0.80, (still, round(slope, 3), Counter(h[1] for h in hits).most_common(4)))
            self.assertLessEqual(stair, 0.10, (still, round(stair, 3)))

    def test_r12_tower_roof_is_a_spire(self):
        """A spire, not a low pyramid (the critic's "temple"): the roof's rise over the cornice is >= 1.8 x its
        half-width (r11: 3 over 2.5 = 1.2)."""
        x0, x1, z0, z1 = self.m["tower"]
        roof = [q for q, n in self.p.n.items() if self.in_tower(q) and q[1] > self.m["cornice"]
                and S.glyph(n) not in "|*"]
        rise = max(q[1] for q in roof) - self.m["cornice"]
        half = (x1 - x0 + 3) / 2
        self.assertGreaterEqual(rise / half, 1.8, (rise, half))

    def test_r12_no_dark_ring_on_the_tower_roof(self):
        """The critic: "black corner pieces draw a dark ring pattern ... a target from above". No dark-family cell
        (deepslate, blackstone, coal) on the tower roof in rts_close or 34."""
        bad = Counter((h[0], h[1]) for st in ("rts_close", "34") for h in self.tower_roof_hits(st)
                      if any(k in h[1] for k in ("deepslate", "blackstone", "coal")))
        self.assertEqual(sorted(bad)[:8], [])

    # ------------------------------------------------------------------------------------------- pick 4: soot, gate
    def soot(self, name, face):
        """Soot by look (camera_colours): at most SOOT_DARK x the value of brick on that face, and grey."""
        c, b = CC.lookup(self.table, name, face), CC.lookup(self.table, "bricks", face)
        return bool(c and b) and c[2] <= SOOT_DARK * b[2] and c[1] <= 0.15

    def test_r12_soot_at_every_stack_crown(self):
        """lovish: soot. Each stack's top (the crown course and the 3 under it, the cap) shows >= 3 soot cells in 34 or
        rts_close, and soot fades down: the shaft under that shows at most a third of the top's soot share."""
        for s in self.m["stacks"]:
            foot, _, _ = self.stack_parts(s)
            seen = {}
            for st in ("34", "rts_close"):
                for h in self.p.hits[st].values():
                    if self.in_stack(h[0], s, 0) and h[0][1] > foot and not moulding(h[1]):
                        seen[h[0]] = seen.get(h[0]) or self.soot(h[1], h[2])
            top = [q for q in seen if q[1] >= s["top"] - 4]
            low = [q for q in seen if q[1] < s["top"] - 4]
            n_top = sum(seen[q] for q in top)
            self.assertGreaterEqual(n_top, 3, (s["top"], n_top))
            if low:
                self.assertLessEqual(sum(seen[q] for q in low) / len(low), n_top / len(top) / 3, s["top"])

    def test_r12_soot_streaks_under_the_eaves(self):
        """lovish: soot and weathering (the critic: "no streaks under the eaves"). >= 4 streaks on the camera faces'
        brick walls in 34 or rts_close (the gate frame is not wall): 2+ soot cells one over the other at y 2..eave, the top
        one under an eave (a wall, dentil or roof cell within 2 above)."""
        p = self.p
        cells, frame = {}, self.gate_frame()
        for st in ("34", "rts_close"):
            for h in p.hits[st].values():
                if h[2] in ("west", "south") and 2 <= h[0][1] <= EAVE and not self.in_tower(h[0]) and not any(
                        self.in_stack(h[0], s) for s in self.m["stacks"]) and h[0] not in frame:
                    cells[h[0]] = cells.get(h[0]) or self.soot(h[1], h[2])
        dark = {q for q, v in cells.items() if v}
        streaks = [q for q in dark if (q[0], q[1] - 1, q[2]) in dark and (q[0], q[1] + 1, q[2]) not in dark
                   and any((q[0], q[1] + k, q[2]) in p.n for k in (1, 2))]
        self.assertGreaterEqual(len(streaks), 4, sorted(streaks))

    def gate_frame(self):
        """The gate (the lane's mouth at x 0, between its jambs z 9 and 15 and under its head at y 9): the portal over
        the rails at the front - not the coal stage on the left plinth (x 1-2, z 1-3), the other dark mass the critic
        saw."""
        z0, z1 = L.LANE_Z[0] - 1, L.LANE_Z[-1] + 1
        return {(0, y, z) for y in range(1, 10) for z in (z0, z1)} | {(0, 9, z) for z in range(z0, z1 + 1)}

    def test_r12_gate_frame_not_an_obsidian_lump(self):
        """lovish: the gate "must not read as an obsidian lump" (the critic: "near-black untextured beams"). The gate
        frame's pixels in rts and 34, each coloured as the stills show its block and face: >= 70 % read mid stone, camera
        value 0.27-0.50 (both edges: not blackstone's 0.13-0.22, not a snow-white U-frame - r4's owl face)."""
        frame = self.gate_frame()
        for still in ("rts", "34"):
            hits = [h for h in self.p.hits[still].values() if h[0] in frame]
            self.assertGreater(len(hits), 50, still)
            vals = [CC.lookup(self.table, h[1], h[2]) for h in hits]
            mid = sum(1 for v in vals if v and FRAME_VAL[0] <= v[2] <= FRAME_VAL[1]) / len(hits)
            self.assertGreaterEqual(mid, 0.70, (still, round(mid, 3), Counter(h[1] for h in hits).most_common(4)))


def block_light(n):
    """{cell: block light} - a flood from every light block (15) through every cell that is not a full cube, -1 a
    step (Minecraft's block light, without the sky)."""
    lv = {}
    todo = deque()
    for q, name in n.items():
        if light(name):
            lv[q] = 15
            todo.append(q)
    while todo:
        q = todo.popleft()
        v = lv[q] - 1
        if v <= 0:
            continue
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            r = (q[0] + d[0], q[1] + d[1], q[2] + d[2])
            if not (-2 <= r[0] <= 40 and -1 <= r[1] <= 40 and -2 <= r[2] <= 40):
                continue
            if r in n and full(n[r]):
                continue
            if lv.get(r, 0) < v:
                lv[r] = v
                todo.append(r)
    return lv


def surface_light(lv, n, q):
    """The light a block's faces show: the brightest open cell beside it."""
    return max((lv.get((q[0] + a, q[1] + b, q[2] + c), 0) for a, b, c in ((1, 0, 0), (-1, 0, 0), (0, 1, 0),
                                                                           (0, -1, 0), (0, 0, 1), (0, 0, -1))
                if not ((q[0] + a, q[1] + b, q[2] + c) in n and full(n[(q[0] + a, q[1] + b, q[2] + c)]))), default=0)


# the r11 class must not run twice from this module
del EngineShedR11

if __name__ == "__main__":
    unittest.main()
