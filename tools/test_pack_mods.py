#!/usr/bin/env python3
"""Tests for the tester pack's mod list and shipped configs (pack/build_pack.py, pack/build_packwiz.py).

lovish, Oct 28: This Rocks!, Particular Reforged, Immersive Weathering and Better Biome Blend go in the tester pack
(never in our jar). Rules the list must keep:
  - one mod makes falling leaves (Particular's, per leaf type), so fallingleavesforge is gone;
  - Immersive Weathering ships a config that stops block ticks changing a map mid-match;
  - every pin / client-only entry names a project that is in EXTRA_PROJECTS;
  - the shipped configs reach the packwiz pack as "preserve" files and the mrpack as overrides.

Offline: Modrinth is not reached.

    python3 tools/test_pack_mods.py
"""
import json
import os
import pathlib
import sys
import tempfile
import tomllib
import unittest
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "pack"))
import build_pack  # noqa: E402
import build_packwiz  # noqa: E402

THIS_ROCKS = "nsHXyrFh"   # Modrinth project id of "This Rocks! (Forge)" (MIT)
NEW = (THIS_ROCKS, "particular-reforged", "immersive-weathering", "better-biome-blend")
# Oct 28 second batch (lovish ~06:30). Flutter, Windblown and RLFoliage (Ported) are CurseForge-only (no Modrinth project;
# the pack builder resolves from Modrinth only, so they are left out, not hand-added).
CRITTERS = ("cosy-critters", "light-dust")
CURSEFORGE_ONLY = ("flutter", "windblown", "rlfoliage-ported")


class ModList(unittest.TestCase):
    def test_the_four_landscape_mods_are_in_the_pack(self):
        for p in NEW:
            self.assertIn(p, build_pack.EXTRA_PROJECTS)

    def test_the_ambient_mods_are_in_the_pack_and_client_only(self):
        for p in CRITTERS:
            self.assertIn(p, build_pack.EXTRA_PROJECTS)
            self.assertIn(p, build_pack.PINNED_VERSIONS)
            self.assertIn(p, build_pack.CLIENT_ONLY_PROJECTS)

    def test_curseforge_only_mods_are_not_hand_added(self):
        for p in CURSEFORGE_ONLY:
            self.assertNotIn(p, build_pack.EXTRA_PROJECTS)

    def test_every_new_mod_is_pinned(self):
        for p in NEW:
            self.assertIn(p, build_pack.PINNED_VERSIONS, p + " must be pinned to a vetted version")

    def test_only_one_mod_makes_falling_leaves(self):
        self.assertNotIn("fallingleavesforge", build_pack.EXTRA_PROJECTS)
        self.assertNotIn("fallingleavesforge", build_pack.PINNED_VERSIONS)
        self.assertNotIn("fallingleavesforge", build_pack.CLIENT_ONLY_PROJECTS)

    def test_client_only_and_server_sides(self):
        for p in ("particular-reforged", "better-biome-blend"):
            self.assertIn(p, build_pack.CLIENT_ONLY_PROJECTS)
        # these add blocks: a server without them could not load the world
        for p in (THIS_ROCKS, "immersive-weathering"):
            self.assertNotIn(p, build_pack.CLIENT_ONLY_PROJECTS)

    def test_pins_and_client_only_name_projects_that_are_in_the_pack(self):
        for p in build_pack.PINNED_VERSIONS:
            self.assertIn(p, build_pack.EXTRA_PROJECTS)
        for p in build_pack.CLIENT_ONLY_PROJECTS:
            self.assertIn(p, build_pack.EXTRA_PROJECTS)

    def test_none_of_the_four_needs_embeddium_so_the_mac_list_is_unchanged(self):
        self.assertEqual(set(build_pack.MAC_SKIP), {"embeddium", "immediatelyfast", "rubidium-extra", "oculus"})


class ShippedConfigs(unittest.TestCase):
    def parsed(self, name):
        return tomllib.loads(build_pack.PACK_CONFIGS[name])

    def test_every_config_is_valid_toml_under_config(self):
        self.assertTrue(build_pack.PACK_CONFIGS)
        for name, text in build_pack.PACK_CONFIGS.items():
            self.assertFalse(name.startswith("/") or ".." in name)
            if name.endswith(".toml"):
                tomllib.loads(text)
            elif name.endswith(".json"):
                json.loads(text)

    def test_immersive_weathering_does_not_change_the_map_on_block_ticks(self):
        c = self.parsed("immersive_weathering-common.toml")
        self.assertIs(c["general"]["block_growths"], False)          # every data-driven growth: weeds, frost, sand, ...
        self.assertIs(c["mossy_blocks"]["enabled"], False)           # moss spreading on mossable blocks
        self.assertIs(c["cracked_blocks"]["enabled"], False)         # cracking spreading
        self.assertIs(c["rusting"]["rusting"], False)
        self.assertEqual(c["rusting"]["rusting_rate"], 0.0)          # the rate the rust code actually reads
        self.assertEqual(c["leaf_piles"]["spawn_entity_from_decay"], 0.0)
        self.assertEqual(c["charring"]["fire_chars_wood"], 0.0)      # fire must not char a building for good

    def test_immersive_weathering_leaves_are_particulars_job(self):
        c = self.parsed("immersive_weathering-client.toml")
        self.assertIs(c["general"]["falling_leaf_particles"], False)

    def test_particle_densities_are_capped_low_for_big_armies(self):
        c = json.loads(build_pack.PACK_CONFIGS["cosycritters.json"])
        self.assertLessEqual(c["maxBirds"], 12)       # default 50
        self.assertLessEqual(c["maxMoths"], 3)        # default 8
        self.assertLessEqual(c["maxSpiders"], 3)      # default 16
        d = tomllib.loads(build_pack.PACK_CONFIGS["lightdust/main.toml"])
        self.assertLessEqual(d["Spawning Rules"]["ambientBlockCap"], 4)   # default 12

    def test_packwiz_ships_them_as_preserve_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            n = build_packwiz.build_variant(tmp, [("a.jar", "both", "https://cdn.modrinth.com/a.jar", "00")],
                                            ("our.jar", "https://x/our.jar", "11"),
                                            ("rp.zip", "https://x/rp.zip", "22"), "v", ())
            index = pathlib.Path(tmp, "index.toml").read_text()
            for name, text in build_pack.PACK_CONFIGS.items():
                self.assertEqual(pathlib.Path(tmp, "config", *name.split("/")).read_text(), text)
                block = index.split(f'file = "config/{name}"')[1].split("[[files]]")[0]
                self.assertIn("preserve = true", block)
                self.assertNotIn("metafile", block)
            self.assertGreater(n, len(build_pack.PACK_CONFIGS))

    def test_mrpack_carries_them_as_overrides(self):
        with tempfile.TemporaryDirectory() as tmp:
            jar = os.path.join(tmp, "our.jar")
            pathlib.Path(jar).write_bytes(b"jar")
            build_pack.write_mrpack(os.path.join(tmp, "p.mrpack"), "{}", jar)
            with zipfile.ZipFile(os.path.join(tmp, "p.mrpack")) as z:
                for name, text in build_pack.PACK_CONFIGS.items():
                    self.assertEqual(z.read("overrides/config/" + name).decode(), text)
                self.assertIn("overrides/mods/" + build_pack.OUR_JAR_NAME, z.namelist())


if __name__ == "__main__":
    unittest.main()
