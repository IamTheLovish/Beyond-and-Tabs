#!/usr/bin/env python3
"""The technical artist's ledger (claude/art-theory.md section 11): what a generated body costs to draw, per unit and
per army, before it ships - and what the cheap wins would save.

Minecraft 1.20.1 draws entities immediate-mode: every visible cube of every visible ModelPart is transformed on the
CPU and written to a vertex buffer each frame - 6 faces x 4 vertices = 24 vertices per cube per PASS. Our generated
bodies are drawn in three full passes today: the base texture, TeamColourLayer (the whole model again with the
team texture) and the glow EyesLayer (the whole model again with the glow texture). Parts hidden with
visible = false (the "lod" detail when zoomed out, an unequipped weapon) cost nothing.

    python3 tools/body_budget.py design/models/sunforged_*_v2.json design/models/sunforged_paladin_radiant_winged.json
        [--team tabard,tabard_dark] [--army 40]

Columns: cubes / ModelParts (each a push/translate/rotate/pop) / detail cubes (hidden past zoom 36) / team cubes /
glow cubes / upgrade cubes; vertices per frame for one unit at full detail and zoomed out, with today's three full
passes and with the passes split (team and glow passes drawing only the parts that carry team or glow texels); and
the army total for --army units on screen.
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import dsl_to_game as d2g  # noqa: E402
import render_concept  # noqa: E402

VERTS_PER_CUBE = 24


def count_parts(p):
    return 1 + sum(count_parts(c) for c in p.children)


def ledger(path, team):
    m = render_concept.load_model(path)
    m["_path"] = os.path.abspath(path)
    emissive = set(m.get("emissive", []))
    cubes = [(b, c) for b in m["bones"] for c in b.get("cubes", [])]
    n = len(cubes)
    detail = sum(1 for b, c in cubes if not (c.get("upgrade") or b.get("upgrade")) and d2g.is_detail(c, emissive))
    teamc = sum(1 for b, c in cubes if c["color"] in team)
    glowc = sum(1 for b, c in cubes if c["color"] in emissive)
    upg = sum(1 for b, c in cubes if c.get("upgrade") or b.get("upgrade"))
    _java, _imgs, info = d2g.build(m, "Budget", list(team), "budget", d2g.DENSITY)
    parts = count_parts(info["root"]) - 1
    base_full = n - upg                    # upgrade parts hidden until researched (worst case below adds them back)
    base_far = base_full - detail
    passes = 1 + (1 if teamc else 0) + (1 if glowc else 0)
    today_full = base_full * passes * VERTS_PER_CUBE
    today_far = base_far * passes * VERTS_PER_CUBE
    split_full = (base_full + teamc + glowc) * VERTS_PER_CUBE
    split_far = (base_far + teamc + glowc) * VERTS_PER_CUBE
    return dict(name=os.path.basename(path).replace(".json", ""), cubes=n, parts=parts, detail=detail, team=teamc,
                glow=glowc, upg=upg, passes=passes, today_full=today_full, today_far=today_far, split_full=split_full,
                split_far=split_far)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("models", nargs="+")
    ap.add_argument("--team", default="tabard,tabard_dark")
    ap.add_argument("--army", type=int, default=40, help="units of each body on screen for the army line")
    a = ap.parse_args()
    team = set(t for t in a.team.split(",") if t)
    rows = [ledger(p, team) for p in a.models]
    hdr = ("body", "cubes", "parts", "detail", "team", "glow", "upg", "passes", "verts full", "verts far",
           "split full", "split far")
    print("%-34s %6s %6s %6s %5s %5s %4s %6s %10s %10s %10s %10s" % hdr)
    for r in rows:
        print("%-34s %6d %6d %6d %5d %5d %4d %6d %10d %10d %10d %10d" % (
            r["name"], r["cubes"], r["parts"], r["detail"], r["team"], r["glow"], r["upg"], r["passes"],
            r["today_full"], r["today_far"], r["split_full"], r["split_far"]))
    tf = sum(r["today_far"] for r in rows) * a.army
    sf = sum(r["split_far"] for r in rows) * a.army
    print("\narmy: %d of each body on screen at far zoom -> %.2f M vertices/frame today, %.2f M with split passes "
          "(-%d%%)" % (a.army, tf / 1e6, sf / 1e6, round(100 * (1 - sf / tf)) if tf else 0))


if __name__ == "__main__":
    main()
