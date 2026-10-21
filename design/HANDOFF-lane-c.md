# Handoff - a new developer and their Claude join Beyond and Tabs (Lane C)

Give this file to your AI assistant as its first message, together with the repo. Written Oct 20 2026 by the
project's main session ("Itachi") for lovish (project owner, handle only).

## 0. Who you are working with
- **lovish** owns the game and makes every art, feel and design call. Bring him options with pictures, not surprises.
- **Lane A** - lovish's main Claude session: gameplay AI, bug fixes, lag/performance, CI, installs on his PC.
- **Lane B** - a second Claude session: buildings, faction capitols, the texture pack, building visuals.
- **Lane C - you**: UI, rendering bugs, water crossing, formations, graphics settings, then the neutral-objective
  systems. These were picked so you rarely touch the same files as A and B.

## 1. Read first (in this order, before writing code)
1. `AGENTS.md` - the shared briefing: what the game is, the **hard rules**, repo map, building and testing, how we
   work together, roadmap. Its rules win over anything here.
2. `design/LANES.md` - lanes for parallel sessions and lovish's Oct 20 decisions (no visible Reign of Nether content,
   six-level buildings, runes visual language, playability first).
3. `design/` notes for whatever task you pick (grep the feature name).

The game in one line: a Beyond All Reason-style RTS inside Minecraft Forge 1.20.1 (Java 17), built on the Reign of
Nether engine (GPL-3.0) - RTS camera, selection, orders and production come from it; every faction, unit, building
and name is being made original. Code lives in `bar/` (package `com.solegendary.reignofnether`).

## 2. Setup
- **GitHub:** `IamTheLovish/Beyond-and-Tabs` (public). Ask lovish to add your GitHub account as a **collaborator**
  so you can push branches and CI runs on your PRs (a fork works too, but CI on fork PRs waits for approval).
- **Local build (optional, a real advantage over the AI sandboxes):** JDK 17, then in `bar/`:
  `./gradlew runClient` (play your build) and `./gradlew runGameTestServer` (the headless test suite, the same one CI
  runs). The building-block mods (Macaw's Roofs/Windows/Fences, Chipped, Athena, Resourceful Lib) are pinned in
  `bar/build.gradle` and load automatically.
- **CI is the source of truth** (`.github/workflows/build.yml`): jobs `build`, `bar` + `bar-tests (2)` and `(3)`
  (GameTest shards). Read failures from the check-run annotations. Merging to `main` publishes the jar testers
  install (release `bar-dev`), so `main` must never break.
- **Seeing the game without playing:** the `eyes` workflow photographs the real game (with shaders if asked) -
  see AGENTS.md section 5. Use it for anything visual and attach the stills to your PR.

## 3. Working rules (short version - AGENTS.md has the full list)
- One branch per task, named `yourname/topic`; small PRs; **open a draft PR as soon as you start** so the other lanes
  see it. One or two pushes an hour (each push starts a ~25 min test run).
- Before every push: `python3 tools/check_java.py` must report 0 structural errors. Every public rule gets a
  **GameTest**. Before pushing, `git fetch` and merge `main`.
- Shared hot files (`gametest/SkirmishGameTests.java`, `reignofnether.mixins.json`, `build.gradle`): if a merge
  conflicts where two branches added tests, keep **both tests whole** - never stitch conflict hunks. Better: put your
  tests in their own GameTest class.
- Originals only: no assets from other games/mods (not even recoloured). Never commit secrets or personal details.
- Never touch anyone's computer from automation without that person's OK.
- Commit messages say **why** (which playtest clip or bug forced the change).
- Playability matters most: "if people can't even play the game what is the point". Measure performance; the
  server logs `[PerfAuto]` lines and saves `logs/perf-auto-*.txt` when a big fight lags.

## 4. Your task list (in order - each is a PR; tick them off in the PR title)
Source: lovish's Oct 19 LAN playtest, reviewed second by second (full list in the Claude Project doc
`claude/bugs-oct19-playtest.md`; lovish can share it).

**C1 - UI clean-up (good first PRs, low conflict risk)**
- Experimental-unit announcement: a big black panel for 10+ s, bad wrap ("World Tree / >alker!") with a stray glyph,
  tooltips drawn over it. Make it short, styled, wrapped correctly.
- Notices stack and overlap (Idle factories over Construction complete; Overflowing energy + Energy storage full +
  WASTING together; WASTING over the control-group box); the kill feed jumps rows. One notice layout manager:
  queue, de-duplicate, never overlap the HUD.
- Small ones: raw key `research.reignofnether.research_tier_3` shown; tooltip clipped "Producing World Tree Wal";
  Match Menu subtitle overflows; health bars drawn over the minimap; enemy name label without a background; tall white
  selection hexagons clutter big selections; Thornheart Druid empty ability slots; Brace says passive but is
  toggleable; a "Raider - Leaf Dash" panel on a Leafblade; population counter jumps (502 -> 482 -> 502); lore
  leftovers ("elven cavalry", "the Court's units").

**C2 - Rendering bugs**
- Dragons drawn as flat panels (Thunder Dragon = flat orange planks; gold/teal panels multiply over a fight after a
  dragon dies and hide units).
- Portraits: Stag Lancer and Mossback Bison = dark smear (camera inside the model); World Tree Walker portrait blank;
  enemy building portraits are a flat cube placeholder.
- Floating "ETA" bars over empty grass; LOD pops between model / outline / square icon; buildings not rendering
  properly from the RTS camera (lovish's report - reproduce with eyes first).
- First person: a dark-green block fills the bottom-right (held item/arm); night in first person vs day overhead.
- Huge particle bursts cover whole groups in big fights (cap them; they also cost frames).

**C3 - Water crossing for every faction**
Units struggle to cross water and not every faction can build a bridge. Give every faction a way across (its own
bridge building in its style, or a shared one first), make pathing prefer bridges and shallow fords, and test that a
mixed army can reach a base across a river on each map that has one.

**C4 - Omnidirectional formations**
Today there is a line drag (gets ranks in Lane A's PR #26 - wait until it merges, it edits `FormationDragMove`)
and a rectangle. lovish wants any shape, e.g. "a spherical formation": add a ring/circle mode (and arcs), a key or
gesture to pick the shape, slots assigned so units don't cross paths, and a GameTest per shape.

**C5 - Graphics presets (Low / Medium / High / Ultra)**
One menu that sets our own costly features (particles, building animation, unit LOD distances, shader-friendly
options) so the game stays playable at 300 units on weak PCs. Measure before/after.

**C6 - Neutral objectives (design first, then build - check with lovish)**
Capturable points as floating ruins that reassemble when held (beams in the holder's colour), giving map control
advantages (e.g. a refinery or a tower); later merchant posts and hero jungle camps. Write a one-page design in
`design/` with sketches, get lovish's yes, then implement.

**Not yours (Lane A or B - ask before touching):** unit AI and clumping (`Separation`, `Engage`), possession, the
lag profiler, the `bots_play_each_other_without_errors` builder stall, installs, building structures/capitols,
textures (`tools/gen_blocks.py`), angel statue, maps' metal layout.

## 5. First hour checklist for your assistant
1. Read AGENTS.md and design/LANES.md; skim `bar/src/main/java/com/solegendary/reignofnether/hud/` (`NotificationClientEvents`, `EconomyBarRenderer`) for C1.
2. Confirm collaborator access (`git push` a branch) or set up a fork.
3. Run `python3 tools/check_java.py` on a clean checkout (expect 0 errors).
4. Open a draft PR "C1: notices never overlap" and start there.
5. When something is visible, dispatch eyes on your branch and post the stills for lovish.

Welcome - lovish wants to make the best RTS in Minecraft. Ask him when a call is about how the game should feel.
