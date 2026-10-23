#!/usr/bin/env python3
"""Tests for the copy-paste installers (installer/install.sh, installer/install.ps1).

lovish, Oct 23 00:32: "improve the launcher and the way I can copy n paste it to allow them to play linux might be
getting in the way" - the .exe can't run on Linux, gets blocked as unsigned on Windows, and installs a second Prism
next to one people already have, so testers ended up importing the game into Prism by hand.

install.sh runs for real here in a throwaway HOME (BAT_NO_DOWNLOAD / BAT_NO_LAUNCH keep it offline and quiet);
install.ps1 is checked for the same rules here and run for real on a Windows runner (installer.yml).

    python3 tools/test_install_scripts.py
"""
import os
import pathlib
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parent.parent
SH = ROOT / "installer" / "install.sh"
PS1 = ROOT / "installer" / "install.ps1"
INSTANCE = "BeyondAndTabs-test"
PACK = "https://raw.githubusercontent.com/IamTheLovish/beyond-and-tabs-pack/main/windows/pack.toml"


def run_sh(home, extra=None):
    env = {"HOME": str(home), "PATH": os.environ["PATH"], "BAT_NO_DOWNLOAD": "1", "BAT_NO_LAUNCH": "1",
           "BAT_ASSETS": str(ROOT / "installer" / "brand")}
    env.update(extra or {})
    return subprocess.run(["bash", str(SH)], env=env, capture_output=True, text=True, timeout=60)


class LinuxScript(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.home = pathlib.Path(self.tmp.name)
        (self.home / "Desktop").mkdir()

    def tearDown(self):
        self.tmp.cleanup()

    def inst(self, data):
        return data / "instances" / INSTANCE

    def test_fresh_install_makes_the_instance_icon_and_shortcut(self):
        r = run_sh(self.home)
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        data = self.home / ".local/share/PrismLauncher"
        cfg = (self.inst(data) / "instance.cfg").read_text()
        self.assertIn("name=" + INSTANCE, cfg)
        self.assertIn("iconKey=lovish_cat", cfg)
        self.assertIn("packwiz-installer-bootstrap.jar " + PACK, cfg)
        self.assertIn("OverrideCommands=true", cfg)
        self.assertTrue((self.inst(data) / "mmc-pack.json").is_file())
        self.assertTrue((data / "icons" / "lovish_cat.png").is_file())
        desk = (self.home / ".local/share/applications/lovish.desktop").read_text()
        self.assertIn("Name=lovish", desk)
        self.assertIn(f'-l "{INSTANCE}"', desk)
        self.assertIn("Icon=" + str(data / "icons" / "lovish_cat.png"), desk)
        self.assertTrue((self.home / "Desktop" / "lovish.desktop").is_file())

    def test_uses_the_prism_people_already_have(self):
        # Prism installed through Flatpak: its data folder exists, the game goes there, the shortcut opens it
        flat = self.home / ".var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher"
        (flat / "instances").mkdir(parents=True)
        r = run_sh(self.home)
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertTrue((self.inst(flat) / "instance.cfg").is_file())
        self.assertFalse((self.home / ".local/share/PrismLauncher/instances" / INSTANCE).exists())
        desk = (self.home / ".local/share/applications/lovish.desktop").read_text()
        self.assertIn("flatpak run org.prismlauncher.PrismLauncher", desk)

    def test_rerun_keeps_the_instance_and_its_settings(self):
        run_sh(self.home)
        data = self.home / ".local/share/PrismLauncher"
        cfg = self.inst(data) / "instance.cfg"
        cfg.write_text(cfg.read_text().replace("iconKey=lovish_cat", "iconKey=default") + "JvmArgs=-Xmx8G\n")
        r = run_sh(self.home)
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        text = cfg.read_text()
        self.assertIn("JvmArgs=-Xmx8G", text)          # the player's own settings survive
        self.assertIn("iconKey=lovish_cat", text)       # but the cat comes back
        self.assertEqual(text.count("iconKey="), 1)

    def test_never_replaces_someone_elses_lovish_shortcut(self):
        apps = self.home / ".local/share/applications"
        apps.mkdir(parents=True)
        (apps / "lovish.desktop").write_text("[Desktop Entry]\nName=lovish\nExec=/home/x/server.sh\n")
        run_sh(self.home)
        self.assertIn("server.sh", (apps / "lovish.desktop").read_text())

    def test_prints_what_to_do_next(self):
        r = run_sh(self.home)
        self.assertIn("lovish", r.stdout)
        self.assertIn("Microsoft", r.stdout)


class WindowsScript(unittest.TestCase):
    """Same rules in install.ps1 (run for real on windows-latest in installer.yml)."""

    @classmethod
    def setUpClass(cls):
        cls.ps = PS1.read_text(encoding="utf-8")

    def test_finds_existing_prism_installs_first(self):
        for where in ("LOCALAPPDATA", "BeyondAndTabs", "APPDATA", "PrismLauncher"):
            self.assertIn(where, self.ps)

    def test_instance_matches_the_installer(self):
        self.assertIn("iconKey=lovish_cat", self.ps)
        self.assertIn("packwiz-installer-bootstrap.jar " + PACK, self.ps)
        self.assertIn(INSTANCE, self.ps)

    def test_lovish_shortcut_with_the_cat_and_guard(self):
        self.assertIn("lovish.lnk", self.ps)
        self.assertIn("lovish.ico", self.ps)
        self.assertIn("TargetPath -eq $exe", self.ps)

    def test_ci_switches(self):
        self.assertIn("BAT_NO_LAUNCH", self.ps)
        self.assertIn("BAT_ROOT", self.ps)


if __name__ == "__main__":
    unittest.main()
