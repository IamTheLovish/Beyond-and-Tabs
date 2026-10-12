package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.bot.BotPlayer;
import com.solegendary.reignofnether.bot.BotServerEvents;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.CommanderServerEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Regressions from lovish's Oct 12 Watch Battle clips (design/playtest_oct12.md). Own holder, same arena template as
 * SkirmishGameTests, unique owner names and no global counts, so they run in parallel with everything else.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class PlaytestGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    private static List<LivingEntity> unitsOf(String owner) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity le : UnitServerEvents.getAllUnits())
            if (le instanceof Unit u && owner.equals(u.getOwnerName()) && le.isAlive())
                out.add(le);
        return out;
    }

    /**
     * Clip 01-23-30: three of four hard bots sat at +0 income with an untouched 1000/1000 pool for the whole clip -
     * no worker, no capitol, so BotPlayer.think returned at its capitol step every second forever. A bot seated
     * through the real start (startedAtTick) that finds itself with no worker and no finished capitol now reloads its
     * home chunks and then re-seats its three starting workers, the first its commander (BotPlayer.lostStart). A side
     * built by hand (no startedAtTick, like the other tests' sides) is left alone.
     */
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void bot_with_a_lost_start_reseats_its_workers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String lost = "gametest_pt_lost", handmade = "gametest_pt_handmade";
        BlockPos home = helper.absolutePos(new BlockPos(5, 2, 5));
        BlockPos home2 = helper.absolutePos(new BlockPos(12, 2, 12));
        RTSPlayer lostSide = RTSPlayer.getNewBot(lost, Factions.VILLAGERS);
        RTSPlayer handSide = RTSPlayer.getNewBot(handmade, Factions.VILLAGERS);
        var rtsPlayers = PlayerServerEvents.rtsPlayers;
        synchronized (rtsPlayers) {
            rtsPlayers.add(lostSide);
            rtsPlayers.add(handSide);
        }
        // seated through the start a moment ago, but its starting units are gone
        PlayerServerEvents.startedAtTick.put(lost, level.getGameTime());
        BotServerEvents.brains.put(lost, new BotPlayer(lost, Factions.VILLAGERS, BotPlayer.Difficulty.HARD, home));
        BotServerEvents.brains.put(handmade, new BotPlayer(handmade, Factions.VILLAGERS, BotPlayer.Difficulty.HARD, home2));

        Runnable cleanup = () -> {
            // out of the match first, so discarding its units doesn't run the defeat path
            synchronized (rtsPlayers) {
                rtsPlayers.remove(lostSide);
                rtsPlayers.remove(handSide);
            }
            BotServerEvents.brains.remove(lost);
            BotServerEvents.brains.remove(handmade);
            PlayerServerEvents.startedAtTick.remove(lost);
            for (LivingEntity le : unitsOf(lost))
                le.discard();
            for (LivingEntity le : unitsOf(handmade))
                le.discard();
        };
        // a think a second: 5 to reload the home chunks, 10 to re-seat (BotPlayer.LOST_START_THINKS) plus slack
        final int[] ticks = {0};
        helper.onEachTick(() -> {
            if (++ticks[0] > 400)
                return;
            List<LivingEntity> units = unitsOf(lost);
            long workers = units.stream().filter(e -> e instanceof WorkerUnit).count();
            boolean commander = units.stream().anyMatch(CommanderServerEvents::isCommander);
            if (workers >= 3) {
                String fail = null;
                if (!commander)
                    fail = "the re-seated start has no commander";
                else if (units.stream().anyMatch(e -> e.distanceToSqr(home.getX() + 0.5, e.getY(), home.getZ() + 0.5) > 6 * 6))
                    fail = "the re-seated workers are not at the bot's home";
                else if (!unitsOf(handmade).isEmpty())
                    fail = "a hand-built side (no startedAtTick) was given workers";
                ticks[0] = 1000;
                cleanup.run();
                if (fail != null)
                    helper.fail(fail);
                else
                    helper.succeed();
            } else if (ticks[0] == 400) {
                cleanup.run();
                helper.fail("a bot with no workers and no capitol still had " + workers + " workers after 20 s");
            }
        });
    }
}
