package com.solegendary.reignofnether.startpos;

import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Syncs every capture site (a handful per map) to all clients; sent every few seconds and on a change of owner. */
public class CapturePointsClientboundPacket {

    private final List<CapturePointsClient.Site> sites;

    public static void sendAll(List<Entity> points) {
        List<CapturePointsClient.Site> sites = new ArrayList<>(points.size());
        for (Entity p : points)
            if (!p.isRemoved()) {
                // the garrison's muster is as public as its owner: the ruin stands on open ground for all to see
                boolean garrison = CapturePointServerEvents.kindOf(p) == CapturePointServerEvents.Kind.GARRISON;
                int muster = garrison ? Math.round(100 * p.getPersistentData().getFloat(CapturePointServerEvents.KEY_MUSTER)
                    / CapturePointServerEvents.GARRISON_SECONDS) : 0;
                int banked = garrison ? p.getPersistentData().getInt(CapturePointServerEvents.KEY_BANK) : 0;
                sites.add(new CapturePointsClient.Site(p.getBlockX(), p.getBlockY(), p.getBlockZ(),
                    CapturePointServerEvents.kindOf(p).ordinal(), CapturePointServerEvents.ownerOf(p),
                    CapturePointServerEvents.progressOf(p), CapturePointServerEvents.claimantOf(p),
                    Math.max(0, Math.min(100, muster)), Math.max(0, Math.min(9, banked))));
            }
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new CapturePointsClientboundPacket(sites));
    }

    public CapturePointsClientboundPacket(List<CapturePointsClient.Site> sites) {
        this.sites = sites;
    }

    public static CapturePointsClientboundPacket decode(FriendlyByteBuf buffer) {
        int count = Math.min(buffer.readVarInt(), 256);
        List<CapturePointsClient.Site> sites = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            sites.add(new CapturePointsClient.Site(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readByte(), buffer.readUtf(), buffer.readFloat(), buffer.readUtf(), buffer.readByte(), buffer.readByte()));
        return new CapturePointsClientboundPacket(sites);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(sites.size());
        for (CapturePointsClient.Site s : sites) {
            buffer.writeVarInt(s.x());
            buffer.writeVarInt(s.y());
            buffer.writeVarInt(s.z());
            buffer.writeByte(s.kind());
            buffer.writeUtf(s.owner());
            buffer.writeFloat(s.progress());
            buffer.writeUtf(s.claimant());
            buffer.writeByte(s.muster());
            buffer.writeByte(s.banked());
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        final var success = new AtomicBoolean(false);
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                CapturePointsClient.sync(sites);
                success.set(true);
            });
        });
        ctx.get().setPacketHandled(true);
        return success.get();
    }
}
