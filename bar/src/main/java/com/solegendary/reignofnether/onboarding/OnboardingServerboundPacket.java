package com.solegendary.reignofnether.onboarding;

import com.solegendary.reignofnether.network.PacketAuth;
import com.solegendary.reignofnether.registrars.PacketHandler;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * First Battle, client to server: START (faction code; the host only, it lays out the scenario), REPORT (the client
 * saw a camera / selection step happen, or pressed Continue on an info step), SKIP (the step the player saw) and QUIT.
 * All rare clicks; rate-limited anyway.
 */
public class OnboardingServerboundPacket {

    /** Sent by ordinal: append only. */
    public enum Action { START, REPORT, SKIP, QUIT }

    final Action action;
    final int arg;

    OnboardingServerboundPacket(Action action, int arg) {
        this.action = action;
        this.arg = arg;
    }

    public static void start(int factionCode) {
        PacketHandler.INSTANCE.sendToServer(new OnboardingServerboundPacket(Action.START, factionCode));
    }

    public static void report(OnboardingStep step) {
        PacketHandler.INSTANCE.sendToServer(new OnboardingServerboundPacket(Action.REPORT, step.ordinal()));
    }

    public static void skip(OnboardingStep step) {
        PacketHandler.INSTANCE.sendToServer(new OnboardingServerboundPacket(Action.SKIP, step.ordinal()));
    }

    public static void quit() {
        PacketHandler.INSTANCE.sendToServer(new OnboardingServerboundPacket(Action.QUIT, 0));
    }

    public OnboardingServerboundPacket(FriendlyByteBuf buf) {
        action = buf.readEnum(Action.class);
        arg = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeVarInt(arg);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null || !PacketAuth.allow(sp, "onboarding", 5, 10))
                return;
            switch (action) {
                case START -> {
                    // the scenario sizes the arena and spawns a bot for the whole world: the host's call only
                    if (PacketAuth.isHost(sp))
                        OnboardingServer.start(sp, arg);
                }
                case REPORT -> OnboardingServer.onClientReport(sp, arg);
                case SKIP -> OnboardingServer.onSkip(sp, arg);
                case QUIT -> OnboardingServer.onQuit(sp);
            }
        });
        ctx.get().setPacketHandled(true);
        return true;
    }
}
