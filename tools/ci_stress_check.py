#!/usr/bin/env python3
"""Refuses a flake-stress tests list that would run nothing, before any runner starts (the summary,
tools/flake_stress_summary.py, still marks a run whose samples ran 0 tests INVALID).

Oct 27 night: four runs (40565576897, 40581759617, 40582259285, 40582056986) were given bare test names. A
BAT_GAMETEST_ONLY entry names a batch (full or base name) or, as test:<prefix>, tests by name
(gametest/GameTestShards); a bare test name is neither, so the server ran 0 tests on every runner ("All 0 required tests
passed") and the summary said "[Stress] 6 runners; 0 with any failure" - #273 and #289 merged on that.

  ci_stress_check.py entries <comma-separated list> [gametest source dir]
      exit 1 with an ::error for every entry that is neither test:<prefix> nor a batch the sources define
"""
import pathlib
import re
import sys

GAMETEST_DIR = "bar/src/main/java/com/solegendary/reignofnether/gametest"
# vanilla puts every @GameTest without a batch into "defaultBatch" (chunked as defaultBatch:N)
VANILLA_BATCHES = {"defaultbatch"}


def known_batches(src_dir):
    """Every batch name the gametest sources give: batch = "x", or batch = CONSTANT with CONSTANT = "x"."""
    consts, names = {}, set(VANILLA_BATCHES)
    texts = [p.read_text(errors="replace") for p in pathlib.Path(src_dir).rglob("*.java")]
    for t in texts:
        consts.update(re.findall(r'\b([A-Z][A-Z0-9_]*)\s*=\s*"([^"]+)"', t))
    for t in texts:
        for lit, const in re.findall(r'\bbatch\s*=\s*(?:"([^"]+)"|([A-Za-z_][\w.]*))', t):
            if lit:
                names.add(lit.lower())
            elif const.split(".")[-1] in consts:
                names.add(consts[const.split(".")[-1]].lower())
    return names


def unknown_entries(entries, batches):
    """The entries that name neither a test: prefix nor a known batch (full name or the part before ':')."""
    bad = []
    for e in entries:
        low = e.lower()
        if low.startswith("test:"):
            if len(low) <= 5:
                bad.append(e)
            continue
        if low not in batches and low.split(":")[0] not in batches:
            bad.append(e)
    return bad


def main(argv):
    if len(argv) >= 2 and argv[0] == "entries":
        entries = [e.strip() for e in argv[1].split(",") if e.strip()]
        src = argv[2] if len(argv) > 2 else GAMETEST_DIR
        bad = unknown_entries(entries, known_batches(src))
        for e in bad:
            print(f"::error::flake-stress tests entry '{e}' names no batch and is not test:<prefix> - it would run 0 "
                  f"tests and count as a pass. Write test:{e.split(':', 1)[-1] or '<prefix>'} for tests by name.")
        if not entries:
            print("::error::flake-stress: the tests list is empty")
            return 1
        return 1 if bad else 0
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
