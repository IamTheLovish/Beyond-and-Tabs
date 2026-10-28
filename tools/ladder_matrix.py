#!/usr/bin/env python3
"""The match list of the nightly bot ladder (.github/workflows/bot-ladder.yml, job `plan`).

A match is two sides, each `tier:faction` (BAT_LADDER_MATCH=hard:villagers|medium:monsters, read by
gametest/LadderGameTests). Side A is the higher tier; at equal tiers it is the faction listed first.

  TIERS    read from `enum Difficulty` in bot/BotPlayer.java, weakest first. A new tier (Brutal) is ONE new enum value
           there (plus its BotTuning row) and the matrix grows by itself - nothing in this file or the workflow changes.
  FACTIONS the factions a bot can play (the /bot add names). A new bot faction is one new line here and in
           LadderGameTests.parseSide.

Modes:
  quick    3 matches (the cheap proof the pipeline works);
  nightly  TARGET (40) matches spread over every tier pair (two mirrors, the rest cross pairs per tier pair), rotated by
           the date so the whole faction-pair space is covered over a few nights; 40 = two waves of 20 runners;
  full     every tier pair x every faction pair (231 matches with 3 tiers; GitHub's matrix limit is 256, so more tiers
           than that are cut and said so).
  list     an explicit list from the dispatch input, ';'-separated `tier:faction|tier:faction` entries.

Prints the matrix as JSON (a list of {id, spec, label}) and, with GITHUB_OUTPUT set, writes it as `matrix` / `count`.

    python3 tools/ladder_matrix.py --mode nightly --date 2026-10-22
"""
import argparse
import datetime
import itertools
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BOT_PLAYER = os.path.join(ROOT, "bar/src/main/java/com/solegendary/reignofnether/bot/BotPlayer.java")

FACTIONS = ["villagers", "monsters", "piglins", "verdant_court", "tidewrought", "stormcallers", "brassworks"]
TARGET = 40            # nightly matches in all, spread over the tier pairs: two waves of 20 runners
MATRIX_LIMIT = 256     # GitHub's cap on jobs from one matrix


def tiers():
    src = open(BOT_PLAYER, encoding="utf-8").read()
    m = re.search(r"enum\s+Difficulty\s*\{([^}]*)\}", src)
    if not m:
        sys.exit("ladder_matrix: no `enum Difficulty` in BotPlayer.java")
    found = [t.strip().lower() for t in m.group(1).split(",") if t.strip()]
    if not found:
        sys.exit("ladder_matrix: empty Difficulty enum")
    return found


def tier_pairs(ts):
    """(higher, lower) for every unordered pair of tiers, mirrors included, weakest pair first."""
    return [(ts[j], ts[i]) for i in range(len(ts)) for j in range(i, len(ts))]


def faction_pairs(hi, lo):
    """The faction pairings of one tier pair as (faction of A, faction of B): mirrors first, then the cross pairs.
    At equal tiers (a, b) and (b, a) are the same match; at different tiers they are not (who has the stronger bot)."""
    mirrors = [(f, f) for f in FACTIONS]
    if hi == lo:
        cross = list(itertools.combinations(FACTIONS, 2))
    else:
        cross = [(a, b) for a in FACTIONS for b in FACTIONS if a != b]
    return mirrors, cross


def match(hi, lo, fa, fb):
    spec = f"{hi}:{fa}|{lo}:{fb}"
    mid = f"{hi}-{fa}_v_{lo}-{fb}"
    return {"id": mid, "spec": spec, "label": f"{hi}({fa}) vs {lo}({fb})"}


def full(ts):
    out = []
    for hi, lo in tier_pairs(ts):
        mirrors, cross = faction_pairs(hi, lo)
        out += [match(hi, lo, a, b) for a, b in mirrors + cross]
    return out


def nightly(ts, day, target=TARGET):
    """`target` matches over all tier pairs (the first pairs get the remainder): two mirrors and the rest cross pairs
    each, windows that move with the day number so consecutive nights cover different faction pairs."""
    out = []
    pairs = tier_pairs(ts)
    for n, (hi, lo) in enumerate(pairs):
        each = target // len(pairs) + (1 if n < target % len(pairs) else 0)
        mirrors, cross = faction_pairs(hi, lo)
        n_mirror = min(2, each)
        picks = [mirrors[(day * n_mirror + k) % len(mirrors)] for k in range(n_mirror)]
        n_cross = each - n_mirror
        picks += [cross[(day * n_cross * 5 + k * 5 + n * 3) % len(cross)] for k in range(n_cross)]
        out += [match(hi, lo, a, b) for a, b in picks]
    return out


def quick(ts):
    """Three matches that touch a mirror, a cross pair and two tiers; the strongest tier plays the first."""
    top, mid, low = ts[-1], ts[len(ts) // 2], ts[0]
    return [match(mid, mid, "villagers", "villagers"),
            match(top, mid, "villagers", "monsters"),
            match(mid, low, "monsters", "villagers")]


def from_list(text, ts):
    out = []
    for entry in filter(None, (e.strip() for e in text.split(";"))):
        m = re.fullmatch(r"(\w+):(\w+)\|(\w+):(\w+)", entry)
        if not m or m.group(1) not in ts or m.group(3) not in ts or m.group(2) not in FACTIONS or m.group(4) not in FACTIONS:
            sys.exit(f"ladder_matrix: cannot read '{entry}' (tiers {ts}, factions {FACTIONS})")
        out.append(match(*m.groups()[0:1], m.group(3), m.group(2), m.group(4)))
    return out


def build(mode, ts, day, listing="", target=TARGET):
    if mode == "quick":
        return quick(ts)
    if mode == "full":
        return full(ts)
    if mode == "list":
        return from_list(listing, ts)
    return nightly(ts, day, target)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--mode", default="nightly", choices=["quick", "nightly", "full", "list"])
    ap.add_argument("--date", default="", help="YYYY-MM-DD (default today, UTC) - the nightly rotation")
    ap.add_argument("--list", default="", help="mode list: 'tier:faction|tier:faction;...'")
    ap.add_argument("--target", type=int, default=TARGET, help="nightly: matches in all")
    args = ap.parse_args()
    day = (datetime.date.fromisoformat(args.date) if args.date else datetime.datetime.now(datetime.timezone.utc).date()).toordinal()
    ts = tiers()
    ms = build(args.mode, ts, day, args.list, args.target)
    seen, uniq = set(), []
    for m in ms:
        if m["id"] not in seen:
            seen.add(m["id"])
            uniq.append(m)
    if len(uniq) > MATRIX_LIMIT:
        print(f"::warning::[Ladder] {len(uniq)} matches, GitHub runs {MATRIX_LIMIT} per matrix - the first {MATRIX_LIMIT} only", file=sys.stderr)
        uniq = uniq[:MATRIX_LIMIT]
    print(f"[ladder_matrix] mode {args.mode}: {len(uniq)} matches, tiers {ts}", file=sys.stderr)
    text = json.dumps(uniq)
    print(text)
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as f:
            f.write(f"matrix={text}\ncount={len(uniq)}\n")
    return 0 if uniq else 1


if __name__ == "__main__":
    sys.exit(main())
