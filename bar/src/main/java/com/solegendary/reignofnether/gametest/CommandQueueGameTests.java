package com.solegendary.reignofnether.gametest;

import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.EngagementStance;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitActionItem;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.packets.UnitActionServerboundPacket;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
            if (r0.equals(target))
                sawFirst[0] = true;
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

    // ------------------------------------------------------------------ helpers

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
