"""tools/plant_sprites.py + the plant wiring in tools/gen_blocks.py (round 34, the tufts from the RTS camera).

Run: python3 -m unittest discover -s tools -p 'test_*.py'

The r33 critic scored the tufts 4/10 (default) and 6/10 (preset): ~40 blades 4 texels wide at 128x, minified ~4x at the
default RTS zoom (mip 2, 32 texels per block), blurred into smudges. Round 34 redraws them as a few bold blades
(claude/research-grass-rts-zoom-oct28.md); these checks keep the rules that make that work, and keep every blockstate
pointing at a model and a texture the pack really writes when the number of shapes changes.

Round 35 (critic r34: "vary scale (3 sizes)"; "ferns and tall grass are indistinguishable"): short grass in three
sizes drawn on the same card (the blades keep their widths), and the fern as a low, wide rosette of tilted frond cards.
"""
import collections
import math
import os
import sys
import unittest

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
import gen_blocks  # noqa: E402
import plant_sprites as ps  # noqa: E402

N = 128


def _mip_alpha(alpha, levels):
    """Box-filtered alpha, `levels` halvings down, cut at 0.5 - how much of the sprite is still drawn there."""
    a = alpha.astype(np.float64)
    for _ in range(levels):
        h, w = a.shape
        a = a.reshape(h // 2, 2, w // 2, 2).mean((1, 3))
    return a >= 0.5


def _sprites():
    """name -> the drawn alpha of every plant sprite (both halves of the two-block ones, as one canvas). Short grass
    `grass_<shape>` at full size, `grass_<shape>_<size>` at each of ps.SIZES; `fern_card_<k>` the rosette's cards."""
    out = {}
    for v in range(3):
        for size in ps.SIZES:
            H, sh, green, straw = ps.grass_clump(N, np.random.default_rng(7 + v), variant=v, size=size)
            out["grass_%d_%s" % (v, size)] = np.maximum(green, straw)
        out["grass_%d" % v] = out["grass_%d_1.0" % v]
    for v in range(2):
        H, sh, green, straw = ps.grass_clump(N, np.random.default_rng(17 + v), tall=True, variant=v)
        out["tall_grass_%d" % v] = np.maximum(green, straw)
        out["fern_card_%d" % v] = ps.fern_card(N, np.random.default_rng(27 + v), variant=v)[2]
    out["large_fern"] = ps.fern(N, np.random.default_rng(37), large=True)[2]
    return out


def _height(a):
    """Texels from the bottom of the card to the sprite's highest opaque texel."""
    return N - int(np.nonzero((a > 0.5).any(1))[0].min())


def _reach(a):
    """Texels across the card from the sprite's leftmost to its rightmost opaque texel."""
    cols = np.nonzero((a > 0.5).any(0))[0]
    return int(cols.max() - cols.min() + 1)


def _corners(el):
    """A model element's 8 corners (1/16 block) after its rotation, the way 1.20.1's FaceBakery.applyElementRotation
    turns them: about the axis through the origin, right-handed, then (rescale) x1/cos(angle) across the axis."""
    f, t = el["from"], el["to"]
    pts = np.array([[x, y, z] for x in (f[0], t[0]) for y in (f[1], t[1]) for z in (f[2], t[2])], float)
    r = el.get("rotation")
    if not r:
        return pts
    a = math.radians(r["angle"])
    c, s = math.cos(a), math.sin(a)
    rot = {"x": [[1, 0, 0], [0, c, -s], [0, s, c]], "y": [[c, 0, s], [0, 1, 0], [-s, 0, c]],
           "z": [[c, -s, 0], [s, c, 0], [0, 0, 1]]}[r["axis"]]
    o = np.array(r["origin"], float)
    p = (pts - o) @ np.array(rot).T
    if r.get("rescale"):
        k = 1 / math.cos(a)
        p = p * {"x": [1, k, k], "y": [k, 1, k], "z": [k, k, 1]}[r["axis"]]
    return p + o


def _extent(model):
    """(horizontal span, height) of a model's elements after rotation, in 1/16 block."""
    pts = np.concatenate([_corners(e) for e in model["elements"]])
    span = max(np.ptp(pts[:, 0]), np.ptp(pts[:, 2]))
    return span, pts[:, 1].max() - pts[:, 1].min()


# the eyes RTS camera's direction (run 40812245168, 15_meadow_sampler_rts: the camera at -176, 91, 58 looks at -171, 53, 53)
RTS_DIR = np.array([5.0, -38.0, -5.0]) / np.linalg.norm([5.0, -38.0, -5.0])


def _rotate(el, p):
    """Points (1/16 block) of an element turned by its rotation, as _corners turns its corners."""
    r = el.get("rotation")
    if not r:
        return p
    a = math.radians(r["angle"])
    c, s = math.cos(a), math.sin(a)
    rot = {"x": [[1, 0, 0], [0, c, -s], [0, s, c]], "y": [[c, 0, s], [0, 1, 0], [-s, 0, c]],
           "z": [[c, -s, 0], [s, c, 0], [0, 0, 1]]}[r["axis"]]
    o = np.array(r["origin"], float)
    q = (p - o) @ np.array(rot).T
    if r.get("rescale"):
        k = 1 / math.cos(a)
        q = q * {"x": [1, k, k], "y": [k, 1, k], "z": [k, k, 1]}[r["axis"]]
    return q + o


_TEX = {}


def _alpha(name):
    if name not in _TEX:
        _TEX[name] = gen_blocks.m_plant(name, N)["alpha"] > 0.5
    return _TEX[name]


def _screen_cells(model, ppb=32, res=64):
    """The opaque texels of a plant model's quads, projected along the RTS camera's direction onto a screen of ppb
    pixels per block: {pixel: how many opaque quad texels land there}. The texture's rows run down the quad from its
    top edge, its columns along it (a mirrored face covers the same cells)."""
    rx = np.cross(RTS_DIR, [0, 1.0, 0])
    rx /= np.linalg.norm(rx)
    ry = np.cross(rx, RTS_DIR)
    cells = collections.Counter()
    for el in model["elements"]:
        f, t = np.array(el["from"], float), np.array(el["to"], float)
        for face, fd in el["faces"].items():
            a = _alpha(model["textures"][fd["texture"][1:]].split("/")[-1])
            g = (np.arange(res) + 0.5) / res
            uu, vv = np.meshgrid(g, g)
            if face in ("north", "south", "west", "east"):
                along = 0 if f[0] != t[0] else 2
                p = np.zeros(uu.shape + (3,))
                p[..., along] = f[along] + uu * (t[along] - f[along])
                p[..., 2 - along] = f[2 - along]
                p[..., 1] = t[1] - vv * (t[1] - f[1])
            else:
                p = np.stack([f[0] + uu * (t[0] - f[0]), np.full_like(uu, f[1]), f[2] + vv * (t[2] - f[2])], -1)
            keep = a[(vv * a.shape[0]).astype(int), (uu * a.shape[1]).astype(int)]
            q = _rotate(el, p[keep]) / 16.0
            sx = np.floor(q @ rx * ppb).astype(int)
            sy = np.floor(-(q @ ry) * ppb).astype(int)
            for c in set(zip(sx.tolist(), sy.tolist())):     # each quad counts once per pixel
                cells[c] += 1
    return cells


def _footprint(model):
    """sqrt of the screen area (blocks) a plant model covers from the RTS camera: its linear size there."""
    return math.sqrt(len(_screen_cells(model)) / 32.0 ** 2)


class PlantSprites(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sprites = _sprites()

    def test_blades_survive_the_rts_mip(self):
        # at the default RTS zoom the atlas is read at mip 2: most of the drawn area must still be there
        for name, a in self.sprites.items():
            full = (a > 0.5).mean()
            kept = _mip_alpha(a > 0.5, 2).mean() / max(full, 1e-9)
            self.assertGreaterEqual(kept, 0.9, "%s keeps %.2f of its area at mip 2" % (name, kept))

    def test_short_grass_area_and_mass(self):
        for v in range(3):       # the full-size tufts (r34)
            a = self.sprites["grass_%d" % v] > 0.5
            area = a.mean()
            self.assertTrue(0.22 <= area <= 0.30, "grass_%d covers %.3f of the card" % (v, area))
            cols = a.sum(0)
            mid = cols[int(0.2 * N):int(0.8 * N)].sum() / cols.sum()
            self.assertGreaterEqual(mid, 0.6, "grass_%d: %.2f of the mass in the central 60%%" % (v, mid))

    def test_short_grass_base_is_one_wedge(self):
        # the lowest ~20% of a tuft (of its own height, at every size) is one solid wedge: no gaps between its
        # leftmost and rightmost blade
        for v in range(3):
            for size in ps.SIZES:
                name = "grass_%d_%s" % (v, size)
                a = self.sprites[name] > 0.5
                for y in range(N - int(0.18 * _height(a)), N):
                    xs = np.nonzero(a[y])[0]
                    self.assertTrue(len(xs) and xs[-1] - xs[0] + 1 == len(xs), "%s row %d has a gap" % (name, y))

    def test_short_grass_three_sizes(self):
        # r35 (critic r34: "vary scale (3 sizes)"): ~55 / 80 / 100% of the full tuft's height, its reach across the
        # card shrinking too; drawn on the same card, so the blades stay bold: every blade crossing the row at 80% of
        # the tuft's height is >= 4 texels wide there, at every size
        self.assertEqual(tuple(ps.SIZES), (1.0, 0.8, 0.55))
        for v in range(3):
            full = self.sprites["grass_%d_1.0" % v]
            for size in ps.SIZES:
                name = "grass_%d_%s" % (v, size)
                a = self.sprites[name]
                h = _height(a) / _height(full)
                self.assertAlmostEqual(h, size, delta=0.05, msg="%s is %.2f of the full height" % (name, h))
                r = _reach(a) / _reach(full)
                self.assertTrue(size - 0.05 <= r <= size + 0.15, "%s reaches %.2f of the full width" % (name, r))
                row = (a > 0.5)[N - int(0.8 * _height(a))]
                xs = np.nonzero(row)[0]
                runs = [len(b) for b in np.split(xs, np.nonzero(np.diff(xs) > 1)[0] + 1)]
                self.assertGreaterEqual(min(runs), 4, "%s: blades %s texels wide at 80%% height" % (name, runs))

    def test_short_grass_body_lifted(self):
        # r35: in game (r34 noon stills, shader preset) the blocks holding a short-grass tuft drew 0.958 of the
        # lawn's luma; the body is a step lighter (the r34 drawn median was 0.67)
        for v in range(3):
            for size in ps.SIZES:
                H, sh, green, straw = ps.grass_clump(N, np.random.default_rng(7 + v), variant=v, size=size)
                med = float(np.median(sh[green > 0]))
                self.assertGreaterEqual(med, 0.70, "grass_%d_%s body %.3f" % (v, size, med))

    def test_short_grass_core_is_thin_from_above(self):
        # r36 (critic r35b: the big tufts "read as fist-sized dark splats with a hard shadow halo"): every quad of a
        # tuft carries the whole drawing from the one root, so from the RTS camera (and the sun) its bases stack into
        # a solid core. No upright pair under the leaning quads (edge-on from the RTS camera, but six bases deep at
        # the root), and at most ~30% of a tuft's footprint under >= 3 opaque quads (r35 with r36's sizes: 38%)
        stacked = []
        for k in gen_blocks.GRASS_TUFTS:
            model = gen_blocks.MODEL_OVERRIDES["grass" + k]
            for el in model["elements"]:
                self.assertNotEqual(el["rotation"]["axis"], "y", "grass%s keeps an upright crossed quad" % k)
            cells = _screen_cells(model)
            stacked.append(sum(1 for v in cells.values() if v >= 3) / len(cells))
        self.assertLessEqual(float(np.mean(stacked)), 0.30, "stacked %s" % np.round(stacked, 2))
        self.assertLessEqual(max(stacked), 0.40, "stacked %s" % np.round(stacked, 2))

    def test_short_grass_base_is_light_and_thin(self):
        # r36: the lowest 15% of a tuft no darker than the drawn body less a step (r35: median 0.63-0.66 against the
        # lawn's 0.56) and a narrower crown: fewer opaque texels there than r35's (0.25 / 0.23 / 0.26 of the band
        # at full size)
        r35 = {0: 0.253, 1: 0.233, 2: 0.259}
        for v in range(3):
            for size in ps.SIZES:
                H, sh, green, straw = ps.grass_clump(N, np.random.default_rng(7 + v), variant=v, size=size)
                a = np.maximum(green, straw) > 0
                low = slice(N - int(0.15 * _height(a)), N)
                med = float(np.median(sh[low][green[low] > 0]))
                self.assertGreaterEqual(med, 0.68, "grass_%d_%s base %.3f" % (v, size, med))
                if size == 1.0:
                    fill = float(a[low].mean())
                    self.assertLess(fill, r35[v] - 0.02, "grass_%d base fills %.3f of its band" % (v, fill))

    def test_fern_cards_grow_from_the_bottom_centre(self):
        # the rosette's cards are hinged at the root: a card's sprite must start at the middle of its bottom edge
        for k in range(2):
            a = self.sprites["fern_card_%d" % k] > 0.5
            xs = np.nonzero(a[N - 1])[0]
            self.assertTrue(len(xs), "fern_card_%d: nothing on the bottom row" % k)
            self.assertLess(abs(xs.mean() - N / 2), 4, "fern_card_%d: root at x %.1f" % (k, xs.mean()))
            self.assertFalse(a[:2].any(), "fern_card_%d touches the top edge" % k)

    def test_no_dark_texels_under_the_cutout(self):
        # MC-114265: mipmaps pull the colour of transparent texels into the edges - fill it from the nearest blade
        H, sh, green, straw = ps.grass_clump(N, np.random.default_rng(3), variant=0)
        rgb = np.stack([sh * 1.08, sh, sh * 0.8], -1)
        out = ps.bleed(rgb, green)
        self.assertTrue(np.allclose(out[green > 0], rgb[green > 0]))
        self.assertGreater(out[green == 0].min(), 0.3)


class PlantWiring(unittest.TestCase):
    def _textures(self, model):
        return {v.split("/")[-1] for k, v in gen_blocks.MODEL_OVERRIDES[model]["textures"].items()}

    def test_every_blockstate_names_a_written_model_and_textures(self):
        states = dict(gen_blocks.TUFT_BLOCKSTATES, **gen_blocks.TALL_BLOCKSTATES)
        for block, variants in states.items():
            lists = variants.values() if isinstance(variants, dict) else [variants]
            for lst in lists:
                for v in lst:
                    model = v["model"].split("/")[-1]
                    self.assertIn(model, gen_blocks.MODEL_OVERRIDES, block)
                    for tex in self._textures(model):
                        self.assertIn(tex, gen_blocks.RECIPES, "%s -> %s" % (model, tex))

    def test_grass_shows_three_shapes_in_three_sizes(self):
        # r35: every (shape, size) pair, each size a third of the models (the game picks a blockstate entry evenly)
        shown = collections.Counter(gen_blocks.MODEL_OVERRIDES[v["model"].split("/")[-1]]["textures"]["cross"]
                                    for v in gen_blocks.TUFT_BLOCKSTATES["grass"])
        want = {"minecraft:block/grass" + shape + size for shape in ("", "_b", "_c") for size in ("", "_m", "_sm")}
        self.assertEqual(set(shown), want)
        self.assertEqual(len(set(shown.values())), 1, "uneven: %s" % shown)

    def test_grass_sizes_differ_twofold_from_the_rts_camera(self):
        # r36 (critic r35b: "three sizes are not distinguishable at 30 px/block - only tuft vs slightly bigger tuft"):
        # seen from the RTS camera the big tufts' footprint is ~2x the small ones' (linear), the middle size between
        by_size = collections.defaultdict(list)
        for k, (quads, shape, size) in gen_blocks.GRASS_TUFTS.items():
            by_size[size].append(_footprint(gen_blocks.MODEL_OVERRIDES["grass" + k]))
        big, mid, small = (float(np.mean(by_size[s])) for s in ("", "_m", "_sm"))
        self.assertGreaterEqual(big / small, 1.9, "big %.2f / small %.2f = %.2f" % (big, small, big / small))
        self.assertTrue(small * 1.2 < mid < big / 1.2, "sizes %.2f / %.2f / %.2f" % (small, mid, big))

    def test_fern_is_a_low_wide_rosette(self):
        # r35: frond cards tilted up from the crown toward all four sides, no upright quad; wider than twice its height
        models = {v["model"].split("/")[-1] for v in gen_blocks.TUFT_BLOCKSTATES["fern"]}
        self.assertGreaterEqual(len(models), 2)
        for m in models:
            model = gen_blocks.MODEL_OVERRIDES[m]
            span, height = _extent(model)
            self.assertGreaterEqual(span, 2 * height, "%s: %.1f wide, %.1f tall" % (m, span, height))
            sides = set()
            for el in model["elements"]:
                self.assertEqual(el["from"][1], el["to"][1], "%s: an element that is not a flat card" % m)
                self.assertIn(abs(el["rotation"]["angle"]), (22.5, 45), m)
                p = _corners(el)
                self.assertGreaterEqual(p[:, 1].min(), el["from"][1] - 1e-6, "%s: a card dips below its hinge" % m)
                tip = p[p[:, 1].argmax()]
                sides.add(("+x" if tip[0] > 8 else "-x") if abs(tip[0] - 8) > abs(tip[2] - 8) else
                          ("+z" if tip[2] > 8 else "-z"))
            self.assertEqual(sides, {"+x", "-x", "+z", "-z"}, m)

    def test_fern_cards_show_the_root_at_the_hinge(self):
        # an up face maps u to +x and v to +z, a down face u to +x and v to -z (FaceInfo UP / DOWN); a card sprite's
        # root is at the middle of its bottom edge (v = 16), a transposed one's (texture name ending _t) of its
        # right edge (u = 16): that edge must lie on the hinge, or the frond would grow from the tip in
        for m in {v["model"].split("/")[-1] for v in gen_blocks.TUFT_BLOCKSTATES["fern"]}:
            model = gen_blocks.MODEL_OVERRIDES[m]
            for el in model["elements"]:
                o = el["rotation"]["origin"]
                for face, f in el["faces"].items():
                    u0, v0, u1, v1 = f["uv"]
                    transposed = model["textures"][f["texture"][1:]].endswith("_t")
                    if transposed:      # the hinge is on an x edge: from[0] or to[0] == 8
                        at = u0 if el["from"][0] == o[0] else u1
                    else:
                        lo = el["from"][2] == o[2]          # hinge on the element's min-z edge
                        if face == "up":
                            at = v0 if lo else v1
                        else:
                            at = v1 if lo else v0
                    self.assertEqual(at, 16, "%s: a %s face's root is not on the hinge" % (m, face))

    def test_tall_grass_stays_upright_and_narrow(self):
        for half in ("half=lower", "half=upper"):
            for v in gen_blocks.TALL_BLOCKSTATES["tall_grass"][half]:
                span, height = _extent(gen_blocks.MODEL_OVERRIDES[v["model"].split("/")[-1]])
                self.assertGreaterEqual(height, span, v["model"])

    def test_tall_grass_halves_line_up(self):
        # DoublePlantBlock seeds both halves with the lower block's position, so the same index is picked in both
        # lists: index i of the bottom list must be the same shape as index i of the top list
        st = gen_blocks.TALL_BLOCKSTATES["tall_grass"]
        low, up = st["half=lower"], st["half=upper"]
        self.assertEqual(len(low), 2)
        self.assertEqual(len(low), len(up))
        for lo, hi in zip(low, up):
            b = gen_blocks.MODEL_OVERRIDES[lo["model"].split("/")[-1]]["textures"]["cross"]
            t = gen_blocks.MODEL_OVERRIDES[hi["model"].split("/")[-1]]["textures"]["cross"]
            self.assertEqual(b.replace("_bottom", ""), t.replace("_top", ""))


if __name__ == "__main__":
    unittest.main()


class NoScipy(unittest.TestCase):
    def test_the_sprites_build_with_numpy_and_pillow_only(self):
        # CI installs numpy + pillow only (build.yml's ruff job, eyes.yml's block-pack step): r34's scipy import
        # failed both eyes runs of round r34 (40788983646 / 40789031491) at "Our realistic block pack"
        import subprocess
        code = ("import sys; sys.modules['scipy'] = None; sys.path.insert(0, 'tools'); import plant_sprites as ps; "
                "import numpy as np; a = np.zeros((8, 8)); a[2, 2] = 1; v = np.arange(64.0).reshape(8, 8); "
                "assert ps.bleed(v, a)[0, 0] == v[2, 2]")
        root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        r = subprocess.run([sys.executable, "-c", code], cwd=root, capture_output=True, text=True)
        self.assertEqual(r.returncode, 0, r.stderr[-800:])
