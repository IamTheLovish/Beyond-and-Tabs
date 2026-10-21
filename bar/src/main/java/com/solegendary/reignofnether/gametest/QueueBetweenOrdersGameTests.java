package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.command.CommandQueues;
import com.solegendary.reignofnether.unit.command.QueueMode;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A unit with an order queued behind its current one (Shift+order) does not pick a fight of its own between the two.
 *
 * Between finishing an order and the queue starting the next - the end of the same server tick, CommandQueues.tick,
 * after every unit has ticked - a unit is idle for one moment, and AttackerUnit.tick's idle auto-aggression (every
 * fourth tick of the unit's own count, {@code tickCount % 4}) used to take whoever stood closest as its new target in
 * it. The queue then found the unit busy, and the queued move waited until that fight was over. Killing its target is
 * the usual way to be idle at that moment, and in a scrum the nearest enemy is often not the one just killed.
 *
 * Four attackers, each ordered onto a foe with a move queued behind it, each with an enemy standing closer (0.9
 * blocks) than its foe (1.2). Each foe dies on a tick chosen so that the four attackers' next own ticks have the four
 * different {@code tickCount % 4}: one of them is always on the idle check's tick. Before the fix that one took the
 * enemy beside it and its move never started; all four now go. A fifth attacker with nothing queued stands as the
 * control: it still takes the enemy beside it, so the test cannot pass because the enemies were not targetable.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class QueueBetweenOrdersGameTests {

    static final int QUEUED = 4;
    /** Ticks after a kill by which the queue has started the next order (it does so at the end of the same tick). */
    static final int AFTER_KILL = 14;

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 480)
    public static void a_queued_move_starts_though_an_enemy_stands_closer_than_the_kill(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String mine = "gametest_queue_between_" + helper.absolutePos(BlockPos.ZERO).asLong(), theirs = mine + "_foe";
        BlockPos o = helper.absolutePos(new BlockPos(0, 2, 0));
        List<Entity> all = new ArrayList<>();
        List<Mob> attackers = new ArrayList<>(), foes = new ArrayList<>(), bystanders = new ArrayList<>();
        List<BlockPos> spots = new ArrayList<>();
        // columns 0-3 have a move queued; column 4 (south, out of the others' way) is the control
        for (int c = 0; c <= QUEUED; c++) {
            double bx = c < QUEUED ? 1.5 + 4 * c : 7.5, bz = c < QUEUED ? 2.5 : 14.0;
            Mob a = TerrainGameTests.spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), mine, o.getX() + bx, o.getY(), o.getZ() + bz, all);
            Mob foe = TerrainGameTests.spawn(level, EntityRegistrar.VILLAGER_UNIT.get(), theirs, o.getX() + bx + 1.2, o.getY(), o.getZ() + bz, all);
            Mob by = TerrainGameTests.spawn(level, EntityRegistrar.VILLAGER_UNIT.get(), theirs, o.getX() + bx - 0.9, o.getY(), o.getZ() + bz, all);
            if (a == null || foe == null || by == null) {
                TerrainGameTests.cleanUp(all);
                helper.fail("could not create the units");
                return;
            }
            a.setInvulnerable(true);
            foe.setNoAi(true);
            by.setNoAi(true);
            by.setInvulnerable(true);   // stands there for good: whoever takes it as a target keeps it
            attackers.add(a);
            foes.add(foe);
            bystanders.add(by);
            spots.add(helper.absolutePos(new BlockPos(1 + 4 * c, 2, 9)));
        }
        int[] ordered = { -1 }, killBase = { -1 };
        long[] killedAt = new long[QUEUED + 1];
        Arrays.fill(killedAt, -1L);
        boolean[] checked = new boolean[QUEUED + 1];
        List<String> problems = new ArrayList<>();
        boolean[] done = { false };

        ArenaReady.whenTicking(helper, all, 150, tick -> {
            ordered[0] = tick;
            for (int i = 0; i <= QUEUED; i++) {
                int[] id = { attackers.get(i).getId() };
                CommandQueues.issue(level, mine, UnitAction.ATTACK, foes.get(i).getId(), id,
                    BlockPos.ZERO, BlockPos.ZERO, QueueMode.REPLACE);
                if (i < QUEUED)
                    CommandQueues.issue(level, mine, UnitAction.MOVE, -1, id, spots.get(i), BlockPos.ZERO, QueueMode.APPEND);
            }
        });

        helper.onEachTick(() -> {
            if (ordered[0] < 0 || done[0])
                return;
            long now = helper.getTick();
            if (killBase[0] < 0) {
                // every attacker standing on its foe, not walking, before the kills start
                boolean settled = now >= ordered[0] + 6;
                for (int i = 0; i <= QUEUED && settled; i++) {
                    Mob a = attackers.get(i);
                    if (!a.getNavigation().isDone() || !foes.get(i).isAlive() || a.distanceTo(foes.get(i)) > 1.6)
                        settled = false;
                }
                if (!settled) {
                    if (now > ordered[0] + 120) {
                        done[0] = true;
                        throw new GameTestAssertException("the attackers never settled on their foes: "
                            + ArenaReady.describe(level, attackers));
                    }
                    return;
                }
                killBase[0] = (int) now;
            }
            // Queued attacker i's foe dies when that attacker's next own tick has tickCount % 4 == i (the tick count
            // goes up before the unit ticks): the four of them cover every phase, whatever their tick counts are. The
            // control has no phase to hit.
            for (int i = 0; i <= QUEUED; i++) {
                Mob a = attackers.get(i);
                if (killedAt[i] < 0 && (i == QUEUED || (a.tickCount + 1) % 4 == i)) {
                    foes.get(i).kill();
                    killedAt[i] = now;
                }
            }
            for (int i = 0; i <= QUEUED; i++) {
                if (checked[i] || killedAt[i] < 0 || now < killedAt[i] + AFTER_KILL)
                    continue;
                checked[i] = true;
                Mob a = attackers.get(i);
                if (i < QUEUED) {
                    var q = CommandQueues.get(a.getId());
                    boolean started = q != null && q.current() != null && q.current().action() == UnitAction.MOVE;
                    if (!started)
                        problems.add("attacker " + i + " (its foe died so that its next own tick had tickCount % 4 = " + i
                            + ") never started its queued move: queue current "
                            + (q == null || q.current() == null ? "none" : q.current().action()) + ", waiting "
                            + (q == null ? 0 : q.size()) + ", target " + role(a, foes.get(i), bystanders.get(i))
                            + ", idle " + ((Unit) a).isIdle());
                } else if (a.getTarget() != bystanders.get(i)) {
                    problems.add("the control, with no order queued, did not take the enemy standing beside it (target "
                        + role(a, foes.get(i), bystanders.get(i)) + "): the enemies are not auto-targetable and this test "
                        + "proves nothing");
                }
            }
            for (boolean c : checked)
                if (!c)
                    return;
            done[0] = true;
            try {
                if (problems.isEmpty())
                    helper.succeed();
                else
                    helper.fail(String.join("; ", problems));
            } finally {
                TerrainGameTests.cleanUp(all);
            }
        });
    }

    /** Who a unit is targeting, by role. */
    private static String role(Mob unit, Mob foe, Mob bystander) {
        var target = unit.getTarget();
        if (target == null)
            return "none";
        return target == foe ? "its foe" : target == bystander ? "the enemy beside it" : "another";
    }
}
