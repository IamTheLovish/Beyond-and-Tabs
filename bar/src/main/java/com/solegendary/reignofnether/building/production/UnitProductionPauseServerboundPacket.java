package com.solegendary.reignofnether.building.production;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// "Pause unit production": the client asks to flip its own flag (HUD chip or keybind). The server only ever toggles the
// sender's own state; the new state reaches the HUD in the next EconomyClientboundPacket (4x/s), so there is no reply.
public class UnitProductionPauseServerboundPacket {

    public static void toggle() {
        PacketHandler.INSTANCE.sendToServer(new UnitProductionPauseServerboundPacket());
    }

    public UnitProductionPauseServerboundPacket() { }

    public UnitProductionPauseServerboundPacket(FriendlyByteBuf buffer) { }

    public void encode(FriendlyByteBuf buffer) { }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                ReignOfNether.LOGGER.warn("UnitProductionPauseServerboundPacket: sender was null");
                return;
            }
            UnitProductionPause.toggle(player.getName().getString());
        });
        ctx.get().setPacketHandled(true);
        return true;
    }
}
