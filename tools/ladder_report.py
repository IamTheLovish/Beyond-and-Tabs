#!/usr/bin/env python3
"""The aggregate step of the nightly bot ladder (.github/workflows/bot-ladder.yml, job `report`).

Reads the per-match result files the ladder tests wrote (gametest/LadderGameTests -> ladder/<id>.json, one artifact per
match) and writes ONE markdown report: win rate per tier pair and per faction pair, the pace summary, one row per match,
the [Stuck] events and everything that needs a look (draws by timeout, stalls, think errors, matches with no result).
The tables are plain GitHub markdown so the file can be appended to a Project doc as it is.

    python3 tools/ladder_report.py RESULTS_DIR --matrix matrix.json --date 2026-10-22 --run-url URL --sha SHA \
            --out bot-ladder-2026-10-22.md
    python3 tools/ladder_report.py --selftest

RESULTS_DIR is searched for *.json at any depth (download-artifact puts each artifact in its own folder). --matrix is the
planned list from tools/ladder_matrix.py; a planned match with no file is listed as "no result" (its runner died or the
test could not start). Side A is the higher tier of its match; at equal tiers the faction listed first.

Never exits non-zero on results (this is a measurement); exit 1 only when there is nothing to report at all.
"""
import argparse
import glob
import json
import os
import statistics
import sys
import tempfile


def clock(seconds):
    seconds = int(round(seconds))
    return f"{seconds // 60}:{seconds % 60:02d}"


def med(values):
    values = [v for v in values if v is not None and v >= 0]
    return statistics.median(values) if values else None


def fnum(v, digits=1):
    return "-" if v is None else f"{v:.{digits}f}"


def pct(n, d):
    return "-" if not d else f"{100 * n / d:.0f}%"


def load(root):
    results = {}
    for path in sorted(glob.glob(os.path.join(root, "**", "*.json"), recursive=True)):
        try:
            with open(path, encoding="utf-8") as f:
                r = json.load(f)
        except (OSError, ValueError) as e:
            print(f"::warning::[Ladder report] {path}: {e}", file=sys.stderr)
            continue
        if isinstance(r, dict) and "id" in r and "a" in r:
            results[r["id"]] = r
    return results


def length_s(r):
    return r["ticks"] / 20.0


def tier_pair(r):
    return (r["a"]["tier"], r["b"]["tier"])


def table(head, rows):
    out = ["| " + " | ".join(head) + " |", "|" + "|".join("---" for _ in head) + "|"]
    out += ["| " + " | ".join(str(c) for c in row) + " |" for row in rows]
    return out


def tally(rs):
    a = sum(1 for r in rs if r["outcome"] == "a")
    b = sum(1 for r in rs if r["outcome"] == "b")
    d = sum(1 for r in rs if r["outcome"] == "draw")
    return a, b, d


def report(results, planned, date, run_url="", sha=""):
    ok = [r for r in results.values() if r.get("outcome") in ("a", "b", "draw")]
    errors = [r for r in results.values() if r.get("outcome") == "error"]
    missing = [m for m in planned if m["id"] not in results]
    L = [f"# Bot ladder {date}", ""]
    facts = []
    if run_url:
        facts.append(f"[run]({run_url})")
    if sha:
        facts.append(f"commit `{sha[:7]}`")
    n_dec = sum(1 for r in ok if r["outcome"] != "draw")
    n_draw = len(ok) - n_dec
    facts.append(f"{len(planned) or len(results)} matches planned, {len(ok)} finished ({n_dec} decided, {n_draw} draws), "
                 f"{len(errors)} could not start, {len(missing)} left no result")
    L += [" - ".join(facts), ""]
    if not ok:
        L += ["No match finished.", ""]

    groups = {}
    for r in ok:
        groups.setdefault(tier_pair(r), []).append(r)

    # --- headline: slower fights, bigger armies (the owner's two asks of Oct 22)
    L += ["## Headline: how long units live, how big armies get", "",
          "The owner wants slower fights (units live long enough for their abilities to matter) and huge armies built over a match. "
          "Medians over the matches of each tier pair, A / B. Time to kill = seconds from the first enemy hit a fighter took to "
          "its death. Combat life = seconds from birth to death of fighters an enemy killed. Casts/life = ability casts per "
          "fighter that stood on the field (a cooldown that started, seen once a second). Peak army = highest army value "
          "(metal of fighters) and when. Army at min N = army value standing then ('-' = match already over).", ""]
    rows = []
    def nest(r, field, key, i):
        v = r.get(field, {}).get(key)
        return None if v is None else v[i]
    def two(rs, f, digits=1):
        return f"{fnum(med([r[f][0] for r in rs if f in r]), digits)} / {fnum(med([r[f][1] for r in rs if f in r]), digits)}"
    def at(rs, field, key):
        return f"{fnum(med([nest(r, field, key, 0) for r in rs]), 0)} / {fnum(med([nest(r, field, key, 1) for r in rs]), 0)}"
    buckets = list(groups.items()) + ([(("all", "all"), ok)] if len(groups) > 1 else [])
    for (ta, tb), rs in buckets:
        peak_t = [clock(med([r["peakArmyAtS"][i] for r in rs if "peakArmyAtS" in r])) if any("peakArmyAtS" in r for r in rs) else "-" for i in (0, 1)]
        rows.append((f"{ta} vs {tb}" if ta != "all" else "**all matches**", len(rs),
                     two(rs, "timeToKillS"), two(rs, "medianCombatLifetimeS", 0), two(rs, "castsPerLife", 2),
                     f"{two(rs, 'peakArmy', 0)} at {peak_t[0]} / {peak_t[1]}", two(rs, "peakFighters", 0),
                     at(rs, "armyAt", "10"), at(rs, "armyAt", "20"), at(rs, "armyAt", "30")))
    L += table(["tier pair", "matches", "time to kill (s)", "combat life (s)", "casts / unit-life", "peak army (metal) and when",
                "peak fighters", "army at min 10", "army at min 20", "army at min 30"], rows) if rows else ["-"]
    L.append("")

    # --- tier pairs
    L += ["## Win rate by tier pair", "",
          "A is the higher tier of a pair (at equal tiers, the faction listed first). A win % counts a draw as no win; a draw is a "
          "timeout, and \"ahead\" says whose army value was larger when time ran out.", ""]
    rows = []
    for (ta, tb), rs in groups.items():
        a, b, d = tally(rs)
        ahead_a = sum(1 for r in rs if r["outcome"] == "draw" and r["armyEnd"][0] > r["armyEnd"][1])
        ahead_b = sum(1 for r in rs if r["outcome"] == "draw" and r["armyEnd"][1] > r["armyEnd"][0])
        rows.append((ta, tb, len(rs), a, b, f"{d} (A ahead {ahead_a}, B ahead {ahead_b})", pct(a, len(rs)),
                     clock(med([length_s(r) for r in rs if r["outcome"] != "draw"]) or 0) if any(r["outcome"] != "draw" for r in rs) else "-"))
    L += table(["A tier", "B tier", "matches", "A wins", "B wins", "draws", "A win %", "median length (decided)"], rows)
    L.append("")

    # --- faction pairs (equal tiers only: a fair fight)
    L += ["## Win rate by faction pair", "",
          "Equal-tier matches only (a fair fight), all tiers together. First = the faction listed first alphabetically; a mirror "
          "shows the side bias (A wins vs B wins).", ""]
    fp = {}
    for r in ok:
        if r["a"]["tier"] != r["b"]["tier"]:
            continue
        fa, fb = r["a"]["faction"], r["b"]["faction"]
        first_is_a = fa <= fb
        key = (min(fa, fb), max(fa, fb))
        w = r["outcome"]
        won = "first" if (w == "a") == first_is_a and w != "draw" else ("second" if w != "draw" else "draw")
        fp.setdefault(key, []).append((won, r))
    rows = []
    for (f1, f2), items in sorted(fp.items()):
        n = len(items)
        w1 = sum(1 for w, _ in items if w == "first")
        w2 = sum(1 for w, _ in items if w == "second")
        dr = n - w1 - w2
        decided = [length_s(r) for w, r in items if w != "draw"]
        rows.append((f1 if f1 == f2 else f"{f1} vs {f2}", n, w1, w2, dr, pct(w1, n),
                     clock(med(decided)) if decided else "-"))
    L += table(["faction pair", "matches", "first wins", "second wins", "draws", "first win %", "median length"], rows) if rows else ["No equal-tier matches."]
    L.append("")

    # --- per faction, any match
    L += ["## Win rate by faction", "", "Every cross-faction match a faction played, either side, any tier pair. Cross-tier matches pull this "
          "toward whoever got the stronger bot, so read it next to the pair table.", ""]
    pf = {}
    for r in ok:
        if r["a"]["faction"] == r["b"]["faction"]:
            continue   # a mirror is one win and one loss for the same faction: it says nothing here
        for side, name in (("a", r["a"]["faction"]), ("b", r["b"]["faction"])):
            e = pf.setdefault(name, [0, 0, 0, 0])
            e[0] += 1
            if r["outcome"] == side:
                e[1] += 1
            elif r["outcome"] == "draw":
                e[3] += 1
            else:
                e[2] += 1
    L += table(["faction", "matches", "wins", "losses", "draws", "win %"],
               [(f, e[0], e[1], e[2], e[3], pct(e[1], e[0])) for f, e in sorted(pf.items())]) if pf else ["-"]
    L.append("")

    # --- pace
    L += ["## Pace", "",
          "Medians over the finished matches of each tier pair. Produced = units per game minute per side (A / B). Combat life "
          "= seconds from birth to death of fighters an enemy killed. Fighting = share of the match with units hitting each "
          "other (10 s buckets). Decisive = a side lost 40% or more of its army value within 30 s; gap = time between "
          "decisive fights. Alive at min 10 = units standing at game minute 10 (A / B).", ""]
    rows = []
    for (ta, tb), rs in groups.items():
        def side(field, i):
            return med([r[field][i] for r in rs if field in r])
        gaps = [g for r in rs for g in r.get("fightGapsS", [])]
        alive10 = [(r["aliveAt"]["10"][0], r["aliveAt"]["10"][1]) for r in rs if r.get("aliveAt", {}).get("10", [-1])[0] >= 0]
        rows.append((f"{ta} vs {tb}", len(rs),
                     f"{fnum(side('producedPerMin', 0))} / {fnum(side('producedPerMin', 1))}",
                     f"{fnum(side('medianCombatLifetimeS', 0), 0)} / {fnum(side('medianCombatLifetimeS', 1), 0)} s",
                     pct(med([r['fightingShare'] for r in rs]) or 0, 1),
                     fnum(med([len(r.get('decisiveFights', [])) for r in rs]), 0),
                     clock(med(gaps)) if gaps else "-",
                     f"{fnum(med([a for a, _ in alive10]), 0)} / {fnum(med([b for _, b in alive10]), 0)}" if alive10 else "-"))
    L += table(["tier pair", "matches", "produced/min A / B", "combat life A / B", "fighting", "decisive fights", "median gap", "alive at min 10 A / B"], rows) if rows else ["-"]
    L.append("")

    # --- one row per match
    L += ["## Matches", "", "A is listed first. Army = peak army value (metal) of A / B; lost = fighter value each side lost; army at end = what was standing when the match stopped.", ""]
    rows = []
    for r in sorted(ok, key=lambda r: (tier_pair(r), r["a"]["faction"], r["b"]["faction"])):
        res = {"a": f"A wins {r['clock']}", "b": f"B wins {r['clock']}"}.get(r["outcome"], f"draw ({r['reason']}) {r['clock']}")
        rows.append((f"{r['a']['label']} vs {r['b']['label']}", res,
                     f"{int(r['peakArmy'][0])} / {int(r['peakArmy'][1])}", f"{int(r['lost'][0])} / {int(r['lost'][1])}", f"{int(r['armyEnd'][0])} / {int(r['armyEnd'][1])}",
                     len(r.get("decisiveFights", [])), pct(r.get("fightingShare", 0), 1), f"{r.get('simSpeed', 0)}x"))
    L += table(["match", "result", "peak army", "lost", "army at end", "decisive fights", "fighting", "sim speed"], rows) if rows else ["-"]
    L.append("")

    # --- stuck
    stuck_total = int(sum(sum(r.get("stuck", {}).get("count", [0, 0])) for r in ok))
    give_ups = int(sum(sum(r.get("stuck", {}).get("giveUps", [0, 0])) for r in ok))
    L += ["## Stuck", "", f"{stuck_total} stuck events in {len(ok)} matches, {give_ups} of them gave up their target "
          "(unit/StuckNudge; one event per unit per 10 s). Per match the most repeated spots, with position (x, y, z, 4-block cells):", ""]
    shown = 0
    for r in ok:
        for e in r.get("stuck", {}).get("events", [])[:4]:
            if shown < 60:
                L.append(f"- `{r['a']['label']} vs {r['b']['label']}` {e}")
                shown += 1
    if not shown:
        L.append("- none")
    L.append("")

    # --- needs a look
    look = []
    for r in ok:
        name = f"{r['a']['label']} vs {r['b']['label']}"
        if r["outcome"] == "draw":
            look.append(f"- {name}: draw by {r['reason']} at {r['clock']}, army {int(r['armyEnd'][0])} vs {int(r['armyEnd'][1])}, "
                        f"units {int(r['unitsEnd'][0])} vs {int(r['unitsEnd'][1])}, bots {'/'.join(r.get('botState', []))}")
        for s in r.get("stalls", []):
            look.append(f"- {name}: stall - {s}")
        for e in r.get("errors", []):
            look.append(f"- {name}: error - {e}")
        if r.get("simSpeed", 9) and r.get("simSpeed", 9) < 1:
            look.append(f"- {name}: ran slower than real time ({r['simSpeed']}x)")
    for r in errors:
        look.append(f"- {r['a']['label']} vs {r['b']['label']}: could not start - {r.get('reason', '?')}")
    for m in missing:
        look.append(f"- {m['label']}: no result (runner failed or timed out)")
    L += ["## Needs a look", ""] + (look or ["- nothing"]) + [""]
    return "\n".join(L)


def selftest():
    def res(i, ta, fa, tb, fb, outcome, secs, reason="victory"):
        return {"id": i, "a": {"tier": ta, "faction": fa, "label": f"{ta}({fa})"}, "b": {"tier": tb, "faction": fb, "label": f"{tb}({fb})"},
                "outcome": outcome, "reason": reason, "ticks": secs * 20, "clock": clock(secs), "peakArmy": [3400, 2100],
                "lost": [2100, 3900], "armyEnd": [1000, 0], "unitsEnd": [40, 3], "producedPerMin": [9.1, 7.4],
                "medianCombatLifetimeS": [41, 55], "timeToKillS": [12.5, 9], "castsPerLife": [1.5, 0.8],
                "peakFighters": [60, 45], "peakArmyAtS": [800, 700], "armyAt": {"10": [2500, 1800], "20": [-1, -1], "30": [-1, -1]}, "fightingShare": 0.38, "decisiveFights": [{}, {}], "fightGapsS": [155],
                "aliveAt": {"10": [61, 48], "20": [-1, -1], "30": [-1, -1]}, "simSpeed": 3.1, "botState": ["ATTACK", "DEFEND"],
                "stuck": {"count": [2, 1], "giveUps": [1, 0], "events": ["hard(villagers) zombie_unit near 1, 2, 3 x3 (first 4:10, last 4:30, worst stage 2, last approach hold)"]},
                "stalls": [], "errors": []}
    with tempfile.TemporaryDirectory() as d:
        rs = [res("m1", "hard", "villagers", "medium", "monsters", "a", 700),
              res("m2", "hard", "villagers", "medium", "monsters", "b", 650),
              res("m3", "medium", "monsters", "medium", "villagers", "draw", 900, "timeout"),
              res("m4", "hard", "monsters", "hard", "monsters", "a", 500)]
        for r in rs:
            os.makedirs(os.path.join(d, "ladder-" + r["id"]))
            json.dump(r, open(os.path.join(d, "ladder-" + r["id"], r["id"] + ".json"), "w"))
        err = {"id": "m5", "a": rs[0]["a"], "b": rs[0]["b"], "outcome": "error", "reason": "no dry ground"}
        json.dump(err, open(os.path.join(d, "m5.json"), "w"))
        planned = [{"id": r["id"], "label": r["id"]} for r in rs] + [{"id": "m5", "label": "m5"}, {"id": "m6", "label": "gone"}]
        text = report(load(d), planned, "2026-10-22", "http://run", "abcdef0123")
    for want in ["# Bot ladder 2026-10-22", "## Headline", "| hard vs medium | 2 | 12.5 / 9.0 | 41 / 55 | 1.50 / 0.80 | 3400 / 2100 at 13:20 / 11:40 | 60 / 45 | 2500 / 1800 | - / - | - / - |", "| hard | medium | 2 | 1 | 1 | 0 (A ahead 0, B ahead 0) | 50% |", "| medium | medium | 1 | 0 | 0 | 1 (A ahead 1, B ahead 0) | 0% | - |",
                 "monsters vs villagers", "draw (timeout) 15:00", "gone: no result", "could not start", "12 stuck events"]:
        if want not in text:
            print(text)
            sys.exit(f"selftest: missing {want!r}")
    print("ladder_report selftest ok")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("results", nargs="?", default="")
    ap.add_argument("--matrix", default="")
    ap.add_argument("--date", default="")
    ap.add_argument("--run-url", default="")
    ap.add_argument("--sha", default="")
    ap.add_argument("--out", default="")
    ap.add_argument("--selftest", action="store_true")
    a = ap.parse_args()
    if a.selftest:
        return selftest()
    if not a.results:
        ap.error("RESULTS_DIR is required")
    results = load(a.results)
    planned = json.load(open(a.matrix)) if a.matrix and os.path.exists(a.matrix) else []
    if not results and not planned:
        print("::error::[Ladder report] no results and no plan", file=sys.stderr)
        return 1
    text = report(results, planned, a.date or "undated", a.run_url, a.sha)
    if a.out:
        open(a.out, "w", encoding="utf-8").write(text + "\n")
    print(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
