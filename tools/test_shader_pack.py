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


def _read(*parts):
    return open(os.path.join(spc.SHADERS, *parts), encoding="utf-8").read()


def _const(name, src=None):
    """A `const float NAME = number;` from lib/settings.glsl (or the given source)."""
    src = src if src is not None else _read("lib", "settings.glsl")
    m = re.search(r"const float %s = (-?[\d.]+);" % name, src)
    if m is None:
        raise AssertionError("no const float %s" % name)
    return float(m.group(1))


def _s_curve(x, day=1.0):
    """A model of final.fsh's sCurve() and its shoulder, from lib/settings.glsl's constants (luma in, luma out)."""
    pivot, slope, toe, span = (_const(n) for n in ("S_CURVE_PIVOT", "S_CURVE_SLOPE", "S_CURVE_TOE", "S_CURVE_LIFT_SPAN"))
    if x < pivot:
        u = 1.0 - x / pivot
        y = x * (1.0 - day * (slope * u + toe * u * u))
    else:
        d = min(x - pivot, span)
        y = x + day * slope * (d - d * d / (2.0 * span))
    start, top = _const("DISPLAY_SHOULDER"), _const("DISPLAY_WHITE")
    if y > start:
        y = start + (top - start) * (1.0 - math.exp(-(y - start) / (top - start)))
    return y


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
        # slice 3's judged number: zero bloom on a plain noon lawn. Retired pin: "BLOOM_THRESHOLD >= 1.0" held while the
        # light table put sunlit ground at its albedo; the light pass lights it to LIGHT_OPEN_GROUND x its albedo, so
        # the threshold is that (white quartz, albedo < 1, stays under it) - the same guarantee, not a looser one
        comp = open(os.path.join(spc.SHADERS, "composite.fsh"), encoding="utf-8").read()
        self.assertIn("const float BLOOM_THRESHOLD = LIGHT_OPEN_GROUND;", comp)
        self.assertGreaterEqual(_const("LIGHT_OPEN_GROUND"), 1.0)

    # ---- the light pass (Oct 30, lovish's option B): the targets are named constants in lib/settings.glsl; these pin
    # the arithmetic from them to the numbers the eyes stills are judged on ----

    def test_light_table_hits_the_lawn_and_shadow_targets(self):
        # the noon sun is 60 degrees up (sunPathRotation 30): open ground takes LIGHT_OPEN_GROUND x its albedo in all,
        # the sky's share of it SHADOW_FILL_SHARE (a cast shadow on the lawn keeps that much). The tint lumas and the
        # noon facing in light.glsl are written out as numbers: they must be what they claim
        import math
        light = _read("lib", "light.glsl")
        luma = (0.2126, 0.7152, 0.0722)
        for tint in ("SUN_TINT_HIGH", "SKY_TINT_HIGH"):
            rgb = [float(x) for x in re.search(r"const vec3 %s = vec3\(([^)]*)\);" % tint, light).group(1).split(",")]
            self.assertAlmostEqual(sum(a * b for a, b in zip(rgb, luma)), _const(tint + "_LUMA", light), places=3)
        ndl = math.sin(math.radians(90.0 - _const("sunPathRotation")))
        self.assertAlmostEqual((ndl + math.sqrt(ndl)) / 2.0, _const("NOON_SUN_FACING", light), places=3)
        share, total = _const("SHADOW_FILL_SHARE"), _const("LIGHT_OPEN_GROUND")
        self.assertGreaterEqual(share, 0.62)                   # the coordinator's floor: shadow >= 0.62 of lit
        # Retired pin (light pass 5): "1.2 <= LIGHT_OPEN_GROUND <= 1.4" - it was set for a lawn ~0.42-0.43 on screen, but
        # with the S-curve, the meadow and pass 4 on top the pass-4 noon stills measured the lit lawn at 0.45-0.47. The
        # target is the lawn, so the band follows it: 1.05-1.20 (1.12 takes 0.46 back to ~0.42 through the curve)
        self.assertTrue(1.05 <= total <= 1.20, total)
        self.assertIn("SUN_TINT_HIGH\n    * ((1.0 - SHADOW_FILL_SHARE) * LIGHT_OPEN_GROUND", light)
        self.assertIn("SKY_TINT_HIGH * (SHADOW_FILL_SHARE * LIGHT_OPEN_GROUND", light)
        # the shadow light is cooler than the lit light (the sky's tint against the sun's): blue over green
        sun = [float(x) for x in re.search(r"const vec3 SUN_TINT_HIGH = vec3\(([^)]*)\);", light).group(1).split(",")]
        sky = [float(x) for x in re.search(r"const vec3 SKY_TINT_HIGH = vec3\(([^)]*)\);", light).group(1).split(",")]
        self.assertGreater(sky[2] / sky[1], sun[2] / sun[1])

    def test_walls_get_the_hemisphere_fill(self):
        # an unlit wall: half the sky, half the ground's bounce (GROUND_ALBEDO of the sun and sky on the ground).
        # Target ~0.68-0.72 of a sunlit top on screen, i.e. ~0.43-0.55 in linear light; slice 2's GROUND_BOUNCE is
        # folded in, not stacked on top
        light = _read("lib", "light.glsl")
        self.assertNotIn("const float GROUND_BOUNCE", light)
        self.assertIn("float skySeen = 0.5 + 0.5 * dot(n, up);", light)
        share, ga = _const("SHADOW_FILL_SHARE"), _const("GROUND_ALBEDO")
        wall = 0.5 * share + 0.5 * ga                          # of the sunlit top's light (sun + sky = 1)
        self.assertTrue(0.43 <= wall <= 0.55, wall)
        self.assertLess(wall, share)                           # a wall in shade sits below the shaded lawn

    def test_shadows_are_soft_and_never_self_shadow(self):
        # Retired pins (light pass 2): "PENUMBRA_MIN_BLOCKS ~3-6 px at the default zoom" and "a 5-block caster's tip
        # 3-6.5 px". Light pass 1 set the floor at 0.10 block so no edge was a razor line; in game every shadow then
        # read as an edgeless darkening (critic, 5.5/10: "things float", the penumbra 20+ px at rts_close) and a shadow
        # a block or two across never reached its full depth. The coordinator's target replaces them: a crisp contact
        # and a 2-4 px penumbra at rts_close (~67 px a block) for the usual 2-3 block casters, widening with distance.
        # The slope cap's shortfall is still biased back in every direction (the sun-grazed walls' slivers)
        # Retired pin (light pass 3): "the contact 1-2.5 px at rts_close" (PENUMBRA_MIN_BLOCKS 0.03). In game that edge
        # was ~0 px, a razor that showed the shadow map's texels as stair-steps (critic 6.5/10, N_s2_blob); the target
        # is now 2-4 px at the contact too, and never under 1.5 shadow texels of filter radius (no stair-steps)
        close = 67.0
        along = 1.0 / math.sin(math.radians(60.0))            # blocks along the light per block of caster height
        contact = _const("PENUMBRA_MIN_BLOCKS") * close
        self.assertTrue(2.0 <= contact <= 4.0, contact)
        self.assertGreaterEqual(_const("PENUMBRA_MIN_TEXELS", _read("lib", "shadow.glsl")), 1.5)
        # t11800's long shadows (sun ~13 degrees up): a 3-block caster's tip, 6-10 px at the default zoom (~33 px)
        tip = 3.0 / math.sin(math.radians(13.0)) * _const("SUN_SPREAD") * 33.0
        self.assertTrue(6.0 <= tip <= 10.0, tip)

        def edge(height):
            return max(height * along * _const("SUN_SPREAD"), _const("PENUMBRA_MIN_BLOCKS")) * close

        self.assertTrue(2.0 <= edge(2.0) <= 4.0, edge(2.0))
        self.assertTrue(2.0 <= edge(3.0) <= 4.0, edge(3.0))
        self.assertTrue(edge(5.0) <= 7.0, edge(5.0))
        self.assertLess(edge(2.0), edge(5.0))                  # widening with the blocker's distance
        shadow = _read("lib", "shadow.glsl")
        self.assertEqual(shadow.count("shortfall * length(o)"), 2)
        self.assertNotIn("const float SUN_SPREAD", shadow)
        # the blocker search reaches the widest penumbra it draws (a caster SHADOW_SEARCH_BLOCKS away)
        self.assertIn("0.5 * SHADOW_SEARCH_BLOCKS * SUN_SPREAD / blocksPerUv", shadow)

    def test_contact_ao_never_crushes(self):
        # Retired pin: slice 3's AO_FLOOR 0.55 (picked for the judged floor at 0.35 of the lawn); with the vertex AO
        # under it, block-block contacts and wall feet went near black on the stills. The floor is now >= 0.85
        self.assertGreaterEqual(_const("CONTACT_AO_FLOOR"), 0.85)
        self.assertIn("const float AO_FLOOR = CONTACT_AO_FLOOR;", _read("lib", "ao.glsl"))
        self.assertIn("max(ao, AO_FLOOR)", _read("deferred.fsh"))

    def test_display_keeps_whites_white_and_unclipped(self):
        # Retired pin (light pass 2): "DISPLAY_WHITE <= 0.96" held whites at ~0.92 on the stills (p99 0.92 against the
        # benchmark's 0.93-0.95); the coordinator's target is p99 >= 0.95, toward ~0.97, still unclipped. The shoulder
        # never reaches DISPLAY_WHITE, which stays under 1 (no channel clips), and the brightest white the stills showed
        # (grade output ~1.02) lands 0.95-0.975. Past WHITE_GUARD_LUMA ~30% of the colour goes: whites read white
        top, start = _const("DISPLAY_WHITE"), _const("DISPLAY_SHOULDER")
        self.assertLess(top, 0.99)
        r = top - start
        for lum in (0.9, 1.0, 1.2, 2.0):
            self.assertLess(start + r * (1.0 - math.exp(-(lum - start) / r)), top)
        white = _s_curve(1.02)
        self.assertTrue(0.95 <= white <= 0.975, white)
        self.assertAlmostEqual(_const("WHITE_DESATURATE"), 0.30)
        self.assertAlmostEqual(_const("WHITE_GUARD_LUMA"), 0.85)
        final = _read("final.fsh")
        self.assertIn("perspectiveGeometry ? DISPLAY_SHOULDER_PERSPECTIVE : start", final)
        self.assertIn("float start = mix(DISPLAY_SHOULDER, DISPLAY_SHOULDER_LOW, low);", final)
        self.assertIn("whiteDesaturate * smoothstep(WHITE_GUARD_LUMA", final)
        self.assertIn("perspectiveGeometry ? WHITE_DESATURATE_PERSPECTIVE : WHITE_DESATURATE", final)

    def test_s_curve_keeps_the_lawn_and_scales_the_darks(self):
        # light pass 2: the lit lawn is the curve's fixed point (lovish: ~0.42 on his palette band), the darks go down
        # in proportion. Inputs are the model's on-screen values before the curve (light table + grade): a cast shadow
        # on the lawn 0.33 (the lawn 0.42), blackstone's sunlit top 0.27, a dark block's unlit side ~0.20
        pivot = _const("S_CURVE_PIVOT")
        self.assertAlmostEqual(pivot, 0.42)
        self.assertAlmostEqual(_s_curve(pivot), pivot, places=6)
        self.assertTrue(0.70 <= _s_curve(0.33) / pivot <= 0.75, _s_curve(0.33))
        self.assertLessEqual(_s_curve(0.27), 0.23)
        self.assertTrue(0.14 <= _s_curve(0.20) <= 0.20, _s_curve(0.20))
        # monotonic, never below zero, and the lawn's mean holds (its grain moves about as much up as down)
        xs = [i / 200.0 for i in range(1, 241)]
        ys = [_s_curve(x) for x in xs]
        self.assertTrue(all(b > a for a, b in zip(ys, ys[1:])))
        self.assertGreater(ys[0], 0.0)
        self.assertAlmostEqual((_s_curve(0.37) + _s_curve(0.47)) / 2.0, 0.42, delta=0.005)
        # at night (day = 0) the curve is the identity below the shoulder
        self.assertAlmostEqual(_s_curve(0.15, day=0.0), 0.15)
        final = _read("final.fsh")
        self.assertIn("float day = smoothstep(S_CURVE_DAY_LO, S_CURVE_DAY_HI, sunH);", final)

    def test_glow_never_floors_the_darks(self):
        # the glow's screen blend lifted every pixel near a white block by the same amount (+~0.09 on a dark face):
        # its lift now scales with the pixel's own luma; the perspective sky takes no glow and no local contrast
        final = _read("final.fsh")
        self.assertIn("pow(clamp(dot(c, LUMA) / GLOW_THRESHOLD, 0.0, 1.0), GLOW_DARK_GUARD)", final)
        self.assertNotIn("c = 1.0 - (1.0 - c) * (1.0 - GLOW * glowAt", final)
        self.assertGreaterEqual(_const("GLOW_DARK_GUARD"), 1.0)
        self.assertIn("if (!sky) {", final)

    def test_sky_has_a_gradient_the_haze_shares(self):
        # light pass 2: by day a bluer zenith over a paler horizon; the perspective views' aerial haze fades far ground
        # into that horizon. Light pass 4: as the sun sinks the horizon warms toward the sun (gold at t11800, deep orange
        # at dusk: >= 15 degrees of hue and >= 0.08 luma apart) and the zenith deepens; the haze's air follows
        settings = _read("lib", "settings.glsl")
        luma = (0.2126, 0.7152, 0.0722)

        def vec(name):
            return [float(x) for x in re.search(r"const vec3 %s = vec3\(([^)]*)\);" % name, settings).group(1).split(",")]

        import colorsys
        zen, hor = vec("SKY_ZENITH_DAY"), vec("SKY_HORIZON_DAY")
        self.assertGreater(sum(a * b for a, b in zip(zen, luma)), sum(a * b for a, b in zip(hor, luma)) + 0.08)
        self.assertGreater(zen[2] / zen[0], hor[2] / hor[0])   # bluer at the zenith
        gold, deep = vec("SKY_SUNSET_GOLD"), vec("SKY_SUNSET_DEEP")
        hg, sg, _ = colorsys.rgb_to_hsv(*gold)
        hd, sd, _ = colorsys.rgb_to_hsv(*deep)
        self.assertTrue(25 <= hg * 360 <= 40 and sg >= 0.3, (hg * 360, sg))
        # Retired pin (light pass 5): "gold and deep >= 15 degrees and >= 0.08 luma apart" - it made the dusk horizon a
        # dark red-orange band (0.55 luma, hue 15) that read as a flat peach wash with the haze. The critic's dusk target
        # replaces it: a horizon band ~0.80 luma at hue 20-30 under a zenith <= 0.66 at hue 210-220, >= 0.12 luma apart
        zd = vec("SKY_ZENITH_DUSK")
        self.assertTrue(20 <= hd * 360 <= 30, hd * 360)
        self.assertTrue(0.74 <= sum(a * b for a, b in zip(deep, luma)) <= 0.84)
        hz, sz, _ = colorsys.rgb_to_hsv(*zd)
        self.assertTrue(205 <= hz * 360 <= 220 and 0.2 <= sz <= 0.32, (hz * 360, sz))
        self.assertLessEqual(sum(a * b for a, b in zip(zd, luma)), 0.66)
        self.assertGreaterEqual(sum(a * b for a, b in zip(deep, luma)) - sum(a * b for a, b in zip(zd, luma)), 0.12)
        clouds = _read("program", "clouds.fsh")
        self.assertIn("skyGradient(normalize(viewPos)) * CLOUD_SKY", clouds)
        self.assertIn("c = mix(c, skyGradient(dir), skyOwn());", _read("program", "sky.fsh"))
        self.assertIn("mix(fogColor, skyAir(), skyOwn())", _read("composite.fsh"))

    def test_metals_reflect_a_lit_environment(self):
        # light pass 2: a metal's reflection below the horizon is the lit ground (the fill's hemisphere), dimmed by a
        # low sky light no further than the fill's floor; only F0 codes from DIELECTRIC_F0_MAX_CODE up count as metal,
        # a mip's blend in between as its share of metal (the pack's dielectrics stop at 44, its metals are 230+)
        pbr = _read("lib", "pbr.glsl")
        self.assertIn("GROUND_TINT * GROUND_ALBEDO", pbr)
        self.assertIn("mix(SKY_FILL_FLOOR, 1.0, skyLevel)", pbr)
        self.assertIn("(code - DIELECTRIC_F0_MAX_CODE) / (229.5 - DIELECTRIC_F0_MAX_CODE)", pbr)
        code = _const("DIELECTRIC_F0_MAX_CODE")
        self.assertTrue(44 < code < 230, code)
        gen = open(os.path.join(spc.ROOT, "..", "..", "tools", "gen_blocks.py"), encoding="utf-8").read()
        for f0 in (int(x) for x in re.findall(r"\bf0=(\d+)", gen)):
            self.assertTrue(f0 <= 44 or 230 <= f0 <= 237 or f0 == 255, f0)   # no code a mip blend could mimic

    def test_grass_tufts_sit_in_the_lawns_band(self):
        # light pass 2: biome-tinted plants keep their albedo inside the lawn's band (the tufts read neon lime, luma
        # ~0.62 on a 0.42 lawn), take a little base occlusion, and never emit
        # Retired pins (light pass 3): FOLIAGE_MAX_LUMA <= 0.45 and FOLIAGE_BASE_AO 0.6-0.85 - with a 0.42 luma cap and
        # a 0.70 foot the tufts drew as dark grey smudges; the texture side now lightens their albedo, so only the
        # saturation guard stays, the foot gentler. Particles take the sun's shadow (a green one in a building's
        # shadow drew full-bright), and plants show no sun glint (the far tufts' white "snow" in the 34 views)
        self.assertLessEqual(_const("FOLIAGE_MAX_SAT"), 0.45)
        self.assertNotIn("FOLIAGE_MAX_LUMA", _read("lib", "settings.glsl"))
        self.assertTrue(0.80 <= _const("FOLIAGE_BASE_AO") <= 0.95)
        lit = _read("program", "lit.fsh")
        self.assertIn("ao *= mix(FOLIAGE_BASE_AO, 1.0, plantUp);", lit)
        self.assertIn("mat.emission = 0.0;", lit)
        self.assertIn("reflected *= 1.0 - foliage;", lit)
        particles = spc.expand(os.path.join(spc.SHADERS, "gbuffers_textured_lit.fsh"))
        self.assertIn("float shadow = sunShadow(playerPos", particles)

    def test_low_sun_rakes_warm_across_the_grass(self):
        # light pass 3: t11800 (sun ~13 degrees up) read as a dim room (lit lawn 0.22-0.24, shadow 0.15, 1.5:1). A model
        # of the light table at that height (lib/light.glsl's mix of the low and high rows, the facing with
        # LOW_SUN_RAKE): open ground takes >= 2.4x the light a cast shadow does in linear light (~2.8:1 on screen after
        # the S-curve, which the low sun now takes whole), and the sun is warmer than the noon sun
        light = _read("lib", "light.glsl")

        def vec3(name):
            m = re.search(r"const vec3 %s = vec3\(([^)]*)\)(?: \* ([\d.]+))?;" % name, light)
            rgb = [float(x) for x in m.group(1).split(",")]
            k = float(m.group(2)) if m.group(2) else 1.0
            return [x * k for x in rgb]

        luma = (0.2126, 0.7152, 0.0722)

        def lum(c):
            return sum(a * b for a, b in zip(c, luma))

        h = math.sin(math.radians(13.0))
        t = min(max((h - 0.10) / 0.45, 0.0), 1.0)
        high = t * t * (3.0 - 2.0 * t)
        sun_high = (1.0 - _const("SHADOW_FILL_SHARE")) * _const("LIGHT_OPEN_GROUND") / _const("NOON_SUN_FACING", light)
        sky_high = _const("SHADOW_FILL_SHARE") * _const("LIGHT_OPEN_GROUND")
        sun = lum(vec3("SUN_LOW")) * (1.0 - high) + sun_high * high
        sky = lum(vec3("SKY_LOW")) * (1.0 - high) + sky_high * high
        k = min(0.5 + 0.5 * _const("LOW_SUN_RAKE") * (1.0 - high), 1.0)      # sunFacingRaked: the grass
        facing = (1.0 - k) * h + k * math.sqrt(h)
        self.assertGreaterEqual((sun * facing + sky) / sky, 2.4)
        # light pass 4: only the grass rakes (block tops keep the plain facing, so a white top under the low shoulder
        # stays under ~0.90 against a ~0.37 lawn), and the low sun's shoulder sits lower
        self.assertIn("float facing = mix(sunFacing(n, l), sunFacingRaked(n, l), tinted);", _read("program", "lit.fsh"))
        self.assertLess(_const("DISPLAY_WHITE_LOW"), _const("DISPLAY_WHITE"))
        self.assertLessEqual(_const("DISPLAY_SHOULDER_LOW"), _const("DISPLAY_SHOULDER"))
        low = vec3("SUN_LOW")
        self.assertLess(low[2] / low[0], 0.30)                 # golden, not white
        self.assertLessEqual(_const("S_CURVE_DAY_HI"), h)      # the whole S-curve at t11800

    def test_shadows_lean_toward_the_sky(self):
        # light pass 3: where no sun arrives the fill is cooled by SHADE_TINT (luma 1, bluer than the sky tint), so a
        # shadow is the lit ground's colour shifted toward blue rather than the same hue darker
        settings = _read("lib", "settings.glsl")
        tint = [float(x) for x in re.search(r"const vec3 SHADE_TINT = vec3\(([^)]*)\);", settings).group(1).split(",")]
        self.assertAlmostEqual(sum(a * b for a, b in zip(tint, (0.2126, 0.7152, 0.0722))), 1.0, delta=0.01)
        # light pass 4: the pass-3 tint (blue 1.14 of the luma) did not read - the noon shadow stayed hue 97, blue 0.46 of
        # green. Now blue >= 2x red-weighted (in game the shadow's blue needs ~1.6x on screen for blue >= 0.75 green),
        # on the sky's part of the fill only
        # Retired pin (light pass 5): "blue > 2x green" - pass 4's indigo turned every shaded wall violet (H 231-251,
        # saturation up to 0.26). Now a sky blue, 1.2-1.5x green on faces looking up, a third of that on walls
        self.assertTrue(1.2 <= tint[2] / tint[1] <= 1.5, tint)
        self.assertTrue(0.25 <= _const("SHADE_TINT_WALL") <= 0.5)
        self.assertGreater(tint[2], tint[1])
        self.assertGreater(tint[1], tint[0])                  # blue-cyan, never violet (red above green)
        light = _read("lib", "light.glsl")
        self.assertIn("light += skyPart * (shadeTint(shadow * min(facing * 4.0, 1.0), upness) - 1.0);", light)
        # light pass 5: the fill has a direction (the castle's walls at noon 0.68 / 0.67): a face toward the sun's
        # azimuth against one turned away >= 1.5x of fill, tops untouched
        d = _const("FILL_DIRECTION")
        self.assertGreaterEqual((1.0 + d) / (1.0 - d), 1.5)
        self.assertLessEqual(d, 0.4)
        self.assertIn("* fillDirection(n);", _read("program", "lit.fsh"))

    def test_perspective_whites_keep_their_lines(self):
        # Retired pin (light pass 4): "a lit white wall lands 0.74-0.78" by a roll-off from 0.64 - in game it flattened
        # the castle into grey cardboard, lit and shaded faces 0.02 apart (critic 7.0/10). Now the highlights scale down
        # as a ratio (PERSPECTIVE_HIGHLIGHT, eased in over 0.5-0.9) and only the very top rolls off: a lit white wall
        # (~1.05 out of the grade) ~0.85-0.89, its shaded face (~0.85) ~0.68-0.74, at least 0.15 apart; the lawn untouched
        def persp(x):
            pivot, slope, span = _const("S_CURVE_PIVOT"), _const("S_CURVE_SLOPE"), _const("S_CURVE_LIFT_SPAN")
            d = min(max(x - pivot, 0.0), span)
            y = x + slope * (d - d * d / (2.0 * span))
            t = min(max((y - 0.5) / 0.4, 0.0), 1.0)
            y *= 1.0 - (1.0 - _const("PERSPECTIVE_HIGHLIGHT")) * t * t * (3.0 - 2.0 * t)
            start, top = _const("DISPLAY_SHOULDER_PERSPECTIVE"), _const("DISPLAY_WHITE_PERSPECTIVE")
            if y > start:
                y = start + (top - start) * (1.0 - math.exp(-(y - start) / (top - start)))
            return y

        lit, shaded = persp(1.05), persp(0.85)
        self.assertTrue(0.84 <= lit <= 0.90, lit)
        self.assertTrue(0.66 <= shaded <= 0.75, shaded)
        self.assertGreaterEqual(lit - shaded, 0.15)
        self.assertAlmostEqual(persp(0.42), 0.42, places=6)
        self.assertLess(_const("WHITE_DESATURATE_PERSPECTIVE"), _const("WHITE_DESATURATE"))

    def test_the_meadow_varies_across_the_field(self):
        # light pass 3 (round 41: the lawn read as uniform felt): a world-space low-frequency field on biome-tinted
        # terrain. Patches ~8-16 blocks (the noise cell), a value swing that adds ~0.04 of macro std on screen (numpy
        # model in the constant's comment: lawn std ~0.03 -> ~0.05, the 0.045-0.06 target), the mean held by the
        # log-normal normalisation; ~+-6 degrees of hue at the extremes
        # Retired pins (light pass 4): MEADOW_CELL 8-16 and MEADOW_VALUE 0.30-0.45 - the patches were imperceptible from
        # the RTS height (critic 7.0/10). Larger, softer cells and a value swing of ~0.06 luma std on screen
        self.assertTrue(14.0 <= _const("MEADOW_CELL") <= 22.0)
        self.assertTrue(0.50 <= _const("MEADOW_VALUE") <= 0.65)
        meadow = _read("lib", "meadow.glsl")
        self.assertIn("- 0.5 * (MEADOW_VALUE * 0.45) * (MEADOW_VALUE * 0.45)", meadow)
        lit = _read("program", "lit.fsh")
        self.assertIn("albedo = mix(albedo, meadow(albedo, world.xz), tinted);", lit)

    def test_white_sides_and_rough_glints(self):
        # light pass 3: near-white faces turned from up lose the warm light's colour (white wool's sides read cream);
        # a rough dielectric's glint is capped (nether bricks strobed on their bevels)
        # Retired pin (light pass 4): WHITE_WALL_NEUTRAL 0.6-1.0 - fully neutral, the perspective views' sunlit white walls
        # lost their warmth (the castle read hue-less); half of it keeps them warm without the cream cast
        self.assertTrue(0.4 <= _const("WHITE_WALL_NEUTRAL") <= 0.6)
        self.assertLessEqual(_const("ROUGH_GLINT_CAP"), 0.2)
        lit = _read("program", "lit.fsh")
        self.assertIn("WHITE_WALL_NEUTRAL * whiteness * side", lit)
        self.assertIn("mix(mix(ROUGH_GLINT_CAP, 1.0, glossy), 1.0, mat.metal)", lit)

    def test_the_sky_has_a_sun(self):
        # light pass 4: the loader does not name the sun's render stage, so vanilla's square sun is found by direction
        # in gbuffers_skytextured and dropped; our soft disc (warmer as the sun sinks) is drawn with the dome, which the
        # terrain covers. A glow round the moon
        sky = _read("program", "sky.fsh")
        self.assertIn("normalize(sunPosition)) > SUN_SPRITE_COS)\n        discard;", sky)
        self.assertNotIn("MC_RENDER_STAGE_SUN", sky)
        self.assertIn("discColour * ((disc + halo)", sky)
        self.assertIn("MOON_GLOW * moonUp", sky)
        self.assertTrue(0.85 <= _const("SUN_SPRITE_COS") <= 0.95)
        settings = _read("lib", "settings.glsl")
        for name in ("SUN_DISC_COLOUR", "SUN_DISC_LOW"):
            disc = [float(x) for x in re.search(r"const vec3 %s = vec3\(([^)]*)\)" % name, settings).group(1).split(",")]
            self.assertGreater(disc[0], disc[2])               # warm

    def test_no_film_grain(self):
        # the dither read as grain over the lawn at the RTS zoom: off, and the local contrast never sharpens a texel
        self.assertEqual(_const("DITHER_LSB"), 0.0)
        final = _read("final.fsh")
        self.assertIn("* (DITHER_LSB / 255.0)", final)
        self.assertIn("float fine = dot(texture2D(colortex0, uv, LOCAL_CONTRAST_FINE_LOD).rgb, LUMA);", final)
        self.assertGreaterEqual(_const("LOCAL_CONTRAST_FINE_LOD", final), 1.0)

    def test_aerial_perspective_in_perspective_views_only(self):
        # Retired pins (light pass 4): pass 3's "~50-58% at 150 blocks, 58-66% at 200" and "the air takes all the colour
        # it veils" overshot - the far ground took the sky's colour (0.62, saturation 0.20, hue 212) and the horizon
        # dissolved (critic 7.0/10). Now ~30-35% at 150-200 blocks without the extra desaturation: by the model the far
        # lawn keeps saturation ~0.3 and sits ~0.2 under the horizon. The orthographic RTS camera keeps its own haze
        start, scale, top = _const("AERIAL_START"), _const("AERIAL_SCALE"), _const("AERIAL_MAX")
        at150 = top * (1.0 - math.exp(-(150.0 - start) / scale))
        at200 = top * (1.0 - math.exp(-(200.0 - start) / scale))
        self.assertTrue(0.28 <= at150 <= 0.34, at150)
        self.assertTrue(0.32 <= at200 <= 0.38, at200)
        self.assertLessEqual(_const("AERIAL_DESAT"), 0.25)
        comp = _read("composite.fsh")
        self.assertIn("float start = ortho ? rtsHazeStart() : AERIAL_START;", comp)
        self.assertIn("float hazeMax = ortho ? HAZE_MAX : AERIAL_MAX;", comp)
        self.assertIn("(ortho ? 0.5 : AERIAL_DESAT)", comp)

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
