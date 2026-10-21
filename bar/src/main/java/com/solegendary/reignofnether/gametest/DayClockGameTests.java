package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.match.DayClock;
import com.solegendary.reignofnether.match.DayCycle;
import com.solegendary.reignofnether.match.TimeOfDay;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The match sun (match.DayClock): the lobby's start time is where the clock starts, the day/night cycle how fast it
 * then runs - Slow a third of vanilla, Normal vanilla, Off not at all - and only while a match is played. lovish's
 * Oct 18 ask: "make the days and nights drawn out so it doesnt feel super fast".
 * <p>
 * Each test drives DayClock.step over three minutes of game ticks inside ONE server tick and puts the overworld's clock and
 * rules back before it returns, so no other test in the run ever sees a different day time. Own batch "day_clock".
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class DayClockGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    /** Three minutes of game time (a multiple of every speed), stepped inside one tick of the test. */
    static final int TICKS = 3 * 20 * 60;

    /** The overworld's clock and the two rules the sun reads, to put back afterwards. */
    record Saved(long dayTime, boolean daylight, int cycle) {
        static Saved of(ServerLevel ow) {
            return new Saved(ow.getDayTime(), ow.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT),
                ow.getGameRules().getInt(GameRuleRegistrar.MATCH_DAY_CYCLE));
        }

        void restore(ServerLevel ow) {
            MinecraftServer server = ow.getServer();
            ow.setDayTime(dayTime);
            ow.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(daylight, server);
            ow.getGameRules().getRule(GameRuleRegistrar.MATCH_DAY_CYCLE).set(cycle, server);
        }
    }

    /** How far the clock moves over {@link #TICKS} game ticks from {@code start} with {@code cycle}. */
    static long advance(ServerLevel ow, TimeOfDay start, DayCycle cycle, boolean playing, boolean vanillaCycle) {
        DayClock.apply(ow, start, cycle);
        if (vanillaCycle)
            ow.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, ow.getServer());
        long from = ow.getDayTime();
        long g0 = ow.getGameTime();
        for (long g = g0; g < g0 + TICKS; g++)
            DayClock.step(ow, g, playing);
        return ow.getDayTime() - from;
    }

    /**
     * The client sees the server's time, not its mirror: vanilla's time packet carries the day time NEGATED when the
     * daylight cycle is off - as in every match (DayClock) - and the client's time hook (ClientPacketMixin) used to
     * normalise the raw value, so a server at 13000 showed 11000 and the lobby's Morning (5000) eased toward 19000.
     * Every start time, cycle on and off, through the real packet.
     */
    @GameTest(template = ARENA, batch = "day_clock", timeoutTicks = 20)
    public static void time_packet_day_time_reads_the_same_with_the_cycle_off(GameTestHelper helper) {
        List<String> bad = new ArrayList<>();
        List<Long> times = new ArrayList<>(List.of(13000L, 1L, 23999L));
        for (TimeOfDay t : TimeOfDay.values())
            times.add(t.dayTime);
        for (long dayTime : times)
            for (boolean cycle : new boolean[] { true, false }) {
                var packet = new net.minecraft.network.protocol.game.ClientboundSetTimePacket(1234L, dayTime, cycle);
                long got = com.solegendary.reignofnether.time.TimeUtils.dayTimeFromPacket(packet.getDayTime());
                if (got != dayTime)
                    bad.add(dayTime + (cycle ? " (cycle on)" : " (cycle off)") + " read as " + got);
            }
        if (!bad.isEmpty())
            helper.fail("the client would show the wrong time of day: " + bad);
        helper.succeed();
    }

    /**
     * The speeds: over three minutes of game ticks, Slow moves the day a third as far as Normal, Normal one tick per tick
     * (vanilla's speed) and Off not at all. Outside a played match (the map rising, starts being placed, a decided
     * match) the sun stands still, and with vanilla's own cycle switched on DayClock leaves the clock to vanilla, so
     * a world is never stepped twice.
     */
    @GameTest(template = ARENA, batch = "day_clock")
    public static void day_clock_slow_is_a_third_of_normal_and_off_stands_still(GameTestHelper helper) {
        ServerLevel ow = helper.getLevel().getServer().overworld();
        Saved saved = Saved.of(ow);
        List<String> problems = new ArrayList<>();
        try {
            long slow = advance(ow, TimeOfDay.MORNING, DayCycle.SLOW, true, false);
            long normal = advance(ow, TimeOfDay.MORNING, DayCycle.NORMAL, true, false);
            long off = advance(ow, TimeOfDay.MORNING, DayCycle.OFF, true, false);
            long notPlaying = advance(ow, TimeOfDay.MORNING, DayCycle.SLOW, false, false);
            long vanillaOn = advance(ow, TimeOfDay.MORNING, DayCycle.NORMAL, true, true);
            if (slow != TICKS / 3)
                problems.add("Slow moved the day " + slow + " ticks over " + TICKS + " game ticks, expected " + TICKS / 3);
            if (normal != TICKS)
                problems.add("Normal moved the day " + normal + " ticks over " + TICKS + " game ticks, expected " + TICKS);
            if (off != 0)
                problems.add("Off moved the day " + off + " ticks");
            if (notPlaying != 0)
                problems.add("the sun moved " + notPlaying + " ticks outside a played match");
            if (vanillaOn != 0)
                problems.add("DayClock stepped " + vanillaOn + " ticks while vanilla's daylight cycle was on (stepped twice)");
            ReignOfNether.LOGGER.info("[DayClock] over {} game ticks: slow +{}, normal +{}, off +{}", TICKS, slow, normal, off);
        } finally {
            saved.restore(ow);
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(problems.size() + " problem(s): " + String.join("; ", problems));
    }

    /**
     * Start: the clock is set to the chosen start time, vanilla's cycle is off and the chosen speed is saved as the
     * world's rule (so a reloaded match keeps its sun); the rule reads back as the same cycle. Ids and names: the
     * lobby's default is the slow cycle, and a saved "CYCLE" light from a build before this split loads as Morning.
     */
    @GameTest(template = ARENA, batch = "day_clock")
    public static void day_clock_start_sets_the_light_and_saves_the_cycle(GameTestHelper helper) {
        ServerLevel ow = helper.getLevel().getServer().overworld();
        Saved saved = Saved.of(ow);
        List<String> problems = new ArrayList<>();
        try {
            for (TimeOfDay start : TimeOfDay.values())
                for (DayCycle cycle : DayCycle.values()) {
                    ow.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, ow.getServer());
                    DayClock.apply(ow, start, cycle);
                    if (ow.getDayTime() != start.dayTime)
                        problems.add(start + "/" + cycle + ": day time " + ow.getDayTime() + ", expected " + start.dayTime);
                    if (ow.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT))
                        problems.add(start + "/" + cycle + ": vanilla's daylight cycle left on");
                    if (DayClock.cycleOf(ow) != cycle)
                        problems.add(start + "/" + cycle + ": the saved rule reads back as " + DayClock.cycleOf(ow));
                }
        } finally {
            saved.restore(ow);
        }
        if (DayCycle.byId(-1) != DayCycle.DEFAULT || DayCycle.DEFAULT != DayCycle.SLOW)
            problems.add("the default cycle is " + DayCycle.DEFAULT + " (unknown id -> " + DayCycle.byId(-1) + "), expected SLOW");
        if (TimeOfDay.byName("CYCLE") != TimeOfDay.MORNING)
            problems.add("an old saved CYCLE light loads as " + TimeOfDay.byName("CYCLE") + ", expected MORNING");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(problems.size() + " problem(s): " + String.join("; ", problems));
    }
}
