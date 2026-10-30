#!/usr/bin/env python3
"""
Pre-push gate: catches the errors CI died on (broken class structure, bad braces, missing imports we own).
Runs javac on the mod sources without the Minecraft classpath, then ignores the errors that are only
missing-dependency noise. A parse/structure error fails. Usage: check_java.py <files...>
       check_java.py --tracked   (every tracked source CI checks; for Windows, where that list is too long for argv)
"""
import re, subprocess, sys, tempfile

TRACKED = ["bar/src/main/java/*.java", "mod/src/main/java/*.java", "engine/src/*.java"]  # the same list as build.yml
files = sys.argv[1:]
if files == ["--tracked"]:
    files = subprocess.run(["git", "ls-files", "--"] + TRACKED, capture_output=True, text=True, check=True).stdout.splitlines()
if not files:
    # default: every .java changed vs the last pushed main (or HEAD), plus untracked ones - never a silent no-op
    base = subprocess.run(["git", "merge-base", "HEAD", "origin/main"], capture_output=True, text=True).stdout.strip() or "HEAD"
    out = subprocess.run(["git", "diff", "--name-only", base, "--"], capture_output=True, text=True).stdout
    out += subprocess.run(["git", "ls-files", "--others", "--exclude-standard"], capture_output=True, text=True).stdout
    import os
    files = sorted({f for f in out.split() if f.endswith(".java") and os.path.exists(f)})
    if not files:
        print("no changed .java files to check")
        sys.exit(0)
    print("checking changed files vs", base[:8] + ":", ", ".join(os.path.basename(f) for f in files))
with tempfile.TemporaryDirectory() as d:
    # the file list goes in an @argfile: 2000+ paths are past Windows' 32 KB command line
    argfile = d + "/sources.txt"
    with open(argfile, "w", encoding="utf-8") as fh:
        fh.write("\n".join('"' + f.replace("\\", "/") + '"' for f in files))
    r = subprocess.run(["javac", "--release", "17", "-nowarn", "-Xmaxerrs", "5000", "-proc:none", "-encoding", "UTF-8", "-d", d + "/out",
                        "@" + argfile], capture_output=True, text=True)
noise = re.compile(r"(cannot find symbol|package [\w.]+ (does not exist|is not visible)|"
                   r"method does not override or implement|符号)|location:|symbol:|  (required|found):|\^|^import|"
                   r"error: cannot access|lambda expression not expected here|method reference not expected here|incompatible types|static import only from classes|name clash: |non-static variable super|class file for .* not found|"
                   # two constructors "match" when one's parameter type (FriendlyByteBuf) is off the classpath
                   r"reference to \w+ is ambiguous|"
                   # `T extends LivingEntity` with LivingEntity off the classpath makes `entity instanceof Unit u` look total
                   r"unconditional patterns in instanceof")
bad = []
lines = r.stderr.splitlines()
for i, l in enumerate(lines):
    m = re.match(r"(.+\.java):(\d+): error: (.*)", l)
    if m and not noise.search(m.group(3)):
        bad.append(l)
    elif l.startswith("error:"):  # no file location: a missing file, a bad @argfile or option - javac checked nothing
        bad.append(l)
# imports of our own classes must name a file that exists (`com.solegendary.reignofnether.player.Relationship` cost
# two red builds on Oct 14: javac without the classpath reports it as noise). Static members / nested classes:
# some prefix of the import must be a file.
import os
root = "bar/src/main/java/"
for f in files:
    if not f.startswith(root):
        continue
    for n, line in enumerate(open(f, encoding="utf-8"), 1):
        m = re.match(r"\s*import\s+(static\s+)?(com\.solegendary\.reignofnether\.[\w.]+)\s*;", line)
        if not m:
            continue
        parts = m.group(2).split(".")
        if parts[-1] == "*":
            parts = parts[:-1]
        if not any(os.path.exists(root + "/".join(parts[:k]) + ".java") for k in range(len(parts), 1, -1)):
            bad.append(f"{f}:{n}: error: import of a class we do not have: {m.group(2)}")
for l in bad[:25]:
    print(l)
print(f"{len(files)} files checked, {len(bad)} structural errors")
sys.exit(1 if bad else 0)
