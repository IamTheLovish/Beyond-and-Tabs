package com.solegendary.reignofnether.hero;

import com.solegendary.reignofnether.registrars.PacketHandler;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * Possession, server to client (design/hero_mode.md):
 * <ul>
 *   <li>STATE - to everyone: {@code unitId} is now possessed by {@code text} ("" = released), for the ally marker;</li>
 *   <li>START - to the possessor: the claim went through, put the camera in {@code unitId};</li>
 *   <li>END - to the possessor: back to the RTS view ({@code text}: why, "" when they chose to);</li>
 *   <li>OBJECTIVE - to the possessor: an order or ping became an objective (kind, point, optional target entity);</li>
 *   <li>ROLE - to a Hero-role player at match start: Backspace with nothing selected possesses the team hero.</li>
 * </ul>
 * All small and rare (claims, releases, orders) - nothing per tick.
 */
public class PossessionClientboundPacket {

    /** Sent by ordinal: append only. */
    public enum Kind { STATE, START, END, OBJECTIVE, ROLE }

    public final Kind kind;
    public final int unitId;
    public final String text;
    public final int objective;
    public final double x, y, z;
    public final int targetId;

    PossessionClientboundPacket(Kind kind, int unitId, String text, int objective, double x, double y, double z, int targetId) {
        this.kind = kind;
        this.unitId = unitId;
        this.text = text == null ? "" : text;
        this.objective = objective;
        this.x = x;
        this.y = y;
        this.z = z;
        this.targetId = targetId;
    }

    static void broadcastState(int unitId, String possessor) {
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
            new PossessionClientboundPacket(Kind.STATE, unitId, possessor, 0, 0, 0, 0, -1));
    }

    static void start(ServerPlayer sp, int unitId) {
        send(sp, new PossessionClientboundPacket(Kind.START, unitId, "", 0, 0, 0, 0, -1));
    }

    static void end(ServerPlayer sp, int unitId, String reason) {
        send(sp, new PossessionClientboundPacket(Kind.END, unitId, reason, 0, 0, 0, 0, -1));
    }

    static void objective(ServerPlayer sp, PossessionServer.Objective o, double x, double y, double z, int targetId) {
        send(sp, new PossessionClientboundPacket(Kind.OBJECTIVE, -1, "", o.ordinal(), x, y, z, targetId));
    }

    public static void role(ServerPlayer sp, boolean heroRole) {
        send(sp, new PossessionClientboundPacket(Kind.ROLE, -1, heroRole ? "hero" : "", 0, 0, 0, 0, -1));
    }

    private static void send(ServerPlayer sp, PossessionClientboundPacket p) {
        // fake players (game tests) have no connection to send on
        if (sp == null || sp instanceof FakePlayer)
            return;
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp), p);
    }

    public PossessionClientboundPacket(FriendlyByteBuf buf) {
        kind = buf.readEnum(Kind.class);
        unitId = buf.readInt();
        text = buf.readUtf(256);
        objective = buf.readVarInt();
        x = buf.readDouble();
        y = buf.readDouble();
        z = buf.readDouble();
        targetId = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(kind);
        buf.writeInt(unitId);
        buf.writeUtf(text, 256);
        buf.writeVarInt(objective);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeInt(targetId);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PossessionClientEvents.onPacket(this)));
        ctx.get().setPacketHandled(true);
        return true;
    }
}
