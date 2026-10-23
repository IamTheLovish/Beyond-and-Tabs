package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Fog of war is on in a new match by default (tester, Oct 21), and the host can still start without it. Pure data:
 * the gamerule's default and the decision a seated match makes, so no world state is touched.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class FogDefaultGameTests {

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void fog_is_on_for_a_new_match_unless_the_host_turned_the_default_off(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (!new GameRules().getBoolean(GameRuleRegistrar.FOG_BY_DEFAULT))
            problems.add("reignofnetherFogByDefault does not default to true");
        if (new GameRules().getBoolean(GameRuleRegistrar.FORCE_FOG))
            problems.add("reignofnetherForceFog must stay off by default (it refuses disabling fog)");
        if (!FogOfWarServerEvents.shouldEnableForNewMatch(false, true, true))
            problems.add("a new match on an RTS map with the default on does not turn fog on");
        if (FogOfWarServerEvents.shouldEnableForNewMatch(false, false, true))
            problems.add("the host turned the default off but fog still starts on");
        if (FogOfWarServerEvents.shouldEnableForNewMatch(false, true, false))
            problems.add("fog would be forced on a map it cannot cover (no small world border)");
        if (FogOfWarServerEvents.shouldEnableForNewMatch(true, true, true))
            problems.add("fog already on would be switched on again (a second chat line)");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * Real lobby / skirmish worlds keep the vanilla ~60M border (the arena wall is blocks), which fog refuses
     * (FogOfWarServerEvents.setEnabled): a match now fits a border round its arena before seating. Sizes, and the
     * effect on the level - set and restored inside one server tick, so no other test sees it.
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void a_match_gets_a_border_that_holds_its_arena_so_fog_can_run(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (FogOfWarServerEvents.borderSizeFor(230, 200) != 620)
            problems.add("arena 230, reach 200: expected 620 got " + FogOfWarServerEvents.borderSizeFor(230, 200));
        if (FogOfWarServerEvents.borderSizeFor(560, 300) != 1280)
            problems.add("the huge arena (560) must fit exactly the fog limit, got " + FogOfWarServerEvents.borderSizeFor(560, 300));
        if (FogOfWarServerEvents.borderSizeFor(-1, 260) != 1200)
            problems.add("a random arena (<= 520): expected 1200 got " + FogOfWarServerEvents.borderSizeFor(-1, 260));
        if (FogOfWarServerEvents.borderSizeFor(420, 600) != -1)
            problems.add("a start ring beyond the fog limit must get no (cutting) border");
        var level = helper.getLevel();
        var border = level.getWorldBorder();
        double size = border.getSize(), cx = border.getCenterX(), cz = border.getCenterZ();
        try {
            if (size <= com.solegendary.reignofnether.worldborder.WorldBorderServerEvents.RTS_OPTIMIZED_BORDER)
                problems.add("the test level already has a small border; cannot show the change");
            else if (!FogOfWarServerEvents.fitBorderForMatch(level, 100.5, -40.5, 230, 200, true))
                problems.add("a vanilla-border level got no match border");
            else if (!com.solegendary.reignofnether.worldborder.WorldBorderServerEvents.isRtsOptimisedMap(level)
                    || (int) border.getSize() != 620 || (int) border.getCenterX() != 100)
                problems.add("border after fitting: size " + border.getSize() + " centre " + border.getCenterX());
        } finally {
            border.setCenter(cx, cz);
            border.setSize(size);
        }
        if (border.getSize() != size)
            problems.add("the test level's border was not restored");
        if (FogOfWarServerEvents.fitBorderForMatch(level, 0, 0, 230, 200, false))
            problems.add("the GameTest server's world border must not be touched by a match start");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * The Quick Battle / skirmish instant start (SkirmishServerboundPacket.handle): the border is fitted round the arena
     * BEFORE the first side joins, and that first side (MatchState.sideJoined, called by MatchRoster.join) turns fog on.
     * Walks those two calls on a throwaway MatchState; the level's border and fog flag are restored inside the same
     * server tick, so no other test sees either. (The packet itself needs a host player and a network context.)
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void fog_on_after_quick_battle_start(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        var level = helper.getLevel();
        var border = level.getWorldBorder();
        double size = border.getSize(), cx = border.getCenterX(), cz = border.getCenterZ();
        boolean wasOn = FogOfWarServerEvents.isEnabled();
        int rts = com.solegendary.reignofnether.worldborder.WorldBorderServerEvents.RTS_OPTIMIZED_BORDER;
        try {
            if (size <= rts || wasOn) {
                problems.add("the test level already has a small border or fog on; cannot show the start");
            } else {
                // the packet's order: border first (medium arena 320, far ring 420 + one extra bot), then the first side
                if (!FogOfWarServerEvents.fitBorderForMatch(level, 10.5, 10.5, 320, 420 + 35, true))
                    problems.add("no border was fitted for the instant start");
                new com.solegendary.reignofnether.match.MatchState(com.solegendary.reignofnether.maps.MapMatch.EFFECTS)
                    .sideJoined("gametest_fog_quick");
                if (!FogOfWarServerEvents.isEnabled())
                    problems.add("fog is not on after the first side joined an instant start");
                if (border.getSize() > rts)
                    problems.add("border after the start is " + border.getSize() + ", above the RTS size " + rts);
            }
        } finally {
            if (FogOfWarServerEvents.isEnabled() && !wasOn)
                FogOfWarServerEvents.setEnabled(false);
            border.setCenter(cx, cz);
            border.setSize(size);
        }
        if (FogOfWarServerEvents.isEnabled() != wasOn)
            problems.add("fog flag was not restored");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
