# tools/blender: critic sheets for box-model units

(A copy of the BT-Blender tool, run from the repo: on CI by `.github/workflows/anim-render.yml` on the render
branches, Blender 5.2.3 LTS Linux, `--background --python-exit-code 1`, Workbench only. `export_frames.py` takes the
checkout whose `tools/` do the bone math from `--repo`, default this repo. The local launcher lines below are how it
runs on lovish's PC when local jobs are allowed.)

Blender (Workbench) renders of Beyond and Tabs units, posed by the repo's own Python clip drivers. These sheets replace
the old Python pose/pixmotion renderers for the blind critics (silhouette, articulation, weight/timing, secondary
motion, effects readability at RTS zoom, faction style).

There are two steps, and the bone math lives in ONE place: the repo's tools.

1. `export_frames.py` (plain Python) imports `tools/` from the repo read-only. For every unit, clip and sampled tick it
   writes the 8 posed corners of every cube, the cube's palette colour and its emissive/team flags to
   `frames/<unit>.json`. The corners come from `game_preview.faces_of`: the game's `ModelPart` tree from
   `dsl_to_game.build_tree`, with `_move` and `_scale` applied the way `translateAndRotate` applies them. World
   travel comes from `clip.world_z()`. The camera axes come from `preview_model.view_matrix`.
2. `dsl_render.py` (Blender) draws what is in that JSON and nothing more. It does no bone math of its own.

Run every job through the launcher, ONE at a time. It starts the job at BelowNormal priority, returns exit 3 while a
game runs and exit 4 while another Blender job runs. `wait_run.sh` retries those exits (sleep 120, up to 30 tries):

```
python tools/blender/export_frames.py --model design/models/gravebound_spider_queen.json --clips anim --names idle,walk,attack --step 2 --verify --out out/frames/queen.json
blender --background --python-exit-code 1 --python tools/blender/dsl_render.py -- out/frames/queen.json out/spiders --sheets turnaround
```
(locally, each through the launcher `run_low.ps1` at BelowNormal priority, one job at a time, never while a game runs)

## export_frames.py

| option | meaning |
|---|---|
| `--model` | the DSL json (`design/models/<unit>.json`) |
| `--clips anim` | runs `tools/arachnid_anim.py` clips live. This source includes world travel. |
| `--clips fixture[:path]` | the sampled `arachnid_anim_fixture.json`. It has no world travel. |
| `--clips poses` | the key poses from `tools/arachnid_poses.py`, one frame each |
| `--clips rest` | the rest pose only |
| `--names idle,walk,attack` | which clips (default: all). A `rest` frame is always written. |
| `--step 2` / `--ticks 0-20` | the ticks to sample (default: every 2nd tick) |
| `--team tabard,tabard_dark` / `--owner #3a6fd1` | team palette names and the owner colour (game_preview's default) |
| `--verify` | recomputes every frame through a second route (`contact_check.boxes`, the `gen_models` FK) and prints the worst corner distance |

JSON space: DSL px, y up, the unit faces -Z, +x is the unit's left. Corner bits: x-max 4, MC-y-max (the visual
bottom) 2, z-max 1. Views: game_preview's `front quarter quarter_l side back rts topdown`, plus `profile` (side,
raised 12 degrees so the grid reads) and `turn_000..turn_315` (8 yaws from -40, tilt 18).

Glow: `Pose.glow` layers scale the emissive cubes. The mapping is v1's reading of the layer names:
`eye_bar` = eyes/crown, `sac_<i>`/`sacs` = egg sacs, `fang_tips`, `cracks`/`heart_line` = the abdomen, `soul` = all.
The Java side owns the real mapping.

## dsl_render.py

`blender --background --python-exit-code 1 --python dsl_render.py -- <frames.json> <out_dir> [options]`

(Without `--python-exit-code 1` Blender exits 0 even when the script raises.)

| option | default | meaning |
|---|---|---|
| `--sheets` | `views,turnaround,clips` | which sheets to render |
| `--clips` | all but rest/idle | clips for the frame sheets |
| `--combine` | off | all clips on one frame sheet (default: one sheet per clip, to keep each render short) |
| `--rows` | `profile:42,rts:42,rts:21` | frame-sheet rows, as `view:px_per_block` |
| `--cols` | 12 | frames per strip before a row wraps |
| `--group` | `cube` | one object per cube / bone / unit. Workbench draws its outline between objects. |
| `--colours` | `palette` | `palette` = the raw DSL palette; `game` = dsl_to_game's game-value palette and dyed owner |
| `--deadzone` | 12 | the camera stays put until the body is more than this many px from the camera target |
| `--aa` | 8 | Workbench AA samples |
| `--no-shadow --no-cavity --no-outline` | | turn those passes off |
| `--check CLIP:TICK:VIEW` | | one transparent still for `check_pose.py` (no ground, outline or cavity) |
| `--tag` | unit key | file-name prefix |

Look:
- Workbench with FLAT light. Every face gets game_preview's Minecraft two-light value baked into its colour
  (`0.6 (n.L0 + n.L1) + 0.4`).
- Emissive cubes add `0.8 x glow x colour` on top, so they show bright as in the game.
- Team parts use the owner colour (the dark shade is x190/255).
- Cavity, object outline and a ground shadow are on.
- The ground is light grey with a 1-block grid.
- Labels are burned in with a built-in 5x7 bitmap font. No fonts or assets are loaded.
- Scale is exact: an orthographic camera at `ortho_scale = image px / px_per_block`.

Sheets (`out_dir/<tag>_*.png`, each cell also in `out_dir/cells/`):
- `<tag>_views.png`: the idle pose (tick 0) in RTS close (yaw -35, tilt 52, 42 px/block), RTS tactical (21 px/block)
  and quarter (yaw -40, tilt 18, 42 px/block). Each has a black silhouette under it.
- `<tag>_turnaround.png`: 8 yaws, 45 degrees apart, at tilt 18 and 42 px/block, with one framing for all 8, plus
  their silhouettes.
- `<tag>_frames_<clip>.png`: per clip, a profile row (42), an RTS row (42) and a tactical RTS row (21), every
  exported tick. Each cell is labelled `clip tTICK`.
  - The camera never re-centres per frame. It follows the body only once the body is more than `--deadzone` px from
    the camera target, then drags the target along. Travel shows as the grid sliding; hits and lunges stay visible.
  - Each row keeps one framing, so one scale and one image size, across all its frames.
- `<tag>_timings.json`: seconds per sheet and the Blender process's peak working set (MB).

## check_pose.py

Run it after `dsl_render.py --check walk:12:quarter`. It renders the same tick with `game_preview.render`, crops both
silhouettes, prints their IoU and writes [Blender | game_preview | overlay].

Not in v1: particles and `_fx` projectiles (exported, not drawn) and textures (flat palette colours only).
