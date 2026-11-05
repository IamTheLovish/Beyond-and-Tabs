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
        cls.rts = G.shoot(cls.b, "rts", step=4, declared=cls.size)
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
        """From the RTS camera the glow is 0.5-3.0 % of the building's pixels, all of it in the crater (|d| <= 1).
        Band re-set after round 1 (on-screen evidence, eyes 41268686770): the first band, 0.1-1.5 %, was set before
        any still; round 1 measured 0.4 % and both fresh critics read it as "a speck", "below census size", "one
        block", "does not read at distance". Both edges move: under 0.5 % the faction mark does not read; over 3 % the
        glow starts to outshout the team colour (BAT - Readable at any zoom, R3)."""
        sx, sy, sz = self.size
        cx, cz = (sx - 1) // 2, (sz - 1) // 2
        glow = [h for h in self.rts.values() if any(k in h[1] for k in EMISSIVE)]
        share = len(glow) / max(1, len(self.rts))
        self.assertTrue(0.005 <= share <= 0.03, share)
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

    # ---- the Fable critics' fixes (Nov 4, eyes 41792408905: 5 / 10; critic-a.md, critic-c.md F5) ----
    def test_crater_glow_is_one_patch_not_a_plus(self):
        """The crater's top glow layer is ONE 4-connected patch of at least 6 cells in the 3 x 3 that fills at least
        one corner: a vent, not a sign (critic A fix 1: the shipped plus of 5 read as "an orange X / team mark";
        critic C F5: one irregular patch so the Ashborn and the Gravebound capitols stop being dusk twins)."""
        glow = {q for q, n in self.solid.items() if n == "shroomlight"}
        top = max(q[1] for q in glow)
        layer = {(q[0], q[2]) for q in glow if q[1] == top}
        self.assertGreaterEqual(len(layer), 6, sorted(layer))
        self.assertTrue(any((7 + a, 7 + b) in layer for a in (-1, 1) for b in (-1, 1)), sorted(layer))
        seen, stack = {min(layer)}, [min(layer)]
        while stack:
            x, z = stack.pop()
            for v in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
                if v in layer and v not in seen:
                    seen.add(v)
                    stack.append(v)
        self.assertEqual(seen, layer)

    def test_bronze_on_the_crown_gate_and_rails(self):
        """The Ashborn kit's accent (tools/ashborn_lab_parts.py: bronze = waxed cut copper, worn = waxed exposed cut
        copper) on the crown, the gate's pylons and the stair rails, 1-10 % of the RTS pixels (an accent that reads,
        not a main block): the capitol is "the grey one with bronze", not the Gravebound capitol's grey twin (critic
        A fix 2). No oxidized or weathered copper: that teal is the Stormcallers' jade (critic C)."""
        def bronze(n):
            return n.startswith(("waxed_cut_copper", "waxed_exposed_cut_copper"))
        parts = {"crown": [q for q, n in self.solid.items() if bronze(n) and q[1] >= 13],
                 "gate": [q for q, n in self.solid.items() if bronze(n) and q[0] == 0],
                 "rails": [q for q, n in self.solid.items() if bronze(n) and 4 <= q[1] <= 12 and q[0] < 7
                           and abs(q[2] - 7) == 2]}
        for part, cells in parts.items():
            self.assertTrue(cells, "no bronze on the %s" % part)
        share = sum(1 for h in self.rts.values() if bronze(h[1])) / max(1, len(self.rts))
        self.assertTrue(0.01 <= share <= 0.10, share)
        teal = sorted({n for n in self.solid.values() if "oxidized" in n or "weathered" in n})
        self.assertEqual(teal, [])

    def test_gate_mouth_open_to_the_rts_camera(self):
        """lovish, Nov 4 ("Ashborn: open gate mouth"): the mouth's outer two cells (x 0..1, z 6..8) are open to the
        sky, so the RTS camera looks down into the dark mouth - at least 0.5 % of the building's RTS pixels land in it
        (x <= 2, z 6..8, y <= 3). Shipped: the lintel and the T2 batter roofed it, "invisible from the RTS camera"
        (critic A)."""
        roofed = sorted(q for q in self.solid if q[0] in (0, 1) and 6 <= q[2] <= 8 and q[1] >= 1)
        self.assertEqual(roofed, [])
        seen = sum(1 for h in self.rts.values() if h[0][0] <= 2 and 6 <= h[0][2] <= 8 and h[0][1] <= 3)
        self.assertGreaterEqual(seen / max(1, len(self.rts)), 0.005, seen)

    def test_basalt_column_foot(self):
        """The plinth's piers on the two camera faces (west x 0, south z 14; y 1..3) are the kit's basalt columns (axis
        y) with a polished basalt joint at y 2 - at least 12 cells (critic A fix 5: the Ashborn lab's ground storey is a
        basalt column wall; the capitol's foot was blackstone brick, the Gravebound's block list)."""
        cols = [q for q in self.solid if (q[0] == 0 or q[2] == 14) and 1 <= q[1] <= 3
                and self.solid[q] == ("polished_basalt" if q[1] == 2 else "basalt")]
        self.assertGreaterEqual(len(cols), 12, len(cols))

    def test_occupies_a_capitol_share_of_the_rts_view(self):
        """The capitol is the centrepiece: >= 12 % of the RTS still (the other capitols 15-18 %; RoN's portal 7 %)."""
        share = len(self.rts) / ((G.SCREEN[0] // 4) * (G.SCREEN[1] // 4))
        self.assertGreaterEqual(share, 0.12)


if __name__ == "__main__":
    unittest.main()
