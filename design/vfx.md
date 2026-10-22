# Spell effects and projectile visuals (vfx foundation, Oct 22 2026)

lovish (Oct 22), after showcases of magic mods (stacked rotating rune rings, glowing tile sigils with rising sparks,
gold beams wrapped in helix rings, rune-disc ladders, a sky pillar with a double helix, fire tornadoes): "I want
those results in our game ... effects, animations, projectiles, systems". Art direction: **hyper-realism first, a
touch of dark fantasy - magic is an accent.** Light is physical (it lights the ground and the units), embers obey
gravity and wind, smoke hangs over fire, no flat neon. Ideas from those mods are free; every texture, shape and line of
code here is ours (tools/gen_vfx_textures.py draws the atlas; the glyphs are our own procedural alphabet).

## Architecture

```
server: Vfx.at(level, "id", pos) / Vfx.on(level, "id", entity[, scale])
   -> BarFx.vfx -> one BarFx event (type VFX): id hash (24 bit, VfxIds), followed entity id, scale, faction
   -> BarFx's per-tick queue, 200-block send range, fog-of-war filter (a player who can't see the spot gets nothing)
client: BarFxClient.ingest -> VfxClient.play (definition by hash, from assets/*/vfx/*.json, F3+T reloads)
   -> per frame (BarFxClientEvents, AFTER_TRANSLUCENT_BLOCKS, never in a shader pack's shadow pass):
      step effects + emitters + particles, collect cast lights
      draw: VFX_TRANSLUCENT (smoke) then VFX_ADDITIVE (everything that is light) - ONE atlas, one buffer per blend
   -> mixin/VfxEntityLightMixin raises entities' block light inside a cast light
```

| File | What |
|---|---|
| `barfx/vfx/VfxDef`, `VfxParser`, `VfxTimeline`, `VfxEasing`, `VfxAtlas`, `VfxIds` | the data model, JSON reader, timing maths, easings, atlas tiles, wire ids - pure Java, JUnit `VfxDefTest` |
| `barfx/vfx/Vfx`, `VfxCommands` | server entry points; `/bat vfx <id> [scale]`, `/bat vfx <id> self [scale]` |
| `barfx/VfxClient` | definitions, live effects, particles, lights, projectile visuals, the batched draw |
| `barfx/BarFxRenderTypes` | `VFX_ADDITIVE` (SRC_ALPHA, ONE; depth test, no depth write) and `VFX_TRANSLUCENT`, both vanilla `position_color_tex` so Oculus maps them to the pack's own programs |
| `entities/renderers/VfxProjectileRenderer` | hands a magic projectile to the batched pass; falls back to the old model without a visual |
| `mixin/VfxEntityLightMixin` | the cast light on units |
| `tools/gen_vfx_textures.py` | the 2048 px atlas, 16 tiles (`--check` in CI-style use, `--preview DIR`) |
| `gametest/VfxGameTests`, `matchstart/EyesVfxScene` | jar contents + packet codec; eyes scene 19 (`eyes.yml` input `vfx`) |

## Adding an effect

1. Write `bar/src/main/resources/assets/reignofnether/vfx/<id>.json` (id: lower-case, digits, `_`, `/`).
2. Add the id to `VfxIds.SHIPPED` (VfxDefTest fails until the list and the folder agree).
3. Call `Vfx.at(level, "<id>", pos)` or `Vfx.on(level, "<id>", entity)` where the gameplay happens (server side).
4. Tune in game: `/bat vfx <id>`, edit the JSON, F3+T, again. Problems are logged as `[Vfx] ...` (unknown keys too).

```json
{
  "timeline": { "windup": 0.9, "peak": 1.6, "fade": 1.2 },
  "light": { "color": "#FFD39A", "radius": 12, "intensity": 1.0, "y": 2.5, "flicker": 0.05 },
  "layers": [
    { "type": "ground", "tile": "sigil_star", "radius": 3.5, "spin": -14, "color": "#FFD078", "alpha": 0.8, "grow": 0.6 },
    { "type": "ring", "tile": "ring_glyph", "radius": 2.4, "y": 0.9, "stack": 3, "spacing": 0.9, "radius_step": -0.5,
      "spin": 48, "alternate": true, "tilt": 3, "rise": 0.6, "grow": 0.6, "grow_from": 0.3, "ease": "back_out" },
    { "type": "beam", "start": "peak", "radius": 0.75, "height": 48, "scroll": 1.8, "core": 0.35,
      "helix": { "strands": 2, "radius": 1.3, "turns": 7, "speed": 1.4, "points": 36, "size": 0.32 },
      "bands": { "count": 4, "radius": 1.6, "speed": 7, "tile": "ring_thin" } },
    { "type": "emitter", "particle": "ember", "shape": "ring", "start": "peak", "burst": 50, "rate": 26,
      "velocity": [0, 4.5, 0], "outward": 2.5, "gravity": 3.5, "drag": 0.9, "wind": 0.9, "color_end": "#B23A10" }
  ]
}
```

**Every layer:** `start` / `end` (seconds or a phase: `windup`, `peak`, `fade`, `end`; default the whole effect),
`fade_in` / `fade_out` (default: the wind-up / fade when the layer starts / ends with the effect), `color` (`"#RRGGBB"`
or `[r, g, b]`), `alpha`, `grow` (s) + `grow_from` + `ease`, `y` (blocks above the anchor), `on_ground`.

| type | keys |
|---|---|
| `ground` | `tile`, `radius`, `spin` (deg/s), `flicker`; laid on a grid that follows the terrain |
| `ring` | `tile`, `radius`, `spin`, `stack`, `spacing`, `radius_step`, `alternate`, `tilt` (deg), `rise` |
| `beam` | `radius`, `height`, `scroll`, `core`, `core_color`, `helix {strands, radius, turns, speed, points, size, color}`, `bands {count, radius, speed, tile}` |
| `emitter` | `particle` (ember, spark, smoke, glint, heat), `shape` (point, disc, ring, column, sphere), `rate`, `burst`, `radius`, `height`, `velocity`, `spread`, `outward`, `swirl`, `gravity` (negative = buoyant), `drag`, `wind`, `life`, `size` (number or [min, max]), `size_end`, `size_ease`, `color_end` |

Tiles: glow, ring_glyph, ring_thin, sigil_star, sigil_tiles, sigil_oct, beam, smoke, spark, glint, trail, shockwave,
rune_disc, ember, heat. Easings: linear, smoothstep, sine/quad/cubic/quart/quint/expo/circ/back `_in` `_out` `_in_out`,
elastic_out, bounce_out.

**Projectile visual** (`"type": "projectile"`): `entities` (entity type ids), `core {tile, size, pulse, color,
inner_size, inner_color}`, `trail {samples, width, alpha, step, color, color_end}`, `emitter {...}`, `light`,
`hide_model`. Register `VfxProjectileRenderer` (or a subclass) for the type in ClientModEvents; an impact effect is
`impactVfx()` on the projectile (AbstractMagicProjectile). First converted: the Necromancer's soul bolt.

## Light - the choice

A light-emitting block (vanilla `light` placed client side) was rejected: every move relights and re-meshes chunk
sections, the cost the fog re-mesh work (PR R1) fought. Instead: (1) a soft additive **light pool on the terrain**
(the glow tile on the conforming ground grid, coloured, faded by the timeline) - reads as light falling on the ground
under any shader pack; (2) **units inside the radius get their block light raised** through the packed light every
entity renderer already receives (one mixin, an int compare when nothing is lit); shader packs light entities from
that value, so it works under Complementary. Block light is the warm torch ramp, so a cold light (the soul bolt)
lifts units warm-white - acceptable for now, noted below.

## Performance safeguards

Caps: 48 effects, 3000 particles (struct-of-arrays, no per-particle allocation), 16 cast lights, 256 projectiles.
Client setting `vfx_quality` (0 Minimal - no particles, no cast light; 1 Low 35%; 2 Medium 67%; 3 High) scales every
rate and burst; spell particles halve when BarFx's own pool is three-quarters full. Culling against the view
(BarFxClient.near), fog of war re-checked every 0.25 s. Ground heights sampled once into a grid (again only when a
followed caster crosses a block). Never drawn into the shadow map. Timed as `[FrameParts]` section `vfx`.

## Not yet (next steps)

- Heat haze is a faint veil tile, not refraction (that needs a post pass - WorldLookPass is the place).
- Coloured light on units (block light is warm only); a per-entity tint would need the unit renderers.
- Faction tint from the event's faction byte (sent, unused).
- More effects on the system: fire tornado, rune-disc ladder with lightning (LightningFx), building rune circles
  (PlacementGrid's line circle could move onto the atlas), the other heroes' ultimates.
- A settings-screen button for `vfx_quality` (Lane C's DisplaySettingsScreen; the config file works today).
