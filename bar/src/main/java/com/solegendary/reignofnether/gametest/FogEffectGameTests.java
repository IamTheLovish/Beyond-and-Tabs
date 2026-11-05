package com.solegendary.reignofnether.gametest;

import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * World effects under the fog (backlog 5a, review rounds on #510): a player whose fog hides a spot must not be sent the
 * effects that happen there - a level event (a block breaking, 2001), a sound, a block's crack overlay
 * (destroyBlockProgress) and an explosion. Two parts prove it:
 * <ul>
 *   <li><b>The mixins fire:</b> every server-side fog injection is {@code require = 1}, so a missed target fails the
 *       game at startup (the mixin config has no defaultRequire; before, a miss was silent). This test server starting
 *       is that check.</li>
 *   <li><b>What the mixins decide:</b> this test feeds the two decisions the packets the level makes, as the mixins do:
 *       FogOfWarServerEvents.broadcastSeen (PlayerListFogMixin hands it PlayerList.broadcast's players and arguments:
 *       the level event and the sound) and FogOfWarServerEvents.effectSeenBy (ServerLevelEffectFogMixin's two
 *       redirects: the crack and the explosion).</li>
 * </ul>
 * Why not the effects end to end: PlayerList.getPlayers() and ServerLevel.players() are read-only in Forge 1.20.1
 * (red runs 42084276870 and 42093363651: UnsupportedOperationException at Collections$UnmodifiableCollection.add), so a
 * test cannot put a fake player in either list, and reflection into vanilla's private lists was ruled out.
 * <p>
 * The test server runs with fog off, so everything happens inside ONE callback, in one server tick, and is put back
 * before it returns: the border is fitted for a match and fog turned on; a fake player the fog does not apply to (no
 * RTS side) must be sent each effect (the control), and a fake RTS player with no vision anywhere none.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class FogEffectGameTests {

    /**
     * A fake player's connection that keeps what it is sent. A plain ServerGamePacketListenerImpl on a channel-less
     * Connection (Forge 1.20.1 has no public FakePlayerNetHandler); its constructor makes itself the player's connection,
     * and the test puts the old one back. Never ticked: it is not in the server's connection list.
     */
    static final class Recorder extends ServerGamePacketListenerImpl {
        final List<Packet<?>> sent = new ArrayList<>();

        Recorder(ServerPlayer player) {
            super(player.getServer(), new Connection(PacketFlow.CLIENTBOUND), player);
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

    /** "levelEvent sound crack explode": which of the four effects {@code r} was sent, by packet type. */
    static String got(Recorder r) {
        boolean ev = false, snd = false, crack = false, boom = false;
        for (Packet<?> p : r.sent) {
            ev |= p instanceof ClientboundLevelEventPacket;
            snd |= p instanceof ClientboundSoundPacket;
            crack |= p instanceof ClientboundBlockDestructionPacket;
            boom |= p instanceof ClientboundExplodePacket;
        }
        List<String> out = new ArrayList<>();
        if (ev) out.add("levelEvent");
        if (snd) out.add("sound");
        if (crack) out.add("crack");
        if (boom) out.add("explode");
        return String.join(" ", out);
    }

    /**
     * The four effects at {@code at}, as the level makes their packets and the mixins hand them on: the level event and
     * the sound through broadcastSeen (PlayerList.broadcast's arguments: no player excepted, 64 / 16 blocks, this
     * dimension), the crack and the explosion through effectSeenBy, sent when it says yes (the redirects).
     */
    static void fire(ServerLevel level, BlockPos at, ServerPlayer to) {
        double x = at.getX() + 0.5, y = at.getY() + 0.5, z = at.getZ() + 0.5;
        List<ServerPlayer> one = List.of(to);
        FogOfWarServerEvents.broadcastSeen(one, null, x, y, z, 64, level.dimension(),
            new ClientboundLevelEventPacket(2001, at, Block.getId(Blocks.STONE.defaultBlockState()), false));
        FogOfWarServerEvents.broadcastSeen(one, null, x, y, z, 16, level.dimension(),
            new ClientboundSoundPacket(SoundEvents.NOTE_BLOCK_BELL, SoundSource.BLOCKS, x, y, z, 1f, 1f, 0L));
        for (Packet<?> p : List.<Packet<?>>of(new ClientboundBlockDestructionPacket(-77_001, at, 5),
                new ClientboundExplodePacket(x, y, z, 0.5f, List.of(), null)))
            if (FogOfWarServerEvents.effectSeenBy(to, p))
                to.connection.send(p);
    }

    static FakePlayer fake(ServerLevel level, String name) {
        return FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
    }

    @GameTest(template = SkirmishGameTests.ARENA, batch = "fog_effects", timeoutTicks = 20)
    public static void a_player_whose_fog_hides_a_spot_gets_none_of_its_effects(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        var border = level.getWorldBorder();
        double size = border.getSize(), cx = border.getCenterX(), cz = border.getCenterZ();
        boolean wasOn = FogOfWarServerEvents.isEnabled();
        BlockPos at = helper.absolutePos(new BlockPos(8, 2, 8));
        String base = "gametest_fogfx_" + helper.absolutePos(BlockPos.ZERO).asLong();
        String rtsName = base + "_rts";
        FakePlayer rts = fake(level, rtsName), watcher = fake(level, base + "_obs");
        var rtsWas = rts.connection;
        var watcherWas = watcher.connection;
        try {
            if (wasOn || size <= com.solegendary.reignofnether.worldborder.WorldBorderServerEvents.RTS_OPTIMIZED_BORDER) {
                problems.add("the test level already has fog on or a small border; cannot run the effects");
            } else {
                Recorder toRts = new Recorder(rts), toWatcher = new Recorder(watcher);
                for (FakePlayer p : List.of(rts, watcher))
                    p.moveTo(at.getX() + 6.5, at.getY(), at.getZ() + 0.5, 0, 0);   // 6 blocks away: in every range
                MatchLifecycleCases.side(rtsName, -77_001);   // an RTS player: the fog applies to it, not to the watcher
                if (!FogOfWarServerEvents.fitBorderForMatch(level, at.getX(), at.getZ(), 320, 420 + 35, true))
                    problems.add("no border was fitted (fog can't be turned on)");
                FogOfWarServerEvents.setEnabled(true);
                if (!FogOfWarServerEvents.isEnabled())
                    problems.add("fog did not turn on");
                else if (!FogOfWarServerEvents.isFogActiveFor(rts))
                    problems.add("the fog does not apply to the fake RTS player (nothing tested)");
                else if (FogOfWarServerEvents.isFogActiveFor(watcher))
                    problems.add("the fog applies to the fake watcher too (no control)");
                else {
                    toRts.sent.clear();
                    toWatcher.sent.clear();
                    fire(level, at, watcher);
                    fire(level, at, rts);
                    String control = got(toWatcher), leaked = got(toRts);
                    if (!control.equals("levelEvent sound crack explode"))
                        problems.add("a player the fog does not apply to got only [" + control + "], expected all four");
                    if (!leaked.isEmpty())
                        problems.add("under fog, with no vision of the spot, the player was still sent [" + leaked + "]");
                }
            }
        } finally {
            rts.connection = rtsWas;
            watcher.connection = watcherWas;
            MatchLifecycleCases.forget(rtsName);
            if (FogOfWarServerEvents.isEnabled() && !wasOn)
                FogOfWarServerEvents.setEnabled(false);
            border.setCenter(cx, cz);
            border.setSize(size);
        }
        if (FogOfWarServerEvents.isEnabled() != wasOn)
            problems.add("fog flag was not restored");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
