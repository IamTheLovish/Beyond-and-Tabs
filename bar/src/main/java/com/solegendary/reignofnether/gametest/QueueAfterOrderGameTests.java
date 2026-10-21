package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.EngagementStance;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.command.CommandQueues;
import com.solegendary.reignofnether.unit.command.QueueMode;
import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Lane C playtest #12 (Oct 20): "queue a move after any order - Shift+move after a build or an attack order runs once
 * that order is done". The command queue (unit/command) appends a Shift order behind the one being carried out; this
 * pins that down for an attack: the move waits while the target lives, then runs.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class QueueAfterOrderGameTests {

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 400)
    public static void shift_move_after_an_attack_waits_for_the_kill(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_queue_after_" + helper.absolutePos(BlockPos.ZERO).asLong();
        VindicatorUnit attacker = EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        VindicatorUnit foe = EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (attacker == null || foe == null) {
            helper.fail("could not create the units");
            return;
        }
        BlockPos o = helper.absolutePos(new BlockPos(0, 2, 0));
        attacker.moveTo(o.getX() + 3.5, o.getY(), o.getZ() + 3.5, 0, 0);
        foe.moveTo(o.getX() + 6.5, o.getY(), o.getZ() + 3.5, 0, 0);
        attacker.setOwnerName(owner);
        foe.setOwnerName(owner + "_foe");
        EngagementStance.set(foe, EngagementStance.HOLD_FIRE);   // the target just stands there
        level.addFreshEntity(attacker);
        level.addFreshEntity(foe);
        // a wounded target: a full Vindicator (75 hp) takes ~25 s at 6 dmg / 2 s - longer than the test's 20 s (CI run
        // 39556286185: "still attacking (foe hp 14.0, 1.8 blocks apart)", the attacker on it the whole time). At 18 hp
        // it dies in three hits, so the test judges the queue, not the kill speed.
        foe.setHealth(18f);
        BlockPos spot = helper.absolutePos(new BlockPos(3, 2, 13));
        boolean[] movedEarly = { false };
        helper.runAfterDelay(3, () -> {
            CommandQueues.issue(level, owner, UnitAction.ATTACK, foe.getId(), new int[] { attacker.getId() },
                BlockPos.ZERO, BlockPos.ZERO, QueueMode.REPLACE);
            CommandQueues.issue(level, owner, UnitAction.MOVE, -1, new int[] { attacker.getId() },
                spot, BlockPos.ZERO, QueueMode.APPEND);
        });
        helper.succeedWhen(() -> {
            if (helper.getTick() < 6)
                throw new GameTestAssertException("orders not given yet");
            double toSpot = Math.hypot(attacker.getX() - (spot.getX() + 0.5), attacker.getZ() - (spot.getZ() + 0.5));
            if (foe.isAlive()) {
                // while the target lives the attacker must be on it, not walking to the queued spot
                if (toSpot < 4)
                    movedEarly[0] = true;
                if (movedEarly[0])
                    helper.fail("the Shift+move ran before the attack target died (attacker "
                        + Math.round(toSpot) + " blocks from the spot, foe hp " + foe.getHealth() + ")");
                var q = CommandQueues.get(attacker.getId());
                throw new GameTestAssertException("still attacking (foe hp " + foe.getHealth() + ", "
                    + String.format("%.1f", attacker.distanceTo(foe)) + " blocks apart); attacker target "
                    + (attacker.getTargetGoal().getTarget() == null ? "none" : attacker.getTargetGoal().getTarget() == foe ? "the foe" : "another")
                    + ", move target " + attacker.getMoveGoal().getMoveTarget() + ", nav done " + attacker.getNavigation().isDone()
                    + ", queue current " + (q == null || q.current() == null ? "none" : q.current().action())
                    + " waiting " + (q == null ? 0 : q.size()) + ", idle " + attacker.isIdle());
            }
            if (toSpot > 2.5)
                throw new GameTestAssertException("the target is dead; the queued move has not arrived ("
                    + Math.round(toSpot) + " blocks to go)");
            CommandQueues.issue(level, owner, UnitAction.STOP, -1, new int[] { attacker.getId() },
                BlockPos.ZERO, BlockPos.ZERO, QueueMode.REPLACE);
            attacker.discard();
            foe.discard();
        });
    }
}
