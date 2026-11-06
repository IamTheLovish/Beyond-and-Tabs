#!/usr/bin/env python3
"""Static check of the GameTest shard lists at the top of .github/workflows/build.yml (SHARD_2, SHARD_3, ...).

CI runs shard N >= 2 with BAT_GAMETEST_ONLY=<SHARD_N> and shard 1 with BAT_GAMETEST_SKIP=<every list joined>
(gametest/GameTestShards). Every test then runs exactly once only if no two lists name the same test - a test named by
two lists would run twice (and slow two shards), which CI's `bar-audit` job would only report after a full run. This
reads the @GameTest methods out of the sources (a test's name in CI is its lower-cased method name, no class prefix -
the [TestTime] lines), applies the same matching as GameTestShards.named() and fails when

  - a test is named by two lists (it would run twice), or
  - a list entry names no test at all (a typo - the shard would silently be smaller than meant).

It also prints each shard's test count (shard 1 = everything unnamed) so a re-balance can be eyeballed before a push;
real wall times come from each shard's "[GameTest timing]" notice and the `gametests-shard-N` artifacts.
Entries that name a vanilla chunk ("defaultBatch:3") cannot be resolved from the sources and are skipped here.

    python3 tools/check_shards.py
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import shard_plan  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GT = os.path.join(ROOT, "bar/src/main/java/com/solegendary/reignofnether/gametest/")
WORKFLOW = os.path.join(ROOT, ".github/workflows/build.yml")


def tests():
    """(lower-cased method name, lower-cased batch) of every @GameTest - read by tools/shard_plan.py, which resolves
    batch constants and concatenations (batch = BATCH + "_guard_next") and annotations with parentheses inside."""
    return [(t.name, t.batch) for t in shard_plan.source_tests(GT)]


def lists():
    shards = {}
    for m in re.finditer(r'^  SHARD_(\d+):\s*"([^"]*)"', open(WORKFLOW, encoding="utf-8").read(), re.M):
        shards[int(m.group(1))] = [e.strip().lower() for e in m.group(2).split(",") if e.strip()]
    return shards


def named(batch, test, entries):
    bare = test[test.rfind(".") + 1:]   # a test's name in CI is its method name (no class prefix), lower case
    for e in entries:
        if e == batch or e == batch.split(":")[0]:
            return True
        if e.startswith("test:") and len(e) > 5 and (test.startswith(e[5:]) or bare.startswith(e[5:])):
            return True
    return False


def main():
    ts, shards = tests(), lists()
    errors, warnings = [], []
    if not ts:
        print("check_shards: no @GameTest methods found (moved?)")
        return 1
    if not shards:
        print("check_shards: no SHARD_n lists found in build.yml")
        return 1
    count = {n: 0 for n in [1] + sorted(shards)}
    for name, batch in ts:
        hit = [n for n, es in shards.items() if named(batch, name, es)]
        if len(hit) > 1:
            errors.append("%s is named by shards %s - it would run twice" % (name, hit))
        count[hit[0] if hit else 1] += 1
    for n, es in sorted(shards.items()):
        for e in es:
            if re.fullmatch(r".*:\d+", e) and not e.startswith("test:"):
                warnings.append("SHARD_%d: %s names a vanilla chunk (moves as tests are added)" % (n, e))
            elif not any(named(b, t, [e]) for t, b in ts):
                errors.append("SHARD_%d: entry '%s' names no test" % (n, e))
    print("check_shards: %d tests (from sources) over shards 1-%d: %s" % (
        len(ts), max(shards), ", ".join("%d:%d" % (n, c) for n, c in sorted(count.items()))))
    for w in warnings:
        print("  warning:", w)
    for e in errors:
        print("  ERROR:", e)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
