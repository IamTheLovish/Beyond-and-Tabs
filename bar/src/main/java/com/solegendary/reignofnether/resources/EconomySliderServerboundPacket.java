package com.solegendary.reignofnether.resources;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The economy bar's two BAR sliders: the client asks to move its OWN converter threshold (energy bar) or metal share
 * level (metal bar). The server only ever changes the sender's own economy and re-checks everything in
 * {@link EconomyServerEvents#setSlider} (a number, a pool, the rate limit, the clamp); the level comes back with the
 * next economy sync, so there is no reply packet.
 */
public class EconomySliderServerboundPacket {

    private boolean energy;
    private float level;

    /** energy = the converter threshold, else the metal share level; level 0-1 (the server clamps). */
    public static void send(boolean energy, float level) {
        // no (boolean, float) constructor: next to the (FriendlyByteBuf) decoder it reads as ambiguous to tools/check_java.py
        EconomySliderServerboundPacket packet = new EconomySliderServerboundPacket();
        packet.energy = energy;
        packet.level = level;
        PacketHandler.INSTANCE.sendToServer(packet);
    }

    private EconomySliderServerboundPacket() { }

    public EconomySliderServerboundPacket(FriendlyByteBuf buffer) {
        this.energy = buffer.readBoolean();
        this.level = buffer.readFloat();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(this.energy);
        buffer.writeFloat(this.level);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                ReignOfNether.LOGGER.warn("EconomySliderServerboundPacket: sender was null");
                return;
            }
            EconomyServerEvents.setSlider(player.getName().getString(), energy, level);
        });
        ctx.get().setPacketHandled(true);
        return true;
    }
}
