#!/usr/bin/env bash
# Headless experiments and render sheets for the generated maps (PR #131). Plain JDK 17+, no Minecraft, no Gradle.
#   tools/mapgen_sim/run.sh balance 2 200        spread per balancer, 2 seats, 200 seeds
#   tools/mapgen_sim/run.sh robust 2 100 40      share of 40 weight draws each map stays within 5%
#   tools/mapgen_sim/run.sh time 8 30            build time at 4v4
#   tools/mapgen_sim/run.sh gametest             the 12 maps MapGenGameTests sweeps
#   tools/mapgen_sim/run.sh sheet OUTDIR 2 6 PROSPECTOR_WARDEN RIVER   PNG contact sheets (players, maps per sheet, contrasts)
# It compiles a copy of maps/gen + MapLayout/MapPreset/MetalOptions with a stub of CapturePointServerEvents.Kind.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
SRC="$HERE/../../bar/src/main/java/com/solegendary/reignofnether"
WORK="${MAPGEN_SIM_WORK:-$(mktemp -d)}"
P="$WORK/src/com/solegendary/reignofnether"
mkdir -p "$P/maps" "$WORK/out"
cp -r "$SRC/maps/gen" "$P/maps/"
cp "$SRC/maps/MapLayout.java" "$SRC/maps/MapPreset.java" "$SRC/maps/MetalOptions.java" "$P/maps/"
cp -r "$HERE/src/." "$WORK/src/"
cp -r "$HERE/stub/." "$WORK/src/"
javac -nowarn -d "$WORK/out" $(find "$WORK/src" -name '*.java')
cmd="$1"; shift
if [ "$cmd" = sheet ]; then
  java -Djava.awt.headless=true -cp "$WORK/out" com.solegendary.reignofnether.maps.gen.MapSheet "$@"
else
  java -cp "$WORK/out" com.solegendary.reignofnether.maps.gen.MapGenSim "$cmd" "$@"
fi
