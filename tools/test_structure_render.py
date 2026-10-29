#!/usr/bin/env python3
"""tools/structure_render.py's light pass on tiny scenes, without the block pack (every block takes its flat palette
colour): the sun lights the face that looks at it, a tower's shadow falls away from the sun, the foot of a wall is
occluded, light sources stay bright, and --flat keeps Minecraft's fixed face shades exactly."""
import math
import os
import sys
import unittest

try:
    import numpy as np
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import structure_render as SR
except ImportError as e:   # the renderer needs numpy and Pillow (CI's ruff job installs them)
    SR = None
    MISSING = str(e)

PX = 24


class NoPack:
    """Textures without the pack: nothing resolves, so every block draws in its flat palette colour."""

    def resolve(self, block, axis="y"):
        return None, None


def scene(*extra):
    """A 25 x 25 stone floor (y 0) with an 8-tall pillar at (5, 1..8, 12), plus extra (pos, block) cells."""
    palette = [{"Name": "minecraft:stone"}, {"Name": "minecraft:glowstone"}]
    blocks = [{"pos": [x, 0, z], "state": 0} for x in range(25) for z in range(25)]
    blocks += [{"pos": [5, y, 12], "state": 0} for y in range(1, 9)]
    blocks += [{"pos": list(p), "state": palette.index({"Name": b})} for p, b in extra]
    return {"size": [25, 9, 25], "palette": palette, "blocks": blocks}


def draw(root, light):
    img, *_ = SR.render_root(root, NoPack(), SR.Faces(NoPack()), PX, light)
    return img


def pixel(img, X, Y, Z):
    """The colour drawn at world point (X, Y, Z) (on a face the camera sees), as floats."""
    hw, qh, hh = PX / 2, PX / 4, PX * 0.6
    left, top = img.info["origin"]
    sx, sy = (X + Z) * hw - left, (Z - X) * qh - Y * hh - top
    return np.asarray(img.convert("RGB"), dtype=np.float64)[int(sy), int(sx)]


def light(**kw):
    base = dict(sun=SR.sun_vector(250, 45), ao=0.8, shadows=True, sky=False)
    base.update(kw)
    return SR.Light(**base)


@unittest.skipIf(SR is None, "numpy / Pillow missing: %s" % (None if SR else MISSING))
class LightPass(unittest.TestCase):
    def test_flat_keeps_minecraft_face_shades(self):
        img = draw(scene(), None)
        colour = np.asarray(SR.PS.colour_for("minecraft:stone"), dtype=np.float64)
        top = pixel(img, 12.5, 1, 18.5)
        np.testing.assert_allclose(top, colour, atol=1)
        south = pixel(img, 5.5, 4.5, 13)              # the pillar's south face
        west = pixel(img, 5, 4.5, 12.5)               # and its west face
        np.testing.assert_allclose(south, np.floor(colour / 255 * 0.8 * 255), atol=1)
        np.testing.assert_allclose(west, np.floor(colour / 255 * 0.6 * 255), atol=1)

    def test_sun_lights_the_face_toward_it(self):
        # sun from the west-south-west: the pillar's west face catches more of it than its south face
        img = draw(scene(), light(ao=0, shadows=False))
        self.assertGreater(pixel(img, 5, 4.5, 12.5).sum(), 1.2 * pixel(img, 5.5, 4.5, 13).sum())

    def test_shadow_falls_away_from_the_sun(self):
        on, off = draw(scene(), light(ao=0)), draw(scene(), light(ao=0, shadows=False))
        # 6 blocks along the shadow line from the pillar's foot (east-north-east, away from the sun)
        sun = np.asarray(SR.sun_vector(250, 45))
        p =np.array([5.5, 1, 12.5]) + np.array([-sun[0], 0, -sun[2]]) * 6 / np.hypot(sun[0], sun[2])
        self.assertLess(pixel(on, *p).sum(), 0.8 * pixel(off, *p).sum(), "no shadow behind the pillar at %s" % p)
        # the floor on the sun's side of the pillar keeps its light
        np.testing.assert_allclose(pixel(on, 2.5, 1, 15.5), pixel(off, 2.5, 1, 15.5), atol=1)

    def test_foot_of_a_wall_is_occluded(self):
        img = draw(scene(), light(shadows=False))
        foot, open_floor = pixel(img, 4.9, 1, 12.5), pixel(img, 12.5, 1, 18.5)
        self.assertLess(foot.sum(), 0.85 * open_floor.sum())

    def test_light_sources_stay_bright(self):
        root = scene(((15, 1, 5), "minecraft:glowstone"))
        lit, flat = draw(root, light(sky=True)), draw(root, None)
        for at in ((15.5, 2, 5.5), (15.5, 1.5, 6), (15, 1.5, 5.5)):   # top, south, west
            np.testing.assert_allclose(pixel(lit, *at), pixel(flat, *at), atol=1)

    def test_tilt_draws_the_rts_cameras_true_relief(self):
        # tilt=64.8 (the eyes RTS camera): a block edge is PX px, a side cos(64.8) of it tall (0.43, against the
        # preview's 0.6 of a block over a quarter-block top: relief ~3x too steep), and the light pass still finds
        # every face from the new projection
        root = scene()
        img, *_ = SR.render_root(root, NoPack(), SR.Faces(NoPack()), PX, light(ao=0, shadows=False), tilt=64.8)
        th = math.radians(64.8)
        hw, qh, hh = PX / math.sqrt(2), PX * math.sin(th) / math.sqrt(2), PX * math.cos(th)
        left, top = img.info["origin"]
        rgb = np.asarray(img.convert("RGB"), dtype=np.float64)

        def at(X, Y, Z):
            return rgb[int((Z - X) * qh - Y * hh - top), int((X + Z) * hw - left)]
        bg = np.asarray(SR.BACKGROUND, dtype=np.float64)
        self.assertGreater(np.abs(at(5.5, 8.9, 13) - bg).sum(), 30)     # the pillar's south face, near its top
        self.assertGreater(np.abs(at(12.5, 1, 18.5) - bg).sum(), 30)    # the floor
        # the canvas spans the floor's far corner (top at y 1) to its near corner (bottom at y 0): 50 rows of qh + hh
        self.assertAlmostEqual(img.height - 9, 50 * qh + hh, delta=2)

    def test_shadow_sweep_follows_the_sun(self):
        grid = np.zeros((8, 8, 8), dtype=bool)
        grid[4, 6, 4] = True
        straight = SR.sun_shadow(grid, (0.0, 1.0, 0.0))
        self.assertTrue(straight[4, :6, 4].all())
        self.assertEqual(straight.sum(), 6)
        slanted = SR.sun_shadow(grid, SR.sun_vector(270, 45))   # sun due west: shadow steps east a cell a layer
        self.assertTrue(all(slanted[4 + (6 - y), y, 4] for y in range(3, 6)))
        self.assertFalse(slanted[4, 5, 4])


if __name__ == "__main__":
    unittest.main()
