package com.solegendary.reignofnether.resources;

import com.solegendary.reignofnether.registrars.PacketHandler;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Wreck metal for the reclaim labels: parallel (entity id, whole metal) arrays. See WreckSyncServerEvents. */
public class WreckSyncClientboundPacket {

    static final int MAX_ENTRIES = 512;

    private final int[] ids;
    private final int[] metals;

    // per player only (WreckSyncServerEvents: each player gets the heaps they can see; it was one list to everyone)
    public static void sendTo(ServerPlayer sp, int[] ids, int[] metals) {
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp), new WreckSyncClientboundPacket(ids, metals));
    }

    public WreckSyncClientboundPacket(int[] ids, int[] metals) {
        this.ids = ids;
        this.metals = metals;
    }

    public WreckSyncClientboundPacket(FriendlyByteBuf buffer) {
        int n = Math.min(buffer.readVarInt(), MAX_ENTRIES);
        ids = new int[n];
        metals = new int[n];
        for (int i = 0; i < n; i++) {
            ids[i] = buffer.readVarInt();
            metals[i] = buffer.readVarInt();
        }
    }

    public void encode(FriendlyByteBuf buffer) {
        int n = Math.min(ids.length, MAX_ENTRIES);
        buffer.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buffer.writeVarInt(ids[i]);
            buffer.writeVarInt(metals[i]);
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> WreckClientEvents.sync(ids, metals)));
        ctx.get().setPacketHandled(true);
        return true;
    }
}
