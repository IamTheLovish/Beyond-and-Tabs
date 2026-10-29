#!/usr/bin/env python3
"""Dispatch an eyes run with a unique TAG and print its run id - found by its name, never by its time (Oct 28 2026).

A dispatched run's inputs cannot be read back over REST, so sessions used to take "the newest eyes run created after
I dispatched" - and when two sessions dispatched within a minute one judged the other's stills. eyes.yml names a
dispatched run "eyes [<tag>]" (its run-name); this tool makes a unique tag from a label, dispatches, and waits for the
run with exactly that name.

    python3 tools/eyes_dispatch.py --ref claude/meadow-preview --label "r35 preset" \\
        -i shaders=true -i keep_only=true -i "structure=design/eyes/meadow_sampler.nbt" -i build_run=40808088608
    -> prints:  r35-preset-3f9a 40812345678

Exit 0 with "<tag> <run id>"; 1 when the run never showed (the dispatch may still have happened - look for its tag on
the Actions page before dispatching again); 2 usage error. Needs `gh` (REST only). Stdlib only.
"""
import argparse
import json
import re
import secrets
import subprocess
import sys
import time

REPO = "repos/IamTheLovish/Beyond-and-Tabs"
MAX_TAG = 60


def make_tag(label):
    """A label folded to [A-Za-z0-9._-], cut short, plus 4 random hex digits so two dispatches never share a name."""
    base = re.sub(r"[^A-Za-z0-9._-]+", "-", label).strip("-") or "eyes"
    return f"{base[:MAX_TAG - 5].rstrip('-')}-{secrets.token_hex(2)}"


def run_name(tag):
    """The display title eyes.yml gives a run dispatched with this tag (its `run-name`)."""
    return f"eyes [{tag}]"


def find_run(runs, tag):
    """The id of the one run named run_name(tag); None while it has not shown; ValueError if two have that name."""
    hits = [r["id"] for r in runs if r.get("display_title") == run_name(tag)]
    if len(hits) > 1:
        raise ValueError(f"{len(hits)} runs are named '{run_name(tag)}' ({hits}): a tag must be unique")
    return hits[0] if hits else None


def dispatch_fields(ref, tag, inputs):
    """The `gh api -f` fields of a workflow dispatch: the ref, the tag, and every other input."""
    if "tag" in inputs:
        raise ValueError("the tag input is set by this tool (from --label); do not pass -i tag=...")
    return [f"ref={ref}", f"inputs[tag]={tag}"] + [f"inputs[{k}]={v}" for k, v in inputs.items()]


def dispatch(ref, tag, inputs, post, list_runs, sleep=time.sleep, tries=24, every=5):
    """Dispatch once, then look for the run by name up to `tries` times. TimeoutError when it never shows."""
    post(dispatch_fields(ref, tag, inputs))
    for _ in range(tries):
        rid = find_run(list_runs(), tag)
        if rid is not None:
            return rid
        sleep(every)
    raise TimeoutError(f"no run named '{run_name(tag)}' after {tries} looks - check the Actions page before re-dispatching")


def _gh(args):
    r = subprocess.run(["gh", "api", *args], capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(f"gh api {' '.join(args[:3])}: {r.stderr.strip()}")
    return r.stdout


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--ref", required=True, help="the branch whose eyes.yml and build to use")
    ap.add_argument("--label", required=True, help="a few words for the tag, e.g. 'r35 preset'")
    ap.add_argument("-i", "--input", action="append", default=[], metavar="KEY=VALUE", help="a workflow input")
    args = ap.parse_args(argv)
    inputs = {}
    for kv in args.input:
        k, sep, v = kv.partition("=")
        if not sep or not k:
            print(f"eyes_dispatch: input '{kv}' is not KEY=VALUE", file=sys.stderr)
            return 2
        inputs[k] = v
    tag = make_tag(args.label)
    try:
        rid = dispatch(
            args.ref, tag, inputs,
            post=lambda fields: _gh(["-X", "POST", f"{REPO}/actions/workflows/eyes.yml/dispatches"]
                                    + [x for f in fields for x in ("-f", f)]),
            list_runs=lambda: json.loads(_gh([f"{REPO}/actions/workflows/eyes.yml/runs?event=workflow_dispatch"
                                               "&per_page=30"]))["workflow_runs"])
    except ValueError as e:
        print(f"eyes_dispatch: {e}", file=sys.stderr)
        return 2
    except (TimeoutError, RuntimeError) as e:
        print(f"eyes_dispatch: {e}", file=sys.stderr)
        return 1
    print(f"{tag} {rid}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
