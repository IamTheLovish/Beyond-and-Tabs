"""tools/shader_pack_check.py: our shader pack compiles and names itself ours. Run: python3 -m unittest discover -s tools -p 'test_*.py'

The pack (shaderpacks/BeyondAndTabs, design/own_shader_pack.md) is only ever run by the game on CI runners and testers'
PCs; a GLSL typo there costs a ten-minute eyes run and shows as "no shader pack" in the stills. CI installs glslang
(build.yml, job ruff); without it the compile test is skipped and only the header checks run.
"""
import math
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

    def test_ambient_occlusion_takes_only_the_sky_share(self):
        # slice 3 (lib/ao.glsl): every program that writes colour also writes colortex1, the sky's share of the light,
        # so composite.fsh darkens creases in the fill alone; overlays and water write 0 (an unlit program left out
        # would take the ground's occlusion onto a selection ring)
        for name in spc.programs():
            if not name.startswith("gbuffers_") or not name.endswith(".fsh"):
                continue
            src = spc.expand(os.path.join(spc.SHADERS, name))
            if "orthoView())\n        discard;" in src and "SKY" in src:
                continue                    # the sky: nothing drawn in the RTS view, depth 1 elsewhere
            self.assertIn("/* DRAWBUFFERS:01 */", src, name)
            self.assertIn("gl_FragData[1]", src, name)
        props = open(os.path.join(spc.SHADERS, "shaders.properties"), encoding="utf-8").read()
        self.assertIn("scale.deferred=0.5", props)
        comp = open(os.path.join(spc.SHADERS, "composite.fsh"), encoding="utf-8").read()
        self.assertIn("lit *= 1.0 - skyShare * (1.0 - occlusionAt(", comp)

    def test_ao_depth_tolerance_is_safe_in_the_rts_view(self):
        # the blur (deferred1) and the upsampling (composite) let a neighbour share a pixel's occlusion only within a
        # depth tolerance. In the RTS view the depth comes from a camera plane inside the scene and goes negative behind
        # it (down to the near plane, -2750), so a term in the depth would reach 0 (a division by zero) or turn the
        # weights round. Both passes take aoDepthTolerance, whose RTS branch reads no depth and whose first-person term
        # never drops below the base
        ao = open(os.path.join(spc.SHADERS, "lib", "ao.glsl"), encoding="utf-8").read()
        for name, var in (("deferred1.fsh", "centre.y"), ("composite.fsh", "viewDepth")):
            src = open(os.path.join(spc.SHADERS, name), encoding="utf-8").read()
            self.assertRegex(src, r"tolerance = aoDepthTolerance\(", name)
            self.assertEqual(len(re.findall(r"\btolerance = ", src)), 1, name)
        body = re.search(r"float aoDepthTolerance\([^)]*\) \{(.*?)\n\}", ao, re.S).group(1)
        rts, first_person = re.findall(r"return ([^;]*);", body)
        self.assertIn("if (aoOrtho(projection))", body)
        self.assertNotIn("viewDepth", rts)
        self.assertIn("max(viewDepth, 0.0)", first_person)
        blur = spc.float_const(open(os.path.join(spc.SHADERS, "deferred1.fsh"), encoding="utf-8").read(),
                               "AO_DEPTH_TOLERANCE")
        for base in (blur, 0.25):
            for depth in (-2750.0, -500.0, -25.0, -12.5, 0.0, 40.0, 300.0):
                self.assertGreater(spc.ao_depth_tolerance(ao, base, depth, False), 0.0)
                self.assertEqual(spc.ao_depth_tolerance(ao, base, depth, True),
                                 spc.ao_depth_tolerance(ao, base, 40.0, True))
            for height in (720.0, 1080.0, 2160.0):
                # the default view (zoom 30): under the 1 block of depth between a unit's silhouette and the ground
                # behind it at 65 degrees (1-2.2 blocks), so a unit's occlusion never spreads onto that ground
                self.assertLess(spc.ao_depth_tolerance(ao, base, 40.0, True, 30.0, height), 1.0)
                # flat ground at the configured tilts (camera_pitch_close 55 .. _far 75 degrees) still blends at every
                # zoom: a neighbour two half-resolution pixels away (the blur's reach; the upsampling's is one) keeps
                # at least half its weight
                reach = 2.0 if base == blur else 1.0
                for zoom in (10.0, 30.0, 90.0, 200.0):
                    for pitch in (55.0, 75.0):
                        cell = zoom / (height * spc.float_const(ao, "AO_SCALE"))
                        gap = reach * cell / math.tan(math.radians(pitch))
                        tol = spc.ao_depth_tolerance(ao, base, 40.0, True, zoom, height)
                        self.assertGreaterEqual(1.0 - gap / tol, 0.5, (base, height, zoom, pitch))

    def test_lit_ground_never_blooms(self):
        # slice 3's judged number: zero bloom on a plain noon lawn. The light table puts sunlit ground at its albedo
        # (under 1 in linear light), so the threshold must stay at or above 1
        comp = open(os.path.join(spc.SHADERS, "composite.fsh"), encoding="utf-8").read()
        m = re.search(r"const float BLOOM_THRESHOLD = ([\d.]+);", comp)
        self.assertIsNotNone(m)
        self.assertGreaterEqual(float(m.group(1)), 1.0)

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

    @unittest.skipUnless(spc.find_glslang(), "glslang not installed")
    def test_every_program_compiles(self):
        self.assertEqual(spc.check(spc.find_glslang()), [])


if __name__ == "__main__":
    unittest.main()
