#!/usr/bin/env python3
"""Where the build's GameTest time goes - per test batch and the slowest single tests - as ONE GitHub notice.

The GameTest step is ~26 of a build's ~32 minutes (Oct 15), and job logs cannot be read through the API, only
annotations. This reads the step's log (bar/gametest.log) and prints one ::notice:: with:
- each batch's wall time, from vanilla's "Running test batch '<name>' (<n> tests)..." lines and the timestamps of the
  log ("[HH:MM:SS] ..."), the last batch running to the last timestamped line;
- the slowest tests, from "<test> passed! (<ms>ms)" lines when the log has them.
If the log has no batch lines (the format changed), the notice says so and quotes the first lines that mention a
batch, so the parser can be fixed from the annotation alone. Never fails the build.

    python3 tools/gametest_timing.py bar/gametest.log
"""
import re
import sys

ANSI = re.compile(r"\x1b\[[0-9;]*[A-Za-z]")
TS = re.compile(r"\[(\d\d):(\d\d):(\d\d)\]")
BATCH = re.compile(r"Running test batch '([^']+)' \((\d+) tests?\)")
PASSED = re.compile(r"(\S+) passed! \((\d+)ms\)")


def clock(m):
    return int(m.group(1)) * 3600 + int(m.group(2)) * 60 + int(m.group(3))


def mmss(s):
    return "%dm%02ds" % (s // 60, s % 60)


def main(path):
    try:
        lines = [ANSI.sub("", ln) for ln in open(path, errors="replace")]
    except OSError as e:
        print("::notice::[GameTest timing] no log: %s" % e)
        return
    batches, tests, mentions = [], [], []
    day, last_raw, now = 0, None, None
    for ln in lines:
        m = TS.search(ln[:40])
        if m:
            raw = clock(m)
            if last_raw is not None and raw < last_raw - 3600:   # past midnight
                day += 86400
            last_raw, now = raw, raw + day
        b = BATCH.search(ln)
        if b and now is not None:
            batches.append([b.group(1), int(b.group(2)), now, None])
        elif "batch" in ln.lower() and len(mentions) < 3:
            mentions.append(ln.strip()[:200])
        p = PASSED.search(ln)
        if p:
            tests.append((int(p.group(2)), p.group(1)))
    out = []
    if batches:
        for i, b in enumerate(batches):
            b[3] = (batches[i + 1][2] if i + 1 < len(batches) else now) - b[2]
        total = now - batches[0][2]
        out.append("%d batches, %s from the first batch to the last log line" % (len(batches), mmss(total)))
        for name, n, _, dur in sorted(batches, key=lambda b: -b[3])[:15]:
            out.append("%s  %s (%d tests)" % (mmss(dur), name, n))
    else:
        out.append("no \"Running test batch\" lines - the log format differs; lines that mention a batch:")
        out.extend(mentions or ["(none)"])
    if tests:
        tests.sort(reverse=True)
        out.append("slowest tests (game time per test):")
        out.extend("%.1fs  %s" % (ms / 1000.0, name) for ms, name in tests[:12])
    print("::notice::[GameTest timing]%0A" + "%0A".join(s.replace("%", "%25") for s in out))


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "gametest.log")
