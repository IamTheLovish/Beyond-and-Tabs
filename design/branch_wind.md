# Branch wind: tree limbs that sway, mod-side

lovish (Oct 21 2026, 09:40): tree **branches** should sway in the wind, done in the mod, not by forking the
Complementary shader; "it's okay if it costs some performance". Complementary already waves leaves and grass; the
limbs holding them stand still, and without shaders nothing on a tree moves at all.

Scope: the map trees from `tools/gen_trees.py` (PR #88, branch `claude/map-trees`): vanilla `*_wood` limbs, `*_fence`
twigs on the bare and dead trees, vanilla leaves. Trunks and roots never move. Nothing here changes a hitbox, a
collision shape, pathing, felling (`resources/TreeClearing`) or the energy a felled tree pays.

Status (Oct 21): built on branch `claude/branch-wind` (package `environment/wind/`), not yet merged; the trees are not
in maps yet (Lane A places them in `MapBuilder.decorate` after lovish approves #88).

## 1. The rules the sway must keep

| Rule | How it is met |
|---|---|
| The trunk is static | Trunk and root blocks stay vanilla logs/wood: no sway block, no block entity, nothing drawn by us. |
| Sway grows with distance from the trunk and with height | Each limb block carries a `sway` tier 1-4 in its block state, written by `tools/sway_trees.py` from its reach from the trunk column and its height; tier 4 = the outer twigs. Amplitude = tier x 0.0195 block x wind. |
| One shared wind field | `WindField`: two gust waves travelling across the map along a slowly turning wind direction (wavelengths 41 and 17 blocks, 6.5 and 10 blocks/s) plus a small per-block flutter seeded from the position, all scaled by the match wind (`resources/Wind.multiplier()`, the BAR wind the energy generators follow). Per-tree phase comes from the field itself: trees 20 blocks apart are at different points of the gust. |
| Subtle | Tier 4 at the strongest match wind (x1.7) moves 0.148 block; at the average wind 0.087. A limb bends: tier 1 next to the trunk moves 0.02, each block further out a little more, so the joints open by at most 0.04 block. |
| Never moves collision or hitboxes | The sway is a render-time translation of the block's model. The block itself (a `RotatedPillarBlock` / `FenceBlock` subclass) keeps the vanilla shape, occlusion, pathing and light. |
| Degrades by distance | Full within 48 blocks of the camera, ramping to zero at 60, then **static and free**: beyond 60 the block is back in the chunk mesh like any other block (section 3). |
| Client toggle | Display settings, "Branch wind": **Off** (nothing drawn by us, zero cost), **Near** (default, the 48/60 rule), **All** (every limb in view sways, no distance cut). |
| Felling and resources | The sway wood blocks are in `#minecraft:logs` (data tag), so `TreeClearing.isLog` fells them and credits 5 energy each; the sway fences are tagged too, so a bare tree's twigs come down with it. GameTest `BranchWindGameTests`. |
| With or without shaders | Block entities are drawn by Oculus/Iris through the block-entity program (lit, shadowed), and Complementary's waving is keyed on terrain material ids, so our limbs never get moved twice. Without shaders: vanilla's block-entity path. Embeddium meshes our block through its Forge `ModelData` support like any other model. |

## 2. Approaches compared

The chunk mesh is static: once a section is built its vertices never move until it is rebuilt. Any sway needs
geometry that is rebuilt or transformed every frame, and the static copy in the mesh must not show.

**(a) Sway blocks + block entity renderer.** Custom limb blocks whose baked model is empty in the chunk mesh and a
`BlockEntityRenderer` that draws the block model under a wind translation each frame.
- Positions come free (the block entity), frustum culling comes free (vanilla keeps a block entity list per render
  section and only visits visible sections), the lighting comes free (`ModelBlockRenderer.tesselateBlock` with the
  level at the block's own position gives the same AO and face light as the mesh).
- Cost: one block entity per limb block, saved in the chunk (about 40 bytes each) and sent with the chunk. Rendered:
  the dispatcher visit (~0.5 us) plus the block tesselation (~3-5 us: 6 faces, AO) per block per frame.
- Weakness on its own: every limb in view is drawn by us forever, moving or not - at the strategic zoom a 30-tree map
  is 600-1200 blocks a frame, 3-6 ms.

**(b) Client-only batched geometry in `RenderLevelStageEvent`.** The same custom blocks (something must empty the
mesh; a mixin into the mesher is a second code path under Embeddium, which has its own mesher), no block entity; a
client index of limb positions filled by scanning every chunk section on load (`LevelChunkSection.maybeHas`) and
kept by a block-update hook; one buffer built per frame from cached quads.
- Cheapest draw (~1 us a block: cached `BakedQuad`s, pose rewritten in place, as the scaffold renderer does), one
  draw call per render type.
- Costs its own plumbing: a position index with the same lifetime as the chunks (load, unload, block changes from
  felling, fog-of-war chunk resends), our own frustum test, our own light lookups per face (the mesh's AO is lost,
  so limbs read brighter than the trunk - visible at the near/far switch), and under Oculus the geometry has to be
  drawn in the right pass by hand. Same weakness as (a): everything in view is ours forever.

**(c) A vertex offset in the chunk mesh.** Vanilla lets a resource pack replace the terrain core shaders, and a
`sway` tier could be smuggled through the vertex colour. Dead on arrival: Embeddium replaces the terrain shaders
(the eyes shader lane runs Oculus + Embeddium), Oculus replaces them with the pack's, and Complementary is exactly the
shader lovish does not want forked. It would work only in the one configuration nobody plays.

**Chosen: (a) with a distance switch - the hybrid.** The block entity's Forge `ModelData` carries one flag,
`meshHidden`. The block's baked model (a wrapper installed by `ModelEvent.ModifyBakingResult`) returns no quads when
the mesher asks with that flag set, and the real quads otherwise (the renderer asks with `ModelData.EMPTY`). A client
tick every 4 ticks walks the registry of loaded sway block entities, decides for each whether it should be drawn by us
(mode and camera distance), and when the answer changes flips the flag, calls `requestModelDataUpdate()` and marks the
block's section dirty. So:
- **Off**: no flag set, every limb is an ordinary block in the mesh, the renderer returns at `shouldRender` (~0.1 us
  a block entity in view). Switching the setting re-meshes only the sections that hold limbs.
- **Near**: only limbs within 60 blocks of the camera are ours; the amplitude is 0 at 60 and full at 48, so a limb
  crosses the switch exactly where the two copies coincide. The re-mesh when the camera pans is bounded by the
  sections that hold limbs (a 30-tree map: ~60-90 sections in all; a pan across a forest edge re-meshes a dozen a
  second on the worker threads, the same as a few growing crops).
- **All**: every loaded limb is ours (the 3-6 ms worst case above, by choice).

Why not (b) for the near set: the block entity is already the index, the culling and the lighting; the draw is the
only thing (b) does cheaper, and at the near set's size (a few hundred blocks) it is a difference of a millisecond.

## 3. Numbers (block counts measured; frame costs are estimates - CI measures nothing on the render thread yet)

Sway blocks per tree, `tools/sway_trees.py --check` over the round-21 templates of PR #88:

| Tree | Sway blocks (tier >= 1) | of which tier 3-4 |
|---|---|---|
| oak (3-4 limbs, forks, twigs) | 30-46 | 17-27 |
| birch | 4-18 | 0-2 |
| spruce (whorl stubs) | 4-10 | 0 |
| acacia | 11 | 3-4 |
| cherry (forked trunk) | 15-35 | 6-19 |
| pine | 12-20 | 1-2 |
| bare oak / dead tree (wood + fence twigs) | 50-83 | 20-60 |
| **giant bare** | **283-309** | **245-250** |

Average 43 a tree over the 32 templates; a 30-tree mixed map without giants: **~700 sway block entities**; each giant
bare tree adds ~300 (two giants: ~1300). Memory ~1 KB each; chunk save +40 bytes each; the chunk packets the same
once per load.

Per frame:

| Setting | Camera | Drawn by us | Cost |
|---|---|---|---|
| Off | any | 0 | ~0.1 ms (the dispatcher's `shouldRender` visits) |
| Near | close zoom (camera ~35 blocks over the ground) | 5-10 trees in 60 blocks: 150-300 blocks (one giant: +300) | **0.8-1.5 ms** (a giant in range: +1.5 ms) |
| Near | strategic zoom (camera > 60 blocks up) | 0 | ~0.1 ms |
| All | strategic zoom, whole map in view | 700-1300 | 3-6 ms |

Draw calls: the block entities tessellate into the shared `solid` buffer, flushed once per frame with every other
block entity - **1 draw call** for all limbs, not one per block. The policy tick: ~1000 distance checks every 4 ticks,
~20 us. Per drawn block: `tesselateBlock` with AO and the neighbour cull, ~3-5 us (the mesher's own cost per wood
block). The re-mesh on a pan is bounded by the sections holding limbs: ~60-90 on a 30-tree map, a dozen a second
across a forest edge, on the worker threads.

If a giant's 300 blocks prove too much in range, the lever is in `sway_trees.py`: give the giants' inner limbs tier 0
(trunk) past a reach, or sway only its fence twigs.

## 4. Implementation map

```
environment/wind/
  SwayWoodBlock, SwayFenceBlock     the limb blocks: vanilla shape + the `sway` 1-4 property (+ axis / fence sides)
  SwayBlocks                        registration: sway_<wood>_wood and sway_<wood>_fence for oak, birch, spruce,
                                    acacia, cherry, dark_oak; the block entity type; the tags are data
  SwayBlockEntity                   non-ticking; the `meshHidden` ModelData flag; a client registry (onLoad/setRemoved)
  SwayBakedModel                    the baked-model wrapper: no quads for the mesher while hidden
  WindField                         the shared wind: gusts travelling along a turning direction, flutter, the match wind
  BranchWindRenderer                the BlockEntityRenderer: amplitude by tier x distance ramp, tesselateBlock translated
  BranchWindClient                  the policy tick (mode + distance -> flags, section dirtying)
  BranchWindModEvents               mod-bus: the renderer, the model wrapper (after the fog tint wrapper)
config/                             BRANCH_WIND (0 Off, 1 Near, 2 All), DisplaySettings.branchWind(), the button
assets/reignofnether/blockstates/sway_*.json     vanilla's wood / fence blockstates (the `sway` property is ignored)
data/minecraft/tags/blocks/{logs,fences,wooden_fences,mineable/axe}.json   the 12 blocks join the vanilla tags
tools/sway_trees.py                 any tree template -> the same with sway limbs (section 5); --check prints counts
tools/sway_proof.py                 frames of the wind transform on a template, offline (a port of WindField)
gametest/BranchWindGameTests        felling pays wood, shapes unchanged, no orphan entities; the field's bounds
```

## 5. tools/sway_trees.py

`python3 tools/sway_trees.py design/trees/*.nbt -o design/trees/sway` writes each template again with every
wood/log/fence block that is not trunk or root as `reignofnether:sway_<species>_<wood|fence>` with `sway=<tier>`;
the inputs are untouched. A post-processor rather than a `gen_trees.py --sway` flag: PR #88 is still in review rounds
and this branch must not touch its file; it also works on a hand-built tree. When #88 lands, `gen_trees.py` can call
`sway_trees.swayify()` under a flag in one line. The tier:

- the trunk column = the (x, z) with the tallest stack of wood (a 2x2 trunk: the four such columns' centroid); a
  leaning trunk's side-steps (wood beside the column with trunk straight above or below) are trunk too; roots = wood
  at y <= 1 within 3 of the column. Both tier 0: untouched vanilla blocks, no block entity.
- reach = steps from the trunk through wood/fence blocks (26-neighbourhood); a wood block not joined to the trunk
  (a fallen piece) stays vanilla.
- tier = clamp(ceil((reach + 0.15 x height above the first limb) / 1.6), 1, 4); a fence twig is at least tier 3.

Checked on paper: `tools/sway_proof.py <template> -o <dir> --gain 4 --wind 1.7` draws the tier map (trunk grey,
limbs blue-teal-yellow-red by tier) and 8 frames two seconds apart with the wind field applied; peak offsets over the
frames 0.143-0.147 block at the strongest wind, 0.036 on a spruce (stubs only).

Lane A: place the `sway_trees.py` output and the limbs sway; place the plain templates and nothing changes. Both
fell the same, pay the same, block the same.

## 6. Open points for lovish / Lane A

- Leaves: Complementary waves them; without shaders they stand still over a moving twig. A matching leaf sway would
  need the same treatment on 50x more blocks (1500 leaves a tree) - not worth it on block entities; a leaf-only
  core-shader pack would work only without Oculus. Recommendation: leave leaves to the shader.
- `TreeClearing.tree()` accepts a tree only with at least one natural (non-persistent) leaf. The bare and dead trees
  have no leaves, so they are never felled and never pay energy (pre-existing, nothing to do with sway). Lane A
  decides when placing them.
- The near radius (48/60) is a constant in `BranchWindRenderer`; a slider is easy if lovish wants the sway at the
  mid zoom too.
- The hand-over at 60 blocks: the mesh rebuild is asynchronous, so for a frame or two a limb crossing the line is
  drawn by both the mesh and the renderer (coinciding, amplitude 0 there) or by neither. At 60 blocks a limb is a few
  pixels; if it ever shows, the fix is a two-step hand-over (draw once the rebuilt section is uploaded).
- Not yet seen in game: the structure lane of eyes.yml can photograph a `sway_trees.py` output once #88's templates
  are on main (`structure=<.nbt>` with `ref=claude/branch-wind`); the stills would show the limbs drawn (lit the same
  as the trunk), not the motion.
