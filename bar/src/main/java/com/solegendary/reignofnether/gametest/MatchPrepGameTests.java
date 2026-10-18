package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;

/**
 * The battlefield preparation GameTests (inbox/spec-3, section 5.1): everything heavy runs before the start phase
 * (match.MatchPrep), in order, resumable from every saved stage, cancelled cleanly when a stage throws, and the
 * prepared wall lays the same blocks when raised again. The bodies and helpers are in {@link MatchPrepCases}.
 * <p>
 * Its own batch for the reason {@link MatchLifecycleGameTests} gives: Forge collects every declared method of a
 * holder into one HashSet and cuts batches into runs of 100 in that set's order, so a holder with helpers and
 * lambdas in the default batch reshuffles which tests share a run (the duel simulator went from 100 s to 650-870 s
 * that way). This holder declares only its entry points.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class MatchPrepGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "match_prep";

    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void the_battlefield_is_ready_before_the_start_phase_begins(GameTestHelper helper) {
        MatchPrepCases.the_battlefield_is_ready_before_the_start_phase_begins(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void random_terrain_is_prepared_too_without_a_forge(GameTestHelper helper) {
        MatchPrepCases.random_terrain_is_prepared_too_without_a_forge(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 200, batch = BATCH)
    public static void a_preparation_resumes_from_every_saved_stage_and_repeats_nothing_before_it(GameTestHelper helper)
            throws IOException {
        MatchPrepCases.a_preparation_resumes_from_every_saved_stage_and_repeats_nothing_before_it(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void a_preparation_stage_that_throws_cancels_the_match_to_the_lobby(GameTestHelper helper) {
        MatchPrepCases.a_preparation_stage_that_throws_cancels_the_match_to_the_lobby(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void a_prepared_wall_raised_twice_lays_the_same_blocks(GameTestHelper helper) {
        MatchPrepCases.a_prepared_wall_raised_twice_lays_the_same_blocks(helper);
    }
}
