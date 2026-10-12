# playtest-0123 - progress

Branch for lovish's Oct 12 01:23-01:38 clips (build e211f6a, Watch Battle). Report: design/playtest_oct12.md.

## Done
- Spectator stays in the RTS view: toggle key (Backspace/F12) refused while spectating, auto re-enter twice a second
  (OrthoviewClientEvents, PlayerClientEvents.isMatchSpectator).
- Esc in first person during a live match opens the Match Menu (MatchMenuClientEvents).
- Quick Battle enters the RTS view after 5 ticks instead of 40 (QuickBattle).
- Bot lost start: no worker + no finished capitol in the first 3 min -> reload home chunks (5 s), re-seat 3 workers
  (10 s), max twice, WARNs; early bot unit deaths logged (BotPlayer.lostStart, BotServerEvents.onLivingDeath,
  PlayerServerEvents.spawnBotStartUnits). GameTest PlaytestGameTests.bot_with_a_lost_start_reseats_its_workers.
- Skirmish setup screen: faction buttons sized from the real row name, first word when still too narrow; footer
  wraps and stays on screen; map preview shrinks so the right column clears Watch Battle.

## Next
- Arena chunk pre-generation for spectators (black unloaded areas while panning) - evaluate.
- design/playtest_oct12.md + crops under design/playtest_oct12/.
- CI.

## Known failures
- none yet (CI pending)
