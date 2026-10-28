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
CLIENT_ONLY = ("embeddium", "entityculling", "immediatelyfast", "modernfix", "ferritecore", "sound-physics")
# extra Modrinth projects added on lovish's go-ahead (Oct 21 01:49 "you can add everything else"), resolved like e4mc:
# the newest Forge 1.20.1 release, downloaded and checked against Modrinth's own hashes, plus its required deps.
#   spark                     GPL-3.0  profiler: find what makes big battles lag (/spark profiler)
#   macaws-bridges            ARR      bridges (water crossing, Lane C C3) - used as a dependency, assets never copied
#   macaws-paths-and-pavings  MIT      paths and pavings for bases (Lane B's block vocabulary)
#   macaws-lights-and-lamps   ARR      lamps and lanterns for bases (Lane B)
#   sound-physics-remastered  GPL-3.0  reverb / muffling, client-only (first-person mode)
#   packet-fixer             MIT      fixes oversize-packet / NBT / timeout kicks in big multiplayer matches (both sides)
#   sound-culling            MIT      rate-limits overlapping sounds: 500 units are a wall of footsteps (client)
#   particle-core            MIT      particle culling + spawn caps, the valve for every other particle mod (client)
#   fallingleavesforge       (REMOVED Oct 28: particular-reforged below makes the leaves, per leaf type; two mods
#                            making leaves would double the particles over the RTS view)
# (Oct 21, lovish "add what you think is useful"; research claude/mods-research-oct21b). The four are PINNED to the
# versions that were vetted: PINNED_VERSIONS below. A newer release only gets in by editing the pin, because a
# fresh Sound Culling (3 days old) or Particle Core could change under testers between two pack builds.
# Canary (server tick) is NOT here: it changes vanilla code our mod also hooks, so it waits for a CI run of every
# GameTest with it loaded. Dynamic Lights Reforged is CurseForge-only, and this pack uses Modrinth only.
#   oculus                   LGPL-3   shader loader (Iris for Forge), client-only: lovish Oct 22 "the game needs better
#                            graphics, everything still feels flat" - the PBR maps (_n/_s) only show under a shader
#                            pack. Pinned to 1.8.0, Embeddium 0.3.31's pair (the eyes shader lane's exact setup).
#                            Complementary Reimagined (the shader pack itself) is added by build_packwiz.py.
#   The landscape mods (lovish, Oct 28; pack dependencies only, never bundled in our jar):
#   nsHXyrFh                  MIT      This Rocks! (Forge) (slug this-rocks-forge): rocks, sticks, pinecones, seashells,
#                            starfish, geysers. Adds blocks, so the server needs it. Listed by project id.
#   particular-reforged       LGPL-3   hand-made ambient particles: falling leaves per leaf type, ripples, fireflies. Client.
#   immersive-weathering      LGPL-3   moss, cracks, rust, frost, soil types, icicles. Blocks, so both sides; needs Moonlight
#                            (already in lovish's instance). Its block-tick weathering is switched off in PACK_CONFIGS.
#   better-biome-blend        Unlicense smooth biome colour edges (grass, water, foliage). Client, works with Embeddium.
#   The ambient mods (lovish ~06:30 Oct 28, client only; their spawns are capped low in PACK_CONFIGS):
#   cosy-critters             MIT      Cosy Critters & Creepy Crawlies: birds on forest leaves, moths at lights, spiders.
#   light-dust                LGPL-3   dust floating in light beams. Spawns around the player entity only.
#   NOT added: Flutter, Windblown and RLFoliage (Ported) are CurseForge-only (no Modrinth project, checked by
#   slug, by author and by search), and this builder resolves from Modrinth only; no CurseForge file is hand-added.
EXTRA_PROJECTS = ("spark", "macaws-bridges", "macaws-paths-and-pavings", "macaws-lights-and-lamps",
                  "sound-physics-remastered", "packet-fixer", "sound-culling", "particle-core",
                  "oculus", "nsHXyrFh", "particular-reforged", "immersive-weathering", "better-biome-blend",
                  "cosy-critters", "light-dust")
# project slug -> exact Modrinth version_number; a pinned project that has no such Forge 1.20.1 version fails the build
PINNED_VERSIONS = {"packet-fixer": "3.3.2", "sound-culling": "2.1.0", "particle-core": "0.3.3+1.20.1+forge",
                   "oculus": "1.20.1-1.8.0",
                   # Oct 28, each the newest Forge 1.20.1 release on Modrinth that day
                   "nsHXyrFh": "1.5.1", "particular-reforged": "1.5.7", "immersive-weathering": "1.20.1-2.0.5",
                   "better-biome-blend": "1.20.1-1.4.0-forge",
                   "cosy-critters": "v0.3.3+1.20.1-forge", "light-dust": "2.1.3"}
# top-level extras that only the client runs (a dedicated server may leave them out, like CLIENT_ONLY below)
CLIENT_ONLY_PROJECTS = ("sound-physics-remastered", "sound-culling", "particle-core", "oculus",
                        "particular-reforged", "better-biome-blend", "cosy-critters", "light-dust")
# files the Mac pack drops: Embeddium and ImmediatelyFast misbehave on macOS drivers, and an addon that needs
# Embeddium (Embeddium Extra = rubidium-extra) would crash the Mac pack if it slipped through (research Oct 21).
# One list for macpack.yml, build_packwiz.py and publish-pack's guard.
# Oculus needs Embeddium, so it goes too (and with it the shader pack: build_packwiz.py)
MAC_SKIP = ("embeddium", "immediatelyfast", "rubidium-extra", "oculus")
HERE = os.path.dirname(os.path.abspath(__file__))
# Config files the pack ships under config/ (file name -> text). packwiz writes them once as "preserve" files (a
# tester's own edits survive updates), the .mrpack carries them as overrides, and the eyes workflow copies them into
# its run. Every key below was read from the mod's own source (1.20.1 branch), not guessed.
#
# Immersive Weathering (AstralOrdana/Immersive-Weathering, CommonConfigs.java; Moonlight writes
# config/immersive_weathering-common.toml): a match must not change a map or a building. Static looks stay (its blocks,
# its world-gen moss / ivy / icicle patches); everything that changes blocks over time goes off:
#   general.block_growths          master switch of every data-driven growth (weeds, frost, sand seeping, grass flowers,
#                                  sapling, sandstone erosion, fire/lightning changes ...), checked in tickBlock
#   mossy_blocks.enabled           moss spreading over mossable blocks (Mossable.shouldWeather)
#   cracked_blocks.enabled         cracks spreading (Crackable.shouldWeather)
#   rusting.rusting / rusting_rate the rust code reads only the rate (Rustable.tryWeather), so rate 0 is what stops it
#   leaf_piles.spawn_entity_from_decay   decayed leaves leave a leaf pile: 0
#   charring.fire_chars_wood       fire turning wood into charred wood: 0
#   misc.grass_over_mycelium / mycelium_over_grass   soil swaps on random ticks
# Not configurable in the mod (noted in the PR): snow layers melt in rain on a random tick (SnowLayerBlockMixin), and
# an icicle removes itself by day outside icy biomes.
# The client file: Particular makes the falling leaves, so Immersive Weathering's own leaf particles go off.
# Ambient density for 4v4 armies (the RTS camera is the player entity, ~30 blocks up: orthoview), capped low:
#   cosycritters.json (PigCart/cosy-critters, ConfigData.java, Gson; missing keys keep their defaults):
#     maxBirds 50 -> 12, maxMoths 8 -> 3, maxSpiders 16 -> 3; birds spawn within 128 blocks of the player on leaves
#   lightdust/main.toml (Swiss8191/Light-Dust 1.20.1/Forge, LightDustConfig.java, branch of Jul 24 = 2.0.x; the pinned
#     2.1.3 may differ - Forge drops keys it does not know): [Spawning Rules] ambientBlockCap 12 -> 4, radius 10 -> 8
#   Particular Reforged: no config shipped (its 1.5.x config could not be read from source); particle-core, already in
#     the pack, is the valve for its particle counts.
# This Rocks! (Mrbysco/ThisRocksForge, RocksConfig.java; config/rocks-common.toml): geysers lift players off the ground.
PACK_CONFIGS = {
    "immersive_weathering-common.toml": """\
# Beyond and Tabs tester pack: the map and the buildings stay as built (no weathering on block ticks).
# Written once; delete this file to get the mod's defaults back.
[general]
	block_growths = false

[mossy_blocks]
	enabled = false

[cracked_blocks]
	enabled = false

[rusting]
	rusting = false
	rusting_rate = 0.0
	rust_streaking = false

[leaf_piles]
	spawn_entity_from_decay = 0.0

[charring]
	fire_chars_wood = 0.0

[misc]
	grass_over_mycelium = false
	mycelium_over_grass = false
""",
    "immersive_weathering-client.toml": """\
# Beyond and Tabs tester pack: Particular Reforged makes the falling leaves.
[general]
	falling_leaf_particles = false
""",
    "cosycritters.json": """\
{
  "configVersion": 1,
  "maxBirds": 12,
  "maxMoths": 3,
  "maxSpiders": 3
}
""",
    "lightdust/main.toml": """\
# Beyond and Tabs tester pack: fewer dust motes (a big army is a lot to look at already).
["Spawning Rules"]
	ambientRadius = 8
	ambientHardCapRadius = 10
	ambientBlockCap = 4
""",
    "rocks-common.toml": """\
# Beyond and Tabs tester pack: a geyser must not lift the RTS camera or a possessed hero off the ground.
[General]
	geyserLevitation = false
""",
}
# our mods.toml makes these mandatory (Alex's Mobs since Oct 10; the building-block mods since lovish's Oct 20
# decision, faction structures use their blocks): a pack without one would stop at Forge's "missing mods" screen
REQUIRED_FILES = ("alexsmobs-", "citadel-", "mcw-roofs-", "mcw-mcwwindows-", "mcw-mcwfences-", "chipped-forge-",
                  "athena-forge-", "resourcefullib-forge-")


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


def env_for(filename, client_only=False):
    client_only = client_only or filename.lower().startswith(CLIENT_ONLY)
    return {"client": "required", "server": "optional" if client_only else "required"}


def index_entry(filename, sha1, sha512, size, url, client_only=False):
    return {"path": "mods/" + filename, "hashes": {"sha1": sha1, "sha512": sha512}, "env": env_for(filename, client_only),
            "downloads": [url], "fileSize": size}


def resolve_instance_mods():
    mods = json.load(open(os.path.join(HERE, "instance_mods.json")))
    absent = [p for p in REQUIRED_FILES if not any(m["file"].startswith(p) for m in mods)]
    if absent:
        sys.exit("::error::pack/instance_mods.json lacks mods our mods.toml requires: " + ", ".join(absent))
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
    if project in PINNED_VERSIONS:
        want = PINNED_VERSIONS[project]
        # exact number, or the number followed by a build suffix ("2.1.0" -> "2.1.0+1.20.1-forge"); never a longer number
        pinned = [v for v in versions if v["version_number"] == want
                  or (v["version_number"].startswith(want) and v["version_number"][len(want)] in "+-_ ")]
        pinned.sort(key=lambda v: v["date_published"], reverse=True)
        if not pinned:
            sys.exit(f"::error::{project}: pinned version {want} has no Forge {MC} file on Modrinth "
                     f"(has: {', '.join(v['version_number'] for v in versions) or 'none'})")
        return pinned[0]
    if not versions:
        return None
    versions.sort(key=lambda v: v["date_published"], reverse=True)
    return next((v for v in versions if v["version_type"] == "release"), versions[0])


def resolve_e4mc(out_dir, have_projects):
    """Returns (index entries, sidecar records) for e4mc, the EXTRA_PROJECTS and their required Forge dependencies."""
    top = ["e4mc", *EXTRA_PROJECTS]
    entries, records, queue, seen, added = [], [], list(top), set(have_projects), set()
    while queue:
        proj = queue.pop(0)
        v = newest_forge_version(proj)
        if v is None:
            if proj in top:
                print(f"::warning::{proj} has no Forge {MC} build on Modrinth; pack built without it")
                continue
            sys.exit(f"::error::required dependency {proj} has no Forge {MC} build")
        if v["project_id"] in have_projects or v["project_id"] in added:
            continue   # already in lovish's instance, or already added by an earlier project
        seen.add(v["project_id"])
        added.add(v["project_id"])
        f = next((f for f in v["files"] if f.get("primary")), v["files"][0])
        dest = os.path.join(out_dir, f["filename"])
        download(f["url"], dest)
        sha1, sha512 = file_hashes(dest)
        if sha512 != f["hashes"]["sha512"] or sha1 != f["hashes"]["sha1"]:
            sys.exit(f"::error::{f['filename']}: downloaded bytes do not match Modrinth's hashes")
        size = os.path.getsize(dest)
        entries.append(index_entry(f["filename"], sha1, sha512, size, f["url"], proj in CLIENT_ONLY_PROJECTS))
        records.append({"file": f["filename"], "size": size, "sha512": sha512, "url": f["url"],
                        "project": proj, "version": v["version_number"]})
        print(f"ok  {proj} {v['version_number']} -> {f['url']}")
        for d in v.get("dependencies", []):
            if d["dependency_type"] == "required" and d.get("project_id") and d["project_id"] not in seen:
                seen.add(d["project_id"])
                queue.append(d["project_id"])
    return entries, records


def write_mrpack(path, index_text, our_jar):
    """The .mrpack: the index, our jar and the shipped configs as overrides."""
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("modrinth.index.json", index_text)
        z.write(our_jar, "overrides/mods/" + OUR_JAR_NAME)
        for name, cfg in PACK_CONFIGS.items():
            z.writestr("overrides/config/" + name, cfg)


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
    write_mrpack(pack, text, our_jar)
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
