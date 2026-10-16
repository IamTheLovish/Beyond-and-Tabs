# Face census: the block textures the RTS camera sees on our buildings

Source: client.jar; our pack: tools/gen_blocks.py TARGETS (156 textures). Seen weight = up-face area + 0.5 x side-face area (block faces hidden by a full neighbour not counted).

**Our pack covers 92.7% of the seen faces** (73 structures).

## Partial blocks: our textures on some faces, vanilla on others (fix these first)

| block | vanilla faces (weight) | our faces (weight) | vanilla textures |
|---|---:|---:|---|
| water_cauldron | 36 | 5 | cauldron_side 27, cauldron_inner 5, cauldron_top 4 |
| bookshelf | 24 | 4 | bookshelf 24 |
| potted_flowering_azalea_bush | 11 | 0 | potted_flowering_azalea_bush_plant 4, potted_flowering_azalea_bush_side 4, flower_pot 2, potted_flowering_azalea_bush_top 1 |
| potted_red_mushroom | 3 | 0 | red_mushroom 2, flower_pot 1 |
| potted_brown_mushroom | 3 | 0 | brown_mushroom 2, flower_pot 1 |
| beacon | 2 | 8 | beacon 2 |
| potted_poppy | 2 | 0 | poppy 1, flower_pot 1 |
| potted_dandelion | 2 | 0 | dandelion 1, flower_pot 1 |
| potted_cornflower | 2 | 0 | cornflower 1, flower_pot 1 |
| potted_allium | 2 | 0 | allium 1, flower_pot 1 |

## Uncovered textures by seen weight

| texture | seen | of it up faces | share of all seen | blocks using it |
|---|---:|---:|---:|---|
| fire_0 | 185 | 0 | 0.73% | fire |
| slime_block | 68 | 38 | 0.27% | slime_block |
| cauldron_side | 63 | 0 | 0.25% | water_cauldron, lava_cauldron, cauldron |
| soul_fire_0 | 50 | 0 | 0.20% | soul_fire |
| spruce_trapdoor | 48 | 21 | 0.19% | spruce_trapdoor |
| crimson_trapdoor | 40 | 14 | 0.16% | crimson_trapdoor |
| sea_lantern | 40 | 18 | 0.16% | sea_lantern |
| lava_still | 37 | 37 | 0.14% | lava, lava_cauldron |
| dead_horn_coral_fan | 36 | 36 | 0.14% | dead_horn_coral_fan |
| magma | 36 | 15 | 0.14% | magma_block |
| anvil | 32 | 13 | 0.13% | anvil |
| green_glazed_terracotta | 32 | 22 | 0.12% | green_glazed_terracotta |
| verdant_froglight_side | 30 | 0 | 0.12% | verdant_froglight |
| mossy_cobblestone | 27 | 8 | 0.11% | mossy_cobblestone_wall, mossy_cobblestone |
| campfire_log | 26 | 17 | 0.10% | campfire, soul_campfire |
| gilded_blackstone | 24 | 14 | 0.10% | gilded_blackstone |
| bookshelf | 24 | 0 | 0.09% | bookshelf |
| blue_glazed_terracotta | 24 | 8 | 0.09% | blue_glazed_terracotta |
| campfire_fire | 23 | 0 | 0.09% | campfire |
| amethyst_block | 22 | 6 | 0.09% | amethyst_block |
| shroomlight | 22 | 17 | 0.09% | shroomlight |
| flowering_azalea_side | 22 | 0 | 0.09% | flowering_azalea |
| cyan_wool | 21 | 6 | 0.08% | cyan_wool |
| red_wool | 20 | 14 | 0.08% | red_wool |
| stripped_crimson_stem | 20 | 8 | 0.08% | stripped_crimson_hyphae |
| end_portal_frame_side | 20 | 0 | 0.08% | end_portal_frame |
| verdant_froglight_top | 17 | 17 | 0.07% | verdant_froglight |
| crimson_roots | 16 | 0 | 0.06% | crimson_roots |
| redstone_lamp_on | 16 | 0 | 0.06% | redstone_lamp |
| exposed_cut_copper | 16 | 2 | 0.06% | waxed_exposed_cut_copper |
| gray_glazed_terracotta | 16 | 6 | 0.06% | gray_glazed_terracotta |
| dead_brain_coral_fan | 16 | 16 | 0.06% | dead_brain_coral_fan |
| azalea_plant | 16 | 0 | 0.06% | flowering_azalea |
| dead_brain_coral_block | 15 | 4 | 0.06% | dead_brain_coral_block |
| campfire_log_lit | 15 | 5 | 0.06% | campfire |
| rose_bush_bottom | 14 | 0 | 0.06% | rose_bush |
| rose_bush_top | 14 | 0 | 0.06% | rose_bush |
| copper_block | 14 | 5 | 0.06% | waxed_copper_block |
| yellow_stained_glass | 14 | 3 | 0.05% | yellow_stained_glass |
| warped_trapdoor | 13 | 4 | 0.05% | warped_trapdoor |

## Covered textures by seen weight

| texture | seen | share |
|---|---:|---:|
| azalea_leaves | 2496 | 9.87% |
| stone_bricks | 2203 | 8.71% |
| polished_blackstone_bricks | 1454 | 5.75% |
| cut_copper | 1170 | 4.63% |
| nether_bricks | 958 | 3.79% |
| polished_blackstone | 932 | 3.68% |
| deepslate_bricks | 882 | 3.49% |
| flowering_azalea_leaves | 800 | 3.16% |
| spruce_planks | 760 | 3.00% |
| oak_log | 732 | 2.89% |
| oxidized_cut_copper | 642 | 2.54% |
| deepslate_tiles | 637 | 2.52% |
| polished_andesite | 512 | 2.02% |
| dark_prismarine | 435 | 1.72% |
| netherrack | 352 | 1.39% |
| polished_basalt_side | 326 | 1.29% |
| mossy_stone_bricks | 323 | 1.28% |
| red_terracotta | 322 | 1.27% |
| moss_block | 315 | 1.24% |
| prismarine_bricks | 308 | 1.22% |
| oak_planks | 286 | 1.13% |
| dark_oak_planks | 284 | 1.12% |
| mangrove_roots_side | 268 | 1.06% |
| cobblestone | 251 | 0.99% |
| gold_block | 226 | 0.90% |
| bone_block_side | 223 | 0.88% |
| crimson_planks | 203 | 0.80% |
| stone | 202 | 0.80% |
| obsidian | 190 | 0.75% |
| stripped_oak_log | 175 | 0.69% |
| red_nether_bricks | 169 | 0.67% |
| polished_deepslate | 167 | 0.66% |
| sandstone_top | 145 | 0.57% |
| andesite | 137 | 0.54% |
| deepslate | 136 | 0.54% |
| water_flow | 130 | 0.51% |
| cobbled_deepslate | 128 | 0.50% |
| stripped_spruce_log | 124 | 0.49% |
| red_stained_glass | 120 | 0.47% |
| chiseled_nether_bricks | 116 | 0.46% |
| dark_oak_leaves | 108 | 0.43% |
| stripped_mangrove_log | 104 | 0.41% |
| chiseled_stone_bricks | 94 | 0.37% |
| ladder | 92 | 0.36% |
| quartz_pillar | 91 | 0.36% |
| lantern | 89 | 0.35% |
| water_still | 84 | 0.33% |
| dark_oak_log | 84 | 0.33% |
| blackstone_top | 80 | 0.31% |
| reinforced_deepslate_side | 77 | 0.30% |
| netherite_block | 73 | 0.29% |
| mangrove_roots_top | 73 | 0.29% |
| iron_bars | 64 | 0.25% |
| chiseled_polished_blackstone | 62 | 0.25% |
| mangrove_planks | 62 | 0.25% |
| blackstone | 62 | 0.24% |
| cobweb | 61 | 0.24% |
| quartz_block_bottom | 60 | 0.24% |
| glass | 60 | 0.24% |
| dirt_path_side | 57 | 0.23% |
| blue_terracotta | 56 | 0.22% |
| basalt_side | 56 | 0.22% |
| spruce_log | 54 | 0.22% |
| bone_block_top | 53 | 0.21% |
| calcite | 53 | 0.21% |
| dirt_path_top | 52 | 0.21% |
| dirt | 52 | 0.20% |
| polished_granite | 51 | 0.20% |
| crimson_stem | 51 | 0.20% |
| diamond_block | 50 | 0.20% |
| oak_log_top | 49 | 0.19% |
| warped_stem | 49 | 0.19% |
| white_concrete | 48 | 0.19% |
| soul_lantern | 47 | 0.19% |
| warped_planks | 46 | 0.18% |
| smooth_stone | 44 | 0.17% |
| emerald_block | 40 | 0.16% |
| cracked_polished_blackstone_bricks | 38 | 0.15% |
| quartz_block_top | 36 | 0.14% |
| quartz_block_side | 34 | 0.13% |
| iron_block | 33 | 0.13% |
| lapis_block | 33 | 0.13% |
| cracked_deepslate_bricks | 32 | 0.13% |
| barrel_side | 32 | 0.12% |
| podzol_top | 30 | 0.12% |
| tuff | 29 | 0.11% |
| farmland_moist | 28 | 0.11% |
| basalt_top | 26 | 0.10% |
| oak_leaves | 24 | 0.09% |
| smooth_basalt | 24 | 0.09% |
| grass_block_top | 23 | 0.09% |
| smooth_stone_slab_side | 22 | 0.09% |
| reinforced_deepslate_top | 22 | 0.09% |
| tinted_glass | 20 | 0.08% |
| quartz_bricks | 20 | 0.08% |
| soul_sand | 20 | 0.08% |
| podzol_side | 20 | 0.08% |
| deepslate_top | 18 | 0.07% |
| polished_basalt_top | 18 | 0.07% |
| stripped_mangrove_log_top | 17 | 0.07% |
| stripped_birch_log | 17 | 0.07% |
| polished_diorite | 16 | 0.06% |
| barrel_top | 16 | 0.06% |
| grass_block_side | 16 | 0.06% |
| grass_block_side_overlay | 16 | 0.06% |
| stripped_oak_log_top | 14 | 0.06% |
| mud | 13 | 0.05% |
| stripped_spruce_log_top | 12 | 0.05% |
| cyan_stained_glass | 12 | 0.05% |
| quartz_pillar_top | 10 | 0.04% |
| end_stone | 9 | 0.04% |
| jungle_planks | 9 | 0.03% |
| dark_oak_log_top | 8 | 0.03% |
| coarse_dirt | 8 | 0.03% |
| stripped_jungle_log | 8 | 0.03% |
| glass_pane_top | 7 | 0.03% |
| hay_block_side | 6 | 0.02% |
| cracked_stone_bricks | 6 | 0.02% |
| raw_iron_block | 6 | 0.02% |
| spruce_log_top | 6 | 0.02% |
| diorite | 4 | 0.02% |
| hay_block_top | 4 | 0.02% |
| warped_stem_top | 4 | 0.02% |
| stripped_dark_oak_log | 4 | 0.01% |
| barrel_bottom | 0 | 0.00% |

Our textures no building shows (terrain, trees and the map use them): acacia_leaves, acacia_log, acacia_log_top, acacia_planks, birch_leaves, birch_log, birch_log_top, birch_planks, clay, crimson_stem_top, cyan_stained_glass_pane_top, farmland, granite, grass_block_snow, gravel, jungle_leaves, jungle_log, jungle_log_top, red_sand, red_stained_glass_pane_top, sand, sandstone, sandstone_bottom, snow, spruce_leaves, stripped_acacia_log, stripped_acacia_log_top, stripped_birch_log_top, stripped_dark_oak_log_top, stripped_jungle_log_top, terracotta

Drawn by block entity renderers (entity textures, outside a block pack): chest 32, end_portal 17, wither_skeleton_skull 14, blue_banner 8, black_banner 7, red_banner 7, red_wall_banner 4, spruce_wall_sign 4, blue_wall_banner 4, skeleton_skull 1, zombie_head 1, decorated_pot 1

No blockstate found (modded or renamed blocks): mcwroofs:deepslate_steep_roof 28, mcwroofs:thatch_steep_roof 27, mcwroofs:deepslate_top_roof 7, mcwroofs:thatch_top_roof 7, mcwwindows:spruce_window 6, mcwwindows:blackstone_brick_gothic 4, mcwwindows:dark_oak_window 4, reignofnether:walkable_magma_block 4, decayable_netherwart_block 1, reignofnether:horizontal_portal 1

## Coverage by faction (mean share of seen faces per building)

| faction | buildings | mean | lowest |
|---|---:|---:|---:|
| brass | 5 | 94% | 92% |
| monsters | 14 | 89% | 63% |
| neutral | 5 | 88% | 67% |
| piglins | 15 | 85% | 50% |
| shared | 6 | 86% | 79% |
| storm | 4 | 98% | 97% |
| tide | 5 | 91% | 87% |
| verdant | 5 | 96% | 94% |
| villagers | 14 | 91% | 81% |
