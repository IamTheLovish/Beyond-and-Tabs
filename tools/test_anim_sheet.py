#!/usr/bin/env python3
"""Tests for the animation review sheet (tools/anim_sheet.py, tools/anim_lab.py --sheet, tools/game_preview.py's fast
render).  lovish, Oct 22 19:58: "write the tests first and then implement it so you dont cheat" - this file was written
and run RED before any of the code it tests existed (the commit history shows it); a failing test here is fixed in the
code, never by loosening the test.

    python3 tools/test_anim_sheet.py            # all
    python3 tools/test_anim_sheet.py -v Layout  # one class

What a sheet is for (lovish Oct 22 20:23: "find a way to test animations in the cloud ... see them with your own
eyes"): a Claude session reads ONE png back into the model.  An image viewer downsizes anything past ~2000 px wide, and
the old 9620 x 798 strip came back as 60 px figures nobody could judge - so the maths below pins: sheet <= 2000 x 2000,
figures >= 240 px, a frame at the blow, labels that say which progress / tick each frame is.
"""
import os
import sys
import time
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)

import anim_sheet as S  # noqa: E402  (fails until the module exists: red first)


class KeyProgress(unittest.TestCase):
    def test_one_shot_includes_both_ends_evenly(self):
        ps, hit = S.key_frames(10)
        self.assertEqual(len(ps), 10)
        self.assertEqual(ps[0], 0.0)
        self.assertEqual(ps[-1], 1.0)
        self.assertIsNone(hit)
        steps = [b - a for a, b in zip(ps, ps[1:])]
        for s in steps:
            self.assertAlmostEqual(s, 1 / 9, places=9)

    def test_loop_leaves_out_the_seam(self):
        # a cycle's last key IS its first: showing both wastes a cell on the same pose
        ps, _ = S.key_frames(8, loop=True)
        self.assertEqual(len(ps), 8)
        self.assertEqual(ps[0], 0.0)
        self.assertAlmostEqual(ps[-1], 7 / 8)
        self.assertLess(ps[-1], 1.0)

    def test_a_frame_sits_exactly_on_the_blow(self):
        ps, hit = S.key_frames(10, hit_p=0.27)
        self.assertEqual(len(ps), 10)
        self.assertEqual(ps[hit], 0.27)
        # it replaced the NEAREST uniform frame (p = 2/9 = 0.222; 3/9 = 0.333 is further), kept the count
        self.assertEqual(hit, 2)
        self.assertEqual(ps, sorted(ps))
        self.assertEqual(len(set(ps)), 10)

    def test_the_blow_never_displaces_an_end_frame(self):
        for hp in (0.0, 0.01, 0.99, 1.0):
            ps, hit = S.key_frames(8, hit_p=hp)
            self.assertEqual(ps[0], 0.0)
            self.assertEqual(ps[-1], 1.0)
            self.assertEqual(ps, sorted(ps))
            self.assertEqual(len(set(ps)), 8)
            self.assertIsNotNone(hit)
            self.assertLess(abs(ps[hit] - hp), 0.5 / 7 + 1e-9)

    def test_hit_p_means_nothing_for_a_loop(self):
        ps, hit = S.key_frames(8, loop=True, hit_p=0.5)
        self.assertIsNone(hit)
        self.assertEqual(ps, [i / 8 for i in range(8)])

    def test_needs_two_frames(self):
        with self.assertRaises(ValueError):
            S.key_frames(1)


class Labels(unittest.TestCase):
    def test_progress_label(self):
        self.assertEqual(S.frame_label(2, 1 / 3, 18), "3  p=0.33  t=6.0")

    def test_hit_marker(self):
        self.assertEqual(S.frame_label(2, 0.27, 18, True), "3  p=0.27  t=4.9  HIT")

    def test_time_label_for_captured_frames(self):
        # in-game frames have a clock, not a clip progress: seconds since the window started and the game tick
        self.assertEqual(S.time_label(0, 0.0), "1  +0.00s  (tick 0)")
        self.assertEqual(S.time_label(4, 0.55), "5  +0.55s  (tick 11)")


class ComposeLabels(unittest.TestCase):
    """Rows photographed in the game are separate takes: each row's captions carry its own times."""

    def _sheet(self, labels):
        from PIL import Image
        lay = S.layout([3, 3])
        cells = [Image.new("RGB", (lay.cell, lay.heights[0]), (90, 90, 90)) for _ in range(3)]
        return S.compose("t", "l", [("side", cells), ("front", cells)], lay, labels), lay

    def test_one_list_of_captions_serves_every_row(self):
        sheet, lay = self._sheet(["a", "b", "c"])
        self.assertEqual(sheet.size, (lay.width, lay.height))

    def test_per_row_captions_are_drawn_per_row(self):
        import numpy as np
        same, lay = self._sheet([["wwwwwwww", "wwwwwwww", "wwwwwwww"], ["wwwwwwww", "wwwwwwww", "wwwwwwww"]])
        diff, _ = self._sheet([["wwwwwwww", "wwwwwwww", "wwwwwwww"], ["", "", ""]])
        y = lay.positions[1][0][1] + lay.heights[1]
        a = np.asarray(same)[y:y + lay.label_h]
        b = np.asarray(diff)[y:y + lay.label_h]
        self.assertFalse((a == b).all(), "the second row's captions were not drawn from its own list")
        y0 = lay.positions[0][0][1] + lay.heights[0]
        self.assertTrue((np.asarray(same)[y0:y0 + lay.label_h] == np.asarray(diff)[y0:y0 + lay.label_h]).all())


class SelectNearest(unittest.TestCase):
    def test_picks_the_closest_capture_to_each_target(self):
        times = [0.0, 0.07, 0.11, 0.30, 0.52, 0.55, 0.9]
        self.assertEqual(S.select_nearest(times, [0.0, 0.3, 0.9]), [0, 3, 6])
        self.assertEqual(S.select_nearest(times, [0.1]), [2])

    def test_distinct_frames_even_when_captures_are_sparse(self):
        # two targets that share a nearest capture must not show the same picture twice
        times = [0.0, 1.0]
        got = S.select_nearest(times, [0.4, 0.6])
        self.assertEqual(len(set(got)), 2)
        self.assertEqual(sorted(got), [0, 1])

    def test_fewer_captures_than_targets_repeats_nothing(self):
        got = S.select_nearest([0.0, 0.5], [0.0, 0.2, 0.4, 0.5])
        self.assertEqual(sorted(set(got)), sorted(got))
        self.assertEqual(len(got), 2)

    def test_empty(self):
        self.assertEqual(S.select_nearest([], [0.0, 1.0]), [])

    def test_unsorted_input_is_fine(self):
        times = [0.5, 0.0, 1.0]
        self.assertEqual(S.select_nearest(times, [0.0, 1.0]), [1, 2])


class Layout(unittest.TestCase):
    def check_geometry(self, lay, lines):
        self.assertLessEqual(lay.width, 2000)
        self.assertLessEqual(lay.height, 2000)
        boxes = []
        for li, n in enumerate(lines):
            self.assertEqual(len(lay.positions[li]), n)
            for (x, y) in lay.positions[li]:
                self.assertGreaterEqual(x, 0)
                self.assertGreaterEqual(y, 0)
                self.assertLessEqual(x + lay.cell, lay.width)
                self.assertLessEqual(y + lay.heights[li] + lay.label_h, lay.height)
                boxes.append((x, y, x + lay.cell, y + lay.heights[li] + lay.label_h))
        for i, a in enumerate(boxes):
            for b in boxes[i + 1:]:
                apart = a[2] <= b[0] or b[2] <= a[0] or a[3] <= b[1] or b[3] <= a[1]
                self.assertTrue(apart, "cells %s and %s overlap" % (a, b))

    def test_eight_frames_three_views_stay_one_row_per_view_at_240px_or_more(self):
        lines = [8, 8, 8]
        lay = S.layout(lines)
        self.check_geometry(lay, lines)
        self.assertGreaterEqual(lay.cell, 240)
        self.assertEqual(lay.wrap, 1)               # one view per row: time runs left to right
        ys = {y for (_, y) in (p for line in lay.positions for p in line)}
        self.assertEqual(len(ys), 3)

    def test_ten_frames_wrap_in_two_balanced_lines_instead_of_shrinking(self):
        lines = [10, 10, 10]
        lay = S.layout(lines)
        self.check_geometry(lay, lines)
        self.assertGreaterEqual(lay.cell, 240)
        self.assertEqual(lay.wrap, 2)
        self.assertEqual(lay.cols, 5)

    def test_twelve_frames_two_views(self):
        lines = [12, 12]
        lay = S.layout(lines)
        self.check_geometry(lay, lines)
        self.assertGreaterEqual(lay.cell, 240)

    def test_never_exceeds_the_limit_however_much_is_asked(self):
        for lines in ([30, 30, 30], [40], [3, 3, 3, 3, 3, 3, 3, 3, 3, 3], [24] * 6):
            lay = S.layout(lines)
            self.check_geometry(lay, lines)
            self.assertGreaterEqual(lay.cell, 16)

    def test_wide_figures_get_short_rows_and_so_a_bigger_cell(self):
        # a side view of a unit with a long weapon is 2x wider than tall: a square cell wastes half its height, and
        # the height saved goes into a wider cell - bigger figures in every row
        lines, asp = [10, 10, 10], [0.5, 0.6, 1.4]
        lay = S.layout(lines, aspects=asp)
        self.check_geometry(lay, lines)
        self.assertEqual(lay.heights, [round(lay.cell * a) for a in asp])
        self.assertGreater(lay.cell, S.layout(lines).cell)
        # 2 rows a line, row heights 0.5 + 0.6 + 1.4 = 5.0 cells; room = 2000 - 44 (header) - 6 rows x 22 (caption, pad)
        self.assertEqual(lay.cell, (2000 - 44 - 6 * 22) // 5)

    def test_aspects_are_one_per_line(self):
        with self.assertRaises(ValueError):
            S.layout([8, 8], aspects=[1.0])

    def test_extreme_aspects_still_fit(self):
        for asp in ([0.2, 0.2, 0.2], [3.0, 3.0, 3.0], [0.3, 2.5, 1.0]):
            lines = [8, 8, 8]
            lay = S.layout(lines, aspects=asp)
            self.check_geometry(lay, lines)

    def test_the_default_aspect_is_square(self):
        lay = S.layout([8, 8])
        self.assertEqual(lay.heights, [lay.cell, lay.cell])

    def test_a_small_ask_is_not_blown_up_past_the_cap(self):
        lay = S.layout([3])
        self.assertLessEqual(lay.cell, 420)

    def test_the_cell_is_the_biggest_that_fits(self):
        # one more pixel per cell must overflow one of the two limits (else the solver left size on the table)
        lines = [10, 10, 10]
        lay = S.layout(lines)
        c = lay.cell + 1
        w = lay.pad + lay.cols * (c + lay.pad)
        h = lay.header + lay.pad + sum(-(-n // lay.cols) for n in lines) * (c + lay.label_h + lay.pad)   # square cells
        self.assertTrue(w > 2000 or h > 2000, (w, h, lay.cell))

    def test_reference_rows_add_height_and_still_fit(self):
        lines = [8, 8, 8]
        plain = S.layout(lines)
        withref = S.layout(lines, ref_lines=1)
        self.check_geometry(withref, lines)
        self.assertEqual(len(withref.ref_boxes), 1)
        rx, ry, rw, rh = withref.ref_boxes[0]
        self.assertLessEqual(rx + rw, withref.width)
        self.assertLessEqual(ry + rh, withref.height)
        self.assertLessEqual(withref.cell, plain.cell)
        # the reference band sits below every frame cell
        lowest = max(y + withref.cell + withref.label_h for line in withref.positions for (_, y) in line)
        self.assertGreaterEqual(ry, lowest)

    def test_empty_layout_is_an_error(self):
        with self.assertRaises(ValueError):
            S.layout([])


class Tracking(unittest.TestCase):
    """The motion trail follows one real vertex of the model through every pose (a weapon tip, a heel), not a guess."""

    @staticmethod
    def arm():
        import dsl_to_game as d2g
        root = d2g.Part("root", (0, 0, 0), (0, 0, 0))
        arm = root.child("arm", (0.0, 4.0, 0.0))                 # pivot 4 px up
        arm.boxes.append((-1.0, 0.0, -1.0, 2.0, 10.0, 2.0, 0.0, (0, 0)))   # hangs 10 px down from the pivot
        return root

    def test_the_far_vertex_of_a_swinging_arm_traces_a_circle(self):
        import math
        import dsl_to_game as d2g
        import game_preview as gp
        root = self.arm()
        tags = []
        rest = gp.faces_of(root, {}, True, False, (), None, tags=tags)
        self.assertEqual(len(tags), len(rest))
        # each tag: the owning part's name path from the root, and the world pivot of every part on that path
        self.assertTrue(all(t.path[-1] in ("root", "arm") and len(t.pivots) == len(t.path) for t in tags))
        self.assertEqual({t.path for t in tags}, {("root", "arm")})
        ref = S.pick_tracked(rest, tags, "arm", "far")
        radii, pts = [], []
        for deg in range(0, 91, 15):
            fs = gp.faces_of(root, {"arm": d2g.mc_rot((deg, 0, 0))}, True, False, (), None)
            p = S.tracked_point(fs, ref)
            pts.append(p)
            radii.append(math.dist((p[0], p[1], p[2]), (0.0, 4.0, 0.0)))
        # the pivot is 4 px above the offset origin in model space (y down) -> the tip is on a circle round it
        for r in radii:
            self.assertAlmostEqual(r, radii[0], places=4)
        self.assertGreater(radii[0], 10.0)                      # the long end, not the shoulder end
        self.assertGreater(math.dist(pts[0], pts[-1]), 10.0)    # and it really moved

    def test_low_picks_the_lowest_vertex_at_rest(self):
        import game_preview as gp
        root = self.arm()
        tags = []
        rest = gp.faces_of(root, {}, True, False, (), None, tags=tags)
        fi, vi = S.pick_tracked(rest, tags, "arm", "low")
        lowest = max(v[1] for f in rest for v in f[0])          # model space is y DOWN: the lowest point has the largest y
        self.assertAlmostEqual(rest[fi][0][vi][1], lowest)

    def test_an_unknown_bone_is_a_loud_error(self):
        import game_preview as gp
        root = self.arm()
        tags = []
        rest = gp.faces_of(root, {}, True, False, (), None, tags=tags)
        with self.assertRaises(KeyError):
            S.pick_tracked(rest, tags, "wing", "far")

    def test_the_trail_lands_where_the_renderer_draws_the_tip(self):
        """The projection used for the trail must be the renderer's own: the tip's predicted pixel is the lowest
        drawn pixel of the arm hanging straight down."""
        import numpy as np
        import game_preview as gp
        from PIL import Image
        root = self.arm()
        tags = []
        fs = gp.faces_of(root, {}, True, False, (), None, tags=tags)
        ref = S.pick_tracked(fs, tags, "arm", "low")
        size = 200
        tex = Image.new("RGBA", (64, 64), (200, 40, 40, 255))
        imgs = {"base": tex, "team": Image.new("RGBA", (64, 64), (0, 0, 0, 0)),
                "glow": Image.new("RGBA", (64, 64), (0, 0, 0, 0))}
        view = gp.VIEWS["side"]
        im, box = gp.render(fs, imgs, 1, view, size, (1, 1, 1))
        a = np.asarray(im, dtype=np.int32)
        bg = np.array([round(c * 255) for c in gp.GROUND])
        body = np.abs(a - bg).sum(axis=2) > 30
        ys = np.where(body.any(axis=1))[0]
        px, py = S.to_pixel(S.tracked_point(fs, ref), view, box, size)
        self.assertLess(abs(py - ys[-1]), 2.0, "tip y %.1f vs lowest drawn row %d" % (py, ys[-1]))
        cols = np.where(body[ys[-1]])[0]
        self.assertLessEqual(cols.min() - 2.0, px)
        self.assertLessEqual(px, cols.max() + 2.0)


# Wall-clock bars are for the machine a session iterates on (this container: they must hold here). A shared CI runner's
# speed is not ours to promise, so build.yml sets ANIM_SKIP_TIMING=1 and runs everything else - the equality tests that
# make the fast renderer trustworthy still run there.
SKIP_TIMING = os.environ.get("ANIM_SKIP_TIMING") == "1"


class FastRender(unittest.TestCase):
    """The speed-up must not change a single drawn pixel: the first version of this file compared game_preview.render
    with a frozen copy of the pre-Oct-22 renderer (REFERENCE below, verbatim from commit 3abe41e)."""

    @classmethod
    def setUpClass(cls):
        import render_concept
        import dsl_to_game as d2g
        path = os.path.join(ROOT, "design/models/sunforged_halberdier_v2.json")
        m = render_concept.load_model(path)
        m["_path"] = path
        _j, cls.imgs, info = d2g.build(m, "Preview", [], "preview", d2g.DENSITY)
        cls.root, cls.m = info["root"], m

    def faces(self, pose):
        import dsl_to_game as d2g
        import game_preview as gp
        base = {nm: d2g.mc_rot(r) for nm, r in self.m["pose"].items()}
        for b, r in pose.items():
            cur = base.get(b, (0.0, 0.0, 0.0))
            add = d2g.mc_rot(r)
            base[b] = tuple(cur[i] + add[i] for i in range(3))
        return gp.faces_of(self.root, base, True, False, (), None)

    def test_identical_to_the_reference_renderer_in_every_view(self):
        import numpy as np
        import dsl_to_game as d2g
        import game_preview as gp
        fs = self.faces({"right_arm": [40, 10, 0], "left_thigh": [-25, 0, 0]})
        for vname in ("side", "quarter", "front", "rts"):
            ref_img, ref_box = reference_render(gp, fs, self.imgs, d2g.DENSITY, gp.VIEWS[vname], 180, (0.23, 0.44, 0.82))
            new_img, new_box = gp.render(fs, self.imgs, d2g.DENSITY, gp.VIEWS[vname], 180, (0.23, 0.44, 0.82))
            for a, b in zip(ref_box, new_box):          # the vectorised matmul may differ from the loop in the last bit
                self.assertAlmostEqual(a, b, places=9, msg=vname)
            diff = np.abs(np.asarray(ref_img, dtype=np.int32) - np.asarray(new_img, dtype=np.int32))
            self.assertEqual(int(diff.max()), 0, "%s: %d pixels differ" % (vname, int((diff.sum(axis=2) > 0).sum())))

    def test_identical_with_a_fixed_box(self):
        import numpy as np
        import dsl_to_game as d2g
        import game_preview as gp
        fs = self.faces({"right_arm": [60, 0, 0]})
        box = (-30.0, 30.0, -4.0, 40.0)
        ref_img, _ = reference_render(gp, fs, self.imgs, d2g.DENSITY, gp.VIEWS["side"], 150, (1, 0, 0), box)
        new_img, _ = gp.render(fs, self.imgs, d2g.DENSITY, gp.VIEWS["side"], 150, (1, 0, 0), box)
        self.assertEqual(int(np.abs(np.asarray(ref_img, dtype=np.int32) - np.asarray(new_img, dtype=np.int32)).max()), 0)

    def test_bbox_without_drawing_equals_the_drawn_bbox(self):
        import dsl_to_game as d2g
        import game_preview as gp
        fs = self.faces({"left_arm": [-50, 0, 20]})
        for vname in ("side", "quarter", "front"):
            _, drawn = gp.render(fs, self.imgs, d2g.DENSITY, gp.VIEWS[vname], 64, (1, 1, 1))
            quick = gp.bbox(fs, gp.VIEWS[vname])
            for a, b in zip(drawn, quick):
                self.assertAlmostEqual(a, b, places=6)

    @unittest.skipIf(SKIP_TIMING, "wall-clock bar: not asserted on shared CI runners")
    def test_a_render_is_fast(self):
        import dsl_to_game as d2g
        import game_preview as gp
        fs = self.faces({})
        gp.render(fs, self.imgs, d2g.DENSITY, gp.VIEWS["side"], 260, (1, 1, 1))     # warm
        t = time.time()
        for _ in range(5):
            gp.render(fs, self.imgs, d2g.DENSITY, gp.VIEWS["side"], 260, (1, 1, 1))
        per = (time.time() - t) / 5
        # was 0.25 s; the bar is a 3x cut or better (a sheet is ~35 renders)
        self.assertLess(per, 0.085, "%.3f s a render" % per)


class SheetEndToEnd(unittest.TestCase):
    """The product: halberdier thrust -> one readable png, in under 30 s from a cold start."""

    @unittest.skipIf(SKIP_TIMING, "wall-clock bar: not asserted on shared CI runners")
    def test_halberdier_thrust_sheet(self):
        import shutil
        import subprocess
        import tempfile
        from PIL import Image
        out = tempfile.mkdtemp(prefix="animsheet-")
        cache = tempfile.mkdtemp(prefix="animcache-")
        try:
            env = dict(os.environ, ANIM_LAB_CACHE=cache)         # a COLD cache: the first run of a session
            t = time.time()
            r = subprocess.run([sys.executable, os.path.join(HERE, "anim_lab.py"),
                                os.path.join(ROOT, "design/anims/halberdier.json"), "--sheet", out, "--clip", "thrust"],
                               capture_output=True, text=True, env=env)
            took = time.time() - t
            self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
            png = os.path.join(out, "thrust_sheet.png")
            self.assertTrue(os.path.isfile(png), r.stdout)
            im = Image.open(png)
            self.assertLessEqual(im.size[0], 2000)
            self.assertLessEqual(im.size[1], 2000)
            self.assertGreaterEqual(im.size[0], 1200, "a sheet that small is not using the room it has: %s" % (im.size,))
            self.assertLess(took, 30.0, "cold sheet took %.1f s" % took)
            self.assertIn("wrote", r.stdout)
            # the second run (warm cache) is the one a session repeats: it must be much quicker
            t = time.time()
            r2 = subprocess.run([sys.executable, os.path.join(HERE, "anim_lab.py"),
                                 os.path.join(ROOT, "design/anims/halberdier.json"), "--sheet", out, "--clip", "thrust"],
                                capture_output=True, text=True, env=env)
            warm = time.time() - t
            self.assertEqual(r2.returncode, 0, r2.stdout + r2.stderr)
            self.assertLess(warm, 15.0, "warm sheet took %.1f s" % warm)
            print("\n  sheet: cold %.1f s, warm %.1f s, %dx%d" % (took, warm, im.size[0], im.size[1]))
        finally:
            shutil.rmtree(out, ignore_errors=True)
            shutil.rmtree(cache, ignore_errors=True)

    def test_every_clip_without_a_name(self):
        """--sheet with no --clip does every clip of the file, one png each."""
        import shutil
        import subprocess
        import tempfile
        out = tempfile.mkdtemp(prefix="animsheet-")
        try:
            r = subprocess.run([sys.executable, os.path.join(HERE, "anim_lab.py"),
                                os.path.join(ROOT, "design/anims/halberdier.json"), "--sheet", out, "--frames", "6",
                                "--views", "side"], capture_output=True, text=True)
            self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
            for clip in ("idle", "walk", "thrust"):
                self.assertTrue(os.path.isfile(os.path.join(out, clip + "_sheet.png")), r.stdout)
        finally:
            shutil.rmtree(out, ignore_errors=True)

    def test_unknown_clip_is_an_error(self):
        import subprocess
        import tempfile
        r = subprocess.run([sys.executable, os.path.join(HERE, "anim_lab.py"),
                            os.path.join(ROOT, "design/anims/halberdier.json"), "--sheet", tempfile.mkdtemp(),
                            "--clip", "nope"], capture_output=True, text=True)
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("nope", r.stdout + r.stderr)


def reference_render(gp, faces, imgs, density, view, size, owner, box=None):
    """game_preview.render exactly as it was at commit 3abe41e (before the Oct 22 speed-up): the golden answer."""
    import math
    import numpy as np
    from PIL import Image
    from preview_model import view_matrix
    base = np.asarray(imgs["base"], dtype=np.float32) / 255.0
    team = np.asarray(imgs["team"], dtype=np.float32) / 255.0
    glow = np.asarray(imgs["glow"], dtype=np.float32) / 255.0
    th, tw = base.shape[:2]
    R = np.array(view_matrix(view[0], view[1]))
    flip = np.diag([1.0, -1.0, 1.0])
    proj = []
    for face in faces:
        verts, uvs = face[0], face[1]
        fa = face[2] if len(face) > 2 else 1.0
        P = [R @ (flip @ v + np.array([0, 24.0, 0])) for v in verts]
        n = np.cross(P[1] - P[0], P[2] - P[0])
        nl = np.linalg.norm(n)
        if nl < 1e-9:
            continue
        n = -n / nl
        if n[2] > 0:
            continue
        nw = np.linalg.inv(R) @ n
        if gp.SUN is not None:
            light = min(1.0, 0.75 * max(0.0, nw @ gp.SUN) + 0.35 + 0.10 * max(0.0, nw[1]))
        else:
            light = min(1.0, 0.6 * (max(0.0, nw @ gp.L0) + max(0.0, nw @ gp.L1)) + 0.4)
        proj.append((P, uvs, light, fa))
    proj.sort(key=lambda f: (f[3] < 1.0, -max(p[2] for p in f[0]) if f[3] < 1.0 else 0.0))
    if box is None:
        xs = [p[0] for P, _, _, _ in proj for p in P]
        ys = [p[1] for P, _, _, _ in proj for p in P]
        box = (min(xs), max(xs), min(ys), max(ys))
    lo_x, hi_x, lo_y, hi_y = box
    span = max(hi_x - lo_x, hi_y - lo_y) * 1.08
    sc = size / span
    ox = (size - (hi_x - lo_x) * sc) / 2 - lo_x * sc
    oy = size - ((size - (hi_y - lo_y) * sc) / 2) + lo_y * sc
    img = np.zeros((size, size, 3), dtype=np.float32)
    img[:] = (0.50, 0.62, 0.36)
    zbuf = np.full((size, size), np.inf, dtype=np.float32)
    owner = np.array(owner, dtype=np.float32)
    for P, uvs, light, fa in proj:
        S_ = [np.array([ox + p[0] * sc, oy - p[1] * sc, p[2]]) for p in P]
        e1, e2 = S_[0][:2] - S_[1][:2], S_[2][:2] - S_[1][:2]
        det = e1[0] * e2[1] - e1[1] * e2[0]
        if abs(det) < 1e-9:
            continue
        xs = [s[0] for s in S_]
        ys = [s[1] for s in S_]
        x0, x1 = max(0, int(math.floor(min(xs)))), min(size - 1, int(math.ceil(max(xs))))
        y0, y1 = max(0, int(math.floor(min(ys)))), min(size - 1, int(math.ceil(max(ys))))
        if x1 < x0 or y1 < y0:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        qx, qy = gx - S_[1][0], gy - S_[1][1]
        a = (qx * e2[1] - qy * e2[0]) / det
        b = (e1[0] * qy - e1[1] * qx) / det
        inside = (a >= 0) & (a <= 1) & (b >= 0) & (b <= 1)
        if not inside.any():
            continue
        z = S_[1][2] + a * (S_[0][2] - S_[1][2]) + b * (S_[2][2] - S_[1][2])
        u = uvs[1][0] + a * (uvs[0][0] - uvs[1][0]) + b * (uvs[2][0] - uvs[1][0])
        v = uvs[1][1] + a * (uvs[0][1] - uvs[1][1]) + b * (uvs[2][1] - uvs[1][1])
        ti = np.clip((u * density).astype(int), 0, tw - 1)
        tj = np.clip((v * density).astype(int), 0, th - 1)
        texel = base[tj, ti]
        sub = zbuf[y0:y1 + 1, x0:x1 + 1]
        ok = inside & (texel[..., 3] > 0.1) & (z < sub)
        if not ok.any():
            continue
        col = texel[..., :3].copy()
        tm = team[tj, ti]
        has_team = tm[..., 3] > 0.1
        col[has_team] = tm[has_team][:, :3] * owner
        col = col * light
        gl = glow[tj, ti]
        col = np.clip(col + gl[..., :3] * gl[..., 3:4] * 0.8, 0, 1)
        region = img[y0:y1 + 1, x0:x1 + 1]
        if fa < 1.0:
            region[ok] = region[ok] * (1.0 - fa) + col[ok] * fa
        else:
            region[ok] = col[ok]
        sub[ok] = z[ok]
    return Image.fromarray((img * 255).astype(np.uint8)), box


if __name__ == "__main__":
    unittest.main()
