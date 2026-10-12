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
- `design/code_review.md` written (findings, severity, status, proposals).

## Next
- Wait for CI on the pushed sha; fix compile errors from annotations.
- More hot-path review (see the proposals section of code_review.md).

## Known failures
- 7995038 green (stress 6.19 ms), b83264d green (7.36 ms), 893e99f red: the benchmark at 8.23 ms with the spawning
  probe running in the same batch (probe removed in the next push).
