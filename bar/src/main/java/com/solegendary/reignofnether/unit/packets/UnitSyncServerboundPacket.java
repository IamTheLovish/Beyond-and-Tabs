package com.solegendary.reignofnether.unit.packets;

import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.unit.UnitSyncAction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class UnitSyncServerboundPacket {

    private final UnitSyncAction syncAction;
    private final int entityId;

    public static void requestSyncAbilities(int unitId) {
        PacketHandler.INSTANCE.sendToServer(new UnitSyncServerboundPacket(UnitSyncAction.REQUEST_SYNC_ABILITIES, unitId));
    }

    // packet-handler functions
    public UnitSyncServerboundPacket(
        UnitSyncAction syncAction,
        int unitId
    ) {
        this.syncAction = syncAction;
        this.entityId = unitId;
    }

    public UnitSyncServerboundPacket(FriendlyByteBuf buffer) {
        this.syncAction = buffer.readEnum(UnitSyncAction.class);
        this.entityId = buffer.readInt();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.syncAction);
        buffer.writeInt(this.entityId);
    }

    // server-side packet-consuming functions
    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        final var success = new AtomicBoolean(false);
        ctx.get().enqueueWork(() -> {
            // a client asks once per unit that comes into view, so a camera pan over a big army is hundreds at
            // once: a generous burst, but a cap, and the answer now goes to the asker alone (it went to everyone)
            net.minecraft.server.level.ServerPlayer sender = ctx.get().getSender();
            if (sender == null || !com.solegendary.reignofnether.network.PacketAuth.allow(sender, "unit_sync_request", 400, 1000))
                return;
            if (this.syncAction == UnitSyncAction.REQUEST_SYNC_ABILITIES) {
                // a direct id lookup instead of a walk over every unit per request
                // (sendSyncAbilitiesPacket ignores anything that isn't a Unit)
                if (sender.serverLevel().getEntity(this.entityId) instanceof LivingEntity entity)
                    UnitSyncAbilityClientboundPacket.sendSyncAbilitiesPacket(entity, sender);
            }
        });
        ctx.get().setPacketHandled(true);
        return success.get();
    }
}
