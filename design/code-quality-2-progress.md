# code-quality-2 - progress

Branch `code-quality-2`, based on origin/main 102ba10. Findings go in `design/code_review.md` (round 2 section).
Rewrite plan: `design/ron_rewrite_plan.md`.

## Done
- (starting) branch created.

## Next
1. Integration audit of 30a1b6f..102ba10 (heroes, bots-maps, walls/traps, Brassworks T3, big units/game enders,
   unit skins, building detail): save/load, packets (PacketAuth + rate limits), client null-safety, renderer allocations.
2. Stress scenario: add game enders, walls, traps, heroes.
3. CR-11 owner-name cache.
4. RoN rewrite plan + rewrite of one subsystem with GameTests.

## CI
- none yet
