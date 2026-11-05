package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * unit/Separation: the push that spreads overlapping units bypasses the pathfinder, so it must check the ground it
 * moves a unit onto. The bot soak's Gravebound commander (stacked with its two starting workers) was pushed into lava
 * 10 blocks from its start and died 16 s in (Oct 21, main 223a4bb).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class SeparationGameTests {

    /** Two stacked units at a lava pool's edge: the one nearer the lava is never pushed into it. */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 140)
    public static void separation_never_pushes_a_unit_into_lava(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_separation_lava_" + helper.absolutePos(BlockPos.ZERO).asLong();
        // a lava pool level with the floor, east of x=6 (rows z 6..10)
        for (int x = 6; x <= 9; x++)
            for (int z = 6; z <= 10; z++)
                helper.setBlock(new BlockPos(x, 1, z), Blocks.LAVA);
        VindicatorUnit edge = EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        VindicatorUnit inner = EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (edge == null || inner == null) {
            helper.fail("could not create the units");
            return;
        }
        BlockPos o = helper.absolutePos(new BlockPos(0, 2, 0));
        // overlapping, the edge unit on the lava side: the push would send it east, into the pool
        edge.moveTo(o.getX() + 5.75, o.getY(), o.getZ() + 8.5, 0, 0);
        inner.moveTo(o.getX() + 5.35, o.getY(), o.getZ() + 8.5, 0, 0);
        edge.setOwnerName(owner);
        inner.setOwnerName(owner);
        inner.setHoldPosition(true);   // the inner one holds, so only the edge unit is ever pushed
        level.addFreshEntity(edge);
        level.addFreshEntity(inner);
        float full = edge.getMaxHealth();
        helper.runAfterDelay(100, () -> {
            boolean burned = edge.isInLava() || edge.getRemainingFireTicks() > 0 || !edge.isAlive()
                || edge.getHealth() < full;
            String where = String.format("%.2f/%.2f/%.2f", edge.getX() - o.getX(), edge.getY() - o.getY(), edge.getZ() - o.getZ());
            edge.discard();
            inner.discard();
            for (int x = 6; x <= 9; x++)
                for (int z = 6; z <= 10; z++)
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            if (burned)
                helper.fail("the separation push moved the edge unit into the lava (now at " + where + ")");
            else
                helper.succeed();
        });
    }

    static final int EDGE_CROWD = 8, EDGE_CROWD_WATCH = 300;

    /**
     * Units crowding one cell next to a drop they cannot climb back up are not shoved off it. The arena is a plinth two
     * blocks above the flat ground round it; graveyard_l3_units_get_out_past_a_building_across_half_the_gate sends five
     * Zombies to a rally cell one block from its west edge, and one ended in the trench outside, two blocks down and 11
     * from the rally, with nothing to walk it back (PR 516 CI 41959204992, shard 8; graveyard HALF_BLOCKED likewise in
     * 41810782156). A match has drops like it: a ridge, a wall top. Eight Zombies, the match pathfinder on, all sent to
     * the cell at x 1, z 3: every one must still stand on the plinth when they have settled.
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = EDGE_CROWD_WATCH + 100, batch = "edge_crowd")
    public static void units_crowding_a_cell_by_a_two_block_drop_are_not_pushed_off_it(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Runnable giveBackPathing = RtsPathingLease.takeUntilEnd(helper);
        String owner = "gametest_separation_edge_" + helper.absolutePos(BlockPos.ZERO).asLong();
        BlockPos o = helper.absolutePos(new BlockPos(0, 2, 0));
        BlockPos cell = helper.absolutePos(new BlockPos(1, 2, 3));
        java.util.List<com.solegendary.reignofnether.unit.units.monsters.ZombieUnit> crowd = new java.util.ArrayList<>();
        TestCleanup.atEnd(helper, () -> {
            for (var z : crowd)
                z.discard();
            giveBackPathing.run();
        });
        for (int i = 0; i < EDGE_CROWD; i++) {
            var z = EntityRegistrar.ZOMBIE_UNIT.get().create(level);
            if (z == null) {
                helper.fail("could not create the units");
                return;
            }
            z.moveTo(o.getX() + 6.5 + (i % 4) * 0.9, o.getY(), o.getZ() + 2.5 + (i / 4) * 0.9, 0, 0);
            z.setOwnerName(owner);
            z.setInvulnerable(true);
            level.addFreshEntity(z);
            com.solegendary.reignofnether.unit.EngagementStance.set(z, com.solegendary.reignofnether.unit.EngagementStance.HOLD_FIRE);
            crowd.add(z);
        }
        helper.runAfterDelay(5, () -> {
            for (var z : crowd)
                ((com.solegendary.reignofnether.unit.interfaces.Unit) z).getMoveGoal().setMoveTarget(cell);
        });
        helper.runAfterDelay(EDGE_CROWD_WATCH, () -> {
            java.util.List<String> off = new java.util.ArrayList<>();
            for (var z : crowd) {
                double rx = z.getX() - o.getX(), ry = z.getY() - o.getY(), rz = z.getZ() - o.getZ();
                if (rx < 0 || rx > 16 || rz < 0 || rz > 16 || ry < -0.5)
                    off.add(String.format(java.util.Locale.ROOT, "#%d at %.1f/%.1f/%.1f", z.getId(), rx, ry, rz));
            }
            ReignOfNether.LOGGER.info("[EdgeCrowd] GameTest: {} of {} off the plinth: {}", off.size(), EDGE_CROWD, off);
            if (!off.isEmpty())
                helper.fail(off.size() + " of " + EDGE_CROWD + " Zombies sent to the cell at x 1, z 3 (1 block from a two-block drop) were"
                    + " pushed off it (arena-relative x/y/z, the plinth is x 0..16, y 0): " + String.join("; ", off));
            else
                helper.succeed();
        });
    }
}
