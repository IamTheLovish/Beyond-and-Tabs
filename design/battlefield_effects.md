# Living battlefield effects (Nov 6 2026)

lovish picked "Living battlefield effects" on Nov 6: the realism list from Oct 19 (battle dust, scorch marks, trampled
grass, shore foam) that was never finished. His words: "I want minecraft pov to feel full and lively". The north star
is hyper-realism with a touch less dark fantasy, readable from the RTS camera at every zoom, and no lag.

What already exists, so nothing is built twice:

- **Scorch and craters**: world memory slice 1 (#89, `worldmemory/`). Blasts, heavy impacts, T3 deaths and collapses
  stamp a per-chunk field. The server syncs it per player and respects the fog. The client draws it as a decal mesh,
  one vertex buffer per 64x64 region per layer. The field already has a `TRAMPLE` channel and its decay (half-life
  8 min, floor 0), but nothing writes or draws it yet.
- **Shore foam**: shader pack slice 4 (#438, `shaderpacks/BeyondAndTabs/shaders/lib/water.glsl`). It shows only with a
  shader loader, and the tester pack has none since Oculus left the pack. Without shaders, players see no foam.

## The four effects

| Effect | System | Shared entry (never per unit) | Lifetime and caps | Cost budget (set before measuring) |
|---|---|---|---|---|
| 1. Trampled grass | world memory `TRAMPLE` channel: server field, the same sync, a third decal layer in `ScarRegionMesh` | `GroundMemoryServer`'s level tick walks `UnitServerEvents.getAllUnits()` every 10 ticks and stamps each grounded unit's path since the last sample (a capsule, not dots) | Half-life 8 min to nothing (the spec's default). A cell saturates at 255, so a used road stops changing and stops costing sync bytes. Ground only: grass, soil, sand, snow (`TrampleRules.surfaceOf`) | **Server**: <= 3 us per moving unit per sample pass on the CI GameTest server (~0.15 ms/tick at 500 units on CI; less on a desktop). **Sync**: a 100-unit march <= 6 KB/s per client (the scars brawl budget). **Client, zoom 30**: no draw call per unit; +1 draw per region in view; trample-only changes rebuild a region at most every 1.5 s; drawing the layer on vs off in ONE eyes run <= +0.30 ms/frame of the decal draw + build (`GroundMemoryClient` ms) on CI's software renderer |
| 2. Battle dust | client particles: one textured soft sprite type, our own procedural texture | One client pass over the units in view each client tick (feet on dry ground while moving fast or fighting), plus BarFx's impact calls for hits. No hook in any unit class | A view-wide pool (cap 64 live puffs, 1.5-3 s life) and an emit budget per tick, both shared by every unit. Thinner from zoom 36, none past zoom 70 (strategic icons). Never over a unit's body at RTS zoom (low, wide, pale) | <= 0.20 ms/frame at zoom 30 with 200 units fighting, A/B in one eyes run |
| 3. Scorch from breath and fire | world memory `SCORCH` (exists) | The shared fire-damage paths (BarFx fire calls, breath cones) call `GroundMemoryServer.stampScorch`. One call site per kind of fire, not one per unit | Slice 1's decay (6 min half-life to a 30% floor); a stamp saturates | Bounded by slice 1's stamp budget (< 1 ms a stamp, 6 KB/s per client in a brawl) |
| 4. Shore foam without shaders | a static world memory layer: a foam band on the land side of water edges, built from the chunk's blocks on the client (no server data) | The same region mesh (one extra layer), rebuilt when a chunk loads or a water block changes | Static (no decay); only along water that touches soft ground; animated by texture scroll, no per-frame rebuild | <= 0.10 ms/frame at zoom 30 over a river (one draw per region) |

Order of PRs, smallest first: 1 (trampled grass), 3 (breath scorch: a few call sites), 2 (dust), 4 (foam).

## How each is proven

- **Tests first**: JUnit for the pure rules (`TrampleRules`: which surfaces wear, how much a unit wears by size, the
  capsule weight, the look's colour maths) and GameTests for the world (real units walking wear the cells they cross
  and nothing else, stone stays clean, a flying unit leaves nothing, the field syncs, the cost is inside the budget).
  Each new test is red on main (the writer does not exist), then green on 8 runners on the full head.
- **Look**: eyes runs on GitHub runners only. The control (main) and the change are shot in the SAME eyes run (the
  layer drawn off, then on, over the same pad). A blind critic scores the stills. Rounds continue until every axis
  scores >= 8, or three rounds gain < 0.5.
- **Cost**: each budget above was written before any measurement and is never moved after. The zoom-30 frame cost is a
  hard gate.

## 1. Trampled grass: the rules

- **Who wears the ground**: every unit standing on the ground. Flying units, units in water, and riders do not (the
  mount does).
- **How much**: a unit wears in proportion to its body width, from 0.75x to 4x a militia's. A militia squad of 12
  passing once leaves a flattened, lighter trail. Twenty or more passes wear it down to bare earth. A titan's single
  pass reads like a squad's.
- **What it looks like**: grass flattened first, lighter and straw-yellow (bent blades catch the light). Then worn soil,
  warmer and close in value to the lawn, inside the dirt-path band, so it reads as a path and never as a hole or a
  shadow. Soil gets darker where it is packed, sand gets darker and rougher, snow turns to grey slush.
- **What it never does**: it never changes a block (pathing cannot see it), and it never shows under the fog of war
  beyond what the scars already show (the same sync rules).
