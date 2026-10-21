#!/usr/bin/env python3
"""Claim work before starting it, so two sessions never build the same thing.

Claims live as comments on the claims issue (#91, override with BAT_CLAIMS_ISSUE). Every lane and every agent runs
`check` before starting a task and `take` before writing code.

  python3 tools/claim.py check merged buildings wind      # overlapping open PRs + active claims; exit 1 if any
  python3 tools/claim.py take merged-buildings-1 --lane "Lane A" --branch claude/merged-buildings-1 \
          --what "White Angel wind generators merge, sizes 2-9" [--hours 24]
  python3 tools/claim.py release merged-buildings-1
  python3 tools/claim.py list

Comment format (machine-read; keep it): `CLAIM <slug> | <lane> | <branch> | <what> | until <ISO UTC>` and
`RELEASE <slug>`. A claim is active until released or past its time. Matching is by words (case-insensitive, words of 3+
letters, plus the slug), against PR titles, branch names and claims - crude on purpose: a false alarm costs a look, a
missed duplicate costs a whole PR.
Needs: gh (signed in).
"""
import argparse
import datetime as dt
import json
import os
import re
import subprocess
import sys

REPO = os.environ.get("BAT_REPO", "IamTheLovish/Beyond-and-Tabs")
ISSUE = int(os.environ.get("BAT_CLAIMS_ISSUE", "91"))
CLAIM_RE = re.compile(r"^CLAIM\s+(\S+)\s*\|\s*(.*?)\s*\|\s*(.*?)\s*\|\s*(.*?)\s*\|\s*until\s+(\S+)", re.M)
RELEASE_RE = re.compile(r"^RELEASE\s+(\S+)", re.M)
STOP = {"the", "and", "for", "with", "from", "into", "that", "this", "claude", "lane", "step", "slice", "pass", "fix"}


def gh(path, method="GET", fields=None):
    args = ["gh", "api", "-X", method, path]
    for k, v in (fields or {}).items():
        args += ["-f", f"{k}={v}"]
    out = subprocess.run(args, capture_output=True, text=True)
    if out.returncode != 0:
        sys.exit(f"claim: gh api {method} {path}: {out.stderr.strip() or out.stdout.strip()}")
    return json.loads(out.stdout) if out.stdout.strip() else None


def now():
    return dt.datetime.now(dt.timezone.utc)


def words(text):
    return {w for w in re.findall(r"[a-z0-9]+", text.lower().replace("-", " ").replace("_", " "))
            if len(w) >= 3 and w not in STOP}


def comments():
    out, page = [], 1
    while True:
        batch = gh(f"repos/{REPO}/issues/{ISSUE}/comments?per_page=100&page={page}")
        out += batch
        if len(batch) < 100:
            return out
        page += 1


def active_claims():
    claims = {}
    for c in comments():
        body = c.get("body") or ""
        for m in CLAIM_RE.finditer(body):
            slug, lane, branch, what, until = m.groups()
            claims[slug] = {"slug": slug, "lane": lane, "branch": branch, "what": what, "until": until,
                            "by": c["user"]["login"], "url": c["html_url"]}
        for m in RELEASE_RE.finditer(body):
            claims.pop(m.group(1), None)
    live = {}
    for slug, c in claims.items():
        try:
            if dt.datetime.fromisoformat(c["until"].replace("Z", "+00:00")) > now():
                live[slug] = c
        except ValueError:
            live[slug] = c
    return live


def open_prs():
    prs, page = [], 1
    while True:
        batch = gh(f"repos/{REPO}/pulls?state=open&per_page=100&page={page}")
        prs += batch
        if len(batch) < 100:
            return prs
        page += 1


def cmd_check(a):
    want = words(" ".join(a.words))
    if not want:
        sys.exit("claim check: give a few words describing the work")
    hits = []
    for pr in open_prs():
        overlap = want & words(pr["title"] + " " + pr["head"]["ref"])
        if overlap:
            hits.append(f"PR #{pr['number']} [{pr['head']['ref']}] {pr['title']}  (matches: {', '.join(sorted(overlap))})")
    for c in active_claims().values():
        overlap = want & words(" ".join([c["slug"], c["branch"], c["what"]]))
        if overlap:
            hits.append(f"CLAIM {c['slug']} by {c['lane']} on {c['branch']}: {c['what']} until {c['until']} "
                        f"(matches: {', '.join(sorted(overlap))}) {c['url']}")
    if hits:
        print("Possible overlap - look before starting:")
        print("\n".join("  " + h for h in hits))
        return 1
    print("No overlapping open PR or active claim.")
    return 0


def cmd_take(a):
    live = active_claims()
    if a.slug in live:
        c = live[a.slug]
        sys.exit(f"claim: '{a.slug}' is already claimed by {c['lane']} until {c['until']} ({c['url']})")
    until = (now() + dt.timedelta(hours=a.hours)).strftime("%Y-%m-%dT%H:%MZ")
    body = f"CLAIM {a.slug} | {a.lane} | {a.branch} | {a.what} | until {until}"
    c = gh(f"repos/{REPO}/issues/{ISSUE}/comments", method="POST", fields={"body": body})
    print(f"Claimed: {body}\n{c['html_url']}")
    return 0


def cmd_release(a):
    c = gh(f"repos/{REPO}/issues/{ISSUE}/comments", method="POST", fields={"body": f"RELEASE {a.slug}"})
    print(f"Released {a.slug}: {c['html_url']}")
    return 0


def cmd_list(_a):
    live = active_claims()
    if not live:
        print("No active claims.")
    for c in sorted(live.values(), key=lambda c: c["until"]):
        print(f"{c['slug']}: {c['lane']} on {c['branch']} - {c['what']} (until {c['until']})")
    return 0


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("check"); p.add_argument("words", nargs="+"); p.set_defaults(fn=cmd_check)
    p = sub.add_parser("take"); p.add_argument("slug"); p.add_argument("--lane", required=True)
    p.add_argument("--branch", required=True); p.add_argument("--what", required=True)
    p.add_argument("--hours", type=int, default=24); p.set_defaults(fn=cmd_take)
    p = sub.add_parser("release"); p.add_argument("slug"); p.set_defaults(fn=cmd_release)
    p = sub.add_parser("list"); p.set_defaults(fn=cmd_list)
    a = ap.parse_args()
    return a.fn(a)


if __name__ == "__main__":
    sys.exit(main())
