#!/usr/bin/env python3
"""
Why did the game feel choppy? Reads a playtester's latest.log and says so in plain English.

    python3 tools/lag_report.py <latest.log>

Uses the perf lines in design/perf-measurement.md: [Frame] [FrameParts] [Spike] [Gfx] (client), [Tick] [Sys] (server)
and [GC] (both). Standard library only. The verdict is one line: GC / server-bound / render-bound / chunk loading /
nothing found. Rules of thumb (the numbers are at the top of this file so they can be argued with):
"""
import re
import sys
from collections import Counter
from datetime import datetime

# ---- thresholds -------------------------------------------------------------------------------------------------
SLOW_FRAME_P50_MS = 20.0         # median frame slower than this (under 50 fps) = the client is the limit
SLOW_TICK_P95_MS = 45.0          # server p95 above this (a 50 ms tick is the 20 Hz budget) = the server is the limit
SPIKE_GC_SHARE = 0.4             # this share of the spikes lined up with a GC pause = GC
CHUNK_REMESH_PER_S = 150.0       # section re-mesh requests per second above this, with spikes, = chunk loading
SPIKES_PER_MIN_NOTICEABLE = 3.0  # fewer spikes than this per minute is not what a player calls choppy
# Mirrors GfxLog.INTEGRATED_MARKERS in the mod (perf/GfxLog.java)
INTEGRATED = ("intel", "radeon(tm) graphics", "radeon(tm) 8", "radeon(tm) 7", "radeon(tm) 6", "radeon graphics",
              "890m", "880m", "780m", "760m", "740m", "680m", "660m", "vega 8", "vega 7", "vega 6", "vega 3",
              "uhd graphics", "iris xe", "iris plus", "iris pro", "apple m", "llvmpipe")

TS = re.compile(r"^\[(\d{1,2}\w{3}\d{4} \d\d:\d\d:\d\d(?:\.\d+)?)\]")
MSG = re.compile(r"^\[[^\]]*\] \[[^\]]*\] \[[^\]]*\]: (.*)$")
FRAME = re.compile(r"^\[Frame\] p50 ([\d.]+) ms \((\d+) fps\), p95 ([\d.]+) ms, max ([\d.]+) ms over (\d+) frames; (.*?) view, zoom (-?\d+), (\d+) units known")
TICK = re.compile(r"^\[Tick\] p50 ([\d.]+) ms, p95 ([\d.]+) ms, max ([\d.]+) ms over the last (\d+) ticks; (\d+) units")
SPIKE = re.compile(r"^\[Spike\] ([\d.]+) ms frame")
GC = re.compile(r"^\[GC\] pause (\d+) ms")
ROW = re.compile(r"(\w+(?:/\w+)?) ([\d.]+) \(max ([\d.]+), ")
REMESH = re.compile(r"([\d.]+) section re-mesh requests/s")
GFX = re.compile(r'^\[Gfx\] renderer "(.*?)", vendor "(.*?)"')


def parse_ts(line):
    m = TS.match(line)
    if not m:
        return None
    text = m.group(1)
    for fmt in ("%d%b%Y %H:%M:%S.%f", "%d%b%Y %H:%M:%S"):
        try:
            return datetime.strptime(text, fmt).timestamp()
        except ValueError:
            pass
    return None


def message(line):
    m = MSG.search(line)
    if m:
        return m.group(1).strip()
    return line.strip()


def pct(values, q):
    if not values:
        return 0.0
    v = sorted(values)
    return v[min(len(v) - 1, int(round((len(v) - 1) * q)))]


def analyze(lines):
    r = {"frames": [], "ticks": [], "spikes": [], "gcs": [], "parts": Counter(), "parts_n": 0, "sys": Counter(),
         "sys_n": 0, "remesh": [], "gfx": None, "first_ts": None, "last_ts": None, "tick_n": 0}
    n_lines = 0
    for line in lines:
        n_lines += 1
        ts = parse_ts(line)
        if ts is not None:
            r["first_ts"] = ts if r["first_ts"] is None else r["first_ts"]
            r["last_ts"] = ts
        msg = message(line)
        if not msg.startswith("["):
            continue
        m = FRAME.match(msg)
        if m:
            r["frames"].append({"p50": float(m.group(1)), "fps": int(m.group(2)), "p95": float(m.group(3)),
                                "max": float(m.group(4)), "n": int(m.group(5)), "units": int(m.group(8)), "ts": ts})
            continue
        m = TICK.match(msg)
        if m:
            r["ticks"].append({"p50": float(m.group(1)), "p95": float(m.group(2)), "max": float(m.group(3)), "ts": ts})
            continue
        m = SPIKE.match(msg)
        if m:
            r["spikes"].append({"ms": float(m.group(1)), "ts": ts, "gc": "GC pause" in msg, "text": msg})
            continue
        m = GC.match(msg)
        if m:
            r["gcs"].append({"ms": int(m.group(1)), "ts": ts})
            continue
        m = GFX.match(msg)
        if m:
            r["gfx"] = {"renderer": m.group(1), "vendor": m.group(2), "text": msg}
            continue
        if msg.startswith("[FrameParts]") and " frames, " in msg:
            r["parts_n"] += 1
            body = msg.split("):", 1)[1] if "):" in msg else msg
            body = body.split("; elsewhere")[0]
            for name, ms, _mx in ROW.findall(body):
                r["parts"][name] += float(ms)
            rm = REMESH.search(msg)
            if rm:
                r["remesh"].append(float(rm.group(1)))
            continue
        if msg.startswith("[Sys]") and " ticks, " in msg:
            r["sys_n"] += 1
            body = msg.split("):", 1)[1] if "):" in msg else msg
            body = body.split("; elsewhere")[0]
            for name, ms, _mx in ROW.findall(body):
                r["sys"][name] += float(ms)
    r["lines"] = n_lines
    # spikes that lined up with a [GC] line (same second) when the spike line itself did not say so
    for s in r["spikes"]:
        if not s["gc"] and s["ts"] is not None:
            s["gc"] = any(g["ts"] is not None and abs(g["ts"] - s["ts"]) <= 1.5 for g in r["gcs"])
    return r


def minutes(r):
    if r["first_ts"] is not None and r["last_ts"] is not None and r["last_ts"] > r["first_ts"]:
        return (r["last_ts"] - r["first_ts"]) / 60.0
    return max(len(r["frames"]), len(r["ticks"])) * 10.0 / 60.0   # a [Frame] line every 10 s


def is_integrated(renderer, vendor):
    s = ((renderer or "") + " " + (vendor or "")).lower()
    return any(m in s for m in INTEGRATED)


def verdict(r):
    mins = max(minutes(r), 1 / 6)
    spikes = r["spikes"]
    spm = len(spikes) / mins
    gc_share = (sum(1 for s in spikes if s["gc"]) / len(spikes)) if spikes else 0.0
    frame_p50 = pct([f["p50"] for f in r["frames"]], 0.5)
    tick_p95 = pct([t["p95"] for t in r["ticks"]], 0.5)
    tick_max_p95 = max([t["p95"] for t in r["ticks"]], default=0.0)
    remesh = pct(r["remesh"], 0.5)
    if not r["frames"] and not r["ticks"] and not spikes:
        return "no perf lines in this log (no [Frame]/[Tick]/[Spike]): was the game in a world long enough?"
    if spikes and spm >= SPIKES_PER_MIN_NOTICEABLE and gc_share >= SPIKE_GC_SHARE:
        return "GC: %d%% of the spikes line up with a garbage-collection pause (give the game more heap or find the allocation)" % round(100 * gc_share)
    if tick_max_p95 >= SLOW_TICK_P95_MS or tick_p95 >= SLOW_TICK_P95_MS * 0.7:
        return "server-bound: the server tick p95 is %.0f ms (budget 50 ms); the top [Sys] systems below say what it spends it on" % tick_max_p95
    if frame_p50 >= SLOW_FRAME_P50_MS:
        g = r["gfx"]
        why = " on an integrated GPU" if g and is_integrated(g["renderer"], g["vendor"]) else ""
        return "render-bound: the median frame is %.0f ms (%.0f fps) with a healthy server%s" % (frame_p50, 1000 / max(frame_p50, 0.1), why)
    if spikes and spm >= SPIKES_PER_MIN_NOTICEABLE and remesh >= CHUNK_REMESH_PER_S:
        return "chunk loading: %.1f spikes a minute, no GC, and %.0f section re-mesh requests a second" % (spm, remesh)
    if spikes and spm >= SPIKES_PER_MIN_NOTICEABLE:
        return "spikes without GC, a slow server or a slow median: %.1f a minute, most likely chunk loading or a one-off (saving, pause menu); look at the [Spike] lines' handlers" % spm
    return "nothing obviously wrong in this log: fps and server tick look healthy"


def trend(frames):
    if len(frames) < 3:
        return ""
    k = max(1, len(frames) // 3)
    parts = [frames[:k], frames[k:2 * k], frames[2 * k:]]
    return "start %.0f -> middle %.0f -> end %.0f fps (median of each third)" % tuple(
        pct([f["fps"] for f in p], 0.5) for p in parts[:3])


def top(counter, n, count):
    if not counter or not count:
        return "none logged"
    return ", ".join("%s %.2f ms" % (k, v / count) for k, v in counter.most_common(n))


def report(r):
    out = []
    mins = minutes(r)
    out.append("Lag report: %.1f min of log, %d lines" % (mins, r["lines"]))
    g = r["gfx"]
    if g:
        flag = "  <-- INTEGRATED GPU: expect a low frame rate at full detail" if is_integrated(g["renderer"], g["vendor"]) else ""
        out.append("GPU: %s | %s%s" % (g["renderer"], g["vendor"], flag))
        out.append("  " + g["text"])
    else:
        out.append("GPU: no [Gfx] line (an older build, or the player never joined a world)")
    fr = r["frames"]
    if fr:
        out.append("Frames: median %.0f fps (p50 %.1f ms); p95 frame %.1f ms (median of the 10 s windows), worst %.0f ms"
                   % (pct([f["fps"] for f in fr], 0.5), pct([f["p50"] for f in fr], 0.5),
                      pct([f["p95"] for f in fr], 0.5), max(f["max"] for f in fr)))
        t = trend(fr)
        if t:
            out.append("  fps trend: " + t)
        out.append("  units known: up to %d" % max(f["units"] for f in fr))
    else:
        out.append("Frames: no [Frame] lines")
    sp = r["spikes"]
    if sp:
        gcn = sum(1 for s in sp if s["gc"])
        out.append("Spikes (frames >= 50 ms, logged at most 1 a second): %d lines = %.1f a minute; %d with a GC pause in the same second; worst %.0f ms"
                   % (len(sp), len(sp) / max(mins, 1 / 6), gcn, max(s["ms"] for s in sp)))
    else:
        out.append("Spikes: none logged (or log_frame_spikes was off)")
    out.append("Our client handlers, ms per frame, averaged: " + top(r["parts"], 5, r["parts_n"]))
    if r["remesh"]:
        out.append("  section re-mesh requests: median %.0f/s, worst %.0f/s" % (pct(r["remesh"], 0.5), max(r["remesh"])))
    tk = r["ticks"]
    if tk:
        out.append("Server tick: p50 %.1f ms, p95 %.1f ms (median of windows), worst window p95 %.1f ms, worst tick %.0f ms"
                   % (pct([t["p50"] for t in tk], 0.5), pct([t["p95"] for t in tk], 0.5),
                      max(t["p95"] for t in tk), max(t["max"] for t in tk)))
    else:
        out.append("Server tick: no [Tick] lines (no units yet, or a client-only log)")
    out.append("Top server systems while slow ([Sys], ms per tick): " + top(r["sys"], 5, r["sys_n"]))
    gcs = r["gcs"]
    if gcs:
        out.append("GC pauses of 50 ms or more: %d, total %d ms, worst %d ms" % (len(gcs), sum(g_["ms"] for g_ in gcs), max(g_["ms"] for g_ in gcs)))
    else:
        out.append("GC pauses of 50 ms or more: none")
    out.append("")
    out.append("VERDICT: " + verdict(r))
    return "\n".join(out)


def main(argv):
    if len(argv) != 2 or argv[1] in ("-h", "--help"):
        sys.stdout.write(__doc__ + "\n")
        return 2
    with open(argv[1], errors="replace", encoding="utf-8") as fh:
        lines = fh.readlines()
    print(report(analyze(lines)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
