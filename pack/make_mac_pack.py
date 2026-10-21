#!/usr/bin/env python3
"""BeyondAndTabs-test.mrpack -> BeyondAndTabs-test-mac.mrpack: the same pack without build_pack.MAC_SKIP.

Used by .github/workflows/macpack.yml (on the published pack) and by modpack.yml's dry run (on the pack it just built),
so the strip list lives in ONE place (build_pack.MAC_SKIP) - a name missing from it once let rubidium-extra through.

usage: make_mac_pack.py <in .mrpack> <out .mrpack>
"""
import json
import os
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_pack  # noqa: E402


def main():
    src, dest = sys.argv[1:3]
    with zipfile.ZipFile(src) as zin:
        d = json.loads(zin.read("modrinth.index.json"))
        before = len(d["files"])
        gone = [f["path"] for f in d["files"] if any(k in f["path"].lower() for k in build_pack.MAC_SKIP)]
        d["files"] = [f for f in d["files"] if f["path"] not in gone]
        d["name"] = "Beyond and Tabs (test, Mac-safe)"
        d["versionId"] = d["versionId"] + "-mac"
        d["summary"] = (d.get("summary", "") + " Mac-safe: without Embeddium and ImmediatelyFast.").strip()
        with zipfile.ZipFile(dest, "w", zipfile.ZIP_DEFLATED) as zout:
            zout.writestr("modrinth.index.json", json.dumps(d, indent=1))
            for item in zin.infolist():   # overrides/ (our jar) unchanged
                if item.filename != "modrinth.index.json":
                    zout.writestr(item, zin.read(item.filename))
    left = [f["path"] for f in d["files"] if any(k in f["path"].lower() for k in build_pack.MAC_SKIP)]
    if left or not gone:
        sys.exit(f"::error::mac pack strip failed: removed {gone}, still has {left}")
    print(f"mac pack: {before} -> {len(d['files'])} files; stripped {', '.join(os.path.basename(g) for g in gone)}")


if __name__ == "__main__":
    main()
