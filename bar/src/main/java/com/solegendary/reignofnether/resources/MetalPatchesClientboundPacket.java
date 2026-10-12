package com.solegendary.reignofnether.resources;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Syncs the full list of metal patch centres to clients, so the minimap can show every mex spot on the map, with
 * each patch's metal value (design/maps.md: pre-made maps have 1.5x and 2.0x patches; the minimap tints them).
 */
public class MetalPatchesClientboundPacket {

    private final List<BlockPos> patches;
    private final List<Float> values;

    public MetalPatchesClientboundPacket(List<BlockPos> patches, List<Float> values) {
        this.patches = patches;
        this.values = values;
    }

    public MetalPatchesClientboundPacket(FriendlyByteBuf buffer) {
        int count = buffer.readInt();
        this.patches = new ArrayList<>(count);
        this.values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            this.patches.add(buffer.readBlockPos());
            this.values.add(buffer.readFloat());
        }
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(patches.size());
        for (int i = 0; i < patches.size(); i++) {
            buffer.writeBlockPos(patches.get(i));
            buffer.writeFloat(i < values.size() ? values.get(i) : 1f);
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        final var success = new AtomicBoolean(false);
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                MetalPatchesClient.sync(patches, values);
                success.set(true);
            });
        });
        ctx.get().setPacketHandled(true);
        return success.get();
    }
}
