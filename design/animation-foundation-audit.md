# Animation foundation audit (Lane A, Oct 21 2026; re-audited Oct 29)

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
sees today. **Re-audited Oct 29 2026 against `main` b04b9d20**, after the living army's round 1 (#277-#283), the
Bone Dragon rework (#145, #300) and the Halberdier's keyed thrust (#342). Each score reads "Oct 21 -> Oct 29". The
Oct 21 wording of this table is in git history; section 6 lists what landed.

| Behaviour | What exists (Oct 29) | What is missing (engine side in bold) | Reference technique | Score |
|---|---|---|---|---|
| **Breathing** | `MotionPass` breathes every vanilla-bodied unit (humanoid, illager, villager, golem, witch and spider families) and the 9 keyframe units. The chest pitches, the shoulders rise and the neck follows a beat later, with a quick in-breath and a long out-breath. The phase is state (`BREATH_PHASE`); the rate comes from the shared `EXERTION` (60 ticks at rest, 25 when blown). The generated human rigs and the Bison breathe by themselves (the Bison on the shared exertion). Each unit has its own tempo and amplitude (`Desync`) | The generated human rigs' own breath is a fixed sine that ignores `EXERTION`. Vanilla bodies have no clavicle or neck bone (round 2's jointed bodies). The 11 Alex's Mobs models have nothing | Additive full-body breath, amplitude and rate from exertion (The Isle's rest and pant) | 3 -> **6** |
| **Weight shift / idle variation** | `Idle` is a seeded scheduler per unit. It shifts the weight every 6-14 s (the hips go over, the legs counter-rotate so the feet stay), and adds a grip adjust, a shoulder roll, and glances at real things (the nearest enemy, an ally just hit, its own hero). It stays quiet for 1.5 s after a stop, and the start/stop rock (`SP_PITCH`) settles a stop. The generated rigs get the HumanBase idle clip (READY plus drift) and the pass's hip shift | Feet are not locked, so a big shift on a generated rig slides them (needs foot IK). No stance change over a long hold. The fidget bank has 2 fidgets plus the glance, with nothing per weapon class | A base pose plus seeded additive fidgets, inertialized; the hip shift absorbed by IK legs | 2 -> **5** |
| **Look-at** | Glances spring the head (`SP_HEAD_YAW` / `SP_HEAD_PITCH`, omega 0.35, zeta 0.7: it arrives late and settles) to a real target, for standing units only. Vanilla bodies still use `LookControl` head yaw and pitch. The Crossbow Levy raises and levels its crossbow over 5 ticks and turns its head 0.6 x netHeadYaw (clamped to 40 degrees). `Quadruped.neckLook` splits a share into the neck | The glance moves the head alone. **There is no shared look solver**: no per-bone shares (head, neck, chest), no limits per rig, no spring on a *combat* target. A ranged unit's upper body never turns to its target; the whole body yaws (`yBodyRot`) or the head turns alone | A per-bone look chain (eyes 100%, head 60%, neck 30%, chest 10%) with a spring on the target | 4 -> **5** |
| **Lean into turns / acceleration** | `Kinematics` runs per client tick, shared (0.10 us a unit): speed, acceleration, yaw rate, time since stop and start. Springs lean into a turn (speed x yaw rate, max 12 degrees) and pitch on a start or a brake with an overshoot rock. The arms lag a start and swing on a stop, and the head holds level. This covers every mapped family and, through the whole-body root, the generated rigs | No turn-in-place step and no slope pitch. A generated rig leans as one block: its `body` part is its pelvis, so only the root rolls | A per-tick motion snapshot feeding additive spine and pelvis lean; start and stop transitions | 1 -> **6** |
| **Foot planting / IK** | Unchanged. `Gait` (Bison) and HumanBase's planted-feet walk are played by the stride; anim_lab measures the keyed clips for snaps, joint range and planted feet at the keyed stride | Feet slide when the stride and the speed disagree, and nothing adapts to slopes or stairs. **No ground sampling, no 2-bone IK helper** | Stride warping, foot-lock, 2-bone IK to the sampled ground, the pelvis dropped to the lowest foot | **3** |
| **Hit reactions / stagger / poise** | Directional flinch for every mapped family. `LivingEntityDamageMotionMixin` stamps the blow's direction from the client's damage event (the source's position; vanilla's `hurtDir` is stale on mobs). The torso bends away (a continuous direction, not 8 sectors), the head snaps and the arms jerk. A drop of 12% of max health within 3 ticks turns the flinch into a stagger: the whole body rocks back about the feet. It is additive over the walk and the attack. Keyframe units get the flinch and the stagger on top of their own clips | **No contact event**: the attacker does not recoil when its blow lands, and there is no hit-stop. Generated human rigs flinch with the head and stagger with the whole body only, because their torso cannot bend alone. `Quadruped` and `Dragon` still flinch on `hurtTime`, blind to direction | Additive reactions by direction and size, layered over locomotion; contact drives the target's reaction | 2 -> **5** |
| **Death** | Unchanged. Every humanoid, illager and villager-bodied unit does vanilla's 20-tick 90-degree flip and vanishes; `MotionPass` skips a dying unit. `Quadruped` and `Dragon` fall for 14 ticks, then the renderer rolls them | **No death-pose contract** (nothing can opt out of the flip), the killing blow is not read, and **there is no client-side corpse hold** | A fall seeded by the last hit, then a settled pose held as a corpse | **2** |
| **Combat technique (anticipation / follow-through)** | The Halberdier's thrust is an 18-tick keyed clip played at its own length (`KeyedSwing` sets the swing duration on both sides). The damage is held to the hit key (`HeldBlow`, tick 5), so the target is struck when the blade arrives. `Quadruped` has a horn toss, `Dragon` a bite, and both take a guard stance on a target | Every other unit still plays vanilla's 6-tick arm bump. **The client has no contact moment**: the attacker cannot recoil, and the target's flinch matches the blade only through HeldBlow's timing. No hit-stop | Anticipation -> drive -> contact (hit-stop, recoil) -> recovery, per weapon | 3 -> **4** |
| **Spine / neck wave (dragons, chains)** | `Life.springChain` (an inertial chain on slab slots) exists. The Bone Dragon (#145, merged) and `Dragon` still use `Life.chain` (a lagged sine) and their own bank | **Nothing calls `springChain`**, so tails and necks do not answer a turn or a stop. `BoneMaps` still leaves the Bone Dragon out of `MotionPass` with a stale "#145 owns it" line | Damped springs per link, driven by the parent's acceleration | **3** |
| **Cloth / secondary motion** (tassel, tabard, banners, hair) | Sines on tassels and tabards; `BackBannerLayer` is static; vanilla bodies get the arms' spring lag | Nothing reacts to motion: **`springChain` is unused here too**, and the client has no wind | Spring chains with gravity and wind, clamped | **2** |

Overall, Oct 29: the shared layer the Oct 21 audit asked for exists and is cheap. Every vanilla-bodied unit now
breathes, shifts its weight, fidgets, glances, leans and flinches by direction, each out of step with its neighbours.
What still reads as vanilla: **death** (the flip, then gone), **contact** (no recoil, no hit-stop), the **look** (the
head alone) and anything that should swing (tails, cloth). Those are the next gains, in that order.

## 3. Engine work, in order of visible gain for Lane B

The Oct 29 status follows each item in bold.

1. **A shared per-entity `AnimState` + `Motion` snapshot (client tick, one place).** Once per client tick for every
   unit the camera can see: ground speed, acceleration, yaw rate, vertical speed, airborne, time since stopped /
   started, exertion, last hit (age, direction, magnitude class), attack phase, in a pooled float slab keyed by entity
   id. **Landed (#279, #281, #283): `motion/AnimState`, `AnimStates`, `Kinematics`, `Motion`, `MotionPass`,
   `BoneMaps`. 0.10 us a unit a tick and 0.26 us a unit a frame, 0 bytes allocated. The hit direction comes from the
   damage event, not `hurtDir`. Airborne and attack phase are not in the slab yet.**
2. **`Life.Spring` and `Life.Chain`: critically-damped springs and inertial chains, allocation-free.** **Landed
   (#278): `Life.spring` (implicit Euler, stable at any step), `Life.springAt` (the same pose in every pass) and
   `Life.springChain`. Springs drive the lean, pitch, arms, flinch, stagger, glance and weight. The chain has no user.**
3. **Hit, contact and death contracts.** (a) A client stamp of every hit into `AnimState`. **Landed
   (`LivingEntityDamageMotionMixin`; the size from the synced health drop).** (b) A contact event when a swing lands,
   so the attacker recoils and the target flinches on the same frame. **Missing. Next after (c); for the Halberdier
   it ties to `HeldBlow`'s hit key.** (c) A death-pose contract: a rig that opts in gets `deathTime` 0..20 to play its
   fall seeded by the last hit's direction, `LivingEntityRendererMixin` skips vanilla's flip for it, and the client
   holds the body a few seconds longer (render only). **Missing. First, because it is the biggest vanilla tell left
   and it shows on every humanoid death.**
4. **A look solver** (`Life.look`): per-bone shares, limits, a spring on the target; it replaces the per-rig clamps.
   **Missing (the glance springs the head alone).**
5. **Spring secondary on rigs** (moved up; Oct 21 counted it under item 2): tassels, tabards, banners and tails on
   `springChain`, fed by the snapshot's acceleration and yaw rate. **Missing.**
6. **Cheap foot IK**: ground height under each foot sampled per tick into `AnimState`, a 2-bone IK helper, stride
   warping from the snapshot's speed. Gated by `wantsDetail` (close zoom only). **Missing.**
7. **A far body per unit (geometry LOD), not animation LOD.** The reasoning in section 4 is unchanged. **Not
   started.**
8. **Keep what works**: keys as data (#66) for every clip, `[Anim]` in every eyes log. `motion_check.py` is still not a
   separate tool: anim_lab's measurements (snaps, joint range, planted feet) cover the keyed clips, and the JUnit tests
   in `unit/modelling/motion/` cover the procedural layers. In-game captures come from PR #176 (the cloud animation
   viewer, `eyes.yml` input `anim`).

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

## 6. Re-audit, Oct 29 2026: what landed since Oct 21

| Oct 21 item | Landed as | Where |
|---|---|---|
| Shared `AnimState` + motion snapshot | A slab of 64 floats a unit, stepped per client tick | #279; `motion/AnimState`, `AnimStates`, `Kinematics` |
| Springs and inertial chains | `Life.spring`, `springAt`, `springChain` | #278; `animations/Life.java` |
| Additive pass for vanilla bodies | `Motion` (pure: snapshot -> 8 bones x 6 channels), `MotionPass` (the `ModelPart` adapter after `setupAnim`, restored on return), `BoneMaps` per family | #281, #283 |
| Idle variation | `Idle` scheduler: weight shift, grip, shoulder, real-target glances | #282 |
| Hit stamp | `LivingEntityDamageMotionMixin` -> `AnimStates.onDamage` (the direction from the damage source, the size from the health drop) | #279, #281 |
| Per-unit variety | `Desync` (tempo +-8%, amplitude +-15%), `ZoomGain` per behaviour, 7 sliders and a master switch in the client config | #278 |
| A review stage | `/parade` (`ParadeGround`), A/B on Numpad 0 or `/livingarmy` | #280 |
| Keyed attack length and hit timing | `KeyedSwing` (the swing lasts the clip), `HeldBlow` (the damage at the hit key): the Halberdier | #342 |
| Creature rigs | `Quadruped` (Bison), `Dragon` (Bone Dragon r13, Dive) | #76, #145, #300 |

Perf (the `[AnimBench]` living-army rows on CI): the tick costs 0.10 us a unit and the motion pass 0.26 us a unit,
with 0 bytes allocated per round (budgets 1 and 3 us). In game on lovish's PC, ~150 units with shaders ran at 63-64 fps
with the living army on against 64-66 off. The gate for the next pieces stays the same: posing under 40 us a unit,
the animation share under ~10% of a unit's frame, no allocation per frame or tick, and work only for units being
drawn.
