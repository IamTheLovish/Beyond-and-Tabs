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

    /** A raid tower placement at y 220 over this test's arena, with the box round it cleared; null after a fail. */
    static BuildingPlacement site(GameTestHelper helper, String tag) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        BuildingPlacement site = BuildingUtils.getNewBuildingPlacement(Defences.VILLAGERS.raidTower(), level,
            new BlockPos(base.getX(), 220, base.getZ()), Rotation.NONE,
            "gametest_approach_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong(), false);
        if (site == null) {
            helper.fail("could not make the raid tower placement");
            return null;
        }
        int x0 = site.minCorner.getX(), x1 = site.maxCorner.getX(), z0 = site.minCorner.getZ(), z1 = site.maxCorner.getZ();
        int y = site.minCorner.getY();
        TestCleanup.blocks(helper).region(new BlockPos(x0 - 3, y - 2, z0 - 3), new BlockPos(x1 + 3, y + 3, z1 + 3));
        for (int x = x0 - 3; x <= x1 + 3; x++)
            for (int z = z0 - 3; z <= z1 + 3; z++)
                for (int k = -2; k <= 3; k++)
                    level.setBlock(new BlockPos(x, y + k, z), Blocks.AIR.defaultBlockState(), 2);
        return site;
    }

    /**
     * Review round 1 on #514: when NO ring cell is standable (a site on a pillar: all four sides open air) the nearest
     * ring cell must still win, as on main. A 999999 start for the best distance let no cell carrying the 1e6
     * penalty win, and the helper returned minCorner - a cell inside the footprint (production units spawned in the
     * building, a bridge's builder sent to its far corner).
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void when_no_ring_cell_is_standable_the_nearest_ring_cell_still_wins(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BuildingPlacement site = site(helper, "pillar");
        if (site == null)
            return;
        int x0 = site.minCorner.getX(), x1 = site.maxCorner.getX(), z0 = site.minCorner.getZ(), z1 = site.maxCorner.getZ();
        int y = site.minCorner.getY();
        for (int x = x0; x <= x1; x++)   // the pillar: stone under the footprint only
            for (int z = z0; z <= z1; z++)
                for (int k = -2; k <= 0; k++)
                    level.setBlock(new BlockPos(x, y + k, z), Blocks.STONE.defaultBlockState(), 2);
        int zMid = (z0 + z1) / 2;
        BlockPos cell = site.getClosestGroundPos(new BlockPos(x0 - 6, y, zMid), 1, false);
        if (site.isPosInsideBuilding(cell))
            helper.fail("with every ring cell open air the approach is " + cell.toShortString()
                + ", inside the footprint " + x0 + ".." + x1 + " x " + z0 + ".." + z1 + "; the nearest ring cell is "
                + (x0 - 1) + ", " + y + ", " + zMid);
        else if (cell.getX() != x0 - 1 || cell.getZ() != zMid || cell.getY() != y)
            helper.fail("with every ring cell open air the approach from -x should be the nearest ring cell "
                + (x0 - 1) + ", " + y + ", " + zMid + ", got " + cell.toShortString());
        else
            helper.succeed();
    }

    /**
     * Review round 1 on #514: a carpet, a bottom slab or a few layers of snow on the ground round a site is ground a
     * unit stands on, not a wall. Round 2: only the ring's near side (-x) is covered and the rest is bare, so a rule that
     * counts a low block as a wall picks a bare cell further round, and the right rule the near covered cell.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void carpet_slab_or_snow_on_the_ground_is_still_ground_to_stand_on(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BuildingPlacement site = site(helper, "cover");
        if (site == null)
            return;
        int x0 = site.minCorner.getX(), x1 = site.maxCorner.getX(), z0 = site.minCorner.getZ(), z1 = site.maxCorner.getZ();
        int y = site.minCorner.getY();
        int zMid = (z0 + z1) / 2;
        var covers = new net.minecraft.world.level.block.state.BlockState[] {
            Blocks.WHITE_CARPET.defaultBlockState(),
            Blocks.STONE_SLAB.defaultBlockState(),   // a bottom slab
            Blocks.SNOW.defaultBlockState().setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, 4) };
        java.util.List<String> problems = new java.util.ArrayList<>();
        for (var cover : covers) {
            for (int x = x0 - 3; x <= x1 + 3; x++)
                for (int z = z0 - 3; z <= z1 + 3; z++)
                    for (int k = -2; k <= 3; k++)
                        level.setBlock(new BlockPos(x, y + k, z), k <= 0 ? Blocks.STONE.defaultBlockState()
                            : k == 1 && x == x0 - 1 ? cover   // the near side of the ring only
                            : Blocks.AIR.defaultBlockState(), 2);
            BlockPos cell = site.getClosestGroundPos(new BlockPos(x0 - 6, y, zMid), 1, false);
            if (cell.getX() != x0 - 1 || cell.getZ() != zMid || cell.getY() != y)
                problems.add(cover.getBlock().getDescriptionId() + " on the near side: approach " + cell.toShortString()
                    + ", expected the nearest ring cell " + (x0 - 1) + ", " + y + ", " + zMid + " (covered, standable)");
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * Review round 2 on #514: a bottom slab floating at head height over a ring cell (ground one below the cell, the cell
     * itself air) is a ceiling, not standing room: with only the near side roofed so, the approach is a bare cell.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void a_slab_at_head_height_over_a_ring_cell_is_no_room_to_stand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BuildingPlacement site = site(helper, "ceiling");
        if (site == null)
            return;
        int x0 = site.minCorner.getX(), x1 = site.maxCorner.getX(), z0 = site.minCorner.getZ(), z1 = site.maxCorner.getZ();
        int y = site.minCorner.getY();
        int zMid = (z0 + z1) / 2;
        for (int x = x0 - 3; x <= x1 + 3; x++)
            for (int z = z0 - 3; z <= z1 + 3; z++)
                for (int k = -2; k <= 3; k++)
                    level.setBlock(new BlockPos(x, y + k, z), k <= -1 ? Blocks.STONE.defaultBlockState()
                        : k == 1 && x == x0 - 1 ? Blocks.STONE_SLAB.defaultBlockState()   // a bottom slab at head height
                        : Blocks.AIR.defaultBlockState(), 2);
        BlockPos cell = site.getClosestGroundPos(new BlockPos(x0 - 6, y, zMid), 1, false);
        boolean onRing = (cell.getX() == x0 - 1 || cell.getX() == x1 + 1 || cell.getZ() == z0 - 1 || cell.getZ() == z1 + 1)
            && !site.isPosInsideBuilding(cell);
        if (!onRing)
            helper.fail("the approach " + cell.toShortString() + " is not on the ring round " + x0 + ".." + x1 + " x " + z0 + ".." + z1);
        else if (cell.getX() == x0 - 1)
            helper.fail("the approach " + cell.toShortString() + " is under the slab at head height (y " + (y + 1)
                + "); a bare ring cell was free");
        else
            helper.succeed();
    }
}
