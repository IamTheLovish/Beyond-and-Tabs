# Detail recipe library - how the best downloaded builds construct each element (Oct 29 2026)

lovish, Oct 28 23:44 "become a master architect", 23:49 "should you study it more". design/architecture_rules.md
measured 26 builds in aggregate; this file goes block by block through the best of them and extracts a RECIPE per
architectural element, in vanilla 1.20.1 blocks, sized for our scale (storey 4-6), so tools/roof_kit.py-style parts
can build them. Study only: a recipe of a few blocks is a technique, never a copy. Sources (authors in
scratchpad schem_in/SOURCES.md): victorian-factory.nbt (koin), train-station-1890.schem (seuchendoktor),
gothic-castle.schematic (nighthearted), chapel-of-brittany.schematic (ragnar_le_rouge), copper-spire-a/b.schematic
(polishplayer34), medieval-houses/brickhouse3 + stonehouse3 (black_corsair), steampunk-factory.schematic (ggj16),
elven-tower.schem (skyblocksquad). Coordinates are the file's own (x, y, z); windows dumped were <= 9x9x12.

Reading the ASCII: rows are y (top first), columns the horizontal axis named; `.` air. Glyph letters are per-recipe.
"Glyph contribution" refers to the study tool's classes (`| = * g` thin/detail, `s _` shapes) and the G2/G5 gates.

## 0. Modded -> vanilla 1.20.1 mapping used below (factory uses Create / Create Deco; spires use stand-ins)
| in the file | means | vanilla 1.20.1 (our palette) |
|---|---|---|
| create:polished_cut_tuff (+_slab/_stairs/_wall) | pale cut-stone trim | smooth_stone / polished_andesite (+ slab, stairs; wall -> andesite_wall) |
| create:cut_tuff(_bricks), cut_tuff_brick_wall | pale ashlar | stone_bricks, stone_brick_wall |
| bricks / createdeco:long_red_bricks | body brick T0 | bricks |
| createdeco:scarlet_bricks | a brighter-red course | red_terracotta (dull) or bricks; one row only |
| createdeco:dean_bricks / umber_bricks (+corner, +wall) | dark brown-grey brick (stacks, clerestory) | nether_bricks, nether_brick_wall |
| create:cut_granite(_bricks), polished_cut_granite_wall / _stairs | pink-red dressed stone bands | polished_granite, granite_wall, polished_granite_stairs |
| create:polished_cut_calcite / cut_calcite(_bricks) | white keystone / arch block | quartz_bricks / smooth_quartz |
| create:cut_dripstone(_bricks), polished_cut_dripstone | tan plinth stone | smooth_sandstone (ours: tuff / polished_andesite) |
| create:waxed_oxidized_copper_shingle_stairs / _shingles | green copper roof | waxed_oxidized_cut_copper_stairs / waxed_oxidized_cut_copper |
| create:weathered_iron_window / _pane | iron-framed glazing, full / pane | glass (tinted: gray_stained_glass) / glass_pane; frame iron_bars |
| createdeco:industrial_iron_sheet_metal | riveted iron band | iron_block (ours: waxed_cut_copper = brass band) |
| create:metal_girder (axis=y / x) | girder post / beam | iron_bars (post), stripped_spruce_log or chain (beam) |
| createdeco:industrial_iron_catwalk_railing / _catwalk | railing / grating | iron_bars / iron_trapdoor |
| createdeco:industrial_iron_bars_overlay | ridge cresting | iron_bars |
| create:cut_deepslate (+_wall) | dark crown stone | deepslate_bricks, deepslate_brick_wall |
| create:gantry_shaft + lantern[hanging] | lamp bracket | chain + lantern[hanging=true] |
| copper-spire diamond_block / gold_block | copper shell / brass | waxed_oxidized_copper / waxed_cut_copper |
| steampunk snow_block / cobweb / black_wool | steam cloud / smoke / curved pipe stand-ins | (leave out) / campfire smoke / chain or wall |

---------------------------------------------------------------------------------------------------------------------
## R1. Eave + gutter (hall eave)
**Found:** victorian-factory.nbt, hall south wall z 34, section x 20, y 33..38; the same at the clerestory wall z 37
y 43..44. Train-station-1890.schem section x 23 z 40 y 12..13 (cornice = slab + upside-down stair).
**What it does:** turns the wall top into a 2-cell shadow band (pale lip over a dark soffit) before the roof starts.
```
section x 20 (columns z 33..37; wall at z 34, roof rises inward to the ridge at z 40)
y38  .  .  C  K  L     C = oxidized copper stair facing=south half=bottom (course 3)
y37  .  C  L  O  .     L = oak_stairs facing=north half=top  (UPSIDE-DOWN, under the course = soffit)
y36  C  L  .  .  .     O = spruce_slab top (ceiling lining)
y35  E  P  .  .  .     E = smooth_stone_slab[type=top] (the cornice LIP, 1 proud of the wall)
y34  .  Q  .  .  .     P = smooth_stone (the wall's top course in pale trim)   Q = red_terracotta (1 darker course)
y33  .  R  .  .  .     R = bricks (body)
     z33 z34 z35 z36 z37
```
Surprise: the factory's hall roof does NOT oversail the wall; its first course sits ON the wall and the overhang is
the cornice slab. The eave shadow comes from (a) the pale slab lip and (b) the upside-down stair under the first
course. The station does the same with stone: y 13 `stone_brick_stairs facing=south half=top` under a y 14 `stone_brick_slab top`.
**Vanilla recipe at our scale (hall eave y 7):** wall top y 6 = smooth_stone; y 6 one cell proud = smooth_stone_slab
top; y 7 first roof course over the wall (slab/stair), and the cell inside it at y 7 = `<roof>_stairs half=top`
facing uphill (the soffit); gutter = `waxed_exposed_cut_copper_stairs half=top` facing the wall ON the lip cell (y 7,
1 proud) when the course oversails (medieval version, R2). One darker course (red_terracotta) directly under the lip.
**Rule:** the eave is a pale lip over a dark underside - the stair under the first course is upside-down so the soffit is shadowed.
**Glyphs:** `_` (lip) + `s` (soffit) + one depth plane (lip 1 proud). No thin blocks unless the gutter is a wall/chain downpipe (R10).

## R2. Oversailing eave (house version) + verge / bargeboard
**Found:** brickhouse3.schematic section z 5 y 12..13 (eave), section x 2 y 12..13 (verge), top plan rows z 2..3.
victorian-factory gable end section z 37 x 7..8 y 38..39 (stone verge).
**What it does:** the roof plane projects 1 past the wall; the verge is a different block at the gable edge.
```
brickhouse3 section z 5 (columns x 0..17; walls = spruce_log posts at x 1 and x 15)
y13  A  B  B  B  B ... B  B  A     A = brick_stairs half=bottom, low step OUTWARD (the drip edge, 1 proud of the wall)
y12  .  C  .  .  .     .  C  .     B = brick_slab bottom (the course)        C = spruce_log (wall post)
```
```
factory gable end, section z 37 (columns x 7..12; wall = corner nether_bricks at x 8)
y39  A  B  B  B  B  D     A = smooth_stone_stairs facing=south half=bottom = the stone VERGE cap, 1 proud of the wall
y38  L  M  N  N  N  E     L = stone_brick_stairs facing=north half=TOP (the under-verge bracket)   M = corner brick
                          N = oak_stairs half=top (soffit)   B = copper course
```
**Vanilla recipe:** eave cell (1 proud) = `<roof>_stairs half=bottom` with the low step outward (facing = toward the
wall); verge cell (1 proud at the gable) = a contrasting `dark_oak_slab top` / `smooth_stone_stairs` cap, and under it
`stone_brick_stairs half=top` facing out as the bracket. Copper gutter stair on the eave cell's wall side.
**Rule:** the overhang cell is a STAIR whose low step points outward (a drip edge), never a slab that looks like a shelf.
**Glyphs:** `s` x2 per edge cell; adds the 1-block overhang G1 needs and a second depth plane on the gable.

## R3. Ridge + hip + cresting
**Found:** victorian-factory upper hall ridge, section x 20 y 47..48 z 40; gothic-castle spire section z 42 y 132..165;
elven-tower cone y 41..46 x 4..14 z 4..14 (plan slices).
```
factory ridge (section x 20, columns z 37..43)
y48  .  .  .  A  .  .  .     A = iron_bars (ridge CRESTING, the iron comb along the ridge)
y47  .  .  .  B  .  .  .     B = waxed_oxidized_cut_copper FULL block (the ridge cap, a full block, not a slab)
y46  .  .  C  .  D  .  .     C/D = copper stairs facing south/north half=bottom (top course)
```
Gothic spire: the hips are `dark_oak_fence` columns at the four arrises every 3 rows (y 141..143, 147..149 ...) in a
`dark_oak_planks` pyramid that narrows 5 -> 3 -> 1 over 3-row tiers, finial = `cobblestone_wall` post 3 high.
Elven cone: each ring narrows by 1 per y; the hip cells (diagonals) are `waxed_oxidized_copper` full blocks and the
faces `moss_block` (= a second green tone) - the hips are the DIFFERENT tone, the faces the base tone.
**Vanilla recipe (hall ridge y 9-10, shed ridge y 6):** ridge = one full block (`waxed_oxidized_cut_copper` on copper;
`polished_blackstone_bricks` on slate) standing a half block above the top courses; cresting on the hall only =
`iron_bars` every cell (or every 2nd for a lighter comb); hips on the cone tiers = `polished_blackstone_brick_wall`
columns at the arrises every 3rd row, the plane between in T0.
**Rule:** the ridge is a full block half a block proud with a thin comb on top; hips are the darker/other tone, not a tick.
**Glyphs:** `|` (cresting / hip walls) - the one place a roof gives G2 thin cells; `#` ridge.

## R4. Dormer / cross-gable eye
**Found:** brickhouse3.schematic sections x 6 and x 7 y 8..14, z 6..16 (the four cross gables on the hips).
**What it does:** a 3-wide gable rides up through the main slope; its face is glass with timber cheeks; it breaks the
roof outline and explains the "eye" as a window.
```
section x 6 (columns z 6..16; the main roof is the brick slab plane at y 12..13)
y14  .  .  .  A  B  B  B  A  .  .  .     A = brick_slab bottom (dormer eave)   B = brick_stairs half=bottom (its mini ridge)
y13  A  .  C  D  E  E  E  D  F  .  A     C/F = brick_stairs (the rake, facing in)  D = bricks  E = oak_planks (cheek lining)
y12  .  D  D  E  G  G  G  E  D  D  .     G = glass_pane (a 3-wide eye, 2 rows tall)
y11  .  .  H  E  G  G  G  E  H  .  .     H = stone_bricks (the dormer's cill wall)
y10  .  .  H  E  E  E  E  E  H  .  .
```
In plan the dormer face is 1 proud of the hip slope below it; the rake stairs are the same brick as the roof
courses, the cheeks timber (oak_planks), the sill stone_bricks.
**Vanilla recipe (cone eye y 14..16 z 11..13; hall dormers 1 per 2 bays):** face 3 wide standing 1 proud of the roof
plane: `polished_andesite` sill row, `glass_pane` x3 2 rows tall, `dark_oak_planks` cheeks (1 each side), rake = the
roof's own stair block facing inward each side, cap = 1 `<roof>_slab bottom` ridge cell; 1 `lantern` behind the glass.
**Rule:** a dormer is a tiny gable in the ROOF material with cheeks in the WALL/timber material - it belongs to the roof.
**Glyphs:** `g` x6, `s` x4, `_`; adds a depth plane on the roof (face 1 proud).

## R5. Roof light / glazed barrel roof + clerestory monitor
**Found:** victorian-factory glazed barrel over the north hall, section x 20 z 23..32 y 29..33 and section z 26
x 5..20; clerestory (monitor) along the main hall, section z 37 x 12..42 y 39..43.
```
barrel (section x 20, columns z 23..32; wall at z 25)
y33  A  .  B  B  C  B  B  .  .  .     B = glass full blocks (the vault), C = iron_block ridge purlin (axis=z)
y32  A  B  .  .  .  .  .  B  .  .     A = red_terracotta (the gable wall above the vault)
y31  A  .  .  .  .  .  .  .  B  .     the vault = a stepped quarter-circle of FULL glass blocks: 1 up, 1 in, 1 up,
y30  D  A  .  .  .  .  .  .  B  .     2 in ... ending in a flat 5-wide crown with one iron purlin in the middle
y29  .  E  .  .  F  .  .  .  E  G     E = smooth_stone pier top   F = iron girder   G = smooth_stone_slab top (lip)
```
Along the barrel (section z 26): `glass_pane` edge x 5 then glass full x 6..20 at y 33; every 11th cell an
`iron_block` rib; on the side walls `glass_pane[north/south]` vertical strips y 24..28 between brick piers.
```
clerestory monitor (section z 37, columns x 12..27, y 39..44) - a strip of windows between a lower and an upper roof
y44  A  B  B  B  B ...          A = pale stair verge, B = copper course (upper roof)
y43  D  E  F  F  E  F  F ...    E = nether_bricks post, F = nether_brick_stairs half=top (the window HEAD, upside-down)
y40..42  D  D  I  I  E  I  I    I = glass_pane; window = 2 panes wide, 3 tall, posts 1 brick between pairs
y39  D  D  J  J  E  J  J        J = nether_brick_stairs half=bottom (the SILL)
y38  E  E  E  E  E  E  E ...    lower roof's top course meets the monitor wall here
```
**Vanilla recipe (shed roof lights, hall monitor):** shed: replace the slope cell at x 12 / x 15 for 3 z by
`glass_pane` between `polished_blackstone_brick_wall` posts, `lantern[hanging]` under each; hall: a 2-high monitor
strip at the ridge (y 9..10) of `glass_pane` pairs between `nether_bricks` posts with stair sill/head, its own slab
cap; barrel over the door lane (x 0..8 z 10..14) = a stepped quarter-circle of `glass` full blocks 1-1-2 with a
`waxed_cut_copper` purlin at the crown.
**Rule:** glass in a roof is FRAMED - posts, sill stair, head stair, a purlin; a bare pane row reads as a hole.
**Glyphs:** `g` heavy (G2), `|` posts, `s` sill/head.

## R6. Cornice and string course
**Found:** train-station-1890.schem section z 40 y 13..18 x 20..40 (three stacked cornices); victorian-factory
pier bay y 12..13, y 29..30; factory chimney base y 28..30.
```
station (section z 40, columns x 28..40; wall face = bricks I)
y18  A  A  A  A  C  D  D  D  D ...   A/D = stone_brick_slab bottom (the top ledge), C = stair outer corner
y17  .  .  .  .  .  G  H  .  .       G = stone_brick_wall (parapet posts)   H = iron_bars (balustrade, R13)
y16  .  .  .  .  C  D  .  .  .       the cornice steps OUT as it goes up: slab at wall plane, then slab 1 proud,
y15  C  D  .  .  .  D  .  .  .       then stairs facing=north half=bottom on top of the walls
y14  .  .  D  .  .  .  D  G  H       D = stone_brick_wall[north=tall,up=true] (a thin wall ROW = the dentil band)
y13  .  G  H  G  G  G  H  I  I       I = bricks (body)
y12  M  I  I  I  I  I  I  K  I       K = brick_stairs facing=north half=top (string course under the window heads)
```
The station's cornice is 3 rows: a brick `stairs half=top` string under the heads, a `stone_brick_wall` row (thin,
dentils) at the storey line, a `stone_brick_slab` ledge 1 proud with `stairs half=bottom` fill behind it.
Factory: y 12 = `polished_granite` band flush, y 13 = `smooth_stone_slab bottom` 1 proud along the pier line + a
`polished_diorite` ledge on the pier (the storey line), y 29 = `stone_brick_stairs half=top` bracket under the lip.
**Vanilla recipe (storey 5):** y 4 string = `brick_stairs half=top` facing out, flush (a shadow line); y 5 dentil =
`stone_brick_wall` row (thin, half-block, air behind it reads as teeth); y 6 ledge = `smooth_stone_slab bottom` 1
proud with `smooth_stone_stairs half=bottom` facing in behind it (fills the top to the wall).
**Rule:** a cornice steps outward as it rises (string flush -> dentils half proud -> ledge 1 proud); 3 rows, 3 planes.
**Glyphs:** `|` (the wall row counts as thin), `_`, `s`; the ledge is a depth plane on every camera face (G5).

## R7. Pier / pilaster with base and capital
**Found:** victorian-factory west face, pier at z 23 (columns x 4..8, y 0..13) - the dump at x 3..8 z 23..30;
train-station piers (section z 40, x 20/26/32: `I K I` brick with stair capitals at y 13).
```
factory pier, column at z 23 (x -> depth: x 4 is the OUTER plane)
y13  S  P  .      S = smooth_stone_stairs facing=east half=TOP   } the CAPITAL = a stair pair (top + bottom)
y12  s  P  Q      s = smooth_stone_stairs facing=east half=bottom } that bulges out then back = an echinus
y11  W  P  P      W = stone_brick_wall[east=tall,up=true]  <- a thin WALL block is the pilaster FACE (half a block proud)
y3-10 W  P  G/P   P = smooth_stone (pier body)   G = polished_granite every other row behind (rustication)
y2   W  P  D      D = smooth_sandstone (plinth)
y1   .  a  D      a = mud_bricks / smooth_sandstone (base course, 1 wider at y 0)
y0   .  b  P P P  b = sandstone bricks: the base is a different, paler stone, one block wider
     x4 x5 x6
```
Between piers (z 24..30) the pier plane is x 5 (`smooth_stone` / `polished_granite` alternating rows = a banded
pier face), the window plane x 6 (R8). So a bay has 3 depths: pilaster strip (x 4, thin), pier (x 5), window (x 6).
Every pier carries a `chain` + `lantern[hanging]` at y 9..12 one cell in front (the lamp bracket on each pier).
**Vanilla recipe (storey 5, pier 1 wide):** x 0 = `andesite_wall` column y 1..3 (the pilaster face, thin); x 1 =
`smooth_stone` y 1..4 with `polished_granite` at y 2 and y 4 (bands); base y 0 = `polished_andesite` 1 wider; capital
y 4 = `smooth_stone_stairs half=bottom` facing out + y 5 `smooth_stone_stairs half=top` facing out (2 stairs, a bulge);
one `chain` + `lantern[hanging]` at y 3..4 in front of every 2nd pier.
**Rule:** the pilaster is a thin wall block in front of the pier, the capital a stair pair at the beam line: depth half a block, not a full one.
**Glyphs:** `|` per pier row (the wall column + chain) - the cheapest way to lift G2 on a brick wall; 3 depth planes per bay (G5).

## R8. Window with reveal + sill + head (three sizes)
**Found:** victorian-factory bay z 24..30 (dump x 3..8 y 2..13): big arched; train-station z 40 x 28..32 y 8..12:
medium; brickhouse3 / stonehouse3: small.
```
(a) FACTORY tall window, elevation in the plane x 6 (columns z 24..30), with the pier plane x 5 in front
y12  P  G  G  G  G  G  P      G = polished_granite: the gauged-brick HEAD band (one whole course of the other tone)
y11  P  T  K  K  K  T  P      T = polished_granite_stairs half=top facing in = the SPRINGERS; K = top pane row
y3-10 P  W  K  K  K  W  P     W = brick_wall[tall] = the JAMBS (thin, in the pane plane); K = glass_pane x3
y2   D  S  S  S  S  S  D      S = polished_granite_stairs facing=east half=TOP at x 5 = the SILL (1 proud of the panes)
```
Panes are 1 block behind the pier plane (x 6 vs x 5): the reveal is the pier itself. Jambs are brick_wall blocks
inside the opening, so the glass sits in a thin frame with air at its sides. Sill = upside-down stair on the plane in
front; head = two upside-down stairs as springers under a band of the other stone (an arch at this scale).
```
(b) STATION medium window (section z 40 columns x 28..32, y 8..13)
y13  I  K  I  L  L  L  I     K = brick_stairs facing=north half=top (string course over the head, R6)
y12  I  I  K  I  L  L  L     the head is a stair-top row stepped IN by one (a pointed/segmental head)
y9-11 I  K  I  L  L  L  I    L = glass_pane x3 tall 3; I = bricks   (K here = brick_stairs half=top jamb corbels)
y8   I  N  I  P  G  M  I     N/P/M = brick_stairs half=bottom (sill) ; G = stone_brick_stairs (centre sill cap)
```
```
(c) HOUSE small window (brickhouse3 section x 4 y 9..11): `J J H J J` glass_pane x2 at y 9..10 between
     light_gray_wool/white infill, torch each side at y 11, oak_trapdoor shutters (open) beside the ground windows.
```
**Vanilla recipe at our scale (bay 3 wide, storey 5):**
- Tall (hall bays, 1x3): sill y 1 `polished_granite_stairs half=top` facing out at the pier plane; panes y 2..4
  `glass_pane` 1 wide (or 2 wide in a 4-bay) 1 block back; jamb each side `brick_wall` in the pane plane; head y 5 =
  `polished_granite_stairs half=top` springers at the jambs + a `polished_granite` cell over the pane (the band).
- Medium (wings, 2x2): sill `brick_stairs half=bottom`, panes 2x2, head `brick_stairs half=top` x2 flush.
- Small (shed end, 1x1 round-headed): `glass_pane` in a 1-cell reveal under a `polished_granite_stairs half=top`.
- Behind every pane at dusk: `lantern` / `glowstone` 1 cell inside (the station lights every window).
**Rule:** sill proud (upside-down stair), pane recessed, jambs thin, head in the OTHER stone with springer stairs.
**Glyphs:** `g` + `|` (jambs) + `s` x3 per window; the recess is a 2nd/3rd depth plane in every bay.

## R9. Arched opening / receding portal
**Found:** chapel-of-brittany.schematic west portal, x 1..11 y 0..12 z 16..28 (plan slices y 2/5/7/9/10/11 and
sections x 3, x 4, z 22).
```
plan at y 2 (rows z 16..28, columns x 0..11; door at x 9..10)
z18  .  .  .  .  A  .  D  .  .  G  .  G     A = stone_bricks (outer order, x 4)  D = polished_diorite (2nd order, x 6)
z19  .  .  .  A  C  D  .  .  .  H  .  H     C = quartz_block (3rd order, x 4/5)  G = spruce_door  H = dark_oak_slab (door step)
z20  .  .  B  C  I  .  .  .  .  .  .  .     B = cobblestone   I = stone_brick_stairs facing=north half=top (jamb corbel)
z21  .  .  B  C  J  .  .  K  .  .  .  .     K = oak_trapdoor (door furniture)   L = oak_log (centre trumeau post)
z22  A  A  A  .  D  .  K  L  K  .  .  .     the opening steps IN by one block every 2 z as it goes deeper (x 2 -> 6)
```
Front elevation (section x 4, columns z 18..30): the arch head is a stepped semicircle of `quartz_block` rows
(y 9..11 at z 21..25, `stone_brick_stairs half=bottom` as the voussoir steps at y 9 each side), framed by a
`stone_bricks` outer order with `cobblestone` corner buttresses, the sill-level step a `polished_andesite` row (y 1).
Each receding order is a different material: cobblestone (outer, rough) -> stone_bricks -> quartz (inner, dressed)
-> polished_diorite -> the door. Trumeau `oak_log` centre post with the two `spruce_door` leaves either side.
**Vanilla recipe (door lane 5x8 at x 0..8 z 10..14; portal 5 wide, 5 tall on the west face):** three orders, each
1 block deeper and 1 block narrower: order 1 (x 0) `cobblestone`/`stone_bricks` 7 wide 6 tall with
`stone_brick_stairs` voussoir steps at the shoulders; order 2 (x 1) `stone_bricks` 5 wide 5 tall; order 3 (x 2)
`quartz_bricks` 3 wide 4 tall; the lane behind stays clear. A `polished_andesite` step row at y 0 across the whole
portal, `lantern` on `andesite_wall` posts each side at y 3, `dark_oak_trapdoor` leaves open against order 3.
**Rule:** a portal is nested orders, each one block deeper AND one narrower, each in a finer stone - the arch is read from the steps, not from a curve.
**Glyphs:** `s` (voussoirs), `=` (trapdoor leaves), `*`; three depth planes inside one opening (G5's best friend).

## R10. Corbelled tower stage
**Found:** gothic-castle.schematic, the SE tower at x 50..64 z 8..22, section x 55 y 38..62 and section z 8
y 38..62; the central tower stage at z 42 y 130..132 (window x 26..34).
```
section x 55 (columns z 2..22; the tower wall is at z 10 below y 46, z 8..10 above)
y48  .  .  .  .  H  K  B  G         K = cobblestone_stairs facing=east half=bottom (next stage's own base course)
y47  .  .  .  H  L  E  B  G  M      L = cobblestone_stairs facing=south half=bottom, E = cobblestone: the oversail
y46  .  .  .  .  E  B  B  G  C...   E = cobblestone at z 8: the wall is now 1 WIDER than the wall below
y45  .  .  .  .  O  B  B  C         O = cobblestone_stairs facing=south half=TOP at z 8 = the CORBEL (upside-down, under the oversail)
y44  .  .  .  .  .  B  B  C         (air under the corbel, wall below at z 9..10)
y43  .  .  .  .  H  P  B  C         P = cobblestone_stairs facing=west half=top = a second corbel 2 rows lower,
y42  .  .  .  .  .  Q  B  C         Q = cobblestone_wall  <- a thin wall column between the corbels (a bracket)
y41  .  .  .  .  .  L  B  D
```
```
section z 8 (elevation, columns x 4..30, y 38..47): the corbel table along the whole face
y47  .  .  D  D  D  D  D  D  D  D  D  D  D  E       D = cobblestone_stairs facing=south half=bottom (base course of the new stage)
y46  .  A  F  F  F  F  F  F  F  G  F  F  F  F  H    F = cobblestone (the oversailing course, 1 proud), G = mossy (one cell wet)
y45  .  .  F  B  .  B  .  B  .  B  .  B  .  B  F    B = cobblestone_stairs facing=south half=TOP EVERY OTHER CELL = the corbels,
y44  .  .  F  .  .  .  C  .  C  C  .  .  .  .  F    air between them (the shadow teeth)
```
Central tower (y 130..132): the stage top = `cobblestone` ring 1 proud, under it `cobblestone_stairs half=top`
at the corners and middle only, `torch` in the gaps, `dark_oak_fence` parapet row on top.
**Vanilla recipe (cone tiers y 11 / 15 / 19 / 23: the tier foot r0):** row r0-1 (the top row of the tier below) =
`polished_blackstone_brick_stairs half=top` facing out every other cell (corbels), air between; row r0 = the
tier's base course `red_nether_bricks` 1 proud (the oversail = the lip); row r0+1 = `red_terracotta_stairs
half=bottom` facing in (the skirt that turns the lip back into the tile plane). One mossy/wet cell per 6 in the lip.
**Rule:** the oversail is a full course 1 proud carried on upside-down stairs every other cell - the teeth of shadow between them are the ornament.
**Glyphs:** `s` x2 per cell of edge; the alternating air cells make the lip a 2-plane band (G5) on each tier.

## R11. Chimney with bands and cap
**Found:** victorian-factory stacks at x 10..14 z 2..6 (section z 4 y 28..57, dump y 28..31 and y 50..57); the shed
stacks at x 37..41 z 75..79 (campfire ring at y 49).
```
section z 4 (columns x 8..18, flue 3x3 at x 11..13, y 28..57)
y57  .  .  .  A  .  A       A = deepslate_brick_wall at the 4 corners + deepslate_bricks on the sides = the CROWN (thin corners)
y56  .  .  .  B  C  B       C = campfire lit=true INSIDE the flue top (smoke), B = nether_bricks
y55  .  .  .  B  D  B       D = smooth_stone under the campfire (the flue floor)
y50  .  .  .  E  .  E       E = iron band (sheet metal) one full ring  <- bands at y 29, 35, 50: every ~15 rows, and
y35  .  .  .  E  .  E          a band wherever the stack changes section
y31  .  .  W  B  B  W       W = nether_brick_wall at the corners: the stack is 3x3 solid-sided with THIN corners (an octagon read)
y30  .  F  B  B  B  G       F/G = smooth_stone_stairs half=bottom facing in = the FLASHING skirt where the stack leaves the roof
y29  E  E  E  E  E  E       the base band: a full iron ring 5x5, 1 proud
y28  H  B  B  B  B  H       H = nether_brick_wall corners of the 5x5 base (dark-brick plinth)
```
**Vanilla recipe (shed stack x 13..14 z 0..1 to y 11; hall / wing stacks):** plinth = `nether_bricks` 1 wider with
`nether_brick_wall` corners (2 rows); band = `waxed_cut_copper` (brass) full ring where it leaves the roof; skirt =
`smooth_stone_stairs half=bottom` facing in around that ring (flashing); shaft = `nether_bricks` with the 4 corner cells
`nether_brick_wall` (thin, so the stack reads octagonal); second band 4 rows up; soot = `blackstone` top 2 rows;
crown = `deepslate_brick_wall` corner posts + `deepslate_bricks` sides, `campfire lit` inside on a `smooth_stone`
floor one below the rim.
**Rule:** a stack is plinth - band - shaft - band - soot - crown, with thin corner walls all the way up so the shaft is not a box.
**Glyphs:** `|` on every corner row (4 per y), `*` (campfire), `s` skirt; a 5-block stack gives ~20 thin cells.

## R12. Copper dome / spire profile with ribs and brass bands
**Found:** copper-spire-a.schematic (a quarter model, axis at x 34 z 32; profile from the diamond_block shell radius
per y) and copper-spire-b.schematic (whole, axis x 11 z 9; half-widths per y).
```
spire B half-width per y (width = 2w+1):   spire A quarter radius (diagonal) per y:
y 1..5  w 5 (bulb, with w4 at y 2 and 6 = ribs)   y 0..3   r 18 -> 16 (base skirt)
y 7..11 w 3 (NECK: slab ring y 7, wall ring y 8,   y 5..14  r 15 (the big bulb, 10 rows nearly constant)
        buttons = rivets y 9..11)                  y 16..22 r 14 (the drum: oak stairs 15..17, spruce log 18..22 = a timber gallery)
y 12..16 w 4 (bulb 2)                              y 23..33 r 14 -> 13 (bulb 2, 11 rows)
y 17..21 w 2 (neck 2: slab, wall, buttons)         y 34..39 r 13 (a third drum: planks + lime glass = windows)
y 22..27 w 3 -> 2 (bulb 3, tapering)               y 40..52 r 0 (the needle: brass gold_block with cobblestone_wall collars every 2-3)
y 28..32 w 1 (the spike)                           y 53..66 needle tip
y 33..35 gold + oak_fence + torch (finial lantern)
```
Along the four cardinal meridians of spire A the copper shell is REPLACED by other blocks (oak_planks = a dark RIB,
gold_block = a brass band, lime_stained_glass = a dome light): the ribs are a different material down the meridian,
not a bump. Necks carry `smooth_stone_slab top` (a cornice ring), `cobblestone_wall` (a collar) and `stone_button`
(rivets) - three thin rows per neck. Finial: `gold_block` + `nether_brick_fence` post + `torch`.
**Vanilla recipe (cone tiers y 11 / 15 / 19 / 23 and the needle y 25):** treat each tier as bulb + neck: bulb = the
tier's steep face in `waxed_oxidized_cut_copper(_stairs)` with the 4 cardinal columns `waxed_weathered_cut_copper`
(the rib, one tone different); neck (the tier's top row) = `waxed_cut_copper_slab top` ring, then
`polished_blackstone_brick_wall` collar ring 1 narrower, `stone_button` rivets on it; needle = `waxed_cut_copper`
column with `andesite_wall` collars every 2 rows, `lightning_rod` tip, `lantern` just under it.
**Rule:** a dome is bulbs separated by necks of three thin rings; ribs are a second tone on the cardinal meridians.
**Glyphs:** `_ | ` rings and buttons per neck (3 thin rows per tier), `*` finial; the neck recess is a depth change on every tier.

## R13. Railing / balustrade / parapet
**Found:** train-station-1890.schem platform canopies (section x 30 y 7..13: `iron_bars[east,west]` rows at y 9 and
y 13 on `stone_brick_wall` posts) and the roof parapet at x 21..29 z 36..41 y 15..17; victorian-factory rooftop
catwalk at x 14..16 z 32..35 y 38..39 (railing both sides of a 1-wide iron grating along the roof).
```
station parapet (dump, elevation along z 40, y 14..17)
y17  W  .  .  .  W  .  .  .  W      W = stone_brick_wall[up=true] posts every 4th cell
y16  _  _  _  _  _  _  _  _  _      _ = stone_brick_slab bottom (the coping) with `slab type=double` at the posts
y15  B  S  S  S  B  S  S  S  B      B = bricks pier, S = brick_stairs half=top (the string under the coping)
y14  .  .  P  P  P  .  .  .  .      P = glass_pane / iron_bars panel under the coping between posts
```
Canopy balustrade: `iron_bars` run at y 13 between `stone_brick_wall` posts at 1 proud of the pier line; the
canopy lip above = `polished_andesite_slab top/bottom` pairs (a thin flat roof edge 2 rows).
**Vanilla recipe (hall ridge walkway, wing roof edge, the shed's south loading deck):** posts `andesite_wall` every
3-4 cells, `iron_bars` between, coping `smooth_stone_slab bottom` on top (walls get `up=true` so the posts show); a
roof catwalk = `iron_trapdoor` floor 1 wide with `iron_bars` both sides.
**Rule:** a railing is posts + panel + coping - the post is a wall block that shows above the coping.
**Glyphs:** `|` every cell (the densest thin-block source), `_` coping.

## R14. Half-timber frame + jetty
**Found:** stonehouse3.schematic section z 8 y 0..14 (x 0..13) and section x 6 (z 0..16); brickhouse3 section z 5.
```
stonehouse3 section z 8 (columns x 1..13; ground floor stone, first floor timber on a JETTY)
y13  .  .  A  D  D  B          the roof: stone_brick_stairs on spruce_log plates
y12  C  A  D  G  G  H ...      D = spruce_log posts; G = oak_planks (floor edge / lintel); H = bricks nogging
y9-11 .  .  D  G  L  L  G  H   L = glass_pane between posts; M = glowstone inside
y8   .  .  D  D  D  D  D  H    D row = the JETTY PLATE: a horizontal log course at the floor line (axis along the wall)
y7   .  .  .  D  .  P  D  G    the upper wall stands at x 2, the lower wall at x 3: upper storey oversails by 1
y4-6 .  .  .  E  .  .  E       E = oak_trapdoor = the lower storey's boarded face (a thin plane in front of stone)
y1-3 .  .  .  F  F  F  D       F = stone_bricks plinth storey; T cobblestone at the corners
y0   X  Y  Z  Z  Z  Z  Z       smooth_stone / gravel apron
```
Infill alternates: white_wool (lime render) in one bay, bricks in the next, glass_pane in the third; posts every
3-4 cells and at every corner; a diagonal brace is a stair (`oak_stairs` facing along the wall) in the bay corner.
**Vanilla recipe (wing gables, shed's timber monitor, the works yard sheds):** posts `stripped_spruce_log` (axis=y)
every 3 cells + corners; plate `spruce_log[axis=x/z]` row at each floor line; infill `white_terracotta` or
`mud_bricks` (render) with one bay in `bricks`; brace `spruce_stairs half=top` facing along the wall in the top corner
of each bay; jetty = the upper storey 1 proud on the plate row; `spruce_trapdoor` shutters beside windows.
**Rule:** the frame carries (posts and plates in one material), the infill is a different material and is set back; the floor line is a horizontal log.
**Glyphs:** `=` shutters, `g`, `s` braces; the infill set back half a block (trapdoor plane) gives a 2nd plane.

## R15. Wall tone variation (structured, never a chequer)
**Found:** victorian-factory bay dump x 5..6 z 23..30 y 2..13 and section z 23; train-station section z 40 (bricks
body / stone_brick trim); gothic-castle corbel table (one `mossy_cobblestone` cell per ~12 in the oversail).
```
factory: where the second and third tones go (a pier bay, 12 rows)
y12  polished_granite  <- the head band: a whole course in the other tone
y11  smooth_stone / brick_wall jambs
y9..10  pier face alternates smooth_stone (y odd) / polished_granite (y even) = banded RUSTICATION on the pier only
y3..8   bricks body, uniform; the pier keeps alternating
y2   smooth_sandstone sill course (pale) over
y0..1 sandstone-brick plinth, 1 proud, paler than everything above
y34 (hall top)  red_terracotta one course under the cornice (the dark "wet" line under the lip)
chimney / clerestory  nether_bricks (dark) - the parts that take soot or sit against the sky are the dark family
```
Weathering is placed: dark course under every lip (y 34), moss on 1 of ~12 corbel cells (gothic), mossy wall
blocks on the north faces (steampunk uses mossy_cobblestone_wall for the shaded pipes), vines on the piers' shadow
side (factory x 3..4 leaves and `cave_vines` on the lamp side only).
**Vanilla recipe:** body `bricks` 80 %; T1 `red_terracotta` only as (a) the course under each cornice lip, (b) the
sill course; T2 `polished_granite` only as bands on piers (every 2nd row) and the head course over windows; plinth
`polished_andesite` 2 rows 1 proud; dark family `nether_bricks` for stacks and the monitor; moss/vine on the
NORTH/east (away-from-camera) faces and the shadow side of each pier, 1 cell in 10, never on the camera face's centre.
**Rule:** the second tone goes where the structure changes (bands, heads, sills, under lips), the weathering where water and shade are - never scattered.
**Glyphs:** none directly; it is what makes G3 (no block > 25 %) hold without a chequer.

## R16. Machinery greeble (pipes, valves, gears, gantries)
**Found:** steampunk-factory.schematic: plan y 20 (the yard x 6..57 z 33..57: pipe runs `cobblestone_wall` rows,
`iron_bars` ladders at x 43 z 34..46, `lever` valves, `anvil` machines); section x 46 z 22..32 y 11..32 (a vertical
pipe on wall brackets); section z 27 x 40..56 y 9..24 (a curved pipe arc in black_wool = stand-in). victorian-factory:
`chain` + `lantern` lamp brackets on every pier, `lightning_rod` finials, iron girder beams, rooftop catwalk.
```
steampunk vertical pipe (section x 46, columns z 22..32, y 11..32)
y25..31  .  .  .  .  .  .  .  .  .  .  C/B   C = chiseled_stone_bricks column = a 1-wide stack above the boiler
y24      .  .  .  .  D  E  D  .  .  .  .     D = cobblestone_wall (the pipe), E = black_wool (elbow)
y22      .  .  .  F  G  G  G  G  G  G  G     G = iron_block run = the main horizontal pipe (full blocks), F = stone_button (a valve wheel)
y20..21  .  .  .  .  D  .  D  .  .  .  .     two wall columns = twin vertical pipes dropping from the main
y17..18  .  .  .  .  .  E  .  .  .  .  I/H   E = elbow; H/I slab + stone = a wall bracket under the pipe
y12..14  .  .  .  .  .  .  .  .  .  .  C/J   the pipe lands on a smooth_stone shoe
```
The yard plan: pipe runs are `cobblestone_wall` rows along walls (mossy on the shaded side), `lever` on the wall
beside a pipe = a valve, `iron_bars` x2 stacked = a ladder/grating, `anvil` pairs = machines, `hopper` = a funnel,
`cauldron` = a tank, `iron_block` 2x2 = a boiler, `stone_button` on pipes = bolts, `iron_ore` = rust patches.
Factory: `chain[axis=y]` 2-3 long under a `stripped_spruce_log` beam = a hoist; `lightning_rod` = a finial / vent pipe.
**Vanilla recipe (shed and yard, x 10..17 and the north yard):** pipe = `polished_blackstone_wall` run (vertical
`chain` for thin pipes); elbow = `polished_blackstone` full; valve = `lever` on the wall next to a pipe + a
`stone_button` wheel; tank = `iron_block` 2x2 with `waxed_cut_copper` band, a `cauldron` beside it; gear = `iron_bars`
pair in a `smooth_stone_slab` frame; hoist = `stripped_spruce_log` beam + 2 `chain` + `anvil`; vent = `lightning_rod`
on a `cut_copper_slab`; rust = `waxed_exposed_copper` cells on the tank's lower half (not iron_ore - it reads as terrain).
**Rule:** every greeble is a run + a joint + a fitting (pipe, elbow, valve); one object alone reads as litter.
**Glyphs:** `|` (walls, chains, bars) and `=`; the greeble band is how an industrial building reaches 14 % thin blocks.

---------------------------------------------------------------------------------------------------------------------
## Top 8 recipes for the Engine Shed r9 (tied to the frozen massing)
Massing: plot 17 x 25; halls z 3..7 / 17..21 eave y 7 ridge y 9-10; cone tiers y 11 / 15 / 19 / 23 (outline 11 15 19
23 25 23 19 15 11 over z 8..16), needle y 25; swept wings z 0..2 / 22..24; shed x 11..16 ridge y 6 eave y 5; door lane
x 0..8 z 10..14 y 1..8 clear; camera sees the west face (x 0), the south face (z 24) and the roofs.

1. **R1 + R2 eave band on every hall / shed / wing eave** (hall eave y 7, shed eave y 5 at x 10 / x 17, wing eave at
   the hall side y 8): pale `smooth_stone_slab top` lip 1 proud at the wall top, `<roof>_stairs half=top` soffit cell
   inside the first course, `red_terracotta` course under the lip, copper gutter stair on the lip. Fixes G1 and the
   "roof cuts straight to the wall" fault in one part; cheapest biggest win.
2. **R7 pilaster strip + capital on every hall pier** (west face x 0, south face z 24; piers at every 3rd z / x):
   `andesite_wall` column in front of the pier, stair-pair capital at y 5, `chain + lantern` on every 2nd pier. Gives
   the west face its 3 planes (G5) and ~1 thin cell per pier row (G2).
3. **R8 tall windows in every hall bay** (panes y 2..4 one block back, `brick_wall` jambs, granite springer head y 5,
   upside-down-stair sill y 1; lantern behind at dusk): turns the blind arcade into a Victorian shed; 3 glyph classes
   per bay.
4. **R10 corbel table at each cone tier foot** (y 10, 14, 18, 22: upside-down stairs every other cell under a
   1-proud base course, stair skirt above): the lip-per-tier that ref18 / ref51 / ref55 all have; kills the ziggurat
   read without touching the outline.
5. **R12 bulb + neck on the cone and needle** (each tier = copper face with weathered-copper ribs on the cardinal
   columns; tier top row = brass slab ring + blackstone wall collar + buttons; needle collars every 2 rows): 3 thin
   rows per tier = the tower's whole G2 share.
6. **R5 shed roof lights + hall monitor** (shed: `glass_pane` slope cells at x 12 / 15 for z 6..8 and 16..18 between
   blackstone wall posts, lantern under each; halls: a 2-row glazed monitor at the ridge y 9..10 with nether-brick
   posts and stair sill/head): the shed (40 % of the view) stops being a grey lid.
7. **R11 banded stack** (x 13..14 z 0..1 to y 11: plinth with wall corners, brass band + stair flashing at the roof,
   thin corner walls up the shaft, soot rows, deepslate-wall crown, campfire inside): the one vertical that says
   "works".
8. **R9 receding portal on the door lane** (west face x 0..2 z 9..15: three orders cobblestone -> stone_bricks ->
   quartz_bricks, each 1 deeper and 1 narrower, voussoir stairs, step row, lantern posts, open trapdoor leaves; lane
   stays clear): ref67's threshold; three depth planes in the camera's own face.
Runners-up: R13 railing along the hall ridge walkway and the shed's south deck (dense `|`); R4 dormer for the cone eye
(y 14..16) and one per hall; R15 tone placement is applied inside every part above, not as its own part; R16 greeble
run on the shed's north yard and the pipe from shed to hall.

## Surprises worth carrying into the generator
- The factory's hall roof has no roof overhang; the eave shadow is a cornice slab + an upside-down stair soffit.
  Re-measured: of the study tool's ~148 overhang cells, 76 are `polished_cut_tuff_slab` and 60 pale tuff stairs (the
  cornice LIPS and parapet copings), only 6 are copper roof stairs (the porch roof). So G1 is met in the best build by
  the trim lip, not by the roof plane - the generator should count the lip as the eave.
- Pilasters, jambs, chimney corners and railing posts are all THIN WALL blocks (`*_wall`) standing in front of or
  inside full blocks: half-block depth is what the best builders use for relief, not full-block steps.
- The glazed roofs are FULL glass blocks in a stepped quarter-circle (1-1-2 steps), with panes only at the edges;
  panes alone in a roof read as holes.
- Dome ribs are a different material down the cardinal meridians (planks / brass / glass in the spire), never a bump.
- The gothic corbel table alternates stair / air cell by cell: the air teeth are the ornament.
- Every pier in the factory carries a hanging lantern on a chain; every station window has a light behind it -
  "life" is per bay, not per building.
