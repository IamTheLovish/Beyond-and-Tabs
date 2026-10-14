#!/usr/bin/env bash
# Eyes video: records the CI client's Xvfb display while the scripted tour plays (.github/workflows/eyes.yml).
# Started in the background BEFORE the mc-runtime-test step; that step launches the game under `xvfb-run -a`, which
# picks the display number and writes an Xauthority cookie itself, so this script waits for both to appear, then
# records with ffmpeg's x11grab until it is sent SIGINT (the "Stop the recorder" step). Writes, in $OUT:
#   rec.mp4       the whole session, full screen, RATE fps
#   rec-start.txt epoch milliseconds at which ffmpeg was started (tools/eyes_cut.py aligns the "[Eyes] clip-mark"
#                 log lines against it to cut a short clip per screenshot)
#   rec.log       what happened
set -u
OUT=${1:-eyes-rec}
RATE=${EYES_FPS:-10}
mkdir -p "$OUT"
log() { echo "$(date -u +%T) $*" >> "$OUT/rec.log"; }
log "waiting for the Xvfb display"
disp=""; auth=""
for i in $(seq 1 900); do
  sock=$(ls /tmp/.X11-unix/X* 2>/dev/null | head -1 || true)
  auth=$(ls /tmp/xvfb-run.*/Xauthority* 2>/dev/null | head -1 || true)
  if [ -n "$sock" ] && [ -n "$auth" ]; then
    disp=":${sock##*/X}"
    break
  fi
  sleep 1
done
if [ -z "$disp" ]; then
  log "no display appeared in 15 min; not recording"
  exit 0
fi
export XAUTHORITY="$auth"
# GLFW centres the 1600x900 game window for a screen it believes is larger, so a third of it hung off the 1920x1080
# display's right and bottom edges in the first recording (run 38511368547). Move it to the origin once it exists.
if command -v xdotool > /dev/null; then
  (
    for i in $(seq 1 240); do
      wid=$(DISPLAY="$disp" xdotool search --name "Minecraft" 2>/dev/null | head -1 || true)
      if [ -n "$wid" ]; then
        DISPLAY="$disp" xdotool windowmove "$wid" 0 0 2>/dev/null && log "moved window $wid to 0,0" && break
      fi
      sleep 2
    done
  ) &
fi
# the game needs a moment to open its window; recording from the first second is fine (black until then)
size=$(DISPLAY="$disp" xdpyinfo 2>/dev/null | awk '/dimensions:/{print $2}' | head -1)
[ -n "$size" ] || size=1920x1080
log "display $disp ($size), auth $auth, ${RATE} fps"
date +%s%3N > "$OUT/rec-start.txt"
exec ffmpeg -hide_banner -loglevel warning -nostdin \
  -f x11grab -framerate "$RATE" -video_size "$size" -draw_mouse 0 -i "$disp" \
  -c:v libx264 -preset ultrafast -crf 30 -pix_fmt yuv420p -g "$((RATE * 2))" \
  -movflags +frag_keyframe+empty_moov \
  "$OUT/rec.mp4" >> "$OUT/rec.log" 2>&1
