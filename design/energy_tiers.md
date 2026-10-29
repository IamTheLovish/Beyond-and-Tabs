# Energy tiers - a fantasy Fusion / AFUS ladder per faction (proposal, Oct 29 2026)

lovish, Oct 28: energy feels "stuck on windmills and energy converters"; wants higher-tier energy like Beyond All
Reason's Fusion / Advanced Fusion, "designed to fit the game's fantasy feel". This is a menu to pick from, not code.
It builds ON Joisco's draft PR #193 (`FusionReactor`, Kind FUSION / ADVANCED) - same mechanics, names as the role,
and where a number should move it says so and why. Ideas credited to lovish are his (Oct 29, 01:26-01:28).
Building rules followed: design/vehicle_labs/LESSONS.md (four tiers, one model per tier, grow per tier, never
oversized, T1/T2 upgrade in place, max footprint as a ghost) and design/architecture_rules.md (base-body-cap, roof
as the fifth facade, 3+ depth planes, life).

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

What a player's curve looks like: ~160 E/s at minute 5 (capitol + 10 winds); after Tier 2 the biggest single
building is 120 E/s and only on lava. Late sinks: T2 extractors 20 E/s each (10 = 200 E/s), Tier 2 research
(10080 E over 180 s = 56 E/s), and converters at 50 E/s each. To reach 1000 E/s a player builds ~70 winds (3,500 M,
~1,100 blocks of fields at the bot's 4-block pitch) plus 20 converters - two big, fragile, samey fields. That is the
"stuck" feeling: no rung above 120, so energy never becomes a decision, only more of the same 3x3.

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
(31) than the fusion (22) - the reward for the biggest single risk; (3) energy costs are heavy (fusion = 24 s of its
own output, AFUS 23 s): you must already have the economy to afford the next one; (4) converters get a better ratio
at T2 (58 E per metal vs 70), so the fusion's energy has somewhere to go without 15 fragile buildings.

## (c) The shared ladder (all 7 factions; the look changes, the numbers do not)
One energy LINE per faction, four models (one per tier). T1 and T2 upgrade in place; T4 is a separate build.
| Rung | Role (BAR) | Builder / gate | Footprint (h max) | E/s | Fresh cost M / E | HP | Storage | Death blast |
|---|---|---|---|--:|--:|--:|--:|---|
| T1 L1 | Solar | any builder | 5x5 (10) | 20 | 150 / 60 | 120 | - | none |
| T1 L2 | (in-tier level, add-on) | any builder | 5x5 | 45 | +80 / +600 | 200 | - | none |
| T1 L3 | Advanced Solar | Tier 2 research | 5x5 | 75 | +100 / +900 | 300 | +200 | none |
| T2 | **Fusion (#193 FUSION)** | T2 constructor, Tier 2 | 11x11 (18) | 750 | 2400 / 9000 | 1400 | +1500 | r10, heavy |
| T3 | **AFUS (#193 ADVANCED)** | T2 constructor, Tier 3 | 17x17 (28) | 2250 | 6000 / 27000 | 3000 | +4500 | r16, very heavy |
| T4 | Experimental (faction utility) | Tier 3 + T4 tech, 1 per player | 25x25 (40) | 3000 | 10000 / 45000 | 4000 | +6000 | r20 + faction aftermath |
| T2 converter (new) | BAR T2 maker | T2 constructor | 5x5 | -400 -> +5 M/s (80 E/M) | 300 / 6000 | 200 | - | r7 chain |

T1 L1-L3 absorbs today's Solar Collector and Advanced Solar (same numbers): they become levels of the T1 model, not
separate buildings. Wind stays as the cheap spread rung; Geothermal stays as the map-control rung. T2/T3 may take two
more in-tier levels each (+10 % output, ~15 % of the tier's cost, a small add-on: a stack, a tender, a ring).

**Where I'd change #193, and why** (his mechanics stay; these are three numbers per Kind):
- Fusion energy 3000 -> 9000. In #193 a fusion pays its energy bill in 4 s of its own output (BAR 24 s), so it
  snowballs instantly; 12 s keeps it a commitment. Metal 2100 -> 2400 (31 E/s per 100 M: a hair above wind's 28, as
  BAR has it level - the value is space and steadiness, not raw efficiency). HP 900 -> 1400 (2.8x the Advanced
  Solar's 500; BAR 3.4x): a raid must commit to kill it.
- AFUS metal 7350 -> 6000, energy 10500 -> 27000, HP 1600 -> 3000. In #193 the AFUS is LESS efficient per metal
  (30.6) than the fusion (35.7), so nobody builds it except for space; BAR rewards the bigger risk. At 6000 M it is
  37.5 per 100 M against the fusion's 31.
- "750 E/s is ~6x geothermal" (his open call): keep 750 - it matches BAR exactly. Geothermal stays the best per
  metal (43 per 100 M) because it is lava-locked, as BAR's geo (54) beats BAR's fusion (22). 750 E/s through T1
  converters is 7.5 M/s (9.4 through the T2 converter) = 1.7-2 T2 extractors - a real but not game-ending jump.
- Blast model: #193 rolls explodeChance (0.08 / 0.12) on every broken block for a TNT-class explosion r5 / r8, so a
  dying reactor craters terrain many times over. Proposal: keep that as the visual, but add ONE death blast like
  `EnergyConverter`'s chain blast (buildings within the radius lose blocks, units take damage), radius measured
  from the centre: r10 reaches ~4.5 blocks past an 11x11, r16 ~7.5 past a 17x17. Readable, spaceable, no crater field.
- Storage: add `energyStorage` to the Kinds (+1500 / +4500), as BAR's fusions store 2500 / 9000.

**Upgrade paths in place** - see (e).

## (d) Per-faction identities
Every model obeys the rules: a pale/heavier base course, a body, a cap; a roof (or rim, or canopy) that says what
the building is from the RTS camera; three depth planes; life. Footprints are T1 5x5 -> T2 11x11 -> T3 17x17 ->
T4 25x25 unless noted. Failure = what the death blast looks like.

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
  units), it cannot be sniped apart from the building, and it animates like the wind sails (`WindGenerator.MOTIONS`).
  Its stir speed follows output; it stops during a refit and when the pit is dying.
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
- **Smoke budget (design constraint):** `BuildingAmbienceClientEvents` allows 40 emitters a pass at ~5 puffs/s.
  So: 1 emitter per stack, large slow puffs (campfire-signal style) not many small ones; smoke rises above y+20 and
  leans with `Wind` so it never hides units; strategic zoom keeps 1 emitter per building; at most ~12 stack
  emitters per player. Smog (T4) is a faint ground haze, not a wall.

### Verdant Court (code `verdant_court`) - lead: lovish's ROOT TREE (roots boost buildings: decided)
Kit: mossy stone, living wood, azalea leaves, froglights. A tree whose roots visibly spread over the ground.
- T1 Heartwood Sapling: a young tree in a ring of mossy stones (5x5, ~10 tall), first roots breaking the turf.
- T2 Elder Heartwood: a buttressed trunk, canopy ~11 across, root flares like walls, froglight fruit.
- T3 Great Heartwood: a hollow trunk you can see light inside, two canopy tiers, hanging moss.
- T4 Mother Tree (25x25, ~40 tall): roots arching above ground like bridges. Utility: its roots carry the boost
  further (r20) and heal friendly units standing on them (1 % HP/s).
- **The root mechanic.** Roots reuse `FactionAura` (budgeted, reversible, walkable: rooted dirt and moss with
  walk-through root decorations; never in buildings or on metal). Radius from the footprint edge: T1 6, T2 10,
  T3 14, T4 20 (FactionAura.MAX_RADIUS is 16 today - T4 needs it raised). Spread 1 block of radius per 6 s.
  Boost (one pick): Verdant **labs** touched by roots get **+5 / +10 / +15 / +20 % production speed** (T1-T4), and
  every Verdant building on roots regenerates 0.5 % HP/s out of combat. Not metal income: +15 % on ten T2 extractors
  would be ~7 M/s, a free fusion. Stacking: none - a building takes the strongest tree touching it; trees do not
  boost energy buildings (no loops). Value check: 6 labs at +15 % ~ 0.9 of a lab, cheap for a T3 - fine.
- Reading it: roots reach a building's foot, climb its plinth as veined pale-gold roots, the building's moss
  greens and its lanterns gain a green halo. Distinct from the Rooted Treant: the Treant's roots are dark attack
  lines that strike; the tree's are slow, pale-gold, and only grow.
- Counterplay: while the tree takes damage its roots pull in (boost paused until 5 s out of combat); fire deals
  1.5x to it; roots recede when it dies (FactionAura.recede).
- Failure - **it falls**: leaves brown, bark blackens, and the trunk falls toward the killing blow - a LINE blast
  (length = tree height) instead of a circle; roots die back outermost first.

### Ashborn / Ironhide Horde (code `piglins`) - open: two options
Kit: basalt, tuff, obsidian, glowing cracks, fire bowls; the stone-giant forge.
- **A. The Caldera**: a crater that grows into a volcano. T1 a basalt fire-pit with a crack of glow; T2 a ringed
  crater with a lava lake; T3 a stepped cone with obsidian vents; T4 the Mountain Heart (utility: **Eruption**, an
  every-3-min artillery barrage of magma bombs on a target area). Life: heat shimmer, lava bubbling, ash fall.
  Failure: an eruption - magma bombs scatter, lava flows briefly down the cone (not into neighbours).
- **B. The Bound Ember Giant**: a kneeling stone giant chained to obsidian pylons, his chest a molten forge. T1 an
  ember-shard in chains; T2 the giant's head and shoulders out of the ground; T3 kneeling, both arms chained; T4 he
  stands, chained at the wrists (utility: **Unchain** - once a match, he breaks free as a 60 s T3 unit, then
  crumbles). Life: breathing glow in the chest cracks, chains straining. Failure: he breaks his chains and bursts.
- A is landscape (low, wide); B is a figure (tall, readable from far). Pick one.

### Stormcallers (code `stormcallers`) - open: two options
Kit: jade (waxed oxidized copper), dark oak, paper lanterns, amethyst, the lotus; Overcharge and the Mana Cistern.
- **A. Thunder Pagoda**: a pagoda that grows a storey per tier, a lightning rod on top that calls strikes every few
  seconds (output in pulses, fitting Overcharge). T4 Eye of the Storm: a permanent cloud over the base whose strikes
  hit enemy units under it. Failure: the rod overloads - chain lightning arcs to nearby buildings.
- **B. Storm Lotus**: an amethyst lotus floating over a mana pool inside a slowly turning ring of cloud; petals open
  per tier. T4 a giant lotus (utility: Overcharge costs half while it stands). Failure: the lotus shatters -
  amethyst shrapnel.
- A is vertical and loud; B is low, glowing and calm. Pick one.

### The AI faction (unnamed, replaces Tidewrought) - lead: lovish's literal AFUS
"The AI faction can get AFUS-like building": the one faction where Fusion / AFUS is literal. Tron-Legacy look:
dark plating (black concrete, polished blackstone, tinted glass), cyan light lines, containment rings, a hum.
- T1 Photon Cell: a dark plinth with a light-line grid and a hovering, slowly turning cube.
- T2 Fusion Core: a vertical containment torus, light lines pulsing toward the core, cooling fins on the cap.
- T3 Advanced Fusion: two stacked tori counter-rotating, service drones (structure-bound) orbiting.
- T4 Zero-Point Array: a lattice of four cores around a suspended singularity. Utility: **Shield dome** r20 that
  absorbs projectiles by draining stored energy (BAR's plasma deflector).
- Life: a deep hum, light lines brighten with output, drones, coolant venting.
- Failure - the classic reactor blast: lines go red 2 s first (a warning), then a white-cyan flash and a ring wave.

## (e) Upgrade paths in place
- Placing a T1 shows the T3 footprint (17x17) as a ghost; building inside it warns but is allowed (lovish's rule).
- T1 L1 -> L2 -> L3: add-ons only (same 5x5). L3 needs Tier 2.
- T1 L3 -> T2: needs Tier 2 + a T2 constructor; costs the T2 price minus what the T1 cost (2070 M / 7440 E); 90 s;
  output drops to 0 while refitting (a punishable window, like the lab upgrades); blocked if the 11x11 is not free.
- T2 -> T3: needs Tier 3; pays the difference (3600 M / 18000 E); 150 s; 0 output while refitting.
- T4: a new build, one per player (Q2). A fresh T2/T3 can also be built directly at the full price.
- Each model is its own structure (`<line>_t1` ... `_t4`), the level read from `currentStructureName`, as labs do.

## (f) Balance check
`python3 tools/balance_audit.py` on main: **OK** (exit 0; 277 costs baked; five pre-existing unit warnings, none
energy). It cannot see these numbers: they are design-only (not in Java), and its only energy rule is the wind
payback band. A scratch check of the same kind (payback, output ordering, pace) on the proposal:
| Rung | E/s per 100 M | Own output to pay its energy | Metal payback via converters |
|---|--:|--:|--:|
| #193 Fusion / AFUS | 35.7 / 30.6 | 4 s / 4.7 s | 430 s / 477 s |
| Proposed Fusion / AFUS / T4 | 31.3 / 37.5 / 30.0 | 12 s / 12 s / 15 s | ~470 s / ~420 s / ~480 s |
Every rung pays back in over 5 minutes and rises in output. Pace: a late base with 10 T2 extractors makes ~47 M/s;
adding 2 fusions + 1 AFUS through converters raises it to ~85 M/s (+80 %, BAR-like), a T4 to ~115 M/s. 500 T2
units (~125k M) is then ~25 min of full income without losses - hard to reach, as lovish wants. If that is too
fast, the lever is the converter ratio, not the reactors.

## Questions for lovish (pick one each)
1. #193 numbers: (A) my changes - Fusion 2400 M / 9000 E / HP 1400, AFUS 6000 M / 27000 E / HP 3000;
   (B) Joisco's as drafted; (C) only the AFUS fix (cheaper metal, dearer energy).
2. T4 energy: (A) a separate one-per-player experimental; (B) an in-place upgrade from T3.
3. Ashborn: (A) the Caldera or (B) the Bound Ember Giant?
4. Stormcallers: (A) Thunder Pagoda or (B) Storm Lotus?
5. Death blast: (A) one converter-style death blast (r10 / r16 / r20); (B) #193's per-block explosions only.
6. Add a T2 converter (-400 E/s -> +5 M/s): (A) yes, every faction; (B) no, T1 converters only.
