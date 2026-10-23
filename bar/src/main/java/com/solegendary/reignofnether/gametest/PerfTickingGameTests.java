package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Server-ticking trims (the Oct 22 profile): campfires and unlit blast furnaces get no server block-entity ticker,
 * and a unit's item-pickup query runs every second tick. None of it may be visible.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class PerfTickingGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** A lit campfire stays lit with no server ticker; a lit blast furnace is still put out by its first tick. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void campfires_stay_lit_without_a_server_ticker(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos camp = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos soul = helper.absolutePos(new BlockPos(4, 2, 2));
        BlockPos furnace = helper.absolutePos(new BlockPos(6, 2, 2));
        level.setBlock(camp, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true), 3);
        level.setBlock(soul, Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true), 3);
        level.setBlock(furnace, Blocks.BLAST_FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.LIT, true), 3);

        BlockState campState = level.getBlockState(camp);
        if (((EntityBlock) campState.getBlock()).getTicker(level, campState, BlockEntityType.CAMPFIRE) != null)
            helper.fail("a campfire still has a server ticker");
        BlockState unlit = Blocks.BLAST_FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.LIT, false);
        if (((EntityBlock) unlit.getBlock()).getTicker(level, unlit, BlockEntityType.BLAST_FURNACE) != null)
            helper.fail("an unlit blast furnace still has a server ticker");
        BlockState lit = level.getBlockState(furnace);
        if (((EntityBlock) lit.getBlock()).getTicker(level, lit, BlockEntityType.BLAST_FURNACE) != null)
            helper.fail("a lit blast furnace still has a server ticker");

        helper.runAfterDelay(30, () -> {
            if (!level.getBlockState(camp).getValue(CampfireBlock.LIT))
                helper.fail("the campfire went out");
            if (!level.getBlockState(soul).getValue(CampfireBlock.LIT))
                helper.fail("the soul campfire went out");
            // vanilla keeps a stored-lit, fuel-less furnace lit (LIT only flips when burn time crosses zero): same now
            if (!level.getBlockState(furnace).getValue(AbstractFurnaceBlock.LIT))
                helper.fail("the lit blast furnace went out");
            helper.succeed();
        });
    }

    /** A worker standing on a resource item still picks it up with the pickup query on every second tick. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void worker_still_picks_up_an_item(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VillagerUnit worker = EntityRegistrar.VILLAGER_UNIT.get().create(level);
        if (worker == null) {
            helper.fail("could not create a worker");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
        worker.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        worker.setOwnerName("gametest_pickup");
        level.addFreshEntity(worker);
        ItemEntity logs = new ItemEntity(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, new ItemStack(Items.OAK_LOG, 1));
        logs.setPickUpDelay(0);
        logs.setDeltaMovement(0, 0, 0);
        level.addFreshEntity(logs);
        helper.succeedWhen(() -> {
            if (!logs.isRemoved() || worker.getItems().isEmpty())
                helper.fail("the worker did not pick the log up");
            worker.discard();
        });
    }
}
