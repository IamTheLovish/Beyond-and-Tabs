#!/usr/bin/env python3
"""Our own realistic block pack - original, procedural, 128x + LabPBR 1.3 maps (lovish, Oct 15: "make the 32x even
better"; claude/realistic-look.md: every player gets OUR pack, never a modified Patrix).

Every texture is built from a HEIGHT field (periodic noise, Voronoi stones and pebbles, boards, bark, rings), and the
three maps follow from it so they always agree: the albedo (palette + the height's cavities and ridges), the normal
map (<name>_n.png: RG normal with green along +v, down the texture - the convention tools/materials.py and
Complementary use; B = ambient occlusion from cavities, 255 = none; A = height for parallax, >= 1) and the specular
map (<name>_s.png: R smoothness, G F0 (10 = 4% dielectric), B porosity 0-64 - wet in rain, A 255 = no emission).

DETAIL SCALE: from the RTS camera (zoom 30 = ~36 screen px per block at 1080p) one texel of a 128x texture is ~0.3
px and mipmaps average it away, so the detail lives at 1/64-1/16 block (2-8 texels), not at single texels - the
first round put most of its grain at 1/128 and the sand read flat (pack_study's detail_by_scale, Oct 15).

CALIBRATION (the pack-study numbers, Oct 15 - run 38710336774 on the free Patrix 32x; numbers only): each material
has a target albedo luma mean / spread / saturation and a mean normal tilt. The albedo is fitted to the luma targets
after it is painted, and the normal strength is solved so the mean tilt lands on the target - so "does it match a
mature realistic pack" is measured, not guessed (tools/pack_study.py measures the output the same way). Where the
reference has nothing (height, AO, porosity: the free 32x ships none) we go further.

Deterministic (fixed seeds). Writes resourcepacks/BeyondAndTabs-Realistic/ (a normal resource pack folder) and, with
--preview DIR, lit 2x2 previews and a contact sheet for review.
    python3 tools/gen_blocks.py [--res 128] [--preview DIR] [--jobs N]
Each texture is its own job on its own seed (one process per CPU by default; --jobs 1 runs them in order), so the
output is the same pixel for pixel however many run at once.
"""
import argparse
import json
import math
import os

import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import materials  # noqa: E402  - the ONE LabPBR encoder, shared with the units (tools/dsl_to_game.py)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK_DIR = os.path.join(ROOT, "resourcepacks", "BeyondAndTabs-Realistic")
BLOCK_DIR = os.path.join(PACK_DIR, "assets", "minecraft", "textures", "block")
MODEL_DIR = os.path.join(PACK_DIR, "assets", "minecraft", "models", "block")

# target = the reference pack's group means (luma, luma std, saturation, normal tilt deg, smoothness p50 0-255)
TARGETS = {
    # grass top std: the reference's 0.110 read as noise from the RTS camera (on-screen 0.124 vs vanilla 0.072, run
    # 38735013028); 0.085 gives ~0.057 on screen at the default zoom (vanilla 0.048, round 3 0.070) - a little more
    # texture than vanilla, no camouflage (sim: nearest-mipmap sampling at 36 px per block matched the eyes stills)
    # round 19: std 0.085 -> 0.072 - the plain game's grass still read as speckle from the RTS camera
    "grass_block_top":  dict(luma=0.56, std=0.072, sat=0.00, tilt=21.0, smooth=24, pore=40),
    # round 24 (eyes close-ups, terrain sampler run 39838783742): dirt and the grass side's dirt umber, not salmon
    # (sat 0.38 / 0.36 -> 0.32 / 0.30, a browner base in m_dirt_r24); stone's smoothness 40 -> 22 and gravel's 50 -> 24
    # (the floor patches took a blue sky sheen at the 30-degree view: rgb .27/.28/.32 in full sun) and gravel's tilt
    # 26 -> 18 (bubble wrap); stone std 0.060 -> 0.045 (m_stone_r24's grain); oak planks sat 0.46 -> 0.40, smooth
    # 70 -> 45 (orange, satin); oak bark sat 0.38 -> 0.30 (grey-brown) and tilt 27 -> 22 (m_oak_bark_r24)
    "grass_block_side": dict(luma=0.34, std=0.080, sat=0.30, tilt=22.0, smooth=26, pore=48, soften=0.8, lf=0.3),
    "grass_block_side_overlay": dict(luma=0.56, std=0.090, sat=0.00, tilt=18.0, smooth=24, pore=40),
    "dirt":             dict(luma=0.31, std=0.070, sat=0.32, tilt=16.0, smooth=26, pore=56, soften=0.8, lf=0.3, ao=0.4),
    # round 18: tilt 16 -> 10 and less low-frequency boost (lf): in game with shaders the relief read as crumpled
    # foil (eyes structure run 39536435197) - the grain is for the light to catch, not lumps
    "stone":            dict(luma=0.52, std=0.045, sat=0.04, lf=0.2, ao=0.5, tilt=6.0, smooth=22, pore=12, soften=1.0),
    "cobblestone":      dict(luma=0.41, std=0.090, sat=0.08, tilt=20.5, smooth=32, pore=16),
    "sand":             dict(luma=0.67, std=0.030, sat=0.40, tilt=11.0, smooth=40, pore=60),
    "gravel":           dict(luma=0.46, std=0.070, sat=0.07, tilt=16.0, smooth=24, pore=30, soften=0.8),
    "oak_planks":       dict(luma=0.46, std=0.055, sat=0.40, tilt=8.5, smooth=45, pore=24, soften=0.7, lf=0.3),
    "oak_log":          dict(luma=0.33, std=0.060, sat=0.34, tilt=22.0, smooth=40, pore=36, soften=1.2),
    "oak_log_top":      dict(luma=0.44, std=0.073, sat=0.48, tilt=12.0, smooth=40, pore=30),
    # round 3 (Oct 15): what the RTS view shows most after grass and water (the eyes ground census); new keys go at
    # the END - the seeds follow this order, so the older textures stay byte-identical. Targets = the single-block
    # numbers of pack-study run 38732322940 (polished andesite is near flat and polished; smooth stone is rough)
    "stone_bricks":     dict(luma=0.47, std=0.081, sat=0.03, tilt=18.4, smooth=14, pore=12),
    "polished_andesite": dict(luma=0.49, std=0.035, sat=0.03, tilt=3.0, smooth=119, pore=4),
    "smooth_stone":     dict(luma=0.54, std=0.045, sat=0.04, tilt=17.7, smooth=16, pore=10),   # r21: cool mid grey
    "smooth_stone_slab_side": dict(luma=0.54, std=0.045, sat=0.04, tilt=17.7, smooth=16, pore=10),
    "spruce_planks":    dict(luma=0.36, std=0.060, sat=0.30, tilt=7.7, smooth=53, pore=24),
    # leaves (round 5): the trees seen from above. Greyscale (the foliage colour tints them), alpha holes for 'fancy'
    # leaves; reference leaves group: luma 0.60, std 0.093, tilt 18, smoothness 60, SSS on 67% of texels. pore here is
    # the _s blue CODE: 65-255 = subsurface scattering (light through the leaf under a shader pack)
    # round 18: leaves' tilt 18 -> 12 (every leaf edge drew a dark rim under shaders)
    "oak_leaves":       dict(luma=0.55, std=0.090, sat=0.00, ao=0.35, tilt=9.0, smooth=60, pore=180),
    "birch_leaves":     dict(luma=0.58, std=0.090, sat=0.00, ao=0.35, tilt=9.0, smooth=60, pore=180),
    "spruce_leaves":    dict(luma=0.48, std=0.085, sat=0.00, ao=0.35, tilt=9.0, smooth=50, pore=150),
    "acacia_leaves":    dict(luma=0.55, std=0.090, sat=0.00, ao=0.35, tilt=9.0, smooth=60, pore=180),
    "jungle_leaves":    dict(luma=0.52, std=0.095, sat=0.00, ao=0.35, tilt=9.0, smooth=70, pore=170),
    "dark_oak_leaves":  dict(luma=0.50, std=0.090, sat=0.00, ao=0.35, tilt=9.0, smooth=60, pore=170),
    # round 7: the metal resource patches and the capitol's gold, barrels and hay (pack-study 38740563625, single
    # blocks); f0 230 / 231 = LabPBR 1.3's iron / gold, 255 = a metal with its albedo as F0 (238-254 are undefined)
    "raw_iron_block":   dict(luma=0.53, std=0.080, sat=0.39, tilt=26.0, smooth=76, pore=10),
    # gold: the reference's 23 deg comes from an engraved pattern; a cast plate with tilt 18 came out as lumpy cheese.
    # Luma between the reference (0.63, which leans on a shader's reflections) and vanilla (0.81): without shaders
    # - most players - a 0.63 gold reads brown (pack-study-vanilla 38740868045)
    "gold_block":       dict(luma=0.72, std=0.050, sat=0.70, tilt=8.0, smooth=150, pore=0, f0=231),
    "barrel_side":      dict(luma=0.30, std=0.070, sat=0.46, tilt=17.2, smooth=57, pore=20),
    "barrel_top":       dict(luma=0.30, std=0.070, sat=0.46, tilt=17.2, smooth=57, pore=20),
    "barrel_bottom":    dict(luma=0.30, std=0.070, sat=0.46, tilt=17.2, smooth=57, pore=20),
    "hay_block_side":   dict(luma=0.54, std=0.085, sat=0.51, tilt=21.4, smooth=20, pore=50),
    "hay_block_top":    dict(luma=0.54, std=0.085, sat=0.51, tilt=21.4, smooth=20, pore=50),
    # round 8: every wood's bark and end grain (pack-study 38743610740, single blocks; the reference's log tops carry
    # little relief, 8.4 deg - ours keep 12 like the oak top)
    # round 19: the darkest barks a little lighter and with more spread - under shaders dark oak and jungle read as a
    # flat dark slab (reviewer, eyes run 39548166615); the reference's numbers were for a lit flat view
    "spruce_log":       dict(luma=0.19, std=0.052, sat=0.46, tilt=26.2, smooth=40, pore=36),
    "spruce_log_top":   dict(luma=0.31, std=0.075, sat=0.49, tilt=12.0, smooth=40, pore=30),
    "birch_log":        dict(luma=0.85, std=0.140, sat=0.06, tilt=26.0, smooth=50, pore=30),
    "birch_log_top":    dict(luma=0.67, std=0.110, sat=0.35, tilt=12.0, smooth=40, pore=30),
    "acacia_log":       dict(luma=0.35, std=0.063, sat=0.19, tilt=26.1, smooth=40, pore=36),
    "acacia_log_top":   dict(luma=0.41, std=0.053, sat=0.63, tilt=12.0, smooth=40, pore=30),
    "jungle_log":       dict(luma=0.29, std=0.070, sat=0.57, tilt=31.2, smooth=40, pore=36),
    "jungle_log_top":   dict(luma=0.43, std=0.100, sat=0.55, tilt=12.0, smooth=40, pore=30),
    "dark_oak_log":     dict(luma=0.23, std=0.058, sat=0.49, tilt=31.3, smooth=40, pore=36),
    "dark_oak_log_top": dict(luma=0.21, std=0.039, sat=0.61, tilt=12.0, smooth=40, pore=30),
    # round 11: the other factions' buildings (structure census) - single-block numbers from pack-study 38760372803
    # (reference) beside 38760380796 (vanilla); std a little under the reference where rts_view says busy
    "polished_blackstone":        dict(luma=0.19, std=0.030, sat=0.10, tilt=12.0, smooth=60, pore=4),
    "polished_blackstone_bricks": dict(luma=0.19, std=0.049, sat=0.10, tilt=18.0, smooth=40, pore=8),
    "deepslate_bricks":           dict(luma=0.30, std=0.057, sat=0.02, tilt=15.0, smooth=15, pore=10),
    "deepslate_tiles":            dict(luma=0.21, std=0.060, sat=0.01, tilt=18.0, smooth=30, pore=10),
    "deepslate":                  dict(luma=0.19, std=0.045, sat=0.05, tilt=21.0, smooth=12, pore=10),
    "deepslate_top":              dict(luma=0.19, std=0.040, sat=0.05, tilt=18.0, smooth=15, pore=10),
    "polished_deepslate":         dict(luma=0.31, std=0.030, sat=0.02, tilt=4.5, smooth=90, pore=4),
    "cobbled_deepslate":          dict(luma=0.24, std=0.075, sat=0.03, tilt=21.0, smooth=23, pore=16),
    "cut_copper":                 dict(luma=0.50, std=0.058, sat=0.55, tilt=12.0, smooth=104, pore=0),
    "oxidized_cut_copper":        dict(luma=0.55, std=0.065, sat=0.45, tilt=12.0, smooth=12, pore=20),
    "azalea_leaves":              dict(luma=0.43, std=0.080, sat=0.65, tilt=16.5, smooth=70, pore=180),
    "flowering_azalea_leaves":    dict(luma=0.42, std=0.085, sat=0.62, tilt=16.5, smooth=60, pore=180),
    "netherrack":                 dict(luma=0.19, std=0.055, sat=0.58, tilt=30.0, smooth=60, pore=30),
    "nether_bricks":              dict(luma=0.15, std=0.050, sat=0.35, tilt=18.0, smooth=50, pore=12),
    "prismarine_bricks":          dict(luma=0.58, std=0.075, sat=0.33, tilt=12.0, smooth=44, pore=10),
    "dark_prismarine":            dict(luma=0.31, std=0.070, sat=0.36, tilt=15.6, smooth=78, pore=10),
    "moss_block":                 dict(luma=0.40, std=0.060, sat=0.60, tilt=14.0, smooth=41, pore=60),
    "mossy_stone_bricks":         dict(luma=0.44, std=0.070, sat=0.20, tilt=18.6, smooth=25, pore=30),
    # round 12: single-block numbers from pack-study 38761293030 (reference; vanilla 38761302665 beside it); std
    # trimmed where the reference is busy from the RTS camera (podzol 0.120, coarse dirt 0.128, diorite 0.116)
    "sandstone": dict(luma=0.57, std=0.060, sat=0.54, tilt=12.6, smooth=21, pore=50),
    "sandstone_top": dict(luma=0.83, std=0.035, sat=0.26, tilt=6.7, smooth=27, pore=50),
    "sandstone_bottom": dict(luma=0.76, std=0.056, sat=0.29, tilt=7.8, smooth=21, pore=50),
    "red_terracotta": dict(luma=0.27, std=0.015, sat=0.55, tilt=4.0, smooth=29, pore=30),
    "terracotta": dict(luma=0.33, std=0.015, sat=0.46, tilt=4.0, smooth=29, pore=30),
    "polished_basalt_side": dict(luma=0.36, std=0.077, sat=0.05, tilt=25.0, smooth=39, pore=8),
    "polished_basalt_top": dict(luma=0.33, std=0.075,   # r21: in game 132 vs basalt 114 - read as bone beside it
    sat=0.02, tilt=20.0, smooth=26, pore=8),
    "bone_block_side": dict(luma=0.86, std=0.050, sat=0.11, tilt=18.0, smooth=87, pore=20),
    "bone_block_top": dict(luma=0.80, std=0.070, sat=0.13, tilt=16.2, smooth=73, pore=20),
    "quartz_pillar": dict(luma=0.92, std=0.040, sat=0.08, tilt=16.0, smooth=138, pore=4),
    "quartz_pillar_top": dict(luma=0.88, std=0.050, sat=0.09, tilt=13.4, smooth=104, pore=4),
    "quartz_bricks": dict(luma=0.87, std=0.032, sat=0.07, tilt=10.4, smooth=129, pore=4),
    "quartz_block_bottom": dict(luma=0.87, std=0.030, sat=0.07, tilt=3.0, smooth=145, pore=4),
    # round 17 (lovish Oct 20: "pepper dots"): the crystalline stones trimmed to read as one calm stone from the RTS
    # camera - andesite 0.075, granite 0.071, diorite 0.100 before
    "andesite": dict(luma=0.56, std=0.055, sat=0.03, lf=0.4, ao=0.6, tilt=7.0, smooth=49, pore=12),
    "granite": dict(luma=0.47, std=0.048, sat=0.39, lf=0.4, ao=0.6, tilt=7.0, smooth=42, pore=12),
    "diorite": dict(luma=0.73, std=0.070, sat=0.02, lf=0.4, ao=0.6, tilt=7.0, smooth=22, pore=12),
    "snow": dict(luma=0.93, std=0.019, sat=0.05, tilt=5.9, smooth=92, pore=0),
    "podzol_top": dict(luma=0.32, std=0.090, sat=0.40, tilt=20.0, smooth=41, pore=56),
    "coarse_dirt": dict(luma=0.36, std=0.080, sat=0.37, tilt=25.0, smooth=10, pore=56),
    "clay": dict(luma=0.53, std=0.035, sat=0.10,   # r21: read pink-white under the warm sun - bluer
    tilt=8.9, smooth=102, pore=64),
    "red_sand": dict(luma=0.52, std=0.040, sat=0.60, tilt=12.9, smooth=34, pore=60),
    "mud": dict(luma=0.22, std=0.028, sat=0.14, tilt=16.3, smooth=90, pore=64),
    "calcite": dict(luma=0.87, std=0.075, sat=0.01, tilt=19.4, smooth=59, pore=10),
    # round 18: tuff is a fine ash rock - its 7-texel crystals speckled without shaders (std 0.085 -> 0.060)
    "tuff": dict(luma=0.43, std=0.060, sat=0.06, lf=0.5, tilt=14.0, smooth=11, pore=20),
    # round 13: single-block numbers from pack-study 38762019919 (vanilla 38762030150 beside it); the reference's
    # stripped logs share one generic 23 deg relief - 14 here (bare wood is smoother than bark); diorite std trimmed
    "birch_planks": dict(luma=0.65, std=0.060,   # r21: board joints vanished at RTS zoom
    sat=0.33, tilt=6.0, smooth=76, pore=24),
    "jungle_planks": dict(luma=0.48, std=0.055, sat=0.46, tilt=7.1, smooth=59, pore=24),
    "acacia_planks": dict(luma=0.41, std=0.043, sat=0.62, tilt=6.0, smooth=89, pore=24),
    "dark_oak_planks": dict(luma=0.19, std=0.037, sat=0.64, tilt=6.6, smooth=50, pore=24),
    "mangrove_planks": dict(luma=0.25, std=0.035, sat=0.60, tilt=6.0, smooth=54, pore=24),
    "crimson_planks": dict(luma=0.28, std=0.037, sat=0.56, tilt=10.0, smooth=41, pore=24),
    "warped_planks": dict(luma=0.44, std=0.049, sat=0.59, tilt=10.0, smooth=49, pore=24),
    "stripped_oak_log": dict(luma=0.57, std=0.055, sat=0.50, tilt=14.0, smooth=34, pore=30),
    "stripped_spruce_log": dict(luma=0.36, std=0.055, sat=0.34, tilt=14.0, smooth=34, pore=30),
    "stripped_birch_log": dict(luma=0.68, std=0.055, sat=0.38, tilt=14.0, smooth=34, pore=30),
    "stripped_jungle_log": dict(luma=0.51, std=0.050, sat=0.48, tilt=14.0, smooth=34, pore=30),
    "stripped_acacia_log": dict(luma=0.44, std=0.040, sat=0.64, tilt=14.0, smooth=34, pore=30),
    "stripped_dark_oak_log": dict(luma=0.20, std=0.027, sat=0.56, tilt=14.0, smooth=34, pore=30),
    "stripped_mangrove_log": dict(luma=0.26, std=0.045, sat=0.60, tilt=14.0, smooth=34, pore=30),
    "polished_granite": dict(luma=0.44, std=0.060, sat=0.37, tilt=4.0, smooth=125, pore=4),
    "polished_diorite": dict(luma=0.75, std=0.070, sat=0.02, tilt=4.0, smooth=136, pore=4),
    "blackstone": dict(luma=0.18, std=0.035, sat=0.10, tilt=24.0, smooth=12, pore=12),
    "obsidian": dict(luma=0.06, std=0.025, sat=0.25, tilt=10.0, smooth=194, pore=0),
    "red_nether_bricks": dict(luma=0.11, std=0.035, sat=0.84, tilt=15.0, smooth=29, pore=12),
    "chiseled_stone_bricks": dict(luma=0.45, std=0.085, sat=0.03, tilt=22.0, smooth=25, pore=12),
    "dirt_path_top": dict(luma=0.47, std=0.050, sat=0.44, tilt=15.0, smooth=29, pore=50),
    "dirt_path_side": dict(luma=0.40, std=0.069, sat=0.46, tilt=18.0, smooth=18, pore=56),
    # round 14: single-block numbers, pack-study 38763481360 (vanilla 38763491796 beside it); metal and gem blocks
    # between the reference (which leans on shader reflections) and vanilla's brighter colours; relief trimmed on
    # the carved and plated blocks (the reference's 26-31 deg come from engraved patterns we draw flatter)
    "cracked_stone_bricks": dict(luma=0.44, std=0.085, sat=0.02, tilt=20.0, smooth=10, pore=12),
    "cracked_deepslate_bricks": dict(luma=0.29, std=0.062, sat=0.02, tilt=18.0, smooth=8, pore=10),
    "cracked_polished_blackstone_bricks": dict(luma=0.15, std=0.057, sat=0.11, tilt=22.0, smooth=19, pore=8),
    "chiseled_nether_bricks": dict(luma=0.15, std=0.060, sat=0.30, tilt=22.0, smooth=49, pore=12),
    "chiseled_polished_blackstone": dict(luma=0.19, std=0.058, sat=0.12, tilt=22.0, smooth=30, pore=8),
    "reinforced_deepslate_side": dict(luma=0.40, std=0.150, sat=0.06, tilt=20.0, smooth=11, pore=8),
    "reinforced_deepslate_top": dict(luma=0.36, std=0.140, sat=0.06, tilt=20.0, smooth=16, pore=8),
    "basalt_side": dict(luma=0.28, soften=1.4, lf=0.3, std=0.035, sat=0.08, tilt=10.0, smooth=24, pore=10),   # r24: was foil (tilt 26)
    "basalt_top": dict(luma=0.30, std=0.081, sat=0.06, tilt=24.0, smooth=20, pore=10),
    "smooth_basalt": dict(luma=0.30, std=0.060, sat=0.06, tilt=13.9, smooth=25, pore=8),
    "soul_sand": dict(luma=0.25, std=0.045, sat=0.37, tilt=24.0, smooth=60, pore=40),
    "iron_block": dict(luma=0.76, std=0.070, sat=0.04, tilt=10.0, smooth=147, pore=0, f0=230),
    "netherite_block": dict(luma=0.30, std=0.060, sat=0.12, tilt=10.0, smooth=201, pore=0, f0=255),
    "diamond_block": dict(luma=0.72, std=0.090, sat=0.30, tilt=12.0, smooth=250, pore=0, f0=44),
    "emerald_block": dict(luma=0.42, std=0.080, sat=0.50, tilt=12.0, smooth=200, pore=0, f0=15),
    "lapis_block": dict(luma=0.22, std=0.050, sat=0.55, tilt=7.6, smooth=60, pore=0, f0=15),
    "end_stone": dict(luma=0.80, std=0.075, sat=0.28, tilt=17.1, smooth=39, pore=20),
    "white_concrete": dict(luma=0.80, std=0.008, sat=0.04, tilt=2.0, smooth=86, pore=10),
    "blue_terracotta": dict(luma=0.25, std=0.010, sat=0.30, tilt=3.9, smooth=29, pore=30),
    "podzol_side": dict(luma=0.36, std=0.090, sat=0.44, tilt=21.6, smooth=15, pore=56),
    # round 15 (Oct 15, the texture audit): the face census's partial blocks (tools/face_census.py) - our texture on
    # some faces of a block, vanilla 16x on the rest. Each new face takes its sibling's numbers: a stripped log's top
    # between its log top and its stripped side; blackstone's top, quartz's side / top, farmland and the snowy grass
    # side the stone, quartz or dirt they belong to; the nether stems from vanilla's own colours (no reference study)
    "stripped_oak_log_top": dict(luma=0.50, std=0.065, sat=0.48, tilt=12.0, smooth=40, pore=30),
    "stripped_spruce_log_top": dict(luma=0.34, std=0.065, sat=0.42, tilt=12.0, smooth=40, pore=30),
    "stripped_birch_log_top": dict(luma=0.67, std=0.080, sat=0.36, tilt=12.0, smooth=40, pore=30),
    "stripped_jungle_log_top": dict(luma=0.47, std=0.075, sat=0.50, tilt=12.0, smooth=40, pore=30),
    "stripped_acacia_log_top": dict(luma=0.43, std=0.050, sat=0.63, tilt=12.0, smooth=40, pore=30),
    "stripped_dark_oak_log_top": dict(luma=0.20, std=0.035, sat=0.58, tilt=12.0, smooth=40, pore=30),
    "stripped_mangrove_log_top": dict(luma=0.27, std=0.045, sat=0.60, tilt=12.0, smooth=40, pore=30),
    "blackstone_top": dict(luma=0.17, std=0.033, sat=0.10, tilt=20.0, smooth=14, pore=12),
    "quartz_block_top": dict(luma=0.87, std=0.032, sat=0.07, tilt=4.0, smooth=145, pore=4),
    "quartz_block_side": dict(luma=0.88, std=0.030, sat=0.07, tilt=3.0, smooth=145, pore=4),
    "farmland": dict(luma=0.38, std=0.085, sat=0.38, tilt=22.0, smooth=22, pore=56),
    "farmland_moist": dict(luma=0.24, std=0.070, sat=0.40, tilt=20.0, smooth=60, pore=64),
    # the snowy side: dirt and snow each fitted to their own block's numbers (the recipe); smooth / pore between them
    "grass_block_snow": dict(luma=0.45, std=0.080, sat=0.30, tilt=19.0, smooth=35, pore=40),
    "crimson_stem": dict(luma=0.22, std=0.070, sat=0.60, tilt=24.0, smooth=40, pore=36),
    "crimson_stem_top": dict(luma=0.28, std=0.070, sat=0.55, tilt=12.0, smooth=40, pore=30),
    "warped_stem": dict(luma=0.24, std=0.075, sat=0.50, tilt=24.0, smooth=40, pore=36),
    "warped_stem_top": dict(luma=0.30, std=0.070, sat=0.55, tilt=12.0, smooth=40, pore=30),
    # round 16 (Oct 15, after the audit): the face census's most-seen uncovered textures - cut-out and see-through
    # blocks (mangrove roots 1.8% of the seen faces, glass ~1.5%, ladders, lanterns, iron bars, cobwebs). No reference
    # numbers: roots and ladder take the wood they are, glass and lanterns are painted (their parts keep their own
    # colours - not fitted), tilt modest (thin parts)
    "mangrove_roots_side": dict(luma=0.30, std=0.060, sat=0.38, tilt=20.0, smooth=30, pore=40),
    "mangrove_roots_top": dict(luma=0.30, std=0.060, sat=0.38, tilt=20.0, smooth=30, pore=40),
    "glass": dict(luma=0.80, std=0.040, sat=0.05, tilt=4.0, smooth=235, pore=0, hspan=0.05),
    "glass_pane_top": dict(luma=0.72, std=0.030, sat=0.08, tilt=4.0, smooth=200, pore=0),
    "red_stained_glass": dict(luma=0.30, std=0.030, sat=0.75, tilt=4.0, smooth=235, pore=0, hspan=0.05),
    "red_stained_glass_pane_top": dict(luma=0.25, std=0.030, sat=0.75, tilt=4.0, smooth=200, pore=0),
    "cyan_stained_glass": dict(luma=0.40, std=0.030, sat=0.65, tilt=4.0, smooth=235, pore=0, hspan=0.05),
    "cyan_stained_glass_pane_top": dict(luma=0.34, std=0.030, sat=0.65, tilt=4.0, smooth=200, pore=0),
    "tinted_glass": dict(luma=0.15, std=0.030, sat=0.25, tilt=4.0, smooth=235, pore=0, hspan=0.05),
    "ladder": dict(luma=0.42, std=0.060, sat=0.46, tilt=14.0, smooth=50, pore=24),
    "iron_bars": dict(luma=0.30, std=0.050, sat=0.08, tilt=14.0, smooth=110, pore=0),
    "lantern": dict(luma=0.30, std=0.100, sat=0.30, tilt=12.0, smooth=110, pore=0),
    "soul_lantern": dict(luma=0.30, std=0.100, sat=0.30, tilt=12.0, smooth=110, pore=0),
    "cobweb": dict(luma=0.85, std=0.040, sat=0.02, tilt=4.0, smooth=120, pore=0),
}


# The seeds follow TARGETS' order (1000 + 37 * index): a key inserted in the middle re-rolled every texture after it
# (the leaves, Oct 15 - caught by comparing checksums). So the order is checked against this list: append new keys
# at the END of both, never insert.
SEED_ORDER = (
    "grass_block_top",
    "grass_block_side",
    "grass_block_side_overlay",
    "dirt",
    "stone",
    "cobblestone",
    "sand",
    "gravel",
    "oak_planks",
    "oak_log",
    "oak_log_top",
    "stone_bricks",
    "polished_andesite",
    "smooth_stone",
    "smooth_stone_slab_side",
    "spruce_planks",
    "oak_leaves",
    "birch_leaves",
    "spruce_leaves",
    "acacia_leaves",
    "jungle_leaves",
    "dark_oak_leaves",
    "raw_iron_block",
    "gold_block",
    "barrel_side",
    "barrel_top",
    "barrel_bottom",
    "hay_block_side",
    "hay_block_top",
    "spruce_log",
    "spruce_log_top",
    "birch_log",
    "birch_log_top",
    "acacia_log",
    "acacia_log_top",
    "jungle_log",
    "jungle_log_top",
    "dark_oak_log",
    "dark_oak_log_top",
    "polished_blackstone",
    "polished_blackstone_bricks",
    "deepslate_bricks",
    "deepslate_tiles",
    "deepslate",
    "deepslate_top",
    "polished_deepslate",
    "cobbled_deepslate",
    "cut_copper",
    "oxidized_cut_copper",
    "azalea_leaves",
    "flowering_azalea_leaves",
    "netherrack",
    "nether_bricks",
    "prismarine_bricks",
    "dark_prismarine",
    "moss_block",
    "mossy_stone_bricks",
    "sandstone",
    "sandstone_top",
    "sandstone_bottom",
    "red_terracotta",
    "terracotta",
    "polished_basalt_side",
    "polished_basalt_top",
    "bone_block_side",
    "bone_block_top",
    "quartz_pillar",
    "quartz_pillar_top",
    "quartz_bricks",
    "quartz_block_bottom",
    "andesite",
    "granite",
    "diorite",
    "snow",
    "podzol_top",
    "coarse_dirt",
    "clay",
    "red_sand",
    "mud",
    "calcite",
    "tuff",
    "birch_planks",
    "jungle_planks",
    "acacia_planks",
    "dark_oak_planks",
    "mangrove_planks",
    "crimson_planks",
    "warped_planks",
    "stripped_oak_log",
    "stripped_spruce_log",
    "stripped_birch_log",
    "stripped_jungle_log",
    "stripped_acacia_log",
    "stripped_dark_oak_log",
    "stripped_mangrove_log",
    "polished_granite",
    "polished_diorite",
    "blackstone",
    "obsidian",
    "red_nether_bricks",
    "chiseled_stone_bricks",
    "dirt_path_top",
    "dirt_path_side",
    "cracked_stone_bricks",
    "cracked_deepslate_bricks",
    "cracked_polished_blackstone_bricks",
    "chiseled_nether_bricks",
    "chiseled_polished_blackstone",
    "reinforced_deepslate_side",
    "reinforced_deepslate_top",
    "basalt_side",
    "basalt_top",
    "smooth_basalt",
    "soul_sand",
    "iron_block",
    "netherite_block",
    "diamond_block",
    "emerald_block",
    "lapis_block",
    "end_stone",
    "white_concrete",
    "blue_terracotta",
    "podzol_side",
    "stripped_oak_log_top",
    "stripped_spruce_log_top",
    "stripped_birch_log_top",
    "stripped_jungle_log_top",
    "stripped_acacia_log_top",
    "stripped_dark_oak_log_top",
    "stripped_mangrove_log_top",
    "blackstone_top",
    "quartz_block_top",
    "quartz_block_side",
    "farmland",
    "farmland_moist",
    "grass_block_snow",
    "crimson_stem",
    "crimson_stem_top",
    "warped_stem",
    "warped_stem_top",
    "mangrove_roots_side",
    "mangrove_roots_top",
    "glass",
    "glass_pane_top",
    "red_stained_glass",
    "red_stained_glass_pane_top",
    "cyan_stained_glass",
    "cyan_stained_glass_pane_top",
    "tinted_glass",
    "ladder",
    "iron_bars",
    "lantern",
    "soul_lantern",
    "cobweb",
    # round 19: a second texture per leaf (LEAF_VARIANTS) - appended, so no other texture re-rolls
    "oak_leaves_v2",
    "birch_leaves_v2",
    "spruce_leaves_v2",
    "acacia_leaves_v2",
    "jungle_leaves_v2",
    "dark_oak_leaves_v2",
    "grass_block_top_v2",     # round 19: a second grass top (GRASS_VARIANTS)
    "cherry_log",             # Oct 21: map-tree cherries
    "cherry_log_top",
    "cherry_leaves",
    "grass_block_top_v3",     # round 25: two more grass tops, a shade darker / lighter (GRASS_VARIANTS)
    "grass_block_top_v4",
)


# ------------------------------------------------------------------ periodic building blocks ---------------------
class Field:
    """Periodic noise and shapes on an N x N tile (everything wraps, so every texture tiles seamlessly)."""

    def __init__(self, n, seed):
        self.n = n
        self.rng = np.random.default_rng(seed)
        f = np.fft.fftfreq(n) * n                        # cycles per tile
        self.fx, self.fy = np.meshgrid(f, f)
        yy, xx = np.mgrid[0:n, 0:n]
        self.u, self.v = xx / n, yy / n                  # 0..1 tile coordinates

    def noise(self, lo, hi, slope=1.0, sx=1.0, sy=1.0):
        """Band-limited noise between lo and hi cycles per tile, power ~ f^-slope. sx / sy squeeze the band along u /
        v: a LARGE sx keeps only low frequencies across u, so features are LONG along u (horizontal); a large sy makes
        them long along v (vertical). Normalised to mean 0, std 1."""
        r = np.sqrt((self.fx * sx) ** 2 + (self.fy * sy) ** 2)
        band = (r >= lo) & (r <= hi)
        amp = np.where(band, 1.0 / np.maximum(r, 1e-6) ** slope, 0.0)
        w = self.rng.standard_normal((self.n, self.n)) + 1j * self.rng.standard_normal((self.n, self.n))
        out = np.real(np.fft.ifft2(np.fft.fft2(np.real(np.fft.ifft2(w))) * amp))
        s = out.std()
        return (out - out.mean()) / (s if s > 1e-12 else 1.0)

    def fbm(self, base, octaves, gain=0.5, sx=1.0, sy=1.0):
        out, a, total = np.zeros((self.n, self.n)), 1.0, 0.0
        for o in range(octaves):
            f = base * 2 ** o
            out += a * self.noise(f, f * 2, 0.0, sx, sy)
            total += a
            a *= gain
        return out / total

    def voronoi(self, count, jitter=1.0, aniso=(1.0, 1.0), grid=None):
        """Periodic Voronoi on jittered grid-ish points: F1, F2 (in tile units) and the nearest cell's index.
        grid=(columns, rows) lays the points on an uneven grid (with aniso to match, cells longer than wide - bark)."""
        k = max(1, int(round(math.sqrt(count))))
        ku, kv = grid if grid else (k, k)
        gx, gy = np.meshgrid((np.arange(ku) + 0.5) / ku, (np.arange(kv) + 0.5) / kv)
        pts = np.stack([gx.ravel(), gy.ravel()], 1)
        pts = (pts + (self.rng.random(pts.shape) - 0.5) * jitter / np.array([ku, kv])) % 1.0
        f1 = np.full((self.n, self.n), 9.0)
        f2 = np.full((self.n, self.n), 9.0)
        idx = np.zeros((self.n, self.n), dtype=np.int32)
        # with grid= every periodic copy counts (a stretched metric can make a point's OTHER copy the second nearest:
        # the single nearest copy then left wrong F2 values - isolated dips that drew dots on the bark)
        shifts = [(0.0, 0.0)] if grid is None else [(a, b) for a in (-1.0, 0.0, 1.0) for b in (-1.0, 0.0, 1.0)]
        for i, (px, py) in enumerate(pts):
            for sx_, sy_ in shifts:
                if grid is None:
                    dx = (self.u - px + 0.5) % 1.0 - 0.5          # nearest periodic copy
                    dy = (self.v - py + 0.5) % 1.0 - 0.5
                else:
                    dx, dy = self.u - px + sx_, self.v - py + sy_
                d = np.sqrt((dx * aniso[0]) ** 2 + (dy * aniso[1]) ** 2)
                closer = d < f1
                f2 = np.where(closer, f1, np.minimum(f2, d))
                idx = np.where(closer, i, idx)
                f1 = np.where(closer, d, f1)
        return f1, f2, idx, len(pts)

    def warp(self, img, du, dv):
        """Samples img at (u + du, v + dv) (tile units), bilinear, wrapping."""
        n = self.n
        x = (self.u + du) * n
        y = (self.v + dv) * n
        x0, y0 = np.floor(x).astype(int), np.floor(y).astype(int)
        tx, ty = x - x0, y - y0
        x0 %= n
        y0 %= n
        x1, y1 = (x0 + 1) % n, (y0 + 1) % n
        return (img[y0, x0] * (1 - tx) * (1 - ty) + img[y0, x1] * tx * (1 - ty)
                + img[y1, x0] * (1 - tx) * ty + img[y1, x1] * tx * ty)

    def blur(self, img, sigma_px):
        g = np.exp(-2 * (math.pi ** 2) * (sigma_px ** 2) * ((self.fx / self.n) ** 2 + (self.fy / self.n) ** 2))
        return np.real(np.fft.ifft2(np.fft.fft2(img) * g))


def norm01(a):
    lo, hi = np.percentile(a, 0.5), np.percentile(a, 99.5)
    return np.clip((a - lo) / max(hi - lo, 1e-9), 0, 1)


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def tint(base_rgb, shade, jitter=None):
    """Albedo = a base colour scaled by shade (0..~1.5 around 1), with optional per-pixel colour jitter (N,N,3)."""
    rgb = np.asarray(base_rgb, dtype=np.float64)[None, None, :] * shade[..., None]
    if jitter is not None:
        rgb = rgb * (1 + jitter)
    return np.clip(rgb, 0, 1)


# ------------------------------------------------------------------ the materials ---------------------------------
def m_dirt(F, n):
    clumps = F.fbm(3, 4, 0.55)
    grit = F.noise(12, 40, 0.3)
    f1, f2, idx, k = F.voronoi(90, 1.0)
    pebble_on = F.rng.random(k) < 0.22
    peb = pebble_on[idx] & (f1 < 0.018)
    H = 0.55 + 0.20 * clumps + 0.06 * grit
    H = np.where(peb, H + 0.25 * (1 - f1 / 0.018), H)
    hue = F.noise(2, 6, 0.5)
    jit = np.stack([0.06 * hue, 0.02 * hue, -0.05 * hue], -1)
    shade = 0.8 + 0.35 * norm01(H)
    alb = tint((0.47, 0.33, 0.22), shade, jit)
    alb = np.where(peb[..., None], np.clip(alb * 0.6 + 0.18, 0, 1) * np.array([0.95, 0.93, 0.9]), alb)
    return H, alb


def m_dirt_r24(F, n):
    """Dirt, round 24/25. r24's eyes close-up (run 39838783742): main's dirt read as orange cork. r24's clods read as
    cracked crazy paving (a closed Voronoi network of dark gaps) and scaly pebbles, and doubled the plain-lane noise.
    Real topsoil: a FINE CRUMB - soft rounded crumbs a few mm across, low contrast, no crack network - a few small
    stones, the odd root bit, darker damp patches, warm umber. Crumbs are soft domes (shading, not outlines)."""
    rng = F.rng
    c1, c2, cidx, ck = F.voronoi(520, 1.0)               # crumbs ~1/23 block (5-6 texels at 128x)
    dome = np.clip(1 - (2 * c1 / np.maximum(c1 + c2, 1e-6)) ** 2, 0, 1)
    crumb_tone = (rng.random(ck) - 0.5)[cidx]
    lumpy = F.fbm(3, 3, 0.6)                             # crumbs gather in soft lumps
    moist = smoothstep(0.2, 0.9, F.fbm(2, 3, 0.5))       # darker damp, organic patches
    s1, s2, sidx, sk = F.voronoi(60, 1.0)
    stones = (rng.random(sk) < 0.10)[sidx] & (s1 < 0.022)  # a few small stones
    sdome = np.clip(1 - s1 / 0.022, 0, 1)
    roots = np.zeros((n, n))
    for _ in range(6):                                   # a few short root bits
        x0, y0, ang = rng.random() * n, rng.random() * n, rng.random() * math.pi
        ln = n * (0.06 + 0.08 * rng.random())
        for st in range(int(ln * 2)):
            t_ = st / max(1, int(ln * 2) - 1)
            bend = 0.3 * math.sin(t_ * 3.0)
            x = int(round(x0 + math.cos(ang + bend) * t_ * ln)) % n
            y = int(round(y0 + math.sin(ang + bend) * t_ * ln)) % n
            roots[y, x] = 1.0
    roots = F.blur(roots, 0.6) * 2.5
    H = 0.45 + 0.06 * np.sqrt(dome) + 0.015 * lumpy + 0.03 * crumb_tone + 0.02 * np.clip(roots, 0, 1)
    H = np.where(stones, H + 0.15 * sdome, H)
    shade = (0.94 + 0.08 * crumb_tone + 0.05 * dome + 0.03 * lumpy) - 0.04 * moist
    alb = tint((0.42, 0.35, 0.25), shade)              # umber: red/green 1.2 (the low sun warms it further)
    alb = np.where(stones[..., None], tint((0.42, 0.39, 0.35), 0.80 + 0.2 * sdome), alb)
    rc = np.clip(roots, 0, 1)[..., None]
    alb = alb * (1 - 0.6 * rc) + tint((0.38, 0.26, 0.17), np.ones((n, n))) * 0.6 * rc
    return H, alb

def stone_facets(F, count):
    """Chipped planes: Voronoi cells, each a flat plane at its own small slope, normalised to std 1. Periodic: each
    texel's offset from its cell's centre is taken the short way round the tile."""
    f1, f2, idx, k = F.voronoi(count, 1.0)
    rng = F.rng
    cnt = np.maximum(np.bincount(idx.ravel(), minlength=k), 1)
    ang = 2 * math.pi
    cx = np.arctan2(np.bincount(idx.ravel(), np.sin(ang * F.u).ravel(), k),
                    np.bincount(idx.ravel(), np.cos(ang * F.u).ravel(), k)) / ang
    cy = np.arctan2(np.bincount(idx.ravel(), np.sin(ang * F.v).ravel(), k),
                    np.bincount(idx.ravel(), np.cos(ang * F.v).ravel(), k)) / ang
    del cnt
    dx = (F.u - cx[idx] + 0.5) % 1.0 - 0.5
    dy = (F.v - cy[idx] + 0.5) % 1.0 - 0.5
    gx, gy, h0 = rng.standard_normal(k), rng.standard_normal(k), rng.standard_normal(k)
    plane = h0[idx] * 0.03 + 0.6 * (gx[idx] * dx + gy[idx] * dy)
    edge = 1 - smoothstep(0.0, 0.004, f2 - f1)            # the chip line between two facets, a hair low
    plane = plane - 0.01 * edge
    return (plane - plane.mean()) / max(plane.std(), 1e-9)


def m_stone(F, n):
    # NOT bedding: vanilla turns every stone block a random quarter (ROTATED), so horizontal bands became vertical on
    # every other block and the field read as woven cloth from the RTS camera (round 17) - a soft isotropic drift
    strata = F.fbm(2, 3, 0.5)
    body = F.fbm(3, 5, 0.6)
    grain = F.noise(12, 40, 0.2)
    line = np.abs(F.warp(F.noise(2, 6, 0.5), 0.05 * F.noise(4, 10), 0.05 * F.noise(4, 10)))
    sparse = smoothstep(0.4, 0.9, F.noise(1, 3, 0.5))    # cracks only in some parts of the block
    # blurred a little: the crack line's narrowest points were single black texels (pepper without shaders, r18c)
    cracks = F.blur((1 - smoothstep(0.0, 0.035, line)) * sparse, 0.8)
    # round 17 (lovish Oct 20: stone "pepper dots", not cohesive): the relief is a fine grain on soft bedding, not
    # blobs - body's mid-scale lumps, lit, drew a lattice of bumps from the RTS camera; the colour is a calm mottle
    # with the cracks. The base is a hair warm: a dead-neutral grey whose only hue was the +-jitter had that jitter
    # blown up by the saturation fit into blue and beige patches.
    # the grain is broad-band (20-120 cycles, falling off): a narrow band of noise draws a maze of worms, plain at 256x
    # round 18 (eyes structure run 39536435197: under shaders the stone read as crumpled foil): the relief is broad
    # FACETS - a weathered rock face is flat planes chipped at slight angles, meeting at crisp edges - with a light
    # grain and a few pits, instead of one even field of bumps that the shader lit as foil
    # round 18b: NO mid-scale relief. The eyes scenes light from a low sun, and any patch of relief 1/10-1/4 block
    # across that tilts away from it falls into blue sky-shadow - round 17's bumps read as foil and round 18's chipped
    # facets as dark blue blotches (eyes structure run 39541067693). Ruled out: mid-scale relief of any shape. Left:
    # a fine grain (averages out from the RTS camera, catches the light up close), soft drift and the cracks.
    H = 0.6 + 0.02 * strata + 0.03 * F.noise(24, 120, 1.0) - 0.15 * cracks
    hue = F.noise(2, 5, 0.5)
    jit = np.stack([0.008 * hue, 0.0 * hue, -0.008 * hue], -1)
    shade = 0.92 + 0.035 * body + 0.03 * strata + 0.015 * grain - 0.12 * cracks   # cracks drew dark worms (r18)
    return H, tint((0.56, 0.55, 0.53), shade, jit)


def m_stone_r24(F, n):
    """Stone, round 24 (eyes run 39838783742, close-ups: rounds 17-23's stone read as grey foam - soft clouds 1/8 block
    across, the only thing the mip-2 luma fit could scale up once the relief was taken out). A real rock face is a
    GRANULAR surface: distinct mineral grains 1/64-1/20 block, a little lighter or darker than the ground mass, a soft
    tone drift across the block and a hairline fracture here and there. Nothing in between: whatever lives at 1/16-1/4
    block survives the mipmaps and the luma fit blows it up into blotches (a stain layer here did exactly that).
    The relief stays fine (round 18b: no mid-scale relief under a low sun) - grains and pits the light catches up close."""
    grain = F.noise(14, 56, 0.3)                          # mineral grains, 2-9 texels at 128x
    micro = F.noise(48, 128, 0.0)
    dark = smoothstep(0.8, 1.9, grain)                    # ~15% darker grains (hornblende, wet pores)
    light = smoothstep(0.8, 1.9, -grain)                  # ~15% paler (feldspar, calcite)
    drift = F.noise(1, 3, 0.8)                            # a soft tone drift across the block
    # r24c (eyes run 39845003889: the grain alone read as even asphalt from 3 blocks) the fractal starts an octave
    # lower, 2-128 cycles - tone at every scale, still no one blob size
    rough = F.fbm(2, 6, 0.8)
    line = np.abs(F.warp(F.noise(2, 6, 0.5), 0.05 * F.noise(4, 10), 0.05 * F.noise(4, 10)))
    sparse = smoothstep(0.7, 1.2, F.noise(1, 3, 0.5))     # a fracture or two, not a net
    cracks = F.blur((1 - smoothstep(0.0, 0.025, line)) * sparse, 0.6)
    pits = smoothstep(1.7, 2.6, F.noise(10, 30, 0.2))
    # r24b (eyes run 39841580320: the grain alone read as even sandpaper / concrete): one or two thin pale calcite
    # veins, healed fractures - lines, not blobs, so the luma fit has no blob to blow up
    vline = np.abs(F.warp(F.noise(1, 4, 0.5), 0.04 * F.noise(4, 12), 0.04 * F.noise(4, 12)))
    veins = (1 - smoothstep(0.0, 0.018, vline)) * smoothstep(0.5, 1.1, F.noise(1, 3, 0.5))
    H = 0.6 + 0.05 * grain + 0.015 * micro - 0.15 * cracks - 0.06 * pits + 0.01 * veins
    shade = 1.0 + 0.06 * (light - dark) + 0.05 * rough + 0.015 * micro + 0.004 * drift - 0.08 * cracks - 0.04 * pits \
        + 0.10 * veins
    # r25: a neutral-cool grey (under the warm low sun r24's warm grey read pink-brown)
    return H, tint((0.52, 0.54, 0.57), shade)


def m_cobblestone(F, n, cells=22, contrast=0.45, fine=0.05):
    # irregular joints: warp the lookup position, not the distance (adding noise to the distance punched mortar
    # holes inside the stones)
    f1, f2, idx, k = F.voronoi(cells, 0.9)
    wu, wv = 0.012 * F.noise(4, 12, 0.5), 0.012 * F.noise(4, 12, 0.5)
    f1, f2 = F.warp(f1, wu, wv), F.warp(f2, wu, wv)
    idx = idx[(np.round((F.v + wv) * n).astype(int)) % n, (np.round((F.u + wu) * n).astype(int)) % n]
    edge = f2 - f1                                       # 0 at the joints
    stone = smoothstep(0.003, 0.06, edge)                # 0 in the mortar, 1 inside a stone (rounded)
    tone = F.rng.random(k)
    surf = F.noise(12, 48, 0.4)
    H = 0.25 + 0.6 * np.sqrt(stone) + 0.03 * surf * stone
    shade_stone = (0.96 - contrast / 2 + contrast * tone[idx]) * (1.0 - fine + fine * surf)
    shade = np.where(stone > 0.15, shade_stone, 0.45 + 0.2 * stone)
    hue = (tone[idx] - 0.5)
    jit = np.stack([0.04 * hue, 0.0 * hue, -0.04 * hue], -1)
    return H, tint((0.52, 0.52, 0.51), shade, jit)


def m_gravel(F, n):
    # r21: 70 / 260 cells per block blurred into a muddy mottle at RTS zoom (reviewer: "no pebble shapes")
    f1, f2, idx, k = F.voronoi(36, 1.0)
    g1, g2, gidx, gk = F.voronoi(120, 1.0)
    big = (F.rng.random(k) < 0.45)[idx]                  # some places a big pebble, elsewhere small ones
    edge = np.where(big, f2 - f1, g2 - g1)
    idx = np.where(big, idx, gidx + k)
    k = k + gk
    # a DOMED pebble (1 at its centre, 0 at the cell boundary where f1 = f2), not a bevelled polygon: the old profile
    # (smoothstep of f2 - f1) made flat-topped shards
    a1, a2 = np.where(big, f1, g1), np.where(big, f2, g2)
    peb = np.clip(1 - (2 * a1 / np.maximum(a1 + a2, 1e-6)) ** 2, 0, 1) * smoothstep(0.0, 0.02, edge)
    # round 10: the busiest texture on screen (rts std 0.114 vs vanilla's calm 0.067) with peach "shards" - a narrower
    # tone range and greyer warm pebbles; gravel lines riverbeds, seen through the water
    tone = 0.42 + 0.26 * F.rng.random(k)
    kind = F.rng.random(k)
    H = 0.2 + 0.75 * peb ** 0.45 + 0.03 * F.noise(16, 40, 0.2)
    base = np.where(kind[idx][..., None] < 0.55, np.array([0.50, 0.49, 0.48]),
                    np.where(kind[idx][..., None] < 0.85, np.array([0.50, 0.47, 0.44]), np.array([0.38, 0.37, 0.37])))
    shade = (0.65 + 0.7 * tone[idx]) * (0.55 + 0.45 * peb)
    alb = np.clip(base * shade[..., None], 0, 1)
    return H, alb


def m_gravel_r25(F, n):
    """Gravel, round 25 (reviewer: main's gravel read as Voronoi flagstones / bubble wrap). Gravel is MANY small
    rounded pebbles, ~2-4 cm: two sizes of soft-domed stones packed together, each its own grey (a few warm, a few
    dark), shaded rounder than outlined - the gaps between them a soft shadow, not a black grid."""
    rng = F.rng
    f1, f2, idx, k = F.voronoi(260, 1.0)                 # ~8 texels: the larger pebbles
    g1, g2, gidx, gk = F.voronoi(700, 1.0)               # ~5 texels: the small ones
    big = (rng.random(k) < 0.5)[idx]
    a1, a2 = np.where(big, f1, g1), np.where(big, f2, g2)
    pid = np.where(big, idx, gidx + k)
    dome = np.clip(1 - (2 * a1 / np.maximum(a1 + a2, 1e-6)) ** 2, 0, 1)
    tone = rng.random(k + gk)
    kind = rng.random(k + gk)
    base = np.where(kind[pid][..., None] < 0.6, np.array([0.50, 0.49, 0.48]),
                    np.where(kind[pid][..., None] < 0.85, np.array([0.52, 0.48, 0.44]), np.array([0.40, 0.39, 0.39])))
    H = 0.3 + 0.5 * np.sqrt(dome) + 0.02 * F.noise(24, 64, 0.2)
    shade = (0.85 + 0.25 * tone[pid]) * (0.72 + 0.28 * np.sqrt(dome))
    return H, np.clip(base * shade[..., None], 0, 1)


def m_sand(F, n, base=(0.86, 0.78, 0.58)):
    grains = F.noise(16, 40, 0.0)
    ripple_ph = F.v * 2 * math.pi * 5 + 1.2 * F.noise(1, 3, 0.5)
    ripples = np.sin(ripple_ph)
    H = 0.6 + 0.06 * ripples + 0.08 * grains + 0.05 * F.fbm(3, 3)
    hue = F.noise(3, 8, 0.4)
    jit = np.stack([0.02 * hue, 0.01 * hue, -0.03 * hue], -1)
    shade = 0.92 + 0.12 * norm01(H) + 0.05 * grains
    return H, tint(base, shade, jit)


def grass_blades(F, count, length_px, width_px):
    """Short blades scattered over the tile: a height bump along each blade, wrapped."""
    n = F.n
    H = np.zeros((n, n))
    rng = F.rng
    for _ in range(count):
        cx, cy = rng.random() * n, rng.random() * n
        ang = rng.random() * math.pi
        ln = length_px * (0.6 + 0.8 * rng.random())
        dx, dy = math.cos(ang), math.sin(ang)
        steps = int(ln * 2) + 1
        for s in range(steps):
            t = s / max(1, steps - 1)
            x = int(round(cx + dx * (t - 0.5) * ln)) % n
            y = int(round(cy + dy * (t - 0.5) * ln)) % n
            hgt = 1.0 - abs(t - 0.5) * 1.2                 # highest at the middle of the blade
            for w in range(-(width_px // 2), width_px - width_px // 2):
                xx, yy = (x + int(round(-dy * w))) % n, (y + int(round(dx * w))) % n
                H[yy, xx] = max(H[yy, xx], hgt)
    return H


def m_grass_top(F, n):
    blades = grass_blades(F, int(9 * n), n / 36, 1)     # ~1100 blades of ~3-6 texels at 128x
    clumps = F.fbm(3, 4, 0.55)
    fine = F.noise(16, 40, 0.0)
    soil = smoothstep(0.55, 0.9, F.noise(4, 12, 0.5))   # a few thin spots where the soil shows (darker)
    H = 0.30 + 0.35 * blades + 0.18 * clumps + 0.05 * fine - 0.12 * soil
    # the COLOUR is calmer than the relief (round 4, from the first real eyes stills, run 38735013028): painted from H,
    # the blades and soil spots made a dark squiggly network that read as camouflage / moss from the RTS camera
    # (on-screen luma std 0.124 vs vanilla's calm 0.072). Soft small tufts carry the colour; the blades stay in the
    # normal map, where a shader's light turns them into real grain.
    # small tufts at 1/6 - 1/16 block, nothing coarser: block-sized patches repeat on every block and drew a lattice
    # across the field (a block texture cannot vary across many blocks - the biome tint does that)
    # round 19 (plain game "per-pixel speckle"): the tufts' band (6-16 cycles) drew a dark worm network that read as
    # speckle from the RTS camera - a broader, softer band; the blades' share of the colour halved again
    # r25: smaller, calmer tufts (3-14 cycles drew blobs a third of a block across - camouflage close up)
    tufts = F.noise(6, 22, 1.0)
    # round 18: the blades' share of the colour 0.06 -> 0.03 - without shaders they drew a repeating dash pattern
    # across a field (eyes structure run 39536432605); the normal map keeps them for the light
    shade = 0.85 + 0.12 * norm01(tufts) + 0.015 * norm01(blades + 0.5 * fine) - 0.05 * soil
    grey = np.clip(np.stack([shade] * 3, -1) * 0.62, 0, 1)    # greyscale: the biome colour tints it in game
    return H, grey


def side_blades(F, count, roots, length, tilt_deg, down=0.0, width_px=1):
    """Grass blades seen from the SIDE (round 17; claude/block-textures-2026-10-19.md: "the grass is growing sideways"
    - the fringe used grass_blades(), the TOP's helper, whose blades lie at any angle): each blade stands within
    +-tilt_deg of vertical, rooted at roots(u) (the fringe's lower edge, tile units), pointing UP - or, a `down` share
    of them, hanging down over the dirt. Returns (H, mask): a ridge along each blade, highest at the root."""
    n = F.n
    rng = F.rng
    H = np.zeros((n, n))
    for _ in range(count):
        u = rng.random()
        hang = rng.random() < down
        ln = length * (0.5 + rng.random()) * (0.35 if hang else 1.0)
        ang = math.radians((rng.random() * 2 - 1) * (tilt_deg * (1.6 if hang else 1.0)))
        r0 = roots(u) + (rng.random() - 0.3) * 0.02
        dx, dy = math.sin(ang), (1.0 if hang else -1.0) * math.cos(ang)      # +v is down the texture
        steps = int(ln * n * 2) + 1
        for st in range(steps):
            t = st / max(1, steps - 1)
            x = int(round((u + dx * t * ln) * n)) % n
            y = int(round((r0 + dy * t * ln) * n))
            if not 0 <= y < n:                       # past the block's top edge: no wrap to the bottom
                break
            hgt = 1.0 - 0.6 * t
            for w in range(width_px):
                H[y, (x + w) % n] = max(H[y, (x + w) % n], hgt)
    return H, H > 0


def m_grass_overlay(F, n, fringe):
    """The tinted fringe over the side texture: the lawn seen from the side, grey, alpha = the fringe. Its blades
    stand upright (side_blades), not at the top texture's random angles."""
    blades, _ = side_blades(F, int(4 * n), lambda u: 0.30, 0.30, 14, width_px=max(1, n // 128))
    clumps = F.fbm(3, 3, 0.55)
    H = 0.35 + 0.4 * blades + 0.15 * clumps
    shade = 0.80 + 0.30 * norm01(H)
    grey = np.clip(np.stack([shade] * 3, -1) * 0.62, 0, 1)
    return H, grey


def side_fringe(F, n):
    """How far the grass reaches down the side: a ragged edge ~3/16 of the block, with drips."""
    depth = 0.17 + 0.05 * F.noise(4, 16, 0.6)[0:1, :] + 0.06 * np.clip(F.noise(10, 30, 0.2)[0:1, :], 0, None)
    return (F.v < depth).astype(np.float64) * 1.0


def m_grass_side(F, n, dirt=m_dirt):
    Hd, ad = dirt(F, n)
    fringe = side_fringe(F, n)
    # the fringe's lower edge frays into blades: some stand up out of the band, a few hang over the dirt (the edge was
    # a cut line of sideways blades before)
    edge = (fringe > 0.5).sum(0) / n                                     # the band's depth in each column
    _, tufts = side_blades(F, int(1.2 * n), lambda u: edge[int(u * n) % n], 0.10, 12, down=0.35,
                           width_px=max(1, n // 128))
    fringe = np.maximum(fringe, tufts.astype(np.float64))
    # the untinted base under the overlay: a grey-green where the fringe is (seen only at its frayed edge)
    grassy = np.clip(np.stack([np.full((n, n), 0.42), np.full((n, n), 0.50), np.full((n, n), 0.32)], -1), 0, 1)
    alb = np.where(fringe[..., None] > 0.5, grassy * (0.85 + 0.3 * norm01(Hd))[..., None], ad)
    H = np.where(fringe > 0.5, 0.75 + 0.1 * Hd, Hd)
    return H, alb, fringe


def m_planks(F, n, base=(0.66, 0.51, 0.31), wave=0.02):
    rows = 4                                             # vanilla's four boards per block
    row = np.floor(F.v * rows).astype(int)
    in_row = (F.v * rows) % 1.0
    off = np.array([0.0, 0.5, 0.25, 0.75])[row % 4]
    joint_u = (F.u + off) % 1.0
    grain = F.warp(F.noise(2, 48, 0.8, sx=10.0, sy=1.0), 0.0, wave * F.noise(2, 6, 0.5))
    board_tone = (F.rng.random(rows) - 0.5)[row]
    # round 20 (plain game: "no board joints, reads as log"): wider gaps between boards and at their ends
    gap = smoothstep(0.0, 0.06, in_row) * smoothstep(0.0, 0.06, 1 - in_row)
    endgap = smoothstep(0.0, 0.022, np.minimum(joint_u, 1 - joint_u))
    seams = gap * endgap
    H = 0.35 + 0.55 * seams + 0.05 * grain
    nails = ((np.abs(in_row - 0.5) < 0.06) & (np.minimum(np.abs(joint_u - 0.04), np.abs(joint_u - 0.96)) < 0.012))
    H = np.where(nails, H - 0.15, H)
    shade = (0.82 + 0.12 * board_tone + 0.10 * grain) * (0.45 + 0.55 * seams)
    jit = np.stack([0.03 * grain, 0.0 * grain, -0.03 * grain], -1)
    alb = tint(base, shade, jit)
    alb = np.where(nails[..., None], np.array([0.33, 0.31, 0.29]), alb)
    return H, alb


def m_oak_planks_r24(F, n, base=(0.66, 0.51, 0.31)):
    """Oak planks, round 24 (eyes close-up, run 39838783742: m_planks' oak read as cartoon wood - a wavy marbled grain,
    dark outlines round every board, nail dots, orange). Sawn oak boards: a fine STRAIGHT grain (long thin streaks of
    early- and latewood, a ray fleck here and there), each board its own tone and grain, thin dark gaps, the board
    edges a little darker where dirt and wear collect, no nails. Each board sits at its own height and is very
    slightly cupped; the grain lives mostly in the colour, so the light does not emboss it."""
    rows = 4                                             # vanilla's four boards per block
    row = np.floor(F.v * rows).astype(int)
    in_row = (F.v * rows) % 1.0
    off = np.array([0.0, 0.5, 0.25, 0.75])[row % 4]
    joint_u = (F.u + off) % 1.0
    # r25: boards of different lengths - in some rows a second end joint splits the block's board in two
    rng0 = F.rng
    second = np.where(rng0.random(rows) < 0.5, 0.35 + 0.3 * rng0.random(rows), -1.0)[row]
    ju2 = np.where(second > 0, np.abs(joint_u - second), 1.0)
    # the grain: very long along u, thin across v; a hair of waviness; two scales (latewood lines, broad streaks)
    lines = F.warp(F.noise(16, 64, 0.5, sx=30.0, sy=1.0), 0.0, 0.004 * F.noise(2, 6, 0.5))
    streak = F.noise(4, 20, 0.6, sx=12.0, sy=1.0)
    fleck = smoothstep(1.6, 2.4, F.noise(10, 40, 0.3, sx=6.0, sy=1.0))   # the odd ray fleck (quartersawn oak)
    rng = F.rng
    board_tone = (rng.random(rows) - 0.5)[row]
    board_hue = (rng.random(rows) - 0.5)[row]
    gap = smoothstep(0.0, 0.06, in_row) * smoothstep(0.0, 0.06, 1 - in_row)
    endgap = smoothstep(0.0, 0.022, np.minimum(np.minimum(joint_u, 1 - joint_u), ju2))
    seams = gap * endgap
    edge = smoothstep(0.0, 0.22, np.minimum(in_row, 1 - in_row)) \
        * smoothstep(0.0, 0.05, np.minimum(np.minimum(joint_u, 1 - joint_u), ju2))
    cup = 1 - 4 * (in_row - 0.5) ** 2                    # a board very slightly higher in its middle
    # the gap in the HEIGHT is a narrow step, not a rounded bevel: a bevel the full colour-gap wide lit as a moulded
    # plastic panel under the low sun
    hgap = smoothstep(0.0, 0.025, in_row) * smoothstep(0.0, 0.025, 1 - in_row) \
        * smoothstep(0.0, 0.010, np.minimum(np.minimum(joint_u, 1 - joint_u), ju2))
    H = 0.45 + 0.12 * hgap + 0.04 * board_tone + 0.03 * cup + 0.015 * lines
    shade = (0.86 + 0.10 * board_tone + 0.06 * lines + 0.05 * streak + 0.05 * fleck) * (0.90 + 0.10 * edge) \
        * (0.62 + 0.38 * seams)                          # r25: a soft dark gap, not a black line
    jit = np.stack([0.04 * board_hue + 0.015 * streak, 0.01 * board_hue, -0.04 * board_hue - 0.015 * streak], -1)
    return H, tint(base, shade, jit)


def m_spruce_planks(F, n):
    # the darker, redder board with a straight grain (fit_albedo sets the luma): the oak's wave read as marble on the
    # capitol roof (run 38735013028)
    return m_planks(F, n, base=(0.48, 0.35, 0.21), wave=0.006)


def m_leaves(F, n, count, length, width, round_tip=True, droop=0.0):
    """A leaf canopy tile: `count` leaves (length / width in tile units) dropped at random depths and angles; the top
    leaf wins each texel. Returns H, greyscale albedo (the foliage colour tints it in game) and the alpha mask (the
    holes 'fancy' leaves show). Under the holes the colour is a dark leaf shade: 'fast' graphics ignore the alpha and
    draw it (vanilla does the same)."""
    rng = F.rng
    depth = np.full((n, n), -1.0)
    tone = np.zeros((n, n))
    along = np.zeros((n, n))
    across = np.zeros((n, n))
    lit = np.zeros((n, n))
    # round 17 (lovish Oct 20: calmer, more realistic; leaves cover most of the screen): leaves grow on SPRIGS - a
    # twig with a fan of leaves - instead of being scattered one by one. A sprig's middle sits nearest the sun and its
    # rim and the gaps between sprigs fall into shade, so the light and shadow come in clumps a quarter block across
    # (what a canopy looks like from above, and a scale the RTS camera keeps) instead of 150 equal bright blobs on
    # black (on-screen luma std 0.19 - the busiest texture in the pack).
    needles = width < 0.04
    per = 16 if needles else 6
    sprigs = np.array([(rng.random(), rng.random(), rng.random()) for _ in range(max(3, count // per))])
    cell = 1.0 / math.sqrt(len(sprigs))                               # a sprig's reach, about
    g = int(math.ceil(math.sqrt(count)))
    slots = rng.permutation(g * g)[:count]   # count < g*g: the empty slots fall at random, not in one row
    deep = int(count * 0.25)       # a dim under-layer deep in the crown: the gaps show leaves behind, not sky
    for i in range(count + deep):
        if i < count:
            # leaves stratified over the tile (one per grid cell, jittered) so the gaps stay small and even - the
            # first sprig layout put whole sprigs side by side and left bands of sky between them
            c = slots[i]
            cx = ((c % g) + rng.random()) / g
            cy = ((c // g) + rng.random()) / g
            ddx = (cx - sprigs[:, 0] + 0.5) % 1.0 - 0.5
            ddy = (cy - sprigs[:, 1] + 0.5) % 1.0 - 0.5
            dist = np.sqrt(ddx * ddx + ddy * ddy)
            j = int(np.argmin(dist))
            sh = sprigs[j, 2]
            th = math.atan2(ddy[j], ddx[j])                           # leaves fan out from the twig
            spread = 1.2 if needles else 0.7
            ang = th + (rng.random() - 0.5) * 2 * spread
            mid = max(0.0, 1 - dist[j] / cell)                        # the sprig's crown: nearest its twig
            d = 0.35 + 0.40 * sh + 0.25 * mid
            light = 0.60 + 0.18 * sh + 0.22 * mid
        else:                                                         # loose leaves deep in the crown
            cx, cy, ang = rng.random(), rng.random(), rng.random() * math.pi * 2
            d = 0.3 * rng.random()
            light = 0.40
        L = length * (0.7 + 0.6 * rng.random())
        W = width * (0.7 + 0.6 * rng.random())
        dx = (F.u - cx + 0.5) % 1.0 - 0.5
        dy = (F.v - cy + 0.5) % 1.0 - 0.5
        ca, sa = math.cos(ang), math.sin(ang)
        a = (dx * ca + dy * sa) / L                      # -0.5..0.5 along the leaf, base to tip
        c = (-dx * sa + dy * ca) / W                     # -0.5..0.5 across
        shape = 0.5 * np.sin(np.clip(a + 0.5, 0, 1) * math.pi) ** (0.6 if round_tip else 1.2)
        inside = (np.abs(a) <= 0.5) & (np.abs(c) <= shape) & (d > depth)
        depth = np.where(inside, d, depth)
        tone = np.where(inside, rng.random(), tone)
        lit = np.where(inside, light, lit)
        along = np.where(inside, a, along)
        across = np.where(inside, c / np.maximum(shape, 1e-3), across)
    leaf = depth >= 0
    # round 18 (eyes structure run 39536435197: oak and birch tops "covered in dense dark flecks"): the flecks were
    # the many pin-sized gaps between leaves - in game each shows the dark inside of the crown. Gaps smaller than
    # about 1/20 block are filled with leaves deeper in the crown (a little darker, never black); the real gaps stay.
    cover = F.blur(leaf.astype(np.float64), n / 64)
    # round 19: no gaps at all - the few big ones left were the same pits on every block (birch "moth-eaten"); the
    # canopy's depth now comes from the deeper, darker fill leaves, not from holes onto the dark inside of the crown
    keep_gap = np.zeros_like(leaf)
    filled = (~leaf) & ~keep_gap
    alpha = (~keep_gap).astype(np.float64)
    rib = np.exp(-(across / 0.12) ** 2)                  # the midrib, a faint raised line
    dome = 1 - across ** 2                               # each leaf curves up to its midrib
    # relief: each leaf's curve, and depth between leaves at less weight than before (its steps lit up as a dark rim
    # on every leaf edge under shaders); the fills sit low
    H = np.where(leaf, 0.35 + 0.30 * depth + 0.15 * dome + 0.04 * rib - droop * (along + 0.5) ** 2,
                 np.where(filled, 0.32, 0.0))
    H = F.blur(H, n / 256)
    # colour: the sprig's light carries it, blurred to clump size (~1/16 block at any resolution - 1.5 px was ~1/170
    # block at 256x, so each leaf kept its own light and the canopy read as fine noise); a leaf's own tone and its
    # curve are small touches
    soft = F.blur(np.where(leaf, lit, 0.55), n / 40)
    shade = np.where(leaf, 0.36 + 0.62 * soft + 0.04 * (tone - 0.5) + 0.04 * dome + 0.02 * rib
                     - 0.03 * (along + 0.5), 0.36 + 0.62 * soft - 0.06)
    shade = np.where(alpha > 0, shade, 0.35)
    return H, np.clip(np.stack([shade] * 3, -1), 0, 1), alpha


LEAVES = {   # name: (leaves per tile, length, width, round tip) - at 128 texels per block
    "oak_leaves": (150, 0.16, 0.09, True),
    "birch_leaves": (190, 0.12, 0.08, True),
    "spruce_leaves": (800, 0.10, 0.025, False),          # needles, dense (vanilla spruce covers ~3/4)
    "acacia_leaves": (380, 0.11, 0.05, False),
    "jungle_leaves": (80, 0.26, 0.13, True),
    "dark_oak_leaves": (170, 0.17, 0.10, True),
}

# Round 19: a second texture for every leaf, the same recipe on its own seed (reviewer, eyes run 39548166615: "each
# block shows the same pattern, so the block grid is visible on the canopy top" - 16 random turns of ONE texture still
# repeat). The pack's leaves blockstate picks among both textures x 16 turns. A variant is a new texture name (not a
# vanilla one), drawn through its own model (write_models) and listed in LEAVES so it is tinted, cut out, 256x and
# shallow-height like its base.
LEAF_VARIANTS = {base + "_v2": base for base in list(LEAVES)}
for _v, _b in LEAF_VARIANTS.items():
    LEAVES[_v] = LEAVES[_b]
    TARGETS[_v] = dict(TARGETS[_b])
# and the grass top (lovish Oct 21, item 2: grass is the largest area on screen; vanilla's 4 turns of one texture
# repeat across a field): the pack's grass_block blockstate picks 2 textures x 4 turns when not snowy
GRASS_VARIANTS = {"grass_block_top_v2": "grass_block_top"}
TARGETS["grass_block_top_v2"] = dict(TARGETS["grass_block_top"])
GRASS_TOPS = ("grass_block_top", "grass_block_top_v2")
# Oct 21: cherry for the map trees (lovish's cherry references) - without these the cherries drew vanilla's flat
# textures beside the pack. Appended last (SEED_ORDER rule).
TARGETS["cherry_log"] = dict(luma=0.24, std=0.050, sat=0.42, tilt=22.0, smooth=50, pore=30)
TARGETS["cherry_log_top"] = dict(luma=0.42, std=0.040, sat=0.45, tilt=10.0, smooth=45, pore=30)
TARGETS["cherry_leaves"] = dict(luma=0.70, std=0.100, sat=0.30, ao=0.35, tilt=9.0, smooth=60, pore=180)
# Round 25 (reviewer: grass is ~70% of the RTS screen and reads flat - field luma std 0.043, macro 0.022 - while its
# own texture drew camouflage blobs): the blobs go (calmer tufts, std 0.072 -> 0.060) and the field's variation moves
# to the BLOCK scale - four grass tops, two of them a shade darker / lighter (+-3.5% luma), picked at random per block
# by the grass_block blockstate (4 textures x 4 turns). A texture alone cannot vary across several blocks; that would
# take the mod (a BlockColor for grass_block adding a low-frequency world-position noise to the biome tint).
for _g in ("grass_block_top", "grass_block_top_v2"):
    TARGETS[_g] = dict(TARGETS[_g], std=0.060)
TARGETS["grass_block_top_v3"] = dict(TARGETS["grass_block_top"], luma=0.54)
TARGETS["grass_block_top_v4"] = dict(TARGETS["grass_block_top"], luma=0.58)
GRASS_VARIANTS.update({"grass_block_top_v3": "grass_block_top", "grass_block_top_v4": "grass_block_top"})
GRASS_TOPS = GRASS_TOPS + ("grass_block_top_v3", "grass_block_top_v4")


IRON = 230   # LabPBR 1.3's predefined iron (the shader uses iron's own F0, tinted by the albedo)


def m_raw_iron(F, n, cells=13):
    """The metal resource patches: raw iron lumps fused in a rusty crust - rounded nuggets (Voronoi), metal grey where
    they are worn bright, orange-brown rust in the hollows. Returns H, albedo and the per-texel F0 code."""
    f1, f2, idx, k = F.voronoi(cells, 1.0)   # r21: 26 nuggets per block read as red pepper - larger lumps
    wu, wv = 0.01 * F.noise(4, 12, 0.5), 0.01 * F.noise(4, 12, 0.5)
    f1, f2 = F.warp(f1, wu, wv), F.warp(f2, wu, wv)
    lump = smoothstep(0.0, 0.07, f2 - f1)                # 0 in the seams between nuggets
    pits = smoothstep(0.6, 1.0, F.noise(12, 40, 0.3))
    H = 0.2 + 0.7 * np.sqrt(lump) + 0.04 * F.noise(8, 30, 0.4) - 0.08 * pits
    worn = smoothstep(0.55, 0.85, norm01(H) + 0.15 * F.noise(4, 12, 0.5))   # the crests rubbed to bare metal
    rust = np.array([0.62, 0.38, 0.24])
    metal = np.array([0.62, 0.58, 0.55])
    shade = 0.70 + 0.35 * norm01(H) - 0.15 * pits
    alb = (rust[None, None, :] * (1 - worn[..., None]) + metal[None, None, :] * worn[..., None]) * shade[..., None]
    f0 = np.where(worn > 0.5, IRON, 10)
    return H, np.clip(alb, 0, 1), f0


def m_plate(F, n, rgb):
    """A cast block of metal or cut gem: m_gold's plate with another colour (iron, netherite, diamond, emerald,
    lapis)."""
    H, alb = m_gold(F, n)
    L = alb @ np.array([0.2126, 0.7152, 0.0722])
    return H, tint(rgb, L / max(L.mean(), 1e-6))


def m_facets(F, n, rgb, cells, edge=0.010, rim=True):
    """Round 22b (eyes 39589630703: the gems were m_gold's plate recoloured - a worm swirl that read as slime/ceramic;
    obsidian a flat brown cake): cut facets - Voronoi cells, each a shallow pyramid with its own tone, so every facet
    catches the light differently under the shader's normals, and a thin bright edge where two facets meet."""
    f1, f2, idx, k = F.voronoi(cells, 0.9)
    tone = F.rng.uniform(0.70, 1.25, k)[idx]                    # each facet its own value
    slope = F.rng.uniform(0.6, 1.6, k)[idx]
    cell = 1.0 / math.sqrt(cells)
    H = 0.75 - 0.35 * slope * f1 / cell                          # a shallow pyramid per facet
    ridge = 1 - smoothstep(0.0, edge, f2 - f1)                   # where two facets meet
    shade = tone * (1.05 - 0.25 * f1 / cell) + 0.15 * ridge   # a faint edge: brighter read as leaded glass
    if rim:
        b = frame(F, 0.06, 0.012)
        H = H * b
        shade = shade * (0.8 + 0.2 * b)
    return H, tint(rgb, shade)


def m_lapis(F, n):
    """Round 22b: lapis lazuli as a dressed stone - mottled deep ultramarine with paler calcite wisps and sparse gold
    pyrite flecks, a bevelled block rim; matte (smoothness from TARGETS)."""
    mott = F.fbm(3, 4, 0.55)
    wisp = smoothstep(0.75, 0.95, norm01(F.warp(F.noise(2, 6, 0.5), 0.03 * F.noise(3, 9), 0.03 * F.noise(3, 9))))
    shade = 0.85 + 0.25 * norm01(mott)
    alb = tint((0.14, 0.20, 0.55), shade)
    alb = alb * (1 - 0.5 * wisp[..., None]) + 0.5 * wisp[..., None] * np.array([0.55, 0.58, 0.62])   # calcite veins
    fleck = F.noise(40, 90, 0.0) > 2.6
    alb[fleck] = (0.85, 0.70, 0.30)
    b = frame(F, 0.06, 0.012)
    return 0.6 + 0.1 * mott * b, alb * (0.8 + 0.2 * b)[..., None]


def m_gold(F, n):
    """The capitol's gold: a cast and polished plate with a bevelled rim, faint hammer dents and polish scratches."""
    bev = frame(F, 0.07, 0.015)
    dents = F.noise(2, 6, 0.6)                           # broad, soft unevenness of a cast plate
    scratch = np.abs(F.warp(F.noise(20, 60, 0.0, sx=6.0, sy=1.0), 0.0, 0.01 * F.noise(2, 6)))
    sc = 1 - smoothstep(0.0, 0.04, scratch)
    H = 0.35 + 0.5 * bev + 0.012 * dents - 0.01 * sc
    shade = (0.88 + 0.06 * dents - 0.05 * sc) * (0.72 + 0.28 * bev)
    return H, tint((0.98, 0.78, 0.25), shade)


def staves(F, n, count=6):
    """Barrel staves running along v: count boards, a dark seam between them, a little bow and grain."""
    col = np.floor(F.u * count).astype(int)
    inside = (F.u * count) % 1.0
    seam = smoothstep(0.0, 0.06, inside) * smoothstep(0.0, 0.06, 1 - inside)
    bow = 1 - (2 * inside - 1) ** 2                       # each stave curves out a little
    grain = F.noise(2, 40, 0.8, sx=1.0, sy=8.0)
    tone = (F.rng.random(count) - 0.5)[col % count]
    return seam, bow, grain, tone


def m_barrel_side(F, n):
    seam, bow, grain, tone = staves(F, n)
    hoop = (np.abs(F.v - 0.22) < 0.06) | (np.abs(F.v - 0.78) < 0.06)   # two iron hoops
    H = 0.35 + 0.35 * seam + 0.1 * bow + 0.04 * grain
    H = np.where(hoop, 0.85 + 0.03 * F.noise(8, 30, 0.4), H)
    shade = (0.82 + 0.12 * tone + 0.10 * grain) * (0.55 + 0.45 * seam)
    alb = tint((0.47, 0.33, 0.20), shade)
    iron = tint((0.33, 0.32, 0.31), 0.85 + 0.2 * F.noise(8, 30, 0.4))
    return H, np.where(hoop[..., None], iron, alb), np.where(hoop, IRON, 10)


def m_barrel_end(F, n, hole):
    """Barrel top / bottom: boards across the lid inside an iron rim; the top has the bung hole."""
    rows = 4
    row = np.floor(F.v * rows).astype(int)
    inr = (F.v * rows) % 1.0
    gap = smoothstep(0.0, 0.04, inr) * smoothstep(0.0, 0.04, 1 - inr)
    grain = F.noise(2, 40, 0.8, sx=8.0, sy=1.0)
    tone = (F.rng.random(rows) - 0.5)[row]
    H = 0.35 + 0.35 * gap + 0.04 * grain
    shade = (0.82 + 0.12 * tone + 0.10 * grain) * (0.6 + 0.4 * gap)
    alb = tint((0.47, 0.33, 0.20), shade)
    if hole:
        r = np.sqrt((F.u - 0.5) ** 2 + (F.v - 0.5) ** 2)
        bung = r < 0.08
        H = np.where(bung, 0.1, H)
        alb = np.where(bung[..., None], np.array([0.08, 0.06, 0.05]), alb)
    ring = (frame(F, 0.12, 0.10) < 0.9)               # the iron rim, ~1/10 block
    H = np.where(ring, 0.8, H)
    iron = tint((0.33, 0.32, 0.31), 0.85 + 0.2 * F.noise(8, 30, 0.4))
    return H, np.where(ring[..., None], iron, alb), np.where(ring, IRON, 10)


def m_hay_side(F, n):
    strands = F.noise(10, 60, 0.4, sx=1.0, sy=12.0)     # long straws along v
    clump = F.noise(3, 8, 0.6, sx=1.0, sy=4.0)
    band = (np.abs(F.v - 0.25) < 0.045) | (np.abs(F.v - 0.75) < 0.045)   # two cords round the bale
    H = 0.45 + 0.25 * norm01(strands) + 0.1 * clump
    H = np.where(band, H - 0.15, H)
    shade = 0.80 + 0.25 * norm01(strands) + 0.08 * clump
    alb = tint((0.86, 0.70, 0.30), shade)
    cord = tint((0.55, 0.22, 0.14), 0.85 + 0.15 * norm01(strands))
    return H, np.where(band[..., None], cord, alb)


def m_hay_top(F, n):
    f1, f2, idx, k = F.voronoi(400, 1.0)                  # the cut ends of the straws, packed
    end = smoothstep(0.0, 0.012, f2 - f1)
    tone = F.rng.random(k)[idx]
    H = 0.3 + 0.5 * end + 0.05 * tone
    shade = (0.75 + 0.3 * tone) * (0.5 + 0.5 * end)
    return H, tint((0.86, 0.70, 0.30), shade)


def m_birch_log(F, n):
    """Birch bark: pale and papery, dark horizontal lenticels (short dashes across the trunk) and black patches where
    branches fell or the bark cracked."""
    paper = F.fbm(3, 4, 0.5, sx=3.0, sy=1.0)
    dash = np.abs(F.warp(F.noise(6, 20, 0.4, sx=10.0, sy=1.0), 0.0, 0.01 * F.noise(3, 8)))   # long along u: across
    lent = (1 - smoothstep(0.0, 0.05, dash)) * smoothstep(0.3, 0.8, F.noise(8, 24, 0.4))
    patch = smoothstep(1.2, 1.8, F.noise(3, 8, 0.6, sx=2.0, sy=1.0))
    H = 0.6 + 0.08 * paper - 0.08 * lent - 0.05 * patch   # the marks are in the colour, barely in the relief
    shade = 0.92 + 0.08 * paper - 0.55 * lent - 0.75 * patch
    return H, tint((0.86, 0.85, 0.80), shade)


# each wood: (bark colour, end-grain colour) - fit_albedo sets luma / spread / saturation from the targets
WOODS = {
    "spruce": ((0.30, 0.20, 0.12), (0.62, 0.45, 0.28)),
    "acacia": ((0.45, 0.42, 0.38), (0.78, 0.42, 0.20)),
    "jungle": ((0.45, 0.35, 0.18), (0.72, 0.56, 0.36)),
    "dark_oak": ((0.25, 0.18, 0.10), (0.45, 0.30, 0.18)),
    "cherry": ((0.30, 0.16, 0.16), (0.82, 0.58, 0.52)),     # dark red-brown bark, pale pink heartwood
}


# ------------------------------------------------------------------ round 11: the other factions' buildings -------
def m_bricks(F, n, courses, per_course, base, moss=0.0, pits=0.08, tone_amp=0.06, cracks=0.0):
    """Dressed bricks: `courses` rows per block, `per_course` bricks per row, joints offset by half a brick on every
    other row; chipped arrises, a weathered face; `moss` > 0 grows moss in the joints and over some brick edges."""
    course = np.floor(F.v * courses).astype(int)
    in_c = (F.v * courses) % 1.0
    ju = (F.u * per_course + np.where(course % 2 == 0, 0.0, 0.5)) % 1.0
    brick = (course * per_course + np.floor(F.u * per_course + np.where(course % 2 == 0, 0.0, 0.5)).astype(int))
    chip = 0.004 * F.noise(8, 24, 0.5)
    dv = np.minimum(in_c, 1 - in_c) / courses + chip
    du = np.minimum(ju, 1 - ju) / per_course + chip
    # round 20 (reviewer, block sampler runs 39551401507/39554020573: "no brick pattern visible", "salt-and-pepper"):
    # joints 1.5-3 texels at 32 per block (they were under 1 there, so the mipmaps erased them and fit_albedo made up
    # the spread with the face's grain - pepper). The joint now carries the spread; the face stays calm.
    face = smoothstep(0.010, 0.026, np.minimum(dv, du))
    tone = (F.rng.random(64) - 0.5)[brick % 64]
    weather = F.fbm(3, 4, 0.55)
    grain = F.noise(12, 40, 0.3)
    pit = smoothstep(0.75, 0.95, F.noise(10, 30, 0.4))
    H = 0.25 + 0.6 * face + 0.05 * weather * face + 0.02 * grain - pits * pit * face
    shade = np.where(face > 0.2, 0.84 + tone_amp * tone + 0.07 * weather + 0.015 * grain - 0.6 * pits * pit,
                     0.50 + 0.2 * face)
    alb = tint(base, shade)
    if moss > 0:
        # r21: moss grew as fine noise over whole faces and hid the bricks - it now lives in the joints and creeps
        # onto the lower brick edges in a few patches
        m = smoothstep(0.55, 0.85, F.fbm(3, 4, 0.6) * 0.3 + 0.4 + 0.65 * (1 - face)) * moss
        moss_rgb = tint((0.33, 0.45, 0.18), 0.85 + 0.25 * norm01(F.noise(10, 30, 0.3)))
        alb = alb * (1 - m[..., None]) + moss_rgb * m[..., None]
        H = H + 0.08 * m
    if cracks > 0:   # drawn after everything else, so a brick texture without cracks keeps its random draws
        line = np.abs(F.warp(F.noise(2, 7, 0.5), 0.02 * F.noise(4, 12), 0.02 * F.noise(4, 12)))
        crack = (1 - smoothstep(0.0, 0.02, line)) * smoothstep(0.3, 0.7, F.noise(1, 3, 0.5)) * cracks * face
        H = H - 0.3 * crack
        alb = alb * (1 - 0.6 * crack)[..., None]
    return H, alb


def m_polished(F, n, base, mottle=0.10, crystals=0.10, fleck=(20, 56)):
    """A polished stone face (polished blackstone / deepslate): soft mottling, few crystals, a bevelled border."""
    mot = F.fbm(3, 4, 0.55)
    speck = F.noise(16, 48, 0.0)
    flecks = smoothstep(1.3, 2.2, F.noise(fleck[0], fleck[1], 0.0))
    bev = frame(F, 0.06, 0.012)
    H = 0.35 + 0.5 * bev + 0.03 * mot + 0.01 * speck
    shade = (0.88 + mottle * mot + 0.02 * speck - crystals * flecks) * (0.75 + 0.25 * bev)
    return H, tint(base, shade)


def m_tiles(F, n, grid, base, joint=(0.008, 0.022), grout=0.64):
    """Square tiles, grid x grid per block, each a little uneven in tone and set, thin dark joints."""
    cu, cv = np.floor(F.u * grid).astype(int), np.floor(F.v * grid).astype(int)
    iu, iv = (F.u * grid) % 1.0, (F.v * grid) % 1.0
    edge = np.minimum(np.minimum(iu, 1 - iu), np.minimum(iv, 1 - iv)) / grid
    face = smoothstep(joint[0], joint[1], edge)                # joints that survive the mipmaps (round 20)
    tone = (F.rng.random(grid * grid) - 0.5)[(cv * grid + cu) % (grid * grid)]
    tilt = (F.rng.random(grid * grid) - 0.5)[(cv * grid + cu) % (grid * grid)]
    rough = F.noise(10, 40, 0.3)
    # each tile sits at its own height - not tilted along u: one-way slopes read as wooden boards under shaders (r20)
    H = 0.3 + 0.55 * face + 0.04 * tilt * face + 0.02 * rough
    shade = np.where(face > 0.2, 0.84 + 0.10 * tone + 0.05 * rough, grout + 0.2 * face)   # grout, not a black grid
    return H, tint(base, shade)


def m_deepslate(F, n, top=False):
    """Deepslate: dark slate in vertical layers on the side (it forms upright), a tight grain on the top."""
    if top:
        layers = F.fbm(3, 4, 0.6)
    else:
        layers = F.fbm(2, 4, 0.55, sx=1.0, sy=5.0)       # long along v: the upright bedding
    grain = F.noise(12, 40, 0.3, sx=1.0, sy=1.0 if top else 3.0)
    crack = 1 - smoothstep(0.0, 0.03, np.abs(F.warp(F.noise(2, 6, 0.5, sx=1.0, sy=1.0 if top else 4.0),
                                                       0.03 * F.noise(4, 10), 0.03 * F.noise(4, 10))))
    crack *= smoothstep(0.4, 0.9, F.noise(1, 3, 0.5))
    H = 0.6 + 0.10 * layers + 0.03 * grain - 0.2 * crack
    shade = 0.86 + 0.20 * norm01(H) - 0.2 * crack
    return H, tint((0.28, 0.30, 0.35), shade)


def m_copper(F, n, oxidized):
    """Cut copper: four plates per block with seams; bare metal (LabPBR copper, F0 234) or a teal patina that runs in
    streaks and gathers in the seams (dielectric). Returns H, albedo, F0 map."""
    grid = 2
    iu, iv = (F.u * grid) % 1.0, (F.v * grid) % 1.0
    edge = np.minimum(np.minimum(iu, 1 - iu), np.minimum(iv, 1 - iv)) / grid
    face = smoothstep(0.004, 0.02, edge)
    ham = F.noise(4, 14, 0.6)
    scratch = 1 - smoothstep(0.0, 0.04, np.abs(F.noise(20, 60, 0.0, sx=6.0, sy=1.0)))
    H = 0.35 + 0.5 * face + 0.02 * ham - 0.01 * scratch
    if not oxidized:
        shade = (0.86 + 0.08 * ham - 0.04 * scratch) * (0.65 + 0.35 * face)
        return H, tint((0.80, 0.45, 0.30), shade), np.full(H.shape, 234)
    streak = F.fbm(3, 4, 0.6, sx=1.0, sy=4.0)
    shade = (0.85 + 0.12 * streak + 0.04 * ham) * (0.7 + 0.3 * face)
    alb = tint((0.32, 0.64, 0.55), shade)
    alb = np.where((face < 0.3)[..., None], alb * 0.75, alb)
    return H, alb, np.full(H.shape, 10)


def m_netherrack(F, n):
    """Netherrack: a porous, fused, crumbly red rock - lumps and pits, darker veins."""
    # r21: 14-44 pores per block read as high-contrast dark speckle at RTS zoom - softer lump seams, pores
    # gathered into a few pitted patches, so the face reads fleshy and porous rather than noisy
    f1, f2, idx, k = F.voronoi(60, 1.0)
    lump = smoothstep(0.0, 0.08, f2 - f1)              # softer seams (28 lumps read as paving)
    pores = smoothstep(0.55, 0.95, F.noise(8, 24, 0.2)) * smoothstep(0.35, 0.7, norm01(F.fbm(2, 4, 0.5)))
    vein = 1 - smoothstep(0.0, 0.04, np.abs(F.noise(3, 9, 0.5)))
    H = 0.3 + 0.5 * np.sqrt(lump) - 0.18 * pores - 0.1 * vein
    shade = 0.75 + 0.35 * norm01(H) - 0.15 * vein
    return H, tint((0.50, 0.16, 0.14), shade)


def m_moss(F, n):
    """A moss carpet: soft tufts, a few darker hollows (coloured: moss blocks are not biome-tinted)."""
    tufts = F.noise(6, 18, 0.5)
    fine = F.noise(18, 50, 0.1)
    hollow = smoothstep(0.6, 0.95, F.noise(3, 8, 0.5))
    H = 0.5 + 0.2 * tufts + 0.05 * fine - 0.15 * hollow
    shade = 0.85 + 0.15 * norm01(tufts) + 0.05 * fine - 0.15 * hollow
    return H, tint((0.36, 0.48, 0.18), shade)


def m_azalea(F, n, flowering):
    """Azalea leaves: the canopy generator, coloured (not tinted), with pink blossoms on the flowering kind."""
    H, grey, alpha = m_leaves(F, n, 170, 0.14, 0.09, True)
    alb = grey * np.asarray((0.42, 0.62, 0.22))[None, None, :] / 0.7
    if flowering:
        f1, f2, idx, k = F.voronoi(30, 1.0)
        blossom = (f1 < 0.06) & (F.rng.random(k) < 0.7)[idx] & (alpha > 0.5)   # clusters a leaf wide
        petals = tint((0.88, 0.52, 0.78), 0.85 + 0.2 * norm01(F.noise(12, 40, 0.2)))
        alb = np.where(blossom[..., None], petals, alb)
        H = np.where(blossom, H + 0.1, H)
    return H, np.clip(alb, 0, 1), alpha


def m_cherry_leaves(F, n):
    """Cherry blossom (not tinted): the canopy generator's clumps coloured as blossom - pale pink to white petals in
    clusters, deeper pink in the shaded hollows, a few green leaves showing through."""
    H, grey3, alpha = m_leaves(F, n, 220, 0.09, 0.08, True)
    grey = grey3[..., 0]                                             # the canopy's light, one channel
    clump = norm01(F.fbm(3, 6, 0.55))
    pink = np.asarray((0.93, 0.66, 0.78))
    white = np.asarray((0.98, 0.90, 0.93))
    deep = np.asarray((0.80, 0.42, 0.58))
    mix = smoothstep(0.35, 0.8, grey)[..., None]                     # lit petals pale, shaded ones deep pink
    alb = deep * (1 - mix) + (pink * (1 - clump[..., None] * 0.5) + white * clump[..., None] * 0.5) * mix
    # (round green discs among the petals read as polka dots - no green; the depth comes from deep-pink hollows)
    return H, np.clip(alb * (0.62 + 0.5 * grey[..., None]), 0, 1), alpha


# ------------------------------------------------------------------ round 12: the census's rest + other biomes -----
def m_sandstone(F, n, face):
    """Sandstone: the side shows bedding (horizontal bands, a smooth cap band at the top like vanilla's), the top a
    fine smooth sand face, the bottom a rougher grain."""
    grain = F.noise(16, 44, 0.0)
    soft = F.fbm(3, 4, 0.5)
    if face == "side":
        beds = F.fbm(2, 4, 0.55, sx=6.0, sy=1.0)          # long along u: horizontal bedding
        cap = smoothstep(0.24, 0.20, F.v)                    # the smooth cap band (1/5 of the block)
        line = 1 - smoothstep(0.0, 0.012, np.abs(F.v - 0.22 - 0.01 * F.noise(3, 8)))
        H = 0.55 + 0.08 * beds + 0.03 * grain * (1 - cap) + 0.05 * cap - 0.12 * line
        shade = 0.88 + 0.10 * beds + 0.04 * grain + 0.04 * cap - 0.18 * line
    else:
        rough = 0.02 if face == "top" else 0.06
        H = 0.6 + 0.05 * soft + rough * grain
        shade = 0.92 + 0.06 * soft + rough * grain
    return H, tint((0.86, 0.79, 0.58), shade)


def m_clayish(F, n, base, pits=0.04, mottle=0.08):
    """Fired or wet clay faces (terracotta, clay, mud): matte, soft mottling, fine pits."""
    mot = F.fbm(2, 4, 0.55)
    fine = F.noise(14, 40, 0.2)
    pit = smoothstep(0.7, 0.95, F.noise(16, 48, 0.0))
    H = 0.6 + 0.05 * mot + 0.015 * fine - pits * pit
    shade = 0.9 + mottle * mot + 0.03 * fine - 1.5 * pits * pit
    return H, tint(base, shade)


def m_basalt(F, n, top):
    """Polished basalt: vertical columnar flutes on the side; on the top a squared ring pattern of the cut column."""
    if top:
        d = np.maximum(np.abs(F.u - 0.5), np.abs(F.v - 0.5))
        rings = 0.5 + 0.5 * np.cos(d * 2 * math.pi * 6 + 0.6 * F.noise(3, 8))
        bev = frame(F, 0.06, 0.012)
        H = 0.4 + 0.4 * bev + 0.08 * rings
        shade = (0.86 + 0.10 * rings) * (0.75 + 0.25 * bev)
    else:
        flutes = 0.5 + 0.5 * np.cos(F.u * 2 * math.pi * 5 + 0.4 * F.noise(2, 6, 0.5, sx=1.0, sy=6.0))
        streak = F.noise(4, 30, 0.4, sx=1.0, sy=8.0)
        H = 0.45 + 0.25 * flutes + 0.05 * streak
        shade = 0.82 + 0.15 * flutes + 0.06 * streak
    return H, tint((0.36, 0.36, 0.38), shade)


def m_bone(F, n, top):
    """Bone block: long bone grain along the side; the cut end shows the dense rim and the spongy marrow."""
    if top:
        r = np.sqrt((F.u - 0.5) ** 2 + (F.v - 0.5) ** 2)
        marrow = smoothstep(0.30, 0.22, r)
        sponge = smoothstep(0.2, 0.8, F.noise(14, 40, 0.2))
        bev = frame(F, 0.08, 0.015)
        H = 0.5 + 0.3 * bev - 0.15 * marrow * sponge
        shade = (0.92 - 0.25 * marrow * sponge) * (0.8 + 0.2 * bev)
    else:
        grain = F.noise(6, 40, 0.5, sx=1.0, sy=8.0)
        groove = 1 - smoothstep(0.0, 0.05, np.abs(((F.u * 3) % 1.0) - 0.5) * 2 - 0.9)
        H = 0.6 + 0.05 * grain - 0.15 * groove
        shade = 0.92 + 0.05 * grain - 0.15 * groove
    return H, tint((0.90, 0.88, 0.80), shade)


def m_quartz(F, n, kind):
    """Quartz: a polished white stone with faint grey veins - pillar (vertical flutes / ringed top), bricks, the
    smooth block."""
    vein = 1 - smoothstep(0.0, 0.02, np.abs(F.warp(F.noise(2, 6, 0.5), 0.03 * F.noise(3, 9), 0.03 * F.noise(3, 9))))
    vein *= smoothstep(0.3, 0.8, F.noise(1, 3, 0.5))
    if kind == "bricks":
        H, alb = m_bricks(F, n, 2, 2, (0.92, 0.90, 0.86), pits=0.02, tone_amp=0.03)
        return H, alb * (1 - 0.08 * vein)[..., None]
    if kind == "pillar":
        flutes = 0.5 + 0.5 * np.cos(F.u * 2 * math.pi * 4)
        H = 0.5 + 0.2 * flutes - 0.02 * vein
        shade = 0.92 + 0.06 * flutes - 0.08 * vein
    elif kind == "pillar_top":
        bev = frame(F, 0.10, 0.02)
        inner = frame(F, 0.22, 0.17)
        H = 0.4 + 0.3 * bev + 0.2 * inner
        shade = (0.90 - 0.06 * vein) * (0.8 + 0.1 * bev + 0.1 * inner)
    elif kind == "block_side":   # the quartz block's side: a plain dressed face, a soft chamfer, faint upright drift
        bev = frame(F, 0.04, 0.008)
        drift = F.noise(2, 6, 0.5, sx=1.0, sy=6.0)
        H = 0.5 + 0.35 * bev - 0.02 * vein + 0.01 * drift
        shade = (0.95 - 0.08 * vein + 0.015 * drift) * (0.88 + 0.12 * bev)
    else:                          # smooth quartz (quartz_block_bottom) and the quartz block's top: a bevelled slab
        bev = frame(F, 0.05, 0.01) if kind == "smooth" else frame(F, 0.07, 0.014)
        H = 0.45 + 0.4 * bev - 0.02 * vein
        shade = (0.94 - 0.08 * vein) * (0.85 + 0.15 * bev)
    return H, tint((0.92, 0.90, 0.86), shade)


def m_crystal(F, n, minerals, per_side=20, cluster=0.75, contrast=0.55, mottle=0.05, drift_base=2):
    """A crystalline rock (round 17, lovish Oct 20: granite and stone read as "pepper dots", not cohesive): an
    interlocking MOSAIC of crystals - Voronoi cells ~per_side across the block - each one mineral, instead of
    single-texel specks over a flat ground.
    Why the old specks peppered: they were 1-3 texels, so they averaged away at CAL_MIP and fit_albedo scaled the
    whole albedo up to reach the target spread - blowing the specks up into salt and pepper. Here the minority minerals
    CLUSTER (a mid-scale field at ~1/4-1/10 block decides where they gather, as phenocryst groups and dark streaks do in
    real rock), so the spread lives at the scale the mipmaps keep and close up reads as one stone, not noise.
    minerals: [(rgb, fraction), ...], the first is the matrix (the rest are placed by cluster + chance); contrast pulls
    the minority minerals toward the matrix colour (1 = full colour); mottle = soft tone drift across the block.
    Round 19 (plain game: granite and diorite "single-pixel specks", eyes run 39548164601): fit_albedo sets the spread
    at CAL_MIP, so crystals small enough to average out there get scaled up into specks at full size - the spread has
    to live in the mottle (large scale), with smaller, lower-contrast crystals riding on it."""
    f1, f2, idx, k = F.voronoi(per_side * per_side, 1.0)
    rng = F.rng
    # where each crystal sits in the cluster field (sampled at the texel nearest its centre via the cell's own texels)
    field = F.noise(3, 10, 0.6)
    cnt = np.bincount(idx.ravel(), minlength=k).astype(np.float64)
    at = np.bincount(idx.ravel(), weights=field.ravel(), minlength=k) / np.maximum(cnt, 1)
    at = (at - at.mean()) / max(at.std(), 1e-9)
    score = cluster * at + math.sqrt(max(0.0, 1 - cluster * cluster)) * rng.standard_normal(k)
    order = np.argsort(-score)                   # highest score -> the first minority mineral, and so on down
    kind = np.zeros(k, int)
    start = 0
    for m, (_, frac) in enumerate(minerals[1:], 1):
        take = int(round(frac * k))
        kind[order[start:start + take]] = m
        start += take
    base = np.asarray(minerals[0][0], dtype=np.float64)
    cols = np.array([base + (np.asarray(rgb, dtype=np.float64) - base) * (1.0 if m == 0 else contrast)
                     for m, (rgb, _) in enumerate(minerals)])
    tone = 1 + 0.05 * rng.standard_normal(k)     # every crystal its own slight shade (cleavage faces catch light)
    alb = cols[kind][idx] * tone[idx][..., None]
    edge = 1 - smoothstep(0.0, 0.006, f2 - f1)   # grain boundaries: a faint seam, a touch lower
    # drift_base: the mottle's lowest frequency (cycles per block). Granite and diorite take 3 - at 2 one blob per block
    # repeated and drew the block grid on a wall, at 4 granite grew a vermiculate cell network and tuff/andesite went
    # busy (eyes runs 39550900242/39550902490); the rest keep 2
    drift = F.fbm(drift_base, 3, 0.5)
    alb = alb * (1 + mottle * drift - 0.05 * edge)[..., None]
    alb = np.stack([F.blur(alb[..., c], 0.6) for c in range(3)], -1)   # no single-texel steps (they shimmer)
    # relief: a gentle undulation and a fine grit, crystals barely stepped - solve_strength scales the height to the
    # target tilt, so stepped crystals came out as crumpled facets (the first try of this round)
    hgt = 0.008 * rng.standard_normal(k)
    H = 0.6 + hgt[idx] + 0.015 * F.fbm(3, 2, 0.5) - 0.012 * edge + 0.03 * F.noise(24, 128, 1.0)
    return H, np.clip(alb, 0, 1)


def m_igneous(F, n, base, speck_rgb, density, size=(18, 48)):
    """Andesite / tuff / calcite / blackstone / end stone: a ground mass with crystals of another colour - a two-mineral
    m_crystal. density keeps its old meaning (a higher threshold = fewer crystals of the speck colour) and size its
    old band, so each caller's look stays the same in kind, only cohesive."""
    frac = 0.5 * math.erfc((density + 0.3) / math.sqrt(2))
    return m_crystal(F, n, [(base, 1 - frac), (speck_rgb, frac)], per_side=int(round(0.55 * (size[0] + size[1]) / 2)))


def m_granite(F, n):
    """Granite: salmon feldspar, smoky quartz, a little dark mica in clustered flecks, a few pale plagioclase."""
    return m_crystal(F, n, [((0.62, 0.43, 0.36), 0.56), ((0.36, 0.31, 0.29), 0.08), ((0.50, 0.47, 0.45), 0.24),
                            ((0.74, 0.62, 0.56), 0.12)], per_side=30, cluster=0.75, contrast=0.4, mottle=0.14,
                     drift_base=3)


def m_diorite(F, n):
    """Diorite: white plagioclase with dark hornblende gathered in flecks and streaks (not even pepper)."""
    # round 19b: the dark minerals back to clear flecks (contrast 0.5 -> 0.7, spread out - cluster 0.45): under shaders
    # diorite washed out into pale stone; it reads as diorite by its black-and-white fleck
    return m_crystal(F, n, [((0.84, 0.84, 0.82), 0.72), ((0.30, 0.30, 0.31), 0.16), ((0.66, 0.66, 0.65), 0.12)],
                     per_side=34, cluster=0.45, contrast=0.7, mottle=0.06, drift_base=3)


def m_snow(F, n):
    """Snow: soft drifts, a fine crystalline glint, almost white."""
    drift = F.fbm(2, 4, 0.6)
    glint = smoothstep(1.6, 2.4, F.noise(24, 60, 0.0))
    H = 0.6 + 0.08 * drift + 0.01 * glint
    shade = 0.95 + 0.04 * drift + 0.05 * glint
    return H, tint((0.94, 0.96, 1.0), shade)


def m_podzol(F, n):
    """Podzol: a forest floor - dark soil under fallen needles and leaf bits."""
    Hd, ad = m_dirt(F, n)
    needles = grass_blades(F, int(4 * n), n / 20, 1)
    leaf = smoothstep(0.6, 0.95, F.noise(6, 16, 0.4))
    H = Hd * 0.7 + 0.25 * needles + 0.1 * leaf
    litter = tint((0.45, 0.30, 0.16), 0.8 + 0.3 * norm01(needles))
    m = np.clip(0.8 * needles + 0.6 * leaf, 0, 1)
    return H, ad * (1 - m[..., None]) + litter * m[..., None]


def m_coarse_dirt(F, n):
    """Coarse dirt: dirt with gravel worked through it."""
    Hd, ad = m_dirt(F, n)
    # r21 (reviewer, plain lane: "bright single-pixel pebbles on dark brown, static at RTS"): 200 grey cells per block
    # were 1-2 texels each - fewer, larger stones in an earthy grey-brown close to the dirt's own tone
    f1, f2, idx, k = F.voronoi(70, 1.0)
    stone = (F.rng.random(k) < 0.22)[idx] & (f2 - f1 > 0.012)
    dome = smoothstep(0.0, 0.05, f2 - f1)                         # each stone rounded: lit on top, dark at its rim
    H = np.where(stone, Hd + 0.15 * dome, Hd)
    grey = tint((0.40, 0.37, 0.33), (0.65 + 0.35 * dome) * (0.85 + 0.2 * F.rng.random(k)[idx]))
    half = (f2 - f1 > 0.004) & ~stone & (F.rng.random(k) < 0.22)[idx]   # stones half sunk: a soil-stained rim
    return H, np.where(stone[..., None], grey, np.where(half[..., None], ad * 0.85, ad))


# ------------------------------------------------------------------ round 13: the census's long tail ------------
def m_stripped(F, n, base):
    """A stripped log: the bare wood under the bark - fine grain running along the trunk (v), soft growth streaks and
    the odd knot."""
    grain = F.warp(F.noise(4, 60, 0.7, sx=40.0, sy=1.0).T, 0.005 * F.noise(2, 6, 0.5), 0.0)   # near-straight
    streak = F.fbm(2, 4, 0.5, sx=1.0, sy=6.0)
    f1, f2, idx, k = F.voronoi(6, 1.0)
    knot = smoothstep(0.035, 0.0, f1) * (F.rng.random(k) < 0.4)[idx]
    H = 0.55 + 0.05 * grain + 0.05 * streak - 0.2 * knot
    shade = 0.86 + 0.10 * grain + 0.06 * streak - 0.35 * knot
    return H, tint(base, shade)


def m_chiseled(F, n, base=None):
    """Chiseled stone bricks: a dressed block with a carved square ring and a boss in the middle (base: another
    stone's colour - chiseled nether bricks, chiseled polished blackstone)."""
    Hs, als = m_stone(F, n)
    if base is not None:
        L = als @ np.array([0.2126, 0.7152, 0.0722])
        als = tint(base, L / max(L.mean(), 1e-6))
    d = np.maximum(np.abs(F.u - 0.5), np.abs(F.v - 0.5))
    groove = (1 - smoothstep(0.0, 0.02, np.abs(d - 0.30))) + (1 - smoothstep(0.0, 0.015, np.abs(d - 0.12)))
    bev = frame(F, 0.07, 0.015)
    H = 0.3 * Hs + 0.5 * bev - 0.25 * groove
    return H, als * ((0.75 + 0.25 * bev) * (1 - 0.35 * np.clip(groove, 0, 1)))[..., None]


def m_path(F, n, side):
    """Dirt path: trodden, compacted dirt - flatter and lighter than dirt, a few pressed-in pebbles; the side shows
    the path's edge over plain dirt (the block is 15/16 tall)."""
    Hd, ad = m_dirt(F, n)
    packed = F.noise(3, 10, 0.5)
    Hp = 0.5 + 0.35 * (Hd - 0.55) + 0.04 * packed
    ap = np.clip(ad * 1.12 + 0.03, 0, 1)
    if not side:
        return Hp, ap
    band = F.v < (0.19 + 0.02 * F.noise(4, 12, 0.5)[0:1, :])
    return np.where(band, Hp + 0.1, Hd), np.where(band[..., None], ap, ad)


# ------------------------------------------------------------------ round 14: the structure preview's gaps ---------
def m_reinforced(F, n, top):
    """Reinforced deepslate: a dark slab held in a pale, bone-like frame (bars across on the side, a ring on top)."""
    H, alb = m_polished(F, n, (0.25, 0.26, 0.28), 0.06, 0.03)
    if top:
        bar = (frame(F, 0.16, 0.12) < 0.9) & (frame(F, 0.04, 0.01) > 0.5)
    else:
        bar = (np.abs(F.v - 0.18) < 0.06) | (np.abs(F.v - 0.82) < 0.06) | (np.abs(F.u - 0.5) < 0.05)
    bone = tint((0.80, 0.82, 0.76), 0.85 + 0.15 * norm01(F.noise(8, 30, 0.4)))
    return np.where(bar, H + 0.25, H), np.where(bar[..., None], bone, alb)


def m_basalt_rough(F, n, top):
    """Basalt: rough columns - vertical ridges and cracks on the side, a jointed polygon top."""
    if top:
        f1, f2, idx, k = F.voronoi(5, 0.8)
        joint = 1 - smoothstep(0.0, 0.03, f2 - f1)
        H = 0.55 + 0.05 * F.noise(8, 30, 0.4) - 0.3 * joint
        shade = 0.88 + 0.06 * F.noise(8, 30, 0.4) - 0.4 * joint
    else:
        ridges = F.noise(3, 14, 0.6, sx=1.0, sy=10.0)
        H = 0.5 + 0.15 * ridges + 0.03 * F.noise(16, 40, 0.2)
        shade = 0.82 + 0.15 * norm01(ridges)
    return H, tint((0.32, 0.32, 0.34), shade)


def m_basalt_side_r24(F, n):
    """Basalt's side, round 24/25. Main's m_basalt_rough side lit as folded foil; r24's granular rewrite read as TV
    static in the plain game (Laplacian 6.7 -> 39). Real basalt: COLUMNS - two or three column faces across the
    block, each a flat facet of its own tone, split by straight vertical joints, the odd horizontal cross-joint within
    a column; dark blue-grey, fine-grained (smooth at this scale), low-contrast small vesicles."""
    rng = F.rng
    k = 3
    edges = np.sort((np.arange(k) + 0.5 + 0.35 * (rng.random(k) - 0.5)) / k)     # the joints' u positions
    wav = 0.006 * F.noise(2, 6, 0.6, sx=4.0, sy=1.0)                                 # joints nearly straight
    uu = (F.u + wav) % 1.0
    d = np.min(np.stack([np.minimum(np.abs(uu - e), 1 - np.abs(uu - e)) for e in edges]), 0)
    col = np.searchsorted(edges, uu) % k                                            # which column face
    tone = (rng.random(k) - 0.5)[col]
    lean = (rng.random(k) - 0.5)[col]
    joint = 1 - smoothstep(0.0, 0.011, d)
    cross = np.zeros((n, n))
    for c in range(k):                                  # one horizontal cross-joint in some columns
        if rng.random() < 0.6:
            v0 = rng.random()
            dv = np.minimum(np.abs(F.v - v0), 1 - np.abs(F.v - v0))
            cross = np.maximum(cross, (col == c) * (1 - smoothstep(0.0, 0.012, dv)))
    ves = F.blur(smoothstep(1.8, 2.6, F.noise(10, 32, 0.0)), 0.8)                   # small soft vesicles
    weather = F.fbm(2, 3, 0.6)
    H = 0.6 + 0.04 * tone + 0.03 * lean * (F.u - 0.5) - 0.10 * joint - 0.06 * cross - 0.05 * ves + 0.01 * weather
    shade = 1.0 + 0.06 * tone + 0.03 * weather - 0.18 * joint - 0.12 * cross - 0.04 * ves
    return H, tint((0.29, 0.31, 0.35), shade)

def m_soul_sand(F, n):
    """Soul sand: dark, clumped grains with deep hollows."""
    H, alb = m_sand(F, n, (0.36, 0.27, 0.21))
    hollow = smoothstep(0.7, 1.0, F.noise(5, 12, 0.5))
    return H - 0.25 * hollow, alb * (1 - 0.45 * hollow)[..., None]


def m_podzol_side(F, n):
    """Podzol side: dirt with the litter's edge on top (as the grass side has its fringe)."""
    Hd, ad = m_dirt(F, n)
    band = F.v < (0.15 + 0.04 * F.noise(4, 12, 0.5)[0:1, :])
    litter = tint((0.42, 0.28, 0.15), 0.8 + 0.3 * norm01(F.noise(10, 30, 0.3)))
    return np.where(band, Hd + 0.1, Hd), np.where(band[..., None], litter, ad)


def frame(F, width, groove):
    """A border bevel round the block (cut / polished stone): 1 inside, falling to 0 in a groove at the edge, so tiled
    blocks read as separate stones. width and groove in tile units."""
    d = np.minimum(np.minimum(F.u, 1 - F.u), np.minimum(F.v, 1 - F.v))
    return smoothstep(0.0, groove, d) * (0.85 + 0.15 * smoothstep(groove, width, d))


def m_stone_bricks(F, n):
    # two courses per block, joints offset by half a brick (as vanilla): big dressed blocks, chipped edges, a
    # weathered face - the capitol's walls and stairs
    course = np.floor(F.v * 2).astype(int)
    in_c = (F.v * 2) % 1.0
    ju = (F.u + np.where(course % 2 == 0, 0.0, 0.5)) % 1.0
    chip = 0.004 * F.noise(8, 24, 0.5)                    # ragged joints
    dv = np.minimum(in_c, 1 - in_c) / 2 + chip           # distance to a bed joint (tile units)
    du = np.minimum(ju, 1 - ju) + chip                   # to a head joint
    # joints wide enough to survive the mipmaps (round 20, see m_bricks)
    face = smoothstep(0.010, 0.028, np.minimum(dv, du))  # 0 in the mortar, 1 on the face (a rounded arris)
    # small tone steps between the bricks: the texture repeats on every block (stone bricks are never turned), so one
    # darker brick became a lattice across a wall (rts_view)
    tone = (F.rng.random(4) - 0.5)[(course * 2 + (ju > 0.5)) % 4]
    weather = F.fbm(3, 4, 0.55)
    grain = F.noise(12, 40, 0.3)
    pits = smoothstep(0.75, 0.95, F.noise(10, 30, 0.4))
    H = 0.25 + 0.6 * face + 0.05 * weather * face + 0.02 * grain - 0.08 * pits * face
    shade = np.where(face > 0.2, (0.84 + 0.06 * tone + 0.07 * weather + 0.015 * grain - 0.06 * pits), 0.60 + 0.2 * face)
    hue = F.noise(2, 5, 0.5)
    jit = np.stack([0.02 * hue, 0.0 * hue, -0.02 * hue], -1)
    return H, tint((0.50, 0.50, 0.49), shade, jit)


def m_polished_andesite(F, n):
    # a polished face: soft mottling and speckle (the andesite's crystals), almost no relief, a bevelled border
    mottle = F.fbm(3, 4, 0.55)
    speck = F.noise(16, 48, 0.0)
    flecks = smoothstep(1.3, 2.2, F.noise(20, 56, 0.0))   # sparse dark crystals
    bev = frame(F, 0.06, 0.012)
    H = 0.35 + 0.5 * bev + 0.03 * mottle + 0.01 * speck
    # few, faint crystals: with more the polished steps read as rough granite from the RTS camera (run 38735013028)
    shade = (0.88 + 0.10 * mottle + 0.02 * speck - 0.10 * flecks) * (0.75 + 0.25 * bev)
    jit = np.stack([0.015 * mottle, 0.0 * mottle, -0.01 * mottle], -1)
    return H, tint((0.53, 0.53, 0.52), shade, jit)


def m_smooth_stone(F, n):
    # sawn limestone-like slab face: very flat, faint clouding, a thin groove at the border
    # the reference's relief (17.7 deg at 32 texels per block) is a sawn, granular face, not soft bumps: the tilt comes
    # from grain at 1/20-1/6 block and a few pores, the clouding stays in the colour
    cloud = F.fbm(2, 4, 0.6)
    grain = F.noise(6, 20, 0.4)                          # 1/20-1/6 block: survives the mipmaps (finer read as pitting)
    pores = smoothstep(1.4, 2.4, F.noise(16, 48, 0.0))
    bev = frame(F, 0.04, 0.010)
    H = 0.4 + 0.45 * bev + 0.01 * cloud + 0.03 * grain - 0.03 * pores
    # r21: under the low warm sun 0.63 grey read as cream next to quartz - darker, a cool cast, the clouding carries the
    # spread (a large-scale tone shift) rather than grain
    shade = (0.92 + 0.07 * cloud + 0.02 * grain - 0.08 * pores) * (0.72 + 0.28 * bev)
    return H, tint((0.55, 0.57, 0.61), shade)


def m_smooth_stone_slab_side(F, n):
    # the side of a smooth stone slab block: two slabs (top and bottom halves), each with its own bordered face
    H, alb = m_smooth_stone(F, n)
    seam = smoothstep(0.0, 0.010, np.abs(F.v - 0.5))
    H = H * (0.6 + 0.4 * seam)
    alb = alb * (0.72 + 0.28 * seam)[..., None]
    return H, alb


# bark styles (round 17): (plates across the block, plate length / width, furrow width in tile units, cross-crack
# chance, fibre strength) - oak the ridged default; each wood its own habit
BARK = {
    "oak": (8, 5.0, 0.030, 0.12, 0.8),          # long interlaced ridges, wide furrows
    "spruce": (12, 2.0, 0.010, 0.10, 0.4),      # small scaly flakes, shallow
    "acacia": (9, 6.0, 0.020, 0.08, 1.0),       # long fibrous strips
    "jungle": (8, 4.5, 0.018, 0.08, 0.8),       # smooth-ish plates, clear vertical seams (0.012 read flat, r19)
    "dark_oak": (7, 3.0, 0.036, 0.30, 0.6),     # blocky, deep, cracked across
    "cherry": (9, 4.0, 0.014, 0.06, 0.5),       # smoothish plates, fine seams (heavy cross-cracks read as swirl blobs)
}


def m_bark(F, n, base, style="oak"):
    """Bark as PLATES split by furrows (round 17, lovish Oct 20: leaves and bark cover most of the screen and should
    read calm and real): Voronoi cells stretched up the trunk are the plates, the space between them the furrows,
    and the whole pattern is warped so the furrows meander and the plates interlace instead of tiling like scales;
    plates split across now and then; a fine vertical fibre on the plates. The old bark was warped stripes that
    swirled like marble from the RTS camera. The colour follows the relief - furrows dark (shadowed, damp), plate faces
    lighter and greyed by weather - so the light a shader adds agrees with the paint instead of embossing it."""
    per_side, stretch, furrow_w, cross_p, fibre = BARK[style]
    rows = max(1, int(round(per_side / stretch)))
    # jitter 0.7: at full jitter two neighbouring points can land almost together, and the sliver cell between them
    # drew a closed ring of furrow - a dark dot on the plate
    f1, f2, idx, k = F.voronoi(0, 0.7, aniso=(1.0, 1.0 / stretch), grid=(per_side, rows))
    # meander: sample the plate pattern through a smooth warp (mostly sideways - furrows wander left and right)
    du = 0.35 / per_side * F.noise(2, 6, 0.6, sx=1.0, sy=2.0)
    dv = 0.10 / per_side * F.noise(2, 6, 0.6)
    xi = np.round((F.u + du) * n).astype(int) % n
    yi = np.round((F.v + dv) * n).astype(int) % n
    gap, idx = (f2 - f1)[yi, xi], idx[yi, xi]
    wobble = np.clip(1.0 + 0.3 * F.noise(6, 18, 0.5), 0.5, 1.5)          # furrows widen and narrow (kept > 0:
    furrow = 1 - smoothstep(0.0, furrow_w * wobble, gap)                  # a zero width flooded plates with furrow)
    rng = F.rng
    cut = rng.random(k) < cross_p                                         # some plates break across
    yph = rng.random(k)
    across = np.abs(((F.v * rows + yph[idx]) % 1.0) - 0.5)
    crack = cut[idx] * (1 - smoothstep(0.0, 0.05, across)) * smoothstep(0.004, 0.03, gap)
    crack = F.blur(crack, 0.7)
    plate_h = 0.06 * rng.standard_normal(k)                               # plates stand at their own heights
    fib = F.noise(10, 48, 0.4, sx=1.0, sy=8.0)                          # long along v: the fibre runs up the trunk
    ridge = smoothstep(0.0, 2.5 * furrow_w, gap)                          # each plate rounded like a ridge
    H = 0.55 + plate_h[idx] + 0.25 * ridge - 0.35 * furrow - 0.20 * crack + 0.05 * fibre * fib
    # colour from the relief, softly: furrows deep brown, faces lighter and a touch grey (weathered)
    face = np.clip(ridge * (1 - crack), 0, 1)
    tone = 1 + 0.04 * rng.standard_normal(k)
    shade = (0.76 + 0.24 * face) * tone[idx] + 0.035 * fibre * fib - 0.12 * furrow
    rgb = np.asarray(base, dtype=np.float64)
    grey = rgb.mean()
    weathered = rgb + (grey - rgb) * 0.25                                 # sun and rain grey the plate faces
    col = rgb[None, None, :] * (1 - face[..., None]) + weathered[None, None, :] * face[..., None]
    lichen = F.blur(smoothstep(0.9, 1.6, F.noise(3, 9, 0.6)) * face, 1.5) * 0.4   # a few pale-green lichen patches
    col = col * (1 - lichen[..., None]) + np.array([0.52, 0.55, 0.42])[None, None, :] * grey / 0.5 * lichen[..., None]
    alb = np.clip(col * shade[..., None], 0, 1)
    alb = np.stack([F.blur(alb[..., c], 0.5) for c in range(3)], -1)
    return H, alb


def m_oak_bark_r24(F, n, base=(0.42, 0.33, 0.21)):
    """Oak bark, round 24 (eyes close-up, run 39838783742: m_bark's Voronoi plates read as carved flames - lens-shaped
    plates domed to a point, dark V tips between them; a quieter version read as quilted leather). Mature oak bark is
    long rough RIDGES split by deep, meandering furrows that run up the trunk and join and part again. Those furrows are
    the zero-crossings of a noise stretched up the trunk: lines that wander, fork and merge on their own, at no fixed
    spacing - no cell shapes at all. Ridge tops are flat and rough (scaly, with short horizontal checks), the furrows
    dark and damp; sun and rain grey the ridge faces."""
    warp = 0.02 * F.noise(2, 6, 0.6)
    field = F.warp(F.noise(2, 8, 0.9, sx=1.0, sy=6.0), warp, 0.0)    # long along v (up the trunk)
    gy = (np.roll(field, -1, 0) - np.roll(field, 1, 0)) / 2
    gx = (np.roll(field, -1, 1) - np.roll(field, 1, 1)) / 2
    a = np.abs(field) / np.maximum(np.sqrt(gx * gx + gy * gy), 0.02)   # ~distance to the furrow line, in texels
    # r25: furrow width also drifts across the whole block (wide, deep runs and tight ones) - the ridges were uniform
    w = (n / 40) * np.clip(1.0 + 0.5 * F.noise(4, 16, 0.5) + 0.45 * F.noise(1, 3, 0.8), 0.4, 2.0)
    furrow = 1 - smoothstep(0.0, w, a)                                 # dark, deep
    ridge = smoothstep(0.6 * w, 2.2 * w, a)                            # flat-topped ridges, a rounded shoulder
    breaks = (1 - smoothstep(0.0, 0.12, np.abs(F.noise(2, 8, 0.6, sx=4.0, sy=1.0)))) \
        * smoothstep(0.5, 1.2, F.noise(3, 10, 0.5))                    # a ridge broken across now and then
    breaks = F.blur(breaks * ridge, 0.8)
    scale = F.noise(10, 48, 0.4)                                       # scaly roughness on the ridge tops
    checks = smoothstep(0.7, 1.5, F.noise(8, 40, 0.5, sx=1.0, sy=0.3))  # short checks ACROSS the ridges
    fib = F.noise(10, 48, 0.4, sx=1.0, sy=8.0)
    H = 0.30 + 0.45 * ridge - 0.20 * furrow + 0.06 * scale * ridge - 0.10 * checks * ridge - 0.25 * breaks \
        + 0.02 * fib
    rgb = np.asarray(base, dtype=np.float64)
    grey = rgb.mean()
    weathered = rgb + (grey - rgb) * 0.30 + 0.03                       # the faces sun-greyed (r25: less - too grey)
    face = np.clip(ridge * (1 - breaks), 0, 1)
    col = rgb[None, None, :] * (1 - face[..., None]) + weathered[None, None, :] * face[..., None]
    lichen = F.blur(smoothstep(0.9, 1.6, F.noise(3, 9, 0.6)) * face, 1.5) * 0.35
    col = col * (1 - lichen[..., None]) + np.array([0.52, 0.55, 0.42])[None, None, :] * grey / 0.5 * lichen[..., None]
    shade = (0.62 + 0.38 * face) * (1 + 0.06 * scale * face - 0.10 * checks * face + 0.03 * fib) - 0.10 * furrow
    alb = np.clip(col * shade[..., None], 0, 1)
    return H, alb


def m_log(F, n, base=(0.42, 0.33, 0.21), style="oak"):
    return m_bark(F, n, base, style)


def m_log_top(F, n, wood=(0.70, 0.56, 0.36), bark_rgb=(0.42, 0.33, 0.21), rim=(0.5, 0.2)):
    """A log's end grain: growth rings and radial checks inside a square border of bark (bark_rgb). rim = the
    border's (height, relief): rough bark by default; a stripped log's bare-wood rim is nearly flat."""
    du, dv = F.u - 0.5, F.v - 0.5
    r = np.sqrt(du * du + dv * dv)
    r_w = r + 0.012 * F.noise(3, 10, 0.5)
    rings = 0.5 + 0.5 * np.cos(r_w * 2 * math.pi * 14)
    bark = smoothstep(0.43, 0.47, np.maximum(np.abs(du), np.abs(dv)))   # the square bark border, ~1/16 block
    checks = 1 - smoothstep(0.0, 0.02, np.abs((np.arctan2(dv, du) * 3 / math.pi) % 1.0 - 0.5) * r * 4 + (1 - smoothstep(0.1, 0.35, r)))
    H = 0.6 + 0.08 * rings - 0.15 * checks * (r < 0.4) + 0.05 * F.noise(20, 60, 0.3)
    H = np.where(bark > 0.5, rim[0] + rim[1] * F.noise(8, 30, 0.5), H)
    shade = 0.82 + 0.25 * rings
    alb = tint(wood, shade)
    bark_alb = tint(bark_rgb, 0.75 + 0.4 * norm01(H))
    alb = np.where(bark[..., None] > 0.5, bark_alb, alb)
    return H, alb


# ------------------------------------------------------------------ round 15: the face census's partial blocks ---
def m_farmland(F, n, moist):
    """Farmland: tilled dirt - four furrows across the block (vanilla's rows), crumbs on the ridges; the dry field
    lighter with hairline cracks, the moist one darker and closer (its target)."""
    Hd, ad = m_dirt(F, n)
    ph = (F.v * 4 + 0.05 * F.noise(2, 6, 0.5)) % 1.0
    ridge = np.sin(math.pi * ph) ** 2                    # 0 in a furrow, 1 on a ridge
    crumbs = F.noise(16, 50, 0.3)
    H = 0.25 + 0.40 * ridge + 0.30 * norm01(Hd) + 0.04 * crumbs
    shade = 0.80 + 0.24 * ridge                          # rows, not corduroy, at 36 px per block
    if not moist:
        crack = 1 - smoothstep(0.0, 0.02, np.abs(F.warp(F.noise(3, 9, 0.5), 0.02 * F.noise(4, 10), 0.02 * F.noise(4, 10))))
        crack *= smoothstep(0.3, 0.8, F.noise(2, 5, 0.5)) * ridge
        H = H - 0.12 * crack
        shade = shade - 0.25 * crack
    return H, np.clip(ad * shade[..., None], 0, 1)


def m_grass_snow_side(F, n):
    """The snowy grass block's side: our dirt under a band of snow ~1/4 block deep, its lower edge a soft drift (a
    spiky edge read as white grass blades), a little shadow under the snow's lip. Returns H, the dirt albedo, the
    snow albedo and the band (the recipe fits each to its own block)."""
    Hd, ad = m_dirt(F, n)
    Hs, asn = m_snow(F, n)
    depth = 0.22 + 0.03 * F.noise(1, 4, 1.5)[0:1, :]           # a few soft drifts, no teeth
    band = F.v < depth
    edge = smoothstep(0.0, 0.03, depth - F.v)            # the snow thins to its edge
    lip = 1 - 0.15 * smoothstep(0.04, 0.0, F.v - depth) * (F.v >= depth)
    H = np.where(band, 0.62 + 0.25 * edge + 0.05 * Hs, Hd)
    return H, ad * lip[..., None], asn, band


def m_stem(F, n, base, vein):
    """A nether stem's side: a fungus stalk in upright fibres - the log's ridges, with the bright inner flesh (vein)
    showing in the fissures."""
    ridges = 1 - np.abs(F.warp(F.noise(6, 24, 0.5, sx=1.0, sy=12.0), 0.03 * F.noise(2, 6), 0.0))
    fiss = 1 - smoothstep(0.0, 0.10, np.abs(F.warp(F.noise(5, 12, 0.5, sx=1.0, sy=10.0), 0.02 * F.noise(3, 8), 0.0)))
    H = 0.45 + 0.30 * ridges ** 2 - 0.25 * fiss + 0.04 * F.noise(16, 40, 0.2, sx=1.0, sy=4.0)
    alb = tint(base, 0.75 + 0.4 * norm01(H))
    flesh = tint(vein, 0.9 + 0.2 * F.noise(8, 30, 0.4))
    return H, alb * (1 - fiss[..., None]) + flesh * fiss[..., None]


# ------------------------------------------------------------------ round 16: cut-out and see-through blocks ------
STAINED = {"red": (0.62, 0.12, 0.10), "cyan": (0.10, 0.48, 0.55)}   # the stained glass the buildings use


def periodic_path(F, along, count=3, amp=0.06):
    """A wavy line across the tile: an offset (tile units) as a function of `along` (u or v), periodic - a sum of
    sines with whole frequencies, so a strand leaving one edge comes back in at the other."""
    off = np.zeros_like(along)
    for k in range(1, count + 1):
        off += (amp / k) * np.sin(2 * math.pi * (k * along + F.rng.random()))
    return off


def m_roots(F, n, top):
    """Mangrove roots: a tangle of woody roots with gaps (cut-out). The side (two crossed planes in the model) shows
    them running mostly down, the top a mat in both directions. Each root is a wavy strand with a round section and
    bark fibres; the top strand wins each texel."""
    depth = np.full((n, n), -1.0)
    across, tone = np.zeros((n, n)), np.zeros((n, n))
    for _ in range(10 if top else 8):
        vertical = F.rng.random() < (0.5 if top else 0.75)
        a, b = (F.u, F.v) if vertical else (F.v, F.u)          # a: across the strand, b: along it
        path = F.rng.random() + periodic_path(F, b, 3, 0.10)
        w = 0.030 + 0.030 * F.rng.random()                      # half width: 1/30-1/16 block
        d = (a - path + 0.5) % 1.0 - 0.5
        dd = F.rng.random()
        inside = (np.abs(d) < w) & (dd > depth)
        depth = np.where(inside, dd, depth)
        across = np.where(inside, d / w, across)
        tone = np.where(inside, F.rng.random(), tone)
    alpha = (depth >= 0).astype(np.float64)
    dome = 1 - across ** 2
    fibre = F.noise(8, 40, 0.5)
    H = np.where(alpha > 0, 0.3 + 0.4 * depth + 0.25 * dome + 0.03 * fibre, 0.0)
    shade = np.where(alpha > 0, 0.62 + 0.22 * depth + 0.15 * dome + 0.10 * (tone - 0.5) + 0.04 * fibre, 0.3)
    return dict(H=H, alb=tint((0.36, 0.26, 0.21), shade), alpha=alpha)


def m_glass(F, n, rgb, kind):
    """Glass: a pane with a thin bevelled edge and a few faint diagonal glints. kind 'clear' = the plain glass block
    (vanilla draws it CUT-OUT: alpha 0 or 1, so only the edge and the glints show), 'stained' and 'tinted' are
    translucent (the alpha blends: stained a coloured veil, tinted nearly opaque and dark). 'edge' = a pane's top
    strip (glass_pane_top: the edge of the sheet seen end-on - all glass, opaque)."""
    uc, vc = F.u + 0.5 / n, F.v + 0.5 / n                      # texel centres: the same rim width on every side
    d = np.minimum(np.minimum(uc, 1 - uc), np.minimum(vc, 1 - vc))
    rim = 1 - smoothstep(0.03, 0.045, d)                       # ~1/25 block
    g = ((F.u + F.v) * 1.0 + 0.15 * F.noise(1, 3, 0.5) * 0.1) % 1.0
    glint = (1 - smoothstep(0.004, 0.012, np.abs(g - 0.30))) + 0.6 * (1 - smoothstep(0.003, 0.009, np.abs(g - 0.36)))
    glint *= smoothstep(0.2, 0.6, F.noise(1, 3, 0.5) * 0.5 + 0.5) * (1 - rim)
    smudge = F.noise(2, 8, 0.6)
    base = np.asarray(rgb, dtype=np.float64)
    if kind == "edge":
        shade = 0.9 + 0.05 * F.noise(2, 8, 0.6, sx=1.0, sy=6.0)   # streaks along v: the model maps column u 7-9
        return dict(H=0.5 + 0.02 * smudge, alb=tint(base * 0.85, shade))
    alb = tint(base, 0.95 + 0.03 * smudge)
    alb = alb * (1 - 0.25 * rim[..., None]) + 0.25 * rim[..., None] * (base * 0.6)   # the edge reads darker
    alb = np.clip(alb + 0.35 * glint[..., None], 0, 1)
    if kind == "clear":
        alpha = ((rim > 0.5) | (glint > 0.35)).astype(np.float64)
    elif kind == "stained":
        alpha = np.clip(0.42 + 0.03 * smudge + 0.45 * rim + 0.3 * glint, 0, 1)
    else:   # tinted
        alpha = np.clip(0.80 + 0.02 * smudge + 0.15 * rim + 0.1 * glint, 0, 1)
    H = 0.5 + 0.35 * rim + 0.02 * smudge
    return dict(H=H, alb=alb, alpha=alpha, smooth=np.where(rim > 0.5, 190.0, 235.0))


def m_ladder(F, n):
    """A ladder: two rails and four rungs (vanilla's layout - the model is one plane over the whole texture), each
    a rounded wooden bar with grain along it and a dark peg where a rung meets a rail."""
    rail = ((F.u > 0.10) & (F.u < 0.21)) | ((F.u > 0.79) & (F.u < 0.90))
    rv = (F.v * 4) % 1.0
    rung = (np.abs(rv - 0.5) < 0.07) & (F.u > 0.15) & (F.u < 0.85) & ~rail
    ru = np.where(F.u < 0.5, (F.u - 0.155) / 0.055, (F.u - 0.845) / 0.055)   # -1..1 across a rail
    dome = np.where(rail, 1 - np.clip(ru, -1, 1) ** 2, 1 - ((rv - 0.5) / 0.07) ** 2)
    grain = np.where(rail, F.noise(4, 60, 0.7, sx=40.0, sy=1.0).T, F.noise(4, 60, 0.7, sx=1.0, sy=40.0).T)
    peg = rail & (np.abs(rv - 0.5) < 0.03) & (np.abs(np.abs(F.u - 0.5) - 0.345) < 0.02)
    alpha = (rail | rung).astype(np.float64)
    H = np.where(rail, 0.6 + 0.3 * dome, 0.35 + 0.3 * dome) + 0.03 * grain - 0.15 * peg
    shade = 0.80 + 0.15 * dome + 0.08 * grain - 0.4 * peg - 0.10 * rung
    return dict(H=np.where(alpha > 0, H, 0.0), alb=tint((0.62, 0.47, 0.30), shade), alpha=alpha)


def m_iron_bars(F, n):
    """Wrought iron bars: four round bars per block (one on the centre column u 7/16-9/16, which vanilla's model maps
    onto the post and the edges seen from above) and a flat band along the top and bottom edges (stacked panes join
    into one band); dark iron, F0 iron, rust spots dielectric."""
    uc, vc = F.u + 0.5 / n, F.v + 0.5 / n                      # texel centres (u 7/16-9/16 = columns 56-71 exactly)
    du = (uc * 4 + 0.5) % 1.0 - 0.5                            # 0 on a bar's axis (u = 0, 1/4, 1/2, 3/4)
    bar = np.abs(du) < 0.25                                   # half width 1/16 block
    dv = (vc + 0.5) % 1.0 - 0.5
    band = np.abs(dv) < 0.03
    across = np.clip(du / 0.25, -1, 1)
    dome = np.where(band, 1.0, 1 - across ** 2)
    rust = smoothstep(0.55, 0.95, F.noise(3, 12, 0.5)) * smoothstep(0.0, 1.0, F.noise(8, 30, 0.4) + 0.5)
    alpha = (bar | band).astype(np.float64)
    H = np.where(alpha > 0, 0.4 + 0.4 * dome + 0.05 * F.noise(8, 30, 0.4) + 0.1 * band, 0.0)
    iron = tint((0.30, 0.30, 0.32), 0.75 + 0.35 * dome)
    rust_c = tint((0.45, 0.28, 0.18), 0.8 + 0.2 * dome)
    alb = iron * (1 - rust[..., None]) + rust_c * rust[..., None]
    return dict(H=H, alb=alb, alpha=alpha, f0=np.where(rust > 0.5, 10, IRON))


def m_lantern(F, n, glow):
    """A lantern painted to vanilla's UV layout (16 units per texture; the model maps these rectangles): the body's
    sides x 0-6 / y 2-9 (an iron frame round a glass pane lit by the flame), its cap and base x 0-6 / y 9-15, the knob's
    sides x 1-5 / y 0-2 (its top x 1-5 / y 10-14 is the cap's middle), the handle / chain x 11-14 / y 1-12.
    Iron is F0 iron and smoother; the glass glows (LabPBR emission, the flame's core brightest)."""
    x, y = F.u * 16, F.v * 16

    def rect(x0, y0, x1, y1):
        return (x >= x0) & (x < x1) & (y >= y0) & (y < y1)
    iron_c = tint((0.20, 0.20, 0.22), 0.85 + 0.15 * F.noise(8, 30, 0.4))
    alb = np.zeros((n, n, 3))
    alpha, H = np.zeros((n, n)), np.zeros((n, n))
    smooth, f0, emit = np.full((n, n), 110.0), np.full((n, n), IRON), np.full((n, n), 255.0)
    side = rect(0, 2, 6, 9)
    pane = rect(0.9, 3.0, 5.1, 8.1)
    mullion = pane & (np.abs(x - 3.0) < 0.25)                 # the frame's middle bar, a sixteenth of a block
    core = np.exp(-(((x - 3.0) / 1.1) ** 2 + ((y - 6.2) / 1.6) ** 2))
    lit = pane & ~mullion
    glass = np.clip(np.asarray(glow) * (0.70 + 0.55 * core[..., None]), 0, 1)
    alb = np.where(side[..., None], iron_c, alb)
    alb = np.where(lit[..., None], glass, alb)
    H = np.where(side, np.where(lit, 0.45, 0.8), H)
    smooth = np.where(lit, 225.0, smooth)
    f0 = np.where(lit, 10, f0)
    emit = np.where(lit, np.clip(110 + 140 * core, 0, 250), emit)
    cap = rect(0, 9, 6, 15)
    knob_top = rect(1, 10, 5, 14)
    ring = cap & ~rect(0.7, 9.7, 5.3, 14.3)
    alb = np.where(cap[..., None], iron_c * (0.9 + 0.2 * ring[..., None]), alb)
    H = np.where(cap, 0.55 + 0.25 * ring + 0.2 * knob_top, H)
    knob = rect(1, 0, 5, 2)
    alb = np.where(knob[..., None], iron_c, alb)
    H = np.where(knob, 0.7, H)
    # the handle / chain: oval links stacked along v, each a ring (alternate links seen edge-on as a bar)
    cy = (y - 1.0) / 1.5
    link = np.floor(cy)
    ly = (cy % 1.0) - 0.5
    lx = (x - 12.5) / 1.4
    r = np.sqrt(lx ** 2 + (ly * 1.2) ** 2)
    ring_l = (np.abs(r - 0.42) < 0.16) & (link % 2 == 0)
    bar_l = (np.abs(lx) < 0.16) & (link % 2 == 1)
    chain = rect(11, 1, 14, 12) & (ring_l | bar_l)
    alb = np.where(chain[..., None], iron_c * 1.1, alb)
    H = np.where(chain, 0.7, H)
    alpha = (side | cap | knob | chain).astype(np.float64)
    return dict(H=H, alb=np.clip(alb, 0, 1), alpha=alpha, f0=f0, smooth=smooth, emit=emit)


def m_cobweb(F, n):
    """A cobweb: eight straight spokes from a hub a little off the middle and, between them, rings of STRAIGHT chords
    (an orb web is a polygon spiral, not circles - circles read as a maze), sagging a little; pale silk, cut-out, a
    few chords broken."""
    cx, cy = 0.48, 0.52
    dx, dy = F.u - cx, F.v - cy
    r = np.sqrt(dx * dx + dy * dy)
    ang = np.arctan2(dy, dx)
    sector = math.pi / 4
    k = np.round(ang / sector)
    spoke = np.abs(ang - k * sector) * r < 0.016            # 4 texels: still a thread at mip 2 (RTS zoom)
    mid = (np.floor(ang / sector) + 0.5) * sector             # the middle of the sector this texel is in
    rn = r * np.cos(ang - mid) / math.cos(sector / 2)         # 1 on a chord at radius 1 (a polygon ring)
    sag = 0.012 * np.cos((ang - mid) / (sector / 2) * math.pi / 2)   # chords hang a little between spokes
    ring = (rn + sag - 0.06) % 0.11
    chord = (np.abs(ring - 0.055) < 0.016) & (rn > 0.06) & (rn < 0.46)   # 4 texels wide, like the spokes
    broken = smoothstep(0.3, 0.9, F.noise(2, 5, 0.5)) > 0.5
    alpha = ((spoke & (r < 0.52)) | (chord & ~broken)).astype(np.float64)
    H = np.where(alpha > 0, 0.6, 0.0)
    return dict(H=H, alb=tint((0.90, 0.90, 0.88), 0.92 + 0.08 * F.noise(4, 16, 0.5)), alpha=alpha)


# ------------------------------------------------------------------ calibration + maps ----------------------------
# Calibrated at MIP 2, not at full size (round 2, Oct 15): from the RTS camera at the default zoom a 128x texture is
# sampled at mip 2 (32 texels per block - the reference's own resolution). Fitted at mip 0, round 1 lost ~25% of its
# contrast and half or more of its relief there (grass top 7 deg vs 21; pack_study --mip 2).
CAL_MIP = 2


def cal_mip(size):
    """The calibration level for a texture `size` texels across: the one at 32 texels per block (CAL_MIP for 128x),
    so a 256x texture (HIRES) is fitted at the same on-screen scale, mip 3."""
    return CAL_MIP + max(0, int(round(math.log2(size / 128))))


LF_GAIN = 1.2          # extra low-frequency relief (survives the mipmaps)
TILT_CAP = 1.8         # the full-size tilt may exceed the target by at most this factor (close-ups not embossed)


def mip(a, levels):
    for _ in range(levels):
        h, w = a.shape[:2]
        a = (a[0:h:2, 0:w:2] + a[1:h:2, 0:w:2] + a[0:h:2, 1:w:2] + a[1:h:2, 1:w:2]) / 4.0
    return a


def mip_tilt(nx, ny, m=None, levels=None):
    """Mean tilt (deg) of the normals as the GPU sees them at a mip level (default: the calibration level for this
    size): xy averaged, z rebuilt from xy."""
    levels = cal_mip(nx.shape[0]) if levels is None else levels
    ax, ay = mip(nx, levels), mip(ny, levels)
    r = np.sqrt(np.clip(ax * ax + ay * ay, 0, 1))
    t = np.degrees(np.arcsin(r))
    if m is not None:
        t = t[mip(m.astype(np.float64), levels) > 0.5]
    return float(t.mean())


def fit_albedo(alb, t, mask=None):
    """Fits luma mean / spread (the spread measured at CAL_MIP) and saturation to the targets, keeping each hue."""
    m = np.ones(alb.shape[:2], bool) if mask is None else mask
    L = alb[..., 0] * 0.2126 + alb[..., 1] * 0.7152 + alb[..., 2] * 0.0722
    grey = L[..., None]
    mx, mn = alb.max(-1), alb.min(-1)
    sat = np.where(mx > 1e-6, (mx - mn) / np.maximum(mx, 1e-6), 0)[m].mean()
    if t["sat"] <= 0.01:
        alb = np.repeat(grey, 3, -1)
    elif sat > 1e-6:
        alb = np.clip(grey + (alb - grey) * (t["sat"] / sat), 0, 1)
    L = alb[..., 0] * 0.2126 + alb[..., 1] * 0.7152 + alb[..., 2] * 0.0722
    mu = L[m].mean()
    lv = cal_mip(L.shape[0])
    Lm, mm = mip(L, lv), mip(m.astype(np.float64), lv) > 0.5
    sd = Lm[mm].std() if mm.any() else L[m].std()
    L2 = t["luma"] + (L - mu) * (t["std"] / max(sd, 1e-6))
    scale = np.where(L > 1e-4, L2 / np.maximum(L, 1e-4), 1.0)
    return np.clip(alb * scale[..., None], 0, 1)


def normals(H, strength):
    return materials.height_normals(H, strength, wrap=True)    # a block texture tiles: its slopes wrap


def _bisect(fn, target):
    lo, hi = 0.0, 4096.0
    for _ in range(50):
        mid = (lo + hi) / 2
        lo, hi = (mid, hi) if fn(mid) < target else (lo, mid)
    return (lo + hi) / 2


def solve_strength(H, tilt_deg, mask=None):
    """The gradient scale whose mean tilt AT CAL_MIP (over mask) equals the target, unless that would push the
    full-size tilt past TILT_CAP x the target - then the cap wins (bisection: tilt grows with the scale)."""
    m = np.ones(H.shape, bool) if mask is None else mask

    def at_mip(k):
        nx, ny, _ = normals(H, k)
        return mip_tilt(nx, ny, m)

    def full(k):
        nx, ny, nz = normals(H, k)
        return float(np.degrees(np.arccos(np.clip(nz[m], -1, 1))).mean())
    return min(_bisect(at_mip, tilt_deg), _bisect(full, tilt_deg * TILT_CAP))


def maps(F, H, t, alpha=None):
    # visible texels: alpha above 0.02 - a translucent pane (stained glass, alpha ~0.4) is surface, not a hole
    vis = None if alpha is None else alpha > 0.02
    H = norm01(H)
    lf = F.blur(H, F.n / 48)                              # ~1/48 block: relief the mipmaps keep
    # t["lf"] (round 18): a material may take less of that boost - on stone it raised the grain into lumps that
    # Complementary lit as crumpled foil (eyes structure run 39536435197)
    H = norm01(H + t.get("lf", LF_GAIN) * (lf - lf.mean()))
    k = solve_strength(H, t["tilt"], vis)
    nx, ny, nz = normals(H, k)
    cav = np.clip(F.blur(H, F.n / 48) - H, 0, None)        # below the local average = occluded
    # t["ao"] (round 18b): leaves and stone take a lighter AO - darkening every gap between leaves by up to 45% made
    # the dark flecks over the canopy (eyes structure run 39541067693)
    ao = 1 - np.clip(cav * 2.5 * t.get("ao", 1.0), 0, 0.45 * t.get("ao", 1.0))
    # hspan: a flat material (glass) keeps its height within the top hspan of the range - norm01 would turn a glint
    # into a groove a quarter block deep under parallax
    n = materials.encode_normal(nx, ny, ao, 1 - t["hspan"] * (1 - H) if "hspan" in t else H)
    # R is perceptual smoothness 0-255 (it was clipped at 229, F0's ceiling - the diamond block's 250 came out 229)
    # a material of several parts (a lantern: iron frame, glass) may paint its own base smoothness as t["smooth_map"]
    base = t.get("smooth_map", t["smooth"])
    # round 18c: the smoothness varies only a little, with the relief's broad shape (ridges worn a touch shinier).
    # Its old +-10 band noise at 1/4-1/16 block plus +-15 from the height drew a worm pattern of smoother and rougher
    # texels (stone: 0-80, std 11) - under Complementary the smoother worms reflect the blue sky, so stone, diorite
    # and grass showed blue-grey worm marks in sun and in shade alike (eyes structure runs 39541067693, 39542261012;
    # the smoothness map matched the in-game pattern; three rounds of relief changes had not moved it)
    smooth = np.clip(base + 8 * (F.blur(H, F.n / 32) - 0.5) + 2 * F.noise(1, 4, 0.5), 0, 255)
    # F0 code: 10 = 4%, a dielectric (most blocks); 230 = iron, 231 = gold, 234 = copper, 255 = a metal with its
    # albedo as F0 (LabPBR 1.3; 238-254 are undefined - shader packs read them differently);
    # a material may paint its own per-texel code (rusty iron: metal lumps, dielectric rust) as t["f0_map"]
    f0 = t.get("f0_map", t.get("f0", 10))
    # emission: 255 = none, 0-254 = how much of the albedo glows (a lantern's flame paints t["emit_map"])
    s = materials.encode_specular(np.floor(smooth), f0=f0, porosity=t["pore"], emission=t.get("emit_map", 255),
                                  shape=H.shape)
    if alpha is not None:
        n[..., 3] = np.where(vis, n[..., 3], 255)
    probs = materials.check_labpbr(n, s, vis)
    if probs:
        raise SystemExit("LabPBR check failed: " + "; ".join(probs))
    return n, s, k


def to_png(rgb, alpha=None):
    a = np.ones(rgb.shape[:2]) if alpha is None else alpha
    rgba = np.concatenate([rgb, a[..., None]], -1)
    return Image.fromarray(np.clip(rgba * 255 + 0.5, 0, 255).astype(np.uint8), "RGBA")


def lit_preview(alb, n_img, light=(-0.45, -0.55, 0.70)):
    """Albedo lit by the normal map (a low sun from the top left) and AO, tiled 2x2 - how relief reads."""
    nm = np.asarray(n_img, dtype=np.float64) / 255.0
    nx, ny = nm[..., 0] * 2 - 1, nm[..., 1] * 2 - 1
    nz = np.sqrt(np.clip(1 - nx * nx - ny * ny, 0, 1))
    lx, ly, lz = light
    ln = math.sqrt(lx * lx + ly * ly + lz * lz)
    ndl = np.clip((nx * lx + ny * ly + nz * lz) / ln, 0, 1)
    shade = (0.35 + 0.85 * ndl) * nm[..., 2]
    img = np.clip(alb[..., :3] * shade[..., None], 0, 1)
    return np.tile(img, (2, 2, 1))


RTS_PPB = 36                       # screen px per block at the RTS default zoom (30 blocks of view height, 1080p)
PLAINS_TINT = (0.57, 0.74, 0.35)   # a plains-like biome colour for the tinted textures
FOLIAGE_TINT = (0.47, 0.67, 0.18)  # the plains foliage colour (#77AB2F) for the leaves


ROTATED = {"grass_block_top", "grass_block_top_v2", "grass_block_top_v3", "grass_block_top_v4", "dirt", "sand", "gravel", "stone"}   # turned at random per block
ROTATED |= set(LEAVES) | {"azalea_leaves", "flowering_azalea_leaves", "cherry_leaves"}  # and this pack turns the leaves (round 17)


def rts_view(alb, ppb=RTS_PPB, blocks=8, tint=None, seed=1, rotate=True):
    """The texture as the RTS camera draws it: a field of blocks - each turned a random quarter where the vanilla
    model does that (ROTATED), else all alike, so a pattern that repeats on every block shows - sampled the way the block atlas is (nearest texel, the two nearest mip levels
    blended). Its luma std matched the eyes stills (round 3 grass: 0.070 on screen at zoom 30, 0.074 here), so it is
    how a texture's busyness is judged before a CI run. Returns (image, on-screen luma std)."""
    a = alb[..., :3] if tint is None else alb[..., :3] * np.asarray(tint)[None, None, :]
    mips = [a]
    while mips[-1].shape[0] > 8:
        mips.append(mip(mips[-1], 1))
    lod = max(0.0, math.log2(a.shape[0] / ppb))
    lo = min(int(lod), len(mips) - 1)
    hi = min(lo + 1, len(mips) - 1)
    f = lod - int(lod)
    rng = np.random.default_rng(seed)

    def samp(m, k):
        idx = (np.arange(ppb) * m.shape[0] / ppb).astype(int)
        return np.rot90(m, k)[np.ix_(idx, idx)]
    rows = []
    for _ in range(blocks):
        ks = rng.integers(4, size=blocks) if rotate else np.zeros(blocks, int)
        rows.append(np.concatenate([(1 - f) * samp(mips[lo], k) + f * samp(mips[hi], k) for k in ks], 1))
    img = np.concatenate(rows, 0)
    return img, float((img @ np.array([0.2126, 0.7152, 0.0722])).std())


# ------------------------------------------------------------------ build -----------------------------------------
# Water (round 6): 15% of the RTS view (the eyes ground census). Targets = vanilla's own water (pack-study-vanilla,
# run 38740005727: greyscale - the biome water colour tints it - luma 0.68, std 0.064, alpha 0.71 on every texel)
# because the reference pack's water was lumped with lava. Animated like vanilla's (32 frames, 2 ticks each = a 3.2 s
# loop): ripple layers drift a whole number of tiles per loop, so the strip loops without a seam.
WATER = dict(luma=0.68, std=0.064, alpha=0.71, frames=32, frametime=2, tilt=10.0, smooth=230, f0=5)
# (lo, hi cycles per tile, slope, weight, tiles moved per loop along u, along v)
STILL_LAYERS = ((2, 5, 1.0, 1.0, 1, 0), (4, 10, 0.8, 0.6, 0, 1), (5, 12, 0.8, 0.5, -1, 1), (12, 28, 0.5, 0.25, 2, -1))
# Flowing water (round 9): vanilla's flow texture is drawn at half scale (2 blocks per tile) and turned so v points
# downstream, so the tile is twice as wide (256) and every layer runs down v - streaks stretched along the current.
# 1 tick per frame (vanilla's flow animates every tick): one tile = 2 blocks per 1.6 s loop, ~1.25 blocks / s.
FLOW_LAYERS = ((2, 6, 1.0, 1.0, 0, 1), (5, 14, 0.8, 0.6, 0, 1), (8, 20, 0.8, 0.45, 1, 2), (16, 40, 0.5, 0.25, 0, 3))
FLOW = dict(frametime=1, aniso=(1.0, 3.0))


def water_frames(res, seed=4242, layers_spec=STILL_LAYERS, aniso=(1.0, 1.0)):
    """(H, albedo) per frame of water: a slow swell, crossing ripples and fine glints, each layer a fixed random
    spectrum shifted in phase over the loop (a pure translation by whole tiles). aniso stretches the features (a
    large second value: long along v)."""
    F = Field(res, seed)
    layers = []   # (spectrum, weight, tiles moved per loop along u, along v)
    for lo, hi, slope, w, du, dv in layers_spec:
        r = np.sqrt((F.fx * aniso[0]) ** 2 + (F.fy * aniso[1]) ** 2)
        band = (r >= lo) & (r <= hi)
        spec = np.where(band, 1.0 / np.maximum(r, 1e-6) ** slope, 0.0) * (
            F.rng.standard_normal((res, res)) + 1j * F.rng.standard_normal((res, res)))
        layers.append((spec, w, du, dv))
    out = []
    for t in range(WATER["frames"]):
        ph = t / WATER["frames"]
        H = np.zeros((res, res))
        for spec, w, du, dv in layers:
            # shifting a periodic field by (du, dv) tiles over the loop: phase exp(-2 pi i (fx du + fy dv) ph)
            f = np.real(np.fft.ifft2(spec * np.exp(-2j * math.pi * (F.fx * du + F.fy * dv) * ph)))
            H += w * f / max(f.std(), 1e-9)
        out.append(H)
    # one normalisation for the whole loop, so frames keep their relative brightness (no flicker)
    allH = np.stack(out)
    allH = (allH - allH.mean()) / allH.std()
    return F, [h for h in allH]


def write_water(res, preview_dir=None, name="water_still"):
    flow = name == "water_flow"
    F, Hs = water_frames(res, 4243, FLOW_LAYERS, FLOW["aniso"]) if flow else water_frames(res)
    alb_frames, n_frames, s_frames = [], [], []
    t = dict(tilt=WATER["tilt"])
    # the normal strength is solved once (frame 0) and kept: one surface, moving
    H0 = norm01(Hs[0])
    k = solve_strength(H0, t["tilt"])
    for H in Hs:
        Hn = np.clip(0.5 + 0.18 * H, 0, 1)
        nx, ny, nz = normals(Hn, k)
        # a glint on the crests facing a high sun, troughs a little darker: water reads by its light, not its colour
        lit = nx * -0.35 + ny * -0.45 + nz * 0.82
        shade = 1.0 + 0.9 * (lit - 0.82) + 0.05 * H
        alb_frames.append(np.stack([shade] * 3, -1))
        n_frames.append(materials.encode_normal(nx, ny, np.ones_like(Hn), np.maximum(Hn, 1 / 255)))
        s_frames.append(materials.encode_specular(np.full(Hn.shape, WATER["smooth"]), f0=WATER["f0"], porosity=0,
                                                  emission=255))
    strip = np.concatenate(alb_frames, 0)
    L = strip[..., 0]
    strip = np.clip(WATER["luma"] + (strip - L.mean()) * (WATER["std"] / max(mip(L, CAL_MIP).std(), 1e-6)), 0, 1)
    alpha = np.full(strip.shape[:2], WATER["alpha"])
    nstrip, sstrip = np.concatenate(n_frames, 0), np.concatenate(s_frames, 0)
    probs = materials.check_labpbr(nstrip, sstrip)
    if probs:
        raise SystemExit("LabPBR check failed (water): " + "; ".join(probs))
    to_png(strip, alpha).save(os.path.join(BLOCK_DIR, name + ".png"), **PNG_OPTS)
    Image.fromarray(nstrip, "RGBA").save(os.path.join(BLOCK_DIR, name + "_n.png"), **PNG_OPTS)
    Image.fromarray(sstrip, "RGBA").save(os.path.join(BLOCK_DIR, name + "_s.png"), **PNG_OPTS)
    for suffix in ("", "_n", "_s"):     # the maps animate with the colour (Iris / Oculus read each one's own .mcmeta)
        with open(os.path.join(BLOCK_DIR, "%s%s.png.mcmeta" % (name, suffix)), "w") as f:
            json.dump({"animation": {"frametime": FLOW["frametime"] if flow else WATER["frametime"]}}, f)
    first = strip[:res]
    tinted = first * np.asarray((0.25, 0.46, 0.89))[None, None, :]      # the plains water colour (#3F76E4)
    img, std = rts_view(tinted)
    if preview_dir:
        Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), "RGB").save(
            os.path.join(preview_dir, name + "_rts.png"))
        frames = [np.clip(strip[i * res:(i + 1) * res] * np.asarray((0.25, 0.46, 0.89)), 0, 1) for i in (0, 8, 16, 24)]
        Image.fromarray((np.concatenate(frames, 1) * 255).astype(np.uint8), "RGB").save(
            os.path.join(preview_dir, name + "_frames.png"))
    return {"normal_strength": round(k, 2), "frames": WATER["frames"], "rts_luma_std": round(std, 3),
            "luma": round(float(strip[..., 0].mean()), 3)}


# ------------------------------------------------------------------ the recipe book + the build ------------------
SEEDS = {k: 1000 + i * 37 for i, k in enumerate(TARGETS)}     # by TARGETS order (see SEED_ORDER)
# PNG zlib level 6: Pillow's optimize (level 9 + filter search) took 23 of the 40 s for 2.7% smaller files
PNG_OPTS = dict(compress_level=6)
CUTOUT = set(LEAVES) | {"azalea_leaves", "flowering_azalea_leaves", "cherry_leaves"}   # alpha-cut leaves (azalea is never tinted)
# round 16: textures with holes or see-through parts (previewed over a dark backdrop, like the leaves)
CUTOUT |= {"mangrove_roots_side", "mangrove_roots_top", "ladder", "iron_bars", "lantern", "soul_lantern", "cobweb",
           "glass"} | {c + "_stained_glass" for c in STAINED} | {"tinted_glass"}


# Vanilla draws these blocks CUT-OUT WITHOUT MIPMAPS (RenderType.cutout binds the atlas with mipmap off): fine for its
# 16x textures, which the RTS camera magnifies, but a 128x texture at ~36 px per block is minified 3.5x with nearest
# texels - thin threads, rims and glints break up and flicker as the camera pans (fresh-eyes review, Oct 15). Forge
# reads a "render_type" from a block model, so the pack overrides these models with the same geometry and
# cutout_mipped (what vanilla's leaves use). Geometry copied from vanilla 1.20.1's models.
_MIPPED = "minecraft:cutout_mipped"
MODEL_OVERRIDES = {
    "glass": {"parent": "minecraft:block/cube_all", "textures": {"all": "minecraft:block/glass"}},
    "cobweb": {"parent": "minecraft:block/cross", "textures": {"cross": "minecraft:block/cobweb"}},
    "lantern": {"parent": "minecraft:block/template_lantern", "textures": {"lantern": "minecraft:block/lantern"}},
    "lantern_hanging": {"parent": "minecraft:block/template_hanging_lantern",
                        "textures": {"lantern": "minecraft:block/lantern"}},
    "soul_lantern": {"parent": "minecraft:block/template_lantern",
                     "textures": {"lantern": "minecraft:block/soul_lantern"}},
    "soul_lantern_hanging": {"parent": "minecraft:block/template_hanging_lantern",
                             "textures": {"lantern": "minecraft:block/soul_lantern"}},
    "ladder": {"ambientocclusion": False, "textures": {"particle": "block/ladder", "texture": "block/ladder"},
               "elements": [{"from": [0, 0, 15.2], "to": [16, 16, 15.2], "shade": False, "faces": {
                   "north": {"uv": [0, 0, 16, 16], "texture": "#texture"},
                   "south": {"uv": [16, 0, 0, 16], "texture": "#texture"}}}]},
    "mangrove_roots": {"parent": "block/block", "textures": {
        "side": "block/mangrove_roots_side", "top": "block/mangrove_roots_top", "particle": "#side"}, "elements": [
        {"from": [0, 0, 8], "to": [16, 16, 8], "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#side"},
                                                       "south": {"uv": [0, 0, 16, 16], "texture": "#side"}}},
        {"from": [8, 0, 0], "to": [8, 16, 16], "faces": {"west": {"uv": [0, 0, 16, 16], "texture": "#side"},
                                                       "east": {"uv": [0, 0, 16, 16], "texture": "#side"}}},
        {"from": [0, 15.998, 0], "to": [16, 16, 16], "faces": {
            "down": {"uv": [0, 16, 16, 0], "texture": "#top", "cullface": "up"},
            "up": {"uv": [0, 0, 16, 16], "texture": "#top", "cullface": "up"}}},
        {"from": [0, 0, 0], "to": [16, 0.002, 16], "faces": {
            "down": {"uv": [0, 0, 16, 16], "texture": "#top", "cullface": "down"},
            "up": {"uv": [0, 16, 16, 0], "texture": "#top", "cullface": "down"}}},
        {"from": [0, 0, 0], "to": [16, 16, 0.002], "faces": {
            "north": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "north"},
            "south": {"uv": [16, 0, 0, 16], "texture": "#side", "cullface": "north"}}},
        {"from": [0, 0, 15.998], "to": [16, 16, 16], "faces": {
            "north": {"uv": [16, 0, 0, 16], "texture": "#side", "cullface": "south"},
            "south": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "south"}}},
        {"from": [0, 0, 0], "to": [0.002, 16, 16], "faces": {
            "east": {"uv": [16, 0, 0, 16], "texture": "#side", "cullface": "west"},
            "west": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "west"}}},
        {"from": [15.998, 0, 0], "to": [16, 16, 16], "faces": {
            "east": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "east"},
            "west": {"uv": [16, 0, 0, 16], "texture": "#side", "cullface": "east"}}}]},
}


# Round 17: the leaves turned at random per block, as vanilla already does for stone, dirt and grass. A leaf block
# is one texture on all six faces and its model has no fixed up, so a random quarter turn on both axes costs nothing -
# and it breaks the block-by-block repeat that drew a grid across every canopy from the RTS camera (a tree's crown is
# most of the screen). Vanilla's leaves blockstate is a single model for every state ("" matches them all); this
# pack's copy is the same model under 16 turns. Never applies to a block whose faces differ (logs, grass).
ROTATED_BLOCKSTATES = sorted((set(LEAVES) - set(LEAF_VARIANTS)) | {"azalea_leaves", "flowering_azalea_leaves", "cherry_leaves"})
BLOCKSTATE_DIR = os.path.join(PACK_DIR, "assets", "minecraft", "blockstates")


def write_models():
    os.makedirs(MODEL_DIR, exist_ok=True)
    for name, model in MODEL_OVERRIDES.items():
        with open(os.path.join(MODEL_DIR, name + ".json"), "w") as f:
            json.dump(dict(model, render_type=_MIPPED), f, indent=1)
    os.makedirs(BLOCKSTATE_DIR, exist_ok=True)
    for v, base in LEAF_VARIANTS.items():      # the variant's own model: vanilla's leaves model on its texture
        with open(os.path.join(MODEL_DIR, v + ".json"), "w") as f:
            json.dump({"parent": "minecraft:block/leaves", "textures": {"all": "minecraft:block/" + v}}, f, indent=1)
    grass_models = ["grass_block"]
    for v, base in GRASS_VARIANTS.items():      # grass_block_top_vN -> model grass_block_vN
        m = "grass_block_" + v.rsplit("_", 1)[1]
        grass_models.append(m)
        with open(os.path.join(MODEL_DIR, m + ".json"), "w") as f:
            json.dump({"parent": "minecraft:block/grass_block", "textures": {"top": "minecraft:block/" + v}}, f, indent=1)
    grass = [dict({"model": "minecraft:block/" + m}, **({"y": y} if y else {}))
             for m in grass_models for y in (0, 90, 180, 270)]
    with open(os.path.join(BLOCKSTATE_DIR, "grass_block.json"), "w") as f:   # vanilla's, with the second top added
        json.dump({"variants": {"snowy=false": grass, "snowy=true": {"model": "minecraft:block/grass_block_snow"}}},
                  f, indent=1)
    for name in ROTATED_BLOCKSTATES:
        models = [name] + [v for v, b in LEAF_VARIANTS.items() if b == name]
        turns = [dict({"model": "minecraft:block/" + m}, **({"x": x} if x else {}), **({"y": y} if y else {}))
                 for m in models for x in (0, 90, 180, 270) for y in (0, 90, 180, 270)]
        with open(os.path.join(BLOCKSTATE_DIR, name + ".json"), "w") as f:
            json.dump({"variants": {"": turns}}, f, indent=1)


def _parts(fn, fit=True):
    """Round 16: a material that returns a dict (H, alb and any of alpha / f0 / smooth / emit). The albedo is fitted
    on its visible texels - or not at all (fit=False) for a sprite of several materials painted to vanilla's UV
    layout (a lantern's iron, glass and flame)."""
    def r(F, n, t):
        d = fn(F, n)
        if fit:
            m = d["alpha"] > 0.05 if "alpha" in d else np.ones(d["H"].shape, bool)
            d["alb"] = np.where(m[..., None], fit_albedo(d["alb"], t, m), d["alb"])
        return d
    return r


def soften(F, alb, t):
    """Round 25 (the owner's "dots of pepper", Oct 20; r24's plain-lane close-ups: stone's 4-neighbour Laplacian 14.8 ->
    34): a material whose TARGETS entry has `soften` (texels, sigma) gets its fitted albedo low-passed - nearest-texel
    magnification turns every texel-to-texel step into a hard screen step, so the colour's detail lives at 3+ texels
    and the fine grain stays in the normal map for the light. No `soften`, no change."""
    sg = t.get("soften")
    if not sg:
        return alb
    return np.clip(np.stack([F.blur(alb[..., c], sg) for c in range(alb.shape[-1])], -1), 0, 1)


def _plain(fn):
    def r(F, n, t):
        H, alb = fn(F, n)
        return dict(H=H, alb=soften(F, fit_albedo(alb, t), t))
    return r


def _with_f0(fn):
    """A material that paints its own per-texel F0 code (metal crests, dielectric rust)."""
    def r(F, n, t):
        H, alb, f0 = fn(F, n)
        return dict(H=H, alb=fit_albedo(alb, t), f0=f0)
    return r


def _cutout(fn):
    """Leaves: fitted on the leaf texels only; the holes keep their unfitted colour (invisible, alpha 0)."""
    def r(F, n, t):
        H, alb, alpha = fn(F, n)
        m = alpha > 0.5
        return dict(H=H, alb=np.where(m[..., None], fit_albedo(alb, t, m), alb), alpha=alpha)
    return r


def _grass_side(F, n, t):
    H, alb, _ = m_grass_side(F, n, m_dirt_r24)
    return dict(H=H, alb=soften(F, fit_albedo(alb, t), t))


def _grass_side_overlay(F, n, t):
    # the overlay's fringe is the side's: rebuilt from the side's own seed (deterministic), so each texture is one
    # independent job; the side's albedo is the preview's backdrop
    side = Field(n, SEEDS["grass_block_side"])
    _, side_alb, fringe = m_grass_side(side, n, m_dirt_r24)
    H, alb = m_grass_overlay(F, n, fringe)
    return dict(H=H, alb=fit_albedo(alb, t, fringe > 0.5), alpha=fringe,
                under=fit_albedo(side_alb, TARGETS["grass_block_side"]))


def _grass_snow_side(F, n, t):
    H, ad, asn, band = m_grass_snow_side(F, n)
    alb = np.where(band[..., None], fit_albedo(asn, TARGETS["snow"], band), fit_albedo(ad, TARGETS["dirt"], ~band))
    return dict(H=H, alb=alb)


# (wood, end grain colour, stripped side colour) - the log tops' and the stripped logs' colours above
STRIPPED_ENDS = (
    ("oak", (0.70, 0.56, 0.36), (0.72, 0.56, 0.36)), ("spruce", (0.62, 0.45, 0.28), (0.58, 0.42, 0.26)),
    ("birch", (0.86, 0.76, 0.56), (0.82, 0.72, 0.50)), ("jungle", (0.72, 0.56, 0.36), (0.70, 0.52, 0.34)),
    ("acacia", (0.78, 0.42, 0.20), (0.75, 0.40, 0.22)), ("dark_oak", (0.45, 0.30, 0.18), (0.32, 0.22, 0.14)),
    ("mangrove", (0.58, 0.28, 0.24), (0.52, 0.22, 0.20)),
)


def recipes():
    """name -> recipe(F, n, t) returning H (height), alb (albedo fitted to the target t) and, where a material needs
    them, f0 (a per-texel F0 code map) or alpha (cut-out), under (the preview's backdrop). One per TARGETS key."""
    R = {
        "grass_block_top": _plain(m_grass_top), "grass_block_top_v2": _plain(m_grass_top),
        "grass_block_top_v3": _plain(m_grass_top), "grass_block_top_v4": _plain(m_grass_top),
        "grass_block_side": _grass_side,
        "grass_block_side_overlay": _grass_side_overlay,
        "dirt": _plain(m_dirt_r24), "stone": _plain(m_stone_r24), "cobblestone": _plain(m_cobblestone),
        "sand": _plain(m_sand), "gravel": _plain(m_gravel_r25), "oak_planks": _plain(m_oak_planks_r24), "oak_log": _plain(m_oak_bark_r24),
        "oak_log_top": _plain(m_log_top), "stone_bricks": _plain(m_stone_bricks),
        "polished_andesite": _plain(m_polished_andesite), "smooth_stone": _plain(m_smooth_stone),
        "smooth_stone_slab_side": _plain(m_smooth_stone_slab_side), "spruce_planks": _plain(m_spruce_planks),
        "raw_iron_block": _with_f0(m_raw_iron), "barrel_side": _with_f0(m_barrel_side),
        "barrel_top": _with_f0(lambda F, n: m_barrel_end(F, n, True)),
        "barrel_bottom": _with_f0(lambda F, n: m_barrel_end(F, n, False)),
        "gold_block": _plain(m_gold), "hay_block_side": _plain(m_hay_side), "hay_block_top": _plain(m_hay_top),
        "birch_log": _plain(m_birch_log),
        "birch_log_top": _plain(lambda F, n: m_log_top(F, n, (0.86, 0.76, 0.56), (0.86, 0.85, 0.80))),
        "cut_copper": _with_f0(lambda F, n: m_copper(F, n, False)),
        "oxidized_cut_copper": _with_f0(lambda F, n: m_copper(F, n, True)),
        "azalea_leaves": _cutout(lambda F, n: m_azalea(F, n, False)),
        "cherry_leaves": _cutout(m_cherry_leaves),
        "flowering_azalea_leaves": _cutout(lambda F, n: m_azalea(F, n, True)),
    }
    for wood, (bark, end) in WOODS.items():
        R[wood + "_log"] = _plain(lambda F, n, b=bark, w=wood: m_log(F, n, b, w))
        R[wood + "_log_top"] = _plain(lambda F, n, w=end, b=bark: m_log_top(F, n, w, b))
    for name, (count, length, width, round_tip) in LEAVES.items():
        R[name] = _cutout(lambda F, n, a=(count, length, width, round_tip): m_leaves(F, n, *a))
    plain = {
        # round 11: the other factions' buildings
        "polished_blackstone": lambda F, n: m_polished(F, n, (0.20, 0.18, 0.20), 0.08, 0.06),
        "polished_blackstone_bricks": lambda F, n: m_bricks(F, n, 4, 2, (0.21, 0.19, 0.21)),
        "deepslate_bricks": lambda F, n: m_bricks(F, n, 4, 2, (0.30, 0.30, 0.32)),
        # r21: the 4x4 grid turned to grey static at RTS zoom - wider, darker joints so the grid survives the mips
        "deepslate_tiles": lambda F, n: m_tiles(F, n, 4, (0.22, 0.22, 0.24), joint=(0.014, 0.034), grout=0.38),
        "deepslate": lambda F, n: m_deepslate(F, n),
        "deepslate_top": lambda F, n: m_deepslate(F, n, top=True),
        "polished_deepslate": lambda F, n: m_polished(F, n, (0.32, 0.32, 0.34), 0.06, 0.04),
        # r21: 22 small high-contrast stones read as salt-and-pepper on the dark base - fewer, larger, calmer stones
        "cobbled_deepslate": lambda F, n: m_cobblestone(F, n, cells=11, contrast=0.18, fine=0.03),
        "netherrack": m_netherrack,
        "nether_bricks": lambda F, n: m_bricks(F, n, 4, 2, (0.30, 0.12, 0.14), pits=0.05),
        "prismarine_bricks": lambda F, n: m_bricks(F, n, 2, 2, (0.42, 0.66, 0.58), tone_amp=0.10),
        "dark_prismarine": lambda F, n: m_tiles(F, n, 2, (0.20, 0.38, 0.33)),
        "moss_block": m_moss,
        "mossy_stone_bricks": lambda F, n: m_bricks(F, n, 2, 1, (0.50, 0.50, 0.49), moss=0.8),
        # round 12: the census's rest, other biomes' terrain
        "sandstone": lambda F, n: m_sandstone(F, n, "side"),
        "sandstone_top": lambda F, n: m_sandstone(F, n, "top"),
        "sandstone_bottom": lambda F, n: m_sandstone(F, n, "bottom"),
        "red_terracotta": lambda F, n: m_clayish(F, n, (0.56, 0.24, 0.18)),
        "terracotta": lambda F, n: m_clayish(F, n, (0.60, 0.38, 0.28)),
        "polished_basalt_side": lambda F, n: m_basalt(F, n, False),
        "polished_basalt_top": lambda F, n: m_basalt(F, n, True),
        "bone_block_side": lambda F, n: m_bone(F, n, False),
        "bone_block_top": lambda F, n: m_bone(F, n, True),
        "quartz_pillar": lambda F, n: m_quartz(F, n, "pillar"),
        "quartz_pillar_top": lambda F, n: m_quartz(F, n, "pillar_top"),
        "quartz_bricks": lambda F, n: m_quartz(F, n, "bricks"),
        "quartz_block_bottom": lambda F, n: m_quartz(F, n, "smooth"),
        # neutral, a hair warm: a near-grey whose only hue is the speck's gets that hue blown up by the saturation fit
        "andesite": lambda F, n: m_igneous(F, n, (0.54, 0.53, 0.51), (0.40, 0.40, 0.39), 0.9),
        "granite": m_granite,
        "diorite": m_diorite,
        "snow": m_snow,
        "podzol_top": m_podzol,
        "coarse_dirt": m_coarse_dirt,
        "clay": lambda F, n: m_clayish(F, n, (0.62, 0.64, 0.70), pits=0.02, mottle=0.05),
        "red_sand": lambda F, n: m_sand(F, n, (0.75, 0.42, 0.18)),
        "mud": lambda F, n: m_clayish(F, n, (0.26, 0.22, 0.22), pits=0.03, mottle=0.10),
        "calcite": lambda F, n: m_igneous(F, n, (0.88, 0.88, 0.86), (0.75, 0.75, 0.74), 1.0),
        # fine ash with a few darker lapilli: small crystals, low contrast, the tone in a soft mottle (round 18)
        "tuff": lambda F, n: m_crystal(F, n, [((0.42, 0.42, 0.38), 0.85), ((0.32, 0.32, 0.29), 0.15)],
                                       per_side=34, cluster=0.85, contrast=0.3, mottle=0.08, drift_base=3),
        # round 13: every wood's planks and stripped log, the long tail
        "birch_planks": lambda F, n: m_planks(F, n, (0.80, 0.70, 0.48), 0.012),
        "jungle_planks": lambda F, n: m_planks(F, n, (0.66, 0.46, 0.32), 0.012),
        "acacia_planks": lambda F, n: m_planks(F, n, (0.72, 0.38, 0.20), 0.012),
        "dark_oak_planks": lambda F, n: m_planks(F, n, (0.28, 0.18, 0.10), 0.010),
        "mangrove_planks": lambda F, n: m_planks(F, n, (0.48, 0.20, 0.18), 0.012),
        "crimson_planks": lambda F, n: m_planks(F, n, (0.42, 0.18, 0.28), 0.012),
        "warped_planks": lambda F, n: m_planks(F, n, (0.18, 0.45, 0.42), 0.012),
        "stripped_oak_log": lambda F, n: m_stripped(F, n, (0.72, 0.56, 0.36)),
        "stripped_spruce_log": lambda F, n: m_stripped(F, n, (0.58, 0.42, 0.26)),
        "stripped_birch_log": lambda F, n: m_stripped(F, n, (0.82, 0.72, 0.50)),
        "stripped_jungle_log": lambda F, n: m_stripped(F, n, (0.70, 0.52, 0.34)),
        "stripped_acacia_log": lambda F, n: m_stripped(F, n, (0.75, 0.40, 0.22)),
        "stripped_dark_oak_log": lambda F, n: m_stripped(F, n, (0.32, 0.22, 0.14)),
        "stripped_mangrove_log": lambda F, n: m_stripped(F, n, (0.52, 0.22, 0.20)),
        "polished_granite": lambda F, n: m_polished(F, n, (0.62, 0.42, 0.36), 0.10, 0.08),
        # r21: 20-56 per block flecks read as pepper at RTS zoom - fewer, larger dark crystal clusters
        "polished_diorite": lambda F, n: m_polished(F, n, (0.84, 0.84, 0.82), 0.08, 0.16, fleck=(6, 18)),
        # r21 (reviewer: "high-contrast light speckle on black, pepper at RTS"): crystals closer in tone and larger
        "blackstone": lambda F, n: m_igneous(F, n, (0.17, 0.15, 0.17), (0.215, 0.20, 0.22), 0.6, (8, 20)),
        # r22 (shader review: "purple cobble-cell pattern, no glassy darkness"): volcanic glass is near black with only
        # a faint violet depth - mottle 0.20 -> 0.06
        # r22b (eyes: a flat dark-brown cake, the flecks invisible): 3-5 large conchoidal planes of cool black glass,
        # each with its own tone and a hard bright edge - the shader's smooth/F0 gives the glint
        "obsidian": lambda F, n: m_facets(F, n, (0.08, 0.085, 0.12), 5, edge=0.006, rim=False),
        "red_nether_bricks": lambda F, n: m_bricks(F, n, 4, 2, (0.42, 0.08, 0.08), pits=0.05),
        "chiseled_stone_bricks": m_chiseled,
        "dirt_path_top": lambda F, n: m_path(F, n, False),
        "dirt_path_side": lambda F, n: m_path(F, n, True),
        # round 14: cracked / chiseled variants, metals and gems, the structure preview's gaps
        "cracked_stone_bricks": lambda F, n: m_bricks(F, n, 2, 1, (0.50, 0.50, 0.49), cracks=1.0),
        "cracked_deepslate_bricks": lambda F, n: m_bricks(F, n, 4, 2, (0.30, 0.30, 0.32), cracks=1.0),
        "cracked_polished_blackstone_bricks": lambda F, n: m_bricks(F, n, 4, 2, (0.21, 0.19, 0.21), cracks=1.0),
        "chiseled_nether_bricks": lambda F, n: m_chiseled(F, n, (0.30, 0.12, 0.14)),
        "chiseled_polished_blackstone": lambda F, n: m_chiseled(F, n, (0.21, 0.19, 0.21)),
        "reinforced_deepslate_side": lambda F, n: m_reinforced(F, n, False),
        "reinforced_deepslate_top": lambda F, n: m_reinforced(F, n, True),
        "basalt_side": m_basalt_side_r24,
        "basalt_top": lambda F, n: m_basalt_rough(F, n, True),
        "smooth_basalt": lambda F, n: m_clayish(F, n, (0.28, 0.28, 0.30)),
        "soul_sand": m_soul_sand,
        "iron_block": lambda F, n: m_plate(F, n, (0.86, 0.86, 0.87)),
        "netherite_block": lambda F, n: m_plate(F, n, (0.30, 0.27, 0.29)),
        "diamond_block": lambda F, n: m_facets(F, n, (0.62, 0.86, 0.88), 18),       # r22b: cut facets, not a plate
        "emerald_block": lambda F, n: m_facets(F, n, (0.22, 0.62, 0.36), 16),
        # lapis is a stone, not a crystal: matte ultramarine blocks of rock with gold pyrite flecks
        "lapis_block": lambda F, n: m_lapis(F, n),
        # r21: flat pale yellow, same as sand beside it - fewer, larger darker pits so it reads as porous stone
        "end_stone": lambda F, n: m_igneous(F, n, (0.86, 0.86, 0.66), (0.62, 0.62, 0.46), 0.9, size=(7, 16)),
        "white_concrete": lambda F, n: m_clayish(F, n, (0.86, 0.87, 0.87), pits=0.01, mottle=0.03),
        "blue_terracotta": lambda F, n: m_clayish(F, n, (0.30, 0.25, 0.38)),
        "podzol_side": m_podzol_side,
        # round 15: the face census's partial blocks
        "blackstone_top": lambda F, n: m_igneous(F, n, (0.17, 0.15, 0.17), (0.215, 0.20, 0.22), 0.65, (8, 20)),
        "quartz_block_top": lambda F, n: m_quartz(F, n, "block_top"),
        "quartz_block_side": lambda F, n: m_quartz(F, n, "block_side"),
        "farmland": lambda F, n: m_farmland(F, n, False),
        "farmland_moist": lambda F, n: m_farmland(F, n, True),
        "crimson_stem": lambda F, n: m_stem(F, n, (0.36, 0.12, 0.18), (0.72, 0.22, 0.24)),
        "crimson_stem_top": lambda F, n: m_log_top(F, n, (0.62, 0.20, 0.24), (0.36, 0.12, 0.18)),
        "warped_stem": lambda F, n: m_stem(F, n, (0.20, 0.22, 0.30), (0.16, 0.62, 0.58)),
        "warped_stem_top": lambda F, n: m_log_top(F, n, (0.18, 0.55, 0.52), (0.20, 0.22, 0.30)),
    }
    # a stripped log's end: its own wood's rings (the log top's colour) inside a flat rim of the stripped side's colour
    for wood, end, side in STRIPPED_ENDS:
        plain["stripped_%s_log_top" % wood] = lambda F, n, e=end, s=side: m_log_top(F, n, e, s, rim=(0.58, 0.04))
    for name, fn in plain.items():
        R[name] = _plain(fn)
    R["grass_block_snow"] = _grass_snow_side
    # round 16: cut-out and see-through blocks
    R["mangrove_roots_side"] = _parts(lambda F, n: m_roots(F, n, False))
    R["mangrove_roots_top"] = _parts(lambda F, n: m_roots(F, n, True))
    R["glass"] = _parts(lambda F, n: m_glass(F, n, (0.86, 0.91, 0.93), "clear"), fit=False)
    R["glass_pane_top"] = _parts(lambda F, n: m_glass(F, n, (0.80, 0.88, 0.86), "edge"), fit=False)
    for colour, rgb in STAINED.items():
        R[colour + "_stained_glass"] = _parts(lambda F, n, c=rgb: m_glass(F, n, c, "stained"), fit=False)
        R[colour + "_stained_glass_pane_top"] = _parts(lambda F, n, c=rgb: m_glass(F, n, c, "edge"), fit=False)
    R["tinted_glass"] = _parts(lambda F, n: m_glass(F, n, (0.17, 0.13, 0.20), "tinted"), fit=False)
    R["ladder"] = _parts(m_ladder)
    R["iron_bars"] = _parts(m_iron_bars)
    R["lantern"] = _parts(lambda F, n: m_lantern(F, n, (1.0, 0.72, 0.32)), fit=False)
    R["soul_lantern"] = _parts(lambda F, n: m_lantern(F, n, (0.35, 0.85, 0.95)), fit=False)
    R["cobweb"] = _parts(m_cobweb, fit=False)
    return R


RECIPES = recipes()


# Round 17 (lovish Oct 20: "test 256x where it counts"): the textures that fill the screen and are seen up close in
# first person (possession) are built at twice the resolution - the leaves and the bark (most of the screen in a
# forest) and the grass top. At the RTS camera a 256x texture is sampled at the same mip as a 128x one (rts_view,
# cal_mip), so the RTS look is the same; up close the leaf edges, blades and bark fibre come out sharp instead of
# stepped. Each costs ~4x the memory of a 128x texture (x3 maps) - 13 textures, ~13 MB of atlas with mipmaps.
# Not stone and the building blocks: their grain read worse, not better, at 256x (a maze of worms) and they are many.
HIRES = set(GRASS_TOPS) | set(LEAVES) | {"oak_log"} | {w + "_log" for w in WOODS} | {"birch_log"}


# Round 18d: the textures whose HEIGHT channel (normal map A) stays within a shallow top band. Complementary reads the
# height even with parallax off, and stone's full 0-1 height drew the blue-grey worm marks: in game, stone with a
# flat height and its real normals and colour was calm, and near-flat normals with stone's real height showed the
# worms (eyes structure runs 39546529592 and 39547379343 - channel-split tests on claude/pack-diagnostic-flat-stone).
# The normals keep the relief for the light; the height keeps a little depth for players who turn parallax on.
SHALLOW_HEIGHT = 0.12
CALM_HEIGHT = {"stone", "granite", "diorite", "andesite", "tuff", "calcite", "grass_block_top", "grass_block_top_v2",
               "grass_block_top_v3", "grass_block_top_v4", "dirt", "coarse_dirt",
               "podzol_top", "sand", "red_sand", "gravel"} | set(LEAVES) | {"azalea_leaves", "flowering_azalea_leaves", "cherry_leaves"}


# Round 20: every other opaque texture keeps its height within the top quarter. Complementary's POM (on by default in
# labPBR mode) turned full-range heights into pillowed tops, one-way grooves at block borders and blotchy hammered
# flats on the building blocks (block sampler, eyes run 39554020573). Brick joints keep a quarter-block of depth.
BUILDING_HEIGHT = 0.25


def make(name, res, preview_dir):
    """One texture, start to end: recipe -> LabPBR maps -> the three PNGs (+ previews); returns its report line.
    Independent of every other texture (its own seeded Field), so the build runs them in parallel."""
    t = TARGETS[name]
    if name in CALM_HEIGHT and "hspan" not in t:
        t = dict(t, hspan=SHALLOW_HEIGHT)
    elif "hspan" not in t and name not in CUTOUT:
        t = dict(t, hspan=BUILDING_HEIGHT)
    F = Field(res, SEEDS[name])
    r = RECIPES[name](F, res, t)
    for key in ("f0", "smooth", "emit"):   # per-texel codes a recipe painted (see maps)
        if key in r:
            t = dict(t, **{key + "_map": r[key]})
    H, alb, alpha = r["H"], r["alb"], r.get("alpha")
    n_img, s_img, k = maps(F, H, t, alpha)
    to_png(alb, alpha).save(os.path.join(BLOCK_DIR, name + ".png"), **PNG_OPTS)
    Image.fromarray(n_img, "RGBA").save(os.path.join(BLOCK_DIR, name + "_n.png"), **PNG_OPTS)
    Image.fromarray(s_img, "RGBA").save(os.path.join(BLOCK_DIR, name + "_s.png"), **PNG_OPTS)
    nxx = (n_img[..., 0].astype(np.float64) / 127.5 - 1)
    nyy = (n_img[..., 1].astype(np.float64) / 127.5 - 1)
    vis = None if alpha is None else alpha > 0.5
    tint = PLAINS_TINT if name in GRASS_TOPS + ("grass_block_side_overlay",) else \
        FOLIAGE_TINT if name in LEAVES else None
    seen = alb if alpha is None or name not in CUTOUT else alb * alpha[..., None] + 0.08 * (1 - alpha[..., None])
    rts_img, rts_std = rts_view(seen, tint=tint, rotate=name in ROTATED)
    report = {"normal_strength": round(k, 2), "tilt_mip0": round(mip_tilt(nxx, nyy, vis, 0), 1),
              "tilt_mip2": round(mip_tilt(nxx, nyy, vis), 1), "target": TARGETS[name]["tilt"],
              "rts_luma_std": round(rts_std, 3)}
    if preview_dir:
        shown = alb
        if name in GRASS_TOPS + ("grass_block_side_overlay",):
            shown = np.clip(alb * np.array([0.57, 0.74, 0.35]) / 0.62, 0, 1)   # a plains-like biome tint
        if name in LEAVES:
            shown = np.clip(alb * np.array(FOLIAGE_TINT) / 0.55, 0, 1)
        if alpha is not None:
            under = np.full_like(shown, 0.06) if name in CUTOUT else r["under"]
            shown = np.where(alpha[..., None] > 0.5, shown, under)
        img = lit_preview(shown, n_img)
        Image.fromarray((img * 255).astype(np.uint8), "RGB").resize((256, 256), Image.LANCZOS).save(
            os.path.join(preview_dir, name + "_lit.png"))
        if alpha is None or name in CUTOUT:
            Image.fromarray((np.clip(rts_img, 0, 1) * 255).astype(np.uint8), "RGB").save(
                os.path.join(preview_dir, name + "_rts.png"))
    return name, report


def _job(args):
    name, res, preview_dir, hires = args
    if hires and name in HIRES:
        res *= 2
    if name == "water_still":
        return name, write_water(res, preview_dir)
    if name == "water_flow":
        return name, write_water(res * 2, preview_dir, "water_flow")
    return make(name, res, preview_dir)


def build(res, preview_dir=None, jobs=None, hires=True):
    if tuple(TARGETS) != SEED_ORDER:
        raise SystemExit("TARGETS' order differs from SEED_ORDER - append new materials at the end of both (the seeds "
                         "follow the order; an insertion re-rolls every texture after it)")
    if set(RECIPES) != set(TARGETS):
        raise SystemExit("every TARGETS key needs exactly one recipe - no recipe: %s; no target: %s"
                         % (sorted(set(TARGETS) - set(RECIPES)), sorted(set(RECIPES) - set(TARGETS))))
    os.makedirs(BLOCK_DIR, exist_ok=True)
    if preview_dir:
        os.makedirs(preview_dir, exist_ok=True)
    # the two water strips are the longest jobs: first, so no worker is left finishing them alone
    names = ["water_flow", "water_still"] + list(TARGETS)
    jobs = max(1, jobs or os.cpu_count() or 1)
    work = [(n, res, preview_dir, hires) for n in names]
    if jobs == 1:
        done = dict(map(_job, work))
    else:
        from concurrent.futures import ProcessPoolExecutor
        with ProcessPoolExecutor(max_workers=jobs) as ex:
            done = dict(ex.map(_job, work))
    report = {n: done[n] for n in list(TARGETS) + ["water_still", "water_flow"]}
    write_models()
    # LabPBR format declaration: Iris / Oculus read assets/minecraft/optifine/texture.properties and give shader packs
    # MC_TEXTURE_FORMAT_LAB_PBR(_1_3) - without it a pack can't tell our _n / _s maps are LabPBR 1.3 (study notes,
    # Iris texture/format/TextureFormatLoader)
    opt = os.path.join(PACK_DIR, "assets", "minecraft", "optifine")
    os.makedirs(opt, exist_ok=True)
    with open(os.path.join(opt, "texture.properties"), "w") as f:
        f.write("format=lab-pbr/1.3\n")
    with open(os.path.join(PACK_DIR, "pack.mcmeta"), "w") as f:
        json.dump({"pack": {"pack_format": 15, "description": "Beyond and Tabs - Realistic (original, 128x/256x LabPBR)"}},
                  f, indent=1)
    if preview_dir:
        tiles = [Image.open(os.path.join(preview_dir, n + "_lit.png")).convert("RGB") for n in TARGETS]
        cols = 4
        rows = math.ceil(len(tiles) / cols)
        sheet = Image.new("RGB", (cols * 256, rows * 256), (20, 20, 20))
        for i, im in enumerate(tiles):
            sheet.paste(im, ((i % cols) * 256, (i // cols) * 256))
        sheet.save(os.path.join(preview_dir, "sheet.png"))
        with open(os.path.join(preview_dir, "order.txt"), "w") as f:
            f.write("\n".join(TARGETS))
    return report


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--res", type=int, default=128)
    ap.add_argument("--preview")
    ap.add_argument("--jobs", type=int, help="worker processes (default: one per CPU; 1 = no pool, for debugging)")
    ap.add_argument("--no-hires", action="store_true", help="build the HIRES textures at --res too (a comparison)")
    a = ap.parse_args()
    rep = build(a.res, a.preview, a.jobs, hires=not a.no_hires)
    print(json.dumps(rep, indent=1))
    print("wrote", BLOCK_DIR)


if __name__ == "__main__":
    main()
