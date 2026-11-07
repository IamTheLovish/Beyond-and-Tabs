package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.Buildings;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * lovish: "buildings should not make exploding effects when hit, only when exploding." A hit that knocks blocks off a
 * building (units, or a player breaking a block) must set off no explosion and start no fire; the building's own
 * destruction still blasts.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BuildingHitNoExplosionGameTests {

    static final String BATCH = "building_hit_no_boom";
    // Barracks rolled a 20% explosion per lost block: 60 losses would fire one with probability 1 - 0.8^60 (> 99.9%)
    static final int HITS = 60;

    static BuildingPlacement built(GameTestHelper helper) {
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String owner = "gametest_nohit_" + helper.absolutePos(BlockPos.ZERO).asLong();
        return PartialDestroyGameTests.site(helper, Buildings.BARRACKS, owner, 5, 5, 1f);
    }

    /** Runs action with a counter of explosions in this test's level; returns the count. */
    static int explosionsDuring(GameTestHelper helper, Runnable action) {
        int[] count = { 0 };
        Consumer<ExplosionEvent.Detonate> listener = e -> {
            if (e.getLevel() == helper.getLevel())
                count[0]++;
        };
        MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            action.run();
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
        }
        return count[0];
    }

    static int fireBlocks(GameTestHelper helper, BuildingPlacement bp) {
        int n = 0;
        for (int x = bp.minCorner.getX() - 3; x <= bp.maxCorner.getX() + 3; x++)
            for (int y = bp.minCorner.getY(); y <= bp.maxCorner.getY() + 3; y++)
                for (int z = bp.minCorner.getZ() - 3; z <= bp.maxCorner.getZ() + 3; z++)
                    if (helper.getLevel().getBlockState(new BlockPos(x, y, z)).getBlock() instanceof BaseFireBlock)
                        n++;
        return n;
    }

    /** Unit-style hits (destroyRandomBlocks, one block each) cause no explosion and no fire. */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 100, batch = BATCH)
    public static void unit_hits_on_a_building_make_no_explosion(GameTestHelper helper) {
        BuildingPlacement bp = built(helper);
        int before = bp.getBlocksPlaced();
        int booms = explosionsDuring(helper, () -> {
            for (int i = 0; i < HITS; i++)
                bp.destroyRandomBlocks(bp.getHealthPerBlock() / 2d);
        });
        int lost = before - bp.getBlocksPlaced(), fires = fireBlocks(helper, bp);
        BuildingServerEvents.getBuildings().remove(bp);
        if (lost < HITS / 2)
            helper.fail("setup: only " + lost + " blocks were knocked off in " + HITS + " hits");
        else if (booms > 0 || fires > 0)
            helper.fail(booms + " explosions and " + fires + " fire blocks from " + lost + " lost blocks");
        else
            helper.succeed();
    }

    /** A player breaking the building's blocks (onBlockBreak with breakBlocks = true) causes no explosion either. */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 100, batch = BATCH)
    public static void player_broken_blocks_make_no_explosion(GameTestHelper helper) {
        BuildingPlacement bp = built(helper);
        List<BlockPos> spots = new ArrayList<>();
        for (BuildingBlock b : bp.getBlocks())
            if (!b.getBlockState().isAir() && spots.size() < HITS)
                spots.add(b.getBlockPos());
        int booms = explosionsDuring(helper, () -> {
            for (BlockPos p : spots)
                bp.onBlockBreak(helper.getLevel(), p, true);
        });
        int fires = fireBlocks(helper, bp);
        BuildingServerEvents.getBuildings().remove(bp);
        if (spots.size() < HITS)
            helper.fail("setup: only " + spots.size() + " blocks to break");
        else if (booms > 0 || fires > 0)
            helper.fail(booms + " explosions and " + fires + " fire blocks from " + spots.size() + " broken blocks");
        else
            helper.succeed();
    }

    /** The building's destruction still blasts (the collapse's spread-out explosions). */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 100, batch = BATCH)
    public static void destroying_a_building_still_explodes(GameTestHelper helper) {
        BuildingPlacement bp = built(helper);
        int booms = explosionsDuring(helper, () -> bp.destroy(helper.getLevel()));
        BuildingServerEvents.getBuildings().remove(bp);
        if (booms <= 0)
            helper.fail("destroying a built Barracks made no explosion");
        else
            helper.succeed();
    }
}
