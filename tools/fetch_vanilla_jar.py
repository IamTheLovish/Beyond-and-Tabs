#!/usr/bin/env python3
"""Downloads the official Minecraft client jar of one version from Mojang's version manifest and checks its sha1 -
for CI runs that read the game's own data (tools/face_census.py: blockstates and block models). Run it where
piston-meta.mojang.com is reachable (a GitHub runner; the agent workspace's proxy refuses it).

    python3 tools/fetch_vanilla_jar.py 1.20.1 OUT.jar
Prints one JSON line (version, size, sha1 prefix). Exit status 1 on a missing version or a sha1 mismatch.
"""
import hashlib
import json
import sys
import urllib.request

MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
UA = {"User-Agent": "Beyond-and-Tabs tools (game data for CI checks)"}


def get(url, timeout=60):
    return urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=timeout).read()


def main():
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    version, out = sys.argv[1], sys.argv[2]
    man = json.loads(get(MANIFEST))
    ver = next((v for v in man["versions"] if v["id"] == version), None)
    if ver is None:
        raise SystemExit("no Minecraft version %s in Mojang's manifest" % version)
    client = json.loads(get(ver["url"]))["downloads"]["client"]
    data = get(client["url"], timeout=600)
    if hashlib.sha1(data).hexdigest() != client["sha1"]:
        raise SystemExit("sha1 mismatch for the %s client jar" % version)
    with open(out, "wb") as f:
        f.write(data)
    print(json.dumps({"version": version, "size_mb": round(len(data) / 1e6, 1), "sha1": client["sha1"][:16] + "..."}))


if __name__ == "__main__":
    main()
