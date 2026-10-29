"""The roof tiling system for the faction buildings (lovish, Oct 28 2026: "There isnt a cool roof tiling system";
22:59 decided: red brick walls, GREY SLATE roofs on halls / shed / wings, weathered GREEN COPPER on the tower and
spires). Study: design/vehicle_labs/engine_shed_detail_grammar.md (B1 course rule, refs 45 / 48 / 56 / 62 / 63 / 70).

A roof is courses of tiles from eave to ridge, in three tones of one material (T0 base 55-72 %, T1 darker/wet,
T2 paler/bleached): dark runs of 1-3 cells, never a stripe of 4+, never a chequer; wetter (darker) at the eaves,
bleached (paler) toward the ridge. Structure lines are fixed materials, not chance: the ridge cap, the verges
(bargeboards) at the gable ends, the gutters under the eaves (waxed copper - Brassworks). Eaves and verges
oversail the walls by `overhang` (1) so they throw the shadow band every reference has.

Frame: u across the span (walls at u 0..span-1), v along the ridge (0..length-1), y up from the eave course (0).
axis "z" maps u -> x, v -> z; axis "x" maps u -> z, v -> x. Vanilla slab pairs make the 26.5 degree slate pitch;
Macaw's lower / upper_lower roof shapes take their place once their ids are verified from the CI block list.
Tests: tools/test_roof_kit.py.
"""
import random

# tone families: (full, stairs, slab) per tone T0 / T1 / T2
SLATE = {
    "tones": (("deepslate_tiles", "deepslate_tile_stairs", "deepslate_tile_slab"),             # T0 slate
              ("polished_blackstone", "polished_blackstone_stairs", "polished_blackstone_slab"),  # T1 wet, darker
              ("polished_deepslate", "polished_deepslate_stairs", "polished_deepslate_slab")),    # T2 bleached
    "ridge": ("polished_blackstone_bricks",),
    "verge": ("dark_oak_planks", "dark_oak_stairs", "dark_oak_slab"),
    "gutter": "waxed_exposed_cut_copper_stairs",
}
COPPER = {
    "tones": (("waxed_oxidized_cut_copper", "waxed_oxidized_cut_copper_stairs", "waxed_oxidized_cut_copper_slab"),
              ("waxed_weathered_cut_copper", "waxed_weathered_cut_copper_stairs", "waxed_weathered_cut_copper_slab"),
              ("waxed_exposed_cut_copper", "waxed_exposed_cut_copper_stairs", "waxed_exposed_cut_copper_slab")),
    "ridge": ("waxed_cut_copper",),
    "verge": ("waxed_weathered_cut_copper", "waxed_weathered_cut_copper_stairs", "waxed_weathered_cut_copper_slab"),
    "gutter": "waxed_exposed_cut_copper_stairs",
}


def family(name, mats):
    for i, fam in enumerate(mats["tones"]):
        if name in fam:
            return i
    return None


def _course(rng, n, p_dark, p_pale):
    row, i = [], 0
    while i < n:
        r = rng.random()
        if r < p_dark / 2:                       # a dark run averages 2 cells, so start one at half the rate
            k = min(rng.randint(1, 3), n - i)
            row += [1] * k
            i += k
            gap = min(rng.randint(2, 5), n - i)  # always at least 2 base cells after a dark run: no chequer
            row += [0] * gap
            i += gap
        elif r < p_dark / 2 + p_pale and (not row or row[-1] != 2):
            row.append(2)
            i += 1
        else:
            row.append(0)
            i += 1
    return row


def tones(courses, length, seed=0):
    """[course 0 = eave .. course K-1 = ridge][cell] -> tone 0 / 1 / 2 (see the module doc)."""
    for attempt in range(200):
        rng = random.Random(seed * 7919 + attempt)
        g = []
        for k in range(courses):
            t = k / max(1, courses - 1)
            g.append(_course(rng, length, 0.35 - 0.25 * t, 0.03 + 0.09 * t))
        flat = [x for row in g for x in row]
        if 0.55 <= flat.count(0) / len(flat) <= 0.72:
            return g
    return g


def _place(out, axis, u, y, v, name, props):
    q = (u, y, v) if axis == "z" else (v, y, u)
    out[q] = (name, props)


def _uphill(axis, sign):
    """The facing of a stair whose tall side is uphill: +u is east (axis z) or south (axis x)."""
    return {("z", 1): "east", ("z", -1): "west", ("x", 1): "south", ("x", -1): "north"}[(axis, sign)]


def gable(span, length, axis="z", seed=0, mats=SLATE, overhang=1):
    """A straight gable roof over walls at u 0..span-1, v 0..length-1, pitched 26.5 degrees in slab pairs (bottom
    then top slab per block of run), the eave course at y 0. Returns {(x, y, z): (name, props)}."""
    out = {}
    u0, u1 = -overhang, span - 1 + overhang
    v0, v1 = -overhang, length - 1 + overhang
    mid = (u0 + u1) / 2
    half_cells = int(mid - u0)                   # cells from the eave to the ridge on one side
    g_front = tones(half_cells, v1 - v0 + 1, seed)
    g_back = tones(half_cells, v1 - v0 + 1, seed + 1000)
    for u in range(u0, u1 + 1):
        d = min(u - u0, u1 - u)                  # course index from the nearer eave
        side = 1 if u < mid else -1
        for v in range(v0, v1 + 1):
            if u == mid:
                _place(out, axis, u, d // 2, v, mats["ridge"][0], {})   # half a block proud of the top courses
                continue
            y, typ = d // 2, ("bottom" if d % 2 == 0 else "top")
            if v in (v0, v1):                    # the verge: the dark bargeboard edge at both gable ends
                name = mats["verge"][2]
            else:
                tone = (g_front if side == 1 else g_back)[d][v - v0]
                name = mats["tones"][tone][2]
            _place(out, axis, u, y, v, name, {"type": typ, "waterlogged": "false"})
        if u in (u0, u1):                        # the gutter under each eave, tall side against the wall
            for v in range(v0, v1 + 1):
                _place(out, axis, u, -1, v, mats["gutter"],
                       {"facing": _uphill(axis, side), "half": "top", "shape": "straight", "waterlogged": "false"})
    return out
