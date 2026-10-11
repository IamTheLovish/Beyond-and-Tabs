#!/usr/bin/env python3
"""Builds BeyondAndTabs-test.mrpack for remote testers (run by .github/workflows/modpack.yml).

Every third-party mod is resolved on Modrinth by the sha1 of lovish's own file (pack/instance_mods.json), so a tester's
launcher downloads byte-identical jars from cdn.modrinth.com; nothing is rehosted. e4mc (the open-source Open-to-LAN
internet tunnel) is added so lovish can host without port forwarding. Our own GPL jar goes in overrides/.

usage: build_pack.py <our jar> <out dir>
"""
import datetime
import hashlib
import json
import os
import subprocess
import sys
import time
import urllib.parse
import urllib.request
import zipfile

API = "https://api.modrinth.com/v2"
UA = "christiansaab-hue/Beyond-and-Tabs modpack-builder"
MC, FORGE = "1.20.1", "47.4.0"
OUR_JAR_NAME = "beyondandtabs-0.1.0.jar"
# client-side rendering/memory mods: a dedicated server does not need them (Prism treats both sides as required anyway)
CLIENT_ONLY = ("embeddium", "entityculling", "immediatelyfast", "modernfix", "ferritecore")
HERE = os.path.dirname(os.path.abspath(__file__))


def get_json(url):
    for attempt in range(5):
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.load(r)
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            if attempt == 4:
                raise
        except OSError:
            if attempt == 4:
                raise
        time.sleep(2 ** attempt)  # Modrinth rate-limits bursts; back off rather than fail the build


def download(url, dest):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=300) as r, open(dest, "wb") as f:
        while chunk := r.read(1 << 20):
            f.write(chunk)


def file_hashes(path):
    s1, s5 = hashlib.sha1(), hashlib.sha512()
    with open(path, "rb") as f:
        while chunk := f.read(1 << 20):
            s1.update(chunk)
            s5.update(chunk)
    return s1.hexdigest(), s5.hexdigest()


def env_for(filename):
    client_only = filename.lower().startswith(CLIENT_ONLY)
    return {"client": "required", "server": "optional" if client_only else "required"}


def index_entry(filename, sha1, sha512, size, url):
    return {"path": "mods/" + filename, "hashes": {"sha1": sha1, "sha512": sha512}, "env": env_for(filename),
            "downloads": [url], "fileSize": size}


def resolve_instance_mods():
    mods = json.load(open(os.path.join(HERE, "instance_mods.json")))
    entries, missing, project_ids = [], [], set()
    for m in mods:
        v = get_json(f"{API}/version_file/{m['sha1']}?algorithm=sha1")
        f = next((f for f in (v or {}).get("files", []) if f["hashes"].get("sha1") == m["sha1"]), None)
        if f is None:
            missing.append(m["file"])
            continue
        if f["hashes"].get("sha512") != m["sha512"] or f["size"] != m["size"]:
            sys.exit(f"::error::{m['file']}: Modrinth's sha512/size does not match lovish's file")
        project_ids.add(v["project_id"])
        # keep lovish's filename: the hashes prove it is the same bytes
        entries.append(index_entry(m["file"], m["sha1"], m["sha512"], m["size"], f["url"]))
        print(f"ok  {m['file']} -> {f['url']}")
    if missing:
        sys.exit("::error::not found on Modrinth (no other hosts are used): " + ", ".join(missing))
    return entries, project_ids


def newest_forge_version(project):
    q = urllib.parse.urlencode({"loaders": json.dumps(["forge"]), "game_versions": json.dumps([MC])})
    versions = get_json(f"{API}/project/{project}/version?{q}") or []
    if not versions:
        return None
    versions.sort(key=lambda v: v["date_published"], reverse=True)
    return next((v for v in versions if v["version_type"] == "release"), versions[0])


def resolve_e4mc(out_dir, have_projects):
    """Returns (index entries, sidecar records) for e4mc and its required Forge dependencies, or ([], []) if none."""
    entries, records, queue, seen = [], [], ["e4mc"], set(have_projects)
    while queue:
        proj = queue.pop(0)
        v = newest_forge_version(proj)
        if v is None:
            if proj == "e4mc":
                print(f"::warning::e4mc has no Forge {MC} build on Modrinth; pack built without it")
                return [], []
            sys.exit(f"::error::required dependency {proj} of e4mc has no Forge {MC} build")
        seen.add(v["project_id"])
        f = next((f for f in v["files"] if f.get("primary")), v["files"][0])
        dest = os.path.join(out_dir, f["filename"])
        download(f["url"], dest)
        sha1, sha512 = file_hashes(dest)
        if sha512 != f["hashes"]["sha512"] or sha1 != f["hashes"]["sha1"]:
            sys.exit(f"::error::{f['filename']}: downloaded bytes do not match Modrinth's hashes")
        size = os.path.getsize(dest)
        entries.append(index_entry(f["filename"], sha1, sha512, size, f["url"]))
        records.append({"file": f["filename"], "size": size, "sha512": sha512, "url": f["url"],
                        "project": proj, "version": v["version_number"]})
        print(f"ok  {proj} {v['version_number']} -> {f['url']}")
        for d in v.get("dependencies", []):
            if d["dependency_type"] == "required" and d.get("project_id") and d["project_id"] not in seen:
                seen.add(d["project_id"])
                queue.append(d["project_id"])
    return entries, records


def main():
    our_jar, out_dir = sys.argv[1], sys.argv[2]
    os.makedirs(out_dir, exist_ok=True)
    files, projects = resolve_instance_mods()
    e4mc_entries, e4mc_records = resolve_e4mc(out_dir, projects)
    files += e4mc_entries

    for f in files:  # the pack must only point at Modrinth's CDN
        for u in f["downloads"]:
            if urllib.parse.urlparse(u).hostname != "cdn.modrinth.com":
                sys.exit(f"::error::{f['path']}: download host is not cdn.modrinth.com: {u}")

    sha = os.environ.get("GITHUB_SHA", "local")[:7]
    index = {"formatVersion": 1, "game": "minecraft",
             "versionId": f"{datetime.date.today().isoformat()}-{sha}",
             "name": "Beyond and Tabs (test)",
             "summary": "Beyond and Tabs RTS test build with lovish's mod set and e4mc for internet play.",
             "files": files, "dependencies": {"minecraft": MC, "forge": FORGE}}
    text = json.dumps(index, indent=2)
    json.loads(text)

    pack = os.path.join(out_dir, "BeyondAndTabs-test.mrpack")
    with zipfile.ZipFile(pack, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("modrinth.index.json", text)
        z.write(our_jar, "overrides/mods/" + OUR_JAR_NAME)
    with zipfile.ZipFile(pack) as z:
        if z.testzip() is not None:
            sys.exit("::error::mrpack failed its zip test")

    with open(os.path.join(out_dir, "e4mc.sha512.txt"), "w") as f:
        if not e4mc_records:
            f.write(f"e4mc has no Forge {MC} build on Modrinth; it is not in this pack.\n")
        for r in e4mc_records:
            f.write(f"file:    {r['file']}\nproject: {r['project']} {r['version']}\nsize:    {r['size']} bytes\n"
                    f"sha512:  {r['sha512']}\nsource:  {r['url']}\n\n")
    print(f"built {pack}: {len(files)} Modrinth files + {OUR_JAR_NAME}")


if __name__ == "__main__":
    main()
