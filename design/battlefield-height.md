# Battlefield height budget

The battlefield world used to be vanilla's full overworld column (y -64..319, 24 sections per chunk) although play only
uses the surface (lovish: "we don't have to render the entire underground", "we can still add cliffs and things, just
adjust accordingly"). It is now its own dimension type, `data/reignofnether/dimension_type/battlefield.json`, used by
the `reignofnether:battlefield` world preset (dedicated server `level-type=reignofnether:battlefield`, and every skirmish
world). **Terrain work must stay inside this budget; nothing in code may hardcode it - read `level.getMinBuildHeight()` /
`getMaxBuildHeight()`.**

| Band | y | Notes |
|---|---|---|
| World floor | 48 (min_y) | solid stone, bedrock in 48..50 (noise_settings surface rule `above_bottom` 0..3); no caves, no aquifers, no ore veins, no deepslate; the battlefield biomes carry no underground-only ores (granite/diorite/andesite lower, tuff, iron middle/small, gold, redstone, diamond, lapis, infested) |
| Lowest carved bed today | 49 | MapLayout VOID columns are base-16, the column floor is target-4 but never below min_y+1 (MapBuilder.column), so at base 62 a void pit bottoms out at 49 (13 deep instead of 16) |
| Sea level | 63 | `noise_settings.sea_level`, unchanged (minimap SEA_LEVEL, MetalPatches and the old maps assume it) |
| Natural surface | ~56..78 | `density_function/battlefield/*`: absolute-y gradient + hills/bumps/river, unchanged by the shrink |
| Map base (`MatchSetup.baseY`) | 62..90 | stamped maps go from base-16 (void) to base+16 (mesa) = 46..106 |
| Room for cliffs/ridges | up to ~+60 over the base, so ~150 | future terrain relief |
| Tallest structure | ~25 tall (`.nbt` sizes) | stands on up to ~150 -> ~175 |
| Flying units | up to ~200 | |
| World ceiling | 223 (max_y 224 exclusive) | `logical_height` 176 |

176 high = 11 sections per chunk (vanilla 24; 12 at y 32..223 until Oct 22 2026); the game logs `[Battlefield] dimension height H, sections per chunk N` at
server start. Why min_y 48 (lovish Oct 22 17:44-17:45, after seeing the stone layer in spectator: "maybe even 42-30", "we dont need that
many safe floors in the rts version of the game"): sea level and every ground-relative number stay put; min_y must be a
multiple of 16, so 48 is the next step up from 32. Natural surface starts ~56, so there are only a few blocks of stone over
the bedrock skin. A taller range (e.g. 256) only needs `min_y`/`height`/`logical_height` in the dimension type and
`noise.min_y`/`noise.height` in `noise_settings/battlefield.json` (both multiples of 16; the noise cell is 8) changed
together; `density_function/battlefield/terrain.json` is a gradient in absolute y and keeps the surface where it is.

Existing worlds: the dimension type is part of the world's save, so an old full-height battlefield keeps its old range.
The dedicated server's launcher moves the old world aside and starts a fresh battlefield every session, and skirmish
worlds are created fresh per match, so nothing needs migrating.
