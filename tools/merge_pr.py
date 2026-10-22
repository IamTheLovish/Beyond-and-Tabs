#!/usr/bin/env python3
"""Merge a green PR and log it in the dev log (issue #150). lovish, Oct 22 01:52: sessions may merge their own PRs
when CI is green - "you can give me a dev log of the prs you pushed and if we dont like them can always remove them".

  python3 tools/merge_pr.py 142 --note "Overlays skip the shader shadow pass: ~23% of a frame back with shaders on"
  python3 tools/merge_pr.py 142 143 144 --note-file notes.md     # several: merged one after another (main builds collapse)
  python3 tools/merge_pr.py 142 --wait --note "..."               # wait for CI first (tools/ci_wait.py, flake reruns), then merge

Run it in the background with --wait right after opening a PR: the session keeps working and the PR lands on its own
when green. A red or timed-out PR is not merged; the line printed says why.

A PR merges only when ALL of these hold, checked right before the merge:
  - every check run and commit status on its newest commit is green or skipped (ci_wait's rules; `ci-mirror` counts);
  - GitHub says it is mergeable (no conflict with main - merge main into the branch first, then wait for CI again);
  - it is not a draft and has no `hold` label (lovish's "wait for me" switch).
Merge commit (not squash), like every merge so far. Then one comment on the dev log issue: title, link, the note (what
and why, what to check in game), files changed, and how to revert. Exit 0 = everything asked for merged.
"""
import argparse
import json
import subprocess
import sys

REPO = "IamTheLovish/beyond-and-tabs"
DEVLOG_ISSUE = 150
GREEN = {"success", "skipped", "neutral"}


def gh(path, method="GET", fields=None, body_file=None):
    cmd = ["gh", "api", "-X", method, path]
    for k, v in (fields or {}).items():
        cmd += ["-f", f"{k}={v}"]
    if body_file:
        cmd += ["-F", f"body=@{body_file}"]
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode:
        raise RuntimeError((r.stderr or r.stdout).strip()[:400])
    return json.loads(r.stdout) if r.stdout.strip() else {}


def blockers(pr):
    """Why this PR may not merge now ([] = it may)."""
    why = []
    if pr.get("draft"):
        why.append("draft")
    if any(l["name"] == "hold" for l in pr.get("labels", [])):
        why.append("label hold")
    if pr.get("mergeable") is False or pr.get("mergeable_state") == "dirty":
        why.append("conflicts with main (merge main into the branch, wait for CI)")
    sha = pr["head"]["sha"]
    runs = gh(f"repos/{REPO}/commits/{sha}/check-runs?per_page=100&filter=latest").get("check_runs", [])
    for r in runs:
        if r["status"] != "completed":
            why.append(f"check {r['name']} still {r['status']}")
        elif r["conclusion"] not in GREEN:
            why.append(f"check {r['name']} {r['conclusion']}")
    for st in gh(f"repos/{REPO}/commits/{sha}/status").get("statuses", []):
        if st["state"] != "success":
            why.append(f"status {st['context']} {st['state']}")
    if not runs:
        why.append("no checks on the newest commit yet")
    return why


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("prs", type=int, nargs="+")
    ap.add_argument("--note", default="", help="what it is, why, what to check in game (dev log text)")
    ap.add_argument("--note-file", help="the note from a file (markdown)")
    ap.add_argument("--dry-run", action="store_true", help="only say whether each PR may merge")
    ap.add_argument("--wait", action="store_true", help="wait for each PR's CI first (tools/ci_wait.py)")
    ap.add_argument("--timeout", type=int, default=150, help="--wait: minutes per PR (default 150)")
    a = ap.parse_args()
    note = open(a.note_file).read().strip() if a.note_file else a.note.strip()
    failed = 0
    for n in a.prs:
        if a.wait:
            w = subprocess.run([sys.executable, __file__.replace("merge_pr.py", "ci_wait.py"), "--pr", str(n),
                                "--timeout", str(a.timeout)], capture_output=True, text=True)
            print(f"#{n}: ci_wait: {(w.stdout or w.stderr).strip().splitlines()[-1] if (w.stdout or w.stderr).strip() else w.returncode}")
            if w.returncode != 0:
                failed += 1
                continue
        try:
            pr = gh(f"repos/{REPO}/pulls/{n}")
            if pr.get("merged"):
                print(f"#{n}: already merged")
                continue
            if pr.get("mergeable") is None:   # GitHub computes it lazily: ask once more
                pr = gh(f"repos/{REPO}/pulls/{n}")
            why = blockers(pr)
            if why:
                failed += 1
                print(f"#{n}: NOT merged - " + "; ".join(why))
                continue
            if a.dry_run:
                print(f"#{n}: may merge")
                continue
            res = gh(f"repos/{REPO}/pulls/{n}/merge", "PUT",
                     {"merge_method": "merge", "sha": pr["head"]["sha"],
                      "commit_title": f"Merge pull request #{n} from {REPO.split('/')[0]}/{pr['head']['ref']}"})
            files = pr.get("changed_files", "?")
            text = (f"### Merged #{n}: {pr['title']}\n{pr['html_url']} - merge commit `{res.get('sha', '')[:8]}`, "
                    f"{files} files\n\n{note or '(no note given - see the PR description)'}\n\n"
                    f"Undo: open #{n} and press **Revert**, or tell a session \"revert #{n}\".")
            tmp = f"/tmp/devlog-{n}.md"
            with open(tmp, "w") as f:
                f.write(text)
            gh(f"repos/{REPO}/issues/{DEVLOG_ISSUE}/comments", "POST", body_file=tmp)
            print(f"#{n}: MERGED ({res.get('sha', '')[:8]}) and logged on #{DEVLOG_ISSUE}")
        except RuntimeError as e:
            failed += 1
            print(f"#{n}: error - {e}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
