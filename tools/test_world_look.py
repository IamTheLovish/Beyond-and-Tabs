#!/usr/bin/env python3
"""The Enchanted world look on the noon lawn, with shaders off. Run: python3 -m unittest discover -s tools -p 'test_*.py'

lovish, Oct 28: Enchanted should "dim it down a bit and make it look more saturated and whimsy ... fantasy esque".
With shaders off it turned the lawn blue-green instead (r34 plain lane, eyes stills 02b look off -> 02 Enchanted, same
crop: hue 82 -> 93, V 0.39 -> 0.32). The split tone's shadow bell peaks at luma 0.25, which is where Exposure and the
S-curve park the lawn, so its violet-blue ShadowTint cast the whole terrain. Reference grass scan: hue 72, V 0.43.
lovish approved option B the same night: a warm shadow tint and Exposure 0.95.

The constants come from WorldLookPass.java and bat_postfx.fsh (tools/world_look_model.py parses them), never from a
copy here, so the test checks what the game will run.
"""
import os
import sys
import unittest

try:
    import numpy as np
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import world_look_model as WL
except ImportError as e:   # needs numpy (CI's ruff job installs it)
    WL = None
    MISSING = str(e)

# The look-off lawn: median rgb of eyes still bat_eyes_02b_rts_default_look_off.png (r34 plain lane, build 0c1e282,
# noon, 1600x900), crop (900,80)-(1400,300): hue 82.1, S 0.57, V 0.392.
LAWN_OFF = (79, 100, 43)
LAWN_OFF_V = 0.392
CROP_CENTRE = (1150, 190, 1600, 900)
# The crop sits in the top quarter of the ~15 blocks of ground the default RTS zoom shows (WorldLookPass's haze notes):
# ~10 blocks past the haze start at the screen's bottom edge, so it takes ~5% haze toward the noon sky.
LAWN_HAZE_BLOCKS = 10.0
# The same crop in still 02 (Enchanted, same build), and the two Enchanted values that build ran with: the model must
# reproduce what the game drew before it is trusted on a change.
LAWN_R34_ENCHANTED = (92.6, 0.432, 0.318)
R34_ENCHANTED = {"ShadowTint": (0.93, 0.95, 1.13), "Exposure": 0.86}


def lawn(u):
    """hue, S, V of the look-off lawn after the pass with uniforms u."""
    rgb = np.array(LAWN_OFF) / 255
    h = WL.rts_haze(u, LAWN_HAZE_BLOCKS)
    return WL.hsv(WL.grade(rgb, u, h=h, haze_color=WL.noon_sky(), r2=WL.vignette_r2(*CROP_CENTRE)))


@unittest.skipIf(WL is None, "needs numpy")
class EnchantedLawn(unittest.TestCase):
    def test_the_model_reproduces_the_r34_frame(self):
        u = dict(WL.uniforms("enchanted"), **R34_ENCHANTED)
        hue, s, v = lawn(u)
        want = LAWN_R34_ENCHANTED
        self.assertAlmostEqual(hue, want[0], delta=2.5, msg="hue %.1f, the frame %.1f" % (hue, want[0]))
        self.assertAlmostEqual(s, want[1], delta=0.03, msg="S %.3f, the frame %.3f" % (s, want[1]))
        self.assertAlmostEqual(v, want[2], delta=0.01, msg="V %.3f, the frame %.3f" % (v, want[2]))

    def test_the_lawn_reads_olive_not_blue_green(self):
        hue, s, v = lawn(WL.uniforms("enchanted"))
        self.assertLessEqual(hue, 82.0, "Enchanted lawn hue %.1f (S %.3f, V %.3f): the shadow tint casts it blue-green"
                             % (hue, s, v))

    def test_the_lawn_is_a_little_dimmer_than_with_the_look_off(self):
        hue, s, v = lawn(WL.uniforms("enchanted"))
        self.assertGreaterEqual(v, 0.36, "Enchanted lawn V %.3f (hue %.1f): too dark (look off: %.3f)"
                                % (v, hue, LAWN_OFF_V))
        self.assertLessEqual(v, 0.39, "Enchanted lawn V %.3f: no longer dimmer than the look off (%.3f)"
                             % (v, LAWN_OFF_V))


class PackStateIsNoLook(unittest.TestCase):
    """WorldLookPass.logState prints STATE_PACK as "a shader pack is on, the pass steps aside": a value shared with a
    look made the log say that while the pass ran (STATE_PACK 3 == LOOK_ENCHANTED, Oct 28)."""

    def test_state_pack_differs_from_every_look(self):
        if WL is None:
            self.skipTest(MISSING)
        pack = WL.java_constants()["STATE_PACK"]
        looks = {k: v for k, v in WL.java_constants(WL.DISPLAY_JAVA).items() if k.startswith("LOOK_")}
        self.assertIn("LOOK_ENCHANTED", looks)
        self.assertNotIn(pack, looks.values(), "STATE_PACK %d is also %s" % (
            pack, [k for k, v in looks.items() if v == pack]))


if __name__ == "__main__":
    unittest.main()
