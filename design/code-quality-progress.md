# code-quality - progress

Branch `code-quality`, based on origin/main 30a1b6f. Findings live in `design/code_review.md`.

## Done
- Merge audit of e211f6a..30a1b6f (economy pace/batch/trees, building levels/annexes/decommission, HUD declutter,
  gothic splash, lively buildings, maps/start phase, Brassworks T2, hero possession).
- Fixes: CR-01 (no production on a decommissioning lab), CR-02 (decommission saved/restored), CR-03 (Repeat toggle
  DataType registered before the save is decoded), CR-04 (Backspace with an enemy unit selected), CR-05 (start
  position packet rate limit), CR-08 (production step asks population once), CR-10 (faction lookup cache),
  CR-12 (Kind.values() clone), CR-14 (economy bar worker count per frame).
- GameTests: `gametest/RegressionGameTests.java` (decommission + faction cache).

## Next
- Wait for CI on the pushed sha; fix compile errors from annotations.
- More hot-path review (see the proposals section of code_review.md).

## Known failures
- none yet (CI pending)
