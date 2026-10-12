# building-detail - progress (resume from here)

Task (lovish, Oct 12): lab L2/L3 structures must genuinely transform each faction's T1 lab; then a detail pass on
plain economy buildings and capitols of all 7 factions. Vanilla blocks only, pocket-free, build times unchanged.

## Findings
- `BuildingLevels` has no larger-footprint support: `lab_level_structures_ship_and_have_no_pockets` fails if the
  footprint changes, so every level keeps the L1 footprint (it may grow taller).
- `BuildingPlacement.changeStructure` + `refreshBlocks` only places blocks that are not already there; it never
  removes blocks. So an L2/L3 structure must keep every L1 non-air position non-air (it may be a DIFFERENT block:
  the upgrade overwrites it). `gen_lab_levels.py` asserts this.
- Level structures don't touch build time (the upgrade is a production order). Economy buildings have a fixed
  `buildTimeBlocks`; capitols use their block count, so a capitol whose count changes gets `buildTimeBlocks` set to
  its old count.

## Done
- (in progress) new `tools/gen_lab_levels.py`

## Next
- render previews, pocket audit, commit nbt (both trees)
- audit economy + capitols, `design/building_detail.md`, detail pass
