#!/usr/bin/env python3
"""The mods bar/build.gradle pins from Modrinth's maven are the exact files in lovish's instance.

bar/build.gradle names each third-party mod by its Modrinth VERSION ID (maven.modrinth:<project>:<version id>);
pack/instance_mods.json lists lovish's own files by sha1 (the tester pack resolves those). The GameTest server and
runClient load the build.gradle files, players load the pack's - if the two drift, CI tests a different block set
(or a different Chipped) from the one players see. For every pin this asks Modrinth for the version's files and
fails unless one of them is a file in instance_mods.json; on a mismatch it names the version id that IS lovish's file
(looked up by its sha1), so the fix is one edit.

Runs in CI (job `build`): only the runners reach api.modrinth.com, the dev sandbox does not. A network failure is a
warning, not a red build (Gradle's own resolution still fails loudly on an unknown id).

usage: check_dep_pins.py
"""
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
GRADLE = os.path.join(HERE, "..", "bar", "build.gradle")
INSTANCE = os.path.join(HERE, "..", "pack", "instance_mods.json")
API = "https://api.modrinth.com/v2"
UA = "IamTheLovish/Beyond-and-Tabs dep-pin-check"
PIN_RE = re.compile(r"""['"]maven\.modrinth:([a-z0-9_-]+):([A-Za-z0-9]+)['"]""")


def get_json(url):
    for attempt in range(4):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.load(r)
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            if attempt == 3:
                raise
        except OSError:
            if attempt == 3:
                raise
        time.sleep(2 ** attempt)


def main():
    pins = PIN_RE.findall(open(GRADLE).read())
    if not pins:
        sys.exit("::error::no maven.modrinth pins found in bar/build.gradle - the pattern or the file changed")
    instance = {m["sha1"]: m for m in json.load(open(INSTANCE))}
    bad = []
    try:
        for project, vid in pins:
            v = get_json(f"{API}/version/{vid}")
            if v is None:
                bad.append(f"{project}:{vid} is not a Modrinth version id")
                continue
            ours = [instance[f["hashes"]["sha1"]] for f in v["files"] if f["hashes"].get("sha1") in instance]
            if ours:
                print(f"ok  {project}:{vid} = {v['version_number']} = {ours[0]['file']}")
                continue
            # which of lovish's files belongs to this project, and what is its version id?
            hint = ""
            for m in instance.values():
                w = get_json(f"{API}/version_file/{m['sha1']}?algorithm=sha1")
                if w and w["project_id"] == v["project_id"]:
                    hint = f" - lovish's {m['file']} is version id {w['id']} ({w['version_number']})"
                    break
            bad.append(f"{project}:{vid} ({v['version_number']}) is not the file in pack/instance_mods.json{hint}")
    except OSError as e:
        print(f"::warning::Modrinth unreachable, dependency pins not checked: {e}")
        return
    for b in bad:
        print(f"::error file=bar/build.gradle::{b}")
    if bad:
        sys.exit(1)
    print(f"{len(pins)} pins match lovish's instance")


if __name__ == "__main__":
    main()
