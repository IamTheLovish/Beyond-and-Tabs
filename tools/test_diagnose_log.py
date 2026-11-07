#!/usr/bin/env python3
"""Self-check for tools/diagnose_log.py: uv run --no-project --python 3.12 python -I tools/test_diagnose_log.py"""
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import diagnose_log  # noqa: E402


class DiagnoseLogTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with open(os.path.join(HERE, "diagnose_log_sample.log"), encoding="utf-8") as f:
            cls.text = f.read()
        cls.r = diagnose_log.analyse(cls.text)

    def test_versions(self):
        v = self.r["versions"]
        self.assertEqual(v["launcher"], "Prism Launcher")
        self.assertIn("17.0.9", v["java"])
        self.assertEqual(v["mod"], "bat-1.5.0-beta-4")
        self.assertEqual(v["forge"], "Minecraft 1.20.1, Forge 47.4.0")

    def test_world_lobby_domain(self):
        self.assertEqual(self.r["world_loaded"].clock(), "02:13:30")
        self.assertEqual(self.r["lan"].clock(), "02:13:45")
        self.assertEqual(self.r["domain"], "happy-fox-12.uk.e4mc.link")
        self.assertEqual(len(self.r["screens"]), 4)

    def test_shader_errors_counted_with_first_time(self):
        self.assertEqual(len(self.r["shader_errs"]), 4)
        self.assertEqual(self.r["shader_errs"][0].clock(), "02:14:55")

    def test_errors_deduplicated(self):
        counts = sorted(c[0] for c in self.r["errors"].values())
        self.assertEqual(counts, [2], "two 'Failed to save chunk N' lines are one entry")

    def test_last_events_stop_before_stopping(self):
        ev = diagnose_log.last_events(self.r)
        self.assertLessEqual(len(ev), diagnose_log.LAST_EVENTS)
        self.assertNotIn("Stopping!", ev[-1].msg)
        self.assertTrue(all("[Frame]" not in e.msg for e in ev))

    def test_verdict_sentence(self):
        v = diagnose_log.verdict(self.r)
        self.assertIn("World loaded at 02:13:30", v)
        self.assertIn("sat in the lobby 92 s with 0 joins", v)
        self.assertIn("shader pack was broken from 02:14:55", v)
        self.assertIn("ComplementaryReimagined_r5.9.3", v)
        self.assertIn("75% or more of the RAM", v)
        self.assertIn("lobby seats 1/4", v)
        self.assertIn("stopped at 02:15:17", v)

    def test_report_runs_and_empty_log(self):
        out = diagnose_log.report(self.text)
        self.assertIn("VERDICT:", out)
        self.assertIn("happy-fox-12.uk.e4mc.link", out)
        self.assertIn("no readable lines", diagnose_log.verdict(diagnose_log.analyse("")))
        self.assertIn("No world", diagnose_log.verdict(diagnose_log.analyse(
            "[22Oct2026 01:00:00.000] [main/INFO] [x/]: hello")))


if __name__ == "__main__":
    unittest.main(verbosity=1)
