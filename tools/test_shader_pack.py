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


def _s_curve(x, day=1.0, low=0.0):
    """A model of final.fsh's sCurve() and its shoulder, from lib/settings.glsl's constants (luma in, luma out); low: the
    light table's low row (1 = the low sun's curve)."""
    pivot, span = _const("S_CURVE_PIVOT"), _const("S_CURVE_LIFT_SPAN")
    slope = _const("S_CURVE_SLOPE") * (1.0 - low) + _const("S_CURVE_SLOPE_LOW") * low
    toe = _const("S_CURVE_TOE") * (1.0 - low) + _const("S_CURVE_TOE_LOW") * low
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
        # Retired pin (light pass 10): "sunPathRotation = 30.0" - the tilt lovish picked (30 degrees) is kept, its sign
        # flipped: at +30 the sun stood in the north at day time 5000, turned from both faces the RTS camera sees
        self.assertIn("const float sunPathRotation = -30.0;", src)
        # Retired pin (light pass 7): "shadowMapResolution = 2048" - at 2048 the RTS fit drew ~2 px texels, so edges
        # stair-stepped or blurred into wavy bands (critic: the pad's shadow scalloped); 4096 by default now
        self.assertIn("const int shadowMapResolution = 4096;", src)

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
        # light pass 8: the sky's share fully, CONTACT_SUN_SHARE of the rest (contact darkening at block feet)
        self.assertIn("lin *= 1.0 - (skyShare + CONTACT_SUN_SHARE * (1.0 - skyShare))", comp)

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
        # Retired pin (light pass 13): "SHADOW_FILL_SHARE >= 0.62" (option B's floor, shadows lifted to ~0.78 of the lawn on
        # screen). The critic on pass 11 (5.5/10 against Complementary's 7.0): cast shadows 0.31 on a 0.40 lawn, target
        # 0.22-0.25 under a 0.43-0.46 lawn (>= 1.8:1). By the model of the chain (scratch, calibrated on the pass-11
        # stills) 0.34 gives ~0.242 / ~0.442; the sky a third of the light, as the benchmark's ambient. A cast shadow
        # still keeps >= 0.30 of the light (Erin, #386 r5: shadowed light ~35-45% of lit)
        # Retired pin (light pass 15): "0.30-0.40" - the first stills with our own pack (lp14o) put the 5000 cast shadow at
        # 0.22 under a 0.49 lawn (0.44 on screen; Complementary ~0.78): on screen a shadow keeps ~half its share in light
        # (sky light floor, broad AO, the grade's contrast). Target 0.6-0.65 of the lawn: 0.55-0.70
        self.assertTrue(0.55 <= share <= 0.70, share)
        # Retired pin (light pass 5): "1.2 <= LIGHT_OPEN_GROUND <= 1.4" - it was set for a lawn ~0.42-0.43 on screen, but
        # with the S-curve, the meadow and pass 4 on top the pass-4 noon stills measured the lit lawn at 0.45-0.47. The
        # target is the lawn, so the band follows it: 1.05-1.20 (1.12 takes 0.46 back to ~0.42 through the curve)
        # light pass 10: up to 1.30 (the 5000 lawn read 0.40 at 1.20; 1.26 now)
        # light pass 12: up to 1.50 (measured: the clear 5000 lawn 0.40 at 1.30; 1.45 now)
        # light pass 13: up to 1.60 (1.52 with the fill share 0.34: the lawn ~0.44 by the model)
        self.assertTrue(1.05 <= total <= 1.60, total)
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
        # light pass 12 put walls at WALL_FILL of the fill (lib/light.glsl fillDirection): the fill a wall gets
        wall *= _const("WALL_FILL")
        # Retired pin (light pass 9): "0.43-0.55 of a sunlit top" - with the fill's direction (pass 5) a wall turned
        # from the sun took ~0.36 of it and the pedestals' shaded sides read black (0.28, top / side 3.3 against the
        # benchmark's 1.9). Now 0.55-0.65 before the direction, ~0.5 after it for a wall turned away
        # Retired again (light pass 10): "0.55-0.65" - with the sun now on one visible face, pass 9's bounce had lifted
        # every side alike (quartz's sides 0.59 / 0.60, paper cut-outs). 0.48-0.55 now, the direction carrying the rest
        # Retired again (light pass 13): "0.48-0.55" before WALL_FILL - with the fill share 0.34 (deeper cast shadows) and
        # WALL_FILL 0.42 a wall turned from the sun keeps 0.12-0.20 of a sunlit top's light: quartz's shaded side ~0.62
        # on screen by the model (target 0.52, the benchmark 0.65), a stone pedestal's ~0.22
        self.assertTrue(0.12 <= wall <= 0.20, wall)
        # light pass 11: a wall turned fully from the sun keeps >= 0.12 of a sunlit top's light (its fill alone)
        self.assertGreaterEqual(wall * (1.0 - _const("FILL_DIRECTION")), 0.12)
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
        # Retired pins (light pass 12): "the contact 2-4 px at rts_close", "2-3 block casters 2-4 px at rts_close", "a
        # 5-block caster <= 7 px", "t11800's 3-block tip 6-10 px with the day's spread". The critic measured the 5000
        # edges at ~3-6 px against the benchmark's ~8 and asked for 8-10 px at the default zoom (~33 px a block); the low
        # sun's long shadows take half the spread (LOW_SUN_SPREAD -0.5)
        px = 33.0
        along = 1.0 / math.sin(math.radians(58.0))            # day time 5000
        contact = _const("PENUMBRA_MIN_BLOCKS") * px
        # Retired pin (light pass 15): "the contact 2.5-4.5 px" - lp14o: the shadow at a pillar's foot jumps 0.21 -> 0.49
        # in 2-3 px, Complementary grades it over ~12; target 4-8 px
        self.assertTrue(4.0 <= contact <= 8.0, contact)
        shadow_src = _read("lib", "shadow.glsl")
        self.assertGreaterEqual(_const("PENUMBRA_MIN_TEXELS", shadow_src), 1.0)
        self.assertGreaterEqual(_const("PENUMBRA_MIN_TEXELS_PERSPECTIVE", shadow_src), 2.0)

        def edge(height, spread=1.0):
            return max(height * along * _const("SUN_SPREAD") * spread, _const("PENUMBRA_MIN_BLOCKS")) * px

        # Retired pin (light pass 15): "a 5-block wall 7-11 px" - lp14o measured the edges at 6-9 px, target 10-14
        self.assertTrue(10.0 <= edge(5.0) <= 15.0, edge(5.0))  # a 5-block wall at 5000
        low = 1.0 + _const("LOW_SUN_SPREAD")
        tip = 3.0 / math.sin(math.radians(13.0)) * _const("SUN_SPREAD") * low * px
        self.assertTrue(8.0 <= tip <= 14.0, tip)
        self.assertLess(edge(2.0), edge(5.0))                  # widening with the blocker's distance
        shadow = _read("lib", "shadow.glsl")
        self.assertEqual(shadow.count("shortfall * length(o)"), 2)
        self.assertNotIn("const float SUN_SPREAD", shadow)
        # the blocker search reaches the widest penumbra it draws (a caster SHADOW_SEARCH_BLOCKS away)
        self.assertIn("0.5 * SHADOW_SEARCH_BLOCKS * spread / blocksPerUv", shadow)
        # light pass 9: under a low sun the spread widens (hazier long shadows)
        self.assertIn("float spread = SUN_SPREAD * gShadowSpread;", shadow)
        self.assertIn("gShadowSpread = 1.0 + LOW_SUN_SPREAD * lowSun();", _read("program", "lit.fsh"))

    def test_contact_ao_never_crushes(self):
        # Retired pin: slice 3's AO_FLOOR 0.55 (picked for the judged floor at 0.35 of the lawn); with the vertex AO
        # under it, block-block contacts and wall feet went near black on the stills. The floor is now >= 0.85
        # Retired pin (light pass 8): ">= 0.85" - it left the block feet's contact darkening at ~0.02 luma, the
        # benchmark's one real advantage on the pad (critic); 0.6-0.75 now, taking part of the sun's light too
        # Retired again (light pass 9): "0.6-0.75, 0.3-0.6 of the sun" - the broad occlusion then took the pedestals'
        # shaded sides to 0.28 (top / side 3.3; the benchmark 1.9). It is back at >= 0.85 on the sky's share alone,
        # and the block feet's crease is a short full-resolution term (composite.fsh contactOcclusion): ~4 px, floor 0.75
        self.assertGreaterEqual(_const("CONTACT_AO_FLOOR"), 0.85)
        self.assertEqual(_const("CONTACT_SUN_SHARE"), 0.0)
        self.assertTrue(0.70 <= _const("CONTACT_FLOOR") <= 0.80)
        # Retired (light pass 14): "CONTACT_RADIUS <= 0.2" - the critic on pass 12 found a 1-2 px hard band at the
        # pillars' feet (target a 3-5 px gradient): 0.26 over four rings weighed toward the foot
        self.assertTrue(0.08 <= _const("CONTACT_RADIUS") <= 0.3)
        self.assertIn("float w = 1.0 - 0.15 * ring;", _read("composite.fsh"))
        self.assertIn("lin *= contactOcclusion(uv, texture2D(depthtex1, uv).r);", _read("composite.fsh"))
        self.assertIn("const float AO_FLOOR = CONTACT_AO_FLOOR;", _read("lib", "ao.glsl"))
        self.assertIn("max(ao, AO_FLOOR)", _read("deferred.fsh"))

    def test_display_keeps_whites_white_and_unclipped(self):
        # Retired pin (light pass 2): "DISPLAY_WHITE <= 0.96" held whites at ~0.92 on the stills (p99 0.92 against the
        # benchmark's 0.93-0.95); the coordinator's target is p99 >= 0.95, toward ~0.97, still unclipped. The shoulder
        # never reaches DISPLAY_WHITE, which stays under 1 (no channel clips), and the brightest white the stills showed
        # (grade output ~1.02) lands 0.95-0.975. Past WHITE_GUARD_LUMA ~30% of the colour goes: whites read white
        top, start = _const("DISPLAY_WHITE"), _const("DISPLAY_SHOULDER")
        self.assertLess(top, 0.995)
        r = top - start
        for lum in (0.9, 1.0, 1.2, 2.0):
            self.assertLess(start + r * (1.0 - math.exp(-(lum - start) / r)), top)
        white = _s_curve(1.02)
        self.assertTrue(0.95 <= white <= 0.975, white)
        self.assertAlmostEqual(_const("WHITE_DESATURATE"), 0.30)
        self.assertAlmostEqual(_const("WHITE_GUARD_LUMA"), 0.85)
        final = _read("final.fsh")
        self.assertIn("perspectiveGeometry ? DISPLAY_SHOULDER_PERSPECTIVE : start", final)
        # light pass 13: the shoulder by the low sun in full from t11800's height (its whites stopped at ~0.86)
        self.assertIn("float start = mix(DISPLAY_SHOULDER, DISPLAY_SHOULDER_LOW, lowFull);", final)
        self.assertTrue(math.sin(math.radians(13.4)) <= _const("LOW_SUN_FULL_LO") <= 0.30)
        self.assertIn("whiteDesaturate * smoothstep(WHITE_GUARD_LUMA", final)
        self.assertIn("perspectiveGeometry ? WHITE_DESATURATE_PERSPECTIVE : WHITE_DESATURATE", final)

    def test_s_curve_keeps_the_lawn_and_scales_the_darks(self):
        # light pass 2: the lit lawn is the curve's fixed point (lovish: ~0.42 on his palette band). Light pass 6 split
        # it: the low sun keeps pass 2's curve (its 2.8:1 shadows scored 8.0-8.3) and its pins move to low=1; the day
        # (time 5000) takes a gentle one. Retired day pins, with the reason: "a cast shadow 0.33 -> 0.70-0.75 of the
        # lawn", "blackstone 0.27 -> <= 0.23", "a 0.20 dark side -> 0.14-0.20" - the day's darks were crushed (black
        # concrete 0.106, five dark blocks at 0.15 +- 0.004, the dark pad's median 0.27 against the benchmark's 0.48)
        # and the pivot slope sharpened the lawn's grain (0.045 against 0.037)
        pivot = _const("S_CURVE_PIVOT")
        self.assertAlmostEqual(pivot, 0.42)
        for low in (0.0, 1.0):
            self.assertAlmostEqual(_s_curve(pivot, low=low), pivot, places=6)
        # the low sun: pass 2's pins
        self.assertTrue(0.70 <= _s_curve(0.33, low=1.0) / pivot <= 0.75, _s_curve(0.33, low=1.0))
        # light pass 12: the low sun's toe 0.36 -> 0.15 (its shadows were crushed back): <= 0.24
        self.assertLessEqual(_s_curve(0.27, low=1.0), 0.24)
        # the day: black concrete (~0.16 before the curve) ~0.15, darks barely moved, the lawn's grain at most +10%
        self.assertTrue(0.145 <= _s_curve(0.16) <= 0.16, _s_curve(0.16))
        self.assertGreaterEqual(_s_curve(0.30), 0.285)
        self.assertLessEqual(1.0 + _const("S_CURVE_SLOPE"), 1.10)
        # monotonic, never below zero, and the lawn's mean holds (its grain moves about as much up as down)
        for low in (0.0, 1.0):
            xs = [i / 200.0 for i in range(1, 241)]
            ys = [_s_curve(x, low=low) for x in xs]
            self.assertTrue(all(b > a for a, b in zip(ys, ys[1:])))
            self.assertGreater(ys[0], 0.0)
            self.assertAlmostEqual((_s_curve(0.37, low=low) + _s_curve(0.47, low=low)) / 2.0, 0.42, delta=0.005)
        # at night (day = 0) the curve is the identity below the shoulder
        self.assertAlmostEqual(_s_curve(0.15, day=0.0), 0.15)
        final = _read("final.fsh")
        self.assertIn("float day = smoothstep(S_CURVE_DAY_LO, S_CURVE_DAY_HI, sunH);", final)
        self.assertIn("float slope = mix(S_CURVE_SLOPE, S_CURVE_SLOPE_LOW, low);", final)

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
        # Retired pin (light pass 6): "the zenith >= 0.08 luma over the horizon" - every frame's sky read darker toward
        # the horizon, the inverse of a real sky (critic); now the horizon is >= 0.08 over the zenith, still bluer up
        zen, hor = vec("SKY_ZENITH_DAY"), vec("SKY_HORIZON_DAY")
        # Retired again (light pass 8): ">= 0.08 over the zenith" before the grade - pass 7's 0.80 beige horizon read as
        # a hard two-band sky. The target is one soft blue-grey ramp: a neutral horizon <= 0.78, a little over the zenith
        hl, zl = sum(a * b for a, b in zip(hor, luma)), sum(a * b for a, b in zip(zen, luma))
        self.assertTrue(0.04 <= hl - zl <= 0.12 and hl <= 0.78, (hl, zl))
        self.assertLessEqual(colorsys.rgb_to_hsv(*hor)[1], 0.15)              # neutral blue-grey, not beige
        self.assertGreater(hor[2], hor[0])
        self.assertGreater(zen[2] / zen[0], hor[2] / hor[0])   # bluer at the zenith
        gold, deep = vec("SKY_SUNSET_GOLD"), vec("SKY_SUNSET_DEEP")
        hg, sg, _ = colorsys.rgb_to_hsv(*gold)
        hd, sd, _ = colorsys.rgb_to_hsv(*deep)
        self.assertTrue(25 <= hg * 360 <= 40 and sg >= 0.3, (hg * 360, sg))
        # Retired pin (light pass 5): "gold and deep >= 15 degrees and >= 0.08 luma apart" - it made the dusk horizon a
        # dark red-orange band (0.55 luma, hue 15) that read as a flat peach wash with the haze. The critic's dusk target
        # replaces it: a horizon band ~0.80 luma at hue 20-30 under a zenith <= 0.66 at hue 210-220, >= 0.12 luma apart
        zd = vec("SKY_ZENITH_DUSK")
        # Retired again (light pass 8): "the horizon band 0.74-0.84, the zenith hue 205-220" - the dusk still (13000)
        # read mid-afternoon (sky 0.76 against the benchmark's 0.46 mauve). Now toward the set sun ~0.55-0.62 at hue
        # 18-30, a deep blue-violet zenith ~0.3-0.4 at hue 225-250, and a dim blue-grey horizon away from the sun
        self.assertTrue(18 <= hd * 360 <= 30, hd * 360)
        self.assertTrue(0.52 <= sum(a * b for a, b in zip(deep, luma)) <= 0.62)
        hz, sz, _ = colorsys.rgb_to_hsv(*zd)
        # light pass 9: a mauve zenith instead (target <= (130, 117, 125) on screen)
        # light pass 10: darker still, ~0.28 (the zenith >= 0.25 under the horizon at dusk)
        self.assertTrue(275 <= hz * 360 <= 335 and 0.05 <= sz <= 0.2, (hz * 360, sz))
        self.assertTrue(0.24 <= sum(a * b for a, b in zip(zd, luma)) <= 0.34)
        self.assertTrue(0.2 <= _const("SKY_DUSK_SPAN") <= 0.5)
        hd_away = vec("SKY_HORIZON_DUSK")
        self.assertLess(sum(a * b for a, b in zip(hd_away, luma)), 0.5)
        self.assertGreaterEqual(sum(a * b for a, b in zip(deep, luma)) - sum(a * b for a, b in zip(zd, luma)), 0.12)
        clouds = _read("program", "clouds.fsh")
        self.assertIn("skyGradient(dir) * CLOUD_SKY", clouds)
        # light pass 6: lit from the sun's side, faded before the layer's rim (its far edge drew a dotted ellipse)
        self.assertIn("1.0 - smoothstep(CLOUD_RIM_START, CLOUD_RIM_END", clouds)
        self.assertLess(_const("CLOUD_RIM_END"), 1.0)
        self.assertIn("c = mix(c, skyWithLights(dir), skyOwn());", _read("program", "sky.fsh"))
        # light pass 6: composite paints the sky over every undrawn pixel of a perspective view (the clear colour showed
        # where the sky programs' dome does not reach), so the look never depends on which sky programs run
        self.assertIn("c = mix(c, toDisplay(toneMap(skyWithLights(normalize(viewPosAt(uv, 1.0))))), skyOwn());",
                      _read("composite.fsh"))
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
        # Retired pin (light pass 13): "no FOLIAGE_MAX_LUMA" - with the texture side's brighter tips the pass-11 stills
        # had 673 neon tuft pixels at 1.8x the lit lawn. The cap is back as a soft one, at ~1.15x the lawn's albedo
        # (~0.355), never under it: a tuft is never dimmed below the lawn (pass 2's grey smudges)
        self.assertTrue(0.38 <= _const("FOLIAGE_MAX_LUMA") <= 0.45)
        self.assertGreaterEqual(_const("FOLIAGE_LUMA_KNEE") * _const("FOLIAGE_MAX_LUMA"), 0.32)
        self.assertTrue(0.80 <= _const("FOLIAGE_BASE_AO") <= 0.95)
        lit = _read("program", "lit.fsh")
        self.assertIn("ao *= mix(FOLIAGE_BASE_AO, 1.0, plantUp);", lit)
        self.assertIn("mat.emission = 0.0;", lit)
        self.assertIn("reflected *= 1.0 - foliage;", lit)
        # light pass 16: the plants' luma caps read the light an albedo carries (a saturated leaf escaped a display-
        # space cap at 0.79-0.89 on screen), and an untinted plant's green texels keep under PLANT_MAX_SAT
        self.assertIn("return pow(max(dot(toLinear(albedo), LUMA), 0.0), 1.0 / 2.2);", lit)
        self.assertIn("float hl = plantLuma(held);", lit)
        self.assertIn("float pl = plantLuma(albedo);", lit)
        self.assertTrue(0.45 <= _const("PLANT_MAX_SAT") <= 0.6)
        # light pass 17: any other cutout sprite (no plant id, no biome tint) by its mip-4 alpha gets the same caps on
        # its green texels (lp16's neon sprites at green 236-252); solid blocks keep theirs
        self.assertIn("float cover = texture2D(texture, texcoord, lodBias + 4.0).a;", lit)
        self.assertTrue(0.8 <= _const("PLANT_CUTOUT_COVER") <= 0.95)
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
        # Retired pin (light pass 15): ">= 2.4" - lp14o's t11800 measured the lit lawn 0.43 over shadows ~0.20 (> 5:1 in
        # light: the model's shadow over-reads, as at 5000), Complementary 0.35; the sun's part comes down (SUN_LOW x2.94,
        # LOW_SUN_RAKE 0). The model's ratio stays >= 2.0
        self.assertGreaterEqual((sun * facing + sky) / sky, 2.0)
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
        # Retired pin (light pass 7): "blue 1.2-1.5x green" and "SHADE_TINT_WALL a 0.5-0.7 share" - the ground's
        # shadow at 5000 stayed a dimmer lawn (blue 0.51 of green, target >= 0.65) while walls need far less. The
        # ground's tint and the walls' are separate colours now: the ground's blue 1.8-2.3x green, the walls' 1.1-1.3x
        # Retired pin (light pass 15): "blue 1.8-2.3x green" - lp14o's 5000 shadow (44, 62, 26) against a target ~(55, 78,
        # 50): the lawn has little blue to tint. 2.5-3.0 now
        self.assertTrue(2.5 <= tint[2] / tint[1] <= 3.0, tint)
        wall = [float(x) for x in re.search(r"const vec3 SHADE_TINT_WALL = vec3\(([^)]*)\);", settings).group(1).split(",")]
        self.assertTrue(1.1 <= wall[2] / wall[1] <= 1.3, wall)
        self.assertAlmostEqual(sum(a * b for a, b in zip(wall, (0.2126, 0.7152, 0.0722))), 1.0, delta=0.01)
        # Retired pin (light pass 9): "SHADE_TINT_LOW_BOOST 0.3-1.0" - the low sun's shadows read ink-black and cool
        # against the benchmark's hazy gold; no boost now (and a warmer, larger SKY_LOW)
        self.assertEqual(_const("SHADE_TINT_LOW_BOOST"), 0.0)
        # light pass 16: under a high sun the ground keeps only a little of SHADE_TINT (lp15's 5000 shadows on the lawn
        # read teal, hue 114-125, blue 61-67; target hue 85-95, blue <= 58), faded in above t11800's sine (0.232), so
        # the low sun's tint is untouched
        # Retired pin (light pass 17): "SHADE_TINT_GROUND_HIGH 0-0.2" - at 0.10 lp16's shadows took exactly the lit
        # grass's hue and saturation (76, 0.58; Complementary's shadow sat 0.46 against 0.57). Cooler and less
        # saturated than lit, never teal: 0.25-0.55
        self.assertTrue(0.25 <= _const("SHADE_TINT_GROUND_HIGH") <= 0.55)
        self.assertGreater(_const("SHADE_TINT_HIGH_LO"), math.sin(math.radians(13.4)))
        self.assertLess(_const("SHADE_TINT_HIGH_HI"), 0.84)
        self.assertIn("mix(vec3(1.0), SHADE_TINT, SHADE_TINT_GROUND_HIGH), highSunTint());", _read("lib", "light.glsl"))
        for t in (tint, wall):
            self.assertGreater(t[2], t[1])
            self.assertGreater(t[1], t[0])                    # blue-cyan, never violet (red above green)
        light = _read("lib", "light.glsl")
        self.assertIn("light += skyPart * (shadeTint(shadow * min(facing * 4.0, 1.0), upness) - 1.0);", light)
        # light pass 5: the fill has a direction (the castle's walls at noon 0.68 / 0.67): a face toward the sun's
        # azimuth against one turned away >= 1.5x of fill, tops untouched
        d = _const("FILL_DIRECTION")
        # light pass 9: 0.30 -> 0.20 (with a stronger ground bounce: the shaded sides read black), still >= 1.45x
        # Retired pin (light pass 12): ">= 1.45x" - pass 11's 0.70 lean took both of quartz's sides into the shoulder;
        # the sides now part by plain N.L, the walls' fill at WALL_FILL; the lean only a hint (1.1-1.5x)
        self.assertTrue(1.1 <= (1.0 + d) / (1.0 - d) <= 1.5)
        self.assertTrue(0.3 <= _const("WALL_FILL") <= 0.6)
        # Retired pin (light pass 11): "FILL_DIRECTION <= 0.4" - with the sun on one visible face the side split stayed
        # 0.66-0.72 against 0.56-0.59 on quartz (target 0.80 / 0.52); the fill's direction carries most of it now
        self.assertLessEqual(d, 0.8)
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

    def test_the_sky_and_its_strays_light_pass_7(self):
        # the SKY_PROBE run (eyes 41078134357): gbuffers_skybasic runs under Oculus, gbuffers_clouds never does - the
        # clouds come through gbuffers_textured / gbuffers_basic. The sky is drawn by skybasic, which marks its pixels
        # (colortex1 green) so composite's paint is a fallback only; clouds are caught by world height in the unlit
        # programs (lit by the sky's ramp, softened, faded at the rim) and a stray sun sprite dropped by direction
        sky = _read("program", "sky.fsh")
        self.assertIn("/* DRAWBUFFERS:01 */", sky)
        self.assertIn("gl_FragData[1] = vec4(0.0, 1.0, 0.0, 1.0);", sky)
        self.assertIn("if (texture2D(colortex1, uv).g < 0.5)", _read("composite.fsh"))
        for name in ("gbuffers_textured", "gbuffers_basic"):
            for ext in ("vsh", "fsh"):
                self.assertIn("#define UNLIT_SKYCATCH", _read("%s.%s" % (name, ext)))
        unlit = _read("program", "unlit.fsh")
        # light pass 8: caught clouds are dropped outright (fading left their alpha-tested texels: the dotted ring), and
        # so is anything past SKYCATCH_FAR of the render distance; vanilla's sun and moon sprites by direction
        self.assertIn("if (y > CLOUD_Y_MIN && y < CLOUD_Y_MAX && length(viewPos) > 24.0)\n            discard;", unlit)
        self.assertTrue(_const("CLOUD_Y_MIN") < 192.0 < _const("CLOUD_Y_MAX"))
        self.assertIn("abs(dot(dir, sunDir)) > SUN_SPRITE_COS", unlit)
        self.assertTrue(0.7 <= _const("SKYCATCH_FAR") <= 0.95)
        # Retired pin (light pass 8): "SKY_GRADIENT_SPAN <= 0.2" - the 0.10 span read as a hard two-band sky; one
        # soft square-root ramp over 0.6 now
        self.assertIn("sqrt(clamp(u / span, 0.0, 1.0))", _read("lib", "sky.glsl"))

    def test_plants_cast_soft_shadows_and_perspective_keeps_contrast(self):
        # light pass 7: plants are in the shadow pass again, swaying as lit.vsh sways them, and look their own shadow up
        # a block toward the light; perspective views keep most of the low sun's curve; the ground's bounce takes only
        # part of a wall foot's occlusion
        shadow_vsh = _read("shadow.vsh")
        self.assertNotIn("vec4(2.0, 2.0, 2.0, 1.0)", shadow_vsh)
        self.assertIn("float plant = lower ? top : (upper ? 1.0 + 0.7 * top : 0.0);", shadow_vsh)
        self.assertIn("(FOLIAGE_SHADOW_LIFT * foliage)", _read("program", "lit.fsh"))
        self.assertTrue(0.5 <= _const("FOLIAGE_SHADOW_LIFT") <= 1.5)
        self.assertIn("float curveLow = perspectiveGeometry ? max(low, PERSPECTIVE_CURVE) : low;", _read("final.fsh"))
        self.assertTrue(0.5 <= _const("PERSPECTIVE_CURVE") <= 1.0)
        self.assertIn("mix(1.0, ao * texAo, BOUNCE_AO)", _read("lib", "light.glsl"))
        self.assertLess(_const("BOUNCE_AO"), 1.0)
        # light pass 8: plants' shadows thinned to their solid texels on every other map texel (~half a shadow); the
        # ground's shade tint by material (block.properties 10005), not by the normal; the moon fades in after
        # sunset; sky light from SKY_LIGHT_FULL counts as open sky (the chunk-border light step)
        # light pass 9: pass 8's every-other-texel thinning removed the tufts' shadows; solid texels at full strength
        # from a smaller caster now (shadow.vsh: PLANT_SHADOW_SHRINK across, PLANT_SHADOW_HEIGHT up)
        self.assertIn("if (plantCaster > 0.5 && a < PLANT_SHADOW_ALPHA)", _read("shadow.fsh"))
        self.assertNotIn("mod(floor(gl_FragCoord.x)", _read("shadow.fsh"))
        self.assertTrue(0.4 <= _const("PLANT_SHADOW_SHRINK") <= 0.8)
        self.assertTrue(0.5 <= _const("PLANT_SHADOW_HEIGHT") <= 0.9)
        props = open(os.path.join(spc.SHADERS, "block.properties"), encoding="utf-8").read()
        self.assertRegex(props, r"(?m)^block\.10005=grass_block dirt ")
        self.assertIn("#define ID_GROUND 10005.0", _read("lib", "settings.glsl"))
        light = _read("lib", "light.glsl")
        self.assertIn("clamp(upness, 0.0, 1.0) * gShadeGround", light)
        self.assertIn("gShadeGround = groundMaterial;", _read("program", "lit.fsh"))
        self.assertIn("sunColour *= smoothstep(MOON_FADE_LO, MOON_FADE_HI, -h);", light)
        self.assertTrue(0.7 <= _const("SKY_LIGHT_FULL") <= 0.9)
        # light pass 10: plants' shadow texels read at mip 0 (their thin blades averaged under the alpha cut at the shadow
        # map's scale); the contact crease leaves plants out (colortex1 blue); the ground's tint cut under a low sun;
        # the sun's direct light full again by t11800's height (~0.23)
        self.assertIn("texture2D(texture, texcoord, plantCaster > 0.5 ? -8.0 : 0.0).a", _read("shadow.fsh"))
        self.assertIn("gl_FragData[1] = vec4(skyShare, 0.0, foliage, alpha);", _read("program", "lit.fsh"))
        self.assertIn("texture2D(colortex1, suv).b > 0.5", _read("composite.fsh"))
        self.assertTrue(0.6 <= _const("SHADE_TINT_LOW_CUT") <= 1.0)
        self.assertLessEqual(_const("SUN_FADE_HI"), math.sin(math.radians(13.0)))
        # light pass 11: clouds dim the sun's colour, not the shadow term the shade tint reads; SHADOW_PROBE (off)
        lit = _read("program", "lit.fsh")
        self.assertIn("sunColour *= cloud;", lit)
        self.assertNotIn("shadow *= cloudShade(", lit)
        self.assertRegex(_read("lib", "settings.glsl"), r"(?m)^#define SHADOW_PROBE 0 // \[0 1\]")
        # light pass 14: the plant red keeps green 0 (the probe's test); blue marks the casters turned to a card
        self.assertIn("plantCaster > 0.5 ? vec4(1.0, 0.0, plantTurned, 1.0) : vec4(1.0)", _read("shadow.fsh"))
        self.assertGreaterEqual(_const("CLOUD_DARKEST", _read("lib", "clouds.glsl")), 0.8)
        # light pass 12: plant casters turned square to the light (the probe: each tuft cast a thin X), walls lit by
        # plain N.L
        # Retired (light pass 14): "turned = across * (end * length(o) * ...)" - the end read from the position against
        # the block's centre; grass's random XZ offset and other models can misread it (the pass-12 close still's tuft
        # shadows stayed thin crosses). The end is the sprite's u against its centre's now, the half-width fixed
        vsh = _read("shadow.vsh")
        self.assertIn("float end = gl_MultiTexCoord0.x < mc_midTexCoord.x ? -1.0 : 1.0;", vsh)
        self.assertIn("vec2 turned = across * (end * 0.64 * PLANT_SHADOW_SHRINK);", vsh)
        # light pass 14: the stamp of what the pack saw (the dusk still was drawn with the sun up) stays 8 x 2 px
        self.assertIn("gl_FragCoord.x < 8.0 && gl_FragCoord.y < 2.0", _read("final.fsh"))
        self.assertIn("mix(ndl, sqrt(ndl), 0.5 * clamp(dot(n, normalize(upPosition)), 0.0, 1.0))", _read("lib", "light.glsl"))
        # light pass 13: walls take SIDE_SUN of a high sun (quartz's lit side fused with its top in the shoulder), all of
        # it again for the low sun; the dusk's sun fades from ~8 degrees, still full by t11800's height (~0.232); the
        # RTS view's mesh holes are filled from their sides (composite.fsh seamSource), never the map's edge
        light = _read("lib", "light.glsl")
        # Retired pin (light pass 20): "sideKey(n)" and its "mix(mix(SIDE_SUN, 1.0, up), ...)" - lp19's white south faces
        # 0.74-0.79 against tops 0.95 (Complementary's lit sides nearly their tops): a wall the sun meets squarely takes
        # SIDE_SUN_SQUARE, a grazed one (the east faces) keeps SIDE_SUN; still all of it for a low sun
        self.assertIn("(facing * sideKey(n, facing) * mix(1.0, texAo, 0.5))", light)
        self.assertIn("return mix(mix(side, 1.0, up), 1.0, lowSunFull());", light)
        self.assertTrue(1.0 <= _const("SIDE_SUN_SQUARE") <= 1.6)
        self.assertLess(_const("SIDE_SUN_FULL_LO"), _const("SIDE_SUN_FULL_HI"))
        self.assertGreater(_const("SIDE_SUN_FULL_LO"), 0.26)          # the 5000 east faces stay grazed
        # Retired pin (light pass 16): "SIDE_SUN 0.35-0.75" - lp15 measured the white cubes' sun-lit sides 0.63-0.67
        # (target 0.74-0.78; lp14o 0.71-0.75, Complementary 0.76) with the sun at 0.38 of the light: a lit wall now takes
        # up to 1.3x the Lambert sun of a high sun (tops still brighter: N.L 0.48 x 1.2 against 0.88), never at t11800
        self.assertTrue(0.75 <= _const("SIDE_SUN") <= 1.3)
        self.assertLess(_const("SUN_FADE_LO"), _const("SUN_FADE_HI"))
        self.assertLessEqual(_const("SKY_TWILIGHT_HI"), math.sin(math.radians(13.4)))
        comp = _read("composite.fsh")
        self.assertIn("if (depth >= 1.0 && orthoView()) {", comp)
        self.assertLessEqual(_const("SEAM_SPAN_PX", comp), 48.0)
        # light pass 20: lp19's two missing cube east faces (depth 1, the fog colour at the foot) were filled with the
        # cube's top: a top-facing source costs SEAM_TOP_COST_PX, so the side below is copied; the probes ship off
        self.assertIn("SEAM_TOP_COST_PX * topP", comp)
        settings = _read("lib", "settings.glsl")
        self.assertIn("#define SEAM_PROBE 0", settings)
        self.assertIn("#define ALBEDO_PROBE 0", settings)

    def test_the_meadow_varies_across_the_field(self):
        # light pass 3 (round 41: the lawn read as uniform felt): a world-space low-frequency field on biome-tinted
        # terrain. Patches ~8-16 blocks (the noise cell), a value swing that adds ~0.04 of macro std on screen (numpy
        # model in the constant's comment: lawn std ~0.03 -> ~0.05, the 0.045-0.06 target), the mean held by the
        # log-normal normalisation; ~+-6 degrees of hue at the extremes
        # Retired pins (light pass 4): MEADOW_CELL 8-16 and MEADOW_VALUE 0.30-0.45 - the patches were imperceptible from
        # the RTS height (critic 7.0/10). Larger, softer cells and a value swing of ~0.06 luma std on screen
        self.assertTrue(14.0 <= _const("MEADOW_CELL") <= 22.0)
        # light pass 8: 0.55 -> 0.70 (the lawn to read "full" without more exposure)
        # Retired (light pass 9): "0.60-0.80" - 0.70 read as broad bands along the block axes; blobs at <= 0.03 now,
        # on a lattice turned off the axes
        self.assertTrue(0.15 <= _const("MEADOW_VALUE") <= 0.30)
        self.assertIn("TURN_A * xz / MEADOW_CELL", _read("lib", "meadow.glsl"))
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
        self.assertIn("float neutral = max(WHITE_WALL_NEUTRAL * side, WHITE_LOW_NEUTRAL * lowSun());", lit)
        self.assertTrue(0.4 <= _const("WHITE_LOW_NEUTRAL") <= 0.7)
        # light pass 16: a white albedo's own cast goes on its shaded sides under a high sun (quartz's shade b-r +6..+23
        # by texture; target +15..+25 on every block)
        self.assertTrue(0.5 <= _const("WHITE_SHADE_NEUTRAL") <= 1.0)
        self.assertIn("lin = mix(lin, vec3(dot(lin, LUMA)), WHITE_SHADE_NEUTRAL * whiteness * shadeSide);", lit)
        # light pass 17: and the light there goes to one cool (lp16: b-r +6..+9 on 4 of 5 white sides - quartz's albedo
        # is already neutral, the cast was the light's), luma 1, blue over green over red
        settings = _read("lib", "settings.glsl")
        cool = [float(x) for x in re.search(r"const vec3 WHITE_SHADE_COOL = vec3\(([^)]*)\);", settings).group(1).split(",")]
        self.assertAlmostEqual(sum(a * b for a, b in zip(cool, (0.2126, 0.7152, 0.0722))), 1.0, delta=0.01)
        self.assertTrue(cool[2] > cool[1] > cool[0], cool)
        self.assertIn("light = mix(light, WHITE_SHADE_COOL * dot(light, LUMA), WHITE_SHADE_NEUTRAL * whiteness * shadeSide);", lit)
        # light pass 18: the shade side read from the sun's facing over WHITE_SHADE_SUN_* (the cubes' east face, grazed at
        # N.L ~0.26 by the 5000 sun, counted as fully sunlit under min(facing * 4, 1) and never took the cool); the probe
        # ships off
        self.assertIn("smoothstep(WHITE_SHADE_SUN_LO, WHITE_SHADE_SUN_HI, facing)", lit)
        self.assertTrue(0.26 < _const("WHITE_SHADE_SUN_HI") <= 0.48)
        self.assertLess(_const("WHITE_SHADE_SUN_LO"), 0.26)
        self.assertIn("#define SHADE_PROBE_WHITE 0", settings)
        # light pass 18: the broad AO stops at AO_SHADOW_FLOOR where no sun arrives (lp17's cube shadows on the pad 0.55-
        # 0.62 of lit against 0.78 on open lawn); the contact crease stays
        self.assertTrue(0.88 <= _const("AO_SHADOW_FLOOR") <= 0.97)
        self.assertGreater(_const("AO_SHADOW_FLOOR"), _const("CONTACT_AO_FLOOR"))
        self.assertIn("occ = mix(occ, max(occ, AO_SHADOW_FLOOR), smoothstep(0.70, 0.85, skyShare));", _read("composite.fsh"))
        # light pass 19: the pad's shadow darkness was the vertex AO (AO_SHADOW_FLOOR a no-op in lp18): on a top no sun
        # reaches it stops at VERTEX_AO_SHADOW_FLOOR; the biome-tinted ground takes LOW_SUN_GRASS under the low sun only
        self.assertIn("ao = mix(ao, max(ao, VERTEX_AO_SHADOW_FLOOR), (1.0 - sunHere) * upw);", lit)
        self.assertTrue(0.9 <= _const("VERTEX_AO_SHADOW_FLOOR") <= 1.0)
        self.assertIn("light *= mix(1.0, LOW_SUN_GRASS, lowSunFull() * tinted);", lit)
        self.assertTrue(0.7 <= _const("LOW_SUN_GRASS") <= 0.95)
        self.assertIn("mix(mix(ROUGH_GLINT_CAP, 1.0, glossy), 1.0, mat.metal)", lit)

    def test_the_sky_has_a_sun(self):
        # light pass 4: the loader does not name the sun's render stage, so vanilla's square sun is found by direction
        # in gbuffers_skytextured and dropped; our soft disc (warmer as the sun sinks) is drawn with the dome, which the
        # terrain covers. A glow round the moon
        sky = _read("program", "sky.fsh")
        self.assertIn("normalize(sunPosition))) > SUN_SPRITE_COS)\n        discard;", sky)
        self.assertIn("MOON_DISC_COLOUR * (moonDisc", _read("lib", "sky.glsl"))
        self.assertNotIn("MC_RENDER_STAGE_SUN", sky)
        self.assertIn("discColour * ((disc + halo)", _read("lib", "sky.glsl"))
        self.assertIn("MOON_GLOW * moonUp", _read("lib", "sky.glsl"))
        # the probe that shows which sky programs the loader runs (SKY_PROBE 1: magenta, green, cyan), off for play
        self.assertRegex(_read("lib", "settings.glsl"), r"(?m)^#define SKY_PROBE 0 // \[0 1\]")
        # light pass 9: PROBE2 paints each program that defines PROBE_COLOUR its own flat colour (off for play)
        self.assertRegex(_read("lib", "settings.glsl"), r"(?m)^#define PROBE2 0 // \[0 1\]")
        colours = set()
        for name in ("basic", "textured", "textured_lit", "weather", "beaconbeam", "spidereyes", "damagedblock",
                     "entities", "block", "hand", "clouds", "skytextured"):
            m = re.search(r"#define PROBE_COLOUR (vec3\([^)]*\))", _read("gbuffers_%s.fsh" % name))
            self.assertIsNotNone(m, name)
            colours.add(m.group(1))
        self.assertEqual(len(colours), 12)                     # every program its own colour
        for name, colour in (("program/sky.fsh", "vec3(1.0, 0.0, 1.0)"), ("program/sky.fsh", "vec4(0.0, 1.0, 0.0"),
                             ("program/clouds.fsh", "vec4(0.0, 1.0, 1.0")):
            self.assertIn(colour, _read(*name.split("/")))
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
