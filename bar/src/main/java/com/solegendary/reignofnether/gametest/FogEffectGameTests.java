package com.solegendary.reignofnether.gametest;

import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;

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
import net.minecraft.world.level.Level;
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
 * World effects under the fog (backlog 5a, review round 1 on #510): a player whose fog hides a spot must not be sent
 * the effects that happen there - a level event (a block breaking, 2001), a sound, a block's crack overlay
 * (destroyBlockProgress) and an explosion. The gates are mixins (PlayerListFogMixin at PlayerList.broadcast's entry,
 * ServerLevelEffectFogMixin's redirects) and the mixin config has no defaultRequire, so a missed target would be
 * silent: this test is what proves they fire.
 * <p>
 * The test server runs with fog off, so everything happens inside ONE callback, in one server tick, and is put back
 * before it returns (FogDefaultGameTests' pattern): the border is fitted for a match, a fake RTS player whose
 * connection records what it is sent joins the live player lists, the effects fire with fog off (the recorder must
 * see each - the control) and then with fog on (it must see none: the player has no vision anywhere yet), then the
 * player, the side, the fog flag and the border go back.
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

    /** Which of the four effects the recorder got, by packet type: "levelEvent sound crack explode". */
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

    /** The four effects at {@code at}: a block-break level event, a sound, a crack overlay, a harmless explosion. */
    static void fire(ServerLevel level, BlockPos at) {
        level.levelEvent(null, 2001, at, Block.getId(Blocks.STONE.defaultBlockState()));
        level.playSound(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, SoundEvents.NOTE_BLOCK_BELL.value(),
            SoundSource.BLOCKS, 1f, 1f);
        level.destroyBlockProgress(-77_001, at, 5);
        level.destroyBlockProgress(-77_001, at, -1);
        level.explode(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 0.5f, Level.ExplosionInteraction.NONE);
    }

    @GameTest(template = SkirmishGameTests.ARENA, batch = "fog_effects", timeoutTicks = 20)
    public static void a_player_whose_fog_hides_a_spot_gets_none_of_its_effects(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        var border = level.getWorldBorder();
        double size = border.getSize(), cx = border.getCenterX(), cz = border.getCenterZ();
        boolean wasOn = FogOfWarServerEvents.isEnabled();
        BlockPos at = helper.absolutePos(new BlockPos(8, 2, 8));
        String name = "gametest_fogfx_" + helper.absolutePos(BlockPos.ZERO).asLong();
        FakePlayer sp = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
        var oldConnection = sp.connection;
        Recorder rec = new Recorder(sp);
        List<ServerPlayer> listed = level.getServer().getPlayerList().getPlayers();
        List<ServerPlayer> inLevel = level.players();
        try {
            if (wasOn || size <= com.solegendary.reignofnether.worldborder.WorldBorderServerEvents.RTS_OPTIMIZED_BORDER) {
                problems.add("the test level already has fog on or a small border; cannot run the effects");
            } else {
                sp.connection = rec;
                sp.moveTo(at.getX() + 6.5, at.getY(), at.getZ() + 0.5, 0, 0);   // 6 blocks away: in every effect's range
                listed.add(sp);
                inLevel.add(sp);
                MatchLifecycleCases.side(name, -77_001);   // an RTS player: the fog applies to it

                fire(level, at);   // fog off: the control, the recorder sees all four
                String control = got(rec);
                if (!control.equals("levelEvent sound crack explode"))
                    problems.add("with fog off the player got only [" + control + "], expected all four (the recorder or the setup is wrong)");

                rec.sent.clear();
                if (!FogOfWarServerEvents.fitBorderForMatch(level, at.getX(), at.getZ(), 320, 420 + 35, true))
                    problems.add("no border was fitted (fog can't be turned on)");
                FogOfWarServerEvents.setEnabled(true);
                if (!FogOfWarServerEvents.isEnabled())
                    problems.add("fog did not turn on");
                else if (!FogOfWarServerEvents.isFogActiveFor(sp))
                    problems.add("the fog does not apply to the fake RTS player");
                else {
                    rec.sent.clear();   // (the enable message)
                    fire(level, at);
                    String leaked = got(rec);
                    if (!leaked.isEmpty())
                        problems.add("under fog, with no vision of the spot, the player was still sent [" + leaked + "]");
                }
            }
        } finally {
            listed.remove(sp);
            inLevel.remove(sp);
            sp.connection = oldConnection;
            MatchLifecycleCases.forget(name);
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
