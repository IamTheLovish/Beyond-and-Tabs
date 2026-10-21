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
