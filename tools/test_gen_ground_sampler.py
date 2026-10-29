#!/usr/bin/env python3
"""tools/gen_ground_sampler.py, round r4 (terraces): BEFORE stays byte-identical, AFTER is deterministic and in sync with
the committed .nbt, its terraces step down only to the south and west (the RTS camera looks north-east and sees only
south and west faces), every terrace is reachable on foot from the track over stairs at least 2 wide, nothing traps a
unit in a 1-wide slot at a wall's foot, and no banned block (azalea, rooted dirt, dirt path, decaying bush leaves) is
used. Pure Python, no block pack needed."""
import gzip
import hashlib
import os
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gen_ground_sampler as G  # noqa: E402

ROOT = os.path.dirname(HERE)
# blocks a unit's body passes through (it stands on the block under them)
PASSABLE = ("air", "grass", "fern", "tall_grass", "large_fern", "poppy", "dandelion", "oxeye_daisy", "azure_bluet",
            "cornflower", "vine", "moss_carpet")
_CACHE = {}
BEFORE_MD5 = "dba1f61e89924e5ea714912ec5b38187"     # r3's ground_before.nbt (d251ce36 zipped), unzipped


def after():
    if "after" not in _CACHE:
        _CACHE["after"] = G.build(True)
    return _CACHE["after"]


def nbt_bytes(s):
    """The structure's NBT, unzipped (the gzip header holds the file's name)."""
    with tempfile.TemporaryDirectory() as d:
        p = os.path.join(d, "x.nbt")
        s.write(p)
        return committed(p)


def committed(path):
    with gzip.open(path, "rb") as fh:
        return fh.read()


def names(s):
    return {pos: s.palette[i] for pos, i in s.blocks.items()}


def surfaces(s):
    """Per column off the building: (y of the block a unit stands on, its name), or None where nothing can stand. A
    unit stands on the highest solid block (not leaves or a log) with 2 passable blocks over it; leaves or a log right
    over the ground (a bush, a trunk) leave the column None. Leaves draped higher up (over a wall's foot) let it pass."""
    blocks = names(s)
    sx, sy, sz = s.size
    out = {}
    for x in range(sx):
        for z in range(sz):
            kind = {y: blocks[(x, y, z)][0] for y in range(sy) if (x, y, z) in blocks}

            def free(y):
                return kind.get(y, "air") in PASSABLE
            stand = None
            for y in sorted(kind, reverse=True):
                n = kind[y]
                if n in PASSABLE or "leaves" in n or n.endswith(("_log", "_wood")):
                    continue
                if y + 1 in kind and not free(y + 1) or y + 2 in kind and not free(y + 2):
                    break                                        # covered (a bush, a trunk, a slab of rock)
                if not n.endswith("_slab"):
                    stand = (y, n)
                break
            if stand or kind:
                out[(x, z)] = stand
    return out


def walk(s, start):
    """Flood fill over standing places: a step between neighbours is level, or 1 block up or down when the higher one
    is a stair (units climb stairs, never jump a full block)."""
    surf = surfaces(s)
    seen, todo = {start}, [start]
    while todo:
        x, z = todo.pop()
        a = surf[(x, z)]
        for q in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
            b = surf.get(q)
            if b is None or q in seen:
                continue
            dy = b[0] - a[0]
            hi = b if dy > 0 else a
            if dy == 0 or (abs(dy) == 1 and hi[1].endswith("_stairs")):
                seen.add(q)
                todo.append(q)
    return seen, surf


class GroundSamplerTest(unittest.TestCase):
    def test_before_is_byte_identical(self):
        """Against r3's BEFORE (md5 of the unzipped NBT), not the file on disk: the generator rewrites that one."""
        s, *_ = G.build(False)
        self.assertEqual(hashlib.md5(nbt_bytes(s)).hexdigest(), BEFORE_MD5)
        self.assertEqual(hashlib.md5(committed(os.path.join(ROOT, "design", "eyes", "ground_before.nbt"))).hexdigest(),
                         BEFORE_MD5)

    def test_after_is_deterministic_and_committed(self):
        a = nbt_bytes(after()[0])
        self.assertEqual(a, nbt_bytes(G.build(True)[0]))
        self.assertEqual(a, committed(os.path.join(ROOT, "design", "eyes", "ground_after.nbt")),
                         "rerun python3 tools/gen_ground_sampler.py")

    def test_runs_without_scipy(self):
        code = ("import sys; sys.modules['scipy'] = None; sys.path.insert(0, %r); import gen_ground_sampler as G; "
                "G.build(True)" % HERE)
        r = subprocess.run([sys.executable, "-c", code], capture_output=True, text=True)
        self.assertEqual(r.returncode, 0, r.stderr[-2000:])

    def test_terrace_levels(self):
        _, _, ground, stats = after()
        lv = stats["terraces"]["levels"]
        counts = {h: sum(1 for v in ground.values() if v == h) for h in lv}
        self.assertGreaterEqual(len(lv) - 1, 3, "three terrace levels below the cliff")
        for h in lv[:-1]:
            self.assertGreaterEqual(counts[h], 150, "terrace y %d has %d cells" % (h, counts[h]))
        for lo, hi in zip(lv[:-2], lv[1:-1]):
            self.assertTrue(2 <= hi - lo <= 4, "retaining walls are 2-4 high: %s" % (lv,))
        self.assertGreaterEqual(lv[-1] - lv[-2], 5, "the cliff at the back stands taller than a terrace wall")
        self.assertTrue(set(ground.values()) <= set(lv))

    def test_walls_face_south_and_west(self):
        s, _, ground, _ = after()
        blocks = names(s)
        risers = 0
        for (x, z), h in ground.items():
            for q in ((x, z - 1), (x + 1, z)):                       # north and east: never lower
                if q in ground:
                    self.assertGreaterEqual(ground[q], h, "a riser facing north or east at %s" % ((x, z),))
            for q in ((x, z + 1), (x - 1, z)):                       # south and west: the faces the camera sees
                if q in ground and h - ground[q] >= 2:
                    for y in range(ground[q] + 1, h + 1):
                        n = blocks[(x, y, z)][0]
                        self.assertIn(n, G.WALL_STONE | G.ROCK_STONE, "riser %s y %d is %s" % ((x, z), y, n))
                        risers += 1
        self.assertGreater(risers, 300)
        caps = sum(1 for (x, z), h in ground.items() if blocks[(x, h, z)][0] in G.CAP_STONE)
        self.assertGreater(caps, 60, "a cap row on the retaining walls")

    def test_terraces_reachable_from_the_track(self):
        s, _, ground, stats = after()
        rows = stats["track"][0]
        door = stats["track"][2](0, int(rows[0][0]))
        seen, surf = walk(s, door)
        lv = stats["terraces"]["levels"]
        for h in lv[:-1]:
            cells = [p for p, v in ground.items() if v == h and surf.get(p) and surf[p][0] == h]
            got = sum(1 for p in cells if p in seen)
            self.assertGreaterEqual(got / len(cells), 0.9, "terrace y %d: %d of %d reached" % (h, got, len(cells)))

    def test_flights_at_least_two_wide(self):
        s, _, _, stats = after()
        flights = stats["terraces"]["flights"]
        self.assertGreaterEqual(len(flights), 2)
        blocks = names(s)
        for cells in flights:
            for (x, y, z) in cells:
                self.assertTrue(blocks[(x, y, z)][0].endswith("_stairs"))
                self.assertTrue((x - 1, y, z) in cells or (x + 1, y, z) in cells or
                                (x, y, z - 1) in cells or (x, y, z + 1) in cells)
            width = {}
            for (x, y, z) in cells:
                width.setdefault(y, set()).add((x, z))
            for y, row in width.items():
                self.assertGreaterEqual(len(row), 2, "flight step y %d is %d wide" % (y, len(row)))

    def test_no_one_wide_slot_at_a_wall_foot(self):
        s, _, ground, _ = after()
        surf = surfaces(s)

        def open_(a, q):
            if q not in surf:                                    # off the sampler: open ground in game
                return True
            b = surf[q]
            return b is not None and abs(b[0] - a[0]) <= 1
        slots = []
        for (x, z), h in ground.items():
            a = surf.get((x, z))
            if a is None or a[0] != h:
                continue
            foot = any(ground.get(q, -1) - h >= 2 for q in ((x, z - 1), (x + 1, z)))
            if not foot:
                continue
            ew = open_(a, (x - 1, z)) or open_(a, (x + 1, z))
            ns = open_(a, (x, z - 1)) or open_(a, (x, z + 1))
            if not (ew and ns) and (ew or ns):
                slots.append((x, z))
        self.assertLessEqual(len(slots), 3, "1-wide slots at wall feet: %s" % slots[:12])

    def test_no_banned_blocks(self):
        s, *_ = after()
        for n, props in s.palette:
            self.assertNotIn("azalea", n)
            self.assertNotIn(n, ("rooted_dirt", "dirt_path"))
            if n in G.BUSH_LEAVES:
                self.assertIn(("persistent", "true"), props)
        used = {n for n, _ in s.palette}
        self.assertTrue(used & set(G.BUSH_LEAVES), "leaf bushes over the walls")
        self.assertIn("vine", used)


if __name__ == "__main__":
    unittest.main()
