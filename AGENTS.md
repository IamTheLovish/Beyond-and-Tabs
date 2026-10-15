# Beyond and Tabs - briefing for developers and their AI assistants

Read this first. It is the shared memory of the project for anyone working in this repo: what the game is, how the
code and tools fit together, how we work, and what comes next. It is kept current by the project lead's assistant
(see "Working together"); if something here is out of date, say so in your PR.

The project lead and owner is **lovish** (handle only - never use anyone's real name in code, commits, credits, docs
or anything public).

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
                          look offline), pose_preview.py (animation keyframes on paper)
.github/workflows/        CI and the "senses" (see section 5)
engine/ mod/              an older from-scratch prototype - reference only, not shipped
NOTES.md                  older session log; README.md is out of date (this file supersedes it)
```

## 4. Building and testing

- There is **no local Minecraft classpath** in most AI sandboxes: **CI is the compiler and test runner.**
- Before every push: `python3 tools/check_java.py` (structural/syntax gate without the MC classpath; must report
  0 structural errors). If you touch balance numbers: `python3 tools/balance_audit.py`.
- CI = `.github/workflows/build.yml`:
  - job `build`: tools and audits (no bar/ compile);
  - job `bar`: compiles `bar/` and runs the headless **GameTests** (~25 min). On `main` it publishes the jar to the
    GitHub release **`bar-dev`**.
  - Read failures from check-run annotations (`gh api repos/<owner>/<repo>/check-runs/<job-id>/annotations`) -
    test failures appear as `... failed! <message>`; measurements from passing tests appear as `[Melee]`,
    `[Stress]`, `[Soak]` notices.
- **Writing GameTests** (`bar/.../gametest/`): every public rule gets one. Keep everything inside the test's own
  arena; big tests get their own batch; load far chunks with `GameTestChunks.hold` + progress-based waits (never
  `setChunkForced`/`getChunk` on ungenerated chunks). Units **autocast** by default: a test that counts energy,
  mana or cooldowns must switch bystanders off with `AutocastState.setAll(unit, false)`. Known flakes are listed in
  `design/` progress notes and the CI history; don't paper over a flake - make its failure message diagnostic.
- Combat internals: unit damage bypasses vanilla (`mixin/LivingEntityMixin.actuallyHurt` + our ATTACK_DAMAGE
  attribute); BOTH `LivingHurtEvent` and `LivingDamageEvent` fire on that path (`ForgeHooks.onLivingHurt` /
  `onLivingDamage`, LivingEntityMixin ~265 / ~280) - Ward, Lantern Dome and the Brass shield rely on the hurt event. New `UnitAction` values go at
  the **end** of the enum.
- Structures: `tools/gen_lab_levels.py` generates the army labs' L2/L3 `.nbt` deterministically - check it
  reproduces the committed files byte for byte before changing it.

## 5. The senses (how we see and hear the game without a player)

All run on GitHub Actions, never on anyone's PC:
- **eyes.yml** - runs the real client under Xvfb after every green `main` build; `matchstart/EyesDirector.java`
  plays a scripted tour (build menu, army, box select, fights at two zooms, capitol, river crossing, domes...) and
  publishes stills **and a 10-second video clip per still** to the `eyes` branch. Add a scene when a feature has no
  shot that shows it. A feature nobody has seen is not done.
- **concepts.yml** - Blender 4.2 on CI renders unit concept sheets from `design/models/*.json` (our own cube-model
  DSL; `design/models/README.md`) to the `concepts` branch. Render 3 models or fewer per run.
- **ears.yml / voice.yml / voice-reel.yml** - audio analysis and the original narrator voice (open TTS; never a
  clone of a real actor).
- **web-eyes.yml** - screenshots of reference web pages. **modpack.yml / macpack.yml** - the tester modpacks
  (release `test-pack`).

## 6. How we work together

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

Good first tasks for a new developer: a GameTest for an untested rule, a Sunforged silhouette prop in
`design/models/`, a barfx effect type, or a balance pass with `tools/balance_audit.py` + the duel CI numbers.

## 9. Further reading

- `design/*.md` - per-system design notes and progress logs (camera, economy, bots, multiplayer, building upgrades,
  terrain intelligence, combat engineers, big units, audio...).
- `design/models/README.md` - the model DSL and concept pipeline.
- `NOTICE.md` - credits and licences.
