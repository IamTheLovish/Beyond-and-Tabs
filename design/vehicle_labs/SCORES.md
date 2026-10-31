# Vehicle lab concepts - fresh-critic scores per round

Target: 9/10 from a critic that has never seen the work ("keep looping until it's amazing", lovish Oct 28 02:15),
judged against lovish's references (`design/refs/vehicle_labs/`, 28 images) and the White Angel capitol v4.

| Lab | Faction | Round | Score | Biggest faults (critic) |
|---|---|---|---|---|
| Sunwain Court | Sunforged | 1 | 4/10 | no single landmark (dome boxed in, needles compete); weak gate; no bay rhythm on camera faces; roof blob reads as debris; materials mixed not zoned; messy ground, dead windows |
| Charnel Works | Gravebound | 1 | 4/10 | five equal spikes, no landmark; gate not the biggest opening, floating bone bits; faces are noise; scattered cyan dots + stray lava; extra materials; flat messy roof. KEEP: the L3 bone rib-vault, the bone gate posts |
| Careening Hard | Tidewrought | 1 | 4/10 | no readable west gate; three competing landmarks; noisy ground storey; flat roof slabs; scattered lamps; nothing says shipyard. KEEP: lighthouse cap, copper-over-timber roofs |
| Engine Shed | Brassworks | 1 | 3/10 | no readable gate; two landmarks fighting (tower vs stack); no calm wall; glazed roof reads as scaffolding; clock a grey slab; scattered light; L1 taller than the capitol. KEEP: SE furnace pier, flared verdigris cap |
| Rooting Hollow | Verdant | 1 | 3/10 | no real tree (thin column, no twist/flare/bark); flat canopy slab; gate unreadable; scattered lime lights; stone faces without order; roof clutter. KEEP: galleries under an overgrown roof, mossy south-face palette |
| Thunder Arsenal | Stormcallers | 1 | 4/10 | tower only a bump, armillary floats; no yard (all roofed); gate unreadable; flat roof language; gold/copper noise; scattered light. KEEP: red colonnade with white panels, mossy plinth, palette |
| Ember Pens | Ashborn | 1 | 3/10 | no landmark (giant a small lump); gate unreadable; a cube, not a stepped temple; glow scattered everywhere; no material zoning; no calm ground |

## Engine Shed pilot (Brassworks, imitating ref18 only, T1 13x21 <= 22)
| Round | Critic | Resemblance | Quality | Main faults |
|---|---|---|---|---|
| detail r1 (50c09ed2) | fresh Fable, Oct 28 | 4 | 5 | silhouette worse than greybox r3 (outward stair flares, white lantern box + pole); ~40% grey stone vs ref18's red/black/cream; orange-glass door slot dominates; patchwork wing roofs; arcade rhythm too coarse. Keep: the cream hall eave cornice. |
| detail r2 (fa66b589) | fresh Fable, Oct 28 | 4 | 4 | spire rebuilt as 7/5/3 boxes with quartz "ears" (worse than r3's taper); blackstone renders grey so the corner view reads as a grey box; 1x1 cyan light squares; wing ramps with no overhang; flat door head. Keep: tower-over-door massing between arcade halls and raised end pavilions. |
| detail r3 (0527872b) | fresh Fable, Oct 28 | 4 | 4 | one dark-red lump: no roof/wall material split, blank tower slab, unframed door void, wing roofs read as stepped brick, 1-wide needle reads as an antenna, blank long side walls. Keep: ground-floor arcade (cream cornice, pilasters, dark recesses). Art director: three critics asked for three colour schemes; round 4 gets a fixed material map read from ref18 itself (red panels in a grey lattice). |
| detail r4 (4a70ec67) | fresh Fable, Oct 28 | 4 | 5 | spire rebuilt as 7/5/3 boxes again (2nd time: the "within 1 block of r3" check lets boxes pass); cobble reads as rubble, not a smooth lattice; red terracotta reads brown and too little of it; black door void; cobble dormers. Keep: tiny lights on the pale ledge over the arcade, tower-base buttresses. |
| detail r5 (d2f002e6) | fresh Fable, Oct 28 | 3 | 5 | squint: stubby stepped chimney + black door hole vs ref18's red-black mass with a needle spire; grey lattice dominates (asks for red-dominant with black frames - the opposite of r3's critic); bays too wide; flat roof. Keep: the pale diamond "eye" on the tower. Ceiling at 13x21x22 estimated 6-7. LOOP STOPPED here (see LESSONS, "Pilot rounds 1-5"). |

## Engine Shed T1 (Brassworks, the T1 lab plot 17x25, h <= 26; scored against design/vehicle_labs/briefs/brass_t1_checklist.md)
| Round | Critic | Resemblance | Quality | Main faults | Keep (frozen next round) |
|---|---|---|---|---|---|
| detail r1 (20fdda0) | fresh Fable, Oct 28 | 5 | 5 | the 7x10 door reads as a black hole (lane 93 % dark, luma 20); arcade storey a pale/grey checkerboard (speckle 58 %); tower upper reads as stripes, not a lattice; gable emblems dark-in-dark; shed roof flat; front 27 / 49 / 15 / 9 vs ref18 39 / 41 / 19 / 1 (too dark, short on red). Facade audit 13/13 (greybox 0/13). | R4 hall roofs (67/24/8 target met, ribs over piers); R3 lintel + candles (brightest line); R7 pale cross eye; brass finials and tip; the shed's lit south slot |
| detail r2 (6ab248a7) | fresh Fable, Oct 28 | 5 | 5 | rubric 38 / 54. Measured gains (door lane dark 93 -> 55 %, arcade speckle 58 -> 19 %, front 35 / 37 / 19 / 8 vs 39 / 41 / 19 / 1) did not move the score. Squint: ref18's tower is ONE continuous mid-value cone from hall roof to tip with its brightest spot at the door; ours is a dark door block under a stepped spire. Pale too strong (lintel 2 rows, tower y 12 chevron; pale 8 vs 1). R8 reads as a solid red column, not a panel grid. Gable speckle 38 %. (Its fix 2 put a lantern at x 2 z 12 y 10 - inside the exit lane - rejected.) | R4 roof tiers; R7 eye with black arrises; the south-gable emblem + rake; the lintel as the brightest line, thinner |
| re-mass r3 (3c90e0e8) | builder squint only, no critic | - | - | still a block: the 7x10 lane took 7 of the cone's 9 foot columns, so the base read as a 9-wide rectangle to y 11 with a grey gravestone door under a stepped spire. Coordinator cut the lane to 5x8 (r4). | cone outline 11 15 19 23 25; arrises dark; no pale lid |
| tower r4 (ce869fce) | fresh Fable, Oct 28 | 6 | 5 | door a white U-frame (~20 calcite cells, luma ~199, round a dark mouth); eye +120 over the body splits the cone in two tiers; upper spire 13 of 16 cells dark, a dark cap (a third tier). The cone body at y 10..13 reads as one triangle. | cone outline 11 15 19 23 25 23 19 15 11; the body at y 10..13 and the arris line; the granite band at y 11; buttresses z 7/17 stopping at the eave; the brass tip; halls and wings |
| tower r5 (85bdd5d4) | fresh Fable, Oct 28 | 6 | 6 | the door reads as an owl face (two pale voussoirs either side of a brass keystone over a dark vault band, luma 25); the tiers read as stacked boxes (a ziggurat front-on, a pagoda from the RTS corner); the shed, ~40 % of the RTS view, a black box (median luma 24). | the cone outline; body y 10..15 and the arris line; granite jambs z 9/15; the granite-ring eye; buttresses z 7/17; brass tip; halls, wings, the south-gable emblem, the lintel line |
| tower r6 (dda31dab) | fresh Fable, in game, Oct 28 | 6 | 6 | the granite ticks at every tier corner (y 11, 15, 19, 23) put a pink rim on each 4-row box - the ziggurat read is not resolution; the top a 3x3 red stub under a copper block; no door glow at dusk (door cell luma median 34, no warm pixels). In game the cone is one red mass (red 38 -> 69 %), the shed holds. Verdict: freeze T1 after these fixes; the rest of ref18 belongs to T2 / T3. | as r5's keeps, plus the shed gable |
| tower r7 (c42900b5) | lovish, in game, Oct 28 22:30 | - | - | lovish: "its missing enough detail to satisify my requirements. There isnt a cool roof tiling system, could be more refined and articulate especially for that size. I think your having trouble mimicing the references art detail and making it to your own thing. Maybe you need to think more like an artichetcture professional and study how they make buildings". Method change: an architecture detail study (roof tiling system, facade articulation grammar, Brassworks identity) before any r8. | massing r7: cone outline, 5x8 lane, granite slab treads, needle point, jamb lanterns, shed gable |
| roofs+walls r8 (96a7798) | fresh Fable, in game plain + shaders, Oct 29 | realism 3 | detail 4 | squint: a black pagoda on a black barn with mint stickers. Roof median luma 61 < walls 78 (deepslate tiles read as a hole, not a sky-lit plane; striped eave-to-ridge, not courses); tower a 5-step pyramid, copper patches on 3 of 5 stages; main roof empty (no dormers / roof lights); ~1.5 points better than r7 (palette now industrial). Scores: roof 3, tower 3, cohesion 4. | red brick body + stone plinth; the big door bay; the banded chimney; chain downpipes; slab eaves; green copper on the tower |

## Engine Shed r12 (lovish's four picks on r11, Oct 30; PR #411) - two fresh API critics per round, one fixed rubric
Each round photographed with its control in ONE eyes run (build 4528552, branch pack, plain lane); scores critic A / B.
Measured numbers: design/vehicle_labs/r12_expected.md (expected written before each round's stills).

| Round | Eyes run | RTS | Close | Towers | Gate | Roofs | Weathering | Dusk | Main faults (critics) | Kept |
|---|---|---|---|---|---|---|---|---|---|---|
| r11 round 3 (control) | 03:35Z | 6 | 5 | 4 | 3 | 6 | 2 | 3 | ring-stack chimneys, mint stepped pyramid, white-cool glowing panes, black gate, no soot | - |
| r12 round 1 (1d512ead) | 41022055197 | 7 / 6.5 | 6 / 6 | 6 / 6 | 4 / 4 | 6 / 6 | 3 / 3 | 4 / 4 | brick wash reads as a second ring; spire pale (value 0.54) with a dotted hip seam; rooms not lit; the dark lump may be another mass | one crown, no glowing panes, stone gate jambs |
| r12 round 2 (7ad66c9a) | 41026048601 | 6.5 / 6.5 | 6 / 6 | 6 / 6 | 4 / 4 | 6 / 6 | 4 / 3 | 4 / 4 | grey drips = "a mint arrow on dark grey"; dusk unchanged; coal stage now the darkest lump | one step-in shaft, stone gantry |
| r12 round 3 A spire (f5d3cc8b) | 41034045196 | 7 / 7 | 6.5 / 6.5 | 7 / 7 | 5 / 5 | 6.5 / 6 | 3 / 3 | 5.5 / 5.5 | weathering clean and new; coal heap reads grey, not coal; dusk "cream" | both picked A over the cupola B (RTS 6.5 / 6, towers 6.5 / 6) |
| r12 round 4 (9d3280f7) | 41039523394 | 7 / 6.5 | 6.5 / 6.5 | 6.5 / 6 | 5 / 5 | 6.5 / 6.5 | 4 / 3 | 5 / 5 | fine-grain spire weathering = "a dark V"; acacia rooms no warmer at dusk; both: keep round 3 | pier streaks gone (A: weathering +1) |
| r12 final (f96e1648) | 41044327198 | 7 | 6.5 | 7 | 5 | 6.5 | 3 | 5.5 | one critic, vs round 3: "no pixel difference ... ship the final"; open: dusk warmth (hue 266 in that run), the soot does not read, a dark mass still read at the front yard (the chassis), the spire's cyan vs the team-colour rule (lovish's call) | round 3's spire and dusk + round 4's weathering and gantry |

Lessons (r12): the 3/4 camera model was 1.2x too big (FOV 70, the game draws 77 in creative flight) and the colour
table's hue median was wrong - fix the camera before judging; dusk light differs between eyes runs (compare dusk only
inside one run); a second material on a small roof reads as a pattern at RTS whichever way it is placed (clusters ->
arrow, fine grain -> V); the 3/4 camera looks down so steeply that a lamp more than 1 block behind a pane is never seen.

## Sunwain Court r1 (Sunforged T1 vehicle lab, the r12 method; PR #456) - ref21, 17 x 17 x 26, one fixed rubric
Designer pass: itachi2-api runs 20261030-162547-sunforged-design + -163025-sunforged-design-cont (rubric = r12's 7 axes).
Each round photographed with its control in ONE eyes run (build e7b4e94 = build_run 41096418230, branch pack, plain
lane, keep_only, times 6000); two fresh API critics per round, a / b. Expected and measured numbers:
design/vehicle_labs/sunforged_expected.md.

| Round | Eyes run | RTS | Close | Landmark | Gate | Roofs | Materials | Dusk | Main faults (critics) | Kept |
|---|---|---|---|---|---|---|---|---|---|---|
| round 2 (control, Oct 28) | - | 4 overall (round 1 critic) | | | | | | | no single landmark, weak gate, no bay rhythm, roof blob, mixed materials, messy ground, dead windows | - |
| massing A / B / C + control | 41124572036 | A 6 / 7, C 5 / 5, control 5 / 5 | - | A 5 / 6, C 7 / 7, control 4 / 4 | A 5 / 5, control 3 / 3 | A 6 / 6, control 4 / 3 | - | - | both pick A (ref21 keep); spire flattens to a salmon square at RTS; four equal towers; gate not the darkest opening | A -> A2 (spire shaft, tower hierarchy, pale wing, splayed portal) |
| detail r1 (aeb4ae58) | 41138964754 | 5 / 5 (control 4 / 4) | 5 / 5 | 6 / 5 | 5 / 5 | 5 / 6 | 5 / 5 | 5 / 3 | grey andesite walls (0.29 / 0.38) in the ground's mid band - "a plain grey castle with a yellow square on its roof"; the gold halo ring leads at RTS; the spire a squat hipped roof from above; windows read as white crosses; stacked battlement stripes | overall 5 / 5 vs control 4 / 4: one peak in 3/4, a real gate, a lit rose, zones, no chequer; L1 reads as the first stage |
| detail r2 (da4b623b) | 41145225614 | 5 / 5 (r1 5 / 5) | 5 / 5 | 6 / 6 | 5 / 5 | 4 / 5 | 4 / 4 | 4 / 4 | no gain: the diorite field against stone-brick quoins reads as "zebra / ladder stripes"; the deepslate spire leads but "overshoots into the environment darks" (0.17-0.20); the bare well "a drain, no circle"; grey glass: crosses gone, windows dead at dusk | the plain coping (both); halo removal (b) |
| detail r3 (final) | 41147427956 | 5 / 5 (r2 5 / 5) | 5 / 6 | 5 / 5 | 5 / 5 | 5 / 5 | 5 / 5 | 4 / 4 | 5.5 / 5.5 overall vs r2 5 / 5: zebra gone, a terrace centre; the timber spire (0.39) sits in the ground mids and leads less than the slate; the spire is off the gate-rose axis and tangent to the north tower crown; the flat pale terrace is the largest face from above; the rose an orange smear, the halo ring square; both: detail has plateaued - next is massing (spire on axis, a stepped roof face) for lovish | corners in the field stone, the halo ring (both), timber over slate (b; a: a band question for lovish) |

Lessons (Sunwain Court r1): a light stone's TOP can read as snow while its sides do not (polished diorite: up 0.769,
south 0.592, west 0.435) - put it only on vertical faces and test the level's own rts snow share (L1's cut tops showed
12 %); long-and-short quoins in two values read as "zebra / ladder stripes" at RTS; a steep spire reads from the RTS
camera only by value, and both ends overshoot (granite 0.55 "salmon", slate 0.18 "environment darks", timber 0.39
"in the ground mids") - its place on the gate-rose axis and its footprint are massing, not material; re-measuring a
camera-table entry another lab's tests rely on moves their numbers (copper blotch 0.050 -> 0.052): measure with
camera_colour.py --add-only.

## Rooting Hollow r1 (Verdant T1 vehicle lab, the r12 method; PR #457) - ref06, 17 x 17 x 26, one fixed rubric
Two fresh API critics per round (a / b), the 7-axis rubric from the designer pass (itachi2-api/runs/
20261030-204240-verdant-design); the massing rubric adds "separation from the ground" and resemblance.

| Round | Eyes run | RTS | Close | Landmark | Gate | Roofs | Materials | Dusk | Main faults (critics) | Kept |
|---|---|---|---|---|---|---|---|---|---|---|
| round 2 (control, Oct 28) | - | 3 overall (round 1 critic) | | | | | | | "a mossy stone box with a toy tree on the lid": no real tree, flat canopy slab, unreadable gate, scattered lime lights, stone faces without order, roof clutter | - |
| massing A / B / C + control | 41152348776 | A 7 / 7, B 6 / 6, C 5 / 5, control 3 / 3 | - | A 6 / 6, B 6 / 6, control 3 / 3 | A 5 / 6, B 6 / 6, control 4 / 5 | A 7 / 7, B 7 / 7, control 3 / 3 | (separation A 7 / 8, B 8 / 8, control 4 / 3) | - | both A > B > C > control (A vs B close); A: the trunk hidden behind hall and turret in 3/4, no sky between limb and hall, a stepped-cone canopy leaving the RTS frame, a pale lit gate, the turret cap reads as a chimney | A + those faults -> detail r1 |
| detail r1 (ab959149) | 41155254248 | 6 / 6 (A 6 / 6, control 3 / 3) | 5 / 5 | 5 / 5 | 4 / 5 | 6 / 6 | 5 / 5 | 3 / 4 | overall 5 / 5 (A 5 / 4.5, control 3 / 3): the trunk reads as a staircase / scaffold (stepped pale blocks, dark bands), the tree beside the hall, not wrapping it; no crescent; the canopy a uniform bright pink lid with spruce spots (chequer, V-chevrons); the gate a dark floor "grate" from RTS, no roots frame it; the glow never seen (it faced the well); banded root posts; L1 canopy a plus sign | no turret, the canopy inside the RTS frame, dark bellies, the trunk seen in 3/4; L1 reads as the first stage |
| detail r2 (a30751c5) | 41157566607 | 6 / 6 (r1 5 / 5) | 4 / 5 | 5 / 5 | 4 / 5 | 5 / 5 | 4 / 4 | 5 / 6 | overall 5 / 6 vs r1 4 / 5 (a gain): canopy tops clean, the outside grate gone, the glow seen, no banded posts; still: the tree stands beside the hall (the limb a stepped slab line, "a gallows" in 3/4), the canopy one flat value with green belly bands, the passage floor a grid, the gallery arches compete with the gate, the pad a big grey plinth; L1 canopy a ring | the arch limb, the glow on the south foot, one-tone tops |
| detail r3 (1613cc2a) | 41159373535 | 5 / 5 (r2 5 / 5 rescored) | 5 / 5 | 4 / 4 | 5 / 5 | 6 / 6 | 5 / 5 | 6 / 5 | overall 5 / 5 vs r2 5 / 5 (no gain): the lower half better (gate the tallest opening, clean hall roof, mud floor, lawn under the tree, L1 crown closed) but the crown regressed - its tiers over the well opened "a pink donut" with a dark lattice from above and two dark holes that read as a skull in 3/4; the tree still stands beside the hall; gate and glow on opposite sides | both: keep r3's lower half, close the crown |
