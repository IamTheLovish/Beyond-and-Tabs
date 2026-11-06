#!/usr/bin/env python3
"""The GameTest shard plan of .github/workflows/build.yml (its SHARD_2 .. SHARD_N lines), made from measured times.

CI runs shard 1 (job `bar`, which also builds the jar) with BAT_GAMETEST_SKIP = every SHARD_n list and shard n >= 2 with
BAT_GAMETEST_ONLY = SHARD_n (gametest/GameTestShards). Each server runs its batches ONE AFTER ANOTHER; inside a batch
every test runs at once, after the batch has placed every test's arena. So a batch costs about
    PLACE_S x (its tests) + (its slowest test)
and a shard costs its setup (checkout, gradle, server start) plus the sum over the batch parts it runs. Measured on
run 42155545605 (Nov 6): the 30 default-batch parts fit wall = 1.68 s x tests + slowest test - 3.9 s (k = 0.99 on the
slowest test), so a shard pays every default chunk it touches once, at that chunk part's slowest test.

Which default chunk a test sits in is vanilla's: Forge collects every declared method of every @GameTestHolder class in
a HashSet<Method> (ForgeGameTestHooks.registerGametests), registers them in that set's iteration order, and
GameTestRunner.groupTestsIntoBatches cuts each batch name's list into chunks of 100 ("defaultBatch:1" ...). The set's
order is the bucket order of Method.hashCode() (= declaring class name hash ^ method name hash, spread h ^ h>>>16) in a
table of HASH_CAP buckets; that replays 774 of the 776 default tests' chunks of run 42155545605 exactly (8192 buckets:
the classes declare 3073-6144 methods, helpers and lambdas included). `refresh` re-checks the cap against the runs.

The plan (`plan`): a named batch (`batch = "..."`) is one unit and never split (its tests share a server pass and
often arenas); every default-batch test is its own item. Items are placed slowest first (sorted bin-packing, LPT), each
on the shard whose finish time grows the least - a default test that joins a chunk part already holding a slower test
costs only its placement - then single moves and swaps from the slowest shard are tried until none helps. Shard 1 starts
with the bar job's extra build time. Nothing is skipped, retried or dropped: the plan only decides where each test runs.

The SHARD_n lines it writes name each shard's named batches by batch name (so a new test of that batch follows it) and
its default tests by the shortest `test:` prefix that matches no test outside the shard - so no two lists overlap and
every test runs exactly once (bar-audit proves it on every build). A test the lists do not name (any new test) runs in
shard 1. Times come from tools/shard_times.json, refreshed from green CI runs:

    python3 tools/shard_plan.py                 # each shard's predicted time under build.yml's lists
    python3 tools/shard_plan.py check           # exit 1 when a test is named by two lists (it would run twice)
    python3 tools/shard_plan.py write           # re-plan and rewrite build.yml's SHARD_n lines
    python3 tools/shard_plan.py refresh RUN...  # tools/shard_times.json from green PR build runs (gh, hosted runners)

Stdlib only (the ruff job's tests import it).
"""
import bisect
import glob
import json
import os
import re
import statistics
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GT = os.path.join(ROOT, "bar", "src", "main", "java", "com", "solegendary", "reignofnether", "gametest")
PKG = "com.solegendary.reignofnether.gametest."
BUILD = os.path.join(ROOT, ".github", "workflows", "build.yml")
TABLE = os.path.join(ROOT, "tools", "shard_times.json")
REPO = "IamTheLovish/Beyond-and-Tabs"
DEFAULT = "defaultbatch"
CHUNK = 100   # vanilla GameTestRunner.groupTestsIntoBatches: at most 100 tests per batch


class Test:
    __slots__ = ("cls", "method", "name", "batch")

    def __init__(self, cls, method, batch):
        self.cls, self.method, self.name, self.batch = cls, method, method.lower(), batch.lower()

    @property
    def named(self):
        return self.batch != DEFAULT

    def __repr__(self):
        return "Test(%s.%s, %s)" % (self.cls, self.method, self.batch)


# ---------------------------------------------------------------- the tests and vanilla's batches
def _constants(src):
    """The class's `static final String` constants, several per declaration too (A = "x", B = A + "_y")."""
    consts = {}
    for decl in re.findall(r'static final String\s+([^;]*);', src):
        for m in re.finditer(r'(\w+)\s*=\s*((?:"[^"]*"|\w+)(?:\s*\+\s*(?:"[^"]*"|\w+))*)', decl):
            value = _string(m.group(2), consts)
            if value is not None:
                consts[m.group(1)] = value
    return consts


def _string(expr, consts):
    """A Java string expression of literals and constants joined by +, or None when it is anything else."""
    out = []
    for part in expr.split("+"):
        part = part.strip()
        if part.startswith('"') and part.endswith('"') and len(part) >= 2:
            out.append(part[1:-1])
        elif part in consts:
            out.append(consts[part])
        else:
            return None
    return "".join(out)


def _annotations(src):
    """(arguments, method name) of every @GameTest(...) in a source, the arguments read to the matching parenthesis
    (timeoutTicks = TRIES * (HOLD + WATCH) + 60 has parentheses of its own)."""
    for m in re.finditer(r'@GameTest\(', src):
        depth, i = 1, m.end()
        while depth and i < len(src):
            depth += {"(": 1, ")": -1}.get(src[i], 0)
            i += 1
        method = re.match(r'\s*(?:@\w+(?:\([^)]*\))?\s*)*public static void (\w+)', src[i:])
        if method:
            yield src[m.end():i - 1], method.group(1)


def source_tests(gt=GT):
    """Every @GameTest method of the gametest package: Test(class, method, batch) in file order. A batch given by a
    constant or a concatenation (batch = BATCH + "_guard_next") is resolved; one that cannot be read raises."""
    out = []
    for f in sorted(glob.glob(os.path.join(gt, "*.java"))):
        src = open(f, encoding="utf-8").read()
        consts = _constants(src)
        for args, method in _annotations(src):
            b = re.search(r'\bbatch\s*=\s*((?:"[^"]*"|\w+)(?:\s*\+\s*(?:"[^"]*"|\w+))*)\s*(\S?)', args)
            batch = "defaultBatch"
            if re.search(r'\bbatch\s*=', args):
                batch = _string(b.group(1), consts) if b and b.group(2) in ("", ",") else None
                if batch is None:
                    raise ValueError("%s.%s: cannot read the batch of @GameTest(%s)" % (os.path.basename(f), method, args))
            out.append(Test(os.path.basename(f)[:-5], method, batch))
    return out


def java_hash(s):
    """java.lang.String.hashCode() as an unsigned 32-bit int (UTF-16 code units)."""
    h = 0
    data = s.encode("utf-16-be")
    for i in range(0, len(data), 2):
        h = (31 * h + (data[i] << 8 | data[i + 1])) & 0xFFFFFFFF
    return h


def vanilla_order(tests, cap):
    """The tests in Forge's HashSet<Method> iteration order (bucket order; a shared bucket keeps file order - the real
    order there depends on class loading and is the one thing this cannot know, ~2 tests in 776)."""
    def bucket(t):
        h = java_hash(PKG + t.cls) ^ java_hash(t.method)
        return (h ^ (h >> 16)) & (cap - 1)
    return sorted(tests, key=bucket)


def chunks(tests, cap):
    """{test name: vanilla batch part}: "defaultbatch:3", "maps_metal:1", ... (each batch name cut in chunks of 100)."""
    seen, out = {}, {}
    for t in vanilla_order(tests, cap):
        i = seen.get(t.batch, 0)
        seen[t.batch] = i + 1
        out[t.name] = "%s:%d" % (t.batch, i // CHUNK + 1)
    return out


# ---------------------------------------------------------------- build.yml's lists, read the way GameTestShards does
def read_lists(text):
    return {int(m.group(1)): [e.strip().lower() for e in m.group(2).split(",") if e.strip()]
            for m in re.finditer(r'^  SHARD_(\d+):\s*"([^"]*)"', text, re.M)}


def named(batch_part, test, entries):
    """GameTestShards.named: a batch by full or base name, or a test by a `test:` prefix of its (bare) name."""
    base = batch_part.split(":")[0]
    for e in entries:
        if e == batch_part or e == base:
            return True
        if e.startswith("test:") and len(e) > 5 and test.startswith(e[5:]):
            return True
    return False


def assign(tests, lists, parts):
    """({test name: shard}, [problems]): shard 1 takes what no list names; a test two lists name is a problem."""
    where, problems = {}, []
    for t in tests:
        hit = [n for n, es in sorted(lists.items()) if named(parts[t.name], t.name, es)]
        if len(hit) > 1:
            problems.append("%s is named by shards %s - it would run twice" % (t.name, hit))
        where[t.name] = hit[0] if hit else 1
    for n, es in sorted(lists.items()):
        for e in es:
            if not any(named(parts[t.name], t.name, [e]) for t in tests):
                problems.append("SHARD_%d: entry '%s' names no test" % (n, e))
    return where, problems


# ---------------------------------------------------------------- the time model
class Model:
    """Times from tools/shard_times.json: per test (its [TestTime], s), per named batch (its measured wall, s)."""

    def __init__(self, table):
        m = table["model"]
        self.setup, self.bar_extra = m["setup_s"], m["bar_extra_s"]
        self.place, self.part = m["place_s"], m["part_s"]
        self.cap, self.nshards = m["hash_cap"], m["shards"]
        self.heavy = m["heavy_s"]
        self.times, self.walls = table["tests"], table["batches"]
        known = sorted(self.times.values())
        self.unknown = statistics.median(known) if known else 5.0

    def t(self, name):
        return self.times.get(name, self.unknown)

    def part_cost_n(self, count, slow):
        """One batch part's wall: every arena placed, then the slowest test (never less than the slowest test)."""
        return max(slow, self.part + self.place * count + slow) if count else 0.0

    def part_cost(self, names):
        return self.part_cost_n(len(names), max(self.t(n) for n in names)) if names else 0.0

    def unit_cost(self, batch, names, whole):
        if whole and batch in self.walls:
            return self.walls[batch]
        return self.part_cost(names)

    def base(self, shard):
        return self.setup + (self.bar_extra if shard == 1 else 0.0)


def predict(tests, where, model, parts):
    """{shard: predicted job seconds}: setup + every batch part it runs (named batches at their measured wall)."""
    size = {}
    for t in tests:
        size[t.batch] = size.get(t.batch, 0) + 1
    groups = {}
    for t in tests:
        groups.setdefault((where[t.name], parts[t.name]), []).append(t)
    out = {n: model.base(n) for n in range(1, model.nshards + 1)}
    for (shard, part), ts in groups.items():
        batch = part.split(":")[0]
        whole = batch != DEFAULT and len(ts) == size[batch]
        out[shard] = out.get(shard, model.base(shard)) + model.unit_cost(batch, [t.name for t in ts], whole)
    return out


def balance(pred):
    vals = list(pred.values())
    return max(vals) / statistics.mean(vals)


# ---------------------------------------------------------------- the plan: sorted bin-packing + local moves
class Packing:
    """Shard loads with incremental part costs. An item is ("unit", batch, [names], cost) - a whole named batch at its
    measured wall - or ("test", default part, [name], seconds). Each shard keeps, per default part, its tests' times
    sorted, so adding or removing a test re-prices its part in O(log n)."""

    def __init__(self, model, nshards, limit=None):
        self.m, self.n = model, nshards
        self.load = {s: model.base(s) for s in range(1, nshards + 1)}
        self.parts = {s: {} for s in range(1, nshards + 1)}   # default part -> sorted [seconds]
        self.at = {}                                           # item index -> shard
        self.limit = limit or {}                               # default part -> most heavy tests one shard may run

    def heavy(self, item):
        return item[0] == "test" and item[3] >= self.m.heavy

    def fits(self, item, s):
        """A heavy test joins a shard's part of its chunk only while that part holds fewer than the chunk's share."""
        if not self.heavy(item):
            return True
        return self.fits_instead(item, s, None)

    def fits_instead(self, item, s, leaving):
        """fits(), with `leaving` (an item now in shard s, or None) gone from s first - the check for a swap."""
        if not self.heavy(item):
            return True
        ts = self.parts[s].get(item[1], [])
        n = sum(1 for t in ts if t >= self.m.heavy)
        if leaving is not None and self.heavy(leaving) and leaving[1] == item[1]:
            n -= 1
        return n < self.limit.get(item[1], 1)

    def _cost(self, ts):
        return self.m.part_cost_n(len(ts), ts[-1]) if ts else 0.0

    def delta_add(self, item, s):
        if item[0] == "unit":
            return item[3]
        ts, t = self.parts[s].get(item[1], []), item[3]
        return self.m.part_cost_n(len(ts) + 1, max(t, ts[-1]) if ts else t) - self._cost(ts)

    def delta_remove(self, item, s):
        if item[0] == "unit":
            return -item[3]
        ts, t = self.parts[s][item[1]], item[3]
        if len(ts) == 1:
            return -self._cost(ts)
        top = ts[-2] if t >= ts[-1] else ts[-1]
        return self.m.part_cost_n(len(ts) - 1, top) - self._cost(ts)

    def add(self, i, item, s):
        self.load[s] += self.delta_add(item, s)
        if item[0] == "test":
            bisect.insort(self.parts[s].setdefault(item[1], []), item[3])
        self.at[i] = s

    def remove(self, i, item):
        s = self.at.pop(i)
        self.load[s] += self.delta_remove(item, s)
        if item[0] == "test":
            ts = self.parts[s][item[1]]
            del ts[bisect.bisect_left(ts, item[3])]
        return s


def plan(tests, model, parts, nshards=None):
    """{test name: shard 1..N}."""
    n = nshards or model.nshards
    units, items = {}, []
    for t in tests:
        if t.named:
            units.setdefault(t.batch, []).append(t.name)
        else:
            items.append(("test", parts[t.name], [t.name], model.t(t.name)))
    items += [("unit", b, names, model.unit_cost(b, names, True)) for b, names in sorted(units.items())]
    # heavy tests (>= heavy_s) slow each other when they share a batch part (the production tests generate far chunks:
    # 264 s for the worst of 4 sharing a server, Oct 21) and the model prices a part at its slowest test only - so each
    # chunk's heavy tests are spread over the shards as evenly as they go: at most ceil(heavy in chunk / shards) per part
    heavy = {}
    for it in items:
        if it[0] == "test" and it[3] >= model.heavy:
            heavy[it[1]] = heavy.get(it[1], 0) + 1
    pk = Packing(model, n, {k: -(-v // n) for k, v in heavy.items()})

    def size(it):
        return it[3] if it[0] == "unit" else it[3] + model.place

    # a default test whose whole name begins another test's name (x and x_for_a_hero_that_joins_late) has no test:
    # prefix of its own - any entry naming it names the longer one too - so it stays in shard 1, which no list names
    every = [t.name for t in tests]
    pinned = {i for i, it in enumerate(items)
              if it[0] == "test" and any(o != it[2][0] and o.startswith(it[2][0]) for o in every)}
    for i in sorted(pinned):
        pk.add(i, items[i], 1)
    for i in sorted((i for i in range(len(items)) if i not in pinned), key=lambda i: (-size(items[i]), items[i][2][0])):
        best = min((s for s in range(1, n + 1) if pk.fits(items[i], s)),
                   key=lambda s: (pk.load[s] + pk.delta_add(items[i], s), s))
        pk.add(i, items[i], best)
    _improve(pk, items, n, pinned)
    return {name: s for i, s in pk.at.items() for name in items[i][2]}


def _improve(pk, items, n, pinned=frozenset(), rounds=2000):
    """Single moves, then swaps, off the slowest shard while they lower its time and leave the other shard below the
    old maximum. Deterministic (ties go to the lower item and shard numbers)."""
    for _ in range(rounds):
        top = max(range(1, n + 1), key=lambda s: (pk.load[s], -s))
        peak = pk.load[top]
        mine = sorted(i for i, s in pk.at.items() if s == top and i not in pinned)
        best = None   # (new peak of the two shards, move)
        for i in mine:
            out = pk.delta_remove(items[i], top)
            for s in range(1, n + 1):
                if s != top and pk.fits(items[i], s):
                    after = max(peak + out, pk.load[s] + pk.delta_add(items[i], s))
                    if after < peak - 1e-6 and (best is None or after < best[0] - 1e-6):
                        best = (after, ("move", i, s))
        if best is None:
            others = sorted((i, s) for i, s in pk.at.items() if s != top and i not in pinned)
            for i in mine:
                out_i = pk.delta_remove(items[i], top)
                for j, s in others:
                    if items[i][0] == "test" and items[j][0] == "test" and items[i][1] == items[j][1]:
                        continue   # same default part on both sides: a swap of two tests of one chunk changes little
                    if not pk.fits_instead(items[i], s, items[j]) or not pk.fits_instead(items[j], top, items[i]):
                        continue
                    a = peak + out_i + pk.delta_add(items[j], top)
                    if a >= peak - 1e-6:
                        continue
                    b = pk.load[s] + pk.delta_remove(items[j], s) + pk.delta_add(items[i], s)
                    after = max(a, b)
                    if after < peak - 1e-6 and (best is None or after < best[0] - 1e-6):
                        best = (after, ("swap", i, j))
        if best is None:
            return
        kind, a, b = best[1]
        if kind == "move":
            pk.remove(a, items[a])
            pk.add(a, items[a], b)
        else:
            sa, sb = pk.remove(a, items[a]), pk.remove(b, items[b])
            pk.add(a, items[a], sb)
            pk.add(b, items[b], sa)


# ---------------------------------------------------------------- the lists for build.yml
def entries_for(tests, where, nshards):
    """{shard n >= 2: [entries]}: whole named batches by name, default tests by their shortest unique test: prefix."""
    names = sorted(t.name for t in tests)
    size = {}
    for t in tests:
        size[t.batch] = size.get(t.batch, 0) + 1
    out = {s: [] for s in range(2, nshards + 1)}
    for s in range(2, nshards + 1):
        inside = [t for t in tests if where[t.name] == s]
        mine = {t.name for t in inside}
        batches = sorted({t.batch for t in inside if t.named})
        for b in batches:
            if sum(1 for t in inside if t.batch == b) != size[b]:
                raise ValueError("named batch %s is split across shards" % b)
        out[s] += batches
        loose = sorted(t.name for t in inside if not t.named)
        outside = [x for x in names if x not in mine]
        prefixes = []
        for name in loose:
            for k in range(1, len(name) + 1):
                p = name[:k]
                if not any(o.startswith(p) for o in outside):
                    prefixes.append(p)
                    break
            else:
                raise ValueError("%s is a prefix of a test in another shard - no test: entry can name it alone" % name)
        prefixes = sorted(set(prefixes))
        out[s] += ["test:" + p for p in prefixes if not any(q != p and p.startswith(q) for q in prefixes)]
    # a batch entry must not name a batch of another shard: batch names are unique, and a base name
    # never equals "defaultbatch" here
    return out


def write_lists(text, lists):
    """build.yml with its SHARD_n lines replaced by `lists` (n >= 2), in place of the old block of lines."""
    lines = text.split("\n")
    idx = [i for i, ln in enumerate(lines) if re.match(r'^  SHARD_\d+:\s*"', ln)]
    if not idx:
        raise ValueError("no SHARD_n lines in build.yml")
    new = ['  SHARD_%d: "%s"' % (n, ",".join(es)) for n, es in sorted(lists.items())]
    keep = [ln for i, ln in enumerate(lines[idx[0]:idx[-1] + 1]) if not re.match(r'^  SHARD_\d+:\s*"', ln)]
    keep = [ln for ln in keep if not ln.lstrip().startswith("#")]   # comments between the old lines described them
    return "\n".join(lines[:idx[0]] + new + keep + lines[idx[-1] + 1:])


# ---------------------------------------------------------------- refresh from CI runs
TIME = re.compile(r"\[TestTime\] (\S+) (\d+) ms")
BATCH = re.compile(r"\[(\d\d):(\d\d):(\d\d)\].*Running test batch '([^']+)' \((\d+) tests?\)")
DONE = re.compile(r"\[(\d\d):(\d\d):(\d\d)\].*All (\d+) required tests passed")


def _sec(m):
    return int(m.group(1)) * 3600 + int(m.group(2)) * 60 + int(m.group(3))


def parse_shard(report, log=None):
    """[(batch part, wall s, [(test, s)])] of one green shard. The [TestTime] lines come from the shard report; the
    batch lines from the report when it keeps them (build.yml since this plan) or else from the job log. A green
    batch's tests all pass before the next batch starts, so the passes cut by the batches' own test counts."""
    text = open(report, errors="replace").read()
    # a pass is logged once per test; the duel shards print theirs twice (same line) - keep the first
    times = list({m.group(1).lower(): int(m.group(2)) / 1000.0 for m in reversed(list(TIME.finditer(text)))}.items())
    times.reverse()
    src = text if BATCH.search(text) else (open(log, errors="replace").read() if log else "")
    rows, end = [], None
    for ln in src.splitlines():
        b, d = BATCH.search(ln), DONE.search(ln)
        if b:
            rows.append([b.group(4).lower(), int(b.group(5)), _sec(b)])
        if d:
            end = _sec(d)
    if not rows or end is None:
        raise ValueError("%s: no batch lines or no 'All N required tests passed'" % report)
    out, i = [], 0
    for k, (name, cnt, start) in enumerate(rows):
        stop = rows[k + 1][2] if k + 1 < len(rows) else end
        wall = (stop - start) % 86400
        out.append((name, float(wall), times[i:i + cnt]))
        i += cnt
    if i != len(times):
        raise ValueError("%s: %d passes for batches counting %d tests" % (report, len(times), i))
    return out


def _gh(args, **kw):
    return subprocess.run(["gh"] + args, check=True, capture_output=True, text=True, **kw).stdout


def fetch_run(run, into):
    """Downloads a green PR build's shard reports (and, for a report without batch lines, its job log) under `into`;
    returns [(shard, report path, log path or None, job seconds before the first batch, job seconds after the last)]."""
    jobs = json.loads(_gh(["api", "repos/%s/actions/runs/%s/jobs?per_page=100" % (REPO, run)]))["jobs"]
    _gh(["run", "download", str(run), "-R", REPO, "-p", "gametests-shard-*", "-D", into])
    out = []
    for j in jobs:
        m = re.fullmatch(r"bar-tests \((\d+)\)", j["name"])
        shard = 1 if j["name"] == "bar" else (int(m.group(1)) if m else None)
        if shard is None:
            continue
        if j["conclusion"] != "success":
            raise ValueError("run %s: %s is %s - refresh only from green runs" % (run, j["name"], j["conclusion"]))
        rep = sorted(glob.glob(os.path.join(into, "gametests-shard-%d-a*" % shard, "shard-report.txt")))[-1]
        log = None
        if not BATCH.search(open(rep, errors="replace").read()):
            log = os.path.join(into, "job-%d.log" % shard)
            with open(log, "w", encoding="utf-8") as f:
                f.write(_gh(["api", "repos/%s/actions/jobs/%d/logs" % (REPO, j["id"]), "--allow-escape-sequences"],
                            encoding="utf-8", errors="replace"))
        out.append((shard, rep, log, j["started_at"], j["completed_at"]))
    return out


def _clock(iso):
    h, m, s = iso[11:19].split(":")
    return int(h) * 3600 + int(m) * 60 + int(s)


def refresh(runs, tests=None):
    """A new timing table from green runs: the median over the runs of every test's time and every whole named batch's
    wall; the placement cost fitted on the default parts; setup = job start to first batch + last pass to job end."""
    tests = tests if tests is not None else source_tests()
    per_test, per_batch, fits, setups, extras, chunk_obs = {}, {}, [], [], [], {}
    for run in runs:
        with tempfile.TemporaryDirectory() as d:
            for shard, rep, log, started, done in fetch_run(run, d):
                batches = parse_shard(rep, log)
                src = open(log if log else rep, errors="replace").read()
                first = BATCH.search(src)
                last = None
                for last in DONE.finditer(src):
                    pass
                pre = (_sec(first) - _clock(started)) % 86400
                post = (_clock(done) - _sec(last)) % 86400
                (extras if shard == 1 else setups).append(pre + post)
                for part, wall, ts in batches:
                    for name, s in ts:
                        per_test.setdefault(name, []).append(s)
                        chunk_obs.setdefault(run, {})[name] = part
                    base = part.split(":")[0]
                    if base == DEFAULT and ts:
                        fits.append((len(ts), wall - max(s for _, s in ts)))
                    elif ts:
                        per_batch.setdefault((run, base), []).append((wall, len(ts)))
    size = {}
    for t in tests:
        size[t.batch] = size.get(t.batch, 0) + 1
    walls = {}
    for (run, base), parts_ in per_batch.items():
        if sum(n for _, n in parts_) == size.get(base) and len(parts_) == 1:
            walls.setdefault(base, []).append(parts_[0][0])
    n = len(fits)
    sx = sum(f[0] for f in fits)
    sy = sum(f[1] for f in fits)
    sxx = sum(f[0] * f[0] for f in fits)
    sxy = sum(f[0] * f[1] for f in fits)
    place = (n * sxy - sx * sy) / (n * sxx - sx * sx) if n > 1 and n * sxx != sx * sx else 1.5
    part = (sy - place * sx) / n if n else 0.0
    setup = statistics.median(setups)
    caps = {}
    for cap in (2048, 4096, 8192, 16384, 32768):
        pred = chunks(tests, cap)
        caps[cap] = sum(1 for obs in chunk_obs.values() for k, v in obs.items() if pred.get(k) == v
                        and not v.startswith(tuple(b + ":" for b in walls)))
    return {
        "about": "GameTest times for tools/shard_plan.py, from green PR build runs %s (hosted runners). "
                 "tests = median [TestTime] s; batches = median wall s of each named batch run whole; "
                 "model: setup_s job start to first batch + last pass to job end, bar_extra_s the bar job's extra "
                 "(jar build, JUnit), part_s + place_s x tests + slowest test = one batch part's wall." % runs,
        "runs": list(runs),
        "model": {"setup_s": round(setup, 1), "bar_extra_s": round(statistics.median(extras) - setup, 1),
                  "place_s": round(place, 3), "part_s": round(part, 2), "hash_cap": max(caps, key=caps.get),
                  "shards": 9},
        "tests": {k: round(statistics.median(v), 2) for k, v in sorted(per_test.items())},
        "batches": {k: round(statistics.median(v), 1) for k, v in sorted(walls.items())},
    }


# ---------------------------------------------------------------- command line
def load(table=TABLE):
    return json.load(open(table, encoding="utf-8"))


def report(tests, where, model, parts, title):
    pred = predict(tests, where, model, parts)
    count = {s: 0 for s in pred}
    for t in tests:
        count[where[t.name]] += 1
    rows = " ".join("%d:%dt/%.1fm" % (s, count[s], pred[s] / 60) for s in sorted(pred))
    print("%s: %d tests; max %.1f min, mean %.1f, max/mean %.2f - %s" % (
        title, len(tests), max(pred.values()) / 60, statistics.mean(pred.values()) / 60, balance(pred), rows))
    return pred


def main(argv):
    cmd = argv[1] if len(argv) > 1 else "show"
    if cmd == "refresh":
        if len(argv) < 3:
            print("usage: shard_plan.py refresh RUN_ID [RUN_ID ...]", file=sys.stderr)
            return 2
        table = refresh(argv[2:])
        with open(TABLE, "w", encoding="utf-8", newline="\n") as f:
            json.dump(table, f, indent=1, sort_keys=True)
            f.write("\n")
        print("wrote %s: %d tests, %d named batches, model %s" % (TABLE, len(table["tests"]), len(table["batches"]),
                                                                  table["model"]))
        return 0
    tests, model = source_tests(), Model(load())
    parts = chunks(tests, model.cap)
    text = open(BUILD, encoding="utf-8").read()
    where, problems = assign(tests, read_lists(text), parts)
    if cmd in ("show", "check"):
        report(tests, where, model, parts, "build.yml's lists")
        for p in problems:
            print("  ERROR:" if "twice" in p else "  warning:", p)
        return 1 if cmd == "check" and any("twice" in p for p in problems) else 0
    if cmd == "write":
        report(tests, where, model, parts, "before")
        new = plan(tests, model, parts)
        report(tests, new, model, parts, "planned")
        lists = entries_for(tests, new, model.nshards)
        out = write_lists(text, lists)
        check, problems = assign(tests, read_lists(out), parts)
        if problems or check != new:
            print("refusing to write: the written lists would not reproduce the plan: %s" % problems[:5])
            return 1
        with open(BUILD, "w", encoding="utf-8", newline="\n") as f:
            f.write(out)
        print("wrote %d SHARD_n lines (%d characters) to %s" % (len(lists), sum(len(",".join(v)) for v in lists.values()), BUILD))
        return 0
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
