package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** The answer to a BuildingHoverServerboundPacket: an empty key means "nothing you may see". */
public class BuildingHoverClientboundPacket {

    private final BlockPos pos;
    private final String itemKey;
    private final float progress;
    private final int queued;

    static void send(ServerPlayer sp, BlockPos pos, BuildingHoverInfo.Info info) {
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp), info == null
            ? new BuildingHoverClientboundPacket(pos, "", 0f, 0)
            : new BuildingHoverClientboundPacket(pos, info.itemKey(), info.progress(), info.queued()));
    }

    public BuildingHoverClientboundPacket(BlockPos pos, String itemKey, float progress, int queued) {
        this.pos = pos;
        this.itemKey = itemKey;
        this.progress = progress;
        this.queued = queued;
    }

    public BuildingHoverClientboundPacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        this.itemKey = buffer.readUtf(256);
        this.progress = buffer.readFloat();
        this.queued = buffer.readVarInt();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeUtf(itemKey, 256);
        buffer.writeFloat(progress);
        buffer.writeVarInt(queued);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            com.solegendary.reignofnether.hud.HoverCards.onHoverInfo(pos, itemKey, progress, queued)));
        ctx.get().setPacketHandled(true);
        return true;
    }
}
