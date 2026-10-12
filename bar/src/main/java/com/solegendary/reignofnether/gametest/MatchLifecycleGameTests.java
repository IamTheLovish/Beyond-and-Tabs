package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;

/**
 * The match lifecycle GameTests (design/ron_rewrite_plan.md, rewrite 5): behaviour pins written before the rewrite,
 * then the new match package - see {@link MatchLifecycleCases}, which holds every body and helper.
 * <p>
 * Why the split and the own batch: Forge registers GameTests by collecting <i>every declared method</i> of every
 * holder (helpers and lambda bodies included) into one HashSet, and GameTestRunner cuts each batch into runs of 100
 * tests in that set's order - so adding tests (or enough helpers to resize the set) changes which tests share a run.
 * With this holder in the default batch the duel simulator's wall time went from about 100 s (main, and the
 * pins-only commit) to 650-870 s on every commit after - its counter-matrix twin, alone in its own batch, never
 * moved - and the suite reached CI's 32-minute watchdog. So this holder declares only its entry points (a handful
 * of methods, no lambdas) and runs them as a batch of their own: the default batch keeps main's runs.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class MatchLifecycleGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "match_lifecycle";

    // ------------------------------------------------------------------------------- pinned behaviour (before 5)

    @GameTest(template = ARENA, timeoutTicks = 200, batch = BATCH)
    public static void commander_death_ends_a_1v1_and_the_base_becomes_wrecks(GameTestHelper helper) {
        MatchLifecycleCases.commander_death_ends_a_1v1_and_the_base_becomes_wrecks(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void commander_death_in_a_team_game_fights_on(GameTestHelper helper) {
        MatchLifecycleCases.commander_death_in_a_team_game_fights_on(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void victory_goes_to_the_last_team_standing(GameTestHelper helper) {
        MatchLifecycleCases.victory_goes_to_the_last_team_standing(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void resign_alone_surrenders_and_a_passed_team_vote_beats_every_human(GameTestHelper helper) {
        MatchLifecycleCases.resign_alone_surrenders_and_a_passed_team_vote_beats_every_human(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void a_winner_spectates_and_keeps_their_base_but_a_fighter_cannot(GameTestHelper helper) {
        MatchLifecycleCases.a_winner_spectates_and_keeps_their_base_but_a_fighter_cannot(helper);
    }

    // ------------------------------------------------------------------------------- the match package (rewrite 5)

    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void match_state_walks_every_phase_and_each_survives_a_reload(GameTestHelper helper) throws IOException {
        MatchLifecycleCases.match_state_walks_every_phase_and_each_survives_a_reload(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void resumed_start_phase_holds_for_its_humans_and_keeps_a_floor(GameTestHelper helper) {
        MatchLifecycleCases.resumed_start_phase_holds_for_its_humans_and_keeps_a_floor(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void a_setup_that_cannot_resume_is_cancelled_cleanly(GameTestHelper helper) throws IOException {
        MatchLifecycleCases.a_setup_that_cannot_resume_is_cancelled_cleanly(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void match_phases_move_only_along_the_table(GameTestHelper helper) {
        MatchLifecycleCases.match_phases_move_only_along_the_table(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void alliances_between_the_sides_survive_a_reload(GameTestHelper helper) throws IOException {
        MatchLifecycleCases.alliances_between_the_sides_survive_a_reload(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void the_roster_survives_a_defeat_inside_a_walk_over_it(GameTestHelper helper) {
        MatchLifecycleCases.the_roster_survives_a_defeat_inside_a_walk_over_it(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void a_beaten_base_is_cleared_at_once_before_the_world_saves(GameTestHelper helper) {
        MatchLifecycleCases.a_beaten_base_is_cleared_at_once_before_the_world_saves(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void spectators_get_every_sides_population_and_enemies_do_not(GameTestHelper helper) {
        MatchLifecycleCases.spectators_get_every_sides_population_and_enemies_do_not(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void defeat_reason_keys_read_as_sentence_fragments(GameTestHelper helper) {
        MatchLifecycleCases.defeat_reason_keys_read_as_sentence_fragments(helper);
    }
}
