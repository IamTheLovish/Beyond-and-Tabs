# unit-skins progress (branch `unit-skins`) - complete, ready for review

## Done
- `TeamColourLayer` (unit/modelling/layers): draws `<skin>_team.png` over the model x owner display colour.
  Wired into AbstractVillagerUnitRenderer, PiglinUnitRenderer, Thornbow/ShadeRanger/TidePriest/InkScribe/Moonwell
  renderers and the new Zombie/Husk/Skeleton/WitherSkeleton unit renderers (ClientModEvents registrations).
- `BackBannerLayer`: owner-coloured back banner on the six T2 constructors.
- `tools/hd_skinkit.py` + `tools/gen_hd_skins.py`: 50 HD skins (all 7 factions), portraits regenerated,
  `captain_banner.png`, previews `design/skins_preview_<faction>.png`.
- Audit + readability findings: `design/unit_skins.md`.
- GameTest `gametest/UnitSkinGameTests.java` (new skin files ship in the mod).

## Next (follow-ups)
- livery decision for Sunforged/Gravebound/Horde (armour hides skins) - see unit_skins.md finding 2
- villager worker/militia overlays, stray/drowned overlays, non-humanoid vanilla units, machines' team textures
- commander banners need a client-synced commander flag

## Known
- Rerunning older gen_*_skins.py overwrites HD skins; run gen_hd_skins.py last.
