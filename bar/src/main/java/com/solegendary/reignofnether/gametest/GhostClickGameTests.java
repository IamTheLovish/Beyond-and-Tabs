package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.Blueprints;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.buildings.defence.Defences;
import com.solegendary.reignofnether.gametest.BuilderAssistGameTests.Site;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.command.CommandQueue;
import com.solegendary.reignofnether.unit.command.CommandQueues;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.command.QueueMode;
import com.solegendary.reignofnether.unit.command.UnitCommand;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

import static com.solegendary.reignofnether.gametest.BuilderAssistGameTests.building;
import static com.solegendary.reignofnether.gametest.BuilderAssistGameTests.finish;
import static com.solegendary.reignofnether.gametest.BuilderAssistGameTests.targetOf;
import static com.solegendary.reignofnether.gametest.BuilderAssistGameTests.where;

/**
 * "You cannot click on blueprint ghosts to assist production" (lane C): a ghost has no blocks, so the client's picked
 * block is the ground behind/under it; the client now finds the ghost by its box (GhostHit, JUnit GhostHitTest) and
 * sends BUILD_REPAIR aimed at the ghost's CENTRE (UnitClientEvents, sendUnitCommandAt). These check the server side of
 * exactly that packet: CommandQueues.issue (what UnitActionServerboundPacket.accept calls) with the ghost's centre, plain and
 * Shift-queued (APPEND, what queueModeFromKeys gives under Shift).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class GhostClickGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "ghost_click";

    /** AC-1: the right-click's order (BUILD_REPAIR at the ghost's centre) sends a Royal Architect onto a worker's ghost. */
    @GameTest(template = ARENA, timeoutTicks = 800, batch = BATCH)
    public static void ac1_build_order_at_the_ghosts_centre_joins_it(GameTestHelper helper) {
        Site s = new Site(helper, "gclick");
        Mob a = s.builder(EntityRegistrar.VILLAGER_UNIT.get(), -11, -6);
        Mob b = s.builder(EntityRegistrar.ROYAL_ARCHITECT_UNIT.get(), 11, 6);
        if (a == null || b == null) {
            s.undo();
            helper.fail("(setup) could not create the builders");
            return;
        }
        whenInLevel(helper, s, a, b, () -> buildOrderJoins(helper, s, a, b));
    }

    private static void buildOrderJoins(GameTestHelper helper, Site s, Mob a, Mob b) {
        List<String> problems = new ArrayList<>();
        if (!s.order(Defences.VILLAGERS.raidTower(), a.getId())) {
            s.undo();
            helper.fail("A's build order was refused");
            return;
        }
        if (!Blueprints.isPending(s.bp))
            problems.add("(setup) A's order is not a ghost when B is ordered");
        CommandQueues.issue(s.level, s.owner, UnitAction.BUILD_REPAIR, -1, new int[] { b.getId() }, s.bp.centrePos,
            BlockPos.ZERO, QueueMode.REPLACE);
        if (targetOf(b) != s.bp)
            problems.add("B was not sent to A's ghost by an order at its centre " + s.bp.centrePos + ": " + where(b));
        // that B then builds it with A is assist_ghost's AC-1 (same order, same server path)
        finish(helper, s, problems);
    }

    /** AC-1 (Shift): the same order Shift-queued behind a move runs after it and still lands on the ghost. */
    @GameTest(template = ARENA, timeoutTicks = 800, batch = BATCH)
    public static void ac1_shift_queued_build_order_at_the_ghosts_centre_runs_after_the_move(GameTestHelper helper) {
        Site s = new Site(helper, "gshift");
        Mob a = s.builder(EntityRegistrar.VILLAGER_UNIT.get(), -11, -6);
        Mob b = s.builder(EntityRegistrar.FIELD_MASON_UNIT.get(), 11, 6);
        if (a == null || b == null) {
            s.undo();
            helper.fail("(setup) could not create the builders");
            return;
        }
        whenInLevel(helper, s, a, b, () -> shiftOrderRunsAfterMove(helper, s, a, b));
    }

    private static void shiftOrderRunsAfterMove(GameTestHelper helper, Site s, Mob a, Mob b) {
        List<String> problems = new ArrayList<>();
        if (!s.order(Defences.VILLAGERS.raidTower(), a.getId())) {
            s.undo();
            helper.fail("A's build order was refused");
            return;
        }
        // 3 blocks toward the pad's centre: B stands 11 out on a pad of radius 12, so +3 went past the edge (the Oct 27-28
        // flake). The move B is queued behind must be one it can finish: the queue starts the Shift order only once B is
        // idle, so a move to the void beyond the edge left the test waiting on how the move goal gives up
        BlockPos step = b.blockPosition().offset(-3, 0, 0);
        if (s.level.getBlockState(step.below()).isAir()) {
            problems.add("(setup) B's move goes off the pad: no ground under " + step.toShortString() + " (pad radius "
                + BuilderAssistGameTests.PAD_R + " around " + s.cx + ", " + s.cz + "; B at " + b.blockPosition().toShortString() + ")");
            finish(helper, s, problems);
            return;
        }
        CommandQueues.issue(s.level, s.owner, UnitAction.MOVE, -1, new int[] { b.getId() }, step, BlockPos.ZERO,
            QueueMode.REPLACE);
        CommandQueues.issue(s.level, s.owner, UnitAction.BUILD_REPAIR, -1, new int[] { b.getId() }, s.bp.centrePos,
            BlockPos.ZERO, QueueMode.APPEND);
        boolean queued = false;
        for (UnitCommand c : CommandQueues.queued(b.getId()))
            if (c.action() == UnitAction.BUILD_REPAIR)
                queued = true;
        if (!queued)
            problems.add("the Shift build order was not queued behind the move: " + CommandQueues.queued(b.getId()));
        if (targetOf(b) == s.bp)
            problems.add("the Shift build order jumped the move");
        if (!problems.isEmpty()) {
            finish(helper, s, problems);
            return;
        }
        // a miss says where it stopped: did B's move ever end, did the queue start the Shift order, and was A's ghost
        // still a ghost by then (Oct 27-28 flake, "B never took the queued ghost order ... target none": #120's rerun
        // 40621212302 shard 1, #271, #198, #216); a pass logs its margin to the 560-tick wait
        long start = s.level.getGameTime();
        long[] seen = { -1, -1, -1 };   // ticks after the orders: B first idle, queued BUILD_REPAIR started, ghost left
        BlueprintGameTests.poll(helper, 0, 560,
            () -> {
                long t = s.level.getGameTime() - start;
                CommandQueue q = CommandQueues.get(b.getId());
                UnitCommand cur = q != null ? q.current() : null;
                if (seen[0] < 0 && ((Unit) b).isIdle())
                    seen[0] = t;
                if (seen[1] < 0 && cur != null && cur.action() == UnitAction.BUILD_REPAIR)
                    seen[1] = t;
                if (seen[2] < 0 && !Blueprints.isPending(s.bp))
                    seen[2] = t;
                return targetOf(b) == s.bp || building(s.bp, b);
            },
            () -> {
                ReignOfNether.LOGGER.info("[GhostShift] B took the queued ghost order {} ticks after the orders, of 560 "
                    + "(B idle at {}, Shift order started at {}, A's site stopped being a ghost at {})",
                    s.level.getGameTime() - start, seen[0], seen[1], seen[2]);
                finish(helper, s, problems);
            },
            () -> {
                problems.add("B never took the queued ghost order: " + where(b) + "; " + shiftMissState(s, a, b, seen));
                finish(helper, s, problems);
            });
    }

    /** What a Shift-queue miss looked like at the timeout: B's queue, A's site and A (ticks after the orders; -1 never). */
    private static String shiftMissState(Site s, Mob a, Mob b, long[] seen) {
        CommandQueue q = CommandQueues.get(b.getId());
        BuildingPlacement bp = s.bp;
        boolean aBuilds = building(bp, a);
        return "B first idle at " + seen[0] + ", Shift order started at " + seen[1]
            + ", A's site stopped being a ghost at " + seen[2]
            + "; B idle " + ((Unit) b).isIdle() + ", move target " + ((Unit) b).getMoveGoal().getMoveTarget()
            + ", queue current " + (q != null ? q.current() : null) + " waiting " + CommandQueues.queued(b.getId())
            + "; A's site: ghost " + Blueprints.isPending(bp) + ", built " + bp.isBuilt
            + ", destroyed " + bp.isDestroyedServerside
            + ", placed " + String.format(java.util.Locale.ROOT, "%.0f%%", bp.getBlocksPlacedPercent() * 100)
            + ", " + bp.getBuilders().size() + " builder(s), A among them " + aBuilds
            + ", the ghost at its centre " + (Blueprints.findAt(bp.centrePos) == bp ? "is this site" : "is not this site")
            + "; A: " + where(a);
    }

    /** Most ticks to wait for the builders to be in the level before the orders (timeoutTicks adds this to the old 600). */
    static final int JOIN_WAIT = 200;

    /**
     * Runs {@code body} once both builders are in the level. The pad is wider than the 16x16 arena and the builders
     * stand 11 blocks out, so on a loaded runner a builder's chunk may not be entity-ticking when the test starts:
     * addFreshEntity then only stores the unit, and level.getEntity(id) - what CommandQueues.issue looks units up by -
     * returns null, so the orders missed it (CI run 40457081140: "the Shift build order was not queued behind the
     * move: []"). A real order only names units a client has seen, i.e. units in the level, so the test waits for that.
     */
    static void whenInLevel(GameTestHelper helper, Site s, Mob a, Mob b, Runnable body) {
        int[] waited = { 0 };
        BlueprintGameTests.poll(helper, 0, JOIN_WAIT,
            () -> {
                waited[0] += 5;
                return s.level.getEntity(a.getId()) == a && s.level.getEntity(b.getId()) == b;
            },
            () -> {
                if (waited[0] > 5)
                    ReignOfNether.LOGGER.info("[GhostClick] builders joined the level after {} ticks", waited[0]);
                body.run();
            },
            () -> {
                s.undo();
                helper.fail("(setup) the builders were not in the level after " + JOIN_WAIT + " ticks: A "
                    + (s.level.getEntity(a.getId()) == a) + ", B " + (s.level.getEntity(b.getId()) == b));
            });
    }
}
