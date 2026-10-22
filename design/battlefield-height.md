# Battlefield height budget

The battlefield world used to be vanilla's full overworld column (y -64..319, 24 sections per chunk) although play only
uses the surface (lovish: "we don't have to render the entire underground", "we can still add cliffs and things, just
adjust accordingly"). It is now its own dimension type, `data/reignofnether/dimension_type/battlefield.json`, used by
the `reignofnether:battlefield` world preset (dedicated server `level-type=reignofnether:battlefield`, and every skirmish
world). **Terrain work must stay inside this budget; nothing in code may hardcode it - read `level.getMinBuildHeight()` /
`getMaxBuildHeight()`.**

| Band | y | Notes |
|---|---|---|
| World floor | 32 (min_y) | solid stone, bedrock in 32..36 (noise_settings surface rule `above_bottom`); no caves, no aquifers, no ore veins, no deepslate |
| Lowest carved bed today | ~42 | MapLayout VOID columns are base-16, the column floor is target-4 (MapBuilder); base is 62..90 |
| Sea level | 63 | `noise_settings.sea_level`, unchanged (minimap SEA_LEVEL, MetalPatches and the old maps assume it) |
| Natural surface | ~56..78 | `density_function/battlefield/*`: absolute-y gradient + hills/bumps/river, unchanged by the shrink |
| Map base (`MatchSetup.baseY`) | 62..90 | stamped maps go from base-16 (void) to base+16 (mesa) = 46..106 |
| Room for cliffs/ridges | up to ~+60 over the base, so ~150 | future terrain relief |
| Tallest structure | ~25 tall (`.nbt` sizes) | stands on up to ~150 -> ~175 |
| Flying units | up to ~200 | |
| World ceiling | 223 (max_y 224 exclusive) | `logical_height` 192 |

192 high = 12 sections per chunk (was 24); the game logs `[Battlefield] dimension height H, sections per chunk N` at
server start. Why min_y 32, not 0: sea level and every ground-relative number stay put, and y 0..31 would be 32 blocks
of unseen stone. A taller range (e.g. 256) only needs `min_y`/`height`/`logical_height` in the dimension type and
`noise.min_y`/`noise.height` in `noise_settings/battlefield.json` (both multiples of 16; the noise cell is 8) changed
together; `density_function/battlefield/terrain.json` is a gradient in absolute y and keeps the surface where it is.

Existing worlds: the dimension type is part of the world's save, so an old full-height battlefield keeps its old range.
The dedicated server's launcher moves the old world aside and starts a fresh battlefield every session, and skirmish
worlds are created fresh per match, so nothing needs migrating.
