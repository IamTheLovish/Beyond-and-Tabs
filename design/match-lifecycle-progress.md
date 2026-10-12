# match-lifecycle - progress

Branch for Reign of Nether rewrite #5 (design/ron_rewrite_plan.md): a match lifecycle state machine (`MatchState`:
lobby -> map rising -> start positions -> playing -> ended, saved with the world), defeat / victory / spectate moved
out of `PlayerServerEvents` into a `match` package, and code_review CR-13 (a reload during map rising or the
start-position phase). Base: origin/main d73acee. Not merged.

## Done
- Behaviour pins first: `gametest/MatchLifecycleGameTests` (own holder) - commander death ends a 1v1 and the base
  becomes 50%-metal wrecks; commander death in a team game fights on (twice); victory to the last team standing (team
  and free-for-all, winnersAmong); resign alone surrenders, a passed team vote beats every human while a bot ally
  fights on; spectate refused mid-fight, granted to a winner who keeps their base. Written against the pre-rewrite
  public API so the same tests run on the old and the new code.

## Next
- `match` package: MatchPhase, MatchState (+ SavedData), roster without synchronized lists, defeat / victory /
  spectate; PlayerServerEvents keeps thin delegates.
- CR-13: resume a reloaded map rising / start-position phase; round-trip GameTests for every phase.
- Docs: "Rewrite 5" in design/ron_rewrite_plan.md, CR-13 marked in design/code_review.md.
- Stretch: spectator player-view switch, server-synced population counts, "Leave match" for spectators.

## Known failures
- none yet

## Proposals
- (filled in as the work goes)
