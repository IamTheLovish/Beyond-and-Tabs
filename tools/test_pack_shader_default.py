"""Our shader pack ships ON by default (lovish, Nov 5: "Yes, shader on, MEDIUM default (Recommended)").

The launcher pack (pack/build_packwiz.py) and the tester .mrpack (pack/build_pack.py) carry Oculus (LGPL v3, from its
official Modrinth file, pinned to the version every shader eyes run used with Embeddium 0.3.31), our own pack as plain
files of shaderpacks/BeyondAndTabs (byte for byte - tools/test_pack_no_complementary.py's rule), and Oculus' config
switched on with our pack selected. The config is written once (preserve): a player who turns shaders off (K) keeps
that. The Mac pack has no Oculus (it needs Embeddium, which the Mac pack drops), so it ships neither.
shaders.properties has LOW / MEDIUM / HIGH profiles: MEDIUM is the pack's own defaults, LOW the cheap one for weaker
GPUs. Fully offline (the Modrinth steps are faked).
Run: python3 -m unittest tools/test_pack_shader_default.py
"""
import hashlib
import io
import os
import re
import sys
import tempfile
import unittest
import zipfile
from unittest import mock

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.join(HERE, "..")
sys.path.insert(0, os.path.join(REPO, "pack"))
import build_pack  # noqa: E402
import build_packwiz  # noqa: E402

SHADERS = os.path.join(REPO, "shaderpacks", "BeyondAndTabs", "shaders")
H512 = "a" * 128
ENTRIES = [("embeddium-0.3.31+mc1.20.1.jar", "client", "https://cdn.modrinth.com/data/x/versions/y/embeddium.jar", H512),
           ("oculus-mc1.20.1-1.8.0.jar", "client", "https://cdn.modrinth.com/data/x/versions/y/oculus.jar", H512)]
OUR = ("beyondandtabs-test.jar", "https://example.invalid/beyondandtabs-test.jar", H512)
RP = (build_packwiz.RP_NAME, "https://example.invalid/rp.zip", H512)


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def repo_pack_files():
    """{relative path under the instance: bytes} of every file of our pack in the repo."""
    root = os.path.join(REPO, "shaderpacks", "BeyondAndTabs")
    out = {}
    for base, _, names in os.walk(root):
        for n in names:
            p = os.path.join(base, n)
            rel = "shaderpacks/BeyondAndTabs/" + os.path.relpath(p, root).replace(os.sep, "/")
            with open(p, "rb") as f:
                out[rel] = f.read()
    return out


def properties(text):
    return dict(line.split("=", 1) for line in text.splitlines() if "=" in line and not line.startswith("#"))


def options():
    """{name: (default, [allowed values])} for every option-screen option in lib/settings.glsl."""
    src = read(os.path.join(SHADERS, "lib", "settings.glsl"))
    out = {}
    for m in re.finditer(r"(?m)^(?:#define (\w+) (\S+)|const (?:int|float) (\w+) = ([^;]+);) // \[([^\]]*)\]", src):
        name = m.group(1) or m.group(3)
        out[name] = (m.group(2) or m.group(4), m.group(5).split())
    return out


def profiles():
    """{LOW / MEDIUM / HIGH: {option: value}} from shaders.properties' profile.<name> lines (OptiFine syntax, which
    Iris and Oculus read: option:value for a valued option)."""
    props = properties(read(os.path.join(SHADERS, "shaders.properties")))
    out = {}
    for key, value in props.items():
        if key.startswith("profile."):
            out[key[len("profile."):]] = dict(e.split(":", 1) for e in value.split())
    return out


class Profiles(unittest.TestCase):
    def test_three_profiles_on_known_options_and_values(self):
        opts, prof = options(), profiles()
        self.assertEqual(set(prof), {"LOW", "MEDIUM", "HIGH"})
        for name, entries in prof.items():
            for opt, value in entries.items():
                self.assertIn(opt, opts, "%s sets %s, which the option screen does not have" % (name, opt))
                self.assertIn(value, opts[opt][1], "%s: %s=%s is not one of %s" % (name, opt, value, opts[opt][1]))
        # every profile names the same options, so switching profile sets all of them
        self.assertEqual(set(prof["LOW"]), set(prof["MEDIUM"]))
        self.assertEqual(set(prof["HIGH"]), set(prof["MEDIUM"]))

    def test_medium_is_the_pack_default(self):
        # the default a new player gets IS MEDIUM: every value MEDIUM sets is the option's own default
        opts = options()
        for opt, value in profiles()["MEDIUM"].items():
            self.assertEqual(value, opts[opt][0], opt)

    def test_low_is_cheaper_and_high_richer(self):
        low, med, high = (profiles()[k] for k in ("LOW", "MEDIUM", "HIGH"))
        for opt in ("shadowMapResolution", "shadowDistance"):
            self.assertLess(float(low[opt]), float(med[opt]), opt)
            self.assertGreater(float(high[opt]), float(med[opt]), opt)
        for opt in ("AO", "SOFT_SHADOWS", "BLOOM", "SSR", "CAUSTICS"):     # the per-pixel extras off on LOW
            self.assertEqual(low[opt], "0", opt)
            self.assertEqual(high[opt], "1", opt)
        # #437's wind and cloud shadows (vertex and per-pixel work) off on LOW, on otherwise; its RTS texture sharpening
        # off on LOW (0 = the hardware's mip choice)
        for opt in ("WIND", "CLOUD_SHADOWS"):
            self.assertEqual(low[opt], "0", opt)
            self.assertEqual(med[opt], "1", opt)
        self.assertEqual(float(low["TEXTURE_SHARPNESS"]), 0.0)

    def test_every_on_off_screen_option_is_in_every_profile(self):
        # Eris.15 (#554 review r1): a profile that leaves an option out keeps whatever the player had, so switching to
        # LOW after #437 merged would have kept wind and cloud shadows on and never set the sharpening. Every on/off
        # option on the screen (its values [0 1]) and every slider is set by every profile
        props = properties(read(os.path.join(SHADERS, "shaders.properties")))
        opts = options()
        on_screen = [o for o in props["screen"].split() if o in opts]
        toggles = [o for o in on_screen if opts[o][1] == ["0", "1"]]
        sliders = [o for o in props["sliders"].split() if o in opts and o != "sunPathRotation"]   # the look's own tilt
        self.assertTrue(toggles and sliders)
        for name, entries in profiles().items():
            for opt in toggles + sliders:
                self.assertIn(opt, entries, "profile %s does not set %s" % (name, opt))

    def test_the_screen_shows_the_profile_and_says_how_to_turn_it_off(self):
        props = properties(read(os.path.join(SHADERS, "shaders.properties")))
        self.assertEqual(props["screen"].split()[0], "<profile>")
        lang = properties(read(os.path.join(SHADERS, "lang", "en_us.lang")))
        note = lang.get("profile.comment", "")
        self.assertIn(" K ", " " + note + " ")                            # the toggle key
        self.assertIn("LOW", note)
        for name in ("LOW", "MEDIUM", "HIGH"):
            self.assertIn("profile." + name, lang)


class BuilderLists(unittest.TestCase):
    def test_oculus_is_shipped_pinned_and_client_only(self):
        self.assertIn("oculus", build_pack.EXTRA_PROJECTS)
        self.assertEqual(build_pack.PINNED_VERSIONS["oculus"], "1.20.1-1.8.0")   # every shader eyes run's version
        self.assertIn("oculus", build_pack.CLIENT_ONLY_PROJECTS)
        self.assertIn("oculus", build_pack.MAC_SKIP)                            # it needs Embeddium


class PackwizDefault(unittest.TestCase):
    def build(self, skip):
        with tempfile.TemporaryDirectory() as tmp:
            build_packwiz.build_variant(tmp, ENTRIES, OUR, RP, "test", skip)
            files = {}
            for base, _, names in os.walk(tmp):
                for n in names:
                    p = os.path.join(base, n)
                    with open(p, "rb") as f:
                        files[os.path.relpath(p, tmp).replace(os.sep, "/")] = f.read()
        return files

    def test_windows_pack_ships_our_pack_on(self):
        files = self.build(())
        ours = repo_pack_files()
        shipped = {k: v for k, v in files.items() if k.startswith("shaderpacks/")}
        self.assertEqual(shipped, ours)                                   # every file, byte for byte, nothing else
        for name in ("config/oculus.properties", "config/iris.properties"):
            cfg = properties(files[name].decode())
            self.assertEqual(cfg["enableShaders"], "true", name)
            self.assertEqual(cfg["shaderPack"], "BeyondAndTabs", name)
        index = files["index.toml"].decode()
        # the config is written once (a player's K stays off); the pack's files update with every release
        for name in ("config/oculus.properties", "config/iris.properties"):
            block = index.split('file = "%s"' % name)[1].split("[[files]]")[0]
            self.assertIn("preserve = true", block, name)
        block = index.split('file = "shaderpacks/BeyondAndTabs/shaders/shaders.properties"')[1].split("[[files]]")[0]
        self.assertNotIn("preserve", block)
        # each file's index hash is the sha256 of the bytes shipped
        for rel in ("shaderpacks/BeyondAndTabs/shaders/final.fsh", "config/oculus.properties"):
            block = index.split('file = "%s"' % rel)[1].split("[[files]]")[0]
            self.assertIn(hashlib.sha256(files[rel]).hexdigest(), block, rel)

    def test_mac_pack_ships_neither(self):
        files = self.build(build_pack.MAC_SKIP)
        self.assertFalse([k for k in files if k.startswith("shaderpacks/")])
        self.assertNotIn("config/oculus.properties", files)
        self.assertNotIn("config/iris.properties", files)
        self.assertFalse([k for k in files if "oculus" in k.lower()])


class MrpackDefault(unittest.TestCase):
    def test_mrpack_overrides_carry_our_pack_and_the_config(self):
        sha1, sha512 = hashlib.sha1(b"jar").hexdigest(), hashlib.sha512(b"jar").hexdigest()

        def get_json(url):
            slug = url.split("/project/")[1].split("/")[0]
            return [{"project_id": slug, "version_number": build_pack.PINNED_VERSIONS.get(slug, "1"),
                     "date_published": "2026-10-01", "version_type": "release", "dependencies": [],
                     "files": [{"primary": True, "filename": slug + ".jar", "url": "https://cdn.modrinth.com/" + slug,
                                "hashes": {"sha1": sha1, "sha512": sha512}}]}]

        def download(url, dest):
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
                over = {n.split("/", 1)[1]: z.read(n) for n in z.namelist() if n.startswith("overrides/")}
                index = z.read("modrinth.index.json").decode()
        self.assertEqual({k: v for k, v in over.items() if k.startswith("shaderpacks/")}, repo_pack_files())
        self.assertEqual(properties(over["config/oculus.properties"].decode())["shaderPack"], "BeyondAndTabs")
        self.assertIn("oculus", index)


if __name__ == "__main__":
    unittest.main()
