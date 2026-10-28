# Generated maps: report

PR #131, branch `claude/maps-generated` (draft, based on #122 `claude/maps-m1-metal`).

lovish's ask (Oct 21 22:28): "a way to automatically generate maps that are intricate and sometimes changes rng with
different buffs and bonuses. The random map can give one side more neutral objectives with the other side giving more
mexes. Test this out and see what works."

His Oct 21 decisions apply:
- balanced asymmetry, with seats within about 5%
- square maps as well as round
- room kept for the hero jungle
- more metal along the edges and in empty ground
- bots never cheat
- 4v4 is the design standard

`design/maps.md` section 7 is the design summary. This file holds the numbers, what worked and what did not, the
knobs, and the open problems.

**Who wrote what.**
- This version is by the lane's own session (01CbFRWA), written at the end of Phase 1 and 2.
- An earlier version (Oct 22 00:19) was by Lane A's session (01A82Bej), which took this lane for lost. It wrote the
  first version of this file and the headless `tools/mapgen_sim`, re-ran the balance figures and found the mirror seam.
- Its tables are kept below and marked **[A]**. They were measured on `0d2ece45`, before the tuning in `625914ef`.

## Oct 27 (later): metal in BAR-style clumps

lovish, on the random spread below: "design them like beyond all reason and put clumps in strategic positions all
around the map including the back rear sides ... clump them, have stranded ones alone. Maybe a path or cluster."

**What changed** (`GenEconomy`, design/maps.md section 7 "Economy"):
- **Home clumps, the same for every seat**: a triangle (or a row of three where the wall is close) straight behind
  the base and a pair on each rear flank, 48-72 blocks out, past the 46-block build ground (the 21:37 rule "not
  connected to where the townhall spawns" stays: nothing within 40 of a start). One template in the seat's frame; the
  first distance and turn every seat's plan accepts is levelled (`GenTerrain.homePads`, a small pad at the seat's
  height) and then checked on the real ground. All 240 production maps have the full seven for every seat.
- **Contested clumps of three** on every front seam, two more on the front line in team games.
- **Groups over the open map**, seat by seat (the seat furthest short of its allowance goes next): its free spot
  farthest from all metal becomes a lone stranded patch (likelier on exposed ground; at least one a map, at most 18%),
  a path of 3-5 along the nearest road, or a clump of 2-4. Groups keep 26 apart; a group's patches 9.5-14.
- The **nudge** keeps the layout: a new patch must join one of the poorest seat's clumps (2-4 after), a patch is only
  removed when that strands no neighbour. Rich vein removes any group it touches whole.
- The 1.3x seat slack is gone (the seat-by-seat order meets each allowance directly).

**Tests:** `MetalClumpsTest` (JUnit, maps/gen), written first and red on 2c1878e1 for the right reasons (53 of 53
patches alone, no clump behind any base, 0 home patches); green now with `GeneratedMetalTest`, all 9.
Headless: `tools/mapgen_sim/run.sh junit` (a small JUnit shim; CI runs the real JUnit). The 12-map sweep
(`run.sh gametest`): 0 problems, worst spread 3.9%.

**The production check** (`run.sh lab GenProdStats prod.csv 40`, the same 240 lobby seeds; the three columns run on
the same 2-core machine, two at a time, so the times compare):

| Players, shape | ms median / p95 / max: home rings (2271184a) / random (2c1878e1) / clumps | Robust seat within 5% | Robust team within 5% | Fallback draws |
|---|---|---|---|---|
| 1v1 round | 564 / 1147 / 2251 - 594 / 1414 / 4131 - 666 / 1563 / 2866 | 0.80 / 0.76 / 0.75 | 0.80 / 0.76 / 0.75 | 0 / 4 / 5 |
| 1v1 square | 492 / 679 / 936 - 561 / 1074 / 1092 - 502 / 1025 / 1926 | 0.79 / 0.76 / 0.75 | 0.79 / 0.76 / 0.75 | 0 / 4 / 2 |
| 2v2 round | 1082 / 1306 / 1513 - 1220 / 1641 / 2564 - 1200 / 1448 / 1526 | 0.80 / 0.55 / 0.67 | 0.95 / 0.82 / 0.89 | 0 / 2 / 0 |
| 2v2 square | 1002 / 1203 / 1291 - 1184 / 1809 / 2589 - 1125 / 1340 / 1417 | 0.74 / 0.59 / 0.68 | 0.93 / 0.87 / 0.90 | 0 / 3 / 0 |
| 4v4 round | 1469 / 1654 / 1698 - 1924 / 2181 / 3628 - 1670 / 1940 / 3151 | 0.45 / 0.23 / 0.43 | 0.99 / 0.93 / 0.98 | 0 / 1 / 2 |
| 4v4 square | 1449 / 1609 / 1636 - 1940 / 3885 / 5572 - 1621 / 3229 / 3333 | 0.46 / 0.25 / 0.42 | 0.98 / 0.95 / 0.99 | 0 / 8 / 8 |
| **All 240** | p50 1036 / 1210 / 1165, p95 1592 / 2565 / 1897, max 2251 / 5572 / 3333 | | | **0 / 22 / 17** |

- All 240 maps are valid in every column; the seat spread is within 5% under the central weights on every map
  (clumps: median 1.7-3.2%, max 4.9%; 4v4 team median 0.7%). Patches a map: 57 / 81-82 / 123.
- **Robustness** (share of 40 weight draws within 5%) is back near the home-ring version: the standard home clumps are
  fairness by construction again, and the seat-by-seat spread stops the dice piling one seat's ground. 1v1 stays at
  0.75 (0.80 with the rings).
- **Fallback draws:** 17 of 240 (random: 22, rings: 0). In the draws checked they are the reach rule (a start with
  under 0.6 of the best start's metal within 160 blocks' walk); 8 of them on 4v4 square maps. Anchors past that walk
  count 0.8x (`GenEconomy.FAR_BIAS`), which halved the 1v1 redraws (16 to 7). The reach floor is still the open
  question below (0.6, 0.5 or none); with a standard home set it matters less than it did.
- 2271184a prices tier 2 at the old 7.0/s; the other two at main's 4.5/s.

The section below (the random spread) was measured before this change.

## Oct 27: random metal, tier 2 at main's numbers

lovish (Oct 27 21:37): tier 2 follows main, and "each map should have mexes spread randomly and not be connected to where
the townhall spawns".

- **Tier 2:** the score prices T2 at 4.5 metal/s after a 75 s refit (main's `MetalExtractor.T2_METAL_INCOME`,
  `TierGate.UPGRADE_EXTRACTOR_SECONDS`, 0e223c88), was 7.0 and 45 s. The refit's 300 metal is not charged, because the
  score charges no build cost at all; charging only the refit would tilt it against seats with more safe patches. The
  lab's `EconSim` refits at 300 m / 600 e, 75 s, 4.5/s.
- **Metal:** no home ring and no natural pair. The fill is a seeded random spread over the whole map, kept 0.75 of the
  territory's hole target apart, up to the same per-seat allowances (a seat may draw 1.3x its share; the balancer evens
  the rest). The nudge's new patch is drawn at random from the poorest seat's ground, not nearest its start. The build
  ground keeps 46 blocks round each start clear, as before.
- **Reach rule** (`MapGenerator.metalReach`): a draw is refused, and drawn again, when a start's nearest patch is over
  110 blocks' walk, or its metal within 160 blocks' walk is under 0.6 of the best start's. The floor is a design call
  sent to lovish: 0.6 (default), 0.5, or none.
- **Extra metal** (lobby): it was ignored on generated maps. It now scales the spread from NORMAL, which is the old
  count: NONE 0.75x, LOW 0.875x, HIGH 1.25x.

**What it costs** (production check, 240 lobby maps, the same seeds as before; `run.sh lab GenProdStats prod.csv 40`):

| Players, shape | ms median / max | Spread median / max | Team median | Robust seat within 5% | Robust team within 5% | Patches | Sites |
|---|---|---|---|---|---|---|---|
| 1v1 round | 808 / 6265 | 3.2% / 4.0% | 3.2% | 0.76 (was 0.80) | 0.76 (0.80) | 58 | 5.2 |
| 1v1 square | 618 / 1642 | 3.4% / 4.0% | 3.4% | 0.76 (0.79) | 0.76 (0.79) | 58 | 5.2 |
| 2v2 round | 1412 / 2944 | 2.8% / 4.8% | 1.6% | 0.55 (0.80) | 0.82 (0.95) | 78 | 5.3 |
| 2v2 square | 1324 / 2875 | 3.4% / 4.9% | 1.6% | 0.59 (0.74) | 0.87 (0.93) | 78 | 5.8 |
| 4v4 round | 2354 / 4908 | 3.1% / 4.8% | 1.2% | 0.23 (0.45) | 0.93 (0.99) | 120 | 5.2 |
| 4v4 square | 2440 / 7405 | 3.3% / 4.9% | 1.0% | 0.25 (0.46) | 0.95 (0.98) | 120 | 6.1 |

- All 240 maps are valid. 22 needed a fallback draw (8 at 1v1, 5 at 2v2, 9 at 4v4; none before), mostly the reach
  rule: without it, 2 of 120 did. The redraws are what lift the slowest build from 2.1 s to 7.4 s.
- Under the central weights every map is still within 5%. Under other plausible weights the random spread is less
  robust than the home rings were, most at 2v2 and 4v4 seats. The home ring was fairness by construction; the dice now
  need the balancer for all of it.
- Tighter slack (1.1x instead of 1.3x) on 120 of the same maps was within noise (4v4 seats 0.29 against 0.24).
- **Where the metal lands** (`run.sh lab GenReachStats reach.csv 20`, 120 maps): the nearest patch is 49-87 blocks'
  walk from a start (the home ring was 18), so "far from all metal" never trips on lobby maps. The poorest start's
  metal within 160 is 0.49-1.0 of the best one's before the rule; 7.5% of draws fall under 0.6.
- The 12-map sweep (`run.sh gametest`): 0 problems, worst spread 4.0%.

The older sections below were measured before this change.

## The short version

- **The generator works.**
  - Every seed draws a valid natural map, round or square, for 2 to 8 seats: reachable, level pads, no patch in
    the jungle reserve or too near the wall. This holds for 240 lobby seeds at the tip, plus the 12-map GameTest sweep
    and a 36-map offline copy of it.
  - It is deterministic, so the lobby preview is the map that is built.
  - A lobby map takes about 0.5 s at 1v1 and 1-2 s at 4v4 on one core.
- **Asymmetry plus a measured score plus a balancer gives fair maps.**
  - Each side gets a character: Prospector (more metal) against Warden (more neutral objectives), Open against
    Bastion, or Moated against Banks.
  - The lobby balancer (`SWAP_ROBUST`) holds every seat within 5% under the score's central weights: 240 of 240
    lobby maps, worst 4.0%.
  - Under other plausible weights:
    - a 1v1 map stays within 5% about 80% of the time
    - a 4v4 map's teams about 98% of the time
    - a 4v4 map's individual seats only about 45% of the time
- **The 4v4 seat figure is mostly geometry, not the asymmetry.**
  - The point-symmetric control map (Mirror, no asymmetry at all) already spreads its 4v4 seats 7.5% on round maps
    and 11% on square ones, because front seats sit next to an enemy and back seats do not. Its teams spread 0.3%.
  - At 4v4, team fairness is the bar that matters. Seat-level 5% is a stretch goal.
- **What did not work.**
  - At 4v4, re-rolling the landscape or turning the seats round is not enough on its own: 14-16% median spread, 0% of
    maps within 5%. Small metal edits (the nudge) are what close the gap.
  - The full-strength contrast is too strong at 4v4. Prospector drew 19 patches a seat against Warden's 12, a 22% raw
    spread. Team games now use the square root of the density difference.
  - Prospector/Warden is the most fragile pair under weight uncertainty, because it trades metal against objectives.
    The robust nudge (below) helps most there: 0.51 to 0.68 at 1v1.
  - The economy sim is too noisy to judge single 4v4 seats.

**Recommended defaults** (in the code now):

| Setting | Default |
|---|---|
| Lobby balancer | `SWAP_ROBUST` |
| Tolerance | 5% (nudge stops at 4%) |
| Contrast | AUTO: PW 3, OB 3, River 2, Mixed 1 |
| Team games | archetype density at its square root |
| Modifiers | On: 1-2 per match from the 7 live ones |
| Shape | Any |
| Data-only camps | priced at zero |
| Patches | at least the extractor clearance from the wall |

## How the numbers were made

The tools need only a JDK; nothing here needs Minecraft. Sources are in `tools/mapgen_sim/` and
`tools/mapgen_sim/lab/` (`run.sh lab CLASS ...`). The container had 2 cores.

**exp1** (`GenExperiments`, the Phase 1 run):
- 200 seeds per cell. Cells: 1v1 and 4v4, round and square, five contrasts (Mirror, PW, OB, River, Mixed).
- Per seed, every balancer: NONE, REROLL (up to 12 draws), NUDGE, SWAP, SWAP_NUDGE, REROLL_NUDGE (4 draws, then
  nudge).
- Each map is scored under the central weights and under 40 separate draws from the weights' ranges ("robust").
  The sim is run 12 times on the NONE, NUDGE, REROLL and SWAP_NUDGE maps.
- It ran on the generator as of `71f97be9`: before the fallback draws, live weights, team-game softening, robust nudge
  and clearance. Use it to compare strategies; the lobby's own figures are from the production check.

**Production check** (`GenProdStats`):
- 240 lobby maps through `MapLayout.build(GENERATED)`: 40 per player count (2, 4, 8) and shape, at the tip with the
  clearance.
- Live weights, robust share over 40 live weight draws.

**Tuning runs:**
- `GenRobustCmp`: SWAP_NUDGE against SWAP_ROBUST on the same swapped builds, 1v1 (100 maps) and 4v4 (64 maps).
- `tune/` (lab only): the team-game softening, measured on the exp1 seeds.

**Sim checks:**
- `GenCurves`: per-minute curves, 60 balanced 1v1 maps per contrast.
- `GenTeamBias`: 4v4 team gap, 20 maps per contrast.

**The economy sim** (`EconSim`, crude on purpose):
- 15 minutes in 1 s steps per seat: a growing worker crew builds extractors nearest first, T1 then T2 refits, and
  wind generators when energy lags.
- Spare income buys army. Objectives are contested every 20 s with lognormal noise on each side's army near them.
  Raids hit exposed patches.
- Strength = army + 120 x income at 15:00.
- It is an independent second model, not a measurement of play.

## What the generator does

Entry point: `bar/.../maps/gen/MapGenerator.java`. One build runs plan, ground, economy and objectives, ground checks,
fairness score, then the balancer.

It is deterministic:
- SplitMix64 streams per stage (`GenRng`)
- `StrictMath` trig, sqrt instead of hypot
- the lobby options in the seed's top byte (`GenSeed`)

So the lobby preview is the map built, and a save resumes the same map.

- **Shapes.** Round (ring wall) or square (straight sides, corner towers, same area).
  - Square seats sit in the corners, or in corners and on sides at 4v4.
  - `MapLayout.squareArena()`, `outerRadius()` and `distanceToArenaEdge()` answer for both shapes.
  - `BattlefieldWall.raise(..., square)` builds the square wall.
- **Ground** (`GenTerrain`, `GenNoise`).
  - A warped fractal tier field, mapped by percentile:

    | Share | Tier | Height |
    |---|---|---|
    | 5% | lakes | water |
    | 11% | valleys | 1 |
    | 44% | lowland | 4 |
    | 25% | T1 | 8 |
    | 11.5% | T2 | 12 |
    | 3.5% | crests | 16 |

  - Terraced by a cliffiness field: cliffs where it is high, walkable slopes where it is low.
  - Also: base pads, a plaza, a rim shelf, choke spurs across main roads, lakes, and forests kept off roads, pads,
    sites and bases.
  - A mirror map blends every field across a 24-block band at its fold, so there is no seam (fixed in `b42e2ef3`).
- **Rivers.** A meandering river with fords: always on River maps, on about 30% of the others. It is kept 80+ blocks
  off bases and the plaza.
- **Roads.** Each seat gets a main road to the plaza and a seam road to each neighbour. Roads are held to one block
  of rise per 3.3, through anchors (pads, plaza, fords, earlier roads), so every road is walkable and a Bastion's
  plateau has exactly its ramps.
- **Seat characters** (`Archetype`). One pair per map. The hole target is the open-ground fill's density: smaller
  means denser metal.

  | Contrast | Side A / side B | Difference |
  |---|---|---|
  | PROSPECTOR_WARDEN | Prospector (29) / Warden (46) | Prospector: dense metal. Warden: sparse metal plus two own capture sites on its forward ground |
  | OPEN_BASTION | Open (31) / Bastion (43) | Open: smooth ground, many approaches, more metal. Bastion: a plateau a tier up, cliff ring, two ramps, leaner metal |
  | RIVER | Moated (40) / Banks (34) | A river 70-90 in front of the Moated side, crossable at fords only. Banks: more land |
  | MIXED | each seat its own | |
  | MIRROR | Plain / Plain (37) | Point-symmetric control. AUTO never rolls it |

- **Metal** (`GenEconomy`).
  - Fixed patches:
    - six home patches at 18
    - one 2.0x patch per seat round the plaza
    - a natural pair 42-66 out
  - Then a hole-driven fill: the reachable spot farthest from any patch goes in, while any spot lies beyond its
    owner's hole target. The rim band (within 36 of the wall) is 20% denser.
  - Values by exposure: rich on the front line, lean behind the base.
  - Every patch:
    - sits on a level 7x7 pad, with no deep water within 7
    - is reachable from its owner and off the roads
    - stands at least the extractor clearance (11) from the wall
    - is never in a reserved zone
- **Objectives.**
  - A site on the plaza, plus a contested site on every front seam (where both neighbours walk about as far).
  - Two more on the front line in team games, and a Warden's own two.
  - Kinds: Ancient Mine, Ley Shrine, Mercenary Camp, bell, watchtower, forge, vent, oasis, garrison.
- **Jungle reserve** (for the hero slice). One wild area per side, two in big team games.
  - Its entry and exit differ every game.
  - Its camps are data only.
  - The fill leaves it alone (`GenMap.reserved()` -> `MetalOptions.reserved`).
- **Modifiers** (`Modifier`). On rolls 1-2 per match; Surprise rolls 2-3 and does not name them until the map rises.
  - Live: Rich vein (one side's ground), Ley line, Geothermal rift, Gold rush, Mercenary road, Garrison ruins, Watch
    ridge. All are built from mechanics that already exist.
  - Data only, never rolled in the lobby until their mechanic exists: Hero relic, Hydra lair (the Oct 20 "hydras in
    the middle that buff the hero who kills them"), Fog valleys.
- **Fairness score** (`Fairness`). One unit: metal per second averaged over a 15-minute game. The parts are:
  - patch income, with extractors built nearest first (T1 2.0/s, then a T2 refit to 7.0/s)
  - patches shared with rivals by walk distance, and front-line patches partly lost to raids
  - objective income: energy at `energyPerMetal`, or an estimated worth
  - defence: open approach directions
  - tempo: walk to the middle
  - space

  Spread = (best seat - worst seat) / mean. Team spread is the same over team totals.
- **Balancers** (`GenOptions.Balance`):

  | Balancer | What it does |
  |---|---|
  | NONE | The raw draw. Experiments only |
  | REROLL | A fresh landscape from a derived seed, best of up to 12 |
  | NUDGE | Small metal edits: a standard patch made rich on the poorest seat, a crowding patch removed from the richest, and so on. Stops at 80% of the tolerance |
  | SWAP | The same landscape with the seats turned round the ring and the sides' characters swapped. Best of up to 8 builds |
  | SWAP_NUDGE | SWAP, then NUDGE. The default until `625914ef` |
  | REROLL_NUDGE | 4 rerolls, then NUDGE |
  | **SWAP_ROBUST** | SWAP_NUDGE, but each edit is also judged under 8 seeded weight draws, and the nudge goes on while their mean spread is over the tolerance. **The lobby default** |

  A balanced draw that is invalid or outside the tolerance gives way to a fresh draw, up to 4. In 240 lobby maps
  none was needed.
- **Lobby.**
  - "Generated" is in the map list, with the seed shown as "#1A2B".
  - The **New** button rolls a seed. It works on any stamped map, and the multiplayer host has one too: the
    multiplayer lobby now carries a seed for every stamped map.
  - **Shape** (Any / Round / Square) and **Mods** (On / Off / Surprise) replace the Size row on a generated map.
  - The skirmish preview draws on a worker thread and names the modifiers. The multiplayer lobby has no map preview
    (it had none before).
- **Tests.** `MapGenGameTests` (batch `maps_gen`, 9 tests) checks:
  - the same seed draws the same map
  - every seat reaches every seat, the middle and its whole economy on foot
  - level pads, no deep water near a patch, and the clearance from the wall
  - reserved zones free of metal, with the jungle doors on its edge
  - seats and teams within the tolerance
  - starts on capitol ground
  - the square edge helpers and wall length
  - the options riding the seed
  - a generated map raising in its arena

  The symmetric preset tests sweep `MapPreset.handmade()` only.

## Results

### Which balancer (exp1, central weights)

1v1, 1600 maps per balancer (four asymmetric contrasts, round and square, 200 seeds each):

| Balancer | Median spread | p90 | Within 5% | Robust median | Robust within 5% | Sim spread median | ms median |
|---|---|---|---|---|---|---|---|
| NONE | 3.1% | 7.7% | 73% | 4.4% | 59% | 6.8% | 110 |
| REROLL | 2.2% | 4.3% | 100% | 3.6% | 71% | 6.2% | 123 |
| NUDGE | 2.3% | 3.6% | 100% | 3.6% | 71% | 6.1% | 111 |
| SWAP | 1.1% | 3.7% | 96% | 3.4% | 72% | | 440 |
| **SWAP_NUDGE** | **1.1%** | **2.9%** | **100%** | **3.3%** | **74%** | 5.9% | 441 |
| REROLL_NUDGE | 1.9% | 3.4% | 100% | 3.3% | 74% | | (not timed) |

On the Mirror control (1v1, 400 maps) the seat spread is 0.3% and the sim spread 1.5%.

4v4, 1600 maps per balancer (the same cells at 4v4):

| Balancer | Median spread | p90 | Within 5% | Team median | Robust median | Robust within 5% | Sim team median | ms median |
|---|---|---|---|---|---|---|---|---|
| NONE | 21.0% | 28.7% | 0% | 11.6% | 23.0% | 0% | 10.4% | 170 |
| REROLL | 14.1% | 18.2% | 0% | 8.0% | 16.3% | 0% | 7.0% | 2049 |
| NUDGE | 3.5% | 3.9% | 100% | 1.7% | 6.1% | 36% | 4.6% | 189 |
| SWAP | 16.4% | 20.5% | 0% | 8.5% | 18.3% | 0% | | 1348 |
| **SWAP_NUDGE** | **3.5%** | **3.9%** | **100%** | **1.5%** | **6.0%** | **36%** | 4.6% | 1360 |
| REROLL_NUDGE | 3.5% | 3.9% | 100% | 1.6% | 6.0% | 37% | | 699 |

On the Mirror control (4v4, 400 maps) the seat spread is a median 9.4% (7.5% round, 10.9% square) and the team
spread 0.3%. The sim's 4v4 seat spread is about 32% on every balancer, the Mirror included.

These 4v4 figures are from before the team-game softening and the robust nudge. At the tip the lobby's 4v4 maps are
robust 45% (seats) and 98% (teams): see the production check.

Reading the two tables:
- At 1v1 every balancer works. Swapping gives the most even maps for the time.
- At 4v4 only the nudge closes the gap. Every nudged 4v4 map ends just under 4.0%, the nudge's stop line.
- REROLL is the slowest at 4v4: 12 full builds.
- **[A]** found the same on `0d2ece45` with different seeds:
  - 1v1, 200 seeds: NONE 62.5% within 5%, every balancer 100%, SWAP_NUDGE mean 1.40%.
  - 4v4, 40 seeds: only the nudging balancers reach 5%; REROLL 2.5%, SWAP 2.5%.

### Robustness: the weights are guesses

The score's 20 weights each have a range: for example `energyPerMetal` 20-60 (central 35), camp worth 0.3-1.3
(central 0.6), defence 1-5 (central 2.5). Robustness is the share of 40 draws from those ranges under which a map
stays within 5%.

1v1, exp1, SWAP_NUDGE, by contrast:

| Contrast | Round | Square |
|---|---|---|
| River | 0.96 | 0.95 |
| Mixed | 0.81 | 0.79 |
| Open / Bastion | 0.69 | 0.70 |
| Prospector / Warden | 0.52 | 0.54 |

**[A]** found the same order on 100 maps: RIVER 0.94, MIXED 0.81, OB 0.67, PW 0.44.

Prospector / Warden is fragile because a Prospector's edge is metal and a Warden's is objectives. The exchange rate
between them (`energyPerMetal`, the site values) is the least certain part of the score.

**The robust nudge** judges each metal edit under 8 extra seeded draws as well. The 40 test draws are a different
set. Same swapped builds, SWAP_NUDGE -> SWAP_ROBUST:

| | 1v1 (100 maps) | 4v4 (64 maps) |
|---|---|---|
| Within 5%, all | 0.74 -> **0.82** | 0.35 -> **0.41** |
| Prospector / Warden | 0.51 -> **0.68** | 0.35 -> 0.38 |
| Open / Bastion | 0.69 -> 0.78 | 0.38 -> 0.43 |
| River | 0.94 -> 0.96 | 0.30 -> 0.41 |
| Mixed | 0.82 -> 0.86 | 0.37 -> 0.42 |
| Robust median | 3.3% -> 3.1% | 6.1% -> 5.6% |
| Time | +0% | +7% |

**Team-game softening.** At 4v4 the full contrast drew 19 patches per Prospector seat against 12 per Warden. Counting
the density difference at its square root in team games:
- raw spread 22% -> 17%
- half the nudges
- robust 32% -> 38%

### The lobby's maps at the tip (production check, 240 maps)

| Players, shape | ms median / max | Spread median / max | Team median | Robust seat within 5% | Robust team within 5% | Patches | Sites |
|---|---|---|---|---|---|---|---|
| 1v1 round | 553 / 1711 | 2.0% / 3.8% | 2.0% | 0.80 | 0.80 | 57 | 5.2 |
| 1v1 square | 472 / 1054 | 1.7% / 3.9% | 1.7% | 0.79 | 0.79 | 57 | 5.1 |
| 2v2 round | 1106 / 1263 | 2.5% / 3.6% | 1.2% | 0.80 | 0.95 | 81 | 5.5 |
| 2v2 square | 1044 / 1475 | 2.5% / 3.8% | 1.3% | 0.74 | 0.93 | 81 | 5.5 |
| 4v4 round | 1584 / 2116 | 2.9% / 3.9% | 0.5% | 0.45 | 0.99 | 119 | 5.0 |
| 4v4 square | 1461 / 1793 | 3.1% / 4.0% | 0.6% | 0.46 | 0.98 | 118 | 5.8 |

- All 240 maps are valid, and none needed a fallback draw.
- The AUTO contrast split was PW 78, OB 71, River 67, Mixed 24.
- Each of the seven live modifiers turned up 28-80 times.
- Moving the wall limit from 22 to the extractor clearance (11) changed nothing measurable: the spreads and the
  robustness are the same within noise. The open-ground fill now places patches in the rim band.

### Is the 4v4 seat spread the asymmetry or the geometry?

The Mirror control has no asymmetry. Its terrain and economy are point-symmetric, yet in exp1 its 4v4 seat spread is
a median 7.5% raw on round maps and 10.9% on square maps (5.1% and 8.0% after balancing), while its team spread is
0.3%.

Front seats differ from back seats:
- they have more frontage
- the contested sites are nearer
- the walks to the middle differ

The balancer can trade metal for these, but under other weights the trade moves.

So at 4v4:
- *team* fairness is solid: 98% of weight draws within 5%
- seat-level 5% holds under the central weights only

### The sim as a cross-check

- **Direction.** On raw 1v1 maps the sim's seat difference follows the score's: correlation 0.36 (River) to 0.58
  (OB), slope about 1.
- **Side bias after balancing** (1v1, 400 maps per pair, sim side A - side B):

  | Pair | Mean | sd | A ahead |
  |---|---|---|---|
  | Bastion - Open | +3.4% | 8.7% | 63% |
  | Prospector - Warden | -0.8% | 7.8% | 45% |
  | Banks - Moated | -1.8% | 8.4% | 42% |

  The sim thinks a Bastion is worth about 3% more than the score does: either the score underprices the plateau or
  the sim's raids overprice it. `defence` is the knob, and play should decide.
- **Curves** (1v1, 60 balanced maps per pair):
  - Warden's army leads from minute 4 (about +3%); the Prospector catches up in income around minute 12.
  - Bastion stays 2-3% ahead of Open throughout.
  - Moated is about 1-2% ahead of Banks.
  - So the characters play differently over time, as intended, and end close.
- **4v4 teams** (20 balanced maps per contrast, many sim runs, signed gap side A - side B):

  | Contrast | Mean gap | sd |
  |---|---|---|
  | Mirror | +3.5% | 3.8% |
  | Prospector / Warden | -0.2% | 6.7% |
  | Open / Bastion | +0.5% | 5.2% |
  | River | -0.4% | 5.3% |

  - No archetype side wins on average. The Mirror's +3.5% is the sim's own lean to team 0.
  - The sim's 4v4 seat spread is about 25-32% even on the Mirror, so it cannot judge single 4v4 seats.
  - The sim gives front seats (those with an enemy neighbour) about 8% more strength than back seats; the score gives
    about 1%. One of the two misprices the front seats' contested sites.

### Modifiers

A modifier moves the raw spread by under 2 points, most by under 1, and the balancer absorbs it. Robustness after
balancing is the same with or without any one modifier. On the exp1 1v1 maps it was 0.75-0.84 for every modifier.

### The mirror seam (found by [A], fixed)

The Mirror read its far half straight off the canonical half, so the fold showed as a straight cliff line. [A]
measured 538 of 1206 fold columns jumping 2+. The fields are now blended across a 24-block band.

On 16 mirror maps:
- fold columns with a 2+ step: 30-41% -> 0.4-5.3%
- a parallel control line: 0.8-1.3%

Non-mirror maps are byte-identical. A residual 0.04-0.2% of columns still differ from their point mirror, from road
and object rounding.

## Render sheets

- `tools/mapgen_sim/run.sh lab GenShowcase OUTDIR` draws three hill-shaded sheets: 1v1 and 4v4 seeds 1-8 as the lobby
  draws them, and a 4v4 raw/balanced pair per seed. They are not cherry-picked.
- The contact sheets in `design/generated-maps/` (`run.sh sheet design/generated-maps 2 6 MIRROR MIXED OPEN_BASTION
  PROSPECTOR_WARDEN RIVER` and `... 8 3 MIXED PROSPECTOR_WARDEN`) were redrawn for the BAR-style clumps (Oct 27 later);
  [A] first drew them on `0d2ece45`.

Both are the generator's own grid, not the Minecraft build.

## Known weak spots

1. **The in-game look has not been seen.** CI proves that the map raises and that its structure holds; nobody has
   looked at the block palette, cliffs or trees. The eyes tour has no generated-map scene yet.
2. **The score is a model.** Its weights are guesses with ranges, and the sim is a second, crude model. Neither is
   calibrated against play. First targets:
   - `energyPerMetal` and the site values (the Prospector / Warden trade)
   - `defence` (Bastion)
3. **4v4 seats** are within 5% under the central weights, but only 45% robustly. Teams are 98%.
4. **The nudge stops at 4.0% at 4v4.** That leaves a 20% margin to the 5% promise, and a wrong weight can spend it.
5. **Time.**
   - 1-2 s at 4v4. The lobby preview is on a worker thread.
   - The server builds the layout once in `MatchSetup` at Start, on the server thread, so expect a 1.5-2 s hitch
     there. It was not measured in game.
6. **Three modifiers and the jungle camps are data only.** Lobby maps price them at zero.
7. **The Mirror is not exact.** About 0.1% of columns differ from their point mirror.

## Numbers for lovish

These are generator knobs. No unit or economy numbers change.

| Knob | Value | Where |
|---|---|---|
| Seat tolerance | 5% (the nudge aims for 4%) | `GenOptions.tolerance` |
| Patch allowance per seat | 28 / 20 / 15 at 1v1 / 2v2 / 4v4 (home clumps and plaza patch included), times the archetype's density (its square root in team games), the square root of the lobby's richness and the extra-metal level (NONE 0.75, LOW 0.875, NORMAL 1, HIGH 1.25) | `GenEconomy.capOf`, `spread` |
| Home clumps | a triangle (or row of 3) behind the base and a pair on each rear flank at 72 deg, 48-72 out, turns up to 50 deg; 7 standard patches a seat | `GenEconomy.homeOptions` |
| Groups | lone 15% (35% on exposed ground; at most 18%, at least one a map), path of 3-5 by a road 45% (else 8%), clump 2/3/4 at 20/45/35%; patches 9.5-14 apart, groups 26 apart, anchors 1.25x the hole target apart; ground past 160 walk 0.8x | `GenEconomy` |
| Reach rule | nearest patch within 110 blocks' walk; metal within 160 at least 0.6 of the best start's | `MapGenerator.metalReach` |
| Hole targets, blocks (group anchors 1.25x) | Prospector 29, Open 31, Banks 34, Plain 37, Moated 40, Bastion 43, Warden 46 | `Archetype` |
| Rim band | within 36 of the wall, group anchors 20% closer; never closer than the extractor clearance (11) | `GenEconomy.anchor` |
| Patch values | rich at exposure 0.38-0.62 (the front line), lean below 0.3 behind the base, a 2.0x patch per seat round the plaza | `GenEconomy` |
| Warden | 2 own capture sites | `Archetype` |
| Team games | 2 extra front-line sites | `GenEconomy` |
| Modifiers | On: 1-2 per match, Surprise: 2-3 | `MapGenerator` |
| AUTO contrast odds | PW 3, OB 3, River 2, Mixed 1 | `GenPlan` |
| Map size | starts on a ring of 150 (152 at 4v4, which keeps neighbours 116+ apart); round radius = ring + 90; square of the same area | `GenPlan` |

## Open questions for lovish

1. **4v4 fairness bar.** Is "teams within 5%, seats within about 8%" acceptable? Even a perfectly symmetric 4v4 map
   spreads its seats 7.5-11% front to back.
2. **How strong should the sides feel?** Team games now soften the contrast: Prospector 17 patches a seat against
   Warden 13 at 4v4. Do you want it stronger (more flavour, leaning on the balancer) or weaker?
3. **Show the contrast in the lobby?** For example "Prospector vs Warden" under the preview, or keep it to be
   discovered like Surprise?
4. **Modifiers.**
   - Which of the seven live ones stay?
   - Should Rich vein (one side's ground gets a rich seam) count as "one side gets more mexes", or should that be its
     own contrast?
5. **Next mechanics.** The hydra lair (hero buff), hero relic or fog valleys: which first? Each is a slice, and the
   map side is ready.
6. **Square maps.** Are the seat layouts right: corners at 1v1, corners and sides at 4v4?
7. **Multiplayer lobby preview.** Worth adding? The skirmish lobby has one; multiplayer never had one.

## Next steps

1. Give the eyes tour a generated-map scene (1v1 and 4v4, round and square) and look at the built maps.
2. Calibrate the trade weights from play or the bot match runner. Then decide whether the nudge's stop line can move
   to 90%.
3. Build the first data-only mechanic. Then add it to `Modifier`'s live list; the score already has its weight.
4. Cache the lobby's generated map for Start, or build it off the server thread.
5. A multiplayer map preview, if wanted.
6. Make the Mirror exact, if it is ever offered as a "tournament" option.

## Reproduce

```
tools/mapgen_sim/run.sh lab GenExperiments OUT 2 ROUND PROSPECTOR_WARDEN 200 2 12   # one exp1 cell; all 20 cells for the tables
python3 tools/mapgen_sim/analyse.py OUT ANALYSIS [curves.csv]                       # exp1 tables and charts
tools/mapgen_sim/run.sh lab GenProdStats prod.csv 40                                # the production check
tools/mapgen_sim/run.sh junit                                                       # the maps/gen JUnit tests, headless
tools/mapgen_sim/run.sh lab GenReachStats reach.csv 20                              # nearest metal and reach per start
tools/mapgen_sim/run.sh lab GenRobustCmp robust.csv 2 25                            # 1v1 robust nudge (8 16 for 4v4)
tools/mapgen_sim/run.sh lab GenTeamBias bias.csv 20 40                              # 4v4 team gap in the sim
tools/mapgen_sim/run.sh lab GenCurves curves.csv 60                                 # 1v1 sim curves
tools/mapgen_sim/run.sh lab GenTestMirror 6                                         # MapGenGameTests' checks, 36 maps
tools/mapgen_sim/run.sh lab GenSeam seam 4                                          # the mirror fold
tools/mapgen_sim/run.sh lab GenShowcase OUTDIR                                      # the three sheets
tools/mapgen_sim/run.sh balance 2 200                                               # [A]'s balance table
tools/mapgen_sim/run.sh gametest                                                    # [A]'s 12-map sweep
```

exp1 takes about 2 hours on 2 cores. The rest take minutes.
