#!/usr/bin/env python3
"""Tests for the Complementary settings we ship (pack/build_packwiz.py) and photograph (.github/workflows/eyes.yml).

Oct 28 (Itachi 1, eyes rounds r33 / runs 40767623509 vs 40770799673): from the RTS camera every grass tuft cast a
dark "star". Complementary's shadow pass pushes grass / fern quads (material 10005, and 10017) sideways at noon,
by an amount that grows with camera distance (shaders/program/shadow.glsl ~315, under PERPENDICULAR_TWEAKS).
Its licence (1.2c) forbids shipping it modified, so until its author OKs a change we ship settings that turn the
tweak off: DETAIL_QUALITY=0, with TAA and the leaf shadows put back to what DETAIL_QUALITY 2 gives.
On open meadow the dark share fell 0.050 -> 0.005 and 0.065 -> 0.016; buildings kept their light (within 5%).
lovish, Oct 28 18:08: "just adjust how we can until we get confirmation".

The eyes shader lane must photograph what players get: the same settings, the same pinned pack version.

    python3 tools/test_shader_settings.py
"""
import importlib
import pathlib
import sys
import unittest

ROOT = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "pack"))
EYES = (ROOT / ".github" / "workflows" / "eyes.yml").read_text()

FIX = {"DETAIL_QUALITY": "0", "TAA_DEFINE": "1", "LEAF_SHADOW_OPTIMISATION_DEFINE": "0"}


def bp():
    return importlib.import_module("build_packwiz")


def parse(text):
    """KEY=VALUE lines -> list of (key, value), in order (duplicates kept, so a test can see them)."""
    return [tuple(line.split("=", 1)) for line in text.splitlines() if line.strip()]


def options_file(files):
    txt = [text for rel, text, _meta, _keep in files if rel.startswith("shaderpacks/") and rel.endswith(".txt")]
    assert len(txt) == 1, txt
    return txt[0]


class PlayerPack(unittest.TestCase):
    def test_the_shipped_options_turn_off_the_foliage_shadow_push(self):
        opts = dict(parse(options_file(bp().shader_files(("ComplementaryReimagined_r5.9.3.zip", "u", "s")))))
        self.assertEqual(opts.get("RP_MODE"), "3")              # labPBR: reads our _n / _s maps
        for k, v in FIX.items():
            self.assertEqual(opts.get(k), v, k)

    def test_no_key_is_written_twice(self):
        keys = [k for k, _ in parse(options_file(bp().shader_files(("C.zip", "u", "s"))))]
        self.assertEqual(len(keys), len(set(keys)), keys)


class Options(unittest.TestCase):
    def test_extra_options_replace_a_shipped_key_instead_of_repeating_it(self):
        text = bp().complementary_options("DETAIL_QUALITY=2;BLOOM_STRENGTH=0.18")
        pairs = parse(text)
        keys = [k for k, _ in pairs]
        self.assertEqual(len(keys), len(set(keys)), keys)
        self.assertEqual(dict(pairs)["DETAIL_QUALITY"], "2")
        self.assertEqual(dict(pairs)["BLOOM_STRENGTH"], "0.18")
        self.assertEqual(dict(pairs)["TAA_DEFINE"], "1")

    def test_integrated_pbr_keeps_the_fix(self):
        opts = dict(parse(bp().complementary_options("", rp_mode=1)))
        self.assertEqual(opts["RP_MODE"], "1")
        self.assertEqual(opts["DETAIL_QUALITY"], "0")

    def test_a_malformed_extra_option_is_an_error(self):
        with self.assertRaises(ValueError):
            bp().complementary_options("DETAIL_QUALITY")


class EyesLane(unittest.TestCase):
    def test_the_complementary_lane_writes_the_shipped_options(self):
        self.assertIn("complementary_options(", EYES)

    def test_the_complementary_lane_photographs_the_pinned_version(self):
        self.assertIn("SHADER_VERSION", EYES)


if __name__ == "__main__":
    unittest.main()
