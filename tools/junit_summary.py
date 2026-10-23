#!/usr/bin/env python3
"""One-notice summary of the JUnit run inside `./gradlew build` (plan 008).

Why: the fast table-driven tests (bar/src/test) only protect the match if a green build can PROVE they ran. Job logs
may be unreachable from an agent, but check-run annotations are not, so CI calls this after the build and the count
shows up as a `::notice::` (like the animation bench). It never fails the build - the test task itself does that.

Usage: python3 tools/junit_summary.py [bar/build/test-results/test]
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET


def main() -> int:
    folder = sys.argv[1] if len(sys.argv) > 1 else "bar/build/test-results/test"
    files = sorted(glob.glob(os.path.join(folder, "TEST-*.xml")))
    if not files:
        print(f"::warning::JUnit: no result files in {folder} - did the test task run?")
        return 0
    tests = failed = skipped = 0
    seconds = 0.0
    per_class = []
    for f in files:
        suite = ET.parse(f).getroot()
        t = int(suite.get("tests", 0))
        fl = int(suite.get("failures", 0)) + int(suite.get("errors", 0))
        sk = int(suite.get("skipped", 0))
        s = float(suite.get("time", 0))
        tests, failed, skipped, seconds = tests + t, failed + fl, skipped + sk, seconds + s
        per_class.append((s, suite.get("name", os.path.basename(f)).rsplit(".", 1)[-1], t))
    per_class.sort(reverse=True)
    slowest = ", ".join(f"{n} {s:.1f}s" for s, n, _ in per_class[:3])
    print(f"::notice::JUnit: {tests} tests in {len(files)} classes, {failed} failed, {skipped} skipped, "
          f"{seconds:.1f} s total (slowest: {slowest})")
    for f in files:
        for case in ET.parse(f).getroot().iter("testcase"):
            if case.find("skipped") is not None:
                print(f"::notice::JUnit skipped: {case.get('classname', '').rsplit('.', 1)[-1]}.{case.get('name')}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
