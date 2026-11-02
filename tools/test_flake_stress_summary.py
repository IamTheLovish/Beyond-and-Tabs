#!/usr/bin/env python3
"""tools/flake_stress_summary.py must never report a stress run that ran no test as clean. Oct 27: four flake-stress
runs were dispatched with bare test names (no `test:`), every sample ran 0 tests ("All 0 required tests passed") and
the summary posted "[Stress] ... no test failed in any run", which was read as "0 failures" proof for a flake fix.

Run: python3 tools/test_flake_stress_summary.py"""
import json
import os
import pathlib
import subprocess
import sys
import tempfile
import unittest

TOOL = pathlib.Path(__file__).resolve().parent / "flake_stress_summary.py"


def run(samples, tests="", shard="", focus=""):
    """Writes one stress-<n>.json per sample, runs the summary in that folder; returns (exit code, stdout, summary)."""
    with tempfile.TemporaryDirectory() as d:
        for s in samples:
            pathlib.Path(d, f"stress-{s['run']}.json").write_text(json.dumps(s))
        step = pathlib.Path(d, "step-summary.md")
        env = dict(os.environ, ST_ONLY=tests, ST_SHARD=shard, ST_FOCUS=focus, GITHUB_STEP_SUMMARY=str(step))
        p = subprocess.run([sys.executable, str(TOOL), d], capture_output=True, text=True, env=env)
        return p.returncode, p.stdout, step.read_text() if step.exists() else ""


def sample(n, passed=(), failed=(), outcome="success", with_ran=True):
    s = {"run": n, "passed": list(passed), "failed": list(failed), "outcome": outcome, "log_found": True}
    if with_ran:
        s["ran"] = len(set(passed) | set(failed))
    return s


class Summary(unittest.TestCase):
    def test_clean_run_passes_and_says_so(self):
        code, out, step = run([sample(1, passed=["a_pile"]), sample(2, passed=["a_pile"])], tests="test:a_pile")
        self.assertEqual(code, 0, out)
        self.assertIn("::notice::[Stress]", out)
        self.assertIn("a_pile: failed 0, passed 2", out)
        self.assertIn("no test failed in any run", out)
        self.assertIn("## Flake stress", step)

    def test_samples_that_ran_no_test_fail_the_summary(self):
        # the Oct 27 case: `tests` held bare names, every sample ran nothing and "succeeded"
        code, out, step = run([sample(1), sample(2)], tests="a_pile_of_units_still_spreads_out")
        self.assertNotEqual(code, 0, out)
        err = [l for l in out.splitlines() if l.startswith("::error")]
        self.assertEqual(len(err), 1, out)
        self.assertIn("ran 0 tests: [1, 2]", err[0])
        self.assertIn("test:<name>", err[0])
        self.assertNotIn("no test failed in any run", out)
        self.assertIn("2 runners; 2 with any failure", out)
        self.assertIn("ran 0 tests", step)

    def test_one_empty_sample_among_good_ones_still_fails(self):
        code, out, _ = run([sample(1, passed=["a_pile"]), sample(2)], tests="test:a_pile")
        self.assertNotEqual(code, 0, out)
        self.assertIn("ran 0 tests: [2]", out)
        self.assertIn("2 runners; 1 with any failure", out)

    def test_samples_from_before_the_ran_field_are_counted_from_their_names(self):
        code, out, _ = run([sample(1, with_ran=False)], tests="test:a_pile")
        self.assertNotEqual(code, 0, out)
        self.assertIn("ran 0 tests: [1]", out)

    def test_a_prefix_that_matched_nothing_fails(self):
        # one typo among several names: the other names ran, the typo ran nowhere
        code, out, _ = run([sample(1, passed=["six_melee_attackers"])], tests="test:six_melee,test:stuck_chaserr")
        self.assertNotEqual(code, 0, out)
        self.assertIn("matched no test in any sample: ['test:stuck_chaserr']", out)

    def test_a_whole_shard_is_not_checked_against_tests(self):
        code, out, _ = run([sample(1, passed=["x"])], tests="test:not_in_this_shard", shard="3")
        self.assertEqual(code, 0, out)

    def test_no_sample_at_all_fails(self):
        code, out, _ = run([], tests="test:a_pile")
        self.assertNotEqual(code, 0, out)
        self.assertIn("no stress-*.json", out)

    def test_real_failures_are_listed_first_and_do_not_change_the_exit_code(self):
        # failures are the measurement (the fail rate), not a broken run: the summary job stays green for them
        code, out, _ = run([sample(1, passed=["b"], failed=["a"], outcome="failure"), sample(2, passed=["a", "b"])],
                           tests="test:a,test:b")
        self.assertEqual(code, 0, out)
        self.assertLess(out.index("a: failed 1, passed 1"), out.index("b: failed 0, passed 2"))
        self.assertIn("2 runners; 1 with any failure", out)

    def test_a_sample_that_failed_without_naming_a_test_makes_the_run_invalid(self):
        # its failure could be one of the tests under stress (a name the log parser missed), so the fail counts would
        # understate it: the run proves nothing until it is looked at
        code, out, _ = run([sample(1, passed=["a"]), sample(2, passed=["a"], outcome="failure")], tests="test:a")
        self.assertNotEqual(code, 0, out)
        self.assertIn("::error::", out)
        self.assertIn("without naming a test (build error, timeout, crash): [2]", out)

    def test_names_with_capitals_match_prefixes_and_focus(self):
        # test names keep their method's case (sealed_arena_has_no_fluid_after_openGround); prefixes are compared
        # case-blind, as GameTestShards does
        code, out, _ = run([sample(1, passed=["sealed_arena_after_openGround"])], tests="test:sealed_arena_after_openground")
        self.assertEqual(code, 0, out)
        code, out, _ = run([sample(1, passed=["sealed_arena_after_openGround", "x"])], shard="1", focus="SEALED_arena")
        self.assertIn("sealed_arena_after_openGround: failed 0, passed 1", out)
        self.assertNotIn("x:", out)

    def test_a_focus_written_with_test_prefix_still_matches(self):
        # Oct 27: focus "test:<name>" (written like `tests`) matched nothing, so a shard run listed no per-test line
        # and the evidence of three stress runs was void (40591016638, 40598837754 and #222's)
        code, out, _ = run([sample(1, passed=["a_pile_of_units", "other"])], shard="1", focus="test:a_pile_of_units")
        self.assertEqual(code, 0, out)
        self.assertIn("a_pile_of_units: failed 0, passed 1", out)
        self.assertNotIn("other:", out)

    def test_a_tests_run_lists_every_test_whatever_the_focus(self):
        # focus only trims a whole shard's hundreds of tests; a `tests` run names its few tests on purpose
        code, out, _ = run([sample(1, passed=["a_pile", "b_test"])], tests="test:a_pile,test:b_test", focus="test:zzz")
        self.assertEqual(code, 0, out)
        self.assertIn("a_pile: failed 0, passed 1", out)
        self.assertIn("b_test: failed 0, passed 1", out)

    def test_focus_hides_passing_tests_it_does_not_name(self):
        code, out, _ = run([sample(1, passed=["tidewrought_t2_x", "other"], failed=["broken"], outcome="failure")],
                           shard="1", focus="tidewrought_t2")
        self.assertIn("tidewrought_t2_x: failed 0, passed 1", out)
        self.assertIn("broken: failed 1, passed 0", out)
        self.assertNotIn("other:", out)


if __name__ == "__main__":
    unittest.main()
