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


def read(*parts):
    """A pack file's text (a path under shaders/), closed after reading."""
    with open(os.path.join(spc.SHADERS, *parts), encoding="utf-8") as f:
        return f.read()


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
        src = read("lib", "settings.glsl")
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
        props = read("shaders.properties")
        self.assertIn("scale.deferred=0.5", props)
        comp = read("composite.fsh")
        self.assertIn("lit *= 1.0 - skyShare * (1.0 - occlusionAt(", comp)

    def test_ao_depth_tolerance_is_safe_in_the_rts_view(self):
        # the blur (deferred1) and the upsampling (composite) let a neighbour share a pixel's occlusion only within a
        # depth tolerance. In the RTS view the depth comes from a camera plane inside the scene and goes negative behind
        # it (down to the near plane, -2750), so a term in the depth would reach 0 (a division by zero) or turn the
        # weights round. Both passes take aoDepthTolerance, whose RTS branch reads no depth and whose first-person term
        # never drops below the base. spc.ao_depth_tolerance evaluates the function's own two `return` expressions (the
        # RTS one under `if (aoOrtho(projection))`, then the first-person one), so every number below is the GLSL's
        ao = read("lib", "ao.glsl")
        bases = []
        for name in ("deferred1.fsh", "composite.fsh"):
            src = read(name)
            self.assertEqual(len(re.findall(r"\btolerance = ", src)), 1, name)
            arg = re.search(r"\btolerance = aoDepthTolerance\(([\w.]+),", src).group(1)
            bases.append(float(arg) if re.match(r"[\d.]+$", arg) else spc.float_const(src, arg))
        blur = bases[0]
        for base in bases:
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
        comp = read("composite.fsh")
        m = re.search(r"const float BLOOM_THRESHOLD = ([\d.]+);", comp)
        self.assertIsNotNone(m)
        self.assertGreaterEqual(float(m.group(1)), 1.0)

    def test_plants_cast_a_faint_shadow_never_a_dark_star(self):
        # lovish (Nov 5, buttons): "Try faint shadow + lit side". Grass, flowers and crops cast no shadow since Oct 28
        # (hard shadows read as dark stars under every tuft). Now they cast PLANT_SHADOW of the sun's light; no constant
        # = the old pack, nothing cast. Two-sided at noon on open ground: the ground under a tuft keeps at least 0.80 of
        # the open lawn (slice 1's judged band for grass tufts, design/own_shader_pack.md in PR #385: "luma under a tuft
        # at least 0.8x the open lawn beside it (no dark stars)") and is at least 8% darker, or the shadow does not show
        def code(*path):
            return spc.code_of(read(*path))
        light = code("lib", "light.glsl")
        k = spc.float_const(code("lib", "settings.glsl"), "PLANT_SHADOW", 0.0)
        ndl = math.sin(math.radians(60.0))
        under = spc.noon_light(light, 1.0, ndl, 1.0 - k) / spc.noon_light(light, 1.0, ndl)
        self.assertTrue(0.80 <= under <= 0.92, "ground under a tuft at %.3f of the open lawn" % under)
        # the shadow pass draws the plants with that opacity, and the lookup lets the rest of the sun through (the code
        # itself, comments stripped: a comment quoting a line does not run)
        # both halves of a two-block plant (ID_FOLIAGE_UPPER, #437) cast the faint shadow, never a full one
        shadow_vsh = code("shadow.vsh")
        self.assertIn("bool plant = abs(mc_Entity.x - ID_FOLIAGE) < 0.5 || abs(mc_Entity.x - ID_FOLIAGE_UPPER) < 0.5;",
                      shadow_vsh)
        self.assertIn("casterOpacity = plant ? PLANT_SHADOW : 1.0;", shadow_vsh)
        self.assertIn("gl_FragData[0] = vec4(casterOpacity, casterOpacity, casterOpacity, 1.0);", code("shadow.fsh"))
        lookup = code("lib", "shadow.glsl")
        self.assertIn("return 1.0 - texture2D(shadowcolor0, uv).r;", lookup)
        self.assertIn("? 1.0 : casterThrough(uv);", lookup)
        self.assertIn("through += casterThrough(", lookup)
        self.assertIn("return through / blockers;", lookup)

    def test_plants_have_a_lit_and_a_shaded_side(self):
        # the same answer: a plant's planes take a normal PLANT_SIDE of the way from up toward the face the camera sees
        # (0 = lit as the ground, the old pack). At noon (sun ~60 degrees up) a plane facing the sun reads 1.00-1.10 of
        # the open lawn and one facing away 0.75-0.90: a lit side and a shaded side, never a dark plane
        fsh = spc.code_of(read("program", "lit.fsh"))
        light = spc.code_of(read("lib", "light.glsl"))
        s = spc.float_const(fsh, "PLANT_SIDE", 0.0)
        self.assertIn("n = normalize(mix(geomN, face, PLANT_SIDE));", fsh)
        sun = (math.cos(math.radians(60.0)), math.sin(math.radians(60.0)))      # (toward the sun's azimuth, up)
        lawn = spc.noon_light(light, 1.0, sun[1])
        sides = []
        for facing in (1.0, -1.0):
            n = (s * facing, 1.0 - s)
            length = math.hypot(*n)
            n = (n[0] / length, n[1] / length)
            sides.append(spc.noon_light(light, n[1], n[0] * sun[0] + n[1] * sun[1]) / lawn)
        self.assertTrue(1.00 <= sides[0] <= 1.10, "sunlit side at %.3f of the lawn" % sides[0])
        self.assertTrue(0.75 <= sides[1] <= 0.90, "shaded side at %.3f of the lawn" % sides[1])

    def test_grade_matches_the_mods_enchanted_look(self):
        # one table for both looks (design: players with and without the pack see the same mood). The pack's
        # final.fsh copies the Enchanted column of WorldLookPass (#326); until #326 is on this branch there is no third
        # column to compare and the test skips
        with open(os.path.join(spc.ROOT, "..", "..", "bar", "src", "main", "java", "com", "solegendary",
                               "reignofnether", "postfx", "WorldLookPass.java"), encoding="utf-8") as f:
            java = f.read()
        fsh = read("final.fsh")
        pairs = spc.enchanted_pairs(java, fsh)
        if pairs is None:
            self.skipTest("WorldLookPass has no Enchanted column yet (#326)")
        for name, want, got in pairs:
            self.assertAlmostEqual(want, got, places=4, msg=name)

    def test_material_maps_reach_the_lit_draws_but_not_water(self):
        # slice 2 reads our _n / _s maps through one switch (lib/pbrswitch.glsl): terrain, entities, block entities and
        # the hand pass LIT_PBR. Water has no _n / _s maps and its own surface (lib/water.glsl), so it stays out
        switch = read("lib", "pbrswitch.glsl")
        self.assertIn("!defined LIT_WATER", switch)
        for name in ("gbuffers_terrain.fsh", "gbuffers_entities.fsh", "gbuffers_block.fsh", "gbuffers_hand.fsh"):
            src = spc.expand(os.path.join(spc.SHADERS, name))
            self.assertIn("uniform sampler2D normals;", src, name)
            self.assertIn("uniform sampler2D specular;", src, name)
        self.assertIn("#define LIT_WATER", read("gbuffers_water.fsh"))
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

    def test_wind_moves_the_shadow_with_the_leaves(self):
        # lib/wind.glsl: the terrain pass and the shadow pass call the same function on the same world position, so
        # a leaf's shadow never detaches from the leaf (Oct 30)
        for name in ("gbuffers_terrain.vsh", "shadow.vsh"):
            src = spc.expand(os.path.join(spc.SHADERS, name))
            self.assertIn("windOffset(playerPos + cameraPosition", src, name)
        props = read("block.properties")
        self.assertIn("block.10004=oak_leaves", props)
        self.assertIn("#define ID_LEAVES 10004.0", read("lib", "settings.glsl"))

    def test_block_ids_are_unique_and_named(self):
        # two features claiming one id would light leaves as water (or water as leaves) with nothing failing: #437's
        # leaves and #438's water both took 10002 until Nov 5. Each block.N line once, each block under one id, and
        # every id the programs name (#define ID_*) has its line and the other way round
        props = read("block.properties").replace("\\\r\n", " ").replace("\\\n", " ")
        ids = {}
        owner = {}
        for line in props.splitlines():
            m = re.match(r"block\.(\d+)=(.*)", line)
            if not m:
                continue
            self.assertNotIn(m.group(1), ids, "block.%s is declared twice" % m.group(1))
            ids[m.group(1)] = m.group(2).split()
            for block in ids[m.group(1)]:
                self.assertNotIn(block, owner, "%s is under block.%s and block.%s" % (block, owner.get(block), m.group(1)))
                owner[block] = m.group(1)
        named = re.findall(r"#define (ID_\w+) (\d+)\.0", read("lib", "settings.glsl"))
        values = [v for _, v in named]
        self.assertEqual(len(values), len(set(values)), "two ID_ defines share an id: %s" % named)
        self.assertEqual(set(values), set(ids), "ids without a define or without a block.N line: %s vs %s"
                         % (sorted(values), sorted(ids)))

    def test_options_compile_both_ways(self):
        # every option a program reads is compiled at both ends of its range (a toggle on and off)
        src = spc.expand(os.path.join(spc.SHADERS, "gbuffers_terrain.vsh"))
        self.assertIn("WIND=0", [label for label, _ in spc.option_variants(src)])

    def test_water_has_its_own_surface_with_a_fallback(self):
        """Changed in slice 4 (claude/shader-water): this guard used to pin water to slice 1's plain surface ("water
        keeps slice 1's surface until slice 4"). Slice 4 is here, so it now pins the new contract instead: water is
        its own block id, gbuffers_water reads the opaque depth under it (depthtex1) to set its opacity from the
        path through the water, and WATER_REALISM 0 still compiles to slice 1's one fixed alpha for an A/B."""
        props = read("block.properties")
        self.assertRegex(props, r"(?m)^block\.10002=water\b")
        settings = read("lib", "settings.glsl")
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
        multiply by one number per pixel (composite). The water's hue (test_water_turns_the_bed_turquoise) moves a
        wading unit's team colour by at most a tenth."""
        import math
        src = read("lib", "water.glsl")

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
        comp = read("composite.fsh")
        self.assertRegex(comp, r"lit \*= max\(1\.0 \+ \(water\.g - 0\.5\) / CAUSTIC_ENCODE, 0\.0\);")
        # the water layer's data goes to colortex5 from gbuffers_water only (after colortex1, slice 3's sky share): the
        # directive the loader reads is the preprocessed program's
        water = spc.preprocess(spc.expand(os.path.join(spc.SHADERS, "gbuffers_water.fsh")))
        terrain = spc.preprocess(spc.expand(os.path.join(spc.SHADERS, "gbuffers_terrain.fsh")))
        self.assertEqual(re.findall(r"/\* DRAWBUFFERS:(\d+) \*/", water), ["015"])
        self.assertEqual(re.findall(r"/\* DRAWBUFFERS:(\d+) \*/", terrain), ["01"])
        self.assertIn("uniform sampler2D colortex5;", comp)

    def test_water_turns_the_bed_turquoise(self):
        # Fable critic, round 1: under one alpha a sand bed a block down read "grey-khaki, could pass for a gravel slab".
        # Composite gives what shows through the water its hue over the path (lib/water.glsl's waterHue: red taken
        # first, luma kept) on the pixel's transmitted share. The GLSL's own lines are pinned and its constants read:
        water = read("lib", "water.glsl")
        comp = read("composite.fsh")
        lit = read("program", "lit.fsh")
        self.assertIn("vec3 t = exp(-WATER_ABSORPTION * max(path, 0.0));", water)
        self.assertIn("return t / max(dot(t, LUMA), 1e-3);", water)
        self.assertIn("lit *= mix(vec3(1.0), waterHue(path), 1.0 - waterOpacity(path));", comp)
        self.assertIn("gl_FragData[2] = vec4(waterPathEncode(path),", lit)
        sigma = spc.vec3_const(water, "WATER_ABSORPTION")
        k, deep = spc.float_const(water, "WATER_EXTINCTION"), spc.float_const(water, "WATER_DEEP_OPACITY")
        base, top = spc.float_const(water, "WATER_PATH_BASE"), spc.float_const(water, "WATER_PATH_MAX")
        luma = (0.2126, 0.7152, 0.0722)

        def seen(colour, path):              # composite's tint on a colour that shows through `path` blocks
            t = [math.exp(-s * path) for s in sigma]
            hue = [x / sum(a * b for a, b in zip(t, luma)) for x in t]
            share = 1.0 - deep * (1.0 - math.exp(-k * path))
            return [c * (1.0 + (h - 1.0) * share) for c, h in zip(colour, hue)]

        sand = (0.60, 0.50, 0.33)            # lit sand, linear
        for path in (0.0, 0.5, 1.1, 3.3, 8.0):
            out = seen(sand, path)
            # the hue alone: the luma of what shows is unchanged (readability is waterOpacity's)
            self.assertAlmostEqual(sum(a * b for a, b in zip(out, luma)), sum(a * b for a, b in zip(sand, luma)),
                                   delta=0.02 * sum(a * b for a, b in zip(sand, luma)))
        ford = seen(sand, 1.1)
        # a clear shift (the look itself is the Fable critic's call): the wading bound below caps it - with the tint on the
        # transmitted share, a 1-block ford can turn at most ~1.45x as far as half a block does
        self.assertGreaterEqual((ford[2] / ford[0]) / (sand[2] / sand[0]), 1.15, "a 1-block ford's bed is not bluer")
        for team in ((0.8, 0.1, 0.1), (0.1, 0.2, 0.8), (0.9, 0.8, 0.1)):
            wading = seen(team, 0.5)
            self.assertTrue(all(abs(w / c - 1.0) <= 0.10 for w, c in zip(wading, team)), (team, wading))
        # the path survives the water layer's 8 bits and stays above composite's water flag (r > 0.5)
        self.assertGreater(base, 0.5)
        self.assertLessEqual(top / ((1.0 - base) * 255.0), 0.07)    # one step <= 0.07 blocks

    def test_water_body_has_no_seams_and_leans_to_the_palette(self):
        # Fable critic, rounds 1 and 2: a grid of seams across the pond (the water texture's per-block pattern) and a
        # grey body with a black far bank. The water reads its texture's average and the ripples draw the detail; the
        # body is lifted toward the palette's #3A6A8C, blue most (a measured step, lib/water.glsl)
        lit = read("program", "lit.fsh")
        water = read("lib", "water.glsl")
        # one texture read with one bias: the water body's WATER_TEXTURE_BIAS replaces the RTS sharpening (#437)
        code = spc.code_of(lit)
        self.assertIn("#ifdef WATER_SURFACE if (waterBody > 0.5) lodBias = WATER_TEXTURE_BIAS; #endif "
                      "vec4 tex = texture2D(texture, texcoord, lodBias);", code)
        self.assertEqual(code.count("texture2D(texture, texcoord"), 1)
        self.assertGreaterEqual(spc.float_const(water, "WATER_TEXTURE_BIAS"), 4.0)   # mip 4+: 128 px down to 8 or less
        self.assertIn("c *= WATER_BODY_GAIN * mix(SHALLOW_TINT,", lit)
        gain = spc.vec3_const(water, "WATER_BODY_GAIN")
        self.assertTrue(1.0 <= gain[0] <= gain[1] <= gain[2], gain)
        # never past the measured gap (linear 1.7 / 3.0 / 4.0 from (44, 63, 72) to (58, 106, 140) on screen)
        self.assertTrue(all(g <= cap for g, cap in zip(gain, (1.7, 3.0, 4.0))), gain)

    def test_no_two_passes_give_one_buffer_two_formats(self):
        # the loader takes one format and one clear colour per colortex, from whichever file it reads last: two passes
        # that use one buffer for different data (slice 3's half-resolution occlusion and slice 4's water layer once
        # both claimed colortex2) would silently corrupt one of them
        seen = {}
        for dirpath, _, files in os.walk(spc.SHADERS):
            for n in sorted(files):
                if not n.endswith((".vsh", ".fsh", ".glsl")):
                    continue
                with open(os.path.join(dirpath, n), encoding="utf-8") as f:
                    src = f.read()
                for buf, kind, value in re.findall(
                        r"const (?:int|vec4) colortex(\d+)(Format|ClearColor) = ([^;]+);", src):
                    seen.setdefault((buf, kind), {}).setdefault(re.sub(r"\s+", "", value), []).append(n)
        for (buf, kind), values in sorted(seen.items()):
            self.assertEqual(len(values), 1, "colortex%s%s: %s" % (buf, kind, values))

    def test_reflections_march_only_where_they_show(self):
        """Screen-space reflections (composite.fsh) cost a 16-step march per water pixel; the budget (slice 4: +1.0 ms
        in all on a 3070 Ti at 1080p) only allows it where the mirror is visible. Fresnel for water at the RTS camera's
        ~65 degrees down, with the ripples' steepest tilt (~0.17 rad more), stays under the march's threshold, so from
        above the march never runs; a grazing first-person view passes it."""
        import math
        comp = read("composite.fsh")
        water = read("lib", "water.glsl")
        threshold = float(re.search(r"const float SSR_MIN_FRESNEL = ([0-9.]+);", comp).group(1))
        f0 = float(re.search(r"const float WATER_F0 = ([0-9.]+);", water).group(1))

        def fresnel(off_normal_deg):
            return f0 + (1.0 - f0) * (1.0 - math.cos(math.radians(off_normal_deg))) ** 5

        self.assertLess(fresnel(25.0 + math.degrees(0.17)), threshold)   # the RTS view: no march
        self.assertGreater(fresnel(80.0), threshold)                     # first person near the horizon: mirror
        settings = read("lib", "settings.glsl")
        self.assertRegex(settings, r"(?m)^#define SSR 1 // \[0 1\]")
        self.assertRegex(settings, r"(?m)^#define CAUSTICS 1 // \[0 1\]")

    def test_no_program_declares_a_name_twice(self):
        # GLSL 120 rejects a second `uniform vec3 cameraPosition;`: #438's water declared cameraPosition and
        # frameTimeCounter in lit.fsh and #437's wind.glsl declared them again, so with the default options
        # gbuffers_water.fsh would not compile (Eris.14, #437 round 1). lib/world.glsl declares them once. Every program
        # at every option-screen variant, after its #if / #ifdef (glslang runs only where it is installed)
        for name in spc.programs():
            for label, src in spc.variants_of(spc.expand(os.path.join(spc.SHADERS, name))):
                self.assertEqual(spc.duplicate_declarations(src), [], "%s [%s]" % (name, label or "defaults"))
        for lib in ("wind.glsl", "clouds.glsl", "water.glsl", "pbr.glsl", "light.glsl", "shadow.glsl"):
            self.assertNotRegex(read("lib", lib), r"(?m)^\s*uniform\s+\w+\s+(cameraPosition|frameTimeCounter)\s*;", lib)

    def test_noon_light_leaves_open_ground_its_colour(self):
        # lib/light.glsl aims open ground at noon to come out at its albedo. Itachi 1 measured the lawn under the pack
        # (eyes 41759309261 vs the plain lane 41759297367, same build, ground sampler, noon): every sunlit ground
        # material lost ~17% of its blue against its red, and the green lawn read olive. A warm sun is fine; the sun and
        # the sky's fill together on open ground must stay white, or every colour on the field turns toward yellow. The
        # lobby's noon (6000, the 30 degree tilt) puts the sun ~60 degrees up: an up face in full sky light, unshadowed,
        # takes the sun through sunFacing and the fill through surfaceLight's side share, plus MIN_LIGHT. The test reads
        # those constants and checks the two formulas still use them (review of #506: a change there must go red)
        src = read("lib", "light.glsl")
        self.assertIn("return mix(ndl, sqrt(ndl), SUN_FACING_EASE);", src)
        self.assertIn("(SKY_FILL_SIDE + (1.0 - SKY_FILL_SIDE) * upness)", src)
        self.assertIn("light += skyFill(n, levels, ao, texAo, skyColour);", src)   # surfaceLight's fill is skyFill
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
        src = read("final.fsh")
        for lc in (0.30, 0.35, 0.40, 0.45, 0.50):
            self.assertLessEqual(spc.split_tone_shadows(src, lc), 0.05, "sunlit ground at luma %.2f is tinted" % lc)
        self.assertGreaterEqual(spc.split_tone_shadows(src, 0.15), 0.5, "deep shade lost the warm tone")

    @unittest.skipUnless(spc.find_glslang(), "glslang not installed")
    def test_every_program_compiles(self):
        self.assertEqual(spc.check(spc.find_glslang()), [])


if __name__ == "__main__":
    unittest.main()
