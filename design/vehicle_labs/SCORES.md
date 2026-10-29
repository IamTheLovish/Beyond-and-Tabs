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
