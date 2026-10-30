"""pack/build_pack.py + pack/build_packwiz.py: nothing we ship to players may include Complementary Reimagined.
Run: python3 -m unittest discover -s tools -p 'test_*.py'

Oct 29: Complementary's author refused us permission to ship the shader pack (it had been in the tester pack since
Oct 22). The pack builders may not name it, may not add a shader pack or its settings, and Oculus (which only loaded
it) went too. Our own pack (shaderpacks/BeyondAndTabs, clean-room, GPL v2 or later; design/own_shader_pack.md) takes
its place, so Oculus is allowed again; Complementary stays banned by name anywhere in what we ship, and anything under
shaderpacks/ must be a byte-for-byte copy of a file of the repo's shaderpacks/BeyondAndTabs folder (no zip, no .txt
settings, no .pw.toml pointing at a download). Fully offline: the network steps (Modrinth lookups) are replaced by
fixed entries.
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
OUR_PACK = "shaderpacks/BeyondAndTabs/"
REPO = os.path.join(HERE, "..")
H512 = "a" * 128

ENTRIES = [("embeddium-0.3.31+mc1.20.1.jar", "client", "https://cdn.modrinth.com/data/x/versions/y/embeddium.jar", H512),
           ("spark-1.10.53-forge.jar", "both", "https://cdn.modrinth.com/data/x/versions/y/spark.jar", H512)]
OUR = ("beyondandtabs-test.jar", "https://example.invalid/beyondandtabs-test.jar", H512)
RP = (build_packwiz.RP_NAME, "https://example.invalid/rp.zip", H512)


def walk(root):
    """[(relative path, bytes)] of every file under root."""
    out = []
    for base, _, names in os.walk(root):
        for n in names:
            p = os.path.join(base, n)
            with open(p, "rb") as f:
                out.append((os.path.relpath(p, root).replace(os.sep, "/"), f.read()))
    return out


def shader_problems(files):
    """What is wrong with the shaderpacks/ part of a built pack ([(path, bytes)], paths relative to the instance):
    every file there must be a file of our pack, byte for byte as in the repo. A loose settings .txt, a zipped pack or
    a .pw.toml (a download, opaque to this check) is never ours, whatever it is named."""
    problems = []
    for rel, data in files:
        if not rel.startswith("shaderpacks/"):
            continue
        low = rel.lower()
        if low.endswith((".txt", ".pw.toml")) or ".zip" in low:
            problems.append(rel + ": not a plain file of our pack")
            continue
        if not rel.startswith(OUR_PACK):
            problems.append(rel + ": outside " + OUR_PACK)
            continue
        src = os.path.join(REPO, *rel.split("/"))
        if not os.path.isfile(src):
            problems.append(rel + ": not in the repo's pack")
            continue
        with open(src, "rb") as f:
            if f.read() != data:
                problems.append(rel + ": differs from the repo's copy")
    return problems


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

    def test_packwiz_builder_has_no_shader_download_code(self):
        # Complementary was fetched from Modrinth by these; our pack ships from the repo, never resolved or downloaded
        for name in ("resolve_shader", "shader_files"):
            self.assertFalse(hasattr(build_packwiz, name), name)


def our_file(rel):
    with open(os.path.join(REPO, *rel.split("/")), "rb") as f:
        return f.read()


class ShaderFolderRule(unittest.TestCase):
    """shader_problems itself: our files pass, the ways a foreign pack could slip in under our name do not."""
    SETTINGS = b"RP_MODE=1\nTOGGLE_VL_FOG=true\nBorderFog=false\n"
    GLSL = b"#version 120\nuniform sampler2D colortex0;\nvoid main() { gl_FragColor = vec4(1.0); }\n"

    def test_a_copy_of_our_pack_passes(self):
        rels = ["shaderpacks/BeyondAndTabs/LICENSE", "shaderpacks/BeyondAndTabs/shaders/final.fsh"]
        self.assertEqual(shader_problems([(r, our_file(r)) for r in rels] + [("options.txt", b"x")]), [])

    def test_a_settings_txt_named_like_ours_fails(self):
        self.assertTrue(shader_problems([("shaderpacks/BeyondAndTabs.txt", self.SETTINGS)]))
        self.assertTrue(shader_problems([("shaderpacks/BeyondAndTabs/x.txt", self.SETTINGS)]))

    def test_a_zip_or_its_metafile_named_like_ours_fails(self):
        meta = b'name = "x"\n[download]\nurl = "https://example.invalid/a"\n'
        self.assertTrue(shader_problems([("shaderpacks/BeyondAndTabs-r5.9.3.zip.pw.toml", meta)]))
        self.assertTrue(shader_problems([("shaderpacks/BeyondAndTabs.zip", b"PK")]))
        self.assertTrue(shader_problems([("shaderpacks/BeyondAndTabs/shaders/a.fsh.pw.toml", meta)]))

    def test_foreign_glsl_in_our_folder_fails(self):
        self.assertTrue(shader_problems([("shaderpacks/BeyondAndTabs/shaders/composite1.fsh", self.GLSL)]))
        self.assertTrue(shader_problems([("shaderpacks/BeyondAndTabs/shaders/final.fsh", self.GLSL)]))

    def test_a_folder_next_to_ours_fails(self):
        self.assertTrue(shader_problems([("shaderpacks/BeyondAndTabsX/shaders/final.fsh",
                                          our_file("shaderpacks/BeyondAndTabs/shaders/final.fsh"))]))


class PackwizPack(unittest.TestCase):
    def build(self, skip):
        with tempfile.TemporaryDirectory() as tmp:
            n = build_packwiz.build_variant(tmp, ENTRIES, OUR, RP, "test", skip)
            files = walk(tmp)
        self.assertEqual(n, len(files) - 2)   # the pack files plus index.toml and pack.toml
        return files

    def check(self, files):
        for rel, data in files:
            text = data.decode("utf-8", "replace")
            for b in BANNED:
                self.assertNotIn(b, rel.lower(), rel)
                self.assertNotIn(b, text.lower(), rel)
        self.assertEqual(shader_problems(files), [])
        paths = {rel for rel, _ in files}
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
                # overrides are copied into the instance as they are: the shaderpacks/ rule applies to them
                overrides = [(n.split("/", 1)[1], z.read(n)) for n in z.namelist()
                             if n.startswith(("overrides/", "client-overrides/")) and not n.endswith("/")]
                self.assertEqual(shader_problems(overrides), [])
                raw = z.read("modrinth.index.json").decode()
                index = raw.lower()
        # an index entry is a download, never a file of ours
        self.assertEqual([f["path"] for f in json.loads(raw)["files"] if f["path"].startswith("shaderpacks/")], [])
        for b in BANNED:
            self.assertNotIn(b, index)
            self.assertFalse(any(b in r for r in requested), requested)


if __name__ == "__main__":
    unittest.main()
