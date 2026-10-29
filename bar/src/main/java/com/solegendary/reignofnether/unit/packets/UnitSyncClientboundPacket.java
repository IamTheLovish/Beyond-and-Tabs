package com.solegendary.reignofnether.unit.packets;

import com.solegendary.reignofnether.unit.UnitSyncBatcher;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.resources.ResourceName;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.UnitSyncAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.network.Audience;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class UnitSyncClientboundPacket {

    private final UnitSyncAction syncAction;
    private final int entityId;
    private final int targetId;
    private final float health;
    private final float absorb;
    private final double posX;
    private final double posY;
    private final double posZ;
    private final int food;
    private final int wood;
    private final int ore;
    private final int emerald;
    private final int population;
    private final String ownerName;

    public static void sendLeavePacket(LivingEntity entity) {
        UnitSyncBatcher.onUnitRemoved(entity.getId());
        PacketHandler.INSTANCE.send(Audience.all(), // a removal for sticky allUnits lists; the id says nothing
                new UnitSyncClientboundPacket(UnitSyncAction.LEAVE_LEVEL,
                        entity.getId(),0,0,0,0,0,0,0,0,0,0,0, "")
        );
    }

    /**
     * One player's client forgets a unit that went into their fog (FogOfWarServerEvents.onHiddenByFog): its sticky
     * allUnits entry would otherwise sit on the minimap at the spot it was last seen. It comes back on re-tracking.
     */
    public static void sendLeavePacketTo(ServerPlayer player, int entityId) {
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                new UnitSyncClientboundPacket(UnitSyncAction.LEAVE_LEVEL,
                        entityId,0,0,0,0,0,0,0,0,0,0,0, "")
        );
    }

    public static void sendSyncOwnerNamePacket(Unit unit) {
        PacketHandler.INSTANCE.send(Audience.all(), // rare (sandbox, commands): sticky lists drop the old owner
                new UnitSyncClientboundPacket(UnitSyncAction.SYNC_OWNERNAME,
                        ((LivingEntity) unit).getId(),0,0,0,0,0,0,0,0,0,0,0, unit.getOwnerName())
        );
    }

    public static void sendSyncScenarioRoleIndexPacket(Unit unit) {
        PacketHandler.INSTANCE.send(Audience.all(), // scenario editor roles: public
                new UnitSyncClientboundPacket(UnitSyncAction.SYNC_SCENARIO_ROLE_INDEX,
                        ((LivingEntity) unit).getId(), unit.getScenarioRoleIndex(),0,0,0,0,0,0,0,0,0,0, "")
        );
    }

    public static void sendSyncStatsPacket(List<ServerPlayer> players, LivingEntity entity) {
        String owner = "";
        if (entity instanceof Unit unit)
            owner = unit.getOwnerName();
        // out-of-band stats sync: make the next batched sync re-send authoritative stats
        UnitSyncBatcher.invalidate(entity.getId(), UnitBatchSyncClientboundPacket.Field.STATS);

        for (ServerPlayer player : players) {
            if (FogOfWarServerEvents.isBlockVisibleFor(player, entity.getOnPos().getX(), entity.getOnPos().getZ())) {
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                        new UnitSyncClientboundPacket(UnitSyncAction.SYNC_STATS,
                                entity.getId(), 0,
                                entity.getHealth(),
                                entity.getAbsorptionAmount(),
                                entity.getX(), entity.getY(), entity.getZ(),
                                0,0,0,0, com.solegendary.reignofnether.unit.Summons.population(entity), owner)
                );
            }
        }
    }

    public static void sendSyncResourcesPacket(Unit unit) {
        Resources res = Resources.getTotalResourcesFromItems(unit.getItems());
        UnitSyncBatcher.invalidate(((LivingEntity) unit).getId(), UnitBatchSyncClientboundPacket.Field.RESOURCES);
        PacketHandler.INSTANCE.send(Audience.seen((LivingEntity) unit), // a worker's load: whoever may see it (was fog-blind)
            new UnitSyncClientboundPacket(UnitSyncAction.SYNC_RESOURCES,
                ((LivingEntity) unit).getId(), 0,0,0,0,0,0,
                res.food, res.wood, res.ore, res.emerald, 0, "")
        );
    }

    /**
     * Everything a player who just logged in needs about one unit that the entity itself doesn't carry - owner,
     * scenario role, carried resources, anchor - to that player alone. PlayerServerEvents.syncUnits used to send
     * these with the broadcasts above, so every login sent four packets per unit on the map to EVERY player (a
     * friend joining a 400-unit match cost the host 1600 packets). The batched sync's own per-player state is left
     * alone: it still sends the joiner its first full batch. Only units the joiner may know (network/Audience SEEN):
     * an anchor is a position and a load is an economy, so a fogged enemy unit's are not theirs to have - the batched
     * sync sends them once the unit is in view.
     */
    public static void sendJoinSync(Unit unit, ServerPlayer to) {
        LivingEntity entity = (LivingEntity) unit;
        if (!Audience.mayKnow(to, entity))
            return;
        PacketDistributor.PacketTarget target = PacketDistributor.PLAYER.with(() -> to);
        PacketHandler.INSTANCE.send(target, new UnitSyncClientboundPacket(UnitSyncAction.SYNC_OWNERNAME,
            entity.getId(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, unit.getOwnerName()));
        PacketHandler.INSTANCE.send(target, new UnitSyncClientboundPacket(UnitSyncAction.SYNC_SCENARIO_ROLE_INDEX,
            entity.getId(), unit.getScenarioRoleIndex(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ""));
        Resources res = Resources.getTotalResourcesFromItems(unit.getItems());
        if (res.food != 0 || res.wood != 0 || res.ore != 0 || res.emerald != 0)
            PacketHandler.INSTANCE.send(target, new UnitSyncClientboundPacket(UnitSyncAction.SYNC_RESOURCES,
                entity.getId(), 0, 0, 0, 0, 0, 0, res.food, res.wood, res.ore, res.emerald, 0, ""));
        BlockPos bp = unit.getAnchor();
        if (bp != null)
            PacketHandler.INSTANCE.send(target, new UnitSyncClientboundPacket(UnitSyncAction.SYNC_ANCHOR_POS,
                entity.getId(), 0, 0, 0, bp.getX(), bp.getY(), bp.getZ(), 0, 0, 0, 0, 0, ""));
    }

    public static void makeVillagerVeteran(LivingEntity entity) {
        PacketHandler.INSTANCE.send(Audience.seen(entity), // the veteran look: whoever may see it
                new UnitSyncClientboundPacket(
                        UnitSyncAction.MAKE_VILLAGER_VETERAN,
                        entity.getId(), 0,
                        0,0,0,0,0,0,0,0,0,0, "")
        );
    }

    public static void sendSyncAnchorPosPacket(LivingEntity entity, BlockPos bp) {
        UnitSyncBatcher.invalidate(entity.getId(), UnitBatchSyncClientboundPacket.Field.ANCHOR);
        PacketHandler.INSTANCE.send(Audience.seen(entity), // whoever may see the unit
                new UnitSyncClientboundPacket(
                        UnitSyncAction.SYNC_ANCHOR_POS,
                        entity.getId(), 0,0,
                        0, bp.getX(), bp.getY(), bp.getZ(),0,0,0,0,0, "")
        );
    }

    public static void sendRemoveAnchorPosPacket(LivingEntity entity) {
        UnitSyncBatcher.invalidate(entity.getId(), UnitBatchSyncClientboundPacket.Field.ANCHOR);
        PacketHandler.INSTANCE.send(Audience.seen(entity), // whoever may see the unit
                new UnitSyncClientboundPacket(
                        // REMOVE, not SYNC: SYNC_ANCHOR_POS with zeros anchored the unit at (0,0,0) on clients
                        // until the next batched sync cleared it
                        UnitSyncAction.REMOVE_ANCHOR_POS,
                        entity.getId(), 0,0,
                        0,0,0,0,0,0,0,0,0,"")
        );
    }

    // packet-handler functions
    public UnitSyncClientboundPacket(
        UnitSyncAction syncAction,
        int unitId,
        int targetId,
        float health,
        float absorb,
        double posX,
        double posY,
        double posZ,
        int food,
        int wood,
        int ore,
        int emerald,
        int population,
        String ownerName
    ) {
        // filter out non-owned entities so we can't control them
        this.syncAction = syncAction;
        this.entityId = unitId;
        this.targetId = targetId;
        this.health = health;
        this.absorb = absorb;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.food = food;
        this.wood = wood;
        this.emerald = emerald;
        this.ore = ore;
        this.population = population;
        this.ownerName = ownerName;
    }

    public UnitSyncClientboundPacket(FriendlyByteBuf buffer) {
        this.syncAction = buffer.readEnum(UnitSyncAction.class);
        this.entityId = buffer.readInt();
        this.targetId = buffer.readInt();
        this.health = buffer.readFloat();
        this.absorb = buffer.readFloat();
        this.posX = buffer.readDouble();
        this.posY = buffer.readDouble();
        this.posZ = buffer.readDouble();
        this.food = buffer.readInt();
        this.wood = buffer.readInt();
        this.ore = buffer.readInt();
        this.emerald = buffer.readInt();
        this.population = buffer.readInt();
        this.ownerName = buffer.readUtf();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.syncAction);
        buffer.writeInt(this.entityId);
        buffer.writeInt(this.targetId);
        buffer.writeFloat(this.health);
        buffer.writeFloat(this.absorb);
        buffer.writeDouble(this.posX);
        buffer.writeDouble(this.posY);
        buffer.writeDouble(this.posZ);
        buffer.writeInt(this.food);
        buffer.writeInt(this.wood);
        buffer.writeInt(this.ore);
        buffer.writeInt(this.emerald);
        buffer.writeInt(this.population);
        buffer.writeUtf(this.ownerName);
    }

    // client-side packet-consuming functions
    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        final var success = new AtomicBoolean(false);

        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> {
                    switch (this.syncAction) {
                        case LEAVE_LEVEL -> UnitClientEvents.onEntityLeave(this.entityId);
                        case SYNC_OWNERNAME -> UnitClientEvents.syncOwnerName(this.entityId, ownerName);
                        case SYNC_SCENARIO_ROLE_INDEX -> UnitClientEvents.syncScenarioRoleIndex(this.entityId, this.targetId);
                        case SYNC_STATS -> UnitClientEvents.syncUnitStats(
                                this.entityId,
                                this.health,
                                this.absorb,
                                new Vec3(this.posX, this.posY, this.posZ),
                                this.ownerName,
                                this.population
                        );
                        case SYNC_RESOURCES -> UnitClientEvents.syncUnitResources(
                                this.entityId,
                                new Resources("", this.food, this.wood, this.ore, this.emerald)
                        );
                        case MAKE_VILLAGER_VETERAN -> UnitClientEvents.makeVillagerVeteran(this.entityId);
                        case SYNC_ANCHOR_POS -> UnitClientEvents.syncAnchorPos(
                                this.entityId,
                                new BlockPos((int) this.posX, (int) this.posY, (int) this.posZ)
                        );
                        case REMOVE_ANCHOR_POS -> UnitClientEvents.removeAnchorPos(this.entityId);
                    }
                });
        });
        ctx.get().setPacketHandled(true);
        return success.get();
    }
}
