"""Placeholder structures for the Solar Collector (T1) and the Advanced Solar (T2), one shared look for all factions
(claude/solar-spec.md, Oct 21). Lane B replaces them with the per-faction looks; the sizes (3x3 and 5x5 footprints)
are the contract. Pocket-free: every inner cell is solid, so no unit can stand inside.
Called from gen_structures.py's main: write_all(out, Structure)."""


def solar_collector(Structure):
    """3 x 4 x 3: a stone pad, a copper plinth, a blue glass panel with a lightning-rod mast."""
    s = Structure(3, 4, 3)
    s.fill(0, 0, 0, 2, 0, 2, "stone_bricks")
    s.fill(0, 1, 0, 2, 1, 2, "copper_block")
    s.set(1, 1, 1, "iron_block")
    s.fill(0, 2, 0, 2, 2, 2, "light_blue_stained_glass")
    s.set(1, 2, 1, "iron_block")
    s.set(1, 3, 1, "lightning_rod", facing="up", powered="false", waterlogged="false")
    return s


def advanced_solar(Structure):
    """5 x 5 x 5: a bigger pad and plinth, a ring of copper slabs round a sunken glass field, a raised glass panel
    round a tall iron mast with a lightning rod."""
    s = Structure(5, 5, 5)
    s.fill(0, 0, 0, 4, 0, 4, "stone_bricks")
    s.fill(0, 1, 0, 4, 1, 4, "copper_block")
    for x in range(5):
        for z in range(5):
            edge = x in (0, 4) or z in (0, 4)
            if edge:
                s.set(x, 2, z, "cut_copper_slab", type="bottom", waterlogged="false")
            else:
                s.set(x, 2, z, "blue_stained_glass")
    s.fill(1, 3, 1, 3, 3, 3, "light_blue_stained_glass")
    s.set(2, 3, 2, "iron_block")
    s.set(2, 4, 2, "lightning_rod", facing="up", powered="false", waterlogged="false")
    return s


def write_all(out, Structure):
    solar_collector(Structure).write(f"{out}/solar_collector.nbt")
    advanced_solar(Structure).write(f"{out}/advanced_solar.nbt")
