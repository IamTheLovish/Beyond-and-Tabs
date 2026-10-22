package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.match.Spectators;
import com.solegendary.reignofnether.player.PlayerServerEvents;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.List;

/**
 * The bodies of {@link SpectatorFreeFlyGameTests}. Fake online players stand for the watchers and the players; a side
 * is a roster entry (MatchLifecycleCases.side), and the roster is only read by Spectators, so nothing else wakes up.
 * Everything is taken out again in {@code finally}.
 */
final class SpectatorFreeFlyCases {
    private SpectatorFreeFlyCases() { }

    /** The free-fly key's two requests are granted to a watcher, and either way the game mode ends up spectator. */
    static void a_watcher_toggles_free_fly_both_ways_and_stays_a_spectator(GameTestHelper helper) {
        String watcher = "gametest_ff_watch_a", side = "gametest_ff_side_a";
        ServerPlayer sp = MatchLifecycleCases.fake(helper.getLevel(), watcher);
        MatchLifecycleCases.side(side, 1_100_071);   // a running match with a side that is not the watcher
        try {
            List<String> problems = new ArrayList<>();
            if (!Spectators.mayFreeFly(watcher))
                problems.add("a player with no side in a running match is not a watcher");
            // leaving the RTS view restores the mode saved when it opened (survival for a late joiner): the request,
            // sent after it, must leave a spectator
            sp.setGameMode(GameType.SURVIVAL);
            if (!Spectators.setFreeFly(sp, true))
                problems.add("free flight on was refused for a watcher");
            else if (sp.gameMode.getGameModeForPlayer() != GameType.SPECTATOR)
                problems.add("free flight on left the watcher in " + sp.gameMode.getGameModeForPlayer());
            if (!Spectators.setFreeFly(sp, false))
                problems.add("back to the RTS view was refused for a watcher");
            else if (sp.gameMode.getGameModeForPlayer() != GameType.SPECTATOR)
                problems.add("back to the RTS view left the watcher in " + sp.gameMode.getGameModeForPlayer());
            if (!Spectators.setFreeFly(sp, true) || sp.gameMode.getGameModeForPlayer() != GameType.SPECTATOR)
                problems.add("the second trip into free flight did not work");
            finish(helper, problems);
        } finally {
            MatchLifecycleCases.forget(watcher, side);
            PlayerServerEvents.players.remove(sp);
        }
    }

    /** No scouting: a side's player is told no, and neither their game mode nor anything else changes. */
    static void a_player_holding_a_side_is_refused_free_fly_and_keeps_their_game_mode(GameTestHelper helper) {
        String player = "gametest_ff_slot_b", other = "gametest_ff_side_b";
        ServerPlayer sp = MatchLifecycleCases.fake(helper.getLevel(), player);
        MatchLifecycleCases.side(player, sp.getId());
        MatchLifecycleCases.side(other, 1_100_072);
        try {
            List<String> problems = new ArrayList<>();
            sp.setGameMode(GameType.SURVIVAL);
            if (Spectators.mayFreeFly(player))
                problems.add("a player holding a side counts as a watcher");
            if (Spectators.setFreeFly(sp, true))
                problems.add("free flight on was granted to a player holding a side");
            if (Spectators.setFreeFly(sp, false))
                problems.add("back to the RTS view was granted to a player holding a side");
            if (sp.gameMode.getGameModeForPlayer() != GameType.SURVIVAL)
                problems.add("a refused request changed the game mode to " + sp.gameMode.getGameModeForPlayer());
            if (Spectators.setFreeFly(null, true))
                problems.add("a request from nobody was granted");
            finish(helper, problems);
        } finally {
            MatchLifecycleCases.forget(player, other);
            PlayerServerEvents.players.remove(sp);
        }
    }

    /**
     * The tracker's rule (TrackedEntityMixin asks Spectators.isHiddenFrom before it sends a player entity): a watcher
     * is hidden from a side's player and from a Hero, never from another watcher, and a side's own camera body is
     * not a watcher.
     */
    static void watchers_are_hidden_from_the_players_but_not_from_each_other(GameTestHelper helper) {
        String w1 = "gametest_ff_watch_c1", w2 = "gametest_ff_watch_c2", p = "gametest_ff_slot_c", other = "gametest_ff_side_c";
        ServerPlayer watcher1 = MatchLifecycleCases.fake(helper.getLevel(), w1);
        ServerPlayer watcher2 = MatchLifecycleCases.fake(helper.getLevel(), w2);
        ServerPlayer playing = MatchLifecycleCases.fake(helper.getLevel(), p);
        MatchLifecycleCases.side(p, playing.getId());
        MatchLifecycleCases.side(other, 1_100_073);
        try {
            List<String> problems = new ArrayList<>();
            // a side's RTS camera body is a spectator too (the top-down GUI sets it), as is a watcher's
            watcher1.setGameMode(GameType.SPECTATOR);
            watcher2.setGameMode(GameType.SPECTATOR);
            playing.setGameMode(GameType.SPECTATOR);
            if (!Spectators.isHiddenFrom(watcher1, playing))
                problems.add("a watcher is sent to a player who is in the match");
            if (Spectators.isHiddenFrom(watcher1, watcher2) || Spectators.isHiddenFrom(watcher2, watcher1))
                problems.add("watchers cannot see each other");
            if (Spectators.isHiddenFrom(playing, watcher1))
                problems.add("a side's camera body is hidden from a watcher");
            if (Spectators.isHiddenFrom(watcher1, watcher1))
                problems.add("a watcher is hidden from themselves");
            finish(helper, problems);
        } finally {
            MatchLifecycleCases.forget(w1, w2, p, other);
            PlayerServerEvents.players.remove(watcher1);
            PlayerServerEvents.players.remove(watcher2);
            PlayerServerEvents.players.remove(playing);
        }
    }

    private static void finish(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
