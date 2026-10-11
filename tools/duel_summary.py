#!/usr/bin/env python3
"""
Summarises the headless duel simulator's results (gametest/DuelGameTests writes bar/run-gametest/duel_results.json).

CI prints one ::notice:: annotation per tier (plus one overview line) - annotations are the only CI output readable
through the GitHub API here (`gh api repos/<repo>/check-runs/<job>/annotations`). `--markdown` prints the tables that
go into the "Duel results" section of design/balance.md instead; `--from-notices <file>` builds the same tables from
the annotation text saved off the API (the JSON never leaves the runner).

Per matchup (both repetitions together): wins, each side's remaining metal value as a share of what it started with,
mean fight time and the winner's exchange ratio = enemy metal destroyed / own metal lost. A matchup is LOPSIDED when
the winner's exchange ratio is over 3x (at equal cost it lost under a third of what it killed); with only two
near-deterministic repetitions a win share is 0, 50 or 100%, so "won every repetition" alone flags nearly everything.

Never exits non-zero on results: this is a measurement. Exit 1 only if the file is missing or unreadable.
"""
import json, re, sys
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


NOTICE_ROW = re.compile(r'^(?P<ga>.+?) \((?P<ma>\d+)m\) v (?P<gb>.+?) \((?P<mb>\d+)m\): wins (?P<wa>\d+)-(?P<wb>\d+)/(?P<n>\d+), '
                        r'keep (?P<ka>\d+)%/(?P<kb>\d+)%, (?P<w>.+?) (?P<ex>>?[\d.]+)x, (?P<s>\d+)s(?: T(?P<t>\d+))?(?P<lop> LOPSIDED)?$')


def rows_from_notices(text):
    """Rebuilds the matchup rows from the CI annotation text (gh api .../check-runs/<job>/annotations), since the
    JSON file itself never leaves the CI runner."""
    rows, tier = [], None
    for line in text.splitlines():
        line = line.strip()
        m = re.match(r'^\[Duel (T\d)\]', line)
        if m:
            tier = m.group(1)
            continue
        m = NOTICE_ROW.match(line)
        if not m or not tier:
            continue
        a, b = m['ga'].split(' ', 1)[1].split('+')[0], m['gb'].split(' ', 1)[1].split('+')[0]
        win = '=' if m['w'] == 'even' else 'A' if m['w'] == a or m['w'] == m['ga'] else 'B'
        rows.append(dict(tier=tier, a=a, b=b, groupA=m['ga'], groupB=m['gb'], metalA=int(m['ma']), metalB=int(m['mb']),
                         wa=int(m['wa']), wb=int(m['wb']), n=int(m['n']), keepA=int(m['ka']) / 100, keepB=int(m['kb']) / 100,
                         win=win, exch=99.0 if m['ex'].startswith('>') else float(m['ex']), secs=float(m['s']),
                         timeouts=int(m['t'] or 0), lopsided=bool(m['lop'])))
        if win == 'A':
            rows[-1]['a'] = m['w']
        elif win == 'B':
            rows[-1]['b'] = m['w']
    return rows


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    path = args[0] if args else 'bar/run-gametest/duel_results.json'
    if '--from-notices' in sys.argv:   # annotation text in, markdown out
        print(markdown(rows_from_notices(open(path).read())))
        return 0
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
