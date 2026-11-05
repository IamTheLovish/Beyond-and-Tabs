package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.unit.command.QueueMode;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.packets.UnitActionServerboundPacket;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Backlog 5b (Command Center Eris.11), from the Mode A review on #35 (Oct 28, "Fog leaks"): an order whose target the
 * sender's fog hides is dropped on the server, in UnitActionServerboundPacket.accept - the method every unit-order
 * packet goes through. The test server runs with fog off for everyone, so these call accept's overload with the
 * visibility rule set by the test (the packet path passes FogOfWarServerEvents.isEntityVisibleTo); the refusal, the
 * return value and the command queues are the real ones.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class UnitActionFogGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** The unit's attack target: its target goal's, else the mob's own. */
    static Entity target(Mob unit) {
        Entity t = ((Unit) unit).getTargetGoal().getTarget();
        return t != null ? t : unit.getTarget();
    }

    /**
     * Unseen target: an ATTACK on it is refused (false), the unit takes no target and queues nothing; so is a GUARD
     * on it. Seen target: the same ATTACK goes through and the unit takes it. No target (MOVE, unitId -1): never
     * refused, whatever the rule.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void an_order_on_a_target_the_sender_cannot_see_is_refused_and_a_seen_one_obeyed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = CommandQueueGameTests.owner(helper, "fogme"), foe = CommandQueueGameTests.owner(helper, "fogfoe");
        FakePlayer sender = CommandQueueGameTests.fakePlayer(level, me);
        Mob mine = CommandQueueGameTests.fighter(helper, me, 2, 2);
        Mob enemy = CommandQueueGameTests.fighter(helper, foe, 9, 2);
        if (mine == null || enemy == null) {
            if (mine != null) mine.discard();
            if (enemy != null) enemy.discard();
            helper.fail("could not create the units");
            return;
        }
        TestCleanup.entities(helper, List.of(mine, enemy));
        int[] ids = { mine.getId() };
        BiPredicate<net.minecraft.server.level.ServerPlayer, Entity> hidden = (p, e) -> e != enemy;
        BiPredicate<net.minecraft.server.level.ServerPlayer, Entity> seen = (p, e) -> true;
        List<String> problems = new ArrayList<>();
        helper.runAfterDelay(2, () -> {
            if (UnitActionServerboundPacket.accept(sender, me, UnitAction.ATTACK, enemy.getId(), ids, enemy.blockPosition(),
                    BlockPos.ZERO, QueueMode.REPLACE, hidden))
                problems.add("an ATTACK on a target the sender cannot see was accepted");
            if (UnitActionServerboundPacket.accept(sender, me, UnitAction.GUARD, enemy.getId(), ids, enemy.blockPosition(),
                    BlockPos.ZERO, QueueMode.REPLACE, hidden))
                problems.add("a GUARD on a unit the sender cannot see was accepted");
        });
        helper.runAfterDelay(4, () -> {
            if (target(mine) == enemy)
                problems.add("the unit took the unseen target");
            if (UnitServerEvents.hasQueuedActions(mine.getId()))
                problems.add("the refused order left something in the unit's queue");
            BlockPos to = CommandQueueGameTests.at(helper, 4, 8);
            if (!UnitActionServerboundPacket.accept(sender, me, UnitAction.MOVE, -1, ids, to, BlockPos.ZERO, QueueMode.REPLACE, hidden))
                problems.add("a MOVE (no target) was refused");
            else if (!to.equals(CommandQueueGameTests.moveTarget(mine)))
                problems.add("the MOVE was accepted but the unit's move target is " + CommandQueueGameTests.moveTarget(mine));
        });
        helper.runAfterDelay(6, () -> {
            if (!UnitActionServerboundPacket.accept(sender, me, UnitAction.ATTACK, enemy.getId(), ids, enemy.blockPosition(),
                    BlockPos.ZERO, QueueMode.REPLACE, seen))
                problems.add("an ATTACK on a target the sender can see was refused");
        });
        helper.runAfterDelay(8, () -> {
            if (target(mine) != enemy)
                problems.add("after the seen ATTACK the unit's target is " + target(mine) + ", not the enemy");
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        });
    }
}
