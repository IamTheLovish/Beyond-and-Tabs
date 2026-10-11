#!/usr/bin/env python3
"""
Build-bar icon audit (lovish's clips: the Drydock showed the missing-texture "?", the Horde's Fortress a plain
nether-brick face). For every `this.icon = ...` in bar/src/main/java/.../building/buildings/**:

  * a mod-namespace icon must exist under src/main/resources/assets/reignofnether/;
  * a vanilla icon must be a textures/... .png path and never a waxed_ block (those share the unwaxed texture);
  * none of the plain block faces the faction labs / towers / capitols used to ship with may come back
    (PLACEHOLDERS) - tools/gen_hud_icons.py draws a real icon for each.

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
}

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
                    if icon in PLACEHOLDERS:
                        bad.append(f"{rel}: placeholder block face {icon} - draw one in tools/gen_hud_icons.py")
                elif not os.path.exists(os.path.join(ASSETS, icon)):
                    bad.append(f"{rel}: icon {icon} is not in the mod's assets")
    for b in bad:
        print(b)
    print(f"{seen} building icons checked, {len(bad)} problems")
    sys.exit(1 if bad or seen < 40 else 0)


if __name__ == "__main__":
    main()
