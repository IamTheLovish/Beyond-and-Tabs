package com.solegendary.reignofnether.gametest;

import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.worldborder.WorldBorderServerEvents;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * World effects under the fog, end to end (backlog 5a, #510; Command Center Eris.15 and Eris.16, Nov 6): the server
 * makes an effect at a spot the way the game does, and a packet recorder on each player's connection keeps what reaches
 * it. A player whose fog hides the spot must get none of it; a player the fog does not apply to gets all of it (the
 * control, so a quiet recorder can't pass for a gate). One test per leak family - one way the server sends an effect
 * that names a position:
 * <ul>
 *   <li><b>the positional broadcast</b> ({@code PlayerList.broadcast}, gated once at its entry by PlayerListFogMixin):
 *       a level event (2001, a block breaking), a sound (playSeededSound), and a block event (a chest lid, a bell, a
 *       note block - sent the way ServerLevel.runBlockEvents sends it at the end of the tick);</li>
 *   <li><b>per-player sends</b> past that broadcast (ServerLevelEffectFogMixin): a block's crack overlay
 *       (destroyBlockProgress) and an explosion (explode);</li>
 *   <li><b>global level events</b> ({@code ServerLevel.globalLevelEvent}: a wither spawning 1023, a dragon dying 1028,
 *       an end portal opening 1038), sent to every player online, wherever they are, with the event's real position.</li>
 * </ul>
 * The fourth item on the Oct 28 list (#35, "Fog leaks"), order targets, is inbound and was closed in #511
 * (UnitActionFogGameTests), except ATTACK_BUILDING (no server-side record of the buildings a player has seen).
 * <p>
 * The players join the server the way a real one does, through {@code PlayerList.placeNewPlayer} on a Connection with an
 * in-memory channel (netty's EmbeddedChannel: nothing is encoded or leaves the process), so they are in the lists every
 * one of those calls walks (PlayerList.players, ServerLevel.players). Neither list can be added to directly (Forge
 * 1.20.1 hands out read-only views: red runs 42084276870 and 42093363651), and no reflection is used. Their connections
 * are then swapped for the recorder; they are never ticked (the channel is not in the server's connection list) and
 * leave through {@code PlayerList.remove} before anything is put back.
 * <p>
 * The test server runs with fog off, so each test does everything inside ONE callback, in one server tick, in a batch
 * of its own, and puts it back before it returns: the border fitted for a match, fog on, then off again and the border
 * restored.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class FogEffectEndToEndGameTests {

    /** A player's connection that keeps what it is sent (replaces the one placeNewPlayer made, on the same Connection). */
    static final class Recorder extends ServerGamePacketListenerImpl {
        final List<Packet<?>> sent = new ArrayList<>();

        Recorder(MinecraftServer server, Connection connection, ServerPlayer player) {
            super(server, connection, player);
        }

        @Override
        public void send(Packet<?> packet) {
            sent.add(packet);
        }

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
            sent.add(packet);
        }
    }

    /** A player joined to the server through placeNewPlayer, with a recorder for its connection. */
    record Joined(ServerPlayer player, EmbeddedChannel channel, Recorder recorder) { }

    static Joined join(ServerLevel level, String name) {
        MinecraftServer server = level.getServer();
        ServerPlayer player = new ServerPlayer(server, level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);   // channelActive: the Connection has its channel
        connection.setProtocol(ConnectionProtocol.PLAY);
        server.getPlayerList().placeNewPlayer(connection, player);
        return new Joined(player, channel, new Recorder(server, connection, player));
    }

    /** The effects a recorder got, by name, in a fixed order ("levelEvent sound blockEvent crack explode globalEvent"). */
    static String got(Recorder r) {
        boolean ev = false, snd = false, blk = false, crack = false, boom = false, global = false;
        for (Packet<?> p : r.sent) {
            if (p instanceof ClientboundLevelEventPacket e) {
                if (e.isGlobalEvent())
                    global = true;
                else
                    ev = true;
            }
            snd |= p instanceof ClientboundSoundPacket;
            blk |= p instanceof ClientboundBlockEventPacket;
            crack |= p instanceof ClientboundBlockDestructionPacket;
            boom |= p instanceof ClientboundExplodePacket;
        }
        List<String> out = new ArrayList<>();
        if (ev) out.add("levelEvent");
        if (snd) out.add("sound");
        if (blk) out.add("blockEvent");
        if (crack) out.add("crack");
        if (boom) out.add("explode");
        if (global) out.add("globalEvent");
        return String.join(" ", out);
    }

    /** Where a throw came from: "IllegalStateException: msg at Class.method:line < ...", the first frames. */
    static String where(Throwable t) {
        StringBuilder sb = new StringBuilder(t.getClass().getSimpleName()).append(": ").append(t.getMessage());
        StackTraceElement[] st = t.getStackTrace();
        for (int i = 0; i < Math.min(6, st.length); i++)
            sb.append(i == 0 ? " at " : " < ").append(st[i].getClassName().replaceAll(".*\\.", ""))
              .append('.').append(st[i].getMethodName()).append(':').append(st[i].getLineNumber());
        return sb.toString();
    }

    /** The effects of one family, made at {@code at} as the game makes them. */
    @FunctionalInterface
    interface Effects {
        void make(ServerLevel level, BlockPos at, double x, double y, double z);
    }

    /**
     * Joins a fogged RTS player and a control player 6 blocks from {@code at} (inside every effect's range), turns fog
     * on, makes the effects, and checks: the control got exactly {@code all}, the fogged player nothing. Everything is
     * put back before it returns.
     */
    static void scene(GameTestHelper helper, String tag, String all, Effects effects) {
        ServerLevel level = helper.getLevel();
        PlayerList list = level.getServer().getPlayerList();
        List<String> problems = new ArrayList<>();
        var border = level.getWorldBorder();
        double size = border.getSize(), cx = border.getCenterX(), cz = border.getCenterZ();
        boolean wasOn = FogOfWarServerEvents.isEnabled();
        BlockPos at = helper.absolutePos(new BlockPos(8, 2, 8));
        double x = at.getX() + 0.5, y = at.getY() + 0.5, z = at.getZ() + 0.5;
        String base = "gametest_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
        String rtsName = base + "_rts";
        List<Joined> joined = new ArrayList<>();
        String step = "setup";
        try {
            if (wasOn || size <= WorldBorderServerEvents.RTS_OPTIMIZED_BORDER) {
                problems.add("the test level already has fog on or a small border; cannot run the effects");
            } else {
                step = "join the fogged player";
                Joined rts = join(level, rtsName);
                joined.add(rts);
                step = "join the control player";
                Joined watcher = join(level, base + "_obs");
                joined.add(watcher);
                step = "place the players";
                for (Joined j : joined) {
                    if (j.player().serverLevel() != level)
                        problems.add(j.player().getGameProfile().getName() + " joined another level ("
                            + j.player().serverLevel().dimension().location() + ")");
                    j.player().moveTo(x + 6, at.getY(), z, 0, 0);
                }
                if (!list.getPlayers().contains(rts.player()) || !level.players().contains(rts.player()))
                    problems.add("the joined player is not in the player list / the level's players (nothing tested)");
                step = "turn fog on";
                MatchLifecycleCases.side(rtsName, -77_002);   // an RTS side: the fog applies to it, not to the watcher
                if (!FogOfWarServerEvents.fitBorderForMatch(level, at.getX(), at.getZ(), 320, 420 + 35, true))
                    problems.add("no border was fitted (fog can't be turned on)");
                FogOfWarServerEvents.setEnabled(true);
                if (!FogOfWarServerEvents.isEnabled())
                    problems.add("fog did not turn on");
                else if (!FogOfWarServerEvents.isFogActiveFor(rts.player()))
                    problems.add("the fog does not apply to the RTS player (nothing tested)");
                else if (FogOfWarServerEvents.isFogActiveFor(watcher.player()))
                    problems.add("the fog applies to the watcher too (no control)");
                else if (problems.isEmpty()) {
                    step = "make the effects";
                    rts.recorder().sent.clear();
                    watcher.recorder().sent.clear();
                    effects.make(level, at, x, y, z);
                    String control = got(watcher.recorder()), leaked = got(rts.recorder());
                    if (!control.equals(all))
                        problems.add("a player the fog does not apply to got [" + control + "], expected [" + all + "]");
                    if (!leaked.isEmpty())
                        problems.add("under fog, with no vision of the spot, the player was still sent [" + leaked + "]");
                }
            }
        } catch (RuntimeException e) {
            problems.add("threw while trying to " + step + ": " + where(e));
        } finally {
            for (Joined j : joined) {
                try {
                    list.remove(j.player());
                } catch (RuntimeException e) {
                    problems.add("removing " + j.player().getGameProfile().getName() + " threw: " + where(e));
                }
                j.channel().finishAndReleaseAll();
            }
            // a join that threw part-way may have listed its player before join() returned it
            for (ServerPlayer sp : new ArrayList<>(list.getPlayers()))
                if (sp.getGameProfile().getName().startsWith(base)) {
                    try {
                        list.remove(sp);
                    } catch (RuntimeException e) {
                        problems.add("removing " + sp.getGameProfile().getName() + " threw: " + where(e));
                    }
                }
            MatchLifecycleCases.forget(rtsName);
            if (FogOfWarServerEvents.isEnabled() && !wasOn)
                FogOfWarServerEvents.setEnabled(false);
            border.setCenter(cx, cz);
            border.setSize(size);
        }
        for (ServerPlayer sp : list.getPlayers())
            if (sp.getGameProfile().getName().startsWith(base))
                problems.add(sp.getGameProfile().getName() + " is still in the player list");
        if (FogOfWarServerEvents.isEnabled() != wasOn)
            problems.add("fog flag was not restored");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * The positional broadcast: a level event, a sound and a block event. The block event is sent as
     * ServerLevel.runBlockEvents sends it (PlayerList.broadcast, 64 blocks, this dimension) - runBlockEvents itself
     * only runs at the end of the level's tick, after this one-callback test has put the fog back.
     */
    @GameTest(template = SkirmishGameTests.ARENA, batch = "fog_effects_broadcast", timeoutTicks = 20)
    public static void a_fogged_player_is_sent_none_of_a_spots_broadcast_effects(GameTestHelper helper) {
        scene(helper, "fogfx_bc", "levelEvent sound blockEvent", (level, at, x, y, z) -> {
            level.levelEvent(null, 2001, at, Block.getId(Blocks.STONE.defaultBlockState()));
            level.playSeededSound(null, x, y, z, SoundEvents.NOTE_BLOCK_BELL, SoundSource.BLOCKS, 1f, 1f, 0L);
            level.getServer().getPlayerList().broadcast(null, x, y, z, 64, level.dimension(),
                new ClientboundBlockEventPacket(at, Blocks.NOTE_BLOCK, 0, 0));
        });
    }

    /** The per-player sends: a block's crack overlay and an explosion (no blocks broken: ExplosionInteraction.NONE). */
    @GameTest(template = SkirmishGameTests.ARENA, batch = "fog_effects_per_player", timeoutTicks = 20)
    public static void a_fogged_player_is_sent_neither_a_spots_crack_nor_its_explosion(GameTestHelper helper) {
        scene(helper, "fogfx_pp", "crack explode", (level, at, x, y, z) -> {
            level.destroyBlockProgress(-77_002, at, 5);
            level.explode(null, x, y, z, 0.5f, Level.ExplosionInteraction.NONE);
        });
    }

    /** A global level event (1023, a wither spawning): every player online is sent its real position. */
    @GameTest(template = SkirmishGameTests.ARENA, batch = "fog_effects_global", timeoutTicks = 20)
    public static void a_fogged_player_is_not_sent_a_global_event_at_a_hidden_spot(GameTestHelper helper) {
        scene(helper, "fogfx_gl", "globalEvent", (level, at, x, y, z) -> level.globalLevelEvent(1023, at, 0));
    }
}
