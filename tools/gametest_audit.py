#!/usr/bin/env python3
"""bar-audit: every GameTest ran exactly once across the shards (build.yml).

Each shard job uploads shard-report.txt (artifact gametests-shard-N): its "[Shards] this server runs N of T tests ..."
line and one "[TestTime] <test> <ms> ms" line per passed test. Given the directory the artifacts were downloaded to,
this fails when
  - a shard's report is missing a [Shards] line or its tests (a shard that ran nothing),
  - the shards disagree on the suite's size T, or the N's do not add up to T,
  - a shard passed a different number of tests than it said it ran, or
  - a test name appears in two shards (it ran twice).
It prints one notice with the per-shard counts and the slowest tests of the whole run.

    python3 tools/gametest_audit.py shard-reports
"""
import os
import re
import sys

SHARDS = re.compile(r"\[Shards\] this server runs (\d+) of (\d+) tests")
TIME = re.compile(r"\[TestTime\] (\S+) (\d+) ms")


def main(root):
    reports = {}
    for d, _, files in os.walk(root):
        for f in files:
            if f == "shard-report.txt":
                reports[os.path.basename(d)] = os.path.join(d, f)
    if not reports:
        print(f"::error::[Shard audit] no shard reports under {root}")
        return 1
    errors, ran, total, names, times = [], {}, set(), {}, []
    for shard, path in sorted(reports.items()):
        text = open(path, errors="replace").read()
        m = SHARDS.search(text)
        mine = {}
        for t in TIME.finditer(text):
            mine[t.group(1)] = int(t.group(2))
        if not m:
            errors.append(f"{shard}: no [Shards] line")
            continue
        n, t = int(m.group(1)), int(m.group(2))
        total.add(t)
        ran[shard] = n
        if len(mine) != n:
            errors.append(f"{shard}: said it runs {n} tests but {len(mine)} passed")
        for name, ms in mine.items():
            if name in names:
                errors.append(f"{name} ran in {names[name]} and in {shard}")
            names[name] = shard
            times.append((ms, name))
    if len(total) != 1:
        errors.append(f"the shards disagree on the suite's size: {sorted(total)}")
    elif sum(ran.values()) != next(iter(total)):
        errors.append(f"the shards run {sum(ran.values())} tests, the suite has {next(iter(total))}")
    elif len(names) != next(iter(total)):
        errors.append(f"{len(names)} distinct tests passed, the suite has {next(iter(total))}")
    summary = ", ".join(f"{s.replace('gametests-shard-', '')}:{n}" for s, n in sorted(ran.items(), key=lambda kv: int(kv[0].rsplit('-', 1)[1])))
    if errors:
        for e in errors:
            print(f"::error::[Shard audit] {e}")
        print(f"::error::[Shard audit] shards {summary}")
        return 1
    print(f"::notice::[Shard audit] every one of the {len(names)} tests ran exactly once, in {len(ran)} shards (tests per shard {summary})")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "shard-reports"))
