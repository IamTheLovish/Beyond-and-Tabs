package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.command.CommandQueues;
import com.solegendary.reignofnether.unit.command.QueueMode;
import com.solegendary.reignofnether.unit.units.villagers.WitchUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * "Support units don't engage when given an attack order" (Joisco's playtest, Oct 23, issue #35) as a rule that reads
 * only what a player sees: positions and health, no SupportState, so it runs on any build of the game. The healer AI
 * branch's SupportAiGameTests check the same order through the brain's own counters; this class is the player's
 * view of it, and the proof that main failed it (a Field Medic told to attack walked onto the enemy, because a unit
 * that cannot attack was given "follow the enemy" - a healer in melee dies, and one that never gets its cast off
 * has not engaged).
 *
 * The test stands on its own glass floor (ArenaReady.pad, a height no other pad is within 11 of) in its own batch.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class SupportEngageGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "support_engage";

    /**
     * A Field Medic with an ATTACK order on an enemy 13 blocks away (well outside her 8-block throw range, so she has
     * to walk): she walks into throw range and the enemy is hurt by what she throws - she has engaged - and she never
     * gets within 4 blocks of it (a melee unit's reach plus a step), where a medic dies. The enemy stands still with no
     * AI, so nothing but the order moves her.
     */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 500)
    public static void field_medic_ordered_to_attack_walks_into_range_and_hurts_the_target(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos p = ArenaReady.pad(helper, 206);
        List<Entity> made = new ArrayList<>();
        String mine = "gametest_engage_" + helper.absolutePos(BlockPos.ZERO).asLong(), theirs = mine + "_foe";
        Mob medic = ReactionGameTests.spawn(level, EntityRegistrar.WITCH_UNIT.get(), mine, p.getX() + 1.5, p.getY(), p.getZ() + 8.5, made);
        Mob foe = ReactionGameTests.spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), theirs, p.getX() + 14.5, p.getY(), p.getZ() + 8.5, made);
        if (medic == null || foe == null) {
            cleanUp(made);
            helper.fail("could not create the medic and the vindicator");
            return;
        }
        foe.setNoAi(true);
        double range = ((WitchUnit) medic).getPotionThrowRange();
        double[] startDist = { -1 }, closest = { Double.MAX_VALUE };
        int[] start = { -1 };
        boolean[] done = { false };
        ArenaReady.whenTicking(helper, made, 150, tick -> {
            start[0] = tick;
            startDist[0] = flat(medic, foe);
            CommandQueues.issue(level, mine, UnitAction.ATTACK, foe.getId(), new int[] { medic.getId() },
                foe.blockPosition(), BlockPos.ZERO, QueueMode.REPLACE);
        });
        helper.onEachTick(() -> {
            if (start[0] < 0 || done[0])
                return;
            closest[0] = Math.min(closest[0], flat(medic, foe));
            int t = (int) (helper.getTick() - start[0]);
            boolean hurt = foe.getHealth() < foe.getMaxHealth();
            boolean inRange = flat(medic, foe) <= range + 0.5;
            if (!(hurt && inRange) && t < 260)
                return;
            done[0] = true;
            try {
                String where = "after " + t + " ticks: started " + ReactionGameTests.f(startDist[0]) + " off, now "
                    + ReactionGameTests.f(flat(medic, foe)) + " (throw range " + ReactionGameTests.f(range) + "), closest "
                    + ReactionGameTests.f(closest[0]) + ", enemy hp " + ReactionGameTests.f(foe.getHealth()) + "/"
                    + ReactionGameTests.f(foe.getMaxHealth()) + ", medic alive " + medic.isAlive();
                ReignOfNether.LOGGER.info("[SupportEngage] attack order: {}", where);
                List<String> problems = new ArrayList<>();
                if (!inRange)
                    problems.add("the medic did not walk into throw range of the target");
                if (!hurt)
                    problems.add("the target was never hurt: the medic did not act on the order");
                if (closest[0] < 4.0)
                    problems.add("the medic walked within 4 blocks of the enemy melee unit");
                if (!medic.isAlive())
                    problems.add("the medic died");
                if (problems.isEmpty())
                    helper.succeed();
                else
                    helper.fail(String.join("; ", problems) + " (" + where + ")");
            } finally {
                cleanUp(made);
            }
        });
    }

    private static double flat(Entity a, Entity b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    private static void cleanUp(List<Entity> made) {
        for (Entity e : made) {
            if (e instanceof Mob m)
                CommandQueues.clear(m.getId());
            if (!e.isRemoved())
                e.discard();
        }
    }
}
