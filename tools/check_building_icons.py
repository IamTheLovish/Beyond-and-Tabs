#!/usr/bin/env python3
"""
Build-bar icon audit (lovish's clips: the Drydock showed the missing-texture "?", the Horde's Fortress a plain
nether-brick face). For every `this.icon = ...` in bar/src/main/java/.../building/buildings/**:

  * a mod-namespace icon must exist under src/main/resources/assets/reignofnether/;
  * a vanilla icon must be a textures/... .png path and never a waxed_ block (those share the unwaxed texture);
  * none of the plain block faces the faction labs / towers / capitols used to ship with may come back
    (PLACEHOLDERS) - tools/gen_hud_icons.py draws a real icon for each.

Production items (lovish's clip: the Lotus Academy panel showed rows of blank pink squares) are covered too, for
every faction: every literal mod texture under unit/units/**, research/** and building/production/** must exist,
every `super(itemName, "<entity>", "<mobhead>", ...)` of the shared StormProd / TideProd / VerdantT2Prod bodies must
have its textures/mobheads/<mobhead>.png, and every registered unit (EntityRegistrar "<x>_unit") its
textures/mobheads/<x>.png - the HUD's selection strip, queue and control groups all blit that path.

Exit 1 with the offenders listed. No Minecraft needed, so it runs in the safety-protocol step before the build.
"""
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "bar", "src", "main")
JAVA = os.path.join(ROOT, "java", "com", "solegendary", "reignofnether", "building", "buildings")
ASSETS = os.path.join(ROOT, "resources", "assets", "reignofnether")

# vanilla block faces that are too anonymous for a build button; each one now has a drawn icon
PLACEHOLDERS = {
    "textures/block/cobblestone.png", "textures/block/stone_bricks.png", "textures/block/amethyst_block.png",
    "textures/block/polished_granite.png", "textures/block/fletching_table_front.png",
    "textures/block/mossy_stone_bricks.png", "textures/block/spawner.png", "textures/block/reinforced_deepslate_side.png",
    "textures/block/deepslate_bricks.png", "textures/block/deepslate_tiles.png",
    "textures/block/red_glazed_terracotta.png", "textures/block/chiseled_nether_bricks.png",
    "textures/block/chiseled_polished_blackstone.png", "textures/block/obsidian.png",
    "textures/block/cherry_planks.png",   # the Lotus Academy's "pink block"
}
# any plain planks / wool / concrete face is as anonymous as the list above
PLAIN_FACE = re.compile(r"textures/block/\w+_(planks|wool|concrete)\.png$")
JAVA_ROOT = os.path.join(ROOT, "java", "com", "solegendary", "reignofnether")
PROD_DIRS = [os.path.join(JAVA_ROOT, d) for d in ("unit/units", "research", "building/production")]
LITERAL_RE = re.compile(r'fromNamespaceAndPath\(\s*ReignOfNether\.MOD_ID\s*,\s*"(textures/[^"]+\.png)"\s*\)')
MOBHEAD_SUPER_RE = re.compile(r'super\(itemName,\s*"[a-z0-9_]+",\s*"([a-z0-9_]+)"')
UNIT_ID_RE = re.compile(r'"([a-z0-9_]+)_unit"')

# `this.icon = ...` and the `static final ResourceLocation ICON = ...` some buildings keep; a trailing `+` means the
# path is a prefix with a variant appended (the wind generator's per-faction sails)
ICON_RE = re.compile(r'(?i:icon)\s*=\s*ResourceLocation\.fromNamespaceAndPath\(\s*(ReignOfNether\.MOD_ID|"minecraft")\s*,\s*"([^"]+)"(\s*\+)?')


def main():
    bad = []
    seen = 0
    for dirpath, _, files in os.walk(JAVA):
        for f in files:
            if not f.endswith(".java"):
                continue
            path = os.path.join(dirpath, f)
            rel = os.path.relpath(path, JAVA)
            with open(path, encoding="utf-8") as fh:
                src = fh.read()
            for ns, icon, plus in ICON_RE.findall(src):
                seen += 1
                if plus:
                    d, prefix = os.path.split(os.path.join(ASSETS, icon))
                    if not any(n.startswith(prefix) for n in (os.listdir(d) if os.path.isdir(d) else [])):
                        bad.append(f"{rel}: no icon starts with {icon}")
                    continue
                if ns == '"minecraft"':
                    if not icon.startswith("textures/") or not icon.endswith(".png"):
                        bad.append(f"{rel}: vanilla icon {icon} is not a texture path")
                    if "waxed_" in icon:
                        bad.append(f"{rel}: vanilla icon {icon} - waxed blocks have no texture of their own")
                    if icon in PLACEHOLDERS or PLAIN_FACE.search(icon):
                        bad.append(f"{rel}: placeholder block face {icon} - draw one in tools/gen_hud_icons.py")
                elif not os.path.exists(os.path.join(ASSETS, icon)):
                    bad.append(f"{rel}: icon {icon} is not in the mod's assets")
    prod = check_production_icons(bad)
    for b in bad:
        print(b)
    print(f"{seen} building icons and {prod} production / unit icons checked, {len(bad)} problems")
    sys.exit(1 if bad or seen < 40 or prod < 60 else 0)


def check_production_icons(bad):
    """Production queue / selection icons for every faction; returns how many were checked."""
    checked = 0
    for top in PROD_DIRS:
        for dirpath, _, files in os.walk(top):
            for f in files:
                if not f.endswith(".java"):
                    continue
                path = os.path.join(dirpath, f)
                rel = os.path.relpath(path, JAVA_ROOT)
                src = open(path, encoding="utf-8").read()
                for tex in LITERAL_RE.findall(src):
                    checked += 1
                    if not os.path.exists(os.path.join(ASSETS, tex)):
                        bad.append(f"{rel}: {tex} is not in the mod's assets")
                for head in MOBHEAD_SUPER_RE.findall(src):
                    checked += 1
                    if not os.path.exists(os.path.join(ASSETS, "textures", "mobheads", head + ".png")):
                        bad.append(f"{rel}: production icon textures/mobheads/{head}.png is missing")
    reg = open(os.path.join(JAVA_ROOT, "registrars", "EntityRegistrar.java"), encoding="utf-8").read()
    for unit in sorted(set(UNIT_ID_RE.findall(reg))):
        checked += 1
        if not os.path.exists(os.path.join(ASSETS, "textures", "mobheads", unit + ".png")):
            bad.append(f"unit {unit}_unit: textures/mobheads/{unit}.png is missing (selection / queue / control-group icon)")
    return checked


if __name__ == "__main__":
    main()
