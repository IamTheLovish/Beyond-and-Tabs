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
 *   <li>ATTACK - left click at the current view; ABILITY - key 1-4 ({@code slot}) at the current view;</li>
 *   <li>RANK_UP - Alt + key 1-4: spend an ability point on that slot (a hero; hero slice 2);</li>
 *   <li>MOVE - INPUT plus where the client's prediction put the unit after it, and the client tick ({@code seq})
 *       (hero slice 3): sent every tick while predicting, instead of INPUT. The server moves the real unit there
 *       only within what it could have walked (PossessionServer.applyClaim).</li>
 * </ul>
 */
public class PossessionServerboundPacket {

    /** Sent by ordinal: append only. */
    public enum Kind { POSSESS, RELEASE, INPUT, ATTACK, ABILITY, RANK_UP, MOVE }

    static final int JUMP = 1, DESCEND = 2;

    private final Kind kind;
    private final int unitId;
    private final byte forward, strafe, flags;
    private final float yaw, pitch;
    private final double x, y, z;
    private final int seq;

    public PossessionServerboundPacket(Kind kind, int unitId, int forward, int strafe, float yaw, float pitch, int flags) {
        this.kind = kind;
        this.unitId = unitId;
        this.forward = (byte) forward;
        this.strafe = (byte) strafe;
        this.yaw = yaw;
        this.pitch = pitch;
        this.flags = (byte) flags;
        this.x = this.y = this.z = Double.NaN;
        this.seq = 0;
    }

    private PossessionServerboundPacket(int unitId, int forward, int strafe, float yaw, float pitch, int flags,
                                        double x, double y, double z, int seq) {
        this.kind = Kind.MOVE;
        this.unitId = unitId;
        this.forward = (byte) forward;
        this.strafe = (byte) strafe;
        this.yaw = yaw;
        this.pitch = pitch;
        this.flags = (byte) flags;
        this.x = x;
        this.y = y;
        this.z = z;
        this.seq = seq;
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

    public static void move(int unitId, int forward, int strafe, float yaw, float pitch, boolean jump, boolean descend,
                            double x, double y, double z, int seq) {
        PacketHandler.INSTANCE.sendToServer(new PossessionServerboundPacket(unitId, forward, strafe, yaw, pitch,
            (jump ? JUMP : 0) | (descend ? DESCEND : 0), x, y, z, seq));
    }

    public static void attack(int unitId, float yaw, float pitch) {
        PacketHandler.INSTANCE.sendToServer(new PossessionServerboundPacket(Kind.ATTACK, unitId, 0, 0, yaw, pitch, 0));
    }

    public static void ability(int unitId, int slot, float yaw, float pitch) {
        PacketHandler.INSTANCE.sendToServer(new PossessionServerboundPacket(Kind.ABILITY, unitId, 0, 0, yaw, pitch, slot));
    }

    public static void rankUp(int unitId, int slot) {
        PacketHandler.INSTANCE.sendToServer(new PossessionServerboundPacket(Kind.RANK_UP, unitId, 0, 0, 0, 0, slot));
    }

    public PossessionServerboundPacket(FriendlyByteBuf buf) {
        kind = buf.readEnum(Kind.class);
        unitId = buf.readInt();
        forward = buf.readByte();
        strafe = buf.readByte();
        yaw = buf.readFloat();
        pitch = buf.readFloat();
        flags = buf.readByte();
        if (kind == Kind.MOVE) {
            x = buf.readDouble();
            y = buf.readDouble();
            z = buf.readDouble();
            seq = buf.readVarInt();
        } else {
            x = y = z = Double.NaN;
            seq = 0;
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(kind);
        buf.writeInt(unitId);
        buf.writeByte(forward);
        buf.writeByte(strafe);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        buf.writeByte(flags);
        if (kind == Kind.MOVE) {
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeVarInt(seq);
        }
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
                case MOVE -> {
                    // shares INPUT's bucket: one MOVE replaces one INPUT, at most 20 a second
                    if (PacketAuth.allow(sp, "possess_input", 30, 60))
                        PossessionServer.applyMove(sp.getUUID(), unitId, forward, strafe, yaw, pitch,
                            (flags & JUMP) != 0, (flags & DESCEND) != 0, x, y, z, seq);
                }
                case ATTACK -> {
                    if (PacketAuth.allow(sp, "possess_act", 20, 30))
                        PossessionServer.attack(sp.getUUID(), unitId, yaw, pitch);
                }
                case ABILITY -> {
                    if (PacketAuth.allow(sp, "possess_act", 20, 30))
                        PossessionServer.ability(sp.getUUID(), unitId, flags, yaw, pitch);
                }
                case RANK_UP -> {
                    if (PacketAuth.allow(sp, "possess_act", 20, 30))
                        PossessionServer.rankUp(sp.getUUID(), unitId, flags);
                }
            }
        });
        ctx.get().setPacketHandled(true);
        return true;
    }
}
