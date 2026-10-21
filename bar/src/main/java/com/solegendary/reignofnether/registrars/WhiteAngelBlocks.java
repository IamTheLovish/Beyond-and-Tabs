package com.solegendary.reignofnether.registrars;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The White Angel block set (lovish, Oct 21: "If we need to create new blocks... can we" - where a capitol shape or
 * stone can't be done well with vanilla, Macaw's or Chipped, it gets its own block). tools/gen_wa_blocks.py draws
 * every texture (128x, with LabPBR _n / _s maps for shader packs) and writes the blockstates, models, item models,
 * loot tables and names; this class registers them. Any White Angel building can use them:
 *
 * - wa_ashlar (+ stairs, slab, wall): the warm light grey of lovish's chosen option C, coursed limestone ashlar -
 *   vanilla has no warm light grey (polished andesite reads a cool mid grey);
 * - wa_dressed (+ stairs, slab): finely dressed stone for sills, string courses, copings and ribs;
 * - wa_tracery / wa_tracery_head: lit gothic lancet panels (stone mullion, leaded amber glass), light 10;
 * - wa_finial: a crocketed finial for pinnacles and gable apexes (a block model, a slim collision box);
 *   wa_finial_gold: the same gilded, for the spires' tips and the crown;
 * - wa_slate (+ stairs, slab): a lighter blue-grey roofing slate for the great roofs (deepslate tiles read black from
 *   the RTS camera: lovish via the main session, Oct 21 14:20, "the big black roof slopes read as dark patches");
 * - wa_gargoyle: a crouching stone gargoyle (a block model) looking out from a corner or an eaves (horizontal facing);
 * - wa_dedication_0 .. _7: the carved dedication tablet, 4 x 2 blocks - "INSPIRED BY SIXWINGS / EMBERHEART CASTLE" -
 *   built into the capitol's foundation, turned to face out (a horizontal facing).
 *
 * Its own DeferredRegister (Forge allows several per registry and mod) so the set lives in one place; the block items
 * join BlockRegistrar's building-blocks creative tab list.
 */
public final class WhiteAngelBlocks {
    private WhiteAngelBlocks() { }

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ReignOfNether.MOD_ID);

    /** Dressed limestone: as hard as stone bricks, the sand map colour (the warm light grey on maps). */
    private static BlockBehaviour.Properties stone() {
        return BlockBehaviour.Properties.copy(Blocks.STONE_BRICKS).mapColor(MapColor.SAND);
    }

    public static final RegistryObject<Block> ASHLAR = register("wa_ashlar", () -> new Block(stone()));
    public static final RegistryObject<StairBlock> ASHLAR_STAIRS = register("wa_ashlar_stairs",
            () -> new StairBlock(() -> ASHLAR.get().defaultBlockState(), stone()));
    public static final RegistryObject<SlabBlock> ASHLAR_SLAB = register("wa_ashlar_slab", () -> new SlabBlock(stone()));
    public static final RegistryObject<WallBlock> ASHLAR_WALL = register("wa_ashlar_wall",
            () -> new WallBlock(stone().forceSolidOn()));
    public static final RegistryObject<Block> DRESSED = register("wa_dressed", () -> new Block(stone()));
    public static final RegistryObject<StairBlock> DRESSED_STAIRS = register("wa_dressed_stairs",
            () -> new StairBlock(() -> DRESSED.get().defaultBlockState(), stone()));
    public static final RegistryObject<SlabBlock> DRESSED_SLAB = register("wa_dressed_slab", () -> new SlabBlock(stone()));
    public static final RegistryObject<Block> TRACERY = register("wa_tracery",
            () -> new Block(stone().lightLevel(s -> 10)));
    public static final RegistryObject<Block> TRACERY_HEAD = register("wa_tracery_head",
            () -> new Block(stone().lightLevel(s -> 10)));
    public static final RegistryObject<Block> FINIAL = register("wa_finial",
            () -> new FinialBlock(stone().noOcclusion()));
    public static final RegistryObject<Block> FINIAL_GOLD = register("wa_finial_gold",
            () -> new FinialBlock(BlockBehaviour.Properties.copy(Blocks.GOLD_BLOCK).noOcclusion()));

    /** Roofing slate: as hard as deepslate tiles, a slate-grey map colour. */
    private static BlockBehaviour.Properties slate() {
        return BlockBehaviour.Properties.copy(Blocks.DEEPSLATE_TILES).mapColor(MapColor.COLOR_GRAY);
    }

    public static final RegistryObject<Block> SLATE = register("wa_slate", () -> new Block(slate()));
    public static final RegistryObject<StairBlock> SLATE_STAIRS = register("wa_slate_stairs",
            () -> new StairBlock(() -> SLATE.get().defaultBlockState(), slate()));
    public static final RegistryObject<SlabBlock> SLATE_SLAB = register("wa_slate_slab", () -> new SlabBlock(slate()));
    public static final RegistryObject<Block> GARGOYLE = register("wa_gargoyle",
            () -> new GargoyleBlock(stone().noOcclusion()));

    /** The dedication tablet's eight faces, left to right, top row first (the generator's split). */
    public static final List<RegistryObject<Block>> DEDICATION = dedication();

    private static List<RegistryObject<Block>> dedication() {
        List<RegistryObject<Block>> out = new ArrayList<>();
        for (int k = 0; k < 8; k++)
            out.add(register("wa_dedication_" + k, () -> new CarvedBlock(stone())));
        return List.copyOf(out);
    }

    private static <T extends Block> RegistryObject<T> register(String name, Supplier<T> block) {
        RegistryObject<T> b = BLOCKS.register(name, block);
        ItemRegistrar.ITEMS.register(name, () -> {
            BlockItem item = new BlockItem(b.get(), new Item.Properties());
            BlockRegistrar.blockItems.computeIfAbsent(CreativeModeTabs.BUILDING_BLOCKS, k -> new ArrayList<>()).add(item);
            return item;
        });
        return b;
    }

    public static void init(FMLJavaModLoadingContext context) {
        BLOCKS.register(context.getModEventBus());
    }

    /** A crocketed finial: a slim post to collide with, its own model (tools/gen_wa_blocks.finial_model). */
    public static final class FinialBlock extends Block {
        private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 16, 11);

        public FinialBlock(BlockBehaviour.Properties props) {
            super(props);
        }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE;
        }
    }

    /**
     * A gargoyle: its model's extent to collide with (the haunches against the wall, the body and head leaning out),
     * turned by its facing - the way it looks out (tools/gen_wa_blocks.GARGOYLE, modelled facing north).
     */
    public static final class GargoyleBlock extends HorizontalDirectionalBlock {
        private static final VoxelShape NORTH = Block.box(4, 0, 0, 12, 13, 16);
        private static final VoxelShape EAST_WEST = Block.box(0, 0, 4, 16, 13, 12);

        public GargoyleBlock(BlockBehaviour.Properties props) {
            super(props);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
        }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return state.getValue(FACING).getAxis() == Direction.Axis.Z ? NORTH : EAST_WEST;
        }
    }

    /** A stone with one carved face, turned to face the one who places it (and kept by a structure's rotation). */
    public static final class CarvedBlock extends HorizontalDirectionalBlock {
        public CarvedBlock(BlockBehaviour.Properties props) {
            super(props);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
        }
    }
}
