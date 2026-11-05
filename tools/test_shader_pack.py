"""tools/shader_pack_check.py: our shader pack compiles and names itself ours. Run: python3 -m unittest discover -s tools -p 'test_*.py'

The pack (shaderpacks/BeyondAndTabs, design/own_shader_pack.md) is only ever run by the game on CI runners and testers'
PCs; a GLSL typo there costs a ten-minute eyes run and shows as "no shader pack" in the stills. CI installs glslang
(build.yml, job ruff); without it the compile test is skipped and only the header checks run.
"""
import math
import os
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
        # the hand pass LIT_PBR; water keeps slice 1's surface until slice 4 (design/own_shader_pack.md)
        switch = open(os.path.join(spc.SHADERS, "lib", "pbrswitch.glsl"), encoding="utf-8").read()
        self.assertIn("!defined LIT_WATER", switch)
        for name in ("gbuffers_terrain.fsh", "gbuffers_entities.fsh", "gbuffers_block.fsh", "gbuffers_hand.fsh"):
            src = spc.expand(os.path.join(spc.SHADERS, name))
            self.assertIn("uniform sampler2D normals;", src, name)
            self.assertIn("uniform sampler2D specular;", src, name)
        self.assertIn("#define LIT_WATER", open(os.path.join(spc.SHADERS, "gbuffers_water.fsh"), encoding="utf-8").read())
        # and after the preprocessor (what the GPU compiles) the water pass binds no material maps at all, while
        # terrain keeps them - so this check cannot pass by the preprocessor dropping everything
        terrain = spc.preprocess(spc.expand(os.path.join(spc.SHADERS, "gbuffers_terrain.fsh")))
        self.assertIn("uniform sampler2D normals;", terrain)
        water = spc.preprocess(spc.expand(os.path.join(spc.SHADERS, "gbuffers_water.fsh")))
        self.assertIn("void main()", water)
        self.assertNotIn("uniform sampler2D normals;", water)
        self.assertNotIn("uniform sampler2D specular;", water)

    def test_materials_off_is_slice_one(self):
        # the option screen promises MATERIALS Off = slice 1's look (review of #434, Nov 5): no material maps and slice 1's
        # exposure 0.95. Only the near-neutral highlight tint (lovish, Oct 30) stays. Neither setting bounces the sun
        # onto walls: lovish, Nov 5, "Keep the strong step" (slice 1's wall-to-roof step, 1.73 on the block sampler)
        def program(name, materials):
            src = spc.expand(os.path.join(spc.SHADERS, name))
            return spc.preprocess(src.replace("#define MATERIALS 1 ", "#define MATERIALS %d " % materials))
        for materials, exposure in ((1, "1.05"), (0, "0.95")):
            final, terrain = program("final.fsh", materials), program("gbuffers_terrain.fsh", materials)
            self.assertIn("const float EXPOSURE = %s;" % exposure, final, materials)
            self.assertNotIn("BOUNCE", terrain, materials)
            self.assertEqual("uniform sampler2D normals;" in terrain, materials == 1)

    def test_noon_light_leaves_open_ground_its_colour(self):
        # lib/light.glsl aims open ground at noon to come out at its albedo. Itachi 1 measured the lawn under the pack
        # (eyes 41759309261 vs the plain lane 41759297367, same build, ground sampler, noon): every sunlit ground
        # material lost ~17% of its blue against its red, and the green lawn read olive. A warm sun is fine; the sun and
        # the sky's fill together on open ground must stay white, or every colour on the field turns toward yellow. The
        # lobby's noon (6000, the 30 degree tilt) puts the sun ~60 degrees up: an up face in full sky light, unshadowed,
        # takes the sun through sunFacing and the fill through surfaceLight's side share, plus MIN_LIGHT. The test reads
        # those constants and checks the two formulas still use them (review of #506: a change there must go red)
        src = open(os.path.join(spc.SHADERS, "lib", "light.glsl"), encoding="utf-8").read()
        self.assertIn("return mix(ndl, sqrt(ndl), SUN_FACING_EASE);", src)
        self.assertIn("(SKY_FILL_SIDE + (1.0 - SKY_FILL_SIDE) * upness)", src)
        ease, side = spc.float_const(src, "SUN_FACING_EASE"), spc.float_const(src, "SKY_FILL_SIDE")
        sun, sky = spc.vec3_const(src, "SUN_HIGH"), spc.vec3_const(src, "SKY_HIGH")
        floor = spc.vec3_const(src, "MIN_LIGHT")
        ndl = math.sin(math.radians(60.0))
        facing = ndl + (math.sqrt(ndl) - ndl) * ease
        fill = side + (1.0 - side) * 1.0
        light = [s * facing + k * fill + m for s, k, m in zip(sun, sky, floor)]
        self.assertLessEqual(max(light) / min(light), 1.02, "noon light on open ground %s is not white" % light)
        self.assertTrue(0.97 <= sum(light) / 3 <= 1.03, "noon light on open ground %s is not the albedo's level" % light)

    def test_split_tone_leaves_the_lit_ground_neutral(self):
        # final.fsh's warm shadow tint (the Enchanted mood) belongs to the dark tones, never the sunlit field: the
        # palette's darkest sunlit ground is dirt and paths at 0.30 luma (the lawn 0.33-0.43). The old bell peaked at
        # 0.25 and still gave 0.9 of the tint at 0.30 - the larger half of the lawn's olive pull. Deep shade (0.15)
        # keeps the tone
        src = open(os.path.join(spc.SHADERS, "final.fsh"), encoding="utf-8").read()
        for lc in (0.30, 0.35, 0.40, 0.45, 0.50):
            self.assertLessEqual(spc.split_tone_shadows(src, lc), 0.05, "sunlit ground at luma %.2f is tinted" % lc)
        self.assertGreaterEqual(spc.split_tone_shadows(src, 0.15), 0.5, "deep shade lost the warm tone")

    @unittest.skipUnless(spc.find_glslang(), "glslang not installed")
    def test_every_program_compiles(self):
        self.assertEqual(spc.check(spc.find_glslang()), [])


if __name__ == "__main__":
    unittest.main()
