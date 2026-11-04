package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.buildings.defence.Defences;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Where a builder walks to build (BuildRepairGoal.calcMoveTarget -> BuildingPlacement.getClosestGroundPos): the ring
 * cell one outside the footprint nearest the builder, taken at the foundation's height (minCorner.y). Backlog item 6
 * (Command Center Eris.11, Nov 4), bots_play_each_other_without_errors on #505's run 41913285997: the Stormcallers
 * bot's capitol stalled half built, its builder "at 2714, 74, 159 ... approach 2723, 78, 167, ... path done,
 * backoff true, stalled 218 ticks" - the approach cell four blocks above the ground the builder walks, where the
 * terrain falls away from the levelled site, so its path ended short and it never came in range. A cell nobody can
 * stand in must lose to one a builder can reach.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BuildingApproachGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** Ground a unit could stand at for this cell, read either way the ring's y may be meant (on the floor, or in the feet). */
    static boolean standable(ServerLevel level, BlockPos c) {
        boolean onFloor = !level.getBlockState(c).getCollisionShape(level, c).isEmpty()
            && level.getBlockState(c.above()).getCollisionShape(level, c.above()).isEmpty()
            && level.getBlockState(c.above(2)).getCollisionShape(level, c.above(2)).isEmpty();
        boolean inFeet = !level.getBlockState(c.below()).getCollisionShape(level, c.below()).isEmpty()
            && level.getBlockState(c).getCollisionShape(level, c).isEmpty()
            && level.getBlockState(c.above()).getCollisionShape(level, c.above()).isEmpty();
        return onFloor || inFeet;
    }

    /**
     * A site on a ledge: ground under the footprint and three sides of its ring, the fourth side (-x) open air - a
     * drop. A builder coming from below the drop must be given a ring cell it can stand in, not the one in the air.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void a_builder_coming_from_below_a_ledge_is_sent_to_ground_it_can_stand_on(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        BlockPos origin = new BlockPos(base.getX(), 220, base.getZ());
        BuildingPlacement site = BuildingUtils.getNewBuildingPlacement(Defences.VILLAGERS.raidTower(), level, origin,
            Rotation.NONE, "gametest_approach_" + helper.absolutePos(BlockPos.ZERO).asLong(), false);
        if (site == null) {
            helper.fail("could not make the raid tower placement");
            return;
        }
        int x0 = site.minCorner.getX(), x1 = site.maxCorner.getX(), z0 = site.minCorner.getZ(), z1 = site.maxCorner.getZ();
        int y = site.minCorner.getY();
        TestCleanup.blocks(helper).region(new BlockPos(x0 - 3, y - 2, z0 - 3), new BlockPos(x1 + 3, y + 3, z1 + 3));
        for (int x = x0 - 3; x <= x1 + 3; x++)
            for (int z = z0 - 3; z <= z1 + 3; z++)
                for (int k = -2; k <= 3; k++)
                    level.setBlock(new BlockPos(x, y + k, z), Blocks.AIR.defaultBlockState(), 2);
        // the ledge: two layers of stone under the footprint and its ring, except the ring's -x side (and beyond)
        for (int x = x0 - 1; x <= x1 + 1; x++)
            for (int z = z0 - 1; z <= z1 + 1; z++)
                if (x >= x0)
                    for (int k = -1; k <= 0; k++)
                        level.setBlock(new BlockPos(x, y + k, z), Blocks.STONE.defaultBlockState(), 2);
        int zMid = (z0 + z1) / 2;
        BlockPos builder = new BlockPos(x0 - 6, y - 4, zMid);   // below the drop, on the -x side
        BlockPos cell = site.getClosestGroundPos(builder, 1, false);
        boolean onRing = (cell.getX() == x0 - 1 || cell.getX() == x1 + 1 || cell.getZ() == z0 - 1 || cell.getZ() == z1 + 1)
            && cell.getX() >= x0 - 1 && cell.getX() <= x1 + 1 && cell.getZ() >= z0 - 1 && cell.getZ() <= z1 + 1;
        if (!onRing)
            helper.fail("the approach cell " + cell.toShortString() + " is not on the ring round the footprint "
                + x0 + ".." + x1 + " x " + z0 + ".." + z1);
        else if (!standable(level, cell))
            helper.fail("a builder at " + builder.toShortString() + " was sent to " + cell.toShortString()
                + ", open air over the drop (the foundation's y " + y + "); a ring cell on the ledge was free");
        else
            helper.succeed();
    }

    /** On flat ground nothing changes: the nearest ring cell, as before. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void on_flat_ground_the_nearest_ring_cell_is_still_the_approach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        BlockPos origin = new BlockPos(base.getX(), 220, base.getZ());
        BuildingPlacement site = BuildingUtils.getNewBuildingPlacement(Defences.VILLAGERS.raidTower(), level, origin,
            Rotation.NONE, "gametest_approach_flat_" + helper.absolutePos(BlockPos.ZERO).asLong(), false);
        if (site == null) {
            helper.fail("could not make the raid tower placement");
            return;
        }
        int x0 = site.minCorner.getX(), x1 = site.maxCorner.getX(), z0 = site.minCorner.getZ(), z1 = site.maxCorner.getZ();
        int y = site.minCorner.getY();
        TestCleanup.blocks(helper).region(new BlockPos(x0 - 3, y - 2, z0 - 3), new BlockPos(x1 + 3, y + 3, z1 + 3));
        for (int x = x0 - 3; x <= x1 + 3; x++)
            for (int z = z0 - 3; z <= z1 + 3; z++)
                for (int k = -2; k <= 3; k++)
                    level.setBlock(new BlockPos(x, y + k, z), k <= 0 ? Blocks.STONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState(), 2);
        int zMid = (z0 + z1) / 2;
        BlockPos cell = site.getClosestGroundPos(new BlockPos(x0 - 6, y, zMid), 1, false);
        if (cell.getX() != x0 - 1 || cell.getZ() != zMid || cell.getY() != y)
            helper.fail("on flat ground the approach from -x should be the ring cell " + (x0 - 1) + ", " + y + ", " + zMid
                + ", got " + cell.toShortString());
        else
            helper.succeed();
    }
}
