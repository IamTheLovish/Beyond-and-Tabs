#!/usr/bin/env python3
"""
What happened in this latest.log? A timeline and a plain verdict, for a tester who says "it was stuck".

    python3 tools/diagnose_log.py <latest.log>          (or the latest.log inside a bat-report-*.zip, unzipped)

Reads the lines from design/diagnostics.md: [Screen], [Gfx] (and the Forge / Minecraft / Oculus lines around them).
Prints: versions (launcher, Java, mod build), -Xmx and GPU, the shader pack and its error count, world create / join,
the lobby and its e4mc domain, every [Screen] line, errors (deduplicated), the last events before "Stopping!", and a
one-paragraph verdict. Standard library only. Companion of tools/lag_report.py (that one answers "why choppy").
"""
import re
import sys
from datetime import datetime

LAST_EVENTS = 15
SKIP_TAGS = ("[Frame]", "[FrameParts]", "[Tick]", "[Paths]", "[Sys]", "[GC]", "[Anim]", "[Spike]", "[FrameAuto]", "[PerfAuto]")

LINE = re.compile(r"^\[(?:(\d{1,2}\w{3}\d{4}) )?(\d\d:\d\d:\d\d)(?:\.\d+)?\] \[([^\]/]*)/(\w+)\] \[([^\]]*)\]: (.*)$")
SHADER_ERR = re.compile(r"Unknown variable|GL_INVALID_|GL ERROR|Failed to compile|Failed to link|shader.*(?:error|exception)", re.I)
E4MC = re.compile(r"([a-z0-9-]+(?:\.[a-z0-9-]+)*\.e4mc\.link)", re.I)
XMX = re.compile(r"-Xmx(\d+)([MmGgKk])")
MOD_JAR = re.compile(r"((?:bat|beyond[-_ ]?and[-_ ]?tabs|reignofnether)[\w.\-+]*?\d[\w.\-+]*)\.jar", re.I)
FORGE = re.compile(r"forge[-_ ](\d+\.\d+(?:\.\d+)?)-(\d+\.\d+\.\d+)", re.I)
JVM = re.compile(r"JVM identified as (.+)$|Java is (.+?), version ([\w.+]+)|^Java: ([\w.+]+)")
GFX_GPU = re.compile(r'\[Gfx\] renderer "(.*?)", vendor "(.*?)"(.*?); window (\d+x\d+)')
GFX_MEM = re.compile(r"\[Gfx\] (Xmx [^;]*)")
GFX_PACK = re.compile(r"shader pack detail: (.*)")
JOINED = re.compile(r"^(\S+) joined the game$")
LEFT = re.compile(r"^(\S+) left the game$")


class Event:
    __slots__ = ("ts", "thread", "level", "logger", "msg", "extra")

    def __init__(self, ts, thread, level, logger, msg):
        self.ts, self.thread, self.level, self.logger, self.msg, self.extra = ts, thread, level, logger, msg, []

    def clock(self):
        return self.ts.strftime("%H:%M:%S") if self.ts else "--:--:--"


def parse(text):
    """Lines to Events; a line with no timestamp (a stack trace) is attached to the one before it."""
    events = []
    raw_head = []
    for raw in text.splitlines():
        m = LINE.match(raw)
        if m:
            try:
                if m.group(1):
                    ts = datetime.strptime(m.group(1) + " " + m.group(2), "%d%b%Y %H:%M:%S")
                else:   # dev runs print the time only
                    ts = datetime.strptime("01Jan2000 " + m.group(2), "%d%b%Y %H:%M:%S")
            except ValueError:
                ts = None
            events.append(Event(ts, m.group(3), m.group(4), m.group(5), m.group(6).strip()))
        elif events:
            events[-1].extra.append(raw.strip())
        else:
            raw_head.append(raw.strip())
    return events, raw_head


def mb(num, unit):
    return int(num) * {"k": 1 / 1024, "m": 1, "g": 1024}[unit.lower()]


def find_versions(events, head, text):
    v = {}
    low = text.lower()
    v["launcher"] = "Prism Launcher" if "prism" in low else "(not shown in the log)"
    for e in events[:400]:
        j = JVM.search(e.msg)
        if j and "java" not in v:
            v["java"] = " ".join(g for g in j.groups() if g)
        f = FORGE.search(e.msg)
        if f and "forge" not in v:
            v["forge"] = "Minecraft %s, Forge %s" % (f.group(1), f.group(2))
    m = MOD_JAR.search(text)
    v["mod"] = m.group(1) if m else "(mod jar not named in the log)"
    return v


def analyse(text):
    events, head = parse(text)
    r = {"events": events}
    r["versions"] = find_versions(events, head, text)
    xm = XMX.search(text)
    r["xmx_arg_mb"] = mb(xm.group(1), xm.group(2)) if xm else None
    r["gpu"] = r["gpu_flags"] = r["window"] = r["memory"] = r["pack_detail"] = None
    r["stop"] = None
    r["world_created"] = r["world_loaded"] = r["lan"] = r["domain"] = None
    r["first_screen"] = None
    r["screens"] = []
    r["shader_errs"] = []
    r["errors"] = {}
    r["joins"] = []
    r["leaves"] = []
    for e in events:
        m = e.msg
        g = GFX_GPU.search(m)
        if g:
            r["gpu"] = "%s (%s)" % (g.group(1), g.group(2))
            r["gpu_flags"] = g.group(3).strip(" ()") or None
            r["window"] = g.group(4)
        g = GFX_MEM.search(m)
        if g:
            r["memory"] = g.group(1)
        g = GFX_PACK.search(m)
        if g:
            r["pack_detail"] = g.group(1)
        if "[Screen]" in m:
            r["screens"].append(e)
        own_count = m.startswith("[Gfx] shader/GL errors in the last")
        if own_count or (not m.startswith(("[Gfx]", "[Screen]")) and SHADER_ERR.search(m) and (e.level in ("ERROR", "WARN", "FATAL") or "GL" in m or "iris" in e.logger.lower() or "oculus" in e.logger.lower())):
            r["shader_errs"].append(e)
        elif e.level in ("ERROR", "FATAL"):
            key = re.sub(r"\d+", "N", m)[:140]
            c = r["errors"].setdefault(key, [0, e, m[:200]])
            c[0] += 1
        if "Starting integrated minecraft server" in m and not r["world_created"]:
            r["world_created"] = e
        if re.match(r"Done \(", m) and "For help" in m and not r["world_loaded"]:
            r["world_loaded"] = e
        if ("Local game hosted on" in m or "Started serving on" in m) and not r["lan"]:
            r["lan"] = e
        if "opened the multiplayer lobby" in m and not r.get("lobby_open"):
            r["lobby_open"] = e
        d = E4MC.search(m)
        # broker./natives. are e4mc's own service hosts, not the join address
        if d and not r["domain"] and not d.group(1).lower().startswith(("broker.", "natives.")):
            r["domain"] = d.group(1)
        j = JOINED.match(m)
        if j:
            r["joins"].append(e)
            # an integrated server never prints vanilla's "Done (": the host joining means the world loaded
            if not r["world_loaded"]:
                r["world_loaded"] = e
        if LEFT.match(m):
            r["leaves"].append(e)
        if m.startswith("Stopping!") or m == "Stopping server":
            if not r["stop"]:
                r["stop"] = e
    return r


def screen_name(e):
    """'MultiplayerLobbyScreen "Lobby" still open ...' -> 'MultiplayerLobbyScreen'."""
    body = e.msg.split("[Screen]", 1)[1].strip()
    return body.split(" ", 1)[0].split(";", 1)[0]


def lobby_span(r):
    """(first, last) seconds spent on a lobby screen, from its [Screen] lines to the next screen or the stop."""
    start = end = None
    for i, e in enumerate(r["screens"]):
        if "Lobby" in screen_name(e) and start is None and e.ts:
            start = e
            nxt = None
            for e2 in r["screens"][i + 1:]:
                if "Lobby" not in screen_name(e2) and e2.ts:
                    nxt = e2
                    break
            end = nxt or r["stop"] or r["events"][-1]
    if start is None and r.get("lobby_open"):
        # older builds have no [Screen] lines: the lobby log line to the next screen-changing event or the stop
        start = r["lobby_open"]
        end = next((e for e in r["events"] if e.ts and e.ts > start.ts and "ENABLE_ORTHOVIEW" in e.msg), None) \
            or r["stop"] or r["events"][-1]
    if start and end and start.ts and end.ts:
        return start, end, int((end.ts - start.ts).total_seconds())
    return None


def verdict(r):
    parts = []
    ev = r["events"]
    if not ev:
        return "The log has no readable lines."
    if r["world_loaded"]:
        parts.append("world loaded at %s" % r["world_loaded"].clock())
    elif r["world_created"]:
        pcts = [re.search(r"Preparing spawn area: (\d+)%", e.msg) for e in ev]
        pcts = [m.group(1) for m in pcts if m]
        parts.append("a world started (%s) but never reported 'Done'%s" % (
            r["world_created"].clock(), (" (spawn area stopped at %s%%)" % pcts[-1]) if pcts else ""))
    else:
        parts.append("no world was ever created or loaded")
    if r["lan"]:
        parts.append("opened to LAN at %s%s" % (r["lan"].clock(), (" (" + r["domain"] + ")") if r["domain"] else ""))
    span = lobby_span(r)
    if span:
        start, end, sec = span
        others = [j for j in r["joins"][1:] if j.ts and j.ts >= start.ts]   # the first join is the host
        parts.append("sat in the lobby %d s with %d joins" % (sec, len(others)))
    if r["shader_errs"]:
        first = r["shader_errs"][0]
        pack = ""
        if r["pack_detail"]:
            m = re.search(r'"([^"]+)"', r["pack_detail"])
            if m:
                pack = " (pack %s)" % m.group(1)
        parts.append("the shader pack was broken from %s%s: %d shader/GL errors, the picture may have frozen"
                     % (first.clock(), pack, len(r["shader_errs"])))
    if r["memory"] and "TOO HIGH" in r["memory"]:
        parts.append("Java was given 75%% or more of the RAM (%s)" % r["memory"].split(" - ")[0])
    elif r["xmx_arg_mb"] is None and not r["memory"]:
        parts.append("the memory setting is not in the log")
    if r["gpu_flags"]:
        parts.append("the GPU is integrated")
    if r["screens"]:
        last = r["screens"][-1]
        body = last.msg.split("[Screen]", 1)[1].strip()
        parts.append("the last screen was %s" % body.split(";", 1)[0])
        if "waiting for:" in body:
            parts.append("it was waiting for %s" % body.split("waiting for:", 1)[1].strip())
    else:
        parts.append("the log has no [Screen] lines (an older build, or the client never ticked)")
    if r["stop"]:
        parts.append("the game stopped at %s" % r["stop"].clock())
    else:
        parts.append("there is no 'Stopping!' line (crash or killed)")
    s = "; ".join(parts)
    return s[:1].upper() + s[1:] + "."


def last_events(r):
    cut = r["stop"]
    ev = r["events"]
    end = ev.index(cut) if cut in ev else len(ev)
    out = [e for e in ev[:end] if not any(t in e.msg for t in SKIP_TAGS)]
    return out[-LAST_EVENTS:]


def report(text):
    r = analyse(text)
    out = []
    add = out.append
    add("== Versions ==")
    for k, label in (("launcher", "Launcher"), ("java", "Java"), ("forge", "Game"), ("mod", "Mod build")):
        add("  %-10s %s" % (label, r["versions"].get(k, "(not in the log)")))
    add("== Memory and graphics ==")
    add("  Xmx        %s" % (r["memory"] or ("-Xmx%d MB (from the launch arguments)" % r["xmx_arg_mb"] if r["xmx_arg_mb"] else "(not in the log)")))
    add("  GPU        %s%s" % (r["gpu"] or "(no [Gfx] line)", (" - " + r["gpu_flags"]) if r["gpu_flags"] else ""))
    if r["window"]:
        add("  Window     %s" % r["window"])
    add("== Shader pack ==")
    add("  %s" % (r["pack_detail"] or "(no shader detail in the log)"))
    if r["shader_errs"]:
        add("  %d shader/GL errors, first at %s, last at %s" % (len(r["shader_errs"]), r["shader_errs"][0].clock(), r["shader_errs"][-1].clock()))
        add("  first: %s" % r["shader_errs"][0].msg[:160])
    else:
        add("  no shader/GL errors")
    add("== World and lobby ==")
    add("  world created %s, loaded %s" % (r["world_created"].clock() if r["world_created"] else "-", r["world_loaded"].clock() if r["world_loaded"] else "-"))
    add("  lobby/LAN opened %s, e4mc domain %s" % (r["lan"].clock() if r["lan"] else "-", r["domain"] or "-"))
    add("  players joined: %d, left: %d" % (len(r["joins"]), len(r["leaves"])))
    add("== [Screen] timeline (%d lines) ==" % len(r["screens"]))
    for e in r["screens"]:
        add("  %s %s" % (e.clock(), e.msg.split("[Screen]", 1)[1].strip()))
    add("== Errors (deduplicated) ==")
    if not r["errors"]:
        add("  none")
    for n, e, m in sorted(r["errors"].values(), key=lambda x: x[1].ts or datetime.min):
        add("  %s x%d [%s] %s" % (e.clock(), n, e.logger, m))
    add("== Last %d events before Stopping! ==" % LAST_EVENTS)
    for e in last_events(r):
        add("  %s %s/%s %s" % (e.clock(), e.thread, e.level, e.msg[:160]))
    add("")
    add("VERDICT: " + verdict(r))
    return "\n".join(out)


def main(argv):
    if len(argv) != 2:
        print(__doc__)
        return 2
    with open(argv[1], encoding="utf-8", errors="replace") as f:
        print(report(f.read()))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
