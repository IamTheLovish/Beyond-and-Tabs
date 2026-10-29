#!/usr/bin/env python3
"""Builds the auto-updating tester pack (packwiz format) - run by .github/workflows/publish-pack.yml.

Testers import a small Prism instance ONCE; its pre-launch command runs packwiz-installer-bootstrap against
<variant>/pack.toml in the public download repo, so every launch pulls whatever we published last.

The third-party mods are the same set as the .mrpack tester pack (build_pack.py: lovish's instance_mods.json resolved
on Modrinth by sha1, plus e4mc and its dependencies) and download from cdn.modrinth.com. Our own jar and our
realistic resource pack download from the download repo's release assets. Every file carries a sha512.
No shader pack and no shader loader ship (Oct 29: Complementary Reimagined's author refused us permission; our own
shader pack will add them back). tools/test_pack_no_complementary.py fails if a Complementary file ever gets in.

Two variants: windows/ (everything) and mac/ (without build_pack.MAC_SKIP: Embeddium, ImmediatelyFast, Embeddium Extra - like macpack.yml).

usage: build_packwiz.py <our jar> <resource pack zip> <jar url> <resource pack url> <version> <out dir>
"""
import hashlib
import json
import os
import re
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import build_pack  # noqa: E402  (same folder: Modrinth resolution, MC/FORGE versions, CLIENT_ONLY)

MAC_SKIP = build_pack.MAC_SKIP
RP_NAME = "BeyondAndTabs-Realistic.zip"
OUR_JAR_NAME = build_pack.OUR_JAR_NAME
MC_DATA_VERSION = 3465  # 1.20.1, so the seeded options.txt is not run through the data fixer


def q(s):
    """A TOML basic string (JSON escaping is valid TOML for these ASCII values)."""
    return json.dumps(s)


def sha512_of(path):
    h = hashlib.sha512()
    with open(path, "rb") as f:
        while chunk := f.read(1 << 20):
            h.update(chunk)
    return h.hexdigest()


def sha256_text(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def slug(filename):
    s = re.sub(r"\.(jar|zip)$", "", filename.lower())
    return re.sub(r"[^a-z0-9]+", "-", s).strip("-")


def metafile(name, filename, side, url, sha512):
    return (f"name = {q(name)}\nfilename = {q(filename)}\nside = {q(side)}\n\n"
            f"[download]\nurl = {q(url)}\nhash-format = \"sha512\"\nhash = {q(sha512)}\n")


def write(root, rel, text):
    path = os.path.join(root, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        f.write(text)
    return text


def build_variant(root, entries, our, rp, version, skip):
    """entries: [(filename, side, url, sha512)] for third-party mods; our / rp: (filename, url, sha512)."""
    files = []  # (rel path, text, metafile?, preserve?)
    for filename, side, url, sha in entries:
        if any(k in filename.lower() for k in skip):
            continue
        rel = f"mods/{slug(filename)}.pw.toml"
        files.append((rel, metafile(filename, filename, side, url, sha), True, False))
    files.append(("mods/beyondandtabs.pw.toml", metafile("Beyond and Tabs", our[0], "both", our[1], our[2]), True, False))
    files.append((f"resourcepacks/{slug(rp[0])}.pw.toml",
                  metafile("Beyond and Tabs - Realistic", rp[0], "client", rp[1], rp[2]), True, False))
    # options.txt is only written when the tester has none yet (preserve): it switches our resource pack on once.
    options = (f"version:{MC_DATA_VERSION}\n"
               f"resourcePacks:[\"vanilla\",\"file/{rp[0]}\"]\n")
    files.append(("options.txt", options, False, True))

    if len({f[0] for f in files}) != len(files):
        sys.exit("::error::two pack files map to the same path - rename one")

    index = 'hash-format = "sha256"\n'
    for rel, text, is_meta, preserve in sorted(files):
        write(root, rel, text)
        index += f"\n[[files]]\nfile = {q(rel)}\nhash = {q(sha256_text(text))}\n"
        if is_meta:
            index += "metafile = true\n"
        if preserve:
            index += "preserve = true\n"
    write(root, "index.toml", index)

    pack = (f"name = \"Beyond and Tabs (test)\"\nauthor = \"Beyond and Tabs\"\nversion = {q(version)}\n"
            f"pack-format = \"packwiz:1.1.0\"\n\n"
            f"[index]\nfile = \"index.toml\"\nhash-format = \"sha256\"\nhash = {q(sha256_text(index))}\n\n"
            f"[versions]\nminecraft = {q(build_pack.MC)}\nforge = {q(build_pack.FORGE)}\n")
    write(root, "pack.toml", pack)
    return len(files)


def main():
    our_jar, rp_zip, jar_url, rp_url, version, out_dir = sys.argv[1:7]
    os.makedirs(out_dir, exist_ok=True)

    mods, projects = build_pack.resolve_instance_mods()
    with tempfile.TemporaryDirectory() as tmp:
        e4mc, _ = build_pack.resolve_e4mc(tmp, projects)
    entries = []
    for e in mods + e4mc:
        filename = os.path.basename(e["path"])
        side = "client" if e["env"]["server"] == "optional" else "both"   # build_pack.env_for decided it
        entries.append((filename, side, e["downloads"][0], e["hashes"]["sha512"]))
    for _, _, url, _ in entries:
        if not url.startswith("https://cdn.modrinth.com/"):
            sys.exit(f"::error::third-party download not on cdn.modrinth.com: {url}")

    our = (os.path.basename(jar_url), jar_url, sha512_of(our_jar))
    rp = (RP_NAME, rp_url, sha512_of(rp_zip))
    if not our[0].endswith(".jar"):
        sys.exit("::error::the jar url must end in .jar")

    summary = {"version": version, "jar": our[0], "jar_sha512": our[2], "variants": {}}
    for variant, skip in (("windows", ()), ("mac", MAC_SKIP)):
        n = build_variant(os.path.join(out_dir, variant), entries, our, rp, version, skip)
        summary["variants"][variant] = n
        print(f"{variant}: {n} files")
    with open(os.path.join(out_dir, "summary.json"), "w") as f:
        json.dump(summary, f, indent=1)
    print(json.dumps(summary, indent=1))


if __name__ == "__main__":
    main()
