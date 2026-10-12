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

## Next
1. Baseline CI run (main numbers + new rows) -> record in balance.md.
2. Fix broken relations / outliers with small number changes, batch CI runs.
3. Hero and T3 rule check, balance.md before/after + RPS matrix.

## Known failures
- none yet
