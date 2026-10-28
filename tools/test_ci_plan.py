"""tools/ci_plan.py: which changes skip the GameTests. Run: python3 -m unittest discover -s tools -p 'test_*.py'

lovish, Oct 23 02:43: an installer-only PR (#204) waited 15-20 min on GameTests that never touch the installer.
The installer has its own build + Go tests (installer.yml), so installer-only changes skip the GameTests like docs do.
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(__file__))
import ci_plan  # noqa: E402


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


if __name__ == "__main__":
    unittest.main()
