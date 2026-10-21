package com.solegendary.reignofnether.environment.wind;

import com.solegendary.reignofnether.ReignOfNether;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Branch wind (design/branch_wind.md; lovish Oct 21 2026: tree branches should sway, mod-side): the limb blocks of the
 * map trees. {@code sway_<wood>_wood} is a vanilla wood block and {@code sway_<wood>_fence} a vanilla fence, each with
 * one extra block state property, {@link #SWAY} = how far out the limb this block is (1 next to the trunk, 4 the outer
 * twigs), which sets how far the wind moves it on screen. Shapes, collision, light, pathing and felling are the vanilla
 * block's (the blocks are in {@code #minecraft:logs}, data/minecraft/tags/blocks/logs.json, so TreeClearing fells
 * them and pays wood). Only the drawing differs: a block entity ({@link SwayBlockEntity}) lets the client draw the
 * block under a wind translation while the chunk mesh leaves it out ({@link SwayBakedModel}).
 * <p>
 * tools/gen_trees.py --sway writes them; trunks and roots stay plain vanilla wood (no block entity, nothing moves).
 */
public final class SwayBlocks {
    private SwayBlocks() { }

    /** How far out the limb the block is: 1 = next to the trunk ... {@link #MAX_TIER} = the outer twigs. */
    public static final int MAX_TIER = 4;
    public static final IntegerProperty SWAY = IntegerProperty.create("sway", 1, MAX_TIER);

    /** The species gen_trees.py uses (its vanilla wood and fence ids are {@code <wood>_wood} / {@code <wood>_fence}). */
    public static final List<String> WOODS = List.of("oak", "birch", "spruce", "acacia", "cherry", "dark_oak");

    private static final Map<String, Block> VANILLA_WOOD = Map.of(
        "oak", Blocks.OAK_WOOD, "birch", Blocks.BIRCH_WOOD, "spruce", Blocks.SPRUCE_WOOD,
        "acacia", Blocks.ACACIA_WOOD, "cherry", Blocks.CHERRY_WOOD, "dark_oak", Blocks.DARK_OAK_WOOD);
    private static final Map<String, Block> VANILLA_FENCE = Map.of(
        "oak", Blocks.OAK_FENCE, "birch", Blocks.BIRCH_FENCE, "spruce", Blocks.SPRUCE_FENCE,
        "acacia", Blocks.ACACIA_FENCE, "cherry", Blocks.CHERRY_FENCE, "dark_oak", Blocks.DARK_OAK_FENCE);

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ReignOfNether.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ReignOfNether.MOD_ID);

    public static final List<RegistryObject<SwayWoodBlock>> WOOD_BLOCKS = new ArrayList<>();
    public static final List<RegistryObject<SwayFenceBlock>> FENCE_BLOCKS = new ArrayList<>();

    static {
        for (String wood : WOODS) {
            Block w = VANILLA_WOOD.get(wood), f = VANILLA_FENCE.get(wood);
            WOOD_BLOCKS.add(BLOCKS.register("sway_" + wood + "_wood",
                () -> new SwayWoodBlock(BlockBehaviour.Properties.copy(w), w)));
            FENCE_BLOCKS.add(BLOCKS.register("sway_" + wood + "_fence",
                () -> new SwayFenceBlock(BlockBehaviour.Properties.copy(f), f)));
        }
    }

    public static final RegistryObject<BlockEntityType<SwayBlockEntity>> SWAY_BLOCK_ENTITY =
        BLOCK_ENTITIES.register("sway_block_entity", () -> {
            List<Block> all = new ArrayList<>();
            WOOD_BLOCKS.forEach(r -> all.add(r.get()));
            FENCE_BLOCKS.forEach(r -> all.add(r.get()));
            return BlockEntityType.Builder.of(SwayBlockEntity::new, all.toArray(new Block[0])).build(null);
        });

    public static void init(FMLJavaModLoadingContext context) {
        BLOCKS.register(context.getModEventBus());
        BLOCK_ENTITIES.register(context.getModEventBus());
    }

    /** Whether a block state is one of the sway limb blocks. */
    public static boolean isSway(BlockState state) {
        return state.getBlock() instanceof SwayWoodBlock || state.getBlock() instanceof SwayFenceBlock;
    }

    /** The sway tier of a limb block, 0 for anything else. */
    public static int tier(BlockState state) {
        return isSway(state) ? state.getValue(SWAY) : 0;
    }

    /** The sway wood block of a species, or null. */
    public static SwayWoodBlock wood(String species) {
        int i = WOODS.indexOf(species);
        return i < 0 ? null : WOOD_BLOCKS.get(i).get();
    }

    /** The sway fence block of a species, or null. */
    public static SwayFenceBlock fence(String species) {
        int i = WOODS.indexOf(species);
        return i < 0 ? null : FENCE_BLOCKS.get(i).get();
    }
}
