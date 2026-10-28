package com.solegendary.reignofnether.maps;

import com.solegendary.reignofnether.registrars.PacketHandler;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The start-position phase, client to server: a click ("my commander starts here", the server checks the box), Ready,
 * un-ready, and the host's Force start (StartPhase; the server checks who is host).
 */
public class StartPhaseServerboundPacket {

    public enum Action { PLACE, READY, UNREADY, FORCE_START }

    final Action action;
    final int x, z;

    public StartPhaseServerboundPacket(Action action, int x, int z) {
        this.action = action;
        this.x = x;
        this.z = z;
    }

    public StartPhaseServerboundPacket(int x, int z) {
        this(Action.PLACE, x, z);
    }

    public static void send(int x, int z) {
        PacketHandler.INSTANCE.sendToServer(new StartPhaseServerboundPacket(Action.PLACE, x, z));
    }

    public static void sendReady(boolean ready) {
        PacketHandler.INSTANCE.sendToServer(new StartPhaseServerboundPacket(ready ? Action.READY : Action.UNREADY, 0, 0));
    }

    public static void sendForceStart() {
        PacketHandler.INSTANCE.sendToServer(new StartPhaseServerboundPacket(Action.FORCE_START, 0, 0));
    }

    public StartPhaseServerboundPacket(FriendlyByteBuf buf) {
        int a = buf.readByte();
        action = a >= 0 && a < Action.values().length ? Action.values()[a] : null;
        x = buf.readVarInt();
        z = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(action.ordinal());
        buf.writeVarInt(x);
        buf.writeVarInt(z);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (action == null)
                return;   // an unknown action from a mismatched client: nothing to do
            switch (action) {
                case PLACE -> StartPhase.handlePlace(ctx.get().getSender(), x, z);
                case READY -> StartPhase.handleReady(ctx.get().getSender(), true);
                case UNREADY -> StartPhase.handleReady(ctx.get().getSender(), false);
                case FORCE_START -> StartPhase.handleForceStart(StartPhase.live(), ctx.get().getSender());
            }
        });
        ctx.get().setPacketHandled(true);
        return true;
    }
}
