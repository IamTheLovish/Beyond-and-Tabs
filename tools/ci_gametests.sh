#!/usr/bin/env bash
# The headless GameTest run of a CI build (.github/workflows/build.yml), shared by its test shards: run from bar/ as
#   bash ../tools/ci_gametests.sh
# The shard is chosen by the environment (gametest/GameTestShards: BAT_GAMETEST_ONLY / BAT_GAMETEST_SKIP, lists of
# test batch names); with neither set every batch runs, as before Oct 15. Moved here verbatim from build.yml's step so
# both shard jobs run the same watchdog, stall dumps, notices and verdict.
set -e
set -o pipefail
# a watchdog: a hung server (a deadlock at shutdown, a test that never settles) used to hold the job for the
# runner's 6 h limit with nothing readable; now it stops at 40 min and the log's tail becomes a notice (32 min
# was too tight once the suite passed ~25 holders on a slow runner)
# stall watcher: when the server log goes quiet for 150 s (a hang, a deadlock, a tick that never ends), take up
# to 3 thread dumps of the game-test JVM so the "Server thread" stack can be reported below - a watchdog kill
# alone says nothing about where it was stuck
( n=0; while sleep 30; do
    [ -f gametest.log ] || continue
    age=$(( $(date +%s) - $(stat -c %Y gametest.log) ))
    if [ "$age" -ge 150 ] && [ "$n" -lt 3 ]; then
      for pid in $(jps -l | grep -v -E "Gradle|Jps|Worker" | awk '{print $1}'); do
        jcmd "$pid" Thread.print > "stall_${n}_${pid}.txt" 2>/dev/null || true
      done
      n=$((n+1)); sleep 120
    fi
  done ) &
WATCHER=$!
# "[ChunkWait]" is kept too: the far-chunk waits (gametest/GameTestChunks) log how generation is going every 15 s
# and, when they give up, where every chunk round the missing ones stood - the two duel pad failures of Oct 17
# ("380 of 380 ... never loaded") were undiagnosable from the job log because that line matched none of these words
timeout 40m ./gradlew runGameTestServer --no-daemon 2>&1 | tee gametest.log | grep -E "GameTest|gametest|FAIL|fail|Exception|Error|error|ChunkWait" || true
kill $WATCHER 2>/dev/null || true
# the server thread's stack from each stall dump, one notice each (%0A = newline)
for f in stall_*.txt; do
  [ -f "$f" ] || continue
  awk '/^"Server thread"/{p=1} p&&/^$/{exit} p' "$f" | head -45 | cut -c1-220 | sed 's/%/%25/g' | awk -v f="$f" 'BEGIN{printf "::notice::[Stall dump " f "]"} {printf "%%0A%s", $0} END{print ""}' || true
done
# the worldgen / IO workers of the last dump (the server thread often waits on them): EVERY Worker-Main /
# IO-Worker / Server thread with its state and top frames, in one notice. It used to keep only stacks
# mentioning reignofnether or a lock, which left the notice empty in run 38342219886 (the server thread
# parked 20+ min in chunk loading while every worker looked idle) - an idle worker (WAITING in
# LockSupport.park / ForkJoinPool.awaitWork) is itself the finding, so each thread shows up, cut to its
# header, state and 7 frames
last=$(ls stall_*.txt 2>/dev/null | tail -1 || true)
if [ -n "$last" ]; then
  awk '/^"/ { p = ($0 ~ /^"(Worker-Main|IO-Worker|Server thread|Util|ForkJoin)/); n = 0; if (p) print "----" }
       p && !NF { p = 0 }
       p { if (n < 9) print; else if (n == 9) print "   ..."; n++ }' "$last" | head -220 | cut -c1-160 | sed 's/%/%25/g' | awk 'BEGIN{printf "::notice::[Stall workers]"} {printf "%%0A%s", $0} END{print ""}' || true
fi
# the per-system tick timing (perf/Sys, gametest/PerfProfileGameTests): a sample [Sys] line and the lag capture of the
# same ticks, folded into ONE notice - what the new lines in a playtest log look like
if grep -q "\[SysSample\]" gametest.log; then
  grep -oE "\[SysSample\].*" gametest.log | head -2 | sed 's/%/%25/g' | awk 'BEGIN{printf "::notice::[Sys sample]"} {printf "%%0A%s", $0} END{print ""}' || true
fi
# the 8v8 stress benchmark's per-system timings, as notices readable through the GitHub API
grep -oE "\[Stress\].*" gametest.log | head -5 | sed 's/^/::notice::/' || true
# the melee diagnostics (gametest/DuelGameTests): one line per fight, folded into ONE notice (%0A = newline)
# because a step only keeps the first 10 notices
grep -oE "\[Melee\].*" gametest.log | head -40 | sed 's/%/%25/g' | awk 'BEGIN{printf "::notice::[Melee diagnostics]"} {printf "%%0A%s", $0} END{print ""}' || true
# the bot soak (gametest/SkirmishGameTests bots_play_each_other_without_errors): one line per bot, one notice
grep -oE "\[Soak\].*" gametest.log | head -8 | sed 's/%/%25/g' | awk 'BEGIN{printf "::notice::[Bot soak]"} {printf "%%0A%s", $0} END{print ""}' || true
# contact fights at scale (gametest/CombatEngageGameTests): acquisition times and damage per side, one notice
if grep -q "\[Engage\]" gametest.log; then
  grep -oE "\[Engage\].*" gametest.log | head -4 | sed 's/%/%25/g' | awk 'BEGIN{printf "::notice::[Engage]"} {printf "%%0A%s", $0} END{print ""}' || true
fi
# the Tier 2 bots (gametest/TierGateGameTests bots_reach_tier_2...): when each began and finished the tech, then its state
# once a game-minute (buildings, incomes, what held it back) - one notice per bot (a notice keeps only ~4 KB)
for bot in hard medium easy; do
  grep -oE "\[T2(Timing|Snap)\] $bot .*" gametest.log | head -20 | cut -c1-230 | sed 's/%/%25/g' | awk -v b="$bot" 'BEGIN{printf "::notice::[T2 " b "]"} {printf "%%0A%s", $0} END{print ""}' || true
done
# combat reactions (gametest/ReactionGameTests): kite steps and brace damage ratios, one notice
grep -oE "\[Reaction\].*" gametest.log | head -12 | sed 's/%/%25/g' | awk 'BEGIN{printf "::notice::[Reactions]"} {printf "%%0A%s", $0} END{print ""}' || true
# the T1 raid measurements (gametest/RaidGameTests): time-to-kill per faction, short lines in ONE notice (a step keeps
# 10 notices; one notice about 4000 characters)
grep -oE "\[Raid\].*" gametest.log | head -46 | sed 's/%/%25/g' | awk 'BEGIN{printf "::notice::[Raid TTK]"} {printf "%%0A%s", $0} END{print ""}' || true
# the building-block mods' block ids (gametest/BuildingBlockModGameTests): counts and a sample per mod, one notice -
# the vocabulary for structure generators (only in the shard that ran that test)
if grep -q "\[ModBlocks\]" gametest.log; then
  grep -oE "\[ModBlocks\].*" gametest.log | head -6 | sed 's/%/%25/g' | awk 'BEGIN{printf "::notice::[Mod blocks]"} {printf "%%0A%s", $0} END{print ""}' || true
fi
# where the ~26 minutes go: wall time per test batch + the slowest tests, one notice (tools/gametest_timing.py)
python3 ../tools/gametest_timing.py gametest.log || true
if ! grep -qE "All [0-9]+ required tests passed" gametest.log; then
  # the test verdicts first ("<name> failed!", timeouts, the summary), then exceptions; chunk-load warnings
  # ("Tried to load a block entity ... failed") used to fill all 30 slots and hide the real failure
  # (each grep may find nothing - after a watchdog kill there is no "failed!" line - so every pipeline ends in
  # `|| true`: under bash -e and pipefail an empty grep used to end the step before the log tail below)
  { grep -E "failed!|timed out|required tests failed|Test .* failed" gametest.log | head -20 || true;
    grep -E "Exception|FAILED" gametest.log | grep -v "Tried to load a block entity\|server.properties\|rethrowAs\|translateTo" | head -10 || true; } | sed 's/^/::error::/' || true
  # the last lines the server wrote, folded into one notice (%0A = newline): shows a hang or a crash
  grep -v "Tried to load a block entity" gametest.log | tail -40 | cut -c1-300 | sed 's/%/%25/g' | awk 'BEGIN{printf "::notice::[Log tail]"} {printf "%%0A%s", $0} END{print ""}' || true
  echo "::error::game tests did not report success"; exit 1
fi
