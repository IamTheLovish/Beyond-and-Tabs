# Animation foundation audit (Lane A, Oct 21 2026)

The engine side of lovish's north star - "a living army: every soldier breathes, shifts its weight and fights with
real technique... watching a single unit should feel like The Isle" - audited from the code on `main`
(7ce498e3, after #66 and #72 merged) and Lane B's open rig branches (#76 quadruped, #79 dragon, #83 halberdier
guard). Lane B owns the models and the clips; this is about what the engine gives them and what it costs at 4v4
scale. Scores are honest and low until earned, against The Isle and the best creature games, not "good for
Minecraft". The perf numbers come from PR #84's bench (`[AnimBench]` notices on its `bar` job) and the new
`[Anim]` log line. Mirror of the Project doc `claude/animation-foundation-audit.md`.

## 1. Facts: what animates a unit today

**GeckoLib is not a dependency and no code touches it.** `bar/build.gradle` has no geckolib artifact, `mods.toml`
requires forge, minecraft, alexsmobs, citadel, the four block mods and their two libraries - nothing else - and
`grep -ri gecko bar/src` finds nothing. The handoff's "GeckoLib 4.8.4 in use" is wrong; what happened is that
Lane B's Oct 21 study note found GeckoLib 4.8.4 *has* a Forge 1.20.1 build and `design/LANES.md` (Oct 20) wrote
"animations via GeckoLib (MIT)" as a direction. The record that holds is `claude/model-tech-geckolib.md`: we chose
not to use it, and nothing since has changed that. Every animation in the game is our own Java on vanilla
`ModelPart` trees. (Citadel, Alex's Mobs' library, does ship its own legacy animation system; we never call it -
it only runs inside the Alex's Mobs models we still render.)

Four animation systems are live, by renderer family (116 renderer classes under `unit/modelling/renderers`):

| System | Units | How it poses | State |
|---|---|---|---|
| **Generated bodies + hand-written procedural `setupAnim`** (`models/gen/*Model` from `tools/dsl_to_game.py`, 242-505 parts each; `HalberdierUnitModel`, `CrossbowLevyUnitModel`, `FieldMedicUnitModel`, `SquireRunnerUnitModel`, `RadiantPaladinUnitModel`) | 5 Sunforged bodies | Every frame: `restPose()` over all parts, then sines for gait/breath/cloth, keyed poses lerped by `attackTime` (Halberdier's thrust is data: `design/anims/halberdier.json` -> `gen/HalberdierAnim`, #66) | Per-entity smoothing in a `WeakHashMap<Entity,float[]>` inside each model |
| **Life layer** (`animations/Life.java`, #72): breath, asymmetric wing beat, chain lag, smoothstep - pure functions of age + entity id | Bone Dragon (on the phantom skeleton); Lane B's `Quadruped` (#76) and `Dragon` (#79) rigs build on it | Called from `setupAnim` | none (pure); the rigs keep their own `STATE` maps (exertion, clip start, last age) |
| **Vanilla keyframe clips** (`KeyframeHierarchicalModel` + `KeyframeAnimations.animate`; Blockbench exports in `animations/*Animations.java`) | 9 inherited RoN models: Royal Guard, Marauder, Necromancer, Wraith, Wretched Wraith, Wildfire, Windcaller, Enchanter, Piglin Merchant | Looping clips keyed on `AnimationState`; one-shots started by `UnitClientEvents.playKeyframeAnimation` from a server packet (`UnitAnimationAction`) | `AnimationState` on the entity |
| **Vanilla / Alex's Mobs models** | everything else: 28 renderers on `AbstractVillagerUnitRenderer`, 9 on `VindicatorUnitRenderer`, skeletons, golems, witches, spiders, parrots...; 11 renderers on Alex's Mobs models (elephant, rhino, bison until #76, phoenix, Celestial/Thunder dragon, leviathan...) | Vanilla `HumanoidModel`/`IllagerModel` walk (sin on `limbSwing`), the arm-swing bump on `attackAnim`, head yaw/pitch from `LookControl`; Alex's Mobs' own clips | vanilla |

Shared across all four: the entity gives the client `walkAnimation` (speed, position), `yBodyRot`/`yHeadRot` and
their previous-tick values, `attackAnim`/`swingTime`, `hurtTime` and `hurtDir` (1.19.4+ `ClientboundHurtAnimationPacket`
carries the hit yaw), `deathTime`, `tickCount`, target/aggressive flags. Each rig reads what it needs itself.

Draw path: one body per unit through vanilla `ModelPart.compile` (every vertex transformed on the CPU, a
`Vector4f` allocated per vertex and a `Vector3f` per face), up to three passes (paint; `TeamColourLayer`'s team mask;
the additive glow) - `SplitPasses` restricts the second and third to the parts that carry texels. Geometry LOD:
the generated bodies hide their `lod` parts past zoom 36 (`GeneratedBodies.wantsDetail`); past zoom 70 units
are icons (`StrategicViewClientEvents`, big units at 94.5). The tester pack ships Embeddium (its `ModelPart`
writer removes the allocation and is several times faster than vanilla's), EntityCulling and ImmediatelyFast.

Tools: `tools/anim_lab.py` (keys as data -> `gen/*Anim.java`), `tools/pose_preview.py` (keys on paper).
`tools/motion_check.py` (snaps, joint range, sliding feet, grip) is planned in #35, not built.

## 2. Behaviour by behaviour, against the bar

Reference technique = how The Isle (Evrima) and the best creature games do it; our score is 1-10 on what a player
sees today, counting #76/#79 as "landing", not landed.

| Behaviour | What exists | What is missing (engine side in bold) | Reference technique | Score |
|---|---|---|---|---|
| **Breathing** | `Life.breath` (quick in, long out, phase per entity) on the Bone Dragon; a plain sine on the five Sunforged bodies; `Quadruped` adds exertion (pants after a run, settles over 10 s) | 60+ vanilla-based units do not breathe at all. Breath moves only the body: no shoulder rise, neck lift, nostril/jaw on creatures. **No shared exertion state** (each rig computes its own) | Additive breath layer on top of every locomotion state, amplitude and rate driven by exertion; the whole upper chain moves (spine, clavicles, neck) - The Isle's rest/pant breathing is a full-body additive | **3** |
| **Weight shift / idle variation** | `Quadruped`: a slow weight shift the legs compensate, graze, head shake, ear flick, tail swish, all phase-offset per entity | Humans: nothing beyond breath + tassel - a block of Halberdiers stands like statues. No idle fidgets, no look-around, no stance change over time, no settle after a stop. **No per-entity random/seeded idle scheduler, no "time since stopped"** | Idle = a base pose + a bank of short additive fidgets picked by a seeded timer, blended with inertialization; weight shift is a hip translation the IK legs absorb | **2** |
| **Look-at** | Vanilla: `LookControl` yaw/pitch into the head only, clamped per rig; `Quadruped.neckLook` splits a share into the neck | No eyes, no torso share, no lag/overshoot, no "notice" moment (snap then settle); ranged units do not track their target with the body. **No shared look solver** (per-bone weights, lag, limits) | Per-bone look-at chain (eyes 100%, head 60%, neck 30%, chest 10%) with a spring on the target so the head arrives late and settles; target chosen by the AI's attention, not only `LookControl` | **4** |
| **Lean into turns / acceleration** | `Dragon` banks on yaw rate (`yBodyRot - yBodyRotO`); `SquireRunner` has an aim turn blend | Nothing on ground units: no lean into a turn, no forward lean on acceleration, no brake pose on a stop, no slope pitch. **Yaw rate, speed and acceleration are not computed anywhere shared** - each rig derives them from raw entity fields | A motion snapshot per tick (velocity, acceleration, yaw rate, ground normal) feeding additive lean on the spine/pelvis; turn-in-place and stop/start transitions as short clips or inertialized blends | **1** |
| **Foot planting / IK** | `Gait` (#76): four-beat lateral walk to trot, planted feet sweep back at the body's speed and stay flat - a kinematic stride match. Humans: stride on vanilla `limbSwing` (speed x 4), knee leads, toe-up | Feet slide whenever the clip's stride and the entity's speed disagree (no stride warping); nothing on slopes, stairs or uneven ground; no foot-lock. **No ground sampling, no 2-bone IK helper, no stride-length knowledge per rig** | Stride warping (scale the stride to the velocity), foot-lock during stance, 2-bone IK to the sampled ground height, pelvis dropped to the lowest foot; cheap when sampled per tick, not per frame | **3** |
| **Hit reactions / stagger / poise** | Vanilla red flash. `Quadruped`/`Dragon`: a flinch on `hurtTime` (head jerks, crouch, ears back) | Direction-blind (the rigs read `hurtTime`, not `hurtDir`), size-blind (the client does not know the damage), no stagger on heavy hits, no poise/stun pose, humans have no flinch at all. **No hit event stamp on the client (direction, magnitude class, source), no additive-reaction helper** | Additive hit-reaction clips blended by hit direction (4-8 directions) and size (flinch / stagger / knockdown), layered over locomotion without stopping it; poise as a gameplay stat drives which one | **2** |
| **Death** | Vanilla: a 20-tick 90-degree flip then the body vanishes. `Quadruped`/`Dragon`: a 14-tick fall (legs buckle, neck drops) then the renderer rolls it | Humans flip like vanilla zombies; nothing is flung by the blow; bodies vanish at 20 ticks; no settle, no corpse. **No death-pose contract (rigs cannot opt out of vanilla's flip), no fling from the last hit, no client-side corpse hold** | Death = blend to ragdoll (or a physics-lite spring chain) seeded with the hit impulse, then a settled pose held as a corpse for a while; The Isle holds bodies | **2** |
| **Combat technique (anticipation / follow-through)** | Halberdier thrust: 4 keys (draw back, drive, hit at 55%, return), keyed as data; `Quadruped` horn toss and `Dragon` bite with anticipation/drive/recovery; guard stance blends in with a target (#83) | Vanilla-based units: the humanoid arm bump. No follow-through into the next action, no hit-stop, no recoil from contact, the body does not step into the blow. **The clip is tied to `attackTime` (6 ticks for everyone) - no per-unit clip length or server-timed windup hook beyond `getAttackWindupTicks`; no contact event on the client** | Attack = anticipation (weight back) -> drive (step in) -> contact (hit-stop frames, recoil) -> recovery, authored per weapon; contact drives the target's reaction; blending with locomotion by bone masks | **3** |
| **Spine / neck wave (dragons, chains)** | `Life.chain` (lagged sine down a chain); Bone Dragon on the phantom skeleton (2 tail links); `Dragon` (#79): neck/tail chains, wings in 3 parts | The wave is a sine with a fixed lag - it does not respond to motion (a turn should whip the tail, a stop should settle it). **No spring/verlet chain helper with inertia from the motion snapshot** | Secondary motion as damped springs per link driven by the parent's acceleration (tails, necks, cloth, banners); one critically-damped spring function covers all of it | **3** |
| **Cloth / secondary motion** (tassel, tabard, banners, hair) | Sines on the tassel and tabard; `BackBannerLayer` static | Nothing reacts to motion or wind. **Same missing spring helper as above; `resources/Wind` is server-side only** | Spring chains with gravity + wind, clamped by cloth limits | **2** |

Overall today: a solid *pattern* (procedural rigs over generated bodies, keys as data) on two creature rigs and five
human bodies, and vanilla motion on ~85% of the roster. The bar is far; the foundation is right.

## 3. Engine work, in order of visible gain for Lane B

1. **A shared per-entity `AnimState` + `Motion` snapshot (client tick, one place).** Once per client tick for every
   unit the camera can see: ground speed, acceleration, yaw rate, vertical speed, airborne, time since stopped /
   started, exertion, last hit (age, direction from `hurtDir`, magnitude class), attack phase. Stored in a pooled
   float slab keyed by entity id (no `WeakHashMap` per model, no per-pass recompute under a shader's shadow pass).
   Rigs read it; nothing else changes. This is what lean-into-turns, stop/start anticipation, directional flinch
   and pant-after-run all need, and it gives them to the 60+ vanilla-based units through one additive pass in
   `LivingEntityRendererMixin` (spine lean, breath, flinch on `HumanoidModel` parts) without Lane B touching each
   model. Biggest gain per line: it makes the whole army move like one living thing.
2. **`Life.Spring` and `Life.Chain`: critically-damped springs and inertial chains, allocation-free.** One
   `spring(state, target, stiffness, damping, dt)` on `AnimState` slots, and a chain that feeds each link the
   parent's acceleration. Covers tails, necks, tassels, banners, cloth, ears, the look-at lag and the settle after
   any motion - the "overshoot and settle" that separates The Isle from a sine. Pure math, Lane B drops it into
   `Quadruped`/`Dragon` and the five human rigs.
3. **Hit, contact and death contracts.** (a) A client stamp of every hit (mixin on `LivingEntity.animateHurt`, which
   has the yaw) into `AnimState`; a server one-shot for heavy hits (stagger) only if gameplay wants a poise stat.
   (b) A contact event when a swing lands (client, from the existing damage path) so the attacker recoils and the
   target flinches on the same frame. (c) A death-pose contract: a rig that opts in gets `deathTime` 0..20 to play
   its fall seeded by the last hit direction, `LivingEntityRendererMixin` skips vanilla's flip for it, and the
   client holds the body a few seconds longer (render only; the entity is already gone server-side).
4. **A look solver** (`Life.look`): per-bone shares, limits, a spring on the target - replaces the per-rig clamps.
5. **Cheap foot IK**: ground height under each foot sampled per tick into `AnimState`, a 2-bone IK helper, stride
   warping from the snapshot's speed. Gated by `wantsDetail` (close zoom only - it is invisible at the RTS zoom).
6. **A far body per unit (geometry LOD), not animation LOD.** The measurements below put the cost in the draw of
   300-500-cube bodies, not in posing them. The hook Lane B asked for: `dsl_to_game.py` emits a second, far root
   (~40-80 cubes) and the renderer swaps models past `DETAIL_ZOOM`; the rig poses whichever is drawn (same bone
   names). Animation decimation (every Nth frame far away) is **not** needed and would show (a block of units
   stuttering at 10 Hz is worse than static).
7. **Keep what works**: keys as data (#66) for every clip, `motion_check.py` in CI, `[Anim]` in every eyes log.

## 4. Perf proof (PR #84)

What was measured and how:
- **`[AnimBench]`** (`bar/src/test/.../perf/AnimationCostBench`, a JUnit test inside `./gradlew build`): 500 units a
  frame on the real model classes without a window - `restPose`, the Halberdier's procedural layer (its `setupAnim`
  transcribed, walk + thrust), Life math for a creature rig (breath, wing beat, a 12-link chain), vanilla's keyframe
  path (Royal Guard WALK), and the body/team/glow draw passes at close and RTS zoom into a counting vertex sink
  (vanilla `ModelPart.compile`, i.e. the CPU transform of every vertex; Embeddium's path is faster). The numbers
  are CI-runner numbers: relative, not a player's PC. Gate: restPose + procedural under 40 us/unit (10x today).
- **`[Anim]`** (`perf/AnimProfile`, `mixin/AnimProfileRendererMixin`): in any client, every 10 s, units drawn per
  frame, posing (prepareMobModel + setupAnim) and whole-unit render ms per frame at p50/p95, per-unit us, zoom and
  detail state. In lovish's `latest.log` and the eyes log next to `[Frame]`.

Results (CI, PR #84's `bar` job, runs 39580028441 and 39582976366 - the `[AnimBench]` notices):

| Phase (500 units a frame, median of 30 frames, vanilla `ModelPart` path, CI runner) | before #84 | with #84 |
|---|---|---|
| `restPose` (Halberdier, 279 parts) | 0.45 ms | 0.46 ms |
| procedural layer (Halberdier walk + thrust, `setupAnim` transcribed) | 0.02 ms | 0.02 ms |
| Life math (breath + wing beat + 12-link chain, a creature rig) | 0.03 ms | 0.03 ms |
| keyframe layer (Royal Guard WALK, `KeyframeAnimations.animate`) | 2.19 ms | **0.19 ms** (bone lookup cached) |
| body pass, close zoom (detail on, 8184 verts/unit) | 27.3 ms | 27.6 ms |
| body pass, RTS zoom (detail off, 5736 verts/unit) | 17.4 ms | 17.6 ms |
| team pass, RTS zoom (1104 verts/unit) | 11.3 ms | **2.4 ms** (subtrees pruned) |
| glow pass, RTS zoom (624 verts/unit) | 10.9 ms | **1.4 ms** (subtrees pruned) |
| Radiant Paladin body pass, close / RTS (505 cubes, 12120 / 10488 verts) | 43.8 / 37.7 ms | 44.3 / 37.6 ms |
| **per unit: animation (restPose + procedural)** | **0.9 us** | **1.0 us** |
| per unit: draw, RTS zoom / close | 79.2 / 98.9 us | **42.7 / 62.7 us** |
| animation as a share of a unit's CPU frame cost | 1% | 2% |

Real client (the eyes tour on this branch, Mesa software GL, 5-30 units in view, the first commit's jar): `[Anim]`
reports 4-40 us of posing per unit against 500-2000 us of unit render (software rasterising) - **animation 1-2%** at
every zoom (5, 30, 50; icons at 200), no new exceptions, the mixin applied.

Reading: the animation layers are not the cost. Posing a 279-part body with its gait, breath, cloth and a keyed
thrust is ~1 us; 500 of them are half a millisecond, and a creature rig's Life math (breath, wing beat, a 12-link
chain wave) is 0.1 us on top. The cost is the draw: vanilla walks every part it can see (a pose push with two matrix
copies, translate/rotate, pop - ~280 times a unit) and transforms every vertex on the CPU, 8-12k of them per body,
three passes. Two things fell out of the first run and are in #84: the team and glow passes drew 1104 and 624
vertices but cost as much as the 5736-vertex body pass, because `SplitPasses` only skipped cubes and the whole tree
was still walked - now the subtrees with nothing to draw are hidden for the pass (`PassPruning`), identical output,
5x cheaper, 36 us a unit saved at the RTS zoom; and vanilla's keyframe path asked `getAnyDescendantWithName` (a
stream over the tree) per bone per frame - cached, 11x cheaper, the 9 RoN keyframe units now cost what a procedural
rig does. What remains is the body pass: ~35 us a unit at the RTS zoom in vanilla's path (Embeddium's writer makes
the vertex part several times cheaper; the tree walk stays), ~75 us for the 505-cube Paladin. The walk is long
because `dsl_to_game.py` emits nearly a part per cube (341 cubes in 279 parts: every rotated cube is its own part,
`body_r10`...); baking static rotated cubes into their parent's polygons would halve it, but vanilla's
`ModelPart.Cube` only builds axis-aligned boxes - a mixin-level job, parked behind the far body.

Decision on LOD rules: **no animation LOD.** Decimating or skipping procedural layers past the detail zoom would save ~0.5 ms
per 500 units and show (a block of units stuttering at 10 Hz reads worse than static). Lane B should spend the
animation budget freely: a rig can do ten times today's work per unit before it registers. The lever that matters
is geometry at the RTS zoom - a far body of ~40-80 cubes per unit (section 3, item 6) would cut the body pass
~5x where most of a match is watched, and that is a Lane B asset plus a one-line swap in the renderer, after the
rigs are right. `[Anim]` and `[AnimBench]` stay as the gate: a change that moves the animation share past ~10% of a
unit's cost, or the per-unit posing past 40 us, gets looked at.

## 5. Open questions for Lane B (posted on #35)

Which engine piece first: the `AnimState`/`Motion` snapshot (1), the springs (2), or the hit/death contracts (3)?
And the cube budget for a far body (6) once lovish's PC gives an `[Anim]` line at 300+ units.
