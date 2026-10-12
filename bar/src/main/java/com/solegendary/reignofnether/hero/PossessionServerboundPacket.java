package com.solegendary.reignofnether.hero;

import com.solegendary.reignofnether.network.PacketAuth;
import com.solegendary.reignofnether.registrars.PacketHandler;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Possession, client to server (design/hero_mode.md). Nothing in it is trusted: PossessionServer checks who may
 * possess what, drops input from anyone but the unit's possessor, clamps the analogue values and finds attack and
 * ability targets itself along the look ray. Every kind is rate limited per player (PacketAuth.allow).
 * <ul>
 *   <li>POSSESS - take {@code unitId} (-1: my team's hero);</li>
 *   <li>RELEASE - back to the RTS view;</li>
 *   <li>INPUT - movement keys and view, sent when they change and as a heartbeat (at most 20 a second);</li>
 *   <li>ATTACK - left click at the current view; ABILITY - key 1-4 ({@code slot}) at the current view.</li>
 * </ul>
 */
public class PossessionServerboundPacket {

    /** Sent by ordinal: append only. */
    public enum Kind { POSSESS, RELEASE, INPUT, ATTACK, ABILITY }

    static final int JUMP = 1, DESCEND = 2;

    private final Kind kind;
    private final int unitId;
    private final byte forward, strafe, flags;
    private final float yaw, pitch;

    public PossessionServerboundPacket(Kind kind, int unitId, int forward, int strafe, float yaw, float pitch, int flags) {
        this.kind = kind;
        this.unitId = unitId;
        this.forward = (byte) forward;
        this.strafe = (byte) strafe;
        this.yaw = yaw;
        this.pitch = pitch;
        this.flags = (byte) flags;
    }

    public static void possess(int unitId) {
        PacketHandler.INSTANCE.sendToServer(new PossessionServerboundPacket(Kind.POSSESS, unitId, 0, 0, 0, 0, 0));
    }

    public static void release() {
        PacketHandler.INSTANCE.sendToServer(new PossessionServerboundPacket(Kind.RELEASE, -1, 0, 0, 0, 0, 0));
    }

    public static void input(int unitId, int forward, int strafe, float yaw, float pitch, boolean jump, boolean descend) {
        PacketHandler.INSTANCE.sendToServer(new PossessionServerboundPacket(Kind.INPUT, unitId, forward, strafe, yaw,
            pitch, (jump ? JUMP : 0) | (descend ? DESCEND : 0)));
    }

    public static void attack(int unitId, float yaw, float pitch) {
        PacketHandler.INSTANCE.sendToServer(new PossessionServerboundPacket(Kind.ATTACK, unitId, 0, 0, yaw, pitch, 0));
    }

    public static void ability(int unitId, int slot, float yaw, float pitch) {
        PacketHandler.INSTANCE.sendToServer(new PossessionServerboundPacket(Kind.ABILITY, unitId, 0, 0, yaw, pitch, slot));
    }

    public PossessionServerboundPacket(FriendlyByteBuf buf) {
        kind = buf.readEnum(Kind.class);
        unitId = buf.readInt();
        forward = buf.readByte();
        strafe = buf.readByte();
        yaw = buf.readFloat();
        pitch = buf.readFloat();
        flags = buf.readByte();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(kind);
        buf.writeInt(unitId);
        buf.writeByte(forward);
        buf.writeByte(strafe);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        buf.writeByte(flags);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null)
                return;
            switch (kind) {
                case POSSESS -> {
                    if (PacketAuth.allow(sp, "possess", 4, 8))
                        PossessionServer.requestPossess(sp, unitId);
                }
                case RELEASE -> {
                    if (PacketAuth.allow(sp, "possess", 4, 8))
                        PossessionServer.release(sp.getUUID(), null);
                }
                case INPUT -> {
                    // the client sends at most 20 a second; anything far past that is a modified client
                    if (PacketAuth.allow(sp, "possess_input", 30, 60))
                        PossessionServer.applyInput(sp.getUUID(), unitId, forward, strafe, yaw, pitch,
                            (flags & JUMP) != 0, (flags & DESCEND) != 0);
                }
                case ATTACK -> {
                    if (PacketAuth.allow(sp, "possess_act", 20, 30))
                        PossessionServer.attack(sp.getUUID(), unitId, yaw, pitch);
                }
                case ABILITY -> {
                    if (PacketAuth.allow(sp, "possess_act", 20, 30))
                        PossessionServer.ability(sp.getUUID(), unitId, flags, yaw, pitch);
                }
            }
        });
        ctx.get().setPacketHandled(true);
        return true;
    }
}
