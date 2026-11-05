#!/usr/bin/env python3
"""Summarise logs/bat-cursor.log (written by CursorDiagClient): grabs per minute, jumps, focus losses, who grabbed.

Usage: python tools/cursor_diag.py [path-to-bat-cursor.log]
Line format: <ISO time> f=<frame> <kind> [detail]   e.g.  2026-10-23T12:00:00.123Z f=88 regrab by OrthoviewClientEvents.onClientTick
"""
import re
import sys
from collections import Counter
from datetime import datetime

LINE = re.compile(r"^(\S+)\s+f=(\d+)\s+(\S+)(?:\s+(.*))?$")


def parse_time(s):
    return datetime.fromisoformat(s.replace("Z", "+00:00"))


def main(path):
    kinds = Counter()
    grabbers = Counter()
    modes = []
    first = last = None
    with open(path, encoding="utf-8", errors="replace") as fh:
        for raw in fh:
            m = LINE.match(raw.strip())
            if not m:
                continue
            when, _frame, kind, detail = parse_time(m.group(1)), m.group(2), m.group(3), m.group(4) or ""
            first = first or when
            last = when
            kinds[kind] += 1
            if kind in ("grab", "regrab", "release"):
                grabbers[(kind, detail.removeprefix("by "))] += 1
            if kind in ("mode-change", "mode-switch"):
                modes.append(f"{when.time()} {kind} {detail}")
    if first is None:
        print("no cursor events found in", path)
        return 1
    minutes = max((last - first).total_seconds() / 60.0, 1 / 60.0)
    grabs = kinds["grab"] + kinds["regrab"] + kinds["release"]
    print(f"span {first.time()} .. {last.time()} ({minutes:.1f} min), {sum(kinds.values())} events")
    print(f"grabs+releases: {grabs} ({grabs / minutes:.1f}/min)")
    print(f"jumps: {kinds['jump']}   focus lost: {kinds['focus-lost']}   focus gained: {kinds['focus-gained']}")
    print(f"screens opened: {kinds['screen-open']}   mode changes: {kinds['mode-change']}   mode switches: {kinds['mode-switch']}")
    if grabbers:
        print("who grabbed/released:")
        for (kind, who), n in grabbers.most_common(10):
            print(f"  {n:5d}  {kind:8s} {who}")
    if modes:
        print("last mode events:")
        for line in modes[-5:]:
            print("  " + line)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "logs/bat-cursor.log"))
