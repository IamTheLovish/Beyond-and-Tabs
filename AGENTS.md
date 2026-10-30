# Beyond and Tabs - briefing for developers and their AI assistants

Read this first. It is the shared memory of the project for anyone working in this repo: what the game is, how the
code and tools fit together, how we work, and what comes next. It is kept current by the project lead's assistant
(see "Working together"); if something here is out of date, say so in your PR.

The project lead and owner is **lovish** (handle only - never use anyone's real name in code, commits, credits, docs
or anything public).

**Several Claude sessions work here in parallel - read `design/LANES.md` (lanes + the owner's current decisions) right after this file.**

---

## 1. What we are building

**Beyond and Tabs** is a real-time strategy game inside Minecraft: Beyond All Reason's depth (flow economy, metal
patches, commanders, tech tiers, big armies, BAR's controls and camera) in a fantasy world, played from a top-down
RTS camera, with the option to drop into any unit and fight in first person.

- **Engine:** Minecraft Java 1.20.1, **Forge 47.4.0**. A credited **GPL-3.0 fork of Reign of Nether 1.5.0-beta-4**.
  The mod id is still `reignofnether` and the Java package is `com.solegendary.reignofnether` (renaming is not worth
  the churn). Credits to Reign of Nether and Beyond All Reason live in `NOTICE.md` and `mods.toml`.
- **Factions (7 + 1 planned):** Sunforged (gilded knights, sun magic), Gravebound (undead, soul-fire), Horde
  (brutes, war paint), Verdant (living forest - "the base is a forest"), Tidewrought (amphibious, coral and
  tridents), Stormcallers (monks, lightning, wind), Brassworks (steam machines). Eighth, designed later: Hivebound
  (alien insect swarm). Each has T1/T2/T3 labs, a hero, combat engineers and a game ender.
- **Pillars of the design** (the questions every change answers to):
  1. **Feels like BAR.** A BAR player should adapt instantly: flow economy, area commands, patrol/guard/fight,
     Shift-queue, strategic zoom icons, BAR hotkeys (fully rebindable).
  2. **Readable at every zoom.** Clear silhouettes and icons, little clutter; nothing gets worse as armies grow.
  3. **Comes alive.** Units are not reskinned villagers: unique bodies, upgrades that change the look, abilities,
     reactions, effects. "Keep base, improve everything."
  4. **Fair and varied.** Competitive balance without killing fun; every match feels different (maps, wall sizes,
     metal richness).
  5. **Publish-ready at all times.** Our own art and audio only, licences respected, works from a fresh world.
- **Current design standard:** 4v4 matches with "a lot of units, still micro-able, never lose track of them". 8v8
  only later. Quality first, optimise later; never reject an art idea on performance grounds alone.

## 2. Hard rules (never crossed)

- **Originals only.** No art, models, textures, sounds or animations taken from other games, shows or mods - not even
  "recoloured" or "resized" (that is still a derivative). Ideas and mechanics are free to borrow; assets are not.
  Reference clips and screenshots are for inspiration only.
- **Licences.** The repo is GPL-3.0 and public. Never paste code under an incompatible licence. Third-party mods stay
  **dependencies**, never bundled in our jar (a release is a modpack listing them + our jar).
- **Decompiled study:** open-source games only (BAR/Spring, Zero-K, OpenRA, 0 A.D., Reign of Nether) and Minecraft
  via the official mappings. Never decompile closed games.
- **No real names, no secrets.** Handles only. Never commit API keys, tokens, personal paths or personal details.
  The repo is public: check `git status` before every commit, never `git add -A` blindly.
- **Never touch anyone's computer from automation** without that person's explicit OK. Installs on testers' machines
  are done by them (or by their own assistant with their permission).
- **Don't break `main`.** Every push to `main` publishes the test jar (release `bar-dev`) that people install.
- **Rules are advice; these are enforced** (Oct 20, from lovish's guardrails handbook): `.claude/settings.json` denies
  force pushes, pushes straight to `main` and `git add -A` / `git add .` for every Claude Code session in this repo, and
  asks before `git stash`, `git reset --hard` and `git clean`. GitHub branch protection on `main` (lovish sets it in the
  repo settings) requires a pull request with green CI. A rule broken twice gets moved from prose into a check like these.
- **One rule, one place:** lovish's standing decisions live in `design/LANES.md` (his words, with the date); hard rules
  here. A decision he reverses moves to a **Retired** list there with the date and his reason - never deleted.

## 3. Repository map

```
bar/                      the live game (Forge 1.20.1 mod). Everything that ships is here.
  src/main/java/com/solegendary/reignofnether/
    unit/                 units, goals, formations, pathfinding/ (custom grid A*), Terrain, Engage, StuckNudge
    building/             buildings, levels (L1-L3 in place), placement, faction auras
    ability/              abilities, AutoRule / AutocastState / AutoCaster (units autocast; heroes never)
    resources/            BAR flow economy (metal/energy, storage, no population cap)
    hud/ cursor/ keybinds/ orthoview/   RTS HUD, command card (CommandCard), BAR camera, hotkeys
    hero/                 hero mode + possession (first-person "unit feed")
    bot/                  skirmish AI
    barfx/                our effects layer (projectile tints, domes, rings...)
    match/ matchstart/    lobby, match lifecycle, multiplayer; EyesDirector (CI screenshot/video tour)
    gametest/             headless GameTests (the real test suite)
    mixin/                Forge/vanilla hooks
  src/main/resources/     assets (our own textures, models, sounds, lang/en_us.json), structures (.nbt)
design/                   design docs (*.md), JSON design sheets, design/models/*.json (unit model DSL for Blender)
tools/                    python tools: check_java.py, balance_audit.py, gen_*.py (generators), triage_log.py,
                          render_concept.py, lineup.py, eyes_record.sh/eyes_cut.py ...
                          concept -> game: dsl_to_game.py (DSL model -> generated ModelPart Java in
                          unit/modelling/models/gen/ + painted atlas, self-checked), game_preview.py (the game's
                          look offline), pose_preview.py (animation keyframes on paper), export_bodies.py (the five
                          bodies' exports; --check), gen_body_icons.py (their HUD portraits - rerun after a re-export),
                          unit_light.py (baked sky light + occlusion per texel, from the voxelised rest pose); the
                          realism pass is materials.REALISM + dsl_to_game STEEL_PALETTE / BAKED_LIGHT (all on since
                          Oct 16 - set one False only for a comparison render, never for an export); shape helpers in
                          gen_models.py for clean forms: ngon_prism (exact round discs - not rounded()'s stepped
                          boxes), octagon_pole, octagon_axle (hinge joints), slab, grip_hand (fingers round a grip),
                          solve_arm(elbow_at=) (anatomical elbows); levy_reload_preview.py (the Levy's crossbow
                          mechanism as game_preview poses - mirrors CrossbowLevyUnitModel; game_preview poses take
                          "_move" / "_scale" / "_hide")
                          realistic block pack: gen_blocks.py (procedural 128x + LabPBR -> resourcepacks/
                          BeyondAndTabs-Realistic, generated, not in git), materials.py (the one LabPBR encoder +
                          check_labpbr), face_census.py (what the RTS camera sees on our buildings, from the game's
                          own models), structure_preview.py (buildings drawn with the pack, offline; structure_render.py
                          draws them on their real shapes from the RTS side, sun-lit with shadows and AO like the shader refs, --flat unlit), pack_study.py;
                          check_parts.py (the drawn extractor rigs' anchors: building/BuildingParts.java against
                          gen_structures.py's SUN_/GRAVE_RIG_ANCHORS, and their room in the .nbt - change a rig
                          structure, change its row; the parts follow the client setting "Building animation"
                          Off / Low / Full, design/moving_buildings.md)
resourcepacks/            BeyondAndTabs-Realistic: pack.mcmeta + README only in git; CI and eyes generate the rest
.github/workflows/        CI and the "senses" (see section 5)
engine/ mod/              an older from-scratch prototype - reference only, not shipped
NOTES.md                  older session log; README.md is out of date (this file supersedes it)
```

## 4. Building and testing

- There is **no local Minecraft classpath** in most AI sandboxes: **CI is the compiler and test runner.**
- Before every push: `bash tools/prepush.sh` (~35 s; CI's fast checks - check_java's structural/syntax gate without
  the MC classpath, guards, parts, icons, anims, dep pins, the engine compile; it lists every failure). At minimum
  `python3 tools/check_java.py` (must report 0 structural errors). If you touch balance numbers: `python3 tools/balance_audit.py`.
- CI = `.github/workflows/build.yml`:
  - job `build`: tools and audits (no bar/ compile); always runs;
  - job `changes` (`tools/ci_plan.py`): decides what the run must test. A change that touches **only documentation**
    (`*.md` anywhere, `docs/**`, the design sheets `design/*.png`) skips the GameTests - jobs `bar`, `bar-tests`,
    `bar-audit` and `bar-publish` are skipped, which `tools/ci_wait.py` counts as passing (nothing new to publish).
    Anything else - java, json, py, gradle, workflows, resources, `design/*.json`, nbt - runs everything. Unsure
    (API error, 300+ files, no base) also runs everything. PRs compare against their merge base; a push to main
    compares against the last *successful* main build, so a docs-only merge right after a replaced code merge still
    tests that code;
  - job `bar`: compiles `bar/`, keeps the jar as the run's artifact `bar-mod` (what the eyes tours test) and runs
    **shard 1** of the headless **GameTests**: everything no `SHARD_n` list names (so a new test or batch lands
    here). Job `bar-tests` runs shards 2..N side by side, one matrix entry per `SHARD_n` line.
  - **The shard split (Oct 21, lovish: "I just want all twenty runners being used"):** 8 shards, so a build is 9 jobs
    (`build` + 8) and two builds fit in the ~20 hosted runners of the public repo at once. Before: 3 shards of very
    unequal length, 15-19 min a build; now ~9.4 min (measured, PR #101). The lists are the `SHARD_2` ... `SHARD_8` lines
    at the top of build.yml: whole batches (`duel_matrix`), or `test:<prefix>` of the test's lower-cased **method name**
    (no class prefix in CI). Shards 2/3: the eight far-chunk `production_buildings_place_every_unit` tests, three per
    shard (they slow each other: 2 sharing a server take ~85 s, 4 take ~300 s) and the last two in shard 8; 4 and 5:
    one duel simulator each (3 min, the floor); 6: the bots soak alone; 7: the ~40 slow (90-100 s) tests, which
    cluster in vanilla chunks 2 and 5; 8: 190 quick tests; shard 1 (`bar`, which also builds the jar) keeps the rest,
    the small batches and everything new. **Adding a shard is one new `SHARD_9: "..."` line** (the matrix and shard 1's
    skip list are built from the `SHARD_*` variables). Why 8: each shard pays ~4.5 min before its first test
    (checkout with history, gradle, server start) and the longest single tests (duel simulators, bots soak) run 3-5 min,
    so a build cannot get under ~8-9 min whatever the count; 8 shards keep every shard's tests under that floor, while
    a 9th would only shave seconds and take a runner other builds wait for. `python3 tools/check_shards.py` (also in
    prepush) fails when two lists name the same test or an entry names none; CI's `bar-audit` proves from the shards'
    `gametests-shard-N` artifacts that every test ran exactly once. **Re-balance** from each shard's `[GameTest
    timing]` and `[Test times]` notices (every test's wall time). A vanilla chunk name (`defaultBatch:3`) in a list
    moves as tests are added - prefer prefixes. **A test's arena sits beside its batch neighbours'**, so a new shard
    mix can put another test's units within reach of one that assumes empty ground
    (`striker_autocasts_on_an_enemy_in_range_and_not_out_of_range` failed deterministically that way and now runs
    alone in shard 6): a test that fails right after a re-cut, passed before and fails again on a rerun, is a
    neighbour problem - move it to another shard, do not retry.
  - On `main`, job `bar-publish` publishes the jar to the GitHub release **`bar-dev`** once every shard and the audit
    passed (it also waits for `build` so the two releases are never updated at the same moment; `bar` itself no
    longer does, which took 1.5 min off every build).
  - **Main builds collapse while queued:** pushes to main share one concurrency group without cancel-in-progress, so at
    most one main build runs plus **one** pending; a newer merge replaces the pending one, the running one is never
    cut off. The cost: per-commit attribution on main is lost in a fast batch of merges (a commit whose build was
    replaced has a CANCELLED run, `ci_wait --sha` says so) - bisect with `flake-stress.yml` if a regression has to be
    pinned to one commit. PR builds still cancel their own older run on a new push; manual runs never cancel each other.
    So **merge several PRs in quick succession** (design/LANES.md).
  - **A tests-only head (tests first, before its red run): put `[compile-only]` in that commit's subject line.** Its PR build
    then compiles the jar and runs JUnit only (~7 runner-minutes instead of ~75, no GameTests) and job `compile-only`
    fails on purpose, so the head can never merge; read job `bar` for the compile, then dispatch the red flake-stress
    run. The tag counts only on the head commit: the next push (the fix, a main merge) runs the full suite. Never tag a
    head that is meant to merge (tools/ci_plan.py).
  - Read failures from check-run annotations (`gh api repos/<owner>/<repo>/check-runs/<job-id>/annotations`) of
    the job that ran the test (`bar` or one of the `bar-tests` entries) - test failures appear as
    `... failed! <message>`; measurements from passing tests appear as `[Melee]`, `[Stress]`, `[Soak]` notices.
  - **Wait for CI with `python3 tools/ci_wait.py --pr N`** (or `--sha S`), started once in the background where your
    tool allows it - never a hand-written polling loop (each check-in is a paid turn). It prints one line at the end
    (GREEN / RED with the failed tests / TIMEOUT) and reruns a run once when every failed test is in
    `tools/known_flakes.txt`. A failure that names no test is never rerun.
- **Fast JUnit tests** (`bar/src/test/java`, JUnit 5, plan 008): pure rules in seconds, not the minutes of a GameTest.
  They run inside job `bar`'s `./gradlew build` (no extra job or runner); the `JUnit summary` step posts the count as a
  notice (`tools/junit_summary.py`). A rule that touches the world cannot load here - pull the arithmetic into a plain
  class (`PopulationRules`, `EconomyMath`, `PlacementGrid`, `MergeGrid`...) and test that; do not mock the world. Table-
  driven, written from the rule before the code; a real bug found by a test stays `@Disabled("BUG: ...")` in the PR for
  the commander to decide - never bend the test.
- **Writing GameTests** (`bar/.../gametest/`): every public rule gets one. Keep everything inside the test's own
  arena; big tests get their own batch; load far chunks with `GameTestChunks.hold` + progress-based waits (never
  `setChunkForced`/`getChunk` on ungenerated chunks). Units **autocast** by default: a test that counts energy,
  mana or cooldowns must switch bystanders off with `AutocastState.setAll(unit, false)`. Known flakes are listed in
  `tools/known_flakes.txt`; don't paper over a flake - fix it (or make its failure message diagnostic) and take it
  off the list.
  Two traps that made flakes (Oct 15): an entity's state flags (`isInWater`, on-ground, fluid height) are refreshed by
  its OWN next tick, not by `teleportTo` - wait a tick or two after moving a mob before asserting on them; and judge
  where units END UP with a bounded poll (check, then every few ticks up to a limit) rather than at one fixed tick, so
  a loaded CI server walking them slower does not fail a spacing rule.
- Combat internals: unit damage bypasses vanilla (`mixin/LivingEntityMixin.actuallyHurt` + our ATTACK_DAMAGE
  attribute); BOTH `LivingHurtEvent` and `LivingDamageEvent` fire on that path (`ForgeHooks.onLivingHurt` /
  `onLivingDamage`, LivingEntityMixin ~265 / ~280) - Ward, Lantern Dome and the Brass shield rely on the hurt event. New `UnitAction` values go at
  the **end** of the enum.
- Structures: `tools/gen_lab_levels.py` generates the army labs' L2/L3 `.nbt` deterministically - check it
  reproduces the committed files byte for byte before changing it.
- **Building-block mods are required dependencies** (lovish, Oct 20 2026: "increase block vocabulary, scale the
  buildings bigger"): Macaw's Roofs, Macaw's Windows, Macaw's Fences and Walls and Chipped, plus Chipped's libraries
  Athena and Resourceful Lib. `bar/build.gradle` pins lovish's exact files (runtimeOnly, so runClient and the GameTest
  server load them; `tools/check_dep_pins.py` checks the pins against `pack/instance_mods.json` in CI), `mods.toml`
  makes them mandatory, the tester pack ships them. Structures reference their blocks by full id - in
  `tools/gen_structures.py` palettes write `"mcwroofs:thatch_steep_roof"` (a bare name means `minecraft:`). An id no
  mod registers loads as AIR: `BuildingBlockModGameTests` fails on any structure that names one, and the CI notice
  `[Mod blocks]` lists each mod's real ids. **Never** copy, extract, recolour or commit their textures, models or
  other assets (Macaw's Roofs/Windows and Chipped's art are All Rights Reserved; the repo is public) - not even on a
  branch, not even for an offline preview; `structure_preview.py` draws unknown blocks as its placeholder tile.
- The block pack (`tools/gen_blocks.py`): new textures go at the END of TARGETS and SEED_ORDER (seeds follow the
  order; an insertion re-rolls every texture after it - the build refuses it); every export passes
  `materials.check_labpbr` (LabPBR 1.3: F0 0-229, 230-237 named metals, 255 = albedo metal, 238-254 undefined); a
  texture of a block vanilla draws cut-out WITHOUT mipmaps (glass, plants, ladders, lanterns...) needs a model
  override with `"render_type": "minecraft:cutout_mipped"` (MODEL_OVERRIDES - else a 128x texture flickers from the
  RTS camera), and `face_census.py --check-overrides` keeps those copies equal to the game's models. Judge a texture
  at RTS scale (`rts_view`, ~36 px per block), not flat. Round 17 (Oct 20): HIRES textures (leaves, bark, grass top)
  build at 256x, calibrated at the same on-screen scale (`cal_mip`); the leaves ship random-turn blockstates
  (ROTATED_BLOCKSTATES) so a canopy has no block grid; keep features in a ROTATED texture isotropic (bands become
  cloth when half the blocks turn), and keep a near-grey material's hue in its base colour (the saturation fit blows
  a jitter-only hue up into blue and beige).

## 5. The senses (how we see and hear the game without a player)

All run on GitHub Actions, never on anyone's PC:
- **eyes.yml** - runs the real client under Xvfb after every green `main` build; `matchstart/EyesDirector.java`
  plays a scripted tour (build menu, army, box select, fights at two zooms, capitol, river crossing, domes...) and
  publishes stills **and a 10-second video clip per still** to the `eyes` branch. Add a scene when a feature has no
  shot that shows it. A feature nobody has seen is not done.
  Dispatch inputs: `shaders` (Oculus + Embeddium + a shader pack: `shader_pack` complementary - screenshots in CI only: its author refused us
  permission to ship it (Oct 29), so no pack we publish may contain it (tools/test_pack_no_complementary.py) - or bliss, which fogs out the RTS view; `rp` labpbr / integrated; `shader_options` KEY=VALUE;... for a pack
  setting A/B), `pack` ours / none, `look` (the world look), `build_run` (a BUILD run id whose jar to test). Each
  variant publishes to its own branch; `eyes-approve.yml` copies a lane's stills to `<lane>-approved`, the baseline
  of the picture regression (tools/eyes_diff.py).
  **Structure photos** (input `structure`: names in the jar or `.nbt` paths, comma-separated): instead of the tour,
  each structure is stamped on a flat pad and shot four ways (`bat_eyes_15_<name>_rts`, `_rts_close`, `_34` - a
  first-person 3/4 view - and `_dusk`) to branch `eyes-structures` (`eyes-structures-shaders` with `shaders`). Judge a
  new building design here, with real textures and glass, before asking lovish. For a design on a feature branch,
  dispatch the workflow with `ref=<that branch>`: it uses that branch's build (the header of eyes.yml has the call).
- **bot-ladder.yml** (nightly 07:03 UTC + `workflow_dispatch`; Oct 22, lovish approved) - bot vs bot full matches, one per hosted
  runner (a 40-match matrix = two waves of the ~20 runners), every Difficulty pair x faction pair
  (`tools/ladder_matrix.py` reads `enum Difficulty` in BotPlayer, so a new tier is one enum value). The match is
  `gametest/LadderGameTests` (batch `bot_ladder`, a no-op unless `BAT_LADDER_MATCH` is set, so the per-push shards are
  unchanged); pace arithmetic in `LadderStats`. It logs `[Sim]`, `[Pace]` and `[Stuck]` lines, writes a JSON per match, and
  `tools/ladder_report.py` makes the win-rate / pace table (job summary + artifact `bot-ladder-<date>.md`, built to append to
  a Project doc). Dispatch inputs: `quick` (3 matches), `mode` nightly/full, `matches` (explicit list), `minutes`. Measurement
  only - never change a unit or a bot in a ladder PR.
- **pack-study.yml** (numbers only from a reference pack or vanilla's jar) and **face-census.yml** (our pack's
  coverage of the faction buildings, face by face, on Mojang's jar) - block pack calibration and coverage.
- **concepts.yml** - Blender 4.2 on CI renders unit concept sheets from `design/models/*.json` (our own cube-model
  DSL; `design/models/README.md`) to the `concepts` branch. Render 3 models or fewer per run.
- **ears.yml / voice.yml / voice-reel.yml** - audio analysis and the original narrator voice (open TTS; never a
  clone of a real actor).
- **web-eyes.yml** - screenshots of reference web pages. **modpack.yml / macpack.yml** - the tester modpacks
  (release `test-pack`).
  A pack change is proved from its branch: `modpack.yml` with `dry_run` (builds the pack and its Mac copy, publishes nothing) and
  `eyes.yml` with `branch_pack` + `shaders` (the tour runs the pack the checkout builds). Extra mods are pinned by version in
  `pack/build_pack.py` (`PINNED_VERSIONS`); the Mac strip list is `build_pack.MAC_SKIP` (one list for all three pack workflows).

## 6. How we work together

- **Agent checklist** (every session and every agent it starts; a brief can say "follow the AGENTS.md agent
  checklist" instead of repeating it):
  1. Read this file, `design/LANES.md`, `design/ART-FAULTS.md` for art; the project's north star if you can reach it.
  2. `tools/claim.py check` then `take` (below). One branch per task from fresh `origin/main`.
  3. Commit by explicit path (never `add -A` / `add .`), identity
     `git -c user.name=IamTheLovish -c user.email=IamTheLovish@users.noreply.github.com commit`, the commit trailer
     lines your session gives you. New GameTests in their own class. No gameplay number changes without lovish -
     list them under "Numbers for lovish" in the PR.
  4. Push at the first working commit (containers get reclaimed - an Oct 21 agent lost everything it hadn't pushed).
     `bash tools/prepush.sh` before every push. Never push `main`, never force push. Post on #35 before touching
     `hud/` or another lane's files.
  5. `tools/ci_wait.py --pr N` for CI; fix real failures, never a blind retry.
  6. Review passes after green, scored honestly against the best reference in the category (north star); every
     visual change gets eyes stills.
  7. `gh` REST only (GraphQL is blocked in cloud sessions); the git proxy takes 2 concurrent network ops - never run
     git network commands in parallel; on HTTP 429 wait 10 s and retry once.
  8. Release the claim when the PR merges or you stop; the final report is short: PR, SHA, CI, what changed, open
     questions.
- **Claim before you start** (issue #91): `python3 tools/claim.py check <a few words>` - if an open PR or an active
  claim overlaps, read it and coordinate on #35 instead of starting a second version; otherwise
  `python3 tools/claim.py take <slug> --lane ... --branch ... --what ...`, and `release` when the PR merges or you
  stop. Agents a session starts claim too (or the session claims for them).
- **Branch + pull request** for every change: `yourname/topic` branches, small focused PRs, CI green before merge.
  Don't push straight to `main` (it publishes the test jar). Prefer one or two pushes per hour per branch - every
  push starts a 25-minute test run and too many parallel runs cause chunk-timing flakes.
- **Commit messages and code comments explain WHY** - which playtest clip, CI run or design rule forced the change -
  not just what. Example: "Sunforged keep gate: the L2 gate was 1.94 high for 1.95-tall units, so trained units
  stayed in the yard (playtest clip 16:52)".
- **Code quality bar:** "best of the best". Review the code you touch and leave it better; prefer one well-placed rule
  over edits in 100 unit classes; no silent failures.
- **Design from the player's chair:** what would make this more playable for the person at the screen? Check the
  relevant design doc in `design/` and, if there is one, the playtest clip before changing a feature.
- **Coordination:** lovish's AI assistant ("Itadori") also works on this repo, usually on `main` through CI. If you
  start a feature, open a draft PR early (title = the feature) so two people don't build the same thing; if a
  roadmap item below is yours, say so in the PR.
- **Art and feel decisions belong to lovish.** Bring options with a recommendation and pictures (concept sheets,
  eyes stills), not finished surprises.

## 7. Where things stand (Oct 14 2026)

Working in game today: BAR flow economy and metal patches; commanders + D-gun; T1-T3 labs per faction with in-place
building upgrades; seven factions with heroes, combat engineers and game enders; bots; multiplayer lobby (online
hosting via a tunnel mod); BAR camera, strategic icons, Smart Select box rules, command card with BAR keys (A
attack, **F fight**, M move, S stop, H hold, P patrol, G guard), formations for move/fight/patrol, a typed
command bar; autocast with per-ability toggles; no population cap; upgrade visuals (glow/trim/shape); terrain
(wading = half speed, 0.75x damage; chases hold the bank; melee ring slots; line formations; stuck nudge);
possession with unit hopping; veterancy; combat reactions round A (Kite, Brace); shield domes. Five Sunforged
bodies built through the concept -> game pipeline (generated model + painted texture + team colour + glow +
animation subclass, each checked in an eyes close-up): the Paladin (winged "Radiant" - folded wings, planted
greatsword, halo; walk, idle, chop and brace), the Field Medic (staff with a spinning sun disc; cast on the potion
throw), the Halberdier (halberd planted at rest, guard against a target, thrust; Diamond Axes shows tempered parts),
the Crossbow Levy (aims to the shoulder, spans, rides a ravager; Multishot makes the crossbow glow) and the Squire
Runner (militia_unit: a team pennant that flutters with speed; sword or bow as he swaps; a side-on bow aim with the
arrow drawn; Militia Bows shows a slung bow). Captains wear the banner on these bodies (CaptainBannerLayer).
Generators: tools/gen_paladin.py, gen_medic.py, gen_halberdier.py, gen_levy.py, gen_squire.py; tools/game_preview.py
and tools/game_lineup.py show the in-game look offline. Never hand-edit
`unit/modelling/models/gen/` - change the generator in tools/ and re-export.

## 8. Roadmap (in order)

1. Install/verify the latest green build with testers; multiplayer feedback.
2. **Sunforged T1 bodies**: every unit readable at strategic zoom - one oversized signature prop each (Medic,
   Halberdier, Crossbow Levy, Squire Runner DONE); team colour the biggest colour at distance; slimmer limbs on the
   older bodies if wanted (the "clunky" lesson in the model notes).
3. Individual control: per-unit orders that survive inside a formation; "follow me / hold" from first person.
4. Terrain round 2: target dropping in open water (design question), **bridge building**.
5. **The unit bar** (biggest art push): Paladin and Halberdier DONE through the pipeline; next the heavy melee
   body and every faction's units the same way (concept passes -> dsl_to_game -> game_preview -> renderer +
   animation subclass -> eyes close-up); Blender action turntables (walk + attack) in concepts.yml.
6. Combat reactions round B: Parry, Focus fire, attack telegraphs, stagger/"death fling".
7. Battle Sandbox mode (place two budgeted armies, watch them fight).
8. Autopilot playtest recordings (an AI player in an eyes run, recorded for tuning).
9. Voice commands layer 3 (offline push-to-talk on top of the existing command language).
10. More barfx: ground rings, lightning bolts, frost shards; Paladin Sun Ward.
11. New heroes: Necromancer, element-bender, the Watcher (Gravebound stalker).
12. Hivebound faction.
13. Capitol pass: the capitol is the biggest building; every upgrade adds a visible change.
14. Performance (profiling a CI 4v4 battle), groves with canopy fade, storage vs income balance.
15. **Animated home screen** (lovish, Oct 16): sci-fi fantasy meeting a gothic-Byzantine church empire - gold mosaic
    domes, pointed arches and rose windows lit by future light (light-lines in the stone, a halo-ring or second moon),
    slow parallax / drifting light that loops without a seam; the name "Beyond and Tabs" in our own gilded lettering
    that stands out (a light sweep, bloom, rising motes, a reveal on launch); a credit line "Designs by lovish".
    Original art only; concept frames to lovish first. Spec: the project doc claude/home-screen.md.

Good first tasks for a new developer: a GameTest for an untested rule, a Sunforged silhouette prop in
`design/models/`, a barfx effect type, or a balance pass with `tools/balance_audit.py` + the duel CI numbers.

## 9. Further reading

- `design/*.md` - per-system design notes and progress logs (camera, economy, bots, multiplayer, building upgrades,
  terrain intelligence, combat engineers, big units, audio...).
- `design/models/README.md` - the model DSL and concept pipeline.
- `NOTICE.md` - credits and licences.
