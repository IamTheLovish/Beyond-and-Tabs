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
   - floating -> **another army lab** first (up to `maxLabs`; also one more lab whenever floating, every lab busy or
     not);
   - energy under 30% with metal to spare -> **wind**;
   - Tier 2 researched -> the **T2 lab**, then the faction's T2 defence;
   - a free **metal patch** (two plus one a minute - every patch while floating);
   - wind on the clock, a **converter** each time energy sits over 80%, a **storage vault** only after 20 s pinned
     at the cap *with* 10+ metal/s income (a small-income bot at the cap has an idle-labs problem, not a storage
     problem);
   - farm (factions that have one), the next army lab on the clock (`labsPerMinutes`), a tower toward the enemy.
6. **Tech**: Tier 2 at `t2Minute` (hard 3 -> the T2 lab stands by ~8), home extractors refitted one at a time,
   Tier 3 at `t3Minute` (hard 11 -> the T3 lab by ~18, raised by a T2 constructor), one experimental in production
   whenever 20 population is free.
7. **Abilities**: commander signature and D-gun, faction powers, titan abilities, and the per-faction habits
   (Verdant snares / owl / thickets / healers, Tidewrought fog / Krakens / Leviathans, Stormcallers spells).
8. **Production**: every lab keeps `queueDepth` units queued (repeat queue on, so it never runs dry) in a mix
   `pickArmyItem` chooses: the kit's list at random, **anti-air** while the enemy fields flyers (`queueAntiAir`),
   the role table's **siege** unit once a known enemy site is a static defence (one fighter in five). The Horde's
   lab is a Basic Portal the bot refits to a Military Portal (the Bastion only researches; the old kit never fielded
   a Brute). The T2 lab trains its constructors first (hard: two) and then T2 units.
9. **Lab assist**: workers with nothing left to do guard the labs round-robin (they repair under fire and stand
   where the next building goes); `assignBuilders` pulls them onto new sites.
10. **Army** (`BotArmy.run`, below).

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
DEFEND    from any state: enemy value within 45 blocks of home over defendValue (or half our army), or any of our
          buildings losing health -> everyone attack-moves onto the nearest intruder; 5 s after the last -> BUILD_UP.
          A wave past 2.5x the threshold on the enemy's doorstep keeps going. Anything smaller at home (a scout, a
          lone raider) is met by the three nearest idle fighters - the old bot recalled its whole army for any one
          enemy within 45 blocks, which is why it never pressured anyone.
```

**Raids**: every `raidCooldownTicks`, the `raidSize` fastest idle fighters (not siege) attack-move to the nearest
known enemy extractor that has no known defence or capitol within 30 blocks. **Scouting**: one scout-role unit
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
- `bot_army_attacks_when_its_value_crosses_the_threshold`: 950 metal stays in BUILD_UP, 1140 launches at the known
  site and every fighter carries it as its attack-move target.
- `bot_army_pulls_back_to_defend_its_base`: 285 metal of enemy at home flips a wave to DEFEND and onto the intruder.
