package com.solegendary.reignofnether.match;

import com.solegendary.reignofnether.maps.MapMatch;
import com.solegendary.reignofnether.maps.StartPhase;
import com.solegendary.reignofnether.matchstart.MultiplayerLobby;
import com.solegendary.reignofnether.player.MatchHistory;
import com.solegendary.reignofnether.player.PlayerClientboundPacket;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.resources.MetalPatches;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The lifecycle's Forge hooks (registered in ServerEventRegistrar, which the integrated server runs too): the match
 * clock, the battlefield preparation's tick (MatchPrep), what a player logging in to a match being prepared needs,
 * and the save / restore around server start and stop (MatchLifecycleSaveData).
 */
public class MatchServerEvents {

    /** Every 10 s everyone's match clock is corrected (RoN's cadence). */
    static final int CLOCK_SYNC_TICKS = 200;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        // the battlefield being prepared (MAP_RISING): its stages run on the server tick, nothing is in play yet
        MatchPrep.tickLive();
        // the match clock runs while anyone is in play (RoN's rtsGameTicks, now saved with the match)
        long clock = MatchState.live().advanceClock(MatchRoster.any());
        PlayerServerEvents.rtsGameTicks = clock;
        if (clock <= 0)
            return;
        // results-screen graphs: one sample at the start and every 30 s after (bounded per side, MatchHistory)
        if ((clock - 1) % MatchHistory.SAMPLE_TICKS == 0)
            MatchHistory.sampleAll(MatchRoster.LIVE);
        if (clock % CLOCK_SYNC_TICKS == 0)
            PlayerClientboundPacket.syncRtsGameTime(clock);
    }

    /**
     * A player arriving in a match being prepared (a rejoin after a reload or a dropped connection) is put back where
     * the setup wants them: the RTS view, and in start position mode the camera on their own pick, BAR-style. They
     * hear what a resume did, and anyone gets the match clock straight away rather than within 10 s. A side of a
     * decided match gets its results again (client memory, gone with the reload or the dropped connection).
     */
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        if (!(evt.getEntity() instanceof ServerPlayer sp))
            return;
        MatchState state = MatchState.live();
        String name = sp.getName().getString();
        if (state.resumeNotice() != null)
            sp.sendSystemMessage(Component.literal(state.resumeNotice()));
        MatchSetup setup = state.setup();
        if (state.phase() == MatchPhase.MAP_RISING && setup != null && inLineup(setup, sp)) {
            PlayerClientboundPacket.setRTSCamera(name, true);
        } else if (state.phase() == MatchPhase.START_POSITIONS && state.startPhase() != null) {
            StartPhase.Pick pick = state.startPhase().pickOf(name);
            if (pick != null && !pick.bot) {
                PlayerClientboundPacket.setRTSCamera(name, true);
                PlayerClientboundPacket.teleport(name,
                    new BlockPos(pick.x, MetalPatches.solidTop(sp.serverLevel(), pick.x, pick.z), pick.z));
            }
        } else if (state.owesResults(name)) {
            MatchOutcome.sendMatchStats(sp, state.winners(), state.endClock());
        }
        if (MatchRoster.any())
            PlayerClientboundPacket.syncRtsGameTime(state.clock());
    }

    static boolean inLineup(MatchSetup setup, ServerPlayer sp) {
        for (MultiplayerLobby.Seat s : setup.lineup)
            if (!s.bot && (s.uuid != null ? s.uuid.equals(sp.getUUID()) : s.name.equals(sp.getName().getString())))
                return true;
        return false;
    }

    // LOW: after PlayerServerEvents (NORMAL) has put the saved sides back in play, so a world saved before the
    // lifecycle existed can tell it has a match running
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onServerStarted(ServerStartedEvent evt) {
        MatchLifecycleSaveData.onServerStarted(evt.getServer());
        BattlefieldJournal.onServerStarted(evt.getServer());   // a journal saved mid-match records again
    }

    // HIGHEST: before MapBuilder, StartPhase, MapMatch and the lobby clear their statics in their own handlers
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerStoppingSnapshot(ServerStoppingEvent evt) {
        MatchLifecycleSaveData.onServerStopping(evt.getServer());
    }

    // LOWEST: the snapshot is taken; the next world opened in this game (integrated server) starts with no match
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStoppingClear(ServerStoppingEvent evt) {
        MatchPrep.dropLive();
        BattlefieldJournal.onServerStopping();
        MatchState.replaceLive(new MatchState(MapMatch.EFFECTS));
    }
}
