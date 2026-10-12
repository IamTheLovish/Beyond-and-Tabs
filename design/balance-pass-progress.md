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

## Next
1. Read batch-2 CI run; then balance.md before/after + RPS matrix.
2. Fix broken relations / outliers with small number changes, batch CI runs.
3. Hero and T3 rule check, balance.md before/after + RPS matrix.

## Known failures
- none yet
