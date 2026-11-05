# Animation foundation audit (Lane A, Oct 21 2026; re-audited Oct 29 and Nov 5)

> **Re-audit Nov 5 2026** against `main` e92a89e1 (Characters and Animation lane). Sections 2, 3 and 6 are
> current; section 1 is the Oct 21 inventory (the motion pass of #281 is a fifth, shared layer on top of the
> four systems); section 4 is PR #84's measurement.

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
sees today. **Re-audited Nov 5 2026 against `main` e92a89e1**, after #373 (death pose), #375 (contact), #377 (look
solver), #382/#399/#405 (death finishes), #412 (smooth turning), #421 (blow on the strike), #440/#509 (dragon passes),
#441 (walking a line), #448 (hit flash), #469 (body-yaw hold) and #512 (no look-around while waiting for a path). Each score
reads "Oct 21 -> Oct 29 -> Nov 5"; the older wording is in git history and section 6 lists what landed.

| Behaviour | What exists (Nov 5) | What is missing (engine side in bold) | Reference technique | Score |
|---|---|---|---|---|
| **Breathing** | `MotionPass` breathes every vanilla-bodied unit (humanoid, illager, villager, golem, witch and spider families) and the 9 keyframe units: chest, shoulders, then the neck a beat later; the rate from the shared `EXERTION` (60 ticks at rest, 25 blown); per-unit tempo and amplitude (`Desync`). The generated rigs and the Bison breathe by themselves; the dragon's ribcage no longer strobes on a glide change (#509) | The generated human rigs' breath ignores `EXERTION`. Vanilla bodies have no clavicle or neck bone. The 11 Alex's Mobs models have nothing | Additive full-body breath, amplitude and rate from exertion | 3 -> 6 -> **6** |
| **Weight shift / idle variation** | `Idle` (seeded per unit): a weight shift every 6-14 s with the feet kept, 3 fidgets (grip adjust, shoulder roll, a 50-degree look-around) and glances at real things (nearest enemy, an ally just hit, its own hero); quiet 1.5 s after a stop. On the server side, the look-around goal holds while a unit waits for its path (#512: `RandomLookAroundUnitGoal`, `MoveToTargetBlockGoal`). Generated rigs: the HumanBase idle clip | Feet are not locked, so a big shift on a generated rig slides them. No stance change over a long hold; nothing per weapon class | Base pose + seeded additive fidgets, inertialized; the hip shift absorbed by IK legs | 2 -> 5 -> **5** |
| **Look-at** | `Life.look` (#377): the look spring (`SP_LOOK_YAW/PITCH`) is shared between chest and head with per-bone shares and limits; a ranged unit drawing on a target (`AIM`) turns its upper body to it (chest share 0.45, limit 25 degrees), and on a vanilla body the arms keep the model's aim. Glances still spring the head | No neck bone on vanilla bodies (chest + head only); the eyes do not lead; creature rigs keep their own `neckLook` | A per-bone look chain (eyes 100%, head 60%, neck 30%, chest 10%) with a spring on the target | 4 -> 5 -> **6** |
| **Turning / lean / acceleration** | `Kinematics` per client tick (0.10 us a unit): lean into turns (max 12 degrees), start/brake pitch with an overshoot rock, arm lag on a start. Smooth turning (#412): the drawn body yaw is a spring (`SP_TURN`, `Kinematics.bodyYawAt`), so a new order swings the body round instead of spinning it 90 degrees a tick; units walk a line without rocking (#441); every unit ticks on the client so the yaw spring never holds (#469) | No stepping feet in a turn on the spot; no slope pitch; a generated rig leans as one block | A per-tick motion snapshot feeding additive spine/pelvis lean; turn-in-place steps | 1 -> 6 -> **7** |
| **Foot planting / gait** | `Gait` (the Bison) and HumanBase's planted walk for the generated humans. **Open: #523, the gait bridge**: every unit on vanilla's one-bone legs (~60 types) gets a planted-foot stride (the stance sweeping back steadily, the swing eased), hips that drop so one sole is always on the ground, a 1.6 px step lift and hip sway (JUnit `GaitBridgeTest`: stance slip 0.36-0.82 -> 0.27-0.30 of the ground speed, lower-sole lift up to 8 px -> 0) | Vanilla-legged units slide and float until #523; a full plant needs a retimed cadence and retimed arms. **No ground sampling, no 2-bone IK helper**; nothing adapts to slopes or stairs | Stride warping, foot-lock, 2-bone IK to the sampled ground, the pelvis dropped to the lowest foot | 3 -> 3 -> **3** (4-5 with #523) |
| **Hit reactions / stagger** | Directional flinch for every mapped family from the damage event's direction (`LivingEntityDamageMotionMixin`); a stagger past a 12% health drop in 3 ticks; keyframe units included. Contact (#375): the attacker's arms and torso jolt back (`SP_RECOIL`, `Kinematics.onContact`) on the frame its blow lands. Hit flash (#448) on vanilla's overlay row, no extra draw | **No hit-stop.** `Quadruped` and `Dragon` still flinch on `hurtTime` (Quadruped.java:338, Dragon.java:773), blind to direction. Generated human rigs flinch with the head and stagger with the whole body only | Additive reactions by direction and size, layered over locomotion; contact drives the target's reaction | 2 -> 5 -> **6** |
| **Death** | Death pose (#373): humanoid, illager and villager bodies fall rigidly and the generated human rigs fall jointed (elbows, knees), away from the killing blow: reel, fall, one bounce, still by tick 18; `Corpses` holds up to 96 bodies after the server removes the unit; faction finishes (#382, #399, data in death_finishes.json); weapons lie down with the body (#399, #405) | Every body outside those families still does vanilla's flip and vanishes: golems, spiders, keyframe rigs, slimes and every Alex's Mobs model (`BoneMaps.build` gives them no death parts); the Bison and the dragons fall by their own rigs (Quadruped.java:348) but get no corpse hold. **No fallback fall for any other model**, and every death still draws the cost-sized blast (`BarFxServerEvents.onLivingDeath`) | A fall seeded by the last hit, a settled corpse, a finish | 2 -> 2 -> **5** |
| **Combat technique (anticipation / follow-through)** | The Halberdier's keyed 18-tick thrust (`KeyedSwing`, `HeldBlow`). The blow on the strike for every family (#421, `BlowTiming`): vanilla's arm swing lands its damage at tick 3, the generated chop at tick 4, the Bison's toss at tick 9, so the target's flinch, the spark and sound and the attacker's recoil all fall on the visible strike | Every vanilla body still plays the 6-tick arm bump: no wind-up, no follow-through, no weapon classes. **No hit-stop, no crossfade into or out of an attack** | Anticipation -> drive -> contact (hit-stop, recoil) -> recovery, per weapon class | 3 -> 4 -> **5** |
| **Clip blending** (new row) | HumanBase crossfades idle and walk by the stride; the motion pass is additive, so it never pops | **The 9 keyframe units hard-cut between clips** (`AnimationState` start/stop); the generated bodies blend into their attack and cast poses with their own per-model ramps | Inertialized blends: a 4-6 tick crossfade on every clip change | **3** |
| **Melee spacing (the fight's read)** (new row) | Melee ring slots (`Engage`) spread attackers round a target; a knockback gate per target is open (#463) | **Pairs are not held at weapon reach**: the ring is one fixed radius (`Engage.RING_RADIUS`), not the weapon's reach, and nothing measures how far fighting pairs overlap. The blow is timed, not spaced | Attack positions at the weapon's reach, a facing turn before the strike | **3** |
| **Spine / neck wave (dragons, chains)** | `Life.springChain` exists. The Bone Dragon and `Dragon` use `Life.chain` (a lagged sine) and their own bank; the dragon passes (#440, #509) fixed the roll and the strobe | **Nothing calls `springChain`**. `BoneMaps` still returns NONE for the Bone Dragon with a stale "PR #145 ... owns its motion" line (BoneMaps.java:113; #145 merged Oct 28) | Damped springs per link, driven by the parent's acceleration | 3 -> 3 -> **3** |
| **Cloth / secondary motion** | Sines on tassels and tabards; a captain's back banner sways on a slow per-unit sine (`BackBannerLayer`); vanilla bodies get the arms' spring lag | Nothing reacts to motion: **`springChain` is unused here too**; no client wind for units | Spring chains with gravity and wind, clamped | 2 -> 2 -> **2** |

Overall, Nov 5: death, contact, look and turning landed, so a single unit reads alive. What still reads as "weird
moving units and animations smacking each other" (lovish, Nov 5) is **how two units fight**: no wind-up or
follow-through on ~60 vanilla bodies, hard cuts between clips, pairs that overlap instead of standing at reach, no
hit-stop. Then **feet that slide** (#523) and the **bodies with no fall** (every family outside humanoid, illager, villager and the generated humans). Secondary motion (cloth, tails) is still
sines.

## 3. Engine work, in order of visible gain

lovish (Nov 5): "lets focus on base animation and refining that so we have good quality animations and effects for
the troops" and "we need to make base unit animations more and more fluid so the battles look more articulated rather
than just weird moving units and animations smacking each other". This order is the Characters and Animation lane's
plan. Every piece goes in at the shared entry so every unit gains, piloted on the Field Medic and the Halberdier. The
status follows each item in bold.

1. **Shared `AnimState` + `Motion` snapshot.** **Landed (#279, #281, #283): 80 floats a unit (`AnimState.STRIDE`),
   0.10 us a unit a tick, 0.26 us a frame, 0 bytes allocated.** Airborne and attack phase are still not in it.
2. **Springs and chains.** **Landed (#278): `Life.spring`, `springAt`, `springChain`.** The chain has no production
   caller (only `LifeSpringTest`).
3. **Hit, contact and death contracts.** (a) The hit stamp: **landed.** (b) Contact: **landed (#375)**, and the blow
   on the strike: **landed (#421).** (c) The death pose and corpse hold: **landed (#373)**, finishes (#382, #399).
   **Left: a fallback fall for every other model, and retiring the blast.**
4. **Look solver.** **Landed (#377).**
5. **Smooth turning.** **Landed (#412, #441, #469, #512).**
6. **The gait bridge** (vanilla one-bone legs: planted stride, hip drop, step lift, sway). **Open: #523.**
7. **Fight fluidity (next):** (a) attacks by weapon class on the vanilla bodies (wind-up, the drive on the strike tick
   `BlowTiming` already holds, follow-through, recovery) with hit-stop on contact; (b) a crossfade on every clip change
   (keyframe and generated); (c) melee pairs held at weapon reach, facing before the strike; (d) tests that the strike
   frame and the damage tick agree within one tick, and that measure pair overlap. **Not started.**
8. **Rig articulation for the pilot bodies** (lovish: "they need better articulation"): elbows, wrists, knees, spine
   and hips where they show, on the Field Medic and the Halberdier first. **Not started.**
9. **Spring secondary on rigs**: tabards, banners and tails on `springChain`. **Missing.**
10. **Shared troop effects**: dust at footfalls, swing trails, impact puffs: one layer every unit uses, styled per
    faction. **Missing.**
11. **Cheap foot IK** (the ground under each foot, 2-bone IK, close zoom only). **Missing.**
12. **A far body per unit (geometry LOD).** Section 4's reasoning is unchanged. **Not started.**
13. **Keep what works**: keys as data (#66), `[Anim]` in every eyes log, the JUnit tests in `unit/modelling/motion/`.
    In-game captures will come from PR #176 (the cloud animation viewer, still open); until then the eyes close-ups
    (07b-y, plus 07zg-zl for vanilla legs on #523).

Small fixes found on the way (no PR yet): the stale "#145" line in `BoneMaps`; `Quadruped` and `Dragon` flinching on
`hurtTime` instead of the stamped direction.

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
is geometry at the RTS zoom - a far body of ~40-80 cubes per unit (section 3, item 12) would cut the body pass
~5x where most of a match is watched, and that is a Lane B asset plus a one-line swap in the renderer, after the
rigs are right. `[Anim]` and `[AnimBench]` stay as the gate: a change that moves the animation share past ~10% of a
unit's cost, or the per-unit posing past 40 us, gets looked at.

## 5. Open questions for Lane B (posted on #35)

Which engine piece first: the `AnimState`/`Motion` snapshot (1), the springs (2), or the hit/death contracts (3)?
And the cube budget for a far body (now item 12) once lovish's PC gives an `[Anim]` line at 300+ units.

## 6. What landed since Oct 21

| Item | Landed as | Where |
|---|---|---|
| Shared `AnimState` + motion snapshot | A slab of 80 floats a unit (64 before the look and turn slots), stepped per client tick | #279; `motion/AnimState`, `AnimStates`, `Kinematics` |
| Springs and inertial chains | `Life.spring`, `springAt`, `springChain` | #278; `animations/Life.java` |
| Additive pass for vanilla bodies | `Motion` (pure: snapshot -> 8 bones x 6 channels), `MotionPass`, `BoneMaps` per family | #281, #283 |
| Idle variation | `Idle`: weight shift, grip, shoulder, look-around, real-target glances | #282 |
| No look-around while waiting for a path | `RandomLookAroundUnitGoal`, `MoveToTargetBlockGoal` (server goals) | #512 |
| Hit stamp | `LivingEntityDamageMotionMixin` -> `AnimStates.onDamage` | #279, #281 |
| Per-unit variety | `Desync`, `ZoomGain`, 7 sliders and a master switch | #278 |
| A review stage | `/parade` (`ParadeGround`), A/B on Numpad 0 or `/livingarmy` | #280 |
| Keyed attack length and hit timing | `KeyedSwing`, `HeldBlow` (the Halberdier) | #342 |
| Death pose and corpse hold | `DeathFall` (rigid on vanilla bodies, jointed on generated humans), `Corpses` (96 at most) | #373 |
| Death finishes | `DeathFinish` + death_finishes.json; the halberd and the crossbow lie down with the body | #382, #399, #405 |
| Contact | `SP_RECOIL`, `Kinematics.onContact`: the attacker jolts on the landing frame | #375 |
| Look solver | `Life.look`, `SP_LOOK_YAW/PITCH`, `AIM` | #377 |
| Smooth turning | `SP_TURN`, `Kinematics.bodyYawAt` | #412; #441 (no rocking on a line); #469 (always ticks) |
| Blow on the strike | `BlowTiming` by body family, `HeldBlow` for every melee unit | #421 |
| Hit flash | `HitFlash` on vanilla's overlay row | #448 |
| Creature rigs | `Quadruped` (the Bison), `Dragon` (Bone Dragon r13, Dive), the dragon passes | #76, #145, #300, #440, #509 |

Perf: the tick costs 0.10 us a unit and the motion pass 0.26 us a unit, 0 bytes allocated (budgets 1 and 3 us). In
game on lovish's PC (Oct 27), ~150 units with shaders ran at 63-64 fps with the living army on against 64-66 off. The
gate for the next pieces stays: posing under 40 us a unit, the animation share under ~10% of a unit's frame, no
allocation per frame or tick, work only for units being drawn.
