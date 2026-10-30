#!/usr/bin/env python3
"""Two "Never twice" guards (claude/improvement-loop.md, Oct 21 2026) - a rule broken once becomes a check.

1. Camera keys stay camera keys. lovish (Oct 20): "fight command is on a instead of f which is blocking the wasd
   camera" - Attack defaulted to A, which is also pan-left (fixed in #39). A default binding outside the camera
   category on W/A/S/D or an arrow key fails here, unless it is listed in ALLOWED_ON_CAMERA_KEYS with the reason.

2. No new GameTests in the shared hot file. Two PRs that both added tests at the same spot in SkirmishGameTests.java
   conflicted (#44 / #45, Oct 21). New tests go in their own class (gametest/<Topic>GameTests.java), as Lane C does.
   On a pull request this compares the @GameTest count in SkirmishGameTests.java with the base branch and fails if
   it grew. Moving tests OUT of the file is always fine. Outside a PR (a push to main, a local run) it checks against
   origin/main when that ref exists, and skips otherwise.

Usage: python3 tools/check_guards.py      (exit 1 on a failure, with the reason)
"""
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KEYBINDINGS = "bar/src/main/java/com/solegendary/reignofnether/keybinds/Keybindings.java"
HOT_TESTS = "bar/src/main/java/com/solegendary/reignofnether/gametest/SkirmishGameTests.java"

CAMERA_KEYS = {"W", "A", "S", "D", "LEFT", "RIGHT", "UP", "DOWN"}
# key id -> why it may share a camera key (a tap fires the order, a hold pans: keybinds/KeyTaps)
ALLOWED_ON_CAMERA_KEYS = {
    "key.reignofnether.stop": "BAR's Stop on S; tap = order, hold = pan (KeyTaps). Kept until lovish says otherwise.",
    "key.reignofnether.build_grid_2": "BAR grid build menu W: live only while a build tab is open; a hold still pans (KeyTaps). Tester OK 2026-10-26.",
    "key.reignofnether.build_grid_5": "BAR grid build menu A: live only while a build tab is open; a hold still pans (KeyTaps). Tester OK 2026-10-26.",
    "key.reignofnether.build_grid_6": "BAR grid build menu S: live only while a build tab is open; a hold still pans (KeyTaps). Tester OK 2026-10-26.",
    "key.reignofnether.build_grid_7": "BAR grid build menu D: live only while a build tab is open; a hold still pans (KeyTaps). Tester OK 2026-10-26.",
}

BINDING = re.compile(
    r'new\s+Keybinding\(\s*"(?P<id>key\.[^"]+)"\s*,\s*GLFW\.GLFW_KEY_(?P<key>[A-Z0-9_]+)\s*,\s*(?P<cat>CATEGORY_[A-Z_]+)'
    r'(?P<rest>[^;]*);')
ALIAS = re.compile(r'withAlias\(\s*GLFW\.GLFW_KEY_([A-Z0-9_]+)\s*\)')


def check_camera_keys(problems):
    path = os.path.join(ROOT, KEYBINDINGS)
    src = open(path, encoding="utf-8").read()
    found = 0
    for m in BINDING.finditer(src):
        found += 1
        if m.group("cat") == "CATEGORY_CAMERA":
            continue
        keys = [m.group("key")] + ALIAS.findall(m.group("rest"))
        clash = [k for k in keys if k in CAMERA_KEYS]
        if clash and m.group("id") not in ALLOWED_ON_CAMERA_KEYS:
            problems.append(f"{m.group('id')} defaults to {'/'.join(clash)}, a camera pan key "
                            f"(lovish Oct 20: the order on A blocked the WASD camera). Pick another key, or add it "
                            f"to ALLOWED_ON_CAMERA_KEYS in tools/check_guards.py with his OK and the reason.")
    if found < 10:
        problems.append(f"only {found} keybindings parsed from {KEYBINDINGS} - the pattern no longer matches the file")


def git(*args):
    r = subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True)
    return r.returncode, r.stdout


def base_text():
    """The hot test file on the base branch, or None when there is nothing to compare with."""
    base = os.environ.get("GITHUB_BASE_REF")
    if base:
        git("fetch", "--quiet", "--depth=1", "origin", base)
        code, out = git("show", f"FETCH_HEAD:{HOT_TESTS}")
        return out if code == 0 else None
    code, _ = git("rev-parse", "--verify", "--quiet", "origin/main")
    if code != 0:
        return None
    code, out = git("show", f"origin/main:{HOT_TESTS}")
    return out if code == 0 else None


def check_hot_test_file(problems):
    head = open(os.path.join(ROOT, HOT_TESTS), encoding="utf-8").read()
    base = base_text()
    if base is None:
        print("hot test file: no base to compare with, skipped")
        return
    count = lambda text: len(re.findall(r"^\s*@GameTest\b", text, re.M))
    before, after = count(base), count(head)
    print(f"hot test file: {before} -> {after} @GameTest methods in SkirmishGameTests.java")
    if after > before:
        problems.append(f"{after - before} new @GameTest method(s) in SkirmishGameTests.java - put new tests in their "
                        f"own class (gametest/<Topic>GameTests.java); two PRs adding tests to this shared file "
                        f"conflicted on Oct 21 (#44/#45)")


def main():
    problems = []
    check_camera_keys(problems)
    check_hot_test_file(problems)
    for p in problems:
        print("FAIL:", p)
    if problems:
        sys.exit(1)
    print("guards: ok")


if __name__ == "__main__":
    main()
