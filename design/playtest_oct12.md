# Playtest Oct 12, 01:23-01:38 - lovish, Watch Battle (bots only)

Seven clips (~100 s each, 1920x1080, windowed Minecraft + Windows taskbar), build **e211f6a** (88 commits older than
main 102ba10; every finding below was checked against main before fixing). Branch `playtest-0123`.
Crops: `design/playtest_oct12/` (time stamps are into each clip).

| Clip | What it shows |
|---|---|
| 01-23-30 | A 4-bot Watch Battle (Bot2-5, world "Skirmish 1"): 36 s in first person walking Bot5's piglin base, then RTS view, Observer Menu (Z), panning a Huge arena |
| 01-26-32 | Same match: first person the whole clip, Esc -> **vanilla Game Menu**, Save and Quit, then the Multiplayer screen |
| 01-28-34 | Menus: CurseForge RoN page, accessibility settings, Skirmish screen (Huge / Rich / Far, 4 hard AIs), delete world, Watch Battle, loading |
| 01-30-32 | New match (Bot6-9): RTS view, drops to first person twice (0:30, 1:15 - at night), vanilla Game Menu at 1:24 |
| 01-33-52 | Selecting bot buildings/units, drop to first person at 0:24, flying round Bot7's base, vanilla Game Menu at 1:12 |
| 01-35-52 | First person / RTS back and forth, Match Menu in RTS view (0:54, 1:27), first person again at 1:00 and 1:33 |
| 01-38-24 | Mostly RTS: capture-site messages, bot bases, a Sentinel Treant fight, Match Menu at 1:30 |

**Typed notes**: none. The bottom-left chat line was checked on every second of all seven clips (crops every 2 s,
plus tesseract): it only ever shows the hotbar, the selection panel or building tooltips.

## Findings

### 1. Three of four bots never started (01-23-30) - FIXED (safety net + diagnostics)
- 0:51 `01_observer_table_0m51.jpg`: Bot2/3/4 at 0 metal income, **1000 / 1000** metal and energy untouched, 0/400
  population, 0 workers, 0 army; Bot5 1178 / 538, 10 pop. 1:45 `02_player_panel_income_1m45.jpg`: Bot2-4 still
  +0.0 / +0.0 while Bot5 makes +12.5 metal / +81 energy. The minimap only ever shows Bot5's base.
- The population/worker columns count units the **client** has loaded (ObserverPlayerDisplay -> UnitClientEvents),
  so far-away bots always read 0 for a spectator - that part is a display artefact. The untouched 1000/1000 pools are
  server data, though: those bots never spent anything for 100+ s. Clip 01-30-32 (Bot6-9, same settings) shows a
  healthy start - every bot spends within 20 s and +0.0 income for the first ~40 s is normal (no extractor yet).
- Root cause (code): `BotPlayer.think` returns at its capitol step every second when it has **no worker and no
  capitol** - nothing can ever be built or trained again, so the bot idles for the whole match. How the three bots
  lost their workers can't be read from video (no log for this session). Candidates in the code: start units that
  "left the level" with an unloading chunk are not a defeat (UnitServerEvents) but drop out of the unit list, so no
  unit ever asks UnitChunkLoader for their chunk again (a deadlock); or start units dying on arrival (wall, water, fall).
- Fix: `BotPlayer.lostStart` - a bot seated through the real start (`PlayerServerEvents.startedAtTick`) with no worker
  and no finished capitol in its first 3 minutes **reloads and holds its home chunks** after 5 s (brings back units
  that were merely unloaded) and **re-seats its three starting workers** (commander first) after 10 s, at most twice,
  with WARN lines saying what it found. `BotServerEvents.onLivingDeath` logs any bot unit that dies in the bot's
  first minute (type, position, damage source), so the next log names the cause. Start spawning moved into
  `PlayerServerEvents.spawnBotStartUnits` (shared). GameTest:
  `PlaytestGameTests.bot_with_a_lost_start_reseats_its_workers` (and a hand-built side without startedAtTick is left
  alone).
- Not fixed: the observer table's population for far bots (needs server-synced unit counts).

### 2. Spectator sees large dark "unexplored" areas (01-23-30 0:57-1:12) - FIXED (background pre-generation)
- `03_unloaded_terrain_1m00.jpg`: half the screen is dark grey with chunk-staircase edges at 45 degrees while he pans
  across a Huge arena. Fog is already off for spectators (`FogOfWarClientEvents.isEnabled` needs an RTS player);
  these are chunks the server has **never generated** - the camera waits on world generation as it moves.
- Fix: `startpos/ArenaPregen` - once the wall is planned (`BattlefieldWall.planRing`, random and pre-made maps) every
  chunk inside the ring is generated in the background, centre outwards, 2 in flight (level-33 region tickets, async
  chunk system, ticket dropped on arrival, 30 s give-up), paused while the wall is still being raised and only with a
  human online (GameTest servers skip it). Afterwards terrain anywhere in the arena is only read back from disk.
- Not verified in a real game (no client here): time to finish a Huge arena on lovish's machine. The client still
  only draws its own render distance; zoom is already clamped to what that can fill (ZOOM_MAX_HARD).

### 3. Spectator drops into first person; Esc then shows the vanilla Game Menu - FIXED
- `05_drop_to_first_person_0m23.jpg` (01-33-52 0:23 -> 0:24), also 01-30-32 0:30 and 1:15
  (`06_night_first_person_1m18.jpg` - it is simply night; the RTS view hides it), 01-35-52 1:00 and 1:33. Each is an
  instant cut with no selection or click, the camera zoomed in close; the vanilla hotbar with the guide book shows the
  player back in creative. `07_vanilla_game_menu_0m45.jpg`: Esc in that view = Advancements / Statistics / Give Feedback.
- Root cause: in e211f6a (and main) the only ways out of the RTS view are `OrthoviewClientEvents.toggleEnable` via
  the toggle key (**Backspace**, F12 alias), `/rts-camera` or a survival/adventure game mode; nothing server-side
  sends SET_RTS_CAMERA. So it was the toggle key - probably reached for on purpose to look closer, which BAR never
  offers a spectator. The key was honoured for spectators, and `MatchMenuClientEvents` only replaced the pause menu
  while the RTS view was up.
- Fix: `PlayerClientEvents.isMatchSpectator()` (live match, not an RTS player, not sandbox/tutorial, not a Hero-role
  seat). The toggle key no longer leaves the RTS view while spectating (HUD line "Spectating: the view stays on the
  battlefield (Esc for the match menu)"), and a spectator found outside it is put back within half a second. Esc in a
  first-person view during a live match you play or watch opens the Match Menu, not the vanilla one.

### 4. The match starts in first person (01-23-30 0:00-0:36, 01-26-32) - FIXED
- `04_match_in_first_person_0m01.jpg`. Quick Battle waited 40 ticks in the vanilla view before entering the RTS
  camera; a spectator who ended up outside it stayed there until he found the key (36 s in clip 1).
- Fix: Quick Battle enters the RTS view after 5 ticks; the spectator auto re-enter from 3 covers every other path.

### 5. Skirmish screen at GUI scale 4 (01-28-34 1:09) - FIXED
- `08a_skirmish_names_clipped_1m09.jpg`: faction buttons read "rged Kingdo", "Verdant Co", "ravebounc", "onhide Hor";
  `08b_skirmish_footer_cut_1m09.jpg`: the hint line is half below the window edge. Main had widened the button to
  104 px, but the reserve for the row name was a flat 90 px, so on a 1080p window (GUI 4, 480 x 258) it was still 60 px;
  main's six settings buttons also ran under Watch Battle at that height.
- Fix: button width from the row's real name ("AI" is 11 px), first word of the faction when still too narrow, hint
  wraps to the panel width and the panel's bottom makes room for it, map preview shrinks so the right column fits.

### 6. Spectator chat says "scout it before it scouts you" (01-30-32 0:00) - FIXED
- `09_spectator_scout_message_0m00.jpg`. The bot-join line is written for a player with a base; a spectator now gets
  "BotN joined as <faction>." (`BotServerEvents.addBotImpl`).

### 7. Noted, not changed
- `10_spectator_enemy_command_card_0m21.jpg`: a spectator selecting a bot's commander/building gets its full build
  menu and production buttons; the server refuses the clicks (`Tried to process packet from ... for BotN` in earlier
  logs). Useful for reading a bot's options, harmless; hiding it is a HUD design call.
- `11_match_menu_in_rts_view_0m54.jpg`: the Match Menu works in the RTS view (Resume / Options / Mod Settings / Open to
  LAN / Save and Quit) - for a spectator a "Leave match" wording would read better than Save and Quit.
- Spectator selection of an enemy unit shows "<Bot>'s resources" under the economy bar - good, BAR-like.
- No HUD overlaps, missing icons or performance drops seen in the RTS frames; armies were active in clip 01-38-24.

## BAR's spectator, for comparison (next steps)
- Full view by default (fog off) - we have it. **Player-view switch** (watch one side's line of sight and economy,
  hotkeys per player) - missing; the Observer Menu (Z) and player panel give the numbers only.
- Smooth free camera with no body - now enforced (finding 3). BAR's Esc menu for a spectator says "Quit", not Save.
- Units of every side are always visible; ours depend on the client's entity tracking range, which is why far bots
  read 0 population (finding 1) - a server-synced per-player unit count would fix the table.
