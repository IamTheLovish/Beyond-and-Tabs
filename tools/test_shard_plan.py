"""Tests for tools/shard_plan.py and the GameTest shard lists of .github/workflows/build.yml.

    python3 -m unittest tools/test_shard_plan.py

The suite tests (TestTheRealShards) read the real sources, build.yml and tools/shard_times.json: every test runs in
exactly one shard, no named batch is split, and the slowest shard is predicted within 1.5x of the mean. Before the plan
(lists hand-placed Oct 21-22) the bar job ran 692 tests and shard 8 406 while shards 2-6 and 9 ran 1-3 each: predicted
30.8 min against a mean of 10.7 (2.88x), measured 17.6-37 min for bar on five green runs of Nov 6.
"""
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import shard_plan as sp  # noqa: E402


def model(times, walls=None, **kw):
    m = {"setup_s": 100.0, "bar_extra_s": 0.0, "place_s": 1.0, "part_s": 0.0, "hash_cap": 8192, "shards": 3,
         "heavy_s": 50.0}
    m.update(kw)
    return sp.Model({"model": m, "tests": times, "batches": walls or {}})


class TestJavaOrder(unittest.TestCase):
    def test_string_hash_is_javas(self):
        self.assertEqual(sp.java_hash(""), 0)
        self.assertEqual(sp.java_hash("hello"), 99162322)                # "hello".hashCode()
        self.assertEqual(sp.java_hash("polygenelubricants"), 0x80000000)  # Integer.MIN_VALUE
        self.assertEqual(sp.java_hash("été"), (233 * 961 + 116 * 31 + 233) & 0xFFFFFFFF)

    def test_vanilla_cuts_each_batch_in_chunks_of_100(self):
        tests = [sp.Test("ATests", "t%03d" % i, "defaultBatch") for i in range(250)]
        tests += [sp.Test("ATests", "n%02d" % i, "solo") for i in range(3)]
        parts = sp.chunks(tests, 8192)
        count = {}
        for p in parts.values():
            count[p] = count.get(p, 0) + 1
        self.assertEqual(count, {"defaultbatch:1": 100, "defaultbatch:2": 100, "defaultbatch:3": 50, "solo:1": 3})

    def test_order_is_the_bucket_order_of_method_hash_codes(self):
        a, b = sp.Test("ATests", "x", "defaultBatch"), sp.Test("BTests", "y", "defaultBatch")

        def bucket(t, cap):
            h = sp.java_hash(sp.PKG + t.cls) ^ sp.java_hash(t.method)
            return (h ^ (h >> 16)) & (cap - 1)
        for cap in (16, 8192):
            want = sorted([a, b], key=lambda t: bucket(t, cap))
            self.assertEqual([t.method for t in sp.vanilla_order([b, a], cap)], [t.method for t in want])


class TestSources(unittest.TestCase):
    def test_batch_constants_and_concatenations_resolve(self):
        src = ('static final String BATCH = "build_arrival", BATCH_MIXED = "build_arrival_mixed";\n'
               'static final String GUARD = BATCH + "_guard";\n'
               '@GameTest(template = ARENA, batch = BATCH_MIXED, timeoutTicks = 900)\npublic static void a(GameTestHelper h) {}\n'
               '@GameTest(batch = BATCH + "_guard_next")\n@SuppressWarnings("x")\npublic static void b(GameTestHelper h) {}\n'
               '@GameTest(batch = GUARD)\npublic static void c(GameTestHelper h) {}\n'
               '@GameTest(template = ARENA)\npublic static void d(GameTestHelper h) {}\n')
        with tempfile.TemporaryDirectory() as d:
            with open(os.path.join(d, "XGameTests.java"), "w", encoding="utf-8") as f:
                f.write(src)
            got = {t.name: (t.cls, t.batch) for t in sp.source_tests(d)}
            with open(os.path.join(d, "YGameTests.java"), "w", encoding="utf-8") as f:
                f.write('@GameTest(batch = name())\npublic static void e(GameTestHelper h) {}\n')
            with self.assertRaises(ValueError):
                sp.source_tests(d)
        self.assertEqual(got, {"a": ("XGameTests", "build_arrival_mixed"), "b": ("XGameTests", "build_arrival_guard_next"),
                               "c": ("XGameTests", "build_arrival_guard"), "d": ("XGameTests", "defaultbatch")})


class TestListsMatchLikeGameTestShards(unittest.TestCase):
    def test_batch_by_full_or_base_name_and_tests_by_prefix(self):
        self.assertTrue(sp.named("duel_matrix:1", "anything", ["duel_matrix"]))
        self.assertTrue(sp.named("duel_matrix:1", "anything", ["duel_matrix:1"]))
        self.assertFalse(sp.named("duel_matrix:2", "anything", ["duel_matrix:1"]))
        self.assertTrue(sp.named("defaultbatch:4", "production_buildings_place_every_unit_6", ["test:production_b"]))
        self.assertFalse(sp.named("defaultbatch:4", "tidewrought_units", ["test:production_b", "test:"]))

    def test_assign_puts_unnamed_tests_in_shard_1_and_reports_doubles(self):
        tests = [sp.Test("A", "alpha", "defaultBatch"), sp.Test("A", "beta", "defaultBatch"),
                 sp.Test("A", "gamma", "g")]
        parts = sp.chunks(tests, 8192)
        where, problems = sp.assign(tests, {2: ["test:al", "g"], 3: ["test:a", "test:zzz"]}, parts)
        self.assertEqual(where["beta"], 1)
        self.assertEqual(where["gamma"], 2)
        self.assertTrue(any("alpha is named by shards [2, 3]" in p for p in problems), problems)
        self.assertTrue(any("'test:zzz' names no test" in p for p in problems), problems)


class TestPlan(unittest.TestCase):
    def setUp(self):
        self.tests = [sp.Test("A", "slow_%d" % i, "defaultBatch") for i in range(6)]
        self.tests += [sp.Test("A", "fast_%02d" % i, "defaultBatch") for i in range(30)]
        self.tests += [sp.Test("B", "big_batch_%d" % i, "big") for i in range(5)]
        self.tests += [sp.Test("A", "fast_00_and_more", "defaultBatch")]   # its name begins with fast_00's
        times = {"slow_%d" % i: 90.0 for i in range(6)}
        times.update({"fast_%02d" % i: 2.0 for i in range(30)})
        times.update({"big_batch_%d" % i: 10.0 for i in range(5)})
        # 6 heavy tests of one chunk cap at 2 per shard, so every shard pays one ~92 s part; the 30 s batch then fits
        # anywhere within 15% (a 120 s batch could not: 100 + 120 + 92 against ~200 is the best there is)
        self.m = model(times, {"big": 30.0})
        self.parts = sp.chunks(self.tests, self.m.cap)

    def test_plan_balances_keeps_batches_whole_and_spreads_heavy_tests(self):
        where = sp.plan(self.tests, self.m, self.parts)
        self.assertEqual(len({where[t.name] for t in self.tests if t.batch == "big"}), 1, "a named batch was split")
        heavy = {}
        for t in self.tests:
            if self.m.t(t.name) >= self.m.heavy:
                key = (where[t.name], self.parts[t.name])
                heavy[key] = heavy.get(key, 0) + 1
        self.assertLessEqual(max(heavy.values()), 2, heavy)   # 6 heavy tests of one chunk over 3 shards
        pred = sp.predict(self.tests, where, self.m, self.parts)
        self.assertLessEqual(sp.balance(pred), 1.15, pred)
        self.assertEqual(where["fast_00"], 1, "a test whose name begins another's must stay in shard 1")
        self.assertEqual(sp.plan(self.tests, self.m, self.parts), where, "the plan is not deterministic")

    def test_written_lists_reproduce_the_plan_exactly(self):
        where = sp.plan(self.tests, self.m, self.parts)
        lists = sp.entries_for(self.tests, where, 3)
        text = 'env:\n  # lists\n  SHARD_2: "old"\n  SHARD_3: "old"\n  SHARD_4: "gone"\njobs:\n'
        out = sp.write_lists(text, lists)
        self.assertNotIn("SHARD_4", out)
        self.assertTrue(out.startswith("env:\n  # lists\n  SHARD_2: ") and out.endswith("\njobs:\n"), out)
        back, problems = sp.assign(self.tests, sp.read_lists(out), self.parts)
        self.assertEqual(problems, [])
        self.assertEqual(back, where)

    def test_a_part_costs_its_placements_and_its_slowest_test(self):
        m = model({"a": 30.0, "b": 5.0}, place_s=2.0, part_s=-1.0)
        self.assertEqual(m.part_cost(["a", "b"]), -1.0 + 2 * 2.0 + 30.0)
        self.assertEqual(m.part_cost(["b"]), 6.0)
        self.assertEqual(model({"a": 30.0}, place_s=0.0, part_s=-5.0).part_cost(["a"]), 30.0)   # never below the test
        self.assertEqual(m.part_cost([]), 0.0)


class TestTheRealShards(unittest.TestCase):
    """build.yml's SHARD_n lines against the real sources and the checked-in timing table."""

    @classmethod
    def setUpClass(cls):
        cls.tests = sp.source_tests()
        cls.model = sp.Model(sp.load())
        cls.parts = sp.chunks(cls.tests, cls.model.cap)
        cls.where, cls.problems = sp.assign(cls.tests, sp.read_lists(open(sp.BUILD, encoding="utf-8").read()),
                                            cls.parts)

    def test_the_suite_is_found(self):
        self.assertGreater(len(self.tests), 1000)
        self.assertEqual(len({t.name for t in self.tests}), len(self.tests), "two @GameTest methods share a name")

    def test_every_test_runs_in_exactly_one_shard(self):
        doubles = [p for p in self.problems if "twice" in p]
        self.assertEqual(doubles, [])
        ran = {}
        for t in self.tests:
            ran.setdefault(self.where[t.name], set()).add(t.name)
        self.assertEqual(set().union(*ran.values()), {t.name for t in self.tests})
        self.assertEqual(sum(len(v) for v in ran.values()), len(self.tests))
        self.assertEqual(set(ran) - set(range(1, self.model.nshards + 1)), set(), "a list for a shard CI does not run")

    def test_no_named_batch_is_split_across_shards(self):
        shards = {}
        for t in self.tests:
            if t.named:
                shards.setdefault(t.batch, set()).add(self.where[t.name])
        split = {b: sorted(s) for b, s in shards.items() if len(s) > 1}
        self.assertEqual(split, {}, "named batches run in parts on several shards")

    def test_heavy_tests_of_one_chunk_are_spread_over_the_shards(self):
        """Tests of >= heavy_s slow each other in one batch part (the production tests generate far chunks), so a
        shard runs at most its even share of each chunk's heavy tests: ceil(heavy in that chunk / shards)."""
        per_chunk, per_part = {}, {}
        for t in self.tests:
            if not t.named and self.model.t(t.name) >= self.model.heavy:
                part = self.parts[t.name]
                per_chunk[part] = per_chunk.get(part, 0) + 1
                per_part.setdefault((self.where[t.name], part), []).append(t.name)
        crowded = {"shard %d %s" % k: v for k, v in per_part.items()
                   if len(v) > -(-per_chunk[k[1]] // self.model.nshards)}
        self.assertEqual(crowded, {})

    def test_the_timing_table_covers_the_suite(self):
        known = sum(1 for t in self.tests if t.name in self.model.times)
        self.assertGreaterEqual(known / len(self.tests), 0.9, "refresh tools/shard_times.json (shard_plan.py refresh)")

    def test_the_slowest_shard_is_within_one_and_a_half_times_the_mean(self):
        pred = sp.predict(self.tests, self.where, self.model, self.parts)
        self.assertLessEqual(sp.balance(pred), 1.5, "predicted job minutes per shard: %s - re-plan with "
                             "`python3 tools/shard_plan.py write`" % {s: round(v / 60, 1) for s, v in sorted(pred.items())})


if __name__ == "__main__":
    unittest.main()
