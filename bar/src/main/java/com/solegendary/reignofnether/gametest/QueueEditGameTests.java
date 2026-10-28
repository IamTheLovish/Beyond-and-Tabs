package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitQueueSync;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.command.QueueEditKeys;
import com.solegendary.reignofnether.unit.command.QueueMode;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.packets.QueueEditServerboundPacket;
import com.solegendary.reignofnether.unit.packets.UnitActionServerboundPacket;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnit;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * BAR's queue editing (unit/command/QueueEditKeys, CommandQueues.skipCurrent / cancelLast): N skips the current
 * order, Ctrl+N cancels the last one, Space held while ordering inserts at the front, and all of it on a builder's
 * build queue too. Every edit goes through the real packet: QueueEditServerboundPacket encoded, decoded and accepted
 * for a fake player, as the server does with a client's packet. Own holder (AGENTS.md: no new tests in
 * SkirmishGameTests); the helpers are CommandQueueGameTests'.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class QueueEditGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** Sends an edit the way a client does: the packet goes over the wire format and the server accepts it. */
    static void edit(FakePlayer player, QueueEditKeys.Edit edit, int... ids) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new QueueEditServerboundPacket(edit, ids).encode(buf);
            QueueEditServerboundPacket p = new QueueEditServerboundPacket(buf);
            QueueEditServerboundPacket.accept(player, p.getEdit(), p.getUnitIds());
        } finally {
            buf.release();
        }
    }

    static void order(FakePlayer player, String name, int[] ids, BlockPos at, QueueMode mode) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new UnitActionServerboundPacket(name, UnitAction.MOVE, -1, ids, at, BlockPos.ZERO, mode).encode(buf);
            QueueMode decoded = new UnitActionServerboundPacket(buf).getQueueMode();
            UnitActionServerboundPacket.accept(player, name, UnitAction.MOVE, -1, ids, at, BlockPos.ZERO, decoded);
        } finally {
            buf.release();
        }
    }

    /**
     * AC-1: N drops every selected own unit's current order and starts its next queued one; with nothing queued the
     * unit stops, as after Stop. Someone else's unit in the same packet is untouched. Key rule: N is the skip key
     * unless the card has a button on N (Garrison) and nothing selected has an order waiting; one action per press.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void ac1_skip_current_starts_the_next_queued_order(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String name = CommandQueueGameTests.owner(helper, "e1"), other = CommandQueueGameTests.owner(helper, "e1o");
        FakePlayer player = CommandQueueGameTests.fakePlayer(level, name);
        Mob unit = CommandQueueGameTests.fighter(helper, name, 2, 2);
        Mob foreign = CommandQueueGameTests.fighter(helper, other, 4, 2);
        if (unit == null || foreign == null) {
            helper.fail("could not create the units");
            return;
        }
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (QueueEditKeys.onSkipKey(false, true, false, false) != QueueEditKeys.Edit.SKIP_CURRENT
                        || QueueEditKeys.onSkipKey(false, true, true, true) != QueueEditKeys.Edit.SKIP_CURRENT
                        || QueueEditKeys.onSkipKey(false, true, true, false) != QueueEditKeys.Edit.NONE
                        || QueueEditKeys.onSkipKey(false, false, false, true) != QueueEditKeys.Edit.NONE
                        || QueueEditKeys.onSkipKey(true, true, true, false) != QueueEditKeys.Edit.CANCEL_LAST)
                    problems.add("the N key rules are off (skip / garrison / Ctrl+N)");
                int[] ids = { unit.getId() };
                int[] fids = { foreign.getId() };
                BlockPos p = CommandQueueGameTests.at(helper, 13, 13), q = CommandQueueGameTests.at(helper, 2, 13),
                    r = CommandQueueGameTests.at(helper, 13, 2);
                order(player, name, ids, p, QueueMode.REPLACE);
                order(player, name, ids, q, QueueMode.APPEND);
                order(player, name, ids, r, QueueMode.APPEND);
                UnitServerEvents.addActionItem(other, UnitAction.MOVE, -1, fids, p, BlockPos.ZERO);
                UnitServerEvents.addActionItem(other, UnitAction.MOVE, -1, fids, q, BlockPos.ZERO, true);
                edit(player, QueueEditKeys.Edit.SKIP_CURRENT, unit.getId(), foreign.getId());
                if (!q.equals(CommandQueueGameTests.moveTarget(unit)) || !CommandQueueGameTests.positions(unit).equals(List.of(r)))
                    problems.add("after N the unit should be on q with [r] waiting (target " + CommandQueueGameTests.moveTarget(unit)
                        + ", queue " + CommandQueueGameTests.positions(unit) + ")");
                if (!p.equals(CommandQueueGameTests.moveTarget(foreign)) || !CommandQueueGameTests.positions(foreign).equals(List.of(q)))
                    problems.add("another player's unit must not be skipped (target " + CommandQueueGameTests.moveTarget(foreign) + ")");
                edit(player, QueueEditKeys.Edit.SKIP_CURRENT, ids);
                if (!r.equals(CommandQueueGameTests.moveTarget(unit)) || !CommandQueueGameTests.positions(unit).isEmpty())
                    problems.add("a second N should start r (target " + CommandQueueGameTests.moveTarget(unit) + ")");
                edit(player, QueueEditKeys.Edit.SKIP_CURRENT, ids);
                if (CommandQueueGameTests.moveTarget(unit) != null || !((Unit) unit).isIdle())
                    problems.add("N with nothing queued should stop the unit (target " + CommandQueueGameTests.moveTarget(unit) + ")");
            } finally {
                UnitServerEvents.clearQueuedActions(unit.getId());
                UnitServerEvents.clearQueuedActions(foreign.getId());
                unit.discard();
                foreign.discard();
            }
            if (!problems.isEmpty())
                helper.fail(String.join("; ", problems));
            else
                helper.succeed();
        });
    }

    /** AC-2: Ctrl+N drops the LAST order in each unit's queue; with nothing queued, the current one (the unit stops). */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void ac2_cancel_last_drops_the_newest_order(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String name = CommandQueueGameTests.owner(helper, "e2");
        FakePlayer player = CommandQueueGameTests.fakePlayer(level, name);
        Mob unit = CommandQueueGameTests.fighter(helper, name, 2, 2);
        if (unit == null) {
            helper.fail("could not create the unit");
            return;
        }
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                int[] ids = { unit.getId() };
                BlockPos p = CommandQueueGameTests.at(helper, 13, 13), q = CommandQueueGameTests.at(helper, 2, 13),
                    r = CommandQueueGameTests.at(helper, 13, 2);
                order(player, name, ids, p, QueueMode.REPLACE);
                order(player, name, ids, q, QueueMode.APPEND);
                order(player, name, ids, r, QueueMode.APPEND);
                edit(player, QueueEditKeys.Edit.CANCEL_LAST, ids);
                if (!p.equals(CommandQueueGameTests.moveTarget(unit)) || !CommandQueueGameTests.positions(unit).equals(List.of(q)))
                    problems.add("Ctrl+N should drop r and keep p going with [q] (target " + CommandQueueGameTests.moveTarget(unit)
                        + ", queue " + CommandQueueGameTests.positions(unit) + ")");
                edit(player, QueueEditKeys.Edit.CANCEL_LAST, ids);
                if (!p.equals(CommandQueueGameTests.moveTarget(unit)) || !CommandQueueGameTests.positions(unit).isEmpty())
                    problems.add("a second Ctrl+N should drop q (target " + CommandQueueGameTests.moveTarget(unit)
                        + ", queue " + CommandQueueGameTests.positions(unit) + ")");
                edit(player, QueueEditKeys.Edit.CANCEL_LAST, ids);
                if (CommandQueueGameTests.moveTarget(unit) != null || !((Unit) unit).isIdle())
                    problems.add("Ctrl+N with nothing queued should drop the current order (target " + CommandQueueGameTests.moveTarget(unit) + ")");
            } finally {
                UnitServerEvents.clearQueuedActions(unit.getId());
                unit.discard();
            }
            if (!problems.isEmpty())
                helper.fail(String.join("; ", problems));
            else
                helper.succeed();
        });
    }

    /**
     * AC-3: Space held while giving an order (with units selected) makes it an insert: it starts now and the order it
     * interrupted resumes after it. The Space release after such an order is swallowed; a plain Space tap (no order,
     * or no units) keeps its old meaning (last alert / shop).
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void ac3_space_order_goes_to_the_front_of_the_queue(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String name = CommandQueueGameTests.owner(helper, "e3");
        FakePlayer player = CommandQueueGameTests.fakePlayer(level, name);
        Mob unit = CommandQueueGameTests.fighter(helper, name, 2, 2);
        if (unit == null) {
            helper.fail("could not create the unit");
            return;
        }
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                QueueEditKeys.consumeInsertRelease();   // start clean
                if (QueueEditKeys.modeFor(false, false, true, false) != QueueMode.REPLACE || QueueEditKeys.consumeInsertRelease())
                    problems.add("Space with no units selected must stay a plain tap");
                if (QueueEditKeys.consumeInsertRelease())
                    problems.add("a Space tap with no order must keep its old meaning");
                if (QueueEditKeys.modeFor(true, false, false, true) != QueueMode.APPEND || QueueEditKeys.consumeInsertRelease())
                    problems.add("Shift alone should append and leave Space alone");
                QueueMode spaceMode = QueueEditKeys.modeFor(false, false, true, true);
                if (spaceMode != QueueMode.INSERT_FRONT)
                    problems.add("Space + order should insert at the front, got " + spaceMode);
                if (!QueueEditKeys.consumeInsertRelease())
                    problems.add("the Space release after an inserted order should be swallowed");
                if (QueueEditKeys.consumeInsertRelease())
                    problems.add("only that one release should be swallowed");
                int[] ids = { unit.getId() };
                BlockPos p = CommandQueueGameTests.at(helper, 13, 13), q = CommandQueueGameTests.at(helper, 2, 13),
                    s = CommandQueueGameTests.at(helper, 8, 8);
                order(player, name, ids, p, QueueMode.REPLACE);
                order(player, name, ids, q, QueueMode.APPEND);
                order(player, name, ids, s, spaceMode);
                if (!s.equals(CommandQueueGameTests.moveTarget(unit)) || !CommandQueueGameTests.positions(unit).equals(List.of(p, q)))
                    problems.add("the Space order should run now with [p, q] behind it (target " + CommandQueueGameTests.moveTarget(unit)
                        + ", queue " + CommandQueueGameTests.positions(unit) + ")");
                edit(player, QueueEditKeys.Edit.SKIP_CURRENT, ids);
                if (!p.equals(CommandQueueGameTests.moveTarget(unit)))
                    problems.add("the interrupted p should resume after the inserted order (target " + CommandQueueGameTests.moveTarget(unit) + ")");
            } finally {
                QueueEditKeys.consumeInsertRelease();
                UnitServerEvents.clearQueuedActions(unit.getId());
                unit.discard();
            }
            if (!problems.isEmpty())
                helper.fail(String.join("; ", problems));
            else
                helper.succeed();
        });
    }

    /**
     * AC-4: a builder's build queue (BuildRepairGoal.queuedBuildings) takes the same edits: N moves it on to its next
     * site, Ctrl+N drops its last queued site, Ctrl+N with only the current site left drops that too. The owner's
     * queue lines (UnitQueueSync, what the client draws) follow each edit.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void ac4_build_queue_skip_and_cancel_last_sync_to_the_queue_lines(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String name = CommandQueueGameTests.owner(helper, "e4");
        FakePlayer player = CommandQueueGameTests.fakePlayer(level, name);
        BlockPos o = ArenaReady.pad(helper, 301);
        int cx = o.getX() + 8, cz = o.getZ() + 8;
        final int y = o.getY() + 1;
        for (int dx = -8; dx <= 8; dx++)
            for (int dz = -8; dz <= 8; dz++) {
                level.setBlock(new BlockPos(cx + dx, y - 1, cz + dz), Blocks.DIRT.defaultBlockState(), 3);
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        VillagerUnit worker = WorkerQueueFogGameTests.worker(level, name, cx + 0.5, y + 1, cz - 7.5);
        if (worker == null) {
            helper.fail("could not create the worker");
            return;
        }
        List<BuildingPlacement> sites = new ArrayList<>();
        Runnable cleanup = () -> {
            BuildingServerEvents.getBuildings().removeAll(sites);
            UnitServerEvents.clearQueuedActions(worker.getId());
            worker.discard();
        };
        var brg = worker.getBuildRepairGoal();
        ArenaReady.whenTicking(helper, List.of(worker), 100, start -> {
            List<String> problems = new ArrayList<>();
            try {
                int[] ids = { worker.getId() };
                for (int x : new int[] { -6, 0, 6 }) {
                    BuildingPlacement p = BuildingServerEvents.placeBuilding(Buildings.WIND_GENERATOR_VILLAGERS,
                        new BlockPos(cx + x, y, cz), Rotation.NONE, name, ids, x != -6, false, true, true);
                    if (p == null) {
                        problems.add("site at " + x + " was refused");
                        return;
                    }
                    sites.add(p);
                }
                BuildingPlacement a = sites.get(0), b = sites.get(1), c = sites.get(2);
                if (brg.getBuildingTarget() != a || !brg.queuedBuildings.equals(List.of(a, b, c))) {
                    problems.add("setup: expected target a with [a, b, c] queued, got " + brg.queuedBuildings.size());
                    return;
                }
                edit(player, QueueEditKeys.Edit.SKIP_CURRENT, ids);
                List<int[]> ls = WorkerQueueFogGameTests.lines(name, worker.getId());
                if (brg.getBuildingTarget() != b || !brg.queuedBuildings.equals(List.of(b, c)))
                    problems.add("N should move the builder on to b with [b, c] (queue " + brg.queuedBuildings.size() + ")");
                if (builds(ls) != 2 || !WorkerQueueFogGameTests.isBuildLine(ls.get(0), b.centrePos)
                        || !WorkerQueueFogGameTests.isBuildLine(ls.get(1), c.centrePos))
                    problems.add("after N the queue lines should be [b, c], got " + WorkerQueueFogGameTests.describe(ls));
                edit(player, QueueEditKeys.Edit.CANCEL_LAST, ids);
                ls = WorkerQueueFogGameTests.lines(name, worker.getId());
                if (brg.getBuildingTarget() != b || !brg.queuedBuildings.equals(List.of(b)))
                    problems.add("Ctrl+N should drop the last site c (queue " + brg.queuedBuildings.size() + ")");
                if (builds(ls) != 1 || !WorkerQueueFogGameTests.isBuildLine(ls.get(0), b.centrePos))
                    problems.add("after Ctrl+N the queue lines should be [b], got " + WorkerQueueFogGameTests.describe(ls));
                edit(player, QueueEditKeys.Edit.CANCEL_LAST, ids);
                ls = WorkerQueueFogGameTests.lines(name, worker.getId());
                if (brg.getBuildingTarget() != null || !brg.queuedBuildings.isEmpty())
                    problems.add("Ctrl+N on the last site should drop it (target " + brg.getBuildingTarget() + ")");
                if (builds(ls) != 0)
                    problems.add("no build lines should be left, got " + WorkerQueueFogGameTests.describe(ls));
            } finally {
                cleanup.run();
                if (!problems.isEmpty())
                    helper.fail(String.join("; ", problems));
                else
                    helper.succeed();
            }
        });
    }

    static long builds(List<int[]> lines) {
        return lines.stream().filter(e -> e[0] == UnitQueueSync.TYPE_BUILD).count();
    }
}
