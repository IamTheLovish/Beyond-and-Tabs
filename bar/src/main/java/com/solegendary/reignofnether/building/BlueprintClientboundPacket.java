package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.custombuilding.CustomBuilding;
import com.solegendary.reignofnether.building.custombuilding.CustomBuildingClientEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * A blueprint's ghost appears on, or goes from, a client (Blueprints). Sent only through {@code Audience.team(owner)}:
 * the owner, their allies and spectators - an enemy client never receives the building, the spot or the fact that
 * one was ordered. The client draws it itself (BlueprintClientEvents) from the building type, origin and rotation.
 */
public class BlueprintClientboundPacket {

    private final boolean add;
    private final BlockPos origin;
    private final String buildingKey;   // registry id, or a custom building's name
    private final boolean custom;
    private final Rotation rotation;
    private final String owner;
    private final boolean diagonal;

    public BlueprintClientboundPacket(boolean add, BlockPos origin, String buildingKey, boolean custom, Rotation rotation,
                                      String owner, boolean diagonal) {
        this.add = add;
        this.origin = origin;
        this.buildingKey = buildingKey;
        this.custom = custom;
        this.rotation = rotation;
        this.owner = owner;
        this.diagonal = diagonal;
    }

    static BlueprintClientboundPacket add(BuildingPlacement bp) {
        return of(true, bp);
    }

    static BlueprintClientboundPacket remove(BuildingPlacement bp) {
        return of(false, bp);
    }

    private static BlueprintClientboundPacket of(boolean add, BuildingPlacement bp) {
        Building b = bp.getBuilding();
        boolean custom = b instanceof CustomBuilding;
        String key = custom ? b.name : ReignOfNetherRegistries.BUILDING.getKey(b).toString();
        return new BlueprintClientboundPacket(add, bp.originPos, key, custom, bp.rotation, bp.ownerName, bp.isDiagonalBridge);
    }

    public BlueprintClientboundPacket(FriendlyByteBuf buffer) {
        this.add = buffer.readBoolean();
        this.origin = buffer.readBlockPos();
        this.buildingKey = buffer.readUtf();
        this.custom = buffer.readBoolean();
        this.rotation = buffer.readEnum(Rotation.class);
        this.owner = buffer.readUtf();
        this.diagonal = buffer.readBoolean();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(this.add);
        buffer.writeBlockPos(this.origin);
        buffer.writeUtf(this.buildingKey);
        buffer.writeBoolean(this.custom);
        buffer.writeEnum(this.rotation);
        buffer.writeUtf(this.owner);
        buffer.writeBoolean(this.diagonal);
    }

    // what a test reads off a packet (the packet-level proof that an enemy gets nothing is which targets it was sent to)
    public boolean isAdd() { return add; }
    public BlockPos origin() { return origin; }
    public String owner() { return owner; }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        final var success = new AtomicBoolean(false);
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                if (add) {
                    Building building = custom
                        ? CustomBuildingClientEvents.getCustomBuilding(buildingKey)
                        : ReignOfNetherRegistries.BUILDING.get(ResourceLocation.tryParse(buildingKey));
                    BlueprintClientEvents.add(building, origin, rotation, owner, diagonal);
                } else {
                    BlueprintClientEvents.remove(origin);
                }
                success.set(true);
            });
        });
        ctx.get().setPacketHandled(true);
        return success.get();
    }
}
