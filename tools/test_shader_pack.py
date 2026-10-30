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
        self.assertIn("lin *= 1.0 - skyShare * (1.0 - occlusionAt(", comp)

    def test_buffers_agree_across_programs(self):
        # the loader gathers every colortexN format and clear directive from all programs into one table, the last one
        # read winning: two passes using one colortex for different things (slice 3's AO and slice 4's water both took
        # colortex2 before the stack) would silently share a format and a clear. Each buffer: one format, one clear
        # colour; every buffer a program draws into or samples has a format; and none is sampled that nothing draws
        formats, clears, written, read = {}, {}, set(), set()
        for name in spc.programs():
            src = spc.expand(os.path.join(spc.SHADERS, name))
            for i, fmt in re.findall(r"const int colortex(\d+)Format = (\w+);", src):
                formats.setdefault(int(i), set()).add(fmt)
            for i, col in re.findall(r"const vec4 colortex(\d+)ClearColor = ([^;]+);", src):
                clears.setdefault(int(i), set()).add(col.replace(" ", ""))
            if name.endswith(".fsh"):
                for d in re.findall(r"/\* DRAWBUFFERS:(\d+) \*/", src):
                    written |= {int(c) for c in d}
                read |= {int(i) for i in re.findall(r"uniform sampler2D colortex(\d+);", src)}
        for i, f in formats.items():
            self.assertEqual(len(f), 1, "colortex%d declared as %s" % (i, sorted(f)))
        for i, c in clears.items():
            self.assertEqual(len(c), 1, "colortex%d cleared to %s" % (i, sorted(c)))
        for i in written | read:
            self.assertIn(i, formats, "colortex%d has no format" % i)
        self.assertEqual(read - written, set(), "sampled but never drawn into")
        # colortex1 (the sky share) must clear to 0, not the loader's default white: an undrawn pixel takes no AO
        self.assertEqual(clears.get(1), {"vec4(0.0,0.0,0.0,0.0)"})

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
        # the hand pass LIT_PBR. Water has no _n / _s maps and its own surface (lib/water.glsl), so it stays out
        switch = open(os.path.join(spc.SHADERS, "lib", "pbrswitch.glsl"), encoding="utf-8").read()
        self.assertIn("!defined LIT_WATER", switch)
        for name in ("gbuffers_terrain.fsh", "gbuffers_entities.fsh", "gbuffers_block.fsh", "gbuffers_hand.fsh"):
            src = spc.expand(os.path.join(spc.SHADERS, name))
            self.assertIn("uniform sampler2D normals;", src, name)
            self.assertIn("uniform sampler2D specular;", src, name)
        self.assertIn("#define LIT_WATER", open(os.path.join(spc.SHADERS, "gbuffers_water.fsh"), encoding="utf-8").read())

    def test_wind_moves_the_shadow_with_the_leaves(self):
        # lib/wind.glsl: the terrain pass and the shadow pass call the same function on the same world position, so
        # a leaf's shadow never detaches from the leaf (Oct 30)
        for name in ("gbuffers_terrain.vsh", "shadow.vsh"):
            src = spc.expand(os.path.join(spc.SHADERS, name))
            self.assertIn("windOffset(playerPos + cameraPosition", src, name)
        props = open(os.path.join(spc.SHADERS, "block.properties"), encoding="utf-8").read()
        self.assertIn("block.10002=oak_leaves", props)

    def test_options_compile_both_ways(self):
        # every option a program reads is compiled at both ends of its range (a toggle on and off)
        src = spc.expand(os.path.join(spc.SHADERS, "gbuffers_terrain.vsh"))
        self.assertIn("WIND=0", [label for label, _ in spc.option_variants(src)])

    def test_water_has_its_own_surface_with_a_fallback(self):
        """Changed in slice 4 (claude/shader-water): this guard used to pin water to slice 1's plain surface ("water
        keeps slice 1's surface until slice 4"). Slice 4 is here, so it now pins the new contract instead: water is
        its own block id (10004 in the stack, as the wind's leaves are 10002), gbuffers_water reads the opaque depth
        under it (depthtex1) to set its opacity from the path through the water, and WATER_REALISM 0 still compiles
        to slice 1's one fixed alpha for an A/B."""
        props = open(os.path.join(spc.SHADERS, "block.properties"), encoding="utf-8").read()
        self.assertRegex(props, r"(?m)^block\.10004=water\b")      # 10002 is the leaves (wind)
        settings = open(os.path.join(spc.SHADERS, "lib", "settings.glsl"), encoding="utf-8").read()
        self.assertIn("#define ID_WATER 10004.0", settings)
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
        # the water layer's data goes to colortex5 from gbuffers_water only (one directive per program; 5 in the stack
        # with slice 3, whose ambient occlusion has colortex1-3 and its bloom colortex4)
        water = spc.expand(os.path.join(spc.SHADERS, "gbuffers_water.fsh"))
        self.assertIn("/* DRAWBUFFERS:015 */", water)
        self.assertIn("uniform sampler2D colortex5;", comp)

    def test_reflections_march_only_where_they_show(self):
        """Screen-space reflections (composite.fsh) cost a 16-step march per water pixel; the budget (slice 4: +1.0 ms
        in all on a 3070 Ti at 1080p) only allows it where the mirror is visible. Fresnel for water at the RTS camera's
        ~65 degrees down, with the ripples' steepest tilt (~0.17 rad more), stays under the march's threshold, so from
        above the march never runs; a grazing first-person view passes it."""
        import math
        comp = open(os.path.join(spc.SHADERS, "composite.fsh"), encoding="utf-8").read()
        water = open(os.path.join(spc.SHADERS, "lib", "water.glsl"), encoding="utf-8").read()
        threshold = float(re.search(r"const float SSR_MIN_FRESNEL = ([0-9.]+);", comp).group(1))
        f0 = float(re.search(r"const float WATER_F0 = ([0-9.]+);", water).group(1))

        def fresnel(off_normal_deg):
            return f0 + (1.0 - f0) * (1.0 - math.cos(math.radians(off_normal_deg))) ** 5

        self.assertLess(fresnel(25.0 + math.degrees(0.17)), threshold)   # the RTS view: no march
        self.assertGreater(fresnel(80.0), threshold)                     # first person near the horizon: mirror
        settings = open(os.path.join(spc.SHADERS, "lib", "settings.glsl"), encoding="utf-8").read()
        self.assertRegex(settings, r"(?m)^#define SSR 1 // \[0 1\]")
        self.assertRegex(settings, r"(?m)^#define CAUSTICS 1 // \[0 1\]")

    @unittest.skipUnless(spc.find_glslang(), "glslang not installed")
    def test_every_program_compiles(self):
        self.assertEqual(spc.check(spc.find_glslang()), [])


if __name__ == "__main__":
    unittest.main()
