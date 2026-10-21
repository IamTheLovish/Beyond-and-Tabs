# Parallel Claude sessions - lanes and tonight's decisions (Oct 20 2026)

lovish runs more than one Claude on this repo (same GitHub account). To avoid stepping on each other:

## Lanes
- **Lane A - gameplay, bugs, performance, CI** (the main session): playtest bug fixes, clumping/separation, pacing,
  the lag profiler, installs on lovish's PC, map/economy code.
- **Lane B - buildings and art** (a second session, when started): the building revamp to his references, the gothic
  part library, faction capitols in six levels, the texture pack, building visuals (runes/glyphs) - see below.
- **Lane C - UI, rendering bugs, water crossing, formations, graphics presets, neutral objectives** (a human
  developer and his Claude, from Oct 20): onboarding in `design/HANDOFF-lane-c.md`.
- Shared files every lane touches (gametest/SkirmishGameTests.java, mixins json, build.gradle): small focused PRs,
  `git fetch` + merge main before pushing; when two branches add GameTests at the same spot, keep both tests whole
  (never stitch conflict hunks - check_java must pass after a merge, and gate the push on it).
- Never stash/reset/checkout over another session's uncommitted work; one branch per task; PRs, CI green, then merge.

## Decisions to build toward (lovish, Oct 20)
- **No Reign of Nether content players can see.** Reign of Nether is the engine (RTS camera, selection, orders,
  production - GPL-3.0, credited in NOTICE.md / mods.toml as the licence requires). Every unit, building, name, icon
  and sound becomes original; the three inherited factions (White Angel, Death, Ashborn) are rebuilt in place;
  "use it for inspiration, but I wanna get more creative than just copying stuff". Death: recognisable undead
  redesigned in our style AND fully original creatures.
- **Buildings grow over six levels** into a footprint reserved at placement (L1 placed, L2-L3 at tech 1, L4-L5 at
  tech 2, L6 at tech 3), bigger and more intricate each level. Detail first, optimise after. Interiors allowed (units
  never trapped). Every walkable space >= 4 wide x 8 tall or sealed. Labs training large units get an exit apron.
- **Macaw's Roofs / Windows / Fences + Chipped are required** (PR #28) - use their blocks; never copy their assets.
- **White Angel:** warm light grey stone, red cones, a gothic castle-cathedral on a mountain crag, angel statue on
  the crown, timber gallery storeys, curtain wall from L4, timber town below. Work in progress: tools/gothic_parts.py,
  tools/gothic_towers.py, tools/capitol_white_angel.py (branch claude/white-angel-capitol-stages).
- **Other factions' directions:** Death = a pale haunted world-tree; Verdant = a living tree-castle; Stormcallers =
  sandstone towers with jade domes and gold; Tidewrought = a glowing coral palace with a ring + a sea-god temple with a
  water spiral; Ashborn/Brassworks = carved brick palace on piers. Futuristic faction (later) = ring-gate towers.
- **Neutral:** capture points as floating ruins that reassemble when held (beams in the holder's colour); merchant
  posts in a whimsical storybook style; a split light/dark cathedral as a central objective.
- **Visual language:** runes everywhere - glyph circles over spires growing with level, construction by rune circles
  instead of nano lines (Brassworks: gears/steam), stacked rune beams for big moments, red ground telegraphs.
  Effects via the Photon library (GPL-3.0), animations via GeckoLib (MIT) - client-side, playable at 300 units.
- **Playability is extremely important:** graphics presets (Low-Ultra), measure with the [PerfAuto] profiler, slower
  pacing so heroes keep up, +10% per stat upgrade rank (capabilities are the exciting upgrades - parked until units
  are developed).
- Judge buildings in game: the eyes `structure` lane (PR #29) photographs any structure with real textures/shaders.

## Decisions to build toward (lovish, Oct 21)
- **Design units from strategy and feel, not stat sheets.** "dont look at flat numbers for balancing think like a
  player would think like a developer think about multiple different strategies" (02:35). Each unit answers: what
  strategy it opens, what the enemy must do when it arrives, how a skilled player uses it, what beats it and what it
  beats, how it feels to command. Proven by strategy scenarios and playtests; the duel sim checks fair trades only.
- **Weaknesses never ruin a unit.** "dont let weaknesses ruin unit design please" - "that is a bad weakness we found"
  (02:35, after T3s were all slow). A unit is built from its strength; its counter is a specific situation or answer
  (anti-air, a wall line, burst, being caught in a choke), never a core stat that makes it bad at its own job.
  In practice: no ground T3 slower than the army it marches with unless its role is to stay back (artillery,
  summoner); every T3 has a job and a moment (claude/t3-roles.md in the project).
- **T3 can be anything:** "tier 3 units can also be fast or have cool effects, or be long range artillarys, or cool
  summoning units, they can be anything" (02:34). BAR's Marauder is the flanker model: fast, enough mass to push
  through and flank, still packs a punch.

## Working habits for every session (from lovish's agent handbook, Oct 20)
Picked from a 212-tip handbook of measured Claude Code practice; these fit this repo.
- **Check what changed, not what the agent says.** Before merging an agent's or a session's work, read the diff and
  the CI check runs; a summary that does not match the record is the signal.
- **A check passes only on its own exit code.** Never report check_java, a test or CI as green from a grepped or tailed
  pipe; run it as its own command and read its result.
- **Fresh session per big task, with a written handoff** (owner and task, files it may touch, done so far, open
  questions, next action). Rewrite the note rather than append. Prefer this to long compacted sessions: every reply
  re-reads the whole context, and the first message after an hour away re-buys all of it.
- **Subagents are most of the bill.** Give each one a narrow task and a hand-back point well before a huge context;
  high effort, not max, for routine work (max for hard bugs, architecture, final reviews); cheaper models for CI
  watching, merges and test runs.
- **Watch CI for terminal states only** (green / red per run), not every job step - each notification is a paid turn.
- **Separate checkouts or disjoint files.** Sessions never stash, reset, checkout or `add -A` over a tree another
  session uses; commit by path.
- **Proof per visual change:** every PR that changes what players see attaches eyes stills (or a short clip).
- **Art passes against a growing fault list:** `design/ART-FAULTS.md` - a reviewer checks every render against it
  before lovish sees it; lovish judges taste only, and every fault he catches is added to the list.
- **Hand-made data stays hand-made:** generators never overwrite structures or maps someone placed by hand; generated
  and hand-built files live apart.

## Retired decisions
(When lovish reverses a decision, move it here with the date, his old words and his reason - never delete it, so a
later session can see what used to hold and why it changed.)
- **Retired Oct 21 2026 - "every T3 has a clear weakness (slow, big target, no anti-air, or a resource drain)"**
  (decided Oct 9, project doc claude/design-factions.md). In practice it made every T3 a slow melee giant the enemy
  walked around ("so far every tier 3 unit I used was extremely slow and almost useless"). Replaced by "Weaknesses
  never ruin a unit" (Oct 21 above). Still holds from Oct 9: T3s are expensive, answerable, and lose to a focused
  counter army of equal cost - through counters, not crippled stats.
