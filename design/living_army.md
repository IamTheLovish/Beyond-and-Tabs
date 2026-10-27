# Living army - motion for every unit (spec, round 1 of 3)

Status: design approved section by section by lovish on Oct 27 2026; this file is the written spec for his review.
Lane: animation (claim `living-army`, branch `claude/living-army`). Client only: no server rule, no gameplay number.

North star (Oct 21): "A living army: every soldier breathes, shifts its weight and fights with real technique...
Watching a single unit should feel like The Isle." lovish's aim for this work: animation as close to The Isle as
possible, and better where The Isle is weak - physical follow-through, real hit directions, no two units in step.

Background: `design/animation-foundation-audit.md` (sections 3-4). About 85% of the roster still moves with vanilla
motion; only the Bison (`Quadruped`), the Bone Dragon (`Dragon`) and the five Sunforged human bodies (`HumanBase` +
their own `setupAnim`, the Halberdier's thrust as data) are bespoke. Each of those rigs computes its own speed, turn
and exertion in a `WeakHashMap`. Posing costs ~1 us a unit (2% of a unit's frame); the CPU vertex draw is the cost.

## 1. The parts (agreed)

| Part | What it is | Where |
|---|---|---|
| `AnimState` | One per-tick motion snapshot per unit in a pooled float slab, slot by entity id: ground speed, acceleration, yaw rate, vertical speed, time since stop / start, exertion, last hit (age, direction from `hurtDir`, size class), the idle scheduler's state, spring slots (previous + current value each). No allocation per tick. | `unit/modelling/motion/AnimState.java`, `AnimStates.java` (the slab + tick) |
| `Life.spring`, `Life.springChain` | Allocation-free critically damped spring on a slab slot, and an inertial chain that feeds each link its parent's acceleration. The existing pure helpers (`breath`, `chain`, `bump`, `wander`...) stay as they are; `Life.chain` keeps its meaning (a lagged sine), so the inertial one has its own name. | `animations/Life.java` |
| `Idle` | Seeded scheduler: weight shift plus a fidget bank (grip adjust, glance, shoulder roll, look-around), one at a time. | `motion/Idle.java` |
| `Motion` | Pure function: `AnimState` + partial tick + gains -> per-bone offsets (rotation xyz, translation xyz) in a reused float array. Renderer-agnostic: the GPU-instancing spike (`claude/spike-gpu-units`) can consume the same offsets as bone transforms. | `motion/Motion.java` |
| `MotionPass` | The adapter: after `setupAnim` in `LivingEntityRendererMixin`, adds `Motion`'s offsets onto vanilla `ModelPart`s through a bone map per model family (humanoid, illager, villager, skeleton, golem, witch, spider...). Custom rigs opt out with a marker and read `AnimState.of(entity)` themselves. | `motion/MotionPass.java`, `motion/BoneMaps.java`, mixin hook |
| `ZoomGain` | Realistic up close, gently larger as the camera zooms out, one 0-200% slider per behaviour. | `motion/ZoomGain.java` (pure) + `ReignOfNetherClientConfigs` |
| Parade ground | `/parade`: blocks of every unit type on a scripted loop, a live A/B toggle. | `motion/ParadeGround.java` (+ command) |

Who gets what in round 1:
- **~60 vanilla-bodied units** (villager, illager, humanoid, skeleton, golem, witch, spider families): every behaviour
  in section 2 through `MotionPass`.
- **Custom rigs** (`HumanBase` bodies, Bison, Halberdier; the Bone Dragon after PR #145 merges): opt out of
  `MotionPass`; switch their private exertion / turn state to `AnimState` and gain directional flinch and stagger.
  Their own idles (from `design/anims/*.json`) stay theirs.
- **The 9 RoN keyframe-clip units** (Royal Guard, Marauder, Necromancer, Wraith, Wretched Wraith, Wildfire,
  Windcaller, Enchanter, Piglin Merchant): breath and flinch only, on top of their clips.
- **Not in round 1:** the 11 Alex's Mobs creature models (no bone map -> the pass does nothing; round 2).

## 2. Behaviours

All behaviours are additive offsets on whatever the body already does (walk, clip, attack). Each has a zoom gain:
x1.0 (realistic) at zoom <= 20, easing (smoothstep) to x(1+k) at zoom 70, where units become icons. The gain scales
amplitude only, never speed or rate (faster reads as twitchy, not alive), and the result is clamped to the bone's
joint limits. Each behaviour has a slider 0-200% (default 100%) in `reignofnether-client.toml`.

| Behaviour | What you see | Driven by | k (x at zoom 70) | How it compares |
|---|---|---|---|---|
| **Breath** | Chest swells, then shoulders rise, then the neck lifts a beat later; quick in-breath, long out-breath. Period ~60 ticks at rest down to ~25 when blown; exertion settles over ~10 s. | exertion (speed above a walk, recent attacks) | 1.5 (x2.5) | The Isle: full-body additive breath with pant states - we match the layering and exertion; vanilla bodies have no clavicle bone until round 2. Total War / Bannerlord: no visible breath on soldiers. Warcraft III: breath baked into the stand loop, identical per unit. |
| **Weight shift** | Hips slide onto one leg, the other knee softens; every 6-14 s, seeded. One settling shift after a stop. | `Idle`, time since stop | 1.0 (x2) | The Isle: hip shift the IK absorbs - our feet do not lock until round 2 (foot IK). Warcraft III / League: authored stand variants that loop; ours never repeats cleanly. |
| **Idle fidgets** | One at a time, seeded gaps 4-12 s, none in the 1.5 s after a stop: grip adjust, glance, shoulder roll, look-around. Suppressed while moving or fighting. Glances look at real things: the nearest enemy in sight, an ally that was just hit, the unit's own commander or hero; random when nothing is near. | `Idle` | 1.3 (x2.3) | Warcraft III: "Stand 2/3/4" fidget clips picked at random - the RTS precedent; ours are seeded per unit, so a block never fidgets together, and the glances react to the battle. The Isle: look-at by attention. |
| **Lean into turns** | Body rolls into the turn by speed x yaw rate (max ~12 degrees); the head leads the turn and the body follows. | yaw rate, speed | 1.5 (x2.5) | The Isle: lean + head lead on every creature. Total War: none on infantry (turns are rotations). Bannerlord: slight on horses only. |
| **Accelerate / brake** | Pitch forward on a start, back on a hard stop, then a spring overshoot: one or two small rocks before still. | acceleration, time since stop / start | 1.5 (x2.5) | The Isle: start / stop transitions with settle. Total War / Bannerlord: start / stop clips, no overshoot. Hades: strong anticipation and settle for readability - our far-zoom gain borrows that. |
| **Directional flinch** | Torso bends away from the hit side (8 sectors), head snaps, arms jerk; seeded variance, no two alike. | `hurtDir` vs body yaw; size class from the health drop the client sees | 2.0 (x3) | The Isle: weak here - generic flinch, direction barely read. For Honor: directional hit reactions per guard side - our model. Bannerlord: directional reactions. Hades / League: big readable hit flashes at a top-down camera - why flinch gets the highest gain. |
| **Stagger (heavy hits)** | The whole body rocks back as if half a step in the hit direction, then recovers with a spring settle. Pose only: the unit does not move. | size class "heavy" | 2.0 (x3) | For Honor / Bannerlord: staggers with real displacement (gameplay); ours is cosmetic until round 2's contact work. The Isle: no stagger. |
| **Spring / chain secondary** | Arms and head lag and overshoot on turns and stops; on rigs, tails, ears, tassels and tabards whip on a turn and settle on a stop. | body acceleration into `Life.spring` / `springChain` | 1.2 (x2.2) | The Isle: physics-driven tails and necks - we match the response (a turn whips, a stop settles) with springs instead of physics. Total War: cloth sims on lords only. |
| **De-sync (always on)** | Per unit, seeded by entity id: tempo +-8% and amplitude +-15% on everything above. A block never moves in step. | entity id | - | Total War: per-soldier animation offsets and variants - the precedent. The Isle: individual players, never in step by nature. Warcraft III: units in step. |

lovish's answers (Oct 27): the k values as proposed; heavy hits stagger (pose only); glances at real targets.

## 3. Data flow and performance budget

**Client tick (20 Hz), `AnimStates.tick`:** for each loaded unit, update its slab slot (about 48 floats):
- kinematics from `x - xo`, `z - zo`, `y - yo`, `yBodyRot - yBodyRotO` (wrapped): speed, acceleration (smoothed),
  yaw rate, vertical speed, time since stop / start, exertion (rises above a walk and with attacks, decays ~10 s);
- hit stamp: when `hurtTime` becomes fresh, read `hurtDir` and the health drop since the last tick (health is synced
  to the client) -> direction relative to body yaw and size class: **stagger** when the drop is at least 12% of max
  health, otherwise **flinch** (a hit that shows no drop, e.g. absorbed by a shield, still flinches); the threshold is
  a client constant, tuned in the parade scene;
- the `Idle` scheduler steps; a glance target is looked up only when a glance fires (one 16-block entity query per
  unit every 4-12 s), never per tick;
- springs step on the fixed 20 Hz tick and keep their previous and current values.
Slots are freed when the entity is removed or unseen for 100 ticks; the slab grows by doubling and never shrinks.

**Every frame, `MotionPass`** (inject after `setupAnim` in `LivingEntityRendererMixin`):
- `Motion.pose(state, partialTick, gains, out)` - springs read as lerp(previous, current, partialTick), so a frame is
  a pure function of the tick state: the shader pack's shadow pass poses the unit identically and nothing is
  integrated twice;
- the bone map for the model class (cached; no map = no-op) adds the offsets onto the `ModelPart`s.

**Budget (gates):**
- `AnimationCostBench` (JUnit, CI) gets a `MotionPass` row: < 3 us per unit per pass and zero bytes allocated
  (thread allocated-bytes counter); the existing posing gate (< 40 us per unit) stays.
- `AnimStates.tick` < 1 us per unit (500 units = 0.5 ms a tick).
- In game: `[Anim]` gains a `motion` column; the parade scene at 300 units, motion on vs off, with and without
  Complementary shaders: frame time no more than 2% worse (lovish, Oct 27). Over that, it does not merge.

How it compares: The Isle (Unreal anim graph) and Total War both sample gameplay state at a fixed rate and evaluate the
pose per frame from cached state; ours is the same split with one shared state per unit instead of one per animation
blueprint.

## 4. The parade ground and testing (tests first)

**`/parade`** (op-only dev command, its own class; not part of any match): stamps a flat pad, spawns a 3x3 block of
every unit type grouped by family plus two lines of foes, and runs a 60 s loop through the existing order system -
stand 10 s, march 20 blocks, wheel 90 degrees, march back, halt hard, then the foes land light and heavy hits from
front, sides and rear on a timer - and repeats. It uses existing spawn, move and damage calls only: no gameplay rule
or number changes. `/parade stop` clears it.
- **A/B toggle:** key **Numpad 0** (rebindable, category RTS; F8 is taken by fog of war / spectator view) and
  `/parade motion on|off`: switches `MotionPass` live, at close and RTS zoom.
- **Eyes:** a parade scene for the eyes tour in its own file; wired into `EyesDirector` only after PR #176 (which owns
  `EyesDirector` / `EyesAnimScene`) merges.

**Tests, each written first and shown failing on `main` before the code:**
- JUnit (pure math, `bar/src/test/.../unit/modelling/motion/`):
  - `Life.spring`: settles with no overshoot at critical damping; bounded for any step size; `springChain` tip lags
    the root and comes to rest.
  - `Idle`: deterministic per seed; never the same fidget twice in a row; quiet for 1.5 s after a stop; neighbouring
    ids start on the same tick in under 2% of starts (independent seeds cannot promise never).
  - Flinch: a hit from the left bends the torso right (all 8 sectors); size class follows the health drop.
  - Lean follows the turn's sign; accelerate pitches forward; brake pitches back then settles.
  - `ZoomGain`: exactly 1 at zoom <= 20, 1 + k at zoom >= 70, monotone between.
  - De-sync: 100 ids give tempos spread across +-8%, no two equal.
  - `Motion`: identity when off; the same frame posed twice (view and shadow pass) gives identical bones; joint
    clamps hold for extreme inputs; a NaN input resets the slot instead of propagating.
- Bench: the `MotionPass` row in `AnimationCostBench`.
- GameTest: `/parade` spawns every unit type without an error and a full loop runs headless.
- In game: the 300-unit 2% check with and without shaders; **lovish's OK in the parade scene before anything
  visible merges.**

How it compares: Bannerlord and Total War tune in formation sandboxes; The Isle's team tunes single creatures. The
parade gives both: a block of every type at both zooms.

## 5. Compatibility, failure handling, slices

**Compatibility:**
- Embeddium, Oculus, shader packs: we only change `ModelPart` angles before the draw, which every renderer honours;
  the shadow pass poses identically.
- GPU-instancing spike: `Motion` outputs offsets, not `ModelPart` edits - an instanced renderer brings its own
  adapter.
- Alex's Mobs models: no bone map, untouched until round 2.
- Rigs being worked on: opting out is one marker; `Dragon.java` is not edited until PR #145 merges; PR #176's files
  are not touched.
- Hero possession: the possessed unit's own body gets no flinch or lean (no camera shake from it); other units do.
- Multiplayer: client only, seeded by entity id - every client sees the same variety.

**Failure handling:**
- An exception in `MotionPass` is caught; the pass disables itself for that model class only, logs one
  `[Motion] disabled for <class>: <reason>` line, and the unit draws as before.
- Unknown model class: no map, skipped, logged once.
- Per-bone joint clamps; a non-finite value resets that unit's springs.
- Master switch `living_army = true` and the per-behaviour sliders in `reignofnether-client.toml`.

**Slices (each its own PR, tests first):**
1. Core math (invisible): `Life.spring`, `Life.springChain`, `ZoomGain`, de-sync seeds + JUnit.
2. `AnimState` slab + tick + hit stamp (invisible) + JUnit + the bench row.
3. Parade ground: `/parade`, the loop, the toggle + GameTest. lovish can watch today's motion before anything new.
4. `MotionPass` for the humanoid and illager families: breath, weight shift, lean, accelerate / brake, flinch,
   stagger, secondary. First visible PR: lovish's in-game OK + the 2% measurement.
5. `Idle`: fidgets and real-target glances on those families. Visible: lovish's OK.
6. The remaining families (villager, skeleton, golem, witch, spider...), breath + flinch for the 9 keyframe units,
   the custom rigs reading `AnimState` (not `Dragon.java` while #145 is open). Visible: lovish's OK.

## 6. Roadmap

| Round | What | Builds on |
|---|---|---|
| **1 (this spec)** | `AnimState`, springs, `Idle`, `MotionPass` on vanilla bodies, `ZoomGain`, parade ground | - |
| **2** | Jointed family bodies generated in code with the same UVs (elbows, knees, a 2-3 part spine, a neck); foot IK with stride warping (no sliding feet); a look solver (eyes / head / neck / chest shares, spring on the target); inertialized blends between states; contact hit-stop and recoil (attacker and target on the same frame); directional deaths seeded by the last hit, with corpses held client-side; the 11 Alex's Mobs creature models | `AnimState` hit stamp + springs; the parade scene as the review stage |
| **3** | Smooth skin: linear blend skinning on the existing `MeshBody` triangle meshes; per-weapon combat technique (anticipation -> drive -> contact -> recovery, authored per weapon class) | Round 2's joints and contact events |

## 7. References

Ideas and technique only - no assets (AGENTS.md section 2).

| Game | What we take | Where it is weak (where we aim to be better) |
|---|---|---|
| The Isle (Evrima) | Full-body additive breath with exertion, start / stop settle, lean and head lead, tails as counterweights, physical secondary motion | Hit direction barely read; generic flinches; no staggers |
| Total War: Warhammer III | Per-soldier offsets and variants so blocks never move in step; matched combat pairs; a cheap fixed-rate state sample | Infantry turns are rotations; idles are clips that loop visibly up close |
| Hades | Readability at a top-down camera: anticipation, hit-stop, exaggerated settle | Stylised, not realistic - we borrow the far-zoom gain, not the style |
| For Honor | Directional hit reactions per side; staggers that read; motion matching for transitions | Duel-scale only, a handful of characters |
| Mount & Blade II: Bannerlord | Directional attacks and reactions at army scale; formation sandbox for tuning | Stiff idles; little secondary motion on foot |
| League of Legends | Silhouette-first motion that reads from far; idle variants; cosmetic layers that fail to plain | Not realistic; a few units on screen |
| Warcraft III | RTS fidget banks ("Stand 2/3/4") at RTS zoom - the original living army | Units in step; clips identical per unit |

## 8. Round 1 as built (Oct 27 2026, overnight)

PRs: #277 (this spec + `design/living_army_plan.md`), #278 slice 1 (springs, zoom gain, de-sync), #279 slice 2 (shared
motion state), #280 slice 3 (parade ground), #281 slice 4 (motion pass), #282 slice 5 (fidgets, glances), #283 slice 6
(every family); an independent review of 4-6 found sign, rig and clock bugs, fixed tests-first on those branches.
Measured on the CI bench: the tick 0.10 us a unit, the motion pass 0.26 us a unit, 0 bytes allocated
by either (budgets 1 and 3 us).

Where the build differs from the text above, and why:
- **Hit direction comes from the damage event, not `hurtDir`.** Vanilla 1.20.1 sets `hurtDir` for players only (the
  camera tilt); on a unit it is stale. The client's damage event (`LivingEntity.handleDamageEvent`) carries the
  source's position, so `mixin/LivingEntityDamageMotionMixin` stamps the direction from that.
- **`/parade` defaults to one unit a type.** A 3x3 block of all 152 unit types is ~1,370 units, more than a PC holds
  at a usable frame rate; `/parade 3` gives the spec's blocks.
- **The A/B switch is Numpad 0 or the client command `/livingarmy on|off`** (F8 is fog of war / spectator; a client
  command needs no packet, so the feature stays client-only).
- **Idle neighbours start together in under 2% of starts**, not "never": independent per-unit seeds cannot promise
  never, and the test measures it.
- **A player body's jacket, sleeves and trouser legs, and a humanoid's hat, follow their limb after the pass**
  (vanilla copies them inside setupAnim, before it).
- **Keyframe units and our generated rigs are hierarchical**: their body bends about its own pivot and the head and
  arms follow as its children - only where the head is in the body's subtree and no leg is. Where it is not (the
  Royal Guard's head and arms are the body's siblings; a generated rig's `body` is its pelvis, carrying the legs) the
  torso is left to the rig's clips and the unit flinches with its head and staggers with its whole body. Rigs that
  breathe by themselves get no second breath.
- **A hanging limb's RX/RZ swing its free end** (the hand or foot forward / to the right), so the weight shift tilts
  the legs against the hips (the feet stay planted) and the arms splay out of a turn.
- **The idle schedule re-seeds when a unit's clock goes back**: a unit that leaves tracking and returns within the
  100 ticks its slot is kept is a new entity object with `tickCount` from 0.
- **The Bone Dragon is left out** while PR #145 reworks it; the bison breathes on the shared exertion.
