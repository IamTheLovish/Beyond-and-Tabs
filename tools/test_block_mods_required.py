"""Every building-block mod we require is wired in every place at once (PR #398 added FramedBlocks + Domum Ornamentum
this way; this test keeps the next ones from forgetting a spot):

- bar/build.gradle: a runtimeOnly pin (the GameTest server and runClient load it),
- mods.toml: a mandatory dependency, side BOTH, ordering AFTER, with a versionRange,
- pack/instance_mods.json: the pinned jar (size, sha1, sha512) every tester pack / server / packwiz builder resolves,
- pack/build_pack.py REQUIRED_FILES: a pack without it stops the build,
- BuildingBlockModGameTests: REQUIRED_MODS (loaded), KNOWN_BLOCKS / MIN_BLOCKS (has its blocks), and the block-id dump
  namespaces, so its ids reach branch block-ids.

Licence rule (lovish, Oct 30 2026: "no weird license stuff"): only mods whose code AND assets are permissive. Brick &
Mortar is NOT on this list - its README says "Asset license (ARR)" - so the test also fails if it ever slips in.
"""
import json
import os
import re
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")
GRADLE = open(os.path.join(ROOT, "bar", "build.gradle")).read()
MODS_TOML = open(os.path.join(ROOT, "bar", "src", "main", "resources", "META-INF", "mods.toml")).read()
GAMETEST = open(os.path.join(ROOT, "bar", "src", "main", "java", "com", "solegendary", "reignofnether", "gametest",
                             "BuildingBlockModGameTests.java")).read()
INSTANCE = json.load(open(os.path.join(ROOT, "pack", "instance_mods.json")))
sys.path.insert(0, os.path.join(ROOT, "pack"))
import build_pack  # noqa: E402

# name -> (gradle pin, mods.toml modId, jar file prefix, dump namespace as DUMP_MODS names it)
REQUIRED = {
    "FramedBlocks": ("maven.modrinth:framedblocks:lSiHA1qU", "framedblocks", "FramedBlocks-", "framedblocks"),
    "Domum Ornamentum": ("curse.maven:domum-ornamentum-527361:8765962", "domum_ornamentum", "domum_ornamentum-",
                         "domum_ornamentum"),
    "Macaw's Trapdoors 1.1.5": ("maven.modrinth:macaws-trapdoors:5B4awHaA", "mcwtrpdoors",
                                "mcw-trapdoors-1.1.5-", "mcw"),
}
NOT_ALLOWED = {"Brick & Mortar": ("brick-and-mortar", "brick_and_mortar")}   # assets All Rights Reserved


def toml_block(mod_id):
    m = re.search(r'\[\[dependencies\.reignofnether\]\]\s*\n\s*modId="%s"(.*?)(?=\n#|\n\[\[|\Z)' % re.escape(mod_id),
                  MODS_TOML, re.S)
    return m.group(1) if m else None


class BlockModsRequired(unittest.TestCase):
    def each(self):
        for name, (pin, mod_id, prefix, ns) in REQUIRED.items():
            with self.subTest(mod=name):
                yield name, pin, mod_id, prefix, ns

    def test_gradle_runtimeonly_pin(self):
        for name, pin, *_ in self.each():
            self.assertIn("runtimeOnly fg.deobf('%s')" % pin, GRADLE, name + " is not a runtimeOnly pin")

    def test_mods_toml_mandatory_both_after_with_range(self):
        for name, _, mod_id, *_ in self.each():
            body = toml_block(mod_id)
            self.assertIsNotNone(body, "%s: no [[dependencies.reignofnether]] with modId=%s" % (name, mod_id))
            self.assertIn("mandatory=true", body)
            self.assertIn('side="BOTH"', body)
            self.assertIn('ordering="AFTER"', body)
            self.assertRegex(body, r'versionRange="[\[(][^"]*[\])]"')

    def test_instance_mods_pins_the_jar_with_hashes(self):
        for name, _, _, prefix, _ in self.each():
            hits = [m for m in INSTANCE if m["file"].startswith(prefix)]
            self.assertEqual(len(hits), 1, "%s: need exactly one pack/instance_mods.json file '%s*'" % (name, prefix))
            m = hits[0]
            self.assertGreater(m["size"], 0)
            self.assertRegex(m["sha1"], r"^[0-9a-f]{40}$")
            self.assertRegex(m["sha512"], r"^[0-9a-f]{128}$")

    def test_pack_builder_requires_the_file(self):
        for name, _, _, prefix, _ in self.each():
            self.assertTrue(any(prefix.startswith(p) or p.startswith(prefix) for p in build_pack.REQUIRED_FILES),
                            "%s: build_pack.REQUIRED_FILES has no prefix for '%s'" % (name, prefix))

    def test_gametest_loads_checks_and_dumps_it(self):
        required = re.search(r"REQUIRED_MODS\s*=\s*List\.of\((.*?)\);", GAMETEST, re.S).group(1)
        dump = re.search(r"DUMP_MODS\s*=\s*List\.of\((.*?)\);", GAMETEST, re.S).group(1)
        for name, _, mod_id, _, ns in self.each():
            self.assertIn('"%s"' % mod_id, required, name + " is not in REQUIRED_MODS")
            self.assertIn('KNOWN_BLOCKS.put("%s"' % mod_id, GAMETEST, name + " has no KNOWN_BLOCKS entry")
            self.assertIn('MIN_BLOCKS.put("%s"' % mod_id, GAMETEST, name + " has no MIN_BLOCKS entry")
            self.assertIn('"%s"' % ns, dump, name + " is not in the block-id dump namespaces (DUMP_MODS)")

    def test_mod_with_arr_assets_stays_out(self):
        for name, (slug, mod_id) in NOT_ALLOWED.items():
            self.assertNotIn(slug, GRADLE, name)
            self.assertIsNone(toml_block(mod_id), name)
            self.assertFalse([m for m in INSTANCE if m["file"].lower().startswith(mod_id)], name)


if __name__ == "__main__":
    unittest.main()
