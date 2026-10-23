#!/usr/bin/env python3
"""Tests for tools/anim_eyes.py (written first, seen red, then the tool): the one command that turns "show me this unit's
animation" into a sheet a Claude session can read back.

    python3 tools/test_anim_eyes.py          # all
    python3 tools/test_anim_eyes.py Spec     # one class

The Java half (EyesAnimScene) is compiled and played on CI only; what is tested here is everything around it that can be
wrong without a game: the property string both sides parse the same way, the log lines the scene writes, which captured
frames end up on the sheet, the sheet itself, and the gh calls that dispatch / wait for / download a run (a fake gh)."""
import os
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)

import anim_eyes as E  # noqa: E402  (the module under test)


def line(msg, level="INFO"):
    """A client log line as Forge writes it: timestamp, thread, logger, then the message."""
    return "[22:14:07] [Render thread/%s] [com.solegendary.reignofnether.matchstart.EyesAnimScene/]: %s\n" % (level, msg)


class Spec(unittest.TestCase):
    def test_defaults(self):
        runs = E.parse_spec("bone_dragon_unit")
        self.assertEqual(len(runs), 1)
        r = runs[0]
        self.assertEqual((r.unit, r.clip), ("bone_dragon_unit", "idle"))
        self.assertEqual(r.views, ("side", "front", "rear"))
        self.assertEqual((r.dist, r.rec), (0.0, E.DEFAULT_TICKS["idle"]))

    def test_full_entry(self):
        r = E.parse_spec("bone_dragon_unit:turn:rear+side:14:300")[0]
        self.assertEqual((r.clip, r.views, r.dist, r.rec), ("turn", ("rear", "side"), 14.0, 300))

    def test_namespace_and_fly_alias_match_the_java(self):
        # the Java side drops a leading "reignofnether:" and reads fly as walk - the Python must too, or the run
        # numbers in the log would point at the wrong sheet
        r = E.parse_spec("reignofnether:bone_dragon_unit:fly:side")[0]
        self.assertEqual((r.unit, r.clip, r.views), ("bone_dragon_unit", "walk", ("side",)))

    def test_bad_views_are_dropped_and_none_left_means_all(self):
        self.assertEqual(E.parse_spec("a_unit:idle:side+top")[0].views, ("side",))
        self.assertEqual(E.parse_spec("a_unit:idle:top")[0].views, ("side", "front", "rear"))

    def test_several_entries_and_blank_ones(self):
        runs = E.parse_spec("a_unit:walk,, b_unit:death:front ")
        self.assertEqual([(r.unit, r.clip) for r in runs], [("a_unit", "walk"), ("b_unit", "death")])

    def test_default_windows_per_clip(self):
        for clip, ticks in E.DEFAULT_TICKS.items():
            self.assertEqual(E.parse_spec("a_unit:%s" % clip)[0].rec, ticks)
        self.assertEqual(set(E.DEFAULT_TICKS), set(E.CLIPS))

    def test_unknown_clip_is_an_error_naming_the_real_ones(self):
        with self.assertRaises(ValueError) as cm:
            E.parse_spec("a_unit:somersault")
        for c in E.CLIPS:
            self.assertIn(c, str(cm.exception))

    def test_steps_run_by_view_in_order(self):
        steps = E.steps(E.parse_spec("a_unit:idle:side+front,b_unit:walk:rear"))
        self.assertEqual([(i, r.unit, v) for i, r, v in steps],
                         [(0, "a_unit", "side"), (0, "a_unit", "front"), (1, "b_unit", "rear")])

    def test_spec_round_trip(self):
        s = E.build_spec("bone_dragon_unit", "turn", ("rear", "side"), 12.5, 300)
        r = E.parse_spec(s)[0]
        self.assertEqual((r.unit, r.clip, r.views, r.dist, r.rec), ("bone_dragon_unit", "turn", ("rear", "side"), 12.5, 300))

    def test_spec_is_safe_to_hand_to_a_jvm_flag(self):
        self.assertTrue(E.safe_spec("bone_dragon_unit:turn:rear+side:12.5:300,a_unit:idle"))
        for bad in ("a_unit idle", "a_unit;rm", "a_unit:idle\n", '"a_unit"', "$(x)", ""):
            self.assertFalse(E.safe_spec(bad), repr(bad))

    def test_unit_names(self):
        self.assertEqual(E.unit_id("halberdier"), "halberdier_unit")
        self.assertEqual(E.unit_id("Bone_Dragon_Unit"), "bone_dragon_unit")
        self.assertEqual(E.unit_id("reignofnether:zombie_unit"), "zombie_unit")


class Log(unittest.TestCase):
    RUN = ("[Eyes] anim-run 0 bone_dragon_unit turn side window 240 ticks from game tick 1200, a frame every 6.7 ticks, "
           "unit at (10.5, 70.0, 4.5)")

    def frame(self, step, view, idx, t, extra="swing=0.00 limb=0.85 yaw=-90.0 pos=1.0,70.0,2.0"):
        return line("[Eyes] anim-frame %d %s %d bat_eyes_20_anim_r%02d_%s_f%03d.png t=%.2f %s"
                    % (step, view, idx, step, view, idx, t, extra))

    def test_frames_runs_and_notes(self):
        text = "".join([
            line("[Eyes] 20_anim: 2 run(s) on a pad at (10.5, 70.0, 4.5): bone_dragon_unit:turn:side"),
            line(self.RUN),
            self.frame(0, "side", 0, 1200.40),
            self.frame(0, "side", 1, 1207.05, "swing=0.50 limb=0.9 yaw=-80.5 pos=3.0,70.0,2.0"),
            line("garbage anim-frame without the tag"),
            line("[Eyes] anim-run-end 0 2 frames in 240 ticks (31 s wall)"),
            line("[Eyes] 20_anim: run 1 - no 'foo_unit' appeared after 20 s", "ERROR"),
        ])
        log = E.parse_log(text)
        fs = log.frames[0]
        self.assertEqual([f.idx for f in fs], [0, 1])
        self.assertAlmostEqual(fs[1].t, 1207.05)
        self.assertAlmostEqual(fs[1].swing, 0.5)
        self.assertAlmostEqual(fs[1].yaw, -80.5)
        self.assertEqual(fs[0].file, "bat_eyes_20_anim_r00_side_f000.png")
        self.assertEqual(log.runs[0]["window"], 240)
        self.assertTrue(any("no 'foo_unit'" in n for n in log.notes))
        self.assertTrue(any("2 run(s) on a pad" in n for n in log.notes))

    def test_comma_decimals_from_a_locale(self):
        log = E.parse_log(self.frame(2, "rear", 3, 5.5).replace("t=5.50", "t=5,50").replace("swing=0.00", "swing=0,25"))
        f = log.frames[2][0]
        self.assertAlmostEqual(f.t, 5.5)
        self.assertAlmostEqual(f.swing, 0.25)

    def test_a_frame_line_with_no_state_still_counts(self):
        # the unit was gone (a death clip): the scene writes state=gone and no numbers
        log = E.parse_log(line("[Eyes] anim-frame 0 side 7 bat_eyes_20_anim_r00_side_f007.png t=1300.00 gone"))
        f = log.frames[0][0]
        self.assertEqual(f.idx, 7)
        self.assertIsNone(f.swing)

    def test_empty_log(self):
        log = E.parse_log("")
        self.assertEqual((log.frames, log.runs, log.notes), ({}, {}, []))

    def test_duplicate_frame_lines_keep_one(self):
        # the log is searched in two places on the runner and can be concatenated twice
        text = self.frame(0, "side", 0, 10.0) * 2
        self.assertEqual(len(E.parse_log(text).frames[0]), 1)


class Choosing(unittest.TestCase):
    def frames(self, ts):
        return [E.Frame(step=0, view="side", idx=i, file="f%d.png" % i, t=t, swing=None, limb=None, yaw=None)
                for i, t in enumerate(ts)]

    def test_even_times_over_the_window(self):
        got = E.pick(self.frames([i * 6.7 for i in range(36)]), 10)
        self.assertEqual(len(got), 10)
        self.assertEqual(got[0].idx, 0)
        self.assertEqual(got[-1].idx, 35)
        gaps = [b.t - a.t for a, b in zip(got, got[1:])]
        self.assertLess(max(gaps) - min(gaps), 14)           # no gap more than a couple of captures off

    def test_uneven_captures_pick_by_time_not_index(self):
        # a stall: 20 frames bunched in the first 10 ticks, then 10 more over the next 90
        ts = [i * 0.5 for i in range(20)] + [10 + i * 10 for i in range(10)]
        got = E.pick(self.frames(ts), 5)
        wanted = [0, 25, 50, 75, 100]
        for g, w in zip(got, wanted):
            self.assertLessEqual(abs((g.t - ts[0]) - w), 12, (g.t, w))

    def test_fewer_frames_than_asked(self):
        self.assertEqual(len(E.pick(self.frames([0, 5, 9]), 10)), 3)
        self.assertEqual(E.pick([], 10), [])

    def test_time_zero_is_the_first_capture(self):
        fs = self.frames([1200.4, 1210.0, 1220.0])
        self.assertEqual(E.seconds_of(fs[2], fs[0]), (1220.0 - 1200.4) / 20.0)

    def test_crop_box_is_the_centre_square(self):
        l, t, r, b = E.crop_box((1600, 900), 0.6)
        self.assertEqual((r - l, b - t), (540, 540))
        self.assertEqual((l + r) / 2, 800)
        self.assertEqual((t + b) / 2, 450)

    def test_crop_never_leaves_the_frame(self):
        l, t, r, b = E.crop_box((300, 200), 1.5)
        self.assertGreaterEqual(l, 0)
        self.assertGreaterEqual(t, 0)
        self.assertLessEqual(r, 300)
        self.assertLessEqual(b, 200)


def make_frames(dirpath, steps, n, size=(1600, 900)):
    """Synthetic captures: a dark frame with a coloured square that moves with the frame number (so the test can see
    which frame landed in which cell)."""
    from PIL import Image, ImageDraw
    for step, view in steps:
        for i in range(n):
            im = Image.new("RGB", size, (30, 60, 30))
            d = ImageDraw.Draw(im)
            cx = size[0] // 2
            cy = size[1] // 2
            d.rectangle((cx - 60, cy - 60 + i, cx + 60, cy + 60 + i), fill=(200, 40 + 4 * i, 60))
            im.save(os.path.join(dirpath, "bat_eyes_20_anim_r%02d_%s_f%03d.png" % (step, view, i)))


def make_log(steps, n, t0=1200.0, every=6.0):
    text = "[Eyes] 20_anim: %d run(s)\n" % len(steps)
    for step, view in steps:
        text += "[Eyes] anim-run %d a_unit attack %s window %d ticks from game tick %d, a frame every %.1f ticks, unit at (1,2,3)\n" % (
            step, view, int(n * every), int(t0), every)
        for i in range(n):
            text += ("[Eyes] anim-frame %d %s %d bat_eyes_20_anim_r%02d_%s_f%03d.png t=%.2f swing=%.2f limb=0.50 yaw=-90.0 pos=1,2,3\n"
                     % (step, view, i, step, view, i, t0 + i * every, (i % 10) / 10.0))
    return text


class Sheet(unittest.TestCase):
    def test_end_to_end_one_sheet_per_clip(self):
        from PIL import Image
        steps = [(0, "side"), (1, "front"), (2, "rear")]
        with tempfile.TemporaryDirectory() as d:
            make_frames(d, steps, 36)
            out = os.path.join(d, "out")
            paths = E.collect(d, "a_unit:attack", make_log(steps, 36), out, n=10)
            self.assertEqual(len(paths), 1)
            self.assertTrue(os.path.basename(paths[0]).startswith("a_unit_attack"))
            im = Image.open(paths[0])
            self.assertLessEqual(max(im.size), 2000)
            self.assertGreater(im.size[0], 600)
            self.assertGreater(im.size[1], 300)
            # the crop is the centre: the moving square must be inside every cell, and the cells must differ over time
            row_h = (im.size[1] - 60) // 3
            first = im.getpixel((10 + 60, 44 + row_h // 3))
            self.assertNotEqual(first, (24, 26, 24), "the first cell is empty")

    def test_sheet_names_the_clip_the_views_and_the_time(self):
        steps = [(0, "side"), (1, "front")]
        with tempfile.TemporaryDirectory() as d:
            make_frames(d, steps, 12)
            paths = E.collect(d, "a_unit:attack:side+front", make_log(steps, 12), os.path.join(d, "o"), n=6)
            meta = E.describe(paths[0])
            self.assertIn("a_unit", meta["title"])
            self.assertIn("attack", meta["title"])
            self.assertEqual(meta["views"], ["side", "front"])
            self.assertEqual(meta["frames"], 6)

    def test_selected_frames_are_kept_one_by_one_too(self):
        steps = [(0, "side")]
        with tempfile.TemporaryDirectory() as d:
            make_frames(d, steps, 20)
            out = os.path.join(d, "o")
            E.collect(d, "a_unit:idle:side", make_log(steps, 20), out, n=5)
            kept = sorted(f for f in os.listdir(os.path.join(out, "a_unit_idle")) if f.endswith(".png"))
            self.assertEqual(len(kept), 5)
            self.assertTrue(kept[0].startswith("side_"))

    def test_no_log_falls_back_to_even_spacing(self):
        steps = [(0, "side")]
        with tempfile.TemporaryDirectory() as d:
            make_frames(d, steps, 20)
            paths = E.collect(d, "a_unit:idle:side", "", os.path.join(d, "o"), n=5)
            self.assertEqual(len(paths), 1)

    def test_a_run_with_no_frames_is_reported_not_hidden(self):
        steps = [(0, "side")]
        with tempfile.TemporaryDirectory() as d:
            make_frames(d, steps, 10)
            log = make_log(steps, 10) + line("[Eyes] 20_anim: run 1 - no 'zzz_unit' appeared after 20 s", "ERROR")
            with self.assertRaises(E.CollectError) as cm:
                E.collect(d, "a_unit:idle:side,zzz_unit:idle:side", log, os.path.join(d, "o"), n=4)
            self.assertIn("zzz_unit", str(cm.exception))
            self.assertIn("no 'zzz_unit' appeared", str(cm.exception))

    def test_a_good_run_still_gets_its_sheet_when_another_failed(self):
        steps = [(0, "side")]
        with tempfile.TemporaryDirectory() as d:
            make_frames(d, steps, 10)
            out = os.path.join(d, "o")
            try:
                E.collect(d, "a_unit:idle:side,zzz_unit:idle:side", make_log(steps, 10), out, n=4)
            except E.CollectError as e:
                self.assertEqual(len(e.sheets), 1)
                self.assertTrue(os.path.exists(e.sheets[0]))
            else:
                self.fail("expected a CollectError")

    def test_attack_labels_carry_the_swing(self):
        steps = [(0, "side")]
        with tempfile.TemporaryDirectory() as d:
            make_frames(d, steps, 12)
            paths = E.collect(d, "a_unit:attack:side", make_log(steps, 12), os.path.join(d, "o"), n=4)
            self.assertTrue(any("sw=" in lab for lab in E.describe(paths[0])["labels"]))


class FakeGh:
    """Stands in for the gh CLI: records the calls, answers from a script."""

    def __init__(self, answers):
        self.calls = []
        self.answers = list(answers)

    def __call__(self, args):
        self.calls.append(list(args))
        if not self.answers:
            raise AssertionError("unexpected gh call %r" % (args,))
        a = self.answers.pop(0)
        if isinstance(a, Exception):
            raise a
        return a


class Ci(unittest.TestCase):
    def test_dispatch_args(self):
        self.assertEqual(E.dispatch_args("a_unit:idle", "claude/x"),
                         ["workflow", "run", "eyes.yml", "--ref", "claude/x", "-f", "anim=a_unit:idle"])

    def test_dispatch_refuses_an_unsafe_spec(self):
        with self.assertRaises(ValueError):
            E.dispatch_args("a_unit idle; rm", "claude/x")

    def test_pick_run_takes_the_first_dispatch_after_ours(self):
        runs = [
            {"databaseId": 3, "createdAt": "2026-10-22T12:00:09Z"},
            {"databaseId": 2, "createdAt": "2026-10-22T12:00:03Z"},
            {"databaseId": 1, "createdAt": "2026-10-22T11:00:00Z"},
        ]
        self.assertEqual(E.pick_run(runs, "2026-10-22T12:00:01Z")["databaseId"], 2)
        self.assertIsNone(E.pick_run(runs, "2026-10-22T12:30:00Z"))

    def test_wait_polls_until_done_and_returns_the_conclusion(self):
        import json
        gh = FakeGh([json.dumps({"status": "queued"}), json.dumps({"status": "in_progress"}),
                     json.dumps({"status": "completed", "conclusion": "success", "url": "https://x/1"})])
        sleeps = []
        out = E.wait_run(7, gh, sleep=sleeps.append, timeout=600, poll=5)
        self.assertEqual(out["conclusion"], "success")
        self.assertEqual(len(sleeps), 2)
        self.assertEqual(gh.calls[0][:3], ["run", "view", "7"])

    def test_wait_times_out_loudly(self):
        import json
        clock = iter(range(0, 10000, 100))
        gh = FakeGh([json.dumps({"status": "in_progress"})] * 100)
        with self.assertRaises(E.CiError) as cm:
            E.wait_run(7, gh, sleep=lambda s: None, timeout=300, poll=100, now=lambda: next(clock))
        self.assertIn("7", str(cm.exception))

    def test_download_args(self):
        self.assertEqual(E.download_args(7, "/tmp/x"), ["run", "download", "7", "-n", "anim-7", "-D", "/tmp/x"])

    def test_no_artifact_is_explained(self):
        gh = FakeGh([RuntimeError("no artifact matches anim-7")])
        with self.assertRaises(E.CiError) as cm:
            E.download(7, "/tmp/nowhere-anim", gh)
        self.assertIn("anim-7", str(cm.exception))


class Offline(unittest.TestCase):
    def test_units_with_data_files_are_found_by_name_or_entity_id(self):
        self.assertTrue(E.offline_data("halberdier").endswith(os.path.join("design", "anims", "halberdier.json")))
        self.assertTrue(E.offline_data("vindicator_unit").endswith("halberdier.json"))
        self.assertIsNone(E.offline_data("bone_dragon_unit"))

    def test_attack_means_the_clip_with_a_blow(self):
        import json
        with open(E.offline_data("halberdier")) as f:
            data = json.load(f)
        self.assertEqual(E.offline_clip(data, "attack"), "thrust")
        self.assertEqual(E.offline_clip(data, "walk"), "walk")
        self.assertIsNone(E.offline_clip(data, "turn"))

    def test_cli_help_names_the_modes(self):
        out = subprocess.run([sys.executable, os.path.join(HERE, "anim_eyes.py"), "--help"], capture_output=True, text=True)
        self.assertEqual(out.returncode, 0)
        for word in ("--ci", "--collect", "unit", "clip"):
            self.assertIn(word, out.stdout)

    def test_cli_unit_without_data_and_without_ci_says_what_to_do(self):
        out = subprocess.run([sys.executable, os.path.join(HERE, "anim_eyes.py"), "bone_dragon_unit", "turn"],
                             capture_output=True, text=True)
        self.assertNotEqual(out.returncode, 0)
        self.assertIn("--ci", out.stdout + out.stderr)

    def test_cli_unknown_clip(self):
        out = subprocess.run([sys.executable, os.path.join(HERE, "anim_eyes.py"), "halberdier", "somersault", "--ci"],
                             capture_output=True, text=True)
        self.assertNotEqual(out.returncode, 0)
        self.assertIn("idle", out.stdout + out.stderr)

    def test_offline_halberdier_idle_makes_a_sheet(self):
        from PIL import Image
        with tempfile.TemporaryDirectory() as d:
            out = subprocess.run([sys.executable, os.path.join(HERE, "anim_eyes.py"), "halberdier", "idle", "--out", d],
                                 capture_output=True, text=True)
            self.assertEqual(out.returncode, 0, out.stdout + out.stderr)
            path = [ln.split(" ", 1)[1] for ln in out.stdout.splitlines() if ln.startswith("sheet ")][0]
            with Image.open(path) as im:
                self.assertLessEqual(max(im.size), 2000)


if __name__ == "__main__":
    unittest.main()
