"""pack/build_pack.py + pack/build_packwiz.py: nothing we ship to players may include Complementary Reimagined.
Run: python3 -m unittest discover -s tools -p 'test_*.py'

Oct 29: Complementary's author refused us permission to ship the shader pack (it had been in the tester pack since
Oct 22). The pack builders may not name it, may not add a shader pack or its settings, and Oculus (which only loaded
it) went too. Our own pack (shaderpacks/BeyondAndTabs, clean-room, GPL v2 or later; design/own_shader_pack.md) takes
its place, so Oculus and a shaderpacks/ folder are allowed again; only Complementary is banned, by name anywhere in
what we ship, and a shaderpacks/ folder may hold nothing but ours. Fully offline: the network steps (Modrinth lookups)
are replaced by fixed entries.
"""
import hashlib
import io
import json
import os
import sys
import tempfile
import unittest
import zipfile
from unittest import mock

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "pack"))
import build_pack  # noqa: E402
import build_packwiz  # noqa: E402

BANNED = ("complementary",)
OUR_PACK = "shaderpacks/BeyondAndTabs"
H512 = "a" * 128

ENTRIES = [("embeddium-0.3.31+mc1.20.1.jar", "client", "https://cdn.modrinth.com/data/x/versions/y/embeddium.jar", H512),
           ("spark-1.10.53-forge.jar", "both", "https://cdn.modrinth.com/data/x/versions/y/spark.jar", H512)]
OUR = ("beyondandtabs-test.jar", "https://example.invalid/beyondandtabs-test.jar", H512)
RP = (build_packwiz.RP_NAME, "https://example.invalid/rp.zip", H512)


def walk(root):
    """[(relative path, text)] of every file under root."""
    out = []
    for base, _, names in os.walk(root):
        for n in names:
            p = os.path.join(base, n)
            with open(p, encoding="utf-8") as f:
                out.append((os.path.relpath(p, root).replace(os.sep, "/"), f.read()))
    return out


class BuilderDefinitions(unittest.TestCase):
    def test_no_shader_project_in_the_mod_lists(self):
        names = [*build_pack.EXTRA_PROJECTS, *build_pack.PINNED_VERSIONS, *build_pack.CLIENT_ONLY_PROJECTS,
                 *build_pack.CLIENT_ONLY]
        for n in names:
            self.assertNotIn("complementary", n.lower())

    def test_instance_mods_have_none(self):
        with open(os.path.join(HERE, "..", "pack", "instance_mods.json"), encoding="utf-8") as f:
            for m in json.load(f):
                self.assertNotIn("complementary", m["file"].lower())

    def test_packwiz_builder_names_no_complementary(self):
        for name in ("SHADER_PROJECT", "SHADER_VERSION"):
            self.assertNotIn("complementary", str(getattr(build_packwiz, name, "")).lower(), name)


class PackwizPack(unittest.TestCase):
    def build(self, skip):
        with tempfile.TemporaryDirectory() as tmp:
            n = build_packwiz.build_variant(tmp, ENTRIES, OUR, RP, "test", skip)
            files = walk(tmp)
        self.assertEqual(n, len(files) - 2)   # the pack files plus index.toml and pack.toml
        return files

    def check(self, files):
        for rel, text in files:
            for b in BANNED:
                self.assertNotIn(b, rel.lower(), rel)
                self.assertNotIn(b, text.lower(), rel)
        paths = {rel for rel, _ in files}
        others = [p for p in paths if p.startswith("shaderpacks/") and not p.startswith(OUR_PACK)]
        self.assertEqual(others, [])
        self.assertIn("mods/beyondandtabs.pw.toml", paths)   # the pack is not just empty
        self.assertIn("options.txt", paths)

    def test_windows_pack(self):
        self.check(self.build(()))

    def test_mac_pack(self):
        self.check(self.build(build_pack.MAC_SKIP))


class MrpackFile(unittest.TestCase):
    """build_pack.main with a fake Modrinth that has every project asked for: whatever the builder requests ends up in
    the .mrpack, so a shader project left in EXTRA_PROJECTS shows up here."""

    def fake_modrinth(self, requested):
        sha1, sha512 = hashlib.sha1(b"jar").hexdigest(), hashlib.sha512(b"jar").hexdigest()

        def get_json(url):
            slug = url.split("/project/")[1].split("/")[0]
            requested.append(slug)
            return [{"project_id": slug, "version_number": build_pack.PINNED_VERSIONS.get(slug, "1"),
                     "date_published": "2026-10-01", "version_type": "release", "dependencies": [],
                     "files": [{"primary": True, "filename": slug + ".jar", "url": "https://cdn.modrinth.com/" + slug,
                                "hashes": {"sha1": sha1, "sha512": sha512}}]}]

        def download(url, dest):
            with open(dest, "wb") as f:
                f.write(b"jar")

        return get_json, download

    def test_mrpack_has_no_complementary(self):
        requested = []
        get_json, download = self.fake_modrinth(requested)
        with tempfile.TemporaryDirectory() as tmp:
            jar = os.path.join(tmp, "x.jar")
            with open(jar, "wb") as f:
                f.write(b"jar")
            out = os.path.join(tmp, "out")
            with mock.patch.object(build_pack, "resolve_instance_mods", return_value=([], set())),                     mock.patch.object(build_pack, "get_json", get_json),                     mock.patch.object(build_pack, "download", download),                     mock.patch.object(sys, "argv", ["build_pack.py", jar, out]),                     mock.patch("sys.stdout", new=io.StringIO()):
                build_pack.main()
            self.assertIn("spark", requested)   # the fake really drove the project list
            with zipfile.ZipFile(os.path.join(out, "BeyondAndTabs-test.mrpack")) as z:
                for name in z.namelist():
                    self.assertFalse(any(b in name.lower() for b in BANNED), name)
                index = z.read("modrinth.index.json").decode().lower()
        for b in BANNED:
            self.assertNotIn(b, index)
            self.assertFalse(any(b in r for r in requested), requested)


if __name__ == "__main__":
    unittest.main()
