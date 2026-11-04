package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;

/**
 * "A new match on a running server" (issue #35, Joisco: "an option to reset the map, or move to a new location, so a
 * server can keep hosting new games"): maps.NewMatch moves the arena to fresh ground. Entry points only - the bodies
 * and helpers are in {@link NewMatchCases} and the batch is its own, for the reasons MatchLifecycleGameTests gives (a
 * holder's declared methods decide which tests share a run, and this one resets the whole match state of the world,
 * so nothing else may run beside it).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class NewMatchGameTests {

    static final String BATCH = "new_match";

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 400, batch = BATCH)
    public static void new_match_moves_the_arena_to_fresh_ground(GameTestHelper helper) {
        NewMatchCases.new_match_moves_the_arena_to_fresh_ground(helper);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 100, batch = BATCH)
    public static void new_match_slots_survive_a_save_and_a_restart(GameTestHelper helper) throws IOException {
        NewMatchCases.new_match_slots_survive_a_save_and_a_restart(helper);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 100, batch = BATCH)
    public static void new_match_command_is_registered_for_ops_only(GameTestHelper helper) {
        NewMatchCases.new_match_command_is_registered_for_ops_only(helper);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 100, batch = BATCH)
    public static void new_match_retires_units_at_once_and_buildings_in_their_own_level(GameTestHelper helper) {
        NewMatchCases.new_match_retires_units_at_once_and_buildings_in_their_own_level(helper);
    }
}
