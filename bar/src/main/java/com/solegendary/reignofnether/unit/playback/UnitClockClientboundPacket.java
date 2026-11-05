package com.solegendary.reignofnether.unit.playback;

import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * The server's tick stamp (motion fluidity): sent to every player at the start of every server tick, before the level
 * ticks and its entity trackers send their packets, so the client knows that the move packets it reads after this
 * stamp belong to this tick (one connection keeps the order). The client draws units from those stamped positions on a
 * steady playback clock (UnitPlayback) instead of vanilla's 3-tick lerp, which stalls whenever a tick runs late.
 * A var-int a tick per player: a few bytes.
 */
public class UnitClockClientboundPacket {

    public final int tick;

    public UnitClockClientboundPacket(int tick) {
        this.tick = tick;
    }

    public UnitClockClientboundPacket(FriendlyByteBuf buffer) {
        this.tick = buffer.readVarInt();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(tick);
    }

    /** At the start of server tick {@code tick}, to everyone. */
    public static void broadcast(int tick) {
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new UnitClockClientboundPacket(tick));
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> UnitPlaybackClient.heard(tick)));
        ctx.get().setPacketHandled(true);
        return true;
    }
}
