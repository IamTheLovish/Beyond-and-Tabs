package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.network.Audience;
import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class FogBuildingClientboundPacket {

    private final BlockPos pos;

    /**
     * A site queued in the fog was refused: the clients that drew it - whoever may order {@code ownerName}'s workers -
     * drop the ghost. It went to everyone, telling the enemy where a player had tried to build out of sight.
     */
    public static void removeFogQueuedBuilding(String ownerName, BlockPos pos) {
        PacketHandler.INSTANCE.send(Audience.commanders(ownerName), // only commanders drew it (network/Audience)
                new FogBuildingClientboundPacket(pos)
        );
    }

    public FogBuildingClientboundPacket(BlockPos pos) {
        this.pos = pos;
    }

    public FogBuildingClientboundPacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        final var success = new AtomicBoolean(false);

        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> {
                        BuildingClientEvents.removeFogQueuedBuilding(pos);
                        success.set(true);
                    });
        });
        ctx.get().setPacketHandled(true);
        return success.get();
    }
}
