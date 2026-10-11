package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.network.PacketAuth;
import com.solegendary.reignofnether.registrars.PacketHandler;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * One multiplayer-lobby click, client to server: an action, the seat it is about and a value (faction code, map
 * colour id, team, ready 0/1, difficulty, map setting). MultiplayerLobby.handle checks who may do it - your own
 * seat, or the host for bots and settings - so nothing here is trusted.
 */
public class LobbyServerboundPacket {

    private final MultiplayerLobby.Action action;
    private final int seatId;
    private final int value;

    public LobbyServerboundPacket(MultiplayerLobby.Action action, int seatId, int value) {
        this.action = action;
        this.seatId = seatId;
        this.value = value;
    }

    public static void send(MultiplayerLobby.Action action, int seatId, int value) {
        PacketHandler.INSTANCE.sendToServer(new LobbyServerboundPacket(action, seatId, value));
    }

    public LobbyServerboundPacket(FriendlyByteBuf buf) {
        action = buf.readEnum(MultiplayerLobby.Action.class);
        seatId = buf.readVarInt();
        value = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeVarInt(seatId);
        buf.writeInt(value);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            // every accepted click re-sends the lobby to everyone: 10 a second per player is plenty for a human
            if (sp != null && PacketAuth.allow(sp, "lobby", 10, 20))
                MultiplayerLobby.handle(sp, action, seatId, value);
        });
        ctx.get().setPacketHandled(true);
        return true;
    }
}
