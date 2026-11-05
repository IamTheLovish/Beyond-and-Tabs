# building-detail - progress (resume from here)

Task (lovish, Oct 12): lab L2/L3 structures must genuinely transform each faction's T1 lab; then a detail pass on
plain economy buildings and capitols of all 7 factions. Vanilla blocks only, pocket-free, build times unchanged.
Design write-up: `design/building_detail.md`.

## Findings
- `BuildingLevels` has no larger-footprint support: every level keeps the L1 footprint (it may grow taller).
- `changeStructure` + `refreshBlocks` never remove blocks: every lower-level non-air cell must stay non-air
  (asserted in `gen_lab_levels.py`; also for mex T1 -> T2 via `check_mex_tiers` in `gen_structures.py`).
- `centrePos` (the indoor spawn) is computed from the L1 only; the audit tool's spawn note on L3s is pessimistic.
- Regenerating `gen_structures.py` rewrites some nbt byte-differently with identical blocks: commit only files with
  real block changes (scratch `nbtdiff.py` compares block maps).

## Done
- `tools/gen_lab_levels.py` rewritten: per-faction L2/L3 (8 labs incl. assembly_works), support + leaf checks.
- `tools/preview_structure.py`: shaped rendering of thin blocks.
- `tools/gen_structures.py`: bespoke tide/storm/brass extractor T1/T2, converter, vault; verdant vault; foundry hall
  and heartwood hall detail. FoundryHall / HeartwoodHall `buildTimeBlocks` = old counts (428 / 498).
- Contact sheets `design/building_preview_*.png`; `design/building_detail.md`.

## Status: complete. Stale "not merged" corrected Oct 26 2026: the work is on main (no PR number - it landed before the PR workflow; `tools/gen_lab_levels.py` per-faction L2/L3 first appears in main history at 41b03205, 2026-10-19; `design/building_detail.md` at 1fe3bbf7, 2026-10-16).

## Next / open
- CI: green at 91969dc and 97558d5 (all structure GameTests pass). Two runs failed only on the timing test
  stress_8v8_server_systems_stay_in_budget (8.2 / 10.1 ms vs 8.0, same tree green elsewhere: runner load).
- Follow-ups: detail overlay for the RoN capitols (Town Centre, Mausoleum, Central Portal).
