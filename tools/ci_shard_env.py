#!/usr/bin/env python3
"""Prints the shell exports that make a runner play one CI shard, cut from the SHARD_n lines of build.yml.

  eval "$(python3 tools/ci_shard_env.py 3)"     # BAT_GAMETEST_ONLY=<SHARD_3's list>
  eval "$(python3 tools/ci_shard_env.py 1)"     # BAT_GAMETEST_SKIP=<every SHARD_n list joined>: shard 1's "everything else"

The same cut build.yml makes (shard 1 skips all lists, shard n runs its own), used by flake-stress.yml's `shard` input
to repeat a whole shard many times. Stdlib only.
"""
import os
import re
import shlex
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
BUILD = os.path.join(HERE, "..", ".github", "workflows", "build.yml")


def shard_lists(text):
    return {int(m.group(1)): m.group(2) for m in re.finditer(r'^\s*SHARD_(\d+):\s*"([^"]*)"', text, re.M)}


def main(argv):
    if len(argv) != 2 or not argv[1].isdigit():
        print("usage: ci_shard_env.py <shard number>", file=sys.stderr)
        return 2
    n = int(argv[1])
    lists = shard_lists(open(BUILD).read())
    if n == 1:
        print("export BAT_GAMETEST_SKIP=" + shlex.quote(",".join(v for v in lists.values() if v)))
    elif n in lists and lists[n]:
        print("export BAT_GAMETEST_ONLY=" + shlex.quote(lists[n]))
    else:
        print(f"no SHARD_{n} line in build.yml (have {sorted(lists)})", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
