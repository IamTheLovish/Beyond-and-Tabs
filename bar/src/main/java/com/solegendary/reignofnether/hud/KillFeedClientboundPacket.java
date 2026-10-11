package com.solegendary.reignofnether.hud;

import com.solegendary.reignofnether.registrars.PacketHandler;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * BAR kill feed: a handful of "killer x victim" lines, flushed by KillFeedServerEvents every few ticks and only to
 * the players who may know about each kill. Names travel as translation keys (entity description ids / building
 * keys), so the client shows them in its own language; duplicates within a flush are merged into one line with a
 * count ("x3"). ~40 bytes per line.
 */
public class KillFeedClientboundPacket {

    public static final int MAX_ENTRIES = 8;
    public static final int FLAG_BUILDING = 1;

    public record Entry(String killerOwner, String killerKey, String victimOwner, String victimKey, int count,
                        boolean building) {
        public boolean sameKill(Entry o) {
            return building == o.building && killerKey.equals(o.killerKey) && victimKey.equals(o.victimKey)
                && killerOwner.equals(o.killerOwner) && victimOwner.equals(o.victimOwner);
        }
    }

    public final List<Entry> entries;

    public KillFeedClientboundPacket(List<Entry> entries) {
        this.entries = entries;
    }

    public static void sendTo(ServerPlayer sp, List<Entry> entries) {
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp), new KillFeedClientboundPacket(entries));
    }

    public static KillFeedClientboundPacket decode(FriendlyByteBuf buffer) {
        int n = Math.min(buffer.readVarInt(), MAX_ENTRIES);
        List<Entry> entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String ko = buffer.readUtf(64);
            String kk = buffer.readUtf(128);
            String vo = buffer.readUtf(64);
            String vk = buffer.readUtf(128);
            int count = buffer.readVarInt();
            int flags = buffer.readByte();
            entries.add(new Entry(ko, kk, vo, vk, count, (flags & FLAG_BUILDING) != 0));
        }
        return new KillFeedClientboundPacket(entries);
    }

    public void encode(FriendlyByteBuf buffer) {
        int n = Math.min(entries.size(), MAX_ENTRIES);
        buffer.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Entry e = entries.get(i);
            buffer.writeUtf(e.killerOwner(), 64);
            buffer.writeUtf(e.killerKey(), 128);
            buffer.writeUtf(e.victimOwner(), 64);
            buffer.writeUtf(e.victimKey(), 128);
            buffer.writeVarInt(e.count());
            buffer.writeByte(e.building() ? FLAG_BUILDING : 0);
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> KillFeedClientEvents.receive(this.entries)));
        ctx.get().setPacketHandled(true);
        return true;
    }
}
