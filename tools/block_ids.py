#!/usr/bin/env python3
"""Checks the building-block id dump and prints its counts (CI job `bar`, before it is pushed to branch `block-ids`).

The GameTest `building_block_ids_dump_to_json` (gametest/BuildingBlockModGameTests) writes run-gametest/block_ids/:
one <namespace>.json per building-block mod and counts.json. Later building rounds read them from the `block-ids`
branch (every id + blockstate property + allowed values, without lovish's PC or a game boot), so a broken or empty
dump must never replace a good one: this fails unless every required namespace is there with blocks, and every
block's states are consistent.

  <namespace>.json  {"namespace": "mcwroofs", "mod_version": "2.3.2", "count": 2,
                     "blocks": {"mcwroofs:x": {"properties": {"facing": ["north", ...], ...},
                                               "default": {"facing": "north", ...},
                                               "states": 8, "block_entity": false}, ...}}
  counts.json       {"minecraft": "1.20.1", "total": 1234, "namespaces": {"mcwroofs": 2, ...}}

usage: block_ids.py <dump dir>
"""
import json
import math
import os
import sys

# the building-block mods (mods.toml makes them mandatory): Macaw's (all mcw*), Chipped, FramedBlocks, Domum Ornamentum,
# Decorative Blocks. The GameTest dumps every namespace starting with "mcw" plus the others named here.
REQUIRED_NAMESPACES = ("mcwroofs", "mcwwindows", "mcwfences", "mcwdoors", "mcwbridges", "mcwstairs", "mcwlights",
                       "mcwpaths", "chipped", "framedblocks", "domum_ornamentum", "decorative_blocks")


def load(path, problems):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError) as e:
        problems.append(f"{os.path.basename(path)}: unreadable ({e})")
        return None


def check_block(ns, bid, b, problems):
    if not bid.startswith(ns + ":"):
        problems.append(f"{ns}.json: {bid} is not in namespace {ns}")
    props, default = b.get("properties"), b.get("default")
    if not isinstance(props, dict) or not isinstance(default, dict):
        problems.append(f"{bid}: properties and default must be objects")
        return
    for name, values in props.items():
        if not values or not isinstance(values, list) or len(set(values)) != len(values) \
                or not all(isinstance(v, str) for v in values):
            problems.append(f"{bid}: property {name} needs a list of distinct string values, got {values}")
    if set(default) != set(props):
        problems.append(f"{bid}: default names {sorted(default)}, properties are {sorted(props)}")
    for name, v in default.items():
        if name in props and v not in props[name]:
            problems.append(f"{bid}: default {name}={v} is not one of {props[name]}")
    want = math.prod(len(v) for v in props.values() if isinstance(v, list))
    if b.get("states") != want:
        problems.append(f"{bid}: states {b.get('states')} != {want} (every combination of the properties)")
    if not isinstance(b.get("block_entity"), bool):
        problems.append(f"{bid}: block_entity must be true or false")


def validate(root):
    """[problem] for the dump in root; empty = good."""
    problems = []
    counts = load(os.path.join(root, "counts.json"), problems)
    if counts is None:
        return problems
    by_ns = counts.get("namespaces") or {}
    if counts.get("total") != sum(by_ns.values()):
        problems.append(f"counts.json: total {counts.get('total')} != sum of namespaces {sum(by_ns.values())}")
    for ns in sorted(set(REQUIRED_NAMESPACES) | set(by_ns)):
        if not by_ns.get(ns):
            problems.append(f"{ns}: no blocks in counts.json (mod missing or not loaded)")
        doc = load(os.path.join(root, ns + ".json"), problems)
        if doc is None:
            continue
        blocks = doc.get("blocks") or {}
        if doc.get("namespace") != ns:
            problems.append(f"{ns}.json: namespace is {doc.get('namespace')}")
        if not (doc.get("count") == len(blocks) == by_ns.get(ns)):
            problems.append(f"{ns}.json: count {doc.get('count')}, {len(blocks)} blocks, counts.json {by_ns.get(ns)}")
        for bid, b in blocks.items():
            check_block(ns, bid, b, problems)
    return problems


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    root = sys.argv[1]
    problems = validate(root)
    for p in problems[:40]:
        print(f"::error::[Block ids] {p}")
    if problems:
        sys.exit(f"block id dump in {root}: {len(problems)} problems")
    with open(os.path.join(root, "counts.json"), encoding="utf-8") as f:
        counts = json.load(f)
    line = ", ".join(f"{ns} {n}" for ns, n in sorted(counts["namespaces"].items()))
    print(f"::notice::[Block ids] {counts['total']} blocks: {line}")


if __name__ == "__main__":
    main()
