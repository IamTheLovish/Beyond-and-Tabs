"""publish-pack.yml's fresh-install test accepts our shader pack ON on Windows and nothing on Mac (#554).

Eris.15 (#554 review r1): the old guard ("pack has a shader pack" for any shaderpacks folder) failed every publish from
main once our pack shipped, after the push, so the keep-last-3 asset cleanup was skipped. The step now runs
pack/check_tester_install.py for both variants. These tests build fake installs from the real pack builder
(pack/build_packwiz.py, as packwiz-installer would lay its files out) and check the guard on them. Fully offline.
Run: python3 -m unittest tools/test_pack_tester_install.py
"""
import os
import re
import shutil
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.join(HERE, "..")
sys.path.insert(0, os.path.join(REPO, "pack"))
import build_pack  # noqa: E402
import build_packwiz  # noqa: E402
import check_tester_install as cti  # noqa: E402

REPO_PACK = os.path.join(REPO, "shaderpacks", "BeyondAndTabs")
H512 = "a" * 128
ENTRIES = [("embeddium-0.3.31+mc1.20.1.jar", "client", "https://cdn.modrinth.com/data/x/versions/y/embeddium.jar", H512),
           ("oculus-mc1.20.1-1.8.0.jar", "client", "https://cdn.modrinth.com/data/x/versions/y/oculus.jar", H512)]
OUR = ("beyondandtabs-test.jar", "https://example.invalid/beyondandtabs-test.jar", H512)
RP = (build_packwiz.RP_NAME, "https://example.invalid/rp.zip", H512)


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(data)


class Install(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.tmp)

    def install(self, variant):
        """A fresh instance's minecraft dir: the files the real builder puts in the variant's pack, as installed."""
        pack = os.path.join(self.tmp, "pack-" + variant)
        build_packwiz.build_variant(pack, ENTRIES, OUR, RP, "test", build_pack.MAC_SKIP if variant == "mac" else ())
        inst = os.path.join(self.tmp, "inst-" + variant)
        for base, _, names in os.walk(pack):
            for n in names:
                p = os.path.join(base, n)
                rel = os.path.relpath(p, pack)
                if rel.endswith(".pw.toml") or rel in ("pack.toml", "index.toml"):
                    continue                     # packwiz metafiles: the installer downloads these, they are not files
                with open(p, "rb") as f:
                    write(os.path.join(inst, rel), f.read())
        return inst

    def errors(self, variant, inst):
        return cti.check(variant, inst, REPO_PACK)

    def test_the_real_windows_and_mac_installs_pass(self):
        self.assertEqual(self.errors("windows", self.install("windows")), [])
        self.assertEqual(self.errors("mac", self.install("mac")), [])

    def test_windows_needs_exactly_our_pack(self):
        inst = self.install("windows")
        sp = os.path.join(inst, "shaderpacks")
        write(os.path.join(sp, "Other.zip"), b"PK")                                      # another pack
        self.assertTrue(any("Other.zip" in e for e in self.errors("windows", inst)))
        os.remove(os.path.join(sp, "Other.zip"))
        props = os.path.join(sp, "BeyondAndTabs", "shaders", "shaders.properties")
        with open(props, "rb") as f:
            good = f.read()
        write(props, good + b"\n# changed\n")                                           # a file not byte for byte
        self.assertTrue(any("differs" in e for e in self.errors("windows", inst)))
        os.remove(props)                                                                 # a file missing
        self.assertTrue(any("missing" in e for e in self.errors("windows", inst)))
        shutil.rmtree(sp)                                                                # no pack at all
        self.assertTrue(self.errors("windows", inst))

    def test_windows_needs_the_config_on_with_our_pack(self):
        for cfg in cti.CONFIGS:
            for text in (b"enableShaders=false\nshaderPack=BeyondAndTabs\n", b"enableShaders=true\nshaderPack=Other\n",
                         None):
                inst = self.install("windows")
                path = os.path.join(inst, cfg)
                if text is None:
                    os.remove(path)
                else:
                    write(path, text)
                self.assertTrue(any(cfg in e for e in self.errors("windows", inst)), (cfg, text))
                shutil.rmtree(inst)

    def test_mac_has_neither(self):
        inst = self.install("mac")
        write(os.path.join(inst, "shaderpacks", "BeyondAndTabs", "shaders", "final.fsh"), b"void main() {}")
        self.assertTrue(self.errors("mac", inst))
        inst = self.install("mac")
        shutil.rmtree(os.path.join(inst, "shaderpacks"), ignore_errors=True)
        write(os.path.join(inst, "config", "oculus.properties"), b"enableShaders=true\n")
        self.assertTrue(any("oculus.properties" in e for e in self.errors("mac", inst)))

    def test_the_workflow_step_runs_this_check_for_both_variants(self):
        # the old guard failed any install with a shader pack: the Windows pack, which now has ours, could never pass
        with open(os.path.join(REPO, ".github", "workflows", "publish-pack.yml"), encoding="utf-8") as f:
            yml = f.read()
        step = yml.split("- name: Test - a fresh tester install gets this exact build")[1].split("\n      - name:")[0]
        self.assertIn("for variant in windows mac; do", step)
        self.assertIn('python3 "$GITHUB_WORKSPACE/pack/check_tester_install.py" "$variant" . '
                      '"$GITHUB_WORKSPACE/shaderpacks/BeyondAndTabs"', step)
        self.assertNotRegex(step, re.compile(r"\[ -d shaderpacks \]"))
        self.assertNotIn("pack has a shader pack\"", step)
        # the Complementary guard stays
        self.assertIn("grep -rIqi complementary .", step)


if __name__ == "__main__":
    unittest.main()
