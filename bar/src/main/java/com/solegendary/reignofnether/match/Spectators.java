package com.solegendary.reignofnether.match;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.hero.HeroRoles;
import com.solegendary.reignofnether.player.PlayerClientboundPacket;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.taskscheduler.TaskSchedulerServerEvents;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import javax.annotation.Nullable;

/**
 * Leaving play to watch (moved out of RoN's PlayerServerEvents). A beaten side is out of play the moment it falls
 * and watches anyway; this is for a side whose match is over but who is still in play - a winner - and wants the
 * observer's view of what is left of it: fog off, every side on the player panel, everything they own left standing
 * and theirs. Refused for a side still in the fight: spectating is not a way to look through the fog mid-match
 * (BAR's spectators never had a side; its players can't switch to one while their team lives).
 * <p>
 * Free-fly is the same rule for the camera: a watcher may swap the RTS view for vanilla spectator flight (noclip, no
 * interaction) and back, but a player holding a side may not - flying past the fog is exactly the scouting the fog
 * exists to stop. The key is the client's; this class is the authority.
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

    /**
     * Whether {@code name} is a watcher of a running match: no side in play (never seated, beaten, or spectating) and no
     * Hero role - the hero plays under their team's fog. Sandbox editors hold a side, so they are not watchers.
     */
    public static boolean mayFreeFly(String name) {
        return MatchRoster.any() && !MatchRoster.isInPlay(name) && !HeroRoles.isHeroRole(name);
    }

    /**
     * Whether {@code body}, a watcher's camera body, must not be sent to {@code viewer}: everyone who plays - a side in
     * the match or a Hero - never sees a watcher (BAR's spectators are not in the game), while watchers still see each
     * other. Vanilla only hides spectators from non-spectators, and every side's RTS camera body is a vanilla spectator
     * too, so vanilla alone would show watchers to the players; ChunkMap's tracker asks this
     * (TrackedEntityMixin).
     */
    public static boolean isHiddenFrom(ServerPlayer body, ServerPlayer viewer) {
        return body != viewer && body.isSpectator()
            && mayFreeFly(body.getName().getString()) && !mayFreeFly(viewer.getName().getString());
    }

    /**
     * The spectator free-fly key: {@code on} puts a watcher in vanilla spectator mode to fly around, off lets them
     * back into the RTS view (which re-opens the top-down GUI and puts them in spectator mode itself). Either way
     * the answer for a player holding a side is no, and nothing changes for them. Leaving the RTS view restores the
     * game mode saved when it opened (survival for someone who joined mid-match), so {@code on} sets spectator
     * AFTER that restore, which the client sends first. Returns whether the request was granted. Game thread only.
     */
    public static boolean setFreeFly(@Nullable ServerPlayer sp, boolean on) {
        if (sp == null)
            return false;
        String name = sp.getName().getString();
        if (!mayFreeFly(name)) {
            ReignOfNether.LOGGER.warn("[Match] {} asked for free-fly but is not a watcher of a running match", name);
            sp.sendSystemMessage(Component.translatable("server.reignofnether.free_fly_refused"));
            return false;
        }
        if (on)
            sp.setGameMode(GameType.SPECTATOR);
        return true;
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
