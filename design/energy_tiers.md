# Energy tiers - a fantasy Fusion / AFUS ladder per faction (proposal, Oct 29 2026)

lovish, Oct 28: energy feels "stuck on windmills and energy converters"; wants BAR-style Fusion / AFUS "designed to
fit the game's fantasy feel". A menu to pick from, not code, built ON Joisco's draft #193 (`FusionReactor` FUSION /
ADVANCED): his mechanics, with the numbers I'd move and why. Ideas marked lovish's are his (Oct 29, 01:26-01:28).
Rules followed: design/vehicle_labs/LESSONS.md (4 tiers, a model per tier, T1/T2 refit in place, ghost footprint)
and design/architecture_rules.md (base-body-cap, roof as fifth facade, 3+ depth planes, life).

## (a) The problem, in numbers (main, d2fb33f0)
| Source | E/s | Cost M / E | HP | Gate | Code |
|---|--:|--:|--:|---|---|
| Capitol | 20 | - | - | owned | EconomyServerEvents.BASE_ENERGY_INCOME |
| Wind Generator (3x3) | 14 avg (4-24 with wind) | 50 / 30 | 110 | T1 | WindGenerator, Wind.MIN/MAX |
| Solar Collector (3x3) | 20 steady | 150 / 60 | 120 | T1 | SolarGenerator.Kind.BASIC |
| Advanced Solar (5x5) | 75 | 300 / 1500 | 500 | T2 constructor | SolarGenerator.Kind.ADVANCED |
| Geothermal (5x5) | 120 | 280 / 1000 | 400 | T2, lava only | GeothermalPlant |
| Energy Converter (3x3) | -50 -> +0.5 M/s | 75 / 300 | 50, chain blast r5 | T1 | TierGate (100 E per metal) |
| Storage | 1000 base +500 capitol, vault +3000 (Mana Cistern +4500) | vault 95 / 180 | 60 | T1 | StorageVault |

The curve: ~160 E/s at minute 5 (capitol + 10 winds); after Tier 2 the biggest building is 120 E/s, lava only.
Sinks: T2 extractors 20 E/s each, Tier 2 research (56 E/s for 180 s), converters 50 E/s each. 1000 E/s takes ~70
winds (3,500 M, ~1,100 blocks of field) + 20 converters: two big, fragile, samey fields. That is the "stuck"
feeling - no rung above 120, so energy is never a decision, only more of the same 3x3.

## (b) BAR's ladder (Armada; beyondallreason.info unit pages, fetched Oct 29)
| BAR building | E/s | Metal | Energy | HP | Storage | What makes it a decision |
|---|--:|--:|--:|--:|--:|---|
| Wind turbine | wind-varying | 40 | 175 | 196 | - | cheap, spread, map-dependent gamble |
| Solar | 20 | 155 | 0 | 340 | - | steady, worse per metal |
| Advanced Solar | 80 | 350 | 5000 | 1130 | 100 | T2, compact |
| Geothermal | 300 | 560 | 13000 | 1940 | - | only on vents: map control |
| Fusion | 750 | 3350 | 18000 | 3800 | 2500 | "large explosion": one big target, space it |
| Advanced Fusion | 3000 | 9700 | 69000 | 7900 | 9000 | "very large explosion": best per metal, biggest risk |
| Converter T1 / T2 | -70 -> 1 M/s / -600 -> 10.3 M/s | 1 / 380 | 1150 / 21000 | 167 / 445 | - | "explode violently", space them |
| Energy storage | - | 170 | 1700 | 1890 | +6000 | explodes hard - even used as a bomb |

The decisions: (1) concentration vs spread - a fusion is ~22 E/s per 100 metal, the same as a wind field, but in
one footprint that can die in one raid and takes its neighbours with it; (2) the AFUS is MORE efficient per metal
(31) than the fusion (22) - the reward for the biggest single risk; (3) build time: fusion 54000 BT, AFUS 313000
(6.8x / 39x the adv. solar's 7950) - in a flow economy that, not price, is the brake; (4) T2 converters get a better
ratio (58 E per metal vs 70), so the fusion's energy has somewhere to go without 15 fragile buildings.

## (c) The shared ladder (all 7 factions; the look changes, the numbers do not)
One energy LINE per faction, four models (one per tier). T1 and T2 upgrade in place; T4 is a separate build.
| Rung | Role (BAR) | Builder / gate | Footprint (h max) | E/s | Fresh cost M / E | Build (blocks) | HP | Storage | Death blast |
|---|---|---|---|--:|--:|--:|--:|--:|---|
| T1 L1 | Solar | any builder | 3x3 core in a 5x5 plot (10) | 20 | 150 / 60 | 90 | 120 | - | none |
| T1 L2 | (in-tier level, add-on) | any builder | fills the 5x5 | 45 | +80 / +600 | +40 | 200 | - | none |
| T1 L3 | Advanced Solar | Tier 2 research | 5x5 | 75 | +100 / +900 | +40 | 300 | +200 | none |
| T2 | **Fusion (#193 FUSION)** | T2 constructor, Tier 2 | 11x11 (18) | 750 | 2400 / 9000 | 900 | 1400 | +1500 | r10, heavy |
| T3 | **AFUS (#193 ADVANCED)** | T2 constructor, Tier 3 | 17x17 (28) | 2250 | 6000 / 27000 | 2400 | 3000 | +4500 | r16, very heavy |
| T4 | Experimental (faction utility) | Tier 3 + T4 tech, 1 per player | 25x25 (40) | 3000 | 10000 / 45000 | 3600 | 4000 | +6000 | r20 + faction aftermath |
| T2 converter (new) | BAR T2 maker | T2 constructor | 5x5 | -400 -> +5 M/s (80 E/M) | 300 / 6000 | 200 | 200 | - | r7 chain |

**Build time is the brake.** A flow economy (BuildingServerEvents ~L478: no upfront payment) drains cost while
building, so price-vs-output brakes nothing; build time x build power does. A block = 21 ticks = 1.05 builder-s.
From the Advanced Solar (140 blocks) at BAR's ratios (6.8x / 39x): fusion 900 blocks (6.4x; ~4 min for four T2
constructors), AFUS 2400 (compressed to 17x: ~7 min for six - our builders are slower and fewer). #193's 220 / 320
(1.6x / 2.3x) puts a fusion up as fast as a lab: the biggest change I'd ask. Energy cost then sets the drain while
building (9000 E over ~4 min = ~38 E/s), the competition with units.
T1 L1 is the 3x3 core of a 5x5 plot (2.2 E/s per cell, above wind's 1.6; a bare 5x5 at 20 E/s would be 0.8 - worse
than wind); L2 and L3 fill the plot with add-ons (L3: 3 E/s per cell).

T1 L1-L3 absorbs today's Solar Collector and Advanced Solar (same numbers) as levels of one model. Wind stays the
cheap spread rung, Geothermal the map-control rung. T2/T3 may take two in-tier levels (+10 % output, ~15 % cost).

**Where I'd change #193, and why** (his mechanics stay; only numbers in his Kinds plus one blast pass):
- Build time 220 -> 900 blocks, AFUS 320 -> 2400 (above). Fusion energy 3000 -> 9000 (BAR's is 5.4 E per metal,
  #193's 1.4): a heavier drain while it goes up. Metal 2100 -> 2400 (31 E/s per 100 M: a hair above wind's 28, as
  BAR has it level - the value is space and steadiness, not raw efficiency). HP 900 -> 1400 (2.8x the Advanced
  Solar's 500; BAR 3.4x): a raid must commit to kill it.
- AFUS metal 7350 -> 6000, energy 10500 -> 27000, HP 1600 -> 3000. In #193 the AFUS is LESS efficient per metal
  (30.6) than the fusion (35.7), so nobody builds it except for space; BAR rewards the bigger risk. At 6000 M it is
  37.5 per 100 M against the fusion's 31.
- "750 E/s is ~6x geothermal" (his open call): keep 750 - it matches BAR exactly. Geothermal stays the best per
  metal (43 per 100 M) because it is lava-locked, as BAR's geo (54) beats BAR's fusion (22). 750 E/s through T1
  converters is 7.5 M/s (9.4 through the T2 converter) = 1.7-2 T2 extractors - a real but not game-ending jump.
- Blast model: #193 rolls explodeChance (0.08 / 0.12) on every broken block for a TNT-class explosion r5 / r8, so a
  dying reactor craters terrain many times over. Proposal: keep that as the visual, but add ONE death blast in
  `EnergyConverter`'s shape (one pass over placements within the radius, from the centre: r10 reaches ~4.5 blocks
  past an 11x11, r16 ~7.5 past a 17x17). The converter's flat 28 damage (RaidTuning.CONVERTER_BLAST_DAMAGE) cannot
  touch a 1400-HP neighbour, so damage SCALES: each building inside loses **40 % of its own max HP** (full inside
  r/2, half to r), **80 % if it is another reactor of the line** - so a reactor already hurt past 20 % dies and
  chains, as BAR's do; units inside take 300 (T2) / 600 (T3) / 800 (T4). Readable, spaceable, no crater field.
- Storage: add `energyStorage` to the Kinds (+1500 / +4500; BAR's store 2500 / 9000). Upgrade paths: (e).

## (d) Per-faction identities
Every model: base course, body, cap; a roof (rim, canopy) that names it from the RTS camera; 3 depth planes; life.
Footprints T1 5x5 -> T2 11x11 -> T3 17x17 -> T4 25x25 unless noted. Failure = what the death blast looks like.

### Sunforged / White Angel (code `villagers`, HUD "White Angel") - lead: lovish's CHURCH OF PRAYER
Energy is prayer. Kit: white stone and quartz, blue-glass lancets, candles, an ochre-froglight sun.
- T1 Wayside Chapel: one nave, a pale ashlar plinth, a steep slate-grey roof with a bell-cote; a candle-lit door.
- T2 Pilgrim Church: cruciform, a square bell tower at the crossing, a rose window on the camera face, buttresses.
- T3 Basilica of Prayer: a ribbed dome on a blue-glass drum, two west towers, a cloister with kneeling worshippers.
- T4 Cathedral of the Thousand Prayers (25x25, ~40 tall): twin spires and a lantern crown; a column of pale light
  rises from the crown (the energy made visible). Utility: **Sanctuary** - friendly units within 24 blocks regenerate
  1 % HP/s out of combat; the light column brightens when it is working.
- Life: candles in every window at dusk, a bell tolls with each energy pulse, kneeling worshippers (structure-bound
  figures, not units) bow, a slow choir loop, gold motes drifting up from the roof.
- Failure - **the Silence**: bells crack and stop, the light collapses inward then bursts as a white-gold flare;
  T3+ leaves the ruin ringing (enemy units in the blast are blinded 3 s).
- Option B (genuinely different, outdoor): the Sunwell - a ring of tilting gold mirrors focusing on a sun crystal.

### Gravebound (code `monsters`, HUD "Death") - lead: lovish's SOUL CAULDRON
"A massive pit or cauldron of someone mixing souls into the ground." Kit: deepslate, blackstone, bone ribs, soul
lanterns, green glow, chains, skulls. It is SUNK: the footprint is mostly a pit, so its height stays low and its
"roof" is the glowing soul-surface seen from above - the fifth facade is the pit itself.
- T1 Soul Pit: a 5x5 ring of polished-deepslate kerb, 3 deep, a green soul-surface; ONE Tender on a bone gantry
  stirring with a long paddle.
- T2 Soul Cauldron: a half-sunk iron cauldron rim (11x11), soul fire visible through vents in the stone collar, two
  Tenders, a chain-hung bone crane tipping a cage of souls in.
- T3 Soul Well: a stepped pit (17x17, 6 deep), great bone ribs arching over it like a half-built ribcage, four
  Tenders walking a ring gallery, soul lanterns on every rib.
- T4 Soul Maw (25x25): a vast throat in the earth; one colossal Tender (a hunched giant) stirs with a ship's-mast
  paddle. Utility: **Harvest** - every unit that dies within 32 blocks (either side) adds +2 % output for 30 s
  (cap +50 %), so big fights near the base feed it; each soul visibly streaks into the maw.
- The Tender is a **structure-bound animated figure, not a unit**: no pathing or selection clutter (pace: fewer
  units) and it cannot be sniped apart from the building. It is NOT a `WindGenerator.MOTIONS` part (those are rigid
  WHEEL / BELL / PETALS / VANES rotations): torso, two arms and the paddle need a small multi-joint rig (see Rigs
  below). Its stir speed follows output; it slows to half during a refit and stops when the pit is dying.
- Life: the stirring (the core of it), a slow green vortex on the surface, wisps rising and sinking, chains swaying.
- Failure - **the pit boils over**: a green soul geyser, wraiths spiralling outward through the blast radius, then
  the pit caves in and stays blighted (dead, ashen ground) for 60 s. No second option: nothing else adds as much.

### Brassworks Artificers (code `brassworks`) - lead: lovish's SMOKE FACTORY WITH GEARS
Red brick walls, grey slate roofs, weathered green copper on towers and spires (decided). Coal and steam.
- T1 Boiler House: a brick shed on a pale stone plinth, one banded brick stack with a dark cap, a firebox glow
  under an iron-bar grate, ONE exposed flywheel on the side wall.
- T2 Steam Works: an engine hall, slate roof with roof vents and a copper cupola, two stacks, a gear train across
  the camera facade (three meshed gears, 3-5 wide).
- T3 Power Station: a glazed barrel-roof engine hall, four stacks, a gear wall with a 7-wide wheel, coal bunkers.
- T4 The Great Engine: six stacks in two banks, a green-copper governor tower (spinning flyballs on top), a 9-wide
  flywheel in an open arch. Utility: **Smog** - the base within 24 blocks is hidden from enemy sight unless an enemy
  unit is within 8 blocks of a building (a BAR jammer in steam).
- Life: heavy constant smoke, furnace glow, pistons and connecting rods, gears turning faster at high output (and
  stalling when energy is pinned at the cap), steam bursts from safety valves.
- Failure - **boiler explosion**: a white steam blast, the stacks topple outward as falling blocks, scalding steam
  lingers 5 s (damage over time).
- Smoke: 1 emitter per stack, large slow puffs (campfire-signal style), rising above y+20 and leaning with `Wind`
  so it never hides units; T4's six stacks share its 4 emitters in rotation (budget below). Smog (T4) is a faint
  ground haze, not a wall.

### Verdant Court (code `verdant_court`) - lead: lovish's ROOT TREE (roots boost buildings: decided)
Kit: mossy stone, living wood, azalea leaves, froglights. A tree whose roots visibly spread over the ground.
- T1 Heartwood Sapling: a young tree in a ring of mossy stones (5x5, ~10 tall), first roots breaking the turf.
- T2 Elder Heartwood: a buttressed trunk, canopy ~11 across, root flares like walls, froglight fruit.
- T3 Great Heartwood: a hollow trunk you can see light inside, two canopy tiers, hanging moss.
- T4 Mother Tree (25x25, ~40 tall): roots arching above ground like bridges. Utility: its roots carry the boost
  further (r20) and heal friendly units standing on them (1 % HP/s).
- **The root mechanic.** Roots reuse `FactionAura` (budgeted, reversible, walkable: rooted dirt and moss with
  walk-through root decorations; never in buildings or on metal). Its radius is measured from the CENTRE
  (`radiusFor` = half-footprint + skirt), so: T1 8 (2+6), T2 13 (5+8), T3 16 (8+8: exactly today's
  `MAX_RADIUS` 16), T4 24 (12+12: needs MAX_RADIUS raised to 24). The first draft's edge radii (T3 8+14 = 22) were
  already over the cap at T3, not only T4. Spread is the aura budget, not a fixed speed: `CELLS_PER_AURA` 1 per
  `PASS_TICKS` 10 = 2 cells/s, so a T3 disc (~800 cells) fills in ~7 min and a T4 disc (~1800) in ~15 min - give the
  T4 tree 2 cells a pass (7.5 min). Cost check: each pass looks at most `EXAMINE_PER_STEP` 8 candidates per aura and
  the registry serves `AURAS_PER_PASS` 24 auras, so a tree costs what a lab's aura costs; a bigger plan only grows
  the precomputed cell list (~1800 packed ints at r24). A building is boosted once roots have reached its foot.
  Boost (one pick): Verdant **labs** touched by roots get **+5 / +10 / +15 / +20 % production speed** (T1-T4), and
  every Verdant building on roots regenerates 0.5 % HP/s out of combat. Not metal income: +15 % on ten T2 extractors
  would be ~7 M/s, a free fusion. Stacking: none - a building takes the strongest tree touching it; trees do not
  boost energy buildings (no loops). Value check: 6 labs at +15 % ~ 0.9 of a lab, cheap for a T3 - fine.
- Reading it: pale-gold roots reach a building's foot and climb its plinth; its moss greens, its lanterns gain a
  green halo. Unlike the Rooted Treant's dark striking roots, the tree's are slow, pale-gold, and only grow.
- Counterplay: while the tree takes damage its roots pull in (boost paused until 5 s out of combat); fire deals
  1.5x to it; roots recede when it dies (FactionAura.recede).
- Failure - **it falls**: leaves brown, bark blackens, and the trunk falls toward the killing blow - a LINE blast
  (length = tree height) instead of a circle; roots die back outermost first.

### Ashborn / Ironhide Horde (code `piglins`) - open: two options
Kit: basalt, tuff, obsidian, glowing cracks, fire bowls; the stone-giant forge.
- **A. The Caldera**: a crater that grows into a volcano. T1 a basalt fire-pit with a crack of glow; T2 a ringed
  crater with a lava lake; T3 a stepped cone with obsidian vents; T4 the Mountain Heart (utility: **Eruption**, an
  player-aimed barrage: range 64 from the Heart, 12 magma bombs over 4 s into an r6 circle, 80 damage per bomb to
  units, 40 % of the converter-style building blast; costs 3000 E, 3-min cooldown; telegraph: the target circle
  glows red and the cone rumbles for 3 s, visible to the enemy). Life: heat shimmer, lava bubbling, ash fall.
  Failure: an eruption - magma bombs scatter, lava flows briefly down the cone (not into neighbours).
- **B. The Bound Ember Giant**: a kneeling stone giant chained to obsidian pylons, his chest a molten forge. T1 an
  ember-shard in chains; T2 the giant's head and shoulders out of the ground; T3 kneeling, both arms chained; T4 he
  stands, chained at the wrists (utility: **Unchain** - once a match, he breaks free as a 60 s T3 unit, then
  crumbles). Life: breathing glow in the chest cracks, chains straining. Failure: he breaks his chains and bursts.

### Stormcallers (code `stormcallers`) - open: two options
Kit: jade (waxed oxidized copper), dark oak, paper lanterns, amethyst, the lotus; Overcharge and the Mana Cistern.
- **A. Thunder Pagoda**: a pagoda that grows a storey per tier, a lightning rod on top that calls strikes every few
  seconds (output in pulses, fitting Overcharge). T4 Eye of the Storm: a permanent cloud over the base whose strikes
  hit enemy units under it. Failure: the rod overloads - chain lightning arcs to nearby buildings.
- **B. Storm Lotus**: an amethyst lotus floating over a mana pool inside a slowly turning ring of cloud; petals open
  per tier. T4 a giant lotus (utility: Overcharge costs half while it stands). Failure: the lotus shatters -
  amethyst shrapnel.

### The AI faction (unnamed, replaces Tidewrought) - lead: lovish's literal AFUS
"The AI faction can get AFUS-like building": the one faction where Fusion / AFUS is literal. Tron-Legacy look:
dark plating (black concrete, polished blackstone, tinted glass), cyan light lines, containment rings, a hum.
- T1 Photon Cell: a dark plinth with a light-line grid and a hovering, slowly turning cube.
- T2 Fusion Core: a vertical containment torus, light lines pulsing toward the core, cooling fins on the cap.
- T3 Advanced Fusion: two stacked tori counter-rotating, service drones (structure-bound) orbiting.
- T4 Zero-Point Array: a lattice of four cores around a suspended singularity. Utility: **Shield dome** r20 (BAR's plasma
  deflector): a 3000-point shield that absorbs projectiles; it regenerates at most 100 points/s at **15 E per point**
  (up to 1500 E/s, half the Array's output), costs 100 E/s upkeep while up, and when broken goes down for 15 s. So
  siege above ~100 DPS breaks it, and holding it up starves the rest of the base - never siege-immune.
- Life: a deep hum, light lines brighten with output, drones, coolant venting.
- Failure - the classic reactor blast: lines go red 2 s first (a warning), then a white-cyan flash and a ring wave.

## (e) Upgrade paths in place
- Placing a T1 shows the T3 footprint (17x17) as a ghost; building inside it warns but is allowed (lovish's rule).
- T1 L1 -> L2 -> L3: add-ons only (same 5x5). L3 needs Tier 2.
- T1 L3 -> T2: needs Tier 2 + a T2 constructor; costs the T2 price minus what the T1 cost (2070 M / 7440 E) and
  the build-time difference (~760 blocks); blocked if the 11x11 is not free.
- T2 -> T3: needs Tier 3; pays the difference (3600 M / 18000 E, ~1500 blocks).
- While refitting the building **keeps its old output** (a refit must beat building fresh, or nobody refits); the
  punishable window is HP and blast: it keeps its old tier's HP and blast while scaffolding covers the new ring, and
  if it dies mid-refit the spent resources are lost. The new output switches on when the refit completes.
- T4: a new build, one per player (Q2). A fresh T2/T3 can also be built directly at the full price.
- Each model is its own structure (`<line>_t1` ... `_t4`), the level read from `currentStructureName`, as labs do.

**Rigs.** Rigid `WindGenerator.MOTIONS` rotations (WHEEL / BELL / PETALS / VANES, cheap): Brassworks gears and
flywheels, governor flyballs, White Angel bells, AI cube and tori (vertical axis is new), Stormcallers cloud ring and
lotus petals, Sunwell mirrors. Multi-joint rigs (a new renderer, near a unit model - one per line is the budget):
the Tender and T4 giant Tender, the bone crane (2 joints), bowing worshippers (one hinge, instanced), Brassworks
pistons and rods (crank linkage), the Ashborn giant, AI drones (rigid, on orbits). One-offs: the tree's fall, stacks.

**Particle budget, every faction.** `BuildingAmbienceClientEvents`: a pass every 4 ticks (~5 puffs/s per emitter),
40 emitters a pass for the whole view (IDLE_BUDGET), none in strategic zoom. Energy line: T1 1, T2 2, T3 3, T4 4
emitters; a late base (3 T2 + T3 + T4 = 13) leaves 27 for the rest. Spent on: White Angel gold motes, Gravebound
soul wisps, Brassworks stack smoke, Verdant spores and petals, Ashborn ash and lava pops, Stormcallers rod sparks,
AI coolant vents. Free (blocks or render, not particles): candles, light column, light lines, lightning, shimmer.

## (f) Balance check
`python3 tools/balance_audit.py` on main: **OK** (exit 0; 277 costs baked; five pre-existing unit warnings, none
energy). It cannot see these numbers: they are design-only (not in Java), and its only energy rule is the wind
payback band. A scratch check of the same kind (payback, output ordering, pace) on the proposal:
| Rung | E/s per 100 M | Builder-seconds (x adv. solar) | Metal payback via converters |
|---|--:|--:|--:|
| #193 Fusion / AFUS | 35.7 / 30.6 | 231 / 336 (1.6x / 2.3x) | 430 s / 477 s |
| Proposed Fusion / AFUS / T4 | 31.3 / 37.5 / 30.0 | 945 / 2520 / 3780 (6.4x / 17x / 26x) | ~470 s / ~420 s / ~480 s |
Every rung pays back in over 5 minutes and rises in output. Pace: a late base with 10 T2 extractors makes ~47 M/s;
adding 2 fusions + 1 AFUS through converters raises it to ~85 M/s (+80 %, BAR-like), a T4 to ~115 M/s. 500 T2
units (~125k M) is then ~25 min of full income without losses - hard to reach, as lovish wants. If that is too
fast, the lever is the converter ratio, not the reactors.

## Questions for lovish (pick one each)
1. #193 numbers: (A) my changes - Fusion 2400 M / 9000 E / HP 1400, AFUS 6000 M / 27000 E / HP 3000;
   (B) Joisco's as drafted; (C) only the AFUS fix (cheaper metal, dearer energy).
2. T4 energy: (A) a separate one-per-player experimental; (B) an in-place upgrade from T3.
3. Ashborn: (A) the Caldera (landscape, low and wide) or (B) the Bound Ember Giant (a figure, tall, reads from far)?
4. Stormcallers: (A) Thunder Pagoda (vertical, loud) or (B) Storm Lotus (low, glowing, calm)?
5. Death blast: (A) one converter-style death blast (r10 / r16 / r20); (B) #193's per-block explosions only.
6. Add a T2 converter (-400 E/s -> +5 M/s): (A) yes, every faction; (B) no, T1 converters only.
