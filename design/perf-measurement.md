# Performance measurement (Oct 21 2026, PR 1 of the perf plan)

Every performance change measures before and after with these lines. They are in `latest.log` (a player's PC), the
eyes tour log and the CI game-test log; nothing here changes what the game does.

## The lines

| line | where | when | says |
|---|---|---|---|
| `[Tick]` | server | every 10 s while units exist | p50 / p95 / max of the last 100 server ticks (vanilla's own ring) |
| `[Paths]` | server | with every `[Tick]` line | the units' vanilla `createPath` calls in the last 10 s, how many took the distance-scaled short search (`VanillaPathBudget`), how many of those found no complete path and re-ran at full range, and the chase cell memo's hit rate (`Terrain.classify`). Fallbacks near the short-search count mean the short range is too tight |
| `[Sys]` | server | every 10 s **while the server is slow** (p95 of the last 100 ticks >= 25 ms; off again below 18) | milliseconds per tick each of our systems spent (self time), the worst tick, calls per tick, and how much of the whole tick the timed systems are. `elsewhere` is vanilla, other mods and GC |
| `[PerfAuto]` | server | p95 >= 50 ms with 60+ units, at most every 5 min | a 10 s run of vanilla's metrics recorder (what `/perf start` runs), our `bat:` sections summed by name, the heaviest leaves, `logs/perf-auto-*.txt`, and the recorder's own zip under `debug/profiling/` |
| `[Frame]` | client | every 10 s in a world | p50 / p95 / max frame, zoom, units known |
| `[FrameParts]` | client | every 10 s in a world | milliseconds per frame in our render handlers (unit outlines, building boxes, strategic icons, minimap, HUD, unit bodies), per client tick in `Unit.tick`, and section re-mesh requests per second |
| `[FrameAuto]` | client | p95 frame >= 25 ms with 100+ units, at most every 5 min | a 10 s client metrics-recorder run (F3+L's); the heaviest sections read back out of its zip |
| `[GC]` | both | every stop-the-world pause >= 50 ms; a summary when 10 s held >= 100 ms of GC | a hitch is a GC stall when a `[GC]` line shares its second |
| `[Anim]` | client | every 10 s | animation and unit render cost per unit (unchanged) |

`/rts-perf <seconds>` (op) still records every tick to `logs/perf-*.json`, and forces `[Sys]` on for its length.

## Reading `[Sys]`

`attacker 12.30 (max 40.1, 610/t)` = 12.3 ms per tick of self time over the window, 40.1 ms in the worst single
tick, 610 calls per tick. Self time means the sections nested inside are taken out: `attacker/buildingScan` is not
also counted in `attacker`, so the rows add up and `ours` can be set against the mean tick.

Sections (`perf/Sys.java`): `unit`, `attacker`, `attacker/attackMove`, `attacker/idleAggro`, `attacker/retarget`,
`attacker/entityScan`, `attacker/buildingScan`, `separation`, `stuckNudge`, `unstick`, `reactions`, `pathfinder`
(main-thread half), `buildings`, `bots`, `unitSync`, `commandQueues`, `autoCaster`, `unitGrid` (rebuild),
`chunkLoader`, `economy`, `fogOfWar`, `targetGrid` (rebuild of the target scan's grid, perf plan PR 5).

Each is also a `bat:<name>` section in vanilla's profiler. Vanilla files a unit's tick under its entity type, so the
raw tree has `bat:attacker` once per unit type; `[PerfAuto]` and `[FrameAuto]` add them up by name.

## Adding a section

```java
int t = Sys.begin(Sys.MY_SYSTEM);          // server; FrameParts.begin(...) on the client
try { ... } finally { Sys.end(t); }
```
Add the id and the name in `Sys` (`FrameParts`), wrap the call site (or rename the method body and wrap the
header, as `Unit.tick` and `AttackerUnit.tick` do), nothing else. Healthy server and not recording: `begin` is one
volatile read. Do not call it from another thread (it is ignored) and always use `finally`.

## Not measured (yet)

The pathfinder worker threads, Embeddium's chunk rebuilds (only the re-mesh *requests* are counted), the GPU. For a
client profile without a build: **F3+L** while it lags, then send `debug/profiling/<date>.zip`.

## History

Until Oct 21 the lag capture ran the time profiler behind `/debug start`, which only times the tick rate: its profile
was empty ("heaviest:  0.0%") and its game test passed on an empty profile. `PerfProfileGameTests` now fails on an
empty profile or one that does not name `bat:unit` and `bat:attacker`.

## Late-game frame rate (Oct 23, lovish's log)

120 fps below 60 units, 55-75 fps at 450-575, p95 frame 9 -> 40-45 ms, server tick fine. At ~570 units a frame is 14-19 ms:
ours 7-8 ms, "elsewhere" (vanilla/Embeddium entity and world draw) 5 -> 10.6 ms. What changed (branch claude/perf-late-game):

- **Unit models.** The icon switch (zoom 70) now comes in as the crowd grows: `UnitLodRules.iconEnterZoom` scales it by the
  known-unit count, 1.0 up to 200 units, down to 0.7 (zoom 49) at 600, in steps of 0.05 so a jittering count never
  flips the view (a step is 3.5 zoom, the hysteresis band 8); never closer than 48 and never touching the detail zoom
  (36). `unit_render_lod = false` turns it off with the rest of the unit LOD. The model skip also moved in front of the
  renderer: `EntityRenderDispatcherMixin.shouldRender` returns false for a unit whose model is skipped (same rule as the
  render event - `UnitLodRules.skipUnitModel`), so no light lookup, renderer, pose or event is paid per unit.
- **worldMarkers / buildingDecor "calls".** 165 calls a frame were not draw calls: Forge fires every level stage at every
  handler (~10 a frame) and our handlers opened their timer and asked the shader pack (a reflective call) before checking
  the stage. All 14 handlers now test the stage first (`RenderStageGateTest` guards it), so the `calls` column of the
  next [FrameParts] line drops to about the handler count. Metal patch rings and gems, banners and mill parts are also
  frustum-culled (they were built for every patch within 260 blocks, on screen or not).
- **buildingDecor spikes.** `BannerRenderClientEvents.computeMount` walks every block of a placement; a base coming into
  view or a fog reveal handed it dozens in one frame. `FrameBudget` allows one a frame.
