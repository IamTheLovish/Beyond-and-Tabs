#!/usr/bin/env bash
# Headless experiments and render sheets for the generated maps (PR #131). Plain JDK 17+, no Minecraft, no Gradle.
#   tools/mapgen_sim/run.sh balance 2 200        spread per balancer, 2 seats, 200 seeds
#   tools/mapgen_sim/run.sh robust 2 100 40      share of 40 weight draws each map stays within 5%
#   tools/mapgen_sim/run.sh time 8 30            build time at 4v4
#   tools/mapgen_sim/run.sh gametest             the 12 maps MapGenGameTests sweeps
#   tools/mapgen_sim/run.sh sheet OUTDIR 2 6 PROSPECTOR_WARDEN RIVER   PNG contact sheets (players, maps per sheet, contrasts)
#   tools/mapgen_sim/run.sh junit                the maps/gen JUnit tests (bar/src/test/.../maps/gen) on a JUnit shim (junit/)
# The Phase 1 harness (lab/: the economy sim, the experiments, the hill-shaded sheets); each main's javadoc has its usage:
#   tools/mapgen_sim/run.sh lab GenExperiments OUTDIR 8 ROUND PROSPECTOR_WARDEN 200 2 12   one exp1 cell (CSV + sheet)
#   tools/mapgen_sim/run.sh lab GenProdStats prod.csv 40      lobby maps: time, fallbacks, spreads, robustness
#   tools/mapgen_sim/run.sh lab GenRobustCmp robust.csv 8 16  SWAP_NUDGE vs SWAP_ROBUST under 40 weight draws
#   tools/mapgen_sim/run.sh lab GenTeamBias bias.csv 20 40    4v4 team gap in the sim, by contrast
#   tools/mapgen_sim/run.sh lab GenCurves curves.csv 60       1v1 sim curves per archetype
#   tools/mapgen_sim/run.sh lab GenShowcase OUTDIR            the 1v1 / 4v4 / balancing sheets
#   tools/mapgen_sim/run.sh lab GenSeam seam 4                mirror fold check (and "hash K" for non-mirror maps)
#   python3 tools/mapgen_sim/analyse.py EXPDIR OUTDIR [curves.csv]   exp1 tables and charts (pandas, matplotlib)
# It compiles a copy of maps/gen + MapLayout/MapPreset/MetalOptions/HomeMetal with a stub of CapturePointServerEvents.Kind.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
SRC="$HERE/../../bar/src/main/java/com/solegendary/reignofnether"
WORK="${MAPGEN_SIM_WORK:-$(mktemp -d)}"
P="$WORK/src/com/solegendary/reignofnether"
mkdir -p "$P/maps" "$WORK/out"
cp -r "$SRC/maps/gen" "$P/maps/"
cp "$SRC/maps/MapLayout.java" "$SRC/maps/MapPreset.java" "$SRC/maps/MetalOptions.java" "$SRC/maps/HomeMetal.java" "$P/maps/"
cp -r "$HERE/src/." "$WORK/src/"
cp -r "$HERE/stub/." "$WORK/src/"
cp -r "$HERE/lab/." "$WORK/src/"
cmd="$1"; shift
if [ "$cmd" = junit ]; then   # Gradle is not reachable in cloud sessions; CI runs these through the real JUnit
  TESTS="$HERE/../../bar/src/test/java/com/solegendary/reignofnether/maps/gen"
  cp "$TESTS"/*.java "$P/maps/gen/"
  cp -r "$HERE/junit/." "$WORK/src/"
fi
javac -nowarn -d "$WORK/out" $(find "$WORK/src" -name '*.java')
if [ "$cmd" = junit ]; then
  java -Xmx3g -cp "$WORK/out" JUnitLite $(cd "$TESTS" && ls *Test.java | sed 's/\.java$//; s/^/com.solegendary.reignofnether.maps.gen./')
elif [ "$cmd" = lab ]; then
  main="$1"; shift
  java -Xmx3g -Djava.awt.headless=true -cp "$WORK/out" "com.solegendary.reignofnether.maps.gen.$main" "$@"
elif [ "$cmd" = sheet ]; then
  java -Djava.awt.headless=true -cp "$WORK/out" com.solegendary.reignofnether.maps.gen.MapSheet "$@"
else
  java -cp "$WORK/out" com.solegendary.reignofnether.maps.gen.MapGenSim "$cmd" "$@"
fi
