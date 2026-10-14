#!/usr/bin/env python3
"""Eyes video: cut one short clip per screenshot out of the session recording (see tools/eyes_record.sh).

    python3 tools/eyes_cut.py <rec dir> <client latest.log> <out dir> [--before 4] [--after 6]

EyesDirector logs "[Eyes] clip-mark <name> <epoch ms>" right before every screenshot; rec-start.txt holds the epoch
ms at which ffmpeg started. Each clip is [mark - before, mark + after] seconds of the recording, re-encoded (clean
keyframes, small), named <name>.mp4. Also writes full_540p.mp4, the whole session at 960x540 for a scroll-through.
Prints one line per clip; exits 0 even when there is nothing to cut (the stills are still published).
"""
import argparse, os, re, subprocess, sys

MARK = re.compile(r"\[Eyes\] clip-mark (\S+) (\d+)")


def run(args):
    r = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    if r.returncode != 0:
        print("  ffmpeg:", r.stdout.strip().splitlines()[-1] if r.stdout.strip() else r.returncode)
    return r.returncode == 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("rec_dir")
    ap.add_argument("log")
    ap.add_argument("out")
    ap.add_argument("--before", type=float, default=4.0)
    ap.add_argument("--after", type=float, default=6.0)
    a = ap.parse_args()
    rec = os.path.join(a.rec_dir, "rec.mp4")
    start_file = os.path.join(a.rec_dir, "rec-start.txt")
    if not (os.path.exists(rec) and os.path.exists(start_file)) or os.path.getsize(rec) < 10_000:
        print("no recording to cut")
        return 0
    start_ms = int(open(start_file).read().strip())
    marks = []
    if os.path.exists(a.log):
        for line in open(a.log, errors="replace"):
            m = MARK.search(line)
            if m:
                marks.append((m.group(1), int(m.group(2))))
    os.makedirs(a.out, exist_ok=True)
    print(f"recording started at {start_ms}, {len(marks)} marks")
    for name, at in marks:
        t0 = (at - start_ms) / 1000.0 - a.before
        dur = a.before + a.after
        if t0 < 0:
            dur += t0
            t0 = 0.0
        dst = os.path.join(a.out, f"{name}.mp4")
        ok = run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-ss", f"{t0:.3f}", "-i", rec, "-t", f"{dur:.3f}",
                  "-c:v", "libx264", "-preset", "veryfast", "-crf", "28", "-pix_fmt", "yuv420p", "-movflags", "+faststart",
                  "-an", dst])
        size = os.path.getsize(dst) // 1024 if ok and os.path.exists(dst) else 0
        print(f"  {name}: {t0:.1f}s +{dur:.0f}s -> {size} KB")
    full = os.path.join(a.out, "full_540p.mp4")
    if run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", rec, "-vf", "scale=960:-2",
            "-c:v", "libx264", "-preset", "veryfast", "-crf", "30", "-pix_fmt", "yuv420p", "-movflags", "+faststart",
            "-an", full]):
        print(f"  full_540p.mp4: {os.path.getsize(full) // 1024} KB")
    return 0


if __name__ == "__main__":
    sys.exit(main())
