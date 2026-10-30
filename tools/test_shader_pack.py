"""tools/shader_pack_check.py: our shader pack compiles and names itself ours. Run: python3 -m unittest discover -s tools -p 'test_*.py'

The pack (shaderpacks/BeyondAndTabs, design/own_shader_pack.md) is only ever run by the game on CI runners and testers'
PCs; a GLSL typo there costs a ten-minute eyes run and shows as "no shader pack" in the stills. CI installs glslang
(build.yml, job ruff); without it the compile test is skipped and only the header checks run.
"""
import os
import re
import sys
import unittest

sys.path.insert(0, os.path.dirname(__file__))
import shader_pack_check as spc  # noqa: E402


class ShaderPack(unittest.TestCase):
    def test_every_file_is_ours_and_clean_room(self):
        self.assertEqual(spc.header_problems(), [])

    def test_includes_expand(self):
        for name in spc.programs():
            src = spc.expand(os.path.join(spc.SHADERS, name))
            self.assertTrue(src.startswith("#version 120"), name)
            self.assertNotIn("#include", src, name)

    def test_the_loader_reads_our_settings(self):
        # the sun tilt lovish picked (Oct 29) and the shadow map's size, where every program sees them
        src = open(os.path.join(spc.SHADERS, "lib", "settings.glsl"), encoding="utf-8").read()
        self.assertIn("const float sunPathRotation = 30.0;", src)
        self.assertIn("const int shadowMapResolution = 2048;", src)

    def test_grade_matches_the_mods_enchanted_look(self):
        # one table for both looks (design: players with and without the pack see the same mood). The pack's
        # final.fsh copies the Enchanted column of WorldLookPass (#326); until #326 is on this branch there is no third
        # column to compare and the test skips
        java = open(os.path.join(spc.ROOT, "..", "..", "bar", "src", "main", "java", "com", "solegendary",
                                 "reignofnether", "postfx", "WorldLookPass.java"), encoding="utf-8").read()
        fsh = open(os.path.join(spc.SHADERS, "final.fsh"), encoding="utf-8").read()
        pairs = spc.enchanted_pairs(java, fsh)
        if pairs is None:
            self.skipTest("WorldLookPass has no Enchanted column yet (#326)")
        for name, want, got in pairs:
            self.assertAlmostEqual(want, got, places=4, msg=name)

    def test_material_maps_reach_the_lit_draws_but_not_water(self):
        # slice 2 reads our _n / _s maps through one switch (lib/pbrswitch.glsl): terrain, entities, block entities and
        # the hand pass LIT_PBR. Water has no _n / _s maps and its own surface (lib/water.glsl), so it stays out
        switch = open(os.path.join(spc.SHADERS, "lib", "pbrswitch.glsl"), encoding="utf-8").read()
        self.assertIn("!defined LIT_WATER", switch)
        for name in ("gbuffers_terrain.fsh", "gbuffers_entities.fsh", "gbuffers_block.fsh", "gbuffers_hand.fsh"):
            src = spc.expand(os.path.join(spc.SHADERS, name))
            self.assertIn("uniform sampler2D normals;", src, name)
            self.assertIn("uniform sampler2D specular;", src, name)
        self.assertIn("#define LIT_WATER", open(os.path.join(spc.SHADERS, "gbuffers_water.fsh"), encoding="utf-8").read())

    def test_water_has_its_own_surface_with_a_fallback(self):
        """Changed in slice 4 (claude/shader-water): this guard used to pin water to slice 1's plain surface ("water
        keeps slice 1's surface until slice 4"). Slice 4 is here, so it now pins the new contract instead: water is
        its own block id, gbuffers_water reads the opaque depth under it (depthtex1) to set its opacity from the
        path through the water, and WATER_REALISM 0 still compiles to slice 1's one fixed alpha for an A/B."""
        props = open(os.path.join(spc.SHADERS, "block.properties"), encoding="utf-8").read()
        self.assertRegex(props, r"(?m)^block\.10002=water\b")
        settings = open(os.path.join(spc.SHADERS, "lib", "settings.glsl"), encoding="utf-8").read()
        self.assertIn("#define ID_WATER 10002.0", settings)
        self.assertRegex(settings, r"(?m)^#define WATER_REALISM 1 // \[0 1\]")
        water = spc.expand(os.path.join(spc.SHADERS, "gbuffers_water.fsh"))
        self.assertIn("uniform sampler2D depthtex1;", water)
        self.assertIn("waterOpacity(", water)
        plain = spc.with_options(water, {"WATER_REALISM": "0"})
        self.assertIn("#define WATER_REALISM 0", plain)
        self.assertIn("WATER_REALISM", spc.OPTION_VARIANTS[1])
        # the slice 1 path survives: the texture's own alpha, written unless the realistic surface replaces it
        self.assertIn("float alpha = tex.a;", water)

    def test_units_in_a_ford_stay_readable(self):
        """Readability through the water (slice 4: "units crossing a river stay readable", the 12 river stills).

        Units are entities: the loader draws them before translucent terrain, so they are in depthtex1 and the water
        layer lies over their submerged part with the path from the surface down to the unit, not to the bed - a leg
        in a ford is only a short path deep. A model of the opacity curve from lib/water.glsl's own constants: half a
        block of path (a unit wading) keeps at least 65% of the unit's colour, a 1-block ford shows the bed, and only
        3+ blocks settle into the palette's opaque teal-blue. The foam band stays a few pixels thin, and the caustics
        multiply by one number per pixel (composite), so a team colour's hue never shifts under water."""
        import math
        src = open(os.path.join(spc.SHADERS, "lib", "water.glsl"), encoding="utf-8").read()

        def const(name):
            return float(re.search(r"const float %s = ([0-9.]+);" % name, src).group(1))

        k, deep = const("WATER_EXTINCTION"), const("WATER_DEEP_OPACITY")

        def opacity(path):
            return deep * (1.0 - math.exp(-k * path))

        self.assertLessEqual(opacity(0.5), 0.35)          # a unit wading: its team colour keeps >= 65%
        self.assertLessEqual(opacity(1.1), 0.62)          # a 1-block ford at the RTS tilt: the bed shows
        self.assertGreaterEqual(opacity(3.3), 0.85)       # 3 blocks deep: open water, the palette's dark teal
        self.assertLessEqual(const("FOAM_WIDTH"), 0.3)    # ~4 px at the default zoom
        self.assertLessEqual(const("FOAM_OPACITY"), 0.6)
        comp = open(os.path.join(spc.SHADERS, "composite.fsh"), encoding="utf-8").read()
        self.assertRegex(comp, r"lin \*= max\(1\.0 \+ \(water\.g - 0\.5\) / CAUSTIC_ENCODE, 0\.0\);")
        # the water layer's data goes to colortex2 from gbuffers_water only (one directive per program)
        water = spc.expand(os.path.join(spc.SHADERS, "gbuffers_water.fsh"))
        self.assertIn("/* DRAWBUFFERS:02 */", water)
        self.assertIn("uniform sampler2D colortex2;", comp)

    @unittest.skipUnless(spc.find_glslang(), "glslang not installed")
    def test_every_program_compiles(self):
        self.assertEqual(spc.check(spc.find_glslang()), [])


if __name__ == "__main__":
    unittest.main()
