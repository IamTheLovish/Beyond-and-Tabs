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
import glob, json, os, re, sys
from collections import OrderedDict

LOPSIDED_EXCHANGE = 3.0


NOTICE_CHARS = 3800   # GitHub cuts an annotation message at 4096 characters: the T1 matrix alone is longer
MIX_SPREAD = 1.5      # cross-faction best-T1-mix fights: no faction should win by more than this exchange


def load(path):
    """The main matrix file plus any sibling duel_results_*.json (the counter batch writes its own file)."""
    with open(path) as f:
        data = json.load(f)
    base = os.path.splitext(path)[0]
    for extra in sorted(glob.glob(base + '_*.json')):
        try:
            with open(extra) as f:
                more = json.load(f)
        except Exception as e:
            data.setdefault('errors', []).append(f'{extra}: {e}')
            continue
        data['duels'] += more.get('duels', [])
        data.setdefault('errors', []).extend(more.get('errors', []))
        data['wallSeconds'] = f"{data.get('wallSeconds', '?')}+{more.get('wallSeconds', '?')}"
    return data


def verdict(r):
    """Counter rows (tier 'RPS ...') put the intended winner on side A; mix rows must stay within MIX_SPREAD."""
    if r['tier'].startswith('RPS'):
        return 'ok' if r['win'] == 'A' else 'EVEN' if r['win'] == '=' else 'BROKEN'
    if r['tier'] == 'Mix':
        return 'ok' if r['win'] == '=' or r['exch'] <= MIX_SPREAD else 'SPREAD'
    return ''


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
    if w != 'even':
        # a label the group text doesn't start with ("Sunforged", "T2 mix") gets its side, for --from-notices
        g = r['groupA'] if r['win'] == 'A' else r['groupB']
        if g.split(' ', 1)[-1].split('+')[0] != w:
            w += '(' + r['win'] + ')'
    return (f"{r['groupA']} ({r['metalA']}m) v {r['groupB']} ({r['metalB']}m): wins {r['wa']}-{r['wb']}"
            f"/{r['n']}, keep {r['keepA'] * 100:.0f}%/{r['keepB'] * 100:.0f}%, {w} {fmt_exch(r['exch'])}, "
            f"{r['secs']:.0f}s{' T' + str(r['timeouts']) if r['timeouts'] else ''}{' LOPSIDED' if r['lopsided'] else ''}"
            f"{' ' + verdict(r) if verdict(r) else ''}")


def risen_note(data):
    """Bone Dragon kills that rose as skeletons, per side label over both repetitions (only when any rose)."""
    risen = OrderedDict()
    for d in data['duels']:
        for side in ('A', 'B'):
            n = d.get('risen' + side, 0)
            if n:
                label = d['a'] if side == 'A' else d['b']
                risen[label] = risen.get(label, 0) + n
    return ('; risen skeletons: ' + ', '.join(f'{k} {v}' for k, v in risen.items())) if risen else ''


def notices(data, rows):
    out = []
    flagged = [r for r in rows if r['lopsided']]
    out.append(f"[Duel] {len(data['duels'])} duels, {len(rows)} matchups, max {data.get('maxSeconds', '?')}s each; "
               f"wall {data.get('wallSeconds', '?')}s; lopsided (winner exchange > {LOPSIDED_EXCHANGE:.0f}x): {len(flagged)}; errors: {len(data.get('errors', []))}"
               + risen_note(data))
    broken = [r for r in rows if verdict(r) in ('BROKEN', 'EVEN', 'SPREAD')]
    if any(r['tier'].startswith('RPS') or r['tier'] == 'Mix' for r in rows):
        out[0] += f"; counter/mix rows off target: {len(broken)}"
    # every tier's rows under a "[Duel <tier>]" header line, packed into as few notices as fit under NOTICE_CHARS each
    # (GitHub truncates longer messages and keeps at most 10 notices per step)
    lines = []
    for tier in OrderedDict((r['tier'], 1) for r in rows):
        lines.append(f"[Duel {tier}]")
        lines += [short(r) for r in rows if r['tier'] == tier]
    chunks, cur = [], []
    for line in lines:
        if cur and sum(len(x) + 1 for x in cur) + len(line) > NOTICE_CHARS:
            chunks.append(cur)
            cur = []
        cur.append(line)
    if cur:
        chunks.append(cur)
    for i, c in enumerate(chunks):
        # workflow-command escaping: a literal % is %25, a newline %0A
        out.append(f"[Duel results {i + 1}/{len(chunks)}]%0A" + '%0A'.join(x.replace('%', '%25') for x in c))
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
        flag = lambda r: ' '.join(x for x in ('**lopsided**' if r['lopsided'] else '',
                                              {'ok': 'ok', '': ''}.get(verdict(r), '**' + verdict(r) + '**')) if x)
        for r in rows:
            if r['tier'] != tier:
                continue
            w = r['a'] if r['win'] == 'A' else r['b'] if r['win'] == 'B' else 'even'
            L.append(f"| {r['groupA']} | {r['groupB']} | {r['metalA']}:{r['metalB']} | {r['wa']}-{r['wb']} | "
                     f"{r['keepA'] * 100:.0f}% | {r['keepB'] * 100:.0f}% | {w} {fmt_exch(r['exch'])} | {r['secs']:.0f} s"
                     f"{' (time)' if r['timeouts'] else ''} | {flag(r)} |")
        L.append('')
    return '\n'.join(L)


NOTICE_ROW = re.compile(r'^(?P<ga>.+?) \((?P<ma>\d+)m\) v (?P<gb>.+?) \((?P<mb>\d+)m\): wins (?P<wa>\d+)-(?P<wb>\d+)/(?P<n>\d+), '
                        r'keep (?P<ka>\d+)%/(?P<kb>\d+)%, (?P<w>.+?) (?P<ex>>?[\d.]+)x, (?P<s>\d+)s(?: T(?P<t>\d+))?(?P<lop> LOPSIDED)?'
                        r'(?: (?:ok|EVEN|BROKEN|SPREAD))?$')


def rows_from_notices(text):
    """Rebuilds the matchup rows from the CI annotation text (gh api .../check-runs/<job>/annotations), since the
    JSON file itself never leaves the CI runner."""
    rows, tier = [], None
    for line in text.splitlines():
        line = line.strip()
        m = re.match(r'^\[Duel ([^\]]+)\]', line)
        if m:
            if not m.group(1).startswith('results'):
                tier = m.group(1)
            continue
        m = NOTICE_ROW.match(line)
        if not m or not tier:
            continue
        a, b = m['ga'].split(' ', 1)[1].split('+')[0], m['gb'].split(' ', 1)[1].split('+')[0]
        w = m['w']
        if w.endswith('(A)') or w.endswith('(B)'):
            win, w = w[-2], w[:-3]
        else:
            win = '=' if w == 'even' else 'A' if w == a or w == m['ga'] else 'B'
        rows.append(dict(tier=tier, a=a, b=b, groupA=m['ga'], groupB=m['gb'], metalA=int(m['ma']), metalB=int(m['mb']),
                         wa=int(m['wa']), wb=int(m['wb']), n=int(m['n']), keepA=int(m['ka']) / 100, keepB=int(m['kb']) / 100,
                         win=win, exch=99.0 if m['ex'].startswith('>') else float(m['ex']), secs=float(m['s']),
                         timeouts=int(m['t'] or 0), lopsided=bool(m['lop'])))
        if win == 'A':
            rows[-1]['a'] = w
        elif win == 'B':
            rows[-1]['b'] = w
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
