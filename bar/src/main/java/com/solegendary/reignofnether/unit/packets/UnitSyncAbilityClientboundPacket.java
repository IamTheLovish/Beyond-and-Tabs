package com.solegendary.reignofnether.unit.packets;

import com.solegendary.reignofnether.network.Audience;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.UnitSyncAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class UnitSyncAbilityClientboundPacket {

    private final UnitSyncAction syncAction;
    private final int entityId;
    private final int[] abilityCooldowns;
    private final int[] abilityCharges;

    public static void sendSyncAbilitiesPacket(LivingEntity entity) {
        sendSyncAbilitiesPacket(entity, null);
    }

    /**
     * As above to one player only ({@code to} null = everyone). A client asks for this whenever a unit enters its
     * view (UnitClientEvents), so answering to everyone made each camera pan of one player send a packet per unit to
     * every other player as well (design/multiplayer.md).
     */
    public static void sendSyncAbilitiesPacket(LivingEntity entity, @javax.annotation.Nullable net.minecraft.server.level.ServerPlayer to) {
        UnitSyncAbilityClientboundPacket packet = of(entity);
        if (packet != null)
            // cooldowns and charges are private: the unit's side and spectators (network/Audience)
            PacketHandler.INSTANCE.send(to == null ? Audience.teamOf(entity) : PacketDistributor.PLAYER.with(() -> to),
                packet);
    }

    /** A unit's ability cooldowns and charges right now; null for anything that isn't a unit (AudienceResync too). */
    @javax.annotation.Nullable
    public static UnitSyncAbilityClientboundPacket of(LivingEntity entity) {
        if (!(entity instanceof Unit unit))
            return null;
        var abilities = unit.getAbilities().get();
        var abilityCooldowns = new int[abilities.size()];
        var abilityCharges = new int[abilities.size()];
        for (int i = 0; i < abilities.size(); i++) {
            abilityCooldowns[i] = (int) abilities.get(i).getCooldown(unit);
        }
        for (int i = 0; i < abilities.size(); i++) {
            abilityCharges[i] = abilities.get(i).getCharges(unit);
        }
        return new UnitSyncAbilityClientboundPacket(UnitSyncAction.SYNC_ABILITIES, entity.getId(), abilityCooldowns,
            abilityCharges);
    }

    // packet-handler functions
    public UnitSyncAbilityClientboundPacket(
        UnitSyncAction syncAction,
        int entityId,
        final int[] abilityCooldowns,
        final int[] abilityCharges
    ) {
        // filter out non-owned entities so we can't control them
        this.syncAction = syncAction;
        this.entityId = entityId;
        this.abilityCooldowns = abilityCooldowns;
        this.abilityCharges = abilityCharges;
    }

    public UnitSyncAbilityClientboundPacket(FriendlyByteBuf buffer) {
        this.syncAction = buffer.readEnum(UnitSyncAction.class);
        this.entityId = buffer.readInt();
        this.abilityCooldowns = buffer.readVarIntArray();
        this.abilityCharges = buffer.readVarIntArray();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.syncAction);
        buffer.writeInt(this.entityId);
        buffer.writeVarIntArray(this.abilityCooldowns);
        buffer.writeVarIntArray(this.abilityCharges);
    }

    // client-side packet-consuming functions
    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        final var success = new AtomicBoolean(false);

        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> {
                    switch (this.syncAction) {
                        case SYNC_ABILITIES -> {
                            for (LivingEntity entity : UnitClientEvents.getAllUnits()) {
                                if (entity.getId() == this.entityId && entity instanceof Unit unit) {
                                    for (int i = 0; i < unit.getAbilities().get().size(); i++) {
                                        if (this.abilityCooldowns.length > i)
                                            unit.getAbilities().get().get(i).setCooldown(this.abilityCooldowns[i], false, unit);
                                        if (this.abilityCharges.length > i)
                                            unit.getAbilities().get().get(i).setCharges(unit, this.abilityCharges[i]);
                                    }
                                    unit.updateAbilityButtons();
                                    break;
                                }
                            }
                        }
                    }
                });
        });
        ctx.get().setPacketHandled(true);
        return success.get();
    }
}
