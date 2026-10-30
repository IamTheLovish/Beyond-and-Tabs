"""tools/ci_plan.py: which changes skip the GameTests. Run: python3 -m unittest discover -s tools -p 'test_*.py'

lovish, Oct 23 02:43: an installer-only PR (#204) waited 15-20 min on GameTests that never touch the installer.
The installer has its own build + Go tests (installer.yml), so installer-only changes skip the GameTests like docs do.
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(__file__))
import ci_plan  # noqa: E402

NL = chr(10)


class SkipsGameTests(unittest.TestCase):
    def test_docs_still_skip(self):
        for p in ["README.md", "docs/index.html", "design/sheet.png"]:
            self.assertTrue(ci_plan.is_doc(p), p)

    def test_installer_only_skips(self):
        for p in ["installer/main.go", "installer/bubble/bubble.ps1", "installer/brand/cat.png",
                  ".github/workflows/installer.yml"]:
            self.assertTrue(ci_plan.is_doc(p), p)

    def test_game_code_still_tests(self):
        for p in ["mod/src/main/java/X.java", "bar/src/main/java/Y.java", "tools/ci_plan.py",
                  ".github/workflows/build.yml", "design/units.json", "pack/INSTALL-for-testers.txt",
                  "installers/x.go", "src/installer/x.java"]:
            self.assertFalse(ci_plan.is_doc(p), p)


class CompileOnly(unittest.TestCase):
    """[compile-only] on a tests-only head: jar + JUnit, no GameTests (the red flake-stress run proves the tests)."""
    def test_tag_on_a_pull_request_head(self):
        self.assertTrue(ci_plan.compile_only("pull_request", "Hero marker: GameTest first [compile-only]" + NL + NL + "body"))

    def test_no_tag_runs_everything(self):
        for m in ["Hero marker: the fix", "", None, "compile-only without brackets", "[compile only]",
                  "The fix" + NL + NL + "The earlier head carried [compile-only]; this one runs everything."]:
            self.assertFalse(ci_plan.compile_only("pull_request", m), m)

    def test_never_on_main_or_by_hand(self):
        # a squash message on main can quote a PR's tagged commit; main and manual runs always run the suite
        for ev in ["push", "workflow_dispatch", "", None]:
            self.assertFalse(ci_plan.compile_only(ev, "Squash of #400" + NL + NL + "* Tests first [compile-only]"), ev)


class PlanCode(unittest.TestCase):
    """The `code` output main() writes: what jobs bar / bar-tests / compile-only key on."""
    def test_values(self):
        tagged = "Tests first [compile-only]"
        self.assertEqual(ci_plan.plan_code(True, "pull_request", tagged), "compile")
        self.assertEqual(ci_plan.plan_code(True, "pull_request", "the fix"), "true")
        self.assertEqual(ci_plan.plan_code(False, "pull_request", tagged), "false")   # docs only stays docs only
        self.assertEqual(ci_plan.plan_code(True, "pull_request", ""), "true")         # a failed lookup = the full suite
        self.assertEqual(ci_plan.plan_code(True, "push", tagged), "true")


if __name__ == "__main__":
    unittest.main()
