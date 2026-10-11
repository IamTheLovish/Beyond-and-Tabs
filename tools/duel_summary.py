#!/usr/bin/env python3
"""
Summarises the headless duel simulator's results (gametest/DuelGameTests writes bar/run-gametest/duel_results.json).

CI prints one ::notice:: annotation per tier (plus one overview line) - annotations are the only CI output readable
through the GitHub API here (`gh api repos/<repo>/check-runs/<job>/annotations`). `--markdown` prints the tables that
go into the "Duel results" section of design/balance.md instead.

Per matchup (both repetitions together): wins, each side's remaining metal value as a share of what it started with,
mean fight time and the winner's exchange ratio = enemy metal destroyed / own metal lost. A matchup is LOPSIDED when
the winner's exchange ratio is over 3x (at equal cost it lost under a third of what it killed); with only two
near-deterministic repetitions a win share is 0, 50 or 100%, so "won every repetition" alone flags nearly everything.

Never exits non-zero on results: this is a measurement. Exit 1 only if the file is missing or unreadable.
"""
import json, sys
from collections import OrderedDict

LOPSIDED_EXCHANGE = 3.0


def load(path):
    with open(path) as f:
        return json.load(f)


def aggregate(data):
    pairs = OrderedDict()
    for d in data['duels']:
        key = (d['tier'], d['a'], d['b'])
        pairs.setdefault(key, []).append(d)
    rows = []
    for (tier, a, b), runs in pairs.items():
        wa = sum(1 for r in runs if r['winner'] == 'A')
        wb = sum(1 for r in runs if r['winner'] == 'B')
        ma = sum(r['metalA'] for r in runs)
        mb = sum(r['metalB'] for r in runs)
        va = sum(r['valueA'] for r in runs)
        vb = sum(r['valueB'] for r in runs)
        keep_a, keep_b = va / max(1, ma), vb / max(1, mb)
        if wa != wb:
            win = 'A' if wa > wb else 'B'
        elif abs(keep_a - keep_b) >= 0.05:
            win = 'A' if keep_a > keep_b else 'B'
        else:
            win = '='
        if win == 'A':
            exch = (mb - vb) / max(1.0, ma - va)
        elif win == 'B':
            exch = (ma - va) / max(1.0, mb - vb)
        else:
            exch = 1.0
        rows.append(dict(tier=tier, a=a, b=b, groupA=runs[0]['groupA'], groupB=runs[0]['groupB'],
                         metalA=runs[0]['metalA'], metalB=runs[0]['metalB'], wa=wa, wb=wb, n=len(runs),
                         keepA=keep_a, keepB=keep_b, win=win, exch=exch,
                         secs=sum(r['seconds'] for r in runs) / len(runs),
                         timeouts=sum(1 for r in runs if r['timeout']),
                         lopsided=win != '=' and exch > LOPSIDED_EXCHANGE))
    return rows


def fmt_exch(x):
    return '>50x' if x > 50 else f'{x:.1f}x'


def short(r):
    w = r['a'] if r['win'] == 'A' else r['b'] if r['win'] == 'B' else 'even'
    return (f"{r['groupA']} ({r['metalA']}m) v {r['groupB']} ({r['metalB']}m): wins {r['wa']}-{r['wb']}"
            f"/{r['n']}, keep {r['keepA'] * 100:.0f}%/{r['keepB'] * 100:.0f}%, {w} {fmt_exch(r['exch'])}, "
            f"{r['secs']:.0f}s{' T' + str(r['timeouts']) if r['timeouts'] else ''}{' LOPSIDED' if r['lopsided'] else ''}")


def notices(data, rows):
    out = []
    flagged = [r for r in rows if r['lopsided']]
    out.append(f"[Duel] {len(data['duels'])} duels, {len(rows)} matchups, max {data.get('maxSeconds', '?')}s each; "
               f"wall {data.get('wallSeconds', '?')}s; lopsided (winner exchange > {LOPSIDED_EXCHANGE:.0f}x): {len(flagged)}; errors: {len(data.get('errors', []))}")
    for tier in OrderedDict((r['tier'], 1) for r in rows):
        # workflow-command escaping: a literal % is %25, a newline %0A
        body = '%0A'.join(short(r).replace('%', '%25') for r in rows if r['tier'] == tier)
        out.append(f"[Duel {tier}]%0A{body}")
    if data.get('errors'):
        out.append('[Duel errors] ' + ' | '.join(data['errors'])[:3000])
    return out


def markdown(rows):
    L = []
    for tier in OrderedDict((r['tier'], 1) for r in rows):
        L.append(f'### {tier}')
        L.append('')
        L.append('| A | B | Metal A:B | Wins A-B | Keep A | Keep B | Winner exchange | Time | Flag |')
        L.append('|---|---|--:|:-:|--:|--:|--:|--:|---|')
        for r in rows:
            if r['tier'] != tier:
                continue
            w = r['a'] if r['win'] == 'A' else r['b'] if r['win'] == 'B' else 'even'
            L.append(f"| {r['groupA']} | {r['groupB']} | {r['metalA']}:{r['metalB']} | {r['wa']}-{r['wb']} | "
                     f"{r['keepA'] * 100:.0f}% | {r['keepB'] * 100:.0f}% | {w} {fmt_exch(r['exch'])} | {r['secs']:.0f} s"
                     f"{' (time)' if r['timeouts'] else ''} | {'**lopsided**' if r['lopsided'] else ''} |")
        L.append('')
    return '\n'.join(L)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    path = args[0] if args else 'bar/run-gametest/duel_results.json'
    try:
        data = load(path)
    except Exception as e:
        print(f'::warning::[Duel] no duel results at {path}: {e}')
        return 1
    rows = aggregate(data)
    if '--markdown' in sys.argv:
        print(markdown(rows))
    else:
        for n in notices(data, rows):
            print('::notice::' + n)
    return 0


if __name__ == '__main__':
    sys.exit(main())
