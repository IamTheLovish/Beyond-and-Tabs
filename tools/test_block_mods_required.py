"""FramedBlocks and Domum Ornamentum are required building-block mods, and CI dumps every building-block id.
Run: python3 -m unittest discover -s tools -p 'test_*.py'

lovish, Oct 29 2026 ~17:05: buildings "look blocky and toy" - he approved FramedBlocks as a required mod and Domum
Ornamentum (shingles, timber framing) for Brassworks. A required mod must be required the same way as Macaw's:
mandatory in mods.toml, pinned on the GameTest/runClient classpath in bar/build.gradle (runtimeOnly, never bundled),
and delivered by the tester packs - or a player's game stops at Forge's "missing mods" screen. And the block-id dump
(BuildingBlockModGameTests -> tools/block_ids.py -> branch `block-ids`) lets later building rounds use every id and
blockstate without lovish's PC. Offline: no network, no Gradle.
"""
import hashlib
import importlib
import io
import json
import os
import re
import sys
import tempfile
import tomllib
import unittest
import zipfile
from unittest import mock

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")
MODS_TOML = os.path.join(ROOT, "bar/src/main/resources/META-INF/mods.toml")
GRADLE = os.path.join(ROOT, "bar/build.gradle")
INSTANCE = os.path.join(ROOT, "pack/instance_mods.json")
WORKFLOW = os.path.join(ROOT, ".github/workflows/build.yml")
GAMETEST = os.path.join(ROOT, "bar/src/main/java/com/solegendary/reignofnether/gametest/BuildingBlockModGameTests.java")
DOMUM_CF_PROJECT = 527361
sys.path.insert(0, os.path.join(ROOT, "pack"))
sys.path.insert(0, HERE)


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def dependencies():
    return {d["modId"]: d for d in tomllib.loads(read(MODS_TOML))["dependencies"]["reignofnether"]}


def gradle_runtime_pins():
    """'group:artifact:version' of every `runtimeOnly fg.deobf('...')` line that is not commented out."""
    pins = []
    for line in read(GRADLE).splitlines():
        m = re.match(r"""\s*runtimeOnly\s+fg\.deobf\(['"]([^'"]+)['"]\)""", line)
        if m:
            pins.append(m.group(1))
    return pins


class RequiredInModsToml(unittest.TestCase):
    def check_like_macaws(self, mod_id):
        deps = dependencies()
        self.assertIn("mcwroofs", deps)   # the reference: Macaw's Roofs
        self.assertIn(mod_id, deps, f"{mod_id} is not a dependency in mods.toml")
        d = deps[mod_id]
        self.assertIs(d.get("mandatory"), True, f"{mod_id} must be mandatory=true")
        for key in ("side", "ordering"):
            self.assertEqual(d.get(key), deps["mcwroofs"][key], f"{mod_id} {key}")
        self.assertTrue(d.get("versionRange"), f"{mod_id} needs a versionRange")

    def test_framedblocks_is_mandatory(self):
        self.check_like_macaws("framedblocks")

    def test_domum_ornamentum_is_mandatory(self):
        self.check_like_macaws("domum_ornamentum")


class PinnedInGradle(unittest.TestCase):
    def test_macaws_pattern_still_there(self):
        self.assertTrue(any(p.startswith("maven.modrinth:macaws-roofs:") for p in gradle_runtime_pins()))

    def test_framedblocks_pinned_from_modrinth(self):
        pins = [p for p in gradle_runtime_pins() if p.startswith("maven.modrinth:framedblocks:")]
        self.assertEqual(len(pins), 1, "one runtimeOnly fg.deobf('maven.modrinth:framedblocks:<version id>')")
        self.assertRegex(pins[0].split(":")[2], r"^[A-Za-z0-9]{8}$")   # a Modrinth version id, not a range

    def test_domum_pinned_by_curseforge_file(self):
        # not on Modrinth: CurseForge file id through cursemaven (the repository build.gradle already declares)
        self.assertIn('includeGroup "curse.maven"', read(GRADLE))
        pins = [p for p in gradle_runtime_pins() if p.startswith(f"curse.maven:domum-ornamentum-{DOMUM_CF_PROJECT}:")]
        self.assertEqual(len(pins), 1, "one runtimeOnly fg.deobf('curse.maven:domum-ornamentum-527361:<file id>')")
        self.assertRegex(pins[0].split(":")[2], r"^\d{6,9}$")

    def test_never_bundled(self):
        text = read(GRADLE)
        for name in ("framedblocks", "domum"):
            for line in text.splitlines():
                if name in line.lower() and not line.strip().startswith("//"):
                    self.assertNotRegex(line, r"jarJar|shade|implementation|include ", line)


class DeliveredByThePacks(unittest.TestCase):
    def test_framedblocks_in_the_instance_list_and_required(self):
        build_pack = importlib.import_module("build_pack")
        self.assertIn("FramedBlocks-", build_pack.REQUIRED_FILES)
        files = [m["file"] for m in json.loads(read(INSTANCE))]
        self.assertTrue(any(f.startswith("FramedBlocks-") for f in files), files)

    def test_domum_is_a_pinned_curseforge_file_matching_gradle(self):
        build_pack = importlib.import_module("build_pack")
        cf = [f for f in build_pack.CURSEFORGE_FILES if f["project_id"] == DOMUM_CF_PROJECT]
        self.assertEqual(len(cf), 1)
        pin = next(p for p in gradle_runtime_pins() if p.startswith("curse.maven:domum-ornamentum-"))
        self.assertEqual(str(cf[0]["file_id"]), pin.split(":")[2], "pack and build.gradle pin different files")
        self.assertTrue(cf[0]["file"].endswith(".jar"))
        url = build_pack.curseforge_url(cf[0])
        self.assertTrue(url.startswith("https://mediafilez.forgecdn.net/files/"), url)
        self.assertTrue(url.endswith("/" + cf[0]["file"]), url)

    def test_forgecdn_path_rule(self):
        build_pack = importlib.import_module("build_pack")
        f = {"project_id": 1, "file_id": 5505046, "file": "x.jar"}
        self.assertEqual(build_pack.curseforge_url(f), "https://mediafilez.forgecdn.net/files/5505/46/x.jar")

    def test_mrpack_carries_domum_and_still_refuses_other_hosts(self):
        build_pack = importlib.import_module("build_pack")
        sha1, sha512 = hashlib.sha1(b"jar").hexdigest(), hashlib.sha512(b"jar").hexdigest()
        downloaded = []

        def get_json(url):
            slug = url.split("/project/")[1].split("/")[0]
            return [{"project_id": slug, "version_number": build_pack.PINNED_VERSIONS.get(slug, "1"),
                     "date_published": "2026-10-01", "version_type": "release", "dependencies": [],
                     "files": [{"primary": True, "filename": slug + ".jar", "url": "https://cdn.modrinth.com/" + slug,
                                "hashes": {"sha1": sha1, "sha512": sha512}}]}]

        def download(url, dest):
            downloaded.append(url)
            with open(dest, "wb") as f:
                f.write(b"jar")

        with tempfile.TemporaryDirectory() as tmp:
            jar = os.path.join(tmp, "x.jar")
            with open(jar, "wb") as f:
                f.write(b"jar")
            out = os.path.join(tmp, "out")
            with mock.patch.object(build_pack, "resolve_instance_mods", return_value=([], set())), \
                    mock.patch.object(build_pack, "get_json", get_json), \
                    mock.patch.object(build_pack, "download", download), \
                    mock.patch.object(sys, "argv", ["build_pack.py", jar, out]), \
                    mock.patch("sys.stdout", new=io.StringIO()):
                build_pack.main()
            with zipfile.ZipFile(os.path.join(out, "BeyondAndTabs-test.mrpack")) as z:
                index = json.loads(z.read("modrinth.index.json"))
        domum = [f for f in index["files"] if "domum_ornamentum" in f["path"]]
        self.assertEqual(len(domum), 1, [f["path"] for f in index["files"]])
        self.assertEqual(domum[0]["env"], {"client": "required", "server": "required"})
        self.assertEqual(domum[0]["hashes"]["sha512"], sha512)
        self.assertTrue(domum[0]["downloads"][0].startswith("https://mediafilez.forgecdn.net/"))
        self.assertIn(domum[0]["downloads"][0], downloaded)   # hashed from the real download, not trusted blind
        # any other non-Modrinth host is still refused
        self.assertFalse(build_pack.allowed_download("https://mediafilez.forgecdn.net/files/1/2/other.jar"))
        self.assertFalse(build_pack.allowed_download("https://example.com/x.jar"))
        self.assertTrue(build_pack.allowed_download("https://cdn.modrinth.com/data/x/versions/y/z.jar"))


class GameTestRequiresThem(unittest.TestCase):
    def test_required_mods_list(self):
        m = re.search(r"REQUIRED_MODS\s*=\s*List\.of\(([^)]*)\)", read(GAMETEST), re.S)
        self.assertIsNotNone(m)
        for mod in ("framedblocks", "domum_ornamentum"):
            self.assertIn(f'"{mod}"', m.group(1))


def fixture(tmp, counts=None, mutate=None):
    """A minimal valid dump: one file per required namespace plus counts.json."""
    block_ids = importlib.import_module("block_ids")
    counts = counts or {ns: 1 for ns in block_ids.REQUIRED_NAMESPACES}
    for ns, n in counts.items():
        blocks = {f"{ns}:b{i}": {"properties": {"facing": ["north", "south"], "waterlogged": ["false", "true"]},
                                 "default": {"facing": "north", "waterlogged": "false"}, "states": 4,
                                 "block_entity": False} for i in range(n)}
        doc = {"namespace": ns, "mod_version": "1.0", "count": n, "blocks": blocks}
        if mutate:
            mutate(ns, doc)
        with open(os.path.join(tmp, ns + ".json"), "w") as f:
            json.dump(doc, f)
    with open(os.path.join(tmp, "counts.json"), "w") as f:
        json.dump({"minecraft": "1.20.1", "total": sum(counts.values()), "namespaces": counts}, f)


class BlockIdDump(unittest.TestCase):
    def test_required_namespaces(self):
        block_ids = importlib.import_module("block_ids")
        for ns in ("mcwroofs", "mcwwindows", "mcwfences", "chipped", "framedblocks", "domum_ornamentum"):
            self.assertIn(ns, block_ids.REQUIRED_NAMESPACES)

    def test_valid_dump_passes(self):
        block_ids = importlib.import_module("block_ids")
        with tempfile.TemporaryDirectory() as tmp:
            fixture(tmp)
            self.assertEqual(block_ids.validate(tmp), [])

    def test_zero_count_fails(self):
        block_ids = importlib.import_module("block_ids")
        with tempfile.TemporaryDirectory() as tmp:
            counts = {ns: 1 for ns in block_ids.REQUIRED_NAMESPACES}
            counts["framedblocks"] = 0
            fixture(tmp, counts)
            self.assertTrue(any("framedblocks" in p for p in block_ids.validate(tmp)))

    def test_missing_namespace_fails(self):
        block_ids = importlib.import_module("block_ids")
        with tempfile.TemporaryDirectory() as tmp:
            fixture(tmp)
            os.remove(os.path.join(tmp, "domum_ornamentum.json"))
            self.assertTrue(any("domum_ornamentum" in p for p in block_ids.validate(tmp)))

    def test_bad_state_fails(self):
        block_ids = importlib.import_module("block_ids")

        def bad_default(ns, doc):
            if ns == "chipped":
                next(iter(doc["blocks"].values()))["default"]["facing"] = "up"   # not an allowed value

        def wrong_ns(ns, doc):
            if ns == "mcwroofs":
                doc["blocks"]["minecraft:stone"] = next(iter(doc["blocks"].values()))
                doc["count"] += 1

        for mutate, word in ((bad_default, "chipped"), (wrong_ns, "minecraft:stone")):
            with tempfile.TemporaryDirectory() as tmp:
                fixture(tmp, mutate=mutate)
                self.assertTrue(any(word in p for p in block_ids.validate(tmp)), word)

    def test_count_mismatch_fails(self):
        block_ids = importlib.import_module("block_ids")

        def miscount(ns, doc):
            if ns == "mcwwindows":
                doc["count"] = 7

        with tempfile.TemporaryDirectory() as tmp:
            fixture(tmp, mutate=miscount)
            self.assertTrue(any("mcwwindows" in p for p in block_ids.validate(tmp)))

    def test_ci_dumps_and_publishes(self):
        wf = read(WORKFLOW)
        # the GameTest job that boots the server (shard 1, job `bar`) validates the dump and pushes branch block-ids
        bar_job = wf.split("\n  bar:\n", 1)[1].split("\n  bar-tests:\n", 1)[0]
        self.assertIn("tools/block_ids.py", bar_job)
        self.assertIn("block-ids", bar_job)
        self.assertLess(bar_job.index("ci_gametests.sh"), bar_job.index("tools/block_ids.py"))
        java = read(GAMETEST)
        self.assertIn('"block_ids"', java)          # the dump directory under run-gametest/
        self.assertIn("counts.json", java)


if __name__ == "__main__":
    unittest.main()
