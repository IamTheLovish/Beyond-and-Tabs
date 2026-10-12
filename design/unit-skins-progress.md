# unit-skins progress (branch `unit-skins`)

## Done
- `TeamColourLayer` (unit/modelling/layers): draws `<skin>_team.png` over the model multiplied by the owner's display colour; wired into AbstractVillagerUnitRenderer (all villager-model units).
- `tools/hd_skinkit.py` (toolkit) + `tools/gen_hd_skins.py` (38 villager-model units, 7 factions), portraits regenerated, previews `design/skins_preview_<faction>.png`.

## Next
- audit doc `design/unit_skins.md`
- piglin (grunt/brute/headhunter), zombie/skeleton/husk/stray (own renderers with our textures + team layer)

## Known
- Sunforged/Gravebound/Horde units also wear livery armour (UnitDressServerEvents) that hides chest/head of the skin.
