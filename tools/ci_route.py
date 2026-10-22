#!/usr/bin/env python3
"""Where should this PR's GameTests run? build.yml's `changes` job asks; prints `where=here|mirror` to GITHUB_OUTPUT.

lovish, Oct 22 01:53: "I want to be cycling through the 40 runners as well as possible". GitHub Free gives this account
20 concurrent jobs and the org ILovishThe another 20 (its repo ILovishThe/beyond-and-tabs-ci runs the GameTests
from this public repo with mirror-tests.yml - no secrets).

  mirror  the PR has the label `ci-mirror` (forced), or the mirror has fewer GameTest jobs waiting or running than here
  here    everything else: main pushes and manual runs (always: main is tested and published here), the label
          `ci-here` (forced), an API error (unsure -> here), or here
          is no busier than the mirror.

Load = queued + in-progress runs (build.yml here, mirror-tests.yml there), counted from each repo's runs (a queued run that has not started
its jobs yet counts as a whole build, ~10 jobs). The mirror is public: read without a token.
"""
import json
import os
import subprocess
import sys
import urllib.request

MIRROR = "ILovishThe/beyond-and-tabs-ci"
BUILD_JOBS = 10   # a build's GameTest-sized jobs (bar + 7 shards + changes/audit), for runs not yet expanded


def load_here(repo):
    total = 0
    for status in ("queued", "in_progress"):
        r = subprocess.run(["gh", "api", f"repos/{repo}/actions/workflows/build.yml/runs?status={status}&per_page=50",
                            "--jq", ".workflow_runs[].id"], capture_output=True, text=True, timeout=60)
        if r.returncode:
            raise RuntimeError(r.stderr.strip()[:200])
        total += len([x for x in r.stdout.split() if x]) * (BUILD_JOBS if status == "queued" else BUILD_JOBS // 2)
    return total


def load_mirror():
    total = 0
    for status in ("queued", "in_progress"):
        url = f"https://api.github.com/repos/{MIRROR}/actions/workflows/mirror-tests.yml/runs?status={status}&per_page=50"
        with urllib.request.urlopen(url, timeout=30) as resp:
            runs = json.load(resp).get("workflow_runs", [])
        total += len(runs) * (BUILD_JOBS if status == "queued" else BUILD_JOBS // 2)
    return total


def decide(env):
    if env.get("GITHUB_EVENT_NAME") != "pull_request":
        return "here", "not a PR build (main and manual runs are tested here)"
    labels = json.loads(env.get("PR_LABELS") or "[]")
    if "ci-here" in labels:
        return "here", "label ci-here"
    if "ci-mirror" in labels:
        return "mirror", "label ci-mirror"
    try:
        here = load_here(env.get("GITHUB_REPOSITORY", ""))
        there = load_mirror()
    except Exception as e:   # unsure -> here, as before the mirror existed
        return "here", f"load check failed ({e})"
    # this run itself is in progress here: don't count it against us
    here = max(0, here - BUILD_JOBS // 2)
    if there < here:
        return "mirror", f"mirror less busy (~{there} jobs vs ~{here} here)"
    return "here", f"here no busier (~{here} jobs vs ~{there} in the mirror)"


def main():
    where, why = decide(os.environ)
    print(f"[ci_route] where={where}: {why}")
    print(f"::notice::[Route] GameTests run {'in the mirror (' + MIRROR + ')' if where == 'mirror' else 'here'} - {why}")
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as f:
            f.write(f"where={where}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
