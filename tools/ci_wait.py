#!/usr/bin/env python3
"""Wait quietly for CI on a commit or PR, rerun known flakes once, print one summary.

Why: an agent that polls CI in a sleep loop pays for every check-in. This script does the waiting itself, so a session
starts it once (in the background where the tool allows it) and reads one result at the end.

Usage:
  python3 tools/ci_wait.py --pr 81
  python3 tools/ci_wait.py --sha 57407cca
  python3 tools/ci_wait.py --pr 81 --timeout 120 --no-rerun

Exit codes: 0 = all checks green (or skipped), 1 = red or cancelled, 2 = timed out, 3 = usage / API error.
With --pr it follows the PR to its newest commit, so a push while waiting does not end the wait.

Known flakes live in tools/known_flakes.txt (one GameTest name per line). When every failed GameTest of a run is on
that list, the failed jobs are rerun once (--reruns to change) and the wait continues. A failure that names no test
(build error, check_java, a crash) is never rerun - it is real until someone reads it. Fixing a flake beats listing it:
take a name off the list when its fix lands.
Needs: gh (signed in). Reads failures from check-run annotations, because job logs may be unreachable.
"""
import argparse
import json
import os
import re
import subprocess
import sys
import time

REPO = os.environ.get("BAT_REPO", "IamTheLovish/Beyond-and-Tabs")
HERE = os.path.dirname(os.path.abspath(__file__))
FAILED_RE = re.compile(r"(\b[a-z0-9_]+) failed!")


def gh(path, method="GET", fields=None):
    args = ["gh", "api", "-X", method, path]
    for k, v in (fields or {}).items():
        args += ["-f", f"{k}={v}"]
    out = subprocess.run(args, capture_output=True, text=True)
    if out.returncode != 0:
        raise RuntimeError(f"gh api {method} {path}: {out.stderr.strip() or out.stdout.strip()}")
    return json.loads(out.stdout) if out.stdout.strip() else None


def known_flakes():
    path = os.path.join(HERE, "known_flakes.txt")
    names = set()
    if os.path.exists(path):
        for line in open(path, encoding="utf-8"):
            line = line.split("#", 1)[0].strip()
            if line:
                names.add(line)
    return names


def latest_runs(sha):
    """Newest check run per name (a rerun adds a new run with the same name)."""
    data = gh(f"repos/{REPO}/commits/{sha}/check-runs?per_page=100&filter=latest")
    newest = {}
    for run in data.get("check_runs", []):
        name = run["name"]
        if name not in newest or (run.get("started_at") or "") > (newest[name].get("started_at") or ""):
            newest[name] = run
    # commit statuses count too: `ci-mirror` (the GameTests of a PR labelled ci-mirror, run in the mirror repo's
    # runners - .github/workflows/ci-mirror.yml) arrives as a status, not a check run
    try:
        statuses = gh(f"repos/{REPO}/commits/{sha}/status").get("statuses", [])
    except RuntimeError:
        statuses = []
    for st in statuses:
        state = st.get("state")
        entry = {
            "id": None, "status": "completed" if state in ("success", "failure", "error") else "in_progress",
            "conclusion": {"success": "success", "failure": "failure", "error": "failure"}.get(state),
            "html_url": st.get("target_url") or "", "started_at": st.get("updated_at") or "",
            "mirror": mirror_run(st.get("target_url") or "")}
        if st["context"] == "ci-mirror" and state in ("failure", "error") and entry["mirror"]:
            fresh_mirror_state(entry)
        newest["status:" + st["context"]] = entry
    return newest


MIRROR_RUN_RE = re.compile(r"github\.com/([\w.-]+/[\w.-]+)/actions/runs/(\d+)")


def mirror_run(url):
    """(repo, run id) of the mirror's build run a `ci-mirror` status links to, else None (queued: the link is the repo's
    Actions page)."""
    m = MIRROR_RUN_RE.search(url or "")
    return (m.group(1), int(m.group(2))) if m else None


def annotation_tests(repo, check_run_id, names):
    for ann in gh(f"repos/{repo}/check-runs/{check_run_id}/annotations?per_page=100") or []:
        for m in FAILED_RE.finditer(ann.get("message") or ""):
            if m.group(1) not in names:
                names.append(m.group(1))


def fresh_mirror_state(entry):
    """The `ci-mirror` status is set by ci-mirror-report.yml every ~5 min (often much later), so after a re-run in the
    mirror it still says failure. Read the mirror run itself (a public repo): a re-run in progress is pending, one that
    passed is success - and the report workflow is dispatched so the status itself catches up."""
    repo, rid = entry["mirror"]
    try:
        run = gh(f"repos/{repo}/actions/runs/{rid}")
    except RuntimeError:
        return
    if run.get("status") != "completed":
        entry["status"], entry["conclusion"] = "in_progress", None
    elif run.get("conclusion") == "success":
        entry["conclusion"] = "success"
        try:
            gh(f"repos/{REPO}/actions/workflows/ci-mirror-report.yml/dispatches", method="POST", fields={"ref": "main"})
        except RuntimeError:
            pass


def failed_tests(run_id, mirror=None):
    names = []
    if run_id is None:
        # a commit status (the mirror): its link is the mirror's run, a PUBLIC repo; the "<test> failed!" lines are the
        # annotations of that run's failed jobs, read the same way as ours (Oct 22, #164: a soak stall in the mirror was
        # reported "no failed GameTest named - a real failure", so the known-flake rerun never applied)
        if not mirror:
            return []
        repo, rid = mirror
        try:
            jobs = gh(f"repos/{repo}/actions/runs/{rid}/jobs?per_page=100").get("jobs", [])
            for j in jobs:
                if j.get("conclusion") == "failure":
                    annotation_tests(repo, j["id"], names)
        except RuntimeError:
            pass
        return names
    annotation_tests(REPO, run_id, names)
    return names


def rerun_mirror(mirror):
    """Re-runs the failed jobs of the mirror's run. The mirror is another account's repo: refused without access to it
    (the caller says so). It cannot be re-queued from here: the mirror starts one run per PR head commit."""
    repo, rid = mirror
    gh(f"repos/{repo}/actions/runs/{rid}/rerun-failed-jobs", method="POST")
    return f"mirror run {repo}#{rid}"


def rerun_failed(sha):
    runs = gh(f"repos/{REPO}/actions/runs?head_sha={sha}&per_page=20").get("workflow_runs", [])
    done = []
    for wr in runs:
        if wr.get("status") == "completed" and wr.get("conclusion") == "failure":
            gh(f"repos/{REPO}/actions/runs/{wr['id']}/rerun-failed-jobs", method="POST")
            done.append(f"{wr['name']} #{wr['id']}")
    return done


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--pr", type=int)
    g.add_argument("--sha")
    ap.add_argument("--timeout", type=int, default=90, help="minutes (default 90)")
    ap.add_argument("--interval", type=int, default=60, help="seconds between polls (default 60)")
    ap.add_argument("--reruns", type=int, default=1, help="flake reruns allowed (default 1)")
    ap.add_argument("--no-rerun", action="store_true")
    a = ap.parse_args()

    try:
        sha = gh(f"repos/{REPO}/pulls/{a.pr}")["head"]["sha"] if a.pr else a.sha
        if not a.pr and len(sha) < 40:
            sha = gh(f"repos/{REPO}/commits/{sha}")["sha"]
    except RuntimeError as e:
        print(f"ci_wait: {e}")
        return 3

    flakes = known_flakes()
    reruns_left = 0 if a.no_rerun else a.reruns
    deadline = time.time() + a.timeout * 60
    label = f"PR #{a.pr} ({sha[:8]})" if a.pr else sha[:8]
    log = []

    while True:
        if a.pr:  # follow the PR: a new push supersedes the commit we were waiting on (its runs get cancelled)
            try:
                head = gh(f"repos/{REPO}/pulls/{a.pr}")["head"]["sha"]
            except RuntimeError as e:
                print(f"ci_wait: {e}")
                return 3
            if head != sha:
                log.append(f"followed the PR from {sha[:8]} to {head[:8]}")
                sha, label = head, f"PR #{a.pr} ({head[:8]})"
        try:
            runs = latest_runs(sha)
        except RuntimeError as e:
            print(f"ci_wait: {e}")
            return 3
        pending = [n for n, r in runs.items() if r["status"] != "completed"]
        if runs and not pending:
            bad = {n: r for n, r in runs.items() if r["conclusion"] not in ("success", "skipped", "neutral", "cancelled")}
            cancelled = sorted(n for n, r in runs.items() if r["conclusion"] == "cancelled")
            if cancelled and not bad:
                print(f"CANCELLED {label}: {', '.join(cancelled)} (superseded by a newer push or stopped by hand)"
                      + (f" | {'; '.join(log)}" if log else ""))
                return 1
            if cancelled:
                log.append("cancelled after a failure: " + ", ".join(cancelled))
            if not bad:
                print(f"GREEN {label}: " + ", ".join(sorted(runs)) + (f" | {'; '.join(log)}" if log else ""))
                return 0
            details, all_flaky = [], True
            for name, r in sorted(bad.items()):
                tests = failed_tests(r["id"], r.get("mirror"))
                if not tests:
                    all_flaky = False
                    details.append(f"{name}: {r['conclusion']} (no failed GameTest named - a real failure; read the "
                                   f"check run {r['html_url']})")
                else:
                    unknown = [t for t in tests if t not in flakes]
                    if unknown:
                        all_flaky = False
                    details.append(f"{name}: " + ", ".join(t + ("" if t in unknown else " [known flake]")
                                                         for t in tests))
            if all_flaky and reruns_left > 0:
                reruns_left -= 1
                try:
                    redone = rerun_failed(sha)
                    for n, r in sorted(bad.items()):
                        if r.get("mirror"):
                            try:
                                redone.append(rerun_mirror(r["mirror"]))
                            except RuntimeError as e:
                                print(f"RED {label}: known flake(s) in the mirror, but its run cannot be re-run from here "
                                      f"(needs access to {r['mirror'][0]}): {r['html_url']} | " + " | ".join(details)
                                      + f" | {str(e)[:160]}")
                                return 1
                except RuntimeError as e:
                    print(f"RED {label}: rerun failed: {e} | " + " | ".join(details))
                    return 1
                log.append("reran known flakes: " + "; ".join(details) + f" -> {', '.join(redone) or 'nothing to rerun'}")
                time.sleep(a.interval)
                continue
            print(f"RED {label}: " + " | ".join(details) + (f" | {'; '.join(log)}" if log else ""))
            return 1
        if time.time() > deadline:
            print(f"TIMEOUT {label} after {a.timeout} min; still running: {', '.join(pending) or 'no checks yet'}")
            return 2
        time.sleep(a.interval)


if __name__ == "__main__":
    sys.exit(main())
