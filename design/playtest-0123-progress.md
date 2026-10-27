# playtest-0123 - progress

Branch for lovish's Oct 12 01:23-01:38 clips (build e211f6a, Watch Battle). Report: design/playtest_oct12.md
(crops in design/playtest_oct12/). Status (Oct 26 2026): on main, no PR number - it landed before the PR workflow (`isMatchSpectator` first appears in OrthoviewClientEvents at 1fe3bbf7, 2026-10-16).

## Done (CI green on 0843a76)
- Spectator stays in the RTS view: toggle key (Backspace/F12) refused while spectating, auto re-enter twice a second
  (OrthoviewClientEvents, PlayerClientEvents.isMatchSpectator).
- Esc in first person during a live match opens the Match Menu (MatchMenuClientEvents).
- Quick Battle enters the RTS view after 5 ticks instead of 40 (QuickBattle).
- Bot lost start: no worker + no finished capitol in the first 3 min -> reload home chunks (5 s), re-seat 3 workers
  (10 s), max twice, WARNs; early bot unit deaths logged (BotPlayer.lostStart, BotServerEvents.onLivingDeath,
  PlayerServerEvents.spawnBotStartUnits). GameTest PlaytestGameTests.bot_with_a_lost_start_reseats_its_workers.
- Arena chunks pre-generated in the background once the wall is planned (startpos/ArenaPregen).
- Spectator gets "BotN joined as X." instead of "scout it before it scouts you".
- Skirmish setup screen: faction buttons sized from the real row name, first word when still too narrow; footer
  wraps and stays on screen; map preview shrinks so the right column clears Watch Battle.

## Next (ideas, not started)
- Server-synced per-player unit counts for the observer table (far bots read 0 population).
- BAR player-view switch for spectators.

## Known failures
- Run e395bf9 failed two unrelated tests (each_faction_hero_trains_at_its_lab, mirror_images_decoys_...); the next two
  runs with the same code were green - flaky neighbours, not this branch.
