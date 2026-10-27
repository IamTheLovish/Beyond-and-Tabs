#!/usr/bin/env python3
"""tools/ci_stress_check.py refuses a flake-stress tests list that would run nothing, before any runner starts. Oct 27 night: four runs (40565576897,
40581759617, 40582259285, 40582056986) were given bare test names, which name neither a batch nor a test: prefix, so the
server ran 0 tests on every runner ("All 0 required tests passed") and the summary said "[Stress] 6 runners; 0 with any
failure"; #273 and #289 merged on that."""
import pathlib
import subprocess
import sys
import tempfile
import unittest

TOOL = pathlib.Path(__file__).resolve().parent / "ci_stress_check.py"

JAVA = '''
public class PathingBudgetGameTests {
    static final String BATCH_FALLBACK = "pathing_budget_fallback";
    @GameTest(template = ARENA, batch = BATCH_FALLBACK, timeoutTicks = 500)
    public static void vanilla_path_wall_detour(GameTestHelper helper) { }
    @GameTest(template = ARENA, batch = "parade_ground")
    public static void parade_spawns(GameTestHelper helper) { }
}
'''


def run(*args):
    return subprocess.run([sys.executable, str(TOOL), *args], capture_output=True, text=True)


class Entries(unittest.TestCase):
    def setUp(self):
        self.d = tempfile.TemporaryDirectory()
        pathlib.Path(self.d.name, "PathingBudgetGameTests.java").write_text(JAVA)

    def tearDown(self):
        self.d.cleanup()

    def test_bare_test_names_are_rejected_before_any_runner_starts(self):
        r = run("entries", "sealed_arena_has_no_fluid_after_openGround,vanilla_path_melee_chaser", self.d.name)
        self.assertEqual(r.returncode, 1, r.stdout + r.stderr)
        self.assertIn("::error", r.stdout)
        self.assertIn("sealed_arena_has_no_fluid_after_openGround", r.stdout)
        self.assertIn("test:sealed_arena_has_no_fluid_after_openGround", r.stdout)   # says what to write instead

    def test_prefixes_and_batch_names_pass(self):
        r = run("entries", "test:vanilla_path, pathing_budget_fallback ,parade_ground,defaultBatch:4", self.d.name)
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)

    def test_an_empty_prefix_is_rejected(self):
        self.assertEqual(run("entries", "test:", self.d.name).returncode, 1)


if __name__ == "__main__":
    unittest.main()
