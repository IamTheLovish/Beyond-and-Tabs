package com.solegendary.reignofnether.hero;

import com.solegendary.reignofnether.network.Audience;
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
 *   <li>STATE - to the unit's side: {@code unitId} is now possessed by {@code text} ("" = released), for the ally
 *       marker;</li>
 *   <li>START - to the possessor: the claim went through, put the camera in {@code unitId};</li>
 *   <li>END - to the possessor: back to the RTS view ({@code text}: why, "" when they chose to);</li>
 *   <li>OBJECTIVE - to the possessor: an order or ping became an objective (kind, point, optional target entity);</li>
 *   <li>ROLE - to a Hero-role player at match start (and on relog; "" when it ends, CR-17): Backspace with nothing
 *       selected possesses the team hero, and the client plays under the team's fog;</li>
 *   <li>SYNC - to the possessor (hero slice 3, movement prediction): the server's position of the unit after the
 *       client's claim {@code objective} (a sequence number) - a correction when the claim was impossible, or the
 *       start/end of a stretch where the server moves the unit itself ({@code targetId} flags, see SYNC_*);</li>
 *   <li>HIT - to the possessor: their unit hurt {@code unitId} for {@code targetId}/10 damage at x,y,z
 *       ({@code objective} HIT_KILL when it died) - the hit marker and damage number;</li>
 *   <li>CAST - to the possessor: what became of ability key {@code objective} ({@code targetId}: CAST_* result).</li>
 * </ul>
 * All small: claims, releases, orders; SYNC only on a correction (at most one per few ticks per possessor), HIT capped
 * per possessor per tick (PossessionServer).
 */
public class PossessionClientboundPacket {

    /** Sent by ordinal: append only. */
    public enum Kind { STATE, START, END, OBJECTIVE, ROLE, SYNC, HIT, CAST }

    /** SYNC flags: the server is moving the unit (an ability's AI window, a leap arc) - stop predicting. */
    public static final int SYNC_SUSPEND = 1;
    /** SYNC flags: far off (a teleport, a big rejection) - snap rather than blend. */
    public static final int SYNC_SNAP = 2;
    /** HIT flags. */
    public static final int HIT_KILL = 1;
    /** CAST results. */
    public static final int CAST_OK = 0, CAST_COOLDOWN = 1, CAST_MANA = 2, CAST_RANK = 3, CAST_BUSY = 4;

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
        // the "who drives it" tag shows only to the unit's side (PossessionClientEvents.onNameTag); a release for a
        // unit already gone goes to everyone, so no client keeps a tag it could never clear (network/Audience)
        net.minecraft.world.entity.Entity unit = Audience.entity(unitId);
        PacketHandler.INSTANCE.send(unit != null ? Audience.alliesOf(unit) : Audience.all(),
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

    /**
     * The Hero role on or off for {@code sp}. "On" carries the commanders they serve ("hero|a,b"): the client's team
     * fog treats those commanders' (and their allies') units as its own (hero slice 3). Names that would overflow the
     * 256-char field are left off - only the client's "never hide my team's units" shortcut reads them; the server's
     * vision is authoritative either way.
     */
    public static void role(ServerPlayer sp, boolean heroRole) {
        String text = "";
        if (heroRole) {
            StringBuilder sb = new StringBuilder("hero|");
            boolean first = true;
            for (String c : HeroRoles.commandersOf(sp.getName().getString())) {
                if (sb.length() + c.length() + 1 > 250)
                    break;
                if (!first)
                    sb.append(',');
                sb.append(c);
                first = false;
            }
            text = sb.toString();
        }
        send(sp, new PossessionClientboundPacket(Kind.ROLE, -1, text, 0, 0, 0, 0, -1));
    }

    static void sync(ServerPlayer sp, int unitId, int seq, double x, double y, double z, int flags) {
        send(sp, new PossessionClientboundPacket(Kind.SYNC, unitId, "", seq, x, y, z, flags));
    }

    static void hit(ServerPlayer sp, int victimId, double x, double y, double z, float amount, boolean killed) {
        int tenths = Math.max(0, Math.min(1_000_000, Math.round(amount * 10)));
        send(sp, new PossessionClientboundPacket(Kind.HIT, victimId, "", killed ? HIT_KILL : 0, x, y, z, tenths));
    }

    static void cast(ServerPlayer sp, int unitId, int slot, int result) {
        send(sp, new PossessionClientboundPacket(Kind.CAST, unitId, "", slot, 0, 0, 0, result));
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
