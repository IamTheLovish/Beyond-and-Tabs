# balance-pass - progress

Branch `balance-pass` (based on main 102ba10). Scope: stat numbers, costs, the duel simulator, balance tools and
`design/balance.md`. No ability code.

## Done
- `tools/duel_summary.py`: notices are packed into <=3800-char chunks (GitHub truncates an annotation at 4096: on main
  the T1 notice lost its last ~10 rows), sibling `duel_results_*.json` files are merged, counter rows get ok / EVEN /
  BROKEN, mix rows ok / SPREAD (>1.5x), `--from-notices` reads any tier name.
- `gametest/DuelGameTests`: melee diagnostic pads moved out of the duel matrix (at 3600 south they sat between duel rows
  12-14); new batch `duel_counters` (its own test, its own pads east, its own result file) with the rock-paper-scissors
  rows (open field: front ranks ~22 blocks apart), equal-metal best-T1-mix fights between all factions, and level-5
  heroes against 2 and 3 Iron Golems.

- Baseline run 38222390183 (68a7110, main numbers + new rows): 376 duels, 41 counter/mix rows off target.
  skirmisher>line BROKEN 12/14, artillery>line BROKEN 6/6, raider>artillery ok 9/9, aa>air ok 6/6, line>raider ok 8/13,
  raider>skirmisher ok 8/12; mixes outside 1.5x: 11/21 (Brassworks loses all, Verdant weak, Gravebound/Tidewrought top);
  level-5 heroes lose to 2 Iron Golems (hero rows merged 2 and 3 Golems: label fixed).
- Batch 1 (stats): line ranged resist down (Vindicator/Zombie/Husk/Drowned/Brute 0.2->0.1, Reef 0.45->0.3), Sentinel
  dmg 8->10 + 190->170M, Thornbow dmg 4->5, Iron Warden 150->125M, Steam Runner 95->85M, Cutlass 80->85M, Pillager
  90->80M, Headhunter 70->65M, Pillager/Skeleton/Stray/Headhunter speed 0.24->0.26; mixes filled closest to budget.

- Batch-1 run 38226490250 (5ec119a): mixes all within 1.5x except Brassworks (loses all 1.6-2.0x); line>raider 9/13
  (rest even), raider>skirmisher 8/12 (Spider, Marauder lose), skirmisher>line 2/14 (margins down from up to 3.2x to
  1.2-2.2x), artillery>line 0/6; Sun Colossus now beat the T2 mix (rule c); L5 heroes lose to 2 Golems (Arch-Engineer
  beats 2, splits 3).
- Batch 2: lines (Vindicator/Zombie/Husk/Drowned/Brute) speed 0.28->0.25, Spider/Poison Spider 75->65M, Marauder
  165->145M, Rifle Squad 85->75M, Sun Colossus HP 1200->1100, heroes (not Arch-Engineer) +~20% health and damage.

- Batch-2 run 38229756311 (7a84d6e): mixes 19/21 within 1.5x (Horde v Verdant 1.6x, Storm v Brass 1.5x); line>raider
  10/13, raider>skirmisher 6/12, skirmisher>line 3/14, raider>artillery 9/9, aa>air 6/6, artillery>line 0/6; T3 rule (c)
  holds 6/6; L5 heroes now about even with 2 Golems and lose to 3; Zombie still 0-10 in the T1 matrix.
- Batch 3: Zombie/Husk/Drowned HP 40->45, Marauder speed 0.28->0.31.

- Batch-3 run 38233016657 (9952c21): mixes 18/21, line>raider 10/13, raider>skirmisher 8/12, skirmisher>line 3/14,
  T3 (c) 6/6; Zombie still 0-10 (left alone, documented); heroes ~1.5 T2 units. The bar job failed only on
  HeroGameTests.each_faction_hero_trains_at_its_lab (arch_engineer: no hero spawned at the lab - a spawn-position flake,
  passed in every other run, hero code untouched by batch 3).
- Batch 4 (with the docs): heroes step 2 to just under the hero test's 2.5-Tesla cap (Paladin 200/9, Gravecaller 165/8,
  Druid 165/7, Corsair 185/9). design/balance.md rewritten (summary, RPS matrix before/after, mixes, heroes, T3, full
  matrix); balance_audit.py change lists split into this pass / earlier passes.

- Batch-4 run 38236529998 (b32d8d1): all green. Heroes: Paladin beats 2 Golems 2-0, Corsair and Druid win on value,
  Gravecaller and Arch-Engineer even, Warchief/Storm Witch lose; all lose to 3 (~2 T2 units). Mixes 19/21 (Brassworks
  loses to Verdant and Stormcallers 1.7x). Sun Colossus drew 1-1 again at 1100 HP.
- Batch 5: Sun Colossus HP 1100->1000.

- Final run 38238801777 (6e235b8): all green. design/balance.md Duel results rebuilt: before = 38222390183, after =
  38236529998 + 38238801777 pooled (4 fights per matchup; Sun Colossus row from the last run only).

## Next
- Done. Open items for a later pass: Brassworks mix on the low side, Zombie 0-10 in the close T1 matrix, Gravecaller /
  Warchief / Storm Witch under two T2 units at level 5 (stat cap reached: abilities), skirmisher>line and artillery>line
  need micro or static targets the duel lacks.

## Known failures
- each_faction_hero_trains_at_its_lab flaked once (run 38233016657).
