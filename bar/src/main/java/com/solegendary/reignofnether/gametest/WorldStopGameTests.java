package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.blocks.BlockServerEvents;
import com.solegendary.reignofnether.blocks.TemporaryBlock;
import com.solegendary.reignofnether.hero.HeroServerEvents;
import com.solegendary.reignofnether.unit.HeroUnitSave;
import com.solegendary.reignofnether.unit.TargetResourcesSave;
import com.solegendary.reignofnether.unit.UnitServerEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * What the server stopping leaves behind for the next world (security audit F4/F5, Oct 26). Static lists outlived the
 * server: quitting world A and opening world B let A's fallen heroes be revived in B (and saved into it), A's pending
 * blocks were placed in B, and A kept its temporary blocks for good. These run the stop handlers' own clear steps -
 * not the whole stop, which would wipe every other test's state - each in a batch of its own (nothing runs beside
 * them), and put back whatever other tests had in the hero and gather lists.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class WorldStopGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    @GameTest(template = ARENA, batch = "world_stop_unit_lists", timeoutTicks = 20)
    public static void fallen_heroes_and_gather_targets_do_not_outlive_the_server(GameTestHelper helper) {
        List<HeroUnitSave> heroesBefore = new ArrayList<>(HeroServerEvents.fallenHeroes);
        List<TargetResourcesSave> targetsBefore;
        synchronized (UnitServerEvents.savedTargetResources) {
            targetsBefore = new ArrayList<>(UnitServerEvents.savedTargetResources);
        }
        List<String> problems = new ArrayList<>();
        try {
            HeroServerEvents.fallenHeroes.add(new HeroUnitSave("00000000-0000-0000-0000-0000000000f4", "gt_world_a_hero",
                "gt_world_a_owner", 0, 1, 0, 1, 0, 0, 0, null));
            synchronized (UnitServerEvents.savedTargetResources) {
                UnitServerEvents.savedTargetResources.add(new TargetResourcesSave());
            }
            UnitServerEvents.clearWorldLists();
            if (!HeroServerEvents.fallenHeroes.isEmpty())
                problems.add(HeroServerEvents.fallenHeroes.size() + " fallen hero(es) left for the next world");
            synchronized (UnitServerEvents.savedTargetResources) {
                if (!UnitServerEvents.savedTargetResources.isEmpty())
                    problems.add(UnitServerEvents.savedTargetResources.size() + " gather target(s) left for the next world");
            }
        } finally {
            HeroServerEvents.fallenHeroes.clear();
            HeroServerEvents.fallenHeroes.addAll(heroesBefore);
            synchronized (UnitServerEvents.savedTargetResources) {
                UnitServerEvents.savedTargetResources.clear();
                UnitServerEvents.savedTargetResources.addAll(targetsBefore);
            }
        }
        TerrainGameTests.finish(helper, problems);
    }

    @GameTest(template = ARENA, batch = "world_stop_temp_blocks", timeoutTicks = 60)
    public static void temporary_and_pending_blocks_are_settled_and_cleared_on_stop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos temp = helper.absolutePos(new BlockPos(4, 3, 4));
        BlockPos pending = helper.absolutePos(new BlockPos(8, 3, 8));
        level.setBlockAndUpdate(temp, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pending, Blocks.AIR.defaultBlockState());
        // a long-lived temporary block (as an ice wall or a web is), placed by the world tick within a few ticks
        BlockServerEvents.addTempBlock(level, temp, Blocks.GLASS.defaultBlockState(), Blocks.AIR.defaultBlockState(), 2000, true);
        helper.runAfterDelay(15, () -> {
            List<String> problems = new ArrayList<>();
            if (level.getBlockState(temp).getBlock() != Blocks.GLASS) {
                helper.fail("test setup: the temporary block was not placed (" + level.getBlockState(temp) + ")");
                return;
            }
            TemporaryBlock ours = null;
            for (TemporaryBlock tb : BlockServerEvents.tempBlocks)
                if (tb.bp.equals(temp))
                    ours = tb;
            if (ours == null) {
                helper.fail("test setup: the temporary block is not in the list");
                return;
            }
            // a pending placement and the stop in one step, so no world tick places it in between
            BlockServerEvents.blocksToPlace.put(pending, Blocks.STONE.defaultBlockState());
            BlockServerEvents.revertAndClear(level);
            if (!BlockServerEvents.tempBlocks.isEmpty())
                problems.add(BlockServerEvents.tempBlocks.size() + " temporary block(s) left for the next world");
            if (!BlockServerEvents.blocksToPlace.isEmpty())
                problems.add(BlockServerEvents.blocksToPlace.size() + " pending block(s) left for the next world");
            if (!level.getBlockState(temp).isAir())
                problems.add("the temporary block was not reverted: " + level.getBlockState(temp));
            if (level.getBlockState(pending).getBlock() != Blocks.STONE)
                problems.add("the pending block was not placed: " + level.getBlockState(pending));
            TerrainGameTests.finish(helper, problems);
        });
    }
}
