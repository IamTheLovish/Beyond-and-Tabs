package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingBlockData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The order a building's blocks come out of its .nbt is the order they are laid in: the settle and count GameTests lay
 * them so, bottom up, and the list is every builder's tie-break. A vine (or glow lichen) laid before the full block it
 * hangs on turns to air as soon as a neighbour is set beside it - a stair, or a stair turning its corner - and the
 * building can never count as built: #522's round-13 Heartwood Hall lost two vines so (build 42139034765). Vanilla's
 * StructureTemplate lays a structure the same way these tests ask for: in each layer the full cubes first, then the rest.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class LayOrderGameTests {

    private static boolean fullCube(BlockState bs) {
        return bs.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    /**
     * Every structure every building can wear comes out of the loader layer by layer, bottom up, and in each layer
     * every full cube before any other block. On main the loader kept each file's own order: the generated files list
     * a layer by z then x (a vine at z 2 before the pier at z 3 it hangs on), and files saved by a structure block list
     * all full cubes first, then everything else, layers mixed.
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 100)
    public static void every_structure_lays_its_full_blocks_first_in_each_layer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> wrong = new ArrayList<>();
        int structures = 0;
        for (Building b : ReignOfNetherRegistries.BUILDING) {
            for (String name : BiomeBaseGameTests.structuresOf(b)) {
                List<BuildingBlock> blocks = BuildingBlockData.getBuildingBlocksFromNbt(name, level);
                if (blocks.isEmpty())
                    continue;
                structures++;
                String why = firstOutOfOrder(blocks);
                if (why != null)
                    wrong.add(name + ": " + why);
            }
        }
        if (structures < 20)
            helper.fail("only " + structures + " structures loaded");
        else if (!wrong.isEmpty())
            helper.fail(wrong.size() + " of " + structures + " structures lay a block out of order: "
                + String.join("; ", wrong.subList(0, Math.min(5, wrong.size()))));
        else
            helper.succeed();
    }

    /** Why the list is out of lay order (a layer below one already laid, or a full cube after a non-full block in its
     *  layer), or null when it is in order. */
    static String firstOutOfOrder(List<BuildingBlock> blocks) {
        int y = Integer.MIN_VALUE;
        BlockState nonFull = null;
        BlockPos nonFullAt = null;
        for (BuildingBlock b : blocks) {
            BlockPos p = b.getBlockPos();
            BlockState bs = b.getBlockState();
            if (p.getY() < y)
                return "layer y" + p.getY() + " comes after layer y" + y;
            if (p.getY() > y) {
                y = p.getY();
                nonFull = null;
            }
            if (fullCube(bs) && nonFull != null)
                return bs.getBlock() + " at " + p.toShortString() + " after " + nonFull.getBlock() + " at "
                    + nonFullAt.toShortString();
            if (!fullCube(bs) && nonFull == null) {
                nonFull = bs;
                nonFullAt = p;
            }
        }
        return null;
    }

    /**
     * A structure that lists a vine before the stone it hangs on, with a stair beside the vine (the round-13 Heartwood
     * Hall's eave corner, in three blocks), laid in the order the loader gives, with block updates: the vine still
     * stands. On main the stair, set next, told the vine its face was bare and the vine turned to air.
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void a_vine_listed_before_its_support_still_stands_once_laid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.SOUTH, true);
        BlockState stair = Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
        BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
        // the file order a generator writes (y, then z, then x): the vine, the stair beside it, then the stone south
        List<BuildingBlock> laid = BuildingBlockData.getBuildingBlocksFromNbt(structure(
            new BlockState[] { vine, stair, stone },
            new int[][] { { 0, 0, 0 }, { 1, 0, 0 }, { 0, 0, 1 } }));
        if (laid.size() != 3) {
            helper.fail("the loader gave " + laid.size() + " blocks, expected 3");
            return;
        }
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        BlockPos origin = new BlockPos(base.getX() + 200, 226, base.getZ() + 200);
        // 200 blocks off over other tests' arenas, put back when the test ends, pass or fail
        TestCleanup.BlockUndo undo = TestCleanup.blocks(helper);
        undo.region(origin.offset(-1, -1, -1), origin.offset(2, 1, 2));
        for (BuildingBlock b : laid)
            level.setBlockAndUpdate(origin.offset(b.getBlockPos()), b.getBlockState());
        List<String> order = new ArrayList<>();
        for (BuildingBlock b : laid)
            order.add(b.getBlockState().getBlock().toString());
        BlockState now = level.getBlockState(origin);
        if (!now.is(Blocks.VINE))
            helper.fail("the vine turned to " + now + " when laid in the order " + order);
        else if (!level.getBlockState(origin.offset(0, 0, 1)).is(Blocks.STONE_BRICKS)
            || !level.getBlockState(origin.offset(1, 0, 0)).is(Blocks.STONE_BRICK_STAIRS))
            helper.fail("the stone or the stair is missing after the lay " + order);
        else
            helper.succeed();
    }

    /** A structure .nbt as a generator writes it: the palette, then the blocks in the order given. */
    static CompoundTag structure(BlockState[] states, int[][] at) {
        CompoundTag nbt = new CompoundTag();
        ListTag palette = new ListTag();
        ListTag blocks = new ListTag();
        for (int i = 0; i < states.length; i++) {
            palette.add(NbtUtils.writeBlockState(states[i]));
            CompoundTag block = new CompoundTag();
            block.putInt("state", i);
            ListTag pos = new ListTag();
            for (int c : at[i])
                pos.add(IntTag.valueOf(c));
            block.put("pos", pos);
            blocks.add(block);
        }
        ListTag size = new ListTag();
        for (int c : new int[] { 2, 1, 2 })
            size.add(IntTag.valueOf(c));
        nbt.put("size", size);
        nbt.put("palette", palette);
        nbt.put("blocks", blocks);
        nbt.put("entities", new ListTag());
        return nbt;
    }
}
