# match-lifecycle - progress

Branch for Reign of Nether rewrite #5 (design/ron_rewrite_plan.md): a match lifecycle state machine (`MatchState`:
lobby -> map rising -> start positions -> playing -> ended, saved with the world), defeat / victory / spectate moved
out of `PlayerServerEvents` into a `match` package, and code_review CR-13 (a reload during map rising or the
start-position phase). Base: origin/main d73acee. Not merged. Write-up: design/ron_rewrite_plan.md "Rewrite 5".

## Done
- Behaviour pins first: `gametest/MatchLifecycleGameTests` (own holder), written against the pre-rewrite public API -
  green on the old code (run 38248700318, c4460f0): commander death ends a 1v1 and the base becomes 50%-metal
  wrecks; commander death in a team game fights on (twice); victory to the last team standing (team and FFA,
  winnersAmong); resign alone surrenders, a passed team vote beats every human while a bot ally fights on; spectate
  refused mid-fight, granted to a winner who keeps their base.
- `match/` package: MatchPhase (+ transition table), MatchState (one owner for phase changes, effects behind
  MatchEffects = maps.MapMatch.EFFECTS), MatchSetup (frozen lineup + settings), MatchRoster (copy-on-write side
  lists, no `synchronized`), MatchOutcome (defeat, victory, beacon, commander rule, results), Spectators,
  MatchLifecycleSaveData (`bt-match-lifecycle`: state, alliances, finished sides), MatchServerEvents (clock, login,
  save hooks). PlayerServerEvents keeps thin delegates (1621 -> 1383 lines); MapMatch rebuilt as the effects;
  StartPhase save/load + held resume; MapBuilder.stage() accessor only; lobby/skirmish start through MatchState.
- CR-13: resume (rebuild the map unless its economy is stamped; start phase with picks and clock, held until a human
  is back, floor 20 s, early end only with every human back); clean cancel when a setup can't resume. Rejoining
  players get the RTS view and their camera on their pick; a notice on login says what the resume did.
- Fixed on the way (lifecycle bugs): alliances were never saved; the clock restarted at 0 on reload; finished sides
  forgotten on reload (winners couldn't spectate); beacon victory threw CME (defeats inside the synchronized walk);
  defeat cleanup half-done at save stays ownerless forever (now finished at server stop); raw translation keys as
  defeat reasons; skirmish / lobby start silently mixed into a running match (now refused with a message).
- New GameTests: every phase through NBT bytes + what each resume does; held start phase + floor; clean cancel (4
  ways); transition table + stray signals; alliances round trip; roster walk survives defeats; defeat cleanup
  finished at once; reason keys.
- Stretch: "Leave Match" instead of "Save and Quit" in the Match Menu for spectators; server-synced population for the
  spectator's observer table (PlayerPanel row/entry: popUsed / popWorkers / popArmy behind the existing ally /
  observer gate) + GameTest of the gate and codec. Also removes a per-frame walk over every client unit per row.
  Spectator player view (`hud/playerdisplay/SpectatorView`, client only): F8 follows the next side (Shift+F8 back,
  past the ends = full view) - its economy on the top bar while nothing is selected, the camera on its capitol.
  Not its line of sight (Proposals). Client-side: unverified headless.
- Docs: ron_rewrite_plan.md (Rewrite 5, inventory #16 done), code_review.md (CR-13 fixed), persistence.md,
  maps.md section 5, multiplayer.md.

## Next
- CI on the refactor commit (c74a564) and the stretch commit; fix anything red.
- Stretch left: the per-player view switch for spectators (see Proposals - LOS needs the fog subsystem).

## Known failures
- none yet (known flake elsewhere: `tidewrought_t2_units_attack`)

## Proposals
- **Battlefield forming is still memory only** (`startpos/BattlefieldSetup` 3 s settle, `BattlefieldWall` rising
  over 30-90 s): a save in that window reloads with no wall / half a wall (and on random terrain no patches).
  MatchState could keep a "forming" record {centre, radius, material, columns done} and replay `BattlefieldWall.buildAt`
  (column writes are idempotent) on load. Not done here: startpos/ is outside this lane.
- **Commander rule vs BAR**: BAR's commander-ends mode kills an ally team when its last commander falls; ours lets a
  team whose every commander is dead play on (pinned). lovish to decide.
- The `synchronized (PlayerServerEvents.rtsPlayers)` blocks left in bot/, building/, commands/, startpos/,
  gamemode/, gameender, CommandsServerEvents are harmless now (copy-on-write list) and can be dropped by their owners.
- `RTSPlayerSaveData` and `PlayerServerEvents.startedAtTick` belong with `MatchRoster`; move when nobody else is in
  player/.
- Spectator per-player view: switching the economy bar and camera to one side is client-only and easy; showing that
  side's *line of sight* needs the server to send its bright-chunk set to spectators who follow it
  (FogOfWarServerEvents) - safe (spectators may see everything) but a fog-subsystem change.
- `MatchEndScreen` results are client memory: after a reload of an ENDED match the results popup is gone; the finished
  sides are now saved, so the server could re-send `broadcastMatchStats` to a participant logging in to an ENDED match.
