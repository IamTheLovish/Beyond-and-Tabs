# Generated maps: report

PR #131, branch `claude/maps-generated` (draft, based on #122 `claude/maps-m1-metal`).
Written Oct 22 from the PR body, the commit messages and the code on the branch.
lovish's ask (Oct 21 22:28): "a way to automatically generate maps that are intricate and sometimes changes rng with
different buffs and bonuses. The random map can give one side more neutral objectives with the other side giving more
mexes." His Oct 21 decisions: balanced asymmetry, seats within about 5%, square maps as well as round, room kept for
the hero jungle.

## How the numbers in this report were made

Every number below is marked **re-run** or **carried over**.

- **Re-run** means I ran it on the branch in this session with `tools/mapgen_sim/run.sh`. It needs only a JDK. It
  compiles `maps/gen` (plus `MapLayout`, `MapPreset`, `MetalOptions`, and a stub of `CapturePointServerEvents.Kind`)
  and runs without Minecraft or Gradle. The container had 2 cores.
- **Carried over** means it came from the PR work before this session and I could not reproduce it from what is on
  the branch.

The Phase 1 experiment harness was never committed. The new `tools/mapgen_sim/` is a fresh one. Its seeds and its
definitions differ from the lost harness, so some numbers differ a little. Where they do, both are shown.

## What the generator does

Entry point: `bar/src/main/java/com/solegendary/reignofnether/maps/gen/MapGenerator.java`. One build runs in this
order: plan, ground, economy and objectives, ground checks, fairness score, balancer. It is deterministic (SplitMix
streams in `GenRng.java`, `StrictMath` trig, sqrt not hypot). The lobby preview is therefore the map that is built.

- **Shapes.** Round (ring wall) or square (straight sides, corner towers). `GenPlan.java` picks the shape; a square
  has the same area as the round map (`SQUARE_SIDE` 0.886). Square seats sit in corners or on sides, by seed.
  `BattlefieldWall.java` raises the square wall. `MapLayout.java` carries `squareArena()`, `outerRadius()` and
  `distanceToArenaEdge()`.
- **Ground** (`GenTerrain.java`, `GenNoise.java`). Natural, unfolded terrain in height tiers, with cliffs and ramps.
  Also choke spurs, lakes and a rim shelf. A plateau base stands +4 above the lowland.
- **Rivers.** A meandering river with fords. Always for the RIVER contrast, in about 30% of the other maps. It is
  pushed at least 80 blocks off every base and off the plaza (`GenPlan.riverLine`).
- **Lanes.** Every seat gets a main road to the plaza and a seam road to each neighbour (`GenPlan.roads`). The
  checks guarantee every seat reaches every other seat and the middle by walking (`MapGenerator.check`).
- **Metal** (`GenEconomy.java`). Home patches, centre patches, natural and fill patches, veins. The open-ground fill
  uses the M1 reach rule and level 7x7 pads. Richness comes from the lobby Metal setting. A patch the ground does not
  reach is dropped, with its twin.
- **Objectives.** Capture sites on the front seams (contested) and a Warden's own sites. The kinds are Ancient Mine,
  Ley Shrine, camp, bell, watchtower, forge, vent, oasis and garrison. Lava vents come with the Geothermal modifier.
- **Jungle reserve.** `GenMap.Jungle`: a few discs, an entry and an exit on the edge, and the side it lies on. The
  metal fill leaves it alone (`GenMap.reserved()`). Camps inside are data only (CREEP, RELIC, and a rare HYDRA lair).
- **Modifiers** (`Modifier.java`). 1 or 2 are rolled per match when on (2 or 3 on Surprise).
  - Live today (the lobby rolls only these): Rich vein, Ley line, Geothermal rift, Gold rush, Mercenary road,
    Garrison ruins, Watch ridge.
  - Data only, need a new mechanic: Hero relic, Hydra lair, Fog valleys.
- **Seat archetypes** (`Archetype.java`). Contrasts pair them. Each has a metal-density target (`holeTarget`: smaller
  is denser metal).

  | Contrast | Seat A / Seat B | What differs |
  |---|---|---|
  | PROSPECTOR_WARDEN | PROSPECTOR / WARDEN | Prospector: dense metal (29). Warden: sparse metal (46) plus 2 own neutral sites |
  | OPEN_BASTION | OPEN / BASTION | Open: smooth ground, many approaches. Bastion: plateau pad, cliff ring, few ramps |
  | RIVER | MOATED / BANKS | A river splits the map; one side sits behind it |
  | MIXED | each seat random from PROSPECTOR, WARDEN, OPEN, BASTION, PLAIN | |
  | MIRROR | PLAIN / PLAIN | Point-symmetric control, the tournament map. Falls back to MIXED if the sides are uneven |

  AUTO picks by seed with weights 3 / 3 / 2 / 1 (PW, OB, RIVER, MIXED). MIRROR is never rolled by AUTO.
- **Fairness score** (`Fairness.java`). One unit: metal per second averaged over a 15-minute game. Parts: patch
  income (T1 2.0/s, T2 7.0/s after a refit), objective income, defence (how many approach directions are open), tempo
  (walk to the middle), space. The spread is (max - min) / mean over the seats. The weights are guesses with ranges
  (`Weights.sample`).
- **Balancers** (`GenOptions.Balance`, `MapGenerator.java`):

  | Balancer | What it does |
  |---|---|
  | NONE | The raw draw. Experiments only |
  | REROLL | Draw again from a derived seed, up to 12 tries |
  | NUDGE | Small metal edits: make a patch rich or lean, add or remove a fill patch. Up to 48 steps, stops at 80% of the tolerance |
  | SWAP | Same landscape, seats turned round the ring and the sides' characters swapped. Best of up to 8 |
  | SWAP_NUDGE | SWAP, then NUDGE. **The default** |
  | REROLL_NUDGE | 4 rerolls, then NUDGE |

  If a balanced draw is invalid or still outside the tolerance, `generate` tries up to 4 fresh draws and keeps the
  best.
- **Lobby** (`SkirmishSetupScreen.java`, `GenSeed.java`). "Generated" is in the map list. A **New** button rolls a new
  seed for any stamped map. **Shape** (Any / Round / Square) and **Mods** (On / Off / Surprise) sit in the Size row.
  The options ride the top byte of the seed, so no packet or save changes. The preview draws on a worker thread and
  names the modifiers. `MapPreset.GENERATED` is appended to the enum.
- **Tests.** `MapGenGameTests.java` (batch `maps_gen`): determinism, every seat reaches everything, level pads,
  reserved zones, seats within tolerance, square edge helpers, options ride the seed, layout carries the generator's
  map. The symmetric preset tests sweep `handmade()` maps only.

## Balance results

### Seat spread by balancer (re-run)

Setup: `tools/mapgen_sim/run.sh balance N SEEDS`. Default options (either shape, modifiers on, contrast AUTO, live
modifiers only, 5% tolerance, central weights). Seeds `0x5EED000000 + 7919 i`.

1v1, 200 seeds:

| Balancer | Within 5% | Mean spread | Worst |
|---|---|---|---|
| NONE | 63.0% | 4.55% | 50.9% |
| REROLL | 100% | 2.28% | 4.98% |
| NUDGE | 100% | 2.23% | 3.99% |
| SWAP | 100% | 1.36% | 4.88% |
| **SWAP_NUDGE** | **100%** | **1.37%** | **3.91%** |
| REROLL_NUDGE | 100% | 1.86% | 3.99% |

- All 200 maps are valid under every balancer.
- NONE is 63% here. The earlier run recorded 68-80% for the unbalanced draw. The seeds differ, so I expect the
  gap to be seed luck. By contrast, my NONE rates were MIXED 73%, RIVER 63%, PROSPECTOR_WARDEN 61%, OPEN_BASTION 60%.

4v4, 40 seeds (new in this report):

| Balancer | Within 5% | Mean spread | Worst |
|---|---|---|---|
| NONE | 0% | 21.5% | 33.2% |
| REROLL | 0% | 10.9% | 13.7% |
| NUDGE | 100% | 3.31% | 4.00% |
| SWAP | 0% | 12.5% | 19.5% |
| **SWAP_NUDGE** | **100%** | **3.49%** | **4.00%** |
| REROLL_NUDGE | 100% | 3.37% | 4.00% |

At 4v4 only the nudge makes the seats even. Reroll and swap alone cannot. Every nudged map ends at 4.00%, which is
the nudge's own stop line (80% of 5%).

### Robustness under weight uncertainty (re-run, definition mine)

The weights in the score are guesses. For each 1v1 map I drew 40 weight sets from their ranges
(`Weights.sample`, 100 maps) and measured the share of draws in which the spread stays within 5%.

| Contrast (maps) | NONE | SWAP_NUDGE |
|---|---|---|
| MIXED (11) | 0.63 | 0.83 |
| OPEN_BASTION (23) | 0.58 | 0.70 |
| PROSPECTOR_WARDEN (39) | 0.40 | 0.43 |
| RIVER (27) | 0.63 | 0.93 |
| All | 0.53 | 0.67 |

- PROSPECTOR_WARDEN is the weakest, as the earlier run found (about 0.5 then). Balancing barely helps it (0.40 to
  0.43), while the other contrasts gain 0.1 to 0.3.
- Why: a Prospector's edge is metal and a Warden's edge is neutral objectives. The score trades the two at
  `energyPerMetal`, `camp`, `garrison` and the other site weights. Those weights are the least certain, so the
  balance moves with them.
- My read, not measured here: the balancer fits the central weights, so under other weights a map can sit outside
  5% even though it looks fair under the central ones.

### Other figures

| Figure | Value | Status |
|---|---|---|
| Plain-Java port of the GameTest sweep (`run.sh gametest`: 2/4/8 seats, the 4 test seeds, 12 maps) | 0 problems, worst seat spread 3.98%, team spread within 5% | **Re-run.** The test code itself was not run: it needs the game, so CI runs it |
| In-repo GameTest mirror, "24 maps, 0 problems, worst 4.0%" | worst spread 4.0% matches my 3.98% | Carried over. The test sweeps 12 maps, so I could not explain "24" |
| Econ-sim side bias after SWAP+NUDGE, up to 1.3% | not reproducible | **Carried over.** No economy simulation exists on the branch. The score is the only model |
| Build time at 4v4, about 145 ms | one raw build (NONE) took a median 216 ms, p90 402 ms. The default balanced generate took a median 1.47 s, p90 1.60 s. 1v1: raw 129 ms, balanced 517 ms | **Re-run** (2-core container, 30 builds after warm-up). 145 ms matches a raw build on a faster machine, not the balanced default |

## Render sheets

Made. A headless top-down renderer (plain `java.awt`, no Minecraft) is in
`tools/mapgen_sim/src/.../MapSheet.java`. Run `tools/mapgen_sim/run.sh sheet OUTDIR PLAYERS MAPS CONTRAST...`.

How to read them: green is grass, dark green is forest, orange is mesa, grey is cliff, blue is water, tan lines are
roads. Gold and cyan dots are metal patches by the owning team (big = rich or centre, small = lean). White diamonds
are capture sites, magenta triangles are camps, orange squares are vents. White circles are jungle reserves. Seat
discs show the archetype letter ($ Prospector, W Warden, O Open, B Bastion, M Moated, K Banks, P Plain). The
caption gives the seed's low hex digits, shape, contrast, seat spread, patch and site counts, and the modifiers.

- 1v1, 6 seeds each (alternating round and square):
  - [Prospector / Warden](generated-maps/sheet-2p-prospector-warden.png)
  - [Open / Bastion](generated-maps/sheet-2p-open-bastion.png)
  - [Moated / Banks (river)](generated-maps/sheet-2p-river.png)
  - [Mixed](generated-maps/sheet-2p-mixed.png)
  - [Mirror control](generated-maps/sheet-2p-mirror.png)
- 4v4, 3 seeds each:
  - [Prospector / Warden](generated-maps/sheet-8p-prospector-warden.png)
  - [Mixed](generated-maps/sheet-8p-mixed.png)

This is the generator's own grid. It is not the Minecraft build. The look in game (block palette, trees, cliffs)
still needs the CI eyes tour.

## Known weak spots

1. **Prospector / Warden is fragile.** It is the most common contrast (36% of AUTO rolls) and has the lowest
   robustness (0.43). The cross-resource trade is the cause.
2. **At 4v4 only the nudge balances, and it stops at exactly 4.0%.** Reroll and swap never reach 5% on their own.
   The margin to the 5% promise is a fixed 20%. A wrong weight can spend it.
3. **Balanced generation is slow at 4v4.** The default takes about 1.5 s in my container, about 7 times a raw build.
   It runs on a worker thread in the lobby preview but probably blocks the server at match start. I did not
   measure the match-start path.
4. **The score is a model, not a measurement.** No economy simulation backs it on the branch. The earlier "econ-sim
   at most 1.3%" figure cannot be checked from here. The weights are guesses with ranges and none of them is
   calibrated against play.
5. **The MIRROR control has a seam.** It reflects every field across the line through the centre that is perpendicular
   to the sides' axis (`GenTerrain.sym`). In 6 round mirror maps, 538 of 1206 columns along that line had a height
   jump of 2 or more against its neighbours. Away from the line it was 6 of 1206. A Prospector / Warden map, checked the same way, showed
   none (8 of 1206); I did not check the other contrasts. The sheets show it as a straight line through the middle. The mirror is also not exact: up to
   332 of 40401 columns differ from their point mirror, and the seat spread is 0.1-0.5%, not 0.
6. **Three modifiers and the camps are data only** (Hero relic, Hydra lair, Fog valleys; creep and relic camps). They
   place spots but do nothing in game. The fairness score still prices camps, hydra and relics, so those weights
   count for nothing until the mechanics exist.
7. **Doc gap.** Code comments in `MapGenerator`, `GenMap` and `GenOptions` point to `design/maps.md` "Generated
   maps" and to "the report". `design/maps.md` has no such section on this branch. This file is the report.
8. **Untested in game.** The in-engine GameTests were not run here, and the look of the built map has not been seen.

## Recommended next steps

1. Run the `maps_gen` GameTests and the eyes tour on this branch in CI, and look at a generated 1v1 and 4v4 in game.
2. Raise robustness for Prospector / Warden before it ships as the main contrast. Options: balance against the worst
   of several sampled weight sets instead of the central set; or give a Warden a visible edge that is not a metal
   trade; or lower its AUTO weight until it is better.
3. Build a small economy simulation (or use the bot match runner) to calibrate the weights. The first targets are the
   site values, `energyPerMetal` and `riskMax`.
4. Fix the mirror seam, either by blending the fields across the line or by making them even there. Then make the
   mirror exact.
5. Make the 4v4 balance cheaper or move it off the match-start path: cache the generated map from the lobby preview,
   or cap the swap options at 4v4.
6. Give the nudge its margin back at 4v4: stop at 90% of the tolerance if the score is trusted, or keep 80% and say so
   in the lobby.
7. Build the data-only mechanics (hero relic, hydra lair, fog valleys) one at a time, then add them to the lobby's
   live list.
8. Write the "Generated maps" section in `design/maps.md`, linking this file.

## Reproduce

```
tools/mapgen_sim/run.sh balance 2 200
tools/mapgen_sim/run.sh balance 8 40
tools/mapgen_sim/run.sh robust 2 100 40
tools/mapgen_sim/run.sh time 8 30
tools/mapgen_sim/run.sh gametest
tools/mapgen_sim/run.sh sheet design/generated-maps 2 6 PROSPECTOR_WARDEN OPEN_BASTION RIVER MIXED MIRROR
tools/mapgen_sim/run.sh sheet design/generated-maps 8 3 PROSPECTOR_WARDEN MIXED
```

Plain JDK 17 or newer, no Minecraft. The first two take a few minutes on 2 cores. `time` should be run alone.
