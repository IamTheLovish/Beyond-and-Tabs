package com.solegendary.reignofnether.maps;

import com.solegendary.reignofnether.registrars.PacketHandler;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A click during the start-position phase: "my commander starts here". The server checks the box (StartPhase). */
public class StartPhaseServerboundPacket {

    final int x, z;

    public StartPhaseServerboundPacket(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public static void send(int x, int z) {
        PacketHandler.INSTANCE.sendToServer(new StartPhaseServerboundPacket(x, z));
    }

    public StartPhaseServerboundPacket(FriendlyByteBuf buf) {
        x = buf.readVarInt();
        z = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(x);
        buf.writeVarInt(z);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> StartPhase.handlePlace(ctx.get().getSender(), x, z));
        ctx.get().setPacketHandled(true);
        return true;
    }
}
