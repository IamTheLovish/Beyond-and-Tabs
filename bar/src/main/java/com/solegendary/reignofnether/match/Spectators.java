package com.solegendary.reignofnether.match;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.player.PlayerClientboundPacket;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.taskscheduler.TaskSchedulerServerEvents;

import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

/**
 * Leaving play to watch (moved out of RoN's PlayerServerEvents). A beaten side is out of play the moment it falls
 * and watches anyway; this is for a side whose match is over but who is still in play - a winner - and wants the
 * observer's view of what is left of it: fog off, every side on the player panel, everything they own left standing
 * and theirs. Refused for a side still in the fight: spectating is not a way to look through the fog mid-match
 * (BAR's spectators never had a side; its players can't switch to one while their team lives).
 * Game thread only.
 */
public final class Spectators {
    private Spectators() { }

    /** Ticks before a new spectator's chunks are re-sent without fog. */
    static final int FOG_RESEND_TICKS = 10;

    /** Whether {@code name} may spectate now: in play, and their match is over for them. */
    public static boolean maySpectate(String name) {
        return MatchRoster.isFinished(name) && MatchRoster.isInPlay(name);
    }

    /** The results screen's / match menu's Spectate from the player with this entity id. */
    public static void spectate(int playerId) {
        spectatePlayer(online(playerId));
    }

    /** True when {@code sp} left play to watch. (Not an overload of spectate: see MatchOutcome's defeat note.) */
    public static boolean spectatePlayer(@Nullable ServerPlayer sp) {
        if (sp == null)
            return false;
        String name = sp.getName().getString();
        if (!maySpectate(name))
            return false;
        ReignOfNether.LOGGER.info("[Match] {} spectates the rest of the match", name);
        MatchRoster.leave(name);
        PlayerServerEvents.saveRTSPlayers();
        PlayerClientboundPacket.removeRTSPlayer(name);
        TaskSchedulerServerEvents.schedule(FOG_RESEND_TICKS, () -> {
            if (!sp.hasDisconnected())
                FogOfWarServerEvents.resendAllTrackedChunks(sp);
        });
        return true;
    }

    @Nullable
    static ServerPlayer online(int playerId) {
        for (ServerPlayer sp : PlayerServerEvents.players)
            if (sp.getId() == playerId)
                return sp;
        return null;
    }
}
