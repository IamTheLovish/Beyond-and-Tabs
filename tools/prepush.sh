#!/usr/bin/env bash
# Run CI's fast checks locally before a push, so a 25-minute CI run is never spent finding a check_java error.
# Mirrors the `build` job in .github/workflows/build.yml up to the gradle step (keep the two in step: when a check is
# added there, add it here). Seconds, not minutes. Exit 0 = every check passed; it runs them all and lists the failures.
# Usage: bash tools/prepush.sh            (from the repo root)
#        bash tools/prepush.sh --quick    (skip the engine javac bench compile)
set -u
cd "$(git rev-parse --show-toplevel)" || exit 2
quick=0; [ "${1:-}" = "--quick" ] && quick=1
failed=()
run() {  # run "<label>" <command...>; its own exit code decides
  local label="$1"; shift
  if "$@" >/tmp/prepush.$$.log 2>&1; then echo "ok    $label"
  else echo "FAIL  $label"; sed 's/^/      /' /tmp/prepush.$$.log | tail -25; failed+=("$label"); fi
}
run "preflight (design sheets)"   python3 tools/preflight.py --allow-unverified
run "generated code matches"      bash -c 'python3 tools/gen_java.py && git diff --exit-code engine/src/main/java/dev/beyondtabs/engine/gen'
run "check_java"                  bash -c "python3 tools/check_java.py \$(git ls-files 'bar/src/main/java/*.java' 'mod/src/main/java/*.java' 'engine/src/*.java')"
run "check_guards"                python3 tools/check_guards.py
run "balance_audit"               python3 tools/balance_audit.py
run "check_building_icons"        python3 tools/check_building_icons.py
run "check_sail_hubs"             python3 tools/check_sail_hubs.py
run "check_parts"                 python3 tools/check_parts.py
run "hud_layout"                  python3 tools/hud_layout.py
run "animation lab data"          bash -c 'for f in design/anims/*.json; do [ -e "$f" ] || continue; python3 tools/anim_lab.py "$f" --check --motion || exit 1; done'
run "animation sheet tests"       python3 tools/test_anim_sheet.py KeyProgress Labels SelectNearest Layout Tracking
run "dependency pins"             python3 tools/check_dep_pins.py
run "gametest shard lists"        python3 tools/check_shards.py
if [ $quick = 0 ] && command -v javac >/dev/null; then
  run "engine compiles"           bash -c 'd=$(mktemp -d) && javac --release 17 -d "$d" $(find engine/src -name "*.java")'
fi
rm -f /tmp/prepush.$$.log
if [ ${#failed[@]} -gt 0 ]; then echo "prepush: ${#failed[@]} failed: ${failed[*]}"; exit 1; fi
echo "prepush: all passed"
