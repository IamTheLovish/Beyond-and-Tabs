#!/usr/bin/env python3
"""Audit of every unit skin in textures/entities: size, generator, faction, PBR maps, who draws it - and a contact
sheet of the ones still at 64x64 (nearest-neighbour, for the record). Writes design/skins/AUDIT.md and
design/skins/audit_64x_contact.png.

    python3 tools/skin_audit.py
"""
import os
import re
import sys

from PIL import Image, ImageDraw

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
ENT = os.path.join(ROOT, "bar/src/main/resources/assets/reignofnether/textures/entities")
JAVA = os.path.join(ROOT, "bar/src/main/java")
OUT = os.path.join(ROOT, "design/skins")

GENERATORS = ("gen_hd_skins", "gen_verdant_skins", "gen_tide_skins", "gen_storm_skins", "gen_brass_skins",
              "gen_t2t3_skins", "gen_hero_skins", "gen_skins", "gen_squire", "gen_levy", "gen_paladin", "gen_medic",
              "gen_halberdier", "gen_angel_statue", "gen_dragon", "dsl_to_game")

FACTION_HINTS = {
    "sunforged": ("villager", "vindicator", "pillager", "evoker", "royal_architect", "field_mason", "dawn_paladin",
                  "halberdier", "crossbow_levy", "squire_runner", "radiant_paladin", "field_medic", "sun_colossus",
                  "royal_guard", "enchanter", "windcaller", "white_angel", "oriflamme", "captain_banner",
                  "totem_of"),
    "gravebound": ("zombie", "husk", "skeleton", "embalmer", "bonesetter", "bone_dragon", "gravecaller", "bogged",
                   "necromancer", "wraith", "ossuary", "soulfire"),
    "horde": ("grunt", "brute", "headhunter", "bonewright", "wither_skeleton", "horde_warchief", "war_mammoth",
              "marauder", "piglin_merchant", "wildfire", "armoured_hoglin", "pioneer", "gorehorn"),
    "verdant": ("seedshaper", "elder_druid", "bloom_priestess", "leafblade", "hive_keeper", "thornbow",
                "shade_ranger", "moonwell_bearer", "rootwarden", "thornheart", "treant", "world_tree", "grizzly",
                "mossback"),
    "tidewrought": ("shipwright", "harbourmaster", "cutlass", "reef_guard", "bombard", "harpooner", "flak_swivel",
                    "kraken", "tide_priest", "sky_corsair", "leviathan", "mudskipper", "mortar_galley", "salvager",
                    "corsair_captain", "shellback"),
    "stormcallers": ("geomancer", "ley_master", "blade_dancer", "jade_guard", "fire_adept", "frost_adept",
                     "wind_monk", "gravity_monk", "mirror_mage", "seer", "rocket_battery", "ink_scribe",
                     "paper_crane", "thunder_dragon", "celestial_dragon", "phoenix", "lantern_fortress",
                     "earthshaker", "jade_mender", "storm_witch", "jade_qilin"),
    "brassworks": ("tinker", "engineer_captain", "rifle_squad", "sapper", "flak_cart", "shield_projector",
                   "mortar_crawler", "tesla_walker", "iron_warden", "steam_runner", "gyro", "steam_juggernaut",
                   "drill_burrower", "repair_crawler", "clockwork", "arch_engineer", "iron_leviathan",
                   "steam_hauler", "wrecker"),
}


def faction_of(name):
    for fac, hints in FACTION_HINTS.items():
        if any(h in name for h in hints):
            return fac
    return "?"


def generator_of(name, gen_text):
    for g in GENERATORS:
        t = gen_text.get(g, "")
        if re.search(r'"%s"' % re.escape(name), t) or re.search(r"'%s'" % re.escape(name), t) \
                or re.search(r'"%s_unit' % re.escape(name), t) or ("--texture " + name) in t:
            return g
    return "-"


def main():
    gen_text = {}
    for g in GENERATORS:
        p = os.path.join(ROOT, "tools", g + ".py")
        if os.path.exists(p):
            with open(p, encoding="utf-8") as f:
                gen_text[g] = f.read()
    java = ""
    for dp, _, fs in os.walk(JAVA):
        for fn in fs:
            if fn.endswith(".java"):
                with open(os.path.join(dp, fn), encoding="utf-8", errors="replace") as f:
                    java += f.read()
    files = sorted(f for f in os.listdir(ENT) if f.endswith(".png"))
    suffixes = ("_team", "_n", "_s", "_glow", "_team_n", "_team_s", "_overlay", "_armored", "_tail")
    bases = []
    for f in files:
        n = f[:-4]
        if any(n.endswith(s) for s in suffixes):
            continue
        bases.append(n)
    rows = []
    for n in bases:
        im = Image.open(os.path.join(ENT, n + ".png"))
        w, h = im.size
        has = lambda s: os.path.exists(os.path.join(ENT, n + s + ".png"))
        gen = generator_of(n.replace("_unit", ""), gen_text) if n.endswith("_unit") else generator_of(n, gen_text)
        if gen == "-" and n.endswith("_unit"):
            gen = generator_of(n, gen_text)
        used = ("textures/entities/" + n + ".png") in java or ('"' + n + '"') in java or ("\"" + n.replace("_unit", "") + "\"") in java
        rows.append(dict(name=n, w=w, h=h, gen=gen, fac=faction_of(n), team=has("_team"), n=has("_n"), s=has("_s"),
                         glow=has("_glow"), used=used))
    n64 = [r for r in rows if r["w"] <= 64 and r["h"] <= 64]
    small = [r for r in rows if max(r["w"], r["h"]) < 512]
    hd = [r for r in rows if r["n"] and r["s"]]
    by_fac = {}
    for r in rows:
        by_fac.setdefault(r["fac"], []).append(r)
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, "AUDIT.md"), "w", encoding="utf-8") as f:
        f.write("# Unit skin audit (claude/unit-skins-1, Oct 21 2026)\n\n")
        f.write("Every base texture in `textures/entities` (companions `_team`, `_n`, `_s`, `_glow` folded into columns). "
                "Generated by `tools/skin_audit.py`.\n\n")
        f.write(f"- base skins: **{len(rows)}**\n- at most 64x64: **{len(n64)}**\n- below 512 on the long side "
                f"(no 8x atlas): **{len(small)}**\n- with LabPBR `_n` + `_s`: **{len(hd)}** "
                f"({', '.join(r['name'] for r in hd)})\n- with a `_team` mask: **{sum(r['team'] for r in rows)}**\n\n")
        f.write("## By faction (base skins / at 64x64 / with _n+_s)\n\n| faction | skins | 64x64 | PBR |\n|---|---|---|---|\n")
        for fac in sorted(by_fac):
            rs = by_fac[fac]
            f.write(f"| {fac} | {len(rs)} | {sum(1 for r in rs if r['w'] <= 64 and r['h'] <= 64)} | "
                    f"{sum(1 for r in rs if r['n'] and r['s'])} |\n")
        f.write("\n## Every skin\n\n| skin | size | faction | generator | team | _n | _s | glow | referenced in Java |\n|---|---|---|---|---|---|---|---|---|\n")
        for r in sorted(rows, key=lambda r: (r["fac"], r["name"])):
            y = lambda b: "yes" if b else "-"
            f.write(f"| {r['name']} | {r['w']}x{r['h']} | {r['fac']} | {r['gen']} | {y(r['team'])} | {y(r['n'])} | "
                    f"{y(r['s'])} | {y(r['glow'])} | {y(r['used'])} |\n")
        f.write("\nContact sheet of the 64x64 skins (nearest-neighbour 3x): `audit_64x_contact.png`.\n")
    # contact sheet of the 64x64 ones
    cols, cell, scale = 10, 64 * 3 + 8, 3
    rows64 = sorted(n64, key=lambda r: (r["fac"], r["name"]))
    nrow = (len(rows64) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * cell, nrow * (cell + 14)), (28, 30, 34))
    d = ImageDraw.Draw(sheet)
    for k, r in enumerate(rows64):
        im = Image.open(os.path.join(ENT, r["name"] + ".png")).convert("RGBA")
        im = im.resize((im.width * scale, im.height * scale), Image.NEAREST)
        x, y = (k % cols) * cell, (k // cols) * (cell + 14)
        bg = Image.new("RGBA", im.size, (90, 90, 96, 255))
        sheet.paste(Image.alpha_composite(bg, im).convert("RGB"), (x + 4, y + 14))
        d.text((x + 4, y + 1), r["name"][:30], fill=(220, 220, 220))
    sheet.save(os.path.join(OUT, "audit_64x_contact.png"))
    print(f"{len(rows)} skins, {len(n64)} at <=64x64, {len(hd)} with _n+_s; by faction:",
          {k: len(v) for k, v in by_fac.items()})


if __name__ == "__main__":
    main()
