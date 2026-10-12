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

## Next / open
- CI green check (GameTests: lab_level_structures_ship_and_have_no_pockets, structures_have_no_unit_pockets_*).
- Follow-ups: detail overlay for the RoN capitols (Town Centre, Mausoleum, Central Portal).
