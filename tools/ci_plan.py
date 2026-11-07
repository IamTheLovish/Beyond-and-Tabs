#!/usr/bin/env python3
"""The `changes` job of .github/workflows/build.yml: what does this run need to test? Writes three step outputs.

  matrix  JSON list of the GameTest shard numbers >= 2, from the SHARD_n variables of the workflow's env block
          (adding a shard = adding a SHARD_9 line there); shards = their count + 1 (shard 1 is job `bar`).
  code    "true" when anything outside the docs changed, so the GameTests must run; "false" when every changed file
          is documentation (below), and jobs bar / bar-tests are skipped - skipped counts as passing for
          tools/ci_wait.py, and a docs-only main push has nothing new to publish. The fast `build` checks always run.
          "compile" on a pull request whose head commit's subject line carries [compile-only] (below): job bar builds the jar
          and runs the JUnit suite, the GameTests (bar's shard 1, bar-tests) are skipped, and job compile-only fails on
          purpose, so such a head can never look green to ci_wait, merge_pr or the Command Center merger.

[compile-only] (Oct 30, runner budget): a tests-only head exists to prove the new tests compile and then fail in a
flake-stress red run - the full suite on it costs ~70 runner-minutes of the 20 shared runners and proves nothing the red
does not. The tag is read from the head commit's subject line only, so the next push (the fix, a main merge) gets the
full suite again, and prose that mentions the tag in a body does not trigger it. Pull requests only (a squash message on
main may quote the tag); a failed or hung lookup means the full suite.

What was changed is read from the compare API (no history needed):
  pull_request       the PR's changes since its merge base (base.sha...head.sha);
  push to main       everything since the head commit of the LAST SUCCESSFUL main build - not the previous commit: when
                     a quick batch of merges makes builds queue and a pending one is replaced (concurrency group), a
                     docs-only push right after a replaced code push must still test that code;
  workflow_dispatch  always code.
Anything unsure (API error, no base, 300+ files, an empty diff) counts as code. Docs = *.md anywhere, docs/**, the
design sheets design/*.png, and the Windows installer (installer/** + its workflow: own build and Go tests in
installer.yml, nothing in the game jar). Everything else (java, json, py, gradle, workflows, resources, design/*.json, nbt ...) is
code.
"""
import fnmatch
import json
import os
import re
import subprocess
import sys


def is_doc(path):
    """Changes that cannot affect the game jar, so the GameTests skip. The installer counts (lovish Oct 23): it has
    its own build and Go tests in installer.yml."""
    return (path.lower().endswith(".md") or path.startswith("docs/")
            or (fnmatch.fnmatch(path, "design/*.png") and path.count("/") == 1)
            or path.startswith("installer/") or path == ".github/workflows/installer.yml")


def changed_files(repo, base, head):
    """[(filename, previous_filename)] of base...head, page by page (the compare API lists at most 300 files)."""
    files = []
    for page in (1, 2, 3):
        r = subprocess.run(["gh", "api", f"repos/{repo}/compare/{base}...{head}?per_page=100&page={page}", "--jq",
                            ".files[]? | [.filename, (.previous_filename // \"\")] | @tsv"], capture_output=True, text=True)
        if r.returncode:
            raise RuntimeError(r.stderr.strip()[:300])
        got = [ln.split("\t") for ln in r.stdout.splitlines() if ln]
        files += got
        if len(got) < 100:
            break
    return files


def last_good_main_sha(repo):
    r = subprocess.run(["gh", "api", f"repos/{repo}/actions/workflows/build.yml/runs?branch=main&event=push&status=success&per_page=1",
                        "--jq", ".workflow_runs[0].head_sha // empty"], capture_output=True, text=True)
    return r.stdout.strip() if r.returncode == 0 else ""


COMPILE_ONLY_TAG = "[compile-only]"


def compile_only(event, message):
    """True when this run builds and unit-tests only: a pull request whose head commit's subject line has the tag."""
    return event == "pull_request" and COMPILE_ONLY_TAG in (message or "").split(chr(10), 1)[0]


def plan_code(code, event, message):
    """The `code` output: "compile" (tagged PR head with code changes), "true" or "false" (docs only stays false)."""
    if code and compile_only(event, message):
        return "compile"
    return "true" if code else "false"


def head_message(repo, sha):
    """The commit message of sha, or "" when it cannot be read in 60 s (then the full suite runs)."""
    if not repo or not sha:
        return ""
    try:
        r = subprocess.run(["gh", "api", f"repos/{repo}/commits/{sha}", "--jq", ".commit.message"], capture_output=True,
                           text=True, timeout=60)
    except (OSError, subprocess.TimeoutExpired):
        return ""
    return r.stdout if r.returncode == 0 else ""


def decide(env):
    """(code?, reason)"""
    repo, event = env.get("GITHUB_REPOSITORY", ""), env.get("GITHUB_EVENT_NAME", "")
    if event == "pull_request":
        base, head = env.get("PR_BASE", ""), env.get("PR_HEAD", "")
    elif event == "push":
        base, head = last_good_main_sha(repo), env.get("GITHUB_SHA", "")
    else:
        return True, f"{event or 'unknown event'}: always test"
    if not base or not head or base == head:
        return True, "no base commit to compare with"
    try:
        files = changed_files(repo, base, head)
    except RuntimeError as e:
        return True, f"compare failed ({e})"
    if not files or len(files) >= 300:
        return True, f"{len(files)} files in the diff - treated as code"
    code = [f[0] for f in files if not is_doc(f[0])] + [f[1] for f in files if f[1] and not is_doc(f[1])]
    if code:
        return True, f"{len(files)} files changed, e.g. {code[0]}"
    return False, f"{len(files)} files changed, all documentation"


def main():
    shards = sorted(int(m.group(1)) for k in os.environ if (m := re.fullmatch(r"SHARD_(\d+)", k)) and os.environ[k].strip())
    code, why = decide(os.environ)
    event = os.environ.get("GITHUB_EVENT_NAME", "")
    msg = head_message(os.environ.get("GITHUB_REPOSITORY", ""), os.environ.get("PR_HEAD", "")) if code and event == "pull_request" else ""
    planned = plan_code(code, event, msg)
    slim = planned == "compile"
    print(f"[ci_plan] code={code}{' (compile only)' if slim else ''}: {why}; shards 2..{shards[-1] if shards else '?'} = {shards}")
    out = {"code": planned, "matrix": json.dumps(shards), "shards": str(len(shards) + 1)}
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as f:
            for k, v in out.items():
                f.write(f"{k}={v}\n")
    if slim:
        print(f"::notice::[Changes] {COMPILE_ONLY_TAG} head: the jar and JUnit only, no GameTests, not mergeable - {why}")
    else:
        print("::notice::[Changes] " + ("tests run - " + why if code else "docs only, the GameTests are skipped - " + why))
    return 0


if __name__ == "__main__":
    sys.exit(main())
