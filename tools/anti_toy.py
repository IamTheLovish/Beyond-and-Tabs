#!/usr/bin/env python3
"""The anti-toy camera gate (Lane B, Oct 29 2026; lesson 6 of claude/lessons-2026-10-29-itachi2-h.md: "blocky / toy" =
full-block planes). It judges what the photo camera SEES - building_gates.camera(): the first block on each ray - and
classes each picture cell by that block's silhouette:

  full  a full cube: a flat block plane
  half  a vanilla half-block step: stairs, slabs, walls, fences, panes' posts (8 px relief - the Engine Shed already
        had these everywhere and still read as a toy)
  fine  relief finer than half a block: our own moulding blocks (reignofnether:cornice_* ...), trapdoors, buttons,
        lanterns, chains, signs
  open  glass, panes, bars: an opening

Gates on a wall elevation (default the "south" view, the face the RTS camera and the 3/4 still look at):
  sub   share of FINE cells >= 15 %
  run   no run of more than 3 FULL cells along a row or a column without relief (half, fine or open breaks it)

    python3 tools/anti_toy.py FILE.nbt [--view south] [--x0 0 --x1 8]
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import building_gates as G  # noqa: E402
import schem_study as S  # noqa: E402

SUB_MIN = 0.15
RUN_MAX = 3
FINE = ("trapdoor", "button", "lantern", "chain", "sign", "pressure_plate", "carpet", "torch", "candle", "lever",
        "rail", "ladder", "banner", "flower_pot", "end_rod", "lightning_rod", "tripwire", "head", "skull")
HALF = ("_stairs", "_slab", "_wall", "fence", "fence_gate")
OPEN = ("glass", "pane", "iron_bars")


def cell_kind(name, props, side="south"):
    """full / half / fine / open for the block a camera sees; None for air. `side` is kept for profile-aware
    classes (a moulding seen end-on); every class here holds from every side."""
    if name in S.AIR:
        return None
    if name.startswith("reignofnether:"):
        return "fine"
    n = name.split(":", 1)[-1]
    if any(k in n for k in OPEN):
        return "open"
    if any(k in n for k in FINE):
        return "fine"
    if n.endswith(HALF) or any(k in n for k in HALF[3:]):
        return "half"
    return "full"


def picture(b, view="south"):
    """{(u, v): kind} of the camera picture, ground blocks left out."""
    out = {}
    for k, (q, n, pr) in G.camera(b, view).items():
        if n in S.GROUND:
            continue
        kind = cell_kind(n, pr, view)
        if kind:
            out[k] = kind
    return out


def longest_full_run(pic):
    best = 0
    for (u, v), kind in pic.items():
        if kind != "full":
            continue
        for du, dv in ((1, 0), (0, 1)):
            if pic.get((u - du, v - dv)) == "full":
                continue                        # not the start of a run
            n = 0
            while pic.get((u + n * du, v + n * dv)) == "full":
                n += 1
            best = max(best, n)
    return best


def toy_gates(b, view="south"):
    pic = picture(b, view)
    n = len(pic)
    sub = sum(1 for k in pic.values() if k == "fine") / n if n else 0.0
    run = longest_full_run(pic)
    return {"ok": n > 0 and sub >= SUB_MIN and run <= RUN_MAX, "sub": round(sub, 3), "run": run, "cells": n,
            "kinds": {k: sum(1 for x in pic.values() if x == k) for k in ("full", "half", "fine", "open")}}


if __name__ == "__main__":
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("file")
    ap.add_argument("--view", default="south")
    ap.add_argument("--x0", type=int)
    ap.add_argument("--x1", type=int)
    a = ap.parse_args()
    b = S.load(a.file)
    if a.x0 is not None:
        b = S.Build({q: v for q, v in b.blocks.items() if a.x0 <= q[0] <= a.x1})
    print(toy_gates(b, a.view))
