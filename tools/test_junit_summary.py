#!/usr/bin/env python3
"""tools/junit_summary.py must name every failing JUnit test and why, as a CI error annotation: job logs are not
always reachable (cloud sessions read annotations), and "14 failed" alone left a session blind (PR #174, Oct 23)."""
import pathlib
import subprocess
import sys
import tempfile
import unittest

TOOL = pathlib.Path(__file__).resolve().parent / "junit_summary.py"
XML = """<?xml version="1.0"?>
<testsuite name="com.x.EconomyMathTest" tests="2" failures="1" errors="0" skipped="0" time="0.1">
  <testcase classname="com.x.EconomyMathTest" name="bankedCapsAtStorage" time="0.01"/>
  <testcase classname="com.x.EconomyMathTest" name="converterRate()" time="0.01">
    <failure message="expected: &lt;50.0&gt; but was: &lt;100.0&gt;" type="org.opentest4j.AssertionFailedError">trace
line 2</failure>
  </testcase>
</testsuite>"""


class Summary(unittest.TestCase):
    def test_each_failure_is_an_error_annotation(self):
        with tempfile.TemporaryDirectory() as d:
            pathlib.Path(d, "TEST-com.x.EconomyMathTest.xml").write_text(XML)
            out = subprocess.run([sys.executable, str(TOOL), d], capture_output=True, text=True).stdout
        self.assertIn("1 failed", out)
        err = [l for l in out.splitlines() if l.startswith("::error")]
        self.assertEqual(len(err), 1, out)
        self.assertIn("EconomyMathTest.converterRate()", err[0])
        self.assertIn("expected: <50.0> but was: <100.0>", err[0])
        self.assertNotIn("\n", err[0])


if __name__ == "__main__":
    unittest.main()
