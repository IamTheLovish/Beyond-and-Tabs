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
    hits = [l for l in lines if _re.search(r'error|warning|ErrorProne|errorprone|exception|EP-PROCPATH', l, _re.I) and not l.lstrip().startswith('at ')]
    print('::warning title=compiler lines::' + _esc('\n'.join(l[:300] for l in hits[:60])[:30000]))
    # the checks that point at real bugs, with their locations (the trial's report)
    wanted = {'IdentityBinaryExpression': 20, 'ModifyCollectionInEnhancedForLoop': 20, 'BadInstanceof': 20,
              'NonAtomicVolatileUpdate': 20, 'ReturnValueIgnored': 20, 'ObjectToString': 20, 'NonOverridingEquals': 20,
              'ModifiedButNotUsed': 20, 'MissingCasesInEnumSwitch': 40, 'FutureReturnValueIgnored': 40,
              'ReferenceEquality': 60, 'NarrowingCompoundAssignment': 25, 'IntLongMath': 15, 'MathRoundIntLong': 15,
              'LongDoubleConversion': 10, 'EmptyCatch': 12, 'HidingField': 15, 'InconsistentCapitalization': 8,
              'UnnecessaryAsync': 8, 'CatchAndPrintStackTrace': 5, 'JdkObsolete': 5, 'StringSplitter': 5}
    out = []
    for c, cap in wanted.items():
        for r in [x for x in rows if x[0] == c][:cap]:
            out.append(r[1].replace('src/main/java/com/solegendary/reignofnether/', '') + ':' + r[2] + ': [' + c + '] ' + r[3][:90])
    half = len(out) // 2
    print('::warning title=serious A::' + _esc('\n'.join(out[:half])[:60000]))
    print('::warning title=serious B::' + _esc('\n'.join(out[half:])[:60000]))
