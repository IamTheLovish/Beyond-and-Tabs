# Engine Shed detail grammar - architecture study (Oct 28, read-only)

Studied: ref18 (230 x 180, blown up 5x / nearest 7x), ref12 / ref44 / ref11 for roof technique, r7 cell by cell
(`detail()` dumped as x- and z-sections), `tools/lab_vehicle_brass.py`, `brass_lab_parts.py`, the checklist, the
frozen tables in `tools/test_lab_vehicle_brass.py`. Axes as the code: x = depth (0 = west front), z = across, y = up.

## 1. Diagnosis - what ref18 and professionals do at each scale that r7 does not

Architects design in a hierarchy: **mass > bay > element > ornament**, and every edge where two surfaces meet gets a
*moulding* (a drip, a cap, a lip) that throws a shadow line. r7 has the mass and the value and almost nothing else:

| # | Scale | ref18 / professional practice | r7 (measured in the section dump) |
|---|---|---|---|
| 1 | Roof plane | A **pitched tile plane**: rows of courses running down the slope, each row a shadow line | The hall "roof" is two **slab terraces** (y 8 slabs at x 0..3 and 7..8, full cells x 4..6) - a flat lid, no slope, no course lines |
| 2 | Eave | Overhang + **drip + soffit shadow** + gutter; the brightest line (ref18's lit string course) sits *under* a dark eave shadow | Eave = a candle and a bottom slab floating over the quartz cornice; no overhang shadow, no drip |
| 3 | Ridge / verge | A **ridge cap** (darker roll), hips capped, gables closed by a **bargeboard** and a finial (ref12 trims every roof edge) | Ridge = granite slab / dark block / granite slab: a stripe, not a cap; wing gables beaded "pale / red" on a flat rake |
| 4 | Wing roof form | ref18's pavilions are **steep pyramids** that flare: they slope in BOTH directions, the outer tier lips pale | Wing roof slopes only along z; across x it is flat (z-section x 3: a flat top row per z). A terrace, not a roof |
| 5 | Dormers | A dormer per bay - the hall roof is a GRID of red panes in pale frames | None; the hall roof has one grey rib (z 5) |
| 6 | Tower stages | Each setback is a **ring**: corbel / cornice / frieze, pilaster strips at the arrises, one motif per stage, pinnacles at every setback | Each 4-row tier is a flat red face: a mangrove stair step row + 3 plain rows; one eye; no corbel, no frieze, no pinnacle |
| 7 | Depth planes | ref44: 3-4 planes per face at every scale (pier > frame > reveal > back); ref39: column = base + shaft + capital | Front has 2 planes (pier at x 0, bay back at x 2); tower faces 1 plane per tier; roofs 1 plane |
| 8 | Ornament discipline | Ornament sits at the *joints* (cornice, capital, keystone, finial), never in the field | Granite "ticks" in the field; the emblem as a flat painted cross |

The root cause is in the generator: `pitched()` / `swept_half()` are voxelized with `shape_kit`'s slab edge pass, so a
2.5-row rise over 4.5 run becomes terraces with slab lids; `tower()` paints the cone's faces instead of building each
tier as a stage; nothing in `brass_lab_parts.py` is a *roof* or *cornice* part. Detail was done by block swaps on
one plane, which is "wallpaper" (the critic's speckle metric catches scatter but not flatness).

## 2. The roof tiling system (a part: `roof_kit` in brass_lab_parts style)

One parametric recipe, five elements: **course, eave, ridge, verge, dormer**, plus finials. Two implementations of the
course: VANILLA (preview tool draws it) and MACAW (true tile texture; `mcwroofs` has been a mandatory dependency since
Oct 20, so no new mod is needed). Confirm ids against CI's `[Mod blocks]` notice; the repo already places
`mcwroofs:deepslate_steep_roof` / `deepslate_top_roof` (`part="switched_90"`), so `<material>_{roof, lower_roof,
upper_lower_roof, steep_roof, upper_steep_roof, top_roof, attic_roof}` with `facing` is the pattern; terracotta
colours exist (`red_terracotta_*`), and rain gutters (`mcwroofs:gutter_middle`, `gutter_base`, dyed variants).

Legend: `W` wall red terracotta, `R` red terracotta (roof fill), `r<` mangrove stair facing west (low side out),
`r>` facing east, `d^` dark upside-down stair (polished blackstone brick, half=top), `d_` dark bottom slab, `D` dark full,
`B` brass (waxed cut copper), `b_` brass slab, `Q^` quartz upside-down stair, `G` gutter (`mcwroofs:gutter_middle`
or `d^`), `L` lantern, `*` candle, `t` deepslate tiles, `t<` deepslate tile stair, `.` air,
`l<`/`u<` Macaw red_terracotta lower_roof / upper_lower_roof facing west, `T` red_terracotta_top_roof.

### 2a. Hall roof (R4) cross-section, any z in 3..7 / 17..21, x 0..10, eave y 7, ridge y 9 (tops stay 9)

```
VANILLA 1:2 course pattern                     MACAW true 1:2 plane
      x0  x1  x2  x3  x4  x5  x6  x7  x8  x9         x0  x1  x2  x3  x4  x5  x6  x7  x8  x9
y9    .   .   .   .   r<  D   r>  .   .   .          .   .   .   .   .   T   .   .   .   .
y8    .   .   .   r<  R   R   R   r>  .   .          .   .   .   l<  u<  R   u>  l>  .   .
y7    G   r<  R   R   R   R   R   R   r>  G          G   l<  u<  R   R   R   R   u>  l>  G
y6    Q^  W   W   W   W   W   W   W   W   W          Q^  W   W   W   W   W   W   W   W   W
```
Rules of the course: every tier front is a **stair, low side out** (its riser is the course's shadow line); the tread
cell behind it is a *darker* red (`mangrove_planks` or `red_nether_bricks`, the ref12 two-tone row) so the plane reads
as rows from the RTS camera; alternation runs **along the slope only**, never across z (across = checkerboard).
Eave: the gutter `G` at x 0 y 7 hangs 1 proud of the wall (an upside-down dark stair if no Macaw: its underside is the
soffit shadow over the quartz cornice, the brightest line now sits *under* a dark drip, as in ref18). Ridge: `r< D r>`
is a 3-wide cap whose centre is dark, with ONE brass boss `B` in place of `D` at each ridge end (z 3 and z 7, where
the ridge meets wing and cone) - nothing may sit at y 10 (frozen tops); with Macaw a single `T` top piece per z and
the same two bosses. Every other z column (z 3, 5, 7 - the piers) stays a **rib**:
polished deepslate stairs in the same shapes, so the grid of ref18's "red panes in frames" appears at 1-block
resolution without speckle (rib / 1 red / rib / 1 red / rib).

### 2b. The dormer (one per hall, z 4..6, face x 0; max y 9 keeps the outline)

```
front (x 0), z 4 5 6         section at z 5, x 0..3
y9   .   Q^  .               y9  Q^  r<  .  .      Q^ = quartz upside-down stair facing west: the hood
y8   D   L   D               y8  L   R   r< .      D  = polished blackstone brick WALL (cheeks), L = lit pane/lantern
y7   *   D   *               y7  D   R   R  R      sill row: the bay candles stay at z 4 / 6, dark sill at z 5
```
Light comes from the dormer window, not from a roof lamp (ref15 / ref43 rule); the hood is the only pale cell.

### 2c. Wing roofs (R5): steep swept hip, gable at the outer end, x-section at z 0 / z 1 / z 2 (tops 14 / 12 / 10)

```
z 0 (top 14)                          z 1 (top 12)                       z 2 (top 10)
      x0 x1 x2 x3 x4 x5 x6 x7 x8 x9        x0 x1 x2 x3 x4 x5 x6 x7 x8 x9     x0 x1 x2 x3 x4 x5 x6 x7 x8 x9
y14   .  .  .  r< B  r> .  .  .  .         .  .  .  .  .  .  .  .  .  .      .  .  .  .  .  .  .  .  .  .
y13   .  .  .  R  D  R  .  .  .  .         .  .  .  .  .  .  .  .  .  .      .  .  .  .  .  .  .  .  .  .
y12   .  .  r< R  R  R  r> .  .  .         .  .  r< D  D  D  r> .  .  .      .  .  .  .  .  .  .  .  .  .
y11   .  .  R  R  R  R  R  .  .  .         .  .  R  R  R  R  R  .  .  .      .  .  .  .  .  .  .  .  .  .
y10   .  r< R  R  R  R  R  r> .  .         .  r< R  R  R  R  R  r> .  .      .  r< D  D  D  D  D  r> .  .
y9    .  R  R  R  R  R  R  R  .  .         .  R  R  R  R  R  R  R  .  .      .  R  R  R  R  R  R  R  .  .
y8    G  r< R  R  R  R  R  r> G  .         G  r< R  R  R  R  R  r> G  .      G  r< R  R  R  R  R  r> G  .
y7    .  W  W  W  W  W  W  W  W  W         (wing walls x 1..8 to y 7, hall wall x 9 to y 6, lintel y 6 as now)
```
A 2:1 pitch (stair over full block per column; Macaw: `steep_roof` + `upper_steep_roof`), eave gutters at x 0 y 8 (hung
1 proud of the wall) and x 8 y 8 (flush on the back wall; the hall roof's slab at x 9 y 7 lies below it), the crest line falling 14 / 12 / 10 toward the hall
= the sweep, now a real hip that the RTS
camera reads as a roof. The dark `D` row under each crest is the **ridge cap** of that z column (hips capped), the
brass `B` at (4, 14, 0) the finial over a `d^` post. The outer gable (z 0 / z 24 face) keeps its emblem and gets a
**bargeboard**: the rake's cells are dark stairs (low side out, following the slope) with ONE quartz cell at each
change of pitch (y 10, 12, 14) - a beaded dark rake, never a pale column (r5 lesson).

### 2d. The shed gable (R9, x 11..16, ridge y 6) - 40 % of the RTS view

```
x-section, z 2..22:   x10 x11 x12 x13 x14 x15 x16
y6                     .   .   t<  d_  d_  t>  .     ridge roll: dark slabs (polished blackstone brick) replace r6's
y5                     G   t<  t   t   t   t   t>    pale deepslate slabs; eave gutter at x 10 hung off the wall
y4                     .   D   D   D   D   D   D     (west only: x 17 is outside the plot); wall top course as now
```
The roof is 6 deep, so its courses run along z: r6's rib rows (z % 3 == 0, deepslate bricks) now carry a `d^` drip at
the eave cell (11, 5, z), a dark wall post on the ridge, and at z 10 / 14 a **vent**: a lantern at (13, 5, z) under an
iron-bar grille at (13, 6, z) with a brass slab cap - light and "smoke" from the works, the shed read as bays from
above instead of a grey slab.

### 2e. The cone (R7 / R8): tiers as stages, outline untouched

Each 4-row tier = **corbel row + 3 face rows**. The step row (y 11, 15, 19, 23, mangrove stairs now) stays the tile
lip; the row *under* it (y 10, 14, 18) becomes a dashed **corbel course**: `D` at the arrises and at z 12 (the centre),
red between (dashed, so no full-width dark band - the checklist rule holds). On each face: arris strips dark (kept),
the field red, ONE motif per tier at z 12 (y 9 keystone-lantern, y 14..16 the gauge dial, y 20 a brass vent slot), a
`d^` pinnacle stub at the 4 setback corners of tiers 1-2 (y 12 and y 16: a dark wall post + brass slab, ref40 /
ref44) - the outline test counts tops per z column, so stubs must sit *inside* the next tier's footprint, never on
the outline's edge cells.

## 3. Facade articulation grammar at 17 x 25

**Three scales, three budgets.**
1. **Mass (frozen):** cone, halls, wings, shed, stack. Not touched.
2. **Bay (the rhythm):** the front is 25 columns = `3 | 5 | 9 | 5 | 3`. Inside a hall, the bay pitch is 2 (pier /
   bay / pier...), so every vertical line on the front sits on an odd z: piers z 3 5 7, roof ribs z 3 5 7, dormer
   centre z 5, wing crest x 4. **Every vertical element shares one of those lines**; nothing starts on an even z.
   Horizontals: plinth y 0-1, impost y 5, cornice y 6, eave y 7-8, ridge y 9, wing crests 10 / 12 / 14, cone corbels
   10 / 14 / 18 / 22. A course every 3-4 rows (ref44 halved).
3. **Element and ornament:** each element is `base + body + cap` (pier = andesite base, dark shaft, andesite capital;
   dormer = sill, window, hood; tier = corbel, face, lip). Ornament only at caps and joints: keystone, finial, dial,
   rosette, the gable emblem - at most ONE per element.

**Depth planes: 3 per face, each 1 block.** Plane 0 = projecting (piers, gutters, cornices, bargeboards); plane 1 =
the field (wall, roof plane); plane 2 = recess (bay backs, dormer window, the door). The facade_audit "planes >= 2"
rule becomes "planes = 3 on every camera face, and plane 0 covers 15-30 %" - a field with nothing proud of it is
wallpaper, a field more than 30 % proud is a cliff.

```
plan of one arcade bay (x 0..2, z 3..5), looking down:       the same bay in elevation:
 z3   P  .  .     P = pier plane 0 (x 0)                      y6  Q^ Q^ Q^   cornice, proud
 z4   .  h  k     h = head stair plane 1 (x 1, y 4)           y5  P  i  P    impost course
 z5   P  .  .     k = red panel + calcite eye, plane 2 (x 2)  y1-4 P  k  P   recess, 2 deep
```
Translating ref18's density without speckle: ref18 has ~12 bays a side; we have 2. So we do not shrink its pattern -
we keep its **pattern-per-bay** (pane in frame, dormer over bay, rib over pier) at full block size and accept fewer
repeats. Texture variety goes *inside a role along a course* (terracotta field, mangrove tread, nether-brick shadow
row), never cell-random.

## 4. Making it ours

**Keep from ref18 (the essence):** one cone on a long arcade hall; red field in dark frames with pale lines only at
the joints; the lit string course under the eave; dormer rows; the glowing door; the symmetry.

**Brassworks identity (5 signature details, each at a joint, brass <= 3 % of the front):**
1. **Cast-iron rainwater goods:** `mcwroofs:gutter_middle` along every eave, a `gutter_base` at the buttress
   corners feeding a **downpipe** (a vertical `chain` or stacked `lightning_rod` - the unwaxed-copper check already
   exempts it) down the dark buttress at z 7 / 17 to a tuff shoe. This is the single most "engine shed" line.
2. **Brass ridge roll + steam vents:** a `waxed_cut_copper_slab` on each ridge cap; the shed ridge carries two louvred
   vents (iron bars over a lit cell, 1 x 2) - smoke and light from the works, not lamps on roofs.
3. **The gauge dial:** the R7 eye becomes a brass-ringed gauge (ring = 4 `waxed_cut_copper` cells, red heart, a dark
   needle cell) - ref18's clock in our material.
4. **Rivet courses:** `polished_blackstone_button`s on the dark pier shafts at y 2 and y 4 and on the gate reveal
   pilasters (vertical rows of 2) - rivets at 1-block resolution are buttons, and they add plane 0 at zero cost.
5. **The stack as the second landmark:** keep the corbelled cap and brass band, add a `decorative_blocks:brazier`
   (lit) or campfire on top and TWO hoop bands (`waxed_cut_copper_slab` ring) - the pagoda-finial reading of ref42.
Supporting vocabulary already licensed and installed: `decorative_blocks:<wood>_support` (MIT) as the eave bracket
course under gutters (ref42's bracket tier), `decorative_blocks:bar_panel` for the belfry / vent grilles,
`mcwwindows:blackstone_brick_gothic` (already used) for the dormer light, Chipped terracotta variants for the tread
row. No further mod needed; Macaw's Roofs (ARR art, already pinned) is the roof texture source - its blocks are
referenced by id only, never copied.

## 5. Next Engine Shed round - ranked moves (frozen: cone outline 11 15 19 23 25 23 19 15 11 over z 8..16; lane
x 0..8 z 10..14 y 1..8 air; spawn column (8, *, 12) air; plot 17 x 25 x 26; FRONT_T1 column tops)

1. **Hall roofs as 2a** (z 3..7, 17..21, x 0..9, y 7..9): stair-fronted courses, dark tread rows, gutter at x 0 y 7,
   `r< D r>` ridge with brass slab, deepslate ribs on z 3 / 5 / 7. Biggest visible change from both cameras.
2. **Wing roofs as 2c** (z 0..2 / 22..24, x 0..8, y 8..14): the hip across x with the crest falling 14 / 12 / 10;
   gutters, dark hip caps, brass finial at (4, 14, 0) / (4, 14, 24); the z 0 / z 24 rake as a dark beaded bargeboard.
   `wing_roofs()` is rewritten, `swept_half()` is no longer voxelized for these cells.
3. **Dormer 2b** at z 4..6 and z 18..20, x 0..1, y 7..9 (one light each; candles kept at z 4 / 6 / 18 / 20).
4. **Cone corbel courses + pinnacle stubs** (2e): rows y 10 / 14 / 18 dashed dark at arrises and z 12; stubs at the
   tier-1 and tier-2 setback corners inside the footprint; the gauge ring at (1, 14..16, 11..13) in brass.
5. **Rainwater goods and rivets** (4.1, 4.4): gutters on every eave, downpipes at z 7 / 17, buttons on piers.
6. **Shed ridge** (2d): brass slab roll on x 13..14 y 6, dark posts and vents at z 10 / 14, `d^` drips at every rib's
   eave cell (x 11 / 16, y 4).
7. Last, and only if the audit still shows plane 0 under 15 %: `decorative_blocks:mangrove_support` brackets under the
   hall gutter at the piers (x 0, y 6 is the cornice - brackets go at (0, 7, odd z) only where no candle sits).
Do not touch: `tower()`'s outline logic, `door()`, the lane far wall, `gables()`' emblem, the stack's height.
Render: `--px 32` front sheet AND the RTS corner AND a 2x close crop of one hall roof bay; `preview_structure.py` needs
a colour hint for `mcwroofs:*` ids (draw as the base material's stair) or the critic sees placeholder tiles.

## 6. Rules for the art-director skill (testable)

1. **Every roof has four edges and all four are detailed:** an eave with a drip 1 proud, a ridge cap of a different
   value, verges with a bargeboard, hips capped. Test: dump each roof's x-section; a top row of slabs = fail.
2. **A roof is a slope, never a terrace:** between eave and ridge no run of 3+ adjacent columns shares a top (a 1:2
   pitch pairs them; a slab lid fails), and every tier front is a stair, never a slab.
3. **Three planes per camera face, plane 0 = 15-30 % of cells** (extend facade_audit with a plane-0 share).
4. **A course line every 3-4 rows on every face taller than 4** (cornice, string, corbel, impost); list them with y.
5. **Every element = base + body + cap;** an element missing one is unfinished. Ornament only at caps and joints,
   one per element, none in a field.
6. **Vertical elements sit on the bay grid** (odd z in the halls); anything off-grid is a bug, not variety.
7. **Variety runs along courses, not across cells:** two tones per roof plane, alternating per course; speckle stays
   under the audit's limit.
8. **Judge from both cameras before the sheet:** the RTS corner at 32 px, a 2x crop of one bay, and the squint; a
   roof that reads as one flat colour from above fails regardless of the front sheet.

## Sources
- Facade articulation intervals and "at least one detail from each of three categories" per interval, cornice as the
  identifiable top: Anacortes Municipal Code 19.63.050, https://anacortes.municipal.codes/AMC/19.63.050
- Roof terms (eaves flush vs projecting, verge, ridge, hip, valley, ridge tile): Marley, "The different parts of a
  roof explained", https://cm.marley.co.uk/support/essentials/the-different-parts-of-a-roof-explained
- Minecraft roof technique (stairs for pitch, one-block overhang, layered stair/slab, trapdoor trim, dark trim on
  edges, ridge slabs / upside-down stairs): https://www.exitlag.com/blog/minecraft-roof-designs/
- Macaw's Roofs 2.3.x (roofs, steep, lower, top, gutters, awnings): https://modrinth.com/mod/macaws-roofs and
  https://www.curseforge.com/minecraft/mc-mods/macaws-roofs/files/5078226 ; building tutorial (image):
  https://github.com/sketchmacaw/MacawsModsIssues/wiki/Roofs-Building-Tutorial-Guide
- Repo: `design/refs/vehicle_labs/README.md` (ref12, ref17, ref42, ref44 lessons), `design/vehicle_labs/study_part_refs.md`,
  commit 031fd93d (the six new mods and their licences), `bar/build.gradle` (Macaw's Roofs pinned since Oct 20).
