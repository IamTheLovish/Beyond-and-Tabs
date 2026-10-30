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
    # round 28 (photo scans, SCAN section below): sat 0.05 keeps a little of the scan's own hue spread (dry and green
    # blades) under the biome tint; the side is fitted by the dirt's targets (its soil IS the dirt texture) and only
    # its tilt / smooth / pore are read here; lf 0 = the scan's relief as it is. Round 28b (eyes run 39884443333, the
    # plain close-ups: grass top Laplacian 39.7 vs main's 8.6 - speckle; stone +10-22%, dirt +8% with 26% less
    # mid-band): softer at the texel (soften) with the forms 3-12 texels across raised again (mid), calibrated on the
    # close-up's sampling offline (150 / 250 px per block, nearest texel + mips)
    # round 34 (lovish: a CLOVER LAWN, "richer saturation and shadows and depth"; the critic measured the r33 lawn's
    # in-game stdev at 0.036 vs the benchmark's 0.057): procedural clover (clover_lawn) - std 0.045 -> 0.065 in 2-3
    # value bands of plants plus the clump shadows, a warm-cool hue spread (sat 0.05 -> 0.08), the mean unchanged
    # (the environment palette); soften 2.4 -> 1.0 and no mid boost (no scan grain to tame)
    # r39 (eyes 41044148818: the clover read as single-frequency speckle at the RTS zoom, std 0.10 on screen vs the
    # benchmark's 0.06): std 0.065 -> 0.05 with more of it in the clumps (clover_lawn), tilt 18 -> 12 (the lit
    # leaflets added speckle), soften 0.4 -> 1.0, the mean a few degrees toward yellow-green
    # ao 1.0 -> 0.4: the AO darkened every gap between leaflets by up to 45% (AO std 0.125 - the largest part of the
    # on-screen speckle in a lit mip test); the clump shadow is already painted in the albedo
    "grass_block_top":  dict(luma=0.645, std=0.050, sat=0.11, lf=0.3, ao=0.4, tilt=9.0, smooth=24, pore=40, soften=1.0,
                             shoulder=0.82),
    "grass_block_side": dict(luma=0.31, std=0.050, sat=0.34, lf=0.0, tilt=14.0, smooth=26, pore=48, toe=0.235),
    # r34: tilt 16 -> 8 - the turf lip is a rounded roll with hanging blades; at 16 the solver's strength (the band is
    # thin, so its mip tilt is small) made every blade edge a wall whose normals drew a black line under the lip
    "grass_block_side_overlay": dict(luma=0.56, std=0.070, sat=0.00, lf=0.0, tilt=8.0, smooth=24, pore=40),
    "dirt":             dict(luma=0.34, std=0.045, sat=0.36, lf=0.0, tilt=12.0, smooth=26, pore=56, soften=1.4, mid=1.5,
                             toe=0.285),
    # round 18: tilt 16 -> 10 and less low-frequency boost (lf): in game with shaders the relief read as crumpled
    # foil (eyes structure run 39536435197) - the grain is for the light to catch, not lumps
    "stone":            dict(luma=0.52, std=0.050, sat=0.04, lf=0.0, ao=0.5, tilt=8.0, smooth=22, pore=12, soften=1.2,
                             mid=0.3),
    "cobblestone":      dict(luma=0.41, std=0.090, sat=0.08, tilt=20.5, smooth=32, pore=16),
    # Oct 29 (claude/environment-palette-oct29.md): luma 0.67 -> 0.50 - at 0.67 the river banks were the brightest
    # ground on screen (luma 0.72-0.75 after the grade, r54) and competed with buildings and units; the palette band
    # for sand is 0.48-0.58 (lights belong to what matters). Screen / texture was ~1.09 on r54.
    "sand":             dict(luma=0.50, std=0.030, sat=0.40, tilt=11.0, smooth=40, pore=60),
    # round 24-26 (PR #160, eyes close-ups of the terrain sampler, run 39838783742; shipped by lovish Oct 22 20:23 "you
    # can ship them and keep working on them"): gravel's smoothness 50 -> 24 (a blue sky sheen at the 30-degree view)
    # and tilt 26 -> 16 (bubble wrap); oak planks sat 0.46 -> 0.40, smooth 70 -> 45 (orange, satin); oak bark sat
    # 0.38 -> 0.34 (grey-brown) and tilt 27 -> 22; `soften` (see soften()) keeps the plain game free of pepper
    "gravel":           dict(luma=0.46, std=0.070, sat=0.07, tilt=16.0, smooth=24, pore=30, soften=0.8),
    # r35 (critic: the planks too dark and too close in value - oak ~0.55, jungle ~0.48, dark oak ~0.35): oak lifted
    "oak_planks":       dict(luma=0.55, std=0.055, sat=0.40, tilt=8.5, smooth=45, pore=24, soften=0.4, lf=0.3),
    "oak_log":          dict(luma=0.33, std=0.060, sat=0.34, tilt=22.0, smooth=40, pore=36, soften=2.0),
    "oak_log_top":      dict(luma=0.44, std=0.073, sat=0.48, tilt=12.0, smooth=40, pore=30),
    # round 3 (Oct 15): what the RTS view shows most after grass and water (the eyes ground census); new keys go at
    # the END - the seeds follow this order, so the older textures stay byte-identical. Targets = the single-block
    # numbers of pack-study run 38732322940 (polished andesite is near flat and polished; smooth stone is rough)
    # r31: narrower, lighter mortar and flatter faces - std 0.081 -> 0.055, tilt 18.4 -> 11 (it read as cobble)
    # r35 (the 1890 station render: stone_bricks, polished andesite, smooth stone and stone sat within 0.05 of each
    # other and the facade read as one flat grey mass): the greys step apart - stone_bricks 0.50 -> 0.44 with darker
    # recessed joints and more brick-to-brick spread (std 0.055 -> 0.065), polished andesite 0.49 -> 0.57 cooler and
    # smooth, smooth stone 0.54 -> 0.63 light and calm; stone stays 0.52 (the scan and the gates), andesite 0.56 -> 0.48
    "stone_bricks":     dict(luma=0.44, std=0.065, sat=0.03, tilt=11.0, smooth=18, pore=12, soften=0.8),
    "polished_andesite": dict(luma=0.57, std=0.035, sat=0.04, tilt=3.0, smooth=119, pore=4),
    "smooth_stone":     dict(luma=0.63, std=0.040, sat=0.04, tilt=17.7, smooth=16, pore=10),
    "smooth_stone_slab_side": dict(luma=0.63, std=0.040, sat=0.04, tilt=17.7, smooth=16, pore=10),
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
    "raw_iron_block":   dict(luma=0.53, std=0.080, sat=0.26, tilt=24.0, smooth=76, pore=10),   # r31: pink-grey, rust blooms
    # gold: the reference's 23 deg comes from an engraved pattern; a cast plate with tilt 18 came out as lumpy cheese.
    # Luma between the reference (0.63, which leans on a shader's reflections) and vanilla (0.81): without shaders
    # - most players - a 0.63 gold reads brown (pack-study-vanilla 38740868045)
    "gold_block":       dict(luma=0.72, std=0.050, sat=0.70, tilt=11.0, smooth=196, pore=0, f0=231),   # r31: polished
    # r31 (art director: "pale sandy planks with black holes"): darker oak staves, iron hoops with their own smoothness
    # (m_barrel_side / m_barrel_end paint smooth), the hoop bevel the main relief (tilt 17 -> 12)
    # r32 (critic: "the top is too dark and the hoop band doesn't read"): the heads lifted (0.26 -> 0.40) with the
    # spread up so the iron chime hoop stays dark against the oak (std 0.075 -> 0.11); the staves a step lighter too
    "barrel_side":      dict(luma=0.32, std=0.085, sat=0.46, tilt=12.0, smooth=57, pore=20),
    # r33 (critic: "black X too heavy"): a narrower, grey chime hoop - less spread (std 0.11 -> 0.08)
    "barrel_top":       dict(luma=0.40, std=0.080, sat=0.46, tilt=12.0, smooth=57, pore=20),
    "barrel_bottom":    dict(luma=0.40, std=0.080, sat=0.46, tilt=12.0, smooth=57, pore=20),
    # r31 (art director: "reads as packed dirt"): straw is pale gold - luma 0.54 -> 0.62, more spread (stalk-to-stalk
    # contrast), satin stalks (smooth 20 -> 42)
    # r31b (eyes: ours tan beside the benchmark's yellow): one step more yellow saturation (0.56 -> 0.66)
    # r32 (critic: "too tan, weak bands"): one step more golden saturation (0.66 -> 0.76), the twine darker and wider
    # r33 (critic, eyes 41029731853: ours 190/154/65 beside the benchmark's 215/196/23 - dimmer, tan): luma +15%
    # (0.63 -> 0.72), a more saturated yellow (0.76 -> 0.86), the stalk colour itself more lemon than orange
    "hay_block_side":   dict(luma=0.72, std=0.105, sat=0.86, tilt=21.4, smooth=42, pore=40),
    "hay_block_top":    dict(luma=0.71, std=0.095, sat=0.86, tilt=21.4, smooth=38, pore=40),
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
    # r35 (critic: the dark stone families "read 0.25-0.35 and merge into one mass" - lift them with clear joints):
    # blackstone 0.19 -> 0.27 / 0.29, deepslate 0.19 -> 0.27, its bricks 0.30 -> 0.38, tiles 0.21 -> 0.30, polished
    # 0.32 -> 0.38, cobbled 0.27 -> 0.32 - the family keeps its order (deepslate under stone_bricks at 0.44)
    # r39: the whole blackstone family a violet-black (sat 0.10 -> 0.13, the base hue aligned with blackstone's), so
    # the wall's body and its weathered blocks read as one stone, apart from blue deepslate and warm basalt
    "polished_blackstone":        dict(luma=0.27, std=0.030, sat=0.13, tilt=12.0, smooth=60, pore=4),
    "polished_blackstone_bricks": dict(luma=0.29, std=0.055, sat=0.13, tilt=18.0, smooth=40, pore=8),
    "deepslate_bricks":           dict(luma=0.38, std=0.060, sat=0.02, tilt=15.0, smooth=15, pore=10),
    # r33 (critic: "micro-mesh"): 3 x 3 tiles in a running bond, narrower joints - std 0.06 -> 0.05, tilt 18 -> 14
    "deepslate_tiles":            dict(luma=0.30, std=0.045, sat=0.01, tilt=14.0, smooth=30, pore=10),
    "deepslate":                  dict(luma=0.27, std=0.045, sat=0.14, tilt=21.0, smooth=12, pore=10),   # r39: blue-grey
    "deepslate_top":              dict(luma=0.27, std=0.040, sat=0.14, tilt=18.0, smooth=15, pore=10),
    # r33 (critic: "brown-grey"): blue-grey - sat 0.02 -> 0.14 on a cool base
    "polished_deepslate":         dict(luma=0.38, std=0.030, sat=0.14, tilt=4.5, smooth=90, pore=4),
    # r31: blue-grey slate (sat 0.03 -> 0.10), a touch lighter (0.24 -> 0.27), joints lower in contrast (std 0.075
    # -> 0.060), flatter flakes (tilt 21 -> 16)
    # r31b (eyes: washed-out mosaic): the joints back (std 0.06 -> 0.085, tilt 16 -> 20), cooler (sat 0.10 -> 0.12)
    "cobbled_deepslate":          dict(luma=0.32, std=0.085, sat=0.12, tilt=20.0, smooth=26, pore=14),
    "cut_copper":                 dict(luma=0.50, std=0.058, sat=0.55, tilt=12.0, smooth=104, pore=0),
    "oxidized_cut_copper":        dict(luma=0.55, std=0.065, sat=0.45, tilt=12.0, smooth=12, pore=20),
    "azalea_leaves":              dict(luma=0.43, std=0.080, sat=0.65, tilt=16.5, smooth=70, pore=180),
    "flowering_azalea_leaves":    dict(luma=0.42, std=0.085, sat=0.62, tilt=16.5, smooth=60, pore=180),
    "netherrack":                 dict(luma=0.19, std=0.055, sat=0.58, tilt=30.0, smooth=60, pore=30),
    "nether_bricks":              dict(luma=0.15, std=0.050, sat=0.35, tilt=18.0, smooth=50, pore=12),
    "prismarine_bricks":          dict(luma=0.58, std=0.075, sat=0.33, tilt=12.0, smooth=44, pore=10),
    "dark_prismarine":            dict(luma=0.31, std=0.070, sat=0.36, tilt=15.6, smooth=78, pore=10),
    "moss_block":                 dict(luma=0.40, std=0.060, sat=0.60, tilt=14.0, smooth=41, pore=60),
    # r32 (critic: moss patches must be "real green, readable from above"): the moss clumps carry the hue and the
    # spread - sat 0.12 -> 0.22, std 0.06 -> 0.075, a touch more relief for the cushions
    "mossy_stone_bricks":         dict(luma=0.41, std=0.075, sat=0.15, tilt=13.0, smooth=22, pore=36, soften=0.8),   # r35: with stone_bricks
    # round 12: single-block numbers from pack-study 38761293030 (reference; vanilla 38761302665 beside it); std
    # trimmed where the reference is busy from the RTS camera (podzol 0.120, coarse dirt 0.128, diorite 0.116)
    "sandstone": dict(luma=0.57, std=0.060, sat=0.54, tilt=12.6, smooth=21, pore=50),
    "sandstone_top": dict(luma=0.83, std=0.035, sat=0.26, tilt=6.7, smooth=27, pore=50),
    "sandstone_bottom": dict(luma=0.76, std=0.056, sat=0.29, tilt=7.8, smooth=21, pore=50),
    # r31 (art director: "muddy, half a stop dark"): luma +20% (0.27 -> 0.33, 0.33 -> 0.40), cleaner hue (sat up),
    # a little more mottle spread, a satin fired skin (smooth 29 -> 36)
    "red_terracotta": dict(luma=0.33, std=0.022, sat=0.62, tilt=3.5, smooth=36, pore=30, soften=1.0),
    "terracotta": dict(luma=0.40, std=0.022, sat=0.52, tilt=3.5, smooth=36, pore=30, soften=1.0),
    # r31: cut columns (m_columnar polished) - crisp grooves, flat faces: tilt 25 -> 14 / 20 -> 10, satin 39 -> 70
    "polished_basalt_side": dict(luma=0.36, std=0.065, sat=0.05, tilt=14.0, smooth=70, pore=8),
    # r32 (critic: the top "a tight cobble / hex grid, indistinguishable from basalt"): ground rings, lifted 15%
    # r33 (critic: "4 bullseyes"): the spread in the slow mottle and the rim only (std 0.05 -> 0.035), flatter
    "polished_basalt_top": dict(luma=0.38, std=0.035, sat=0.02, tilt=6.0, smooth=90, pore=8),
    # r32 (critic: "ivory not cream"): sat 0.12 -> 0.07; the top's spread in the pores, matte bone (smooth 80 -> 50)
    "bone_block_side": dict(luma=0.84, std=0.060, sat=0.07, tilt=16.0, smooth=50, pore=24),
    # r33 (critic: "marshmallows / knuckles"): one flat sawn surface - the spread in fine pores (std 0.08 -> 0.055),
    # flatter (tilt 18 -> 11)
    "bone_block_top": dict(luma=0.82, std=0.040, sat=0.07, tilt=7.0, smooth=44, pore=30, soften=1.0),
    # r32 (critic: "whites are cream" - mostly the shader's grade; the textures' own warm cast cut to near neutral)
    # r33 (critic: "corrugation"): three broad flutes, softer (std 0.04 -> 0.03, tilt 16 -> 10)
    "quartz_pillar": dict(luma=0.92, std=0.030, sat=0.05, tilt=8.0, smooth=138, pore=4),
    "quartz_pillar_top": dict(luma=0.90, std=0.050, sat=0.0, tilt=13.4, smooth=104, pore=4),   # r35: neutral white
    "quartz_bricks": dict(luma=0.87, std=0.032, sat=0.05, tilt=10.4, smooth=129, pore=4),
    "quartz_block_bottom": dict(luma=0.91, std=0.012, sat=0.03, tilt=2.5, smooth=145, pore=4),   # r32: seamless, no veins
    # round 17 (lovish Oct 20: "pepper dots"): the crystalline stones trimmed to read as one calm stone from the RTS
    # camera - andesite 0.075, granite 0.071, diorite 0.100 before
    "andesite": dict(luma=0.48, std=0.050, sat=0.03, lf=0.4, ao=0.6, tilt=7.0, smooth=49, pore=12),   # r35: a step under stone
    "granite": dict(luma=0.47, std=0.048, sat=0.39, lf=0.4, ao=0.6, tilt=7.0, smooth=42, pore=12),
    "diorite": dict(luma=0.73, std=0.070, sat=0.02, lf=0.4, ao=0.6, tilt=7.0, smooth=22, pore=12),
    "snow": dict(luma=0.94, std=0.019, sat=0.08, tilt=5.9, smooth=92, pore=0),   # r35: bluish-white
    "podzol_top": dict(luma=0.32, std=0.090, sat=0.40, tilt=20.0, smooth=41, pore=56),
    "coarse_dirt": dict(luma=0.36, std=0.080, sat=0.37, tilt=25.0, smooth=10, pore=56),
    "clay": dict(luma=0.53, std=0.035, sat=0.10,   # r21: read pink-white under the warm sun - bluer
    tilt=8.9, smooth=102, pore=64),
    "red_sand": dict(luma=0.52, std=0.040, sat=0.60, tilt=12.9, smooth=34, pore=60),
    "mud": dict(luma=0.22, std=0.045, sat=0.14, tilt=16.3, smooth=90, pore=64),   # r31: the cracks carry the spread
    # r35 (calcite / diorite / snow indistinguishable at 32 px): calcite a smooth bright white (std 0.075 -> 0.035,
    # tilt 19 -> 8), diorite keeps its speckle, snow the brightest and bluish
    "calcite": dict(luma=0.88, std=0.035, sat=0.01, tilt=8.0, smooth=70, pore=10),
    # round 18: tuff is a fine ash rock - its 7-texel crystals speckled without shaders (std 0.085 -> 0.060)
    "tuff": dict(luma=0.43, std=0.060, sat=0.06, lf=0.5, tilt=14.0, smooth=11, pore=20),
    # round 13: single-block numbers from pack-study 38762019919 (vanilla 38762030150 beside it); the reference's
    # stripped logs share one generic 23 deg relief - 14 here (bare wood is smoother than bark); diorite std trimmed
    "birch_planks": dict(luma=0.65, std=0.060,   # r21: board joints vanished at RTS zoom
    sat=0.33, tilt=6.0, smooth=76, pore=24),
    "jungle_planks": dict(luma=0.48, std=0.055, sat=0.46, tilt=7.1, smooth=59, pore=24),
    "acacia_planks": dict(luma=0.45, std=0.048, sat=0.70, tilt=6.0, smooth=80, pore=24),   # r31: orange, a touch lighter
    # r31 (art director: "too black, grain lost"): 0.19 -> 0.25, more spread for the grain (0.037 -> 0.050)
    "dark_oak_planks": dict(luma=0.35, std=0.050, sat=0.55, tilt=6.6, smooth=50, pore=24),   # r35: 0.25 -> 0.35
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
    # r31 (art director: "dull red-brown"): pinkish salmon, lighter (0.44 -> 0.52), the spread in the crystals
    # r32 (critic: "1.5 stops too dark / red"): 0.54 -> 0.68, pinker (sat 0.36 -> 0.26), the quartz grains' spread
    "polished_granite": dict(luma=0.68, std=0.055, sat=0.26, tilt=4.0, smooth=140, pore=4),
    "polished_diorite": dict(luma=0.75, std=0.070, sat=0.02, tilt=4.0, smooth=136, pore=4),
    "blackstone": dict(luma=0.25, std=0.035, sat=0.17, tilt=24.0, smooth=12, pore=12),   # r35: lifted; r39: violet-black
    # r30: m_obsidian_glass paints its own smoothness (faces 228). r31: luma 0.085 -> 0.15, std 0.05 -> 0.075 - in
    # game the r30b block still read near-black (its top face measured 0.053 on screen vs the benchmark's 0.11 base
    # with glints at 0.32 over a quarter of the face); the lift lives in the sheen bands, the glass between stays dark
    # r31b: brighter, more numerous glints (eyes 41018675091: the benchmark's top face glitters) - std 0.075 -> 0.09
    "obsidian": dict(luma=0.16, std=0.090, sat=0.20, tilt=10.0, smooth=228, pore=0),   # r31c: chroma cut (0.30 -> 0.20)
    "red_nether_bricks": dict(luma=0.11, std=0.035, sat=0.84, tilt=15.0, smooth=29, pore=12),
    "chiseled_stone_bricks": dict(luma=0.45, std=0.085, sat=0.03, tilt=22.0, smooth=25, pore=12),
    # r34: cracked dry earth (m_cracked_path) - the spread is the cracks' (std 0.05 -> 0.065), the value stays on
    # the palette band
    "dirt_path_top": dict(luma=0.47, std=0.065, sat=0.44, tilt=15.0, smooth=29, pore=50),
    "dirt_path_side": dict(luma=0.40, std=0.069, sat=0.46, tilt=18.0, smooth=18, pore=56),
    # round 14: single-block numbers, pack-study 38763481360 (vanilla 38763491796 beside it); metal and gem blocks
    # between the reference (which leans on shader reflections) and vanilla's brighter colours; relief trimmed on
    # the carved and plated blocks (the reference's 26-31 deg come from engraved patterns we draw flatter)
    "cracked_stone_bricks": dict(luma=0.43, std=0.068, sat=0.02, tilt=13.0, smooth=16, pore=12, soften=0.8),   # r32: fissures; r35: with stone_bricks
    "cracked_deepslate_bricks": dict(luma=0.36, std=0.062, sat=0.02, tilt=18.0, smooth=8, pore=10),   # r35: with the family
    "cracked_polished_blackstone_bricks": dict(luma=0.26, std=0.057, sat=0.13, tilt=22.0, smooth=19, pore=8),
    "chiseled_nether_bricks": dict(luma=0.15, std=0.060, sat=0.30, tilt=22.0, smooth=49, pore=12),
    "chiseled_polished_blackstone": dict(luma=0.27, std=0.058, sat=0.14, tilt=22.0, smooth=30, pore=8),   # r35; r39
    "reinforced_deepslate_side": dict(luma=0.40, std=0.150, sat=0.06, tilt=20.0, smooth=11, pore=8),
    "reinforced_deepslate_top": dict(luma=0.36, std=0.140, sat=0.06, tilt=20.0, smooth=16, pore=8),
    # round 28: the Rock035 scan (SCAN section) - toe keeps its darkest grain inside the albedo range (S3)
    # r31: columnar (m_columnar) - the ribs and joints carry the spread (std 0.05 -> 0.06) and the relief (tilt 12 ->
    # 16); soften 2.5 -> 1.0 (procedural, no scan grain to tame)
    # r39 (eyes 41044148818: blackstone / basalt / deepslate one dark grey): basalt a warm grey (sat 0.05 -> 0.10)
    "basalt_side": dict(luma=0.30, std=0.060, sat=0.10, tilt=16.0, smooth=30, pore=10, soften=1.0),
    "basalt_top": dict(luma=0.30, std=0.060, sat=0.10, tilt=14.0, smooth=26, pore=10, soften=1.0),
    # r32: a cut face - calm (std 0.06 -> 0.035), flat (tilt 13.9 -> 5), satin (25 -> 70); basalt keeps its columns
    # r33 (critic: "concrete pavers"): no seam or rim - the spread in the grain only (std 0.035 -> 0.025)
    "smooth_basalt": dict(luma=0.30, std=0.025, sat=0.04, tilt=4.0, smooth=70, pore=8),
    # r31 (art director: "reads as dirt"): grey-brown ash - sat 0.37 -> 0.20, a touch lighter, matte (smooth 60 -> 28)
    "soul_sand": dict(luma=0.28, std=0.050, sat=0.30, tilt=15.0, smooth=28, pore=48, soften=0.8),   # r31b: warmer
    # r31 (sampler 3: metals "painted stone"): chamfer + brushing in the relief (tilt 10 -> 13), smoother (147 -> 178)
    "iron_block": dict(luma=0.76, std=0.070, sat=0.0, tilt=13.0, smooth=178, pore=0, f0=230),   # r35: neutral (the grade adds the warmth)
    "netherite_block": dict(luma=0.30, std=0.065, sat=0.12, tilt=13.0, smooth=205, pore=0, f0=255),
    # r31: hard facets - more spread (bright tops, dark body) and tilt; emerald darker (0.42 -> 0.36), glossier
    # r33 (critic, eyes 41029731853: the gems need "brighter albedo bodies and smoother faces" so the shader's sky
    # reflection makes them glassy): diamond 0.70 -> 0.78, emerald 0.50 -> 0.58, both glass-smooth (253)
    "diamond_block": dict(luma=0.78, std=0.110, sat=0.35, tilt=14.0, smooth=253, pore=0, f0=44),
    # r32 (critic: "too dark and muddy"): 0.36 -> 0.50, a cleaner green (sat 0.60 -> 0.72)
    "emerald_block": dict(luma=0.58, std=0.100, sat=0.72, tilt=14.0, smooth=253, pore=0, f0=15),
    # r33 (critic, eyes 41029731853: the blues "crushed"): lapis lifted to a deep ultramarine that reads blue from
    # above - luma 0.22 -> 0.30, sat 0.55 -> 0.72
    # r36 (lovish lifted its protection; in game it read darker and duller than vanilla): a saturated, brighter
    # ultramarine (0.30 -> 0.38, sat 0.72 -> 0.86) with more gold pyrite
    "lapis_block": dict(luma=0.38, std=0.060, sat=0.86, tilt=7.6, smooth=70, pore=0, f0=15),
    "end_stone": dict(luma=0.80, std=0.075, sat=0.28, tilt=17.1, smooth=39, pore=20),
    # r33 (critic, eyes 41029731853: 206/204/188 "greenish grey, not white"): neutral (sat 0 = greyscale - the old
    # 0.04 fit blew a near-grey base's green up), luma 0.80 -> 0.87, a faint concrete mottle (std 0.008 -> 0.012)
    "white_concrete": dict(luma=0.87, std=0.012, sat=0.0, tilt=2.0, smooth=86, pore=10),
    # r31: 0.25 -> 0.30 and a real blue-violet (sat 0.30 -> 0.42) - it read grey-purple like blackstone
    # r31b: -20% chroma. r33 (critic: "crushed"): a clearer dusty blue - luma 0.30 -> 0.37, sat 0.33 -> 0.42
    "blue_terracotta": dict(luma=0.37, std=0.018, sat=0.42, tilt=3.5, smooth=36, pore=30, soften=1.0),
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
    "blackstone_top": dict(luma=0.24, std=0.033, sat=0.17, tilt=20.0, smooth=14, pore=12),   # r35; r39: violet-black
    # r31: near-white, the tile line subtle (std 0.03 -> 0.022), cool veins (sat 0.07 -> 0.05)
    # r32: the vein carries the hue (sat 0.05 -> 0.04 with a near-neutral body), a touch more spread for it
    "quartz_block_top": dict(luma=0.90, std=0.018, sat=0.03, tilt=3.0, smooth=145, pore=4),
    "quartz_block_side": dict(luma=0.90, std=0.018, sat=0.03, tilt=3.0, smooth=145, pore=4),
    "farmland": dict(luma=0.38, std=0.080, sat=0.38, tilt=20.0, smooth=22, pore=56, soften=0.8),     # r31: rows
    "farmland_moist": dict(luma=0.24, std=0.070, sat=0.40, tilt=18.0, smooth=60, pore=64, soften=0.8),
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
    "grass_block_top_v2",     # round 19: a second grass top (round 28: SCAN_VARIANT_OF)
    "cherry_log",             # Oct 21: map-tree cherries
    "cherry_log_top",
    "cherry_leaves",
    "exposed_cut_copper",     # round 23: copper stages
    "weathered_cut_copper",
    "stone_v2",               # round 28: the photo scans' variants (SCAN_VARIANT_OF)
    "stone_v3",
    "stone_v4",
    "stone_v5",
    "stone_v6",
    "dirt_v2",
    "dirt_v3",
    "dirt_v4",
    "dirt_v5",
    "dirt_v6",
    "grass_block_top_v3",
    "grass_block_top_v4",
    "grass_block_top_v5",
    "grass_block_top_v6",
    "grass_block_side_v2",
    "grass_block_side_v3",
    "grass_block_side_v4",
    "grass_block_side_v5",
    "grass_block_side_v6",
    "grass_block_side_overlay_v2",
    "grass_block_side_overlay_v3",
    "grass_block_side_overlay_v4",
    "grass_block_side_overlay_v5",
    "grass_block_side_overlay_v6",
    "basalt_side_v2",
    "basalt_side_v3",
    "basalt_side_v4",
    "basalt_side_v5",
    "basalt_side_v6",
    "basalt_top_v2",
    "basalt_top_v3",
    "basalt_top_v4",
    "basalt_top_v5",
    "basalt_top_v6",
    # round 33 (lovish: break the tiling of the big repeated building blocks - PROC_VARIANT_OF): two more textures
    # per block, their own seeds, appended last
    "stone_bricks_v2",
    "stone_bricks_v3",
    "cobblestone_v2",
    "cobblestone_v3",
    "quartz_block_top_v2",
    "quartz_block_top_v3",
    "quartz_block_side_v2",
    "quartz_block_side_v3",
    "quartz_block_bottom_v2",
    "quartz_block_bottom_v3",
    "oak_planks_v2",
    "oak_planks_v3",
    "spruce_planks_v2",
    "spruce_planks_v3",
    "dark_oak_planks_v2",
    "dark_oak_planks_v3",
    "birch_planks_v2",
    "birch_planks_v3",
    "deepslate_bricks_v2",
    "deepslate_bricks_v3",
    "polished_blackstone_bricks_v2",
    "polished_blackstone_bricks_v3",
    # round 34: mossy cobblestone and the emissive blocks - appended last
    "mossy_cobblestone",
    "magma_block",
    "redstone_ore",
    "deepslate_redstone_ore",
    "glowstone",
    "shroomlight",
    "sea_lantern",
    "bricks",                 # round 35
    "bricks_v2",
    "bricks_v3",
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


def m_cobbled_slate(F, n, cells=16):
    """Round 31 (art director: cobbled deepslate's stones "far too large (3-4 per face) with high-contrast cartoon
    edges"): a rubble wall of SLATE - flat angular flakes, 4x4 per face, each a plane at its own slight tilt with a
    narrow chipped bevel (not a dome), set in a thin dark joint that is only a little darker than the stone. The
    stones are blue-grey, each its own tone, with faint lamination lines (slate splits in layers) in the relief."""
    # r31b (eyes 41023221433, the one block WORSE than the benchmark: "contrast collapsed into a washed-out uniform
    # mosaic, no gap shadow, shaded side featureless"): stones of two sizes (a few large flakes among the small),
    # a dark open joint with AO (the gap shadow), the bevel rounder so the shaded side of each flake reads
    f1, f2, idx, k = F.voronoi(cells, 0.85)
    g1, g2, gidx, gk = F.voronoi(cells * 2, 0.9)
    big = (F.rng.random(k) < 0.6)[idx]                   # where a large flake sits; elsewhere two smaller ones
    f1, f2 = np.where(big, f1, g1), np.where(big, f2, g2)
    idx = np.where(big, idx, gidx + k)
    k = k + gk
    wu, wv = 0.006 * F.noise(4, 12, 0.5), 0.006 * F.noise(4, 12, 0.5)
    f1, f2 = F.warp(f1, wu, wv), F.warp(f2, wu, wv)
    idx = idx[(np.round((F.v + wv) * n).astype(int)) % n, (np.round((F.u + wu) * n).astype(int)) % n]
    edge = f2 - f1
    stone = smoothstep(0.006, 0.040, edge)                # the stone's top: a rounded bevel, then flat
    rng = F.rng
    tone = rng.uniform(0.78, 1.18, k)[idx]
    # each flake a plane at its own slight tilt (the chipped cleavage face), its own level
    ang = 2 * math.pi
    cx = np.arctan2(np.bincount(idx.ravel(), np.sin(ang * F.u).ravel(), k),
                    np.bincount(idx.ravel(), np.cos(ang * F.u).ravel(), k)) / ang
    cy = np.arctan2(np.bincount(idx.ravel(), np.sin(ang * F.v).ravel(), k),
                    np.bincount(idx.ravel(), np.cos(ang * F.v).ravel(), k)) / ang
    dx = (F.u - cx[idx] + 0.5) % 1.0 - 0.5
    dy = (F.v - cy[idx] + 0.5) % 1.0 - 0.5
    gx, gy, lvl = rng.standard_normal(k), rng.standard_normal(k), rng.uniform(-0.03, 0.03, k)
    plane = lvl[idx] + 0.25 * (gx[idx] * dx + gy[idx] * dy)
    lam = 0.5 * np.sin(2 * math.pi * (F.u * 14 + F.v * 9 + 0.6 * F.noise(2, 6, 0.5)))   # lamination lines
    lam = lam * smoothstep(0.4, 0.8, norm01(F.noise(2, 5, 0.5)))                          # only on some flakes
    grit = F.noise(20, 50, 0.3)
    H = 0.30 + 0.55 * np.sqrt(stone) + plane * stone + 0.015 * grit + 0.012 * lam * stone
    shade_stone = tone * (0.95 + 0.04 * grit + 0.05 * lam) * (0.82 + 0.18 * stone)
    shade = shade_stone * stone + (0.40 + 0.1 * grit) * (1 - stone) * tone   # the joint: dark, the gap's shadow
    hue = tone - 1.0
    jit = np.stack([-0.10 * hue, -0.03 * hue, 0.08 * hue], -1)                # lighter flakes bluer, darker warmer
    return H, tint((0.29, 0.32, 0.38), shade, jit)


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
    # r31 (art director: the speckle too fine - 2-3 px features at the RTS camera are fine): larger pebbles
    f1, f2, idx, k = F.voronoi(180, 1.0)                 # ~9.5 texels: the larger pebbles
    g1, g2, gidx, gk = F.voronoi(420, 1.0)               # ~6 texels: the small ones
    big = (rng.random(k) < 0.5)[idx]
    a1, a2 = np.where(big, f1, g1), np.where(big, f2, g2)
    pid = np.where(big, idx, gidx + k)
    dome = np.clip(1 - (2 * a1 / np.maximum(a1 + a2, 1e-6)) ** 2, 0, 1)
    tone = rng.random(k + gk)
    kind = rng.random(k + gk)
    # r32 (critic: gravel "brown - shift toward cool grey with mixed stones"; its luma stays, it sits on the
    # environment palette): most pebbles a cool grey, some blue-grey slate, a few warm tan, the rest dark
    kk = kind[pid][..., None]
    base = np.where(kk < 0.50, np.array([0.48, 0.49, 0.50]),
                    np.where(kk < 0.66, np.array([0.45, 0.47, 0.51]),
                             np.where(kk < 0.80, np.array([0.55, 0.50, 0.43]), np.array([0.36, 0.36, 0.38]))))
    H = 0.3 + 0.5 * np.sqrt(dome) + 0.015 * F.noise(16, 48, 0.2)
    shade = (0.85 + 0.25 * tone[pid]) * (0.72 + 0.28 * np.sqrt(dome))
    return H, np.clip(base * shade[..., None], 0, 1)


def m_sand(F, n, base=(0.86, 0.78, 0.58)):
    grains = F.noise(10, 28, 0.0)                        # r31: coarser (the speckle was too fine at RTS zoom)
    ripple_ph = F.v * 2 * math.pi * 5 + 1.2 * F.noise(1, 3, 0.5)
    ripples = np.sin(ripple_ph)
    H = 0.6 + 0.06 * ripples + 0.08 * grains + 0.05 * F.fbm(3, 3)
    hue = F.noise(3, 8, 0.4)
    jit = np.stack([0.02 * hue, 0.01 * hue, -0.03 * hue], -1)
    shade = 0.92 + 0.12 * norm01(H) + 0.05 * grains
    return H, tint(base, shade, jit)


def m_sand_sheet(F, blocks, base=(0.86, 0.78, 0.58)):
    """Round 35: wind ripples across a SHEET of blocks x blocks sand (Athena's "giant" model lays the sheet over the
    world by block position, so the crests run on from block to block instead of repeating every block). Crests
    every ~0.28 block, sinuous, with the asymmetric profile of a wind ripple (a gentle windward slope, a steep lee
    face), a coarse grain and a slow drift of tone; periodic over the sheet. F is a Field of the sheet's size."""
    cyc = 3.6 * blocks                                   # crests per sheet
    wander = 0.30 * F.noise(1, 3, 0.6) + 0.10 * F.noise(3, 8, 0.4)     # gently sinuous, nearly parallel
    ph = (F.v * cyc + wander + 0.04 * F.noise(8, 20, 0.3)) % 1.0
    ripple = np.where(ph < 0.72, ph / 0.72, 1 - (ph - 0.72) / 0.28)      # sawtooth: long rise, short drop
    ripple = ripple * ripple * (3 - 2 * ripple)          # eased
    grains = F.noise(10 * blocks, 28 * blocks, 0.0)
    drift = F.fbm(2, 3, 0.5)
    H = 0.5 + 0.22 * (ripple - 0.5) + 0.012 * grains + 0.04 * drift
    hue = F.noise(3, 8, 0.4)
    jit = np.stack([0.02 * hue, 0.01 * hue, -0.03 * hue], -1)
    # the crests catch the light, the lee lies a touch darker - in the colour too, for the plain game
    shade = 0.90 + 0.10 * ripple + 0.02 * grains + 0.04 * drift
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
    tufts = F.noise(3, 14, 1.2)
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


def m_grass_side(F, n):
    Hd, ad = m_dirt(F, n)
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
    # r25 drew a second end joint in some rows; r26 dropped it - its two draws stay, so every board keeps the tone it
    # had in round 26 (the build is pixel-identical to #160's round 26)
    F.rng.random(rows)
    F.rng.random(rows)
    # r26 (review: brickwork at RTS): no second joints, and only two of the four rows have a butt joint in this
    # block - the others run on into the next block (boards a block and more long)
    ju2 = np.ones_like(joint_u)
    joint_u = np.where(np.isin(row, [0, 2]), joint_u, 0.5)
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
TARGETS["grass_block_top_v2"] = dict(TARGETS["grass_block_top"])
GRASS_TOPS = ("grass_block_top", "grass_block_top_v2")
# Oct 21: cherry for the map trees (lovish's cherry references) - without these the cherries drew vanilla's flat
# textures beside the pack. Appended last (SEED_ORDER rule).
TARGETS["cherry_log"] = dict(luma=0.24, std=0.050, sat=0.42, tilt=22.0, smooth=50, pore=30)
TARGETS["cherry_log_top"] = dict(luma=0.42, std=0.040, sat=0.45, tilt=10.0, smooth=45, pore=30)
TARGETS["cherry_leaves"] = dict(luma=0.70, std=0.100, sat=0.30, ao=0.35, tilt=9.0, smooth=60, pore=180)
# round 23: the copper weathering stages in between (the capitol's roofs use all four); smooth is the base the
# recipe's own per-texel smoothness replaces
TARGETS["exposed_cut_copper"] = dict(luma=0.44, std=0.065, sat=0.42, tilt=12.0, smooth=90, pore=6)
TARGETS["weathered_cut_copper"] = dict(luma=0.46, std=0.075, sat=0.42, tilt=12.0, smooth=40, pore=14)
# Round 28: the photo scans' shared-border variants (the SCAN section: SCAN_VARIANTS textures per block, none turned)
# - appended last (SEED_ORDER rule); each takes its base texture's targets
SCAN_VARIANT_OF = {}
for _base in ("stone", "dirt", "grass_block_top", "grass_block_side", "grass_block_side_overlay", "basalt_side",
              "basalt_top"):
    for _k in range(2, 7):
        if _base == "grass_block_top" and _k == 2:
            continue                                  # grass_block_top_v2 exists since round 19
        SCAN_VARIANT_OF["%s_v%d" % (_base, _k)] = _base
        TARGETS["%s_v%d" % (_base, _k)] = dict(TARGETS[_base])
SCAN_VARIANT_OF["grass_block_top_v2"] = "grass_block_top"
GRASS_TOPS = GRASS_TOPS + tuple("grass_block_top_v%d" % k for k in range(3, 7))
# Round 33 (lovish chose variants to break the tiling of the big repeated blocks): the procedural building blocks
# get PROC_VARIANTS textures each - the same recipe on its own seed inside variant_mask's blob, the base tile in the
# border band (proc_variant), so neighbours always meet as the seamless tile meets itself; each is fitted to the
# base's targets, so a wall of variants keeps one mean value and saturation (no checkerboard). Only FULL-CUBE blocks
# (their blockstate is a single "" variant with no turns): the pack's blockstate picks one model per block at random
# (write_models); stairs, slabs and walls keep the base texture through vanilla's own models.
PROC_VARIANTS = 3
# block -> (the cube's model parent, its texture slots -> base texture); the variants replace every slot
PROC_VARIANT_BLOCKS = {
    "stone_bricks": ("cube_all", {"all": "stone_bricks"}),
    "cobblestone": ("cube_all", {"all": "cobblestone"}),
    "quartz_block": ("cube_column", {"end": "quartz_block_top", "side": "quartz_block_side"}),
    "smooth_quartz": ("cube_all", {"all": "quartz_block_bottom"}),
    "oak_planks": ("cube_all", {"all": "oak_planks"}),
    "spruce_planks": ("cube_all", {"all": "spruce_planks"}),
    "dark_oak_planks": ("cube_all", {"all": "dark_oak_planks"}),
    "birch_planks": ("cube_all", {"all": "birch_planks"}),
    "deepslate_bricks": ("cube_all", {"all": "deepslate_bricks"}),
    "polished_blackstone_bricks": ("cube_all", {"all": "polished_blackstone_bricks"}),
}
PROC_VARIANT_OF = {}
for _parent, _slots in PROC_VARIANT_BLOCKS.values():
    for _base in _slots.values():
        for _k in range(2, PROC_VARIANTS + 1):
            PROC_VARIANT_OF["%s_v%d" % (_base, _k)] = _base
            TARGETS["%s_v%d" % (_base, _k)] = dict(TARGETS[_base])
# the planks' boards run on from block to block (two of the four rows have no butt joint in the tile): their blob
# edge is feathered over 12 texels, so a board's tone drifts along its length instead of stepping
PROC_FEATHER = {b: 12.0 for b in ("oak_planks", "spruce_planks", "dark_oak_planks", "birch_planks")}
# Round 34 (lovish: "hyper realism" - the ground blocks and the glowing blocks; claude/handoff r34): new textures,
# appended last (SEED_ORDER rule). Mossy cobblestone takes cobblestone's numbers with the moss's green on top; the
# emissive blocks (magma, redstone ore, glowstone, shroomlight, sea lantern) paint their own emission into _s alpha
# (LabPBR: 0-254 = glow strength, 255 = none) on the hot / lit parts only - their luma is the UNLIT albedo's
TARGETS["mossy_cobblestone"] = dict(luma=0.37, std=0.090, sat=0.22, tilt=20.5, smooth=30, pore=24)
# the glowing blocks are painted (their parts keep their own colours - not fitted): luma / std / sat are reference
# notes only; tilt is solved as usual, smooth is the base the recipe's own map replaces
TARGETS["magma_block"] = dict(luma=0.30, std=0.120, sat=0.60, tilt=16.0, smooth=60, pore=8)
TARGETS["redstone_ore"] = dict(luma=0.48, std=0.090, sat=0.20, tilt=10.0, smooth=22, pore=12)
TARGETS["deepslate_redstone_ore"] = dict(luma=0.24, std=0.080, sat=0.25, tilt=18.0, smooth=12, pore=10)
TARGETS["glowstone"] = dict(luma=0.70, std=0.120, sat=0.70, tilt=14.0, smooth=150, pore=0)
TARGETS["shroomlight"] = dict(luma=0.55, std=0.100, sat=0.75, tilt=14.0, smooth=80, pore=20)
TARGETS["sea_lantern"] = dict(luma=0.80, std=0.080, sat=0.15, tilt=8.0, smooth=150, pore=0)
# round 35: red clay bricks (m_clay_bricks) - the build census's biggest missing block; a full cube, so it gets the
# r33 procedural variants too (PROC_VARIANT_BLOCKS below)
TARGETS["bricks"] = dict(luma=0.40, std=0.070, sat=0.52, tilt=12.0, smooth=24, pore=40, soften=0.6)
PROC_VARIANT_BLOCKS["bricks"] = ("cube_all", {"all": "bricks"})
for _k in range(2, PROC_VARIANTS + 1):
    PROC_VARIANT_OF["bricks_v%d" % _k] = "bricks"
    TARGETS["bricks_v%d" % _k] = dict(TARGETS["bricks"])
# round 36: the dyed families (DYES, colour_targets) and the bookshelf - appended last; red_ / blue_terracotta and
# terracotta keep their r31-r33 entries above (the family's anchors)
DYES = {  # name: (wool, concrete, terracotta) rgb 0..1
    "white":      ((0.91, 0.91, 0.90), (0.82, 0.84, 0.84), (0.82, 0.70, 0.63)),
    "light_gray": ((0.58, 0.58, 0.55), (0.50, 0.50, 0.46), (0.54, 0.43, 0.39)),
    "gray":       ((0.28, 0.30, 0.31), (0.23, 0.24, 0.25), (0.26, 0.20, 0.17)),
    "black":      ((0.11, 0.11, 0.12), (0.07, 0.07, 0.09), (0.16, 0.10, 0.08)),
    "brown":      ((0.45, 0.29, 0.17), (0.38, 0.24, 0.13), (0.31, 0.21, 0.14)),
    "red":        ((0.64, 0.17, 0.15), (0.57, 0.14, 0.13), (0.57, 0.25, 0.19)),
    "orange":     ((0.94, 0.48, 0.10), (0.88, 0.40, 0.03), (0.64, 0.34, 0.15)),
    "yellow":     ((0.96, 0.78, 0.18), (0.94, 0.70, 0.10), (0.73, 0.53, 0.15)),
    "lime":       ((0.48, 0.76, 0.14), (0.40, 0.68, 0.11), (0.42, 0.47, 0.20)),
    "green":      ((0.33, 0.44, 0.12), (0.29, 0.37, 0.14), (0.30, 0.33, 0.17)),
    "cyan":       ((0.11, 0.58, 0.60), (0.09, 0.50, 0.56), (0.34, 0.38, 0.38)),
    "light_blue": ((0.26, 0.70, 0.86), (0.16, 0.56, 0.78), (0.45, 0.43, 0.54)),
    "blue":       ((0.22, 0.26, 0.66), (0.18, 0.19, 0.58), (0.30, 0.36, 0.52)),
    "purple":     ((0.55, 0.28, 0.76), (0.43, 0.14, 0.64), (0.47, 0.28, 0.35)),
    "magenta":    ((0.76, 0.28, 0.72), (0.68, 0.19, 0.64), (0.59, 0.35, 0.43)),
    "pink":       ((0.93, 0.56, 0.68), (0.85, 0.40, 0.56), (0.63, 0.31, 0.31)),
}


def powder_colour(rgb):
    """The powder's colour from its concrete's: lighter and less saturated (dry pigment on white filler)."""
    rgb = np.asarray(rgb, dtype=np.float64)
    grey = rgb[0] * 0.2126 + rgb[1] * 0.7152 + rgb[2] * 0.0722
    return tuple(np.clip(0.72 * rgb + 0.28 * grey + 0.10, 0, 1))


def colour_targets(rgb, std, **kw):
    """TARGETS entry for a painted colour: its own luma and saturation (the fit keeps the hue), the given spread."""
    rgb = np.asarray(rgb, dtype=np.float64)
    L = float(rgb[0] * 0.2126 + rgb[1] * 0.7152 + rgb[2] * 0.0722)
    sat = float((rgb.max() - rgb.min()) / max(rgb.max(), 1e-6))
    return dict(luma=L, std=std, sat=sat, **kw)


R36_FAMILY = []
for _c, (_w, _k, _t) in DYES.items():
    TARGETS[_c + "_wool"] = colour_targets(_w, 0.030, tilt=6.0, smooth=8, pore=48)
    R36_FAMILY.append(_c + "_wool")
for _c, (_w, _k, _t) in DYES.items():
    if _c + "_concrete" not in TARGETS:              # white_concrete keeps its r33 entry (m_clayish, neutral)
        TARGETS[_c + "_concrete"] = colour_targets(_k, 0.018, tilt=3.0, smooth=64, pore=10)
        R36_FAMILY.append(_c + "_concrete")
for _c, (_w, _k, _t) in DYES.items():
    if _c + "_terracotta" not in TARGETS:
        TARGETS[_c + "_terracotta"] = colour_targets(_t, 0.022, tilt=3.5, smooth=36, pore=30, soften=1.0)
        R36_FAMILY.append(_c + "_terracotta")
TARGETS["bookshelf"] = dict(luma=0.36, std=0.090, sat=0.42, tilt=14.0, smooth=40, pore=24)
R36_FAMILY.append("bookshelf")
SEED_ORDER = SEED_ORDER + tuple(R36_FAMILY)       # (the order check in build(): appended last)
# round 37: the 14 other stained glasses (+ pane tops) from the DYES wool hues, chiseled quartz, the chiseled
# bookshelf's four textures, and smooth stone's flagstone sheet (GIANT) - appended last
R37 = []
for _c, (_w, _k, _t) in DYES.items():
    if _c in ("red", "cyan"):
        continue
    _rgb = tuple(min(1.0, 0.25 + 0.75 * v) for v in _w)
    TARGETS[_c + "_stained_glass"] = dict(colour_targets(_rgb, 0.030, tilt=4.0, smooth=235, pore=0, hspan=0.05))
    TARGETS[_c + "_stained_glass_pane_top"] = dict(colour_targets(_rgb, 0.030, tilt=4.0, smooth=200, pore=0))
    R37 += [_c + "_stained_glass", _c + "_stained_glass_pane_top"]
TARGETS["chiseled_quartz_block"] = dict(luma=0.90, std=0.030, sat=0.0, tilt=10.0, smooth=140, pore=4)
TARGETS["chiseled_quartz_block_top"] = dict(luma=0.90, std=0.025, sat=0.0, tilt=6.0, smooth=140, pore=4)
TARGETS["chiseled_bookshelf_top"] = dict(luma=0.50, std=0.055, sat=0.40, tilt=8.0, smooth=45, pore=24)
TARGETS["chiseled_bookshelf_side"] = dict(luma=0.46, std=0.070, sat=0.40, tilt=12.0, smooth=45, pore=24)
TARGETS["chiseled_bookshelf_empty"] = dict(luma=0.36, std=0.090, sat=0.40, tilt=14.0, smooth=40, pore=24)
TARGETS["chiseled_bookshelf_occupied"] = dict(luma=0.38, std=0.090, sat=0.42, tilt=14.0, smooth=40, pore=24)
R37 += ["chiseled_quartz_block", "chiseled_quartz_block_top", "chiseled_bookshelf_top", "chiseled_bookshelf_side",
        "chiseled_bookshelf_empty", "chiseled_bookshelf_occupied"]
SEED_ORDER = SEED_ORDER + tuple(R37)
# round 38: concrete powders, the full copper blocks, glazed terracottas, and the odd blocks of the builds
R38 = []
for _c, (_w, _k, _t) in DYES.items():
    TARGETS[_c + "_concrete_powder"] = colour_targets(powder_colour(_k), 0.030, tilt=8.0, smooth=10, pore=56)
    R38.append(_c + "_concrete_powder")
for _name, _base in (("copper_block", "cut_copper"), ("exposed_copper", "exposed_cut_copper"),
                     ("weathered_copper", "weathered_cut_copper"), ("oxidized_copper", "oxidized_cut_copper")):
    TARGETS[_name] = dict(TARGETS[_base], tilt=9.0)
    R38.append(_name)
for _c, (_w, _k, _t) in DYES.items():
    _g = tuple(0.55 * a + 0.45 * b for a, b in zip(_k, _t))        # the glaze: between the concrete and the clay
    TARGETS[_c + "_glazed_terracotta"] = colour_targets(_g, 0.080, tilt=5.0, smooth=205, pore=0)
    R38.append(_c + "_glazed_terracotta")
TARGETS["crafting_table_top"] = dict(luma=0.50, std=0.060, sat=0.40, tilt=9.0, smooth=45, pore=24)
TARGETS["crafting_table_side"] = dict(luma=0.48, std=0.060, sat=0.40, tilt=9.0, smooth=45, pore=24)
TARGETS["crafting_table_front"] = dict(luma=0.48, std=0.070, sat=0.36, tilt=9.0, smooth=45, pore=24)
TARGETS["rooted_dirt"] = dict(luma=0.38, std=0.070, sat=0.36, tilt=14.0, smooth=24, pore=56)
TARGETS["mushroom_stem"] = dict(luma=0.74, std=0.045, sat=0.14, tilt=10.0, smooth=40, pore=40)
TARGETS["composter_side"] = dict(luma=0.34, std=0.070, sat=0.36, tilt=10.0, smooth=40, pore=24)
TARGETS["composter_top"] = dict(luma=0.24, std=0.120, sat=0.30, tilt=14.0, smooth=30, pore=24)
TARGETS["composter_bottom"] = dict(luma=0.34, std=0.060, sat=0.36, tilt=8.0, smooth=40, pore=24)
TARGETS["composter_compost"] = dict(luma=0.26, std=0.060, sat=0.40, tilt=14.0, smooth=24, pore=56)
TARGETS["composter_ready"] = dict(luma=0.30, std=0.070, sat=0.34, tilt=14.0, smooth=24, pore=56)
TARGETS["note_block"] = dict(luma=0.36, std=0.090, sat=0.40, tilt=12.0, smooth=45, pore=24)
TARGETS["jukebox_side"] = dict(luma=0.40, std=0.080, sat=0.42, tilt=10.0, smooth=50, pore=24)
TARGETS["jukebox_top"] = dict(luma=0.40, std=0.070, sat=0.40, tilt=8.0, smooth=50, pore=24)
R38 += ["crafting_table_top", "crafting_table_side", "crafting_table_front", "rooted_dirt", "mushroom_stem",
        "composter_side", "composter_top", "composter_bottom", "composter_compost", "composter_ready", "note_block",
        "jukebox_side", "jukebox_top"]
SEED_ORDER = SEED_ORDER + tuple(R38)
# round 39: the battlefield wall's mud bricks and packed mud (tan, the sampler's bricks' cousin) - appended last
TARGETS["mud_bricks"] = dict(luma=0.45, std=0.055, sat=0.42, tilt=10.0, smooth=20, pore=50, soften=0.6)
TARGETS["packed_mud"] = dict(luma=0.46, std=0.040, sat=0.42, tilt=8.0, smooth=18, pore=56, soften=0.8)
SEED_ORDER = SEED_ORDER + ("mud_bricks", "packed_mud")


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
    # r31 (art director: raw iron "toward pink-grey with rust blooms", it sat in the salmon band with the path and
    # red sand): the lumps a pink-grey metal, the rust in BLOOMS - a few orange-brown patches that grow out of the
    # hollows - rather than every hollow orange
    rust = np.array([0.60, 0.34, 0.20])
    metal = np.array([0.66, 0.60, 0.58])
    bloom = smoothstep(0.45, 0.8, norm01(F.fbm(2, 3, 0.55)))                # where the rust has bloomed
    rusty = np.clip((1 - worn) * (0.25 + 0.75 * bloom), 0, 1)
    shade = 0.70 + 0.35 * norm01(H) - 0.15 * pits
    alb = (rust[None, None, :] * rusty[..., None] + metal[None, None, :] * (1 - rusty[..., None])) * shade[..., None]
    f0 = np.where(rusty < 0.5, IRON, 10)
    return H, np.clip(alb, 0, 1), f0


def m_netherite(F, n, rgb=(0.30, 0.27, 0.29)):
    """Round 31 (art director: netherite "dark polished with bolted bosses"): m_plate's dark polished plate with
    four round bolted bosses (a dome in a ring seat) inset from the corners, each a touch brighter on its crown."""
    H, alb, smooth = m_plate(F, n, rgb)
    cs = np.array([0.22, 0.78])
    du = np.min(np.abs(F.u[..., None] - cs[None, None, :]), -1)
    dv = np.min(np.abs(F.v[..., None] - cs[None, None, :]), -1)
    r = np.sqrt(du * du + dv * dv)
    dome = np.clip(1 - (r / 0.055) ** 2, 0, 1)
    seat = smoothstep(0.055, 0.062, r) * (1 - smoothstep(0.070, 0.080, r))   # the ring round the boss
    H = H + 0.14 * np.sqrt(dome) - 0.05 * seat
    alb = alb * (0.96 + 0.12 * dome - 0.15 * seat)[..., None]
    smooth = np.where(dome > 0, 205 + 20 * dome, smooth)
    return H, alb, smooth


def m_plate(F, n, rgb):
    """Round 23: a cast block of metal: m_gold's plate (bevel, cast dents, polish scratches) in another colour,
    plus a per-texel smoothness - the split test (eyes 39675571483 / 39675576144, claude/diag-metals) showed the
    metals read PAINTED under Complementary: the _s panel (D) was indistinguishable from the grey reference (a
    uniform 150-200 smoothness on an F0-230 metal gives a soft wide sheen like gloss paint), so the metal signal
    has to come from the smoothness map itself. Used for netherite (F0 255 - the albedo as F0)."""
    H, alb, smooth = m_metal_plate(F, n)
    L = alb @ np.array([0.2126, 0.7152, 0.0722])
    return H, tint(rgb, L / max(L.mean(), 1e-6)), smooth


def brushed(F, strength=1.0, coarse=False):
    """Brushed-metal grain: long fine streaks along u (a rolled / ground plate), as a tone 0-1 around 0.5."""
    # fine (40-64 cycles: 2-3 texels) so the mipmaps average it away at RTS distance - the streaks are a close-up
    # and reflection signal, not a stripe pattern on the block (v1 at 8-64 cycles drew vertical bars at RTS zoom)
    # r33 (critic, eyes 41029731853: "fine glitter grain" on the gold and iron tops - with the shader now reading the
    # specular map, per-texel smoothness steps sparkle): coarse = the brushing at 4-12 texels (10-32 cycles), long
    # along u, so the streaks are bands the mipmaps keep as a soft anisotropy rather than glitter
    if coarse:
        g = F.noise(12, 32, 0.2, sx=1.0, sy=40.0) + 0.3 * F.noise(32, 48, 0.0, sx=1.0, sy=30.0)
    else:
        g = F.noise(40, 64, 0.0, sx=1.0, sy=14.0) + 0.35 * F.noise(16, 40, 0.0, sx=1.0, sy=14.0)
    return 0.5 + 0.5 * np.tanh(0.5 * strength * g)


def m_metal_plate(F, n, bevel=0.07, groove=0.012, dent=0.012, brush=1.0, smooth_base=165, smooth_var=55,
                  coarse=False):
    """Round 23: the shared cast-and-ground metal plate. Height: a bevelled rim, broad cast dents, the brushing as
    a fine ripple. Albedo: a flat metal tone with the brushing (real polished metal is near-uniform in albedo - its
    look is all reflection). Smoothness: HIGH on the ground faces (the sky and sun reflect crisp), lower in the
    brushing grain and the dents - a per-texel map, the anisotropic streak that makes metal read as metal."""
    # r31 (art director, sampler 3: the metals "read as painted stone" - need a plate bevel in the height so the sun
    # catches an edge, the brushing in the normal, smoothness ~160-200): the rim is a straight 45-degree CHAMFER
    # (linear, not a smoothstep pillow) a `bevel` wide, the brushing goes into the height at 4x (the normals carry
    # the anisotropic streak), the dents stay broad and shallow
    d = np.minimum(np.minimum(F.u, 1 - F.u), np.minimum(F.v, 1 - F.v))
    bev = smoothstep(0.0, groove, d) * np.clip((d - groove) / max(bevel - groove, 1e-6), 0, 1)
    dents = F.noise(2, 6, 0.6)
    br = brushed(F, brush, coarse)
    wear = smoothstep(0.3, 0.9, norm01(F.fbm(2, 3, 0.5)))        # patches handled more (duller)
    H = 0.35 + 0.5 * bev + dent * dents + 0.012 * (br - 0.5)
    shade = (0.93 + 0.04 * dents + 0.025 * (br - 0.5)) * (0.72 + 0.28 * bev)
    alb = tint((0.80, 0.80, 0.80), shade)
    smooth = smooth_base + smooth_var * (br - 0.5) - 20 * wear - 25 * (1 - bev)
    return H, alb, np.clip(smooth, 40, 245)


def m_iron(F, n):
    """Round 23: an iron block as a riveted hot-rolled plate - brushed, a rivet row inside the bevel, the plate
    faces a cool neutral grey (the warm cast in the eyes stills was the rig's low sun: the grey reference block
    took the same tint)."""
    # r33: coarse brushing (4+ texels), the smoothness swing halved - no glitter grain under the shader
    H, alb, smooth = m_metal_plate(F, n, bevel=0.07, groove=0.010, dent=0.010, brush=1.0, smooth_base=185,
                                   smooth_var=26, coarse=True)
    # rivets: 4 per side, round domes at 1/8 block in from the edge
    cx = np.array([0.125, 0.375, 0.625, 0.875])
    du = np.min(np.abs(F.u[..., None] - cx[None, None, :]), -1)
    dv = np.min(np.abs(F.v[..., None] - cx[None, None, :]), -1)
    inset = 0.09
    r = 0.028
    dom_u = np.sqrt(np.minimum(np.abs(F.u - inset), np.abs(F.u - 1 + inset)) ** 2 + dv ** 2)
    dom_v = np.sqrt(np.minimum(np.abs(F.v - inset), np.abs(F.v - 1 + inset)) ** 2 + du ** 2)
    d = np.minimum(dom_u, dom_v)
    dome = np.clip(1 - (d / r) ** 2, 0, 1)
    head = dome > 0
    H = np.where(head, H + 0.10 * np.sqrt(dome), H)
    alb = np.where(head[..., None], alb * (0.92 + 0.12 * np.sqrt(dome))[..., None], alb)
    smooth = np.where(head, 120 + 40 * dome, smooth)
    return H, alb, smooth


def m_gold_r23(F, n):
    """Round 23 gold: a cast plate, finer polish than iron (less brushing), warm yellow, calm smoothness with the
    polish grain."""
    H, alb, smooth = m_metal_plate(F, n, bevel=0.08, groove=0.012, dent=0.012, brush=0.6, smooth_base=200,
                                   smooth_var=16, coarse=True)        # r33: coarse polish grain, no glitter
    L = alb @ np.array([0.2126, 0.7152, 0.0722])
    return H, tint((0.98, 0.78, 0.25), L / max(L.mean(), 1e-6)), smooth


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


def m_gem(F, n, body, top, inclusions=0.0, edge_glow=0.12):
    """Round 31 (art director, sampler 3: the gems "read as blobs / cabbage - 3-5 large hard facets per face, deep
    body colour with bright facet tops, low roughness, emerald darker with inclusion streaks"): four Voronoi cells
    per face (jittered: 3-5 visible facets), each a FLAT plane at its own tilt (m_facets' pyramids rounded into
    cabbage), meeting at hard steps; a facet's colour runs from the deep body colour to the bright top colour by how
    its plane faces the key light, so some facets blaze and some lie dark. inclusions: thin dark streaks along one
    direction inside the facets (emerald's jardin)."""
    rng = F.rng
    f1, f2, idx, k = F.voronoi(4, 0.8)
    ang = 2 * math.pi
    cx = np.arctan2(np.bincount(idx.ravel(), np.sin(ang * F.u).ravel(), k),
                    np.bincount(idx.ravel(), np.cos(ang * F.u).ravel(), k)) / ang
    cy = np.arctan2(np.bincount(idx.ravel(), np.sin(ang * F.v).ravel(), k),
                    np.bincount(idx.ravel(), np.cos(ang * F.v).ravel(), k)) / ang
    dx = (F.u - cx[idx] + 0.5) % 1.0 - 0.5
    dy = (F.v - cy[idx] + 0.5) % 1.0 - 0.5
    gx, gy = rng.uniform(-1, 1, k), rng.uniform(-1, 1, k)
    lvl = rng.uniform(-0.08, 0.08, k)
    plane = lvl[idx] + 0.45 * (gx[idx] * dx + gy[idx] * dy)
    H = 0.6 + plane
    edge = 1 - smoothstep(0.0, 0.006, f2 - f1)
    # the key light from the top left: a facet's plane normal ~ (-gx, -gy, 1)
    lx, ly, lz = -0.45, -0.55, 0.70
    nl = (-gx * lx - gy * ly + lz) / np.sqrt(gx * gx + gy * gy + 1) / math.sqrt(lx * lx + ly * ly + lz * lz)
    catch = np.clip((nl[idx] - 0.45) / 0.55, 0, 1) ** 1.3                   # a soft ramp: not one lit facet a block
    alb = np.asarray(body)[None, None, :] * (1 - catch[..., None]) + np.asarray(top)[None, None, :] * catch[..., None]
    alb = alb * (1 + edge_glow * edge)[..., None]                  # a bright line where two facets meet
    if inclusions > 0:
        line = np.abs(F.noise(8, 24, 0.5, sx=1.0, sy=6.0))
        inc = (1 - smoothstep(0.0, 0.05, line)) * smoothstep(0.4, 0.8, norm01(F.noise(2, 5, 0.5))) * inclusions
        alb = alb * (1 - 0.45 * inc)[..., None]
        H = H - 0.01 * inc
    bev = frame(F, 0.05, 0.010)
    H = H * (0.85 + 0.15 * bev)
    return H, np.clip(alb * (0.8 + 0.2 * bev)[..., None], 0, 1)


def m_obsidian_glass(F, n, cells=4):
    """Round 30 (lovish, Oct 30: "I really like how the obsidian looked in complementary ... I want our textures more
    realistic than his"): obsidian as what it is - volcanic glass broken along conchoidal fractures, not r22b's flat
    leaded-glass facets. A few large fracture faces with CURVED edges (the cell metric is warped by slow noise, so no
    face is a polygon), each a shallow shell-shaped scoop round its own point of impact off to one side, with faint
    ripple rings (Wallner lines) that widen and fade away from the impact - in the relief only, so the light finds
    them and the colour stays calm. Where two faces meet the glass is thin: some aretes (not all - a uniform web of
    light lines read as crazed tiles in the v1 sheet) catch a smoky brown-grey edge and are rougher than the faces.
    The faces are near-black with a cold violet depth and slow flow banding; glass-smooth (faces ~228, aretes down to
    ~130), so a shader's sky and sun reflections give the gloss - the albedo stays dark, the light does the work.
    No inclusion specks: v1's tiny violet dots were the "dots of pepper" fault (round 25)."""
    rng = F.rng
    k = max(2, int(round(math.sqrt(cells))))
    gx, gy = np.meshgrid((np.arange(k) + 0.5) / k, (np.arange(k) + 0.5) / k)
    pts = (np.stack([gx.ravel(), gy.ravel()], 1) + (rng.random((k * k, 2)) - 0.5) * 1.0 / k) % 1.0
    m = len(pts)
    cell = 1.0 / k
    ang = rng.uniform(0, 2 * math.pi, m)
    imp = (pts + 0.6 * cell * np.stack([np.cos(ang), np.sin(ang)], 1)) % 1.0
    curv = rng.uniform(0.6, 1.4, m)                 # how deep each scoop is
    lam = rng.uniform(0.035, 0.05, m)               # ripple spacing at the impact: 4-6 texels at 128x
    tone = rng.uniform(0.94, 1.06, m)               # glass is uniform: faces differ only a little
    weight = rng.uniform(-0.04, 0.04, m)            # additive weights: faces of unequal size
    lit_edge = rng.random((m, m)) < 0.35            # which face pairs show a smoky edge
    lit_edge = lit_edge | lit_edge.T
    # warp the metric so the face edges curve (slow, periodic)
    wu = 0.05 * F.noise(1, 3, 0.5)
    wv = 0.05 * F.noise(1, 3, 0.5)
    U, V = (F.u + wu) % 1.0, (F.v + wv) % 1.0
    f1 = np.full((n, n), 9.0)
    f2 = np.full((n, n), 9.0)
    idx = np.zeros((n, n), dtype=np.int32)
    idx2 = np.zeros((n, n), dtype=np.int32)
    for i, (px, py) in enumerate(pts):
        dx = (U - px + 0.5) % 1.0 - 0.5
        dy = (V - py + 0.5) % 1.0 - 0.5
        d = np.sqrt(dx * dx + dy * dy) + weight[i]
        closer = d < f1
        second = ~closer & (d < f2)
        f2 = np.where(closer, f1, np.where(second, d, f2))
        idx2 = np.where(closer, idx, np.where(second, i, idx2))
        idx = np.where(closer, i, idx)
        f1 = np.where(closer, d, f1)
    ix, iy = imp[idx, 0], imp[idx, 1]
    di = np.sqrt(((U - ix + 0.5) % 1.0 - 0.5) ** 2 + ((V - iy + 0.5) % 1.0 - 0.5) ** 2) / cell
    H = 0.30 * curv[idx] * np.minimum(di, 1.8) ** 2   # the shell: lowest at the impact
    # ripples widen with distance (phase ~ sqrt) and fade; slightly wavy, as real ones are
    phase = np.sqrt(di + 0.05) * cell / lam[idx] + 0.3 * F.noise(3, 8, 0.5)
    ripple = np.sin(2 * math.pi * phase) * np.exp(-2.0 * di)
    H = H + 0.02 * ripple
    arete = 1 - smoothstep(0.0, 0.012, f2 - f1)
    H = H + 0.18 * arete
    lit = lit_edge[idx, idx2] * (0.5 + 0.5 * norm01(F.noise(2, 6, 0.5)))   # a lighter edge that comes and goes
    # flow banding: slow and faint - the bands repeat every block, and strong ones striped a wall (v2 sheet)
    # r33 (critic, eyes 41029731853: "horizontal banding" on the side faces): the flow banding is no longer laid
    # along u - an isotropic slow cloud, warped, so no face carries a horizontal stripe through every block
    flow = norm01(F.warp(F.noise(1, 3, 0.5), 0.04 * F.noise(2, 6, 0.5), 0.04 * F.noise(2, 6, 0.5)))
    shade = tone[idx] * (0.965 + 0.07 * flow)
    alb = tint((0.075, 0.07, 0.095), shade)
    # r30b (eyes 41012344723 beside the Complementary benchmark 41012313108): the brown "smoky" aretes read as a web
    # of cracks in tar, and the block had no sparkle - the benchmark's obsidian owes its look to lavender glints
    # (hue ~265, luma ~0.32, about a quarter of the top face). Real obsidian has that too: SHEEN obsidian, violet
    # lenses of aligned micro-inclusions laid along the flow. So: the edges only a touch lighter and neutral, and
    # violet sheen lenses (3-12 texels, stretched along the flow, ~18% of the face) that are also glossier, so a
    # shader's sun makes them glint - an original pattern, not specks.
    a = (0.18 * arete * lit)[..., None]
    alb = alb * (1 - a) + np.array([0.20, 0.20, 0.22]) * a
    # r31 (art director, the r30b stills beside the benchmark: the block still read near-black, and the one broad
    # sheen patch per block was a sticker - the same heart shape on every block of a wall): the sheen is BANDS - in
    # real sheen obsidian the aligned micro-inclusions lie in thin layers along the flow, so a fracture face shows
    # several narrow lavender stripes (3-8 texels, two edges each), wavy, each one coming and going along its length
    # (a lens is finite) and catching more on some faces than others. Five bands at their own spacing and strength
    # make a stripe set, not a blob; the lift to the benchmark's values (base ~0.11, glints ~0.32 over a quarter of
    # the face) goes through TARGETS (luma / std).
    # r31c (eyes 41023221433: the bands "line up as parallel diagonal bands on the top and horizontal bands on the
    # sides; clusters repeat per tile"): each fracture FACE cuts the layering at its own angle, so the band set is
    # laid per face - its own direction, phase and spacing - and no band runs as one line across the tile
    wob = 0.015 * F.noise(1, 3, 0.5) + 0.006 * F.noise(3, 9, 0.5)
    th = rng.uniform(0, math.pi, m)
    # r33: no band set within 20 degrees of horizontal (th ~ pi/2 lays the stripes along u - the sides banded)
    th = np.where(np.abs(th - math.pi / 2) < 0.35, math.pi / 2 + np.sign(th - math.pi / 2 + 1e-9) * 0.35, th)
    ph = rng.uniform(0, 1, m)
    sp = rng.uniform(3.5, 6.0, m)                                            # band sets per block on that face
    flowc = (np.cos(th[idx]) * (U - 0.5) + np.sin(th[idx]) * (V - 0.5)) * sp[idx] + ph[idx] + wob * sp[idx]
    bands = np.zeros((n, n))
    nb = 2
    centres = (np.arange(nb) + 0.5 + 0.5 * (rng.random(nb) - 0.5)) / nb     # the band set, repeating every 1.0
    widths = rng.uniform(0.05, 0.16, nb)                                     # in band-spacing units: 1-4 texels
    strength = rng.uniform(0.65, 1.0, nb)
    for c, w, s in zip(centres, widths, strength):
        d = (flowc - c + 0.5) % 1.0 - 0.5
        bands += s * np.exp(-(d / w) ** 2)
    along = 0.15 + 0.85 * smoothstep(0.25, 0.7, norm01(F.noise(2, 6, 0.6)))   # the bands break into lenses
    streak = 0.8 + 0.2 * norm01(F.noise(12, 32, 0.3, sx=4.0, sy=1.0))           # fibrous inside the band
    # the sheen is seen only where the fracture face meets the layers at the right angle: some faces show the whole
    # band set, others almost none - so a wall is not zebra-striped, the stripes come in patches
    dim = np.zeros(m, bool)
    dim[rng.permutation(m)[:max(1, m // 3)]] = True                           # a third of the faces (one of four)
    face_catch = np.where(dim, rng.uniform(0.08, 0.25, m), rng.uniform(0.45, 1.0, m))[idx]
    sheen = np.clip(bands * along * streak * face_catch * (1 - arete), 0, 0.85)
    # r31b (eyes 41018675091, r30b beside the benchmark: our sheen "dimmer and duller - mauve, not lavender", the
    # benchmark's glints "brighter and more numerous, its top face glitters"): sharper bright CORES inside the soft
    # bands - where a mid-scale fibre noise peaks along the band the inclusions lie densest, 2-6 texels, of varied
    # size, lavender (hue ~265) - the glitter; they sit in the bands, so no polka dots on the dark glass
    fibre = norm01(F.warp(F.noise(6, 18, 0.4), 0.01 * F.noise(3, 8, 0.5), 0.01 * F.noise(3, 8, 0.5)))
    core = smoothstep(0.55, 0.85, fibre) * smoothstep(0.15, 0.5, sheen) * (1 - arete)
    # r31c: "pink too bright / saturated for black glass" - chroma halved, the lavender cooler and greyer
    alb = alb * (1 - sheen[..., None]) + sheen[..., None] * np.array([0.37, 0.33, 0.50])   # grey-lavender
    alb = alb * (1 - core[..., None]) + core[..., None] * np.array([0.60, 0.55, 0.76])     # the glints, cool
    smooth = 228 - 95 * arete - 4 * np.abs(ripple) + 22 * sheen + 5 * core    # the bands and cores glint
    return dict(H=H, alb=alb, smooth=smooth)


def m_lapis(F, n):
    """Round 22b: lapis lazuli as a dressed stone - mottled deep ultramarine with paler calcite wisps and sparse gold
    pyrite flecks, a bevelled block rim; matte (smoothness from TARGETS)."""
    mott = F.fbm(3, 4, 0.55)
    wisp = smoothstep(0.75, 0.95, norm01(F.warp(F.noise(2, 6, 0.5), 0.03 * F.noise(3, 9), 0.03 * F.noise(3, 9))))
    shade = 0.85 + 0.25 * norm01(mott)
    alb = tint((0.10, 0.20, 0.62), shade)                    # r33: a cleaner ultramarine (less green in the blue)
    alb = alb * (1 - 0.5 * wisp[..., None]) + 0.5 * wisp[..., None] * np.array([0.55, 0.58, 0.62])   # calcite veins
    fleck = smoothstep(2.0, 2.6, F.noise(30, 70, 0.0))      # r36: more, larger pyrite flecks (2-3 texels)
    alb = alb * (1 - fleck[..., None]) + fleck[..., None] * np.array([0.78, 0.60, 0.20])
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


def oak_grain(F, along_v=True):
    """Sawn oak: fine straight latewood lines and broad streaks, long along v (along_v) or u. Two arrays (lines,
    streak), each ~N(0, 1)."""
    sx, sy = (1.0, 30.0) if along_v else (30.0, 1.0)
    lines = F.warp(F.noise(16, 64, 0.5, sx=sx, sy=sy), 0.004 * F.noise(2, 6, 0.5) if along_v else 0.0,
                   0.0 if along_v else 0.004 * F.noise(2, 6, 0.5))
    sx, sy = (1.0, 12.0) if along_v else (12.0, 1.0)
    streak = F.noise(4, 20, 0.6, sx=sx, sy=sy)
    return lines, streak


def iron_hoop(F, band, along_u=True, tone=1.0):
    """A forged iron hoop over mask `band` (0-1, two soft edges): a dull dark iron with a brushed grain along the
    hoop, a little rust at its edges, a proud bevel. Returns (albedo, height add, smoothness). tone lifts the iron
    (a weathered grey hoop, r33)."""
    sx, sy = (1.0, 10.0) if not along_u else (10.0, 1.0)
    br = 0.5 + 0.5 * np.tanh(0.6 * F.noise(30, 60, 0.0, sx=sx, sy=sy))
    dent = F.noise(3, 9, 0.5)
    rust = smoothstep(0.6, 0.9, norm01(F.noise(4, 12, 0.5))) * (1 - smoothstep(0.3, 0.9, band))   # at the edges
    iron = tint((0.21 * tone, 0.21 * tone, 0.22 * tone), 0.9 + 0.08 * dent + 0.06 * (br - 0.5))   # darker than the oak
    rusty = tint((0.42, 0.26, 0.16), 0.9 + 0.1 * dent)
    alb = iron * (1 - rust[..., None]) + rusty * rust[..., None]
    H = 0.16 * smoothstep(0.0, 1.0, band) + 0.01 * dent
    smooth = 120 + 50 * (br - 0.5) - 70 * rust
    return alb, H, smooth


def m_barrel_side(F, n, count=6):
    """Round 31 (art director: "pale sandy planks with black holes"): an oak cask - six darker oak staves along v,
    each slightly bowed with a straight sawn grain and its own tone, thin dark seams; two forged iron hoops with
    two edges each (a proud bevel), brushed round the cask, a touch of rust at their edges, a rivet on each."""
    col = np.floor(F.u * count).astype(int)
    inside = (F.u * count) % 1.0
    seam = smoothstep(0.0, 0.03, inside) * smoothstep(0.0, 0.03, 1 - inside)
    bow = 1 - (2 * inside - 1) ** 2
    lines, streak = oak_grain(F, along_v=True)
    rng = F.rng
    tone = (rng.random(count) - 0.5)[col % count]
    hue = (rng.random(count) - 0.5)[col % count]
    H = 0.40 + 0.10 * seam + 0.06 * bow + 0.012 * lines + 0.02 * tone
    shade = (0.85 + 0.14 * tone + 0.07 * lines + 0.06 * streak) * (0.55 + 0.45 * seam)
    jit = np.stack([0.05 * hue + 0.02 * streak, 0.0 * hue, -0.05 * hue - 0.02 * streak], -1)
    alb = tint((0.42, 0.28, 0.15), shade, jit)
    smooth = 55 + 10 * streak
    d = np.minimum(np.abs(F.v - 0.20), np.abs(F.v - 0.80))
    band = 1 - smoothstep(0.045, 0.058, d)                   # two hoops, ~14 texels, two edges each
    ialb, iH, ismooth = iron_hoop(F, band, along_u=True)
    rivet = np.clip(1 - ((F.u - 0.5) ** 2 + np.minimum((F.v - 0.20) ** 2, (F.v - 0.80) ** 2)) / 0.018 ** 2, 0, 1)
    iH = iH + 0.06 * np.sqrt(rivet)
    ialb = ialb * (0.95 + 0.15 * np.sqrt(rivet))[..., None]
    b = band[..., None]
    return dict(H=H * (1 - band) + (0.46 + iH) * band, alb=alb * (1 - b) + ialb * b,
                f0=np.where(band > 0.5, IRON, 10), smooth=smooth * (1 - band) + ismooth * band)


def m_barrel_end(F, n, hole, rows=4):
    """Round 31: the cask's head - oak boards across it inside a forged iron chime hoop; the top has the bung, a round
    wooden plug sitting a hair proud in a dark groove (not a black hole)."""
    row = np.floor(F.v * rows).astype(int)
    inr = (F.v * rows) % 1.0
    gap = smoothstep(0.0, 0.03, inr) * smoothstep(0.0, 0.03, 1 - inr)
    lines, streak = oak_grain(F, along_v=False)
    rng = F.rng
    tone = (rng.random(rows) - 0.5)[row]
    hue = (rng.random(rows) - 0.5)[row]
    H = 0.40 + 0.10 * gap + 0.012 * lines + 0.02 * tone
    shade = (0.85 + 0.14 * tone + 0.07 * lines + 0.06 * streak) * (0.55 + 0.45 * gap)
    jit = np.stack([0.05 * hue + 0.02 * streak, 0.0 * hue, -0.05 * hue - 0.02 * streak], -1)
    alb = tint((0.56, 0.38, 0.20), shade, jit)      # r32: the head a paler oak than the staves (it is sawn, not split)
    smooth = 55 + 10 * streak
    if hole:
        r = np.sqrt((F.u - 0.5) ** 2 + (F.v - 0.5) ** 2)
        plug = 1 - smoothstep(0.070, 0.080, r)
        groove = smoothstep(0.070, 0.080, r) * (1 - smoothstep(0.085, 0.100, r))
        pg = tint((0.44, 0.31, 0.17), 0.9 + 0.08 * F.noise(10, 30, 0.3) + 0.12 * np.clip(1 - (r / 0.075) ** 2, 0, 1))
        alb = alb * (1 - plug[..., None]) + pg * plug[..., None]
        alb = alb * (1 - 0.5 * groove[..., None])
        H = H + 0.05 * plug * (1 - (r / 0.075) ** 2).clip(0, 1) - 0.10 * groove
    d = np.minimum(np.minimum(F.u, 1 - F.u), np.minimum(F.v, 1 - F.v))
    # r33 (critic, eyes 41029731853: "black X too heavy" - the r32 15-texel near-black chime hoop drew a dark cross
    # through every 2x2 of barrels): a narrower hoop (~9 texels) of weathered grey iron, still a hoop, not a bar
    band = 1 - smoothstep(0.062, 0.075, d)
    ialb, iH, ismooth = iron_hoop(F, band, along_u=True, tone=1.35)
    b = band[..., None]
    return dict(H=H * (1 - band) + (0.46 + iH) * band, alb=alb * (1 - b) + ialb * b,
                f0=np.where(band > 0.5, IRON, 10), smooth=smooth * (1 - band) + ismooth * band)


def hay_twine(F, centres=(0.25, 0.75), half=0.036):
    """Two baler-twine bands round the bale at v = centres: a twisted cord (a diagonal twist along u), sunk a little
    into the straw, with two edges. Returns (band mask 0-1, twist tone 0-1)."""
    d = np.min(np.abs((F.v[..., None] - np.array(centres)[None, None, :] + 0.5) % 1.0 - 0.5), -1)
    band = 1 - smoothstep(half - 0.006, half + 0.004, d)
    twist = 0.5 + 0.5 * np.sin(2 * math.pi * (F.u * 24 + F.v * 8))        # the lay of the cord
    return band, twist


def m_hay_side(F, n):
    """Round 31 (art director: "reads as packed dirt"): a bale side is a wall of straw stalks - long, straight, each
    2-5 texels wide and running the length of the face, each its own value (sun-bleached gold, dull straw, a shadowed
    gap between two), a few lying at a slight angle across the others, with two dark twine bands round the bale.
    The colour contrast is stalk-to-stalk (what the RTS camera keeps); the relief is each stalk's round profile."""
    # stalks: a band of 16-40 cycles across u (3-8 texels per stalk), very long along v, with a slow wobble so no
    # stalk is a ruler line; plus a second thinner layer of stalks that cross at a slight angle
    wob = 0.005 * F.noise(1, 3, 0.5, sx=1.0, sy=3.0)
    stalk = F.warp(F.noise(16, 40, 0.3, sx=1.0, sy=40.0), wob, 0.0)
    fine = F.warp(F.noise(40, 64, 0.0, sx=1.0, sy=40.0), wob, 0.0)
    # r31b (eyes 41023221433: the sides "read woven wicker"): no crossing stalks - straight, with a slow drift
    stalk = stalk + 0.25 * F.noise(4, 12, 0.5, sx=1.0, sy=30.0)
    clump = F.noise(2, 6, 0.6, sx=1.0, sy=3.0)                                 # the bale's loose and packed patches
    prof = norm01(stalk)                                                       # 0 in the gap, 1 on a stalk's crest
    band, twist = hay_twine(F)
    H = 0.45 + 0.28 * prof + 0.04 * fine + 0.08 * clump
    H = H - 0.12 * band + 0.03 * band * twist
    # value: stalk-to-stalk - each stalk's crest gold, the gaps between stalks a deep ochre shadow
    tone = 0.62 + 0.55 * prof + 0.06 * fine + 0.10 * clump
    # some stalks bleached pale, some still green-grey: a slow hue drift along the stalks
    bleach = norm01(F.noise(6, 20, 0.4, sx=1.0, sy=30.0))
    base = np.array([0.92, 0.80, 0.20])[None, None, :] * (1 + 0.10 * (bleach - 0.5))[..., None] \
        * np.array([1.0, 1.06, 1.3])[None, None, :] ** (bleach - 0.5)[..., None]     # bleached: paler, less orange
    alb = np.clip(base * tone[..., None], 0, 1)
    cord = tint((0.22, 0.15, 0.09), 0.70 + 0.40 * twist)             # dark sisal twine (r32: darker, a dull brown)
    a = band[..., None]
    return H, alb * (1 - a) + cord * a


def m_hay_top(F, n):
    """Round 31b (eyes 41023221433: the r31 tube ends "read oatmeal / granola noise"; the benchmark's top reads as
    directional streaks): the top of the bale as stalks too - lying along u, pressed flat, in a few bundles that
    cross at slight angles, with a scatter of cut ends (small pale rings) where stalks point up out of the bale."""
    wob = 0.004 * F.noise(1, 3, 0.5, sx=3.0, sy=1.0)
    stalk = F.warp(F.noise(16, 40, 0.3, sx=40.0, sy=1.0), 0.0, wob)
    stalk = stalk + 0.25 * F.noise(4, 12, 0.5, sx=30.0, sy=1.0)
    fine = F.warp(F.noise(40, 64, 0.0, sx=40.0, sy=1.0), 0.0, wob)
    # bundles lying at a slight angle across the rest
    cross = F.warp(F.noise(14, 36, 0.3, sx=20.0, sy=1.0), 0.0, 0.14 * F.u)
    where_cross = smoothstep(0.55, 0.9, norm01(F.noise(1, 4, 0.5, sx=2.0, sy=1.0)))
    stalk = stalk * (1 - 0.6 * where_cross) + cross * 0.6 * where_cross
    clump = F.noise(2, 6, 0.6)
    prof = norm01(stalk)
    # cut ends poking up: sparse small rings
    f1, f2, idx, k = F.voronoi(120, 1.0)
    cell = 1.0 / math.sqrt(120)
    on = (F.rng.random(k) < 0.18)[idx]
    r = f1 / cell
    ring = np.exp(-((r - 0.32) / 0.09) ** 2) * on
    hole = (1 - smoothstep(0.12, 0.24, r)) * on
    H = 0.45 + 0.26 * prof + 0.04 * fine + 0.08 * clump + 0.12 * ring - 0.10 * hole
    tone = 0.62 + 0.55 * prof + 0.06 * fine + 0.10 * clump + 0.25 * ring - 0.35 * hole
    bleach = norm01(F.noise(6, 20, 0.4, sx=30.0, sy=1.0))
    base = np.array([0.92, 0.80, 0.20])[None, None, :] * (1 + 0.10 * (bleach - 0.5))[..., None] \
        * np.array([1.0, 1.06, 1.3])[None, None, :] ** (bleach - 0.5)[..., None]
    return H, np.clip(base * tone[..., None], 0, 1)


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
        # r32 (critic: cracked polished blackstone bricks identical to the plain ones at RTS scale): the fissures
        # wider (2-3 texels at 32 per block - they survive the mipmaps) and through more of the bricks, and each
        # crack has a pale SPALL beside it - the fresh stone where a flake broke away - which is what reads on a
        # dark block (a darker line on near-black is nothing)
        # (the thresholds are in units of a unit-std noise whose gradient is ~2 pi f per tile: on noise(2, 7) a
        # threshold of 0.02 was a fifth of a texel - the old cracks were never visible; noise(1, 4) at 0.18 is ~2
        # texels each side)
        line = np.abs(F.warp(F.noise(1, 4, 1.0), 0.02 * F.noise(4, 12), 0.02 * F.noise(4, 12)))
        where = smoothstep(0.0, 0.45, F.noise(1, 3, 0.5))
        crack = (1 - smoothstep(0.0, 0.14, line)) * where * cracks * face
        spall = smoothstep(0.14, 0.3, line) * (1 - smoothstep(0.4, 0.7, line)) * where * cracks * face
        spall = spall * smoothstep(0.2, 0.6, norm01(F.noise(3, 9, 0.5)))        # only along parts of the crack
        H = H - 0.3 * crack - 0.10 * spall
        alb = alb * (1 - 0.7 * crack + 0.6 * spall)[..., None]
    return H, alb


def m_polished(F, n, base, mottle=0.10, crystals=0.10, fleck=(20, 56), seam=0.0, rim=(0.06, 0.012), calm=False):
    """A polished stone face (polished blackstone / deepslate): soft mottling, few crystals, a bevelled border.
    Round 32 (critic: "polished vs rough twins read identical at RTS scale"): seam > 0 draws a faint 2x2 seam - a
    1-texel line through the middle each way, `seam` darker - the mark of a cut and set stone; calm swaps the
    mottle for a tighter, quieter grain (a polished face is one calm tone, the rough twin keeps its mottle)."""
    mot = F.fbm(4, 3, 0.5) if calm else F.fbm(3, 4, 0.55)
    speck = F.noise(16, 48, 0.0)
    flecks = smoothstep(1.3, 2.2, F.noise(fleck[0], fleck[1], 0.0))
    bev = frame(F, rim[0], rim[1]) if rim else np.ones((n, n))     # rim=None (r33): one continuous slab, no border
    H = 0.35 + 0.5 * bev + (0.015 if calm else 0.03) * mot + (0.004 if calm else 0.01) * speck
    shade = (0.88 + mottle * mot + (0.008 if calm else 0.02) * speck - crystals * flecks) * (0.75 + 0.25 * bev)
    if seam > 0:
        d = np.minimum(np.abs(F.u - 0.5), np.abs(F.v - 0.5))
        line = 1 - smoothstep(0.0, 1.2 / n, d)                      # ~1 texel each side of the middle
        H = H - 0.08 * line
        shade = shade * (1 - seam * line)
    return H, tint(base, shade)


def m_tiles(F, n, grid, base, joint=(0.008, 0.022), grout=0.64, bond=0.0):
    """Square tiles, grid x grid per block, each a little uneven in tone and set, thin dark joints. bond > 0 shifts
    each row along u by that much more of the block than the row above (a running bond; grid * grid * bond must be
    whole for the tile to wrap - 1/9 on a 3 x 3: a third of a tile per row)."""
    cv = np.floor(F.v * grid).astype(int)
    us = (F.u + bond * cv) % 1.0
    cu = np.floor(us * grid).astype(int)
    iu, iv = (us * grid) % 1.0, (F.v * grid) % 1.0
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


def m_copper(F, n, stage, plate=False):
    """Round 23 cut copper, four plates per block with seams, in its four weathering stages (0 bare .. 3 oxidized):
    bare: salmon-pink metal, brushed, F0 234. exposed: the metal dulled brown-tarnished with the first green
    speckle in the seams. weathered: tarnish everywhere, verdigris spread in blotches from the seams and the low
    spots over most of the face, bare metal left on the crests. oxidized: solid verdigris, matte, dielectric.
    Real patina starts where water sits (seams, dents, under the edge) and grows outwards - not a uniform teal."""
    grid = 2
    iu, iv = (F.u * grid) % 1.0, (F.v * grid) % 1.0
    edge = np.minimum(np.minimum(iu, 1 - iu), np.minimum(iv, 1 - iv)) / grid
    if plate:
        # round 38: the FULL copper blocks - one rolled sheet per block (no cut seams): the sheet's own edge is the
        # block's, with soft hammer marks across it; the patina creeps in from the edges and the hammered low spots
        edge = np.minimum(np.minimum(F.u, 1 - F.u), np.minimum(F.v, 1 - F.v))
    # r31 (art director, sampler 3: "keep the cut seams on all four, patina creeping from the seams, rough patina
    # over glossier metal, stage colours distinct"): seams wider (survive the mips under the patina), a crisper step,
    # the bare metal glossier (170) with the brushing in the relief
    face = smoothstep(0.005, 0.026, edge)
    ham = F.noise(4, 14, 0.6)
    br = brushed(F, 0.8)
    if plate:
        f1, f2, idx, k = F.voronoi(40, 1.0)
        cell = 1.0 / math.sqrt(40)
        dents = np.clip(1 - (f1 / (0.7 * cell)) ** 2, 0, 1) * (0.5 + 0.5 * F.rng.random(k))[idx]   # hammer marks
        ham = 0.6 * ham - 0.9 * dents + 0.45
    # r36 (lovish: "copper rims brighter - the cut seams catch the light"): a chamfered arris round each plate, a
    # step lighter in the colour and raised in the height, so the seams draw a bright line under the sun
    arris = smoothstep(0.016, 0.026, edge) * (1 - smoothstep(0.026, 0.050, edge))
    H = 0.35 + 0.5 * smoothstep(0.005, 0.016, edge) + 0.06 * arris + 0.02 * ham + 0.010 * (br - 0.5)
    metal = tint((0.82, 0.46, 0.30), (0.95 + 0.06 * ham + 0.03 * (br - 0.5) + 0.22 * arris) * (0.60 + 0.40 * face))
    smooth_metal = np.clip(170 + 40 * (br - 0.5) - 20 * (1 - face), 40, 235)
    if stage == 0:
        return H, metal, np.full(H.shape, 234), smooth_metal
    # tarnish: a brown-purple film that dulls the metal (copper's first oxide, Cu2O)
    tarn_n = norm01(F.fbm(2, 4, 0.55))
    tarn = smoothstep(0.1, 0.7, tarn_n + (0.5 if stage >= 2 else 0.0))
    tarnished = metal * (1 - tarn[..., None]) + tarn[..., None] * tint((0.50, 0.30, 0.22), 0.9 + 0.2 * ham)
    # verdigris: grows from the seams and the hollows outward
    seed_n = norm01(F.warp(F.fbm(2, 3, 0.55), 0.015 * F.noise(2, 6), 0.015 * F.noise(2, 6)))
    low = 1 - face + 0.25 * (1 - norm01(F.blur(ham, F.n / 24)))
    spread = {1: 0.22, 2: 0.82, 3: 1.05}[stage]
    verd = smoothstep(0.0, 0.45, seed_n * 0.6 + 0.5 * low + spread - 0.85)
    spots = smoothstep(0.88, 0.97, norm01(F.noise(6, 16, 0.4))) * (0.5 if stage == 1 else 0.0)
    verd = np.clip(verd + spots, 0, 1)
    vtone = 0.85 + 0.2 * norm01(F.fbm(3, 3, 0.5)) + 0.05 * ham
    # the stages' greens differ: weathered a deeper blue-green, oxidized the lighter chalky teal of a thick patina
    green = tint((0.28, 0.56, 0.46) if stage == 2 else (0.36, 0.66, 0.52), vtone)
    pale = tint((0.56, 0.78, 0.64), vtone)                 # the chalky light bloom on thick patina
    bloom = smoothstep(0.6, 0.95, norm01(F.fbm(4, 3, 0.5))) * (stage >= 2) * (0.6 if stage == 2 else 1.0)
    green = green * (1 - 0.6 * bloom[..., None]) + pale * 0.6 * bloom[..., None]
    alb = tarnished * (1 - verd[..., None]) + green * verd[..., None]
    alb = alb * (0.72 + 0.28 * face)[..., None]            # the seams stay under the patina (every stage)
    H = H + 0.03 * verd * norm01(F.noise(12, 40, 0.3))    # the crust is rough
    f0 = np.where(verd > 0.5, 10, 234)
    smooth = smooth_metal * (1 - tarn * 0.45) * (1 - verd) + verd * (18 + 10 * (1 - vtone))
    return H, alb, f0, np.clip(smooth, 8, 230)


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
    return H, tint(MOSS_RGB, shade)


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


def m_terracotta(F, n, base, flash=(0.92, 0.90, 0.96)):
    """Round 31 (art director: the terracottas "muddy, half a stop dark", blue "grey-purple like blackstone"): fired
    clay is a CLEAN hue, mostly flat - a slow soft mottle (the clay body's mix), a few broad kiln-flashing clouds a
    touch darker and cooler, a fine grain in the relief only and a handful of shallow pits. No peppery pits in the
    colour (m_clayish's 16-48 cycle pits read as grit). The lift to value goes through TARGETS."""
    mot = F.fbm(2, 3, 0.5)
    cloud = smoothstep(0.55, 0.95, norm01(F.fbm(1, 3, 0.6)))          # kiln flashing, broad
    fine = F.noise(20, 60, 0.3)
    pit = smoothstep(0.85, 0.97, norm01(F.noise(10, 24, 0.3))) * smoothstep(0.5, 0.8, norm01(F.noise(1, 3, 0.5)))
    H = 0.6 + 0.04 * mot + 0.012 * fine - 0.05 * pit
    shade = 0.92 + 0.07 * mot + 0.004 * fine - 0.06 * pit                # the grain is for the light, not the colour
    alb = tint(base, shade)
    alb = alb * (1 - cloud[..., None]) + cloud[..., None] * alb * np.array(flash)[None, None, :]
    return H, alb


COLUMN_EDGES = (0.0, 0.21, 0.40, 0.62, 0.81)   # the five columns' left edges along u - FIXED, so stacked blocks
#                                                (any variant) keep each column running through


def hex_ends(F, n, jitter=0.35):
    """The cut ends of basalt columns: a near-hexagonal Voronoi (low jitter, 3x3 per block), each column its own
    level. Returns (joint 0-1, per-cell level, f2 - f1)."""
    f1, f2, idx, k = F.voronoi(9, jitter)
    level = F.rng.uniform(-1, 1, k)[idx]
    return 1 - smoothstep(0.0, 0.022, f2 - f1), level, f2 - f1


def m_columnar(F, n, top, polished):
    """Round 31 (art director: basalt's "columnar structure lost: hex column ends on top, vertical ribs with relief
    on the sides"): basalt as it forms - a bundle of columns. Side: five upright columns per block, each a rounded
    rib (a polished one a flat facet) with a dark crevice between, cross-joints at different heights on each column
    (the columns are jointed), fine-grained dark grey rock with a faint grit. Top: the cut ends, near-hexagonal,
    each at its own level, joints as dark grooves with chamfered edges. The polished block keeps the same structure
    cut flat: crisp grooves, flat faces, a satin finish."""
    rng = F.rng
    grit = F.noise(16, 48, 0.3)
    mottle = F.fbm(2, 3, 0.5)
    if top:
        joint, level, gap = hex_ends(F, n, 0.30 if polished else 0.4)
        face = smoothstep(0.0, 0.05 if polished else 0.07, gap)        # chamfer into the joint
        H = 0.55 + 0.35 * face + (0.02 if polished else 0.05) * level + (0.004 if polished else 0.02) * grit
        shade = (0.90 + (0.03 if polished else 0.06) * level + 0.05 * mottle + 0.02 * grit) * (0.60 + 0.40 * face)
        if not polished:                                                # a rough end: pits and a few chips
            pit = smoothstep(0.8, 0.96, norm01(F.noise(8, 24, 0.3)))
            H = H - 0.08 * pit * face
            shade = shade - 0.10 * pit * face
    else:
        edges = np.array(COLUMN_EDGES)
        wob = 0.006 * F.noise(1, 3, 0.5, sx=1.0, sy=4.0)
        u = (F.u + wob) % 1.0
        d = (u[..., None] - edges[None, None, :]) % 1.0                 # distance past each edge
        col = np.argmin(d, -1)
        start = edges[col]
        width = (np.roll(edges, -1) - edges) % 1.0
        w = width[col]
        x = ((u - start) % 1.0) / w                                     # 0..1 across the column
        if polished:
            rib = smoothstep(0.0, 0.08, x) * smoothstep(0.0, 0.08, 1 - x)   # flat with a crisp drop
        else:                                                           # an angular prism: two facets, a crease
            ridge_at = (0.42 + 0.16 * rng.random(len(edges)))[col]
            rib = np.where(x < ridge_at, x / ridge_at, (1 - x) / (1 - ridge_at)) ** 0.7
            rib = rib * (0.9 + 0.1 * norm01(F.noise(3, 10, 0.5, sx=1.0, sy=5.0)))   # weathered, uneven
        crev = 1 - smoothstep(0.0, 0.035 if polished else 0.06, np.minimum(x, 1 - x) * w)
        # cross-joints: each column broken at its own heights (1-2 per block), a thin dark line, slightly open
        seg = np.zeros((n, n))
        for c in range(len(edges)):
            for _ in range(2 if rng.random() < 0.6 else 1):
                y0 = rng.random()
                dy = np.abs((F.v - y0 + 0.5) % 1.0 - 0.5)
                seg = np.where(col == c, np.maximum(seg, 1 - smoothstep(0.004, 0.012, dy)), seg)
        seg = seg * smoothstep(0.1, 0.9, 1 - crev)
        H = 0.35 + 0.45 * rib - 0.06 * seg + (0.005 if polished else 0.03) * grit \
            + (0.0 if polished else 0.05) * F.noise(3, 12, 0.5, sx=1.0, sy=6.0)
        tone = rng.uniform(0.9, 1.1, len(edges))[col]
        shade = tone * (0.72 + 0.28 * rib) * (1 - 0.35 * crev - 0.25 * seg) * (1 + 0.04 * mottle + 0.03 * grit)
    base = (0.33, 0.33, 0.35) if polished else (0.33, 0.31, 0.29)     # r39: basalt a WARM grey (beside blue deepslate)
    hue = F.noise(2, 5, 0.5)
    jit = np.stack([0.01 * hue, 0.0 * hue, -0.01 * hue], -1)
    return H, tint(base, shade, jit)


def m_basalt(F, n, top):
    return m_columnar(F, n, top, polished=True)


def m_polished_basalt_top(F, n, rings=16, lobe=4.0):
    """Round 32 (critic, eyes 41025894452: polished basalt "got WORSE: its top is a tight cobble / hex grid,
    indistinguishable from basalt"): the polished block's top is a cut and ground face, not column ends - the
    grinder leaves concentric rings that follow the block's square outline with rounded corners (a p-norm radius,
    lobe = 4), one ring per 4 texels at 128 (`rings` from the centre to the edge), every fourth ring cut a touch
    deeper so the pattern survives the mipmaps as a few soft bands; a faint grit and mottle, a bevelled rim. The
    column cells stay on the sides only (m_columnar)."""
    x, y = F.u - 0.5, F.v - 0.5
    r = (np.abs(x) ** lobe + np.abs(y) ** lobe) ** (1.0 / lobe)          # a rounded square's radius
    r = r + 0.002 * F.noise(2, 6, 0.5)                                   # the rings wander a hair
    fine = np.cos(2 * math.pi * r * 2 * rings)                           # 1 ring per 4 texels
    # r33 (critic, eyes 41029731853: "4 bullseyes" on the 2x2 - the every-fourth deeper ring survived the mipmaps as
    # a target per block): no broad rings. The fine rings stay in the relief (the light finds them up close, the
    # mipmaps average them to a calm sheen), the colour a calm polished face: a slow mottle, a faint grit, the rim.
    grit = F.noise(16, 48, 0.3)
    mottle = F.fbm(2, 3, 0.5)
    bev = frame(F, 0.05, 0.012)
    H = 0.35 + 0.5 * bev + 0.02 * fine + 0.004 * grit + 0.015 * mottle
    shade = (0.92 + 0.02 * fine + 0.05 * mottle + 0.008 * grit) * (0.75 + 0.25 * bev)
    hue = F.noise(2, 5, 0.5)
    jit = np.stack([0.01 * hue, 0.0 * hue, -0.01 * hue], -1)
    return H, tint((0.35, 0.35, 0.37), shade, jit)


def m_mud(F, n):
    """Round 31 (art director: mud "a flat dark slab - wet sheen via smoothness, drying cracks"): a drying mud flat -
    a network of shrinkage cracks (Voronoi plates ~1/4 block, the crack a groove whose plate edges curl up a little),
    the plates' tops drying pale and matte, the cracks and the low puddled hollows still wet, dark and glossy (the
    smoothness map carries the wet / dry split; the F0 stays dielectric)."""
    f1, f2, idx, k = F.voronoi(16, 0.9)
    wu, wv = 0.015 * F.noise(3, 9, 0.5), 0.015 * F.noise(3, 9, 0.5)
    f1, f2 = F.warp(f1, wu, wv), F.warp(f2, wu, wv)
    gap = f2 - f1
    crack = 1 - smoothstep(0.0, 0.018, gap)              # the open crack
    curl = smoothstep(0.018, 0.06, gap)                  # the plate: 0 at the curled-up lip, 1 inside
    lip = (1 - curl) * (1 - crack)
    wet = smoothstep(0.55, 0.9, norm01(F.fbm(2, 3, 0.55)))   # low hollows still wet
    grain = F.noise(14, 40, 0.3)
    H = 0.55 - 0.25 * crack + 0.06 * lip + 0.01 * grain - 0.04 * wet
    dry = (1 - wet) * (1 - crack)
    shade = 0.80 + 0.25 * dry * (0.6 + 0.4 * curl) + 0.02 * grain - 0.35 * crack
    alb = tint((0.30, 0.25, 0.21), shade)
    alb = alb * (1 - 0.15 * dry[..., None] * np.array([0.0, 0.2, 0.5])[None, None, :])   # dry mud: paler, greyer
    smooth = 38 + 100 * np.clip(wet + 0.8 * crack, 0, 1) - 10 * lip
    return dict(H=H, alb=alb, smooth=np.clip(smooth, 20, 170))


def m_bone(F, n, top):
    """Bone block: long bone grain along the side; the cut end shows the dense rim and the spongy marrow.
    Round 31 (art director: the sides "plain"): three bone shafts per side, each a rounded cylinder with fine
    longitudinal striations, a slight bulge toward its ends, a few pits and a hairline crack, each its own tone.
    Round 32 (critic, eyes 41025894452: "reads as tiled ceramic: real bone, a porous fibrous end grain on top and
    long fibres on the sides, ivory not cream"): the top is a sawn bone end - no square frame; a dense cortical
    rim (smooth, bright) round a cancellous core, a foam of pores 3-6 texels across with thin bright walls (a
    Voronoi's cell boundaries), the gap outside the rim in shadow; the sides' fibres are long, wavy and clearly
    drawn (the cylinder shading pulled back so the shafts are not three tiles)."""
    if top:
        # r33 (critic, eyes 41029731853: "4 fat rounded ends read as marshmallows / knuckles" - the r32 rounded-square
        # outline with its shadowed gap drew one domed lump per block): ONE sawn bone surface per face, no outline, no
        # gap, no dome - a flat cut through cancellous bone: a foam of fine pores 3-5 texels across (shallow, with
        # thin pale trabecular walls), denser and more open in slow patches (the marrow's cavities), a few solid
        # cortical patches where the saw cut through the compact bone, a faint sawn grain. Ivory.
        f1, f2, idx, k = F.voronoi(900, 1.0)                               # pores ~4.3 texels
        wu, wv = 0.004 * F.noise(4, 12, 0.5), 0.004 * F.noise(4, 12, 0.5)
        f1, f2 = F.warp(f1, wu, wv), F.warp(f2, wu, wv)
        wall = 1 - smoothstep(0.0, 0.009, f2 - f1)                         # the thin trabecular walls
        cell = 1.0 / math.sqrt(900)
        pore = smoothstep(0.2, 0.65, f1 / cell) * (1 - wall)              # deep in the middle of a cell
        density = norm01(F.fbm(2, 3, 0.55))                                # slow: open marrow vs compact bone
        compact = smoothstep(0.72, 0.9, density)                           # solid cortical patches (no pores)
        open_ = (F.rng.random(k) < 0.85)[idx] * (1 - compact)
        pore = pore * open_ * (0.55 + 0.45 * (1 - density))
        grain = F.noise(10, 30, 0.3, sx=4.0, sy=1.0)                        # the saw's marks, faint, along u
        H = 0.70 - 0.10 * pore + 0.015 * wall * (1 - compact) + 0.008 * grain + 0.015 * compact + 0.02 * density
        shade = (0.94 + 0.02 * grain + 0.03 * compact + 0.05 * density) * (1 - 0.18 * pore) \
            * (1 + 0.03 * wall * (1 - compact))
        return H, tint((0.92, 0.89, 0.81), shade)
    else:
        x = (F.u * 3) % 1.0                              # across one shaft
        col = np.floor(F.u * 3).astype(int)
        bulge = 1 + 0.08 * np.cos(2 * math.pi * F.v) ** 2   # a hair wider toward the block's ends
        cyl = np.sqrt(np.clip(1 - ((2 * x - 1) / bulge) ** 2, 0, 1))   # a cylinder's profile
        groove = 1 - smoothstep(0.0, 0.05, np.minimum(x, 1 - x))
        wob = 0.004 * F.noise(1, 3, 0.5, sx=1.0, sy=3.0)
        stria = F.warp(F.noise(20, 56, 0.3, sx=1.0, sy=24.0), wob, 0.0)   # fine lines along the shaft, wavy
        fibre = F.warp(F.noise(6, 16, 0.5, sx=1.0, sy=12.0), wob, 0.0)    # the broader fibre bundles
        grain = F.noise(4, 14, 0.5, sx=1.0, sy=8.0)
        pit = smoothstep(0.86, 0.96, norm01(F.noise(8, 24, 0.3)))
        crack = (1 - smoothstep(0.0, 0.08, np.abs(F.noise(1, 3, 1.0, sx=1.0, sy=6.0)))) \
            * smoothstep(0.6, 0.9, norm01(F.noise(1, 3, 0.5)))
        tone = F.rng.uniform(0.96, 1.03, 3)[col]
        H = 0.3 + 0.4 * cyl + 0.03 * stria + 0.04 * fibre + 0.02 * grain - 0.05 * pit - 0.08 * crack
        shade = tone * (0.86 + 0.14 * cyl) * (1 + 0.05 * stria + 0.07 * fibre + 0.03 * grain - 0.12 * pit - 0.2 * crack) \
            * (1 - 0.25 * groove)
        hue = grain
        alb = tint((0.92, 0.89, 0.81), shade, np.stack([0.015 * hue, 0.0 * hue, -0.02 * hue], -1))
        return H, alb


def m_quartz(F, n, kind):
    """Quartz: a polished white stone with faint grey veins - pillar (vertical flutes / ringed top), bricks, the
    smooth block."""
    vein = 1 - smoothstep(0.0, 0.02, np.abs(F.warp(F.noise(2, 6, 0.5), 0.03 * F.noise(3, 9), 0.03 * F.noise(3, 9))))
    vein *= smoothstep(0.3, 0.8, F.noise(1, 3, 0.5))
    if kind == "bricks":
        H, alb = m_bricks(F, n, 2, 2, (0.92, 0.90, 0.86), pits=0.02, tone_amp=0.03)
        return H, alb * (1 - 0.08 * vein)[..., None]
    if kind == "pillar":
        # r33 (critic, eyes 41029731853: "corrugation" - 4 sine ridges per block at full contrast): a classical
        # fluted column - three broad shallow scoops per block with a flat fillet between, the scoop's shading soft
        # (its relief does the work under the light), the vein faint across them
        x = (F.u * 3) % 1.0
        fillet = 0.14                                                  # the flat land each side of a scoop
        sc = np.clip((x - fillet / 2) / (1 - fillet), 0, 1)
        flutes = np.sin(math.pi * sc) ** 2 * (x > fillet / 2) * (x < 1 - fillet / 2)   # 1 deep in the scoop
        H = 0.62 - 0.09 * flutes - 0.015 * vein
        shade = 0.95 - 0.025 * flutes - 0.06 * vein
    elif kind == "chiseled":
        # round 37: a carved panel - a raised border with a chamfer, a recessed field with a raised diamond boss
        bev = frame(F, 0.10, 0.025)                                    # 1 inside the border
        du, dv = np.abs(F.u - 0.5), np.abs(F.v - 0.5)
        diamond = 1 - smoothstep(0.17, 0.21, du + dv)                  # the boss
        dedge = smoothstep(0.13, 0.17, du + dv) * diamond
        H = 0.70 - 0.18 * bev + 0.16 * diamond - 0.015 * vein
        shade = (0.95 - 0.05 * vein) * (1 - 0.05 * bev + 0.04 * diamond + 0.03 * dedge)
    elif kind == "chiseled_top":
        bev = frame(F, 0.08, 0.02)
        ring = frame(F, 0.20, 0.16)
        H = 0.55 + 0.25 * bev - 0.12 * ring
        shade = (0.95 - 0.05 * vein) * (0.96 + 0.04 * bev - 0.03 * ring)
    elif kind == "pillar_top":
        bev = frame(F, 0.10, 0.02)
        inner = frame(F, 0.22, 0.17)
        H = 0.4 + 0.3 * bev + 0.2 * inner
        shade = (0.90 - 0.06 * vein) * (0.8 + 0.1 * bev + 0.1 * inner)
    elif kind in ("block_side", "block_top"):
        # r31 (art director: "remove the heavy dark seam through the 2x2 ... quartz_block a subtle tile, faint cool
        # marble vein, near-white"): a very narrow, shallow chamfer (a tile line, not a groove) and cool veins
        # r32 (critic: quartz_block vs smooth_quartz identical at RTS): the block keeps the chamfer and carries a
        # wider, clearer blue-grey vein (3-4 texels, a soft halo) - the smooth block is seamless and vein-free
        bev = frame(F, 0.025, 0.006)
        drift = F.noise(2, 6, 0.5, sx=1.0, sy=6.0) if kind == "block_side" else F.noise(1, 3, 0.5)
        # (the r31 vein's threshold 0.02 on a unit-std noise was a fifth of a texel wide - invisible, a few dots)
        line = np.abs(F.warp(F.noise(1, 4, 1.0), 0.03 * F.noise(3, 9), 0.03 * F.noise(3, 9)))
        vein = (1 - smoothstep(0.0, 0.22, line)) * smoothstep(0.1, 0.6, F.noise(1, 3, 0.5))
        vein = np.maximum(vein, 0.6 * F.blur(vein, 3.0))                 # a soft halo round the vein
        H = 0.5 + 0.12 * bev - 0.01 * vein + 0.01 * drift
        shade = (0.96 - 0.05 * vein + 0.010 * drift) * (0.96 + 0.04 * bev)
    else:                          # smooth quartz (quartz_block_bottom): SEAMLESS - a soft cloud only (r32: no veins)
        slow = F.noise(1, 3, 0.5)
        cloud = slow + 0.5 * F.noise(4, 10, 0.5)                       # a soft marble cloud at two scales (colour)
        vein = np.zeros_like(slow)
        H = 0.55 + 0.02 * slow + 0.004 * F.noise(20, 60, 0.3)          # the relief: flat, a fine polish grain
        shade = 0.96 + 0.012 * cloud
    # r32 (critic: "whites are cream" - mostly the shader's grade; the texture's own warm cast cut to near neutral)
    alb = tint((0.93, 0.93, 0.92), shade)
    if kind in ("block_side", "block_top"):
        alb = alb * (1 - 0.07 * vein[..., None] * np.array([0.5, 0.25, 0.0])[None, None, :])   # a cool grey-blue vein
    return H, alb


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


def m_polished_granite(F, n):
    """Round 31 (art director: "dull red-brown"): polished granite is a pinkish-salmon crystal mosaic - salmon
    orthoclase feldspar, smoky-grey quartz, black mica flakes, a few white plagioclase - with the crystals large
    enough to read at 30 px (~6 texels, per_side 20; the rough granite's 4-texel grains average to mud at RTS zoom)
    and lower contrast than the rough block (a polished face is one calm stone), inside a bevelled block rim."""
    # r31b (eyes 41023221433: "confetti speckle - halve the speckle contrast, larger crystals, a faint sheen"):
    # per_side 20 -> 15 (~8.5 texels), contrast 0.7 -> 0.35, the bevel a clear edge; smoothness up in TARGETS
    # r32 (critic: "still ~1.5 stops too dark / red - lift it toward a pink-salmon granite with grey-white quartz
    # grains"): a paler pink feldspar, the quartz grains grey-WHITE and clearer (contrast 0.35 -> 0.5), less mica
    H, alb = m_crystal(F, n, [((0.88, 0.68, 0.62), 0.58), ((0.30, 0.27, 0.27), 0.06), ((0.80, 0.80, 0.80), 0.26),
                              ((0.94, 0.90, 0.88), 0.10)], per_side=15, cluster=0.5, contrast=0.5, mottle=0.05,
                       drift_base=3)
    bev = frame(F, 0.06, 0.012)
    return 0.35 + 0.5 * bev + 0.1 * (H - 0.6), alb * (0.72 + 0.28 * bev)[..., None]


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
    # r31 (art director: dirt path / raw iron / red sand / cut copper "all landed in one salmon band"): the path's
    # hue toward olive-tan (hue ~36 deg from dirt's 26), its value (palette) unchanged - fit_albedo sets it
    ap = np.clip((ad * 1.12 + 0.03) * np.array([0.99, 1.07, 0.92])[None, None, :], 0, 1)
    if not side:
        return Hp, ap
    band = F.v < (0.19 + 0.02 * F.noise(4, 12, 0.5)[0:1, :])
    return np.where(band, Hp + 0.1, Hd), np.where(band[..., None], ap, ad)


# ------------------------------------------------------------------ round 34: domed cobbles, laid stones ----------
def domed_stones(F, cells, jitter=0.9, warp=0.012, joint=(0.006, 0.030), flat=0.55, two_sizes=0.0):
    """Round 34 (lovish: cobbles as LARGE ROUNDED stones with lit domed tops, dark deep joints): a periodic field of
    stones packed in a bed. Each Voronoi cell is one stone whose top is a DOME - 1 at its centre, falling to 0 at the
    joint along the cell's own radius (f1 / (f1 + f2), so the dome's foot follows the stone's outline) - with a
    flatter crown (`flat`: the share of the radius over which the top stays high). Returns dict(dome 0..1, stone
    0..1 (0 in the joint), joint 0..1 (1 at the joint's floor), idx, k, f1, f2): the recipes paint colour and height
    from these. two_sizes > 0: that share of the cells is split into smaller stones (a mix of large and small)."""
    n = F.n
    f1, f2, idx, k = F.voronoi(cells, jitter)
    if two_sizes > 0:
        g1, g2, gidx, gk = F.voronoi(cells * 3, jitter)
        small = (F.rng.random(k) < two_sizes)[idx]
        f1, f2 = np.where(small, g1, f1), np.where(small, g2, f2)
        idx = np.where(small, gidx + k, idx)
        k = k + gk
    wu, wv = warp * F.noise(2, 7, 0.6), warp * F.noise(2, 7, 0.6)       # a slow warp: outlines bulge, no jags
    f1, f2 = F.warp(f1, wu, wv), F.warp(f2, wu, wv)
    idx = idx[(np.round((F.v + wv) * n).astype(int)) % n, (np.round((F.u + wu) * n).astype(int)) % n]
    edge = f2 - f1                                       # 0 at the joint line
    stone = smoothstep(joint[0], joint[1], edge)         # 0 in the joint, 1 on the stone
    r = np.clip(2 * f1 / np.maximum(f1 + f2, 1e-6), 0, 1)     # 0 at the stone's centre, 1 at its outline
    rr = np.clip((r - flat) / max(1 - flat, 1e-6), 0, 1)       # the crown flat, the flank falling
    dome = np.sqrt(np.clip(1 - rr * rr, 0, 1)) * stone         # a quarter-circle flank: a rounded cobble
    jointf = 1 - smoothstep(0.0, joint[1] * 1.6, edge)         # the joint's floor and its shoulder
    return dict(dome=dome, stone=stone, joint=jointf, idx=idx, k=k, f1=f1, f2=f2, r=r)


def m_cobble_domed(F, n, cells=16, moss=0.15, base=(0.50, 0.50, 0.49)):
    """Cobblestone, round 34: ~14 large rounded cobbles per face, each its own grey (cool, warm, a few dark), its
    domed top lit by the normal map and a little lighter in the albedo too (the plain game sees domes as well); the
    joints a dark packed grit three to five texels wide at 128 with deep AO; a little moss and a few blades of green
    in some of the joints (`moss`: how much - mossy cobblestone creeps it onto the stones' lower flanks)."""
    d = domed_stones(F, cells, jitter=0.9, warp=0.009, joint=(0.010, 0.050), flat=0.30, two_sizes=0.12)
    dome, stone, joint, idx, k = d["dome"], d["stone"], d["joint"], d["idx"], d["k"]
    rng = F.rng
    tone = rng.uniform(0.74, 1.20, k)[idx]
    kind = rng.random(k)[idx][..., None]
    rgb = np.where(kind < 0.55, np.array(base),
                   np.where(kind < 0.78, np.array([0.47, 0.49, 0.53]),       # blue-grey
                            np.where(kind < 0.92, np.array([0.54, 0.50, 0.45]), np.array([0.38, 0.38, 0.38]))))
    if moss > 0.5:       # the mossy block: near-neutral stones, so its saturation fit goes to the moss, not the stones
        rgb = rgb * 0.4 + 0.6 * (rgb @ LUMA)[..., None]
    grain = F.noise(14, 44, 0.3)
    pits = smoothstep(1.3, 2.2, -F.noise(8, 24, 0.5)) * stone     # a few shallow pits on the stones
    H = 0.15 + 0.75 * dome + 0.02 * grain * stone - 0.05 * pits - 0.10 * joint
    # colour: the crown a step lighter than the flank, the joint a dark grit
    lit = 0.76 + 0.32 * dome
    shade_stone = tone * lit * (1.0 + 0.05 * grain - 0.20 * pits)
    grit = 0.9 + 0.2 * F.noise(16, 48, 0.3)
    stone_rgb = rgb * shade_stone[..., None]
    joint_rgb = np.array([0.26, 0.245, 0.22]) * grit[..., None]
    alb = stone_rgb * stone[..., None] + joint_rgb * (1 - stone[..., None])
    # moss (the shared material, moss_cushions): in some of the joints and creeping up the stones' feet; mossy
    # cobblestone (moss > 0.5) also grows cushions on some of the stone tops - patches, not a green wash
    where = smoothstep(0.55 - 0.45 * moss, 1.0 - 0.45 * moss, F.noise(2, 8, 0.6))
    foot = 1 - smoothstep(0.0, 0.20 + 0.50 * moss, dome + 0.12 * F.noise(6, 20, 0.4))   # a ragged creep
    seed = np.clip(where * foot, 0, 1)
    if moss > 0.5:
        tops = smoothstep(0.62, 0.95, norm01(F.fbm(3, 3, 0.55))) * stone
        seed = np.clip(seed + tops, 0, 1)
    m, mdome, moss_rgb = moss_cushions(F, seed, min(1.0, 0.6 + moss))
    # the moss between stones sits in their shadow: darker than moss_block's open carpet (it read as lime specks)
    alb = alb * (1 - m[..., None]) + moss_rgb * 0.72 * m[..., None]
    H = H + 0.08 * m * mdome                             # the moss cushions stand a little proud
    return H, np.clip(alb, 0, 1)


def m_mud_bricks(F, n):
    """Mud bricks (r39): the battlefield wall's tower stands beside the eyes samplers in MUD_BRICKS + PACKED_MUD,
    which had no texture of ours and rendered as vanilla's under our shader as a purple-grey with soot blotches
    (eyes 41044148818). A warm tan sun-dried brick: four courses of two in a running bond like the clay bricks, the
    tones close (tan, a touch of ochre, a paler one), soft worn edges, a darker packed-mud mortar, a few darker damp
    blotches ~20% darker (not 60%)."""
    courses, per = 4, 2
    course = np.floor(F.v * courses).astype(int)
    in_c = (F.v * courses) % 1.0
    off = np.where(course % 2 == 0, 0.0, 0.5)
    ju = (F.u * per + off) % 1.0
    brick = (course * per + np.floor(F.u * per + off).astype(int)) % 64
    chip = 0.004 * F.noise(8, 24, 0.5)
    dv = np.minimum(in_c, 1 - in_c) / courses + chip
    du = np.minimum(ju, 1 - ju) / per + chip
    dj = np.minimum(dv, du)
    face = smoothstep(0.010, 0.022, dj)
    worn = smoothstep(0.010, 0.06, dj)
    rng = F.rng
    kind = rng.random(64)[brick]
    tone = (0.90 + 0.20 * rng.random(64))[brick]
    base = np.where(kind[..., None] < 0.6, np.array([0.62, 0.50, 0.34]),
                    np.where(kind[..., None] < 0.85, np.array([0.58, 0.44, 0.28]), np.array([0.68, 0.58, 0.42])))
    grain = F.noise(12, 40, 0.3)
    blotch = smoothstep(0.6, 0.9, norm01(F.fbm(3, 3, 0.55))) * face
    straw = smoothstep(2.3, 2.7, F.noise(16, 40, 0.0, sx=1.0, sy=5.0)) * face   # a few straw flecks in the clay
    H = 0.30 + 0.5 * np.sqrt(worn) * face + 0.008 * grain + 0.02 * F.fbm(4, 3, 0.5) * face + 0.02 * straw
    shade = tone * (0.84 + 0.14 * worn + 0.02 * grain) * (1 - 0.20 * blotch)
    alb = base * shade[..., None]
    mortar = np.array([0.46, 0.38, 0.27]) * (0.85 + 0.08 * grain)[..., None]
    alb = alb * face[..., None] + mortar * (1 - face[..., None])
    alb = alb * (1 - straw[..., None]) + straw[..., None] * np.array([0.78, 0.70, 0.48])
    return H, np.clip(alb, 0, 1)


def m_packed_mud(F, n):
    """Packed mud (r39): the sun-dried earth the mud bricks are made of - a smooth tamped face, broad soft damp
    blotches ~20% darker, a few hairline shrink cracks, straw flecks."""
    clumps = F.fbm(3, 3, 0.55)
    blotch = smoothstep(0.55, 0.9, norm01(F.fbm(2, 3, 0.55)))
    grain = F.noise(12, 40, 0.3)
    line = np.abs(F.warp(F.noise(2, 6, 0.5), 0.02 * F.noise(4, 10), 0.02 * F.noise(4, 10)))
    crack = (1 - smoothstep(0.0, 0.10, line)) * smoothstep(0.4, 0.8, norm01(F.noise(1, 3, 0.5)))
    straw = smoothstep(2.4, 2.8, F.noise(16, 40, 0.0, sx=1.0, sy=5.0))      # sparse (a dense fleck field tiled)
    H = 0.55 + 0.04 * clumps + 0.004 * grain - 0.10 * crack + 0.02 * straw
    shade = (0.92 + 0.05 * clumps + 0.02 * grain) * (1 - 0.20 * blotch) * (1 - 0.30 * crack)
    alb = tint((0.60, 0.48, 0.32), shade)
    alb = alb * (1 - straw[..., None]) + straw[..., None] * np.array([0.78, 0.70, 0.48])
    return H, np.clip(alb, 0, 1)


def m_cracked_path(F, n, side):
    """Dirt path, round 34 (lovish's reference, Oct 30 05:43: a CRACKED DRY-EARTH path): packed, sun-baked earth
    broken into irregular polygon plates by dark cracks 1-3 texels wide (at 128), the plates' edges a little raised
    and lit (dried earth curls up at a crack) in the height and the normal, the plate tops faintly concave with a
    fine grit, a few small pressed-in pebbles. The mean value and hue stay on the environment palette band (TARGETS
    dirt_path_top: olive-tan, not salmon) - the richness is the plate / crack contrast. No grass on the top: the
    path's irregular edge against the lawn is a separate overlay. The side: the path's edge (15/16 block) over dirt."""
    # plates of two sizes (a few large among the small - a real crack net is uneven), their outlines wandering
    f1, f2, idx, k = F.voronoi(14, 0.95)
    g1, g2, gidx, gk = F.voronoi(34, 0.95)
    small = (F.rng.random(k) < 0.45)[idx]
    f1, f2 = np.where(small, g1, f1), np.where(small, g2, f2)
    idx = np.where(small, gidx + k, idx)
    k = k + gk
    wu, wv = 0.014 * F.noise(3, 10, 0.5), 0.014 * F.noise(3, 10, 0.5)
    f1, f2 = F.warp(f1, wu, wv), F.warp(f2, wu, wv)
    idx = idx[(np.round((F.v + wv) * n).astype(int)) % n, (np.round((F.u + wu) * n).astype(int)) % n]
    gap = f2 - f1
    width = 0.009 + 0.005 * np.clip(F.noise(2, 6, 0.5), -1, 1)   # the cracks' width varies along the net
    crack = 1 - smoothstep(0.0, width, gap)                       # the open crack: 1-3 texels at 128
    lip = smoothstep(0.0, 0.022, gap) * (1 - smoothstep(0.022, 0.07, gap))    # the raised, curled plate edge
    inside = smoothstep(0.03, 0.12, gap)                           # the plate's concave middle
    grit = F.noise(12, 40, 0.3)
    clods = F.fbm(4, 3, 0.5)
    pf1, pf2, pidx, pk = F.voronoi(70, 1.0)
    peb = (F.rng.random(pk) < 0.16)[pidx] & (pf1 < 0.016) & (crack < 0.3)
    pebble = np.where(peb, np.clip(1 - pf1 / 0.016, 0, 1), 0.0)
    H = 0.55 - 0.30 * crack + 0.045 * lip - 0.03 * inside + 0.015 * grit + 0.02 * clods + 0.22 * pebble
    tone = F.rng.uniform(0.92, 1.08, k)[idx]
    shade = tone * (0.86 + 0.16 * lip + 0.03 * grit + 0.04 * clods - 0.04 * inside) * (1 - 0.42 * crack)
    hue = F.noise(2, 6, 0.5)
    jit = np.stack([0.04 * hue, 0.01 * hue, -0.04 * hue], -1)
    alb = tint((0.56, 0.47, 0.30), shade, jit)                     # olive-tan (hue ~39 deg), the fit sets the value
    alb = np.where(peb[..., None], np.clip(alb * 0.7 + 0.12, 0, 1) * np.array([0.95, 0.94, 0.92]), alb)
    alb = np.clip(alb, 0, 1)
    if not side:
        return H, alb
    Hd, ad = m_dirt(F, n)
    band = F.v < (0.19 + 0.02 * F.noise(4, 12, 0.5)[0:1, :])
    return np.where(band, H + 0.1, Hd), np.where(band[..., None], alb, ad)


# ------------------------------------------------------------------ round 35: clay bricks --------------------------
def m_clay_bricks(F, n):
    """Red clay bricks (r35; the build census's biggest missing block, 3.4% of the schematics' blocks): four courses
    of two bricks per block in a running bond (vanilla's layout), each brick its own fired tone - most a red-orange,
    some a darker brown-red clinker, a few paler and pinker - with worn, rounded arrises, a face of fine sand grain
    with a few chips and pits, set in a recessed pale lime mortar with real AO in the joint. It must read as BRICK,
    not stone_bricks, at 32 px: the colour and the four narrow courses do that."""
    courses, per = 4, 2
    course = np.floor(F.v * courses).astype(int)
    in_c = (F.v * courses) % 1.0
    off = np.where(course % 2 == 0, 0.0, 0.5)
    ju = (F.u * per + off) % 1.0
    brick = (course * per + np.floor(F.u * per + off).astype(int)) % 64
    chip = 0.003 * F.noise(8, 24, 0.5)
    dv = np.minimum(in_c, 1 - in_c) / courses + chip
    du = np.minimum(ju, 1 - ju) / per + chip
    dj = np.minimum(dv, du)
    joint_w = 0.012                                       # ~1.5 texels at 128 each side: 3 texels of mortar
    face = smoothstep(joint_w, joint_w + 0.010, dj)       # 0 in the mortar, 1 on the brick
    arris = smoothstep(joint_w, joint_w + 0.045, dj)      # the worn, rounded edge: a soft shoulder
    rng = F.rng
    kind = rng.random(64)[brick]
    tone = (0.86 + 0.28 * rng.random(64))[brick]
    base = np.where(kind[..., None] < 0.62, np.array([0.64, 0.30, 0.20]),              # red-orange
                    np.where(kind[..., None] < 0.85, np.array([0.48, 0.24, 0.18]),      # darker clinker
                             np.array([0.70, 0.42, 0.32])))                            # paler, pinker
    flash = norm01(F.noise(2, 5, 0.5, sx=3.0, sy=1.0))     # firing flash along each brick: a darker end
    grain = F.noise(14, 44, 0.3)
    pits = smoothstep(0.9, 1.05, F.noise(10, 30, 0.4)) * face
    H = 0.28 + 0.55 * np.sqrt(arris) * face + 0.03 * F.fbm(3, 3, 0.5) * face + 0.015 * grain - 0.08 * pits
    shade = tone * (0.82 + 0.16 * arris - 0.12 * (flash - 0.5) + 0.02 * grain - 0.25 * pits)
    alb = base * shade[..., None]
    mortar = np.array([0.66, 0.62, 0.55]) * (0.80 + 0.06 * grain)[..., None]
    alb = alb * face[..., None] + mortar * (1 - face[..., None])
    return H, np.clip(alb, 0, 1)


# ------------------------------------------------------------------ round 36: the dyed families -------------------
# The build census's most-used blocks still vanilla's: wool, concrete and the coloured terracottas, 16 colours each -
# ONE recipe per family, the colour from a table (our own hues, kept natural: lime a clean lime, purple a lavender-
# purple, cyan mid-bright, orange clean), the values stepped so neighbours separate at 32 px. The targets are the
# table colour's own luma and saturation (colour_targets), so the fit keeps the hue and sets only the spread.
def m_wool(F, n, rgb):
    """Wool: a felted nap - matted fibre with no direction (round 37: a plain weave of 22 or 14 yarns per block both
    read as STRIPES at 32 px in the lit preview, wool_compare.png; the felt reads as cloth), soft tufts under it, a
    slow drift of tone; soft and matte (the smoothness and tilt from TARGETS: no specular)."""
    nap = F.noise(10, 36, 0.4)
    fibre = F.noise(30, 80, 0.2)
    tuft = F.fbm(5, 3, 0.55)
    drift = F.fbm(2, 3, 0.5)
    H = 0.5 + 0.06 * nap + 0.02 * fibre + 0.05 * tuft
    shade = 0.92 + 0.06 * nap + 0.015 * fibre + 0.06 * tuft + 0.04 * drift
    return H, tint(rgb, shade)


def m_concrete(F, n, rgb):
    """Concrete: a smooth cast face - faint form marks (a few broad straight bands where the shuttering boards met),
    a soft cloud of tone, sparse small air pores; slightly satin (TARGETS)."""
    boards = np.floor(F.v * 3 + 0.3).astype(int)
    mark = 1 - smoothstep(0.0, 0.012, np.abs(((F.v * 3 + 0.3) % 1.0) - 0.5) / 3)     # a hairline where boards met
    btone = (F.rng.random(4) - 0.5)[boards % 4] * 0.03
    cloud = F.fbm(2, 3, 0.55)
    pores = smoothstep(0.90, 1.0, norm01(F.noise(14, 36, 0.3))) * smoothstep(0.3, 0.7, norm01(F.noise(1, 4, 0.5)))
    fine = F.noise(20, 60, 0.3)
    H = 0.6 + 0.02 * cloud + 0.006 * fine - 0.06 * pores - 0.015 * mark
    shade = 0.94 + 0.05 * cloud + btone + 0.004 * fine - 0.12 * pores - 0.04 * mark
    return H, tint(rgb, shade)


def m_bookshelf(F, n):
    """Bookshelf (the side): three shelves of books in an oak frame - spines of varied width, colour and height,
    some leaning, a pale title band on each, the shelf and the books' tops in real depth (the books recessed from
    the frame, AO in the gaps, the gap between two books a shadow, never a black hole)."""
    rng = F.rng
    plank = tint((0.66, 0.51, 0.31), 0.9 + 0.08 * F.noise(2, 40, 0.8, sx=10.0, sy=1.0))
    H = np.full((n, n), 0.85)
    alb = plank.copy()
    shelves = [(0.06, 0.34), (0.39, 0.66), (0.71, 0.95)]       # (top, bottom) of each books row, v
    side = 0.04                                                 # the frame's uprights
    palette = [(0.55, 0.18, 0.16), (0.20, 0.30, 0.55), (0.25, 0.42, 0.25), (0.48, 0.32, 0.18), (0.72, 0.62, 0.42),
               (0.30, 0.22, 0.40), (0.62, 0.45, 0.20), (0.18, 0.18, 0.20), (0.60, 0.30, 0.35), (0.35, 0.50, 0.55)]
    for top, bot in shelves:
        u = side
        while u < 1 - side - 0.02:
            w = 0.035 + 0.06 * rng.random()
            w = min(w, 1 - side - u)
            if w < 0.02:
                break
            col = np.array(palette[rng.integers(len(palette))]) * (0.85 + 0.3 * rng.random())
            h = 0.72 + 0.28 * rng.random()                      # the book's height: a share of the shelf
            lean = (rng.random() - 0.5) * 0.02 * (rng.random() < 0.3)
            bt = bot - h * (bot - top)
            depth = 0.55 + 0.15 * rng.random()                  # how far back the spine sits
            uu = F.u - lean * (F.v - bot) / (bot - top)
            inb = (uu >= u) & (uu < u + w - 0.006) & (F.v >= bt) & (F.v < bot)
            # the spine: rounded across its width, a pale title band near the top, bands at the head and tail
            x = (uu - u) / max(w - 0.006, 1e-6)
            round_ = np.sqrt(np.clip(1 - (2 * x - 1) ** 2, 0, 1))
            band = smoothstep(0.0, 0.03, (F.v - bt) / (bot - bt) - 0.12) * (1 - smoothstep(0.0, 0.03, (F.v - bt) / (bot - bt) - 0.22))
            sp = col * (0.75 + 0.35 * round_)[..., None] * (1 + 0.5 * band[..., None] * np.array([1.1, 1.0, 0.8]))
            alb = np.where(inb[..., None], np.clip(sp, 0, 1), alb)
            H = np.where(inb, depth + 0.10 * round_, H)
            # the gap beside it: the shelf back in shadow
            gap = (uu >= u + w - 0.006) & (uu < u + w) & (F.v >= top) & (F.v < bot)
            alb = np.where(gap[..., None], np.array([0.12, 0.09, 0.06]), alb)
            H = np.where(gap, 0.25, H)
            # above a short book: the shelf back (dark wood) above it
            above = (uu >= u) & (uu < u + w - 0.006) & (F.v >= top) & (F.v < bt)
            alb = np.where(above[..., None], plank * 0.35, alb)
            H = np.where(above, 0.25, H)
            u += w
        # the shelf board's front edge under the books
        lip = (F.v >= bot) & (F.v < bot + 0.05)
        alb = np.where(lip[..., None], plank * 1.05, alb)
        H = np.where(lip, 0.9, H)
    grain = F.noise(20, 60, 0.3)
    return H + 0.01 * grain, np.clip(alb, 0, 1)


# ------------------------------------------------------------------ round 37: chiseled bookshelf, flagstones -------
def m_chiseled_bookshelf(F, n, face):
    """Chiseled bookshelf (vanilla's UV: six slots, three across in two rows, x 1-5 / 6-10 / 11-15, y 1-7 / 9-15 of
    16). 'empty': the slots are shallow recesses showing the shelf's dark oak back - a recess with AO, never a black
    hole (the critic on sampler 3); 'occupied': each slot holds three or four books; 'side': the oak frame with the
    two shelf grooves; 'top': planks with a border."""
    x, y = F.u * 16, F.v * 16
    grain = F.noise(2, 40, 0.8, sx=10.0, sy=1.0)
    plank = tint((0.66, 0.51, 0.31), 0.9 + 0.08 * grain)
    H = np.full((n, n), 0.85) + 0.01 * F.noise(20, 60, 0.3)
    alb = plank.copy()
    if face == "top":
        bev = frame(F, 0.06, 0.012)
        return H - 0.08 * (1 - bev), alb * (0.92 + 0.08 * bev)[..., None]
    if face == "side":
        groove = ((np.abs(y - 8.0) < 0.5) | (y < 0.6) | (y > 15.4)).astype(np.float64)
        return H - 0.15 * groove, alb * (1 - 0.18 * groove)[..., None]
    rng = F.rng
    palette = [(0.55, 0.18, 0.16), (0.20, 0.30, 0.55), (0.25, 0.42, 0.25), (0.48, 0.32, 0.18), (0.72, 0.62, 0.42),
               (0.30, 0.22, 0.40), (0.62, 0.45, 0.20), (0.60, 0.30, 0.35), (0.35, 0.50, 0.55)]
    for row, (y0, y1) in enumerate(((1.0, 7.0), (9.0, 15.0))):
        for col, (x0, x1) in enumerate(((1.0, 5.0), (6.0, 10.0), (11.0, 15.0))):
            slot = (x >= x0) & (x < x1) & (y >= y0) & (y < y1)
            inset = np.minimum(np.minimum(x - x0, x1 - x), np.minimum(y - y0, y1 - y))
            shadow = 1 - smoothstep(0.0, 1.2, inset)                   # the recess's walls in shadow
            back = plank * 0.45 * (1 - 0.35 * shadow)[..., None]
            alb = np.where(slot[..., None], back, alb)
            H = np.where(slot, 0.45, H)
            if face == "occupied":
                u = x0 + 0.2
                while u < x1 - 0.5:
                    w = 0.8 + 1.2 * rng.random()
                    w = min(w, x1 - 0.2 - u)
                    colr = np.array(palette[rng.integers(len(palette))]) * (0.85 + 0.3 * rng.random())
                    h = (y1 - y0) * (0.70 + 0.30 * rng.random())
                    book = (x >= u) & (x < u + w - 0.15) & (y >= y1 - h) & (y < y1)
                    t = (x - u) / max(w - 0.15, 1e-6)
                    round_ = np.sqrt(np.clip(1 - (2 * t - 1) ** 2, 0, 1))
                    band = ((y - (y1 - h)) / h > 0.12) & ((y - (y1 - h)) / h < 0.22)
                    sp = colr * (0.75 + 0.35 * round_)[..., None] * (1 + 0.5 * band[..., None])
                    alb = np.where(book[..., None], np.clip(sp, 0, 1), alb)
                    H = np.where(book, 0.62 + 0.10 * round_, H)
                    u += w
    return H, np.clip(alb, 0, 1)


def m_flagstone_sheet(F, blocks):
    """Round 37: smooth stone as a FLAGSTONE floor over a sheet of blocks x blocks (Athena's giant model): large
    irregular pale limestone slabs (~3 per block across, many straddling the block edges - no block grid), set with
    wider dark gaps, each slab its own subtle tone, its edges worn and slightly domed, hairline dark cracks across
    some slabs, a fine sawn grain. F is a Field of the sheet's size; the single-block smooth_stone texture stays the
    slab's and the particle's."""
    n = F.n
    cells = int(round((1.6 * blocks) ** 2))              # ~1.6 slabs across per block: large flags
    f1, f2, idx, k = F.voronoi(cells, 1.0)
    wu, wv = 0.004 * F.noise(1 * blocks, 3 * blocks, 0.6), 0.004 * F.noise(1 * blocks, 3 * blocks, 0.6)
    f1, f2 = F.warp(f1, wu, wv), F.warp(f2, wu, wv)
    idx = idx[(np.round((F.v + wv) * n).astype(int)) % n, (np.round((F.u + wu) * n).astype(int)) % n]
    edge = f2 - f1
    gap_w = 0.008 / blocks                                # ~0.008 block: a wide joint
    slab = smoothstep(gap_w, gap_w + 0.02 / blocks, edge)
    worn = smoothstep(gap_w, gap_w + 0.10 / blocks, edge)   # the domed, worn edge: a soft shoulder
    tone = (0.93 + 0.12 * F.rng.random(k))[idx]
    cloud = F.fbm(2 * blocks, 3, 0.6)
    grain = F.noise(6 * blocks, 20 * blocks, 0.4)
    pores = smoothstep(1.9, 2.6, F.noise(10 * blocks, 30 * blocks, 0.0))
    line = np.abs(F.warp(F.noise(1 * blocks, 4 * blocks, 1.0), 0.02 * F.noise(3, 9), 0.02 * F.noise(3, 9)))
    where = smoothstep(0.55, 0.85, norm01(F.noise(1, 3 * blocks, 0.5)))
    crack = (1 - smoothstep(0.0, 0.05, line)) * where * slab
    H = 0.30 + 0.55 * np.sqrt(worn) * slab + 0.015 * grain - 0.03 * pores - 0.12 * crack
    shade = tone * (0.82 + 0.16 * worn) * (0.95 + 0.05 * cloud + 0.02 * grain - 0.08 * pores - 0.30 * crack)
    joint = np.array([0.40, 0.39, 0.38]) * (0.9 + 0.1 * grain)[..., None]
    alb = tint((0.55, 0.57, 0.61), shade) * slab[..., None] + joint * (1 - slab[..., None])
    return H, np.clip(alb, 0, 1)


# ------------------------------------------------------------------ round 38: powders, glazes, the odd blocks ------
def m_concrete_powder(F, n, rgb):
    """Concrete powder: dry pigment - a granular field of fine grains in soft clumps, matte, a little lighter and
    less saturated than its concrete (the colour is pre-mixed by the caller)."""
    grains = F.noise(14, 44, 0.2)
    clumps = F.fbm(4, 3, 0.55)
    fine = F.noise(30, 80, 0.0)
    f1, f2, idx, k = F.voronoi(400, 1.0)
    cell = 1.0 / math.sqrt(400)
    crumb = np.clip(1 - (f1 / (0.6 * cell)) ** 2, 0, 1) * (F.rng.random(k) < 0.4)[idx]
    H = 0.5 + 0.08 * clumps + 0.04 * grains + 0.02 * fine + 0.06 * crumb
    shade = 0.92 + 0.06 * clumps + 0.04 * grains + 0.02 * fine + 0.05 * crumb
    return H, tint(rgb, shade)


def m_glazed(F, n, colour, kind):
    """Glazed terracotta, round 38: our OWN motifs (never vanilla's designs) - one of four fantasy-medieval families
    picked per colour: 'vine' (a curling tendril with leaves from one corner), 'star' (an eight-point star on a
    quarter, its rays to the far corners), 'knot' (an interlaced band loop, open on two sides), 'scales' (a fish-
    scale field with a quarter-rosette). Each motif is asymmetric, so vanilla's facing rotation lays four blocks
    into a larger pattern. Glossy glaze: a crackle net in the relief and a faint line in the colour, the glaze
    pooled darker at the tile's edges, smoothness ~200 (the recipe paints it: lower in the crackle)."""
    base = np.asarray(colour, dtype=np.float64)
    light = np.clip(base * 0.35 + 0.65 * np.array([0.95, 0.92, 0.85]), 0, 1)     # cream slip
    dark = np.clip(base * 0.45, 0, 1)                                              # the dark line
    u, v = F.u, F.v
    motif = np.zeros((n, n))        # 1 = the light slip, -1 = the dark line, 0 = the glaze
    if kind == "vine":
        # a tendril curling out of the bottom-left corner: a thick dark stem along an arc, cream leaves (outlined
        # discs with a dark midrib) alternating on each side of it, a curl at the tip
        r = np.sqrt(u * u + (1 - v) ** 2)
        ang = np.arctan2(1 - v, u)
        stem = 1 - smoothstep(0.012, 0.03, np.abs(r - 0.66))
        motif = np.where((stem > 0.5) & (ang > 0.08) & (ang < 1.45), -1.0, motif)
        curl = 1 - smoothstep(0.012, 0.03, np.abs(np.sqrt((u - 0.10) ** 2 + (v - 0.38) ** 2) - 0.08))
        motif = np.where((curl > 0.5) & (u > 0.10), -1.0, motif)
        for i in range(4):
            a = 0.25 + i * 0.36
            rr = 0.66 + (0.13 if i % 2 == 0 else -0.13)
            cx, cy = rr * math.cos(a), 1 - rr * math.sin(a)
            d = np.sqrt((u - cx) ** 2 + (v - cy) ** 2)
            motif = np.where(d < 0.085, 1.0, motif)                              # the leaf
            motif = np.where((d > 0.070) & (d < 0.085), -1.0, motif)             # its outline
            mid = np.abs((u - cx) * math.sin(a) + (v - cy) * math.cos(a)) < 0.008
            motif = np.where(mid & (d < 0.07), -1.0, motif)                      # its midrib
    elif kind == "star":
        # an eight-point star on the top-right corner (a quarter shows: two squares, one turned 45 degrees), a dark
        # core, a thin diagonal ray to the far corner, a cream dot there
        du, dv = u - 1.0, v
        sq1 = np.maximum(np.abs(du), np.abs(dv)) < 0.30
        sq2 = (np.abs(du) + np.abs(dv)) < 0.44
        r = np.sqrt(du * du + dv * dv)
        motif = np.where(sq1 | sq2, 1.0, motif)
        motif = np.where(r < 0.13, -1.0, motif)
        diag = 1 - smoothstep(0.0, 0.015, np.abs(u - v))
        motif = np.where((diag > 0.5) & (r > 0.50), -1.0, motif)
        dots = np.sqrt((u - 0.18) ** 2 + (v - 0.82) ** 2) < 0.07
        motif = np.where(dots, 1.0, motif)
        motif = np.where(np.sqrt((u - 0.18) ** 2 + (v - 0.82) ** 2) < 0.03, -1.0, motif)
    elif kind == "knot":
        # an interlaced band: a loop whose two ends run off the left and bottom edges
        d1 = np.abs(np.sqrt((u - 0.55) ** 2 + (v - 0.45) ** 2) - 0.30)
        band = 1 - smoothstep(0.045, 0.065, d1)
        line = 1 - smoothstep(0.0, 0.012, np.abs(d1 - 0.055))
        out1 = (1 - smoothstep(0.045, 0.065, np.abs(v - 0.45))) * (u < 0.3)
        out2 = (1 - smoothstep(0.045, 0.065, np.abs(u - 0.55))) * (v > 0.7)
        motif = np.where((band > 0.5) | (out1 > 0.5) | (out2 > 0.5), 1.0, motif)
        motif = np.where(line > 0.5, -1.0, motif)
        motif = np.where((1 - smoothstep(0.0, 0.012, np.abs(np.abs(v - 0.45) - 0.055)) > 0.5) & (u < 0.3), -1.0, motif)
        motif = np.where((1 - smoothstep(0.0, 0.012, np.abs(np.abs(u - 0.55) - 0.055)) > 0.5) & (v > 0.7), -1.0, motif)
    else:   # scales
        rows = 5
        row = np.floor(v * rows)
        uu = (u * rows + 0.5 * (row % 2)) % 1.0
        vv = (v * rows) % 1.0
        d = np.sqrt((uu - 0.5) ** 2 + (vv - 1.0) ** 2) * (1.0 / rows)
        scale_line = 1 - smoothstep(0.0, 0.012, np.abs(d - 0.5 / rows))
        motif = np.where(scale_line > 0.5, -1.0, motif)
        shade_sc = smoothstep(0.3 / rows, 0.5 / rows, d)
        motif = np.where((motif == 0) & (shade_sc > 0.6), 0.5, motif)
        ros = np.sqrt((u - 0.0) ** 2 + (v - 0.0) ** 2)
        motif = np.where(ros < 0.26, 1.0, motif)
        motif = np.where(np.abs(ros - 0.26) < 0.015, -1.0, motif)
        motif = np.where(ros < 0.10, -1.0, motif)
    # the glaze: pooled darker toward the edges, a crackle net
    e = np.minimum(np.minimum(u, 1 - u), np.minimum(v, 1 - v))
    pool = 1 - smoothstep(0.0, 0.08, e)
    f1, f2, idx, k = F.voronoi(140, 1.0)
    crackle = 1 - smoothstep(0.0, 0.004, f2 - f1)
    gloss = F.fbm(2, 3, 0.5)
    alb = np.where(motif[..., None] > 0.75, light, np.where(motif[..., None] < -0.5, dark,
                   np.where(motif[..., None] > 0.25, np.clip(base * 1.18, 0, 1), base)))
    alb = alb * (1 - 0.22 * pool[..., None]) * (1 + 0.04 * gloss[..., None]) * (1 - 0.12 * crackle[..., None])
    H = 0.6 + 0.02 * gloss - 0.03 * crackle + 0.03 * (motif > 0.75) - 0.02 * (motif < -0.5) - 0.02 * pool
    smooth = np.clip(205 - 60 * crackle - 10 * pool, 80, 230)
    return dict(H=H, alb=np.clip(alb, 0, 1), smooth=smooth)


GLAZE_KIND = {"white": "star", "light_gray": "knot", "gray": "scales", "black": "vine", "brown": "knot",
              "red": "star", "orange": "vine", "yellow": "star", "lime": "vine", "green": "scales",
              "cyan": "knot", "light_blue": "scales", "blue": "star", "purple": "knot", "magenta": "vine",
              "pink": "scales"}


def m_crafting_table(F, n, face):
    """Crafting table: an oak-planks box. 'top': a worked oak top with a 3 x 3 grid scored into it (saw marks and a
    few nicks); 'front': the planks with two hung tools - a saw blade and a hammer - as painted silhouettes in
    relief; 'side': the planks with a drawer (a recessed panel with an iron pull)."""
    H, alb = m_planks(F, n, (0.66, 0.51, 0.31), 0.012)
    H = H * 0.6 + 0.2
    if face == "top":
        gx = np.abs(((F.u * 3) % 1.0) - 0.5)
        gy = np.abs(((F.v * 3) % 1.0) - 0.5)
        score = np.maximum(smoothstep(0.45, 0.49, gx), smoothstep(0.45, 0.49, gy))     # 1 on the grid's lines
        rim = frame(F, 0.06, 0.012)
        nick = smoothstep(0.85, 0.97, norm01(F.noise(6, 18, 0.4)))
        H = H - 0.14 * score - 0.06 * nick + 0.06 * (1 - rim)
        alb = alb * (1 - 0.35 * score - 0.15 * nick + 0.06 * (1 - rim))[..., None]
        return H, np.clip(alb, 0, 1)
    if face == "side":
        panel = frame(F, 0.14, 0.02) * (1 - frame(F, 0.30, 0.02))     # a drawer front: a raised border
        inner = frame(F, 0.30, 0.02)
        pull = 1 - smoothstep(0.03, 0.045, np.sqrt((F.u - 0.5) ** 2 + (F.v - 0.5) ** 2))
        H = H + 0.08 * panel - 0.04 * inner + 0.15 * pull
        alb = alb * (1 + 0.08 * panel - 0.06 * inner)[..., None]
        alb = np.where(pull[..., None] > 0.5, np.array([0.30, 0.30, 0.32]), alb)
        return H, np.clip(alb, 0, 1)
    # front: a saw (a blade with teeth) on the left, a hammer on the right, hung on the planks
    blade = (F.u > 0.12) & (F.u < 0.42) & (F.v > 0.25) & (F.v < 0.60) & (F.v > 0.25 + 0.6 * (F.u - 0.12))
    teeth = ((F.u * 40) % 1.0) < 0.5
    blade = blade & ~((F.v > 0.25 + 0.6 * (F.u - 0.12) - 0.0) & (F.v < 0.25 + 0.6 * (F.u - 0.12) + 0.03) & ~teeth)
    handle = (F.u > 0.08) & (F.u < 0.20) & (F.v > 0.55) & (F.v < 0.80)
    head = (F.u > 0.56) & (F.u < 0.86) & (F.v > 0.22) & (F.v < 0.36)
    shaft = (F.u > 0.68) & (F.u < 0.75) & (F.v > 0.36) & (F.v < 0.82)
    iron = np.array([0.58, 0.58, 0.60]) * (0.9 + 0.1 * brushed(F, 0.6))[..., None]
    wood = np.array([0.40, 0.28, 0.16])
    alb = np.where((blade | head)[..., None], iron, alb)
    alb = np.where((handle | shaft)[..., None], wood * (0.9 + 0.2 * F.noise(8, 30, 0.4))[..., None], alb)
    H = np.where(blade | head | handle | shaft, 0.85, H)
    return H, np.clip(alb, 0, 1)


def m_rooted_dirt(F, n):
    """Rooted dirt: our dirt crossed by a net of pale fibrous roots, thick and thin, a few knots."""
    Hd, ad = m_dirt(F, n)
    line = np.abs(F.warp(F.noise(2, 6, 0.6), 0.03 * F.noise(3, 9), 0.03 * F.noise(3, 9)))
    thick = 1 - smoothstep(0.0, 0.30, line)                  # ~3 texels (unit-std noise: 0.035 was a third of one)
    line2 = np.abs(F.warp(F.noise(4, 12, 0.6), 0.02 * F.noise(4, 10), 0.02 * F.noise(4, 10)))
    thin = (1 - smoothstep(0.0, 0.14, line2)) * smoothstep(0.3, 0.7, norm01(F.noise(1, 4, 0.5)))
    root = np.clip(thick + 0.7 * thin, 0, 1)
    root_rgb = np.array([0.72, 0.62, 0.46]) * (0.85 + 0.3 * norm01(F.noise(10, 30, 0.3)))[..., None]
    alb = ad * (1 - root[..., None]) + root_rgb * root[..., None]
    return Hd + 0.14 * root, np.clip(alb, 0, 1)


def m_mushroom_stem(F, n):
    """Mushroom stem: a pale fibrous stalk - long vertical fibres, a soft lumpy surface, a few darker bruises."""
    fib = F.noise(6, 30, 0.4, sx=1.0, sy=8.0)
    lumps = F.fbm(3, 3, 0.55, sx=1.0, sy=2.0)
    bruise = smoothstep(0.7, 0.95, norm01(F.fbm(2, 3, 0.5)))
    H = 0.55 + 0.08 * lumps + 0.03 * fib
    shade = 0.90 + 0.06 * lumps + 0.04 * fib - 0.12 * bruise
    return H, tint((0.82, 0.78, 0.70), shade)


def m_composter(F, n, face):
    """Composter: spruce slats bound by two iron hoops ('side'), the open top's rim ('top': a square ring of slat
    ends round a dark void), 'bottom' the floor of slats, 'compost' a bed of brown leaf litter and crumbs, 'ready'
    the finished compost with bone-meal flecks."""
    if face in ("compost", "ready"):
        Hd, ad = m_dirt(F, n)
        litter = smoothstep(0.5, 0.85, norm01(F.noise(6, 18, 0.4)))
        alb = ad * np.array([0.9, 0.85, 0.75]) * (1 - 0.25 * litter)[..., None] + litter[..., None] * np.array([0.45, 0.32, 0.14])
        if face == "ready":
            fl = smoothstep(2.0, 2.6, F.noise(20, 50, 0.0))
            alb = alb * (1 - fl[..., None]) + fl[..., None] * np.array([0.90, 0.90, 0.82])
        return Hd, np.clip(alb, 0, 1)
    H, alb = m_planks(F, n, (0.46, 0.34, 0.20), 0.012)
    H, alb = np.rot90(H), np.rot90(alb)                   # upright slats
    alb = alb * 0.9
    if face == "side":
        hoop = ((np.abs(F.v - 0.22) < 0.045) | (np.abs(F.v - 0.78) < 0.045)).astype(np.float64)
        iron = np.array([0.30, 0.30, 0.32]) * (0.9 + 0.15 * brushed(F, 0.6))[..., None]
        alb = np.where(hoop[..., None] > 0.5, iron, alb)
        H = np.where(hoop > 0.5, H + 0.12, H)
        return H, np.clip(alb, 0, 1)
    if face == "top":
        e = np.minimum(np.minimum(F.u, 1 - F.u), np.minimum(F.v, 1 - F.v))
        inner = smoothstep(0.11, 0.13, e)                 # 1 inside the rim: the dark void
        alb = np.where(inner[..., None] > 0.5, np.array([0.08, 0.06, 0.04]), alb * 1.05)
        H = np.where(inner > 0.5, 0.1, 0.85)
        return H, np.clip(alb, 0, 1)
    return H, np.clip(alb, 0, 1)


def m_note_block(F, n, kind):
    """Note block ('note'): an oak box with a round speaker grille of holes on each face; jukebox side ('jukebox'): the
    box with a brass inlay band; jukebox top ('jukebox_top'): the lid with a dark record slot."""
    H, alb = m_planks(F, n, (0.50, 0.36, 0.20), 0.012)
    H = H * 0.6 + 0.2
    if kind == "note":
        r = np.sqrt((F.u - 0.5) ** 2 + (F.v - 0.5) ** 2)
        disc = 1 - smoothstep(0.27, 0.29, r)
        ring = (1 - smoothstep(0.0, 0.02, np.abs(r - 0.28)))
        hx, hy = ((F.u * 14) % 1.0) - 0.5, ((F.v * 14) % 1.0) - 0.5
        holes = (1 - smoothstep(0.18, 0.26, np.sqrt(hx * hx + hy * hy))) * disc
        alb = alb * (1 - 0.35 * disc)[..., None]
        alb = np.where(holes[..., None] > 0.5, np.array([0.06, 0.05, 0.04]), alb)
        alb = np.where(ring[..., None] > 0.5, np.array([0.62, 0.50, 0.22]), alb)   # a brass ring
        H = H - 0.10 * disc - 0.15 * holes + 0.08 * ring
        return H, np.clip(alb, 0, 1)
    if kind == "jukebox":
        band = ((F.v > 0.40) & (F.v < 0.52)).astype(np.float64)
        inlay = band * (1 - smoothstep(0.0, 0.01, np.abs(((F.u * 6) % 1.0) - 0.5) - 0.25))
        brass = np.array([0.70, 0.56, 0.24]) * (0.9 + 0.15 * brushed(F, 0.6))[..., None]
        alb = np.where(band[..., None] > 0.5, brass * (1 - 0.3 * inlay)[..., None], alb)
        H = H + 0.06 * band - 0.04 * inlay
        return H, np.clip(alb, 0, 1)
    rim = frame(F, 0.08, 0.015)
    slot = ((np.abs(F.v - 0.5) < 0.025) & (F.u > 0.2) & (F.u < 0.8)).astype(np.float64)
    alb = alb * (0.92 + 0.08 * rim)[..., None]
    alb = np.where(slot[..., None] > 0.5, np.array([0.05, 0.05, 0.05]), alb)
    H = H + 0.06 * rim - 0.2 * slot
    return H, np.clip(alb, 0, 1)


# ------------------------------------------------------------------ round 34: the glowing blocks -----------------
# LabPBR emission lives in the _s alpha: 0-254 = how much of the albedo glows, 255 = none. Only the hot or lit parts
# glow (magma's cracks, the ore's crystals, the lamp's core), with a bright core and a falloff; the body stays unlit.
# check_labpbr refuses emission on more than half of a texture's texels, so a glowing block is still mostly body.
def glow_falloff(core, peak=254.0):
    """The emission code of a glow field `core` (0..1): `peak` (0-254, 254 = full) at 1 falling to 0 at the glow's
    edge, 255 (none) where there is no glow at all."""
    return np.where(core > 0.02, np.clip(peak * np.clip(core, 0, 1), 0, 254), 255.0)


def m_magma(F, n):
    """Magma block: a dark, slaggy basalt crust broken into plates by a network of cracks through which the melt
    glows - yellow-white at the widest cracks, falling through orange to a dull red at the crust's edge; the plates
    domed a little and pitted, the cracks deep (the crust floats on the melt). The glow is emission on the cracks."""
    f1, f2, idx, k = F.voronoi(18, 0.95)
    wu, wv = 0.016 * F.noise(2, 7, 0.6), 0.016 * F.noise(2, 7, 0.6)
    f1, f2 = F.warp(f1, wu, wv), F.warp(f2, wu, wv)
    gap = f2 - f1
    width = 0.020 + 0.012 * np.clip(F.noise(2, 6, 0.5), -1, 1)
    melt = 1 - smoothstep(0.0, width, gap)                       # the open crack: 1 at its middle
    heat = 1 - smoothstep(0.0, width * 2.2, gap)                 # the crust's edge, baking
    # thinner secondary cracks within the plates (not all glowing: some cold)
    fine = 1 - smoothstep(0.0, 0.012, np.abs(F.warp(F.noise(4, 12, 0.5), 0.02 * F.noise(4, 10), 0.02 * F.noise(4, 10))))
    fine *= smoothstep(0.3, 0.8, F.noise(2, 5, 0.5))
    hot_fine = fine * smoothstep(0.4, 0.9, F.noise(2, 6, 0.5))
    melt = np.clip(melt + 0.7 * hot_fine, 0, 1)
    heat = np.clip(heat + 0.8 * hot_fine, 0, 1)
    r = np.clip(2 * f1 / np.maximum(f1 + f2, 1e-6), 0, 1)
    dome = np.sqrt(np.clip(1 - r * r, 0, 1))
    pits = smoothstep(1.2, 2.0, -F.noise(10, 30, 0.5))
    grit = F.noise(16, 50, 0.3)
    H = 0.25 + 0.55 * dome * (1 - melt) + 0.03 * grit - 0.08 * pits - 0.25 * melt - 0.12 * fine * (1 - hot_fine)
    crust = tint((0.16, 0.14, 0.13), 0.85 + 0.25 * dome + 0.05 * grit - 0.3 * pits - 0.4 * fine * (1 - hot_fine))
    # the glow's colour by temperature: dull red at the crust's edge -> orange -> yellow-white in the crack's middle
    t = np.clip(0.35 * heat + 0.65 * melt, 0, 1)
    glow_rgb = np.stack([0.55 + 0.45 * t, 0.10 + 0.70 * t ** 1.6, 0.02 + 0.55 * t ** 4], -1)
    w = np.clip(heat, 0, 1)[..., None]
    alb = crust * (1 - w) + glow_rgb * w
    emit = glow_falloff(np.clip(0.25 * heat + 0.75 * melt, 0, 1))
    smooth = 40 + 120 * melt                                     # the melt glassy, the crust rough
    return dict(H=H, alb=np.clip(alb, 0, 1), smooth=smooth, emit=emit)


def m_redstone_ore(F, n, deep):
    """Redstone ore: the host rock (stone, or deepslate's upright slate) with clusters of blood-red crystals - small
    angular facets grouped in 3-5 veins, each facet its own shade, bright glassy tops - glowing from inside (dim: a
    redstone ore lit up, not a lamp), the host rock unlit."""
    if deep:
        H, alb = m_deepslate(F, n)
    else:
        H, alb = m_stone(F, n)
    # the veins: where a slow field is high, filled with small facet cells
    vein = smoothstep(0.55, 0.85, norm01(F.fbm(2, 3, 0.55) + 0.4 * F.noise(4, 10, 0.5)))
    f1, f2, idx, k = F.voronoi(400, 1.0)
    cell = 1.0 / math.sqrt(400)
    inner = smoothstep(0.0, 0.20 * cell, 0.80 * cell - f1)       # a facet with a thin dark rim
    seam = smoothstep(0.0, 0.006, f2 - f1)                       # the dark seam between facets
    on = (F.rng.random(k) < 0.75)[idx]
    crystal = np.clip(vein * inner * on, 0, 1)
    facet = (0.3 + 0.7 * F.rng.random(k))[idx]                   # each facet its own shade: deep garnet to bright
    glint = smoothstep(0.8, 1.0, facet)
    red = np.stack([0.30 + 0.60 * facet, 0.02 + 0.06 * facet + 0.45 * glint, 0.02 + 0.04 * facet + 0.40 * glint], -1)
    red = red * (0.35 + 0.65 * seam)[..., None]
    alb = alb * (1 - crystal[..., None]) + red * crystal[..., None]
    H = H + 0.14 * crystal * (0.5 + 0.5 * facet) * seam - 0.05 * vein * (1 - crystal)
    emit = glow_falloff(crystal * seam * (0.4 + 0.6 * facet), peak=150.0)
    smooth = np.where(crystal > 0.5, 150.0 + 60 * facet, 22.0 if not deep else 12.0)
    return dict(H=H, alb=np.clip(alb, 0, 1), smooth=smooth, emit=emit)


def m_glowstone(F, n):
    """Glowstone: a packed cluster of yellow-amber crystal nodules, each lit from inside - bright cores, their rims
    darker amber - in a dark resinous matrix; the nodules' tops are the emission (bright cores, a falloff to the
    rim), the matrix between them unlit."""
    f1, f2, idx, k = F.voronoi(90, 1.0)
    g1, g2, gidx, gk = F.voronoi(260, 1.0)
    big = (F.rng.random(k) < 0.55)[idx]
    f1, f2 = np.where(big, f1, g1), np.where(big, f2, g2)
    idx = np.where(big, idx, gidx + k)
    k = k + gk
    r = np.clip(2 * f1 / np.maximum(f1 + f2, 1e-6), 0, 1)
    nodule = smoothstep(0.004, 0.02, f2 - f1)                    # 0 at the joint between nodules
    dome = np.sqrt(np.clip(1 - r * r, 0, 1)) * nodule
    core = np.clip(1 - (r / 0.68) ** 2, 0, 1) * nodule           # the lit heart of each nodule
    tone = (0.75 + 0.35 * F.rng.random(k))[idx]
    lit_on = (F.rng.random(k) < 0.7)[idx]                        # some nodules dark (burnt out)
    glow = core * tone * lit_on
    H = 0.2 + 0.7 * dome + 0.02 * F.noise(16, 48, 0.3)
    amber = np.stack([0.96, 0.72, 0.24]) * (0.70 + 0.30 * dome)[..., None] * tone[..., None]
    bright = np.array([1.0, 0.94, 0.66])
    alb = amber * (1 - glow[..., None]) + bright * glow[..., None]
    alb = np.where(lit_on[..., None], alb, amber * 0.70)
    matrix = np.array([0.50, 0.34, 0.12])
    alb = alb * nodule[..., None] + matrix * (1 - nodule[..., None])
    emit = glow_falloff(glow)
    smooth = 120 + 80 * core
    return dict(H=H, alb=np.clip(alb, 0, 1), smooth=smooth, emit=emit)


def m_shroomlight(F, n):
    """Shroomlight: a spongy fungal mass, orange-red, its skin pocked with round pores that glow from inside (the
    emission - the pores' depths bright yellow-orange, the skin unlit); a few larger lit blisters."""
    f1, f2, idx, k = F.voronoi(120, 1.0)
    cell = 1.0 / math.sqrt(120)
    on = (F.rng.random(k) < 0.6)[idx]
    rad = (0.28 + 0.2 * F.rng.random(k))[idx] * cell
    pore = np.clip(1 - (f1 / np.maximum(rad, 1e-6)) ** 2, 0, 1) * on
    blister = smoothstep(0.7, 0.95, norm01(F.fbm(3, 3, 0.55)))
    spongy = F.noise(6, 20, 0.5)
    H = 0.6 + 0.08 * spongy - 0.25 * pore + 0.1 * blister
    skin = tint((0.78, 0.36, 0.14), 0.85 + 0.15 * norm01(spongy) + 0.1 * blister)
    glow = np.clip(pore ** 0.8 + 0.6 * blister, 0, 1)
    hot = np.array([1.0, 0.80, 0.35])
    alb = skin * (1 - glow[..., None]) + hot * glow[..., None]
    emit = glow_falloff(np.clip(pore + 0.5 * blister, 0, 1), peak=240.0)
    smooth = 60 + 100 * pore
    return dict(H=H, alb=np.clip(alb, 0, 1), smooth=smooth, emit=emit)


def m_sea_lantern(F, n):
    """Sea lantern: a pale prismarine frame round a glowing core - the frame a bevelled ring of cool grey-green
    stone (unlit), the core a panel of milky aqua-white crystal with a bright centre and brighter seams where the
    crystal is thinnest; the core is the emission."""
    e = np.minimum(np.minimum(F.u, 1 - F.u), np.minimum(F.v, 1 - F.v))
    bevel = smoothstep(0.12, 0.17, e) * (1 - smoothstep(0.17, 0.20, e))
    core = smoothstep(0.17, 0.21, e)
    cx, cy = F.u - 0.5, F.v - 0.5
    rr = np.sqrt(cx * cx + cy * cy)
    centre = np.clip(1 - (rr / 0.36) ** 2, 0, 1)
    f1, f2, idx, k = F.voronoi(20, 0.8)
    seam = 1 - smoothstep(0.0, 0.015, f2 - f1)                  # crystal seams, the thinnest glass: brightest
    grain = F.noise(8, 24, 0.4)
    H = np.where(core > 0.5, 0.80 + 0.02 * grain - 0.03 * seam, 0.55 + 0.25 * bevel + 0.02 * grain)
    stone = tint((0.58, 0.68, 0.62), 0.9 + 0.15 * bevel + 0.04 * grain)
    glass = np.stack([0.70 + 0.28 * centre + 0.1 * seam, 0.88 + 0.12 * centre, 0.90 + 0.10 * centre], -1)
    alb = stone * (1 - core[..., None]) + np.clip(glass, 0, 1) * core[..., None]
    glow = core * np.clip(0.45 + 0.45 * centre + 0.4 * seam, 0, 1)
    emit = glow_falloff(glow)
    smooth = np.where(core > 0.5, 200.0, 70.0)
    return dict(H=H, alb=np.clip(alb, 0, 1), smooth=smooth, emit=emit)


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


def m_soul_sand(F, n):
    """Round 31 (art director: "reads as dirt"): grey-brown ASH sand - fine grains, soft dunes - with a few shallow
    hollows a face-width across that suggest a skull (a rounded dish with two deeper sockets side by side and a
    short groove below), the depth in the height / normal so the light finds them, and a handful of pale bone chips
    lying in the sand. Dark fantasy as an accent; the block stays sand."""
    rng = F.rng
    grains = F.noise(20, 56, 0.2)
    dune = F.fbm(2, 3, 0.5)
    H = 0.6 + 0.08 * dune + 0.03 * grains
    shade = 0.92 + 0.10 * norm01(H) + 0.04 * grains
    hue = F.noise(2, 6, 0.4)
    jit = np.stack([0.03 * hue, 0.0 * hue, -0.03 * hue], -1)
    # r31b (eyes 41023221433: the sides "show dark dashes in horizontal rows that repeat vertically", the base
    # "reads grey pumice"): warmer brown (toward the benchmark's top), and every hollow turned to its own full
    # angle so the socket pairs never line up as a row of dashes
    alb = tint((0.46, 0.36, 0.27), shade, jit)
    hollow = np.zeros((n, n))
    sockets = np.zeros((n, n))
    for _ in range(5):
        cx, cy = rng.random(2)
        rad = rng.uniform(0.11, 0.15)
        ang = rng.uniform(0, 2 * math.pi)
        dx = (F.u - cx + 0.5) % 1.0 - 0.5
        dy = (F.v - cy + 0.5) % 1.0 - 0.5
        x = dx * math.cos(ang) + dy * math.sin(ang)
        y = -dx * math.sin(ang) + dy * math.cos(ang)
        dish = np.clip(1 - (x * x + (y * 1.15) ** 2) / (rad * rad), 0, 1)
        hollow = np.maximum(hollow, dish ** 1.2)
        for sx_ in (-0.36, 0.36):                         # two eye sockets, deeper, soft-walled
            d2 = ((x - sx_ * rad) ** 2 + (y + 0.22 * rad) ** 2) / (0.34 * rad) ** 2
            sockets = np.maximum(sockets, np.exp(-1.5 * d2) * (d2 < 2.5))
        groove = np.exp(-((y - 0.5 * rad) / (0.14 * rad)) ** 2) * np.clip(1 - (x / (0.4 * rad)) ** 2, 0, 1)
        sockets = np.maximum(sockets, 0.5 * groove)
    H = H - 0.25 * hollow - 0.22 * sockets
    alb = alb * (1 - 0.14 * hollow - 0.18 * sockets)[..., None]
    # bone chips: a few pale irregular flakes, 3-7 texels
    chip = smoothstep(0.84, 0.92, norm01(F.warp(F.noise(4, 10, 0.4), 0.01 * F.noise(4, 10), 0.01 * F.noise(4, 10))))
    chip = chip * smoothstep(0.6, 0.85, norm01(F.noise(1, 3, 0.5)))     # only in some places
    H = H + 0.06 * chip
    alb = alb * (1 - chip[..., None]) + chip[..., None] * tint((0.58, 0.54, 0.46), 0.92 + 0.08 * grains)
    return H, alb


def m_podzol_side(F, n):
    """Podzol side: dirt with the litter's edge on top (as the grass side has its fringe)."""
    Hd, ad = m_dirt(F, n)
    band = F.v < (0.15 + 0.04 * F.noise(4, 12, 0.5)[0:1, :])
    litter = tint((0.42, 0.28, 0.15), 0.8 + 0.3 * norm01(F.noise(10, 30, 0.3)))
    return np.where(band, Hd + 0.1, Hd), np.where(band[..., None], litter, ad)


# Round 34: ONE moss material for every mossy block (the builds' weathering run moss_block -> mossy_cobblestone ->
# mossy_stone_bricks -> stone_bricks; the family sheet showed moss_block a yellow-olive and mossy_stone_bricks' moss
# a brighter lime). moss_block's hue; the blocks differ in coverage only.
MOSS_RGB = (0.36, 0.48, 0.18)


def moss_cushions(F, seed, strength, cells=300):
    """Moss clumps where `seed` (0..1) says it grows: rounded cushions ~7 texels across (Voronoi cells with a domed
    profile) inside soft-edged contiguous patches, a velvet grain, the MOSS_RGB colour. Returns (m 0..1 coverage,
    dome, moss_rgb): albedo = alb * (1 - m) + moss_rgb * m, height + ~0.1 * m * dome."""
    f1, f2, idx, k = F.voronoi(cells, 1.0)
    cell = 1.0 / math.sqrt(cells)
    dome = np.clip(1 - (f1 / (0.62 * cell)) ** 2, 0, 1)
    grow = (0.7 + 0.3 * F.rng.random(k))[idx] * seed
    cover = smoothstep(0.22, 0.5, grow)
    m = np.clip(strength * cover * (0.35 + 0.65 * dome ** 0.5), 0, 1)
    velvet = 0.85 + 0.3 * norm01(F.noise(10, 30, 0.3))
    return m, dome, tint(MOSS_RGB, velvet * (0.8 + 0.4 * dome))


def frame(F, width, groove):
    """A border bevel round the block (cut / polished stone): 1 inside, falling to 0 in a groove at the edge, so tiled
    blocks read as separate stones. width and groove in tile units."""
    d = np.minimum(np.minimum(F.u, 1 - F.u), np.minimum(F.v, 1 - F.v))
    return smoothstep(0.0, groove, d) * (0.85 + 0.15 * smoothstep(groove, width, d))


def m_stone_bricks(F, n, moss=0.0, cracks=0.0):
    """Stone bricks (r31, art director: "mortar too heavy and dark so it reads as cobble - narrower, lighter
    mortar, flatter faces, moss as edge tint not blobs"): two courses per block, two dressed blocks per course,
    joints offset by half a brick (vanilla's layout; the mossy and cracked blocks share it). The mortar is a thin
    light-grey line (~2 texels at 128, a hair lighter than the stone, as lime mortar is), the arrises crisp, the
    faces flat with a soft weathering and a few pits in the relief. moss: a green tint creeping from the joints
    over the lower brick edges; cracks: a hairline through some bricks."""
    course = np.floor(F.v * 2).astype(int)
    in_c = (F.v * 2) % 1.0
    ju = (F.u + np.where(course % 2 == 0, 0.0, 0.5)) % 1.0
    chip = 0.003 * F.noise(8, 24, 0.5)                    # ragged joints
    dv = np.minimum(in_c, 1 - in_c) / 2 + chip           # distance to a bed joint (tile units)
    du = np.minimum(ju, 1 - ju) + chip                   # to a head joint
    dj = np.minimum(dv, du)
    # r35 (station render: the courses turned into a uniform stripe at 16 px): the joint a little wider and darker,
    # recessed deeper, so the blocks read as blocks from the RTS camera
    face = smoothstep(0.007, 0.020, dj)                  # 0 in the mortar, 1 on the face
    hf = smoothstep(0.007, 0.013, dj)                    # the height step crisper still
    # small tone steps between the bricks: the texture repeats on every block (stone bricks are never turned), so one
    # darker brick became a lattice across a wall (rts_view)
    tone = (F.rng.random(4) - 0.5)[(course * 2 + (ju > 0.5)) % 4] * 1.6     # r35: more brick-to-brick spread
    weather = F.fbm(3, 4, 0.55)
    grain = F.noise(12, 40, 0.3)
    pits = smoothstep(0.78, 0.95, F.noise(10, 30, 0.4))
    H = 0.30 + 0.55 * hf + 0.03 * weather * face + 0.015 * grain - 0.06 * pits * face
    stone = 0.88 + 0.05 * tone + 0.045 * weather + 0.008 * grain - 0.03 * pits
    mortar = 0.68 + 0.03 * grain                          # r35: the recessed joint in shadow, still not black
    shade = stone * face + mortar * (1 - face)
    hue = F.noise(2, 5, 0.5) * (0.0 if moss > 0 else 1.0)   # (the mossy block's sat fit would blow the jitter up)
    jit = np.stack([0.02 * hue, 0.0 * hue, -0.02 * hue], -1)
    alb = tint((0.52, 0.52, 0.51), shade, jit)
    alb = np.where((face < 0.5)[..., None], alb * np.array([1.0, 0.99, 0.96])[None, None, :], alb)   # mortar warm
    if moss > 0:
        # r32 (critic: "mossy needs saturated green moss patches at 6-8 px scale - moss on edges and joints, real
        # green, readable from above"; r31's joint tint read grey): moss as CLUMPS - rounded cushions 6-8 texels
        # across (Voronoi cells with a domed profile), seeded where the joints are and spreading over the brick
        # arrises and corners in a few patches along the wall, a real green with a velvet grain; the clumps stand a
        # little proud of the stone
        near = 1 - smoothstep(0.02, 0.14, dj)                            # the joints and the arrises beside them
        patch = norm01(F.fbm(2, 3, 0.55))
        seed = np.clip(near * (0.3 + 0.9 * patch) + 0.6 * smoothstep(0.7, 0.95, patch), 0, 1)   # where moss grows
        m, dome, moss_rgb = moss_cushions(F, seed, moss)                 # r34: the shared moss material
        alb = alb * (1 - m[..., None]) + moss_rgb * m[..., None]
        H = H + 0.10 * m * dome
    if cracks > 0:
        # r32 (critic: "cracked needs 2-3 px dark fissures that survive mipmaps at 32 px per block"): r31's crack
        # threshold (0.014 on a unit-std noise(2, 7)) was a twentieth of a texel wide - it never showed. Now a
        # fissure ~4 texels across with a soft dark halo (~10 texels: what the mipmaps keep) through about half
        # the bricks, and pale chipped edges where the stone broke
        line = np.abs(F.warp(F.noise(1, 4, 1.0), 0.02 * F.noise(4, 12), 0.02 * F.noise(4, 12)))
        where = smoothstep(0.1, 0.5, F.noise(1, 3, 0.5)) * cracks * face
        crack = (1 - smoothstep(0.0, 0.09, line)) * where
        halo = (1 - smoothstep(0.06, 0.35, line)) * where
        chip = smoothstep(0.09, 0.2, line) * (1 - smoothstep(0.2, 0.4, line)) * where \
            * smoothstep(0.3, 0.7, norm01(F.noise(3, 9, 0.5)))
        H = H - 0.18 * crack - 0.04 * halo - 0.03 * chip
        alb = alb * (1 - 0.42 * crack - 0.10 * halo + 0.10 * chip)[..., None]
    return H, alb


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
    return H, tint((0.52, 0.54, 0.57), shade, jit)       # r35: cooler than the stone bricks


def m_smooth_stone(F, n):
    # sawn limestone-like slab face: very flat, faint clouding, a thin groove at the border
    # the reference's relief (17.7 deg at 32 texels per block) is a sawn, granular face, not soft bumps: the tilt comes
    # from grain at 1/20-1/6 block and a few pores, the clouding stays in the colour
    cloud = F.fbm(2, 4, 0.6)
    grain = F.noise(6, 20, 0.4)                          # 1/20-1/6 block: survives the mipmaps (finer read as pitting)
    pores = smoothstep(1.4, 2.4, F.noise(10, 30, 0.0))   # r31: larger pores (the speckle was too fine at RTS zoom)
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
    border's (height, relief): rough bark by default; a stripped log's bare-wood rim is nearly flat.
    Round 35 (lovish: "radial drying cracks, growth rings and saturated heartwood"): the rings unevenly spaced
    (wide earlywood, a thin dark latewood line each, the pith a little off centre and the rings wobbling), a darker
    more saturated HEARTWOOD inside a paler sapwood, and 4-6 drying cracks running out from the pith - widest there,
    tapering, deep in the height and dark - with a few fine ones between."""
    rng = F.rng
    cx, cy = 0.5 + 0.04 * (rng.random() - 0.5), 0.5 + 0.04 * (rng.random() - 0.5)   # the pith a little off centre
    du, dv = F.u - cx, F.v - cy
    r = np.sqrt(du * du + dv * dv)
    ang = np.arctan2(dv, du)
    r_w = r * (1 + 0.04 * F.noise(1, 3, 0.5)) + 0.005 * F.noise(4, 12, 0.5)        # the rings wobble
    # uneven ring spacing: the radius warped by a slow function, ~18 rings to the edge
    rw = r_w + 0.010 * np.sin(r_w * 2 * math.pi * 3.3 + 1.0)
    ph = (rw * 18) % 1.0
    late = smoothstep(0.62, 0.78, ph) * (1 - smoothstep(0.86, 1.0, ph))              # the thin dark latewood line
    early = 1 - late
    bark = smoothstep(0.43, 0.47, np.maximum(np.abs(F.u - 0.5), np.abs(F.v - 0.5)))   # the square bark border
    # drying cracks: 4-6 radial, from the pith out to 0.15-0.45 of the block, widest at the pith
    cracks = np.zeros((n, n))
    k = 4 + int(rng.random() * 3)
    for i in range(k):
        a0 = i * 2 * math.pi / k + (rng.random() - 0.5) * 0.9
        length = 0.15 + 0.30 * rng.random()
        da = np.abs(((ang - a0 + math.pi) % (2 * math.pi)) - math.pi)
        across = da * r                                     # distance from the crack line (tile units)
        wide = 0.020 * (1 - r / length)                     # width tapers to the tip
        c = (1 - smoothstep(0.0, np.maximum(wide, 1e-4), across)) * (r < length) * (r > 0.01)
        cracks = np.maximum(cracks, c)
    fine = np.zeros((n, n))
    for i in range(6):
        a0 = rng.random() * 2 * math.pi
        r0, length = 0.12 + 0.2 * rng.random(), 0.08 + 0.12 * rng.random()
        da = np.abs(((ang - a0 + math.pi) % (2 * math.pi)) - math.pi)
        c = (1 - smoothstep(0.0, 0.004, da * r)) * (r > r0) * (r < r0 + length)
        fine = np.maximum(fine, c)
    cracks = np.clip(cracks + 0.6 * fine, 0, 1) * (bark < 0.5)
    grain = F.noise(20, 60, 0.3)
    H = 0.62 - 0.05 * late + 0.02 * grain - 0.22 * cracks
    H = np.where(bark > 0.5, rim[0] + rim[1] * F.noise(8, 30, 0.5), H)
    heart = 1 - smoothstep(0.16, 0.30, r_w)                 # the heartwood: darker and more saturated
    wood = np.asarray(wood, dtype=np.float64)
    sap = wood * np.array([1.06, 1.04, 1.02])
    core = wood * np.array([0.84, 0.66, 0.52])
    body = sap * (1 - heart)[..., None] + core * heart[..., None]
    shade = 0.92 + 0.10 * early - 0.22 * late + 0.02 * grain - 0.55 * cracks
    alb = np.clip(body * shade[..., None], 0, 1)
    bark_alb = tint(bark_rgb, 0.75 + 0.4 * norm01(H))
    alb = np.where(bark[..., None] > 0.5, bark_alb, alb)
    return H, alb


# ------------------------------------------------------------------ round 15: the face census's partial blocks ---
def m_farmland(F, n, moist):
    """Farmland (r31, art director: "furrows not a dotted grid, darker moist troughs"): tilled soil in four rows
    across the block - continuous ridges and troughs (no pebbles: m_dirt's stones drew a dotted grid), the crumbs
    elongated along the rows, the troughs darker (and in the moist field wet-dark and smoother), the dry field's
    ridges paler with hairline cracks.
    Round 34 (lovish: "wet, glossy tilled furrows"): the ridge a crisper crest of broken clods, the furrow floor a
    smooth trodden channel; the MOIST field paints its own smoothness - the wet floor and the clods' wet faces glossy
    (so a shader's sun glint runs along the furrows), the dry field matte with a pale dusty crust on the crests."""
    ph = (F.v * 4 + 0.05 * F.noise(1, 3, 0.5, sx=3.0, sy=1.0)) % 1.0     # the rows wander a little along u
    ridge = np.sin(math.pi * ph) ** 2                    # 0 in a furrow, 1 on a ridge
    crest = ridge ** 2.2                                 # the crest narrower than the flank
    clumps = F.fbm(3, 3, 0.55, sx=2.0, sy=1.0)           # soil clods, a little longer along the rows
    # clods: broken lumps along the ridges (Voronoi cells, each a small dome) - the tilled look
    f1, f2, idx, k = F.voronoi(200, 1.0, aniso=(0.75, 1.0))
    g1, g2, gidx, gk = F.voronoi(500, 1.0, aniso=(0.75, 1.0))
    big = (F.rng.random(k) < 0.55)[idx]                  # clods of two sizes
    f1 = np.where(big, f1, g1)
    cell = np.where(big, 1.0 / math.sqrt(200), 1.0 / math.sqrt(500))
    size = np.where(big, (0.6 + 0.4 * F.rng.random(k))[idx], 0.7 * (0.6 + 0.4 * F.rng.random(gk))[gidx])
    clod = np.clip(1 - (f1 / (0.80 * cell)) ** 2, 0, 1) * size
    crumbs = F.noise(10, 32, 0.3, sx=2.5, sy=1.0)
    floor = 1 - smoothstep(0.0, 0.30, ridge)             # the furrow's trodden floor
    H = 0.22 + 0.36 * ridge + 0.26 * clod * ridge ** 1.2 + 0.05 * clumps * ridge + 0.02 * crumbs * (1 - floor)
    hue = F.noise(2, 6, 0.5)
    jit = np.stack([0.05 * hue, 0.02 * hue, -0.04 * hue], -1)
    trough = 0.58 if moist else 0.74
    shade = trough + (1.0 - trough) * ridge + 0.06 * clumps + 0.03 * crumbs + (0.34 * clod - 0.17) * ridge ** 1.5
    alb = tint((0.44, 0.31, 0.20) if moist else (0.46, 0.33, 0.22), shade, jit)
    if not moist:
        crack = 1 - smoothstep(0.0, 0.02, np.abs(F.warp(F.noise(3, 9, 0.5), 0.02 * F.noise(4, 10), 0.02 * F.noise(4, 10))))
        crack *= smoothstep(0.3, 0.8, F.noise(2, 5, 0.5)) * ridge
        H = H - 0.12 * crack
        alb = alb * (1 - 0.25 * crack)[..., None] * (1 + 0.10 * crest)[..., None]   # a pale dusty crust
        return dict(H=H, alb=np.clip(alb, 0, 1))
    # wet: the furrow floor glossy (standing damp), the clods' faces damp-shiny, the crests drying first
    wetness = np.clip(floor * 1.0 + 0.55 * (1 - crest) * smoothstep(0.2, 0.8, clod) + 0.25 * (1 - ridge), 0, 1)
    smooth = 40 + 150 * wetness
    return dict(H=H, alb=np.clip(alb, 0, 1), smooth=smooth)


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
# round 37: the other 14 stained glasses, their tint from the wool hue of DYES (a glass is a lighter, cleaner veil
# of the dye); red and cyan keep their r16 colours above
STAINED_R37 = {}


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
    strip (glass_pane_top: the edge of the sheet seen end-on - all glass, opaque).
    Round 36 (lovish: "faint frost and droplets, high smoothness for the sky reflection, dark slim frame lines,
    still clearly see-through"): a slimmer, darker frame line; a faint FROST creeping in from the corners (a feathery
    noise, pale, in the alpha on the cut-out block) and a few round DROPLETS (a bright rim, a dark heart, a dome in
    the normal), the rest of the pane open; glass-smooth (235) everywhere but the frame."""
    uc, vc = F.u + 0.5 / n, F.v + 0.5 / n                      # texel centres: the same rim width on every side
    d = np.minimum(np.minimum(uc, 1 - uc), np.minimum(vc, 1 - vc))
    rim = 1 - smoothstep(0.018, 0.030, d)                      # ~1/40 block: slim
    g = ((F.u + F.v) * 1.0 + 0.15 * F.noise(1, 3, 0.5) * 0.1) % 1.0
    glint = (1 - smoothstep(0.004, 0.012, np.abs(g - 0.30))) + 0.6 * (1 - smoothstep(0.003, 0.009, np.abs(g - 0.36)))
    glint *= smoothstep(0.2, 0.6, F.noise(1, 3, 0.5) * 0.5 + 0.5) * (1 - rim)
    smudge = F.noise(2, 8, 0.6)
    base = np.asarray(rgb, dtype=np.float64)
    if kind == "edge":
        shade = 0.9 + 0.05 * F.noise(2, 8, 0.6, sx=1.0, sy=6.0)   # streaks along v: the model maps column u 7-9
        return dict(H=0.5 + 0.02 * smudge, alb=tint(base * 0.85, shade))
    # frost: feathery, from the corners, thinning toward the middle of the pane
    corner = np.sqrt((F.u - 0.5) ** 2 + (F.v - 0.5) ** 2)
    feather = norm01(F.blur(F.fbm(6, 3, 0.6) + 0.5 * F.noise(14, 40, 0.3), 1.2))
    frost = smoothstep(0.62, 0.9, feather * (0.2 + 1.1 * smoothstep(0.38, 0.70, corner))) * (1 - rim)
    # droplets: a few round drops, each a dome with a bright rim and a darker heart
    f1, f2, idx, k = F.voronoi(60, 1.0)
    on = (F.rng.random(k) < 0.22)[idx]
    rad = (0.012 + 0.012 * F.rng.random(k))[idx]
    dr = np.clip(1 - (f1 / np.maximum(rad, 1e-6)) ** 2, 0, 1) * on * (1 - rim)
    drop = dr > 0.02
    drop_rim = smoothstep(0.0, 0.35, dr) * (1 - smoothstep(0.35, 0.8, dr))
    alb = tint(base, 0.95 + 0.03 * smudge)
    alb = alb * (1 - 0.45 * rim[..., None]) + 0.45 * rim[..., None] * (base * 0.35)   # the frame line dark
    alb = np.clip(alb + 0.35 * glint[..., None], 0, 1)
    alb = alb * (1 - frost[..., None]) + frost[..., None] * np.clip(base * 1.08, 0, 1)   # frost pale
    alb = np.where(drop[..., None], np.clip(base * (0.75 + 0.45 * drop_rim)[..., None], 0, 1), alb)
    if kind == "stained":
        # round 37 (cathedral-window richness at 32 px): a faint inner lead line a tenth of the block in from the
        # frame, and a slow mottle of the tint (hand-blown glass is never one even colour)
        lead = (1 - smoothstep(0.004, 0.010, np.abs(d - 0.10))) * (1 - rim)
        mottle = 1 + 0.10 * F.noise(2, 6, 0.6) + 0.04 * F.noise(6, 16, 0.4)
        alb = np.clip(alb * mottle[..., None] * (1 - 0.55 * lead[..., None]), 0, 1)
    if kind == "clear":
        alpha = ((rim > 0.5) | (glint > 0.35) | (frost > 0.5) | drop).astype(np.float64)
    elif kind == "stained":
        alpha = np.clip(0.52 + 0.03 * smudge + 0.40 * rim + 0.3 * glint + 0.2 * frost + 0.3 * dr, 0, 1)   # r37: a richer veil
    else:   # tinted
        alpha = np.clip(0.80 + 0.02 * smudge + 0.15 * rim + 0.1 * glint + 0.1 * frost, 0, 1)
    H = 0.5 + 0.35 * rim + 0.02 * smudge + 0.12 * np.sqrt(dr) + 0.03 * frost
    return dict(H=H, alb=alb, alpha=alpha, smooth=np.where(rim > 0.5, 150.0, np.where(frost > 0.5, 120.0, 235.0)))


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
    # r34: a FLAME in the pane - a teardrop core (yellow-white, tallest upward) with the glow falling off to the
    # pane's corners; the emission follows it (254 at the core, dim at the edges), the glass itself a warm amber
    dy = (y - 6.4) / np.where(y < 6.4, 2.0, 1.1)
    core = np.exp(-(((x - 3.0) / 0.75) ** 2 + dy ** 2))
    halo = np.exp(-(((x - 3.0) / 1.6) ** 2 + ((y - 6.0) / 2.4) ** 2))
    lit = pane & ~mullion
    white = np.array([1.0, 0.97, 0.85])
    glass = np.asarray(glow) * (0.45 + 0.55 * halo[..., None])
    glass = np.clip(glass * (1 - core[..., None] ** 1.5) + white * core[..., None] ** 1.5, 0, 1)
    alb = np.where(side[..., None], iron_c, alb)
    alb = np.where(lit[..., None], glass, alb)
    H = np.where(side, np.where(lit, 0.45, 0.8), H)
    smooth = np.where(lit, 225.0, smooth)
    f0 = np.where(lit, 10, f0)
    emit = np.where(lit, np.clip(90 + 164 * np.maximum(core, 0.6 * halo), 0, 254), emit)
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
# ------------------------------------------------------------------ photo scans (round 28) -----------------------
# lovish Oct 22 20:23 ("yes switch them and keep improving"): stone, dirt and grass come from CC0 photo scans
# (ambientCG, public domain - NOTICE.md) instead of procedural noise; rounds 17-26 of procedural stone, dirt and
# grass plateaued at ~5/10 (fresh review of PR #160's round 26: "soft procedural noise, not rock", "moss / felt").
# tools/scan_prep.py keeps 512 px copies of the seamless ambientCG tiles in tools/scans/; one tile = one block. Every
# map comes from the scan: the colour (fitted to the material's targets like any other texture), the scan's own
# normals (scaled to the material's tilt target - maps(nxy=)) and its displacement as the height (kept shallow,
# CALM_HEIGHT).
# SEAMS: vanilla turns and mirrors stone, dirt and grass tops at random per block. On a photo that breaks every
# border (the scan's texels on two sides of a border are unrelated) and mirroring drew kaleidoscope stars across a
# stone floor (offline 8 x 8 preview, round 28 step 1). So these blocks are NOT turned (SCAN_BLOCKSTATES): each picks
# one of SCAN_VARIANTS textures that all share the same border band - the seamless tile itself - and differ inside
# an irregular blob, where the same scan shows turned a quarter, a half or three quarters. Any two neighbours then
# meet seamlessly, and the repeat is broken inside the blocks.
SCAN_DIR = os.path.join(ROOT, "tools", "scans")
SCAN_STONE = "Rock058"     # cool grey faceted rock, iron-stained fracture edges - stone that reads as rock
SCAN_SOIL = "Ground106"    # brown topsoil with darker damp patches and leaf litter (its leaves turned brown)
SCAN_GRASS = "Grass004"    # a meadow, not a lawn: dense blades of mixed green, dry straw and dark gaps
SCAN_BASALT = "Rock035"    # dark fine-grained volcanic rock with hairline fractures - basalt's side and its end
SCAN_VARIANTS = 6
LUMA = np.array([0.2126, 0.7152, 0.0722])


def scan_map(asset, kind, n):
    """One map of a scan, resampled to n x n with its edges wrapped (it stays seamless): 'Color' -> rgb 0..1,
    'Displacement' -> 0..1, 'NormalGL' -> (nx, ny) in this pack's convention (y DOWN the texture; GL's green points
    up the image)."""
    im = Image.open(os.path.join(SCAN_DIR, "%s_%s.jpg" % (asset, kind)))
    im = im.convert("L" if kind == "Displacement" else "RGB")
    w = im.size[0]
    if w != n:
        big = Image.new(im.mode, (3 * w, 3 * w))
        for i in range(3):
            for j in range(3):
                big.paste(im, (i * w, j * w))
        im = big.resize((3 * n, 3 * n), Image.LANCZOS).crop((n, n, 2 * n, 2 * n))
    a = np.asarray(im, dtype=np.float64) / 255.0
    if kind == "NormalGL":
        return a[..., 0] * 2 - 1, -(a[..., 1] * 2 - 1)
    return a


def flatten(F, rgb, cycles, keep=0.0):
    """Divides out a scan's light and dark patches larger than ~1/cycles of the block (on the luma; each texel keeps
    its colour), keeping `keep` of them. The Gaussian's half-power point sits at `cycles` per tile."""
    L = rgb @ LUMA
    lo = F.blur(L, 0.1874 * F.n / cycles)
    ratio = (lo.mean() / np.maximum(lo, 1e-4)) ** (1 - keep)
    return np.clip(rgb * ratio[..., None], 0, 1)


def neutral(rgb):
    """The scan's mean colour divided out (a grey of the same luma), its hue SPREAD kept: a texture the biome colour
    tints, whose dry blades still come out yellower and the dark gaps bluer than the average blade."""
    mean = rgb.reshape(-1, 3).mean(0)
    return np.clip(rgb / mean * (mean @ LUMA), 0, 1)


def no_green(rgb):
    """Soil only: moss flecks and fallen leaves turn olive-brown (green never above red) - the grass side's soil must
    not read as grass below its turf (the S5 gate), and dirt is dirt."""
    return np.stack([rgb[..., 0], np.minimum(rgb[..., 1], rgb[..., 0] * 0.97), rgb[..., 2]], -1)


def periodic(a):
    """A scan tile with the COHERENT step across its wrap taken out (Moisan's periodic + smooth decomposition, per
    channel, on the edge mismatch smoothed along the edge): ambientCG's tiles wrap with a low-frequency step on some
    sets (Rock058: border step 1.27 x the inside step at 256 texels - S7). The texel-to-texel steps across the wrap are
    left as they are - the full decomposition, which takes them out too, left a SMOOTHER line than the texture on every
    block edge (S7 0.51-0.57, offline round 28)."""
    if a.ndim == 3:
        return np.stack([periodic(a[..., c]) for c in range(a.shape[-1])], -1)
    h, w = a.shape
    sig = h / 64.0                                     # ~4 texels at 256: what the eye reads as a line, not grain

    def along(x):                                      # circular Gaussian along the edge
        f = np.fft.fftfreq(x.shape[0])
        return np.real(np.fft.ifft(np.fft.fft(x) * np.exp(-2 * (np.pi * sig * f) ** 2)))
    v = np.zeros_like(a)
    dv, du = along(a[-1, :] - a[0, :]), along(a[:, -1] - a[:, 0])
    v[0, :] += dv
    v[-1, :] -= dv
    v[:, 0] += du
    v[:, -1] -= du
    q = np.arange(h).reshape(-1, 1)
    r = np.arange(w).reshape(1, -1)
    den = 2 * np.cos(2 * np.pi * q / h) + 2 * np.cos(2 * np.pi * r / w) - 4
    den[0, 0] = 1.0
    s = np.fft.fft2(v) / den
    s[0, 0] = 0.0
    return a - np.real(np.fft.ifft2(s))


def scan_set(asset, n, scale=1.0):
    """dict(alb=colour, H=height, nxy=(nx, ny)) of a scan at n texels, every map's wrap healed (periodic). scale > 1
    shows the scan that much larger (the block a 1/scale part of the tile), still as a seamless tile (enlarged)."""
    m = int(round(n * scale))
    maps = [scan_map(asset, "Color", m), scan_map(asset, "Displacement", m), *scan_map(asset, "NormalGL", m)]
    if m != n:
        maps = enlarged(maps, n)
    alb, H, nx, ny = maps
    return dict(alb=np.clip(periodic(alb), 0, 1), H=periodic(H), nxy=(periodic(nx), periodic(ny)))


def enlarged(maps, n):
    """An n x n seamless tile cut from larger seamless maps (period m > n), all maps alike: the n x n corner, rolled
    half a tile so its four cut edges meet in a cross through the middle (the tile's own edges are then the corner's
    insides - continuous); the cross is covered by the same maps from another place (half a period on), inside a
    band 6-12% of the block wide each side of the cross that wanders, feathered over ~3 texels. Round 28: Grass004
    at one tile per block put its blades at ~1 texel - speckle at 3.5 blocks (offline close-up preview)."""
    m = maps[0].shape[0]
    h = n // 2
    F = Field(n, 2771)
    w = np.clip(0.09 + 0.025 * F.noise(1, 5, 0.6), 0.06, 0.12)
    du = np.abs(F.u - 0.5)
    dv = np.abs(F.v - 0.5)
    w2 = np.clip(0.09 + 0.025 * F.noise(1, 5, 0.6), 0.06, 0.12)
    mask = np.maximum(1 - smoothstep(w - 1.5 / n, w + 1.5 / n, du), 1 - smoothstep(w2 - 1.5 / n, w2 + 1.5 / n, dv))
    out = []
    for a in maps:
        corner = np.roll(a[:n, :n], (h, h), (0, 1))
        other = np.roll(a, (m // 2, m // 2), (0, 1))[:n, :n]
        w3 = mask if a.ndim == 2 else mask[..., None]
        out.append(corner * (1 - w3) + other * w3)
    return out


# variant i's inside: the tile turned k quarters, mirrored first or not, and rolled half a block or not (8 of the 16
# such copies; a multiple of 8 texels, so the fit's mip statistics stay the same)
VARIANT_TRANSFORMS = [None, (1, False, True), (2, False, False), (3, False, True), (0, True, True), (2, True, False)]


def turned(scalars, vec, k, mirror=False, roll=False):
    """Maps turned k quarter turns (np.rot90: counter-clockwise as seen), mirrored (left-right) first and rolled half
    a tile: every scalar map (2-D or with channels) as it is, the vector pair (x, y) turned with them (a quarter
    turn: (x, y) -> (y, -x), y down the texture; a mirror: x -> -x)."""
    nx, ny = vec
    if roll:
        h = nx.shape[0] // 2
        scalars = [np.roll(a, (h, h), (0, 1)) for a in scalars]
        nx, ny = np.roll(nx, (h, h), (0, 1)), np.roll(ny, (h, h), (0, 1))
    if mirror:
        scalars = [a[:, ::-1] for a in scalars]
        nx, ny = -nx[:, ::-1], ny[:, ::-1]
    for _ in range(k % 4):
        nx, ny = ny, -nx
    return [np.rot90(a, k) for a in scalars], (np.rot90(nx, k), np.rot90(ny, k))


VARIANT_MARGIN = (0.075, 0.03, 0.012, 0.04, 0.11)   # the band's width: base, slow wander, fine wander, min, max


def variant_mask(n, i, feather=3.0, margin=VARIANT_MARGIN):
    """Where variant i shows its own copy: 1 inside an irregular blob, 0 in a thin border band (4-11% of the block
    from each edge, its width wandering), soft over `feather` texels. Variant 0 is the tile itself (all 0). The band
    is thin so the share of every block that is the same tile stays small (S4: the repeat at one block)."""
    if i == 0:
        return np.zeros((n, n))
    F = Field(n, 2800 + 31 * i)                      # its own seed: the same blob for every scan material
    edge = np.minimum(np.minimum(F.u, 1 - F.u), np.minimum(F.v, 1 - F.v))
    m0, slow, fine, lo, hi = margin
    margin = np.clip(m0 + slow * F.noise(1, 6, 0.6) + fine * F.noise(6, 20, 0.3), lo, hi)
    return smoothstep(margin, margin + feather / n, edge)


def blend_maps(alb, n_img, s_img, other, m):
    """The tile's FINAL maps (albedo, LabPBR _n and _s) with `other` = (alb2, (nx2, ny2), ao2, h2, s2) shown where
    mask m is 1 and the tile itself where it is 0 - bit for bit in the band, so two neighbours always meet as the
    seamless tile meets itself (colour, normals, AO, height and smoothness alike)."""
    nf = n_img.astype(np.float64) / 255.0
    sf = s_img.astype(np.float64)
    vec = (nf[..., 0] * 2 - 1, nf[..., 1] * 2 - 1)
    alb2, (nx2, ny2), ao2, h2, s2 = other

    def mix(a, b):
        w = m if a.ndim == 2 else m[..., None]
        return a * (1 - w) + b * w
    nx, ny = mix(vec[0], nx2), mix(vec[1], ny2)
    out_n = materials.encode_normal(nx, ny, mix(nf[..., 2], ao2), (mix(nf[..., 3], h2) * 255 - 1) / 254)
    keep = m <= 0
    out_n[keep] = n_img[keep]                          # the band exactly as the tile (no re-encoding round-off)
    out_s = np.clip(np.rint(mix(sf, s2)), 0, 255).astype(np.uint8)
    return mix(alb, alb2), out_n, out_s


# r39 (eyes 41044148818: "lighter rectangles" and straight bands on the lawn): every variant shows the SAME tile in
# its border band, so whatever clump pattern lies in that ring repeats on every block as a lattice. The grass top
# flattens its clump field inside the shared ring (clover_lawn: variant_frame), blends its blobs over 16 texels
# instead of 3, and turns its copies without the half-block roll (a rolled copy would carry its calm frame through
# the block's centre as a cross).
VARIANT_FEATHER = {"grass_block_top": 8.0}
# the grass top's band is thin (2.5-5.5% of the block: the seam only needs the mips to match, 8-12 texels at 256) so
# the calm ring that every block shares stays a couple of screen pixels wide at the RTS zoom
VARIANT_MARGIN_OF = {"grass_block_top": (0.04, 0.012, 0.006, 0.025, 0.055)}
VARIANT_TRANSFORMS_UNROLLED = [None, (1, False, False), (2, False, False), (3, False, False), (0, True, False),
                               (2, True, False)]
VARIANT_TRANSFORMS_OF = {"grass_block_top": VARIANT_TRANSFORMS_UNROLLED}


def variant_frame(n, feather, margin=VARIANT_MARGIN):
    """Where ANY variant shows the tile itself (the union of the border bands, 1 in the band, 0 inside every blob):
    a periodic material that flattens its large features here hides the ring that every block shares."""
    frame = np.zeros((n, n))
    for i in range(1, 6):
        frame = np.maximum(frame, 1 - variant_mask(n, i, feather, margin))
    return frame


def variant_maps(alb, n_img, s_img, i, feather=3.0, transforms=None, margin=VARIANT_MARGIN):
    """Variant i of a scan material's FINAL maps: its own transformed copy inside variant_mask's blob, the tile
    itself in the border band (blend_maps)."""
    if i == 0:
        return alb, n_img, s_img
    n = alb.shape[0]
    nf = n_img.astype(np.float64) / 255.0
    vec = (nf[..., 0] * 2 - 1, nf[..., 1] * 2 - 1)
    (alb2, ao2, h2, s2), nxy2 = turned([alb, nf[..., 2], nf[..., 3], s_img.astype(np.float64)], vec,
                                       *(transforms or VARIANT_TRANSFORMS)[i])
    return blend_maps(alb, n_img, s_img, (alb2, nxy2, ao2, h2, s2), variant_mask(n, i, feather, margin))


def finished(name, seed, n):
    """A procedural texture's finished maps on a given seed: dict(alb, H, n_img, s_img, k) - what make() computes
    before it writes (the recipe, then the LabPBR maps). Round 33: the procedural variants' building block."""
    t = targets_for(name)
    F = Field(n, seed)
    r = RECIPES[name](F, n, t)
    for key in ("f0", "smooth", "emit"):
        if key in r:
            t = dict(t, **{key + "_map": r[key]})
    n_img, s_img, k = maps(F, r["H"], t, r.get("alpha"), r.get("nxy"))
    return dict(alb=r["alb"], H=r["H"], n_img=n_img, s_img=s_img, k=k)


def proc_variant(base, i):
    """Round 33: variant i (1, 2, ...) of a procedural block texture - the base's finished maps on the variant's own
    seed inside variant_mask's blob, the base tile in the border band (so a wall never shows a cut stone or board at
    a block edge). Both are fitted to the same targets: one mean value and saturation across the variants."""
    name = "%s_v%d" % (base, i + 1)

    def r(F, n, t):
        b = finished(base, SEEDS[base], n)
        v = finished(base, SEEDS[name], n)
        vf = v["n_img"].astype(np.float64) / 255.0
        other = (v["alb"], (vf[..., 0] * 2 - 1, vf[..., 1] * 2 - 1), vf[..., 2], vf[..., 3],
                 v["s_img"].astype(np.float64))
        m = variant_mask(n, i, PROC_FEATHER.get(base, 3.0))
        alb, n_img, s_img = blend_maps(b["alb"], b["n_img"], b["s_img"], other, m)
        H = b["H"] * (1 - m) + v["H"] * m
        return dict(H=H, alb=alb, maps=(n_img, s_img), k=v["k"])
    return r


def m_scan_stone(F, n):
    d = scan_set(SCAN_STONE, n)
    d["alb"] = flatten(F, d["alb"], 3.0, keep=0.35)
    return d


def m_scan_dirt(F, n):
    d = scan_set(SCAN_SOIL, n, scale=1.5)                # crumbs and stones half as large again (round 28b, S2)
    d["alb"] = no_green(flatten(F, d["alb"], 3.0, keep=0.4))
    return d


def m_scan_grass_top(F, n):
    d = scan_set(SCAN_GRASS, n, scale=3.0)              # blades ~4 texels at 256: soft strokes, not speckle (enlarged)
    d["alb"] = neutral(flatten(F, d["alb"], 2.0, keep=0.3))
    return d


def mid_band(F, alb, t):
    """Key `mid` (round 28): the scan's structure 3-12 texels across (blades, crumbs, grain) raised by that factor
    AFTER `soften` - the texel-to-texel steps that read as pepper in the plain game (S1) stay soft while the forms a
    few texels wide keep their contrast (S2), so a calm texture is not a blurred one. No `mid`, no change."""
    k = t.get("mid")
    if not k:
        return alb
    L = alb @ LUMA
    band = F.blur(L, 2.0) - F.blur(L, 7.0)
    return np.clip(alb * (1 + k * band / np.maximum(L, 1e-3))[..., None], 0, 1)


def tone_limits(alb, t):
    """A scan's darkest and brightest texels eased in (keys `toe` / `shoulder`, luma 0..1): below the toe a texel's
    distance to it is cut to 35%, above the shoulder likewise - a photo's deep crevices and sunlit blade tips land
    inside the pack's albedo range (S3: 1st..99th percentile luma 55..220) without flattening the middle tones."""
    L = alb @ LUMA
    L2 = L.copy()
    if "toe" in t:
        L2 = np.where(L2 < t["toe"], t["toe"] - (t["toe"] - L2) * 0.35, L2)
    if "shoulder" in t:
        L2 = np.where(L2 > t["shoulder"], t["shoulder"] + (L2 - t["shoulder"]) * 0.35, L2)
    return np.clip(alb * (L2 / np.maximum(L, 1e-4))[..., None], 0, 1)


def m_scan_basalt(F, n, end=False):
    """Basalt (round 28): Rock035, near black in the photo, lifted to basalt's grey by the fit; the end of the column
    is the same rock turned a quarter, so its grain does not run on from the side's."""
    d = scan_set(SCAN_BASALT, n)
    d["alb"] = flatten(F, d["alb"], 3.0, keep=0.35)
    if end:
        (alb, H), nxy = turned([d["alb"], d["H"]], d["nxy"], 1)
        d = dict(alb=alb, H=H, nxy=nxy)
    return d


def clover_lawn(F, n):
    """Round 34 (lovish's reference: a CLOVER LAWN seen from above): a carpet of small round clover leaflets - each
    plant three leaflets round a stem point, drawn back to front so the higher plants overlap the lower - with short
    blades of grass between and over them, the plants in clumps (mounds of turf) with deep self-shadow in the gaps
    between the clumps, a few tiny flowers. Returns (H, alb (grey with a hue spread: the biome colour tints it),
    flower mask). Everything periodic. Leaflets ~5-7 texels across at 256 (1/40 block): they do not resolve from the
    RTS camera, where the clumps and their shadows carry the look (2-3 value bands); up close each leaflet is lit."""
    rng = F.rng
    yy, xx = np.mgrid[0:n, 0:n]
    H = np.zeros((n, n))
    shade = np.ones((n, n)) * 0.55          # the gaps: the dark floor of the lawn
    warm = np.zeros((n, n))                 # hue: +1 warm (yellow), -1 cool (blue-green)
    mound = F.fbm(3, 3, 0.5) + 0.5 * F.noise(6, 14, 0.5)     # the clumps: where the turf heaps up (1/10-1/4 block)
    mound = norm01(mound)
    # r39: no clumps in the ring every variant shares (variant_frame) - the ring repeats on every block, and a clump
    # in it drew a lattice of lighter rectangles in game; the blobs' turned copies carry the clumps instead
    frame = F.blur(variant_frame(n, VARIANT_FEATHER["grass_block_top"], VARIANT_MARGIN_OF["grass_block_top"]), n / 64)
    mound = 0.5 + (mound - 0.5) * (1 - frame)
    floor = -0.30 * (1 - mound)             # the gaps lie lower
    H += 0.45 * mound                       # r39: the clumps carry the relief (0.30 -> 0.45), the leaf steps less
    count = int(0.0030 * n * n)             # ~200 plants per tile at 256
    leaf_r = n / 38.0
    # each plant: a level (its height in the canopy), a tone band (3 bands) and a hue lean
    px, py = rng.random(count) * n, rng.random(count) * n
    mi = mound[np.minimum((py).astype(int), n - 1), np.minimum((px).astype(int), n - 1)]
    level = 0.25 * mi + 0.20 * rng.random(count) * (0.4 + 0.6 * mi)   # r39: shallower canopy steps (0.40 / 0.35)
    band = rng.integers(3, size=count)
    tone = np.array([0.90, 1.0, 1.10])[band] * (0.97 + 0.06 * rng.random(count))   # r39: calmer leaf-to-leaf
    lean = rng.standard_normal(count)
    rot = rng.random(count) * 2 * math.pi
    tilt_a = rng.random(count) * 2 * math.pi          # r36: each plant leans toward its own light direction
    tx, ty = np.cos(tilt_a), np.sin(tilt_a)
    scale = 0.8 + 0.4 * rng.random(count)
    order = np.argsort(level)               # back to front
    r0 = int(leaf_r * 1.6) + 3
    for i in order:
        cx, cy, lv = px[i], py[i], level[i]
        for j in range(3):
            a = rot[i] + j * 2 * math.pi / 3
            lx, ly = cx + math.cos(a) * leaf_r * 0.95 * scale[i], cy + math.sin(a) * leaf_r * 0.95 * scale[i]
            R = leaf_r * scale[i]
            x0, y0 = int(math.floor(lx)) - r0, int(math.floor(ly)) - r0
            win_y = (np.arange(y0, y0 + 2 * r0 + 1)) % n
            win_x = (np.arange(x0, x0 + 2 * r0 + 1)) % n
            dy = (np.arange(y0, y0 + 2 * r0 + 1) - ly)[:, None]
            dx = (np.arange(x0, x0 + 2 * r0 + 1) - lx)[None, :]
            # a heart-shaped leaflet: a disc with a notch at its outer edge (away from the stem)
            ox, oy = math.cos(a), math.sin(a)
            along = (dx * ox + dy * oy) / R
            across = (-dx * oy + dy * ox) / R
            rr = np.sqrt((along - 0.1) ** 2 + across ** 2)
            notch = smoothstep(0.55, 0.9, along) * (1 - smoothstep(0.0, 0.35, np.abs(across)))
            inside = (rr < 1.0) & (notch < 0.5)
            dome = np.clip(1 - rr * rr, 0, 1) * inside
            h = lv + 0.07 * np.sqrt(dome)           # a cushion: steep only at its rim (r39: 0.12 -> 0.07)
            sub = H[np.ix_(win_y, win_x)]
            above = inside & (h > sub)
            H[np.ix_(win_y, win_x)] = np.where(above, h, sub)
            # the leaflet's shade: its tone band, a lit centre, a darker rim (self-shadow of the leaf above)
            rim = 1 - 0.30 * (1 - smoothstep(0.0, 0.30, dome))        # a dark rim: the leaf's edge over its neighbour
            vein = 1 + 0.10 * (1 - smoothstep(0.0, 0.12, np.abs(across))) * (along > -0.2)
            tiltl = (dx * tx[i] + dy * ty[i]) / R                       # r36: the leaf's own tilt (one side lit)
            lit = tone[i] * (0.78 + 0.26 * dome) * (1 + 0.14 * tiltl) * rim * vein
            s_sub = shade[np.ix_(win_y, win_x)]
            shade[np.ix_(win_y, win_x)] = np.where(above, lit, s_sub)
            w_sub = warm[np.ix_(win_y, win_x)]
            warm[np.ix_(win_y, win_x)] = np.where(above, lean[i] * 0.6 + 0.8 * (tone[i] - 1.0) * 4, w_sub)
    H = H + floor
    # blades of grass between and over the clover: thin, short, standing proud; their colour a touch lighter
    blades = F.blur(grass_blades(F, int(1.6 * n), n / 22, max(1, n // 128)), 0.7)   # soft-edged (no black steps)
    bl = blades > 0.25
    H = np.where(bl, H + 0.10 * blades, H)              # the blades ride on the canopy (a tall ridge drew black edges)
    shade = np.where(bl, 0.95 + 0.30 * blades, shade)
    warm = np.where(bl, warm + 0.8, warm)
    # the clumps: their crowns catch the light, the gaps between them lie in shadow (this is what the RTS camera
    # sees - 3-8 px wide at 32 px per block); plus the leaves' own self-shadow where the canopy drops
    shade = shade * (0.70 + 0.55 * mound)                # r39: the clumps carry more of the spread (eyes 41044148818:
    # the lawn read as single-frequency speckle, std 0.10 vs the benchmark's 0.06, with no macro variation)
    shade = shade * (1 - 0.09 * frame)                   # the ring, flatter and unshadowed, lit ~2.5% brighter than
    # the clumps (measured on the finished maps): 9% here is 2.5% after the fit scales the spread to its target
    rel = H - F.blur(H, n / 40)
    shadow = smoothstep(0.0, 0.25, -rel)
    shade = shade * (1 - 0.40 * shadow)
    # a few tiny flowers: 4-5 petals, pale, sparse
    flowers = np.zeros((n, n))
    for _ in range(int(0.00018 * n * n) + 2):
        fx, fy = rng.random() * n, rng.random() * n
        pr = n / 80.0
        k = 4 + int(rng.random() < 0.5)
        for j in range(k):
            a = j * 2 * math.pi / k + rng.random() * 0.3
            cxp, cyp = fx + math.cos(a) * pr * 1.1, fy + math.sin(a) * pr * 1.1
            d2 = ((xx - cxp + n / 2) % n - n / 2) ** 2 + ((yy - cyp + n / 2) % n - n / 2) ** 2
            flowers = np.maximum(flowers, np.clip(1 - d2 / (pr * pr), 0, 1))
    fl = flowers > 0.15
    H = np.where(fl, np.maximum(H, 0.9), H)
    shade = np.where(fl, 1.45, shade)
    warm = np.where(fl, 1.5, warm)
    hue = np.clip(warm, -1.5, 1.5)
    alb = np.clip(np.stack([shade * (1 + 0.05 * hue), shade * (1 + 0.01 * hue), shade * (1 - 0.06 * hue)], -1) * 0.55, 0, 1)
    alb = np.clip(alb * np.array([1.14, 1.0, 0.80]), 0, 1)   # r39: the mean ~6 degrees toward yellow-green (the fit
    # scales the cast down with the leaf-to-leaf spread when it sets sat, so it is pushed hard here)
    return H, alb, flowers


def m_clover_top(F, n):
    """The grass block's top, round 34: the procedural clover lawn in place of the Grass004 scan (its own normals
    from the height, so nxy is None). The variants (SCAN_VARIANT_OF) cut their blobs from it as before."""
    H, alb, _ = clover_lawn(F, n)
    return dict(alb=alb, H=H, nxy=None)


SCAN_BASES = {"stone": m_scan_stone, "dirt": m_scan_dirt, "grass_block_top": m_clover_top}   # r34: clover lawn
# (basalt left the scans in r31: m_columnar)


def scan_textures(base, i, n):
    """Variant i of a scanned material, finished: the tile is fitted to the base's targets, softened, its extremes
    eased in (tone_limits) and given its LabPBR maps (its own normals at one scale for every variant); variant_maps
    then cuts the variant from those finished maps. Returns dict(alb, H, maps=(n_img, s_img), k)."""
    t = targets_for(base)
    F = Field(n, SEEDS[base])
    d = SCAN_BASES[base](F, n)
    alb = tone_limits(mid_band(F, soften(F, fit_albedo(d["alb"], t), t), t), t)
    n_img, s_img, k = maps(F, d["H"], t, None, d.get("nxy"))
    alb, n_img, s_img = variant_maps(alb, n_img, s_img, i, feather=VARIANT_FEATHER.get(base, 3.0),
                                     transforms=VARIANT_TRANSFORMS_OF.get(base),
                                     margin=VARIANT_MARGIN_OF.get(base, VARIANT_MARGIN))
    return dict(alb=alb, H=d["H"], maps=(n_img, s_img), k=k)


def _scan(base, i=0):
    return lambda F, n, t: scan_textures(base, i, n)


def turf(n, i):
    """The grass block's side, round 28: the turf seen edge-on - a band of grass ~1/8-1/5 of the block deep whose
    blades run UP and DOWN (lovish: grass on the side must not look like it grows sideways; a top-down grass photo
    on the side reads as blades lying sideways), its lower edge ragged with single blades hanging over the soil
    (sod folds over a cut edge), tips pointing down. Returns (alpha 0/1, height, grey shade ~1 of the blades): the
    shade is the grass scan's own luma stretched 6x along v, so every streak is a real blade's tone run vertical.
    Variant i keeps the band and the hanging blades of the outer 12% of the block on each side (shared, so the turf
    runs on from block to block) and draws its own in between."""
    S = Field(n, SEEDS["grass_block_side_overlay"])
    V = S if i == 0 else Field(n, SEEDS["grass_block_side_overlay_v%d" % (i + 1)])
    inner = smoothstep(0.06, 0.14, np.minimum(S.u, 1 - S.u))[0:1, :]       # 0 near the block's left / right edge
    # the band and its hanging blades end within the top quarter of the side (S5: no green below it)
    depth = 0.13 + 0.025 * S.noise(1, 6, 0.6)[0:1, :] + 0.012 * S.noise(6, 24, 0.3)[0:1, :]
    if i:
        depth = depth + inner * (0.02 * V.noise(1, 6, 0.6)[0:1, :] + 0.01 * V.noise(6, 24, 0.3)[0:1, :])
    depth = np.clip(depth, 0.08, 0.18)
    alpha = (S.v < depth).astype(np.float64)
    blades = np.zeros((n, n))

    def strands(rng, keep):
        # r34: a THICK turf lip whose blades overhang the edge and hang down the dirt in a ragged fringe - more of
        # them, longer (to 0.08 block), tapered; a few clover leaflets peeking over the lip's edge
        for _ in range(int(1.4 * n)):                 # hanging blades: tapered, nearly vertical, from the band's edge
            x, r1, r2, r3, r4 = rng.random(5)
            if not keep(x):
                continue
            x *= n
            top = depth[0, int(x) % n] * n - r1 * 0.03 * n
            ln = n * (0.02 + 0.06 * r2 ** 1.4)
            drift = (r3 - 0.5) * 0.3                  # a slight lean, never sideways
            wid = 1.0 + 1.4 * r4 * n / 256
            steps = int(ln)
            for st in range(steps):
                t = st / max(1, steps - 1)
                w = wid * (1 - 0.8 * t)               # tapers to a tip
                y = int(top + st)
                if y >= n:
                    break
                xc = x + drift * st
                for dx in range(int(math.floor(xc - w / 2)), int(math.ceil(xc + w / 2))):
                    blades[y, dx % n] = max(blades[y, dx % n], 1.0 - 0.5 * t)
        for _ in range(int(0.12 * n)):                # clover leaflets over the lip's edge: small discs
            x, r1, r2 = rng.random(3)
            if not keep(x):
                continue
            cx = x * n
            cy = depth[0, int(cx) % n] * n + (r1 - 0.2) * 0.02 * n
            rad = n / 60.0 * (0.8 + 0.5 * r2)
            y0, y1 = int(cy - rad) - 1, int(cy + rad) + 2
            for y in range(max(0, y0), min(n, y1)):
                for dx in range(int(cx - rad) - 1, int(cx + rad) + 2):
                    d2 = ((dx - cx) ** 2 + (y - cy) ** 2) / (rad * rad)
                    if d2 < 1.0:
                        blades[y, dx % n] = max(blades[y, dx % n], 0.9 - 0.3 * d2)

    def edge_zone(x):
        return x < 0.12 or x > 0.88
    strands(np.random.default_rng(SEEDS["grass_block_side_overlay"]), (lambda x: True) if i == 0 else edge_zone)
    if i:
        strands(np.random.default_rng(SEEDS["grass_block_side_overlay_v%d" % (i + 1)]), lambda x: not edge_zone(x))
    alpha = np.maximum(alpha, (blades > 0).astype(np.float64))
    g = scan_map(SCAN_GRASS, "Color", n) @ LUMA
    yy = (np.arange(n) // 6)[:, None] % n             # 6x along v: blade tones become vertical streaks
    streak = g[yy, np.arange(n)[None, :]]
    streak = streak / streak.mean()
    below = np.clip((S.v - depth) / 0.12, 0, 1)        # the hanging blades darken toward their tips (in shadow)
    # r34: the lip - its crest (top fifth of the band) catches the light, its underside (bottom third) is in shadow
    f = np.clip(S.v / np.maximum(depth, 1e-3), 0, 1)     # 1 below the band too: the hanging blades continue
    # from the lip's underside (a reset to the crest there drew an up-facing step - a black line under the lip)
    lip = 1.0 + 0.10 * (1 - smoothstep(0.0, 0.25, f)) - 0.22 * smoothstep(0.5, 1.0, f)
    shade = streak * lip * (1 - 0.25 * below)
    # the lip's height: rounded - full at the crest, falling toward its lower edge (the normal shows the roll)
    roll = 1 - 0.22 * smoothstep(0.3, 1.0, f) ** 2
    rib = S.blur(np.clip(streak - 1, -1, 1), 1.2)        # the blades' ribs, smoothed (texel steps blew the normal strength up)
    hang = blades * smoothstep(0.0, 0.02, S.v - depth)  # the blades' relief only below the band, eased in from
    H = np.where(alpha > 0.5, (0.75 + 0.10 * rib) * roll + 0.04 * hang, 0.0)   # the root (a step there drew a line)
    # the cut-away texels take the turf's own nearby height (a 0 there made a cliff at the alpha edge whose normals
    # drew a black line under the lip in game and in the lit preview, r34)
    fill = S.blur(H * alpha, 3.0) / np.maximum(S.blur(alpha, 3.0), 1e-3)
    H = np.where(alpha > 0.5, H, fill)
    return alpha, H, shade


def _scan_grass_side(i=0):
    """The side's soil IS the dirt texture (variant i, finished - colour, normals, AO, height, smoothness), so a dirt
    block under a grass block continues it with no seam; the top of the soil is a darker humus band (colour only),
    and under the turf a dark green-brown base shows only through the turf's gaps at a distance (mipmaps)."""
    def r(F, n, t):
        soil = scan_textures("dirt", i, n)
        n_img, s_img = soil["maps"][0].copy(), soil["maps"][1].copy()
        alpha, Ht, _ = turf(n, i)
        m = alpha > 0.5
        humus = 1 - 0.22 * (1 - smoothstep(0.10, 0.50, F.v))
        # r34: thin roots wandering down from the turf (fading by mid-block) and a few small pale pebbles
        rx = F.noise(3, 8, 0.6, sx=1.0, sy=4.0)                      # long along v: root lines
        root = (1 - smoothstep(0.0, 0.05, np.abs(rx))) * (1 - smoothstep(0.15, 0.55, F.v)) * smoothstep(0.12, 0.2, F.v)
        root *= smoothstep(0.2, 0.7, norm01(F.noise(1, 4, 0.5)))      # only some columns carry a root
        pf1, pf2, pidx, pk = F.voronoi(60, 1.0)
        peb = ((F.rng.random(pk) < 0.2)[pidx] & (pf1 < 0.014) & (F.v > 0.25)).astype(np.float64)
        pebble = peb * np.clip(1 - pf1 / 0.014, 0, 1)
        alb = tone_limits(soil["alb"] * humus[..., None] * (1 - 0.35 * root)[..., None], t)
        alb = alb * (1 - pebble[..., None]) + np.array([0.52, 0.50, 0.46]) * pebble[..., None]
        alb = np.where(m[..., None], np.array([0.27, 0.29, 0.20]) * (0.8 + 0.4 * Ht[..., None]), alb)
        n_img[m] = (128, 128, 255, 255)                 # under the turf: flat, unshaded, at the top of the relief
        H = np.where(m, 1.0, soil["H"] + 0.15 * pebble - 0.05 * root)
        return dict(H=H, alb=alb, maps=(n_img, s_img), k=soil["k"])
    return r


def _scan_grass_overlay(i=0):
    def r(F, n, t):
        alpha, H, shade = turf(n, i)
        grey = np.repeat(shade[..., None], 3, -1) * 0.5
        m = alpha > 0.5
        fitted = fit_albedo(grey, t, m)
        # the cut-away texels take the turf's mean grey: the mipmaps average them in, and a stray value there would
        # draw a dark or pale rim round the band from the RTS camera
        alb = np.where(m[..., None], fitted, fitted[m].mean(0))
        side = _scan_grass_side(i)(F, n, TARGETS["grass_block_side"])
        return dict(H=H, alb=alb, alpha=alpha, under=side["alb"])
    return r


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


def scan_normals(nxy, tilt_deg, mask=None):
    """Round 28: a photo scan's own normals with their slopes scaled by k, k solved like solve_strength's (the tilt
    at CAL_MIP on target, the full-size tilt capped at TILT_CAP x the target). Returns (k, (nx, ny, nz))."""
    m = np.ones(nxy[0].shape, bool) if mask is None else mask
    nx0, ny0 = nxy
    nz0 = np.sqrt(np.clip(1 - nx0 * nx0 - ny0 * ny0, 0.05, 1))
    sx, sy = nx0 / nz0, ny0 / nz0

    def at(k):
        ln = np.sqrt(1 + k * k * (sx * sx + sy * sy))
        return k * sx / ln, k * sy / ln, 1 / ln

    def at_mip(k):
        x, y, _ = at(k)
        return mip_tilt(x, y, m)

    def full(k):
        return float(np.degrees(np.arccos(np.clip(at(k)[2][m], -1, 1))).mean())
    k = min(_bisect(at_mip, tilt_deg), _bisect(full, tilt_deg * TILT_CAP))
    return k, at(k)


def maps(F, H, t, alpha=None, nxy=None):
    # visible texels: alpha above 0.02 - a translucent pane (stained glass, alpha ~0.4) is surface, not a hole
    vis = None if alpha is None else alpha > 0.02
    H = norm01(H)
    lf = F.blur(H, F.n / 48)                              # ~1/48 block: relief the mipmaps keep
    # t["lf"] (round 18): a material may take less of that boost - on stone it raised the grain into lumps that
    # Complementary lit as crumpled foil (eyes structure run 39536435197)
    H = norm01(H + t.get("lf", LF_GAIN) * (lf - lf.mean()))
    if nxy is None:
        k = solve_strength(H, t["tilt"], vis)
        nx, ny, nz = normals(H, k)
    else:                                                 # a photo scan brings its own normals (round 28)
        k, (nx, ny, nz) = scan_normals(nxy, t["tilt"], vis)
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


ROTATED = {"sand", "gravel"}   # turned at random per block (round 28: not stone, dirt or grass - SCAN section)
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
# alpha 0.71 -> 0.85 (environment palette, Oct 29): at 0.71 the sand bed was 29% of every water pixel and lifted the
# river to the lawn's value (eyes r54: water 0.42 vs lawn 0.32) - it read as a painted strip, not water lying low
WATER = dict(luma=0.68, std=0.064, alpha=0.85, frames=32, frametime=2, tilt=10.0, smooth=230, f0=5)
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
           "glass"} | {c + "_stained_glass" for c in STAINED} | {c + "_stained_glass" for c in DYES} | {"tinted_glass"}


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
    # round 28 (the SCAN section): stone, dirt and grass pick one of SCAN_VARIANTS textures per block and are never
    # turned or mirrored - their variants share a seamless border band, a turn or a mirror would break it
    def model(name, parent, textures):
        with open(os.path.join(MODEL_DIR, name + ".json"), "w") as f:
            json.dump({"parent": parent, "textures": {k: "minecraft:block/" + v for k, v in textures.items()}}, f,
                      indent=1)
    grass, stone, dirt, basalt = ["grass_block"], ["stone"], ["dirt"], ["basalt"]
    for k in range(2, SCAN_VARIANTS + 1):
        model("grass_block_v%d" % k, "minecraft:block/grass_block", {
            "top": "grass_block_top_v%d" % k, "side": "grass_block_side_v%d" % k,
            "overlay": "grass_block_side_overlay_v%d" % k, "particle": "dirt", "bottom": "dirt"})
        model("stone_v%d" % k, "minecraft:block/cube_all", {"all": "stone_v%d" % k})
        model("dirt_v%d" % k, "minecraft:block/cube_all", {"all": "dirt_v%d" % k})
        grass.append("grass_block_v%d" % k)
        stone.append("stone_v%d" % k)
        dirt.append("dirt_v%d" % k)
        model("basalt_v%d" % k, "minecraft:block/cube_column",
              {"end": "basalt_top_v%d" % k, "side": "basalt_side_v%d" % k})
        basalt.append("basalt_v%d" % k)

    def blockstate(name, variants):
        with open(os.path.join(BLOCKSTATE_DIR, name + ".json"), "w") as f:
            json.dump({"variants": variants}, f, indent=1)
    pick = lambda ms: [{"model": "minecraft:block/" + m} for m in ms]          # noqa: E731
    blockstate("grass_block", {"snowy=false": pick(grass), "snowy=true": {"model": "minecraft:block/grass_block_snow"}})
    blockstate("stone", {"": pick(stone)})
    blockstate("dirt", {"": pick(dirt)})
    # basalt: vanilla's axis turns of the column, the same turn for every variant (the shared band stays shared)
    turn = {"axis=x": {"x": 90, "y": 90}, "axis=y": {}, "axis=z": {"x": 90}}
    blockstate("basalt", {k: [dict({"model": "minecraft:block/" + m}, **r) for m in basalt] for k, r in turn.items()})
    # round 33: the procedural variants - a model per variant on its textures, the blockstate a weighted pick (equal
    # weights: the tiling-breaker; the base is the texture the block's stairs / slabs / walls draw, through vanilla's
    # own models, which this pack does not touch). Full cubes only: no turn, no uvlock.
    for block, (parent, slots) in PROC_VARIANT_BLOCKS.items():
        picks = [{"model": "minecraft:block/" + block, "weight": 1}]
        for k in range(2, PROC_VARIANTS + 1):
            model("%s_v%d" % (block, k), "minecraft:block/" + parent, {s: "%s_v%d" % (b, k) for s, b in slots.items()})
            picks.append({"model": "minecraft:block/%s_v%d" % (block, k), "weight": 1})
        blockstate(block, {"": picks})
    # round 35: the giant sheets (GIANT) - Athena's loader in the blockstate, the sprites 1..N*N, the vanilla model as
    # the fallback (no turns: the sheet's position does the variation)
    for name, (_recipe, blocks, _tilt_k) in GIANT.items():
        ctm = {"particle": "minecraft:block/" + name}
        for i in range(1, blocks * blocks + 1):
            ctm[str(i)] = "minecraft:block/%s_g%d" % (name, i)
        with open(os.path.join(BLOCKSTATE_DIR, name + ".json"), "w") as f:
            json.dump({"athena:loader": "athena:giant", "width": blocks, "height": blocks, "ctm_textures": ctm,
                       "variants": {"": {"model": "minecraft:block/" + name}}}, f, indent=1)
    for name in ROTATED_BLOCKSTATES:
        models = [name] + [v for v, b in LEAF_VARIANTS.items() if b == name]
        turns = [dict({"model": "minecraft:block/" + m}, **({"x": x} if x else {}), **({"y": y} if y else {}))
                 for m in models for x in (0, 90, 180, 270) for y in (0, 90, 180, 270)]
        with open(os.path.join(BLOCKSTATE_DIR, name + ".json"), "w") as f:
            json.dump({"variants": {"": turns}}, f, indent=1)


def _d3(r):
    """(H, alb, smooth) -> the parts dict (round 23 metals paint their own smoothness)."""
    H, alb, sm = r
    return dict(H=H, alb=alb, smooth=sm)


def _d4(r):
    H, alb, f0, sm = r
    return dict(H=H, alb=alb, f0=f0, smooth=sm)


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
    H, alb, _ = m_grass_side(F, n)
    return dict(H=H, alb=fit_albedo(alb, t))


def _grass_side_overlay(F, n, t):
    # the overlay's fringe is the side's: rebuilt from the side's own seed (deterministic), so each texture is one
    # independent job; the side's albedo is the preview's backdrop
    side = Field(n, SEEDS["grass_block_side"])
    _, side_alb, fringe = m_grass_side(side, n)
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
        # round 28: photo scans (the SCAN section); their variants are added below the table
        "grass_block_top": _scan("grass_block_top"),
        "grass_block_side": _scan_grass_side(0),
        "grass_block_side_overlay": _scan_grass_overlay(0),
        "dirt": _scan("dirt"), "stone": _scan("stone"),
        "cobblestone": _plain(m_cobble_domed),   # r34: domed cobbles (m_cobblestone kept for reference)
        "mossy_cobblestone": _plain(lambda F, n: m_cobble_domed(F, n, moss=0.85)),
        # r31: columnar basalt (m_columnar) in place of the Rock035 scan - the scan's variants turned the tile, which
        # would lay the ribs sideways; each variant is its own roll on the fixed column edges (COLUMN_EDGES)
        "basalt_side": _plain(lambda F, n: m_columnar(F, n, False, False)),
        "basalt_top": _plain(lambda F, n: m_columnar(F, n, True, False)),
        "sand": _plain(m_sand), "gravel": _plain(m_gravel_r25), "oak_planks": _plain(m_oak_planks_r24),
        "oak_log": _plain(m_oak_bark_r24),
        "oak_log_top": _plain(m_log_top), "stone_bricks": _plain(m_stone_bricks),
        "polished_andesite": _plain(m_polished_andesite), "smooth_stone": _plain(m_smooth_stone),
        "smooth_stone_slab_side": _plain(m_smooth_stone_slab_side), "spruce_planks": _plain(m_spruce_planks),
        "raw_iron_block": _with_f0(m_raw_iron), "barrel_side": _parts(m_barrel_side),
        "barrel_top": _parts(lambda F, n: m_barrel_end(F, n, True)),
        "barrel_bottom": _parts(lambda F, n: m_barrel_end(F, n, False)),
        "gold_block": _parts(lambda F, n: _d3(m_gold_r23(F, n))), "hay_block_side": _plain(m_hay_side), "hay_block_top": _plain(m_hay_top),
        "birch_log": _plain(m_birch_log),
        "birch_log_top": _plain(lambda F, n: m_log_top(F, n, (0.86, 0.76, 0.56), (0.86, 0.85, 0.80))),
        "cut_copper": _parts(lambda F, n: _d4(m_copper(F, n, 0))),
        "oxidized_cut_copper": _parts(lambda F, n: _d4(m_copper(F, n, 3))),
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
        "polished_blackstone": lambda F, n: m_polished(F, n, (0.21, 0.18, 0.23), 0.08, 0.06),   # r39: violet
        "polished_blackstone_bricks": lambda F, n: m_bricks(F, n, 4, 2, (0.21, 0.18, 0.23)),
        "deepslate_bricks": lambda F, n: m_bricks(F, n, 4, 2, (0.30, 0.30, 0.32)),
        # r21: the 4x4 grid turned to grey static at RTS zoom - wider, darker joints so the grid survives the mips
        # r33 (critic: "micro-mesh" - 4 x 4 with wide dark joints): 3 x 3 larger tiles in a running bond, the joints
        # narrower and lighter (a grout line, not a black grid)
        "deepslate_tiles": lambda F, n: m_tiles(F, n, 3, (0.22, 0.22, 0.24), joint=(0.008, 0.022), grout=0.60,
                                                bond=1.0 / 9),
        "deepslate": lambda F, n: m_deepslate(F, n),
        "deepslate_top": lambda F, n: m_deepslate(F, n, top=True),
        # r32 (critic: polished vs rough twins identical at RTS): a faint 2x2 seam, a tighter calmer grain
        # r33 (critic: "reads brown-grey"): cooled to a blue-grey slate
        "polished_deepslate": lambda F, n: m_polished(F, n, (0.27, 0.31, 0.40), 0.05, 0.03, seam=0.25, calm=True),
        # r21: 22 small high-contrast stones read as salt-and-pepper on the dark base - fewer, larger, calmer stones
        # r31: m_cobbled_slate - 16 flat angular flakes per face, soft joints (3-4 domed stones read as cartoon)
        "cobbled_deepslate": lambda F, n: m_cobbled_slate(F, n, cells=16),
        "netherrack": m_netherrack,
        "nether_bricks": lambda F, n: m_bricks(F, n, 4, 2, (0.30, 0.12, 0.14), pits=0.05),
        "prismarine_bricks": lambda F, n: m_bricks(F, n, 2, 2, (0.42, 0.66, 0.58), tone_amp=0.10),
        "dark_prismarine": lambda F, n: m_tiles(F, n, 2, (0.20, 0.38, 0.33)),
        "moss_block": m_moss,
        "mossy_stone_bricks": lambda F, n: m_stone_bricks(F, n, moss=0.9),   # r31: the plain layout, moss at the joints
        # round 12: the census's rest, other biomes' terrain
        "sandstone": lambda F, n: m_sandstone(F, n, "side"),
        "sandstone_top": lambda F, n: m_sandstone(F, n, "top"),
        "sandstone_bottom": lambda F, n: m_sandstone(F, n, "bottom"),
        # r31: m_terracotta - clean fired-clay hues (orange-tan, brick red, blue-violet), soft mottle, no grit
        "red_terracotta": lambda F, n: m_terracotta(F, n, (0.64, 0.22, 0.15)),
        "terracotta": lambda F, n: m_terracotta(F, n, (0.72, 0.44, 0.28)),
        "polished_basalt_side": lambda F, n: m_basalt(F, n, False),
        "polished_basalt_top": m_polished_basalt_top,   # r32: a ground face with concentric rings
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
        # ("mud" moved below: r31 m_mud paints its own smoothness)
        "calcite": lambda F, n: m_igneous(F, n, (0.90, 0.90, 0.89), (0.80, 0.80, 0.79), 0.4),   # r35: smooth bright white
        # fine ash with a few darker lapilli: small crystals, low contrast, the tone in a soft mottle (round 18)
        "tuff": lambda F, n: m_crystal(F, n, [((0.42, 0.42, 0.38), 0.85), ((0.32, 0.32, 0.29), 0.15)],
                                       per_side=34, cluster=0.85, contrast=0.3, mottle=0.08, drift_base=3),
        # round 13: every wood's planks and stripped log, the long tail
        "birch_planks": lambda F, n: m_planks(F, n, (0.80, 0.70, 0.48), 0.012),
        "jungle_planks": lambda F, n: m_planks(F, n, (0.66, 0.46, 0.32), 0.012),
        # r31 (art director: "reddish-brown, should be the distinctive orange acacia"): hue 22 -> 27 deg, straighter
        "acacia_planks": lambda F, n: m_planks(F, n, (0.82, 0.46, 0.17), 0.006),
        "dark_oak_planks": lambda F, n: m_planks(F, n, (0.34, 0.22, 0.12), 0.006),   # r31: lifted, straighter grain
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
        "polished_granite": m_polished_granite,     # r31: a salmon crystal mosaic, not a red-brown mottle
        # r21: 20-56 per block flecks read as pepper at RTS zoom - fewer, larger dark crystal clusters
        "polished_diorite": lambda F, n: m_polished(F, n, (0.84, 0.84, 0.82), 0.08, 0.16, fleck=(6, 18)),
        # r21 (reviewer: "high-contrast light speckle on black, pepper at RTS"): crystals closer in tone and larger
        "blackstone": lambda F, n: m_igneous(F, n, (0.19, 0.14, 0.21), (0.23, 0.19, 0.25), 0.6, (8, 20)),   # r39: violet-black
        # r22 (shader review: "purple cobble-cell pattern, no glassy darkness"): volcanic glass is near black with only
        # a faint violet depth - mottle 0.20 -> 0.06
        # r22b (eyes: a flat dark-brown cake, the flecks invisible): 3-5 large conchoidal planes of cool black glass,
        # each with its own tone and a hard bright edge - the shader's smooth/F0 gives the glint
        "red_nether_bricks": lambda F, n: m_bricks(F, n, 4, 2, (0.42, 0.08, 0.08), pits=0.05),
        "chiseled_stone_bricks": m_chiseled,
        "dirt_path_top": lambda F, n: m_cracked_path(F, n, False),   # r34: cracked dry earth
        "dirt_path_side": lambda F, n: m_cracked_path(F, n, True),
        # round 14: cracked / chiseled variants, metals and gems, the structure preview's gaps
        "cracked_stone_bricks": lambda F, n: m_stone_bricks(F, n, cracks=1.0),   # r31: the plain layout
        "cracked_deepslate_bricks": lambda F, n: m_bricks(F, n, 4, 2, (0.30, 0.30, 0.32), cracks=1.0),
        "cracked_polished_blackstone_bricks": lambda F, n: m_bricks(F, n, 4, 2, (0.21, 0.18, 0.23), cracks=1.0),
        "chiseled_nether_bricks": lambda F, n: m_chiseled(F, n, (0.30, 0.12, 0.14)),
        "chiseled_polished_blackstone": lambda F, n: m_chiseled(F, n, (0.21, 0.18, 0.23)),
        "reinforced_deepslate_side": lambda F, n: m_reinforced(F, n, False),
        "reinforced_deepslate_top": lambda F, n: m_reinforced(F, n, True),
        # r32 (critic: smooth basalt vs basalt identical): a cut, calm face with a faint 2x2 seam, a hair of rim
        # r33 (critic, eyes 41029731853: "concrete pavers" - the 2x2 seam and the rim drew a paver grid): one calm
        # polished slab with a faint grain, no seam, no rim; a cut face still (flat, satin), basalt keeps its columns
        "smooth_basalt": lambda F, n: m_polished(F, n, (0.28, 0.28, 0.30), 0.06, 0.03, rim=None, calm=True),
        "soul_sand": m_soul_sand,
        # r31: m_gem - 3-5 hard flat facets, deep body, bright tops (m_facets' 16-18 pyramids read as cabbage)
        "diamond_block": lambda F, n: m_gem(F, n, (0.40, 0.72, 0.82), (0.90, 0.98, 1.0)),   # r33: brighter body
        # r32 (critic: "too dark and muddy - a clear emerald green body with lighter facet edges"): a clean emerald
        # body, bright facet tops, the facet edges glowing, half the jardin
        "emerald_block": lambda F, n: m_gem(F, n, (0.14, 0.60, 0.30), (0.62, 0.97, 0.66), inclusions=0.4, edge_glow=0.35),
        # lapis is a stone, not a crystal: matte ultramarine blocks of rock with gold pyrite flecks
        "lapis_block": lambda F, n: m_lapis(F, n),
        # r21: flat pale yellow, same as sand beside it - fewer, larger darker pits so it reads as porous stone
        "end_stone": lambda F, n: m_igneous(F, n, (0.86, 0.86, 0.66), (0.62, 0.62, 0.46), 0.9, size=(7, 16)),
        "white_concrete": lambda F, n: m_clayish(F, n, (0.88, 0.88, 0.88), pits=0.01, mottle=0.04),   # r33: neutral
        # r31b (eyes: "reads as violet dyed wool"): a dusty slate-blue, chroma pulled toward grey
        # r33 (critic: "crushed"): a clearer dusty blue, the hue toward blue rather than violet
        "blue_terracotta": lambda F, n: m_terracotta(F, n, (0.30, 0.36, 0.52), flash=(0.94, 0.95, 0.99)),
        "podzol_side": m_podzol_side,
        # round 15: the face census's partial blocks
        "blackstone_top": lambda F, n: m_igneous(F, n, (0.19, 0.14, 0.21), (0.23, 0.19, 0.25), 0.65, (8, 20)),
        "quartz_block_top": lambda F, n: m_quartz(F, n, "block_top"),
        "quartz_block_side": lambda F, n: m_quartz(F, n, "block_side"),
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
    # r30 (lovish Oct 30: "more realistic than" Complementary's obsidian): volcanic glass - conchoidal shell scoops
    # with ripple rings, smoky thin aretes, glass-smooth faces (m_obsidian_glass paints its own smoothness)
    R["obsidian"] = _parts(m_obsidian_glass)
    R["mud"] = _parts(m_mud)                   # r31: drying mud flat, wet / dry in the smoothness
    R["farmland"] = _parts(lambda F, n: m_farmland(F, n, False))            # r34: clods and crisp furrows
    R["farmland_moist"] = _parts(lambda F, n: m_farmland(F, n, True))       # r34: wet glossy furrows (own smoothness)
    R["iron_block"] = _parts(lambda F, n: _d3(m_iron(F, n)))                       # round 23: brushed, riveted
    R["netherite_block"] = _parts(lambda F, n: _d3(m_netherite(F, n)))   # r31: bolted bosses
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
    # round 34: the glowing blocks (emission in the _s alpha on the hot / lit parts only)
    R["magma_block"] = _parts(m_magma, fit=False)
    R["redstone_ore"] = _parts(lambda F, n: m_redstone_ore(F, n, False), fit=False)
    R["deepslate_redstone_ore"] = _parts(lambda F, n: m_redstone_ore(F, n, True), fit=False)
    R["glowstone"] = _parts(m_glowstone, fit=False)
    R["shroomlight"] = _parts(m_shroomlight, fit=False)
    R["sea_lantern"] = _parts(m_sea_lantern, fit=False)
    R["bricks"] = _plain(m_clay_bricks)                        # round 35
    for colour, (w_rgb, k_rgb, t_rgb) in DYES.items():         # round 36: the dyed families
        R[colour + "_wool"] = _plain(lambda F, n, c=w_rgb: m_wool(F, n, c))
        if colour + "_concrete" not in R:
            R[colour + "_concrete"] = _plain(lambda F, n, c=k_rgb: m_concrete(F, n, c))
        if colour + "_terracotta" not in R:
            R[colour + "_terracotta"] = _plain(lambda F, n, c=t_rgb: m_terracotta(F, n, c))
    R["bookshelf"] = _plain(m_bookshelf)
    for colour, (w_rgb, _k, _t) in DYES.items():                # round 37: the other stained glasses
        if colour in STAINED:
            continue
        rgb = tuple(min(1.0, 0.25 + 0.75 * v) for v in w_rgb)
        R[colour + "_stained_glass"] = _parts(lambda F, n, c=rgb: m_glass(F, n, c, "stained"), fit=False)
        R[colour + "_stained_glass_pane_top"] = _parts(lambda F, n, c=rgb: m_glass(F, n, c, "edge"), fit=False)
    R["chiseled_quartz_block"] = _plain(lambda F, n: m_quartz(F, n, "chiseled"))
    R["chiseled_quartz_block_top"] = _plain(lambda F, n: m_quartz(F, n, "chiseled_top"))
    R["chiseled_bookshelf_top"] = _plain(lambda F, n: m_chiseled_bookshelf(F, n, "top"))
    R["chiseled_bookshelf_side"] = _plain(lambda F, n: m_chiseled_bookshelf(F, n, "side"))
    R["chiseled_bookshelf_empty"] = _plain(lambda F, n: m_chiseled_bookshelf(F, n, "empty"))
    R["chiseled_bookshelf_occupied"] = _plain(lambda F, n: m_chiseled_bookshelf(F, n, "occupied"))
    for colour, (_w, k_rgb, _t) in DYES.items():                # round 38
        R[colour + "_concrete_powder"] = _plain(lambda F, n, c=powder_colour(k_rgb): m_concrete_powder(F, n, c))
        gl = tuple(0.55 * a + 0.45 * b for a, b in zip(k_rgb, _t))
        R[colour + "_glazed_terracotta"] = _parts(lambda F, n, c=gl, kd=GLAZE_KIND[colour]: m_glazed(F, n, c, kd))
    for stage, name in enumerate(("copper_block", "exposed_copper", "weathered_copper", "oxidized_copper")):
        R[name] = _parts(lambda F, n, st=stage: _d4(m_copper(F, n, st, plate=True)))
    R["crafting_table_top"] = _plain(lambda F, n: m_crafting_table(F, n, "top"))
    R["crafting_table_side"] = _plain(lambda F, n: m_crafting_table(F, n, "side"))
    R["crafting_table_front"] = _plain(lambda F, n: m_crafting_table(F, n, "front"))
    R["rooted_dirt"] = _plain(m_rooted_dirt)
    R["mushroom_stem"] = _plain(m_mushroom_stem)
    for f in ("side", "top", "bottom", "compost", "ready"):
        R["composter_" + f] = _plain(lambda F, n, ff=f: m_composter(F, n, ff))
    R["mud_bricks"] = _plain(m_mud_bricks)                     # round 39
    R["packed_mud"] = _plain(m_packed_mud)
    R["note_block"] = _plain(lambda F, n: m_note_block(F, n, "note"))
    R["jukebox_side"] = _plain(lambda F, n: m_note_block(F, n, "jukebox"))
    R["jukebox_top"] = _plain(lambda F, n: m_note_block(F, n, "jukebox_top"))
    R["exposed_cut_copper"] = _parts(lambda F, n: _d4(m_copper(F, n, 1)))
    R["weathered_cut_copper"] = _parts(lambda F, n: _d4(m_copper(F, n, 2)))
    for name, base in SCAN_VARIANT_OF.items():        # round 28: variant k is SCAN index k - 1
        i = int(name.rsplit("_v", 1)[1]) - 1
        if base in ("basalt_side", "basalt_top"):     # r31: procedural columns, their own seed each
            R[name] = R[base]
        elif base in SCAN_BASES:
            R[name] = _scan(base, i)
        elif base == "grass_block_side":
            R[name] = _scan_grass_side(i)
        else:
            R[name] = _scan_grass_overlay(i)
    for name, base in PROC_VARIANT_OF.items():        # round 33: variant k is index k - 1 (its own seed)
        R[name] = proc_variant(base, int(name.rsplit("_v", 1)[1]) - 1)
    return R


RECIPES = recipes()


# Round 17 (lovish Oct 20: "test 256x where it counts"): the textures that fill the screen and are seen up close in
# first person (possession) are built at twice the resolution - the leaves and the bark (most of the screen in a
# forest) and the grass top. At the RTS camera a 256x texture is sampled at the same mip as a 128x one (rts_view,
# cal_mip), so the RTS look is the same; up close the leaf edges, blades and bark fibre come out sharp instead of
# stepped. Each costs ~4x the memory of a 128x texture (x3 maps) - 13 textures, ~13 MB of atlas with mipmaps.
# Not stone and the building blocks: their grain read worse, not better, at 256x (a maze of worms) and they are many.
HIRES = set(GRASS_TOPS) | set(LEAVES) | {"oak_log"} | {w + "_log" for w in WOODS} | {"birch_log"}
# round 28: the photo scans at 256 too (from 512 px sources) - at 128 the scans' grain fell under a texel and the mips
# averaged it to mush; the grass side and its overlay match the top's resolution
HIRES |= {"stone", "dirt", "grass_block_side", "grass_block_side_overlay", "basalt_side", "basalt_top"} \
    | set(SCAN_VARIANT_OF)


# Round 18d: the textures whose HEIGHT channel (normal map A) stays within a shallow top band. Complementary reads the
# height even with parallax off, and stone's full 0-1 height drew the blue-grey worm marks: in game, stone with a
# flat height and its real normals and colour was calm, and near-flat normals with stone's real height showed the
# worms (eyes structure runs 39546529592 and 39547379343 - channel-split tests on claude/pack-diagnostic-flat-stone).
# The normals keep the relief for the light; the height keeps a little depth for players who turn parallax on.
SHALLOW_HEIGHT = 0.12
CALM_HEIGHT = {"stone", "granite", "diorite", "andesite", "tuff", "calcite", "grass_block_top", "grass_block_top_v2", "dirt", "coarse_dirt",
               "podzol_top", "sand", "red_sand", "gravel"} | set(LEAVES) | {"azalea_leaves", "flowering_azalea_leaves", "cherry_leaves"}
CALM_HEIGHT |= {v for v, b in SCAN_VARIANT_OF.items() if b in CALM_HEIGHT}


# Round 20: every other opaque texture keeps its height within the top quarter. Complementary's POM (on by default in
# labPBR mode) turned full-range heights into pillowed tops, one-way grooves at block borders and blotchy hammered
# flats on the building blocks (block sampler, eyes run 39554020573). Brick joints keep a quarter-block of depth.
BUILDING_HEIGHT = 0.25


def targets_for(name):
    """The targets a texture is built with: TARGETS plus the height band of rounds 18d / 20."""
    t = TARGETS[name]
    if name in CALM_HEIGHT and "hspan" not in t:
        t = dict(t, hspan=SHALLOW_HEIGHT)
    elif "hspan" not in t and name not in CUTOUT:
        t = dict(t, hspan=BUILDING_HEIGHT)
    return t


def make(name, res, preview_dir):
    """One texture, start to end: recipe -> LabPBR maps -> the three PNGs (+ previews); returns its report line.
    Independent of every other texture (its own seeded Field), so the build runs them in parallel."""
    t = targets_for(name)
    F = Field(res, SEEDS[name])
    r = RECIPES[name](F, res, t)
    for key in ("f0", "smooth", "emit"):   # per-texel codes a recipe painted (see maps)
        if key in r:
            t = dict(t, **{key + "_map": r[key]})
    H, alb, alpha = r["H"], r["alb"], r.get("alpha")
    if "maps" in r:     # round 28: a recipe that finishes its own maps (the scans' variants, the grass side)
        n_img, s_img = r["maps"]
        k = r["k"]
        probs = materials.check_labpbr(n_img, s_img, None if alpha is None else alpha > 0.02)
        if probs:
            raise SystemExit("LabPBR check failed (%s): %s" % (name, "; ".join(probs)))
    else:
        n_img, s_img, k = maps(F, H, t, alpha, r.get("nxy"))
    to_png(alb, alpha).save(os.path.join(BLOCK_DIR, name + ".png"), **PNG_OPTS)
    Image.fromarray(n_img, "RGBA").save(os.path.join(BLOCK_DIR, name + "_n.png"), **PNG_OPTS)
    Image.fromarray(s_img, "RGBA").save(os.path.join(BLOCK_DIR, name + "_s.png"), **PNG_OPTS)
    nxx = (n_img[..., 0].astype(np.float64) / 127.5 - 1)
    nyy = (n_img[..., 1].astype(np.float64) / 127.5 - 1)
    vis = None if alpha is None else alpha > 0.5
    overlays = ("grass_block_side_overlay",) + tuple(v for v in SCAN_VARIANT_OF if v.startswith("grass_block_side_ov"))
    tint = PLAINS_TINT if name in GRASS_TOPS + overlays else \
        FOLIAGE_TINT if name in LEAVES else None
    seen = alb if alpha is None or name not in CUTOUT else alb * alpha[..., None] + 0.08 * (1 - alpha[..., None])
    rts_img, rts_std = rts_view(seen, tint=tint, rotate=name in ROTATED)
    report = {"normal_strength": round(k, 2), "tilt_mip0": round(mip_tilt(nxx, nyy, vis, 0), 1),
              "tilt_mip2": round(mip_tilt(nxx, nyy, vis), 1), "target": TARGETS[name]["tilt"],
              "rts_luma_std": round(rts_std, 3)}
    if preview_dir:
        shown = alb
        if name in GRASS_TOPS + overlays:
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


# Round 35: multi-block sheets through Athena's "giant" model (athena-forge 3.1.2, installed with Chipped): the
# blockstate names a width x height grid of sprites and each block shows the one at its world (x, z) on the top -
# (x | z, y) on the sides - so a pattern larger than a block (wind ripples across a dune) carries on from block to
# block. Square sizes only (the 1.20.1 code indexes with height). Its quads carry no biome tint: never grass.
GIANT = {   # name: (sheet recipe(F, blocks) -> (H, alb), blocks per side, tilt factor)
    "sand": (lambda F, b: m_sand_sheet(F, b, (0.86, 0.78, 0.58)), 4, 1.3),
    "red_sand": (lambda F, b: m_sand_sheet(F, b, (0.75, 0.42, 0.18)), 4, 1.3),     # r36: protection lifted
    # round 37: smooth stone as a flagstone floor (the full cube only: smooth_stone_slab's own model draws the single
    # smooth_stone.png for its top and bottom, which stays as it is, and smooth_stone_slab_side for its side)
    "smooth_stone": (m_flagstone_sheet, 4, 0.6),
}


def write_giant(name, res, preview_dir=None):
    """The sheet of `name` (GIANT): one Field over blocks x blocks, fitted and solved once (one mean, one normal
    strength), cut into blocks * blocks tiles <name>_g1.. (row-major from the sheet's top left, Athena's order:
    1 + x + z * N) with their _n and _s. The single-block texture (make) stays as the particle and the fallback."""
    recipe, blocks, tilt_k = GIANT[name]
    t = targets_for(name)
    t = dict(t, tilt=t["tilt"] * tilt_k)                 # the ripples want a lower sun than the flat grains did
    big = res * blocks
    F = Field(big, SEEDS[name] + 7)
    H, alb = recipe(F, blocks)
    alb = soften(F, fit_albedo(alb, t), t)
    n_img, s_img, k = maps(F, H, t)
    for z in range(blocks):
        for x in range(blocks):
            i = 1 + x + z * blocks
            sl = (slice(z * res, (z + 1) * res), slice(x * res, (x + 1) * res))
            to_png(alb[sl]).save(os.path.join(BLOCK_DIR, "%s_g%d.png" % (name, i)), **PNG_OPTS)
            Image.fromarray(n_img[sl], "RGBA").save(os.path.join(BLOCK_DIR, "%s_g%d_n.png" % (name, i)), **PNG_OPTS)
            Image.fromarray(s_img[sl], "RGBA").save(os.path.join(BLOCK_DIR, "%s_g%d_s.png" % (name, i)), **PNG_OPTS)
    if preview_dir:
        img = lit_preview(alb, n_img)[:big, :big]
        Image.fromarray((img * 255).astype(np.uint8), "RGB").resize((512, 512), Image.LANCZOS).save(
            os.path.join(preview_dir, name + "_sheet_lit.png"))
    return {"normal_strength": round(k, 2), "blocks": blocks}


def _job(args):
    name, res, preview_dir, hires = args
    if hires and name in HIRES:
        res *= 2
    if name.endswith("_giant"):
        return name, write_giant(name[:-6], res, preview_dir)
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
    names = ["water_flow", "water_still"] + [g + "_giant" for g in GIANT] + list(TARGETS)
    jobs = max(1, jobs or os.cpu_count() or 1)
    work = [(n, res, preview_dir, hires) for n in names]
    if jobs == 1:
        done = dict(map(_job, work))
    else:
        from concurrent.futures import ProcessPoolExecutor
        with ProcessPoolExecutor(max_workers=jobs) as ex:
            done = dict(ex.map(_job, work))
    report = {n: done[n] for n in list(TARGETS) + ["water_still", "water_flow"] + [g + "_giant" for g in GIANT]}
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
