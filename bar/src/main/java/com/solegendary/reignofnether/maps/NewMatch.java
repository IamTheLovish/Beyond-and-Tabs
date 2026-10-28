package com.solegendary.reignofnether.maps;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.match.MatchPhase;
import com.solegendary.reignofnether.match.MatchState;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.resources.MetalPatches;
import com.solegendary.reignofnether.startpos.ArenaPregen;
import com.solegendary.reignofnether.startpos.BattlefieldSetup;
import com.solegendary.reignofnether.worldborder.ArenaPathing;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A new match on a running server (issue #35, Joisco: "an option to reset the map, or move to a new location, so a
 * server can keep hosting new games"). The arena is not regenerated and the world is not reloaded: the match
 * moves to <b>fresh, never-used ground</b> one slot further out ({@link ArenaSlots}, saved with the world in
 * {@link ArenaOrigins}), and everything that is anchored to the arena moves with it.
 * <p>
 * <b>What a new match does</b>, in one server tick except the removal marked below:
 * <ol>
 *   <li>refuses while a battlefield is being prepared, and while a match is in play unless forced ({@link #refusal});</li>
 *   <li>resets the match like /rts-hard-reset (sides, lobby, possession, fog snapshot, patches, research, alliances)
 *       but unregisters the old units and buildings at once, removes the units at once and takes the buildings down
 *       over the next ticks ({@link ArenaRetirement}) - the shift never stalls the tick;</li>
 *   <li>takes the next slot and finds its ground (one chunk);</li>
 *   <li>moves everyone to it (the lobby's return point), then the world border's centre (its size is the host's and
 *       stays; an unbounded border is left alone) and the world spawn, so people landing or joining arrive in the
 *       new lobby and units can walk the new arena;</li>
 *   <li>forgets the fog of war's view of the old arena (the snapshot went with the reset; the new one is captured
 *       by the first side that starts, as for any match) and the battlefield formation that was meant for it;</li>
 *   <li>starts generating the lobby's surroundings in the background.</li>
 * </ol>
 * The next match then starts exactly as before, but its centre is the slot ({@link ArenaOrigins#centreFor}, used by
 * the multiplayer lobby and the skirmish start), so the map layout, the start slots, the wall, the bot seats and the
 * capitol sites all come out round the new origin. Nothing about the map or the pre-match flow knows about slots.
 * <p>
 * <b>For Lane C's lobby / end-of-match button</b>: on the server, after the host check, call
 * {@code NewMatch.start(server, null, false)} and show the {@link Result}'s message to the host. Ops can use
 * {@code /rts-newmatch} (and {@code /rts-newmatch force} to throw a match in play away).
 */
public final class NewMatch {
    private NewMatch() { }

    /** A world border wider than this is the vanilla one (about 60 million): not an arena's border, never moved. */
    static final double UNBOUNDED_BORDER = 1.0E6;
    /** Blocks round the new lobby generated in the background, so the arriving camera does not see holes. */
    static final int LOBBY_PREWARM_RADIUS = 96;

    /** What a new-match request came to: {@code origin} and {@code slot} are set when {@code ok}. */
    public record Result(boolean ok, String message, int slot, @Nullable BlockPos origin) { }

    /** Null when a new match may start in {@code phase}, else why not. */
    @Nullable
    public static String refusal(MatchPhase phase, boolean force) {
        if (phase.preparing())
            return "A battlefield is being prepared - wait for the match to start (or cancel it with /rts-reset).";
        if (phase == MatchPhase.PLAYING && !force)
            return "A match is in progress. Wait for it to end, or throw it away with: /rts-newmatch force";
        return null;
    }

    /**
     * Moves the world to the next fresh arena. Server thread only. {@code hint}: where the arena just played stood
     * (used only the first time, to anchor the slot grid; null finds it from the metal patches, the map in play or the
     * world spawn). {@code force}: also allowed while a match is in play.
     */
    public static Result start(MinecraftServer server, @Nullable BlockPos hint, boolean force) {
        ServerLevel level = server.overworld();
        MatchState state = MatchState.live();
        String why = refusal(state.phase(), force);
        if (why != null)
            return new Result(false, why, -1, null);

        ArenaOrigins origins = ArenaOrigins.get(server);
        BlockPos anchor = hint != null ? hint : currentArena(level, state);

        // 1. the old match is over for everyone (the same reset /rts-hard-reset does; its units and buildings come
        //    down over the next ticks) and nothing prepared for it may form on the new ground
        PlayerServerEvents.resetRTS(true, true);
        BattlefieldSetup.abandon();
        ArenaPathing.abandon();

        // 2. the next slot, saved at once so a crash or restart never hands it out again
        int[] at = origins.cursor().shift(anchor.getX(), anchor.getZ());
        origins.setDirty();
        level.getDataStorage().save();
        BlockPos origin = ArenaOrigins.groundAt(level, at[0], at[1]);

        // 3. everything anchored to the arena follows: people first (a player left outside a moved border takes
        //    damage), then the border, then the spawn point and the fog
        for (ServerPlayer sp : server.getPlayerList().getPlayers())
            sp.teleportTo(level, origin.getX() + 0.5, origin.getY() + 1.0, origin.getZ() + 0.5, sp.getYRot(), sp.getXRot());
        WorldBorder border = level.getWorldBorder();
        if (border.getSize() < UNBOUNDED_BORDER)
            border.setCenter(origin.getX() + 0.5, origin.getZ() + 0.5);
        level.setDefaultSpawnPos(origin, 0f);
        FogOfWarServerEvents.forgetArena();

        // 4. the lobby's surroundings, in the background (no-op on a server with nobody on it)
        ArenaPregen.start(level, origin.getX(), origin.getZ(), LOBBY_PREWARM_RADIUS, 4, false);

        int slot = origins.cursor().slot();
        String msg = "A new match arena is ready (slot " + slot + ", x " + origin.getX() + " z " + origin.getZ()
            + "). Open the lobby to start the next match.";
        ReignOfNether.LOGGER.info("[NewMatch] {} (was centred at x {} z {})", msg, anchor.getX(), anchor.getZ());
        MapMatch.say(server, msg);
        return new Result(true, msg, slot, origin);
    }

    /** Where the arena in play stands, for the first shift of a world: its metal patches, its map, else the spawn. */
    private static BlockPos currentArena(ServerLevel level, MatchState state) {
        List<BlockPos> patches = MetalPatches.getPatches(level);
        if (!patches.isEmpty()) {
            long x = 0, z = 0;
            for (BlockPos p : patches) {
                x += p.getX();
                z += p.getZ();
            }
            return new BlockPos((int) (x / patches.size()), level.getSharedSpawnPos().getY(), (int) (z / patches.size()));
        }
        if (state.mapPreset().premade())
            return new BlockPos(state.mapCx(), level.getSharedSpawnPos().getY(), state.mapCz());
        return level.getSharedSpawnPos();
    }

    // ---- /rts-newmatch ----

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent evt) {
        // for hosts until the lobby has a button; level 2 like /rts-reset
        evt.getDispatcher().register(Commands.literal("rts-newmatch")
            .requires(source -> source.hasPermission(2))
            .executes(ctx -> run(ctx.getSource(), false))
            .then(Commands.literal("force").executes(ctx -> run(ctx.getSource(), true))));
    }

    private static int run(CommandSourceStack source, boolean force) {
        Result r = start(source.getServer(), null, force);
        if (r.ok()) {
            source.sendSuccess(() -> Component.literal(r.message()), true);
            return 1;
        }
        source.sendFailure(Component.literal(r.message()));
        return 0;
    }
}
