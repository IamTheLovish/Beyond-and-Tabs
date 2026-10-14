package com.solegendary.reignofnether.unit.reactions;

import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** A reaction fired on a unit, for the clients tracking it: which, on whom, for how many ticks. */
public class ReactionClientboundPacket {

    public enum Kind { BRACE }

    private final Kind kind;
    private final int entityId;
    private final int ticks;

    public static void brace(LivingEntity unit, int ticks) {
        PacketHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(() -> unit),
            new ReactionClientboundPacket(Kind.BRACE, unit.getId(), ticks));
    }

    public ReactionClientboundPacket(Kind kind, int entityId, int ticks) {
        this.kind = kind;
        this.entityId = entityId;
        this.ticks = ticks;
    }

    public ReactionClientboundPacket(FriendlyByteBuf buffer) {
        this.kind = buffer.readEnum(Kind.class);
        this.entityId = buffer.readVarInt();
        this.ticks = buffer.readVarInt();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(kind);
        buffer.writeVarInt(entityId);
        buffer.writeVarInt(ticks);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ReactionClientEvents.receive(kind, entityId, ticks)));
        ctx.get().setPacketHandled(true);
        return true;
    }
}
