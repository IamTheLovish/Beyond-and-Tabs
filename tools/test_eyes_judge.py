#!/usr/bin/env python3
"""tools/eyes_judge.py: the proof gate, the measurements and the critic sheet for the CI "eyes" structure stills.

Oct 28: hours were lost because critics judged stills that did not show the structure, and because their colour
claims ("olive", "too dark") were wrong when measured. The rule: no proof that the subject is in the still -> no
critique; every colour claim is measured. These tests build tiny synthetic lanes (PNGs + an eyes-lines.txt in the
format EyesDirector.logAim writes) so no real still is needed.

Run: python3 tools/test_eyes_judge.py"""
import colorsys
import importlib
import json
import pathlib
import random
import statistics
import subprocess
import sys
import tempfile
import unittest
import warnings

from PIL import Image

HERE = pathlib.Path(__file__).resolve().parent
TOOL = HERE / "eyes_judge.py"
KEY = "15_meadow_sampler_rts"

# the meadow still's lines, verbatim from a real eyes-structures-shaders run (build 7b47b8a, Oct 28): the client count
# comes from the render thread, the server's from a task queued to the server thread, so it lands AFTER the clip-mark
REAL = """\
[20:14:35] [Render thread/INFO]: [Eyes] 15_meadow_sampler: '/home/runner/work/Beyond-and-Tabs/Beyond-and-Tabs/eyes-structures/meadow_sampler.nbt' 48x18x48 placed at -295, 71, 17 (pad centre -271, 71, 41)
[20:15:05] [Render thread/INFO]: [Eyes] 15_meadow_sampler_rts: aimed at -271, 76, 41 - the camera at -276, 91, 46 looks at -271, 41 (28 frames, 30.1 s in the phase)
[20:15:05] [Render thread/INFO]: [Eyes] 15_meadow_sampler_rts: the structure's box holds 3648 non-air blocks on the client
[20:15:05] [Render thread/INFO]: [Eyes] clip-mark 15_meadow_sampler_rts 1793218505565
[20:15:05] [Server thread/INFO]: [Eyes] 15_meadow_sampler_rts: ... and 3648 on the server
[20:15:06] [IO-Worker-11/INFO]: [Eyes] 15_meadow_sampler_rts: Saved screenshot as bat_eyes_15_meadow_sampler_rts.png
"""


def EJ():
    """The tool, imported lazily so that every test fails on its own while the tool does not exist yet."""
    if str(HERE) not in sys.path:
        sys.path.insert(0, str(HERE))
    return importlib.import_module("eyes_judge")


def proof_lines(key=KEY, aim=(-271, 76, 41), looks=(-271, 41), client=3648, server=3648):
    """The evidence lines of one still; None leaves that line out."""
    out = []
    if aim is not None and looks is not None:
        out.append(f"[20:15:05] [Render thread/INFO]: [Eyes] {key}: aimed at {aim[0]}, {aim[1]}, {aim[2]} - the camera "
                   f"at -276, 91, 46 looks at {looks[0]}, {looks[1]} (28 frames, 30.1 s in the phase)")
    if client is not None:
        out.append(f"[20:15:05] [Render thread/INFO]: [Eyes] {key}: the structure's box holds {client} non-air "
                   "blocks on the client")
    out.append(f"[20:15:05] [Render thread/INFO]: [Eyes] clip-mark {key} 1793218505565")
    if server is not None:
        out.append(f"[20:15:05] [Server thread/INFO]: [Eyes] {key}: ... and {server} on the server")
    return out


def make_lane(d, keys=(KEY,), colour=(90, 160, 60), size=(64, 36), lines=None, build="abc1234", **proof):
    """A lane folder: one flat-colour PNG per key, an eyes-lines.txt (proof for every key unless `lines` is given)."""
    d = pathlib.Path(d)
    d.mkdir(parents=True, exist_ok=True)
    for k in keys:
        Image.new("RGB", size, colour).save(d / f"bat_eyes_{k}.png")
    if lines is None:
        lines = ["[20:10:03] [main/ERROR]: Mixin config soundculling.mixins.json does not specify \"minVersion\" property"]
        for k in keys:
            lines += proof_lines(k, **proof)
    (d / "eyes-lines.txt").write_text("\n".join(lines) + "\n")
    (d / "README.md").write_text(f"Screenshots from build {build} at 2026-10-28T20:18:47Z\n")
    return d


def run(*args):
    p = subprocess.run([sys.executable, str(TOOL), *map(str, args)], capture_output=True, text=True)
    return p.returncode, p.stdout, p.stderr


SMALL = "0,0,32,18"     # a crop that fits the tiny synthetic stills


class ParseLines(unittest.TestCase):
    def test_the_real_log_format(self):
        p = EJ().parse_lines(REAL)
        self.assertEqual(list(p), [KEY])
        self.assertEqual(p[KEY]["aimed_at"], (-271, 76, 41))
        self.assertEqual(p[KEY]["looks_at"], (-271, 41))
        self.assertEqual(p[KEY]["client"], 3648)
        self.assertEqual(p[KEY]["server"], 3648)

    def test_the_server_line_may_come_before_the_client_line(self):
        lines = proof_lines()
        lines[1], lines[-1] = lines[-1], lines[1]
        p = EJ().parse_lines("\n".join(lines))
        self.assertEqual((p[KEY]["client"], p[KEY]["server"]), (3648, 3648))

    def test_each_still_keeps_its_own_numbers_and_the_last_line_wins(self):
        text = "\n".join(proof_lines("15_a_rts", client=10, server=10) + proof_lines("15_a_rts_close", client=20, server=20)
                         + proof_lines("15_a_rts", client=12, server=12))
        p = EJ().parse_lines(text)
        self.assertEqual((p["15_a_rts"]["client"], p["15_a_rts"]["server"]), (12, 12))
        self.assertEqual((p["15_a_rts_close"]["client"], p["15_a_rts_close"]["server"]), (20, 20))

    def test_a_missing_line_is_none_not_zero(self):
        p = EJ().parse_lines("\n".join(proof_lines(aim=None, looks=None, client=None, server=None)))
        self.assertEqual(p, {})     # nothing but a clip-mark: the still has no evidence at all
        p = EJ().parse_lines("\n".join(proof_lines(client=None)))
        self.assertIsNone(p[KEY]["client"])
        self.assertEqual(p[KEY]["server"], 3648)

    def test_noise_and_stack_traces_are_ignored(self):
        text = "java.lang.RuntimeException: Unknown variable: BIOME_PALE_GARDEN\n\tat a.b.C(C.java:5)\n" + REAL
        self.assertEqual(list(EJ().parse_lines(text)), [KEY])


class StillKeys(unittest.TestCase):
    def test_only_rts_stills_are_judged(self):
        k = EJ().still_key
        self.assertEqual(k("bat_eyes_15_meadow_sampler_rts.png"), "15_meadow_sampler_rts")
        self.assertEqual(k("bat_eyes_15_meadow_sampler_rts_close.png"), "15_meadow_sampler_rts_close")
        for other in ("bat_eyes_15_meadow_sampler_34.png", "bat_eyes_15_meadow_sampler_dusk.png",
                      "bat_eyes_02_rts_default.png", "bat_eyes_02b_rts_default_look_off.png",
                      "bat_eyes_01_title.png", "README.md"):
            self.assertIsNone(k(other), other)

    def test_short_name_drops_the_step_prefix(self):
        self.assertEqual(EJ().short_name(KEY), "meadow_sampler_rts")
        self.assertEqual(EJ().short_name("15_ground_after_rts_close"), "ground_after_rts_close")


class ProofGate(unittest.TestCase):
    def check(self, **kw):
        ej = EJ()
        p = ej.parse_lines("\n".join(proof_lines(**kw)))
        return ej.check_proof(p.get(KEY))

    def test_a_complete_consistent_proof_passes(self):
        self.assertEqual(self.check(), [])

    def test_no_lines_at_all_fails(self):
        self.assertTrue(EJ().check_proof(None))

    def test_a_missing_aim_line_fails(self):
        problems = self.check(aim=None, looks=None)
        self.assertTrue(any("aimed at" in m for m in problems), problems)

    def test_a_missing_client_line_fails(self):
        problems = self.check(client=None)
        self.assertTrue(any("client" in m for m in problems), problems)

    def test_a_missing_server_line_fails(self):
        problems = self.check(server=None)
        self.assertTrue(any("server" in m for m in problems), problems)

    def test_zero_blocks_fail_on_either_side(self):
        self.assertTrue(any("0 non-air" in m for m in self.check(client=0, server=0)))
        self.assertTrue(self.check(client=0))
        self.assertTrue(self.check(server=0, client=100))

    def test_a_client_far_below_the_server_fails_at_ninety_percent(self):
        self.assertEqual(self.check(client=900, server=1000), [])          # exactly 0.9: still a pass
        problems = self.check(client=899, server=1000)
        self.assertTrue(any("899" in m and "1000" in m for m in problems), problems)
        self.assertTrue(self.check(client=3000, server=3648))              # the unmeshed-void case

    def test_a_client_above_the_server_is_not_a_failure(self):
        self.assertEqual(self.check(client=1010, server=1000), [])

    def test_the_threshold_is_a_parameter(self):
        ej = EJ()
        p = ej.parse_lines("\n".join(proof_lines(client=800, server=1000)))
        self.assertTrue(ej.check_proof(p[KEY]))
        self.assertEqual(ej.check_proof(p[KEY], min_share=0.75), [])

    def test_a_camera_looking_away_from_the_aim_fails(self):
        problems = self.check(aim=(-271, 76, 41), looks=(-250, 41))
        self.assertTrue(any("looks at" in m for m in problems), problems)
        self.assertEqual(self.check(aim=(-271, 76, 41), looks=(-269, 42)), [])   # 2 blocks off: fine

    def test_every_problem_is_listed_not_just_the_first(self):
        self.assertGreaterEqual(len(self.check(client=0, server=None)), 2)


def reference_measure(im, box):
    """tools measure.py of Oct 28 (the definitions past rounds' numbers come from), copied as it was."""
    im = im.convert("RGB").crop(box)
    hs, ss, ls = [], [], []
    with warnings.catch_warnings():     # Pillow 12 deprecates getdata (same values as get_flattened_data)
        warnings.simplefilter("ignore", DeprecationWarning)
        pixels = list(im.getdata())
    for r, g, b in pixels:
        h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
        hs.append(h * 360)
        ss.append(s * 100)
        ls.append(0.299 * r + 0.587 * g + 0.114 * b)
    ml = statistics.median(ls)
    dark = sum(1 for lum in ls if lum < 0.8 * ml) / len(ls)
    return statistics.median(hs), statistics.median(ss), ml, ml / 255, dark


class Measure(unittest.TestCase):
    def test_a_flat_colour(self):
        m = EJ().measure(Image.new("RGB", (20, 20), (100, 150, 50)), (0, 0, 20, 20))
        h, s, _ = colorsys.rgb_to_hsv(100 / 255, 150 / 255, 50 / 255)
        self.assertAlmostEqual(m["hue"], h * 360, places=6)
        self.assertAlmostEqual(m["sat"], s * 100, places=6)
        self.assertAlmostEqual(m["luma"], 0.299 * 100 + 0.587 * 150 + 0.114 * 50, places=6)     # Rec.601: 123.65
        self.assertAlmostEqual(m["luma01"], m["luma"] / 255, places=9)
        self.assertEqual(m["dark_share"], 0.0)

    def test_dark_share_is_the_fraction_under_eighty_percent_of_the_median_luma(self):
        im = Image.new("RGB", (10, 10), (200, 200, 200))
        im.paste((10, 10, 10), (0, 0, 3, 10))           # 3 of 10 columns dark
        m = EJ().measure(im, (0, 0, 10, 10))
        self.assertAlmostEqual(m["dark_share"], 0.3)
        self.assertAlmostEqual(m["luma"], 200.0, places=6)
        im.paste((161, 161, 161), (0, 0, 1, 10))        # 161 > 0.8 * 200 = 160: not dark; the other two columns are
        self.assertAlmostEqual(EJ().measure(im, (0, 0, 10, 10))["dark_share"], 0.2)

    def test_the_numbers_equal_the_reference_definitions_exactly(self):
        rng = random.Random(7)
        im = Image.new("RGB", (40, 30))
        im.putdata([(rng.randrange(256), rng.randrange(256), rng.randrange(256)) for _ in range(40 * 30)])
        for box in ((0, 0, 40, 30), (5, 3, 25, 20)):
            m = EJ().measure(im, box)
            ref = reference_measure(im, box)
            got = (m["hue"], m["sat"], m["luma"], m["luma01"], m["dark_share"])
            for a, b in zip(got, ref):
                self.assertEqual(a, b)

    def test_stars_count_tuft_sized_dark_spots_not_grain_or_big_shadows(self):
        # r33 night (#350 on r5.9.3): dark share was swamped by big wall smears and a dark-blob count by lawn grain;
        # a star is a tuft-sized dark spot (15-300 px) under 0.75 x the median luma after a 2 px blur
        rng = random.Random(3)
        im = Image.new("RGB", (200, 200))
        im.putdata([(v, v, v) for v in (150 + rng.randrange(-12, 13) for _ in range(200 * 200))])   # grain only
        self.assertEqual(EJ().measure(im, (0, 0, 200, 200))["stars"], 0)
        for x, y in ((20, 20), (60, 20), (100, 30), (20, 80), (70, 90)):
            im.paste((60, 60, 60), (x, y, x + 8, y + 8))                 # five tuft-sized stars
        im.paste((60, 60, 60), (120, 120, 190, 190))                      # one big shadow: not a star
        self.assertEqual(EJ().measure(im, (0, 0, 200, 200))["stars"], 5)
        self.assertEqual(EJ().measure(im, (0, 0, 50, 50))["stars"], 1)    # only the crop counts

    def test_only_the_crop_is_measured(self):
        im = Image.new("RGB", (40, 40), (255, 0, 0))
        im.paste((0, 0, 255), (10, 10, 30, 30))
        m = EJ().measure(im, (10, 10, 30, 30))
        self.assertAlmostEqual(m["hue"], 240.0, places=6)

    def test_a_crop_outside_the_image_is_an_error(self):
        with self.assertRaises(ValueError):
            EJ().measure(Image.new("RGB", (40, 40)), (0, 0, 41, 40))
        with self.assertRaises(ValueError):
            EJ().measure(Image.new("RGB", (40, 40)), (10, 10, 10, 20))     # empty


class Crops(unittest.TestCase):
    def test_the_meadow_default_and_the_override_order(self):
        ej = EJ()
        self.assertEqual(ej.parse_box("900,80,1400,300"), (900, 80, 1400, 300))
        # the meadow still's table crop is open meadow: r33 (Oct 28) showed the old (900,80)-(1400,300) crop held a
        # tree's shadow in its corner, which swamped the tufts' dark share (0.137 vs 0.050 on open meadow)
        self.assertEqual(ej.crop_for(KEY, [])[0], (1000, 250, 1415, 383))
        # a still without a table entry takes the default, and says so
        self.assertEqual(ej.crop_for("15_ground_after_rts", [])[0], (900, 80, 1400, 300))
        self.assertNotEqual(ej.crop_for("15_ground_after_rts", [])[1], ej.crop_for(KEY, [])[1])
        # CLI flag: NAME=box (short name or full key) beats the table; a bare box is for every other still
        box, _ = ej.crop_for(KEY, [("meadow_sampler_rts", (1, 2, 3, 4))])
        self.assertEqual(box, (1, 2, 3, 4))
        box, _ = ej.crop_for(KEY, [(KEY, (5, 6, 7, 8)), (None, (9, 9, 19, 19))])
        self.assertEqual(box, (5, 6, 7, 8))
        box, _ = ej.crop_for("15_other_rts", [(KEY, (5, 6, 7, 8)), (None, (9, 9, 19, 19))])
        self.assertEqual(box, (9, 9, 19, 19))

    def test_a_malformed_box_is_an_error(self):
        for bad in ("1,2,3", "a,b,c,d", "10,10,5,5", "-1,0,5,5"):
            with self.assertRaises(ValueError, msg=bad):
                EJ().parse_box(bad)


class Compare(unittest.TestCase):
    @staticmethod
    def doc(metrics, crop=(0, 0, 32, 18), ok=True, lane="eyes-structures"):
        return {"lanes": {lane: {"stills": {KEY: {"proof": {"ok": ok}, "crop": list(crop), "metrics": metrics}}}}}

    M1 = {"hue": 80.0, "sat": 60.0, "luma": 120.0, "luma01": 0.47, "dark_share": 0.10}

    def test_deltas_per_metric(self):
        m2 = {"hue": 85.0, "sat": 55.0, "luma": 130.0, "luma01": 0.51, "dark_share": 0.05}
        rows = EJ().compare(self.doc(self.M1), self.doc(m2))
        by = {r["metric"]: r for r in rows}
        self.assertEqual(set(by), {"hue", "sat", "luma", "dark_share"})
        self.assertAlmostEqual(by["hue"]["delta"], 5.0)
        self.assertAlmostEqual(by["sat"]["delta"], -5.0)
        self.assertAlmostEqual(by["luma"]["delta"], 10.0)
        self.assertAlmostEqual(by["dark_share"]["delta"], -0.05)
        self.assertEqual((by["hue"]["old"], by["hue"]["new"]), (80.0, 85.0))

    def test_hue_is_circular(self):
        a, b = dict(self.M1, hue=359.0), dict(self.M1, hue=1.0)
        by = {r["metric"]: r for r in EJ().compare(self.doc(a), self.doc(b))}
        self.assertAlmostEqual(by["hue"]["delta"], 2.0)
        by = {r["metric"]: r for r in EJ().compare(self.doc(b), self.doc(a))}
        self.assertAlmostEqual(by["hue"]["delta"], -2.0)

    def test_a_still_without_numbers_on_either_side_has_no_delta_and_says_why(self):
        rows = EJ().compare(self.doc(self.M1), self.doc(None, ok=False))
        self.assertTrue(rows)
        self.assertTrue(all(r["delta"] is None and r["note"] for r in rows), rows)
        rows = EJ().compare({"lanes": {}}, self.doc(self.M1))
        self.assertTrue(all(r["delta"] is None and "new" in r["note"] for r in rows), rows)

    def test_a_changed_crop_is_flagged(self):
        rows = EJ().compare(self.doc(self.M1, crop=(0, 0, 32, 18)), self.doc(self.M1, crop=(0, 0, 16, 9)))
        self.assertTrue(all("crop" in r["note"] for r in rows), rows)


class Cli(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = pathlib.Path(self.tmp.name)

    def test_a_good_lane_passes_prints_a_table_and_saves_json(self):
        lane = make_lane(self.root / "eyes-structures")
        out_json = self.root / "round.json"
        code, out, err = run(lane, "--crop", SMALL, "--save", out_json)
        self.assertEqual(code, 0, out + err)
        row = [ln for ln in out.splitlines() if KEY in ln][0]
        self.assertTrue(row.lstrip().startswith("|"), out)          # a markdown table
        self.assertIn("3648", row)
        self.assertIn("PASS", row)
        h, s, _ = colorsys.rgb_to_hsv(90 / 255, 160 / 255, 60 / 255)
        self.assertIn(f"{h * 360:.0f}", row)
        doc = json.loads(out_json.read_text())
        st = doc["lanes"]["eyes-structures"]["stills"][KEY]
        self.assertTrue(st["proof"]["ok"])
        self.assertEqual((st["proof"]["client"], st["proof"]["server"]), (3648, 3648))
        self.assertEqual(st["crop"], [0, 0, 32, 18])
        self.assertAlmostEqual(st["metrics"]["hue"], h * 360, places=2)
        self.assertEqual(doc["lanes"]["eyes-structures"]["build"], "abc1234")

    def test_zero_blocks_fail_the_gate_with_a_clear_message_and_no_numbers(self):
        lane = make_lane(self.root / "bad", client=0, server=3648)
        out_json = self.root / "round.json"
        code, out, err = run(lane, "--crop", SMALL, "--save", out_json)
        self.assertEqual(code, 1, out + err)
        self.assertIn("FAIL", err)
        self.assertIn(KEY, err)
        self.assertIn("0 non-air", err)
        self.assertIn("no critique", err.lower())
        st = json.loads(out_json.read_text())["lanes"]["bad"]["stills"][KEY]
        self.assertFalse(st["proof"]["ok"])
        self.assertIsNone(st["metrics"])      # no proof, no colour claim

    def test_one_failing_still_fails_the_whole_run(self):
        keys = (KEY, "15_meadow_sampler_rts_close")
        lines = proof_lines(KEY) + proof_lines("15_meadow_sampler_rts_close", client=100, server=1000)
        lane = make_lane(self.root / "lane", keys=keys, lines=lines)
        code, out, err = run(lane, "--crop", SMALL)
        self.assertEqual(code, 1)
        fails = [ln for ln in err.splitlines() if ln.startswith("FAIL")]
        self.assertTrue(fails, err)
        self.assertTrue(all("meadow_sampler_rts_close" in ln for ln in fails), fails)   # the good still is not named

    def test_a_missing_lines_file_fails(self):
        lane = make_lane(self.root / "lane")
        (lane / "eyes-lines.txt").unlink()
        code, out, err = run(lane, "--crop", SMALL)
        self.assertEqual(code, 1)
        self.assertIn("eyes-lines.txt", err)

    def test_a_lane_with_no_rts_still_fails_instead_of_passing_empty(self):
        lane = make_lane(self.root / "lane", keys=("15_meadow_sampler_34",))
        code, out, err = run(lane, "--crop", SMALL)
        self.assertEqual(code, 1)
        self.assertIn("no RTS still", err)

    def test_a_logged_still_without_a_png_fails(self):
        lane = make_lane(self.root / "lane")
        (lane / f"bat_eyes_{KEY}.png").unlink()
        Image.new("RGB", (64, 36)).save(lane / "bat_eyes_15_other_rts.png")
        (lane / "eyes-lines.txt").write_text("\n".join(proof_lines(KEY) + proof_lines("15_other_rts")) + "\n")
        code, out, err = run(lane, "--crop", SMALL)
        self.assertEqual(code, 1)
        self.assertIn(KEY, err)
        self.assertIn("PNG", err)

    def test_first_person_stills_are_listed_as_not_judged_and_never_fail(self):
        lane = make_lane(self.root / "lane", keys=(KEY, "15_meadow_sampler_34", "15_meadow_sampler_dusk"),
                         lines=proof_lines(KEY))
        code, out, err = run(lane, "--crop", SMALL)
        self.assertEqual(code, 0, out + err)
        self.assertIn("not judged", out)
        self.assertIn("15_meadow_sampler_34", out)

    def test_the_default_crop_is_the_meadow_one(self):
        lane = make_lane(self.root / "big", size=(1600, 900))
        out_json = self.root / "big.json"
        code, out, err = run(lane, "--save", out_json)
        self.assertEqual(code, 0, out + err)
        st = json.loads(out_json.read_text())["lanes"]["big"]["stills"][KEY]
        self.assertEqual(st["crop"], [1000, 250, 1415, 383])      # the table's open-meadow crop (r33)

    def test_a_crop_outside_the_still_is_a_usage_error_not_a_traceback(self):
        lane = make_lane(self.root / "lane")        # 64 x 36: the default crop does not fit
        code, out, err = run(lane)
        self.assertEqual(code, 2)
        self.assertIn("crop", err)
        self.assertNotIn("Traceback", err)

    def test_a_named_crop_beats_the_default_and_the_name_may_be_short(self):
        lane = make_lane(self.root / "lane")
        out_json = self.root / "r.json"
        code, out, err = run(lane, "--crop", "meadow_sampler_rts=4,4,20,12", "--save", out_json)
        self.assertEqual(code, 0, out + err)
        st = json.loads(out_json.read_text())["lanes"]["lane"]["stills"][KEY]
        self.assertEqual(st["crop"], [4, 4, 20, 12])

    def test_two_lanes_a_label_and_a_missing_directory(self):
        a = make_lane(self.root / "dir-a")
        b = make_lane(self.root / "dir-b", colour=(200, 40, 40))
        out_json = self.root / "r.json"
        code, out, err = run(f"plain={a}", f"shaders={b}", "--crop", SMALL, "--save", out_json)
        self.assertEqual(code, 0, out + err)
        self.assertEqual(sorted(json.loads(out_json.read_text())["lanes"]), ["plain", "shaders"])
        code, out, err = run(self.root / "nope", "--crop", SMALL)
        self.assertEqual(code, 2)
        self.assertIn("nope", err)

    def test_against_prints_the_deltas_and_the_build_change(self):
        prev_dir = make_lane(self.root / "r1" / "eyes-structures", colour=(90, 160, 60), build="1111111")
        cur_dir = make_lane(self.root / "r2" / "eyes-structures", colour=(90, 160, 60), build="2222222")
        # the new round: the same lane, a darker grass in the left third of the crop
        im = Image.new("RGB", (64, 36), (90, 160, 60))
        im.paste((20, 40, 15), (0, 0, 12, 36))
        im.save(cur_dir / f"bat_eyes_{KEY}.png")
        prev_json = self.root / "prev.json"
        self.assertEqual(run(prev_dir, "--crop", SMALL, "--save", prev_json)[0], 0)
        code, out, err = run(cur_dir, "--crop", SMALL, "--against", prev_json)
        self.assertEqual(code, 0, out + err)
        self.assertIn("1111111", out)
        self.assertIn("2222222", out)
        dark = [ln for ln in out.splitlines() if "dark_share" in ln and KEY in ln]
        self.assertEqual(len(dark), 1, out)
        self.assertIn("+0.375", dark[0])        # 12 of the crop's 32 columns are dark: 0.375


class Sheet(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = pathlib.Path(self.tmp.name)

    def test_the_zoom_is_a_two_times_nearest_neighbour_of_the_measured_crop(self):
        ej = EJ()
        lane = make_lane(self.root / "lane", size=(80, 45))
        im = Image.new("RGB", (80, 45))
        im.putdata([((x * 7) % 256, (y * 11) % 256, (x + y) % 256) for y in range(45) for x in range(80)])
        im.save(lane / f"bat_eyes_{KEY}.png")
        res = ej.judge_lane(lane, "lane", [(None, (10, 5, 40, 25))])
        sheet, tiles = ej.build_sheet(lane, "lane", res)
        t = [t for t in tiles if t["key"] == KEY][0]
        zx, zy, zw, zh = t["zoom_box"]
        self.assertEqual((zw, zh), (60, 40))        # 2 x the 30 x 20 crop
        crop = im.crop((10, 5, 40, 25))
        zoom = sheet.convert("RGB").crop((zx, zy, zx + zw, zy + zh))
        for y in range(zh):
            for x in range(zw):
                self.assertEqual(zoom.getpixel((x, y)), crop.getpixel((x // 2, y // 2)), (x, y))

    def test_a_real_sized_lane_stays_under_2500_px_wide(self):
        ej = EJ()
        keys = (KEY, "15_meadow_sampler_rts_close")
        lane = make_lane(self.root / "lane", keys=keys, size=(1600, 900))
        res = ej.judge_lane(lane, "lane", [])
        sheet, tiles = ej.build_sheet(lane, "lane", res)
        self.assertLessEqual(sheet.width, 2500)
        self.assertGreaterEqual(sheet.width, 1600)      # not a thumbnail wall: the zoom is there
        self.assertEqual(len([t for t in tiles if t["zoom_box"]]), 2)

    def test_a_crop_too_wide_for_the_row_stacks_under_the_still_at_one_to_one(self):
        ej = EJ()
        lane = make_lane(self.root / "lane", size=(2000, 400))
        res = ej.judge_lane(lane, "lane", [(None, (0, 0, 2000, 400))])
        sheet, tiles = ej.build_sheet(lane, "lane", res)
        t = tiles[0]
        self.assertEqual(t["zoom"], 1)                      # 2x (4000 px) and 1x beside the still (2000 + 800) do not fit
        self.assertLessEqual(sheet.width, 2500)
        self.assertGreater(t["zoom_box"][1], t["thumb_box"][1] + t["thumb_box"][3] - 1)    # below the still, not over it

    def test_a_still_without_proof_gets_no_zoom(self):
        ej = EJ()
        lane = make_lane(self.root / "lane", client=0, server=10)
        res = ej.judge_lane(lane, "lane", [(None, (0, 0, 32, 18))])
        sheet, tiles = ej.build_sheet(lane, "lane", res)
        self.assertIsNone(tiles[0]["zoom_box"])
        self.assertTrue(tiles[0]["no_proof"])

    def test_the_cli_writes_one_png_per_lane(self):
        a = make_lane(self.root / "a")
        b = make_lane(self.root / "b")
        out_dir = self.root / "sheets"
        code, out, err = run(f"plain={a}", f"shaders={b}", "--crop", SMALL, "--sheet-dir", out_dir)
        self.assertEqual(code, 0, out + err)
        self.assertEqual(sorted(p.name for p in out_dir.iterdir()), ["plain-sheet.png", "shaders-sheet.png"])
        with Image.open(out_dir / "plain-sheet.png") as im:
            self.assertLessEqual(im.width, 2500)

    def test_the_sheet_is_written_even_when_the_gate_fails(self):
        # the critic's (and the author's) view of a failing lane is the banner that says why
        a = make_lane(self.root / "a", client=0)
        out_dir = self.root / "sheets"
        code, out, err = run(a, "--crop", SMALL, "--sheet-dir", out_dir)
        self.assertEqual(code, 1)
        self.assertTrue((out_dir / "a-sheet.png").exists())


class LaneArgs(unittest.TestCase):
    def test_label_equals_path_but_a_path_with_separators_before_the_equals_is_a_path(self):
        f = EJ().split_lane_arg
        self.assertEqual(f("plain=/tmp/x"), ("plain", "/tmp/x"))
        self.assertEqual(f("/tmp/a=b/c"), (None, "/tmp/a=b/c"))
        self.assertEqual(f("/tmp/x"), (None, "/tmp/x"))
        self.assertEqual(f("D:\\eyes\\plain"), (None, "D:\\eyes\\plain"))

    def test_the_label_defaults_to_the_folder_name_outside_a_git_clone(self):
        with tempfile.TemporaryDirectory() as d:
            sub = pathlib.Path(d, "es-plain")
            sub.mkdir()
            self.assertEqual(EJ().lane_label(sub), "es-plain")


if __name__ == "__main__":
    unittest.main()
