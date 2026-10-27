# Living army round 1 - implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans (chosen for the overnight run, Oct 27:
> native implementation, a fresh reviewer on the strongest model per PR before any merge). Steps use `- [ ]`.

**Goal:** every vanilla-bodied unit breathes, shifts its weight, fidgets, leans into turns, rocks on starts and stops,
flinches and staggers by hit direction, and never moves in step - client only, behind a master switch.

**Architecture:** one pooled per-unit motion slab updated on the client tick (pure `Kinematics` / `Idle` math, a thin
world-side `AnimStates`), a pure `Motion.pose` that turns it into per-bone offsets, and a `MotionPass` adapter in
`LivingEntityRendererMixin` that adds them to vanilla `ModelPart`s (plus a root transform about the feet).

**Tech stack:** Java 17, Forge 47.4.0 / MC 1.20.1 official mappings, Mixin, JUnit 5 (`bar/src/test`), GameTests.

**Spec:** `design/living_army.md` (approved by lovish Oct 27). Read it first; this plan does not repeat its reasons.

## Global constraints

- Client only: no server rule, packet, gameplay number or attribute change. The parade command is an op-only dev tool.
- Tests first: every task's test commit is pushed and seen RED (locally or CI) before its code.
- Zero allocation per tick and per frame in `Kinematics`, `Idle`, `Motion`, `MotionPass`.
- Package for new code: `com.solegendary.reignofnether.unit.modelling.motion` (`@OnlyIn(Dist.CLIENT)` where it
  touches client classes); tests in `bar/src/test/java/com/solegendary/reignofnether/unit/modelling/motion/`.
- Do not edit `Dragon.java` (PR #145 open) or PR #176's files (`EyesDirector`, `EyesAnimScene`, `tools/anim_*`).
- Commit identity `git -c user.name=IamTheLovish -c user.email=IamTheLovish@users.noreply.github.com commit`, explicit
  paths only. One PR per slice from `claude/living-army-<n>-<slug>`, based on fresh `origin/main`.
- Merge rule (overnight, lovish Oct 27: "ready Prs can be merged just update me on them later"): every slice merges
  when CI is green, the fresh reviewer passes it, and - for slices 4-6, which players see - the 300-unit budget holds
  and the parade scene has been checked in game on lovish's PC (stills + clip in the PR for his morning look).
- Spec values: ZoomGain x1 at zoom <= 20 -> x(1+k) at zoom >= 70, smoothstep; k breath 1.5, weight 1.0, fidget 1.3,
  lean 1.5, accel 1.5, flinch 2.0 (stagger too), secondary 1.2; sliders 0-200 (%); de-sync tempo +-8%, amplitude
  +-15%; lean max 12 deg; stagger at a health drop >= 12% of max health; fidget gaps 80-240 ticks, quiet 30 ticks after
  a stop; weight shift every 120-280 ticks; breath period 60 ticks at rest -> 25 blown; exertion decays over ~200 ticks;
  slot freed after 100 ticks unseen; budget MotionPass < 3 us/unit/pass, tick < 1 us/unit, 300 units <= 2% frame time.

## Review focus

1. A unit teleported (`/tp`, a summon, chunk reload) reads as a huge speed for one tick -> no lean / pitch spike:
   `Kinematics` treats a jump > 4 blocks in one tick as a reset (test in Task 2).
2. A dead or dying unit (`deathTime > 0`) gets no flinch, lean or fidget layered over vanilla's death flip (Task 4).
3. Possessed hero in first person: its own body gets no flinch / lean (Task 4, `MotionPass.skip`).
4. Healing or a max-health change between ticks never reads as a hit (negative drop -> no stamp; Task 2).
5. Two renders of one unit in one frame (shadow pass + view) pose identically (Task 4).

---

## Slice 1 - core math (PR 1, invisible)

### Task 1: springs, zoom gain, de-sync

**Files:**
- Modify: `bar/src/main/java/com/solegendary/reignofnether/unit/modelling/animations/Life.java` (append)
- Create: `.../unit/modelling/motion/ZoomGain.java`, `.../unit/modelling/motion/Desync.java`
- Test: `bar/src/test/.../unit/modelling/animations/LifeSpringTest.java`, `.../motion/ZoomGainTest.java`,
  `.../motion/DesyncTest.java`

**Interfaces (produces):**
- `Life.SPRING = 3` (floats per spring slot: value, velocity, previous value).
- `static void Life.spring(float[] s, int i, float target, float omega, float zeta, float dt)` - implicit-Euler step
  (unconditionally stable): `prev = x; v = (v + dt*omega*omega*(target - x)) / (1 + 2*zeta*omega*dt + omega*omega*dt*dt); x += dt*v`.
- `static float Life.springAt(float[] s, int i, float partial)` = `prev + (x - prev) * partial`.
- `static void Life.springChain(float[] s, int base, int links, float rootTarget, float omega, float zeta, float dt)` -
  link k (slot `base + k*SPRING`) springs toward link k-1's new value, link 0 toward `rootTarget`.
- `ZoomGain.gain(float zoom, float k)`; constants `K_BREATH..K_SECONDARY` (spec values); `ZoomGain.NEAR = 20f`,
  `FAR = 70f`.
- `Desync.hash01(int id, int salt)` -> [0,1) (integer mix, e.g. murmur3 fmix32); `Desync.tempo(int id)` in
  [0.92, 1.08]; `Desync.amp(int id)` in [0.85, 1.15].

- [ ] **Step 1: tests.** `LifeSpringTest`: critical (`zeta=1, omega=0.5, dt=1`) from 0 to 1 never exceeds 1.0001 and
  is within 0.01 of 1 by tick 20; `zeta=0.45` overshoots (> 1.02) then within 0.02 by tick 40; any `dt` in
  {0.1, 1, 5, 50} with `omega=2` stays in [-0.01, 1.01] for 200 steps; `springAt(…, 0.5)` is the midpoint;
  `springChain` with 4 links, root target stepped to 1: at tick 5 value(link0) > value(link3), all within 0.02 of 1
  by tick 80. `ZoomGainTest`: gain(5,k)=gain(20,k)=1; gain(70,k)=gain(200,k)=1+k; monotone over 20..70 in 0.5 steps.
  `DesyncTest`: ids 0..99 give 100 distinct tempos, min < 0.93, max > 1.07, all within [0.92, 1.08]; same for amp
  over [0.85, 1.15].
- [ ] **Step 2:** commit tests, push, see them fail to compile in CI (RED evidence in the PR body).
- [ ] **Step 3:** implement the signatures above.
- [ ] **Step 4:** `bash tools/prepush.sh` (check_java 0 errors) and CI green.
- [ ] **Step 5:** commit, PR "Living army 1/6: springs, zoom gain, de-sync (invisible)", reviewer, merge on green.

## Slice 2 - shared motion state (PR 2, invisible)

### Task 2: `AnimState` layout + pure `Kinematics`

**Files:** Create `.../motion/AnimState.java`, `.../motion/Kinematics.java`; Test `.../motion/KinematicsTest.java`.

**Interfaces (produces):**
- `AnimState` int constants (floats in a slot): `SPEED, ACCEL, YAW_RATE, VSPEED, SINCE_STOP, SINCE_START, EXERTION,
  HIT_AGE, HIT_DIR, HIT_SIZE, LAST_HEALTH, LAST_X, LAST_Y, LAST_Z, LAST_YAW, SEEN_TICK, IDLE_*` (Task 3) and spring
  slots `SP_LEAN, SP_PITCH, SP_FLINCH_X, SP_FLINCH_Z, SP_STAGGER, SP_HEAD_YAW, SP_HEAD_PITCH, SP_ARMS, SP_WEIGHT`
  (each `Life.SPRING` wide); `STRIDE` = total (pad to 64). `HIT_SIZE` values `NONE=0, FLINCH=1, STAGGER=2`.
  `HIT_DIR` = radians of the hit relative to body facing (0 = from the front, +PI/2 = from the unit's right).
- `static void Kinematics.step(float[] s, int b, double x, double y, double z, float bodyYawDeg, int hurtTime,
  float hurtDirDeg, float health, float maxHealth, boolean attacking, boolean fresh)` - one tick; `fresh` = first tick
  of the slot (seed LAST_*, no motion). Also steps the springs with targets: lean = clamp(speed*yawRate*4 deg, +-12),
  pitch = clamp(accel*200 deg, +-8) with `zeta 0.45`, flinch/stagger impulses on a fresh hit, `omega 0.6`.
- `STAGGER_DROP = 0.12f`, `TELEPORT_BLOCKS = 4f`, `STOP_SPEED = 0.01f` (blocks/tick).
- Check before writing the test: in 1.20.1 decompiled `LivingEntity` / `ClientboundHurtAnimationPacket`, confirm what
  `hurtDir` holds on the client (expected: attack yaw relative to the entity's yRot, degrees) and write the
  conversion to `HIT_DIR` from that; note the finding in the class doc.

- [ ] **Step 1: tests** (`KinematicsTest`): constant 0.2 b/t straight line -> SPEED ~0.2, YAW_RATE 0, lean spring ~0;
  turning +6 deg/tick at 0.2 b/t -> lean > 0 and <= 12 deg; from 0.2 b/t to standing in one tick -> pitch spring goes
  negative then overshoots past 0 (one rock) and is within 0.2 deg of 0 by 40 ticks; a 10-block jump in one tick ->
  SPEED, ACCEL unchanged from before (teleport reset); a hit with health 20 -> 19 of max 20 from 90 deg -> HIT_SIZE
  FLINCH and HIT_DIR ~ +PI/2; 20 -> 17 -> STAGGER; health rising (heal) -> no stamp; SINCE_STOP counts ticks after
  speed < STOP_SPEED; EXERTION rises while SPEED > 0.15 and halves within ~70 ticks of stopping; NaN health -> slot
  values all finite.
- [ ] **Step 2:** commit, RED.
- [ ] **Step 3:** implement.
- [ ] **Step 4:** green.

### Task 3: `Idle` scheduler (pure)

**Files:** Create `.../motion/Idle.java`; Test `.../motion/IdleTest.java`.

**Interfaces:** `Idle` kinds `NONE, GRIP, GLANCE, SHOULDER, LOOK_AROUND`; slot fields `AnimState.IDLE_KIND,
IDLE_START, IDLE_LEN, IDLE_NEXT, IDLE_COUNT, WEIGHT_NEXT, WEIGHT_SIDE, GLANCE_YAW, GLANCE_PITCH`.
`static int Idle.step(float[] s, int b, int id, int tick, boolean moving, boolean fighting)` returns the kind that
STARTED this tick (or `NONE`) so the world side can pick a glance target. `static float Idle.weight(float[] s, int b,
float tickF)` (-1..1 hip side, eased), `static float Idle.fidget(float[] s, int b, float tickF)` (0..1..0 envelope).
Gaps from `Desync.hash01(id, IDLE_COUNT)`: fidget 80-240 ticks, weight 120-280; fidget lengths 20-50 ticks.

- [ ] **Step 1: tests:** same id + same inputs twice -> identical start ticks; 20 consecutive fidgets never repeat a
  kind back to back; no start within 30 ticks after `moving` turns false; none while `moving` or `fighting`; for ids
  0..49 over 4800 ticks, neighbouring ids start on the same tick in < 2% of starts; gaps all within 80..240.
- [ ] **Step 2-4:** RED, implement, green.

### Task 4: `AnimStates` world side + bench row

**Files:** Create `.../motion/AnimStates.java` (client tick subscriber, slab, `Int2IntOpenHashMap` id -> base,
free list, doubling growth); register in `registrars/ClientEventRegistrar.java`; Modify
`bar/src/test/.../perf/AnimationCostBench.java` (rows `motionTick`), `config/ReignOfNetherClientConfigs.java`
(`LIVING_ARMY` bool true, `MOTION_BREATH..MOTION_SECONDARY` ints 0-200 default 100).

**Interfaces:** `static int AnimStates.base(int entityId)` (-1 if none), `static float[] AnimStates.slab()`,
`static boolean AnimStates.enabled()`. On a `GLANCE` start: nearest entity in 16 blocks by priority enemy unit
(`UnitClientEvents.getPlayerToEntityRelationship == HOSTILE`), an ally hurt in the last 40 ticks, own commander / hero,
else a random yaw +-60 deg from `Desync.hash01`; writes GLANCE_YAW / PITCH relative to body, clamped +-70 / +-30.
Slots unseen for 100 ticks are freed.

- [ ] **Step 1:** bench rows first (`Kinematics.step` + `Idle.step` over 500 slots, and allocated bytes via
  `com.sun.management.ThreadMXBean.getThreadAllocatedBytes` == 0 across 30 frames); RED (classes missing).
- [ ] **Step 2:** implement; bench < 1 us/unit, 0 bytes. Green CI, PR "Living army 2/6", reviewer, merge.

## Slice 3 - the parade ground (PR 3, dev tool)

### Task 5: `/parade`, the loop, the toggle

**Files:** Create `.../motion/ParadeGround.java` (server: pad, spawn, loop) and `.../motion/ParadeCommand.java`
(register on `RegisterCommandsEvent`, `requires(src -> src.hasPermission(2))`: `/parade`, `/parade stop`,
`/parade motion on|off`); Modify `keybinds/Keybindings.java` (new `key.reignofnether.motion_toggle`,
`GLFW_KEY_KP_0`) + `lang/en_us.json`; GameTest `gametest/ParadeGroundGameTests.java`.

**Behaviour:** pad 64x48 of smooth stone at the player's feet; for each unit `EntityType` registered by the mod's
unit registrar (skip workers/buildings-only types that cannot move), a 3x3 block via `UnitServerEvents.spawnMobs`,
owner = the caller; two foe lines owned by `"parade_foe"`. Loop of 1200 ticks: 0-200 stand; 200-500 `setMoveTarget`
20 blocks ahead; 500-600 wheel 90 deg (move targets on an arc); 600-900 march back; 900 hard halt
(`setMoveTarget(null)`); 900-1200 every 40 ticks a foe deals `level.damageSources().mobAttack(foe)` of 1.0 (light) or
`0.15 * maxHealth` (heavy, alternate) to a random unit from front / left / right / rear. Units are healed to full at
the loop's start (no deaths). `/parade motion` and the key flip a client flag read by `AnimStates.enabled()`.

- [ ] **Step 1:** GameTest first: `parade_spawns_every_unit_type_and_runs_a_loop` (spawns, runs 1200 ticks headless,
  asserts every type present and alive, no exception). RED.
- [ ] **Step 2:** implement; green CI; PR "Living army 3/6"; reviewer; merge (dev tool, nothing in a match changes).
- [ ] **Step 3:** run the client on lovish's PC (`gradlew runClient`), `/parade`, record `[Anim]` lines at zoom 15 and
  60 as the before-numbers for slice 4.

## Slice 4 - `MotionPass` on humanoids and illagers (PR 4, visible - waits for lovish)

### Task 6: `Motion.pose` (pure) + bone maps + mixin

**Files:** Create `.../motion/Motion.java`, `.../motion/BoneMap.java`, `.../motion/BoneMaps.java`,
`.../motion/MotionPass.java`, `.../motion/OwnMotion.java` (marker interface for custom-rig models); Modify
`mixin/LivingEntityRendererMixin.java` (inject after `setupAnim`, before `renderToBuffer`); mark custom rigs
`implements OwnMotion` (gen HumanBase models, `HalberdierUnitModel`, the Quadruped bison model - not `Dragon.java`);
Test `.../motion/MotionTest.java`; bench row `motionPass`.

**Interfaces:**
- Bones `ROOT=0, BODY=1, HEAD=2, ARM_R=3, ARM_L=4, LEG_R=5, LEG_L=6, ARMS=7`; `Motion.OUT = 8*6` floats
  (rx, ry, rz radians, tx, ty, tz px).
- `static void Motion.pose(float[] s, int b, int id, float tickF, float partial, float zoom, float[] sliders,
  boolean flinchOnly, float[] out)`; sliders indexed `BREATH, WEIGHT, FIDGET, LEAN, ACCEL, FLINCH, SECONDARY`.
- `BoneMap` (nullable `ModelPart` per bone, `waistPx` = 12 for humanoid, `flinchOnly`); `BoneMaps.forModel(EntityModel<?>)`
  cached per class: `HumanoidModel` by fields; `IllagerModel`, `VillagerModel` / `WitchModel`, `IronGolemModel` by
  root child names (`head`, `body`, `arms`, `right_arm`, ...; a missing name -> null bone); `OwnMotion` -> null;
  `KeyframeHierarchicalModel` subclasses -> flinchOnly map; anything else -> null. Slice 4 enables humanoid + illager
  only (`BoneMaps.FAMILIES_ENABLED`).
- `MotionPass.apply(LivingEntity e, EntityModel<?> m, PoseStack ps, float partial)`: skip if disabled, no map,
  `deathTime > 0`, or the possessed hero in first person; ROOT as `ps` rotation about model-space (0, 1.5, 0);
  BODY bend about the waist with head / arm pivots moved by the same rigid rotation; other bones added to the parts.
  Any exception -> `BoneMaps.disable(class)` + one `[Motion] disabled for <class>: <reason>` log line.

- [ ] **Step 1: tests:** all sliders 0 -> `out` all zeros; standing slot + same inputs posed twice -> identical `out`;
  hit from the left (`HIT_DIR = -PI/2`) -> BODY rz bends away (sign test, all 8 sectors table-driven); turn right ->
  ROOT roll into the turn; extreme inputs (speed 50, yaw rate 180) -> every rotation within the clamp table; zoom 70
  flinch amplitude = 3x zoom 20 (k 2.0); breath period at EXERTION 1 ~25 ticks vs ~60 at 0 (zero-crossings).
- [ ] **Step 2-4:** RED, implement, green; bench `motionPass` < 3 us/unit, 0 bytes.
- [ ] **Step 5:** in game on lovish's PC: `/parade`, Numpad 0 A/B at zoom 15 and 60; `[Anim]` 300 units motion on vs
  off, with and without Complementary (Oculus): <= 2% frame time. Stills + a short clip of each into the PR.
- [ ] **Step 6:** PR "Living army 4/6 (visible)"; reviewer; merge per the merge rule.

## Slice 5 - idle fidgets and glances (PR 5, visible - waits for lovish)

### Task 7: fidget poses
**Files:** Modify `Motion.java` (fidget layer: GRIP = arms ry/rx twitch, GLANCE = head yaw/pitch spring to
GLANCE_YAW/PITCH, SHOULDER = arm roll + body rz, LOOK_AROUND = slow head sweep +-50 deg; weight shift = ROOT tx +-0.6
px and body rz +-2 deg from `Idle.weight`); Test additions to `MotionTest`.
- [ ] Tests: no fidget offsets while SPEED > STOP_SPEED; a GLANCE to +40 deg ends with HEAD ry within 2 deg of +40
  (scaled by slider); weight shift alternates sides. RED, implement, green, parade check, PR, reviewer, merge per the merge rule.

## Slice 6 - every other family (PR 6, visible - waits for lovish)

### Task 8: remaining maps and custom rigs
**Files:** `BoneMaps.FAMILIES_ENABLED` += villager, witch, golem, skeleton (HumanoidModel), spider (`head`, `body0`,
`body1`); keyframe units flinchOnly; `Quadruped.java` reads EXERTION and HIT_DIR / HIT_SIZE from `AnimStates`
instead of its own `STATE` exertion (keep its other fields); HumanBase bodies get flinch + stagger through a call to
`Motion.pose(..., flinchOnly=true, ...)` in their `setupAnim` tail.
- [ ] Tests: `BoneMapsTest` (each family's map resolves the expected non-null bones on a model baked from its
  vanilla `LayerDefinition`); `QuadrupedExertionTest` (exertion follows the shared slab). RED, implement, green,
  parade check per family, PR, reviewer, merge per the merge rule.

## Done for the night

As many slices merged as pass the merge rule, in order; each PR body lists what lovish should look at in the parade
scene, and the morning report links every PR.
