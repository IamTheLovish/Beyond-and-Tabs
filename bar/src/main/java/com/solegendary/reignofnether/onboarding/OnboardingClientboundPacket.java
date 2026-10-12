package com.solegendary.reignofnether.onboarding;

import com.solegendary.reignofnether.registrars.PacketHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * First Battle, server to the student: STEP (the current step, where its world highlight is, and whether a matching
 * building is going up) or END (the tutorial is over for this player: a lang key says why). Sent on step changes and
 * when the highlight or status changes, never per tick.
 */
public class OnboardingClientboundPacket {

    /** Sent by ordinal: append only. */
    public enum Kind { STEP, END }

    public final Kind kind;
    public final int step;
    public final boolean hasHighlight;
    public final BlockPos highlight;
    public final boolean inProgress;
    public final String text;

    OnboardingClientboundPacket(Kind kind, int step, BlockPos highlight, boolean inProgress, String text) {
        this.kind = kind;
        this.step = step;
        this.hasHighlight = highlight != null;
        this.highlight = highlight == null ? BlockPos.ZERO : highlight;
        this.inProgress = inProgress;
        this.text = text == null ? "" : text;
    }

    static void step(ServerPlayer sp, OnboardingSession s) {
        send(sp, new OnboardingClientboundPacket(Kind.STEP, s.step.ordinal(), s.highlight, s.inProgress, s.enemy));
    }

    static void end(ServerPlayer sp, String reasonKey) {
        send(sp, new OnboardingClientboundPacket(Kind.END, 0, null, false, reasonKey));
    }

    private static void send(ServerPlayer sp, OnboardingClientboundPacket p) {
        if (sp == null || sp instanceof FakePlayer)
            return;
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp), p);
    }

    public OnboardingClientboundPacket(FriendlyByteBuf buf) {
        kind = buf.readEnum(Kind.class);
        step = buf.readVarInt();
        hasHighlight = buf.readBoolean();
        highlight = buf.readBlockPos();
        inProgress = buf.readBoolean();
        text = buf.readUtf(128);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(kind);
        buf.writeVarInt(step);
        buf.writeBoolean(hasHighlight);
        buf.writeBlockPos(highlight);
        buf.writeBoolean(inProgress);
        buf.writeUtf(text, 128);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> OnboardingClient.onPacket(this)));
        ctx.get().setPacketHandled(true);
        return true;
    }
}
