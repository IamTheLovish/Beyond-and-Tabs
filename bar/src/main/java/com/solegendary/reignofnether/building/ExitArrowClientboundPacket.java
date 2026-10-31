package com.solegendary.reignofnether.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The answer to an ExitArrowServerboundPacket: the spawn rule of the asked building type (no world data) and, per asked
 * origin, whether to show its arrow and the exit cell. A cell is only ever present for the asker's own, visible building.
 */
public class ExitArrowClientboundPacket {

    /** One asked building: its origin as sent, and the cell if the arrow is shown (else null). */
    public record Entry(BlockPos origin, boolean show, @Nullable BlockPos cell) { }

    private final String typeKey;
    private final boolean hasSpec;
    private final boolean indoors;
    private final Vec3i offset;
    private final List<Entry> entries;

    public ExitArrowClientboundPacket(String typeKey, boolean hasSpec, boolean indoors, Vec3i offset, List<Entry> entries) {
        this.typeKey = typeKey;
        this.hasSpec = hasSpec;
        this.indoors = indoors;
        this.offset = offset;
        this.entries = entries;
    }

    public ExitArrowClientboundPacket(FriendlyByteBuf buffer) {
        this.typeKey = buffer.readUtf(128);
        this.hasSpec = buffer.readBoolean();
        this.indoors = buffer.readBoolean();
        this.offset = new Vec3i(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
        int n = buffer.readVarInt();
        if (n > ExitArrowServerboundPacket.MAX_ORIGINS)
            throw new io.netty.handler.codec.DecoderException("ExitArrowClientboundPacket: " + n + " entries");
        this.entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            BlockPos origin = buffer.readBlockPos();
            boolean show = buffer.readBoolean();
            entries.add(new Entry(origin, show, show ? buffer.readBlockPos() : null));
        }
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(typeKey, 128);
        buffer.writeBoolean(hasSpec);
        buffer.writeBoolean(indoors);
        buffer.writeVarInt(offset.getX());
        buffer.writeVarInt(offset.getY());
        buffer.writeVarInt(offset.getZ());
        buffer.writeVarInt(entries.size());
        for (Entry e : entries) {
            buffer.writeBlockPos(e.origin());
            buffer.writeBoolean(e.show());
            if (e.show())
                buffer.writeBlockPos(e.cell());
        }
    }

    public String typeKey() { return typeKey; }

    public boolean hasSpec() { return hasSpec; }

    public boolean indoors() { return indoors; }

    public Vec3i offset() { return offset; }

    public List<Entry> entries() { return entries; }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            ExitArrowClientEvents.onAnswer(this)));
        ctx.get().setPacketHandled(true);
        return true;
    }
}
