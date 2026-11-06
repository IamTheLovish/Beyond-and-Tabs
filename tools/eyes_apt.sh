#!/usr/bin/env bash
# Installs the eyes recorder's packages (.github/workflows/eyes.yml: ffmpeg, xdotool) without eating the job's time.
#
#   bash tools/eyes_apt.sh <deb cache dir> <package>...      # each package's binary has the package's name
#
# Nov 6: `apt-get install -y ffmpeg` pulled ~80 packages from the runner's azure.archive.ubuntu.com mirror at ~40 kB/s
# and took 32-37 min (runs 42145511019, 42159677482, 42165274016, 42153307758). Three of those four runs were then
# cut at the job's 45-min limit before the tour ended. Now:
#   - the .debs of an earlier run come back from the actions cache (eyes.yml restores <deb cache dir> before this and
#     saves it right after), so apt downloads only what is missing;
#   - --no-install-recommends: x11grab and H.264 come from ffmpeg's own dependencies, not its recommended VA drivers;
#   - the download has a time budget: past APT_BUDGET s (default 420) apt is stopped and retried ONCE from
#     archive.ubuntu.com instead of the runner's mirror list, with the same budget;
#   - one notice says how long it took and where the packages came from.
# Exits non-zero if a package is still missing: the recorder needs both, and the step says why it failed.
set -uo pipefail
cache=$1; shift
budget=${APT_BUDGET:-420}
t0=$(date +%s)
need=()
for p in "$@"; do command -v "$p" > /dev/null 2>&1 || need+=("$p"); done
if [ ${#need[@]} -eq 0 ]; then
  echo "::notice::[Eyes apt] $* already installed (0 s)"
  exit 0
fi
mkdir -p "$cache"
cached=$(find "$cache" -maxdepth 1 -name '*.deb' | wc -l)
[ "$cached" -eq 0 ] || sudo cp "$cache"/*.deb /var/cache/apt/archives/
opts=(-y -q --no-install-recommends -o APT::Keep-Downloaded-Packages=true -o Acquire::Retries=3
      -o Acquire::http::Timeout=30)
install() { sudo DEBIAN_FRONTEND=noninteractive timeout "$budget" apt-get install "${opts[@]}" "${need[@]}"; }
from="the runner's mirror"
if ! install; then
  echo "::warning::[Eyes apt] apt did not finish in ${budget} s from the runner's mirror - retrying from archive.ubuntu.com"
  sudo dpkg --configure -a || true
  # ubuntu-24.04 images read their archive from a mirror list (mirror+file:/etc/apt/apt-mirrors.txt): point it at the
  # main archive; the package lists stay valid (same suite, same URI in sources)
  if [ -f /etc/apt/apt-mirrors.txt ]; then
    echo "http://archive.ubuntu.com/ubuntu/" | sudo tee /etc/apt/apt-mirrors.txt > /dev/null
  else
    sudo sed -i 's#http://azure.archive.ubuntu.com/ubuntu#http://archive.ubuntu.com/ubuntu#g' /etc/apt/sources.list \
      /etc/apt/sources.list.d/*.sources 2>/dev/null || true
  fi
  from="archive.ubuntu.com (after ${budget} s on the runner's mirror)"
  install || true
fi
find /var/cache/apt/archives -maxdepth 1 -name '*.deb' -exec cp -n {} "$cache"/ \; 2>/dev/null || true
missing=()
for p in "${need[@]}"; do command -v "$p" > /dev/null 2>&1 || missing+=("$p"); done
took=$(( $(date +%s) - t0 ))
debs=$(find "$cache" -maxdepth 1 -name '*.deb' | wc -l)
if [ ${#missing[@]} -gt 0 ]; then
  echo "::error::[Eyes apt] still missing after ${took} s: ${missing[*]} (from ${from}; ${cached} .debs came from the cache)"
  exit 1
fi
echo "::notice::[Eyes apt] ${need[*]} installed in ${took} s from ${from}; ${cached} .debs came from the cache, ${debs} are in it now"
