#!/usr/bin/env python3
"""tools/eyes_dispatch.py: dispatch an eyes run with a TAG and find that run's id by its name.

Oct 28: a dispatched run's inputs cannot be read back over REST, so sessions matched their runs by the creation time -
and when two sessions dispatched within a minute, one judged the other's stills. eyes.yml now names a dispatched run
"eyes [<tag>]" (run-name); this tool makes a unique tag, dispatches, and finds the run whose display_title is exactly
that name. These tests need no network: the GitHub calls are passed in.

Run: python3 tools/test_eyes_dispatch.py"""
import importlib
import pathlib
import re
import sys
import unittest

HERE = pathlib.Path(__file__).resolve().parent


def ED():
    if str(HERE) not in sys.path:
        sys.path.insert(0, str(HERE))
    return importlib.import_module("eyes_dispatch")


class Tags(unittest.TestCase):
    def test_a_tag_keeps_the_label_and_is_unique(self):
        ed = ED()
        a, b = ed.make_tag("r35 preset"), ed.make_tag("r35 preset")
        self.assertNotEqual(a, b)
        self.assertTrue(a.startswith("r35-preset-"), a)
        self.assertRegex(a, r"^[A-Za-z0-9._-]+$")

    def test_odd_characters_are_folded_and_the_tag_stays_short(self):
        t = ED().make_tag("fog=0.50; exposure/0.45 !!" * 4)
        self.assertRegex(t, r"^[A-Za-z0-9._-]+$")
        self.assertLessEqual(len(t), 60)

    def test_the_run_name_matches_the_workflow(self):
        # eyes.yml's run-name and this tool must agree, or no run is ever found
        self.assertEqual(ED().run_name("r35-preset-ab12"), "eyes [r35-preset-ab12]")
        wf = (HERE.parent / ".github" / "workflows" / "eyes.yml").read_text()
        m = re.search(r"^run-name: (.+)$", wf, re.M)
        self.assertIsNotNone(m, "eyes.yml has no run-name")
        self.assertIn("format('eyes [{0}]', inputs.tag)", m.group(1))
        self.assertIn("\n      tag:\n", wf, "eyes.yml has no `tag` input")


class FindRun(unittest.TestCase):
    RUNS = [{"id": 1, "display_title": "eyes [r35-plain-aa]"},
            {"id": 2, "display_title": "eyes [r35-preset-bb]"},
            {"id": 3, "display_title": "Wall-shift flake: a rescued unit's repath counts"}]

    def test_the_run_with_exactly_that_name(self):
        self.assertEqual(ED().find_run(self.RUNS, "r35-preset-bb"), 2)

    def test_no_run_yet_is_none(self):
        self.assertIsNone(ED().find_run(self.RUNS, "r35-preset-cc"))

    def test_a_prefix_is_not_a_match(self):
        self.assertIsNone(ED().find_run(self.RUNS, "r35-preset"))

    def test_two_runs_with_one_name_is_an_error(self):
        with self.assertRaises(ValueError):
            ED().find_run(self.RUNS + [{"id": 9, "display_title": "eyes [r35-plain-aa]"}], "r35-plain-aa")


class Dispatch(unittest.TestCase):
    def test_inputs_become_dispatch_fields_with_the_tag(self):
        f = ED().dispatch_fields("claude/meadow-preview", "t1", {"shaders": "true", "structure": "a.nbt,b.nbt"})
        self.assertIn("ref=claude/meadow-preview", f)
        self.assertIn("inputs[tag]=t1", f)
        self.assertIn("inputs[shaders]=true", f)
        self.assertIn("inputs[structure]=a.nbt,b.nbt", f)

    def test_a_tag_input_given_by_hand_is_refused(self):
        with self.assertRaises(ValueError):
            ED().dispatch_fields("main", "t1", {"tag": "mine"})

    def test_dispatch_then_poll_until_the_run_shows(self):
        calls, polls = [], iter([[], [], [{"id": 77, "display_title": "eyes [x-1]"}]])
        rid = ED().dispatch("main", "x-1", {"shaders": "false"}, post=calls.append, list_runs=lambda: next(polls),
                            sleep=lambda s: None, tries=5)
        self.assertEqual(rid, 77)
        self.assertEqual(len(calls), 1)

    def test_a_run_that_never_shows_is_an_error_not_a_guess(self):
        with self.assertRaises(TimeoutError):
            ED().dispatch("main", "x-1", {}, post=lambda f: None, list_runs=lambda: [{"id": 5, "display_title": "eyes"}],
                          sleep=lambda s: None, tries=3)


if __name__ == "__main__":
    unittest.main()
