"""tools/schem_study.py - reads other builders' .nbt / .schem (Sponge v2, v3) / .litematic files and measures how they
are built (roofs, eaves, depth planes, palette), so lessons from great builds become numbers our generators can be
tested against. Written before the tool (Oct 28 2026). Run:
python3 -m unittest discover -s tools -p 'test_schem_study.py'

Fixtures are tiny synthetic files written here with gen_structures' NBT writer - no downloaded build is ever
committed (other people's work, public repo).
"""
import gzip
import os
import struct
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs  # noqa: E402
import schem_study as S  # noqa: E402

TAG_SHORT, TAG_INT, TAG_BYTES, TAG_STRING, TAG_LIST, TAG_COMPOUND, TAG_LONGS = 2, 3, 7, 8, 9, 10, 12


def _short(v):
    return struct.pack(">h", v)


def _bytes(b):
    return struct.pack(">i", len(b)) + bytes(b)


def _longs(vals):
    return struct.pack(">i", len(vals)) + b"".join(struct.pack(">q", v) for v in vals)


def _varints(vals):
    out = bytearray()
    for v in vals:
        while True:
            b = v & 0x7F
            v >>= 7
            out.append(b | (0x80 if v else 0))
            if not v:
                break
    return out


def _write(path, root_payload):
    with open(path, "wb") as f:
        f.write(gzip.compress(gs.tag(TAG_COMPOUND, "", root_payload)))


# A 3 x 2 x 2 test block (x 0..2, y 0..1, z 0..1): y 0 all stone, y 1 one oak stair at (1, 1, 0), else air.
CELLS = {(x, 0, z): "minecraft:stone" for x in range(3) for z in range(2)}
CELLS[(1, 1, 0)] = "minecraft:oak_stairs[facing=east,half=bottom]"


def _schem_v2(path, v3=False):
    pal = {"minecraft:air": 0, "minecraft:stone": 1, "minecraft:oak_stairs[facing=east,half=bottom]": 2}
    data = []
    for y in range(2):
        for z in range(2):
            for x in range(3):
                data.append(pal.get(CELLS.get((x, y, z), "minecraft:air")))
    palette = gs.t_compound([(TAG_INT, k, gs.t_int(v)) for k, v in pal.items()])
    dims = [(TAG_SHORT, "Width", _short(3)), (TAG_SHORT, "Height", _short(2)), (TAG_SHORT, "Length", _short(2))]
    if v3:
        blocks = gs.t_compound([(TAG_COMPOUND, "Palette", palette), (TAG_BYTES, "Data", _bytes(_varints(data)))])
        inner = gs.t_compound(dims + [(TAG_INT, "Version", gs.t_int(3)), (TAG_COMPOUND, "Blocks", blocks)])
        _write(path, gs.t_compound([(TAG_COMPOUND, "Schematic", inner)]))
    else:
        _write(path, gs.t_compound(dims + [(TAG_INT, "Version", gs.t_int(2)), (TAG_COMPOUND, "Palette", palette),
                                           (TAG_BYTES, "BlockData", _bytes(_varints(data)))]))


def _litematic(path):
    pal = [("minecraft:air", {}), ("minecraft:stone", {}), ("minecraft:oak_stairs", {"facing": "east", "half": "bottom"})]
    key = {"minecraft:air": 0, "minecraft:stone": 1, "minecraft:oak_stairs[facing=east,half=bottom]": 2}
    vals = []
    for y in range(2):
        for z in range(2):
            for x in range(3):
                vals.append(key[CELLS.get((x, y, z), "minecraft:air")])
    bits = 2
    packed = [0, 0]
    for i, v in enumerate(vals):
        start = i * bits
        packed[start // 64] |= v << (start % 64)
    packed = [p - (1 << 64) if p >= 1 << 63 else p for p in packed]
    entries = []
    for name, props in pal:
        pairs = [(TAG_STRING, "Name", gs.t_string(name))]
        if props:
            pairs.append((TAG_COMPOUND, "Properties",
                          gs.t_compound([(TAG_STRING, k, gs.t_string(v)) for k, v in props.items()])))
        entries.append(gs.t_compound(pairs))
    size = gs.t_compound([(TAG_INT, "x", gs.t_int(3)), (TAG_INT, "y", gs.t_int(-2)), (TAG_INT, "z", gs.t_int(2))])
    region = gs.t_compound([(TAG_COMPOUND, "Size", size), (TAG_LIST, "BlockStatePalette", gs.t_list(TAG_COMPOUND, entries)),
                            (TAG_LONGS, "BlockStates", _longs(packed))])
    _write(path, gs.t_compound([(TAG_COMPOUND, "Regions", gs.t_compound([(TAG_COMPOUND, "main", region)]))]))


def _vanilla(path, cells):
    sx = max(q[0] for q in cells) + 1
    sy = max(q[1] for q in cells) + 1
    sz = max(q[2] for q in cells) + 1
    st = gs.Structure(sx, sy, sz)
    for (x, y, z), (n, props) in cells.items():
        st.set(x, y, z, n, **props)
    st.write(path)


EXPECT = {(x, 0, z): ("stone", {}) for x in range(3) for z in range(2)}
EXPECT[(1, 1, 0)] = ("oak_stairs", {"facing": "east", "half": "bottom"})


class Formats(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()

    def test_sponge_v2(self):
        p = os.path.join(self.dir, "a.schem")
        _schem_v2(p)
        self.assertEqual(S.load(p).blocks, EXPECT)

    def test_sponge_v3(self):
        p = os.path.join(self.dir, "b.schem")
        _schem_v2(p, v3=True)
        self.assertEqual(S.load(p).blocks, EXPECT)

    def test_litematic_negative_size(self):
        p = os.path.join(self.dir, "c.litematic")
        _litematic(p)
        self.assertEqual(S.load(p).blocks, EXPECT)

    def test_vanilla_nbt_keeps_mod_namespace(self):
        p = os.path.join(self.dir, "d.nbt")
        _vanilla(p, {(0, 0, 0): ("stone", {}), (0, 1, 0): ("mcwroofs:deepslate_steep_roof", {"facing": "west"})})
        self.assertEqual(S.load(p).blocks, {(0, 0, 0): ("stone", {}),
                                            (0, 1, 0): ("mcwroofs:deepslate_steep_roof", {"facing": "west"})})

    def test_litematic_spanning_values(self):
        """A 5-bit palette packs values across long boundaries (12 * 5 = 60, the 13th spans bits 60..64)."""
        b = S.Build({})
        vals = list(range(20))
        bits = 5
        packed = [0, 0]
        for i, v in enumerate(vals):
            start = i * bits
            w, off = divmod(start, 64)
            packed[w] |= (v << off) & ((1 << 64) - 1)
            if off + bits > 64:
                packed[w + 1] |= v >> (64 - off)
        signed = [p - (1 << 64) if p >= 1 << 63 else p for p in packed]
        self.assertEqual(S.unpack_litematic(signed, bits, len(vals)), vals)
        self.assertEqual(b.blocks, {})


def _box_with_eaves(over):
    """A 5 x 5 stone box, walls y 1..3 on the rim of x 1..5 / z 1..5 inside a 7 x 7 plot, a flat slab roof at y 4
    covering x 1-over..5+over."""
    cells = {}
    for x in range(1, 6):
        for z in range(1, 6):
            for y in (1, 2, 3):
                if x in (1, 5) or z in (1, 5):
                    cells[(x, y, z)] = ("stone_bricks", {})
    for x in range(1 - over, 6 + over):
        for z in range(1 - over, 6 + over):
            cells[(x, 4, z)] = ("deepslate_tile_slab", {"type": "bottom"})
    return S.Build(cells)


class Measures(unittest.TestCase):
    def test_eave_overhang(self):
        self.assertEqual(S.eave_overhang(_box_with_eaves(1)), {1: 24})
        self.assertEqual(S.eave_overhang(_box_with_eaves(0)), {})
        self.assertEqual(S.eave_overhang(_box_with_eaves(2)), {1: 24, 2: 32})

    def test_roof_cells_are_sky_exposed_shapes(self):
        b = _box_with_eaves(0)
        self.assertEqual(len(S.roof_cells(b)), 25)
        b.blocks[(3, 5, 3)] = ("lantern", {})          # a lantern on top hides the slab under it from the sky
        self.assertEqual(len(S.roof_cells(b)), 24)

    def test_depth_layers_flat_wall(self):
        b = S.Build({(0, y, z): ("bricks", {}) for y in range(4) for z in range(6)})
        self.assertEqual(S.depth_layers(b, "west"), {0: 24})

    def test_depth_layers_recess(self):
        cells = {(0, y, z): ("bricks", {}) for y in range(4) for z in range(6)}
        for y in (1, 2):
            del cells[(0, y, 2)]
            cells[(1, y, 2)] = ("glass_pane", {})
        b = S.Build(cells)
        self.assertEqual(S.depth_layers(b, "west"), {0: 22, 1: 2})
        self.assertEqual(S.depth_layers(b, "south"), {0: 4, 3: 2})   # from +z: x 0 at z 5, x 1 the panes 3 back

    def test_palette_shares(self):
        b = S.Build({(0, 0, 0): ("stone", {}), (1, 0, 0): ("stone", {}), (2, 0, 0): ("oak_stairs", {"facing": "east"}),
                     (3, 0, 0): ("oak_stairs", {"facing": "west"})})
        self.assertEqual(S.palette_shares(b), [("oak_stairs", 0.5), ("stone", 0.5)])

    def test_section(self):
        b = S.Build({(0, 0, 0): ("stone", {}), (1, 1, 0): ("oak_stairs", {"facing": "east"}),
                     (1, 0, 0): ("glass", {})})
        rows = S.section(b, "z", 0)
        self.assertEqual(rows, [".s", "#g"])                 # top row first; x left to right


def _legacy(path):
    """MCEdit .schematic (pre-1.13): numeric ids in Blocks, metadata in Data, index (y * Length + z) * Width + x."""
    w, h, l = 3, 2, 2
    ids, data = [0] * (w * h * l), [0] * (w * h * l)
    def at(x, y, z, i, d=0):
        k = (y * l + z) * w + x
        ids[k], data[k] = i, d
    at(0, 0, 0, 1, 0)        # stone
    at(1, 0, 0, 1, 1)        # granite
    at(2, 0, 0, 45)          # bricks
    at(0, 0, 1, 98, 1)       # mossy stone bricks
    at(1, 1, 0, 53, 2)       # oak stairs facing south, bottom
    at(2, 1, 1, 44, 8 | 5)   # stone brick slab, top half
    at(0, 1, 1, 109, 4 | 1)  # stone brick stairs facing west, upside down
    _write(path, gs.t_compound([(TAG_SHORT, "Width", _short(w)), (TAG_SHORT, "Height", _short(h)),
                                (TAG_SHORT, "Length", _short(l)), (TAG_STRING, "Materials", gs.t_string("Alpha")),
                                (TAG_BYTES, "Blocks", _bytes([i & 0xFF for i in ids])),
                                (TAG_BYTES, "Data", _bytes(data))]))


class Legacy(unittest.TestCase):
    def test_mcedit_schematic(self):
        p = os.path.join(tempfile.mkdtemp(), "e.schematic")
        _legacy(p)
        self.assertEqual(S.load(p).blocks, {
            (0, 0, 0): ("stone", {}), (1, 0, 0): ("granite", {}), (2, 0, 0): ("bricks", {}),
            (0, 0, 1): ("mossy_stone_bricks", {}),
            (1, 1, 0): ("oak_stairs", {"facing": "south", "half": "bottom"}),
            (2, 1, 1): ("stone_brick_slab", {"type": "top"}),
            (0, 1, 1): ("stone_brick_stairs", {"facing": "west", "half": "top"})})

    def test_unknown_id_is_named_not_dropped(self):
        self.assertEqual(S.legacy_block(250, 0), ("legacy_250", {}))


if __name__ == "__main__":
    unittest.main()
