package com.solegendary.reignofnether.hero.wager;

import com.solegendary.reignofnether.registrars.PacketHandler;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * Server to client for the Wager Book: whether the debug toggle is on (so key 1 of a hero opens the book), and Lovish's
 * reply to a page, written into the open book with the margin tally and the pages left.
 */
public class WagerClientboundPacket {

    static final int TOGGLE = 0, REPLY = 1;
    static final int MAX = 512;

    final int kind;
    final boolean enabled;
    final int heroId;
    final String request, reply, tally;
    final int pages;

    WagerClientboundPacket(int kind, boolean enabled, int heroId, String request, String reply, String tally, int pages) {
        this.kind = kind;
        this.enabled = enabled;
        this.heroId = heroId;
        this.request = cut(request);
        this.reply = cut(reply);
        this.tally = cut(tally);
        this.pages = pages;
    }

    static String cut(String s) {
        return s == null ? "" : s.length() > MAX ? s.substring(0, MAX) : s;
    }

    public WagerClientboundPacket(FriendlyByteBuf buffer) {
        this(buffer.readByte(), buffer.readBoolean(), buffer.readInt(), buffer.readUtf(MAX), buffer.readUtf(MAX),
            buffer.readUtf(MAX), buffer.readInt());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(kind);
        buffer.writeBoolean(enabled);
        buffer.writeInt(heroId);
        buffer.writeUtf(request, MAX);
        buffer.writeUtf(reply, MAX);
        buffer.writeUtf(tally, MAX);
        buffer.writeInt(pages);
    }

    static void toggle(ServerPlayer player, boolean on) {
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new WagerClientboundPacket(TOGGLE, on, -1, "", "", "", 0));
    }

    static void toggleAll(boolean on) {
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new WagerClientboundPacket(TOGGLE, on, -1, "", "", "", 0));
    }

    static void reply(ServerPlayer player, int heroId, String request, String reply, String tally, int pages) {
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
            new WagerClientboundPacket(REPLY, true, heroId, request, reply, tally, pages));
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (kind == TOGGLE)
                WagerClient.enabled = enabled;
            else
                WagerClient.onReply(heroId, request, reply, tally, pages);
        }));
        ctx.get().setPacketHandled(true);
        return true;
    }
}
