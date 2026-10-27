package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.EngagementStance;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.command.CommandQueues;
import com.solegendary.reignofnether.unit.command.QueueMode;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.pathfinding.VanillaPathBudget;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The Oct 22 pathing perf pass (vanilla A* for units) must not change how units move:
 * <ul>
 *   <li>a following melee chaser (Zombie Villager: chases even unseen) now repaths only on its tick() throttle, not
 *       every goal-cleanup pass - it must still run down a target that walks away;</li>
 *   <li>a shooter keeps its path while the target moved less than a block and a half (Engage.keepPath) instead of a
 *       fresh A* every tick - it must still close to its range on a target that shifts;</li>
 *   <li>the units' first vanilla search is distance-scaled (VanillaPathBudget) - a detour round a wall far longer
 *       than that range must still be found through the one full-range fallback.</li>
 * </ul>
 * Vanilla pathfinding (the rtsPathfinding gamerule off), as on a world with no battlefield arena and in GameTests.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class PathingBudgetGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    // its own batch: it caps the short search server-wide for its run (batches run one at a time)
    static final String BATCH_FALLBACK = "pathing_budget_fallback";

    static String owner(GameTestHelper helper, String tag) {
        return "gametest_pathbudget_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    static Mob unit(ServerLevel level, net.minecraft.world.entity.EntityType<? extends Mob> type, String owner,
                    double x, double y, double z, List<Entity> made) {
        Mob m = TerrainGameTests.spawn(level, type, owner, x, y, z, made);
        if (m != null) {
            EngagementStance.set(m, EngagementStance.HOLD_FIRE);   // nothing picks a fight with a neighbouring arena
            m.setInvulnerable(true);
        }
        return m;
    }

    static void attack(ServerLevel level, String owner, Mob attacker, Mob target) {
        CommandQueues.issue(level, owner, UnitAction.ATTACK, target.getId(), new int[] { attacker.getId() },
            BlockPos.ZERO, BlockPos.ZERO, QueueMode.REPLACE);
    }

    /**
     * A Zombie Villager is ordered onto a Vindicator that then walks 11 blocks away at 60% of the chaser's speed. The
     * chaser must catch it (inside its melee reach) after the target has got at least 5 blocks from where it stood.
     */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void vanilla_path_melee_chaser_runs_down_a_walking_target(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String mine = owner(helper, "chase"), theirs = owner(helper, "chase_foe");
        BlockPos o = helper.absolutePos(new BlockPos(0, 2, 0));
        List<Entity> made = new ArrayList<>();
        Mob chaser = unit(level, EntityRegistrar.ZOMBIE_VILLAGER_UNIT.get(), mine, o.getX() + 1.5, o.getY(), o.getZ() + 2.5, made);
        Mob runner = unit(level, EntityRegistrar.VINDICATOR_UNIT.get(), theirs, o.getX() + 4.5, o.getY(), o.getZ() + 2.5, made);
        if (chaser == null || runner == null) {
            TerrainGameTests.cleanUp(made);
            helper.fail("could not create the units");
            return;
        }
        runner.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(
            chaser.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue() * 0.6);
        BlockPos away = helper.absolutePos(new BlockPos(14, 2, 13));
        double sx = runner.getX(), sz = runner.getZ();
        int[] ordered = { -1 };
        boolean[] done = { false };
        ArenaReady.whenTicking(helper, made, 150, tick -> {
            ordered[0] = tick;
            OrdersObeyGameTests.move(level, theirs, runner, away);
            attack(level, mine, chaser, runner);
        });
        helper.onEachTick(() -> {
            if (ordered[0] < 0 || done[0])
                return;
            long t = helper.getTick() - ordered[0];
            double moved = Math.hypot(runner.getX() - sx, runner.getZ() - sz);
            double apart = TerrainGameTests.horizontal(chaser, runner);
            double w = chaser.getBbWidth() + ((AttackerUnit) chaser).getBonusMeleeRange();   // the goal's own reach
            double reach = Math.sqrt(w * 2.0 * w * 2.0 + runner.getBbWidth());
            boolean caught = moved >= 5 && apart <= reach + 0.3;
            if (caught || t > 300) {
                done[0] = true;
                List<String> problems = new ArrayList<>();
                if (!caught)
                    problems.add("the chaser did not catch a target walking away at 60% of its speed in 300 ticks ("
                        + TerrainGameTests.f(apart) + " apart, reach " + TerrainGameTests.f(reach) + ", target moved "
                        + TerrainGameTests.f(moved) + ", chaser target " + (chaser.getTarget() == runner ? "the runner" : String.valueOf(chaser.getTarget()))
                        + ", nav done " + chaser.getNavigation().isDone() + "; " + ArenaReady.describe(level, made)
                        + "; chaser " + ArenaReady.around(chaser, runner) + ")");
                try {
                    TerrainGameTests.finish(helper, problems);
                } finally {
                    TerrainGameTests.cleanUp(made);
                }
            }
        });
    }

    /**
     * A Skeleton (range 12) is ordered onto a target standing across the arena's diagonal (~18 blocks). The target is
     * shifted by one block (inside the keep-path drift) while the shooter walks, and later by two (outside it). The
     * shooter must end within its range of where the target really is - a kept path must never leave it short.
     */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void vanilla_path_shooter_closes_to_range_on_a_shifting_target(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String mine = owner(helper, "range"), theirs = owner(helper, "range_foe");
        BlockPos o = helper.absolutePos(new BlockPos(0, 2, 0));
        List<Entity> made = new ArrayList<>();
        Mob shooter = unit(level, EntityRegistrar.SKELETON_UNIT.get(), mine, o.getX() + 1.5, o.getY(), o.getZ() + 1.5, made);
        Mob target = TerrainGameTests.dummy(level, EntityRegistrar.VINDICATOR_UNIT.get(), theirs, o.getX() + 13.5, o.getY(), o.getZ() + 13.5, made);
        if (shooter == null || target == null) {
            TerrainGameTests.cleanUp(made);
            helper.fail("could not create the units");
            return;
        }
        float range = ((AttackerUnit) shooter).getAttackRange();
        int[] ordered = { -1 };
        boolean[] done = { false };
        ArenaReady.whenTicking(helper, made, 150, tick -> {
            ordered[0] = tick;
            attack(level, mine, shooter, target);
        });
        helper.onEachTick(() -> {
            if (ordered[0] < 0 || done[0])
                return;
            long t = helper.getTick() - ordered[0];
            if (t == 10)
                target.teleportTo(o.getX() + 14.5, o.getY(), o.getZ() + 13.5);
            if (t == 40)
                target.teleportTo(o.getX() + 14.5, o.getY(), o.getZ() + 11.5);
            if (t < 60)
                return;
            double d = shooter.distanceTo(target);
            boolean inRange = d <= range;
            if (inRange || t > 300) {
                done[0] = true;
                List<String> problems = new ArrayList<>();
                if (!inRange)
                    problems.add("the shooter did not close to its range (" + TerrainGameTests.f(d) + " > " + range
                        + ") in 300 ticks; nav done " + shooter.getNavigation().isDone() + ", target "
                        + (shooter.getTarget() == target ? "the dummy" : String.valueOf(shooter.getTarget()))
                        + "; " + ArenaReady.describe(level, made));
                try {
                    TerrainGameTests.finish(helper, problems);
                } finally {
                    TerrainGameTests.cleanUp(made);
                }
            }
        });
    }

    /**
     * A wall across the arena (x 7, z 0..13, three high) between a Vindicator at x 2 and its move spot at x 13, the
     * only way round at z 14-15: an 11-block move whose path is ~30. The short search is capped at 6 blocks for this
     * run, so it can never find the way round; the move must still arrive (the full-range fallback) and the fallback
     * counter must show it ran.
     */
    @GameTest(template = ARENA, batch = BATCH_FALLBACK, timeoutTicks = 500)
    public static void vanilla_path_wall_detour_beyond_the_short_search_still_arrives(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        boolean wasRts = UnitServerEvents.rtsPathfinding;
        UnitServerEvents.rtsPathfinding = false;
        VanillaPathBudget.shortRangeCapForTests = 6f;
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        for (int z = 0; z <= 13; z++)
            for (int y = 2; y <= 4; y++)
                helper.setBlock(new BlockPos(7, y, z), Blocks.STONE);
        // a fence round the arena (x and z -1 and 16): the full-range fallback searches 64 blocks, and without one it
        // routed through the generated ground between test slots - main 5421b13f ended at x 16.30, off the arena,
        // still walking (Oct 26). That ground depends on the slot the batch gives this test, so it passed or failed
        // by layout; now the only way round is the arena's own gap at z 14-15
        // (what stood there is put back in restore: the fence is outside the arena, where later batches would find it)
        java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> under = new java.util.LinkedHashMap<>();
        for (int i = -1; i <= 16; i++)
            for (int y = 2; y <= 4; y++)
                for (BlockPos rel : new BlockPos[] { new BlockPos(i, y, -1), new BlockPos(i, y, 16), new BlockPos(-1, y, i), new BlockPos(16, y, i) }) {
                    BlockPos abs = helper.absolutePos(rel);
                    if (under.putIfAbsent(abs, level.getBlockState(abs)) == null)
                        level.setBlock(abs, Blocks.STONE.defaultBlockState(), 3);
                }
        String mine = owner(helper, "detour");
        BlockPos o = helper.absolutePos(new BlockPos(0, 2, 0));
        List<Entity> made = new ArrayList<>();
        Mob m = unit(level, EntityRegistrar.VINDICATOR_UNIT.get(), mine, o.getX() + 2.5, o.getY(), o.getZ() + 4.5, made);
        Runnable restore = () -> {
            UnitServerEvents.rtsPathfinding = wasRts;
            VanillaPathBudget.shortRangeCapForTests = Float.MAX_VALUE;
            under.forEach((abs, state) -> level.setBlock(abs, state, 3));
        };
        if (m == null) {
            restore.run();
            TerrainGameTests.cleanUp(made);
            helper.fail("could not create the vindicator");
            return;
        }
        BlockPos to = helper.absolutePos(new BlockPos(13, 2, 4));
        long[] fallbacksBefore = { 0 };
        int[] ordered = { -1 };
        boolean[] done = { false };
        ArenaReady.whenTicking(helper, made, 150, tick -> {
            ordered[0] = tick;
            fallbacksBefore[0] = VanillaPathBudget.fallbacksTotal;
            OrdersObeyGameTests.move(level, mine, m, to);
        });
        helper.onEachTick(() -> {
            if (ordered[0] < 0 || done[0])
                return;
            long t = helper.getTick() - ordered[0];
            boolean arrived = OrdersObeyGameTests.dist(m, to) < 1.6;
            if (arrived || t > 420) {
                done[0] = true;
                List<String> problems = new ArrayList<>();
                if (!arrived)
                    problems.add("the vindicator did not get round the wall in 420 ticks ("
                        + TerrainGameTests.f(OrdersObeyGameTests.dist(m, to)) + " from its spot, at x "
                        + TerrainGameTests.f(m.getX() - o.getX()) + " z " + TerrainGameTests.f(m.getZ() - o.getZ())
                        + ", nav done " + m.getNavigation().isDone() + "; " + ArenaReady.around(m, to.getX() + 0.5, to.getZ() + 0.5) + ")");
                if (VanillaPathBudget.fallbacksTotal == fallbacksBefore[0])
                    problems.add("no full-range fallback ran: the short search was not used or found the detour itself");
                try {
                    TerrainGameTests.finish(helper, problems);
                } finally {
                    restore.run();
                    TerrainGameTests.cleanUp(made);
                }
            }
        });
    }
}
