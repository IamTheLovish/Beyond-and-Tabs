#!/usr/bin/env python3
"""Error Prone trial: summarise the warnings in a gradle build log (count by check, first lines).
Usage: errorprone_summary.py build.log  - prints markdown; CI appends it to $GITHUB_STEP_SUMMARY."""
import re, sys, collections
pat = re.compile(r'^(?P<file>\S*?/(?:bar/)?src/\S+\.java):(?P<line>\d+): warning: \[(?P<check>\w+)\] (?P<msg>.*)$')
rows = []
for ln in open(sys.argv[1], errors='replace'):
    m = pat.match(ln.rstrip('\n'))
    if m:
        rows.append((m['check'], re.sub(r'^.*?/src/', 'src/', m['file']), m['line'], m['msg'][:140]))
by = collections.Counter(r[0] for r in rows)
print(f'## Error Prone: {len(rows)} warnings, {len(by)} checks\n')
print('| check | count |\n|---|---|')
for c, n in by.most_common(40):
    print(f'| {c} | {n} |')
print('\n```')
for r in rows[:40]:
    print(f'{r[1]}:{r[2]}: [{r[0]}] {r[3]}')
print('```')
