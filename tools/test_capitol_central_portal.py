#!/usr/bin/env python3
"""The Ashborn capitol (central_portal.nbt) as the RTS camera sees it - tools/capitol_central_portal.py's tests.

Each test fails on the Reign of Nether portal that shipped until Nov 2026 (11 x 16 x 9, nether brick, an obsidian
portal frame; two fresh critics, eyes 41263811904: 2/10, "red nether portal on a red pad") and holds the massing two
blind critics picked (eyes 41266607714: the stepped volcano-temple). Camera tests cast the eyes stills' real rays
(tools/building_gates.shoot, FOV 77) into the shipped structure; colours by how blocks LOOK on camera
(design/vehicle_labs/camera_colours.json), not by id.
"""
import os
import re
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import building_gates as G  # noqa: E402
import camera_colour as CC  # noqa: E402
import schem_study as S  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "bar", "src", "main", "resources")
NBT = os.path.join(RES, "data", "reignofnether", "structures", "central_portal.nbt")
NBT_ASSETS = os.path.join(RES, "assets", "reignofnether", "structures", "central_portal.nbt")
JAVA = os.path.join(ROOT, "bar", "src", "main", "java", "com", "solegendary", "reignofnether", "building",
                    "buildings", "piglins", "CentralPortal.java")

# lovish, Oct 19 2026: Ashborn "must NOT resemble Reign of Nether's Piglins: no pigs, gold, gilded blackstone,
# nether brick, portals" (design/building-revamp-progress.md). Ids that draw those looks:
BANNED = ("nether_brick", "netherrack", "nether_portal", "gilded_blackstone", "gold", "crimson", "warped",
          "nether_wart", "nylium")
EMISSIVE = ("shroomlight", "campfire", "magma", "lava", "froglight", "glowstone", "lantern", "torch", "fire",
            "sea_lantern", "end_rod")
FULL_GLYPHS = "#"     # schem_study: a full cube; stairs, slabs, walls, posts are sub-blocks


def _load():
    return S.load(NBT)


def _size():
    """The box from the structure's header (the game's box: RoN's structures carry their air)."""
    import preview_structure as PS
    return tuple(int(v) for v in PS.load_nbt(NBT)["size"])


def _solid(b):
    return {q: n for q, (n, _) in b.blocks.items() if n not in S.AIR}


class CentralPortalTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.b = _load()
        cls.solid = _solid(cls.b)
        cls.size = _size()
        cls.rts = G.shoot(cls.b, "rts", step=4)
        cls.table = CC.load()

    def test_capitol_size_like_the_other_six(self):
        """15 x 22 x 15, the box of every other capitol (BiomeBaseGameTests' capitol rows); RoN's was 11 x 16 x 9."""
        self.assertEqual(self.size, (15, 22, 15))

    def test_both_structure_trees_are_the_same_building(self):
        with open(NBT, "rb") as a, open(NBT_ASSETS, "rb") as c:
            self.assertEqual(a.read(), c.read())

    def test_nothing_of_the_nether_portal_kit(self):
        bad = sorted({n for n in self.solid.values() if any(k in n for k in BANNED)})
        self.assertEqual(bad, [], "banned for the Ashborn by lovish (Oct 19)")

    def test_fire_cell_is_never_air(self):
        """CentralPortal.tick sets FIRE at centrePos + (-1, 0, 0) or (0, -1, 0) (by rotation) when the cell is air;
        centrePos is the box's middle (y rounds either way with the sign of world y). No stray fire."""
        sx, sy, sz = self.size
        cx, cz = (sx - 1) // 2, (sz - 1) // 2
        cys = {(sy - 1) // 2, sy // 2}
        cells = set()
        for cy in cys:
            for dx in (-1, 1):
                cells.add((cx + dx, cy, cz))
                cells.add((cx, cy, cz + dx))
            cells.add((cx, cy - 1, cz))
        air = sorted(q for q in cells if q not in self.solid)
        self.assertEqual(air, [])

    def test_foundation_holds_the_java_starting_type(self):
        """CentralPortal.startingBlockTypes must stand in the foundation layer, or a fresh site counts as destroyed."""
        with open(JAVA) as f:
            src = f.read()
        types = [t.lower() for t in re.findall(r"startingBlockTypes\.add\(Blocks\.([A-Z_]+)\)", src)]
        self.assertTrue(types)
        y0 = {n for (x, y, z), n in self.solid.items() if y == 0}
        for t in types:
            self.assertIn(t, y0)

    def test_crown_is_the_single_landmark(self):
        """The top stands on the centre (|dx|, |dz| <= 2) and rises >= 5 above anything off the crown."""
        sx, sy, sz = self.size
        cx, cz = (sx - 1) / 2, (sz - 1) / 2
        crown = [q for q in self.solid if max(abs(q[0] - cx), abs(q[2] - cz)) <= 2]
        other = [q for q in self.solid if max(abs(q[0] - cx), abs(q[2] - cz)) > 3]
        top = max(q[1] for q in self.solid)
        self.assertTrue(crown and max(q[1] for q in crown) == top)
        self.assertGreaterEqual(top - max(q[1] for q in other), 5)

    def test_one_glow_focus_in_the_crater(self):
        """From the RTS camera the glow is 0.1-1.5 % of the building's pixels, all of it on the crown (|d| <= 1)."""
        sx, sy, sz = self.size
        cx, cz = (sx - 1) // 2, (sz - 1) // 2
        glow = [h for h in self.rts.values() if any(k in h[1] for k in EMISSIVE)]
        share = len(glow) / max(1, len(self.rts))
        self.assertTrue(0.001 <= share <= 0.015, share)
        off = [h[0] for h in glow if max(abs(h[0][0] - cx), abs(h[0][2] - cz)) > 1]
        self.assertEqual(off, [])

    def test_gate_mouth_on_the_west_axis(self):
        """A mouth >= 3 wide, 3 tall, 3 deep in the west face on the axis, backed by the darkest block (obsidian)."""
        sx, sy, sz = self.size
        cz = (sz - 1) // 2
        mouth = [(x, y, z) for x in range(3) for y in (1, 2, 3) for z in (cz - 1, cz, cz + 1)]
        self.assertEqual([q for q in mouth if q in self.solid and "obsidian" not in self.solid[q]], [])
        back = [self.solid.get((3, y, cz)) for y in (1, 2, 3)]
        self.assertEqual(back, ["obsidian"] * 3)

    def test_light_rings_read_from_the_rts_camera(self):
        """Tier rims carry the lights: 10-40 % of the building's RTS pixels read value >= 0.44 on camera (both
        edges: no light rings reads as a dark lump, too many as a pale block)."""
        light = 0
        for (q, n, face, behind) in self.rts.values():
            v = CC.lookup(self.table, n, face)
            if v and v[2] >= 0.44:
                light += 1
        share = light / max(1, len(self.rts))
        self.assertTrue(0.10 <= share <= 0.40, share)

    def test_sub_blocks_on_camera_faces(self):
        """Anti-toy gate (lessons H, 6): >= 15 % of the RTS pixels land on sub-blocks (stairs, slabs, walls, posts)."""
        sub = sum(1 for h in self.rts.values() if S.glyph(h[1]) not in FULL_GLYPHS)
        self.assertGreaterEqual(sub / max(1, len(self.rts)), 0.15)

    def test_occupies_a_capitol_share_of_the_rts_view(self):
        """The capitol is the centrepiece: >= 12 % of the RTS still (the other capitols 15-18 %; RoN's portal 7 %)."""
        share = len(self.rts) / ((G.SCREEN[0] // 4) * (G.SCREEN[1] // 4))
        self.assertGreaterEqual(share, 0.12)


if __name__ == "__main__":
    unittest.main()
