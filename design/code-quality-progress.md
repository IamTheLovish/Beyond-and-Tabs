# code-quality - progress

Branch `code-quality`, based on origin/main 30a1b6f. Findings live in `design/code_review.md`.

## Done
- Merge audit of e211f6a..30a1b6f (economy pace/batch/trees, building levels/annexes/decommission, HUD declutter,
  gothic splash, lively buildings, maps/start phase, Brassworks T2, hero possession).
- Fixes: CR-01 (no production on a decommissioning lab), CR-02 (decommission saved/restored), CR-03 (Repeat toggle
  DataType registered before the save is decoded), CR-04 (Backspace with an enemy unit selected), CR-05 (start
  position packet rate limit), CR-06 partial (Hero-role players no longer get enemy economy/stats), CR-08
  (production step asks population once), CR-09 (annex count pre-filter), CR-10 (faction lookup cache), CR-12
  (Kind.values() clone), CR-14 (economy bar worker count per frame), CR-16 (decommission follows pace).
- GameTests: `gametest/RegressionGameTests.java` (decommission, faction cache, hero stats). The queue-lines profile
  probe ran once (results in code_review.md CR-15) and was removed: it perturbed the benchmark.
- CR-15a: cheapest-first filter order in queue lines, stats table, formation, momentum.
- CR-15: TEMP profile counters (860d41f, green, 6.44 ms) showed allUnits ~2,355 during the benchmark; the benchmark
  now runs in its own batch `stress_8v8` and logs the allUnits size (5115519). TEMP code removed.
- `design/code_review.md` written (findings, severity, status, proposals).

## Next (for whoever picks this up)
- Proposals in code_review.md: Hero team fog (CR-06, coordinate with the hero slice-2 branch), owner-name field cache
  (CR-11), tighter stress budget + amortised/median reporting + RTS-player owners in the benchmark (CR-15), MapMatch
  persistence (CR-13), hero role reset packet (CR-17), queue lines only for connected owners (CR-18).

## CI
- 5115519 and cce851c green: stress 2.46 ms, "400 units (400 in allUnits)".
- Earlier: 7995038 green (6.19 ms), b83264d green (7.36), 893e99f red (8.23, the spawning probe), a266317 red
  (flaky tidewrought_t2_units_attack, Mortar Galley), 860d41f green (6.44, TEMP profile counters).
