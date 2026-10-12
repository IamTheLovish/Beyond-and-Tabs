package com.solegendary.reignofnether.gamemode;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class GameModeServerboundPacket {

    public GameMode gameMode;

    // copies the gamemode to all other clients
    public static void setAndLockAllClientGameModes(GameMode mode) {
        PacketHandler.INSTANCE.sendToServer(new GameModeServerboundPacket(mode));
    }

    public GameModeServerboundPacket(GameMode gameMode) {
        this.gameMode = gameMode;
    }

    public GameModeServerboundPacket(FriendlyByteBuf buffer) {
        this.gameMode = buffer.readEnum(GameMode.class);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.gameMode);
    }

    // server-side packet-consuming functions
    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        final var success = new AtomicBoolean(false);
        ctx.get().enqueueWork(() -> {
            // the game mode is match-wide: only the host (or an op) may set it, not a player who just joined
            if (!com.solegendary.reignofnether.network.PacketAuth.isHostOrOp(ctx.get().getSender(), 2)) {
                ReignOfNether.LOGGER.warn("[GameMode] refused a game mode change from a non-host");
                return;
            }
            ReignOfNether.LOGGER.info("[GameMode] Setting game mode to: {}", this.gameMode);
            GameModeClientboundPacket.setAndLockAllClientGameModes(this.gameMode);
            success.set(true);
        });
        ctx.get().setPacketHandled(true);
        return success.get();
    }
}