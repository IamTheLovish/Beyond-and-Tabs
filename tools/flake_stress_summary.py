#!/usr/bin/env python3
"""The fail rate per test of a flake-stress.yml run, from the samples' stress-<n>.json files.

    python3 tools/flake_stress_summary.py [folder]      (default: the current folder)

Environment (the workflow's inputs): ST_ONLY (`tests`), ST_SHARD (`shard`), ST_FOCUS (`focus`), GITHUB_STEP_SUMMARY.

Prints one `[Stress]` annotation and appends the same lines to the step summary. A run that proves nothing is
INVALID: an `::error::` annotation and exit code 1, so neither a reader of the annotations nor of the run's conclusion
can take it for a clean result. Invalid means: no sample reported; a sample ran 0 tests (Oct 27: four runs were
dispatched with bare test names - no `test:` - ran nothing, "All 0 required tests passed", and were posted as
"[Stress] 0 failures"); or a `test:<prefix>` that was asked for matched no test in any sample.
Real test failures are the measurement, not a broken run: they are listed (most failures first) and exit 0.
Tests: tools/test_flake_stress_summary.py.
"""
import collections
import glob
import json
import os
import sys


def ran_count(sample):
    # samples recorded before the "ran" field existed: count their names
    return sample.get("ran", len(set(sample["failed"]) | set(sample["passed"])))


def unmatched_prefixes(tests, ran_names):
    """Each `test:<prefix>` entry that no test that ran starts with. Batch names cannot be checked from the log."""
    out = []
    for entry in tests.split(","):
        entry = entry.strip()
        if entry.lower().startswith("test:"):
            prefix = entry[5:].strip().lower()
            if prefix and not any(t.startswith(prefix) for t in ran_names):
                out.append(entry)
    return out


def summarise(runs, tests="", shard="", focus=""):
    """Returns (lines, invalid)."""
    n = len(runs)
    fails = collections.Counter(t for r in runs for t in r["failed"])
    passes = collections.Counter(t for r in runs for t in r["passed"])
    ran_names = set(fails) | set(passes)
    empty = [r["run"] for r in runs if ran_count(r) == 0]
    bad_runs = sum(1 for r in runs if r["outcome"] != "success" or r["run"] in empty)
    # a whole shard ignores `tests`, so the prefixes are only checked for a `tests` run
    unmatched = [] if shard.strip() else unmatched_prefixes(tests, ran_names)
    # focus trims a whole shard's hundreds of tests to the ones that matter; a `tests` run names its few tests on
    # purpose, so it lists them all. An entry may be written like `tests` ("test:<prefix>"): the prefix is what counts
    # (Oct 27: "test:<name>" focus entries matched nothing and three stress runs printed no per-test line).
    focus_list = [] if not shard.strip() else [
        f[5:].strip() if f.startswith("test:") else f for f in (e.strip() for e in focus.lower().split(",")) if f]
    focus_list = [f for f in focus_list if f]

    lines = [f"{n} runners; {bad_runs} with any failure"]
    if n == 0:
        lines.append("NO SAMPLE REPORTED: no stress-*.json artifact came back")
    if empty:
        lines.append(f"INVALID: samples that ran 0 tests: {empty} - a `tests` entry needs `test:<name>` "
                     "(a bare name is read as a batch name and matches nothing)")
    if unmatched:
        lines.append(f"INVALID: asked for but matched no test in any sample: {unmatched}")
    for t in sorted(ran_names, key=lambda t: (-fails[t], t)):
        # a whole-shard run has hundreds of tests: list the failures and the focus tests only
        if focus_list and not fails[t] and not any(t.startswith(f) for f in focus_list):
            continue
        lines.append(f"{t}: failed {fails[t]}, passed {passes[t]}")
    if not fails and ran_names:
        lines.append("no test failed in any run")
    crashed = [r["run"] for r in runs if r["outcome"] != "success" and not r["failed"] and r["run"] not in empty]
    if crashed:
        lines.append(f"runs that failed without naming a test (build error, timeout, crash): {crashed}")
    return lines, n == 0 or bool(empty) or bool(unmatched)


def main():
    folder = sys.argv[1] if len(sys.argv) > 1 else "."
    runs = [json.load(open(f)) for f in sorted(glob.glob(os.path.join(folder, "stress-*.json")))]
    lines, invalid = summarise(runs, os.environ.get("ST_ONLY", ""), os.environ.get("ST_SHARD", ""),
                               os.environ.get("ST_FOCUS", ""))
    print(("::error::[Stress] INVALID " if invalid else "::notice::[Stress] ") + "%0A".join(lines))
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if path:
        with open(path, "a") as s:
            s.write("## Flake stress\n\n" + "\n".join("- " + l for l in lines) + "\n")
    return 1 if invalid else 0


if __name__ == "__main__":
    sys.exit(main())
