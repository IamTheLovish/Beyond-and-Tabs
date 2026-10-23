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

# CI can read job annotations through the API but not logs/artifacts: repeat the summary, and the gradle failure text if
# the build failed, as two multi-line annotations (workflow-command escaping)
def _esc(t):
    return t.replace('%', '%25').replace('\r', '').replace('\n', '%0A')
import io, contextlib
if len(sys.argv) > 2 and sys.argv[2] == '--annotate':
    lines = open(sys.argv[1], errors='replace').read().splitlines()
    body = [l for l in lines if 'What went wrong' in l]
    idx = next((i for i, l in enumerate(lines) if 'What went wrong' in l), None)
    if idx is not None:
        print('::error title=gradle failure::' + _esc('\n'.join(lines[max(0, idx - 15):idx + 30])[:30000]))
    else:
        print('::warning title=gradle log tail::' + _esc('\n'.join(lines[-30:])[:30000]))
    print('::warning title=Error Prone summary::' + _esc(f'{len(rows)} warnings\n' + '\n'.join(f'{c} {n}' for c, n in by.most_common(40)) + '\n\n' + '\n'.join(f'{r[1]}:{r[2]}: [{r[0]}] {r[3]}' for r in rows[:40])))
    # the compiler's own lines (javac prints them before gradle's failure report)
    import re as _re
    hits = [l for l in lines if _re.search(r'error|warning|ErrorProne|errorprone|exception', l, _re.I) and not l.lstrip().startswith('at ')]
    print('::warning title=compiler lines::' + _esc('\n'.join(l[:300] for l in hits[:60])[:30000]))
