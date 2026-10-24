package com.solegendary.reignofnether.gametest;

import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.EngagementStance;
import com.solegendary.reignofnether.unit.PatrolServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitActionItem;
import com.solegendary.reignofnether.unit.UnitQueueSync;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.command.CommandQueues;
import com.solegendary.reignofnether.unit.command.QueueMode;
import com.solegendary.reignofnether.unit.goals.GenericTargetedSpellGoal;
import com.solegendary.reignofnether.unit.goals.GenericUntargetedSpellGoal;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.unit.packets.UnitActionServerboundPacket;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rewrite 2 of design/ron_rewrite_plan.md: the command pipeline (BAR's per-unit command queue in place of Reign of
 * Nether's two global action lists). Its own holder, so the feature branches merging into SkirmishGameTests don't
 * collide with it.
 * <p>
 * The tests in the first section pin what players see from the queue today and were written to pass on the old
 * pipeline as well (rule 1 of the rewrite plan: behaviour first, then structure). They avoid depending on
 * pathfinding: a unit is made to finish its current order by stopping it ({@link #finish}), which is exactly what
 * reaching the destination does to its move goal, and the test then watches which order the unit takes next.
 * Every unit holds fire, so a fight in a neighbouring arena can't pull it off its orders. Same rules as every other
 * holder: own owners, own arena, no global counts.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class CommandQueueGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    // ------------------------------------------------------------------ behaviour pins (old and new pipeline)

    /**
     * BAR-style shift-queue depth: a unit holds at most UnitServerEvents.MAX_QUEUED_ACTIONS queued orders. Past the
     * cap an order is refused for that unit ("Queue full"); what was already queued is kept, oldest first.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void shift_queue_holds_at_most_the_cap_per_unit(GameTestHelper helper) {
        String owner = owner(helper, "cap");
        Mob unit = fighter(helper, owner, 2, 2);
        if (unit == null) {
            helper.fail("could not create the unit");
            return;
        }
        helper.runAfterDelay(2, () -> {
            try {
                int[] ids = { unit.getId() };
                UnitServerEvents.addActionItem(owner, UnitAction.MOVE, -1, ids, at(helper, 13, 13), BlockPos.ZERO);
                int cap = UnitServerEvents.MAX_QUEUED_ACTIONS;
                for (int i = 0; i < cap + 8; i++)
                    UnitServerEvents.addActionItem(owner, UnitAction.MOVE, -1, ids, waypoint(helper, i), BlockPos.ZERO, true);
                List<UnitActionItem> queue = UnitServerEvents.getQueuedActions(unit.getId());
                if (queue.size() != cap) {
                    helper.fail("expected " + cap + " queued orders after " + (cap + 8) + " shift orders, got " + queue.size());
                    return;
                }
                for (int i = 0; i < cap; i++)
                    if (!waypoint(helper, i).equals(queue.get(i).getPreselectedBlockPos())) {
                        helper.fail("queued order #" + i + " should be the " + i + "th shift order, got " + queue.get(i).getPreselectedBlockPos());
                        return;
                    }
                if (!UnitServerEvents.hasQueuedActions(unit.getId()))
                    helper.fail("hasQueuedActions disagrees with getQueuedActions");
                else
                    helper.succeed();
            } finally {
                UnitServerEvents.clearQueuedActions(unit.getId());
                unit.discard();
            }
        });
    }

    /**
     * Shift appends: the queued orders run one after another, in the order given, each once the unit has finished
     * the one before. A plain order then replaces the whole queue at once.
     */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void shift_orders_run_in_order_and_a_plain_order_replaces_them(GameTestHelper helper) {
        String owner = owner(helper, "ord");
        Mob unit = fighter(helper, owner, 2, 2);
        if (unit == null) {
            helper.fail("could not create the unit");
            return;
        }
        int[] ids = { unit.getId() };
        BlockPos w = at(helper, 13, 2), p0 = at(helper, 13, 13), p1 = at(helper, 2, 13), p2 = at(helper, 8, 2),
            p3 = at(helper, 8, 13), p4 = at(helper, 13, 8), p5 = at(helper, 2, 8);
        int[] phase = { 0 };
        String[] problem = { null };
        helper.succeedWhen(() -> {
            if (problem[0] != null)
                helper.fail(problem[0]);
            BlockPos target = moveTarget(unit);
            switch (phase[0]) {
                case 0 -> {
                    UnitServerEvents.addActionItem(owner, UnitAction.MOVE, -1, ids, w, BlockPos.ZERO);
                    phase[0] = 1;
                    helper.fail("phase 0: ordered the first move");
                }
                case 1 -> {
                    if (!w.equals(target))
                        helper.fail("phase 1: waiting for the unit to take the first move (target " + target + ")");
                    // busy now: a plain order and two shift orders behind it
                    UnitServerEvents.addActionItem(owner, UnitAction.MOVE, -1, ids, p0, BlockPos.ZERO);
                    UnitServerEvents.addActionItem(owner, UnitAction.MOVE, -1, ids, p1, BlockPos.ZERO, true);
                    UnitServerEvents.addActionItem(owner, UnitAction.MOVE, -1, ids, p2, BlockPos.ZERO, true);
                    if (!positions(unit).equals(List.of(p1, p2)))
                        problem[0] = "after a plain order and two shift orders the queue should be [p1, p2], got " + positions(unit);
                    phase[0] = 2;
                    helper.fail("phase 1: ordered p0 + [p1, p2]");
                }
                case 2 -> {
                    if (!p0.equals(target))
                        helper.fail("phase 2: waiting for the plain order p0 (target " + target + ")");
                    finish(unit);
                    phase[0] = 3;
                    helper.fail("phase 2: finished p0");
                }
                case 3 -> {
                    if (!p1.equals(target) || !positions(unit).equals(List.of(p2)))
                        helper.fail("phase 3: waiting for the first queued order p1 (target " + target + ", queue " + positions(unit) + ")");
                    finish(unit);
                    phase[0] = 4;
                    helper.fail("phase 3: finished p1");
                }
                case 4 -> {
                    if (!p2.equals(target) || !positions(unit).isEmpty())
                        helper.fail("phase 4: waiting for the second queued order p2 (target " + target + ", queue " + positions(unit) + ")");
                    UnitServerEvents.addActionItem(owner, UnitAction.MOVE, -1, ids, p3, BlockPos.ZERO, true);
                    UnitServerEvents.addActionItem(owner, UnitAction.MOVE, -1, ids, p4, BlockPos.ZERO, true);
                    if (!positions(unit).equals(List.of(p3, p4)))
                        problem[0] = "two shift orders on a busy unit should queue [p3, p4], got " + positions(unit);
                    UnitServerEvents.addActionItem(owner, UnitAction.MOVE, -1, ids, p5, BlockPos.ZERO);
                    if (!positions(unit).isEmpty())
                        problem[0] = "a plain order should empty the queue at once, it still holds " + positions(unit);
                    phase[0] = 5;
                    helper.fail("phase 4: queued [p3, p4], then a plain order p5");
                }
                default -> {
                    if (!p5.equals(target))
                        helper.fail("phase 5: waiting for the replacing order p5 (target " + target + ", queue " + positions(unit) + ")");
                    UnitServerEvents.clearQueuedActions(unit.getId());
                    unit.discard();
                }
            }
        });
    }

    /**
     * A queued order whose target is gone is skipped (BAR drops a command whose target died): the unit moves on to
     * the next order and never locks onto the removed unit.
     */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void queued_order_on_a_removed_target_is_skipped(GameTestHelper helper) {
        String owner = owner(helper, "dead"), foe = owner(helper, "dfo");
        Mob unit = fighter(helper, owner, 2, 2);
        Mob victim = fighter(helper, foe, 13, 13);
        if (unit == null || victim == null) {
            helper.fail("could not create the units");
            return;
        }
        int[] ids = { unit.getId() };
        BlockPos w = at(helper, 13, 2), p = at(helper, 2, 13);
        int[] phase = { 0 };
        String[] problem = { null };
        helper.succeedWhen(() -> {
            if (problem[0] != null)
                helper.fail(problem[0]);
            BlockPos target = moveTarget(unit);
            switch (phase[0]) {
                case 0 -> {
                    UnitServerEvents.addActionItem(owner, UnitAction.MOVE, -1, ids, w, BlockPos.ZERO);
                    phase[0] = 1;
                    helper.fail("phase 0: ordered the first move");
                }
                case 1 -> {
                    if (!w.equals(target))
                        helper.fail("phase 1: waiting for the unit to take the first move (target " + target + ")");
                    UnitServerEvents.addActionItem(owner, UnitAction.ATTACK, victim.getId(), ids, victim.blockPosition(), BlockPos.ZERO, true);
                    UnitServerEvents.addActionItem(owner, UnitAction.MOVE, -1, ids, p, BlockPos.ZERO, true);
                    if (UnitServerEvents.getQueuedActions(unit.getId()).size() != 2)
                        problem[0] = "expected [attack, move] queued, got " + UnitServerEvents.getQueuedActions(unit.getId()).size() + " orders";
                    victim.discard();   // the target is gone before its order comes up
                    finish(unit);
                    phase[0] = 2;
                    helper.fail("phase 1: queued an attack on a removed unit, then a move");
                }
                default -> {
                    if (((Unit) unit).getTargetGoal().getTarget() == victim)
                        problem[0] = "the unit locked onto a removed target";
                    if (!p.equals(target) || UnitServerEvents.hasQueuedActions(unit.getId()))
                        helper.fail("phase 2: waiting for the move behind the dead target's order (target " + target
                            + ", queue " + positions(unit) + ")");
                    unit.discard();
                }
            }
        });
    }

    /**
     * PacketAuth stays the gate: an order in another player's name is refused outright (shift or not), an order in
     * the sender's own name that lists someone else's unit goes through with that unit filtered out, and the
     * owner's own order is obeyed.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void orders_from_a_player_who_does_not_own_the_unit_are_refused(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String nameA = owner(helper, "pa"), nameB = owner(helper, "pb");
        FakePlayer fakeA = fakePlayer(level, nameA), fakeB = fakePlayer(level, nameB);
        Mob unit = fighter(helper, nameA, 2, 2);
        if (unit == null) {
            helper.fail("could not create the unit");
            return;
        }
        int[] ids = { unit.getId() };
        BlockPos p = at(helper, 13, 13);
        List<String> problems = new ArrayList<>();
        helper.runAfterDelay(2, () -> {
            if (UnitActionServerboundPacket.accept(fakeB, nameA, UnitAction.MOVE, -1, ids, p, BlockPos.ZERO, false))
                problems.add("B's plain order in A's name was accepted");
            if (UnitActionServerboundPacket.accept(fakeB, nameA, UnitAction.MOVE, -1, ids, p, BlockPos.ZERO, true))
                problems.add("B's shift order in A's name was accepted");
            if (!UnitActionServerboundPacket.accept(fakeB, nameB, UnitAction.MOVE, -1, ids, p, BlockPos.ZERO, false))
                problems.add("B's order in its own name was refused outright (A's unit should just be filtered out)");
            UnitActionServerboundPacket.accept(fakeB, nameB, UnitAction.MOVE, -1, ids, p, BlockPos.ZERO, true);
        });
        helper.runAfterDelay(6, () -> {
            if (moveTarget(unit) != null || UnitServerEvents.hasQueuedActions(unit.getId()))
                problems.add("A's unit took B's orders (target " + moveTarget(unit) + ", queue " + positions(unit) + ")");
            if (!UnitActionServerboundPacket.accept(fakeA, nameA, UnitAction.MOVE, -1, ids, p, BlockPos.ZERO, false))
                problems.add("A's own order was refused");
        });
        helper.runAfterDelay(9, () -> {
            try {
                if (!p.equals(moveTarget(unit)))
                    problems.add("A's unit did not take A's own order (target " + moveTarget(unit) + ")");
                if (!problems.isEmpty())
                    helper.fail(String.join("; ", problems));
                else
                    helper.succeed();
            } finally {
                UnitServerEvents.clearQueuedActions(unit.getId());
                unit.discard();
            }
        });
    }

    /**
     * Production rally points: a unit leaving the factory walks to the first rally point (a plain order) and then to
     * the next ones, which ProductionPlacement queues behind it with shift.
     */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void production_rally_points_still_run_in_order(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "ral");
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        final int y = 245;   // this test's own height (the other gametests use 170-236 and 260+)
        for (int dx = 0; dx < 16; dx++)
            for (int dz = 0; dz < 16; dz++)
                level.setBlock(new BlockPos(base.getX() + dx, y - 1, base.getZ() + dz), Blocks.STONE.defaultBlockState(), 2);
        BuildingPlacement bp = RegressionGameTests.stamp(level, Buildings.BARRACKS, new BlockPos(base.getX() + 1, y, base.getZ() + 1), owner);
        if (!(bp instanceof ProductionPlacement pp)) {
            RegressionGameTests.clear(level, bp);
            helper.fail("could not stamp a Barracks: " + bp);
            return;
        }
        int rz = Math.min(base.getZ() + 14, bp.maxCorner.getZ() + 4);
        BlockPos r0 = new BlockPos(Math.min(base.getX() + 14, bp.maxCorner.getX() + 3), y, rz);
        BlockPos r1 = new BlockPos(base.getX() + 1, y, rz);
        pp.setRallyPoint(r0);
        pp.addRallyPoint(r1);
        if (pp.getRallyPoints().size() != 2) {
            RegressionGameTests.clear(level, bp);
            helper.fail("the Barracks refused its rally points " + r0 + " / " + r1 + ": " + pp.getRallyPoints());
            return;
        }
        Entity produced = pp.produceUnit(level, EntityRegistrar.VINDICATOR_UNIT.get(), owner, true);
        if (!(produced instanceof Mob unit)) {
            RegressionGameTests.clear(level, bp);
            helper.fail("the Barracks produced nothing");
            return;
        }
        boolean[] sawFirst = { false }, cleaned = { false };
        Runnable cleanup = () -> {
            if (cleaned[0])
                return;
            cleaned[0] = true;
            UnitServerEvents.clearQueuedActions(unit.getId());
            unit.discard();
            RegressionGameTests.clear(level, bp);
        };
        helper.runAfterDelay(290, cleanup);   // a timeout must not leave a registered Barracks behind
        helper.succeedWhen(() -> {
            BlockPos target = moveTarget(unit);
            if (r0.equals(target)) {
                sawFirst[0] = true;
                // the second point waits behind the first (shift): arriving at the first must start it. Finished by
                // hand so the test doesn't depend on the unit finding its way out of the stamped Barracks
                if (positions(unit).equals(List.of(r1)))
                    finish(unit);
            }
            if (!sawFirst[0] || !r1.equals(target))
                helper.fail("waiting for rally point 1 then 2 (target " + target + ", saw the first " + sawFirst[0]
                    + ", queue " + positions(unit) + ")");
            cleanup.run();
        });
    }

    /**
     * A fire-state toggle (BAR state command) is applied at once and leaves the unit's current order and its
     * queue alone.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void fire_state_toggle_keeps_the_queue_and_the_current_order(GameTestHelper helper) {
        String owner = owner(helper, "stc");
        Mob unit = fighter(helper, owner, 2, 2);
        if (unit == null) {
            helper.fail("could not create the unit");
            return;
        }
        int[] ids = { unit.getId() };
        BlockPos w = at(helper, 13, 2), p1 = at(helper, 13, 13), p2 = at(helper, 2, 13);
        int[] phase = { 0 };
        String[] problem = { null };
        helper.succeedWhen(() -> {
            if (problem[0] != null)
                helper.fail(problem[0]);
            BlockPos target = moveTarget(unit);
            switch (phase[0]) {
                case 0 -> {
                    UnitServerEvents.addActionItem(owner, UnitAction.MOVE, -1, ids, w, BlockPos.ZERO);
                    phase[0] = 1;
                    helper.fail("phase 0: ordered the first move");
                }
                case 1 -> {
                    if (!w.equals(target))
                        helper.fail("phase 1: waiting for the unit to take the first move (target " + target + ")");
                    UnitServerEvents.addActionItem(owner, UnitAction.MOVE, -1, ids, p1, BlockPos.ZERO, true);
                    UnitServerEvents.addActionItem(owner, UnitAction.MOVE, -1, ids, p2, BlockPos.ZERO, true);
                    UnitServerEvents.addActionItem(owner, UnitAction.SET_STANCE_RETURN_FIRE, -1, ids, BlockPos.ZERO, BlockPos.ZERO);
                    if (!positions(unit).equals(List.of(p1, p2)))
                        problem[0] = "a fire-state toggle changed the queue to " + positions(unit);
                    phase[0] = 2;
                    helper.fail("phase 1: queued [p1, p2] and toggled the fire state");
                }
                default -> {
                    if (EngagementStance.of(unit) != EngagementStance.RETURN_FIRE)
                        helper.fail("phase 2: waiting for the fire state to apply (now " + EngagementStance.of(unit) + ")");
                    if (!w.equals(target) || !positions(unit).equals(List.of(p1, p2)))
                        problem[0] = "the toggle disturbed the order (target " + target + ", queue " + positions(unit) + ")";
                    else {
                        UnitServerEvents.clearQueuedActions(unit.getId());
                        unit.discard();
                    }
                }
            }
        });
    }

    // ------------------------------------------------------------------ the per-unit queue (new behaviour)

    /**
     * Orders take effect the tick the packet is handled (the old fast list ran them at the end of the next overworld
     * tick): a shift order on a unit with nothing to do starts at once (Spring runs a lone command immediately), a
     * plain order replaces it at once, and a Ctrl order starts at once and hands the move it interrupted back to the
     * head of the queue.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void orders_take_effect_the_tick_the_packet_is_handled(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String name = owner(helper, "now");
        FakePlayer player = fakePlayer(level, name);
        Mob unit = fighter(helper, name, 2, 2);
        if (unit == null) {
            helper.fail("could not create the unit");
            return;
        }
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                int[] ids = { unit.getId() };
                BlockPos p = at(helper, 13, 13), q = at(helper, 2, 13), r = at(helper, 13, 2);
                UnitActionServerboundPacket.accept(player, name, UnitAction.MOVE, -1, ids, p, BlockPos.ZERO, QueueMode.APPEND);
                if (!p.equals(moveTarget(unit)) || UnitServerEvents.hasQueuedActions(unit.getId()))
                    problems.add("a shift order on an idle unit should start at once (target " + moveTarget(unit) + ", queue " + positions(unit) + ")");
                UnitActionServerboundPacket.accept(player, name, UnitAction.MOVE, -1, ids, q, BlockPos.ZERO, QueueMode.REPLACE);
                if (!q.equals(moveTarget(unit)))
                    problems.add("a plain order should take effect in the same tick (target " + moveTarget(unit) + ")");
                UnitActionServerboundPacket.accept(player, name, UnitAction.MOVE, -1, ids, r, BlockPos.ZERO, QueueMode.INSERT_FRONT);
                if (!r.equals(moveTarget(unit)))
                    problems.add("a Ctrl order should start at once (target " + moveTarget(unit) + ")");
                if (!positions(unit).equals(List.of(q)))
                    problems.add("the move a Ctrl order interrupts should wait at the head of the queue, got " + positions(unit));
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
     * Ctrl inserts at the front (Spring's CMD_INSERT at 0): the busy unit switches to the new order now, then resumes
     * the order it was interrupted in, then the rest of its queue. On a unit with nothing to do a Ctrl order simply
     * starts, and nothing that was already finished comes back.
     */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void ctrl_order_interrupts_and_the_interrupted_order_resumes(GameTestHelper helper) {
        String owner = owner(helper, "ins");
        Mob unit = fighter(helper, owner, 2, 2);
        if (unit == null) {
            helper.fail("could not create the unit");
            return;
        }
        int[] ids = { unit.getId() };
        BlockPos w = at(helper, 13, 2), p1 = at(helper, 13, 13), q = at(helper, 2, 13), late = at(helper, 8, 8);
        int[] phase = { 0 };
        String[] problem = { null };
        helper.succeedWhen(() -> {
            if (problem[0] != null)
                helper.fail(problem[0]);
            BlockPos target = moveTarget(unit);
            switch (phase[0]) {
                case 0 -> {
                    UnitServerEvents.addActionItem(owner, UnitAction.MOVE, -1, ids, w, BlockPos.ZERO);
                    UnitServerEvents.addActionItem(owner, UnitAction.MOVE, -1, ids, p1, BlockPos.ZERO, true);
                    phase[0] = 1;
                    helper.fail("phase 0: ordered w, then p1 queued");
                }
                case 1 -> {
                    if (!w.equals(target))
                        helper.fail("phase 1: waiting for w (target " + target + ")");
                    UnitServerEvents.addActionItem(owner, UnitAction.MOVE, -1, ids, q, BlockPos.ZERO, QueueMode.INSERT_FRONT);
                    if (!q.equals(moveTarget(unit)) || !positions(unit).equals(List.of(w, p1)))
                        problem[0] = "a Ctrl order should start at once with [w, p1] waiting behind it (target "
                            + moveTarget(unit) + ", queue " + positions(unit) + ")";
                    phase[0] = 2;
                    helper.fail("phase 1: inserted q at the front");
                }
                case 2 -> {
                    finish(unit);
                    phase[0] = 3;
                    helper.fail("phase 2: finished q");
                }
                case 3 -> {
                    if (!w.equals(target) || !positions(unit).equals(List.of(p1)))
                        helper.fail("phase 3: waiting for the interrupted w to resume (target " + target + ", queue " + positions(unit) + ")");
                    finish(unit);
                    phase[0] = 4;
                    helper.fail("phase 3: finished w");
                }
                case 4 -> {
                    if (!p1.equals(target) || !positions(unit).isEmpty())
                        helper.fail("phase 4: waiting for p1 (target " + target + ", queue " + positions(unit) + ")");
                    finish(unit);
                    phase[0] = 5;
                    helper.fail("phase 4: finished p1");
                }
                default -> {
                    // idle now: a Ctrl order just starts, and the finished p1 is not handed back
                    UnitServerEvents.addActionItem(owner, UnitAction.MOVE, -1, ids, late, BlockPos.ZERO, QueueMode.INSERT_FRONT);
                    if (!late.equals(moveTarget(unit)) || !positions(unit).isEmpty())
                        problem[0] = "a Ctrl order on an idle unit should just start (target " + moveTarget(unit) + ", queue " + positions(unit) + ")";
                    else {
                        UnitServerEvents.clearQueuedActions(unit.getId());
                        unit.discard();
                    }
                }
            }
        });
    }

    /**
     * A dying target's order is dropped in the same executor pass as the next order starts (no idle tick in between),
     * and the unit never locks onto the dying unit.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void dying_target_order_is_skipped_in_the_same_pass(GameTestHelper helper) {
        String owner = owner(helper, "dyi"), foe = owner(helper, "dyf");
        Mob unit = fighter(helper, owner, 2, 2);
        Mob victim = fighter(helper, foe, 13, 13);
        if (unit == null || victim == null) {
            helper.fail("could not create the units");
            return;
        }
        int[] ids = { unit.getId() };
        BlockPos w = at(helper, 13, 2), p = at(helper, 2, 13);
        int[] phase = { 0 };
        String[] problem = { null };
        helper.succeedWhen(() -> {
            if (problem[0] != null)
                helper.fail(problem[0]);
            switch (phase[0]) {
                case 0 -> {
                    UnitServerEvents.addActionItem(owner, UnitAction.MOVE, -1, ids, w, BlockPos.ZERO);
                    phase[0] = 1;
                    helper.fail("phase 0: ordered w");
                }
                case 1 -> {
                    UnitServerEvents.addActionItem(owner, UnitAction.ATTACK, victim.getId(), ids, victim.blockPosition(), BlockPos.ZERO, true);
                    UnitServerEvents.addActionItem(owner, UnitAction.MOVE, -1, ids, p, BlockPos.ZERO, true);
                    victim.kill();   // dying, still in the level for its death animation
                    finish(unit);
                    phase[0] = 2;
                    helper.fail("phase 1: queued [attack the dying unit, p] and finished w");
                }
                default -> {
                    // the executor ran at the end of this tick's overworld tick: the attack was skipped, p started
                    if (((Unit) unit).getTargetGoal().getTarget() == victim)
                        problem[0] = "the unit locked onto a dying target";
                    else if (!p.equals(moveTarget(unit)) || UnitServerEvents.hasQueuedActions(unit.getId()))
                        problem[0] = "p should start in the pass that skipped the dead target's order (target "
                            + moveTarget(unit) + ", queue " + positions(unit) + ")";
                    else {
                        unit.discard();
                        victim.discard();
                    }
                }
            }
        });
    }

    /**
     * A shift order on idle units starts at once for all of them, as one group order: a move spreads them in formation
     * like a plain click does. (The old slow list ran each unit's copy alone, one per tick server-wide, all to the same
     * block.)
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void shift_order_on_idle_units_starts_them_together(GameTestHelper helper) {
        String owner = owner(helper, "grp");
        List<Mob> units = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Mob m = fighter(helper, owner, 2 + i * 2, 2);
            if (m == null) {
                helper.fail("could not create the units");
                units.forEach(Entity::discard);
                return;
            }
            units.add(m);
        }
        helper.runAfterDelay(2, () -> {
            try {
                int[] ids = units.stream().mapToInt(Entity::getId).toArray();
                BlockPos p = at(helper, 8, 12);
                UnitServerEvents.addActionItem(owner, UnitAction.MOVE, -1, ids, p, BlockPos.ZERO, true);
                java.util.Set<BlockPos> targets = new java.util.HashSet<>();
                for (Mob m : units) {
                    if (UnitServerEvents.hasQueuedActions(m.getId()) || moveTarget(m) == null) {
                        helper.fail("every idle unit should start the shift order at once (target " + moveTarget(m)
                            + ", queue " + positions(m) + ")");
                        return;
                    }
                    targets.add(moveTarget(m));
                }
                if (targets.size() != units.size())
                    helper.fail("the group should spread in formation, got " + targets.size() + " distinct targets for " + units.size() + " units");
                else
                    helper.succeed();
            } finally {
                for (Mob m : units) {
                    UnitServerEvents.clearQueuedActions(m.getId());
                    m.discard();
                }
            }
        });
    }

    /**
     * State commands (BAR's CMD_FIRE_STATE / autocast) never touch the queue or a standing order: an autocast toggle on
     * a busy worker keeps its order and its queue, and neither a fire-state nor an autocast toggle cancels a patrol.
     * (The old pipeline let an autocast toggle empty the shift-queue and cancel patrol and guard.)
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void state_commands_leave_the_queue_and_a_patrol_alone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "sta");
        Mob worker = EntityRegistrar.VILLAGER_UNIT.get().create(level);
        Mob guard = fighter(helper, owner, 2, 13);
        if (worker == null || guard == null) {
            helper.fail("could not create the units");
            if (guard != null)
                guard.discard();
            return;
        }
        BlockPos start = at(helper, 2, 2);
        worker.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0, 0);
        ((Unit) worker).setOwnerName(owner);
        level.addFreshEntity(worker);
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                int[] wid = { worker.getId() }, gid = { guard.getId() };
                BlockPos w = at(helper, 13, 2), p1 = at(helper, 13, 13);
                UnitServerEvents.addActionItem(owner, UnitAction.MOVE, -1, wid, w, BlockPos.ZERO);
                UnitServerEvents.addActionItem(owner, UnitAction.MOVE, -1, wid, p1, BlockPos.ZERO, true);
                UnitServerEvents.addActionItem(owner, UnitAction.ENABLE_AUTOCAST_BUILD_REPAIR, -1, wid, BlockPos.ZERO, BlockPos.ZERO);
                var goal = ((WorkerUnit) worker).getBuildRepairGoal();
                if (goal == null || !goal.autocastRepair)
                    problems.add("the autocast toggle did not apply");
                if (!w.equals(moveTarget(worker)) || !positions(worker).equals(List.of(p1)))
                    problems.add("an autocast toggle disturbed the worker's order (target " + moveTarget(worker) + ", queue " + positions(worker) + ")");
                UnitServerEvents.addActionItem(owner, UnitAction.PATROL, -1, gid, at(helper, 13, 13), BlockPos.ZERO);
                if (!PatrolServerEvents.isPatrolling(guard.getId()))
                    problems.add("the patrol order did not start");
                UnitServerEvents.addActionItem(owner, UnitAction.SET_STANCE_RETURN_FIRE, -1, gid, BlockPos.ZERO, BlockPos.ZERO);
                UnitServerEvents.addActionItem(owner, UnitAction.THROW_HEALING_POTION_AUTOCAST_ENABLE, -1, gid, BlockPos.ZERO, BlockPos.ZERO);
                if (!PatrolServerEvents.isPatrolling(guard.getId()))
                    problems.add("a fire-state or autocast toggle cancelled the patrol");
            } finally {
                UnitServerEvents.clearQueuedActions(worker.getId());
                PatrolServerEvents.clear(guard.getId());
                worker.discard();
                guard.discard();
            }
            if (!problems.isEmpty())
                helper.fail(String.join("; ", problems));
            else
                helper.succeed();
        });
    }

    /**
     * The queue lines are read straight from the unit's queue: its current order (from its goals) then every waiting
     * order in order, and an order on a target that is gone draws no line (it will be skipped).
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void queue_lines_are_read_from_the_units_own_queue(GameTestHelper helper) {
        String owner = owner(helper, "lin"), foe = owner(helper, "lif");
        Mob unit = fighter(helper, owner, 2, 2);
        Mob victim = fighter(helper, foe, 13, 13);
        if (unit == null || victim == null) {
            helper.fail("could not create the units");
            return;
        }
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                int[] ids = { unit.getId() };
                BlockPos w = at(helper, 13, 2), p1 = at(helper, 2, 13), p2 = at(helper, 8, 8);
                UnitServerEvents.addActionItem(owner, UnitAction.MOVE, -1, ids, w, BlockPos.ZERO);
                UnitServerEvents.addActionItem(owner, UnitAction.MOVE, -1, ids, p1, BlockPos.ZERO, true);
                UnitServerEvents.addActionItem(owner, UnitAction.ATTACK, victim.getId(), ids, victim.blockPosition(), BlockPos.ZERO, true);
                UnitServerEvents.addActionItem(owner, UnitAction.MOVE, -1, ids, p2, BlockPos.ZERO, true);
                List<int[]> lines = lines(owner, unit.getId());
                if (lines.size() != 4 || !isLine(lines.get(0), UnitQueueSync.TYPE_MOVE, w, -1) || !isLine(lines.get(1), UnitQueueSync.TYPE_MOVE, p1, -1)
                        || lines.get(2)[0] != UnitQueueSync.TYPE_ATTACK || lines.get(2)[4] != victim.getId()
                        || !isLine(lines.get(3), UnitQueueSync.TYPE_MOVE, p2, -1))
                    problems.add("expected [move w, move p1, attack victim, move p2], got " + describe(lines));
                victim.discard();
                lines = lines(owner, unit.getId());
                if (lines.size() != 3 || !isLine(lines.get(2), UnitQueueSync.TYPE_MOVE, p2, -1))
                    problems.add("after the target is gone expected [move w, move p1, move p2], got " + describe(lines));
            } finally {
                UnitServerEvents.clearQueuedActions(unit.getId());
                unit.discard();
                victim.discard();
            }
            if (!problems.isEmpty())
                helper.fail(String.join("; ", problems));
            else
                helper.succeed();
        });
    }

    /**
     * UnitActionItem.equals compares every field (its old operator precedence compared only the building position and
     * the ids, so the client skipped predicting a move to a new spot with the same selection), and the order packet
     * carries its queue mode intact: the old shift boolean's byte values still mean replace / append, and a byte a
     * modified client invents is a plain order.
     */
    @GameTest(template = ARENA)
    public static void order_item_equality_and_packet_queue_mode_round_trip(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        int[] ids = { 1, 2 };
        BlockPos a = new BlockPos(1, 2, 3), b = new BlockPos(4, 5, 6);
        UnitActionItem base = new UnitActionItem("o", UnitAction.MOVE, -1, ids, a, BlockPos.ZERO);
        if (!base.equals(new UnitActionItem("o", UnitAction.MOVE, -1, new int[] { 1, 2 }, a, BlockPos.ZERO)))
            problems.add("identical orders are not equal");
        if (base.equals(new UnitActionItem("o", UnitAction.MOVE, -1, ids, b, BlockPos.ZERO)))
            problems.add("a move to another spot counts as the same order");
        if (base.equals(new UnitActionItem("o", UnitAction.ATTACK_MOVE, -1, ids, a, BlockPos.ZERO)))
            problems.add("another action counts as the same order");
        if (base.equals(new UnitActionItem("p", UnitAction.MOVE, -1, ids, a, BlockPos.ZERO)))
            problems.add("another owner counts as the same order");
        if (base.equals(null))
            problems.add("equals(null)");

        for (QueueMode mode : QueueMode.values()) {
            QueueMode back = roundTrip(new UnitActionServerboundPacket("o", UnitAction.MOVE, -1, ids, a, b, mode), -1);
            if (back != mode)
                problems.add(mode + " came back as " + back);
        }
        if (roundTrip(new UnitActionServerboundPacket("o", UnitAction.MOVE, -1, ids, a, b, true), -1) != QueueMode.APPEND
                || roundTrip(new UnitActionServerboundPacket("o", UnitAction.MOVE, -1, ids, a, b, false), -1) != QueueMode.REPLACE)
            problems.add("the shift boolean constructor no longer maps to append / replace");
        if (roundTrip(new UnitActionServerboundPacket("o", UnitAction.MOVE, -1, ids, a, b, QueueMode.APPEND), 7) != QueueMode.REPLACE)
            problems.add("an unknown mode byte should decode as a plain order");
        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        else
            helper.succeed();
    }

    // ------------------------------------------------------------------ 8v8 benchmark of the command pipeline

    static final int CQ_OWNERS = 8, CQ_PER_OWNER = 50, CQ_QUEUED = 3, CQ_WARMUP = 20, CQ_PASSES = 100;
    /** One executor pass over 400 busy units with 1,200 orders waiting, in ms. Generous: it measures ~tens of us. */
    static final double CQ_BUDGET_MS = 0.5;

    /**
     * The 8v8 scale of SkirmishGameTests' stress benchmark (400 units of eight test owners, the same unit mix), each
     * busy in one of three ways (walking, attack-moving, following) with three shift-orders waiting: the executor's
     * per-tick pass is timed and its allocation counted, against the old pipeline's per-tick scan of a global list
     * holding the same orders (with the old Unit.isIdle, whose answers the new one must match), and the queue lines
     * against a copy of the old UnitQueueSync build over that list. Then the whole army finishes its orders at once
     * and every unit must start its next one within a few passes - the old list started one queued order per tick
     * for the whole server. Own batch, so nothing else's units are in allUnits (CR-15);
     * spawned, measured and removed inside one callback, so the units never tick. Logged as "[Stress] command queue"
     * (a CI notice).
     */
    @GameTest(template = ARENA, timeoutTicks = 12600, batch = "stress_command_queue")
    public static void stress_command_queue_executor_stays_in_budget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        int baseX = origin.getX() - 3000, baseZ = origin.getZ();   // the 8v8 benchmark's spot (other batch, never together)
        final double y = 150;
        List<long[]> chunks = new ArrayList<>();
        for (int o = 0; o < CQ_OWNERS; o++) {
            int cx = baseX + (o % 4) * 20, cz = baseZ + (o / 4) * 20;
            for (int dx = -16; dx <= 16; dx += 16)
                for (int dz = -16; dz <= 16; dz += 16) {
                    long[] c = { (cx + dx) >> 4, (cz + dz) >> 4 };
                    if (chunks.stream().noneMatch(k -> k[0] == c[0] && k[1] == c[1]))
                        chunks.add(c);
                }
        }
        for (long[] c : chunks)
            GameTestChunks.hold(level, (int) c[0], (int) c[1]);

        Runnable measure = () -> {
            var rng = new java.util.Random(8);
            List<Mob> units = new ArrayList<>();
            List<String> problems = new ArrayList<>();
            try {
                for (int o = 0; o < CQ_OWNERS; o++) {
                    int cx = baseX + (o % 4) * 20, cz = baseZ + (o / 4) * 20;
                    for (int i = 0; i < CQ_PER_OWNER; i++) {
                        Mob m = SkirmishGameTests.stressUnitType(o, i).create(level);
                        if (m == null)
                            continue;
                        m.moveTo(cx + rng.nextDouble() * 24 - 12, y, cz + rng.nextDouble() * 24 - 12, rng.nextFloat() * 360, 0);
                        ((Unit) m).setOwnerName("gametest_cqs_" + o);
                        level.addFreshEntity(m);
                        units.add(m);
                    }
                }
                // orders find their units by id (level.getEntity), which only sees entities in loaded chunks
                int unfindable = 0;
                for (Mob m : units)
                    if (level.getEntity(m.getId()) != m)
                        unfindable++;
                if (unfindable > 0)
                    problems.add(unfindable + " of " + units.size() + " units can't be found by id (their chunk isn't loaded)");
                // busy three ways, like an army mid-order
                for (int i = 0; i < units.size(); i++) {
                    Mob m = units.get(i);
                    BlockPos far = new BlockPos(m.getBlockX() + 40, (int) y, m.getBlockZ());
                    if (i % 3 == 1 && m instanceof AttackerUnit attacker)
                        attacker.setAttackMoveTarget(far);
                    else if (i % 3 == 2)
                        ((Unit) m).setFollowTarget(units.get(i - 1));
                    else
                        ((Unit) m).getMoveGoal().setMoveTarget(far);
                }
                List<Mob> busy = new ArrayList<>();
                int disagree = 0;
                for (Mob m : units) {
                    if (((Unit) m).isIdle() != oldIsIdle(m) && disagree++ == 0)
                        problems.add("isIdle disagrees with the old form for a busy " + m.getType().getDescriptionId());
                    if (!((Unit) m).isIdle())
                        busy.add(m);
                }
                // three shift-orders per busy unit, given per owner the way a player shift-clicks waypoints for an army
                List<UnitActionItem> oldSlow = java.util.Collections.synchronizedList(new ArrayList<>());
                for (int o = 0; o < CQ_OWNERS; o++) {
                    String owner = "gametest_cqs_" + o;
                    int[] ids = busy.stream().filter(m -> owner.equals(((Unit) m).getOwnerName())).mapToInt(Entity::getId).toArray();
                    for (int k = 0; k < CQ_QUEUED; k++) {
                        BlockPos wp = new BlockPos(baseX + (o % 4) * 20 + k * 3, (int) y, baseZ + (o / 4) * 20 + 30);
                        CommandQueues.issue(level, owner, UnitAction.MOVE, -1, ids, wp, BlockPos.ZERO, QueueMode.APPEND);
                        for (int id : ids)   // the same orders in the old pipeline's shape, for the reference scan
                            oldSlow.add(new UnitActionItem(owner, UnitAction.MOVE, -1, new int[] { id }, wp, BlockPos.ZERO));
                    }
                }
                for (Mob m : busy)
                    if (CommandQueues.queuedCount(m.getId()) != CQ_QUEUED) {
                        problems.add("a busy " + m.getType().getDescriptionId() + " holds " + CommandQueues.queuedCount(m.getId())
                            + " orders instead of " + CQ_QUEUED);
                        break;
                    }

                String[] names = { "executor", "REF old slow-list scan", "isIdle x" + units.size(), "REF old isIdle x" + units.size(),
                    "queue lines", "REF old queue lines" };
                Runnable[] systems = {
                    () -> CommandQueues.tick(level),
                    () -> cqSink += oldSlowListScan(level, oldSlow),
                    () -> { for (Mob m : units) cqSink += ((Unit) m).isIdle() ? 1 : 0; },
                    () -> { for (Mob m : units) cqSink += oldIsIdle(m) ? 1 : 0; },
                    () -> cqSink += UnitQueueSync.buildPayloads().size(),
                    () -> cqSink += oldQueueLines(oldSlow).size(),
                };
                long[] total = new long[systems.length];
                long executorBytes = -1;
                for (int s = 0; s < systems.length; s++) {
                    for (int it = 0; it < CQ_WARMUP; it++)
                        systems[s].run();
                    long b0 = s == 0 ? allocatedBytes() : -1;
                    for (int it = 0; it < CQ_PASSES; it++) {
                        long t0 = System.nanoTime();
                        systems[s].run();
                        total[s] += System.nanoTime() - t0;
                    }
                    if (s == 0 && b0 >= 0) {
                        long b1 = allocatedBytes();
                        executorBytes = b1 >= 0 ? (b1 - b0) / CQ_PASSES : -1;
                    }
                }
                for (Mob m : busy)
                    if (CommandQueues.queuedCount(m.getId()) != CQ_QUEUED) {
                        problems.add("the executor started an order on a busy unit");
                        break;
                    }

                // the whole army finishes its current orders at once: every idle unit starts its next order
                for (Mob m : units) {
                    ((Unit) m).getMoveGoal().stopMoving();
                    if (m instanceof AttackerUnit attacker)
                        attacker.setAttackMoveTarget(null);
                    ((Unit) m).setFollowTarget(null);
                }
                List<Mob> idle = new ArrayList<>();
                disagree = 0;
                // workers stay busy here: WorkerUnit.isIdle also waits for the gather goal to have ticked with nothing
                // to do (GatherResourcesGoal.isIdle: a fresh worker sits at its idle timeout), and these units never
                // tick - so only the others are expected to go idle (CI: 320 of 400, the 80 workers busy)
                int canIdle = 0;
                for (Mob m : busy) {
                    if (!(m instanceof WorkerUnit))
                        canIdle++;
                    if (((Unit) m).isIdle() != oldIsIdle(m) && disagree++ == 0)
                        problems.add("isIdle disagrees with the old form for an idle " + m.getType().getDescriptionId());
                    if (((Unit) m).isIdle())
                        idle.add(m);
                }
                int passes = 0, started = 0, firstPassStarts = 0;
                long firstPassNanos = 0;
                while (started < idle.size() && passes < 100) {
                    long t0 = System.nanoTime();
                    CommandQueues.tick(level);
                    long dt = System.nanoTime() - t0;
                    passes++;
                    started = 0;
                    for (Mob m : idle)
                        if (CommandQueues.queuedCount(m.getId()) == CQ_QUEUED - 1)
                            started++;
                    if (passes == 1) {
                        firstPassNanos = dt;
                        firstPassStarts = started;
                    }
                }
                if (idle.size() < canIdle * 9 / 10)
                    problems.add("only " + idle.size() + " of " + canIdle + " non-worker units went idle when their orders were cleared");
                if (started < idle.size())
                    problems.add("after " + passes + " passes only " + started + " of " + idle.size() + " idle units started their next order");

                StringBuilder sb = new StringBuilder();
                for (int s = 0; s < systems.length; s++)
                    sb.append(String.format(java.util.Locale.ROOT, "%s=%.4fms ", names[s], total[s] / 1e6 / CQ_PASSES));
                double executorMs = total[0] / 1e6 / CQ_PASSES;
                ReignOfNether.LOGGER.info("[Stress] command queue: {} units ({} in allUnits, {} busy with {} orders waiting each), avg per pass over {} passes: {}| executor allocation {} bytes/pass | army finishing at once: {} of {} idle units started their next order in {} pass(es), first pass {} orders in {}ms (rtsPathfinding {})",
                    units.size(), UnitServerEvents.getAllUnits().size(), busy.size(), CQ_QUEUED, CQ_PASSES, sb,
                    executorBytes < 0 ? "n/a" : String.valueOf(executorBytes), started, idle.size(), passes, firstPassStarts,
                    String.format(java.util.Locale.ROOT, "%.3f", firstPassNanos / 1e6), UnitServerEvents.rtsPathfinding);
                if (units.size() < CQ_OWNERS * CQ_PER_OWNER * 9 / 10)
                    problems.add("only " + units.size() + " units spawned");
                if (busy.size() < units.size() * 9 / 10)
                    problems.add("only " + busy.size() + " of " + units.size() + " units were busy");
                if (executorBytes > 16)
                    problems.add("the executor allocated " + executorBytes + " bytes per pass with nothing to start");
                if (executorMs > CQ_BUDGET_MS)
                    problems.add(String.format(java.util.Locale.ROOT, "the executor took %.3f ms per pass (budget %.1f ms)", executorMs, CQ_BUDGET_MS));
            } finally {
                for (Mob m : units)
                    m.discard();   // leaving the level drops each queue (CommandQueues.forget)
                GameTestChunks.releaseAll(level, chunks);
            }
            if (!problems.isEmpty())
                helper.fail(String.join("; ", problems));
            else
                helper.succeed();
        };
        // measure once every forced chunk is entity-ticking: a fixed 40 ticks was not always enough (CI: 35 of the
        // 400 units were in chunks still loading, so no order could find them)
        boolean[] ran = { false };
        // generating ~50 fresh chunks at x-3000 can take well over 15 s on a CI runner. Giving up at 300 ticks dropped
        // the tickets mid-generation, and the next batch's structure load then parked the server thread for good (run
        // 38336416146: stack in StructureUtils.forceLoadChunks -> setChunkForced, watchdog kill, no verdict). Wait up
        // to a minute so the chunks finish before they are released.
        // Run 38470339031 (after the duel pads had just generated 380 chunks of their own): a flat 60 s was still not
        // enough. So the rule is the one GameTestChunks.awaitLoaded uses: keep waiting while chunks keep becoming
        // ticking, give up only after 60 s (1200 ticks) with NO progress, hard cap 10 min - a stuck future shows as
        // no progress, honest generation never trips it.
        // "ticking" alone is blind for most of a load: the first chunk turns entity-ticking only once its whole region is
        // FULL, so a slow runner showed "0 of 24, no progress since tick 20" while the cone was still generating
        // (the failure of Oct 22/23 on main and PR #174). Generation progress anywhere in the cone counts too, and
        // the message says where the cone stood, so slow reads differently from stuck.
        final int firstTry = 20, lastTry = 12000, stallTicks = 1200;
        int[] best = { -1 }, lastProgressTick = { firstTry };
        GameTestChunks.ConeMeter cone = new GameTestChunks.ConeMeter(chunks);
        long[] bestScore = { -1 };
        for (int t = firstTry; t <= lastTry; t += 5) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                if (ran[0])
                    return;
                int ticking = 0;
                for (long[] c : chunks)
                    if (level.isPositionEntityTicking(new BlockPos(((int) c[0] << 4) + 8, (int) y, ((int) c[1] << 4) + 8)))
                        ticking++;
                long score = cone.score(level);
                if (ticking > best[0] || score > bestScore[0]) {
                    best[0] = Math.max(best[0], ticking);
                    bestScore[0] = Math.max(bestScore[0], score);
                    lastProgressTick[0] = tick;
                }
                if (ticking == chunks.size()) {
                    ran[0] = true;
                    measure.run();
                } else if (tick - lastProgressTick[0] >= stallTicks || tick + 5 > lastTry) {
                    ran[0] = true;
                    GameTestChunks.releaseAll(level, chunks);   // keeps the ticket of a chunk still loading: see there
                    helper.fail("the benchmark's chunks never became entity-ticking (" + ticking + " of " + chunks.size()
                        + " after " + tick + " ticks, no progress since tick " + lastProgressTick[0] + "; " + cone.describe() + ")");
                }
            });
        }
    }

    static long cqSink;   // keeps the JIT from discarding the reference work

    /** The old pipeline's per-tick work: the slow list scanned from the top for the first item whose unit was idle. */
    static int oldSlowListScan(ServerLevel level, List<UnitActionItem> slow) {
        synchronized (slow) {
            for (UnitActionItem uai : slow)
                if (uai.getUnitIds().length > 0 && level.getEntity(uai.getUnitIds()[0]) instanceof Mob m && oldIsIdle(m))
                    return 1;   // the old loop actioned this one item and stopped for the tick
        }
        return 0;
    }

    /**
     * UnitQueueSync.buildPayloads as it was before rewrite 2 (main d73acee), over a list in the old slow list's shape:
     * everybody's queued items regrouped by unit, then one pass over all units. Copied with its package-private helpers
     * (typeOf, put, the two caps) so the benchmark times old and new on the same orders in the same run.
     */
    static Map<String, IntArrayList> oldQueueLines(List<UnitActionItem> slow) {
        Int2ObjectOpenHashMap<List<UnitActionItem>> queued = new Int2ObjectOpenHashMap<>();
        synchronized (slow) {
            for (UnitActionItem uai : slow) {
                if (uai.getUnitIds().length == 0)
                    continue;
                int id = uai.getUnitIds()[0];
                List<UnitActionItem> list = queued.get(id);
                if (list == null) {
                    list = new ArrayList<>();
                    queued.put(id, list);
                }
                list.add(uai);
            }
        }
        Map<String, IntArrayList> byOwner = new java.util.HashMap<>();
        Map<String, int[]> unitCounts = new java.util.HashMap<>();
        IntArrayList scratch = new IntArrayList();
        for (net.minecraft.world.entity.LivingEntity le : UnitServerEvents.getAllUnits()) {
            if (!(le instanceof Unit unit))
                continue;
            scratch.clear();
            oldCollect(le, unit, queued.get(le.getId()), scratch);
            if (scratch.isEmpty() || !le.isAlive())
                continue;
            String owner = unit.getOwnerName();
            int[] count = unitCounts.computeIfAbsent(owner, k -> new int[1]);
            if (count[0] >= 400)   // MAX_UNITS_PER_PLAYER
                continue;
            count[0]++;
            IntArrayList out = byOwner.computeIfAbsent(owner, k -> new IntArrayList());
            out.add(le.getId());
            out.add(scratch.size() / UnitQueueSync.INTS_PER_ENTRY);
            out.addAll(scratch);
        }
        return byOwner;
    }

    static void oldCollect(net.minecraft.world.entity.LivingEntity le, Unit unit, List<UnitActionItem> queue, IntArrayList out) {
        Entity reclaim = le instanceof WorkerUnit ? com.solegendary.reignofnether.resources.WreckServerEvents.getReclaimTarget(le) : null;
        BuildingPlacement building = le instanceof WorkerUnit wu && wu.getBuildRepairGoal() != null
            ? wu.getBuildRepairGoal().getBuildingTarget() : null;
        if (reclaim != null) {
            oldPut(out, UnitQueueSync.TYPE_RECLAIM, reclaim.blockPosition(), reclaim.getId());
        } else if (building != null) {
            oldPut(out, UnitQueueSync.TYPE_BUILD, building.centrePos, -1);
        } else if (le instanceof AttackerUnit au && au.getAttackMoveTarget() != null) {
            oldPut(out, UnitQueueSync.TYPE_ATTACK, au.getAttackMoveTarget(), -1);
        } else if (unit.getMoveGoal() != null && unit.getMoveGoal().getMoveTarget() != null) {
            oldPut(out, UnitQueueSync.TYPE_MOVE, unit.getMoveGoal().getMoveTarget(), -1);
        } else if (queue != null && unit.getTargetGoal() != null && unit.getTargetGoal().getTarget() != null) {
            oldPut(out, UnitQueueSync.TYPE_ATTACK, BlockPos.ZERO, unit.getTargetGoal().getTarget().getId());
        }
        if (queue == null)
            return;
        for (UnitActionItem uai : queue) {
            if (out.size() >= 16 * UnitQueueSync.INTS_PER_ENTRY)   // MAX_ENTRIES_PER_UNIT
                break;
            int type = switch (uai.getAction()) {
                case MOVE, PATROL, FOLLOW, GARRISON, GUARD -> UnitQueueSync.TYPE_MOVE;
                case ATTACK, ATTACK_MOVE, ATTACK_BUILDING -> UnitQueueSync.TYPE_ATTACK;
                case BUILD_REPAIR, FARM -> UnitQueueSync.TYPE_BUILD;
                case RECLAIM -> UnitQueueSync.TYPE_RECLAIM;
                default -> -1;
            };
            if (type < 0)
                continue;
            BlockPos pos = uai.getPreselectedBlockPos();
            int eid = -1;
            if (uai.getAction() == UnitAction.BUILD_REPAIR) {
                BuildingPlacement b = com.solegendary.reignofnether.building.BuildingUtils.findBuilding(false, pos);
                if (b != null)
                    pos = b.centrePos;
            } else if (uai.getAction() == UnitAction.ATTACK || uai.getAction() == UnitAction.FOLLOW
                    || uai.getAction() == UnitAction.RECLAIM) {
                eid = uai.getTargetUnitId();
            }
            if (pos == null || (eid < 0 && pos.getX() == 0 && pos.getY() == 0 && pos.getZ() == 0))
                continue;
            oldPut(out, type, pos, eid);
        }
    }

    static void oldPut(IntArrayList out, int type, BlockPos pos, int eid) {
        out.add(type);
        out.add(pos.getX());
        out.add(pos.getY());
        out.add(pos.getZ());
        out.add(eid);
    }

    /** Unit.isIdle as it was before rewrite 2: every part computed, the goal scan first. The new form must agree. */
    static boolean oldIsIdle(Mob mob) {
        Unit unit = (Unit) mob;
        boolean idleAttacker = true;
        if (mob instanceof AttackerUnit attackerUnit)
            idleAttacker = attackerUnit.getAttackMoveTarget() == null && !unit.hasLivingTarget()
                && !AttackerUnit.isAttackingBuilding(attackerUnit);
        boolean idleRangedAttacker = true;
        if (mob instanceof RangedAttackerUnit rangedAttackerUnit)
            idleRangedAttacker = rangedAttackerUnit.getRangedAttackGroundGoal() == null
                || rangedAttackerUnit.getRangedAttackGroundGoal().getGroundTarget() == null;
        boolean idleWorker = true;
        if (mob instanceof WorkerUnit workerUnit)
            idleWorker = WorkerUnit.isIdle(workerUnit);
        for (Goal goal : mob.goalSelector.getAvailableGoals()) {
            if (goal instanceof GenericUntargetedSpellGoal spellGoal && spellGoal.isCasting())
                return false;
            if (goal instanceof GenericTargetedSpellGoal spellGoal && spellGoal.isCasting())
                return false;
        }
        boolean stationaryNearMoveTarget = false;
        if (unit.getMoveGoal().getMoveTarget() != null) {
            double distToMoveTarget = mob.distanceToSqr(unit.getMoveGoal().getMoveTarget().getCenter());
            Vec3 dm = mob.getDeltaMovement();
            boolean stationary = Math.abs(dm.x) < 1.0e-3 && Math.abs(dm.z) < 1.0e-3;
            stationaryNearMoveTarget = stationary && distToMoveTarget < 4;
        }
        boolean isMoving = !mob.getNavigation().isDone() || unit.getMoveGoal().getMoveTarget() != null;
        return (!isMoving || stationaryNearMoveTarget) && unit.getFollowTarget() == null && idleAttacker && idleWorker
            && idleRangedAttacker && (unit.getItemGoal() == null || unit.getItemGoal().isIdle());
    }

    /** Bytes this thread has allocated so far (HotSpot's counter), or -1 where the JVM can't tell. */
    static long allocatedBytes() {
        try {
            if (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean mx
                    && mx.isThreadAllocatedMemorySupported() && mx.isThreadAllocatedMemoryEnabled())
                return mx.getCurrentThreadAllocatedBytes();
        } catch (Throwable t) {
            ReignOfNether.LOGGER.warn("[Stress] command queue: no allocation counter ({})", t.toString());
        }
        return -1;
    }

    // ------------------------------------------------------------------ helpers

    /** Encodes the packet, optionally overwrites its last byte (the queue mode) and decodes it again. */
    static QueueMode roundTrip(UnitActionServerboundPacket packet, int lastByte) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.encode(buf);
            if (lastByte >= 0)
                buf.setByte(buf.writerIndex() - 1, lastByte);
            return new UnitActionServerboundPacket(buf).getQueueMode();
        } finally {
            buf.release();
        }
    }

    /** This unit's queue-line entries for its owner, from UnitQueueSync, each {type, x, y, z, entityId}. */
    static List<int[]> lines(String owner, int unitId) {
        List<int[]> out = new ArrayList<>();
        Map<String, IntArrayList> payloads = UnitQueueSync.buildPayloads();
        IntArrayList data = payloads.get(owner);
        if (data == null)
            return out;
        int i = 0;
        while (i + 1 < data.size()) {
            int id = data.getInt(i++), n = data.getInt(i++);
            for (int e = 0; e < n; e++, i += UnitQueueSync.INTS_PER_ENTRY)
                if (id == unitId)
                    out.add(new int[] { data.getInt(i), data.getInt(i + 1), data.getInt(i + 2), data.getInt(i + 3), data.getInt(i + 4) });
        }
        return out;
    }

    static boolean isLine(int[] line, int type, BlockPos pos, int entityId) {
        return line[0] == type && line[1] == pos.getX() && line[2] == pos.getY() && line[3] == pos.getZ() && line[4] == entityId;
    }

    static String describe(List<int[]> lines) {
        List<String> out = new ArrayList<>();
        for (int[] l : lines)
            out.add(java.util.Arrays.toString(l));
        return out.toString();
    }

    /** A short owner name unique to this test instance (fake players' names must stay within 16 characters). */
    static String owner(GameTestHelper helper, String tag) {
        return "cq" + tag + "_" + Long.toString(Math.abs(helper.absolutePos(BlockPos.ZERO).asLong()) % 1679616L, 36);
    }

    /** The block a unit stands in at arena coordinates (x, z): the arena's floor is grass at y=1. */
    static BlockPos at(GameTestHelper helper, int x, int z) {
        return helper.absolutePos(new BlockPos(x, 2, z));
    }

    /** Forty distinct spots inside the arena. */
    static BlockPos waypoint(GameTestHelper helper, int i) {
        return at(helper, 2 + i % 12, 2 + (i / 12) * 4);
    }

    /** A Vindicator for {@code owner} at arena (x, z) that never picks or answers a fight on its own. */
    static Mob fighter(GameTestHelper helper, String owner, int x, int z) {
        Mob mob = EntityRegistrar.VINDICATOR_UNIT.get().create(helper.getLevel());
        if (mob == null)
            return null;
        BlockPos p = at(helper, x, z);
        mob.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
        ((Unit) mob).setOwnerName(owner);
        EngagementStance.set(mob, EngagementStance.HOLD_FIRE);
        helper.getLevel().addFreshEntity(mob);
        return mob;
    }

    static FakePlayer fakePlayer(ServerLevel level, String name) {
        return FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
    }

    static BlockPos moveTarget(Mob unit) {
        return ((Unit) unit).getMoveGoal().getMoveTarget();
    }

    /** The unit's queued (not yet started) orders as their target positions, oldest first. */
    static List<BlockPos> positions(Mob unit) {
        List<BlockPos> out = new ArrayList<>();
        for (UnitActionItem item : UnitServerEvents.getQueuedActions(unit.getId()))
            out.add(item.getPreselectedBlockPos());
        return out;
    }

    /** What arriving does to a move order: the move goal lets go of its target and the unit is idle again. */
    static void finish(Mob unit) {
        ((Unit) unit).getMoveGoal().stopMoving();
    }
}
