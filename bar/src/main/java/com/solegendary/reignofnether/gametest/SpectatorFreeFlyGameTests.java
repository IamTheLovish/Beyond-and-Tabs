package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Spectator free flight (lovish, Oct 21): a watcher swaps the RTS view for vanilla spectator flight and back, a player
 * holding a side may not (no scouting past the fog), and watchers are never sent to the players. Entry points only -
 * the bodies are in {@link SpectatorFreeFlyCases} (see MatchLifecycleGameTests for why a holder stays small), in a
 * batch of their own like the other match tests: they write the match roster.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class SpectatorFreeFlyGameTests {

    static final String BATCH = "spectator_free_fly";

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 40, batch = BATCH)
    public static void a_watcher_toggles_free_fly_both_ways_and_stays_a_spectator(GameTestHelper helper) {
        SpectatorFreeFlyCases.a_watcher_toggles_free_fly_both_ways_and_stays_a_spectator(helper);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 40, batch = BATCH)
    public static void a_player_holding_a_side_is_refused_free_fly_and_keeps_their_game_mode(GameTestHelper helper) {
        SpectatorFreeFlyCases.a_player_holding_a_side_is_refused_free_fly_and_keeps_their_game_mode(helper);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 40, batch = BATCH)
    public static void watchers_are_hidden_from_the_players_but_not_from_each_other(GameTestHelper helper) {
        SpectatorFreeFlyCases.watchers_are_hidden_from_the_players_but_not_from_each_other(helper);
    }
}
