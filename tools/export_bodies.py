#!/usr/bin/env python3
"""Re-export every generated unit body through tools/dsl_to_game.py with the arguments it ships with.

The exports used to be one-off command lines nobody wrote down (the team palette names in particular). This table is
the record: a change to dsl_to_game.py or tools/materials.py that should touch every body (normal strength, mips,
painting) is applied with one command, and `--check` proves a refactor changes nothing (every Java file and texture
pixel-identical to the committed ones; verified Oct 15 at 612f4b9).

    python3 tools/export_bodies.py            # write all bodies
    python3 tools/export_bodies.py --check    # export, compare with git HEAD, restore nothing - report only
    python3 tools/export_bodies.py halberdier # one body (a substring of its texture name)
"""
import io
import os
import subprocess
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

# (concept model, Java class, texture name, team palette names)
BODIES = [
    ("design/models/sunforged_paladin_radiant_winged.json", "RadiantPaladinModel", "radiant_paladin_unit", "tabard,tabard_dark"),
    ("design/models/sunforged_field_medic_v2.json", "FieldMedicModel", "field_medic_unit", "tabard,tabard_dark"),
    ("design/models/sunforged_halberdier_v2.json", "HalberdierModel", "halberdier_unit", "tabard,tabard_dark"),
    ("design/models/sunforged_crossbow_levy_v2.json", "CrossbowLevyModel", "crossbow_levy_unit", "tabard,tabard_dark"),
    ("design/models/sunforged_squire_runner_v2.json", "SquireRunnerModel", "squire_runner_unit", "tabard,tabard_dark"),
]
TEX = "bar/src/main/resources/assets/reignofnether/textures/entities/"
JAVA = "bar/src/main/java/com/solegendary/reignofnether/unit/modelling/models/gen/"
SUFFIXES = ("", "_team", "_glow", "_n", "_s", "_team_n", "_team_s")


def same_pixels(path):
    """True when the file on disk draws the same pixels as the committed one (PNG bytes may differ by encoder)."""
    import numpy as np
    from PIL import Image
    old = subprocess.run(["git", "show", "HEAD:" + path], cwd=ROOT, capture_output=True).stdout
    if not old:
        return False
    a = np.asarray(Image.open(io.BytesIO(old)).convert("RGBA"))
    b = np.asarray(Image.open(os.path.join(ROOT, path)).convert("RGBA"))
    return a.shape == b.shape and bool((a == b).all())


def main(argv):
    check = "--check" in argv
    only = [a for a in argv if not a.startswith("--")]
    changed = []
    for model, cls, texture, team in BODIES:
        if only and not any(o in texture for o in only):
            continue
        r = subprocess.run([sys.executable, "tools/dsl_to_game.py", model, "--class", cls, "--texture", texture,
                            "--team", team], cwd=ROOT, capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stdout + r.stderr)
            raise SystemExit("export failed: " + texture)
        print(r.stdout.strip().splitlines()[0])
        if check:
            for s in SUFFIXES:
                if not same_pixels(TEX + texture + s + ".png"):
                    changed.append(texture + s + ".png")
            if subprocess.run(["git", "diff", "--quiet", "--", JAVA + cls + ".java"], cwd=ROOT).returncode != 0:
                changed.append(cls + ".java")
    if check:
        print("changed vs HEAD: " + (", ".join(changed) if changed else "nothing"))
        return 1 if changed else 0
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
