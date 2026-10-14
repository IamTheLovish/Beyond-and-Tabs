# Skirmish bot

How the skirmish opponent (`bot/BotPlayer`, `bot/BotArmy`, `bot/BotTuning`, `bot/BotRoles`) plays a match. The
goal is BAR's BARbarian on "hard": a real opponent that never floats, techs on a clock, raids and attacks in waves -
with the player's income, not a cheat. Each bot thinks once a second (`BotServerEvents`, staggered by name), and
every decision is re-derived from world state so a reload loses nothing but the brain's timers.

## One think, in order

1. **Capitol** above all else (laid by decree after three failed sites).
2. **Economy snapshot**: metal income, metal and energy fill (stored / storage). `metalFill > FLOAT_FRAC (0.6)` is
   *floating* - the alarm the rest of the step reacts to.
3. **Workers** from the capitol up to `targetWorkers()` = base + clock + income (`workerPerIncome`): two queued at a
   time while floating.
4. **Builders**: every unbuilt or damaged site gets the nearest free workers; idle workers **reclaim** the nearest
   wrecks within 70 blocks of home (RECLAIM orders, one worker per eight, never the commander).
5. **Build sites**, `maxConcurrentSites()` at once (+`floatSites` while floating), tried in priority order with a
   failed placement falling through to the next:
   - floating (from minute 3) -> **another army lab** first (the clock's count plus two, up to `maxLabs`);
   - energy under 30% with metal to spare -> **wind**;
   - the **first lab** from minute 1;
   - Tier 2 researched -> the **T2 lab**, then the faction's T2 defence;
   - a free **metal patch** (two plus one a minute);
   - wind on the clock, a **converter** each time energy sits over 80%, a **storage vault** only after 20 s pinned
     at the cap *with* 10+ metal/s income (a small-income bot at the cap has an idle-labs problem, not a storage
     problem);
   - farm (factions that have one), the next army lab on the clock (`labsPerMinutes`);
   - floating with the labs in hand -> **every free patch** there is; then a tower toward the enemy.

   One placement per think, so this order *is* the priority: a rule that fires every think starves the ones
   under it (an early draft put "every free patch while floating" ahead of the lab and built no lab at all).
6. **Tech**: Tier 2 at `t2Minute` (hard 3 -> the T2 lab stands by ~8) once eight workers are out (the research
   holds the capitol's queue, and workers only come from there), home extractors refitted one at a time,
   Tier 3 at `t3Minute` (hard 11 -> the T3 lab by ~18, raised by a T2 constructor), one experimental in production
   whenever 20 population is free.
7. **Abilities**: commander signature and D-gun, faction powers, titan abilities, and the per-faction habits
   (Verdant snares / owl / thickets / healers, Tidewrought fog / Krakens / Leviathans, Stormcallers spells).
8. **Production**: every lab keeps `queueDepth` units queued (repeat queue on, so it never runs dry) in a mix
   `pickArmyItem` chooses: the kit's list at random, **anti-air** while the enemy fields flyers (`queueAntiAir`),
   the role table's **siege** unit once a known enemy site is a static defence (one fighter in five). The Horde's
   lab is a Basic Portal the bot refits to a Military Portal (the Bastion only researches; the old kit never fielded
   a Brute). The T2 lab trains its constructors first (hard: two) and then T2 units.
9. **Repair**: a built building under 80% health gets an idle worker (step 4). There is no nanolathe assist of a
   lab's production in this fork (`getProductionBuildPower` is constant), so idle workers are spent on more sites
   and reclaim rather than parked at labs - an earlier draft that had them *guard* the labs froze a hard bot's
   whole base in the soak.
10. **Army** (`BotArmy.run`, below).

## Maps, start positions and the new economy (Oct 12, branch `bots-maps`)

How the bot plays the pre-made maps (`design/maps.md`), lab levels (`design/building_upgrades.md`), the economy
pace (`design/economy_pace.md`), geothermal and tree reclaim. BARbarian is the reference throughout, and none of it
cheats: the map, its patches and its sites are public, and the bot only spends the income it has.

- **Start position** (`BotStartPick`, hooked into `StartPhase.placeBots` from `StartPhase.begin`): in the live
  start-position phase a bot moves from its slot to the best spot within 32 blocks of it *inside its team's box*:
  a grid every 4 blocks, each spot scored by the patch value within 30 blocks (weight 1 at the capitol, 0 at 30),
  minus 0.01 a block from the slot and 0.05 a block of stepping toward the map centre (raid exposure). The capitol
  square must stay clear of every patch's reserved square, then the human click snap (`clearSpot`) runs on it.
  With Start positions: Auto every side keeps its slot, bots too; Random terrain stamps its patches after the phase,
  so there the bot keeps its slot as well.
- **Expansions by value over distance** (`BotPlayer.bestPatch`): the next extractor goes on the free patch with the
  lowest distance / value (1.0, 1.5, 2.0x), so a rich patch wins at equal distance and a 2.0x centre patch is worth
  walking twice as far for. The old safety rule stays (no patch next to an enemy building). The value bound lets
  the search skip most patches before the expensive building scan.
- **Raids on rich patches**: the raid target is the known, undefended enemy extractor with the lowest distance /
  patch value - a 2.0x extractor is the one that hurts.
- **Capture sites** (`BotSites`, every 5 s from `BotArmy` while it builds up or attacks, from economy minute 2):
  each site within 150 blocks (and 40 up or down) is scored worth / (1 + distance / 40), worth by kind and by need:

  | site | worth |
  |---|---|
  | Ancient Mine | 1.0 (1.3 while metal is under 30%) |
  | Ley Shrine / Nexus, Lava Vent | 0.9 (1.5 while energy is under 30%) |
  | Ancient Forge | 1.2 until Tier 3 is researched, then 0.6 |
  | Watchtower | 0.9 (prophylaxis) |
  | Sunken Bell, Mercenary Camp | 0.7 |
  | Oasis | 0.5 |

  A site we hold that an enemy is neutralizing scores x3 (defend it); an enemy-held site x1.2 (retake: it denies
  them too); a held site with fewer than two holders x0.5 (top up the garrison, while under a quarter of the army is
  on site duty). The site's threat is the enemy value within 16 blocks; the squad grows to 1.5x that (3 to take, 2
  to hold, at most 8), and a site that would need more than half the army is skipped. Squad members off the site
  (capture counts units within 6 blocks) attack-move back onto it. Fighters on site duty are left out of the rally,
  raids and waves, so a taken site stays held; a base defence (DEFEND) still pulls everyone home. This replaces the
  old `claimSite` (four idle fighters to the nearest unowned site from minute 4, never held).
- **Lab levels vs another lab** (`BotEconomy.levelBeatsNewLab`): with two labs, the lowest lab's next level when its
  metal per point of production (L2 90 m / +0.75 = 120, L3 180 m / +0.75 = 240) is under 6x a new lab's (a 45 m lab:
  270 - the premium is the lab's builder time, ground and second target, which a level does not cost), the bot can
  pay (metal a quarter full, or half the level's metal stored) and it is not defending (the lab makes nothing while it
  rebuilds: a developing move, a mistake under pressure). While a lab can still level, no new lab is laid.
- **Geothermal on lava** (`BotEconomy.placeGeothermal`): after the T2 lab, with a T2 constructor out, a Geothermal
  Plant on the nearest free lava pool within 64 blocks of home (the maps' vents, the rift's edge, natural pools),
  one site at a time. Lava is found by a column read every 3 blocks (a 3x3 vent can't slip between), once a minute,
  loaded chunks only.
- **Trees when energy-starved** (`BotEconomy.fellTrees`): energy under 15% with metal to spare -> one idle worker
  fells the nearest three trees within 32 blocks of home (the area-reclaim orders), at most every 15 s.
- **Decommission** (`BotEconomy.decommissionObsolete`): from economy minute 12, with Tier 2, a pool pinned at its cap
  for 30 s, energy over 90% and a Geothermal Plant (or two converters) standing, the furthest wind generator is
  decommissioned (one at a time, four always kept): its energy goes nowhere, and the ground and 60% of its metal come
  back. Labs are never sold - a capped bot is short of spending, and selling a door makes that worse. A building
  being decommissioned no longer counts as a lab and gets no orders.
- **Economy pace**: unchanged - the build-order clock already runs in economy minutes (`paceMinutes`), and the new
  minute gates (sites from 2, decommission from 12) read the same clock.

**Soak** (`[Soak]` notice, 4 game-minutes, random terrain so no map sites; random seed each run - read as noise
bands, not deltas): main 30a1b6f -> bots-maps 4fa5b34: hard Sunforged 21 -> 17 buildings, 2 -> 2 labs, army value
max 275 -> 280, metal fill at the end 0.67 -> 0.23; the five medium bots 14-16 buildings, 1 lab and army value
270-430 before, 14-15 buildings, 1 lab and 180-420 after; no placement failures and no think errors either side.

**Cost at 16 bots**: the site step is a pass over the site list plus one grid query per real candidate every 5 s;
the lava scan ~1800 heightmap reads a minute and only after T2; the tree scan only while starved, every 15 s; the
start pick once per match. Nothing runs per tick.

## Army state machine (`BotArmy`)

Everything is measured in **army value**: the summed metal cost of living fighters (scouts excluded), as the match
history counts it, so ten T2 units weigh like thirty T1.

```
BUILD_UP  fighters gather at the rally point (30 blocks ahead of home toward the enemy); raids and scouting go out;
          value >= attackValue(minute) and the wave cooldown up (or value >= 2.5x the threshold, cooldown or not)
          and a target known                                                        -> ATTACK
ATTACK    attack-move on the target (nearest known enemy capitol, else nearest known site); stragglers and
          reinforcements rejoin every 10 s; a fallen target is replaced by the next known one, none left -> BUILD_UP;
          value under 60% of what the wave set out with AND enemy value within 24 blocks of the army's centre over
          retreatRatio x ours                                                       -> RETREAT
RETREAT   Return fire, walk to the rally point; there (or after 20 s)                -> BUILD_UP (Fire at will)
DEFEND    from any state: enemy value within 45 blocks of home over defendValue (or half our army), any of our
          buildings losing health, or the commander hit in the last 3 s -> everyone attack-moves onto the nearest
          intruder; 5 s after the last -> BUILD_UP. The attacker of a hit building (its lastAttacker) or of the
          commander is the intruder even when the flat home scan cannot see it - a flyer above the 24-block height
          band, artillery past 45 blocks (lovish's Oct 13 2v2: a Celestial Dragon razed the Sunforged base while
          forty fighters stood in rows beside it). A flying intruder is met on the ground under it. A wave past
          2.5x the threshold keeps going only while its centre is actually away (over 67 blocks from home); an
          army standing at home is a home guard whatever its state says. Anything smaller at home (a scout, a
          lone raider) is met by the three nearest idle fighters - the old bot recalled its whole army for any one
          enemy within 45 blocks, which is why it never pressured anyone.
```

**Minimum army** (`BotArmy.MIN_FIGHTERS` = 6): no raid, no scout trained, no capture-site squad until six fighters
are alive; before that each fighter would walk out alone and die (the Oct 13 ally hard bot: 33 lost, 0 kills, army
value 0 for 28 minutes). A wave only ever launches above the threshold anyway. A bot at army value 0 for two minutes
logs `[Bot] <name> army: value 0, units N, lost L, state S, <why>` once a minute (BotPlayer.logNoArmy): no lab placed
(with the last blocked reason), a lab unbuilt, a built lab with an empty queue, or the army step's own reason.

**The lab always goes up somewhere.** The first lab is wanted at its layout slot 12 blocks toward the enemy; when
that fails (no valid ground within 32 blocks, or a site the watch cancelled because its builders never reached it),
`labSlotShift` moves the next try to the next of six ARMY slots - the front row, the two flanks, then well behind
the wind field - and the cancelled ground is skipped for four minutes (`badSites`). Until a lab stands, a bot past
minute 3 keeps only `workerBase + 3` workers: an economy with no army behind it is just workers waiting to be farmed.

**Raids**: every `raidCooldownTicks`, the `raidSize` fastest idle fighters (not siege) attack-move to the best
(distance over patch value) known enemy extractor that has no known defence or capitol within 30 blocks. **Scouting**: one scout-role unit
(the faction's cheapest and fastest, trained at the capitol every `scoutEveryTicks` when the last is dead) runs on
Hold fire to the nearest enemy building not yet seen, else sweeps a 100-block ring round home. **Stances**: Fire at
will for waves, defence and raids; Return fire on a retreat; Hold fire for the scout.

**No map hack.** A wave or raid only targets `knownSites`: enemy buildings that one of the bot's units (sampled to
~16) or buildings has come within 28 blocks of, refreshed every 5 s and forgotten when destroyed. The scout's
destination and the fallback after `blindAttackMinute` (minute 6) do use the global building list, because start
positions are public in the lobby and a bot that never finds the enemy is no opponent.

**Cost**: per think, one grid query round home, one round the army's centre only while a wave is out, the sightings
pass (a few hundred flat distances) every 5 s, and no per-tick work at all - 16 bots at 8v8 stay well under the
stress budget.

## Knobs (`BotTuning`)

| knob | easy | medium | hard |
|---|---|---|---|
| t2Minute / t3Minute | 7 / never | 5 / 14 | 3 / 11 |
| workers: base + per 2 min + per income, cap | 5 + 0, 10 | 7 + 1 + 1/3 m/s, 20 | 9 + 2 + 1/2 m/s, 36 |
| build sites (+ while floating) | 1 (+1) | 2 (+1) | 3 (+2) |
| labs: one per N min, max | 6, 3 | 4, 6 | 3, 12 |
| queue depth per lab | 2 | 2 | 3 |
| wave at army value: base + per min, cap | 600 + 30, 1500 | 900 + 50, 2600 | 1100 + 70, 3800 |
| wave cooldown | 240 s | 170 s | 100 s |
| retreat when outweighed | never | 2.0x | 1.8x |
| raids: every, squad | none | 150 s, 3 | 90 s, 4 |
| scout errand every | never | 180 s | 120 s |
| defend at enemy value at home | 150 | 150 | 180 |

Difficulty is in the bot's name ("Bot3 (hard)") and read back after a reload, so a hard bot stays hard. The lobby's
labels are unchanged.

## Tests (`gametest/SkirmishGameTests`)

- `bots_play_each_other_without_errors`: the four-minute soak; the Sunforged bot plays on hard, starts floating
  (metal at the storage cap) and must end under FLOAT_FRAC of storage or with a second lab. Each bot's peak army
  value, labs and end metal fill are logged as `[Soak]` lines, folded into one CI notice.
  Measured (CI, 4 game-minutes, random seed each run): the hard Sunforged bot went from 16 buildings / 1 lab /
  army value 90 / metal 0.34 of storage (old brain) to 17 buildings / 2 labs / army value 375 / metal 0.22.
- `bot_army_attacks_when_its_value_crosses_the_threshold`: 950 metal stays in BUILD_UP, 1140 launches at the known
  site and every fighter carries it as its attack-move target.
- `bot_army_pulls_back_to_defend_its_base`: 285 metal of enemy at home flips a wave to DEFEND and onto the intruder.
- The soak plays as teams (Sunforged + Gravebound, Court + Tide, Storm + Brass): the allied hard bot must have a lab
  placed and a fighter trained (or a lab finished) by four minutes.

`gametest/BotGameTests` (maps and the new economy):
- `bot_takes_a_nearby_capture_site`: six Vindicators and a neutral Watchtower 12 blocks from home; the real capture
  loop makes the bot its owner within 72 s of game time, with a squad on site duty.
- `bot_army_defends_its_base_against_a_flyer`: twenty idle Vindicators, a Ghast 30 blocks up (outside the home scan's
  height band) hits the Town Centre: DEFEND, and every fighter attack-moves to the ground under the Ghast.
- `bot_prefers_a_rich_patch_at_equal_distance`: `bestPatch` takes 1.5x over 1.0x at equal distance, 2.0x at 36 over
  1.0x at 20, but 1.0x at 20 over 1.5x at 40.
- `bot_places_its_start_inside_its_box_near_metal`: on Highlands 1v1 the start is inside the box, clear of every
  patch, scores at least the slot's, and moves toward two 2.0x patches added to one side.
- `bot_upgrades_a_lab_with_two_labs_and_spare_metal`: one lab, starved metal or a defence -> no level; two labs and
  spare metal -> exactly one Level 2 queued, and no second while it runs.
