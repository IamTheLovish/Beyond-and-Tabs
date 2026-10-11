package com.solegendary.reignofnether.mana;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Stormcallers Mana: the client asks to toggle its own Overcharge (HUD chip or keybind). The server only ever toggles
// the sender's own state and re-checks every rule (Mana faction, energy) in ManaServerEvents.setOvercharge; the new
// state reaches every client in the next EconomyClientboundPacket (4x/s), so there is no reply packet.
public class ManaServerboundPacket {

    public enum Action { TOGGLE_OVERCHARGE }

    private Action action;

    public static void toggleOvercharge() {
        // no (Action) constructor: next to the (FriendlyByteBuf) decoder it reads as ambiguous to tools/check_java.py
        ManaServerboundPacket packet = new ManaServerboundPacket();
        packet.action = Action.TOGGLE_OVERCHARGE;
        PacketHandler.INSTANCE.sendToServer(packet);
    }

    private ManaServerboundPacket() { }

    public ManaServerboundPacket(FriendlyByteBuf buffer) {
        this.action = buffer.readEnum(Action.class);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.action);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                ReignOfNether.LOGGER.warn("ManaServerboundPacket: sender was null");
                return;
            }
            if (action == Action.TOGGLE_OVERCHARGE)
                ManaServerEvents.toggleOvercharge(player.getName().getString());
        });
        ctx.get().setPacketHandled(true);
        return true;
    }
}
