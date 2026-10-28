package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.bot.BotArmy;
import com.solegendary.reignofnether.bot.BotPlayer;
import com.solegendary.reignofnether.bot.BotServerEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.onboarding.PassiveBot;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The Passive AI difficulty: Easy's economy and base, but it never sends units out (so a tester is never attacked).
 * Its own batch "passive_ai". AC-1 and AC-2 share one run (a Passive Sunforged bot next to an idle enemy base, so
 * there is always a known target to be tempted by); AC-3 is the difficulty cycle and the name parse.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class PassiveAiGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "passive_ai";
    /** Game-minutes of simulated play. */
    static final int MINUTES = 5;
    static final int TICKS = 20 * 60 * MINUTES;

    /**
     * AC-1 and AC-2: over MINUTES game-minutes a Passive bot gives no attack-move or attack order whose target lies
     * outside its base radius, its army never enters the ATTACK state, and it still raises a metal extractor and its
     * army building (the faction's factory).
     */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = TICKS + 600)
    public static void passive_ai_never_attacks_outside_its_base_but_still_builds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String passive = "gametest_passive_bot", dummy = "gametest_passive_target";
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        Vec3[] starts = SkirmishGameTests.soakAnyRow(level, origin.getX() + 1500, origin.getZ(), 2);
        if (starts == null) {
            helper.fail("no dry ground for two bot bases near x=" + (origin.getX() + 1500));
            return;
        }
        var bots = BotServerEvents.brains;
        var failures = BotServerEvents.thinkFailures;
        failures.remove(passive);
        failures.remove(dummy);
        BlockPos home = BlockPos.containing(starts[0]);
        PlayerServerEvents.startRTSBot(level, passive, starts[0], Factions.VILLAGERS, 0);
        PlayerServerEvents.startRTSBot(level, dummy, starts[1], Factions.VILLAGERS, 0);
        BotPlayer brain = new BotPlayer(passive, Factions.VILLAGERS, BotPlayer.Difficulty.PASSIVE, home);
        List<String> outside = new ArrayList<>();
        int[] orders = { 0 };
        brain.orderListener = (action, target) -> {
            orders[0]++;
            boolean attack = action == UnitAction.ATTACK_MOVE || action == UnitAction.ATTACK
                || action == UnitAction.ATTACK_BUILDING;
            double dx = target.getX() - home.getX(), dz = target.getZ() - home.getZ();
            if (attack && dx * dx + dz * dz > BotArmy.BASE_RADIUS * BotArmy.BASE_RADIUS)
                outside.add(action + " to " + target.toShortString());
        };
        bots.put(passive, brain);
        // the other base never thinks (onboarding's sparring partner), so nothing interrupts the Passive bot
        bots.put(dummy, new PassiveBot(dummy, Factions.VILLAGERS, BlockPos.containing(starts[1])));
        var kit = BotPlayer.kitFor(Factions.VILLAGERS);

        boolean[] finished = { false }, attackState = { false };
        long[] extractorAt = { -1 }, factoryAt = { -1 };
        long t0 = level.getGameTime();
        Runnable finish = () -> {
            if (finished[0])
                return;
            finished[0] = true;
            List<String> problems = new ArrayList<>();
            if (failures.get(passive) != null)
                problems.add("think threw " + failures.get(passive));
            if (!outside.isEmpty())
                problems.add("AC-1: attack orders outside the base radius: " + outside);
            if (attackState[0])
                problems.add("AC-1: the Passive army entered the ATTACK state");
            if (extractorAt[0] < 0)
                problems.add("AC-2: no metal extractor in " + MINUTES + " minutes (blocked " + brain.lastBlockedReason + ")");
            if (factoryAt[0] < 0)
                problems.add("AC-2: no army building in " + MINUTES + " minutes (blocked " + brain.lastBlockedReason + ")");
            ReignOfNether.LOGGER.info("[PassiveAi] orders {} extractor at {} factory at {} army state {}", orders[0],
                extractorAt[0], factoryAt[0], brain.army.state);
            List<LivingEntity> leftovers = new ArrayList<>();
            for (LivingEntity le : UnitServerEvents.getAllUnits())
                if (le instanceof Unit u && (passive.equals(u.getOwnerName()) || dummy.equals(u.getOwnerName())))
                    leftovers.add(le);
            for (String n : List.of(passive, dummy)) {
                PlayerServerEvents.defeat(n, "gametest finished");
                bots.remove(n);
                failures.remove(n);
            }
            leftovers.forEach(net.minecraft.world.entity.Entity::discard);
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        };
        // a fresh Runnable per sample (GameTestInfo keys its delayed tasks by the Runnable itself)
        for (int t = 20; t <= TICKS; t += 20) {
            final int tick = t;
            helper.runAfterDelay(t, new Runnable() {
                @Override
                public void run() {
                    if (finished[0])
                        return;
                    long now = level.getGameTime() - t0;
                    if (brain.army.state == BotArmy.State.ATTACK)
                        attackState[0] = true;
                    for (BuildingPlacement bp : BuildingServerEvents.getBuildings()) {
                        if (!passive.equals(bp.ownerName) || bp.isDestroyedServerside)
                            continue;
                        if (extractorAt[0] < 0 && bp.getBuilding() == kit.extractor())
                            extractorAt[0] = now;
                        if (factoryAt[0] < 0 && bp.getBuilding() == kit.armyBuilding())
                            factoryAt[0] = now;
                    }
                    if (tick >= TICKS - 20)
                        finish.run();
                }
            });
        }
    }

    /** AC-3: Passive is the last step of the lobby's difficulty cycle and parses from "passive" (command and name). */
    @GameTest(template = ARENA, batch = BATCH)
    public static void passive_is_in_the_difficulty_cycle_and_parses(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        BotPlayer.Difficulty[] cycle = BotPlayer.Difficulty.values();   // the lobby buttons cycle through these in order
        if (cycle.length != 4 || cycle[3] != BotPlayer.Difficulty.PASSIVE || !"Passive".equals(cycle[3].label()))
            problems.add("Passive is not the fourth step of the cycle " + java.util.Arrays.toString(cycle));
        if (BotServerEvents.difficultyFromArg("passive") != BotPlayer.Difficulty.PASSIVE
                || BotServerEvents.difficultyFromArg("PASSIVE") != BotPlayer.Difficulty.PASSIVE)
            problems.add("\"passive\" does not parse to PASSIVE");
        if (BotServerEvents.difficultyFromName("Bot4 (passive)") != BotPlayer.Difficulty.PASSIVE)
            problems.add("\"Bot4 (passive)\" does not read back as PASSIVE");
        if (BotServerEvents.difficultyFromArg("easy") != BotPlayer.Difficulty.EASY
                || BotServerEvents.difficultyFromArg("hard") != BotPlayer.Difficulty.HARD
                || BotServerEvents.difficultyFromArg("far") != BotPlayer.Difficulty.MEDIUM)
            problems.add("the existing difficulty words changed");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
