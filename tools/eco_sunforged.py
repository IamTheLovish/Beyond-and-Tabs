#!/usr/bin/env python3
"""The Sunforged (White Angel) economy set redrawn as one family: THE SUN MILL (wind_generator), THE SUN CRUCIBLE
(energy_converter) and THE SUN TREASURY (storage_vault) - so the three small buildings a base packs in rows belong to
the Temple of the Sun (the capitol, tools/capitol_town_centre.py build_grand), the Angel Fountain (the extractor,
gen_structures._sun_fountain_t1 / _t2) and the Training Hall (the T1 lab, tools/lab_barracks.py).

lovish, Oct 19 2026: "I want the buildings to look like they match each other so when bases get bigger they look
cohesive and beautiful". Players build these side by side, so they must read as a SET in a row, and each must still be
recognisable at a glance: the generator is energy, the converter turns energy into metal, the vault stores.

THE SUNFORGED STYLE KIT (the capitol's, the fountain's and the barracks'):
  - main blocks: white stone and quartz (quartz pillar / bricks / smooth / chiseled quartz, calcite, polished diorite);
  - trim: gold (the column capitals, the crowns, the sun's rim), lapis on the axes (the frieze);
  - accent: BLUE GLASS (lancets, the dome's drum) and blue banners;
  - light: lanterns on the crowns, candles on the cornice, an ochre-froglight sun as the heart;
  - silhouette motif: TIERED DOMES - a ring of stairs climbing in to a gold crown and its lantern;
  - family figure: the angel (lab_barracks.angel, the fountain's and the capitol's plaza angels);
  - signature detail: THE GOLD SUN - an ochre froglight in a gold ring - on every one of the three.

THE SET'S SHARED GRAMMAR (why they read as a row): the same 3 x 3 plinth (chiseled quartz corners, smooth quartz
edges, a gold heart); the same classical order on every corner - a shaft, then the same capital course at the same
look: GOLD capitals on the corners and a LAPIS frieze between them (order_band); the same light (ochre froglight and
lanterns); and THE GOLD SUN - a glowing ochre boss set in gold - on the camera faces of every one. Their heights step
down along a row - mill 12, crucible 9, treasury 7 - so a line of them has a skyline, not a fence; and each has its own
top for the RTS camera, which sees tops first: the angel, the radiant sun, the dome with its banners.

  THE SUN MILL (energy). A slender white tower on the order: tall blue lancets (panes, the recessed plane between the
  pier shafts) lit from inside by an ochre-froglight heart, each crowned by a glowing sun under the gold-and-lapis band.
  At the tower's top the drawn sails (white canvas on gilded spars, WindmillRenderClientEvents) turn on the north face
  and lean back over it, and on the tower top, facing the camera, stands THE ANGEL (lab_barracks.angel - the Training
  Hall's turret angel) with its wings spread free against the sky and its lantern raised, the sails turning up behind
  it like a second, greater pair of wings. Every cell the sails sweep (sail_sweep) is air.
  THE SUN CRUCIBLE (energy -> metal). A tempietto cage: chiseled feet, four thin posts (diorite walls - a full pier on
  the near corner would hide the middle of any 3 x 3 from the RTS camera) with iron bars between them round a column
  of furnace-orange glow (shroomlight: the melt); two lit blast furnaces open their mouths on the camera faces at its
  feet over an iron heart (the metal); the band; and on top a quartz bowl holding the gold sun (ochre froglight, the
  energy) radiating end-rod rays with a tall rod up - the sun poured down into metal. Gold-white light above, orange
  heat below: it reads as a furnace at a glance and glows at night.
  THE SUN TREASURY (storage). The closed, heavy one: quartz-pillar piers, casks (barrels, lids out) stacked on the
  west face, an iron strongroom door (no mob opens it) set back against the core on the south, the sun's boss over it in
  the frieze; a low dome of stairs to the gold crown and its lantern, and the kit's blue banners standing on the four
  gold capitals - a domed strongroom, shut.

GAME RULES (asserted in check_all; the three classes are shared by every faction - WindGenerator / EnergyConverter /
StorageVault - so nothing is per-variant except WindGenerator.SAIL_HUBS and BuildingAmbienceClientEvents.ENERGY_SPOTS):
  - the footprint is exactly 3 x 3 (BuildingPlacement and the bots pack them in rows); layer 0 a full solid pad (the
    classes declare no startingBlockTypes; the pad is what wind_generator_accepts_a_step_but_not_a_cliff stands on);
  - the mill keeps its SAILS (WindGenerator.SAIL_HUBS ""): the hub is a horizontal spruce log on the z axis on the
    north face, with air north of it; the hub rises from (1, 4, 0) to (1, 7, 0) and the blades from 2.6 to 2.8 (a
    taller tower: the sails turn at its top like a mill's cap, not halfway up). The rotor plane sits 0.95 outside the
    hub and leans back 40 degrees, so the blades sweep back INTO the footprint above the hub: every cell they pass
    through (sail_sweep(), worked out from the renderer's own geometry) is air;
  - nothing pops off (_SunRig.check_support, lab_barracks.Rig's: lanterns, candles, banners, bells); no pockets and
    nothing a unit can stand in (tools/audit_structure_pockets.py); no water, lava, fire, gravity blocks or redstone;
  - under ~150 blocks each, generator <= 16 high, converter and vault <= 10.

Usage: python3 tools/eco_sunforged.py [OUT_DIR] [--sheet SHEET.png]
       (writes wind_generator.nbt, energy_converter.nbt, storage_vault.nbt to OUT_DIR - default the scratch folder -
       checks them, and with --sheet draws the before / after sheet; shipping them is the coordinator's step)
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs   # noqa: E402
import lab_barracks as lb     # noqa: E402  - the family's angel, dome ring and support check

N = 3                                         # the footprint (BuildingPlacement packs them in rows)
CORNERS = ((0, 0), (2, 0), (0, 2), (2, 2))
EDGES = {(1, 0): "north", (0, 1): "west", (2, 1): "east", (1, 2): "south"}   # edge cell -> its outward face
INWARD = {"north": "south", "south": "north", "west": "east", "east": "west"}

# THE SAILS (WindGenerator.SAIL_HUBS "") - the hub cell, the face the rotor sits outside of, the blade length
HUB = (1, 7, 0)
HUB_OUT = "north"
BLADE = 2.8
# WindmillRenderClientEvents' rotor geometry (renderSails): the plane 0.95 outside the hub's centre, leaning back 40
# degrees, blades from 0.35 to 0.35 + length, 0.8 wide; the gilded spar runs 0 .. length + 0.4
ROTOR_OUT, ROTOR_TILT, BLADE_ROOT, BLADE_W, SPAR_W = 0.95, math.radians(40), 0.35, 0.8, 0.18
GRAZE = 0.1

FORBIDDEN = ("water", "lava", "fire", "sand", "gravel", "concrete_powder", "anvil", "redstone", "magma", "campfire",
             "powder_snow", "scaffolding", "pointed_dripstone", "dragon_egg", "_leaves", "grass_block", "mycelium")


class Rig(lb.Rig):
    """The Training Hall's rig (gen_structures._SunRig: settle() shapes stairs and joins walls and panes like the game
    does; lab_barracks.Rig.check_support knows hung lanterns and wall banners) on a 3 x 3."""

    def __init__(self, sy):
        gs._SunRig.__init__(self, N, sy, N)


# ---------------------------------------------------------------------------------------------- the shared grammar
def plinth(r):
    """Layer 0, the same on all three: chiseled quartz corners, smooth quartz edges and a gold heart - a full solid pad
    (the instant foundation the step test stands on)."""
    for x in range(N):
        for z in range(N):
            r.set(x, 0, z, "chiseled_quartz_block" if (x, z) in CORNERS else "smooth_quartz")
    r.set(1, 0, 1, "gold_block")


def moulding(r, y, skip=()):
    """A base moulding of quartz stairs on the edges, their low step outward (the plinth's profile, a third depth)."""
    for (x, z), out in EDGES.items():
        if (x, z) not in skip:
            r.st(x, y, z, INWARD[out])


def cornice(r, y, skip=(), name="quartz_stairs"):
    """The order's cornice: upside-down stairs all round with their cut-away lower step outward, so the crown course
    overhangs the frieze like the capitol's and the barracks'. settle() turns the corners."""
    for (x, z), out in EDGES.items():
        if (x, z) not in skip:
            r.st(x, y, z, INWARD[out], "top", name=name)
    for (x, z) in CORNERS:
        if (x, z) not in skip:
            # a corner takes the facing of one of its sides; settle() makes it an outer corner
            r.st(x, y, z, "south" if z == 0 else "north", "top", name=name)


def gold_sun(r, cx, cy, z):
    """THE SIGNATURE: the gold sun, a 3 x 3 disc in the x-y plane (it faces the south camera side) - a gold cross round
    an ochre-froglight heart, yellow glass in the corners for the rays' glow."""
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            if dx == 0 and dy == 0:
                r.set(cx, cy, z, "ochre_froglight", axis="y")
            elif dx == 0 or dy == 0:
                r.set(cx + dx, cy + dy, z, "gold_block")
            else:
                r.set(cx + dx, cy + dy, z, "yellow_stained_glass")


# ---------------------------------------------------------------------------------------------- the sails' sweep
def sail_sweep():
    """Every cell of the 3 x 3 the drawn sails pass through in a full turn (WindmillRenderClientEvents.renderSails:
    the rotor's centre 0.95 outside the hub's centre on its outward face, the plane leant back 40 degrees, four
    blades 0.8 wide from 0.35 to 0.35 + BLADE and the spar to BLADE + 0.4). Sampled finely over the whole disc the
    blades sweep, so it is a superset of the cells any one pose touches. A cell counts when a blade goes more than
    GRAZE into it: the blades' back face only skims the top corner of the cornice under the rotor (by < 0.07), as a
    real mill's sails skim its cap."""
    hx, hy, hz = HUB
    assert HUB_OUT == "north"
    cx, cy, cz = hx + 0.5, hy + 0.5, hz + 0.5 - ROTOR_OUT
    reach = math.hypot(BLADE_ROOT + BLADE, BLADE_W / 2) + 0.05
    cells = set()
    steps = 120
    for i in range(-steps, steps + 1):
        for j in range(-steps, steps + 1):
            u, v = reach * i / steps, reach * j / steps        # u along x, v up the leant plane
            if u * u + v * v > reach * reach:
                continue
            for t in (-0.1, 0.0, 0.1):                         # the blade's own thickness
                x = cx + u
                y = cy + v * math.cos(ROTOR_TILT) - t * math.sin(ROTOR_TILT)
                z = cz + v * math.sin(ROTOR_TILT) + t * math.cos(ROTOR_TILT)
                c = (math.floor(x), math.floor(y), math.floor(z))
                inside = all(GRAZE <= q - math.floor(q) <= 1 - GRAZE for q in (x, y, z))
                if inside and 0 <= c[0] < N and 0 <= c[2] < N and c[1] >= 0:
                    cells.add(c)
    return cells


def sail_pose(angle_deg=45.0):
    """The sails in one pose, as (cell -> block) for the preview sheet only (the game draws them; structure_render
    can't): white wool where a canvas blade covers most of a cell, gold where the spar runs. Coordinates may lie
    outside the footprint (negative x / z)."""
    hx, hy, hz = HUB
    cx, cy, cz = hx + 0.5, hy + 0.5, hz + 0.5 - ROTOR_OUT
    hits = {}
    for k in range(4):
        a = math.radians(angle_deg + 90 * k)
        dx, dy = math.sin(a), math.cos(a)               # along the blade in the rotor plane
        lx, ly = math.cos(a), -math.sin(a)              # across it
        for s in range(0, 60):
            rr = (BLADE + 0.4) * s / 59
            for w in (-0.35, -0.15, 0.0, 0.15, 0.35):
                if rr < BLADE_ROOT and w:
                    continue
                u, v = dx * rr + lx * w, dy * rr + ly * w
                x = cx + u
                y = cy + v * math.cos(ROTOR_TILT)
                z = cz + v * math.sin(ROTOR_TILT)
                c = (math.floor(x), math.floor(y), math.floor(z))
                spar = abs(w) < 0.01
                tally = hits.setdefault(c, [0, 0])
                tally[0 if spar else 1] += 1
    return {c: ("gold_block" if t[0] > t[1] / 3 else "white_wool") for c, t in hits.items() if sum(t) >= 4}


def order_band(r, y, skip=(), suns=()):
    """The order's capital course, the same on all three: GOLD capitals on the corners and a LAPIS frieze between them
    (the barracks' triglyphs, the capitol's axes); `suns` edges carry the gold sun's glowing boss instead."""
    for (x, z) in CORNERS:
        r.set(x, y, z, "gold_block")
    for (x, z) in EDGES:
        if (x, z) not in skip:
            r.set(x, y, z, "ochre_froglight" if (x, z) in suns else "lapis_block", **({"axis": "y"} if (x, z) in suns else {}))


# ======================================================================================================== THE SUN MILL
MILL_H = 12
CAMERA_EDGES = ((0, 1), (1, 2))                        # the west and south faces: what the RTS camera sees


def draw_mill(r):
    plinth(r)
    hx, hy, hz = HUB
    # the order on the corners: quartz-pillar shafts y1..5, the capital course at y6, the cornice at y7
    for (x, z) in CORNERS:
        for y in range(1, 6):
            r.pillar(x, y, z)
    order_band(r, 6, skip=((1, 0),))
    # the base moulding and the tower's core: quartz bricks round a heart of ochre froglights that light the lancets
    moulding(r, 1)
    for y in range(1, 7):
        r.set(1, y, 1, "ochre_froglight" if y in (2, 3, 4) else "quartz_bricks", **({"axis": "y"} if y in (2, 3, 4) else {}))
    # tall lancets on the three faces the sails don't cover (blue panes: the recessed plane between the pier shafts);
    # over the two the camera sees, THE SUN - a glowing ochre boss in a gold ring (the band's capitals and the gold
    # head under it); the north face, behind the sails, is plain quartz brick
    for (x, z), out in EDGES.items():
        if out == "north":
            for y in (2, 3, 4, 5):
                r.set(x, y, z, "quartz_bricks")
        else:
            for y in (2, 3, 4):
                r.set(x, y, z, "blue_stained_glass_pane")
            if (x, z) in CAMERA_EDGES:
                r.set(x, 5, z, "ochre_froglight", axis="y")
            else:
                r.set(x, 5, z, "chiseled_quartz_block")
    r.st(1, 6, 0, "south", "top")                     # the corbel the hub rests on
    # the hub: a spruce axle on the z axis, its rotor face north (WindGenerator.SAIL_HUBS "")
    r.set(hx, hy, hz, "stripped_spruce_log", axis="z")
    # the cornice round the tower top (the hub takes its north middle)
    cornice(r, 7, skip=((1, 0),))
    r.set(1, 7, 1, "quartz_bricks")
    # the tower top (y 8+): the sails sweep the north row at y8-9 and the middle row at y9 (sail_sweep) - kept air.
    # The angel stands on the south edge facing the camera, its wings spread free against the sky (the barracks'
    # turret angel), the sails turning up behind it like a second, greater pair of wings.
    lb.angel(r, 1, 8, 2)                               # body y8, head y9, wings y9-11, gold boss y10, lantern y11
    for x in (0, 2):                                   # candles on the tower's shoulders, beside the angel
        r.candle(x, 8, 1)


def wind():
    """THE SUN MILL (wind_generator): a white tower on the classical order, tall lit blue lancets under glowing gold
    suns, the drawn sails turning on its north face at the top, and on it the angel facing the camera with its lantern
    raised. 3 x 12 x 3."""
    return wind_rig().structure()


def wind_rig():
    """The settled, support-checked canvas wind() writes (the checks and the sheet work on it)."""
    r = Rig(MILL_H)
    draw_mill(r)
    r.settle()
    r.check_support("wind_generator")
    return r


# ======================================================================================================= THE SUN CRUCIBLE
CRUCIBLE_H = 9


def draw_crucible(r):
    plinth(r)
    # chiseled feet, then the cage's four posts (diorite walls - thin, so the RTS camera, which looks across the near
    # corner, sees the glow inside: a full pier there would hide it, as it hides any 3 x 3's middle), the capital
    # course at y4
    for (x, z) in CORNERS:
        r.set(x, 1, z, "chiseled_quartz_block")
        r.set(x, 2, z, "diorite_wall")
        r.set(x, 3, z, "diorite_wall")
    order_band(r, 4)
    # at its feet two lit blast furnaces open their mouths on the camera faces (west, south); the back edges are the
    # moulding; the heart an iron block - the metal the sun is turned into
    r.set(1, 1, 2, "blast_furnace", facing="south", lit="true")
    r.set(0, 1, 1, "blast_furnace", facing="west", lit="true")
    r.st(1, 1, 0, "south")
    r.st(2, 1, 1, "west")
    r.set(1, 1, 1, "iron_block")
    # the cage: iron bars between the posts, two courses, round the melt - a shroomlight column glowing furnace-orange
    # (the sun above it is ochre gold: the energy, then the heat)
    for (x, z) in EDGES:
        r.set(x, 2, z, "iron_bars")
        r.set(x, 3, z, "iron_bars")
    r.set(1, 2, 1, "shroomlight")
    r.set(1, 3, 1, "shroomlight")
    r.set(1, 4, 1, "iron_block")
    # the bowl: a ring of stairs rising outward round a gold cradle, the sun in it
    for (x, z), out in EDGES.items():
        r.st(x, 5, z, out)
    for (x, z) in CORNERS:
        r.st(x, 5, z, "north" if z == 0 else "south")
    r.set(1, 5, 1, "gold_block")
    r.set(1, 6, 1, "ochre_froglight", axis="y")
    # the sun's rays: end rods outward from the sun and one up
    for (x, z), out in EDGES.items():
        r.set(x, 6, z, "end_rod", facing=out)
    r.set(1, 7, 1, "end_rod", facing="up")
    r.set(1, 8, 1, "end_rod", facing="up")


def converter():
    """THE SUN CRUCIBLE (energy_converter): a tempietto of quartz columns round an iron cage with a glowing melt, lit
    blast furnaces at its feet and the gold sun radiating in a bowl on top. 3 x 9 x 3."""
    return converter_rig().structure()


def converter_rig():
    """The settled, support-checked canvas converter() writes (the checks and the sheet work on it)."""
    r = Rig(CRUCIBLE_H)
    draw_crucible(r)
    r.settle()
    r.check_support("energy_converter")
    return r


# ======================================================================================================== THE SUN TREASURY
TREASURY_H = 7


def draw_treasury(r):
    plinth(r)
    for (x, z) in CORNERS:
        for y in (1, 2):
            r.pillar(x, y, z)
    order_band(r, 3, suns=((1, 2),))                 # the sun over the door
    # the west face: casks stacked lids-out between the piers
    r.set(0, 1, 1, "barrel", facing="west", open="false")
    r.set(0, 2, 1, "barrel", facing="west", open="false")
    # the south face: the strongroom door - an iron door (no mob opens one) hung against the core, so it stands back
    # 13/16 behind the piers (the recessed plane), under the sun's boss in the frieze
    for y, half in ((1, "lower"), (2, "upper")):
        r.set(1, y, 2, "iron_door", facing="south", half=half, hinge="left", open="false", powered="false")
    # the back faces: quartz brick walls; the core quartz brick
    for (x, z) in ((1, 0), (2, 1)):
        r.set(x, 1, z, "quartz_bricks")
        r.set(x, 2, z, "quartz_bricks")
    for y in (1, 2, 3):
        r.set(1, y, 1, "quartz_bricks")
    # the dome: stairs climbing in on the edges, blue banners standing on the corner capitals, the gold crown, lantern
    for (x, z), out in EDGES.items():
        r.st(x, 4, z, INWARD[out])
    r.set(1, 4, 1, "smooth_quartz")
    r.set(1, 5, 1, "gold_block")
    r.lantern(1, 6, 1)
    for (x, z), rot in (((0, 0), "6"), ((2, 0), "10"), ((0, 2), "2"), ((2, 2), "14")):
        r.set(x, 4, z, "blue_banner", rotation=rot)


def vault():
    """THE SUN TREASURY (storage_vault): a closed quartz strongroom on the order - casks on the west face, a gold door
    on the south, the sun in its frieze - under the kit's dome with a gold crown, a lantern and blue corner banners.
    3 x 7 x 3."""
    return vault_rig().structure()


def vault_rig():
    """The settled, support-checked canvas vault() writes (the checks and the sheet work on it)."""
    r = Rig(TREASURY_H)
    draw_treasury(r)
    r.settle()
    r.check_support("storage_vault")
    return r


# ======================================================================================================= checks
def check_rig(r, name, max_h):
    sx, sy, sz = r.size
    assert (sx, sz) == (N, N), "%s: footprint %dx%d, must be 3x3" % (name, sx, sz)
    assert sy <= max_h, "%s: %d high, at most %d" % (name, sy, max_h)
    for x in range(N):
        for z in range(N):
            n = r.get(x, 0, z)[0]
            assert gs._sun_sturdy(r.get(x, 0, z), "up") and not gs._sun_is(r.get(x, 0, z), *gs._SUN_THIN), \
                "%s: layer 0 must be a full solid pad, (%d,0,%d) is %s" % (name, x, z, n)
    top = max(y for (_, y, _) in r.b)
    assert top == sy - 1, "%s: the canvas is %d high but the art tops out at y%d" % (name, sy, top)
    bad = [(p, n) for p, (n, _) in r.b.items() if any(f in n for f in FORBIDDEN) and n not in ("blue_stained_glass",)]
    assert not bad, "%s: forbidden blocks %s" % (name, bad)
    count = sum(1 for (n, _) in r.b.values() if n not in ("air", "cave_air"))
    assert count < 150, "%s: %d blocks, keep under ~150" % (name, count)
    return count


def check_sails(r):
    """The renderer's hub and the sails' room, as tools/check_sail_hubs.py and every_sail_hub_sits_inside_its_mill
    check them, plus the sweep (theirs doesn't look at it): the hub log on the z axis, air north of it, blades that
    clear the ground once tilted, and every swept cell air."""
    hx, hy, hz = HUB
    n, p = r.get(hx, hy, hz)
    assert n in ("stripped_spruce_log",) and p.get("axis") == "z", "hub %s is %s %s" % (HUB, n, p)
    assert hz == 0 and r.get(hx, hy, hz - 1)[0] == "air"        # north of the hub is outside the footprint: open
    drop = (BLADE_ROOT + BLADE) * math.cos(ROTOR_TILT)
    assert drop <= hy + 0.5, "blades reach %.2f below a hub %.1f up" % (drop, hy + 0.5)
    assert 1.5 <= BLADE <= 3.2
    busy = sorted((c, r.get(*c)[0]) for c in sail_sweep() if r.get(*c)[0] != "air")
    assert not busy, "the sails would cut through %s" % busy


def check_pockets(path, name):
    import audit_structure_pockets as ap
    one_way, ground, roof, rooms, note = ap.audit(name, path)
    assert not one_way and not ground and "TRAPPED" not in note, "%s: pockets %s %s %s" % (name, one_way, ground, note)
    # nothing a unit can stand in: every walkable cell over the footprint is a roof-top cell no move from the ground
    # reaches (the top of the cornice, the dome) - none on the plinth or the faces
    # (the audit's own forward flood from the open ground round it, with the grid's moves)
    pad = ap.Pad(ap.load_nbt(path))
    m = ap.MARGIN
    stack = [(x, 1, z) for x in range(-m, N + m) for z in range(-m, N + m)
             if not (0 <= x < N and 0 <= z < N) and pad.walkable(x, 1, z)]
    entered = set(stack)
    while stack:
        for c in ap.neighbours(pad, *stack.pop()):
            if -m <= c[0] < N + m and -m <= c[2] < N + m and 1 <= c[1] <= pad.sy + 1 and c not in entered:
                entered.add(c)
                stack.append(c)
    stand = [(x, y, z) for x in range(N) for z in range(N) for y in range(1, pad.sy + 1) if pad.walkable(x, y, z)]
    reachable = sorted(c for c in stand if c in entered)
    assert not reachable, "%s: a unit can walk onto %s" % (name, reachable)
    return roof, rooms, note, stand


BUILDS = (("wind_generator", wind_rig, 16), ("energy_converter", converter_rig, 10), ("storage_vault", vault_rig, 10))


def check_all(out):
    os.makedirs(out, exist_ok=True)
    report = []
    for name, fn, max_h in BUILDS:
        r = fn()
        count = check_rig(r, name, max_h)
        if name == "wind_generator":
            check_sails(r)
        path = os.path.join(out, name + ".nbt")
        r.structure().write(path)
        roof, rooms, note, stand = check_pockets(path, name)
        report.append("%-17s 3x%dx3 %3d blocks; pockets: none (ground or one-way); no cell a unit reaches from the ground (%d roof-top cells only a fall leaves)%s" % (
            name, r.size[1], count, len(stand), note))
    return report


# ======================================================================================================= the sheet
def sheet(out, path):
    """Old three vs new three at 32 px, a close row of the new three side by side (as they're built), and the mill
    with its sails drawn in one pose (the game draws them; this is an approximation in blocks)."""
    import subprocess
    from PIL import Image, ImageDraw, ImageFont
    here = os.path.dirname(os.path.abspath(__file__))
    render = os.path.join(here, "structure_render.py")
    tmp = os.path.join(out, "_sheet")
    os.makedirs(tmp, exist_ok=True)

    def run(args, png, px=32):
        subprocess.run([sys.executable, render] + args + ["-o", png, "--px", str(px)], check=True,
                       stdout=subprocess.DEVNULL, cwd=os.path.dirname(here))
        return Image.open(png).convert("RGB")

    new = [os.path.join(out, n + ".nbt") for n, _, _ in BUILDS]
    old_img = run(["wind_generator", "energy_converter", "storage_vault"], os.path.join(tmp, "old.png"))
    new_img = run(new, os.path.join(tmp, "new.png"))
    # the mill with its sails in one pose: a wider canvas (the blades reach past the footprint)
    m = wind_rig()
    pose = sail_pose(20.0)
    xs = [c[0] for c in pose] + [0, N - 1]
    zs = [c[2] for c in pose] + [0, N - 1]
    ox, oz = -min(xs), -min(zs)
    big = gs._SunRig(max(xs) + ox + 1, MILL_H, max(zs) + oz + 1)
    for (x, y, z), (n, p) in m.b.items():
        big.set(x + ox, y, z + oz, n, **p)
    for (x, y, z), n in pose.items():
        if 0 <= y < MILL_H and big.get(x + ox, y, z + oz)[0] == "air":
            big.set(x + ox, y, z + oz, n)
    sails_nbt = os.path.join(tmp, "mill_with_sails.nbt")
    big.structure().write(sails_nbt)
    sails_img = run([sails_nbt], os.path.join(tmp, "sails.png"))
    # the row: three of each side by side on one canvas, one block apart, as a base lays them out
    row = gs._SunRig(N * 9 + 8, MILL_H, N)
    for i, (name, fn, _) in enumerate(BUILDS * 3):
        rr = fn()
        for (x, y, z), (n, p) in rr.b.items():
            row.set(x + i * (N + 1), y, z, n, **p)
    row_nbt = os.path.join(tmp, "row.nbt")
    row.structure().write(row_nbt)
    row_img = run([row_nbt], os.path.join(tmp, "row.png"))
    close_img = run(new, os.path.join(tmp, "close.png"), px=64)

    font = None
    for f in ("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",):
        if os.path.exists(f):
            font = ImageFont.truetype(f, 18)
    font = font or ImageFont.load_default()
    pad = 16
    top_w = old_img.width + new_img.width + sails_img.width + 4 * pad
    w = max(top_w, row_img.width + 2 * pad, close_img.width + 2 * pad)
    h = 30 + max(old_img.height, new_img.height, sails_img.height) + 40 + row_img.height + 40 + close_img.height + pad
    canvas = Image.new("RGB", (w, h), (24, 24, 28))
    d = ImageDraw.Draw(canvas)
    y = 8
    x = pad
    for title, img in (("BEFORE (32 px/block)", old_img), ("AFTER: Sun Mill / Sun Crucible / Sun Treasury", new_img),
                       ("+ sails (one pose, approx.)", sails_img)):
        d.text((x, y), title, fill=(230, 230, 230), font=font)
        canvas.paste(img, (x, y + 24))
        x += img.width + pad
        if title.startswith("BEFORE"):
            d.line((x - pad // 2, y, x - pad // 2, y + 24 + img.height), fill=(90, 90, 90), width=2)
    y += 24 + max(old_img.height, new_img.height, sails_img.height) + 12
    d.text((pad, y), "AS A ROW (mill, crucible, treasury x3, one block apart, 32 px/block)", fill=(230, 230, 230),
           font=font)
    canvas.paste(row_img, (pad, y + 24))
    y += 24 + row_img.height + 12
    d.text((pad, y), "AFTER close-up (64 px/block)", fill=(230, 230, 230), font=font)
    canvas.paste(close_img, (pad, y + 24))
    canvas.save(path)
    return path


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    out = args[0] if args else os.path.join(os.environ.get("TMPDIR", "/tmp"), "eco_sunforged")
    for line in check_all(out):
        print(line)
    if "--sheet" in sys.argv:
        print("sheet:", sheet(out, sys.argv[sys.argv.index("--sheet") + 1]))
