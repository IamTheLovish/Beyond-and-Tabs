package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.perf.ServerTickProfile;
import com.solegendary.reignofnether.perf.Sys;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The measurement itself (Oct 21 perf plan, PR 1): the automatic lag capture and the per-system timing must produce
 * something. The capture used to run the time profiler behind /debug start, which only times the tick rate, so its
 * profile was empty - "[PerfAuto] ... heaviest:  0.0%" in lovish's log - while the test that stood guard only checked
 * that a summary string existed. This one fails on an empty profile, and on a profile that does not name our own
 * systems ({@code bat:} sections, perf/Sys).
 *
 * In its own class: check_guards.py stops new tests going into SkirmishGameTests.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class PerfProfileGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final int PER_SIDE = 12;

    private static VindicatorUnit spawn(GameTestHelper helper, String owner, double x, double z, List<Entity> made) {
        VindicatorUnit v = EntityRegistrar.VINDICATOR_UNIT.get().create(helper.getLevel());
        if (v == null)
            return null;
        BlockPos at = helper.absolutePos(new BlockPos(0, 2, 0));
        v.moveTo(at.getX() + x, at.getY(), at.getZ() + z, 0, 0);
        v.setOwnerName(owner);
        helper.getLevel().addFreshEntity(v);
        made.add(v);
        return v;
    }

    private static void notYet(String why) {
        throw new GameTestAssertException(why);
    }

    /**
     * Two dozen Vindicators fight while the capture runs 40 ticks. The profile must come back with sections, and the
     * ones the units' ticks add ({@code bat:attacker}, {@code bat:unit}) must be among them; the [Sys] line of those
     * ticks must name the attacker system. The line is logged as {@code [SysSample]} (a CI notice), the first sample
     * of what lovish's playtest logs will say.
     */
    @GameTest(template = ARENA, timeoutTicks = 500)
    public static void lag_capture_profiles_the_tick_and_names_the_heaviest_sections(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_perfprof_" + helper.absolutePos(BlockPos.ZERO).asLong();
        List<Entity> made = new ArrayList<>();
        for (int i = 0; i < PER_SIDE; i++) {
            if (spawn(helper, owner, 1.5 + i * 1.1, 3.5, made) == null || spawn(helper, owner + "_foe", 1.5 + i * 1.1, 12.5, made) == null) {
                cleanUp(made);
                helper.fail("could not create the units");
                return;
            }
        }
        boolean[] started = { false };
        String[] sys = { null };
        helper.succeedWhen(() -> {
            if (!started[0]) {
                ServerTickProfile.lastCaptureSummary = null;
                Sys.force(true);
                // another capture on this shared server (the 8v8 stress test can trip the automatic one): wait for it
                if (!ServerTickProfile.startCapture(level.getServer(), 40, "gametest"))
                    notYet("another capture is running on this shared server");
                started[0] = true;
            }
            String summary = ServerTickProfile.lastCaptureSummary;
            if (summary == null)
                notYet("waiting for the recorder's profile");
            if (sys[0] == null) {
                sys[0] = Sys.snapshot();
                Sys.force(false);
            }
            if (ServerTickProfile.lastCaptureEmpty || ServerTickProfile.lastCaptureSections <= 0)
                throw new GameTestAssertException("the lag capture came back with an EMPTY profile: " + summary);
            if (!ServerTickProfile.lastCaptureBat.contains("bat:attacker") || !ServerTickProfile.lastCaptureBat.contains("bat:unit"))
                throw new GameTestAssertException("the profile does not name our unit systems (bat:unit, bat:attacker); found "
                    + ServerTickProfile.lastCaptureBat + " in " + summary);
            if (sys[0] == null || !sys[0].contains("attacker"))
                throw new GameTestAssertException("the [Sys] line of the capture does not name the attacker system: " + sys[0]);
            ReignOfNether.LOGGER.info("[SysSample] {} units fighting: {}", made.size(), sys[0]);
            ReignOfNether.LOGGER.info("[SysSample] and the capture of the same ticks: {}", summary);
            cleanUp(made);
        });
    }

    /** Per-system timing is off while the server is healthy and nothing records: a section call costs nothing and keeps no state. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void sections_are_inert_when_nothing_is_measuring(GameTestHelper helper) {
        if (Sys.isMeasuring()) {
            helper.succeed();   // another test on this shared server forces measuring right now: nothing to check
            return;
        }
        long before = Sys.calls(Sys.UNIT_GRID);
        int token = Sys.begin(Sys.UNIT_GRID);
        Sys.end(token);
        if (token != 0)
            helper.fail("a section opened while nothing measures (token " + token + ")");
        else if (Sys.calls(Sys.UNIT_GRID) != before)
            helper.fail("a section was counted while nothing measures");
        else
            helper.succeed();
    }

    private static void cleanUp(List<Entity> made) {
        for (Entity e : made) {
            UnitServerEvents.clearQueuedActions(e.getId());
            e.discard();
        }
    }
}
