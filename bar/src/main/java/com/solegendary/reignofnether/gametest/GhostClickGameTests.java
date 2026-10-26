package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.Blueprints;
import com.solegendary.reignofnether.building.buildings.defence.Defences;
import com.solegendary.reignofnether.gametest.BuilderAssistGameTests.Site;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.command.CommandQueues;
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
    @GameTest(template = ARENA, timeoutTicks = 600, batch = BATCH)
    public static void ac1_build_order_at_the_ghosts_centre_joins_it(GameTestHelper helper) {
        Site s = new Site(helper, "gclick");
        List<String> problems = new ArrayList<>();
        Mob a = s.builder(EntityRegistrar.VILLAGER_UNIT.get(), -11, -6);
        Mob b = s.builder(EntityRegistrar.ROYAL_ARCHITECT_UNIT.get(), 11, 6);
        if (a == null || b == null) {
            s.undo();
            helper.fail("(setup) could not create the builders");
            return;
        }
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
    @GameTest(template = ARENA, timeoutTicks = 600, batch = BATCH)
    public static void ac1_shift_queued_build_order_at_the_ghosts_centre_runs_after_the_move(GameTestHelper helper) {
        Site s = new Site(helper, "gshift");
        List<String> problems = new ArrayList<>();
        Mob a = s.builder(EntityRegistrar.VILLAGER_UNIT.get(), -11, -6);
        Mob b = s.builder(EntityRegistrar.FIELD_MASON_UNIT.get(), 11, 6);
        if (a == null || b == null) {
            s.undo();
            helper.fail("(setup) could not create the builders");
            return;
        }
        if (!s.order(Defences.VILLAGERS.raidTower(), a.getId())) {
            s.undo();
            helper.fail("A's build order was refused");
            return;
        }
        BlockPos step = b.blockPosition().offset(3, 0, 0);
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
        BlueprintGameTests.poll(helper, 0, 560,
            () -> targetOf(b) == s.bp || building(s.bp, b),
            () -> finish(helper, s, problems),
            () -> {
                problems.add("B never took the queued ghost order: " + where(b));
                finish(helper, s, problems);
            });
    }
}
